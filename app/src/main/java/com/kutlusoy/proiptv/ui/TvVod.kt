package com.kutlusoy.proiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind

internal data class TvVodEntry(val key: String, val label: String, val count: Int?)

/** Android TV (live TV, movies and series): the lists in a column on the left (favorites, continue watching, all, the categories with their number of titles). */
@Composable
internal fun TvVodSidebar(entries: List<TvVodEntry>, selected: String, contentEntry: androidx.compose.ui.focus.FocusRequester, selectedEntry: androidx.compose.ui.focus.FocusRequester, onSelect: (String) -> Unit) {
    LazyColumn(Modifier.width(300.dp).fillMaxHeight().padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(entries, key = { it.key }) { e ->
            val on = e.key == selected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else androidx.compose.ui.graphics.Color.Transparent)
                    .then(if (on) Modifier.focusRequester(selectedEntry) else Modifier)
                    .focusHighlight()
                    // Right goes to the first entry of the content, not to the search or the menu above it
                    .onPreviewKeyEvent { k -> k.type == KeyEventType.KeyDown && k.key == Key.DirectionRight && contentEntry.requestFocus() }
                    .clickable { onSelect(e.key) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(e.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                e.count?.let { Text("  ($it)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)) }
            }
        }
    }
}

/** The name of the list, the sort buttons (bit 8 A to Z, bit 16 Z to A) and the search. */
@Composable
internal fun TvVodTopBar(showSort: Boolean, title: String, sort: Int, onSort: (Int) -> Unit, searching: Boolean, onSearch: () -> Unit, menu: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (showSort) FilterChip(selected = sort == 8, onClick = { onSort(8) }, label = { Text(stringResource(R.string.sort_az)) }, modifier = Modifier.focusHighlight())
        if (showSort) FilterChip(selected = sort == 16, onClick = { onSort(16) }, label = { Text(stringResource(R.string.sort_za)) }, modifier = Modifier.focusHighlight())
        IconButton(onClick = onSearch, modifier = Modifier.focusHighlight()) {
            Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, stringResource(if (searching) R.string.action_close else R.string.live_search))
        }
        menu()
    }
}

/** The titles that were started and not finished, for the list "Continue watching" (movies; series by their last started episode). */
@Composable
internal fun ContinuePane(vm: MainViewModel, kind: Int, favKeys: Set<Triple<Long, Int, String>>, onToggleFavorite: (Item) -> Unit, accountIds: List<Long>? = null, onOpen: (Item) -> Unit) {
    val allRows by vm.repo.db.progress().continueWatching().collectAsState(initial = emptyList())
    // only the accounts that are chosen in the filter
    val rows = if (accountIds == null) allRows else allRows.filter { it.accountId in accountIds }
    val items by produceState(emptyList<Item>(), rows, kind) {
        val seen = HashSet<Long>()
        val out = ArrayList<Item>()
        for (p in rows) {
            val item = if (kind == Kind.MOVIE && p.kind == Kind.MOVIE) vm.repo.db.items().find(p.accountId, Kind.MOVIE, p.remoteId)
            else if (kind == Kind.SERIES && p.kind == Kind.EPISODE && p.seriesRemoteId.isNotEmpty()) vm.repo.db.items().find(p.accountId, Kind.SERIES, p.seriesRemoteId)
            else null
            if (item != null && seen.add(item.id)) out += item
        }
        value = out
    }
    var menuFor by remember { mutableStateOf<Item?>(null) }
    menuFor?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    androidx.compose.material3.TextButton(
                        onClick = { vm.run { vm.repo.dismissContinue(item) }; menuFor = null },
                        modifier = Modifier.fillMaxWidth().focusHighlight(),
                    ) { Text(stringResource(R.string.continue_remove), Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { menuFor = null }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (items.isEmpty()) {
        Text(stringResource(R.string.live_no_results), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(16.dp))
    }
    LazyVerticalGrid(
        GridCells.Fixed(4), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.id }) { item ->
            PosterCard(item, favorite = Triple(item.accountId, item.kind, item.remoteId) in favKeys, onLongClick = { menuFor = item }) { onOpen(item) }
        }
    }
}
