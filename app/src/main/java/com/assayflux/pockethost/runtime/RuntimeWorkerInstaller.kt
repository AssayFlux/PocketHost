package com.assayflux.pockethost.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import android.app.PendingIntent
import java.io.IOException

/**
 * Installs the separately signed runtime companion embedded in the main APK.
 * Keeping it as a distinct Android package preserves the separate application UID
 * used to execute customer Python and Node.js projects.
 */
object RuntimeWorkerInstaller {
    const val WORKER_PACKAGE = "com.assayflux.pockethost.worker"
    private const val EMBEDDED_APK = "pockethost-runtime-worker.apk"
    private const val INSTALL_ACTION = "com.assayflux.pockethost.INSTALL_RUNTIME_WORKER"

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(WORKER_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun canRequestInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun installPermissionSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    @Throws(IOException::class)
    fun installEmbedded(context: Context): String {
        require(canRequestInstallPermission(context)) {
            "Allow PocketHost to install the bundled runtime companion in Android's Install unknown apps settings, then retry."
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(WORKER_PACKAGE)
            setInstallReason(PackageInstaller.INSTALL_REASON_USER)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("runtime-worker.apk", 0, -1).use { output ->
                    context.assets.open(EMBEDDED_APK).use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                val callback = Intent(context, RuntimeWorkerInstallReceiver::class.java).setAction(INSTALL_ACTION)
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callback,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pending.intentSender)
            }
            return "Android's runtime installation confirmation has been requested. Approve the PocketHost Runtime Worker installation."
        } catch (error: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
    }
}

class RuntimeWorkerInstallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    private fun confirmationIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_INTENT)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                confirmationIntent(intent)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS -> Toast.makeText(
                context, "PocketHost Python and Node.js runtime companion installed.", Toast.LENGTH_LONG
            ).show()
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.take(160).orEmpty()
                Toast.makeText(
                    context,
                    if (detail.isBlank()) "Runtime companion installation failed." else "Runtime install failed: $detail",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
