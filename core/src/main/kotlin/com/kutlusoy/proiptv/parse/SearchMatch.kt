package com.kutlusoy.proiptv.parse

/** Search with several words: every word has to occur somewhere in the text, in any order ("tengri fr" finds "Tengri · FR FRANCE"). */
object SearchMatch {
    private val SPACES = Regex("\\s+")

    fun words(query: String): List<String> = query.trim().split(SPACES).filter { it.isNotEmpty() }

    fun matches(text: String, query: String): Boolean = matches(text, words(query))

    fun matches(text: String, words: List<String>): Boolean = words.all { text.contains(it, ignoreCase = true) }
}
