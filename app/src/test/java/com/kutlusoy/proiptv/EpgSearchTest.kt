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
        assertEquals(1, db.items().count(Kind.LIVE, ids, null, "Tagesschau", null, null, 0))
        assertEquals("Erstes", db.items().at(Kind.LIVE, ids, null, "Tagesschau", null, null, 0, 0)?.name)
        assertEquals(1, db.items().count(Kind.LIVE, ids, null, "Zweites", null, null, 0))
        assertEquals(0, db.items().count(Kind.LIVE, ids, null, "Fußball", null, null, 0))
        assertEquals(3, db.items().count(Kind.LIVE, ids, null, "", null, null, 0))
    }

    @Test
    fun quickFiltersKeepOnlyHdCatchupAndFavoriteChannels() = runBlocking {
        val ids = listOf(account)
        db.items().upsert(
            listOf(
                Item(accountId = account, kind = Kind.LIVE, remoteId = "4", name = "Kanal HD", ord = 3, categoryRemoteId = "n", archiveDays = 3),
                Item(accountId = account, kind = Kind.LIVE, remoteId = "5", name = "Kanal UHD", ord = 4, categoryRemoteId = "n"),
            ),
        )
        val list = db.favorites().insertList(com.kutlusoy.proiptv.data.FavoriteList(name = "F", kind = Kind.LIVE))
        db.favorites().add(com.kutlusoy.proiptv.data.Favorite(list, account, Kind.LIVE, "2", "Zweites", 1.0))
        assertEquals(2, db.items().count(Kind.LIVE, ids, null, "", null, null, 1))
        assertEquals(1, db.items().count(Kind.LIVE, ids, null, "", null, null, 2))
        assertEquals(1, db.items().count(Kind.LIVE, ids, null, "", null, null, 4))
        assertEquals(0, db.items().count(Kind.LIVE, ids, null, "", null, null, 1 or 4))
        assertEquals(5, db.items().count(Kind.LIVE, ids, null, "", null, null, 0))
    }

    @Test
    fun theListCanBeSortedFromAToZAndFromZToA() = runBlocking {
        val ids = listOf(account)
        suspend fun names(flags: Int): List<String> {
            val source = db.items().paging(Kind.LIVE, ids, null, "", null, null, flags)
            val page = source.load(androidx.paging.PagingSource.LoadParams.Refresh(null, 50, false)) as androidx.paging.PagingSource.LoadResult.Page
            return page.data.map { it.name }
        }
        assertEquals(listOf("Erstes", "Zweites", "Sport 1"), names(0))
        assertEquals(listOf("Erstes", "Sport 1", "Zweites"), names(8))
        assertEquals(listOf("Zweites", "Sport 1", "Erstes"), names(16))
    }

    @Test
    fun categoriesAreCounted() = runBlocking {
        val counts = db.items().categoryCounts(listOf(account), Kind.LIVE).first()
        assertEquals(2, counts.first { it.categoryRemoteId == "n" }.n)
        assertEquals(1, counts.first { it.categoryRemoteId == "s" }.n)
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
