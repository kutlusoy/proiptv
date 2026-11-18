package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import java.awt.MouseInfo
import java.awt.Rectangle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val MIN_WIDTH = 240

/** Size of the small window as a share of the screen area, with a 16:9 picture. */
/** Whether a window at these bounds can be seen on one of the screens (a monitor that was disconnected must not hide the window). */
fun onScreen(r: Rectangle): Boolean =
    runCatching {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any {
            val visible = it.defaultConfiguration.bounds.intersection(r)
            visible.width >= 160 && visible.height >= 80
        }
    }.getOrDefault(true)

fun BoundsDto.toRectangle() = Rectangle(x, y, width, height)

fun compactBounds(share: Double, current: Rectangle?): Rectangle {
    val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
    var w = (sqrt(share) * screen.width).toInt()
    var h = w * 9 / 16
    if (h > screen.height) { h = screen.height; w = h * 16 / 9 }
    w = max(w, MIN_WIDTH)
    h = w * 9 / 16
    val x = (current?.x ?: (screen.width - w - 24)).coerceIn(0, max(0, screen.width - w))
    val y = (current?.y ?: (screen.height - h - 64)).coerceIn(0, max(0, screen.height - h))
    return Rectangle(x, y, w, h)
}

/** The small borderless window: a strip to move it and to choose a size at the top (on hover), handles in the corners that keep the 16:9 proportion. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun FrameWindowScope.CompactOverlay(state: AppState) {
    var hover by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize()
            .onPointerEvent(PointerEventType.Enter) { hover = true }
            .onPointerEvent(PointerEventType.Move) { hover = true }
            .onPointerEvent(PointerEventType.Exit) { hover = false },
    ) {
        if (!hover) return@Box
        Row(Modifier.fillMaxWidth().align(Alignment.TopStart).background(Color(0xCC000000)), verticalAlignment = Alignment.CenterVertically) {
            var grabX = 0
            var grabY = 0
            Box(
                Modifier.weight(1f).pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            val p = MouseInfo.getPointerInfo()?.location
                            if (p != null) { grabX = p.x - window.x; grabY = p.y - window.y }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            MouseInfo.getPointerInfo()?.location?.let { window.setLocation(it.x - grabX, it.y - grabY) }
                        },
                    )
                },
            ) {
                Text(state.controller?.title.orEmpty(), Modifier.padding(horizontal = 10.dp, vertical = 8.dp), maxLines = 1, style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
            listOf("1/2" to 0.5, "1/4" to 0.25, "1/8" to 0.125, "1/16" to 0.0625).forEach { (label, share) ->
                TextButton(onClick = { window.bounds = compactBounds(share, window.bounds) }) { Text(label, style = MaterialTheme.typography.labelMedium) }
            }
            Tip(t("player_pin")) { IconButton(onClick = { state.pinned = !state.pinned }, Modifier.size(36.dp)) {
                Icon(Icons.Filled.PushPin, t("player_pin"), tint = if (state.pinned) Color(0xFFFF5252) else Color.White)
            } }
            Tip(t("player_restore")) { IconButton(onClick = { state.setCompact(false, window.bounds) }, Modifier.size(36.dp)) { Icon(Icons.Filled.CloseFullscreen, t("player_restore"), tint = Color.White) } }
        }
        for (corner in Corner.values()) ResizeHandle(corner, Modifier.align(corner.alignment))
    }
}

private enum class Corner(val alignment: Alignment, val right: Boolean, val bottom: Boolean) {
    TL(Alignment.TopStart, false, false), TR(Alignment.TopEnd, true, false), BL(Alignment.BottomStart, false, true), BR(Alignment.BottomEnd, true, true),
}

@Composable
private fun FrameWindowScope.ResizeHandle(corner: Corner, modifier: Modifier) {
    var anchorX = 0
    var anchorY = 0
    Box(
        modifier.size(22.dp).background(Color.White.copy(alpha = 0.35f)).pointerInput(corner) {
            detectDragGestures(
                onDragStart = {
                    val b = window.bounds
                    anchorX = if (corner.right) b.x else b.x + b.width
                    anchorY = if (corner.bottom) b.y else b.y + b.height
                },
                onDrag = { change, _ ->
                    change.consume()
                    val p = MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
                    val w = max(MIN_WIDTH, max(abs(p.x - anchorX), abs(p.y - anchorY) * 16 / 9))
                    val h = w * 9 / 16
                    window.bounds = Rectangle(if (corner.right) anchorX else anchorX - w, if (corner.bottom) anchorY else anchorY - h, w, h)
                },
            )
        },
    )
}
