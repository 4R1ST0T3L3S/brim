package com.david.waterreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
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

    @Test
    fun asHourLabel_defaults_parseBackAsReminder() {
        val r = Reminder.fromPrefsString(
            "${DEFAULT_REMINDER_START_HOUR.asHourLabel()}|hourly|true|$DEFAULT_REMINDER_END_HOUR"
        )!!
        assertEquals(DEFAULT_REMINDER_START_HOUR, r.hour)
        assertEquals(DEFAULT_REMINDER_END_HOUR, r.endHour)
    }
}
