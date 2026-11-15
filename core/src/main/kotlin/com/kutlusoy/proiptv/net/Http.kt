package com.kutlusoy.proiptv.net

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import okhttp3.OkHttpClient
import okhttp3.Request

object Http {
    const val DEFAULT_AGENT = "ProIPTV"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun request(url: String, userAgent: String?): Request =
        Request.Builder().url(url).header("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_AGENT).build()
}

class HttpException(val code: Int, message: String) : Exception(message)

fun InputStream.maybeGunzip(): InputStream {
    val buffered = if (this is BufferedInputStream) this else BufferedInputStream(this, 16 * 1024)
    buffered.mark(2)
    val b0 = buffered.read()
    val b1 = buffered.read()
    buffered.reset()
    return if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(buffered, 16 * 1024) else buffered
}
