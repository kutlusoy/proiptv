package com.kutlusoy.proiptv

import android.app.Application
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.BackupManager
import com.kutlusoy.proiptv.data.Repository
import com.kutlusoy.proiptv.data.SecretBox
import com.kutlusoy.proiptv.sync.SyncManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kutlusoy.proiptv.download.DownloadManager
import com.kutlusoy.proiptv.engine.EngineManager
import com.kutlusoy.proiptv.net.Http
import com.kutlusoy.proiptv.recording.RecordingManager
import com.kutlusoy.proiptv.sync.GuideWorker
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProIptvApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repo: Repository
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var engine: EngineManager
        private set
    lateinit var recordings: RecordingManager
        private set
    lateinit var backup: BackupManager
        private set
    lateinit var downloads: DownloadManager
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.create(this)
        val secrets = SecretBox()
        repo = Repository(db, secrets, SyncManager(db, secrets, scope))
        prefs = Prefs(this)
        val hubDir = File(cacheDir, "timeshift")
        engine = EngineManager(hubDir, scope, capacity = {
            val wanted = prefs.bufferMb * 1024L * 1024L
            minOf(wanted, hubDir.usableSpace / 3).coerceAtLeast(64L * 1024 * 1024)
        })
        recordings = RecordingManager(this, db, engine, scope, resolve = { repo.target(it) }, maxConnections = { repo.maxConnections(it) })
        downloads = DownloadManager(this, db, scope, Http.client, File(getExternalFilesDir(null) ?: filesDir, "downloads"), resolve = { repo.downloadSource(it) })
        backup = BackupManager(repo, { prefs.timeshift to prefs.bufferMb }, { on, mb -> prefs.timeshift = on; prefs.bufferMb = mb }, { secrets.decrypt(it.password) })
        scope.launch {
            recordings.recoverInterrupted()
            recordings.rescheduleAll()
            downloads.resume()
        }
        scope.launch {
            repo.sync.syncStale(STALE_MS)
            while (true) {
                repo.sync.syncStaleGuides()
                delay(GUIDE_CHECK_MS)
            }
        }
        runCatching {
            val request = PeriodicWorkRequestBuilder<GuideWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork("guide", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    private companion object {
        const val STALE_MS = 12 * 60 * 60 * 1000L
        const val GUIDE_CHECK_MS = 30 * 60 * 1000L
    }
}
