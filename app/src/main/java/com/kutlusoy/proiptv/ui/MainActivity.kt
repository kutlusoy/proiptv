package com.kutlusoy.proiptv.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.BuildConfig
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Kind
import kotlinx.coroutines.launch

private enum class Tab(val label: Int, val icon: ImageVector) {
    LIVE(R.string.nav_live, Icons.Filled.PlayArrow),
    MOVIES(R.string.nav_movies, Icons.Filled.AccountBox),
    SERIES(R.string.nav_series, Icons.AutoMirrored.Filled.List),
    RECORDINGS(R.string.nav_recordings, Icons.Filled.DateRange),
    DOWNLOADS(R.string.nav_downloads, DownloadIcon),
    EPG(R.string.nav_epg, GuideIcon),
    ACCOUNTS(R.string.nav_accounts, Icons.Filled.Person),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings),
}

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    override fun onStart() {
        super.onStart()
        vm.repo.sync.syncStaleGuides()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersive()
        val systemTv = isTelevision(this)
        setContent {
            val mode by vm.uiMode.collectAsState()
            ProIptvTheme(useTvLayout(mode, systemTv)) { AppRoot(vm) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: MainViewModel) {
    val request by vm.player.collectAsState()
    val prefs = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.kutlusoy.proiptv.ProIptvApp).prefs
    var tab by rememberSaveable { mutableStateOf(Tab.values().firstOrNull { it.name == prefs.lastTab } ?: Tab.LIVE) }
    LaunchedEffect(tab) { prefs.lastTab = tab.name }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val chromeHidden = remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    if (confirmExit) {
        val activity = androidx.activity.compose.LocalActivity.current
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmExit = false },
            text = { Text(stringResource(R.string.exit_confirm)) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { activity?.finishAffinity() }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.nav_exit)) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmExit = false }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    request?.let { r ->
        key(r) { PlayerScreen(vm, r) { vm.player.value = null } }
        return
    }

    BackHandler { confirmExit = true }
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }

    val ctx = LocalContext.current
    val exit: () -> Unit = { (ctx as? android.app.Activity)?.finishAffinity() }
    val tv = LocalTv.current
    val content: @Composable () -> Unit = {
        holder.SaveableStateProvider(tab.name) {
            when (tab) {
                Tab.LIVE -> BrowseScreen(Kind.LIVE, vm)
                Tab.MOVIES -> BrowseScreen(Kind.MOVIE, vm)
                Tab.SERIES -> BrowseScreen(Kind.SERIES, vm)
                Tab.EPG -> EpgScreen(vm)
                Tab.RECORDINGS -> RecordingsScreen(vm)
                Tab.DOWNLOADS -> DownloadsScreen(vm)
                Tab.ACCOUNTS -> AccountsScreen(vm)
                Tab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }

    if (tv) {
        CompositionLocalProvider(LocalChromeHidden provides chromeHidden) {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (!chromeHidden.value) TvRail(tab, exit) { tab = it }
                Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 36.dp, vertical = 24.dp)) { content() }
            }
        }
        return
    }

    CompositionLocalProvider(LocalChromeHidden provides chromeHidden) {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = !chromeHidden.value,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Palette.Surface) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                        Text("ProIPTV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Palette.WineSoft)
                        Text(stringResource(R.string.main_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Tab.values().forEach { t ->
                            NavigationDrawerItem(
                                label = { Text(stringResource(t.label)) },
                                icon = { Icon(t.icon, null) },
                                selected = tab == t,
                                onClick = { tab = t; scope.launch { drawer.close() } },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Palette.WineDeep,
                                    selectedIconColor = Color.White,
                                    selectedTextColor = Color.White,
                                    unselectedContainerColor = Color.Transparent,
                                ),
                                modifier = Modifier.focusHighlight(),
                            )
                        }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.nav_exit)) },
                        icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                        selected = false,
                        onClick = exit,
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                        modifier = Modifier.focusHighlight(),
                    )
                    }
                }
            },
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    if (!chromeHidden.value) {
                        TopAppBar(
                            title = { Text(stringResource(tab.label), fontWeight = FontWeight.SemiBold) },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawer.open() } }, modifier = Modifier.focusHighlight()) {
                                    Icon(Icons.Filled.Menu, stringResource(R.string.nav_menu))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) { content() }
            }
        }
    }
}

@Composable
private fun TvRail(selected: Tab, exit: () -> Unit, onSelect: (Tab) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(Modifier.width(250.dp).fillMaxHeight().background(Palette.Surface).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 14.dp, vertical = 16.dp)) {
        Text("ProIPTV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Palette.WineSoft, modifier = Modifier.padding(start = 10.dp))
        Text(stringResource(R.string.main_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(start = 10.dp, bottom = 10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Tab.values().forEach { t ->
                NavigationDrawerItem(
                    label = { Text(stringResource(t.label), style = MaterialTheme.typography.bodyLarge) },
                    icon = { Icon(t.icon, null) },
                    selected = selected == t,
                    onClick = { onSelect(t) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Palette.WineDeep,
                        selectedIconColor = Color.White,
                        selectedTextColor = Color.White,
                        unselectedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.height(50.dp).then(if (selected == t) Modifier.focusRequester(first) else Modifier).focusHighlight(),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.nav_exit), style = MaterialTheme.typography.bodyLarge) },
            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
            selected = false,
            onClick = exit,
            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
            modifier = Modifier.height(50.dp).focusHighlight(),
        )
    }
}
