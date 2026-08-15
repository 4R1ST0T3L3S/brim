package com.david.waterreminder

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

const val PREF_UNIT = "unit"
const val UNIT_ML   = "ml"
const val UNIT_CL   = "cl"
const val UNIT_OZ   = "oz"

const val ML_PER_FL_OZ = 29.5735
const val KG_PER_LB = 0.45359237

/** oz (imperial volume) is currently the only unit that implies the imperial system — weight follows it (lb). */
fun isImperial(unit: String): Boolean = unit == UNIT_OZ

/** True for units whose typical values aren't whole numbers, so input fields should allow a decimal point. */
fun isDecimalUnit(unit: String): Boolean = unit == UNIT_CL || unit == UNIT_OZ

const val PREF_THEME_MODE = "theme_mode"
val DEFAULT_THEME_MODE = AppCompatDelegate.MODE_NIGHT_NO

const val PREF_PROFILE_SETUP_DONE = "profile_setup_done"
const val PREF_PROFILE_AGE = "profile_age"
const val PREF_PROFILE_WEIGHT_KG = "profile_weight_kg"

const val PROFILE_AGE_MIN = 1
const val PROFILE_AGE_MAX = 120
const val PROFILE_WEIGHT_KG_MIN = 1.0
const val PROFILE_WEIGHT_KG_MAX = 300.0

/** Total water (drinks + food moisture) estimate, ml per kg of body weight — the rate commonly attributed to EFSA (2010). */
const val TOTAL_WATER_ML_PER_KG = 35.0

/** Share of total water intake that should come from drinking; the rest is assumed to come from food moisture. */
const val DRINKING_WATER_SHARE = 0.8

/**
 * General hydration estimate (not medical advice). Under 18, EFSA's child/teen water AI
 * bands are age- and sex-driven rather than weight-driven — rather than approximate that with
 * a weight-based number (which can overshoot or undershoot depending on the band, and can even
 * exceed adult figures once averaged across sexes), we use the app's own flat recommended
 * minimum instead. 18 and up: weight × 35 ml/kg for total water, scaled down to the ~80% that
 * should come from drinking rather than food.
 */
fun recommendedGoalMl(ageYears: Int, weightKg: Double): Int {
    if (ageYears < 18) return DEFAULT_DAILY_GOAL_ML
    return (weightKg * TOTAL_WATER_ML_PER_KG * DRINKING_WATER_SHARE).toInt().coerceIn(GOAL_MIN_ML, GOAL_MAX_ML)
}

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
fun Int.toDisplay(unit: String): String = when (unit) {
    UNIT_CL -> formatCl(this)
    UNIT_OZ -> formatOz(this)
    else -> this.toString()
}

/** ml → display string with unit suffix (e.g. "0.5 cl", "250 ml", "8.5 oz"). */
fun Int.fmt(unit: String): String = "${toDisplay(unit)} $unit"

fun Int.days(): String = if (this == 1) "1 day" else "$this days"

/** User-entered display string → ml. Accepts "0.5" / "0,5" for cl/oz. Null if invalid or out of range. */
fun String.toMl(unit: String): Int? {
    val v = trim().replace(',', '.').toDoubleOrNull() ?: return null
    val ml = when (unit) {
        UNIT_CL -> v * 10
        UNIT_OZ -> v * ML_PER_FL_OZ
        else -> v
    }
    if (ml < 0 || ml > Int.MAX_VALUE.toDouble()) return null
    return ml.toInt()
}

const val CUSTOM_MAX_ML = 5_000
const val GOAL_MIN_ML   = 100
const val GOAL_MAX_ML   = 10_000

/** The app's fixed recommended minimum, shown as the non-personalized default goal. */
const val DEFAULT_DAILY_GOAL_ML = 1500

/** Default reminder window, and where the default reminder is anchored. */
const val DEFAULT_REMINDER_START_HOUR = 8
const val DEFAULT_REMINDER_END_HOUR   = 20

const val PREF_WINDOW_START_HOUR = "reminder_window_start_hour"
const val PREF_WINDOW_END_HOUR   = "reminder_window_end_hour"

/**
 * Hour of day → the "08:00" label reminders are stored and keyed by.
 * Locale-fixed: the label is persisted and parsed back, so it must stay ASCII digits.
 */
fun Int.asHourLabel(): String = String.format(Locale.US, "%02d:00", this)

/**
 * [start]..[end] if reminders can be scheduled in it, the default window otherwise — so
 * callers can trust start < end. A stored window can be inverted or out of range: the two
 * hour pickers don't constrain each other, and prefs can be hand-edited or imported.
 */
fun usableWindow(start: Int, end: Int): Pair<Int, Int> =
    if (start in 0..23 && end in 0..23 && start < end) start to end
    else DEFAULT_REMINDER_START_HOUR to DEFAULT_REMINDER_END_HOUR

/**
 * The "get up" and "go to bed" hours bounding every repeating reminder. Kept in their own
 * prefs, so the window outlives any single reminder the user edits or deletes.
 */
fun SharedPreferences.reminderWindow(): Pair<Int, Int> = usableWindow(
    getInt(PREF_WINDOW_START_HOUR, DEFAULT_REMINDER_START_HOUR),
    getInt(PREF_WINDOW_END_HOUR, DEFAULT_REMINDER_END_HOUR)
)

fun SharedPreferences.setReminderWindow(startHour: Int, endHour: Int) {
    edit().putInt(PREF_WINDOW_START_HOUR, startHour)
        .putInt(PREF_WINDOW_END_HOUR, endHour)
        .apply()
}

/**
 * When an interval reminder anchored at [anchorHour]:[anchorMinute] and repeating every
 * [intervalHours] hours should next fire, given it is now [from].
 *
 * Repeats are confined to [windowStartHour]..[windowEndHour] and pick up again at the
 * anchor tomorrow, so an every-2-hours reminder doesn't ping at 03:30. The anchor itself
 * always fires even outside the window: the user chose that time explicitly, so only the
 * repeats are bounded.
 */
fun nextIntervalFireTime(
    from: Calendar,
    anchorHour: Int,
    anchorMinute: Int,
    intervalHours: Int,
    windowEndHour: Int,
    windowStartHour: Int = 0
): Calendar {
    val step = intervalHours.coerceAtLeast(1)
    var repeat = 0
    while (true) {
        val hour = anchorHour + repeat * step
        if (hour > 23) break
        if (repeat > 0 && hour > windowEndHour) break
        // Skipped, not stopped: with an early anchor the later repeats still land in window.
        if (repeat == 0 || hour >= windowStartHour) {
            val candidate = from.sameDayAt(hour, anchorMinute)
            if (candidate.after(from)) return candidate
        }
        repeat++
    }
    return from.sameDayAt(anchorHour, anchorMinute)
        .apply { add(Calendar.DAY_OF_MONTH, 1) }
}

/** [hour]:[minute] today if that is still ahead, tomorrow otherwise. */
fun Calendar.nextOccurrenceOf(hour: Int, minute: Int): Calendar =
    sameDayAt(hour, minute).apply {
        if (!after(this@nextOccurrenceOf)) add(Calendar.DAY_OF_MONTH, 1)
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

/** "kg" or "lb" depending on the active water unit — oz implies imperial, everything else metric. */
fun weightUnitLabel(unit: String): String = if (isImperial(unit)) "lb" else "kg"

/** Age & weight validation bounds (stored in kg) converted to whichever unit is currently active. */
fun weightBoundsDisplay(unit: String): Pair<Int, Int> = if (isImperial(unit)) {
    (PROFILE_WEIGHT_KG_MIN / KG_PER_LB).toInt() to (PROFILE_WEIGHT_KG_MAX / KG_PER_LB).toInt()
} else {
    PROFILE_WEIGHT_KG_MIN.toInt() to PROFILE_WEIGHT_KG_MAX.toInt()
}

/** User-entered weight in the active unit → kg. Null if unparseable (range isn't checked here). */
fun String.parseWeightInput(unit: String): Double? {
    val entered = trim().replace(',', '.').toDoubleOrNull() ?: return null
    return if (isImperial(unit)) entered * KG_PER_LB else entered
}

/** Weight stored in kg → compact display string in the active unit (e.g. "70", "154.3"), no unit suffix. */
fun Float.formatWeight(unit: String): String {
    val display = if (isImperial(unit)) this / KG_PER_LB else this
    return if (display == display.toInt().toFloat()) display.toInt().toString()
        else String.format(Locale.US, "%.1f", display)
}

private fun formatCl(ml: Int): String {
    val cl = ml / 10.0
    return if (cl == cl.toInt().toDouble()) cl.toInt().toString()
           else String.format(Locale.US, "%.1f", cl)
}

private fun formatOz(ml: Int): String {
    val oz = ml / ML_PER_FL_OZ
    return if (oz == oz.toInt().toDouble()) oz.toInt().toString()
           else String.format(Locale.US, "%.1f", oz)
}
