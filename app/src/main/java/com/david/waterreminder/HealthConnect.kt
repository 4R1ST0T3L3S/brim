package com.david.waterreminder

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord

/**
 * Health Connect: opt-in, write-only (Hydration). Built in from Android 14, an app on 9–13, absent below 9.
 * The library needs Android 8, so every call into it goes through [healthConnectPossible] first.
 */
enum class HealthConnectAvailability {
    /** Ready to ask for permission and write. */
    AVAILABLE,

    /** Installed but too old; the Play Store has the update. */
    UPDATE_REQUIRED,

    /** Not on this phone; the Settings row stays hidden. */
    UNAVAILABLE,
}

/** False below Android 9, where the library must never be called. */
fun healthConnectPossible(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

fun healthConnectAvailability(context: Context): HealthConnectAvailability {
    if (!healthConnectPossible()) return HealthConnectAvailability.UNAVAILABLE
    return when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            HealthConnectAvailability.UPDATE_REQUIRED
        else -> HealthConnectAvailability.UNAVAILABLE
    }
}

/** The client, or null when Health Connect isn't available ([HealthConnectClient.getOrCreate] would throw). */
fun healthConnectClientOrNull(context: Context): HealthConnectClient? =
    if (healthConnectAvailability(context) == HealthConnectAvailability.AVAILABLE) {
        HealthConnectClient.getOrCreate(context)
    } else null

/** Asked for when sync is turned on: write Hydration. */
val HEALTH_CONNECT_SYNC_PERMISSIONS: Set<String> by lazy {
    // Lazy, so loading this file on Android 7 or 8 doesn't call the library.
    setOf(HealthPermission.getWritePermission(HydrationRecord::class))
}

/** Whether all [permissions] are granted; the user can revoke them any time. */
suspend fun HealthConnectClient.hasPermissions(permissions: Set<String>): Boolean =
    permissionController.getGrantedPermissions().containsAll(permissions)

// ─── The sync switch ──────────────────────────────────────────────

/** Sync on/off, in [transientPrefs] so a backup restored elsewhere starts off and asks again. */
const val PREF_HEALTH_CONNECT_SYNC = "health_connect_sync"

/** The switch as left; Settings re-checks the permission, which can be revoked meanwhile. */
fun SharedPreferences.healthConnectSyncOn(): Boolean = getBoolean(PREF_HEALTH_CONNECT_SYNC, false)

fun SharedPreferences.setHealthConnectSync(on: Boolean) {
    edit().putBoolean(PREF_HEALTH_CONNECT_SYNC, on).apply()
}

/** Health Connect's Play Store page, for [HealthConnectAvailability.UPDATE_REQUIRED]. */
fun healthConnectUpdateIntent(context: Context): Intent =
    Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        // Google's url parameter: opens Health Connect's onboarding after the update.
        data = "market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding".toUri()
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
    }

/** The Health Connect app on Android 13 and lower. */
private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
