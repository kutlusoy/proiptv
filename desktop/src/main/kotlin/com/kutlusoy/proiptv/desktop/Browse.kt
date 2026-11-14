package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.kutlusoy.proiptv.net.XtreamEpisode
import com.kutlusoy.proiptv.parse.TitleParser

private const val FAV_PREFIX = "fav:"
private const val ALL_KEY = "__all"

@Composable
fun BrowseScreen(state: AppState, kind: Int) {
    val data = state.data
    val enabled = state.accounts.filter { it.enabled }
    if (enabled.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t("main_no_accounts_title"), style = MaterialTheme.typography.headlineMedium)
            Text(t("main_no_accounts_text"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        }
        return
    }
    state.series?.takeIf { kind == Kind.SERIES }?.let { SeriesDetail(state, it); return }
    state.movie?.takeIf { kind == Kind.MOVIE }?.let { MovieDetail(state, it); return }

    var category by remember(kind) { mutableStateOf(ALL_KEY) }
    var query by remember(kind) { mutableStateOf("") }
    val categories = data.categories(kind)
    val all = data.entries(kind)
    val q = query.trim()
    val shown = remember(all, category, q, state.favorites, state.guide, state.tick / 60) {
        all.asSequence()
            .filter { e ->
                when {
                    category == ALL_KEY -> true
                    category.startsWith(FAV_PREFIX) -> state.inList(e, category.removePrefix(FAV_PREFIX))
                    else -> e.categoryId == category.substringAfter('|') && e.accountId == category.substringBefore('|')
                }
            }
            .filter { e ->
                q.isEmpty() || e.name.contains(q, ignoreCase = true) ||
                    (kind == Kind.LIVE && state.guide.nowNext(e.accountId, e.epgId, state.tick).first?.title?.contains(q, ignoreCase = true) == true)
            }
            .toList()
            .let { list ->
                // a favorites list keeps the order that was set by hand
                if (category.startsWith(FAV_PREFIX)) {
                    val order = state.favoriteOrder(category.removePrefix(FAV_PREFIX))
                    list.sortedBy { order[it.key] ?: Int.MAX_VALUE }
                } else {
                    list
                }
            }
    }
    val lists = state.listsOf(kind)
    // the chosen favorites list may have been deleted
    if (category.startsWith(FAV_PREFIX) && lists.none { it.id == category.removePrefix(FAV_PREFIX) }) category = ALL_KEY
    val inFavorites = category.startsWith(FAV_PREFIX)
    val currentList = lists.firstOrNull { it.id == category.removePrefix(FAV_PREFIX) }
    var listDialog by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val options = buildList<Pair<String, String>> {
                lists.forEach { add("$FAV_PREFIX${it.id}" to if (lists.size == 1) t("fav_title") else "\u2605 ${it.name}") }
                add(ALL_KEY to t("live_all"))
                categories.forEach { add("${it.accountId}|${it.id}" to it.name.ifBlank { t("live_other") }) }
            }
            DropdownField(options.firstOrNull { it.first == category }?.second ?: t("live_all"), options, { category = it }, Modifier.weight(0.45f).widthIn(min = 160.dp, max = 360.dp))
            OutlinedTextField(
                query, { query = it }, singleLine = true, modifier = Modifier.weight(0.55f),
                placeholder = { Text(t(if (kind == Kind.LIVE) "live_search" else "live_search")) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null) } },
            )
            IconButton(onClick = { state.reload() }, enabled = !state.loading && !state.guideLoading) { Icon(Icons.Filled.Refresh, t("settings_refresh")) }
        }
        if (inFavorites && currentList != null) {
            var menu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                Text(t("fav_hint_touch").substringBefore('.') + ".", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, t("action_more")) }
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text(t("fav_new_list")) }, onClick = { menu = false; listDialog = "new" })
                        androidx.compose.material3.DropdownMenuItem(text = { Text(t("fav_rename")) }, onClick = { menu = false; listDialog = "rename" })
                        if (lists.size > 1) androidx.compose.material3.DropdownMenuItem(text = { Text(t("action_delete")) }, onClick = { menu = false; state.deleteList(currentList.id) })
                    }
                }
            }
        }
        data.errors.forEach { (id, message) ->
            val name = state.accounts.firstOrNull { it.id == id }?.name ?: id
            Text("$name: $message", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (state.loading) Text(t("series_loading"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.padding(8.dp))
        else if (shown.isEmpty()) Text(t("live_no_results"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(8.dp))

        if (kind == Kind.LIVE || inFavorites) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(shown, key = { _, e -> e.key }) { index, e ->
                    ChannelRow(
                        state, e,
                        onMove = if (inFavorites && q.isEmpty()) { delta -> state.moveFavorite(currentList!!.id, e, delta) } else null,
                    ) {
                        if (kind == Kind.LIVE) state.player = PlayRequest.Live(shown, index)
                        else if (kind == Kind.SERIES) state.series = e else state.movie = e
                    }
                }
            }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(shown, key = { it.key }) { e ->
                    Poster(state, e) { if (kind == Kind.SERIES) state.series = e else state.movie = e }
                }
            }
        }
    }
    listDialog?.let { mode ->
        var name by remember(mode) { mutableStateOf(if (mode == "rename") currentList?.name.orEmpty() else "") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { listDialog = null },
            title = { Text(t("fav_list_name")) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = name.isNotBlank(), onClick = {
                    if (mode == "rename") currentList?.let { state.renameList(it.id, name) } else category = FAV_PREFIX + state.createList(kind, name)
                    listDialog = null
                }) { Text(t("action_save")) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { listDialog = null }) { Text(t("action_cancel")) } },
        )
    }
}

@Composable
private fun ChannelRow(state: AppState, e: Entry, onMove: ((Int) -> Unit)? = null, onClick: () -> Unit) {
    val info = remember(e.name) { TitleParser.parse(e.name) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Logo(e.logo, Modifier.size(width = 64.dp, height = 40.dp))
        if (info.country.isNotEmpty()) Badge(info.country)
        val now = state.guide.nowNext(e.accountId, e.epgId, state.tick).first
        Column(Modifier.weight(1f)) {
            Text(info.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (now != null) {
                Text(now.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { ((state.tick - now.start).toFloat() / (now.end - now.start).coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }
        if (info.year > 0) Badge(info.year.toString())
        if (onMove != null) {
            IconButton(onClick = { onMove(-1) }) { Icon(Icons.Filled.KeyboardArrowUp, t("fav_move_up")) }
            IconButton(onClick = { onMove(1) }) { Icon(Icons.Filled.KeyboardArrowDown, t("fav_move_down")) }
        }
        IconButton(onClick = { state.toggleFavorite(e) }) {
            Icon(if (state.isFavorite(e)) Icons.Filled.Star else Icons.Filled.StarBorder, null, tint = if (state.isFavorite(e)) Color(0xFFFFC107) else Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f),
        modifier = Modifier.background(Palette.Wine.copy(alpha = 0.35f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun Poster(state: AppState, e: Entry, onClick: () -> Unit) {
    val info = remember(e.name) { TitleParser.parse(e.name) }
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(2.dp)) {
        Box {
            Logo(e.logo, Modifier.fillMaxWidth().aspectRatio(0.68f), ContentScale.Crop)
            if (state.isFavorite(e)) Icon(Icons.Filled.Star, null, Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp), tint = Color(0xFFFFC107))
        }
        Text(info.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        val tags = listOf(info.country, if (info.year > 0) info.year.toString() else "").filter { it.isNotEmpty() }.joinToString("  ")
        if (tags.isNotEmpty()) Text(tags, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
    }
}

@Composable
private fun DetailHeader(state: AppState, e: Entry, onBack: () -> Unit) {
    val info = remember(e.name) { TitleParser.parse(e.name) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("action_close")) }
        Text(info.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (info.year > 0) Badge(info.year.toString())
        IconButton(onClick = { state.toggleFavorite(e) }) {
            Icon(if (state.isFavorite(e)) Icons.Filled.Star else Icons.Filled.StarBorder, t(if (state.isFavorite(e)) "fav_remove" else "fav_add"), tint = if (state.isFavorite(e)) Color(0xFFFFC107) else Color.White)
        }
    }
}

@Composable
private fun MovieDetail(state: AppState, e: Entry) {
    val saved = state.progress.let { it.version; it.get(e.accountId, Kind.MOVIE, e.remoteId) }
    val resume = saved?.takeIf { it.positionMs > 5_000 }
    fun play(startMs: Long) {
        val local = state.downloadOf(e.accountId, Kind.MOVIE, e.remoteId)?.let { state.localPlayable(it) }
        (local ?: state.library.playable(e))?.let { state.player = PlayRequest.Single(it, startMs) }
    }
    val download = state.downloadOf(e.accountId, Kind.MOVIE, e.remoteId)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DetailHeader(state, e) { state.movie = null }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Logo(e.logo, Modifier.width(220.dp).aspectRatio(0.68f), ContentScale.Crop)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { play(resume?.positionMs ?: 0) }) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Text(if (resume != null) t("detail_resume", clock(resume.positionMs)) else t("detail_play"), Modifier.padding(start = 6.dp))
                    }
                    if (resume != null) androidx.compose.material3.OutlinedButton(onClick = { play(0) }) { Text(t("detail_restart")) }
                    androidx.compose.material3.OutlinedButton(enabled = download == null, onClick = { state.downloadMovie(e) }) {
                        Icon(Icons.Filled.Download, null)
                        Text(t(if (download?.status == DlStatus.DONE) "dl_downloaded" else if (download != null) "dl_running" else "dl_download"), Modifier.padding(start = 6.dp))
                    }
                    val watched = saved?.watched == true
                    androidx.compose.material3.OutlinedButton(onClick = { state.progress.setWatched(e.accountId, Kind.MOVIE, listOf(e.remoteId), !watched) }) {
                        Text(t(if (watched) "detail_mark_unwatched" else "detail_mark_watched"))
                    }
                }
                if (saved?.watched == true) Text("\u2713 " + t("detail_watched"), color = Palette.WineSoft)
                if (resume != null && resume.durationMs > 0) {
                    androidx.compose.material3.LinearProgressIndicator(progress = { (resume.positionMs.toFloat() / resume.durationMs).coerceIn(0f, 1f) }, Modifier.width(260.dp))
                }
                e.rating?.let { Text("\u2605 $it") }
            }
        }
    }
}

@Composable
private fun SeriesDetail(state: AppState, e: Entry) {
    val episodes by produceState<List<XtreamEpisode>?>(null, e.key) { value = runCatching { state.library.episodes(e) }.getOrDefault(emptyList()) }
    var season by remember(e.key) { mutableStateOf(-1) }
    val list = episodes.orEmpty()
    val seasons = list.map { it.season }.distinct()
    val active = if (season in seasons) season else seasons.firstOrNull() ?: 1
    val version = state.progress.version
    fun progressOf(id: String) = state.progress.get(e.accountId, Kind.EPISODE, id)
    val next = remember(list, version) { SeriesProgress.next(list, ::progressOf) }
    fun play(ep: XtreamEpisode, startMs: Long = 0) {
        val local = state.downloadOf(e.accountId, Kind.EPISODE, ep.id)?.let { state.localPlayable(it) }
        (local ?: state.library.episodePlayable(e, ep))?.let { state.player = PlayRequest.Single(it, startMs) }
    }
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailHeader(state, e) { state.series = null }
        if (episodes == null) Text(t("series_loading"))
        next?.let { n ->
            val ep = n.episode
            val resume = progressOf(ep.id)?.positionMs?.takeIf { it > 5_000 } ?: 0
            Button(onClick = { play(ep, resume) }) {
                Icon(Icons.Filled.PlayArrow, null)
                Text(t(if (n.continuing) "detail_continue_episode" else "detail_play_episode", ep.season, ep.number), Modifier.padding(start = 6.dp))
            }
        }
        if (seasons.isNotEmpty()) {
            val seasonEpisodes = list.filter { it.season == active }
            val allWatched = seasonEpisodes.isNotEmpty() && seasonEpisodes.all { progressOf(it.id)?.watched == true }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DropdownField(t("series_season", active), seasons.map { it to t("series_season", it) }, { season = it }, Modifier.width(240.dp))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, t("action_more")) }
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(t("dl_season")) },
                            onClick = { menu = false; state.downloadEpisodes(e, seasonEpisodes) },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(t(if (allWatched) "detail_season_unwatched" else "detail_season_watched")) },
                            onClick = { menu = false; state.progress.setWatched(e.accountId, Kind.EPISODE, seasonEpisodes.map { it.id }, !allWatched) },
                        )
                    }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(list.filter { it.season == active }, key = { _, ep -> ep.id }) { _, ep ->
                val p = progressOf(ep.id)
                val watched = p?.watched == true
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { play(ep, p?.positionMs?.takeIf { it > 5_000 } ?: 0) }.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Logo(ep.image ?: e.logo, Modifier.size(width = 140.dp, height = 80.dp), ContentScale.Crop)
                        if (p != null && p.positionMs > 0 && p.durationMs > 0) {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) },
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${ep.number}. ${ep.title}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ep.plot?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.75f)) }
                    }
                    val dl = state.downloadOf(e.accountId, Kind.EPISODE, ep.id)
                    IconButton(enabled = dl == null, onClick = { state.downloadEpisodes(e, listOf(ep)) }) {
                        Icon(Icons.Filled.Download, t("dl_download"), tint = if (dl?.status == DlStatus.DONE) Palette.WineSoft else Color.White.copy(alpha = if (dl == null) 0.8f else 0.4f))
                    }
                    IconButton(onClick = { state.progress.setWatched(e.accountId, Kind.EPISODE, listOf(ep.id), !watched) }) {
                        Icon(
                            Icons.Filled.CheckCircle, t(if (watched) "detail_mark_unwatched" else "detail_mark_watched"),
                            tint = if (watched) Palette.WineSoft else Color.White.copy(alpha = 0.3f),
                        )
                    }
                    Icon(Icons.Filled.PlayArrow, null)
                }
            }
        }
    }
}
