package com.assayflux.pockethost

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.biometric.BiometricManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import java.time.LocalDate
import com.assayflux.pockethost.ui.theme.PocketHostTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.diagnostics.OperationalLog
import com.assayflux.pockethost.diagnostics.MetricsStore
import com.assayflux.pockethost.diagnostics.DiagnosticsBundle
import com.assayflux.pockethost.diagnostics.CapacityController
import com.assayflux.pockethost.diagnostics.CapacityLab
import com.assayflux.pockethost.diagnostics.DailyOperationsReport
import com.assayflux.pockethost.diagnostics.TelemetryStore
import com.assayflux.pockethost.diagnostics.DeviceTelemetry
import com.assayflux.pockethost.service.HostingService
import com.assayflux.pockethost.storage.SiteImporter
import com.assayflux.pockethost.storage.SiteFileManager
import com.assayflux.pockethost.storage.PythonProjectImporter
import com.assayflux.pockethost.storage.NodeProjectImporter
import com.assayflux.pockethost.storage.ApplicationBackupManager
import com.assayflux.pockethost.runtime.PythonHostRuntime
import com.assayflux.pockethost.runtime.NodeHostRuntime
import com.assayflux.pockethost.sites.SiteRegistry
import com.assayflux.pockethost.management.ManagementApiServer
import com.assayflux.pockethost.management.RemoteControlAgent
import com.assayflux.pockethost.management.RelayAgent
import com.assayflux.pockethost.management.RelayCredentialStore
import com.assayflux.pockethost.management.RelayPairingClient
import com.assayflux.pockethost.relay.NativeRelay
import com.assayflux.pockethost.relay.NativeRelayService
import com.assayflux.pockethost.relay.RelayNodeConfigStore
import com.assayflux.pockethost.relay.RelayNodeIdentityStore
import com.assayflux.pockethost.relay.RelayCredentialIssuer
import com.assayflux.pockethost.tunnel.CloudflareTokenStore
import com.assayflux.pockethost.tunnel.CloudflareTunnelRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


class MainActivity : ComponentActivity() {
    private var pendingBiometricCallback: ((Boolean) -> Unit)? = null
    private val biometricEnableRequestCode = 1202
    private val biometricLaunchRequestCode = 1201
    private val allowedAuthenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            // Android 8–10 cannot combine DEVICE_CREDENTIAL with BIOMETRIC_STRONG
            // in the AndroidX prompt. Keep those devices on strong biometrics only.
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
    private var unlocked = mutableStateOf(true)
    private var ownerAuthenticated = mutableStateOf(PocketHostOwnerSession.username != null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw edge-to-edge and apply Compose system-bar insets to the app chrome/content.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !systemDark
            isAppearanceLightNavigationBars = !systemDark
        }
        val backupRecovery = runCatching { ApplicationBackupManager.recoverInterruptedRestore(this) }
        backupRecovery.onFailure { error ->
            OperationalLog.record(this, "ERROR", "BACKUP_RESTORE_RECOVERY_FAILED",
                error.message ?: "Could not recover an interrupted full backup restore.")
        }
        if (backupRecovery.isSuccess) {
            runCatching { SiteImporter.recoverInterruptedActivation(this) }
                .onSuccess { recovered ->
                    if (recovered) OperationalLog.record(this, "WARN", "SITE_ACTIVATION_RECOVERED",
                        "The previous last-known-good website was restored after an interrupted deployment.")
                }
                .onFailure { error ->
                    OperationalLog.record(this, "ERROR", "SITE_ACTIVATION_RECOVERY_FAILED",
                        error.message ?: "Could not recover the previous website deployment.")
                }
        }
        val enabled = getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
            .getBoolean("biometric_unlock_enabled", false)
        unlocked = mutableStateOf(!enabled)
        ownerAuthenticated = mutableStateOf(PocketHostOwnerSession.username != null)
        setContent {
            val appearance = getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
            val savedMode = appearance.getString("appearance_mode", null)
                ?: if (appearance.contains("dark_theme")) { if (appearance.getBoolean("dark_theme", false)) "Dark" else "Light" } else "System"
            val useDark = when (savedMode) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
            val savedTheme = appearance.getString("appearance_theme", "Classic") ?: "Classic"
            PocketHostTheme(darkTheme = useDark, themeName = savedTheme) {
                if (!unlocked.value) {
                    BiometricUnlockScreen(onRetry = { authenticateAtLaunch() })
                } else if (ownerAuthenticated.value) {
                    PocketHostDashboard(onSignOut = {
                        PocketHostOwnerSession.username = null
                        ownerAuthenticated.value = false
                    })
                } else {
                    PocketHostLoginScreen(this@MainActivity, onAuthenticated = {
                        ownerAuthenticated.value = true
                    })
                }
            }
        }
        if (enabled) authenticateAtLaunch()
    }

    /** Launch the legacy biometric host with a bounded 16-bit request code. */
    private fun launchBiometricHost(requestCode: Int) {
        startActivityForResult(
            Intent(this, BiometricHostActivity::class.java),
            requestCode
        )
    }

    /** Confirms the enrolled biometric/device credential before enabling the local app gate. */
    fun confirmBiometricUnlock(onResult: (Boolean) -> Unit) {
        if (BiometricManager.from(this).canAuthenticate(allowedAuthenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Set up a strong biometric or secure device lock first.", Toast.LENGTH_LONG).show()
            onResult(false)
            return
        }
        pendingBiometricCallback = onResult
        launchBiometricHost(biometricEnableRequestCode)
    }

    private fun authenticateAtLaunch() {
        if (BiometricManager.from(this).canAuthenticate(allowedAuthenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                .putBoolean("biometric_unlock_enabled", false).apply()
            unlocked.value = true
            Toast.makeText(this, "Biometric unlock is unavailable; the app lock was disabled.", Toast.LENGTH_LONG).show()
            return
        }
        launchBiometricHost(biometricLaunchRequestCode)
    }

    @Deprecated("Legacy result callback is intentionally used with a 16-bit request code for the biometric host.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == biometricLaunchRequestCode) {
            if (resultCode == RESULT_OK) unlocked.value = true
        } else if (requestCode == biometricEnableRequestCode) {
            val callback = pendingBiometricCallback
            pendingBiometricCallback = null
            callback?.invoke(resultCode == RESULT_OK)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

@Composable
private fun PocketHostDashboard(onSignOut: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { HostingSchedule.onTimeChanged(context) }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("Home") }
    var settingsSection by remember { mutableStateOf("General") }
    var operatingMode by remember {
        mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
            .getString("operating_mode", "SERVER_ONLY") ?: "SERVER_ONLY")
    }
    val appearancePreferences = remember { context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE) }
    var appearanceMode by remember {
        mutableStateOf(appearancePreferences.getString("appearance_mode", null) ?: run {
            if (appearancePreferences.contains("dark_theme")) {
                if (appearancePreferences.getBoolean("dark_theme", false)) "Dark" else "Light"
            } else "System"
        })
    }
    var selectedTheme by remember {
        mutableStateOf(appearancePreferences.getString("appearance_theme", "Classic") ?: "Classic")
    }
    val darkTheme = when (appearanceMode) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }
    SideEffect {
        context.findActivity()?.let { activity ->
            val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    var biometricUnlockEnabled by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getBoolean("biometric_unlock_enabled", false)) }
    var autoStartOnBoot by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getBoolean("auto_start_on_boot", false)) }
    var logFilterMode by remember { mutableStateOf("All") }
    var showClearLogsConfirmation by remember { mutableStateOf(false) }
    var siteFileRefresh by remember { mutableIntStateOf(0) }
    var showCreateSiteFile by remember { mutableStateOf(false) }
    var newSiteFilePath by remember { mutableStateOf("new-page.html") }
    var newSiteFileContent by remember { mutableStateOf("") }
    var editingSiteFilePath by remember { mutableStateOf<String?>(null) }
    var editingSiteFileContent by remember { mutableStateOf("") }
    var renamingSiteFilePath by remember { mutableStateOf<String?>(null) }
    var renamedSiteFilePath by remember { mutableStateOf("") }
    var pendingDeleteSiteFile by remember { mutableStateOf<String?>(null) }
    var pendingIndexSiteFile by remember { mutableStateOf<String?>(null) }
    var hosting by remember { mutableStateOf(runCatching { NativeEngine.status() == "RUNNING" }.getOrDefault(false)) }
    var uptimeText by remember { mutableStateOf("—") }
    var importing by remember { mutableStateOf(false) }
    var keepAwake by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getBoolean("keep_awake", false)) }
    var portText by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getInt("port", 8080).toString()) }
    var allowLan by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getBoolean("allow_lan", false)) }
    val lanAddress = remember(allowLan, hosting) { if (allowLan) localLanAddress() else null }
    var status by remember { mutableStateOf("Ready to configure your first website.") }
    var serviceStatus by remember {
        mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
            .getString("service_status", "Waiting for the hosting service status.")
            ?: "Waiting for the hosting service status.")
    }
    var requests by remember { mutableLongStateOf(0L) }
    var bytesServed by remember { mutableLongStateOf(0L) }
    var todayRequests by remember { mutableLongStateOf(0L) }
    var todayBytes by remember { mutableLongStateOf(0L) }
    var activeConnections by remember { mutableLongStateOf(0L) }
    var recentEvents by remember { mutableStateOf(emptyList<OperationalLog.Entry>()) }
    var logFilter by remember { mutableStateOf("") }
    var importedSummary by remember {
        mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
            .getString("site_summary", "Bundled sample website is available.") ?: "Bundled sample website is available.")
    }
    var previousReleases by remember { mutableStateOf(SiteImporter.listReleases(context)) }
    var pythonPortText by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getInt("python_port", 8081).toString()) }
    var pythonProjectSummary by remember { mutableStateOf(if (File(context.filesDir, "python-project/wsgi.py").isFile) "Python WSGI project imported." else "No Python project imported.") }
    var pythonHosting by remember { mutableStateOf(runCatching { PythonHostRuntime.status(context) == "RUNNING" }.getOrDefault(false)) }
    var nodePortText by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getInt("node_port", 3000).toString()) }
    var nodeProjectSummary by remember { mutableStateOf(if (File(context.filesDir, "node-project-default/package.json").isFile) "Node project imported." else "No Node project imported.") }
    var nodeHosting by remember { mutableStateOf(NodeHostRuntime.status(context) == "RUNNING") }
    var trustedRuntimeCodeAck by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getBoolean("trusted_runtime_code_ack", false)) }
    var hostnameText by remember { mutableStateOf("") }
    var hostnameRuntime by remember { mutableStateOf("static") }
    var visitorUsername by remember { mutableStateOf("") }
    var issuedVisitorCredential by remember { mutableStateOf<Pair<String, String>?>(null) }
    var cloudflareTokenText by remember { mutableStateOf("") }
    var relayEndpointText by remember { mutableStateOf(RelayCredentialStore.endpoint(context)) }
    var relayTokenText by remember { mutableStateOf("") }
    var relayPairingInviteText by remember { mutableStateOf("") }
    var relayConfigured by remember { mutableStateOf(RelayCredentialStore.isConfigured(context)) }
    var relayStatus by remember { mutableStateOf(RelayAgent.status()) }
    var nativeRelayStatus by remember { mutableStateOf(runCatching { NativeRelay.status() }.getOrDefault("UNAVAILABLE")) }
    var nativeRelayConfigured by remember { mutableStateOf(RelayNodeConfigStore.isConfigured(context)) }
    var relayAcmeEmail by remember { mutableStateOf(runCatching { JSONObject(RelayNodeConfigStore.read(context) ?: "{}").optString("acme_contact_email") }.getOrDefault("")) }
    var relayAcmeTermsAccepted by remember { mutableStateOf(runCatching { JSONObject(RelayNodeConfigStore.read(context) ?: "{}").optBoolean("acme_terms_accepted", false) }.getOrDefault(false)) }
    var autoStartRelayOnBoot by remember { mutableStateOf(context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).getBoolean("relay_auto_start_on_boot", false)) }
    var nativeRelayIdentity by remember { mutableStateOf(runCatching { if (RelayNodeIdentityStore.exists()) RelayNodeIdentityStore.getOrCreate() else null }.getOrNull()) }
    var nativeRelayHostIdText by remember { mutableStateOf("") }
    var nativeRelayCredentialToken by remember { mutableStateOf<String?>(null) }
    var nativeRelayPairingInvite by remember { mutableStateOf<String?>(null) }
    var nativeRelayHostIds by remember { mutableStateOf(runCatching { RelayCredentialIssuer.registeredHostIds(context) }.getOrDefault(emptyList())) }
    var backupPassphrase by remember { mutableStateOf("") }
    var cloudflareTokenSaved by remember { mutableStateOf(CloudflareTokenStore.hasToken(context)) }
    var tunnelStatus by remember { mutableStateOf(CloudflareTunnelRuntime.status()) }
    var pendingPairingCode by remember { mutableStateOf<String?>(null) }
    var controlPlaneEndpoint by remember { mutableStateOf(RemoteControlAgent.endpoint(context)) }
    var controlPlaneToken by remember { mutableStateOf("") }
    var remoteControlConfigured by remember { mutableStateOf(RemoteControlAgent.isConfigured(context)) }
    var capacityPercent by remember { mutableFloatStateOf(CapacityController.snapshot(context).optInt("percent", 100).toFloat()) }
    var capacityInfo by remember { mutableStateOf(CapacityController.snapshot(context)) }
    var capacityBenchmark by remember { mutableStateOf(CapacityLab.snapshot(context)) }
    var capacityDuration by remember { mutableStateOf("180") }
    var selectedCapacityConcurrency by remember { mutableIntStateOf(-1) }
    var capacityDetailsExpanded by remember { mutableStateOf(false) }
    var deviceTelemetry by remember { mutableStateOf(DeviceTelemetry.snapshot(context)) }

    val startHosting: (Boolean) -> Unit = { notificationDenied ->
        val intent = Intent(context, HostingService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }.onSuccess {
            status = if (notificationDenied) "Starting hosting. Android notification permission is denied, so the ongoing service notification may be hidden from the notification drawer." else "Starting local server on port ${portText.toIntOrNull() ?: 8080}…"
        }.onFailure {
            status = it.message ?: "Could not start hosting service."
        }
    }
    val reloadSiteRoutes: () -> Unit = {
        val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        if (preferences.getBoolean("python_enabled", false)) {
            runCatching { PythonHostRuntime.stop(context) }
            val pythonHosts = SiteRegistry.pythonHostnameMap(context)
            val pythonRoot = preferences.getString("python_project_root", File(context.filesDir, "python-project").absolutePath)
                ?: File(context.filesDir, "python-project").absolutePath
            runCatching {
                if (pythonHosts.length() > 0) PythonHostRuntime.startSites(context, pythonHosts.toString(), preferences.getInt("python_port", 8081))
                else PythonHostRuntime.start(context, pythonRoot, preferences.getInt("python_port", 8081))
            }.onFailure { OperationalLog.record(context, "ERROR", "PYTHON_ROUTE_RELOAD_FAILED", it.message ?: "Python route reload failed.") }
        }
        if (NativeEngine.status() == "RUNNING") {
            MetricsStore.recordSample(context, NativeEngine.requestCount(), NativeEngine.bytesServed())
            NativeEngine.stop()
            val routes = SiteRegistry.hostRoutingJson(context, preferences.getInt("python_port", 8081), preferences.getInt("node_port", 3000))
            val result = if (routes.length() == 0) NativeEngine.start(File(context.filesDir, "site").absolutePath,
                preferences.getInt("port", 8080), preferences.getBoolean("allow_lan", false))
            else NativeEngine.startMultiSite(File(context.filesDir, "site").absolutePath, routes.toString(),
                preferences.getInt("port", 8080), preferences.getBoolean("allow_lan", false))
            check(result == "OK") { result }
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        startHosting(!granted)
    }

    val nativeRelayConfigPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val configJson = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Could not read the Relay configuration file.")
                    }
                    check(configJson.toByteArray(Charsets.UTF_8).size <= 256 * 1024) {
                        "Relay configuration exceeds 256 KiB."
                    }
                    check(RelayNodeConfigStore.save(context, configJson)) {
                        "Could not encrypt and save the Relay configuration."
                    }
                }
                result.onSuccess {
                    nativeRelayConfigured = true
                    nativeRelayHostIds = RelayCredentialIssuer.registeredHostIds(context)
                    nativeRelayCredentialToken = null
                    val importedConfig = JSONObject(RelayNodeConfigStore.read(context) ?: "{}")
                    relayAcmeEmail = importedConfig.optString("acme_contact_email")
                    relayAcmeTermsAccepted = importedConfig.optBoolean("acme_terms_accepted", false)
                    status = "Native Relay configuration encrypted with Android Keystore and stored transactionally. Start the Relay Node when ready."
                }.onFailure {
                    status = it.message ?: "Could not import native Relay configuration."
                }
            }
        }
    }

    val activityLifecycle = (context as? ComponentActivity)?.lifecycle
    val activityStarted = remember {
        mutableStateOf(activityLifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true)
    }
    DisposableEffect(activityLifecycle) {
        val lifecycle = activityLifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> activityStarted.value = true
                Lifecycle.Event.ON_STOP -> activityStarted.value = false
                else -> Unit
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(activityStarted.value) {
        if (!activityStarted.value) return@LaunchedEffect
        val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        var lastIdleTelemetryAt = 0L
        while (activityStarted.value) {
            hosting = runCatching { NativeEngine.status() == "RUNNING" }.getOrDefault(false)
            val hostingStartedAt = preferences.getLong("hosting_started_elapsed_ms", 0L)
            uptimeText = if (hosting && hostingStartedAt > 0L) {
                formatUptime(((android.os.SystemClock.elapsedRealtime() - hostingStartedAt) / 1000L).coerceAtLeast(0L))
            } else "—"
            val metrics = withContext(Dispatchers.IO) { MetricsStore.snapshot(context) }
            requests = metrics.totalRequests
            bytesServed = metrics.totalBytes
            todayRequests = metrics.todayRequests
            todayBytes = metrics.todayBytes
            activeConnections = runCatching { NativeEngine.activeConnections() }.getOrDefault(0L)
            pythonHosting = runCatching { PythonHostRuntime.status(context) == "RUNNING" }.getOrDefault(false)
            nodeHosting = runCatching { NodeHostRuntime.status(context) == "RUNNING" }.getOrDefault(false)
            tunnelStatus = CloudflareTunnelRuntime.status()
            relayStatus = RelayAgent.status()
            nativeRelayStatus = runCatching { NativeRelay.status() }.getOrDefault("UNAVAILABLE")
            pendingPairingCode = ManagementApiServer.pendingApprovalCode()
            preferences.getString("service_status", null)?.let { serviceStatus = it }
            recentEvents = withContext(Dispatchers.IO) { OperationalLog.read(context, 500) }
            capacityInfo = withContext(Dispatchers.IO) { CapacityController.tick(context)
                .put("activeConnections", NativeEngine.activeConnections())
                .put("queuedConnections", NativeEngine.queuedConnections())
                .put("rateLimitedRequests", NativeEngine.rateLimitedRequests())
                .put("siteLimitRejections", NativeEngine.siteLimitRejections())
                .put("rejectedConnections", NativeEngine.rejectedConnections()) }
            capacityBenchmark = CapacityLab.snapshot(context)
            val telemetryNow = android.os.SystemClock.elapsedRealtime()
            if (hosting) {
                deviceTelemetry = TelemetryStore.latest(context) ?: DeviceTelemetry.snapshot(context)
            } else if (telemetryNow - lastIdleTelemetryAt >= 30_000L) {
                // Keep the device panel live while the web server is stopped, without writing
                // a persistent history row every UI refresh.
                deviceTelemetry = DeviceTelemetry.snapshot(context)
                lastIdleTelemetryAt = telemetryNow
            }
            delay(1_000)
        }
    }

    val logExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-ndjson")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val exported = withContext(Dispatchers.IO) { OperationalLog.export(context, uri) }
                status = if (exported) "Redacted operational logs exported." else "Could not export operational logs."
            }
        }
    }

    val dailyReportExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val exported = withContext(Dispatchers.IO) { DailyOperationsReport.exportLatest(context, uri) }
                status = if (exported) "Latest daily operations report exported." else "Could not export the daily report."
            }
        }
    }

    val diagnosticsExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val exported = withContext(Dispatchers.IO) { DiagnosticsBundle.export(context, uri) }
                status = if (exported) "Diagnostics bundle exported. Review it before sharing." else "Could not export diagnostics bundle."
            }
        }
    }

    val backupExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            val passphrase = backupPassphrase.toCharArray()
            backupPassphrase = ""
            importing = true
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { ApplicationBackupManager.export(context, uri, passphrase) }
                }
                status = if (result.getOrDefault(false)) "Encrypted full app backup exported. Keep the passphrase safe; device-bound credentials are excluded." else result.exceptionOrNull()?.message ?: "Full backup export failed."
                importing = false
            }
        }
    }

    val backupRestorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val passphrase = backupPassphrase.toCharArray()
            backupPassphrase = ""
            importing = true
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { ApplicationBackupManager.restore(context, uri, passphrase) }
                }
                result.onSuccess { report ->
                    importedSummary = "Full backup restored · ${report.fileCount} files · ${report.totalBytes / 1024} KB"
                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                        .putString("site_summary", importedSummary).apply()
                    previousReleases = SiteImporter.listReleases(context)
                    status = if (report.legacyStaticOnly) "Legacy website backup restored. Runtime projects and settings were not included." else "Full backup restored. Python and Node remain stopped; review trusted-code acknowledgement before starting either runtime."
                }.onFailure { error -> status = error.message ?: "Backup restore failed." }
                importing = false
            }
        }
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            importing = true
            status = "Inspecting ZIP archive…"
            scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { SiteImporter.importZip(context, uri) } }
                result.onSuccess { report ->
                    importedSummary = "ZIP imported · ${report.fileCount} files · ${report.totalBytes / 1024} KB"
                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                        .putString("site_summary", importedSummary).apply()
                    previousReleases = SiteImporter.listReleases(context)
                    status = "ZIP website deployed locally. Start hosting to preview it."
                }.onFailure { error -> status = error.message ?: "ZIP import failed." }
                importing = false
            }
        }
    }

    val singleFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            importing = true
            status = "Uploading website file…"
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { SiteFileManager.importFile(context, uri) }
                }
                result.onSuccess { entry ->
                    siteFileRefresh++
                    status = "Uploaded ${entry.relativePath} (${formatBytes(entry.size)})."
                }.onFailure { error ->
                    status = error.message ?: "Individual file upload failed."
                }
                importing = false
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            importing = true
            status = "Validating and importing website…"
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { SiteImporter.importTree(context, uri) }
                }
                result.onSuccess { report ->
                    importedSummary = "Folder imported · ${report.fileCount} files · ${report.totalBytes / 1024} KB"
                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                        .putString("site_summary", importedSummary).apply()
                    previousReleases = SiteImporter.listReleases(context)
                    status = "Website deployed locally. Start hosting to preview it."
                }.onFailure { error ->
                    status = error.message ?: "Website import failed."
                }
                importing = false
            }
        }
    }

    val pythonProjectPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            importing = true
            status = "Validating Python WSGI project…"
            scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { PythonProjectImporter.importTree(context, uri) } }
                result.onSuccess { report ->
                    pythonProjectSummary = "Python project imported · ${report.fileCount} files · ${report.totalBytes / 1024} KB"
                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                        .putString("python_project_root", report.projectDirectory.absolutePath).apply()
                    runCatching { SiteRegistry.put(context, SiteRegistry.Site("python-default", "Python application", "python-wsgi",
                        report.projectDirectory.absolutePath, emptyList(), true)) }
                    status = "Python project is ready. Start the Python backend to run its wsgi.py application."
                }.onFailure { error -> status = error.message ?: "Python project import failed." }
                importing = false
            }
        }
    }

    val nodeProjectPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            importing = true
            status = "Importing Node.js project and validating package.json…"
            scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { NodeProjectImporter.importTree(context, uri, "default") } }
                result.onSuccess { report ->
                    nodeProjectSummary = "Node.js project imported · ${report.fileCount} files · ${report.totalBytes / 1024} KB · ${report.entryFile.name}"
                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                        .putString("node_project_root", report.projectDirectory.absolutePath).apply()
                    runCatching {
                        SiteRegistry.put(context, SiteRegistry.Site("node-default", "Node application", "node",
                            report.projectDirectory.absolutePath, emptyList(), true))
                    }.onFailure { OperationalLog.record(context, "WARN", "NODE_SITE_REGISTRY", it.message ?: "Could not register Node project.") }
                    status = "Node.js project is ready. Dependencies must already be included in node_modules; npm install scripts are not run on-device."
                }.onFailure { error -> status = error.message ?: "Node project import failed." }
                importing = false
            }
        }
    }

    PocketHostTheme(darkTheme = darkTheme, themeName = selectedTheme) {
        val palette = MaterialTheme.colorScheme
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val contentScrollState = rememberScrollState()
        LaunchedEffect(selectedTab) { contentScrollState.scrollTo(0) }
        val navigate: (String) -> Unit = { destination ->
            if (destination.startsWith("Settings:")) {
                selectedTab = "Settings"
                settingsSection = destination.substringAfter(":")
            } else {
                selectedTab = destination
            }
            scope.launch { drawerState.close() }
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    PocketHostSidebar(
                        selectedTab = selectedTab,
                        selectedSection = settingsSection,
                        operatingMode = operatingMode,
                        onNavigate = navigate
                    )
                }
            }
        ) {
        Scaffold(
            containerColor = palette.background,
            topBar = {
                PocketHostOwnerHeader(
                    selectedTab = selectedTab,
                    selectedSection = settingsSection,
                    hosting = hosting,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNavigate = navigate,
                    onSignOut = onSignOut
                )
            }
        ) { innerPadding ->
            Surface(Modifier.fillMaxSize(), color = palette.background) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Keep tablet/desktop-like widths readable while letting phones use all available width.
                    val contentWidth = maxWidth.coerceAtMost(840.dp)
                    Column(
                        Modifier.width(contentWidth).fillMaxHeight().align(Alignment.TopCenter)
                            .padding(innerPadding).consumeWindowInsets(innerPadding).imePadding()
                            .verticalScroll(contentScrollState).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                if (selectedTab == "Home") {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("MOBILE OPERATING MODE", color = palette.onSurface, fontWeight = FontWeight.Bold)
                            Text("Website serving stays available in both modes. Fleet administration is enabled only in Command Center + Server.", color = palette.onSurfaceVariant)
                            Text(if (operatingMode == "COMMAND_CENTER_SERVER") "Command Center + Server" else "Server only", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    operatingMode = "SERVER_ONLY"
                                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit().putString("operating_mode", operatingMode).apply()
                                    RemoteControlAgent.stop()
                                    if (selectedTab == "Devices") selectedTab = "Home"
                                }, modifier = Modifier.weight(1f)) { Text("Server only") }
                                Button(onClick = {
                                    operatingMode = "COMMAND_CENTER_SERVER"
                                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit().putString("operating_mode", operatingMode).apply()
                                    if (hosting) RemoteControlAgent.start(context)
                                }, modifier = Modifier.weight(1f)) { Text("Command Center + Server") }
                            }
                            if (operatingMode == "COMMAND_CENTER_SERVER") {
                                OutlinedButton(onClick = { selectedTab = "Devices" }, modifier = Modifier.fillMaxWidth()) { Text("Manage trusted devices") }
                            }
                        }
                    }
                    PocketHostHomeScreen(
                        hosting = hosting,
                        status = status,
                        requests = requests,
                        activeConnections = activeConnections,
                        uptimeText = uptimeText,
                        importedSummary = importedSummary,
                        tunnelStatus = tunnelStatus,
                        pythonHosting = pythonHosting,
                        nodeHosting = nodeHosting,
                        deviceTelemetry = deviceTelemetry,
                        recentEvents = recentEvents,
                        operatingMode = operatingMode,
                        onOpen = navigate
                    )
                }
                if (selectedTab == "Devices" && operatingMode == "COMMAND_CENTER_SERVER") {
                    PocketHostDevicesScreen(context = context)
                }
                if (selectedTab == "Workbench") {
                    PocketHostWorkbenchScreen(context = context)
                }
                if (selectedTab == "Deployments") {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SERVICE COMING SOON", color = palette.primary, fontWeight = FontWeight.Bold)
                            Text("Production deployment and rollback", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("Workbench currently supports private draft editing and immutable local snapshots. Publishing a reviewed draft to the live site and selecting a historical release for rollback are not available from this page yet.", color = palette.onSurfaceVariant)
                            TextButton(onClick = { selectedTab = "Workbench" }) { Text("Open Workbench drafts") }
                        }
                    }
                }
                if (selectedTab == "Learn") {
                    PocketHostLearnScreen(onOpenDashboard = { selectedTab = "Dashboard" }, onOpenSites = { selectedTab = "Sites" }, onOpenSettings = { selectedTab = "Settings" })
                }
                if (selectedTab == "Dashboard") {
                if (pendingPairingCode != null) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("CONSOLE PAIRING REQUEST", color = palette.onSurface, fontWeight = FontWeight.Bold)
                            Text("Code: $pendingPairingCode", color = palette.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Only approve this if you initiated pairing from your own trusted browser.", color = palette.onSurfaceVariant)
                            Button(onClick = {
                                val approved = ManagementApiServer.approvePairing(pendingPairingCode.orEmpty())
                                if (approved) { status = "Management console pairing approved."; pendingPairingCode = null }
                                else status = "Pairing request expired. Start pairing again."
                            }) { Text("Approve console pairing") }
                        }
                    }
                }
                }
                if (selectedTab == "Settings") {
                    PocketHostSettingsSectionNavigation(
                        selectedSection = settingsSection,
                        onSectionSelected = { settingsSection = it }
                    )
                }
                if (selectedTab == "Settings" && settingsSection == "General") {
                    PocketHostSettingsScreen(
                        state = PocketHostGeneralSettingsState(
                            appearanceMode = appearanceMode,
                            selectedTheme = selectedTheme,
                            biometricUnlockEnabled = biometricUnlockEnabled,
                            autoStartOnBoot = autoStartOnBoot
                        ),
                        actions = PocketHostGeneralSettingsActions(
                            onAppearanceModeChange = { mode ->
                                appearanceMode = mode
                                appearancePreferences.edit().putString("appearance_mode", mode).remove("dark_theme").apply()
                            },
                            onThemeChange = { theme ->
                                selectedTheme = theme
                                appearancePreferences.edit().putString("appearance_theme", theme).apply()
                            },
                            onBiometricUnlockChange = { enabled ->
                                if (!enabled) {
                                    biometricUnlockEnabled = false
                                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                        .putBoolean("biometric_unlock_enabled", false).apply()
                                    status = "Biometric app unlock disabled."
                                } else {
                                    val activity = context as? MainActivity
                                    if (activity == null) {
                                        status = "Could not open biometric confirmation."
                                    } else {
                                        activity.confirmBiometricUnlock { success ->
                                            if (success) {
                                                biometricUnlockEnabled = true
                                                context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                                    .putBoolean("biometric_unlock_enabled", true).apply()
                                                status = "Biometric app unlock enabled for the next app launch."
                                            } else {
                                                status = "Biometric confirmation was not completed; the setting remains off."
                                            }
                                        }
                                    }
                                }
                            },
                            onAutoStartOnBootChange = { enabled ->
                                autoStartOnBoot = enabled
                                context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                    .putBoolean("auto_start_on_boot", enabled).apply()
                            },
                            onBatteryOptimizationSettings = {
                                runCatching {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }.onFailure { status = it.message ?: "Could not open battery optimization settings." }
                            }
                        )
                    )
                }
                if (selectedTab == "Settings" && settingsSection in setOf("Server", "Remote")) {
                if (selectedTab == "Settings" && settingsSection == "Server") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = palette.surface)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SERVER CONFIGURATION", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = portText,
                                onValueChange = { value -> if (value.length <= 5 && value.all(Char::isDigit)) portText = value },
                                label = { Text("HTTP port") },
                                enabled = !hosting,
                                singleLine = true
                            )
                            OutlinedButton(
                                onClick = {
                                    val port = portText.toIntOrNull()
                                    if (port == null || port !in 1024..65535) {
                                        status = "Choose a port between 1024 and 65535."
                                    } else {
                                        context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                            .putInt("port", port).apply()
                                        status = "Server port saved. Start hosting to apply it."
                                    }
                                },
                                enabled = !hosting
                            ) { Text("Save server settings") }
                            Text("Engine limits: 16 concurrent connections · 500 MB maximum file · two Tokio workers.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Keep CPU awake while hosting", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text("Optional screen-off reliability aid. Uses more battery and cannot override every OEM network policy.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                                }
                                Switch(
                                    checked = keepAwake,
                                    onCheckedChange = { enabled ->
                                        keepAwake = enabled
                                        context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                            .putBoolean("keep_awake", enabled).apply()
                                        status = if (enabled) "CPU wake lock will be used the next time hosting starts." else "CPU wake lock disabled for the next hosting session."
                                    },
                                    enabled = !hosting
                                )
                            }
                        }
                    }
                }
                if (selectedTab == "Settings" && settingsSection == "Remote") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = palette.surface)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("REMOTE CONTROL PLANE", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text(if (remoteControlConfigured) "Device is configured for outbound remote management." else "Connect this Android host to your Pocket Host fleet control plane.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                            OutlinedTextField(
                                value = controlPlaneEndpoint,
                                onValueChange = { controlPlaneEndpoint = it },
                                label = { Text("Control-plane HTTPS URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = controlPlaneToken,
                                onValueChange = { controlPlaneToken = it },
                                label = { Text("One-time device token") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    runCatching {
                                        RemoteControlAgent.configure(context, controlPlaneEndpoint, controlPlaneToken)
                                        remoteControlConfigured = true
                                        controlPlaneToken = ""
                                        if (hosting) RemoteControlAgent.start(context)
                                        status = if (hosting) "Remote control connected. This host agent will poll the control plane while its host service is active." else "Remote connection saved securely. The agent starts when hosting starts."
                                    }.onFailure { status = it.message ?: "Could not configure remote control." }
                                }, enabled = controlPlaneEndpoint.isNotBlank() && controlPlaneToken.length >= 32) {
                                    Text("Save remote connection")
                                }
                                OutlinedButton(onClick = {
                                    RemoteControlAgent.clear(context)
                                    remoteControlConfigured = false
                                    controlPlaneToken = ""
                                    status = "Remote control credentials removed from this device."
                                }, enabled = remoteControlConfigured) { Text("Disconnect") }
                            }
                            Text("This host agent makes outbound HTTPS requests only. The control plane cannot open inbound ports or execute arbitrary shell commands on the host.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }
                }

                }
                if (selectedTab == "Dashboard") {
                    PocketHostDashboardScreen(
                        context = context,
                        state = PocketHostDashboardState(
                            hosting = hosting,
                            importing = importing,
                            status = status,
                            serviceStatus = serviceStatus,
                            importedSummary = importedSummary,
                            allowLan = allowLan,
                            lanAddress = lanAddress,
                            portText = portText,
                            tunnelStatus = tunnelStatus,
                            requests = requests,
                            activeConnections = activeConnections,
                            bytesServed = bytesServed,
                            uptimeText = uptimeText,
                            todayRequests = todayRequests,
                            todayBytes = todayBytes,
                            capacityInfo = capacityInfo,
                            capacityDetailsExpanded = capacityDetailsExpanded,
                            capacityPercent = capacityPercent,
                            capacityDuration = capacityDuration,
                            capacityBenchmark = capacityBenchmark,
                            deviceTelemetry = deviceTelemetry,
                            selectedCapacityConcurrency = selectedCapacityConcurrency,
                            notificationPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ),
                        actions = PocketHostDashboardActions(
                            onStopHosting = { context.stopService(Intent(context, HostingService::class.java)) },
                            onStartHosting = { startHosting(false) },
                            onRequestNotificationPermission = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            onStatusChange = { status = it },
                            onOpenSites = { selectedTab = "Sites" },
                            onCapacityDetailsExpandedChange = { capacityDetailsExpanded = it },
                            onCapacityPercentChange = { capacityPercent = it },
                            onCapacityDurationChange = { capacityDuration = it },
                            onCapacityInfoChange = { capacityInfo = it },
                            onCapacityBenchmarkChange = { capacityBenchmark = it },
                            onSelectedCapacityConcurrencyChange = { selectedCapacityConcurrency = it },
                            onExportDailyReport = { dailyReportExportPicker.launch("pockethost-daily-report.json") }
                        )
                    )
                }
                if (selectedTab == "Activity") {
                    PocketHostActivityScreen(
                        events = recentEvents,
                        filterMode = logFilterMode,
                        onFilterModeChange = { logFilterMode = it },
                        filterText = logFilter,
                        onFilterTextChange = { logFilter = it },
                        showClearConfirmation = showClearLogsConfirmation,
                        onShowClearConfirmationChange = { showClearLogsConfirmation = it },
                        onExportLogs = { logExportPicker.launch("pockethost-operations.jsonl") },
                        onExportDiagnostics = { diagnosticsExportPicker.launch("pockethost-diagnostics.zip") },
                        onClearLogs = {
                            OperationalLog.clear(context)
                            recentEvents = emptyList()
                            logFilter = ""
                            logFilterMode = "All"
                            status = "Operational logs cleared."
                        }
                    )
                }
                if (selectedTab == "Sites") {
                    PocketHostSitesScreen(
                        context = context,
                        state = PocketHostSitesState(
                            importedSummary = importedSummary,
                            siteFiles = remember(importedSummary, siteFileRefresh) { SiteFileManager.list(context) },
                            hosting = hosting,
                            importing = importing,
                            showCreateSiteFile = showCreateSiteFile,
                            newSiteFilePath = newSiteFilePath,
                            newSiteFileContent = newSiteFileContent,
                            renamingSiteFilePath = renamingSiteFilePath,
                            renamedSiteFilePath = renamedSiteFilePath,
                            editingSiteFilePath = editingSiteFilePath,
                            editingSiteFileContent = editingSiteFileContent,
                            pendingDeleteSiteFile = pendingDeleteSiteFile,
                            pendingIndexSiteFile = pendingIndexSiteFile,
                            backupPassphrase = backupPassphrase,
                            pythonHosting = pythonHosting,
                            nodeHosting = nodeHosting,
                            previousReleases = previousReleases
                        ),
                        actions = PocketHostSitesActions(
                            onShowCreateSiteFileChange = { showCreateSiteFile = it },
                            onNewSiteFilePathChange = { newSiteFilePath = it },
                            onNewSiteFileContentChange = { newSiteFileContent = it },
                            onRenamingSiteFilePathChange = { renamingSiteFilePath = it },
                            onRenamedSiteFilePathChange = { renamedSiteFilePath = it },
                            onEditingSiteFilePathChange = { editingSiteFilePath = it },
                            onEditingSiteFileContentChange = { editingSiteFileContent = it },
                            onPendingDeleteSiteFileChange = { pendingDeleteSiteFile = it },
                            onPendingIndexSiteFileChange = { pendingIndexSiteFile = it },
                            onBackupPassphraseChange = { backupPassphrase = it },
                            onUploadIndividualFile = { singleFilePicker.launch(arrayOf("*/*")) },
                            onImportFolder = { folderPicker.launch(null) },
                            onImportZip = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                            onExportBackup = { backupExportPicker.launch("pockethost-app.afbackup") },
                            onRestoreBackup = { backupRestorePicker.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) },
                            onPreviewSite = { context.startActivity(Intent(context, SitePreviewActivity::class.java)) },
                            onFilesChanged = { siteFileRefresh++ },
                            onStatusChange = { status = it },
                            onImportedSummaryChange = { importedSummary = it },
                            onPreviousReleasesChange = { previousReleases = it }
                        )
                    )
                }
                if (selectedTab == "Settings") {
if (settingsSection in setOf("Relay node", "Relay host", "Cloudflare")) {
                if (selectedTab == "Settings" && settingsSection == "Relay node") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("NATIVE ASSAYFLUX RELAY NODE", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = {
                                runCatching { RelayNodeIdentityStore.getOrCreate() }
                                    .onSuccess {
                                        nativeRelayIdentity = it
                                        status = "Relay node identity created in Android Keystore."
                                    }
                                    .onFailure { status = it.message ?: "Could not create Relay node identity." }
                            }) { Text(if (nativeRelayIdentity == null) "Create node identity" else "Refresh identity") }
                            Text(
                                nativeRelayIdentity?.nodeId ?: if (RelayNodeIdentityStore.exists()) "Identity exists on this device" else "No node identity yet",
                                color = palette.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            when (nativeRelayStatus) {
                                "RUNNING" -> "Native visitor gateway is running. Public access still depends on valid DNS proof, TLS and your network edge."
                                "UNAVAILABLE" -> "Native Relay library is unavailable in this APK."
                                else -> if (nativeRelayConfigured) "Node configuration is encrypted on this device. Relay is stopped." else "No native Relay Node configuration has been imported."
                            },
                            color = palette.onSurfaceVariant, fontSize = 14.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Start Relay after device reboot", color = palette.onSurface, fontSize = 14.sp)
                                Text("Only starts when a node configuration is saved.", color = palette.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Switch(
                                checked = autoStartRelayOnBoot,
                                onCheckedChange = { enabled ->
                                    autoStartRelayOnBoot = enabled
                                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                                        .edit().putBoolean("relay_auto_start_on_boot", enabled).commit()
                                    status = if (enabled) "Native Relay boot recovery enabled." else "Native Relay boot recovery disabled."
                                }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { nativeRelayConfigPicker.launch(arrayOf("application/json", "text/plain", "*/*")) }
                            ) { Text(if (nativeRelayConfigured) "Replace node config" else "Import node config") }
                            OutlinedButton(
                                onClick = {
                                    NativeRelayService.start(context)
                                    status = "Starting native Relay Node foreground service…"
                                },
                                enabled = nativeRelayConfigured && nativeRelayStatus != "RUNNING"
                            ) { Text("Start Node") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    NativeRelayService.stop(context)
                                    status = "Stopping native Relay Node…"
                                },
                                enabled = nativeRelayStatus == "RUNNING"
                            ) { Text("Stop Node") }
                            OutlinedButton(
                                onClick = {
                                    NativeRelayService.stop(context)
                                    runCatching { RelayNodeConfigStore.clear(context) }
                                        .onSuccess {
                                            nativeRelayConfigured = false
                                            nativeRelayStatus = "STOPPED"
                                            status = "Native Relay configuration and encryption key cleared; host revocation history is retained."
                                        }
                                        .onFailure { status = it.message ?: "Could not clear native Relay configuration." }
                                },
                                enabled = nativeRelayConfigured
                            ) { Text("Remove config") }
                        }
                        if (nativeRelayConfigured) {
                            Text("AUTOMATIC HTTPS CERTIFICATES", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = relayAcmeEmail,
                                onValueChange = { relayAcmeEmail = it },
                                label = { Text("ACME contact email") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = relayAcmeTermsAccepted,
                                    onCheckedChange = { relayAcmeTermsAccepted = it }
                                )
                                Text("I agree to the Let's Encrypt Terms of Service", color = palette.onSurfaceVariant, fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = {
                                runCatching {
                                    RelayNodeConfigStore.updateAcmeSettings(context, relayAcmeEmail, relayAcmeTermsAccepted)
                                }.onSuccess {
                                    status = "ACME contact and Terms acceptance saved in encrypted Relay configuration."
                                }.onFailure {
                                    status = it.message ?: "Could not save ACME settings."
                                }
                            }) { Text("Save automatic TLS settings") }
                            Text(
                                "HTTP-01 issuance requires public DNS A/AAAA records and router forwarding: TCP 80 to this phone's Relay HTTP port (default 8082), and TCP 443 to its HTTPS port (default 8444). The foreground service checks certificate expiry every 12 hours and renews within 30 days.",
                                color = palette.onSurfaceVariant, fontSize = 12.sp
                            )
                            Text("OWNER-ISSUED HOST CREDENTIALS", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("Registered host IDs: ${nativeRelayHostIds.joinToString().ifBlank { "none" }}", color = palette.onSurfaceVariant, fontSize = 12.sp)
                            OutlinedTextField(
                                value = nativeRelayHostIdText,
                                onValueChange = { nativeRelayHostIdText = it },
                                label = { Text("Registered host UUID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        nativeRelayHostIds = RelayCredentialIssuer.registeredHostIds(context)
                                        status = "Relay host registry refreshed."
                                    }, modifier = Modifier.weight(1f)) { Text("Refresh hosts") }
                                    Button(onClick = {
                                        runCatching { NativeRelay.createPairingInvite(nativeRelayHostIdText) }
                                            .onSuccess { invite ->
                                                if (invite.startsWith("ERROR:")) {
                                                    nativeRelayPairingInvite = null
                                                    status = invite
                                                } else {
                                                    nativeRelayPairingInvite = invite
                                                    status = "A single-use pairing invite was created. It expires in 2 minutes and is bound to this host UUID."
                                                }
                                            }
                                            .onFailure { status = it.message ?: "Could not create Relay pairing invite." }
                                    }, enabled = nativeRelayStatus == "RUNNING", modifier = Modifier.weight(1f)) {
                                        Text("Create pairing invite", maxLines = 2)
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        runCatching { RelayCredentialIssuer.issueOrRotate(context, nativeRelayHostIdText) }
                                            .onSuccess { issued ->
                                                nativeRelayCredentialToken = issued.token
                                                nativeRelayHostIds = RelayCredentialIssuer.registeredHostIds(context)
                                                if (nativeRelayStatus == "RUNNING") {
                                                    NativeRelayService.stop(context)
                                                    NativeRelayService.start(context)
                                                }
                                                status = "A new host credential was issued. Copy it to the host device now; the raw token is not stored on this node."
                                            }
                                            .onFailure { status = it.message ?: "Could not issue Relay host credential." }
                                    }, modifier = Modifier.weight(1f)) { Text("Issue / rotate", maxLines = 2) }
                                    OutlinedButton(onClick = {
                                        runCatching { RelayCredentialIssuer.revoke(context, nativeRelayHostIdText) }
                                            .onSuccess { revoked ->
                                                if (revoked) {
                                                    nativeRelayCredentialToken = null
                                                    nativeRelayHostIds = RelayCredentialIssuer.registeredHostIds(context)
                                                    NativeRelayService.stop(context)
                                                    if (nativeRelayHostIds.isNotEmpty()) NativeRelayService.start(context)
                                                    status = "Host credential revoked and its routes removed."
                                                } else status = "That host is not registered."
                                            }
                                            .onFailure { status = it.message ?: "Could not revoke Relay host." }
                                    }, modifier = Modifier.weight(1f)) { Text("Revoke host", maxLines = 2) }
                                }
                            }
                            nativeRelayPairingInvite?.let { invite ->
                                Text("Single-use pairing invite (expires in 2 minutes):", color = palette.onSurfaceVariant, fontSize = 12.sp)
                                Text(invite, color = palette.onSurface, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                OutlinedButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("AssayFlux Relay pairing invite", invite))
                                    Toast.makeText(context, "Pairing invite copied", Toast.LENGTH_SHORT).show()
                                }) { Text("Copy pairing invite") }
                            }
                            nativeRelayCredentialToken?.let { token ->
                                Text("Copy this credential to the host device. It is shown only for this issuance:", color = palette.onSurfaceVariant, fontSize = 12.sp)
                                Text(token, color = palette.onSurface, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                OutlinedButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("AssayFlux Relay host credential", token))
                                    Toast.makeText(context, "Relay credential copied", Toast.LENGTH_SHORT).show()
                                }) { Text("Copy credential") }
                            }
                        }
                        Text(
                            "This is the inbound visitor gateway on this phone, separate from the outbound host connection. Pairing invites are short-lived and single-use; ACME HTTP-01 issues and renews certificates. DNS TXT ownership proof is currently checked at node startup, not continuously.",
                            color = palette.onSurfaceVariant, fontSize = 13.sp
                        )
                        TextButton(onClick = { settingsSection = "DNS management" }) { Text("DNS management options") }
                    }
                }
                }

                if (selectedTab == "Settings" && settingsSection == "Relay host") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ASSAYFLUX RELAY", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (relayStatus) {
                                "CONNECTED" -> "Independent Relay connection is active."
                                "CONNECTING" -> "Connecting to your owner-operated Relay Node…"
                                "RETRYING" -> "Relay connection lost. Reconnecting with backoff."
                                "ERROR" -> "Relay credentials could not be read. Re-save the connection."
                                else -> if (relayConfigured) "Relay credentials are encrypted on this device." else "No independent Relay connection configured."
                            },
                            color = palette.onSurfaceVariant, fontSize = 14.sp
                        )
                        OutlinedTextField(
                            value = relayEndpointText,
                            onValueChange = { relayEndpointText = it },
                            label = { Text("Your Relay HTTPS endpoint") },
                            singleLine = true,
                            enabled = relayStatus !in setOf("CONNECTED", "CONNECTING"),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = relayTokenText,
                            onValueChange = { relayTokenText = it },
                            label = { Text("Dedicated Relay host credential") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = relayStatus !in setOf("CONNECTED", "CONNECTING"),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Host ID: ${RelayCredentialStore.hostDeviceId(context)}", color = palette.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Relay host ID", RelayCredentialStore.hostDeviceId(context)))
                                Toast.makeText(context, "Host ID copied", Toast.LENGTH_SHORT).show()
                            }) { Text("Copy ID") }
                        }
                        OutlinedTextField(
                            value = relayPairingInviteText,
                            onValueChange = { relayPairingInviteText = it.trim() },
                            label = { Text("Owner-issued 2-minute pairing invite") },
                            singleLine = true,
                            enabled = relayStatus !in setOf("CONNECTED", "CONNECTING"),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val paired = runCatching {
                                        withContext(Dispatchers.IO) {
                                            RelayPairingClient.pair(context, relayEndpointText, relayPairingInviteText)
                                        }
                                    }
                                    paired.onSuccess { hostId ->
                                        relayPairingInviteText = ""
                                        relayConfigured = true
                                        relayStatus = "STOPPED"
                                        if (hosting) RelayAgent.start(context)
                                        status = "Relay host paired successfully ($hostId). The credential is stored in this phone's Android Keystore."
                                    }.onFailure {
                                        relayConfigured = RelayCredentialStore.isConfigured(context)
                                        relayTokenText = ""
                                        status = (it.message ?: "Could not pair this host with the Relay Node.") +
                                            if (relayConfigured) " The generated credential was retained on this phone so a committed pairing can still connect." else ""
                                    }
                                }
                            },
                            enabled = relayEndpointText.isNotBlank() && relayPairingInviteText.length == 32 &&
                                relayStatus !in setOf("CONNECTED", "CONNECTING")
                        ) { Text("Pair with owner Relay") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    runCatching {
                                        RelayCredentialStore.configure(context, relayEndpointText, relayTokenText)
                                        relayTokenText = ""
                                        relayConfigured = true
                                        if (hosting) RelayAgent.start(context)
                                        status = if (hosting) "Dedicated Relay credentials saved. Connecting to your Relay Node." else "Dedicated Relay credentials saved securely. Start hosting to connect."
                                    }.onFailure { status = it.message ?: "Could not save Relay credentials." }
                                },
                                enabled = relayEndpointText.isNotBlank() && relayTokenText.length >= 32 &&
                                    relayStatus !in setOf("CONNECTED", "CONNECTING")
                            ) { Text("Save Relay connection") }
                            OutlinedButton(
                                onClick = {
                                    if (hosting) {
                                        RelayAgent.start(context)
                                        relayStatus = RelayAgent.status()
                                        status = "Relay connection requested."
                                    } else {
                                        status = "Start website hosting before connecting the Relay."
                                    }
                                },
                                enabled = relayConfigured && hosting && relayStatus !in setOf("CONNECTED", "CONNECTING")
                            ) { Text("Connect") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    RelayAgent.stop()
                                    relayStatus = RelayAgent.status()
                                    status = "AssayFlux Relay disconnected. Local and Cloudflare hosting remain available."
                                },
                                enabled = relayStatus in setOf("CONNECTED", "CONNECTING", "RETRYING", "ERROR")
                            ) { Text("Stop Relay") }
                            OutlinedButton(
                                onClick = {
                                    RelayAgent.stop()
                                    runCatching { RelayCredentialStore.clear(context) }
                                        .onSuccess {
                                            relayConfigured = false
                                            relayTokenText = ""
                                            relayEndpointText = ""
                                            relayStatus = "STOPPED"
                                            status = "Dedicated Relay credentials removed from this device."
                                        }
                                        .onFailure { status = it.message ?: "Could not remove Relay credentials." }
                                },
                                enabled = relayConfigured
                            ) { Text("Disconnect") }
                        }
                        Text(
                            "This is independent of the fleet Remote Control Plane and Cloudflare. The Relay Node must be reachable from the public Internet; this phone connects outbound and never opens an Internet-facing Relay port.",
                            color = palette.onSurfaceVariant, fontSize = 13.sp
                        )
                    }
                }
                }

                if (selectedTab == "Settings" && settingsSection == "Cloudflare") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CLOUDFLARE TUNNEL", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (tunnelStatus) {
                                "CONNECTED" -> "Tunnel connected to Cloudflare."
                                "CONNECTING" -> "Tunnel process started; waiting for an active edge connection."
                                "ERROR" -> "Tunnel process stopped with an error. Check the service status."
                                else -> if (cloudflareTokenSaved) "Named tunnel token is encrypted on this device." else "No tunnel token saved."
                            },
                            color = palette.onSurfaceVariant, fontSize = 14.sp
                        )
                        OutlinedTextField(
                            value = cloudflareTokenText,
                            onValueChange = { cloudflareTokenText = it },
                            label = { Text("Cloudflare named-tunnel token") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = tunnelStatus != "CONNECTED" && tunnelStatus != "CONNECTING",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    runCatching { CloudflareTokenStore.save(context, cloudflareTokenText) }
                                        .onSuccess {
                                            cloudflareTokenText = ""
                                            cloudflareTokenSaved = true
                                            status = "Cloudflare token encrypted with Android Keystore."
                                        }
                                        .onFailure { status = it.message ?: "Could not save Cloudflare token." }
                                },
                                enabled = cloudflareTokenText.isNotBlank() && tunnelStatus != "CONNECTED" && tunnelStatus != "CONNECTING"
                            ) { Text("Save token securely") }
                            OutlinedButton(
                                onClick = {
                                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                        .putBoolean("tunnel_enabled", true).apply()
                                    if (cloudflareTokenSaved) {
                                        if (hosting) {
                                            // Re-enter the foreground service command path so the
                                            // bounded reconnect supervisor owns this tunnel session.
                                            ContextCompat.startForegroundService(context, Intent(context, HostingService::class.java))
                                        } else {
                                            startHosting(false)
                                        }
                                    } else {
                                        status = "Save a Cloudflare tunnel token first."
                                    }
                                },
                                enabled = tunnelStatus != "CONNECTED" && tunnelStatus != "CONNECTING"
                            ) { Text("Start tunnel") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                        .putBoolean("tunnel_enabled", false).apply()
                                    scope.launch {
                                        tunnelStatus = withContext(Dispatchers.IO) { CloudflareTunnelRuntime.stop() }
                                        status = "Cloudflare Tunnel stopped. Static hosting remains available."
                                    }
                                },
                                enabled = tunnelStatus == "CONNECTED" || tunnelStatus == "CONNECTING" || tunnelStatus == "ERROR"
                            ) { Text("Stop tunnel") }
                            OutlinedButton(
                                onClick = {
                                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                        .putBoolean("tunnel_enabled", false).apply()
                                    CloudflareTokenStore.clear(context)
                                    cloudflareTokenSaved = false
                                    status = "Encrypted Cloudflare token removed from this device."
                                },
                                enabled = cloudflareTokenSaved && tunnelStatus == "STOPPED"
                            ) { Text("Remove token") }
                        }
                        Text(
                            "Reuse your existing tunnel. Public Hostname origin: http://127.0.0.1:${portText.toIntOrNull() ?: 8080}.",
                            color = palette.onSurfaceVariant, fontSize = 14.sp
                        )
                        Text(
                            "Use Cloudflare Access for private sites.",
                            color = palette.onSurfaceVariant, fontSize = 14.sp
                        )
                    }
                }
                }

                }

if (settingsSection in setOf("Python", "Node.js")) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("TRUSTED APPLICATION CODE ONLY", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Text("Python and Node run in the separate-UID runtime companion. They share that companion UID and are not isolated from each other as hostile-code sandboxes. Run only owner-controlled code you trust.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("I understand and will run trusted code only", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                                Text("Required to start a runtime.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                            }
                            Switch(checked = trustedRuntimeCodeAck, onCheckedChange = { accepted ->
                                trustedRuntimeCodeAck = accepted
                                val prefs = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("trusted_runtime_code_ack", accepted).apply()
                                if (!accepted) {
                                    prefs.edit().putBoolean("python_enabled", false).putBoolean("node_enabled", false).apply()
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            runCatching { PythonHostRuntime.stop(context) }
                                            runCatching { NodeHostRuntime.stop(context) }
                                        }
                                        pythonHosting = false
                                        nodeHosting = false
                                        if (hosting) {
                                            runCatching { NativeEngine.stop() }
                                            startHosting(false)
                                        }
                                    }
                                    status = "Optional runtimes stopped. Static hosting remains available."
                                }
                            })
                        }
                    }
                }

                }

if (settingsSection == "Python") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("PYTHON BACKEND · CPYTHON 3.11", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(pythonProjectSummary, color = palette.onSurfaceVariant, fontSize = 14.sp)
                        OutlinedButton(
                            onClick = { pythonProjectPicker.launch(null) },
                            enabled = !importing && !pythonHosting,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Import Python WSGI project") }
                        OutlinedTextField(
                            value = pythonPortText,
                            onValueChange = { value -> if (value.length <= 5 && value.all(Char::isDigit)) pythonPortText = value },
                            label = { Text("Python HTTP port") },
                            enabled = !pythonHosting,
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val port = pythonPortText.toIntOrNull()
                                    val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                                    // Compare against the port actually persisted for the running static engine,
                                    // not an unsaved value currently typed into the Advanced Mode field.
                                    val staticPort = preferences.getInt("port", 8080)
                                    val root = preferences.getString("python_project_root", File(context.filesDir, "python-project").absolutePath)
                                        ?: File(context.filesDir, "python-project").absolutePath
                                    if (port == null || port !in 1024..65535 || port == staticPort) {
                                        status = "Choose a Python port from 1024 to 65535, different from the static server port."
                                    } else if (!File(root, "wsgi.py").isFile) {
                                        status = "Import a Python project containing wsgi.py first."
                                    } else {
                                        context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                            .putBoolean("python_enabled", true).putInt("python_port", port).apply()
                                        startHosting(false)
                                    }
                                },
                                enabled = !pythonHosting && !importing
                            ) { Text("Start Python") }
                            OutlinedButton(
                                onClick = {
                                    context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit().putBoolean("python_enabled", false).apply()
                                    scope.launch {
                                        withContext(Dispatchers.IO) { runCatching { PythonHostRuntime.stop(context) } }
                                        pythonHosting = false
                                        status = "Python backend stopped. Static hosting remains available."
                                    }
                                },
                                enabled = pythonHosting
                            ) { Text("Stop Python") }
                        }
                        if (pythonHosting) Text(
                            if (allowLan && lanAddress != null) "Python preview: http://$lanAddress:$pythonPortText" else "Python preview: http://127.0.0.1:$pythonPortText",
                            color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text("Runs trusted WSGI code in the separate-UID runtime companion. Loopback by default; LAN access follows your network setting. Per-project hostile-code isolation is not provided.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                    }
                }

                }

if (settingsSection == "Hostnames & access") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("CUSTOM HOSTNAME ROUTING", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Text("Map a domain to an imported website or backend. Configure Cloudflare's public hostname origin to the Rust static server port.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        OutlinedTextField(value = hostnameText, onValueChange = { hostnameText = it.trim().lowercase() },
                            label = { Text("Hostname, e.g. app.example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("static", "python-wsgi", "node").forEach { runtime ->
                                FilterChip(selected = hostnameRuntime == runtime, onClick = { hostnameRuntime = runtime },
                                    label = { Text(if (runtime == "python-wsgi") "Python" else runtime.replaceFirstChar { it.uppercase() }) })
                            }
                        }
                        Button(onClick = {
                            val host = hostnameText.trim().trimEnd('.').lowercase()
                            val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                            val root = when (hostnameRuntime) {
                                "static" -> File(context.filesDir, "site").absolutePath
                                "python-wsgi" -> preferences.getString("python_project_root", File(context.filesDir, "python-project").absolutePath) ?: ""
                                else -> preferences.getString("node_project_root", File(context.filesDir, "node-project-default").absolutePath) ?: ""
                            }
                            val id = when (hostnameRuntime) { "static" -> "static-default"; "python-wsgi" -> "python-default"; else -> "node-default" }
                            val name = when (hostnameRuntime) { "static" -> "Static website"; "python-wsgi" -> "Python application"; else -> "Node application" }
                            runCatching {
                                val current = SiteRegistry.list(context).firstOrNull { it.id == id }
                                SiteRegistry.put(context, SiteRegistry.Site(id, name, hostnameRuntime, root,
                                    (current?.hostnames.orEmpty() + host).distinct(), true))
                                if (preferences.getBoolean("python_enabled", false)) {
                                    runCatching { PythonHostRuntime.stop(context) }
                                    val pythonHosts = SiteRegistry.pythonHostnameMap(context)
                                    val pythonRoot = preferences.getString("python_project_root", File(context.filesDir, "python-project").absolutePath)
                                        ?: File(context.filesDir, "python-project").absolutePath
                                    if (pythonHosts.length() > 0) PythonHostRuntime.startSites(context, pythonHosts.toString(),
                                        preferences.getInt("python_port", 8081))
                                    else PythonHostRuntime.start(context, pythonRoot, preferences.getInt("python_port", 8081))
                                }
                                if (hosting) {
                                    MetricsStore.recordSample(context, NativeEngine.requestCount(), NativeEngine.bytesServed())
                                    NativeEngine.stop()
                                    val routes = SiteRegistry.hostRoutingJson(context, preferences.getInt("python_port", 8081), preferences.getInt("node_port", 3000))
                                    val result = if (routes.length() == 0) NativeEngine.start(File(context.filesDir, "site").absolutePath,
                                        preferences.getInt("port", 8080), preferences.getBoolean("allow_lan", false))
                                    else NativeEngine.startMultiSite(File(context.filesDir, "site").absolutePath, routes.toString(),
                                        preferences.getInt("port", 8080), preferences.getBoolean("allow_lan", false))
                                    check(result == "OK") { result }
                                }
                            }.onSuccess {
                                hostnameText = ""
                                status = "Hostname route saved. It is active now if hosting is running."
                            }.onFailure { status = it.message ?: "Could not save hostname route." }
                        }, enabled = hostnameText.isNotBlank() && !importing) { Text("Save hostname route") }
                        SiteRegistry.list(context).filter { it.hostnames.isNotEmpty() }.forEach { site ->
                            Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(site.name + " · " + site.hostnames.joinToString(), color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Visitor access · " + site.accessMode.uppercase(), color = palette.onSurfaceVariant, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("public", "private", "restricted").forEach { mode ->
                                        FilterChip(selected = site.accessMode == mode, onClick = {
                                            runCatching {
                                                SiteRegistry.setAccessMode(context, site.id, mode)
                                                reloadSiteRoutes()
                                                issuedVisitorCredential = null
                                                status = "Visitor access for " + site.name + " changed to " + mode + "."
                                            }.onFailure { status = it.message ?: "Could not update visitor access." }
                                        }, label = { Text(mode.replaceFirstChar { it.uppercase() }) })
                                    }
                                }
                                if (site.accessMode != "public") {
                                    if (site.accessMode == "restricted") {
                                        OutlinedTextField(value = visitorUsername, onValueChange = { visitorUsername = it.take(64) },
                                            label = { Text("Invited visitor name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    } else {
                                        Text("Private mode uses one shared visitor credential.", color = palette.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                    Button(onClick = {
                                        runCatching {
                                            val username = if (site.accessMode == "private") "visitor" else visitorUsername.trim()
                                            val token = SiteRegistry.issueVisitor(context, site.id, username)
                                            issuedVisitorCredential = site.id to token
                                            visitorUsername = ""
                                            reloadSiteRoutes()
                                            status = "Visitor credential issued. Copy it now; it is shown only once."
                                        }.onFailure { status = it.message ?: "Could not issue visitor credential." }
                                    }, enabled = site.accessMode == "private" || visitorUsername.isNotBlank()) {
                                        Text(if (site.accessMode == "private") "Create / rotate shared credential" else "Invite visitor")
                                    }
                                    site.visitorCredentials.keys.sorted().forEach { username ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Text(username, color = palette.onSurfaceVariant, fontSize = 13.sp)
                                            TextButton(onClick = {
                                                runCatching {
                                                    SiteRegistry.revokeVisitor(context, site.id, username)
                                                    if (issuedVisitorCredential?.first == site.id) issuedVisitorCredential = null
                                                    reloadSiteRoutes()
                                                    status = "Visitor " + username + " revoked from " + site.name + "."
                                                }.onFailure { status = it.message ?: "Could not revoke visitor." }
                                            }) { Text("Revoke") }
                                        }
                                    }
                                    issuedVisitorCredential?.takeIf { it.first == site.id }?.let { (_, token) ->
                                        Text("One-time visitor password. Username: " + if (site.accessMode == "private") "visitor" else "the invited visitor name",
                                            color = palette.onSurfaceVariant, fontSize = 12.sp)
                                        Text(token, color = palette.onSurface, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                        OutlinedButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Pocket Host visitor credential", token))
                                            status = "Visitor credential copied."
                                        }) { Text("Copy credential") }
                                    }
                                }
                            }
                            Divider(color = palette.outline)
                        }
                    }
                }

                }

if (settingsSection == "Node.js") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("NODE.JS 24 LTS · EMBEDDED RUNTIME", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(nodeProjectSummary, color = palette.onSurfaceVariant, fontSize = 14.sp)
                        OutlinedButton(onClick = { nodeProjectPicker.launch(null) }, enabled = !importing && !nodeHosting,
                            modifier = Modifier.fillMaxWidth()) { Text("Import Node.js project") }
                        OutlinedTextField(value = nodePortText,
                            onValueChange = { value -> if (value.length <= 5 && value.all(Char::isDigit)) nodePortText = value },
                            label = { Text("Node.js HTTP port") }, enabled = !nodeHosting, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = {
                                val port = nodePortText.toIntOrNull()
                                val root = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                                    .getString("node_project_root", File(context.filesDir, "node-project-default").absolutePath)
                                    ?: File(context.filesDir, "node-project-default").absolutePath
                                val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
                                val staticPort = preferences.getInt("port", 8080)
                                val pythonPort = preferences.getInt("python_port", 8081)
                                if (port == null || port !in 1024..65535 || port == staticPort || port == pythonPort) {
                                    status = "Choose a valid Node port different from the static and Python ports."
                                } else {
                                    preferences.edit().putInt("node_port", port).putBoolean("node_enabled", true)
                                        .putString("node_project_root", root).apply()
                                    startHosting(false)
                                    status = "Node.js runtime requested. The foreground supervisor will launch the separate Node.js process."
                                }
                            }, enabled = !nodeHosting && !importing) { Text("Start Node.js") }
                            OutlinedButton(onClick = {
                                context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit().putBoolean("node_enabled", false).apply()
                                NodeHostRuntime.stop(context)
                                nodeHosting = false
                                status = "Stopping the isolated Node.js process."
                            }, enabled = nodeHosting) { Text("Stop Node.js") }
                        }
                        if (nodeHosting) Text("Node.js preview: http://127.0.0.1:$nodePortText", color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Runs in the separate-UID runtime companion, not a per-project sandbox. Prebundle node_modules; subprocess scripts and native add-ons may not work.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                    }
                }

                }

if (settingsSection == "Hostnames & access") {
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surface)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("CONNECTION & SECURITY", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("Cloudflare provides public HTTPS to this host's local Rust website listener.", color = palette.onSurfaceVariant)
                            Text("Private and restricted visitor access is available in hostname routing. Use Cloudflare Access as an additional edge policy if desired.", color = palette.onSurfaceVariant)
                            Text("Android and OEM power policies can interrupt background hosting.", color = palette.onSurfaceVariant)
                            OutlinedButton(onClick = { selectedTab = "Sites" }, modifier = Modifier.fillMaxWidth()) {
                                Text("Open backup and restore")
                            }
                        }
                    }
                }

                if (settingsSection == "DNS management") {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SERVICE COMING SOON", color = palette.primary, fontWeight = FontWeight.Bold)
                            Text("Automatic DNS record management", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("Pocket Host does not create or update public DNS records yet. Native Relay currently validates its configured DNS TXT ownership proof when the node starts; it does not continuously revalidate that proof.", color = palette.onSurfaceVariant)
                            Text("Until DNS management is implemented, configure A/AAAA records and router forwarding with your DNS provider, then restart the Relay Node after changing ownership records.", color = palette.onSurfaceVariant)
                        }
                    }
                }
                if (settingsSection == "Cloudflare Access") {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SERVICE COMING SOON", color = palette.primary, fontWeight = FontWeight.Bold)
                            Text("In-app Cloudflare Access management", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("Pocket Host does not create, edit, or verify Cloudflare Access policies. Configure Access in the Cloudflare dashboard. Do not treat this app as proof that an external Access policy is active.", color = palette.onSurfaceVariant)
                        }
                    }
                }
                if (settingsSection == "Network") {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("NETWORK & PUBLIC ACCESS", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("Choose a dedicated page for the native Relay Node, outbound Relay host, Cloudflare Tunnel, DNS management, Cloudflare Access, or hostname and visitor access.", color = palette.onSurfaceVariant)
                            Text("Each connector has independent credentials and lifecycle controls. Enable only the route you intend to expose.", color = palette.onSurfaceVariant)
                        }
                    }
                }
                if (settingsSection == "Runtimes") {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = palette.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("OPTIONAL APPLICATION RUNTIMES", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("Python WSGI and Node.js are managed independently. Open their dedicated pages to import a project, configure its port, and start or stop that runtime.", color = palette.onSurfaceVariant)
                            Text("Only run code you own and trust. These runtimes are not a hostile-code sandbox.", color = palette.error)
                        }
                    }
                }

                    if (settingsSection == "About") {
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = palette.surface)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("ABOUT", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("AssayFlux Pocket Host", color = palette.onSurface, fontWeight = FontWeight.Bold)
                            Text("Version 0.1.0 · Designed by AssayFlux", color = palette.onSurfaceVariant)
                            Text("Android host agent powered by Rust and Kotlin, with optional trusted Python/Node runtime providers.", color = palette.onSurfaceVariant)
                        }
                    }
                }
if (settingsSection == "Local access") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Device & access", fontWeight = FontWeight.SemiBold, color = palette.onSurface, fontSize = 17.sp)
                        Text("${if (deviceTelemetry.isNull("batteryPercent")) "Battery unknown" else deviceTelemetry.optInt("batteryPercent").toString() + "% battery"}  ·  ${deviceTelemetry.optString("batteryTemperatureC", "Temp unavailable")}°C  ·  ${deviceTelemetry.optString("networkTransport", "Network unknown")}", color = palette.onSurfaceVariant, fontSize = 13.sp)
                        if ((!deviceTelemetry.isNull("batteryPercent") && deviceTelemetry.optInt("batteryPercent") < 20) ||
                            deviceTelemetry.optString("thermalLabel").uppercase() in listOf("SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")) {
                            Text("Host protection warning: battery is low or thermal pressure is high. Reduce workload and allow the device to cool.", color = palette.error, fontWeight = FontWeight.SemiBold)
                        }
                        val localSiteUrl = if (allowLan && lanAddress != null) "http://$lanAddress:${portText.toIntOrNull() ?: 8080}/" else "http://127.0.0.1:${portText.toIntOrNull() ?: 8080}/"
                        Text(localSiteUrl, color = palette.onSurface, fontFamily = FontFamily.Monospace)
                        val configuredPublicHost = SiteRegistry.list(context).flatMap { it.hostnames }.firstOrNull()
                        if (tunnelStatus == "CONNECTED" && configuredPublicHost != null) {
                            Text("Public URL: https://$configuredPublicHost", color = palette.onSurface,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Pocket Host site URL", localSiteUrl))
                                status = "Local site URL copied."
                            }) { Text("Copy URL") }
                            OutlinedButton(onClick = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, localSiteUrl)
                                }
                                context.startActivity(Intent.createChooser(share, "Share site URL"))
                            }) { Text("Share URL") }
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Allow local network access", color = palette.onSurface, fontWeight = FontWeight.SemiBold)
                                Text("Lets devices on your current Wi-Fi open this site. No visitor authentication is provided.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                            }
                            Switch(checked = allowLan, onCheckedChange = { enabled ->
                                allowLan = enabled
                                context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE).edit()
                                    .putBoolean("allow_lan", enabled).apply()
                            }, enabled = !hosting)
                        }
                        Text(if (allowLan) "LAN mode enabled · port $portText · ${lanAddress ?: "Connect to Wi-Fi to view this host’s LAN address"} · restart hosting after changing this setting." else "Loopback only · LAN and public access disabled.", color = palette.onSurfaceVariant, fontSize = 14.sp)
                        OutlinedButton(
                            onClick = { context.startActivity(Intent(context, SitePreviewActivity::class.java)) },
                            enabled = hosting
                        ) { Text("Preview site") }
                    }
                }
                }
                }
            }
        }
        }
    }
}
}
