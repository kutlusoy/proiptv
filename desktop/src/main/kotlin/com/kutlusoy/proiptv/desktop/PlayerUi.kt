package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.background
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
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.awt.Canvas
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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
    DisposableEffect(controller) { onDispose { engine.stop(); engine.detach() } }
    val fullscreen = state.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(controller.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            if (!engine.available) {
                Text(engine.error ?: "libVLC not found", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            } else {
                SwingPanel(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        object : Canvas() {
                            override fun addNotify() {
                                super.addNotify()
                                engine.attach(this)
                            }
                        }.apply {
                            background = java.awt.Color.BLACK
                            isFocusable = false
                            addMouseListener(object : MouseAdapter() {
                                override fun mouseClicked(e: MouseEvent) { if (e.clickCount == 2) state.toggleFullscreen() }
                            })
                            addMouseWheelListener { e -> controller.changeVolume(controller.volume - e.wheelRotation * 5) }
                        }
                    },
                    update = { engine.attach(it) },
                )
            }
            controller.failed?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Row(Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = { controller.stop() }) { Icon(Icons.Filled.Stop, t("player_stop")) }
            IconButton(onClick = { controller.togglePause() }) {
                Icon(if (controller.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, t(if (controller.paused) "player_play" else "player_pause"))
            }
            if (controller.canZap) {
                IconButton(onClick = { controller.zap(-1) }) { Icon(Icons.Filled.KeyboardArrowUp, t("player_channel_prev")) }
                IconButton(onClick = { controller.zap(1) }) { Icon(Icons.Filled.KeyboardArrowDown, t("player_channel_next")) }
            }
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
}
