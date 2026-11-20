package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assayflux.pockethost.ui.theme.PocketHostPalettes
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PocketHostThemeAccessibilityTest {
    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val values = listOf(luminance(foreground), luminance(background)).sortedDescending()
        return (values[0] + 0.05) / (values[1] + 0.05)
    }

    @Test
    fun everyPaletteProvidesWcagAaContrastForMaterialTextPairs() {
        PocketHostPalettes.names.forEach { name ->
            listOf(false, true).forEach { dark ->
                val scheme = PocketHostPalettes.colorScheme(name, dark)
                val textPairs = listOf(
                    "primary" to (scheme.onPrimary to scheme.primary),
                    "secondary" to (scheme.onSecondary to scheme.secondary),
                    "tertiary" to (scheme.onTertiary to scheme.tertiary),
                    "primary container" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                    "secondary container" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                    "background" to (scheme.onBackground to scheme.background),
                    "surface" to (scheme.onSurface to scheme.surface),
                    "error" to (scheme.onError to scheme.error),
                    "error container" to (scheme.onErrorContainer to scheme.errorContainer)
                )
                textPairs.forEach { (label, pair) ->
                    val ratio = contrast(pair.first, pair.second)
                    assertTrue("$name dark=$dark $label contrast was $ratio:1",
                        ratio >= 4.5)
                }
                listOf(scheme.background, scheme.surface, scheme.surfaceVariant).forEach { background ->
                    val ratio = contrast(scheme.onSurfaceVariant, background)
                    assertTrue("$name dark=$dark secondary text contrast was $ratio:1",
                        ratio >= 4.5)
                }
            }
        }
    }
}
