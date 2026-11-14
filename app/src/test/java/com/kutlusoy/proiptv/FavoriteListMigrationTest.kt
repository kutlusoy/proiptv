package com.kutlusoy.proiptv

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoriteListMigrationTest {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE favorite_lists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, ord INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE favorites (listId INTEGER NOT NULL, accountId INTEGER NOT NULL, kind INTEGER NOT NULL, remoteId TEXT NOT NULL, name TEXT NOT NULL, sortKey REAL NOT NULL, PRIMARY KEY(listId, accountId, kind, remoteId))")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        db = helper.writableDatabase
    }

    @After
    fun tearDown() = helper.close()

    private fun count(sql: String): Int = db.query(sql).use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun movieAndSeriesFavoritesMoveToTheirOwnLists() {
        db.execSQL("INSERT INTO favorite_lists (id, name, ord) VALUES (1, 'Favoriten', 0), (2, 'News', 1)")
        db.execSQL(
            "INSERT INTO favorites VALUES (1, 1, 0, 'ch1', 'Channel', 1.0), (1, 1, 1, 'm1', 'Movie', 2.0), (1, 1, 1, 'm2', 'Movie 2', 3.0), " +
                "(1, 1, 2, 's1', 'Series', 4.0), (2, 1, 0, 'ch2', 'News channel', 1.0)",
        )
        AppDatabase.MIGRATION_7_8.migrate(db)

        assertEquals(1, count("SELECT COUNT(*) FROM favorite_lists WHERE name = 'News'"))
        assertEquals(1, count("SELECT COUNT(*) FROM favorite_lists WHERE name = 'Favoriten' AND kind = 0"))
        assertEquals(1, count("SELECT COUNT(*) FROM favorite_lists WHERE name = 'Favoriten' AND kind = 1"))
        assertEquals(1, count("SELECT COUNT(*) FROM favorite_lists WHERE name = 'Favoriten' AND kind = 2"))
        // every favorite sits in a list of its own area, nothing got lost
        assertEquals(5, count("SELECT COUNT(*) FROM favorites"))
        assertEquals(0, count("SELECT COUNT(*) FROM favorites f JOIN favorite_lists l ON l.id = f.listId WHERE f.kind <> l.kind"))
        assertEquals(2, count("SELECT COUNT(*) FROM favorites f JOIN favorite_lists l ON l.id = f.listId WHERE f.kind = 1 AND l.kind = 1"))
    }
}
