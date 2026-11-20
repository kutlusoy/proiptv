package com.kutlusoy.proiptv.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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

    // some devices (a TV) have no app to choose a file: then the backup is written into the storage folder and chosen from a list
    // only a TV can be without the file chooser: a phone always uses it (the check could be wrong there)
    val isTv = LocalTv.current
    val noFileApp = remember {
        if (!isTv) return@remember false
        val create = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("application/json")
        val open = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*")
        ctx.packageManager.resolveActivity(create, 0) == null || ctx.packageManager.resolveActivity(open, 0) == null
    }
    var latest by remember { mutableStateOf<java.io.File?>(null) }
    var transfer by remember { mutableStateOf(false) }
    // Android 10 and older: the storage permission lets the backup go to a folder of the drive that stays when the app is uninstalled
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { afterPermission?.invoke(); afterPermission = null }
    fun withStorageAccess(action: () -> Unit) {
        val needed = android.os.Build.VERSION.SDK_INT <= 29 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needed) {
            afterPermission = action
            permission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            action()
        }
    }

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
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { exportDialog = true }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.backup_create)) }
            OutlinedButton(onClick = { if (noFileApp) withStorageAccess { latest = app.storage.newestBackup().also { if (it == null) toast(ctx.getString(R.string.backup_none_found, app.storage.openFolderPath() ?: app.storage.backups().path)) } } else opener.launch(arrayOf("*/*")) }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.backup_restore)) }
            OutlinedButton(onClick = { transfer = true }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.backup_transfer)) }
        }
        Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
    }

    latest?.let { file ->
        AlertDialog(
            onDismissRequest = { latest = null },
            title = { Text(stringResource(R.string.backup_restore)) },
            text = {
                Column {
                    Text(stringResource(R.string.backup_latest, file.name))
                    Text(
                        file.parent.orEmpty() + "\n" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    latest = null
                    scope.launch {
                        val text = withContext(Dispatchers.IO) { runCatching { file.readText(Charsets.UTF_8) }.getOrNull() }
                        if (text == null) {
                            toast(ctx.getString(R.string.backup_invalid))
                        } else {
                            try {
                                if (BackupCodec.isEncrypted(text)) importText = text else restore(text, null)
                            } catch (e: InvalidBackup) {
                                toast(ctx.getString(R.string.backup_invalid))
                            }
                        }
                    }
                }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.backup_restore)) }
            },
            dismissButton = { TextButton(onClick = { latest = null }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (transfer) BackupTransferDialog { transfer = false }

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
                    val name = "proiptv-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".json"
                    if (noFileApp) {
                        withStorageAccess {
                            scope.launch {
                                val text = app.backup.export(exportPassword.ifEmpty { null })
                                exportPassword = ""
                                val open = withContext(Dispatchers.IO) { app.storage.writeOpenBackup(name, text) }
                                if (open != null) {
                                    toast(ctx.getString(R.string.backup_saved_file, open.path))
                                } else {
                                    // this device does not allow a folder outside the app: the file goes into the folder of the app, which an uninstall deletes
                                    val file = withContext(Dispatchers.IO) { java.io.File(app.storage.backups().apply { mkdirs() }, name).also { it.writeText(text, Charsets.UTF_8) } }
                                    toast(ctx.getString(R.string.backup_saved_file, file.path) + "\n" + ctx.getString(R.string.backup_not_public))
                                }
                            }
                        }
                    } else {
                        saver.launch(name)
                    }
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
