package com.kutlusoy.proiptv.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    // Receiving the broadcast starts the app process, ProIptvApp re-registers the alarms of scheduled recordings.
    override fun onReceive(context: Context, intent: Intent) = Unit
}
