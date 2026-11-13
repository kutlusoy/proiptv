package com.kutlusoy.proiptv.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kutlusoy.proiptv.ProIptvApp

class GuideWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as ProIptvApp).repo.sync.refreshDueGuidesNow()
        return Result.success()
    }
}
