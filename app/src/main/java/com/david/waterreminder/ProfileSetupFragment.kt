package com.david.waterreminder

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.david.waterreminder.databinding.FragmentProfileSetupBinding

class ProfileSetupFragment : Fragment() {

    private var _binding: FragmentProfileSetupBinding? = null
    private val binding get() = _binding!!

    private val prefs by lazy {
        requireContext().waterPrefs()
    }
    private val unit get() = prefs.getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML

    private var isCustomized = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup is mandatory: block the system/gesture back button instead of
        // letting the NavHostFragment pop back to FirstFragment unfinished.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            requireActivity().finish()
        }

        binding.toggleUnits.check(if (isImperial(unit)) binding.buttonImperial.id else binding.buttonMetric.id)
        refreshUnitDependentUi()
        binding.toggleUnits.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val chosen = if (checkedId == binding.buttonImperial.id) UNIT_OZ else UNIT_ML
            prefs.edit().putString(PREF_UNIT, chosen).apply()
            binding.layoutWeight.error = null
            refreshUnitDependentUi()
        }

        binding.cardBasic.setOnClickListener { selectExperience(customized = false) }
        binding.cardCustomized.setOnClickListener { selectExperience(customized = true) }
        selectExperience(customized = false)

        binding.buttonWhyNeeded.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_why_needed_title))
                .setMessage(getString(R.string.dialog_why_needed_message))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        binding.fabSetupDone.setOnClickListener { onDoneClicked() }
    }

    private fun refreshUnitDependentUi() {
        binding.textBasicSubtitle.text = getString(R.string.profile_setup_basic_subtitle, 1500.fmt(unit))
        binding.layoutWeight.hint = getString(R.string.profile_setup_weight_hint, weightUnitLabel(unit))
    }

    private fun selectExperience(customized: Boolean) {
        isCustomized = customized
        applyCardSelectionStyle(binding.cardBasic, binding.checkBasic, selected = !customized)
        applyCardSelectionStyle(binding.cardCustomized, binding.checkCustomized, selected = customized)
        binding.groupCustomFields.visibility = if (customized) View.VISIBLE else View.GONE
    }

    private fun onDoneClicked() {
        if (isCustomized) {
            // Validate both fields unconditionally first so age AND weight errors
            // show together when both are missing, instead of stopping at the first.
            val age = validateAgeField(binding.editAge, binding.layoutAge)
            val weight = validateWeightField(binding.editWeight, binding.layoutWeight, unit)
            if (age == null) {
                binding.editAge.requestFocus()
                return
            }
            if (weight == null) {
                binding.editWeight.requestFocus()
                return
            }

            val recommendedMl = recommendedGoalMl(age, weight)
            prefs.edit()
                .putInt(PREF_PROFILE_AGE, age)
                .putFloat(PREF_PROFILE_WEIGHT_KG, weight.toFloat())
                .putInt("personal_goal_ml", recommendedMl)
                .apply()

            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_recommended_goal_title))
                .setMessage(getString(R.string.dialog_recommended_goal_message, recommendedMl.fmt(unit)))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.dialog_recommended_goal_button)) { _, _ -> goToReminderSetup() }
                .show()
        } else {
            goToReminderSetup()
        }
    }

    private fun goToReminderSetup() {
        findNavController().navigate(R.id.action_ProfileSetupFragment_to_ReminderSetupFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
