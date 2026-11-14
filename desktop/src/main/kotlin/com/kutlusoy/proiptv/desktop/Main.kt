package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FiberManualRecord
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

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { t, e -> Log.write("error in ${t.name}: ${e.stackTraceToString().take(1500)}") }
    runApplication()
}

private fun runApplication() = application {
    Log.trim()
    Log.write("ProIPTV ${AppInfo.label} started")
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
    // the clock of the guide, and a new guide every 6 hours
    LaunchedEffect(Unit) {
        var minutes = 0
        while (true) {
            kotlinx.coroutines.delay(60_000)
            state.tick = System.currentTimeMillis() / 1000
            Log.trimPlayerLog()
            if (++minutes % 360 == 0) state.refreshGuide()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(15_000)
            state.checkScheduled()
        }
    }
    LaunchedEffect(state.placement) { windowState.placement = state.placement }

    // AWT cannot make a shown window undecorated, so the window is created again when full screen changes (the state lives in AppState)
    androidx.compose.runtime.key(state.placement == WindowPlacement.Fullscreen) {
    Window(
        onCloseRequest = {
            state.recordingManager.stopAll()
            state.hubs.closeAll()
            state.engine.release()
            Http.client.dispatcher.executorService.shutdown()
            exitApplication()
        },
        title = "ProIPTV",
        state = windowState,
        undecorated = state.placement == WindowPlacement.Fullscreen,
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
}

@Composable
private fun App(state: AppState, updater: Updater, update: UpdateState) {
    val request = state.player
    if (request != null) {
        val controller = remember(request) { state.controller?.takeIf { it.isFor(request) } ?: PlayerController(state, request).also { state.controller = it } }
        PlayerScreen(state, controller)
    } else {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(Modifier.width(112.dp), containerColor = Palette.Surface, header = { Spacer(Modifier.height(16.dp)) }) {
                Tab.values().forEach { tab ->
                    NavigationRailItem(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        selected = state.tab == tab,
                        onClick = { state.tab = tab; state.series = null; state.movie = null },
                        icon = { Icon(icon(tab), null) },
                        label = { Text(t(tab.label)) },
                    )
                }
            }
            Box(Modifier.fillMaxSize().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                when (state.tab) {
                    Tab.HOME -> HomeScreen(state)
                    Tab.LIVE -> BrowseScreen(state, Kind.LIVE)
                    Tab.MOVIES -> BrowseScreen(state, Kind.MOVIE)
                    Tab.SERIES -> BrowseScreen(state, Kind.SERIES)
                    Tab.EPG -> EpgScreen(state)
                    Tab.DOWNLOADS -> DownloadsScreen(state)
                    Tab.RECORDINGS -> RecordingsScreen(state)
                    Tab.ACCOUNTS -> AccountsScreen(state)
                    Tab.SETTINGS -> SettingsScreen(state, updater, update)
                }
            }
        }
    }
    state.confirmRemove?.let { e ->
        AlertDialog(
            onDismissRequest = { state.confirmRemove = null },
            text = { Text(t("fav_remove_confirm", e.name)) },
            confirmButton = { TextButton(onClick = { state.removeFromAll(e); state.confirmRemove = null }) { Text(t("fav_remove")) } },
            dismissButton = { TextButton(onClick = { state.confirmRemove = null }) { Text(t("action_cancel")) } },
        )
    }
    state.listsFor?.let { e ->
        AlertDialog(
            onDismissRequest = { state.listsFor = null },
            title = { Text(t("fav_choose_lists")) },
            text = {
                androidx.compose.foundation.layout.Column {
                    state.listsOf(e.kind).forEach { list ->
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().clickable { state.setInList(e, list.id, !state.inList(e, list.id)) },
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Checkbox(state.inList(e, list.id), onCheckedChange = null)
                            Text(list.name, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { state.listsFor = null }) { Text(t("fav_done")) } },
        )
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
    Tab.HOME -> Icons.Filled.Home
    Tab.LIVE -> Icons.Filled.LiveTv
    Tab.MOVIES -> Icons.Filled.Movie
    Tab.SERIES -> Icons.Filled.VideoLibrary
    Tab.EPG -> Icons.Filled.Event
    Tab.DOWNLOADS -> Icons.Filled.Download
    Tab.RECORDINGS -> Icons.Filled.FiberManualRecord
    Tab.ACCOUNTS -> Icons.Filled.Person
    Tab.SETTINGS -> Icons.Filled.Settings
}
