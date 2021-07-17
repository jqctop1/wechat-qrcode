package com.ocean.star.wechatqrcode

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ocean.star.wechatqrcode.databinding.ScanActivityBinding
import com.ocean.star.wechatscan.ScanQRCodeView
import com.ocean.star.wechatscan.WeChatQRCodeDetector

class ScanActivity : AppCompatActivity(), ScanQRCodeView.ScanCallback {


    private lateinit var scanActivityBinding: ScanActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanActivityBinding = ScanActivityBinding.inflate(layoutInflater)
        setContentView(scanActivityBinding.root)
        scanActivityBinding.scanView.onCreate()
        scanActivityBinding.switchCameraBtn.setOnClickListener {
            val front = scanActivityBinding.scanView.useFrontCamera
            scanActivityBinding.scanView.useFrontCamera(!front)
        }
        scanActivityBinding.switchFlashBtn.setOnClickListener {
            scanActivityBinding.scanView.openFlash(!scanActivityBinding.scanView.openFlash)
        }
        scanActivityBinding.scanView.scanCallback = this
    }

    override fun onScanResult(resultList: List<WeChatQRCodeDetector.DecodeResult>) {
        scanActivityBinding.scanView.stopScan()
        AlertDialog.Builder(this)
            .setMessage(resultList.first().text)
            .setNegativeButton("Cancel Scan", null)
            .setPositiveButton("Continue Scan") { _, _ ->
                scanActivityBinding.scanView.startScan()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        scanActivityBinding.scanView.onResume()
    }

    override fun onStop() {
        super.onStop()
        scanActivityBinding.scanView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanActivityBinding.scanView.onDestroy()
    }
}