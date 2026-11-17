package com.kutlusoy.proiptv.parse

/** Search with several words: every word has to occur somewhere in the text, in any order ("tengri fr" finds "Tengri · FR FRANCE"). */
object SearchMatch {
    private val SPACES = Regex("\\s+")

    fun words(query: String): List<String> = query.trim().split(SPACES).filter { it.isNotEmpty() }

    fun matches(text: String, query: String): Boolean = matches(text, words(query))

    /** The search words in four slots for a database query: the first three words, then the rest as one. Empty slots match everything. */
    fun four(query: String): List<String> {
        val w = words(query)
        return listOf(w.getOrElse(0) { "" }, w.getOrElse(1) { "" }, w.getOrElse(2) { "" }, w.drop(3).joinToString(" "))
    }

    fun matches(text: String, words: List<String>): Boolean = words.all { text.contains(it, ignoreCase = true) }
}
