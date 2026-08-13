package com.david.waterreminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TimePicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.david.waterreminder.databinding.FragmentReminderSetupBinding

class ReminderSetupFragment : Fragment() {

    companion object {
        private const val DEFAULT_START_HOUR = 8
        private const val DEFAULT_END_HOUR = 20
    }

    private var _binding: FragmentReminderSetupBinding? = null
    private val binding get() = _binding!!

    private val prefs by lazy {
        requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
    }

    private var isCustomWindow = false
    private var dayBeginsHour = DEFAULT_START_HOUR
    private var offToBedHour = DEFAULT_END_HOUR
    private var pendingReminder: Reminder? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingReminder?.let(::enableNotifications)
        finishSetup()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReminderSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardHourly.setOnClickListener { selectSchedule(custom = false) }
        binding.cardCustom.setOnClickListener { selectSchedule(custom = true) }
        selectSchedule(custom = false)

        binding.buttonDayBegins.setOnClickListener {
            showHourPicker(dayBeginsHour) { dayBeginsHour = it; updateWindowLabels() }
        }
        binding.buttonOffToBed.setOnClickListener {
            showHourPicker(offToBedHour) { offToBedHour = it; updateWindowLabels() }
        }
        updateWindowLabels()

        binding.fabReminderSetupBack.setOnClickListener { findNavController().popBackStack() }
        binding.fabReminderSetupDone.setOnClickListener { onDoneClicked() }
    }

    private fun selectSchedule(custom: Boolean) {
        isCustomWindow = custom
        applyCardSelectionStyle(binding.cardHourly, binding.checkHourly, selected = !custom)
        applyCardSelectionStyle(binding.cardCustom, binding.checkCustom, selected = custom)
        binding.groupCustomWindow.visibility = if (custom) View.VISIBLE else View.GONE
    }

    private fun updateWindowLabels() {
        binding.buttonDayBegins.text =
            getString(R.string.reminder_setup_day_begins, String.format("%02d:00", dayBeginsHour))
        binding.buttonOffToBed.text =
            getString(R.string.reminder_setup_off_to_bed, String.format("%02d:00", offToBedHour))
    }

    private fun showHourPicker(currentHour: Int, onPicked: (Int) -> Unit) {
        val timePicker = TimePicker(requireContext()).apply {
            setIs24HourView(true)
            hour = currentHour
            minute = 0
        }
        AlertDialog.Builder(requireContext())
            .setView(timePicker)
            .setPositiveButton(getString(R.string.dialog_save)) { _, _ -> onPicked(timePicker.hour) }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun onDoneClicked() {
        val (startHour, endHour) = if (isCustomWindow) {
            if (dayBeginsHour >= offToBedHour) {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.reminder_setup_error_window_title))
                    .setMessage(getString(R.string.reminder_setup_error_window))
                    .setPositiveButton(getString(R.string.dialog_save), null)
                    .show()
                return
            }
            dayBeginsHour to offToBedHour
        } else {
            DEFAULT_START_HOUR to DEFAULT_END_HOUR
        }

        val reminder = Reminder(String.format("%02d:00", startHour), startHour, 0, "hourly", true, endHour)
        prefs.edit().putStringSet("reminders_v3", setOf(reminder.toPrefsString())).apply()
        pendingReminder = reminder

        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

        if (needsPermission) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableNotifications(reminder)
            finishSetup()
        }
    }

    private fun enableNotifications(reminder: Reminder) {
        prefs.edit().putBoolean("notifications_enabled", true).apply()
        ReminderScheduler.schedule(requireContext(), reminder)
    }

    private fun finishSetup() {
        prefs.edit().putBoolean(PREF_PROFILE_SETUP_DONE, true).apply()
        findNavController().popBackStack(R.id.FirstFragment, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
