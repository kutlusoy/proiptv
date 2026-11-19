package com.kutlusoy.proiptv.parse

/** The genre of a title as providers write it: several names in one text ("Horror / Action / Thriller", "Sci-Fi & Fantasy, Drama"). */
object Genres {
    private val separators = Regex("\\s*[,/;|]\\s*")

    fun split(text: String?): List<String> =
        text?.split(separators)?.map { it.trim() }?.filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }?.distinctBy { it.lowercase() }.orEmpty()

    fun has(text: String?, genre: String): Boolean = split(text).any { it.equals(genre, ignoreCase = true) }

    /** The genres of a list of titles with the number of titles each, the biggest first. Names that differ only in case count as one. */
    fun count(texts: Sequence<String?>): List<Pair<String, Int>> {
        val names = HashMap<String, String>()
        val counts = HashMap<String, Int>()
        for (t in texts) for (g in split(t)) {
            val key = g.lowercase()
            names.putIfAbsent(key, g)
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { names.getValue(it.key) to it.value }
    }

    /** How a title keeps its genres in the database: the names between bars ("|Horror|Action|"), so a name can be searched as a whole. Empty without genre. */
    fun encode(text: String?): String = split(text).let { if (it.isEmpty()) "" else it.joinToString("|", prefix = "|", postfix = "|") }

    fun decode(stored: String): List<String> = stored.split('|').filter { it.isNotEmpty() }

    /** The same as [count] for rows of the database: the stored text of a genre column with the number of titles that have exactly this text. */
    fun countStored(rows: List<Pair<String, Int>>): List<Pair<String, Int>> {
        val names = HashMap<String, String>()
        val counts = HashMap<String, Int>()
        for ((stored, n) in rows) for (g in decode(stored)) {
            val key = g.lowercase()
            names.putIfAbsent(key, g)
            counts[key] = (counts[key] ?: 0) + n
        }
        return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { names.getValue(it.key) to it.value }
    }
}
