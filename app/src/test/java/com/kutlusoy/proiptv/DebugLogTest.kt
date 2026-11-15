package com.kutlusoy.proiptv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
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

    @org.junit.Test
    fun theLogFileNeverGrowsPastItsLimit() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        DebugLog.init(context)
        val file = java.io.File(context.filesDir, "debug-log.txt")
        repeat(6_000) { DebugLog.write("line $it " + "x".repeat(100)) }
        assertTrue("size ${file.length()}", file.length() <= 210_000)
        assertTrue(DebugLog.tail(5).contains("line 5999"))
    }
}
