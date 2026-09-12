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
 * The idle reminder: a nudge once nothing has been logged for a chosen stretch of time.
 *
 * Unlike everything in [Reminder], this one is not anchored to a clock time the user picked —
 * it is anchored to their last drink, so it moves every time they log one. That makes it a
 * single alarm of its own rather than another row on the Reminders screen, and it is why the
 * arithmetic lives here instead of in [nextIntervalFireTime].
 */

// ─── Preferences ──────────────────────────────────────────────────

/** When the user last *added* water, in epoch millis. 0 means never — see [lastIntakeAt]. */
const val PREF_LAST_INTAKE_AT = "last_intake_at"

/** How long a gap earns a nudge, in minutes. 0 is the feature switched off, and the default. */
const val PREF_IDLE_REMINDER_MINUTES = "idle_reminder_minutes"

/** When a reminder card was last put on screen, in epoch millis — read only by [shouldShowIdleReminder]. */
const val PREF_LAST_REMINDER_POST_AT = "last_reminder_post_at"

/**
 * A gap longer than a day is not a reminder setting any more, so it reads as off. The picker
 * can't produce one; an imported or hand-edited prefs file can.
 */
const val IDLE_REMINDER_MAX_MINUTES = 24 * 60

/** What the picker offers, in minutes. The leading 0 is the "Off" entry. */
val IDLE_REMINDER_CHOICES_MINUTES = listOf(0, 30, 60, 90, 120, 180, 240)

/**
 * A regular reminder and an idle one can land on the same minute — an every-hour reminder and a
 * 1-hour gap both fire on the hour once a day starts with nothing logged. Two cards saying the
 * same thing is a fault, so within this much of a regular card the idle one stands down.
 */
const val IDLE_REMINDER_DEDUPE_MS = 2 * 60 * 1000L

/** The chosen gap in minutes, or 0 when off. Sanitized on every read: prefs can be imported. */
fun SharedPreferences.idleReminderMinutes(): Int =
    getInt(PREF_IDLE_REMINDER_MINUTES, 0).takeIf { it in 1..IDLE_REMINDER_MAX_MINUTES } ?: 0

fun SharedPreferences.setIdleReminderMinutes(minutes: Int) {
    edit().putInt(
        PREF_IDLE_REMINDER_MINUTES,
        minutes.takeIf { it in 1..IDLE_REMINDER_MAX_MINUTES } ?: 0
    ).apply()
}

/**
 * When water was last added, or 0 if that is unknown. A stored value in the future is treated as
 * unknown rather than trusted — it can only come from a clock that has since been set back, or
 * from a backup taken on a device whose clock was wrong, and counting a gap from it would park
 * the reminder indefinitely.
 */
fun SharedPreferences.lastIntakeAt(nowMillis: Long = System.currentTimeMillis()): Long =
    getLong(PREF_LAST_INTAKE_AT, 0L).takeIf { it in 1..nowMillis } ?: 0L

/** Index of the stored gap in [IDLE_REMINDER_CHOICES_MINUTES], falling back to Off. */
fun idleReminderChoiceIndex(minutes: Int): Int =
    IDLE_REMINDER_CHOICES_MINUTES.indexOf(minutes).coerceAtLeast(0)

// ─── When it should fire ──────────────────────────────────────────

/**
 * The moment the countdown runs from: the last drink, or the start of today if that was longer
 * ago than this morning.
 *
 * The floor matters because the gap is meant to read as "you haven't had a drink all morning",
 * not "you haven't had one since last night" — without it, someone who last drank at 21:00 would
 * be nudged the instant their day opened, having done nothing wrong. The floor is deliberately
 * allowed to sit in the future: before the window opens, the countdown has not started yet.
 */
internal fun idleCountdownAnchor(now: Calendar, lastIntakeMillis: Long, windowStartHour: Int): Long =
    maxOf(lastIntakeMillis, now.atHour(windowStartHour).timeInMillis)

/**
 * When the idle alarm should next go off, or null when the feature is switched off.
 *
 * The result depends only on [now] and the anchor — never on how many times this has been called
 * — because the alarm is rescheduled from half a dozen places, and a rule that pushed the fire
 * time out by another gap on every call would let a user postpone the reminder forever just by
 * opening the Notifications screen. The answer is always the first anchor + n × gap strictly
 * after [now], so a late delivery or a reboot resumes the original cadence instead of drifting
 * off it.
 *
 * Fire times are held inside the reminder window, as repeating reminders are: a time before the
 * get-up hour waits for it, and a time past the go-to-bed hour waits for tomorrow's. Landing on
 * the get-up hour exactly is correct rather than premature — the anchor is floored to the same
 * hour, so a full gap is still measured from the moment the day opens.
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
    val anchor = idleCountdownAnchor(now, lastIntakeMillis, windowStartHour)

    // ceil of a non-positive elapsed is 0 or less — an anchor at or ahead of now still owes a
    // full gap, hence the floor of one step.
    val steps = ceil((now.timeInMillis - anchor).toDouble() / gapMs).toLong().coerceAtLeast(1L)
    var targetMs = anchor + steps * gapMs
    // An elapsed time that is an exact multiple of the gap lands the target on now itself.
    if (targetMs <= now.timeInMillis) targetMs += gapMs

    val target = (now.clone() as Calendar).apply { timeInMillis = targetMs }
    val hour = target.get(Calendar.HOUR_OF_DAY)
    return when {
        hour < windowStartHour -> target.atHour(windowStartHour)
        // The window end is an hour, not an instant: 20:45 is still inside a 20:00 bedtime — the
        // same reading nextIntervalFireTime and Reminder.firedOutsideWindow take.
        hour > windowEndHour ->
            target.atHour(windowStartHour).apply { add(Calendar.DAY_OF_MONTH, 1) }
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
 * Every reason not to put the idle card on screen. Separate from [shouldShowReminder] because
 * the two answer different questions, but they share the rules that are about notifications as a
 * whole — the master switch, the window, and what happens once the goal is met.
 *
 * The gap here is measured from the raw last drink, not from [idleCountdownAnchor]: the
 * scheduler floors the anchor to the start of the day so the *countdown* restarts each morning,
 * but by the time the alarm lands, what the card claims has to be literally true.
 */
// internal rather than private only so the tests can reach it — production callers go through
// postIdleReminderNotification, which applies it for you.
internal fun shouldShowIdleReminder(
    prefs: SharedPreferences,
    progress: NotificationProgress,
    nowMillis: Long = System.currentTimeMillis(),
    currentHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
): Boolean {
    if (!prefs.getBoolean("notifications_enabled", false)) return false

    val idleMinutes = prefs.idleReminderMinutes()
    if (idleMinutes <= 0) return false

    // Android can deliver an alarm hours late under battery management, so the window is checked
    // again on arrival and not only when the alarm was set.
    val (windowStart, windowEnd) = prefs.reminderWindow()
    if (currentHour !in windowStart..windowEnd) return false

    // A drink logged between the alarm being set and it being delivered answers the card before
    // it appears.
    val lastIntake = prefs.lastIntakeAt(nowMillis)
    if (lastIntake > 0 && nowMillis - lastIntake < idleMinutes * 60_000L) return false

    // A regular reminder has just said the same thing.
    val lastPost = prefs.getLong(PREF_LAST_REMINDER_POST_AT, 0L)
    if (lastPost in 1..nowMillis && nowMillis - lastPost < IDLE_REMINDER_DEDUPE_MS) return false

    return !(progress.goalReached && !prefs.showNotificationsAfterGoal())
}

// ─── The alarm ────────────────────────────────────────────────────

/**
 * The one idle alarm. There is only ever a single one — it is about the gap since the last
 * drink, and there is only one of those — so it needs no key and carries no extras:
 * [IdleReminderReceiver] reads everything back out of prefs.
 */
object IdleReminderScheduler {

    private const val REQUEST_CODE = 0x1D1E

    /**
     * Arms the alarm for its next fire time, or takes it away when there is nothing to fire.
     * Safe and cheap to call from anywhere at any time — see [nextIdleFireTime] for why calling
     * it repeatedly can't push the reminder away.
     */
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

    /** Deliberately not gated on the exact-alarm permission — taking an alarm away always works. */
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
        // Rearming first: this is a single alarm with no sibling to repair it the way the
        // reminder alarms repair each other, so the chain must not depend on what follows.
        IdleReminderScheduler.schedule(context)
        postIdleReminderNotification(context, context.waterPrefs())
    }
}

/**
 * Everything that has to happen after water is logged, beyond the logging itself: the countdown
 * restarts, and any standing idle card has just been answered.
 *
 * Kept out of [logIntake] so that stays free of Android plumbing and testable on the JVM.
 */
fun onIntakeLogged(context: Context) {
    cancelIdleReminderNotification(context)
    IdleReminderScheduler.schedule(context)
}
