package com.kutlusoy.proiptv.ui

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.kutlusoy.proiptv.ProIptvApp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.StorageVolume

/**
 * Where downloads, recordings and backups are kept. The drives that are connected are listed (internal storage, memory card, USB drive); no file chooser app is
 * needed, so it works on a TV too. The standard place stays until another one is chosen, "Restore default" goes back to it.
 */
@Composable
fun StorageSettings() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as ProIptvApp
    var version by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(false) }
    val chosen = remember(version) { app.storage.chosenRoot() }
    val missing = remember(version) { app.storage.chosenMissing() }
    val volumes = remember(version, dialog) { app.storage.volumes() }
    val internal = stringResource(R.string.storage_internal)

    fun describe(v: StorageVolume) = (if (v.primary) internal else v.label) + " - " + ctx.getString(R.string.storage_free, Formatter.formatFileSize(ctx, v.freeBytes))

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val current = chosen?.let { dir -> volumes.firstOrNull { dir.path.startsWith(it.dir.path) }?.let { describe(it) + "\n" + dir.path } }
        Text(current ?: stringResource(R.string.storage_default))
        if (missing) Text(stringResource(R.string.storage_missing), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { dialog = true }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.storage_choose)) }
            OutlinedButton(onClick = { app.prefs.storageRoot = null; version++ }, enabled = app.prefs.storageRoot != null, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.storage_reset)) }
        }
        Text(stringResource(R.string.storage_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Text(stringResource(R.string.storage_uninstall_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }

    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text(stringResource(R.string.storage_choose)) },
            text = {
                Column {
                    volumes.forEach { v ->
                        // the internal storage is the standard place
                        val selected = if (v.primary) chosen == null else chosen?.path?.startsWith(v.dir.path) == true
                        Row(
                            Modifier.fillMaxWidth().focusHighlight().clickable {
                                app.prefs.storageRoot = if (v.primary) null else v.dir.path
                                dialog = false
                                version++
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Text(describe(v), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = false }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
