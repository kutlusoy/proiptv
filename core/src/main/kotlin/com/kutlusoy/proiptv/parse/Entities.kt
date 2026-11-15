package com.kutlusoy.proiptv.parse

private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

private val NAMED = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "auml" to "ä", "ouml" to "ö", "uuml" to "ü", "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü", "szlig" to "ß",
    "eacute" to "é", "egrave" to "è", "agrave" to "à", "ccedil" to "ç", "ndash" to "–", "mdash" to "—", "hellip" to "…",
)

/** Providers often escape text twice, so "&amp;amp;" arrives as "&amp;". Turns such entities into the real character. */
fun String.decodeEntities(): String {
    if (indexOf('&') < 0) return this
    var text = this
    repeat(2) {
        val next = ENTITY.replace(text) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") -> body.drop(2).toIntOrNull(16)?.let { cp -> codePointText(cp) }
                body.startsWith("#") -> body.drop(1).toIntOrNull()?.let { cp -> codePointText(cp) }
                else -> NAMED[body]
            } ?: m.value
        }
        if (next == text) return text
        text = next
    }
    return text
}

private fun codePointText(cp: Int): String? = if (cp in 1..0x10FFFF && cp !in 0xD800..0xDFFF) String(Character.toChars(cp)) else null
