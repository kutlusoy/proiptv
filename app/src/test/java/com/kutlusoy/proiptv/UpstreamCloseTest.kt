package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.net.Upstream
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class UpstreamCloseTest {
    /** A server that sends an endless live stream and notes when the client has cut the connection. */
    private fun endlessServer(cut: AtomicBoolean): ServerSocket {
        val server = ServerSocket(0)
        Thread {
            runCatching {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val buffer = ByteArray(4096)
                    input.read(buffer)
                    val out = socket.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nConnection: close\r\n\r\n".toByteArray())
                    val chunk = ByteArray(188 * 7) { 0x47 }
                    while (true) {
                        out.write(chunk)
                        out.flush()
                        Thread.sleep(20)
                    }
                }
            }
            cut.set(true)
        }.start()
        return server
    }

    @Test
    fun closingTheStreamCutsTheConnectionToTheProviderAtOnce() = runBlocking {
        val cut = AtomicBoolean(false)
        val server = endlessServer(cut)
        try {
            val stream = Upstream.opener("http://127.0.0.1:${server.localPort}/live", null)()
            assertTrue(stream.read(ByteArray(1316)) > 0)
            stream.close()
            val end = System.currentTimeMillis() + 3_000
            while (!cut.get() && System.currentTimeMillis() < end) Thread.sleep(20)
            assertTrue("the provider still sees an open connection", cut.get())
        } finally {
            server.close()
        }
    }

    @Test
    fun leavingAChannelCutsItsConnectionAndTheNextChannelCanTakeTheOnlyOne() = runBlocking {
        val cut = AtomicBoolean(false)
        val server = endlessServer(cut)
        val dir = java.nio.file.Files.createTempDirectory("hubs").toFile()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val manager = com.kutlusoy.proiptv.engine.EngineManager(dir, scope, { 1L shl 20 }, graceMs = 60_000)
        try {
            val lease = manager.acquire(1, "a", "A", 1, Upstream.opener("http://127.0.0.1:${server.localPort}/live", null))
            val reader = lease.hub.reader(0)
            assertTrue(reader.read(ByteArray(188), 0, 188, 3_000) > 0)
            // switching to another channel of the account with one connection: the first one is closed for real
            lease.closeNow()
            val end = System.currentTimeMillis() + 3_000
            while (!cut.get() && System.currentTimeMillis() < end) Thread.sleep(20)
            assertTrue("the provider still sees the first channel", cut.get())
            manager.acquire(1, "b", "B", 1) { java.io.ByteArrayInputStream(ByteArray(0)) }.close()
        } finally {
            manager.closeAll()
            scope.cancel()
            server.close()
        }
    }
}
