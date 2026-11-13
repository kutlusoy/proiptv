package com.kutlusoy.proiptv.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kutlusoy.proiptv.ProIptvApp
import com.kutlusoy.proiptv.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RecordingService : Service() {
    private var wake: PowerManager.WakeLock? = null
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as ProIptvApp
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.nav_recordings), NotificationManager.IMPORTANCE_LOW))
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(app.recordings.active.value.firstOrNull()?.title.orEmpty()),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (wake == null) {
            wake = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "proiptv:recording").apply { acquire(12 * 60 * 60 * 1000L) }
        }
        job?.cancel()
        job = app.scope.launch {
            app.recordings.active.collect { list ->
                if (list.isEmpty()) {
                    stopSelf()
                } else {
                    manager.notify(NOTIFICATION_ID, notification(list.joinToString(", ") { it.title }))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(titles: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(getString(R.string.rec_notification, titles))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    override fun onDestroy() {
        job?.cancel()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
        }
    }
}
