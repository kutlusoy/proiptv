package com.kutlusoy.proiptv

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.AccountType
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Favorite
import com.kutlusoy.proiptv.data.FavoriteList
import com.kutlusoy.proiptv.data.FavoriteReconciler
import com.kutlusoy.proiptv.data.HistoryEntry
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
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
class FavoriteReconcilerTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private var account = 0L
    private var list = 0L

    private fun channel(id: String, name: String, epg: String? = null) = Item(accountId = account, kind = Kind.LIVE, remoteId = id, name = name, epgId = epg)

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        account = db.accounts().insert(Account(name = "A", type = AccountType.M3U, url = "http://a"))
        list = db.favorites().insertList(FavoriteList(name = "Fav", kind = Kind.LIVE, builtin = true))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun favorite(id: String, name: String, key: Double) = db.favorites().add(Favorite(list, account, Kind.LIVE, id, name, key))
    private suspend fun ids() = db.favorites().entries(list).map { it.remoteId to it.name }

    @Test
    fun aFavoriteKeepsItsChannelWhileNothingChanged() = runBlocking {
        db.items().upsert(listOf(channel("1", "ORF 1"), channel("2", "ORF 2")))
        favorite("1", "ORF 1", 1.0)
        FavoriteReconciler(db).run(account)
        assertEquals(listOf("1" to "ORF 1"), ids())
    }

    @Test
    fun anIdThatWasGivenToAnotherChannelFollowsTheNameOrGoes() = runBlocking {
        // 1 is another channel now, "ORF 2" has the id 9 now, "RTL" is gone
        db.items().upsert(listOf(channel("1", "Sky Sport"), channel("9", "ORF 2")))
        favorite("1", "ORF 1", 1.0)
        favorite("2", "ORF 2", 2.0)
        favorite("3", "RTL", 3.0)
        FavoriteReconciler(db).run(account)
        assertEquals(listOf("9" to "ORF 2"), ids())
    }

    @Test
    fun anEmptyUpdateDoesNotEmptyTheLists() = runBlocking {
        favorite("1", "ORF 1", 1.0)
        FavoriteReconciler(db).run(account)
        assertEquals(listOf("1" to "ORF 1"), ids())
    }

    @Test
    fun lastChannelsThatAreGoneAreRemoved() = runBlocking {
        db.items().upsert(listOf(channel("1", "ORF 1")))
        db.history().put(HistoryEntry(account, "1", 10))
        db.history().put(HistoryEntry(account, "7", 20))
        favorite("1", "ORF 1", 1.0)
        FavoriteReconciler(db).run(account)
        assertEquals(listOf("1"), db.history().items().map { it.remoteId })
    }

    @Test
    fun theTvgIdFindsTheChannelWhenTheNameChanged() = runBlocking {
        db.items().upsert(listOf(channel("5", "TRT 2", "trt2.tr"), channel("6", "ATV", "atv.tr")))
        favorite("5", "TRT 2", 1.0)
        val before = FavoriteReconciler(db).snapshot(account)
        // after the update the id 5 is ATV, TRT 2 has got a new id and a new name
        db.items().upsert(listOf(channel("5", "ATV", "atv.tr"), channel("8", "TRT 2 HD", "trt2.tr")))
        db.items().deleteStale(account, Kind.LIVE, 0)
        FavoriteReconciler(db).run(account, before)
        assertEquals(listOf("8" to "TRT 2 HD"), ids())
    }

    @Test
    fun aRenamedChannelWithTheSameTvgIdKeepsItsFavorite() = runBlocking {
        db.items().upsert(listOf(channel("5", "STAR TV HD", "startv.tr")))
        favorite("5", "STAR TV HD", 1.0)
        val before = FavoriteReconciler(db).snapshot(account)
        db.items().upsert(listOf(channel("5", "STAR TV FHD", "startv.tr")))
        FavoriteReconciler(db).run(account, before)
        assertEquals(listOf("5" to "STAR TV FHD"), ids())
    }

    @Test
    fun aFavoriteOfAnotherAccountIsNeverMoved() = runBlocking {
        val other = db.accounts().insert(Account(name = "B", type = AccountType.M3U, url = "http://b"))
        db.items().upsert(listOf(channel("1", "ORF 1"), Item(accountId = other, kind = Kind.LIVE, remoteId = "7", name = "RTL")))
        db.favorites().add(Favorite(list, account, Kind.LIVE, "9", "RTL", 1.0))
        FavoriteReconciler(db).run(account)
        assertEquals(emptyList<Pair<String, String>>(), ids())
    }
}
