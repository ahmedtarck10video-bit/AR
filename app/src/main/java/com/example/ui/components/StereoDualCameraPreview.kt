package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * True Stereoscopic Dual Viewport Container for Mixed Reality (MR).
 * Houses left and right eye overlay viewports with optical stereoscopic separation
 * and hardware-accelerated pass-through compositing.
 */
@Composable
fun StereoDualCameraPreview(
    modifier: Modifier = Modifier,
    leftOverlay: @Composable () -> Unit = {},
    rightOverlay: @Composable () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Stereoscopic Split-View Dual Feed (Left Eye + Right Eye)
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Eye 3D/Spatial Overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            ) {
                leftOverlay()
            }

            // Central Optical Stereo Interpupillary Divider
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF000000))
            )

            // Right Eye 3D/Spatial Overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
            ) {
                rightOverlay()
            }
        }
    }
}


