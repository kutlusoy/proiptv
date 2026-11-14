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

    /** Downloads and verifies the setup. Returns the file, the caller starts it. */
    suspend fun download(release: ReleaseInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: throw UpdateException(UpdateException.Code.NO_APK)
        val name = release.apkName ?: throw UpdateException(UpdateException.Code.NO_APK)
        val sums = release.checksumsUrl ?: throw UpdateException(UpdateException.Code.NO_CHECKSUM)
        folder.deleteRecursively()
        folder.mkdirs()
        val checksum = Checksums.find(downloader.fetchText(sums), name) ?: throw UpdateException(UpdateException.Code.NO_CHECKSUM)
        val target = File(folder, name)
        downloader.download(url, target, checksum, MAX_BYTES, onProgress)
        target
    }

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/kutlusoy/proiptv/releases/latest"
        const val RELEASES_URL = "https://api.github.com/repos/kutlusoy/proiptv/releases?per_page=30"
        private const val ACCEPT = "application/vnd.github+json"
        private const val MAX_BYTES = 800L * 1024 * 1024

        /** Starts the installer silently. It closes the app, installs and starts the new version. */
        fun runInstaller(setup: File) {
            ProcessBuilder(setup.absolutePath, "/SILENT", "/SUPPRESSMSGBOXES", "/CLOSEAPPLICATIONS", "/RESTARTAPPLICATIONS", "/NORESTART").start()
        }
    }
}
