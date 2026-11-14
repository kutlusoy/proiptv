package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.update.ReleaseInfo
import com.kutlusoy.proiptv.update.UpdateException
import kotlin.system.exitProcess
import kotlinx.coroutines.launch

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class UpToDate(val version: String) : UpdateUi
    data class Available(val release: ReleaseInfo) : UpdateUi
    data class Downloading(val percent: Int) : UpdateUi
    data class Error(val code: UpdateException.Code, val detail: String?) : UpdateUi
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.WineSoft)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))

@Composable
fun SettingsScreen(state: AppState, updater: Updater, update: UpdateState) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)) {
        item {
            Text(t("nav_settings"), style = MaterialTheme.typography.headlineMedium)
            Group(t("settings_group_general")) {
                Text(t("settings_language"), style = MaterialTheme.typography.labelLarge)
                DropdownField(
                    Strings.languages.firstOrNull { it.first == Strings.language }?.second ?: "English", Strings.languages,
                    { code -> Strings.language = code; state.store.update { it.copy(language = code) } }, Modifier.width(320.dp),
                )
            }
            Group(t("settings_group_playback")) {
                val vlc = state.engine
                Text(if (vlc.available) "libVLC" else (vlc.error ?: "libVLC not found"), color = if (vlc.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                Hint("LGPL 2.1, loaded as a separate library")
            }
            Group(t("settings_about")) {
                Text("ProIPTV " + t("main_version", AppInfo.label))
                Hint(t("settings_license"))
                Hint("github.com/kutlusoy/proiptv")
            }
            UpdateGroup(state, updater, update)
        }
    }
}

/** State of the update check, kept outside of the settings screen so the daily check can use it as well. */
class UpdateState(private val updater: Updater, private val scope: kotlinx.coroutines.CoroutineScope, private val store: Store) {
    var ui by mutableStateOf<UpdateUi>(UpdateUi.Idle)
    var announce by mutableStateOf<ReleaseInfo?>(null)

    fun check(background: Boolean = false) {
        if (ui is UpdateUi.Checking || ui is UpdateUi.Downloading) return
        if (!background) ui = UpdateUi.Checking
        scope.launch {
            try {
                when (val r = updater.check()) {
                    is Updater.Result.Available -> {
                        ui = UpdateUi.Available(r.release)
                        val today = System.currentTimeMillis() / DAY_MS
                        if (background && store.config.lastUpdateNoticeDay != today) {
                            store.update { it.copy(lastUpdateNoticeDay = today) }
                            announce = r.release
                        }
                    }
                    is Updater.Result.UpToDate -> if (!background) ui = UpdateUi.UpToDate(r.version)
                }
                store.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            } catch (e: UpdateException) {
                if (!background) ui = UpdateUi.Error(e.code, e.message)
            } catch (e: Exception) {
                if (!background) ui = UpdateUi.Error(UpdateException.Code.NETWORK, e.message)
            }
        }
    }

    fun install(release: ReleaseInfo) {
        ui = UpdateUi.Downloading(0)
        scope.launch {
            try {
                val file = updater.download(release) { ui = UpdateUi.Downloading(it) }
                Updater.runInstaller(file)
                exitProcess(0)
            } catch (e: UpdateException) {
                ui = UpdateUi.Error(e.code, e.message)
            } catch (e: Exception) {
                ui = UpdateUi.Error(UpdateException.Code.INSTALL_FAILED, e.message)
            }
        }
    }

    /** Runs once at startup and then every few hours while the app stays open. One notice per day at most. */
    fun startDaily() {
        scope.launch {
            while (true) {
                if (store.config.autoUpdate && System.currentTimeMillis() - store.config.lastUpdateCheck > DAY_MS) check(background = true)
                kotlinx.coroutines.delay(60 * 60 * 1000L)
            }
        }
    }

    companion object { const val DAY_MS = 24L * 60 * 60 * 1000 }
}

@Composable
private fun UpdateGroup(state: AppState, updater: Updater, update: UpdateState) {
    var auto by remember { mutableStateOf(state.store.config.autoUpdate) }
    val last = state.store.config.lastUpdateCheck
    Group(t("update_title")) {
        Text(t("update_current", AppInfo.label))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("update_auto"))
                Hint(t("update_auto_hint"))
            }
            Switch(checked = auto, onCheckedChange = { auto = it; state.store.update { c -> c.copy(autoUpdate = it) } })
        }
        val busy = update.ui is UpdateUi.Checking || update.ui is UpdateUi.Downloading
        OutlinedButton(onClick = { update.check() }, enabled = !busy) { Text(t("update_check")) }
        Hint(if (last > 0) t("update_last_check", java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(last))) else t("update_never"))
        when (val st = update.ui) {
            UpdateUi.Idle -> Unit
            UpdateUi.Checking -> Text(t("update_checking"))
            is UpdateUi.UpToDate -> Text(t("update_up_to_date", st.version), color = Palette.WineSoft)
            is UpdateUi.Available -> {
                Text(t("update_available", st.release.version), color = Palette.WineSoft)
                Button(onClick = { update.install(st.release) }) { Text(t("update_install")) }
            }
            is UpdateUi.Downloading -> {
                Text(t("update_downloading", st.percent))
                LinearProgressIndicator(progress = { st.percent / 100f }, modifier = Modifier.fillMaxWidth())
            }
            is UpdateUi.Error -> Text(errorText(st), color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun errorText(e: UpdateUi.Error): String = when (e.code) {
    UpdateException.Code.NETWORK -> Strings.get("update_err_network")
    UpdateException.Code.NOT_FOUND -> Strings.get("update_err_not_found")
    UpdateException.Code.NO_APK -> Strings.get("update_err_no_apk")
    UpdateException.Code.NO_CHECKSUM -> Strings.get("update_err_no_checksum")
    UpdateException.Code.CHECKSUM_MISMATCH -> Strings.get("update_err_checksum")
    UpdateException.Code.INSECURE_URL -> Strings.get("update_err_insecure")
    UpdateException.Code.TOO_LARGE -> Strings.get("update_err_too_large")
    UpdateException.Code.STORAGE -> Strings.get("update_err_storage")
    UpdateException.Code.INSTALL_FAILED -> Strings.get("update_err_install", e.detail.orEmpty())
}
