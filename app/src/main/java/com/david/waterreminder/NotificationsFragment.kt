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
import androidx.core.content.ContextCompat
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

        refreshRows()
    }

    override fun onResume() {
        super.onResume()
        // The exact-alarm screen is a system Activity — re-read on the way back.
        _binding?.let { refreshRows() }
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
