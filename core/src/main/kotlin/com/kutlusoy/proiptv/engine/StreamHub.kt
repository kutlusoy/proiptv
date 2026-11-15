package com.kutlusoy.proiptv.engine

import java.io.Closeable
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class HubState { CONNECTING, LIVE, RECONNECTING, FAILED, CLOSED }

class StreamHub(
    val key: String,
    val accountId: Long,
    val title: String,
    val buffer: RingBuffer,
    private val open: suspend () -> InputStream,
    private val scope: CoroutineScope,
    private val maxAttempts: Int = 30,
    private val backoffMs: (Int) -> Long = { minOf(500L * (1L shl minOf(it, 5)), 10_000L) },
) : Closeable {
    private val _state = MutableStateFlow(HubState.CONNECTING)
    val state: StateFlow<HubState> = _state
    private var job: Job? = null

    @Volatile
    private var current: InputStream? = null

    @Volatile
    var lastError: String? = null
        private set

    fun start() {
        job = scope.launch(Dispatchers.IO) { run() }
    }

    private suspend fun run() {
        var attempt = 0
        val chunk = ByteArray(CHUNK)
        while (scope.isActive && !buffer.closed) {
            try {
                val input = open()
                current = input
                var gotData = false
                input.use {
                    while (!buffer.closed) {
                        val n = it.read(chunk)
                        if (n < 0) break
                        if (n > 0) {
                            if (!gotData) {
                                gotData = true
                                attempt = 0
                                _state.value = HubState.LIVE
                            }
                            buffer.write(chunk, 0, n)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            } finally {
                current = null
            }
            if (buffer.closed) break
            attempt++
            if (attempt > maxAttempts) {
                _state.value = HubState.FAILED
                buffer.close()
                return
            }
            _state.value = HubState.RECONNECTING
            padToPacket()
            delay(backoffMs(attempt))
        }
    }

    private fun padToPacket() {
        val rest = (PACKET - buffer.end % PACKET) % PACKET
        if (rest > 0) buffer.write(ByteArray(rest.toInt()) { 0xFF.toByte() }, 0, rest.toInt())
    }

    fun reader(from: Long = buffer.end): HubReader = HubReader(buffer, from)

    override fun close() {
        _state.value = HubState.CLOSED
        buffer.close()
        runCatching { current?.close() }
        job?.cancel()
    }

    private companion object {
        const val CHUNK = 64 * 1024
        const val PACKET = 188L
    }
}

class HubReader(private val buffer: RingBuffer, var position: Long) {
    var skipped = 0L
        private set

    fun read(dst: ByteArray, off: Int, len: Int, timeoutMs: Long): Int {
        while (true) {
            val n = buffer.read(position, dst, off, len, timeoutMs)
            when {
                n > 0 -> {
                    position += n
                    return n
                }
                n == RingBuffer.OVERRUN -> {
                    val s = buffer.start
                    skipped += s - position
                    position = s
                }
                else -> return n
            }
        }
    }
}
