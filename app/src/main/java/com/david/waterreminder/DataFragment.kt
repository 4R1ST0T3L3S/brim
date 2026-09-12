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
import org.json.JSONObject

class DataFragment : BaseMenuFragment() {

    private var _binding: FragmentDataBinding? = null
    private val binding get() = _binding!!

    private lateinit var exportLauncher: ActivityResultLauncher<String>
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>

    private val prefs by lazy {
        requireContext().waterPrefs()
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
            exportLauncher.launch("fillmeup_backup.json")
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
            val json = buildBackupJson(prefs.all, tourPrefs.getBoolean(TOUR_DONE_KEY, false))

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

            val editor = prefs.edit().clear()
            applyBackupJson(JSONObject(content), editor) { done ->
                tourPrefs.edit().putBoolean(TOUR_DONE_KEY, done).apply()
            }
            editor.apply()

            IdleReminderScheduler.schedule(requireContext())

            Toast.makeText(requireContext(), getString(R.string.import_success), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
