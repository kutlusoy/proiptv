package com.kutlusoy.proiptv.net

import com.kutlusoy.proiptv.parse.RecordingNames
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingNamesTest {
    private fun at(h: Int, m: Int) = Calendar.getInstance().apply { clear(); set(2026, Calendar.DECEMBER, 23, h, m) }.timeInMillis
    private val start = at(20, 15)

    @Test
    fun languageChannelShowDateAndStartTime() {
        assertEquals("DE-ORF 1-Tatort-2026.12.23-2015.ts", RecordingNames.fileName("DE | ORF 1", "Tatort", start) { false })
    }

    @Test
    fun leavesOutWhatIsMissingAndForbiddenCharacters() {
        assertEquals("kabel eins-2026.12.23-2015.ts", RecordingNames.fileName("kabel eins", null, start) { false })
        assertEquals("Das Beste Aus-Heute Show-2026.12.23-2015.ts", RecordingNames.fileName("Das Beste Aus", "Heute: Show?", start) { false })
    }

    @Test
    fun theStopTimeIsAddedWhenTheRecordingIsFinished() {
        val running = RecordingNames.fileName("DE | ORF 1", "Tatort", start) { false }
        assertEquals("DE-ORF 1-Tatort-2026.12.23-2015-2145.ts", RecordingNames.withStop(running, at(21, 45)) { false })
    }

    @Test
    fun makesNamesUnique() {
        val used = mutableSetOf("DE-ORF 1-Tatort-2026.12.23-2015.ts")
        val second = RecordingNames.fileName("DE | ORF 1", "Tatort", start) { it in used }
        assertEquals("DE-ORF 1-Tatort-2026.12.23-2015-2.ts", second)
        used += second
        assertEquals("DE-ORF 1-Tatort-2026.12.23-2015-3.ts", RecordingNames.fileName("DE | ORF 1", "Tatort", start) { it in used })
    }

    @Test
    fun findsTheShowInAChannelColonTitle() {
        assertEquals("Tatort", RecordingNames.programOf("ORF 1: Tatort", "ORF 1"))
        assertEquals("", RecordingNames.programOf("ORF 1", "ORF 1"))
        assertEquals("Tatort", RecordingNames.programOf("Tatort", "ORF 1"))
    }
}
