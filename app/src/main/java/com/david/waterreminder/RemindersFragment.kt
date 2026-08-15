package com.david.waterreminder

import android.content.Context
import android.content.SharedPreferences
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
    val intervalHours: Int = 0  // 0 = fires once a day; N = repeats every N hours
) {
    /** Repeats through the day rather than firing once — "every 2 hours" and friends. */
    val isInterval get() = intervalHours > 0

    /** Fires once and stays off, so it is never rescheduled. */
    val isOneTime get() = type == TYPE_ONETIME

    // Positional format, so a trailing field can only be added: 3 fields, plus a 5th for
    // the repeat interval. Field 4 held the old hourly reminder's window end — written as
    // a placeholder and ignored on read, so older entries still parse.
    fun toPrefsString() =
        if (intervalHours > 0) "$time|$type|$enabled|-1|$intervalHours"
        else "$time|$type|$enabled"

    companion object {
        const val TYPE_DAILY = "daily"
        const val TYPE_ONETIME = "onetime"

        /** Retired in favour of a plain every-1-hour reminder; still read from old prefs. */
        private const val LEGACY_TYPE_HOURLY = "hourly"

        /** What a new install starts with and Reset puts back — an ordinary reminder. */
        fun defaultReminder(startHour: Int) = Reminder(
            time = startHour.asHourLabel(),
            hour = startHour,
            minute = 0,
            type = TYPE_DAILY,
            enabled = true,
            intervalHours = 1
        )

        fun parseAll(saved: Set<String>?): List<Reminder> =
            saved?.mapNotNull { fromPrefsString(it) } ?: emptyList()

        fun fromPrefsString(s: String): Reminder? {
            val parts = s.split("|")
            if (parts.size < 3) return null
            val timeParts = parts[0].split(":")
            if (timeParts.size != 2) return null
            // An old hourly entry reads as what it always was: every 1 hour.
            val legacyHourly = parts[1] == LEGACY_TYPE_HOURLY
            return Reminder(
                time = parts[0],
                hour = timeParts[0].toIntOrNull() ?: return null,
                minute = timeParts[1].toIntOrNull() ?: return null,
                type = if (legacyHourly) TYPE_DAILY else parts[1],
                enabled = parts[2].toBoolean(),
                intervalHours = if (legacyHourly) 1 else parts.getOrNull(4)?.toIntOrNull() ?: 0
            )
        }

        /** The window an old hourly entry carried, or null if there is none. */
        fun legacyHourlyWindow(saved: Set<String>?): Pair<Int, Int>? {
            val legacy = saved.orEmpty().map { it.split("|") }
                .firstOrNull { it.size >= 4 && it[1] == LEGACY_TYPE_HOURLY } ?: return null
            val start = legacy[0].substringBefore(":").toIntOrNull() ?: return null
            return start to (legacy[3].toIntOrNull() ?: return null)
        }

        /**
         * Rescues the window an old install kept on its hourly reminder. The entry itself
         * needs no rewriting — [fromPrefsString] already reads it correctly, and its alarm
         * is keyed by a time label that hasn't changed.
         */
        fun migrateLegacyHourly(prefs: SharedPreferences) {
            if (prefs.contains(PREF_WINDOW_END_HOUR)) return
            legacyHourlyWindow(prefs.getStringSet("reminders_v3", null))
                ?.let { (start, end) -> prefs.setReminderWindow(start, end) }
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
            val (start, end) = prefs.reminderWindow()
            AlertDialog.Builder(requireContext())
                .setTitle("Reset reminders")
                .setMessage(
                    "This will remove all current reminders and restore the default one: " +
                        "every hour from ${start.asHourLabel()} to ${end.asHourLabel()}."
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
            addDefaultReminder()   // First launch
        } else {
            saved.mapNotNull { Reminder.fromPrefsString(it) }.forEach { reminders.add(it) }
        }

        rebuildReminderRows()
        refreshEmptyState()
    }

    private fun resetToDefaults() {
        reminders.forEach { ReminderScheduler.cancel(requireContext(), it) }
        reminders.clear()
        addDefaultReminder()
        rebuildReminderRows()
        refreshEmptyState()
    }

    /** Adds, schedules and saves the default reminder — the caller only redraws. */
    private fun addDefaultReminder() {
        val default = Reminder.defaultReminder(prefs.reminderWindow().first)
        reminders.add(default)
        scheduleOrCancel(default)
        saveAllReminders()
    }

    private fun saveAllReminders() {
        val set = reminders.map { it.toPrefsString() }.toSet()
        prefs.edit().putStringSet("reminders_v3", set).apply()
    }

    // ─── UI ───────────────────────────────────────────────────────

    private fun rebuildReminderRows() {
        binding.containerReminders.removeAllViews()
        reminders.sortedBy { it.time }.forEach { addReminderRow(it) }
    }

    private fun addReminderRow(reminder: Reminder) {
        val container = binding.containerReminders

        val rowBinding = ItemReminderBinding.inflate(
            LayoutInflater.from(requireContext()), container, false
        )

        // A repeating reminder reads as a range, ending at the go-to-bed hour.
        rowBinding.textReminderTime.text =
            if (reminder.isInterval)
                "🔔  ${reminder.time}–${prefs.reminderWindow().second.asHourLabel()}"
            else "🔔  ${reminder.time}"
        rowBinding.switchReminderEnabled.isChecked = reminder.enabled

        applyTypeChip(rowBinding, reminder)

        rowBinding.textReminderType.setOnClickListener {
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

        // Swipe right = edit, swipe left = delete
        addSwipeActions(
            foreground = rowBinding.foreground,
            editBg = rowBinding.editBackground,
            deleteBg = rowBinding.deleteBackground,
            swipeHandle = rowBinding.foreground,
            reminder = reminder
        )

        container.addView(rowBinding.root)
    }

    private fun applyTypeChip(rowBinding: ItemReminderBinding, reminder: Reminder) {
        rowBinding.textReminderType.text = triggerLabel(reminder.type, reminder.intervalHours)
        val color = when {
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
        val hasReminders = reminders.isNotEmpty()
        binding.textNoReminders.visibility = if (hasReminders) View.GONE else View.VISIBLE
        // The legend describes row gestures — nothing to describe without a row.
        binding.legendSwipe.visibility = if (hasReminders) View.VISIBLE else View.GONE
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
        reminder: Reminder
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
                    val deltaX = event.rawX - startX
                    val deltaY = event.rawY - startY
                    if (!isSwiping && Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 12) {
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
                            }
                            deltaX < -threshold -> {
                                foreground.animate()
                                    .translationX(-foreground.width.toFloat())
                                    .setDuration(200)
                                    .withEndAction { deleteReminder(reminder) }
                                    .start()
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
        binding.containerReminders.alpha = alpha
        binding.textNoReminders.alpha = alpha
        binding.legendSwipe.alpha = alpha
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
