package com.kutlusoy.proiptv

import android.app.Application
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Repository
import com.kutlusoy.proiptv.data.SecretBox
import com.kutlusoy.proiptv.sync.SyncManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kutlusoy.proiptv.sync.GuideWorker
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

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.create(this)
        val secrets = SecretBox()
        repo = Repository(db, secrets, SyncManager(db, secrets, scope))
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
