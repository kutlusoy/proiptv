package com.kutlusoy.proiptv.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
        setContent {
            ProIptvTheme { AppRoot(vm) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: MainViewModel) {
    val request by vm.player.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.LIVE) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val chromeHidden = remember { mutableStateOf(false) }

    request?.let { r ->
        key(r) { PlayerScreen(vm, r) { vm.player.value = null } }
        return
    }

    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }

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
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (tab) {
                        Tab.LIVE -> BrowseScreen(Kind.LIVE, vm)
                        Tab.MOVIES -> BrowseScreen(Kind.MOVIE, vm)
                        Tab.SERIES -> BrowseScreen(Kind.SERIES, vm)
                        Tab.RECORDINGS -> RecordingsScreen(vm)
                        Tab.ACCOUNTS -> AccountsScreen(vm)
                        Tab.SETTINGS -> SettingsScreen(vm)
                    }
                }
            }
        }
    }
}
