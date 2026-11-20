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
    /** [query] is the search of the list the player was started from (null when the list has no search), [origin] the list without that search, [allScope] whether that list is all channels of all accounts. */
    class Live(val entries: List<Entry>, val index: Int, val query: String? = null, val origin: List<Entry> = emptyList(), val allScope: Boolean = false) : PlayRequest
    /** [following] are the next episodes of the season (resolved when it is their turn): with autoplay the next one starts when an episode has ended. */
    class Single(val item: Playable, val startMs: Long = 0, val following: List<() -> Playable?> = emptyList()) : PlayRequest
}

class AppState(val store: Store, val scope: CoroutineScope) {
    val library = Library(store)
    // two players: the big picture with the sound and, for the picture in picture, a second one that plays quietly. A swap only changes their roles,
    // so no connection is built again.
    private val firstEngine = lazy { VlcEngine() }
    private val secondEngine = lazy { VlcEngine("vlc-pip.log", heal = false) }
    private var firstIsMain by mutableStateOf(true)

    /** The player of the big picture. */
    val engine: VlcEngine get() = if (firstIsMain) firstEngine.value else secondEngine.value

    /** The player of the small picture. */
    val cast = CastClient()
    val pipEngine: VlcEngine get() = if (firstIsMain) secondEngine.value else firstEngine.value

    fun swapEngines() { firstIsMain = !firstIsMain }

    /** Releases the player that is not the main one (the main one is released by the caller), when it was created. */
    fun releasePip() {
        val other = if (firstIsMain) secondEngine else firstEngine
        if (other.isInitialized()) other.value.release()
    }

    /**
     * A second channel needs a second connection: that is possible with several accounts, or with an account that allows more than one. An account
     * that says nothing about its connections (a playlist) can be tried, the provider decides.
     */
    val pipAvailable: Boolean get() = accounts.count { it.enabled } > 1 || data.status.values.any { (it.info?.maxConnections ?: 2) > 1 }

    var tab by mutableStateOf(Tab.HOME)

    init {
        // the mute is kept for the next start
        AudioState.muted = store.config.muted
        AudioState.onChange = { muted -> store.update { it.copy(muted = muted) } }
    }

    /** True while a text field has the focus, so the keyboard shortcuts of the player step aside. */
    var typing by mutableStateOf(false)
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

    /** The intro at the start of the program. */
    var splash by mutableStateOf(store.config.intro)

    /** What a list showed when the player was opened, so it looks the same after the player was closed: the search text per area and the scroll position per list view. */
    val browseQuery = HashMap<Int, String>()

    /** The genre that was chosen per area (series), so it is still there after a detail page. */
    val browseGenre = HashMap<Int, String?>()
    val scrollMemory = HashMap<String, IntArray>()

    fun reload() {
        if (loading) return
        loading = true
        scope.launch {
            data = library.load()
            loading = false
            reconcileFavorites(data)
            refreshGuide()
        }
    }

    /**
     * After the lists were loaded: a favorite has to point to the channel it was made for. When the provider has removed it, or has given its id
     * to another channel (the name is not the same any more), it follows the channel of the same name or is removed. The same for the last channels
     * and the reminders. Accounts that did not load, and kinds that came back empty, are not touched.
     */
    private fun reconcileFavorites(d: LibraryData) {
        val ok = store.config.accounts.filter { it.enabled && it.id !in d.errors }.map { it.id }.toSet()
        fun loaded(account: String, kind: Int) = account in ok && d.entries(kind).any { it.accountId == account }
        val byKey = HashMap<String, Entry>()
        val matchers = HashMap<String, com.kutlusoy.proiptv.parse.FavoriteMatch>()
        val entriesOf = HashMap<String, Entry>()
        for (kind in 0..2) {
            for (e in d.entries(kind)) byKey[e.key] = e
            for ((account, list) in d.entries(kind).groupBy { it.accountId }) {
                matchers["$account|$kind"] = com.kutlusoy.proiptv.parse.FavoriteMatch(list.map { com.kutlusoy.proiptv.parse.MatchCandidate(it.remoteId, it.name, it.epgId) })
                list.forEach { entriesOf["${it.accountId}|${it.kind}|${it.remoteId}"] = it }
            }
        }
        val same = com.kutlusoy.proiptv.parse.StableId::sameName
        val kept = ArrayList<FavoriteDto>()
        var favoritesChanged = false
        for (f in store.config.favorites) {
            if (!loaded(f.account, f.kind)) { kept += f; continue }
            // only the channels of the same account are considered, a favorite never moves to another account
            val found = matchers["${f.account}|${f.kind}"]?.resolve(f.remoteId, f.name, f.epgId)
            val entry = found?.let { entriesOf["${f.account}|${f.kind}|${it.remoteId}"] }
            if (entry == null) {
                // channels of switched off categories are not in the lists, their favorites stay for the day the category is switched on again
                if (found == null && f.kind == Kind.LIVE && accounts.firstOrNull { it.id == f.account }?.hiddenLive?.isNotEmpty() == true) { kept += f; continue }
                favoritesChanged = true
                continue
            }
            val updated = f.copy(remoteId = entry.remoteId, name = entry.name, epgId = entry.epgId ?: f.epgId)
            if (updated != f) favoritesChanged = true
            if (kept.none { it.listId == updated.listId && it.account == updated.account && it.kind == updated.kind && it.remoteId == updated.remoteId }) kept += updated
        }
        val history = store.config.history.filter { key ->
            val account = key.substringBefore('|')
            !loaded(account, Kind.LIVE) || byKey.containsKey(key)
        }
        val reminders = store.config.reminders.mapNotNull { r ->
            if (!loaded(r.account, Kind.LIVE)) return@mapNotNull r
            val current = byKey["${r.account}|${Kind.LIVE}|${r.channelId}"]
            if (current != null && same(current.name, r.channelName)) return@mapNotNull r
            val found = matchers["${r.account}|${Kind.LIVE}"]?.resolve(r.channelId, r.channelName, null) ?: return@mapNotNull null
            r.copy(channelId = found.remoteId, channelName = found.name)
        }
        if (favoritesChanged || history.size != store.config.history.size || reminders != store.config.reminders) {
            store.update { c -> c.copy(favorites = kept, history = history, reminders = reminders) }
            favorites = store.config.favorites
            this.history = store.config.history
            this.reminders = store.config.reminders
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

    /** Backup as a file text, in the format that the Android app writes as well. Account passwords only with a password. */
    fun createBackup(password: String?): String {
        val c = store.config
        val keys = c.accounts.associate { it.id to com.kutlusoy.proiptv.data.BackupCodec.accountKey(it.type, it.url, it.username) }
        val lists = c.favoriteLists.map { l ->
            com.kutlusoy.proiptv.data.BackupList(
                l.name,
                c.favorites.filter { it.listId == l.id }.mapNotNull { f -> keys[f.account]?.let { com.kutlusoy.proiptv.data.BackupFavorite(it, f.kind, f.remoteId, f.name) } },
            )
        }
        val withPasswords = !password.isNullOrEmpty()
        val data = com.kutlusoy.proiptv.data.BackupData(
            accounts = c.accounts.map {
                com.kutlusoy.proiptv.data.BackupAccount(
                    it.name, it.type, it.url, it.username, if (withPasswords) Secrets.unprotect(it.password) else null,
                    it.epgUrl, it.userAgent, "auto", it.enabled, true, true,
                    hiddenLive = it.hiddenLive.map { id -> com.kutlusoy.proiptv.data.BackupHidden(id, data.allCategories(Kind.LIVE).firstOrNull { c -> c.accountId == it.id && c.id == id }?.name ?: id) },
                )
            },
            lists = lists, timeshift = c.timeshift, bufferMb = c.bufferMb,
        )
        return com.kutlusoy.proiptv.data.BackupCodec.encode(data, password)
    }

    /** Adds the accounts and favorites of a backup that are not there yet. Returns the number of new accounts and favorites. */
    fun restoreBackup(text: String, password: String?): Pair<Int, Int> {
        val codec = com.kutlusoy.proiptv.data.BackupCodec
        val data = codec.decode(text, password)
        val ids = store.config.accounts.associate { codec.accountKey(it.type, it.url, it.username) to it.id }.toMutableMap()
        var added = 0
        for (a in data.accounts) {
            val key = codec.accountKey(a.type, a.url, a.username)
            if (key in ids) continue
            val id = newAccountId()
            val dto = AccountDto(id = id, name = a.name, type = a.type, url = a.url, username = a.username, password = Secrets.protect(a.password.orEmpty()), userAgent = a.userAgent, enabled = a.enabled, epgUrl = a.epgUrl, hiddenLive = a.hiddenLive.map { it.id })
            store.update { c -> c.copy(accounts = c.accounts + dto) }
            ids[key] = id
            added++
        }
        accounts = store.config.accounts
        var restored = 0
        for (l in data.lists) {
            for (e in l.entries) {
                val accountId = ids[e.account] ?: continue
                val area = if (e.kind in 0..2) e.kind else Kind.LIVE
                val listId = listsOf(area).firstOrNull { it.name == l.name }?.id ?: createList(area, l.name)
                if (favorites.any { it.listId == listId && it.account == accountId && it.kind == e.kind && it.remoteId == e.remoteId }) continue
                store.update { c -> c.copy(favorites = c.favorites + FavoriteDto(accountId, e.kind, e.remoteId, e.name, listId)) }
                favorites = store.config.favorites
                restored++
            }
        }
        store.update { it.copy(timeshift = data.timeshift, bufferMb = data.bufferMb) }
        if (added > 0) reload()
        return added to restored
    }

    fun deleteAccount(id: String) {
        // everything that belongs to the account goes with it: favorites, last channels, progress, reminders and planned recordings
        store.update { c ->
            c.copy(
                accounts = c.accounts.filter { it.id != id }, favorites = c.favorites.filter { it.account != id },
                history = c.history.filterNot { it.startsWith("$id|") }, reminders = c.reminders.filter { it.account != id },
            )
        }
        progress.removeAccount(id)
        recordingStore.all().filter { it.account == id && it.status == RecStatus.SCHEDULED }.forEach { recordingStore.remove(it.id) }
        accounts = store.config.accounts
        favorites = store.config.favorites
        history = store.config.history
        reminders = store.config.reminders
        reload()
    }

    /** The live categories that are switched off for an account; the lists are loaded again without their channels. */
    var videoBrightness by androidx.compose.runtime.mutableIntStateOf(store.config.videoBrightness)
        private set
    var videoZoom by androidx.compose.runtime.mutableIntStateOf(store.config.videoZoom)
        private set

    fun changeBrightness(value: Int) {
        videoBrightness = value.coerceIn(-100, 100)
        store.update { c -> c.copy(videoBrightness = videoBrightness) }
    }

    fun changeZoom(value: Int) {
        videoZoom = value.coerceIn(100, 300)
        store.update { c -> c.copy(videoZoom = videoZoom) }
    }

    fun setHiddenLive(id: String, hidden: List<String>) {
        store.update { c -> c.copy(accounts = c.accounts.map { if (it.id == id) it.copy(hiddenLive = hidden) else it }) }
        accounts = store.config.accounts
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

    /** The name of the account when more than one is switched on, so lists and cards say where an entry comes from. */
    fun accountLabel(id: String): String? = if (accounts.count { it.enabled } > 1) accounts.firstOrNull { it.id == id }?.name else null

    fun listsOf(kind: Int) = favLists.filter { it.kind == kind }

    var history by mutableStateOf(store.config.history)

    fun recordHistory(e: Entry) {
        if (e.kind != Kind.LIVE) return
        store.update { c -> c.copy(history = (listOf(e.key) + c.history.filter { it != e.key }).take(24)) }
        history = store.config.history
    }

    fun isFavorite(e: Entry) = favorites.any { it.account == e.accountId && it.kind == e.kind && it.remoteId == e.remoteId }

    fun inList(e: Entry, listId: String) = favorites.any { it.listId == listId && it.account == e.accountId && it.kind == e.kind && it.remoteId == e.remoteId }

    fun setInList(e: Entry, listId: String, on: Boolean) {
        store.update { c ->
            val rest = c.favorites.filterNot { it.listId == listId && it.account == e.accountId && it.kind == e.kind && it.remoteId == e.remoteId }
            c.copy(favorites = if (on) rest + FavoriteDto(e.accountId, e.kind, e.remoteId, e.name, listId, e.epgId) else rest)
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
        java.io.File(System.getProperty("java.io.tmpdir"), "proiptv-timeshift"), scope, capacity = {
            val dir = java.io.File(System.getProperty("java.io.tmpdir"), "proiptv-timeshift")
            // never more than a third of the free space, so a long session cannot fill the disk
            minOf(store.config.bufferMb.coerceIn(64, 4096) * 1024L * 1024L, dir.usableSpace.takeIf { it > 0 }?.div(3) ?: Long.MAX_VALUE).coerceAtLeast(64L * 1024 * 1024)
        },
    )
    val recordingStore = RecordingStore(store.dir)
    val recordingManager = RecordingManager(recordingStore, hubs, scope, { DownloadManager.mediaDir("Recordings") })

    val downloads = DownloadStore(store.dir)
    init { DownloadManager.chosenRoot = { store.config.storageDir?.let { java.io.File(it) }?.takeIf { it.isDirectory } } }
    val downloadManager = DownloadManager(downloads, scope, com.kutlusoy.proiptv.net.Http.client, { DownloadManager.mediaDir("Downloads") }, ::downloadSource)

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
    var reminderNotice by mutableStateOf<String?>(null)
    var reminders by mutableStateOf(store.config.reminders)

    fun removeReminder(r: ReminderDto) {
        store.update { c -> c.copy(reminders = c.reminders - r) }
        reminders = store.config.reminders
    }

    fun hasReminder(e: Entry, startMs: Long) = reminders.any { it.account == e.accountId && it.channelId == e.remoteId && it.startMs == startMs }

    fun toggleReminder(e: Entry, title: String, startMs: Long) {
        store.update { c ->
            val rest = c.reminders.filterNot { it.account == e.accountId && it.channelId == e.remoteId && it.startMs == startMs }
            c.copy(reminders = if (rest.size == c.reminders.size) rest + ReminderDto(e.accountId, e.remoteId, e.name, title, startMs) else rest)
        }
        reminders = store.config.reminders
    }

    /** One minute before a show with a reminder the player jumps to its channel. */
    fun checkReminders() {
        val now = System.currentTimeMillis()
        val due = reminders.filter { it.startMs - 60_000 <= now }
        if (due.isEmpty()) return
        store.update { c -> c.copy(reminders = c.reminders - due.toSet()) }
        reminders = store.config.reminders
        val last = due.filter { it.startMs > now - 5 * 60_000 }.maxByOrNull { it.startMs } ?: return
        val entry = data.entries(Kind.LIVE).firstOrNull { it.accountId == last.account && it.remoteId == last.channelId } ?: return
        if (compact) setCompact(false, windowRef?.bounds)
        player = PlayRequest.Live(listOf(entry), 0)
        reminderNotice = Strings.get("reminder_toast", last.title)
        windowRef?.let { w -> runCatching { w.toFront(); w.requestFocus() } }
    }

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

    var windowRef: java.awt.Window? = null
    var compact by mutableStateOf(false)
    var pinned by mutableStateOf(true)
    private var normalBounds: java.awt.Rectangle? = null
    private var beforeFullscreen = WindowPlacement.Floating
    var pendingBounds: java.awt.Rectangle? = null

    /** The small borderless window for watching next to other work: always on top (pin), movable, resizable. */
    fun setCompact(on: Boolean, bounds: java.awt.Rectangle?) {
        if (on == compact) return
        if (on) {
            normalBounds = bounds
            if (placement == WindowPlacement.Fullscreen) placement = beforeFullscreen
            // the small window comes back where it was and as big as it was
            pendingBounds = store.config.compactBounds?.toRectangle()?.takeIf { onScreen(it) } ?: compactBounds(0.25, null)
        } else {
            pendingBounds = normalBounds
        }
        compact = on
    }

    private var boundsBeforeFullscreen: java.awt.Rectangle? = null

    /**
     * Full screen fills the whole screen above the task bar (the window gets the size of the screen and stays on top), the key F or Esc brings the window back
     * as it was: same place and size, or maximized again.
     */
    fun toggleFullscreen() {
        if (placement == WindowPlacement.Fullscreen) {
            placement = beforeFullscreen
            pendingBounds = if (beforeFullscreen == WindowPlacement.Maximized) null else boundsBeforeFullscreen
            return
        }
        // the maximize button of the title bar does not go through this state, so the window itself is asked
        val maximized = ((windowRef as? java.awt.Frame)?.extendedState ?: 0) and java.awt.Frame.MAXIMIZED_BOTH == java.awt.Frame.MAXIMIZED_BOTH
        beforeFullscreen = if (maximized || placement == WindowPlacement.Maximized) WindowPlacement.Maximized else WindowPlacement.Floating
        boundsBeforeFullscreen = if (compact) normalBounds else windowRef?.bounds
        if (compact) compact = false
        val screen = windowRef?.graphicsConfiguration?.bounds ?: java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().screenSize)
        pendingBounds = screen
        placement = WindowPlacement.Fullscreen
    }
}
