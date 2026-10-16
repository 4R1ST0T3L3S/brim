package com.david.waterreminder

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

// What the widget decides before a RemoteViews is involved, kept apart from WaterWidgetProvider
// so all of it is reachable from a JVM test.

/** Width three "+100 ml" chips need, with their gaps and the card's padding. */
const val THREE_CHIP_MIN_WIDTH_DP = 224

// The row widget's widths decide only how many chips, never their type: that is measured per
// label (see fitChipTextSp). Each is where that many "+1500 ml" chips still fit at 11sp, so a
// chip is only added once its label can read: 24 padding, the 44 ring, 10 + ~70 for a "1650 ml"
// total, 12 before the chips — 160 — plus 47dp a chip. Measured on a placed 4×1 (320dp): "750
// ml" drew 59dp wide, and two chips 80dp each.

/** 160 + three chips + two gaps: a 4×1 on a four-column grid, and anything wider. */
const val ROW_THREE_CHIP_MIN_WIDTH_DP = 316

/** 160 + two chips + a gap. */
const val ROW_TWO_CHIP_MIN_WIDTH_DP = 262

/** 160 + one chip. Below it the total goes before the last chip does. */
const val ROW_TOTAL_MIN_WIDTH_DP = 208

/**
 * How much of the widget a size buys. One band rather than a function per setting, so they can't
 * turn over at different sizes — and it is all a redraw varies over, which lets two widgets in
 * the same band share one RemoteViews.
 */
enum class WidgetSizeBand(
    /**
     * The one-row widget's, drawn from `widget_water_row`: the card laid on its side, ring for
     * bar. Never reached by resizing — the two are separate widgets in the picker.
     */
    val isRow: Boolean,
    val chips: Int,
    /**
     * On the card, the size with every slot filled; see [chipTextSpFor]. On the row, where each
     * label is measured, only where fitting starts — the full size, at every band. The full size
     * must match `Widget.WaterReminder.WidgetChip`, which the layouts start from.
     */
    val chipTextSp: Float,
    /**
     * Whether a met goal is spelled out: beside the total on the card, in place of "of 1500 ml"
     * on the row. On the card the percentage stays on the goal line at every size: beside a total
     * that can read "2030 ml" and never yields, it was what got cut. The row has it in the ring.
     */
    val spellsOutGoalReached: Boolean,
    /** False only where the total and goal line would take the last chip's room. */
    val showsTotal: Boolean = true,
) {
    WIDE(isRow = false, chips = 3, chipTextSp = 13f, spellsOutGoalReached = true),

    /**
     * Smaller type rather than a label stripped of its unit: two chips on two cells are ~64dp
     * each, and "+1500 ml" wants 55dp at 13sp against 47 at 11 — the margin a long oz label
     * needs. Still sp, so a large font scale can outgrow it; overriding that would be worse.
     */
    COMPACT(isRow = false, chips = 2, chipTextSp = 11f, spellsOutGoalReached = false),

    /** 4×1 on a four-column grid of a ~360dp phone (~48dp chips), and 5×1. */
    ROW(isRow = true, chips = 3, chipTextSp = 13f, spellsOutGoalReached = true),

    /** 4×1 on a five-column grid. */
    ROW_COMPACT(isRow = true, chips = 2, chipTextSp = 13f, spellsOutGoalReached = true),

    /**
     * 3×1. The card's floor of two chips can't hold here: beside the ring and the total, two
     * would be ~24dp each. One chip that reads beats two that don't.
     */
    ROW_NARROW(isRow = true, chips = 1, chipTextSp = 13f, spellsOutGoalReached = true),

    /** 2×1: the ring and one chip. The ring still says how far along the day is. */
    ROW_TINY(
        isRow = true, chips = 1, chipTextSp = 13f, spellsOutGoalReached = false,
        showsTotal = false,
    ),
}

/**
 * A width test rather than a cell count: `OPTION_APPWIDGET_MIN_WIDTH` reports the dp a launcher
 * really hands over, and real cells are wider than the 70n − 30 the provider declares bounds in.
 * Which widget it is, [row] or card, is the provider's to say: the width only picks within it.
 */
fun widgetSizeBand(widthDp: Int, row: Boolean): WidgetSizeBand = when {
    !row -> if (widthDp >= THREE_CHIP_MIN_WIDTH_DP) WidgetSizeBand.WIDE else WidgetSizeBand.COMPACT
    widthDp >= ROW_THREE_CHIP_MIN_WIDTH_DP -> WidgetSizeBand.ROW
    widthDp >= ROW_TWO_CHIP_MIN_WIDTH_DP -> WidgetSizeBand.ROW_COMPACT
    widthDp >= ROW_TOTAL_MIN_WIDTH_DP -> WidgetSizeBand.ROW_NARROW
    else -> WidgetSizeBand.ROW_TINY
}

// What widget_water_row.xml puts beside its chips, for [rowChipWidthDp]: 12 + 12 padding, the 44
// ring and 12 before the chips; 10 before the total and goal line when they show; 8 between chips.
private const val ROW_FIXED_DP = 80
const val ROW_TEXT_MARGIN_DP = 10
private const val ROW_CHIP_GAP_DP = 8
const val ROW_TOTAL_TEXT_SP = 20f
const val ROW_GOAL_TEXT_SP = 12f

/** What a label leaves clear inside the chip's rounded ends. */
private const val CHIP_LABEL_SLACK_DP = 8

/**
 * Floor for a row chip's label. A label too long even here ellipsizes instead: below this the
 * chip is no easier to read for showing it whole.
 */
const val MIN_CHIP_TEXT_SP = 9f

/**
 * Each row chip's share of [widthDp] once the ring and the text block beside it — [textBlockDp],
 * its margin included, or 0 where the band hides it — have theirs. The chips split what is left
 * by weight, as `widget_water_row` lays them out.
 */
fun rowChipWidthDp(widthDp: Int, textBlockDp: Float, chips: Int): Float =
    (widthDp - ROW_FIXED_DP - textBlockDp - ROW_CHIP_GAP_DP * (chips - 1)) / chips

/**
 * [startSp], or smaller when the widest label — [labelWidthDp] at [startSp] — doesn't fit a chip
 * [chipWidthDp] wide: "+1250 ml" three to a 4×1 row. Text width scales with its size, so the fit
 * is one division rather than a search; rounded down to half an sp, never below
 * [MIN_CHIP_TEXT_SP]. Applied chip by chip, so only the long label shrinks.
 */
fun fitChipTextSp(startSp: Float, labelWidthDp: Float, chipWidthDp: Float): Float {
    val room = chipWidthDp - CHIP_LABEL_SLACK_DP
    if (labelWidthDp <= room) return startSp
    val fitted = kotlin.math.floor(startSp * room / labelWidthDp * 2f) / 2f
    return fitted.coerceAtLeast(MIN_CHIP_TEXT_SP)
}

/**
 * The **Quick log buttons** selection, trimmed to what [band] holds. Shared with the reminder card
 * because both answer the same question; a second picker would only be somewhere to disagree. The
 * trim is the widget's own — narrowing drops a chip without touching the setting.
 */
fun SharedPreferences.widgetAmounts(band: WidgetSizeBand): List<Int> =
    notificationAmounts().take(band.chips)

/**
 * A card band's smaller type is for when every slot is filled. A one-amount selection in a
 * two-chip band has a whole chip's width to spare, so it gets the full size back. The row's bands
 * all start at the full size anyway.
 */
fun WidgetSizeBand.chipTextSpFor(chipCount: Int): Float =
    if (chipCount < chips) WidgetSizeBand.WIDE.chipTextSp else chipTextSp

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
