package com.kutlusoy.proiptv

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsTest {
    private val entry = Regex("<string name=\"([^\"]+)\"([^>]*)>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
    private val placeholder = Regex("%\\d+\\$[a-z]")

    private fun load(folder: String): Map<String, String> =
        entry.findAll(File("src/main/res/$folder/strings.xml").readText())
            .filterNot { it.groupValues[2].contains("translatable=\"false\"") }
            .associate { it.groupValues[1] to it.groupValues[3] }

    private fun languageFolders(): List<String> =
        File("src/main/res").listFiles { f -> Regex("values-[a-z]{2}").matches(f.name) }!!
            .map { it.name }.sorted()

    @Test
    fun allTierOneLanguagesArePresent() {
        val expected = setOf("de", "tr", "fr", "it", "es", "pt", "nl", "pl", "ru", "ar").map { "values-$it" }.toSet()
        assertEquals(expected, languageFolders().toSet())
    }

    @Test
    fun namedLanguagesAreCompleteAndOthersNeverBreakPlaceholders() {
        val english = load("values")
        assertTrue(english.isNotEmpty())
        val complete = setOf("values-de", "values-tr", "values-fr", "values-it", "values-es")
        for (folder in languageFolders()) {
            val other = load(folder)
            if (folder in complete) assertEquals("$folder has other keys", english.keys, other.keys)
            else assertTrue("$folder has unknown keys", english.keys.containsAll(other.keys))
            for ((key, text) in other) {
                val expected = placeholder.findAll(english.getValue(key)).map { it.value }.sorted().toList()
                val actual = placeholder.findAll(text).map { it.value }.sorted().toList()
                assertEquals("$folder placeholders of $key", expected, actual)
            }
        }
    }
}
