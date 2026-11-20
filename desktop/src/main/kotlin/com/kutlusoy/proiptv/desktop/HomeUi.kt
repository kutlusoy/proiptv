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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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

/** A row of posters (search) or, with [grid], a band of three rows of small tiles (the start page). */
private class HomeRow(val title: String, val kind: Int, val entries: List<Entry>, val progress: Map<String, Float> = emptyMap(), val grid: Boolean = false)

/** The start page: continue watching, the own lists and the newest titles in rows, and one search over everything. */
@Composable
fun HomeScreen(state: AppState) {
    val data = state.data
    if (state.accounts.none { it.enabled }) {
        NoAccountsPane(state)
        return
    }
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val version = state.progress.version
    val rows = remember(data, state.favorites, state.favLists, version, q, Strings.language) {
        if (q.length >= 2) searchRows(state, q) else homeRows(state)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        TextInput(
            query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            placeholder = { Text(t("home_search")) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) Tip(t("action_close")) { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, t("action_close")) } } },
        )
        if (rows.isEmpty() && q.isEmpty() && (state.loading || state.guideLoading)) SkeletonRows()
        if (rows.isEmpty() && !(q.isEmpty() && (state.loading || state.guideLoading))) {
            Text(t(if (q.isNotEmpty()) "home_no_results" else "home_empty"), Modifier.padding(8.dp), color = Color.White.copy(alpha = 0.7f))
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(rows) { row ->
                Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (row.grid) {
                    // up to three rows, the arrows (and the mouse wheel with the shift key) move the whole band
                    ScrollGrid(rows = minOf(3, row.entries.size), cellHeight = TILE_HEIGHT, modifier = Modifier.padding(top = 6.dp)) {
                        gridItems(row.entries, key = { it.key }) { e ->
                            HomeTile(e, row.progress[e.key], state.accountLabel(e.accountId)) { open(state, e, row.entries) }
                        }
                    }
                } else {
                    ScrollRow(Modifier.padding(top = 6.dp)) {
                        items(row.entries, key = { it.key }) { e ->
                            PosterCard(e, row.progress[e.key], state.accountLabel(e.accountId)) { open(state, e, row.entries) }
                        }
                    }
                }
            }
        }
    }
}

private val TILE_HEIGHT = 76.dp

/** A small tile for the bands of the start page: the picture on the left, title, kind and account on the right. */
@Composable
private fun HomeTile(e: Entry, progress: Float?, account: String?, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val live = e.kind == Kind.LIVE
    androidx.compose.foundation.layout.Row(
        Modifier.width(270.dp).height(TILE_HEIGHT).clip(RoundedCornerShape(10.dp)).background(if (hovered) Palette.SurfaceHigh else Palette.Surface)
            .clickable(interactionSource = hover, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(if (live) 96.dp else 54.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.25f))) {
            Logo(e.logo, Modifier.fillMaxSize().padding(if (live) 6.dp else 0.dp), if (live) ContentScale.Fit else ContentScale.Crop, name = e.name)
            if (progress != null) LinearProgressIndicator(progress = { progress }, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(TitleParser.parse(e.name).title.ifBlank { e.name }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val kindLabel = Strings.get(when (e.kind) { Kind.LIVE -> "nav_live"; Kind.MOVIE -> "nav_movies"; else -> "nav_series" })
            Text(listOfNotNull(kindLabel, account).joinToString(" \u00B7 "), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun open(state: AppState, e: Entry, siblings: List<Entry>) {
    when (e.kind) {
        Kind.LIVE -> {
            val list = siblings.filter { it.kind == Kind.LIVE }
            state.player = PlayRequest.Live(list, list.indexOf(e).coerceAtLeast(0))
        }
        Kind.MOVIE -> { state.tab = Tab.MOVIES; state.series = null; state.movie = null; state.openVod(e) }
        else -> { state.tab = Tab.SERIES; state.movie = null; state.series = null; state.openVod(e) }
    }
}

@Composable
private fun PosterCard(e: Entry, progress: Float?, account: String?, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val wide = e.kind == Kind.LIVE
    Column(Modifier.width(if (wide) 190.dp else 140.dp).scale(if (hovered) 1.05f else 1f).clickable(interactionSource = hover, indication = null, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 9f else 0.68f).clip(RoundedCornerShape(10.dp)).background(Palette.Surface)) {
            Logo(e.logo, Modifier.fillMaxSize(), if (wide) ContentScale.Fit else ContentScale.Crop, name = e.name)
            if (progress != null) LinearProgressIndicator(progress = { progress }, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
        Text(TitleParser.parse(e.name).title.ifBlank { e.name }, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        account?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

private fun homeRows(state: AppState): List<HomeRow> {
    val data = state.data
    val rows = ArrayList<HomeRow>()
    val movies = data.entries(Kind.MOVIE).associateBy { it.key }
    val seriesByKey = data.entries(Kind.SERIES).associateBy { it.key }
    val bars = HashMap<String, Float>()
    val watching = ArrayList<Entry>()
    // movies and series that were begun, the newest first, then the channels that were watched last
    state.progress.all().filter { !it.watched && it.positionMs > 5_000 }.sortedByDescending { it.updated }.forEach { p ->
        val entry = if (p.kind == Kind.MOVIE) movies["${p.account}|${Kind.MOVIE}|${p.remoteId}"]
        else seriesByKey["${p.account}|${Kind.SERIES}|${p.seriesId}"]
        if (entry != null && watching.none { it.key == entry.key }) {
            watching += entry
            if (p.durationMs > 0) bars[entry.key] = (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f)
        }
    }
    val live = data.entries(Kind.LIVE).associateBy { it.key }
    state.history.mapNotNull { live[it] }.forEach { if (watching.none { w -> w.key == it.key }) watching += it }
    if (watching.isNotEmpty()) rows += HomeRow(Strings.get("home_continue"), -1, watching.take(MAX_TILES), bars, grid = true)
    // the favorites of every area (all lists of the area together)
    for ((kind, title) in listOf(Kind.LIVE to "home_fav_live", Kind.MOVIE to "home_fav_movies", Kind.SERIES to "home_fav_series")) {
        val all = data.entries(kind)
        val shown = LinkedHashMap<String, Entry>()
        for (list in state.listsOf(kind)) {
            val order = state.favoriteOrder(list.id)
            all.filter { state.inList(it, list.id) }.sortedBy { order[it.key] ?: Int.MAX_VALUE }.forEach { shown.putIfAbsent(it.key, it) }
        }
        if (shown.isNotEmpty()) rows += HomeRow("\u2605 " + Strings.get(title), kind, shown.values.take(MAX_TILES), grid = true)
    }
    return rows
}

private const val MAX_TILES = 60

private fun searchRows(state: AppState, q: String): List<HomeRow> {
    val data = state.data
    return listOf(Kind.LIVE to "nav_live", Kind.MOVIE to "nav_movies", Kind.SERIES to "nav_series").mapNotNull { (kind, label) ->
        val found = data.entries(kind).filter { e ->
            com.kutlusoy.proiptv.parse.SearchMatch.matches(e.name + " " + (if (kind == Kind.LIVE) state.guide.nowNext(e.accountId, e.epgId, state.tick).first?.title.orEmpty() else ""), q)
        }.take(60)
        if (found.isEmpty()) null else HomeRow(Strings.get(label), kind, found)
    }
}
