@file:OptIn(UnstableApi::class)

package com.kutlusoy.proiptv.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kutlusoy.proiptv.ProIptvApp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.PlayTarget
import com.kutlusoy.proiptv.data.Program
import com.kutlusoy.proiptv.net.Http
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private fun buildPlayer(ctx: Context): ExoPlayer {
    val load = DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 60_000, 1_500, 3_000).build()
    val renderers = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)
    return ExoPlayer.Builder(ctx, renderers).setLoadControl(load).build()
}

private fun start(exo: ExoPlayer, target: PlayTarget, hls: Boolean) {
    val data = OkHttpDataSource.Factory(Http.client).setUserAgent(target.userAgent?.takeIf { it.isNotBlank() } ?: Http.DEFAULT_AGENT)
    val extractors = DefaultExtractorsFactory()
        .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
        .setConstantBitrateSeekingEnabled(true)
    val item = MediaItem.Builder().setUri(target.url).apply { if (hls) setMimeType(MimeTypes.APPLICATION_M3U8) }.build()
    exo.setMediaSource(DefaultMediaSourceFactory(data, extractors).createMediaSource(item))
    exo.prepare()
    exo.playWhenReady = true
}

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
    var touched by remember { mutableIntStateOf(0) }
    var digits by remember { mutableStateOf("") }
    var resize by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var tracks by remember { mutableStateOf(exo.currentTracks) }
    var tracksDialog by remember { mutableStateOf(false) }
    var guideDialog by remember { mutableStateOf(false) }
    var listsDialog by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val favLists by vm.favLists.collectAsState()
    val favKeys by vm.favKeys.collectAsState()
    val root = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    val active = catchup ?: target
    val zapping = request is PlayRequest.Zap && catchup == null
    val dialogOpen = tracksDialog || guideDialog || listsDialog

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
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    retry = 0
                    message = null
                }
            }

            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
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
        target = i?.let { vm.repo.target(it) }
        overlay = true
        touched++
    }

    LaunchedEffect(active, reload, forceHls) {
        val t = active ?: return@LaunchedEffect
        start(exo, t, forceHls)
        if (!t.live) {
            val saved = vm.repo.progress(t)?.positionMs ?: 0
            if (saved > 5_000) exo.seekTo(saved)
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
            delay(500)
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

    BackHandler { onClose() }

    fun zap(delta: Int) {
        if (!zapping) return
        index = ((index + delta) % count + count) % count
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(root)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || dialogOpen) return@onPreviewKeyEvent false
                touched++
                val code = e.key.nativeKeyCode
                when {
                    e.key == Key.DirectionUp || e.key == Key.ChannelDown || e.key == Key.PageUp -> {
                        if (zapping) { zap(-1); true } else { overlay = true; false }
                    }
                    e.key == Key.DirectionDown || e.key == Key.ChannelUp || e.key == Key.PageDown -> {
                        if (zapping) { zap(1); true } else { overlay = true; false }
                    }
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
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { overlay = !overlay },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c -> PlayerView(c).apply { useController = false; player = exo; keepScreenOn = true } },
            update = { it.resizeMode = resize },
        )
        message?.let { Text(stringResource(it), Modifier.align(Alignment.Center).background(Color(0xAA000000)).padding(12.dp), color = Color.White) }
        if (digits.isNotEmpty()) {
            Text(digits, Modifier.align(Alignment.TopEnd).padding(24.dp).background(Color(0xAA000000)).padding(12.dp), style = MaterialTheme.typography.displaySmall, color = Color.White)
        }
        AnimatedVisibility(overlay, Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(active?.title ?: "", style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (active?.live == true) {
                    val pair by produceState<Pair<Program?, Program?>>(null to null, item, position / 60_000) {
                        val i = item
                        value = if (i == null) null to null else vm.repo.nowAndNext(i.accountId, i.epgId)
                    }
                    pair.first?.let { now ->
                        val total = (now.end - now.start).coerceAtLeast(1)
                        val done = ((System.currentTimeMillis() / 1000 - now.start).toFloat() / total).coerceIn(0f, 1f)
                        Text("${stringResource(R.string.player_now)}: ${now.title}", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LinearProgressIndicator(progress = { done }, Modifier.fillMaxWidth())
                    }
                    pair.second?.let { Text("${stringResource(R.string.player_next)}: ${it.title}", color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                } else if (duration > 0) {
                    Slider(
                        value = (position.toFloat() / duration).coerceIn(0f, 1f),
                        onValueChange = { exo.seekTo((it * duration).toLong()); position = exo.currentPosition; touched++ },
                        modifier = Modifier.focusHighlight(),
                    )
                    Text("${clock(position)} / ${clock(duration)}", color = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val i = item
                    if (zapping && i != null) {
                        val fav = Triple(i.accountId, i.kind, i.remoteId) in favKeys
                        OutlinedButton(
                            onClick = {
                                if (favLists.size <= 1) {
                                    val list = favLists.firstOrNull() ?: return@OutlinedButton
                                    vm.run { vm.repo.setFavorite(list.id, i.accountId, i.kind, i.remoteId, i.name, !fav) }
                                } else {
                                    listsDialog = true
                                }
                            },
                            modifier = Modifier.focusRequester(first).focusHighlight(),
                        ) { Text(stringResource(if (fav) R.string.fav_remove else R.string.fav_add)) }
                    }
                    if (zapping && i?.epgId != null) {
                        OutlinedButton(onClick = { guideDialog = true }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.player_guide)) }
                    }
                    if (active?.live == false) {
                        OutlinedButton(onClick = { if (exo.isPlaying) exo.pause() else exo.play() }, modifier = Modifier.focusRequester(first).focusHighlight()) { Text("⏯") }
                    }
                    OutlinedButton(onClick = { tracksDialog = true }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.player_tracks)) }
                    OutlinedButton(
                        onClick = {
                            resize = when (resize) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        modifier = Modifier.focusHighlight(),
                    ) { Text(stringResource(R.string.player_aspect)) }
                }
            }
        }
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
            }, onDismiss = { guideDialog = false })
        }
    }
    if (listsDialog) item?.let { ListsDialog(vm, it) { listsDialog = false } }
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
private fun GuideDialog(vm: MainViewModel, item: Item, onPlay: (Program) -> Unit, onDismiss: () -> Unit) {
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
                when {
                    list == null -> Text(stringResource(R.string.series_loading))
                    list.isEmpty() -> Text(stringResource(R.string.epg_none))
                    else -> LazyColumn {
                        items(list, key = { it.id }) { p ->
                            val past = p.end < now && item.archiveDays > 0
                            val current = p.start <= now && p.end > now
                            Column(
                                Modifier.fillMaxWidth().focusHighlight().clickable(enabled = past || current) { if (past) onPlay(p) else onDismiss() }.padding(vertical = 6.dp, horizontal = 4.dp),
                            ) {
                                Text(
                                    "${day.format(Date(p.start * 1000))} ${time.format(Date(p.start * 1000))} - ${time.format(Date(p.end * 1000))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                                Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (!past && !current) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
