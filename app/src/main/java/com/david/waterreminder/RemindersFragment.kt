package com.david.waterreminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TimePicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.david.waterreminder.databinding.FragmentRemindersBinding
import com.david.waterreminder.databinding.ItemReminderBinding

data class Reminder(
    val time: String,
    val hour: Int,
    val minute: Int,
    var type: String,    // "daily" or "onetime"
    var enabled: Boolean
) {
    fun toPrefsString() = "$time|$type|$enabled"

    companion object {
        fun fromPrefsString(s: String): Reminder? {
            val parts = s.split("|")
            if (parts.size != 3) return null
            val timeParts = parts[0].split(":")
            if (timeParts.size != 2) return null
            return Reminder(
                time = parts[0],
                hour = timeParts[0].toIntOrNull() ?: return null,
                minute = timeParts[1].toIntOrNull() ?: return null,
                type = parts[1],
                enabled = parts[2].toBoolean()
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
        Reminder("10:00", 10, 0, "daily", true),
        Reminder("12:00", 12, 0, "daily", true),
        Reminder("15:00", 15, 0, "daily", true),
        Reminder("17:00", 17, 0, "daily", true)
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        binding.switchNotifications.isChecked = granted
        if (granted) enableNotifications() else binding.switchNotifications.isChecked = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRemindersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        createNotificationChannel()

        binding.switchNotifications.isChecked = prefs.getBoolean("notifications_enabled", false)
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requestNotificationPermission() else disableNotifications()
        }

        binding.buttonAddReminder.setOnClickListener { showTimePickerDialog() }

        binding.buttonResetReminders.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset reminders")
                .setMessage("This will remove all current reminders and restore the defaults.")
                .setPositiveButton("Reset") { _, _ -> resetToDefaults() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        loadReminders()
    }

    // ─── Load / Save ──────────────────────────────────────────────

    private fun loadReminders() {
        val saved = prefs.getStringSet("reminders_v2", null)

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
        prefs.edit().putStringSet("reminders_v2", set).apply()
    }

    // ─── UI ───────────────────────────────────────────────────────

    private fun rebuildReminderRows() {
        binding.containerReminders.removeAllViews()
        reminders.sortedBy { it.time }.forEach { reminder ->
            addReminderRow(reminder)
        }
    }

    private fun addReminderRow(reminder: Reminder) {
        val rowBinding = ItemReminderBinding.inflate(
            LayoutInflater.from(requireContext()), binding.containerReminders, false
        )

        rowBinding.textReminderTime.text = "🔔  ${reminder.time}"
        rowBinding.switchReminderEnabled.isChecked = reminder.enabled

        applyTypeChip(rowBinding, reminder.type)

        // Toggle daily / one-time
        rowBinding.textReminderType.setOnClickListener {
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

        // Swipe right = edit, swipe left = delete
        addSwipeActions(
            foreground = rowBinding.foreground,
            editBg = rowBinding.editBackground,
            deleteBg = rowBinding.deleteBackground,
            swipeHandle = rowBinding.textReminderTime,
            reminder = reminder
        )

        binding.containerReminders.addView(rowBinding.root)
    }

    private fun applyTypeChip(rowBinding: ItemReminderBinding, type: String) {
        val isDaily = type == "daily"
        rowBinding.textReminderType.text = if (isDaily) "Daily" else "One-time"
        val color = if (isDaily) 0xFF2196F3.toInt() else 0xFFFF9800.toInt()
        val bg = rowBinding.textReminderType.background.mutate() as? GradientDrawable
            ?: GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 48f
            }
        bg.setColor(color)
        rowBinding.textReminderType.background = bg
    }

    private fun refreshEmptyState() {
        binding.textNoReminders.visibility = if (reminders.isEmpty()) View.VISIBLE else View.GONE
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
                        // Show the correct background based on direction
                        if (deltaX > 0) {
                            editBg.visibility = View.VISIBLE
                            deleteBg.visibility = View.GONE
                        } else {
                            editBg.visibility = View.GONE
                            deleteBg.visibility = View.VISIBLE
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
                                // Swiped left → delete
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Permission required")
                    .setMessage("To receive reminders when the app is closed, please allow exact alarms in Settings.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }
        }
        ReminderScheduler.schedule(requireContext(), reminder)
    }

    // ─── Notifications ────────────────────────────────────────────

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> enableNotifications()
                else -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            enableNotifications()
        }
    }

    private fun enableNotifications() {
        prefs.edit().putBoolean("notifications_enabled", true).apply()
        // Reschedule all enabled reminders in case the alarm chain was broken
        // while notifications were disabled.
        reminders.filter { it.enabled }.forEach { scheduleOrCancel(it) }
    }

    private fun disableNotifications() {
        prefs.edit().putBoolean("notifications_enabled", false).apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "water_reminder_channel", "Water Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Daily water intake reminders" }
            requireContext().getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
