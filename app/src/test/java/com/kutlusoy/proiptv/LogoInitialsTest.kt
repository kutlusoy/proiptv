package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.ui.initialsOf
import org.junit.Assert.assertEquals
import org.junit.Test

class LogoInitialsTest {
    @Test
    fun aMissingLogoIsReplacedByTheFirstLettersOfTheFirstTwoWords() {
        assertEquals("DE", initialsOf("Das Erste"))
        assertEquals("S", initialsOf("Sat1"))
        assertEquals("", initialsOf(null))
        assertEquals("", initialsOf("   "))
    }
}
