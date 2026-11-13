package com.kutlusoy.proiptv.data

import com.kutlusoy.proiptv.net.XtreamEpisode

data class NextEpisode(val episode: XtreamEpisode, val continuing: Boolean)

object SeriesProgress {
    private const val STARTED_MS = 5_000L

    fun next(episodes: List<XtreamEpisode>, progress: Map<String, Progress>): NextEpisode? {
        if (episodes.isEmpty()) return null
        val started = episodes.filter { (progress[it.id]?.positionMs ?: 0) > STARTED_MS }
            .maxByOrNull { progress.getValue(it.id).updated }
        if (started != null) return NextEpisode(started, true)
        val anyWatched = episodes.any { progress[it.id]?.watched == true }
        val firstUnwatched = episodes.firstOrNull { progress[it.id]?.watched != true }
        return NextEpisode(firstUnwatched ?: episodes.first(), anyWatched)
    }
}
