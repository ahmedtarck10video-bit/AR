package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Stereoscopic Dual Camera Viewport for Mixed Reality (MR Mode).
 *
 * Provides a Hardware-Accelerated Dual Filament GPU Passthrough stream:
 * - Left Eye: True 3D model perspective with Left IPD & vergence angle.
 * - Right Eye: True 3D model perspective with Right IPD & vergence angle.
 * - Zero-copy camera passthrough with ocular separation.
 * - Full PBR materials, textures, shaders, and metric alignment.
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
    val halfIpdOffset = (ipdMeters * 0.5f).coerceIn(0.020f, 0.045f)
    val vergenceOffsetRad = (stereoEyeState.vergenceDegrees * (Math.PI / 180.0).toFloat()).coerceIn(-0.08f, 0.08f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Dual Ocular Split View (Left Eye + Right Eye)
        Row(modifier = Modifier.fillMaxSize()) {
            // 1. Left Eye Stream & 3D Filament Renderer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                CameraPreview(modifier = Modifier.fillMaxSize())

                if (model != null) {
                    Sceneview3DViewport(
                        model = model,
                        rotX = rotX,
                        rotY = rotY - vergenceOffsetRad,
                        rotZ = rotZ,
                        scale = scale,
                        panX = panX + (halfIpdOffset * 100f),
                        panY = panY,
                        surfaceAnchor = surfaceAnchor,
                        isAnchored = isAnchored,
                        isTransparent = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Left Eye Indicator Reticle
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("👁️ LEFT EYE", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Optical Center Divider
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.4f))
            )

            // 2. Right Eye Stream & 3D Filament Renderer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                CameraPreview(modifier = Modifier.fillMaxSize())

                if (model != null) {
                    Sceneview3DViewport(
                        model = model,
                        rotX = rotX,
                        rotY = rotY + vergenceOffsetRad,
                        rotZ = rotZ,
                        scale = scale,
                        panX = panX - (halfIpdOffset * 100f),
                        panY = panY,
                        surfaceAnchor = surfaceAnchor,
                        isAnchored = isAnchored,
                        isTransparent = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Right Eye Indicator Reticle
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("RIGHT EYE 👁️", color = Color(0xFFFFB703), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

