package com.assayflux.pockethost.dns

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.assayflux.pockethost.diagnostics.OperationalLog
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * DDNS ownership is explicit: background sync may update only record IDs previously
 * created or explicitly adopted by the owner in the foreground UI.
 */
internal object DynamicDnsSync {
    private const val PREFS = "pockethost_ddns"
    private const val OWNERSHIP = "managed_record_ids"
    private const val HISTORY = "sync_history"

    data class FamilyResult(val type: String, val success: Boolean, val message: String, val address: String? = null)

    fun schedule(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            manager.cancelUniqueWork("pockethost-dynamic-dns-periodic")
            return
        }
        val request = PeriodicWorkRequestBuilder<DynamicDnsWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniquePeriodicWork(
            "pockethost-dynamic-dns-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun isScheduled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto_sync", false)

    @Synchronized
    fun run(context: Context, allowAdoptExisting: Boolean = false): List<FamilyResult> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastAttempt = prefs.getLong("last_attempt_ms", 0L)
        require(now - lastAttempt >= 30_000L) { "Dynamic DNS is rate-limited to one sync attempt every 30 seconds." }
        check(prefs.edit().putLong("last_attempt_ms", now).commit()) { "Could not record the last DDNS attempt." }
        val domain = CloudflareDnsApi.normalizeDomain(prefs.getString("domain", "").orEmpty())
        val host = prefs.getString("host", "@").orEmpty().trim()
        require(host.isNotBlank()) { "A DNS hostname is required." }
        val include4 = prefs.getBoolean("ipv4", true)
        val include6 = prefs.getBoolean("ipv6", true)
        require(include4 || include6) { "Select at least one address family." }
        val provider = prefs.getString("provider", "cloudflare") ?: "cloudflare"
        val addresses = PublicAddressDetector.detect()
        if (provider == "dynadot") {
            val password = DynadotDdnsCredentialStore.read(context)
                ?: throw IllegalStateException("Save the domain-specific Dynadot DDNS password first.")
            val outcomes = mutableListOf<FamilyResult>()
            if (include4) outcomes += syncDynadotFamily(domain, host, "A", addresses.ipv4, password)
            if (include6) outcomes += syncDynadotFamily(domain, host, "AAAA", addresses.ipv6, password)
            outcomes.forEach { recordHistory(context, it) }
            val successes = outcomes.count { it.success }
            OperationalLog.record(context, if (successes == outcomes.size && outcomes.isNotEmpty()) "INFO" else "WARN",
                "DDNS_SYNC_DYNADOT", outcomes.joinToString("; ") { "${it.type}: ${it.message}" })
            return outcomes
        }
        val token = CloudflareApiTokenStore.read(context)
            ?: throw IllegalStateException("Save a scoped Cloudflare DNS API token first.")
        val zone = CloudflareDnsApi.findZone(token, domain)
        val fqdn = if (host == "@") zone.name else "${host.trimEnd('.').lowercase()}.${zone.name}"
        require(fqdn == zone.name || fqdn.endsWith(".${zone.name}")) { "Hostname is outside the selected DNS zone." }
        val records = CloudflareDnsApi.records(token, zone.id)
        val outcomes = mutableListOf<FamilyResult>()
        if (include4) outcomes += syncFamily(context, token, zone, records, fqdn, host, "A", addresses.ipv4, prefs.getBoolean("proxied", true), allowAdoptExisting)
        if (include6) outcomes += syncFamily(context, token, zone, records, fqdn, host, "AAAA", addresses.ipv6, prefs.getBoolean("proxied", true), allowAdoptExisting)
        outcomes.forEach { recordHistory(context, it) }
        val success = outcomes.count { it.success }
        OperationalLog.record(context, if (success == outcomes.size && outcomes.isNotEmpty()) "INFO" else "WARN",
            "DDNS_SYNC", outcomes.joinToString("; ") { "${it.type}: ${it.message}" })
        return outcomes
    }

    private fun syncDynadotFamily(domain: String, host: String, type: String, address: String?, password: String): FamilyResult {
        return try {
            val ip = address ?: throw IllegalStateException("No globally routable public $type address detected; Dynadot record left unchanged.")
            val response = DynadotDdnsApi.update(domain, host, type, ip, password)
            val fqdn = if (host == "@") domain else "${host.trimEnd('.').lowercase()}.$domain"
            if (!verifyPublicDns(fqdn, type, ip, false)) {
                throw IllegalStateException("Dynadot accepted the update, but public DNS has not converged yet. It will be checked again. Response: ${response.take(180)}")
            }
            val timestamp = Instant.now().toString()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("last_success_$type", timestamp).putString("last_address_$type", ip).apply()
            FamilyResult(type, true, "Dynadot updated and public DNS verified as $ip", ip)
        } catch (error: Exception) {
            FamilyResult(type, false, error.message ?: "Dynadot DDNS update failed.")
        }
    }

    private fun syncFamily(
        context: Context, token: String, zone: CloudflareZone, allRecords: List<CloudflareDnsRecord>,
        fqdn: String, host: String, type: String, address: String?, proxied: Boolean, allowAdopt: Boolean
    ): FamilyResult {
        return try {
            val ip = address ?: throw IllegalStateException("No globally routable public $type address detected; existing record left unchanged.")
            val matches = allRecords.filter { it.type == type && it.name.equals(fqdn, true) }
            require(matches.size <= 1) { "Multiple $type records exist for $fqdn. Resolve duplicates; none were changed." }
            val record = matches.singleOrNull()
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val owners = JSONObject(preferences.getString(OWNERSHIP, "{}") ?: "{}")
            val ownerKey = "$type|$fqdn"
            val ownedId = owners.optString(ownerKey).takeIf { it.isNotBlank() }
            val recordId = when {
                record == null -> null
                ownedId != null && ownedId == record.id -> record.id
                ownedId != null -> throw IllegalStateException("The saved managed record ID no longer matches Cloudflare. Ownership was not transferred.")
                allowAdopt -> record.id
                else -> throw IllegalStateException("An existing unowned $type record was found. Enable the explicit adopt-existing confirmation to manage it.")
            }
            val alreadyCurrent = record != null && record.id == recordId &&
                record.proxied == proxied && sameAddress(record.content, ip)
            val savedId = if (alreadyCurrent) record!!.id
                else CloudflareDnsApi.save(token, zone, recordId, type, host, ip, proxied, "10")
            owners.put(ownerKey, savedId)
            check(preferences.edit().putString(OWNERSHIP, owners.toString()).commit()) { "Could not persist DNS record ownership; verify the Cloudflare record before retrying." }
            val verified = verifyPublicDns(fqdn, type, ip, proxied)
            if (!verified) throw IllegalStateException("Cloudflare accepted the change, but public DNS has not converged yet. It will be checked again.")
            val timestamp = Instant.now().toString()
            preferences.edit().putString("last_success_$type", timestamp).putString("last_address_$type", ip).apply()
            FamilyResult(type, true, "Updated and publicly verified as $ip", ip)
        } catch (error: Exception) {
            FamilyResult(type, false, error.message ?: "DNS update failed.")
        }
    }

    private fun sameAddress(first: String, second: String): Boolean = runCatching {
        java.net.InetAddress.getByName(first).address.contentEquals(java.net.InetAddress.getByName(second).address)
    }.getOrDefault(false)

    private fun verifyPublicDns(name: String, type: String, expected: String, proxied: Boolean): Boolean {
        val encoded = URLEncoder.encode(name, "UTF-8")
        val expectedNormalized = expected.substringBefore('%').lowercase()
        repeat(3) { attempt ->
            val result = runCatching {
                val url = URL("https://cloudflare-dns.com/dns-query?name=$encoded&type=$type")
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5000
                    connection.readTimeout = 7000
                    connection.setRequestProperty("Accept", "application/dns-json")
                    if (connection.responseCode !in 200..299) return@runCatching false
                    val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    val answers = json.optJSONArray("Answer") ?: JSONArray()
                    if (proxied) answers.length() > 0 else (0 until answers.length()).any { i ->
                        val data = answers.optJSONObject(i)?.optString("data")?.trimEnd('.')
                        data != null && runCatching {
                            java.net.InetAddress.getByName(data).address.contentEquals(
                                java.net.InetAddress.getByName(expectedNormalized).address
                            )
                        }.getOrDefault(false)
                    }
                } finally { connection.disconnect() }
            }.getOrDefault(false)
            if (result) return true
            if (attempt < 2) Thread.sleep(1200L * (attempt + 1))
        }
        return false
    }

    private fun recordHistory(context: Context, result: FamilyResult) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val history = runCatching { JSONArray(prefs.getString(HISTORY, "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray().put(JSONObject()
            .put("time", Instant.now().toString())
            .put("type", result.type)
            .put("success", result.success)
            .put("address", result.address ?: "")
            .put("message", result.message.take(400)))
        for (i in 0 until minOf(history.length(), 99)) next.put(history.getJSONObject(i))
        prefs.edit().putString(HISTORY, next.toString()).apply()
    }

    fun history(context: Context): List<String> {
        val raw = runCatching { JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(HISTORY, "[]")) }.getOrDefault(JSONArray())
        return (0 until raw.length()).mapNotNull { raw.optJSONObject(it) }.map {
            "${it.optString("time")} · ${it.optString("type")} · ${if (it.optBoolean("success")) "OK" else "FAILED"} · ${it.optString("message")}"
        }
    }
}

internal class DynamicDnsWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val outcomes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DynamicDnsSync.run(applicationContext)
        }
        if (outcomes.isEmpty() || outcomes.any { !it.success }) {
            notify(applicationContext, "Dynamic DNS needs attention", outcomes.joinToString("\n") { "${it.type}: ${it.message}" })
            Result.retry()
        } else {
            notify(applicationContext, "Dynamic DNS synchronized", outcomes.joinToString("\n") { "${it.type}: ${it.message}" })
            Result.success()
        }
    } catch (error: Exception) {
        OperationalLog.record(applicationContext, "WARN", "DDNS_WORKER_RETRY", error.message ?: "Background DDNS failed.")
        notify(applicationContext, "Dynamic DNS sync failed", error.message ?: "Check DNS settings and connectivity.")
        Result.retry()
    }

    private fun notify(context: Context, title: String, message: String) {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(android.app.NotificationChannel(
                "pockethost-ddns", "Dynamic DNS", android.app.NotificationManager.IMPORTANCE_DEFAULT
            ))
        }
        val notification = androidx.core.app.NotificationCompat.Builder(context, "pockethost-ddns")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message.lineSequence().firstOrNull()?.take(120) ?: title)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message.take(1000)))
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(if (title.contains("failed") || title.contains("attention")) 3102 else 3101, notification) }
    }
}
