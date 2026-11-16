package com.kutlusoy.proiptv.desktop

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.focus.focusRequester
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
    fun record(stopAfterMs: Long?): String? {
        val item = current ?: return null
        return try {
            recordingId = state.recordingManager.start(item, stopAfterMs?.let { System.currentTimeMillis() + it })
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

    /** Plays the channel at this position of [list]. From now on zapping walks through that list. */
    fun play(list: List<Entry>, i: Int) {
        if (request !is PlayRequest.Live || i !in list.indices) return
        playEntries = list
        index = i
        listOpen = false
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
            VideoSurface(engine, modifier, onDoubleClick = { state.toggleFullscreen() }, onScroll = { dy -> controller.changeVolume(controller.volume - dy * 5) })
        }
        controller.failed?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp)) }
        if (controller.reconnecting && controller.failed == null) Text(t("player_retrying"), Modifier.background(Color(0xAA000000)).padding(12.dp), color = Color.White)
    }

    Box(Modifier.fillMaxSize()) {
    if (state.compact) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { video(Modifier.fillMaxSize()) }
    } else if (fullscreen) {
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
    if (controller.listOpen && controller.live) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { controller.listOpen = false })
        ChannelPanel(state, controller, Modifier.align(Alignment.CenterStart))
    }
    }
}

private class PanelList(val name: String, val entries: () -> List<Entry>)

/**
 * The channel list at the left edge, bound to the mouse: the header shows the name of the list with arrows to switch between the lists,
 * the magnifier at the bottom opens a search field. With "All channels" the search looks through all accounts and lists.
 * The channel keeps playing while the list is open.
 */
@Composable
private fun ChannelPanel(state: AppState, controller: PlayerController, modifier: Modifier) {
    val live = state.data.entries(Kind.LIVE)
    val byKey = remember(live) { live.associateBy { it.key } }
    val favLists = state.listsOf(Kind.LIVE)
    val favName = t("fav_title")
    val historyName = t("history_title")
    val allName = t("live_all")
    val currentName = t("player_list_current")
    val otherName = t("live_other")
    val lists = remember(controller.playEntries, state.favorites, state.history, favLists, live, state.data.categories(Kind.LIVE)) {
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
        // the list the player was started from comes first, under its own name when it is one of the lists
        val start = controller.playEntries.map { it.key }
        val same = out.indexOfFirst { l -> l.entries().map { it.key } == start }
        if (same > 0) out.add(0, out.removeAt(same)) else if (same < 0) out.add(0, PanelList(currentName) { controller.playEntries })
        out
    }
    var listIndex by remember(lists.size) { mutableIntStateOf(0) }
    val listNow = lists[listIndex.coerceIn(0, lists.lastIndex)]
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val everywhere = state.store.config.playerSearchAll
    val q = query.trim()
    val shown = remember(listNow, listIndex, searchOpen, q, everywhere, live, state.tick / 60) {
        val base = if (searchOpen && q.isNotEmpty() && everywhere) live else listNow.entries()
        if (!searchOpen || q.isEmpty()) base
        else base.filter { e ->
            e.name.contains(q, ignoreCase = true) || com.kutlusoy.proiptv.parse.TitleParser.parse(e.name).title.contains(q, ignoreCase = true) ||
                state.guide.nowNext(e.accountId, e.epgId, state.tick).first?.title?.contains(q, ignoreCase = true) == true
        }
    }
    val playingKey = controller.playEntries.getOrNull(controller.index)?.key
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(listNow, searchOpen, q, everywhere) {
        val at = shown.indexOfFirst { it.key == playingKey }
        list.scrollToItem(if (at > 3) at - 3 else 0)
    }
    val previousText = t("player_list_previous")
    val nextText = t("player_list_next")
    val searchText = t("player_search")
    Column(modifier.fillMaxHeight().width(340.dp).background(Palette.Velvet)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { listIndex = (listIndex - 1 + lists.size) % lists.size }, enabled = lists.size > 1, modifier = Modifier.semantics { contentDescription = previousText }) {
                Icon(Icons.Filled.KeyboardArrowLeft, null)
            }
            Text(
                if (searchOpen && q.isNotEmpty() && everywhere) t("player_search_all") else listNow.name,
                Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            IconButton(onClick = { listIndex = (listIndex + 1) % lists.size }, enabled = lists.size > 1, modifier = Modifier.semantics { contentDescription = nextText }) {
                Icon(Icons.Filled.KeyboardArrowRight, null)
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list) {
            itemsIndexed(shown, key = { _, e -> e.key }) { i, e ->
                val now = state.guide.nowNext(e.accountId, e.epgId, state.tick).first
                Row(
                    Modifier.fillMaxWidth().background(if (e.key == playingKey) Palette.WineDeep.copy(alpha = 0.7f) else Color.Transparent)
                        .clickable { controller.play(shown, i) }.padding(horizontal = 12.dp, vertical = 8.dp),
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
        if (searchOpen) {
            Column(Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 8.dp, vertical = 6.dp)) {
                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                androidx.compose.material3.OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text(t("live_search")) },
                    trailingIcon = { IconButton(onClick = { query = ""; searchOpen = false }) { Icon(Icons.Filled.Close, t("action_close")) } },
                )
                Row(Modifier.fillMaxWidth().clickable { state.store.update { c -> c.copy(playerSearchAll = !c.playerSearchAll) } }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = everywhere, onCheckedChange = { on -> state.store.update { c -> c.copy(playerSearchAll = on) } })
                    Text(t("player_search_all"), color = Color.White)
                }
            }
        } else {
            IconButton(onClick = { searchOpen = true }, modifier = Modifier.align(Alignment.CenterHorizontally).semantics { contentDescription = searchText }) { Icon(Icons.Filled.Search, null) }
        }
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
        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(20.dp))
        Slider(value = controller.volume / 200f, onValueChange = { controller.changeVolume((it * 200).toInt()) }, modifier = Modifier.width(96.dp))
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
                                controller.record(p.end * 1000 + 60_000 - System.currentTimeMillis()) ?: Strings.get("rec_status_recording")
                            } else if (!state.recordingManager.fits(item, p.start * 1000 - 30_000, p.end * 1000 + 60_000)) {
                                Strings.get("rec_refused", item.maxConnections ?: 1)
                            } else {
                                state.recordingManager.schedule(item, p.start * 1000 - 30_000, p.end * 1000 + 60_000)
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
private fun VideoSurface(engine: VlcEngine, modifier: Modifier, onDoubleClick: () -> Unit, onScroll: (Int) -> Unit) {
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
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleClick() }) }
            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Scroll) { e -> onScroll(e.changes.firstOrNull()?.scrollDelta?.y?.toInt() ?: 0) },
    ) {
        val img = image ?: return@Canvas
        val scale = minOf(size.width / img.width, size.height / img.height)
        val w = (img.width * scale).toInt()
        val h = (img.height * scale).toInt()
        drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()), dstSize = androidx.compose.ui.unit.IntSize(w, h))
    }
}
