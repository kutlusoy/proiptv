package com.kutlusoy.proiptv

import android.app.Application
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.AccountType
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.parse.Genres
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
class GenreFilterTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private var account = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        account = db.accounts().insert(Account(name = "A", type = AccountType.M3U, url = "http://a"))
        val titles = listOf("Kraken" to "Horror / Action / Thriller", "Madden" to "Drama / Historie", "Alien" to "Horror", "Ohne" to null)
        db.items().upsert(titles.mapIndexed { i, (n, g) -> Item(accountId = account, kind = Kind.MOVIE, remoteId = "$i", name = n, ord = i, genre = Genres.encode(g)) })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun names(genre: String?): List<String> {
        val page = db.items().paging(Kind.MOVIE, listOf(account), null, "", "", "", "", null, null, 0, genre)
            .load(PagingSource.LoadParams.Refresh(null, 50, false)) as PagingSource.LoadResult.Page
        return page.data.map { it.name }
    }

    @Test
    fun aGenreShowsOnlyItsTitles() = runBlocking {
        assertEquals(listOf("Kraken", "Madden", "Alien", "Ohne"), names(null))
        assertEquals(listOf("Kraken", "Alien"), names("Horror"))
        assertEquals(listOf("Madden"), names("Drama"))
        assertEquals(listOf("Kraken"), names("Action"))
        assertEquals(emptyList<String>(), names("Hor"))
    }

    @Test
    fun theGenresAreCounted() = runBlocking {
        val rows = db.items().genreCounts(listOf(account), Kind.MOVIE).first().map { it.genre to it.n }
        assertEquals(listOf("Horror" to 2, "Action" to 1, "Drama" to 1, "Historie" to 1, "Thriller" to 1), Genres.countStored(rows))
    }
}
