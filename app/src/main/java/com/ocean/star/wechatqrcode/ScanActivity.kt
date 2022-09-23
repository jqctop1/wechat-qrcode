package com.ocean.star.wechatqrcode

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ocean.star.wechatqrcode.databinding.ScanActivityBinding
import com.ocean.star.wechatscan.DecodeResult
import com.ocean.star.wechatscan.ScanQRCodeView

class ScanActivity : AppCompatActivity(), ScanQRCodeView.ScanCallback {

    private val TAG = "ScanActivity"

    private lateinit var scanActivityBinding: ScanActivityBinding
    private var hasShowResult = false
    private var startScan = 0L

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
        startScan = System.currentTimeMillis()
    }

    override fun onScanResult(resultList: List<DecodeResult>) {
        scanActivityBinding.scanView.stopScan()
        if (!hasShowResult) {
            Toast.makeText(this, "scan result coast ${System.currentTimeMillis() - startScan}", Toast.LENGTH_SHORT).show()
            AlertDialog.Builder(this)
                .setMessage(resultList.first().text)
                .setNegativeButton("Cancel Scan", null)
                .setPositiveButton("Continue Scan") { _, _ ->
                    hasShowResult = false
                    startScan = System.currentTimeMillis()
                    scanActivityBinding.scanView.clearQRCode()
                    scanActivityBinding.scanView.startScan()
                }
                .show()
        }
        hasShowResult = true
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