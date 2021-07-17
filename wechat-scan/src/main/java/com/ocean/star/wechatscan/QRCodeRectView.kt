package com.ocean.star.wechatscan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View

class QRCodeRectView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    private val TAG = "QRCodeRectView"

    private val codeRectList = ArrayList<RectF>()

    private val paint = Paint().apply {
        color = Color.GREEN
        isAntiAlias = true
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    fun drawQRCode(rects: List<RectF>) {
        codeRectList.clear()
        codeRectList.addAll(rects)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        codeRectList.forEach {
            Log.i(TAG, "draw rect $it")
            canvas.drawRect(it, paint)
        }
    }

}