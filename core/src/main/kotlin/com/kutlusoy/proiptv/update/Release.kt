package com.kutlusoy.proiptv.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/** A published release (or test release) and the files that belong to it. */
class ReleaseInfo(
    val version: String,
    val versionCode: Long,
    val build: Int,
    val apkName: String?,
    val apkUrl: String?,
    val apkSize: Long,
    val checksumsUrl: String?,
)

class UpdateException(val code: Code, detail: String? = null) : Exception(detail ?: code.name) {
    enum class Code { NETWORK, NOT_FOUND, NO_APK, NO_CHECKSUM, CHECKSUM_MISMATCH, INSECURE_URL, TOO_LARGE, STORAGE, INSTALL_FAILED }
}

object ReleaseParser {
    val APK_NAME = Regex("^ProIPTV-.+\\.apk$")
    val WINDOWS_NAME = Regex("^ProIPTV-.+-windows-x64-setup\\.exe$")
    private const val CHECKSUMS_NAME = "SHA256SUMS.txt"
    private val TEST_TAG = Regex("^test-(\\d+)$")
    private val json = Json { ignoreUnknownKeys = true }

    /** The answer of /releases/latest. Null for a draft, a pre-release or a bad answer. */
    fun parseLatest(text: String, pattern: Regex = APK_NAME): ReleaseInfo? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        if (root.flag("draft") || root.flag("prerelease")) return null
        val tag = root.str("tag_name") ?: return null
        val version = tag.removePrefix("v")
        val code = Versions.code(version) ?: return null
        return build(root, version, code, 0, pattern)
    }

    /** The newest test release (pre-release with a tag like test-12) from the list of releases. */
    fun parseNewestTest(text: String, pattern: Regex = APK_NAME): ReleaseInfo? {
        val list: JsonArray = runCatching { json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return null
        var best: Pair<Int, JsonObject>? = null
        for (element in list) {
            val obj = runCatching { element.jsonObject }.getOrNull() ?: continue
            if (obj.flag("draft") || !obj.flag("prerelease")) continue
            val n = TEST_TAG.matchEntire(obj.str("tag_name") ?: continue)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            if (best == null || n > best.first) best = n to obj
        }
        val (n, obj) = best ?: return null
        return build(obj, "test $n", 0, n, pattern)
    }

    private fun build(root: JsonObject, version: String, code: Long, build: Int, pattern: Regex): ReleaseInfo {
        var apkName: String? = null
        var apkUrl: String? = null
        var apkSize = -1L
        var checksumsUrl: String? = null
        runCatching { root["assets"]?.jsonArray }.getOrNull()?.forEach { element ->
            val asset = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
            val name = asset.str("name") ?: return@forEach
            val url = asset.str("browser_download_url") ?: return@forEach
            when {
                name == CHECKSUMS_NAME -> checksumsUrl = url
                apkName == null && pattern.matches(name) -> {
                    apkName = name
                    apkUrl = url
                    apkSize = runCatching { asset["size"]?.jsonPrimitive?.long }.getOrNull() ?: -1L
                }
            }
        }
        return ReleaseInfo(version, code, build, apkName, apkUrl, apkSize, checksumsUrl)
    }

    private fun JsonObject.str(key: String): String? = runCatching { this[key]?.jsonPrimitive?.takeIf { it.isString }?.content }.getOrNull()
    private fun JsonObject.flag(key: String): Boolean = runCatching { this[key]?.jsonPrimitive?.boolean }.getOrNull() == true
}

object Versions {
    private val PATTERN = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(-[0-9A-Za-z.]+)?$")

    /** The version code of a version, the same scheme as app/version.txt and tools/version.py. Null for anything else. */
    fun code(version: String): Long? {
        val m = PATTERN.matchEntire(version) ?: return null
        val (major, minor, patch) = m.destructured
        if (minor.length > 3 || patch.length > 3 || major.length > 4) return null
        return major.toLong() * 1_000_000 + minor.toLong() * 1_000 + patch.toLong()
    }
}

object Checksums {
    private val HASH = Regex("^[0-9a-fA-F]{64}$")

    /** Finds the SHA-256 of a file in the text of SHA256SUMS.txt (lines like "hash  ./name"). */
    fun find(text: String, fileName: String): String? =
        text.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val name = parts[1].removePrefix("*").removePrefix("./")
            if (name == fileName && HASH.matches(parts[0])) parts[0].lowercase() else null
        }.firstOrNull()
}

/** At most one notification per calendar day. */
object UpdateNotifyPolicy {
    fun shouldNotify(lastDay: Long, today: Long): Boolean = today != lastDay
}
