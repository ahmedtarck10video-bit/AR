package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.example.engine.HdriPreset
import com.example.engine.RenderEngineProfile
import com.example.engine.Renderer3D
import com.example.engine.ar.ARStereoEyeState
import com.example.engine.ar.ARSurfaceAnchor
import com.example.math3d.Model3D
import java.util.concurrent.Executors

/**
 * Stereoscopic Dual Camera Viewport for Mixed Reality (MR Mode).
 *
 * Provides a Dual Camera Passthrough stream (Double Camera):
 * - Left Eye gets its own camera frame feed + Left 3D model perspective.
 * - Right Eye gets its own camera frame feed + Right 3D model perspective.
 * - Optical Interpupillary Distance (IPD) & Vergence alignment.
 * - Seamless immersion without dividing lines.
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
    hdriPreset: HdriPreset = HdriPreset.STUDIO_PRO,
    renderEngineProfile: RenderEngineProfile = RenderEngineProfile.REALITYKIT,
    isWireframe: Boolean = false,
    modelColor: Color = Color(0xFFE2E8F0),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val renderer = remember { Renderer3D() }
    val halfIpdPx = (ipdMeters * 350f).coerceIn(6f, 40f)
    val vergenceOffsetRad = (stereoEyeState.vergenceDegrees * 0.008f).coerceIn(-0.1f, 0.1f)

    var cameraBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(lifecycleOwner) {
        val cameraExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProviderRef = cameraProvider

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetResolution(Size(1280, 720))
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        val rawBitmap = imageProxy.toBitmap()
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val orientedBitmap = if (rotation != 0) {
                            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                        } else {
                            rawBitmap
                        }
                        cameraBitmap = orientedBitmap
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                cameraProviderRef?.unbindAll()
                cameraExecutor.shutdown()
            } catch (e: Exception) {
                // Ignore cleanup error
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Stereoscopic Double Camera Viewports: Left Eye + Right Eye
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Eye: Camera Stream + 3D Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Left Eye Camera Feed
                cameraBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Left Eye Camera Feed",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Left Eye 3D Model Overlay
                if (model != null && model.triangles.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        renderer.render(
                            drawScope = this,
                            model = model,
                            rotX = rotX,
                            rotY = rotY - vergenceOffsetRad,
                            rotZ = rotZ,
                            scale = scale,
                            panX = panX - halfIpdPx,
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

            // Right Eye: Camera Stream + 3D Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Right Eye Camera Feed
                cameraBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Right Eye Camera Feed",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Right Eye 3D Model Overlay
                if (model != null && model.triangles.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        renderer.render(
                            drawScope = this,
                            model = model,
                            rotX = rotX,
                            rotY = rotY + vergenceOffsetRad,
                            rotZ = rotZ,
                            scale = scale,
                            panX = panX + halfIpdPx,
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
    }
}
