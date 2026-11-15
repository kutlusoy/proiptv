package com.kutlusoy.proiptv.recording

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kutlusoy.proiptv.DebugLog
import com.kutlusoy.proiptv.ui.MainActivity

/** Sets an alarm for a planned recording or a reminder. Exact when the device allows it, else inexact: never an exception. */
object Alarms {
    fun set(context: Context, atMs: Long, operation: PendingIntent) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val exact = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
        if (exact) {
            try {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(atMs, show), operation)
                return
            } catch (e: SecurityException) {
                DebugLog.write("Exact alarm refused: ${e.message}")
            }
        }
        runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, operation) }
            .onFailure { DebugLog.write("Alarm failed: ${it.javaClass.simpleName} ${it.message}") }
    }
}
