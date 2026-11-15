package com.kutlusoy.proiptv.engine

import java.io.File

enum class RecordingEnd { STOPPED, TIME_REACHED, STREAM_ENDED, ERROR }

class Recorder(
    private val hub: StreamHub,
    private val file: File,
    private val fromOffset: Long,
    private val stopAtMs: Long?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    var bytes = 0L
        private set

    @Volatile
    private var stop = false

    fun requestStop() {
        stop = true
    }

    fun run(): RecordingEnd {
        val reader = hub.reader(fromOffset)
        val chunk = ByteArray(64 * 1024)
        return try {
            file.parentFile?.mkdirs()
            file.outputStream().buffered(256 * 1024).use { out ->
                var reason = RecordingEnd.STOPPED
                while (true) {
                    if (stop) break
                    if (stopAtMs != null && clock() >= stopAtMs) {
                        reason = RecordingEnd.TIME_REACHED
                        break
                    }
                    val n = reader.read(chunk, 0, chunk.size, 1000)
                    if (n < 0) {
                        reason = RecordingEnd.STREAM_ENDED
                        break
                    }
                    if (n > 0) {
                        out.write(chunk, 0, n)
                        bytes += n
                    }
                }
                reason
            }
        } catch (e: Exception) {
            RecordingEnd.ERROR
        }
    }
}
