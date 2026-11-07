package com.david.waterreminder

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import java.io.File
import java.io.FileOutputStream
import com.david.waterreminder.databinding.DialogPickListBinding
import com.david.waterreminder.databinding.FragmentAnalyticsBinding
import com.david.waterreminder.databinding.ItemPickDayBinding
import com.david.waterreminder.databinding.ItemStatCardBinding
import com.google.android.material.color.MaterialColors
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val goalMl = DEFAULT_DAILY_GOAL_ML
    private val dayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    private val fullDayFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
    private val prefs by lazy {
        requireContext().waterPrefs()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val unit get() = prefs.getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        render(animate = true)

        binding.textViewAllHistory.setOnClickListener {
            findNavController().navigate(R.id.action_AnalyticsFragment_to_AllHistoryFragment)
        }

        binding.btnShare.setOnClickListener { shareWeeklyCard() }
        binding.fabEditDay.setOnClickListener { showDayPicker() }
    }

    /**
     * Chart + stats, rebuilt from prefs — also how the screen refreshes after a day is edited.
     * [animate] is for the first pass only: growing the bars out of the floor again on every edit
     * reads as a reload rather than as the one value changing.
     */
    private fun render(animate: Boolean = false) {
        // A day that ended while this screen was open still has its total sitting in today's
        // counter until something files it away; do that before reading any of it back.
        prefs.rollOverDayIfNeeded()

        val personalGoal = prefs.getInt("personal_goal_ml", 0)
        setupChart(loadHistory(), personalGoal, animate)
        setupStats(prefs.loadAllHistory(), personalGoal)
    }

    // ─── Editing a day ────────────────────────────────────────────

    /**
     * The editable days as one tappable row each — only the six finished days of the week on show
     * can be changed, so the whole choice fits without a date picker. A row is date, total, and a
     * drop that fills in on days that reached the minimum: the chart's reading, one tap from the
     * field that changes it.
     */
    private fun showDayPicker() {
        // Cheap and idempotent, and this can open hours after the screen was drawn: the day may
        // have turned over in between, which would shift what "the last six days" even means.
        prefs.rollOverDayIfNeeded()

        val palette = DayRowPalette(requireContext(), requireView())
        val inflater = LayoutInflater.from(requireContext())
        val pickerBinding = DialogPickListBinding.inflate(inflater)
        pickerBinding.textHint.text = getString(R.string.analytics_pick_day_hint)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.analytics_pick_day_title))
            .setView(pickerBinding.root)
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .create()

        editableHistoryDays().forEach { date ->
            val row = ItemPickDayBinding.inflate(inflater, pickerBinding.containerItems, true)
            bindDayRow(row, date, palette)
            row.root.setOnClickListener {
                dialog.dismiss()
                showDayTotalDialog(date)
            }
        }
        dialog.show()
    }

    /** An untracked day reads as 0, the same as a day that was tracked and simply came to nothing. */
    private fun bindDayRow(row: ItemPickDayBinding, date: LocalDate, palette: DayRowPalette) {
        val ml = prefs.dayTotal(date)
        val goalMet = ml >= goalMl
        val total = ml.fmt(unit)

        row.textDayDate.text = date.format(dayFormatter)
        row.textDayTotal.text = total
        row.textDayTotal.setTextColor(if (ml > 0) palette.onSurface else palette.muted)
        row.iconDayGoalMet.visibility = if (goalMet) View.VISIBLE else View.INVISIBLE

        // Blue for a day that got there, pale blue for one that didn't, grey for an empty day.
        row.iconDay.imageTintList = when {
            goalMet -> palette.goalMetTint
            ml > 0 -> palette.partialTint
            else -> palette.mutedTint
        }
        // The tick has to be spoken here: a description on the row stops TalkBack reading its
        // children, so an icon-only signal would be silent for anyone using a screen reader.
        row.root.contentDescription = getString(
            if (goalMet) R.string.analytics_pick_day_row_description_goal_met
            else R.string.analytics_pick_day_row_description,
            date.format(fullDayFormatter),
            total
        )
    }

    /** Total for one day: prefilled with what's stored, with a Clear button when there is a record. */
    private fun showDayTotalDialog(date: LocalDate) {
        val storedMl = prefs.storedDayTotal(date)
        val editText = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                if (isDecimalUnit(unit)) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
            hint = getString(R.string.analytics_edit_day_hint, unit, goalMl.toDisplay(unit))
            setPadding(48, 32, 48, 32)
            if (storedMl != null) { setText(storedMl.toDisplay(unit)); selectAll() }
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.analytics_edit_day_title, date.format(fullDayFormatter)))
            .setView(editText)
            .setPositiveButton(getString(R.string.dialog_save), null)
            .apply {
                if (storedMl != null) {
                    setNeutralButton(getString(R.string.dialog_clear)) { _, _ -> clearDay(date) }
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val ml = editText.text.toString().toValidDayTotalMl(unit)
            if (ml == null) {
                editText.error = getString(
                    R.string.analytics_edit_day_error, 0.fmt(unit), DAY_TOTAL_MAX_ML.fmt(unit)
                )
            } else {
                prefs.writeDayTotal(date, ml)
                requestHealthConnectDaySync(requireContext(), date, ml)
                afterDayEdit(getString(
                    R.string.analytics_day_saved, date.format(fullDayFormatter), ml.fmt(unit)
                ))
                dialog.dismiss()
            }
        }
        editText.requestFocus()
    }

    private fun clearDay(date: LocalDate) {
        prefs.removeDayTotal(date)
        requestHealthConnectDaySync(requireContext(), date, ml = null)
        afterDayEdit(getString(R.string.analytics_day_cleared, date.format(fullDayFormatter)))
    }

    private fun afterDayEdit(message: String) {
        // No BackupManager call here: MainActivity keeps a listener on water_prefs for the whole
        // time it's started and reports every change, this one included.
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        render()
    }

    private fun shareWeeklyCard() {
        val view = binding.analyticsContent
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(MaterialColors.getColor(view, R.attr.pageBackground))
        view.draw(canvas)

        val shareDir = File(requireContext().cacheDir, "share")
        shareDir.mkdirs()
        val file = File(shareDir, "weekly_history.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }

        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_weekly_progress_text))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)))
    }

    // ─── Data ─────────────────────────────────────────────────────

    private fun loadHistory(): List<Pair<LocalDate, Int>> {
        val today = LocalDate.now()
        return (WEEKLY_HISTORY_DAYS - 1 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            date to prefs.dayTotal(date)   // today comes from the live counter, the rest from their records
        }
    }

    // ─── Chart ────────────────────────────────────────────────────

    /**
     * The week as rounded bars on a bare panel: full accent for a day that reached the minimum, a
     * faint accent for one that didn't, no value axis or grid, the goals as quiet dashed lines.
     */
    private fun setupChart(history: List<Pair<LocalDate, Int>>, personalGoal: Int, animate: Boolean) {
        val ctx = requireContext()
        val accent = ctx.getColor(R.color.brand_blue)
        val partial = ColorUtils.setAlphaComponent(accent, 0x59)
        val muted = ctx.getColor(R.color.text_muted)
        val secondary = ctx.getColor(R.color.text_secondary)
        val geist = ResourcesCompat.getFont(ctx, R.font.geist_regular)
        val geistBold = ResourcesCompat.getFont(ctx, R.font.geist_bold)
        val density = resources.displayMetrics.density

        val entries = history.mapIndexed { index, (_, ml) ->
            BarEntry(index.toFloat(), ml.toFloat())
        }

        val dataSet = BarDataSet(entries, "Water intake ($unit)").apply {
            colors = history.map { (_, ml) -> if (ml >= goalMl) accent else partial }
            valueTextSize = 10f
            valueTextColor = secondary
            valueTypeface = geist
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) =
                    if (value > 0f) value.toInt().toDisplay(unit) else ""
            }
        }

        fun goalLine(ml: Int, label: String) = LimitLine(ml.toFloat(), label).apply {
            lineColor = ColorUtils.setAlphaComponent(muted, 0x80)
            lineWidth = 1f
            textColor = muted
            textSize = 9f
            typeface = geistBold
            enableDashedLine(6f * density, 4f * density, 0f)
        }

        binding.barChart.apply {
            renderer = RoundedBarChartRenderer(this, animator, viewPortHandler, 8f * density)
            data = BarData(dataSet).apply { barWidth = 0.55f }
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setDrawGridBackground(false)
            setDrawBorders(false)
            setNoDataText("No data yet")
            setNoDataTextColor(muted)
            extraBottomOffset = 6f

            val maxIntake = history.maxOfOrNull { it.second } ?: 0
            axisLeft.apply {
                removeAllLimitLines()   // render() runs again after an edit
                addLimitLine(goalLine(goalMl, getString(R.string.analytics_chart_minimum)))
                if (personalGoal > 0) {
                    addLimitLine(goalLine(personalGoal, getString(R.string.analytics_chart_personal)))
                }
                setDrawLimitLinesBehindData(true)
                axisMinimum = 0f
                axisMaximum = maxOf(goalMl, personalGoal, maxIntake) * 1.2f
                setDrawLabels(false)
                setDrawGridLines(false)
                setDrawAxisLine(false)
            }
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                setDrawAxisLine(false)
                textColor = muted
                textSize = 11f
                typeface = geist
                yOffset = 8f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val idx = value.toInt().coerceIn(0, history.size - 1)
                        return if (idx == history.lastIndex) getString(R.string.analytics_chart_today)
                        else history[idx].first.format(weekdayFormatter)
                    }
                }
            }

            if (animate) animateY(600)
            invalidate()
        }
    }

    // ─── Stats ────────────────────────────────────────────────────

    /** Whole-history numbers, one card each: average of tracked days, best day, days at goal. */
    private fun setupStats(history: List<Pair<LocalDate, Int>>, personalGoal: Int) {
        val nonZero = history.filter { it.second > 0 }
        val avg = if (nonZero.isNotEmpty()) nonZero.sumOf { it.second } / nonZero.size else 0
        val best = history.maxOfOrNull { it.second } ?: 0
        val goalDays = history.count { it.second >= goalMl }

        bindStat(binding.statAvg, R.string.analytics_stat_avg, avg.toDisplay(unit), unit)
        bindStat(binding.statBest, R.string.analytics_stat_best, best.toDisplay(unit), unit)
        bindStat(binding.statGoalDays, R.string.analytics_stat_goal_days, goalDays.toString(), daysUnit(goalDays))

        binding.statPersonalGoalDays.root.visibility = if (personalGoal > 0) View.VISIBLE else View.GONE
        if (personalGoal > 0) {
            val personalDays = history.count { it.second >= personalGoal }
            bindStat(
                binding.statPersonalGoalDays, R.string.analytics_stat_personal_days,
                personalDays.toString(), daysUnit(personalDays)
            )
        }
    }

    private fun bindStat(card: ItemStatCardBinding, labelRes: Int, value: String, unitText: String) {
        card.statLabel.text = getString(labelRes)
        card.statValue.text = value
        card.statUnit.text = unitText
    }

    private fun daysUnit(count: Int) = resources.getQuantityString(R.plurals.analytics_days_unit, count)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Theme colours the day-picker rows need, resolved once per dialog instead of once per row — and
 * per dialog rather than cached on the fragment, so a theme switch can't leave stale colours behind.
 */
private class DayRowPalette(context: Context, view: View) {
    val onSurface = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)
    val muted = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant)
    val goalMetTint: ColorStateList = ColorStateList.valueOf(context.getColor(R.color.brand_blue))
    val partialTint: ColorStateList = ColorStateList.valueOf(context.getColor(R.color.brand_blue_light))
    val mutedTint: ColorStateList = ColorStateList.valueOf(muted)
}
