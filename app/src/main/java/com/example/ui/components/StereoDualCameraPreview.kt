package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * True Stereoscopic Dual Viewport Container for Mixed Reality (MR).
 * Houses left and right eye overlay viewports with optical stereoscopic separation
 * and hardware-accelerated pass-through compositing (Zero black background).
 */
@Composable
fun StereoDualCameraPreview(
    modifier: Modifier = Modifier,
    showCameraPassthrough: Boolean = true,
    leftOverlay: @Composable () -> Unit = {},
    rightOverlay: @Composable () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Transparent)) {
        // Live Camera Passthrough Background Stream (No black background in MR)
        if (showCameraPassthrough) {
            CameraPreview(modifier = Modifier.fillMaxSize())
        }

        // Stereoscopic Split-View Dual Feed (Left Eye + Right Eye)
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Eye 3D/Spatial Overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
            ) {
                leftOverlay()
            }

            // Central Optical Stereo Interpupillary Divider Line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(Color(0x8000E5FF))
            )

            // Right Eye 3D/Spatial Overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
            ) {
                rightOverlay()
            }
        }
    }
}



