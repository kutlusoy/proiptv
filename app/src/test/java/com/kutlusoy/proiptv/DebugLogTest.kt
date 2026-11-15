package com.kutlusoy.proiptv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLogTest {
    @Test
    fun passwordsUserNamesAndAddressesAreBlackedOut() {
        val text = DebugLog.redact("failed http://host:8080/player_api.php?username=ali&password=geheim&action=x and password=abc user=bob")
        assertFalse(text.contains("geheim"))
        assertFalse(text.contains("ali&"))
        assertFalse(text.contains("host:8080"))
        assertTrue(text.contains("password=***"))
        assertTrue(text.contains("user=***"))
    }
}
