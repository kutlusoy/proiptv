package com.kutlusoy.proiptv.net

import android.util.Base64
import java.io.Closeable
import java.io.InputStream
import java.net.URLEncoder
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import com.kutlusoy.proiptv.parse.decodeEntities
import okhttp3.OkHttpClient
import okhttp3.Response

data class XtreamInfo(
    val status: String?,
    val expiry: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val trial: Boolean,
    val outputFormats: List<String>,
)

data class ShortProgram(val start: Long, val end: Long, val title: String, val description: String?)

data class XtreamEpisode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String,
    val plot: String?,
    val durationSecs: Int?,
    val image: String? = null,
    val rating: String? = null,
)

data class VodInfo(
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val durationSecs: Int?,
    val rating: String?,
    val cover: String?,
    val backdrop: String?,
)

data class SeriesInfo(
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val cover: String?,
    val backdrop: String?,
)

data class SeriesDetail(val info: SeriesInfo?, val episodes: List<XtreamEpisode>)

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
fun JsonObject.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()
fun JsonObject.long(key: String): Long? = str(key)?.toDoubleOrNull()?.toLong()

class JsonStream(private val response: Response, val items: Sequence<JsonObject>) : Closeable {
    override fun close() = response.close()
}

class XtreamApi(
    private val client: OkHttpClient,
    val server: String,
    val username: String,
    val password: String,
    private val userAgent: String?,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun url(path: String, extra: String = ""): String =
        "$server/$path?username=${enc(username)}&password=${enc(password)}$extra"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun open(url: String): Response {
        val response = client.newCall(Http.request(url, userAgent)).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw HttpException(code, "HTTP $code")
        }
        return response
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun authenticate(): XtreamInfo {
        open(url("player_api.php")).use { response ->
            val root = response.body!!.byteStream().use { json.decodeFromStream<JsonElement>(it) } as? JsonObject
                ?: throw HttpException(0, "Unexpected answer")
            val user = root["user_info"] as? JsonObject ?: throw HttpException(0, "Login failed")
            if (user.str("auth") == "0") throw HttpException(401, "Login failed")
            val formats = (user["allowed_output_formats"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            return XtreamInfo(
                status = user.str("status"),
                expiry = user.long("exp_date"),
                maxConnections = user.int("max_connections"),
                activeConnections = user.int("active_cons"),
                trial = user.str("is_trial") == "1",
                outputFormats = formats,
            )
        }
    }

    fun stream(action: String, extra: String = ""): JsonStream {
        val response = open(url("player_api.php", "&action=$action$extra"))
        return JsonStream(response, objects(response.body!!.byteStream()))
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun objects(input: InputStream): Sequence<JsonObject> = sequence {
        try {
            yieldAll(json.decodeToSequence<JsonObject>(input, DecodeSequenceMode.ARRAY_WRAPPED))
        } catch (e: SerializationException) {
            // providers answer an empty list as false or an empty object
        } catch (e: IllegalStateException) {
        }
    }

    private fun text(o: JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> o.str(k)?.trim()?.takeIf { it.isNotEmpty() && it != "null" }?.decodeEntities() }

    private fun firstUrl(e: JsonElement?): String? = when (e) {
        is JsonArray -> e.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
        is JsonPrimitive -> e.contentOrNull?.takeIf { it.isNotBlank() }
        else -> null
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun vodInfo(vodId: String): VodInfo? {
        open(url("player_api.php", "&action=get_vod_info&vod_id=${enc(vodId)}")).use { response ->
            val root = response.body!!.byteStream().use { json.decodeFromStream<JsonElement>(it) } as? JsonObject ?: return null
            val info = root["info"] as? JsonObject ?: return null
            return VodInfo(
                plot = text(info, "plot", "description"),
                cast = text(info, "cast", "actors"),
                director = text(info, "director"),
                genre = text(info, "genre"),
                releaseDate = text(info, "releasedate", "release_date", "releaseDate"),
                durationSecs = info.int("duration_secs"),
                rating = text(info, "rating"),
                cover = text(info, "movie_image", "cover_big"),
                backdrop = firstUrl(info["backdrop_path"]),
            )
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun seriesDetail(seriesId: String): SeriesDetail {
        open(url("player_api.php", "&action=get_series_info&series_id=${enc(seriesId)}")).use { response ->
            val root = response.body!!.byteStream().use { json.decodeFromStream<JsonElement>(it) } as? JsonObject ?: return SeriesDetail(null, emptyList())
            val info = (root["info"] as? JsonObject)?.let {
                SeriesInfo(
                    plot = text(it, "plot"),
                    cast = text(it, "cast"),
                    director = text(it, "director"),
                    genre = text(it, "genre"),
                    releaseDate = text(it, "releaseDate", "release_date", "releasedate"),
                    rating = text(it, "rating"),
                    cover = text(it, "cover"),
                    backdrop = firstUrl(it["backdrop_path"]),
                )
            }
            val episodes = root["episodes"]
            val seasons: List<Pair<String, JsonElement>> = when (episodes) {
                is JsonObject -> episodes.entries.map { it.key to it.value }
                is JsonArray -> episodes.mapIndexed { i, e -> (i + 1).toString() to e }
                else -> emptyList()
            }
            val result = mutableListOf<XtreamEpisode>()
            for ((seasonKey, list) in seasons) {
                for (e in (list as? JsonArray).orEmpty()) {
                    val o = e as? JsonObject ?: continue
                    val id = o.str("id") ?: continue
                    val episodeInfo = o["info"] as? JsonObject
                    result += XtreamEpisode(
                        id = id,
                        season = o.int("season") ?: seasonKey.toIntOrNull() ?: 1,
                        number = o.int("episode_num") ?: 0,
                        title = (o.str("title") ?: "Episode ${o.int("episode_num") ?: ""}").decodeEntities(),
                        ext = o.str("container_extension") ?: "mp4",
                        plot = episodeInfo?.let { text(it, "plot") },
                        durationSecs = episodeInfo?.int("duration_secs"),
                        image = episodeInfo?.let { text(it, "movie_image") },
                        rating = episodeInfo?.let { text(it, "rating") },
                    )
                }
            }
            return SeriesDetail(info, result.sortedWith(compareBy({ it.season }, { it.number })))
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun shortEpg(streamId: String, limit: Int = 8): List<ShortProgram> {
        open(url("player_api.php", "&action=get_short_epg&stream_id=${enc(streamId)}&limit=$limit")).use { response ->
            val root = response.body!!.byteStream().use { json.decodeFromStream<JsonElement>(it) } as? JsonObject ?: return emptyList()
            val listings = root["epg_listings"] as? JsonArray ?: return emptyList()
            return listings.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val start = o.long("start_timestamp") ?: return@mapNotNull null
                val end = o.long("stop_timestamp") ?: return@mapNotNull null
                if (end <= start) return@mapNotNull null
                ShortProgram(start, end, decode(o.str("title")).orEmpty().decodeEntities(), decode(o.str("description"))?.decodeEntities())
            }
        }
    }

    private fun decode(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return try {
            val text = String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
            if (text.contains('\uFFFD')) value else text
        } catch (e: IllegalArgumentException) {
            value
        }
    }
}
