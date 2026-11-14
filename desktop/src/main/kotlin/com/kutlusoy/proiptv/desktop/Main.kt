package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kutlusoy.proiptv.net.Http

fun main() = application {
    val store = remember { Store(Store.defaultDir()).also { Strings.init(it.config.language) } }
    val scope = rememberCoroutineScope()
    val state = remember { AppState(store, scope) }
    val updater = remember { Updater() }
    val update = remember { UpdateState(updater, scope, store) }
    val screen = remember { java.awt.Toolkit.getDefaultToolkit().screenSize }
    val windowState = rememberWindowState(size = DpSize(minOf(1280, (screen.width * 0.9).toInt()).dp, minOf(800, (screen.height * 0.88).toInt()).dp))

    LaunchedEffect(Unit) {
        state.reload()
        update.startDaily()
    }
    LaunchedEffect(state.placement) { windowState.placement = state.placement }

    Window(
        onCloseRequest = {
            state.engine.release()
            Http.client.dispatcher.executorService.shutdown()
            exitApplication()
        },
        title = "ProIPTV",
        state = windowState,
        icon = painterResource("icon.png"),
        onPreviewKeyEvent = { e ->
            val c = state.controller
            if (c == null || e.type != KeyEventType.KeyDown) false
            else when (e.key) {
                Key.Escape -> { if (state.placement == WindowPlacement.Fullscreen) state.toggleFullscreen() else c.stop(); true }
                Key.Spacebar -> { c.togglePause(); true }
                Key.F, Key.F11 -> { state.toggleFullscreen(); true }
                Key.DirectionUp -> { c.zap(-1); true }
                Key.DirectionDown -> { c.zap(1); true }
                Key.DirectionLeft -> { c.seekBy(-10_000); true }
                Key.DirectionRight -> { c.seekBy(10_000); true }
                Key.M -> { state.engine.setMute(true); true }
                else -> false
            }
        },
    ) {
        window.minimumSize = java.awt.Dimension(640, 420)
        ProIptvTheme {
            Surface(Modifier.fillMaxSize()) {
                App(state, updater, update)
            }
        }
    }
}

@Composable
private fun App(state: AppState, updater: Updater, update: UpdateState) {
    val request = state.player
    if (request != null) {
        val controller = remember(request) { PlayerController(state, request).also { state.controller = it } }
        PlayerScreen(state, controller)
    } else {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                Tab.values().forEach { tab ->
                    NavigationRailItem(
                        selected = state.tab == tab,
                        onClick = { state.tab = tab; state.series = null; state.movie = null },
                        icon = { Icon(icon(tab), null) },
                        label = { Text(t(tab.label)) },
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                when (state.tab) {
                    Tab.LIVE -> BrowseScreen(state, Kind.LIVE)
                    Tab.MOVIES -> BrowseScreen(state, Kind.MOVIE)
                    Tab.SERIES -> BrowseScreen(state, Kind.SERIES)
                    Tab.ACCOUNTS -> AccountsScreen(state)
                    Tab.SETTINGS -> SettingsScreen(state, updater, update)
                }
            }
        }
    }
    update.announce?.let { release ->
        AlertDialog(
            onDismissRequest = { update.announce = null },
            title = { Text(t("update_notification_title")) },
            text = { Text(t("update_available", release.version)) },
            confirmButton = { TextButton(onClick = { update.announce = null; state.tab = Tab.SETTINGS; state.player = null; update.install(release) }) { Text(t("update_install")) } },
            dismissButton = { TextButton(onClick = { update.announce = null }) { Text(t("action_cancel")) } },
        )
    }
}

private fun icon(tab: Tab) = when (tab) {
    Tab.LIVE -> Icons.Filled.LiveTv
    Tab.MOVIES -> Icons.Filled.Movie
    Tab.SERIES -> Icons.Filled.VideoLibrary
    Tab.ACCOUNTS -> Icons.Filled.Person
    Tab.SETTINGS -> Icons.Filled.Settings
}
