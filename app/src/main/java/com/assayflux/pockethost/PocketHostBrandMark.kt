package com.assayflux.pockethost

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Displays the launcher artwork generated from the canonical Pocket Host SVG.
 * Rendering is precomputed at build time so the large vector is never parsed on
 * the Android main thread.
 */
@Composable
internal fun PocketHostBrandMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.mipmap.ic_launcher_artwork),
        contentDescription = "Pocket Host logo",
        contentScale = ContentScale.Fit,
        modifier = modifier.clip(RoundedCornerShape(13.dp))
    )
}
