package com.kutlusoy.proiptv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Account::class, Category::class, Item::class, Program::class, FavoriteList::class, Favorite::class, Progress::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun items(): ItemDao
    abstract fun programs(): ProgramDao
    abstract fun favorites(): FavoriteDao
    abstract fun progress(): ProgressDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (sql in MIGRATION_1_2_SQL) db.execSQL(sql)
            }
        }

        val MIGRATION_1_2_SQL = listOf(
            "ALTER TABLE accounts ADD COLUMN epgSync INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE accounts ADD COLUMN epgEtag TEXT",
            "ALTER TABLE accounts ADD COLUMN epgModified TEXT",
            "ALTER TABLE accounts ADD COLUMN autoEpgUrl TEXT",
        )

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "proiptv.db").addMigrations(MIGRATION_1_2).build()
    }
}
