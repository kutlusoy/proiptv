package com.kutlusoy.proiptv.net

import com.kutlusoy.proiptv.parse.RecordingNames
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingNamesTest {
    private val at = Calendar.getInstance().apply { clear(); set(2026, Calendar.DECEMBER, 23, 20, 15) }.timeInMillis

    @Test
    fun languageChannelShowAndDate() {
        assertEquals("DE-ORF 1-Tatort-2026.12.23.ts", RecordingNames.fileName("DE | ORF 1", "Tatort", at) { false })
    }

    @Test
    fun leavesOutWhatIsMissingAndForbiddenCharacters() {
        assertEquals("kabel eins-2026.12.23.ts", RecordingNames.fileName("kabel eins", null, at) { false })
        assertEquals("Das Beste Aus-Heute Show-2026.12.23.ts", RecordingNames.fileName("Das Beste Aus", "Heute: Show?", at) { false })
    }

    @Test
    fun makesNamesUnique() {
        val used = mutableSetOf("DE-ORF 1-Tatort-2026.12.23.ts")
        val second = RecordingNames.fileName("DE | ORF 1", "Tatort", at) { it in used }
        assertEquals("DE-ORF 1-Tatort-2026.12.23-2015.ts", second)
        used += second
        assertEquals("DE-ORF 1-Tatort-2026.12.23-2015-2.ts", RecordingNames.fileName("DE | ORF 1", "Tatort", at) { it in used })
    }

    @Test
    fun findsTheShowInAChannelColonTitle() {
        assertEquals("Tatort", RecordingNames.programOf("ORF 1: Tatort", "ORF 1"))
        assertEquals("", RecordingNames.programOf("ORF 1", "ORF 1"))
        assertEquals("Tatort", RecordingNames.programOf("Tatort", "ORF 1"))
    }
}
