package com.assayflux.pockethost.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pocket Host's classic editorial theme system, adapted from the palette approach
 * used in AssayFlux Director-X. Theme names are stable preference values.
 */
object PocketHostPalettes {
    val names = listOf(
        "Classic", "Midnight", "Forest", "Burgundy", "Ocean",
        "Graphite", "Sandstone", "Lavender", "Copper", "Arctic"
    )

    private data class Palette(val lightBg: Color, val lightSurface: Color, val lightRaised: Color,
        val lightText: Color, val lightMuted: Color, val accent: Color, val secondary: Color,
        val darkBg: Color, val darkSurface: Color, val darkRaised: Color, val darkText: Color,
        val darkMuted: Color, val darkAccent: Color)

    private fun palette(name: String) = when (name) {
        "Midnight" -> Palette(Color(0xFFF0F1F4),Color(0xFFE3E6EC),Color(0xFFD4DAE4),Color(0xFF202735),Color(0xFF596579),Color(0xFF536B91),Color(0xFF8A744F),Color(0xFF11151D),Color(0xFF1C2330),Color(0xFF283344),Color(0xFFF1F3F7),Color(0xFFB5C0D0),Color(0xFFD5A84B))
        "Forest" -> Palette(Color(0xFFF0F3EA),Color(0xFFE1E9D9),Color(0xFFD0DDC8),Color(0xFF243126),Color(0xFF526653),Color(0xFF52734F),Color(0xFF9A7847),Color(0xFF172019),Color(0xFF222E25),Color(0xFF304235),Color(0xFFEAF1E8),Color(0xFFB6C7B5),Color(0xFF9DBA83))
        "Burgundy" -> Palette(Color(0xFFF7EFED),Color(0xFFEEDFDA),Color(0xFFE2CBC6),Color(0xFF352321),Color(0xFF70534E),Color(0xFF873F4C),Color(0xFFB18A62),Color(0xFF211719),Color(0xFF302124),Color(0xFF432D31),Color(0xFFF4E9E7),Color(0xFFD0B9B5),Color(0xFFD18B98))
        "Ocean" -> Palette(Color(0xFFEDF4F5),Color(0xFFDCEAEC),Color(0xFFC8DDE1),Color(0xFF1D3036),Color(0xFF4D6870),Color(0xFF286D7A),Color(0xFF4F8E91),Color(0xFF142126),Color(0xFF1D3036),Color(0xFF29414A),Color(0xFFEAF4F5),Color(0xFFB2C9CE),Color(0xFF79BBC5))
        "Graphite" -> Palette(Color(0xFFF0F1F2),Color(0xFFE3E5E7),Color(0xFFD3D7DA),Color(0xFF25292E),Color(0xFF5B626A),Color(0xFF596A7B),Color(0xFF8C795F),Color(0xFF17191C),Color(0xFF22262B),Color(0xFF2D333A),Color(0xFFF2F2EF),Color(0xFFBEC3C7),Color(0xFFC6A15B))
        "Sandstone" -> Palette(Color(0xFFF4EBDD),Color(0xFFE9DCC8),Color(0xFFDDCBB1),Color(0xFF332B22),Color(0xFF6D5D4B),Color(0xFF9A6B35),Color(0xFF6E7660),Color(0xFF211D18),Color(0xFF302920),Color(0xFF40372A),Color(0xFFF4EBDD),Color(0xFFC8B9A2),Color(0xFFD4A16A))
        "Lavender" -> Palette(Color(0xFFF2EFF7),Color(0xFFE5DFEF),Color(0xFFD8CFE6),Color(0xFF30283B),Color(0xFF665A76),Color(0xFF75618F),Color(0xFF9B7185),Color(0xFF1D1925),Color(0xFF292333),Color(0xFF393047),Color(0xFFF2EFF7),Color(0xFFC5BBD5),Color(0xFFB69BD5))
        "Copper" -> Palette(Color(0xFFF5EEE8),Color(0xFFE9DCD0),Color(0xFFDDC9B8),Color(0xFF332720),Color(0xFF70594B),Color(0xFF9A5838),Color(0xFFB17A48),Color(0xFF211A17),Color(0xFF30251F),Color(0xFF43332A),Color(0xFFF5EEE8),Color(0xFFCDB9A9),Color(0xFFD28B61))
        "Arctic" -> Palette(Color(0xFFF1F5F8),Color(0xFFE2EAF0),Color(0xFFD2DEE7),Color(0xFF202D38),Color(0xFF536675),Color(0xFF426F91),Color(0xFF5E8F9B),Color(0xFF151E26),Color(0xFF202D38),Color(0xFF2C3C49),Color(0xFFEAF2F8),Color(0xFFB8C8D5),Color(0xFF82B2D2))
        else -> Palette(Color(0xFFF5F0E6),Color(0xFFFFFCF5),Color(0xFFEAE3D2),Color(0xFF2C2621),Color(0xFF655B50),Color(0xFFC29B38),Color(0xFF5A6B57),Color(0xFF171C23),Color(0xFF202731),Color(0xFF2C3540),Color(0xFFE8E9E7),Color(0xFFBEC5CD),Color(0xFFB7C8DE))
    }

    fun colorScheme(name: String, dark: Boolean) : androidx.compose.material3.ColorScheme {
        val p = palette(name)
        return if (dark) darkColorScheme(
            primary=p.darkAccent, onPrimary=Color(0xFF17191C),
            primaryContainer=p.darkRaised, onPrimaryContainer=p.darkText,
            secondary=p.secondary, onSecondary=Color.White,
            secondaryContainer=p.darkRaised, onSecondaryContainer=p.darkText,
            tertiary=p.darkAccent, onTertiary=Color(0xFF17191C),
            background=p.darkBg, onBackground=p.darkText,
            surface=p.darkSurface, onSurface=p.darkText,
            surfaceVariant=p.darkRaised, onSurfaceVariant=p.darkMuted,
            outline=p.darkMuted, outlineVariant=p.darkRaised,
            error=Color(0xFFFFB4AB), onError=Color(0xFF690005),
            errorContainer=Color(0xFF7F2925), onErrorContainer=Color(0xFFFFDAD6)
        ) else lightColorScheme(
            primary=p.accent, onPrimary=Color.White,
            primaryContainer=p.lightRaised, onPrimaryContainer=p.lightText,
            secondary=p.secondary, onSecondary=Color.White,
            secondaryContainer=p.lightRaised, onSecondaryContainer=p.lightText,
            tertiary=p.secondary, onTertiary=Color.White,
            background=p.lightBg, onBackground=p.lightText,
            surface=p.lightSurface, onSurface=p.lightText,
            surfaceVariant=p.lightRaised, onSurfaceVariant=p.lightMuted,
            outline=p.lightMuted, outlineVariant=p.lightRaised,
            error=Color(0xFFB3261E), onError=Color.White,
            errorContainer=Color(0xFFF9DEDC), onErrorContainer=Color(0xFF410E0B)
        )
    }
}

private val PocketHostTypography = Typography(
    displayLarge = TextStyle(fontFamily=FontFamily.Serif, fontWeight=FontWeight.SemiBold, fontSize=36.sp, lineHeight=42.sp),
    displayMedium = TextStyle(fontFamily=FontFamily.Serif, fontWeight=FontWeight.SemiBold, fontSize=30.sp, lineHeight=36.sp),
    headlineLarge = TextStyle(fontFamily=FontFamily.Serif, fontWeight=FontWeight.SemiBold, fontSize=26.sp, lineHeight=32.sp),
    headlineMedium = TextStyle(fontFamily=FontFamily.Serif, fontWeight=FontWeight.SemiBold, fontSize=22.sp, lineHeight=28.sp),
    titleLarge = TextStyle(fontFamily=FontFamily.Serif, fontWeight=FontWeight.SemiBold, fontSize=19.sp, lineHeight=25.sp),
    titleMedium = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=16.sp, lineHeight=22.sp),
    titleSmall = TextStyle(fontWeight=FontWeight.Medium, fontSize=14.sp, lineHeight=20.sp),
    bodyLarge = TextStyle(fontSize=16.sp, lineHeight=24.sp),
    bodyMedium = TextStyle(fontSize=14.sp, lineHeight=21.sp),
    bodySmall = TextStyle(fontSize=12.sp, lineHeight=18.sp),
    labelLarge = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=14.sp, lineHeight=20.sp),
    labelMedium = TextStyle(fontWeight=FontWeight.Medium, fontSize=12.sp, lineHeight=16.sp),
    labelSmall = TextStyle(fontWeight=FontWeight.Medium, fontSize=11.sp, lineHeight=14.sp)
)

private val PocketHostShapes = Shapes(
    extraSmall=RoundedCornerShape(8.dp), small=RoundedCornerShape(12.dp),
    medium=RoundedCornerShape(16.dp), large=RoundedCornerShape(20.dp),
    extraLarge=RoundedCornerShape(24.dp)
)

@Composable
fun PocketHostTheme(
    darkTheme: Boolean = false,
    themeName: String = "Classic",
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme=PocketHostPalettes.colorScheme(themeName, darkTheme),
        typography=PocketHostTypography,
        shapes=PocketHostShapes,
        content=content
    )
}
