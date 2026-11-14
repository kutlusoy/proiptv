package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

object Palette {
    val Velvet = Color(0xFF0B0709)
    val Surface = Color(0xFF151013)
    val SurfaceHigh = Color(0xFF211619)
    val Wine = Color(0xFFC40035)
    val WineDeep = Color(0xFF7D0F25)
    val WineSoft = Color(0xFFF2506F)
    val Text = Color(0xFFF3EBED)
    val Outline = Color(0xFF4A3A3F)
}

private val Colors = darkColorScheme(
    primary = Palette.Wine, onPrimary = Color.White, primaryContainer = Palette.WineDeep, onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = Palette.WineSoft, secondaryContainer = Palette.WineDeep, onSecondaryContainer = Color(0xFFFFD9DE), tertiary = Palette.WineSoft,
    background = Palette.Velvet, onBackground = Palette.Text, surface = Palette.Surface, onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh, onSurfaceVariant = Color(0xFFCDBFC3), surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh, outline = Palette.Outline, outlineVariant = Color(0xFF33272B), error = Color(0xFFFF8A80),
)

@Composable
fun ProIptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

@Composable
fun <T> DropdownField(selected: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                .clickable { open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { open = false; onSelect(value) })
            }
        }
    }
}

@Composable
fun Logo(url: String?, modifier: Modifier, scale: ContentScale = ContentScale.Fit) {
    val shape = RoundedCornerShape(6.dp)
    if (url.isNullOrBlank()) Box(modifier.clip(shape).border(1.dp, Color.White.copy(alpha = 0.08f), shape))
    else AsyncImage(model = url, contentDescription = null, modifier = modifier.clip(shape), contentScale = scale)
}

fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
