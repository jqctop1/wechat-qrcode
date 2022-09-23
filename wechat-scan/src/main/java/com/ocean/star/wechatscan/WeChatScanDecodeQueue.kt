package com.ocean.star.wechatscan

import android.content.Context
import android.media.Image
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

class WeChatScanDecodeQueue(private val context: Context, private val lifecycleOwner: LifecycleOwner) : LifecycleObserver {

    private val TAG = "WeChatScanDecodeQueue"

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    private enum class Msg {
        onNextDecode,
        onCloseImage,
        onResult
    }

    private var decodingTask: FutureTask<List<DecodeResult>>? = null
    private var decodeExecutor = Executors.newSingleThreadExecutor()
    private val qrCodeDetector = WeChatQRCodeDetector()
    var decodeCallback: DecodeCallback? = null

    private fun initQRCodeDetector() {
        val modelDir = context.filesDir.canonicalPath + "/wechat_qrcode_model"
        copyAssetsFile("wechat_qrcode/detect.prototxt", modelDir)
        copyAssetsFile("wechat_qrcode/detect.caffemodel", modelDir)
        copyAssetsFile("wechat_qrcode/sr.prototxt", modelDir)
        copyAssetsFile("wechat_qrcode/sr.caffemodel", modelDir)
        val initSuccess = qrCodeDetector.initDetector(
            "$modelDir/detect.prototxt",
            "$modelDir/detect.caffemodel",
            "$modelDir/sr.prototxt",
            "$modelDir/sr.caffemodel"
        )
        Log.i(TAG, "init detector success:$initSuccess")
    }

    private fun copyAssetsFile(source: String, dir: String) {
        val fileInput = context.assets.open(source)
        val filename = source.substring(source.lastIndexOf('/'))
        val modelDir = File(dir)
        modelDir.mkdirs()
        val targetFile = File(modelDir, filename)
        if (targetFile.exists()) return
        val fileOutput = FileOutputStream(targetFile)
        fileInput.copyTo(fileOutput)
    }

    fun decode(image: Image) {
        Log.i(TAG, "decode $image")
        if (decodingTask == null || decodingTask!!.isDone) {
            decodingTask = FutureTask(DecodeJob(image))
            decodeExecutor.execute(decodingTask)
        }
    }

    private val callbackHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
            when (msg.what) {
                Msg.onNextDecode.ordinal -> {
                    decodeCallback?.onNextDecode()
                }
                Msg.onCloseImage.ordinal -> {
                    (msg.obj as? Image)?.let {
                        decodeCallback?.onCloseImage(it)
                    }
                }
                Msg.onResult.ordinal -> {
                    (msg.obj as? List<DecodeResult>)?.let {
                        decodeCallback?.onResult(it)
                    }
                }
            }
        }
    }

    private inner class DecodeJob(private val image: Image) : Callable<List<DecodeResult>> {
        override fun call(): List<DecodeResult> {
            val resultList : MutableList<DecodeResult> = mutableListOf()
            try {
                Log.i(TAG, "width, height: ${image.width}, ${image.height}")
                val start = System.currentTimeMillis()
                val nv21: ByteArray = image.getNV21()
                val grayCost = System.currentTimeMillis() - start
                Log.i(TAG, "nv21 data len ${nv21.size}")
                Log.i(TAG, "convert to nv21 cost $grayCost ms")
                callbackHandler.sendMessage(Message.obtain(callbackHandler, Msg.onCloseImage.ordinal, image))
                val decodeStart = System.currentTimeMillis()
                resultList.addAll(qrCodeDetector.detectNV21(nv21, image.width, image.height))
                val decodeCost = System.currentTimeMillis() - decodeStart
                val totalCost = System.currentTimeMillis() - start
                Log.i(TAG, "decode get results: $resultList")
                Log.i(TAG, "decodeCost:$decodeCost ms, total:$totalCost ms")
                if (resultList.isEmpty()) {
                    callbackHandler.sendEmptyMessage(Msg.onNextDecode.ordinal)
                } else {
                    callbackHandler.sendMessage(Message.obtain(callbackHandler, Msg.onResult.ordinal, resultList))
                }
            } catch (e: Throwable) {
                Log.e(TAG, "decode failed, ${Log.getStackTraceString(e)}")
            } finally {
                if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                    qrCodeDetector.releaseDetector()
                }
            }
            return resultList
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        Log.i(TAG, "onCreate")
        initQRCodeDetector()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        Log.i(TAG, "onResume")
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        Log.i(TAG, "onStop")
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        Log.i(TAG, "onDestroy")
        if (decodingTask == null || decodingTask!!.isDone) {
            qrCodeDetector.releaseDetector()
        }
        callbackHandler.removeCallbacksAndMessages(null)
        lifecycleOwner.lifecycle.removeObserver(this)
    }
}