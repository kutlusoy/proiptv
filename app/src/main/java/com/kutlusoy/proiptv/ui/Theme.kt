package com.kutlusoy.proiptv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Palette {
    val Velvet = Color(0xFF0B0709)
    val Surface = Color(0xFF151013)
    val SurfaceHigh = Color(0xFF211619)
    val Wine = Color(0xFFC40035)
    val WineDeep = Color(0xFF7D0F25)
    val WineSoft = Color(0xFFF2506F)
    val Text = Color(0xFFF3EBED)
    val Outline = Color(0xFF4A3A3F)
}

private val Colors = darkColorScheme(
    primary = Color(0xFFE23A60),
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
    outline = Color(0xFF8F7A81),
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

val LocalTv = staticCompositionLocalOf { false }

const val UI_AUTO = 0
const val UI_TV = 1
const val UI_TOUCH = 2

fun useTvLayout(mode: Int, system: Boolean): Boolean = when (mode) {
    UI_TV -> true
    UI_TOUCH -> false
    else -> system
}

private fun tvTypography(): Typography {
    val t = Typography()
    return t.copy(
        bodySmall = t.bodySmall.copy(fontSize = 15.sp, lineHeight = 20.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
        labelSmall = t.labelSmall.copy(fontSize = 14.sp),
        labelLarge = t.labelLarge.copy(fontSize = 17.sp),
        titleMedium = t.titleMedium.copy(fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = t.titleLarge.copy(fontSize = 26.sp),
    )
}

/**
 * A clear focus ring for the remote control: the element with the focus gets a bright frame and a light fill, on every button,
 * list row and menu entry, also in dialogs. Without it the default focus look of the buttons is hardly visible on a dark TV screen.
 */
private object FocusRingIndication : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode =
        Node(interactionSource)

    override fun equals(other: Any?) = other === this
    override fun hashCode() = -1

    private class Node(private val source: androidx.compose.foundation.interaction.InteractionSource) :
        androidx.compose.ui.Modifier.Node(), androidx.compose.ui.node.DrawModifierNode {
        private var focused = false
        private var pressed = false

        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect { i ->
                    when (i) {
                        is androidx.compose.foundation.interaction.FocusInteraction.Focus -> focused = true
                        is androidx.compose.foundation.interaction.FocusInteraction.Unfocus -> focused = false
                        is androidx.compose.foundation.interaction.PressInteraction.Press -> pressed = true
                        is androidx.compose.foundation.interaction.PressInteraction.Release, is androidx.compose.foundation.interaction.PressInteraction.Cancel -> pressed = false
                    }
                    invalidateDraw()
                }
            }
        }

        override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.draw() {
            drawContent()
            if (focused || pressed) {
                val radius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                drawRoundRect(Color.White.copy(alpha = if (pressed) 0.25f else 0.14f), cornerRadius = radius)
                if (focused) {
                    val stroke = 3.dp.toPx()
                    drawRoundRect(
                        Color.White, topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke), cornerRadius = radius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                    )
                }
            }
        }
    }
}

@Composable
fun ProIptvTheme(tv: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, shapes = AppShapes, typography = if (tv) tvTypography() else Typography()) {
        if (tv) {
            androidx.compose.runtime.CompositionLocalProvider(LocalTv provides true, androidx.compose.foundation.LocalIndication provides FocusRingIndication, content = content)
        } else {
            androidx.compose.runtime.CompositionLocalProvider(LocalTv provides false, content = content)
        }
    }
}
