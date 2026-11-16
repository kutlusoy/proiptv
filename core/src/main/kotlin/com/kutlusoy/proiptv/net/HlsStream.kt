package com.kutlusoy.proiptv.net

import java.io.IOException
import java.io.InputStream
import java.net.URI

fun interface HlsSource {
    fun open(url: String): InputStream
}

/**
 * Turns a live HLS playlist into one continuous stream of transport stream bytes, so that the stream hub can buffer and record it
 * like a plain TS channel. Starts at the newest segment. Encrypted and fragmented MP4 playlists are not supported.
 */
class HlsStream(
    private val playlistUrl: String,
    private val source: HlsSource,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val now: () -> Long = System::currentTimeMillis,
) : InputStream() {
    @Volatile
    private var closed = false
    private var current: InputStream? = null
    private var mediaUrl: String? = null
    private var nextSeq = -1L
    private var ended = false
    private var pollMs = 1000L
    private val pending = ArrayDeque<Pair<Long, String>>()
    private var lastProgress = now()

    fun prepare() {
        refresh()
    }

    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        return if (n < 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        while (true) {
            if (closed) throw IOException("closed")
            val stream = current ?: openNext() ?: return -1
            val n = stream.read(b, off, len)
            if (n > 0) return n
            runCatching { stream.close() }
            current = null
        }
    }

    private fun openNext(): InputStream? {
        while (!closed) {
            pending.removeFirstOrNull()?.let { (seq, url) ->
                nextSeq = seq + 1
                lastProgress = now()
                return source.open(url).also { current = it }
            }
            if (ended) return null
            refresh()
            if (pending.isEmpty() && !ended) {
                if (now() - lastProgress > STALL_MS) throw IOException("The playlist has no new segments")
                sleep(pollMs)
            }
        }
        throw IOException("closed")
    }

    private fun refresh() {
        var url = mediaUrl ?: playlistUrl
        var text = source.open(url).use { it.readBytes().decodeToString() }
        if (!text.trimStart().startsWith("#EXTM3U")) throw IOException("Not a playlist")
        if (mediaUrl == null && text.contains("#EXT-X-STREAM-INF")) {
            url = bestVariant(url, text) ?: throw IOException("No stream in the playlist")
            mediaUrl = url
            text = source.open(url).use { it.readBytes().decodeToString() }
            if (!text.trimStart().startsWith("#EXTM3U")) throw IOException("Not a playlist")
        } else {
            mediaUrl = url
        }
        parseMedia(url, text)
    }

    private fun bestVariant(base: String, text: String): String? {
        var best: String? = null
        var bestRate = -1L
        val lines = text.lines().map { it.trim() }
        for (i in lines.indices) {
            if (!lines[i].startsWith("#EXT-X-STREAM-INF")) continue
            val rate = Regex("(?<![A-Z-])BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: continue
            if (rate > bestRate) {
                bestRate = rate
                best = resolve(base, uri)
            }
        }
        return best
    }

    private fun parseMedia(base: String, text: String) {
        var mediaSeq = 0L
        val segments = ArrayList<String>()
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> mediaSeq = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                line.startsWith("#EXT-X-TARGETDURATION:") -> pollMs = ((line.substringAfter(':').trim().toLongOrNull() ?: 4L) * 500L).coerceIn(500L, 5000L)
                line.startsWith("#EXT-X-KEY:") && !line.contains("METHOD=NONE") -> throw IOException("Encrypted playlists are not supported")
                line.startsWith("#EXT-X-MAP:") -> throw IOException("Fragmented MP4 playlists are not supported")
                line.startsWith("#EXT-X-ENDLIST") -> ended = true
                line.isNotEmpty() && !line.startsWith("#") -> segments += resolve(base, line)
            }
        }
        if (segments.isEmpty()) return
        val knownEnd = pending.lastOrNull()?.first?.plus(1) ?: nextSeq
        val first = if (knownEnd < 0) mediaSeq + segments.size - 1 else maxOf(knownEnd, mediaSeq)
        for ((i, s) in segments.withIndex()) {
            val seq = mediaSeq + i
            if (seq >= first) pending.addLast(seq to s)
        }
    }

    private fun resolve(base: String, ref: String): String = URI(base).resolve(ref).toString()

    override fun close() {
        closed = true
        runCatching { current?.close() }
    }

    private companion object {
        const val STALL_MS = 60_000L
    }
}
