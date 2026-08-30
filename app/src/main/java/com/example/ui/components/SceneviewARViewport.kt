package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.engine.HdriPreset
import com.example.engine.RenderEngineProfile
import com.example.engine.Renderer3D
import com.example.engine.ar.ARSurfaceAnchor
import com.example.math3d.Model3D

/**
 * Augmented Reality Viewport with Live Camera Passthrough & High-Performance 3D Renderer.
 *
 * Features:
 * - Live Camera Passthrough feed (Zero black screen).
 * - Full interactive 3D model manipulation (pinch-to-zoom, drag rotation, panning).
 * - Clean view with top blue badges removed as requested.
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
    val renderer = remember { Renderer3D() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // 1. Live Camera Passthrough Background
        CameraPreview(modifier = Modifier.fillMaxSize())

        // 2. Hardware-Accelerated 3D Model Rendering over Camera Stream
        if (model != null && model.triangles.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                renderer.render(
                    drawScope = this,
                    model = model,
                    rotX = rotX,
                    rotY = rotY,
                    rotZ = rotZ,
                    scale = scale,
                    panX = panX,
                    panY = panY,
                    distance = 3.5f,
                    wireframe = isWireframe,
                    primaryColor = modelColor,
                    hdriPreset = hdriPreset,
                    engineProfile = renderEngineProfile
                )
            }
        }
    }
}
