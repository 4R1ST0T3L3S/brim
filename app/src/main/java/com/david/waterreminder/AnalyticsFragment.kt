package com.david.waterreminder

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.david.waterreminder.databinding.FragmentAnalyticsBinding
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val goalMl = 1500
    private val dayFormatter = DateTimeFormatter.ofPattern("MMM d")
    private val prefs by lazy {
        requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val unit get() = prefs.getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val history = loadHistory()
        val personalGoal = prefs.getInt("personal_goal_ml", 0)
        setupChart(history, personalGoal)
        setupStats(history, personalGoal)
    }

    // ─── Data ─────────────────────────────────────────────────────

    private fun loadHistory(): List<Pair<LocalDate, Int>> {
        val prefs = requireContext().getSharedPreferences("water_prefs", Context.MODE_PRIVATE)
        val today = LocalDate.now()

        return (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val key = "history_${date}"
            // Include today's live intake too
            val ml = if (daysAgo == 0) {
                prefs.getInt("intake_ml", 0)
            } else {
                prefs.getInt(key, 0)
            }
            Pair(date, ml)
        }
    }

    // ─── Chart ────────────────────────────────────────────────────

    private fun setupChart(history: List<Pair<LocalDate, Int>>, personalGoal: Int) {
        val entries = history.mapIndexed { index, (_, ml) ->
            BarEntry(index.toFloat(), ml.toFloat())
        }

        val dataSet = BarDataSet(entries, "Water intake ($unit)").apply {
            colors = history.map { (_, ml) ->
                if (ml >= goalMl) Color.parseColor("#2196F3") else Color.parseColor("#90CAF9")
            }
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) =
                    if (value > 0f) value.toInt().fmt(unit) else ""
            }
        }

        binding.barChart.apply {
            data = BarData(dataSet).apply { barWidth = 0.6f }
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setDrawGridBackground(false)
            setNoDataText("No data yet")

            // Daily goal line (red)
            val goalLine = LimitLine(goalMl.toFloat(), "Goal").apply {
                lineColor = Color.parseColor("#F44336")
                lineWidth = 1.5f
                textColor = Color.parseColor("#F44336")
                textSize = 10f
                enableDashedLine(10f, 5f, 0f)
            }

            // Personal goal line (orange) — only when set
            val personalGoalLine = if (personalGoal > 0) {
                LimitLine(personalGoal.toFloat(), "Personal").apply {
                    lineColor = Color.parseColor("#FF6F00")
                    lineWidth = 1.5f
                    textColor = Color.parseColor("#FF6F00")
                    textSize = 10f
                    enableDashedLine(6f, 4f, 0f)
                }
            } else null

            val axisMax = maxOf(goalMl, personalGoal).coerceAtLeast(goalMl) * 1.4f

            axisLeft.apply {
                addLimitLine(goalLine)
                personalGoalLine?.let { addLimitLine(it) }
                axisMinimum = 0f
                axisMaximum = axisMax
                gridColor = Color.parseColor("#F0F0F0")
                textColor = Color.parseColor("#888888")
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = value.toInt().fmt(unit)
                }
            }
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textColor = Color.parseColor("#888888")
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val idx = value.toInt().coerceIn(0, history.size - 1)
                        return history[idx].first.format(dayFormatter)
                    }
                }
            }

            animateY(600)
            invalidate()
        }
    }

    // ─── Stats ────────────────────────────────────────────────────

    private fun setupStats(history: List<Pair<LocalDate, Int>>, personalGoal: Int) {
        val nonZero = history.filter { it.second > 0 }
        val avg = if (nonZero.isNotEmpty()) nonZero.sumOf { it.second } / nonZero.size else 0
        val best = history.maxOfOrNull { it.second } ?: 0
        val goalDays = history.count { it.second >= goalMl }

        binding.textAvg.text = "Avg daily intake:  ${avg.fmt(unit)}"
        binding.textBest.text = "Best day:  ${best.fmt(unit)}"
        binding.textGoalDays.text = "Goal reached:  $goalDays / 7 days"

        if (personalGoal > 0) {
            val personalDays = history.count { it.second >= personalGoal }
            binding.textPersonalGoalDays.text = "Personal goal reached:  $personalDays / 7 days"
            binding.textPersonalGoalDays.visibility = View.VISIBLE
        } else {
            binding.textPersonalGoalDays.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
