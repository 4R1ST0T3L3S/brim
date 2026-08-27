package com.david.waterreminder

import android.widget.EditText
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputLayout

/**
 * Validates an age field, setting an error message on [layoutAge] if invalid.
 * Returns the parsed age, or null if empty/out of range. Shared by ProfileSetupFragment
 * and SettingsFragment's Age & weight dialog — the rules are identical in both places.
 */
fun Fragment.validateAgeField(editAge: EditText, layoutAge: TextInputLayout): Int? {
    val age = editAge.text.toString().trim().toIntOrNull()
    layoutAge.error = when {
        age == null -> getString(R.string.profile_error_age)
        age !in PROFILE_AGE_MIN..PROFILE_AGE_MAX ->
            getString(R.string.profile_error_age_range, PROFILE_AGE_MIN, PROFILE_AGE_MAX)
        else -> null
    }
    return age?.takeIf { it in PROFILE_AGE_MIN..PROFILE_AGE_MAX }
}

/**
 * Validates a weight field in the active display unit, setting an error message on
 * [layoutWeight] if invalid. Returns the weight converted to kg (the storage unit),
 * or null if empty/out of range.
 */
fun Fragment.validateWeightField(editWeight: EditText, layoutWeight: TextInputLayout, unit: String): Double? {
    val weightKg = editWeight.text.toString().parseWeightInput(unit)
    val (min, max) = weightBoundsDisplay(unit)
    layoutWeight.error = when {
        weightKg == null -> getString(R.string.profile_error_weight)
        weightKg !in PROFILE_WEIGHT_KG_MIN..PROFILE_WEIGHT_KG_MAX ->
            getString(R.string.profile_error_weight_range, min.toString(), max.toString(), weightUnitLabel(unit))
        else -> null
    }
    return weightKg?.takeIf { it in PROFILE_WEIGHT_KG_MIN..PROFILE_WEIGHT_KG_MAX }
}
