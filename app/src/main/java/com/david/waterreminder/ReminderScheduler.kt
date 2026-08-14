package com.david.waterreminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object ReminderScheduler {

    fun schedule(context: Context, reminder: Reminder) {
        if (reminder.isHourly) {
            scheduleHourly(context, reminder)
            return
        }
        if (reminder.isInterval) {
            scheduleInterval(context, reminder)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(Calendar.getInstance())) add(Calendar.DAY_OF_MONTH, 1)
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            buildPendingIntent(context, reminder)
        )
    }

    fun cancel(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context, reminder))
    }

    /**
     * Interval reminders repeat from their own start time, so — unlike the hourly one —
     * the window only bounds them at the top end. That end hour lives with the hourly
     * reminder in prefs, so it is read here rather than threaded through every caller.
     */
    private fun scheduleInterval(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return

        val prefs = context.getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
        val fireAt = nextIntervalFireTime(
            from = Calendar.getInstance(),
            anchorHour = reminder.hour,
            anchorMinute = reminder.minute,
            intervalHours = reminder.intervalHours,
            windowEndHour = prefs.reminderWindowEndHour()
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            fireAt.timeInMillis,
            buildPendingIntent(context, reminder)
        )
    }

    private fun scheduleHourly(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return

        val startHour = reminder.hour
        val endHour = reminder.endHour
        val now = Calendar.getInstance()

        // Next top-of-hour from now
        val candidate = Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, 1)
        }
        val candidateHour = candidate.get(Calendar.HOUR_OF_DAY)

        val fireCal = if (candidateHour in startHour..endHour) {
            candidate
        } else {
            // Outside window — use startHour today (or tomorrow if already past)
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, startHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (!after(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            fireCal.timeInMillis,
            buildPendingIntent(context, reminder)
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
