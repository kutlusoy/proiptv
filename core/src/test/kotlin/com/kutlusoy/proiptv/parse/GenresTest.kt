package com.kutlusoy.proiptv.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenresTest {
    @Test
    fun splitsTheSeparatorsOfProviders() {
        assertEquals(listOf("Horror", "Action", "Thriller"), Genres.split("Horror / Action / Thriller"))
        assertEquals(listOf("Sci-Fi & Fantasy", "Drama", "Action & Adventure"), Genres.split("Sci-Fi & Fantasy, Drama, Action & Adventure"))
        assertEquals(listOf("Drama", "Comedy"), Genres.split(" Drama ;Comedy; drama "))
        assertTrue(Genres.split(null).isEmpty())
        assertTrue(Genres.split("  ").isEmpty())
    }

    @Test
    fun matchesWithoutRegardToCase() {
        assertTrue(Genres.has("Horror / Action", "action"))
        assertFalse(Genres.has("Horror / Action", "Act"))
    }

    @Test
    fun countsTheBiggestGenreFirst() {
        val counted = Genres.count(sequenceOf("Drama, Action", "drama", null, "Action / Drama", "Comedy"))
        assertEquals(listOf("Drama" to 3, "Action" to 2, "Comedy" to 1), counted)
    }

    @Test
    fun storedFormKeepsTheNamesBetweenBars() {
        assertEquals("|Horror|Action|", Genres.encode("Horror / Action"))
        assertEquals("", Genres.encode(null))
        assertEquals(listOf("Horror", "Action"), Genres.decode("|Horror|Action|"))
        val rows = listOf("|Drama|Action|" to 2, "|drama|" to 1, "" to 5, "|Comedy|" to 1)
        assertEquals(listOf("Drama" to 3, "Action" to 2, "Comedy" to 1), Genres.countStored(rows))
    }
}
