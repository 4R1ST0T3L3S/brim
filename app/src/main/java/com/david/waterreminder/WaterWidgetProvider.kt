package com.david.waterreminder

import android.app.PendingIntent
import android.app.backup.BackupManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

/**
 * The home-screen widget: today's total against the goal, and the same quick-log buttons the
 * reminder card carries. An [AppWidgetProvider] is a BroadcastReceiver, so the taps come back
 * here rather than to [QuickLogReceiver], which exists to rebuild a notification — from either
 * widget: every log button targets this class, so [WaterRowWidgetProvider] only draws.
 */
open class WaterWidgetProvider : AppWidgetProvider() {

    /** Which of the two widgets this is; see [WidgetSizeBand.isRow]. */
    protected open val isRow: Boolean get() = false

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetManager.drawWidgets(context, appWidgetIds, isRow)
    }

    /** Fired as the handles are dragged; without it a narrowed card keeps its three chips. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        appWidgetManager.drawWidgets(context, intArrayOf(appWidgetId), isRow)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WIDGET_LOG) {
            logFromWidget(context, intent.getIntExtra(EXTRA_LOG_ML, 0))
        }
        super.onReceive(context, intent)
    }

    /** On the main thread for the reason [QuickLogReceiver] is. */
    private fun logFromWidget(context: Context, ml: Int) {
        if (ml <= 0) return
        val prefs = context.waterPrefs()
        prefs.logIntake(ml, transientStore = context.transientPrefs())
        // Off-screen tap, so the app's own listener won't fire — as in [QuickLogReceiver].
        BackupManager(context).dataChanged()
        // Crossing the goal is quiet here, as from a notification button — see [QuickLogReceiver].
        onIntakeLogged(context)
        syncReminderCards(context, prefs, drink = true)
        updateWaterWidgets(context)
    }
}

/**
 * The one-row widget (issue #38): a separate entry in the picker rather than the card resized
 * down, so neither has to guess from a height a launcher may report loosely which it should be.
 */
class WaterRowWidgetProvider : WaterWidgetProvider() {
    override val isRow: Boolean get() = true
}

private const val ACTION_WIDGET_LOG = "com.david.waterreminder.WIDGET_LOG"

/**
 * Redraws every placed widget of both kinds — called wherever the day can change with no widget
 * update of its own to ride on: the app's prefs listener, a notification button, an alarm, a
 * reboot. `getAppWidgetIds` matches the component exactly, so the subclass's widgets aren't in
 * the card's list.
 */
fun updateWaterWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context) ?: return
    manager.drawWidgets(
        context,
        manager.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java)),
        row = false,
    )
    manager.drawWidgets(
        context,
        manager.getAppWidgetIds(ComponentName(context, WaterRowWidgetProvider::class.java)),
        row = true,
    )
}

/**
 * Draws [appWidgetIds]. Two widgets differ only by band and chip type sizes, so each pair's
 * views are built once.
 */
private fun AppWidgetManager.drawWidgets(context: Context, appWidgetIds: IntArray, row: Boolean) {
    if (appWidgetIds.isEmpty()) return
    val prefs = context.waterPrefs()
    val built = HashMap<Pair<WidgetSizeBand, List<Float>>, RemoteViews>()
    appWidgetIds.forEach { id ->
        val widthDp = widgetWidthDp(id, row)
        val band = widgetSizeBand(widthDp, row)
        val state = prefs.widgetState(band)
        val chipTextSps = context.chipTextSps(band, state, widthDp)
        updateAppWidget(
            id,
            built.getOrPut(band to chipTextSps) {
                buildWidgetViews(context, prefs, band, state, chipTextSps)
            }
        )
    }
}

/**
 * One size per chip: the band's, shrunk on the row only for a label too long for its chip —
 * measured, since whether "+1250 ml" fits three to a 4×1 depends on the font and on the total
 * beside it. Per chip rather than one for all, so "+100 ml" keeps its size beside a "+1250 ml"
 * that can't. The card's bands were sized for the longest label it allows, so it keeps theirs.
 */
private fun Context.chipTextSps(
    band: WidgetSizeBand,
    state: WidgetState,
    widthDp: Int,
): List<Float> {
    val startSp = band.chipTextSpFor(state.amountsMl.size)
    if (!band.isRow) return state.amountsMl.map { startSp }
    val textBlockDp = if (band.showsTotal) {
        ROW_TEXT_MARGIN_DP + maxOf(
            textWidthDp(state.progress.intakeMl.fmt(state.unit), ROW_TOTAL_TEXT_SP, bold = true),
            textWidthDp(
                rowGoalText(this, state, band),
                ROW_GOAL_TEXT_SP,
                bold = rowAnnouncesGoal(state, band),
            ),
        )
    } else {
        0f
    }
    val chipWidthDp = rowChipWidthDp(widthDp, textBlockDp, state.amountsMl.size)
    return state.amountsMl.map { ml ->
        fitChipTextSp(
            startSp,
            labelWidthDp = textWidthDp(chipLabel(this, ml, state.unit), startSp, bold = true),
            chipWidthDp = chipWidthDp,
        )
    }
}

/**
 * [text]'s width at [sp], in dp. Measured in the app's process with the system face, the one the
 * launcher draws the widget in; through the sp conversion, so the user's font scale counts.
 */
private fun Context.textWidthDp(text: CharSequence, sp: Float, bold: Boolean): Float {
    val metrics = resources.displayMetrics
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    return paint.measureText(text, 0, text.length) / metrics.density
}

private fun chipLabel(context: Context, ml: Int, unit: String): String =
    context.getString(R.string.widget_action_add, ml.fmt(unit))

/**
 * `OPTION_APPWIDGET_MIN_WIDTH` is the portrait width, the one the chips have to fit. Falls back to
 * a guess on the first update after placing, when the bundle is still empty.
 */
private fun AppWidgetManager.widgetWidthDp(appWidgetId: Int, row: Boolean): Int =
    getAppWidgetOptions(appWidgetId)
        ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        ?.takeIf { it > 0 }
        ?: if (row) PLACED_ROW_WIDTH_GUESS_DP else DECLARED_MIN_WIDTH_DP

/** Mirrors `android:minWidth` in `widget_water_info.xml`. */
private const val DECLARED_MIN_WIDTH_DP = 250

/**
 * A 4×1 as a 360dp phone places it, rather than the row's declared 250: that reads as
 * ROW_NARROW, and would flash one chip until the real width arrives.
 */
private const val PLACED_ROW_WIDTH_GUESS_DP = 320

/** The button slots in the layout, in the order [widgetAmounts] fills them. */
private val BUTTON_IDS = intArrayOf(R.id.widget_button_0, R.id.widget_button_1, R.id.widget_button_2)

private fun buildWidgetViews(
    context: Context,
    prefs: SharedPreferences,
    band: WidgetSizeBand,
    state: WidgetState,
    chipTextSps: List<Float>,
): RemoteViews {
    val palette = context.widgetPalette(prefs)
    val views = RemoteViews(
        context.packageName,
        if (band.isRow) R.layout.widget_water_row else R.layout.widget_water
    )

    val text = ContextCompat.getColor(context, palette.textColorRes)
    val textVariant = ContextCompat.getColor(context, palette.textVariantColorRes)

    views.setInt(R.id.widget_root, "setBackgroundResource", palette.backgroundRes)
    views.setTextColor(R.id.widget_total, text)
    views.setTextColor(R.id.widget_goal, textVariant)

    views.setTextViewText(R.id.widget_total, state.progress.intakeMl.fmt(state.unit))

    if (band.isRow) {
        drawRowGoal(context, views, state, band, text)
    } else {
        val announceGoal = state.progress.goalReached && band.spellsOutGoalReached
        drawCardGoal(context, views, state, announceGoal, textVariant)
    }
    // Capped at 100, so a met goal doesn't overshoot.
    views.setProgressBar(R.id.widget_progress, 100, state.progress.percent, false)

    BUTTON_IDS.forEachIndexed { index, id ->
        val ml = state.amountsMl.getOrNull(index)
        if (ml == null) {
            views.setViewVisibility(id, View.GONE)
        } else {
            views.setViewVisibility(id, View.VISIBLE)
            views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, chipTextSps[index])
            views.setTextViewText(id, chipLabel(context, ml, state.unit))
            views.setOnClickPendingIntent(id, logPendingIntent(context, ml))
        }
    }
    // An empty row would still take its height and leave the widget looking broken-off.
    views.setViewVisibility(
        R.id.widget_buttons,
        if (state.amountsMl.isEmpty()) View.GONE else View.VISIBLE
    )

    views.setOnClickPendingIntent(R.id.widget_header, openAppPendingIntent(context))
    return views
}

/** The card's goal line carries the percentage; a met goal adds the banner beside the total. */
private fun drawCardGoal(
    context: Context,
    views: RemoteViews,
    state: WidgetState,
    announceGoal: Boolean,
    textVariant: Int,
) {
    views.setTextColor(R.id.widget_goal_banner, textVariant)
    views.setTextViewText(
        R.id.widget_goal,
        context.getString(
            R.string.widget_of_goal_percent,
            state.progress.goalMl.fmt(state.unit),
            state.progress.percent,
        )
    )
    views.setViewVisibility(
        R.id.widget_goal_banner,
        if (announceGoal) View.VISIBLE else View.GONE
    )
}

/**
 * The row's percentage is in the ring, so its goal line is just the goal — or, once met, says so
 * in bold where the goal was: the row has no room beside the total for the card's banner.
 */
private fun drawRowGoal(
    context: Context,
    views: RemoteViews,
    state: WidgetState,
    band: WidgetSizeBand,
    text: Int,
) {
    views.setTextColor(R.id.widget_percent, text)
    views.setTextViewText(
        R.id.widget_percent,
        context.getString(R.string.widget_percent, state.progress.percent)
    )
    views.setViewVisibility(R.id.widget_text, if (band.showsTotal) View.VISIBLE else View.GONE)
    views.setTextViewText(R.id.widget_goal, rowGoalText(context, state, band))
}

/** Shared with [chipTextSps], which has to know how wide the line will be. */
private fun rowAnnouncesGoal(state: WidgetState, band: WidgetSizeBand): Boolean =
    state.progress.goalReached && band.spellsOutGoalReached

private fun rowGoalText(context: Context, state: WidgetState, band: WidgetSizeBand): CharSequence =
    if (rowAnnouncesGoal(state, band)) {
        SpannableString(context.getString(R.string.widget_goal_reached)).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    } else {
        context.getString(R.string.widget_of_goal, state.progress.goalMl.fmt(state.unit))
    }

/** One per amount — see [quickLogPendingIntent] for why both the URI and the code are set. */
private fun logPendingIntent(context: Context, ml: Int): PendingIntent {
    val intent = Intent(context, WaterWidgetProvider::class.java).apply {
        action = ACTION_WIDGET_LOG
        data = Uri.parse("waterreminder://widget/log/$ml")
        putExtra(EXTRA_LOG_ML, ml)
    }
    return PendingIntent.getBroadcast(
        context, ml, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
