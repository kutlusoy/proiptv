package com.kutlusoy.proiptv.update

import android.content.Context
import com.kutlusoy.proiptv.BuildConfig
import com.kutlusoy.proiptv.net.Http
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Looks for a newer release on GitHub and installs it. Installing always needs a press of the button. */
class UpdateManager(private val context: Context, private val downloader: UpdateDownloader = UpdateDownloader(Http.client)) {
    private val folder = File(context.cacheDir, "update")

    sealed class CheckResult {
        class Available(val release: ReleaseInfo) : CheckResult()
        class UpToDate(val version: String) : CheckResult()
    }

    enum class Phase { VERIFYING, DOWNLOADING, INSTALLING }

    /** Release builds follow the latest release, test builds follow the newest test release. */
    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) {
            val text = downloader.fetchText(RELEASES_URL, ACCEPT)
            val release = ReleaseParser.parseNewestTest(text) ?: throw UpdateException(UpdateException.Code.NOT_FOUND)
            if (release.build > BuildConfig.TEST_BUILD) CheckResult.Available(release) else CheckResult.UpToDate(release.version)
        } else {
            val text = downloader.fetchText(LATEST_URL, ACCEPT)
            val release = ReleaseParser.parseLatest(text) ?: throw UpdateException(UpdateException.Code.NOT_FOUND)
            if (release.versionCode > BuildConfig.VERSION_CODE) CheckResult.Available(release) else CheckResult.UpToDate(release.version)
        }
    }

    suspend fun downloadAndInstall(release: ReleaseInfo, onPhase: (Phase, Int) -> Unit) = withContext(Dispatchers.IO) {
        val apkUrl = release.apkUrl
        val apkName = release.apkName
        if (apkUrl == null || apkName == null) throw UpdateException(UpdateException.Code.NO_APK)
        val checksumsUrl = release.checksumsUrl ?: throw UpdateException(UpdateException.Code.NO_CHECKSUM)
        folder.deleteRecursively()
        folder.mkdirs()
        if (release.apkSize > 0 && folder.usableSpace < release.apkSize * 2) throw UpdateException(UpdateException.Code.STORAGE)

        onPhase(Phase.VERIFYING, 0)
        val checksum = Checksums.find(downloader.fetchText(checksumsUrl), apkName) ?: throw UpdateException(UpdateException.Code.NO_CHECKSUM)

        val target = File(folder, apkName)
        onPhase(Phase.DOWNLOADING, 0)
        downloader.download(apkUrl, target, checksum, MAX_APK_BYTES) { onPhase(Phase.DOWNLOADING, it) }

        onPhase(Phase.INSTALLING, 100)
        try {
            UpdateInstaller.install(context, target)
        } catch (e: Exception) {
            throw UpdateException(UpdateException.Code.INSTALL_FAILED, e.message)
        }
    }

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/kutlusoy/proiptv/releases/latest"
        const val RELEASES_URL = "https://api.github.com/repos/kutlusoy/proiptv/releases?per_page=30"
        private const val ACCEPT = "application/vnd.github+json"
        private const val MAX_APK_BYTES = 300L * 1024 * 1024
    }
}
