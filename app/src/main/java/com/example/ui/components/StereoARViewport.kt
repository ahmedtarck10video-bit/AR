package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.ar.ARSurfaceAnchor
import com.example.engine.ar.PlaneOrientation
import com.example.math3d.Model3D
import com.example.ui.theme.NeonCyan
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * True Hardware-Accelerated Stereoscopic Mixed Reality Viewport.
 *
 * Implements:
 * 1. Unified ARCore Camera Passthrough (zero CameraX / zero black background).
 * 2. Real ARCore Camera Pose & 6DoF Surface Anchor synchronization.
 * 3. Metric Inter-Pupillary Distance (IPD) optical eye separation: Left (-IPD/2) and Right (+IPD/2).
 * 4. Geometric Optical Vergence calculation matching focal convergence plane.
 * 5. ARCore Hardware Depth Occlusion & Environmental HDR Lighting on Filament PBR engine.
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var leftModelNode by remember { mutableStateOf<ModelNode?>(null) }
    var rightModelNode by remember { mutableStateOf<ModelNode?>(null) }
    var leftArViewRef by remember { mutableStateOf<ARSceneView?>(null) }
    var rightArViewRef by remember { mutableStateOf<ARSceneView?>(null) }
    var lastLoadedPath by remember { mutableStateOf<String?>(null) }

    val isArCoreInstalled = remember {
        try {
            val pInfo = try {
                context.packageManager.getPackageInfo("com.google.ar.core", 0)
            } catch (e: Exception) {
                null
            }
            if (pInfo != null) {
                val availability = ArCoreApk.getInstance().checkAvailability(context)
                availability == ArCoreApk.Availability.SUPPORTED_INSTALLED
            } else {
                false
            }
        } catch (e: Throwable) {
            false
        }
    }

    // Physical Stereo Calculations
    val halfIpd = (ipdMeters.coerceIn(0.040f, 0.090f)) * 0.5f // Half IPD baseline in meters
    val focalDistanceMeters = 1.5f // Default convergence plane distance
    val vergenceDeg = atan2(halfIpd, focalDistanceMeters) * 180f / Math.PI.toFloat()

    DisposableEffect(Unit) {
        onDispose {
            try {
                leftModelNode?.let { node ->
                    leftArViewRef?.removeChildNode(node)
                    node.destroy()
                }
                rightModelNode?.let { node ->
                    rightArViewRef?.removeChildNode(node)
                    node.destroy()
                }
                leftModelNode = null
                rightModelNode = null
                leftArViewRef = null
                rightArViewRef = null
            } catch (e: Exception) {
                android.util.Log.w("StereoARViewport", "Cleanup warning: ${e.localizedMessage}")
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // =================================================================
            // LEFT EYE VIEWPORT (Offset: -halfIpd, Vergence: +vergenceDeg)
            // =================================================================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        ARSceneView(ctx).apply {
                            planeRenderer.isVisible = !isAnchored
                            planeRenderer.isEnabled = true
                            cameraStream?.isDepthOcclusionEnabled = true
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
                            leftArViewRef = this
                        }
                    },
                    update = { arSceneView ->
                        leftArViewRef = arSceneView
                        val targetModel = model
                        val targetPath = targetModel?.localFilePath ?: targetModel?.fileUri?.toString()
                        val isModelPlaced = surfaceAnchor != null && isAnchored
                        val liveAnchorPose = surfaceAnchor?.arcoreAnchor?.pose

                        val finalPosition: Position
                        val finalRotation: Rotation

                        if (isModelPlaced && surfaceAnchor != null) {
                            val isVertical = surfaceAnchor.surfaceType == PlaneOrientation.VERTICAL
                            // Metric pan and optical stereo eye offset (-halfIpd)
                            val localDx = (panX * 0.001f) + (-halfIpd)
                            val localDy = if (isVertical) -panY * 0.001f else 0f
                            val localDz = if (!isVertical) panY * 0.001f else 0f

                            if (liveAnchorPose != null) {
                                val offsetPose = com.google.ar.core.Pose.makeTranslation(localDx, localDy, localDz)
                                val combinedYaw = (rotY + vergenceDeg)
                                val yawPose = com.google.ar.core.Pose.makeRotation(
                                    0f,
                                    sin(combinedYaw * Math.PI.toFloat() / 360f),
                                    0f,
                                    cos(combinedYaw * Math.PI.toFloat() / 360f)
                                )
                                val combinedPose = liveAnchorPose.compose(offsetPose).compose(yawPose)
                                finalPosition = Position(combinedPose.tx(), combinedPose.ty(), combinedPose.tz())
                                finalRotation = Rotation(
                                    x = rotX * 180f / Math.PI.toFloat(),
                                    y = combinedYaw,
                                    z = rotZ * 180f / Math.PI.toFloat()
                                )
                            } else {
                                finalPosition = Position(
                                    surfaceAnchor.position.x + localDx,
                                    surfaceAnchor.position.y + localDy,
                                    surfaceAnchor.position.z + localDz
                                )
                                finalRotation = Rotation(
                                    x = rotX * 180f / Math.PI.toFloat(),
                                    y = rotY + vergenceDeg,
                                    z = rotZ * 180f / Math.PI.toFloat()
                                )
                            }
                        } else {
                            finalPosition = Position(0f, -1000f, 0f)
                            finalRotation = Rotation(0f, 0f, 0f)
                        }

                        if (targetModel != null && targetPath != null && targetPath != lastLoadedPath) {
                            coroutineScope.launch {
                                try {
                                    val file = targetModel.localFilePath?.let { File(it) }
                                    val instance = if (file != null && file.exists()) {
                                        arSceneView.modelLoader.createModelInstance(file)
                                    } else if (targetModel.fileUri != null) {
                                        arSceneView.modelLoader.loadModelInstance(targetModel.fileUri.toString())
                                    } else null

                                    if (instance != null) {
                                        leftModelNode?.let {
                                            arSceneView.removeChildNode(it)
                                            it.destroy()
                                        }
                                        val node = ModelNode(instance).apply {
                                            this.position = finalPosition
                                            this.scale = Scale(scale, scale, scale)
                                            this.rotation = finalRotation
                                            this.isVisible = isModelPlaced
                                        }
                                        arSceneView.addChildNode(node)
                                        leftModelNode = node
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("StereoARViewport", "Error loading left eye model", e)
                                }
                            }
                        } else {
                            leftModelNode?.let { node ->
                                node.isVisible = isModelPlaced
                                if (isModelPlaced) {
                                    node.position = finalPosition
                                    node.scale = Scale(scale, scale, scale)
                                    node.rotation = finalRotation
                                }
                            }
                        }
                    }
                )

                // Left Eye Indicator
                Text(
                    text = "L EYE [MR 6DoF]",
                    color = NeonCyan.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(Color(0x80000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // =================================================================
            // RIGHT EYE VIEWPORT (Offset: +halfIpd, Vergence: -vergenceDeg)
            // =================================================================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        ARSceneView(ctx).apply {
                            planeRenderer.isVisible = !isAnchored
                            planeRenderer.isEnabled = true
                            cameraStream?.isDepthOcclusionEnabled = true
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
                            rightArViewRef = this
                        }
                    },
                    update = { arSceneView ->
                        rightArViewRef = arSceneView
                        val targetModel = model
                        val targetPath = targetModel?.localFilePath ?: targetModel?.fileUri?.toString()
                        val isModelPlaced = surfaceAnchor != null && isAnchored
                        val liveAnchorPose = surfaceAnchor?.arcoreAnchor?.pose

                        val finalPosition: Position
                        val finalRotation: Rotation

                        if (isModelPlaced && surfaceAnchor != null) {
                            val isVertical = surfaceAnchor.surfaceType == PlaneOrientation.VERTICAL
                            // Metric pan and optical stereo eye offset (+halfIpd)
                            val localDx = (panX * 0.001f) + (halfIpd)
                            val localDy = if (isVertical) -panY * 0.001f else 0f
                            val localDz = if (!isVertical) panY * 0.001f else 0f

                            if (liveAnchorPose != null) {
                                val offsetPose = com.google.ar.core.Pose.makeTranslation(localDx, localDy, localDz)
                                val combinedYaw = (rotY - vergenceDeg)
                                val yawPose = com.google.ar.core.Pose.makeRotation(
                                    0f,
                                    sin(combinedYaw * Math.PI.toFloat() / 360f),
                                    0f,
                                    cos(combinedYaw * Math.PI.toFloat() / 360f)
                                )
                                val combinedPose = liveAnchorPose.compose(offsetPose).compose(yawPose)
                                finalPosition = Position(combinedPose.tx(), combinedPose.ty(), combinedPose.tz())
                                finalRotation = Rotation(
                                    x = rotX * 180f / Math.PI.toFloat(),
                                    y = combinedYaw,
                                    z = rotZ * 180f / Math.PI.toFloat()
                                )
                            } else {
                                finalPosition = Position(
                                    surfaceAnchor.position.x + localDx,
                                    surfaceAnchor.position.y + localDy,
                                    surfaceAnchor.position.z + localDz
                                )
                                finalRotation = Rotation(
                                    x = rotX * 180f / Math.PI.toFloat(),
                                    y = rotY - vergenceDeg,
                                    z = rotZ * 180f / Math.PI.toFloat()
                                )
                            }
                        } else {
                            finalPosition = Position(0f, -1000f, 0f)
                            finalRotation = Rotation(0f, 0f, 0f)
                        }

                        if (targetModel != null && targetPath != null && targetPath != lastLoadedPath) {
                            lastLoadedPath = targetPath
                            coroutineScope.launch {
                                try {
                                    val file = targetModel.localFilePath?.let { File(it) }
                                    val instance = if (file != null && file.exists()) {
                                        arSceneView.modelLoader.createModelInstance(file)
                                    } else if (targetModel.fileUri != null) {
                                        arSceneView.modelLoader.loadModelInstance(targetModel.fileUri.toString())
                                    } else null

                                    if (instance != null) {
                                        rightModelNode?.let {
                                            arSceneView.removeChildNode(it)
                                            it.destroy()
                                        }
                                        val node = ModelNode(instance).apply {
                                            this.position = finalPosition
                                            this.scale = Scale(scale, scale, scale)
                                            this.rotation = finalRotation
                                            this.isVisible = isModelPlaced
                                        }
                                        arSceneView.addChildNode(node)
                                        rightModelNode = node
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("StereoARViewport", "Error loading right eye model", e)
                                }
                            }
                        } else {
                            rightModelNode?.let { node ->
                                node.isVisible = isModelPlaced
                                if (isModelPlaced) {
                                    node.position = finalPosition
                                    node.scale = Scale(scale, scale, scale)
                                    node.rotation = finalRotation
                                }
                            }
                        }
                    }
                )

                // Right Eye Indicator
                Text(
                    text = "R EYE [MR 6DoF]",
                    color = NeonCyan.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color(0x80000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
