package com.kutlusoy.proiptv.engine

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RingBuffer(file: File, val capacity: Long, private val clock: () -> Long = System::currentTimeMillis) : Closeable {
    private val raf = RandomAccessFile(file, "rw").also { it.setLength(capacity) }
    private val channel = raf.channel
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val marks = ArrayDeque<LongArray>()
    private var lastMark = 0L

    @Volatile
    var end: Long = 0
        private set

    @Volatile
    var closed = false
        private set

    val start: Long get() = maxOf(0L, end - capacity)

    fun write(data: ByteArray, off: Int, len: Int) {
        if (closed || len <= 0) return
        var done = 0
        val base = end
        while (done < len) {
            val pos = (base + done) % capacity
            val chunk = minOf((len - done).toLong(), capacity - pos).toInt()
            channel.write(ByteBuffer.wrap(data, off + done, chunk), pos)
            done += chunk
        }
        lock.withLock {
            end = base + len
            val now = clock()
            if (now - lastMark >= MARK_INTERVAL_MS || marks.isEmpty()) {
                marks.addLast(longArrayOf(now, end))
                lastMark = now
            }
            val s = start
            while (marks.size > 1 && marks[1][1] <= s) marks.removeFirst()
            changed.signalAll()
        }
    }

    fun read(pos: Long, dst: ByteArray, off: Int, len: Int, timeoutMs: Long): Int {
        var nanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        lock.withLock {
            while (pos >= end) {
                if (closed) return END
                if (nanos <= 0) return TIMEOUT
                nanos = changed.awaitNanos(nanos)
            }
            if (pos < start) return OVERRUN
        }
        val available = end - pos
        val want = minOf(len.toLong(), available).toInt()
        var done = 0
        while (done < want) {
            val p = (pos + done) % capacity
            val chunk = minOf((want - done).toLong(), capacity - p).toInt()
            val n = channel.read(ByteBuffer.wrap(dst, off + done, chunk), p)
            if (n <= 0) break
            done += n
        }
        return if (pos < start) OVERRUN else done
    }

    fun offsetAtTime(timeMs: Long): Long = lock.withLock {
        val s = start
        var found = s
        var lo = 0
        var hi = marks.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (marks[mid][0] <= timeMs) {
                found = marks[mid][1]
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        found.coerceIn(s, end)
    }

    fun timeAtOffset(offset: Long): Long = lock.withLock {
        for (m in marks) if (m[1] >= offset) return@withLock m[0]
        clock()
    }

    fun oldestTime(): Long = lock.withLock { marks.firstOrNull()?.get(0) ?: clock() }

    override fun close() {
        lock.withLock {
            closed = true
            changed.signalAll()
        }
        runCatching { raf.close() }
    }

    companion object {
        const val END = -1
        const val TIMEOUT = 0
        const val OVERRUN = -2
        private const val MARK_INTERVAL_MS = 250L
    }
}
