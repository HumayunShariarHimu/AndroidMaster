package com.androidmaster.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val appContext = context.applicationContext
        try {
            val bridge = DeviceBridgeHolder.get(appContext)
            if (bridge.hasSavedConnection()) {
                DeviceConnectionService.startSaved(appContext)
            }
        } catch (error: Exception) {
            // OEM battery policies and foreground-service restrictions vary. Keep the failure diagnosable.
            Log.e("AndroidMasterBoot", "Unable to resume the saved device connection after $action", error)
        }
    }
}
