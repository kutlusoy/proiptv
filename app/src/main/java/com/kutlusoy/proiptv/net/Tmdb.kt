package com.kutlusoy.proiptv.net

import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient

/**
 * Plot, cast and more for titles that the provider describes badly (M3U lists have none). Optional: it needs the own API key of the user at
 * themoviedb.org and sends only the cleaned title and the year of the title that is opened. [base] is replaceable for tests.
 */
class Tmdb(
    private val client: OkHttpClient,
    private val key: String,
    private val language: String,
    private val base: String = "https://api.themoviedb.org/3",
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun get(path: String, vararg params: Pair<String, String>): JsonObject? {
        val query = (listOf("api_key" to key, "language" to language) + params).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        client.newCall(Http.request("$base/$path?$query", null)).execute().use { r ->
            if (!r.isSuccessful) return null
            return json.parseToJsonElement(r.body!!.string()) as? JsonObject
        }
    }

    private fun JsonObject.names(key: String, field: String = "name", limit: Int = 100): List<String> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str(field)?.takeIf { s -> s.isNotBlank() } }.take(limit)

    private fun firstId(path: String, title: String, yearKey: String, year: Int): String? {
        val params = buildList {
            add("query" to title)
            if (year > 0) add(yearKey to year.toString())
        }
        val found = get(path, *params.toTypedArray())?.get("results") as? JsonArray
        return (found?.firstOrNull() as? JsonObject)?.str("id")
    }

    private fun trailer(root: JsonObject): String? {
        val list = (root["videos"] as? JsonObject)?.get("results") as? JsonArray ?: return null
        val videos = list.mapNotNull { it as? JsonObject }.filter { it.str("site") == "YouTube" }
        return (videos.firstOrNull { it.str("type") == "Trailer" } ?: videos.firstOrNull())?.str("key")
    }

    private fun image(path: String?, size: String) = path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/$size$it" }

    fun movie(title: String, year: Int): VodInfo? {
        val id = firstId("search/movie", title, "year", year) ?: return null
        val m = get("movie/$id", "append_to_response" to "credits,videos") ?: return null
        val credits = m["credits"] as? JsonObject
        val directors = (credits?.get("crew") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter { it.str("job") == "Director" }.mapNotNull { it.str("name") }
        return VodInfo(
            plot = m.str("overview")?.takeIf { it.isNotBlank() },
            cast = credits?.names("cast", limit = 8)?.joinToString(", ")?.takeIf { it.isNotEmpty() },
            director = directors.joinToString(", ").takeIf { it.isNotEmpty() },
            genre = m.names("genres").joinToString(", ").takeIf { it.isNotEmpty() },
            releaseDate = m.str("release_date")?.takeIf { it.isNotBlank() },
            durationSecs = m.int("runtime")?.takeIf { it > 0 }?.let { it * 60 },
            rating = m.str("vote_average")?.takeIf { it.toDoubleOrNull() ?: 0.0 > 0 },
            cover = image(m.str("poster_path"), "w500"),
            backdrop = image(m.str("backdrop_path"), "w780"),
            trailer = trailer(m),
        )
    }

    fun series(title: String, year: Int): SeriesInfo? {
        val id = firstId("search/tv", title, "first_air_date_year", year) ?: return null
        val s = get("tv/$id", "append_to_response" to "credits,videos") ?: return null
        val credits = s["credits"] as? JsonObject
        return SeriesInfo(
            plot = s.str("overview")?.takeIf { it.isNotBlank() },
            cast = credits?.names("cast", limit = 8)?.joinToString(", ")?.takeIf { it.isNotEmpty() },
            director = s.names("created_by").joinToString(", ").takeIf { it.isNotEmpty() },
            genre = s.names("genres").joinToString(", ").takeIf { it.isNotEmpty() },
            releaseDate = s.str("first_air_date")?.takeIf { it.isNotBlank() },
            rating = s.str("vote_average")?.takeIf { it.toDoubleOrNull() ?: 0.0 > 0 },
            cover = image(s.str("poster_path"), "w500"),
            backdrop = image(s.str("backdrop_path"), "w780"),
            trailer = trailer(s),
        )
    }

    companion object {
        private val movies = ConcurrentHashMap<String, VodInfo>()
        private val shows = ConcurrentHashMap<String, SeriesInfo>()

        /** The answer is kept for the run of the program, so opening a title again does not ask again. */
        fun cachedMovie(tmdb: Tmdb, title: String, year: Int): VodInfo? {
            val k = "${tmdb.language}|$title|$year"
            movies[k]?.let { return it }
            return runCatching { tmdb.movie(title, year) }.getOrNull()?.also { movies[k] = it }
        }

        fun cachedSeries(tmdb: Tmdb, title: String, year: Int): SeriesInfo? {
            val k = "${tmdb.language}|$title|$year"
            shows[k]?.let { return it }
            return runCatching { tmdb.series(title, year) }.getOrNull()?.also { shows[k] = it }
        }
    }
}
