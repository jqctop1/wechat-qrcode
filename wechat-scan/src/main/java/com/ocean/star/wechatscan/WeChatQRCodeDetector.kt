package com.ocean.star.wechatscan

import android.graphics.Rect

class WeChatQRCodeDetector {

    init {
        System.loadLibrary("c++_shared")
        System.loadLibrary("wechatqrcode")
    }

    private var id = 0

    fun initDetector(detectProto: String, detectModel: String, srProto: String, srModel: String) : Boolean {
        if (hasInit()) return true
        id = init(detectProto, detectModel, srProto, srModel)
        return id > 0
    }

    fun detectRGB(rgb: IntArray, width: Int, height: Int): List<DecodeResult> {
        val resultList = ArrayList<DecodeResult>();
        if (id > 0) {
            val results = detectRGB(id, rgb, width, height)
            if (results.isNotEmpty()) {
                resultList.addAll(results)
            }
        }
        return resultList
    }

    fun detectNV21(yuv: ByteArray, width: Int, height: Int) : List<DecodeResult> {
        val resultList = ArrayList<DecodeResult>();
        if (id > 0) {
            val results = detectNV21(id, yuv, width, height)
            if (results.isNotEmpty()) {
                resultList.addAll(results)
            }
        }
        return resultList
    }

    fun hasInit() : Boolean {
        return id > 0
    }

    fun releaseDetector() {
        if (hasInit()) {
            release(id)
        }
    }

    class DecodeResult(val text: String, val rect: Rect) {

        override fun toString(): String {
            return "(text:$text, rect:$rect)"
        }
    }

    private external fun init(detectProto: String, detectModel: String, srProto: String, srModel: String) : Int

    private external fun detectRGB(id: Int, rgb: IntArray, width: Int, height: Int) : Array<DecodeResult>

    private external fun detectNV21(id: Int, yuv: ByteArray, width: Int, height: Int) : Array<DecodeResult>

    private external fun release(id: Int)

}