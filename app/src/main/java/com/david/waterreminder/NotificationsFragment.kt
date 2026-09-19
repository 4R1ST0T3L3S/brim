package com.david.waterreminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.david.waterreminder.databinding.DialogPickListBinding
import com.david.waterreminder.databinding.DialogReminderWindowBinding
import com.david.waterreminder.databinding.FragmentNotificationsBinding
import com.david.waterreminder.databinding.ItemPickAmountBinding

class NotificationsFragment : BaseMenuFragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private val prefs by lazy {
        requireContext().waterPrefs()
    }
    private val unit get() = prefs.getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML

    /** The clip the picker is currently previewing, kept so the next tap can cut it off. */
    private var previewRingtone: Ringtone? = null

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

        // The two boolean rows swap the chevron for a switch; the shared row layout ships
        // with the switch hidden.
        listOf(binding.rowEnableNotifications, binding.rowShowAfterGoal).forEach { row ->
            row.rowSwitch.visibility = View.VISIBLE
            row.rowChevron.visibility = View.GONE
        }

        binding.rowEnableNotifications.rowIcon.setImageResource(R.drawable.ic_bell)
        binding.rowEnableNotifications.rowTitle.text =
            getString(R.string.settings_row_enable_notifications)
        binding.rowEnableNotifications.rowSubtitle.text =
            getString(R.string.settings_notifications_subtitle)
        binding.rowEnableNotifications.rowContainer.setOnClickListener {
            if (prefs.getBoolean("notifications_enabled", false)) {
                disableNotifications()
                refreshRows()
            } else {
                // May be async (permission prompt) — the launcher refreshes on its way back,
                // and refreshRows puts the switch back if the prompt was denied.
                requestNotificationPermission()
            }
        }

        binding.rowShowAfterGoal.rowIcon.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
        binding.rowShowAfterGoal.rowTitle.text = getString(R.string.settings_row_show_after_goal)
        binding.rowShowAfterGoal.rowSubtitle.text =
            getString(R.string.settings_show_after_goal_subtitle)
        binding.rowShowAfterGoal.rowContainer.setOnClickListener {
            prefs.setShowNotificationsAfterGoal(!prefs.showNotificationsAfterGoal())
            refreshRows()
        }

        binding.rowIdleReminder.rowIcon.setImageResource(R.drawable.ic_clock)
        binding.rowIdleReminder.rowTitle.text = getString(R.string.settings_row_idle_reminder)
        binding.rowIdleReminder.rowContainer.setOnClickListener { showIdleReminderDialog() }

        binding.rowQuickLogActions.rowIcon.setImageResource(R.drawable.ic_water_drop)
        binding.rowQuickLogActions.rowTitle.text =
            getString(R.string.settings_row_quick_log_actions)
        binding.rowQuickLogActions.rowContainer.setOnClickListener { showQuickLogActionsDialog() }

        binding.rowReminderSound.rowIcon.setImageResource(R.drawable.ic_sound)
        binding.rowReminderSound.rowTitle.text = getString(R.string.settings_row_reminder_sound)
        binding.rowReminderSound.rowContainer.setOnClickListener { showReminderSoundDialog() }

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

    override fun onPause() {
        super.onPause()
        // The dialog's own dismiss covers Save, Cancel and back; this covers leaving the screen
        // or the app with it still open, where a long "System default" ringtone would otherwise
        // carry on playing over whatever the user went to.
        stopPreview()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPreview()
        _binding = null
    }

    // ─── UI refresh ───────────────────────────────────────────────

    private fun refreshRows() {
        val enabled = prefs.getBoolean("notifications_enabled", false)
        // The switches carry no listener of their own — the row owns the tap — so the state
        // can be written straight in without detaching anything first.
        binding.rowEnableNotifications.rowSwitch.isChecked = enabled

        binding.rowShowAfterGoal.rowSwitch.isChecked = prefs.showNotificationsAfterGoal()

        // What happens at the goal is moot while notifications are off.
        binding.rowShowAfterGoal.root.alpha = if (enabled) 1f else 0.38f
        binding.rowShowAfterGoal.rowContainer.isEnabled = enabled
        binding.rowShowAfterGoal.rowSwitch.isEnabled = enabled

        val idleMinutes = prefs.idleReminderMinutes()
        binding.rowIdleReminder.rowSubtitle.text =
            if (idleMinutes <= 0) getString(R.string.settings_idle_reminder_off)
            else getString(R.string.settings_idle_reminder_value, idleReminderLabel(idleMinutes))
        binding.rowIdleReminder.root.alpha = if (enabled) 1f else 0.38f
        binding.rowIdleReminder.rowContainer.isEnabled = enabled

        val actions = prefs.notificationAmounts()
        val unit = unit
        binding.rowQuickLogActions.rowSubtitle.text =
            if (actions.isEmpty()) getString(R.string.settings_quick_log_actions_off)
            else actions.joinToString(", ") { it.fmt(unit) }
        // Not dimmed with the rows above: this selection is also the widget's buttons, and
        // gating it would leave a widget-only user unable to reach the one control over them.

        binding.rowReminderSound.rowSubtitle.text = getString(prefs.reminderSound().label)
        // Dimmed with the rows above, unlike the quick-log buttons: a sound is only ever heard
        // on a notification, so it has nothing to say while notifications are off.
        binding.rowReminderSound.root.alpha = if (enabled) 1f else 0.38f
        binding.rowReminderSound.rowContainer.isEnabled = enabled

        val (windowStart, windowEnd) = prefs.reminderWindow()
        binding.rowReminderWindow.rowSubtitle.text = getString(
            R.string.settings_reminder_window_value,
            windowStart.asHourLabel(),
            windowEnd.asHourLabel()
        )
    }

    // ─── Idle reminder ───────────────────────────────────────────

    /** "Off", "90 minutes", "2 hours" — whole hours read as hours, everything else as minutes. */
    private fun idleReminderLabel(minutes: Int): String =
        if (minutes <= 0) getString(R.string.dialog_idle_reminder_off)
        else requireContext().durationLabel(minutes)

    /**
     * Picks how long a gap earns a nudge. Applies on pick and closes, as the trigger and hour
     * lists elsewhere do — there is one value and nothing to confirm.
     */
    private fun showIdleReminderDialog() {
        val labels = IDLE_REMINDER_CHOICES_MINUTES.map { idleReminderLabel(it) }.toTypedArray()
        val current = idleReminderChoiceIndex(prefs.idleReminderMinutes())

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_idle_reminder_title))
            .setSingleChoiceItems(labels, current) { dialog, which ->
                saveIdleReminder(IDLE_REMINDER_CHOICES_MINUTES[which])
                refreshRows()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    /**
     * Switching it off takes the alarm and any card already in the shade away. Switching it on
     * needs the same exact-alarm permission the reminders do; onResume reschedules once a
     * deferred grant comes back, so a prompt left unanswered costs nothing but the delay.
     */
    private fun saveIdleReminder(minutes: Int) {
        prefs.setIdleReminderMinutes(minutes)

        if (minutes <= 0) {
            IdleReminderScheduler.cancel(requireContext())
            cancelIdleReminderNotification(requireContext())
            return
        }
        if (!prefs.getBoolean("notifications_enabled", false)) return
        if (!ensureExactAlarmPermission()) return
        IdleReminderScheduler.schedule(requireContext())
    }

    // ─── Quick log buttons ───────────────────────────────────────

    /**
     * Picks which quick amounts get a quick-log button — on the reminder notification and on the
     * home-screen widget, which share one selection. The list is the home screen's own amounts,
     * so there is nothing new to invent here and nothing that can name an amount the user no
     * longer has.
     */
    private fun showQuickLogActionsDialog() {
        // One snapshot of what the home screen offers, shared by the rows, the seeded selection
        // and the save — rather than three separate reads that could disagree.
        val available = prefs.quickAmounts()
        if (available.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_quick_log_actions_title))
                .setMessage(getString(R.string.dialog_quick_log_actions_empty))
                .setPositiveButton(getString(R.string.dialog_ok), null)
                .show()
            return
        }

        val dialogBinding = DialogPickListBinding.inflate(layoutInflater)
        dialogBinding.textHint.text =
            getString(R.string.dialog_quick_log_actions_hint, NOTIFICATION_ACTIONS_MAX)

        val unit = unit
        var selection = prefs.notificationAmounts()

        val rows = available.map { ml ->
            ml to ItemPickAmountBinding.inflate(layoutInflater, dialogBinding.containerItems, true)
                .also { it.textAmount.text = ml.fmt(unit) }
        }

        // Every row redraws on every tap, not just the one tapped: checking a fourth amount
        // frees its slot by unchecking whichever has been checked longest.
        fun redraw() = rows.forEach { (ml, row) -> row.checkAmount.isChecked = ml in selection }
        redraw()

        rows.forEach { (ml, row) ->
            row.root.setOnClickListener {
                selection = selection.toggleNotificationAmount(ml)
                redraw()
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_quick_log_actions_title))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.dialog_save)) { _, _ ->
                prefs.setNotificationAmounts(selection)
                refreshRows()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    // ─── Reminder sound ──────────────────────────────────────────

    /**
     * Picks what a reminder sounds like. Every tap previews the clip, so unlike the other pick
     * lists here this one waits for Save: tapping down the list to hear them all would
     * otherwise apply and close on the first one.
     */
    private fun showReminderSoundDialog() {
        val sounds = ReminderSound.values()
        var selected = prefs.reminderSound()

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_reminder_sound_title))
            .setSingleChoiceItems(
                sounds.map { getString(it.label) }.toTypedArray(),
                selected.ordinal,
            ) { _, which ->
                selected = sounds[which]
                playPreview(selected)
            }
            .setPositiveButton(getString(R.string.dialog_save)) { _, _ ->
                saveReminderSound(selected)
                refreshRows()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .create()

        // Covers Save, Cancel and the back button alike — a clip started by the last tap must
        // not carry on playing over the settings screen.
        dialog.setOnDismissListener { stopPreview() }
        dialog.show()
    }

    /**
     * The channels are switched here rather than left to the next reminder to do, so the
     * system's own notification settings already show the chosen sound by the time the dialog
     * closes.
     */
    private fun saveReminderSound(sound: ReminderSound) {
        prefs.setReminderSound(sound)
        sound.selectChannel(requireContext())
    }

    /**
     * Plays a choice the way a reminder would, down to the audio attributes: the volume being
     * judged here has to be the notification volume it will actually arrive at.
     */
    private fun playPreview(sound: ReminderSound) {
        stopPreview()
        val uri = sound.uri(requireContext())
        previewRingtone = RingtoneManager.getRingtone(requireContext(), uri)?.apply {
            audioAttributes = NOTIFICATION_AUDIO_ATTRIBUTES
            play()
        }
    }

    private fun stopPreview() {
        previewRingtone?.stop()
        previewRingtone = null
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
                    showWindowError()
                    return@setOnClickListener
                }
                saveReminderWindow(startHour, endHour)
                refreshRows()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /**
     * No reminder is rewritten and no alarm cancelled — the window is its own pref, and
     * alarms are keyed by time labels that don't change. Every repeating reminder is
     * bounded by the window, so they all get rescheduled onto their new fire times.
     */
    private fun saveReminderWindow(startHour: Int, endHour: Int) {
        prefs.setReminderWindow(startHour, endHour)
        // The idle alarm is bounded by the window too, and unlike the reminders it is one alarm
        // whose fire time the new hours can move on their own.
        IdleReminderScheduler.schedule(requireContext())

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
        // The idle alarm is the exception: there is nothing to keep it warm, so it is dropped
        // outright and rebuilt by enableNotifications. Its card goes with it.
        IdleReminderScheduler.cancel(requireContext())
        cancelIdleReminderNotification(requireContext())
    }

    private fun rescheduleEnabledReminders() = rescheduleAllAlarms(requireContext())
}
