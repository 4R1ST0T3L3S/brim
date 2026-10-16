package com.david.waterreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ─── What the card reports ──────────────────────────

    @Test
    fun percentAndRemaining_describeTheWayToTheGoal() {
        val progress = NotificationProgress(intakeMl = 1_000, goalMl = 1_600)
        assertEquals(62, progress.percent)
        assertEquals(600, progress.remainingMl)
    }

    /**
     * Every number on the card converts, not just the quick-log buttons: someone on ounces must
     * not read "33.8 / 1600 ml".
     */
    @Test
    fun theCardsNumbersFollowTheChosenUnit() {
        val progress = NotificationProgress(intakeMl = 1_000, goalMl = 1_600)
        assertEquals("33.8", progress.intakeMl.toDisplay(UNIT_OZ))
        assertEquals("54.1 oz", progress.goalMl.fmt(UNIT_OZ))
        assertEquals("20.3 oz", progress.remainingMl.fmt(UNIT_OZ))
    }

    /**
     * Over the goal the card says 100% and nothing left to drink — never 112% or a negative
     * amount to go. Only reachable with "Display when goal reached" on.
     */
    @Test
    fun percentAndRemaining_dontOvershootPastTheGoal() {
        val progress = NotificationProgress(intakeMl = 1_800, goalMl = 1_600)
        assertEquals(100, progress.percent)
        assertEquals(0, progress.remainingMl)
    }

    @Test
    fun activeKeys_mapsShowingCardsBackToTheirReminders() {
        val keys = activeReminderKeys(
            listOf(reminderNotificationId("08:00"), reminderNotificationId("12:00")),
            listOf("08:00", "10:00", "12:00"),
        )
        assertEquals(listOf("08:00", "12:00"), keys)
    }

    @Test
    fun activeKeys_neverIncludesADismissedCard() {
        assertEquals(emptyList<String>(), activeReminderKeys(emptyList(), listOf("08:00", "10:00")))
    }

    @Test
    fun activeKeys_includesTheIdleCardWhenItIsUp() {
        assertEquals(
            listOf("08:00", IDLE_REMINDER_KEY),
            activeReminderKeys(
                listOf(reminderNotificationId(IDLE_REMINDER_KEY), reminderNotificationId("08:00")),
                listOf("08:00"),
            ),
        )
    }

    @Test
    fun activeKeys_findsTheIdleCardWithNoRemindersAtAll() {
        assertEquals(
            listOf(IDLE_REMINDER_KEY),
            activeReminderKeys(listOf(reminderNotificationId(IDLE_REMINDER_KEY)), emptyList()),
        )
    }

    @Test
    fun activeKeys_ignoresNotificationsThatAreNotReminderCards() {
        assertEquals(emptyList<String>(), activeReminderKeys(listOf(42, -7), listOf("08:00")))
    }

    @Test
    fun activeKeys_reportsADuplicatedTimeOnce() {
        assertEquals(
            listOf("08:00"),
            activeReminderKeys(listOf(reminderNotificationId("08:00")), listOf("08:00", "08:00")),
        )
    }

    @Test
    fun plan_aDrinkTakesTheIdleCardDown() {
        val plan = planCardSync(listOf("08:00", IDLE_REMINDER_KEY), drink = true)
        assertEquals(CardSync.TAKE_DOWN, plan[IDLE_REMINDER_KEY])
    }

    @Test
    fun plan_aTapOnAReminderCardRebuildsItAndTakesTheIdleCardDown() {
        // QuickLogReceiver's own path: onIntakeLogged has just cancelled the idle card, but the
        // cancel is async and the snapshot can still list it — it must not be rebuilt.
        val plan = planCardSync(listOf("08:00", IDLE_REMINDER_KEY), drink = true, tappedKey = "08:00")
        assertEquals(mapOf("08:00" to CardSync.REBUILD, IDLE_REMINDER_KEY to CardSync.TAKE_DOWN), plan)
    }

    @Test
    fun plan_aTakeBackKeepsTheIdleWording() {
        val plan = planCardSync(listOf(IDLE_REMINDER_KEY), drink = false)
        assertEquals(CardSync.REBUILD_KEEP_IDLE_WORDING, plan[IDLE_REMINDER_KEY])
    }

    @Test
    fun plan_aTappedIdleCardIsRebuiltPlain() {
        val plan = planCardSync(listOf(IDLE_REMINDER_KEY), drink = true, tappedKey = IDLE_REMINDER_KEY)
        assertEquals(CardSync.REBUILD, plan[IDLE_REMINDER_KEY])
    }

    @Test
    fun plan_aTappedReminderCardIsRebuiltEvenIfNotActive() {
        val plan = planCardSync(emptyList(), drink = true, tappedKey = "08:00")
        assertEquals(mapOf("08:00" to CardSync.REBUILD), plan)
    }

    @Test
    fun plan_scheduledCardsAreAlwaysRebuilt() {
        listOf(true, false).forEach { drink ->
            val plan = planCardSync(listOf("08:00", "12:00"), drink)
            assertEquals(mapOf("08:00" to CardSync.REBUILD, "12:00" to CardSync.REBUILD), plan)
        }
    }

    @Test
    fun plan_hasNoDuplicatesWhenTheTappedKeyIsAlsoActive() {
        val plan = planCardSync(listOf("08:00", "12:00"), drink = true, tappedKey = "08:00")
        assertEquals(listOf("08:00", "12:00"), plan.keys.toList())
    }

    @Test
    fun pending_isNullWhenNothingWasAdded() {
        assertNull(PendingCardSync().take())
    }

    @Test
    fun pending_aDrinkReportsTrue() {
        val pending = PendingCardSync().apply { add(true) }
        assertEquals(true, pending.take())
    }

    @Test
    fun pending_aTakeBackAloneReportsFalse() {
        val pending = PendingCardSync().apply { add(false) }
        assertEquals(false, pending.take())
    }

    @Test
    fun pending_aDrinkFollowedByATakeBackStillReportsTrue() {
        val pending = PendingCardSync().apply { add(true); add(false) }
        assertEquals(true, pending.take())
    }

    @Test
    fun pending_takeClears() {
        val pending = PendingCardSync().apply { add(true) }
        pending.take()
        assertNull(pending.take())
        pending.add(false)
        assertEquals(false, pending.take())
    }
}
