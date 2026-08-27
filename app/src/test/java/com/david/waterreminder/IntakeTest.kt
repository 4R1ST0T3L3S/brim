package com.david.waterreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IntakeTest {

    private val today = LocalDate.of(2026, 8, 24)
    private val yesterday = today.minusDays(1)

    private fun prefsOnDay(vararg entries: Pair<String, Any>) =
        FakePrefs(mapOf("date" to today.toString(), *entries))

    // ─── intakeAfter — the arithmetic ─────────────────────────────

    @Test
    fun intakeAfter_add_belowGoal_leavesStreakAlone() {
        val result = intakeAfter(currentMl = 200, deltaMl = 300, personalGoalMl = 0, streak = 4)
        assertEquals(500, result.newTotal)
        assertFalse(result.crossedDailyGoal)
        assertFalse(result.fellBelowDailyGoal)
        assertEquals(4, result.streak)
    }

    @Test
    fun intakeAfter_crossingGoal_incrementsStreakOnce() {
        val crossing = intakeAfter(DEFAULT_DAILY_GOAL_ML - 100, 100, 0, 4)
        assertTrue(crossing.crossedDailyGoal)
        assertEquals(5, crossing.streak)

        // Already over: the next drink must not bank a second day.
        val again = intakeAfter(crossing.newTotal, 100, 0, crossing.streak)
        assertFalse(again.crossedDailyGoal)
        assertEquals(5, again.streak)
    }

    @Test
    fun intakeAfter_fallingBelowGoal_decrementsStreak() {
        val result = intakeAfter(DEFAULT_DAILY_GOAL_ML, -100, 0, 5)
        assertTrue(result.fellBelowDailyGoal)
        assertFalse(result.crossedDailyGoal)
        assertEquals(4, result.streak)
    }

    @Test
    fun intakeAfter_streakNeverGoesNegative() {
        assertEquals(0, intakeAfter(DEFAULT_DAILY_GOAL_ML, -100, 0, 0).streak)
    }

    /**
     * The whole point of [intakeAfter] taking no goal parameter: `goal_met` is what the midnight
     * rollover and the history chart read, so a personal goal must not move the line.
     */
    @Test
    fun intakeAfter_personalGoal_doesNotMoveTheStreakLine() {
        val result = intakeAfter(
            currentMl = DEFAULT_DAILY_GOAL_ML - 100,
            deltaMl = 100,
            personalGoalMl = 3_000,
            streak = 2,
        )
        assertTrue(result.crossedDailyGoal)
        assertEquals(3, result.streak)
        assertFalse(result.crossedPersonalGoal)
    }

    @Test
    fun intakeAfter_personalGoal_crossedOnceOnTheWayUp() {
        val crossing = intakeAfter(1_900, 200, personalGoalMl = 2_000, streak = 1)
        assertTrue(crossing.crossedPersonalGoal)
        assertFalse(intakeAfter(crossing.newTotal, 200, 2_000, 1).crossedPersonalGoal)
    }

    @Test
    fun intakeAfter_personalGoal_notCelebratedOnRemoval() {
        // Below the goal after removing, so nothing to re-cross; and never on a negative delta.
        assertFalse(intakeAfter(2_200, -100, personalGoalMl = 2_000, streak = 1).crossedPersonalGoal)
    }

    @Test
    fun intakeAfter_clampsToTheDayCeiling() {
        assertEquals(INTAKE_MAX_ML, intakeAfter(INTAKE_MAX_ML, 500, 0, 0).newTotal)
    }

    @Test
    fun intakeAfter_neverGoesBelowZero() {
        assertEquals(0, intakeAfter(100, -500, 0, 0).newTotal)
    }

    // ─── logIntake — the writes ───────────────────────────────────

    @Test
    fun logIntake_addsToTheStoredTotal() {
        val prefs = prefsOnDay("intake_ml" to 300)
        assertEquals(500, prefs.logIntake(200, today).newTotal)
        assertEquals(500, prefs.getInt("intake_ml", 0))
    }

    @Test
    fun logIntake_mirrorsTodayUnderItsDatedKey() {
        val prefs = prefsOnDay("intake_ml" to 300)
        prefs.logIntake(200, today)
        assertEquals(500, prefs.getInt(historyKey(today), 0))
    }

    @Test
    fun logIntake_negativeTakesWaterBack() {
        val prefs = prefsOnDay("intake_ml" to 500)
        assertEquals(300, prefs.logIntake(-200, today).newTotal)
        assertEquals(300, prefs.getInt(historyKey(today), 0))
    }

    @Test
    fun logIntake_writesStreakOnlyWhenTheGoalLineIsCrossed() {
        val prefs = prefsOnDay(
            "intake_ml" to DEFAULT_DAILY_GOAL_ML,
            "goal_met" to true,
            "streak" to 3,
        )
        // Already over the line — a top-up must not touch either key.
        prefs.logIntake(200, today)
        assertEquals(3, prefs.getInt("streak", 0))
        assertTrue(prefs.getBoolean("goal_met", false))
    }

    @Test
    fun logIntake_crossingGoal_banksTheDay() {
        val prefs = prefsOnDay("intake_ml" to DEFAULT_DAILY_GOAL_ML - 100, "streak" to 3)
        prefs.logIntake(100, today)
        assertEquals(4, prefs.getInt("streak", 0))
        assertTrue(prefs.getBoolean("goal_met", false))
    }

    @Test
    fun logIntake_removingBackUnderGoal_givesTheDayBack() {
        val prefs = prefsOnDay(
            "intake_ml" to DEFAULT_DAILY_GOAL_ML,
            "goal_met" to true,
            "streak" to 4,
        )
        prefs.logIntake(-100, today)
        assertEquals(3, prefs.getInt("streak", 0))
        assertFalse(prefs.getBoolean("goal_met", true))
    }

    /**
     * Logging after midnight from a notification button: yesterday has to be filed before the
     * amount lands, or it gets added to a total that belongs to a day that is already over.
     */
    @Test
    fun logIntake_afterMidnight_filesYesterdayFirst() {
        val prefs = FakePrefs(
            mapOf(
                "date" to yesterday.toString(),
                "intake_ml" to 1_600,
                "goal_met" to true,
                "streak" to 3,
            )
        )

        assertEquals(200, prefs.logIntake(200, today).newTotal)

        assertEquals(1_600, prefs.getInt(historyKey(yesterday), 0))
        assertEquals(200, prefs.getInt("intake_ml", 0))
        assertEquals(200, prefs.getInt(historyKey(today), 0))
        assertEquals(today.toString(), prefs.getString("date", ""))
        // Yesterday met the goal, so the streak carries into the new day.
        assertEquals(3, prefs.getInt("streak", 0))
        assertFalse(prefs.getBoolean("goal_met", true))
    }

    @Test
    fun logIntake_afterMidnight_missedDayBreaksTheStreak() {
        val prefs = FakePrefs(
            mapOf(
                "date" to yesterday.toString(),
                "intake_ml" to 400,
                "goal_met" to false,
                "streak" to 3,
            )
        )
        prefs.logIntake(200, today)
        assertEquals(0, prefs.getInt("streak", 0))
    }
}
