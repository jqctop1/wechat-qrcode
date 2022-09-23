package com.ocean.star.wechatscan

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup

class ScanDetectQRCodeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : ScanQRCodeView(context, attrs, defStyleAttr) {

    private val TAG = "ScanDetectQRCodeView"

    private val qrCodeRectView: QRCodeRectView

    init {
        qrCodeRectView = QRCodeRectView(context)
        addView(qrCodeRectView, 1, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun onResult(resultList: List<DecodeResult>) {
        val matrix = getPreviewMatrix()
        qrCodeRectView.drawQRCode(resultList.map {
            val viewRect = RectF()
            if (it.rect.width() > 0 && it.rect.height() > 0) {
                matrix.mapRect(viewRect, RectF(it.rect))
                Log.d(TAG, "${it.rect} -> $viewRect")
            }
            viewRect
        })
        super.onResult(resultList)
    }

    fun clearQRCode() {
        qrCodeRectView.drawQRCode(emptyList())
    }
}