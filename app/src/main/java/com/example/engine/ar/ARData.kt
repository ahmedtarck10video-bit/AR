package com.example.engine.ar

import com.example.math3d.Vec3

enum class PlaneOrientation(val label: String) {
    HORIZONTAL_UPWARD("Floor / Table"),
    HORIZONTAL_DOWNWARD("Ceiling"),
    VERTICAL("Wall")
}

enum class ARPlaneFilter(val label: String) {
    ALL("All Surfaces"),
    HORIZONTAL_ONLY("Floors & Tables"),
    VERTICAL_ONLY("Walls")
}

enum class ARPlacementMode(val label: String) {
    TAP_TO_PLACE("Tap Surface"),
    SURFACE_LOCKED("Surface Lock"),
    FREE_FLOAT("Free Float")
}

enum class ARHitType(val label: String) {
    PLANE_POLYGON("Physical Plane"),
    DEPTH_POINT("Depth API Surface"),
    FEATURE_POINT("Feature Point Normal"),
    INSTANT_PLACEMENT("Instant Placement"),
    AUGMENTED_IMAGE("Image Target Anchor"),
    AUGMENTED_FACE("Face 3D Mesh Anchor"),
    CLOUD_ANCHOR("Cloud Anchor ☁️"),
    GEOSPATIAL_ANCHOR("Geospatial GPS 🌍"),
    TERRAIN_ROOFTOP("Terrain / Rooftop 🏙️"),
    STREETSCAPE_MESH("Streetscape Geometry"),
    GEOMETRIC_FALLBACK("Sensor Approximation")
}

enum class ARTrackingStateQuality(val label: String, val colorHex: Long) {
    EXCELLENT("Excellent Tracking (6DoF)", 0xFF4CAF50),
    GOOD("Good Surface Tracking", 0xFF8BC34A),
    LOW_LIGHT("Warning: Low Light Environment", 0xFFFF9800),
    EXCESSIVE_MOTION("Warning: Excessive Device Motion", 0xFFFF5722),
    INSUFFICIENT_FEATURES("Searching for Physical Features...", 0xFFFFC107),
    INITIALIZING("Initializing ARCore...", 0xFF2196F3),
    PAUSED_OR_LOST("Tracking Lost - Relocalizing...", 0xFFF44336)
}

enum class SceneSemanticType(val label: String, val colorHex: Long) {
    UNLABELED("Background", 0xFF9E9E9E),
    SKY("Sky", 0xFF03A9F4),
    BUILDING("Building Structure", 0xFF9C27B0),
    TREE("Vegetation / Tree", 0xFF4CAF50),
    ROAD("Paved Road", 0xFF607D8B),
    SIDEWALK("Pedestrian Sidewalk", 0xFF009688),
    TERRAIN("Natural Ground / Soil", 0xFF795548),
    STRUCTURE("Architectural Structure", 0xFF3F51B5),
    OBJECT("Physical Object", 0xFFFF9800),
    VEHICLE("Moving Vehicle", 0xFFE91E63),
    PERSON("Human Person", 0xFFFF5722),
    WATER("Water Surface", 0xFF00BCD4)
}

data class ARCoreCapabilities(
    val isArCoreInstalled: Boolean = false,
    val isDepthSupported: Boolean = false,
    val isRawDepthSupported: Boolean = false,
    val isGeospatialSupported: Boolean = false,
    val isSemanticSupported: Boolean = false,
    val isCloudAnchorSupported: Boolean = false,
    val isAugmentedFacesSupported: Boolean = false,
    val isAugmentedImagesSupported: Boolean = false,
    val isInstantPlacementSupported: Boolean = false,
    val isStreetscapeSupported: Boolean = false,
    val summary: String = "Checking Hardware Capabilities..."
)

enum class ARHitResultCategory(val label: String) {
    REAL_ARCORE_HIT("Verified ARCore Surface (6DoF)"),
    ESTIMATED_FALLBACK("Geometric Fallback (Sensor Estimated)"),
    NO_VALID_HIT("No Surface Detected")
}

data class GeospatialAccuracyThresholds(
    val maxHorizontalAccuracyMeters: Float = 5.0f,
    val maxVerticalAccuracyMeters: Float = 10.0f,
    val maxHeadingAccuracyDegrees: Float = 15.0f
)

sealed class GeospatialValidationResult {
    data class Valid(val latitude: Double, val longitude: Double, val altitude: Double, val horizontalAccuracyMeters: Float, val headingAccuracyDegrees: Float) : GeospatialValidationResult()
    data class LowHorizontalAccuracy(val currentAccuracyMeters: Float, val requiredAccuracyMeters: Float) : GeospatialValidationResult()
    data class LowHeadingAccuracy(val currentHeadingAccuracyDegrees: Float, val requiredHeadingAccuracyDegrees: Float) : GeospatialValidationResult()
    data class LowVerticalAccuracy(val currentVerticalAccuracyMeters: Float, val requiredVerticalAccuracyMeters: Float) : GeospatialValidationResult()
    object VPSNotAvailable : GeospatialValidationResult()
    object EarthNotTracking : GeospatialValidationResult()
    object ServiceUnavailable : GeospatialValidationResult()
    object PermissionDenied : GeospatialValidationResult()
}

data class ARGeospatialInfo(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitudeMeters: Double = 0.0,
    val headingDegrees: Double = 0.0,
    val horizontalAccuracyMeters: Float = 999.0f,
    val verticalAccuracyMeters: Float = 999.0f,
    val headingAccuracyDegrees: Float = 999.0f,
    val isVPSAvailable: Boolean = false,
    val isPositionAccurate: Boolean = false,
    val vpsStatus: String = "Acquiring VPS...",
    val lastValidationResult: GeospatialValidationResult = GeospatialValidationResult.EarthNotTracking
)

data class ARStreetscapeMesh(
    val id: String,
    val type: String, // BUILDING or TERRAIN
    val center: Vec3,
    val verticesCount: Int,
    val trianglesCount: Int,
    val meshVertices: List<Vec3> = emptyList(),
    val isOcclusionActive: Boolean = true
)

data class ARFaceMeshTracking(
    val isTracking: Boolean = false,
    val faceCenterPose: Vec3 = Vec3(0f, 0f, 1f),
    val noseTipPose: Vec3 = Vec3(0f, 0f, 0.95f),
    val foreheadLeftPose: Vec3 = Vec3(-0.04f, 0.06f, 0.98f),
    val foreheadRightPose: Vec3 = Vec3(0.04f, 0.06f, 0.98f),
    val landmarksCount: Int = 468,
    val landmarkMeshPoints: List<Vec3> = emptyList(),
    val leftEyeOpenRatio: Float = 1.0f,
    val rightEyeOpenRatio: Float = 1.0f
)

data class ARDepthFusionInfo(
    val isDepthActive: Boolean = false,
    val rawDepthAvailable: Boolean = false,
    val averageDepthMeters: Float = 1.8f,
    val closestObjectDistanceMeters: Float = 0.5f,
    val occlusionRatioPercentage: Float = 0.0f,
    val isGeospatialDepthFused: Boolean = false
)

data class PersistentARAnchorData(
    val id: String,
    val modelName: String,
    val posX: Float,
    val posY: Float,
    val posZ: Float,
    val rotY: Float,
    val scale: Float,
    val cloudAnchorId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val hitType: ARHitType = ARHitType.PLANE_POLYGON,
    val timestamp: Long = System.currentTimeMillis()
)

data class ARTrackedImage(
    val id: String,
    val name: String,
    val center: Vec3,
    val extentX: Float,
    val extentZ: Float,
    val isTracking: Boolean,
    val trackingMethod: String = "FULL_TRACKING",
    val anchor: com.google.ar.core.Anchor? = null
)

data class RecordedSessionItem(
    val id: String,
    val fileName: String,
    val filePath: String,
    val fileSizeFormatted: String,
    val durationSeconds: Int,
    val timestamp: Long
)

data class ARTrackedPlane(
    val id: String,
    val center: Vec3,
    val normal: Vec3,
    val extentX: Float, // width in meters
    val extentZ: Float, // depth in meters
    val polygon: List<Vec3>, // 3D boundary polygon points
    val orientation: PlaneOrientation,
    val confidence: Float = 0.95f,
    val isSelected: Boolean = false
) {
    val areaM2: Float get() = extentX * extentZ
}

data class ARSurfaceAnchor(
    val id: String = "anchor_primary",
    val planeId: String? = null,
    val position: Vec3 = Vec3(0f, -0.6f, 2.5f),
    val normal: Vec3 = Vec3(0f, 1f, 0f),
    val rotationY: Float = 0f,
    val scale: Float = 1.0f,
    val isGrounded: Boolean = true,
    val surfaceType: PlaneOrientation = PlaneOrientation.HORIZONTAL_UPWARD,
    val arcoreAnchor: com.google.ar.core.Anchor? = null,
    val isReal6DOFTracking: Boolean = (arcoreAnchor != null),
    val hitType: ARHitType = if (arcoreAnchor != null) ARHitType.PLANE_POLYGON else ARHitType.GEOMETRIC_FALLBACK,
    val hitResultCategory: ARHitResultCategory = if (arcoreAnchor != null) ARHitResultCategory.REAL_ARCORE_HIT else ARHitResultCategory.ESTIMATED_FALLBACK
)

data class ARDepthMapBuffer(
    val width: Int = 0,
    val height: Int = 0,
    val depthMillimeters: ShortArray = ShortArray(0),
    val timestampNs: Long = 0L,
    val isValid: Boolean = false
) {
    /**
     * Samples the real-world physical depth in meters at normalized UV coordinates (0.0 to 1.0).
     * Returns Float.MAX_VALUE if sample is invalid or out of bounds.
     */
    fun getDepthMetersAt(u: Float, v: Float): Float {
        if (!isValid || width <= 0 || height <= 0 || depthMillimeters.isEmpty()) return Float.MAX_VALUE
        val x = (u.coerceIn(0f, 1f) * (width - 1)).toInt()
        val y = (v.coerceIn(0f, 1f) * (height - 1)).toInt()
        val index = y * width + x
        if (index in depthMillimeters.indices) {
            val mm = depthMillimeters[index].toInt() and 0xFFFF
            if (mm in 100..20000) { // 10cm to 20m valid range
                return mm / 1000.0f
            }
        }
        return Float.MAX_VALUE
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ARDepthMapBuffer
        return width == other.width && height == other.height && timestampNs == other.timestampNs && isValid == other.isValid
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + timestampNs.hashCode()
        result = 31 * result + isValid.hashCode()
        return result
    }
}

data class ARFrameSnapshot(
    val timestampNs: Long = 0L,
    val trackingQuality: ARTrackingStateQuality = ARTrackingStateQuality.INITIALIZING,
    val cameraPoseMatrix: FloatArray = FloatArray(16),
    val leftViewMatrix: FloatArray = FloatArray(16),
    val rightViewMatrix: FloatArray = FloatArray(16),
    val leftProjectionMatrix: FloatArray = FloatArray(16),
    val rightProjectionMatrix: FloatArray = FloatArray(16),
    val leftEyePose: Vec3 = Vec3(0f, 0f, 0f),
    val rightEyePose: Vec3 = Vec3(0f, 0f, 0f),
    val ipdMeters: Float = 0.064f,
    val vergenceDegrees: Float = 0f,
    val depthMap: ARDepthMapBuffer = ARDepthMapBuffer(),
    val isTracking: Boolean = false
)

data class ARStereoEyeState(
    val isStereoReady: Boolean = false,
    val focalLengthX: Float = 0f,
    val focalLengthY: Float = 0f,
    val principalPointX: Float = 0f,
    val principalPointY: Float = 0f,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val ipdMeters: Float = 0.064f,
    val eyeSeparationMeters: Float = 0.064f, // Full physical inter-pupillary baseline distance
    val convergenceDistanceMeters: Float = 1.5f,
    val vergenceDegrees: Float = 0f,
    val cameraPoseTx: Float = 0f,
    val cameraPoseTy: Float = 0f,
    val cameraPoseTz: Float = 0f,
    val leftEyePoseTx: Float = 0f,
    val leftEyePoseTy: Float = 0f,
    val leftEyePoseTz: Float = 0f,
    val rightEyePoseTx: Float = 0f,
    val rightEyePoseTy: Float = 0f,
    val rightEyePoseTz: Float = 0f,
    val leftViewMatrix: FloatArray = FloatArray(16),
    val rightViewMatrix: FloatArray = FloatArray(16),
    val leftProjectionMatrix: FloatArray = FloatArray(16),
    val rightProjectionMatrix: FloatArray = FloatArray(16)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ARStereoEyeState
        return isStereoReady == other.isStereoReady &&
                focalLengthX == other.focalLengthX &&
                focalLengthY == other.focalLengthY &&
                ipdMeters == other.ipdMeters &&
                eyeSeparationMeters == other.eyeSeparationMeters &&
                convergenceDistanceMeters == other.convergenceDistanceMeters &&
                vergenceDegrees == other.vergenceDegrees &&
                cameraPoseTx == other.cameraPoseTx &&
                cameraPoseTy == other.cameraPoseTy &&
                cameraPoseTz == other.cameraPoseTz
    }

    override fun hashCode(): Int {
        var result = isStereoReady.hashCode()
        result = 31 * result + focalLengthX.hashCode()
        result = 31 * result + focalLengthY.hashCode()
        result = 31 * result + ipdMeters.hashCode()
        result = 31 * result + eyeSeparationMeters.hashCode()
        result = 31 * result + convergenceDistanceMeters.hashCode()
        result = 31 * result + vergenceDegrees.hashCode()
        return result
    }
}
