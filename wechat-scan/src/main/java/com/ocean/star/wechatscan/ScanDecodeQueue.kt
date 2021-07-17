package com.ocean.star.wechatscan

import android.content.Context
import android.media.Image
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

class ScanDecodeQueue(private val context: Context, private val lifecycleOwner: LifecycleOwner) : LifecycleObserver {

    private val TAG = "ScanDecodeQueue"

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    interface DecodeCallback {
        fun onResult(resultList: List<WeChatQRCodeDetector.DecodeResult>)
        fun onNextDecode()
        fun onNextImage()
    }

    private var decodingTask: FutureTask<List<WeChatQRCodeDetector.DecodeResult>>? = null
    private var decodeExecutor = Executors.newSingleThreadExecutor()
    private val qrCodeDetector = WeChatQRCodeDetector()
    private val mainHandler = Handler(Looper.getMainLooper())
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

    private val nextDecodeRunnable = Runnable {
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) return@Runnable
        decodeCallback?.onNextDecode()
    }

    private val nextImageRunnable = Runnable {
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) return@Runnable
        decodeCallback?.onNextImage()
    }

    private val callbackResultRunnable = Runnable {
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED) return@Runnable
        decodeCallback?.onResult(decodingTask?.get().orEmpty())
    }

    private inner class DecodeJob(private val image: Image) : Callable<List<WeChatQRCodeDetector.DecodeResult>> {
        override fun call(): List<WeChatQRCodeDetector.DecodeResult> {
            var resultList : List<WeChatQRCodeDetector.DecodeResult> = emptyList()
            try {
                val start = System.currentTimeMillis()
                val y = image.planes[0].buffer
                val u = image.planes[1].buffer
                val v = image.planes[2].buffer
                val width = image.width
                val height = image.height
                val rowStride = image.planes[0].rowStride
                val uPixelStride = image.planes[1].pixelStride
                val vPixelStride = image.planes[2].pixelStride
                Log.i(TAG, "width, height: $width, $height, rowStride $rowStride")
                Log.i(TAG, "y:${y.limit()} ${image.planes[0].pixelStride}, u:${u.limit()} $uPixelStride, v:${v.limit()} $vPixelStride")
                val nv21 = ByteArray(width * height * 3 / 2)
                for (row in 0 until height) {
                    y.position(row * rowStride)
                    y.get(nv21, row * width, width)
                }

                var dst = 0
                val vRowBuffer = ByteArray(rowStride)
                val uRowBuffer = ByteArray(rowStride)
                for (row in 0 until (height / 2 - 1)) {
                    v.position(row * rowStride)
                    v.get(vRowBuffer, 0, rowStride)
                    u.position(row * rowStride)
                    u.get(uRowBuffer, 0, rowStride)
                    dst = width * (height + row)
                    for (col in 0 until (width / 2)) {
                        nv21[dst++] = vRowBuffer[col * vPixelStride]
                        nv21[dst++] = uRowBuffer[col * uPixelStride]
                    }
                }
                //last line , data length less than rowStride
                v.position((height / 2 - 1) * rowStride)
                u.position((height / 2 - 1) * rowStride)
                for (col in 0 until (width / 2)) {
                    nv21[dst++] = vRowBuffer[col * vPixelStride]
                    nv21[dst++] = uRowBuffer[col * uPixelStride]
                }
                image.close()
                val grayCost = System.currentTimeMillis() - start
                Log.i(TAG, "nv21 data len ${nv21.size}")
                Log.i(TAG, "convert to nv21 cost $grayCost")
                mainHandler.post(nextImageRunnable)
                val decodeStart = System.currentTimeMillis()
                resultList = qrCodeDetector.detectNV21(nv21, width, height)
                val decodeCost = System.currentTimeMillis() - decodeStart
                val totalCost = System.currentTimeMillis() - start
                Log.i(TAG, "decode get results: $resultList")
                Log.i(TAG, "decodeCost:$decodeCost, total:$totalCost")
                if (resultList.isEmpty()) {
                    mainHandler.post(nextDecodeRunnable)
                } else {
                    mainHandler.post(callbackResultRunnable)
                }
            } catch (e: Exception) {
                Log.e(TAG, "decode ${e.message}")
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
        mainHandler.removeCallbacks(nextDecodeRunnable)
        mainHandler.removeCallbacks(callbackResultRunnable)
        lifecycleOwner.lifecycle.removeObserver(this)
    }
}