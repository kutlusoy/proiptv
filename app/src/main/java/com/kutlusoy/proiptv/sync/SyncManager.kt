package com.kutlusoy.proiptv.sync

import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.AccountType
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Category
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.Program
import com.kutlusoy.proiptv.data.SecretBox
import com.kutlusoy.proiptv.net.Http
import com.kutlusoy.proiptv.net.HttpException
import com.kutlusoy.proiptv.net.XtreamApi
import com.kutlusoy.proiptv.net.int
import com.kutlusoy.proiptv.net.maybeGunzip
import com.kutlusoy.proiptv.net.str
import com.kutlusoy.proiptv.parse.M3uParser
import com.kutlusoy.proiptv.parse.XmltvParser
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Response

enum class Phase { CONNECTING, LIVE, MOVIES, SERIES, GUIDE }

data class SyncState(val phase: Phase, val count: Int = 0)

class SyncManager(
    private val db: AppDatabase,
    private val secrets: SecretBox,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<Map<Long, SyncState>>(emptyMap())
    val state: StateFlow<Map<Long, SyncState>> = _state
    private val jobs = ConcurrentHashMap<Long, Job>()

    fun isRunning(id: Long) = jobs[id]?.isActive == true

    fun sync(accountId: Long) {
        if (isRunning(accountId)) return
        jobs[accountId] = scope.launch(Dispatchers.IO) {
            try {
                run(accountId)
            } finally {
                _state.value = _state.value - accountId
                jobs.remove(accountId)
            }
        }
    }

    fun cancel(accountId: Long) {
        jobs.remove(accountId)?.cancel()
        _state.value = _state.value - accountId
    }

    suspend fun syncStale(maxAgeMs: Long) {
        val now = System.currentTimeMillis()
        for (a in db.accounts().list()) {
            if (a.enabled && now - a.lastSync > maxAgeMs) sync(a.id)
        }
    }

    fun refreshGuide(accountId: Long) {
        if (isRunning(accountId)) return
        jobs[accountId] = scope.launch(Dispatchers.IO) {
            try {
                refreshGuideNow(accountId)
            } finally {
                jobs.remove(accountId)
            }
        }
    }

    suspend fun refreshGuideNow(accountId: Long) {
        val account = db.accounts().get(accountId) ?: return
        val url = guideUrl(account) ?: return
        try {
            loadGuide(account, url, conditional = true)
        } finally {
            _state.value = _state.value - accountId
        }
    }

    private suspend fun dueGuides(): List<Long> {
        val now = System.currentTimeMillis() / 1000
        return db.accounts().list().filter { a ->
            a.enabled && guideUrl(a) != null &&
                GuidePolicy.isDue(now, a.epgSync, db.programs().maxEnd(a.id))
        }.map { it.id }
    }

    fun syncStaleGuides() {
        scope.launch(Dispatchers.IO) {
            for (id in dueGuides()) refreshGuide(id)
        }
    }

    suspend fun refreshDueGuidesNow() {
        for (id in dueGuides()) if (!isRunning(id)) refreshGuideNow(id)
    }

    private fun guideUrl(a: Account): String? = when {
        !a.epgUrl.isNullOrBlank() -> a.epgUrl
        a.type == AccountType.XTREAM ->
            "${a.url}/xmltv.php?username=${URLEncoder.encode(a.username, "UTF-8")}&password=${URLEncoder.encode(secrets.decrypt(a.password), "UTF-8")}"
        else -> a.autoEpgUrl
    }

    private fun set(id: Long, phase: Phase, count: Int = 0) {
        _state.value = _state.value + (id to SyncState(phase, count))
    }

    private suspend fun run(id: Long) {
        val account = db.accounts().get(id) ?: return
        var error: String? = null
        try {
            set(id, Phase.CONNECTING)
            when (account.type) {
                AccountType.XTREAM -> syncXtream(account)
                else -> syncM3u(account)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = describe(e)
        }
        val fresh = db.accounts().get(id) ?: return
        db.accounts().update(
            fresh.copy(
                lastSync = if (error == null) System.currentTimeMillis() else fresh.lastSync,
                lastError = error ?: fresh.lastError.takeIf { it?.startsWith("Guide: ") == true },
            ),
        )
    }

    private fun describe(e: Exception): String = when (e) {
        is HttpException -> if (e.code == 401 || e.code == 403) "Login failed" else e.message ?: "HTTP error"
        else -> e.message ?: e.javaClass.simpleName
    }

    private suspend fun nextGen(accountId: Long, epg: Boolean): Int {
        val account = db.accounts().get(accountId) ?: return (System.currentTimeMillis() / 1000).toInt()
        val gen = maxOf((System.currentTimeMillis() / 1000).toInt(), (if (epg) account.epgGen else account.syncGen) + 1)
        db.accounts().update(if (epg) account.copy(epgGen = gen) else account.copy(syncGen = gen))
        return gen
    }

    private suspend fun syncXtream(account: Account) {
        val api = XtreamApi(Http.client, account.url, account.username, secrets.decrypt(account.password), account.userAgent)
        val info = api.authenticate()
        db.accounts().get(account.id)?.let {
            db.accounts().update(
                it.copy(
                    status = info.status, expiry = info.expiry, maxConnections = info.maxConnections,
                    activeConnections = info.activeConnections, trial = info.trial,
                    outputFormats = info.outputFormats.joinToString(","),
                ),
            )
        }
        val gen = nextGen(account.id, false)
        loadXtreamKind(api, account, Kind.LIVE, "get_live_categories", "get_live_streams", gen, Phase.LIVE)
        if (account.useVod) loadXtreamKind(api, account, Kind.MOVIE, "get_vod_categories", "get_vod_streams", gen, Phase.MOVIES)
        else clearKind(account.id, Kind.MOVIE)
        if (account.useSeries) loadXtreamKind(api, account, Kind.SERIES, "get_series_categories", "get_series", gen, Phase.SERIES)
        else clearKind(account.id, Kind.SERIES)
        db.accounts().get(account.id)?.let { fresh -> guideUrl(fresh)?.let { loadGuide(fresh, it, conditional = false) } }
    }

    private suspend fun clearKind(accountId: Long, kind: Int) {
        db.items().deleteKind(accountId, kind)
        db.items().deleteCategories(accountId, kind)
    }

    private suspend fun loadXtreamKind(api: XtreamApi, account: Account, kind: Int, catAction: String, streamAction: String, gen: Int, phase: Phase) {
        val cats = api.stream(catAction).use { s ->
            s.items.mapIndexed { i, o ->
                Category(accountId = account.id, kind = kind, remoteId = o.str("category_id").orEmpty(), name = o.str("category_name").orEmpty(), ord = i)
            }.toList()
        }
        db.items().replaceCategories(account.id, kind, cats)
        var ord = 0
        val batch = ArrayList<Item>(BATCH)
        api.stream(streamAction).use { s ->
            for (o in s.items) {
                val remote = (if (kind == Kind.SERIES) o.str("series_id") else o.str("stream_id")) ?: continue
                batch += Item(
                    accountId = account.id, kind = kind, remoteId = remote,
                    name = o.str("name").orEmpty(),
                    categoryRemoteId = o.str("category_id").orEmpty(),
                    logo = (o.str("stream_icon") ?: o.str("cover"))?.takeIf { it.isNotBlank() },
                    epgId = o.str("epg_channel_id")?.takeIf { it.isNotBlank() }?.lowercase(),
                    ext = o.str("container_extension")?.takeIf { it.isNotBlank() },
                    archiveDays = if (o.int("tv_archive") == 1) o.int("tv_archive_duration") ?: 0 else 0,
                    rating = o.str("rating")?.takeIf { it.isNotBlank() },
                    plot = o.str("plot")?.takeIf { it.isNotBlank() },
                    ord = ord++, gen = gen,
                )
                if (batch.size >= BATCH) {
                    db.items().upsert(batch.toList())
                    batch.clear()
                    set(account.id, phase, ord)
                }
            }
        }
        if (batch.isNotEmpty()) db.items().upsert(batch.toList())
        db.items().deleteStale(account.id, kind, gen)
    }

    private suspend fun syncM3u(account: Account) {
        val gen = nextGen(account.id, false)
        var epgFromHeader: String? = null
        val groups = mapOf(Kind.LIVE to LinkedHashMap<String, Int>(), Kind.MOVIE to LinkedHashMap<String, Int>())
        val counters = intArrayOf(0, 0)
        val batch = ArrayList<Item>(BATCH)
        open(account.url, account.userAgent).use { raw ->
            val reader = BufferedReader(InputStreamReader(raw.maybeGunzip(), Charsets.UTF_8), 64 * 1024)
            for (e in M3uParser.parse(reader) { epgFromHeader = it.epgUrl }) {
                val kind = detectKind(e.url)
                val map = groups.getValue(kind)
                map.getOrPut(e.group) { map.size }
                val ord = if (kind == Kind.LIVE) counters[0]++ else counters[1]++
                batch += Item(
                    accountId = account.id, kind = kind, remoteId = hash(e.url), name = e.name,
                    categoryRemoteId = e.group, logo = e.logo, epgId = e.tvgId?.lowercase(), url = e.url,
                    archiveDays = e.catchupDays, catchupSource = e.catchupSource, ord = ord, gen = gen,
                )
                if (batch.size >= BATCH) {
                    db.items().upsert(batch.toList())
                    batch.clear()
                    set(account.id, Phase.LIVE, counters[0] + counters[1])
                }
            }
        }
        if (batch.isNotEmpty()) db.items().upsert(batch.toList())
        for ((kind, map) in groups) {
            db.items().deleteStale(account.id, kind, gen)
            db.items().replaceCategories(account.id, kind, map.map { (name, i) -> Category(accountId = account.id, kind = kind, remoteId = name, name = name, ord = i) })
        }
        db.items().deleteStale(account.id, Kind.SERIES, gen)
        val header = epgFromHeader?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        db.accounts().get(account.id)?.let { fresh ->
            val updated = fresh.copy(autoEpgUrl = header)
            db.accounts().update(updated)
            guideUrl(updated)?.let { loadGuide(updated, it, conditional = false) }
        }
    }

    private suspend fun loadGuide(account: Account, url: String, conditional: Boolean) {
        var gen = 0
        try {
            val ids = db.items().epgIds(account.id).toHashSet()
            if (ids.isEmpty()) return
            set(account.id, Phase.GUIDE)
            val useValidators = conditional && db.programs().count(account.id) > 0
            var etag: String? = null
            var modified: String? = null
            val raw: InputStream = if (url.startsWith("file:")) {
                File(URI(url)).inputStream()
            } else {
                val request = Http.request(url, account.userAgent).newBuilder().apply {
                    if (useValidators) {
                        account.epgEtag?.let { header("If-None-Match", it) }
                        account.epgModified?.let { header("If-Modified-Since", it) }
                    }
                }.build()
                val response: Response = withContext(Dispatchers.IO) { Http.client.newCall(request).execute() }
                if (response.code == 304) {
                    response.close()
                    markGuide(account.id, null, null, keepValidators = true)
                    return
                }
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    throw HttpException(code, "HTTP $code")
                }
                etag = response.header("ETag")
                modified = response.header("Last-Modified")
                response.body!!.byteStream()
            }
            gen = nextGen(account.id, true)
            val now = System.currentTimeMillis() / 1000
            val batch = ArrayList<Program>(BATCH)
            var count = 0
            raw.use {
                val programs = XmltvParser.parse(it.maybeGunzip(), { id -> id.lowercase() in ids }, now - 2 * 86400, now + 4 * 86400)
                for (p in programs) {
                    batch += Program(accountId = account.id, epgId = p.channel.lowercase(), start = p.start, end = p.end, title = p.title, description = p.description, gen = gen)
                    count++
                    if (batch.size >= BATCH) {
                        db.programs().insertAll(batch.toList())
                        batch.clear()
                        set(account.id, Phase.GUIDE, count)
                    }
                }
            }
            if (batch.isNotEmpty()) db.programs().insertAll(batch.toList())
            db.programs().deleteStale(account.id, gen)
            markGuide(account.id, etag, modified, keepValidators = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (gen != 0) db.programs().deleteGen(account.id, gen)
            db.accounts().get(account.id)?.let { db.accounts().update(it.copy(lastError = "Guide: " + (e.message ?: e.javaClass.simpleName))) }
        }
    }

    private suspend fun markGuide(accountId: Long, etag: String?, modified: String?, keepValidators: Boolean) {
        val account = db.accounts().get(accountId) ?: return
        db.accounts().update(
            account.copy(
                epgSync = System.currentTimeMillis() / 1000,
                epgEtag = if (keepValidators) account.epgEtag else etag,
                epgModified = if (keepValidators) account.epgModified else modified,
                lastError = account.lastError?.takeUnless { it.startsWith("Guide: ") },
            ),
        )
    }

    private suspend fun open(url: String, userAgent: String?): InputStream = withContext(Dispatchers.IO) {
        if (url.startsWith("file:")) return@withContext File(URI(url)).inputStream()
        val response = Http.client.newCall(Http.request(url, userAgent)).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw HttpException(code, "HTTP $code")
        }
        response.body!!.byteStream()
    }

    private fun detectKind(url: String): Int {
        val u = url.lowercase().substringBefore('?')
        return when {
            "/movie/" in u || "/series/" in u -> Kind.MOVIE
            u.endsWith(".mkv") || u.endsWith(".mp4") || u.endsWith(".avi") || u.endsWith(".mov") || u.endsWith(".m4v") -> Kind.MOVIE
            else -> Kind.LIVE
        }
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    private companion object {
        const val BATCH = 1000
    }
}
