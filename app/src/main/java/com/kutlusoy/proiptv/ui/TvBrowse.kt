package com.kutlusoy.proiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Program
import com.kutlusoy.proiptv.data.Source
import com.kutlusoy.proiptv.parse.TitleParser
import com.kutlusoy.proiptv.parse.decodeEntities
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.first

/** The lists a TV screen can switch between: the favorites lists, all channels and the categories (with the account name when several accounts are on). */
internal suspend fun browseListsFor(vm: MainViewModel, enabled: List<com.kutlusoy.proiptv.data.Account>, favLists: List<com.kutlusoy.proiptv.data.FavoriteList>, allLabel: String): List<BrowseList> {
    val out = ArrayList<BrowseList>()
    favLists.filter { it.kind == com.kutlusoy.proiptv.data.Kind.LIVE }.forEach { out += BrowseList(it.name, Source.Favs(it.id)) }
    out += BrowseList(allLabel, Source.Browse(com.kutlusoy.proiptv.data.Kind.LIVE, enabled.map { it.id }, null, ""))
    for (a in enabled) {
        for (c in vm.repo.db.items().categories(a.id, com.kutlusoy.proiptv.data.Kind.LIVE).first()) {
            out += BrowseList((if (enabled.size > 1) a.name + " \u00B7 " else "") + c.name.ifBlank { allLabel }, Source.Browse(com.kutlusoy.proiptv.data.Kind.LIVE, listOf(a.id), c.remoteId, ""))
        }
    }
    return out
}

/** A list that the channel browser of the TV player can show: a favorites list, all channels or one category. */
internal data class BrowseList(val name: String, val source: Source)

/** The share of the width that the list column has: with the lists column next to it (three columns) it is wider. */
internal const val TV_LEFT_TWO = 0.32f
internal const val TV_LEFT_THREE = 0.52f
private const val VIDEO_OF_RIGHT = 0.62f
private const val TOP = 0.5f

/** Places the video in the window of the browser: top, right of the list(s), left of the info card. */
internal fun Modifier.tvPreviewRect(left: Float = TV_LEFT_TWO): Modifier = layout { measurable, constraints ->
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed((w * (1f - left) * VIDEO_OF_RIGHT).toInt(), (h * TOP).toInt()))
    layout(w, h) { placeable.place((w * left).toInt(), 0) }
}

/**
 * The browser of the TV player: the list on the left with its name on top (Left and Right switch the list), the running channel in a small
 * window with an info card, and the guide of the highlighted channel below. The video is placed behind the empty window by [tvPreviewRect].
 */
@Composable
internal fun TvBrowseLayer(
    vm: MainViewModel,
    lists: List<BrowseList>,
    listPos: Int,
    count: Int,
    highlighted: Int,
    highlightedItem: Item?,
    playing: Item?,
    playingNow: Program?,
    left: Float = TV_LEFT_TWO,
    wide: Boolean = false,
    favorites: Set<Triple<Long, Int, String>> = emptySet(),
) {
    val source = lists.getOrNull(listPos)?.source
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    Row(Modifier.fillMaxSize()) {
        // the TV cuts a little off the edges of the picture, so the list keeps a safe margin to the left, top and bottom
        Row(Modifier.weight(left).fillMaxHeight().background(Palette.Velvet).padding(start = 40.dp, top = 20.dp, bottom = 16.dp)) {
        if (wide) {
            // the lists (favorites, categories) in a column of their own: Up and Down choose, OK or Right goes back to the channels
            val listsState = rememberLazyListState()
            LaunchedEffect(listPos) { runCatching { listsState.animateScrollToItem((listPos - 3).coerceAtLeast(0)) } }
            LazyColumn(Modifier.weight(0.46f).fillMaxHeight().background(Palette.Surface), state = listsState) {
                items(lists.size, key = { it }) { i ->
                    Row(
                        Modifier.fillMaxWidth().then(if (i == listPos) Modifier.background(Palette.Wine, RoundedCornerShape(6.dp)) else Modifier).padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(lists[i].name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (i == listPos) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Column(Modifier.weight(if (wide) 0.54f else 1f).fillMaxHeight().padding(start = if (wide) 8.dp else 0.dp)) {
            Row(Modifier.fillMaxWidth().height(64.dp).background(Palette.WineDeep), verticalAlignment = Alignment.CenterVertically) {
                if (!wide) Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, Modifier.padding(start = 8.dp).size(36.dp), tint = Color.White)
                Text(lists.getOrNull(listPos)?.name.orEmpty(), Modifier.weight(1f).padding(horizontal = if (wide) 12.dp else 0.dp), color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = if (wide) TextAlign.Start else TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val state = rememberLazyListState()
            LaunchedEffect(highlighted, source) { runCatching { state.animateScrollToItem((highlighted - 3).coerceAtLeast(0)) } }
            if (source != null) {
                LazyColumn(Modifier.fillMaxSize(), state = state) {
                    items(count, key = { it }) { i ->
                        val channel by produceState<Item?>(null, i, source) { value = vm.repo.at(source, i) }
                        Row(
                            Modifier.fillMaxWidth().then(if (i == highlighted) Modifier.background(Palette.Wine, RoundedCornerShape(6.dp)) else Modifier).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Logo(channel?.logo, Modifier.size(width = 56.dp, height = 36.dp), name = channel?.name)
                            Text(channel?.let { TitleParser.parse(it.name).title.ifBlank { it.name } } ?: "", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            // the star of a favorite (Hold OK on a channel adds it or takes it away)
                            if (channel?.let { Triple(it.accountId, it.kind, it.remoteId) in favorites } == true) {
                                Icon(Icons.Filled.Star, null, Modifier.size(20.dp), tint = Color(0xFFFFC107))
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.tv_browse_hint), Modifier.padding(top = 6.dp, end = 8.dp), color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelMedium, maxLines = 2)
        }
        }
        Column(Modifier.weight(1f - left).fillMaxHeight()) {
            Row(Modifier.weight(TOP).fillMaxWidth()) {
                // the window of the video
                Spacer(Modifier.weight(VIDEO_OF_RIGHT).fillMaxHeight())
                Column(Modifier.weight(1f - VIDEO_OF_RIGHT).fillMaxHeight().background(Palette.Surface).padding(start = 16.dp, end = 32.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.Center) {
                    Text(
                        stringResource(R.string.tv_live_badge), Modifier.background(Palette.Wine, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White, style = MaterialTheme.typography.labelLarge,
                    )
                    Text(playing?.name.orEmpty(), Modifier.padding(top = 8.dp), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    playingNow?.let {
                        Text(it.title.decodeEntities(), Modifier.padding(top = 8.dp), color = Palette.WineSoft, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(time.format(Date(it.start * 1000)) + " - " + time.format(Date(it.end * 1000)), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            val programs by produceState<List<Program>?>(null, highlightedItem?.id) {
                value = highlightedItem?.epgId?.let { vm.repo.guide(highlightedItem.accountId, it, 0, 2) } ?: emptyList()
            }
            Column(Modifier.weight(1f - TOP).fillMaxWidth().background(Palette.Surface).padding(end = 32.dp, bottom = 16.dp)) {
                val list = programs.orEmpty().filter { it.end > System.currentTimeMillis() / 1000 }
                if (list.isEmpty()) {
                    Text(stringResource(R.string.epg_none), Modifier.padding(16.dp), color = Color.White.copy(alpha = 0.7f))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.id }) { p ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                Row {
                                    Text(time.format(Date(p.start * 1000)) + " - " + time.format(Date(p.end * 1000)), color = Palette.WineSoft, style = MaterialTheme.typography.bodyMedium)
                                    Text("  " + p.title.decodeEntities(), color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                p.description?.takeIf { it.isNotBlank() }?.let {
                                    Text(it.decodeEntities(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
