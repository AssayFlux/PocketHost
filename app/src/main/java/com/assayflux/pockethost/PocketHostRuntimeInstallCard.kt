package com.assayflux.pockethost

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.assayflux.pockethost.runtime.RuntimeWorkerInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PocketHostRuntimeInstallCard() {
    val context = LocalContext.current
    val palette = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(RuntimeWorkerInstaller.isInstalled(context)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (installed) Color(0xFFE3EEDB) else palette.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("PYTHON & NODE.JS RUNTIMES", color = if (installed) Color(0xFF31572D) else palette.primary,
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(
                if (installed) "Isolated runtime companion installed"
                else "One-time runtime component installation",
                color = palette.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
            )
            Text(
                "The runtime companion is bundled inside this PocketHost APK but remains a separate signed Android package. This preserves a separate Android application UID for customer Python and Node.js code. Android requires approval for the companion installation.",
                color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
            if (message.isNotBlank()) Text(message, color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    installed = RuntimeWorkerInstaller.isInstalled(context)
                    if (installed) {
                        message = "Runtime companion is ready."
                    } else if (!RuntimeWorkerInstaller.canRequestInstallPermission(context)) {
                        runCatching {
                            context.startActivity(RuntimeWorkerInstaller.installPermissionSettingsIntent(context)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.onFailure { message = it.message ?: "Could not open Android install permission settings." }
                        if (message.isBlank()) message = "Allow PocketHost to install apps from this source, then return and tap Install runtime companion."
                    } else {
                        busy = true
                        message = "Preparing the signed runtime package…"
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { RuntimeWorkerInstaller.installEmbedded(context) }
                            }
                            busy = false
                            message = result.getOrElse { it.message ?: "Runtime companion installation could not start." }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(when {
                    busy -> "Preparing…"
                    installed -> "Refresh installation status"
                    else -> "Install runtime companion"
                })
            }
            OutlinedButton(
                onClick = {
                    installed = RuntimeWorkerInstaller.isInstalled(context)
                    message = if (installed) "Runtime companion is ready." else "Runtime companion is not installed yet."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Check runtime installation") }
        }
    }
}
