package com.david.waterreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getStringSet("reminders_v3", emptySet()) ?: return

        saved.mapNotNull { Reminder.fromPrefsString(it) }
            .filter { it.enabled }
            .forEach { ReminderScheduler.schedule(context, it) }
    }
}
