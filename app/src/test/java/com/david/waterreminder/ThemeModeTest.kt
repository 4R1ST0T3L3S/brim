package com.david.waterreminder

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemeModeTest {

    /** The theme modes that are app-specific values rather than AppCompatDelegate night modes. */
    private val TINTED = listOf(THEME_MODE_BLUE_DARK, THEME_MODE_BLUE_LIGHT)

    @Test
    fun themeModeLabelRes_light() {
        assertEquals(R.string.theme_light, themeModeLabelRes(AppCompatDelegate.MODE_NIGHT_NO))
    }

    @Test
    fun themeModeLabelRes_dark() {
        assertEquals(R.string.theme_dark, themeModeLabelRes(AppCompatDelegate.MODE_NIGHT_YES))
    }

    @Test
    fun themeModeLabelRes_blueDark() {
        assertEquals(R.string.theme_blue_dark, themeModeLabelRes(THEME_MODE_BLUE_DARK))
    }

    @Test
    fun themeModeLabelRes_blueLight() {
        assertEquals(R.string.theme_blue_light, themeModeLabelRes(THEME_MODE_BLUE_LIGHT))
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
    fun themeModeDialogIndex_blueDark() {
        assertEquals(4, themeModeDialogIndex(THEME_MODE_BLUE_DARK))
    }

    @Test
    fun themeModeDialogIndex_blueLight() {
        assertEquals(3, themeModeDialogIndex(THEME_MODE_BLUE_LIGHT))
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

    // ─── Dark Blue is a dark theme wearing its own surface colors ───

    @Test
    fun nightModeFor_blueDark_isDark() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, nightModeFor(THEME_MODE_BLUE_DARK))
    }

    @Test
    fun nightModeFor_blueLight_isLight() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, nightModeFor(THEME_MODE_BLUE_LIGHT))
    }

    @Test
    fun nightModeFor_realNightModes_passThrough() {
        (THEME_MODES.toList() - TINTED).forEach { mode ->
            assertEquals(mode, nightModeFor(mode))
        }
    }

    @Test
    fun themeStyleRes_blueDark_usesBlueDarkTheme() {
        assertEquals(R.style.Theme_WaterReminder_BlueDark, themeStyleRes(THEME_MODE_BLUE_DARK))
    }

    @Test
    fun themeStyleRes_blueLight_usesBlueLightTheme() {
        assertEquals(R.style.Theme_WaterReminder_BlueLight, themeStyleRes(THEME_MODE_BLUE_LIGHT))
    }

    @Test
    fun themeStyleRes_otherModes_useDefaultTheme() {
        (THEME_MODES.toList() - TINTED).forEach { mode ->
            assertEquals(R.style.Theme_WaterReminder, themeStyleRes(mode))
        }
    }

    @Test
    fun tintedThemeModes_haveDistinctStoredValues() {
        assertEquals(TINTED.size, TINTED.toSet().size)
    }

    @Test
    fun tintedThemeModes_doNotCollideWithNightModes() {
        val nightModes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_UNSPECIFIED,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
        )
        nightModes.forEach { mode ->
            TINTED.forEach { tinted ->
                assertNotEquals(mode.toLong(), tinted.toLong())
            }
        }
    }

    // ─── Fresh-install default ───

    @Test
    fun defaultThemeMode_isDark() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, DEFAULT_THEME_MODE)
    }

    @Test
    fun defaultThemeMode_labelIsDark() {
        assertEquals(R.string.theme_dark, themeModeLabelRes(DEFAULT_THEME_MODE))
    }

    @Test
    fun defaultThemeMode_dialogSelectsDark() {
        assertEquals(2, themeModeDialogIndex(DEFAULT_THEME_MODE))
    }
}
