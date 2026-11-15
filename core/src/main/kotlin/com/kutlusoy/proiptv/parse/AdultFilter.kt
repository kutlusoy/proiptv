package com.kutlusoy.proiptv.parse

/** Recognises adult categories and channels by their names. */
object AdultFilter {
    private val WORDS = Regex("""(^|[^\p{L}\p{N}])(xxx|adults?|porn\w*|erotic\w*|18\+|sex)($|[^\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    fun isAdult(vararg names: String?): Boolean = names.any { it != null && WORDS.containsMatchIn(it) }
}
