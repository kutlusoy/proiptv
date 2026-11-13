package com.kutlusoy.proiptv

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.PlayTarget
import com.kutlusoy.proiptv.data.RecStatus
import com.kutlusoy.proiptv.engine.BudgetExceeded
import com.kutlusoy.proiptv.engine.EngineManager
import com.kutlusoy.proiptv.engine.RingBuffer
import com.kutlusoy.proiptv.engine.StreamHub
import com.kutlusoy.proiptv.player.HubDataSource
import com.kutlusoy.proiptv.recording.RecNote
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
import org.robolectric.Shadows.shadowOf
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
    private var limit: Int? = null
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        engine = EngineManager(tmp.newFolder("hubs"), scope, { 1L shl 20 }, graceMs = 100)
        server = MockWebServer().also { it.start() }
        manager = RecordingManager(
            app, db, engine, scope,
            resolve = { PlayTarget(server.url("/live/${it.remoteId}.ts").toString(), "Test", it.name, true, it.accountId, Kind.LIVE, it.remoteId) },
            maxConnections = { limit },
        )
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

    private fun live(remote: String = "10", name: String = "One") = Item(accountId = 1, kind = Kind.LIVE, remoteId = remote, name = name)

    private val alarms get() = shadowOf(app.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager)

    @Test
    fun scheduledRecordingGetsAnAlarmStartsAndFinishes() = runBlocking {
        repeat(5) { server.enqueue(MockResponse().setBodyDelay(100, TimeUnit.MILLISECONDS).setBody(Buffer().write(ByteArray(100_000) { 5 }))) }
        db.items().upsert(listOf(live()))
        val now = System.currentTimeMillis()
        val id = manager.schedule(live(), "One: Show", now + 3_600_000, now + 3_700_000)
        assertEquals(RecStatus.SCHEDULED, db.recordings().get(id)!!.status)
        assertEquals(1, alarms.scheduledAlarms.size)
        db.recordings().update(db.recordings().get(id)!!.copy(plannedEnd = now + 1_500))
        manager.startScheduled(id)
        assertEquals(1, manager.active.value.size)
        withTimeout(10_000) { while (manager.active.value.isNotEmpty()) delay(50) }
        val row = db.recordings().get(id)!!
        assertEquals(RecStatus.DONE, row.status)
        assertEquals("One: Show", row.title)
        assertTrue(row.bytes > 0)
        assertTrue(File(row.path).exists())
    }

    @Test
    fun scheduledRecordingIsMissedWithAReason() = runBlocking {
        val now = System.currentTimeMillis()
        db.items().upsert(listOf(live("10"), live("11", "Eleven")))
        limit = 1
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(10_000))))
        val running = manager.start(PlayTarget(server.url("/live/11.ts").toString(), null, "Eleven", true, 1, Kind.LIVE, "11"), 1, null)
        val busy = manager.schedule(live("10"), "x", now, now + 600_000)
        manager.startScheduled(busy)
        assertEquals(RecStatus.MISSED, db.recordings().get(busy)!!.status)
        assertEquals(RecNote.BUDGET, db.recordings().get(busy)!!.note)
        manager.stop(running)
        withTimeout(10_000) { while (manager.active.value.isNotEmpty()) delay(50) }

        val ended = manager.schedule(live("10"), "y", now - 7_200_000, now - 3_600_000)
        manager.startScheduled(ended)
        assertEquals(RecNote.ENDED, db.recordings().get(ended)!!.note)

        val gone = manager.schedule(live("99", "Gone"), "z", now, now + 600_000)
        manager.startScheduled(gone)
        assertEquals(RecNote.CHANNEL, db.recordings().get(gone)!!.note)
    }

    @Test
    fun deletingAScheduledRecordingCancelsItsAlarm() = runBlocking {
        val now = System.currentTimeMillis()
        val id = manager.schedule(live(), "x", now + 3_600_000, now + 7_200_000)
        assertEquals(1, alarms.scheduledAlarms.size)
        manager.delete(id)
        assertEquals(0, alarms.scheduledAlarms.size)
        assertEquals(null, db.recordings().get(id))
    }

    @Test
    fun restartReschedulesFutureAndStartsRunningPrograms() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(20_000)))) }
        db.items().upsert(listOf(live("10"), live("11", "Eleven")))
        val now = System.currentTimeMillis()
        val future = manager.schedule(live("11", "Eleven"), "later", now + 3_600_000, now + 7_200_000)
        val due = manager.schedule(live("10"), "now", now - 60_000, now + 1_500)
        alarms.scheduledAlarms.clear()
        manager.rescheduleAll()
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(RecStatus.SCHEDULED, db.recordings().get(future)!!.status)
        withTimeout(10_000) { while (db.recordings().get(due)!!.status != RecStatus.DONE) delay(50) }
    }

    @Test
    fun conflictsConsiderTheConnectionLimitAndOtherChannels() = runBlocking {
        val now = System.currentTimeMillis()
        manager.schedule(live("10"), "a", now + 1_000, now + 3_600_000)
        limit = 1
        assertTrue(manager.conflicts(live("11", "Eleven"), now + 10_000, now + 20_000))
        assertTrue(!manager.conflicts(live("10"), now + 10_000, now + 20_000))
        assertTrue(!manager.conflicts(live("11", "Eleven"), now + 4_000_000, now + 5_000_000))
        limit = 2
        assertTrue(!manager.conflicts(live("11", "Eleven"), now + 10_000, now + 20_000))
        limit = null
        assertTrue(!manager.conflicts(live("11", "Eleven"), now + 10_000, now + 20_000))
    }
}
