package com.kutlusoy.proiptv.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private fun relative(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))

@Composable
fun AccountsScreen(state: AppState) {
    var editing by remember { mutableStateOf<AccountDto?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AccountDto?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("nav_accounts"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { state.reload() }, enabled = !state.loading) { Text(t("settings_refresh")) }
                Button(onClick = { adding = true }) { Text(t("accounts_add")) }
            }
        }
        if (state.accounts.isEmpty()) item { Text(t("accounts_empty"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)) }
        items(state.accounts, key = { it.id }) { a ->
            AccountCard(state, a, expanded = openId == a.id, onToggle = { openId = if (openId == a.id) null else a.id }, onEdit = { editing = a }, onDelete = { deleting = a })
        }
    }

    if (adding) AccountDialog(state, null) { adding = false }
    editing?.let { AccountDialog(state, it) { editing = null } }
    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(t("account_delete_confirm", a.name)) },
            confirmButton = { TextButton(onClick = { state.deleteAccount(a.id); deleting = null }) { Text(t("action_delete")) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(t("action_cancel")) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountCard(state: AppState, a: AccountDto, expanded: Boolean, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val status = state.data.status[a.id]
    val info = status?.info
    val error = state.data.errors[a.id]
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val days = info?.expiry?.let { ((it * 1000 - System.currentTimeMillis()) / 86_400_000L).toInt() }
    val date = info?.expiry?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it * 1000)) }
    val summary = when {
        state.loading -> t("series_loading")
        error != null -> t("account_error", error)
        days != null && date != null && days >= 0 -> t("account_until", date)
        days != null && date != null -> t("account_expired", date)
        else -> if (a.type == "xtream") "Xtream Codes" else t("account_type_m3u")
    }
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = if (error != null || (days != null && days <= 7)) MaterialTheme.colorScheme.error else dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = a.enabled, onCheckedChange = { state.setEnabled(a.id, it) }, modifier = Modifier.padding(horizontal = 8.dp))
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = dim)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (a.type == "xtream") "Xtream Codes" else t("account_type_m3u"), color = dim, style = MaterialTheme.typography.bodySmall)
                    info?.status?.let { Text(t("account_status", it), color = dim) }
                    if (days != null && date != null) {
                        if (days >= 0) Text(t("account_expires", date, days), color = if (days <= 7) MaterialTheme.colorScheme.error else dim)
                        else Text(t("account_expired", date), color = MaterialTheme.colorScheme.error)
                    }
                    info?.maxConnections?.let { max -> Text(t("account_connections", info.activeConnections ?: 0, max), color = dim) }
                    if (status != null) {
                        Text(t("account_counts", status.channels, status.movies, status.series), color = dim)
                        Text(t("account_last_sync", relative(status.loadedAt)), color = dim)
                    } else {
                        Text(t("account_never_synced"), color = dim)
                    }
                    val guideAt = state.guide.updated[a.id]
                    if (guideAt != null) Text(t("account_guide_updated", relative(guideAt)), color = dim)
                    else if ((status?.channels ?: 0) > 0) Text(t("account_guide_never"), color = dim)
                    state.guide.errors[a.id]?.let { Text(t("account_error", it), color = MaterialTheme.colorScheme.error) }
                    error?.let { Text(t("account_error", it), color = MaterialTheme.colorScheme.error) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { state.reload() }, enabled = !state.loading) { Text(t("account_sync")) }
                        OutlinedButton(onClick = { state.refreshGuide() }, enabled = !state.guideLoading) { Text(t("account_sync_guide")) }
                        OutlinedButton(onClick = onEdit) { Text(t("action_edit")) }
                        OutlinedButton(onClick = onDelete) { Text(t("action_delete")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDialog(state: AppState, existing: AccountDto?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(existing?.type ?: "xtream") }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    var user by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf(existing?.epgUrl.orEmpty()) }
    var agent by remember { mutableStateOf(existing?.userAgent.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(t(if (existing == null) "accounts_add" else "account_edit_title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                DropdownField(if (type == "xtream") "Xtream Codes" else t("account_type_m3u"), listOf("xtream" to "Xtream Codes", "m3u" to t("account_type_m3u")), { type = it })
                OutlinedTextField(name, { name = it }, label = { Text(t("account_name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text(t(if (type == "xtream") "account_server" else "account_playlist_url")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (type == "xtream") {
                    OutlinedTextField(user, { user = it }, label = { Text(t("account_username")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        password, { password = it }, label = { Text(t("account_password")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        supportingText = { if (existing != null) Text(t("account_password_keep")) },
                    )
                }
                OutlinedTextField(epg, { epg = it }, label = { Text(t("account_epg_url")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(agent, { agent = it }, label = { Text(t("account_user_agent")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && name.isNotBlank() && url.isNotBlank(),
                onClick = {
                    val server = if (type == "xtream") Library.normalizeServer(url) else url.trim()
                    val stored = if (password.isNotEmpty()) Secrets.protect(password) else existing?.password.orEmpty()
                    val draft = AccountDto(
                        id = existing?.id ?: state.newAccountId(), name = name.trim(), type = type, url = server, username = user.trim(),
                        password = stored, userAgent = agent.trim().ifBlank { null }, enabled = existing?.enabled ?: true, epgUrl = epg.trim().ifBlank { null },
                    )
                    busy = true
                    scope.launch {
                        val problem = state.library.test(draft)
                        busy = false
                        if (problem == null) {
                            state.saveAccount(draft)
                            onClose()
                        } else {
                            message = problem
                        }
                    }
                },
            ) { Text(t("action_save")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(t("action_cancel")) } },
    )
}
