package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.net.Tmdb
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbTest {
    private fun server(): Pair<MockWebServer, MutableList<String>> {
        val seen = mutableListOf<String>()
        val s = MockWebServer()
        s.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request.path.orEmpty()
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/3/search/movie") && "Odyssee" in path -> MockResponse().setBody("""{"results":[{"id":42}]}""")
                    path.startsWith("/3/search/movie") -> MockResponse().setBody("""{"results":[]}""")
                    path.startsWith("/3/movie/42") -> MockResponse().setBody(
                        """{"overview":"Eine Reise.","runtime":172,"vote_average":8.0,"release_date":"2026-07-15","poster_path":"/p.jpg","backdrop_path":"/b.jpg",
                        "genres":[{"name":"Abenteuer"},{"name":"Fantasy"}],
                        "credits":{"cast":[{"name":"Matt Damon"},{"name":"Tom Holland"}],"crew":[{"job":"Writer","name":"X"},{"job":"Director","name":"Christopher Nolan"}]},
                        "videos":{"results":[{"site":"YouTube","type":"Teaser","key":"teaser1"},{"site":"YouTube","type":"Trailer","key":"trailer1"}]}}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        s.start()
        return s to seen
    }

    @Test
    fun aMovieIsFoundByTitleAndYearAndMappedToTheInfoFields() {
        val (s, seen) = server()
        try {
            val info = Tmdb(OkHttpClient(), "KEY", "de", s.url("/3").toString().trimEnd('/')).movie("Die Odyssee", 2026)!!
            assertEquals("Eine Reise.", info.plot)
            assertEquals("Christopher Nolan", info.director)
            assertEquals("Matt Damon, Tom Holland", info.cast)
            assertEquals("Abenteuer, Fantasy", info.genre)
            assertEquals(172 * 60, info.durationSecs)
            assertEquals("trailer1", info.trailer)
            assertEquals("https://image.tmdb.org/t/p/w780/b.jpg", info.backdrop)
            assertTrue(seen.first().contains("api_key=KEY") && seen.first().contains("year=2026") && seen.first().contains("language=de"))
        } finally {
            s.shutdown()
        }
    }

    @Test
    fun nothingIsReturnedWhenTheTitleIsNotFound() {
        val (s, _) = server()
        try {
            assertNull(Tmdb(OkHttpClient(), "KEY", "de", s.url("/3").toString().trimEnd('/')).movie("Unbekannt", 0))
        } finally {
            s.shutdown()
        }
    }
}
