package com.david.waterreminder

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

// What the widget decides before a RemoteViews is involved, kept apart from WaterWidgetProvider
// so all of it is reachable from a JVM test.

/** Width three "+100 ml" chips need, with their gaps and the card's padding. */
const val THREE_CHIP_MIN_WIDTH_DP = 224

/**
 * How much of the card a width buys. One band rather than a function per setting, so they can't
 * turn over at different widths — and it is all a redraw varies over, which lets two widgets in
 * the same band share one RemoteViews.
 */
enum class WidgetSizeBand(
    val chips: Int,
    /** Must match the size in `Widget.WaterReminder.WidgetChip`, which the layout starts from. */
    val chipTextSp: Float,
    /**
     * Whether a met goal is announced beside the total. The percentage is on the goal line at
     * every size: beside a total that can read "2030 ml" and never yields, it was what got cut.
     */
    val spellsOutGoalReached: Boolean,
) {
    WIDE(chips = 3, chipTextSp = 13f, spellsOutGoalReached = true),

    /**
     * Smaller type rather than a label stripped of its unit: two chips on two cells are ~64dp
     * each, and "+1500 ml" wants 55dp at 13sp against 47 at 11 — the margin a long oz label
     * needs. Still sp, so a large font scale can outgrow it; overriding that would be worse.
     */
    COMPACT(chips = 2, chipTextSp = 11f, spellsOutGoalReached = false),
}

/**
 * A width test rather than a cell count: `OPTION_APPWIDGET_MIN_WIDTH` reports the dp a launcher
 * really hands over, and real cells are wider than the 70n − 30 the provider declares bounds in.
 */
fun widgetSizeBand(widthDp: Int): WidgetSizeBand =
    if (widthDp >= THREE_CHIP_MIN_WIDTH_DP) WidgetSizeBand.WIDE else WidgetSizeBand.COMPACT

/**
 * The **Quick log buttons** selection, trimmed to what [band] holds. Shared with the reminder card
 * because both answer the same question; a second picker would only be somewhere to disagree. The
 * trim is the widget's own — narrowing drops a chip without touching the setting.
 */
fun SharedPreferences.widgetAmounts(band: WidgetSizeBand): List<Int> =
    notificationAmounts().take(band.chips)

/**
 * Each theme's page background and the text that sits on it. A widget is drawn by the launcher and
 * can't read theme attributes, so these name concrete resources. No chip colour: that one is fixed
 * in `Widget.WaterReminder.WidgetChip`.
 */
enum class WidgetPalette(
    val backgroundRes: Int,
    val textColorRes: Int,
    val textVariantColorRes: Int,
) {
    LIGHT(
        R.drawable.widget_bg_light,
        R.color.widget_text_light,
        R.color.widget_text_variant_light,
    ),
    DARK(
        R.drawable.widget_bg_dark,
        R.color.widget_text_dark,
        R.color.widget_text_variant_dark,
    ),
    BLUE_LIGHT(
        R.drawable.widget_bg_blue_light,
        R.color.blue_light_on_surface,
        R.color.blue_light_on_surface_variant,
    ),
    BLUE_DARK(
        R.drawable.widget_bg_blue_dark,
        R.color.blue_dark_on_surface,
        R.color.blue_dark_on_surface_variant,
    ),
}

/**
 * "Follow system" — and any unrecognised value, since prefs can arrive from an import — resolves
 * against [systemIsNight], the *launcher's* night mode: that is the surface the widget sits on.
 */
fun widgetPaletteFor(themeMode: Int, systemIsNight: Boolean): WidgetPalette = when (themeMode) {
    THEME_MODE_BLUE_LIGHT -> WidgetPalette.BLUE_LIGHT
    THEME_MODE_BLUE_DARK -> WidgetPalette.BLUE_DARK
    AppCompatDelegate.MODE_NIGHT_NO -> WidgetPalette.LIGHT
    AppCompatDelegate.MODE_NIGHT_YES -> WidgetPalette.DARK
    else -> if (systemIsNight) WidgetPalette.DARK else WidgetPalette.LIGHT
}

fun Context.widgetPalette(prefs: SharedPreferences): WidgetPalette = widgetPaletteFor(
    prefs.getInt(PREF_THEME_MODE, DEFAULT_THEME_MODE),
    systemIsNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES,
)

/** What the card is handed to draw, apart from its colours. */
data class WidgetState(
    val progress: NotificationProgress,
    val unit: String,
    val amountsMl: List<Int>,
)

/**
 * Through [notificationProgress] rather than [dayTotal]: nothing has rolled the stored day over
 * out here, so yesterday's total must not be drawn as today's.
 */
fun SharedPreferences.widgetState(band: WidgetSizeBand): WidgetState = WidgetState(
    progress = notificationProgress(),
    unit = getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML,
    amountsMl = widgetAmounts(band),
)
