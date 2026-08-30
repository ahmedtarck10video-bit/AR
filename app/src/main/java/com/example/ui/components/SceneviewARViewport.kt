package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
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
import com.example.engine.HdriPreset
import com.example.engine.RenderEngineProfile
import com.example.engine.ar.ARSurfaceAnchor
import com.example.math3d.Model3D
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.launch
import java.io.File

/**
 * Enterprise Augmented Reality Viewport with Native ARCore Camera Pipeline & Google Filament GPU Renderer.
 * Includes automatic ARCore capability verification and zero-crash fallback to 3D Spatial simulation
 * if running on emulators or devices without Google Play Services for AR installed.
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
    onFrameCallback: ((Session, com.google.ar.core.Frame) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isArCoreSupported = remember {
        try {
            val isPkgInstalled = try {
                context.packageManager.getPackageInfo("com.google.ar.core", 0) != null
            } catch (e: Exception) {
                false
            }
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            isPkgInstalled && availability == ArCoreApk.Availability.SUPPORTED_INSTALLED
        } catch (e: Throwable) {
            false
        }
    }

    if (!isArCoreSupported) {
        // Fallback for emulators or devices without Google Play Services for AR installed
        Box(modifier = modifier.fillMaxSize().background(Color(0xFF090D16))) {
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
                modifier = Modifier.fillMaxSize()
            )

            // AR Simulation Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 76.dp)
                    .background(Color(0xCC0F172A), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "AR Spatial Simulation Mode (ARCore APK not present on device/emulator)",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        return
    }

    var arSceneViewRef by remember { mutableStateOf<ARSceneView?>(null) }
    var currentAnchorNode by remember { mutableStateOf<AnchorNode?>(null) }
    var currentModelNode by remember { mutableStateOf<ModelNode?>(null) }
    var loadedModelPath by remember { mutableStateOf<String?>(null) }
    var isLoadingModel by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                currentModelNode?.let { modelNode ->
                    currentAnchorNode?.removeChildNode(modelNode)
                    arSceneViewRef?.removeChildNode(modelNode)
                    modelNode.destroy()
                }
                currentAnchorNode?.let { anchorNode ->
                    arSceneViewRef?.removeChildNode(anchorNode)
                    anchorNode.destroy()
                }
                currentModelNode = null
                currentAnchorNode = null
                arSceneViewRef = null
                isLoadingModel = false
                loadedModelPath = null
            } catch (e: Exception) {
                // Safe cleanup
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    ARSceneView(ctx).apply {
                        planeRenderer.isEnabled = true
                        planeRenderer.isVisible = true

                        configureSession { session, config ->
                            try {
                                config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                                    Config.DepthMode.AUTOMATIC
                                } else {
                                    Config.DepthMode.DISABLED
                                }
                                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                                config.focusMode = Config.FocusMode.AUTO
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        this.onSessionUpdated = { session, frame ->
                            onFrameCallback?.invoke(session, frame)
                        }

                        arSceneViewRef = this
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                    // If instantiation fails, create a safe fallback view
                    ARSceneView(ctx).apply {
                        arSceneViewRef = this
                    }
                }
            },
            onRelease = { arSceneView ->
                try {
                    currentModelNode?.let { modelNode ->
                        currentAnchorNode?.removeChildNode(modelNode)
                        arSceneView.removeChildNode(modelNode)
                        modelNode.destroy()
                    }
                    currentAnchorNode?.let { anchorNode ->
                        arSceneView.removeChildNode(anchorNode)
                        anchorNode.destroy()
                    }
                    currentModelNode = null
                    currentAnchorNode = null
                    arSceneViewRef = null
                    isLoadingModel = false
                    loadedModelPath = null
                } catch (e: Exception) {
                    // Safe cleanup
                }
            },
            update = { arSceneView ->
                arSceneViewRef = arSceneView
                val targetModel = model
                val targetPath = targetModel?.localFilePath ?: targetModel?.fileUri?.toString()

                // Update Anchor binding if anchor changed
                val activeAnchor = surfaceAnchor?.arcoreAnchor
                if (activeAnchor != null && currentAnchorNode?.anchor != activeAnchor) {
                    try {
                        currentAnchorNode?.let { oldAnchorNode ->
                            currentModelNode?.let { oldModelNode ->
                                oldAnchorNode.removeChildNode(oldModelNode)
                            }
                            arSceneView.removeChildNode(oldAnchorNode)
                            oldAnchorNode.destroy()
                        }
                        val newAnchorNode = AnchorNode(arSceneView.engine, activeAnchor)
                        arSceneView.addChildNode(newAnchorNode)
                        currentAnchorNode = newAnchorNode
                        currentModelNode?.let { mNode ->
                            newAnchorNode.addChildNode(mNode)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (targetModel == null || targetPath == null) {
                    if (currentModelNode != null) {
                        try {
                            currentModelNode?.let { oldNode ->
                                currentAnchorNode?.removeChildNode(oldNode)
                                arSceneView.removeChildNode(oldNode)
                                oldNode.destroy()
                            }
                        } catch (e: Exception) {
                            // Safe cleanup
                        }
                        currentModelNode = null
                    }
                    loadedModelPath = null
                    isLoadingModel = false
                } else if (targetPath != loadedModelPath && !isLoadingModel) {
                    isLoadingModel = true
                    loadedModelPath = targetPath

                    coroutineScope.launch {
                        try {
                            val filePath = targetModel.localFilePath
                            val file = if (filePath != null) File(filePath) else null

                            val instance = if (file != null && file.exists()) {
                                arSceneView.modelLoader.createModelInstance(file)
                            } else if (targetModel.fileUri != null) {
                                arSceneView.modelLoader.loadModelInstance(targetModel.fileUri.toString())
                            } else {
                                null
                            }

                            if (instance != null) {
                                currentModelNode?.let { oldNode ->
                                    try {
                                        currentAnchorNode?.removeChildNode(oldNode)
                                        arSceneView.removeChildNode(oldNode)
                                        oldNode.destroy()
                                    } catch (e: Exception) {
                                        // Safe cleanup
                                    }
                                }

                                val newNode = ModelNode(
                                    modelInstance = instance
                                ).apply {
                                    val finalScale = scale
                                    this.scale = Scale(finalScale, finalScale, finalScale)

                                    val finalRotY = ((rotY + (surfaceAnchor?.rotationY ?: 0f)) * 180f / Math.PI.toFloat())
                                    val finalRotX = (rotX * 180f / Math.PI.toFloat())
                                    val finalRotZ = (rotZ * 180f / Math.PI.toFloat())
                                    this.rotation = Rotation(x = finalRotX, y = finalRotY, z = finalRotZ)

                                    if (currentAnchorNode != null) {
                                        this.position = Position(x = panX * 0.001f, y = -panY * 0.001f, z = 0f)
                                    } else if (surfaceAnchor != null) {
                                        this.position = Position(
                                            x = surfaceAnchor.position.x + (panX * 0.001f),
                                            y = surfaceAnchor.position.y - (panY * 0.001f),
                                            z = surfaceAnchor.position.z
                                        )
                                    } else {
                                        this.position = Position(x = panX * 0.001f, y = -panY * 0.001f, z = -1.5f)
                                    }
                                }

                                val parentAnchor = currentAnchorNode
                                if (parentAnchor != null) {
                                    parentAnchor.addChildNode(newNode)
                                } else {
                                    arSceneView.addChildNode(newNode)
                                }
                                currentModelNode = newNode
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isLoadingModel = false
                        }
                    }
                } else {
                    currentModelNode?.let { node ->
                        val finalScale = scale
                        node.scale = Scale(finalScale, finalScale, finalScale)

                        val finalRotY = ((rotY + (surfaceAnchor?.rotationY ?: 0f)) * 180f / Math.PI.toFloat())
                        val finalRotX = (rotX * 180f / Math.PI.toFloat())
                        val finalRotZ = (rotZ * 180f / Math.PI.toFloat())
                        node.rotation = Rotation(x = finalRotX, y = finalRotY, z = finalRotZ)

                        if (currentAnchorNode != null) {
                            node.position = Position(x = panX * 0.001f, y = -panY * 0.001f, z = 0f)
                        } else if (surfaceAnchor != null) {
                            node.position = Position(
                                x = surfaceAnchor.position.x + (panX * 0.001f),
                                y = surfaceAnchor.position.y - (panY * 0.001f),
                                z = surfaceAnchor.position.z
                            )
                        } else {
                            node.position = Position(x = panX * 0.001f, y = -panY * 0.001f, z = -1.5f)
                        }
                    }
                }
            }
        )
    }
}
