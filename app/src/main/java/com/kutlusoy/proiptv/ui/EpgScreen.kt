package com.kutlusoy.proiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.Program
import com.kutlusoy.proiptv.data.Source
import com.kutlusoy.proiptv.engine.BudgetExceeded
import com.kutlusoy.proiptv.parse.TitleParser
import com.kutlusoy.proiptv.parse.decodeEntities
import com.kutlusoy.proiptv.recording.NotEnoughSpace
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val START_PAD_MS = 30_000L
private const val END_PAD_MS = 60_000L
private const val SPAN_HOURS = 38

/** The program guide as a timeline: the channels one below the other, the shows of each channel side by side by time. A click on a show opens its details. */
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
    var nowSecs by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowSecs = System.currentTimeMillis() / 1000
        }
    }
    val tv = LocalTv.current
    val hourWidth = if (tv) 320.dp else 220.dp
    val channelWidth = if (tv) 190.dp else 104.dp
    val windowStart = remember { (System.currentTimeMillis() / 1000 - 2 * 3600) / 1800 * 1800 }
    val windowEnd = windowStart + SPAN_HOURS * 3600L
    val categories by (effective?.let { vm.categories(it, Kind.LIVE) } ?: flowOf(emptyList())).collectAsState(initial = emptyList())
    val q = query.trim()
    val channels by remember(ids, category, q) { vm.guideChannels(ids, category, q, windowStart) }.collectAsState(initial = emptyList())
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    fun xOf(secs: Long): Dp = hourWidth * ((secs - windowStart).coerceAtLeast(0) / 3600f)
    val coroutines = rememberCoroutineScope()
    LaunchedEffect(Unit) { scroll.scrollTo(with(density) { xOf(System.currentTimeMillis() / 1000 - 1800).toPx().toInt() }) }
    var detail by remember { mutableStateOf<Pair<Item, Program>?>(null) }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            val allLabel = stringResource(R.string.live_all)
            val otherLabel = stringResource(R.string.live_other)
            if (effective != null && categories.isNotEmpty()) {
                val options = buildList<Pair<String?, String>> {
                    add(null to allLabel)
                    categories.forEach { add(it.remoteId to it.name.ifBlank { otherLabel }) }
                }
                DropdownField(
                    top = true,
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
            OutlinedButton(onClick = { coroutines.launch { scroll.animateScrollTo(with(density) { xOf(System.currentTimeMillis() / 1000 - 1800).toPx().toInt() }) } }, modifier = Modifier.focusHighlight()) {
                Text(stringResource(R.string.epg_now))
            }
        }
        OutlinedTextField(
            query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            placeholder = { Text(stringResource(R.string.epg_search)) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) }
            },
        )
        if (channels.isEmpty()) {
            Text(stringResource(R.string.epg_empty), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(16.dp))
        }
        Row(Modifier.fillMaxWidth().height(26.dp)) {
            Box(Modifier.width(channelWidth))
            Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                Box(Modifier.width(hourWidth * SPAN_HOURS).fillMaxHeight()) {
                    var t = windowStart
                    while (t < windowEnd) {
                        Text(timeFormat.format(Date(t * 1000)), Modifier.offset(x = xOf(t)).padding(start = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
                        t += 1800
                    }
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(channels, key = { it.id }) { channel ->
                val programs by produceState(emptyList<Program>(), channel.id, windowStart) {
                    value = vm.repo.db.programs().range(channel.accountId, channel.epgId.orEmpty(), windowStart, windowEnd)
                }
                Row(Modifier.fillMaxWidth().height(60.dp)) {
                    Row(
                        Modifier.width(channelWidth).fillMaxHeight().padding(vertical = 2.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface)
                            .focusHighlight()
                            .clickable {
                                vm.run {
                                    val index = vm.repo.db.items().livePosition(ids, category, channel.accountId, channel.ord)
                                    vm.player.value = PlayRequest.Zap(Source.Browse(Kind.LIVE, ids, category, ""), index)
                                }
                            }.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Logo(channel.logo, Modifier.size(width = if (tv) 44.dp else 32.dp, height = 30.dp))
                        Text(TitleParser.parse(channel.name).title.ifBlank { channel.name }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                        Box(Modifier.width(hourWidth * SPAN_HOURS).fillMaxHeight()) {
                            for (p in programs) {
                                val from = maxOf(p.start, windowStart)
                                val to = minOf(p.end, windowEnd)
                                if (to <= from) continue
                                val running = p.start <= nowSecs && p.end > nowSecs
                                Box(
                                    Modifier.offset(x = xOf(from)).width((hourWidth * ((to - from) / 3600f) - 2.dp).coerceAtLeast(2.dp)).fillMaxHeight().padding(vertical = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (running) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface)
                                        .focusHighlight()
                                        .clickable { detail = channel to p }.padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Column {
                                        Text(p.title.decodeEntities(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = if (running) FontWeight.SemiBold else FontWeight.Normal)
                                        Text(timeFormat.format(Date(p.start * 1000)) + " - " + timeFormat.format(Date(p.end * 1000)), maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                                    }
                                }
                            }
                            if (nowSecs in windowStart..windowEnd) Box(Modifier.offset(x = xOf(nowSecs)).width(2.dp).fillMaxHeight().background(Color(0xFFE53935)))
                        }
                    }
                }
            }
        }
    }
    detail?.let { (channel, p) ->
        ProgramDialog(vm, channel, p, ids, category, timeFormat) { detail = null }
    }
}

@Composable
private fun ProgramDialog(vm: MainViewModel, channel: Item, p: Program, ids: List<Long>, category: String?, timeFormat: DateFormat, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as com.kutlusoy.proiptv.ProIptvApp
    val now = System.currentTimeMillis() / 1000
    val running = p.start <= now && p.end > now
    val future = p.start > now
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(p.title.decodeEntities(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    TitleParser.parse(channel.name).title + "  ·  " + timeFormat.format(Date(p.start * 1000)) + " - " + timeFormat.format(Date(p.end * 1000)) + "  ·  " + (p.end - p.start) / 60 + " min",
                    style = MaterialTheme.typography.bodySmall,
                )
                p.description?.takeIf { it.isNotBlank() }?.let { Text(it.decodeEntities(), style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis) }
                if (running || future) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val title = "${channel.name}: ${p.title.decodeEntities()}"
                                val endMs = p.end * 1000 + END_PAD_MS
                                message = try {
                                    if (running) {
                                        val target = vm.repo.target(channel)?.copy(title = title)
                                        if (target == null) ctx.getString(R.string.epg_empty) else {
                                            app.recordings.start(target, vm.repo.maxConnections(channel.accountId), endMs)
                                            ctx.getString(R.string.rec_status_recording)
                                        }
                                    } else {
                                        val startMs = p.start * 1000 - START_PAD_MS
                                        val conflict = app.recordings.conflicts(channel, startMs, endMs)
                                        app.recordings.schedule(channel, title, startMs, endMs)
                                        ctx.getString(R.string.rec_scheduled) + if (conflict) " " + ctx.getString(R.string.rec_conflict) else ""
                                    }
                                } catch (e: BudgetExceeded) {
                                    ctx.getString(R.string.player_no_connection, e.limit, e.openTitles.joinToString(", "))
                                } catch (e: NotEnoughSpace) {
                                    ctx.getString(R.string.player_no_space)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusHighlight(),
                    ) { Text(stringResource(R.string.rec_record)) }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onClose()
                    vm.run {
                        val index = vm.repo.db.items().livePosition(ids, category, channel.accountId, channel.ord)
                        vm.player.value = PlayRequest.Zap(Source.Browse(Kind.LIVE, ids, category, ""), index)
                    }
                },
                modifier = Modifier.focusHighlight(),
            ) { Text(stringResource(R.string.epg_to_channel)) }
        },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.action_close)) } },
    )
}
