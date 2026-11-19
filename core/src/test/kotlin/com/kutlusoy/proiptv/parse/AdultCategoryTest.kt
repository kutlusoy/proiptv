package com.kutlusoy.proiptv.parse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultCategoryTest {
    @Test
    fun aNameThatStartsWithXxxIsAdult() {
        assertTrue(AdultCategory.matches("XXX"))
        assertTrue(AdultCategory.matches("xxx | Movies"))
        assertTrue(AdultCategory.matches("[XXX] Live"))
        assertTrue(AdultCategory.matches("  XXX - DE"))
        assertTrue(AdultCategory.matches("XXX: Some Channel HD"))
    }

    @Test
    fun otherNamesAreNot() {
        assertFalse(AdultCategory.matches("FR SPORT"))
        assertFalse(AdultCategory.matches("Sexy XXX Movies"))
        assertFalse(AdultCategory.matches("MAXXX"))
        assertFalse(AdultCategory.matches(""))
        assertFalse(AdultCategory.matches(null))
    }
}
