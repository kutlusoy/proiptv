package com.kutlusoy.proiptv.desktop

import com.kutlusoy.proiptv.update.ReleaseParser
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopTest {
    private fun tempDir() = File.createTempFile("proiptv", "").also { it.delete(); it.mkdirs() }

    @Test
    fun configSurvivesARestartAndPasswordsAreNotStoredAsPlainText() {
        val dir = tempDir()
        val store = Store(dir)
        store.update { it.copy(accounts = listOf(AccountDto(id = "a", name = "Home", type = "xtream", url = "http://x", username = "u", password = Secrets.protect("secret"))), language = "de") }
        val reread = Store(dir).config
        assertEquals("de", reread.language)
        assertEquals("secret", Secrets.unprotect(reread.accounts.single().password))
        assertTrue(!File(dir, "config.json").readText().contains("secret"))
    }

    @Test
    fun brokenConfigFallsBackToDefaults() {
        val dir = tempDir()
        File(dir, "config.json").writeText("{not json")
        assertEquals(0, Store(dir).config.accounts.size)
    }

    @Test
    fun windowsInstallerIsFoundInTheRelease() {
        val latest = """{"tag_name":"v0.2.0","draft":false,"prerelease":false,"assets":[
            {"name":"ProIPTV-0.2.0.apk","browser_download_url":"https://e/a.apk","size":1},
            {"name":"ProIPTV-0.2.0-windows-x64-setup.exe","browser_download_url":"https://e/w.exe","size":2},
            {"name":"SHA256SUMS.txt","browser_download_url":"https://e/s","size":3}]}"""
        val windows = ReleaseParser.parseLatest(latest, ReleaseParser.WINDOWS_NAME)!!
        assertEquals("ProIPTV-0.2.0-windows-x64-setup.exe", windows.apkName)
        assertEquals("ProIPTV-0.2.0.apk", ReleaseParser.parseLatest(latest)!!.apkName)
        val tests = """[{"tag_name":"test-7","draft":false,"prerelease":true,"assets":[
            {"name":"ProIPTV-0.0.1-test7-abc1234.apk","browser_download_url":"https://e/a.apk","size":1},
            {"name":"ProIPTV-0.0.1-test7-abc1234-windows-x64-setup.exe","browser_download_url":"https://e/w.exe","size":2}]}]"""
        assertEquals("https://e/w.exe", ReleaseParser.parseNewestTest(tests, ReleaseParser.WINDOWS_NAME)!!.apkUrl)
    }

    @Test
    fun everyTextKeyUsedByTheDesktopAppExistsInTheEnglishTexts() {
        val english = Strings::class.java.getResourceAsStream("/strings/values/strings.xml")!!.bufferedReader().readText()
        val used = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
            .flatMap { Regex("""\b(?:t|Strings\.get)\("([a-z0-9_]+)"""").findAll(it.readText()).map { m -> m.groupValues[1] } }
            .toSet() + Tab.values().map { it.label }
        val missing = used.filter { !english.contains("name=\"$it\"") }
        assertTrue("missing texts: $missing", missing.isEmpty())
    }

    @Test
    fun textsFallBackToEnglishAndFillInValues() {
        Strings.language = "de"
        assertEquals("Konten", Strings.get("nav_accounts"))
        assertTrue(Strings.get("update_up_to_date", "1.2.3").contains("1.2.3"))
        Strings.language = "en"
    }

    @Test
    fun m3uAccountLoadsChannelsAndGroups() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:-1 group-title=\"News\" tvg-logo=\"http://l/1.png\",DE: Erstes\nhttp://s/1.ts\n#EXTINF:-1 group-title=\"Kino\",Film (1999)\nhttp://s/m.mp4\n"))
        server.start()
        try {
            val store = Store(tempDir())
            store.update { it.copy(accounts = listOf(AccountDto(id = "m", name = "List", type = "m3u", url = server.url("/list.m3u").toString()))) }
            val data = runBlocking { Library(store).load() }
            assertEquals(1, data.entries(Kind.LIVE).size)
            assertEquals(1, data.entries(Kind.MOVIE).size)
            assertEquals(listOf("News"), data.categories(Kind.LIVE).map { it.name })
            val live = data.entries(Kind.LIVE).single()
            assertNotNull(Library(store).playable(live))
            assertNull(data.errors["m"])
        } finally {
            server.shutdown()
        }
    }
}
