package com.kutlusoy.proiptv

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.DlStatus
import com.kutlusoy.proiptv.data.Download
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.download.DownloadManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadManagerTest {
    private lateinit var db: AppDatabase
    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var manager: DownloadManager
    private val payload = ByteArray(300_000) { (it % 251).toByte() }
    private var status = 200
    private val ranges = mutableListOf<String?>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dir = File(context.cacheDir, "dl-test").apply { deleteRecursively(); mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                ranges += request.getHeader("Range")
                if (status != 200) return MockResponse().setResponseCode(status)
                val range = request.getHeader("Range")?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull()
                return if (range != null) {
                    MockResponse().setResponseCode(206).setBody(Buffer().write(payload, range, payload.size - range))
                } else {
                    MockResponse().setBody(Buffer().write(payload))
                }
            }
        }
        server.start()
        manager = DownloadManager(null, db, scope, OkHttpClient(), dir) { server.url("/movie.mp4").toString() to null }
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
        db.close()
    }

    private fun movie(id: String = "42") = Download(accountId = 1, kind = Kind.MOVIE, remoteId = id, title = "Movie $id", ext = "mp4")

    private fun waitDone() = runBlocking { withTimeout(10_000) { while (db.downloads().pending() > 0) delay(50) } }

    @Test
    fun downloadsAMovieToTheDevice() {
        manager.enqueue(listOf(movie()))
        Thread.sleep(200)
        waitDone()
        val d = runBlocking { db.downloads().nextQueued() }
        assertNull(d)
        val row = runBlocking { db.downloads().all().first() }.single()
        assertEquals(DlStatus.DONE, row.status)
        assertEquals(payload.size.toLong(), row.bytes)
        assertTrue(manager.fileOf(row).readBytes().contentEquals(payload))
    }

    @Test
    fun resumesAPartialFile() {
        val id = runBlocking { db.downloads().insert(movie("7").copy(status = DlStatus.QUEUED)) }
        val partial = File(dir, "$id.mp4")
        partial.writeBytes(payload.copyOf(100_000))
        manager.start()
        Thread.sleep(200)
        waitDone()
        assertEquals("bytes=100000-", ranges.first())
        assertTrue(partial.readBytes().contentEquals(payload))
    }

    @Test
    fun failedDownloadsCanBeRetried() {
        status = 404
        manager.enqueue(listOf(movie()))
        Thread.sleep(200)
        waitDone()
        val row = runBlocking { db.downloads().all().first() }.single()
        assertEquals(DlStatus.FAILED, row.status)
        status = 200
        manager.retry(row.id)
        Thread.sleep(200)
        waitDone()
        assertEquals(DlStatus.DONE, runBlocking { db.downloads().all().first() }.single().status)
    }

    @Test
    fun deleteRemovesTheFileAndTheRow() {
        manager.enqueue(listOf(movie()))
        Thread.sleep(200)
        waitDone()
        val row = runBlocking { db.downloads().all().first() }.single()
        manager.delete(row.id)
        Thread.sleep(300)
        assertFalse(manager.fileOf(row).exists())
        assertTrue(runBlocking { db.downloads().all().first() }.isEmpty())
    }

    @Test
    fun sameItemIsOnlyQueuedOnce() {
        manager.enqueue(listOf(movie(), movie()))
        Thread.sleep(200)
        waitDone()
        assertEquals(1, runBlocking { db.downloads().all().first() }.size)
    }
}
