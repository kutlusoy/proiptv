package com.kutlusoy.proiptv

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.AccountType
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.Program
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgSearchTest {
    private lateinit var db: AppDatabase
    private var account = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        account = db.accounts().insert(Account(name = "A", type = AccountType.XTREAM, url = "http://x", username = "u"))
        db.items().upsert(
            listOf(
                Item(accountId = account, kind = Kind.LIVE, remoteId = "1", name = "Erstes", epgId = "one", ord = 0, categoryRemoteId = "n"),
                Item(accountId = account, kind = Kind.LIVE, remoteId = "2", name = "Zweites", epgId = "two", ord = 1, categoryRemoteId = "n"),
                Item(accountId = account, kind = Kind.LIVE, remoteId = "3", name = "Sport 1", epgId = "three", ord = 2, categoryRemoteId = "s"),
            ),
        )
        val now = System.currentTimeMillis() / 1000
        db.programs().insertAll(
            listOf(
                Program(accountId = account, epgId = "one", start = now - 600, end = now + 1800, title = "Tagesschau", description = null, gen = 1),
                Program(accountId = account, epgId = "two", start = now - 600, end = now + 1800, title = "Krimi am Abend", description = null, gen = 1),
                Program(accountId = account, epgId = "two", start = now - 7200, end = now - 600, title = "Tagesschau", description = null, gen = 1),
                Program(accountId = account, epgId = "three", start = now + 3600, end = now + 7200, title = "Fußball", description = null, gen = 1),
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun searchFindsRunningShowsButNotFinishedOnes() = runBlocking {
        val ids = listOf(account)
        assertEquals(1, db.items().count(Kind.LIVE, ids, null, "Tagesschau", null, null))
        assertEquals("Erstes", db.items().at(Kind.LIVE, ids, null, "Tagesschau", null, null, 0)?.name)
        assertEquals(1, db.items().count(Kind.LIVE, ids, null, "Zweites", null, null))
        assertEquals(0, db.items().count(Kind.LIVE, ids, null, "Fußball", null, null))
        assertEquals(3, db.items().count(Kind.LIVE, ids, null, "", null, null))
    }

    @Test
    fun guideFiltersByTimeCategoryAndText() = runBlocking {
        val now = System.currentTimeMillis() / 1000
        val ids = listOf(account)
        assertEquals(2, db.programs().guide(ids, null, now, now + 1, "").first().size)
        assertEquals(3, db.programs().guide(ids, null, now, now + 4 * 3600, "").first().size)
        assertEquals(1, db.programs().guide(ids, "s", now, now + 4 * 3600, "").first().size)
        assertEquals(listOf("Krimi am Abend"), db.programs().guide(ids, null, now, now + 1, "krimi").first().map { it.title })
        assertEquals(1, db.programs().guide(ids, null, now, now + 1, "Zweites").first().size)
    }

    @Test
    fun positionTellsWhereTheChannelIsInTheList() = runBlocking {
        val ids = listOf(account)
        assertEquals(2, db.items().livePosition(ids, null, account, 2))
        assertEquals(0, db.items().livePosition(ids, "s", account, 2))
    }
}
