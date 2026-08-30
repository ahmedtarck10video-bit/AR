package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.HdriPreset
import com.example.engine.RenderEngineProfile
import com.example.engine.ar.ARStereoEyeState
import com.example.engine.ar.ARSurfaceAnchor
import com.example.math3d.Model3D

/**
 * Enterprise Stereoscopic Dual Viewport for Mixed Reality (MR Mode).
 * Renders synchronized Left Eye and Right Eye viewports over live camera passthrough.
 * Both eyes share identical, synchronized scale, rotation, and position transformations in real-time.
 */
@Composable
fun StereoARViewport(
    model: Model3D?,
    rotX: Float,
    rotY: Float,
    rotZ: Float = 0f,
    scale: Float = 1.0f,
    panX: Float = 0f,
    panY: Float = 0f,
    surfaceAnchor: ARSurfaceAnchor? = null,
    isAnchored: Boolean = false,
    ipdMeters: Float = 0.064f,
    stereoEyeState: ARStereoEyeState = ARStereoEyeState(),
    depthMap: com.example.engine.ar.ARDepthMapBuffer = com.example.engine.ar.ARDepthMapBuffer(),
    isDepthOcclusionEnabled: Boolean = true,
    closestDepthDistanceMeters: Float? = null,
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
        // 1. Live Camera Passthrough Background
        LiveCameraPreview(modifier = Modifier.fillMaxSize())

        // 2. Stereoscopic Side-by-Side Dual Display (Left Eye & Right Eye)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            // ================= LEFT EYE (50% WIDTH) =================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
            ) {
                if (model != null) {
                    Sceneview3DViewport(
                        model = model,
                        rotX = rotX,
                        rotY = rotY,
                        rotZ = rotZ,
                        scale = scale,
                        panX = panX,
                        panY = panY,
                        surfaceAnchor = surfaceAnchor,
                        isAnchored = isAnchored,
                        isTransparent = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // ================= RIGHT EYE (50% WIDTH) =================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
            ) {
                if (model != null) {
                    Sceneview3DViewport(
                        model = model,
                        rotX = rotX,
                        rotY = rotY,
                        rotZ = rotZ,
                        scale = scale,
                        panX = panX,
                        panY = panY,
                        surfaceAnchor = surfaceAnchor,
                        isAnchored = isAnchored,
                        isTransparent = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

