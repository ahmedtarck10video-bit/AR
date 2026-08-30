package com.example.engine.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedMediaItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val isVideo: Boolean,
    val timestamp: Long,
    val sizeBytes: Long,
    val formattedSize: String,
    val formattedDate: String,
    val filePath: String? = null
)

object MediaStoreHelper {
    private const val TAG = "MediaStoreHelper"
    private const val ALBUM_DIRECTORY = "SpatialMR"

    /**
     * Saves a captured Bitmap directly to the user's Android Gallery (MediaStore.Images).
     * The photo is stored in Pictures/SpatialMR so it is immediately visible in Google Photos/Gallery.
     */
    suspend fun saveImageToGallery(
        context: Context,
        bitmap: Bitmap,
        titlePrefix: String = "Spatial_Photo"
    ): SavedMediaItem? = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val dateString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))
        val filename = "${titlePrefix}_$dateString.jpg"
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_DIRECTORY")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val imageUri = try {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting image to MediaStore", e)
            null
        } ?: return@withContext null

        try {
            resolver.openOutputStream(imageUri)?.use { outStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 96, outStream)
                outStream.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }

            val id = ContentUris.parseId(imageUri)
            val formattedDate = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(timestamp))
            val approxSize = (bitmap.byteCount / 4).toLong() // approximate JPEG file size
            val sizeStr = formatFileSize(approxSize)

            Log.i(TAG, "Successfully saved photo to Gallery: $imageUri")
            SavedMediaItem(
                id = id,
                uri = imageUri,
                title = filename,
                isVideo = false,
                timestamp = timestamp,
                sizeBytes = approxSize,
                formattedSize = sizeStr,
                formattedDate = formattedDate
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing image stream to MediaStore", e)
            try {
                resolver.delete(imageUri, null, null)
            } catch (_: Exception) {}
            null
        }
    }

    /**
     * Copies and registers a recorded MP4 video file directly into the device Gallery (MediaStore.Video).
     * Stored in Movies/SpatialMR so it is immediately playable from device Gallery apps.
     */
    suspend fun saveVideoToGallery(
        context: Context,
        videoFile: File,
        titlePrefix: String = "Spatial_AR_Record"
    ): SavedMediaItem? = withContext(Dispatchers.IO) {
        if (!videoFile.exists() || videoFile.length() == 0L) {
            Log.w(TAG, "Video file does not exist or is empty: ${videoFile.absolutePath}")
            return@withContext null
        }

        val timestamp = System.currentTimeMillis()
        val dateString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))
        val filename = "${titlePrefix}_$dateString.mp4"
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, timestamp / 1000)
            put(MediaStore.Video.Media.DATE_TAKEN, timestamp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$ALBUM_DIRECTORY")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val videoUri = try {
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting video to MediaStore", e)
            null
        } ?: return@withContext null

        try {
            resolver.openOutputStream(videoUri)?.use { outStream ->
                videoFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
                outStream.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(videoUri, contentValues, null, null)
            }

            val id = ContentUris.parseId(videoUri)
            val formattedDate = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(timestamp))
            val fileSize = videoFile.length()
            val sizeStr = formatFileSize(fileSize)

            Log.i(TAG, "Successfully saved video recording to Gallery: $videoUri")
            SavedMediaItem(
                id = id,
                uri = videoUri,
                title = filename,
                isVideo = true,
                timestamp = timestamp,
                sizeBytes = fileSize,
                formattedSize = sizeStr,
                formattedDate = formattedDate,
                filePath = videoFile.absolutePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing video stream to MediaStore", e)
            try {
                resolver.delete(videoUri, null, null)
            } catch (_: Exception) {}
            null
        }
    }

    /**
     * Loads all photos and videos saved in the Gallery album as well as internal cache captures.
     */
    suspend fun loadGalleryCaptures(context: Context): List<SavedMediaItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<SavedMediaItem>()
        val resolver = context.contentResolver

        // 1. Query Images from MediaStore
        try {
            val imgProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE
            )
            val imgSelection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            val imgArgs = arrayOf("Spatial_%")
            val imgSort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imgProjection,
                imgSelection,
                imgArgs,
                imgSort
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Spatial Photo"
                    val dateAddedSec = cursor.getLong(dateCol)
                    val sizeBytes = cursor.getLong(sizeCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val timestamp = if (dateAddedSec > 0) dateAddedSec * 1000 else System.currentTimeMillis()

                    mediaList.add(
                        SavedMediaItem(
                            id = id,
                            uri = uri,
                            title = name,
                            isVideo = false,
                            timestamp = timestamp,
                            sizeBytes = sizeBytes,
                            formattedSize = formatFileSize(sizeBytes),
                            formattedDate = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(timestamp))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading images from MediaStore", e)
        }

        // 2. Query Videos from MediaStore
        try {
            val vidProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.SIZE
            )
            val vidSelection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
            val vidArgs = arrayOf("Spatial_%")
            val vidSort = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                vidProjection,
                vidSelection,
                vidArgs,
                vidSort
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Spatial Video"
                    val dateAddedSec = cursor.getLong(dateCol)
                    val sizeBytes = cursor.getLong(sizeCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val timestamp = if (dateAddedSec > 0) dateAddedSec * 1000 else System.currentTimeMillis()

                    mediaList.add(
                        SavedMediaItem(
                            id = id,
                            uri = uri,
                            title = name,
                            isVideo = true,
                            timestamp = timestamp,
                            sizeBytes = sizeBytes,
                            formattedSize = formatFileSize(sizeBytes),
                            formattedDate = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(timestamp))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading videos from MediaStore", e)
        }

        // 3. Fallback: Also check app cache directory for local AR recordings
        try {
            val cacheFiles = context.cacheDir.listFiles { _, name ->
                (name.startsWith("ar_session_") || name.startsWith("Spatial_")) && (name.endsWith(".mp4") || name.endsWith(".jpg"))
            }
            if (cacheFiles != null) {
                for (file in cacheFiles) {
                    val isVid = file.name.endsWith(".mp4")
                    val uri = Uri.fromFile(file)
                    val alreadyInList = mediaList.any { it.title == file.name }
                    if (!alreadyInList && file.length() > 0) {
                        mediaList.add(
                            SavedMediaItem(
                                id = file.name.hashCode().toLong(),
                                uri = uri,
                                title = file.name,
                                isVideo = isVid,
                                timestamp = file.lastModified(),
                                sizeBytes = file.length(),
                                formattedSize = formatFileSize(file.length()),
                                formattedDate = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(file.lastModified())),
                                filePath = file.absolutePath
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking cache files", e)
        }

        mediaList.sortedByDescending { it.timestamp }
    }

    /**
     * Deletes a media item from MediaStore / local storage.
     */
    suspend fun deleteMediaItem(context: Context, item: SavedMediaItem): Boolean = withContext(Dispatchers.IO) {
        var deleted = false
        try {
            if (item.uri.scheme == "content") {
                val rows = context.contentResolver.delete(item.uri, null, null)
                deleted = rows > 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting MediaStore URI ${item.uri}", e)
        }

        try {
            item.filePath?.let { path ->
                val f = File(path)
                if (f.exists()) {
                    f.delete()
                    deleted = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting local file", e)
        }
        deleted
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", kb)
        }
    }
}
