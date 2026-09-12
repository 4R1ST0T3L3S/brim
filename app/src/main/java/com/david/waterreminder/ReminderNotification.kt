package com.david.waterreminder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.util.Calendar
import kotlin.random.Random

/**
 * The reminder card: when it may be shown, what goes on it, and the quick-log buttons that let a
 * drink be logged without opening the app. Shared by the alarm that first posts it and the button
 * tap that rebuilds it, which is why it doesn't live in either receiver.
 */

const val REMINDER_CHANNEL_ID = "water_reminder_channel"

/** Extras a quick-log button carries back to [QuickLogReceiver]. */
const val EXTRA_LOG_ML = "log_ml"
const val EXTRA_LOG_REMINDER_TIME = "log_reminder_time"

/**
 * Stands in for a reminder time on the idle card, which has none — it is about a gap, not a
 * clock. Every key on a real reminder is an "HH:MM" label, so this can never name one, and it
 * gives the idle card a notification id and quick-log buttons of its own for free.
 */
const val IDLE_REMINDER_KEY = "idle"

/**
 * One notification per reminder time, so a rebuild after a quick-log tap replaces the card the
 * button was on rather than stacking a second one beside it.
 */
private fun reminderNotificationId(reminderTime: String): Int = reminderTime.hashCode()

/** The reminder a notification or alarm is about, or null once the user has deleted it. */
fun SharedPreferences.reminderAt(time: String): Reminder? =
    Reminder.parseAll(getStringSet(REMINDERS_KEY, emptySet())).firstOrNull { it.time == time }

/**
 * Today's running total as a notification should report it.
 *
 * Unlike [dayTotal] this can't assume the stored day is current: every screen calls
 * [rollOverDayIfNeeded] on the way in, but an alarm can fire against a day nobody has rolled
 * over yet, and yesterday's total must not be shown as today's.
 */
fun SharedPreferences.todayIntake(): Int =
    (if (getString("date", "") == LocalDate.now().toString()) getInt("intake_ml", 0) else 0)
        .asDayIntake()

/** Today's total and the goal the notification reports against. */
data class NotificationProgress(val intakeMl: Int, val goalMl: Int) {
    val goalReached: Boolean get() = goalMl > 0 && intakeMl >= goalMl

    /** Whole percent of the goal, floored and capped: the card never claims more than 100%. */
    val percent: Int get() = if (goalMl <= 0) 0 else (intakeMl * 100 / goalMl).coerceAtMost(100)

    /** What's still owed on the goal, in ml. Zero once it's met, never negative. */
    val remainingMl: Int get() = (goalMl - intakeMl).coerceAtLeast(0)
}

fun SharedPreferences.notificationProgress(): NotificationProgress {
    val personalGoal = getInt("personal_goal_ml", 0)
    return NotificationProgress(
        intakeMl = todayIntake(),
        goalMl = if (personalGoal > 0) personalGoal else DEFAULT_DAILY_GOAL_ML,
    )
}

/**
 * Posts the card for an alarm firing, or takes it away if it no longer belongs on screen.
 * Returns whether it was posted — the caller needs that to decide a one-time reminder's fate.
 *
 * Always alerts. An interval reminder posts to one id all day, so suppressing the alert on a
 * card still sitting in the shade would mute every reminder after the first.
 */
fun postReminderNotification(
    context: Context,
    prefs: SharedPreferences,
    reminder: Reminder,
): Boolean {
    val posted = updateReminderNotification(context, prefs, reminder, reminder.time, onlyAlertOnce = false)
    if (posted) {
        // The idle card says the same thing in other words, so it doesn't get to sit beside this
        // one. The timestamp keeps an idle alarm landing on the same minute quiet as well —
        // which of the two is delivered first isn't ours to decide. See shouldShowIdleReminder.
        prefs.edit().putLong(PREF_LAST_REMINDER_POST_AT, System.currentTimeMillis()).apply()
        cancelIdleReminderNotification(context)
    }
    return posted
}

/**
 * Posts the idle card, or takes it away if the gap it was about has since been answered.
 * Returns whether it was posted.
 *
 * Always alerts, for the same reason [postReminderNotification] does: the card is reposted to
 * one id every time the gap elapses again, so alerting once would mute every nudge after the
 * first.
 */
fun postIdleReminderNotification(context: Context, prefs: SharedPreferences): Boolean {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val progress = prefs.notificationProgress()

    if (!shouldShowIdleReminder(prefs, progress)) {
        cancelIdleReminderNotification(context)
        return false
    }

    notificationManager.notify(
        reminderNotificationId(IDLE_REMINDER_KEY),
        buildReminderNotification(
            context, prefs, IDLE_REMINDER_KEY, progress, onlyAlertOnce = false,
            encouragementOverride = context.getString(R.string.notification_body_idle),
        )
    )
    return true
}

/** Takes the idle card away — because water was logged, or because a reminder card replaced it. */
fun cancelIdleReminderNotification(context: Context) {
    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        .cancel(reminderNotificationId(IDLE_REMINDER_KEY))
}

/**
 * Rebuilds the card a quick-log button was on so it shows the new total and can be tapped again,
 * or takes it away when that tap is what turned it off. Never re-alerts: the user is looking at
 * the card, and on a watch a second buzz for their own tap reads like a fault.
 */
fun refreshReminderNotification(context: Context, prefs: SharedPreferences, reminderTime: String) {
    updateReminderNotification(context, prefs, prefs.reminderAt(reminderTime), reminderTime, onlyAlertOnce = true)
}

/**
 * The single place a reminder card is put on screen or taken off it, so the decision and the
 * card can't disagree — [notificationProgress] is read once and drives both.
 */
private fun updateReminderNotification(
    context: Context,
    prefs: SharedPreferences,
    reminder: Reminder?,
    reminderTime: String,
    onlyAlertOnce: Boolean,
): Boolean {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notificationId = reminderNotificationId(reminderTime)
    val progress = prefs.notificationProgress()

    if (reminder == null || !shouldShowReminder(prefs, reminder, progress)) {
        notificationManager.cancel(notificationId)
        return false
    }

    notificationManager.notify(
        notificationId,
        buildReminderNotification(context, prefs, reminderTime, progress, onlyAlertOnce)
    )
    return true
}

/**
 * Every reason not to put this reminder on screen, in one place — because a quick-log tap
 * rebuilds the card and has to answer the same question the alarm did. Rebuilding without
 * asking would resurrect a reminder the user had just switched off, push one outside the
 * reminder window, or keep the card alive through the very tap that reached the goal, for
 * someone who asked for them to stop there.
 */
// internal rather than private only so the tests can reach it — every production caller goes
// through postReminderNotification/refreshReminderNotification, which apply it for you.
internal fun shouldShowReminder(
    prefs: SharedPreferences,
    reminder: Reminder,
    progress: NotificationProgress,
    currentHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
): Boolean {
    if (!prefs.getBoolean("notifications_enabled", false)) return false
    // A reminder switched off individually. Its alarm is cancelled on the way out, but a card
    // already in the shade keeps its buttons — tapping one must not bring it back.
    if (!reminder.enabled) return false

    val (windowStart, windowEnd) = prefs.reminderWindow()
    if (reminder.firedOutsideWindow(currentHour, windowStart, windowEnd)) return false

    return !(progress.goalReached && !prefs.showNotificationsAfterGoal())
}

/**
 * [encouragementOverride] is how the idle card gets its own voice while sharing everything else.
 * It takes the place of the random encouragement in the body and leaves the title alone on
 * purpose: the title is where the numbers live precisely because the collapsed row cuts
 * everything after it, and an idle card that spent that space on wording would undo the reason
 * [progressTitle] exists. Nothing to override on the goal-reached card — "it's been a while"
 * has nothing to add once the day is already won.
 */
private fun buildReminderNotification(
    context: Context,
    prefs: SharedPreferences,
    reminderTime: String,
    progress: NotificationProgress,
    onlyAlertOnce: Boolean,
    encouragementOverride: String? = null,
): Notification {
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val contentIntent = PendingIntent.getActivity(
        context, 0, openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_water_drop)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .setOnlyAlertOnce(onlyAlertOnce)

    val unit = prefs.getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML

    // Phones ignore the action icon; watches draw it.
    prefs.notificationAmounts().forEach { ml ->
        builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_water_drop,
                context.getString(R.string.notification_action_add, ml.fmt(unit)),
                quickLogPendingIntent(context, reminderTime, ml)
            )
        )
    }

    // Same title as every other card, so the numbers are on the collapsed row here too. The
    // buttons stay — a drink after the goal should still log from the card — but no progress
    // bar: a bar is what makes an OEM phone drop the body, which is the whole message here.
    if (progress.goalReached) return builder
        .setContentTitle(progressTitle(context, progress, unit))
        .setContentText(context.getString(R.string.notification_body_goal_reached))
        .build()

    // Seeded per reminder per day rather than rolled fresh, because a quick-log tap rebuilds
    // this card: the wording must not change under the user's finger.
    val rng = Random(reminderTime.hashCode() + LocalDate.now().toEpochDay())

    builder
        .setContentTitle(progressTitle(context, progress, unit))
        .setContentText(
            context.getString(
                R.string.notification_body_progress,
                progress.remainingMl.fmt(unit),
                encouragementOverride
                    ?: context.resources.getStringArray(R.array.notification_bodies).random(rng),
            )
        )
        .setProgress(progress.goalMl, progress.intakeMl.coerceAtMost(progress.goalMl), false)

    return builder.build()
}

/**
 * The one line every reminder card leads with — today's total against the goal, in the user's
 * unit. It's the title rather than the subtext because the collapsed row spends its width on the
 * title first: as a subtext the numbers were the part that got cut ("1000 ml / 1…").
 */
private fun progressTitle(context: Context, progress: NotificationProgress, unit: String): String =
    context.getString(
        R.string.notification_title_progress,
        progress.intakeMl.toDisplay(unit),
        progress.goalMl.fmt(unit),
        progress.percent,
    )

/**
 * Every button targets the same receiver, so they need distinguishing or the system hands all
 * three whichever was registered last. Extras don't count towards a PendingIntent's identity —
 * the data URI does, and the request code does. Both are set: either alone would be enough.
 */
private fun quickLogPendingIntent(context: Context, reminderTime: String, ml: Int): PendingIntent {
    val intent = Intent(context, QuickLogReceiver::class.java).apply {
        data = Uri.parse("waterreminder://log/$reminderTime/$ml")
        putExtra(EXTRA_LOG_ML, ml)
        putExtra(EXTRA_LOG_REMINDER_TIME, reminderTime)
    }
    return PendingIntent.getBroadcast(
        context,
        31 * reminderTime.hashCode() + ml,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
