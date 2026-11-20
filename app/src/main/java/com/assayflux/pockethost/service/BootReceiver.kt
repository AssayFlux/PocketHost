package com.assayflux.pockethost.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.assayflux.pockethost.diagnostics.OperationalLog
import java.io.File

/** Opt-in boot recovery for local website hosting and the independent Relay gateway. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        val scheduled = HostingSchedule.settings(context).enabled
        if (scheduled) {
            // A configured daily window takes precedence over the legacy boot auto-start switch.
            HostingSchedule.onBoot(context)
        } else if (preferences.getBoolean("auto_start_on_boot", false)) {
            if (!File(context.filesDir, "site").isDirectory) {
                OperationalLog.record(context, "WARN", "BOOT_AUTOSTART_SKIPPED",
                    "Website directory is missing; hosting was not started.")
            } else {
                runCatching {
                    val service = Intent(context, HostingService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        ContextCompat.startForegroundService(context, service)
                    else context.startService(service)
                }.onFailure {
                    OperationalLog.record(context, "ERROR", "BOOT_AUTOSTART_FAILED",
                        it.message ?: "Android did not allow hosting to start after boot.")
                }
            }
        }

        // Relay recovery is separately opted in and does not depend on the website directory.
        if (preferences.getBoolean("relay_auto_start_on_boot", false)) {
            val configured = context.getSharedPreferences("pockethost_relay_node", Context.MODE_PRIVATE)
                .contains("encrypted_config_v1")
            if (!configured) {
                OperationalLog.record(context, "WARN", "RELAY_BOOT_AUTOSTART_SKIPPED",
                    "Relay auto-start is enabled but no encrypted node configuration exists.")
            } else {
                runCatching { com.assayflux.pockethost.relay.NativeRelayService.start(context) }
                    .onFailure {
                        OperationalLog.record(context, "ERROR", "RELAY_BOOT_AUTOSTART_FAILED",
                            it.message ?: "Android did not allow Relay recovery after boot.")
                    }
            }
        }
    }
}
