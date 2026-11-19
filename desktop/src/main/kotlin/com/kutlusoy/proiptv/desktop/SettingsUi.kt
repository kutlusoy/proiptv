package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
    data class Downloaded(val release: ReleaseInfo, val pkg: Updater.Package) : UpdateUi
    data object Installing : UpdateUi
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
                var intro by remember { mutableStateOf(state.store.config.intro) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("settings_intro"), Modifier.weight(1f))
                    Switch(checked = intro, onCheckedChange = { intro = it; state.store.update { c -> c.copy(intro = it) } })
                }
                Text(t("settings_language"), style = MaterialTheme.typography.labelLarge)
                DropdownField(
                    Strings.languages.firstOrNull { it.first == Strings.language }?.second ?: "English", Strings.languages,
                    { code -> Strings.language = code; state.store.update { it.copy(language = code) } }, Modifier.widthIn(max = 320.dp).fillMaxWidth(),
                )
            }
            Group(t("settings_group_data")) {
                Text(t("nav_accounts"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { state.reload() }, enabled = !state.loading) { Text(t("settings_refresh")) }
                    OutlinedButton(onClick = { state.refreshGuide() }, enabled = !state.guideLoading) { Text(t("account_sync_guide")) }
                }
                Hint(t("settings_guide_info"))
            }
            Group(t("settings_group_content")) {
                var hide by remember { mutableStateOf(state.store.config.hideAdult) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("settings_hide_adult"), Modifier.weight(1f))
                    Switch(checked = hide, onCheckedChange = { hide = it; state.store.update { c -> c.copy(hideAdult = it) }; state.reload() })
                }
                Hint(t("settings_hide_adult_hint"))
            }
            Group(t("settings_group_playback")) {
                val vlc = state.engine
                Text(if (vlc.available) "libVLC" else (vlc.error ?: "libVLC not found"), color = if (vlc.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                Hint("LGPL 2.1, loaded as a separate library")
                var ts by remember { mutableStateOf(state.store.config.timeshift) }
                var mb by remember { mutableStateOf(state.store.config.bufferMb) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("settings_timeshift"), Modifier.weight(1f))
                    Switch(checked = ts, onCheckedChange = { ts = it; state.store.update { c -> c.copy(timeshift = it) } })
                }
                if (ts) {
                    Text(t("settings_buffer"), style = MaterialTheme.typography.labelLarge)
                    DropdownField("$mb MB", listOf(128, 256, 512, 1024, 2048).map { it to "$it MB" }, { mb = it; state.store.update { c -> c.copy(bufferMb = it) } }, Modifier.widthIn(max = 320.dp).fillMaxWidth())
                    Hint(t("settings_buffer_hint"))
                }
            }
            Group(t("settings_about")) {
                Text("ProIPTV " + t("main_version", AppInfo.label))
                Hint(t("settings_license"))
                Hint("github.com/kutlusoy/proiptv")
                Text("Ali Kutlusoy")
                Text(
                    "kutlusoy.dev@gmail.com", color = Palette.WineSoft,
                    modifier = Modifier.clickable { runCatching { java.awt.Desktop.getDesktop().mail(java.net.URI("mailto:kutlusoy.dev@gmail.com")) } },
                )
            }
            Group(t("settings_storage")) { StorageGroup(state) }
            Group(t("settings_backup")) { BackupGroup(state) }
            UpdateGroup(state, updater, update)
            Group(t("settings_tmdb")) {
                var key by remember { mutableStateOf(Secrets.unprotect(state.store.config.tmdbKey)) }
                TextInput(
                    key, { key = it; state.store.update { c -> c.copy(tmdbKey = Secrets.protect(it.trim())) } }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("settings_tmdb_key")) },
                )
                Hint(t("settings_tmdb_hint"))
            }
            // the people who helped, from supporters.md in the root folder of the project
            val supporters = remember { loadSupporters() }
            if (supporters.isNotEmpty()) {
                Group(t("settings_thanks")) {
                    supporters.forEach { section ->
                        if (section.title.isNotEmpty()) Text(
                            section.title, color = Palette.WineSoft, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            style = if (section.level <= 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        )
                        section.lines.forEach { Text(it) }
                    }
                }
            }

        }
    }
}

/** State of the update check, kept outside of the settings screen so the daily check can use it as well. */
class UpdateState(private val updater: Updater, private val scope: kotlinx.coroutines.CoroutineScope, private val store: Store) {
    var ui by mutableStateOf<UpdateUi>(UpdateUi.Idle)
    var announce by mutableStateOf<ReleaseInfo?>(null)

    fun check(background: Boolean = false) {
        if (ui is UpdateUi.Checking || ui is UpdateUi.Downloading || ui is UpdateUi.Installing) return
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

    /** Step one: download and check the file. Nothing is started yet. */
    fun download(release: ReleaseInfo) {
        ui = UpdateUi.Downloading(0)
        scope.launch {
            try {
                val pkg = updater.download(release) { ui = UpdateUi.Downloading(it) }
                ui = UpdateUi.Downloaded(release, pkg)
            } catch (e: UpdateException) {
                ui = UpdateUi.Error(e.code, e.message)
            } catch (e: Exception) {
                ui = UpdateUi.Error(UpdateException.Code.NETWORK, e.message)
            }
        }
    }

    /** Step two, when the person asks for it: the file is checked again and the setup is started, then the app ends. */
    fun install(release: ReleaseInfo, pkg: Updater.Package) {
        ui = UpdateUi.Installing
        scope.launch {
            try {
                updater.install(pkg)
                exitProcess(0)
            } catch (e: UpdateException) {
                Log.write("Update: ${e.code} ${e.message}")
                ui = UpdateUi.Error(e.code, e.message)
            } catch (e: Exception) {
                Log.write("Update: ${e.message}")
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
        val busy = update.ui is UpdateUi.Checking || update.ui is UpdateUi.Downloading || update.ui is UpdateUi.Installing
        OutlinedButton(onClick = { update.check() }, enabled = !busy) { Text(t("update_check")) }
        Hint(if (last > 0) t("update_last_check", java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(last))) else t("update_never"))
        when (val st = update.ui) {
            UpdateUi.Idle -> Unit
            UpdateUi.Checking -> Text(t("update_checking"))
            is UpdateUi.UpToDate -> Text(t("update_up_to_date", st.version), color = Palette.WineSoft)
            is UpdateUi.Available -> {
                Text(t("update_available", st.release.version), color = Palette.WineSoft)
                Button(onClick = { update.download(st.release) }) { Text(t("update_download")) }
            }
            is UpdateUi.Downloaded -> {
                Text(t("update_ready", st.release.version), color = Palette.WineSoft)
                Button(onClick = { update.install(st.release, st.pkg) }) { Text(t("update_install_now")) }
            }
            UpdateUi.Installing -> Text(t("update_installing_win"))
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

private fun chooseFile(title: String, save: Boolean, name: String = "", startIn: java.io.File? = null): java.io.File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, if (save) java.awt.FileDialog.SAVE else java.awt.FileDialog.LOAD)
    startIn?.takeIf { it.isDirectory }?.let { dialog.directory = it.absolutePath }
    if (save) dialog.file = name
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return java.io.File(dialog.directory, file)
}

/** The folder for backups inside the chosen storage place (the dialogs open there); null at the standard place. */
private fun backupFolder(state: AppState): java.io.File? =
    state.store.config.storageDir?.let { java.io.File(it, "Backups") }?.also { runCatching { it.mkdirs() } }

/** Where downloads, recordings and backups are kept: the standard place until a folder (also on an external drive) is chosen. */
@Composable
private fun StorageGroup(state: AppState) {
    var chosen by remember { mutableStateOf(state.store.config.storageDir) }
    val available = chosen?.let { java.io.File(it).isDirectory } ?: true
    val shown = chosen ?: DownloadManager.mediaDir("").parentFile?.absolutePath.orEmpty()
    Text(shown, style = MaterialTheme.typography.bodyMedium)
    if (chosen == null) Hint(t("storage_default")) else if (!available) Text(t("storage_missing"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val chooser = javax.swing.JFileChooser(chosen?.let { java.io.File(it) }).apply {
                fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                dialogTitle = Strings.get("storage_choose")
            }
            if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
                val dir = chooser.selectedFile
                chosen = dir.absolutePath
                state.store.update { c -> c.copy(storageDir = dir.absolutePath) }
            }
        }) { Text(t("storage_choose")) }
        OutlinedButton(onClick = { chosen = null; state.store.update { c -> c.copy(storageDir = null) } }, enabled = chosen != null) { Text(t("storage_reset")) }
    }
    Hint(t("storage_hint"))
}

/** Backup of accounts, favorites and settings, the same file format as in the Android app. */
@Composable
private fun BackupGroup(state: AppState) {
    var exportDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf<String?>(null) }
    var importPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val codec = com.kutlusoy.proiptv.data.BackupCodec

    fun restore(text: String, password: String?) {
        message = try {
            val (accounts, favorites) = state.restoreBackup(text, password)
            importText = null
            importPassword = ""
            Strings.get("backup_restored", accounts, favorites)
        } catch (e: com.kutlusoy.proiptv.data.WrongPassword) {
            Strings.get("backup_wrong_password")
        } catch (e: com.kutlusoy.proiptv.data.InvalidBackup) {
            importText = null
            Strings.get("backup_invalid")
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { exportDialog = true }) { Text(t("backup_create")) }
        OutlinedButton(onClick = {
            val file = chooseFile(Strings.get("backup_restore"), save = false, startIn = backupFolder(state)) ?: return@OutlinedButton
            val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            if (text == null) message = Strings.get("backup_invalid")
            else try {
                if (codec.isEncrypted(text)) importText = text else restore(text, null)
            } catch (e: com.kutlusoy.proiptv.data.InvalidBackup) {
                message = Strings.get("backup_invalid")
            }
        }) { Text(t("backup_restore")) }
    }
    Hint(t("backup_hint"))
    message?.let { Text(it, color = Palette.WineSoft) }

    if (exportDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { exportDialog = false; exportPassword = "" },
            title = { Text(t("backup_create")) },
            text = {
                TextInput(
                    exportPassword, { exportPassword = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("backup_password")) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    exportDialog = false
                    val name = "proiptv-backup-" + java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date()) + ".json"
                    val file = chooseFile(Strings.get("backup_create"), save = true, name = name, startIn = backupFolder(state))
                    if (file != null) {
                        message = runCatching { file.writeText(state.createBackup(exportPassword.ifEmpty { null }), Charsets.UTF_8) }
                            .fold({ Strings.get("backup_done") }, { it.message ?: "error" })
                    }
                    exportPassword = ""
                }) { Text(t("action_save")) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { exportDialog = false; exportPassword = "" }) { Text(t("action_cancel")) } },
        )
    }
    importText?.let { text ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { importText = null; importPassword = "" },
            title = { Text(t("backup_restore")) },
            text = {
                TextInput(
                    importPassword, { importPassword = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("account_password")) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { restore(text, importPassword) }) { Text(t("backup_restore")) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { importText = null; importPassword = "" }) { Text(t("action_cancel")) } },
        )
    }
}

private fun loadSupporters(): List<com.kutlusoy.proiptv.parse.SupporterSection> =
    runCatching {
        SettingsUi::class.java.getResourceAsStream("/supporters.md")?.use { com.kutlusoy.proiptv.parse.SupportersMd.parse(String(it.readBytes(), Charsets.UTF_8)) }
    }.getOrNull().orEmpty()

private object SettingsUi
