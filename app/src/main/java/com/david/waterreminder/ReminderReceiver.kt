package com.david.waterreminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
        onReceiveWithPrefs(context, intent, prefs)
    }

    fun onReceiveWithPrefs(context: Context, intent: Intent, prefs: SharedPreferences) {
        val reminderTime = intent.getStringExtra("reminder_time") ?: return

        // Alarms keep firing on an install that is never reopened, and rescheduling below
        // reads the window — so the migration runs here as well as in MainActivity.
        Reminder.migrateLegacyHourly(prefs)

        // Prefs are the source of truth, so the alarm carries only its time label and
        // everything else is looked up. Nothing to look up means the reminder is gone,
        // so there is nothing to fire either.
        val saved = Reminder.parseAll(prefs.getStringSet("reminders_v3", emptySet()))
        val reminder = saved.firstOrNull { it.time == reminderTime } ?: return

        // Reschedule first so the chain never breaks regardless of the notification toggle.
        if (reminder.enabled && !reminder.isOneTime) ReminderScheduler.schedule(context, reminder)

        // Respect the global notifications toggle
        if (!prefs.getBoolean("notifications_enabled", false)) return

        // Piggyback: refresh the others so a dropped alarm is recovered on the next
        // firing rather than waiting for a reboot.
        saved.filter { it !== reminder && it.enabled && !it.isOneTime }
            .forEach { ReminderScheduler.schedule(context, it) }

        // Read today's intake and goal (shared by suppression check + progress bar)
        val savedDate = prefs.getString("date", "")
        val today = java.time.LocalDate.now().toString()
        val intakeToday = if (savedDate == today) prefs.getInt("intake_ml", 0) else 0
        val personalGoal = prefs.getInt("personal_goal_ml", 0)
        val goal = if (personalGoal > 0) personalGoal else prefs.getInt("goal_ml", 1500)
        val unit = prefs.getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML
        val goalReached = goal > 0 && intakeToday >= goal

        if (goalReached && prefs.getBoolean("stop_notifications_on_goal", false)) return

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

        val builder = NotificationCompat.Builder(context, "water_reminder_channel")
            .setSmallIcon(R.drawable.ic_water_drop)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        if (goalReached) {
            builder
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(R.string.notification_body_goal_reached))
        } else {
            val intakeFmt = intakeToday.fmt(unit)
            val goalFmt = goal.fmt(unit)
            val title = context.resources.getStringArray(R.array.notification_titles).random()
            val body = context.resources.getStringArray(R.array.notification_bodies).random()
            builder
                .setContentTitle(title)
                .setContentText(body)
                .setSubText("$intakeFmt / $goalFmt")
                .setProgress(goal, intakeToday.coerceAtMost(goal), false)
        }

        val notification = builder.build()

        notificationManager.notify(reminderTime.hashCode(), notification)

        if (reminder.isOneTime) {
            // Mark as disabled in prefs so the UI shows it as off
            val updated = saved.map { if (it === reminder) it.copy(enabled = false) else it }
            prefs.edit()
                .putStringSet("reminders_v3", updated.map { it.toPrefsString() }.toSet())
                .apply()
        }
    }
}
