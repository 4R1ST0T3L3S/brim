package com.david.waterreminder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderTime = intent.getStringExtra("reminder_time") ?: return
        val reminderType = intent.getStringExtra("reminder_type") ?: "daily"

        val prefs = context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE)

        // Respect the global notifications toggle
        if (!prefs.getBoolean("notifications_enabled", false)) return

        // Show the notification
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, "water_reminder_channel")
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle("Time to drink water! 💧")
            .setContentText("Stay hydrated — log your intake in RemindWell.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(reminderTime.hashCode(), notification)

        if (reminderType == "daily") {
            // Reschedule for next day using exact alarm
            ReminderScheduler.rescheduleNextDay(context, reminderTime, reminderType)
        } else {
            // One-time: mark as disabled in prefs so the UI shows it as off
            val saved = prefs.getStringSet("reminders_v2", emptySet())
                ?.toMutableSet() ?: return
            val updated = saved.map { entry ->
                val r = Reminder.fromPrefsString(entry)
                if (r?.time == reminderTime) r.copy(enabled = false).toPrefsString() else entry
            }.toSet()
            prefs.edit().putStringSet("reminders_v2", updated).apply()
        }
    }
}
