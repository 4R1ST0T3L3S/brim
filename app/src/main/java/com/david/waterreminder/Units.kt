package com.david.waterreminder

import java.util.Locale

const val PREF_UNIT = "unit"
const val UNIT_ML   = "ml"
const val UNIT_CL   = "cl"

/** ml → display string in the active unit (no unit suffix; suitable for EditText). */
fun Int.toDisplay(unit: String): String =
    if (unit == UNIT_CL) formatCl(this) else this.toString()

/** ml → display string with unit suffix (e.g. "0.5 cl", "250 ml"). */
fun Int.fmt(unit: String): String = "${toDisplay(unit)} $unit"

/** User-entered display string → ml. Accepts "0.5" / "0,5" for cl. Null if invalid. */
fun String.toMl(unit: String): Int? {
    val v = trim().replace(',', '.').toDoubleOrNull() ?: return null
    return if (unit == UNIT_CL) (v * 10).toInt() else v.toInt()
}

private fun formatCl(ml: Int): String {
    val cl = ml / 10.0
    return if (cl == cl.toInt().toDouble()) cl.toInt().toString()
           else String.format(Locale.US, "%.1f", cl)
}
