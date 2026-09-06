package com.david.waterreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.waterPrefs()
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
        val saved = Reminder.parseAll(prefs.getStringSet(REMINDERS_KEY, emptySet()))
        val reminder = saved.firstOrNull { it.time == reminderTime } ?: return

        // All of the rescheduling first, so the chain never breaks regardless of whether
        // anything is allowed on screen — alarms keep running while notifications are off,
        // and ReminderReceiver checks the toggle before posting. The piggyback repairs the
        // rest of the chain, so a dropped alarm is recovered on the next firing rather than
        // waiting for a reboot.
        if (reminder.enabled && !reminder.isOneTime) ReminderScheduler.schedule(context, reminder)
        saved.filter { it !== reminder && it.enabled && !it.isOneTime }
            .forEach { ReminderScheduler.schedule(context, it) }

        // Retired before the notification decision, not after: this delivery spends the
        // alarm either way and nothing above reschedules a one-time, so leaving it enabled
        // would show it as on with nothing behind it — and re-arm it for the wrong day.
        if (reminder.isOneTime) {
            val updated = saved.map { if (it === reminder) it.copy(enabled = false) else it }
            prefs.edit()
                .putStringSet(REMINDERS_KEY, updated.map { it.toPrefsString() }.toSet())
                .apply()
        }

        // A late delivery is rescheduled above like any other fire; only the notification for
        // this stale one is skipped.
        postReminderNotification(context, prefs, reminder)
    }
}
