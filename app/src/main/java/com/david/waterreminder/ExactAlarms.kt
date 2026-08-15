package com.david.waterreminder

import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

/**
 * Reminders need the exact-alarm permission to fire while the app is closed —
 * without it ReminderScheduler drops the alarm silently.
 *
 * Returns true when scheduling can go ahead. Otherwise prompts for the
 * permission and returns false, so callers should bail out.
 */
fun Fragment.ensureExactAlarmPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true

    val alarmManager = requireContext().getSystemService(AlarmManager::class.java)
    if (alarmManager.canScheduleExactAlarms()) return true

    AlertDialog.Builder(requireContext())
        .setTitle(getString(R.string.dialog_exact_alarm_title))
        .setMessage(getString(R.string.dialog_exact_alarm_message))
        .setPositiveButton(getString(R.string.dialog_exact_alarm_open_settings)) { _, _ ->
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
        .setNegativeButton(getString(R.string.dialog_cancel), null)
        .show()
    return false
}
