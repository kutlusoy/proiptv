package com.kutlusoy.proiptv.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** The texts of the Android app (the strings.xml files) are the source of the texts here, so both apps speak the same 11 languages. */
object Strings {
    val languages = listOf(
        "en" to "English", "de" to "Deutsch", "tr" to "Türkçe", "fr" to "Français", "it" to "Italiano", "es" to "Español",
        "pt" to "Português", "nl" to "Nederlands", "pl" to "Polski", "ru" to "Русский", "ar" to "العربية",
    )

    private val cache = HashMap<String, Map<String, String>>()
    private val english: Map<String, String> by lazy { load("values") }
    var language by mutableStateOf("en")

    fun init(saved: String) {
        language = saved.ifBlank { Locale.getDefault().language.takeIf { code -> languages.any { it.first == code } } ?: "en" }
    }

    private fun load(dir: String): Map<String, String> = cache.getOrPut(dir) {
        val stream = Strings::class.java.getResourceAsStream("/strings/$dir/strings.xml") ?: return@getOrPut emptyMap()
        stream.use {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
            val nodes = doc.getElementsByTagName("string")
            (0 until nodes.length).associate { i ->
                val e = nodes.item(i) as Element
                e.getAttribute("name") to unescape(e.textContent)
            }
        }
    }

    private fun unescape(raw: String): String =
        raw.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("\\@", "@").replace("\\?", "?")

    fun get(key: String, vararg args: Any): String {
        val dir = if (language == "en") "values" else "values-$language"
        val text = load(dir)[key] ?: english[key] ?: return key
        return if (args.isEmpty()) text.replace("%%", "%") else runCatching { String.format(text, *args) }.getOrDefault(text)
    }
}

@Composable
fun t(key: String, vararg args: Any): String {
    val language = Strings.language
    return remember(key, language, args.toList()) { Strings.get(key, *args) }
}
