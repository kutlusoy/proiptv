package com.kutlusoy.proiptv.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.kutlusoy.proiptv.ui.splash.SplashScreen

class SplashActivity : AppCompatActivity() {
    private var finished = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersive()
        setContent {
            ProIptvTheme {
                SplashScreen(onDone = ::proceed)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        proceed()
        return true
    }

    private fun proceed() {
        if (finished) return
        finished = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
