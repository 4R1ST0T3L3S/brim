package com.david.waterreminder

import android.app.PendingIntent
import android.app.backup.BackupManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

/**
 * The home-screen widget: today's total against the goal, and the same quick-log buttons the
 * reminder card carries. An [AppWidgetProvider] is a BroadcastReceiver, so the taps come back
 * here rather than to [QuickLogReceiver], which exists to rebuild a notification.
 */
class WaterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetManager.drawWidgets(context, appWidgetIds)
    }

    /** Fired as the handles are dragged; without it a narrowed card keeps its three chips. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        appWidgetManager.drawWidgets(context, intArrayOf(appWidgetId))
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
        context.waterPrefs().logIntake(ml)
        // Off-screen tap, so the app's own listener won't fire — as in [QuickLogReceiver].
        BackupManager(context).dataChanged()
        // Crossing the goal is quiet here, as from a notification button — see [QuickLogReceiver].
        onIntakeLogged(context)
        updateWaterWidgets(context)
    }
}

private const val ACTION_WIDGET_LOG = "com.david.waterreminder.WIDGET_LOG"

/**
 * Redraws every placed widget — called wherever the day can change with no widget update of its
 * own to ride on: the app's prefs listener, a notification button, an alarm, a reboot.
 */
fun updateWaterWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context) ?: return
    manager.drawWidgets(
        context,
        manager.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java))
    )
}

/**
 * Draws [appWidgetIds], reading the day once for all of them — it is a property of the app, not
 * of any one widget. Two widgets differ only by band, so the card is built at most twice.
 */
private fun AppWidgetManager.drawWidgets(context: Context, appWidgetIds: IntArray) {
    if (appWidgetIds.isEmpty()) return
    val prefs = context.waterPrefs()
    val byBand = HashMap<WidgetSizeBand, RemoteViews>()
    appWidgetIds.forEach { id ->
        val band = widgetSizeBand(widgetWidthDp(id))
        updateAppWidget(id, byBand.getOrPut(band) { buildWidgetViews(context, prefs, band) })
    }
}

/**
 * `OPTION_APPWIDGET_MIN_WIDTH` is the portrait width, the one the chips have to fit. Falls back to
 * the declared `minWidth` on the first update after placing, when the bundle is still empty.
 */
private fun AppWidgetManager.widgetWidthDp(appWidgetId: Int): Int =
    getAppWidgetOptions(appWidgetId)
        ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        ?.takeIf { it > 0 }
        ?: DECLARED_MIN_WIDTH_DP

/** Mirrors `android:minWidth` in `widget_water_info.xml`. */
private const val DECLARED_MIN_WIDTH_DP = 250

/** The button slots in the layout, in the order [widgetAmounts] fills them. */
private val BUTTON_IDS = intArrayOf(R.id.widget_button_0, R.id.widget_button_1, R.id.widget_button_2)

private fun buildWidgetViews(
    context: Context,
    prefs: SharedPreferences,
    band: WidgetSizeBand,
): RemoteViews {
    val state = prefs.widgetState(band)
    val palette = context.widgetPalette(prefs)
    val views = RemoteViews(context.packageName, R.layout.widget_water)

    val text = ContextCompat.getColor(context, palette.textColorRes)
    val textVariant = ContextCompat.getColor(context, palette.textVariantColorRes)

    views.setInt(R.id.widget_root, "setBackgroundResource", palette.backgroundRes)
    views.setTextColor(R.id.widget_total, text)
    views.setTextColor(R.id.widget_goal_banner, textVariant)
    views.setTextColor(R.id.widget_goal, textVariant)

    views.setTextViewText(R.id.widget_total, state.progress.intakeMl.fmt(state.unit))

    // Capped at 100, so a met goal doesn't overshoot.
    views.setTextViewText(
        R.id.widget_goal,
        context.getString(
            R.string.widget_of_goal_percent,
            state.progress.goalMl.fmt(state.unit),
            state.progress.percent,
        )
    )

    val announceGoal = state.progress.goalReached && band.spellsOutGoalReached
    views.setViewVisibility(
        R.id.widget_goal_banner,
        if (announceGoal) View.VISIBLE else View.GONE
    )
    views.setProgressBar(R.id.widget_progress, 100, state.progress.percent, false)

    BUTTON_IDS.forEachIndexed { index, id ->
        val ml = state.amountsMl.getOrNull(index)
        if (ml == null) {
            views.setViewVisibility(id, View.GONE)
        } else {
            views.setViewVisibility(id, View.VISIBLE)
            views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, band.chipTextSp)
            views.setTextViewText(id, context.getString(R.string.widget_action_add, ml.fmt(state.unit)))
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
