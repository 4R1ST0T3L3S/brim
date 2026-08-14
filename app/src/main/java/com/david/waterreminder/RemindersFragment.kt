package com.david.waterreminder

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TimePicker
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.david.waterreminder.databinding.FragmentRemindersBinding
import com.david.waterreminder.databinding.ItemReminderBinding

data class Reminder(
    val time: String,
    val hour: Int,
    val minute: Int,
    var type: String,        // one of the TYPE_* constants
    var enabled: Boolean,
    val endHour: Int = -1,      // only used by TYPE_HOURLY
    val intervalHours: Int = 0  // 0 = fires once a day; N = repeats every N hours
) {
    /** The built-in every-hour reminder, as opposed to one the user added. */
    val isHourly get() = type == TYPE_HOURLY

    /** Repeats through the day rather than firing once — "every 2 hours" and friends. */
    val isInterval get() = intervalHours > 0

    // Positional format, so a trailing field can only be added: 3 fields for daily and
    // one-time, a 4th for the hourly window, a 5th for the repeat interval. Entries
    // written by older versions still parse.
    fun toPrefsString() = when {
        intervalHours > 0 -> "$time|$type|$enabled|$endHour|$intervalHours"
        endHour >= 0 -> "$time|$type|$enabled|$endHour"
        else -> "$time|$type|$enabled"
    }

    companion object {
        const val TYPE_HOURLY = "hourly"
        const val TYPE_DAILY = "daily"
        const val TYPE_ONETIME = "onetime"

        /** An hourly reminder spanning [startHour]..[endHour]; minute is always 0. */
        private fun hourly(startHour: Int, endHour: Int, enabled: Boolean = true) = Reminder(
            time = startHour.asHourLabel(),
            hour = startHour,
            minute = 0,
            type = TYPE_HOURLY,
            enabled = enabled,
            endHour = endHour
        )

        /** The built-in hourly reminder as it ships: every hour, 08:00–20:00, on. */
        fun defaultHourly() = hourly(DEFAULT_REMINDER_START_HOUR, DEFAULT_REMINDER_END_HOUR)

        fun parseAll(saved: Set<String>?): List<Reminder> =
            saved?.mapNotNull { fromPrefsString(it) } ?: emptyList()

        /**
         * The stored hourly reminder, falling back to [defaultHourly] when there is none.
         * A missing endHour reads as the default window end; a window that is still
         * unusable (end at or before the start) reads as the whole default window, so
         * callers can trust that start < end. The on/off state is always preserved.
         */
        fun hourlyOrDefault(reminders: List<Reminder>): Reminder {
            val stored = reminders.firstOrNull { it.isHourly } ?: return defaultHourly()
            val endHour = if (stored.endHour >= 0) stored.endHour else DEFAULT_REMINDER_END_HOUR
            return if (endHour > stored.hour) stored.copy(endHour = endHour)
            else defaultHourly().copy(enabled = stored.enabled)
        }

        /**
         * [reminders] with its hourly entry replaced by one spanning [startHour]..[endHour].
         * The user's own reminders are left untouched and the hourly on/off state is kept.
         */
        fun withHourlyWindow(reminders: List<Reminder>, startHour: Int, endHour: Int): List<Reminder> =
            reminders.filterNot { it.isHourly } +
                hourly(startHour, endHour, reminders.firstOrNull { it.isHourly }?.enabled ?: true)

        fun fromPrefsString(s: String): Reminder? {
            val parts = s.split("|")
            if (parts.size < 3) return null
            val timeParts = parts[0].split(":")
            if (timeParts.size != 2) return null
            return Reminder(
                time = parts[0],
                hour = timeParts[0].toIntOrNull() ?: return null,
                minute = timeParts[1].toIntOrNull() ?: return null,
                type = parts[1],
                enabled = parts[2].toBoolean(),
                endHour = if (parts.size >= 4) parts[3].toIntOrNull() ?: -1 else -1,
                intervalHours = if (parts.size >= 5) parts[4].toIntOrNull() ?: 0 else 0
            )
        }
    }
}

class RemindersFragment : Fragment() {

    private var _binding: FragmentRemindersBinding? = null
    private val binding get() = _binding!!

    private val reminders = mutableListOf<Reminder>()

    private val prefs by lazy {
        requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
    }

    private val defaultReminders = listOf(Reminder.defaultHourly())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRemindersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowNotificationSettings.rowIcon.setImageResource(R.drawable.ic_bell)
        binding.rowNotificationSettings.rowTitle.text = getString(R.string.action_notifications)
        binding.rowNotificationSettings.rowSubtitle.text =
            getString(R.string.reminders_row_notifications_subtitle)
        binding.rowNotificationSettings.rowContainer.setOnClickListener {
            findNavController().navigate(R.id.action_RemindersFragment_to_NotificationsFragment)
        }

        binding.buttonAddReminder.setOnClickListener { showTimePickerDialog() }

        binding.buttonResetReminders.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset reminders")
                .setMessage(
                    "This will remove all current reminders and restore hourly reminders " +
                        "from ${DEFAULT_REMINDER_START_HOUR.asHourLabel()} " +
                        "to ${DEFAULT_REMINDER_END_HOUR.asHourLabel()}."
                )
                .setPositiveButton("Reset") { _, _ -> resetToDefaults() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        loadReminders()
    }

    override fun onResume() {
        super.onResume()
        // The master toggle lives in Settings → Notifications, so re-read it on the way back.
        _binding?.let { updateRemindersAlpha(prefs.getBoolean("notifications_enabled", false)) }
    }

    // ─── Load / Save ──────────────────────────────────────────────

    private fun loadReminders() {
        // The fragment instance outlives its view, so onViewCreated can run more than
        // once (e.g. returning from Notifications). Start clean or rows pile up.
        reminders.clear()

        val saved = prefs.getStringSet("reminders_v3", null)

        if (saved == null) {
            // First launch — set defaults
            defaultReminders.forEach { r ->
                reminders.add(r)
                scheduleOrCancel(r)
            }
            saveAllReminders()
        } else {
            saved.mapNotNull { Reminder.fromPrefsString(it) }
                .sortedBy { it.time }
                .forEach { reminders.add(it) }
        }

        rebuildReminderRows()
        refreshEmptyState()
    }

    private fun resetToDefaults() {
        // Cancel all existing alarms
        reminders.forEach { ReminderScheduler.cancel(requireContext(), it) }
        reminders.clear()
        // Add and schedule defaults
        defaultReminders.forEach { r ->
            reminders.add(r)
            scheduleOrCancel(r)
        }
        saveAllReminders()
        rebuildReminderRows()
        refreshEmptyState()
    }

    private fun saveAllReminders() {
        val set = reminders.map { it.toPrefsString() }.toSet()
        prefs.edit().putStringSet("reminders_v3", set).apply()
    }

    // ─── UI ───────────────────────────────────────────────────────

    private fun rebuildReminderRows() {
        binding.containerDefaultReminder.removeAllViews()
        binding.containerReminders.removeAllViews()
        reminders.sortedWith(compareBy({ !it.isHourly }, { it.time })).forEach { reminder ->
            addReminderRow(reminder)
        }
    }

    private fun addReminderRow(reminder: Reminder) {
        // Hourly is the built-in default and lives above the "My reminders" section.
        val container = if (reminder.isHourly)
            binding.containerDefaultReminder else binding.containerReminders

        val rowBinding = ItemReminderBinding.inflate(
            LayoutInflater.from(requireContext()), container, false
        )

        rowBinding.textReminderTime.text = if (reminder.isHourly) {
            "🔔  ${reminder.hour.asHourLabel()}–${reminder.endHour.asHourLabel()}"
        } else if (reminder.isInterval) {
            // The repeats stop at the go-to-bed hour, so show where they end.
            val until = prefs.reminderWindowEndHour().asHourLabel()
            "🔔  ${reminder.time}  ·  ${getString(R.string.reminder_repeats_until, until)}"
        } else {
            "🔔  ${reminder.time}"
        }
        rowBinding.switchReminderEnabled.isChecked = reminder.enabled

        // The section divider already separates the hourly row from "My reminders",
        // so drop the row's own to avoid two lines stacked together.
        if (reminder.isHourly) rowBinding.rowDivider.visibility = View.GONE

        applyTypeChip(rowBinding, reminder)

        // Tap the chip to change how often it repeats (not applicable for hourly, whose
        // window is set in Settings → Notifications).
        rowBinding.textReminderType.setOnClickListener {
            if (reminder.isHourly) return@setOnClickListener
            showTriggerDialog(reminder.type to reminder.intervalHours) { type, hours ->
                applyTrigger(reminder, type, hours)
            }
        }

        // Enable / disable switch
        rowBinding.switchReminderEnabled.setOnCheckedChangeListener { _, isChecked ->
            reminder.enabled = isChecked
            scheduleOrCancel(reminder)
            saveAllReminders()
        }

        // Swipe right = edit (daily/onetime only), swipe left = delete (daily/onetime only)
        addSwipeActions(
            foreground = rowBinding.foreground,
            editBg = rowBinding.editBackground,
            deleteBg = rowBinding.deleteBackground,
            swipeHandle = rowBinding.foreground,
            reminder = reminder,
            canEdit = !reminder.isHourly,
            canDelete = !reminder.isHourly
        )

        container.addView(rowBinding.root)
    }

    private fun applyTypeChip(rowBinding: ItemReminderBinding, reminder: Reminder) {
        rowBinding.textReminderType.text =
            if (reminder.isHourly) getString(R.string.reminder_trigger_builtin_hourly)
            else triggerLabel(reminder.type, reminder.intervalHours)
        val color = when {
            reminder.isHourly -> 0xFF009688.toInt()               // teal
            reminder.isInterval -> 0xFF9C27B0.toInt()              // purple
            reminder.type == Reminder.TYPE_ONETIME -> 0xFFFF9800.toInt()  // orange
            else -> 0xFF2196F3.toInt()                             // blue
        }
        val bg = rowBinding.textReminderType.background.mutate() as? GradientDrawable
            ?: GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 48f
            }
        bg.setColor(color)
        rowBinding.textReminderType.background = bg
    }

    private fun refreshEmptyState() {
        val hasCustom = reminders.any { !it.isHourly }
        binding.textNoReminders.visibility = if (hasCustom) View.GONE else View.VISIBLE
        // Hourly rows aren't swipeable, so the legend only makes sense alongside a custom one.
        binding.legendSwipe.visibility = if (hasCustom) View.VISIBLE else View.GONE
    }

    // ─── Add reminder dialog ──────────────────────────────────────

    private fun showTimePickerDialog() {
        val timePicker = TimePicker(requireContext()).apply { setIs24HourView(true) }

        AlertDialog.Builder(requireContext())
            .setTitle("Choose reminder time")
            .setView(timePicker)
            // "Next" rather than "Add": choosing how often it repeats comes second.
            .setPositiveButton("Next") { _, _ ->
                val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    timePicker.hour else @Suppress("DEPRECATION") timePicker.currentHour
                val minute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    timePicker.minute else @Suppress("DEPRECATION") timePicker.currentMinute
                val label = String.format("%02d:%02d", hour, minute)

                if (reminders.none { it.time == label }) {
                    showTriggerDialog(Reminder.TYPE_DAILY to 0) { type, hours ->
                        val reminder = Reminder(
                            time = label,
                            hour = hour,
                            minute = minute,
                            type = type,
                            enabled = true,
                            intervalHours = hours
                        )
                        reminders.add(reminder)
                        scheduleOrCancel(reminder)
                        saveAllReminders()
                        rebuildReminderRows()
                        refreshEmptyState()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** How often a reminder retriggers, in the order the chooser offers them. */
    private val triggers = listOf(
        Reminder.TYPE_ONETIME to 0,
        Reminder.TYPE_DAILY to 0,
        Reminder.TYPE_DAILY to 1,
        Reminder.TYPE_DAILY to 2,
        Reminder.TYPE_DAILY to 3,
        Reminder.TYPE_DAILY to 4
    )

    private fun triggerLabel(type: String, intervalHours: Int): String = when {
        intervalHours > 0 -> resources.getQuantityString(
            R.plurals.reminder_trigger_every_hours, intervalHours, intervalHours
        )
        type == Reminder.TYPE_ONETIME -> getString(R.string.reminder_trigger_onetime)
        else -> getString(R.string.reminder_trigger_daily)
    }

    /** Picking a trigger applies it and closes, as with the hour list in Notifications. */
    private fun showTriggerDialog(current: Pair<String, Int>?, onPicked: (String, Int) -> Unit) {
        val labels = triggers.map { (type, hours) -> triggerLabel(type, hours) }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_reminder_trigger_title))
            .setSingleChoiceItems(labels, triggers.indexOf(current)) { dialog, which ->
                val (type, hours) = triggers[which]
                onPicked(type, hours)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyTrigger(reminder: Reminder, type: String, intervalHours: Int) {
        val index = reminders.indexOf(reminder)
        if (index < 0) return

        ReminderScheduler.cancel(requireContext(), reminder)
        val updated = reminder.copy(type = type, intervalHours = intervalHours)
        reminders[index] = updated
        scheduleOrCancel(updated)
        saveAllReminders()
        rebuildReminderRows()
    }

    private fun showEditTimePickerDialog(reminder: Reminder) {
        val timePicker = TimePicker(requireContext()).apply {
            setIs24HourView(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hour = reminder.hour
                minute = reminder.minute
            } else {
                @Suppress("DEPRECATION") currentHour = reminder.hour
                @Suppress("DEPRECATION") currentMinute = reminder.minute
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit reminder time")
            .setView(timePicker)
            .setPositiveButton("Save") { _, _ ->
                val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    timePicker.hour else @Suppress("DEPRECATION") timePicker.currentHour
                val minute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    timePicker.minute else @Suppress("DEPRECATION") timePicker.currentMinute
                val label = String.format("%02d:%02d", hour, minute)

                // Don't allow duplicate times (excluding self)
                if (reminders.none { it !== reminder && it.time == label }) {
                    ReminderScheduler.cancel(requireContext(), reminder)
                    val index = reminders.indexOf(reminder)
                    // copy(), so the trigger and its interval survive a time change.
                    val updated = reminder.copy(time = label, hour = hour, minute = minute)
                    reminders[index] = updated
                    scheduleOrCancel(updated)
                    saveAllReminders()
                    rebuildReminderRows()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Swipe to delete ─────────────────────────────────────────

    private fun addSwipeActions(
        foreground: View,
        editBg: View,
        deleteBg: View,
        swipeHandle: View,
        reminder: Reminder,
        canEdit: Boolean = true,
        canDelete: Boolean = true
    ) {
        var startX = 0f
        var startY = 0f
        var isSwiping = false

        swipeHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    isSwiping = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val rawDeltaX = event.rawX - startX
                    val deltaY = event.rawY - startY
                    // Clamp movement to allowed directions
                    val deltaX = when {
                        rawDeltaX > 0 && !canEdit -> 0f
                        rawDeltaX < 0 && !canDelete -> 0f
                        else -> rawDeltaX
                    }
                    if (!isSwiping && Math.abs(rawDeltaX) > Math.abs(deltaY) && Math.abs(rawDeltaX) > 12) {
                        isSwiping = true
                        foreground.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (isSwiping) {
                        foreground.translationX = deltaX
                        if (deltaX > 0) {
                            editBg.visibility = View.VISIBLE
                            deleteBg.visibility = View.GONE
                        } else if (deltaX < 0) {
                            editBg.visibility = View.GONE
                            deleteBg.visibility = View.VISIBLE
                        } else {
                            editBg.visibility = View.GONE
                            deleteBg.visibility = View.GONE
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isSwiping) {
                        val deltaX = event.rawX - startX
                        val threshold = foreground.width * 0.4f
                        when {
                            deltaX > threshold -> {
                                if (canEdit) {
                                    // Swiped right → edit
                                    foreground.animate()
                                        .translationX(foreground.width.toFloat())
                                        .setDuration(200)
                                        .withEndAction {
                                            foreground.translationX = 0f
                                            editBg.visibility = View.GONE
                                            showEditTimePickerDialog(reminder)
                                        }
                                        .start()
                                } else {
                                    foreground.animate().translationX(0f).setDuration(150)
                                        .withEndAction { editBg.visibility = View.GONE }.start()
                                }
                            }
                            deltaX < -threshold -> {
                                if (canDelete) {
                                    foreground.animate()
                                        .translationX(-foreground.width.toFloat())
                                        .setDuration(200)
                                        .withEndAction { deleteReminder(reminder) }
                                        .start()
                                } else {
                                    foreground.animate().translationX(0f).setDuration(150)
                                        .withEndAction { deleteBg.visibility = View.GONE }.start()
                                }
                            }
                            else -> {
                                // Snap back
                                foreground.animate().translationX(0f).setDuration(150)
                                    .withEndAction {
                                        editBg.visibility = View.GONE
                                        deleteBg.visibility = View.GONE
                                    }.start()
                            }
                        }
                        foreground.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun deleteReminder(reminder: Reminder) {
        ReminderScheduler.cancel(requireContext(), reminder)
        reminders.remove(reminder)
        saveAllReminders()
        rebuildReminderRows()
        refreshEmptyState()
    }

    // ─── Alarm scheduling ────────────────────────────────────────

    fun scheduleOrCancel(reminder: Reminder) {
        if (reminder.enabled) {
            checkExactAlarmPermissionAndSchedule(reminder)
        } else {
            ReminderScheduler.cancel(requireContext(), reminder)
        }
    }

    private fun checkExactAlarmPermissionAndSchedule(reminder: Reminder) {
        if (!ensureExactAlarmPermission()) return
        ReminderScheduler.schedule(requireContext(), reminder)
    }

    // Dimmed while the master toggle in Settings → Notifications is off.
    private fun updateRemindersAlpha(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.38f
        binding.containerDefaultReminder.alpha = alpha
        binding.containerReminders.alpha = alpha
        binding.textNoReminders.alpha = alpha
        binding.legendSwipe.alpha = alpha
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
