package com.assayflux.pockethost.diagnostics

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Small, bounded, app-private operational event log. Never stores credentials. */
object OperationalLog {
    data class Entry(
        val timestamp: String,
        val level: String,
        val event: String,
        val message: String
    )

    private const val DIRECTORY = "operations"
    private const val CURRENT = "events.jsonl"
    private const val MAX_FILE_BYTES = 1024L * 1024L
    private const val RETAINED_FILES = 3

    @Synchronized
    fun record(context: Context, level: String, event: String, message: String = "") {
        runCatching {
            val directory = File(context.applicationContext.filesDir, DIRECTORY)
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, CURRENT)
            if (file.exists() && file.length() >= MAX_FILE_BYTES) rotate(directory, file)
            val safeMessage = redact(message).take(1_200)
            val line = JSONObject()
                .put("timestamp", Instant.now().toString())
                .put("level", level.uppercase().take(12))
                .put("event", event.take(80))
                .put("message", safeMessage)
                .toString()
            file.appendText(line + "\n", Charsets.UTF_8)
        }
    }

    @Synchronized
    fun read(context: Context, limit: Int = 50): List<Entry> {
        require(limit in 1..500)
        return runCatching {
            val directory = File(context.applicationContext.filesDir, DIRECTORY)
            val files = (RETAINED_FILES downTo 1).map { File(directory, "$CURRENT.$it") } +
                File(directory, CURRENT)
            files.filter { it.isFile }.flatMap { file ->
                file.readLines(Charsets.UTF_8).mapNotNull { line ->
                    runCatching {
                        val json = JSONObject(line)
                        Entry(
                            json.optString("timestamp"),
                            json.optString("level", "INFO"),
                            json.optString("event"),
                            json.optString("message")
                        )
                    }.getOrNull()
                }
            }.takeLast(limit).reversed()
        }.getOrDefault(emptyList())
    }

    private fun rotate(directory: File, current: File) {
        File(directory, "$CURRENT.$RETAINED_FILES").delete()
        for (index in RETAINED_FILES - 1 downTo 1) {
            val from = File(directory, "$CURRENT.$index")
            if (from.exists()) from.renameTo(File(directory, "$CURRENT.${index + 1}"))
        }
        current.renameTo(File(directory, "$CURRENT.1"))
    }

    private fun redact(value: String): String {
        val assignment = Regex("(?i)(token|authorization|secret|password|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+")
        val bearer = Regex("(?i)bearer\\s+[a-z0-9._~+/-]+=*")
        return value.replace(assignment, "$1=[REDACTED]")
            .replace(bearer, "Bearer [REDACTED]")
            .replace(Regex("[\\r\\n\\t]+"), " ")
    }
}
