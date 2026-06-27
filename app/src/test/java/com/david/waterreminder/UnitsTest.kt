package com.david.waterreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
