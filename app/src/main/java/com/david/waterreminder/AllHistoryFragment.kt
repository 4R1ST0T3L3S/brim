package com.david.waterreminder

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.File
import java.io.FileOutputStream
import com.david.waterreminder.databinding.FragmentAllHistoryBinding
import com.google.android.material.color.MaterialColors
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class AllHistoryFragment : Fragment() {

    private var _binding: FragmentAllHistoryBinding? = null
    private val binding get() = _binding!!

    private val prefs by lazy {
        requireContext().waterPrefs()
    }

    private val unit get() = prefs.getString(PREF_UNIT, UNIT_ML) ?: UNIT_ML

    private val labelFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val markerFormatter = DateTimeFormatter.ofPattern("EEE, MMM d yyyy", Locale.ENGLISH)

    companion object {
        private const val GOAL_ML = 1500
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val history = prefs.loadAllHistory()
        val nonZero = history.filter { it.second > 0 }
        val avg = if (nonZero.isNotEmpty()) nonZero.sumOf { it.second } / nonZero.size else 0

        binding.textTotalDays.text = "Days tracked: ${history.size}"
        binding.textOverallAvg.text = "Overall average: ${avg.fmt(unit)}"

        setupChart(history)

        binding.btnShare.setOnClickListener { shareHistoryCard() }
    }

    private fun shareHistoryCard() {
        val view = binding.allHistoryContent
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(MaterialColors.getColor(view, R.attr.pageBackground))
        view.draw(canvas)

        val shareDir = File(requireContext().cacheDir, "share")
        shareDir.mkdirs()
        val file = File(shareDir, "all_history.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }

        val uri = FileProvider.getUriForFile(
            requireContext(),
            "com.david.waterreminder.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_all_history_text))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)))
    }

    private fun setupChart(history: List<Pair<LocalDate, Int>>) {
        val personalGoal = prefs.getInt("personal_goal_ml", 0)
        val maxData = history.maxOfOrNull { it.second } ?: 0

        val chartEntries = history.mapIndexed { index, (_, ml) ->
            Entry(index.toFloat(), ml.toFloat())
        }

        val fillGradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(90, 33, 150, 243), Color.argb(0, 33, 150, 243))
        )

        val dataSet = LineDataSet(chartEntries, "").apply {
            color = Color.parseColor("#2196F3")
            setCircleColor(Color.parseColor("#2196F3"))
            circleHoleColor = Color.WHITE
            // Show dots only when there are few enough points to be readable
            circleRadius = if (history.size > 60) 0f else 4f
            circleHoleRadius = if (history.size > 60) 0f else 2f
            lineWidth = 2.5f
            valueTextSize = 0f
            mode = LineDataSet.Mode.LINEAR
            setDrawFilled(true)
            fillDrawable = fillGradient
            highLightColor = Color.parseColor("#2196F3")
            highlightLineWidth = 1.2f
            enableDashedHighlightLine(8f, 4f, 0f)
            setDrawHighlightIndicators(true)
            isHighlightEnabled = true
        }

        binding.lineChartAll.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false
            setDrawGridBackground(false)
            setNoDataText("No data yet")
            setExtraOffsets(8f, 16f, 8f, 8f)

            // Tap marker
            marker = HistoryMarkerView(requireContext(), history, unit, markerFormatter)
                .also { it.chartView = this }

            axisLeft.apply {
                removeAllLimitLines()
                addLimitLine(LimitLine(GOAL_ML.toFloat(), "Recommended Minimum").apply {
                    lineColor = Color.parseColor("#F44336")
                    lineWidth = 1.5f
                    textColor = Color.parseColor("#F44336")
                    textSize = 10f

                    enableDashedLine(10f, 5f, 0f)
                    labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                })
                if (personalGoal > 0) {
                    addLimitLine(LimitLine(personalGoal.toFloat(), "Personal").apply {
                        lineColor = Color.parseColor("#FF6F00")
                        lineWidth = 1.5f
                        textColor = Color.parseColor("#FF6F00")
                        textSize = 10f
    
                        enableDashedLine(6f, 4f, 0f)
                        labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                    })
                }
                axisMinimum = 0f
                axisMaximum = maxOf(GOAL_ML, personalGoal, maxData) * 1.25f
                gridColor = Color.parseColor("#F0F0F0")
                gridLineWidth = 0.8f
                textColor = Color.parseColor("#888888")
                textSize = 12f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = value.toInt().fmt(unit)
                }
            }
            axisRight.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setLabelCount(6, false)
                setDrawGridLines(false)
                textColor = Color.parseColor("#888888")
                textSize = 12f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val idx = value.toInt().coerceIn(0, history.size - 1)
                        return history[idx].first.format(labelFormatter)
                    }
                }
            }

            if (history.size > 30) {
                setVisibleXRangeMaximum(30f)
                moveViewToX((history.size - 1).toFloat())
            }

            animateY(800)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class HistoryMarkerView(
    context: Context,
    private val history: List<Pair<LocalDate, Int>>,
    private val unit: String,
    private val formatter: DateTimeFormatter
) : MarkerView(context, R.layout.marker_history) {

    private val tvDate: TextView = findViewById(R.id.marker_date)
    private val tvValue: TextView = findViewById(R.id.marker_value)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        val idx = e?.x?.toInt()?.coerceIn(0, history.size - 1) ?: return
        val (date, ml) = history[idx]
        tvDate.text = date.format(formatter)
        tvValue.text = ml.fmt(unit)
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF =
        MPPointF(-(width / 2f), -height.toFloat() - 16f)
}
