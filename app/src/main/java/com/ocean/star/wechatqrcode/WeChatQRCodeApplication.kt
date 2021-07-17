package com.ocean.star.wechatqrcode

import android.app.Application
import android.util.Log

class WeChatQRCodeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("WeChatQRCodeApplication", "onCreate")
    }
}