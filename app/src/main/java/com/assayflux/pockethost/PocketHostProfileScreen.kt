package com.assayflux.pockethost

import android.content.Context
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.assayflux.pockethost.management.HostAccountStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PocketHostProfileScreen(
    currentUserId: String,
    onOpenSecuritySettings: () -> Unit
) {
    val context = LocalContext.current
    val palette = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Profile & security", style = MaterialTheme.typography.headlineMedium,
            color = palette.onSurface, fontWeight = FontWeight.Bold)
        Text("Manage the local Owner identity used to protect this PocketHost installation.",
            color = palette.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface),
            shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("USER ID", color = palette.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                Text(currentUserId, color = palette.onSurface, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Text("Local Owner account · credentials encrypted with Android Keystore",
                    color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface),
            shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Change password", color = palette.onSurface, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = currentPassword, onValueChange = { currentPassword = it.take(128) },
                    label = { Text("Current password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = newPassword, onValueChange = { newPassword = it.take(128) },
                    label = { Text("New password (12–128 characters)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it.take(128) },
                    label = { Text("Confirm new password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                if (message.isNotBlank()) Text(message, color = if (message.startsWith("Password changed")) palette.primary else palette.error)
                Button(
                    onClick = {
                        message = when {
                            newPassword.length !in 12..128 -> "Choose a password between 12 and 128 characters."
                            newPassword != confirmPassword -> "The new passwords do not match."
                            currentPassword.isEmpty() -> "Enter your current password."
                            else -> ""
                        }
                        if (message.isNotBlank()) return@Button
                        val old = currentPassword
                        val fresh = newPassword
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { HostAccountStore.changePassword(context, currentUserId, old.toCharArray(), fresh.toCharArray()) }
                            }
                            busy = false
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                            message = if (result.getOrDefault(false)) "Password changed successfully."
                                else result.exceptionOrNull()?.message ?: "Current password is incorrect."
                        }
                    },
                    enabled = !busy && currentPassword.isNotEmpty() && newPassword.isNotEmpty() && confirmPassword.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Updating…" else "Update password") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = palette.surface),
            shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fingerprint & device security", color = palette.onSurface,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Configure biometric unlock and device authentication using Android's secure biometric prompt.",
                    color = palette.onSurfaceVariant)
                OutlinedButton(onClick = onOpenSecuritySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Open security settings")
                }
            }
        }
    }
}
