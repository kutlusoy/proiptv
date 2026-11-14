package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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

/** Holds what is playing right now, so the screen and the keyboard shortcuts of the window use the same functions. */
class PlayerController(private val state: AppState, private val request: PlayRequest) {
    private val engine get() = state.engine
    var index by mutableIntStateOf(if (request is PlayRequest.Live) request.index else 0)
    var title by mutableStateOf("")
    var paused by mutableStateOf(false)
    var volume by mutableIntStateOf(state.store.config.volume)
    var position by mutableLongStateOf(0)
    var length by mutableLongStateOf(0)
    var failed by mutableStateOf<String?>(null)

    val live: Boolean get() = request is PlayRequest.Live
    val canZap: Boolean get() = request is PlayRequest.Live && request.entries.size > 1

    fun start() {
        val item = when (request) {
            is PlayRequest.Single -> request.item
            is PlayRequest.Live -> state.library.playable(request.entries[index])
        }
        if (item == null) { failed = "No address"; return }
        title = item.title
        paused = false
        failed = null
        engine.play(item, volume)
    }

    fun zap(delta: Int) {
        val r = request as? PlayRequest.Live ?: return
        if (r.entries.size < 2) return
        index = ((index + delta) % r.entries.size + r.entries.size) % r.entries.size
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
        engine.stop()
        state.player = null
        state.controller = null
        if (state.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen) state.toggleFullscreen()
    }

    fun poll() {
        position = engine.time()
        length = engine.length()
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(state: AppState, controller: PlayerController) {
    val engine = state.engine
    LaunchedEffect(controller) { controller.start() }
    LaunchedEffect(controller) {
        while (true) {
            controller.poll()
            delay(500)
        }
    }
    DisposableEffect(controller) { onDispose { engine.stop() } }
    val fullscreen = state.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen

    var barVisible by remember { mutableStateOf(true) }
    var lastMove by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(fullscreen) {
        barVisible = true
        while (fullscreen) {
            delay(500)
            barVisible = System.currentTimeMillis() - lastMove < 3000
        }
        barVisible = true
    }

    val video: @Composable (Modifier) -> Unit = { modifier ->
        if (!engine.available) {
            Text(engine.error ?: "libVLC not found", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
        } else {
            VideoSurface(engine, modifier, onDoubleClick = { state.toggleFullscreen() }, onScroll = { dy -> controller.changeVolume(controller.volume - dy * 5) })
        }
        controller.failed?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    if (fullscreen) {
        // the picture fills the screen, the controls float above it and fade away when the mouse rests
        Box(
            Modifier.fillMaxSize().background(Color.Black).onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move) {
                lastMove = System.currentTimeMillis()
                barVisible = true
            },
            contentAlignment = Alignment.Center,
        ) {
            video(Modifier.fillMaxSize())
            if (barVisible) ControlBar(state, controller, Modifier.align(Alignment.BottomCenter), floating = true)
        }
    } else {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Row(Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(controller.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) { video(Modifier.fillMaxSize()) }
            ControlBar(state, controller, Modifier, floating = false)
        }
    }
}

@Composable
private fun ControlBar(state: AppState, controller: PlayerController, modifier: Modifier, floating: Boolean) {
    Row(
        modifier.fillMaxWidth().background(if (floating) Color(0xCC000000) else Palette.Surface).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = { controller.stop() }) { Icon(Icons.Filled.Stop, t("player_stop")) }
        IconButton(onClick = { controller.togglePause() }) {
            Icon(if (controller.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, t(if (controller.paused) "player_play" else "player_pause"))
        }
        if (controller.canZap) {
            IconButton(onClick = { controller.zap(-1) }) { Icon(Icons.Filled.KeyboardArrowUp, t("player_channel_prev")) }
            IconButton(onClick = { controller.zap(1) }) { Icon(Icons.Filled.KeyboardArrowDown, t("player_channel_next")) }
        }
        if (floating) Text(controller.title, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        if (!controller.live && controller.length > 0) {
            Text(clock(controller.position), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = (controller.position.toFloat() / controller.length).coerceIn(0f, 1f),
                onValueChange = { state.engine.seek((it * controller.length).toLong()); controller.poll() },
                modifier = Modifier.weight(1f),
            )
            Text(clock(controller.length), style = MaterialTheme.typography.bodySmall)
        } else {
            Box(Modifier.weight(1f))
        }
        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(20.dp))
        Slider(value = controller.volume / 200f, onValueChange = { controller.changeVolume((it * 200).toInt()) }, modifier = Modifier.width(120.dp))
        IconButton(onClick = { state.toggleFullscreen() }) { Icon(Icons.Filled.Fullscreen, null) }
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
