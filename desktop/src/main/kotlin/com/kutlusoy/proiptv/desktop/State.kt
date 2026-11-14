package com.kutlusoy.proiptv.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.WindowPlacement
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class Tab(val label: String) {
    LIVE("nav_live"), MOVIES("nav_movies"), SERIES("nav_series"), EPG("nav_epg"), ACCOUNTS("nav_accounts"), SETTINGS("nav_settings"),
}

sealed interface PlayRequest {
    class Live(val entries: List<Entry>, val index: Int) : PlayRequest
    class Single(val item: Playable) : PlayRequest
}

class AppState(val store: Store, val scope: CoroutineScope) {
    val library = Library(store)
    val engine by lazy { VlcEngine() }

    var tab by mutableStateOf(Tab.LIVE)
    var data by mutableStateOf(LibraryData())
    var loading by mutableStateOf(false)
    var guide by mutableStateOf(Guide())
    var guideLoading by mutableStateOf(false)
    var tick by mutableStateOf(System.currentTimeMillis() / 1000)
    var accounts by mutableStateOf(store.config.accounts)
    var favorites by mutableStateOf(store.config.favorites)
    var player by mutableStateOf<PlayRequest?>(null)
    var placement by mutableStateOf(WindowPlacement.Floating)
    var series by mutableStateOf<Entry?>(null)
    var movie by mutableStateOf<Entry?>(null)
    var controller by mutableStateOf<PlayerController?>(null)

    fun reload() {
        if (loading) return
        loading = true
        scope.launch {
            data = library.load()
            loading = false
            refreshGuide()
        }
    }

    /** Loads the program guide again, also by hand from the accounts and the settings. */
    fun refreshGuide() {
        if (guideLoading) return
        guideLoading = true
        scope.launch {
            try {
                guide = library.loadGuide(data.entries(Kind.LIVE))
            } finally {
                guideLoading = false
            }
        }
    }

    fun saveAccount(a: AccountDto) {
        store.update { c -> c.copy(accounts = if (c.accounts.any { it.id == a.id }) c.accounts.map { if (it.id == a.id) a else it } else c.accounts + a) }
        accounts = store.config.accounts
        reload()
    }

    fun deleteAccount(id: String) {
        store.update { c -> c.copy(accounts = c.accounts.filter { it.id != id }, favorites = c.favorites.filter { it.account != id }) }
        accounts = store.config.accounts
        favorites = store.config.favorites
        reload()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        store.update { c -> c.copy(accounts = c.accounts.map { if (it.id == id) it.copy(enabled = enabled) else it }) }
        accounts = store.config.accounts
        reload()
    }

    fun isFavorite(e: Entry) = favorites.any { it.account == e.accountId && it.kind == e.kind && it.remoteId == e.remoteId }

    fun toggleFavorite(e: Entry) {
        store.update { c ->
            val has = c.favorites.any { it.account == e.accountId && it.kind == e.kind && it.remoteId == e.remoteId }
            c.copy(favorites = if (has) c.favorites.filterNot { it.account == e.accountId && it.kind == e.kind && it.remoteId == e.remoteId }
            else c.favorites + FavoriteDto(e.accountId, e.kind, e.remoteId, e.name))
        }
        favorites = store.config.favorites
    }

    fun newAccountId(): String = UUID.randomUUID().toString()

    fun toggleFullscreen() {
        placement = if (placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
    }
}
