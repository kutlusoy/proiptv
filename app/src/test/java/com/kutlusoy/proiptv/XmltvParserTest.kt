package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.parse.XmltvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XmltvParserTest {
    @Test
    fun xmltvTimeAndPrograms() {
        assertEquals(1735732800L, XmltvParser.parseTime("20250101120000 +0000"))
        assertEquals(1735732800L - 3600, XmltvParser.parseTime("20250101120000 +0100"))
        assertNull(XmltvParser.parseTime("garbage"))
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="one.de"><display-name>One</display-name></channel>
              <programme start="20250101120000 +0000" stop="20250101130000 +0000" channel="one.de">
                <title lang="de">News</title><desc>Daily news</desc>
              </programme>
              <programme start="20250101130000 +0000" stop="20250101140000 +0000" channel="other">
                <title>Skipped</title>
              </programme>
              <programme start="20240101130000 +0000" stop="20240101140000 +0000" channel="one.de">
                <title>Too old</title>
              </programme>
            </tv>""".trimIndent()
        val list = XmltvParser.parse(xml.byteInputStream(), { it == "one.de" }, 1735700000L, 1735900000L).toList()
        assertEquals(1, list.size)
        assertEquals("News", list[0].title)
        assertEquals("Daily news", list[0].description)
    }
}
