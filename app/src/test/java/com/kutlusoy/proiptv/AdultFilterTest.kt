package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.parse.AdultFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultFilterTest {
    @Test
    fun matchesAdultNames() {
        assertTrue(AdultFilter.isAdult("XXX | Adults"))
        assertTrue(AdultFilter.isAdult("DE: 18+ Movies"))
    }

    @Test
    fun keepsNormalNames() {
        assertFalse(AdultFilter.isAdult("Sky Sport Essex", "Sports", null))
        assertFalse(AdultFilter.isAdult("Deutschland HD"))
    }
}
