package com.david.waterreminder

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

const val PREF_UNIT = "unit"
const val UNIT_ML   = "ml"
const val UNIT_CL   = "cl"

const val PREF_THEME_MODE = "theme_mode"
val DEFAULT_THEME_MODE = AppCompatDelegate.MODE_NIGHT_NO

val THEME_MODES = intArrayOf(
    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
    AppCompatDelegate.MODE_NIGHT_NO,
    AppCompatDelegate.MODE_NIGHT_YES
)

fun themeModeDialogIndex(mode: Int): Int =
    THEME_MODES.indexOfFirst { it == mode }.coerceAtLeast(0)

fun themeModeLabelRes(mode: Int): Int = when (mode) {
    AppCompatDelegate.MODE_NIGHT_NO -> R.string.theme_light
    AppCompatDelegate.MODE_NIGHT_YES -> R.string.theme_dark
    else -> R.string.theme_system_default
}

/** ml → display string in the active unit (no unit suffix; suitable for EditText). */
fun Int.toDisplay(unit: String): String =
    if (unit == UNIT_CL) formatCl(this) else this.toString()

/** ml → display string with unit suffix (e.g. "0.5 cl", "250 ml"). */
fun Int.fmt(unit: String): String = "${toDisplay(unit)} $unit"

fun Int.days(): String = if (this == 1) "1 day" else "$this days"

/** User-entered display string → ml. Accepts "0.5" / "0,5" for cl. Null if invalid or out of range. */
fun String.toMl(unit: String): Int? {
    val v = trim().replace(',', '.').toDoubleOrNull() ?: return null
    val ml = if (unit == UNIT_CL) v * 10 else v
    if (ml < 0 || ml > Int.MAX_VALUE.toDouble()) return null
    return ml.toInt()
}

const val CUSTOM_MAX_ML = 5_000
const val GOAL_MIN_ML   = 100
const val GOAL_MAX_ML   = 10_000

/** Default window for the built-in hourly reminder. */
const val DEFAULT_REMINDER_START_HOUR = 8
const val DEFAULT_REMINDER_END_HOUR   = 20

/**
 * Hour of day → the "08:00" label reminders are stored and keyed by.
 * Locale-fixed: the label is persisted and parsed back, so it must stay ASCII digits.
 */
fun Int.asHourLabel(): String = String.format(Locale.US, "%02d:00", this)

/** The "go to bed" hour that bounds the hourly reminder and every interval reminder. */
fun SharedPreferences.reminderWindowEndHour(): Int =
    Reminder.hourlyOrDefault(Reminder.parseAll(getStringSet("reminders_v3", emptySet()))).endHour

/**
 * When an interval reminder anchored at [anchorHour]:[anchorMinute] and repeating every
 * [intervalHours] hours should next fire, given it is now [from].
 *
 * Repeats stop once they would land past [windowEndHour] — the "go to bed" hour — and
 * pick up again at the anchor tomorrow, so an every-2-hours reminder doesn't ping at
 * 03:30. The anchor itself always fires even when it sits outside the window: the user
 * chose that time explicitly, so only the repeats are bounded.
 */
fun nextIntervalFireTime(
    from: Calendar,
    anchorHour: Int,
    anchorMinute: Int,
    intervalHours: Int,
    windowEndHour: Int
): Calendar {
    val step = intervalHours.coerceAtLeast(1)
    var repeat = 0
    while (true) {
        val hour = anchorHour + repeat * step
        if (hour > 23) break
        if (repeat > 0 && hour > windowEndHour) break
        val candidate = from.sameDayAt(hour, anchorMinute)
        if (candidate.after(from)) return candidate
        repeat++
    }
    return from.sameDayAt(anchorHour, anchorMinute)
        .apply { add(Calendar.DAY_OF_MONTH, 1) }
}

private fun Calendar.sameDayAt(hour: Int, minute: Int): Calendar =
    (clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

fun String.toValidCustomMl(unit: String): Int? =
    toMl(unit)?.takeIf { it in 1..CUSTOM_MAX_ML }

fun String.toValidGoalMl(unit: String): Int? =
    toMl(unit)?.takeIf { it in GOAL_MIN_ML..GOAL_MAX_ML }

fun SharedPreferences.loadAllHistory(): List<Pair<LocalDate, Int>> {
    val today = LocalDate.now()
    val fromHistory = all.keys
        .filter { it.startsWith("history_") }
        .mapNotNull { key ->
            val dateStr = key.removePrefix("history_")
            runCatching { LocalDate.parse(dateStr) }.getOrNull()
                ?.let { date -> date to getInt(key, 0) }
        }
        .toMap()
    val merged = fromHistory.toMutableMap()
    merged[today] = getInt("intake_ml", 0)
    return merged.entries.map { (date, ml) -> Pair(date, ml) }.sortedBy { it.first }
}

private fun formatCl(ml: Int): String {
    val cl = ml / 10.0
    return if (cl == cl.toInt().toDouble()) cl.toInt().toString()
           else String.format(Locale.US, "%.1f", cl)
}
