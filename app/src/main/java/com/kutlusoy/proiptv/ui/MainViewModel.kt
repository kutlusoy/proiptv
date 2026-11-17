package com.kutlusoy.proiptv.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.cachedIn
import androidx.paging.PagingConfig
import com.kutlusoy.proiptv.ProIptvApp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.DlStatus
import com.kutlusoy.proiptv.data.Download
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.PlayTarget
import com.kutlusoy.proiptv.data.Recording
import com.kutlusoy.proiptv.data.Source
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class UpToDate(val version: String) : UpdateUi
    data class Available(val release: com.kutlusoy.proiptv.update.ReleaseInfo) : UpdateUi
    data class Working(val phase: com.kutlusoy.proiptv.update.UpdateManager.Phase, val percent: Int) : UpdateUi
    data class Error(val code: com.kutlusoy.proiptv.update.UpdateException.Code, val detail: String?) : UpdateUi
}

sealed interface PlayRequest {
    data class Zap(val source: Source, val index: Int, val direct: Boolean = false) : PlayRequest
    data class Single(val target: PlayTarget, val fromStart: Boolean = false) : PlayRequest
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as ProIptvApp).repo
    private val db = repo.db
    private val prefs = (app as ProIptvApp).prefs
    val uiMode = kotlinx.coroutines.flow.MutableStateFlow(prefs.uiMode)

    fun setUiMode(mode: Int) {
        prefs.uiMode = mode
        uiMode.value = mode
    }

    val accounts = repo.accounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<Account>())
    val syncState = repo.sync.state
    val favLists = db.favorites().lists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val favKeys = db.favorites().keys().map { list -> list.map { Triple(it.accountId, it.kind, it.remoteId) }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val player = MutableStateFlow<PlayRequest?>(null)

    /** A screen asks the main screen to open a tab (for example the button in the empty state). */
    val requestedTab = MutableStateFlow<String?>(null)
    var historyOnReturn = false
    val pendingOpen = MutableStateFlow<com.kutlusoy.proiptv.data.Item?>(null)
    val activeRecordings = (app as ProIptvApp).recordings.active
    val downloads = db.downloads().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val downloadManager get() = (getApplication<Application>() as ProIptvApp).downloads

    fun download(list: List<Download>) = downloadManager.enqueue(list)

    private fun tmdb(): com.kutlusoy.proiptv.net.Tmdb? {
        val key = prefs.tmdbKey
        return if (key.isEmpty()) null else com.kutlusoy.proiptv.net.Tmdb(com.kutlusoy.proiptv.net.Http.client, key, java.util.Locale.getDefault().toLanguageTag())
    }

    /** The information of the provider; what is missing (or all of it, for M3U) comes from TMDB when the user has entered a key. */
    suspend fun movieInfo(item: Item): com.kutlusoy.proiptv.net.VodInfo? = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val base = runCatching { repo.vodInfo(item) }.getOrNull()
        if (base?.plot != null) return@withContext base
        val t = tmdb() ?: return@withContext base
        val parsed = com.kutlusoy.proiptv.parse.TitleParser.parse(item.name)
        val found = com.kutlusoy.proiptv.net.Tmdb.cachedMovie(t, parsed.title, parsed.year) ?: return@withContext base
        if (base == null) found else base.copy(
            plot = base.plot ?: found.plot, cast = base.cast ?: found.cast, director = base.director ?: found.director, genre = base.genre ?: found.genre,
            releaseDate = base.releaseDate ?: found.releaseDate, durationSecs = base.durationSecs ?: found.durationSecs, rating = base.rating ?: found.rating,
            cover = base.cover ?: found.cover, backdrop = base.backdrop ?: found.backdrop, trailer = base.trailer ?: found.trailer,
        )
    }

    suspend fun seriesInfo(series: Item, base: com.kutlusoy.proiptv.net.SeriesInfo?): com.kutlusoy.proiptv.net.SeriesInfo? = withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (base?.plot != null) return@withContext base
        val t = tmdb() ?: return@withContext base
        val parsed = com.kutlusoy.proiptv.parse.TitleParser.parse(series.name)
        val found = com.kutlusoy.proiptv.net.Tmdb.cachedSeries(t, parsed.title, parsed.year) ?: return@withContext base
        if (base == null) found else base.copy(
            plot = base.plot ?: found.plot, cast = base.cast ?: found.cast, director = base.director ?: found.director, genre = base.genre ?: found.genre,
            releaseDate = base.releaseDate ?: found.releaseDate, rating = base.rating ?: found.rating, cover = base.cover ?: found.cover,
            backdrop = base.backdrop ?: found.backdrop, trailer = base.trailer ?: found.trailer,
        )
    }

    fun retryDownload(id: Long) = downloadManager.retry(id)

    fun pauseDownload(id: Long) = downloadManager.pause(id)

    fun deleteDownload(id: Long) = downloadManager.delete(id)

    fun localTarget(d: Download): PlayTarget? {
        if (d.status != DlStatus.DONE) return null
        val file = downloadManager.fileOf(d)
        if (!file.exists()) return null
        val title = if (d.kind == Kind.EPISODE) "${d.seriesName} S${d.season}E${d.episode}" else d.title
        return PlayTarget(Uri.fromFile(file).toString(), null, title, false, d.accountId, d.kind, d.remoteId, d.seriesRemoteId)
    }

    fun playDownload(d: Download) {
        localTarget(d)?.let { player.value = PlayRequest.Single(it) }
    }

    val recordings = db.recordings().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            com.kutlusoy.proiptv.update.UpdateEvents.installFailure.collect {
                updateUi.value = UpdateUi.Error(com.kutlusoy.proiptv.update.UpdateException.Code.INSTALL_FAILED, it)
            }
        }
        viewModelScope.launch {
            repo.ensureDefaultLists(app.getString(R.string.fav_title))
            // the lists stored by an older version were loaded without the filter for adult content: once load them again
            if (prefs.filterSync < FILTER_SYNC) {
                prefs.filterSync = FILTER_SYNC
                repo.db.accounts().list().filter { it.enabled }.forEach { repo.sync.sync(it.id) }
            }
        }
    }

    val updateUi = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    private val updates = com.kutlusoy.proiptv.update.UpdateManager(app)

    fun checkUpdate() {
        if (updateUi.value is UpdateUi.Checking || updateUi.value is UpdateUi.Working) return
        updateUi.value = UpdateUi.Checking
        viewModelScope.launch {
            val prefs = (getApplication<Application>() as ProIptvApp).prefs
            updateUi.value = try {
                when (val r = updates.check()) {
                    is com.kutlusoy.proiptv.update.UpdateManager.CheckResult.Available -> UpdateUi.Available(r.release)
                    is com.kutlusoy.proiptv.update.UpdateManager.CheckResult.UpToDate -> UpdateUi.UpToDate(r.version)
                }.also { prefs.lastUpdateCheck = System.currentTimeMillis() }
            } catch (e: com.kutlusoy.proiptv.update.UpdateException) {
                UpdateUi.Error(e.code, e.message)
            } catch (e: Exception) {
                UpdateUi.Error(com.kutlusoy.proiptv.update.UpdateException.Code.NETWORK, e.message)
            }
        }
    }

    fun installUpdate(release: com.kutlusoy.proiptv.update.ReleaseInfo) {
        if (updateUi.value is UpdateUi.Working) return
        updateUi.value = UpdateUi.Working(com.kutlusoy.proiptv.update.UpdateManager.Phase.VERIFYING, 0)
        viewModelScope.launch {
            try {
                updates.downloadAndInstall(release) { phase, percent -> updateUi.value = UpdateUi.Working(phase, percent) }
            } catch (e: com.kutlusoy.proiptv.update.UpdateException) {
                updateUi.value = UpdateUi.Error(e.code, e.message)
            } catch (e: Exception) {
                updateUi.value = UpdateUi.Error(com.kutlusoy.proiptv.update.UpdateException.Code.INSTALL_FAILED, e.message)
            }
        }
    }

    fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun categories(accountId: Long, kind: Int) = db.items().categories(accountId, kind)

    fun categoryCounts(ids: List<Long>, kind: Int) = db.items().categoryCounts(ids, kind)

    fun liveChannels(ids: List<Long>, category: String?) = db.items().liveChannels(ids, category)

    fun favoriteChannels(listId: Long) = db.favorites().channelItems(listId)

    fun guideChannels(ids: List<Long>, category: String?, query: String, from: Long) = com.kutlusoy.proiptv.parse.SearchMatch.four(query).let { (w1, w2, w3, w4) -> db.programs().guideChannels(ids, category, w1, w2, w3, w4, from) }

    fun guide(ids: List<Long>, category: String?, from: Long, to: Long, query: String) = com.kutlusoy.proiptv.parse.SearchMatch.four(query).let { (w1, w2, w3, w4) -> db.programs().guide(ids, category, from, to, w1, w2, w3, w4) }

    fun counts(accountId: Long) = combine(
        db.items().countFlow(accountId, Kind.LIVE),
        db.items().countFlow(accountId, Kind.MOVIE),
        db.items().countFlow(accountId, Kind.SERIES),
    ) { a, b, c -> Triple(a, b, c) }

    // the loaded pages of the last list per area are kept, so the list is the same (and at the same place) after the player was closed
    private val pagers = HashMap<Int, Pair<List<Any?>, kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Item>>>>()

    fun pager(kind: Int, ids: List<Long>, category: String?, query: String, flags: Int = 0): kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Item>> {
        val key = listOf(kind, ids, category, query, flags)
        pagers[kind]?.takeIf { it.first == key }?.let { return it.second }
        val flow = Pager(PagingConfig(pageSize = 60, enablePlaceholders = false)) {
            val (w1, w2, w3, w4) = com.kutlusoy.proiptv.parse.SearchMatch.four(query)
            db.items().paging(kind, ids, category, w1, w2, w3, w4, null, null, flags)
        }.flow.cachedIn(viewModelScope)
        pagers[kind] = key to flow
        return flow
    }


    fun favoriteRows(listId: Long, kind: Int) = db.favorites().rows(listId, kind)

    fun stopRecording(id: Long) = (getApplication<Application>() as ProIptvApp).recordings.stop(id)

    fun deleteRecording(id: Long) = run { (getApplication<Application>() as ProIptvApp).recordings.delete(id) }

    /** Opens a channel full screen, for a reminder. */
    fun openChannel(accountId: Long, remoteId: String, title: String) {
        viewModelScope.launch {
            val item = repo.db.items().find(accountId, Kind.LIVE, remoteId) ?: return@launch
            val cat = item.categoryRemoteId.ifEmpty { null }
            val index = repo.db.items().livePosition(listOf(accountId), cat, accountId, item.ord)
            player.value = PlayRequest.Zap(Source.Browse(Kind.LIVE, listOf(accountId), cat, ""), index, direct = true)
            val context = getApplication<Application>()
            android.widget.Toast.makeText(context, context.getString(R.string.reminder_toast, title), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun refreshAll(restart: Boolean = false) {
        accounts.value.filter { it.enabled }.forEach {
            // a load that is running was started with the old setting: it is stopped and started again
            if (restart) repo.sync.cancel(it.id)
            repo.sync.sync(it.id)
        }
    }

    suspend fun channelOf(r: Recording): Item? =
        if (r.channelRemoteId.isEmpty()) null else db.items().find(r.accountId, Kind.LIVE, r.channelRemoteId)

    fun playItem(item: Item) {
        run {
            val target = repo.target(item) ?: return@run
            player.value = PlayRequest.Single(target)
        }
    }

    suspend fun importPlaylist(uri: Uri): String = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val dir = File(ctx.filesDir, "playlists").apply { mkdirs() }
        val file = File(dir, UUID.randomUUID().toString() + ".m3u")
        ctx.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        file.toURI().toString()
    }
}

private const val FILTER_SYNC = 1
