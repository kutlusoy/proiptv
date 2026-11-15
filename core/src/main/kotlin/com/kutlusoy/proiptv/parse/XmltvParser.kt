package com.kutlusoy.proiptv.parse

import java.io.InputStream
import java.util.Calendar
import java.util.TimeZone
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class XmltvProgram(val channel: String, val start: Long, val end: Long, val title: String, val description: String?)

object XmltvParser {
    fun parseTime(value: String): Long? {
        val t = value.trim()
        if (t.length < 12) return null
        val digits = t.substring(0, minOf(14, t.length))
        if (!digits.all { it.isDigit() }) return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(
            digits.substring(0, 4).toInt(),
            digits.substring(4, 6).toInt() - 1,
            digits.substring(6, 8).toInt(),
            digits.substring(8, 10).toInt(),
            digits.substring(10, 12).toInt(),
            if (digits.length >= 14) digits.substring(12, 14).toInt() else 0,
        )
        var millis = cal.timeInMillis
        val zone = t.substring(digits.length).trim()
        if (zone.length >= 5 && (zone[0] == '+' || zone[0] == '-') && zone.substring(1, 5).all { it.isDigit() }) {
            val offset = (zone.substring(1, 3).toInt() * 60 + zone.substring(3, 5).toInt()) * 60_000L
            millis += if (zone[0] == '+') -offset else offset
        }
        return millis / 1000
    }

    fun parse(input: InputStream, wanted: (String) -> Boolean, windowStart: Long, windowEnd: Long): Sequence<XmltvProgram> = sequence {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, null)
        var channel: String? = null
        var start = 0L
        var stop = 0L
        var title: String? = null
        var desc: String? = null
        var inProgramme = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "programme" -> {
                        channel = parser.getAttributeValue(null, "channel")
                        start = parseTime(parser.getAttributeValue(null, "start") ?: "") ?: 0L
                        stop = parseTime(parser.getAttributeValue(null, "stop") ?: "") ?: 0L
                        title = null
                        desc = null
                        inProgramme = channel != null && start > 0 && stop > start &&
                            stop > windowStart && start < windowEnd && wanted(channel)
                    }
                    "title" -> if (inProgramme && title == null) title = parser.nextText()
                    "desc" -> if (inProgramme && desc == null) desc = parser.nextText()
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "programme") {
                if (inProgramme) yield(XmltvProgram(channel!!, start, stop, title?.trim().orEmpty().decodeEntities(), desc?.trim()?.decodeEntities()))
                inProgramme = false
            }
            event = parser.next()
        }
    }
}
