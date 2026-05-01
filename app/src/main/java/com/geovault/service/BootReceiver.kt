package com.geovault.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // This is where we could re-initialize services or alarms if needed
            // Accessibility services are handled by the system, but we might want to
            // ensure certain state is preserved.
        }
    }
}
