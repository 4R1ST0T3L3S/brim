package com.david.waterreminder

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WaterWidgetTest {

    // ─── Which amounts get a button ───────────────────────────────

    /** Wide enough for three, so the widget shows the whole Quick log buttons selection. */
    private fun FakePrefs.wideAmounts() = widgetAmounts(WidgetSizeBand.WIDE)

    @Test
    fun widgetAmounts_followsTheQuickLogSelection() {
        val prefs = FakePrefs(
            mapOf(
                PREF_QUICK_AMOUNTS_ML to "100,200,300,400",
                PREF_NOTIFICATION_AMOUNTS_ML to "200,400",
            )
        )
        assertEquals(listOf(200, 400), prefs.wideAmounts())
    }

    /** Nobody has opened the picker: the selection derives, and the widget derives with it. */
    @Test
    fun widgetAmounts_derivesWhenNothingIsPicked() {
        val prefs = FakePrefs(mapOf(PREF_QUICK_AMOUNTS_ML to "100,200,300,400"))
        assertEquals(listOf(100, 200, 300), prefs.wideAmounts())
    }

    /** An amount deleted from the home screen can't linger as a phantom widget button. */
    @Test
    fun widgetAmounts_dropsWhatTheHomeScreenNoLongerOffers() {
        val prefs = FakePrefs(
            mapOf(
                PREF_QUICK_AMOUNTS_ML to "100,200",
                PREF_NOTIFICATION_AMOUNTS_ML to "200,500",
            )
        )
        assertEquals(listOf(200), prefs.wideAmounts())
    }

    /** Every button turned off is a real state — the widget drops its row rather than inventing one. */
    @Test
    fun widgetAmounts_emptyWhenTheSelectionIsEmpty() {
        val prefs = FakePrefs(mapOf(PREF_NOTIFICATION_AMOUNTS_ML to ""))
        assertTrue(prefs.wideAmounts().isEmpty())
    }

    @Test
    fun widgetAmounts_defaultsOnAFreshInstall() {
        assertEquals(DEFAULT_QUICK_AMOUNTS_ML, FakePrefs().wideAmounts())
    }

    // ─── How many of them the width has room for ──────────────────

    @Test
    fun band_wideOnceThereIsRoom() {
        assertEquals(WidgetSizeBand.WIDE, widgetSizeBand(THREE_CHIP_MIN_WIDTH_DP))
        assertEquals(WidgetSizeBand.WIDE, widgetSizeBand(250))   // 4 cells, where it is placed
        assertEquals(WidgetSizeBand.WIDE, widgetSizeBand(246))   // 3 cells on a typical phone
    }

    @Test
    fun band_compactWhenNarrow() {
        assertEquals(WidgetSizeBand.COMPACT, widgetSizeBand(THREE_CHIP_MIN_WIDTH_DP - 1))
        assertEquals(WidgetSizeBand.COMPACT, widgetSizeBand(164))  // 2 cells on a typical phone
        assertEquals(WidgetSizeBand.COMPACT, widgetSizeBand(110))  // 2 cells at the declared floor
    }

    /** A narrow card carries fewer chips and smaller type; a wide one neither. */
    @Test
    fun band_compactIsSmallerInBothSenses() {
        assertTrue(WidgetSizeBand.COMPACT.chips < WidgetSizeBand.WIDE.chips)
        assertTrue(WidgetSizeBand.COMPACT.chipTextSp < WidgetSizeBand.WIDE.chipTextSp)
    }

    /** Narrowing the card drops a chip; it must never drop the pair the widget is for. */
    @Test
    fun band_neverBelowTwoChips() {
        assertTrue(WidgetSizeBand.entries.all { it.chips >= 2 })
    }

    /**
     * The percentage is on the goal line at every width, so the header slot is left for the
     * goal-reached banner — which only a card with room to spare announces.
     */
    @Test
    fun band_onlyWideAnnouncesTheGoal() {
        assertTrue(WidgetSizeBand.WIDE.spellsOutGoalReached)
        assertFalse(WidgetSizeBand.COMPACT.spellsOutGoalReached)
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

    /** The trim is the widget's own — a narrow card must not rewrite the stored selection. */
    @Test
    fun widgetAmounts_narrowCardLeavesTheSelectionAlone() {
        val prefs = FakePrefs(
            mapOf(
                PREF_QUICK_AMOUNTS_ML to "100,200,500",
                PREF_NOTIFICATION_AMOUNTS_ML to "100,200,500",
            )
        )
        prefs.widgetAmounts(WidgetSizeBand.COMPACT)
        assertEquals("100,200,500", prefs.values[PREF_NOTIFICATION_AMOUNTS_ML])
        assertEquals(listOf(100, 200, 500), prefs.notificationAmounts())
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

    @Test
    fun palette_followSystemUsesTheHostNightMode() {
        val mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        assertEquals(WidgetPalette.LIGHT, widgetPaletteFor(mode, systemIsNight = false))
        assertEquals(WidgetPalette.DARK, widgetPaletteFor(mode, systemIsNight = true))
    }

    /** Prefs can arrive from an import or an older install, so an unknown value must still draw. */
    @Test
    fun palette_unknownModeFallsBackToTheHostNightMode() {
        assertEquals(WidgetPalette.LIGHT, widgetPaletteFor(9999, systemIsNight = false))
        assertEquals(WidgetPalette.DARK, widgetPaletteFor(9999, systemIsNight = true))
    }

    // ─── The numbers on it ────────────────────────────────────────

    @Test
    fun state_readsTodaysTotalAgainstThePersonalGoal() {
        val prefs = FakePrefs(
            mapOf(
                "date" to LocalDate.now().toString(),
                "intake_ml" to 750,
                "personal_goal_ml" to 3000,
                PREF_UNIT to UNIT_ML,
            )
        )
        val state = prefs.widgetState(WidgetPalette.LIGHT, WidgetSizeBand.WIDE)
        assertEquals(750, state.progress.intakeMl)
        assertEquals(3000, state.progress.goalMl)
        assertEquals(25, state.progress.percent)
        assertEquals(UNIT_ML, state.unit)
    }

    /** Nothing rolls the day over out on the home screen — yesterday's total is not today's. */
    @Test
    fun state_showsNothingForAStaleDay() {
        val prefs = FakePrefs(
            mapOf(
                "date" to LocalDate.now().minusDays(1).toString(),
                "intake_ml" to 1200,
            )
        )
        assertEquals(0, prefs.widgetState(WidgetPalette.LIGHT, WidgetSizeBand.WIDE).progress.intakeMl)
    }

    @Test
    fun state_fallsBackToTheRecommendedMinimum() {
        val prefs = FakePrefs(mapOf("date" to LocalDate.now().toString(), "intake_ml" to 1500))
        val state = prefs.widgetState(WidgetPalette.DARK, WidgetSizeBand.WIDE)
        assertEquals(DEFAULT_DAILY_GOAL_ML, state.progress.goalMl)
        assertTrue(state.progress.goalReached)
    }

    @Test
    fun state_carriesTheChipsTheWidthAllows() {
        val prefs = FakePrefs(
            mapOf(
                "date" to LocalDate.now().toString(),
                PREF_QUICK_AMOUNTS_ML to "100,200,500",
                PREF_NOTIFICATION_AMOUNTS_ML to "100,200,500",
            )
        )
        val wide = prefs.widgetState(WidgetPalette.LIGHT, WidgetSizeBand.WIDE)
        val compact = prefs.widgetState(WidgetPalette.LIGHT, WidgetSizeBand.COMPACT)
        assertEquals(listOf(100, 200, 500), wide.amountsMl)
        assertEquals(listOf(100, 200), compact.amountsMl)
    }

    /** Every number drawn on the card is formatted with this unit, so it can't go unasserted. */
    @Test
    fun state_readsTheChosenUnit() {
        val prefs = FakePrefs(
            mapOf(
                "date" to LocalDate.now().toString(),
                "intake_ml" to 750,
                PREF_UNIT to UNIT_OZ,
            )
        )
        val state = prefs.widgetState(WidgetPalette.LIGHT, WidgetSizeBand.WIDE)
        assertEquals(UNIT_OZ, state.unit)
        assertEquals("25.4 oz", state.progress.intakeMl.fmt(state.unit))
        assertEquals("50.7 oz", DEFAULT_DAILY_GOAL_ML.fmt(state.unit))
    }

    /** An install from before the unit was ever chosen still has to draw something. */
    @Test
    fun state_fallsBackToMlWhenNoUnitIsStored() {
        assertEquals(UNIT_ML, FakePrefs().widgetState(WidgetPalette.LIGHT, WidgetSizeBand.WIDE).unit)
    }

    /** The bar is driven by this percentage, and it must never claim more than a full card. */
    @Test
    fun state_percentCapsAtOneHundred() {
        val prefs = FakePrefs(
            mapOf(
                "date" to LocalDate.now().toString(),
                "intake_ml" to 5000,
                "personal_goal_ml" to 2000,
            )
        )
        assertEquals(100, prefs.widgetState(WidgetPalette.LIGHT, WidgetSizeBand.WIDE).progress.percent)
    }
}
