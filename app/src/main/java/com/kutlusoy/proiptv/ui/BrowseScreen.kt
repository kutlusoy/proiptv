package com.kutlusoy.proiptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
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
private const val FAV_KEY = "__fav"
private const val ALL_KEY = "__all"

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
    var movie by remember { mutableStateOf<Item?>(null) }
    var favDialog by remember { mutableStateOf<Item?>(null) }
    var searching by rememberSaveable(kind) { mutableStateOf(query.isNotEmpty()) }
    val chrome = LocalChromeHidden.current
    SideEffect { chrome.value = series != null || movie != null }
    DisposableEffect(Unit) { onDispose { chrome.value = false } }

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
        SeriesDetailScreen(vm, it) { series = null }
        return
    }
    movie?.let {
        BackHandler { movie = null }
        MovieDetail(vm, it) { movie = null }
        return
    }

    val categories by (effective?.let { vm.categories(it, kind) } ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (searching) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.live_search)) },
                    trailingIcon = {
                        IconButton(onClick = { query = ""; searching = false }) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) }
                    },
                )
            } else {
                val allLabel = stringResource(R.string.live_all)
                val favLabel = stringResource(R.string.fav_title)
                val otherLabel = stringResource(R.string.live_other)
                val options = buildList<Pair<String, String>> {
                    if (kind == Kind.LIVE) add(FAV_KEY to favLabel)
                    add(ALL_KEY to allLabel)
                    if (effective != null) categories.forEach { add(it.remoteId to it.name.ifBlank { otherLabel }) }
                }
                val current = when {
                    favMode -> FAV_KEY
                    category == null -> ALL_KEY
                    else -> category!!
                }
                DropdownField(
                    selected = options.firstOrNull { it.first == current }?.second ?: allLabel,
                    options = options,
                    onSelect = { v ->
                        when (v) {
                            FAV_KEY -> favMode = true
                            ALL_KEY -> { favMode = false; category = null }
                            else -> { favMode = false; category = v }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                if (enabled.size > 1) {
                    DropdownField<Long?>(
                        selected = enabled.firstOrNull { it.id == selected }?.name ?: stringResource(R.string.live_all_accounts),
                        options = listOf<Pair<Long?, String>>(null to stringResource(R.string.live_all_accounts)) + enabled.map { it.id to it.name },
                        onSelect = { accountSel = it },
                        modifier = Modifier.weight(0.7f),
                    )
                }
                IconButton(onClick = { searching = true }, modifier = Modifier.focusHighlight()) { Icon(Icons.Filled.Search, stringResource(R.string.live_search)) }
            }
        }

        val useRows = kind != Kind.LIVE && !favMode && category == null && query.isBlank() && effective != null && categories.isNotEmpty()
        if (kind == Kind.LIVE && favMode) {
            FavoritesPane(vm, favList) { favList = it }
        } else if (useRows) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(categories, key = { it.id }) { c ->
                    CategoryRow(vm, effective!!, kind, c, onOpen = { if (it.kind == Kind.SERIES) series = it else movie = it }, onAll = { category = c.remoteId })
                }
            }
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
                        PosterCard(item) { if (item.kind == Kind.SERIES) series = item else movie = item }
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
private fun PosterCard(item: Item, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "poster")
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.hasFocus }
            .then(if (focused) Modifier.border(2.dp, Palette.Wine, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick).padding(2.dp),
    ) {
        Logo(item.logo, Modifier.fillMaxWidth().aspectRatio(0.68f).clip(RoundedCornerShape(8.dp)), ContentScale.Crop)
        Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
    }
}

@Composable
private fun CategoryRow(vm: MainViewModel, accountId: Long, kind: Int, category: com.kutlusoy.proiptv.data.Category, onOpen: (Item) -> Unit, onAll: () -> Unit) {
    val items by produceState<List<Item>>(emptyList(), accountId, kind, category.remoteId) {
        value = vm.repo.db.items().first(kind, accountId, category.remoteId, 20)
    }
    if (items.isEmpty()) return
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onAll).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(18.dp).background(Palette.Wine, RoundedCornerShape(2.dp)))
            Text(category.name.ifBlank { stringResource(R.string.live_other) }, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.detail_see_all), color = Palette.WineSoft, style = MaterialTheme.typography.labelLarge)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
            items(items, key = { it.id }) { item -> PosterCard(item, Modifier.width(118.dp)) { onOpen(item) } }
        }
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
        var menu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField(
                selected = current?.name.orEmpty(),
                options = lists.map { it.id to it.name },
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
            if (sort) {
                TextButton(onClick = { sort = false }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.fav_done)) }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.focusHighlight()) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.fav_new_list)) }, onClick = { menu = false; newDialog = true })
                    if (current != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.fav_sort)) }, onClick = { menu = false; sort = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.fav_rename)) }, onClick = { menu = false; renameDialog = true })
                        if (lists.size > 1) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; vm.run { vm.repo.deleteList(current.id) } })
                        }
                    }
                }
            }
        }
        if (current != null) {
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
