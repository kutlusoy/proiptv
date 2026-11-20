package com.kutlusoy.proiptv

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.AccountType
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Category
import com.kutlusoy.proiptv.data.HiddenCategory
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
class HiddenCategoryTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private var account = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        account = db.accounts().insert(Account(name = "A", type = AccountType.XTREAM, url = "http://a"))
        db.items().insertCategories(listOf("FR", "TR", "DE").mapIndexed { i, n -> Category(accountId = account, kind = Kind.LIVE, remoteId = n, name = n, ord = i) })
        db.items().upsert(listOf("FR" to "France 2", "TR" to "ATV", "DE" to "ARD", "TR" to "Show TV").mapIndexed { i, (c, n) -> Item(accountId = account, kind = Kind.LIVE, remoteId = "$i", name = n, categoryRemoteId = c, ord = i) })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aHiddenCategoryIsNotOffered() = runBlocking {
        db.items().hide(listOf(HiddenCategory(account, Kind.LIVE, "TR", "TR")))
        assertEquals(listOf("FR", "DE"), db.items().categories(account, Kind.LIVE).first().map { it.remoteId })
        assertEquals(listOf("TR"), db.items().hiddenFlow(account, Kind.LIVE).first().map { it.remoteId })
    }

    @Test
    fun theChannelsOfAHiddenCategoryGo() = runBlocking {
        db.items().deleteItemsIn(account, Kind.LIVE, listOf("TR"))
        db.items().deleteCategoriesIn(account, Kind.LIVE, listOf("TR"))
        assertEquals(setOf("FR", "DE"), db.items().categoryCounts(listOf(account), Kind.LIVE).first().map { it.categoryRemoteId }.toSet())
        assertEquals(listOf("FR", "DE"), db.items().categories(account, Kind.LIVE).first().map { it.remoteId })
    }

    @Test
    fun switchingOnTakesTheEntryAway() = runBlocking {
        db.items().hide(listOf(HiddenCategory(account, Kind.LIVE, "TR", "TR"), HiddenCategory(account, Kind.LIVE, "DE", "DE")))
        db.items().unhide(account, Kind.LIVE, listOf("TR"))
        assertEquals(listOf("DE"), db.items().hiddenIds(account, Kind.LIVE))
    }
}
