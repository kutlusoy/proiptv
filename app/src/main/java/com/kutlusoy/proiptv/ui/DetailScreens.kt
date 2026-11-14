package com.kutlusoy.proiptv.ui

import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import com.kutlusoy.proiptv.data.DlStatus
import com.kutlusoy.proiptv.data.Download
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import com.kutlusoy.proiptv.net.trailerUrl
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
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
import com.kutlusoy.proiptv.parse.TitleParser
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.Progress
import com.kutlusoy.proiptv.data.SeriesProgress
import com.kutlusoy.proiptv.net.SeriesDetail
import com.kutlusoy.proiptv.net.VodInfo
import com.kutlusoy.proiptv.net.XtreamEpisode

@Composable
private fun Hero(image: String?, height: Dp, onBack: () -> Unit, trailing: @Composable BoxScope.() -> Unit = {}, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().height(height)) {
        if (!image.isNullOrBlank()) {
            AsyncImage(model = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent, Palette.Velvet))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Palette.Velvet.copy(alpha = 0.8f), Color.Transparent))))
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp).focusHighlight()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = Color.White)
        }
        trailing()
        content()
    }
}

@Composable
private fun BoxScope.FavStar(vm: MainViewModel, item: Item) {
    val keys by vm.favKeys.collectAsState()
    val allLists by vm.favLists.collectAsState()
    val lists = allLists.filter { it.kind == item.kind }
    var dialog by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val fav = Triple(item.accountId, item.kind, item.remoteId) in keys
    IconButton(
        onClick = {
            if (lists.size <= 1) {
                val list = lists.firstOrNull() ?: return@IconButton
                if (fav) confirmRemove = true else vm.run { vm.repo.setFavorite(list.id, item.accountId, item.kind, item.remoteId, item.name, true) }
            } else {
                dialog = true
            }
        },
        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).focusHighlight(),
    ) {
        Icon(Icons.Filled.Star, stringResource(if (fav) R.string.fav_remove else R.string.fav_add), tint = if (fav) Color(0xFFFFC107) else Color.White.copy(alpha = 0.8f))
    }
    if (dialog) ListsDialog(vm, item) { dialog = false }
    if (confirmRemove) {
        RemoveFavoriteDialog(item.name, onConfirm = {
            lists.firstOrNull()?.let { list -> vm.run { vm.repo.setFavorite(list.id, item.accountId, item.kind, item.remoteId, item.name, false) } }
            confirmRemove = false
        }, onDismiss = { confirmRemove = false })
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieDetail(vm: MainViewModel, item: Item, onBack: () -> Unit, onOpen: (Item) -> Unit = {}) {
    val loaded by produceState<Pair<Boolean, VodInfo?>>(false to null, item.id) {
        value = true to runCatching { vm.repo.vodInfo(item) }.getOrNull()
    }
    val info = loaded.second
    var refresh by remember { mutableIntStateOf(0) }
    val progress by produceState<Progress?>(null, item.id, refresh) { value = vm.repo.db.progress().get(item.accountId, Kind.MOVIE, item.remoteId) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val downloads by vm.downloads.collectAsState()
    val dl = downloads.firstOrNull { it.accountId == item.accountId && it.kind == Kind.MOVIE && it.remoteId == item.remoteId }

    fun play(fromStart: Boolean) = vm.run {
        val target = dl?.let { vm.localTarget(it) } ?: vm.repo.target(item) ?: return@run
        vm.player.value = PlayRequest.Single(target, fromStart)
    }

    val saved = progress?.takeIf { it.positionMs > 5_000 }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Hero(info?.backdrop ?: info?.cover ?: item.logo, heroHeight(), onBack, trailing = { FavStar(vm, item) }) {
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(remember(item.name) { TitleParser.parse(item.name).title }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                MetaRow(listOf(rating(info?.rating ?: item.rating), year(info?.releaseDate), durationText(info?.durationSecs), info?.genre, if (progress?.watched == true) "\u2713 " + stringResource(R.string.detail_watched) else null))
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayButton(
                    if (saved != null) stringResource(R.string.detail_resume, formatClock(saved.positionMs)) else stringResource(R.string.detail_play),
                    focus,
                ) { play(false) }
                if (saved != null) {
                    OutlinedButton(onClick = { play(true) }, modifier = Modifier.focusHighlight()) { Text(stringResource(R.string.detail_restart), maxLines = 1) }
                }
                DownloadButton(dl, onStart = {
                    vm.download(listOf(Download(accountId = item.accountId, kind = Kind.MOVIE, remoteId = item.remoteId, title = item.name, logo = item.logo, ext = item.ext ?: "mp4")))
                }, onRetry = { dl?.let { vm.retryDownload(it.id) } })
                val watched = progress?.watched == true
                OutlinedButton(
                    onClick = { vm.run { vm.repo.setWatched(item.accountId, Kind.MOVIE, listOf(item.remoteId), !watched); refresh++ } },
                    modifier = Modifier.focusHighlight(),
                ) { Text(stringResource(if (watched) R.string.detail_mark_unwatched else R.string.detail_mark_watched), maxLines = 1) }
                TrailerButton(info?.trailer)
            }
            RatingStars(info?.rating ?: item.rating)
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
            RelatedRow(vm, item, R.string.detail_related_movies, onOpen)
        }
    }
}

@Composable
private fun TrailerButton(trailer: String?) {
    val url = trailerUrl(trailer) ?: return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    OutlinedButton(
        onClick = { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } },
        modifier = Modifier.focusHighlight(),
    ) { Text(stringResource(R.string.detail_trailer), maxLines = 1) }
}

/** Five stars from the rating of the provider (0 to 10). */
@Composable
private fun RatingStars(value: String?) {
    val r = value?.toDoubleOrNull()?.takeIf { it > 0 } ?: return
    val filled = Math.round(r / 2).toInt().coerceIn(0, 5)
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) Icon(Icons.Filled.Star, null, tint = if (i <= filled) Color(0xFFFFC107) else Color.White.copy(alpha = 0.25f), modifier = Modifier.size(22.dp))
        Text("  " + "%.1f".format(r), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Other titles of the same category, one click away. */
@Composable
private fun RelatedRow(vm: MainViewModel, item: Item, title: Int, onOpen: (Item) -> Unit) {
    val related by produceState<List<Item>>(emptyList(), item.id) {
        value = vm.repo.db.items().first(item.kind, item.accountId, item.categoryRemoteId, 30).filter { it.id != item.id }
    }
    if (related.isEmpty()) return
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
        items(related, key = { it.id }) { PosterCard(it, Modifier.width(if (LocalTv.current) 150.dp else 112.dp)) { onOpen(it) } }
    }
}

@Composable
fun SeriesDetailScreen(vm: MainViewModel, series: Item, onBack: () -> Unit, onOpen: (Item) -> Unit = {}) {
    val detail by produceState<SeriesDetail?>(null, series.id) {
        value = runCatching { vm.repo.seriesDetail(series) }.getOrDefault(SeriesDetail(null, emptyList()))
    }
    val episodes = detail?.episodes.orEmpty()
    var refresh by remember { mutableIntStateOf(0) }
    val progress by produceState<Map<String, Progress>>(emptyMap(), detail, refresh) {
        value = vm.repo.episodeProgress(series.accountId, episodes.map { it.id })
    }
    var season by rememberSaveable { mutableIntStateOf(-1) }
    val seasons = episodes.map { it.season }.distinct()
    val active = if (season in seasons) season else seasons.firstOrNull() ?: 1
    val info = detail?.info
    val next = SeriesProgress.next(episodes, progress)
    val focus = remember { FocusRequester() }
    LaunchedEffect(next?.episode?.id) { if (next != null) runCatching { focus.requestFocus() } }

    val downloads by vm.downloads.collectAsState()
    fun downloadOf(ep: XtreamEpisode) = downloads.firstOrNull { it.accountId == series.accountId && it.kind == Kind.EPISODE && it.remoteId == ep.id }
    fun toDownload(ep: XtreamEpisode) = Download(
        accountId = series.accountId, kind = Kind.EPISODE, remoteId = ep.id, title = ep.title, logo = ep.image ?: series.logo, ext = ep.ext,
        seriesRemoteId = series.remoteId, seriesName = series.name, season = ep.season, episode = ep.number,
    )

    fun play(ep: XtreamEpisode) = vm.run {
        val target = downloadOf(ep)?.let { vm.localTarget(it) } ?: vm.repo.episodeTarget(series, ep) ?: return@run
        vm.player.value = PlayRequest.Single(target)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Hero(info?.backdrop ?: info?.cover ?: series.logo, heroHeight(), onBack, trailing = { FavStar(vm, series) }) {
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(remember(series.name) { TitleParser.parse(series.name).title }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    MetaRow(listOf(rating(info?.rating ?: series.rating), year(info?.releaseDate), info?.genre))
                }
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (next != null) {
                    val ep = next.episode
                    val label = if (next.continuing) stringResource(R.string.detail_continue_episode, ep.season, ep.number) else stringResource(R.string.detail_play_episode, ep.season, ep.number)
                    PlayButton(label, focus) { play(ep) }
                }
                RatingStars(info?.rating ?: series.rating)
                TrailerButton(info?.trailer)
                val plot = info?.plot ?: series.plot
                if (plot != null) Text(plot, style = MaterialTheme.typography.bodyLarge)
                Column {
                    Fact(R.string.detail_cast, info?.cast)
                    Fact(R.string.detail_director, info?.director)
                    Fact(R.string.detail_released, info?.releaseDate)
                }
                if (detail == null) Text(stringResource(R.string.series_loading))
            }
        }
        if (seasons.isNotEmpty()) {
            item {
                var menu by remember { mutableStateOf(false) }
                val seasonIds = episodes.filter { it.season == active }.map { it.id }
                val allWatched = seasonIds.isNotEmpty() && seasonIds.all { progress[it]?.watched == true }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    DropdownField(
                        selected = stringResource(R.string.series_season, active),
                        options = seasons.map { it to stringResource(R.string.series_season, it) },
                        onSelect = { season = it },
                        modifier = Modifier.width(220.dp),
                    )
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.focusHighlight()) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dl_season)) },
                                onClick = {
                                    menu = false
                                    vm.download(episodes.filter { it.season == active && downloadOf(it) == null }.map { toDownload(it) })
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (allWatched) R.string.detail_season_unwatched else R.string.detail_season_watched)) },
                                onClick = {
                                    menu = false
                                    vm.run { vm.repo.setWatched(series.accountId, Kind.EPISODE, seasonIds, !allWatched); refresh++ }
                                },
                            )
                        }
                    }
                }
            }
        }
        items(episodes.filter { it.season == active }, key = { it.id }) { ep ->
            EpisodeRow(
                ep, progress[ep.id], downloadOf(ep), onClick = { play(ep) },
                onDownload = { val d = downloadOf(ep); if (d == null) vm.download(listOf(toDownload(ep))) else if (d.status == DlStatus.FAILED) vm.retryDownload(d.id) },
                onToggleWatched = { vm.run { vm.repo.setWatched(series.accountId, Kind.EPISODE, listOf(ep.id), progress[ep.id]?.watched != true); refresh++ } },
            )
        }
    }
}

@Composable
private fun EpisodeRow(ep: XtreamEpisode, progress: Progress?, download: Download?, onClick: () -> Unit, onDownload: () -> Unit, onToggleWatched: () -> Unit) {
    val watched = progress?.watched == true
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).focusHighlight().clickable(onClick = onClick).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(140.dp).height(80.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (!ep.image.isNullOrBlank()) AsyncImage(model = ep.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Icon(Icons.Filled.PlayArrow, null, Modifier.align(Alignment.Center).size(28.dp), tint = Color.White.copy(alpha = 0.85f))
            if (watched && (progress?.positionMs ?: 0) == 0L) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp), tint = Palette.WineSoft)
            }
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
        IconButton(onClick = onDownload, modifier = Modifier.focusHighlight()) {
            when (download?.status) {
                DlStatus.RUNNING -> CircularProgressIndicator(
                    progress = { if (download.total > 0) (download.bytes.toFloat() / download.total).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.size(22.dp), strokeWidth = 2.dp,
                )
                DlStatus.QUEUED -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                DlStatus.DONE -> Icon(DownloadIcon, stringResource(R.string.dl_downloaded), tint = Palette.WineSoft)
                DlStatus.FAILED -> Icon(DownloadIcon, stringResource(R.string.dl_retry), tint = MaterialTheme.colorScheme.error)
                else -> Icon(DownloadIcon, stringResource(R.string.dl_download), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        }
        IconButton(onClick = onToggleWatched, modifier = Modifier.focusHighlight()) {
            Icon(
                Icons.Filled.CheckCircle,
                stringResource(if (watched) R.string.detail_mark_unwatched else R.string.detail_mark_watched),
                tint = if (watched) Palette.WineSoft else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun DownloadButton(dl: Download?, onStart: () -> Unit, onRetry: () -> Unit) {
    val label = when (dl?.status) {
        null -> stringResource(R.string.dl_download)
        DlStatus.QUEUED -> stringResource(R.string.dl_queued)
        DlStatus.RUNNING -> "${stringResource(R.string.dl_running)} ${if (dl.total > 0) (dl.bytes * 100 / dl.total).toInt() else 0} %"
        DlStatus.DONE -> "\u2713 " + stringResource(R.string.dl_downloaded)
        else -> stringResource(R.string.dl_retry)
    }
    OutlinedButton(
        onClick = { if (dl == null) onStart() else if (dl.status == DlStatus.FAILED) onRetry() },
        enabled = dl == null || dl.status == DlStatus.FAILED,
        modifier = Modifier.focusHighlight(),
    ) { Text(label) }
}
