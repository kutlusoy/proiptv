package com.kutlusoy.proiptv

import com.kutlusoy.proiptv.net.ProviderProblem
import com.kutlusoy.proiptv.net.ProviderProblems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderProblemTest {
    @Test
    fun statusCodesAreMappedToWhatTheUserCanDo() {
        assertEquals(ProviderProblem.AUTH, ProviderProblems.fromHttp(401))
        assertEquals(ProviderProblem.AUTH, ProviderProblems.fromHttp(403))
        assertEquals(ProviderProblem.LIMIT, ProviderProblems.fromHttp(429))
        assertEquals(ProviderProblem.BLOCKED, ProviderProblems.fromHttp(451))
        assertEquals(ProviderProblem.NOT_FOUND, ProviderProblems.fromHttp(404))
        assertEquals(ProviderProblem.OTHER, ProviderProblems.fromHttp(500))
        assertEquals(ProviderProblem.OTHER, ProviderProblems.fromHttp(null))
    }

    @Test
    fun theCodeIsReadFromTheMessageOfTheHub() {
        assertEquals(403, ProviderProblems.codeOf("HTTP 403"))
        assertEquals(ProviderProblem.LIMIT, ProviderProblems.fromMessage("Unexpected: HTTP 429 Too Many"))
        assertNull(ProviderProblems.codeOf("timeout"))
        assertNull(ProviderProblems.codeOf(null))
    }
}
