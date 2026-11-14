package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.SortKeys
import com.kutlusoy.proiptv.net.StreamUrls
import com.kutlusoy.proiptv.net.XtreamUrl
import com.kutlusoy.proiptv.parse.M3uParser
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    private val playlist = """
        #EXTM3U url-tvg="http://example.com/guide.xml.gz"
        #EXTINF:-1 tvg-id="one.de" tvg-name="One" tvg-logo="http://x/one.png" group-title="News, Info" catchup="default" catchup-days="3" catchup-source="http://x/c?s=${'$'}{start}",One HD
        http://example.com/live/1.ts
        #EXTINF:-1 group-title="Movies",A Film
        #EXTVLCOPT:http-user-agent=X
        http://example.com/movie/u/p/5.mkv

        #EXTINF:-1,No Attributes
        http://example.com/live/2.m3u8
    """.trimIndent()

    @Test
    fun m3uEntriesAndHeader() {
        var epg: String? = null
        val entries = M3uParser.parse(playlist.reader().buffered()) { epg = it.epgUrl }.toList()
        assertEquals("http://example.com/guide.xml.gz", epg)
        assertEquals(3, entries.size)
        val one = entries[0]
        assertEquals("One HD", one.name)
        assertEquals("News, Info", one.group)
        assertEquals("one.de", one.tvgId)
        assertEquals(3, one.catchupDays)
        assertEquals("http://example.com/live/1.ts", one.url)
        assertEquals("A Film", entries[1].name)
        assertEquals("No Attributes", entries[2].name)
        assertNull(entries[2].tvgId)
    }

    @Test
    fun xtreamUrlNormalizing() {
        assertEquals("http://host:8080", XtreamUrl.normalizeServer("host:8080/"))
        assertEquals("http://host:8080", XtreamUrl.normalizeServer("http://host:8080/player_api.php?username=a&password=b"))
        assertEquals("https://host/panel", XtreamUrl.normalizeServer("https://host/panel/c/"))
        val login = XtreamUrl.parse("http://host:80/get.php?username=bob&password=p%40ss&type=m3u_plus")
        assertEquals("http://host:80", login.server)
        assertEquals("bob", login.username)
        assertEquals("p@ss", login.password)
    }

    @Test
    fun sortKeys() {
        assertEquals(1000.0, SortKeys.between(null, null)!!, 0.0)
        assertEquals(500.0, SortKeys.between(null, 1500.0)!!, 0.0)
        assertEquals(2000.0, SortKeys.between(1000.0, null)!!, 0.0)
        assertEquals(1500.0, SortKeys.between(1000.0, 2000.0)!!, 0.0)
        assertNull(SortKeys.between(1.0, 1.0 + 1e-9))
        assertEquals(listOf(1000.0, 2000.0, 3000.0), SortKeys.evenly(3))
    }

    @Test
    fun streamUrls() {
        assertEquals("http://h/live/u/p/7.ts", StreamUrls.live("http://h", "u", "p", "7", "ts"))
        assertEquals("ts", StreamUrls.liveFormat("auto", "m3u8,ts"))
        assertEquals("m3u8", StreamUrls.liveFormat("auto", "m3u8"))
        assertEquals("ts", StreamUrls.liveFormat("auto", null))
        assertEquals("m3u8", StreamUrls.liveFormat("m3u8", "ts"))
        assertEquals("http://h/timeshift/u/p/65/2025-01-01:12-00/7.ts", StreamUrls.xtreamCatchup("http://h", "u", "p", "7", 1735732800L, 65))
        val item = Item(accountId = 1, kind = 0, remoteId = "x", name = "n", url = "http://x/live.ts", catchupSource = "?s=\${start}&e=\${end}")
        assertEquals("http://x/live.ts?s=100&e=200", StreamUrls.m3uCatchup(item.url, item.catchupSource, 100, 200, 300))
        assertNotNull(StreamUrls.m3uCatchup(item.url, null, 100, 200, 300))
        assertTrue(StreamUrls.m3uCatchup(item.url, null, 100, 200, 300)!!.contains("utc=100"))
    }
}
