package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kutlusoy.proiptv.parse.TitleParser
import com.kutlusoy.proiptv.parse.XmltvProgram
import java.text.DateFormat
import java.util.Date

private class GuideRow(val entry: Entry, val program: XmltvProgram)

/** All shows of the channels of one group, by time. A click starts the channel; up and down while playing follow the same group. */
@Composable
fun EpgScreen(state: AppState) {
    var category by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var range by remember { mutableStateOf(0) }
    val live = state.data.entries(Kind.LIVE)
    val categories = state.data.categories(Kind.LIVE)
    val horizon = when (range) { 1 -> 3 * 3600L; 2 -> 12 * 3600L; else -> 0L }
    val now = state.tick
    val q = query.trim()
    val channels = remember(live, category) {
        if (category == null) live else live.filter { "${it.accountId}|${it.categoryId}" == category }
    }
    val rows = remember(channels, state.guide, q, horizon, now / 60) {
        val list = ArrayList<GuideRow>()
        for (e in channels) {
            val programs = state.guide.programs[Guide.key(e.accountId, e.epgId ?: continue)] ?: continue
            for (p in programs) {
                if (p.end <= now || p.start > now + horizon) continue
                if (q.isNotEmpty() && !p.title.contains(q, ignoreCase = true) && !e.name.contains(q, ignoreCase = true)) continue
                list += GuideRow(e, p)
            }
        }
        list.sortedBy { it.program.start }.take(400)
    }
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(t("nav_epg"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val options = buildList<Pair<String?, String>> {
                add(null to t("live_all"))
                categories.forEach { add("${it.accountId}|${it.id}" to it.name.ifBlank { t("live_other") }) }
            }
            DropdownField(options.firstOrNull { it.first == category }?.second ?: t("live_all"), options, { category = it }, Modifier.weight(0.45f).widthIn(min = 160.dp, max = 360.dp))
            DropdownField(
                t(when (range) { 1 -> "epg_next_3h"; 2 -> "epg_next_12h"; else -> "epg_now" }),
                listOf(0 to t("epg_now"), 1 to t("epg_next_3h"), 2 to t("epg_next_12h")), { range = it }, Modifier.weight(0.3f).widthIn(min = 140.dp, max = 240.dp),
            )
            IconButton(onClick = { state.refreshGuide() }, enabled = !state.guideLoading) { Icon(Icons.Filled.Refresh, t("account_sync_guide")) }
        }
        OutlinedTextField(
            query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            placeholder = { Text(t("epg_search")) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null) } },
        )
        if (rows.isEmpty()) Text(t(if (state.guideLoading) "series_loading" else "epg_empty"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(8.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(rows, key = { "${it.entry.key}-${it.program.start}" }) { row ->
                val live = row.program.start <= now && row.program.end > now
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        val index = channels.indexOfFirst { it.key == row.entry.key }.coerceAtLeast(0)
                        state.player = PlayRequest.Live(channels, index)
                    }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Logo(row.entry.logo, Modifier.size(width = 56.dp, height = 36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.program.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal)
                        Text(
                            TitleParser.parse(row.entry.name).title + "  ·  " + timeFormat.format(Date(row.program.start * 1000)) + " - " + timeFormat.format(Date(row.program.end * 1000)),
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (live) {
                            LinearProgressIndicator(
                                progress = { ((now - row.program.start).toFloat() / (row.program.end - row.program.start).coerceAtLeast(1)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
