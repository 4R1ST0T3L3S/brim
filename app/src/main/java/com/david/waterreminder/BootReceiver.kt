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
            // RTC alarms survive a clock or timezone change but keep their old wall time, so
            // every fire time has to be recomputed against the new one.
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> rescheduleAll(context)
        }
    }

    private fun rescheduleAll(context: Context) {
        ReminderScheduler.rescheduleAll(context)
        // Beside it rather than inside it: the idle alarm is independent of the reminder list,
        // so someone who deleted every reminder still gets it back. Both calls check the
        // notifications toggle themselves.
        IdleReminderScheduler.schedule(context)
    }
}
