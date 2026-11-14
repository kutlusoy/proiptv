package com.kutlusoy.proiptv.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

fun Modifier.focusHighlight(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    onFocusChanged { focused = it.hasFocus }
        .then(if (focused) Modifier.border(2.dp, color, RoundedCornerShape(10.dp)) else Modifier)
}

@Composable
fun Logo(url: String?, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Fit) {
    val shape = RoundedCornerShape(6.dp)
    if (url.isNullOrBlank()) {
        Box(modifier.clip(shape).border(1.dp, Color.White.copy(alpha = 0.08f), shape))
    } else {
        AsyncImage(model = url, contentDescription = null, modifier = modifier.clip(shape), contentScale = scale)
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
) {
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
            androidx.compose.material3.OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().focusHighlight()) {
                androidx.compose.material3.Text(selected, Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.ArrowDropDown, null)
            }
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (value, name) ->
                    androidx.compose.material3.DropdownMenuItem(text = { androidx.compose.material3.Text(name) }, onClick = { open = false; onSelect(value) })
                }
            }
        }
    }
}

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
