package com.david.waterreminder

import android.content.SharedPreferences
import java.time.LocalDate

/**
 * What to send to Health Connect: one Hydration record per drink in [todaysDrinks]; undo = rewrite or delete.
 * No Health Connect classes here, so it runs in JVM tests. The writing is [HealthConnectSyncWorker].
 */

/** How long one drink lasts; Health Connect needs an end after the start. */
const val HYDRATION_SPAN_MILLIS = 60_000L

/** One record to write. [clientId] comes from the tap's time, so resending replaces it. */
data class HydrationEntry(val clientId: String, val startMillis: Long, val endMillis: Long, val ml: Int)

fun hydrationClientId(drinkAtMillis: Long) = "fillmeup-drink-$drinkAtMillis"

/** The single record a day becomes after a History editor correction. */
fun dayTotalClientId(day: LocalDate) = "fillmeup-day-$day"

/**
 * The records [drinks] become, oldest first. Each ends at the next drink or [dayEndMillis],
 * so records never overlap (counted once) and never cross midnight.
 */
fun hydrationEntriesFor(drinks: List<Drink>, dayEndMillis: Long = Long.MAX_VALUE): List<HydrationEntry> =
    drinks.mapIndexed { i, drink ->
        val next = drinks.getOrNull(i + 1)?.atMillis ?: dayEndMillis
        val end = minOf(drink.atMillis + HYDRATION_SPAN_MILLIS, next, dayEndMillis)
        HydrationEntry(
            clientId = hydrationClientId(drink.atMillis),
            startMillis = drink.atMillis,
            // Never empty: Health Connect refuses end == start.
            endMillis = maxOf(end, drink.atMillis + 1),
            ml = drink.ml,
        )
    }

/** What Health Connect needs to match the log. */
data class HealthConnectChanges(val upserts: List<HydrationEntry>, val deletes: List<HydrationEntry>) {
    val isEmpty get() = upserts.isEmpty() && deletes.isEmpty()
}

/**
 * New or changed records to write, removed ones to delete. Drinks older than the oldest
 * left are kept: the log dropped them at [DRINK_LOG_MAX_ENTRIES], they weren't taken back.
 */
fun healthConnectChanges(wanted: List<HydrationEntry>, synced: List<HydrationEntry>): HealthConnectChanges {
    val syncedById = synced.associateBy { it.clientId }
    val wantedIds = wanted.mapTo(HashSet()) { it.clientId }
    val oldestKept = wanted.minOfOrNull { it.startMillis }
    return HealthConnectChanges(
        upserts = wanted.filter { syncedById[it.clientId] != it },
        deletes = synced
            .filter { it.clientId !in wantedIds && (oldestKept == null || it.startMillis > oldestKept) },
    )
}

/** Today's records, taken when the sync is asked for; null if the log isn't [today]'s. */
fun SharedPreferences.hydrationToSend(today: LocalDate, dayEndMillis: Long): List<HydrationEntry>? =
    if (drinkLogDay() == today) hydrationEntriesFor(todaysDrinks(today), dayEndMillis) else null

/**
 * What to store as sent while writing, so an interrupted run is finished by the next.
 * New records are marked [HYDRATION_UNCONFIRMED_ML]: rewritten if still wanted, deleted if not.
 */
fun syncedWhileWriting(synced: List<HydrationEntry>, changes: HealthConnectChanges): List<HydrationEntry> {
    val syncedIds = synced.mapTo(HashSet()) { it.clientId }
    return synced + changes.upserts
        .filter { it.clientId !in syncedIds }
        .map { it.copy(ml = HYDRATION_UNCONFIRMED_ML) }
}

/** Never a real amount, so it never matches a wanted record. */
const val HYDRATION_UNCONFIRMED_ML = -1

// ─── What was last sent ───────────────────────────────────────────

/** The day [PREF_HEALTH_CONNECT_SYNCED] is about, in [transientPrefs]. */
const val PREF_HEALTH_CONNECT_SYNCED_DATE = "health_connect_synced_date"

/** The records last sent for that day, encoded by [encodeHydrationEntries]. */
const val PREF_HEALTH_CONNECT_SYNCED = "health_connect_synced"

/** [entries] as `start:end:ml` joined by `;`. */
fun encodeHydrationEntries(entries: List<HydrationEntry>): String =
    entries.joinToString(";") { "${it.startMillis}:${it.endMillis}:${it.ml}" }

/** Reverse of [encodeHydrationEntries]; skips anything malformed. */
fun decodeHydrationEntries(encoded: String?): List<HydrationEntry> =
    encoded.orEmpty()
        .split(';')
        .mapNotNull { entry ->
            val parts = entry.split(':').takeIf { it.size == 3 } ?: return@mapNotNull null
            val start = parts[0].toLongOrNull() ?: return@mapNotNull null
            val end = parts[1].toLongOrNull() ?: return@mapNotNull null
            val ml = parts[2].toIntOrNull() ?: return@mapNotNull null
            HydrationEntry(hydrationClientId(start), start, end, ml)
        }

/** What was last sent for [day]; empty if nothing, or if stored for another day. */
fun SharedPreferences.syncedHydration(day: LocalDate): List<HydrationEntry> {
    if (getString(PREF_HEALTH_CONNECT_SYNCED_DATE, null) != day.toString()) return emptyList()
    return decodeHydrationEntries(getString(PREF_HEALTH_CONNECT_SYNCED, null))
}

/** Stores [entries] as sent for [day], unless a later day is already stored. */
fun SharedPreferences.setSyncedHydration(day: LocalDate, entries: List<HydrationEntry>) {
    val stored = getString(PREF_HEALTH_CONNECT_SYNCED_DATE, null)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (stored != null && stored.isAfter(day)) return
    edit()
        .putString(PREF_HEALTH_CONNECT_SYNCED_DATE, day.toString())
        .putString(PREF_HEALTH_CONNECT_SYNCED, encodeHydrationEntries(entries))
        .apply()
}

/** The drink log's day; null when there's no log, as after an import. */
fun SharedPreferences.drinkLogDay(): LocalDate? =
    getString(PREF_DRINK_LOG_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
