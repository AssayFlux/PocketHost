package com.assayflux.pockethost.diagnostics

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Durable, privacy-redacted daily summaries derived from aggregate counters and operational events. */
object DailyOperationsReport {
    @Synchronized fun archivePreviousDay(context: Context, day: String, requests: Long, bytes: Long) {
        runCatching {
            val directory = File(context.applicationContext.filesDir, "reports/daily")
            if (!directory.exists()) directory.mkdirs()
            val destination = File(directory, "$day.json")
            val entries = OperationalLog.read(context, 500).filter { it.timestamp.startsWith(day) }
            val report = JSONObject().put("date", day).put("generatedAt", Instant.now().toString())
                .put("totalRequests", requests).put("totalBytes", bytes)
                .put("eventCount", entries.size)
                .put("errorCount", entries.count { it.level == "ERROR" })
                .put("warningCount", entries.count { it.level == "WARN" })
                .put("events", org.json.JSONArray().also { array ->
                    entries.take(100).forEach { e ->
                        array.put(JSONObject().put("timestamp", e.timestamp).put("level", e.level)
                            .put("event", e.event).put("message", e.message))
                    }
                })
                .put("scope", "Aggregate local hosting counters and retained redacted operational events; not financial records.")
            val temporary = File(directory, ".$day.tmp")
            temporary.writeText(report.toString(2), Charsets.UTF_8)
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            directory.listFiles()?.filter { it.isFile && it.extension == "json" }
                ?.sortedByDescending { it.name }?.drop(90)?.forEach { it.delete() }
        }
    }


    @Synchronized fun refreshCurrent(context: Context, day: String, requests: Long, bytes: Long) {
        val prefs = context.getSharedPreferences("pockethost_daily_reports", Context.MODE_PRIVATE)
        val now = Instant.now().epochSecond
        if (now - prefs.getLong("last_snapshot_at", 0L) < 3600L) return
        runCatching {
            val directory = File(context.applicationContext.filesDir, "reports/daily")
            if (!directory.exists()) directory.mkdirs()
            val entries = OperationalLog.read(context, 500).filter { it.timestamp.startsWith(day) }
            val report = JSONObject().put("date", day).put("generatedAt", Instant.now().toString())
                .put("totalRequests", requests).put("totalBytes", bytes).put("eventCount", entries.size)
                .put("errorCount", entries.count { it.level == "ERROR" })
                .put("warningCount", entries.count { it.level == "WARN" })
                .put("events", org.json.JSONArray().also { array ->
                    entries.take(100).forEach { e -> array.put(JSONObject().put("timestamp", e.timestamp)
                        .put("level", e.level).put("event", e.event).put("message", e.message)) }
                })
                .put("scope", "Hourly snapshot of aggregate static-engine counters and retained redacted events; not financial records.")
            val destination = File(directory, "$day.json")
            val temporary = File(directory, ".$day.current.tmp")
            temporary.writeText(report.toString(2), Charsets.UTF_8)
            if (!temporary.renameTo(destination)) { temporary.copyTo(destination, overwrite = true); temporary.delete() }
            prefs.edit().putLong("last_snapshot_at", now).apply()
        }
    }

    fun list(context: Context): List<JSONObject> = runCatching {
        File(context.applicationContext.filesDir, "reports/daily").listFiles()
            ?.filter { it.isFile && it.extension == "json" }?.sortedByDescending { it.name }
            ?.take(90)?.mapNotNull { file -> runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull() }
            ?: emptyList()
    }.getOrDefault(emptyList())
}
