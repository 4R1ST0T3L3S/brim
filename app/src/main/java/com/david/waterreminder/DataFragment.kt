package com.david.waterreminder

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.david.waterreminder.databinding.FragmentDataBinding
import org.json.JSONArray
import org.json.JSONObject

class DataFragment : BaseMenuFragment() {

    private var _binding: FragmentDataBinding? = null
    private val binding get() = _binding!!

    private lateinit var exportLauncher: ActivityResultLauncher<String>
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>

    private val prefs by lazy {
        requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
    }
    private val tourPrefs by lazy {
        requireContext().getSharedPreferences("tour_prefs", Context.MODE_PRIVATE)
    }

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
    ): View {
        _binding = FragmentDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowExportData.rowIcon.setImageResource(android.R.drawable.ic_menu_save)
        binding.rowExportData.rowTitle.text = getString(R.string.settings_export_data)
        binding.rowExportData.rowSubtitle.text = getString(R.string.settings_export_data_subtitle)
        binding.rowExportData.rowContainer.setOnClickListener {
            exportLauncher.launch("waterreminder_backup.json")
        }

        binding.rowImportData.rowIcon.setImageResource(android.R.drawable.ic_menu_revert)
        binding.rowImportData.rowTitle.text = getString(R.string.settings_import_data)
        binding.rowImportData.rowSubtitle.text = getString(R.string.settings_import_data_subtitle)
        binding.rowImportData.rowContainer.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_import_title))
                .setMessage(getString(R.string.dialog_import_message))
                .setPositiveButton(getString(R.string.dialog_import_confirm)) { _, _ ->
                    importLauncher.launch(arrayOf("application/json"))
                }
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

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
            Toast.makeText(requireContext(), getString(R.string.import_success), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
