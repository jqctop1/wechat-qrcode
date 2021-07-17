package com.ocean.star.wechatscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.util.*
import java.util.concurrent.Executors

class FileDecodeQueue private constructor() {

    private val TAG = "FileDecodeQueue"

    interface DecodeCallback {
        fun onResult(resultList: List<WeChatQRCodeDetector.DecodeResult>)
    }

    private var decodeExecutor = Executors.newSingleThreadExecutor()
    private val qrCodeDetector = WeChatQRCodeDetector()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fileQueue = LinkedList<DecodeFileJob>()
    private val bitmapQueue = LinkedList<DecodeBitmapJob>()


    companion object {
        private var instance: FileDecodeQueue? = null

        fun getInstance(context: Context) : FileDecodeQueue {
            if (instance == null) {
                synchronized(FileDecodeQueue::class.java) {
                    if (instance == null) {
                        instance = FileDecodeQueue()
                        instance!!.initQRCodeDetector(context)
                    }
                }
            }
            return instance!!
        }
    }

    private fun initQRCodeDetector(context: Context) {
        val modelDir = context.filesDir.canonicalPath + "/wechat_qrcode_model"
        copyAssetsFile(context, "wechat_qrcode/detect.prototxt", modelDir)
        copyAssetsFile(context, "wechat_qrcode/detect.caffemodel", modelDir)
        copyAssetsFile(context, "wechat_qrcode/sr.prototxt", modelDir)
        copyAssetsFile(context, "wechat_qrcode/sr.caffemodel", modelDir)
        val initSuccess = qrCodeDetector.initDetector(
            "$modelDir/detect.prototxt",
            "$modelDir/detect.caffemodel",
            "$modelDir/sr.prototxt",
            "$modelDir/sr.caffemodel"
        )
        Log.i(TAG, "init detector success:$initSuccess")
    }

    private fun copyAssetsFile(context: Context, source: String, dir: String) {
        val fileInput = context.assets.open(source)
        val filename = source.substring(source.lastIndexOf('/'))
        val modelDir = File(dir)
        modelDir.mkdirs()
        val targetFile = File(modelDir, filename)
        if (targetFile.exists()) return
        val fileOutput = FileOutputStream(targetFile)
        fileInput.copyTo(fileOutput)
    }

    fun decode(filePath: String, callback: DecodeCallback) {
        Log.i(TAG, "decode file: $filePath")
        if (filePath.isEmpty()) {
            callback.onResult(emptyList())
            return
        }
        val existJob = fileQueue.firstOrNull { it.filePath == filePath }
        existJob?.let {
            it.addCallback(callback)
            return
        }
        fileQueue.add(DecodeFileJob(filePath).apply { addCallback(callback) })
        mainHandler.post(decodeNextRunnable)
    }

    fun decode(bitmap: Bitmap, callback: DecodeCallback) {
        Log.i(TAG, "decode bitmap: $bitmap")
        bitmapQueue.add(DecodeBitmapJob(bitmap, callback))
        mainHandler.post(decodeNextRunnable)
    }

    private val decodeNextRunnable = Runnable {
        bitmapQueue.poll()?.let {
            decodeExecutor.execute(it)
            return@Runnable
        }
        fileQueue.poll()?.let {
            decodeExecutor.execute(it)
        }
    }

    private inner class DecodeFileJob(val filePath: String) : Runnable {

        private val callbacks = ArrayList<DecodeCallback>()

        override fun run() {
            try {
                val start = System.currentTimeMillis()
                val bitmap = BitmapFactory.decodeFile(filePath)
                if (bitmap == null) {
                    doCallback(emptyList())
                    return
                }
                Log.i(TAG, "decode file bitmap, width: ${bitmap.width}, height: ${bitmap.height}")
                val rgb = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(rgb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                Log.i(TAG, "decode file bitmap, cost: ${System.currentTimeMillis() - start} ms")
                val decodeStart = System.currentTimeMillis()
                val resultList = qrCodeDetector.detectRGB(rgb, bitmap.width, bitmap.height)
                val decodeCost = System.currentTimeMillis() - decodeStart
                val totalCost = System.currentTimeMillis() - start
                Log.i(TAG, "decode file results: $resultList")
                Log.i(TAG, "decode file cost: $decodeCost, total cost: $totalCost")
                doCallback(resultList)
            } catch (e: Exception) {
                Log.e(TAG, "decode file, ${e.message}")
            }
        }

        private fun doCallback(resultList: List<WeChatQRCodeDetector.DecodeResult>) {
            callbacks.forEach {
                it.onResult(resultList)
            }
            mainHandler.post(decodeNextRunnable)
        }

        fun addCallback(callback: DecodeCallback) {
            if (!callbacks.contains(callback)) {
                callbacks.add(callback)
            }
        }
    }

    private inner class DecodeBitmapJob(private val bitmap: Bitmap, private val callback: DecodeCallback) : Runnable {

        override fun run() {
            try {
                val start = System.currentTimeMillis()
                Log.i(TAG, "decode bitmap width: ${bitmap.width}, height: ${bitmap.height}")
                val rgb = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(rgb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val decodeStart = System.currentTimeMillis()
                val resultList = qrCodeDetector.detectRGB(rgb, bitmap.width, bitmap.height)
                val decodeCost = System.currentTimeMillis() - decodeStart
                val totalCost = System.currentTimeMillis() - start
                Log.i(TAG, "decode bitmap results: $resultList")
                Log.i(TAG, "decode bitmap cost: ${decodeCost}, total cost: $totalCost")
                doCallback(resultList)
            } catch (e: Exception) {
                Log.e(TAG, "decode bitmap, ${e.message}")
            }
        }

        private fun doCallback(resultList: List<WeChatQRCodeDetector.DecodeResult>) {
            callback.onResult(resultList)
            mainHandler.post(decodeNextRunnable)
        }
    }

}