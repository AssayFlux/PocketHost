package com.assayflux.pockethost.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.assayflux.pockethost.diagnostics.OperationalLog
import java.io.File

/** Opt-in boot recovery for the owner's local static website hosting service. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("auto_start_on_boot", false)) return
        if (!File(context.filesDir, "site").isDirectory) {
            OperationalLog.record(context, "WARN", "BOOT_AUTOSTART_SKIPPED", "Website directory is missing; hosting was not started.")
            return
        }
        runCatching {
            val service = Intent(context, HostingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, service)
            } else {
                context.startService(service)
            }
        }.onFailure {
            OperationalLog.record(context, "ERROR", "BOOT_AUTOSTART_FAILED", it.message ?: "Android did not allow hosting to start after boot.")
        }
    }
}
