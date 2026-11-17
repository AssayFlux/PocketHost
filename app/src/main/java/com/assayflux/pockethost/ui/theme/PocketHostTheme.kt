package com.assayflux.pockethost.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// A restrained, warm-neutral palette: confident navy, paper-like canvas and
// clear semantic colours. Keep surfaces flat and legible rather than decorative.
private val PocketHostLight = lightColorScheme(
    primary = Color(0xFF243B5A), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5EBF2), onPrimaryContainer = Color(0xFF1B2F49),
    secondary = Color(0xFF64748B), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8ECF0), onSecondaryContainer = Color(0xFF334155),
    background = Color(0xFFF5F3EE), onBackground = Color(0xFF20252B),
    surface = Color(0xFFFFFEFC), onSurface = Color(0xFF20252B),
    surfaceVariant = Color(0xFFEDEAE3), onSurfaceVariant = Color(0xFF626A73),
    outline = Color(0xFFDEDAD1), outlineVariant = Color(0xFFEAE6DE),
    error = Color(0xFFB6423B), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9E5E2), onErrorContainer = Color(0xFF7F2925),
    tertiary = Color(0xFF9A7044), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF1E7DA), onTertiaryContainer = Color(0xFF624629)
)
private val PocketHostDark = darkColorScheme(
    primary = Color(0xFFB7C8DE), onPrimary = Color(0xFF1C2D43),
    primaryContainer = Color(0xFF344A65), onPrimaryContainer = Color(0xFFE0E9F4),
    secondary = Color(0xFFB7C2CF), onSecondary = Color(0xFF273442),
    secondaryContainer = Color(0xFF394858), onSecondaryContainer = Color(0xFFDCE4EC),
    background = Color(0xFF171C23), onBackground = Color(0xFFE8E9E7),
    surface = Color(0xFF202731), onSurface = Color(0xFFE8E9E7),
    surfaceVariant = Color(0xFF2C3540), onSurfaceVariant = Color(0xFFBEC5CD),
    outline = Color(0xFF46515D), outlineVariant = Color(0xFF35404B),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF7F2925), onErrorContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFD6B58E), onTertiary = Color(0xFF382510),
    tertiaryContainer = Color(0xFF59432C), onTertiaryContainer = Color(0xFFF2DFCA)
)

private val PocketHostShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PocketHostTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) PocketHostDark else PocketHostLight,
        shapes = PocketHostShapes,
        content = content
    )
}
