package com.kutlusoy.proiptv.net

/** What an error answer of a provider means for the user, so the screen can say what to do instead of showing "HTTP 403". */
enum class ProviderProblem { AUTH, LIMIT, BLOCKED, NOT_FOUND, OTHER }

object ProviderProblems {
    private val http = Regex("""HTTP (\d{3})""")

    fun fromHttp(code: Int?): ProviderProblem = when (code) {
        401, 403 -> ProviderProblem.AUTH
        429, 509 -> ProviderProblem.LIMIT
        451 -> ProviderProblem.BLOCKED
        404, 410 -> ProviderProblem.NOT_FOUND
        else -> ProviderProblem.OTHER
    }

    /** The status code in a message like "HTTP 403", or null. */
    fun codeOf(message: String?): Int? = message?.let { http.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    fun fromMessage(message: String?): ProviderProblem = fromHttp(codeOf(message))
}
