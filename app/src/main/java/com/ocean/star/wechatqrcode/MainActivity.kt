package com.ocean.star.wechatqrcode

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.net.toFile
import com.ocean.star.wechatqrcode.databinding.MainActivityBinding
import com.ocean.star.wechatscan.FileDecodeQueue
import com.ocean.star.wechatscan.WeChatQRCodeDetector

class MainActivity : AppCompatActivity(), FileDecodeQueue.DecodeCallback {

    private val TAG = "MainActivity"

    
    private lateinit var mainActivityBinding: MainActivityBinding

    private val REQUEST_PERMISSION_READ_EXTERNAL = 0x100
    private val REQUEST_PERMISSION_CAMERA = 0x101

    private val REQUEST_CODE_SELECT_PICTURE = 0x102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainActivityBinding = MainActivityBinding.inflate(layoutInflater)
        setContentView(mainActivityBinding.root)
        mainActivityBinding.scanFile.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_PERMISSION_READ_EXTERNAL)
            } else {
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                }, REQUEST_CODE_SELECT_PICTURE)
            }
        }
        mainActivityBinding.scanPreview.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_PERMISSION_CAMERA)
            } else {
                startActivity(Intent(this, ScanActivity::class.java))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            REQUEST_PERMISSION_READ_EXTERNAL -> {
                val index = permissions.indexOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                if (index >= 0 && grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                    startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                    }, REQUEST_CODE_SELECT_PICTURE)
                }
            }
            REQUEST_PERMISSION_CAMERA -> {
                val index = permissions.indexOf(Manifest.permission.CAMERA)
                if (index >= 0 && grantResults[index] == PackageManager.PERMISSION_GRANTED) {
                    startActivity(Intent(this, ScanActivity::class.java))
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_CODE_SELECT_PICTURE && data != null) {
                data.data?.let {
                    val cursor = contentResolver.query(it, arrayOf(MediaStore.Images.ImageColumns.DATA),
                            null, null, null)
                    if (cursor?.moveToFirst() == true) {
                        val path = cursor.getString(0)
                        cursor.close();
                        if (!path.isNullOrEmpty()) {
                            FileDecodeQueue.getInstance(this).decode(path, this)
                        }
                    }
                }
            }
        }
    }

    override fun onResult(resultList: List<WeChatQRCodeDetector.DecodeResult>) {
        Log.i(TAG, "decode file result ${resultList.first().text}")
        runOnUiThread {
            Toast.makeText(this, resultList.first().text, Toast.LENGTH_SHORT).show()
        }
    }
}