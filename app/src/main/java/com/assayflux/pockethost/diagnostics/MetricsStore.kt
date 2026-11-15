package com.assayflux.pockethost.diagnostics

import android.content.Context
import java.time.LocalDate

/** Persists aggregate traffic counters across engine and service restarts. */
object MetricsStore {
    data class Snapshot(
        val totalRequests: Long,
        val totalBytes: Long,
        val todayRequests: Long,
        val todayBytes: Long
    )

    private const val PREFS = "pockethost_metrics"

    @Synchronized
    fun beginSession(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("session_requests", 0L)
            .putLong("session_bytes", 0L)
            .apply()
    }

    @Synchronized
    fun recordSample(context: Context, requestCount: Long, bytesServed: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousRequests = prefs.getLong("session_requests", 0L)
        val previousBytes = prefs.getLong("session_bytes", 0L)
        val requestDelta = if (requestCount >= previousRequests) requestCount - previousRequests else requestCount
        val byteDelta = if (bytesServed >= previousBytes) bytesServed - previousBytes else bytesServed
        val today = LocalDate.now().toString()
        val storedDay = prefs.getString("day", null)
        val todayRequests = if (storedDay == today) prefs.getLong("today_requests", 0L) else 0L
        val todayBytes = if (storedDay == today) prefs.getLong("today_bytes", 0L) else 0L

        prefs.edit()
            .putLong("total_requests", prefs.getLong("total_requests", 0L) + requestDelta)
            .putLong("total_bytes", prefs.getLong("total_bytes", 0L) + byteDelta)
            .putString("day", today)
            .putLong("today_requests", todayRequests + requestDelta)
            .putLong("today_bytes", todayBytes + byteDelta)
            .putLong("session_requests", requestCount)
            .putLong("session_bytes", bytesServed)
            .apply()
        DailyOperationsReport.refreshCurrent(context, today, todayRequests + requestDelta, todayBytes + byteDelta)
    }

    fun snapshot(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val sameDay = prefs.getString("day", null) == today
        return Snapshot(
            totalRequests = prefs.getLong("total_requests", 0L),
            totalBytes = prefs.getLong("total_bytes", 0L),
            todayRequests = if (sameDay) prefs.getLong("today_requests", 0L) else 0L,
            todayBytes = if (sameDay) prefs.getLong("today_bytes", 0L) else 0L
        )
    }

    /** Explicit analytics reset, used by the dashboard and instrumentation tests. */
    @Synchronized
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
