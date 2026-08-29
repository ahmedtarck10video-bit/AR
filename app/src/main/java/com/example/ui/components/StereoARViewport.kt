package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.HdriPreset
import com.example.engine.RenderEngineProfile
import com.example.engine.Renderer3D
import com.example.engine.ar.ARStereoEyeState
import com.example.engine.ar.ARSurfaceAnchor
import com.example.engine.ar.PlaneOrientation
import com.example.math3d.Model3D
import com.example.ui.theme.NeonCyan
import com.google.ar.core.Config
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.launch
import java.io.File

/**
 * Enterprise Single ARCore Session Stereoscopic Mixed Reality Viewport.
 *
 * Architectural Principles:
 * 1. Single ARCore Session & Single ARCore Camera Frame (Zero CameraX / Zero Session Conflicts).
 * 2. 6DoF World-Space Anchor Positioning: Model sits strictly at real physical anchor coordinates.
 * 3. Dynamic IPD Baseline & True Optical Vergence calculated from anchor distance.
 * 4. Hardware Depth Occlusion (Config.DepthMode.AUTOMATIC) & Environmental HDR Lighting on Filament PBR.
 * 5. Left/Right Eye MVP Matrix Application: Passes exact 4x4 View and Projection matrices to dual ocular pipelines.
 * 6. Instant Camera Passthrough with Zero Black Frame delay.
 * 7. Precision Stereoscopic Ocular Optics HUD with dynamic optical baseline division.
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
    closestDepthDistanceMeters: Float = 0f,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var modelNode by remember { mutableStateOf<ModelNode?>(null) }
    var arSceneViewRef by remember { mutableStateOf<ARSceneView?>(null) }
    var lastLoadedPath by remember { mutableStateOf<String?>(null) }
    val renderer3D = remember { Renderer3D() }

    DisposableEffect(Unit) {
        onDispose {
            try {
                modelNode?.let { node ->
                    arSceneViewRef?.removeChildNode(node)
                    node.destroy()
                }
                modelNode = null
                arSceneViewRef = null
            } catch (e: Exception) {
                android.util.Log.w("StereoARViewport", "Cleanup warning: ${e.localizedMessage}")
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Transparent)) {
        // =====================================================================
        // 1. MASTER ARCORE SESSION & CAMERA PASSTHROUGH (NO DUPLICATE MODEL)
        // =====================================================================
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ARSceneView(ctx).apply {
                    planeRenderer.isVisible = !isAnchored
                    planeRenderer.isEnabled = true
                    cameraStream?.isDepthOcclusionEnabled = isDepthOcclusionEnabled
                    sessionConfiguration = { session, config ->
                        config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                            Config.DepthMode.AUTOMATIC
                        } else {
                            Config.DepthMode.DISABLED
                        }
                        config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                        config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                        config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        config.focusMode = Config.FocusMode.AUTO
                    }
                    arSceneViewRef = this
                }
            },
            update = { arSceneView ->
                arSceneViewRef = arSceneView
                arSceneView.cameraStream?.isDepthOcclusionEnabled = isDepthOcclusionEnabled
                arSceneView.planeRenderer.isVisible = !isAnchored
            }
        )

        // =====================================================================
        // 2. DUAL-EYE STEREOSCOPIC MATRIX PIPELINE (LEFT EYE & RIGHT EYE)
        // =====================================================================
        // When real ARCore eye matrices are ready, render true dual-eye stereoscopic projections
        if (stereoEyeState.isStereoReady && model != null && isAnchored && surfaceAnchor != null) {
            val liveAnchorPose = surfaceAnchor.arcoreAnchor?.pose

            // Build 4x4 Model-to-World matrix directly from ARCore 6DoF Anchor Matrix multiplied with local transform
            val modelMatrix = remember(liveAnchorPose, surfaceAnchor.position, rotX, rotY, rotZ, scale, panX, panY) {
                val anchorM = FloatArray(16)
                if (liveAnchorPose != null) {
                    liveAnchorPose.toMatrix(anchorM, 0)
                } else {
                    android.opengl.Matrix.setIdentityM(anchorM, 0)
                    android.opengl.Matrix.translateM(anchorM, 0, surfaceAnchor.position.x, surfaceAnchor.position.y, surfaceAnchor.position.z)
                }

                val localM = FloatArray(16)
                android.opengl.Matrix.setIdentityM(localM, 0)
                android.opengl.Matrix.translateM(localM, 0, panX * 0.001f, -panY * 0.001f, 0f)
                android.opengl.Matrix.rotateM(localM, 0, rotY * 180f / Math.PI.toFloat(), 0f, 1f, 0f)
                android.opengl.Matrix.rotateM(localM, 0, rotX * 180f / Math.PI.toFloat(), 1f, 0f, 0f)
                android.opengl.Matrix.rotateM(localM, 0, rotZ * 180f / Math.PI.toFloat(), 0f, 0f, 1f)
                android.opengl.Matrix.scaleM(localM, 0, scale * 0.2f, scale * 0.2f, scale * 0.2f)

                val resultM = FloatArray(16)
                android.opengl.Matrix.multiplyMM(resultM, 0, anchorM, 0, localM, 0)
                resultM
            }

            val depthOcclusionDist = if (isDepthOcclusionEnabled && closestDepthDistanceMeters > 0.2f) {
                closestDepthDistanceMeters
            } else 0f
            val activeDepthMap = if (isDepthOcclusionEnabled) depthMap else null

            Row(modifier = Modifier.fillMaxSize()) {
                // Left Eye Viewport (applies leftViewMatrix & leftProjectionMatrix)
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        renderer3D.renderStereoEyeWithMatrix(
                            drawScope = this,
                            model = model,
                            modelMatrix = modelMatrix,
                            viewMatrix = stereoEyeState.leftViewMatrix,
                            projectionMatrix = stereoEyeState.leftProjectionMatrix,
                            depthMap = activeDepthMap,
                            cameraTimestampNs = stereoEyeState.timestampNs,
                            depthOcclusionThresholdMeters = depthOcclusionDist,
                            screenUOffset = 0.0f,
                            screenUScale = 0.5f,
                            wireframe = false,
                            primaryColor = Color(0xFF00E5FF),
                            depthOcclusionLevel = if (isDepthOcclusionEnabled) com.example.engine.DepthOcclusionLevel.MEDIUM else com.example.engine.DepthOcclusionLevel.OFF,
                            hdriPreset = HdriPreset.STUDIO_PRO,
                            engineProfile = RenderEngineProfile.REALITYKIT
                        )
                    }
                }

                // Right Eye Viewport (applies rightViewMatrix & rightProjectionMatrix)
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        renderer3D.renderStereoEyeWithMatrix(
                            drawScope = this,
                            model = model,
                            modelMatrix = modelMatrix,
                            viewMatrix = stereoEyeState.rightViewMatrix,
                            projectionMatrix = stereoEyeState.rightProjectionMatrix,
                            depthMap = activeDepthMap,
                            cameraTimestampNs = stereoEyeState.timestampNs,
                            depthOcclusionThresholdMeters = depthOcclusionDist,
                            screenUOffset = 0.5f,
                            screenUScale = 0.5f,
                            wireframe = false,
                            primaryColor = Color(0xFF00E5FF),
                            depthOcclusionLevel = if (isDepthOcclusionEnabled) com.example.engine.DepthOcclusionLevel.MEDIUM else com.example.engine.DepthOcclusionLevel.OFF,
                            hdriPreset = HdriPreset.STUDIO_PRO,
                            engineProfile = RenderEngineProfile.REALITYKIT
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 3. STEREOSCOPIC DUAL-EYE OPTICAL OVERLAY & CENTRAL SEPARATION
        // =====================================================================
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midX = w / 2f

            // Central Optical Divider Line (prevents binocular crosstalk)
            drawLine(
                color = Color(0x6600E5FF),
                start = Offset(midX, 0f),
                end = Offset(midX, h),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            )

            // Left Eye Optical Crosshair Reticle
            val leftCenterX = midX / 2f
            val centerY = h / 2f
            drawCircle(
                color = Color(0x3300E5FF),
                radius = 28.dp.toPx(),
                center = Offset(leftCenterX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
            )
            drawLine(
                color = Color(0x5500E5FF),
                start = Offset(leftCenterX - 16.dp.toPx(), centerY),
                end = Offset(leftCenterX + 16.dp.toPx(), centerY),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0x5500E5FF),
                start = Offset(leftCenterX, centerY - 16.dp.toPx()),
                end = Offset(leftCenterX, centerY + 16.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )

            // Right Eye Optical Crosshair Reticle
            val rightCenterX = midX + (midX / 2f)
            drawCircle(
                color = Color(0x3300E5FF),
                radius = 28.dp.toPx(),
                center = Offset(rightCenterX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
            )
            drawLine(
                color = Color(0x5500E5FF),
                start = Offset(rightCenterX - 16.dp.toPx(), centerY),
                end = Offset(rightCenterX + 16.dp.toPx(), centerY),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color(0x5500E5FF),
                start = Offset(rightCenterX, centerY - 16.dp.toPx()),
                end = Offset(rightCenterX, centerY + 16.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )
        }

        // =====================================================================
        // 4. HUD TELEMETRY LABELS WITH REAL-TIME EYE MATRICES STATUS
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Eye HUD
            Box(
                modifier = Modifier
                    .background(Color(0x800F172A), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x4000E5FF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column {
                    Text(
                        text = "L EYE [MR 6DoF MATRIX]",
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Offset: -${(ipdMeters * 500).toInt()}mm | Depth Occlusion: ${if (isDepthOcclusionEnabled) "ON" else "OFF"}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Right Eye HUD
            Box(
                modifier = Modifier
                    .background(Color(0x800F172A), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x4000E5FF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "R EYE [MR 6DoF MATRIX]",
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Offset: +${(ipdMeters * 500).toInt()}mm | Vergence: ${String.format("%.1f°", stereoEyeState.vergenceDegrees)}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

