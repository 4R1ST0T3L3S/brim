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
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.david.waterreminder.databinding.FragmentReminderSetupBinding

class ReminderSetupFragment : Fragment() {

    private var _binding: FragmentReminderSetupBinding? = null
    private val binding get() = _binding!!

    private val prefs by lazy {
        requireContext().waterPrefs()
    }

    private enum class Schedule { HOURLY, CUSTOM, NONE }

    private var schedule = Schedule.HOURLY
    private var dayBeginsHour = DEFAULT_REMINDER_START_HOUR
    private var offToBedHour = DEFAULT_REMINDER_END_HOUR
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

        binding.cardHourly.setOnClickListener { selectSchedule(Schedule.HOURLY) }
        binding.cardCustom.setOnClickListener { selectSchedule(Schedule.CUSTOM) }
        binding.cardNone.setOnClickListener { selectSchedule(Schedule.NONE) }
        selectSchedule(Schedule.HOURLY)

        // Titled with the button's own label, so it's clear which end is being set.
        binding.buttonDayBegins.setOnClickListener {
            showHourPicker(binding.buttonDayBegins.text.toString(), dayBeginsHour) {
                dayBeginsHour = it; updateWindowLabels()
            }
        }
        binding.buttonOffToBed.setOnClickListener {
            showHourPicker(binding.buttonOffToBed.text.toString(), offToBedHour) {
                offToBedHour = it; updateWindowLabels()
            }
        }
        updateWindowLabels()

        binding.fabReminderSetupBack.setOnClickListener { findNavController().popBackStack() }
        binding.fabReminderSetupDone.setOnClickListener { onDoneClicked() }
    }

    private fun selectSchedule(choice: Schedule) {
        schedule = choice
        applyCardSelectionStyle(binding.cardHourly, binding.checkHourly, choice == Schedule.HOURLY)
        applyCardSelectionStyle(binding.cardCustom, binding.checkCustom, choice == Schedule.CUSTOM)
        applyCardSelectionStyle(binding.cardNone, binding.checkNone, choice == Schedule.NONE)
        binding.groupCustomWindow.visibility =
            if (choice == Schedule.CUSTOM) View.VISIBLE else View.GONE
    }

    private fun updateWindowLabels() {
        binding.buttonDayBegins.text =
            getString(R.string.reminder_setup_day_begins, dayBeginsHour.asHourLabel())
        binding.buttonOffToBed.text =
            getString(R.string.reminder_setup_off_to_bed, offToBedHour.asHourLabel())
    }

    private fun onDoneClicked() {
        val (startHour, endHour) = if (schedule == Schedule.CUSTOM) {
            if (dayBeginsHour >= offToBedHour) {
                showWindowError()
                return
            }
            dayBeginsHour to offToBedHour
        } else {
            DEFAULT_REMINDER_START_HOUR to DEFAULT_REMINDER_END_HOUR
        }

        // The window is its own pref — storing it on the reminder is what the old
        // built-in hourly entry did, and it no longer has anywhere to put it.
        prefs.setReminderWindow(startHour, endHour)

        // The default entry is written either way: opting out only leaves the master
        // toggle off, so turning notifications on later has a reminder to fire.
        val reminder = Reminder.defaultReminder(startHour)
        prefs.edit().putStringSet(REMINDERS_KEY, setOf(reminder.toPrefsString())).apply()
        pendingReminder = reminder

        if (schedule == Schedule.NONE) {
            prefs.edit().putBoolean("notifications_enabled", false).apply()
            finishSetup()
            return
        }

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
