package com.kutlusoy.proiptv.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

object DlStatus {
    const val QUEUED = 0
    const val RUNNING = 1
    const val DONE = 2
    const val FAILED = 3
}

@Serializable
data class DownloadDto(
    val id: Long,
    val account: String,
    val kind: Int,
    val remoteId: String,
    val title: String,
    val logo: String? = null,
    val ext: String = "mp4",
    val seriesId: String = "",
    val seriesName: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val bytes: Long = 0,
    val total: Long = 0,
    val status: Int = DlStatus.QUEUED,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val key: String get() = "$account|$kind|$remoteId"
}

/** The list of downloads in downloads.json. Every change is written at once, so a restart finds the queue as it was. */
class DownloadStore(private val dir: File) {
    private val file = File(dir, "downloads.json")
    private val json = Json { ignoreUnknownKeys = true }
    private var list: List<DownloadDto> = runCatching { json.decodeFromString<List<DownloadDto>>(file.readText()) }.getOrDefault(emptyList())

    var version by mutableIntStateOf(0)
        private set

    @Synchronized
    fun all(): List<DownloadDto> = list

    @Synchronized
    fun get(id: Long) = list.firstOrNull { it.id == id }

    @Synchronized
    fun add(d: DownloadDto): Boolean {
        if (list.any { it.key == d.key }) return false
        list = list + d.copy(id = (list.maxOfOrNull { it.id } ?: 0) + 1)
        write()
        return true
    }

    @Synchronized
    fun update(id: Long, change: (DownloadDto) -> DownloadDto) {
        list = list.map { if (it.id == id) change(it) else it }
        write()
    }

    @Synchronized
    fun remove(id: Long) {
        list = list.filterNot { it.id == id }
        write()
    }

    @Synchronized
    fun resetRunning() {
        list = list.map { if (it.status == DlStatus.RUNNING) it.copy(status = DlStatus.QUEUED) else it }
        write()
    }

    @Synchronized
    fun next() = list.firstOrNull { it.status == DlStatus.QUEUED }

    private fun write() {
        dir.mkdirs()
        runCatching { file.writeText(json.encodeToString(list)) }
        version++
    }
}

/** Downloads one file after the other with a resumable HTTP transfer (Range). The source is looked up when the transfer starts, so no address with a password is stored. */
class DownloadManager(
    private val store: DownloadStore,
    private val scope: CoroutineScope,
    private val client: OkHttpClient,
    private val dir: File,
    private val resolve: (DownloadDto) -> Pair<String, String?>?,
) {
    private var worker: Job? = null
    private var running: Job? = null
    private var currentId = -1L

    fun fileOf(d: DownloadDto): File = File(dir, "${d.id}.${d.ext.ifBlank { "mp4" }}")

    fun enqueue(list: List<DownloadDto>) {
        list.forEach { store.add(it) }
        start()
    }

    fun retry(id: Long) {
        store.update(id) { it.copy(status = DlStatus.QUEUED, error = null) }
        start()
    }

    fun delete(id: Long) {
        scope.launch(Dispatchers.IO) {
            if (currentId == id) { running?.cancel(); running?.join() }
            store.get(id)?.let { fileOf(it).delete() }
            store.remove(id)
        }
    }

    fun resume() {
        store.resetRunning()
        start()
    }

    @Synchronized
    fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch(Dispatchers.IO) {
            while (true) {
                val next = store.next() ?: break
                currentId = next.id
                val job = launch { process(next) }
                running = job
                job.join()
                currentId = -1L
            }
        }
    }

    private suspend fun process(d: DownloadDto) {
        store.update(d.id) { it.copy(status = DlStatus.RUNNING, error = null) }
        try {
            val source = resolve(d)
            if (source == null) {
                store.update(d.id) { it.copy(status = DlStatus.FAILED, error = ERR_SOURCE) }
                return
            }
            transfer(d, source.first, source.second)
            val size = fileOf(d).length()
            store.update(d.id) { it.copy(status = DlStatus.DONE, bytes = size, total = size, error = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoSpace) {
            store.update(d.id) { it.copy(status = DlStatus.FAILED, error = ERR_SPACE) }
        } catch (e: Exception) {
            store.update(d.id) { it.copy(status = DlStatus.FAILED, error = e.message?.take(120) ?: ERR_NETWORK) }
        }
    }

    private fun transfer(d: DownloadDto, url: String, userAgent: String?) {
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
                            val w = written
                            store.update(d.id) { it.copy(bytes = w, total = total) }
                            if (!scope.isActive) throw CancellationException()
                        }
                    }
                }
                val w = written
                store.update(d.id) { it.copy(bytes = w, total = total) }
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

        /** The folder for downloads and recordings: the Videos folder of the user. */
        fun mediaDir(sub: String): File {
            val home = System.getProperty("user.home")
            val videos = File(home, "Videos")
            return File(if (videos.isDirectory) videos else File(home), "ProIPTV/$sub")
        }
    }
}
