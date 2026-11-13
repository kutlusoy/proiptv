package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.sync.GuidePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidePolicyTest {
    private val now = 1_000_000L
    private val hour = 3600L

    @Test
    fun neverLoadedIsDue() = assertTrue(GuidePolicy.isDue(now, 0, null))

    @Test
    fun freshWithGoodCoverageIsNotDue() = assertFalse(GuidePolicy.isDue(now, now - hour, now + 24 * hour))

    @Test
    fun oldIsDueEvenWithCoverage() = assertTrue(GuidePolicy.isDue(now, now - 7 * hour, now + 48 * hour))

    @Test
    fun shortCoverageIsDueAfterMinRetry() {
        assertFalse(GuidePolicy.isDue(now, now - 30 * 60, now + 2 * hour))
        assertTrue(GuidePolicy.isDue(now, now - 2 * hour, now + 2 * hour))
    }

    @Test
    fun emptyGuideIsDueAfterMinRetry() {
        assertFalse(GuidePolicy.isDue(now, now - 10 * 60, null))
        assertTrue(GuidePolicy.isDue(now, now - 2 * hour, null))
    }
}
