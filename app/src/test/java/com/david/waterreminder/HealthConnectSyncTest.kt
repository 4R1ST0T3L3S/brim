package com.david.waterreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HealthConnectSyncTest {

    private val today = LocalDate.of(2026, 10, 25)

    // ─── Drinks to records ─────────────────────────────────────────

    @Test
    fun entries_lastAMinuteWhenNothingFollowsSoon() {
        val entries = hydrationEntriesFor(listOf(Drink(0L, 250), Drink(600_000L, 500)))
        assertEquals(HydrationEntry("fillmeup-drink-0", 0L, HYDRATION_SPAN_MILLIS, 250), entries[0])
        assertEquals(600_000L + HYDRATION_SPAN_MILLIS, entries[1].endMillis)
    }

    /** Two quick taps must not overlap, or Health Connect might count the water once. */
    @Test
    fun entries_endWhereTheNextDrinkStarts() {
        val entries = hydrationEntriesFor(listOf(Drink(0L, 200), Drink(1_500L, 200)))
        assertEquals(1_500L, entries[0].endMillis)
    }

    /** Clearing the next day by time in Health Connect mustn't catch a drink from just before midnight. */
    @Test
    fun entries_stayInsideTheirDay() {
        val entries = hydrationEntriesFor(listOf(Drink(90_000L, 250)), dayEndMillis = 100_000L)
        assertEquals(100_000L, entries[0].endMillis)
    }

    @Test
    fun entries_neverEndWhereTheyStart() {
        val entries = hydrationEntriesFor(listOf(Drink(1_000L, 200), Drink(1_000L, 200)))
        assertTrue(entries.all { it.endMillis > it.startMillis })
    }

    // ─── What changes in Health Connect ────────────────────────────

    @Test
    fun changes_sendsEveryDrinkTheFirstTime() {
        val wanted = hydrationEntriesFor(listOf(Drink(0L, 250), Drink(600_000L, 500)))
        val changes = healthConnectChanges(wanted, synced = emptyList())
        assertEquals(wanted, changes.upserts)
        assertTrue(changes.deletes.isEmpty())
    }

    @Test
    fun changes_nothingWhenAlreadyInStep() {
        val wanted = hydrationEntriesFor(listOf(Drink(0L, 250)))
        assertTrue(healthConnectChanges(wanted, synced = wanted).isEmpty)
    }

    /** A new drink also cuts the one before short, so that one is rewritten too. */
    @Test
    fun changes_aQuickNewDrinkRewritesTheOneBefore() {
        val synced = hydrationEntriesFor(listOf(Drink(0L, 250)))
        val wanted = hydrationEntriesFor(listOf(Drink(0L, 250), Drink(10_000L, 250)))
        val changes = healthConnectChanges(wanted, synced)
        assertEquals(wanted, changes.upserts)
    }

    @Test
    fun changes_aShrunkDrinkIsRewritten() {
        val synced = hydrationEntriesFor(listOf(Drink(0L, 500)))
        val wanted = hydrationEntriesFor(listOf(Drink(0L, 300)))
        val changes = healthConnectChanges(wanted, synced)
        assertEquals(wanted, changes.upserts)
        assertTrue(changes.deletes.isEmpty())
    }

    @Test
    fun changes_anUndoneDrinkIsDeleted() {
        val synced = hydrationEntriesFor(listOf(Drink(0L, 250), Drink(600_000L, 500)))
        val wanted = hydrationEntriesFor(listOf(Drink(0L, 250)))
        val changes = healthConnectChanges(wanted, synced)
        assertEquals(listOf(synced[1]), changes.deletes)
    }

    @Test
    fun changes_undoingEverythingDeletesEverything() {
        val synced = hydrationEntriesFor(listOf(Drink(0L, 250), Drink(600_000L, 500)))
        val changes = healthConnectChanges(wanted = emptyList(), synced = synced)
        assertEquals(synced, changes.deletes)
    }

    /** The log dropping its oldest at the cap isn't the drink being taken back. */
    @Test
    fun changes_aDrinkPushedOutOfTheLogStays() {
        val synced = hydrationEntriesFor(listOf(Drink(0L, 100), Drink(600_000L, 100)))
        val wanted = hydrationEntriesFor(listOf(Drink(600_000L, 100), Drink(1_200_000L, 100)))
        val changes = healthConnectChanges(wanted, synced)
        assertTrue(changes.deletes.isEmpty())
        assertEquals(listOf(wanted[1]), changes.upserts)
    }

    // ─── A run stopped partway ─────────────────────────────────────

    /** Its record may have gone in before the run was stopped: taking the drink back must still delete it. */
    @Test
    fun whileWriting_aNewDrinkUndoneAfterAStoppedRunIsDeleted() {
        val synced = hydrationEntriesFor(listOf(Drink(0L, 250)))
        val wanted = hydrationEntriesFor(listOf(Drink(0L, 250), Drink(600_000L, 500)))
        val trail = syncedWhileWriting(synced, healthConnectChanges(wanted, synced))

        val changes = healthConnectChanges(wanted = synced, synced = trail)
        assertEquals(listOf(wanted[1].clientId), changes.deletes.map { it.clientId })
        assertTrue(changes.upserts.isEmpty())
    }

    /** Or it may not have: still wanted, it's written again rather than taken as sent. */
    @Test
    fun whileWriting_aNewDrinkStillWantedIsWrittenAgain() {
        val wanted = hydrationEntriesFor(listOf(Drink(0L, 250)))
        val trail = syncedWhileWriting(emptyList(), healthConnectChanges(wanted, emptyList()))
        assertEquals(HYDRATION_UNCONFIRMED_ML, trail.single().ml)
        assertEquals(wanted, healthConnectChanges(wanted, synced = trail).upserts)
    }

    /** A record being rewritten keeps what it was, so the rewrite is tried again. */
    @Test
    fun whileWriting_aRewriteInFlightIsTriedAgain() {
        val synced = hydrationEntriesFor(listOf(Drink(0L, 500)))
        val wanted = hydrationEntriesFor(listOf(Drink(0L, 300)))
        val trail = syncedWhileWriting(synced, healthConnectChanges(wanted, synced))
        assertEquals(synced, trail)
        assertEquals(wanted, healthConnectChanges(wanted, synced = trail).upserts)
    }

    // ─── What a request carries ────────────────────────────────────

    @Test
    fun toSend_isTodaysDrinks() {
        val prefs = FakePrefs()
        prefs.recordDrink(250, today, nowMillis = 1_000L)
        assertEquals(
            hydrationEntriesFor(listOf(Drink(1_000L, 250)), dayEndMillis = 100_000L),
            prefs.hydrationToSend(today, dayEndMillis = 100_000L),
        )
    }

    /** Every drink taken back is something to send — the deletes — not nothing. */
    @Test
    fun toSend_isEmptyWhenEverythingWasTakenBack() {
        val prefs = FakePrefs()
        prefs.recordDrink(250, today, nowMillis = 1_000L)
        prefs.recordDrink(-250, today, nowMillis = 2_000L)
        assertEquals(emptyList<HydrationEntry>(), prefs.hydrationToSend(today, dayEndMillis = 100_000L))
    }

    /** Turning sync on before the first drink of the day must not send yesterday's. */
    @Test
    fun toSend_isNothingForAnEarlierDaysLog() {
        val prefs = FakePrefs()
        prefs.recordDrink(250, today.minusDays(1), nowMillis = 1_000L)
        assertNull(prefs.hydrationToSend(today, dayEndMillis = 100_000L))
        assertNull(FakePrefs().hydrationToSend(today, dayEndMillis = 100_000L))
    }

    @Test
    fun entries_encodingRoundTrips() {
        val entries = listOf(
            HydrationEntry(hydrationClientId(0L), 0L, 60_000L, 250),
            HydrationEntry(hydrationClientId(90_000L), 90_000L, 90_001L, HYDRATION_UNCONFIRMED_ML),
        )
        assertEquals(entries, decodeHydrationEntries(encodeHydrationEntries(entries)))
        assertEquals(emptyList<HydrationEntry>(), decodeHydrationEntries(encodeHydrationEntries(emptyList())))
    }

    // ─── What was last sent ────────────────────────────────────────

    /** Yesterday's sync finishing late mustn't make today's drinks look unsent. */
    @Test
    fun synced_aLateRunForAnEarlierDayKeepsTodays() {
        val prefs = FakePrefs()
        val todays = hydrationEntriesFor(listOf(Drink(100_000L, 250)))
        prefs.setSyncedHydration(today, todays)
        prefs.setSyncedHydration(today.minusDays(1), hydrationEntriesFor(listOf(Drink(0L, 500))))
        assertEquals(todays, prefs.syncedHydration(today))
    }

    @Test
    fun synced_aLaterDayReplacesAnEarlierOne() {
        val prefs = FakePrefs()
        prefs.setSyncedHydration(today.minusDays(1), hydrationEntriesFor(listOf(Drink(0L, 500))))
        val todays = hydrationEntriesFor(listOf(Drink(100_000L, 250)))
        prefs.setSyncedHydration(today, todays)
        assertEquals(todays, prefs.syncedHydration(today))
    }

    @Test
    fun synced_roundTrips() {
        val prefs = FakePrefs()
        val entries = hydrationEntriesFor(listOf(Drink(0L, 250), Drink(30_000L, 500)))
        prefs.setSyncedHydration(today, entries)
        assertEquals(entries, prefs.syncedHydration(today))
    }

    /** A new day starts from nothing sent, so yesterday's records are never deleted. */
    @Test
    fun synced_isEmptyForAnotherDay() {
        val prefs = FakePrefs()
        prefs.setSyncedHydration(today.minusDays(1), hydrationEntriesFor(listOf(Drink(0L, 250))))
        assertTrue(prefs.syncedHydration(today).isEmpty())
    }

    @Test
    fun synced_skipsMalformedEntries() {
        val prefs = FakePrefs(mapOf(
            PREF_HEALTH_CONNECT_SYNCED_DATE to today.toString(),
            PREF_HEALTH_CONNECT_SYNCED to "0:60000:250;garbage;1:2:x;5:6:7:8;100:200:300",
        ))
        assertEquals(
            listOf(
                HydrationEntry(hydrationClientId(0L), 0L, 60_000L, 250),
                HydrationEntry(hydrationClientId(100L), 100L, 200L, 300),
            ),
            prefs.syncedHydration(today),
        )
    }

    @Test
    fun drinkLogDay_readsTheLogsDate() {
        val prefs = FakePrefs()
        prefs.recordDrink(250, today, nowMillis = 1_000L)
        assertEquals(today, prefs.drinkLogDay())
        assertEquals(null, FakePrefs().drinkLogDay())
    }
}
