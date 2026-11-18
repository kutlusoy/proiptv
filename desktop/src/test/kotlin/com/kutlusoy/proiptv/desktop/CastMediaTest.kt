package com.kutlusoy.proiptv.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class CastMediaTest {
    @Test
    fun contentTypeFollowsTheEndingOfTheAddress() {
        assertEquals("application/x-mpegURL", CastMedia.contentType("http://x.tv/live/u/p/1.m3u8?token=a.ts"))
        assertEquals("video/mp4", CastMedia.contentType("http://x.tv/movie/u/p/5.MP4"))
        assertEquals("video/x-matroska", CastMedia.contentType("http://x.tv/series/u/p/9.mkv"))
        assertEquals("video/mp2t", CastMedia.contentType("http://x.tv/live/u/p/1.ts"))
        assertEquals("video/mp2t", CastMedia.contentType("http://x.tv/live/u/p/1"))
    }
}
