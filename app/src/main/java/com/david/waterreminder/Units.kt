package com.david.waterreminder

const val PREF_UNIT = "unit"
const val UNIT_ML   = "ml"
const val UNIT_CL   = "cl"

/** ml → display value (divides by 10 for cl). */
fun Int.toDisplay(unit: String): Int = if (unit == UNIT_CL) this / 10 else this

/** ml → "X ml" or "X cl" string. */
fun Int.fmt(unit: String) = "${toDisplay(unit)} $unit"

/** User-entered display value → ml (multiplies by 10 for cl). */
fun Int.toMl(unit: String): Int = if (unit == UNIT_CL) this * 10 else this
