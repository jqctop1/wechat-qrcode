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
    : CameraPreviewView(context, attrs, defStyleAttr), ImageReader.OnImageAvailableListener, DecodeCallback {

    private val TAG = "ScanQRCodeView"

    var scanCallback: ScanCallback? = null
    private var nextImage: Image? = null
    private var scanDecodeQueue = WeChatScanDecodeQueue(context, this).apply {
        decodeCallback = this@ScanQRCodeView
    }
    /*private var scanDecodeQueue = ZxingScanDecodeQueue(this).apply {
        decodeCallback = this@ScanQRCodeView
    }*/

    interface ScanCallback {
        fun onScanResult(resultList: List<DecodeResult>)
    }

    private val cameraCallback = object : CameraController.CameraCallback() {

        override fun onCameraOpened() {
            Log.i(TAG, "oCameraOpened")
        }

        override fun onPreviewStarted() {
            Log.i(TAG, "onPreviewStarted, preview size: ${cameraController?.previewSize}, ${cameraController?.pictureFormat}")
            cameraController?.previewImageReader?.setOnImageAvailableListener(this@ScanQRCodeView, null)
        }
    }

    override fun onImageAvailable(imageReader: ImageReader?) {
        if (imageReader == cameraController?.previewImageReader) {
            imageReader?.let { reader ->
                Log.i(TAG, "onImageAvailable, ${reader.maxImages}")
                nextImage?.close()
                nextImage = reader.acquireNextImage()
                nextImage?.let {
                    Log.i(TAG, "image format: $it.format")
                    if (it.format == ImageFormat.YUV_420_888) {
                        scanDecodeQueue.decode(it)
                    }
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

    override fun onResult(resultList: List<DecodeResult>) {
        Log.i(TAG, "onResult, result size: ${resultList.size}")
        scanCallback?.onScanResult(resultList)
    }

    override fun onNextDecode() {
        Log.i(TAG, "onNextDecode, nextImage $nextImage")
        nextImage?.let {
            scanDecodeQueue.decode(it)
        }
    }

    override fun onCloseImage(image: Image) {
        Log.i(TAG, "onCloseImage image $image")
        image.close()
        if (image == nextImage) {
            nextImage = null
        }
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