package com.kutlusoy.proiptv.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowLeft
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
    primary = Color(0xFFE23A60), onPrimary = Color.White, primaryContainer = Palette.WineDeep, onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = Palette.WineSoft, secondaryContainer = Palette.WineDeep, onSecondaryContainer = Color(0xFFFFD9DE), tertiary = Palette.WineSoft,
    background = Palette.Velvet, onBackground = Palette.Text, surface = Palette.Surface, onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh, onSurfaceVariant = Color(0xFFCDBFC3), surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh, outline = Color(0xFF8F7A81), outlineVariant = Color(0xFF33272B), error = Color(0xFFFF8A80),
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

/** The letters of a title for a missing logo: the first letters of its first two words. */
fun initialsOf(name: String?): String {
    val words = com.kutlusoy.proiptv.parse.TitleParser.parse(name.orEmpty()).title.ifBlank { name.orEmpty() }
        .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    return words.take(2).joinToString("") { it.first().uppercase() }
}

@Composable
private fun Initials(name: String?, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.clip(shape).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.08f), shape), contentAlignment = Alignment.Center) {
        val letters = initialsOf(name)
        if (letters.isNotEmpty()) Text(letters, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.55f), maxLines = 1)
    }
}

/** The stop symbol of a running recording: a square in a red ring. */
@Composable
fun RecStopIcon(size: androidx.compose.ui.unit.Dp = 32.dp) {
    Box(Modifier.size(size).border(2.dp, Palette.WineSoft, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 0.38f).background(Palette.WineSoft, RoundedCornerShape(2.dp)))
    }
}

/** A logo or poster. When the address is missing or the picture cannot be loaded, the first letters of [name] take its place. */
@Composable
fun Logo(url: String?, modifier: Modifier, scale: ContentScale = ContentScale.Fit, name: String? = null) {
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

/** What the user sees before the first account exists: what to do next, with the button for it. */
@Composable
fun NoAccountsPane(state: AppState) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(t("main_no_accounts_title"), style = MaterialTheme.typography.headlineMedium)
        Text(t("main_no_accounts_text"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 8.dp))
        androidx.compose.material3.Button(onClick = { state.tab = Tab.ACCOUNTS }) { Text(t("onboarding_add_account")) }
    }
}

/** Grey placeholder rows while the first data is loaded, instead of an empty screen. */
@Composable
fun SkeletonRows() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(0.04f, 0.12f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "alpha")
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(3) {
            Box(Modifier.width(160.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = alpha)))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(8) { Box(Modifier.width(140.dp).height(200.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = alpha))) }
            }
        }
    }
}

fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}


/**
 * A text field whose cursor stands at the end of the text when it gets the focus (also when the text was filled in before, for example a search
 * that is still there). Takes over changes of the text from outside, such as a cleared search.
 */
@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(value.length))) }
    if (field.text != value) field = androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(value.length))
    androidx.compose.material3.OutlinedTextField(
        field, { field = it; if (it.text != value) onValueChange(it.text) }, modifier, singleLine = singleLine,
        label = label, placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon, supportingText = supportingText,
        visualTransformation = visualTransformation,
    )
}


/**
 * A row of cards that can be moved sideways with the mouse: an arrow at each side appears while there is more to see in that direction, and a
 * click on it moves the row by most of its width.
 */
@Composable
fun ScrollRow(modifier: Modifier = Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val previous = t("player_list_previous")
    val next = t("player_list_next")
    fun move(direction: Int) {
        scope.launch { state.animateScrollBy(direction * state.layoutInfo.viewportSize.width * 0.8f) }
    }
    Box(modifier) {
        androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth(), state = state, horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)
        if (state.canScrollBackward) RowArrow(Alignment.CenterStart, previous, Icons.Filled.KeyboardArrowLeft) { move(-1) }
        if (state.canScrollForward) RowArrow(Alignment.CenterEnd, next, Icons.Filled.KeyboardArrowRight) { move(1) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.RowArrow(align: Alignment, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier.align(align).padding(horizontal = 4.dp).size(40.dp).background(Color(0xCC000000), androidx.compose.foundation.shape.CircleShape),
    ) { Icon(icon, description, tint = Color.White) }
}

/** A small label with the name of a control and, when there is one, its key, after the mouse rests on it. The player controls sit at the bottom edge, so the label opens above the mouse. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Tip(text: String, key: String? = null, content: @Composable () -> Unit) {
    androidx.compose.foundation.TooltipArea(
        tooltip = {
            androidx.compose.material3.Surface(color = Color(0xF2222222), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), shadowElevation = 4.dp) {
                Text(if (key != null) "$text  ($key)" else text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = Color.White)
            }
        },
        delayMillis = 500,
        tooltipPlacement = androidx.compose.foundation.TooltipPlacement.CursorPoint(offset = androidx.compose.ui.unit.DpOffset(0.dp, (-40).dp)),
        content = content,
    )
}
