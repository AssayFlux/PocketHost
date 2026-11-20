package com.assayflux.pockethost

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caverock.androidsvg.SVG

/**
 * Renders the exact repository-owned Pocket Host identity asset in app chrome.
 * Gradle copies Assets/Pocket Host 7.svg into the app assets directory.
 */
@Composable
internal fun PocketHostBrandMark(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logo = remember(context) {
        runCatching { SVG.getFromAsset(context.assets, "pocket-host-logo.svg") }.getOrNull()
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFF0A1C4A)),
        contentAlignment = Alignment.Center
    ) {
        if (logo != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { composeCanvas ->
                    val canvas = composeCanvas.nativeCanvas
                    val saveCount = canvas.save()
                    canvas.scale(size.width / 2500f, size.height / 2500f)
                    logo.renderToCanvas(canvas)
                    canvas.restoreToCount(saveCount)
                }
            }
        } else {
            Text("PH", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
