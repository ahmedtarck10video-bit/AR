package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Stereoscopic Dual Viewport Container for Mixed Reality (MR).
 * Houses left and right eye overlay viewports with optical stereoscopic separation
 * and zero-copy hardware-accelerated pass-through compositing.
 */
@Composable
fun StereoDualCameraPreview(
    modifier: Modifier = Modifier,
    showCameraPassthrough: Boolean = true,
    leftOverlay: @Composable () -> Unit = {},
    rightOverlay: @Composable () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Zero-copy Hardware-Accelerated Camera Passthrough Background Stream
        if (showCameraPassthrough) {
            CameraPreview(modifier = Modifier.fillMaxSize())
        }

        // Stereoscopic Split-View Dual Feed (Left Eye + Right Eye)
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Eye Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                leftOverlay()
            }

            // Right Eye Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                rightOverlay()
            }
        }
    }
}
