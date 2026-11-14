package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.remote.RemoteAccount
import com.kutlusoy.proiptv.remote.RemoteRouter
import com.kutlusoy.proiptv.remote.RemoteSetupServer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteSetupTest {
    private val received = mutableListOf<RemoteAccount>()
    private var reject: String? = null
    private lateinit var server: RemoteSetupServer
    private var port = 0
    private val token = "abc123"

    @Before
    fun setUp() {
        val page = File("src/main/assets/remote_setup.html").readBytes()
        server = RemoteSetupServer(RemoteRouter(token, { page }, { draft -> received += draft; reject }))
        port = server.start(18080..18200)
    }

    @After
    fun tearDown() = server.stop()

    private fun call(method: String, path: String, body: String? = null): Pair<Int, String> {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        return code to text
    }

    @Test
    fun theSetupPageNeedsTheTokenFromTheQrCode() {
        assertEquals(403, call("GET", "/").first)
        assertEquals(403, call("GET", "/?t=wrong").first)
        val (code, html) = call("GET", "/?t=$token")
        assertEquals(200, code)
        assertTrue(html.contains("/api/account"))
        assertEquals(403, call("POST", "/api/account", """{"name":"x","url":"http://h"}""").first)
    }

    @Test
    fun anAccountFromThePhoneIsPassedOn() {
        val (code, answer) = call("POST", "/api/account?t=$token", """{"type":"xtream","name":"Home","url":"http://h:8080","username":"u","password":"p"}""")
        assertEquals(200, code)
        assertTrue(answer.contains("\"ok\":true"))
        val a = received.single()
        assertEquals("Home", a.name)
        assertEquals("u", a.username)
        assertEquals("p", a.password)
    }

    @Test
    fun wrongOrIncompleteDataIsRefused() {
        assertEquals(400, call("POST", "/api/account?t=$token", "not json").first)
        assertEquals(400, call("POST", "/api/account?t=$token", """{"type":"xtream","name":"","url":"http://h","username":"u"}""").first)
        assertEquals(400, call("POST", "/api/account?t=$token", """{"type":"ftp","name":"n","url":"http://h"}""").first)
        assertEquals(400, call("POST", "/api/account?t=$token", """{"type":"xtream","name":"n","url":"http://h"}""").first)
        assertTrue(received.isEmpty())
        // credentials inside the address are fine
        assertEquals(200, call("POST", "/api/account?t=$token", """{"type":"xtream","name":"n","url":"http://h/get.php?username=u&password=p"}""").first)
    }

    @Test
    fun aRejectionOfTheProviderIsShownOnThePhone() {
        reject = "HTTP 401"
        val (code, answer) = call("POST", "/api/account?t=$token", """{"type":"xtream","name":"n","url":"http://h","username":"u","password":"bad"}""")
        assertEquals(200, code)
        assertTrue(answer.contains("\"ok\":false"))
        assertTrue(answer.contains("HTTP 401"))
        assertNotNull(received.singleOrNull())
    }

    @Test
    fun everyTextOfThePageExistsInAllLanguages() {
        val html = File("src/main/assets/remote_setup.html").readText()
        val keys = Regex("""(\w+): "[^"]*"""")
        for (lang in listOf("en", "de", "tr", "fr", "it", "es")) {
            val block = Regex("""\n  $lang: \{(.*)\},""").find(html)!!.groupValues[1]
            val names = keys.findAll(block).map { it.groupValues[1] }.toSet()
            assertTrue("$lang is missing ${listOf("hint", "send", "ok", "network").filter { it !in names }}", names.containsAll(listOf("hint", "type", "m3u", "name", "server", "playlist", "user", "pass", "epg", "agent", "send", "ok", "sending", "network", "invalid_link", "name_missing", "url_missing", "username_missing", "login")))
        }
    }
}
