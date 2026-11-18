package com.kutlusoy.proiptv.desktop

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.ui.ClockGlyph
import java.text.DateFormat
import java.util.Date

private class TimerRow(val key: String, val reminder: ReminderDto?, val recording: RecordingDto?, val title: String, val whenMs: Long)

/** The timers that are set: reminders (clock) and planned recordings (red dot), the next one first. */
@Composable
fun TimersDialog(state: AppState, onClose: () -> Unit) {
    state.recordingStore.version
    val now = System.currentTimeMillis()
    val rows = (
        state.reminders.filter { it.startMs > now }.map { TimerRow("r${it.account}|${it.channelId}|${it.startMs}", it, null, it.title + "  \u00B7  " + it.channelName, it.startMs) } +
            state.recordingStore.all().filter { it.status == RecStatus.SCHEDULED && (it.plannedStart ?: it.startedAt) > now }.map { TimerRow("c${it.id}", null, it, it.title, it.plannedStart ?: it.startedAt) }
        ).sortedBy { it.whenMs }
    val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(t("timers_title")) },
        text = {
            if (rows.isEmpty()) {
                Text(t("timers_empty"))
            } else {
                LazyColumn {
                    items(rows, key = { it.key }) { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (row.reminder != null) ClockGlyph(size = 20.dp) else Box(Modifier.size(14.dp).background(Color(0xFFE53935), CircleShape))
                            Column(Modifier.weight(1f)) {
                                Text(row.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(format.format(Date(row.whenMs)), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                            }
                            Tip(t("action_delete")) { IconButton(onClick = { row.reminder?.let { state.removeReminder(it) }; row.recording?.let { state.recordingManager.delete(it.id) } }) {
                                Icon(Icons.Filled.Close, t("action_delete"))
                            } }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(t("action_close")) } },
    )
}
