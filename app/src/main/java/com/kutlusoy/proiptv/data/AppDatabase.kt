package com.kutlusoy.proiptv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.kutlusoy.proiptv.parse.TitleParser
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Account::class, Category::class, Item::class, Program::class, FavoriteList::class, Favorite::class, Progress::class, Recording::class],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun items(): ItemDao
    abstract fun programs(): ProgramDao
    abstract fun favorites(): FavoriteDao
    abstract fun progress(): ProgressDao
    abstract fun recordings(): RecordingDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (sql in MIGRATION_1_2_SQL) db.execSQL(sql)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (sql in MIGRATION_2_3_SQL) db.execSQL(sql)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (sql in MIGRATION_5_6_SQL) db.execSQL(sql)
                db.query(
                    "SELECT i.id, i.name, COALESCE(c.name, '') FROM items i LEFT JOIN categories c " +
                        "ON c.accountId = i.accountId AND c.kind = i.kind AND c.remoteId = i.categoryRemoteId",
                ).use { cur ->
                    val update = db.compileStatement("UPDATE items SET country = ?, year = ? WHERE id = ?")
                    while (cur.moveToNext()) {
                        val info = TitleParser.parse(cur.getString(1), cur.getString(2))
                        if (info.country.isEmpty() && info.year == 0) continue
                        update.bindString(1, info.country)
                        update.bindLong(2, info.year.toLong())
                        update.bindLong(3, cur.getLong(0))
                        update.executeUpdateDelete()
                    }
                }
            }
        }

        val MIGRATION_5_6_SQL = listOf(
            "ALTER TABLE items ADD COLUMN country TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE items ADD COLUMN year INTEGER NOT NULL DEFAULT 0",
        )

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (sql in MIGRATION_4_5_SQL) db.execSQL(sql)
            }
        }

        val MIGRATION_4_5_SQL = listOf(
            "ALTER TABLE progress ADD COLUMN watched INTEGER NOT NULL DEFAULT 0",
        )

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (sql in MIGRATION_3_4_SQL) db.execSQL(sql)
            }
        }

        val MIGRATION_3_4_SQL = listOf(
            "ALTER TABLE recordings ADD COLUMN plannedStart INTEGER",
            "ALTER TABLE recordings ADD COLUMN channelRemoteId TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE recordings ADD COLUMN note TEXT",
        )

        val MIGRATION_2_3_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `recordings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` INTEGER NOT NULL, `title` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `plannedEnd` INTEGER, `path` TEXT NOT NULL, `bytes` INTEGER NOT NULL, `status` INTEGER NOT NULL)",
        )

        val MIGRATION_1_2_SQL = listOf(
            "ALTER TABLE accounts ADD COLUMN epgSync INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE accounts ADD COLUMN epgEtag TEXT",
            "ALTER TABLE accounts ADD COLUMN epgModified TEXT",
            "ALTER TABLE accounts ADD COLUMN autoEpgUrl TEXT",
        )

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "proiptv.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
    }
}
