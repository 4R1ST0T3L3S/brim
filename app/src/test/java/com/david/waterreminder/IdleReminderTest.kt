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
     * Nobody drinks in their sleep, so a night is not a gap between drinks. The clock stops
     * entirely until the day's first glass rather than counting from last night's.
     */
    @Test
    fun lastNightsDrinkStartsNoCountdown() {
        assertNull(nextFire(at(8, 30), at(21, dayOffset = -1), gapMinutes = 60))
        assertNull(nextFire(at(6), at(21, dayOffset = -1), gapMinutes = 60))
    }

    @Test
    fun nothingLoggedAtAll_hasNothingToSchedule() {
        assertNull(nextFire(at(12), null, gapMinutes = 60))
    }

    /** The day's first drink is what starts it. */
    @Test
    fun theDaysFirstDrinkStartsTheCountdown() {
        assertFiresAt(at(10, 30), nextFire(at(9, 40), at(9, 30), gapMinutes = 60))
    }

    /** A drink before the window opened counts; the nudge for it waits for the get-up hour. */
    @Test
    fun aDrinkBeforeTheWindowOpens_waitsForTheGetUpHour() {
        assertFiresAt(at(8), nextFire(at(6, 40), at(6, 30), gapMinutes = 60))
    }

    /** The window end is an hour, not an instant — 20:30 is still inside a 20:00 bedtime. */
    @Test
    fun aFireTimeInsideTheBedtimeHourStands() {
        assertFiresAt(at(20, 30), nextFire(at(19, 45), at(19, 30), gapMinutes = 60))
    }

    /** Past bedtime there is nothing to arm: tomorrow starts from tomorrow's first drink. */
    @Test
    fun pastBedtime_thereIsNothingLeftToFire() {
        assertNull(nextFire(at(20, 40), at(20, 30), gapMinutes = 60))
    }

    @Test
    fun aFireTimeThatWouldRollPastMidnight_isDropped() {
        assertNull(nextFire(at(22), at(21, 30), gapMinutes = 240))
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

    /** Every gap the picker offers is a multiple of 30, so an on-time delivery is exact. */
    @Test
    fun elapsed_reportsTheGapExactlyWhenDeliveredOnTime() {
        assertEquals(30, elapsed(at(12), at(11, 30)))
        assertEquals(60, elapsed(at(12), at(11)))
        assertEquals(90, elapsed(at(12), at(10, 30)))
        assertEquals(120, elapsed(at(12), at(10)))
    }

    /**
     * A late delivery is the only way an odd total arises. Rounded down to the half-hour, so it
     * reads as "2 hours" rather than "125 minutes" and never claims more than has passed.
     */
    @Test
    fun elapsed_roundsALateDeliveryDownToTheHalfHour() {
        assertEquals(120, elapsed(at(12, 5), at(10)))
        assertEquals(120, elapsed(at(12, 29), at(10)))
        assertEquals(150, elapsed(at(12, 35), at(10)))
    }

    /**
     * The half-hour rounding above makes 90, 150, 330, 390 the ordinary values here, so the label
     * has to lead with hours — "330 minutes" is arithmetic, not language.
     */
    @Test
    fun aDurationSplitsIntoHoursAndTheMinutesLeftOver() {
        assertEquals(0 to 30, durationHoursAndMinutes(30))
        assertEquals(0 to 45, durationHoursAndMinutes(45))
        assertEquals(1 to 0, durationHoursAndMinutes(60))
        assertEquals(1 to 30, durationHoursAndMinutes(90))
        assertEquals(2 to 0, durationHoursAndMinutes(120))
        assertEquals(5 to 30, durationHoursAndMinutes(330))
        assertEquals(6 to 30, durationHoursAndMinutes(390))
    }

    /** Every gap the picker offers, so none of them can read as a pile of minutes. */
    @Test
    fun everyPickerChoiceLeadsWithHoursOnceItPassesOne() {
        IDLE_REMINDER_CHOICES_MINUTES.filter { it >= 60 }.forEach { choice ->
            assertTrue("$choice should have an hours part", durationHoursAndMinutes(choice).first > 0)
        }
    }

    /**
     * The elapsed rounding emits half-hour steps and nothing else, so walk the lot: past an hour
     * every one of them must lead with hours, and the remainder can only ever be 0 or 30.
     */
    @Test
    fun everyHalfHourStepUpToHalfADayLeadsWithHours() {
        (60..720 step 30).forEach { minutes ->
            val (hours, rest) = durationHoursAndMinutes(minutes)
            assertEquals("$minutes should be ${minutes / 60}h", minutes / 60, hours)
            assertTrue("$minutes left $rest minutes over", rest == 0 || rest == 30)
            assertTrue("$minutes should have an hours part", hours > 0)
        }
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
            *entries,
        )
    )

    private fun FakePrefs.shows(
        now: Calendar = at(12),
        lastIntake: Calendar = at(9),
        lastReminderPost: Calendar? = null,
    ) = shouldShowIdleReminder(
        this, notificationProgress(), now, lastIntake.timeInMillis, lastReminderPost?.timeInMillis ?: 0L
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
        assertFalse(prefs().shows(lastIntake = at(11, 30)))
    }

    /** The clock has not started, so there is no gap to report. */
    @Test
    fun hidden_whenNothingHasBeenLoggedToday() {
        assertFalse(prefs().shows(lastIntake = at(21, dayOffset = -1)))
    }

    @Test
    fun shown_whenTheGapHasElapsedExactly() {
        assertTrue(prefs().shows(lastIntake = at(11)))
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

    // ─── the stamp moving out of the main store ───────────────────

    /** An install from before the move: the stamp leaves the main store and keeps counting. */
    @Test
    fun moveLastIntakeAt_carriesTheStampOver() {
        val main = FakePrefs(mapOf(PREF_LAST_INTAKE_AT to at(9).timeInMillis))
        val transient = FakePrefs()
        moveLastIntakeAt(from = main, to = transient)
        assertFalse(main.contains(PREF_LAST_INTAKE_AT))
        assertEquals(at(9).timeInMillis, transient.lastIntakeAt(nowMillis = at(12).timeInMillis))
    }

    /** An old backup brings back an old stamp; the drink logged since is the one that counts. */
    @Test
    fun moveLastIntakeAt_neverOverwritesALaterDrink() {
        val main = FakePrefs(mapOf(PREF_LAST_INTAKE_AT to at(9).timeInMillis))
        val transient = FakePrefs(mapOf(PREF_LAST_INTAKE_AT to at(11).timeInMillis))
        moveLastIntakeAt(from = main, to = transient)
        assertFalse(main.contains(PREF_LAST_INTAKE_AT))
        assertEquals(at(11).timeInMillis, transient.lastIntakeAt(nowMillis = at(12).timeInMillis))
    }

    @Test
    fun moveLastIntakeAt_doesNothingOnceMoved() {
        val transient = FakePrefs(mapOf(PREF_LAST_INTAKE_AT to at(11).timeInMillis))
        moveLastIntakeAt(from = FakePrefs(), to = transient)
        assertEquals(at(11).timeInMillis, transient.lastIntakeAt(nowMillis = at(12).timeInMillis))
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

    /**
     * The gap is not asked — [idleCardAllowed] isn't even given the last drink — so a drink a
     * moment ago still leaves the card welcome.
     */
    @Test
    fun rebuild_isAllowedWithoutAskingAboutTheGap() {
        assertTrue(prefs().allows())
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
