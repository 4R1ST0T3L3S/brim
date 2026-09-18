package com.david.waterreminder

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Only what the widget decides for itself: what it reads through [notificationProgress] and
 * [notificationAmounts] is UnitsTest's and ReminderNotificationTest's to pin down.
 */
class WaterWidgetTest {

    // ─── Which amounts get a button ───────────────────────────────

    /** The selection, not the home row — the whole of what this adds to [notificationAmounts]. */
    @Test
    fun widgetAmounts_followsTheQuickLogSelection() {
        val prefs = FakePrefs(
            mapOf(
                PREF_QUICK_AMOUNTS_ML to "100,200,300,400",
                PREF_NOTIFICATION_AMOUNTS_ML to "200,400",
            )
        )
        assertEquals(listOf(200, 400), prefs.widgetAmounts(WidgetSizeBand.WIDE))
    }

    /** A three-amount selection loses exactly one button when the card is dragged narrow. */
    @Test
    fun widgetAmounts_narrowCardKeepsTheFirstTwo() {
        val prefs = FakePrefs(
            mapOf(
                PREF_QUICK_AMOUNTS_ML to "100,200,500",
                PREF_NOTIFICATION_AMOUNTS_ML to "100,200,500",
            )
        )
        assertEquals(listOf(100, 200, 500), prefs.widgetAmounts(WidgetSizeBand.WIDE))
        assertEquals(listOf(100, 200), prefs.widgetAmounts(WidgetSizeBand.COMPACT))
    }

    // ─── How much of the card a width buys ────────────────────────

    @Test
    fun band_wideOnceThereIsRoom() {
        assertEquals(WidgetSizeBand.WIDE, widgetSizeBand(THREE_CHIP_MIN_WIDTH_DP))
        assertEquals(WidgetSizeBand.WIDE, widgetSizeBand(246))   // 3 cells on a typical phone
    }

    @Test
    fun band_compactWhenNarrow() {
        assertEquals(WidgetSizeBand.COMPACT, widgetSizeBand(THREE_CHIP_MIN_WIDTH_DP - 1))
        assertEquals(WidgetSizeBand.COMPACT, widgetSizeBand(110))  // 2 cells at the declared floor
    }

    /** Narrowing the card drops a chip; it must never drop the pair the widget is for. */
    @Test
    fun band_neverBelowTwoChips() {
        assertTrue(WidgetSizeBand.entries.all { it.chips >= 2 })
    }

    /**
     * COMPACT is WIDE with things taken away, never the other way round. Stated as an ordering
     * rather than as the four literals, so it survives a future band and still fails if one of
     * them is flipped — which nothing else would catch: the provider reads all three fields and
     * no other suite touches them.
     */
    @Test
    fun band_compactIsNeverMoreGenerousThanWide() {
        val wide = WidgetSizeBand.WIDE
        val compact = WidgetSizeBand.COMPACT
        assertTrue(compact.chips <= wide.chips)
        assertTrue(compact.chipTextSp <= wide.chipTextSp)
        assertFalse(compact.spellsOutGoalReached && !wide.spellsOutGoalReached)
    }

    // ─── Colours ──────────────────────────────────────────────────

    @Test
    fun palette_followsTheChosenTheme() {
        assertEquals(
            WidgetPalette.LIGHT,
            widgetPaletteFor(AppCompatDelegate.MODE_NIGHT_NO, systemIsNight = true)
        )
        assertEquals(
            WidgetPalette.DARK,
            widgetPaletteFor(AppCompatDelegate.MODE_NIGHT_YES, systemIsNight = false)
        )
        assertEquals(
            WidgetPalette.BLUE_LIGHT,
            widgetPaletteFor(THEME_MODE_BLUE_LIGHT, systemIsNight = true)
        )
        assertEquals(
            WidgetPalette.BLUE_DARK,
            widgetPaletteFor(THEME_MODE_BLUE_DARK, systemIsNight = false)
        )
    }

    /** "Follow system" and an unrecognised value — an imported pref — both use the host's mode. */
    @Test
    fun palette_fallsBackToTheHostNightMode() {
        listOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, 9999).forEach { mode ->
            assertEquals(WidgetPalette.LIGHT, widgetPaletteFor(mode, systemIsNight = false))
            assertEquals(WidgetPalette.DARK, widgetPaletteFor(mode, systemIsNight = true))
        }
    }

    // ─── What the card is handed to draw ──────────────────────────

    /** Reads zero for a finished day, which is what fails if [widgetState] ever uses [dayTotal]. */
    @Test
    fun state_showsNothingForAStaleDay() {
        val prefs = FakePrefs(
            mapOf(
                "date" to LocalDate.now().minusDays(1).toString(),
                "intake_ml" to 1200,
            )
        )
        assertEquals(0, prefs.widgetState(WidgetSizeBand.WIDE).progress.intakeMl)
    }
}
