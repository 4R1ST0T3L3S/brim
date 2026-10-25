package com.david.waterreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DrinkLogTest {

    private val today = LocalDate.of(2026, 8, 24)
    private val yesterday = today.minusDays(1)

    @Test
    fun recordDrink_keepsTheDayInOrder() {
        val log = FakePrefs()
        log.recordDrink(250, today, nowMillis = 1_000L)
        log.recordDrink(500, today, nowMillis = 2_000L)
        assertEquals(listOf(Drink(1_000L, 250), Drink(2_000L, 500)), log.todaysDrinks(today))
    }

    @Test
    fun recordDrink_ignoresZero() {
        val log = FakePrefs()
        log.recordDrink(0, today, nowMillis = 1_000L)
        assertFalse(log.contains(PREF_DRINK_LOG))
    }

    /** Nothing clears the log at midnight, so a read has to know an old one isn't today's. */
    @Test
    fun todaysDrinks_isEmptyForALogFromAnotherDay() {
        val log = FakePrefs()
        log.recordDrink(250, yesterday, nowMillis = 1_000L)
        assertTrue(log.todaysDrinks(today).isEmpty())
    }

    @Test
    fun recordDrink_startsAFreshLogOnANewDay() {
        val log = FakePrefs()
        log.recordDrink(250, yesterday, nowMillis = 1_000L)
        log.recordDrink(500, today, nowMillis = 2_000L)
        assertEquals(listOf(Drink(2_000L, 500)), log.todaysDrinks(today))
    }

    @Test
    fun recordDrink_dropsTheOldestPastTheCap() {
        val log = FakePrefs()
        repeat(DRINK_LOG_MAX_ENTRIES + 5) { log.recordDrink(100, today, nowMillis = it.toLong()) }
        val drinks = log.todaysDrinks(today)
        assertEquals(DRINK_LOG_MAX_ENTRIES, drinks.size)
        assertEquals((DRINK_LOG_MAX_ENTRIES + 4).toLong(), drinks.last().atMillis)
    }

    @Test
    fun todaysDrinks_skipsMalformedEntries() {
        val log = FakePrefs(mapOf(
            PREF_DRINK_LOG_DATE to today.toString(),
            PREF_DRINK_LOG to "1000:250;garbage;2000:x;3000:0;4000:-100;5000:500",
        ))
        assertEquals(listOf(Drink(1_000L, 250), Drink(5_000L, 500)), log.todaysDrinks(today))
    }

    // ─── Taking water back undoes drinks ──────────────────────────

    private fun logOf(vararg drinks: Drink) = FakePrefs().apply {
        drinks.forEach { recordDrink(it.ml, today, it.atMillis) }
    }

    /** The case the remove buttons exist for: a mistaken tap, taken straight back. */
    @Test
    fun removal_ofTheLatestDrink_fallsBackToTheOneBefore() {
        val log = logOf(Drink(1_000L, 250), Drink(2_000L, 250))
        log.recordDrink(-250, today, nowMillis = 3_000L)
        assertEquals(listOf(Drink(1_000L, 250)), log.todaysDrinks(today))
    }

    @Test
    fun removal_smallerThanTheLatestDrink_shrinksItInPlace() {
        val log = logOf(Drink(1_000L, 250), Drink(2_000L, 500))
        log.recordDrink(-100, today, nowMillis = 3_000L)
        assertEquals(listOf(Drink(1_000L, 250), Drink(2_000L, 400)), log.todaysDrinks(today))
    }

    @Test
    fun removal_largerThanTheLatestDrink_carriesIntoTheOnesBefore() {
        val log = logOf(Drink(1_000L, 250), Drink(2_000L, 250), Drink(3_000L, 100))
        log.recordDrink(-300, today, nowMillis = 4_000L)
        assertEquals(listOf(Drink(1_000L, 250), Drink(2_000L, 50)), log.todaysDrinks(today))
    }

    /** More taken back than the log holds — a total from before the log existed, say. */
    @Test
    fun removal_pastEverythingLogged_emptiesTheLog() {
        val log = logOf(Drink(1_000L, 250))
        log.recordDrink(-1_000, today, nowMillis = 2_000L)
        assertTrue(log.todaysDrinks(today).isEmpty())
    }

    @Test
    fun clearStaleDrinkLog_erasesAnEarlierDay() {
        val log = FakePrefs()
        log.recordDrink(250, yesterday, nowMillis = 1_000L)
        log.clearStaleDrinkLog(today)
        assertFalse(log.contains(PREF_DRINK_LOG))
        assertFalse(log.contains(PREF_DRINK_LOG_DATE))
    }

    @Test
    fun clearStaleDrinkLog_keepsToday() {
        val log = FakePrefs()
        log.recordDrink(250, today, nowMillis = 1_000L)
        log.clearStaleDrinkLog(today)
        assertEquals(listOf(Drink(1_000L, 250)), log.todaysDrinks(today))
    }

    // ─── Through logIntake ────────────────────────────────────────

    private fun prefsOnDay(intakeMl: Int) =
        FakePrefs(mapOf("date" to today.toString(), "intake_ml" to intakeMl))

    @Test
    fun logIntake_recordsTheDrink() {
        val log = FakePrefs()
        prefsOnDay(300).logIntake(200, today, nowMillis = 1_000L, transientStore = log)
        assertEquals(listOf(Drink(1_000L, 200)), log.todaysDrinks(today))
    }

    /** Only what the total actually lost is undone: 250 asked, 100 there to take. */
    @Test
    fun logIntake_removalUndoesOnlyWhatTheTotalLost() {
        val log = logOf(Drink(1_000L, 300))
        prefsOnDay(100).logIntake(-250, today, nowMillis = 2_000L, transientStore = log)
        assertEquals(listOf(Drink(1_000L, 200)), log.todaysDrinks(today))
    }

    @Test
    fun logIntake_removalAtZero_leavesTheLogAlone() {
        val log = logOf(Drink(1_000L, 250))
        prefsOnDay(0).logIntake(-250, today, nowMillis = 2_000L, transientStore = log)
        assertEquals(listOf(Drink(1_000L, 250)), log.todaysDrinks(today))
    }
}
