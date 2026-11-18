package com.kutlusoy.proiptv.desktop

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.kutlusoy.proiptv.ui.PlayerGlyph
import com.kutlusoy.proiptv.ui.RoundControl
import com.kutlusoy.proiptv.ui.TimeBar
import com.kutlusoy.proiptv.ui.hoursMinutes

/** Holds what is playing right now, so the screen and the keyboard shortcuts of the window use the same functions. */
class PlayerController(private val state: AppState, private val request: PlayRequest) {
    private val engine get() = state.engine
    var started = false
    fun isFor(r: PlayRequest) = r === request
    var index by mutableIntStateOf(if (request is PlayRequest.Live) request.index else 0)
    var title by mutableStateOf("")
    var paused by mutableStateOf(false)
    var volume by mutableIntStateOf(state.store.config.volume)
    var position by mutableLongStateOf(0)
    var length by mutableLongStateOf(0)
    var failed by mutableStateOf<String?>(null)
    var reconnecting by mutableStateOf(false)
    var listOpen by mutableStateOf(false)

    val live: Boolean get() = request is PlayRequest.Live
    /** The channels that zapping and the next and previous keys walk through. It becomes the list the user clicked a channel in. */
    var playEntries by mutableStateOf((request as? PlayRequest.Live)?.entries.orEmpty())

    /**
     * The search of the channel list lives here, so it is still there when the list is opened again. When the player was started from a list with a
     * search, that search is the one of the panel (and a change in the panel is handed back to the list).
     */
    private val liveRequest = request as? PlayRequest.Live
    var searchOpen by mutableStateOf(liveRequest?.query?.isNotBlank() == true)
    private var searchTextState by mutableStateOf(liveRequest?.query.orEmpty())
    var searchText: String
        get() = searchTextState
        set(value) {
            searchTextState = value
            if (liveRequest?.query != null) state.browseQuery[Kind.LIVE] = value
        }

    /** The playing list is the result of a search, and the list that the search started from. */
    var playFromSearch by mutableStateOf(liveRequest?.query?.isNotBlank() == true)
    var searchOrigin: List<Entry> = liveRequest?.origin.orEmpty()

    /** The channel list shows the categories and the channels side by side. */
    var columnMode by mutableStateOf(state.store.config.playerColumns)
    fun setColumns(on: Boolean) {
        columnMode = on
        state.store.update { c -> c.copy(playerColumns = on) }
    }

    /** The search looks through all channels. A search that comes from a list starts inside that list (all channels only when that list is "All"), whatever was chosen last time. */
    var searchAll by mutableStateOf(if (liveRequest?.query.isNullOrBlank()) state.store.config.playerSearchAll else liveRequest?.allScope == true)

    /** Empties the search and closes it everywhere: the list goes back to the one that the search started from. */
    fun clearSearch() {
        searchText = ""
        searchOpen = false
        if (playFromSearch) {
            val key = playEntries.getOrNull(index)?.key
            val origin = searchOrigin
            if (origin.isNotEmpty()) {
                playEntries = origin
                index = origin.indexOfFirst { it.key == key }.coerceAtLeast(0)
            }
            playFromSearch = false
            searchOrigin = emptyList()
        }
    }

    val canZap: Boolean get() = request is PlayRequest.Live && playEntries.size > 1

    var current: Playable? = null
        private set
    private var lastSaved = 0L
    private var lease: com.kutlusoy.proiptv.engine.HubLease? = null

    /** How far behind the live picture the player is, in ms. Only with the shared buffer. */
    var behindMs by mutableLongStateOf(0)
    var bufferedMs by mutableLongStateOf(0)
    var recordingId by mutableStateOf<Long?>(null)
    val timeShift: Boolean get() = lease != null

    private fun releaseHub(now: Boolean = false) {
        if (now) lease?.closeNow() else lease?.close()
        lease = null
        behindMs = 0
        bufferedMs = 0
    }

    fun start() {
        saveProgress()
        val item = when (request) {
            is PlayRequest.Single -> request.item
            is PlayRequest.Live -> playEntries.getOrNull(index)?.let { state.library.playable(it) }
        }
        if (item == null) { failed = "No address"; return }
        current = item
        title = item.title
        paused = false
        failed = null
        // another channel: the old connection is cut at once
        releaseHub(now = true)
        if (request is PlayRequest.Live) {
            val entry = playEntries[index]
            state.recordHistory(entry)
            val a = state.store.config.accounts.firstOrNull { it.id == entry.accountId }
            val live = item.copy(accountNumber = (a?.id ?: "").hashCode().toLong(), channelId = entry.remoteId, maxConnections = state.data.status[entry.accountId]?.info?.maxConnections)
            current = live
            recordingId = state.recordingManager.isRecording(live.account, live.channelId)
            if (state.store.config.timeshift && !item.url.contains(".m3u8", ignoreCase = true)) {
                try {
                    val l = state.hubs.acquire(live.accountNumber, live.channelId, live.title, live.maxConnections, com.kutlusoy.proiptv.net.Upstream.opener(live.url, live.userAgent))
                    lease = l
                    engine.playReader(l.hub.reader(), live.title, volume)
                    return
                } catch (e: com.kutlusoy.proiptv.engine.BudgetExceeded) {
                    failed = Strings.get("player_no_connection", e.limit, e.openTitles.joinToString())
                    return
                }
            }
        }
        engine.play(item, volume, if (request is PlayRequest.Single) request.startMs else 0)
    }

    /** Goes back (negative) or forward in the buffered live picture. */
    fun shift(deltaMs: Long) {
        val l = lease ?: return
        val buffer = l.hub.buffer
        val now = System.currentTimeMillis()
        val target = (buffer.timeAtOffset(engine.readerPosition) + deltaMs).coerceIn(buffer.oldestTime(), now)
        val offset = if (target >= now - 1500) buffer.end else buffer.offsetAtTime(target)
        engine.playReader(l.hub.reader(offset), title, volume)
        paused = false
    }

    fun goLive() {
        val l = lease ?: return
        engine.playReader(l.hub.reader(l.hub.buffer.end), title, volume)
        paused = false
    }

    /** Records the current channel. stopAfterMs null means until it is stopped. Returns an error text or null. */
    fun record(stopAfterMs: Long?, program: String? = null): String? {
        val item = current ?: return null
        val show = program ?: playEntries.getOrNull(index)?.let { state.guide.nowNext(it.accountId, it.epgId, System.currentTimeMillis() / 1000).first?.title }
        return try {
            recordingId = state.recordingManager.start(item, stopAfterMs?.let { System.currentTimeMillis() + it }, show)
            null
        } catch (e: com.kutlusoy.proiptv.engine.BudgetExceeded) {
            Strings.get("player_no_connection", e.limit, e.openTitles.joinToString())
        } catch (e: NotEnoughSpace) {
            Strings.get("player_no_space")
        } catch (e: Exception) {
            e.message ?: "error"
        }
    }

    fun stopRecording() {
        recordingId?.let { state.recordingManager.stop(it) }
        recordingId = null
    }

    /** Remembers where a movie or episode stopped. Watched means that 92 percent were seen. */
    fun saveProgress() {
        val item = current ?: return
        if (item.live || item.kind < 0 || item.account.isEmpty()) return
        val pos = engine.time()
        val len = engine.length()
        if (len <= 0) return
        val watched = pos.toDouble() / len > 0.92
        val old = state.progress.get(item.account, item.kind, item.remoteId)
        state.progress.save(
            ProgressDto(item.account, item.kind, item.remoteId, if (watched) 0 else pos, len, watched || old?.watched == true && pos < 5000, System.currentTimeMillis(), item.seriesRemoteId),
        )
    }

    /** The channel in the small picture (without sound) and the message when it could not be opened. */
    var pipEntry by mutableStateOf<Entry?>(null)
    var pipMessage by mutableStateOf<String?>(null)
    private var pipLease: com.kutlusoy.proiptv.engine.HubLease? = null

    /** Opens a channel in the small picture. It uses a second connection, so it can fail with the limit of the account. */
    fun openPip(entry: Entry) {
        val item = state.livePlayable(entry)
        if (item == null) { pipMessage = Strings.get("player_failed"); return }
        closePip()
        try {
            val l = state.hubs.acquire(item.accountNumber, item.channelId, item.title, item.maxConnections, com.kutlusoy.proiptv.net.Upstream.opener(item.url, item.userAgent))
            pipLease = l
            pipEntry = entry
            state.pipEngine.playReader(l.hub.reader(), item.title, 0)
        } catch (e: com.kutlusoy.proiptv.engine.BudgetExceeded) {
            pipMessage = Strings.get("player_no_connection", e.limit, e.openTitles.joinToString())
        }
    }

    fun closePip() {
        if (pipEntry == null && pipLease == null) return
        state.pipEngine.stop()
        pipLease?.closeNow()
        pipLease = null
        pipEntry = null
    }

    /** The two channels change places: the small one becomes the big one with sound, the big one goes into the small picture. */
    fun swapPip() {
        val small = pipEntry ?: return
        val big = playEntries.getOrNull(index) ?: return
        closePip()
        val at = playEntries.indexOfFirst { it.key == small.key }
        if (at >= 0) index = at else { playEntries = listOf(small) + playEntries; index = 0 }
        start()
        openPip(big)
    }

    /** Plays the channel at this position of [list]. From now on zapping walks through that list. */
    fun play(list: List<Entry>, i: Int, fromSearch: Boolean = false, origin: List<Entry> = emptyList()) {
        if (request !is PlayRequest.Live || i !in list.indices) return
        playEntries = list
        playFromSearch = fromSearch
        searchOrigin = origin
        index = i
        // the channel browser stays open: the channel plays in the small window, the menu button leads to the big picture
        start()
    }

    val entries: List<Entry> get() = playEntries

    fun zap(delta: Int) {
        if (request !is PlayRequest.Live || playEntries.size < 2) return
        index = ((index + delta) % playEntries.size + playEntries.size) % playEntries.size
        start()
    }

    fun togglePause() { paused = !paused; engine.pause(paused) }
    fun seekBy(ms: Long) { if (!live) engine.seek(engine.time() + ms) }

    fun changeVolume(percent: Int) {
        volume = percent.coerceIn(0, 200)
        engine.setVolume(volume)
        state.store.update { it.copy(volume = volume) }
    }

    fun stop() {
        state.typing = false
        closePip()
        saveProgress()
        engine.stop()
        // leaving the player: the provider connection is closed at once
        releaseHub(now = true)
        state.player = null
        state.controller = null
        if (state.compact) state.setCompact(false, null)
        if (state.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen) state.toggleFullscreen()
    }

    fun poll() {
        position = engine.time()
        length = engine.length()
        lease?.let { l ->
            val buffer = l.hub.buffer
            val now = System.currentTimeMillis()
            behindMs = (now - buffer.timeAtOffset(engine.readerPosition)).coerceAtLeast(0)
            bufferedMs = now - buffer.oldestTime()
        }
        lease?.hub?.let { hub ->
            reconnecting = hub.state.value == com.kutlusoy.proiptv.engine.HubState.RECONNECTING
            if (hub.state.value == com.kutlusoy.proiptv.engine.HubState.FAILED && failed == null) {
                // the provider did not let the stream through: say why in words
                failed = when (com.kutlusoy.proiptv.net.ProviderProblems.fromMessage(hub.lastError)) {
                    com.kutlusoy.proiptv.net.ProviderProblem.AUTH -> Strings.get("player_err_auth")
                    com.kutlusoy.proiptv.net.ProviderProblem.LIMIT -> Strings.get("player_err_limit")
                    com.kutlusoy.proiptv.net.ProviderProblem.BLOCKED -> Strings.get("player_err_blocked")
                    com.kutlusoy.proiptv.net.ProviderProblem.NOT_FOUND -> Strings.get("player_err_notfound")
                    else -> Strings.get("player_failed")
                }
            }
        }
        recordingId?.let { id -> if (state.recordingStore.get(id)?.status != RecStatus.RECORDING) recordingId = null }
        if (!live && length > 0 && System.currentTimeMillis() - lastSaved > 10_000) {
            lastSaved = System.currentTimeMillis()
            saveProgress()
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(state: AppState, controller: PlayerController) {
    val engine = state.engine
    LaunchedEffect(controller) { if (!controller.started) { controller.started = true; controller.start() } }
    LaunchedEffect(controller) {
        while (true) {
            controller.poll()
            delay(500)
        }
    }
    DisposableEffect(controller) { onDispose { if (state.player == null) engine.stop() } }
    val fullscreen = state.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen

    var barVisible by remember { mutableStateOf(true) }
    var lastMove by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(fullscreen) {
        barVisible = true
        while (fullscreen) {
            delay(500)
            barVisible = controller.listOpen || System.currentTimeMillis() - lastMove < 3000
        }
        barVisible = true
    }

    val video: @Composable (Modifier) -> Unit = { modifier ->
        if (!engine.available) {
            Text(engine.error ?: "libVLC not found", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
        } else {
            VideoSurface(engine, modifier, onDoubleClick = {
                // in the channel browser a double click makes the stream big (like the menu button), in the big picture it makes the window borderless
                if (controller.listOpen && controller.live && !state.compact) controller.listOpen = false else state.toggleFullscreen()
            }, onScroll = { dy -> controller.changeVolume(controller.volume - dy * 5) })
        }
        controller.failed?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp)) }
        if (controller.reconnecting && controller.failed == null) Text(t("player_retrying"), Modifier.background(Color(0xAA000000)).padding(12.dp), color = Color.White)
    }

    // 0 compact, 1 fullscreen, 2 window, 3 channel browser: the change between them fades instead of jumping
    val layoutMode = when {
        state.compact -> 0
        controller.listOpen && controller.live -> 3
        fullscreen -> 1
        else -> 2
    }
    Box(Modifier.fillMaxSize()) {
    androidx.compose.animation.AnimatedContent(
        layoutMode, Modifier.fillMaxSize(),
        transitionSpec = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(260)) },
        label = "player layout",
    ) { mode ->
    if (mode == 3) {
        // the channel browser: list on the left, the running channel as a small preview with its info card, the guide below
        Column(Modifier.fillMaxSize().background(Palette.Velvet)) {
            ChannelBrowser(state, controller, video, Modifier.weight(1f))
            ControlBar(state, controller, Modifier, floating = false)
        }
    } else if (mode == 0) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { video(Modifier.fillMaxSize()) }
    } else if (mode == 1) {
        // the picture fills the screen, the controls float above it and fade away when the mouse rests
        Box(
            Modifier.fillMaxSize().background(Color.Black).onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move) {
                lastMove = System.currentTimeMillis()
                barVisible = true
            },
            contentAlignment = Alignment.Center,
        ) {
            video(Modifier.fillMaxSize())
            if (barVisible) {
                Column(Modifier.align(Alignment.TopStart).background(Color(0x99000000)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(controller.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    NowNextLine(state, controller)
                }
                ControlBar(state, controller, Modifier.align(Alignment.BottomCenter), floating = true)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(controller.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    NowNextLine(state, controller)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) { video(Modifier.fillMaxSize()) }
            ControlBar(state, controller, Modifier, floating = false)
        }
    }
    }
    // the small picture lies above the big one (not in the channel browser, which has its own small window)
    if (layoutMode == 1 || layoutMode == 2) PipWindow(state, controller, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 84.dp))
    controller.pipMessage?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { controller.pipMessage = null },
            text = { Text(msg) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { controller.pipMessage = null }) { Text(t("action_close")) } },
        )
    }
    }
}

/** The browser of the player: the channel list at the left, at the right the running channel as a preview next to its info card, and its guide below. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ChannelBrowser(state: AppState, controller: PlayerController, video: @Composable (Modifier) -> Unit, modifier: Modifier) {
    val playing = controller.playEntries.getOrNull(controller.index)
    // the guide shows the channel the mouse touched last, at the start the running one
    var hovered by remember { mutableStateOf<Entry?>(null) }
    // a new channel shows its own guide and info card
    androidx.compose.runtime.LaunchedEffect(playing?.key) { hovered = null }
    val shownEntry = hovered ?: playing
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm") }
    val nowSecs = state.tick
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = (maxWidth * 0.32f).coerceAtLeast(300.dp).coerceAtMost(maxWidth * 0.6f)
        val wide = (maxWidth * 0.6f).coerceAtLeast(520.dp).coerceAtMost(maxWidth * 0.78f)
        // the list grows to make room for the categories next to the channels
        val left by androidx.compose.animation.core.animateDpAsState(if (controller.columnMode) wide else narrow, androidx.compose.animation.core.tween(220), label = "list width")
        Row(Modifier.fillMaxSize()) {
            ChannelPanel(state, controller, Modifier.width(left).fillMaxHeight(), onHover = { hovered = it })
            // with the mouse over this side the guide of the running channel is shown again (over the list it follows the channel under the mouse)
            Column(Modifier.weight(1f).fillMaxHeight().onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Enter) { hovered = null }) {
                Row(Modifier.weight(0.5f).fillMaxWidth()) {
                    Box(Modifier.weight(0.62f).fillMaxHeight().background(Color.Black), contentAlignment = Alignment.Center) { video(Modifier.fillMaxSize()) }
                    Column(Modifier.weight(0.38f).fillMaxHeight().background(Palette.Surface).padding(16.dp), verticalArrangement = Arrangement.Center) {
                        Text(
                            t("tv_live_badge"), Modifier.background(Palette.Wine, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Color.White, style = MaterialTheme.typography.labelLarge,
                        )
                        Text(playing?.name.orEmpty(), Modifier.padding(top = 8.dp), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 5, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        val now = playing?.let { state.guide.nowNext(it.accountId, it.epgId, nowSecs).first }
                        if (now != null) {
                            Text(now.title, Modifier.padding(top = 8.dp), color = Palette.WineSoft, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(timeFormat.format(java.util.Date(now.start * 1000)) + " - " + timeFormat.format(java.util.Date(now.end * 1000)), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                val programs = remember(shownEntry, state.guide, nowSecs / 60) {
                    shownEntry?.epgId?.let { id -> state.guide.programs[Guide.key(shownEntry.accountId, id)].orEmpty().filter { it.end > nowSecs }.distinctBy { it.start } }.orEmpty()
                }
                androidx.compose.animation.Crossfade(shownEntry?.key, Modifier.weight(0.5f).fillMaxWidth().background(Palette.Surface), animationSpec = androidx.compose.animation.core.tween(200), label = "guide") { _ ->
                  Column(Modifier.fillMaxSize()) {
                    if (shownEntry != null && shownEntry.key != playing?.key) {
                        Text(shownEntry.name, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = Palette.WineSoft, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    if (programs.isEmpty()) {
                        Text(t("epg_none"), Modifier.padding(16.dp), color = Color.White.copy(alpha = 0.7f))
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                            itemsIndexed(programs, key = { _, p -> "${shownEntry?.accountId}|${p.channel}|${p.start}" }) { _, p ->
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    Row {
                                        Text(timeFormat.format(java.util.Date(p.start * 1000)) + " - " + timeFormat.format(java.util.Date(p.end * 1000)), color = Palette.WineSoft, style = MaterialTheme.typography.bodyMedium)
                                        Text("  " + p.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                    p.description?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                  }
                }
            }
        }
    }
}

private class PanelList(val name: String, val isSearch: Boolean = false, val entries: () -> List<Entry>)

/**
 * The channel list at the left edge, bound to the mouse: the header shows the name of the list with arrows to switch between the lists,
 * the magnifier at the bottom opens a search field. With "All channels" the search looks through all accounts and lists.
 * The channel keeps playing while the list is open.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ChannelPanel(state: AppState, controller: PlayerController, modifier: Modifier, onHover: (Entry) -> Unit) {
    val live = state.data.entries(Kind.LIVE)
    val byKey = remember(live) { live.associateBy { it.key } }
    val favLists = state.listsOf(Kind.LIVE)
    val favName = t("fav_title")
    val historyName = t("history_title")
    val allName = t("live_all")
    val currentName = t("player_list_current")
    val searchName = t("player_list_search")
    val otherName = t("live_other")
    val listsAndStart = remember(controller.playEntries, controller.playFromSearch, state.favorites, state.history, favLists, live, state.data.categories(Kind.LIVE)) {
        val out = ArrayList<PanelList>()
        favLists.forEach { l ->
            out += PanelList(if (favLists.size == 1) favName else "\u2605 ${l.name}") {
                val order = state.favoriteOrder(l.id)
                live.filter { state.inList(it, l.id) }.sortedBy { order[it.key] ?: Int.MAX_VALUE }
            }
        }
        out += PanelList(historyName) { state.history.mapNotNull { byKey[it] } }
        out += PanelList(allName) { live }
        state.data.categories(Kind.LIVE).forEach { c ->
            out += PanelList((state.accountLabel(c.accountId)?.let { "$it \u00B7 " } ?: "") + c.name.ifBlank { otherName }) {
                live.filter { it.accountId == c.accountId && it.categoryId == c.id }
            }
        }
        // the list the player was started from stays at its own place, so the arrows lead to the next list of the start screen; a list that is not
        // one of them (a search, a zapping list from elsewhere) is put in front
        val start = controller.playEntries.map { it.key }
        val same = if (controller.playFromSearch) -1 else {
            val exact = out.indexOfFirst { l -> l.entries().map { it.key } == start }
            // the start screen shows only the first 40 channels of a category: such a row belongs to the category that begins with them
            if (exact >= 0 || start.size < 20) exact
            else out.indexOfFirst { l -> l.name != allName && l.name != historyName && l.entries().map { it.key }.take(start.size) == start }
        }
        if (same < 0) out.add(0, PanelList(if (controller.playFromSearch) searchName else currentName, controller.playFromSearch) { controller.playEntries })
        out to (if (same < 0) 0 else same)
    }
    val lists = listsAndStart.first
    var listIndex by remember(lists.size) { mutableIntStateOf(listsAndStart.second) }
    val listNow = lists[listIndex.coerceIn(0, lists.lastIndex)]
    val searchOpen = controller.searchOpen
    val query = controller.searchText
    val everywhere = controller.searchAll
    val setEverywhere: (Boolean) -> Unit = { on -> controller.searchAll = on; state.store.update { c -> c.copy(playerSearchAll = on) } }
    val q = query.trim()
    val shownAll = remember(listNow, listIndex, searchOpen, q, everywhere, live, state.tick / 60) {
        val searching = searchOpen && q.isNotEmpty()
        // inside the list of an earlier search the new search starts again from the list that the earlier one started from
        val base = if (searching && everywhere) live else if (searching && listNow.isSearch) controller.searchOrigin.ifEmpty { listNow.entries() } else listNow.entries()
        // every channel once: the list is keyed by the channel
        (if (!searching) base
        else base.filter { e ->
            com.kutlusoy.proiptv.parse.SearchMatch.matches(e.name + " " + state.guide.nowNext(e.accountId, e.epgId, state.tick).first?.title.orEmpty(), q)
        }).distinctBy { it.key }
    }
    // in the side by side view the fields at the top of the columns filter the categories and the channels of the chosen category
    var categoryFilter by remember { mutableStateOf("") }
    var channelFilter by remember { mutableStateOf("") }
    LaunchedEffect(listIndex) { channelFilter = "" }
    val shown = remember(shownAll, controller.columnMode, channelFilter) {
        val f = channelFilter.trim()
        if (!controller.columnMode || f.isEmpty()) shownAll else shownAll.filter { com.kutlusoy.proiptv.parse.SearchMatch.matches(it.name, f) }
    }
    val playingKey = controller.playEntries.getOrNull(controller.index)?.key
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(listNow, searchOpen, q, everywhere) {
        val at = shownAll.indexOfFirst { it.key == playingKey }
        runCatching { list.animateScrollToItem(if (at > 3) at - 3 else 0) }
    }
    val previousText = t("player_list_previous")
    val nextText = t("player_list_next")
    val searchText = t("player_search")
    val columnsText = t("player_columns")
    val pipOpenText = t("pip_open")
    val typingFocus = Modifier.onFocusChanged { state.typing = it.isFocused }
    // when the panel goes away no field has the focus any more, so the shortcuts of the player work again
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { state.typing = false } }
    val columnsButton: @Composable () -> Unit = {
        IconButton(onClick = { controller.setColumns(!controller.columnMode) }, modifier = Modifier.semantics { contentDescription = columnsText }) { ColumnsIcon(controller.columnMode) }
    }
    val pane: @Composable (Modifier) -> Unit = { paneModifier ->
    Column(paneModifier.background(Palette.Velvet)) {
        // in the side by side view the categories at the left are the choice of the list, so the header with the arrows is not needed
        if (!controller.columnMode) Row(Modifier.fillMaxWidth().height(56.dp).background(Palette.WineDeep), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { listIndex = (listIndex - 1 + lists.size) % lists.size }, enabled = lists.size > 1, modifier = Modifier.semantics { contentDescription = previousText }) {
                Icon(Icons.Filled.KeyboardArrowLeft, null, tint = Color.White)
            }
            androidx.compose.animation.AnimatedContent(
                if (searchOpen && q.isNotEmpty() && everywhere) t("player_search_all") else listNow.name, Modifier.weight(1f),
                transitionSpec = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)) },
                label = "list name",
            ) { name ->
                Text(
                    name, Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
            }
            IconButton(onClick = { listIndex = (listIndex + 1) % lists.size }, enabled = lists.size > 1, modifier = Modifier.semantics { contentDescription = nextText }) {
                Icon(Icons.Filled.KeyboardArrowRight, null, tint = Color.White)
            }
        }
        if (controller.columnMode) {
            TextInput(
                channelFilter, { channelFilter = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).then(typingFocus),
                placeholder = { Text(t("player_filter_channels", shownAll.size)) },
                trailingIcon = { if (channelFilter.isNotEmpty()) IconButton(onClick = { channelFilter = "" }) { Icon(Icons.Filled.Close, t("action_close")) } },
            )
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list) {
            itemsIndexed(shown, key = { _, e -> e.key }) { i, e ->
                val now = state.guide.nowNext(e.accountId, e.epgId, state.tick).first
                Box(Modifier.animateItem()) {
                PipMenu(state.pipAvailable, pipOpenText, { controller.openPip(e) }) {
                Row(
                    Modifier.fillMaxWidth().then(if (e.key == playingKey) Modifier.background(Palette.Wine, RoundedCornerShape(6.dp)) else Modifier)
                        .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Enter) { onHover(e) }
                        .clickable {
                            val searching = searchOpen && q.isNotEmpty()
                            val origin = if (searching && everywhere) live else if (searching && listNow.isSearch) controller.searchOrigin.ifEmpty { listNow.entries() } else listNow.entries()
                            controller.play(shown, i, fromSearch = searching, origin = origin)
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Logo(e.logo, Modifier.size(width = 56.dp, height = 36.dp), name = e.name)
                    Column(Modifier.weight(1f)) {
                        Text(com.kutlusoy.proiptv.parse.TitleParser.parse(e.name).title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = Color.White)
                        val account = if (everywhere && searchOpen) state.accountLabel(e.accountId) else null
                        val sub = listOfNotNull(account, now?.title).joinToString(" \u00B7 ")
                        if (sub.isNotEmpty()) Text(sub, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                    }
                }
                }
                }
            }
        }
        if (searchOpen) {
            Column(Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 8.dp, vertical = 6.dp)) {
                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                androidx.compose.runtime.DisposableEffect(Unit) { onDispose { state.typing = false } }
                TextInput(
                    query, { controller.searchText = it }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { state.typing = it.isFocused },
                    placeholder = { Text(t("live_search")) },
                    trailingIcon = { IconButton(onClick = { controller.clearSearch() }) { Icon(Icons.Filled.Close, t("action_close")) } },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable { setEverywhere(!everywhere) }, verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = everywhere, onCheckedChange = setEverywhere)
                        Text(t("player_search_all"), color = Color.White)
                    }
                    columnsButton()
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                columnsButton()
                // the filter fields at the top take the place of the magnifier in the side by side view
                if (!controller.columnMode) IconButton(onClick = { controller.searchOpen = true }, modifier = Modifier.semantics { contentDescription = searchText }) { Icon(Icons.Filled.Search, null) }
            }
        }
    }
    }
    if (controller.columnMode) {
        // categories and channels side by side, like in a classic channel browser
        val counts = remember(lists) { lists.map { it.entries().size } }
        Row(modifier) {
            val categories = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (listIndex - 3).coerceAtLeast(0))
            val cf = categoryFilter.trim()
            val visible = remember(lists, cf) { lists.withIndex().filter { cf.isEmpty() || com.kutlusoy.proiptv.parse.SearchMatch.matches(it.value.name, cf) } }
            Column(Modifier.weight(0.4f).fillMaxHeight().background(Palette.Surface)) {
                TextInput(
                    categoryFilter, { categoryFilter = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).then(typingFocus),
                    placeholder = { Text(t("player_filter_categories", lists.size)) },
                    trailingIcon = { if (categoryFilter.isNotEmpty()) IconButton(onClick = { categoryFilter = "" }) { Icon(Icons.Filled.Close, t("action_close")) } },
                )
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = categories) {
                    itemsIndexed(visible, key = { _, v -> "${v.index}|${v.value.name}" }) { _, v ->
                        val i = v.index
                        Row(
                            Modifier.fillMaxWidth().then(if (i == listIndex) Modifier.background(Palette.WineDeep) else Modifier).clickable { listIndex = i }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(v.value.name, Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Text(counts.getOrNull(i)?.toString().orEmpty(), color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            pane(Modifier.weight(0.6f).fillMaxHeight())
        }
    } else {
        pane(modifier)
    }
}

/** The right click on a channel: "open as picture in picture", only when a second connection is possible. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PipMenu(enabled: Boolean, label: String, onOpen: () -> Unit, content: @Composable () -> Unit) {
    if (enabled) androidx.compose.foundation.ContextMenuArea(items = { listOf(androidx.compose.foundation.ContextMenuItem(label, onOpen)) }, content = content) else content()
}

/** The second channel in a small window above the big picture, without sound. A click on it changes the places of the two channels. */
@Composable
private fun PipWindow(state: AppState, controller: PlayerController, modifier: Modifier) {
    val entry = controller.pipEntry ?: return
    val swapText = t("pip_swap")
    val closeText = t("pip_close")
    Box(modifier.width(300.dp).aspectRatio(16f / 9f).border(2.dp, Palette.WineSoft).background(Color.Black)) {
        VideoSurface(state.pipEngine, Modifier.fillMaxSize().semantics { contentDescription = swapText }, onDoubleClick = {}, onScroll = {}, onTap = { controller.swapPip() })
        Text(
            com.kutlusoy.proiptv.parse.TitleParser.parse(entry.name).title, Modifier.align(Alignment.BottomStart).background(Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        IconButton(onClick = { controller.closePip() }, Modifier.align(Alignment.TopEnd).size(28.dp).background(Color(0xAA000000)).semantics { contentDescription = closeText }) {
            Icon(Icons.Filled.Close, closeText, Modifier.size(16.dp), tint = Color.White)
        }
    }
}

/** Three upright bars: the switch for the view with categories and channels side by side. */
@Composable
private fun ColumnsIcon(active: Boolean) {
    val color = if (active) Palette.WineSoft else LocalContentColor.current
    Row(Modifier.size(24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { Box(Modifier.width(3.dp).height(18.dp).background(color, RoundedCornerShape(2.dp))) }
    }
}

@Composable
private fun ControlBar(state: AppState, controller: PlayerController, modifier: Modifier, floating: Boolean) {
    var tracksOpen by remember { mutableStateOf(false) }
    var recordOpen by remember { mutableStateOf(false) }
    var guideOpen by remember { mutableStateOf(false) }
    if (guideOpen) GuideDialog(state, controller) { guideOpen = false }
    var recordError by remember { mutableStateOf<String?>(null) }
    if (recordOpen) RecordDialog(state, controller, onError = { recordError = it }) { recordOpen = false }
    recordError?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { recordError = null },
            text = { Text(msg) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { recordError = null }) { Text(t("action_close")) } },
        )
    }
    if (tracksOpen) TracksDialog(state.engine) { tracksOpen = false }
    Row(
        modifier.fillMaxWidth().background(if (floating) Color(0xCC000000) else Palette.Surface).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // the same round controls and symbols as in the Android player: pause, stop, record
        if (controller.live) RoundControl(PlayerGlyph.MENU, t("player_channel_list"), { controller.listOpen = !controller.listOpen }, diameter = 38.dp)
        RoundControl(if (controller.paused) PlayerGlyph.PLAY else PlayerGlyph.PAUSE, t(if (controller.paused) "player_play" else "player_pause"), { controller.togglePause() }, diameter = 38.dp)
        RoundControl(PlayerGlyph.STOP, t("player_stop"), { controller.stop() }, diameter = 38.dp)
        if (controller.live) {
            val recording = controller.recordingId != null
            RoundControl(if (recording) PlayerGlyph.REC_STOP else PlayerGlyph.REC, t(if (recording) "rec_stop" else "rec_record"), { if (recording) controller.stopRecording() else recordOpen = true }, diameter = 38.dp)
        }
        if (controller.canZap) {
            RoundControl(PlayerGlyph.UP, t("player_channel_prev"), { controller.zap(-1) }, diameter = 38.dp)
            RoundControl(PlayerGlyph.DOWN, t("player_channel_next"), { controller.zap(1) }, diameter = 38.dp)
        }
        if (controller.live && controller.timeShift) {
            IconButton(enabled = controller.bufferedMs > 5_000, onClick = { controller.shift(-30_000) }) { Icon(Icons.Filled.Replay30, t("player_back_30")) }
            IconButton(enabled = controller.behindMs > 3_000, onClick = { controller.shift(30_000) }) { Icon(Icons.Filled.Forward30, t("player_forward_30")) }
            if (controller.behindMs > 3_000) {
                androidx.compose.material3.TextButton(onClick = { controller.goLive() }) { Text("${t("player_live")}  -${clock(controller.behindMs)}") }
            }
        }
        val liveEntry = controller.entries.getOrNull(controller.index)
        val liveNow = if (controller.live && liveEntry != null) state.guide.nowNext(liveEntry.accountId, liveEntry.epgId, state.tick).first else null
        if (!controller.live && controller.length > 0) {
            TimeBar(
                fraction = controller.position.toFloat() / controller.length, left = hoursMinutes(0), right = hoursMinutes(controller.length),
                mark = "-" + hoursMinutes(controller.length - controller.position), modifier = Modifier.weight(1f),
                onSeek = { state.engine.seek((it * controller.length).toLong()); controller.poll() },
            )
        } else if (liveNow != null) {
            val shortTime = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
            val nowMs = state.tick * 1000
            TimeBar(
                fraction = (nowMs / 1000 - liveNow.start).toFloat() / (liveNow.end - liveNow.start).coerceAtLeast(1),
                left = shortTime.format(java.util.Date(liveNow.start * 1000)), right = shortTime.format(java.util.Date(liveNow.end * 1000)),
                mark = "-" + hoursMinutes((liveNow.end * 1000 - nowMs).coerceAtLeast(0)), modifier = Modifier.weight(1f),
            )
        } else {
            Box(Modifier.weight(1f))
        }
        // the symbol shows and switches the mute (the key M does the same); a volume of zero shows the crossed symbol too
        val muteText = t("player_mute")
        val silent = state.engine.muted || controller.volume == 0
        IconButton(
            onClick = { if (controller.volume == 0 && !state.engine.muted) controller.changeVolume(50) else state.engine.toggleMute() },
            modifier = Modifier.size(32.dp).semantics { contentDescription = muteText },
        ) {
            Icon(if (silent) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, muteText, Modifier.size(20.dp), tint = if (silent) Palette.WineSoft else LocalContentColor.current)
        }
        Slider(value = controller.volume / 200f, onValueChange = { controller.changeVolume((it * 200).toInt()) }, modifier = Modifier.width(96.dp).alpha(if (silent) 0.4f else 1f))
        if (controller.live) RoundControl(PlayerGlyph.GUIDE, t("player_guide"), { guideOpen = true }, diameter = 38.dp)
        RoundControl(PlayerGlyph.GEAR, t("player_more"), { tracksOpen = true }, diameter = 38.dp)
        IconButton(onClick = { state.setCompact(true, state.windowRef?.bounds) }) { Icon(Icons.Filled.PictureInPictureAlt, t("player_compact")) }
        IconButton(onClick = { state.toggleFullscreen() }) { Icon(Icons.Filled.Fullscreen, t("player_fullscreen")) }
    }
}

/** What runs now and what follows, with the end time, for live channels that have guide data. */
@Composable
private fun NowNextLine(state: AppState, controller: PlayerController) {
    if (!controller.live) return
    val entry = controller.entries.getOrNull(controller.index) ?: return
    val (now, next) = state.guide.nowNext(entry.accountId, entry.epgId, state.tick)
    if (now == null && next == null) return
    val format = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    val text = buildString {
        now?.let { append(t("player_now")).append(": ").append(it.title).append(" (").append(format.format(java.util.Date(it.end * 1000))).append(")") }
        next?.let { if (isNotEmpty()) append("   "); append(t("player_next")).append(": ").append(it.title) }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
}

@Composable
private fun RecordDialog(state: AppState, controller: PlayerController, onError: (String?) -> Unit, onClose: () -> Unit) {
    val item = controller.current
    val entry = controller.entries.getOrNull(controller.index)
    val now = state.guide.nowNext(item?.account ?: "", entry?.epgId, System.currentTimeMillis() / 1000).first
    fun go(ms: Long?) { onClose(); onError(controller.record(ms)) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(t("rec_record")) },
        text = {
            Column {
                if (now != null) RecordChoice(t("rec_until_program_end")) { go(((now.end * 1000) - System.currentTimeMillis() + 60_000).coerceAtLeast(60_000)) }
                listOf(30, 60, 120).forEach { m -> RecordChoice(t("rec_minutes", m)) { go(m * 60_000L) } }
                RecordChoice(t("rec_until_stopped")) { go(null) }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text(t("action_cancel")) } },
    )
}

/** The shows of the channel that is playing: now, next and the following hours. A running show can be recorded, a later one is planned. */
@Composable
private fun GuideDialog(state: AppState, controller: PlayerController, onClose: () -> Unit) {
    val entry = controller.entries.getOrNull(controller.index)
    val now = System.currentTimeMillis() / 1000
    val programs = entry?.epgId?.let { state.guide.programs[Guide.key(entry.accountId, it)] }.orEmpty().filter { it.end > now }.take(40)
    var message by remember { mutableStateOf<String?>(null) }
    val format = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(t("player_guide")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (programs.isEmpty()) Text(t("epg_none"))
                programs.forEach { p ->
                    val running = p.start <= now
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            val item = entry?.let { state.livePlayable(it) } ?: return@clickable
                            message = if (running) {
                                controller.record(p.end * 1000 + 60_000 - System.currentTimeMillis(), p.title) ?: Strings.get("rec_status_recording")
                            } else if (!state.recordingManager.fits(item, p.start * 1000 - 30_000, p.end * 1000 + 60_000)) {
                                Strings.get("rec_refused", item.maxConnections ?: 1)
                            } else {
                                state.recordingManager.schedule(item, p.start * 1000 - 30_000, p.end * 1000 + 60_000, p.title)
                                Strings.get("rec_scheduled")
                            }
                        }.padding(vertical = 6.dp),
                    ) {
                        Text(p.title, fontWeight = if (running) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
                        Text(format.format(java.util.Date(p.start * 1000)) + " - " + format.format(java.util.Date(p.end * 1000)), style = MaterialTheme.typography.bodySmall)
                    }
                    if (!running && entry != null) {
                        val have = state.hasReminder(entry, p.start * 1000)
                        androidx.compose.material3.TextButton(onClick = { state.toggleReminder(entry, p.title, p.start * 1000) }) {
                            Text(t(if (have) "reminder_remove_action" else "reminder_set_action"), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Text(t("player_record_hint"), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                message?.let { Text(it, Modifier.padding(top = 6.dp), color = Palette.WineSoft) }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text(t("action_close")) } },
    )
}

@Composable
private fun RecordChoice(text: String, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp))
}

@Composable
private fun TracksDialog(engine: VlcEngine, onClose: () -> Unit) {
    var tracks by remember { mutableStateOf(engine.tracks()) }
    val data = tracks
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(t("player_tracks")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (data == null) return@Column
                Text(t("player_audio"), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                data.audio.forEach { tr ->
                    TrackRow(tr.name, tr.id == data.audioSelected) { engine.selectAudio(tr.id); tracks = engine.tracks() }
                }
                Text(t("player_subtitles"), Modifier.padding(top = 12.dp), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                data.subtitles.forEach { tr ->
                    TrackRow(if (tr.id == -1) t("player_off") else tr.name, tr.id == data.subtitleSelected) { engine.selectSubtitle(tr.id); tracks = engine.tracks() }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text(t("action_close")) } },
    )
}

@Composable
private fun TrackRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Text(name, Modifier.padding(start = 8.dp))
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun VideoSurface(engine: VlcEngine, modifier: Modifier, onDoubleClick: () -> Unit, onScroll: (Int) -> Unit, onTap: (() -> Unit)? = null) {
    var image by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(engine) {
        var shown = -1L
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val f = engine.frame.get()
            if (f == null) {
                if (image != null) image = null
                shown = -1L
            } else if (f.version != shown) {
                shown = f.version
                image = org.jetbrains.skia.Image.makeRaster(
                    org.jetbrains.skia.ImageInfo(f.width, f.height, org.jetbrains.skia.ColorType.BGRA_8888, org.jetbrains.skia.ColorAlphaType.OPAQUE),
                    f.pixels, f.width * 4,
                ).toComposeImageBitmap()
            }
        }
    }
    androidx.compose.foundation.Canvas(
        modifier.background(Color.Black)
            .pointerInput(onTap != null) {
                if (onTap != null) detectTapGestures(onTap = { onTap() }) else detectTapGestures(onDoubleTap = { onDoubleClick() })
            }
            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Scroll) { e -> onScroll(e.changes.firstOrNull()?.scrollDelta?.y?.toInt() ?: 0) },
    ) {
        val img = image ?: return@Canvas
        val scale = minOf(size.width / img.width, size.height / img.height)
        val w = (img.width * scale).toInt()
        val h = (img.height * scale).toInt()
        drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()), dstSize = androidx.compose.ui.unit.IntSize(w, h))
    }
}
