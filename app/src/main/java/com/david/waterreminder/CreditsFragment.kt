package com.david.waterreminder

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.david.waterreminder.databinding.FragmentCreditsBinding

/**
 * Licenses & Credits. The sentences come from strings.xml so they can be translated; the names,
 * licence names and links below are never translated, so they stay here as plain data.
 */
class CreditsFragment : Fragment() {

    private var _binding: FragmentCreditsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreditsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textLicenseLinks.text = paragraphs(
            link(getString(R.string.credits_source_code), SOURCE_URL),
            link(getString(R.string.credits_full_license), AGPL_URL),
        )

        binding.textSounds.text = paragraphs(
            SOUNDS.map { sound ->
                lines(
                    bullet(getString(sound.sentence, sound.title, sound.author, sound.license)),
                    sound.url,
                )
            } + link(CC_BY, CC_BY_URL)
        )

        binding.textLibraries.text = paragraphs(
            LIBRARIES.groupBy { it.license }.map { (license, libraries) ->
                lines(libraries.map { bullet(getString(R.string.credits_library, it.name, it.author)) } +
                    link(license.name, license.url))
            }
        )
    }

    private fun link(label: String, url: String) = lines(getString(R.string.credits_link_label, label), url)

    private fun bullet(text: String) = getString(R.string.credits_list_item, text)

    private fun lines(vararg items: String) = items.joinToString("\n")
    private fun lines(items: List<String>) = items.joinToString("\n")

    private fun paragraphs(vararg items: String) = items.joinToString("\n\n")
    private fun paragraphs(items: List<String>) = items.joinToString("\n\n")

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class Sound(
        @StringRes val sentence: Int,
        val title: String,
        val author: String,
        val license: String,
        val url: String,
    )

    private class License(val name: String, val url: String)

    private class Library(val name: String, val author: String, val license: License)

    private companion object {
        const val SOURCE_URL = "https://codeberg.org/DavidCRicardo/FillMeUp"
        const val AGPL_URL = "https://www.gnu.org/licenses/agpl-3.0.html"

        const val CC_BY = "CC BY 4.0"
        // Link URLs end without a slash: autoLink leaves a trailing "/" out of the link.
        const val CC_BY_URL = "https://creativecommons.org/licenses/by/4.0"

        // Keep in step with the Credits table in README.md: it carries the CC BY attribution,
        // and anything added to res/raw/ belongs in both.
        val SOUNDS = listOf(
            Sound(R.string.credits_sound_licensed, "Water Drop Tap 4", "AleXZavesa", CC_BY,
                "https://freesound.org/s/853900"),
            Sound(R.string.credits_sound_public_domain, "water drop", "IRF1010N", "CC0 1.0",
                "https://freesound.org/s/661630"),
        )

        val APACHE = License("Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0")
        val GPL_CE = License("GPL-2.0 with the Classpath Exception", "https://openjdk.org/legal/gplv2+ce.html")

        // Everything bundled into the APK (releaseRuntimeClasspath, plus desugaring), grouped
        // under its licence in this order.
        val LIBRARIES = listOf(
            Library("AndroidX", "The Android Open Source Project", APACHE),
            Library("Material Components for Android", "Google", APACHE),
            Library("MPAndroidChart", "Philipp Jahoda", APACHE),
            Library("TapTargetView", "Keepsafe", APACHE),
            Library("Kotlin Standard Library and kotlinx libraries", "JetBrains", APACHE),
            Library("Desugared JDK libraries", "Google", GPL_CE),
        )
    }
}
