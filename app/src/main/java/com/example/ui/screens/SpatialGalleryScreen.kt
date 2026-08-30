package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.engine.media.SavedMediaItem
import com.example.ui.components.GlassCard
import com.example.ui.theme.GlowGreen
import com.example.ui.theme.NeonCyan
import com.example.viewmodel.MixedRealityViewModel

data class SpatialAsset(
    val title: String,
    val category: String,
    val vertices: Int,
    val triangles: Int,
    val modelIndex: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

enum class GalleryTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CAPTURES("Saved Media", Icons.Default.Collections),
    MODELS("3D Models", Icons.Default.ViewInAr)
}

enum class MediaFilter(val title: String) {
    ALL("All"),
    PHOTOS("Photos 📸"),
    VIDEOS("Recordings 🎥")
}

@Composable
fun SpatialGalleryScreen(
    viewModel: MixedRealityViewModel,
    onOpenIn3D: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var activeTab by remember { mutableStateOf(GalleryTab.CAPTURES) }
    var mediaFilter by remember { mutableStateOf(MediaFilter.ALL) }
    var selectedMediaPreview by remember { mutableStateOf<SavedMediaItem?>(null) }

    val assets = remember {
        listOf(
            SpatialAsset("Vision Pro Visor [USDZ]", "RealityKit • USDZ", 840, 1680, 0, Icons.Default.ViewInAr),
            SpatialAsset("Cyber Drone [GLB]", "SceneKit • GLB", 620, 1240, 1, Icons.Default.FlightTakeoff),
            SpatialAsset("Companion Bot [GLTF]", "ARKit • GLTF", 950, 1900, 2, Icons.Default.SmartToy),
            SpatialAsset("Spatial Audio Pod [USDZ]", "ModelIO • USDZ", 520, 1040, 3, Icons.Default.SurroundSound),
            SpatialAsset("Spatial Anchor Prism [GLB]", "Spatial 3D • GLB", 8, 12, 4, Icons.Default.Category)
        )
    }
    var selectedAssetIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.loadGalleryCaptures(context)
    }

    val filteredMedia = remember(uiState.galleryCaptures, mediaFilter) {
        when (mediaFilter) {
            MediaFilter.ALL -> uiState.galleryCaptures
            MediaFilter.PHOTOS -> uiState.galleryCaptures.filter { !it.isVideo }
            MediaFilter.VIDEOS -> uiState.galleryCaptures.filter { it.isVideo }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Tab Segmented Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x331E293B))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GalleryTab.values().forEach { tab ->
                val isSelected = activeTab == tab
                val countBadge = when (tab) {
                    GalleryTab.CAPTURES -> " (${uiState.galleryCaptures.size})"
                    GalleryTab.MODELS -> " (${assets.size})"
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) NeonCyan.copy(alpha = 0.85f) else Color.Transparent)
                        .clickable { activeTab = tab },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${tab.title}$countBadge",
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        when (activeTab) {
            GalleryTab.CAPTURES -> {
                // Media Captures View
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Filter Chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MediaFilter.values().forEach { filter ->
                            val isSelected = mediaFilter == filter
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0x6600E5FF) else Color(0x26FFFFFF))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) NeonCyan else Color(0x33FFFFFF),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { mediaFilter = filter }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = filter.title,
                                    color = if (isSelected) NeonCyan else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Refresh Button
                    IconButton(
                        onClick = { viewModel.loadGalleryCaptures(context) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Gallery",
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (filteredMedia.isEmpty()) {
                    // Empty State
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = Color(0x221E293B)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x3300E5FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Text(
                                    text = "No Captures in Gallery Yet",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "Tap PHOTO or REC on the bottom bar to capture high-res snapshots and video recordings directly to your Android Gallery.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xAAFFFFFF),
                                        fontSize = 12.sp
                                    ),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // Captures List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredMedia, key = { it.id }) { item ->
                            MediaItemCard(
                                item = item,
                                onClick = { selectedMediaPreview = item },
                                onShare = { shareMedia(context, item) },
                                onDelete = { viewModel.deleteGalleryItem(item, context) }
                            )
                        }
                    }
                }
            }

            GalleryTab.MODELS -> {
                // 3D Models Library View
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Holographic Assets",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Pre-loaded procedural & PBR meshes",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xAAFFFFFF))
                        )
                    }

                    Button(
                        onClick = { onOpenIn3D(assets[selectedAssetIndex].modelIndex) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Project", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(assets) { index, asset ->
                        val isSelected = selectedAssetIndex == index
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(135.dp)
                                .clickable { selectedAssetIndex = index },
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = if (isSelected) Color(0x4D00E5FF) else Color(0x261E293B),
                            borderColor = if (isSelected) NeonCyan else Color(0x33FFFFFF),
                            borderGlow = if (isSelected) NeonCyan else Color.Transparent
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x33FFFFFF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = asset.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) NeonCyan else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = asset.category,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isSelected) NeonCyan else Color(0x88FFFFFF)
                                        )
                                    )
                                }

                                Column {
                                    Text(
                                        text = asset.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${asset.vertices} verts • ${asset.triangles} polys",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xAAFFFFFF),
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Media Preview Dialog
    selectedMediaPreview?.let { mediaItem ->
        Dialog(onDismissRequest = { selectedMediaPreview = null }) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0xEE111827),
                borderColor = NeonCyan
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (mediaItem.isVideo) "Video Recording 🎥" else "Photo Capture 📸",
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            fontSize = 15.sp
                        )
                        IconButton(
                            onClick = { selectedMediaPreview = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    // Preview Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!mediaItem.isVideo) {
                            AsyncImage(
                                model = mediaItem.uri,
                                contentDescription = mediaItem.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(NeonCyan.copy(alpha = 0.8f))
                                        .clickable {
                                            openMediaInExternalApp(context, mediaItem)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Video",
                                        tint = Color.Black,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Text(
                                    text = "Tap to play in Gallery Player",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Metadata details
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = mediaItem.title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${mediaItem.formattedDate} • ${mediaItem.formattedSize}",
                            color = Color(0xAAFFFFFF),
                            fontSize = 11.sp
                        )
                    }

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                shareMedia(context, mediaItem)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.85f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                openMediaInExternalApp(context, mediaItem)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery", color = Color.White, fontSize = 13.sp)
                        }

                        IconButton(
                            onClick = {
                                viewModel.deleteGalleryItem(mediaItem, context)
                                selectedMediaPreview = null
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaItemCard(
    item: SavedMediaItem,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        backgroundColor = Color(0x2E1E293B),
        borderColor = if (item.isVideo) GlowGreen.copy(alpha = 0.5f) else NeonCyan.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.isVideo) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF064E3B), Color(0xFF022C22))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = GlowGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Type Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (item.isVideo) "MP4" else "JPG",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isVideo) GlowGreen else NeonCyan
                    )
                }
            }

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.formattedDate,
                    color = Color(0xAAFFFFFF),
                    fontSize = 11.sp
                )
                Text(
                    text = item.formattedSize,
                    color = if (item.isVideo) GlowGreen else NeonCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Quick Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color(0xAAFF5252),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

fun shareMedia(context: Context, item: SavedMediaItem) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (item.isVideo) "video/mp4" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Spatial Media"))
    } catch (e: Exception) {
        android.util.Log.e("SpatialGallery", "Error sharing media", e)
    }
}

fun openMediaInExternalApp(context: Context, item: SavedMediaItem) {
    try {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, if (item.isVideo) "video/mp4" else "image/jpeg")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(viewIntent)
    } catch (e: Exception) {
        android.util.Log.e("SpatialGallery", "Error opening media in external app", e)
    }
}
