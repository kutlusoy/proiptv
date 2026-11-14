package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.engine.EngineManager
import com.kutlusoy.proiptv.engine.HubLease
import com.kutlusoy.proiptv.engine.Recorder
import com.kutlusoy.proiptv.engine.RecordingEnd
import com.kutlusoy.proiptv.net.Upstream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object RecStatus {
    const val RECORDING = 0
    const val DONE = 1
    const val INTERRUPTED = 2
    const val SCHEDULED = 3
    const val MISSED = 4
}

@Serializable
data class RecordingDto(
    val id: Long,
    val account: String,
    val title: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val plannedEnd: Long? = null,
    val path: String,
    val bytes: Long = 0,
    val status: Int = RecStatus.RECORDING,
    val channelRemoteId: String = "",
    val note: String? = null,
    val plannedStart: Long? = null,
)

class RecordingStore(private val dir: File) {
    private val file = File(dir, "recordings.json")
    private val json = Json { ignoreUnknownKeys = true }
    private var list: List<RecordingDto> = runCatching { json.decodeFromString<List<RecordingDto>>(file.readText()) }.getOrDefault(emptyList())

    var version by mutableIntStateOf(0)
        private set

    init {
        // recordings that were running when the program ended were cut off
        list = list.map { if (it.status == RecStatus.RECORDING) it.copy(status = RecStatus.INTERRUPTED, bytes = File(it.path).length()) else it }
    }

    @Synchronized fun all(): List<RecordingDto> = list

    @Synchronized fun get(id: Long) = list.firstOrNull { it.id == id }

    @Synchronized
    fun add(r: RecordingDto): Long {
        val id = (list.maxOfOrNull { it.id } ?: 0) + 1
        list = list + r.copy(id = id)
        write()
        return id
    }

    @Synchronized
    fun update(id: Long, change: (RecordingDto) -> RecordingDto) {
        list = list.map { if (it.id == id) change(it) else it }
        write()
    }

    @Synchronized
    fun remove(id: Long) {
        list = list.filterNot { it.id == id }
        write()
    }

    private fun write() {
        dir.mkdirs()
        runCatching { file.writeText(json.encodeToString(list)) }
        version++
    }
}

class NotEnoughSpace : Exception("not enough free storage")

/** Records live channels from the shared stream buffer (one connection for watching and recording). The result is a transport stream file. */
class RecordingManager(
    private val store: RecordingStore,
    private val engine: EngineManager,
    private val scope: CoroutineScope,
    private val dir: File,
) {
    private class Running(val recorder: Recorder, val lease: HubLease)

    private val running = ConcurrentHashMap<Long, Running>()

    fun isRecording(account: String, channelRemoteId: String): Long? =
        store.all().firstOrNull { it.status == RecStatus.RECORDING && it.account == account && it.channelRemoteId == channelRemoteId }?.id

    /** Starts recording the live item. Throws BudgetExceeded when the account has no free connection and NotEnoughSpace when the disk is nearly full. */
    fun start(item: Playable, stopAtMs: Long?): Long {
        dir.mkdirs()
        if (dir.usableSpace < MIN_FREE_BYTES) throw NotEnoughSpace()
        val lease = engine.acquire(item.accountNumber, item.channelId, item.title, item.maxConnections, Upstream.opener(item.url, item.userAgent))
        try {
            val now = System.currentTimeMillis()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm").format(Date(now))
            val safe = item.title.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(40)
            val file = File(dir, "$safe-$stamp.ts")
            val id = store.add(RecordingDto(0, item.account, item.title, now, null, stopAtMs, file.absolutePath, 0, RecStatus.RECORDING, item.channelId))
            val recorder = Recorder(lease.hub, file, lease.hub.buffer.end, stopAtMs)
            running[id] = Running(recorder, lease)
            scope.launch(Dispatchers.IO) { finish(id, recorder.run()) }
            return id
        } catch (e: Exception) {
            lease.close()
            throw e
        }
    }

    private fun finish(id: Long, reason: RecordingEnd) {
        val r = running.remove(id)
        r?.lease?.close()
        val status = if (reason == RecordingEnd.STOPPED || reason == RecordingEnd.TIME_REACHED) RecStatus.DONE else RecStatus.INTERRUPTED
        store.update(id) { it.copy(endedAt = System.currentTimeMillis(), bytes = r?.recorder?.bytes ?: File(it.path).length(), status = status) }
    }

    fun stop(id: Long) {
        running[id]?.recorder?.requestStop()
    }

    fun stopAll() = running.keys.toList().forEach { stop(it) }

    fun delete(id: Long) {
        stop(id)
        scope.launch(Dispatchers.IO) {
            store.get(id)?.let { if (it.path.isNotEmpty()) runCatching { File(it.path).delete() } }
            store.remove(id)
        }
    }

    /** Plans a recording of a later show. It starts when the program is running at that time (see startDue). */
    fun schedule(item: Playable, startMs: Long, endMs: Long): Long =
        store.add(RecordingDto(0, item.account, item.title, startMs, null, endMs, "", 0, RecStatus.SCHEDULED, item.channelId, plannedStart = startMs))

    /** Starts the planned recordings whose time has come. [resolve] gives the live item of a channel. */
    fun startDue(now: Long, resolve: (RecordingDto) -> Playable?) {
        for (r in store.all().filter { it.status == RecStatus.SCHEDULED }) {
            val start = r.plannedStart ?: continue
            if (start > now) continue
            val end = r.plannedEnd ?: continue
            if (end <= now) { store.update(r.id) { it.copy(status = RecStatus.MISSED, note = "ended") }; continue }
            val item = resolve(r)
            if (item == null) { store.update(r.id) { it.copy(status = RecStatus.MISSED, note = "channel") }; continue }
            try {
                store.remove(r.id)
                start(item, end)
            } catch (e: Exception) {
                store.add(r.copy(status = RecStatus.MISSED, note = if (e is NotEnoughSpace) "space" else "budget"))
            }
        }
    }

    /** Bytes written so far by a running recording. */
    fun liveBytes(id: Long): Long = running[id]?.recorder?.bytes ?: 0

    private companion object {
        const val MIN_FREE_BYTES = 300L * 1024 * 1024
    }
}

@Composable
fun RecordingsScreen(state: AppState) {
    state.recordingStore.version
    val all = state.recordingStore.all().sortedByDescending { it.startedAt }
    var deleting by remember { mutableStateOf<RecordingDto?>(null) }
    if (all.isEmpty()) {
        Text(t("rec_empty"), Modifier.padding(16.dp), color = Color.White.copy(alpha = 0.75f))
        return
    }
    val format = remember { SimpleDateFormat("dd.MM.yyyy HH:mm") }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(all, key = { it.id }) { r ->
            val active = r.status == RecStatus.RECORDING
            val playable = r.status == RecStatus.DONE || r.status == RecStatus.INTERRUPTED
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = playable) {
                    state.player = PlayRequest.Single(Playable(r.title, File(r.path).toURI().toString().replaceFirst("file:/", "file:///"), null, false))
                }.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val bytes = if (active) state.recordingManager.liveBytes(r.id) else r.bytes
                    Text(
                        "${format.format(Date(r.startedAt))} · " + t(when (r.status) { RecStatus.RECORDING -> "rec_status_recording"; RecStatus.DONE -> "rec_status_done"; RecStatus.SCHEDULED -> "rec_status_scheduled"; RecStatus.MISSED -> "rec_status_missed"; else -> "rec_status_interrupted" }) +
                            " · ${sizeText(bytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.status == RecStatus.INTERRUPTED || r.status == RecStatus.MISSED) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.75f),
                    )
                }
                if (active) IconButton(onClick = { state.recordingManager.stop(r.id) }) { Icon(Icons.Filled.Stop, t("rec_stop")) }
                else if (playable) Icon(Icons.Filled.PlayArrow, t("rec_play"))
                IconButton(onClick = { deleting = r }) { Icon(Icons.Filled.Delete, t("action_delete")) }
            }
        }
    }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(t("rec_delete_confirm")) },
            confirmButton = { TextButton(onClick = { state.recordingManager.delete(r.id); deleting = null }) { Text(t("action_delete")) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(t("action_cancel")) } },
        )
    }
}
