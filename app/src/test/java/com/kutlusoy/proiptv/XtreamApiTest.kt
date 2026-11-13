package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.net.XtreamApi
import com.kutlusoy.proiptv.net.int
import com.kutlusoy.proiptv.net.str
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XtreamApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: XtreamApi

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = XtreamApi(OkHttpClient(), server.url("").toString().trimEnd('/'), "bob", "p w", "Test")
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun authenticateReadsAccountStatus() {
        server.enqueue(
            MockResponse().setBody(
                """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000","max_connections":"2","active_cons":0,
                "is_trial":"0","allowed_output_formats":["m3u8","ts"]},"server_info":{"url":"x"}}""",
            ),
        )
        val info = api.authenticate()
        assertEquals("Active", info.status)
        assertEquals(1893456000L, info.expiry)
        assertEquals(2, info.maxConnections)
        assertEquals(0, info.activeConnections)
        assertFalse(info.trial)
        assertEquals(listOf("m3u8", "ts"), info.outputFormats)
        val request = server.takeRequest()
        assertTrue(request.path!!.contains("username=bob"))
        assertTrue(request.path!!.contains("password=p+w"))
        assertEquals("Test", request.getHeader("User-Agent"))
    }

    @Test(expected = Exception::class)
    fun authenticateRejectsWrongLogin() {
        server.enqueue(MockResponse().setBody("""{"user_info":{"auth":0}}"""))
        api.authenticate()
    }

    @Test
    fun streamsAreReadWithMixedTypes() {
        server.enqueue(
            MockResponse().setBody(
                """[{"num":1,"name":"A","stream_id":10,"category_id":"3","tv_archive":1},
                   {"num":"2","name":"B","stream_id":"11","category_id":4,"tv_archive":0}]""",
            ),
        )
        val items = api.stream("get_live_streams").use { s -> s.items.toList() }
        assertEquals(2, items.size)
        assertEquals("10", items[0].str("stream_id"))
        assertEquals("11", items[1].str("stream_id"))
        assertEquals(4, items[1].int("category_id"))
        assertTrue(server.takeRequest().path!!.contains("action=get_live_streams"))
    }

    @Test
    fun emptyAnswersGiveNoItems() {
        server.enqueue(MockResponse().setBody("false"))
        assertEquals(0, api.stream("get_vod_streams").use { it.items.toList().size })
        server.enqueue(MockResponse().setBody("[]"))
        assertEquals(0, api.stream("get_series").use { it.items.toList().size })
    }

    @Test
    fun seriesEpisodesAreSorted() {
        server.enqueue(
            MockResponse().setBody(
                """{"episodes":{"2":[{"id":"21","episode_num":1,"title":"S2E1","container_extension":"mkv","season":2}],
                "1":[{"id":"12","episode_num":2,"title":"S1E2","season":1},{"id":"11","episode_num":1,"title":"S1E1","season":1}]}}""",
            ),
        )
        val list = api.seriesDetail("5").episodes
        assertEquals(listOf("11", "12", "21"), list.map { it.id })
        assertEquals("mkv", list[2].ext)
        assertEquals("mp4", list[0].ext)
    }

    @Test
    fun vodInfoReadsDetailsAndHandlesBackdropArraysAndEmptyInfo() {
        server.enqueue(
            MockResponse().setBody(
                """{"info":{"movie_image":"http://x/p.jpg","plot":"A plot","cast":"A, B","director":"D","genre":"Drama",
                "releasedate":"2026-03-01","duration_secs":6838,"rating":"7.4","backdrop_path":["http://x/b.jpg"]},"movie_data":{}}""",
            ),
        )
        val info = api.vodInfo("5")!!
        assertEquals("A plot", info.plot)
        assertEquals("A, B", info.cast)
        assertEquals("2026-03-01", info.releaseDate)
        assertEquals(6838, info.durationSecs)
        assertEquals("http://x/b.jpg", info.backdrop)
        assertEquals("http://x/p.jpg", info.cover)
        assertTrue(server.takeRequest().path!!.contains("action=get_vod_info&vod_id=5"))
        server.enqueue(MockResponse().setBody("""{"info":[],"movie_data":{}}"""))
        assertEquals(null, api.vodInfo("6"))
    }

    @Test
    fun seriesDetailReadsInfoAndEpisodeImages() {
        server.enqueue(
            MockResponse().setBody(
                """{"info":{"plot":"Series plot","cover":"http://x/c.jpg","genre":"Crime","releaseDate":"2025-01-01","rating":"8.1","backdrop_path":"http://x/bd.jpg"},
                "episodes":{"1":[{"id":"11","episode_num":1,"title":"Pilot","season":1,"info":{"movie_image":"http://x/e1.jpg","plot":"First","duration_secs":2700,"rating":"8"}}]}}""",
            ),
        )
        val detail = api.seriesDetail("5")
        assertEquals("Series plot", detail.info?.plot)
        assertEquals("http://x/bd.jpg", detail.info?.backdrop)
        assertEquals("2025-01-01", detail.info?.releaseDate)
        val episode = detail.episodes.single()
        assertEquals("http://x/e1.jpg", episode.image)
        assertEquals(2700, episode.durationSecs)
        assertEquals("First", episode.plot)
    }
}
