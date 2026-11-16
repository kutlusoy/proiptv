package com.kutlusoy.proiptv.net

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HlsStreamTest {
    private class Fake(val files: MutableMap<String, ByteArray>) : HlsSource {
        override fun open(url: String) = ByteArrayInputStream(files[url] ?: throw IOException("404 $url"))
    }

    private fun text(s: String) = s.trimIndent().toByteArray()

    @Test
    fun startsAtNewestSegmentAndFollowsTheLiveEdge() {
        val files = mutableMapOf(
            "http://h/live/p.m3u8" to text("#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:10\n#EXTINF:4,\ns10.ts\n#EXTINF:4,\ns11.ts\n#EXTINF:4,\ns12.ts\n"),
            "http://h/live/s10.ts" to "A".toByteArray(), "http://h/live/s11.ts" to "B".toByteArray(),
            "http://h/live/s12.ts" to "C".toByteArray(), "http://h/live/s13.ts" to "D".toByteArray(),
        )
        var polls = 0
        val stream = HlsStream("http://h/live/p.m3u8", Fake(files), sleep = {
            polls++
            files["http://h/live/p.m3u8"] = text("#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXT-X-MEDIA-SEQUENCE:11\n#EXTINF:4,\ns11.ts\n#EXTINF:4,\ns12.ts\n#EXTINF:4,\ns13.ts\n#EXT-X-ENDLIST\n")
        })
        stream.prepare()
        assertEquals("CD", String(stream.readBytes()))
        assertTrue(polls >= 1)
    }

    @Test
    fun choosesTheBestVariantOfAMasterPlaylist() {
        val files = mutableMapOf(
            "http://h/m.m3u8" to text("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=500000\nlow/p.m3u8\n#EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=9,BANDWIDTH=2000000\nhigh/p.m3u8\n"),
            "http://h/high/p.m3u8" to text("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:0\n#EXTINF:4,\nhttp://h/seg/x.ts\n#EXT-X-ENDLIST\n"),
            "http://h/seg/x.ts" to "VIDEO".toByteArray(),
        )
        val stream = HlsStream("http://h/m.m3u8", Fake(files))
        stream.prepare()
        assertEquals("VIDEO", String(stream.readBytes()))
    }

    @Test
    fun refusesEncryptedAndFragmentedPlaylists() {
        for (tag in listOf("#EXT-X-KEY:METHOD=AES-128,URI=\"k\"", "#EXT-X-MAP:URI=\"init.mp4\"")) {
            val files = mutableMapOf("http://h/p.m3u8" to text("#EXTM3U\n$tag\n#EXTINF:4,\ns.ts\n"))
            try {
                HlsStream("http://h/p.m3u8", Fake(files)).prepare()
                fail("expected an error for $tag")
            } catch (e: IOException) {
                assertTrue(e.message!!.contains("not supported"))
            }
        }
    }

    @Test
    fun givesUpWhenThePlaylistStandsStill() {
        val files = mutableMapOf("http://h/p.m3u8" to text("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:5\n#EXTINF:4,\ns5.ts\n"), "http://h/s5.ts" to "X".toByteArray())
        var clock = 0L
        val stream = HlsStream("http://h/p.m3u8", Fake(files), sleep = { clock += 20_000 }, now = { clock })
        stream.prepare()
        val first = ByteArray(1)
        assertEquals(1, stream.read(first, 0, 1))
        try {
            stream.read(first, 0, 1)
            fail("expected a stall error")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("no new segments"))
        }
    }

    @Test
    fun recognisesHlsAddresses() {
        assertTrue(Upstream.isHls("http://h/live/1.m3u8"))
        assertTrue(Upstream.isHls("http://h/live/1.M3U8?token=a.ts"))
        assertFalse(Upstream.isHls("http://h/live/1.ts"))
        assertFalse(Upstream.isHls("http://h/live/1.ts?x=.m3u8"))
    }
}
