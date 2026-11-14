package com.kutlusoy.proiptv.remote

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.security.MessageDigest

class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: ByteArray,
)

/** The small part of HTTP that the setup page needs. */
object RemoteHttp {
    private const val MAX_LINE = 8192
    private const val MAX_HEADERS = 64
    const val MAX_BODY = 64 * 1024

    fun readRequest(input: InputStream): HttpRequest? {
        val requestLine = readLine(input) ?: return null
        val parts = requestLine.split(' ')
        if (parts.size < 2) return null

        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: return null
            if (line.isEmpty()) break
            if (headers.size >= MAX_HEADERS) throw IOException("Too many headers")
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }

        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length < 0 || length > MAX_BODY) throw IOException("Body too large")
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(body, read, length - read)
            if (count < 0) throw IOException("Unexpected end of the request")
            read += count
        }

        val target = parts[1]
        return HttpRequest(parts[0].uppercase(), target.substringBefore('?'), parseQuery(target.substringAfter('?', "")), headers, body)
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (buffer.size() == 0) null else buffer.toString(Charsets.UTF_8.name())
            if (byte == '\n'.code) break
            if (byte != '\r'.code) buffer.write(byte)
            if (buffer.size() > MAX_LINE) throw IOException("Line too long")
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    fun parseQuery(raw: String): Map<String, String> =
        raw.split('&').filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }

    fun response(status: Int, contentType: String, body: ByteArray): ByteArray {
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Error"
        }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Connection: close\r\n\r\n"
        return head.toByteArray(Charsets.UTF_8) + body
    }

    fun tokenMatches(expected: String, provided: String?): Boolean =
        provided != null && MessageDigest.isEqual(expected.toByteArray(), provided.toByteArray())
}
