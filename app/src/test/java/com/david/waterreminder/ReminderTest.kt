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
        assertEquals(0, r.intervalHours)
    }

    @Test
    fun fromPrefsString_disabled_reminder() {
        val r = Reminder.fromPrefsString("09:30|onetime|false")!!
        assertFalse(r.enabled)
    }

    // An entry from before the hourly reminder became an ordinary row still has to
    // behave as what it always was: every hour, starting at its own time.
    @Test
    fun fromPrefsString_legacyHourly_readsAsEveryOneHour() {
        val r = Reminder.fromPrefsString("08:00|hourly|true|20")!!
        assertEquals(Reminder.TYPE_DAILY, r.type)
        assertEquals(8, r.hour)
        assertEquals(1, r.intervalHours)
        assertTrue(r.isInterval)
        assertTrue(r.enabled)
    }

    @Test
    fun fromPrefsString_legacyHourly_keepsDisabledState() {
        assertFalse(Reminder.fromPrefsString("08:00|hourly|false|20")!!.enabled)
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

    // ─── round-trip ───────────────────────────────────────────────

    @Test
    fun roundTrip_daily() {
        val original = Reminder("14:30", 14, 30, "daily", true)
        val restored = Reminder.fromPrefsString(original.toPrefsString())!!
        assertEquals(original, restored)
    }

    @Test
    fun roundTrip_defaultReminder() {
        val original = Reminder.defaultReminder(DEFAULT_REMINDER_START_HOUR)
        assertEquals(original, Reminder.fromPrefsString(original.toPrefsString()))
    }

    @Test
    fun roundTrip_disabled_onetime() {
        val original = Reminder("22:00", 22, 0, "onetime", false)
        val restored = Reminder.fromPrefsString(original.toPrefsString())!!
        assertEquals(original, restored)
    }

    // ─── repeating reminders ──────────────────────────────────────

    private fun repeating(hours: Int) =
        Reminder("09:30", 9, 30, Reminder.TYPE_DAILY, true, intervalHours = hours)

    @Test
    fun repeating_roundTripsThroughPrefsString() {
        val original = repeating(2)
        assertEquals("09:30|daily|true|-1|2", original.toPrefsString())
        assertEquals(original, Reminder.fromPrefsString(original.toPrefsString()))
    }

    @Test
    fun repeating_isIntervalOnlyWhenHoursSet() {
        assertTrue(repeating(3).isInterval)
        assertFalse(repeating(0).isInterval)
    }

    // Entries written before repeats existed must keep parsing.
    @Test
    fun legacyEntries_haveNoInterval() {
        assertEquals(0, Reminder.fromPrefsString("08:00|daily|true")!!.intervalHours)
    }

    @Test
    fun nonRepeating_prefsStringStaysThreeFields() {
        assertEquals("09:30|daily|true", repeating(0).toPrefsString())
    }

    @Test
    fun repeating_malformedIntervalField_readsAsZero() {
        assertEquals(0, Reminder.fromPrefsString("09:30|daily|true|-1|x")!!.intervalHours)
    }

    // ─── defaultReminder ──────────────────────────────────────────

    @Test
    fun defaultReminder_isAnOrdinaryEveryOneHourReminder() {
        val r = Reminder.defaultReminder(DEFAULT_REMINDER_START_HOUR)
        assertEquals(DEFAULT_REMINDER_START_HOUR, r.hour)
        assertEquals("08:00", r.time)
        assertEquals(0, r.minute)
        assertEquals(Reminder.TYPE_DAILY, r.type)
        assertEquals(1, r.intervalHours)
        assertTrue(r.enabled)
    }

    // Nothing marks it as special, so it can be edited and deleted like any other.
    @Test
    fun defaultReminder_isIndistinguishableFromAHandMadeOne() {
        assertEquals(
            Reminder("07:00", 7, 0, Reminder.TYPE_DAILY, true, intervalHours = 1),
            Reminder.defaultReminder(7)
        )
    }

    @Test
    fun defaultReminder_followsTheGetUpHour() {
        assertEquals("06:00", Reminder.defaultReminder(6).time)
    }

    // ─── legacyHourlyWindow ───────────────────────────────────────

    @Test
    fun legacyHourlyWindow_readsTheWindowOffTheHourlyEntry() {
        val saved = setOf("06:00|hourly|true|22", "09:30|daily|true")
        assertEquals(6 to 22, Reminder.legacyHourlyWindow(saved))
    }

    @Test
    fun legacyHourlyWindow_noneStored_isNull() {
        assertNull(Reminder.legacyHourlyWindow(null))
        assertNull(Reminder.legacyHourlyWindow(emptySet()))
        assertNull(Reminder.legacyHourlyWindow(setOf("09:30|daily|true|-1|2")))
    }

    @Test
    fun legacyHourlyWindow_missingOrMalformedEndHour_isNull() {
        assertNull(Reminder.legacyHourlyWindow(setOf("06:00|hourly|true")))
        assertNull(Reminder.legacyHourlyWindow(setOf("06:00|hourly|true|x")))
    }

    // Migration writes it through as-is; usableWindow is what makes it safe on read.
    @Test
    fun legacyHourlyWindow_invertedWindow_readsAsDefaults() {
        val (start, end) = Reminder.legacyHourlyWindow(setOf("21:00|hourly|true|8"))!!
        assertEquals(
            DEFAULT_REMINDER_START_HOUR to DEFAULT_REMINDER_END_HOUR,
            usableWindow(start, end)
        )
    }

    // The entry is left alone by the migration, so it has to keep reading correctly.
    @Test
    fun legacyEntry_survivesMigrationUnrewritten() {
        val r = Reminder.parseAll(setOf("06:00|hourly|false|22")).single()
        assertEquals("06:00", r.time)   // the alarm key, which must not change
        assertEquals(1, r.intervalHours)
        assertFalse(r.enabled)
    }

    // ─── parseAll ─────────────────────────────────────────────────

    @Test
    fun parseAll_null_returnsEmpty() {
        assertTrue(Reminder.parseAll(null).isEmpty())
    }

    @Test
    fun parseAll_skipsMalformedEntries() {
        val parsed = Reminder.parseAll(setOf("08:00|daily|true", "garbage", "0800|daily|true"))
        assertEquals(1, parsed.size)
        assertEquals("08:00", parsed.single().time)
    }
}
