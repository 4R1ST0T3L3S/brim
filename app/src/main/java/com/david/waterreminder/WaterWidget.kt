package com.david.waterreminder

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/**
 * Everything about the home-screen widget that can be decided without a RemoteViews in hand —
 * which amounts get a button, which colours it is drawn in, and what the numbers on it are.
 * Kept apart from [WaterWidgetProvider] so all of it is reachable from a JVM test.
 */

/** The width at which three chips stop fitting. Derived in [widgetSizeBand]. */
const val THREE_CHIP_MIN_WIDTH_DP = 224

/**
 * How much of the card a given width buys. One band carrying every setting rather than a function
 * per setting, so they can't turn over at different widths — and it is the only thing a redraw
 * varies over, which is what lets two widgets in the same band share one RemoteViews.
 */
enum class WidgetSizeBand(
    /** Chips to draw, if the user has picked that many. */
    val chips: Int,
    /** Chip text size in sp. */
    val chipTextSp: Float,
    /**
     * Whether a met goal is announced beside the total. The percentage is on the goal line at
     * every size — beside a total that can read "2030 ml" and never yields, it was what got cut —
     * which leaves the header for this, on the cards with room for it.
     */
    val spellsOutGoalReached: Boolean,
) {
    /** Room for three chips at the size the layout asks for. */
    WIDE(chips = 3, chipTextSp = 13f, spellsOutGoalReached = true),

    /**
     * A narrow card: one chip fewer, and smaller type rather than a label stripped of its unit.
     * Two chips on two cells are about 64dp each, where "+1500 ml" wants 55dp at 13sp and 47 at
     * 11 — the margin a long oz label needs. Still sp, so a large font scale can outgrow it;
     * overriding an accessibility setting would be the worse answer.
     */
    COMPACT(chips = 2, chipTextSp = 11f, spellsOutGoalReached = false),
}

/**
 * The band a card [widthDp] across falls in. Three "+100 ml" chips want 60dp each plus gaps and
 * padding — [THREE_CHIP_MIN_WIDTH_DP] in all. A width test rather than a cell count, because
 * `OPTION_APPWIDGET_MIN_WIDTH` reports the dp a launcher really hands over and real cells are
 * wider than the 70n − 30 the provider declares its bounds in.
 */
fun widgetSizeBand(widthDp: Int): WidgetSizeBand =
    if (widthDp >= THREE_CHIP_MIN_WIDTH_DP) WidgetSizeBand.WIDE else WidgetSizeBand.COMPACT

/**
 * The amounts the widget puts buttons on — the **Quick log buttons** selection, trimmed to what a
 * card in [band] holds. Shared with the reminder card because both answer the same question, and
 * a second picker for one decision would only be somewhere for them to disagree. The trim is the
 * widget's own: narrowing drops a chip without touching the setting, so widening brings it back.
 */
fun SharedPreferences.widgetAmounts(band: WidgetSizeBand): List<Int> =
    notificationAmounts().take(band.chips)

/**
 * The widget's colours: each theme's own page background and the text that sits on it. A widget is
 * drawn by the launcher and can't read theme attributes, so these name concrete resources and
 * [widgetPaletteFor] picks the set. No chip colour here — `quickAddBackground` is set once on the
 * base theme and no tinted theme overrides it, so the chip is fixed in its style.
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
 * The palette for a stored [themeMode]. "Follow system" — and any unrecognised value, since prefs
 * can arrive from an import — resolves against [systemIsNight], which is the *launcher's* night
 * mode: that is the surface the widget sits on.
 */
fun widgetPaletteFor(themeMode: Int, systemIsNight: Boolean): WidgetPalette = when (themeMode) {
    THEME_MODE_BLUE_LIGHT -> WidgetPalette.BLUE_LIGHT
    THEME_MODE_BLUE_DARK -> WidgetPalette.BLUE_DARK
    AppCompatDelegate.MODE_NIGHT_NO -> WidgetPalette.LIGHT
    AppCompatDelegate.MODE_NIGHT_YES -> WidgetPalette.DARK
    else -> if (systemIsNight) WidgetPalette.DARK else WidgetPalette.LIGHT
}

/** Whether the configuration the widget is being drawn under is in night mode. */
private fun Context.isNightMode(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

fun Context.widgetPalette(prefs: SharedPreferences): WidgetPalette =
    widgetPaletteFor(prefs.getInt(PREF_THEME_MODE, DEFAULT_THEME_MODE), isNightMode())

/** Everything the widget draws, read in one pass so its numbers can't disagree with each other. */
data class WidgetState(
    val progress: NotificationProgress,
    val unit: String,
    val amountsMl: List<Int>,
    val palette: WidgetPalette,
)

/**
 * The widget's reading of the day. Through [notificationProgress] rather than [dayTotal] for the
 * reason the reminder card is: nothing has rolled the stored day over out here, so yesterday's
 * total must not be drawn as today's.
 */
fun SharedPreferences.widgetState(palette: WidgetPalette, band: WidgetSizeBand): WidgetState =
    WidgetState(
        progress = notificationProgress(),
        unit = getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML,
        amountsMl = widgetAmounts(band),
        palette = palette,
    )
