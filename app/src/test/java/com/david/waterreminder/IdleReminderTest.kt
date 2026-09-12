package com.david.waterreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Calendar

/**
 * The idle reminder's two decisions: when its single alarm should next go off, and whether the
 * card belongs on screen once it does.
 *
 * The fire time gets the most attention because the alarm is rescheduled from half a dozen
 * places — a rule that moved the answer each time it was asked would let the user postpone the
 * reminder indefinitely just by walking past the Notifications screen.
 */
class IdleReminderTest {

    private val windowStart = 8
    private val windowEnd = 20

    /** A fixed mid-June day, well clear of any daylight-saving transition. */
    private fun at(hour: Int, minute: Int = 0, dayOffset: Int = 0): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JUNE, 10 + dayOffset, hour, minute, 0)
        }

    private fun nextFire(
        now: Calendar,
        lastIntake: Calendar?,
        gapMinutes: Int,
        start: Int = windowStart,
        end: Int = windowEnd,
    ): Calendar? = nextIdleFireTime(
        now = now,
        lastIntakeMillis = lastIntake?.timeInMillis ?: 0L,
        idleMinutes = gapMinutes,
        windowStartHour = start,
        windowEndHour = end,
    )

    private fun assertFiresAt(expected: Calendar, actual: Calendar?) =
        assertEquals(expected.time, actual?.time)

    // ─── nextIdleFireTime ─────────────────────────────────────────

    @Test
    fun off_hasNothingToSchedule() {
        assertNull(nextFire(at(10), at(9, 30), gapMinutes = 0))
    }

    @Test
    fun firesOneGapAfterTheLastDrink() {
        assertFiresAt(at(10, 30), nextFire(at(10), at(9, 30), gapMinutes = 60))
    }

    /**
     * The gap has already run out — an alarm set for the past would be delivered instantly, and
     * every reschedule would do it again.
     */
    @Test
    fun anOverdueGapIsCarriedForward_neverScheduledIntoThePast() {
        assertFiresAt(at(13), nextFire(at(12), at(9), gapMinutes = 60))
    }

    /**
     * The load-bearing property: every call inside one gap has to give the same answer, because
     * MainActivity, the Notifications screen, BootReceiver and every reminder firing all
     * reschedule this alarm.
     */
    @Test
    fun everyCallInsideOneGapAgrees() {
        val lastDrink = at(9)
        listOf(at(10, 1), at(10, 30), at(10, 59)).forEach { now ->
            assertFiresAt(at(11), nextFire(now, lastDrink, gapMinutes = 60))
        }
    }

    /** A drink pushes the countdown out — that is the whole point of it. */
    @Test
    fun aDrinkRestartsTheCountdown() {
        assertFiresAt(at(12, 15), nextFire(at(10, 20), at(10, 15), gapMinutes = 120))
    }

    /**
     * Nobody drinks in their sleep, so the countdown restarts with the day rather than running
     * from last night — otherwise the first thing the app did each morning was scold.
     */
    @Test
    fun theCountdownRestartsAtTheGetUpHour() {
        assertFiresAt(at(9), nextFire(at(8, 30), at(21, dayOffset = -1), gapMinutes = 60))
    }

    @Test
    fun beforeTheWindowOpens_theGapIsMeasuredFromTheGetUpHour() {
        assertFiresAt(at(9), nextFire(at(6), at(21, dayOffset = -1), gapMinutes = 60))
    }

    /** The window end is an hour, not an instant — 20:30 is still inside a 20:00 bedtime. */
    @Test
    fun aFireTimeInsideTheBedtimeHourStands() {
        assertFiresAt(at(20, 30), nextFire(at(19, 45), at(19, 30), gapMinutes = 60))
    }

    @Test
    fun pastBedtime_waitsForTheNextGetUpHour() {
        assertFiresAt(at(8, dayOffset = 1), nextFire(at(20, 40), at(20, 30), gapMinutes = 60))
    }

    @Test
    fun aFireTimeThatRollsPastMidnight_waitsForTheNextGetUpHour() {
        assertFiresAt(at(8, dayOffset = 1), nextFire(at(22), at(21, 30), gapMinutes = 240))
    }

    /**
     * A clock set back leaves a last-drink stamp in the future. Trusting it would park the
     * reminder until the clock caught up, so it reads as "never" and the day's start takes over.
     */
    @Test
    fun aLastDrinkInTheFuture_isIgnored() {
        val prefs = FakePrefs(mapOf(PREF_LAST_INTAKE_AT to at(18).timeInMillis))
        assertEquals(0L, prefs.lastIntakeAt(nowMillis = at(10).timeInMillis))
    }

    // ─── how long it has been ─────────────────────────────────────

    private fun elapsed(now: Calendar, lastIntake: Calendar?) =
        idleElapsedMinutes(now.timeInMillis, lastIntake?.timeInMillis ?: 0L)

    @Test
    fun elapsed_readsInWholeFiveMinuteSteps() {
        assertEquals(120, elapsed(at(12), at(10)))
        assertEquals(45, elapsed(at(12), at(11, 15)))
    }

    /** Rounded down, so a delivery running late never reports more than has really passed. */
    @Test
    fun elapsed_roundsDownRatherThanReportingAnOddNumber() {
        assertEquals(60, elapsed(at(12, 3), at(11)))
        assertEquals(90, elapsed(at(12, 4), at(10, 30)))
    }

    /** Nothing to measure from: the card falls back to its vaguer wording. */
    @Test
    fun elapsed_isZeroWithoutALastDrink() {
        assertEquals(0, elapsed(at(12), null))
        assertEquals(0, elapsed(at(12), at(18)))
    }

    // ─── shouldShowIdleReminder ───────────────────────────────────

    private fun prefs(vararg entries: Pair<String, Any>) = FakePrefs(
        mapOf(
            "notifications_enabled" to true,
            PREF_IDLE_REMINDER_MINUTES to 60,
            "date" to LocalDate.now().toString(),
            PREF_LAST_INTAKE_AT to at(9).timeInMillis,
            *entries,
        )
    )

    private fun FakePrefs.shows(now: Calendar = at(12), lastReminderPost: Calendar? = null) =
        shouldShowIdleReminder(
            this, notificationProgress(), now, lastReminderPost?.timeInMillis ?: 0L
        )


    @Test
    fun shown_whenTheGapHasRunOutAndNothingObjects() {
        assertTrue(prefs().shows())
    }

    @Test
    fun hidden_whenNotificationsAreOff() {
        assertFalse(prefs("notifications_enabled" to false).shows())
    }

    @Test
    fun hidden_whenTheFeatureIsOff() {
        assertFalse(prefs(PREF_IDLE_REMINDER_MINUTES to 0).shows())
    }

    /** A gap no picker can produce reads as off rather than as a reminder years out. */
    @Test
    fun hidden_whenTheStoredGapIsOutOfRange() {
        assertFalse(prefs(PREF_IDLE_REMINDER_MINUTES to IDLE_REMINDER_MAX_MINUTES + 1).shows())
    }

    /** Android can deliver an alarm hours late, so the window is checked again on arrival. */
    @Test
    fun hidden_whenDeliveredOutsideTheWindow() {
        assertFalse(prefs().shows(now = at(22)))
    }

    /** The drink landed between the alarm being set and it being delivered. */
    @Test
    fun hidden_whenADrinkAnsweredItFirst() {
        assertFalse(prefs(PREF_LAST_INTAKE_AT to at(11, 30).timeInMillis).shows())
    }

    @Test
    fun shown_whenTheGapHasElapsedExactly() {
        assertTrue(prefs(PREF_LAST_INTAKE_AT to at(11).timeInMillis).shows())
    }

// ─── the reminder the user configured wins ────────────────────

    /** A reminder card just said the same thing; two of them is a fault, not a nudge. */
    @Test
    fun hidden_rightAfterAReminderCard() {
        assertFalse(prefs().shows(lastReminderPost = at(11, 59)))
    }

    @Test
    fun shown_onceThatReminderCardIsOldNews() {
        assertTrue(prefs().shows(lastReminderPost = at(11, 50)))
    }

    /**
     * The regression this test exists for: suppressing on the reminder *schedule* rather than on
     * a card actually posted.
     *
     * An every-hour reminder anchored at the get-up hour is due at 12:00, and a 1-hour gap whose
     * countdown floored to that same hour lands there too — so on a day with nothing logged the
     * two collide on every single fire. If the reminder's alarm chain has been dropped it never
     * posts, and the nudge that exists to cover exactly that must still arrive. Only a real post
     * silences it.
     */
    @Test
    fun shown_whenAReminderWasDueButNeverPosted() {
        val everyHourFromGetUp = Reminder(
            time = "08:00", hour = 8, minute = 0,
            type = Reminder.TYPE_DAILY, enabled = true, intervalHours = 1,
        )
        val prefs = prefs(REMINDERS_KEY to mutableSetOf(everyHourFromGetUp.toPrefsString()))
        assertTrue(prefs.shows())
    }

    /** A post stamped in the future — the clock was set back — is not a reason to go quiet. */
    @Test
    fun aReminderPostInTheFuture_isIgnored() {
        val prefs = FakePrefs(mapOf(PREF_LAST_REMINDER_POST_AT to at(18).timeInMillis))
        assertEquals(0L, prefs.lastReminderPostAt(nowMillis = at(12).timeInMillis))
    }

    // ─── rebuilding a card that is already up ─────────────────────

    private fun FakePrefs.allows(now: Calendar = at(12)) =
        idleCardAllowed(this, notificationProgress(), now)

    /** The gap is not asked, so a drink a moment ago still leaves the card welcome. */
    @Test
    fun rebuild_isAllowedEvenThoughTheGapWasJustAnswered() {
        assertTrue(prefs(PREF_LAST_INTAKE_AT to at(11, 59).timeInMillis).allows())
    }

    /** But a card tapped long past bedtime goes, exactly as a reminder card would. */
    @Test
    fun rebuild_isRefusedOutsideTheWindow() {
        assertFalse(prefs().allows(now = at(23, 30)))
    }

    /** An import can switch the feature off while a card is still in the shade. */
    @Test
    fun rebuild_isRefusedOnceTheFeatureIsOff() {
        assertFalse(prefs(PREF_IDLE_REMINDER_MINUTES to 0).allows())
    }

    @Test
    fun hidden_atTheGoal_forSomeoneWhoAskedRemindersToStopThere() {
        assertFalse(
            prefs(
                "intake_ml" to DEFAULT_DAILY_GOAL_ML,
                PREF_STOP_NOTIFICATIONS_ON_GOAL to true,
            ).shows()
        )
    }

    @Test
    fun shown_atTheGoal_byDefault() {
        assertTrue(prefs("intake_ml" to DEFAULT_DAILY_GOAL_ML).shows())
    }

    // ─── the picker's own bookkeeping ─────────────────────────────

    @Test
    fun anImportedGapThatIsNotOnTheList_selectsOff() {
        assertEquals(0, idleReminderChoiceIndex(45))
    }

    @Test
    fun aStoredGapPicksItsOwnRow() {
        assertEquals(
            IDLE_REMINDER_CHOICES_MINUTES.indexOf(120),
            idleReminderChoiceIndex(120)
        )
    }
}
