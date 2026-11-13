package com.kutlusoy.proiptv.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Kind

private enum class Tab(val label: Int, val icon: ImageVector) {
    LIVE(R.string.nav_live, Icons.Filled.PlayArrow),
    MOVIES(R.string.nav_movies, Icons.Filled.AccountBox),
    SERIES(R.string.nav_series, Icons.Filled.Menu),
    ACCOUNTS(R.string.nav_accounts, Icons.Filled.Person),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings),
}

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        vm.repo.sync.syncStaleGuides()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = isTelevision(this)
        setContent {
            ProIptvTheme(forceDark = tv) { AppRoot(vm) }
        }
    }
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    val request by vm.player.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.LIVE) }
    val wide = LocalConfiguration.current.screenWidthDp >= 720

    request?.let { r ->
        key(r) { PlayerScreen(vm, r) { vm.player.value = null } }
        return
    }

    val content: @Composable () -> Unit = {
        when (tab) {
            Tab.LIVE -> BrowseScreen(Kind.LIVE, vm)
            Tab.MOVIES -> BrowseScreen(Kind.MOVIE, vm)
            Tab.SERIES -> BrowseScreen(Kind.SERIES, vm)
            Tab.ACCOUNTS -> AccountsScreen(vm)
            Tab.SETTINGS -> SettingsScreen(vm)
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.values().forEach { t ->
                    NavigationRailItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { Icon(t.icon, null) }, label = { Text(stringResource(t.label)) },
                        modifier = Modifier.focusHighlight(),
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) { content() }
        }
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.values().forEach { t ->
                        NavigationBarItem(
                            selected = tab == t, onClick = { tab = t },
                            icon = { Icon(t.icon, null) }, label = { Text(stringResource(t.label), maxLines = 1) },
                        )
                    }
                }
            },
        ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { content() } }
    }
}
