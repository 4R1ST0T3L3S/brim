package com.david.waterreminder

import android.content.Context
import android.content.SharedPreferences
import android.os.RemoteException
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Writes one day's drinks to Health Connect in the background, retrying on failure.
 * Diffs against what was last sent (own prefs), so it never reads Health Connect.
 */
class HealthConnectSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val day = inputData.getString(KEY_DAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return Result.failure()
        // Missing isn't empty: empty means every drink was taken back.
        val wanted = inputData.getString(KEY_ENTRIES)?.let(::decodeHydrationEntries)
            ?: return Result.failure()
        val store = applicationContext.transientPrefs()
        // Sync off or Health Connect gone: nothing to do.
        if (!store.healthConnectSyncOn()) return Result.success()
        val client = healthConnectClientOrNull(applicationContext) ?: return Result.success()

        return try {
            // Permission revoked: retrying won't help; Settings turns the switch off.
            if (!client.hasPermissions(HEALTH_CONNECT_SYNC_PERMISSIONS)) return Result.success()

            val synced = store.syncedHydration(day)
            val changes = healthConnectChanges(wanted, synced)
            if (!changes.isEmpty) {
                // Saved first, so an interrupted run can be finished by the next.
                store.setSyncedHydration(day, syncedWhileWriting(synced, changes))
                val version = store.nextHealthConnectVersion()
                if (changes.deletes.isNotEmpty()) client.deleteDrinks(changes.deletes, version)
                if (changes.upserts.isNotEmpty()) {
                    client.insertRecords(changes.upserts.mapNotNull { it.toRecordOrNull(version) })
                }
            }
            // What this run was given; a drink logged meanwhile queued its own sync.
            store.setSyncedHydration(day, wanted)
            Result.success()
        } catch (e: CancellationException) {
            // Replaced by a newer request, which carries on from here.
            throw e
        } catch (e: SecurityException) {
            Log.w(TAG, "Health Connect refused the write", e)
            Result.success()
        } catch (e: Exception) {
            // RemoteException/IOException: unreachable or rate-limited, so retry. Anything else would fail again.
            Log.w(TAG, "Health Connect sync failed (attempt ${runAttemptCount + 1})", e)
            val transient = e is RemoteException || e is IOException
            if (transient && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    /**
     * Deletes these drinks' records, even ones already gone. Health Connect fails the whole delete
     * ("invalid UID") on a missing id, so each is first written as a 0 ml placeholder.
     */
    private suspend fun HealthConnectClient.deleteDrinks(entries: List<HydrationEntry>, version: Long) {
        val placeholders = entries.mapNotNull { it.copy(ml = 0).toRecordOrNull(version) }
        if (placeholders.isEmpty()) return
        insertRecords(placeholders)
        // Only the ids just written; one that was refused would fail the delete.
        deleteRecords(
            HydrationRecord::class,
            recordIdsList = emptyList(),
            clientRecordIdsList = placeholders.mapNotNull { it.metadata.clientRecordId },
        )
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

/**
 * Sends a History editor change for a past day: clears this app's records for it, then writes
 * the new total as one record (none if cleared or zero). Safe to repeat.
 */
class HealthConnectDayWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val day = inputData.getString(KEY_DAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return Result.failure()
        val ml = inputData.getInt(KEY_ML, 0)
        val store = applicationContext.transientPrefs()
        if (!store.healthConnectSyncOn()) return Result.success()
        val client = healthConnectClientOrNull(applicationContext) ?: return Result.success()

        return try {
            if (!client.hasPermissions(HEALTH_CONNECT_SYNC_PERMISSIONS)) return Result.success()

            val start = startOfDayMillis(day)
            val end = startOfDayMillis(day.plusDays(1))
            // Deletes only this app's records, and drink records never cross midnight.
            client.deleteRecords(
                HydrationRecord::class,
                TimeRangeFilter.between(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end)),
            )
            if (ml > 0) {
                // Ends just before midnight, so it shows as 00:00 – 23:59.
                val record = HydrationEntry(dayTotalClientId(day), start, end - 1, ml)
                    .toRecordOrNull(store.nextHealthConnectVersion())
                if (record != null) client.insertRecords(listOf(record))
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Log.w(TAG, "Health Connect refused the day correction", e)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect day correction failed (attempt ${runAttemptCount + 1})", e)
            val transient = e is RemoteException || e is IOException
            if (transient && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

private const val TAG = "HealthConnectSync"

private const val KEY_DAY = "day"
private const val KEY_ML = "ml"
private const val KEY_ENTRIES = "entries"

/** Midnight at the start of [day], local time, in epoch millis. */
private fun startOfDayMillis(day: LocalDate): Long =
    day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** The record for this entry, or null if Health Connect rejects it, so one bad drink doesn't block the day. */
private fun HydrationEntry.toRecordOrNull(version: Long): HydrationRecord? = try {
    val start = Instant.ofEpochMilli(startMillis)
    val end = Instant.ofEpochMilli(endMillis)
    val zone = ZoneId.systemDefault().rules
    HydrationRecord(
        startTime = start,
        startZoneOffset = zone.getOffset(start),
        endTime = end,
        endZoneOffset = zone.getOffset(end),
        volume = Volume.milliliters(ml.toDouble()),
        // Entered by the user, not a sensor.
        metadata = Metadata.manualEntry(clientRecordId = clientId, clientRecordVersion = version),
    )
} catch (e: IllegalArgumentException) {
    Log.w(TAG, "Skipping an entry Health Connect won't take: $this", e)
    null
}

/** The last version written, in [transientPrefs]. */
private const val PREF_HEALTH_CONNECT_VERSION = "health_connect_version"

/** A version above any before; Health Connect ignores rewrites with a lower one, even if the clock goes back. */
private fun SharedPreferences.nextHealthConnectVersion(): Long {
    val version = maxOf(getLong(PREF_HEALTH_CONNECT_VERSION, 0L) + 1, System.currentTimeMillis())
    edit().putLong(PREF_HEALTH_CONNECT_VERSION, version).apply()
    return version
}

/** One queued sync per day; a newer tap replaces it, and midnight doesn't cancel the previous day's. */
private fun healthConnectSyncWorkName(day: LocalDate) = "health_connect_sync_$day"

/** Waits for the taps to stop, since Health Connect rate-limits writes. */
private const val HEALTH_CONNECT_SYNC_DELAY_SECONDS = 5L

/**
 * Queues a sync of today's drinks if sync is on. Call after [logIntake] and when sync is turned on.
 * The records travel in the request, so a run after midnight still sends the right day.
 */
fun requestHealthConnectSync(context: Context) {
    val store = context.transientPrefs()
    if (!store.healthConnectSyncOn()) return
    val today = LocalDate.now()
    val entries = store.hydrationToSend(today, dayEndMillis = startOfDayMillis(today.plusDays(1))) ?: return
    val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
        .setInputData(workDataOf(KEY_DAY to today.toString(), KEY_ENTRIES to encodeHydrationEntries(entries)))
        .setInitialDelay(HEALTH_CONNECT_SYNC_DELAY_SECONDS, TimeUnit.SECONDS)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork(healthConnectSyncWorkName(today), ExistingWorkPolicy.REPLACE, request)
}

/** Queues a History editor change to [day] if sync is on: new total in [ml], or null if cleared. Runs at once. */
fun requestHealthConnectDaySync(context: Context, day: LocalDate, ml: Int?) {
    if (!context.transientPrefs().healthConnectSyncOn()) return
    val request = OneTimeWorkRequestBuilder<HealthConnectDayWorker>()
        .setInputData(workDataOf(KEY_DAY to day.toString(), KEY_ML to (ml ?: 0)))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
    val workManager = WorkManager.getInstance(context)
    // Cancel that day's pending drink sync, or it would count the water twice.
    workManager.cancelUniqueWork(healthConnectSyncWorkName(day))
    workManager.enqueueUniqueWork("health_connect_day_$day", ExistingWorkPolicy.REPLACE, request)
}
