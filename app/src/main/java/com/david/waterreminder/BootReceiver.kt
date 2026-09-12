package com.david.waterreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> rescheduleAll(context)
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val airplaneModeOn = intent.getBooleanExtra("state", false)
                if (!airplaneModeOn) rescheduleAll(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> rescheduleAll(context)
        }
    }

    private fun rescheduleAll(context: Context) = rescheduleAllAlarms(context)
}
