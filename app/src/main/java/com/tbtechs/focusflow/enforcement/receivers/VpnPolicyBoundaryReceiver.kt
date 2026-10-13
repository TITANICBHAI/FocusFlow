package com.tbtechs.focusflow.enforcement.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tbtechs.focusflow.enforcement.VpnPolicyCoordinator
import com.tbtechs.focusflow.enforcement.VpnPolicyBoundaryScheduler

class VpnPolicyBoundaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            VpnPolicyBoundaryScheduler.ACTION_BOUNDARY,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> VpnPolicyCoordinator.requestSync(context)
        }
    }

    companion object {
        private const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
