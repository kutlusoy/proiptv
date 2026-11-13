package com.kutlusoy.proiptv.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.PlayTarget
import com.kutlusoy.proiptv.data.RecStatus
import com.kutlusoy.proiptv.data.Recording
import com.kutlusoy.proiptv.recording.RecNote
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun RecordingsScreen(vm: MainViewModel) {
    val list by vm.recordings.collectAsState()
    val active by vm.activeRecordings.collectAsState()
    var deleting by remember { mutableStateOf<Recording?>(null) }
    val ctx = LocalContext.current
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.nav_recordings), style = MaterialTheme.typography.headlineMedium) }
        if (list.isEmpty()) {
            item { Text(stringResource(R.string.rec_empty), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)) }
        }
        items(list, key = { it.id }) { r ->
            val running = active.any { it.id == r.id }
            val size = if (running) File(r.path).length() else r.bytes
            val minutes = (((r.endedAt ?: System.currentTimeMillis()) - r.startedAt) / 60_000).coerceAtLeast(0)
            val status = when {
                running -> R.string.rec_status_recording
                r.status == RecStatus.SCHEDULED -> R.string.rec_status_scheduled
                r.status == RecStatus.MISSED -> R.string.rec_status_missed
                r.status == RecStatus.DONE -> R.string.rec_status_done
                else -> R.string.rec_status_interrupted
            }
            val note = when (r.note) {
                RecNote.BUDGET -> R.string.rec_note_budget
                RecNote.SPACE -> R.string.rec_note_space
                RecNote.CHANNEL -> R.string.rec_note_channel
                RecNote.ENDED -> R.string.rec_note_ended
                else -> null
            }
            val scheduled = r.status == RecStatus.SCHEDULED
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium)
                    val details = if (scheduled) {
                        "${format.format(Date(r.plannedStart ?: r.startedAt))} - ${format.format(Date(r.plannedEnd ?: r.startedAt))}, ${stringResource(status)}"
                    } else {
                        "${format.format(Date(r.startedAt))}, ${minutes / 60}:${"%02d".format(minutes % 60)}, ${Formatter.formatShortFileSize(ctx, size)}, ${stringResource(status)}"
                    }
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        if (size > 0 && !scheduled) {
                            OutlinedButton(
                                onClick = {
                                    vm.player.value = PlayRequest.Single(
                                        PlayTarget(Uri.fromFile(File(r.path)).toString(), null, r.title, false, r.accountId, Kind.EPISODE, "rec-${r.id}"),
                                    )
                                },
                                modifier = Modifier.focusHighlight(),
                            ) { Text(stringResource(R.string.rec_play)) }
                        }
                        if (running) {
                            OutlinedButton(onClick = { vm.stopRecording(r.id) }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.rec_stop)) }
                        }
                        OutlinedButton(onClick = { if (scheduled) vm.deleteRecording(r.id) else deleting = r }, modifier = Modifier.focusHighlight()) {
                            Text(stringResource(if (scheduled) R.string.action_cancel else R.string.action_delete))
                        }
                    }
                }
            }
        }
    }

    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.rec_delete_confirm)) },
            confirmButton = { TextButton(onClick = { vm.deleteRecording(r.id); deleting = null }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
