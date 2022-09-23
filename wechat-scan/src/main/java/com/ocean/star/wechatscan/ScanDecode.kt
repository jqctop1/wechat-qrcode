package com.ocean.star.wechatscan

import android.graphics.Rect
import android.media.Image


open class DecodeResult(val text: String, val rect: Rect) {

    override fun toString(): String {
        return "(text:$text, rect:$rect)"
    }
}

interface DecodeCallback {
    fun onResult(resultList: List<DecodeResult>)
    fun onNextDecode()
    fun onCloseImage(image: Image)
}