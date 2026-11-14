package com.kutlusoy.proiptv.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.awt.Canvas
import java.io.File

/** The small part of the libVLC C interface that the player needs. libVLC is LGPL and is loaded as a separate DLL (never vlcj, which is GPL). */
interface LibVlc : Library {
    fun libvlc_new(argc: Int, argv: Array<String>): Pointer?
    fun libvlc_release(instance: Pointer)
    fun libvlc_errmsg(): String?
    fun libvlc_media_new_location(instance: Pointer, mrl: String): Pointer?
    fun libvlc_media_add_option(media: Pointer, option: String)
    fun libvlc_media_release(media: Pointer)
    fun libvlc_media_player_new(instance: Pointer): Pointer?
    fun libvlc_media_player_release(player: Pointer)
    fun libvlc_media_player_set_media(player: Pointer, media: Pointer)
    fun libvlc_media_player_set_hwnd(player: Pointer, drawable: Pointer?)
    fun libvlc_media_player_set_xwindow(player: Pointer, drawable: Int)
    fun libvlc_media_player_play(player: Pointer): Int
    fun libvlc_media_player_set_pause(player: Pointer, pause: Int)
    fun libvlc_media_player_stop(player: Pointer)
    fun libvlc_media_player_is_playing(player: Pointer): Int
    fun libvlc_media_player_get_time(player: Pointer): Long
    fun libvlc_media_player_set_time(player: Pointer, time: Long)
    fun libvlc_media_player_get_length(player: Pointer): Long
    fun libvlc_media_player_get_state(player: Pointer): Int
    fun libvlc_audio_set_volume(player: Pointer, volume: Int): Int
    fun libvlc_audio_set_mute(player: Pointer, mute: Int)
    fun libvlc_video_set_mouse_input(player: Pointer, on: Int)
    fun libvlc_video_set_key_input(player: Pointer, on: Int)
}

class VlcEngine {
    private var lib: LibVlc? = null
    private var instance: Pointer? = null
    private var player: Pointer? = null
    private var attached: Canvas? = null
    var error: String? = null
        private set

    val available: Boolean get() = player != null

    init {
        try {
            val dir = findVlcDir()
            if (dir != null) {
                NativeLibrary.addSearchPath("vlccore", dir.absolutePath)
                NativeLibrary.addSearchPath("libvlccore", dir.absolutePath)
                NativeLibrary.addSearchPath("vlc", dir.absolutePath)
                NativeLibrary.addSearchPath("libvlc", dir.absolutePath)
            }
            val name = if (System.getProperty("os.name").lowercase().contains("win")) "libvlc" else "vlc"
            val loaded = Native.load(name, LibVlc::class.java)
            val args = arrayOf("--no-video-title-show", "--no-osd", "--no-snapshot-preview", "--network-caching=1500", "--avcodec-hw=any", "--quiet")
            val inst = loaded.libvlc_new(args.size, args)
            if (inst == null) {
                error = loaded.libvlc_errmsg() ?: "libvlc_new failed"
            } else {
                lib = loaded
                instance = inst
                player = loaded.libvlc_media_player_new(inst)
                player?.let {
                    loaded.libvlc_video_set_mouse_input(it, 0)
                    loaded.libvlc_video_set_key_input(it, 0)
                }
            }
        } catch (e: Throwable) {
            error = e.message ?: e.javaClass.simpleName
        }
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

    /** Ties the video output to the canvas. Needs the canvas to be displayable. */
    fun attach(canvas: Canvas) {
        val l = lib ?: return
        val p = player ?: return
        if (attached === canvas || !canvas.isDisplayable) return
        attached = canvas
        if (System.getProperty("os.name").lowercase().contains("win")) l.libvlc_media_player_set_hwnd(p, Native.getComponentPointer(canvas))
        else l.libvlc_media_player_set_xwindow(p, Native.getComponentID(canvas).toInt())
    }

    fun play(item: Playable, volume: Int) {
        val l = lib ?: return
        val i = instance ?: return
        val p = player ?: return
        val media = l.libvlc_media_new_location(i, item.url) ?: run { error = l.libvlc_errmsg(); return }
        item.userAgent?.takeIf { it.isNotBlank() }?.let { l.libvlc_media_add_option(media, ":http-user-agent=$it") }
        l.libvlc_media_player_set_media(p, media)
        l.libvlc_media_release(media)
        l.libvlc_audio_set_volume(p, volume)
        l.libvlc_media_player_play(p)
    }

    fun pause(paused: Boolean) { val l = lib ?: return; player?.let { l.libvlc_media_player_set_pause(it, if (paused) 1 else 0) } }
    fun stop() { val l = lib ?: return; player?.let { l.libvlc_media_player_stop(it) } }
    fun isPlaying(): Boolean { val l = lib ?: return false; return player?.let { l.libvlc_media_player_is_playing(it) == 1 } ?: false }
    fun time(): Long { val l = lib ?: return 0; return player?.let { l.libvlc_media_player_get_time(it) } ?: 0 }
    fun length(): Long { val l = lib ?: return 0; return player?.let { l.libvlc_media_player_get_length(it) } ?: 0 }
    fun seek(ms: Long) { val l = lib ?: return; player?.let { l.libvlc_media_player_set_time(it, ms.coerceAtLeast(0)) } }
    fun setVolume(percent: Int) { val l = lib ?: return; player?.let { l.libvlc_audio_set_volume(it, percent.coerceIn(0, 200)) } }
    fun setMute(mute: Boolean) { val l = lib ?: return; player?.let { l.libvlc_audio_set_mute(it, if (mute) 1 else 0) } }

    fun release() {
        val l = lib ?: return
        player?.let { l.libvlc_media_player_stop(it); l.libvlc_media_player_release(it) }
        instance?.let { l.libvlc_release(it) }
        player = null
        instance = null
    }
}
