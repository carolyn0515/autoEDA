package com.example.autoeda

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 1.5초 뒤 UploadActivity로 이동
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, UploadActivity::class.java)
            startActivity(intent)
            finish()   // 뒤로가기 눌러도 스플래시로 안 돌아오게
        }, 1500)
    }
}
