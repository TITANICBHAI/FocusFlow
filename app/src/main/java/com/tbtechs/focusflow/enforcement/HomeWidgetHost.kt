package com.tbtechs.focusflow.enforcement

import android.appwidget.AppWidgetHost
import android.content.Context

/**
 * The one widget host id FocusFlow uses. Android ties each hosted widget to the host that
 * allocated it, so the launcher and the "Add to Home screen" screen must use the same id.
 */
object HomeWidgetHost {
    private const val HOST_ID = 0x464F4357

    fun create(context: Context): AppWidgetHost = AppWidgetHost(context.applicationContext, HOST_ID)
}
