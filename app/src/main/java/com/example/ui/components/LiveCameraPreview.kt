package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/**
 * Universal Hardware-Accelerated Live Camera Feed for AR & MR Passthrough.
 * - Dynamically binds physical back/front camera when available.
 * - Provides an active spatial optical passthrough background for emulators & streaming environments
 *   so the viewport is never black.
 */
@Composable
fun LiveCameraPreview(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isCameraActive by remember { mutableStateOf(false) }

    val hasPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        // Dynamic animated optical spatial background visible in emulator & physical devices
        SpatialCameraSimulationBackground(modifier = Modifier.fillMaxSize())

        if (hasPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val cameraSelector = when {
                                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                                    else -> null
                                }

                                if (cameraSelector != null) {
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(surfaceProvider)
                                    }
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                                    isCameraActive = true
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * High-performance static spatial optical matrix & reticle passthrough background for streaming emulators.
 */
@Composable
private fun SpatialCameraSimulationBackground(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // High-contrast futuristic deep spatial dark canvas
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1E293B),
                    Color(0xFF0F172A),
                    Color(0xFF020617)
                ),
                center = Offset(cx, cy),
                radius = maxOf(w, h) * 0.75f
            )
        )

        // Matrix Grid Lines
        val step = 48.dp.toPx()
        val gridColor = Color(0xFF0EA5E9).copy(alpha = 0.15f)

        var x = 0f
        while (x <= w) {
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f
            )
            x += step
        }

        var y = 0f
        while (y <= h) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
            y += step
        }

        // Concentric Optical Radar & Reticle Rings
        val maxRadius = minOf(w, h) * 0.42f
        val ringColor = Color(0xFF38BDF8).copy(alpha = 0.25f)
        val accentRingColor = Color(0xFF06B6D4).copy(alpha = 0.35f)

        drawCircle(
            color = ringColor,
            radius = maxRadius * 0.35f,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        )
        drawCircle(
            color = ringColor,
            radius = maxRadius * 0.70f,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            )
        )
        drawCircle(
            color = accentRingColor,
            radius = maxRadius,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
            )
        )

        // Center Optical Crosshairs
        val crosshairLength = 24.dp.toPx()
        val crosshairColor = Color(0xFF38BDF8).copy(alpha = 0.5f)
        drawLine(
            color = crosshairColor,
            start = Offset(cx - crosshairLength, cy),
            end = Offset(cx + crosshairLength, cy),
            strokeWidth = 1.5f
        )
        drawLine(
            color = crosshairColor,
            start = Offset(cx, cy - crosshairLength),
            end = Offset(cx, cy + crosshairLength),
            strokeWidth = 1.5f
        )
    }
}
