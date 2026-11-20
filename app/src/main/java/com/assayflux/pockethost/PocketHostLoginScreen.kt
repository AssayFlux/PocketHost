package com.assayflux.pockethost

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import com.assayflux.pockethost.management.HostAccountStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Process-scoped Owner session. It is intentionally not persisted across app process restarts. */
internal object PocketHostOwnerSession {
    @Volatile var username: String? = null
}

@Composable
internal fun PocketHostLoginScreen(context: Context, onAuthenticated: (String) -> Unit) {
    val palette = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val ownerExists = remember { runCatching { HostAccountStore.hasOwner(context) }.getOrDefault(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("AF", color = palette.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Text("Pocket Host", color = palette.onSurface, style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold)
        Text("YOUR DEVICE. YOUR CONTROL.", color = palette.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (ownerExists) "Sign in to Pocket Host" else "Create your Owner account",
                    color = palette.onSurface, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (ownerExists) "Unlock your local hosting workspace with your Owner credentials."
                    else "This account controls the local host and its trusted management settings. Choose a unique username and a strong password.",
                    color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = username, onValueChange = { username = it.take(32) },
                    label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it.take(128) },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                )
                if (!ownerExists) {
                    OutlinedTextField(
                        value = confirmation, onValueChange = { confirmation = it.take(128) },
                        label = { Text("Confirm password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                    )
                    Text("Username: 3–32 letters, numbers, dots, dashes or underscores. Password: 12–128 characters.",
                        color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                if (error.isNotBlank()) Text(error, color = palette.error, style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        error = ""
                        if (!ownerExists && password != confirmation) {
                            error = "The passwords do not match."
                            return@Button
                        }
                        if (username.trim().length !in 3..32) {
                            error = "Enter a username between 3 and 32 characters."
                            return@Button
                        }
                        if (password.length !in (if (ownerExists) 1..128 else 12..128)) {
                            error = if (ownerExists) "Enter your password." else "Choose a password with at least 12 characters."
                            return@Button
                        }
                        busy = true
                        val enteredUsername = username.trim()
                        val enteredPassword = password
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    if (ownerExists) {
                                        val account = HostAccountStore.verify(context, enteredUsername, enteredPassword.toCharArray())
                                            ?: throw IllegalArgumentException("The username or password is incorrect.")
                                        if (account.role != "OWNER") throw IllegalArgumentException("Only the Owner account can unlock this workspace.")
                                        account
                                    } else {
                                        HostAccountStore.create(context, enteredUsername, enteredPassword.toCharArray(), "OWNER")
                                    }
                                }
                            }
                            busy = false
                            password = ""
                            confirmation = ""
                            result.onSuccess { account ->
                                PocketHostOwnerSession.username = account.username
                                onAuthenticated(account.username)
                            }.onFailure { failure ->
                                error = when (failure.message) {
                                    "username_must_be_3_to_32_safe_characters" -> "Use 3–32 letters, numbers, dots, dashes or underscores."
                                    "password_must_be_12_to_128_characters" -> "Choose a password with at least 12 characters."
                                    "owner_account_already_exists" -> "An Owner account already exists. Restart the app and sign in."
                                    else -> failure.message ?: "Could not verify your Owner account."
                                }
                            }
                        }
                    },
                    enabled = !busy && username.isNotBlank() && password.isNotEmpty() &&
                        (ownerExists || confirmation.isNotEmpty()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (busy) "Please wait…" else if (ownerExists) "Sign in" else "Create Owner account")
                }
            }
        }
    }
}
