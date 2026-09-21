package com.david.waterreminder

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

/**
 * The app's one preferences store. Every screen and receiver goes through here, so the file name
 * is spelled once — a typo in a receiver would otherwise read an empty store and write to one
 * nothing else reads, with no compile error and no crash.
 */
fun Context.waterPrefs(): SharedPreferences =
    getSharedPreferences("water_prefs", Context.MODE_PRIVATE)

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

/**
 * The version the "What's new" popup was last shown for. Absent on a fresh install until
 * onboarding finishes, which is what keeps the popup from landing on top of the welcome flow.
 */
const val PREF_LAST_SEEN_VERSION_CODE = "last_seen_version_code"

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

/**
 * Tinted themes are not AppCompatDelegate night modes but are stored in the same preference, so
 * they need values none of the MODE_NIGHT_* constants use (those range from -100 to 4). Number
 * them from 100 up; the stored value is what a user's preference holds, so never reuse one.
 */
const val THEME_MODE_BLUE_DARK = 100
const val THEME_MODE_BLUE_LIGHT = 101

/**
 * Display order for the theme picker — light before dark within each pair. Deliberately not the
 * order the THEME_MODE_* values are numbered in: those are stored in users' preferences and are
 * fixed, while this list is free to be reordered.
 */
val THEME_MODES = intArrayOf(
    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
    AppCompatDelegate.MODE_NIGHT_NO,
    AppCompatDelegate.MODE_NIGHT_YES,
    THEME_MODE_BLUE_LIGHT,
    THEME_MODE_BLUE_DARK
)

fun themeModeDialogIndex(mode: Int): Int =
    THEME_MODES.indexOfFirst { it == mode }.coerceAtLeast(0)

fun themeModeLabelRes(mode: Int): Int = when (mode) {
    AppCompatDelegate.MODE_NIGHT_NO -> R.string.theme_light
    AppCompatDelegate.MODE_NIGHT_YES -> R.string.theme_dark
    THEME_MODE_BLUE_DARK -> R.string.theme_blue_dark
    THEME_MODE_BLUE_LIGHT -> R.string.theme_blue_light
    else -> R.string.theme_system_default
}

/** The night mode to hand to AppCompatDelegate for a stored theme mode. */
fun nightModeFor(mode: Int): Int = when (mode) {
    THEME_MODE_BLUE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
    THEME_MODE_BLUE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
    else -> mode
}

/** The activity theme to apply for a stored theme mode. */
fun themeStyleRes(mode: Int): Int = when (mode) {
    THEME_MODE_BLUE_DARK -> R.style.Theme_WaterReminder_BlueDark
    THEME_MODE_BLUE_LIGHT -> R.style.Theme_WaterReminder_BlueLight
    else -> R.style.Theme_WaterReminder
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

/** Quick add/remove amounts a fresh install ships with; the user can add to or remove from the set. */
val DEFAULT_QUICK_AMOUNTS_ML = listOf(100, 200)

/**
 * Cap on how many amounts each home row holds — 6 add buttons and the 6 matching remove ones,
 * which is what fits before the rows start wrapping into a wall of buttons.
 */
const val QUICK_AMOUNTS_MAX = 6

const val PREF_QUICK_AMOUNTS_ML = "quick_amounts_ml"

/** Pre-multi-amount pref: the single custom slot, folded into the list the first time it is read. */
const val PREF_CUSTOM_QUICK_ML = "custom_quick_ml"

/**
 * Sanitized amount list — in-range values only, no duplicates, sorted, capped at
 * [QUICK_AMOUNTS_MAX]. Applied to anything read from prefs, which can be hand-edited or imported.
 */
fun List<Int>.asQuickAmounts(): List<Int> =
    filter { it in 1..CUSTOM_MAX_ML }.distinct().sorted().take(QUICK_AMOUNTS_MAX)

fun List<Int>.encodeAmountCsv(): String = joinToString(",")

/** CSV -> the numbers in it, unfiltered. Each list applies its own sanitizer on top. */
fun String.decodeAmountCsv(): List<Int> = split(',').mapNotNull { it.trim().toIntOrNull() }


/**
 * The amounts both home rows are built from, given the [stored] pref and the legacy single custom
 * slot. A null [stored] means an install from before the amounts became editable: it starts from
 * the shipped defaults plus whatever custom value that user had. An empty [stored] is a real
 * state, not a missing one — every button may be removed.
 */
fun quickAmountsFrom(stored: String?, legacyCustomMl: Int): List<Int> =
    (stored?.decodeAmountCsv() ?: (DEFAULT_QUICK_AMOUNTS_ML + legacyCustomMl)).asQuickAmounts()

fun SharedPreferences.quickAmounts(): List<Int> =
    quickAmountsFrom(getString(PREF_QUICK_AMOUNTS_ML, null), getInt(PREF_CUSTOM_QUICK_ML, 0))

/**
 * Stores [amounts] sanitized and returns exactly what was stored, saving callers a re-read.
 *
 * A stored quick-log selection is re-filtered against the new list in the same commit, so a
 * deleted amount can't linger as a phantom button on the reminder card or the widget. This is tidying, not the
 * safeguard: prefs also arrive from an import, a cloud restore or a legacy install that never
 * wrote this key at all, none of which come through here — [notificationAmounts] filters on
 * every read for that reason.
 */
fun SharedPreferences.setQuickAmounts(amounts: List<Int>): List<Int> {
    val stored = amounts.asQuickAmounts()
    val previous = getString(PREF_NOTIFICATION_AMOUNTS_ML, null)?.decodeAmountCsv()
    val pruned = previous?.asNotificationAmounts(stored)
    edit()
        .putString(PREF_QUICK_AMOUNTS_ML, stored.encodeAmountCsv())
        .apply {
            when {
                // Never picked, so there is nothing to prune — leave it deriving.
                pruned == null -> {}
                // Everything the user had picked has just been edited or deleted away. Storing
                // the empty list here would read back as "every button deliberately turned off"
                // and never recover, so drop the key and let it derive again instead.
                pruned.isEmpty() && previous.isNotEmpty() -> remove(PREF_NOTIFICATION_AMOUNTS_ML)
                else -> putString(PREF_NOTIFICATION_AMOUNTS_ML, pruned.encodeAmountCsv())
            }
        }
        .apply()
    return stored
}

/**
 * Cap on how many quick amounts get a quick-log button. Three is all the standard notification
 * template will render, so the setting can't promise a fourth — and it happens to be what the
 * home-screen widget has room for too, which shares this selection. The two reasons are
 * unrelated; see [WidgetSizeBand] for the widget's own.
 */
const val NOTIFICATION_ACTIONS_MAX = 3

const val PREF_NOTIFICATION_AMOUNTS_ML = "notification_amounts_ml"

/**
 * Sanitized notification selection: only amounts that still have a button on the home screen,
 * no duplicates, sorted, capped at [NOTIFICATION_ACTIONS_MAX].
 */
fun List<Int>.asNotificationAmounts(available: List<Int>): List<Int> =
    filter { it in available }.distinct().sorted().take(NOTIFICATION_ACTIONS_MAX)

/**
 * The amounts to draw as quick-log buttons — on the reminder card, and on the home-screen
 * widget, which trims this list to what its width holds — given what the home screen offers.
 *
 * An absent key — nobody has opened the picker — derives the first [NOTIFICATION_ACTIONS_MAX]
 * available amounts rather than nothing: an empty default would ship the feature invisible to
 * everyone who never goes looking for it. A *stored* empty list is a real state and is
 * honoured, meaning the user turned every button off; [setQuickAmounts] is careful never to
 * manufacture it by pruning.
 */
fun SharedPreferences.notificationAmounts(): List<Int> {
    val available = quickAmounts()
    return (getString(PREF_NOTIFICATION_AMOUNTS_ML, null)?.decodeAmountCsv() ?: available)
        .asNotificationAmounts(available)
}

fun SharedPreferences.setNotificationAmounts(amounts: List<Int>) {
    edit()
        .putString(PREF_NOTIFICATION_AMOUNTS_ML, amounts.asNotificationAmounts(quickAmounts()).encodeAmountCsv())
        .apply()
}

/**
 * The selection with [ml] checked or unchecked. Checking past [NOTIFICATION_ACTIONS_MAX] drops
 * the amount at the front rather than refusing the tap — the picker states the limit before the
 * user gets there, so bouncing the tap back would only tell them they were wrong to try.
 *
 * The receiver is the picker's working list, which starts sorted (that is how the selection is
 * stored) and grows by appending. So the amount dropped is whichever has been checked longest,
 * and among the ones already checked when the dialog opened, the smallest.
 */
fun List<Int>.toggleNotificationAmount(ml: Int): List<Int> =
    if (ml in this) this - ml else (this + ml).takeLast(NOTIFICATION_ACTIONS_MAX)

/** The app's fixed recommended minimum, shown as the non-personalized default goal. */
const val DEFAULT_DAILY_GOAL_ML = 1500

/**
 * Whether reaching a goal pops a congratulations dialog. On by default: the celebration is part of
 * what the app is for, and only gets in the way for people who ask for it to stop.
 */
const val PREF_CELEBRATIONS_ENABLED = "celebrations_enabled"

fun SharedPreferences.celebrationsEnabled(): Boolean =
    getBoolean(PREF_CELEBRATIONS_ENABLED, true)

/**
 * Whether reminders keep firing once the day's goal is met. On by default — the reminder is
 * still useful past the minimum, and going quiet unasked reads like the app broke.
 *
 * Backed by the legacy `stop_notifications_on_goal` key, inverted here: the row used to read
 * "Pause when goal reached" and now reads "Display when goal reached", so the stored sense is
 * the opposite of the shown one. Flipping the key itself would need a migration on every
 * install — and on every exported backup — to buy nothing.
 */
const val PREF_STOP_NOTIFICATIONS_ON_GOAL = "stop_notifications_on_goal"

fun SharedPreferences.showNotificationsAfterGoal(): Boolean =
    !getBoolean(PREF_STOP_NOTIFICATIONS_ON_GOAL, false)

fun SharedPreferences.setShowNotificationsAfterGoal(show: Boolean) {
    edit().putBoolean(PREF_STOP_NOTIFICATIONS_ON_GOAL, !show).apply()
}

/** Water one bottle on the home row stands for — the goal decides how many bottles are shown. */
const val ML_PER_BOTTLE = 500

/** Fill steps a bottle icon can show, from empty (0) to full ([BOTTLE_FILL_STEPS]). */
const val BOTTLE_FILL_STEPS = 5

/**
 * Bottles picturing [goalMl]: one per [ML_PER_BOTTLE], rounded up so the goal is always covered —
 * 3 for the 1500 ml minimum, 20 for a [GOAL_MAX_ML] goal.
 */
fun bottleCount(goalMl: Int): Int {
    val goal = goalMl.coerceAtLeast(1).toLong()
    return ((goal + ML_PER_BOTTLE - 1) / ML_PER_BOTTLE).coerceIn(1, GOAL_MAX_ML.toLong()).toInt()
}

/**
 * Fill step of every bottle for an intake of [currentMl] against [goalMl], from 0 (empty) to
 * [BOTTLE_FILL_STEPS] (full). Each bottle holds [ML_PER_BOTTLE] except the last, which spans
 * whatever is left of a goal that isn't a round number of bottles — so reaching the goal always
 * fills the row and never leaves a bottle looking empty.
 */
fun bottleFillSteps(currentMl: Int, goalMl: Int): List<Int> {
    val goal = goalMl.coerceAtLeast(1)
    return (0 until bottleCount(goal)).map { index ->
        val start = index.toLong() * ML_PER_BOTTLE
        val span = (goal - start).coerceIn(1L, ML_PER_BOTTLE.toLong())
        val fill = (currentMl - start).coerceIn(0L, span)
        (fill * BOTTLE_FILL_STEPS / span).toInt()
    }
}

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
 * Fire times are confined to [windowStartHour]..[windowEndHour] and pick up again at the
 * anchor tomorrow, so an every-2-hours reminder doesn't ping at 03:30.
 *
 * An anchor before the get-up hour is pulled forward to the first repeat inside the window
 * — otherwise a reminder would fire at an hour the window says it shouldn't. An anchor past
 * the go-to-bed hour still fires: only repeats are bounded at the top, so a deliberately
 * late reminder isn't silently killed by a window it was never inside.
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

    // The day's first fire: the anchor, or the first repeat that reaches the get-up hour.
    // An anchor with no in-window slot left in the day keeps its own time rather than
    // going silent.
    var firstHour = anchorHour
    while (firstHour < windowStartHour && firstHour + step <= 23) firstHour += step

    var repeat = 0
    while (true) {
        val hour = firstHour + repeat * step
        if (hour > 23) break
        if (repeat > 0 && hour > windowEndHour) break
        val candidate = from.sameDayAt(hour, anchorMinute)
        if (candidate.after(from)) return candidate
        repeat++
    }
    // Nothing left today — same first fire, tomorrow.
    return from.sameDayAt(firstHour, anchorMinute)
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
