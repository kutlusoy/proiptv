package com.kutlusoy.proiptv.net

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object StreamUrls {
    fun live(server: String, user: String, pass: String, id: String, format: String): String =
        "$server/live/$user/$pass/$id.$format"

    fun movie(server: String, user: String, pass: String, id: String, ext: String): String =
        "$server/movie/$user/$pass/$id.$ext"

    fun episode(server: String, user: String, pass: String, id: String, ext: String): String =
        "$server/series/$user/$pass/$id.$ext"

    fun liveFormat(setting: String, allowed: String?): String = when {
        setting == "ts" || setting == "m3u8" -> setting
        allowed.isNullOrBlank() -> "ts"
        allowed.split(',').contains("ts") -> "ts"
        else -> "m3u8"
    }

    fun xtreamCatchup(server: String, user: String, pass: String, id: String, startSecs: Long, minutes: Int): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return "$server/timeshift/$user/$pass/$minutes/${fmt.format(Date(startSecs * 1000))}/$id.ts"
    }

    fun m3uCatchup(base: String?, source: String?, startSecs: Long, endSecs: Long, nowSecs: Long): String? {
        if (base == null) return null
        fun fill(template: String): String = template
            .replace("\${start}", startSecs.toString()).replace("{utc}", startSecs.toString())
            .replace("\${end}", endSecs.toString()).replace("{utcend}", endSecs.toString())
            .replace("\${timestamp}", nowSecs.toString()).replace("{lutc}", nowSecs.toString())
            .replace("\${duration}", (endSecs - startSecs).toString()).replace("{duration}", (endSecs - startSecs).toString())
            .replace("\${offset}", (nowSecs - startSecs).toString())
        return when {
            source != null && source.contains("://") -> fill(source)
            source != null -> base + fill(source)
            else -> base + (if (base.contains('?')) "&" else "?") + "utc=$startSecs&lutc=$nowSecs"
        }
    }
}
