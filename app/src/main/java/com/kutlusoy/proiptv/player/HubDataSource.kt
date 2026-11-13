package com.kutlusoy.proiptv.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.kutlusoy.proiptv.engine.HubReader
import com.kutlusoy.proiptv.engine.StreamHub
import java.io.IOException
import java.io.InterruptedIOException

@UnstableApi
class HubDataSource(private val hub: StreamHub, private val startOffset: Long) : BaseDataSource(false) {
    private var reader: HubReader? = null
    private var uri: Uri? = null
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        reader = hub.reader(startOffset + dataSpec.position)
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val r = reader ?: throw IOException("data source is not open")
        var waited = 0L
        while (true) {
            val n = try {
                r.read(buffer, offset, length, POLL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException()
            }
            if (n > 0) {
                bytesTransferred(n)
                return n
            }
            if (n < 0) return C.RESULT_END_OF_INPUT
            waited += POLL_MS
            if (waited >= STALL_MS) throw IOException("stream stalled")
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        reader = null
        uri = null
    }

    class Factory(private val hub: StreamHub, private val startOffset: Long) : DataSource.Factory {
        override fun createDataSource(): DataSource = HubDataSource(hub, startOffset)
    }

    private companion object {
        const val POLL_MS = 1000L
        const val STALL_MS = 30_000L
    }
}
