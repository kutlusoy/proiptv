package com.kutlusoy.proiptv.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kutlusoy.proiptv.R

object RecordingNotifications {
    const val CHANNEL = "recording"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.nav_recordings), NotificationManager.IMPORTANCE_LOW))
    }

    fun missed(context: Context, id: Long, title: String) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(context.getString(R.string.rec_missed_notification, title))
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(1000 + id.toInt(), notification) }
    }
}
