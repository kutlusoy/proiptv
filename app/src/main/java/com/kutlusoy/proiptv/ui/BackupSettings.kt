package com.kutlusoy.proiptv.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.ProIptvApp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.BackupCodec
import com.kutlusoy.proiptv.data.InvalidBackup
import com.kutlusoy.proiptv.data.WrongPassword
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupSettings() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as ProIptvApp
    val scope = rememberCoroutineScope()
    var exportDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf<String?>(null) }
    var importPassword by remember { mutableStateOf("") }

    fun toast(text: String) = Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()

    suspend fun restore(text: String, password: String?) {
        try {
            val r = app.backup.restore(text, password)
            importText = null
            importPassword = ""
            toast(ctx.getString(R.string.backup_restored, r.accounts, r.favorites))
        } catch (e: WrongPassword) {
            toast(ctx.getString(R.string.backup_wrong_password))
        } catch (e: InvalidBackup) {
            importText = null
            toast(ctx.getString(R.string.backup_invalid))
        }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val text = app.backup.export(exportPassword.ifEmpty { null })
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
                exportPassword = ""
                toast(ctx.getString(R.string.backup_done))
            }
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) } }
                try {
                    if (BackupCodec.isEncrypted(text)) importText = text else restore(text, null)
                } catch (e: InvalidBackup) {
                    toast(ctx.getString(R.string.backup_invalid))
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportDialog = true }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.backup_create)) }
            OutlinedButton(onClick = { opener.launch(arrayOf("*/*")) }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.backup_restore)) }
        }
        Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }

    if (exportDialog) {
        AlertDialog(
            onDismissRequest = { exportDialog = false },
            title = { Text(stringResource(R.string.backup_create)) },
            text = {
                OutlinedTextField(
                    exportPassword, { exportPassword = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.backup_password)) }, visualTransformation = PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    exportDialog = false
                    saver.launch("proiptv-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".json")
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { exportDialog = false; exportPassword = "" }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    importText?.let { text ->
        AlertDialog(
            onDismissRequest = { importText = null; importPassword = "" },
            title = { Text(stringResource(R.string.backup_restore)) },
            text = {
                OutlinedTextField(
                    importPassword, { importPassword = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.account_password)) }, visualTransformation = PasswordVisualTransformation(),
                )
            },
            confirmButton = { TextButton(onClick = { scope.launch { restore(text, importPassword) } }) { Text(stringResource(R.string.backup_restore)) } },
            dismissButton = { TextButton(onClick = { importText = null; importPassword = "" }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
