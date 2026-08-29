package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.components.StereoARViewport
import com.example.ui.components.GlassCard
import com.example.ui.theme.NeonCyan
import com.example.viewmodel.MRUiState
import com.example.viewmodel.MixedRealityViewModel

@Composable
fun StereoVRScreen(
    uiState: MRUiState,
    viewModel: MixedRealityViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val currentModel = uiState.models.getOrNull(uiState.selectedModelIndex) ?: return

    val headPitch = uiState.sensorOrientation.pitch * 0.02f
    val headRoll = uiState.sensorOrientation.roll * 0.02f
    val headYaw = uiState.sensorOrientation.yaw * 0.02f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        if (hasCameraPermission) {
            // Live Unified Hardware-Accelerated Stereoscopic Mixed Reality AR Viewport
            StereoARViewport(
                model = currentModel,
                rotX = uiState.rotX + headPitch,
                rotY = uiState.rotY + headRoll,
                rotZ = headYaw,
                scale = uiState.scale * (uiState.surfaceAnchor?.scale ?: 1.0f),
                panX = uiState.panX,
                panY = uiState.panY,
                surfaceAnchor = uiState.surfaceAnchor,
                isAnchored = uiState.arAnchorPlaced,
                ipdMeters = uiState.ipdDistance,
                stereoEyeState = uiState.stereoEyeState,
                depthMap = uiState.depthMap,
                isDepthOcclusionEnabled = uiState.isDepthOcclusion,
                closestDepthDistanceMeters = uiState.depthFusionInfo.closestObjectDistanceMeters,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                val normX = offset.x / size.width.toFloat()
                                val normY = offset.y / size.height.toFloat()
                                viewModel.onSurfaceTapped(normX, normY)
                            },
                            onDoubleTap = {
                                viewModel.resetPosition()
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, rotation ->
                            if (pan.x != 0f || pan.y != 0f) {
                                viewModel.updatePan(pan.x, pan.y)
                            }
                            if (zoom != 1.0f) {
                                viewModel.updateScale(zoom)
                            }
                            if (rotation != 0f) {
                                viewModel.updateRotation(0f, rotation * 0.02f)
                            }
                        }
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = "Camera Access Required for MR Passthrough",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Enable MR Camera", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Top Status Pill with Real-Time Optics Telemetry
        GlassCard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0x600F172A),
            borderColor = Color(0x3300E5FF)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (uiState.arAnchorPlaced) "6DoF ANCHORED" else "SCANNING PHYSICAL SURFACE",
                    color = if (uiState.arAnchorPlaced) NeonCyan else Color.Yellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "•",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                Text(
                    text = "Convergence: ${String.format("%.2fm", uiState.stereoEyeState.convergenceDistanceMeters)}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Floating Liquid Glass Control Bar at bottom
        GlassCard(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
            shape = RoundedCornerShape(18.dp),
            backgroundColor = Color(0x600F172A),
            borderColor = Color(0x4DFFFFFF)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MR Stereo IPD: ${(uiState.ipdDistance * 1000).toInt()} mm | Vergence: ${String.format("%.1f°", uiState.stereoEyeState.vergenceDegrees)}",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    )
                    Row {
                        IconButton(onClick = { viewModel.toggleWireframe() }) {
                            Icon(Icons.Default.GridOn, contentDescription = "Wireframe", tint = if (uiState.isWireframe) NeonCyan else Color.White)
                        }
                        IconButton(onClick = { viewModel.resetView() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = NeonCyan)
                        }
                    }
                }
                Slider(
                    value = uiState.ipdDistance,
                    onValueChange = { viewModel.setIpdDistance(it) },
                    valueRange = 0.045f..0.085f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = Color(0x33FFFFFF)
                    )
                )
            }
        }
    }
}
