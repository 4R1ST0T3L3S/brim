package com.david.waterreminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.david.waterreminder.databinding.DialogHourPickerBinding
import com.david.waterreminder.databinding.DialogReminderWindowBinding
import com.david.waterreminder.databinding.FragmentNotificationsBinding

class NotificationsFragment : BaseMenuFragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private val prefs by lazy {
        requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) enableNotifications()
        _binding?.let { refreshRows() }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowEnableNotifications.rowIcon.setImageResource(R.drawable.ic_bell)
        binding.rowEnableNotifications.rowTitle.text =
            getString(R.string.settings_row_enable_notifications)
        binding.rowEnableNotifications.rowChevron.visibility = View.GONE
        binding.rowEnableNotifications.rowContainer.setOnClickListener {
            if (prefs.getBoolean("notifications_enabled", false)) {
                disableNotifications()
                refreshRows()
            } else {
                // May be async (permission prompt) — the launcher refreshes on its way back.
                requestNotificationPermission()
            }
        }

        binding.rowStopOnGoal.rowIcon.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
        binding.rowStopOnGoal.rowTitle.text = getString(R.string.settings_row_stop_on_goal)
        binding.rowStopOnGoal.rowChevron.visibility = View.GONE
        binding.rowStopOnGoal.rowContainer.setOnClickListener {
            val current = prefs.getBoolean("stop_notifications_on_goal", false)
            prefs.edit().putBoolean("stop_notifications_on_goal", !current).apply()
            refreshRows()
        }

        binding.rowReminderWindow.rowIcon.setImageResource(R.drawable.ic_clock)
        binding.rowReminderWindow.rowTitle.text = getString(R.string.settings_row_reminder_window)
        binding.rowReminderWindow.rowContainer.setOnClickListener { showReminderWindowDialog() }

        refreshRows()
    }

    override fun onResume() {
        super.onResume()
        // The exact-alarm screen is a system Activity — re-read on the way back.
        _binding?.let { refreshRows() }
        // Self-heal: saving a window cancels the old alarm before scheduling the new
        // one, so a save that stopped at the exact-alarm prompt leaves nothing pending.
        // schedule() no-ops while the permission is still missing, so this is safe to
        // run on every resume, and rescheduling an existing alarm is idempotent.
        if (prefs.getBoolean("notifications_enabled", false)) rescheduleEnabledReminders()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─── UI refresh ───────────────────────────────────────────────

    private fun refreshRows() {
        val enabled = prefs.getBoolean("notifications_enabled", false)
        binding.rowEnableNotifications.rowSubtitle.text =
            if (enabled) getString(R.string.settings_notifications_enabled)
            else getString(R.string.settings_notifications_disabled)

        binding.rowStopOnGoal.rowSubtitle.text =
            if (prefs.getBoolean("stop_notifications_on_goal", false))
                getString(R.string.settings_stop_on_goal_enabled)
            else getString(R.string.settings_stop_on_goal_disabled)

        // Pausing on goal is meaningless while notifications are off.
        binding.rowStopOnGoal.root.alpha = if (enabled) 1f else 0.38f
        binding.rowStopOnGoal.rowContainer.isEnabled = enabled

        val (windowStart, windowEnd) = prefs.reminderWindow()
        binding.rowReminderWindow.rowSubtitle.text = getString(
            R.string.settings_reminder_window_value,
            windowStart.asHourLabel(),
            windowEnd.asHourLabel()
        )
    }

    // ─── Reminder window ─────────────────────────────────────────

    private fun showReminderWindowDialog() {
        var (startHour, endHour) = prefs.reminderWindow()

        val dialogBinding = DialogReminderWindowBinding.inflate(layoutInflater)

        dialogBinding.buttonResetWindow.text = getString(
            R.string.dialog_reminder_window_reset,
            DEFAULT_REMINDER_START_HOUR.asHourLabel(),
            DEFAULT_REMINDER_END_HOUR.asHourLabel()
        )

        // Each phrase lives in one string only: it labels the button and titles the
        // hour list opened from it, so the two can never drift apart.
        fun getUpLabel() = getString(R.string.reminder_window_get_up, startHour.asHourLabel())
        fun goToBedLabel() = getString(R.string.reminder_window_go_to_bed, endHour.asHourLabel())

        fun refreshDialog() {
            dialogBinding.buttonGetUp.text = getUpLabel()
            dialogBinding.buttonGoToBed.text = goToBedLabel()
            // Nothing to reset while the pickers already sit on the defaults. Kept
            // INVISIBLE rather than GONE so the dialog doesn't resize under the pointer
            // as the hours are edited.
            val isDefault = startHour == DEFAULT_REMINDER_START_HOUR &&
                endHour == DEFAULT_REMINDER_END_HOUR
            dialogBinding.buttonResetWindow.visibility =
                if (isDefault) View.INVISIBLE else View.VISIBLE
        }
        refreshDialog()

        // Titled with the button's own label, so it's clear which end is being set.
        dialogBinding.buttonGetUp.setOnClickListener {
            showHourPicker(getUpLabel(), startHour) { startHour = it; refreshDialog() }
        }
        dialogBinding.buttonGoToBed.setOnClickListener {
            showHourPicker(goToBedLabel(), endHour) { endHour = it; refreshDialog() }
        }

        // Puts the pickers back to 08:00–20:00; still takes a Save to apply, so it
        // stays undoable with Cancel.
        dialogBinding.buttonResetWindow.setOnClickListener {
            startHour = DEFAULT_REMINDER_START_HOUR
            endHour = DEFAULT_REMINDER_END_HOUR
            refreshDialog()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_reminder_window_title))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.dialog_save), null)
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .create()

        // Save is wired after show() so an invalid window keeps the dialog open
        // instead of dismissing and throwing away the picked hours.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // The hour pickers are unrestricted, so the window is validated here.
                if (startHour >= endHour) {
                    showWindowError(getString(R.string.reminder_setup_error_window))
                    return@setOnClickListener
                }
                saveReminderWindow(startHour, endHour)
                refreshRows()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // Reminders always fire at the top of the hour, so there is no minute to choose: an
    // hours-only wheel instead of a TimePicker, whose minute column can't be hidden
    // through public API and whose value would be discarded anyway.
    // displayedValues (rather than setFormatter) so the labels render before any scroll.
    private fun showHourPicker(title: String, initialHour: Int, onPicked: (Int) -> Unit) {
        val pickerBinding = DialogHourPickerBinding.inflate(layoutInflater)
        val picker = pickerBinding.pickerHour.apply {
            minValue = 0
            maxValue = 23
            displayedValues = Array(24) { it.asHourLabel() }
            value = initialHour
            wrapSelectorWheel = true
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(pickerBinding.root)
            .setPositiveButton(getString(R.string.dialog_save)) { _, _ -> onPicked(picker.value) }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun showWindowError(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.reminder_setup_error_window_title))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * No reminder is rewritten and no alarm cancelled — the window is its own pref, and
     * alarms are keyed by time labels that don't change. Every repeating reminder is
     * bounded by the window, so they all get rescheduled onto their new fire times.
     */
    private fun saveReminderWindow(startHour: Int, endHour: Int) {
        prefs.setReminderWindow(startHour, endHour)

        if (prefs.getBoolean("notifications_enabled", false)) {
            // onResume reschedules again once a deferred exact-alarm grant comes back.
            if (!ensureExactAlarmPermission()) return
            rescheduleEnabledReminders()
        }
    }

    // ─── Notifications ────────────────────────────────────────────

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    enableNotifications()
                    refreshRows()
                }
                else -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            enableNotifications()
            refreshRows()
        }
    }

    private fun enableNotifications() {
        prefs.edit().putBoolean("notifications_enabled", true).apply()
        if (!ensureExactAlarmPermission()) return
        rescheduleEnabledReminders()
    }

    private fun disableNotifications() {
        // Alarms keep firing and rescheduling; ReminderReceiver checks this flag before posting.
        prefs.edit().putBoolean("notifications_enabled", false).apply()
    }

    private fun rescheduleEnabledReminders() {
        prefs.getStringSet("reminders_v3", emptySet())
            ?.mapNotNull { Reminder.fromPrefsString(it) }
            ?.filter { it.enabled }
            ?.forEach { ReminderScheduler.schedule(requireContext(), it) }
    }
}
