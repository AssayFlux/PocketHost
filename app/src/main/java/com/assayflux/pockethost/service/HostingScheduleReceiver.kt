package com.assayflux.pockethost.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives daily alarms and re-aligns them after system clock or timezone changes. */
class HostingScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED ->
                HostingSchedule.onTimeChanged(context)
            else -> HostingSchedule.handleAlarm(context, intent?.action)
        }
    }
}
