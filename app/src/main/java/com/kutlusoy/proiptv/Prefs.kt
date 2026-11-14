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

    var lastTab: String
        get() = sp.getString("last_tab", "") ?: ""
        set(value) = sp.edit().putString("last_tab", value).apply()

    fun browseAccount(kind: Int): Long? = sp.getLong("acc_$kind", -1L).takeIf { it >= 0 }

    fun browseCategory(kind: Int, account: Long?): String? =
        sp.getString("cat_$kind", null)?.takeIf { sp.getLong("cat_acc_$kind", -1L) == (account ?: -1L) }

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
