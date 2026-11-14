package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.parse.TitleParser

private class HomeRow(val title: String, val kind: Int, val entries: List<Entry>, val progress: Map<String, Float> = emptyMap())

/** The start page: continue watching, the own lists and the newest titles in rows, and one search over everything. */
@Composable
fun HomeScreen(state: AppState) {
    val data = state.data
    if (state.accounts.none { it.enabled }) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t("main_no_accounts_title"), style = MaterialTheme.typography.headlineMedium)
            Text(t("main_no_accounts_text"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        }
        return
    }
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val version = state.progress.version
    val rows = remember(data, state.favorites, state.favLists, version, q, Strings.language) {
        if (q.length >= 2) searchRows(state, q) else homeRows(state)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            placeholder = { Text(t("home_search")) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null) } },
        )
        if (rows.isEmpty()) {
            Text(t(if (q.isNotEmpty()) "home_no_results" else "main_loading"), Modifier.padding(8.dp), color = Color.White.copy(alpha = 0.7f))
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(rows) { row ->
                Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(row.entries, key = { it.key }) { e ->
                        PosterCard(e, row.progress[e.key]) { open(state, e, row.entries) }
                    }
                }
            }
        }
    }
}

private fun open(state: AppState, e: Entry, siblings: List<Entry>) {
    when (e.kind) {
        Kind.LIVE -> {
            val list = siblings.filter { it.kind == Kind.LIVE }
            state.player = PlayRequest.Live(list, list.indexOf(e).coerceAtLeast(0))
        }
        Kind.MOVIE -> { state.tab = Tab.MOVIES; state.series = null; state.movie = e }
        else -> { state.tab = Tab.SERIES; state.movie = null; state.series = e }
    }
}

@Composable
private fun PosterCard(e: Entry, progress: Float?, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val wide = e.kind == Kind.LIVE
    Column(Modifier.width(if (wide) 190.dp else 140.dp).scale(if (hovered) 1.05f else 1f).clickable(interactionSource = hover, indication = null, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else 0.68f).clip(RoundedCornerShape(10.dp)).background(Palette.Surface)) {
            Logo(e.logo, Modifier.fillMaxSize(), if (wide) ContentScale.Fit else ContentScale.Crop)
            if (progress != null) LinearProgressIndicator(progress = { progress }, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
        Text(TitleParser.parse(e.name).title.ifBlank { e.name }, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun homeRows(state: AppState): List<HomeRow> {
    val data = state.data
    val rows = ArrayList<HomeRow>()
    val movies = data.entries(Kind.MOVIE).associateBy { it.key }
    val seriesByKey = data.entries(Kind.SERIES).associateBy { it.key }
    val bars = HashMap<String, Float>()
    val continuing = ArrayList<Entry>()
    state.progress.all().filter { !it.watched && it.positionMs > 5_000 }.sortedByDescending { it.updated }.forEach { p ->
        val entry = if (p.kind == Kind.MOVIE) movies["${p.account}|${Kind.MOVIE}|${p.remoteId}"]
        else seriesByKey["${p.account}|${Kind.SERIES}|${p.seriesId}"]
        if (entry != null && continuing.none { it.key == entry.key }) {
            continuing += entry
            if (p.durationMs > 0) bars[entry.key] = (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f)
        }
    }
    if (continuing.isNotEmpty()) rows += HomeRow(Strings.get("home_continue"), -1, continuing.take(30), bars)
    val areas = listOf(Kind.LIVE to "nav_live", Kind.MOVIE to "nav_movies", Kind.SERIES to "nav_series")
    for ((kind, label) in areas) {
        val all = data.entries(kind).associateBy { it.key }
        for (list in state.listsOf(kind)) {
            val order = state.favoriteOrder(list.id)
            val entries = all.values.filter { state.inList(it, list.id) }.sortedBy { order[it.key] ?: Int.MAX_VALUE }
            if (entries.isNotEmpty()) rows += HomeRow("★ ${list.name} · ${Strings.get(label)}", kind, entries.take(40))
        }
    }
    for ((kind, label) in areas.drop(1)) {
        val list = data.entries(kind)
        if (list.isNotEmpty()) rows += HomeRow(Strings.get(label), kind, list.take(30))
    }
    val live = data.entries(Kind.LIVE)
    if (live.isNotEmpty() && rows.none { it.kind == Kind.LIVE }) rows += HomeRow(Strings.get("nav_live"), Kind.LIVE, live.take(30))
    return rows
}

private fun searchRows(state: AppState, q: String): List<HomeRow> {
    val data = state.data
    return listOf(Kind.LIVE to "nav_live", Kind.MOVIE to "nav_movies", Kind.SERIES to "nav_series").mapNotNull { (kind, label) ->
        val found = data.entries(kind).filter { e ->
            e.name.contains(q, ignoreCase = true) ||
                (kind == Kind.LIVE && state.guide.nowNext(e.accountId, e.epgId, state.tick).first?.title?.contains(q, ignoreCase = true) == true)
        }.take(60)
        if (found.isEmpty()) null else HomeRow(Strings.get(label), kind, found)
    }
}
