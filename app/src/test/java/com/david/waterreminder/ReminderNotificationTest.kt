package com.david.waterreminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The gate that decides whether a reminder card belongs on screen. Worth pinning down because
 * both the alarm and a quick-log tap run through it, and every branch is a way to resurrect a
 * notification the user thought they had silenced.
 */
class ReminderNotificationTest {

    private val noon = 12

    private fun reminder(enabled: Boolean = true, intervalHours: Int = 0, hour: Int = 12) =
        Reminder(
            time = hour.asHourLabel(),
            hour = hour,
            minute = 0,
            type = Reminder.TYPE_DAILY,
            enabled = enabled,
            intervalHours = intervalHours,
        )

    private fun prefs(vararg entries: Pair<String, Any>) = FakePrefs(
        mapOf(
            "notifications_enabled" to true,
            "date" to LocalDate.now().toString(),
            *entries,
        )
    )

    private fun FakePrefs.shows(reminder: Reminder, hour: Int = noon) =
        shouldShowReminder(this, reminder, notificationProgress(), hour)

    @Test
    fun shown_whenNotificationsAreOnAndNothingObjects() {
        assertTrue(prefs().shows(reminder()))
    }

    @Test
    fun hidden_whenNotificationsAreOff() {
        assertFalse(prefs("notifications_enabled" to false).shows(reminder()))
    }

    /**
     * The reminder was switched off individually while its card sat in the shade. Its alarm is
     * cancelled on the way out, but the card keeps its buttons — tapping one must not bring it
     * back.
     */
    @Test
    fun hidden_whenThatReminderIsSwitchedOff() {
        assertFalse(prefs().shows(reminder(enabled = false)))
    }

    @Test
    fun hidden_whenARepeatingReminderIsDeliveredOutsideTheWindow() {
        val everyTwoHours = reminder(intervalHours = 2, hour = 10)
        assertTrue(prefs().shows(everyTwoHours, hour = 10))
        assertFalse(prefs().shows(everyTwoHours, hour = 23))
    }

    // ─── The goal, through the inverted legacy key ────────────────

    private fun atGoal(vararg entries: Pair<String, Any>) =
        prefs("intake_ml" to DEFAULT_DAILY_GOAL_ML, *entries)

    @Test
    fun shown_atTheGoal_byDefault() {
        // "Display when goal reached" is on out of the box, stored as stop_… = false.
        assertTrue(atGoal().shows(reminder()))
    }

    @Test
    fun hidden_atTheGoal_whenTheUserAskedThemToStop() {
        assertFalse(atGoal("stop_notifications_on_goal" to true).shows(reminder()))
    }

    @Test
    fun shown_belowTheGoal_evenWhenTheUserAskedThemToStop() {
        assertTrue(
            prefs("intake_ml" to 500, "stop_notifications_on_goal" to true).shows(reminder())
        )
    }

    @Test
    fun goalIsThePersonalOneWhenSet() {
        val prefs = prefs(
            "intake_ml" to DEFAULT_DAILY_GOAL_ML,
            "personal_goal_ml" to 3_000,
            "stop_notifications_on_goal" to true,
        )
        // 1500 clears the app minimum but not the personal goal, so reminders continue.
        assertTrue(prefs.shows(reminder()))
    }

    /** Yesterday's total must never be reported as today's on an app nobody has opened. */
    @Test
    fun progress_readsZeroOnceTheStoredDayIsStale() {
        val stale = prefs(
            "date" to LocalDate.now().minusDays(1).toString(),
            "intake_ml" to DEFAULT_DAILY_GOAL_ML,
        )
        assertFalse(stale.notificationProgress().goalReached)
    }
}
