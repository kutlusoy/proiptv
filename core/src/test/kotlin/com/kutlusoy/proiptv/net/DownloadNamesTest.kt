package com.kutlusoy.proiptv.net

import com.kutlusoy.proiptv.parse.DownloadNames
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNamesTest {
    private val label = { n: Int -> "Season %02d".format(n) }

    @Test
    fun aMovieHasLanguageNameAndYear() {
        assertEquals("DE-Der letzte Zug-2023.mp4", DownloadNames.movie("DE | Der letzte Zug (2023)", "mp4"))
        assertEquals("Nachtwind.mkv", DownloadNames.movie("Nachtwind", "mkv"))
    }

    @Test
    fun anEpisodeGoesIntoTheFolderOfItsSeriesAndSeason() {
        assertEquals(
            "DE-Hafenlichter-2021/Season 01/DE-Hafenlichter-2021-S01E05-Die Spur.mp4",
            DownloadNames.episode("DE | Hafenlichter (2021)", 1, 5, "Die Spur", "mp4", label),
        )
    }

    @Test
    fun missingPartsAreLeftOutAndForbiddenCharactersGo() {
        assertEquals("Show/Season 02/Show-S02E10.mp4", DownloadNames.episode("Show", 2, 10, "Show", "mp4", label))
        assertEquals("Show/Season 00/Show-A B.mp4", DownloadNames.episode("Show", 0, 0, "A: B?", "mp4", label))
    }
}
