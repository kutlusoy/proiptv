package com.kutlusoy.proiptv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// The round controls of the player and their symbols. Android and Windows use this one file, so both look the same (see syncShared in desktop/build.gradle).

enum class PlayerGlyph { PLAY, PAUSE, STOP, UP, DOWN, REC, REC_STOP, STAR_ON, STAR_OFF, MENU, GEAR, GUIDE }

@Composable
fun RoundControl(glyph: PlayerGlyph, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, diameter: androidx.compose.ui.unit.Dp = 76.dp) {
    var focused by remember { mutableStateOf(false) }
    Box(
        // required: a crowded row must never squeeze a round control into an oval
        modifier.requiredSize(diameter).onFocusChanged { focused = it.hasFocus }
            .background(Color(0xCC000000), CircleShape)
            .border(if (focused) 3.dp else 1.5.dp, if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f), CircleShape)
            .semantics { contentDescription = description }
            // no square focus frame: the round border shows the focus
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter * 0.4f)) {
            val w = size.width
            val h = size.height
            when (glyph) {
                PlayerGlyph.PLAY -> drawPath(Path().apply { moveTo(w * 0.12f, 0f); lineTo(w, h / 2); lineTo(w * 0.12f, h); close() }, Color.White)
                PlayerGlyph.PAUSE -> {
                    drawRect(Color.White, Offset(w * 0.08f, 0f), Size(w * 0.28f, h))
                    drawRect(Color.White, Offset(w * 0.64f, 0f), Size(w * 0.28f, h))
                }
                PlayerGlyph.REC -> drawCircle(Color(0xFFFF3B30), radius = w / 2)
                PlayerGlyph.REC_STOP -> drawRoundRect(Color(0xFFFF3B30), Offset(w * 0.06f, h * 0.06f), Size(w * 0.88f, h * 0.88f), androidx.compose.ui.geometry.CornerRadius(w * 0.12f))
                PlayerGlyph.STAR_ON, PlayerGlyph.STAR_OFF -> {
                    val cx = w / 2
                    val cy = h / 2
                    val star = Path().apply {
                        for (k in 0 until 10) {
                            val r = if (k % 2 == 0) w / 2 else w / 4.4f
                            val a = Math.PI / 5 * k - Math.PI / 2
                            val x = cx + (r * Math.cos(a)).toFloat()
                            val y = cy + (r * Math.sin(a)).toFloat()
                            if (k == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    if (glyph == PlayerGlyph.STAR_ON) drawPath(star, Color(0xFFFFC107))
                    else drawPath(star, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f))
                }
                PlayerGlyph.MENU -> for (k in 0..2) drawRoundRect(Color.White, Offset(0f, h * (0.08f + 0.36f * k)), Size(w, h * 0.14f), androidx.compose.ui.geometry.CornerRadius(h * 0.07f))
                PlayerGlyph.UP -> drawPath(Path().apply { moveTo(w / 2, 0f); lineTo(w, h * 0.85f); lineTo(0f, h * 0.85f); close() }, Color.White)
                PlayerGlyph.DOWN -> drawPath(Path().apply { moveTo(0f, h * 0.15f); lineTo(w, h * 0.15f); lineTo(w / 2, h); close() }, Color.White)
                PlayerGlyph.STOP -> drawRoundRect(Color.White, Offset(w * 0.04f, h * 0.04f), Size(w * 0.92f, h * 0.92f), androidx.compose.ui.geometry.CornerRadius(w * 0.1f))
                PlayerGlyph.GEAR -> {
                    // a gear: eight teeth around a ring
                    val c = Offset(w / 2, h / 2)
                    for (k in 0 until 8) {
                        rotate(k * 45f, c) {
                            drawRoundRect(Color.White, Offset(w * 0.4f, 0f), Size(w * 0.2f, h * 0.24f), androidx.compose.ui.geometry.CornerRadius(w * 0.04f))
                        }
                    }
                    drawCircle(Color.White, radius = w * 0.34f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.16f))
                }
                PlayerGlyph.GUIDE -> {
                    // a calendar: frame, header bar, two rings
                    drawRoundRect(Color.White, Offset(w * 0.04f, h * 0.14f), Size(w * 0.92f, h * 0.82f), androidx.compose.ui.geometry.CornerRadius(w * 0.1f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f))
                    drawRect(Color.White, Offset(w * 0.04f, h * 0.34f), Size(w * 0.92f, h * 0.09f))
                    drawRoundRect(Color.White, Offset(w * 0.26f, 0f), Size(w * 0.1f, h * 0.3f), androidx.compose.ui.geometry.CornerRadius(w * 0.04f))
                    drawRoundRect(Color.White, Offset(w * 0.64f, 0f), Size(w * 0.1f, h * 0.3f), androidx.compose.ui.geometry.CornerRadius(w * 0.04f))
                }
            }
        }
    }
}

