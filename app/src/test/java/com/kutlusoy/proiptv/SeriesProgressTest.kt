package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.data.Progress
import com.kutlusoy.proiptv.data.SeriesProgress
import com.kutlusoy.proiptv.net.XtreamEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesProgressTest {
    private val episodes = listOf(
        XtreamEpisode("e1", 1, 1, "One", "mp4", null, null),
        XtreamEpisode("e2", 1, 2, "Two", "mp4", null, null),
        XtreamEpisode("e3", 1, 3, "Three", "mp4", null, null),
    )

    private fun p(id: String, position: Long = 0, watched: Boolean = false, updated: Long = 1) =
        Progress(1, 3, id, position, 100_000, updated, watched)

    @Test
    fun nothingWatchedStartsAtTheFirstEpisode() {
        val next = SeriesProgress.next(episodes, emptyMap())!!
        assertEquals("e1", next.episode.id)
        assertFalse(next.continuing)
    }

    @Test
    fun watchedEpisodesAreSkipped() {
        val next = SeriesProgress.next(episodes, mapOf("e1" to p("e1", watched = true)))!!
        assertEquals("e2", next.episode.id)
        assertTrue(next.continuing)
    }

    @Test
    fun anEpisodeInProgressWins() {
        val progress = mapOf(
            "e1" to p("e1", watched = true, updated = 5),
            "e2" to p("e2", position = 60_000, updated = 9),
            "e3" to p("e3", position = 20_000, updated = 7),
        )
        val next = SeriesProgress.next(episodes, progress)!!
        assertEquals("e2", next.episode.id)
        assertTrue(next.continuing)
    }

    @Test
    fun aWholeSeasonMarkedWatchedByHandStartsOverAtTheFirstEpisode() {
        val progress = episodes.associate { it.id to p(it.id, watched = true, updated = 100) }
        val next = SeriesProgress.next(episodes, progress)!!
        assertEquals("e1", next.episode.id)
        assertTrue(next.continuing)
    }

    @Test
    fun startsLessThanFiveSecondsDoNotCountAsStarted() {
        val next = SeriesProgress.next(episodes, mapOf("e1" to p("e1", position = 3_000)))!!
        assertEquals("e1", next.episode.id)
        assertFalse(next.continuing)
    }

    @Test
    fun noEpisodesGiveNothing() = assertNull(SeriesProgress.next(emptyList(), emptyMap()))
}
