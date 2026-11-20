package com.tbtechs.focusflow.enforcement

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * The grid home screen's canvas: a fixed number of equal cells that fill the space it is given, and
 * children that each cover a block of them (see [HomeGrid] for the rules about where things may
 * sit). It does no dragging and keeps no list of what is on it. The activity adds one child per
 * [HomeGrid.Item], each with [Params] saying which cells it covers.
 */
class HomeGridLayout(context: Context) : ViewGroup(context) {
    var columns: Int = HomeGrid.DEFAULT_COLUMNS
        set(value) {
            val v = value.coerceAtLeast(1)
            if (field != v) {
                field = v
                requestLayout()
            }
        }

    var rows: Int = HomeGrid.DEFAULT_ROWS
        set(value) {
            val v = value.coerceAtLeast(1)
            if (field != v) {
                field = v
                requestLayout()
            }
        }

    /** Space kept free around every child so neighbours do not touch. */
    var insetPx: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    /** Width of one cell in pixels, as of the last measure. */
    var cellWidth: Int = 0
        private set

    /** Height of one cell in pixels, as of the last measure. */
    var cellHeight: Int = 0
        private set

    class Params(
        @JvmField var col: Int,
        @JvmField var row: Int,
        @JvmField var spanX: Int = 1,
        @JvmField var spanY: Int = 1,
    ) : ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is Params

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = Params(0, 0)

    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams = Params(0, 0)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        cellWidth = ((w - paddingLeft - paddingRight) / columns).coerceAtLeast(0)
        cellHeight = ((h - paddingTop - paddingBottom) / rows).coerceAtLeast(0)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val p = child.layoutParams as Params
            val cw = (p.spanX * cellWidth - 2 * insetPx).coerceAtLeast(0)
            val ch = (p.spanY * cellHeight - 2 * insetPx).coerceAtLeast(0)
            child.measure(
                MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val p = child.layoutParams as Params
            val left = paddingLeft + p.col * cellWidth + insetPx
            val top = paddingTop + p.row * cellHeight + insetPx
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
        }
    }
}
