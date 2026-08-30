package com.example.engine

import androidx.compose.ui.graphics.Color

/**
 * 3D & Spatial XR Rendering Shading Profiles.
 * Controls shading algorithms, PBR texture sampling, lighting equations, and shadow rendering.
 * Provides specialized profile presets for Filament PBR, Real-time Mobile Shading, Studio Lighting, and High-Fidelity Ray-tracing.
 */
enum class RenderEngineProfile(
    val title: String,
    val shortName: String,
    val subtitle: String,
    val description: String,
    val themeColor: Color,
    val pbrRoughness: Float,
    val specularMultiplier: Float,
    val shadowIntensity: Float,
    val useFilmicToneMapping: Boolean
) {
    REALITYKIT(
        title = "PBR Ultra Quality Profile",
        shortName = "Ultra PBR",
        subtitle = "Physically Based Rendering & Specular Reflections",
        description = "Advanced vision-grade physically based rendering, dynamic Fresnel Schlick reflections, and spatial environment mapping.",
        themeColor = Color(0xFF00E5FF),
        pbrRoughness = 0.22f,
        specularMultiplier = 1.40f,
        shadowIntensity = 1.15f,
        useFilmicToneMapping = true
    ),
    SCENEKIT(
        title = "Multi-Pass Studio Profile",
        shortName = "Studio",
        subtitle = "High-Precision 3D Scene Graph Lighting",
        description = "Multi-pass lighting pipeline, dynamic Phong/PBR specular highlights, and real-time volumetric shadows.",
        themeColor = Color(0xFF9D4EDD),
        pbrRoughness = 0.28f,
        specularMultiplier = 1.25f,
        shadowIntensity = 1.10f,
        useFilmicToneMapping = true
    ),
    ARKIT(
        title = "AR Low-Latency Profile",
        shortName = "Low Latency",
        subtitle = "Visual Inertial Odometry & Anchoring Preset",
        description = "Optimized for 6-DoF SLAM tracking, fast frame rendering, and realistic camera passthrough depth occlusion.",
        themeColor = Color(0xFF00FF88),
        pbrRoughness = 0.25f,
        specularMultiplier = 1.30f,
        shadowIntensity = 1.20f,
        useFilmicToneMapping = true
    ),
    MODELIO(
        title = "Mobile Fast Profile",
        shortName = "Fast Mobile",
        subtitle = "Low Power & High Performance Rendering",
        description = "Direct vertex attribute extraction, UV texture coordinate mapping, and low-power spatial mesh synthesis.",
        themeColor = Color(0xFFFFB703),
        pbrRoughness = 0.35f,
        specularMultiplier = 1.05f,
        shadowIntensity = 0.95f,
        useFilmicToneMapping = true
    ),
    FILAMENT(
        title = "Google Filament PBR",
        shortName = "Filament",
        subtitle = "Physically Based Real-Time PBR Engine",
        description = "High-precision HDRi spherical irradiance, Cook-Torrance specular reflections, and ACES filmic highlight compression.",
        themeColor = Color(0xFF38BDF8),
        pbrRoughness = 0.25f,
        specularMultiplier = 1.35f,
        shadowIntensity = 1.1f,
        useFilmicToneMapping = true
    ),
    SCENEVIEW(
        title = "Sceneview Dynamic Profile",
        shortName = "Sceneview",
        subtitle = "Jetpack Compose + Filament GLB Engine",
        description = "Optimized for glTF/GLB PBR material textures, UV texture sampling, and seamless AR spatial tracking.",
        themeColor = Color(0xFFFF0055),
        pbrRoughness = 0.30f,
        specularMultiplier = 1.15f,
        shadowIntensity = 1.0f,
        useFilmicToneMapping = true
    )
}
