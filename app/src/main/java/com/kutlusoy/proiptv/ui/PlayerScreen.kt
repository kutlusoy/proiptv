@file:OptIn(UnstableApi::class)

package com.kutlusoy.proiptv.ui

import android.content.Context
import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.width
import com.kutlusoy.proiptv.data.Source
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kutlusoy.proiptv.ProIptvApp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.parse.TitleParser
import com.kutlusoy.proiptv.parse.decodeEntities
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.PlayTarget
import com.kutlusoy.proiptv.data.Program
import com.kutlusoy.proiptv.engine.BudgetExceeded
import com.kutlusoy.proiptv.engine.HubLease
import com.kutlusoy.proiptv.engine.StreamHub
import com.kutlusoy.proiptv.net.Http
import com.kutlusoy.proiptv.net.Upstream
import com.kutlusoy.proiptv.player.HubDataSource
import com.kutlusoy.proiptv.recording.NotEnoughSpace
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private fun buildPlayer(ctx: Context): ExoPlayer {
    val load = DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 60_000, 1_500, 3_000).build()
    val renderers = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)
    return ExoPlayer.Builder(ctx, renderers).setLoadControl(load).build()
}

private fun extractors() = DefaultExtractorsFactory()
    .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
    .setConstantBitrateSeekingEnabled(true)

private fun start(ctx: Context, exo: ExoPlayer, target: PlayTarget, hls: Boolean) {
    val http = OkHttpDataSource.Factory(Http.client).setUserAgent(target.userAgent?.takeIf { it.isNotBlank() } ?: Http.DEFAULT_AGENT)
    val item = MediaItem.Builder().setUri(target.url).apply { if (hls) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
    exo.setMediaSource(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http), extractors()).createMediaSource(item))
    exo.prepare()
    exo.playWhenReady = true
}

private fun startHub(exo: ExoPlayer, hub: StreamHub, offset: Long) {
    val item = MediaItem.Builder().setUri("hub://${hub.key}").setMimeType(MimeTypes.VIDEO_MP2T).build()
    exo.setMediaSource(ProgressiveMediaSource.Factory(HubDataSource.Factory(hub, offset), extractors()).createMediaSource(item))
    exo.prepare()
    exo.playWhenReady = true
}

private fun isTransportStream(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return !path.endsWith(".m3u8") && !path.endsWith(".mpd")
}

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private class LeaseHolder {
    @Volatile
    var lease: HubLease? = null
}

private const val LIVE_LAG_MS = 1500L
private const val START_PAD_MS = 60_000L
private const val END_PAD_MS = 5 * 60_000L
private const val LIVE_THRESHOLD_MS = 3000L
private const val SHIFT_STEP_MS = 10_000L

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun PlayerScreen(vm: MainViewModel, request: PlayRequest, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = ctx.applicationContext as ProIptvApp
    val exo = remember { buildPlayer(ctx) }

    var index by remember { mutableIntStateOf((request as? PlayRequest.Zap)?.index ?: 0) }
    var count by remember { mutableIntStateOf(1) }
    var item by remember { mutableStateOf<Item?>(null) }
    var target by remember { mutableStateOf((request as? PlayRequest.Single)?.target) }
    var catchup by remember { mutableStateOf<PlayTarget?>(null) }
    var forceHls by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<Int?>(null) }
    var buffering by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var flash by remember { mutableStateOf<String?>(null) }
    var flashBack by remember { mutableStateOf(false) }
    var flashTick by remember { mutableIntStateOf(0) }
    var moreDialog by remember { mutableStateOf(false) }
    var listOpen by remember { mutableStateOf(false) }
    var removeFavDialog by remember { mutableStateOf(false) }
    var touched by remember { mutableIntStateOf(0) }
    var digits by remember { mutableStateOf("") }
    var resize by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var tracks by remember { mutableStateOf(exo.currentTracks) }
    var tracksDialog by remember { mutableStateOf(false) }
    var guideDialog by remember { mutableStateOf(false) }
    var listsDialog by remember { mutableStateOf(false) }
    var guideTick by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val holder = remember { LeaseHolder() }
    var hubActive by remember { mutableStateOf(false) }
    var hubStartTime by remember { mutableLongStateOf(0L) }
    var behindMs by remember { mutableLongStateOf(0L) }
    var shiftTime by remember { mutableStateOf<Long?>(null) }
    var shiftRequest by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var recordDialog by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var landscape by remember { mutableStateOf(true) }
    var skipResume by remember { mutableStateOf((request as? PlayRequest.Single)?.fromStart == true) }
    val activity = remember { ctx.findActivity() }
    var failed by remember { mutableStateOf(false) }
    val tried = remember { mutableSetOf<Long>() }
    val activeRecs by app.recordings.active.collectAsState()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val allFavLists by vm.favLists.collectAsState()
    val favLists = allFavLists.filter { it.kind == (item?.kind ?: Kind.LIVE) }
    val favKeys by vm.favKeys.collectAsState()
    val root = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val first = remember { FocusRequester() }
    val active = catchup ?: target
    val zapping = request is PlayRequest.Zap && catchup == null
    val dialogOpen = tracksDialog || guideDialog || listsDialog || recordDialog || moreDialog || removeFavDialog || listOpen
    val activeNow by rememberUpdatedState(active)
    val hubKey = active?.let { "${it.accountId}:${it.remoteId}" }
    val recordingHere = hubKey?.let { k -> activeRecs.firstOrNull { it.hubKey == k } }

    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (previous != null) activity.requestedOrientation = previous
        }
    }

    LaunchedEffect(landscape) {
        activity?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED && !forceHls) {
                    forceHls = true
                    return
                }
                if (retry < 3) {
                    retry++
                    message = R.string.player_retrying
                    scope.launch {
                        delay(1500L * retry)
                        reload++
                    }
                } else {
                    message = R.string.player_failed
                    failed = true
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    retry = 0
                    message = null
                    notice = null
                }
                if (state == Player.STATE_ENDED && activeNow?.live == true) {
                    scope.launch {
                        delay(1000)
                        reload++
                    }
                }
            }

            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            holder.lease?.close()
            val t = active
            if (t != null && !t.live) {
                val pos = exo.currentPosition
                val dur = exo.duration
                app.scope.launch { app.repo.saveProgress(t, pos, dur) }
            }
            exo.release()
        }
    }

    LaunchedEffect(index) {
        val r = request as? PlayRequest.Zap ?: return@LaunchedEffect
        count = vm.repo.count(r.source).coerceAtLeast(1)
        val i = vm.repo.at(r.source, index)
        item = i
        retry = 0
        forceHls = false
        message = null
        notice = null
        shiftTime = null
        failed = false
        tried.clear()
        target = i?.let { vm.repo.target(it) }
        overlay = true
        touched++
    }

    LaunchedEffect(failed) {
        if (!failed || request !is PlayRequest.Zap || catchup != null) return@LaunchedEffect
        val current = item ?: return@LaunchedEffect
        tried += current.id
        val alternative = vm.repo.alternatives(current).firstOrNull { it.id !in tried } ?: return@LaunchedEffect
        val next = vm.repo.target(alternative) ?: return@LaunchedEffect
        failed = false
        retry = 0
        forceHls = false
        message = null
        item = alternative
        target = next
        info = ctx.getString(R.string.player_switched, alternative.name)
    }

    LaunchedEffect(info) {
        if (info != null) {
            delay(4_000)
            info = null
        }
    }

    LaunchedEffect(item?.id) {
        val i = item ?: return@LaunchedEffect
        if (vm.repo.nowAndNext(i.accountId, i.epgId).first == null && vm.repo.fetchShortEpg(i)) guideTick++
    }

    LaunchedEffect(active, reload, forceHls, shiftRequest) {
        val t = active ?: return@LaunchedEffect
        holder.lease?.close()
        holder.lease = null
        hubActive = false
        val useHub = t.live && !forceHls && catchup == null && app.prefs.timeshift && isTransportStream(t.url)
        if (useHub) {
            val lease = try {
                val max = vm.repo.maxConnections(t.accountId)
                withContext(Dispatchers.IO) { app.engine.acquire(t.accountId, t.remoteId, t.title, max, Upstream.opener(t.url, t.userAgent)) }
            } catch (e: BudgetExceeded) {
                notice = ctx.getString(R.string.player_no_connection, e.limit, e.openTitles.joinToString(", "))
                return@LaunchedEffect
            }
            holder.lease = lease
            hubActive = true
            val buffer = lease.hub.buffer
            val offset = shiftTime?.let { buffer.offsetAtTime(it) } ?: buffer.offsetAtTime(System.currentTimeMillis() - LIVE_LAG_MS)
            hubStartTime = buffer.timeAtOffset(offset)
            startHub(exo, lease.hub, offset)
        } else {
            start(ctx, exo, t, forceHls)
        }
        if (!t.live) {
            if (skipResume) {
                skipResume = false
            } else {
                val saved = vm.repo.progress(t)?.positionMs ?: 0
                if (saved > 5_000) exo.seekTo(saved)
            }
        }
    }

    LaunchedEffect(buffering, active, reload) {
        if (buffering && active?.live == true) {
            delay(25_000)
            if (exo.playbackState == Player.STATE_BUFFERING) reload++
        }
    }

    LaunchedEffect(active) {
        while (active != null && !active.live) {
            delay(10_000)
            vm.repo.saveProgress(active, exo.currentPosition, exo.duration)
        }
    }

    LaunchedEffect(overlay, touched, dialogOpen) {
        if (overlay && !dialogOpen) {
            delay(5_000)
            overlay = false
        }
    }

    LaunchedEffect(overlay) {
        while (overlay) {
            position = exo.currentPosition
            duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            if (hubActive) behindMs = (System.currentTimeMillis() - (hubStartTime + position)).coerceAtLeast(0)
            delay(500)
        }
    }

    LaunchedEffect(flashTick) {
        if (flashTick > 0) {
            delay(700)
            flash = null
        }
    }

    LaunchedEffect(overlay, dialogOpen) {
        yield()
        runCatching { if (overlay && !dialogOpen) first.requestFocus() else if (!dialogOpen) root.requestFocus() }
    }

    LaunchedEffect(digits) {
        if (digits.isNotEmpty()) {
            delay(1_500)
            val n = digits.toIntOrNull() ?: 1
            index = (n - 1).coerceIn(0, count - 1)
            digits = ""
        }
    }

    BackHandler { if (listOpen) listOpen = false else onClose() }

    fun shiftBy(deltaMs: Long) {
        val hub = holder.lease?.hub ?: return
        val now = System.currentTimeMillis()
        val current = hubStartTime + exo.currentPosition
        val wanted = (current + deltaMs).coerceIn(hub.buffer.oldestTime(), now)
        shiftTime = if (now - wanted < LIVE_THRESHOLD_MS) null else wanted
        shiftRequest++
        overlay = true
        touched++
    }

    fun goLive() {
        shiftTime = null
        shiftRequest++
    }

    fun startRecording(stopAt: Long?, title: String? = null) {
        val base = active ?: return
        val t = if (title != null) base.copy(title = title) else base
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                app.recordings.start(t, vm.repo.maxConnections(t.accountId), stopAt)
            } catch (e: BudgetExceeded) {
                notice = ctx.getString(R.string.player_no_connection, e.limit, e.openTitles.joinToString(", "))
            } catch (e: NotEnoughSpace) {
                notice = ctx.getString(R.string.player_no_space)
            }
        }
    }

    val nowNext by produceState<Pair<Program?, Program?>>(null to null, item, position / 60_000, guideTick) {
        val i = item
        value = if (i == null) null to null else vm.repo.nowAndNext(i.accountId, i.epgId)
    }

    fun zap(delta: Int) {
        if (!zapping) return
        index = ((index + delta) % count + count) % count
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(root)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || dialogOpen) return@onPreviewKeyEvent false
                touched++
                val code = e.key.nativeKeyCode
                when {
                    // up and down always change the channel, whether the controls are shown or not
                    e.key == Key.DirectionUp || e.key == Key.ChannelDown || e.key == Key.PageUp -> {
                        if (zapping) { zap(-1); true } else { overlay = true; false }
                    }
                    e.key == Key.DirectionDown || e.key == Key.ChannelUp || e.key == Key.PageDown -> {
                        if (zapping) { zap(1); true } else { overlay = true; false }
                    }
                    // left and right bring the controls back, then they walk through all of them
                    zapping && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight) -> {
                        if (!overlay) overlay = true
                        else focusManager.moveFocus(if (e.key == Key.DirectionLeft) FocusDirection.Previous else FocusDirection.Next)
                        true
                    }
                    e.key == Key.MediaRewind && hubActive -> { shiftBy(-SHIFT_STEP_MS); true }
                    e.key == Key.MediaFastForward && hubActive -> { shiftBy(SHIFT_STEP_MS); true }
                    e.key == Key.DirectionLeft && active?.live == false && !overlay -> { exo.seekBack(); true }
                    e.key == Key.DirectionRight && active?.live == false && !overlay -> { exo.seekForward(); true }
                    (e.key == Key.DirectionCenter || e.key == Key.Enter) && !overlay -> { overlay = true; true }
                    e.key == Key.MediaPlayPause || e.key == Key.Spacebar -> {
                        if (exo.isPlaying) exo.pause() else exo.play(); true
                    }
                    zapping && (code in 7..16 || code in 144..153) -> {
                        digits += if (code in 7..16) code - 7 else code - 144
                        digits = digits.takeLast(4)
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(zapping) {
                var sum = 0f
                val threshold = 90.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { sum = 0f },
                    onDragEnd = {
                        if (zapping) {
                            if (sum < -threshold) zap(1) else if (sum > threshold) zap(-1)
                        }
                    },
                    onVerticalDrag = { _, dy -> sum += dy },
                )
            }
            .pointerInput(active?.live) {
                detectTapGestures(
                    onTap = { overlay = !overlay },
                    onDoubleTap = { pos ->
                        if (active?.live == false) {
                            val back = pos.x < size.width / 2
                            val limit = exo.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                            exo.seekTo((exo.currentPosition + if (back) -SKIP_MS else SKIP_MS).coerceIn(0L, limit))
                            flash = (if (back) "-" else "+") + "10 s"
                            flashBack = back
                            flashTick++
                        }
                    },
                )
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c -> PlayerView(c).apply { useController = false; player = exo; keepScreenOn = true } },
            update = { it.resizeMode = resize },
        )
        (notice ?: message?.let { stringResource(it) })?.let { Text(it, Modifier.align(Alignment.Center).background(Color(0xAA000000)).padding(12.dp), color = Color.White, textAlign = TextAlign.Center) }
        info?.let { Text(it, Modifier.align(Alignment.TopCenter).padding(24.dp).background(Color(0xAA000000)).padding(10.dp), color = Color.White, textAlign = TextAlign.Center) }
        Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Fade(overlay) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (zapping) RoundControl(PlayerGlyph.MENU, stringResource(R.string.player_channel_list), { listOpen = true }, diameter = 44.dp)
                }
            }
            if (recordingHere != null) {
                Text("\u25CF REC", Modifier.background(Color(0xAA000000)).padding(8.dp), color = Color(0xFFFF5252))
            }
        }
        if (digits.isNotEmpty()) {
            Text(digits, Modifier.align(Alignment.TopEnd).padding(24.dp).background(Color(0xAA000000)).padding(12.dp), style = MaterialTheme.typography.displaySmall, color = Color.White)
        }
        flash?.let {
            Text(
                it, Modifier.align(if (flashBack) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 56.dp)
                    .background(Color(0xAA000000), CircleShape).padding(horizontal = 20.dp, vertical = 12.dp),
                color = Color.White, style = MaterialTheme.typography.titleLarge,
            )
        }
        if (zapping && !LocalTv.current) {
            Fade(overlay, Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    RoundControl(PlayerGlyph.UP, stringResource(R.string.player_channel_prev), { zap(-1); touched++ }, diameter = 56.dp)
                    RoundControl(PlayerGlyph.DOWN, stringResource(R.string.player_channel_next), { zap(1); touched++ }, diameter = 56.dp)
                }
            }
        }
        Fade(overlay, Modifier.align(Alignment.TopEnd)) {
            Row(Modifier.statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val i = item
                if (zapping && i != null) {
                    val fav = Triple(i.accountId, i.kind, i.remoteId) in favKeys
                    RoundControl(
                        PlayerGlyph.STAR_ON.takeIf { fav } ?: PlayerGlyph.STAR_OFF,
                        stringResource(if (fav) R.string.fav_remove else R.string.fav_add),
                        {
                            if (favLists.size <= 1) {
                                val list = favLists.firstOrNull()
                                if (list != null) {
                                    if (fav) removeFavDialog = true else vm.run { vm.repo.setFavorite(list.id, i.accountId, i.kind, i.remoteId, i.name, true) }
                                }
                            } else {
                                listsDialog = true
                            }
                        },
                        diameter = 44.dp,
                    )
                }
                OutlinedButton(onClick = { moreDialog = true }, modifier = Modifier.background(Color(0x99000000), CircleShape).focusHighlight()) {
                    Text(stringResource(R.string.player_more), color = Color.White)
                }
            }
        }
        Fade(overlay, Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(active?.title?.decodeEntities() ?: "", style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (active?.live == true) {
                    nowNext.first?.let { now ->
                        val total = (now.end - now.start).coerceAtLeast(1)
                        val done = ((System.currentTimeMillis() / 1000 - now.start).toFloat() / total).coerceIn(0f, 1f)
                        Text("${stringResource(R.string.player_now)}: ${now.title.decodeEntities()}", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LinearProgressIndicator(progress = { done }, Modifier.fillMaxWidth())
                    }
                    nowNext.second?.let { Text("${stringResource(R.string.player_next)}: ${it.title.decodeEntities()}", color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                if (active?.live == false && duration > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(clock(position), color = Color.White)
                        Slider(
                            value = (position.toFloat() / duration).coerceIn(0f, 1f),
                            onValueChange = { exo.seekTo((it * duration).toLong()); position = exo.currentPosition; touched++ },
                            modifier = Modifier.weight(1f).focusHighlight(),
                        )
                        Text(clock(duration), color = Color.White)
                    }
                }
                if (hubActive) {
                    Text(
                        if (behindMs < LIVE_THRESHOLD_MS) stringResource(R.string.player_live) else "-" + clock(behindMs),
                        color = if (behindMs < LIVE_THRESHOLD_MS) Color(0xFFFF5252) else Color.White,
                    )
                }
                Box(Modifier.fillMaxWidth()) {
                    Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val i = item
                        if (zapping && i?.epgId != null) {
                            OutlinedButton(onClick = { guideDialog = true }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.player_guide)) }
                        }
                        if (hubActive) {
                            OutlinedButton(onClick = { goLive() }, enabled = behindMs >= LIVE_THRESHOLD_MS, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.player_live)) }
                            RoundControl(
                                if (recordingHere != null) PlayerGlyph.REC_STOP else PlayerGlyph.REC,
                                stringResource(if (recordingHere != null) R.string.rec_stop else R.string.rec_record),
                                { if (recordingHere != null) app.recordings.stop(recordingHere.id) else recordDialog = true },
                                diameter = 44.dp,
                            )
                        }
                    }
                    val canSeek = active?.live == false || hubActive
                    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundControl(PlayerGlyph.STOP, stringResource(R.string.player_stop), onClose, Modifier.then(if (!canSeek) Modifier.focusRequester(first) else Modifier), diameter = 48.dp)
                        if (canSeek) {
                            RoundControl(
                                if (playing) PlayerGlyph.PAUSE else PlayerGlyph.PLAY, stringResource(if (playing) R.string.player_pause else R.string.player_play),
                                { if (exo.isPlaying) exo.pause() else exo.play(); touched++ }, Modifier.focusRequester(first), diameter = 48.dp,
                            )
                        }
                    }
                }
            }
        }
        if (listOpen && request is PlayRequest.Zap) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { listOpen = false })
            ChannelPanel(
                vm, request.source, count, index,
                onPick = { i -> index = i; listOpen = false; overlay = false },
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }

    if (moreDialog) {
        AlertDialog(
            onDismissRequest = { moreDialog = false },
            title = { Text(stringResource(R.string.player_more)) },
            text = {
                Column {
                    TextButton(onClick = { moreDialog = false; tracksDialog = true }, modifier = Modifier.fillMaxWidth().focusHighlight()) { Text(stringResource(R.string.player_tracks), Modifier.fillMaxWidth()) }
                    if (!LocalTv.current) {
                        TextButton(onClick = { moreDialog = false; landscape = !landscape }, modifier = Modifier.fillMaxWidth().focusHighlight()) { Text(stringResource(R.string.player_rotate), Modifier.fillMaxWidth()) }
                    }
                    TextButton(
                        onClick = {
                            moreDialog = false
                            resize = when (resize) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusHighlight(),
                    ) { Text(stringResource(R.string.player_aspect), Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moreDialog = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    if (tracksDialog) TracksDialog(exo, tracks) { tracksDialog = false }
    if (guideDialog) {
        item?.let { i ->
            GuideDialog(vm, i, onPlay = { p ->
                scope.launch {
                    vm.repo.catchupTarget(i, p)?.let { catchup = it; retry = 0; forceHls = false }
                    guideDialog = false
                    overlay = false
                }
            }, onRecord = { p ->
                guideDialog = false
                scope.launch {
                    val now = System.currentTimeMillis()
                    val title = "${i.name}: ${p.title.decodeEntities()}"
                    val endMs = p.end * 1000 + END_PAD_MS
                    if (p.start * 1000 <= now) {
                        startRecording(endMs, title)
                    } else {
                        val startMs = p.start * 1000 - START_PAD_MS
                        val conflict = app.recordings.conflicts(i, startMs, endMs)
                        app.recordings.schedule(i, title, startMs, endMs)
                        info = ctx.getString(R.string.rec_scheduled) + if (conflict) " " + ctx.getString(R.string.rec_conflict) else ""
                    }
                }
            }, onDismiss = { guideDialog = false })
        }
    }
    if (listsDialog) item?.let { ListsDialog(vm, it) { listsDialog = false } }
    if (removeFavDialog) {
        item?.let { i ->
            RemoveFavoriteDialog(i.name, onConfirm = {
                favLists.firstOrNull()?.let { list -> vm.run { vm.repo.setFavorite(list.id, i.accountId, i.kind, i.remoteId, i.name, false) } }
                removeFavDialog = false
            }, onDismiss = { removeFavDialog = false })
        } ?: run { removeFavDialog = false }
    }
    if (recordDialog) {
        RecordDialog(nowNext.first?.end?.times(1000), onDismiss = { recordDialog = false }) { stopAt ->
            recordDialog = false
            startRecording(stopAt)
        }
    }
}

@Composable
private fun RecordDialog(programEnd: Long?, onDismiss: () -> Unit, onStart: (Long?) -> Unit) {
    val now = System.currentTimeMillis()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rec_record)) },
        text = {
            Column {
                @Composable
                fun option(label: String, stopAt: Long?) {
                    TextButton(onClick = { onStart(stopAt) }, modifier = Modifier.fillMaxWidth().focusHighlight()) { Text(label, Modifier.fillMaxWidth()) }
                }
                if (programEnd != null && programEnd > now) option(stringResource(R.string.rec_until_program_end), programEnd + 60_000)
                option(stringResource(R.string.rec_minutes, 30), now + 30 * 60_000L)
                option(stringResource(R.string.rec_minutes, 60), now + 60 * 60_000L)
                option(stringResource(R.string.rec_minutes, 120), now + 120 * 60_000L)
                option(stringResource(R.string.rec_until_stopped), null)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TracksDialog(exo: ExoPlayer, tracks: Tracks, onDismiss: () -> Unit) {
    @Composable
    fun section(title: Int, type: Int, canDisable: Boolean) {
        val groups = tracks.groups.filter { it.type == type }
        if (groups.isEmpty() && !canDisable) return
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        if (canDisable) {
            val off = exo.trackSelectionParameters.disabledTrackTypes.contains(type)
            Row(Modifier.fillMaxWidth().focusHighlight().clickable {
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type, true).build()
            }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = off, onClick = null)
                Text(stringResource(R.string.player_off), Modifier.padding(start = 8.dp))
            }
        }
        groups.forEach { g ->
            for (i in 0 until g.length) {
                val f = g.getTrackFormat(i)
                val label = listOfNotNull(f.label, f.language?.takeIf { it != "und" }, f.channelCount.takeIf { it > 0 }?.let { "${it}ch" }).joinToString(" ").ifBlank { "#${i + 1}" }
                val selected = g.isTrackSelected(i) && !exo.trackSelectionParameters.disabledTrackTypes.contains(type)
                Row(Modifier.fillMaxWidth().focusHighlight().clickable {
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
                }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = null)
                    Text(label, Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_tracks)) },
        text = {
            LazyColumn {
                item { section(R.string.player_audio, C.TRACK_TYPE_AUDIO, false) }
                item { section(R.string.player_subtitles, C.TRACK_TYPE_TEXT, true) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun GuideDialog(vm: MainViewModel, item: Item, onPlay: (Program) -> Unit, onRecord: (Program) -> Unit, onDismiss: () -> Unit) {
    var confirm by remember { mutableStateOf<Program?>(null) }
    val programs by produceState<List<Program>?>(null, item.id) {
        value = item.epgId?.let { vm.repo.guide(item.accountId, it, if (item.archiveDays > 0) 3 else 0, 2) } ?: emptyList()
    }
    val now = System.currentTimeMillis() / 1000
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    val day = DateFormat.getDateInstance(DateFormat.SHORT)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            val list = programs
            Column {
                if (item.archiveDays > 0) Text(stringResource(R.string.player_catchup_hint), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.player_record_hint), style = MaterialTheme.typography.bodySmall)
                when {
                    list == null -> Text(stringResource(R.string.series_loading))
                    list.isEmpty() -> Text(stringResource(R.string.epg_none))
                    else -> LazyColumn {
                        items(list, key = { it.id }) { p ->
                            val past = p.end < now && item.archiveDays > 0
                            val current = p.start <= now && p.end > now
                            Column(
                                Modifier.fillMaxWidth().focusHighlight().clickable { if (past) onPlay(p) else confirm = p }.padding(vertical = 6.dp, horizontal = 4.dp),
                            ) {
                                Text(
                                    "${day.format(Date(p.start * 1000))} ${time.format(Date(p.start * 1000))} - ${time.format(Date(p.end * 1000))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                                Text(p.title.decodeEntities(), maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (!past && !current) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
    confirm?.let { p ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(stringResource(R.string.rec_confirm, p.title, time.format(Date(p.start * 1000)), time.format(Date(p.end * 1000)))) },
            confirmButton = { TextButton(onClick = { confirm = null; onRecord(p) }) { Text(stringResource(R.string.rec_record)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private const val SKIP_MS = 10_000L

@Composable
private fun Fade(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (visible) 1f else 0f, androidx.compose.animation.core.tween(140), label = "fade")
    if (visible || alpha > 0.01f) {
        Box(modifier.graphicsLayer { this.alpha = alpha }) { content() }
    }
}

private enum class PlayerGlyph { PLAY, PAUSE, STOP, UP, DOWN, REC, REC_STOP, STAR_ON, STAR_OFF, MENU }

@Composable
private fun RoundControl(glyph: PlayerGlyph, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, diameter: androidx.compose.ui.unit.Dp = 76.dp) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.size(diameter).onFocusChanged { focused = it.hasFocus }
            .background(Color(0xCC000000), CircleShape)
            .border(if (focused) 3.dp else 1.5.dp, if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f), CircleShape)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter * 0.4f)) {
            val w = size.width
            val h = size.height
            when (glyph) {
                PlayerGlyph.PLAY -> drawPath(Path().apply { moveTo(w * 0.12f, 0f); lineTo(w, h / 2); lineTo(w * 0.12f, h); close() }, Color.White)
                PlayerGlyph.PAUSE -> {
                    drawRect(Color.White, Offset(w * 0.08f, 0f), Size(w * 0.28f, h))
                    drawRect(Color.White, Offset(w * 0.64f, 0f), Size(w * 0.28f, h))
                }
                PlayerGlyph.REC -> drawCircle(Color(0xFFFF3B30), radius = w / 2)
                PlayerGlyph.REC_STOP -> drawRoundRect(Color(0xFFFF3B30), Offset(w * 0.06f, h * 0.06f), Size(w * 0.88f, h * 0.88f), androidx.compose.ui.geometry.CornerRadius(w * 0.12f))
                PlayerGlyph.STAR_ON, PlayerGlyph.STAR_OFF -> {
                    val cx = w / 2
                    val cy = h / 2
                    val star = Path().apply {
                        for (k in 0 until 10) {
                            val r = if (k % 2 == 0) w / 2 else w / 4.4f
                            val a = Math.PI / 5 * k - Math.PI / 2
                            val x = cx + (r * Math.cos(a)).toFloat()
                            val y = cy + (r * Math.sin(a)).toFloat()
                            if (k == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                    if (glyph == PlayerGlyph.STAR_ON) drawPath(star, Color(0xFFFFC107))
                    else drawPath(star, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.09f))
                }
                PlayerGlyph.MENU -> for (k in 0..2) drawRoundRect(Color.White, Offset(0f, h * (0.08f + 0.36f * k)), Size(w, h * 0.14f), androidx.compose.ui.geometry.CornerRadius(h * 0.07f))
                PlayerGlyph.UP -> drawPath(Path().apply { moveTo(w / 2, 0f); lineTo(w, h * 0.85f); lineTo(0f, h * 0.85f); close() }, Color.White)
                PlayerGlyph.DOWN -> drawPath(Path().apply { moveTo(0f, h * 0.15f); lineTo(w, h * 0.15f); lineTo(w / 2, h); close() }, Color.White)
                PlayerGlyph.STOP -> drawRoundRect(Color.White, Offset(w * 0.04f, h * 0.04f), Size(w * 0.92f, h * 0.92f), androidx.compose.ui.geometry.CornerRadius(w * 0.1f))
            }
        }
    }
}

@Composable
private fun ChannelPanel(vm: MainViewModel, source: Source, count: Int, selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (selected - 3).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier.fillMaxHeight().width(320.dp).background(Palette.Velvet).statusBarsPadding().navigationBarsPadding(),
        state = state,
    ) {
        items(count, key = { it }) { i ->
            val channel by androidx.compose.runtime.produceState<Item?>(null, i) { value = vm.repo.at(source, i) }
            Row(
                Modifier.fillMaxWidth().then(if (i == selected) Modifier.background(Palette.WineDeep.copy(alpha = 0.7f)).focusRequester(focus) else Modifier)
                    .focusHighlight().clickable { onPick(i) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Logo(channel?.logo, Modifier.size(width = 56.dp, height = 36.dp))
                Text(
                    channel?.let { TitleParser.parse(it.name).title } ?: "", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
