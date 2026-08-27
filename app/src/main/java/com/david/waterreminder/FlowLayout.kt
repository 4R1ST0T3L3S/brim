package com.david.waterreminder

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * Lays children out left to right, wrapping when the next one no longer fits.
 *
 * The rows are centred as one block rather than individually: the widest row decides the block,
 * and every row starts at its left edge. So the group sits centred in the view while the buttons
 * still line up with each other down the left, instead of each short row drifting to its own
 * centre. Measure and layout share [buildRows], so the height reserved for the rows can never
 * disagree with the rows actually placed.
 */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private val View.widthWithMargins: Int
        get() = (layoutParams as MarginLayoutParams)
            .let { measuredWidth + it.leftMargin + it.rightMargin }

    private val View.heightWithMargins: Int
        get() = (layoutParams as MarginLayoutParams)
            .let { measuredHeight + it.topMargin + it.bottomMargin }

    /**
     * Width a laid-out [row] occupies. The last child's end margin is a trailing gap rather than
     * content, so it is left out — counting it would push the block half a gap off centre.
     */
    private fun rowWidth(row: List<View>): Int =
        row.sumOf { it.widthWithMargins } - (row.last().layoutParams as MarginLayoutParams).rightMargin

    /** Visible children grouped into rows that each fit [availableWidth]; measure them first. */
    private fun buildRows(availableWidth: Int): List<List<View>> {
        val rows = mutableListOf<List<View>>()
        var currentRow = mutableListOf<View>()
        var rowWidth = 0

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue
            if (rowWidth + child.widthWithMargins > availableWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                rowWidth = 0
            }
            currentRow.add(child)
            rowWidth += child.widthWithMargins
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)
        return rows
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
        }

        val rowsHeight = buildRows(availableWidth).sumOf { row -> row.maxOf { it.heightWithMargins } }
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(rowsHeight + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val availableWidth = r - l - paddingLeft - paddingRight
        val rows = buildRows(availableWidth)
        // One shared left edge for every row, placed so the widest row is centred.
        val blockLeft = paddingLeft +
            ((availableWidth - (rows.maxOfOrNull(::rowWidth) ?: 0)) / 2).coerceAtLeast(0)
        var rowTop = paddingTop

        for (row in rows) {
            val rowHeight = row.maxOf { it.heightWithMargins }
            var cursorX = blockLeft

            for (child in row) {
                val margins = child.layoutParams as MarginLayoutParams
                val childTop = rowTop + margins.topMargin +
                    (rowHeight - margins.topMargin - margins.bottomMargin - child.measuredHeight) / 2
                child.layout(
                    cursorX + margins.leftMargin,
                    childTop,
                    cursorX + margins.leftMargin + child.measuredWidth,
                    childTop + child.measuredHeight
                )
                cursorX += child.widthWithMargins
            }
            rowTop += rowHeight
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet?) = MarginLayoutParams(context, attrs)
    override fun generateDefaultLayoutParams() = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(p: LayoutParams?) = MarginLayoutParams(p)
    override fun checkLayoutParams(p: LayoutParams?) = p is MarginLayoutParams
}
