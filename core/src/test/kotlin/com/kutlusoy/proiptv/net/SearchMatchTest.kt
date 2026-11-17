package com.kutlusoy.proiptv.net

import com.kutlusoy.proiptv.parse.SearchMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatchTest {
    @Test
    fun everyWordHasToOccurInAnyOrder() {
        assertTrue(SearchMatch.matches("Tengri · FR FRANCE", "tengri FR"))
        assertTrue(SearchMatch.matches("Tengri · FR FRANCE", "france  tengri"))
        assertTrue(SearchMatch.matches("EUROSPORT 1 HD Snooker", "eurosport snooker"))
        assertFalse(SearchMatch.matches("Tengri · FR FRANCE", "tengri DE"))
    }

    @Test
    fun anEmptySearchFindsEverything() {
        assertTrue(SearchMatch.matches("anything", "   "))
        assertEquals(listOf("a", "b"), SearchMatch.words(" a   b "))
    }
}
