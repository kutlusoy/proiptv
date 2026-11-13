package com.kutlusoy.proiptv.recording

import android.app.Application
import android.os.Environment
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.PlayTarget
import com.kutlusoy.proiptv.data.RecStatus
import com.kutlusoy.proiptv.data.Recording
import com.kutlusoy.proiptv.engine.EngineManager
import com.kutlusoy.proiptv.engine.HubLease
import com.kutlusoy.proiptv.engine.Recorder
import com.kutlusoy.proiptv.engine.RecordingEnd
import com.kutlusoy.proiptv.net.Upstream
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

class RecordingManager(
    private val app: Application,
    private val db: AppDatabase,
    private val engine: EngineManager,
    private val scope: CoroutineScope,
) {
    private class Running(val recorder: Recorder, val lease: HubLease, val info: ActiveRecording)

    private val running = ConcurrentHashMap<Long, Running>()
    private val _active = MutableStateFlow<List<ActiveRecording>>(emptyList())
    val active: StateFlow<List<ActiveRecording>> = _active

    fun directory(): File = app.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(app.filesDir, "recordings")

    suspend fun start(target: PlayTarget, maxConnections: Int?, stopAtMs: Long?): Long = withContext(Dispatchers.IO) {
        val dir = directory().apply { mkdirs() }
        if (dir.usableSpace < MIN_FREE_BYTES) throw NotEnoughSpace()
        val lease = engine.acquire(target.accountId, target.remoteId, target.title, maxConnections, Upstream.opener(target.url, target.userAgent))
        try {
            val now = System.currentTimeMillis()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now))
            val safe = target.title.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(40)
            val file = File(dir, "$safe-$stamp.ts")
            val id = db.recordings().insert(Recording(accountId = target.accountId, title = target.title, startedAt = now, plannedEnd = stopAtMs, path = file.absolutePath))
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
        db.recordings().get(id)?.let { runCatching { File(it.path).delete() } }
        db.recordings().delete(id)
    }

    suspend fun recoverInterrupted() = db.recordings().markInterrupted()

    private fun publish() {
        _active.value = running.values.map { it.info }
    }

    private companion object {
        const val MIN_FREE_BYTES = 200L * 1024 * 1024
    }
}
