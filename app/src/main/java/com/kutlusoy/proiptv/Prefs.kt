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
}
