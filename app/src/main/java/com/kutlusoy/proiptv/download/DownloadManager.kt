package com.kutlusoy.proiptv.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.DlStatus
import com.kutlusoy.proiptv.data.Download
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request

class DownloadManager(
    private val context: Context?,
    private val db: AppDatabase,
    private val scope: CoroutineScope,
    private val client: OkHttpClient,
    private val dir: File,
    private val resolve: suspend (Download) -> Pair<String, String?>?,
) {
    private var worker: Job? = null
    private var running: Job? = null
    private var currentId = -1L
    private val dao get() = db.downloads()

    private val _current = MutableStateFlow<Download?>(null)
    val current: StateFlow<Download?> = _current

    fun fileOf(d: Download): File = File(dir, "${d.id}.${d.ext.ifBlank { "mp4" }}")

    fun enqueue(list: List<Download>) {
        scope.launch {
            for (d in list) dao.insert(d)
            start()
        }
    }

    fun retry(id: Long) {
        scope.launch {
            dao.setStatus(id, DlStatus.QUEUED, null)
            start()
        }
    }

    fun delete(id: Long) {
        scope.launch {
            if (currentId == id) running?.cancelAndJoin()
            dao.get(id)?.let { fileOf(it).delete() }
            dao.delete(id)
        }
    }

    suspend fun resume() {
        dao.resetRunning()
        if (dao.pending() > 0) start()
    }

    fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            context?.let { runCatching { ContextCompat.startForegroundService(it, Intent(it, DownloadService::class.java)) } }
            while (true) {
                val next = dao.nextQueued() ?: break
                currentId = next.id
                val job = launch { process(next) }
                running = job
                job.join()
                currentId = -1L
                _current.value = null
            }
            context?.let { runCatching { it.stopService(Intent(it, DownloadService::class.java)) } }
        }
    }

    private suspend fun process(d: Download) {
        dao.setStatus(d.id, DlStatus.RUNNING, null)
        _current.value = d
        try {
            val source = resolve(d)
            if (source == null) {
                dao.setStatus(d.id, DlStatus.FAILED, ERR_SOURCE)
                return
            }
            withContext(Dispatchers.IO) { transfer(d, source.first, source.second) }
            val size = fileOf(d).length()
            dao.setProgress(d.id, size, size)
            dao.setStatus(d.id, DlStatus.DONE, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoSpace) {
            dao.setStatus(d.id, DlStatus.FAILED, ERR_SPACE)
        } catch (e: Exception) {
            dao.setStatus(d.id, DlStatus.FAILED, e.message?.take(120) ?: ERR_NETWORK)
        }
    }

    private suspend fun transfer(d: Download, url: String, userAgent: String?) {
        dir.mkdirs()
        val file = fileOf(d)
        val offset = if (file.exists()) file.length() else 0L
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: "ProIPTV")
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 416 && offset > 0) return
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val resumed = response.code == 206
            val start = if (resumed) offset else 0L
            val length = response.body?.contentLength() ?: -1L
            val total = if (length > 0) start + length else 0L
            if (total > 0 && dir.usableSpace < (total - start) + MIN_FREE) throw NoSpace()
            val body = response.body ?: throw IOException("Empty answer")
            RandomAccessFile(file, "rw").use { out ->
                if (!resumed) out.setLength(0)
                out.seek(start)
                var written = start
                var lastReport = 0L
                val buffer = ByteArray(64 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        written += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= REPORT_MS) {
                            lastReport = now
                            dao.setProgress(d.id, written, total)
                            if (!scope.isActive) throw CancellationException()
                        }
                    }
                }
                dao.setProgress(d.id, written, total)
            }
        }
    }

    private class NoSpace : IOException()

    companion object {
        const val ERR_SOURCE = "source"
        const val ERR_SPACE = "space"
        const val ERR_NETWORK = "network"
        private const val MIN_FREE = 100L * 1024 * 1024
        private const val REPORT_MS = 1000L
    }
}

private suspend fun Job.cancelAndJoin() {
    cancel()
    join()
}
