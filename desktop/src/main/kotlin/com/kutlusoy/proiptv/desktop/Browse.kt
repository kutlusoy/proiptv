package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.items
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
import com.kutlusoy.proiptv.net.SeriesDetail
import com.kutlusoy.proiptv.net.SeriesInfo
import com.kutlusoy.proiptv.net.VodInfo
import com.kutlusoy.proiptv.net.trailerUrl
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

    var category by remember(kind) { mutableStateOf(state.store.config.lastCategory[kind.toString()] ?: ALL_KEY) }
    androidx.compose.runtime.LaunchedEffect(category) {
        if (state.store.config.lastCategory[kind.toString()] != category) state.store.update { c -> c.copy(lastCategory = c.lastCategory + (kind.toString() to category)) }
    }
    var query by remember(kind) { mutableStateOf("") }
    var accountFilter by remember(kind) { mutableStateOf<String?>(null) }
    val activeAccount = accountFilter?.takeIf { id -> enabled.any { it.id == id } }
    val categories = data.categories(kind).filter { activeAccount == null || it.accountId == activeAccount }
    val all = data.entries(kind).let { list -> if (activeAccount == null) list else list.filter { it.accountId == activeAccount } }
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
                categories.forEach { add("${it.accountId}|${it.id}" to ((state.accountLabel(it.accountId)?.let { n -> "$n \u00B7 " } ?: "") + it.name.ifBlank { t("live_other") })) }
            }
            if (enabled.size > 1) {
                DropdownField(
                    enabled.firstOrNull { it.id == activeAccount }?.name ?: t("live_all_accounts"),
                    listOf<Pair<String?, String>>(null to t("live_all_accounts")) + enabled.map { it.id to it.name },
                    { accountFilter = it; if (!category.startsWith(FAV_PREFIX) && category != ALL_KEY) category = ALL_KEY },
                    Modifier.weight(0.3f).widthIn(min = 140.dp, max = 260.dp),
                )
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
        state.accountLabel(e.accountId)?.let { Badge(it) }
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
        val tags = listOf(info.country, if (info.year > 0) info.year.toString() else "", state.accountLabel(e.accountId) ?: "").filter { it.isNotEmpty() }.joinToString("  ")
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
    val vod by produceState<VodInfo?>(null, e.key) { value = state.library.vodInfo(e) }
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                val v = vod
                Facts(v?.rating ?: e.rating, v?.plot, v?.director, v?.cast, v?.releaseDate, v?.genre, v?.durationSecs, v?.trailer)
            }
        }
        RelatedRow(state, e, "detail_related_movies")
    }
}

@Composable
private fun Facts(rating: String?, plot: String?, director: String?, cast: String?, released: String?, genre: String?, durationSecs: Int?, trailer: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rating?.toDoubleOrNull()?.takeIf { it > 0 }?.let { r ->
            val filled = Math.round(r / 2).toInt().coerceIn(0, 5)
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (i in 1..5) Icon(Icons.Filled.Star, null, tint = if (i <= filled) Color(0xFFFFC107) else Color.White.copy(alpha = 0.25f), modifier = Modifier.size(22.dp))
                Text("  " + "%.1f".format(r))
            }
        }
        plot?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        val facts = listOf("detail_director" to director, "detail_released" to released, "detail_cast" to cast, "detail_genre" to genre,
            "detail_runtime" to durationSecs?.takeIf { it > 0 }?.let { s -> if (s >= 3600) "${s / 3600} h ${s % 3600 / 60} min" else "${s / 60} min" })
        for ((key, value) in facts) {
            if (value.isNullOrBlank()) continue
            Row { Text(t(key) + ": ", fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f)); Text(value, Modifier.weight(1f, fill = false)) }
        }
        trailerUrl(trailer)?.let { url ->
            androidx.compose.material3.OutlinedButton(onClick = { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) } }) { Text(t("detail_trailer")) }
        }
    }
}

/** Other titles of the same category, one click away. */
@Composable
private fun RelatedRow(state: AppState, e: Entry, titleKey: String) {
    val related = remember(e.key, state.data) {
        state.data.entries(e.kind).filter { it.accountId == e.accountId && it.categoryId == e.categoryId && it.remoteId != e.remoteId }.take(30)
    }
    if (related.isEmpty()) return
    Text(t(titleKey), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(related, key = { it.key }) { r ->
            Box(Modifier.width(120.dp)) {
                Poster(state, r) { if (r.kind == Kind.SERIES) { state.series = r; state.movie = null } else { state.movie = r; state.series = null } }
            }
        }
    }
}

@Composable
private fun SeriesDetail(state: AppState, e: Entry) {
    val detail by produceState<SeriesDetail?>(null, e.key) { value = runCatching { state.library.seriesDetail(e) }.getOrDefault(SeriesDetail(null, emptyList())) }
    val episodes = detail?.episodes
    val seriesFacts by produceState<SeriesInfo?>(null, detail) { if (detail != null) value = state.library.seriesInfo(e, detail?.info) }
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
            (seriesFacts ?: detail?.info)?.let { i ->
                item { Facts(i.rating ?: e.rating, i.plot, i.director, i.cast, i.releaseDate, i.genre, null, i.trailer) }
            }
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
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) { RelatedRow(state, e, "detail_related_series") } }
        }
    }
}
