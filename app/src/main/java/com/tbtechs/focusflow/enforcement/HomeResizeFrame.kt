package com.tbtechs.focusflow.enforcement

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * The outline and four edge handles drawn over a grid item while it is being resized. It only
 * draws: touches pass straight through it to the item underneath, and the activity decides which
 * edge a touch grabbed (see [HomeGrid.edgeHit]).
 */
class HomeResizeFrame(context: Context, private val accent: Int) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = accent
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val handleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = accent
    }
    private val rect = RectF()

    init {
        // Nothing here takes touches: they reach whatever is under the frame.
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        val inset = 1.5f * density
        rect.set(inset, inset, width - inset, height - inset)
        val radius = 16f * density
        canvas.drawRoundRect(rect, radius, radius, stroke)
        val r = 7f * density
        val pad = r + 3f * density
        val cx = width / 2f
        val cy = height / 2f
        for ((x, y) in listOf(pad to cy, width - pad to cy, cx to pad, cx to height - pad)) {
            canvas.drawCircle(x, y, r, handleFill)
            canvas.drawCircle(x, y, r, handleRing)
        }
    }
}
