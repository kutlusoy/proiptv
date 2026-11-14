package com.kutlusoy.proiptv.desktop

import com.kutlusoy.proiptv.engine.HubReader
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

fun interface LockCallback : Callback { fun callback(opaque: Pointer?, planes: Pointer?): Pointer? }
fun interface UnlockCallback : Callback { fun callback(opaque: Pointer?, picture: Pointer?, planes: Pointer?) }
fun interface DisplayCallback : Callback { fun callback(opaque: Pointer?, picture: Pointer?) }
fun interface FormatCallback : Callback { fun callback(opaque: Pointer?, chroma: Pointer?, width: Pointer?, height: Pointer?, pitches: Pointer?, lines: Pointer?): Int }
fun interface CleanupCallback : Callback { fun callback(opaque: Pointer?) }
fun interface MediaOpenCallback : Callback { fun callback(opaque: Pointer?, data: Pointer?, size: Pointer?): Int }
fun interface MediaReadCallback : Callback { fun callback(opaque: Pointer?, buffer: Pointer?, length: Long): Long }
fun interface MediaSeekCallback : Callback { fun callback(opaque: Pointer?, offset: Long): Int }
fun interface MediaCloseCallback : Callback { fun callback(opaque: Pointer?) }

/** The small part of the libVLC C interface that the player needs. libVLC is LGPL and is loaded as a separate DLL (never vlcj, which is GPL). */
interface LibVlc : Library {
    fun libvlc_new(argc: Int, argv: Array<String>): Pointer?
    fun libvlc_release(instance: Pointer)
    fun libvlc_errmsg(): String?
    fun libvlc_media_new_location(instance: Pointer, mrl: String): Pointer?
    fun libvlc_media_new_callbacks(instance: Pointer, open: MediaOpenCallback, read: MediaReadCallback, seek: MediaSeekCallback, close: MediaCloseCallback, opaque: Pointer?): Pointer?
    fun libvlc_media_add_option(media: Pointer, option: String)
    fun libvlc_media_release(media: Pointer)
    fun libvlc_media_player_new(instance: Pointer): Pointer?
    fun libvlc_media_player_release(player: Pointer)
    fun libvlc_media_player_set_media(player: Pointer, media: Pointer)
    fun libvlc_video_set_callbacks(player: Pointer, lock: LockCallback, unlock: UnlockCallback, display: DisplayCallback, opaque: Pointer?)
    fun libvlc_video_set_format_callbacks(player: Pointer, setup: FormatCallback, cleanup: CleanupCallback)
    fun libvlc_media_player_play(player: Pointer): Int
    fun libvlc_media_player_set_pause(player: Pointer, pause: Int)
    fun libvlc_media_player_stop(player: Pointer)
    fun libvlc_media_player_is_playing(player: Pointer): Int
    fun libvlc_media_player_get_time(player: Pointer): Long
    fun libvlc_media_player_set_time(player: Pointer, time: Long)
    fun libvlc_media_player_get_length(player: Pointer): Long
    fun libvlc_audio_set_volume(player: Pointer, volume: Int): Int
    fun libvlc_audio_set_mute(player: Pointer, mute: Int)
    fun libvlc_audio_get_track_description(player: Pointer): Pointer?
    fun libvlc_audio_get_track(player: Pointer): Int
    fun libvlc_audio_set_track(player: Pointer, id: Int): Int
    fun libvlc_video_get_spu_description(player: Pointer): Pointer?
    fun libvlc_video_get_spu(player: Pointer): Int
    fun libvlc_video_set_spu(player: Pointer, id: Int): Int
    fun libvlc_track_description_list_release(list: Pointer)
}

/** One decoded picture, BGRA, 4 bytes per pixel. */
class Frame(val width: Int, val height: Int, val pixels: ByteArray, val version: Long)

/**
 * libVLC draws into memory (video callbacks) and the screen shows the pictures as a Compose image.
 * That works without any window handle, so the video is always inside the app window.
 */
class VlcEngine {
    private var lib: LibVlc? = null
    private var instance: Pointer? = null
    private var player: Pointer? = null
    var error: String? = null
        private set

    val available: Boolean get() = player != null

    val frame = AtomicReference<Frame?>(null)
    private val versions = AtomicLong()
    private var buffer: Memory? = null
    private var frameWidth = 0
    private var frameHeight = 0
    private var frameBytes = 0

    // kept in fields so they are not garbage collected while libVLC still calls them
    private val lockCb = LockCallback { _, planes -> safe { planes?.setPointer(0, buffer) }; null }
    private val unlockCb = UnlockCallback { _, _, _ -> }
    private val displayCb = DisplayCallback { _, _ ->
        safe {
            val mem = buffer
            if (mem != null && frameBytes > 0) {
                val bytes = ByteArray(frameBytes)
                mem.read(0, bytes, 0, frameBytes)
                val n = versions.incrementAndGet()
                if (n == 1L || n % 500 == 0L) Log.write("picture $n arrived (${frameWidth}x$frameHeight)")
                frame.set(Frame(frameWidth, frameHeight, bytes, n))
            }
        }
    }
    private val formatCb = FormatCallback { _, chroma, width, height, pitches, lines ->
        try {
            val w = width!!.getInt(0)
            val h = height!!.getInt(0)
            chroma!!.setString(0, "RV32")
            pitches!!.setInt(0, w * 4)
            lines!!.setInt(0, h)
            frameWidth = w
            frameHeight = h
            frameBytes = w * h * 4
            buffer = Memory(frameBytes.toLong() + 64)
            Log.write("video format ${w}x$h")
            1
        } catch (e: Throwable) {
            Log.write("video format callback failed: $e")
            0
        }
    }
    private val cleanupCb = CleanupCallback { }

    @Volatile
    private var streamReader: HubReader? = null

    @Volatile
    private var streamStop = false
    private val mediaOpenCb = MediaOpenCallback { _, data, size ->
        data?.setPointer(0, Pointer.NULL)
        size?.setLong(0, -1L)
        0
    }
    private val mediaReadCb = MediaReadCallback { _, buffer, length ->
        val reader = streamReader
        if (reader == null || buffer == null) 0L else {
            val tmp = ByteArray(minOf(length, 64L * 1024).toInt())
            var result = 0L
            while (!streamStop) {
                val n = reader.read(tmp, 0, tmp.size, 500)
                if (n > 0) { buffer.write(0, tmp, 0, n); result = n.toLong(); break }
                if (n < 0) break
            }
            result
        }
    }
    private val mediaSeekCb = MediaSeekCallback { _, _ -> -1 }
    private val mediaCloseCb = MediaCloseCallback { }

    /** The position of the reader in the buffer, for the time shift display. */
    val readerPosition: Long get() = streamReader?.position ?: 0

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (e: Throwable) { Log.write("video callback failed: $e") }
    }

    init {
        try {
            val dir = findVlcDir()
            Log.write("libVLC folder: ${dir?.absolutePath ?: "not found"}")
            if (dir != null) {
                for (name in listOf("vlccore", "libvlccore", "vlc", "libvlc")) NativeLibrary.addSearchPath(name, dir.absolutePath)
            }
            val name = if (System.getProperty("os.name").lowercase().contains("win")) "libvlc" else "vlc"
            val loaded = Native.load(name, LibVlc::class.java)
            val args = arrayOf("--no-video-title-show", "--no-osd", "--no-snapshot-preview", "--network-caching=1500", "--avcodec-hw=none", "--file-logging", "--logfile=${File(Store.defaultDir(), "vlc.log").also { it.delete() }.absolutePath}", "--log-verbose=2")
            val inst = loaded.libvlc_new(args.size, args)
            if (inst == null) {
                error = loaded.libvlc_errmsg() ?: "libvlc_new failed"
            } else {
                lib = loaded
                instance = inst
                val p = loaded.libvlc_media_player_new(inst)
                player = p
                if (p != null) {
                    loaded.libvlc_video_set_callbacks(p, lockCb, unlockCb, displayCb, null)
                    loaded.libvlc_video_set_format_callbacks(p, formatCb, cleanupCb)
                }
            }
        } catch (e: Throwable) {
            error = e.message ?: e.javaClass.simpleName
        }
        error?.let { Log.write("libVLC could not be started: $it") }
    }

    /** The folder with libvlc.dll: next to the program (installed layout), or the folder named in PROIPTV_VLC_DIR, or a VLC install. */
    private fun findVlcDir(): File? {
        val candidates = mutableListOf<File>()
        System.getenv("PROIPTV_VLC_DIR")?.let { candidates += File(it) }
        ProcessHandle.current().info().command().orElse(null)?.let { exe -> File(exe).parentFile?.let { candidates += File(it, "vlc") } }
        candidates += File(System.getProperty("user.dir"), "vlc")
        System.getenv("ProgramFiles")?.let { candidates += File(it, "VideoLAN/VLC") }
        return candidates.firstOrNull { File(it, "libvlc.dll").exists() || File(it, "libvlc.so").exists() }
    }

    fun play(item: Playable, volume: Int, startMs: Long = 0) {
        val l = lib ?: return
        val i = instance ?: return
        val p = player ?: return
        frame.set(null)
        streamStop = true
        streamReader = null
        val media = l.libvlc_media_new_location(i, item.url) ?: run { error = l.libvlc_errmsg(); return }
        item.userAgent?.takeIf { it.isNotBlank() }?.let { l.libvlc_media_add_option(media, ":http-user-agent=$it") }
        if (startMs > 0) l.libvlc_media_add_option(media, ":start-time=${startMs / 1000.0}")
        l.libvlc_media_player_set_media(p, media)
        l.libvlc_media_release(media)
        l.libvlc_audio_set_volume(p, volume)
        val result = l.libvlc_media_player_play(p)
        Log.write("play ${item.url.substringBefore('?').takeLast(60)} result=$result")
        if (result != 0) Log.write("play failed: ${l.libvlc_errmsg()}")
    }

    /** Plays what a reader delivers (the shared stream buffer): one connection can then show the channel and record it, and the buffer allows to go back in time. */
    fun playReader(reader: HubReader, title: String, volume: Int) {
        val l = lib ?: return
        val i = instance ?: return
        val p = player ?: return
        frame.set(null)
        streamStop = true
        l.libvlc_media_player_stop(p)
        streamStop = false
        streamReader = reader
        val media = l.libvlc_media_new_callbacks(i, mediaOpenCb, mediaReadCb, mediaSeekCb, mediaCloseCb, null) ?: run { error = l.libvlc_errmsg(); return }
        l.libvlc_media_player_set_media(p, media)
        l.libvlc_media_release(media)
        l.libvlc_audio_set_volume(p, volume)
        val result = l.libvlc_media_player_play(p)
        Log.write("play stream $title result=$result")
        if (result != 0) Log.write("play failed: ${l.libvlc_errmsg()}")
    }

    fun pause(paused: Boolean) { val l = lib ?: return; player?.let { l.libvlc_media_player_set_pause(it, if (paused) 1 else 0) } }
    fun stop() {
        val l = lib ?: return
        streamStop = true
        player?.let { l.libvlc_media_player_stop(it) }
        streamReader = null
        frame.set(null)
    }
    fun isPlaying(): Boolean { val l = lib ?: return false; return player?.let { l.libvlc_media_player_is_playing(it) == 1 } ?: false }
    fun time(): Long { val l = lib ?: return 0; return player?.let { l.libvlc_media_player_get_time(it) } ?: 0 }
    fun length(): Long { val l = lib ?: return 0; return player?.let { l.libvlc_media_player_get_length(it) } ?: 0 }
    fun seek(ms: Long) { val l = lib ?: return; player?.let { l.libvlc_media_player_set_time(it, ms.coerceAtLeast(0)) } }
    fun setVolume(percent: Int) { val l = lib ?: return; player?.let { l.libvlc_audio_set_volume(it, percent.coerceIn(0, 200)) } }
    fun setMute(mute: Boolean) { val l = lib ?: return; player?.let { l.libvlc_audio_set_mute(it, if (mute) 1 else 0) } }

    /** An audio or subtitle track: its id for libVLC and its name. */
    class Track(val id: Int, val name: String)

    class Tracks(val audio: List<Track>, val audioSelected: Int, val subtitles: List<Track>, val subtitleSelected: Int)

    private fun readTracks(first: Pointer?): List<Track> {
        val list = ArrayList<Track>()
        var node = first
        val size = Native.POINTER_SIZE
        while (node != null && list.size < 200) {
            list += Track(node.getInt(0), node.getPointer(size.toLong())?.getString(0) ?: "")
            node = node.getPointer(2L * size)
        }
        return list
    }

    fun tracks(): Tracks? {
        val l = lib ?: return null
        val p = player ?: return null
        val audio = l.libvlc_audio_get_track_description(p)
        val subs = l.libvlc_video_get_spu_description(p)
        return try {
            Tracks(readTracks(audio), l.libvlc_audio_get_track(p), readTracks(subs), l.libvlc_video_get_spu(p))
        } finally {
            audio?.let { l.libvlc_track_description_list_release(it) }
            subs?.let { l.libvlc_track_description_list_release(it) }
        }
    }

    fun selectAudio(id: Int) { val l = lib ?: return; player?.let { l.libvlc_audio_set_track(it, id) } }
    fun selectSubtitle(id: Int) { val l = lib ?: return; player?.let { l.libvlc_video_set_spu(it, id) } }

    fun release() {
        val l = lib ?: return
        player?.let { l.libvlc_media_player_stop(it); l.libvlc_media_player_release(it) }
        instance?.let { l.libvlc_release(it) }
        player = null
        instance = null
    }
}
