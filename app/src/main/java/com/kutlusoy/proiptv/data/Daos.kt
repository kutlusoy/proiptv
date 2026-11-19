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

private const val NOW_SECS = "CAST(strftime('%s','now') AS INTEGER)"
/**
 * The search of channels, movies and series: every one of up to four words (:w1 to :w4, empty ones match everything) has to occur in the name or
 * in the title of the program that runs now. The order of the words does not matter.
 */
private const val WORD_1 =
    "(:w1 = '' OR name LIKE '%' || :w1 || '%' OR (epgId IS NOT NULL AND EXISTS (SELECT 1 FROM programs p " +
        "WHERE p.accountId = items.accountId AND p.epgId = items.epgId AND p.start <= " + NOW_SECS + " AND p.`end` > " + NOW_SECS +
        " AND p.title LIKE '%' || :w1 || '%')))"

private const val WORD_2 =
    "(:w2 = '' OR name LIKE '%' || :w2 || '%' OR (epgId IS NOT NULL AND EXISTS (SELECT 1 FROM programs p " +
        "WHERE p.accountId = items.accountId AND p.epgId = items.epgId AND p.start <= " + NOW_SECS + " AND p.`end` > " + NOW_SECS +
        " AND p.title LIKE '%' || :w2 || '%')))"

private const val WORD_3 =
    "(:w3 = '' OR name LIKE '%' || :w3 || '%' OR (epgId IS NOT NULL AND EXISTS (SELECT 1 FROM programs p " +
        "WHERE p.accountId = items.accountId AND p.epgId = items.epgId AND p.start <= " + NOW_SECS + " AND p.`end` > " + NOW_SECS +
        " AND p.title LIKE '%' || :w3 || '%')))"

private const val WORD_4 =
    "(:w4 = '' OR name LIKE '%' || :w4 || '%' OR (epgId IS NOT NULL AND EXISTS (SELECT 1 FROM programs p " +
        "WHERE p.accountId = items.accountId AND p.epgId = items.epgId AND p.start <= " + NOW_SECS + " AND p.`end` > " + NOW_SECS +
        " AND p.title LIKE '%' || :w4 || '%')))"

private const val NAME_OR_NOW_PLAYING = "(" + WORD_1 + " AND " + WORD_2 + " AND " + WORD_3 + " AND " + WORD_4 + ")"

/** The same for the guide: every word in the title of the program or the name of the channel. */
private const val GUIDE_WORDS =
    "(:w1 = '' OR p.title LIKE '%' || :w1 || '%' OR i.name LIKE '%' || :w1 || '%') AND (:w2 = '' OR p.title LIKE '%' || :w2 || '%' OR i.name LIKE '%' || :w2 || '%') AND " +
        "(:w3 = '' OR p.title LIKE '%' || :w3 || '%' OR i.name LIKE '%' || :w3 || '%') AND (:w4 = '' OR p.title LIKE '%' || :w4 || '%' OR i.name LIKE '%' || :w4 || '%')"

/** How many titles a category has, for the list column of the TV layout. */
data class CategoryCount(val accountId: Long, val categoryRemoteId: String, val n: Int)

/** How many titles an account has, and how many entries a favorites list has (only titles that still exist), for the numbers in the selection menus. */
data class KnownGenre(val remoteId: String, val genre: String)
data class GenreCount(val genre: String, val n: Int)
data class AccountCount(val accountId: Long, val n: Int)
data class ListCount(val listId: Long, val n: Int)

/** Quick filters of the channel list: bit 1 HD, bit 2 catch-up available, bit 4 only favorites. */
private const val QUICK_FILTERS =
    " AND (:flags & 1 = 0 OR name LIKE '%HD%' OR name LIKE '%4K%') AND (:flags & 2 = 0 OR archiveDays > 0) " +
        "AND (:flags & 4 = 0 OR EXISTS (SELECT 1 FROM favorites f WHERE f.accountId = items.accountId AND f.kind = items.kind AND f.remoteId = items.remoteId))"

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<Item>)

    /** The genres found so far (the marker "|" means: asked, the movie has none), so a new load of the list does not lose them. */
    @Query("SELECT remoteId, genre FROM items WHERE accountId = :accountId AND kind = :kind AND genre <> ''")
    suspend fun knownGenres(accountId: Long, kind: Int): List<KnownGenre>

    @Query("SELECT remoteId FROM items WHERE accountId = :accountId AND kind = :kind AND genre = '' ORDER BY ord")
    suspend fun withoutGenre(accountId: Long, kind: Int): List<String>

    @Query("UPDATE items SET genre = :genre WHERE accountId = :accountId AND kind = :kind AND remoteId = :remoteId")
    suspend fun setGenre(accountId: Long, kind: Int, remoteId: String, genre: String)

    @Query("DELETE FROM items WHERE accountId = :accountId AND kind = :kind AND gen <> :gen")
    suspend fun deleteStale(accountId: Long, kind: Int, gen: Int)

    @Query("DELETE FROM items WHERE accountId = :accountId AND kind = :kind")
    suspend fun deleteKind(accountId: Long, kind: Int)

    @Query("DELETE FROM items WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: Long)

    @Query(
        "SELECT * FROM items WHERE kind = :kind AND accountId IN (:ids) AND (:cat IS NULL OR categoryRemoteId = :cat) " +
            "AND (:country IS NULL OR country = :country) AND (:year IS NULL OR year = :year) " +
            "AND (:genre IS NULL OR genre LIKE '%|' || :genre || '|%') " +
            "AND " + NAME_OR_NOW_PLAYING + QUICK_FILTERS + " ORDER BY CASE WHEN :flags & 8 <> 0 THEN name END COLLATE NOCASE ASC, CASE WHEN :flags & 16 <> 0 THEN name END COLLATE NOCASE DESC, accountId, ord",
    )
    fun paging(kind: Int, ids: List<Long>, cat: String?, w1: String, w2: String, w3: String, w4: String, country: String?, year: Int?, flags: Int, genre: String?): PagingSource<Int, Item>

    @Query(
        "SELECT COUNT(*) FROM items WHERE kind = :kind AND accountId IN (:ids) AND (:cat IS NULL OR categoryRemoteId = :cat) " +
            "AND (:country IS NULL OR country = :country) AND (:year IS NULL OR year = :year) " +
            "AND " + NAME_OR_NOW_PLAYING + QUICK_FILTERS,
    )
    suspend fun count(kind: Int, ids: List<Long>, cat: String?, w1: String, w2: String, w3: String, w4: String, country: String?, year: Int?, flags: Int): Int

    @Query(
        "SELECT * FROM items WHERE kind = :kind AND accountId IN (:ids) AND (:cat IS NULL OR categoryRemoteId = :cat) " +
            "AND (:country IS NULL OR country = :country) AND (:year IS NULL OR year = :year) " +
            "AND " + NAME_OR_NOW_PLAYING + QUICK_FILTERS + " ORDER BY accountId, ord LIMIT 1 OFFSET :offset",
    )
    suspend fun at(kind: Int, ids: List<Long>, cat: String?, w1: String, w2: String, w3: String, w4: String, country: String?, year: Int?, flags: Int, offset: Int): Item?

    @Query(
        "SELECT COUNT(*) FROM items WHERE kind = 0 AND accountId IN (:ids) AND (:cat IS NULL OR categoryRemoteId = :cat) " +
            "AND (accountId < :accountId OR (accountId = :accountId AND ord < :ord))",
    )
    suspend fun livePosition(ids: List<Long>, cat: String?, accountId: Long, ord: Int): Int

    @Query("SELECT * FROM items WHERE kind = :kind AND accountId = :accountId AND categoryRemoteId = :cat ORDER BY ord LIMIT :limit")
    suspend fun first(kind: Int, accountId: Long, cat: String, limit: Int): List<Item>

    @Query("SELECT * FROM items WHERE kind = :kind AND accountId IN (:ids) ORDER BY accountId, ord LIMIT :limit")
    suspend fun top(kind: Int, ids: List<Long>, limit: Int): List<Item>

    @Query("SELECT * FROM items WHERE kind = :kind AND accountId IN (:ids) AND (:w1 = '' OR name LIKE '%' || :w1 || '%') AND (:w2 = '' OR name LIKE '%' || :w2 || '%') AND (:w3 = '' OR name LIKE '%' || :w3 || '%') AND (:w4 = '' OR name LIKE '%' || :w4 || '%') ORDER BY accountId, ord LIMIT :limit")
    suspend fun search(kind: Int, ids: List<Long>, w1: String, w2: String, w3: String, w4: String, limit: Int): List<Item>

    @Query("SELECT accountId, categoryRemoteId, COUNT(*) AS n FROM items WHERE kind = :kind AND accountId IN (:ids) GROUP BY accountId, categoryRemoteId")
    fun categoryCounts(ids: List<Long>, kind: Int): Flow<List<CategoryCount>>

    @Query("SELECT genre, COUNT(*) AS n FROM items WHERE kind = :kind AND accountId IN (:ids) AND genre <> '' GROUP BY genre")
    fun genreCounts(ids: List<Long>, kind: Int): Flow<List<GenreCount>>

    @Query("SELECT accountId, COUNT(*) AS n FROM items WHERE kind = :kind AND accountId IN (:ids) GROUP BY accountId")
    fun accountCounts(ids: List<Long>, kind: Int): Flow<List<AccountCount>>

    @Query("SELECT * FROM items WHERE kind = 0 AND accountId IN (:ids) AND (:cat IS NULL OR categoryRemoteId = :cat) ORDER BY accountId, ord LIMIT 400")
    fun liveChannels(ids: List<Long>, cat: String?): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): Item?

    @Query(
        "SELECT * FROM items WHERE kind = 0 AND id <> :id AND accountId IN (SELECT id FROM accounts WHERE enabled = 1) " +
            "AND ((:epg <> '' AND epgId = :epg) OR (:name <> '' AND lower(name) = :name)) " +
            "ORDER BY (accountId = :accountId) DESC, ord LIMIT 8",
    )
    suspend fun alternatives(id: Long, accountId: Long, epg: String, name: String): List<Item>

    @Query("SELECT * FROM items WHERE accountId = :accountId AND kind = :kind AND remoteId = :remoteId")
    suspend fun find(accountId: Long, kind: Int, remoteId: String): Item?

    @Query("SELECT * FROM items WHERE accountId = :accountId AND kind = :kind")
    suspend fun ofKind(accountId: Long, kind: Int): List<Item>

    @Query("SELECT COUNT(*) FROM items WHERE accountId = :accountId AND kind = :kind")
    suspend fun countOf(accountId: Long, kind: Int): Int

    @Query("SELECT COUNT(*) FROM items WHERE accountId = :accountId AND kind = :kind")
    fun countFlow(accountId: Long, kind: Int): Flow<Int>

    @Query("SELECT DISTINCT epgId FROM items WHERE accountId = :accountId AND kind = 0 AND epgId IS NOT NULL AND epgId <> ''")
    suspend fun epgIds(accountId: Long): List<String>

    @Insert
    suspend fun insertCategories(list: List<Category>)

    @Query("DELETE FROM categories WHERE accountId = :accountId AND kind = :kind AND remoteId NOT IN (SELECT DISTINCT categoryRemoteId FROM items WHERE accountId = :accountId AND kind = :kind)")
    suspend fun deleteEmptyCategories(accountId: Long, kind: Int)

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

    @Query(
        "SELECT p.title AS title, p.start AS start, p.`end` AS `end`, i.id AS itemId, i.name AS channel, i.logo AS logo, " +
            "i.accountId AS accountId, i.ord AS ord, i.categoryRemoteId AS category FROM programs p " +
            "JOIN items i ON i.accountId = p.accountId AND i.epgId = p.epgId AND i.kind = 0 " +
            "WHERE i.accountId IN (:ids) AND (:cat IS NULL OR i.categoryRemoteId = :cat) AND p.`end` > :from AND p.start < :to " +
            "AND " + GUIDE_WORDS + " " +
            "ORDER BY p.start, i.accountId, i.ord LIMIT 400",
    )
    fun guide(ids: List<Long>, cat: String?, from: Long, to: Long, w1: String, w2: String, w3: String, w4: String): Flow<List<GuideRow>>

    @Query(
        "SELECT i.* FROM items i WHERE i.kind = 0 AND i.accountId IN (:ids) AND (:cat IS NULL OR i.categoryRemoteId = :cat) " +
            "AND i.epgId IS NOT NULL AND i.epgId <> '' AND EXISTS (SELECT 1 FROM programs p WHERE p.accountId = i.accountId AND p.epgId = i.epgId " +
            "AND p.`end` > :from AND " + GUIDE_WORDS + ") " +
            "ORDER BY i.accountId, i.ord LIMIT 300",
    )
    fun guideChannels(ids: List<Long>, cat: String?, w1: String, w2: String, w3: String, w4: String, from: Long): Flow<List<Item>>
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_lists ORDER BY builtin DESC, lower(name), id")
    fun lists(): Flow<List<FavoriteList>>

    @Query("SELECT DISTINCT kind FROM favorite_lists WHERE builtin = 1")
    suspend fun builtinKinds(): List<Int>

    @Query("UPDATE favorite_lists SET name = :name WHERE builtin = 1")
    suspend fun nameBuiltin(name: String)

    @Insert
    suspend fun insertList(list: FavoriteList): Long

    @Query("UPDATE favorite_lists SET name = :name WHERE id = :id AND builtin = 0")
    suspend fun renameList(id: Long, name: String)

    @Query("DELETE FROM favorite_lists WHERE id = :id AND builtin = 0")
    suspend fun deleteList(id: Long)

    @Query("SELECT COUNT(*) > 0 FROM favorite_lists WHERE id = :id AND builtin = 1")
    suspend fun isBuiltin(id: Long): Boolean

    @Query(
        "SELECT f.listId AS listId, COUNT(*) AS n FROM favorites f JOIN items i ON i.accountId = f.accountId AND i.kind = f.kind AND i.remoteId = f.remoteId " +
            "WHERE f.kind = :kind AND f.accountId IN (:ids) GROUP BY f.listId",
    )
    fun listCounts(ids: List<Long>, kind: Int): Flow<List<ListCount>>

    @Query("DELETE FROM favorites WHERE listId = :id")
    suspend fun deleteListEntries(id: Long)

    @Query(
        "SELECT f.listId AS listId, f.accountId AS accountId, f.kind AS kind, f.remoteId AS remoteId, f.name AS name, " +
            "f.sortKey AS sortKey, i.logo AS logo, i.epgId AS epgId, i.id AS itemId FROM favorites f " +
            "LEFT JOIN items i ON i.accountId = f.accountId AND i.kind = f.kind AND i.remoteId = f.remoteId " +
            "WHERE f.listId = :listId AND f.kind = :kind AND f.accountId IN (SELECT id FROM accounts WHERE enabled = 1) ORDER BY f.sortKey",
    )
    fun rows(listId: Long, kind: Int): Flow<List<FavoriteRow>>

    @Query(
        "SELECT i.* FROM favorites f JOIN items i ON i.accountId = f.accountId AND i.kind = f.kind AND i.remoteId = f.remoteId " +
            "WHERE f.listId = :listId AND i.kind = 0 AND i.accountId IN (SELECT id FROM accounts WHERE enabled = 1) ORDER BY f.sortKey LIMIT 1 OFFSET :offset",
    )
    suspend fun itemAt(listId: Long, offset: Int): Item?

    @Query(
        "SELECT COUNT(*) FROM favorites f JOIN items i ON i.accountId = f.accountId AND i.kind = f.kind AND i.remoteId = f.remoteId " +
            "WHERE f.listId = :listId AND i.kind = 0 AND i.accountId IN (SELECT id FROM accounts WHERE enabled = 1)",
    )
    suspend fun presentCount(listId: Long): Int

    @Query(
        "SELECT i.* FROM favorites f JOIN items i ON i.accountId = f.accountId AND i.kind = f.kind AND i.remoteId = f.remoteId " +
            "WHERE f.listId = :listId AND i.kind = 0 AND i.accountId IN (SELECT id FROM accounts WHERE enabled = 1) ORDER BY f.sortKey",
    )
    fun channelItems(listId: Long): Flow<List<Item>>

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

    @Query("SELECT * FROM favorites WHERE accountId = :accountId")
    suspend fun ofAccount(accountId: Long): List<Favorite>
}

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(progress: Progress)

    @Query("SELECT * FROM progress WHERE accountId = :accountId AND kind = :kind AND remoteId = :remoteId")
    suspend fun get(accountId: Long, kind: Int, remoteId: String): Progress?

    @Query("SELECT * FROM progress WHERE accountId = :accountId AND kind = :kind AND remoteId IN (:ids)")
    suspend fun forIds(accountId: Long, kind: Int, ids: List<String>): List<Progress>

    @Query(
        "SELECT * FROM progress WHERE dismissed = 0 AND accountId IN (SELECT id FROM accounts WHERE enabled = 1) AND ((kind = 1 AND watched = 0 AND positionMs > 5000) " +
            "OR (kind = 3 AND seriesRemoteId <> '' AND (positionMs > 5000 OR watched = 1))) ORDER BY updated DESC LIMIT 300",
    )
    fun continueWatching(): Flow<List<Progress>>

    @Query("UPDATE progress SET dismissed = 1 WHERE accountId = :accountId AND kind = 1 AND remoteId = :remoteId")
    suspend fun dismissMovie(accountId: Long, remoteId: String)

    @Query("UPDATE progress SET dismissed = 1 WHERE accountId = :accountId AND kind = 3 AND seriesRemoteId = :seriesRemoteId")
    suspend fun dismissSeries(accountId: Long, seriesRemoteId: String)

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

    @Query("SELECT * FROM recordings WHERE status = 3")
    suspend fun scheduled(): List<Recording>

    @Query("SELECT * FROM recordings WHERE accountId = :accountId AND status IN (0, 3) AND COALESCE(plannedStart, startedAt) < :to AND (plannedEnd IS NULL OR plannedEnd > :from)")
    suspend fun overlapping(accountId: Long, from: Long, to: Long): List<Recording>

    @Query("UPDATE recordings SET status = 2 WHERE status = 0")
    suspend fun markInterrupted()

    @Query("DELETE FROM recordings WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: Long)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY seriesName, season, episode, createdAt")
    fun all(): Flow<List<Download>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(download: Download): Long

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): Download?

    @Query("SELECT * FROM downloads WHERE status = 0 ORDER BY id LIMIT 1")
    suspend fun nextQueued(): Download?

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN (0, 1)")
    suspend fun pending(): Int

    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: Int, error: String?)

    @Query("UPDATE downloads SET bytes = :bytes, total = :total WHERE id = :id")
    suspend fun setProgress(id: Long, bytes: Long, total: Long)

    @Query("UPDATE downloads SET status = 0 WHERE status = 1")
    suspend fun resetRunning()

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE accountId = :accountId AND channelRemoteId = :channel AND startMs = :startMs")
    suspend fun find(accountId: Long, channel: String, startMs: Long): Reminder?

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<Reminder>

    @Query("SELECT * FROM reminders ORDER BY startMs")
    fun allFlow(): Flow<List<Reminder>>

    @Query("DELETE FROM reminders WHERE startMs < :now")
    suspend fun deleteBefore(now: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM reminders WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: Long)
}

@Dao
interface HistoryDao {
    @Query(
        "SELECT i.*, c.name AS listName FROM history h JOIN items i ON i.accountId = h.accountId AND i.kind = 0 AND i.remoteId = h.remoteId " +
            "LEFT JOIN categories c ON c.accountId = i.accountId AND c.kind = 0 AND c.remoteId = i.categoryRemoteId " +
            "WHERE h.accountId IN (SELECT id FROM accounts WHERE enabled = 1) ORDER BY h.watched DESC LIMIT 24",
    )
    fun rows(): kotlinx.coroutines.flow.Flow<List<HistoryRow>>

    @Query(
        "SELECT i.* FROM history h JOIN items i ON i.accountId = h.accountId AND i.kind = 0 AND i.remoteId = h.remoteId " +
            "WHERE h.accountId IN (SELECT id FROM accounts WHERE enabled = 1) ORDER BY h.watched DESC LIMIT 24",
    )
    suspend fun items(): List<Item>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: HistoryEntry)

    @Query("DELETE FROM history WHERE watched NOT IN (SELECT watched FROM history ORDER BY watched DESC LIMIT 24)")
    suspend fun prune()

    @Query("DELETE FROM history WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: Long)

    @Query("DELETE FROM history WHERE accountId = :accountId AND remoteId NOT IN (SELECT remoteId FROM items WHERE accountId = :accountId AND kind = 0)")
    suspend fun pruneMissing(accountId: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}
