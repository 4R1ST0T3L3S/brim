package com.david.waterreminder

import android.app.backup.BackupManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Logs the amount behind a quick-log button on a reminder notification — the path that makes
 * the buttons usable from a paired watch, where there is no app to open.
 *
 * Kept to a few prefs writes on the main thread: a receiver has about ten seconds, and
 * `goAsync()` here would move the write off the main thread and quietly break the assumption
 * the home screen's live refresh rests on. See [logIntake].
 */
class QuickLogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ml = intent.getIntExtra(EXTRA_LOG_ML, 0)
        if (ml <= 0) return
        val reminderTime = intent.getStringExtra(EXTRA_LOG_REMINDER_TIME) ?: return

        val prefs = context.waterPrefs()
        prefs.logIntake(ml)
        // MainActivity's listener does this while the app is on screen, but this can land with
        // the app in the background or not running at all.
        BackupManager(context).dataChanged()

        // Crossing the goal from here is deliberately quiet: the congratulation is a dialog and
        // there is no screen to put it on. The card changing to its goal-reached wording — or
        // being taken away — is the feedback, and the streak is waiting in the app.
        refreshReminderNotification(context, prefs, reminderTime)
    }
}
