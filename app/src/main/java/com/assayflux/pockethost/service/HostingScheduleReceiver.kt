package com.assayflux.pockethost.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives daily start/stop alarms and schedules the following day's boundaries. */
class HostingScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        HostingSchedule.handleAlarm(context, intent?.action)
    }
}
