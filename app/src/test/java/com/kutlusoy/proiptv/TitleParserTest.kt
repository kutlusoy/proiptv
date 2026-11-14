package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.parse.TitleParser
import com.kutlusoy.proiptv.parse.decodeEntities
import org.junit.Assert.assertEquals
import org.junit.Test

class TitleParserTest {
    private fun check(raw: String, title: String, country: String, year: Int) {
        val i = TitleParser.parse(raw)
        assertEquals(raw, title, i.title)
        assertEquals(raw, country, i.country)
        assertEquals(raw, year, i.year)
    }

    @Test
    fun countryPrefixes() {
        check("DE: Tagesschau", "Tagesschau", "DE", 0)
        check("|DE| RTL HD", "RTL HD", "DE", 0)
        check("[TR] Show TV", "Show TV", "TR", 0)
        check("(FR) France 2", "France 2", "FR", 0)
        check("UK - BBC One", "BBC One", "GB", 0)
        check("IT | Rai 1", "Rai 1", "IT", 0)
        check("USA: CNN", "CNN", "US", 0)
        check("(DE-) Heartland", "Heartland", "DE", 0)
    }

    @Test
    fun yearsStayRecognisable() {
        check("The Matrix (1999)", "The Matrix", "", 1999)
        check("DE - Oppenheimer [2023]", "Oppenheimer", "DE", 2023)
        check("Dune Part Two 2024", "Dune Part Two", "", 2024)
    }

    @Test
    fun noFalsePositives() {
        check("It Follows", "It Follows", "", 0)
        check("Das Boot", "Das Boot", "", 0)
        check("1917", "1917", "", 0)
        check("TV: Nachrichten", "TV: Nachrichten", "", 0)
        check("Sky Sport 1", "Sky Sport 1", "", 0)
        check("Blade Runner 2049", "Blade Runner 2049", "", 0)
    }

    @Test
    fun groupFallback() {
        assertEquals("DE", TitleParser.parse("Das Erste HD", "DE | Allgemein").country)
        assertEquals("FR", TitleParser.parse("FR: TF1", "DE | Allgemein").country)
    }
}

class EntitiesTest {
    @org.junit.Test
    fun decodesDoubleEscapedText() {
        org.junit.Assert.assertEquals("Wau & Wau", "Wau &amp; Wau".decodeEntities())
        org.junit.Assert.assertEquals("Tom & Jerry", "Tom &amp;amp; Jerry".decodeEntities())
        org.junit.Assert.assertEquals("a < b > c", "a &lt; b &gt; c".decodeEntities())
        org.junit.Assert.assertEquals("It's \"x\"", "It&#39;s &quot;x&quot;".decodeEntities())
        org.junit.Assert.assertEquals("ä ö", "&#xE4; &#246;".decodeEntities())
        org.junit.Assert.assertEquals("Fish &chips; and R&B", "Fish &chips; and R&B".decodeEntities())
        org.junit.Assert.assertEquals("plain", "plain".decodeEntities())
    }
}
