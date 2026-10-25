package com.david.waterreminder

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import com.david.waterreminder.databinding.DialogProfileAgeWeightBinding
import com.david.waterreminder.databinding.FragmentSettingsBinding

class SettingsFragment : BaseMenuFragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val prefs by lazy {
        requireContext().waterPrefs()
    }
    private val unit get() = prefs.getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML

    /** Where the Health Connect switch lives — see [PREF_HEALTH_CONNECT_SYNC]. */
    private val devicePrefs by lazy {
        requireContext().transientPrefs()
    }

    /** Health Connect's permission screen; null below Android 9 (see [healthConnectPossible]). */
    private val requestHealthConnectSync = if (!healthConnectPossible()) null else
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            val allowed = granted.containsAll(HEALTH_CONNECT_SYNC_PERMISSIONS)
            devicePrefs.setHealthConnectSync(allowed)
            // Sends today's drinks; earlier days have no drink times.
            requestHealthConnectSync(requireContext())
            // Also shown when Health Connect stops asking after two refusals.
            if (!allowed) {
                Toast.makeText(requireContext(), R.string.settings_health_connect_denied, Toast.LENGTH_LONG)
                    .show()
            }
            _binding?.let { refreshRows() }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowSetTheme.rowIcon.setImageResource(android.R.drawable.ic_menu_manage)
        binding.rowSetTheme.rowTitle.text = getString(R.string.settings_row_theme)

        binding.rowResetQuickAmounts.rowIcon.setImageResource(R.drawable.ic_water_drop)
        binding.rowResetQuickAmounts.rowTitle.text = getString(R.string.settings_row_reset_amounts)
        binding.rowResetQuickAmounts.rowChevron.setImageResource(android.R.drawable.ic_menu_delete)
        // Title only: the row does one thing, and listing the current amounts here would just
        // duplicate the home screen.
        binding.rowResetQuickAmounts.rowSubtitle.visibility = View.GONE

        binding.rowSetUnit.rowIcon.setImageResource(R.drawable.ic_water_drop)
        binding.rowSetUnit.rowTitle.text = getString(R.string.settings_row_unit)

        binding.rowProfile.rowIcon.setImageResource(R.drawable.ic_person)
        binding.rowProfile.rowTitle.text = getString(R.string.settings_row_profile)

        binding.rowSetPersonalGoal.rowIcon.setImageResource(R.drawable.ic_bottle_full)
        binding.rowSetPersonalGoal.rowTitle.text = getString(R.string.settings_row_personal_goal)

        binding.rowCelebrations.rowIcon.setImageResource(R.drawable.ic_check)
        binding.rowCelebrations.rowTitle.text = getString(R.string.settings_row_celebrations)
        // Swaps the chevron for a switch; the shared row layout ships with the switch hidden.
        binding.rowCelebrations.rowChevron.visibility = View.GONE
        binding.rowCelebrations.rowSwitch.visibility = View.VISIBLE

        refreshRows()

        binding.rowSetTheme.rowContainer.setOnClickListener { showThemeDialog() }

        binding.rowResetQuickAmounts.rowContainer.setOnClickListener {
            // Already the shipped set — nothing a reset would change.
            if (prefs.quickAmounts() == DEFAULT_QUICK_AMOUNTS_ML) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_reset_amounts_title))
                .setMessage(getString(R.string.dialog_reset_amounts_message))
                .setPositiveButton(getString(R.string.dialog_reset)) { _, _ ->
                    prefs.setQuickAmounts(DEFAULT_QUICK_AMOUNTS_ML)
                    refreshRows()
                    firstFragment()?.onQuickAmountsChanged()
                }
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show()
        }

        binding.rowSetUnit.rowContainer.setOnClickListener { showUnitDialog() }

        binding.rowProfile.rowContainer.setOnClickListener { showProfileDialog() }

        binding.rowSetPersonalGoal.rowContainer.setOnClickListener { showPersonalGoalDialog() }

        binding.rowCelebrations.rowContainer.setOnClickListener {
            // Only the popups are silenced — the streak and the home banner still track the goal.
            prefs.edit()
                .putBoolean(PREF_CELEBRATIONS_ENABLED, !prefs.celebrationsEnabled())
                .apply()
            refreshRows()
        }

        binding.rowNotifications.rowIcon.setImageResource(R.drawable.ic_bell)
        binding.rowNotifications.rowTitle.text = getString(R.string.action_notifications)
        binding.rowNotifications.rowSubtitle.text =
            getString(R.string.settings_row_notifications_subtitle)
        binding.rowNotifications.rowContainer.setOnClickListener {
            findNavController().navigate(R.id.action_SettingsFragment_to_NotificationsFragment)
        }

        binding.rowHealthConnect.rowIcon.setImageResource(R.drawable.ic_health)
        binding.rowHealthConnect.rowTitle.text = getString(R.string.settings_row_health_connect)
        binding.rowHealthConnect.rowContainer.setOnClickListener { onHealthConnectRowTapped() }

        binding.rowData.rowIcon.setImageResource(android.R.drawable.ic_menu_save)
        binding.rowData.rowTitle.text = getString(R.string.action_data)
        binding.rowData.rowSubtitle.text = getString(R.string.settings_row_data_subtitle)
        binding.rowData.rowContainer.setOnClickListener {
            findNavController().navigate(R.id.action_SettingsFragment_to_DataFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.let { refreshRows() }
        turnOffHealthConnectSyncIfRevoked()
    }

    // ─── Health Connect ───────────────────────────────────────────

    private fun onHealthConnectRowTapped() {
        when (healthConnectAvailability(requireContext())) {
            HealthConnectAvailability.AVAILABLE -> {
                if (devicePrefs.healthConnectSyncOn()) {
                    // Already-written data stays in Health Connect.
                    devicePrefs.setHealthConnectSync(false)
                    refreshRows()
                } else {
                    requestHealthConnectSync?.launch(HEALTH_CONNECT_SYNC_PERMISSIONS)
                }
            }
            HealthConnectAvailability.UPDATE_REQUIRED -> {
                try {
                    startActivity(healthConnectUpdateIntent(requireContext()))
                } catch (_: ActivityNotFoundException) {
                    // No Play Store, e.g. a de-Googled phone.
                    Toast.makeText(requireContext(), R.string.settings_health_connect_no_store, Toast.LENGTH_LONG)
                        .show()
                }
            }
            // Health Connect went away while Settings was open.
            HealthConnectAvailability.UNAVAILABLE -> refreshRows()
        }
    }

    /** Turns sync off if the permission was revoked in Health Connect. */
    private fun turnOffHealthConnectSyncIfRevoked() {
        if (!devicePrefs.healthConnectSyncOn()) return
        val client = healthConnectClientOrNull(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            // A failed check (e.g. mid-update) isn't a refusal; only "not granted" or gone turns it off.
            val stillAllowed = client != null &&
                runCatching { client.hasPermissions(HEALTH_CONNECT_SYNC_PERMISSIONS) }.getOrDefault(true)
            if (stillAllowed) return@launch
            devicePrefs.setHealthConnectSync(false)
            _binding?.let { refreshRows() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─── Dialogs ──────────────────────────────────────────────────

    private fun showThemeDialog() {
        val options = THEME_MODES.map { getString(themeModeLabelRes(it)) }.toTypedArray()
        val current = prefs.getInt(PREF_THEME_MODE, DEFAULT_THEME_MODE)
        val currentIndex = themeModeDialogIndex(current)
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_row_theme))
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val selected = THEME_MODES[which]
                dialog.dismiss()
                if (selected == current) return@setSingleChoiceItems
                prefs.edit().putInt(PREF_THEME_MODE, selected).apply()
                AppCompatDelegate.setDefaultNightMode(nightModeFor(selected))
                // Dark and Blue share a night mode, so switching between them produces no uiMode
                // change for AppCompat to react to; recreating is what actually swaps the theme.
                requireActivity().recreate()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun showUnitDialog() {
        val unitValues = arrayOf(UNIT_ML, UNIT_CL, UNIT_OZ)
        val options = arrayOf(getString(R.string.unit_ml), getString(R.string.unit_cl), getString(R.string.unit_oz))
        val currentIndex = unitValues.indexOf(unit).coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_row_unit))
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                prefs.edit().putString(PREF_UNIT, unitValues[which]).apply()
                refreshRows()
                firstFragment()?.onUnitChanged()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun showPersonalGoalDialog() {
        val currentMl = prefs.getInt("personal_goal_ml", 0)
        val editText = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                if (isDecimalUnit(unit)) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
            hint = "e.g. ${1000.toDisplay(unit)} $unit"
            setPadding(48, 32, 48, 32)
            if (currentMl > 0) { setText(currentMl.toDisplay(unit)); selectAll() }
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_row_personal_goal))
            .setView(editText)
            .setPositiveButton(getString(R.string.dialog_save), null)
            .apply {
                if (currentMl > 0) {
                    setNeutralButton(getString(R.string.dialog_reset_to_default)) { _, _ ->
                        prefs.edit().putInt("personal_goal_ml", 0).apply()
                        refreshRows()
                    }
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val storedMl = editText.text.toString().toValidGoalMl(unit)
            if (storedMl != null) {
                prefs.edit().putInt("personal_goal_ml", storedMl).apply()
                refreshRows()
                dialog.dismiss()
            } else {
                editText.error = "Enter a value between ${GOAL_MIN_ML.fmt(unit)} and ${GOAL_MAX_ML.fmt(unit)}"
            }
        }
        editText.requestFocus()
    }

    private fun showProfileDialog() {
        val context = requireContext()
        val currentAge = prefs.getInt(PREF_PROFILE_AGE, 0)
        val currentWeight = prefs.getFloat(PREF_PROFILE_WEIGHT_KG, 0f)

        val fieldsBinding = DialogProfileAgeWeightBinding.inflate(LayoutInflater.from(context))
        fieldsBinding.layoutWeight.hint = getString(R.string.profile_setup_weight_hint, weightUnitLabel(unit))
        if (currentAge > 0) fieldsBinding.editAge.setText(currentAge.toString())
        if (currentWeight > 0) fieldsBinding.editWeight.setText(currentWeight.formatWeight(unit))

        val dialog = AlertDialog.Builder(context)
            .setTitle(getString(R.string.settings_row_profile))
            .setView(fieldsBinding.root)
            .setPositiveButton(getString(R.string.dialog_save), null)
            .apply {
                if (currentAge > 0 || currentWeight > 0) {
                    setNeutralButton(getString(R.string.dialog_clear)) { _, _ ->
                        prefs.edit()
                            .remove(PREF_PROFILE_AGE)
                            .remove(PREF_PROFILE_WEIGHT_KG)
                            .apply()
                        refreshRows()
                    }
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val age = validateAgeField(fieldsBinding.editAge, fieldsBinding.layoutAge)
            val weight = validateWeightField(fieldsBinding.editWeight, fieldsBinding.layoutWeight, unit)
            if (age == null || weight == null) return@setOnClickListener

            prefs.edit()
                .putInt(PREF_PROFILE_AGE, age)
                .putFloat(PREF_PROFILE_WEIGHT_KG, weight.toFloat())
                .apply()
            refreshRows()
            dialog.dismiss()
            offerGoalUpdate(age, weight)
        }
    }

    /** After editing age/weight, offer to sync the personal goal — never overwrite it silently. */
    private fun offerGoalUpdate(age: Int, weightKg: Double) {
        val recommendedMl = recommendedGoalMl(age, weightKg)
        val currentGoalMl = prefs.getInt("personal_goal_ml", 0)
        if (recommendedMl == currentGoalMl) return

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_recommended_goal_title))
            .setMessage(getString(R.string.dialog_update_goal_message, recommendedMl.fmt(unit)))
            .setPositiveButton(getString(R.string.dialog_update_goal_button)) { _, _ ->
                prefs.edit().putInt("personal_goal_ml", recommendedMl).apply()
                refreshRows()
                firstFragment()?.onUnitChanged()
            }
            .setNegativeButton(getString(R.string.dialog_keep_current_goal), null)
            .show()
    }

    // ─── UI refresh ───────────────────────────────────────────────

    private fun refreshRows() {
        binding.rowSetTheme.rowSubtitle.text =
            getString(themeModeLabelRes(prefs.getInt(PREF_THEME_MODE, DEFAULT_THEME_MODE)))

        binding.rowSetUnit.rowSubtitle.text = when (unit) {
            UNIT_CL -> getString(R.string.unit_cl)
            UNIT_OZ -> getString(R.string.unit_oz)
            else -> getString(R.string.unit_ml)
        }

        val age = prefs.getInt(PREF_PROFILE_AGE, 0)
        val weightKg = prefs.getFloat(PREF_PROFILE_WEIGHT_KG, 0f)
        binding.rowProfile.rowSubtitle.text =
            if (age > 0 && weightKg > 0) {
                val weightDisplay = "${weightKg.formatWeight(unit)} ${weightUnitLabel(unit)}"
                getString(R.string.settings_profile_value, age, weightDisplay)
            } else {
                getString(R.string.settings_not_set)
            }

        val personalMl = prefs.getInt("personal_goal_ml", 0)
        binding.rowSetPersonalGoal.rowSubtitle.text =
            if (personalMl > 0) getString(R.string.settings_personal_goal_value, personalMl.fmt(unit))
            else getString(R.string.settings_not_set)

        // The switch says on or off; the subtitle says what that actually does to the app, so
        // the consequence of flipping it is readable without trying it.
        val celebrations = prefs.celebrationsEnabled()
        binding.rowCelebrations.rowSwitch.isChecked = celebrations
        binding.rowCelebrations.rowSubtitle.text =
            if (celebrations) getString(R.string.settings_celebrations_enabled)
            else getString(R.string.settings_celebrations_disabled)

        refreshHealthConnectRow()
    }

    /** Switch when ready, Play Store link when outdated, hidden otherwise. */
    private fun refreshHealthConnectRow() {
        val availability = healthConnectAvailability(requireContext())
        val shown = availability != HealthConnectAvailability.UNAVAILABLE
        binding.textSectionHealthConnect.visibility = if (shown) View.VISIBLE else View.GONE
        binding.rowHealthConnect.root.visibility = if (shown) View.VISIBLE else View.GONE

        val row = binding.rowHealthConnect
        if (availability == HealthConnectAvailability.AVAILABLE) {
            val on = devicePrefs.healthConnectSyncOn()
            row.rowChevron.visibility = View.GONE
            row.rowSwitch.visibility = View.VISIBLE
            row.rowSwitch.isChecked = on
            row.rowSubtitle.text =
                if (on) getString(R.string.settings_health_connect_enabled)
                else getString(R.string.settings_health_connect_disabled)
        } else {
            row.rowSwitch.visibility = View.GONE
            row.rowChevron.visibility = View.VISIBLE
            row.rowSubtitle.text = getString(R.string.settings_health_connect_update)
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private fun firstFragment(): FirstFragment? =
        requireActivity()
            .supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main)
            ?.childFragmentManager?.fragments
            ?.filterIsInstance<FirstFragment>()
            ?.firstOrNull()
}
