package com.kutlusoy.proiptv.desktop

import com.kutlusoy.proiptv.net.XtreamEpisode

data class NextEpisode(val episode: XtreamEpisode, val continuing: Boolean)

/** Which episode to offer next: the one that was started last, else the first one that is not watched. Same rule as on Android. */
object SeriesProgress {
    private const val STARTED_MS = 5_000L

    fun next(episodes: List<XtreamEpisode>, progress: (String) -> ProgressDto?): NextEpisode? {
        if (episodes.isEmpty()) return null
        val started = episodes.filter { (progress(it.id)?.positionMs ?: 0) > STARTED_MS }.maxByOrNull { progress(it.id)!!.updated }
        if (started != null) return NextEpisode(started, true)
        val anyWatched = episodes.any { progress(it.id)?.watched == true }
        val firstUnwatched = episodes.firstOrNull { progress(it.id)?.watched != true }
        return NextEpisode(firstUnwatched ?: episodes.first(), anyWatched)
    }
}
