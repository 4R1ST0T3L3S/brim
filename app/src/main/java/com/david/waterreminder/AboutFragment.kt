package com.david.waterreminder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.david.waterreminder.databinding.FragmentAboutBinding

class AboutFragment : BaseMenuFragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rowPrivacyPolicy.rowIcon.setImageResource(android.R.drawable.ic_menu_info_details)
        binding.rowPrivacyPolicy.rowTitle.text = getString(R.string.second_fragment_label)
        binding.rowPrivacyPolicy.rowSubtitle.text = getString(R.string.about_row_privacy_subtitle)
        binding.rowPrivacyPolicy.rowContainer.setOnClickListener {
            findNavController().navigate(R.id.action_AboutFragment_to_SecondFragment)
        }

        binding.rowChangelog.rowIcon.setImageResource(android.R.drawable.ic_menu_recent_history)
        binding.rowChangelog.rowTitle.text = getString(R.string.changelog_label)
        binding.rowChangelog.rowSubtitle.text = getString(R.string.changelog_row_subtitle)
        binding.rowChangelog.rowContainer.setOnClickListener {
            findNavController().navigate(R.id.action_AboutFragment_to_ChangelogFragment)
        }

        binding.rowSupportDev.rowIcon.setImageResource(android.R.drawable.ic_menu_agenda)
        binding.rowSupportDev.rowTitle.text = getString(R.string.about_row_support_title)
        binding.rowSupportDev.rowSubtitle.text = getString(R.string.about_row_support_subtitle)
        binding.rowSupportDev.rowContainer.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://buy.stripe.com/14AfZbf8WdRjdxXfWxbjW00")))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
