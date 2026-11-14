package com.kutlusoy.proiptv.desktop

import com.kutlusoy.proiptv.net.Http
import com.kutlusoy.proiptv.net.StreamUrls
import com.kutlusoy.proiptv.net.XtreamApi
import com.kutlusoy.proiptv.net.XtreamEpisode
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
) {
    val key: String get() = "$accountId|$kind|$remoteId"
}

data class Category(val accountId: String, val id: String, val name: String)

data class Playable(val title: String, val url: String, val userAgent: String?, val live: Boolean)

data class LibraryData(
    val entries: Map<Int, List<Entry>> = emptyMap(),
    val categories: Map<Int, List<Category>> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
) {
    fun entries(kind: Int): List<Entry> = entries[kind].orEmpty()
    fun categories(kind: Int): List<Category> = categories[kind].orEmpty()
}

/** Loads channels, movies and series of all enabled accounts into memory. */
class Library(private val store: Store) {

    private fun api(a: AccountDto) = XtreamApi(Http.client, a.url, a.username, Secrets.unprotect(a.password), a.userAgent)

    suspend fun load(): LibraryData = withContext(Dispatchers.IO) {
        val entries = HashMap<Int, MutableList<Entry>>()
        val categories = HashMap<Int, MutableList<Category>>()
        val errors = HashMap<String, String>()
        for (a in store.config.accounts.filter { it.enabled }) {
            try {
                if (a.type == "xtream") loadXtream(a, entries, categories) else loadM3u(a, entries, categories)
            } catch (e: Exception) {
                errors[a.id] = e.message ?: e.javaClass.simpleName
            }
        }
        LibraryData(entries, categories, errors)
    }

    private fun loadXtream(a: AccountDto, entries: MutableMap<Int, MutableList<Entry>>, categories: MutableMap<Int, MutableList<Category>>) {
        val api = api(a)
        api.authenticate()
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
                    )
                }
            }
        }
    }

    private fun loadM3u(a: AccountDto, entries: MutableMap<Int, MutableList<Entry>>, categories: MutableMap<Int, MutableList<Category>>) {
        val response = Http.client.newCall(Http.request(a.url, a.userAgent)).execute()
        response.use {
            if (!it.isSuccessful) throw java.io.IOException("HTTP ${it.code}")
            val reader = BufferedReader(InputStreamReader(it.body!!.byteStream(), Charsets.UTF_8), 64 * 1024)
            val groups = LinkedHashSet<String>()
            var n = 0
            for (e in M3uParser.parse(reader)) {
                val vod = e.url.contains("/movie/") || e.url.contains("/series/") || e.url.substringBefore('?').let { u -> VOD_EXT.any { x -> u.endsWith(x) } }
                val kind = if (vod) Kind.MOVIE else Kind.LIVE
                groups += "$kind|${e.group}"
                entries.getOrPut(kind) { mutableListOf() } += Entry(a.id, kind, "m${n++}", e.name, e.group, e.logo, "", e.url, null)
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
        if (e.url != null) return Playable(e.name, e.url, a.userAgent, e.kind == Kind.LIVE)
        val pass = Secrets.unprotect(a.password)
        val url = when (e.kind) {
            Kind.LIVE -> StreamUrls.live(a.url, a.username, pass, e.remoteId, "ts")
            Kind.MOVIE -> StreamUrls.movie(a.url, a.username, pass, e.remoteId, e.ext.ifBlank { "mp4" })
            else -> return null
        }
        return Playable(e.name, url, a.userAgent, e.kind == Kind.LIVE)
    }

    suspend fun episodes(e: Entry): List<XtreamEpisode> = withContext(Dispatchers.IO) {
        val a = store.config.accounts.firstOrNull { it.id == e.accountId } ?: return@withContext emptyList()
        api(a).seriesDetail(e.remoteId).episodes
    }

    fun episodePlayable(series: Entry, ep: XtreamEpisode): Playable? {
        val a = store.config.accounts.firstOrNull { it.id == series.accountId } ?: return null
        val url = StreamUrls.episode(a.url, a.username, Secrets.unprotect(a.password), ep.id, ep.ext)
        return Playable("${series.name} S${ep.season}E${ep.number}", url, a.userAgent, false)
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

    companion object {
        private val VOD_EXT = listOf(".mp4", ".mkv", ".avi", ".mov")
        fun normalizeServer(input: String) = XtreamUrl.normalizeServer(input)
    }
}
