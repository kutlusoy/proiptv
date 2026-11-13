package com.kutlusoy.proiptv.ui.splash

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

data class SplashFrame(
    val markAlpha: Float,
    val markOffsetY: Float,
    val markScale: Float,
    val glowWide: Float,
    val glowNear: Float,
    val sheenAlpha: Float,
    val sheenOffsetX: Float,
    val titleAlpha: Float,
    val titleOffsetY: Float,
    val subtitleAlpha: Float,
    val subtitleOffsetY: Float,
    val sparkAlpha: Float,
    val sparkScale: Float,
)

class CubicBezier(private val x1: Float, private val y1: Float, private val x2: Float, private val y2: Float) {
    fun ease(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var t = x
        repeat(6) {
            val error = sampleX(t) - x
            val slope = slopeX(t)
            if (abs(slope) < 1e-6f) return@repeat
            t = (t - error / slope).coerceIn(0f, 1f)
        }
        return sampleY(t)
    }

    private fun sampleX(t: Float) = 3 * x1 * t * (1 - t) * (1 - t) + 3 * x2 * t * t * (1 - t) + t * t * t
    private fun sampleY(t: Float) = 3 * y1 * t * (1 - t) * (1 - t) + 3 * y2 * t * t * (1 - t) + t * t * t
    private fun slopeX(t: Float) = 3 * x1 * (1 - t) * (1 - t) + 6 * (x2 - x1) * (1 - t) * t + 3 * (1 - x2) * t * t
}

object SplashTimeline {
    const val DURATION_MS = 4500L
    const val HOLD_MS = 500L
    const val PUSH_IN = 0.045f

    private const val BREATH_PERIOD_MS = 2400f
    private const val BREATH_START = 0.62f
    private val ease = CubicBezier(0.22f, 0.72f, 0.25f, 1f)
    private val settle = CubicBezier(0.3f, 0f, 0.2f, 1f)
    private val easeInOut = CubicBezier(0.42f, 0f, 0.58f, 1f)

    private class Track(private val easing: CubicBezier, vararg keys: Pair<Float, Float>) {
        private val keys = keys

        fun at(progress: Float): Float {
            if (progress <= keys.first().first) return keys.first().second
            if (progress >= keys.last().first) return keys.last().second
            val index = keys.indexOfLast { it.first <= progress }
            val (startTime, startValue) = keys[index]
            val (endTime, endValue) = keys[index + 1]
            return startValue + (endValue - startValue) * easing.ease((progress - startTime) / (endTime - startTime))
        }
    }

    private val markAlpha = Track(ease, 0f to 0f, 0.05f to 0f, 0.23f to 1f, 1f to 1f)
    private val markOffsetY = Track(settle, 0f to 18f, 0.05f to 18f, 0.5f to 0f, 1f to 0f)
    private val markScale = Track(settle, 0f to 0.9f, 0.05f to 0.9f, 0.5f to 1f, 1f to 1f)
    private val glow = Track(ease, 0f to 0f, 0.05f to 0f, 0.22f to 0.8f, 0.38f to 1f, 0.62f to 0.9f, 1f to 0.9f)
    private val sheenAlpha = Track(ease, 0f to 0f, 0.19f to 0f, 0.27f to 1f, 0.46f to 0.75f, 0.57f to 0f, 1f to 0f)
    private val sheenOffset = Track(easeInOut, 0f to -260f, 0.19f to -260f, 0.57f to 260f, 1f to 260f)
    private val titleAlpha = Track(ease, 0f to 0f, 0.34f to 0f, 0.57f to 1f, 1f to 1f)
    private val titleOffset = Track(ease, 0f to 20f, 0.34f to 20f, 0.57f to 0f, 1f to 0f)
    private val subtitleAlpha = Track(ease, 0f to 0f, 0.45f to 0f, 0.66f to 1f, 1f to 1f)
    private val subtitleOffset = Track(ease, 0f to 13f, 0.45f to 13f, 0.66f to 0f, 1f to 0f)
    private val sparkAlpha = Track(ease, 0f to 0f, 0.36f to 0f, 0.45f to 0.95f, 0.60f to 0f, 1f to 0f)
    private val sparkScale = Track(ease, 0f to 0.6f, 0.36f to 0.6f, 0.45f to 1.1f, 0.60f to 0.8f, 1f to 0.8f)

    /** [extraMs] is the time after the end of the intro, while the splash screen is held. */
    fun frame(progress: Float, extraMs: Float = 0f): SplashFrame {
        val p = progress.coerceIn(0f, 1f)
        val elapsedMs = p * DURATION_MS + extraMs
        val breathWeight = ((p - BREATH_START) / 0.1f).coerceIn(0f, 1f)
        val breath = 1f + 0.08f * breathWeight * sin(2.0 * PI * elapsedMs / BREATH_PERIOD_MS).toFloat()
        val sheen = sheenAlpha.at(p)
        val glowBase = glow.at(p)
        return SplashFrame(
            markAlpha = markAlpha.at(p),
            markOffsetY = markOffsetY.at(p),
            markScale = markScale.at(p),
            glowWide = (glowBase * breath).coerceIn(0f, 1f),
            glowNear = (glowBase * 0.9f * breath + 0.3f * sheen).coerceIn(0f, 1f),
            sheenAlpha = sheen,
            sheenOffsetX = sheenOffset.at(p),
            titleAlpha = titleAlpha.at(p),
            titleOffsetY = titleOffset.at(p),
            subtitleAlpha = subtitleAlpha.at(p),
            subtitleOffsetY = subtitleOffset.at(p),
            sparkAlpha = sparkAlpha.at(p),
            sparkScale = sparkScale.at(p),
        )
    }
}
