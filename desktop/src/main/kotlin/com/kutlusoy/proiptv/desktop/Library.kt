package com.kutlusoy.proiptv.desktop

import com.kutlusoy.proiptv.net.Http
import com.kutlusoy.proiptv.net.StreamUrls
import com.kutlusoy.proiptv.net.XtreamApi
import com.kutlusoy.proiptv.net.XtreamEpisode
import com.kutlusoy.proiptv.net.XtreamInfo
import com.kutlusoy.proiptv.net.maybeGunzip
import com.kutlusoy.proiptv.parse.XmltvParser
import com.kutlusoy.proiptv.parse.XmltvProgram
import com.kutlusoy.proiptv.net.XtreamUrl
import com.kutlusoy.proiptv.net.str
import com.kutlusoy.proiptv.parse.M3uParser
import com.kutlusoy.proiptv.parse.decodeEntities
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Kind {
    const val LIVE = 0
    const val MOVIE = 1
    const val SERIES = 2
    const val EPISODE = 3
}

data class Entry(
    val accountId: String,
    val kind: Int,
    val remoteId: String,
    val name: String,
    val categoryId: String,
    val logo: String?,
    val ext: String,
    val url: String?,
    val rating: String?,
    val epgId: String? = null,
) {
    val key: String get() = "$accountId|$kind|$remoteId"
}

data class Category(val accountId: String, val id: String, val name: String)

/** What the player gets. account, kind and remoteId say where to keep the progress (empty for live TV and local files). */
data class Playable(
    val title: String,
    val url: String,
    val userAgent: String?,
    val live: Boolean,
    val account: String = "",
    val kind: Int = -1,
    val remoteId: String = "",
    val accountNumber: Long = 0,
    val channelId: String = "",
    val maxConnections: Int? = null,
)

data class AccountStatus(val info: XtreamInfo?, val channels: Int, val movies: Int, val series: Int, val loadedAt: Long)

data class LibraryData(
    val entries: Map<Int, List<Entry>> = emptyMap(),
    val categories: Map<Int, List<Category>> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
    val status: Map<String, AccountStatus> = emptyMap(),
) {
    fun entries(kind: Int): List<Entry> = entries[kind].orEmpty()
    fun categories(kind: Int): List<Category> = categories[kind].orEmpty()
}

/** Loads channels, movies and series of all enabled accounts into memory. */
class Library(private val store: Store) {
    private val m3uGuide = HashMap<String, String>()

    private fun api(a: AccountDto) = XtreamApi(Http.client, a.url, a.username, Secrets.unprotect(a.password), a.userAgent)

    suspend fun load(): LibraryData = withContext(Dispatchers.IO) {
        val entries = HashMap<Int, MutableList<Entry>>()
        val categories = HashMap<Int, MutableList<Category>>()
        val errors = HashMap<String, String>()
        val status = HashMap<String, AccountStatus>()
        for (a in store.config.accounts.filter { it.enabled }) {
            try {
                val info = if (a.type == "xtream") loadXtream(a, entries, categories) else { loadM3u(a, entries, categories); null }
                fun count(kind: Int) = entries[kind].orEmpty().count { it.accountId == a.id }
                status[a.id] = AccountStatus(info, count(Kind.LIVE), count(Kind.MOVIE), count(Kind.SERIES), System.currentTimeMillis())
            } catch (e: Exception) {
                errors[a.id] = e.message ?: e.javaClass.simpleName
            }
        }
        LibraryData(entries, categories, errors, status)
    }

    private fun loadXtream(a: AccountDto, entries: MutableMap<Int, MutableList<Entry>>, categories: MutableMap<Int, MutableList<Category>>): XtreamInfo {
        val api = api(a)
        val info = api.authenticate()
        val kinds = listOf(
            Triple(Kind.LIVE, "get_live_categories", "get_live_streams"),
            Triple(Kind.MOVIE, "get_vod_categories", "get_vod_streams"),
            Triple(Kind.SERIES, "get_series_categories", "get_series"),
        )
        for ((kind, catAction, streamAction) in kinds) {
            api.stream(catAction).use { s ->
                s.items.forEach { o ->
                    categories.getOrPut(kind) { mutableListOf() } +=
                        Category(a.id, o.str("category_id").orEmpty(), o.str("category_name").orEmpty().decodeEntities())
                }
            }
            api.stream(streamAction).use { s ->
                for (o in s.items) {
                    val id = (if (kind == Kind.SERIES) o.str("series_id") else o.str("stream_id")) ?: continue
                    entries.getOrPut(kind) { mutableListOf() } += Entry(
                        a.id, kind, id, o.str("name").orEmpty().decodeEntities(), o.str("category_id").orEmpty(),
                        (o.str("stream_icon") ?: o.str("cover"))?.takeIf { it.isNotBlank() },
                        o.str("container_extension")?.takeIf { it.isNotBlank() } ?: "mp4", null,
                        o.str("rating")?.takeIf { it.isNotBlank() },
                        o.str("epg_channel_id")?.takeIf { it.isNotBlank() }?.lowercase(),
                    )
                }
            }
        }
        return info
    }

    private fun loadM3u(a: AccountDto, entries: MutableMap<Int, MutableList<Entry>>, categories: MutableMap<Int, MutableList<Category>>) {
        val response = Http.client.newCall(Http.request(a.url, a.userAgent)).execute()
        response.use {
            if (!it.isSuccessful) throw java.io.IOException("HTTP ${it.code}")
            val reader = BufferedReader(InputStreamReader(it.body!!.byteStream(), Charsets.UTF_8), 64 * 1024)
            val groups = LinkedHashSet<String>()
            var n = 0
            for (e in M3uParser.parse(reader) { header -> header.epgUrl?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { m3uGuide[a.id] = it } }) {
                val vod = e.url.contains("/movie/") || e.url.contains("/series/") || e.url.substringBefore('?').let { u -> VOD_EXT.any { x -> u.endsWith(x) } }
                val kind = if (vod) Kind.MOVIE else Kind.LIVE
                groups += "$kind|${e.group}"
                entries.getOrPut(kind) { mutableListOf() } += Entry(a.id, kind, "m${n++}", e.name, e.group, e.logo, "", e.url, null, e.tvgId?.lowercase())
            }
            for (g in groups) {
                val kind = g.substringBefore('|').toInt()
                val name = g.substringAfter('|')
                categories.getOrPut(kind) { mutableListOf() } += Category(a.id, name, name)
            }
        }
    }

    fun playable(e: Entry): Playable? {
        val a = store.config.accounts.firstOrNull { it.id == e.accountId } ?: return null
        if (e.url != null) return Playable(e.name, e.url, a.userAgent, e.kind == Kind.LIVE, a.id, e.kind, e.remoteId)
        val pass = Secrets.unprotect(a.password)
        val url = when (e.kind) {
            Kind.LIVE -> StreamUrls.live(a.url, a.username, pass, e.remoteId, "ts")
            Kind.MOVIE -> StreamUrls.movie(a.url, a.username, pass, e.remoteId, e.ext.ifBlank { "mp4" })
            else -> return null
        }
        return Playable(e.name, url, a.userAgent, e.kind == Kind.LIVE, a.id, e.kind, e.remoteId)
    }

    suspend fun episodes(e: Entry): List<XtreamEpisode> = withContext(Dispatchers.IO) {
        val a = store.config.accounts.firstOrNull { it.id == e.accountId } ?: return@withContext emptyList()
        api(a).seriesDetail(e.remoteId).episodes
    }

    fun episodePlayable(series: Entry, ep: XtreamEpisode): Playable? {
        val a = store.config.accounts.firstOrNull { it.id == series.accountId } ?: return null
        val url = StreamUrls.episode(a.url, a.username, Secrets.unprotect(a.password), ep.id, ep.ext)
        return Playable("${series.name} S${ep.season}E${ep.number}", url, a.userAgent, false, a.id, Kind.EPISODE, ep.id)
    }

    /** Checks the login of an account before it is saved. */
    suspend fun test(a: AccountDto): String? = withContext(Dispatchers.IO) {
        try {
            if (a.type == "xtream") api(a).authenticate()
            else Http.client.newCall(Http.request(a.url, a.userAgent)).execute().use { if (!it.isSuccessful) throw java.io.IOException("HTTP ${it.code}") }
            null
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    /** Downloads the XMLTV guide of every account and keeps the programs of the channels that exist, from 3 hours ago to 36 hours ahead. */
    suspend fun loadGuide(live: List<Entry>): Guide = withContext(Dispatchers.IO) {
        val programs = HashMap<String, MutableList<XmltvProgram>>()
        val updated = HashMap<String, Long>()
        val errors = HashMap<String, String>()
        val now = System.currentTimeMillis() / 1000
        for (a in store.config.accounts.filter { it.enabled }) {
            val wanted = live.asSequence().filter { it.accountId == a.id }.mapNotNull { it.epgId }.toHashSet()
            if (wanted.isEmpty()) continue
            val url = a.epgUrl?.takeIf { it.isNotBlank() }
                ?: if (a.type == "xtream") "${a.url}/xmltv.php?username=${java.net.URLEncoder.encode(a.username, "UTF-8")}&password=${java.net.URLEncoder.encode(Secrets.unprotect(a.password), "UTF-8")}"
                else m3uGuide[a.id]
            if (url == null) continue
            try {
                Http.client.newCall(Http.request(url, a.userAgent)).execute().use { r ->
                    if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                    r.body!!.byteStream().maybeGunzip().use { input ->
                        for (p in XmltvParser.parse(input, { it.lowercase() in wanted }, now - 3 * 3600, now + 36 * 3600L)) {
                            programs.getOrPut(Guide.key(a.id, p.channel)) { mutableListOf() } += p
                        }
                    }
                }
                updated[a.id] = System.currentTimeMillis()
            } catch (e: Exception) {
                errors[a.id] = e.message ?: e.javaClass.simpleName
            }
        }
        Guide(programs.mapValues { (_, list) -> list.sortedBy { it.start } }, updated, errors)
    }

    companion object {
        private val VOD_EXT = listOf(".mp4", ".mkv", ".avi", ".mov")
        fun normalizeServer(input: String) = XtreamUrl.normalizeServer(input)
    }
}

/** The program guide of all accounts: programs per channel, when each account was updated, and errors. */
class Guide(
    val programs: Map<String, List<XmltvProgram>> = emptyMap(),
    val updated: Map<String, Long> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
) {
    fun nowNext(accountId: String, epgId: String?, nowSecs: Long): Pair<XmltvProgram?, XmltvProgram?> {
        if (epgId == null) return null to null
        val list = programs[key(accountId, epgId)] ?: return null to null
        return list.firstOrNull { it.start <= nowSecs && it.end > nowSecs } to list.firstOrNull { it.start > nowSecs }
    }

    companion object {
        fun key(accountId: String, epgId: String) = "$accountId|${epgId.lowercase()}"
    }
}

