package com.david.waterreminder

import android.content.SharedPreferences
import java.time.LocalDate

/**
 * Today's drinks, one entry per tap with the time it landed — the home screen's "Last drink" line
 * and the sheet it opens ([TodayDrinksSheet]).
 *
 * Today only, on purpose: the day's total already lives in the main store, and keeping a time for
 * every drink of every day would be a record of someone's routine that nothing in the app needs.
 * The log is filed under the day it belongs to, so a log from an earlier day reads as empty and is
 * replaced by the next drink — nothing has to remember to clear it at midnight.
 *
 * Lives in [transientPrefs], never the main store: [buildBackupJson] serialises that one
 * wholesale, and the backup agent copies it off the device.
 */

/** The day [PREF_DRINK_LOG] belongs to, as an ISO date. */
const val PREF_DRINK_LOG_DATE = "drink_log_date"

/** `millis:ml` pairs joined by `;`, oldest first. */
const val PREF_DRINK_LOG = "drink_log"

/**
 * Entries kept for one day. Far past a real day's drinking; it's there so someone leaning on the
 * add button can't grow the string without bound. The oldest give way, so "Last drink" stays right.
 */
const val DRINK_LOG_MAX_ENTRIES = 100

/** One drink still standing: always a positive amount, since water taken back undoes drinks. */
data class Drink(val atMillis: Long, val ml: Int)

/** [today]'s drinks, oldest first; empty when the stored log is from another day. The last is the latest drink. */
fun SharedPreferences.todaysDrinks(today: LocalDate = LocalDate.now()): List<Drink> {
    if (getString(PREF_DRINK_LOG_DATE, null) != today.toString()) return emptyList()
    return getString(PREF_DRINK_LOG, null).orEmpty()
        .split(';')
        .mapNotNull { entry ->
            val (millis, ml) = entry.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            Drink(millis.toLongOrNull() ?: return@mapNotNull null, ml.toIntOrNull() ?: return@mapNotNull null)
                .takeIf { it.ml > 0 }
        }
}

/**
 * Applies [ml] to [today]'s log, starting a fresh one if the stored log is from another day.
 *
 * Adding water is a new drink. Taking it back undoes drinks, newest first — the remove buttons are
 * there to correct a tap — so removing 250 ml right after adding it leaves no trace, and "Last
 * drink" falls back to the one before. A removal smaller than the latest drink shrinks it in place.
 */
fun SharedPreferences.recordDrink(ml: Int, today: LocalDate, nowMillis: Long) {
    if (ml == 0) return
    val drinks = todaysDrinks(today).toMutableList()
    if (ml > 0) {
        drinks += Drink(nowMillis, ml)
    } else {
        var toTakeBack = -ml
        while (toTakeBack > 0 && drinks.isNotEmpty()) {
            val latest = drinks.removeAt(drinks.lastIndex)
            if (latest.ml > toTakeBack) drinks += latest.copy(ml = latest.ml - toTakeBack)
            toTakeBack -= latest.ml
        }
    }
    edit()
        .putString(PREF_DRINK_LOG_DATE, today.toString())
        .putString(
            PREF_DRINK_LOG,
            drinks.takeLast(DRINK_LOG_MAX_ENTRIES).joinToString(";") { "${it.atMillis}:${it.ml}" }
        )
        .apply()
}

/**
 * Erases a log left over from an earlier day. Reads already ignore one; this is so the times
 * don't sit on the device until the next drink happens to replace them.
 */
fun SharedPreferences.clearStaleDrinkLog(today: LocalDate = LocalDate.now()) {
    val date = getString(PREF_DRINK_LOG_DATE, null) ?: return
    if (date == today.toString()) return
    edit().remove(PREF_DRINK_LOG_DATE).remove(PREF_DRINK_LOG).apply()
}
