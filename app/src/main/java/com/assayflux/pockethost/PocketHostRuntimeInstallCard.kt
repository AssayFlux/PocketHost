package com.assayflux.pockethost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager

private const val RUNTIME_WORKER_PACKAGE = "com.assayflux.pockethost.worker"

private fun runtimeWorkerInstalled(context: android.content.Context): Boolean = runCatching {
    context.packageManager.getPackageInfo(RUNTIME_WORKER_PACKAGE, 0)
    true
}.getOrDefault(false)

@Composable
internal fun PocketHostRuntimeInstallCard() {
    val context = LocalContext.current
    val palette = MaterialTheme.colorScheme
    var installed by remember { mutableStateOf(runtimeWorkerInstalled(context)) }
    var message by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (installed) Color(0xFFE3EEDB) else palette.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("PYTHON & NODE.JS RUNTIMES",
                color = if (installed) Color(0xFF31572D) else palette.primary,
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(
                if (installed) "Isolated runtime companion installed"
                else "Separate runtime APK required",
                color = palette.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
            )
            Text(
                if (installed) {
                    "The signed Runtime Worker is installed as a separate Android application. Python and Node.js projects execute under its separate application UID."
                } else {
                    "Download both APK files from the same PocketHost Alpha build artifact. Install PocketHost first, then install the separate 'PocketHost Runtime Worker' APK. Return here and check the installation. Do not rename or modify either APK."
                },
                color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
            )
            if (message.isNotBlank()) Text(message, color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    installed = runtimeWorkerInstalled(context)
                    message = if (installed) {
                        "Runtime companion is installed and visible to PocketHost."
                    } else {
                        "Runtime companion not detected. Install the separate Runtime Worker APK, then check again."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Check runtime installation") }
        }
    }
}
