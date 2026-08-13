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
    var type: String,    // "daily", "onetime", or "hourly"
    var enabled: Boolean,
    val endHour: Int = -1   // only used for "hourly" type
) {
    fun toPrefsString() =
        if (endHour >= 0) "$time|$type|$enabled|$endHour" else "$time|$type|$enabled"

    companion object {
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
                endHour = if (parts.size >= 4) parts[3].toIntOrNull() ?: -1 else -1
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

    private val defaultReminders = listOf(
        Reminder("08:00", 8, 0, "hourly", true, endHour = 20)
    )

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
                .setMessage("This will remove all current reminders and restore hourly reminders from 08:00 to 20:00.")
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
        reminders.sortedWith(compareBy({ it.type != "hourly" }, { it.time })).forEach { reminder ->
            addReminderRow(reminder)
        }
    }

    private fun addReminderRow(reminder: Reminder) {
        // Hourly is the built-in default and lives above the "My reminders" section.
        val container = if (reminder.type == "hourly")
            binding.containerDefaultReminder else binding.containerReminders

        val rowBinding = ItemReminderBinding.inflate(
            LayoutInflater.from(requireContext()), container, false
        )

        rowBinding.textReminderTime.text = if (reminder.type == "hourly") {
            "🔔  ${String.format("%02d:00", reminder.hour)}–${String.format("%02d:00", reminder.endHour)}"
        } else {
            "🔔  ${reminder.time}"
        }
        rowBinding.switchReminderEnabled.isChecked = reminder.enabled

        // The section divider already separates the hourly row from "My reminders",
        // so drop the row's own to avoid two lines stacked together.
        if (reminder.type == "hourly") rowBinding.rowDivider.visibility = View.GONE

        applyTypeChip(rowBinding, reminder.type)

        // Toggle daily / one-time (not applicable for hourly)
        rowBinding.textReminderType.setOnClickListener {
            if (reminder.type == "hourly") return@setOnClickListener
            reminder.type = if (reminder.type == "daily") "onetime" else "daily"
            applyTypeChip(rowBinding, reminder.type)
            scheduleOrCancel(reminder)
            saveAllReminders()
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
            canEdit = reminder.type != "hourly",
            canDelete = reminder.type != "hourly"
        )

        container.addView(rowBinding.root)
    }

    private fun applyTypeChip(rowBinding: ItemReminderBinding, type: String) {
        rowBinding.textReminderType.text = when (type) {
            "hourly"  -> "Hourly"
            "onetime" -> "One-time"
            else      -> "Daily"
        }
        val color = when (type) {
            "hourly"  -> 0xFF009688.toInt()   // teal
            "onetime" -> 0xFFFF9800.toInt()   // orange
            else      -> 0xFF2196F3.toInt()   // blue
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
        val hasCustom = reminders.any { it.type != "hourly" }
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
            .setPositiveButton("Add") { _, _ ->
                val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    timePicker.hour else @Suppress("DEPRECATION") timePicker.currentHour
                val minute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    timePicker.minute else @Suppress("DEPRECATION") timePicker.currentMinute
                val label = String.format("%02d:%02d", hour, minute)

                if (reminders.none { it.time == label }) {
                    val reminder = Reminder(label, hour, minute, "daily", true)
                    reminders.add(reminder)
                    scheduleOrCancel(reminder)
                    saveAllReminders()
                    rebuildReminderRows()
                    refreshEmptyState()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                    val updated = Reminder(label, hour, minute, reminder.type, reminder.enabled)
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
