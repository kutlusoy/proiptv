package com.kutlusoy.proiptv

import android.app.Application
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Repository
import com.kutlusoy.proiptv.data.SecretBox
import com.kutlusoy.proiptv.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        scope.launch { repo.sync.syncStale(STALE_MS) }
    }

    private companion object {
        const val STALE_MS = 12 * 60 * 60 * 1000L
    }
}
