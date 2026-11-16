package com.assayflux.pockethost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun BiometricUnlockScreen(onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7F7F5)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text("ASSAYFLUX", color = Color(0xFF1F3A5F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Text("Pocket Host is locked", color = Color(0xFF1C1C1C), fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
            Spacer(Modifier.height(8.dp))
            Text("Authenticate with a strong biometric; Android 11 and later can also use your secure device credential.",
                color = Color(0xFF6B6B6B), fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) { Text("Unlock") }
        }
    }
}
