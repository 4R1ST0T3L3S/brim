package com.david.waterreminder

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.findNavController
import com.david.waterreminder.databinding.FragmentSettingsBinding

class SettingsFragment : BaseMenuFragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val prefs by lazy {
        requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
    }
    private val unit get() = prefs.getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowSetTheme.rowIcon.setImageResource(android.R.drawable.ic_menu_manage)
        binding.rowSetTheme.rowTitle.text = "Theme"

        binding.rowResetCustom.rowIcon.setImageResource(R.drawable.ic_water_drop)
        binding.rowResetCustom.rowTitle.text = "Reset custom quick button"
        binding.rowResetCustom.rowChevron.setImageResource(android.R.drawable.ic_menu_delete)

        binding.rowSetUnit.rowIcon.setImageResource(R.drawable.ic_water_drop)
        binding.rowSetUnit.rowTitle.text = "Measure unit"

        binding.rowSetPersonalGoal.rowIcon.setImageResource(R.drawable.ic_bottle_full)
        binding.rowSetPersonalGoal.rowTitle.text = "Personal goal"

        refreshRows()

        binding.rowSetTheme.rowContainer.setOnClickListener { showThemeDialog() }

        binding.rowResetCustom.rowContainer.setOnClickListener {
            val current = prefs.getInt("custom_quick_ml", 0)
            if (current == 0) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("Reset custom button")
                .setMessage("Remove the custom ${current.fmt(unit)} quick button?")
                .setPositiveButton("Reset") { _, _ ->
                    prefs.edit().putInt("custom_quick_ml", 0).apply()
                    refreshRows()
                    firstFragment()?.applyCustomQuickMl(0)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.rowSetUnit.rowContainer.setOnClickListener { showUnitDialog() }

        binding.rowSetPersonalGoal.rowContainer.setOnClickListener { showPersonalGoalDialog() }

        binding.rowData.rowIcon.setImageResource(android.R.drawable.ic_menu_save)
        binding.rowData.rowTitle.text = getString(R.string.action_data)
        binding.rowData.rowSubtitle.text = "Export or import your data"
        binding.rowData.rowContainer.setOnClickListener {
            findNavController().navigate(R.id.action_SettingsFragment_to_DataFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.let { refreshRows() }
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
            .setTitle("Theme")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                prefs.edit().putInt(PREF_THEME_MODE, THEME_MODES[which]).apply()
                AppCompatDelegate.setDefaultNightMode(THEME_MODES[which])
                refreshRows()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUnitDialog() {
        val options = arrayOf("Millilitres (ml)", "Centilitres (cl)")
        val currentIndex = if (unit == UNIT_CL) 1 else 0
        AlertDialog.Builder(requireContext())
            .setTitle("Measure unit")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val chosen = if (which == 1) UNIT_CL else UNIT_ML
                prefs.edit().putString(PREF_UNIT, chosen).apply()
                refreshRows()
                firstFragment()?.onUnitChanged()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPersonalGoalDialog() {
        val currentMl = prefs.getInt("personal_goal_ml", 0)
        val editText = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                if (unit == UNIT_CL) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
            hint = "e.g. ${1000.toDisplay(unit)} $unit"
            setPadding(48, 32, 48, 32)
            if (currentMl > 0) { setText(currentMl.toDisplay(unit)); selectAll() }
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Personal goal")
            .setView(editText)
            .setPositiveButton("Save", null)
            .apply {
                if (currentMl > 0) {
                    setNeutralButton("Reset to Default") { _, _ ->
                        prefs.edit().putInt("personal_goal_ml", 0).apply()
                        refreshRows()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
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

    // ─── UI refresh ───────────────────────────────────────────────

    private fun refreshRows() {
        binding.rowSetTheme.rowSubtitle.text =
            getString(themeModeLabelRes(prefs.getInt(PREF_THEME_MODE, DEFAULT_THEME_MODE)))

        val customMl = prefs.getInt("custom_quick_ml", 0)
        binding.rowResetCustom.rowSubtitle.text =
            if (customMl > 0) "Currently set to ${customMl.fmt(unit)}" else "No custom value set"

        binding.rowSetUnit.rowSubtitle.text =
            if (unit == UNIT_CL) "Centilitres (cl)" else "Millilitres (ml)"

        val personalMl = prefs.getInt("personal_goal_ml", 0)
        binding.rowSetPersonalGoal.rowSubtitle.text =
            if (personalMl > 0) "${personalMl.fmt(unit)} per day" else "Not set"
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
