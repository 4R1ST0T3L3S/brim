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
}
