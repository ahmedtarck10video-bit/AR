package com.example.engine.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DualCameraHardwareStatus(val label: String, val isDualPhysical: Boolean) {
    INITIALIZING("Detecting Physical Camera Hardware...", false),
    DUAL_PHYSICAL_SENSORS_ACTIVE("Dual Physical Cameras Active (Left + Right Stream)", true),
    LOGICAL_MULTI_CAMERA_SPLIT("Multi-Camera Sensor Active (Independent Optical Physical IDs)", true),
    SINGLE_PHYSICAL_SENSOR_ACTIVE("Single Physical Sensor Detected (Optical Stereo Pass-Through)", false),
    CAMERA_UNAVAILABLE("Camera Hardware Inactive / Permission Required", false)
}

data class DualCameraSessionInfo(
    val status: DualCameraHardwareStatus = DualCameraHardwareStatus.INITIALIZING,
    val leftCameraId: String? = null,
    val rightCameraId: String? = null,
    val logicalCameraId: String? = null,
    val isHardwareSynchronized: Boolean = false,
    val physicalCameraIds: List<String> = emptyList(),
    val summary: String = "Initializing Stereo Video Pipeline..."
)

/**
 * Dual Physical Camera Manager for Mixed Reality (MR) Stereo Pass-Through.
 * Discovers and streams from physical left and right camera sensors independently,
 * leveraging Android Camera2 Logical Multi-Camera Physical IDs (API 28+) or dual physical camera devices.
 */
class DualPhysicalCameraManager(private val context: Context) {

    companion object {
        private const val TAG = "DualPhysicalCamera"
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _sessionInfo = MutableStateFlow(DualCameraSessionInfo())
    val sessionInfo: StateFlow<DualCameraSessionInfo> = _sessionInfo.asStateFlow()

    private var leftCameraDevice: CameraDevice? = null
    private var rightCameraDevice: CameraDevice? = null
    private var leftCaptureSession: CameraCaptureSession? = null
    private var rightCaptureSession: CameraCaptureSession? = null

    init {
        detectCameraTopology()
    }

    /**
     * Inspects camera hardware to identify physical multi-camera sensors.
     */
    fun detectCameraTopology() {
        try {
            val cameraIds = cameraManager.cameraIdList
            if (cameraIds.isEmpty()) {
                _sessionInfo.value = DualCameraSessionInfo(
                    status = DualCameraHardwareStatus.CAMERA_UNAVAILABLE,
                    summary = "No camera hardware detected"
                )
                return
            }

            var foundLogicalMultiCamera: String? = null
            var physicalSubIds: Set<String> = emptySet()
            val rearCameras = mutableListOf<String>()

            for (id in cameraIds) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    rearCameras.add(id)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val physicalIds = chars.physicalCameraIds
                        if (physicalIds.isNotEmpty() && physicalIds.size >= 2) {
                            foundLogicalMultiCamera = id
                            physicalSubIds = physicalIds
                        }
                    }
                }
            }

            when {
                foundLogicalMultiCamera != null && physicalSubIds.size >= 2 -> {
                    val subIdList = physicalSubIds.toList()
                    _sessionInfo.value = DualCameraSessionInfo(
                        status = DualCameraHardwareStatus.LOGICAL_MULTI_CAMERA_SPLIT,
                        logicalCameraId = foundLogicalMultiCamera,
                        leftCameraId = subIdList[0],
                        rightCameraId = subIdList[1],
                        isHardwareSynchronized = true,
                        physicalCameraIds = subIdList,
                        summary = "Dual Physical Sensors Detected: ID [${subIdList[0]}] (Left) + ID [${subIdList[1]}] (Right)"
                    )
                    Log.i(TAG, "Multi-Camera detected: logical=$foundLogicalMultiCamera, physical=$physicalSubIds")
                }
                rearCameras.size >= 2 -> {
                    _sessionInfo.value = DualCameraSessionInfo(
                        status = DualCameraHardwareStatus.DUAL_PHYSICAL_SENSORS_ACTIVE,
                        leftCameraId = rearCameras[0],
                        rightCameraId = rearCameras[1],
                        isHardwareSynchronized = false,
                        physicalCameraIds = rearCameras,
                        summary = "Dual Physical Cameras: Primary [${rearCameras[0]}] + Auxiliary [${rearCameras[1]}]"
                    )
                    Log.i(TAG, "Dual independent rear cameras: ${rearCameras[0]} and ${rearCameras[1]}")
                }
                rearCameras.isNotEmpty() -> {
                    _sessionInfo.value = DualCameraSessionInfo(
                        status = DualCameraHardwareStatus.SINGLE_PHYSICAL_SENSOR_ACTIVE,
                        leftCameraId = rearCameras[0],
                        rightCameraId = rearCameras[0],
                        isHardwareSynchronized = true,
                        physicalCameraIds = listOf(rearCameras[0]),
                        summary = "Single Camera Sensor [${rearCameras[0]}]: Dual-Ocular Optical Pipeline Active"
                    )
                    Log.i(TAG, "Single camera fallback: ${rearCameras[0]}")
                }
                else -> {
                    val firstCam = cameraIds.first()
                    _sessionInfo.value = DualCameraSessionInfo(
                        status = DualCameraHardwareStatus.SINGLE_PHYSICAL_SENSOR_ACTIVE,
                        leftCameraId = firstCam,
                        rightCameraId = firstCam,
                        physicalCameraIds = listOf(firstCam),
                        summary = "Camera Sensor [$firstCam] Active"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting camera topology", e)
            _sessionInfo.value = DualCameraSessionInfo(
                status = DualCameraHardwareStatus.CAMERA_UNAVAILABLE,
                summary = "Camera detection error: ${e.message}"
            )
        }
    }

    /**
     * Starts dual streaming to provided left and right output surfaces.
     */
    @SuppressLint("MissingPermission")
    fun startDualStreams(leftSurface: Surface, rightSurface: Surface) {
        val info = _sessionInfo.value
        val leftId = info.leftCameraId ?: return
        val rightId = info.rightCameraId ?: leftId

        try {
            // Open Left Camera
            cameraManager.openCamera(leftId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    leftCameraDevice = camera
                    createCameraSession(camera, leftSurface) { session ->
                        leftCaptureSession = session
                    }
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    leftCameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    leftCameraDevice = null
                }
            }, mainHandler)

            // If right camera is distinct from left, open right camera device
            if (rightId != leftId) {
                cameraManager.openCamera(rightId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        rightCameraDevice = camera
                        createCameraSession(camera, rightSurface) { session ->
                            rightCaptureSession = session
                        }
                    }
                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        rightCameraDevice = null
                    }
                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        rightCameraDevice = null
                    }
                }, mainHandler)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening dual camera streams", e)
        }
    }

    private fun createCameraSession(camera: CameraDevice, surface: Surface, onReady: (CameraCaptureSession) -> Unit) {
        try {
            val surfaces = listOf(surface)
            @Suppress("DEPRECATION")
            camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    try {
                        val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        }
                        session.setRepeatingRequest(requestBuilder.build(), null, mainHandler)
                        onReady(session)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting preview capture request", e)
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Camera capture session configuration failed")
                }
            }, mainHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating capture session", e)
        }
    }

    fun stop() {
        try {
            leftCaptureSession?.close()
            leftCaptureSession = null
            rightCaptureSession?.close()
            rightCaptureSession = null

            leftCameraDevice?.close()
            leftCameraDevice = null
            rightCameraDevice?.close()
            rightCameraDevice = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping dual camera sessions", e)
        }
    }
}
