package com.kutlusoy.proiptv.ui.splash

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.ui.Palette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val DESIGN_WIDTH = 900f
private const val DESIGN_HEIGHT = 800f
private const val MARK_HEIGHT = 380f
private const val TITLE_WIDTH = 560f
private const val GLOW_TEXTURE_MARK_HEIGHT = 360f

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val reduced = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var progress by remember { mutableFloatStateOf(if (reduced) 1f else 0f) }
    var extraMs by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        if (reduced) {
            delay(1_200)
            onDone()
            return@LaunchedEffect
        }
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val ms = (now - start) / 1_000_000f
            progress = (ms / SplashTimeline.DURATION_MS).coerceAtMost(1f)
            extraMs = (ms - SplashTimeline.DURATION_MS).coerceAtLeast(0f)
            if (extraMs >= SplashTimeline.HOLD_MS) {
                onDone()
                break
            }
        }
    }

    val mark = ImageBitmap.imageResource(R.drawable.splash_mark)
    val title = ImageBitmap.imageResource(R.drawable.splash_title)
    val glowWide = ImageBitmap.imageResource(R.drawable.splash_glow_wide)
    val glowNear = ImageBitmap.imageResource(R.drawable.splash_glow_near)
    val spark = ImageBitmap.imageResource(R.drawable.splash_spark)
    val particles = remember { SplashParticles.create(46, 11) }
    val frame = SplashTimeline.frame(progress, extraMs)
    val elapsedSeconds = (progress * SplashTimeline.DURATION_MS + extraMs) / 1000f

    Canvas(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
    ) {
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFF1C0A10), Palette.Velvet, Color.Black),
                center = Offset(size.width / 2, size.height * 0.45f), radius = max(size.width, size.height) * 0.8f,
            ),
        )
        val unit = min(size.width / DESIGN_WIDTH, size.height / DESIGN_HEIGHT)
        val center = Offset(size.width / 2, size.height / 2)
        withTransform({ scale(1f + SplashTimeline.PUSH_IN * progress, 1f + SplashTimeline.PUSH_IN * progress, pivot = center) }) {
            drawParticles(particles, elapsedSeconds, frame, size)
            drawLockup(frame, unit, mark, title, glowWide, glowNear, spark)
        }
    }
}

private fun DrawScope.drawLockup(
    frame: SplashFrame, unit: Float,
    mark: ImageBitmap, title: ImageBitmap, glowWide: ImageBitmap, glowNear: ImageBitmap, spark: ImageBitmap,
) {
    val markH = MARK_HEIGHT * unit
    val markW = markH * mark.width / mark.height
    val titleW = TITLE_WIDTH * unit
    val titleH = titleW * title.height / title.width
    val gap = 40f * unit
    val total = markH + gap + titleH
    val top = size.height / 2 - total / 2
    val markLeft = size.width / 2 - markW / 2
    val markTop = top + frame.markOffsetY * unit
    val markCenter = Offset(size.width / 2, markTop + markH / 2)

    fun image(bitmap: ImageBitmap, centerX: Float, centerY: Float, width: Float, height: Float, alpha: Float) {
        drawImage(
            bitmap, dstOffset = IntOffset((centerX - width / 2).toInt(), (centerY - height / 2).toInt()),
            dstSize = IntSize(width.toInt(), height.toInt()), alpha = alpha.coerceIn(0f, 1f), filterQuality = FilterQuality.High,
        )
    }

    val glowScale = markH / GLOW_TEXTURE_MARK_HEIGHT
    image(glowWide, markCenter.x, markCenter.y, glowWide.width * glowScale, glowWide.height * glowScale, frame.glowWide * 0.75f)
    image(glowNear, markCenter.x, markCenter.y, glowNear.width * glowScale, glowNear.height * glowScale, frame.glowNear * 0.8f)

    withTransform({ scale(frame.markScale, frame.markScale, pivot = markCenter) }) {
        val bounds = Rect(markLeft, markTop, markLeft + markW, markTop + markH)
        drawContext.canvas.saveLayer(bounds, Paint())
        image(mark, markCenter.x, markCenter.y, markW, markH, frame.markAlpha)
        if (frame.sheenAlpha > 0.01f) {
            val band = 150f * unit
            val x = markCenter.x + frame.sheenOffsetX * unit
            drawRect(
                brush = Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.6f * frame.sheenAlpha), Color.Transparent),
                    start = Offset(x - band, markTop), end = Offset(x + band, markTop + markH * 0.55f),
                ),
                topLeft = bounds.topLeft, size = Size(bounds.width, bounds.height), blendMode = BlendMode.SrcAtop,
            )
        }
        drawContext.canvas.restore()
    }

    if (frame.sparkAlpha > 0.01f) {
        val s = 170f * unit * frame.sparkScale
        image(spark, markLeft + markW * 0.74f, markTop + markH * 0.2f, s, s, frame.sparkAlpha)
    }

    val titleTop = top + markH + gap + frame.titleOffsetY * unit
    image(title, size.width / 2, titleTop + titleH / 2, titleW, titleH, frame.titleAlpha)
}

private fun DrawScope.drawParticles(particles: List<Particle>, elapsedSeconds: Float, frame: SplashFrame, size: Size) {
    val fade = ((elapsedSeconds - 0.3f) / 1.2f).coerceIn(0f, 1f)
    for (p in particles) {
        val twinkle = 0.65f + 0.35f * sin(elapsedSeconds * 2.2f + p.phase)
        val alpha = 0.55f * p.depth * twinkle * fade * (0.6f + 0.4f * frame.glowWide)
        drawCircle(Palette.WineSoft.copy(alpha = alpha.coerceIn(0f, 1f)), p.radius * density, Offset(p.x * size.width, SplashParticles.y(p, elapsedSeconds) * size.height))
    }
}
