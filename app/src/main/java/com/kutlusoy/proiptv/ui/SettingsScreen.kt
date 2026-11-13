package com.kutlusoy.proiptv.ui

import android.text.format.Formatter
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.WineSoft)
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

    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        item {
            Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)
            Section(stringResource(R.string.settings_language)) {
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
            Section(stringResource(R.string.settings_interface)) {
                val mode by vm.uiMode.collectAsState()
                val labels = listOf(UI_AUTO to auto, UI_TV to stringResource(R.string.interface_tv), UI_TOUCH to stringResource(R.string.interface_touch))
                DropdownField(
                    selected = labels.first { it.first == mode }.second,
                    options = labels,
                    onSelect = { vm.setUiMode(it) },
                )
                Text(stringResource(R.string.settings_interface_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
            }
            Section(stringResource(R.string.settings_timeshift)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_timeshift), Modifier.weight(1f))
                    Switch(checked = timeshift, onCheckedChange = { timeshift = it; prefs.timeshift = it }, modifier = Modifier.focusHighlight())
                }
                if (timeshift) {
                    DropdownField(
                        label = stringResource(R.string.settings_buffer),
                        selected = Formatter.formatShortFileSize(ctx, bufferMb * 1024L * 1024L),
                        options = bufferSizesMb.map { it to Formatter.formatShortFileSize(ctx, it * 1024L * 1024L) },
                        onSelect = { bufferMb = it; prefs.bufferMb = it },
                    )
                    Text(stringResource(R.string.settings_buffer_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
                }
            }
            Section(stringResource(R.string.nav_accounts)) {
                OutlinedButton(onClick = { vm.refreshAll() }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.settings_refresh)) }
                Text(stringResource(R.string.settings_guide_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
            }
            Section(stringResource(R.string.settings_backup)) { BackupSettings() }
            HorizontalDivider(Modifier.padding(top = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Section(stringResource(R.string.settings_about)) {
                Text("ProIPTV " + stringResource(R.string.main_version, BuildConfig.VERSION_NAME))
                Text(stringResource(R.string.settings_license), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
                Text("github.com/kutlusoy/proiptv", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f))
            }
        }
    }
}
