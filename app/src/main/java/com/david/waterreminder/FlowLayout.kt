package com.david.waterreminder

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup

class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var cursorX = 0
        var cursorY = 0
        var rowHeight = 0

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue

            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, cursorY)
            val margins = child.layoutParams as MarginLayoutParams
            val childWidthWithMargins  = child.measuredWidth  + margins.leftMargin + margins.rightMargin
            val childHeightWithMargins = child.measuredHeight + margins.topMargin  + margins.bottomMargin

            if (cursorX + childWidthWithMargins > availableWidth && cursorX > 0) {
                cursorX = 0
                cursorY += rowHeight
                rowHeight = 0
            }
            cursorX += childWidthWithMargins
            rowHeight = maxOf(rowHeight, childHeightWithMargins)
        }

        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(cursorY + rowHeight + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val availableWidth = r - l - paddingLeft - paddingRight

        // Pass 1: group children into rows
        val rows = mutableListOf<MutableList<Int>>()
        var currentRow = mutableListOf<Int>()
        var cursorX = 0

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue
            val margins = child.layoutParams as MarginLayoutParams
            val childWidthWithMargins = child.measuredWidth + margins.leftMargin + margins.rightMargin

            if (cursorX + childWidthWithMargins > availableWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                cursorX = 0
            }
            currentRow.add(index)
            cursorX += childWidthWithMargins
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)

        // Pass 2: place each child centered vertically within its row
        var rowTop = paddingTop
        for (row in rows) {
            val rowHeight = row.maxOf { index ->
                val margins = getChildAt(index).layoutParams as MarginLayoutParams
                getChildAt(index).measuredHeight + margins.topMargin + margins.bottomMargin
            }
            var cursorX = paddingLeft
            for (index in row) {
                val child = getChildAt(index)
                val margins = child.layoutParams as MarginLayoutParams
                val childWidthWithMargins = child.measuredWidth + margins.leftMargin + margins.rightMargin
                val childTop = rowTop + margins.topMargin +
                    (rowHeight - margins.topMargin - margins.bottomMargin - child.measuredHeight) / 2
                child.layout(
                    cursorX + margins.leftMargin,
                    childTop,
                    cursorX + margins.leftMargin + child.measuredWidth,
                    childTop + child.measuredHeight
                )
                cursorX += childWidthWithMargins
            }
            rowTop += rowHeight
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet?) = MarginLayoutParams(context, attrs)
    override fun generateDefaultLayoutParams() = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(p: LayoutParams?) = MarginLayoutParams(p)
    override fun checkLayoutParams(p: LayoutParams?) = p is MarginLayoutParams
}
