package com.kutlusoy.proiptv.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AccountDto(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String,
    val url: String,
    val username: String = "",
    val password: String = "",
    val userAgent: String? = null,
    val enabled: Boolean = true,
    val epgUrl: String? = null,
)

@Serializable
data class FavoriteDto(val account: String, val kind: Int, val remoteId: String, val name: String, val listId: String = "")

/** A favorites list. Live TV, movies and series each have their own lists. */
@Serializable
data class FavoriteListDto(val id: String = UUID.randomUUID().toString(), val name: String, val kind: Int)

@Serializable
data class ProgressDto(
    val account: String,
    val kind: Int,
    val remoteId: String,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val watched: Boolean = false,
    val updated: Long = 0,
    val seriesId: String = "",
) {
    val key: String get() = "$account|$kind|$remoteId"
}

@Serializable
data class ConfigDto(
    val accounts: List<AccountDto> = emptyList(),
    val favorites: List<FavoriteDto> = emptyList(),
    val favoriteLists: List<FavoriteListDto> = emptyList(),
    val language: String = "",
    val autoUpdate: Boolean = true,
    val lastUpdateCheck: Long = 0,
    val lastUpdateNoticeDay: Long = -1,
    val volume: Int = 100,
    val timeshift: Boolean = true,
    val bufferMb: Int = 512,
)

/** Passwords are protected with Windows DPAPI, so only the same Windows user can read them. Other systems keep them unprotected (development only). */
object Secrets {
    private val windows = System.getProperty("os.name").lowercase().contains("win")

    fun protect(plain: String): String {
        if (plain.isEmpty()) return ""
        return if (windows) "dpapi:" + Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(plain.toByteArray(Charsets.UTF_8)))
        else "plain:" + Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8))
    }

    fun unprotect(stored: String): String = when {
        stored.startsWith("dpapi:") && windows ->
            String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(stored.removePrefix("dpapi:"))), Charsets.UTF_8)
        stored.startsWith("plain:") -> String(Base64.getDecoder().decode(stored.removePrefix("plain:")), Charsets.UTF_8)
        else -> ""
    }
}

class Store(val dir: File) {
    private val file = File(dir, "config.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    var config: ConfigDto = load()
        private set

    private fun load(): ConfigDto = runCatching { json.decodeFromString<ConfigDto>(file.readText()) }.getOrDefault(ConfigDto())

    @Synchronized
    fun update(change: (ConfigDto) -> ConfigDto) {
        config = change(config)
        dir.mkdirs()
        val tmp = File(dir, "config.json.tmp")
        tmp.writeText(json.encodeToString(config))
        tmp.copyTo(file, overwrite = true)
        tmp.delete()
    }

    companion object {
        fun defaultDir(): File {
            val appData = System.getenv("APPDATA")
            return if (appData != null) File(appData, "ProIPTV") else File(System.getProperty("user.home"), ".proiptv")
        }
    }
}

/** Where the player stopped and what was watched, kept in progress.json next to the settings. */
class ProgressStore(private val dir: File) {
    private val file = File(dir, "progress.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val map: MutableMap<String, ProgressDto> = runCatching { json.decodeFromString<List<ProgressDto>>(file.readText()) }
        .getOrDefault(emptyList()).associateBy { it.key }.toMutableMap()

    /** Changes with every write, so screens that read the progress update themselves. */
    var version by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    @Synchronized
    fun get(account: String, kind: Int, remoteId: String): ProgressDto? = map["$account|$kind|$remoteId"]

    @Synchronized
    fun all(): List<ProgressDto> = map.values.toList()

    @Synchronized
    fun save(p: ProgressDto) {
        map[p.key] = p
        write()
    }

    @Synchronized
    fun setWatched(account: String, kind: Int, ids: List<String>, watched: Boolean) {
        for (id in ids) {
            val old = map["$account|$kind|$id"]
            map["$account|$kind|$id"] = (old ?: ProgressDto(account, kind, id)).copy(watched = watched, positionMs = if (watched) 0 else old?.positionMs ?: 0, updated = System.currentTimeMillis())
        }
        write()
    }

    private fun write() {
        version++
        runCatching {
            dir.mkdirs()
            File(dir, "progress.json.tmp").also { it.writeText(json.encodeToString(map.values.toList())) }.copyTo(file, overwrite = true)
        }
    }
}
