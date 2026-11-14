package com.kutlusoy.proiptv.desktop

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.parse.TitleParser
import com.kutlusoy.proiptv.parse.XmltvProgram
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private val CHANNEL_WIDTH = 170.dp
private val ROW_HEIGHT = 58.dp
private val HOUR_WIDTH = 300.dp

private class Selected(val entry: Entry, val program: XmltvProgram)

/** The program guide as a timeline: the channels one below the other, the shows of each channel side by side by time. A click on a show opens its details. */
@Composable
fun EpgScreen(state: AppState) {
    var category by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Selected?>(null) }
    val live = state.data.entries(Kind.LIVE)
    val categories = state.data.categories(Kind.LIVE)
    val q = query.trim()
    val now = state.tick
    val channels = remember(live, state.guide, category, q) {
        live.asSequence()
            .filter { category == null || "${it.accountId}|${it.categoryId}" == category }
            .filter { e ->
                val programs = state.guide.programs[Guide.key(e.accountId, e.epgId ?: return@filter false)] ?: return@filter false
                q.isEmpty() || e.name.contains(q, ignoreCase = true) || programs.any { it.title.contains(q, ignoreCase = true) }
            }
            .toList()
    }
    // the window: 2 hours back to 36 hours ahead, on half hours
    val windowStart = remember { (System.currentTimeMillis() / 1000 - 2 * 3600) / 1800 * 1800 }
    val windowEnd = windowStart + 38 * 3600L
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    fun xOf(secs: Long): Dp = HOUR_WIDTH * ((secs - windowStart).coerceAtLeast(0) / 3600f)
    val coroutines = androidx.compose.runtime.rememberCoroutineScope()
    fun jumpToNow() {
        coroutines.launch { scroll.animateScrollTo(with(density) { xOf(now - 1800).toPx().toInt() }) }
    }
    LaunchedEffect(Unit) { scroll.scrollTo(with(density) { xOf(System.currentTimeMillis() / 1000 - 1800).toPx().toInt() }) }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val options = buildList<Pair<String?, String>> {
                    add(null to t("live_all"))
                    categories.forEach { add("${it.accountId}|${it.id}" to it.name.ifBlank { t("live_other") }) }
                }
                DropdownField(options.firstOrNull { it.first == category }?.second ?: t("live_all"), options, { category = it }, Modifier.weight(0.4f).widthIn(min = 160.dp, max = 320.dp))
                OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.weight(0.6f),
                    placeholder = { Text(t("epg_search")) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null) } },
                )
                IconButton(onClick = { coroutines.launch { scroll.animateScrollTo((scroll.value - with(density) { (HOUR_WIDTH * 2).toPx().toInt() }).coerceAtLeast(0)) } }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) }
                OutlinedButton(onClick = { jumpToNow() }) { Text(t("epg_now")) }
                IconButton(onClick = { coroutines.launch { scroll.animateScrollTo(scroll.value + with(density) { (HOUR_WIDTH * 2).toPx().toInt() }) } }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                IconButton(onClick = { state.refreshGuide() }, enabled = !state.guideLoading) { Icon(Icons.Filled.Refresh, t("account_sync_guide")) }
            }
            if (channels.isEmpty()) {
                Text(t(if (state.guideLoading) "series_loading" else "epg_empty"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(8.dp))
            }
            TimeAxis(scroll, windowStart, windowEnd)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(channels, key = { it.key }) { e ->
                    val programs = state.guide.programs[Guide.key(e.accountId, e.epgId ?: "")].orEmpty()
                    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
                        ChannelCell(e) {
                            val index = channels.indexOfFirst { it.key == e.key }.coerceAtLeast(0)
                            state.player = PlayRequest.Live(channels, index)
                        }
                        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                            Box(Modifier.width(HOUR_WIDTH * ((windowEnd - windowStart) / 3600f)).fillMaxHeight()) {
                                for (p in programs) {
                                    if (p.end <= windowStart || p.start >= windowEnd) continue
                                    val from = maxOf(p.start, windowStart)
                                    val to = minOf(p.end, windowEnd)
                                    val running = p.start <= now && p.end > now
                                    val isSel = selected?.let { it.entry.key == e.key && it.program.start == p.start } == true
                                    Box(
                                        Modifier.offset(x = xOf(from)).width((HOUR_WIDTH * ((to - from) / 3600f) - 2.dp).coerceAtLeast(2.dp)).fillMaxHeight().padding(vertical = 2.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (running) Palette.WineDeep.copy(alpha = 0.75f) else Color(0xFF2B222A))
                                            .then(if (isSel) Modifier.border(2.dp, Palette.WineSoft, RoundedCornerShape(6.dp)) else Modifier)
                                            .clickable { selected = Selected(e, p) }.padding(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Column {
                                            Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = if (running) FontWeight.SemiBold else FontWeight.Normal)
                                            Text(clockOf(p.start) + " - " + clockOf(p.end), maxLines = 1, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                                        }
                                    }
                                }
                                if (now in windowStart..windowEnd) Box(Modifier.offset(x = xOf(now)).width(2.dp).fillMaxHeight().background(Color(0xFFE53935)))
                            }
                        }
                    }
                }
            }
        }
        selected?.let { sel -> DetailPanel(state, sel, channels) { selected = null } }
    }
}

private fun clockOf(secs: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(secs * 1000))

@Composable
private fun ChannelCell(e: Entry, onClick: () -> Unit) {
    Row(
        Modifier.width(CHANNEL_WIDTH).fillMaxHeight().padding(vertical = 2.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Surface).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Logo(e.logo, Modifier.size(width = 44.dp, height = 32.dp))
        Text(TitleParser.parse(e.name).title.ifBlank { e.name }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TimeAxis(scroll: androidx.compose.foundation.ScrollState, windowStart: Long, windowEnd: Long) {
    Row(Modifier.fillMaxWidth().height(28.dp)) {
        Box(Modifier.width(CHANNEL_WIDTH))
        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
            Box(Modifier.width(HOUR_WIDTH * ((windowEnd - windowStart) / 3600f)).fillMaxHeight()) {
                var t = windowStart
                while (t < windowEnd) {
                    Text(clockOf(t), Modifier.offset(x = HOUR_WIDTH * ((t - windowStart) / 3600f)).padding(start = 4.dp), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                    t += 1800
                }
            }
        }
    }
}

@Composable
private fun DetailPanel(state: AppState, sel: Selected, channels: List<Entry>, onClose: () -> Unit) {
    val p = sel.program
    val now = System.currentTimeMillis() / 1000
    val running = p.start <= now && p.end > now
    val future = p.start > now
    var message by remember(sel) { mutableStateOf<String?>(null) }
    Column(
        Modifier.width(340.dp).fillMaxHeight().background(Palette.Surface).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(TitleParser.parse(sel.entry.name).title.ifBlank { sel.entry.name }, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, t("action_close")) }
        }
        Text(p.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(clockOf(p.start) + " - " + clockOf(p.end) + "  ·  " + ((p.end - p.start) / 60) + " min", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
        p.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = {
            val index = channels.indexOfFirst { it.key == sel.entry.key }.coerceAtLeast(0)
            state.player = PlayRequest.Live(channels, index)
        }, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.PlayArrow, null)
            Text(t("epg_to_channel"), Modifier.padding(start = 6.dp))
        }
        if (running || future) {
            OutlinedButton(onClick = {
                val item = state.livePlayable(sel.entry)
                message = when {
                    item == null -> Strings.get("epg_empty")
                    running -> recordNow(state, item, p)
                    else -> { state.recordingManager.schedule(item, p.start * 1000 - 30_000, p.end * 1000 + 60_000); Strings.get("rec_scheduled") }
                }
            }, Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.FiberManualRecord, null, tint = Color(0xFFE53935))
                Text(t("rec_record"), Modifier.padding(start = 6.dp))
            }
        }
        message?.let { Text(it, color = Palette.WineSoft) }
    }
}

private fun recordNow(state: AppState, item: Playable, p: XmltvProgram): String =
    try {
        state.recordingManager.start(item, p.end * 1000 + 60_000)
        Strings.get("rec_status_recording")
    } catch (e: com.kutlusoy.proiptv.engine.BudgetExceeded) {
        Strings.get("player_no_connection", e.limit, e.openTitles.joinToString())
    } catch (e: NotEnoughSpace) {
        Strings.get("player_no_space")
    } catch (e: Exception) {
        e.message ?: "error"
    }
