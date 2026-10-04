package com.example.ui

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.data.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Builds an optimized Coil ImageRequest that handles both static photos and video thumbnails.
 */
fun buildMediaImageRequest(
    context: Context,
    imageUrl: String,
    isVideo: Boolean = false,
    frameTimeMs: Long = 1000L
): ImageRequest {
    val builder = ImageRequest.Builder(context)
        .data(imageUrl)
        .crossfade(true)

    if (isVideo) {
        builder.decoderFactory(VideoFrameDecoder.Factory())
        builder.videoFrameMillis(frameTimeMs)
    }

    return builder.build()
}

/**
 * Checks whether a given Photo record represents a video asset.
 */
fun isVideoAsset(photo: Photo): Boolean {
    if (photo.mimeType.startsWith("video/", ignoreCase = true)) return true
    val tags = photo.tags.split(",").map { it.trim().lowercase() }
    if (tags.contains("video")) return true
    val urlLower = photo.imageUrl.lowercase()
    return urlLower.endsWith(".mp4") || urlLower.endsWith(".mkv") || urlLower.endsWith(".webm") ||
            urlLower.endsWith(".mov") || urlLower.endsWith(".3gp") || urlLower.contains("/video/")
}

/**
 * Extracts a fallback video thumbnail Bitmap using Android system APIs if Coil's frame decoder
 * fails or encounters unusual URI formats.
 */
suspend fun extractLocalVideoThumbnail(context: Context, uriString: String): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            if (uriString.startsWith("content://")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        return@withContext context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
                    } catch (e: Exception) {
                        // fallback to retriever
                    }
                }
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val bmp = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0)
                retriever.release()
                return@withContext bmp
            } else if (uriString.startsWith("file://") || uriString.startsWith("/")) {
                val path = if (uriString.startsWith("file://")) uri.path ?: uriString.removePrefix("file://") else uriString
                val file = File(path)
                if (file.exists()) {
                    val bmp = ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
                    if (bmp != null) return@withContext bmp
                }
            } else if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(uriString, HashMap<String, String>())
                val bmp = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0)
                retriever.release()
                return@withContext bmp
            }
            null
        } catch (e: Exception) {
            null
        }
    }

/**
 * Reliable Video Thumbnail Preview placeholder when the thumbnail is loading or unavailable.
 */
@Composable
fun VideoThumbnailPlaceholder(
    modifier: Modifier = Modifier,
    title: String = ""
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "placeholderAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E293B).copy(alpha = alphaAnim),
                        Color(0xFF0F172A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video Asset",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
