package com.kutlusoy.proiptv

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.AccountType
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.Repository
import com.kutlusoy.proiptv.data.SecretBox
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Source
import com.kutlusoy.proiptv.sync.SyncManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncIntegrationTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private lateinit var secrets: SecretBox
    private lateinit var server: MockWebServer
    private var guideTitle = "Now Show"
    private var guideEtag = "\"v1\""
    private var guideStatus = 200
    private val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun xmltv(): String {
        val now = System.currentTimeMillis()
        fun t(offsetMin: Int) = fmt.format(Date(now + offsetMin * 60_000L))
        return """<?xml version="1.0"?><tv>
            <programme start="${t(-30)}" stop="${t(30)}" channel="One.DE"><title>$guideTitle</title></programme>
            <programme start="${t(30)}" stop="${t(90)}" channel="One.DE"><title>Next Show</title></programme>
            <programme start="${t(-300)}" stop="${t(-240)}" channel="ghost"><title>Ignored</title></programme>
            </tv>"""
    }

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        secrets = SecretBox()
        val sync = SyncManager(db, secrets, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        repo = Repository(db, secrets, sync)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/xmltv.php") -> when {
                        guideStatus != 200 -> MockResponse().setResponseCode(guideStatus)
                        request.getHeader("If-None-Match") == guideEtag -> MockResponse().setResponseCode(304)
                        else -> MockResponse().setHeader("ETag", guideEtag).setBody(xmltv())
                    }
                    "action=get_short_epg" in path -> {
                        val now = System.currentTimeMillis() / 1000
                        val title = java.util.Base64.getEncoder().encodeToString("Short Show".toByteArray())
                        MockResponse().setBody("""{"epg_listings":[{"title":"$title","description":"","start_timestamp":"${now - 600}","stop_timestamp":"${now + 1800}"}]}""")
                    }
                    path.startsWith("/playlist.m3u") -> MockResponse().setBody(
                        "#EXTM3U\n#EXTINF:-1 tvg-id=\"m1\" group-title=\"G\",M1\nhttp://s/live/1.ts\n" +
                            "#EXTINF:-1 group-title=\"Film\",F1\nhttp://s/movie/u/p/9.mkv\n",
                    )
                    "action=get_live_categories" in path -> MockResponse().setBody("""[{"category_id":"1","category_name":"News"},{"category_id":"2","category_name":"Sport"}]""")
                    "action=get_live_streams" in path -> MockResponse().setBody(
                        """[{"num":1,"name":"One","stream_id":10,"epg_channel_id":"One.DE","category_id":"1","tv_archive":1,"tv_archive_duration":3},
                           {"num":2,"name":"Two","stream_id":11,"category_id":"2"},
                           {"num":3,"name":"Three","stream_id":12,"category_id":"2"}]""",
                    )
                    "action=get_vod_categories" in path -> MockResponse().setBody("""[{"category_id":"7","category_name":"Action"}]""")
                    "action=get_vod_streams" in path -> MockResponse().setBody("""[{"stream_id":50,"name":"Film A","category_id":"7","container_extension":"mkv"}]""")
                    "action=get_series_categories" in path -> MockResponse().setBody("[]")
                    "action=get_series" in path -> MockResponse().setBody("""[{"series_id":70,"name":"Show","category_id":"0"}]""")
                    path.startsWith("/player_api.php") -> MockResponse().setBody(
                        """{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000","max_connections":"1","active_cons":"0","allowed_output_formats":["m3u8","ts"]}}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun waitForSync(id: Long) = runBlocking {
        withTimeout(30_000) {
            delay(200)
            while (repo.sync.isRunning(id)) delay(100)
        }
    }

    private fun base() = server.url("").toString().trimEnd('/')

    @Test
    fun xtreamAccountSyncsEverything() = runBlocking {
        val id = repo.saveAccount(Account(name = "Test", type = AccountType.XTREAM, url = base() + "/player_api.php", username = "bob"), "secret")
        waitForSync(id)
        val account = db.accounts().get(id)!!
        assertNull(account.lastError, account.lastError)
        assertEquals("Active", account.status)
        assertEquals(1, account.maxConnections)
        assertTrue(account.lastSync > 0)
        assertEquals(3, db.items().count(Kind.LIVE, listOf(id), null, ""))
        assertEquals(1, db.items().count(Kind.MOVIE, listOf(id), null, ""))
        assertEquals(1, db.items().count(Kind.SERIES, listOf(id), null, ""))
        assertEquals(2, db.items().count(Kind.LIVE, listOf(id), "2", ""))
        assertEquals(1, db.items().count(Kind.LIVE, listOf(id), null, "tWo"))
        assertEquals(listOf("News", "Sport"), db.items().categories(id, Kind.LIVE).first().map { it.name })

        val one = db.items().find(id, Kind.LIVE, "10")!!
        assertEquals(3, one.archiveDays)
        val target = repo.target(one)!!
        assertEquals("${base()}/live/bob/secret/10.ts", target.url)
        assertTrue(target.live)
        assertEquals("${base()}/movie/bob/secret/50.mkv", repo.target(db.items().find(id, Kind.MOVIE, "50")!!)!!.url)

        val (now, next) = repo.nowAndNext(id, "ONE.de")
        assertEquals("Now Show", now?.title)
        assertEquals("Next Show", next?.title)
        assertNull(repo.nowAndNext(id, "ghost").first)
    }

    @Test
    fun wrongLoginIsReported() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("""{"user_info":{"auth":0}}""")
        }
        val id = repo.saveAccount(Account(name = "Bad", type = AccountType.XTREAM, url = base(), username = "x"), "y")
        waitForSync(id)
        assertEquals("Login failed", db.accounts().get(id)!!.lastError)
    }

    @Test
    fun resyncKeepsFavoritesAndRemovesGoneChannels() = runBlocking {
        val id = repo.saveAccount(Account(name = "Test", type = AccountType.XTREAM, url = base(), username = "bob"), "secret")
        waitForSync(id)
        val list = repo.createList("L")
        for (remote in listOf("10", "11", "12")) repo.setFavorite(list, id, Kind.LIVE, remote, "n$remote", true)
        repo.moveFavorite(list, 2, 0)
        assertEquals(listOf("12", "10", "11"), db.favorites().entries(list).map { it.remoteId })
        repo.moveFavorite(list, 0, 1)
        assertEquals(listOf("10", "12", "11"), db.favorites().entries(list).map { it.remoteId })

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val p = request.path.orEmpty()
                return when {
                    "action=get_live_streams" in p -> MockResponse().setBody("""[{"name":"One","stream_id":10,"category_id":"1"},{"name":"Three","stream_id":12,"category_id":"2"}]""")
                    "action=" in p -> MockResponse().setBody("[]")
                    else -> MockResponse().setBody("""{"user_info":{"auth":1,"status":"Active"}}""")
                }
            }
        }
        repo.sync.sync(id)
        waitForSync(id)
        assertEquals(2, db.items().count(Kind.LIVE, listOf(id), null, ""))
        val rows = db.favorites().rows(list).first()
        assertEquals(listOf("10", "12", "11"), rows.map { it.remoteId })
        assertNotNull(rows[0].itemId)
        assertNull(rows[2].itemId)
        assertEquals(2, db.favorites().presentCount(list))
        assertEquals("12", repo.at(Source.Favs(list), 1)!!.remoteId)
    }

    @Test
    fun m3uAccountSyncsAndDetectsMovies() = runBlocking {
        val id = repo.saveAccount(Account(name = "M", type = AccountType.M3U, url = base() + "/playlist.m3u"), "")
        waitForSync(id)
        assertNull(db.accounts().get(id)!!.lastError)
        assertEquals(1, db.items().count(Kind.LIVE, listOf(id), null, ""))
        assertEquals(1, db.items().count(Kind.MOVIE, listOf(id), null, ""))
        val live = repo.at(Source.Browse(Kind.LIVE, listOf(id), null, ""), 0)!!
        assertEquals("http://s/live/1.ts", repo.target(live)!!.url)
        assertEquals("m1", live.epgId)
        assertEquals(listOf("G"), db.items().categories(id, Kind.LIVE).first().map { it.name })
    }

    @Test
    fun deletingAnAccountRemovesItsData() = runBlocking {
        val id = repo.saveAccount(Account(name = "Test", type = AccountType.XTREAM, url = base(), username = "bob"), "secret")
        waitForSync(id)
        repo.deleteAccount(id)
        assertEquals(0, db.items().count(Kind.LIVE, listOf(id), null, ""))
        assertNull(db.accounts().get(id))
        assertEquals(0, db.programs().range(id, "one.de", 0, Long.MAX_VALUE).size)
    }

    @Test
    fun guideRefreshIsConditionalAndKeepsPrograms() = runBlocking {
        val id = repo.saveAccount(Account(name = "Test", type = AccountType.XTREAM, url = base(), username = "bob"), "secret")
        waitForSync(id)
        val first = db.accounts().get(id)!!
        assertEquals("\"v1\"", first.epgEtag)
        assertTrue(first.epgSync > 0)
        val before = db.programs().count(id)
        assertTrue(before > 0)
        repo.sync.refreshGuideNow(id)
        val after = db.accounts().get(id)!!
        assertEquals(before, db.programs().count(id))
        assertTrue(after.epgSync >= first.epgSync)
        assertEquals("\"v1\"", after.epgEtag)
        assertEquals("Now Show", repo.nowAndNext(id, "one.de").first?.title)
    }

    @Test
    fun changedGuideReplacesProgramsWithoutDuplicates() = runBlocking {
        val id = repo.saveAccount(Account(name = "Test", type = AccountType.XTREAM, url = base(), username = "bob"), "secret")
        waitForSync(id)
        val before = db.programs().count(id)
        guideTitle = "Changed Show"
        guideEtag = "\"v2\""
        repo.sync.refreshGuideNow(id)
        assertEquals(before, db.programs().count(id))
        assertEquals("Changed Show", repo.nowAndNext(id, "one.de").first?.title)
        assertEquals("\"v2\"", db.accounts().get(id)!!.epgEtag)
    }

    @Test
    fun failedGuideRefreshKeepsOldProgramsAndRecovers() = runBlocking {
        val id = repo.saveAccount(Account(name = "Test", type = AccountType.XTREAM, url = base(), username = "bob"), "secret")
        waitForSync(id)
        val before = db.programs().count(id)
        guideStatus = 500
        repo.sync.refreshGuideNow(id)
        assertEquals(before, db.programs().count(id))
        assertTrue(db.accounts().get(id)!!.lastError!!.startsWith("Guide: "))
        guideStatus = 200
        guideEtag = "\"v3\""
        repo.sync.refreshGuideNow(id)
        assertNull(db.accounts().get(id)!!.lastError)
        assertEquals(before, db.programs().count(id))
    }

    @Test
    fun shortGuideFillsAChannelWithoutXmltvData() = runBlocking {
        guideStatus = 500
        val id = repo.saveAccount(Account(name = "Test", type = AccountType.XTREAM, url = base(), username = "bob"), "secret")
        waitForSync(id)
        val one = db.items().find(id, Kind.LIVE, "10")!!
        assertNull(repo.nowAndNext(id, one.epgId).first)
        assertTrue(repo.fetchShortEpg(one))
        assertEquals("Short Show", repo.nowAndNext(id, one.epgId).first?.title)
    }

    @Test
    fun migrationAddsGuideColumns() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val sqlite = android.database.sqlite.SQLiteDatabase.create(null)
        sqlite.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY, name TEXT)")
        sqlite.execSQL("INSERT INTO accounts (id, name) VALUES (1, 'old')")
        for (sql in AppDatabase.MIGRATION_1_2_SQL) sqlite.execSQL(sql)
        sqlite.execSQL("UPDATE accounts SET epgSync = 5, epgEtag = 'x', epgModified = 'y', autoEpgUrl = 'u' WHERE id = 1")
        sqlite.rawQuery("SELECT epgSync, epgEtag, autoEpgUrl FROM accounts", null).use {
            assertTrue(it.moveToFirst())
            assertEquals(5, it.getInt(0))
            assertEquals("u", it.getString(2))
        }
        sqlite.close()
        assertNotNull(ctx)
    }

    @Test
    fun backupRestoresAccountsFavoritesAndPasswords() = runBlocking {
        val id = repo.saveAccount(Account(name = "Home", type = AccountType.XTREAM, url = base(), username = "bob"), "secret")
        waitForSync(id)
        val list = repo.createList("Sport")
        repo.setFavorite(list, id, Kind.LIVE, "10", "One", true)
        repo.setFavorite(list, id, Kind.LIVE, "12", "Three", true)
        val manager = com.kutlusoy.proiptv.data.BackupManager(repo, { true to 512 }, { _, _ -> }, { secrets.decrypt(it.password) })
        val protectedText = manager.export("pw")
        val plainText = manager.export(null)
        assertTrue(!plainText.contains("secret"))
        repo.deleteAccount(id)
        repo.deleteList(list)
        val result = manager.restore(protectedText, "pw")
        assertEquals(1, result.accounts)
        assertEquals(2, result.favorites)
        val restored = db.accounts().list().single()
        waitForSync(restored.id)
        assertEquals("bob", restored.username)
        assertEquals("secret", secrets.decrypt(restored.password))
        val lists = db.favorites().lists().first()
        assertEquals(listOf("Sport"), lists.map { it.name })
        assertEquals(listOf("10", "12"), db.favorites().entries(lists[0].id).map { it.remoteId })
        val again = manager.restore(protectedText, "pw")
        assertEquals(0, again.accounts)
        assertEquals(0, again.favorites)
        assertEquals(1, db.accounts().list().size)
    }

    @Test
    fun alternativesFindTheSameChannelInOtherEnabledAccounts() = runBlocking {
        val a1 = db.accounts().insert(Account(name = "A", type = AccountType.M3U, url = "u1"))
        val a2 = db.accounts().insert(Account(name = "B", type = AccountType.M3U, url = "u2"))
        val a3 = db.accounts().insert(Account(name = "C", type = AccountType.M3U, url = "u3", enabled = false))
        db.items().upsert(
            listOf(
                Item(accountId = a1, kind = Kind.LIVE, remoteId = "1", name = "One", epgId = "one.de"),
                Item(accountId = a2, kind = Kind.LIVE, remoteId = "1", name = "Eins HD", epgId = "one.de"),
                Item(accountId = a1, kind = Kind.LIVE, remoteId = "2", name = "ONE"),
                Item(accountId = a3, kind = Kind.LIVE, remoteId = "1", name = "One", epgId = "one.de"),
                Item(accountId = a2, kind = Kind.LIVE, remoteId = "9", name = "Unrelated", epgId = "other"),
            ),
        )
        val original = db.items().find(a1, Kind.LIVE, "1")!!
        val alternatives = repo.alternatives(original)
        assertEquals(listOf("2", "1"), alternatives.map { it.remoteId })
        assertEquals(listOf(a1, a2), alternatives.map { it.accountId })
    }

    @Test
    fun migrationToVersion4CreatesTheSameColumnsAsRoom() {
        fun describe(c: android.database.Cursor): List<String> = c.use {
            val rows = mutableListOf<String>()
            while (it.moveToNext()) rows += "${it.getString(1)}|${it.getString(2)}|${it.getInt(3)}|${it.getInt(5)}"
            rows
        }
        val fromRoom = describe(db.openHelper.writableDatabase.query("PRAGMA table_info(recordings)"))
        val migrated = android.database.sqlite.SQLiteDatabase.create(null)
        for (sql in AppDatabase.MIGRATION_2_3_SQL) migrated.execSQL(sql)
        migrated.execSQL("INSERT INTO recordings (accountId, title, startedAt, path, bytes, status) VALUES (1, 't', 1, 'p', 0, 1)")
        for (sql in AppDatabase.MIGRATION_3_4_SQL) migrated.execSQL(sql)
        val fromMigration = describe(migrated.rawQuery("PRAGMA table_info(recordings)", null))
        migrated.rawQuery("SELECT channelRemoteId, plannedStart, note FROM recordings", null).use {
            assertTrue(it.moveToFirst())
            assertEquals("", it.getString(0))
            assertTrue(it.isNull(1))
        }
        migrated.close()
        assertEquals(fromRoom.sorted(), fromMigration.sorted())
    }
}
