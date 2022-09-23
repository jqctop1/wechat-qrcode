package com.ocean.star.wechatscan.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import java.util.*
import kotlin.math.abs


class CameraController(private val context: Context, private val lifecycleOwner: LifecycleOwner) : LifecycleObserver {

    private val TAG = "CameraController"

    private val cameraManager: CameraManager
    private var previewSurface: Surface? = null
    private var currentCameraInfo: CameraCharacteristics? = null
    private var currentCamera: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    var previewImageReader: ImageReader? = null
        private set
    var previewSize: Point ?= null
    var previewFormat: Int = ImageFormat.YUV_420_888

    var pictureImageReader: ImageReader? = null
        private set
    var pictureSize: Point ?= null
    var pictureFormat: Int = ImageFormat.JPEG

    var useFrontCamera: Boolean = false
        private set
    var preferFocusMode: Int = -1
    private var openFlash: Boolean = false
    private var autoFocus: Boolean = false
    private val cameraStateCallback: CameraDevice.StateCallback
    private val previewCallback: CameraCaptureSession.CaptureCallback
    private val focusCallback: CameraCaptureSession.CaptureCallback
    private val captureCallback: CameraCaptureSession.CaptureCallback
    var autoFocusInterval = 1500L
    var focusArea: Rect? = null
        private set
    var zoomRatio : Float = 1.0f
        private set
    private var cropRegion: Rect? = null
    private var autoFocusRunnable : Runnable
    private var cameraCallbacks: MutableList<CameraCallback> = LinkedList()
    private val mainHandler = Handler(Looper.getMainLooper())

    abstract class CameraCallback {

        open fun onCameraOpened() {}

        open fun onCameraClosed() {}

        open fun onPreviewStarted() {}

        open fun onPreviewStopped() {}

        open fun onFocusCompleted() {}

    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)
        cameraManager = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraStateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Log.i(TAG, "camera ${camera.id} opened")
                currentCamera = camera
                synchronized(cameraCallbacks) {
                    cameraCallbacks.forEach {callback ->
                        callback.onCameraOpened()
                    }
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.i(TAG, "camera ${camera.id} disconnected")
                closeCamera()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "camera ${camera.id} error:$error")
                closeCamera()
            }
        }

        previewCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
                Log.v(TAG, "onPreviewStarted $frameNumber")
            }

            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                Log.v(TAG, "onPreviewCompleted ${result.frameNumber}")
            }

            override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                Log.e(TAG, "onPreviewFailed ${failure.frameNumber}")
            }
        }

        captureCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
                Log.v(TAG, "onCaptureStarted $frameNumber")
            }

            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                Log.v(TAG, "onCaptureCompleted ${result.frameNumber}")
            }

            override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                Log.e(TAG, "onCaptureFailed ${failure.frameNumber}")
            }
        }

        focusCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
                Log.v(TAG, "onFocusStarted $frameNumber")
            }

            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                Log.v(TAG, "onFocusCompleted ${result.frameNumber}")
                synchronized(cameraCallbacks) {
                    cameraCallbacks.forEach {callback ->
                        callback.onFocusCompleted()
                    }
                }
                if (autoFocus) {
                    startAutoFocus(autoFocusInterval)
                }
            }

            override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                Log.e(TAG, "onFocusFailed ${failure.frameNumber}")
                if (autoFocus) {
                    startAutoFocus(autoFocusInterval)
                }
            }
        }

        autoFocusRunnable = Runnable {
            if (isPreviewStarted()) {
                playSingleFocus()
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        Log.i(TAG, "onCreate")
        openCamera()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        Log.i(TAG, "onResume")
        if (currentCameraInfo != null) {
            startPreview()
        } else {
            openCamera()
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        Log.i(TAG, "onStop")
        closeCamera()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        lifecycleOwner.lifecycle.removeObserver(this)
        Log.i(TAG, "onDestroy")
        synchronized(cameraCallbacks) {
            cameraCallbacks.clear()
        }
    }

    fun addCameraCallback(callback: CameraCallback) {
        synchronized(cameraCallbacks) {
            if (!cameraCallbacks.contains(callback)) {
                cameraCallbacks.add(callback)
            }
        }
    }

    fun removeCameraCallback(callback: CameraCallback) {
        synchronized(cameraCallbacks) {
            cameraCallbacks.remove(callback)
        }
    }

    fun getSelectedCameraFacing() : Int {
        return if (useFrontCamera)
            CameraCharacteristics.LENS_FACING_FRONT
        else
            CameraCharacteristics.LENS_FACING_BACK
    }

    private fun printCameraInfo() {
        currentCameraInfo?.let {
            Log.i(TAG, "--------------------print camera info start-------------------")
            Log.i(TAG, "camera facing: ${it[CameraCharacteristics.LENS_FACING]}")
            Log.i(TAG, "camera rotation: ${it[CameraCharacteristics.SENSOR_ORIENTATION]}")
            Log.i(TAG, "camera support preview sizes: ${it.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(SurfaceTexture::class.java)?.asList()}")
            Log.i(TAG, "camera support focus mode: ${it.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.asList()}")
            Log.i(TAG, "camera focus region: ${it.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)}")
            Log.i(TAG, "camera max zoom: ${it.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)}")
            Log.i(TAG, "--------------------print camera info end-------------------")
        }
    }

    fun useFrontCamera(useFrontCamera: Boolean) {
        Log.i(TAG, "useFrontCamera $useFrontCamera")
        if (this.useFrontCamera == useFrontCamera) return
        this.useFrontCamera = useFrontCamera
        closeCamera()
        openCamera()
    }

    @SuppressLint("MissingPermission")
    fun openCamera() {
        val hasPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        Log.i(TAG, "openCamera, has permission: $hasPermission")
        if (hasPermission) {
            if (isCameraOpened()) return
            cameraManager.cameraIdList.forEach { cameraId ->
                try {
                    val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
                    if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == getSelectedCameraFacing()) {
                        currentCameraInfo = cameraCharacteristics
                        printCameraInfo()
                        cameraManager.openCamera(cameraId, cameraStateCallback, null)
                        return@forEach
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "openCamera $e")
                }
            }
        }
    }

    fun startPreview() {
        Log.i(TAG, "startPreview, currentCamera ${currentCamera?.id}, captureSession $captureSession")
        if (isPreviewStarted())  {
            sendPreviewRequest()
            synchronized(cameraCallbacks) {
                cameraCallbacks.forEach {callback ->
                    callback.onPreviewStarted()
                }
            }
            //启动手动对焦
            if (getAutoFocusMode() == CaptureRequest.CONTROL_AF_MODE_AUTO) {
                startAutoFocus()
            }
            return
        }
        if (currentCamera != null && previewSurface != null && previewImageReader != null && pictureImageReader != null) {
            try {
                currentCamera!!.createCaptureSession(listOf(previewSurface, previewImageReader!!.surface, pictureImageReader!!.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.i(TAG, "onConfigured session")
                        captureSession = session
                        sendPreviewRequest()
                        synchronized(cameraCallbacks) {
                            cameraCallbacks.forEach {callback ->
                                callback.onPreviewStarted()
                            }
                        }
                        //启动手动对焦
                        if (getAutoFocusMode() == CaptureRequest.CONTROL_AF_MODE_AUTO) {
                            startAutoFocus()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "onConfigureFailed")
                    }
                }, null)
            } catch (e: Exception) {
                Log.e(TAG, "startPreview: $e")
            }
        }
    }

    private fun getAutoFocusMode() : Int {
        var focusMode = CameraCharacteristics.CONTROL_AF_MODE_AUTO
        currentCameraInfo?.let { it ->
            val supportModes = it.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            supportModes?.let { modes ->
                Log.i(TAG, "support focus mode ${modes.toList()}, prefer mode $preferFocusMode")
                focusMode = if (modes.contains(preferFocusMode)) {
                    preferFocusMode
                } else {
                    if (modes.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {
                        CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    } else if (modes.contains(CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                        CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    } else if (modes.contains(CameraCharacteristics.CONTROL_AF_MODE_AUTO)) {
                        CameraCharacteristics.CONTROL_AF_MODE_AUTO
                    } else {
                        modes.first()
                    }
                }
            }
        }
        return focusMode
    }

    private fun playSingleFocus() {
        captureSession?.let {
            try {
                previewSurface?.let {
                    val requestBuilder = currentCamera?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    requestBuilder?.addTarget(it)
                    requestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    requestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                    requestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CameraCharacteristics.CONTROL_AE_MODE_ON)
                    focusArea?.let {
                        requestBuilder?.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(MeteringRectangle(focusArea, MeteringRectangle.METERING_WEIGHT_MAX)))
                        requestBuilder?.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(MeteringRectangle(focusArea, MeteringRectangle.METERING_WEIGHT_MAX)))
                    }
                    requestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                    if (openFlash) {
                        requestBuilder?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                    } else {
                        requestBuilder?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    }
                    cropRegion?.let {
                        requestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
                    }
                    val captureRequest = requestBuilder?.build()
                    captureRequest?.let { request ->
                        captureSession?.capture(request, focusCallback, null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "playAutoFocus $e")
            }
        }
    }

    fun startAutoFocus(delay: Long = 0L) {
        autoFocus = true
        mainHandler.removeCallbacks(autoFocusRunnable)
        mainHandler.sendMessageDelayed(Message.obtain(mainHandler, autoFocusRunnable), delay)
    }

    fun stopAutoFocus() {
        autoFocus = false
        mainHandler.removeCallbacks(autoFocusRunnable)
    }

    fun getFocusRegion() : Rect? {
        var region : Rect? = null
        if (currentCameraInfo != null) {
            region = currentCameraInfo!!.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            region?.let {
                region = Rect(it.left - it.left, it.top - it.top, it.right - it.left, it.bottom - it.top)
            }
        }
        return region
    }

    fun focusOn(focusArea: Rect) {
        this.focusArea = focusArea
        Log.i(TAG, "focus on $focusArea")
        playSingleFocus()
    }

    private fun computeCropRegion() : Rect? {
        var region : Rect? = null
        if (currentCameraInfo != null) {
            region = currentCameraInfo!!.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            region?.let {
                if (zoomRatio > 1) {
                    val newWidth = (it.width() / zoomRatio).toInt()
                    val newHeight = (it.height() / zoomRatio).toInt()
                    val left = (it.width() - newWidth) / 2
                    val top = (it.height() - newHeight) / 2
                    region = Rect(left, top, newWidth + left, newHeight + top)
                }
            }
        }
        return region
    }

    fun zoom(scale: Float) {
        zoomTo(scale * zoomRatio)
    }

    /**
     *   zoom的实际生效还要取决于当前预览图的size，即缩放的后的cropRegion的宽高不能小于当前预览图的size
     */
    fun getMaxRoomRatio() : Float {
        var maxRatio = currentCameraInfo?.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f
        val region = currentCameraInfo!!.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        if (previewSize != null && region != null) {
            maxRatio = minOf(maxRatio, region.width() / previewSize!!.x.toFloat(), region.height() / previewSize!!.y.toFloat())
        }
        return maxRatio
    }

    fun zoomTo(ratio: Float) {
        var maxRatio = getMaxRoomRatio()
        Log.i(TAG, "try zoom to $ratio, max zoom $maxRatio")
        zoomRatio = if (ratio > maxRatio) {
            maxRatio
        } else if (ratio < 1.0f) {
            1.0f
        } else {
            ratio
        }
        cropRegion = computeCropRegion()
        Log.i(TAG, "zoomRatio $zoomRatio, cropRegion $cropRegion")
        if (isPreviewStarted() && cropRegion != null) {
            sendPreviewRequest()
        }
    }

    fun openFlash(open: Boolean) {
        val supportFlash = isSupportFlash()
        Log.i(TAG, "support $supportFlash, openFlash $openFlash -> $open")
        if (openFlash == open) return
        openFlash = open
        if (supportFlash && isPreviewStarted()) {
            sendPreviewRequest()
        }
    }

    private fun sendPreviewRequest() {
        captureSession?.let {
            try {
                previewSurface?.let {
                    Log.i(TAG, "previewSurface ${it.isValid}")
                    val requestBuilder = currentCamera?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    requestBuilder?.addTarget(it)
                    previewImageReader?.let {
                        requestBuilder?.addTarget(it.surface)
                    }
                    requestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    requestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, getAutoFocusMode())
                    requestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CameraCharacteristics.CONTROL_AE_MODE_ON)
                    requestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                    requestBuilder?.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
                    if (openFlash) {
                        requestBuilder?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                    } else {
                        requestBuilder?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    }
                    cropRegion?.let {
                        requestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
                    }
                    val captureRequest = requestBuilder?.build()
                    captureRequest?.let { request ->
                        captureSession?.setRepeatingRequest(request, previewCallback, null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendPreviewRequest $e")
            }
        }
    }

    fun pausePreview() {
        try {
            Log.i(TAG, "pause preview")
            captureSession?.stopRepeating()
            captureSession?.abortCaptures()
        } catch (e: Exception) {
            Log.e(TAG, "pausePreview $e")
        }
    }

    fun stopPreview() {
        try {
            Log.i(TAG, "stop preview")
            captureSession?.close()
            captureSession = null
            openFlash = false
            stopAutoFocus()
            synchronized(cameraCallbacks) {
                cameraCallbacks.forEach {callback ->
                    callback.onPreviewStopped()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopPreview $e")
        }
    }

    fun takePicture() {
        try {
            captureSession?.let {
                pictureImageReader?.let {
                    Log.i(TAG, "take picture")
                    val requestBuilder = currentCamera?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    requestBuilder?.addTarget(it.surface)
                    requestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    requestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                    focusArea?.let {
                        requestBuilder?.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(MeteringRectangle(focusArea, MeteringRectangle.METERING_WEIGHT_MAX)))
                        requestBuilder?.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(MeteringRectangle(focusArea, MeteringRectangle.METERING_WEIGHT_MAX)))
                    }
                    requestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                    requestBuilder?.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
                    if (openFlash) {
                        requestBuilder?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                    } else {
                        requestBuilder?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    }
                    cropRegion?.let {
                        requestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, cropRegion)
                    }
                    val captureRequest = requestBuilder?.build()
                    captureRequest?.let { request ->
                        captureSession?.capture(request, captureCallback, null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "takePicture $e")
        }
    }

    fun closeCamera() {
        try {
            stopPreview()
            Log.i(TAG, "close camera")
            previewSurface?.release()
            previewImageReader?.close()
            previewImageReader = null
            pictureImageReader?.close()
            pictureImageReader = null
            currentCamera?.close()
            currentCamera = null
            currentCameraInfo = null
            synchronized(cameraCallbacks) {
                cameraCallbacks.forEach {callback ->
                    callback.onCameraClosed()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "closeCamera $e")
        }
    }

    fun isCameraOpened() : Boolean {
        return currentCamera != null
    }

    fun isPreviewStarted() : Boolean {
        return captureSession != null
    }

    fun isSupportFlash() : Boolean {
        currentCameraInfo?.let { it ->
            val supportFlash = it.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            Log.i(TAG, "support flash: $supportFlash")
            return supportFlash
        }
        return false
    }

    fun onSurfaceCreated(surface: Surface, size: Point) {
        Log.i(TAG, "onSurfaceCreated")
        stopPreview()
        previewSurface?.release()
        previewSurface = surface
        previewSize = size
        if (pictureSize == null) {
            pictureSize = size
        }
        if (previewImageReader == null) {
            previewImageReader = ImageReader.newInstance(previewSize!!.x, previewSize!!.y, previewFormat, 1)
        }
        if (pictureImageReader == null) {
            pictureImageReader = ImageReader.newInstance(pictureSize!!.x, pictureSize!!.y, pictureFormat, 1)
        }
        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
            startPreview()
        }
    }

    fun onSurfaceDestroyed() {
        Log.i(TAG, "onSurfaceDestroyed")
        stopPreview()
        previewSurface?.release()
    }

    fun getSensorRotation() : Int {
        return currentCameraInfo?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
    }

    fun getBestPreviewSize(surfaceSize: Point, landscape: Boolean) : Point? {
        var bestPreviewSize : Point? = null
        currentCameraInfo?.let {
            val previewMap = it.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val rotation = it.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            Log.i(TAG, "rotation $rotation, landscape $landscape")
            val needRotate = !landscape && (rotation % 180 != 0)
            val surfaceRatio = if (needRotate) {
                surfaceSize.y.toFloat() / surfaceSize.x
            } else {
                surfaceSize.x.toFloat() / surfaceSize.y
            }
            val previewSizeList = previewMap?.getOutputSizes(SurfaceTexture::class.java)
            previewSizeList?.first()?.let { first ->
                bestPreviewSize = Point(first.width, first.height)
            }
            previewSizeList?.filter { size ->
                val screenSize = getScreenRealSize(context)
                (size.width * size.height <= screenSize.x * screenSize.y)
            }?.forEach { size ->
                val previewRatio = 1.0f * size.width / size.height
                Log.d(TAG, "preview size: ${size.width}, ${size.height}, preview ratio: ${previewRatio}, surface ratio: $surfaceRatio")
                if ( abs(previewRatio - surfaceRatio) < abs(bestPreviewSize!!.x.toFloat() / bestPreviewSize!!.y - surfaceRatio) ) {
                    bestPreviewSize = Point(size.width, size.height)
                }
            }
        }
        Log.i(TAG, "select best preview size $bestPreviewSize")
        return bestPreviewSize
    }

    private fun getScreenRealSize(context: Context): Point {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealMetrics(metrics)
        return Point(metrics.widthPixels, metrics.heightPixels)
    }

}