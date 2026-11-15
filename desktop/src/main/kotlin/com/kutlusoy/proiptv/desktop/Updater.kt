package com.kutlusoy.proiptv.desktop

import com.kutlusoy.proiptv.net.Http
import com.kutlusoy.proiptv.update.Checksums
import com.kutlusoy.proiptv.update.ReleaseInfo
import com.kutlusoy.proiptv.update.ReleaseParser
import com.kutlusoy.proiptv.update.UpdateDownloader
import com.kutlusoy.proiptv.update.UpdateException
import com.kutlusoy.proiptv.update.Versions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Same method as the Android app: ask GitHub, download the setup, check the SHA-256 of SHA256SUMS.txt, then start the installer. */
class Updater(private val downloader: UpdateDownloader = UpdateDownloader(Http.client), private val folder: File = File(System.getProperty("java.io.tmpdir"), "proiptv-update")) {

    sealed class Result {
        class Available(val release: ReleaseInfo) : Result()
        class UpToDate(val version: String) : Result()
    }

    suspend fun check(): Result = withContext(Dispatchers.IO) {
        if (AppInfo.isTest) {
            val text = downloader.fetchText(RELEASES_URL, ACCEPT)
            val release = ReleaseParser.parseNewestTest(text, ReleaseParser.WINDOWS_NAME) ?: throw UpdateException(UpdateException.Code.NOT_FOUND)
            if (release.build > AppInfo.testBuild) Result.Available(release) else Result.UpToDate(release.version)
        } else {
            val text = downloader.fetchText(LATEST_URL, ACCEPT)
            val release = ReleaseParser.parseLatest(text, ReleaseParser.WINDOWS_NAME) ?: throw UpdateException(UpdateException.Code.NOT_FOUND)
            val installed = Versions.code(AppInfo.version) ?: 0L
            if (release.versionCode > installed) Result.Available(release) else Result.UpToDate(release.version)
        }
    }

    /** The downloaded setup and the checksum it was verified with. */
    class Package(val file: File, val sha256: String, val size: Long)

    /** Downloads and verifies the setup. The caller starts it later with install. */
    suspend fun download(release: ReleaseInfo, onProgress: (Int) -> Unit): Package = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: throw UpdateException(UpdateException.Code.NO_APK)
        val name = release.apkName ?: throw UpdateException(UpdateException.Code.NO_APK)
        val sums = release.checksumsUrl ?: throw UpdateException(UpdateException.Code.NO_CHECKSUM)
        folder.deleteRecursively()
        folder.mkdirs()
        val checksum = Checksums.find(downloader.fetchText(sums), name) ?: throw UpdateException(UpdateException.Code.NO_CHECKSUM)
        val target = File(folder, name)
        downloader.download(url, target, checksum, MAX_BYTES, onProgress)
        Package(target, checksum, target.length())
    }

    /** Checks the file on the disk again, then starts the setup. A setup that is still locked (virus scanner) or ends at once is tried again. */
    suspend fun install(pkg: Package) = withContext(Dispatchers.IO) {
        val file = pkg.file
        if (!file.isFile || file.length() != pkg.size) throw UpdateException(UpdateException.Code.INSTALL_FAILED, "The file is incomplete: ${file.absolutePath}")
        if (!sha256(file).equals(pkg.sha256, ignoreCase = true)) throw UpdateException(UpdateException.Code.CHECKSUM_MISMATCH)
        var last: String? = null
        repeat(8) { attempt ->
            try {
                val process = ProcessBuilder(file.absolutePath, "/SILENT", "/SUPPRESSMSGBOXES", "/CLOSEAPPLICATIONS", "/RESTARTAPPLICATIONS", "/NORESTART").start()
                delay(2000)
                if (process.isAlive || process.exitValue() == 0) return@withContext
                last = "The setup ended with code ${process.exitValue()}"
            } catch (e: java.io.IOException) {
                last = e.message
            }
            Log.write("Update: start of the setup failed (try ${attempt + 1}): $last")
            delay(1500)
        }
        throw UpdateException(UpdateException.Code.INSTALL_FAILED, "$last (${file.absolutePath})")
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/kutlusoy/proiptv/releases/latest"
        const val RELEASES_URL = "https://api.github.com/repos/kutlusoy/proiptv/releases?per_page=30"
        private const val ACCEPT = "application/vnd.github+json"
        private const val MAX_BYTES = 800L * 1024 * 1024
    }
}
