package com.kutlusoy.proiptv.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.focusable

/** The item that was opened from a list: when the list comes back, its card takes the focus again (TV). */
val LocalRestoreId = androidx.compose.runtime.compositionLocalOf<Long?> { null }

/**
 * A block of text that a TV remote can read: it takes the focus, and Down and Up scroll the page until the end of the text is in view.
 * Only then the focus moves on, so a long description is not jumped over.
 */
fun Modifier.tvReadable(canForward: () -> Boolean, canBackward: () -> Boolean, scrollBy: suspend (Float) -> Unit): Modifier = composed {
    if (!LocalTv.current) return@composed this
    var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val windowHeight = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    val density = androidx.compose.ui.platform.LocalDensity.current
    val step = with(density) { 220.dp.toPx() }
    val margin = with(density) { 24.dp.toPx() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    this
        .onGloballyPositioned { bounds = it.boundsInWindow() }
        .onPreviewKeyEvent { e ->
            val b = bounds
            if (b == null || e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) false
            else when (e.key) {
                androidx.compose.ui.input.key.Key.DirectionDown -> if (b.bottom > windowHeight - margin && canForward()) { scope.launch { scrollBy(step) }; true } else false
                androidx.compose.ui.input.key.Key.DirectionUp -> if (b.top < margin && canBackward()) { scope.launch { scrollBy(-step) }; true } else false
                else -> false
            }
        }
        .focusHighlight()
        .focusable()
}

fun Modifier.tvReadable(state: androidx.compose.foundation.ScrollState): Modifier =
    tvReadable({ state.canScrollForward }, { state.canScrollBackward }, { state.animateScrollBy(it) })

fun Modifier.tvReadable(state: androidx.compose.foundation.lazy.LazyListState): Modifier =
    tvReadable({ state.canScrollForward }, { state.canScrollBackward }, { state.animateScrollBy(it) })

fun Modifier.focusHighlight(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    onFocusChanged { focused = it.hasFocus }
        .then(if (focused) Modifier.border(2.dp, color, RoundedCornerShape(10.dp)) else Modifier)
}

/** The letters of a title for a missing logo: the first letters of its first two words ("TRT 1 HD" gives "T1"). */
fun initialsOf(name: String?): String {
    val words = com.kutlusoy.proiptv.parse.TitleParser.parse(name.orEmpty()).title.ifBlank { name.orEmpty() }
        .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    return words.take(2).joinToString("") { it.first().uppercase() }
}

@Composable
private fun Initials(name: String?, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.clip(shape).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.08f), shape), contentAlignment = androidx.compose.ui.Alignment.Center) {
        val letters = initialsOf(name)
        if (letters.isNotEmpty()) androidx.compose.material3.Text(letters, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.55f), maxLines = 1)
    }
}

/** The stop symbol of a running recording: a square in a red ring. */
@Composable
fun RecStopIcon(size: androidx.compose.ui.unit.Dp = 32.dp) {
    Box(Modifier.size(size).border(2.dp, Palette.WineSoft, CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Box(Modifier.size(size * 0.38f).background(Palette.WineSoft, RoundedCornerShape(2.dp)))
    }
}

/** A logo or poster. When the address is missing or the picture cannot be loaded, the first letters of [name] take its place. */
@Composable
fun Logo(url: String?, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Fit, name: String? = null) {
    val shape = RoundedCornerShape(6.dp)
    if (url.isNullOrBlank()) {
        Initials(name, modifier)
    } else {
        coil3.compose.SubcomposeAsyncImage(
            model = url, contentDescription = name, modifier = modifier.clip(shape), contentScale = scale,
            loading = { Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.04f))) },
            error = { Initials(name, Modifier.fillMaxSize()) },
        )
    }
}

/**
 * What the user sees before the first account exists. On a TV the QR code is the first choice (the account is typed on the phone), otherwise
 * the form comes first. Both ways are one click away.
 */
@Composable
fun NoAccountsPane(vm: MainViewModel) {
    val tv = LocalTv.current
    var phone by remember { mutableStateOf(false) }
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Centered {
        androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.kutlusoy.proiptv.R.string.main_no_accounts_title), style = MaterialTheme.typography.headlineMedium)
            androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.kutlusoy.proiptv.R.string.main_no_accounts_text), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 8.dp))
            if (tv) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.kutlusoy.proiptv.R.string.onboarding_tv_hint), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 12.dp))
            }
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                val qr = @Composable { first: Modifier ->
                    androidx.compose.material3.Button(onClick = { phone = true }, modifier = first.focusHighlight()) {
                        androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.kutlusoy.proiptv.R.string.accounts_phone))
                    }
                }
                val manual = @Composable { first: Modifier ->
                    val style: @Composable () -> Unit = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.kutlusoy.proiptv.R.string.onboarding_add_account)) }
                    if (tv) androidx.compose.material3.OutlinedButton(onClick = { vm.requestedTab.value = "ACCOUNTS" }, modifier = first.focusHighlight()) { style() }
                    else androidx.compose.material3.Button(onClick = { vm.requestedTab.value = "ACCOUNTS" }, modifier = first.focusHighlight()) { style() }
                }
                if (tv) { qr(Modifier.focusRequester(first)); manual(Modifier) } else { manual(Modifier.focusRequester(first)); qr(Modifier) }
            }
        }
    }
    if (phone) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        RemoteSetupDialog(vm, onAdded = { name -> phone = false; android.widget.Toast.makeText(ctx, ctx.getString(com.kutlusoy.proiptv.R.string.remote_added, name), android.widget.Toast.LENGTH_LONG).show() }, onDismiss = { phone = false })
    }
}

/** Grey placeholder rows while the first data is loaded, instead of an empty screen. */
@Composable
fun SkeletonRows() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(0.04f, 0.12f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "alpha")
    androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp)) {
        repeat(3) {
            Box(Modifier.width(160.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = alpha)))
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                repeat(6) { Box(Modifier.width(120.dp).height(170.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = alpha))) }
            }
        }
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { content() }
}

fun formatClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun <T> DropdownField(
    selected: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    top: Boolean = false,
    counts: Map<T, Int> = emptyMap(),
) {
    val topFocus = LocalTopFocus.current
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Column(modifier) {
        if (label != null) {
            androidx.compose.material3.Text(
                label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Box {
            androidx.compose.material3.OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().then(if (top && topFocus != null) Modifier.focusRequester(topFocus) else Modifier).focusHighlight()) {
                androidx.compose.material3.Text(selected, Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.ArrowDropDown, null)
            }
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (value, name) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.Text(name, Modifier.weight(1f, fill = false), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                // how many titles the entry holds
                                counts[value]?.let { androidx.compose.material3.Text(it.toString(), Modifier.padding(start = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                            }
                        },
                        onClick = { open = false; onSelect(value) },
                    )
                }
            }
        }
    }
}

/** The first selection field of the screen. Right on the star of a list row jumps there, so one does not scroll to the top. */
val LocalTopFocus = androidx.compose.runtime.compositionLocalOf<androidx.compose.ui.focus.FocusRequester?> { null }

/** Account names by id, filled only while more than one account is switched on, so lists can show where an entry comes from. */
val LocalAccountNames = androidx.compose.runtime.compositionLocalOf<Map<Long, String>> { emptyMap() }

val LocalChromeHidden = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.MutableState<Boolean>> {
    androidx.compose.runtime.mutableStateOf(false)
}

val DownloadIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("download", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            androidx.compose.ui.graphics.vector.PathParser().parsePathString("M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z").toNodes(),
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
        .build()
}

@Composable
fun RemoveFavoriteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        text = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.kutlusoy.proiptv.R.string.fav_remove_confirm, name.ifBlank { "?" })) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm, modifier = Modifier.focusHighlight()) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.kutlusoy.proiptv.R.string.fav_remove))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.focusHighlight()) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.kutlusoy.proiptv.R.string.action_cancel))
            }
        },
    )
}

val GuideIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("guide", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            androidx.compose.ui.graphics.vector.PathParser().parsePathString(
                "M20 13H4c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h16c.55 0 1-.45 1-1v-6c0-.55-.45-1-1-1zm0-10H4c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h16c.55 0 1-.45 1-1V4c0-.55-.45-1-1-1z",
            ).toNodes(),
            fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
        )
        .build()
}

/** Moves the focus to a neighbour (next, else previous) before a control removes its own row, so the focus stays in the screen. */
@Composable
fun rememberLeaveFocus(): () -> Unit {
    val manager = androidx.compose.ui.platform.LocalFocusManager.current
    return { if (!manager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)) manager.moveFocus(androidx.compose.ui.focus.FocusDirection.Up) }
}
