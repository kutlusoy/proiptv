package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

fun sizeText(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1024.0 * 1024))
    else -> "%.0f KB".format(bytes / 1024.0)
}

@Composable
fun DownloadsScreen(state: AppState) {
    state.downloads.version
    val all = state.downloads.all()
    var deleting by remember { mutableStateOf<DownloadDto?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(DownloadManager.mediaDir("Downloads").absolutePath, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f))
            androidx.compose.material3.OutlinedButton(onClick = {
                val dir = DownloadManager.mediaDir("Downloads").also { it.mkdirs() }
                runCatching { java.awt.Desktop.getDesktop().open(dir) }
            }) {
                Icon(Icons.Filled.FolderOpen, null)
                Text(t("dl_open_folder"), Modifier.padding(start = 6.dp))
            }
        }
        DownloadList(state, all) { deleting = it }
    }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(t("dl_delete_confirm")) },
            confirmButton = { TextButton(onClick = { state.downloadManager.delete(d.id); deleting = null }) { Text(t("action_delete")) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(t("action_cancel")) } },
        )
    }
}

@Composable
private fun DownloadList(state: AppState, all: List<DownloadDto>, onAsk: (DownloadDto) -> Unit) {
    if (all.isEmpty()) {
        Text(t("dl_empty"), Modifier.padding(16.dp), color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f))
        return
    }
    val movies = all.filter { it.kind == Kind.MOVIE }
    val episodes = all.filter { it.kind == Kind.EPISODE }.groupBy { it.seriesName }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (movies.isNotEmpty()) {
            item { Text(t("nav_movies"), Modifier.padding(8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(movies, key = { it.id }) { DownloadRow(state, it, it.title) { onAsk(it) } }
        }
        episodes.forEach { (series, list) ->
            item { Text(series, Modifier.padding(8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(list.sortedWith(compareBy({ it.season }, { it.episode })), key = { it.id }) {
                DownloadRow(state, it, "S${it.season} E${it.episode}") { onAsk(it) }
            }
        }
    }
}

@Composable
private fun DownloadRow(state: AppState, d: DownloadDto, label: String, onDelete: () -> Unit) {
    val done = d.status == DlStatus.DONE
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = done) { state.localPlayable(d)?.let { state.player = PlayRequest.Single(it, state.progress.get(d.account, d.kind, d.remoteId)?.positionMs ?: 0) } }.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(d.logo, Modifier.size(width = 80.dp, height = 56.dp), ContentScale.Crop)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when (d.status) {
                DlStatus.DONE -> Text("${t("dl_downloaded")} · ${sizeText(d.bytes)}", style = MaterialTheme.typography.bodySmall)
                DlStatus.FAILED -> Text("${t("dl_failed")}: " + when (d.error) {
                    DownloadManager.ERR_SPACE -> t("dl_err_space")
                    DownloadManager.ERR_SOURCE -> t("dl_err_source")
                    else -> d.error.orEmpty()
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else -> {
                    Text(t(when (d.status) { DlStatus.RUNNING -> "dl_running"; DlStatus.PAUSED -> "dl_paused"; else -> "dl_queued" }) + if (d.total > 0) " · ${sizeText(d.bytes)} / ${sizeText(d.total)}" else "", style = MaterialTheme.typography.bodySmall)
                    if ((d.status == DlStatus.RUNNING || d.status == DlStatus.PAUSED) && d.total > 0) LinearProgressIndicator(progress = { (d.bytes.toFloat() / d.total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                }
            }
        }
        if (done) Icon(Icons.Filled.PlayArrow, null)
        if (d.status == DlStatus.FAILED) IconButton(onClick = { state.downloadManager.retry(d.id) }) { Icon(Icons.Filled.Refresh, t("dl_retry")) }
        if (d.status == DlStatus.RUNNING || d.status == DlStatus.QUEUED) IconButton(onClick = { state.downloadManager.pause(d.id) }) { Icon(Icons.Filled.Pause, t("dl_pause")) }
        if (d.status == DlStatus.PAUSED) IconButton(onClick = { state.downloadManager.resumePaused(d.id) }) { Icon(Icons.Filled.PlayArrow, t("dl_resume")) }
        if (d.status == DlStatus.RUNNING || d.status == DlStatus.QUEUED || d.status == DlStatus.PAUSED) {
            IconButton(onClick = { state.downloadManager.delete(d.id) }) { Icon(Icons.Filled.Stop, t("dl_stop")) }
        } else {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, t("action_delete")) }
        }
    }
}
