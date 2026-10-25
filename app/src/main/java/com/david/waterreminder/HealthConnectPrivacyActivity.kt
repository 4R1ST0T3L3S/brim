package com.david.waterreminder

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.david.waterreminder.databinding.FragmentPrivacypolicyBinding

/**
 * The privacy policy, opened by Health Connect (it requires one). Separate so [MainActivity] stays private.
 * May start in a fresh process, so it applies the user's theme itself.
 */
class HealthConnectPrivacyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeMode = waterPrefs().getInt(PREF_THEME_MODE, DEFAULT_THEME_MODE)
        setTheme(themeStyleRes(themeMode))
        AppCompatDelegate.setDefaultNightMode(nightModeFor(themeMode))
        super.onCreate(savedInstanceState)

        val binding = FragmentPrivacypolicyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // No app bar, so keep the text clear of the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        // Dark bar icons in light themes, so they stay visible.
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }
}
