package com.kutlusoy.proiptv.net

import com.kutlusoy.proiptv.parse.StableId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StableIdTest {
    @Test
    fun theSameAddressGivesTheSameId() {
        val url = "http://uzay.pro:8080/u/p/289003"
        assertEquals(StableId.of(url), StableId.of(url))
        assertEquals(16, StableId.of(url).length)
        assertNotEquals(StableId.of(url), StableId.of("$url/x"))
    }

    @Test
    fun namesAreComparedWithoutCaseAndExtraSpaces() {
        assertTrue(StableId.sameName("TR:  TRT 1 HD ", "tr: trt 1 hd"))
        assertTrue(!StableId.sameName("TR: TRT 1 HD", "TR: TRT 2 HD"))
    }
}
