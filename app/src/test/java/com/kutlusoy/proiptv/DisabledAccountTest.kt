package com.kutlusoy.proiptv

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.AccountType
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Favorite
import com.kutlusoy.proiptv.data.FavoriteList
import com.kutlusoy.proiptv.data.HistoryEntry
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.Progress
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
class DisabledAccountTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private var a = 0L
    private var b = 0L
    private var list = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        a = db.accounts().insert(Account(name = "A", type = AccountType.M3U, url = "http://a"))
        b = db.accounts().insert(Account(name = "B", type = AccountType.M3U, url = "http://b"))
        list = db.favorites().insertList(FavoriteList(name = "Fav", kind = Kind.LIVE, builtin = true))
        for ((acc, id) in listOf(a to "1", b to "2")) {
            db.items().upsert(listOf(Item(accountId = acc, kind = Kind.LIVE, remoteId = id, name = "Channel $id")))
            db.favorites().add(Favorite(list, acc, Kind.LIVE, id, "Channel $id", acc.toDouble()))
            db.history().put(HistoryEntry(acc, id, 1000L + acc))
        }
        for ((acc, id) in listOf(a to "10", b to "20")) {
            db.progress().save(Progress(acc, Kind.MOVIE, id, 60_000, 600_000, 1000L))
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun favoritesHistoryAndContinueHideADisabledAccount() = runBlocking {
        assertEquals(2, db.favorites().rows(list, Kind.LIVE).first().size)
        assertEquals(2, db.favorites().presentCount(list))
        assertEquals(2, db.history().items().size)
        assertEquals(2, db.progress().continueWatching().first().size)

        db.accounts().update(db.accounts().get(b)!!.copy(enabled = false))

        assertEquals(listOf("1"), db.favorites().rows(list, Kind.LIVE).first().map { it.remoteId })
        assertEquals(1, db.favorites().presentCount(list))
        assertEquals(listOf("Channel 1"), db.favorites().channelItems(list).first().map { it.name })
        assertEquals("Channel 1", db.favorites().itemAt(list, 0)?.name)
        assertEquals(null, db.favorites().itemAt(list, 1))
        assertEquals(listOf("1"), db.history().items().map { it.remoteId })
        assertEquals(listOf("1"), db.history().rows().first().map { it.item.remoteId })
        assertEquals(listOf(a), db.progress().continueWatching().first().map { it.accountId })

        db.accounts().update(db.accounts().get(b)!!.copy(enabled = true))
        assertEquals(2, db.favorites().rows(list, Kind.LIVE).first().size)
    }
}
