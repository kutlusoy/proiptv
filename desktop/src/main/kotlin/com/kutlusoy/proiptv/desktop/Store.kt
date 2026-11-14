package com.kutlusoy.proiptv.desktop

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
)

@Serializable
data class FavoriteDto(val account: String, val kind: Int, val remoteId: String, val name: String)

@Serializable
data class ConfigDto(
    val accounts: List<AccountDto> = emptyList(),
    val favorites: List<FavoriteDto> = emptyList(),
    val language: String = "",
    val autoUpdate: Boolean = true,
    val lastUpdateCheck: Long = 0,
    val lastUpdateNoticeDay: Long = -1,
    val volume: Int = 100,
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
