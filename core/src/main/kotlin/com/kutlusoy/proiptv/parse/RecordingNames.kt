package com.kutlusoy.proiptv.parse

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** File names of recordings: language, channel, show and date, for example `DE-ORF 1-Tatort-2026.12.23.ts`. */
object RecordingNames {
    private val FORBIDDEN = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]")
    private const val PART_MAX = 60

    private fun clean(text: String): String =
        text.decodeEntities().replace(FORBIDDEN, " ").replace(Regex("\\s{2,}"), " ").trim().trim('.', ' ', '-').take(PART_MAX).trim().trim('.', ' ', '-')

    /** The show of a title that was built as "Channel: Show", or the title itself when it is no channel name. Empty when it is only the channel. */
    fun programOf(title: String, channel: String): String {
        val t = title.trim()
        if (t.equals(channel.trim(), ignoreCase = true)) return ""
        return t.removePrefix("${channel.trim()}:").trim()
    }

    /** [taken] tells whether a name is used already. Then the time, and after that a counter, makes the name unique. */
    fun fileName(channelRaw: String, program: String?, atMs: Long, taken: (String) -> Boolean): String {
        val language = TitleParser.country(channelRaw.decodeEntities().trim())
        val channel = clean(TitleParser.parse(channelRaw).title)
        val show = clean(program.orEmpty())
        val date = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(atMs))
        val time = SimpleDateFormat("HHmm", Locale.US).format(Date(atMs))
        val head = listOf(language, channel, show).filter { it.isNotEmpty() }.joinToString("-").ifEmpty { "Recording" }
        val first = "$head-$date.ts"
        if (!taken(first)) return first
        val timed = "$head-$date-$time.ts"
        if (!taken(timed)) return timed
        var n = 2
        while (taken("$head-$date-$time-$n.ts")) n++
        return "$head-$date-$time-$n.ts"
    }
}
