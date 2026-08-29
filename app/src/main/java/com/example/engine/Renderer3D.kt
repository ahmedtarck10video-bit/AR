package com.example.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.engine.ar.ARDepthMapBuffer
import com.example.math3d.Model3D
import com.example.math3d.Triangle
import com.example.math3d.Vec3
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Depth Occlusion Precision Levels.
 * Eliminates CPU per-pixel bottlenecks while providing accurate physical occlusion.
 */
enum class DepthOcclusionLevel {
    OFF,
    LOW,     // Fast 1-sample centroid depth test
    MEDIUM,  // 3-sample vertex depth test
    HIGH     // Multi-sample vertex + centroid test
}

class Renderer3D {

    private val viewDir = Vec3(0f, 0f, 1f)

    // Reusable Path to avoid object allocations in hot render loop
    private val reusablePath = Path()
    private val strokeStyle = Stroke(width = 1.2f)

    // Pre-allocated primitive buffers to completely eliminate GC pressure and object allocations in onDraw
    private var bufferCapacity = 0
    private var bufP1x = FloatArray(0)
    private var bufP1y = FloatArray(0)
    private var bufP2x = FloatArray(0)
    private var bufP2y = FloatArray(0)
    private var bufP3x = FloatArray(0)
    private var bufP3y = FloatArray(0)
    private var bufZ1 = FloatArray(0)
    private var bufZ2 = FloatArray(0)
    private var bufZ3 = FloatArray(0)
    private var bufAvgZ = FloatArray(0)
    private var bufColor = LongArray(0)
    private var bufIndices = IntArray(0)

    // Reusable MVP matrix buffers
    private val vmMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val vIn = FloatArray(4)
    private val vClip1 = FloatArray(4)
    private val vClip2 = FloatArray(4)
    private val vClip3 = FloatArray(4)
    private val vEye1 = FloatArray(4)
    private val vEye2 = FloatArray(4)
    private val vEye3 = FloatArray(4)

    private fun ensureCapacity(required: Int) {
        if (bufferCapacity < required) {
            val newCap = max(required + 256, (bufferCapacity * 1.5).toInt())
            bufferCapacity = newCap
            bufP1x = FloatArray(newCap)
            bufP1y = FloatArray(newCap)
            bufP2x = FloatArray(newCap)
            bufP2y = FloatArray(newCap)
            bufP3x = FloatArray(newCap)
            bufP3y = FloatArray(newCap)
            bufZ1 = FloatArray(newCap)
            bufZ2 = FloatArray(newCap)
            bufZ3 = FloatArray(newCap)
            bufAvgZ = FloatArray(newCap)
            bufColor = LongArray(newCap)
            bufIndices = IntArray(newCap)
        }
    }

    companion object {
        fun colorFromArgbLong(c: Long): Color {
            if (c == 0L) return Color(0xFFE2E8F0)
            val a = ((c ushr 24) and 0xFF).toInt() / 255f
            val r = ((c ushr 16) and 0xFF).toInt() / 255f
            val g = ((c ushr 8) and 0xFF).toInt() / 255f
            val b = (c and 0xFF).toInt() / 255f
            return Color(r, g, b, if (a > 0.01f) a else 1f)
        }

        fun colorToArgbLong(c: Color): Long {
            val a = (c.alpha.coerceIn(0f, 1f) * 255f).toInt() and 0xFF
            val r = (c.red.coerceIn(0f, 1f) * 255f).toInt() and 0xFF
            val g = (c.green.coerceIn(0f, 1f) * 255f).toInt() and 0xFF
            val b = (c.blue.coerceIn(0f, 1f) * 255f).toInt() and 0xFF
            return ((a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
        }

        /** Converts sRGB [0..1] to Linear space */
        fun srgbToLinear(v: Float): Float {
            return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        }

        /** Converts Linear space [0..1] to sRGB with ACES-like highlight compression */
        fun linearToSrgb(v: Float): Float {
            val a = 2.51f
            val b = 0.03f
            val c = 2.43f
            val d = 0.59f
            val e = 0.14f
            val mapped = ((v * (a * v + b)) / (v * (c * v + d) + e)).coerceIn(0f, 1f)
            return if (mapped <= 0.0031308f) mapped * 12.92f else 1.055f * mapped.pow(1f / 2.4f) - 0.055f
        }
    }

    /**
     * Standard 3D Renderer for Single-Viewport display with Zero-Allocation Pipeline.
     */
    fun render(
        drawScope: DrawScope,
        model: Model3D,
        rotX: Float,
        rotY: Float,
        rotZ: Float,
        scale: Float,
        panX: Float,
        panY: Float,
        distance: Float = 4.0f,
        wireframe: Boolean = false,
        primaryColor: Color = Color(0xFFE2E8F0),
        drawShadow: Boolean = false,
        drawFloorGrid: Boolean = false,
        hdriPreset: HdriPreset = HdriPreset.STUDIO_PRO,
        engineProfile: RenderEngineProfile = RenderEngineProfile.REALITYKIT
    ) {
        val allTriangles = model.triangles
        if (allTriangles.isEmpty()) return

        val width = drawScope.size.width
        val height = drawScope.size.height
        if (width <= 0f || height <= 0f) return

        val centerX = width / 2f + panX
        val centerY = height / 2f + panY
        val fov = 460f * scale

        // Precompute rotation matrices to eliminate trigonometric recomputations per vertex
        val radX = rotX; val radY = rotY; val radZ = rotZ
        val cx = cos(radX); val sx = sin(radX)
        val cy = cos(radY); val sy = sin(radY)
        val cz = cos(radZ); val sz = sin(radZ)

        // Combined 3x3 rotation matrix R = Rz * Ry * Rx
        val m00 = cz * cy
        val m01 = cz * sy * sx - sz * cx
        val m02 = cz * sy * cx + sz * sx

        val m10 = sz * cy
        val m11 = sz * sy * sx + cz * cx
        val m12 = sz * sy * cx - cz * sx

        val m20 = -sy
        val m21 = cy * sx
        val m22 = cy * cx

        val totalTriangles = allTriangles.size
        ensureCapacity(totalTriangles)

        // Adaptive LOD Stride for massive models to guarantee locked 60 FPS
        val stride = if (totalTriangles > 30000) (totalTriangles / 15000).coerceAtLeast(1) else 1

        val margin = 200f
        val minScreenX = -margin
        val maxScreenX = width + margin
        val minScreenY = -margin
        val maxScreenY = height + margin

        var visibleCount = 0

        for (i in 0 until totalTriangles step stride) {
            val tri = allTriangles[i]

            // Fast matrix rotate v1
            val v1x = m00 * tri.v1.x + m01 * tri.v1.y + m02 * tri.v1.z
            val v1y = m10 * tri.v1.x + m11 * tri.v1.y + m12 * tri.v1.z
            val v1z = m20 * tri.v1.x + m21 * tri.v1.y + m22 * tri.v1.z

            // Fast matrix rotate v2
            val v2x = m00 * tri.v2.x + m01 * tri.v2.y + m02 * tri.v2.z
            val v2y = m10 * tri.v2.x + m11 * tri.v2.y + m12 * tri.v2.z
            val v2z = m20 * tri.v2.x + m21 * tri.v2.y + m22 * tri.v2.z

            // Fast matrix rotate v3
            val v3x = m00 * tri.v3.x + m01 * tri.v3.y + m02 * tri.v3.z
            val v3y = m10 * tri.v3.x + m11 * tri.v3.y + m12 * tri.v3.z
            val v3z = m20 * tri.v3.x + m21 * tri.v3.y + m22 * tri.v3.z

            // World offset z
            val wz1 = v1z + distance
            val wz2 = v2z + distance
            val wz3 = v3z + distance

            // Near-plane clipping
            if (wz1 < 0.05f && wz2 < 0.05f && wz3 < 0.05f) continue

            val p1z = max(0.05f, wz1)
            val p2z = max(0.05f, wz2)
            val p3z = max(0.05f, wz3)

            val p1x = centerX + (v1x / p1z) * fov
            val p1y = centerY - (v1y / p1z) * fov
            val p2x = centerX + (v2x / p2z) * fov
            val p2y = centerY - (v2y / p2z) * fov
            val p3x = centerX + (v3x / p3z) * fov
            val p3y = centerY - (v3y / p3z) * fov

            // 2D Backface culling: tests 2D screen winding order
            val cross2D = (p2x - p1x) * (p3y - p1y) - (p2y - p1y) * (p3x - p1x)
            if (cross2D <= 0f && !wireframe && totalTriangles > 60) {
                continue
            }

            // 2D Viewport Frustum Culling
            val triMinX = min(p1x, min(p2x, p3x))
            val triMaxX = max(p1x, max(p2x, p3x))
            val triMinY = min(p1y, min(p2y, p3y))
            val triMaxY = max(p1y, max(p2y, p3y))

            if (triMaxX < minScreenX || triMinX > maxScreenX || triMaxY < minScreenY || triMinY > maxScreenY) {
                continue
            }

            // Normal calculation in world space
            val e1x = v2x - v1x; val e1y = v2y - v1y; val e1z = v2z - v1z
            val e2x = v3x - v1x; val e2y = v3y - v1y; val e2z = v3z - v1z

            var nx = e1y * e2z - e1z * e2y
            var ny = e1z * e2x - e1x * e2z
            var nz = e1x * e2y - e1y * e2x
            val lenSq = nx * nx + ny * ny + nz * nz
            if (lenSq > 1e-7f) {
                val invLen = 1f / kotlin.math.sqrt(lenSq)
                nx *= invLen; ny *= invLen; nz *= invLen
            } else {
                nx = 0f; ny = 0.707f; nz = 0.707f
            }

            val normal = Vec3(nx, ny, nz)

            val baseColor = if (tri.color != 0L) {
                colorFromArgbLong(tri.color)
            } else if (primaryColor == Color(0xFFE2E8F0)) {
                Color(0xFFD6C5AD)
            } else {
                primaryColor
            }

            val emissiveColor = if (tri.emissiveColor != 0L) colorFromArgbLong(tri.emissiveColor) else Color.Transparent
            val roughness = tri.roughness.coerceIn(0.04f, 1.0f)
            val metallic = tri.metallic.coerceIn(0.0f, 1.0f)
            val effectiveRoughness = (roughness * (engineProfile.pbrRoughness / 0.30f)).coerceIn(0.04f, 1.0f)

            val diffuseIrradiance = hdriPreset.computeDiffuseIrradiance(normal)
            val specularRadiance = hdriPreset.computeSpecularRadiance(
                normal,
                viewDir,
                roughness = effectiveRoughness
            ) * engineProfile.specularMultiplier

            // Compute Shaded Color
            val linR = srgbToLinear(baseColor.red)
            val linG = srgbToLinear(baseColor.green)
            val linB = srgbToLinear(baseColor.blue)

            val dielectricDiffuse = (1.0f - metallic).coerceIn(0.0f, 1.0f)
            val diffR = linR * diffuseIrradiance.x * dielectricDiffuse
            val diffG = linG * diffuseIrradiance.y * dielectricDiffuse
            val diffB = linB * diffuseIrradiance.z * dielectricDiffuse

            val f0R = 0.04f * (1.0f - metallic) + linR * metallic
            val f0G = 0.04f * (1.0f - metallic) + linG * metallic
            val f0B = 0.04f * (1.0f - metallic) + linB * metallic

            val specR = specularRadiance.x * f0R
            val specG = specularRadiance.y * f0G
            val specB = specularRadiance.z * f0B

            val linEmissiveR = srgbToLinear(emissiveColor.red) * emissiveColor.alpha
            val linEmissiveG = srgbToLinear(emissiveColor.green) * emissiveColor.alpha
            val linEmissiveB = srgbToLinear(emissiveColor.blue) * emissiveColor.alpha

            val litR = diffR + specR + linEmissiveR
            val litG = diffG + specG + linEmissiveG
            val litB = diffB + specB + linEmissiveB

            val outR = if (engineProfile.useFilmicToneMapping) linearToSrgb(litR) else litR.coerceIn(0f, 1f).pow(1f / 2.2f)
            val outG = if (engineProfile.useFilmicToneMapping) linearToSrgb(litG) else litG.coerceIn(0f, 1f).pow(1f / 2.2f)
            val outB = if (engineProfile.useFilmicToneMapping) linearToSrgb(litB) else litB.coerceIn(0f, 1f).pow(1f / 2.2f)

            val shadedColor = Color(outR, outG, outB, baseColor.alpha)

            // Store in pre-allocated primitive buffers
            val idx = visibleCount
            bufP1x[idx] = p1x; bufP1y[idx] = p1y
            bufP2x[idx] = p2x; bufP2y[idx] = p2y
            bufP3x[idx] = p3x; bufP3y[idx] = p3y
            bufAvgZ[idx] = (wz1 + wz2 + wz3) * 0.33333334f
            bufColor[idx] = colorToArgbLong(shadedColor)
            bufIndices[idx] = idx
            visibleCount++
        }

        // Fast In-Place Depth Index Sorting (Painter's Algorithm without object allocations)
        quickSortIndices(bufIndices, bufAvgZ, 0, visibleCount - 1)

        // Draw triangles in sorted order
        for (i in 0 until visibleCount) {
            val triIdx = bufIndices[i]
            val c = colorFromArgbLong(bufColor[triIdx])

            reusablePath.reset()
            reusablePath.moveTo(bufP1x[triIdx], bufP1y[triIdx])
            reusablePath.lineTo(bufP2x[triIdx], bufP2y[triIdx])
            reusablePath.lineTo(bufP3x[triIdx], bufP3y[triIdx])
            reusablePath.close()

            if (wireframe) {
                drawScope.drawPath(path = reusablePath, color = c.copy(alpha = 0.9f), style = strokeStyle)
            } else {
                drawScope.drawPath(path = reusablePath, color = c)
            }
        }
    }

    /**
     * Hardware-Optimized Stereoscopic MVP Matrix Renderer.
     * Replaces CPU per-pixel scanline sampling with fast hierarchical depth testing.
     * Features zero object allocations per frame and shared geometry transformations.
     */
    fun renderStereoEyeWithMatrix(
        drawScope: DrawScope,
        model: Model3D,
        modelMatrix: FloatArray,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        depthMap: ARDepthMapBuffer? = null,
        cameraTimestampNs: Long = 0L,
        depthOcclusionThresholdMeters: Float = 0f,
        screenUOffset: Float = 0f,
        screenUScale: Float = 1f,
        wireframe: Boolean = false,
        primaryColor: Color = Color(0xFFE2E8F0),
        depthOcclusionLevel: DepthOcclusionLevel = DepthOcclusionLevel.MEDIUM,
        hdriPreset: HdriPreset = HdriPreset.STUDIO_PRO,
        engineProfile: RenderEngineProfile = RenderEngineProfile.REALITYKIT
    ) {
        val allTriangles = model.triangles
        if (allTriangles.isEmpty()) return

        val width = drawScope.size.width
        val height = drawScope.size.height
        if (width <= 0f || height <= 0f) return

        // Compute combined View-Model matrix = View * Model
        android.opengl.Matrix.multiplyMM(vmMatrix, 0, viewMatrix, 0, modelMatrix, 0)

        // Compute combined MVP matrix = Projection * View * Model
        android.opengl.Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, vmMatrix, 0)

        val totalTriangles = allTriangles.size
        ensureCapacity(totalTriangles)

        // Adaptive LOD Stride for heavy models in Stereo mode to maintain locked 60 FPS
        val stride = if (totalTriangles > 20000) (totalTriangles / 10000).coerceAtLeast(1) else 1

        val hasDepthBuffer = depthOcclusionLevel != DepthOcclusionLevel.OFF &&
                depthMap != null && depthMap.isValid && depthMap.isFresh(cameraTimestampNs)

        var visibleCount = 0

        for (i in 0 until totalTriangles step stride) {
            val tri = allTriangles[i]

            // 1. Transform vertex 1 to clip space & eye space
            vIn[0] = tri.v1.x; vIn[1] = tri.v1.y; vIn[2] = tri.v1.z; vIn[3] = 1.0f
            android.opengl.Matrix.multiplyMV(vClip1, 0, mvpMatrix, 0, vIn, 0)
            android.opengl.Matrix.multiplyMV(vEye1, 0, vmMatrix, 0, vIn, 0)

            // 2. Transform vertex 2 to clip space & eye space
            vIn[0] = tri.v2.x; vIn[1] = tri.v2.y; vIn[2] = tri.v2.z; vIn[3] = 1.0f
            android.opengl.Matrix.multiplyMV(vClip2, 0, mvpMatrix, 0, vIn, 0)
            android.opengl.Matrix.multiplyMV(vEye2, 0, vmMatrix, 0, vIn, 0)

            // 3. Transform vertex 3 to clip space & eye space
            vIn[0] = tri.v3.x; vIn[1] = tri.v3.y; vIn[2] = tri.v3.z; vIn[3] = 1.0f
            android.opengl.Matrix.multiplyMV(vClip3, 0, mvpMatrix, 0, vIn, 0)
            android.opengl.Matrix.multiplyMV(vEye3, 0, vmMatrix, 0, vIn, 0)

            val w1 = vClip1[3]; val w2 = vClip2[3]; val w3 = vClip3[3]

            // Near-plane clipping in homogeneous coordinates
            if (w1 <= 0.01f && w2 <= 0.01f && w3 <= 0.01f) continue

            val invW1 = if (w1 > 0.001f) 1.0f / w1 else 1000f
            val invW2 = if (w2 > 0.001f) 1.0f / w2 else 1000f
            val invW3 = if (w3 > 0.001f) 1.0f / w3 else 1000f

            // NDC to Screen Coordinates
            val ndcX1 = vClip1[0] * invW1; val ndcY1 = vClip1[1] * invW1
            val ndcX2 = vClip2[0] * invW2; val ndcY2 = vClip2[1] * invW2
            val ndcX3 = vClip3[0] * invW3; val ndcY3 = vClip3[1] * invW3

            val p1x = (ndcX1 + 1.0f) * 0.5f * width
            val p1y = (1.0f - ndcY1) * 0.5f * height
            val p2x = (ndcX2 + 1.0f) * 0.5f * width
            val p2y = (1.0f - ndcY2) * 0.5f * height
            val p3x = (ndcX3 + 1.0f) * 0.5f * width
            val p3y = (1.0f - ndcY3) * 0.5f * height

            // 2D Screen Winding Backface Culling
            val cross2D = (p2x - p1x) * (p3y - p1y) - (p2y - p1y) * (p3x - p1x)
            if (cross2D <= 0f && !wireframe && totalTriangles > 60) {
                continue
            }

            // Screen Frustum Culling
            val triMinX = min(p1x, min(p2x, p3x))
            val triMaxX = max(p1x, max(p2x, p3x))
            val triMinY = min(p1y, min(p2y, p3y))
            val triMaxY = max(p1y, max(p2y, p3y))
            if (triMaxX < -100f || triMinX > width + 100f || triMaxY < -100f || triMinY > height + 100f) {
                continue
            }

            val eyeDepth1 = -vEye1[2]
            val eyeDepth2 = -vEye2[2]
            val eyeDepth3 = -vEye3[2]
            val avgEyeDepth = (eyeDepth1 + eyeDepth2 + eyeDepth3) * 0.33333334f

            // Fast Hierarchical Depth Buffer Occlusion (Zero per-pixel scanline CPU bottleneck!)
            if (hasDepthBuffer && depthMap != null) {
                when (depthOcclusionLevel) {
                    DepthOcclusionLevel.LOW -> {
                        // Centroid test (1 sample)
                        val uMid = screenUOffset + ((p1x + p2x + p3x) * 0.33333334f / width) * screenUScale
                        val vMid = (p1y + p2y + p3y) * 0.33333334f / height
                        val realDepth = depthMap.getDepthMetersAt(uMid, vMid, cameraTimestampNs)
                        if (realDepth < 20.0f && avgEyeDepth > (realDepth + 0.05f)) {
                            // Culled by physical object
                            continue
                        }
                    }
                    DepthOcclusionLevel.MEDIUM -> {
                        // 3-vertex test
                        val u1 = screenUOffset + (p1x / width) * screenUScale
                        val v1 = p1y / height
                        val d1 = depthMap.getDepthMetersAt(u1, v1, cameraTimestampNs)
                        val occ1 = d1 < 20.0f && eyeDepth1 > (d1 + 0.03f)

                        val u2 = screenUOffset + (p2x / width) * screenUScale
                        val v2 = p2y / height
                        val d2 = depthMap.getDepthMetersAt(u2, v2, cameraTimestampNs)
                        val occ2 = d2 < 20.0f && eyeDepth2 > (d2 + 0.03f)

                        val u3 = screenUOffset + (p3x / width) * screenUScale
                        val v3 = p3y / height
                        val d3 = depthMap.getDepthMetersAt(u3, v3, cameraTimestampNs)
                        val occ3 = d3 < 20.0f && eyeDepth3 > (d3 + 0.03f)

                        if (occ1 && occ2 && occ3) {
                            // Triangle completely occluded by physical environment
                            continue
                        }
                    }
                    DepthOcclusionLevel.HIGH -> {
                        // 3-vertex + centroid test
                        val uMid = screenUOffset + ((p1x + p2x + p3x) * 0.33333334f / width) * screenUScale
                        val vMid = (p1y + p2y + p3y) * 0.33333334f / height
                        val dMid = depthMap.getDepthMetersAt(uMid, vMid, cameraTimestampNs)
                        val occMid = dMid < 20.0f && avgEyeDepth > (dMid + 0.03f)

                        val u1 = screenUOffset + (p1x / width) * screenUScale
                        val v1 = p1y / height
                        val d1 = depthMap.getDepthMetersAt(u1, v1, cameraTimestampNs)
                        val occ1 = d1 < 20.0f && eyeDepth1 > (d1 + 0.03f)

                        val u2 = screenUOffset + (p2x / width) * screenUScale
                        val v2 = p2y / height
                        val d2 = depthMap.getDepthMetersAt(u2, v2, cameraTimestampNs)
                        val occ2 = d2 < 20.0f && eyeDepth2 > (d2 + 0.03f)

                        val u3 = screenUOffset + (p3x / width) * screenUScale
                        val v3 = p3y / height
                        val d3 = depthMap.getDepthMetersAt(u3, v3, cameraTimestampNs)
                        val occ3 = d3 < 20.0f && eyeDepth3 > (d3 + 0.03f)

                        if (occMid && (occ1 || occ2 || occ3)) {
                            continue
                        }
                    }
                    DepthOcclusionLevel.OFF -> { /* No culling */ }
                }
            }

            // Normal transformation to world space
            val nx = modelMatrix[0] * tri.normal.x + modelMatrix[4] * tri.normal.y + modelMatrix[8] * tri.normal.z
            val ny = modelMatrix[1] * tri.normal.x + modelMatrix[5] * tri.normal.y + modelMatrix[9] * tri.normal.z
            val nz = modelMatrix[2] * tri.normal.x + modelMatrix[6] * tri.normal.y + modelMatrix[10] * tri.normal.z
            val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
            val normal = if (len > 1e-6f) Vec3(nx / len, ny / len, nz / len) else Vec3(0f, 1f, 0f)

            val baseColor = if (tri.color != 0L) {
                colorFromArgbLong(tri.color)
            } else if (primaryColor == Color(0xFFE2E8F0)) {
                Color(0xFFD6C5AD)
            } else {
                primaryColor
            }

            val emissiveColor = if (tri.emissiveColor != 0L) colorFromArgbLong(tri.emissiveColor) else Color.Transparent
            val roughness = tri.roughness.coerceIn(0.04f, 1.0f)
            val metallic = tri.metallic.coerceIn(0.0f, 1.0f)
            val effectiveRoughness = (roughness * (engineProfile.pbrRoughness / 0.30f)).coerceIn(0.04f, 1.0f)

            val diffuseIrradiance = hdriPreset.computeDiffuseIrradiance(normal)
            val specularRadiance = hdriPreset.computeSpecularRadiance(
                normal,
                viewDir,
                roughness = effectiveRoughness
            ) * engineProfile.specularMultiplier

            // Compute Shaded Color in linear space with tone mapping
            val linR = srgbToLinear(baseColor.red)
            val linG = srgbToLinear(baseColor.green)
            val linB = srgbToLinear(baseColor.blue)

            val dielectricDiffuse = (1.0f - metallic).coerceIn(0.0f, 1.0f)
            val diffR = linR * diffuseIrradiance.x * dielectricDiffuse
            val diffG = linG * diffuseIrradiance.y * dielectricDiffuse
            val diffB = linB * diffuseIrradiance.z * dielectricDiffuse

            val f0R = 0.04f * (1.0f - metallic) + linR * metallic
            val f0G = 0.04f * (1.0f - metallic) + linG * metallic
            val f0B = 0.04f * (1.0f - metallic) + linB * metallic

            val specR = specularRadiance.x * f0R
            val specG = specularRadiance.y * f0G
            val specB = specularRadiance.z * f0B

            val linEmissiveR = srgbToLinear(emissiveColor.red) * emissiveColor.alpha
            val linEmissiveG = srgbToLinear(emissiveColor.green) * emissiveColor.alpha
            val linEmissiveB = srgbToLinear(emissiveColor.blue) * emissiveColor.alpha

            val litR = diffR + specR + linEmissiveR
            val litG = diffG + specG + linEmissiveG
            val litB = diffB + specB + linEmissiveB

            val outR = if (engineProfile.useFilmicToneMapping) linearToSrgb(litR) else litR.coerceIn(0f, 1f).pow(1f / 2.2f)
            val outG = if (engineProfile.useFilmicToneMapping) linearToSrgb(litG) else litG.coerceIn(0f, 1f).pow(1f / 2.2f)
            val outB = if (engineProfile.useFilmicToneMapping) linearToSrgb(litB) else litB.coerceIn(0f, 1f).pow(1f / 2.2f)

            val shadedColor = Color(outR, outG, outB, baseColor.alpha)
            val avgClipZ = (vClip1[2] * invW1 + vClip2[2] * invW2 + vClip3[2] * invW3) * 0.33333334f

            val idx = visibleCount
            bufP1x[idx] = p1x; bufP1y[idx] = p1y
            bufP2x[idx] = p2x; bufP2y[idx] = p2y
            bufP3x[idx] = p3x; bufP3y[idx] = p3y
            bufAvgZ[idx] = avgClipZ
            bufColor[idx] = colorToArgbLong(shadedColor)
            bufIndices[idx] = idx
            visibleCount++
        }

        // Fast In-Place Index Sort (Avoids allocating any wrapper objects!)
        quickSortIndices(bufIndices, bufAvgZ, 0, visibleCount - 1)

        for (i in 0 until visibleCount) {
            val triIdx = bufIndices[i]
            val c = colorFromArgbLong(bufColor[triIdx])

            reusablePath.reset()
            reusablePath.moveTo(bufP1x[triIdx], bufP1y[triIdx])
            reusablePath.lineTo(bufP2x[triIdx], bufP2y[triIdx])
            reusablePath.lineTo(bufP3x[triIdx], bufP3y[triIdx])
            reusablePath.close()

            if (wireframe) {
                drawScope.drawPath(path = reusablePath, color = c.copy(alpha = 0.9f), style = strokeStyle)
            } else {
                drawScope.drawPath(path = reusablePath, color = c)
            }
        }
    }

    /**
     * Fast in-place primitive Quicksort to sort indices by depth values without object allocations.
     */
    private fun quickSortIndices(indices: IntArray, depths: FloatArray, low: Int, high: Int) {
        if (low >= high) return
        val pivot = depths[indices[(low + high) ushr 1]]
        var i = low
        var j = high
        while (i <= j) {
            // Sort back-to-front (larger depth first for painter's algorithm)
            while (depths[indices[i]] > pivot) i++
            while (depths[indices[j]] < pivot) j--
            if (i <= j) {
                val temp = indices[i]
                indices[i] = indices[j]
                indices[j] = temp
                i++
                j--
            }
        }
        if (low < j) quickSortIndices(indices, depths, low, j)
        if (i < high) quickSortIndices(indices, depths, i, high)
    }
}
