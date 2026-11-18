package com.kutlusoy.proiptv.desktop

import androidx.compose.runtime.mutableStateListOf
import su.litvak.chromecast.api.v2.ChromeCast
import su.litvak.chromecast.api.v2.ChromeCasts
import su.litvak.chromecast.api.v2.ChromeCastsListener
import su.litvak.chromecast.api.v2.Media

/** What the TV needs to know about a stream: the content type tells the receiver how to read it. */
object CastMedia {
    fun contentType(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".m3u8") -> "application/x-mpegURL"
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".avi") -> "video/x-msvideo"
            else -> "video/mp2t"
        }
    }
}

/** Google Cast: finds the TVs in the network and sends them the address of the stream, the TV plays it itself (the PC only steers). */
class CastClient {
    class Device(val name: String, val cast: ChromeCast)

    val devices = mutableStateListOf<Device>()
    private var searching = false
    private var connected: ChromeCast? = null

    private val listener = object : ChromeCastsListener {
        override fun newChromeCastDiscovered(chromeCast: ChromeCast) {
            val name = chromeCast.title ?: chromeCast.name
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                if (devices.none { it.cast.name == chromeCast.name }) devices.add(Device(name, chromeCast))
            }
        }

        override fun chromeCastRemoved(chromeCast: ChromeCast) {
            androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot { devices.removeAll { it.cast.name == chromeCast.name } }
        }
    }

    @Synchronized
    fun startSearch() {
        if (searching) return
        searching = true
        runCatching {
            ChromeCasts.registerListener(listener)
            ChromeCasts.startDiscovery()
        }.onFailure { Log.write("cast search: ${it.message}"); searching = false }
    }

    @Synchronized
    fun stopSearch() {
        if (!searching) return
        searching = false
        runCatching { ChromeCasts.unregisterListener(listener); ChromeCasts.stopDiscovery() }
    }

    /** Starts the stream on the TV. Throws when the TV does not answer. */
    @Synchronized
    fun play(device: Device, url: String, title: String, live: Boolean) {
        val cast = device.cast
        if (connected != null && connected !== cast) close()
        if (!cast.isConnected) cast.connect()
        connected = cast
        cast.launchApp("CC1AD845")
        load(url, title, live)
    }

    /** Another address on the TV that is already connected (zapping while casting). */
    @Synchronized
    fun load(url: String, title: String, live: Boolean) {
        val cast = connected ?: return
        val media = Media(url, CastMedia.contentType(url), null, if (live) Media.StreamType.LIVE else Media.StreamType.BUFFERED, mapOf(Media.METADATA_TITLE to title), null, null, null)
        cast.load(media)
    }

    @Synchronized
    fun close() {
        val cast = connected ?: return
        connected = null
        runCatching { cast.stopApp() }
        runCatching { cast.disconnect() }
    }

    val isOpen: Boolean @Synchronized get() = connected != null
}
