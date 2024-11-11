package com.ocean.star.wechatscan

import android.graphics.Rect
import android.media.Image
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import zxingcpp.BarcodeReader
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

class ZxingScanDecodeQueue(private val lifecycleOwner: LifecycleOwner) : LifecycleObserver {

    private val TAG = "ZxingScanDecodeQueue"

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
    private val qrCodeReader = BarcodeReader(options = BarcodeReader.Options(
        formats = setOf(BarcodeReader.Format.QR_CODE),
        tryRotate = true,
        tryDownscale = true))
    var decodeCallback: DecodeCallback? = null

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
                val start = System.currentTimeMillis()
                val width = image.width
                val height = image.height
                Log.i(TAG, "width, height: $width, $height")
                callbackHandler.sendMessage(Message.obtain(callbackHandler, Msg.onCloseImage.ordinal, image))
                val decodeStart = System.currentTimeMillis()
                qrCodeReader.read(image).forEach {
                    if (!it.text.isNullOrEmpty()) {
                        val left = it.position.topLeft.x.coerceAtMost(it.position.bottomLeft.x)
                        val top = it.position.topLeft.y.coerceAtMost(it.position.topRight.y)
                        val right = it.position.topRight.x.coerceAtLeast(it.position.bottomRight.x)
                        val bottom = it.position.bottomLeft.y.coerceAtLeast(it.position.bottomRight.y)
                        resultList.add(DecodeResult(it.text, Rect(left, top, right, bottom)))
                    }
                }
                val decodeCost = System.currentTimeMillis() - decodeStart
                Log.i(TAG, "decode get results: $resultList")
                Log.i(TAG, "decodeCost:$decodeCost ms")
                if (resultList.isEmpty()) {
                    callbackHandler.sendEmptyMessage(Msg.onNextDecode.ordinal)
                } else {
                    callbackHandler.sendMessage(Message.obtain(callbackHandler, Msg.onResult.ordinal, resultList))
                }
            } catch (e: Throwable) {
                Log.e(TAG, "decode failed, ${Log.getStackTraceString(e)}")
            }
            return resultList
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        Log.i(TAG, "onCreate")
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
        callbackHandler.removeCallbacksAndMessages(null)
        lifecycleOwner.lifecycle.removeObserver(this)
    }
}