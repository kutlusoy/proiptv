package com.kutlusoy.proiptv.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kutlusoy.proiptv.ProIptvApp
import com.kutlusoy.proiptv.R
import java.util.concurrent.TimeUnit

/** Checks once a day in the background and shows at most one notification per day. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ProIptvApp
        if (!app.prefs.autoUpdate) return Result.success()
        val result = runCatching { UpdateManager(applicationContext).check() }.getOrNull() ?: return Result.success()
        app.prefs.lastUpdateCheck = System.currentTimeMillis()
        if (result is UpdateManager.CheckResult.Available) {
            val today = System.currentTimeMillis() / DAY_MS
            if (UpdateNotifyPolicy.shouldNotify(app.prefs.lastUpdateNotifyDay, today)) {
                notify(applicationContext, result.release.version)
                app.prefs.lastUpdateNotifyDay = today
            }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "update-check"
        private const val CHANNEL = "updates"
        private const val DAY_MS = 24L * 60 * 60 * 1000

        fun schedule(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun notify(context: Context, version: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.update_title), NotificationManager.IMPORTANCE_DEFAULT))
            }
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_record)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(context.getString(R.string.update_notification_text, version))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }

        private const val NOTIFICATION_ID = 7313
    }
}
