package com.david.waterreminder

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import com.david.waterreminder.databinding.FragmentWaterBinding
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
import java.time.LocalDate

class FirstFragment : Fragment() {

    companion object {
        val FIXED_ML = listOf(100, 200, 250, 330, 500)
        val AMOUNT_ICONS = mapOf(
            100 to R.drawable.ic_glass_half,
            200 to R.drawable.ic_glass_full
        )
        const val ADD_BG = "#2196F3"
        const val ADD_FG = "#FFFFFF"
        const val REM_BG = "#E3F2FD"
        const val REM_FG = "#2196F3"
        const val TAG_CUSTOM_SLOT = "custom_slot"
    }

    private var _binding: FragmentWaterBinding? = null
    private val binding get() = _binding!!

    private var currentMl = 0
    private val goalMl = 1500
    private var customQuickMl = 0

    private val prefs by lazy {
        requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
    }
    // Separate prefs file excluded from backup so the tour always runs after a reinstall.
    private val tourPrefs by lazy {
        requireContext().getSharedPreferences("tour_prefs", Context.MODE_PRIVATE)
    }
    private val unit get() = prefs.getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWaterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        customQuickMl = prefs.getInt("custom_quick_ml", 0)
        rebuildQuickButtons()
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
        rebuildQuickButtons()   // refresh labels if unit changed in Settings
    }

    // ─── Day reset / load ─────────────────────────────────────────

    private fun loadAndResetIfNewDay() {
        val today = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()
        val savedDate = prefs.getString("date", "")

        if (savedDate != today) {
            if (!savedDate.isNullOrEmpty()) {
                val previousIntake = prefs.getInt("intake_ml", 0)
                prefs.edit().putInt("history_$savedDate", previousIntake).apply()
            }
            val goalMetPreviousDay = prefs.getBoolean("goal_met", false)
            val streakBroken = savedDate != yesterday || !goalMetPreviousDay
            val newStreak = if (streakBroken) 0 else prefs.getInt("streak", 0)
            currentMl = 0
            prefs.edit()
                .putString("date", today)
                .putInt("intake_ml", 0)
                .putBoolean("goal_met", false)
                .putInt("streak", newStreak)
                .apply()
        } else {
            currentMl = prefs.getInt("intake_ml", 0)
        }
    }

    // ─── Water add / remove ───────────────────────────────────────

    private fun addWater(ml: Int) {
        val personalGoal = prefs.getInt("personal_goal_ml", 0)
        val wasGoalMet         = currentMl >= goalMl
        val wasPersonalGoalMet = personalGoal > 0 && currentMl >= personalGoal

        currentMl += ml

        if (!wasGoalMet && currentMl >= goalMl) {
            val newStreak = prefs.getInt("streak", 0) + 1
            prefs.edit()
                .putInt("streak", newStreak)
                .putBoolean("goal_met", true)
                .apply()
            showGoalReachedPopup(newStreak)
        }

        if (personalGoal > 0 && !wasPersonalGoalMet && currentMl >= personalGoal) {
            showPersonalGoalReachedPopup(personalGoal)
        }

        saveIntake()
        updateIntakeUI()
    }

    private fun showGoalReachedPopup(streak: Int) {
        val streakLine = if (streak > 1) "\n🔥 $streak day streak — keep it up!" else ""
        AlertDialog.Builder(requireContext())
            .setTitle("🎉 Goal reached!")
            .setMessage("You've hit your daily goal of ${goalMl.fmt(unit)}!$streakLine")
            .setPositiveButton("Thanks! 💧", null)
            .show()
    }

    private fun showPersonalGoalReachedPopup(personalGoal: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("⭐ Personal goal reached!")
            .setMessage("Amazing! You've hit your personal goal of ${personalGoal.fmt(unit)} today!")
            .setPositiveButton("Let's go! 💪", null)
            .show()
    }

    private fun removeWater(ml: Int) {
        val wasGoalMet = currentMl >= goalMl
        currentMl = (currentMl - ml).coerceAtLeast(0)
        if (wasGoalMet && currentMl < goalMl) {
            val reducedStreak = (prefs.getInt("streak", 0) - 1).coerceAtLeast(0)
            prefs.edit()
                .putInt("streak", reducedStreak)
                .putBoolean("goal_met", false)
                .apply()
        }
        saveIntake()
        updateIntakeUI()
    }

    private fun saveIntake() {
        prefs.edit().putInt("intake_ml", currentMl).apply()
        android.app.backup.BackupManager(requireContext()).dataChanged()
    }

    private fun updateIntakeUI() {
        // Active goal = nearest milestone not yet reached (personal goal + daily goal, sorted).
        // Falls back to the highest goal once everything is reached.
        val personalGoal = prefs.getInt("personal_goal_ml", 0)
        val milestones = listOfNotNull(personalGoal.takeIf { it > 0 }, goalMl).sorted()
        val activeGoal = milestones.firstOrNull { currentMl < it } ?: milestones.last()

        binding.progressWater.progress = ((currentMl * 100) / activeGoal).coerceAtMost(100)
        binding.textIntakeAmount.text = "${currentMl.fmt(unit)} / ${activeGoal.fmt(unit)}"

        // Congratulations banner is tied to the fixed daily goal
        binding.textGoalReached.visibility = if (currentMl >= goalMl) View.VISIBLE else View.GONE

        updateBottlesUI()
    }

    private fun updateBottlesUI() {
        val mlPerBottle = 500
        val bottles = listOf(binding.bottle1, binding.bottle2, binding.bottle3, binding.bottle4)
        bottles.forEachIndexed { index, imageView ->
            val bottleMin = index * mlPerBottle
            val fill = (currentMl - bottleMin).coerceIn(0, mlPerBottle)
            val icon = when ((fill / 100).coerceIn(0, 5)) {
                0    -> R.drawable.ic_bottle_empty
                1    -> R.drawable.ic_bottle_quarter
                2    -> R.drawable.ic_bottle_half
                3    -> R.drawable.ic_bottle_three_quarters
                4    -> R.drawable.ic_bottle_almost_full
                else -> R.drawable.ic_bottle_full
            }
            imageView.setImageResource(icon)
        }
    }

    // ─── Custom quick slot ────────────────────────────────────────

    fun applyCustomQuickMl(value: Int) {
        customQuickMl = value
        prefs.edit().putInt("custom_quick_ml", customQuickMl).apply()
        rebuildQuickButtons()
    }

    // ─── Quick button builders ────────────────────────────────────

    private fun rebuildQuickButtons() {
        val dp = resources.displayMetrics.density

        binding.containerQuickAdd.removeAllViews()
        addSortedItems().forEach { (ml, isCustom) ->
            val iconRes = AMOUNT_ICONS[ml] ?: R.drawable.ic_water_drop
            val btn = if (isCustom && ml == 0) makeCustomSlotButton(dp)
                      else makeAmountButton("+${ml.fmt(unit)}", ADD_BG, ADD_FG, dp,
                               iconRes = iconRes,
                               onClick = { if (isCustom) addWater(customQuickMl) else addWater(ml) },
                               onLongClick = if (isCustom) ::showCustomValueDialog else null)
            btn.tag = if (isCustom && ml == 0) TAG_CUSTOM_SLOT else ml
            binding.containerQuickAdd.addView(btn)
        }

        binding.containerQuickRemove.removeAllViews()
        removeSortedItems().forEach { (ml, _) ->
            val iconRes = AMOUNT_ICONS[ml] ?: R.drawable.ic_water_drop
            binding.containerQuickRemove.addView(
                makeAmountButton("-${ml.fmt(unit)}", REM_BG, REM_FG, dp,
                    iconRes = iconRes, onClick = { removeWater(ml) })
            )
        }
    }

    /** Add row: circle "+" first when no value; otherwise sorted by ml. */
    private fun addSortedItems(): List<Pair<Int, Boolean>> {
        val fixed = FIXED_ML.map { it to false }
        return when {
            customQuickMl == 0          -> listOf(0 to true) + fixed
            customQuickMl !in FIXED_ML  -> (fixed + (customQuickMl to true)).sortedBy { it.first }
            else                        -> fixed + (customQuickMl to true)
        }
    }

    /** Remove row: fixed values + custom (only when set and unique). */
    private fun removeSortedItems(): List<Pair<Int, Boolean>> {
        val fixed = FIXED_ML.map { it to false }
        return if (customQuickMl > 0 && customQuickMl !in FIXED_ML)
            (fixed + (customQuickMl to true)).sortedBy { it.first }
        else fixed
    }

    /** Button for a specific ml amount — used in both add and remove rows. */
    private fun makeAmountButton(
        label: String, bgHex: String, fgHex: String, dp: Float,
        iconRes: Int = R.drawable.ic_water_drop,
        onClick: () -> Unit, onLongClick: (() -> Unit)? = null
    ) = MaterialButton(requireContext()).apply {
        text = label
        setTextColor(Color.parseColor(fgHex))
        backgroundTintList = ColorStateList.valueOf(Color.parseColor(bgHex))
        cornerRadius = (24 * dp).toInt()
        insetTop = 0; insetBottom = 0
        textSize = 11f
        isSingleLine = true
        setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        setIconResource(iconRes)
        iconTint = ColorStateList.valueOf(Color.parseColor(fgHex))
        iconPadding = (2 * dp).toInt()
        iconSize = (12 * dp).toInt()
        iconGravity = ICON_GRAVITY_TEXT_START
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = (8 * dp).toInt() }
        setOnClickListener { onClick() }
        onLongClick?.let { lc -> setOnLongClickListener { lc(); true } }
    }

    /** Placeholder shown in the add row when no custom value has been set yet. */
    private fun makeCustomSlotButton(dp: Float): MaterialButton {
        val size = (40 * dp).toInt()
        return MaterialButton(requireContext()).apply {
            text = "+"
            setTextColor(Color.parseColor(ADD_FG))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor(ADD_BG))
            cornerRadius = size / 2
            insetTop = 0; insetBottom = 0
            textSize = 16f
            isSingleLine = true
            setPadding(0, 0, 0, 0)
            iconSize = 0
            layoutParams = LinearLayout.LayoutParams(size, size)
                .apply { marginEnd = (8 * dp).toInt() }
            setOnClickListener { showCustomValueDialog() }
            setOnLongClickListener { showCustomValueDialog(); true }
        }
    }

    private fun showCustomValueDialog() {
        val editText = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                if (unit == UNIT_CL) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
            hint = "Amount in $unit (e.g. ${180.toDisplay(unit)})"
            setPadding(48, 32, 48, 32)
            if (customQuickMl > 0) setText(customQuickMl.toDisplay(unit))
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Custom amount")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val ml = editText.text.toString().toMl(unit)
                if (ml != null && ml > 0) {
                    applyCustomQuickMl(ml)
                }
            }
            .apply {
                if (customQuickMl > 0) {
                    setNeutralButton("Clear") { _, _ -> applyCustomQuickMl(0) }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                        "Each bottle fills as you reach a quarter of your $goalMl ml goal."
                    ).style(radius = 110)
                },
                {
                    findQuickAdd(200)?.let {
                        TapTarget.forView(
                            it, "Small sips",
                            "Glass icons are small amounts: 100 ml (half) and 200 ml (full). Tap to log instantly."
                        ).style()
                    }
                },
                {
                    findQuickAdd(250)?.let {
                        TapTarget.forView(
                            it, "Bigger drinks",
                            "Drop icons are larger amounts (250–500 ml)."
                        ).style()
                    }
                },
                {
                    findQuickAdd(TAG_CUSTOM_SLOT)?.let {
                        TapTarget.forView(
                            it, "Your own amount",
                            "Long-press the + button to set a custom quick-add value."
                        ).style()
                    }
                },
                {
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
                    activity.findViewById<View>(R.id.fab)?.let {
                        TapTarget.forView(
                            it, "Reminders",
                            "Tap here, then the bell, to set up reminder times."
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
        _binding = null
    }
}
