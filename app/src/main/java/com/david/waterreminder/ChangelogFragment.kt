package com.david.waterreminder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class ChangelogFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_changelog, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val entries = requireContext().loadChangelogEntries()
        val content = if (entries.isEmpty()) {
            getString(R.string.changelog_no_entries)
        } else {
            entries.joinToString("\n\n${getString(R.string.changelog_separator)}\n\n") { entry ->
                "${getString(R.string.changelog_version_header, entry.versionName)}\n${entry.text}"
            }
        }

        view.findViewById<TextView>(R.id.text_changelog_content).text = content
    }
}
