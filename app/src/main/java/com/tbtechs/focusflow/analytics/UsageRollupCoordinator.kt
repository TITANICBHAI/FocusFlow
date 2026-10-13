package com.tbtechs.focusflow.analytics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Event-driven rollup triggers. It deliberately has no timer or polling loop.
 */
class UsageRollupCoordinator(
    context: Context,
    private val applicationScope: CoroutineScope,
    private val writer: UsageRollupWriter,
) {
    private val appContext = context.applicationContext
    private val receiverRegistered = AtomicBoolean(false)
    private val dateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (
                intent?.action == Intent.ACTION_DATE_CHANGED ||
                intent?.action == Intent.ACTION_TIME_CHANGED ||
                intent?.action == Intent.ACTION_TIMEZONE_CHANGED
            ) {
                trigger("calendar_changed")
            }
        }
    }

    fun registerCalendarChangeReceiver() {
        if (!receiverRegistered.compareAndSet(false, true)) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(dateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(dateReceiver, filter)
        }
    }

    fun trigger(reason: String) {
        applicationScope.launch {
            runCatching { writer.writeRecentPastDays() }
                .onSuccess { result ->
                    Log.d(TAG, "rollup trigger=$reason result=$result")
                }
                .onFailure { error ->
                    Log.w(TAG, "rollup trigger=$reason failed", error)
                }
        }
    }

    companion object {
        private const val TAG = "UsageRollupCoordinator"
    }
}
