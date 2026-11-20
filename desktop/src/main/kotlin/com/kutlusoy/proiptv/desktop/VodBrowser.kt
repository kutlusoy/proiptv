package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.net.SeriesDetail
import com.kutlusoy.proiptv.net.SeriesInfo
import com.kutlusoy.proiptv.net.VodInfo
import com.kutlusoy.proiptv.parse.SearchMatch
import com.kutlusoy.proiptv.parse.TitleParser

private const val FAV_KEY = "fav:"
private const val ALL = "__all"

private class VodCategory(val key: String, val name: String, val count: Int)

/**
 * The preview layout for movies and series (setting), three columns like the live channels: the categories, the series (or movies) of the chosen category, and
 * the preview with its controls and details. A click on a series opens its episodes in the middle column (the arrow leads back to the list of series); a click on
 * an episode plays it. Going to the big picture does not interrupt the playing: it is the same player.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VodBrowser(state: AppState, controller: PlayerController, video: @Composable (Modifier) -> Unit, modifier: Modifier) {
    val start = controller.vodEntry ?: return
    val kind = start.kind
    val isSeries = kind == Kind.SERIES
    // what the details show: the title that was opened, or the one that was chosen in the list since
    var shown by remember(start.key) { mutableStateOf(start) }
    var drilled by remember(start.key) { mutableStateOf(isSeries) }
    var categoryKey by remember(start.key) { mutableStateOf("${start.accountId}|${start.categoryId}") }
    var categoryFilter by remember { mutableStateOf("") }
    var itemFilter by remember { mutableStateOf("") }
    val typingFocus = Modifier.onFocusChanged { state.typing = it.isFocused }
    DisposableEffect(Unit) { onDispose { state.typing = false } }

    val all = state.data.entries(kind)
    val lists = state.listsOf(kind)
    val other = t("live_other")
    val categories = remember(all, lists, state.favorites, state.data) {
        val counts = all.groupingBy { "${it.accountId}|${it.categoryId}" }.eachCount()
        buildList {
            lists.forEach { l -> add(VodCategory(FAV_KEY + l.id, if (lists.size == 1) Strings.get("fav_title") else "★ ${l.name}", all.count { state.inList(it, l.id) })) }
            add(VodCategory(ALL, Strings.get("live_all"), all.size))
            state.data.categories(kind).forEach { c ->
                val key = "${c.accountId}|${c.id}"
                add(VodCategory(key, (state.accountLabel(c.accountId)?.let { "$it · " } ?: "") + c.name.ifBlank { other }, counts[key] ?: 0))
            }
        }
    }
    val cf = categoryFilter.trim()
    val visibleCategories = remember(categories, cf) { categories.filter { cf.isEmpty() || SearchMatch.matches(it.name, cf) } }
    val inCategory = remember(all, categoryKey, state.favorites) {
        when {
            categoryKey == ALL -> all
            categoryKey.startsWith(FAV_KEY) -> {
                val id = categoryKey.removePrefix(FAV_KEY)
                val order = state.favoriteOrder(id)
                all.filter { state.inList(it, id) }.sortedBy { order[it.key] ?: Int.MAX_VALUE }
            }
            else -> all.filter { "${it.accountId}|${it.categoryId}" == categoryKey }
        }
    }
    val itf = itemFilter.trim()
    val items = remember(inCategory, itf) { if (itf.isEmpty()) inCategory else inCategory.filter { SearchMatch.matches(it.name, itf) } }

    val shownSeries = shown.kind == Kind.SERIES
    val detail by produceState<SeriesDetail?>(null, shown.key) { if (shownSeries) value = runCatching { state.library.seriesDetail(shown) }.getOrDefault(SeriesDetail(null, emptyList())) }
    val seriesInfo by produceState<SeriesInfo?>(null, detail) { if (shownSeries && detail != null) value = state.library.seriesInfo(shown, detail?.info) }
    val vod by produceState<VodInfo?>(null, shown.key) { if (!shownSeries) value = state.library.vodInfo(shown) }
    val episodes = detail?.episodes.orEmpty()
    val seasons = episodes.map { it.season }.distinct()
    val playingEntry = controller.vodEntry
    val playingId = controller.current?.remoteId
    val playing = if (playingEntry?.key == shown.key) episodes.firstOrNull { it.id == playingId } else null
    val version = state.progress.version
    // before anything plays: the episode where the user stopped (or the next one to watch) is ready, with the minute
    val nextEpisode = remember(episodes, version) { if (shownSeries) SeriesProgress.next(episodes) { id -> state.progress.get(shown.accountId, Kind.EPISODE, id) } else null }
    val readyId = if (controller.current == null || playingEntry?.key != shown.key) nextEpisode?.episode?.id else null
    var season by remember(shown.key, playing?.season, nextEpisode?.episode?.season) { mutableStateOf(playing?.season ?: nextEpisode?.episode?.season ?: -1) }
    val active = if (season in seasons) season else seasons.firstOrNull() ?: 1
    val episodeList = rememberLazyListState()
    val seasonEpisodes = episodes.filter { it.season == active }
    LaunchedEffect(readyId, active, episodes.size) {
        val at = seasonEpisodes.indexOfFirst { it.id == readyId }
        if (at >= 0) episodeList.scrollToItem((at - 1).coerceAtLeast(0))
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val first = (maxWidth * 0.2f).coerceAtLeast(190.dp).coerceAtMost(280.dp)
        val second = (maxWidth * 0.27f).coerceAtLeast(280.dp).coerceAtMost(420.dp)
        Row(Modifier.fillMaxSize()) {
            // the categories
            Column(Modifier.width(first).fillMaxHeight().background(Palette.Surface)) {
                TextInput(
                    categoryFilter, { categoryFilter = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).then(typingFocus),
                    placeholder = { Text(t("player_filter_categories", categories.size)) },
                    trailingIcon = { if (categoryFilter.isNotEmpty()) IconButton(onClick = { categoryFilter = "" }) { Icon(Icons.Filled.Close, t("action_close")) } },
                )
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(visibleCategories, key = { it.key }) { c ->
                        Row(
                            Modifier.fillMaxWidth().background(if (c.key == categoryKey) Palette.WineDeep else Color.Transparent)
                                .combinedClickable { categoryKey = c.key; drilled = false; itemFilter = "" }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (c.key == categoryKey) FontWeight.Bold else FontWeight.Normal)
                            Text(c.count.toString(), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
                        }
                    }
                }
            }
            // the series (or movies) of the category, or the episodes of the series that was opened
            Column(Modifier.width(second).fillMaxHeight().background(Palette.Velvet)) {
                if (isSeries && drilled) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tip(t("action_back")) { IconButton(onClick = { drilled = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("action_back")) } }
                        Text(TitleParser.parse(shown.name).title.ifBlank { shown.name }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    }
                    if (seasons.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            DropdownField(t("series_season", active), seasons.map { it to t("series_season", it) }, { season = it }, Modifier.weight(1f))
                            // the whole season: download or mark as watched
                            var menu by remember { mutableStateOf(false) }
                            val allWatched = seasonEpisodes.isNotEmpty() && seasonEpisodes.all { state.progress.get(shown.accountId, Kind.EPISODE, it.id)?.watched == true }
                            Box {
                                Tip(t("action_more")) { IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, t("action_more")) } }
                                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    androidx.compose.material3.DropdownMenuItem(text = { Text(t("dl_season")) }, onClick = { menu = false; state.downloadEpisodes(shown, seasonEpisodes) })
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(t(if (allWatched) "detail_season_unwatched" else "detail_season_watched")) },
                                        onClick = { menu = false; state.progress.setWatched(shown.accountId, Kind.EPISODE, seasonEpisodes.map { it.id }, !allWatched) },
                                    )
                                }
                            }
                        }
                    } else if (detail == null) {
                        Text(t("series_loading"), Modifier.padding(12.dp), color = Color.White.copy(alpha = 0.7f))
                    }
                    LazyColumn(Modifier.fillMaxSize(), state = episodeList, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(seasonEpisodes, key = { it.id }) { ep ->
                            val p = state.progress.let { it.version; it.get(shown.accountId, Kind.EPISODE, ep.id) }
                            val now = ep.id == playingId && playingEntry?.key == shown.key
                            val ready = ep.id == readyId
                            Row(
                                Modifier.fillMaxWidth().background(if (now) Palette.Wine else if (ready) Palette.Wine.copy(alpha = 0.35f) else Color.Transparent)
                                    .combinedClickable { state.playEpisode(shown, ep, episodes, p?.positionMs?.takeIf { it > 5_000 } ?: 0) }.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.width(80.dp).height(46.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Surface)) {
                                    Logo(ep.image ?: shown.logo, Modifier.fillMaxSize(), ContentScale.Crop, name = ep.title)
                                    if (p != null && p.positionMs > 0 && p.durationMs > 0) {
                                        LinearProgressIndicator(progress = { (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) }, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("${ep.number}. ${ep.title}", maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                    val extra = listOfNotNull(ep.durationSecs?.takeIf { it > 0 }?.let { "${it / 60} min" }, if (p?.watched == true) "✓" else null).joinToString("  ")
                                    if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                                    // the place where the user stopped
                                    if (ready) {
                                        val minute = p?.positionMs?.takeIf { it > 5_000 }
                                        Text(if (minute != null) t("detail_resume", clock(minute)) else t("detail_play"), style = MaterialTheme.typography.labelMedium, color = Palette.WineSoft, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                // download and watched stand at the right of each episode (the same as on the detail page)
                                val dl = state.downloadOf(shown.accountId, Kind.EPISODE, ep.id)
                                val watched = p?.watched == true
                                Tip(t("dl_download")) {
                                    IconButton(enabled = dl == null, onClick = { state.downloadEpisodes(shown, listOf(ep)) }, modifier = Modifier.width(36.dp)) {
                                        Icon(Icons.Filled.Download, t("dl_download"), tint = if (dl?.status == DlStatus.DONE) Palette.WineSoft else Color.White.copy(alpha = if (dl == null) 0.8f else 0.4f))
                                    }
                                }
                                Tip(t(if (watched) "detail_mark_unwatched" else "detail_mark_watched")) {
                                    IconButton(onClick = { state.progress.setWatched(shown.accountId, Kind.EPISODE, listOf(ep.id), !watched) }, modifier = Modifier.width(36.dp)) {
                                        Icon(Icons.Filled.CheckCircle, t(if (watched) "detail_mark_unwatched" else "detail_mark_watched"), tint = if (watched) Palette.WineSoft else Color.White.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    TextInput(
                        itemFilter, { itemFilter = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).then(typingFocus),
                        placeholder = { Text(t(if (isSeries) "vod_filter_series" else "vod_filter_movies", inCategory.size)) },
                        trailingIcon = { if (itemFilter.isNotEmpty()) IconButton(onClick = { itemFilter = "" }) { Icon(Icons.Filled.Close, t("action_close")) } },
                    )
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(items, key = { it.key }) { e ->
                            val chosen = e.key == shown.key
                            Row(
                                Modifier.fillMaxWidth().background(if (chosen) Palette.WineDeep else Color.Transparent)
                                    .combinedClickable(
                                        onDoubleClick = { if (!isSeries) state.playMovie(e) },
                                        onClick = { shown = e; if (isSeries) drilled = true },
                                    ).padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(Modifier.width(44.dp).height(64.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Surface)) { Logo(e.logo, Modifier.fillMaxSize(), ContentScale.Crop, name = e.name) }
                                Text(TitleParser.parse(e.name).title.ifBlank { e.name }, Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            // the preview, its controls and the details
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.weight(0.5f).fillMaxWidth()) {
                    Box(Modifier.weight(0.62f).fillMaxHeight().background(Color.Black), contentAlignment = Alignment.Center) {
                        // until something plays the poster is shown
                        if (controller.current == null) Logo(shown.logo, Modifier.fillMaxSize().padding(8.dp), ContentScale.Fit, name = shown.name) else video(Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(0.38f).fillMaxHeight().background(Palette.Surface).padding(12.dp), verticalArrangement = Arrangement.Center) {
                        val badge = if (shownSeries && playing != null) "S${playing.season} · E${playing.number}" else t(if (shownSeries) "nav_series" else "nav_movies")
                        Text(badge, Modifier.background(Palette.Wine, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelLarge)
                        Text(TitleParser.parse(shown.name).title.ifBlank { shown.name }, Modifier.padding(top = 8.dp), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        if (shownSeries && playing != null) Text(playing.title, Modifier.padding(top = 6.dp), color = Palette.WineSoft, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (!shownSeries) {
                            val dl = state.downloadOf(shown.accountId, Kind.MOVIE, shown.remoteId)
                            val seen = state.progress.let { it.version; it.get(shown.accountId, Kind.MOVIE, shown.remoteId) }?.watched == true
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Tip(t("dl_download")) {
                                    androidx.compose.material3.OutlinedButton(enabled = dl == null, onClick = { state.downloadMovie(shown) }) {
                                        Icon(Icons.Filled.Download, null)
                                        Text(t(if (dl?.status == DlStatus.DONE) "dl_downloaded" else if (dl != null) "dl_running" else "dl_download"), Modifier.padding(start = 6.dp))
                                    }
                                }
                                Tip(t(if (seen) "detail_mark_unwatched" else "detail_mark_watched")) {
                                    IconButton(onClick = { state.progress.setWatched(shown.accountId, Kind.MOVIE, listOf(shown.remoteId), !seen) }) {
                                        Icon(Icons.Filled.CheckCircle, t(if (seen) "detail_mark_unwatched" else "detail_mark_watched"), tint = if (seen) Palette.WineSoft else Color.White.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                        if (controller.current == null || playingEntry?.key != shown.key) {
                            // nothing plays yet: the button starts the movie, or the episode that comes next
                            val saved = if (shownSeries) null else state.progress.let { it.version; it.get(shown.accountId, Kind.MOVIE, shown.remoteId) }
                            val resume = (if (shownSeries) nextEpisode?.episode?.let { state.progress.get(shown.accountId, Kind.EPISODE, it.id) } else saved)?.positionMs?.takeIf { it > 5_000 } ?: 0
                            if (!shownSeries || nextEpisode != null) {
                                androidx.compose.material3.Button(
                                    onClick = { if (shownSeries) nextEpisode?.let { state.playEpisode(shown, it.episode, episodes, resume) } else state.playMovie(shown, resume) },
                                    modifier = Modifier.padding(top = 10.dp),
                                ) {
                                    Icon(Icons.Filled.PlayArrow, null)
                                    Text(
                                        when {
                                            shownSeries && nextEpisode != null ->
                                                t(if (nextEpisode.continuing) "detail_continue_episode" else "detail_play_episode", nextEpisode.episode.season, nextEpisode.episode.number) +
                                                    (if (resume > 0) " · " + t("detail_resume", clock(resume)) else "")
                                            resume > 0 -> t("detail_resume", clock(resume))
                                            else -> t("detail_play")
                                        },
                                        Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                // the controls of the player, under the picture
                ControlBar(state, controller, Modifier, floating = false)
                Column(Modifier.weight(0.5f).fillMaxWidth().background(Palette.Surface).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    if (shownSeries) {
                        val i = seriesInfo
                        Facts(i?.rating ?: shown.rating, playing?.plot ?: i?.plot, i?.director, i?.cast, i?.releaseDate, i?.genre, playing?.durationSecs, i?.trailer)
                    } else {
                        val v = vod
                        Facts(v?.rating ?: shown.rating, v?.plot, v?.director, v?.cast, v?.releaseDate, v?.genre, v?.durationSecs, v?.trailer)
                    }
                }
            }
        }
    }
}
