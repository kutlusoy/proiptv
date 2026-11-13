package com.kutlusoy.proiptv.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.kutlusoy.proiptv.BuildConfig
import com.kutlusoy.proiptv.R

private val languages = listOf(
    "en" to "English", "de" to "Deutsch", "tr" to "Türkçe", "fr" to "Français", "it" to "Italiano", "es" to "Español",
    "pt" to "Português", "nl" to "Nederlands", "pl" to "Polski", "ru" to "Русский", "ar" to "العربية",
)

@Composable
fun SettingsScreen(vm: MainViewModel) {
    var current by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')) }
    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp)) }
        item { Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium) }
        item { LanguageRow(stringResource(R.string.option_auto), current.isEmpty()) { current = ""; apply("") } }
        items(languages) { (tag, name) ->
            LanguageRow(name, current == tag) { current = tag; apply(tag) }
        }
        item {
            OutlinedButton(onClick = { vm.refreshAll() }, modifier = Modifier.padding(top = 16.dp).focusHighlight()) { Text(stringResource(R.string.settings_refresh)) }
        }
        item { Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
        item { Text("ProIPTV ${stringResource(R.string.main_version, BuildConfig.VERSION_NAME)}") }
        item { Text(stringResource(R.string.settings_license), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)) }
        item { Text("github.com/kutlusoy/proiptv", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)) }
    }
}

@Composable
private fun LanguageRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().focusHighlight().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Text(name)
    }
}

private fun apply(tag: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}
