package com.david.waterreminder

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.david.waterreminder.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

class SettingsFragment : Fragment() {

    private lateinit var exportLauncher: ActivityResultLauncher<String>
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>

    private val prefs by lazy {
        requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
    }
    private val tourPrefs by lazy {
        requireContext().getSharedPreferences("tour_prefs", Context.MODE_PRIVATE)
    }
    private val unit get() = prefs.getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { writeBackup(it) }
        }
        importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { readBackup(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshRows(view)

        view.findViewById<View>(R.id.row_set_theme).setOnClickListener {
            showThemeDialog(view)
        }

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
            tourPrefs.edit().putBoolean("tour_done", false).apply()
            findNavController().popBackStack(R.id.FirstFragment, false)
        }

        view.findViewById<View>(R.id.row_privacy_policy).setOnClickListener {
            findNavController().navigate(R.id.action_SettingsFragment_to_SecondFragment)
        }

        view.findViewById<View>(R.id.row_export_data).setOnClickListener {
            exportLauncher.launch("waterreminder_backup.json")
        }

        view.findViewById<View>(R.id.row_import_data).setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }

        view.findViewById<View>(R.id.row_changelog).setOnClickListener {
            findNavController().navigate(R.id.action_SettingsFragment_to_ChangelogFragment)
        }

        view.findViewById<TextView>(R.id.text_app_version).text =
            getString(R.string.settings_version, BuildConfig.VERSION_NAME)
    }

    override fun onResume() {
        super.onResume()
        view?.let { refreshRows(it) }
    }

    // ─── Export / Import ─────────────────────────────────────────

    private fun writeBackup(uri: Uri) {
        try {
            val strings = JSONObject()
            val ints = JSONObject()
            val booleans = JSONObject()
            val sets = JSONObject()

            prefs.all.forEach { (key, value) ->
                when (value) {
                    is String  -> strings.put(key, value)
                    is Int     -> ints.put(key, value)
                    is Boolean -> booleans.put(key, value)
                    is Set<*>  -> {
                        val arr = JSONArray()
                        value.forEach { arr.put(it) }
                        sets.put(key, arr)
                    }
                }
            }

            booleans.put("tour_done", tourPrefs.getBoolean("tour_done", false))

            val json = JSONObject()
                .put("strings", strings)
                .put("ints", ints)
                .put("booleans", booleans)
                .put("sets", sets)

            requireContext().contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(json.toString(2).toByteArray())
            }

            Toast.makeText(requireContext(), getString(R.string.export_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun readBackup(uri: Uri) {
        try {
            val content = requireContext().contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText() ?: return

            val json = JSONObject(content)
            val editor = prefs.edit().clear()

            json.getJSONObject("strings").let { obj ->
                obj.keys().forEach { key -> editor.putString(key, obj.getString(key)) }
            }
            json.getJSONObject("ints").let { obj ->
                obj.keys().forEach { key -> editor.putInt(key, obj.getInt(key)) }
            }
            json.getJSONObject("booleans").let { obj ->
                obj.keys().forEach { key ->
                    if (key == "tour_done") tourPrefs.edit().putBoolean(key, obj.getBoolean(key)).apply()
                    else editor.putBoolean(key, obj.getBoolean(key))
                }
            }
            json.getJSONObject("sets").let { obj ->
                obj.keys().forEach { key ->
                    val arr = obj.getJSONArray(key)
                    val set = (0 until arr.length()).mapTo(mutableSetOf()) { arr.getString(it) }
                    editor.putStringSet(key, set)
                }
            }

            editor.apply()
            view?.let { refreshRows(it) }
            Toast.makeText(requireContext(), getString(R.string.import_success), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Dialogs ──────────────────────────────────────────────────

    private fun showThemeDialog(view: View) {
        val options = arrayOf("System default", "Light", "Dark")
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val current = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val currentIndex = modes.indexOfFirst { it == current }.coerceAtLeast(0)
        AlertDialog.Builder(requireContext())
            .setTitle("Theme")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                prefs.edit().putInt("theme_mode", modes[which]).apply()
                AppCompatDelegate.setDefaultNightMode(modes[which])
                refreshRows(view)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

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
            hint = "e.g. ${1000.toDisplay(unit)} $unit"
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
        val themeLabel = when (prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_NO)) {
            AppCompatDelegate.MODE_NIGHT_NO -> "Light"
            AppCompatDelegate.MODE_NIGHT_YES -> "Dark"
            else -> "System default"
        }
        view.findViewById<TextView>(R.id.text_theme_current).text = themeLabel

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
