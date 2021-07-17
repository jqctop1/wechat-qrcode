package com.ocean.star.wechatscan

import android.content.Context
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.util.AttributeSet
import android.util.Log
import com.ocean.star.wechatscan.camera.CameraController
import com.ocean.star.wechatscan.camera.CameraPreviewView

open class ScanQRCodeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : CameraPreviewView(context, attrs, defStyleAttr), ImageReader.OnImageAvailableListener, ScanDecodeQueue.DecodeCallback {

    private val TAG = "ScanQRCodeView"

    var scanCallback: ScanCallback? = null
    private var nextImage: Image? = null
    private var scanDecodeQueue = ScanDecodeQueue(context, this).apply {
        decodeCallback = this@ScanQRCodeView
    }

    init {
        cameraController?.pictureFormat = ImageFormat.YUV_420_888
        cameraController?.pictureMaxCount = 1
    }

    interface ScanCallback {
        fun onScanResult(resultList: List<WeChatQRCodeDetector.DecodeResult>)
    }

    private val cameraCallback = object : CameraController.CameraCallback() {

        override fun onCameraOpened() {
            Log.i(TAG, "oCameraOpened")
        }

        override fun onPreviewStarted() {
            Log.i(TAG, "onPreviewStarted, preview size: ${cameraController?.previewSize}, ${cameraController?.pictureFormat}")
            cameraController?.takePicture()
            cameraController?.imageReader?.setOnImageAvailableListener(this@ScanQRCodeView, null)
        }
    }

    override fun onImageAvailable(imageReader: ImageReader?) {
        imageReader?.let {
            Log.i(TAG, "onImageAvailable, ${it.maxImages}")
            nextImage?.close()
            nextImage = it.acquireNextImage()
            nextImage?.apply {
                Log.i(TAG, "image format: ${format}")
                if (format == ImageFormat.YUV_420_888) {
                    scanDecodeQueue.decode(this)
                }
            }
        }
    }

    fun startScan() {
        cameraController?.startPreview()
    }

    fun stopScan() {
        nextImage?.close()
        nextImage = null
        cameraController?.pausePreview()
    }

    override fun onResult(resultList: List<WeChatQRCodeDetector.DecodeResult>) {
        Log.i(TAG, "onResult, result size: ${resultList.size}")
        scanCallback?.onScanResult(resultList)
    }

    override fun onNextDecode() {
        Log.i(TAG, "onNextDecode, nextImage $nextImage")
        nextImage?.let {
            scanDecodeQueue.decode(it)
        }
    }

    override fun onNextImage() {
        Log.i(TAG, "onNextImage")
        cameraController?.takePicture()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        cameraController?.addCameraCallback(cameraCallback)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cameraController?.removeCameraCallback(cameraCallback)
    }
}