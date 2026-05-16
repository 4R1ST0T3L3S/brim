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

        val assets = requireContext().assets
        val versionMap = assets.open("changelogs/versions.txt").bufferedReader()
            .readLines()
            .mapNotNull { line ->
                val parts = line.split("=")
                if (parts.size == 2) parts[0].trim().toIntOrNull()?.let { it to parts[1].trim() }
                else null
            }
            .toMap()

        val content = assets.list("changelogs")
            ?.filter { it != "versions.txt" }
            ?.mapNotNull { name ->
                val code = name.removeSuffix(".txt").toIntOrNull() ?: return@mapNotNull null
                val versionName = versionMap[code] ?: code.toString()
                val text = assets.open("changelogs/$name").bufferedReader().readText().trim()
                Triple(code, versionName, text)
            }
            ?.sortedByDescending { it.first }
            ?.joinToString("\n\n${getString(R.string.changelog_separator)}\n\n") { (_, versionName, text) ->
                "${getString(R.string.changelog_version_header, versionName)}\n$text"
            }
            ?: getString(R.string.changelog_no_entries)

        view.findViewById<TextView>(R.id.text_changelog_content).text = content
    }
}
