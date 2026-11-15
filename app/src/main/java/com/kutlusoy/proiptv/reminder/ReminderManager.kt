package com.kutlusoy.proiptv.reminder

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kutlusoy.proiptv.DebugLog
import com.kutlusoy.proiptv.ProIptvApp
import com.kutlusoy.proiptv.R
import com.kutlusoy.proiptv.data.AppDatabase
import com.kutlusoy.proiptv.data.Reminder
import com.kutlusoy.proiptv.ui.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** Delivers a reminder to the open app; without an open app a notification is shown. */
object ReminderBus {
    val events = MutableSharedFlow<Reminder>(extraBufferCapacity = 4)

    fun deliver(reminder: Reminder): Boolean = events.subscriptionCount.value > 0 && events.tryEmit(reminder)
}

/** A reminder one minute before a show starts: the app jumps to the channel. */
class ReminderManager(private val app: Application, private val db: AppDatabase) {

    suspend fun forProgram(accountId: Long, channelRemoteId: String, startMs: Long): Reminder? =
        db.reminders().find(accountId, channelRemoteId, startMs)

    suspend fun add(accountId: Long, channelRemoteId: String, channelName: String, title: String, startMs: Long): Long {
        val id = db.reminders().insert(Reminder(accountId = accountId, channelRemoteId = channelRemoteId, channelName = channelName, title = title, startMs = startMs))
        setAlarm(id, startMs - LEAD_MS)
        return id
    }

    suspend fun remove(id: Long) {
        cancelAlarm(id)
        db.reminders().delete(id)
    }

    /** Reminders whose show has begun are used up. */
    suspend fun removeExpired() = db.reminders().deleteBefore(System.currentTimeMillis())

    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        for (r in db.reminders().all()) {
            if (r.startMs <= now) db.reminders().delete(r.id) else setAlarm(r.id, r.startMs - LEAD_MS)
        }
    }

    /** The alarm went off: the reminder is used up. */
    suspend fun fire(id: Long) {
        val reminder = db.reminders().get(id) ?: return
        db.reminders().delete(id)
        if (reminder.startMs < System.currentTimeMillis() - 5 * 60_000L) return
        if (!ReminderBus.deliver(reminder)) notify(reminder)
    }

    private fun notify(reminder: Reminder) {
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, app.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            app, reminder.id.toInt(),
            Intent(app, MainActivity::class.java).putExtra(EXTRA_ACCOUNT, reminder.accountId).putExtra(EXTRA_CHANNEL, reminder.channelRemoteId).putExtra(EXTRA_TITLE, reminder.title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(app.getString(R.string.reminder_notification, reminder.title))
            .setContentText(reminder.channelName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(2000 + reminder.id.toInt(), notification) }
    }

    private fun alarmIntent(id: Long, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            app, 100_000 + id.toInt(), Intent(app, ReminderAlarmReceiver::class.java).putExtra(EXTRA_ID, id),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun setAlarm(id: Long, atMs: Long) {
        val manager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        // a show that starts within the minute: the reminder comes at once
        val at = maxOf(atMs, System.currentTimeMillis() + 2_000L)
        val operation = alarmIntent(id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
        } catch (e: Exception) {
            // a device that refuses exact alarms: an inexact one is better than a crash
            DebugLog.write("Reminder alarm: ${e.javaClass.simpleName} ${e.message}")
            runCatching { manager.set(AlarmManager.RTC_WAKEUP, at, operation) }
        }
    }

    private fun cancelAlarm(id: Long) {
        val manager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmIntent(id, PendingIntent.FLAG_NO_CREATE)?.let { manager.cancel(it) }
    }

    companion object {
        const val LEAD_MS = 60_000L
        const val CHANNEL = "reminder"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_ACCOUNT = "reminder_account"
        const val EXTRA_CHANNEL = "reminder_channel"
        const val EXTRA_TITLE = "reminder_title"
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderManager.EXTRA_ID, -1)
        if (id < 0) return
        val app = context.applicationContext as ProIptvApp
        val pending = goAsync()
        app.scope.launch {
            try {
                app.reminders.fire(id)
            } finally {
                pending.finish()
            }
        }
    }
}
