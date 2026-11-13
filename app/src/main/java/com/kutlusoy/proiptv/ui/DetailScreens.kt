package com.kutlusoy.proiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.Progress
import com.kutlusoy.proiptv.net.SeriesDetail
import com.kutlusoy.proiptv.net.VodInfo
import com.kutlusoy.proiptv.net.XtreamEpisode

@Composable
private fun Hero(image: String?, height: Dp, onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().height(height)) {
        if (!image.isNullOrBlank()) {
            AsyncImage(model = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent, Palette.Velvet))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Palette.Velvet.copy(alpha = 0.8f), Color.Transparent))))
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp).focusHighlight()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = Color.White)
        }
        content()
    }
}

@Composable
private fun heroHeight(): Dp = if (LocalConfiguration.current.screenWidthDp >= 720) 340.dp else 260.dp

@Composable
private fun durationText(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val minutes = seconds / 60
    return if (minutes >= 60) stringResource(R.string.detail_duration_hm, minutes / 60, minutes % 60) else stringResource(R.string.detail_duration_m, minutes)
}

@Composable
private fun MetaRow(parts: List<String?>) {
    val shown = parts.filterNotNull().filter { it.isNotBlank() }
    if (shown.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { i, text ->
            if (i == 0 && text.startsWith("★")) {
                Text(text, color = Palette.WineSoft, fontWeight = FontWeight.SemiBold)
            } else {
                Text(text, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Fact(label: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.padding(top = 4.dp)) {
        Text(stringResource(label) + ": ", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun year(date: String?): String? = date?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }

private fun rating(value: String?): String? = value?.toDoubleOrNull()?.takeIf { it > 0 }?.let { "★ " + "%.1f".format(it) }

@Composable
private fun PlayButton(label: String, focus: FocusRequester? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Wine, contentColor = Color.White),
        modifier = Modifier.then(if (focus != null) Modifier.focusRequester(focus) else Modifier).focusHighlight(),
    ) {
        Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
        Text(label, Modifier.padding(start = 6.dp), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MovieDetail(vm: MainViewModel, item: Item, onBack: () -> Unit) {
    val loaded by produceState<Pair<Boolean, VodInfo?>>(false to null, item.id) {
        value = true to runCatching { vm.repo.vodInfo(item) }.getOrNull()
    }
    val info = loaded.second
    val progress by produceState<Progress?>(null, item.id) { value = vm.repo.db.progress().get(item.accountId, Kind.MOVIE, item.remoteId) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun play(fromStart: Boolean) = vm.run {
        val target = vm.repo.target(item) ?: return@run
        vm.player.value = PlayRequest.Single(target, fromStart)
    }

    val saved = progress?.takeIf { it.positionMs > 5_000 }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Hero(info?.backdrop ?: info?.cover ?: item.logo, heroHeight(), onBack) {
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                MetaRow(listOf(rating(info?.rating ?: item.rating), year(info?.releaseDate), durationText(info?.durationSecs), info?.genre))
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayButton(
                    if (saved != null) stringResource(R.string.detail_resume, formatClock(saved.positionMs)) else stringResource(R.string.detail_play),
                    focus,
                ) { play(false) }
                if (saved != null) {
                    OutlinedButton(onClick = { play(true) }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.detail_restart)) }
                }
            }
            if (saved != null && saved.durationMs > 0) {
                LinearProgressIndicator(progress = { (saved.positionMs.toFloat() / saved.durationMs).coerceIn(0f, 1f) }, Modifier.fillMaxWidth(0.5f))
            }
            val plot = info?.plot ?: item.plot
            if (plot != null) Text(plot, style = MaterialTheme.typography.bodyLarge)
            Column {
                Fact(R.string.detail_director, info?.director)
                Fact(R.string.detail_cast, info?.cast)
                Fact(R.string.detail_released, info?.releaseDate)
            }
            if (loaded.first && plot == null && info == null) {
                Text(stringResource(R.string.detail_no_info), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
        }
    }
}

private fun resumeEpisode(episodes: List<XtreamEpisode>, progress: Map<String, Progress>): XtreamEpisode? {
    if (episodes.isEmpty()) return null
    val last = episodes.filter { it.id in progress }.maxByOrNull { progress.getValue(it.id).updated } ?: return episodes.first()
    val p = progress.getValue(last.id)
    if (p.positionMs > 5_000) return last
    val index = episodes.indexOf(last)
    return episodes.getOrNull(index + 1) ?: last
}

@Composable
fun SeriesDetailScreen(vm: MainViewModel, series: Item, onBack: () -> Unit) {
    val detail by produceState<SeriesDetail?>(null, series.id) {
        value = runCatching { vm.repo.seriesDetail(series) }.getOrDefault(SeriesDetail(null, emptyList()))
    }
    val episodes = detail?.episodes.orEmpty()
    val progress by produceState<Map<String, Progress>>(emptyMap(), detail) {
        value = vm.repo.episodeProgress(series.accountId, episodes.map { it.id })
    }
    var season by rememberSaveable { mutableIntStateOf(-1) }
    val seasons = episodes.map { it.season }.distinct()
    val active = if (season in seasons) season else seasons.firstOrNull() ?: 1
    val info = detail?.info
    val next = resumeEpisode(episodes, progress)
    val focus = remember { FocusRequester() }
    LaunchedEffect(next?.id) { if (next != null) runCatching { focus.requestFocus() } }

    fun play(ep: XtreamEpisode) = vm.run {
        val target = vm.repo.episodeTarget(series, ep) ?: return@run
        vm.player.value = PlayRequest.Single(target)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Hero(info?.backdrop ?: info?.cover ?: series.logo, heroHeight(), onBack) {
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(series.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    MetaRow(listOf(rating(info?.rating ?: series.rating), year(info?.releaseDate), info?.genre))
                }
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (next != null) {
                    val label = if (progress.containsKey(next.id) || progress.isNotEmpty()) {
                        stringResource(R.string.detail_continue_episode, next.season, next.number)
                    } else {
                        stringResource(R.string.detail_play_episode, next.season, next.number)
                    }
                    PlayButton(label, focus) { play(next) }
                }
                val plot = info?.plot ?: series.plot
                if (plot != null) Text(plot, style = MaterialTheme.typography.bodyLarge)
                Column {
                    Fact(R.string.detail_cast, info?.cast)
                    Fact(R.string.detail_director, info?.director)
                }
                if (detail == null) Text(stringResource(R.string.series_loading))
            }
        }
        if (seasons.isNotEmpty()) {
            item {
                DropdownField(
                    selected = stringResource(R.string.series_season, active),
                    options = seasons.map { it to stringResource(R.string.series_season, it) },
                    onSelect = { season = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).width(220.dp),
                )
            }
        }
        items(episodes.filter { it.season == active }, key = { it.id }) { ep ->
            EpisodeRow(ep, progress[ep.id]) { play(ep) }
        }
    }
}

@Composable
private fun EpisodeRow(ep: XtreamEpisode, progress: Progress?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).focusHighlight().clickable(onClick = onClick).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(140.dp).height(80.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (!ep.image.isNullOrBlank()) AsyncImage(model = ep.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Icon(Icons.Filled.PlayArrow, null, Modifier.align(Alignment.Center).size(28.dp), tint = Color.White.copy(alpha = 0.85f))
            if (progress != null && progress.positionMs > 0 && progress.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${ep.number}. ${ep.title}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            durationText(ep.durationSecs)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
            ep.plot?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)) }
        }
    }
}
