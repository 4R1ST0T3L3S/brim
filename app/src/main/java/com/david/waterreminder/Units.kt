package com.david.waterreminder

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import java.time.LocalDate
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
