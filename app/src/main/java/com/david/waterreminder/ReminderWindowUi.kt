package com.david.waterreminder

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.david.waterreminder.databinding.DialogHourPickerBinding

/**
 * The get-up / go-to-bed pickers, shared by onboarding and Settings → Notifications so the
 * two screens ask the same question the same way.
 *
 * Reminders always fire at the top of the hour, so there is no minute to choose: an
 * hours-only wheel instead of a TimePicker, whose minute column can't be hidden through
 * public API and whose value would be discarded anyway.
 * displayedValues (rather than setFormatter) so the labels render before any scroll.
 */
fun Fragment.showHourPicker(title: String?, initialHour: Int, onPicked: (Int) -> Unit) {
    val pickerBinding = DialogHourPickerBinding.inflate(layoutInflater)
    val picker = pickerBinding.pickerHour.apply {
        minValue = 0
        maxValue = 23
        displayedValues = Array(24) { it.asHourLabel() }
        value = initialHour
        wrapSelectorWheel = true
    }
    AlertDialog.Builder(requireContext())
        .apply { if (title != null) setTitle(title) }
        .setView(pickerBinding.root)
        .setPositiveButton(getString(R.string.dialog_save)) { _, _ -> onPicked(picker.value) }
        .setNegativeButton(getString(R.string.dialog_cancel), null)
        .show()
}

/** Shown when the picked window can't be scheduled in — bedtime at or before getting up. */
fun Fragment.showWindowError() {
    AlertDialog.Builder(requireContext())
        .setTitle(getString(R.string.reminder_setup_error_window_title))
        .setMessage(getString(R.string.reminder_setup_error_window))
        .setPositiveButton(android.R.string.ok, null)
        .show()
}
