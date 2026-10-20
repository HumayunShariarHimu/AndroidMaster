package com.androidmaster.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val bridge = DeviceBridgeHolder.get(context.applicationContext)
        if (bridge.hasSavedConnection()) {
            DeviceConnectionService.startSaved(context.applicationContext)
        }
    }
}
