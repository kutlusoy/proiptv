package com.kutlusoy.proiptv

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kutlusoy.proiptv.data.Account
import com.kutlusoy.proiptv.data.AccountType
import com.kutlusoy.proiptv.data.Item
import com.kutlusoy.proiptv.data.Kind
import com.kutlusoy.proiptv.data.Program
import com.kutlusoy.proiptv.ui.EpgScreen
import com.kutlusoy.proiptv.ui.MainViewModel
import com.kutlusoy.proiptv.ui.ProIptvTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ProIptvApp::class)
class EpgTimerUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingAReminderFromTheGuideDoesNotCrash() {
        val app = ApplicationProvider.getApplicationContext<ProIptvApp>()
        val vm = MainViewModel(app)
        val db = vm.repo.db
        runBlocking {
            val account = db.accounts().insert(Account(name = "A", type = AccountType.XTREAM, url = "http://x", username = "u"))
            db.items().upsert(listOf(Item(accountId = account, kind = Kind.LIVE, remoteId = "1", name = "Erstes", epgId = "one", ord = 0, categoryRemoteId = "n")))
            val now = System.currentTimeMillis() / 1000
            db.programs().insertAll(
                listOf(
                    Program(accountId = account, epgId = "one", start = now - 1800, end = now + 600, title = "Tagesschau", description = null, gen = 1),
                    Program(accountId = account, epgId = "one", start = now + 600, end = now + 5400, title = "Krimi am Abend", description = "x", gen = 1),
                ),
            )
        }
        compose.setContent { ProIptvTheme(false) { EpgScreen(vm) } }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Krimi am Abend").assertExists(); true }.getOrDefault(false) }
        compose.onNodeWithText("Krimi am Abend").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Remind me 1 minute before").performClick()
        compose.waitForIdle()
        Thread.sleep(500)
        compose.waitForIdle()
        assertEquals(1, runBlocking { db.reminders().all().size })
        compose.onNodeWithText("Close").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Timers (1)", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Krimi am Abend  \u00B7  Erstes").assertExists()
    }
}
