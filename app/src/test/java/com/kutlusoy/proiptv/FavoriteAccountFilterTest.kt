package com.kutlusoy.proiptv

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.AccountType
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.Repository
import com.kutlusoy.proiptv.data.SecretBox
import com.kutlusoy.proiptv.data.Source
import com.kutlusoy.proiptv.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
class FavoriteAccountFilterTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private var a = 0L
    private var b = 0L
    private var list = 0L

    @Before
    fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val secrets = SecretBox()
        repo = Repository(db, secrets, SyncManager(db, secrets, CoroutineScope(SupervisorJob() + Dispatchers.IO)))
        a = db.accounts().insert(Account(name = "A", type = AccountType.M3U, url = "http://a"))
        b = db.accounts().insert(Account(name = "B", type = AccountType.M3U, url = "http://b"))
        db.items().upsert(listOf(a to "1", b to "2", a to "3", b to "4").mapIndexed { i, (acc, id) -> Item(accountId = acc, kind = Kind.LIVE, remoteId = id, name = "Ch $id", ord = i) })
        list = repo.createList("L")
        for ((acc, id) in listOf(a to "1", b to "2", a to "3", b to "4")) repo.setFavorite(list, acc, Kind.LIVE, id, "Ch $id", true)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aSourceCanBeLimitedToOneAccount() = runBlocking {
        assertEquals(4, repo.count(Source.Favs(list)))
        assertEquals(2, repo.count(Source.Favs(list, listOf(b))))
        assertEquals(listOf("2", "4"), (0..1).map { repo.at(Source.Favs(list, listOf(b)), it)!!.remoteId })
        assertEquals(listOf("2", "4"), db.favorites().channelItemsFor(list, listOf(b)).first().map { it.remoteId })
    }

    @Test
    fun movingInAShortenedListKeepsTheOtherAccountInPlace() = runBlocking {
        // the list of account A shows 1 and 3: 3 goes in front of 1, the favorites of B stay where they are relative to the rest
        repo.moveFavoriteAfter(list, Kind.LIVE, a, "3", null, null)
        assertEquals(listOf("3", "1", "2", "4"), db.favorites().entries(list).map { it.remoteId })
        // 4 goes behind 3
        repo.moveFavoriteAfter(list, Kind.LIVE, b, "4", a, "3")
        assertEquals(listOf("3", "4", "1", "2"), db.favorites().entries(list).map { it.remoteId })
    }
}
