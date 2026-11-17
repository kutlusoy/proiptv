package com.kutlusoy.proiptv.net

import com.kutlusoy.proiptv.parse.FavoriteMatch
import com.kutlusoy.proiptv.parse.MatchCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteMatchTest {
    private val list = listOf(
        MatchCandidate("1", "TR: TRT 1 HD", "trt1.tr"),
        MatchCandidate("2", "TR: ATV HD", "atv.tr"),
        MatchCandidate("3", "TR: STAR TV FHD", "startv.tr"),
        MatchCandidate("4", "DE: Das Erste", ""),
    )
    private val match = FavoriteMatch(list)

    @Test
    fun sameIdAndSameNameStays() = assertEquals("1", match.resolve("1", "tr: trt 1 hd", null)?.remoteId)

    @Test
    fun aRenamedChannelWithTheSameTvgIdStays() = assertEquals("3", match.resolve("3", "TR: STAR TV HD", "startv.tr")?.remoteId)

    @Test
    fun anIdOfAnotherChannelFollowsTheName() {
        // the id 2 belongs to ATV now, the favorite was made for TRT 1
        assertEquals("1", match.resolve("2", "TR: TRT 1 HD", null)?.remoteId)
    }

    @Test
    fun withoutNameTheTvgIdFindsTheChannel() = assertEquals("3", match.resolve("99", "TR: STAR TV HD", "STARTV.tr")?.remoteId)

    @Test
    fun nothingFoundMeansRemoval() {
        assertNull(match.resolve("99", "TR: NOW TV HD", "now.tr"))
        assertNull(match.resolve("99", "TR: NOW TV HD", null))
        assertNull(match.resolve("2", "TR: TRT 2", ""))
    }
}
