package com.assayflux.pockethost.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PocketHostLight = lightColorScheme(
    primary = Color(0xFF1F3A5F), onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF1F3A5F), onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F7F5), onBackground = Color(0xFF1C1C1C),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFEFEFEB), onSurfaceVariant = Color(0xFF6B6B6B),
    outline = Color(0xFFE2E2DE), error = Color(0xFFC62828),
    tertiary = Color(0xFFB26A00)
)
private val PocketHostDark = darkColorScheme(
    primary = Color(0xFFA9BEDC), onPrimary = Color(0xFF15243A),
    secondary = Color(0xFFA9BEDC), onSecondary = Color(0xFF15243A),
    background = Color(0xFF121821), onBackground = Color(0xFFF1F3F5),
    surface = Color(0xFF1C2633), onSurface = Color(0xFFF1F3F5),
    surfaceVariant = Color(0xFF293646), onSurfaceVariant = Color(0xFFB5BFCC),
    outline = Color(0xFF394657), error = Color(0xFFFFB4AB),
    tertiary = Color(0xFFE5B567)
)
@Composable
fun PocketHostTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) PocketHostDark else PocketHostLight, content = content)
}
