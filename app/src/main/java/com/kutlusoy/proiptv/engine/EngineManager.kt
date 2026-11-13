package com.kutlusoy.proiptv.engine

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BudgetExceeded(val limit: Int, val openTitles: List<String>) : Exception("connection limit $limit reached")

class HubLease internal constructor(val hub: StreamHub, private val onRelease: (StreamHub) -> Unit) : Closeable {
    private val released = AtomicBoolean(false)

    override fun close() {
        if (released.compareAndSet(false, true)) onRelease(hub)
    }
}

class EngineManager(
    private val dir: File,
    private val scope: CoroutineScope,
    private val capacity: () -> Long,
    private val graceMs: Long = 10_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val hub: StreamHub, val file: File) {
        var refs = 0
        var idleSince = 0L
    }

    private val lock = Any()
    private val hubs = HashMap<String, Entry>()

    init {
        dir.mkdirs()
        dir.listFiles { f -> f.name.startsWith("hub-") }?.forEach { it.delete() }
    }

    fun acquire(
        accountId: Long,
        channelId: String,
        title: String,
        maxConnections: Int?,
        open: suspend () -> InputStream,
    ): HubLease = synchronized(lock) {
        val key = "$accountId:$channelId"
        hubs[key]?.let {
            it.refs++
            return HubLease(it.hub, ::release)
        }
        if (maxConnections != null && maxConnections > 0) {
            hubs.filterValues { it.hub.accountId == accountId && it.refs == 0 }.keys.toList().forEach { closeEntry(it) }
            val inUse = hubs.values.filter { it.hub.accountId == accountId }
            if (inUse.size >= maxConnections) throw BudgetExceeded(maxConnections, inUse.map { it.hub.title })
        }
        val file = File(dir, "hub-${key.hashCode().toUInt()}-${clock()}.buf")
        val hub = StreamHub(key, accountId, title, RingBuffer(file, capacity()), open, scope)
        val entry = Entry(hub, file).also { it.refs = 1 }
        hubs[key] = entry
        hub.start()
        HubLease(hub, ::release)
    }

    private fun release(hub: StreamHub) {
        val idleAt = synchronized(lock) {
            val entry = hubs[hub.key] ?: return
            entry.refs--
            if (entry.refs > 0) return
            entry.idleSince = clock()
            entry.idleSince
        }
        scope.launch {
            delay(graceMs)
            synchronized(lock) {
                val entry = hubs[hub.key]
                if (entry != null && entry.refs == 0 && entry.idleSince == idleAt) closeEntry(hub.key)
            }
        }
    }

    private fun closeEntry(key: String) {
        val entry = hubs.remove(key) ?: return
        entry.hub.close()
        entry.file.delete()
    }

    fun openKeys(): List<String> = synchronized(lock) { hubs.keys.toList() }

    fun refCount(accountId: Long, channelId: String): Int = synchronized(lock) { hubs["$accountId:$channelId"]?.refs ?: 0 }

    fun closeAll() = synchronized(lock) { hubs.keys.toList().forEach { closeEntry(it) } }
}
