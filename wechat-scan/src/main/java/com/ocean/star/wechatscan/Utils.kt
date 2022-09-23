package com.ocean.star.wechatscan

import android.media.Image


fun Image.getNV21() : ByteArray {
    val y = this.planes[0].buffer
    val u = this.planes[1].buffer
    val v = this.planes[2].buffer
    val width = this.width
    val height = this.height
    val rowStride = this.planes[0].rowStride
    val uPixelStride = this.planes[1].pixelStride
    val vPixelStride = this.planes[2].pixelStride
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
    return nv21
}