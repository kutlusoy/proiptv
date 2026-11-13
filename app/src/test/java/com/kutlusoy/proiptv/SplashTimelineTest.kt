package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.ui.splash.SplashParticles
import com.kutlusoy.proiptv.ui.splash.SplashTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashTimelineTest {
    @Test
    fun startsEmptyAndEndsComplete() {
        val start = SplashTimeline.frame(0f)
        assertEquals(0f, start.markAlpha, 0f)
        assertEquals(0f, start.titleAlpha, 0f)
        val end = SplashTimeline.frame(1f)
        assertEquals(1f, end.markAlpha, 0f)
        assertEquals(1f, end.titleAlpha, 0f)
        assertEquals(0f, end.sheenAlpha, 0f)
        assertEquals(1f, end.markScale, 0f)
        assertEquals(0f, end.markOffsetY, 0f)
    }

    @Test
    fun elementsAppearInOrderAndNeverFlicker() {
        var markAlpha = 0f
        var titleAlpha = 0f
        for (i in 0..200) {
            val f = SplashTimeline.frame(i / 200f)
            assertTrue(f.markAlpha >= markAlpha - 1e-4f)
            assertTrue(f.titleAlpha >= titleAlpha - 1e-4f)
            markAlpha = f.markAlpha
            titleAlpha = f.titleAlpha
        }
        val mid = SplashTimeline.frame(0.4f)
        assertTrue(mid.markAlpha > mid.titleAlpha)
    }

    @Test
    fun glowStaysInRangeWhileHeld() {
        for (ms in 0..3000 step 100) {
            val f = SplashTimeline.frame(1f, ms.toFloat())
            assertTrue(f.glowWide in 0f..1f)
            assertTrue(f.glowNear in 0f..1f)
        }
    }

    @Test
    fun particlesAreDeterministicAndWrapAround() {
        val a = SplashParticles.create(30, 5)
        val b = SplashParticles.create(30, 5)
        assertEquals(a, b)
        for (p in a) for (t in listOf(0f, 1f, 10f, 100f)) {
            val y = SplashParticles.y(p, t)
            assertTrue(y >= 0f && y < 1f)
        }
    }
}
