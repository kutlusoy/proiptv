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

sealed interface PlayRequest {
    data class Zap(val source: Source, val index: Int) : PlayRequest
    data class Single(val target: PlayTarget, val fromStart: Boolean = false) : PlayRequest
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as ProIptvApp).repo
    private val db = repo.db

    val accounts = repo.accounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<Account>())
    val syncState = repo.sync.state
    val favLists = db.favorites().lists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val favKeys = db.favorites().keys().map { list -> list.map { Triple(it.accountId, it.kind, it.remoteId) }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val player = MutableStateFlow<PlayRequest?>(null)
    val activeRecordings = (app as ProIptvApp).recordings.active
    val recordings = db.recordings().all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { repo.ensureDefaultList(app.getString(R.string.fav_title)) }
    }

    fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun categories(accountId: Long, kind: Int) = db.items().categories(accountId, kind)

    fun counts(accountId: Long) = combine(
        db.items().countFlow(accountId, Kind.LIVE),
        db.items().countFlow(accountId, Kind.MOVIE),
        db.items().countFlow(accountId, Kind.SERIES),
    ) { a, b, c -> Triple(a, b, c) }

    fun pager(kind: Int, ids: List<Long>, category: String?, query: String) =
        Pager(PagingConfig(pageSize = 60, enablePlaceholders = false)) {
            db.items().paging(kind, ids, category, query)
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
