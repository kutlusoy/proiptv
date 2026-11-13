package com.kutlusoy.proiptv

import android.content.Intent
import com.kutlusoy.proiptv.ui.MainActivity
import com.kutlusoy.proiptv.ui.SplashActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ProIptvApp::class)
class StartupSmokeTest {
    @Test
    fun splashOpensTheMainScreen() {
        val controller = Robolectric.buildActivity(SplashActivity::class.java).setup()
        controller.get().onKeyDown(23, null)
        val next = shadowOf(controller.get()).nextStartedActivity
        assertNotNull(next)
        assertEquals(MainActivity::class.java.name, next.component?.className)
    }

    @Test
    fun mainScreenStartsWithoutAccounts() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, Intent()).setup()
        assertNotNull(controller.get().window.decorView)
        controller.pause().stop().destroy()
    }
}
