package com.kutlusoy.proiptv.parse

data class TitleInfo(val title: String, val country: String, val year: Int)

object TitleParser {
    private val ALIASES = mapOf("UK" to "GB", "USA" to "US")
    private val CODES: Set<String> = (
        "AD AE AF AL AM AO AR AT AU AZ BA BD BE BG BH BR BY CA CH CL CN CO CY CZ DE DK DZ EE EG ES FI FR GB GE GR HK HR HU " +
            "ID IE IL IN IQ IR IS IT JO JP KE KR KW KZ LB LT LU LV LY MA MD ME MK MT MX MY NG NL NO NZ OM PE PH PK PL PS PT QA " +
            "RO RS RU SA SE SG SI SK SY TH TN TR TW UA US UY VE VN XK ZA EN LAT"
        ).split(' ').toSet()
    private val PREFIX = Regex("""^[\s|\[(]*([A-Za-z]{2,3})\s*(?:[-–_]?\s*[|\])]+|:|\s[-–]\s|\s*[-–_]\s)\s*""")
    private val YEAR_BRACKET = Regex("""[(\[]\s*((?:19|20)\d{2})\s*[)\]]""")
    private val YEAR_TRAIL = Regex("""(?:^|\s)[-–]?\s*((?:19|20)\d{2})\s*$""")
    private const val MAX_YEAR = 2030

    fun country(raw: String): String {
        val m = PREFIX.find(raw) ?: return ""
        val token = m.groupValues[1]
        val bracketed = raw.take(m.range.last + 1).any { it == '|' || it == '[' || it == '(' || it == ']' || it == ')' || it == ':' }
        if (!bracketed && token != token.uppercase()) return ""
        val code = ALIASES[token.uppercase()] ?: token.uppercase()
        return if (code in CODES) code else ""
    }

    fun parse(raw: String): TitleInfo {
        var rest = raw.trim()
        val country = country(rest)
        if (country.isNotEmpty()) rest = PREFIX.replaceFirst(rest, "")
        var year = 0
        val bracket = YEAR_BRACKET.find(rest)
        if (bracket != null && bracket.groupValues[1].toInt() <= MAX_YEAR) {
            year = bracket.groupValues[1].toInt()
            rest = rest.removeRange(bracket.range)
        } else {
            val trail = YEAR_TRAIL.find(rest)
            if (trail != null && trail.range.first > 0 && trail.groupValues[1].toInt() <= MAX_YEAR) {
                year = trail.groupValues[1].toInt()
                rest = rest.removeRange(trail.range)
            }
        }
        val title = rest.replace(Regex("""\s{2,}"""), " ").trim().trim('-', '–', '|', ':', ' ')
        return TitleInfo(title.ifEmpty { raw.trim() }, country, year)
    }

    fun parse(raw: String, group: String): TitleInfo {
        val info = parse(raw)
        return if (info.country.isEmpty()) info.copy(country = country(group)) else info
    }
}
