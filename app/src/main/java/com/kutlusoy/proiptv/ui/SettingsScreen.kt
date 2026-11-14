package com.kutlusoy.proiptv.ui

import android.text.format.Formatter
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.kutlusoy.proiptv.BuildConfig
import com.kutlusoy.proiptv.ProIptvApp
import com.kutlusoy.proiptv.R

private val languages = listOf(
    "en" to "English", "de" to "Deutsch", "tr" to "Türkçe", "fr" to "Français", "it" to "Italiano", "es" to "Español",
    "pt" to "Português", "nl" to "Nederlands", "pl" to "Polski", "ru" to "Русский", "ar" to "العربية",
)

private val bufferSizesMb = listOf(256, 512, 1024, 2048, 4096)

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
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
        content()
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val prefs = (ctx.applicationContext as ProIptvApp).prefs
    var current by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')) }
    var timeshift by remember { mutableStateOf(prefs.timeshift) }
    var bufferMb by remember { mutableIntStateOf(prefs.bufferMb) }
    val auto = stringResource(R.string.option_auto)

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)) {
        item {
            Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)

            Group(stringResource(R.string.settings_group_general)) {
                Labeled(stringResource(R.string.settings_language)) {
                    val options = listOf("" to auto) + languages
                    DropdownField(
                        selected = options.firstOrNull { it.first == current }?.second ?: auto,
                        options = options,
                        onSelect = {
                            current = it
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it))
                        },
                    )
                }
                Labeled(stringResource(R.string.settings_interface)) {
                    val mode by vm.uiMode.collectAsState()
                    val labels = listOf(UI_AUTO to auto, UI_TV to stringResource(R.string.interface_tv), UI_TOUCH to stringResource(R.string.interface_touch))
                    DropdownField(
                        selected = labels.first { it.first == mode }.second,
                        options = labels,
                        onSelect = { vm.setUiMode(it) },
                    )
                    Hint(stringResource(R.string.settings_interface_hint))
                }
            }

            Group(stringResource(R.string.settings_group_playback)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_timeshift), Modifier.weight(1f))
                    Switch(checked = timeshift, onCheckedChange = { timeshift = it; prefs.timeshift = it }, modifier = Modifier.focusHighlight())
                }
                if (timeshift) {
                    Labeled(stringResource(R.string.settings_buffer)) {
                        DropdownField(
                            selected = Formatter.formatShortFileSize(ctx, bufferMb * 1024L * 1024L),
                            options = bufferSizesMb.map { it to Formatter.formatShortFileSize(ctx, it * 1024L * 1024L) },
                            onSelect = { bufferMb = it; prefs.bufferMb = it },
                        )
                        Hint(stringResource(R.string.settings_buffer_hint))
                    }
                }
            }

            Group(stringResource(R.string.settings_group_data)) {
                Labeled(stringResource(R.string.nav_accounts)) {
                    OutlinedButton(onClick = { vm.refreshAll() }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.settings_refresh)) }
                    Hint(stringResource(R.string.settings_guide_info))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Labeled(stringResource(R.string.settings_backup)) { BackupSettings() }
            }

            Group(stringResource(R.string.settings_about)) {
                Text("ProIPTV " + stringResource(R.string.main_version, BuildConfig.VERSION_NAME))
                Hint(stringResource(R.string.settings_license))
                Hint("github.com/kutlusoy/proiptv")
                Text("Ali Kutlusoy")
                Text(
                    "kutlusoy.dev@gmail.com", color = Palette.WineSoft,
                    modifier = Modifier.focusHighlight().clickable {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:kutlusoy.dev@gmail.com"))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            }

            UpdateGroup(vm)
        }
    }
}

@Composable
private fun UpdateGroup(vm: MainViewModel) {
    val ctx = LocalContext.current
    val prefs = (ctx.applicationContext as ProIptvApp).prefs
    val state by vm.updateUi.collectAsState()
    var auto by remember { mutableStateOf(prefs.autoUpdate) }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    val lastCheck = prefs.lastUpdateCheck
    val current = BuildConfig.VERSION_NAME + if (BuildConfig.DEBUG && BuildConfig.TEST_BUILD > 0) " (test ${BuildConfig.TEST_BUILD})" else ""

    Group(stringResource(R.string.update_title)) {
        Text(stringResource(R.string.update_current, current))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_auto))
                Hint(stringResource(R.string.update_auto_hint))
            }
            Switch(
                checked = auto,
                onCheckedChange = { on ->
                    auto = on
                    prefs.autoUpdate = on
                    com.kutlusoy.proiptv.update.UpdateWorker.schedule(ctx, on)
                    if (on && android.os.Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                },
                modifier = Modifier.padding(start = 12.dp).focusHighlight(),
            )
        }
        val busy = state is UpdateUi.Checking || state is UpdateUi.Working
        OutlinedButton(onClick = { vm.checkUpdate() }, enabled = !busy, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.update_check)) }
        Hint(
            if (lastCheck > 0) stringResource(R.string.update_last_check, android.text.format.DateUtils.getRelativeTimeSpanString(lastCheck, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString())
            else stringResource(R.string.update_never),
        )
        when (val st = state) {
            UpdateUi.Idle -> Unit
            UpdateUi.Checking -> Text(stringResource(R.string.update_checking))
            is UpdateUi.UpToDate -> Text(stringResource(R.string.update_up_to_date, st.version), color = Palette.WineSoft)
            is UpdateUi.Available -> {
                Text(stringResource(R.string.update_available, st.release.version), color = Palette.WineSoft, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                androidx.compose.material3.Button(onClick = { vm.installUpdate(st.release) }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.update_install)) }
            }
            is UpdateUi.Working -> {
                Text(
                    when (st.phase) {
                        com.kutlusoy.proiptv.update.UpdateManager.Phase.VERIFYING -> stringResource(R.string.update_verifying)
                        com.kutlusoy.proiptv.update.UpdateManager.Phase.DOWNLOADING -> stringResource(R.string.update_downloading, st.percent)
                        com.kutlusoy.proiptv.update.UpdateManager.Phase.INSTALLING -> stringResource(R.string.update_installing)
                    },
                )
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { st.percent / 100f }.takeIf { st.phase == com.kutlusoy.proiptv.update.UpdateManager.Phase.DOWNLOADING } ?: { 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is UpdateUi.Error -> Text(updateErrorText(st), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun updateErrorText(e: UpdateUi.Error): String = when (e.code) {
    com.kutlusoy.proiptv.update.UpdateException.Code.NETWORK -> stringResource(R.string.update_err_network)
    com.kutlusoy.proiptv.update.UpdateException.Code.NOT_FOUND -> stringResource(R.string.update_err_not_found)
    com.kutlusoy.proiptv.update.UpdateException.Code.NO_APK -> stringResource(R.string.update_err_no_apk)
    com.kutlusoy.proiptv.update.UpdateException.Code.NO_CHECKSUM -> stringResource(R.string.update_err_no_checksum)
    com.kutlusoy.proiptv.update.UpdateException.Code.CHECKSUM_MISMATCH -> stringResource(R.string.update_err_checksum)
    com.kutlusoy.proiptv.update.UpdateException.Code.INSECURE_URL -> stringResource(R.string.update_err_insecure)
    com.kutlusoy.proiptv.update.UpdateException.Code.TOO_LARGE -> stringResource(R.string.update_err_too_large)
    com.kutlusoy.proiptv.update.UpdateException.Code.STORAGE -> stringResource(R.string.update_err_storage)
    com.kutlusoy.proiptv.update.UpdateException.Code.INSTALL_FAILED -> stringResource(R.string.update_err_install, e.detail.orEmpty())
}
