package com.kutlusoy.proiptv.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Source

/** The last watched live channels as logo tiles, the newest one first. */
@Composable
fun HistoryPane(vm: MainViewModel, accountIds: List<Long>? = null) {
    val all by vm.repo.db.history().rows().collectAsState(initial = emptyList())
    // only the accounts that are chosen in the filter
    val rows = if (accountIds == null) all else all.filter { it.item.accountId in accountIds }
    if (rows.isEmpty()) {
        Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(16.dp))
        return
    }
    val columns = if (LocalTv.current) 4 else 3
    LazyVerticalGrid(
        GridCells.Fixed(columns), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(rows, key = { _, r -> "${r.item.accountId}|${r.item.remoteId}" }) { i, row ->
            Column(
                Modifier.clip(RoundedCornerShape(10.dp)).focusHighlight().clickable { vm.player.value = PlayRequest.Zap(Source.History(accountIds), i) }.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)).padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Logo(row.item.logo, Modifier.fillMaxSize(), name = row.item.name)
                }
                Text(row.item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (row.item.country.isNotEmpty()) {
                        Text(
                            row.item.country, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFD32F2F)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    Text(
                        row.listName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

/** Shown on a TV while the cursor rests on live TV, movies or series in the main menu: the titles that were watched last. */
@Composable
fun TvPreview(vm: MainViewModel, kind: Int, onOpen: (com.kutlusoy.proiptv.data.Item) -> Unit) {
    val favKeys by vm.favKeys.collectAsState()
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(if (kind == com.kutlusoy.proiptv.data.Kind.LIVE) R.string.history_title else R.string.home_continue),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp),
        )
        if (kind == com.kutlusoy.proiptv.data.Kind.LIVE) HistoryPane(vm)
        else ContinuePane(vm, kind, favKeys, onToggleFavorite = {}, onOpen = onOpen)
    }
}
