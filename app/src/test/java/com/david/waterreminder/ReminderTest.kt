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
        assertFalse(repeating(3).isHourly)
    }

    // A repeating user reminder must not be mistaken for the built-in hourly one, or the
    // window dialog would start editing the user's own reminder.
    @Test
    fun repeating_isNotPickedUpAsTheBuiltInHourly() {
        val saved = listOf(repeating(1))
        assertEquals(DEFAULT_REMINDER_START_HOUR, Reminder.hourlyOrDefault(saved).hour)
        assertEquals(2, Reminder.withHourlyWindow(saved, 7, 22).size)
    }

    @Test
    fun withHourlyWindow_keepsRepeatingReminders() {
        val saved = listOf(repeating(2), Reminder.defaultHourly())
        assertTrue(Reminder.withHourlyWindow(saved, 6, 22).contains(repeating(2)))
    }

    // Entries written before repeats existed must keep parsing.
    @Test
    fun legacyEntries_haveNoInterval() {
        assertEquals(0, Reminder.fromPrefsString("08:00|daily|true")!!.intervalHours)
        assertEquals(0, Reminder.fromPrefsString("08:00|hourly|true|20")!!.intervalHours)
    }

    @Test
    fun nonRepeating_prefsStringStaysThreeOrFourFields() {
        assertEquals("09:30|daily|true", repeating(0).toPrefsString())
        assertEquals("08:00|hourly|true|20", Reminder.defaultHourly().toPrefsString())
    }

    @Test
    fun repeating_malformedIntervalField_readsAsZero() {
        assertEquals(0, Reminder.fromPrefsString("09:30|daily|true|-1|x")!!.intervalHours)
    }

    // ─── hourlyOrDefault ──────────────────────────────────────────

    @Test
    fun hourlyOrDefault_empty_returnsDefaultWindow() {
        val r = Reminder.hourlyOrDefault(emptyList())
        assertEquals(DEFAULT_REMINDER_START_HOUR, r.hour)
        assertEquals(DEFAULT_REMINDER_END_HOUR, r.endHour)
        assertTrue(r.enabled)
    }

    @Test
    fun hourlyOrDefault_noHourlyEntry_returnsDefaultWindow() {
        val saved = listOf(Reminder("09:30", 9, 30, "daily", true))
        assertEquals(DEFAULT_REMINDER_START_HOUR, Reminder.hourlyOrDefault(saved).hour)
    }

    @Test
    fun hourlyOrDefault_returnsStoredWindow() {
        val saved = listOf(Reminder("06:00", 6, 0, "hourly", true, endHour = 22))
        val r = Reminder.hourlyOrDefault(saved)
        assertEquals(6, r.hour)
        assertEquals(22, r.endHour)
    }

    @Test
    fun hourlyOrDefault_missingEndHour_fallsBackToDefaultEnd() {
        val saved = listOf(Reminder("06:00", 6, 0, "hourly", true))
        val r = Reminder.hourlyOrDefault(saved)
        assertEquals(6, r.hour)
        assertEquals(DEFAULT_REMINDER_END_HOUR, r.endHour)
    }

    @Test
    fun hourlyOrDefault_keepsDisabledState() {
        val saved = listOf(Reminder("06:00", 6, 0, "hourly", false, endHour = 22))
        assertFalse(Reminder.hourlyOrDefault(saved).enabled)
    }

    // The window dialog only offers hours that keep start < end, so a stored window
    // that breaks it (hand-edited or imported data) has to read as something usable.
    @Test
    fun hourlyOrDefault_invertedWindow_fallsBackToDefaultWindow() {
        val saved = listOf(Reminder("21:00", 21, 0, "hourly", true, endHour = 8))
        val r = Reminder.hourlyOrDefault(saved)
        assertEquals(DEFAULT_REMINDER_START_HOUR, r.hour)
        assertEquals("08:00", r.time)
        assertEquals(DEFAULT_REMINDER_END_HOUR, r.endHour)
    }

    @Test
    fun hourlyOrDefault_emptyWindow_fallsBackToDefaultWindow() {
        val saved = listOf(Reminder("20:00", 20, 0, "hourly", true, endHour = 20))
        assertEquals(DEFAULT_REMINDER_START_HOUR, Reminder.hourlyOrDefault(saved).hour)
    }

    @Test
    fun hourlyOrDefault_missingEndHour_pastDefaultEnd_fallsBackToDefaultWindow() {
        val saved = listOf(Reminder("22:00", 22, 0, "hourly", true))
        val r = Reminder.hourlyOrDefault(saved)
        assertEquals(DEFAULT_REMINDER_START_HOUR, r.hour)
        assertEquals(DEFAULT_REMINDER_END_HOUR, r.endHour)
    }

    @Test
    fun hourlyOrDefault_invertedWindow_stillKeepsDisabledState() {
        val saved = listOf(Reminder("21:00", 21, 0, "hourly", false, endHour = 8))
        assertFalse(Reminder.hourlyOrDefault(saved).enabled)
    }

    @Test
    fun hourlyOrDefault_alwaysReturnsUsableWindow() {
        assertTrue(Reminder.hourlyOrDefault(emptyList()).let { it.endHour > it.hour })
    }

    // ─── withHourlyWindow ─────────────────────────────────────────

    @Test
    fun withHourlyWindow_replacesWindowAndTimeLabel() {
        val saved = listOf(Reminder("08:00", 8, 0, "hourly", true, endHour = 20))
        val hourly = Reminder.withHourlyWindow(saved, 7, 23).single()
        assertEquals("07:00", hourly.time)
        assertEquals(7, hourly.hour)
        assertEquals(0, hourly.minute)
        assertEquals(23, hourly.endHour)
        assertEquals("hourly", hourly.type)
    }

    @Test
    fun withHourlyWindow_keepsCustomRemindersUntouched() {
        val custom = listOf(
            Reminder("09:30", 9, 30, "daily", true),
            Reminder("22:00", 22, 0, "onetime", false)
        )
        val saved = custom + Reminder("08:00", 8, 0, "hourly", true, endHour = 20)

        val result = Reminder.withHourlyWindow(saved, 6, 22)

        assertEquals(3, result.size)
        assertTrue(result.containsAll(custom))
        assertEquals(1, result.count { it.type == "hourly" })
    }

    @Test
    fun withHourlyWindow_preservesEnabledState() {
        val saved = listOf(Reminder("08:00", 8, 0, "hourly", false, endHour = 20))
        assertFalse(Reminder.withHourlyWindow(saved, 6, 22).single().enabled)
    }

    @Test
    fun withHourlyWindow_noExistingHourly_addsEnabledOne() {
        val saved = listOf(Reminder("09:30", 9, 30, "daily", true))
        val result = Reminder.withHourlyWindow(saved, 6, 22)
        assertEquals(2, result.size)
        assertTrue(result.single { it.type == "hourly" }.enabled)
    }

    @Test
    fun withHourlyWindow_collapsesDuplicateHourlyEntries() {
        val saved = listOf(
            Reminder("08:00", 8, 0, "hourly", true, endHour = 20),
            Reminder("09:00", 9, 0, "hourly", true, endHour = 21)
        )
        assertEquals(1, Reminder.withHourlyWindow(saved, 6, 22).size)
    }

    @Test
    fun withHourlyWindow_roundTripsThroughPrefsStrings() {
        val saved = listOf(
            Reminder("09:30", 9, 30, "daily", true),
            Reminder("08:00", 8, 0, "hourly", true, endHour = 20)
        )
        val stored = Reminder.withHourlyWindow(saved, 7, 23).map { it.toPrefsString() }.toSet()
        val hourly = Reminder.hourlyOrDefault(Reminder.parseAll(stored))
        assertEquals(7, hourly.hour)
        assertEquals(23, hourly.endHour)
        assertEquals(2, Reminder.parseAll(stored).size)
    }

    // ─── parseAll ─────────────────────────────────────────────────

    @Test
    fun parseAll_null_returnsEmpty() {
        assertTrue(Reminder.parseAll(null).isEmpty())
    }

    @Test
    fun parseAll_skipsMalformedEntries() {
        val parsed = Reminder.parseAll(setOf("08:00|hourly|true|20", "garbage", "0800|daily|true"))
        assertEquals(1, parsed.size)
        assertEquals("hourly", parsed.single().type)
    }
}
