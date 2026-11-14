package com.kutlusoy.proiptv.desktop

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsTest {
    private fun tempDir() = File.createTempFile("proiptv", "").also { it.delete(); it.mkdirs() }

    private fun waitFor(limitMs: Long = 10_000, done: () -> Boolean) {
        val end = System.currentTimeMillis() + limitMs
        while (!done() && System.currentTimeMillis() < end) Thread.sleep(50)
    }

    private fun movie(id: String = "7") = DownloadDto(0, "a", Kind.MOVIE, id, "Film")

    @Test
    fun aDownloadIsSavedAndASecondOneOfTheSameItemIsIgnored() {
        val dir = tempDir()
        val store = DownloadStore(dir)
        assertTrue(store.add(movie()))
        assertFalse(store.add(movie()))
        assertEquals(1, DownloadStore(dir).all().size)
    }

    @Test
    fun aRunningDownloadGoesBackToTheQueueAfterARestart() {
        val dir = tempDir()
        val store = DownloadStore(dir)
        store.add(movie())
        store.update(1) { it.copy(status = DlStatus.RUNNING) }
        val again = DownloadStore(dir)
        again.resetRunning()
        assertEquals(DlStatus.QUEUED, again.get(1)?.status)
    }

    @Test
    fun theFileIsLoadedAndAnInterruptedOneContinuesWhereItStopped() {
        val server = MockWebServer()
        val payload = ByteArray(200_000) { (it % 251).toByte() }
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                return if (range != null) {
                    val from = range.removePrefix("bytes=").removeSuffix("-").toInt()
                    MockResponse().setResponseCode(206).setBody(Buffer().write(payload, from, payload.size - from))
                } else MockResponse().setBody(Buffer().write(payload))
            }
        }
        server.start()
        val dir = tempDir()
        val store = DownloadStore(tempDir())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val manager = DownloadManager(store, scope, OkHttpClient(), dir) { server.url("/f").toString() to null }
            manager.enqueue(listOf(movie()))
            waitFor { store.get(1)?.status == DlStatus.DONE }
            assertEquals(DlStatus.DONE, store.get(1)?.status)
            assertTrue(payload.contentEquals(manager.fileOf(store.get(1)!!).readBytes()))

            store.add(movie("8"))
            manager.fileOf(store.get(2)!!).writeBytes(payload.copyOf(50_000))
            manager.retry(2)
            waitFor { store.get(2)?.status == DlStatus.DONE }
            assertTrue(payload.contentEquals(manager.fileOf(store.get(2)!!).readBytes()))
        } finally {
            scope.cancel()
            server.shutdown()
        }
    }

    @Test
    fun aPausedDownloadKeepsWhatWasLoadedAndContinuesAfterResume() {
        val server = MockWebServer()
        val payload = ByteArray(400_000) { (it % 251).toByte() }
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
                val r = MockResponse().setBody(Buffer().write(payload, from, payload.size - from)).throttleBody(40_000, 100, java.util.concurrent.TimeUnit.MILLISECONDS)
                return if (range != null) r.setResponseCode(206) else r
            }
        }
        server.start()
        val store = DownloadStore(tempDir())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val manager = DownloadManager(store, scope, OkHttpClient(), tempDir()) { server.url("/f").toString() to null }
            manager.enqueue(listOf(movie()))
            waitFor { (store.get(1)?.bytes ?: 0) > 0 }
            manager.pause(1)
            waitFor { store.get(1)?.status == DlStatus.PAUSED }
            Thread.sleep(500)
            assertEquals(DlStatus.PAUSED, store.get(1)?.status)
            val partial = manager.fileOf(store.get(1)!!).length()
            assertTrue("partial $partial", partial in 1 until payload.size.toLong())
            manager.resumePaused(1)
            waitFor(20_000) { store.get(1)?.status == DlStatus.DONE }
            assertTrue(payload.contentEquals(manager.fileOf(store.get(1)!!).readBytes()))
        } finally {
            scope.cancel()
            server.shutdown()
        }
    }

    @Test
    fun aMissingSourceFailsTheDownloadWithoutCrashing() {
        val store = DownloadStore(tempDir())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val manager = DownloadManager(store, scope, OkHttpClient(), tempDir()) { null }
            manager.enqueue(listOf(movie()))
            waitFor { store.get(1)?.status == DlStatus.FAILED }
            assertEquals(DownloadManager.ERR_SOURCE, store.get(1)?.error)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun theInstallerShipsEveryPluginThatTheAppNeeds() {
        val listed = File("vlc/plugins.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.toSet()
        // imem: media from callbacks (shared stream buffer), vmem: pictures into memory, the others decode and play the usual streams
        for (needed in listOf("access/libimem_plugin.dll", "video_output/libvmem_plugin.dll", "access/libhttp_plugin.dll", "access/libhttps_plugin.dll", "demux/libts_plugin.dll", "codec/libavcodec_plugin.dll", "audio_output/libmmdevice_plugin.dll", "logger/libfile_logger_plugin.dll")) {
            assertTrue("$needed is missing in plugins.txt", needed in listed)
        }
    }
}
