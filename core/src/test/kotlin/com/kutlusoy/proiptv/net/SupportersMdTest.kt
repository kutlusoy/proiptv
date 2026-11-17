package com.kutlusoy.proiptv.net

import com.kutlusoy.proiptv.parse.SupporterSection
import com.kutlusoy.proiptv.parse.SupportersMd
import org.junit.Assert.assertEquals
import org.junit.Test

class SupportersMdTest {
    @Test
    fun sectionsWithBulletsAndComments() {
        val text = """
            <!-- a comment -->
            ## Tester

            * Cengiz, Coşkun
            - Mahmut

            ## Developers
            Somebody
        """.trimIndent()
        assertEquals(
            listOf(SupporterSection("Tester", listOf("Cengiz, Coşkun", "Mahmut")), SupporterSection("Developers", listOf("Somebody"))),
            SupportersMd.parse(text),
        )
    }

    @Test
    fun anEmptyFileHasNoSections() {
        assertEquals(emptyList<SupporterSection>(), SupportersMd.parse("<!-- x -->\n\n## Empty\n"))
    }
}
