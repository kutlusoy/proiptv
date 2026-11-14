package com.kutlusoy.proiptv.desktop

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/** A small log file in the settings folder (log.txt), so problems of the installed app can be seen. Cut to the last 100 KB when it passes 200 KB, at startup and while the program runs. The player log (vlc.log) starts empty with every start and only holds warnings and errors. */
object Log {
    private val file by lazy { File(Store.defaultDir(), "log.txt").also { it.parentFile?.mkdirs() } }
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    private var writes = 0

    @Synchronized
    fun write(message: String) {
        runCatching { file.appendText("${format.format(Date())}  $message\n") }
        if (++writes % 50 == 0) trim()
    }

    fun trim() {
        runCatching {
            if (file.exists() && file.length() > 200_000) file.writeText(file.readText().takeLast(100_000))
        }
    }

    /** The player writes vlc.log itself. When a very long session makes it big, it is emptied (the program may keep it open, so a failure is ignored). */
    fun trimPlayerLog() {
        runCatching {
            val f = File(Store.defaultDir(), "vlc.log")
            if (f.exists() && f.length() > 2_000_000) java.io.RandomAccessFile(f, "rw").use { it.setLength(0) }
        }
    }
}
