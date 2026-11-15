package com.kutlusoy.proiptv.net

import java.net.URI
import java.net.URLDecoder

data class XtreamLogin(val server: String, val username: String?, val password: String?)

object XtreamUrl {
    private val tails = listOf("/player_api.php", "/get.php", "/xmltv.php", "/panel_api.php", "/c/", "/c")

    fun normalizeServer(input: String): String {
        var s = input.trim()
        if (s.isEmpty()) return s
        if (!s.contains("://")) s = "http://$s"
        val q = s.indexOf('?')
        if (q >= 0) s = s.substring(0, q)
        s = s.trimEnd('/')
        for (tail in tails) {
            val t = tail.trimEnd('/')
            if (s.endsWith(t)) {
                s = s.removeSuffix(t)
                break
            }
        }
        return s.trimEnd('/')
    }

    fun parse(input: String): XtreamLogin {
        val server = normalizeServer(input)
        val query = try {
            URI(if (input.contains("://")) input.trim() else "http://${input.trim()}").rawQuery
        } catch (e: Exception) {
            null
        }
        val params = query?.split('&')?.mapNotNull {
            val i = it.indexOf('=')
            if (i < 0) null else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }?.toMap().orEmpty()
        return XtreamLogin(server, params["username"], params["password"])
    }
}
