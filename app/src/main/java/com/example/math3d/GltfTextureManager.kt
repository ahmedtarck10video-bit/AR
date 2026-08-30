package com.example.math3d

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses textures, images, samplers, and materials from GLTF/GLB files
 * and provides pixel sampling based on UV texture coordinates.
 */
class GltfTextureManager(
    private val root: JSONObject,
    private val buffersList: List<ByteArray>,
    private val modelDir: java.io.File? = null
) {
    private val imagesList = mutableListOf<Bitmap?>()
    private val texturesList = mutableListOf<Int>() // maps textureIndex -> imageIndex

    init {
        loadImages()
        loadTextures()
    }

    private fun loadImages() {
        val images = root.optJSONArray("images") ?: return
        val bufferViews = root.optJSONArray("bufferViews")

        for (i in 0 until images.length()) {
            val imgObj = images.optJSONObject(i) ?: continue
            var bmp: Bitmap? = null

            try {
                if (imgObj.has("bufferView") && bufferViews != null) {
                    val bvIdx = imgObj.getInt("bufferView")
                    if (bvIdx in 0 until bufferViews.length()) {
                        val bv = bufferViews.getJSONObject(bvIdx)
                        val bufIdx = bv.optInt("buffer", 0)
                        val rawBuf = buffersList.getOrNull(bufIdx)
                        if (rawBuf != null) {
                            val byteOffset = bv.optInt("byteOffset", 0)
                            val byteLength = bv.getInt("byteLength")
                            if (byteOffset + byteLength <= rawBuf.size) {
                                bmp = BitmapFactory.decodeByteArray(rawBuf, byteOffset, byteLength)
                            }
                        }
                    }
                } else if (imgObj.has("uri")) {
                    val rawUriStr = imgObj.getString("uri")
                    val uriStr = try {
                        java.net.URLDecoder.decode(rawUriStr, "UTF-8")
                    } catch (e: Exception) {
                        rawUriStr
                    }

                    if (uriStr.startsWith("data:") && uriStr.contains("base64,")) {
                        val b64 = uriStr.substringAfter("base64,").trim()
                        val imgBytes = Base64.decode(b64, Base64.DEFAULT)
                        bmp = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size)
                    } else if (modelDir != null) {
                        val cleanRelPath = uriStr.replace('\\', '/')
                        var externalFile = java.io.File(modelDir, cleanRelPath)
                        
                        if (!externalFile.exists()) {
                            // Try subfolders like textures/, images/ or flat name in modelDir
                            val fileName = cleanRelPath.substringAfterLast('/')
                            val altFile1 = java.io.File(modelDir, fileName)
                            val altFile2 = java.io.File(modelDir, "textures/$fileName")
                            val altFile3 = java.io.File(modelDir, "images/$fileName")
                            
                            externalFile = when {
                                altFile1.exists() -> altFile1
                                altFile2.exists() -> altFile2
                                altFile3.exists() -> altFile3
                                else -> externalFile
                            }
                        }

                        if (externalFile.exists() && externalFile.canRead()) {
                            bmp = BitmapFactory.decodeFile(externalFile.absolutePath)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            imagesList.add(bmp)
        }
    }

    private fun loadTextures() {
        val textures = root.optJSONArray("textures") ?: return
        for (i in 0 until textures.length()) {
            val texObj = textures.optJSONObject(i)
            val source = texObj?.optInt("source", -1) ?: -1
            texturesList.add(source)
        }
    }

    /**
     * Resolves the baseColorTexture bitmap for a material index (PBR Metallic-Roughness).
     */
    fun getBaseColorTexture(matIdx: Int): Bitmap? {
        val materials = root.optJSONArray("materials") ?: return null
        if (matIdx !in 0 until materials.length()) return null
        val matObj = materials.optJSONObject(matIdx) ?: return null
        val pbr = matObj.optJSONObject("pbrMetallicRoughness") ?: return null
        val texIdx = pbr.optJSONObject("baseColorTexture")?.optInt("index", -1) ?: -1
        return resolveTextureBitmap(texIdx)
    }

    /**
     * Resolves the diffuseTexture bitmap (from KHR_materials_pbrSpecularGlossiness or legacy diffuse).
     */
    fun getDiffuseTexture(matIdx: Int): Bitmap? {
        val materials = root.optJSONArray("materials") ?: return null
        if (matIdx !in 0 until materials.length()) return null
        val matObj = materials.optJSONObject(matIdx) ?: return null
        val specGloss = matObj.optJSONObject("extensions")?.optJSONObject("KHR_materials_pbrSpecularGlossiness")
        val texIdx = specGloss?.optJSONObject("diffuseTexture")?.optInt("index", -1) ?: -1
        return resolveTextureBitmap(texIdx)
    }

    /**
     * Resolves the emissiveTexture bitmap for a material index.
     */
    fun getEmissiveTexture(matIdx: Int): Bitmap? {
        val materials = root.optJSONArray("materials") ?: return null
        if (matIdx !in 0 until materials.length()) return null
        val matObj = materials.optJSONObject(matIdx) ?: return null
        val texIdx = matObj.optJSONObject("emissiveTexture")?.optInt("index", -1) ?: -1
        return resolveTextureBitmap(texIdx)
    }

    fun getNormalTexture(matIdx: Int): Bitmap? {
        val materials = root.optJSONArray("materials") ?: return null
        if (matIdx !in 0 until materials.length()) return null
        val matObj = materials.optJSONObject(matIdx) ?: return null
        val texIdx = matObj.optJSONObject("normalTexture")?.optInt("index", -1) ?: -1
        return resolveTextureBitmap(texIdx)
    }

    private fun resolveTextureBitmap(texIdx: Int): Bitmap? {
        if (texIdx != -1 && texIdx in texturesList.indices) {
            val imgIdx = texturesList[texIdx]
            if (imgIdx in imagesList.indices) {
                return imagesList[imgIdx]
            }
        }
        return null
    }

    private fun resolveSamplerWrapping(texIdx: Int): Pair<Int, Int> {
        val textures = root.optJSONArray("textures") ?: return Pair(10497, 10497)
        if (texIdx !in 0 until textures.length()) return Pair(10497, 10497)
        val texObj = textures.optJSONObject(texIdx) ?: return Pair(10497, 10497)
        val samplerIdx = texObj.optInt("sampler", -1)
        val samplers = root.optJSONArray("samplers") ?: return Pair(10497, 10497)
        if (samplerIdx in 0 until samplers.length()) {
            val sampObj = samplers.optJSONObject(samplerIdx) ?: return Pair(10497, 10497)
            val wrapS = sampObj.optInt("wrapS", 10497)
            val wrapT = sampObj.optInt("wrapT", 10497)
            return Pair(wrapS, wrapT)
        }
        return Pair(10497, 10497)
    }

    private fun resolveTextureTransform(texInfoObj: JSONObject?): GltfTextureTransform {
        if (texInfoObj == null) return GltfTextureTransform()
        val ext = texInfoObj.optJSONObject("extensions")?.optJSONObject("KHR_texture_transform")
            ?: return GltfTextureTransform()
        val offsetArr = ext.optJSONArray("offset")
        val ox = if (offsetArr != null && offsetArr.length() >= 2) offsetArr.getDouble(0).toFloat() else 0f
        val oy = if (offsetArr != null && offsetArr.length() >= 2) offsetArr.getDouble(1).toFloat() else 0f
        val scaleArr = ext.optJSONArray("scale")
        val sx = if (scaleArr != null && scaleArr.length() >= 2) scaleArr.getDouble(0).toFloat() else 1f
        val sy = if (scaleArr != null && scaleArr.length() >= 2) scaleArr.getDouble(1).toFloat() else 1f
        val rot = ext.optDouble("rotation", 0.0).toFloat()
        return GltfTextureTransform(ox, oy, sx, sy, rot)
    }

    /**
     * Resolves the full PBR material properties and bound textures for a material index.
     */
    fun getPbrMaterial(matIdx: Int): GltfPbrMaterial {
        val materials = root.optJSONArray("materials")
        if (materials == null || matIdx !in 0 until materials.length()) {
            return GltfPbrMaterial()
        }
        val matObj = materials.optJSONObject(matIdx) ?: return GltfPbrMaterial()

        val baseTex = getBaseColorTexture(matIdx)
        val diffTex = getDiffuseTexture(matIdx)
        val emissTex = getEmissiveTexture(matIdx)
        val normalTex = getNormalTexture(matIdx)

        var baseColorFactor = 0L
        var metallic = 0.0f
        var roughness = 0.5f

        val pbr = matObj.optJSONObject("pbrMetallicRoughness")
        var texTransform = GltfTextureTransform()
        var wrapping = Pair(10497, 10497)

        if (pbr != null) {
            metallic = pbr.optDouble("metallicFactor", 0.0).toFloat().coerceIn(0f, 1f)
            roughness = pbr.optDouble("roughnessFactor", 0.5).toFloat().coerceIn(0.04f, 1f)

            val baseColorTexObj = pbr.optJSONObject("baseColorTexture")
            if (baseColorTexObj != null) {
                texTransform = resolveTextureTransform(baseColorTexObj)
                wrapping = resolveSamplerWrapping(baseColorTexObj.optInt("index", -1))
            }

            if (pbr.has("baseColorFactor")) {
                val bcf = pbr.getJSONArray("baseColorFactor")
                val r = (bcf.optDouble(0, 1.0) * 255.0).toInt().coerceIn(0, 255)
                val g = (bcf.optDouble(1, 1.0) * 255.0).toInt().coerceIn(0, 255)
                val b = (bcf.optDouble(2, 1.0) * 255.0).toInt().coerceIn(0, 255)
                val a = (bcf.optDouble(3, 1.0) * 255.0).toInt().coerceIn(0, 255)
                baseColorFactor = ((a.toLong() and 0xFF) shl 24) or
                        ((r.toLong() and 0xFF) shl 16) or
                        ((g.toLong() and 0xFF) shl 8) or
                        (b.toLong() and 0xFF)
            }
        }

        var diffuseFactor = 0L
        val specGloss = matObj.optJSONObject("extensions")?.optJSONObject("KHR_materials_pbrSpecularGlossiness")
        if (specGloss?.has("diffuseFactor") == true) {
            val df = specGloss.getJSONArray("diffuseFactor")
            val r = (df.optDouble(0, 1.0) * 255.0).toInt().coerceIn(0, 255)
            val g = (df.optDouble(1, 1.0) * 255.0).toInt().coerceIn(0, 255)
            val b = (df.optDouble(2, 1.0) * 255.0).toInt().coerceIn(0, 255)
            val a = (df.optDouble(3, 1.0) * 255.0).toInt().coerceIn(0, 255)
            diffuseFactor = ((a.toLong() and 0xFF) shl 24) or
                    ((r.toLong() and 0xFF) shl 16) or
                    ((g.toLong() and 0xFF) shl 8) or
                    (b.toLong() and 0xFF)
        }

        var emissiveFactor = 0L
        if (matObj.has("emissiveFactor")) {
            val ef = matObj.getJSONArray("emissiveFactor")
            val r = (ef.optDouble(0, 0.0) * 255.0).toInt().coerceIn(0, 255)
            val g = (ef.optDouble(1, 0.0) * 255.0).toInt().coerceIn(0, 255)
            val b = (ef.optDouble(2, 0.0) * 255.0).toInt().coerceIn(0, 255)
            if (r > 0 || g > 0 || b > 0) {
                emissiveFactor = (0xFFL shl 24) or
                        ((r.toLong() and 0xFF) shl 16) or
                        ((g.toLong() and 0xFF) shl 8) or
                        (b.toLong() and 0xFF)
            }
        }

        val alphaMode = matObj.optString("alphaMode", "OPAQUE")
        val alphaCutoff = matObj.optDouble("alphaCutoff", 0.5).toFloat()

        return GltfPbrMaterial(
            baseColorTexture = baseTex,
            diffuseTexture = diffTex,
            emissiveTexture = emissTex,
            normalTexture = normalTex,
            baseColorFactor = baseColorFactor,
            diffuseFactor = diffuseFactor,
            emissiveFactor = emissiveFactor,
            metallic = metallic,
            roughness = roughness,
            alphaMode = alphaMode,
            alphaCutoff = alphaCutoff,
            wrapS = wrapping.first,
            wrapT = wrapping.second,
            textureTransform = texTransform
        )
    }

    /**
     * Resolves the bitmap for a material index, checking:
     * 1. pbrMetallicRoughness.baseColorTexture
     * 2. extensions.KHR_materials_pbrSpecularGlossiness.diffuseTexture
     * 3. emissiveTexture
     */
    fun getTextureBitmapForMaterial(matIdx: Int): Bitmap? {
        return getBaseColorTexture(matIdx)
            ?: getDiffuseTexture(matIdx)
            ?: getEmissiveTexture(matIdx)
            ?: getNormalTexture(matIdx)
    }

    companion object {
        /**
         * Bilinearly or nearest-neighbor samples a bitmap using normalized UV coordinates (0..1)
         */
        fun sampleTexture(bitmap: Bitmap, u: Float, v: Float, wrapS: Int = 10497, wrapT: Int = 10497): Long {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) return 0L

            fun wrapCoord(c: Float, mode: Int): Float {
                return when (mode) {
                    33071 -> c.coerceIn(0f, 1f) // CLAMP_TO_EDGE
                    33648 -> { // MIRRORED_REPEAT
                        val floor = kotlin.math.floor(c).toInt()
                        val frac = c - floor
                        if (floor % 2 != 0) 1f - frac else frac
                    }
                    else -> { // 10497: REPEAT
                        var nc = c % 1.0f
                        if (nc < 0f) nc += 1.0f
                        nc
                    }
                }
            }

            val nu = wrapCoord(u, wrapS)
            val nv = wrapCoord(v, wrapT)

            // In glTF, UV (0,0) is top-left
            val px = (nu * (w - 1)).toInt().coerceIn(0, w - 1)
            val py = (nv * (h - 1)).toInt().coerceIn(0, h - 1)

            val pixel = bitmap.getPixel(px, py)
            val a = (pixel ushr 24) and 0xFF
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            return ((a.toLong() and 0xFF) shl 24) or
                    ((r.toLong() and 0xFF) shl 16) or
                    ((g.toLong() and 0xFF) shl 8) or
                    (b.toLong() and 0xFF)
        }
    }
}

data class GltfTextureTransform(
    val offsetU: Float = 0f,
    val offsetV: Float = 0f,
    val scaleU: Float = 1f,
    val scaleV: Float = 1f,
    val rotationRad: Float = 0f
) {
    fun transform(u: Float, v: Float): Pair<Float, Float> {
        var tu = u * scaleU
        var tv = v * scaleV
        if (rotationRad != 0f) {
            val cosR = kotlin.math.cos(rotationRad)
            val sinR = kotlin.math.sin(rotationRad)
            val ru = tu * cosR - tv * sinR
            val rv = tu * sinR + tv * cosR
            tu = ru
            tv = rv
        }
        tu += offsetU
        tv += offsetV
        return Pair(tu, tv)
    }
}

/**
 * Encapsulates extracted PBR texture maps and material coefficients.
 */
data class GltfPbrMaterial(
    val baseColorTexture: Bitmap? = null,
    val diffuseTexture: Bitmap? = null,
    val emissiveTexture: Bitmap? = null,
    val normalTexture: Bitmap? = null,
    val baseColorFactor: Long = 0L,
    val diffuseFactor: Long = 0L,
    val emissiveFactor: Long = 0L,
    val metallic: Float = 0.0f,
    val roughness: Float = 0.5f,
    val alphaMode: String = "OPAQUE",
    val alphaCutoff: Float = 0.5f,
    val wrapS: Int = 10497, // 10497: REPEAT, 33071: CLAMP_TO_EDGE, 33648: MIRRORED_REPEAT
    val wrapT: Int = 10497,
    val textureTransform: GltfTextureTransform = GltfTextureTransform()
) {
    fun sampleBaseOrDiffuseColor(u: Float, v: Float, fallbackVertexColor: Long = 0L): Long {
        val (tu, tv) = textureTransform.transform(u, v)
        if (baseColorTexture != null) {
            val sampled = GltfTextureManager.sampleTexture(baseColorTexture, tu, tv, wrapS, wrapT)
            if (sampled != 0L) {
                val alpha = (sampled ushr 24) and 0xFF
                if (alphaMode == "MASK" && (alpha / 255f) < alphaCutoff) return 0L
                return multiplyColors(sampled, baseColorFactor)
            }
        }
        if (diffuseTexture != null) {
            val sampled = GltfTextureManager.sampleTexture(diffuseTexture, tu, tv, wrapS, wrapT)
            if (sampled != 0L) {
                val alpha = (sampled ushr 24) and 0xFF
                if (alphaMode == "MASK" && (alpha / 255f) < alphaCutoff) return 0L
                return multiplyColors(sampled, diffuseFactor)
            }
        }
        if (baseColorFactor != 0L) return baseColorFactor
        if (diffuseFactor != 0L) return diffuseFactor
        return fallbackVertexColor
    }

    fun sampleEmissiveColor(u: Float, v: Float): Long {
        val (tu, tv) = textureTransform.transform(u, v)
        if (emissiveTexture != null) {
            val sampled = GltfTextureManager.sampleTexture(emissiveTexture, tu, tv, wrapS, wrapT)
            if (sampled != 0L) return sampled
        }
        return emissiveFactor
    }

    private fun multiplyColors(c1: Long, c2: Long): Long {
        if (c2 == 0L) return c1
        val a1 = ((c1 ushr 24) and 0xFF).toFloat() / 255f
        val r1 = ((c1 ushr 16) and 0xFF).toFloat() / 255f
        val g1 = ((c1 ushr 8) and 0xFF).toFloat() / 255f
        val b1 = (c1 and 0xFF).toFloat() / 255f

        val a2 = ((c2 ushr 24) and 0xFF).toFloat() / 255f
        val r2 = ((c2 ushr 16) and 0xFF).toFloat() / 255f
        val g2 = ((c2 ushr 8) and 0xFF).toFloat() / 255f
        val b2 = (c2 and 0xFF).toFloat() / 255f

        val a = ((a1 * a2) * 255f).toInt().coerceIn(0, 255)
        val r = ((r1 * r2) * 255f).toInt().coerceIn(0, 255)
        val g = ((g1 * g2) * 255f).toInt().coerceIn(0, 255)
        val b = ((b1 * b2) * 255f).toInt().coerceIn(0, 255)

        return ((a.toLong() and 0xFF) shl 24) or
                ((r.toLong() and 0xFF) shl 16) or
                ((g.toLong() and 0xFF) shl 8) or
                (b.toLong() and 0xFF)
    }
}

