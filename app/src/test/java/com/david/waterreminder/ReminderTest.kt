package com.david.waterreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ReminderTest {

    // ─── fromPrefsString ──────────────────────────────────────────

    @Test
    fun fromPrefsString_daily_valid() {
        val r = Reminder.fromPrefsString("08:00|daily|true")!!
        assertEquals("08:00", r.time)
        assertEquals(8, r.hour)
        assertEquals(0, r.minute)
        assertEquals("daily", r.type)
        assertTrue(r.enabled)
        assertEquals(-1, r.endHour)
    }

    @Test
    fun fromPrefsString_disabled_reminder() {
        val r = Reminder.fromPrefsString("09:30|onetime|false")!!
        assertFalse(r.enabled)
    }

    @Test
    fun fromPrefsString_hourly_with_endHour() {
        val r = Reminder.fromPrefsString("08:00|hourly|true|20")!!
        assertEquals("hourly", r.type)
        assertEquals(8, r.hour)
        assertEquals(20, r.endHour)
        assertTrue(r.enabled)
    }

    @Test
    fun fromPrefsString_missingFields_returnsNull() {
        assertNull(Reminder.fromPrefsString("08:00|daily"))
    }

    @Test
    fun fromPrefsString_malformedTime_returnsNull() {
        assertNull(Reminder.fromPrefsString("0800|daily|true"))
    }

    @Test
    fun fromPrefsString_nonNumericHour_returnsNull() {
        assertNull(Reminder.fromPrefsString("xx:00|daily|true"))
    }

    @Test
    fun fromPrefsString_empty_returnsNull() {
        assertNull(Reminder.fromPrefsString(""))
    }

    // ─── toPrefsString ────────────────────────────────────────────

    @Test
    fun toPrefsString_daily_noEndHour() {
        val r = Reminder("08:00", 8, 0, "daily", true)
        assertEquals("08:00|daily|true", r.toPrefsString())
    }

    @Test
    fun toPrefsString_disabled() {
        val r = Reminder("09:30", 9, 30, "onetime", false)
        assertEquals("09:30|onetime|false", r.toPrefsString())
    }

    @Test
    fun toPrefsString_hourly_includesEndHour() {
        val r = Reminder("08:00", 8, 0, "hourly", true, endHour = 20)
        assertEquals("08:00|hourly|true|20", r.toPrefsString())
    }

    // ─── round-trip ───────────────────────────────────────────────

    @Test
    fun roundTrip_daily() {
        val original = Reminder("14:30", 14, 30, "daily", true)
        val restored = Reminder.fromPrefsString(original.toPrefsString())!!
        assertEquals(original, restored)
    }

    @Test
    fun roundTrip_hourly() {
        val original = Reminder("08:00", 8, 0, "hourly", true, endHour = 20)
        val restored = Reminder.fromPrefsString(original.toPrefsString())!!
        assertEquals(original, restored)
    }

    @Test
    fun roundTrip_disabled_onetime() {
        val original = Reminder("22:00", 22, 0, "onetime", false)
        val restored = Reminder.fromPrefsString(original.toPrefsString())!!
        assertEquals(original, restored)
    }
}
