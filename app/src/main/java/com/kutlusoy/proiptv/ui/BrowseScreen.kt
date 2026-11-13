package com.kutlusoy.proiptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.Program
import com.kutlusoy.proiptv.data.Source
import com.kutlusoy.proiptv.net.XtreamEpisode
import kotlinx.coroutines.delay

private val StarOn = Color(0xFFFFC107)

@Composable
fun BrowseScreen(kind: Int, vm: MainViewModel) {
    val accounts by vm.accounts.collectAsState()
    val favLists by vm.favLists.collectAsState()
    val favKeys by vm.favKeys.collectAsState()
    val enabled = accounts.filter { it.enabled }
    var accountSel by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = accountSel?.takeIf { id -> enabled.any { it.id == id } }
    val ids = selected?.let { listOf(it) } ?: enabled.map { it.id }
    val effective = selected ?: enabled.singleOrNull()?.id
    var category by rememberSaveable(kind, effective) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(kind) { mutableStateOf("") }
    var favMode by rememberSaveable { mutableStateOf(false) }
    var favList by rememberSaveable { mutableStateOf<Long?>(null) }
    var series by remember { mutableStateOf<Item?>(null) }
    var favDialog by remember { mutableStateOf<Item?>(null) }

    if (enabled.isEmpty()) {
        Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.main_no_accounts_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.main_no_accounts_text), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            }
        }
        return
    }

    series?.let {
        BackHandler { series = null }
        SeriesDetail(vm, it) { series = null }
        return
    }

    val categories by (effective?.let { vm.categories(it, kind) } ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.live_search)) },
            )
            if (enabled.size > 1) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.focusHighlight()) {
                        Text(enabled.firstOrNull { it.id == selected }?.name ?: stringResource(R.string.live_all_accounts), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.live_all_accounts)) }, onClick = { accountSel = null; menu = false })
                        enabled.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { accountSel = a.id; menu = false }) }
                    }
                }
            }
        }
        LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (kind == Kind.LIVE) {
                item {
                    FilterChip(
                        selected = favMode, onClick = { favMode = true },
                        label = { Text(stringResource(R.string.fav_title)) },
                        leadingIcon = { Icon(Icons.Filled.Star, null, tint = StarOn, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.focusHighlight(),
                    )
                }
            }
            item {
                FilterChip(selected = !favMode && category == null, onClick = { favMode = false; category = null }, label = { Text(stringResource(R.string.live_all)) }, modifier = Modifier.focusHighlight())
            }
            if (effective != null) {
                items(categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = !favMode && category == c.remoteId, onClick = { favMode = false; category = c.remoteId },
                        label = { Text(c.name.ifBlank { stringResource(R.string.live_other) }) }, modifier = Modifier.focusHighlight(),
                    )
                }
            }
        }

        if (kind == Kind.LIVE && favMode) {
            FavoritesPane(vm, favList) { favList = it }
        } else {
            val cat = if (effective != null) category else null
            val flow = remember(kind, ids, cat, query) { vm.pager(kind, ids, cat, query) }
            val paged = flow.collectAsLazyPagingItems()
            val source = Source.Browse(kind, ids, cat, query)
            if (paged.itemCount == 0) {
                Text(stringResource(R.string.live_no_results), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(16.dp))
            }
            if (kind == Kind.LIVE) {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(paged.itemCount, key = paged.itemKey { it.id }) { i ->
                        val item = paged[i] ?: return@items
                        val fav = Triple(item.accountId, item.kind, item.remoteId) in favKeys
                        ChannelRow(
                            vm, item.name, item.logo, item.accountId, item.epgId, fav, true,
                            onClick = { vm.player.value = PlayRequest.Zap(source, i) },
                            onStar = {
                                if (favLists.size <= 1) {
                                    val list = favLists.firstOrNull() ?: return@ChannelRow
                                    vm.run { vm.repo.setFavorite(list.id, item.accountId, item.kind, item.remoteId, item.name, !fav) }
                                } else {
                                    favDialog = item
                                }
                            },
                        )
                    }
                }
            } else {
                LazyVerticalGrid(GridCells.Adaptive(130.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(paged.itemCount, key = paged.itemKey { it.id }) { i ->
                        val item = paged[i] ?: return@items
                        PosterCard(item) { if (item.kind == Kind.SERIES) series = item else vm.playItem(item) }
                    }
                }
            }
        }
    }

    favDialog?.let { item -> ListsDialog(vm, item) { favDialog = null } }
}

@Composable
fun ChannelRow(
    vm: MainViewModel,
    name: String,
    logo: String?,
    accountId: Long,
    epgId: String?,
    favorite: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    onStar: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().focusHighlight().clickable(enabled = available, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Logo(logo, Modifier.size(width = 64.dp, height = 40.dp))
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (available) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
            if (!available) {
                Text(stringResource(R.string.fav_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
            } else {
                NowPlaying(vm, accountId, epgId)
            }
        }
        trailing()
        IconButton(onClick = onStar, modifier = Modifier.focusHighlight()) {
            Icon(Icons.Filled.Star, stringResource(if (favorite) R.string.fav_remove else R.string.fav_add), tint = if (favorite) StarOn else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun NowPlaying(vm: MainViewModel, accountId: Long, epgId: String?) {
    if (epgId.isNullOrBlank()) return
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            tick++
        }
    }
    val pair by produceState<Pair<Program?, Program?>>(null to null, accountId, epgId, tick) { value = vm.repo.nowAndNext(accountId, epgId) }
    val now = pair.first ?: return
    val total = (now.end - now.start).coerceAtLeast(1)
    val done = ((System.currentTimeMillis() / 1000 - now.start).toFloat() / total).coerceIn(0f, 1f)
    Text(now.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
    LinearProgressIndicator(progress = { done }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
}

@Composable
private fun PosterCard(item: Item, onClick: () -> Unit) {
    Column(Modifier.focusHighlight().clickable(onClick = onClick).padding(4.dp)) {
        Logo(item.logo, Modifier.fillMaxWidth().aspectRatio(0.68f), ContentScale.Crop)
        Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun FavoritesPane(vm: MainViewModel, selectedList: Long?, onSelect: (Long) -> Unit) {
    val lists by vm.favLists.collectAsState()
    val listId = selectedList?.takeIf { id -> lists.any { it.id == id } } ?: lists.firstOrNull()?.id
    var sort by remember { mutableStateOf(false) }
    var newDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    val current = lists.firstOrNull { it.id == listId }

    Column(Modifier.fillMaxSize()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lists, key = { it.id }) { l ->
                FilterChip(selected = l.id == listId, onClick = { onSelect(l.id) }, label = { Text(l.name) }, modifier = Modifier.focusHighlight())
            }
            item {
                FilterChip(selected = false, onClick = { newDialog = true }, label = { Text(stringResource(R.string.fav_new_list)) }, leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) }, modifier = Modifier.focusHighlight())
            }
        }
        if (current != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { sort = !sort }, modifier = Modifier.focusHighlight()) { Text(stringResource(if (sort) R.string.fav_done else R.string.fav_sort)) }
                TextButton(onClick = { renameDialog = true }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.fav_rename)) }
                if (lists.size > 1) {
                    TextButton(onClick = { vm.run { vm.repo.deleteList(current.id) } }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_delete)) }
                }
            }
            val rows by vm.favoriteRows(current.id).collectAsState(initial = emptyList())
            if (rows.isEmpty()) {
                Text(stringResource(R.string.fav_empty), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(rows, key = { _, r -> "${r.accountId}-${r.kind}-${r.remoteId}" }) { index, row ->
                    ChannelRow(
                        vm, row.name, row.logo, row.accountId, row.epgId, true, row.itemId != null,
                        onClick = {
                            val present = rows.take(index).count { it.itemId != null }
                            vm.player.value = PlayRequest.Zap(Source.Favs(current.id), present)
                        },
                        onStar = { vm.run { vm.repo.setFavorite(current.id, row.accountId, row.kind, row.remoteId, row.name, false) } },
                        trailing = {
                            if (sort) {
                                IconButton(onClick = { vm.run { vm.repo.moveFavorite(current.id, index, index - 1) } }, enabled = index > 0, modifier = Modifier.focusHighlight()) {
                                    Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.fav_move_up))
                                }
                                IconButton(onClick = { vm.run { vm.repo.moveFavorite(current.id, index, index + 1) } }, enabled = index < rows.lastIndex, modifier = Modifier.focusHighlight()) {
                                    Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.fav_move_down))
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (newDialog) NameDialog(null, { newDialog = false }) { name -> vm.run { onSelect(vm.repo.createList(name)) }; newDialog = false }
    if (renameDialog && current != null) NameDialog(current.name, { renameDialog = false }) { name -> vm.run { vm.repo.renameList(current.id, name) }; renameDialog = false }
}

@Composable
private fun NameDialog(initial: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fav_list_name)) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun ListsDialog(vm: MainViewModel, item: Item, onDismiss: () -> Unit) {
    val lists by vm.favLists.collectAsState()
    var member by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(item.id) { member = vm.repo.listsOf(item.accountId, item.kind, item.remoteId).toSet() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fav_choose_lists)) },
        text = {
            Column {
                lists.forEach { l ->
                    Row(Modifier.fillMaxWidth().focusHighlight().clickable {
                        val on = l.id !in member
                        member = if (on) member + l.id else member - l.id
                        vm.run { vm.repo.setFavorite(l.id, item.accountId, item.kind, item.remoteId, item.name, on) }
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = l.id in member, onCheckedChange = null)
                        Text(l.name, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.fav_done)) } },
    )
}

@Composable
private fun SeriesDetail(vm: MainViewModel, series: Item, onBack: () -> Unit) {
    val episodes by produceState<List<XtreamEpisode>?>(null, series.id) {
        value = runCatching { vm.repo.episodes(series) }.getOrDefault(emptyList())
    }
    var season by remember { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_close)) }
            Text(series.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val list = episodes
        if (list == null) {
            Text(stringResource(R.string.series_loading), Modifier.padding(16.dp))
            return
        }
        val seasons = list.map { it.season }.distinct()
        val active = if (season in seasons) season else seasons.firstOrNull() ?: 1
        series.plot?.let { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            items(seasons) { s ->
                FilterChip(selected = s == active, onClick = { season = s }, label = { Text(stringResource(R.string.series_season, s)) }, modifier = Modifier.focusHighlight())
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(list.filter { it.season == active }, key = { it.id }) { ep ->
                Column(
                    Modifier.fillMaxWidth().focusHighlight().clickable {
                        vm.run {
                            val t = vm.repo.episodeTarget(series, ep) ?: return@run
                            vm.player.value = PlayRequest.Single(t)
                        }
                    }.padding(8.dp),
                ) {
                    Text("${ep.number}. ${ep.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ep.plot?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                }
            }
        }
    }
}
