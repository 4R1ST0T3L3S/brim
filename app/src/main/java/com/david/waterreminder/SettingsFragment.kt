package com.david.waterreminder

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class SettingsFragment : Fragment() {

    private val prefs by lazy {
        requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
    }
    private val unit get() = prefs.getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshRows(view)

        view.findViewById<View>(R.id.row_reset_custom).setOnClickListener {
            val current = prefs.getInt("custom_quick_ml", 0)
            if (current == 0) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("Reset custom button")
                .setMessage("Remove the custom ${current.fmt(unit)} quick button?")
                .setPositiveButton("Reset") { _, _ ->
                    prefs.edit().putInt("custom_quick_ml", 0).apply()
                    refreshRows(view)
                    firstFragment()?.applyCustomQuickMl(0)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        view.findViewById<View>(R.id.row_set_unit).setOnClickListener {
            showUnitDialog(view)
        }

        view.findViewById<View>(R.id.row_set_personal_goal).setOnClickListener {
            showPersonalGoalDialog(view)
        }

        view.findViewById<View>(R.id.row_replay_tour).setOnClickListener {
            prefs.edit().putBoolean("tour_done", false).apply()
            findNavController().popBackStack(R.id.FirstFragment, false)
        }

        view.findViewById<View>(R.id.row_privacy_policy).setOnClickListener {
            findNavController().navigate(R.id.action_SettingsFragment_to_SecondFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let { refreshRows(it) }
    }

    // ─── Dialogs ──────────────────────────────────────────────────

    private fun showUnitDialog(view: View) {
        val options = arrayOf("Millilitres (ml)", "Centilitres (cl)")
        val currentIndex = if (unit == UNIT_CL) 1 else 0
        AlertDialog.Builder(requireContext())
            .setTitle("Measure unit")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val chosen = if (which == 1) UNIT_CL else UNIT_ML
                prefs.edit().putString(PREF_UNIT, chosen).apply()
                refreshRows(view)
                firstFragment()?.onUnitChanged()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPersonalGoalDialog(view: View) {
        val currentMl = prefs.getInt("personal_goal_ml", 0)
        val editText = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                if (unit == UNIT_CL) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
            hint = "Your personal goal in $unit (e.g. ${1500.toDisplay(unit)})"
            setPadding(48, 32, 48, 32)
            if (currentMl > 0) { setText(currentMl.toDisplay(unit)); selectAll() }
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Personal goal")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val storedMl = editText.text.toString().toMl(unit)
                if (storedMl != null && storedMl in 100..10_000) {
                    prefs.edit().putInt("personal_goal_ml", storedMl).apply()
                    refreshRows(view)
                }
            }
            .apply {
                if (currentMl > 0) {
                    setNeutralButton("Clear") { _, _ ->
                        prefs.edit().putInt("personal_goal_ml", 0).apply()
                        refreshRows(view)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        editText.requestFocus()
    }

    // ─── UI refresh ───────────────────────────────────────────────

    private fun refreshRows(view: View) {
        val customMl = prefs.getInt("custom_quick_ml", 0)
        view.findViewById<TextView>(R.id.text_custom_current).text =
            if (customMl > 0) "Currently set to ${customMl.fmt(unit)}" else "No custom value set"

        view.findViewById<TextView>(R.id.text_unit_current).text =
            if (unit == UNIT_CL) "Centilitres (cl)" else "Millilitres (ml)"

        val personalMl = prefs.getInt("personal_goal_ml", 0)
        view.findViewById<TextView>(R.id.text_personal_goal_current).text =
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
