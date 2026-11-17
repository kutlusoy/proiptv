package com.kutlusoy.proiptv

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.AccountType
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.parse.SearchMatch
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
class SearchWordsTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private var account = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        account = db.accounts().insert(Account(name = "A", type = AccountType.M3U, url = "http://a"))
        db.items().upsert(
            listOf("kabel eins", "Eurosport 1 HD", "Tengri FR FRANCE", "Das Erste").mapIndexed { i, n ->
                Item(accountId = account, kind = Kind.LIVE, remoteId = "$i", name = n, ord = i)
            },
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun count(q: String): Int {
        val (w1, w2, w3, w4) = SearchMatch.four(q)
        return db.items().count(Kind.LIVE, listOf(account), null, w1, w2, w3, w4, null, null, 0)
    }

    @Test
    fun everyWordHasToOccurInAnyOrder() = runBlocking {
        assertEquals(1, count("eins kab"))
        assertEquals(1, count("kab eins"))
        assertEquals(1, count("tengri france"))
        assertEquals(1, count("FR tengri"))
        assertEquals(0, count("kab zzz"))
        assertEquals(4, count(""))
        assertEquals(1, count("kabel eins x1".removeSuffix(" x1")))
    }

    @Test
    fun theHomeSearchUsesTheWordsToo() = runBlocking {
        val (w1, w2, w3, w4) = SearchMatch.four("eins kabel")
        assertEquals(listOf("kabel eins"), db.items().search(Kind.LIVE, listOf(account), w1, w2, w3, w4, 40).map { it.name })
    }
}
