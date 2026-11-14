package com.kutlusoy.proiptv.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.kutlusoy.proiptv.ProIptvApp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.DlStatus
import com.kutlusoy.proiptv.data.Download
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.PlayTarget
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
    data class Zap(val source: Source, val index: Int) : PlayRequest
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
    val activeRecordings = (app as ProIptvApp).recordings.active
    val downloads = db.downloads().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val downloadManager get() = (getApplication<Application>() as ProIptvApp).downloads

    fun download(list: List<Download>) = downloadManager.enqueue(list)

    fun retryDownload(id: Long) = downloadManager.retry(id)

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
        viewModelScope.launch { repo.ensureDefaultLists(app.getString(R.string.fav_title)) }
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

    fun guide(ids: List<Long>, category: String?, from: Long, to: Long, query: String) = db.programs().guide(ids, category, from, to, query)

    fun counts(accountId: Long) = combine(
        db.items().countFlow(accountId, Kind.LIVE),
        db.items().countFlow(accountId, Kind.MOVIE),
        db.items().countFlow(accountId, Kind.SERIES),
    ) { a, b, c -> Triple(a, b, c) }

    fun pager(kind: Int, ids: List<Long>, category: String?, query: String) =
        Pager(PagingConfig(pageSize = 60, enablePlaceholders = false)) {
            db.items().paging(kind, ids, category, query, null, null)
        }.flow


    fun favoriteRows(listId: Long, kind: Int) = db.favorites().rows(listId, kind)

    fun stopRecording(id: Long) = (getApplication<Application>() as ProIptvApp).recordings.stop(id)

    fun deleteRecording(id: Long) = run { (getApplication<Application>() as ProIptvApp).recordings.delete(id) }

    fun refreshAll() {
        accounts.value.filter { it.enabled }.forEach { repo.sync.sync(it.id) }
    }

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
