package com.kutlusoy.proiptv.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kutlusoy.proiptv.ProIptvApp
import kotlinx.coroutines.launch

class RecordingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(RecordingManager.EXTRA_ID, -1)
        if (id < 0) return
        val app = context.applicationContext as ProIptvApp
        val pending = goAsync()
        app.scope.launch {
            try {
                app.recordings.startScheduled(id)
            } finally {
                pending.finish()
            }
        }
    }
}
