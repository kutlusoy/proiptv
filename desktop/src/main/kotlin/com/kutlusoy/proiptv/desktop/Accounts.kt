package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(state: AppState) {
    var editing by remember { mutableStateOf<AccountDto?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AccountDto?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("nav_accounts"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Button(onClick = { adding = true }) { Text(t("accounts_add")) }
            }
        }
        if (state.accounts.isEmpty()) item { Text(t("accounts_empty"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)) }
        items(state.accounts, key = { it.id }) { a ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.titleMedium)
                            Text(if (a.type == "xtream") "Xtream Codes" else t("account_type_m3u"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Switch(checked = a.enabled, onCheckedChange = { state.setEnabled(a.id, it) })
                    }
                    state.data.errors[a.id]?.let { Text(t("account_error", it), color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editing = a }) { Text(t("action_edit")) }
                        OutlinedButton(onClick = { deleting = a }) { Text(t("action_delete")) }
                    }
                }
            }
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

@Composable
private fun AccountDialog(state: AppState, existing: AccountDto?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(existing?.type ?: "xtream") }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    var user by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var agent by remember { mutableStateOf(existing?.userAgent.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(t(if (existing == null) "accounts_add" else "account_edit_title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(460.dp)) {
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
                        password = stored, userAgent = agent.trim().ifBlank { null }, enabled = existing?.enabled ?: true,
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
