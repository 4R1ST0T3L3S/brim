package com.david.waterreminder

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import com.david.waterreminder.databinding.FragmentWaterBinding
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
import com.google.android.material.color.MaterialColors

class FirstFragment : Fragment() {

    companion object {
        /** Amounts drawn as a glass instead of a drop — the small sips. */
        val AMOUNT_ICONS = mapOf(
            100 to R.drawable.ic_glass_half,
            200 to R.drawable.ic_glass_full
        )
        const val TAG_ADD_SLOT = "add_slot"

        /** Bottles that still fit one row at full size; past this the row wraps at 24dp bottles. */
        const val BOTTLES_PER_ROW = 6
        const val BOTTLES_PER_ROW_COMPACT = 10
    }

    private var _binding: FragmentWaterBinding? = null
    private val binding get() = _binding!!

    private var currentMl = 0
    private val goalMl = DEFAULT_DAILY_GOAL_ML
    /** The amounts both quick rows are built from; empty is a valid state (all buttons removed). */
    private var quickAmounts: List<Int> = DEFAULT_QUICK_AMOUNTS_ML
    /** The bottle icons currently in the row; rebuilt only when the goal changes their number. */
    private var bottleViews: List<ImageView> = emptyList()

    private val prefs by lazy {
        requireContext().waterPrefs()
    }
    // Separate prefs file excluded from backup so the tour always runs after a reinstall.
    private val tourPrefs by lazy {
        requireContext().getSharedPreferences("tour_prefs", Context.MODE_PRIVATE)
    }
    private val unit get() = prefs.getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML

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
    }

    override fun onResume() {
        super.onResume()
        loadAndResetIfNewDay()
        updateIntakeUI()
        quickAmounts = prefs.quickAmounts()
        rebuildQuickButtons()   // refresh if the unit or the amounts changed elsewhere
        prefs.registerOnSharedPreferenceChangeListener(intakeListener)
    }

    override fun onPause() {
        super.onPause()
        prefs.unregisterOnSharedPreferenceChangeListener(intakeListener)
    }

    // ─── Day reset / load ─────────────────────────────────────────

    private fun loadAndResetIfNewDay() {
        // The rollover itself lives in Units.kt, so the History screen's day editor can lean on the
        // same "which day is intake_ml about" rule before it touches today's number.
        prefs.rollOverDayIfNeeded()
        // Clamped on the way in too: prefs can arrive from a backup restore or an imported file.
        currentMl = prefs.getInt("intake_ml", 0).asDayIntake()
    }

    // ─── Water add / remove ───────────────────────────────────────

    private fun addWater(ml: Int) {
        // The arithmetic and the writes live in Intake.kt, shared with the notification
        // buttons — this side is only the celebrating, which needs a fragment to do.
        val result = prefs.logIntake(ml)
        // No BackupManager call: MainActivity keeps a listener on water_prefs for as long as it
        // is started, and a resumed fragment is always inside that window.
        currentMl = result.newTotal
        onIntakeLogged(requireContext())

        if (result.crossedDailyGoal) showGoalReachedPopup(result.streak)
        if (result.crossedPersonalGoal) {
            showPersonalGoalReachedPopup(prefs.getInt("personal_goal_ml", 0))
        }

        updateIntakeUI()
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
        currentMl = prefs.logIntake(-ml).newTotal
        updateIntakeUI()
    }

    private fun updateIntakeUI() {
        // Active goal = nearest milestone not yet reached (personal goal + daily goal, sorted).
        // Falls back to the highest goal once everything is reached.
        val personalGoal = prefs.getInt("personal_goal_ml", 0)
        val activeGoal = if (personalGoal > 0) personalGoal else goalMl

        binding.progressWater.progress = ((currentMl * 100) / activeGoal).coerceAtMost(100)
        binding.textIntakeAmount.text = "${currentMl.fmt(unit)} / ${activeGoal.fmt(unit)}"

        // Congratulations banner is tied to the fixed daily goal
        binding.textGoalReached.visibility = if (currentMl >= goalMl) View.VISIBLE else View.GONE

        updateBottlesUI(activeGoal)
    }

    /**
     * A bottle per 500 ml of the active goal, so a bigger goal simply gets more bottles and the
     * whole row reads full once the goal is reached. Beyond [BOTTLES_PER_ROW] the row wraps and the
     * bottles shrink, which keeps even a 20-bottle goal on screen.
     */
    private fun updateBottlesUI(activeGoal: Int) {
        val fillSteps = bottleFillSteps(currentMl, activeGoal)
        if (bottleViews.size != fillSteps.size) rebuildBottles(fillSteps.size)
        bottleViews.forEachIndexed { index, imageView ->
            imageView.setImageResource(bottleIcon(fillSteps[index]))
        }
    }

    private fun rebuildBottles(count: Int) {
        val dp = resources.displayMetrics.density
        val compact = count > BOTTLES_PER_ROW
        val sizePx = ((if (compact) 24f else 36f) * dp).toInt()
        val gapPx  = ((if (compact) 6f else 12f) * dp).toInt()
        // Balance the rows (13 bottles → 7 + 6, not 10 + 3) rather than filling each to the brim.
        val rows = (count + BOTTLES_PER_ROW_COMPACT - 1) / BOTTLES_PER_ROW_COMPACT
        val perRow = (count + rows - 1) / rows

        val tint = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), R.color.brand_blue)
        )
        val views = List(count) { index ->
            ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    if (index % perRow > 0) marginStart = gapPx
                }
                imageTintList = tint
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }

        binding.containerBottles.removeAllViews()
        views.chunked(perRow).forEachIndexed { rowIndex, rowViews ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (rowIndex > 0) topMargin = gapPx }
            }
            rowViews.forEach(row::addView)
            binding.containerBottles.addView(row)
        }
        bottleViews = views
    }

    private fun bottleIcon(fillStep: Int): Int = when (fillStep) {
        0    -> R.drawable.ic_bottle_empty
        1    -> R.drawable.ic_bottle_quarter
        2    -> R.drawable.ic_bottle_half
        3    -> R.drawable.ic_bottle_three_quarters
        4    -> R.drawable.ic_bottle_almost_full
        else -> R.drawable.ic_bottle_full
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

        val addBg = themeColor(R.attr.quickAddBackground)
        val addFg = themeColor(R.attr.quickAddForeground)

        fillAmountRow(binding.containerQuickAdd, dp, "+", addBg, addFg,
            onClick = ::addWater, onLongClick = { ml -> showAmountDialog(existingMl = ml) })
        // The "+" slot always trails the amounts, so it stays put as they are edited. Once the
        // row is full it disappears until an amount is removed.
        if (quickAmounts.size < QUICK_AMOUNTS_MAX) {
            binding.containerQuickAdd.addView(makeAddSlotButton(dp, addBg, addFg))
        }

        fillAmountRow(
            binding.containerQuickRemove, dp, "-",
            themeColor(R.attr.quickRemoveBackground), themeColor(R.attr.quickRemoveForeground),
            onClick = ::removeWater
        )

        // Nothing to subtract with once every amount is gone — hide the section rather than
        // leaving a labelled empty row behind.
        val removeVisibility = if (quickAmounts.isEmpty()) View.GONE else View.VISIBLE
        binding.textQuickRemoveLabel.visibility = removeVisibility
        binding.containerQuickRemove.visibility = removeVisibility
    }

    /** Resolves a theme color attribute against the current view's theme. */
    @ColorInt
    private fun themeColor(@AttrRes attr: Int): Int = MaterialColors.getColor(requireView(), attr)

    /** Refills [container] with one button per amount, each tagged with its ml value. */
    private fun fillAmountRow(
        container: ViewGroup, dp: Float, prefix: String, @ColorInt bg: Int, @ColorInt fg: Int,
        onClick: (Int) -> Unit, onLongClick: ((Int) -> Unit)? = null
    ) {
        val unit = unit   // a prefs read per button otherwise, and this runs twice per rebuild
        container.removeAllViews()
        quickAmounts.forEach { ml ->
            container.addView(
                makeAmountButton("$prefix${ml.fmt(unit)}", bg, fg, dp,
                    iconRes = AMOUNT_ICONS[ml] ?: R.drawable.ic_water_drop,
                    onClick = { onClick(ml) },
                    onLongClick = onLongClick?.let { handler -> { handler(ml) } }
                ).apply { tag = ml }
            )
        }
    }

    /** Button for a specific ml amount — used in both add and remove rows. */
    private fun makeAmountButton(
        label: String, @ColorInt bg: Int, @ColorInt fg: Int, dp: Float,
        iconRes: Int = R.drawable.ic_water_drop,
        onClick: () -> Unit, onLongClick: (() -> Unit)? = null
    ) = MaterialButton(requireContext()).apply {
        text = label
        setTextColor(fg)
        backgroundTintList = ColorStateList.valueOf(bg)
        cornerRadius = (24 * dp).toInt()
        insetTop = 0; insetBottom = 0
        textSize = 11f
        isSingleLine = true
        setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        setIconResource(iconRes)
        iconTint = ColorStateList.valueOf(fg)
        iconPadding = (2 * dp).toInt()
        iconSize = (12 * dp).toInt()
        iconGravity = ICON_GRAVITY_TEXT_START
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = (8 * dp).toInt(); bottomMargin = (8 * dp).toInt() }
        setOnClickListener { onClick() }
        onLongClick?.let { lc -> setOnLongClickListener { lc(); true } }
    }

    /** The circular "+" closing the add row; opens the dialog that adds a new amount. */
    private fun makeAddSlotButton(dp: Float, @ColorInt bg: Int, @ColorInt fg: Int): MaterialButton {
        val size = (40 * dp).toInt()
        return MaterialButton(requireContext()).apply {
            text = "+"
            tag = TAG_ADD_SLOT
            contentDescription = getString(R.string.dialog_add_amount_title)
            setTextColor(fg)
            backgroundTintList = ColorStateList.valueOf(bg)
            cornerRadius = size / 2
            insetTop = 0; insetBottom = 0
            textSize = 16f
            isSingleLine = true
            setPadding(0, 0, 0, 0)
            iconSize = 0
            layoutParams = LinearLayout.LayoutParams(size, size)
                .apply { marginEnd = (8 * dp).toInt(); bottomMargin = (8 * dp).toInt() }
            setOnClickListener { showAmountDialog(existingMl = null) }
            setOnLongClickListener { showAmountDialog(existingMl = null); true }
        }
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

            // Find a quick-add child by its tag (set in rebuildQuickButtons).
            fun findQuickAdd(tag: Any): View? =
                (0 until binding.containerQuickAdd.childCount)
                    .map(binding.containerQuickAdd::getChildAt)
                    .firstOrNull { it.tag == tag }

            val steps: List<() -> TapTarget?> = listOf(
                {
                    TapTarget.forView(
                        binding.containerBottles,
                        "Daily progress",
                        "Each bottle represents 500 ml, and together they add up to your daily goal. " +
                            "Watch them fill up as you log your intake throughout the day."
                    ).style(radius = 110)
                },
                {
                    // The row's first button, whatever it happens to be — pointing at a specific
                    // amount would break once the user removes it. It has to be a single button:
                    // a target wide enough to hold the whole row leaves the pulse ring drawn
                    // inside the transparent hole, i.e. underneath the buttons.
                    binding.containerQuickAdd.getChildAt(0)?.let {
                        TapTarget.forView(
                            it, "Log a drink",
                            "Tap an amount to log it instantly — a glass icon is a small sip, a " +
                                "drop is a bigger drink. Long-press any button to change its " +
                                "value or remove it from the row."
                        ).style()
                    }
                },
                {
                    findQuickAdd(TAG_ADD_SLOT)?.let {
                        TapTarget.forView(
                            it, "Your own amounts",
                            "Tap + to add an amount of your own, up to $QUICK_AMOUNTS_MAX in total. " +
                                "Reset them any time from Settings."
                        ).style()
                    }
                },
                {
                    // Null once every amount has been removed — the row is empty and hidden then.
                    binding.containerQuickRemove.getChildAt(0)?.let {
                        TapTarget.forView(
                            it, "Made a mistake?",
                            "Use Quick remove to subtract water you logged by accident."
                        ).style()
                    }
                },
                {
                    activity.findViewById<View>(R.id.fab_analytics)?.let {
                        TapTarget.forView(
                            it, "Your history",
                            "Open the chart to see your daily intake and streaks over time."
                        ).style(tint = false)
                    }
                },
                {
                    activity.findViewById<View>(R.id.fab_reminders)?.let {
                        TapTarget.forView(
                            it, "Reminders",
                            "Tap the bell to set up reminder times."
                        ).style(tint = false)
                    }
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
        super.onDestroyView()
        bottleViews = emptyList()   // they belong to the destroyed view tree
        _binding = null
    }
}
