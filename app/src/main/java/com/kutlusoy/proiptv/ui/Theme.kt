package com.kutlusoy.proiptv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF4F8CFF)

private val DarkColors = darkColorScheme(
    primary = Accent,
    background = Color(0xFF0B0F1A),
    surface = Color(0xFF121826),
    onBackground = Color(0xFFE8ECF4),
    onSurface = Color(0xFFE8ECF4),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FD6),
    background = Color(0xFFF6F8FC),
    surface = Color(0xFFFFFFFF),
)

fun isTelevision(context: Context): Boolean {
    val modes = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return modes.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        !context.packageManager.hasSystemFeature("android.hardware.touchscreen")
}

@Composable
fun ProIptvTheme(forceDark: Boolean, content: @Composable () -> Unit) {
    val dark = forceDark || isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
