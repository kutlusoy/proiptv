package com.kutlusoy.proiptv.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads over HTTPS only and refuses a file whose SHA-256 does not match. */
class UpdateDownloader(private val client: OkHttpClient, private val requireHttps: Boolean = true) {

    fun fetchText(url: String, accept: String? = null, maxBytes: Int = 2_000_000): String {
        open(url, accept).use { response ->
            val out = ByteArrayOutputStream()
            val input = response.body?.byteStream() ?: throw UpdateException(UpdateException.Code.NETWORK, "Empty answer")
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > maxBytes) throw UpdateException(UpdateException.Code.TOO_LARGE)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }

    /** Stores the file only when it is complete and its checksum is right. */
    fun download(url: String, target: File, expectedSha256: String, maxBytes: Long, onProgress: (Int) -> Unit = {}) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        try {
            open(url, null).use { response ->
                val body = response.body ?: throw UpdateException(UpdateException.Code.NETWORK, "Empty answer")
                val length = body.contentLength()
                if (length > maxBytes) throw UpdateException(UpdateException.Code.TOO_LARGE)
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > maxBytes) throw UpdateException(UpdateException.Code.TOO_LARGE)
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            if (length > 0) {
                                val percent = (total * 100 / length).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
                if (length > 0 && total != length) throw UpdateException(UpdateException.Code.NETWORK, "Incomplete download")
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expectedSha256, ignoreCase = true)) throw UpdateException(UpdateException.Code.CHECKSUM_MISMATCH)
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) throw UpdateException(UpdateException.Code.STORAGE)
            }
        } catch (e: UpdateException) {
            throw e
        } catch (e: IOException) {
            throw UpdateException(if (e.message?.contains("ENOSPC") == true) UpdateException.Code.STORAGE else UpdateException.Code.NETWORK, e.message)
        } finally {
            if (part.exists()) part.delete()
        }
    }

    private fun open(url: String, accept: String?): okhttp3.Response {
        if (requireHttps && !url.startsWith("https://")) throw UpdateException(UpdateException.Code.INSECURE_URL, url)
        val request = Request.Builder().url(url).header("User-Agent", "ProIPTV").apply { if (accept != null) header("Accept", accept) }.build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw UpdateException(UpdateException.Code.NETWORK, e.message)
        }
        if (requireHttps && !response.request.url.isHttps) {
            response.close()
            throw UpdateException(UpdateException.Code.INSECURE_URL, response.request.url.toString())
        }
        when (response.code) {
            200 -> return response
            404 -> { response.close(); throw UpdateException(UpdateException.Code.NOT_FOUND) }
            else -> { val code = response.code; response.close(); throw UpdateException(UpdateException.Code.NETWORK, "HTTP $code") }
        }
    }
}
