package com.kutlusoy.proiptv.ui

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.AccountType
import com.kutlusoy.proiptv.sync.Phase
import com.kutlusoy.proiptv.sync.SyncState
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(vm: MainViewModel) {
    val accounts by vm.accounts.collectAsState()
    val sync by vm.syncState.collectAsState()
    var editing by remember { mutableStateOf<Account?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Account?>(null) }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nav_accounts), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Button(onClick = { adding = true }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.accounts_add)) }
            }
        }
        if (accounts.isEmpty()) {
            item { Text(stringResource(R.string.accounts_empty), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)) }
        }
        items(accounts, key = { it.id }) { account ->
            AccountCard(account, sync[account.id], vm, onEdit = { editing = account }, onDelete = { deleting = account })
        }
    }

    if (adding) {
        AccountDialog(vm, null, onDismiss = { adding = false }) { account, password ->
            vm.run { vm.repo.saveAccount(account, password) }
            adding = false
        }
    }
    editing?.let { account ->
        AccountDialog(vm, account, onDismiss = { editing = null }) { updated, password ->
            vm.run { vm.repo.saveAccount(updated, password) }
            editing = null
        }
    }
    deleting?.let { account ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.account_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.run { vm.repo.deleteAccount(account.id) }; deleting = null }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun AccountCard(account: Account, state: SyncState?, vm: MainViewModel, onEdit: () -> Unit, onDelete: () -> Unit) {
    val counts by vm.counts(account.id).collectAsState(initial = Triple(0, 0, 0))
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(account.name, style = MaterialTheme.typography.titleLarge)
                    Text(if (account.type == AccountType.XTREAM) "Xtream Codes" else stringResource(R.string.account_type_m3u), color = dim, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = account.enabled, onCheckedChange = { on -> vm.run { vm.repo.setEnabled(account, on) } }, modifier = Modifier.focusHighlight())
            }
            account.status?.let { Text(stringResource(R.string.account_status, it), color = dim) }
            account.expiry?.let { expiry ->
                val days = ((expiry * 1000 - System.currentTimeMillis()) / 86_400_000L).toInt()
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expiry * 1000))
                if (days >= 0) {
                    Text(stringResource(R.string.account_expires, date, days), color = if (days <= 7) MaterialTheme.colorScheme.error else dim)
                } else {
                    Text(stringResource(R.string.account_expired, date), color = MaterialTheme.colorScheme.error)
                }
            }
            account.maxConnections?.let { max ->
                Text(stringResource(R.string.account_connections, account.activeConnections ?: 0, max), color = dim)
            }
            Text(stringResource(R.string.account_counts, counts.first, counts.second, counts.third), color = dim)
            if (state != null) {
                Text(syncText(state), color = MaterialTheme.colorScheme.primary)
            } else if (account.lastSync > 0) {
                val ago = DateUtils.getRelativeTimeSpanString(account.lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                Text(stringResource(R.string.account_last_sync, ago), color = dim)
            } else {
                Text(stringResource(R.string.account_never_synced), color = dim)
            }
            account.lastError?.let { Text(stringResource(R.string.account_error, it), color = Color(0xFFFF8A80)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { vm.repo.sync.sync(account.id) }, enabled = state == null, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.account_sync)) }
                OutlinedButton(onClick = onEdit, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_edit)) }
                OutlinedButton(onClick = onDelete, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
private fun syncText(state: SyncState): String = when (state.phase) {
    Phase.CONNECTING -> stringResource(R.string.sync_connecting)
    Phase.LIVE -> stringResource(R.string.sync_live, state.count)
    Phase.MOVIES -> stringResource(R.string.sync_movies, state.count)
    Phase.SERIES -> stringResource(R.string.sync_series, state.count)
    Phase.GUIDE -> stringResource(R.string.sync_guide, state.count)
}

@Composable
private fun AccountDialog(vm: MainViewModel, initial: Account?, onDismiss: () -> Unit, onSave: (Account, String) -> Unit) {
    var type by remember { mutableStateOf(initial?.type ?: AccountType.XTREAM) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var user by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf(initial?.epgUrl ?: "") }
    var agent by remember { mutableStateOf(initial?.userAgent ?: "") }
    var format by remember { mutableStateOf(initial?.liveFormat ?: "auto") }
    var vod by remember { mutableStateOf(initial?.useVod ?: true) }
    var series by remember { mutableStateOf(initial?.useSeries ?: true) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { url = vm.importPlaylist(uri) }
    }
    val xtream = type == AccountType.XTREAM
    val valid = name.isNotBlank() && url.isNotBlank() && (!xtream || (user.isNotBlank() || url.contains("username=")))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.accounts_add else R.string.account_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = xtream, onClick = { type = AccountType.XTREAM }, label = { Text("Xtream Codes") })
                        FilterChip(selected = !xtream, onClick = { type = AccountType.M3U }, label = { Text(stringResource(R.string.account_type_m3u)) })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.account_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    url, { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(if (xtream) R.string.account_server else R.string.account_playlist_url)) },
                )
                if (!xtream) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.account_choose_file)) }
                } else {
                    OutlinedTextField(user, { user = it }, label = { Text(stringResource(R.string.account_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        password, { password = it }, label = { Text(stringResource(R.string.account_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                        supportingText = if (initial != null) ({ Text(stringResource(R.string.account_password_keep)) }) else null,
                    )
                    Text(stringResource(R.string.account_live_format), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = format == "auto", onClick = { format = "auto" }, label = { Text(stringResource(R.string.option_auto)) })
                        FilterChip(selected = format == "ts", onClick = { format = "ts" }, label = { Text("TS") })
                        FilterChip(selected = format == "m3u8", onClick = { format = "m3u8" }, label = { Text("HLS") })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.account_use_vod), Modifier.weight(1f))
                        Switch(vod, { vod = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.account_use_series), Modifier.weight(1f))
                        Switch(series, { series = it })
                    }
                }
                OutlinedTextField(epg, { epg = it }, label = { Text(stringResource(R.string.account_epg_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(agent, { agent = it }, label = { Text(stringResource(R.string.account_user_agent)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val base = initial ?: Account(name = name, type = type, url = url)
                    onSave(
                        base.copy(
                            name = name.trim(), type = type, url = url.trim(), username = user.trim(),
                            epgUrl = epg.trim().ifBlank { null }, userAgent = agent.trim().ifBlank { null },
                            liveFormat = format, useVod = vod, useSeries = series,
                        ),
                        password,
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
