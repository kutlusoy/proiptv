package com.kutlusoy.proiptv.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.DlStatus
import com.kutlusoy.proiptv.data.Download
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.download.DownloadManager

@Composable
fun DownloadsScreen(vm: MainViewModel) {
    val all by vm.downloads.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<List<Download>?>(null) }
    var open by rememberSaveable { mutableStateOf(listOf<String>()) }
    fun toggle(key: String) { open = if (key in open) open - key else open + key }

    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.nav_downloads), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
        TabRow(selectedTabIndex = tab, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.nav_movies)) }, modifier = Modifier.focusHighlight())
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.nav_series)) }, modifier = Modifier.focusHighlight())
        }
        val movies = all.filter { it.kind == Kind.MOVIE }
        val episodes = all.filter { it.kind == Kind.EPISODE }
        val empty = if (tab == 0) movies.isEmpty() else episodes.isEmpty()
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (empty) item { Text(stringResource(R.string.dl_empty), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)) }
            if (tab == 0) {
                items(movies, key = { it.id }) { d -> DownloadRow(vm, d, d.title, onDelete = { deleting = listOf(d) }) }
            } else {
                val groups = episodes.groupBy { it.accountId to it.seriesRemoteId }.values.sortedBy { it.first().seriesName.lowercase() }
                groups.forEach { eps ->
                    val head = eps.first()
                    val key = "s${head.accountId}-${head.seriesRemoteId}"
                    item(key = key) {
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().focusHighlight().clickable { toggle(key) }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(head.seriesName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        stringResource(R.string.dl_episodes, eps.size, eps.map { it.season }.distinct().size),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    )
                                }
                                OutlinedButton(onClick = { deleting = eps }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_delete)) }
                                Icon(if (key in open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    if (key in open) {
                        eps.groupBy { it.season }.toSortedMap().forEach { (season, list) ->
                            val skey = "$key-$season"
                            item(key = skey) {
                                Row(Modifier.fillMaxWidth().focusHighlight().clickable { toggle(skey) }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.series_season, season), style = MaterialTheme.typography.titleSmall, color = Palette.WineSoft, modifier = Modifier.weight(1f))
                                    Icon(if (skey in open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                                }
                            }
                            if (skey in open) {
                                items(list.sortedBy { it.episode }, key = { it.id }) { d ->
                                    DownloadRow(vm, d, "${d.episode}. ${d.title}", onDelete = { deleting = listOf(d) }, modifier = Modifier.padding(start = 12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { list ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.dl_delete_confirm)) },
            confirmButton = { TextButton(onClick = { list.forEach { vm.deleteDownload(it.id) }; deleting = null }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun DownloadRow(vm: MainViewModel, d: Download, title: String, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            when (d.status) {
                DlStatus.DONE -> Text(Formatter.formatShortFileSize(ctx, d.bytes), style = MaterialTheme.typography.bodySmall, color = dim)
                DlStatus.RUNNING -> {
                    val percent = if (d.total > 0) (d.bytes * 100 / d.total).toInt() else 0
                    Text("${stringResource(R.string.dl_running)} $percent %  ${Formatter.formatShortFileSize(ctx, d.bytes)}", style = MaterialTheme.typography.bodySmall, color = dim)
                    LinearProgressIndicator(progress = { if (d.total > 0) (d.bytes.toFloat() / d.total).coerceIn(0f, 1f) else 0f }, Modifier.fillMaxWidth())
                }
                DlStatus.QUEUED -> Text(stringResource(R.string.dl_queued), style = MaterialTheme.typography.bodySmall, color = dim)
                DlStatus.PAUSED -> {
                    val percent = if (d.total > 0) (d.bytes * 100 / d.total).toInt() else 0
                    Text("${stringResource(R.string.dl_paused)} $percent %  ${Formatter.formatShortFileSize(ctx, d.bytes)}", style = MaterialTheme.typography.bodySmall, color = dim)
                    LinearProgressIndicator(progress = { if (d.total > 0) (d.bytes.toFloat() / d.total).coerceIn(0f, 1f) else 0f }, Modifier.fillMaxWidth())
                }
                else -> Text(
                    stringResource(R.string.dl_failed) + ": " + stringResource(
                        when (d.error) {
                            DownloadManager.ERR_SPACE -> R.string.dl_err_space
                            DownloadManager.ERR_SOURCE -> R.string.dl_err_source
                            else -> R.string.dl_err_network
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (d.status == DlStatus.DONE) {
                    OutlinedButton(onClick = { vm.playDownload(d) }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.detail_play)) }
                }
                if (d.status == DlStatus.FAILED) {
                    OutlinedButton(onClick = { vm.retryDownload(d.id) }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.dl_retry)) }
                }
                if (d.status == DlStatus.RUNNING || d.status == DlStatus.QUEUED) {
                    OutlinedButton(onClick = { vm.pauseDownload(d.id) }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.dl_pause)) }
                }
                if (d.status == DlStatus.PAUSED) {
                    OutlinedButton(onClick = { vm.retryDownload(d.id) }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.dl_resume)) }
                }
                if (d.status == DlStatus.RUNNING || d.status == DlStatus.QUEUED || d.status == DlStatus.PAUSED) {
                    OutlinedButton(onClick = { vm.deleteDownload(d.id) }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.dl_stop)) }
                } else {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}
