package com.kutlusoy.proiptv.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowPlacement
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class Tab(val label: String) {
    HOME("nav_home"), LIVE("nav_live"), MOVIES("nav_movies"), SERIES("nav_series"), EPG("nav_epg"), DOWNLOADS("nav_downloads"), RECORDINGS("nav_recordings"), ACCOUNTS("nav_accounts"), SETTINGS("nav_settings"),
}

sealed interface PlayRequest {
    class Live(val entries: List<Entry>, val index: Int) : PlayRequest
    class Single(val item: Playable, val startMs: Long = 0) : PlayRequest
}

class AppState(val store: Store, val scope: CoroutineScope) {
    val library = Library(store)
    val engine by lazy { VlcEngine() }

    var tab by mutableStateOf(Tab.HOME)
    var data by mutableStateOf(LibraryData())
    var loading by mutableStateOf(false)
    var guide by mutableStateOf(Guide())
    var guideLoading by mutableStateOf(false)
    var tick by mutableStateOf(System.currentTimeMillis() / 1000)
    var accounts by mutableStateOf(store.config.accounts)
    var favorites by mutableStateOf(store.config.favorites)
    var player by mutableStateOf<PlayRequest?>(null)
    var placement by mutableStateOf(WindowPlacement.Floating)
    var series by mutableStateOf<Entry?>(null)
    var movie by mutableStateOf<Entry?>(null)
    var controller by mutableStateOf<PlayerController?>(null)

    fun reload() {
        if (loading) return
        loading = true
        scope.launch {
            data = library.load()
            loading = false
            refreshGuide()
        }
    }

    /** Loads the program guide again, also by hand from the accounts and the settings. */
    fun refreshGuide() {
        if (guideLoading) return
        guideLoading = true
        scope.launch {
            try {
                guide = library.loadGuide(data.entries(Kind.LIVE))
            } finally {
                guideLoading = false
            }
        }
    }

    fun saveAccount(a: AccountDto) {
        store.update { c -> c.copy(accounts = if (c.accounts.any { it.id == a.id }) c.accounts.map { if (it.id == a.id) a else it } else c.accounts + a) }
        accounts = store.config.accounts
        reload()
    }

    fun deleteAccount(id: String) {
        store.update { c -> c.copy(accounts = c.accounts.filter { it.id != id }, favorites = c.favorites.filter { it.account != id }) }
        accounts = store.config.accounts
        favorites = store.config.favorites
        reload()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        store.update { c -> c.copy(accounts = c.accounts.map { if (it.id == id) it.copy(enabled = enabled) else it }) }
        accounts = store.config.accounts
        reload()
    }

    val progress = ProgressStore(store.dir)
    var favLists by mutableStateOf(store.config.favoriteLists)
    var listsFor by mutableStateOf<Entry?>(null)
    var confirmRemove by mutableStateOf<Entry?>(null)

    init {
        ensureDefaultLists()
    }

    /** Every area has at least one list. Favorites from older versions (without a list) go into the first list of their area. */
    private fun ensureDefaultLists() {
        store.update { c ->
            var lists = c.favoriteLists
            for (kind in 0..2) if (lists.none { it.kind == kind }) lists = lists + FavoriteListDto(name = Strings.get("fav_title"), kind = kind)
            val favs = c.favorites.map { f -> if (f.listId.isEmpty()) f.copy(listId = lists.first { it.kind == f.kind.coerceIn(0, 2) }.id) else f }
            c.copy(favoriteLists = lists, favorites = favs)
        }
        favLists = store.config.favoriteLists
        favorites = store.config.favorites
    }

    fun listsOf(kind: Int) = favLists.filter { it.kind == kind }

    fun isFavorite(e: Entry) = favorites.any { it.account == e.accountId && it.kind == e.kind && it.remoteId == e.remoteId }

    fun inList(e: Entry, listId: String) = favorites.any { it.listId == listId && it.account == e.accountId && it.kind == e.kind && it.remoteId == e.remoteId }

    fun setInList(e: Entry, listId: String, on: Boolean) {
        store.update { c ->
            val rest = c.favorites.filterNot { it.listId == listId && it.account == e.accountId && it.kind == e.kind && it.remoteId == e.remoteId }
            c.copy(favorites = if (on) rest + FavoriteDto(e.accountId, e.kind, e.remoteId, e.name, listId) else rest)
        }
        favorites = store.config.favorites
    }

    /** The star: one list toggles directly (removing asks first), several lists open the list dialog. */
    fun toggleFavorite(e: Entry) {
        val lists = listsOf(e.kind)
        when {
            lists.size > 1 -> listsFor = e
            isFavorite(e) -> confirmRemove = e
            else -> lists.firstOrNull()?.let { setInList(e, it.id, true) }
        }
    }

    fun removeFromAll(e: Entry) {
        store.update { c -> c.copy(favorites = c.favorites.filterNot { it.account == e.accountId && it.kind == e.kind && it.remoteId == e.remoteId }) }
        favorites = store.config.favorites
    }

    fun createList(kind: Int, name: String): String {
        val list = FavoriteListDto(name = name.trim(), kind = kind)
        store.update { it.copy(favoriteLists = it.favoriteLists + list) }
        favLists = store.config.favoriteLists
        return list.id
    }

    fun renameList(id: String, name: String) {
        store.update { c -> c.copy(favoriteLists = c.favoriteLists.map { if (it.id == id) it.copy(name = name.trim()) else it }) }
        favLists = store.config.favoriteLists
    }

    fun deleteList(id: String) {
        store.update { c -> c.copy(favoriteLists = c.favoriteLists.filter { it.id != id }, favorites = c.favorites.filter { it.listId != id }) }
        favLists = store.config.favoriteLists
        favorites = store.config.favorites
    }

    /** The order of a list is the order of its entries in the settings file. Moves one entry up or down. */
    fun moveFavorite(listId: String, e: Entry, delta: Int) {
        store.update { c ->
            val inList = c.favorites.filter { it.listId == listId }
            val i = inList.indexOfFirst { it.account == e.accountId && it.kind == e.kind && it.remoteId == e.remoteId }
            val j = i + delta
            if (i < 0 || j !in inList.indices) return@update c
            val reordered = inList.toMutableList().also { java.util.Collections.swap(it, i, j) }
            c.copy(favorites = c.favorites.filter { it.listId != listId } + reordered)
        }
        favorites = store.config.favorites
    }

    fun favoriteOrder(listId: String): Map<String, Int> =
        favorites.filter { it.listId == listId }.withIndex().associate { (i, f) -> "${f.account}|${f.kind}|${f.remoteId}" to i }

    val hubs = com.kutlusoy.proiptv.engine.EngineManager(
        java.io.File(System.getProperty("java.io.tmpdir"), "proiptv-timeshift"), scope, capacity = { store.config.bufferMb.coerceIn(64, 4096) * 1024L * 1024L },
    )
    val recordingStore = RecordingStore(store.dir)
    val recordingManager = RecordingManager(recordingStore, hubs, scope, DownloadManager.mediaDir("Recordings"))

    val downloads = DownloadStore(store.dir)
    val downloadManager = DownloadManager(downloads, scope, com.kutlusoy.proiptv.net.Http.client, DownloadManager.mediaDir("Downloads"), ::downloadSource)

    init {
        downloadManager.resume()
    }

    private fun downloadSource(d: DownloadDto): Pair<String, String?>? {
        val a = store.config.accounts.firstOrNull { it.id == d.account } ?: return null
        val pass = Secrets.unprotect(a.password)
        val url = when (d.kind) {
            Kind.EPISODE -> com.kutlusoy.proiptv.net.StreamUrls.episode(a.url, a.username, pass, d.remoteId, d.ext)
            Kind.MOVIE -> data.entries(Kind.MOVIE).firstOrNull { it.accountId == d.account && it.remoteId == d.remoteId }?.url
                ?: com.kutlusoy.proiptv.net.StreamUrls.movie(a.url, a.username, pass, d.remoteId, d.ext.ifBlank { "mp4" })
            else -> return null
        }
        return url to a.userAgent
    }

    fun downloadMovie(e: Entry) = downloadManager.enqueue(
        listOf(DownloadDto(0, e.accountId, Kind.MOVIE, e.remoteId, e.name, e.logo, e.ext.ifBlank { "mp4" })),
    )

    fun downloadEpisodes(series: Entry, episodes: List<com.kutlusoy.proiptv.net.XtreamEpisode>) = downloadManager.enqueue(
        episodes.map {
            DownloadDto(0, series.accountId, Kind.EPISODE, it.id, "${series.name} S${it.season}E${it.number}", it.image ?: series.logo, it.ext.ifBlank { "mp4" },
                seriesId = series.remoteId, seriesName = series.name, season = it.season, episode = it.number)
        },
    )

    /** The finished download of an item, so it plays from the disk. */
    fun downloadOf(account: String, kind: Int, remoteId: String): DownloadDto? {
        downloads.version
        return downloads.all().firstOrNull { it.account == account && it.kind == kind && it.remoteId == remoteId }
    }

    fun localPlayable(d: DownloadDto): Playable? {
        val f = downloadManager.fileOf(d)
        if (d.status != DlStatus.DONE || !f.exists()) return null
        return Playable(d.title, f.toURI().toString().replaceFirst("file:/", "file:///"), null, false, d.account, d.kind, d.remoteId)
    }

    /** Called every few seconds: starts planned recordings when their time has come (the program has to be running). */
    fun checkScheduled() {
        if (recordingStore.all().none { it.status == RecStatus.SCHEDULED }) return
        recordingManager.startDue(System.currentTimeMillis()) { r ->
            val entry = data.entries(Kind.LIVE).firstOrNull { it.accountId == r.account && it.remoteId == r.channelRemoteId } ?: return@startDue null
            val item = library.playable(entry) ?: return@startDue null
            val a = store.config.accounts.firstOrNull { it.id == r.account }
            item.copy(accountNumber = (a?.id ?: "").hashCode().toLong(), channelId = entry.remoteId, maxConnections = data.status[r.account]?.info?.maxConnections)
        }
    }

    /** The live item of a channel as the engine needs it (connection budget, hub key). */
    fun livePlayable(entry: Entry): Playable? {
        val item = library.playable(entry) ?: return null
        val a = store.config.accounts.firstOrNull { it.id == entry.accountId }
        return item.copy(accountNumber = (a?.id ?: "").hashCode().toLong(), channelId = entry.remoteId, maxConnections = data.status[entry.accountId]?.info?.maxConnections)
    }

    fun newAccountId(): String = UUID.randomUUID().toString()

    fun toggleFullscreen() {
        placement = if (placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
    }
}
