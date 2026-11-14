package com.kutlusoy.proiptv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.AccountType
import com.kutlusoy.proiptv.net.Http
import com.kutlusoy.proiptv.net.XtreamApi
import com.kutlusoy.proiptv.net.XtreamUrl
import com.kutlusoy.proiptv.remote.RemoteAccount
import com.kutlusoy.proiptv.remote.RemoteRouter
import com.kutlusoy.proiptv.remote.RemoteSetupServer
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The address of this device in the local network, preferring Wi-Fi and Ethernet. */
fun localIpv4(): String? {
    val addresses = runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())
        .filter { it.isUp && !it.isLoopback }
        .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
        .flatMap { it.inetAddresses.toList() }
    return addresses.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
}

/** Shows a QR code that opens the setup page on the phone. The server runs only while this dialog is open. */
@Composable
fun RemoteSetupDialog(vm: MainViewModel, onAdded: (String) -> Unit, onDismiss: () -> Unit) {
    var address by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<Int?>(null) }
    var added by remember { mutableStateOf<String?>(null) }
    val assets = androidx.compose.ui.platform.LocalContext.current.applicationContext.assets

    DisposableEffect(Unit) {
        val ip = localIpv4()
        var server: RemoteSetupServer? = null
        if (ip == null) {
            failure = R.string.remote_no_network
        } else {
            val token = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            try {
                val router = RemoteRouter(
                    token = token,
                    page = { assets.open("remote_setup.html").use { it.readBytes() } },
                    onAccount = { draft ->
                        val problem = saveRemote(vm, draft)
                        if (problem == null) withContext(Dispatchers.Main) { added = draft.name }
                        problem
                    },
                )
                val started = RemoteSetupServer(router)
                val port = started.start()
                server = started
                address = "http://$ip:$port/?t=$token"
            } catch (e: Exception) {
                failure = R.string.remote_failed
            }
        }
        onDispose { server?.stop() }
    }
    LaunchedEffect(added) {
        added?.let {
            delay(2500)
            onAdded(it)
        }
    }
    LaunchedEffect(Unit) {
        delay(10 * 60 * 1000L)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_title)) },
        text = {
            Column(Modifier.padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val url = address
                when {
                    added != null -> Text(stringResource(R.string.remote_added, added!!), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                    url != null -> {
                        QrImage(url, Modifier.size(240.dp))
                        Text(url.substringBefore("/?"), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.remote_hint), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                    failure != null -> Text(stringResource(failure!!), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    else -> Text(stringResource(R.string.series_loading))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_close)) } },
    )
}

/** Logs in once for Xtream accounts, so a wrong password is shown on the phone right away. Returns an error text or null. */
private suspend fun saveRemote(vm: MainViewModel, draft: RemoteAccount): String? {
    val xtream = draft.type == "xtream"
    val login = if (xtream) XtreamUrl.parse(draft.url) else null
    val user = draft.username.ifBlank { login?.username.orEmpty() }
    val pass = draft.password.ifBlank { login?.password.orEmpty() }
    if (xtream) {
        val problem = withContext(Dispatchers.IO) {
            try {
                XtreamApi(Http.client, login!!.server, user, pass, draft.userAgent.ifBlank { null }).authenticate()
                null
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
        }
        if (problem != null) return problem
    }
    val account = Account(
        name = draft.name.trim(), type = if (xtream) AccountType.XTREAM else AccountType.M3U, url = draft.url.trim(), username = user,
        epgUrl = draft.epgUrl.trim().ifBlank { null }, userAgent = draft.userAgent.trim().ifBlank { null },
    )
    vm.repo.saveAccount(account, pass)
    return null
}

/** Black modules on white, with a quiet zone, so cameras can read it on any theme. */
@Composable
fun QrImage(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8"),
        )
    }
    Box(modifier.background(Color.White)) {
        Canvas(Modifier.matchParentSize()) {
            val cell = size.width / matrix.width
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    if (matrix.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}
