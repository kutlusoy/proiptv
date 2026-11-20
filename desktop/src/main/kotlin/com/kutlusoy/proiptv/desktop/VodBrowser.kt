package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.net.SeriesDetail
import com.kutlusoy.proiptv.net.SeriesInfo
import com.kutlusoy.proiptv.net.VodInfo
import com.kutlusoy.proiptv.parse.TitleParser

/**
 * The preview layout for movies and series (setting): the list on the left (the episodes with their season, or the other movies of the category), the running
 * title as a small picture with its info card, and its details below. The controls are the ones of the player (the bar below). Going to the big picture does
 * not interrupt the playing: it is the same player.
 */
@Composable
fun VodBrowser(state: AppState, controller: PlayerController, video: @Composable (Modifier) -> Unit, modifier: Modifier) {
    val entry = controller.vodEntry ?: return
    val isSeries = entry.kind == Kind.SERIES
    val detail by produceState<SeriesDetail?>(null, entry.key) { if (isSeries) value = runCatching { state.library.seriesDetail(entry) }.getOrDefault(SeriesDetail(null, emptyList())) }
    val seriesInfo by produceState<SeriesInfo?>(null, detail) { if (isSeries && detail != null) value = state.library.seriesInfo(entry, detail?.info) }
    val vod by produceState<VodInfo?>(null, entry.key) { if (!isSeries) value = state.library.vodInfo(entry) }
    val episodes = detail?.episodes.orEmpty()
    val seasons = episodes.map { it.season }.distinct()
    val playingId = controller.current?.remoteId
    val playing = episodes.firstOrNull { it.id == playingId }
    val version = state.progress.version
    // before anything plays: the episode where the user stopped (or the next one to watch) is ready, with the minute
    val nextEpisode = remember(episodes, version) { if (isSeries) SeriesProgress.next(episodes) { id -> state.progress.get(entry.accountId, Kind.EPISODE, id) } else null }
    val readyId = if (controller.current == null) nextEpisode?.episode?.id else null
    // the list shows the season of the running episode, or of the one that is ready
    var season by remember(entry.key, playing?.season, nextEpisode?.episode?.season) { mutableStateOf(playing?.season ?: nextEpisode?.episode?.season ?: -1) }
    val active = if (season in seasons) season else seasons.firstOrNull() ?: 1
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val seasonEpisodes = episodes.filter { it.season == active }
    androidx.compose.runtime.LaunchedEffect(readyId, active, episodes.size) {
        val at = seasonEpisodes.indexOfFirst { it.id == readyId }
        if (at >= 0) listState.scrollToItem((at - 1).coerceAtLeast(0))
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val left = (maxWidth * 0.32f).coerceAtLeast(300.dp).coerceAtMost(460.dp)
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(left).fillMaxHeight().background(Palette.Velvet)) {
                if (isSeries) {
                    if (seasons.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            DropdownField(t("series_season", active), seasons.map { it to t("series_season", it) }, { season = it }, Modifier.weight(1f))
                            // the whole season: download or mark as watched
                            var menu by remember { mutableStateOf(false) }
                            val allWatched = seasonEpisodes.isNotEmpty() && seasonEpisodes.all { state.progress.get(entry.accountId, Kind.EPISODE, it.id)?.watched == true }
                            Box {
                                Tip(t("action_more")) { IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, t("action_more")) } }
                                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    androidx.compose.material3.DropdownMenuItem(text = { Text(t("dl_season")) }, onClick = { menu = false; state.downloadEpisodes(entry, seasonEpisodes) })
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(t(if (allWatched) "detail_season_unwatched" else "detail_season_watched")) },
                                        onClick = { menu = false; state.progress.setWatched(entry.accountId, Kind.EPISODE, seasonEpisodes.map { it.id }, !allWatched) },
                                    )
                                }
                            }
                        }
                    } else if (detail == null) {
                        Text(t("series_loading"), Modifier.padding(12.dp), color = Color.White.copy(alpha = 0.7f))
                    }
                    LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(seasonEpisodes, key = { it.id }) { ep ->
                            val p = state.progress.let { it.version; it.get(entry.accountId, Kind.EPISODE, ep.id) }
                            val now = ep.id == playingId
                            val ready = ep.id == readyId
                            Row(
                                Modifier.fillMaxWidth().background(if (now) Palette.Wine else if (ready) Palette.Wine.copy(alpha = 0.35f) else Color.Transparent)
                                    .clickable { state.playEpisode(entry, ep, episodes, p?.positionMs?.takeIf { it > 5_000 } ?: 0) }.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Surface)) {
                                    Logo(ep.image ?: entry.logo, Modifier.fillMaxSize(), ContentScale.Crop, name = ep.title)
                                    if (p != null && p.positionMs > 0 && p.durationMs > 0) {
                                        LinearProgressIndicator(progress = { (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) }, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("${ep.number}. ${ep.title}", maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    val extra = listOfNotNull(ep.durationSecs?.takeIf { it > 0 }?.let { "${it / 60} min" }, if (p?.watched == true) "✓" else null).joinToString("  ")
                                    if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                                    // the place where the user stopped
                                    if (ready) {
                                        val minute = p?.positionMs?.takeIf { it > 5_000 }
                                        Text(if (minute != null) t("detail_resume", clock(minute)) else t("detail_play"), style = MaterialTheme.typography.labelMedium, color = Palette.WineSoft, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                // download and watched stand at the right of each episode (the same as on the detail page)
                                val dl = state.downloadOf(entry.accountId, Kind.EPISODE, ep.id)
                                val watched = p?.watched == true
                                Tip(t("dl_download")) {
                                    IconButton(enabled = dl == null, onClick = { state.downloadEpisodes(entry, listOf(ep)) }) {
                                        Icon(Icons.Filled.Download, t("dl_download"), tint = if (dl?.status == DlStatus.DONE) Palette.WineSoft else Color.White.copy(alpha = if (dl == null) 0.8f else 0.4f))
                                    }
                                }
                                Tip(t(if (watched) "detail_mark_unwatched" else "detail_mark_watched")) {
                                    IconButton(onClick = { state.progress.setWatched(entry.accountId, Kind.EPISODE, listOf(ep.id), !watched) }) {
                                        Icon(Icons.Filled.CheckCircle, t(if (watched) "detail_mark_unwatched" else "detail_mark_watched"), tint = if (watched) Palette.WineSoft else Color.White.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(t("detail_related_movies"), Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                    val related = remember(entry.key) { state.data.entries(Kind.MOVIE).filter { it.accountId == entry.accountId && it.categoryId == entry.categoryId && it.remoteId != entry.remoteId }.take(40) }
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(related, key = { it.key }) { r ->
                            Row(Modifier.fillMaxWidth().clickable { state.playMovie(r) }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.width(48.dp).height(70.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Surface)) { Logo(r.logo, Modifier.fillMaxSize(), ContentScale.Crop, name = r.name) }
                                Text(TitleParser.parse(r.name).title.ifBlank { r.name }, Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.weight(0.5f).fillMaxWidth()) {
                    Box(Modifier.weight(0.62f).fillMaxHeight().background(Color.Black), contentAlignment = Alignment.Center) {
                        // until something plays the poster is shown
                        if (controller.current == null) Logo(entry.logo, Modifier.fillMaxSize().padding(8.dp), ContentScale.Fit, name = entry.name) else video(Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(0.38f).fillMaxHeight().background(Palette.Surface).padding(16.dp), verticalArrangement = Arrangement.Center) {
                        val badge = if (isSeries && playing != null) "S${playing.season} · E${playing.number}" else t(if (isSeries) "nav_series" else "nav_movies")
                        Text(badge, Modifier.background(Palette.Wine, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        Text(TitleParser.parse(entry.name).title.ifBlank { entry.name }, Modifier.padding(top = 8.dp), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        if (!isSeries) {
                            val dl = state.downloadOf(entry.accountId, Kind.MOVIE, entry.remoteId)
                            val seen = state.progress.let { it.version; it.get(entry.accountId, Kind.MOVIE, entry.remoteId) }?.watched == true
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Tip(t("dl_download")) {
                                    androidx.compose.material3.OutlinedButton(enabled = dl == null, onClick = { state.downloadMovie(entry) }) {
                                        Icon(Icons.Filled.Download, null)
                                        Text(t(if (dl?.status == DlStatus.DONE) "dl_downloaded" else if (dl != null) "dl_running" else "dl_download"), Modifier.padding(start = 6.dp))
                                    }
                                }
                                Tip(t(if (seen) "detail_mark_unwatched" else "detail_mark_watched")) {
                                    IconButton(onClick = { state.progress.setWatched(entry.accountId, Kind.MOVIE, listOf(entry.remoteId), !seen) }) {
                                        Icon(Icons.Filled.CheckCircle, t(if (seen) "detail_mark_unwatched" else "detail_mark_watched"), tint = if (seen) Palette.WineSoft else Color.White.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                        if (controller.current == null) {
                            // nothing plays yet: the button starts the movie, or the episode that comes next
                            val saved = if (isSeries) null else state.progress.let { it.version; it.get(entry.accountId, Kind.MOVIE, entry.remoteId) }
                            val resume = (if (isSeries) nextEpisode?.episode?.let { state.progress.get(entry.accountId, Kind.EPISODE, it.id) } else saved)?.positionMs?.takeIf { it > 5_000 } ?: 0
                            if (!isSeries || nextEpisode != null) {
                                androidx.compose.material3.Button(
                                    onClick = { if (isSeries) nextEpisode?.let { state.playEpisode(entry, it.episode, episodes, resume) } else state.playMovie(entry, resume) },
                                    modifier = Modifier.padding(top = 10.dp),
                                ) {
                                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, null)
                                    Text(
                                        when {
                                            isSeries && nextEpisode != null ->
                                                t(if (nextEpisode.continuing) "detail_continue_episode" else "detail_play_episode", nextEpisode.episode.season, nextEpisode.episode.number) +
                                                    (if (resume > 0) " \u00B7 " + t("detail_resume", clock(resume)) else "")
                                            resume > 0 -> t("detail_resume", clock(resume))
                                            else -> t("detail_play")
                                        },
                                        Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                        }
                        if (isSeries && playing != null) Text(playing.title, Modifier.padding(top = 6.dp), color = Palette.WineSoft, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(Modifier.weight(0.5f).fillMaxWidth().background(Palette.Surface).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (isSeries) {
                        val i = seriesInfo
                        Facts(i?.rating ?: entry.rating, playing?.plot ?: i?.plot, i?.director, i?.cast, i?.releaseDate, i?.genre, playing?.durationSecs, i?.trailer)
                    } else {
                        val v = vod
                        Facts(v?.rating ?: entry.rating, v?.plot, v?.director, v?.cast, v?.releaseDate, v?.genre, v?.durationSecs, v?.trailer)
                    }
                }
            }
        }
    }
}
