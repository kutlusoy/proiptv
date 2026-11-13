package com.kutlusoy.proiptv.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY ord, id")
    fun all(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY ord, id")
    suspend fun list(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): Account?

    @Query("SELECT id FROM accounts WHERE enabled = 1")
    suspend fun enabledIds(): List<Long>

    @Insert
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<Item>)

    @Query("DELETE FROM items WHERE accountId = :accountId AND kind = :kind AND gen <> :gen")
    suspend fun deleteStale(accountId: Long, kind: Int, gen: Int)

    @Query("DELETE FROM items WHERE accountId = :accountId AND kind = :kind")
    suspend fun deleteKind(accountId: Long, kind: Int)

    @Query("DELETE FROM items WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: Long)

    @Query(
        "SELECT * FROM items WHERE kind = :kind AND accountId IN (:ids) AND (:cat IS NULL OR categoryRemoteId = :cat) " +
            "AND name LIKE '%' || :q || '%' ORDER BY accountId, ord",
    )
    fun paging(kind: Int, ids: List<Long>, cat: String?, q: String): PagingSource<Int, Item>

    @Query(
        "SELECT COUNT(*) FROM items WHERE kind = :kind AND accountId IN (:ids) AND (:cat IS NULL OR categoryRemoteId = :cat) " +
            "AND name LIKE '%' || :q || '%'",
    )
    suspend fun count(kind: Int, ids: List<Long>, cat: String?, q: String): Int

    @Query(
        "SELECT * FROM items WHERE kind = :kind AND accountId IN (:ids) AND (:cat IS NULL OR categoryRemoteId = :cat) " +
            "AND name LIKE '%' || :q || '%' ORDER BY accountId, ord LIMIT 1 OFFSET :offset",
    )
    suspend fun at(kind: Int, ids: List<Long>, cat: String?, q: String, offset: Int): Item?

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): Item?

    @Query("SELECT * FROM items WHERE accountId = :accountId AND kind = :kind AND remoteId = :remoteId")
    suspend fun find(accountId: Long, kind: Int, remoteId: String): Item?

    @Query("SELECT COUNT(*) FROM items WHERE accountId = :accountId AND kind = :kind")
    fun countFlow(accountId: Long, kind: Int): Flow<Int>

    @Query("SELECT DISTINCT epgId FROM items WHERE accountId = :accountId AND kind = 0 AND epgId IS NOT NULL AND epgId <> ''")
    suspend fun epgIds(accountId: Long): List<String>

    @Insert
    suspend fun insertCategories(list: List<Category>)

    @Query("DELETE FROM categories WHERE accountId = :accountId AND kind = :kind")
    suspend fun deleteCategories(accountId: Long, kind: Int)

    @Query("DELETE FROM categories WHERE accountId = :accountId")
    suspend fun deleteAccountCategories(accountId: Long)

    @Query("SELECT * FROM categories WHERE accountId = :accountId AND kind = :kind ORDER BY ord")
    fun categories(accountId: Long, kind: Int): Flow<List<Category>>

    @Transaction
    suspend fun replaceCategories(accountId: Long, kind: Int, list: List<Category>) {
        deleteCategories(accountId, kind)
        insertCategories(list)
    }
}

@Dao
interface ProgramDao {
    @Insert
    suspend fun insertAll(list: List<Program>)

    @Query("DELETE FROM programs WHERE accountId = :accountId AND gen <> :gen")
    suspend fun deleteStale(accountId: Long, gen: Int)

    @Query("DELETE FROM programs WHERE accountId = :accountId AND gen = :gen")
    suspend fun deleteGen(accountId: Long, gen: Int)

    @Query("DELETE FROM programs WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: Long)

    @Query("SELECT MAX(`end`) FROM programs WHERE accountId = :accountId")
    suspend fun maxEnd(accountId: Long): Long?

    @Query("SELECT COUNT(*) FROM programs WHERE accountId = :accountId")
    suspend fun count(accountId: Long): Int

    @Query("DELETE FROM programs WHERE accountId = :accountId AND epgId = :epgId AND start < :to AND `end` > :from")
    suspend fun deleteRange(accountId: Long, epgId: String, from: Long, to: Long)

    @Query("SELECT * FROM programs WHERE accountId = :accountId AND epgId = :epgId AND start <= :now AND `end` > :now LIMIT 1")
    suspend fun now(accountId: Long, epgId: String, now: Long): Program?

    @Query("SELECT * FROM programs WHERE accountId = :accountId AND epgId = :epgId AND start >= :from ORDER BY start LIMIT 1")
    suspend fun next(accountId: Long, epgId: String, from: Long): Program?

    @Query("SELECT * FROM programs WHERE accountId = :accountId AND epgId = :epgId AND `end` > :from AND start < :to ORDER BY start")
    suspend fun range(accountId: Long, epgId: String, from: Long, to: Long): List<Program>
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_lists ORDER BY ord, id")
    fun lists(): Flow<List<FavoriteList>>

    @Query("SELECT COUNT(*) FROM favorite_lists")
    suspend fun listCount(): Int

    @Insert
    suspend fun insertList(list: FavoriteList): Long

    @Query("UPDATE favorite_lists SET name = :name WHERE id = :id")
    suspend fun renameList(id: Long, name: String)

    @Query("DELETE FROM favorite_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("DELETE FROM favorites WHERE listId = :id")
    suspend fun deleteListEntries(id: Long)

    @Query(
        "SELECT f.listId AS listId, f.accountId AS accountId, f.kind AS kind, f.remoteId AS remoteId, f.name AS name, " +
            "f.sortKey AS sortKey, i.logo AS logo, i.epgId AS epgId, i.id AS itemId FROM favorites f " +
            "LEFT JOIN items i ON i.accountId = f.accountId AND i.kind = f.kind AND i.remoteId = f.remoteId " +
            "WHERE f.listId = :listId ORDER BY f.sortKey",
    )
    fun rows(listId: Long): Flow<List<FavoriteRow>>

    @Query(
        "SELECT i.* FROM favorites f JOIN items i ON i.accountId = f.accountId AND i.kind = f.kind AND i.remoteId = f.remoteId " +
            "WHERE f.listId = :listId ORDER BY f.sortKey LIMIT 1 OFFSET :offset",
    )
    suspend fun itemAt(listId: Long, offset: Int): Item?

    @Query(
        "SELECT COUNT(*) FROM favorites f JOIN items i ON i.accountId = f.accountId AND i.kind = f.kind AND i.remoteId = f.remoteId " +
            "WHERE f.listId = :listId",
    )
    suspend fun presentCount(listId: Long): Int

    @Query("SELECT DISTINCT accountId, kind, remoteId FROM favorites")
    fun keys(): Flow<List<FavoriteKey>>

    @Query("SELECT listId FROM favorites WHERE accountId = :accountId AND kind = :kind AND remoteId = :remoteId")
    suspend fun listsOf(accountId: Long, kind: Int, remoteId: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE listId = :listId AND accountId = :accountId AND kind = :kind AND remoteId = :remoteId")
    suspend fun remove(listId: Long, accountId: Long, kind: Int, remoteId: String)

    @Query("DELETE FROM favorites WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: Long)

    @Query("SELECT MAX(sortKey) FROM favorites WHERE listId = :listId")
    suspend fun maxKey(listId: Long): Double?

    @Query("SELECT sortKey FROM favorites WHERE listId = :listId ORDER BY sortKey")
    suspend fun sortKeys(listId: Long): List<Double>

    @Query("UPDATE favorites SET sortKey = :key WHERE listId = :listId AND accountId = :accountId AND kind = :kind AND remoteId = :remoteId")
    suspend fun setKey(listId: Long, accountId: Long, kind: Int, remoteId: String, key: Double)

    @Query("SELECT * FROM favorites WHERE listId = :listId ORDER BY sortKey")
    suspend fun entries(listId: Long): List<Favorite>
}

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(progress: Progress)

    @Query("SELECT * FROM progress WHERE accountId = :accountId AND kind = :kind AND remoteId = :remoteId")
    suspend fun get(accountId: Long, kind: Int, remoteId: String): Progress?

    @Query("DELETE FROM progress WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: Long)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY startedAt DESC")
    fun all(): Flow<List<Recording>>

    @Insert
    suspend fun insert(recording: Recording): Long

    @Update
    suspend fun update(recording: Recording)

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun get(id: Long): Recording?

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE recordings SET status = 2 WHERE status = 0")
    suspend fun markInterrupted()

    @Query("DELETE FROM recordings WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: Long)
}
