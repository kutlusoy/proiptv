package com.kutlusoy.proiptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.ProIptvApp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.BackupCodec
import com.kutlusoy.proiptv.data.InvalidBackup
import com.kutlusoy.proiptv.data.WrongPassword
import com.kutlusoy.proiptv.remote.BackupRouter
import com.kutlusoy.proiptv.remote.RemoteSetupServer
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * A QR code that opens a page on the phone or PC: there the backup is downloaded (a file on that device stays when the app is uninstalled) or a backup
 * from that device is restored. The server runs only while this dialog is open.
 */
@Composable
fun BackupTransferDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as ProIptvApp
    var address by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<Int?>(null) }
    var restored by remember { mutableStateOf<com.kutlusoy.proiptv.data.RestoreResult?>(null) }

    DisposableEffect(Unit) {
        val ip = localIpv4()
        var server: RemoteSetupServer? = null
        if (ip == null) {
            failure = R.string.remote_no_network
        } else {
            val token = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            try {
                val router = BackupRouter(
                    token = token,
                    page = { app.assets.open("remote_backup.html").use { it.readBytes() } },
                    export = { password -> app.backup.export(password) },
                    restore = { text, password ->
                        try {
                            if (password == null && BackupCodec.isEncrypted(text)) {
                                "password_required"
                            } else {
                                val result = app.backup.restore(text, password)
                                withContext(Dispatchers.Main) { restored = result }
                                null
                            }
                        } catch (e: WrongPassword) {
                            "wrong_password"
                        } catch (e: InvalidBackup) {
                            "invalid"
                        }
                    },
                    fileName = { "proiptv-backup-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".json" },
                )
                val started = RemoteSetupServer(router, maxBody = 8 * 1024 * 1024)
                val port = started.start()
                server = started
                address = "http://$ip:$port/?t=$token"
            } catch (e: Exception) {
                failure = R.string.remote_failed
            }
        }
        onDispose { server?.stop() }
    }
    LaunchedEffect(Unit) {
        delay(10 * 60 * 1000L)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_transfer_title)) },
        text = {
            Column(Modifier.padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val url = address
                val done = restored
                when {
                    done != null -> Text(stringResource(R.string.backup_restored, done.accounts, done.favorites), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                    url != null -> {
                        QrImage(url, Modifier.size(240.dp))
                        Text(url.substringBefore("/?"), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.backup_transfer_hint), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                    failure != null -> Text(stringResource(failure!!), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    else -> Text(stringResource(R.string.series_loading))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_close)) } },
    )
}
