package com.tbtechs.focusflow.enforcement

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration

/**
 * The one widget host id FocusFlow uses. Android ties each hosted widget to the host that
 * allocated it, so the launcher and the "Add to Home screen" screen must use the same id.
 */
object HomeWidgetHost {
    private const val HOST_ID = 0x464F4357

    fun create(context: Context): AppWidgetHost = LongPressHost(context.applicationContext)

    private class LongPressHost(context: Context) : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?,
        ): AppWidgetHostView = LongPressHostView(context)
    }
}

/**
 * Widgets such as the system clock have clickable areas inside them. Those areas take the touch,
 * so a long-press listener on the widget itself never fires. This view runs its own timer: if the
 * finger stays down and still for the long-press time, it runs the long-click and takes the touch
 * away from the widget so the widget does not also open its app.
 */
internal class LongPressHostView(context: Context) : AppWidgetHostView(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val check = Runnable {
        if (hasOnLongClickListeners()) {
            fired = true
            performLongClick()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                downX = ev.x
                downY = ev.y
                removeCallbacks(check)
                postDelayed(check, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (Math.abs(ev.x - downX) > slop || Math.abs(ev.y - downY) > slop) removeCallbacks(check)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(check)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        fired || super.onInterceptTouchEvent(ev)

    override fun onTouchEvent(event: MotionEvent): Boolean =
        if (fired) true else super.onTouchEvent(event)

    override fun onDetachedFromWindow() {
        removeCallbacks(check)
        super.onDetachedFromWindow()
    }
}
