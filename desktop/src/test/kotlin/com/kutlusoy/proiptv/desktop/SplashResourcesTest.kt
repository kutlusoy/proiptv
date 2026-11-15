package com.kutlusoy.proiptv.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashResourcesTest {
    @Test
    fun theIntroDrawsEveryFrameFromTheSharedPicturesWithoutAnError() {
        val scene = ImageComposeScene(640, 360, Density(1f)) { SplashScreen { } }
        try {
            scene.render(0)
            for (ms in listOf(500L, 2_000L, 3_500L, 5_800L)) {
                val image = scene.render(ms * 1_000_000L)
                assertTrue(image.width == 640 && image.height == 360)
            }
        } finally {
            scene.close()
        }
    }
}
