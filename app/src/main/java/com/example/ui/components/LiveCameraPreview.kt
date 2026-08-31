package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/**
 * Universal Hardware-Accelerated Live Camera Feed for AR & MR Passthrough using Native Camera2.
 * Eliminates external CameraX dependencies for direct hardware-level performance and low latency.
 */
@Composable
fun LiveCameraPreview(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        // Dynamic optical spatial background
        SpatialCameraSimulationBackground(modifier = Modifier.fillMaxSize())

        if (hasPermission) {
            AndroidView(
                factory = { ctx ->
                    NativeCamera2TextureView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Lightweight Camera2 TextureView for direct camera streaming without CameraX.
 */
@SuppressLint("MissingPermission")
private class NativeCamera2TextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startCamera(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopCamera()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    private fun startCamera(surfaceTexture: SurfaceTexture) {
        val manager = cameraManager ?: return
        try {
            val cameraIds = manager.cameraIdList
            val selectedCameraId = cameraIds.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraIds.firstOrNull() ?: return

            manager.openCamera(selectedCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    try {
                        val surface = Surface(surfaceTexture)
                        val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        }

                        camera.createCaptureSession(
                            listOf(surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    if (cameraDevice == null) return
                                    captureSession = session
                                    try {
                                        session.setRepeatingRequest(requestBuilder.build(), null, mainHandler)
                                    } catch (e: Exception) {
                                        Log.e("Camera2Preview", "Failed to start repeating request", e)
                                    }
                                }

                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                    Log.e("Camera2Preview", "Failed to configure camera capture session")
                                }
                            },
                            mainHandler
                        )
                    } catch (e: Exception) {
                        Log.e("Camera2Preview", "Failed to create capture session", e)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }, mainHandler)
        } catch (e: Exception) {
            Log.e("Camera2Preview", "Error opening camera", e)
        }
    }

    private fun stopCamera() {
        try {
            captureSession?.stopRepeating()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.e("Camera2Preview", "Error closing camera", e)
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
