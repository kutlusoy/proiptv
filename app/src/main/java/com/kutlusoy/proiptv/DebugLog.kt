package com.kutlusoy.proiptv

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/**
 * A small local log file for debug builds only (nothing is written in the release app, nothing is ever sent anywhere). It helps to find
 * problems on a device without a computer: player errors, sync and download failures. Cut to the last 100 KB when it passes 200 KB.
 * Addresses, user names and passwords are blacked out before anything is written.
 */
object DebugLog {
    private var file: File? = null
    private var writes = 0
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    fun init(context: Context) {
        if (!BuildConfig.DEBUG) return
        file = File(context.filesDir, "debug-log.txt")
        write("ProIPTV ${BuildConfig.VERSION_NAME} started")
    }

    /** Blacks out what must not appear in a log: passwords and user names in addresses, and addresses of the provider altogether. */
    fun redact(text: String): String = text
        .replace(Regex("""(?i)(password|username|pass|user)=[^&\s"']+"""), "$1=***")
        .replace(Regex("""(?i)https?://[^\s"']+"""), "<address>")

    @Synchronized
    fun write(message: String) {
        val f = file ?: return
        runCatching {
            f.appendText("${format.format(Date())}  ${redact(message)}\n")
            if (++writes % 50 == 0 && f.length() > 200_000) f.writeText(f.readText().takeLast(100_000))
        }
    }

    /** The last lines, for the settings screen of debug builds. */
    fun tail(lines: Int = 120): String = runCatching { file?.readLines()?.takeLast(lines)?.joinToString("\n").orEmpty() }.getOrDefault("")
}
