package com.david.waterreminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderTime = intent.getStringExtra("reminder_time") ?: return
        val reminderType = intent.getStringExtra("reminder_type") ?: "daily"

        val prefs = context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE)

        // Always reschedule first so the chain never breaks regardless of the notification toggle.
        when (reminderType) {
            "daily" -> ReminderScheduler.rescheduleNextDay(context, reminderTime, reminderType)
            "hourly" -> {
                val startHour = intent.getIntExtra("reminder_start_hour", 8)
                val endHour = intent.getIntExtra("reminder_end_hour", 20)
                ReminderScheduler.rescheduleHourly(context, reminderTime, startHour, endHour, reminderType)
            }
        }

        // Respect the global notifications toggle
        if (!prefs.getBoolean("notifications_enabled", false)) return

        // For hourly: only notify within the window
        if (reminderType == "hourly") {
            val startHour = intent.getIntExtra("reminder_start_hour", 8)
            val endHour = intent.getIntExtra("reminder_end_hour", 20)
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (currentHour !in startHour..endHour) return
        }

        // Show the notification
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "water_reminder_channel")
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle("Time to drink water! 💧")
            .setContentText("Stay hydrated — log your intake in WaterReminder.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(reminderTime.hashCode(), notification)

        if (reminderType == "onetime") {
            // Mark as disabled in prefs so the UI shows it as off
            val saved = prefs.getStringSet("reminders_v3", emptySet())
                ?.toMutableSet() ?: return
            val updated = saved.map { entry ->
                val r = Reminder.fromPrefsString(entry)
                if (r?.time == reminderTime) r.copy(enabled = false).toPrefsString() else entry
            }.toSet()
            prefs.edit().putStringSet("reminders_v3", updated).apply()
        }
    }
}
