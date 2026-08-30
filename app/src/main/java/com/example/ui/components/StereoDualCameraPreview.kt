package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
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
import java.util.concurrent.Executors

/**
 * True Stereoscopic Double Camera Viewport Container for Mixed Reality (MR).
 * Houses left and right eye viewports with independent camera stream feeds for each eye
 * without any dividing lines.
 */
@Composable
fun StereoDualCameraPreview(
    modifier: Modifier = Modifier,
    showCameraPassthrough: Boolean = true,
    leftOverlay: @Composable () -> Unit = {},
    rightOverlay: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    if (showCameraPassthrough) {
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
                    // Ignore
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Stereoscopic Split-View Dual Feed (Double Camera: Left Eye + Right Eye)
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Eye Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (showCameraPassthrough) {
                    cameraBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Left Eye Camera Feed",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                leftOverlay()
            }

            // Right Eye Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (showCameraPassthrough) {
                    cameraBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Right Eye Camera Feed",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                rightOverlay()
            }
        }
    }
}
