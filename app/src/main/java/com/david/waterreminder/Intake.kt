package com.david.waterreminder

import android.content.SharedPreferences
import java.time.LocalDate

// ─── The day, and where it is stored ──────────────────────────────

/**
 * Upper bound for a hand-edited daily total — the same ceiling a goal gets, so a day can be
 * recorded up to the largest target the app will accept and no further.
 */
const val DAY_TOTAL_MAX_ML = GOAL_MAX_ML

/**
 * Ceiling on a day's running total as tapped in on the home screen. Far past anything drinkable —
 * it's there so the "x / goal" readout stays a readable number no matter how long someone leans on
 * the add buttons, and so the arithmetic downstream of it stays well inside an Int.
 */
const val INTAKE_MAX_ML = 1_000_000

/** A day's total clamped to [INTAKE_MAX_ML]. Applied on every write and every read. */
fun Int.asDayIntake(): Int = coerceIn(0, INTAKE_MAX_ML)

/** A whole day's intake typed into the history editor. 0 is allowed: it means "nothing that day". */
fun String.toValidDayTotalMl(unit: String): Int? =
    toMl(unit)?.takeIf { it in 0..DAY_TOTAL_MAX_ML }

/** Days the History screen's weekly chart covers, today included. */
const val WEEKLY_HISTORY_DAYS = 7

/** Prefix every dated intake record is stored under — the one place that spelling is decided. */
const val HISTORY_KEY_PREFIX = "history_"

/** Where a day's total is filed. Today's authoritative number is `intake_ml`; the dated key mirrors it. */
fun historyKey(date: LocalDate) = "$HISTORY_KEY_PREFIX$date"

/**
 * What the app currently holds for [date] — today's live counter, the dated record for any other
 * day, and 0 for a day never tracked. The single answer to "how much on this day", so the chart,
 * the day picker and the editor can't disagree about where a number comes from.
 */
fun SharedPreferences.dayTotal(date: LocalDate): Int =
    (if (date == LocalDate.now()) getInt("intake_ml", 0) else getInt(historyKey(date), 0)).asDayIntake()

/**
 * Moves the stored day forward when the calendar day has changed: the finished day's intake is
 * filed under its dated key, today starts from zero, and the streak survives only if that day
 * both was yesterday and met the goal. A no-op once the stored day is already today.
 */
fun SharedPreferences.rollOverDayIfNeeded(now: LocalDate = LocalDate.now()) {
    val today = now.toString()
    val savedDate = getString("date", "")
    if (savedDate == today) return

    val yesterday = now.minusDays(1).toString()
    val streakBroken = savedDate != yesterday || !getBoolean("goal_met", false)

    // One commit, so the day can never be filed away without the counter being reset behind it.
    edit()
        .apply {
            if (!savedDate.isNullOrEmpty()) {
                putInt("$HISTORY_KEY_PREFIX$savedDate", getInt("intake_ml", 0))
            }
        }
        .putString("date", today)
        .putInt("intake_ml", 0)
        .putBoolean("goal_met", false)
        .putInt("streak", if (streakBroken) 0 else getInt("streak", 0))
        .apply()
}

/**
 * The days the history editor may touch: the six finished days the weekly chart covers, oldest
 * first. Today is deliberately absent — it belongs to the home screen's counter, not to a text
 * field — and so is anything older than the week on show.
 */
fun editableHistoryDays(): List<LocalDate> {
    val today = LocalDate.now()
    return (WEEKLY_HISTORY_DAYS - 1 downTo 1).map { today.minusDays(it.toLong()) }
}

/** The recorded total for [date], or null when that day has no record at all. */
fun SharedPreferences.storedDayTotal(date: LocalDate): Int? =
    getInt(historyKey(date), -1).takeIf { it >= 0 }

/**
 * Overwrites [date]'s total. Only ever a finished day, so `intake_ml` and the goal/streak flags —
 * which all describe today — stay out of it. Call [rollOverDayIfNeeded] first, so a day that has
 * just ended is already filed under its dated key rather than still sitting in `intake_ml`.
 */
fun SharedPreferences.writeDayTotal(date: LocalDate, ml: Int) {
    edit().putInt(historyKey(date), ml).apply()
}

/** Drops [date] from history entirely, so it stops counting as a tracked day. */
fun SharedPreferences.removeDayTotal(date: LocalDate) {
    edit().remove(historyKey(date)).apply()
}

fun SharedPreferences.loadAllHistory(): List<Pair<LocalDate, Int>> {
    val today = LocalDate.now()
    val fromHistory = all.keys
        .filter { it.startsWith(HISTORY_KEY_PREFIX) }
        .mapNotNull { key ->
            val dateStr = key.removePrefix(HISTORY_KEY_PREFIX)
            runCatching { LocalDate.parse(dateStr) }.getOrNull()
                ?.let { date -> date to getInt(key, 0).asDayIntake() }
        }
        .toMap()
    val merged = fromHistory.toMutableMap()
    merged[today] = getInt("intake_ml", 0).asDayIntake()
    return merged.entries.map { (date, ml) -> Pair(date, ml) }.sortedBy { it.first }
}

// ─── Logging a drink ──────────────────────────────────────────────

/**
 * What logging an amount did to the day. The two goal flags are separate because they answer
 * different questions: [crossedDailyGoal] and [fellBelowDailyGoal] drive the streak, while
 * [crossedPersonalGoal] only decides whether a congratulation is due.
 */
data class IntakeResult(
    val newTotal: Int,
    val crossedDailyGoal: Boolean,
    val fellBelowDailyGoal: Boolean,
    val crossedPersonalGoal: Boolean,
    val streak: Int,
)

/**
 * The day's arithmetic, with nothing read or written — every decision about the streak lives
 * here so the home screen and a notification button can't drift apart on it.
 *
 * The streak is scored against [DEFAULT_DAILY_GOAL_ML] and never against [personalGoalMl].
 * That is deliberate, not an oversight: `goal_met` is what [rollOverDayIfNeeded] consults at
 * midnight and what the history chart ticks days by, so scoring it against a personal goal
 * would make today disagree with every day already recorded.
 *
 * A personal goal is only ever crossed upward — removing water re-arms the congratulation
 * rather than firing a second one on the way back up, which is what the home screen has
 * always done.
 */
fun intakeAfter(
    currentMl: Int,
    deltaMl: Int,
    personalGoalMl: Int,
    streak: Int,
): IntakeResult {
    val wasGoalMet = currentMl >= DEFAULT_DAILY_GOAL_ML
    val wasPersonalGoalMet = personalGoalMl > 0 && currentMl >= personalGoalMl

    val newTotal = (currentMl + deltaMl).asDayIntake()
    val goalMet = newTotal >= DEFAULT_DAILY_GOAL_ML

    val crossed = !wasGoalMet && goalMet
    val fellBelow = wasGoalMet && !goalMet

    return IntakeResult(
        newTotal = newTotal,
        crossedDailyGoal = crossed,
        fellBelowDailyGoal = fellBelow,
        crossedPersonalGoal = deltaMl > 0 && personalGoalMl > 0 &&
            !wasPersonalGoalMet && newTotal >= personalGoalMl,
        streak = when {
            crossed -> streak + 1
            fellBelow -> (streak - 1).coerceAtLeast(0)
            else -> streak
        },
    )
}

/**
 * Adds [ml] to today (negative to take it back) and commits the consequences in one write.
 *
 * The running total is read from prefs rather than taken from the caller, which is what makes
 * this safe to call from a notification button while the home screen is on screen holding a
 * counter of its own: whoever writes last still adds to the real number instead of overwriting
 * it with a stale one.
 *
 * Two things go to [transientStore] — [transientPrefs], which keeps them out of exports and
 * backups — rather than to this store: the amount, for the home screen's list of today's
 * drinks, and for a positive [ml] the [PREF_LAST_INTAKE_AT] stamp the idle reminder counts from.
 * Required rather than optional so no caller can leave its drinks out of either. The rescheduling
 * that has to follow needs a Context and so stays with the callers — see [onIntakeLogged].
 *
 * [rollOverDayIfNeeded] runs first, so an amount logged after midnight starts a fresh day
 * instead of being added to yesterday's total — which also fixes the case of a screen left
 * open across midnight, since until now nothing rolled the day over without the app in front.
 */
fun SharedPreferences.logIntake(
    ml: Int,
    now: LocalDate = LocalDate.now(),
    nowMillis: Long = System.currentTimeMillis(),
    transientStore: SharedPreferences,
): IntakeResult {
    rollOverDayIfNeeded(now)

    val currentMl = getInt("intake_ml", 0).asDayIntake()
    val result = intakeAfter(
        currentMl = currentMl,
        deltaMl = ml,
        personalGoalMl = getInt("personal_goal_ml", 0),
        streak = getInt("streak", 0),
    )

    // Both written before the main store: the home screen redraws from its listener on that write,
    // and reads the log to do it.
    // What the total actually moved by, so a removal at 0 or an add past the ceiling isn't logged
    // and a removal undoes no more than it took off.
    transientStore.recordDrink(result.newTotal - currentMl, now, nowMillis)
    // Only adding water counts as a drink. Taking some back is undoing a mistaken tap, and letting
    // that restart the idle countdown would push the reminder away from the user who most needs
    // it. See [IdleReminderScheduler].
    if (ml > 0) transientStore.edit().putLong(PREF_LAST_INTAKE_AT, nowMillis).apply()

    edit()
        .putInt("intake_ml", result.newTotal)
        // Always mirrored under the dated key so the day survives a reinstall or a restore.
        .putInt(historyKey(now), result.newTotal)
        .apply {
            // Left alone unless the goal line was actually crossed, so a plain top-up can
            // never rewrite a streak it has nothing to say about.
            if (result.crossedDailyGoal || result.fellBelowDailyGoal) {
                putInt("streak", result.streak)
                putBoolean("goal_met", result.crossedDailyGoal)
            }
        }
        .apply()

    return result
}
