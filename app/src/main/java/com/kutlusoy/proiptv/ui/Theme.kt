package com.kutlusoy.proiptv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Palette {
    val Velvet = Color(0xFF0B0709)
    val Surface = Color(0xFF151013)
    val SurfaceHigh = Color(0xFF211619)
    val Wine = Color(0xFFB3213A)
    val WineDeep = Color(0xFF7A1226)
    val WineSoft = Color(0xFFE05A70)
    val Text = Color(0xFFF3EBED)
    val Outline = Color(0xFF4A3A3F)
}

private val Colors = darkColorScheme(
    primary = Palette.Wine,
    onPrimary = Color.White,
    primaryContainer = Palette.WineDeep,
    onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = Palette.WineSoft,
    onSecondary = Color.White,
    secondaryContainer = Palette.WineDeep,
    onSecondaryContainer = Color(0xFFFFD9DE),
    tertiary = Palette.WineSoft,
    background = Palette.Velvet,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Color(0xFFCDBFC3),
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    outline = Palette.Outline,
    outlineVariant = Color(0xFF33272B),
    error = Color(0xFFFF8A80),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
)

fun isTelevision(context: Context): Boolean {
    val modes = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return modes.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        !context.packageManager.hasSystemFeature("android.hardware.touchscreen")
}

@Composable
fun ProIptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, shapes = AppShapes, content = content)
}
