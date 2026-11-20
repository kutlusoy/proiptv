package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.remote.BackupRouter
import com.kutlusoy.proiptv.remote.RemoteSetupServer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupTransferTest {
    private lateinit var server: RemoteSetupServer
    private var port = 0
    private val token = "tok123"
    private val restored = mutableListOf<Pair<String, String?>>()

    @Before
    fun setUp() {
        val page = File("src/main/assets/remote_backup.html").readBytes()
        val router = BackupRouter(
            token, { page }, { password -> """{"backup":"${password ?: "none"}"}""" },
            { text, password -> restored += text to password; if (text == "bad") "invalid" else if (text == "locked" && password == null) "password_required" else null },
            { "proiptv-backup-test.json" },
        )
        server = RemoteSetupServer(router, maxBody = 1024 * 1024)
        port = server.start(18300..18400)
    }

    @After
    fun tearDown() = server.stop()

    private data class Reply(val code: Int, val body: String, val disposition: String?)

    private fun call(method: String, path: String, body: String? = null): Reply {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        if (body != null) {
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        return Reply(code, text, c.getHeaderField("Content-Disposition"))
    }

    @Test
    fun everyRequestNeedsTheToken() {
        assertEquals(403, call("GET", "/").code)
        assertEquals(403, call("POST", "/api/backup/export?t=wrong", "{}").code)
        val page = call("GET", "/?t=$token")
        assertEquals(200, page.code)
        assertTrue(page.body.contains("api/backup/export"))
    }

    @Test
    fun theBackupComesAsAFile() {
        val reply = call("POST", "/api/backup/export?t=$token", """{"password":"pw"}""")
        assertEquals(200, reply.code)
        assertEquals("""{"backup":"pw"}""", reply.body)
        assertEquals("attachment; filename=\"proiptv-backup-test.json\"", reply.disposition)
        assertEquals("""{"backup":"none"}""", call("POST", "/api/backup/export?t=$token", """{}""").body)
    }

    @Test
    fun aBackupFromThePhoneIsRestoredOrRefusedWithAReason() {
        assertTrue(call("POST", "/api/backup/restore?t=$token", """{"text":"fine"}""").body.contains("\"ok\":true"))
        assertTrue(call("POST", "/api/backup/restore?t=$token", """{"text":"bad"}""").body.contains("invalid"))
        assertTrue(call("POST", "/api/backup/restore?t=$token", """{"text":"locked"}""").body.contains("password_required"))
        assertTrue(call("POST", "/api/backup/restore?t=$token", """{"text":"locked","password":"x"}""").body.contains("\"ok\":true"))
        assertEquals(listOf("fine" to null, "bad" to null, "locked" to null, "locked" to "x"), restored)
    }

    @Test
    fun aBackupLargerThanTheSetupLimitIsAccepted() {
        val big = "x".repeat(200_000)
        assertTrue(call("POST", "/api/backup/restore?t=$token", """{"text":"$big"}""").body.contains("\"ok\":true"))
    }
}
