package com.kutlusoy.proiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.ProIptvApp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.RecStatus
import java.text.DateFormat
import java.util.Date

private class Timer(val key: String, val reminder: Boolean, val id: Long, val title: String, val whenMs: Long)

/** The timers that are set: reminders (clock) and planned recordings (red dot), the next one first. Used up timers are removed. */
@Composable
fun TimersDialog(vm: MainViewModel, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as ProIptvApp
    LaunchedEffect(Unit) { app.reminders.removeExpired() }
    val reminders by vm.repo.db.reminders().allFlow().collectAsState(initial = emptyList())
    val recordings by vm.recordings.collectAsState()
    val now = System.currentTimeMillis()
    val timers = (
        reminders.filter { it.startMs > now }.map { Timer("r${it.id}", true, it.id, it.title + "  \u00B7  " + it.channelName, it.startMs) } +
            recordings.filter { it.status == RecStatus.SCHEDULED && (it.plannedStart ?: it.startedAt) > now }.map { Timer("c${it.id}", false, it.id, it.title, it.plannedStart ?: it.startedAt) }
        ).sortedBy { it.whenMs }
    val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.timers_title)) },
        text = {
            if (timers.isEmpty()) {
                Text(stringResource(R.string.timers_empty))
            } else {
                LazyColumn {
                    items(timers, key = { it.key }) { t ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (t.reminder) ClockGlyph(size = 20.dp) else Box(Modifier.size(14.dp).background(Color(0xFFE53935), CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(format.format(Date(t.whenMs)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                            IconButton(onClick = { if (t.reminder) vm.run { app.reminders.remove(t.id) } else vm.deleteRecording(t.id) }, modifier = Modifier.focusHighlight()) {
                                Icon(Icons.Filled.Close, stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_close)) } },
    )
}
