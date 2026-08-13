package com.david.waterreminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.david.waterreminder.databinding.FragmentHelpBinding

class HelpFragment : BaseMenuFragment() {

    private var _binding: FragmentHelpBinding? = null
    private val binding get() = _binding!!

    private val tourPrefs by lazy {
        requireContext().getSharedPreferences("tour_prefs", Context.MODE_PRIVATE)
    }
    private val prefs by lazy {
        requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHelpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowReplayTour.rowIcon.setImageResource(R.drawable.ic_water_drop)
        binding.rowReplayTour.rowTitle.text = getString(R.string.help_row_replay_tour_title)
        binding.rowReplayTour.rowSubtitle.text = getString(R.string.help_row_replay_tour_subtitle)
        binding.rowReplayTour.rowContainer.setOnClickListener {
            tourPrefs.edit().putBoolean("tour_done", false).apply()
            findNavController().popBackStack(R.id.FirstFragment, false)
        }

        binding.rowReplayOnboarding.rowIcon.setImageResource(R.drawable.ic_person)
        binding.rowReplayOnboarding.rowTitle.text = getString(R.string.help_row_replay_onboarding_title)
        binding.rowReplayOnboarding.rowSubtitle.text = getString(R.string.help_row_replay_onboarding_subtitle)
        binding.rowReplayOnboarding.rowContainer.setOnClickListener {
            prefs.edit().putBoolean(PREF_PROFILE_SETUP_DONE, false).apply()
            findNavController().navigate(R.id.ProfileSetupFragment)
        }

        binding.rowReportProblem.rowIcon.setImageResource(android.R.drawable.ic_menu_send)
        binding.rowReportProblem.rowTitle.text = getString(R.string.help_section_report)
        binding.rowReportProblem.rowSubtitle.text = getString(R.string.help_row_report_subtitle)
        binding.rowReportProblem.rowContainer.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://codeberg.org/DavidCRicardo/WaterReminder/issues")))
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
