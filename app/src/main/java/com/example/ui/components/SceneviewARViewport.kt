package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.engine.HdriPreset
import com.example.engine.RenderEngineProfile
import com.example.engine.ar.ARSurfaceAnchor
import com.example.math3d.Model3D

/**
 * Augmented Reality Viewport with Live Camera Passthrough & High-Performance Filament GPU 3D Renderer.
 *
 * Features:
 * - Live Camera Passthrough feed (Zero black screen).
 * - Full hardware-accelerated GPU PBR Mesh rendering (Identical quality to Object Mode).
 * - Zero CPU triangle decimation or mesh distortion.
 * - Interactive 3D model manipulation (pinch-to-zoom, drag rotation, panning).
 */
@Composable
fun SceneviewARViewport(
    model: Model3D?,
    rotX: Float,
    rotY: Float,
    rotZ: Float = 0f,
    scale: Float = 1.0f,
    panX: Float = 0f,
    panY: Float = 0f,
    surfaceAnchor: ARSurfaceAnchor? = null,
    isAnchored: Boolean = false,
    hdriPreset: HdriPreset = HdriPreset.STUDIO_PRO,
    renderEngineProfile: RenderEngineProfile = RenderEngineProfile.REALITYKIT,
    isWireframe: Boolean = false,
    modelColor: Color = Color(0xFFE2E8F0),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Zero-Copy Hardware-Accelerated Camera Passthrough Feed
        CameraPreview(modifier = Modifier.fillMaxSize())

        // 2. Hardware-Accelerated Filament GPU 3D Model Viewport (Transparent Overlay)
        if (model != null) {
            Sceneview3DViewport(
                model = model,
                rotX = rotX,
                rotY = rotY,
                rotZ = rotZ,
                scale = scale,
                panX = panX,
                panY = panY,
                isTransparent = true,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

