package com.david.waterreminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object ReminderScheduler {

    fun schedule(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextFireTime(context, reminder).timeInMillis,
            buildPendingIntent(context, reminder)
        )
    }

    /**
     * Re-arms every enabled reminder. The chain only repairs itself while it is alive — each
     * firing schedules the next — so one dropped alarm silences the rest of the day.
     */
    fun rescheduleAll(context: Context) {
        val prefs = context.waterPrefs()
        if (!prefs.getBoolean("notifications_enabled", false)) return
        Reminder.parseAll(prefs.getStringSet(REMINDERS_KEY, emptySet()))
            .filter { it.enabled }
            .forEach { schedule(context, it) }
    }

    fun cancel(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context, reminder))
    }

    /**
     * A repeating reminder is bounded by the window, which is read from prefs here rather
     * than threaded through every caller. Anything else just fires at its own time.
     */
    private fun nextFireTime(context: Context, reminder: Reminder): Calendar {
        val now = Calendar.getInstance()
        if (!reminder.isInterval) return now.nextOccurrenceOf(reminder.hour, reminder.minute)

        val (windowStart, windowEnd) = context
            .waterPrefs().reminderWindow()
        return nextIntervalFireTime(
            from = now,
            anchorHour = reminder.hour,
            anchorMinute = reminder.minute,
            intervalHours = reminder.intervalHours,
            windowEndHour = windowEnd,
            windowStartHour = windowStart
        )
    }

    // Only the time label travels with the alarm — it identifies the reminder, and the
    // receiver reads everything else from prefs. Alarms pending from older versions
    // carry extra values, which are simply ignored.
    private fun buildPendingIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_time", reminder.time)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.time.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
