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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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

private const val FAV_KEY = "__fav"
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
    val shown = remember(all, category, q, state.favorites) {
        all.asSequence()
            .filter { e ->
                when (category) {
                    ALL_KEY -> true
                    FAV_KEY -> state.isFavorite(e)
                    else -> e.categoryId == category.substringAfter('|') && e.accountId == category.substringBefore('|')
                }
            }
            .filter { e -> q.isEmpty() || e.name.contains(q, ignoreCase = true) }
            .toList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val options = buildList<Pair<String, String>> {
                add(FAV_KEY to t("fav_title"))
                add(ALL_KEY to t("live_all"))
                categories.forEach { add("${it.accountId}|${it.id}" to it.name.ifBlank { t("live_other") }) }
            }
            DropdownField(options.firstOrNull { it.first == category }?.second ?: t("live_all"), options, { category = it }, Modifier.width(320.dp))
            OutlinedTextField(
                query, { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text(t(if (kind == Kind.LIVE) "live_search" else "live_search")) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null) } },
            )
            IconButton(onClick = { state.reload() }, enabled = !state.loading) { Icon(Icons.Filled.Refresh, t("settings_refresh")) }
        }
        data.errors.forEach { (id, message) ->
            val name = state.accounts.firstOrNull { it.id == id }?.name ?: id
            Text("$name: $message", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (state.loading) Text(t("series_loading"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.padding(8.dp))
        else if (shown.isEmpty()) Text(t("live_no_results"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(8.dp))

        if (kind == Kind.LIVE) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(shown, key = { _, e -> e.key }) { index, e ->
                    ChannelRow(state, e) { state.player = PlayRequest.Live(shown, index) }
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
}

@Composable
private fun ChannelRow(state: AppState, e: Entry, onClick: () -> Unit) {
    val info = remember(e.name) { TitleParser.parse(e.name) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Logo(e.logo, Modifier.size(width = 64.dp, height = 40.dp))
        if (info.country.isNotEmpty()) Badge(info.country)
        Text(info.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (info.year > 0) Badge(info.year.toString())
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
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DetailHeader(state, e) { state.movie = null }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Logo(e.logo, Modifier.width(220.dp).aspectRatio(0.68f), ContentScale.Crop)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { state.library.playable(e)?.let { state.player = PlayRequest.Single(it) } }) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Text(t("detail_play"), Modifier.padding(start = 6.dp))
                }
                e.rating?.let { Text("★ $it") }
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
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailHeader(state, e) { state.series = null }
        if (episodes == null) Text(t("series_loading"))
        if (seasons.isNotEmpty()) {
            DropdownField(t("series_season", active), seasons.map { it to t("series_season", it) }, { season = it }, Modifier.width(240.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(list.filter { it.season == active }, key = { _, ep -> ep.id }) { _, ep ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { state.library.episodePlayable(e, ep)?.let { state.player = PlayRequest.Single(it) } }.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Logo(ep.image ?: e.logo, Modifier.size(width = 140.dp, height = 80.dp), ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text("${ep.number}. ${ep.title}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ep.plot?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.75f)) }
                    }
                    Icon(Icons.Filled.PlayArrow, null)
                }
            }
        }
    }
}
