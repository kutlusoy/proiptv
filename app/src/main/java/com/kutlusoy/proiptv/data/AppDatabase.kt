package com.kutlusoy.proiptv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Account::class, Category::class, Item::class, Program::class, FavoriteList::class, Favorite::class, Progress::class, Recording::class],
    version = 4,
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
            Room.databaseBuilder(context, AppDatabase::class.java, "proiptv.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
