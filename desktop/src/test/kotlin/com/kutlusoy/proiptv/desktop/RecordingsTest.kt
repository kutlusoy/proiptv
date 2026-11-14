package com.kutlusoy.proiptv.desktop

import com.kutlusoy.proiptv.engine.EngineManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingsTest {
    private fun tempDir() = File.createTempFile("proiptv", "").also { it.delete(); it.mkdirs() }

    private fun waitFor(limitMs: Long = 10_000, done: () -> Boolean) {
        val end = System.currentTimeMillis() + limitMs
        while (!done() && System.currentTimeMillis() < end) Thread.sleep(50)
    }

    @Test
    fun aRunningRecordingIsMarkedInterruptedAfterARestart() {
        val dir = tempDir()
        val store = RecordingStore(dir)
        val f = File(dir, "a.ts").also { it.writeBytes(ByteArray(10)) }
        val id = store.add(RecordingDto(0, "a", "Das Erste", 1, path = f.absolutePath, status = RecStatus.RECORDING))
        val again = RecordingStore(dir)
        assertEquals(RecStatus.INTERRUPTED, again.get(id)?.status)
        assertEquals(10L, again.get(id)?.bytes)
    }

    @Test
    fun theStreamIsRecordedAndStopsAtTheEndTimeWithOneConnection() {
        val server = MockWebServer()
        val chunk = ByteArray(188 * 400) { 0x47 }
        // an endless live stream: the answer is a long body that is throttled
        server.enqueue(MockResponse().setBody(Buffer().write(chunk)).throttleBody(4_000, 100, java.util.concurrent.TimeUnit.MILLISECONDS))
        server.start()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val hubs = EngineManager(tempDir(), scope, capacity = { 4L * 1024 * 1024 })
        val store = RecordingStore(tempDir())
        val manager = RecordingManager(store, hubs, scope, tempDir())
        try {
            val item = Playable("Test channel", server.url("/live").toString(), null, true, "a", 0, "5", accountNumber = 1, channelId = "5", maxConnections = 1)
            val id = manager.start(item, System.currentTimeMillis() + 1_500)
            assertEquals(id, manager.isRecording("a", "5"))
            waitFor { store.get(id)?.status != RecStatus.RECORDING }
            val done = store.get(id)!!
            assertEquals(RecStatus.DONE, done.status)
            assertTrue("bytes ${done.bytes}", done.bytes > 0)
            assertEquals(done.bytes, File(done.path).length())
            assertEquals(1, server.requestCount)
        } finally {
            hubs.closeAll()
            scope.cancel()
            server.shutdown()
        }
    }

    @Test
    fun aPlannedRecordingStartsWhenItsTimeHasComeAndOneThatEndedIsMissed() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(188 * 400) { 0x47 })).throttleBody(4_000, 100, java.util.concurrent.TimeUnit.MILLISECONDS))
        server.start()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val hubs = EngineManager(tempDir(), scope, capacity = { 4L * 1024 * 1024 })
        val store = RecordingStore(tempDir())
        val manager = RecordingManager(store, hubs, scope, tempDir())
        try {
            val item = Playable("Test channel", server.url("/live").toString(), null, true, "a", 0, "5", accountNumber = 1, channelId = "5", maxConnections = 1)
            val now = System.currentTimeMillis()
            val later = manager.schedule(item, now + 3_600_000, now + 7_200_000)
            val due = manager.schedule(item, now - 1_000, now + 1_500)
            val old = manager.schedule(item.copy(channelId = "6"), now - 7_200_000, now - 3_600_000)
            manager.startDue(now) { item }
            assertEquals(RecStatus.SCHEDULED, store.get(later)?.status)
            assertEquals(RecStatus.MISSED, store.get(old)?.status)
            assertEquals(null, store.get(due))
            val running = store.all().first { it.status == RecStatus.RECORDING || it.status == RecStatus.DONE }
            waitFor { store.get(running.id)?.status == RecStatus.DONE }
            assertEquals(RecStatus.DONE, store.get(running.id)?.status)
        } finally {
            hubs.closeAll()
            scope.cancel()
            server.shutdown()
        }
    }
}
