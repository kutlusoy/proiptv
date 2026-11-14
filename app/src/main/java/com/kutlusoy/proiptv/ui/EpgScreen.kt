package com.kutlusoy.proiptv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.parse.decodeEntities
import com.kutlusoy.proiptv.data.GuideRow
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.Source
import com.kutlusoy.proiptv.parse.TitleParser
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

private const val RANGE_NOW = 0
private const val RANGE_3H = 1
private const val RANGE_12H = 2

@Composable
fun EpgScreen(vm: MainViewModel) {
    val accounts by vm.accounts.collectAsState()
    val enabled = accounts.filter { it.enabled }
    var accountSel by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = accountSel?.takeIf { id -> enabled.any { it.id == id } }
    val ids = selected?.let { listOf(it) } ?: enabled.map { it.id }
    val effective = selected ?: enabled.singleOrNull()?.id
    var category by rememberSaveable(effective) { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var range by rememberSaveable { mutableStateOf(RANGE_NOW) }
    var nowSecs by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowSecs = System.currentTimeMillis() / 1000
        }
    }
    val categories by (effective?.let { vm.categories(it, Kind.LIVE) } ?: flowOf(emptyList())).collectAsState(initial = emptyList())
    val horizon = when (range) { RANGE_3H -> 3 * 3600L; RANGE_12H -> 12 * 3600L; else -> 0L }
    val rows by remember(ids, category, nowSecs / 60, horizon, query) {
        vm.guide(ids, category, nowSecs, nowSecs + horizon + 1, query.trim())
    }.collectAsState(initial = emptyList())
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(stringResource(R.string.nav_epg), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            val allLabel = stringResource(R.string.live_all)
            val otherLabel = stringResource(R.string.live_other)
            if (effective != null && categories.isNotEmpty()) {
                val options = buildList<Pair<String?, String>> {
                    add(null to allLabel)
                    categories.forEach { add(it.remoteId to it.name.ifBlank { otherLabel }) }
                }
                DropdownField(
                    selected = options.firstOrNull { it.first == category }?.second ?: allLabel,
                    options = options,
                    onSelect = { category = it },
                    modifier = Modifier.weight(1f),
                )
            }
            if (enabled.size > 1) {
                DropdownField<Long?>(
                    selected = enabled.firstOrNull { it.id == selected }?.name ?: stringResource(R.string.live_all_accounts),
                    options = listOf<Pair<Long?, String>>(null to stringResource(R.string.live_all_accounts)) + enabled.map { it.id to it.name },
                    onSelect = { accountSel = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.epg_search)) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) }
                },
            )
            DropdownField(
                selected = stringResource(when (range) { RANGE_3H -> R.string.epg_next_3h; RANGE_12H -> R.string.epg_next_12h; else -> R.string.epg_now }),
                options = listOf(RANGE_NOW to stringResource(R.string.epg_now), RANGE_3H to stringResource(R.string.epg_next_3h), RANGE_12H to stringResource(R.string.epg_next_12h)),
                onSelect = { range = it },
                modifier = Modifier.width(190.dp),
            )
        }
        if (rows.isEmpty()) {
            Text(stringResource(R.string.epg_empty), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(rows, key = { "${it.accountId}-${it.itemId}-${it.start}" }) { row ->
                GuideEntry(row, nowSecs, timeFormat) {
                    vm.run {
                        val index = vm.repo.db.items().livePosition(ids, category, row.accountId, row.ord)
                        vm.player.value = PlayRequest.Zap(Source.Browse(Kind.LIVE, ids, category, ""), index)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideEntry(row: GuideRow, nowSecs: Long, format: DateFormat, onClick: () -> Unit) {
    val live = row.start <= nowSecs && row.end > nowSecs
    Row(
        Modifier.fillMaxWidth().focusHighlight().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Logo(row.logo, Modifier.size(width = 56.dp, height = 36.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title.decodeEntities(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeightBold = live)
            Text(
                TitleParser.parse(row.channel).title + "  ·  " + format.format(Date(row.start * 1000)) + " - " + format.format(Date(row.end * 1000)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (live) {
                val total = (row.end - row.start).coerceAtLeast(1)
                LinearProgressIndicator(progress = { ((nowSecs - row.start).toFloat() / total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 3.dp))
            }
        }
    }
}

@Composable
private fun Text(text: String, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, fontWeightBold: Boolean) {
    androidx.compose.material3.Text(
        text, maxLines = maxLines, overflow = overflow,
        fontWeight = if (fontWeightBold) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
    )
}
