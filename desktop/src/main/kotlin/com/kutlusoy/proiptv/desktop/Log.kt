package com.kutlusoy.proiptv.desktop

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/** A small log file in the settings folder (log.txt), so problems of the installed app can be seen. Cut to the last 200 KB at startup. */
object Log {
    private val file by lazy { File(Store.defaultDir(), "log.txt").also { it.parentFile?.mkdirs() } }
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    @Synchronized
    fun write(message: String) {
        runCatching { file.appendText("${format.format(Date())}  $message\n") }
    }

    fun trim() {
        runCatching {
            if (file.exists() && file.length() > 200_000) file.writeText(file.readText().takeLast(100_000))
        }
    }
}
