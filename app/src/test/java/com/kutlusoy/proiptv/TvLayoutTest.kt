package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.ui.UI_AUTO
import com.kutlusoy.proiptv.ui.UI_TOUCH
import com.kutlusoy.proiptv.ui.UI_TV
import com.kutlusoy.proiptv.ui.useTvLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLayoutTest {
    @Test
    fun automaticFollowsTheDevice() {
        assertTrue(useTvLayout(UI_AUTO, true))
        assertFalse(useTvLayout(UI_AUTO, false))
    }

    @Test
    fun manualChoiceWins() {
        assertTrue(useTvLayout(UI_TV, false))
        assertFalse(useTvLayout(UI_TOUCH, true))
    }
}
