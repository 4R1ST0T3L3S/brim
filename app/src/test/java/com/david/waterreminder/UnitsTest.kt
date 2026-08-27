package com.david.waterreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

class UnitsTest {

    @Test
    fun toMl_ml_validInput() {
        assertEquals(250, "250".toMl(UNIT_ML))
    }

    @Test
    fun toMl_cl_validInput() {
        assertEquals(250, "25".toMl(UNIT_CL))
    }

    @Test
    fun toMl_cl_decimal_dot() {
        assertEquals(5, "0.5".toMl(UNIT_CL))
    }

    @Test
    fun toMl_cl_decimal_comma() {
        assertEquals(5, "0,5".toMl(UNIT_CL))
    }

    @Test
    fun toMl_whitespace_trimmed() {
        assertEquals(100, "  100  ".toMl(UNIT_ML))
    }

    @Test
    fun toMl_empty_returnsNull() {
        assertNull("".toMl(UNIT_ML))
    }

    @Test
    fun toMl_nonNumeric_returnsNull() {
        assertNull("abc".toMl(UNIT_ML))
    }

    @Test
    fun toMl_overflow_returnsNull() {
        assertNull("11111111111".toMl(UNIT_ML))
    }

    @Test
    fun toMl_overflow_cl_returnsNull() {
        assertNull("11111111111".toMl(UNIT_CL))
    }

    @Test
    fun toMl_negative_returnsNull() {
        assertNull("-100".toMl(UNIT_ML))
    }

    @Test
    fun toMl_intMaxValue_isValid() {
        assertEquals(Int.MAX_VALUE, "${Int.MAX_VALUE}".toMl(UNIT_ML))
    }

    @Test
    fun toMl_aboveIntMax_returnsNull() {
        assertNull("${Int.MAX_VALUE.toLong() + 1}".toMl(UNIT_ML))
    }

    // ─── toValidCustomMl ──────────────────────────────────────────

    @Test
    fun toValidCustomMl_validAmount() {
        assertEquals(250, "250".toValidCustomMl(UNIT_ML))
    }

    @Test
    fun toValidCustomMl_cl() {
        assertEquals(250, "25".toValidCustomMl(UNIT_CL))
    }

    @Test
    fun toValidCustomMl_zero_returnsNull() {
        assertNull("0".toValidCustomMl(UNIT_ML))
    }

    @Test
    fun toValidCustomMl_negative_returnsNull() {
        assertNull("-5".toValidCustomMl(UNIT_ML))
    }

    @Test
    fun toValidCustomMl_empty_returnsNull() {
        assertNull("".toValidCustomMl(UNIT_ML))
    }

    @Test
    fun toValidCustomMl_atMax() {
        assertEquals(CUSTOM_MAX_ML, "$CUSTOM_MAX_ML".toValidCustomMl(UNIT_ML))
    }

    @Test
    fun toValidCustomMl_aboveMax_returnsNull() {
        assertNull("${CUSTOM_MAX_ML + 1}".toValidCustomMl(UNIT_ML))
    }

    @Test
    fun toValidCustomMl_overflow_returnsNull() {
        assertNull("11111111111".toValidCustomMl(UNIT_ML))
    }

    // ─── toValidGoalMl ────────────────────────────────────────────

    @Test
    fun toValidGoalMl_validGoal() {
        assertEquals(2000, "2000".toValidGoalMl(UNIT_ML))
    }

    @Test
    fun toValidGoalMl_lowerBound() {
        assertEquals(100, "100".toValidGoalMl(UNIT_ML))
    }

    @Test
    fun toValidGoalMl_upperBound() {
        assertEquals(10_000, "10000".toValidGoalMl(UNIT_ML))
    }

    @Test
    fun toValidGoalMl_cl_valid() {
        assertEquals(2000, "200".toValidGoalMl(UNIT_CL))
    }

    @Test
    fun toValidGoalMl_belowMin_returnsNull() {
        assertNull("99".toValidGoalMl(UNIT_ML))
    }

    @Test
    fun toValidGoalMl_aboveMax_returnsNull() {
        assertNull("10001".toValidGoalMl(UNIT_ML))
    }

    @Test
    fun toValidGoalMl_zero_returnsNull() {
        assertNull("0".toValidGoalMl(UNIT_ML))
    }

    @Test
    fun toValidGoalMl_empty_returnsNull() {
        assertNull("".toValidGoalMl(UNIT_ML))
    }

    // ─── toValidDayTotalMl ────────────────────────────────────────

    @Test
    fun toValidDayTotalMl_validTotal() {
        assertEquals(2200, "2200".toValidDayTotalMl(UNIT_ML))
    }

    @Test
    fun toValidDayTotalMl_zero_isAllowed() {
        assertEquals(0, "0".toValidDayTotalMl(UNIT_ML))
    }

    @Test
    fun toValidDayTotalMl_oz() {
        assertEquals(2957, "100".toValidDayTotalMl(UNIT_OZ))
    }

    @Test
    fun toValidDayTotalMl_atMax() {
        assertEquals(10_000, DAY_TOTAL_MAX_ML)
        assertEquals(DAY_TOTAL_MAX_ML, "$DAY_TOTAL_MAX_ML".toValidDayTotalMl(UNIT_ML))
    }

    @Test
    fun toValidDayTotalMl_aboveMax_returnsNull() {
        assertNull("${DAY_TOTAL_MAX_ML + 1}".toValidDayTotalMl(UNIT_ML))
    }

    @Test
    fun toValidDayTotalMl_negative_returnsNull() {
        assertNull("-1".toValidDayTotalMl(UNIT_ML))
    }

    @Test
    fun toValidDayTotalMl_empty_returnsNull() {
        assertNull("".toValidDayTotalMl(UNIT_ML))
    }

    // ─── asDayIntake ──────────────────────────────────────────────

    @Test
    fun asDayIntake_ordinaryTotal_untouched() {
        assertEquals(1_800, 1_800.asDayIntake())
    }

    @Test
    fun asDayIntake_atCeiling() {
        assertEquals(1_000_000, INTAKE_MAX_ML)
        assertEquals(INTAKE_MAX_ML, INTAKE_MAX_ML.asDayIntake())
    }

    @Test
    fun asDayIntake_aboveCeiling_clamped() {
        assertEquals(INTAKE_MAX_ML, (INTAKE_MAX_ML + 1).asDayIntake())
        assertEquals(INTAKE_MAX_ML, Int.MAX_VALUE.asDayIntake())
    }

    /** A clamped day times 100 is the progress-bar maths in FirstFragment — it must stay in Int. */
    @Test
    fun asDayIntake_leavesHeadroomForProgressMath() {
        assertTrue(INTAKE_MAX_ML.toLong() * 100 < Int.MAX_VALUE)
    }

    @Test
    fun asDayIntake_negative_clampedToZero() {
        assertEquals(0, (-50).asDayIntake())
    }

    // ─── editableHistoryDays ──────────────────────────────────────

    @Test
    fun editableHistoryDays_coversTheWeekWithoutToday() {
        val today = LocalDate.now()
        val days = editableHistoryDays()

        assertEquals(WEEKLY_HISTORY_DAYS - 1, days.size)
        assertFalse(days.contains(today))
        assertEquals(today.minusDays((WEEKLY_HISTORY_DAYS - 1).toLong()), days.first())
        assertEquals(today.minusDays(1), days.last())
    }

    @Test
    fun editableHistoryDays_oldestFirst() {
        assertEquals(editableHistoryDays().sorted(), editableHistoryDays())
    }

    // ─── bottle row ───────────────────────────────────────────────

    @Test
    fun bottleCount_oneBottlePer500Ml() {
        assertEquals(1, bottleCount(ML_PER_BOTTLE))
        assertEquals(2, bottleCount(1000))
        assertEquals(3, bottleCount(DEFAULT_DAILY_GOAL_ML))
        assertEquals(20, bottleCount(GOAL_MAX_ML))
    }

    @Test
    fun bottleCount_partialBottleRoundsUp() {
        assertEquals(1, bottleCount(GOAL_MIN_ML))
        assertEquals(3, bottleCount(1200))
        assertEquals(4, bottleCount(1501))
    }

    @Test
    fun bottleFillSteps_emptyAtZeroIntake() {
        assertEquals(listOf(0, 0, 0), bottleFillSteps(0, DEFAULT_DAILY_GOAL_ML))
    }

    /** The reported bug: at the default goal every bottle must read full, none left empty. */
    @Test
    fun bottleFillSteps_allFullAtGoal() {
        assertEquals(
            List(3) { BOTTLE_FILL_STEPS },
            bottleFillSteps(DEFAULT_DAILY_GOAL_ML, DEFAULT_DAILY_GOAL_ML)
        )
    }

    @Test
    fun bottleFillSteps_allFullAtGoalForEveryGoalInRange() {
        for (goal in GOAL_MIN_ML..GOAL_MAX_ML) {
            val steps = bottleFillSteps(goal, goal)
            assertEquals("goal $goal", bottleCount(goal), steps.size)
            assertTrue("goal $goal filled $steps", steps.all { it == BOTTLE_FILL_STEPS })
        }
    }

    @Test
    fun bottleFillSteps_fillsLeftToRight() {
        // 700 ml fills the first 500 ml bottle and reaches 40% of the second.
        assertEquals(listOf(BOTTLE_FILL_STEPS, 2, 0), bottleFillSteps(700, DEFAULT_DAILY_GOAL_ML))
    }

    @Test
    fun bottleFillSteps_bottlesStay500MlWhateverTheGoal() {
        // Same intake, different goals → the filled bottles look identical; only the row length grows.
        val small = bottleFillSteps(1200, DEFAULT_DAILY_GOAL_ML)
        val large = bottleFillSteps(1200, GOAL_MAX_ML)
        assertEquals(listOf(BOTTLE_FILL_STEPS, BOTTLE_FILL_STEPS, 2), small)
        assertEquals(small, large.take(small.size))
        assertEquals(20, large.size)
        assertTrue(large.drop(3).all { it == 0 })
    }

    /** A goal that isn't a round number of bottles: the last bottle spans only the remainder. */
    @Test
    fun bottleFillSteps_lastBottleCoversTheRemainder() {
        assertEquals(listOf(BOTTLE_FILL_STEPS, BOTTLE_FILL_STEPS, 0), bottleFillSteps(1000, 1300))
        assertEquals(listOf(BOTTLE_FILL_STEPS, BOTTLE_FILL_STEPS, 2), bottleFillSteps(1120, 1300))
    }

    @Test
    fun bottleFillSteps_intakePastGoalStaysFull() {
        assertEquals(
            List(3) { BOTTLE_FILL_STEPS },
            bottleFillSteps(DEFAULT_DAILY_GOAL_ML * 3, DEFAULT_DAILY_GOAL_ML)
        )
    }

    @Test
    fun bottleFillSteps_nonsenseGoalStillYieldsOneBottle() {
        assertEquals(listOf(BOTTLE_FILL_STEPS), bottleFillSteps(100, 0))
        assertEquals(listOf(BOTTLE_FILL_STEPS), bottleFillSteps(100, -500))
    }

    // ─── asHourLabel ──────────────────────────────────────────────

    @Test
    fun asHourLabel_padsSingleDigit() {
        assertEquals("08:00", 8.asHourLabel())
    }

    @Test
    fun asHourLabel_doubleDigit() {
        assertEquals("20:00", 20.asHourLabel())
    }

    @Test
    fun asHourLabel_midnight() {
        assertEquals("00:00", 0.asHourLabel())
    }

    // The label is persisted and parsed back, so it must survive a round-trip
    // through a locale whose default digits aren't ASCII.
    @Test
    fun asHourLabel_isLocaleIndependent() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
            assertEquals("08:00", 8.asHourLabel())
        } finally {
            Locale.setDefault(previous)
        }
    }

    // ─── nextIntervalFireTime ─────────────────────────────────────

    private fun at(hour: Int, minute: Int): Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.AUGUST, 14, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.hhmm() =
        String.format(Locale.US, "%02d:%02d", get(Calendar.HOUR_OF_DAY), get(Calendar.MINUTE))

    private fun Calendar.dayOfMonth() = get(Calendar.DAY_OF_MONTH)

    @Test
    fun nextInterval_beforeAnchor_firesAtAnchorToday() {
        val next = nextIntervalFireTime(at(7, 0), 9, 30, 2, 20)
        assertEquals("09:30", next.hhmm())
        assertEquals(14, next.dayOfMonth())
    }

    @Test
    fun nextInterval_afterAnchor_firesAtNextRepeat() {
        val next = nextIntervalFireTime(at(10, 0), 9, 30, 2, 20)
        assertEquals("11:30", next.hhmm())
    }

    @Test
    fun nextInterval_exactlyOnAnchor_movesToNextRepeat() {
        val next = nextIntervalFireTime(at(9, 30), 9, 30, 2, 20)
        assertEquals("11:30", next.hhmm())
    }

    @Test
    fun nextInterval_everyHour_stepsByOne() {
        assertEquals("10:30", nextIntervalFireTime(at(10, 0), 9, 30, 1, 20).hhmm())
    }

    @Test
    fun nextInterval_everyFourHours_stepsByFour() {
        assertEquals("17:30", nextIntervalFireTime(at(14, 0), 9, 30, 4, 20).hhmm())
    }

    // Repeats stop at the go-to-bed hour and pick up at the anchor tomorrow.
    @Test
    fun nextInterval_pastWindowEnd_rollsToAnchorTomorrow() {
        val next = nextIntervalFireTime(at(20, 0), 9, 30, 2, 20)
        assertEquals("09:30", next.hhmm())
        assertEquals(15, next.dayOfMonth())
    }

    @Test
    fun nextInterval_repeatLandingPastWindowEnd_isSkipped() {
        // 09:30 + 2h steps → 19:30 is the last one at or before a 20:00 end; 21:30 is not.
        val next = nextIntervalFireTime(at(19, 45), 9, 30, 2, 20)
        assertEquals("09:30", next.hhmm())
        assertEquals(15, next.dayOfMonth())
    }

    @Test
    fun nextInterval_repeatExactlyAtWindowEndHour_stillFires() {
        // End hour 20 is inclusive, matching how the hourly window behaves.
        assertEquals("20:30", nextIntervalFireTime(at(19, 0), 8, 30, 4, 20).hhmm())
    }

    // The anchor is the user's explicit choice, so it fires even outside the window —
    // only the repeats are bounded. Otherwise a 22:00 reminder would never fire at all.
    @Test
    fun nextInterval_anchorOutsideWindow_stillFiresAtAnchor() {
        val next = nextIntervalFireTime(at(21, 0), 22, 0, 2, 20)
        assertEquals("22:00", next.hhmm())
        assertEquals(14, next.dayOfMonth())
    }

    @Test
    fun nextInterval_anchorOutsideWindow_doesNotRepeatSameDay() {
        val next = nextIntervalFireTime(at(22, 30), 22, 0, 2, 20)
        assertEquals("22:00", next.hhmm())
        assertEquals(15, next.dayOfMonth())
    }

    @Test
    fun nextInterval_lateAnchorNeverExceedsMidnight() {
        // 23:00 + 2h would be hour 25 — must roll to tomorrow, not overflow.
        val next = nextIntervalFireTime(at(23, 30), 23, 0, 2, 23)
        assertEquals("23:00", next.hhmm())
        assertEquals(15, next.dayOfMonth())
    }

    @Test
    fun nextInterval_zeroInterval_treatedAsHourly() {
        // Guards against a malformed entry scheduling an infinite loop of same-time fires.
        assertEquals("10:30", nextIntervalFireTime(at(10, 0), 9, 30, 0, 20).hhmm())
    }

    @Test
    fun nextInterval_preservesAnchorMinute() {
        assertEquals("13:07", nextIntervalFireTime(at(12, 0), 7, 7, 3, 20).hhmm())
    }

    // ─── nextOccurrenceOf ─────────────────────────────────────────

    @Test
    fun nextOccurrence_laterToday_staysToday() {
        val next = at(9, 0).nextOccurrenceOf(14, 30)
        assertEquals("14:30", next.hhmm())
        assertEquals(14, next.dayOfMonth())
    }

    @Test
    fun nextOccurrence_alreadyPast_rollsToTomorrow() {
        val next = at(15, 0).nextOccurrenceOf(14, 30)
        assertEquals("14:30", next.hhmm())
        assertEquals(15, next.dayOfMonth())
    }

    // Exactly now counts as past, or the alarm would be set for the moment it fired.
    @Test
    fun nextOccurrence_exactlyNow_rollsToTomorrow() {
        assertEquals(15, at(14, 30).nextOccurrenceOf(14, 30).dayOfMonth())
    }

    // The get-up hour bounds repeats at the bottom the way bedtime does at the top.
    @Test
    fun nextInterval_repeatBeforeWindowStart_isSkipped() {
        // 05:00 + 1h steps, get up at 08:00 → the 06:00 and 07:00 repeats are skipped.
        assertEquals("08:00", nextIntervalFireTime(at(5, 30), 5, 0, 1, 20, windowStartHour = 8).hhmm())
    }

    // An early anchor is pulled forward to the window rather than firing outside it —
    // otherwise resetting the window leaves the pre-populated reminder firing at its old
    // hour, contradicting the window shown in Notifications.
    @Test
    fun nextInterval_anchorBeforeWindowStart_isPulledIntoTheWindow() {
        val next = nextIntervalFireTime(at(4, 0), 5, 0, 1, 20, windowStartHour = 8)
        assertEquals("08:00", next.hhmm())
        assertEquals(14, next.dayOfMonth())
    }

    // The roll-to-tomorrow path has to use the same pulled-forward hour, or the first
    // fire of the next day lands back outside the window.
    @Test
    fun nextInterval_anchorBeforeWindowStart_rollsToTomorrowInsideTheWindow() {
        val next = nextIntervalFireTime(at(21, 0), 5, 0, 1, 20, windowStartHour = 8)
        assertEquals("08:00", next.hhmm())
        assertEquals(15, next.dayOfMonth())
    }

    // A late anchor keeps firing: only repeats are bounded at the top, so a deliberately
    // late reminder isn't silently killed by a window it was never inside.
    @Test
    fun nextInterval_anchorAfterWindowEnd_stillFiresAtAnchor() {
        val next = nextIntervalFireTime(at(21, 0), 22, 0, 2, 20, windowStartHour = 8)
        assertEquals("22:00", next.hhmm())
        assertEquals(14, next.dayOfMonth())
    }

    // No in-window slot left in the day (the step overshoots midnight) — fire at the
    // anchor rather than not at all.
    @Test
    fun nextInterval_noSlotReachesWindow_fallsBackToAnchor() {
        val next = nextIntervalFireTime(at(4, 0), 5, 0, 20, 20, windowStartHour = 8)
        assertEquals("05:00", next.hhmm())
    }

    // The default reminder is anchored at the get-up hour, so it never skips anything.
    @Test
    fun nextInterval_anchoredAtWindowStart_isUnaffectedByTheLowerBound() {
        assertEquals("09:00", nextIntervalFireTime(at(8, 30), 8, 0, 1, 20, windowStartHour = 8).hhmm())
    }

    @Test
    fun nextInterval_noWindowStartGiven_boundsOnlyTheTopEnd() {
        assertEquals("06:00", nextIntervalFireTime(at(5, 30), 5, 0, 1, 20).hhmm())
    }

    @Test
    fun asHourLabel_defaults_parseBackAsReminder() {
        val r = Reminder.fromPrefsString(
            Reminder.defaultReminder(DEFAULT_REMINDER_START_HOUR).toPrefsString()
        )!!
        assertEquals(DEFAULT_REMINDER_START_HOUR, r.hour)
        assertEquals(1, r.intervalHours)
    }

    // ─── usableWindow ─────────────────────────────────────────────

    private val defaultWindow = DEFAULT_REMINDER_START_HOUR to DEFAULT_REMINDER_END_HOUR

    @Test
    fun usableWindow_keepsAWindowRemindersCanRunIn() {
        assertEquals(6 to 22, usableWindow(6, 22))
        assertEquals(0 to 23, usableWindow(0, 23))
    }

    // Callers schedule against the window, so an unusable one has to read as the default.
    @Test
    fun usableWindow_inverted_readsAsDefaults() {
        assertEquals(defaultWindow, usableWindow(21, 8))
    }

    @Test
    fun usableWindow_empty_readsAsDefaults() {
        assertEquals(defaultWindow, usableWindow(20, 20))
    }

    @Test
    fun usableWindow_outOfRangeHour_readsAsDefaults() {
        assertEquals(defaultWindow, usableWindow(-1, 20))
        assertEquals(defaultWindow, usableWindow(8, 24))
    }

    // ─── Quick amounts ──────────────────────────────

    @Test
    fun asQuickAmounts_sortsAndDeduplicates() {
        assertEquals(listOf(100, 250, 500), listOf(500, 250, 100, 250).asQuickAmounts())
    }

    @Test
    fun asQuickAmounts_dropsOutOfRangeValues() {
        assertEquals(
            listOf(1, CUSTOM_MAX_ML),
            listOf(0, -50, 1, CUSTOM_MAX_ML, CUSTOM_MAX_ML + 1).asQuickAmounts()
        )
    }

    @Test
    fun asQuickAmounts_capsAtLimit() {
        val tooMany = (1..QUICK_AMOUNTS_MAX + 5).map { it * 10 }
        assertEquals(QUICK_AMOUNTS_MAX, tooMany.asQuickAmounts().size)
        assertEquals(tooMany.take(QUICK_AMOUNTS_MAX), tooMany.asQuickAmounts())
    }

    @Test
    fun quickAmounts_encodeDecode_roundTrips() {
        assertEquals(
            DEFAULT_QUICK_AMOUNTS_ML,
            DEFAULT_QUICK_AMOUNTS_ML.encodeAmountCsv().decodeAmountCsv().asQuickAmounts()
        )
    }

    // Removing every button is a real state, and it has to survive a round trip as one.
    @Test
    fun quickAmounts_emptyList_roundTripsAsEmpty() {
        assertEquals(emptyList<Int>(), emptyList<Int>().encodeAmountCsv().decodeAmountCsv().asQuickAmounts())
    }

    // No pref yet: an install from before the amounts were editable keeps its custom value.
    @Test
    fun quickAmountsFrom_noPref_defaultsPlusLegacyCustom() {
        assertEquals(listOf(100, 180, 200), quickAmountsFrom(null, 180))
    }

    @Test
    fun quickAmountsFrom_noPref_noLegacyCustom_isDefaults() {
        assertEquals(DEFAULT_QUICK_AMOUNTS_ML, quickAmountsFrom(null, 0))
    }

    // An empty pref means the user removed them all, not that nothing is stored yet.
    @Test
    fun quickAmountsFrom_emptyPref_isEmptyAndIgnoresLegacyCustom() {
        assertEquals(emptyList<Int>(), quickAmountsFrom("", 180))
    }

    @Test
    fun quickAmountsFrom_storedPref_winsOverLegacyCustom() {
        assertEquals(listOf(150, 400), quickAmountsFrom("400,150", 180))
    }

    @Test
    fun decodeAmountCsv_skipsGarbage() {
        assertEquals(listOf(100, 330), " 100 , oops, , 330 ".decodeAmountCsv().asQuickAmounts())
    }

    // ─── Notification quick-log selection ─────────────────────────

    private val available = listOf(100, 200, 350, 500)

    @Test
    fun asNotificationAmounts_dropsAmountsNoLongerOnTheHomeRow() {
        assertEquals(listOf(100, 200), listOf(100, 200, 750).asNotificationAmounts(available))
    }

    @Test
    fun asNotificationAmounts_capsSortsAndDedups() {
        assertEquals(
            listOf(100, 200, 350),
            listOf(500, 200, 200, 100, 350).asNotificationAmounts(available)
        )
    }

    @Test
    fun asNotificationAmounts_emptyIsAllowed() {
        assertEquals(emptyList<Int>(), emptyList<Int>().asNotificationAmounts(available))
    }

    private fun prefsWith(quick: String, notifications: String? = null) = FakePrefs(
        buildMap {
            put(PREF_QUICK_AMOUNTS_ML, quick)
            notifications?.let { put(PREF_NOTIFICATION_AMOUNTS_ML, it) }
        }
    )

    @Test
    fun notificationAmounts_neverPicked_fallsBackToTheFirstThree() {
        assertEquals(listOf(100, 200, 350), prefsWith("100,200,350,500").notificationAmounts())
    }

    @Test
    fun notificationAmounts_emptyStored_meansEveryButtonTurnedOff() {
        assertEquals(emptyList<Int>(), prefsWith("100,200,350,500", "").notificationAmounts())
    }

    @Test
    fun notificationAmounts_deletedAmountIsFilteredOnRead() {
        // Picked, then removed from the home screen by an import or a restore that never went
        // through setQuickAmounts — so the filter has to happen on the way out, every time.
        assertEquals(listOf(100), prefsWith("100,200,350,500", "100,750").notificationAmounts())
    }

    /**
     * Editing a selected amount must not read back as "the user turned every button off".
     * Selecting only 250 and then changing that button to 260 used to store an empty CSV, which
     * is indistinguishable from a deliberate opt-out — silently killing the feature for good.
     */
    @Test
    fun setQuickAmounts_editingTheOnlySelectedAmount_doesNotTurnEveryButtonOff() {
        val prefs = prefsWith("100,200,250", "250")
        prefs.setQuickAmounts(listOf(100, 200, 260))

        assertFalse(prefs.contains(PREF_NOTIFICATION_AMOUNTS_ML))
        assertEquals(listOf(100, 200, 260), prefs.notificationAmounts())
    }

    @Test
    fun setQuickAmounts_deletingOneOfSeveralSelected_keepsTheRest() {
        val prefs = prefsWith("100,200,350", "200,350")
        prefs.setQuickAmounts(listOf(100, 200))
        assertEquals(listOf(200), prefs.notificationAmounts())
    }

    @Test
    fun setQuickAmounts_neverPicked_leavesTheSelectionDeriving() {
        val prefs = prefsWith("100,200,350")
        prefs.setQuickAmounts(listOf(100, 200))
        assertFalse(prefs.contains(PREF_NOTIFICATION_AMOUNTS_ML))
    }

    @Test
    fun setQuickAmounts_deliberateOptOutSurvivesAnEdit() {
        val prefs = prefsWith("100,200", "")
        prefs.setQuickAmounts(listOf(100, 200, 350))
        assertEquals(emptyList<Int>(), prefs.notificationAmounts())
    }

    @Test
    fun toggleNotificationAmount_unchecks() {
        assertEquals(listOf(100), listOf(100, 200).toggleNotificationAmount(200))
    }

    @Test
    fun toggleNotificationAmount_checksBelowTheCap() {
        assertEquals(listOf(100, 200), listOf(100).toggleNotificationAmount(200))
    }

    /** Front of the list is whichever has been checked longest; a fresh dialog seeds it sorted. */
    @Test
    fun toggleNotificationAmount_atTheCap_dropsTheOneCheckedLongest() {
        assertEquals(listOf(200, 350, 500), listOf(100, 200, 350).toggleNotificationAmount(500))
        // Order is insertion, not size: 500 was checked before 350, so 500 goes first.
        assertEquals(listOf(350, 200, 100), listOf(500, 350, 200).toggleNotificationAmount(100))
    }

    @Test
    fun toggleNotificationAmount_atTheCap_stillUnchecks() {
        assertEquals(listOf(100, 350), listOf(100, 200, 350).toggleNotificationAmount(200))
    }
}
