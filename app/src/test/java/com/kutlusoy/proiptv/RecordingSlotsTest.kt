package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.engine.RecordingSlot
import com.kutlusoy.proiptv.engine.RecordingSlots
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSlotsTest {
    private fun slot(channel: String, from: Long, to: Long) = RecordingSlot(channel, from, to)

    @Test
    fun oneConnectionAllowsNoOverlapOfDifferentChannels() {
        assertFalse(RecordingSlots.fits(1, slot("a", 10, 20), listOf(slot("b", 15, 30))))
        assertTrue(RecordingSlots.fits(1, slot("a", 10, 20), listOf(slot("b", 20, 30))))
        assertTrue(RecordingSlots.fits(1, slot("a", 10, 20), listOf(slot("b", 0, 10))))
    }

    @Test
    fun sameChannelSharesTheConnection() {
        assertTrue(RecordingSlots.fits(1, slot("a", 10, 20), listOf(slot("a", 5, 15))))
    }

    @Test
    fun twoConnectionsAllowTwoAtOnceButNotThree() {
        assertTrue(RecordingSlots.fits(2, slot("a", 10, 20), listOf(slot("b", 5, 30))))
        assertFalse(RecordingSlots.fits(2, slot("a", 10, 20), listOf(slot("b", 5, 30), slot("c", 12, 18))))
    }

    @Test
    fun recordingsThatNeverRunTogetherFit() {
        assertTrue(RecordingSlots.fits(2, slot("a", 0, 100), listOf(slot("b", 0, 40), slot("c", 50, 100))))
    }
}
