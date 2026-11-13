package com.kutlusoy.proiptv.data

import com.kutlusoy.proiptv.net.Http
import com.kutlusoy.proiptv.net.StreamUrls
import com.kutlusoy.proiptv.net.XtreamApi
import com.kutlusoy.proiptv.net.XtreamEpisode
import com.kutlusoy.proiptv.net.XtreamUrl
import com.kutlusoy.proiptv.sync.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface Source {
    data class Browse(val kind: Int, val accountIds: List<Long>, val category: String?, val query: String) : Source
    data class Favs(val listId: Long) : Source
}

data class PlayTarget(
    val url: String,
    val userAgent: String?,
    val title: String,
    val live: Boolean,
    val accountId: Long,
    val kind: Int,
    val remoteId: String,
)

class Repository(val db: AppDatabase, private val secrets: SecretBox, val sync: SyncManager) {
    fun accounts() = db.accounts().all()

    suspend fun saveAccount(draft: Account, newPassword: String): Long {
        var account = draft
        var plain = newPassword
        if (account.type == AccountType.XTREAM) {
            val login = XtreamUrl.parse(account.url)
            account = account.copy(
                url = login.server,
                username = account.username.ifBlank { login.username.orEmpty() },
            )
            if (plain.isBlank()) plain = login.password.orEmpty()
        } else {
            account = account.copy(url = account.url.trim())
        }
        if (plain.isNotBlank()) account = account.copy(password = secrets.encrypt(plain))
        val id = if (account.id == 0L) {
            db.accounts().insert(account.copy(ord = db.accounts().list().size))
        } else {
            db.accounts().update(account)
            account.id
        }
        sync.sync(id)
        return id
    }

    suspend fun setEnabled(account: Account, enabled: Boolean) = db.accounts().update(account.copy(enabled = enabled))

    suspend fun deleteAccount(id: Long) {
        sync.cancel(id)
        db.items().deleteAccount(id)
        db.items().deleteAccountCategories(id)
        db.programs().deleteAccount(id)
        db.favorites().deleteAccount(id)
        db.progress().deleteAccount(id)
        db.recordings().deleteAccount(id)
        db.accounts().delete(id)
    }

    suspend fun maxConnections(accountId: Long): Int? = db.accounts().get(accountId)?.maxConnections

    suspend fun enabledIds(): List<Long> = db.accounts().enabledIds()

    suspend fun count(src: Source): Int = when (src) {
        is Source.Browse -> db.items().count(src.kind, src.accountIds, src.category, src.query)
        is Source.Favs -> db.favorites().presentCount(src.listId)
    }

    suspend fun at(src: Source, index: Int): Item? = when (src) {
        is Source.Browse -> db.items().at(src.kind, src.accountIds, src.category, src.query, index)
        is Source.Favs -> db.favorites().itemAt(src.listId, index)
    }

    suspend fun target(item: Item): PlayTarget? = withContext(Dispatchers.IO) {
        val account = db.accounts().get(item.accountId) ?: return@withContext null
        val ua = account.userAgent
        val url = when {
            item.url != null -> item.url
            account.type == AccountType.XTREAM -> {
                val pass = secrets.decrypt(account.password)
                when (item.kind) {
                    Kind.LIVE -> StreamUrls.live(account.url, account.username, pass, item.remoteId, StreamUrls.liveFormat(account.liveFormat, account.outputFormats))
                    Kind.MOVIE -> StreamUrls.movie(account.url, account.username, pass, item.remoteId, item.ext ?: "mp4")
                    else -> null
                }
            }
            else -> null
        } ?: return@withContext null
        PlayTarget(url, ua, item.name, item.kind == Kind.LIVE, item.accountId, item.kind, item.remoteId)
    }

    suspend fun catchupTarget(item: Item, program: Program): PlayTarget? = withContext(Dispatchers.IO) {
        val account = db.accounts().get(item.accountId) ?: return@withContext null
        val now = System.currentTimeMillis() / 1000
        val url = if (account.type == AccountType.XTREAM) {
            val minutes = ((program.end - program.start) / 60).toInt() + 5
            StreamUrls.xtreamCatchup(account.url, account.username, secrets.decrypt(account.password), item.remoteId, program.start, minutes)
        } else {
            StreamUrls.m3uCatchup(item, program.start, program.end, now)
        } ?: return@withContext null
        PlayTarget(url, account.userAgent, "${item.name}: ${program.title}", false, item.accountId, Kind.EPISODE, "${item.remoteId}-${program.start}")
    }

    private fun api(account: Account) =
        XtreamApi(Http.client, account.url, account.username, secrets.decrypt(account.password), account.userAgent)

    suspend fun episodes(item: Item): List<XtreamEpisode> = withContext(Dispatchers.IO) {
        val account = db.accounts().get(item.accountId) ?: return@withContext emptyList()
        api(account).seriesEpisodes(item.remoteId)
    }

    suspend fun episodeTarget(series: Item, episode: XtreamEpisode): PlayTarget? = withContext(Dispatchers.IO) {
        val account = db.accounts().get(series.accountId) ?: return@withContext null
        val url = StreamUrls.episode(account.url, account.username, secrets.decrypt(account.password), episode.id, episode.ext)
        val title = "${series.name} S${episode.season}E${episode.number}"
        PlayTarget(url, account.userAgent, title, false, series.accountId, Kind.EPISODE, episode.id)
    }

    suspend fun progress(t: PlayTarget): Progress? = db.progress().get(t.accountId, t.kind, t.remoteId)

    suspend fun saveProgress(t: PlayTarget, positionMs: Long, durationMs: Long) {
        if (t.live || durationMs <= 0) return
        val finished = positionMs > durationMs * 0.95
        db.progress().save(Progress(t.accountId, t.kind, t.remoteId, if (finished) 0 else positionMs, durationMs, System.currentTimeMillis()))
    }

    suspend fun nowAndNext(accountId: Long, epgId: String?): Pair<Program?, Program?> {
        if (epgId.isNullOrBlank()) return null to null
        val now = System.currentTimeMillis() / 1000
        val key = epgId.lowercase()
        val current = db.programs().now(accountId, key, now)
        val next = db.programs().next(accountId, key, current?.end ?: now)
        return current to next
    }

    suspend fun fetchShortEpg(item: Item): Boolean = withContext(Dispatchers.IO) {
        val account = db.accounts().get(item.accountId) ?: return@withContext false
        val epgId = item.epgId?.lowercase()
        if (account.type != AccountType.XTREAM || epgId.isNullOrBlank()) return@withContext false
        val list = runCatching { api(account).shortEpg(item.remoteId) }.getOrDefault(emptyList())
        if (list.isEmpty()) return@withContext false
        db.programs().deleteRange(account.id, epgId, list.minOf { it.start }, list.maxOf { it.end })
        db.programs().insertAll(list.map { Program(accountId = account.id, epgId = epgId, start = it.start, end = it.end, title = it.title, description = it.description, gen = account.epgGen) })
        true
    }

    suspend fun guide(accountId: Long, epgId: String, daysBack: Int, daysAhead: Int): List<Program> {
        val now = System.currentTimeMillis() / 1000
        return db.programs().range(accountId, epgId.lowercase(), now - daysBack * 86400L, now + daysAhead * 86400L)
    }

    suspend fun ensureDefaultList(name: String): Long {
        if (db.favorites().listCount() == 0) return db.favorites().insertList(FavoriteList(name = name))
        return -1
    }

    suspend fun createList(name: String): Long = db.favorites().insertList(FavoriteList(name = name.trim()))

    suspend fun renameList(id: Long, name: String) = db.favorites().renameList(id, name.trim())

    suspend fun deleteList(id: Long) {
        db.favorites().deleteListEntries(id)
        db.favorites().deleteList(id)
    }

    suspend fun listsOf(accountId: Long, kind: Int, remoteId: String): List<Long> =
        db.favorites().listsOf(accountId, kind, remoteId)

    suspend fun setFavorite(listId: Long, accountId: Long, kind: Int, remoteId: String, name: String, on: Boolean) {
        if (on) {
            val max = db.favorites().maxKey(listId)
            db.favorites().add(Favorite(listId, accountId, kind, remoteId, name, (max ?: 0.0) + SortKeys.STEP))
        } else {
            db.favorites().remove(listId, accountId, kind, remoteId)
        }
    }

    suspend fun moveFavorite(listId: Long, from: Int, to: Int) {
        val entries = db.favorites().entries(listId)
        if (from !in entries.indices || to !in entries.indices || from == to) return
        val moved = entries[from]
        val rest = entries.toMutableList().also { it.removeAt(from) }
        val prev = rest.getOrNull(to - 1)?.sortKey
        val next = rest.getOrNull(to)?.sortKey
        val key = SortKeys.between(prev, next)
        if (key != null) {
            db.favorites().setKey(listId, moved.accountId, moved.kind, moved.remoteId, key)
        } else {
            rest.add(to, moved)
            val keys = SortKeys.evenly(rest.size)
            rest.forEachIndexed { i, f -> db.favorites().setKey(listId, f.accountId, f.kind, f.remoteId, keys[i]) }
        }
    }
}
