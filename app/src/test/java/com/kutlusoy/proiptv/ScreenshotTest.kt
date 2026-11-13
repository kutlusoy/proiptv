package com.kutlusoy.proiptv

import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.AccountType
import com.kutlusoy.proiptv.data.Category
import com.kutlusoy.proiptv.data.Favorite
import com.kutlusoy.proiptv.data.FavoriteList
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.Program
import com.kutlusoy.proiptv.data.Progress
import com.kutlusoy.proiptv.data.RecStatus
import com.kutlusoy.proiptv.data.Recording
import com.github.takahirom.roborazzi.captureRoboImage
import com.kutlusoy.proiptv.ui.MainActivity
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "de-w411dp-h891dp-port-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var server: MockWebServer
    private val app get() = ApplicationProvider.getApplicationContext<ProIptvApp>()

    private fun img(name: String) = "file://" + File("src/test/resources/screens/$name").absolutePath
    private fun str(@StringRes id: Int) = app.getString(id)

    private val movieTitles = listOf("Der letzte Zug", "Nachtwind", "Roter Horizont", "Stille Wasser", "Eisland", "Das Erbe", "Funkenflug", "Mondlicht", "Schattenjagd", "Goldener Herbst", "Blaue Stunde", "Kaltes Feuer")
    private val seriesTitles = listOf("Hafenlichter", "Die Anwälte", "Nordwind", "Kommissar Falk", "Sommerfrost", "Zeitsprung")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    "action=get_vod_info" in path -> MockResponse().setBody(
                        """{"info":{"movie_image":"${img("p0.png")}","plot":"Als ein Nachtzug im Schneesturm strandet, muss eine Gruppe Fremder bis zum Morgen zusammenhalten. Doch jemand an Bord kennt das Geheimnis der Strecke.","cast":"Mara Voss, Jonas Keller, Lea Brandt","director":"Tim Arnold","genre":"Drama, Thriller","releasedate":"2026-03-01","duration_secs":6838,"rating":"7.4","backdrop_path":["${img("b0.png")}"]}}""",
                    )
                    "action=get_series_info" in path -> {
                        fun ep(id: Int, season: Int, n: Int, title: String, poster: Int) =
                            """{"id":"$id","episode_num":$n,"title":"$title","season":$season,"container_extension":"mp4","info":{"movie_image":"${img("p$poster.png")}","plot":"Eine neue Spur führt das Team in den Norden. Nicht alle Beteiligten sagen die Wahrheit.","duration_secs":2700}}"""
                        MockResponse().setBody(
                            """{"info":{"plot":"In einer Hafenstadt verschwindet ein Fischkutter. Die Ermittlerin Anna Falk deckt ein Netz aus alten Schulden und neuen Lügen auf.","cover":"${img("p1.png")}","genre":"Krimi, Drama","releaseDate":"2025-09-12","rating":"8.1","cast":"Nina Roth, Paul Sander","backdrop_path":["${img("b1.png")}"]},
                            "episodes":{"1":[${ep(101, 1, 1, "Ablegen", 2)},${ep(102, 1, 2, "Nebel", 3)},${ep(103, 1, 3, "Strömung", 4)},${ep(104, 1, 4, "Ebbe", 5)}],
                            "2":[${ep(201, 2, 1, "Neue Flut", 6)},${ep(202, 2, 2, "Altlasten", 7)}]}}""",
                        )
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        seed()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun seed() = runBlocking {
        val db = app.repo.db
        val now = System.currentTimeMillis()
        val account = db.accounts().insert(
            Account(
                name = "Wohnzimmer", type = AccountType.XTREAM, url = server.url("").toString().trimEnd('/'), username = "demo",
                status = "Active", expiry = now / 1000 + 315 * 86400L, maxConnections = 2, activeConnections = 1,
                lastSync = now - 5 * 60_000, epgSync = now / 1000 - 1800,
            ),
        )
        db.accounts().insert(
            Account(
                name = "Familie (M3U)", type = AccountType.M3U, url = "http://example.invalid/list.m3u",
                lastSync = now - 3_600_000, epgSync = now / 1000 - 3600, ord = 1,
            ),
        )
        val liveCats = listOf("1" to "Nachrichten", "2" to "Sport", "3" to "Kino")
        db.items().insertCategories(liveCats.mapIndexed { i, c -> Category(accountId = account, kind = Kind.LIVE, remoteId = c.first, name = c.second, ord = i) })
        val channels = listOf("Erstes Programm HD", "Nachrichten 24", "Sport Live 1", "Sport Live 2", "Kino Premiere", "Dokukanal", "Kinderwelt", "Musik TV", "Regionalfenster", "Wetter Panorama")
        db.items().upsert(
            channels.mapIndexed { i, n ->
                Item(accountId = account, kind = Kind.LIVE, remoteId = "${10 + i}", name = n, categoryRemoteId = "${1 + i % 3}", logo = img("l${i % 6}.png"), epgId = "ch$i", ord = i, archiveDays = if (i < 3) 3 else 0)
            },
        )
        val nowSecs = now / 1000
        val shows = listOf("Tagesschau Spezial", "Das Magazin", "Bundesliga Konferenz", "Der Kommissar", "Filmklassiker", "Planet Erde", "Kleine Helden", "Live Konzert", "Regio Journal", "Wetter")
        db.programs().insertAll(
            channels.indices.flatMap { i ->
                listOf(
                    Program(accountId = account, epgId = "ch$i", start = nowSecs - 1500 - i * 200, end = nowSecs + 1200 + i * 100, title = shows[i], description = null, gen = 1),
                    Program(accountId = account, epgId = "ch$i", start = nowSecs + 1200 + i * 100, end = nowSecs + 4 * 86400, title = "Danach: " + shows[(i + 3) % shows.size], description = null, gen = 1),
                )
            },
        )
        val movieCats = listOf("10" to "Neu 2026", "11" to "Drama", "12" to "Komödie")
        db.items().insertCategories(movieCats.mapIndexed { i, c -> Category(accountId = account, kind = Kind.MOVIE, remoteId = c.first, name = c.second, ord = i) })
        db.items().upsert(
            movieTitles.mapIndexed { i, t ->
                Item(accountId = account, kind = Kind.MOVIE, remoteId = "${50 + i}", name = t, categoryRemoteId = "${10 + i % 3}", logo = img("p$i.png"), ext = "mp4", rating = "7.${i % 9}", ord = i)
            },
        )
        val seriesCats = listOf("20" to "Krimi", "21" to "Drama")
        db.items().insertCategories(seriesCats.mapIndexed { i, c -> Category(accountId = account, kind = Kind.SERIES, remoteId = c.first, name = c.second, ord = i) })
        db.items().upsert(
            seriesTitles.mapIndexed { i, t ->
                Item(accountId = account, kind = Kind.SERIES, remoteId = "${70 + i}", name = t, categoryRemoteId = "${20 + i % 2}", logo = img("p${(i + 3) % 12}.png"), ord = i)
            },
        )
        val list = db.favorites().insertList(FavoriteList(name = "Favoriten"))
        listOf(Triple(Kind.LIVE, "10", "Erstes Programm HD"), Triple(Kind.LIVE, "12", "Sport Live 1"), Triple(Kind.LIVE, "14", "Kino Premiere"), Triple(Kind.MOVIE, "50", "Der letzte Zug"), Triple(Kind.SERIES, "70", "Hafenlichter"))
            .forEachIndexed { i, f -> db.favorites().add(Favorite(list, account, f.first, f.second, f.third, (i + 1) * 1000.0)) }
        db.progress().save(Progress(account, Kind.EPISODE, "101", 0, 2_700_000, now - 86_400_000, watched = true))
        db.progress().save(Progress(account, Kind.EPISODE, "102", 0, 2_700_000, now - 80_000_000, watched = true))
        db.progress().save(Progress(account, Kind.EPISODE, "103", 1_200_000, 2_700_000, now - 60_000_000))
        db.progress().save(Progress(account, Kind.MOVIE, "51", 2_400_000, 6_800_000, now - 3_600_000))
        db.recordings().insert(Recording(accountId = account, title = "Sport Live 1: Bundesliga", startedAt = now - 7_200_000, endedAt = now - 3_600_000, path = "x", bytes = 1_300_000_000L, status = RecStatus.DONE, channelRemoteId = "12"))
        db.recordings().insert(Recording(accountId = account, title = "Kino Premiere: Nachtwind", startedAt = now + 7_200_000, plannedStart = now + 7_200_000, plannedEnd = now + 14_400_000, path = "", status = RecStatus.SCHEDULED, channelRemoteId = "14"))
        db.recordings().insert(Recording(accountId = account, title = "Dokukanal: Planet Erde", startedAt = now - 86_400_000, endedAt = now - 86_000_000, path = "", status = RecStatus.MISSED, note = "budget", channelRemoteId = "15"))
    }

    private fun settle() {
        repeat(8) {
            compose.waitForIdle()
            Thread.sleep(150)
        }
    }

    private fun shot(name: String) {
        settle()
        val dir = File("build/screenshots").apply { mkdirs() }
        compose.onRoot().captureRoboImage(File(dir, "$name.png").path)
    }

    private fun openMenu() {
        compose.onNodeWithContentDescription(str(R.string.nav_menu)).performClick()
        settle()
    }

    private fun goTo(@StringRes id: Int) {
        openMenu()
        compose.onNodeWithText(str(id)).performClick()
        settle()
    }

    @Test
    fun screens() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        settle()
        shot("01-live")

        openMenu()
        shot("02-menu")
        compose.onNodeWithText(str(R.string.nav_movies)).performClick()
        shot("03-filme")
        compose.onAllNodesWithText(movieTitles[0]).onFirst().performClick()
        shot("04-film-info")
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()

        goTo(R.string.nav_series)
        shot("05-serien")
        compose.onAllNodesWithText(seriesTitles[0]).onFirst().performClick()
        settle()
        shot("06-serie-info")
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()

        goTo(R.string.nav_accounts)
        shot("07-konten")
        compose.onNodeWithText("Wohnzimmer").performClick()
        shot("08-konten-offen")

        goTo(R.string.nav_recordings)
        shot("09-aufnahmen")

        goTo(R.string.nav_settings)
        shot("10-einstellungen")

        goTo(R.string.nav_live)
        compose.onNodeWithText(str(R.string.live_all)).performClick()
        settle()
        compose.onNodeWithText(str(R.string.fav_title)).performClick()
        shot("11-favoriten")
        scenario.close()
    }
}
