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

    /** A name with the day and the start time; [taken] tells whether a name is used already, then a counter makes it unique. */
    fun fileName(channelRaw: String, program: String?, atMs: Long, taken: (String) -> Boolean): String {
        val language = TitleParser.country(channelRaw.decodeEntities().trim())
        val channel = clean(TitleParser.parse(channelRaw).title)
        val show = clean(program.orEmpty())
        val head = listOf(language, channel, show).filter { it.isNotEmpty() }.joinToString("-").ifEmpty { "Recording" }
        return unique("$head-${SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date(atMs))}-${hhmm(atMs)}", taken)
    }

    /** The name of a finished recording: the stop time is added after the start time, so several recordings of a day have different names. */
    fun withStop(fileName: String, stopMs: Long, taken: (String) -> Boolean): String =
        unique(fileName.removeSuffix(EXT) + "-" + hhmm(stopMs), taken)

    private fun hhmm(ms: Long) = SimpleDateFormat("HHmm", Locale.US).format(Date(ms))

    private fun unique(base: String, taken: (String) -> Boolean): String {
        if (!taken("$base$EXT")) return "$base$EXT"
        var n = 2
        while (taken("$base-$n$EXT")) n++
        return "$base-$n$EXT"
    }

    private const val EXT = ".ts"
}
