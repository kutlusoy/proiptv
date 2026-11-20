package com.kutlusoy.proiptv

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var timeshift: Boolean
        get() = sp.getBoolean("timeshift", true)
        set(value) = sp.edit().putBoolean("timeshift", value).apply()

    var bufferMb: Int
        get() = sp.getInt("buffer_mb", 1024)
        set(value) = sp.edit().putInt("buffer_mb", value).apply()

    var uiMode: Int
        get() = sp.getInt("ui_mode", 0)
        set(value) = sp.edit().putInt("ui_mode", value).apply()

    /** Counts the changes of how the lists are filtered; a lower number than the app knows means the stored lists must be loaded again once. */
    var filterSync: Int
        get() = sp.getInt("filter_sync", 0)
        set(value) = sp.edit().putInt("filter_sync", value).apply()

    var hideAdult: Boolean
        get() = sp.getBoolean("hide_adult", true)
        set(value) = sp.edit().putBoolean("hide_adult", value).apply()

    /** Series: the next episode of the season starts when an episode has ended. */
    var autoplayNext: Boolean
        get() = sp.getBoolean("autoplay_next", true)
        set(value) = sp.edit().putBoolean("autoplay_next", value).apply()

    /** The folder of the app on the volume that was chosen for downloads, recordings and backups; null: the standard places. */
    var storageRoot: String?
        get() = sp.getString("storage_root", null)
        set(value) = sp.edit().apply { if (value == null) remove("storage_root") else putString("storage_root", value) }.apply()

    /** The picture of the player: brightness -100..100, zoom 100..300 percent, position -100..100 of the part that is cut off, size mode of the picture. */
    var videoBrightness: Int
        get() = sp.getInt("video_brightness", 0)
        set(value) = sp.edit().putInt("video_brightness", value).apply()
    var videoZoom: Int
        get() = sp.getInt("video_zoom", 100)
        set(value) = sp.edit().putInt("video_zoom", value).apply()
    var videoPanX: Int
        get() = sp.getInt("video_pan_x", 0)
        set(value) = sp.edit().putInt("video_pan_x", value).apply()
    var videoPanY: Int
        get() = sp.getInt("video_pan_y", 0)
        set(value) = sp.edit().putInt("video_pan_y", value).apply()
    var videoResize: Int
        get() = sp.getInt("video_resize", 0)
        set(value) = sp.edit().putInt("video_resize", value).apply()

    /** A folder that was chosen with the file manager (content address): finished downloads and recordings are moved there, backups are saved there. */
    var storageTree: String?
        get() = sp.getString("storage_tree", null)
        set(value) = sp.edit().apply { if (value == null) remove("storage_tree") else putString("storage_tree", value) }.apply()

    var tmdbKey: String
        get() = sp.getString("tmdb_key", "") ?: ""
        set(value) = sp.edit().putString("tmdb_key", value.trim()).apply()

    var lastTab: String
        get() = sp.getString("last_tab", "") ?: ""
        set(value) = sp.edit().putString("last_tab", value).apply()

    fun browseAccount(kind: Int): Long? = sp.getLong("acc_$kind", -1L).takeIf { it >= 0 }

    fun browseCategory(kind: Int, account: Long?): String? =
        sp.getString("cat_$kind", null)?.takeIf { sp.getLong("cat_acc_$kind", -1L) == (account ?: -1L) }

    /** The category that was open last in an area, with its account: for the rows of the start page. */
    fun lastCategory(kind: Int): Pair<Long, String>? {
        val account = sp.getLong("cat_acc_$kind", -1L).takeIf { it >= 0 } ?: return null
        val category = sp.getString("cat_$kind", null)?.takeIf { it.isNotEmpty() } ?: return null
        return account to category
    }

    fun browseFav(kind: Int): Boolean = sp.getBoolean("fav_$kind", false)

    fun browseFavList(kind: Int): Long? = sp.getLong("favlist_$kind", -1L).takeIf { it >= 0 }

    fun saveBrowse(kind: Int, accountSel: Long?, effective: Long?, category: String?, fav: Boolean, favList: Long?) {
        sp.edit()
            .putLong("acc_$kind", accountSel ?: -1L)
            .putString("cat_$kind", category)
            .putLong("cat_acc_$kind", effective ?: -1L)
            .putBoolean("fav_$kind", fav)
            .putLong("favlist_$kind", favList ?: -1L)
            .apply()
    }

    var autoUpdate: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(value) = sp.edit().putBoolean("auto_update", value).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("update_check", 0L)
        set(value) = sp.edit().putLong("update_check", value).apply()

    var lastUpdateNotifyDay: Long
        get() = sp.getLong("update_notify_day", -1L)
        set(value) = sp.edit().putLong("update_notify_day", value).apply()
}
