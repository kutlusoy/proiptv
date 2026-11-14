package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.net.trailerUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerUrlTest {
    @Test
    fun aVideoIdBecomesAYoutubeAddressAndAFullAddressStaysHttps() {
        assertEquals("https://www.youtube.com/watch?v=abc123", trailerUrl("abc123"))
        assertEquals("https://example.com/t.mp4", trailerUrl("http://example.com/t.mp4"))
        assertEquals("https://www.youtube.com/watch?v=x", trailerUrl("https://www.youtube.com/watch?v=x"))
        assertNull(trailerUrl("  "))
        assertNull(trailerUrl(null))
    }
}
