package com.kutlusoy.proiptv.desktop

import com.kutlusoy.proiptv.update.ReleaseParser
import java.io.File
import java.util.Date
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

    @Test
    fun guideIsLoadedForTheChannelsOfAnAccountAndFindsNowAndNext() {
        val server = MockWebServer()
        val fmt = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        fun t(offsetMin: Int) = fmt.format(Date(System.currentTimeMillis() + offsetMin * 60_000L))
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse = when {
                request.path!!.contains("list.m3u") -> MockResponse().setBody("#EXTM3U url-tvg=\"${server.url("/guide.xml")}\"\n#EXTINF:-1 tvg-id=\"One.DE\" group-title=\"News\",Erstes\nhttp://s/1.ts\n")
                request.path!!.contains("guide.xml") -> MockResponse().setBody(
                    """<?xml version="1.0"?><tv>
                    <programme start="${t(-30)}" stop="${t(30)}" channel="one.de"><title>Tagesschau &amp;amp; Wetter</title></programme>
                    <programme start="${t(30)}" stop="${t(90)}" channel="one.de"><title>Film</title></programme>
                    <programme start="${t(-30)}" stop="${t(30)}" channel="other"><title>Ignored</title></programme></tv>""",
                )
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        try {
            val store = Store(tempDir())
            store.update { it.copy(accounts = listOf(AccountDto(id = "m", name = "List", type = "m3u", url = server.url("/list.m3u").toString()))) }
            val library = Library(store)
            val data = runBlocking { library.load() }
            val guide = runBlocking { library.loadGuide(data.entries(Kind.LIVE)) }
            val channel = data.entries(Kind.LIVE).single()
            val (now, next) = guide.nowNext(channel.accountId, channel.epgId, System.currentTimeMillis() / 1000)
            assertEquals("Tagesschau & Wetter", now?.title)
            assertEquals("Film", next?.title)
            assertEquals(2, guide.programs.values.sumOf { it.size })
            assertTrue(guide.updated.containsKey("m"))
            assertEquals(1, data.status["m"]!!.channels)
        } finally {
            server.shutdown()
        }
    }

    private fun ep(id: String, n: Int) = com.kutlusoy.proiptv.net.XtreamEpisode(id, 1, n, "E$n", "mkv", null, null)

    @Test
    fun progressSurvivesARestartAndWatchedClearsThePosition() {
        val dir = tempDir()
        val store = ProgressStore(dir)
        store.save(ProgressDto("a", 1, "7", positionMs = 60_000, durationMs = 100_000, updated = 1))
        assertEquals(60_000L, ProgressStore(dir).get("a", 1, "7")?.positionMs)
        store.setWatched("a", 1, listOf("7"), true)
        val again = ProgressStore(dir).get("a", 1, "7")
        assertEquals(true, again?.watched)
        assertEquals(0L, again?.positionMs)
    }

    @Test
    fun seriesOffersTheLastStartedEpisodeElseTheFirstUnwatched() {
        val eps = listOf(ep("1", 1), ep("2", 2), ep("3", 3))
        val none = SeriesProgress.next(eps) { null }
        assertEquals("1", none?.episode?.id)
        assertEquals(false, none?.continuing)
        val watchedFirst = SeriesProgress.next(eps) { if (it == "1") ProgressDto("a", 3, "1", watched = true) else null }
        assertEquals("2", watchedFirst?.episode?.id)
        assertEquals(true, watchedFirst?.continuing)
        val started = SeriesProgress.next(eps) { if (it == "2") ProgressDto("a", 3, "2", positionMs = 30_000, updated = 5) else null }
        assertEquals("2", started?.episode?.id)
        assertEquals(true, started?.continuing)
    }
}
