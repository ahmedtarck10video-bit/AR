package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.math3d.Model3D
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-Performance Hardware Accelerated 3D Viewport powered by Google Filament & Sceneview.
 * Renders full PBR materials, textures, environment lighting, and 60+ FPS hardware rasterization.
 * Operates purely on GPU without initializing camera previews or background sensor drains.
 */
@Composable
fun Sceneview3DViewport(
    model: Model3D?,
    rotX: Float,
    rotY: Float,
    rotZ: Float = 0f,
    scale: Float = 1.0f,
    panX: Float = 0f,
    panY: Float = 0f,
    surfaceAnchor: com.example.engine.ar.ARSurfaceAnchor? = null,
    isAnchored: Boolean = false,
    isAutoSpin: Boolean = false,
    autoSpinAngle: Float = 0f,
    isTransparent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var currentModelNode by remember { mutableStateOf<ModelNode?>(null) }
    var sceneViewRef by remember { mutableStateOf<SceneView?>(null) }
    var loadedModelPath by remember { mutableStateOf<String?>(null) }
    var isLoadingModel by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                currentModelNode?.let { node ->
                    sceneViewRef?.removeChildNode(node)
                    node.destroy()
                }
                currentModelNode = null
                sceneViewRef = null
                isLoadingModel = false
                loadedModelPath = null
            } catch (e: Exception) {
                // Safe cleanup
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            SceneView(ctx).apply {
                if (isTransparent) {
                    setZOrderMediaOverlay(true)
                    holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
                }
                cameraNode.position = Position(0f, 0f, 3.5f)
                sceneViewRef = this
            }
        },
        onRelease = { sceneView ->
            try {
                currentModelNode?.let { node ->
                    sceneView.removeChildNode(node)
                    node.destroy()
                }
                currentModelNode = null
                sceneViewRef = null
                isLoadingModel = false
                loadedModelPath = null
            } catch (e: Exception) {
                // Safe release
            }
        },
        update = { sceneView ->
            sceneViewRef = sceneView
            val targetModel = model
            val targetPath = targetModel?.localFilePath ?: targetModel?.fileUri?.toString()

            if (targetModel == null || targetPath == null) {
                if (currentModelNode != null) {
                    try {
                        currentModelNode?.let { oldNode ->
                            sceneView.removeChildNode(oldNode)
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

                // Dynamic camera distance framing based on metric bounds
                val maxDim = maxOf(targetModel.realWorldHeightMeters, targetModel.realWorldWidthMeters, targetModel.realWorldDepthMeters)
                val targetCameraDist = maxOf(1.8f, maxDim * 2.2f)
                sceneView.cameraNode.position = Position(0f, 0f, targetCameraDist)

                coroutineScope.launch {
                    try {
                        val filePath = targetModel.localFilePath
                        val file = if (filePath != null) File(filePath) else null
                        
                        val instance = if (file != null && file.exists()) {
                            sceneView.modelLoader.createModelInstance(file)
                        } else if (targetModel.fileUri != null) {
                            sceneView.modelLoader.loadModelInstance(targetModel.fileUri.toString())
                        } else {
                            null
                        }

                        if (instance != null) {
                            currentModelNode?.let { oldNode ->
                                try {
                                    sceneView.removeChildNode(oldNode)
                                    oldNode.destroy()
                                } catch (e: Exception) {
                                    // Safe cleanup
                                }
                            }
                            val newNode = ModelNode(
                                modelInstance = instance
                            ).apply {
                                val posX = if (isAnchored && surfaceAnchor != null) surfaceAnchor.position.x + (panX * 0.002f) else panX * 0.005f
                                val posY = if (isAnchored && surfaceAnchor != null) surfaceAnchor.position.y - (panY * 0.002f) else -panY * 0.005f
                                val posZ = if (isAnchored && surfaceAnchor != null) (surfaceAnchor.position.z - 3.5f).coerceIn(-5f, 2f) else 0f
                                this.position = Position(x = posX, y = posY, z = posZ)
                                this.scale = Scale(scale, scale, scale)
                                val finalRotY = ((rotY + (surfaceAnchor?.rotationY ?: 0f) + if (isAutoSpin) autoSpinAngle else 0f) * 180f / Math.PI.toFloat())
                                val finalRotX = (rotX * 180f / Math.PI.toFloat())
                                val finalRotZ = (rotZ * 180f / Math.PI.toFloat())
                                this.rotation = Rotation(x = finalRotX, y = finalRotY, z = finalRotZ)
                            }
                            sceneView.addChildNode(newNode)
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
                    val posX = if (isAnchored && surfaceAnchor != null) surfaceAnchor.position.x + (panX * 0.002f) else panX * 0.005f
                    val posY = if (isAnchored && surfaceAnchor != null) surfaceAnchor.position.y - (panY * 0.002f) else -panY * 0.005f
                    val posZ = if (isAnchored && surfaceAnchor != null) (surfaceAnchor.position.z - 3.5f).coerceIn(-5f, 2f) else 0f
                    node.position = Position(x = posX, y = posY, z = posZ)
                    node.scale = Scale(scale, scale, scale)
                    val finalRotY = ((rotY + (surfaceAnchor?.rotationY ?: 0f) + if (isAutoSpin) autoSpinAngle else 0f) * 180f / Math.PI.toFloat())
                    val finalRotX = (rotX * 180f / Math.PI.toFloat())
                    val finalRotZ = (rotZ * 180f / Math.PI.toFloat())
                    node.rotation = Rotation(x = finalRotX, y = finalRotY, z = finalRotZ)
                }
            }
        }
    )
}
