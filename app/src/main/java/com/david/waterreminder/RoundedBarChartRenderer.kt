package com.david.waterreminder

import android.graphics.Canvas
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.renderer.BarChartRenderer
import com.github.mikephil.charting.utils.ViewPortHandler

/**
 * MPAndroidChart's bar renderer with rounded corners, which the library doesn't offer. Only the
 * bars themselves are redrawn; values, limit lines and axes still come from the base renderer.
 * Shadows, borders and highlights are left out because the History chart uses none of them.
 */
class RoundedBarChartRenderer(
    chart: BarDataProvider,
    animator: ChartAnimator,
    viewPortHandler: ViewPortHandler,
    private val radiusPx: Float
) : BarChartRenderer(chart, animator, viewPortHandler) {

    override fun drawDataSet(c: Canvas, dataSet: IBarDataSet, index: Int) {
        val transformer = mChart.getTransformer(dataSet.axisDependency)
        val buffer = mBarBuffers[index].apply {
            setPhases(mAnimator.phaseX, mAnimator.phaseY)
            setDataSet(index)
            setInverted(mChart.isInverted(dataSet.axisDependency))
            setBarWidth(mChart.barData.barWidth)
            feed(dataSet)
        }
        transformer.pointValuesToPixel(buffer.buffer)

        val rects = buffer.buffer
        for (j in 0 until buffer.size() step 4) {
            val left = rects[j]
            val top = rects[j + 1]
            val right = rects[j + 2]
            val bottom = rects[j + 3]
            if (!mViewPortHandler.isInBoundsLeft(right)) continue
            if (!mViewPortHandler.isInBoundsRight(left)) break
            mRenderPaint.color = dataSet.getColor(j / 4)
            c.drawRoundRect(left, top, right, bottom, radiusPx, radiusPx, mRenderPaint)
        }
    }
}
