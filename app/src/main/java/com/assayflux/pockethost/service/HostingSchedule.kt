package com.assayflux.pockethost.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.assayflux.pockethost.diagnostics.OperationalLog
import java.time.LocalTime
import java.time.ZonedDateTime

/** Daily local-hosting schedule. Exact alarms are preferred; inexact idle alarms may be delayed. */
object HostingSchedule {
    private const val PREFS = "pockethost_state"
    private const val KEY_ENABLED = "hosting_schedule_enabled"
    private const val KEY_START = "hosting_schedule_start"
    private const val KEY_STOP = "hosting_schedule_stop"
    private const val REQUEST_START = 8101
    private const val REQUEST_STOP = 8102

    data class Settings(val enabled: Boolean, val start: String, val stop: String)

    fun settings(context: Context): Settings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(prefs.getBoolean(KEY_ENABLED, false),
            prefs.getString(KEY_START, "08:00") ?: "08:00",
            prefs.getString(KEY_STOP, "22:00") ?: "22:00")
    }

    fun save(context: Context, enabled: Boolean, start: String, stop: String) {
        require(parse(start) != null) { "Start time must use 24-hour HH:mm format." }
        require(parse(stop) != null) { "Stop time must use 24-hour HH:mm format." }
        require(start != stop) { "Start and stop times must be different." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled).putString(KEY_START, start)
            .putString(KEY_STOP, stop).commit()
        scheduleNext(context)
        if (enabled) applyCurrentWindow(context, settings(context))
        val zone = ZonedDateTime.now().zone.id
        OperationalLog.record(context, "INFO", "HOSTING_SCHEDULE_UPDATED",
            if (enabled) "Daily hosting schedule enabled: ON " + start + ", OFF " + stop + " (" + zone + ")."
            else "Daily hosting schedule disabled.")
    }

    fun onBoot(context: Context) {
        val config = settings(context)
        if (!config.enabled) return
        scheduleNext(context)
        applyCurrentWindow(context, config)
    }

    fun onTimeChanged(context: Context) {
        val config = settings(context)
        if (!config.enabled) return
        scheduleNext(context)
        applyCurrentWindow(context, config)
    }

    fun scheduleNext(context: Context) {
        val config = settings(context)
        cancel(context, REQUEST_START)
        cancel(context, REQUEST_STOP)
        if (!config.enabled) return
        val start = parse(config.start) ?: return
        val stop = parse(config.stop) ?: return
        schedule(context, ACTION_START, REQUEST_START, nextOccurrence(start))
        schedule(context, ACTION_STOP, REQUEST_STOP, nextOccurrence(stop))
    }

    private fun nextOccurrence(time: LocalTime): Long {
        val now = ZonedDateTime.now()
        var target = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.toInstant().toEpochMilli()
    }

    private fun schedule(context: Context, action: String, requestCode: Int, atMillis: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context, action, requestCode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    private fun cancel(context: Context, requestCode: Int) {
        val action = if (requestCode == REQUEST_START) ACTION_START else ACTION_STOP
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, action, requestCode))
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, HostingScheduleReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun applyCurrentWindow(context: Context, config: Settings) {
        val start = parse(config.start) ?: return
        val stop = parse(config.stop) ?: return
        val now = LocalTime.now()
        val shouldRun = if (start < stop) now >= start && now < stop else now >= start || now < stop
        if (shouldRun) startHosting(context) else stopHosting(context)
    }

    internal fun handleAlarm(context: Context, action: String?) {
        when (action) {
            ACTION_START -> {
                OperationalLog.record(context, "INFO", "HOSTING_SCHEDULE_ON", "Daily schedule switched local hosting ON.")
                startHosting(context)
            }
            ACTION_STOP -> {
                OperationalLog.record(context, "INFO", "HOSTING_SCHEDULE_OFF", "Daily schedule switched local hosting OFF.")
                stopHosting(context)
            }
        }
        scheduleNext(context)
    }

    private fun startHosting(context: Context) {
        val service = Intent(context, HostingService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                androidx.core.content.ContextCompat.startForegroundService(context, service)
            else context.startService(service)
        }.onFailure {
            OperationalLog.record(context, "ERROR", "HOSTING_SCHEDULE_START_FAILED",
                it.message ?: "Android did not allow scheduled hosting to start.")
        }
    }

    private fun stopHosting(context: Context) {
        runCatching { context.stopService(Intent(context, HostingService::class.java)) }
            .onFailure {
                OperationalLog.record(context, "ERROR", "HOSTING_SCHEDULE_STOP_FAILED",
                    it.message ?: "Scheduled hosting stop failed.")
            }
    }

    private fun parse(value: String): LocalTime? = runCatching {
        require(value.matches(Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")))
        LocalTime.parse(value)
    }.getOrNull()

    private const val ACTION_START = "com.assayflux.pockethost.HOSTING_SCHEDULE_START"
    private const val ACTION_STOP = "com.assayflux.pockethost.HOSTING_SCHEDULE_STOP"
}
