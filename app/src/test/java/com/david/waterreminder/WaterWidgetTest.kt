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

    // ─── How much of the widget a size buys ───────────────────────

    @Test
    fun band_wideOnceThereIsRoom() {
        assertEquals(WidgetSizeBand.WIDE, widgetSizeBand(THREE_CHIP_MIN_WIDTH_DP, row = false))
        assertEquals(WidgetSizeBand.WIDE, widgetSizeBand(246, row = false))   // 3 cells, typical phone
    }

    @Test
    fun band_compactWhenNarrow() {
        assertEquals(
            WidgetSizeBand.COMPACT,
            widgetSizeBand(THREE_CHIP_MIN_WIDTH_DP - 1, row = false)
        )
        assertEquals(WidgetSizeBand.COMPACT, widgetSizeBand(110, row = false))  // declared floor
    }

    /** Narrowing the card drops a chip; it must never drop the pair the card is for. */
    @Test
    fun band_cardNeverBelowTwoChips() {
        assertTrue(WidgetSizeBand.entries.filter { !it.isRow }.all { it.chips >= 2 })
    }

    /** The provider says which widget it is; no width turns one into the other. */
    @Test
    fun band_widgetKindComesFromTheProvider() {
        listOf(110, 250, 370, 500).forEach { width ->
            assertFalse(widgetSizeBand(width, row = false).isRow)
            assertTrue(widgetSizeBand(width, row = true).isRow)
        }
    }

    @Test
    fun band_rowChipsFollowTheWidth() {
        assertEquals(WidgetSizeBand.ROW, widgetSizeBand(370, row = true))        // 5×1
        assertEquals(WidgetSizeBand.ROW, widgetSizeBand(320, row = true))        // 4×1, 4 columns
        assertEquals(WidgetSizeBand.ROW_COMPACT, widgetSizeBand(296, row = true)) // 4×1, 5 columns
        assertEquals(WidgetSizeBand.ROW_NARROW, widgetSizeBand(228, row = true)) // 3×1
        assertEquals(WidgetSizeBand.ROW_TINY, widgetSizeBand(150, row = true))   // 2×1
    }

    /** Whatever the row gives up, it keeps a button: a widget for logging that can't log is broken. */
    @Test
    fun band_rowNeverLosesItsLastChip() {
        assertTrue(WidgetSizeBand.entries.all { it.chips >= 1 })
    }

    /** A 4×1 on a 360dp phone measured 320dp: three chosen amounts must all show there. */
    @Test
    fun widgetAmounts_fourByOneShowsAllThree() {
        val prefs = FakePrefs(
            mapOf(
                PREF_QUICK_AMOUNTS_ML to "100,200,250",
                PREF_NOTIFICATION_AMOUNTS_ML to "100,200,250",
            )
        )
        assertEquals(listOf(100, 200, 250), prefs.widgetAmounts(widgetSizeBand(320, row = true)))
    }

    /** One chosen amount in the two-chip card band has room to spare, so it keeps the full type. */
    @Test
    fun chipText_fullSizeWhenASlotIsEmpty() {
        assertEquals(13f, WidgetSizeBand.COMPACT.chipTextSpFor(1))
        assertEquals(11f, WidgetSizeBand.COMPACT.chipTextSpFor(2))
        assertEquals(13f, WidgetSizeBand.WIDE.chipTextSpFor(3))
    }

    /** The row's type is measured per label, so every row band starts it at the full size. */
    @Test
    fun chipText_rowAlwaysStartsFull() {
        WidgetSizeBand.entries.filter { it.isRow }.forEach { band ->
            assertEquals(WidgetSizeBand.WIDE.chipTextSp, band.chipTextSpFor(band.chips))
        }
    }

    // ─── Fitting a long label ─────────────────────────────────────

    /** A label that fits keeps the band's size: fitting only ever shrinks. */
    @Test
    fun chipFit_keepsTheSizeWhenTheLabelFits() {
        assertEquals(11f, fitChipTextSp(11f, labelWidthDp = 40f, chipWidthDp = 50f))
    }

    /** "+1250 ml" three to a 4×1: 50dp of label in a 47dp chip comes down in half-sp steps. */
    @Test
    fun chipFit_shrinksToTheRoomInHalfSteps() {
        // 39dp of room for 50dp of label at 11sp → 8.58sp → floored to 8.5, then the 9sp floor.
        assertEquals(MIN_CHIP_TEXT_SP, fitChipTextSp(11f, labelWidthDp = 50f, chipWidthDp = 47f))
        // 42dp of room for 46dp at 11sp → 10.04 → 10.
        assertEquals(10f, fitChipTextSp(11f, labelWidthDp = 46f, chipWidthDp = 50f))
    }

    /** Rounded *down* to the half sp: 10.4 would round up to 10.5 and no longer fit. */
    @Test
    fun chipFit_roundsDownNotToNearest() {
        // 42dp of room for 44.4dp at 11sp → 10.41sp.
        assertEquals(10f, fitChipTextSp(11f, labelWidthDp = 44.4f, chipWidthDp = 50f))
    }

    /** A label exactly as wide as the room fits. */
    @Test
    fun chipFit_exactFitKeepsTheSize() {
        assertEquals(11f, fitChipTextSp(11f, labelWidthDp = 42f, chipWidthDp = 50f))
    }

    /** No room at all still yields a size, not a negative one. */
    @Test
    fun chipFit_neverBelowTheFloor() {
        assertEquals(MIN_CHIP_TEXT_SP, fitChipTextSp(13f, labelWidthDp = 60f, chipWidthDp = 0f))
    }

    /**
     * The 4×1 measured on a phone: 320dp with "750 ml" (a 10 + 59dp text block) drew two chips
     * 80dp wide. The layout's figures have to land there, or every fit is off by the difference.
     */
    @Test
    fun rowChipWidth_matchesTheMeasuredWidget() {
        assertEquals(80f, rowChipWidthDp(320, textBlockDp = 69f, chips = 2), 2f)
        assertEquals(51.7f, rowChipWidthDp(320, textBlockDp = 69f, chips = 3), 0.1f)
    }

    /** ROW_TINY hides the text block, margin and all: the chip gets everything past the ring. */
    @Test
    fun rowChipWidth_tinyRowHasNoTextBlock() {
        assertEquals(70f, rowChipWidthDp(150, textBlockDp = 0f, chips = 1), 0.01f)
    }

    /** Only the band that has to give something up hides the total. */
    @Test
    fun band_onlyTheTinyRowHidesTheTotal() {
        assertEquals(
            listOf(WidgetSizeBand.ROW_TINY),
            WidgetSizeBand.entries.filter { !it.showsTotal }
        )
    }

    /** Narrowing a row only ever takes away, as COMPACT is to WIDE. */
    @Test
    fun band_rowNarrowingNeverAddsChips() {
        val widestFirst = listOf(500, 340, 320, 300, 262, 240, 208, 180, 110)
            .map { widgetSizeBand(it, row = true) }
        widestFirst.zipWithNext().forEach { (wider, narrower) ->
            assertTrue(narrower.chips <= wider.chips)
            assertTrue(narrower.chipTextSp <= wider.chipTextSp)
        }
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
