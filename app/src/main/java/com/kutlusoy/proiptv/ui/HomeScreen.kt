package com.kutlusoy.proiptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.Source
import com.kutlusoy.proiptv.parse.TitleParser
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private class Card(val key: String, val title: String, val logo: String?, val live: Boolean, val progress: Float? = null, val open: () -> Unit)

private class Row(val title: String, val cards: List<Card>)

/** The start page: continue watching, the own lists and the first titles in rows, and one search over everything. */
@Composable
fun HomeScreen(vm: MainViewModel) {
    val accounts by vm.accounts.collectAsState()
    val enabled = accounts.filter { it.enabled }
    val db = vm.repo.db
    var movie by remember { mutableStateOf<Item?>(null) }
    var series by remember { mutableStateOf<Item?>(null) }
    val scope = rememberCoroutineScope()

    series?.let {
        BackHandler { series = null }
        SeriesDetailScreen(vm, it) { series = null }
        return
    }
    movie?.let {
        BackHandler { movie = null }
        MovieDetail(vm, it) { movie = null }
        return
    }
    if (enabled.isEmpty()) {
        Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.main_no_accounts_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.main_no_accounts_text), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }
        }
        return
    }
    val ids = enabled.map { it.id }
    var query by remember { mutableStateOf("") }
    val q = query.trim()

    fun openItem(item: Item) {
        when (item.kind) {
            Kind.MOVIE -> movie = item
            Kind.SERIES -> series = item
        }
    }

    fun itemCard(item: Item, progress: Float? = null) = Card("${item.accountId}|${item.kind}|${item.remoteId}", TitleParser.parse(item.name).title.ifBlank { item.name }, item.logo, false, progress) { openItem(item) }

    val continueRow by vm.repo.db.progress().continueWatching().collectAsState(initial = emptyList())
    val continueCards by produceState(emptyList<Card>(), continueRow, enabled) {
        val seen = HashSet<String>()
        val out = ArrayList<Card>()
        for (p in continueRow) {
            val item = if (p.kind == Kind.MOVIE) db.items().find(p.accountId, Kind.MOVIE, p.remoteId)
            else if (p.seriesRemoteId.isNotEmpty()) db.items().find(p.accountId, Kind.SERIES, p.seriesRemoteId) else null
            if (item == null || !seen.add("${item.accountId}|${item.kind}|${item.remoteId}")) continue
            out += itemCard(item, if (p.durationMs > 0) (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) else null)
        }
        value = out
    }

    val lists by vm.favLists.collectAsState()
    val listRows by remember(lists) {
        if (lists.isEmpty()) flowOf(emptyList()) else combine(lists.map { l -> db.favorites().rows(l.id, l.kind) }) { arrays -> lists.zip(arrays.toList()) }
    }.collectAsState(initial = emptyList())

    val labels = mapOf(Kind.LIVE to R.string.nav_live, Kind.MOVIE to R.string.nav_movies, Kind.SERIES to R.string.nav_series)
    val continueTitle = stringResource(R.string.home_continue)
    val area = labels.mapValues { stringResource(it.value) }

    val firstRows by produceState(emptyList<Row>(), enabled) {
        value = listOf(Kind.MOVIE, Kind.SERIES).mapNotNull { kind ->
            val top = db.items().top(kind, ids, 30)
            if (top.isEmpty()) null else Row(area.getValue(kind), top.map { itemCard(it) })
        }
    }
    val found by produceState(emptyList<Row>(), q, enabled) {
        if (q.length < 2) { value = emptyList(); return@produceState }
        kotlinx.coroutines.delay(250)
        value = listOf(Kind.LIVE, Kind.MOVIE, Kind.SERIES).mapNotNull { kind ->
            val hits = db.items().search(kind, ids, q, 40)
            if (hits.isEmpty()) null else Row(area.getValue(kind), hits.map { item ->
                if (kind == Kind.LIVE) Card("${item.accountId}|0|${item.remoteId}", item.name, item.logo, true) {
                    vm.player.value = PlayRequest.Zap(Source.Browse(Kind.LIVE, ids, null, q), hits.indexOf(item).coerceAtLeast(0))
                } else itemCard(item)
            })
        }
    }

    val rows = if (q.length >= 2) found else buildList {
        if (continueCards.isNotEmpty()) add(Row(continueTitle, continueCards))
        for ((list, favs) in listRows) {
            if (favs.isEmpty()) continue
            add(
                Row(
                    "★ ${list.name} · ${area.getValue(list.kind)}",
                    favs.mapIndexed { i, f ->
                        Card("${list.id}|${f.accountId}|${f.kind}|${f.remoteId}", TitleParser.parse(f.name).title.ifBlank { f.name }, f.logo, f.kind == Kind.LIVE) {
                            if (f.kind == Kind.LIVE) vm.player.value = PlayRequest.Zap(Source.Favs(list.id), i)
                            else f.itemId?.let { id -> scope.launch { db.items().get(id)?.let { openItem(it) } } }
                        }
                    },
                ),
            )
        }
        addAll(firstRows)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.home_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null) } },
        )
        if (q.length >= 2 && rows.isEmpty()) Text(stringResource(R.string.home_no_results), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(rows) { row ->
                Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                    items(row.cards, key = { it.key }) { HomeCard(it) }
                }
            }
        }
    }
}

@Composable
private fun HomeCard(card: Card) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "home")
    Column(
        Modifier.width(if (card.live) 170.dp else 130.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.hasFocus }
            .focusHighlight()
            .clickable(onClick = card.open),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (card.live) 16f / 9f else 0.68f).clip(RoundedCornerShape(8.dp))) {
            Logo(card.logo, Modifier.fillMaxSize(), if (card.live) ContentScale.Fit else ContentScale.Crop)
            card.progress?.let { LinearProgressIndicator(progress = { it }, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) }
        }
        Text(card.title, Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}
