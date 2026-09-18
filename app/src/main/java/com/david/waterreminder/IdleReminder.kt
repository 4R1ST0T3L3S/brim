package com.david.waterreminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import java.util.Calendar
import kotlin.math.ceil

/**
 * The idle reminder: a nudge once nothing has been logged for a chosen stretch of time. Anchored
 * to the last drink rather than to a clock time, so it is a single alarm of its own rather than
 * another row on the Reminders screen.
 */

// ─── Preferences ──────────────────────────────────────────────────

/** When the user last *added* water, in epoch millis. 0 means never. */
const val PREF_LAST_INTAKE_AT = "last_intake_at"

/** How long a gap earns a nudge, in minutes. 0 is off, and the default. */
const val PREF_IDLE_REMINDER_MINUTES = "idle_reminder_minutes"

/** When a reminder card was last put on screen, in epoch millis. Lives in [transientPrefs]. */
const val PREF_LAST_REMINDER_POST_AT = "last_reminder_post_at"

/**
 * Bookkeeping about this device and this moment, never the user's data — a separate file so
 * [buildBackupJson], which serialises the main store wholesale, can't see it.
 */
fun Context.transientPrefs(): SharedPreferences =
    getSharedPreferences("transient_prefs", Context.MODE_PRIVATE)

/** A time in the future reads as 0: the clock was set back. */
fun SharedPreferences.lastReminderPostAt(nowMillis: Long = System.currentTimeMillis()): Long =
    getLong(PREF_LAST_REMINDER_POST_AT, 0L).takeIf { it in 1..nowMillis } ?: 0L

/** Past this, the setting reads as off. The picker can't produce it; an imported file can. */
const val IDLE_REMINDER_MAX_MINUTES = 24 * 60

/** What the picker offers, in minutes. The leading 0 is the "Off" entry. */
val IDLE_REMINDER_CHOICES_MINUTES = listOf(0, 30, 60, 90, 120, 180, 240)

/**
 * How soon after a reminder card the idle one stands down. The two collide on the same minute all
 * day once nothing has been logged, and the reminder the user configured is the one that wins.
 */
const val IDLE_REMINDER_DEDUPE_MS = 2 * 60 * 1000L

/** The chosen gap, or 0 when off. Sanitized on every read: prefs can be imported. */
fun SharedPreferences.idleReminderMinutes(): Int =
    getInt(PREF_IDLE_REMINDER_MINUTES, 0).takeIf { it in 1..IDLE_REMINDER_MAX_MINUTES } ?: 0

fun SharedPreferences.setIdleReminderMinutes(minutes: Int) {
    edit().putInt(
        PREF_IDLE_REMINDER_MINUTES,
        minutes.takeIf { it in 1..IDLE_REMINDER_MAX_MINUTES } ?: 0
    ).apply()
}

/**
 * When water was last added, or 0 if unknown — including a time in the future, which means the
 * clock was set back and would otherwise park the reminder indefinitely.
 */
fun SharedPreferences.lastIntakeAt(nowMillis: Long = System.currentTimeMillis()): Long =
    getLong(PREF_LAST_INTAKE_AT, 0L).takeIf { it in 1..nowMillis } ?: 0L

/**
 * Minutes since the last drink, rounded down to the half-hour — every gap the picker offers is a
 * multiple of 30, so an on-time delivery is unchanged by this, and a late one reads as "2 hours"
 * rather than "125 minutes". 0 when there is nothing to measure from.
 */
fun idleElapsedMinutes(nowMillis: Long, lastIntakeMillis: Long): Int {
    if (lastIntakeMillis <= 0 || lastIntakeMillis > nowMillis) return 0
    val minutes = (nowMillis - lastIntakeMillis) / 60_000L
    return (minutes - minutes % 30).toInt()
}

/** Whole hours and the minutes left over — what [durationLabel] is built from. */
internal fun durationHoursAndMinutes(minutes: Int): Pair<Int, Int> = minutes / 60 to minutes % 60

/**
 * "45 minutes", "1 hour", "1 hour 30 minutes", "5 hours 30 minutes". Anything past an hour leads
 * with the hours: half-hour totals are the common case here, and reporting one as "330 minutes"
 * is arithmetic, not language. Shared by the picker and the card so the setting and the
 * notification say it the same way.
 */
fun Context.durationLabel(minutes: Int): String {
    val (hours, rest) = durationHoursAndMinutes(minutes)
    val hoursLabel = resources.getQuantityString(R.plurals.idle_reminder_hours, hours, hours)
    val minutesLabel = resources.getQuantityString(R.plurals.idle_reminder_minutes, rest, rest)
    return when {
        hours == 0 -> minutesLabel
        rest == 0 -> hoursLabel
        else -> getString(R.string.duration_hours_minutes, hoursLabel, minutesLabel)
    }
}

/** Index of the stored gap in [IDLE_REMINDER_CHOICES_MINUTES], falling back to Off. */
fun idleReminderChoiceIndex(minutes: Int): Int =
    IDLE_REMINDER_CHOICES_MINUTES.indexOf(minutes).coerceAtLeast(0)

// ─── When it should fire ──────────────────────────────────────────

/**
 * Where the countdown runs from: the last drink, but only when it happened today. Null otherwise,
 * which stops the clock entirely until the day's first glass — a night's sleep is not a gap
 * between drinks, and counting through it would have the app open every morning with a reproach.
 */
internal fun idleCountdownAnchor(now: Calendar, lastIntakeMillis: Long): Long? =
    lastIntakeMillis.takeIf { it in now.atHour(0).timeInMillis..now.timeInMillis }

/**
 * When the idle alarm should next go off. Null when the feature is off, when nothing has been
 * logged today yet, or when the next fire would land past bedtime — the countdown does not
 * survive the night, and tomorrow's starts from tomorrow's first drink.
 *
 * Always the first anchor + n × gap strictly after [now], so the answer never depends on how
 * often this is called — the alarm is rescheduled from half a dozen places, and a rule that
 * pushed the fire time out each time would let the user postpone it forever by opening Settings.
 */
fun nextIdleFireTime(
    now: Calendar,
    lastIntakeMillis: Long,
    idleMinutes: Int,
    windowStartHour: Int,
    windowEndHour: Int,
): Calendar? {
    if (idleMinutes <= 0) return null

    val gapMs = idleMinutes * 60_000L
    val anchor = idleCountdownAnchor(now, lastIntakeMillis) ?: return null

    // An anchor at or ahead of now still owes a full gap, hence the floor of one step.
    val steps = ceil((now.timeInMillis - anchor).toDouble() / gapMs).toLong().coerceAtLeast(1L)
    var targetMs = anchor + steps * gapMs
    if (targetMs <= now.timeInMillis) targetMs += gapMs

    val tomorrow = now.atHour(0).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
    val target = (now.clone() as Calendar).apply { timeInMillis = targetMs }
    val hour = target.get(Calendar.HOUR_OF_DAY)
    return when {
        targetMs >= tomorrow -> null
        hour > windowEndHour -> null
        // A drink before the window opened still counts; the nudge for it waits for the hour.
        hour < windowStartHour -> target.atHour(windowStartHour)
        else -> target
    }
}

/** This calendar's own day at [hour]:00, as a copy. */
private fun Calendar.atHour(hour: Int): Calendar = (clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

// ─── Whether it should be shown ───────────────────────────────────

/**
 * The rules about idle cards in general — everything except whether the gap has run out, which is
 * what separates posting a card from rebuilding one already up. The window is rechecked here
 * because Android can deliver an alarm hours late.
 */
internal fun idleCardAllowed(
    prefs: SharedPreferences,
    progress: NotificationProgress,
    now: Calendar = Calendar.getInstance(),
): Boolean {
    if (!prefs.getBoolean("notifications_enabled", false)) return false
    if (prefs.idleReminderMinutes() <= 0) return false

    val (windowStart, windowEnd) = prefs.reminderWindow()
    if (now.get(Calendar.HOUR_OF_DAY) !in windowStart..windowEnd) return false

    return !(progress.goalReached && !prefs.showNotificationsAfterGoal())
}

/**
 * Every reason not to put the idle card on screen.
 *
 * [lastReminderPostAt] is when a reminder card was really posted, not when one was scheduled — a
 * reminder due but never delivered must not silence the nudge that covers exactly that.
 */
// internal only so the tests can reach it; production goes through postIdleReminderNotification.
internal fun shouldShowIdleReminder(
    prefs: SharedPreferences,
    progress: NotificationProgress,
    now: Calendar = Calendar.getInstance(),
    lastReminderPostAt: Long = 0L,
): Boolean {
    if (!idleCardAllowed(prefs, progress, now)) return false

    // Nothing logged today: the countdown has not started, so there is no gap to report. Also
    // catches a drink logged between the alarm being set and delivered, which answers the card
    // before it appears.
    val anchor = idleCountdownAnchor(now, prefs.lastIntakeAt(now.timeInMillis)) ?: return false
    if (now.timeInMillis - anchor < prefs.idleReminderMinutes() * 60_000L) return false

    // A reminder card has just said the same thing, and it takes precedence.
    return now.timeInMillis - lastReminderPostAt >= IDLE_REMINDER_DEDUPE_MS
}

// ─── The alarm ────────────────────────────────────────────────────

/** The one idle alarm. No key, no extras: [IdleReminderReceiver] reads everything from prefs. */
object IdleReminderScheduler {

    private const val REQUEST_CODE = 0x1D1E

    /** Arms the alarm, or takes it away when there is nothing to fire. Safe to call anywhere. */
    fun schedule(context: Context) {
        val prefs = context.waterPrefs()
        val next = if (prefs.getBoolean("notifications_enabled", false)) {
            val (windowStart, windowEnd) = prefs.reminderWindow()
            nextIdleFireTime(
                now = Calendar.getInstance(),
                lastIntakeMillis = prefs.lastIntakeAt(),
                idleMinutes = prefs.idleReminderMinutes(),
                windowStartHour = windowStart,
                windowEndHour = windowEnd,
            )
        } else null

        if (next == null) {
            cancel(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.timeInMillis,
            buildPendingIntent(context)
        )
    }

    /** Not gated on the exact-alarm permission — taking an alarm away always works. */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, IdleReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

class IdleReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Rearming first: no sibling alarm repairs this one.
        IdleReminderScheduler.schedule(context)
        postIdleReminderNotification(context, context.waterPrefs())
        // Another waking alarm, so it catches a rolled-over day for the same reason
        // [ReminderReceiver] does.
        updateWaterWidgets(context)
    }
}

/**
 * What follows a drink, beyond the logging: the countdown restarts and a standing card has been
 * answered. Kept out of [logIntake] so that stays free of Android plumbing. The early return
 * keeps two binder calls off every add-button tap while the feature is off, which is the default.
 */
fun onIntakeLogged(context: Context) {
    if (context.waterPrefs().idleReminderMinutes() <= 0) return
    cancelIdleReminderNotification(context)
    IdleReminderScheduler.schedule(context)
}

/** Every alarm the app owns, re-armed. Both calls check the notifications toggle themselves. */
fun rescheduleAllAlarms(context: Context) {
    ReminderScheduler.rescheduleAll(context)
    IdleReminderScheduler.schedule(context)
}
