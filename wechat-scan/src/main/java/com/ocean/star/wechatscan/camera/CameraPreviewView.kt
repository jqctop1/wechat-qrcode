package com.ocean.star.wechatscan.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

open class CameraPreviewView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener,
    LifecycleOwner {

    private val TAG = "CameraPreviewView"

    private var lifecycleRegistry: LifecycleRegistry
    protected var textureView: TextureView = TextureView(context)
    protected var cameraController: CameraController? = null
    protected var surfaceTexture: SurfaceTexture? = null
    protected var textureSize: Point? = null
    protected var bestPreviewSize: Point? = null
    protected var gestureDetector: GestureDetector? = null
    protected var scaleGestureDetector: ScaleGestureDetector? = null
    var useFrontCamera = false
        private set
    var openFlash = false
        private set

    var TOUCH_FOCUS_RECT_WIDTH = 100


    protected var defaultPreviewCallback: CameraController.CameraCallback = object: CameraController.CameraCallback() {
        override fun onCameraOpened() {
            Log.i(TAG, "onCameraOpen")
            tryStartPreview()
        }

        override fun onCameraClosed() {
            Log.i(TAG, "onCameraClosed")
        }

        override fun onPreviewStarted() {
            Log.i(TAG, "onPreviewStarted")
        }

        override fun onPreviewStopped() {
            Log.i(TAG, "onPreviewStopped")
        }

        override fun onFocusCompleted() {
            Log.i(TAG, "onFocusCompleted")
        }
    }

    init {
        this.addView(textureView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        this.also {
            lifecycleRegistry = LifecycleRegistry(it)
            textureView.surfaceTextureListener = it
            cameraController = CameraController(context, it)
        }
        gestureDetector = GestureDetector(context, object: GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                return onSingleTap(e)
            }
        })
        scaleGestureDetector = ScaleGestureDetector(context, object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector) : Boolean {
                return onTouchScale(detector)
            }
        })
        setOnTouchListener { v, event ->
            if (event.pointerCount > 1) {
                scaleGestureDetector?.onTouchEvent(event) ?: false
            } else {
                gestureDetector?.onTouchEvent(event) ?: false
            }
        }
    }

    override fun getLifecycle(): LifecycleRegistry {
        return lifecycleRegistry
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        cameraController!!.addCameraCallback(defaultPreviewCallback)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cameraController!!.removeCameraCallback(defaultPreviewCallback)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            onDestroy()
        }
    }

    open fun onCreate() {
        Log.i(TAG, "onCreate")
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    open fun onResume() {
        Log.i(TAG, "onResume")
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    open fun onPause() {
        Log.i(TAG, "onPause")
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    open fun onStop() {
        Log.i(TAG, "onStop")
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    open fun onDestroy() {
        Log.i(TAG, "onDestroy")
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    open fun isLandscape() : Boolean {
        return getDisplayRotation() % 180 != 0
    }

    open fun onSingleTap(event: MotionEvent) : Boolean {
        Log.i(TAG, "onTouchDown ${event.x}, ${event.y}")
        if (textureSize != null) {
            val dx = TOUCH_FOCUS_RECT_WIDTH / 2
            val dy = TOUCH_FOCUS_RECT_WIDTH / 2
            val touchRect = Rect((event.x - dx).toInt(), (event.y - dy).toInt(), (event.x + dx).toInt(), (event.y + dy).toInt())
            if (touchRect.left < 0) {
                touchRect.left = 0
            }
            if (touchRect.top < 0) {
                touchRect.top = 0
            }
            if (touchRect.right > textureSize!!.x) {
                touchRect.right = textureSize!!.x
            }
            if (touchRect.bottom > textureSize!!.y) {
                touchRect.bottom = textureSize!!.y
            }
            val focusArea = getCameraFocusArea(touchRect)
            focusArea?.let {
                cameraController?.focusOn(it)
            }
            return true
        }
        return false
    }

    open fun onTouchScale(scaleGestureDetector: ScaleGestureDetector) : Boolean {
        val scaleFactor = scaleGestureDetector.scaleFactor
        Log.i(TAG, "onTouchScale $scaleFactor")
        cameraController?.zoom(scaleFactor)
        return true
    }

    fun getDisplayRotation() : Int {
        return when (display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    fun useFrontCamera(useFrontCamera: Boolean) {
        if (this.useFrontCamera == useFrontCamera) return
        this.useFrontCamera = useFrontCamera
        cameraController?.useFrontCamera(useFrontCamera)
    }

    fun openFlash(openFlash: Boolean) {
        if (this.openFlash == openFlash) return
        this.openFlash = openFlash
        cameraController?.openFlash(openFlash)
    }

    fun getCameraRotation() : Int {
        return cameraController?.getSensorRotation() ?: 0
    }

    fun getCameraFocusArea(viewRect: Rect) : Rect? {
        val focusRegion = cameraController?.getFocusRegion()
        if (focusRegion != null && bestPreviewSize != null) {
            Log.i(TAG, "focusRegion $focusRegion")
            var focusArea = RectF(focusRegion)
            val matrix = Matrix()
            getPreviewMatrix().invert(matrix)
            matrix.mapRect(focusArea, RectF(viewRect))
            val dx = (focusRegion.width() - bestPreviewSize!!.x) / 2
            val dy = (focusRegion.height() - bestPreviewSize!!.y) / 2
            focusArea = RectF(focusArea.left + dx, focusArea.top + dy, focusArea.right + dx, focusArea.bottom + dy)
            Log.i(TAG, "viewRect $viewRect, focusRect $focusArea")
            return Rect(focusArea.left.toInt(), focusArea.top.toInt(), focusArea.right.toInt(), focusArea.bottom.toInt())
        }
        return null
    }

    fun getPreviewMatrix() : Matrix {
        var matrix = Matrix()
        if (bestPreviewSize != null && textureSize != null) {
            val previewRect = RectF(0f, 0f, bestPreviewSize!!.x.toFloat(), bestPreviewSize!!.y.toFloat())
            val viewRect = RectF(0f, 0f, textureSize!!.x.toFloat(), textureSize!!.y.toFloat())
            val rotation = if (useFrontCamera) {
                (360 - (getCameraRotation() + getDisplayRotation()) % 360) % 360
            } else {
                (360 + getCameraRotation() - getDisplayRotation()) % 360
            }
            Log.i(TAG, "getPreviewMatrix, cameraRotation ${getCameraRotation()}, displayRotation ${getDisplayRotation()}, rotation $rotation")
            Log.i(TAG, "getPreviewMatrix, previewRect:$previewRect, viewRect:$viewRect")
            var scaleX = viewRect.width() / previewRect.width()
            var scaleY = viewRect.height() / previewRect.height()
            if (rotation % 180 != 0) {
                scaleX = viewRect.width() / previewRect.height()
                scaleY = viewRect.height() / previewRect.width()
            }
            Log.i(TAG, "scaleX $scaleX, scaleY $scaleY, previewRect $previewRect")
            val srcPoints = floatArrayOf(previewRect.left, previewRect.top, previewRect.right, previewRect.bottom)
            val destPoints = floatArrayOf(viewRect.left, viewRect.top, viewRect.right, viewRect.bottom)
            if (scaleX > scaleY) {
                srcPoints[0] = previewRect.left
                srcPoints[1] = previewRect.height() * (scaleX - scaleY) / 2
                srcPoints[2] = previewRect.right
                srcPoints[3] = previewRect.height() - srcPoints[1]
            } else if (scaleY > scaleX) {
                srcPoints[0] = previewRect.width() * (scaleY - scaleX) / 2
                srcPoints[1] = previewRect.top
                srcPoints[2] = previewRect.width() - srcPoints[0]
                srcPoints[3] = previewRect.bottom
            }
            when (rotation) {
                90 -> {
                    destPoints[0] = viewRect.right
                    destPoints[1] = viewRect.top
                    destPoints[2] = viewRect.left
                    destPoints[3] = viewRect.bottom
                }
                180 -> {
                    destPoints[0] = viewRect.right
                    destPoints[1] = viewRect.bottom
                    destPoints[2] = viewRect.left
                    destPoints[3] = viewRect.top
                }
                270 -> {
                    destPoints[0] = viewRect.left
                    destPoints[1] = viewRect.bottom
                    destPoints[2] = viewRect.right
                    destPoints[3] = viewRect.top
                }
            }
            matrix.setPolyToPoly(srcPoints, 0, destPoints, 0, 2)
            if (useFrontCamera) {
                matrix.preTranslate(previewRect.width(), 0f)
                matrix.preScale(-1f, 1f)
            }
        }
        return matrix
    }

    private fun computeDisplayMatrix(): Matrix {
        val matrix = Matrix()
        val displayRotation = getDisplayRotation()
        val cameraRotation = cameraController?.getSensorRotation() ?: 0
        Log.i(TAG, "displayRotation:$displayRotation, cameraRotation:$cameraRotation")
        if (textureSize != null && bestPreviewSize != null) {
            var previewWidth = bestPreviewSize!!.x.toFloat()
            var previewHeight = bestPreviewSize!!.y.toFloat()
            if ( cameraRotation % 180 != 0 && !isLandscape()) {
                previewWidth = previewHeight.also { previewHeight = previewWidth }
            }
            Log.i(TAG, "previewWidth:$previewWidth, previewHeight:$previewHeight")
            val scaleX = textureSize!!.x / previewWidth
            val scaleY = textureSize!!.y / previewHeight
            Log.i(TAG, "computeDisplayMatrix, scaleX:$scaleX, scaleY:$scaleY")
            var preScaleX = previewWidth / textureSize!!.x
            var preScaleY = previewHeight / textureSize!!.y
            matrix.postRotate((360 - displayRotation).toFloat(), textureSize!!.x / 2.0f, textureSize!!.y / 2.0f)
            if (displayRotation % 180 != 0) {
                matrix.postScale(textureSize!!.x.toFloat() / textureSize!!.y, textureSize!!.y.toFloat() / textureSize!!.x, textureSize!!.x / 2.0f, textureSize!!.y / 2.0f)
                preScaleX = preScaleY.also { preScaleY = preScaleX }
            }
            val scale = scaleX.coerceAtLeast(scaleY)
            //默认是按照 fitXY 来缩放的, 要先缩放还原回来
            matrix.preScale(preScaleX, preScaleY, textureSize!!.x / 2.0f, textureSize!!.y / 2.0f)
            //缩放
            matrix.postScale(scale, scale, textureSize!!.x / 2.0f, textureSize!!.y / 2.0f)
        }
        return matrix
    }

    private fun tryStartPreview() {
        if (surfaceTexture != null && bestPreviewSize != null) {
            surfaceTexture!!.setDefaultBufferSize(bestPreviewSize!!.x, bestPreviewSize!!.y)
            textureView.setTransform(computeDisplayMatrix())
            cameraController?.onSurfaceCreated(Surface(surfaceTexture), bestPreviewSize!!)
        }
    }



    @SuppressLint("Recycle")
    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceTextureAvailable, width:$width, height:$height")
        textureSize = Point(width, height)
        bestPreviewSize = cameraController?.getBestPreviewSize(textureSize!!, isLandscape())
        this.surfaceTexture = surfaceTexture
        tryStartPreview()
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceTextureSizeChanged, width:$width, height:$height")
        textureSize = Point(width, height)
        bestPreviewSize = cameraController?.getBestPreviewSize(textureSize!!, isLandscape())
        this.surfaceTexture = surfaceTexture
        tryStartPreview()
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        Log.i(TAG, "onSurfaceTextureDestroyed")
        cameraController?.onSurfaceDestroyed()
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
    }
}