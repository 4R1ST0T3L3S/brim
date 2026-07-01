package com.david.waterreminder

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun themeModeLabelRes_light() {
        assertEquals(R.string.theme_light, themeModeLabelRes(AppCompatDelegate.MODE_NIGHT_NO))
    }

    @Test
    fun themeModeLabelRes_dark() {
        assertEquals(R.string.theme_dark, themeModeLabelRes(AppCompatDelegate.MODE_NIGHT_YES))
    }

    @Test
    fun themeModeLabelRes_followSystem() {
        assertEquals(
            R.string.theme_system_default,
            themeModeLabelRes(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        )
    }

    @Test
    fun themeModeLabelRes_unrecognized_fallsBackToSystemDefault() {
        assertEquals(R.string.theme_system_default, themeModeLabelRes(-999))
    }

    @Test
    fun themeModeDialogIndex_followSystem() {
        assertEquals(0, themeModeDialogIndex(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
    }

    @Test
    fun themeModeDialogIndex_light() {
        assertEquals(1, themeModeDialogIndex(AppCompatDelegate.MODE_NIGHT_NO))
    }

    @Test
    fun themeModeDialogIndex_dark() {
        assertEquals(2, themeModeDialogIndex(AppCompatDelegate.MODE_NIGHT_YES))
    }

    @Test
    fun themeModeDialogIndex_unrecognized_coercesToZero() {
        assertEquals(0, themeModeDialogIndex(-999))
    }

    @Test
    fun themeModeDialogIndex_roundTripsWithThemeModes() {
        THEME_MODES.forEach { mode ->
            assertEquals(mode, THEME_MODES[themeModeDialogIndex(mode)])
        }
    }

    // ─── Fresh-install default ───

    @Test
    fun defaultThemeMode_isLight() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, DEFAULT_THEME_MODE)
    }

    @Test
    fun defaultThemeMode_labelIsLight() {
        assertEquals(R.string.theme_light, themeModeLabelRes(DEFAULT_THEME_MODE))
    }

    @Test
    fun defaultThemeMode_dialogSelectsLight() {
        assertEquals(1, themeModeDialogIndex(DEFAULT_THEME_MODE))
    }
}
