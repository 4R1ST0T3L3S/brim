package com.david.waterreminder

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Space
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.david.waterreminder.databinding.FragmentWaterBinding
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import java.time.LocalDate
import java.util.Calendar
import java.util.Date

class FirstFragment : Fragment() {

    companion object {
        /** Amounts drawn as a glass instead of a drop — the small sips. */
        val AMOUNT_ICONS = mapOf(
            100 to R.drawable.ic_glass_half,
            200 to R.drawable.ic_glass_full
        )
        const val TAG_ADD_SLOT = "add_slot"

        /** Amount buttons per row of the home grid. */
        private const val QUICK_COLUMNS = 2

        /** Days in the History card's mini chart, today included. */
        private const val HISTORY_CARD_DAYS = 7

        /** Quiet time after the last tap before the shade is synced. */
        private const val CARD_SYNC_DELAY_MS = 400L
    }

    private var _binding: FragmentWaterBinding? = null
    private val binding get() = _binding!!

    private var currentMl = 0
    private val goalMl = DEFAULT_DAILY_GOAL_ML
    /** The amounts the grid is built from; empty is a valid state (all buttons removed). */
    private var quickAmounts: List<Int> = DEFAULT_QUICK_AMOUNTS_ML
    /** The Add / Remove switch: the same grid adds or subtracts its amounts. */
    private var removeMode = false

    private val prefs by lazy {
        requireContext().waterPrefs()
    }
    // Separate prefs file excluded from backup so the tour always runs after a reinstall.
    private val tourPrefs by lazy {
        requireContext().getSharedPreferences("tour_prefs", Context.MODE_PRIVATE)
    }
    /** Today's drinks — see [todaysDrinks]. */
    private val drinkLog by lazy {
        requireContext().transientPrefs()
    }
    private val unit get() = prefs.getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML
    /** Today's drinks, opened from the "Last drink" line; null until first opened. */
    private var todaySheet: TodayDrinksSheet? = null

    /**
     * Water logged from a notification button lands in prefs behind this screen's back, so the
     * bottles follow it while the phone is awake instead of waiting for the next onResume.
     *
     * Held in a field on purpose: SharedPreferences keeps its listeners weakly, and a lambda
     * passed straight to register() is collectible the moment the call returns. Filtered to the
     * one key because a single logIntake commit touches several, and deliberately does not
     * celebrate — the popups belong to the tap that caused them, in addWater.
     */
    private val intakeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // Note: this also fires for this screen's own writes, so addWater/removeWater
            // redraw a second time. That is deliberate — both passes land inside one click,
            // before any frame is drawn, and the redundant one is nearly free because the
            // views all early-out on unchanged values. Dropping the explicit redraw would make
            // the home screen depend on this listener being registered.
            if (key != "intake_ml" || _binding == null) return@OnSharedPreferenceChangeListener
            currentMl = prefs.getInt("intake_ml", 0).asDayIntake()
            updateIntakeUI()
            todaySheet?.refreshIfShowing()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWaterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The rows are built in onResume, which still runs before the first draw — and so
        // before the tour below measures its targets.
        // Run the guide tour only on the very first launch.
        // doOnPreDraw waits until layout is finished so all targets have positions.
        if (!tourPrefs.getBoolean("tour_done", false)) {
            view.doOnPreDraw { maybeShowGuideTour() }
        }

        binding.textLastDrink.setOnClickListener {
            val sheet = todaySheet ?: TodayDrinksSheet(requireContext(), drinkLog) { unit }
            todaySheet = sheet
            sheet.show()
        }
        ViewCompat.replaceAccessibilityAction(
            binding.textLastDrink,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            getString(R.string.home_last_drink_action),
            null
        )

        binding.btnModeAdd.setOnClickListener { setRemoveMode(false) }
        binding.btnModeRemove.setOnClickListener { setRemoveMode(true) }

        // The ripple would otherwise spill past the rounded corners.
        binding.cardHistory.clipToOutline = true
        binding.cardReminders.clipToOutline = true
        binding.cardHistory.setOnClickListener { findNavController().navigate(R.id.AnalyticsFragment) }
        binding.cardReminders.setOnClickListener { findNavController().navigate(R.id.RemindersFragment) }
    }

    override fun onResume() {
        super.onResume()
        loadAndResetIfNewDay()
        updateIntakeUI()
        quickAmounts = prefs.quickAmounts()
        rebuildQuickButtons()   // refresh if the unit or the amounts changed elsewhere
        updateReminderCard()    // reminders may have changed on their own screen
        prefs.registerOnSharedPreferenceChangeListener(intakeListener)
    }

    override fun onPause() {
        super.onPause()
        prefs.unregisterOnSharedPreferenceChangeListener(intakeListener)
        flushCardSync()
    }

    // ─── Day reset / load ─────────────────────────────────────────

    private fun loadAndResetIfNewDay() {
        // The rollover itself lives in Units.kt, so the History screen's day editor can lean on the
        // same "which day is intake_ml about" rule before it touches today's number.
        prefs.rollOverDayIfNeeded()
        drinkLog.clearStaleDrinkLog()
        // Clamped on the way in too: prefs can arrive from a backup restore or an imported file.
        currentMl = prefs.getInt("intake_ml", 0).asDayIntake()
    }

    // ─── Water add / remove ───────────────────────────────────────

    private fun addWater(ml: Int) {
        // The arithmetic and the writes live in Intake.kt, shared with the notification
        // buttons — this side is only the celebrating, which needs a fragment to do.
        val result = prefs.logIntake(ml, transientStore = drinkLog)
        // No BackupManager call: MainActivity keeps a listener on water_prefs for as long as it
        // is started, and a resumed fragment is always inside that window.
        currentMl = result.newTotal
        onIntakeLogged(requireContext())
        scheduleCardSync(drink = true)
        requestHealthConnectSync(requireContext())

        if (result.crossedDailyGoal) showGoalReachedPopup(result.streak)
        if (result.crossedPersonalGoal) {
            showPersonalGoalReachedPopup(prefs.getInt("personal_goal_ml", 0))
        }

        updateIntakeUI()
    }

    // ─── Keeping the shade in step ────────────────────────────────

    private val pendingCardSync = PendingCardSync()

    private val cardSync = Runnable { flushCardSync() }

    /**
     * Syncs the cards in the shade once the taps stop rather than on each one, waiting for
     * [CARD_SYNC_DELAY_MS] of quiet. The system drops updates to an existing notification past
     * roughly five a second per app, and with two cards up a quick run of taps gets there — the
     * update lost would be the last, leaving a stale total.
     *
     * Only this screen is coalesced. The widget and the notification buttons sync on every tap,
     * so a rapid burst there can still lose the last update: holding work back after a
     * broadcast receiver returns isn't reliable, as the process is no longer protected.
     */
    private fun scheduleCardSync(drink: Boolean) {
        pendingCardSync.add(drink)
        binding.root.removeCallbacks(cardSync)
        binding.root.postDelayed(cardSync, CARD_SYNC_DELAY_MS)
    }

    /** Runs a pending sync now. Also from onPause, so leaving the screen can't lose one. */
    private fun flushCardSync() {
        _binding?.root?.removeCallbacks(cardSync)
        val drink = pendingCardSync.take() ?: return
        context?.let { syncReminderCards(it, prefs, drink = drink) }
    }

    private fun showGoalReachedPopup(streak: Int) {
        if (!prefs.celebrationsEnabled()) return
        val streakLine = if (streak > 1) "\n🔥 $streak day streak — keep it up!" else ""
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_minimum_reached_title))
            .setMessage(getString(R.string.dialog_minimum_reached_message, goalMl.fmt(unit)) + streakLine)
            .setPositiveButton(getString(R.string.dialog_minimum_reached_button), null)
            .show()
    }

    private fun showPersonalGoalReachedPopup(personalGoal: Int) {
        if (!prefs.celebrationsEnabled()) return
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_personal_goal_reached_title))
            .setMessage(getString(R.string.dialog_personal_goal_reached_message, personalGoal.fmt(unit)))
            .setPositiveButton(getString(R.string.dialog_personal_goal_reached_button), null)
            .show()
    }

    private fun removeWater(ml: Int) {
        // Same path as adding, negated — including dropping the streak back on the way under
        // the goal. No popups: taking water off is never a celebration.
        currentMl = prefs.logIntake(-ml, transientStore = drinkLog).newTotal
        // Not onIntakeLogged: taking water back must not restart the idle countdown. The cards
        // in the shade still need the new total, though.
        scheduleCardSync(drink = false)
        // The drink taken back comes out of Health Connect too.
        requestHealthConnectSync(requireContext())
        updateIntakeUI()
    }

    private fun updateIntakeUI() {
        // Active goal = nearest milestone not yet reached (personal goal + daily goal, sorted).
        // Falls back to the highest goal once everything is reached.
        val personalGoal = prefs.getInt("personal_goal_ml", 0)
        val activeGoal = if (personalGoal > 0) personalGoal else goalMl
        val percent = (currentMl * 100) / activeGoal

        binding.textIntakeValue.text = currentMl.toDisplay(unit)
        binding.textIntakeUnit.text = unit
        binding.textIntakeAmount.text =
            getString(R.string.home_intake_of_goal, activeGoal.fmt(unit), percent)
        binding.progressWater.setProgressCompat(percent.coerceAtMost(100), true)

        // Congratulations banner is tied to the fixed daily goal
        binding.textGoalReached.visibility = if (currentMl >= goalMl) View.VISIBLE else View.GONE

        updateLastDrinkUI()
        updateHistoryCard()
    }

    /** Read from the log, which also covers drinks logged from a notification or the widget. */
    private fun updateLastDrinkUI() {
        val last = drinkLog.todaysDrinks().lastOrNull()
        // Invisible rather than gone: the first drink of the day must not push the grid down
        // under the finger that is about to tap again.
        binding.textLastDrink.visibility = if (last == null) View.INVISIBLE else View.VISIBLE
        if (last != null) {
            val time = DateFormat.getTimeFormat(requireContext()).format(Date(last.atMillis))
            binding.textLastDrink.text = getString(R.string.home_last_drink, time)
        }
    }

    // ─── Cards ────────────────────────────────────────────────────

    /**
     * The last [HISTORY_CARD_DAYS] days as bars scaled to the best of them, today last and dimmed
     * because it is still filling. The average leaves today out for the same reason.
     */
    private fun updateHistoryCard() {
        val today = LocalDate.now()
        val totals = (HISTORY_CARD_DAYS - 1 downTo 0).map { back ->
            if (back == 0) currentMl else prefs.dayTotal(today.minusDays(back.toLong()))
        }
        val past = totals.dropLast(1).filter { it > 0 }
        binding.textHistoryAvg.text = if (past.isEmpty()) getString(R.string.home_history_empty)
            else getString(R.string.home_history_avg, (past.sum() / past.size).fmt(unit))

        val dp = resources.displayMetrics.density
        val container = binding.containerMiniBars
        val chartHeight = (34 * dp).toInt()
        val scale = maxOf(totals.max(), goalMl)
        val accent = ContextCompat.getColor(requireContext(), R.color.brand_blue)
        val dim = ContextCompat.getColor(requireContext(), R.color.surface_control_hover)
        container.removeAllViews()
        totals.forEachIndexed { index, ml ->
            val isToday = index == totals.lastIndex
            container.addView(View(requireContext()).apply {
                // Even an empty day keeps a sliver, so the week still reads as seven days.
                val height = maxOf((chartHeight * ml / scale), (3 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(0, height, 1f).apply {
                    if (index > 0) marginStart = (4 * dp).toInt()
                }
                background = GradientDrawable().apply {
                    cornerRadius = 3 * dp
                    setColor(if (isToday || ml == 0) dim else accent)
                }
            })
        }
    }

    /** The soonest enabled reminder, or "Off" with a nudge to set one up. */
    private fun updateReminderCard() {
        val next = ReminderScheduler.nextReminderAt(requireContext())
        if (next == null) {
            binding.textNextReminder.text = getString(R.string.home_reminder_off_value)
            binding.textReminderDetail.text = getString(R.string.home_reminder_off_detail)
            return
        }
        binding.textNextReminder.text = DateFormat.getTimeFormat(requireContext()).format(next.time)
        val isToday = next.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        binding.textReminderDetail.text = getString(
            if (isToday) R.string.home_reminder_today else R.string.home_reminder_tomorrow
        )
    }

    // ─── Quick amounts ────────────────────────────────────────────

    /** Persist [amounts] (sanitized on the way in) and rebuild both rows. */
    private fun applyQuickAmounts(amounts: List<Int>) {
        quickAmounts = prefs.setQuickAmounts(amounts)
        rebuildQuickButtons()
    }

    /** Called by SettingsFragment after it resets the amounts to the defaults. */
    fun onQuickAmountsChanged() {
        quickAmounts = prefs.quickAmounts()
        rebuildQuickButtons()
    }

    // ─── Quick button builders ────────────────────────────────────

    private fun rebuildQuickButtons() {
        val dp = resources.displayMetrics.density
        val bg = themeColor(if (removeMode) R.attr.quickRemoveBackground else R.attr.quickAddBackground)
        val fg = themeColor(if (removeMode) R.attr.quickRemoveForeground else R.attr.quickAddForeground)
        val unit = unit   // a prefs read per button otherwise
        val sign = if (removeMode) "−" else "+"

        val cells = quickAmounts.map { ml ->
            makeAmountButton("$sign${ml.toDisplay(unit)}", bg, fg, dp,
                onClick = { if (removeMode) removeWater(ml) else addWater(ml) },
                // Editing belongs to the add side; in Remove mode a long press does nothing.
                onLongClick = if (removeMode) null else { { showAmountDialog(existingMl = ml) } }
            ).apply {
                tag = ml
                contentDescription = "$sign${ml.fmt(unit)}"
            }
        } + listOfNotNull(
            // The "+" cell trails the amounts, so it stays put as they are edited. It is gone once
            // the grid is full, and in Remove mode, where it would mean nothing.
            if (!removeMode && quickAmounts.size < QUICK_AMOUNTS_MAX) makeAddSlotButton(dp) else null
        )

        val container = binding.containerQuick
        container.removeAllViews()
        val gap = (10 * dp).toInt()
        val cellHeight = (56 * dp).toInt()
        cells.chunked(QUICK_COLUMNS).forEachIndexed { rowIndex, rowCells ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (rowIndex > 0) topMargin = gap }
            }
            rowCells.forEachIndexed { index, cell ->
                row.addView(cell, LinearLayout.LayoutParams(0, cellHeight, 1f).apply {
                    if (index > 0) marginStart = gap
                })
            }
            // A lone last cell keeps its column's width instead of stretching across the row.
            repeat(QUICK_COLUMNS - rowCells.size) {
                row.addView(Space(requireContext()), LinearLayout.LayoutParams(0, 0, 1f).apply {
                    marginStart = gap
                })
            }
            container.addView(row)
        }

        // Nothing to subtract with once every amount is gone.
        binding.btnModeRemove.isEnabled = quickAmounts.isNotEmpty()
        binding.btnModeRemove.alpha = if (quickAmounts.isEmpty()) 0.4f else 1f
        if (quickAmounts.isEmpty() && removeMode) setRemoveMode(false)
    }

    /** Flips the switch and redraws the grid with the other sign. */
    private fun setRemoveMode(remove: Boolean) {
        removeMode = remove
        val on = ContextCompat.getColor(requireContext(), R.color.on_brand)
        val off = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        binding.btnModeAdd.apply {
            setBackgroundResource(if (remove) 0 else R.drawable.bg_segment_on)
            setTextColor(if (remove) off else on)
            isSelected = !remove
        }
        binding.btnModeRemove.apply {
            setBackgroundResource(if (remove) R.drawable.bg_segment_on else 0)
            setTextColor(if (remove) on else off)
            isSelected = remove
        }
        rebuildQuickButtons()
    }

    /** Resolves a theme color attribute against the current view's theme. */
    @ColorInt
    private fun themeColor(@AttrRes attr: Int): Int = MaterialColors.getColor(requireView(), attr)

    /** One cell of the grid: a flat control-coloured button, no outline, no icon. */
    private fun makeAmountButton(
        label: String, @ColorInt bg: Int, @ColorInt fg: Int, dp: Float,
        onClick: () -> Unit, onLongClick: (() -> Unit)? = null
    ) = MaterialButton(requireContext()).apply {
        text = label
        setTextColor(fg)
        backgroundTintList = ColorStateList.valueOf(bg)
        cornerRadius = (18 * dp).toInt()
        insetTop = 0; insetBottom = 0
        textSize = 15f
        typeface = ResourcesCompat.getFont(requireContext(), R.font.geist_semibold)
        fontFeatureSettings = "tnum"
        isSingleLine = true
        stateListAnimator = null
        elevation = 0f
        setOnClickListener { onClick() }
        onLongClick?.let { lc -> setOnLongClickListener { lc(); true } }
    }

    /** The "+" closing the grid; opens the dialog that adds a new amount. */
    private fun makeAddSlotButton(dp: Float) = MaterialButton(requireContext()).apply {
        text = "+"
        tag = TAG_ADD_SLOT
        contentDescription = getString(R.string.dialog_add_amount_title)
        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.surface_card))
        cornerRadius = (18 * dp).toInt()
        insetTop = 0; insetBottom = 0
        textSize = 20f
        stateListAnimator = null
        elevation = 0f
        setOnClickListener { showAmountDialog(existingMl = null) }
    }

    /**
     * One dialog for both jobs: adding an amount ([existingMl] null) and editing an existing one,
     * which can also be removed from here — so every button on the row, shipped or self-added,
     * is changed the same way.
     */
    private fun showAmountDialog(existingMl: Int?) {
        val editText = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                if (isDecimalUnit(unit)) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
            hint = getString(R.string.dialog_amount_hint, unit, 180.toDisplay(unit))
            setPadding(48, 32, 48, 32)
            if (existingMl != null) { setText(existingMl.toDisplay(unit)); selectAll() }
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(
                if (existingMl == null) R.string.dialog_add_amount_title
                else R.string.dialog_edit_amount_title
            ))
            .setView(editText)
            .setPositiveButton(getString(R.string.dialog_save), null)
            .apply {
                if (existingMl != null) {
                    setNeutralButton(getString(R.string.dialog_remove)) { _, _ ->
                        applyQuickAmounts(quickAmounts.filterNot { it == existingMl })
                    }
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val ml = editText.text.toString().toValidCustomMl(unit)
            when {
                ml == null ->
                    editText.error = getString(R.string.error_amount_range, CUSTOM_MAX_ML.fmt(unit))
                ml != existingMl && ml in quickAmounts ->
                    editText.error = getString(R.string.error_amount_duplicate)
                else -> {
                    applyQuickAmounts(quickAmounts.filterNot { it == existingMl } + ml)
                    dialog.dismiss()
                }
            }
        }
        editText.requestFocus()
    }

    /** Called by SettingsFragment when the unit changes so labels refresh immediately. */
    fun onUnitChanged() {
        updateIntakeUI()
        rebuildQuickButtons()
    }

    // ─── First-launch guide tour ──────────────────────────────────

    private fun maybeShowGuideTour() {
        if (_binding == null) return
        val activity = activity ?: return

        // Mark done first — before any TapTargetView code runs — so a crash during
        // target creation can never lock the app in an infinite crash-on-launch loop.
        tourPrefs.edit().putBoolean("tour_done", true).apply()

        try {
            // Common style: no inner highlight circle, tap-anywhere advances, optional view tint.
            fun TapTarget.style(radius: Int = 40, tint: Boolean = true) = this
                .transparentTarget(true)
                .targetRadius(radius)
                .cancelable(true)
                .tintTarget(tint)

            // Grid cells, row by row (rebuildQuickButtons nests them in one row per pair).
            fun quickCells(): List<View> =
                (0 until binding.containerQuick.childCount)
                    .mapNotNull { binding.containerQuick.getChildAt(it) as? ViewGroup }
                    .flatMap { row -> (0 until row.childCount).map(row::getChildAt) }
                    .filterIsInstance<MaterialButton>()

            val steps: List<() -> TapTarget?> = listOf(
                {
                    TapTarget.forView(
                        binding.textIntakeValue,
                        "Daily progress",
                        "Your total for today, and the goal it counts towards. " +
                            "The bar fills as you log your intake throughout the day."
                    ).style(radius = 110)
                },
                {
                    // The grid's first amount, whatever it happens to be — pointing at a specific
                    // amount would break once the user removes it.
                    quickCells().firstOrNull { it.tag != TAG_ADD_SLOT }?.let {
                        TapTarget.forView(
                            it, "Log a drink",
                            "Tap an amount to log it instantly. Long-press any button to change " +
                                "its value or remove it from the grid."
                        ).style()
                    }
                },
                {
                    quickCells().firstOrNull { it.tag == TAG_ADD_SLOT }?.let {
                        TapTarget.forView(
                            it, "Your own amounts",
                            "Tap + to add an amount of your own, up to $QUICK_AMOUNTS_MAX in total. " +
                                "Reset them any time from Settings."
                        ).style()
                    }
                },
                {
                    // Nothing to remove with once every amount has been deleted.
                    binding.btnModeRemove.takeIf { quickAmounts.isNotEmpty() }?.let {
                        TapTarget.forView(
                            it, "Made a mistake?",
                            "Switch to Remove to subtract water you logged by accident."
                        ).style()
                    }
                },
                {
                    TapTarget.forView(
                        binding.cardHistory, "Your history",
                        "Open the chart to see your daily intake and streaks over time."
                    ).style(radius = 70, tint = false)
                },
                {
                    TapTarget.forView(
                        binding.cardReminders, "Reminders",
                        "Tap here to set up reminder times."
                    ).style(radius = 70, tint = false)
                },
            )

            val targets = steps.mapNotNull { it() }
            if (targets.isEmpty()) return

            TapTargetSequence(activity)
                .targets(targets)
                .considerOuterCircleCanceled(true)
                .continueOnCancel(true)
                .start()

        } catch (e: Exception) {
            // TapTargetView can fail on certain device/theme/API combinations.
            // The tour is silently skipped — the user can replay it via Settings.
        }
    }

    override fun onDestroyView() {
        // onPause has already flushed; this only keeps a callback from outliving the view.
        _binding?.root?.removeCallbacks(cardSync)
        // A dialog outliving its window leaks it; the sheet is rebuilt on the next tap.
        todaySheet?.dismiss()
        todaySheet = null
        super.onDestroyView()
        _binding = null
    }
}
