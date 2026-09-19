package com.david.waterreminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RawRes
import androidx.annotation.StringRes
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

/**
 * Which sound a reminder arrives with. Stores [ReminderSound.key] rather than an ordinal, so
 * reordering the picker or dropping a clip can't silently repoint everyone's choice at a
 * different sound.
 */
const val PREF_REMINDER_SOUND = "reminder_sound"

/**
 * A sound a reminder can arrive with, and the channel that carries it.
 *
 * There is a channel per sound because a channel's sound is fixed when it is created and can
 * never be changed afterwards. Deleting the id and recreating it doesn't reset it either —
 * Android remembers a deleted channel and restores its settings if the same id comes back — so
 * the only way to offer a choice is to post to a different channel.
 */
enum class ReminderSound(
    val key: String,
    val channelId: String,
    @RawRes val rawRes: Int?,
    @StringRes val label: Int,
) {
    /** Whatever the phone uses for notifications, which is what every install had before the picker. */
    DEFAULT("default", REMINDER_CHANNEL_ID, null, R.string.reminder_sound_default),
    DROP("drop", "water_reminder_channel_drop", R.raw.drop, R.string.reminder_sound_drop),
    TAP("tap", "water_reminder_channel_tap", R.raw.tap, R.string.reminder_sound_tap);

    /**
     * The clip to play, shared by the channel and the picker's preview. [DEFAULT] hands back a
     * symbolic URI the system resolves at play time against the phone's own notification sound,
     * so a phone set to "None" stays silent without this having to know.
     *
     * A clip is addressed by resource name rather than by [rawRes] itself. The numeric id is
     * rewritten by any build that adds or removes a resource, and a channel stores the URI it
     * was created with outside the APK — so an id baked in here would point at something else
     * after the next update, with no way back, since a channel's sound can never be changed.
     */
    fun uri(context: Context): Uri {
        if (rawRes == null) return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        return Uri.parse("android.resource://${context.packageName}/raw/" +
            context.resources.getResourceEntryName(rawRes))
    }

    /**
     * The channel this sound posts to, created if it isn't there yet and returned so the builder
     * can't be pointed at one that doesn't exist. Only ever adds, which is what makes it safe on
     * the posting path; [selectChannel] is the half that destroys.
     */
    fun ensureChannel(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return channelId

        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            // Nothing to set for DEFAULT: a new channel already plays the phone's notification sound.
            if (rawRes != null) setSound(uri(context), NOTIFICATION_AUDIO_ATTRIBUTES)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return channelId
    }

    /**
     * Makes this the only sound holding a channel, so the system's notification settings go on
     * showing a single "Water Reminders" entry rather than one per clip.
     *
     * Kept off the posting path because it destroys: deleting a channel takes any card still in
     * the shade with it, so an unanswered reminder disappears when the sound changes under it.
     * That is what the single entry costs, and confining the deletes to here means it is only
     * ever paid at the moment the user is in the picker changing the sound themselves.
     */
    fun selectChannel(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            values().filter { it != this }
                .forEach { manager.deleteNotificationChannel(it.channelId) }
        }
        return ensureChannel(context)
    }
}

/**
 * Marks the clip as a notification rather than media, so it follows the notification volume and
 * doesn't duck the user's music as an alarm would. Shared with the picker's preview.
 *
 * Built lazily rather than eagerly: as a top-level val it would otherwise be constructed when
 * this file's facade class loads, which happens in the JVM unit tests too — where android.jar is
 * stubbed and building one throws.
 */
val NOTIFICATION_AUDIO_ATTRIBUTES: AudioAttributes by lazy {
    AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .build()
}

/** The chosen sound, falling back to [ReminderSound.DEFAULT] for a key no version still ships. */
fun SharedPreferences.reminderSound(): ReminderSound {
    val key = getString(PREF_REMINDER_SOUND, null)
    return ReminderSound.values().firstOrNull { it.key == key } ?: ReminderSound.DEFAULT
}

fun SharedPreferences.setReminderSound(sound: ReminderSound) {
    edit().putString(PREF_REMINDER_SOUND, sound.key).apply()
}

/** Extras a quick-log button carries back to [QuickLogReceiver], or to [WaterWidgetProvider]. */
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
        // Recorded rather than inferred from the schedule: a reminder due but never delivered
        // must not silence the idle nudge. The cancel covers the opposite ordering.
        context.transientPrefs().edit()
            .putLong(PREF_LAST_REMINDER_POST_AT, System.currentTimeMillis()).apply()
        cancelIdleReminderNotification(context)
    }
    return posted
}

/**
 * Posts the idle card, or takes it away if the gap has since been answered. Always alerts, for
 * the same reason [postReminderNotification] does — one id, reposted every time the gap elapses.
 */
fun postIdleReminderNotification(context: Context, prefs: SharedPreferences): Boolean {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val progress = prefs.notificationProgress()
    // One clock reading for the decision and for the number on the card, so they can't disagree.
    val now = Calendar.getInstance()

    val lastReminderPost = context.transientPrefs().lastReminderPostAt(now.timeInMillis)
    if (!shouldShowIdleReminder(prefs, progress, now, lastReminderPost)) {
        cancelIdleReminderNotification(context)
        return false
    }

    notificationManager.notify(
        reminderNotificationId(IDLE_REMINDER_KEY),
        buildReminderNotification(
            context, prefs, IDLE_REMINDER_KEY, progress, onlyAlertOnce = false,
            encouragementOverride = idleBody(context, prefs, now.timeInMillis),
        )
    )
    return true
}

/**
 * How long it has actually been, which is not always the gap that was set — a delivery delayed by
 * battery management makes it longer. Falls back to the vaguer wording when there is no last
 * drink to measure from, which is the first idle card of a day that started with nothing logged.
 */
private fun idleBody(context: Context, prefs: SharedPreferences, nowMillis: Long): String {
    val elapsed = idleElapsedMinutes(nowMillis, prefs.lastIntakeAt(nowMillis))
    return if (elapsed >= 5) {
        context.getString(R.string.notification_body_idle_elapsed, context.durationLabel(elapsed))
    } else {
        context.getString(R.string.notification_body_idle)
    }
}

/**
 * Rebuilds the idle card after a quick-log tap, as [refreshReminderNotification] does for a
 * reminder card. The idle wording goes with the rebuild — the tap has just made it false — so
 * what is left is an ordinary card. Never re-alerts: the user is looking at it.
 */
fun refreshIdleReminderNotification(context: Context, prefs: SharedPreferences) {
    val progress = prefs.notificationProgress()
    // Every rule except the gap, which this very tap just answered — that is what makes this a
    // rebuild rather than a cancel.
    if (!idleCardAllowed(prefs, progress)) {
        cancelIdleReminderNotification(context)
        return
    }

    (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
        reminderNotificationId(IDLE_REMINDER_KEY),
        buildReminderNotification(context, prefs, IDLE_REMINDER_KEY, progress, onlyAlertOnce = true)
    )
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
    val sound = prefs.reminderSound()
    val builder = NotificationCompat.Builder(context, sound.ensureChannel(context))
        .setSmallIcon(R.drawable.ic_water_drop)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(openAppPendingIntent(context))
        .setAutoCancel(true)
        .setOnlyAlertOnce(onlyAlertOnce)

    // Pre-O there are no channels, so the sound rides on the notification itself. Without this
    // the card arrives silent — PRIORITY_HIGH alone never made a sound, which is what every
    // Android 7 install got before the picker existed. Ignored from O up, where the channel wins.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        builder.setSound(sound.uri(context), AudioManager.STREAM_NOTIFICATION)
    }

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
 * Opens the app. Shared with the widget's header rather than built twice: same component and same
 * request code make one PendingIntent, and FLAG_UPDATE_CURRENT would let whichever was built last
 * rewrite the other.
 */
fun openAppPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
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
