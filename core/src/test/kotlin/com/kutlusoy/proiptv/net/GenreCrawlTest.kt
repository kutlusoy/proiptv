package com.kutlusoy.proiptv.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GenreCrawlTest {
    @Test
    fun everyAnswerIsHandedOn() = runBlocking {
        val stored = LinkedHashMap<String, String?>()
        val n = GenreCrawl.run(listOf("1", "2", "3"), 0, { if (it == "2") null else "Drama $it" }, { id, g -> stored[id] = g })
        assertEquals(3, n)
        assertEquals(mapOf("1" to "Drama 1", "2" to null, "3" to "Drama 3"), stored)
    }

    @Test
    fun stopsWhenTheProviderRefuses() = runBlocking {
        val stored = ArrayList<String>()
        val n = GenreCrawl.run(listOf("1", "2", "3", "4"), 0, { if (it == "3") throw HttpException(429, "HTTP 429") else "x" }, { id, _ -> stored += id })
        assertEquals(2, n)
        assertEquals(listOf("1", "2"), stored)
    }

    @Test
    fun aMissingMovieIsSkipped() = runBlocking {
        val stored = LinkedHashMap<String, String?>()
        GenreCrawl.run(listOf("1", "2"), 0, { if (it == "1") throw HttpException(404, "HTTP 404") else "Horror" }, { id, g -> stored[id] = g })
        assertEquals(mapOf("1" to null, "2" to "Horror"), stored)
    }

    @Test
    fun noNetworkStopsWithoutMarkingTheMovie() = runBlocking {
        val stored = ArrayList<String>()
        val n = GenreCrawl.run(listOf("1", "2"), 0, { throw java.io.IOException("offline") }, { id, _ -> stored += id })
        assertEquals(0, n)
        assertEquals(emptyList<String>(), stored)
    }
}
