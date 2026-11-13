package com.kutlusoy.proiptv

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.PlayTarget
import com.kutlusoy.proiptv.data.RecStatus
import com.kutlusoy.proiptv.engine.BudgetExceeded
import com.kutlusoy.proiptv.engine.EngineManager
import com.kutlusoy.proiptv.engine.RingBuffer
import com.kutlusoy.proiptv.engine.StreamHub
import com.kutlusoy.proiptv.player.HubDataSource
import com.kutlusoy.proiptv.recording.RecordingManager
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingTest {
    @get:Rule
    val tmp = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var manager: RecordingManager
    private lateinit var engine: EngineManager
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        engine = EngineManager(tmp.newFolder("hubs"), scope, { 1L shl 20 }, graceMs = 100)
        manager = RecordingManager(app, db, engine, scope)
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        engine.closeAll()
        scope.cancel()
        db.close()
    }

    private fun target(name: String = "One", remote: String = "10") =
        PlayTarget(server.url("/live/$remote.ts").toString(), "Test", name, true, 1, Kind.LIVE, remote)

    @Test
    fun recordingWritesTheStreamToAFileAndFinishesAtTheEndTime() = runBlocking {
        val data = ByteArray(300_000) { (it * 7).toByte() }
        repeat(5) { server.enqueue(MockResponse().setBodyDelay(200, TimeUnit.MILLISECONDS).setBody(Buffer().write(data))) }
        val id = manager.start(target(), null, System.currentTimeMillis() + 1500)
        assertEquals(1, manager.active.value.size)
        withTimeout(10_000) { while (manager.active.value.isNotEmpty()) delay(50) }
        val row = db.recordings().get(id)!!
        assertEquals(RecStatus.DONE, row.status)
        assertTrue(row.bytes >= data.size)
        val file = File(row.path)
        assertTrue(file.exists())
        assertArrayEquals(data, file.readBytes().copyOf(data.size))
        assertTrue(row.endedAt != null)
    }

    @Test
    fun stopEndsARecordingAndDeleteRemovesFileAndRow() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(50_000)))) }
        val id = manager.start(target(), null, null)
        delay(500)
        manager.stop(id)
        withTimeout(10_000) { while (manager.active.value.isNotEmpty()) delay(50) }
        assertEquals(RecStatus.DONE, db.recordings().get(id)!!.status)
        val path = db.recordings().get(id)!!.path
        manager.delete(id)
        assertEquals(null, db.recordings().get(id))
        assertTrue(!File(path).exists())
    }

    @Test
    fun recordingCountsAgainstTheConnectionLimitAndSharesTheChannelHub() = runBlocking {
        repeat(5) { server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(20_000)))) }
        val id = manager.start(target(), 1, null)
        val viewer = engine.acquire(1, "10", "One", 1) { ByteArrayInputStream(ByteArray(0)) }
        assertEquals(2, engine.refCount(1, "10"))
        viewer.close()
        try {
            manager.start(target("Two", "11"), 1, null)
            throw AssertionError("limit not enforced")
        } catch (e: BudgetExceeded) {
            assertEquals(listOf("One"), e.openTitles)
        }
        manager.stop(id)
        withTimeout(10_000) { while (manager.active.value.isNotEmpty()) delay(50) }
    }

    @Test
    fun interruptedRecordingsAreMarkedAfterARestart() = runBlocking {
        val id = db.recordings().insert(com.kutlusoy.proiptv.data.Recording(accountId = 1, title = "x", startedAt = 1, path = "p"))
        manager.recoverInterrupted()
        assertEquals(RecStatus.INTERRUPTED, db.recordings().get(id)!!.status)
    }

    @UnstableApi
    @Test
    fun hubDataSourceReadsFromAnOffsetAndEndsWithTheHub() {
        val data = ByteArray(1000) { it.toByte() }
        val buffer = RingBuffer(tmp.newFile(), 1 shl 16)
        val hub = StreamHub("a:1", 1, "One", buffer, { ByteArrayInputStream(data) }, scope)
        hub.start()
        runBlocking { withTimeout(5000) { while (buffer.end < 1000) delay(10) } }
        val source = HubDataSource.Factory(hub, 100).createDataSource()
        assertEquals(C.LENGTH_UNSET.toLong(), source.open(DataSpec(Uri.parse("hub://a:1"))))
        val out = ByteArray(300)
        var got = 0
        while (got < out.size) {
            val n = source.read(out, got, out.size - got)
            assertTrue(n > 0)
            got += n
        }
        assertArrayEquals(data.copyOfRange(100, 400), out)
        source.close()
        hub.close()
        val closed = HubDataSource.Factory(hub, buffer.end).createDataSource()
        closed.open(DataSpec(Uri.parse("hub://a:1")))
        assertEquals(C.RESULT_END_OF_INPUT, closed.read(ByteArray(10), 0, 10))
        closed.close()
    }
}
