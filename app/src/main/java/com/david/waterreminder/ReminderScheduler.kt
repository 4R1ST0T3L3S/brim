package com.david.waterreminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object ReminderScheduler {

    fun schedule(context: Context, reminder: Reminder) {
        if (reminder.type == "hourly") {
            scheduleHourly(context, reminder)
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

    fun rescheduleNextDay(context: Context, reminderTime: String, reminderType: String) {
        val parts = reminderTime.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        val reminder = Reminder(reminderTime, hour, minute, reminderType, true)
        schedule(context, reminder)
    }

    fun scheduleHourly(context: Context, reminder: Reminder) {
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

    fun rescheduleHourly(context: Context, reminderTime: String, startHour: Int, endHour: Int, type: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val fireCal = Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (currentHour < endHour) {
                set(Calendar.HOUR_OF_DAY, currentHour + 1)
            } else {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, startHour)
            }
        }

        val reminder = Reminder(reminderTime, startHour, 0, type, true, endHour)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            fireCal.timeInMillis,
            buildPendingIntent(context, reminder)
        )
    }

    private fun buildPendingIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_time", reminder.time)
            putExtra("reminder_type", reminder.type)
            if (reminder.type == "hourly") {
                putExtra("reminder_start_hour", reminder.hour)
                putExtra("reminder_end_hour", reminder.endHour)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.time.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
