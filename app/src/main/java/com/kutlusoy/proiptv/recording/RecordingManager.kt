package com.kutlusoy.proiptv.recording

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Environment
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.PlayTarget
import com.kutlusoy.proiptv.data.RecStatus
import com.kutlusoy.proiptv.data.Recording
import com.kutlusoy.proiptv.engine.BudgetExceeded
import com.kutlusoy.proiptv.engine.EngineManager
import com.kutlusoy.proiptv.engine.HubLease
import com.kutlusoy.proiptv.engine.Recorder
import com.kutlusoy.proiptv.engine.RecordingEnd
import com.kutlusoy.proiptv.net.Upstream
import com.kutlusoy.proiptv.ui.MainActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotEnoughSpace : Exception("not enough free storage")

data class ActiveRecording(val id: Long, val hubKey: String, val title: String)

object RecNote {
    const val BUDGET = "budget"
    const val SPACE = "space"
    const val CHANNEL = "channel"
    const val ENDED = "ended"
}

class RecordingManager(
    private val app: Application,
    private val db: AppDatabase,
    private val engine: EngineManager,
    private val scope: CoroutineScope,
    private val resolve: suspend (Item) -> PlayTarget?,
    private val maxConnections: suspend (Long) -> Int?,
) {
    private class Running(val recorder: Recorder, val lease: HubLease, val info: ActiveRecording)

    private val running = ConcurrentHashMap<Long, Running>()
    private val _active = MutableStateFlow<List<ActiveRecording>>(emptyList())
    val active: StateFlow<List<ActiveRecording>> = _active

    fun directory(): File = app.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(app.filesDir, "recordings")

    suspend fun start(target: PlayTarget, maxConnections: Int?, stopAtMs: Long?): Long = begin(null, target, maxConnections, stopAtMs)

    private suspend fun begin(existing: Recording?, target: PlayTarget, maxConnections: Int?, stopAtMs: Long?): Long = withContext(Dispatchers.IO) {
        val dir = directory().apply { mkdirs() }
        if (dir.usableSpace < MIN_FREE_BYTES) throw NotEnoughSpace()
        val lease = engine.acquire(target.accountId, target.remoteId, target.title, maxConnections, Upstream.opener(target.url, target.userAgent))
        try {
            val now = System.currentTimeMillis()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now))
            val safe = target.title.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(40)
            val file = File(dir, "$safe-$stamp.ts")
            val id = if (existing == null) {
                db.recordings().insert(
                    Recording(accountId = target.accountId, title = target.title, startedAt = now, plannedEnd = stopAtMs, path = file.absolutePath, channelRemoteId = target.remoteId),
                )
            } else {
                db.recordings().update(existing.copy(startedAt = now, path = file.absolutePath, status = RecStatus.RECORDING, note = null))
                existing.id
            }
            val recorder = Recorder(lease.hub, file, lease.hub.buffer.end, stopAtMs)
            running[id] = Running(recorder, lease, ActiveRecording(id, lease.hub.key, target.title))
            publish()
            scope.launch(Dispatchers.IO) { finish(id, recorder.run()) }
            RecordingService.start(app)
            id
        } catch (e: Exception) {
            lease.close()
            throw e
        }
    }

    private suspend fun finish(id: Long, reason: RecordingEnd) {
        val r = running.remove(id)
        r?.lease?.close()
        db.recordings().get(id)?.let {
            val status = if (reason == RecordingEnd.STOPPED || reason == RecordingEnd.TIME_REACHED) RecStatus.DONE else RecStatus.INTERRUPTED
            db.recordings().update(it.copy(endedAt = System.currentTimeMillis(), bytes = r?.recorder?.bytes ?: File(it.path).length(), status = status))
        }
        publish()
    }

    fun stop(id: Long) {
        running[id]?.recorder?.requestStop()
    }

    suspend fun delete(id: Long) {
        stop(id)
        cancelAlarm(id)
        db.recordings().get(id)?.let { if (it.path.isNotEmpty()) runCatching { File(it.path).delete() } }
        db.recordings().delete(id)
    }

    suspend fun recoverInterrupted() = db.recordings().markInterrupted()

    suspend fun schedule(item: Item, title: String, startMs: Long, endMs: Long): Long {
        val id = db.recordings().insert(
            Recording(
                accountId = item.accountId, title = title, startedAt = startMs, plannedStart = startMs, plannedEnd = endMs,
                path = "", status = RecStatus.SCHEDULED, channelRemoteId = item.remoteId,
            ),
        )
        setAlarm(id, startMs)
        return id
    }

    suspend fun conflicts(item: Item, startMs: Long, endMs: Long): Boolean {
        val max = maxConnections(item.accountId) ?: return false
        val others = db.recordings().overlapping(item.accountId, startMs, endMs).map { it.channelRemoteId }.filter { it != item.remoteId }.distinct()
        return others.size + 1 > max
    }

    suspend fun startScheduled(id: Long) {
        val row = db.recordings().get(id) ?: return
        if (row.status != RecStatus.SCHEDULED) return
        cancelAlarm(id)
        val end = row.plannedEnd ?: return markMissed(row, RecNote.ENDED)
        if (System.currentTimeMillis() >= end) return markMissed(row, RecNote.ENDED)
        val item = db.items().find(row.accountId, Kind.LIVE, row.channelRemoteId) ?: return markMissed(row, RecNote.CHANNEL)
        val target = resolve(item) ?: return markMissed(row, RecNote.CHANNEL)
        try {
            begin(row, target.copy(title = row.title), maxConnections(row.accountId), end)
        } catch (e: BudgetExceeded) {
            markMissed(row, RecNote.BUDGET)
        } catch (e: NotEnoughSpace) {
            markMissed(row, RecNote.SPACE)
        } catch (e: Exception) {
            markMissed(row, RecNote.CHANNEL)
        }
    }

    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        for (row in db.recordings().scheduled()) {
            val start = row.plannedStart ?: row.startedAt
            if (start <= now) startScheduled(row.id) else setAlarm(row.id, start)
        }
    }

    private suspend fun markMissed(row: Recording, note: String) {
        db.recordings().update(row.copy(status = RecStatus.MISSED, note = note, endedAt = System.currentTimeMillis()))
        RecordingNotifications.missed(app, row.id, row.title)
    }

    private fun alarmIntent(id: Long, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            app, id.toInt(), Intent(app, RecordingAlarmReceiver::class.java).putExtra(EXTRA_ID, id),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun setAlarm(id: Long, atMs: Long) {
        val manager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(atMs, show), alarmIntent(id, PendingIntent.FLAG_UPDATE_CURRENT)!!)
    }

    private fun cancelAlarm(id: Long) {
        val manager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmIntent(id, PendingIntent.FLAG_NO_CREATE)?.let { manager.cancel(it) }
    }

    private fun publish() {
        _active.value = running.values.map { it.info }
    }

    companion object {
        const val EXTRA_ID = "recording_id"
        private const val MIN_FREE_BYTES = 200L * 1024 * 1024
    }
}
