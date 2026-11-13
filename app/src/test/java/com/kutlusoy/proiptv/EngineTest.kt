package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.engine.BudgetExceeded
import com.kutlusoy.proiptv.engine.EngineManager
import com.kutlusoy.proiptv.engine.HubReader
import com.kutlusoy.proiptv.engine.HubState
import com.kutlusoy.proiptv.engine.Recorder
import com.kutlusoy.proiptv.engine.RecordingEnd
import com.kutlusoy.proiptv.engine.RingBuffer
import com.kutlusoy.proiptv.engine.StreamHub
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class QueueStream : InputStream() {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private var current: ByteArray? = null
    private var index = 0

    @Volatile
    private var closed = false

    fun push(data: ByteArray) = queue.put(data)
    fun finish() = queue.put(ByteArray(0))

    override fun read(): Int = throw UnsupportedOperationException()

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        while (true) {
            if (closed) throw IOException("closed")
            val c = current
            if (c != null && index < c.size) {
                val n = minOf(len, c.size - index)
                System.arraycopy(c, index, b, off, n)
                index += n
                return n
            }
            val next = queue.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
            if (next.isEmpty()) return -1
            current = next
            index = 0
        }
    }

    override fun close() {
        closed = true
    }
}

class EngineTest {
    @get:Rule
    val tmp = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private var now = 1_000_000L

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() = scope.cancel()

    private fun bytes(n: Int, seed: Int = 0) = ByteArray(n) { (it + seed).toByte() }

    private fun ring(capacity: Long = 1024) = RingBuffer(tmp.newFile(), capacity) { now }

    @Test
    fun ringBufferWritesAndReadsAcrossTheWrap() {
        val r = ring(100)
        r.write(bytes(80), 0, 80)
        r.write(bytes(60, 80), 0, 60)
        assertEquals(140, r.end)
        assertEquals(40, r.start)
        val out = ByteArray(100)
        var got = 0
        var pos = 40L
        while (got < 100) {
            val n = r.read(pos, out, got, 100 - got, 100)
            assertTrue(n > 0)
            got += n
            pos += n
        }
        val expected = ByteArray(100) { (it + 40).toByte() }
        assertArrayEquals(expected, out)
    }

    @Test
    fun ringBufferReportsOverrunTimeoutAndEnd() {
        val r = ring(100)
        r.write(bytes(250), 0, 250)
        assertEquals(RingBuffer.OVERRUN, r.read(0, ByteArray(10), 0, 10, 10))
        assertEquals(RingBuffer.TIMEOUT, r.read(250, ByteArray(10), 0, 10, 20))
        r.close()
        assertEquals(RingBuffer.END, r.read(250, ByteArray(10), 0, 10, 20))
    }

    @Test
    fun blockedReaderWakesWhenDataArrives() {
        val r = ring()
        var result = 0
        val t = Thread { result = r.read(0, ByteArray(10), 0, 10, 5000) }
        t.start()
        Thread.sleep(100)
        r.write(bytes(5), 0, 5)
        t.join(2000)
        assertEquals(5, result)
    }

    @Test
    fun timeIndexMapsTimeToOffsets() {
        val r = ring(10_000)
        for (i in 0 until 10) {
            now = 1_000_000L + i * 1000L
            r.write(bytes(100), 0, 100)
        }
        assertEquals(1_000_000L, r.oldestTime())
        assertEquals(500L, r.offsetAtTime(1_000_000L + 4000L) - 0)
        assertEquals(1000L, r.offsetAtTime(2_000_000L))
        assertEquals(100L, r.offsetAtTime(0L).coerceAtLeast(100L))
        assertEquals(1_004_000L, r.timeAtOffset(500))
    }

    @Test
    fun hubFansOutOneUpstreamToSeveralReaders() {
        val stream = QueueStream()
        val opened = AtomicInteger()
        val hub = StreamHub("a:1", 1, "One", ring(1 shl 20), { opened.incrementAndGet(); stream }, scope)
        hub.start()
        val live = hub.reader(0)
        stream.push(bytes(300))
        val late = hub.reader(0)
        stream.push(bytes(200, 7))
        val a = ByteArray(500)
        val b = ByteArray(500)
        fun fill(r: HubReader, out: ByteArray) {
            var g = 0
            while (g < out.size) {
                val n = r.read(out, g, out.size - g, 3000)
                assertTrue(n > 0)
                g += n
            }
        }
        fill(live, a)
        fill(late, b)
        assertArrayEquals(a, b)
        assertEquals(1, opened.get())
        assertEquals(HubState.LIVE, hub.state.value)
        hub.close()
    }

    @Test
    fun hubReconnectsAndPadsToTransportPacketSize() {
        val streams = LinkedBlockingQueue<InputStream>()
        val first = QueueStream()
        val second = QueueStream()
        streams.put(first)
        streams.put(second)
        val hub = StreamHub("a:1", 1, "One", ring(1 shl 20), { streams.take() }, scope, backoffMs = { 20 })
        hub.start()
        first.push(bytes(200))
        first.finish()
        Thread.sleep(300)
        second.push(bytes(188, 1))
        runBlocking { withTimeout(5000) { while (hub.buffer.end < 188 * 3) delay(20) } }
        assertEquals(0L, hub.buffer.end % 188)
        assertEquals(HubState.LIVE, hub.state.value)
        hub.close()
    }

    @Test
    fun hubFailsAfterTooManyAttempts() {
        val hub = StreamHub("a:1", 1, "One", ring(), { throw IOException("down") }, scope, maxAttempts = 2, backoffMs = { 10 })
        hub.start()
        runBlocking { withTimeout(5000) { while (hub.state.value != HubState.FAILED) delay(20) } }
        assertTrue(hub.buffer.closed)
        assertEquals("down", hub.lastError)
    }

    @Test
    fun recorderCopiesFromAnOffsetUntilStopped() {
        val stream = QueueStream()
        val hub = StreamHub("a:1", 1, "One", ring(1 shl 20), { stream }, scope)
        hub.start()
        stream.push(bytes(100))
        runBlocking { withTimeout(3000) { while (hub.buffer.end < 100) delay(10) } }
        val file = File(tmp.root, "out.ts")
        val recorder = Recorder(hub, file, 40, null)
        var end: RecordingEnd? = null
        val t = Thread { end = recorder.run() }
        t.start()
        stream.push(bytes(50, 100))
        runBlocking { withTimeout(3000) { while (recorder.bytes < 110) delay(10) } }
        recorder.requestStop()
        t.join(3000)
        assertEquals(RecordingEnd.STOPPED, end)
        val expected = ByteArray(110) { (if (it < 60) it + 40 else it - 60 + 100).toByte() }
        assertArrayEquals(expected, file.readBytes())
        hub.close()
    }

    @Test
    fun recorderStopsAtTheGivenTime() {
        val stream = QueueStream()
        val hub = StreamHub("a:1", 1, "One", ring(), { stream }, scope)
        hub.start()
        val recorder = Recorder(hub, File(tmp.root, "t.ts"), 0, System.currentTimeMillis() + 300)
        assertEquals(RecordingEnd.TIME_REACHED, recorder.run())
        hub.close()
    }

    @Test
    fun managerSharesOneHubAndEnforcesTheConnectionLimit() {
        val manager = EngineManager(tmp.newFolder("hubs"), scope, { 1L shl 16 }, graceMs = 100)
        val a = manager.acquire(1, "10", "Ten", 1) { QueueStream() }
        val again = manager.acquire(1, "10", "Ten", 1) { QueueStream() }
        assertEquals(2, manager.refCount(1, "10"))
        assertEquals(a.hub, again.hub)
        try {
            manager.acquire(1, "11", "Eleven", 1) { QueueStream() }
            throw AssertionError("limit not enforced")
        } catch (e: BudgetExceeded) {
            assertEquals(1, e.limit)
            assertEquals(listOf("Ten"), e.openTitles)
        }
        manager.acquire(2, "10", "Other account", 1) { QueueStream() }.close()
        a.close()
        assertEquals(1, manager.refCount(1, "10"))
        again.close()
        manager.closeAll()
    }

    @Test
    fun idleHubIsEvictedForANewChannelAndClosedAfterTheGrace() {
        val manager = EngineManager(tmp.newFolder("hubs2"), scope, { 1L shl 16 }, graceMs = 200)
        val a = manager.acquire(1, "10", "Ten", 1) { QueueStream() }
        val hubA = a.hub
        a.close()
        val b = manager.acquire(1, "11", "Eleven", 1) { QueueStream() }
        assertTrue(hubA.buffer.closed)
        assertEquals(listOf("1:11"), manager.openKeys())
        b.close()
        Thread.sleep(500)
        assertFalse(manager.openKeys().isNotEmpty())
    }

    @Test
    fun hubStaysOpenWhileARecorderHoldsALease() {
        val manager = EngineManager(tmp.newFolder("hubs3"), scope, { 1L shl 16 }, graceMs = 50)
        val viewer = manager.acquire(1, "10", "Ten", 2) { QueueStream() }
        val recording = manager.acquire(1, "10", "Ten", 2) { QueueStream() }
        viewer.close()
        Thread.sleep(300)
        assertFalse(viewer.hub.buffer.closed)
        recording.close()
        Thread.sleep(300)
        assertTrue(viewer.hub.buffer.closed)
    }
}
