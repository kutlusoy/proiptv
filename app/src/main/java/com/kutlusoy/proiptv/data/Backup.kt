package com.kutlusoy.proiptv.data

import java.security.SecureRandom
import kotlinx.coroutines.flow.first
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class InvalidBackup : Exception("not a ProIPTV backup")
class WrongPassword : Exception("wrong password")

@Serializable
data class BackupAccount(
    val name: String,
    val type: String,
    val url: String,
    val username: String = "",
    val password: String? = null,
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val liveFormat: String = "auto",
    val enabled: Boolean = true,
    val useVod: Boolean = true,
    val useSeries: Boolean = true,
)

@Serializable
data class BackupFavorite(val account: String, val kind: Int, val remoteId: String, val name: String)

@Serializable
data class BackupList(val name: String, val entries: List<BackupFavorite>)

@Serializable
data class BackupData(
    val accounts: List<BackupAccount>,
    val lists: List<BackupList>,
    val timeshift: Boolean = true,
    val bufferMb: Int = 1024,
)

@Serializable
data class BackupEnvelope(
    val format: Int,
    val app: String,
    val created: Long,
    val encrypted: Boolean,
    val data: BackupData? = null,
    val kdf: String? = null,
    val iterations: Int? = null,
    val salt: String? = null,
    val iv: String? = null,
    val cipher: String? = null,
)

object BackupCodec {
    private const val FORMAT = 1
    private const val APP = "proiptv"
    private const val ITERATIONS = 120_000
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun accountKey(type: String, url: String, username: String) = "$type|$url|$username"

    fun encode(data: BackupData, password: String?, now: Long = System.currentTimeMillis()): String {
        if (password.isNullOrEmpty()) {
            val stripped = data.copy(accounts = data.accounts.map { it.copy(password = null) })
            return json.encodeToString(BackupEnvelope.serializer(), BackupEnvelope(FORMAT, APP, now, false, data = stripped))
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val kdf = availableKdf()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, kdf, ITERATIONS), GCMParameterSpec(128, iv))
        val plain = json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8)
        val envelope = BackupEnvelope(
            FORMAT, APP, now, true, kdf = kdf, iterations = ITERATIONS,
            salt = hex(salt), iv = hex(iv), cipher = hex(cipher.doFinal(plain)),
        )
        return json.encodeToString(BackupEnvelope.serializer(), envelope)
    }

    fun isEncrypted(text: String): Boolean = parse(text).encrypted

    fun decode(text: String, password: String?): BackupData {
        val envelope = parse(text)
        if (!envelope.encrypted) return envelope.data ?: throw InvalidBackup()
        if (password.isNullOrEmpty()) throw WrongPassword()
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val key = key(password, unhex(envelope.salt!!), envelope.kdf!!, envelope.iterations!!)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, unhex(envelope.iv!!)))
            val plain = cipher.doFinal(unhex(envelope.cipher!!))
            json.decodeFromString(BackupData.serializer(), String(plain, Charsets.UTF_8))
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPassword()
        } catch (e: NullPointerException) {
            throw InvalidBackup()
        }
    }

    private fun parse(text: String): BackupEnvelope {
        val envelope = try {
            json.decodeFromString(BackupEnvelope.serializer(), text)
        } catch (e: Exception) {
            throw InvalidBackup()
        }
        if (envelope.app != APP || envelope.format != FORMAT) throw InvalidBackup()
        return envelope
    }

    private fun availableKdf(): String =
        try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            "PBKDF2WithHmacSHA256"
        } catch (e: Exception) {
            "PBKDF2WithHmacSHA1"
        }

    private fun key(password: String, salt: ByteArray, kdf: String, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance(kdf).generateSecret(spec).encoded, "AES")
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(text: String): ByteArray {
        if (text.length % 2 != 0) throw InvalidBackup()
        return ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}

data class RestoreResult(val accounts: Int, val favorites: Int)

class BackupManager(
    private val repo: Repository,
    private val getSettings: () -> Pair<Boolean, Int>,
    private val setSettings: (Boolean, Int) -> Unit,
    private val decryptPassword: (Account) -> String,
) {
    suspend fun export(password: String?): String {
        val db = repo.db
        val accounts = db.accounts().list()
        val keys = accounts.associate { it.id to BackupCodec.accountKey(it.type, it.url, it.username) }
        val includePasswords = !password.isNullOrEmpty()
        val lists = db.favorites().lists().first().map { list ->
            BackupList(
                list.name,
                db.favorites().entries(list.id).mapNotNull { f ->
                    keys[f.accountId]?.let { BackupFavorite(it, f.kind, f.remoteId, f.name) }
                },
            )
        }
        val (timeshift, bufferMb) = getSettings()
        val data = BackupData(
            accounts = accounts.map {
                BackupAccount(
                    it.name, it.type, it.url, it.username, if (includePasswords) decryptPassword(it) else null,
                    it.epgUrl, it.userAgent, it.liveFormat, it.enabled, it.useVod, it.useSeries,
                )
            },
            lists = lists, timeshift = timeshift, bufferMb = bufferMb,
        )
        return BackupCodec.encode(data, password)
    }

    suspend fun restore(text: String, password: String?): RestoreResult {
        val data = BackupCodec.decode(text, password)
        val db = repo.db
        val existing = db.accounts().list().associateBy { BackupCodec.accountKey(it.type, it.url, it.username) }.toMutableMap()
        val ids = existing.mapValues { it.value.id }.toMutableMap()
        var added = 0
        for (a in data.accounts) {
            val key = BackupCodec.accountKey(a.type, a.url, a.username)
            if (key in ids) continue
            val id = repo.saveAccount(
                Account(
                    name = a.name, type = a.type, url = a.url, username = a.username, epgUrl = a.epgUrl, userAgent = a.userAgent,
                    liveFormat = a.liveFormat, enabled = a.enabled, useVod = a.useVod, useSeries = a.useSeries,
                ),
                a.password.orEmpty(),
            )
            ids[key] = id
            added++
        }
        var favorites = 0
        val lists = db.favorites().lists().first()
        for (l in data.lists) {
            val listId = lists.firstOrNull { it.name == l.name }?.id ?: repo.createList(l.name)
            val known = db.favorites().entries(listId).map { Triple(it.accountId, it.kind, it.remoteId) }.toSet()
            for (e in l.entries) {
                val accountId = ids[e.account] ?: continue
                if (Triple(accountId, e.kind, e.remoteId) in known) continue
                repo.setFavorite(listId, accountId, e.kind, e.remoteId, e.name, true)
                favorites++
            }
        }
        setSettings(data.timeshift, data.bufferMb)
        return RestoreResult(added, favorites)
    }
}
