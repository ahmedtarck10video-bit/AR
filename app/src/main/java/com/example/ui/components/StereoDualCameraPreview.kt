package com.example.ui.components

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.camera.DualPhysicalCameraManager

/**
 * Enterprise Stereoscopic Dual Viewport Container for Mixed Reality (MR).
 * Houses physical left and right eye hardware video streams with zero-copy camera2 pass-through,
 * true multi-camera sensor routing, and stereoscopic optical overlay compositing.
 */
@Composable
fun StereoDualCameraPreview(
    modifier: Modifier = Modifier,
    showCameraPassthrough: Boolean = true,
    leftOverlay: @Composable () -> Unit = {},
    rightOverlay: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val dualCameraManager = remember { DualPhysicalCameraManager(context) }
    var leftSurface by remember { mutableStateOf<Surface?>(null) }
    var rightSurface by remember { mutableStateOf<Surface?>(null) }

    LaunchedEffect(leftSurface, rightSurface, showCameraPassthrough) {
        val lSurf = leftSurface
        val rSurf = rightSurface
        if (showCameraPassthrough && lSurf != null && rSurf != null) {
            dualCameraManager.startDualStreams(lSurf, rSurf)
        } else {
            dualCameraManager.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            dualCameraManager.stop()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Split Left Eye / Right Eye Hardware Surfaces & Overlays
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Eye Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (showCameraPassthrough) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                        leftSurface = Surface(surface)
                                    }
                                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                        leftSurface?.release()
                                        leftSurface = null
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
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
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                        rightSurface = Surface(surface)
                                    }
                                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                        rightSurface?.release()
                                        rightSurface = null
                                        return true
                                    }
                                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                rightOverlay()
            }
        }
    }
}
