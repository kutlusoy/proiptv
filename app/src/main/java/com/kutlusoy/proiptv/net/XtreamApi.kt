package com.kutlusoy.proiptv.net

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

data class XtreamEpisode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val ext: String,
    val plot: String?,
    val durationSecs: Int?,
)

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

    @OptIn(ExperimentalSerializationApi::class)
    fun seriesEpisodes(seriesId: String): List<XtreamEpisode> {
        open(url("player_api.php", "&action=get_series_info&series_id=${enc(seriesId)}")).use { response ->
            val root = response.body!!.byteStream().use { json.decodeFromStream<JsonElement>(it) } as? JsonObject ?: return emptyList()
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
                    val info = o["info"] as? JsonObject
                    result += XtreamEpisode(
                        id = id,
                        season = o.int("season") ?: seasonKey.toIntOrNull() ?: 1,
                        number = o.int("episode_num") ?: 0,
                        title = o.str("title") ?: "Episode ${o.int("episode_num") ?: ""}",
                        ext = o.str("container_extension") ?: "mp4",
                        plot = info?.str("plot"),
                        durationSecs = info?.int("duration_secs"),
                    )
                }
            }
            return result.sortedWith(compareBy({ it.season }, { it.number }))
        }
    }

    fun xmltv(): Response = open(url("xmltv.php"))
}
