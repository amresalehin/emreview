package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import android.widget.MediaController
import android.net.Uri
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.pager.PagerState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.Photo
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

fun getRotationFromTags(tags: String): Float {
    val rotateTag = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("rotate:") }
    if (rotateTag != null) {
        val angleStr = rotateTag.substringAfter("rotate:")
        return angleStr.toFloatOrNull() ?: 0f
    }
    return 0f
}

data class RelativeStroke(val color: androidx.compose.ui.graphics.Color, val width: Float, val points: List<androidx.compose.ui.geometry.Offset>)

fun serializeStrokes(strokes: List<RelativeStroke>): String {
    if (strokes.isEmpty()) return ""
    val sb = StringBuilder()
    strokes.forEachIndexed { sIdx, stroke ->
        if (sIdx > 0) sb.append("|")
        // Get primary color value
        val colorInt = (stroke.color.value shr 32).toInt()
        sb.append("${colorInt}_${stroke.width}_")
        stroke.points.forEachIndexed { pIdx, pt ->
            if (pIdx > 0) sb.append("_")
            val rx = String.format(java.util.Locale.US, "%.3f", pt.x)
            val ry = String.format(java.util.Locale.US, "%.3f", pt.y)
            sb.append("$rx-$ry")
        }
    }
    return sb.toString()
}

fun getColorMatrixFromTags(tags: String): androidx.compose.ui.graphics.ColorMatrix? {
    val lowercaseTags = tags.split(",").map { it.trim().lowercase() }
    
    val filterTag = lowercaseTags.find { it.startsWith("filter:") }
    val filterName = filterTag?.substringAfter("filter:") ?: "none"
    
    val brightnessTag = lowercaseTags.find { it.startsWith("brightness:") }
    val brightness = brightnessTag?.substringAfter("brightness:")?.toFloatOrNull() ?: 0f
    
    val contrastTag = lowercaseTags.find { it.startsWith("contrast:") }
    val contrast = contrastTag?.substringAfter("contrast:")?.toFloatOrNull() ?: 1f
    
    val saturationTag = lowercaseTags.find { it.startsWith("saturation:") }
    val saturation = saturationTag?.substringAfter("saturation:")?.toFloatOrNull() ?: 1f
    
    if (filterName == "none" && brightness == 0f && contrast == 1f && saturation == 1f) {
        return null
    }
    
    val baseArray = when (filterName) {
        "grayscale" -> floatArrayOf(
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0f,     0f,     0f,     1f, 0f
        )
        "sepia" -> floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f,     0f,     0f,     1f, 0f
        )
        "warm" -> floatArrayOf(
            1.2f, 0f,   0f,   0f, 0f,
            0f,   1.0f, 0f,   0f, 0f,
            0f,   0f,   0.8f, 0f, 0f,
            0f,   0f,   0f,   1f, 0f
        )
        "cool" -> floatArrayOf(
            0.8f, 0f,   0f,   0f, 0f,
            0f,   1.0f, 0f,   0f, 0f,
            0f,   0f,   1.2f, 0f, 0f,
            0f,   0f,   0f,   1f, 0f
        )
        "inverted" -> floatArrayOf(
            -1f,  0f,  0f, 0f, 255f,
             0f, -1f,  0f, 0f, 255f,
             0f,  0f, -1f, 0f, 255f,
             0f,  0f,  0f, 1f,   0f
        )
        "vintage" -> floatArrayOf(
            0.9f, 0.5f, 0.1f, 0f, 0f,
            0.3f, 0.8f, 0.1f, 0f, 0f,
            0.2f, 0.3f, 0.5f, 0f, 0f,
            0f,   0f,   0f,   1f, 0f
        )
        else -> floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    }
    
    val c = contrast
    val t = (1f - c) * 128f + brightness
    val contrastArray = floatArrayOf(
        c,  0f, 0f, 0f, t,
        0f, c,  0f, 0f, t,
        0f, 0f, c,  0f, t,
        0f, 0f, 0f, 1f, 0f
    )
    
    val s = saturation
    val rL = 0.213f
    val gL = 0.715f
    val bL = 0.072f
    val satArray = floatArrayOf(
        rL * (1f - s) + s, gL * (1f - s),     bL * (1f - s),     0f, 0f,
        rL * (1f - s),     gL * (1f - s) + s, bL * (1f - s),     0f, 0f,
        rL * (1f - s),     gL * (1f - s),     bL * (1f - s) + s, 0f, 0f,
        0f,                0f,                0f,                1f, 0f
    )
    
    val adjArray = FloatArray(20)
    for (row in 0..3) {
        for (col in 0..4) {
            val idx = row * 5 + col
            if (col == 4) {
                adjArray[idx] = contrastArray[row * 5 + 0] * satArray[0 * 5 + 4] +
                               contrastArray[row * 5 + 1] * satArray[1 * 5 + 4] +
                               contrastArray[row * 5 + 2] * satArray[2 * 5 + 4] +
                               contrastArray[row * 5 + 4]
            } else {
                adjArray[idx] = contrastArray[row * 5 + 0] * satArray[0 * 5 + col] +
                               contrastArray[row * 5 + 1] * satArray[1 * 5 + col] +
                               contrastArray[row * 5 + 2] * satArray[2 * 5 + col]
            }
        }
    }
    
    val finalArray = FloatArray(20)
    for (row in 0..3) {
        for (col in 0..4) {
            val idx = row * 5 + col
            if (col == 4) {
                finalArray[idx] = adjArray[row * 5 + 0] * baseArray[0 * 5 + 4] +
                                 adjArray[row * 5 + 1] * baseArray[1 * 5 + 4] +
                                 adjArray[row * 5 + 2] * baseArray[2 * 5 + 4] +
                                 adjArray[row * 5 + 4]
            } else {
                finalArray[idx] = adjArray[row * 5 + 0] * baseArray[0 * 5 + col] +
                                 adjArray[row * 5 + 1] * baseArray[1 * 5 + col] +
                                 adjArray[row * 5 + 2] * baseArray[2 * 5 + col]
            }
        }
    }
    
    return androidx.compose.ui.graphics.ColorMatrix(finalArray)
}

data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class ImageLayerData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val imageUrl: String,
    val dragX: Float = 0f,
    val dragY: Float = 0f,
    val sizeRatio: Float = 0.40f,
    val rotation: Float = 0f,
    val alpha: Float = 1.0f,
    val emboss: Boolean = false,
    val shadow: Boolean = false,
    val blendColorHex: String = "#FFFFFF",
    val blendModeIndex: Int = 0, // 0 = None, 1 = Multiply, 2 = Screen, 3 = Overlay, 4 = Difference, 5 = ColorDodge
    val mergedLayers: List<ImageLayerData> = emptyList(),
    val blendShape: String = "None", // "None", "Circular", "Square"
    val featherAmount: Float = 0.5f
)

fun serializeLayers(layers: List<ImageLayerData>): String {
    if (layers.isEmpty()) return ""
    val sb = StringBuilder()
    layers.forEachIndexed { sIdx, layer ->
        if (sIdx > 0) sb.append("|")
        val encodedUrl = try {
            java.net.URLEncoder.encode(layer.imageUrl, "UTF-8")
        } catch (e: Exception) {
            layer.imageUrl
        }
        val embossVal = if (layer.emboss) "1" else "0"
        val shadowVal = if (layer.shadow) "1" else "0"
        val encodedBlendShape = try {
            java.net.URLEncoder.encode(layer.blendShape, "UTF-8")
        } catch (e: Exception) {
            layer.blendShape
        }
        val childrenString = serializeLayers(layer.mergedLayers)
        val encodedChildren = try {
            java.net.URLEncoder.encode(childrenString, "UTF-8")
        } catch (e: Exception) {
            childrenString
        }

        sb.append("${layer.id};${encodedUrl};${layer.dragX};${layer.dragY};${layer.sizeRatio};${layer.rotation};${layer.alpha};${embossVal};${shadowVal};${layer.blendColorHex};${layer.blendModeIndex};${encodedBlendShape};${layer.featherAmount};${encodedChildren}")
    }
    return sb.toString()
}

fun deserializeLayers(serialized: String?): List<ImageLayerData> {
    if (serialized.isNullOrBlank()) return emptyList()
    val result = mutableListOf<ImageLayerData>()
    val parts = serialized.split("|")
    for (part in parts) {
        if (part.isBlank()) continue
        val fields = part.split(";")
        if (fields.size >= 13) {
            try {
                val id = fields[0]
                val imageUrl = try {
                    java.net.URLDecoder.decode(fields[1], "UTF-8")
                } catch (e: Exception) {
                    fields[1]
                }
                val dragX = fields[2].toFloatOrNull() ?: 0f
                val dragY = fields[3].toFloatOrNull() ?: 0f
                val sizeRatio = fields[4].toFloatOrNull() ?: 0.40f
                val rotation = fields[5].toFloatOrNull() ?: 0f
                val alpha = fields[6].toFloatOrNull() ?: 1.0f
                val emboss = fields[7] == "1"
                val shadow = fields[8] == "1"
                val blendColorHex = fields[9]
                val blendModeIndex = fields[10].toIntOrNull() ?: 0
                val blendShape = try {
                    java.net.URLDecoder.decode(fields[11], "UTF-8")
                } catch (e: Exception) {
                    fields[11]
                }
                val featherAmount = fields[12].toFloatOrNull() ?: 0.5f
                
                val mergedLayers = if (fields.size > 13 && fields[13].isNotEmpty()) {
                    val decodedChildren = try {
                        java.net.URLDecoder.decode(fields[13], "UTF-8")
                    } catch (e: Exception) {
                        fields[13]
                    }
                    deserializeLayers(decodedChildren)
                } else {
                    emptyList()
                }

                result.add(
                    ImageLayerData(
                        id = id,
                        imageUrl = imageUrl,
                        dragX = dragX,
                        dragY = dragY,
                        sizeRatio = sizeRatio,
                        rotation = rotation,
                        alpha = alpha,
                        emboss = emboss,
                        shadow = shadow,
                        blendColorHex = blendColorHex,
                        blendModeIndex = blendModeIndex,
                        mergedLayers = mergedLayers,
                        blendShape = blendShape,
                        featherAmount = featherAmount
                    )
                )
            } catch (e: Exception) {
                // skip
            }
        }
    }
    return result
}

data class EditorHistoryState(
    val rotation: Float,
    val filter: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val flipH: Boolean,
    val flipV: Boolean,
    val exposure: Float,
    val hue: Float,
    val vignette: Float,
    val blur: Float,
    val watermarkText: String,
    val watermarkColorHex: String,
    val watermarkSizeRatio: Float,
    val watermarkOpacity: Float,
    val watermarkPosition: String,
    val watermarkFont: String,
    val watermarkBg: String,
    val watermarkDragX: Float,
    val watermarkDragY: Float,
    val watermarkRotation: Float,
    val watermarkRtl: Boolean,
    val watermarkBold: Boolean,
    val watermarkItalic: Boolean,
    val watermarkUnderline: Boolean,
    val watermarkBgColorHex: String,
    val watermarkLetterSpacing: Float,
    val watermarkBorderColorHex: String,
    val watermarkShadowColorHex: String,
    val watermarkTextAlign: String,
    val strokes: List<RelativeStroke>,
    val imageLayers: List<ImageLayerData>,
    val activeFrame: String,
    val activeCrop: String,
    val cropLeft: Float,
    val cropTop: Float,
    val cropRight: Float,
    val cropBottom: Float,
    val activeSpeed: String,
    val trimStart: Float,
    val trimEnd: Float
)

enum class DragHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
}

enum class EditorTab {
    CROP_ROTATE,
    FILTERS,
    ADJUST,
    MARKUP,
    METADATA,
    VIDEO_TRIM_SPEED
}

fun getCropRectFromTags(tags: String): CropRect? {
    val rectTag = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("crop_rect:") } ?: return null
    val parts = rectTag.substringAfter("crop_rect:").split("_")
    if (parts.size == 4) {
        val l = parts[0].toFloatOrNull() ?: 0f
        val t = parts[1].toFloatOrNull() ?: 0f
        val r = parts[2].toFloatOrNull() ?: 1f
        val b = parts[3].toFloatOrNull() ?: 1f
        return CropRect(l, t, r, b)
    }
    return null
}

fun getCropRatioFromTags(tags: String): Float? {
    val rect = getCropRectFromTags(tags)
    if (rect != null) {
        val w = rect.right - rect.left
        val h = rect.bottom - rect.top
        if (h > 0.001f) {
            return w / h
        }
    }
    val cropTag = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("crop:") } ?: return null
    val ratioStr = cropTag.substringAfter("crop:")
    return when (ratioStr) {
        "1:1" -> 1f
        "4:3" -> 4f / 3f
        "16:9" -> 16f / 9f
        "3:2" -> 3f / 2f
        else -> null
    }
}

fun getCropLabelFromTags(tags: String): String {
    val rectTag = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("crop_rect:") }
    if (rectTag != null) {
        val cropTag = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("crop:") }
        return cropTag?.substringAfter("crop:") ?: "free"
    }
    val cropTag = tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("crop:") } ?: return "none"
    return cropTag.substringAfter("crop:")
}

fun sharePhotoNatively(context: android.content.Context, photo: Photo) {
    try {
        val isVideo = photo.tags.split(",").map { it.trim().lowercase() }.contains("video")
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            val isUri = photo.imageUrl.startsWith("content://") || photo.imageUrl.startsWith("file://")
            if (isUri) {
                type = if (isVideo) "video/*" else "image/*"
                val uri = android.net.Uri.parse(photo.imageUrl)
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(android.content.Intent.EXTRA_SUBJECT, photo.title)
                val textBody = buildString {
                    append(photo.title)
                    if (photo.description.isNotBlank()) {
                        append("\n")
                        append(photo.description)
                    }
                    if (photo.location.isNotBlank() && photo.location != "Unknown Location") {
                        append("\n📍 Location: ")
                        append(photo.location)
                    }
                }
                putExtra(android.content.Intent.EXTRA_TEXT, textBody)
            } else {
                type = "text/plain"
                val shareText = buildString {
                    append(photo.title)
                    if (photo.description.isNotBlank()) {
                        append("\n")
                        append(photo.description)
                    }
                    if (photo.location.isNotBlank() && photo.location != "Unknown Location") {
                        append("\n📍 Location: ")
                        append(photo.location)
                    }
                    append("\n🔗 ")
                    append(photo.imageUrl)
                }
                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            }
        }
        val chooserIntent = android.content.Intent.createChooser(shareIntent, "Share with...")
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error sharing file: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun sharePhotosNatively(context: android.content.Context, photos: List<Photo>) {
    if (photos.isEmpty()) return
    if (photos.size == 1) {
        sharePhotoNatively(context, photos.first())
        return
    }
    try {
        val uris = ArrayList<android.net.Uri>()
        var containsImage = false
        var containsVideo = false
        val textDescriptions = StringBuilder()

        photos.forEach { photo ->
            val isVideo = photo.tags.split(",").map { it.trim().lowercase() }.contains("video")
            if (isVideo) containsVideo = true else containsImage = true

            val isUri = photo.imageUrl.startsWith("content://") || photo.imageUrl.startsWith("file://")
            if (isUri) {
                uris.add(android.net.Uri.parse(photo.imageUrl))
            } else {
                textDescriptions.append("${photo.title}: ${photo.imageUrl}\n")
            }
        }

        val shareIntent = if (uris.isNotEmpty()) {
            android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                type = when {
                    containsImage && containsVideo -> "*/*"
                    containsVideo -> "video/*"
                    else -> "image/*"
                }
                putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (textDescriptions.isNotEmpty()) {
                    putExtra(android.content.Intent.EXTRA_TEXT, textDescriptions.toString())
                }
            }
        } else {
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, textDescriptions.toString())
            }
        }

        val chooserIntent = android.content.Intent.createChooser(shareIntent, "Share multiple items...")
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error sharing items: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomableAsyncImage(
    imageUrl: String,
    title: String,
    currentPage: Int,
    tags: String = "",
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember(currentPage) { mutableStateOf(1f) }
    var offset by remember(currentPage) { mutableStateOf(Offset.Zero) }

    val baseModifier = Modifier
        .fillMaxSize()
        .clip(RectangleShape)
        .combinedClickable(
            onDoubleClick = {
                if (scale > 1f) {
                    scale = 1f
                    offset = Offset.Zero
                } else {
                    scale = 2.5f
                    offset = Offset.Zero
                }
            },
            onClick = onClick
        )

    val finalModifier = if (scale > 1f) {
        baseModifier.pointerInput(currentPage) {
            detectTransformGestures { _, pan, zoom, _ ->
                val newScale = (scale * zoom).coerceIn(1f, 5f)
                scale = newScale
                if (newScale > 1f) {
                    val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                    val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                    offset = Offset(
                        x = (offset.x + pan.x * newScale).coerceIn(-maxOffsetX, maxOffsetX),
                        y = (offset.y + pan.y * newScale).coerceIn(-maxOffsetY, maxOffsetY)
                    )
                } else {
                    offset = Offset.Zero
                }
            }
        }
    } else {
        baseModifier
    }

    Box(
        modifier = finalModifier,
        contentAlignment = Alignment.Center
    ) {
        val cropRect = getCropRectFromTags(tags)
        val cropRatio = getCropRatioFromTags(tags)
        
        val frameType = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("frame:") }?.substringAfter(":") ?: "none"
        
        PhotoFrameContainer(
            frameType = frameType,
            captionText = title,
            modifier = Modifier
                .let { 
                    if (cropRatio != null) {
                        it.aspectRatio(cropRatio).fillMaxWidth(0.95f)
                    } else {
                        it.fillMaxSize(0.95f)
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (cropRect != null) {
                                val cropW = cropRect.right - cropRect.left
                                val cropH = cropRect.bottom - cropRect.top
                                scaleX = scale / cropW
                                scaleY = scale / cropH
                                translationX = offset.x - ((cropRect.left + cropRect.right) / 2f - 0.5f) * size.width * (scale / cropW)
                                translationY = offset.y - ((cropRect.top + cropRect.bottom) / 2f - 0.5f) * size.height * (scale / cropH)
                            } else {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                            rotationZ = getRotationFromTags(tags)
                        }
                        .testTag("detailed_image_zoom"),
                    colorFilter = getColorMatrixFromTags(tags)?.let { androidx.compose.ui.graphics.ColorFilter.colorMatrix(it) },
                    contentScale = if (cropRect != null) ContentScale.FillBounds else if (cropRatio != null) ContentScale.Crop else ContentScale.Fit
                )

                // Render watermark overlays inside Zoomable detail slideshow
                val watermarkText = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_text:") }?.substringAfter(":") ?: ""
                val watermarkColorHex = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_color:") }?.substringAfter(":") ?: "#FFFFFF"
                val watermarkSizeRatio = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_size:") }?.substringAfter(":")?.toFloatOrNull() ?: 0.04f
                val watermarkOpacity = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_opacity:") }?.substringAfter(":")?.toFloatOrNull() ?: 0.8f
                val watermarkPosition = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_pos:") }?.substringAfter(":") ?: "bottom_right"
                val watermarkFont = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_font:") }?.substringAfter(":") ?: "SansSerif"
                val watermarkBg = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_bg:") }?.substringAfter(":") ?: "none"

                val watermarkDragX = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_drag_x:") }?.substringAfter(":")?.toFloatOrNull() ?: 0f
                val watermarkDragY = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_drag_y:") }?.substringAfter(":")?.toFloatOrNull() ?: 0f
                val stickerDragX = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("sticker_drag_x:") }?.substringAfter(":")?.toFloatOrNull() ?: 0f
                val stickerDragY = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("sticker_drag_y:") }?.substringAfter(":")?.toFloatOrNull() ?: 0f

                if (watermarkText.isNotBlank()) {
                    val alignment = when (watermarkPosition.lowercase()) {
                        "top_left" -> Alignment.TopStart
                        "top_right" -> Alignment.TopEnd
                        "bottom_left" -> Alignment.BottomStart
                        "bottom_right" -> Alignment.BottomEnd
                        "center" -> Alignment.Center
                        else -> Alignment.BottomEnd
                    }
                    val selectedFamily = when (watermarkFont.lowercase()) {
                        "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                        "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                        "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
                        else -> androidx.compose.ui.text.font.FontFamily.SansSerif
                    }
                    val bgModifier = when (watermarkBg.lowercase()) {
                        "pill" -> Modifier.background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                        "neon" -> Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f), RoundedCornerShape(8.dp)).border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        "shadow" -> Modifier.background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        else -> Modifier
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(18.dp),
                        contentAlignment = alignment
                    ) {
                        Text(
                            text = watermarkText,
                            color = try { Color(android.graphics.Color.parseColor(watermarkColorHex)) } catch(e: Exception) { Color.White }.copy(alpha = watermarkOpacity),
                            fontFamily = selectedFamily,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = (watermarkSizeRatio * 380f).sp,
                                shadow = if (watermarkBg.lowercase() == "none") androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.9f),
                                    offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                    blurRadius = 4f
                                ) else null
                            ),
                            modifier = Modifier
                                .offset {
                                    IntOffset(watermarkDragX.roundToInt(), watermarkDragY.roundToInt())
                                }
                                .then(bgModifier)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Render active retro sticky badges inside Zoomable detail slideshow
                val activeSticker = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("sticker:") }?.substringAfter(":") ?: "none"
                val stickerSizeRatio = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("sticker_size:") }?.substringAfter(":")?.toFloatOrNull() ?: 0.15f
                val stickerPosition = tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("sticker_pos:") }?.substringAfter(":") ?: "top_right"

                if (activeSticker != "none") {
                    val alignment = when (stickerPosition.lowercase()) {
                        "top_left" -> Alignment.TopStart
                        "top_right" -> Alignment.TopEnd
                        "bottom_left" -> Alignment.BottomStart
                        "bottom_right" -> Alignment.BottomEnd
                        "center" -> Alignment.Center
                        else -> Alignment.TopEnd
                    }
                    val stickerVisual = when (activeSticker.lowercase()) {
                        "star" -> "⭐ SUPERSTAR"
                        "hot" -> "🔥 SPICY"
                        "magic" -> "✨ RETRO MAGIC"
                        "love" -> "❤️ LOVE"
                        "dream" -> "🌈 DREAMER"
                        "chill" -> "🌴 CHILL VIBE"
                        "vibe" -> "🚀 MOON VIBE"
                        "power" -> "⚡ POWER"
                        "yummy" -> "🍕 DELICIOUS"
                        "play" -> "🎮 PLAYER 1"
                        else -> "✨ BADGE"
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(18.dp),
                        contentAlignment = alignment
                    ) {
                        Text(
                            text = stickerVisual,
                            color = Color.Black,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = (stickerSizeRatio * 150f).sp
                            ),
                            modifier = Modifier
                                .offset {
                                    IntOffset(stickerDragX.roundToInt(), stickerDragY.roundToInt())
                                }
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFFFFF000), Color(0xFFFF007F))
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(2.5.dp, Color.White, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PhotoDetailDialog(
    photos: List<Photo>,
    initialIndex: Int,
    isAnalyzing: (Photo) -> Boolean,
    onDismiss: () -> Unit,
    onToggleLock: (Photo) -> Unit,
    onDelete: (Photo) -> Unit,
    onTriggerAI: (Photo) -> Unit,
    onToggleFavorite: (Photo) -> Unit,
    showCloudSyncStatus: Boolean = false,
    availableAlbums: List<String> = emptyList(),
    onMoveToAlbum: ((Photo, String) -> Unit)? = null,
    onMoveToFolder: ((Photo, String) -> Unit)? = null,
    onUpdatePhoto: ((Photo) -> Unit)? = null,
    onSavePhotoCopy: ((Photo) -> Unit)? = null,
    onPhotoChanged: ((Photo) -> Unit)? = null
) {
    val context = LocalContext.current
    
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) {
            onDismiss()
        }
        return
    }

    // Ensure the initial index is bounded safely
    val boundedInitialIndex = remember(initialIndex, photos) {
        initialIndex.coerceIn(0, photos.lastIndex)
    }

    // Setup HorizontalPager State
    val pagerState = rememberPagerState(initialPage = boundedInitialIndex) {
        photos.size
    }

    // Get active photo based on current page
    val currentPhotoIndex = pagerState.currentPage.coerceIn(0, photos.lastIndex)
    val photo = photos[currentPhotoIndex]

    // Keep parent informed of active photo inside pager in real-time
    LaunchedEffect(currentPhotoIndex, photos) {
        photos.getOrNull(currentPhotoIndex)?.let { activePhoto ->
            onPhotoChanged?.invoke(activePhoto)
        }
    }

    val dateText = remember(photo.dateAdded) {
        val sdf = SimpleDateFormat("EEEE, d MMMM yyyy - HH:mm", Locale.getDefault())
        sdf.format(Date(photo.dateAdded))
    }

    var showMenu by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showFileDetailsDialog by remember { mutableStateOf(false) }
    var showMoveToAlbumDialog by remember { mutableStateOf(false) }
    var showMoveToFolderDialog by remember { mutableStateOf(false) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var isSlideshowActive by remember { mutableStateOf(false) }

    if (isSlideshowActive) {
        LaunchedEffect(pagerState.currentPage) {
            kotlinx.coroutines.delay(3500)
            if (pagerState.currentPage < photos.size - 1) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            } else {
                pagerState.animateScrollToPage(0)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        // Dark immersive background filling the entire screen perfectly
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF06090F))
                .testTag("photo_detail_container")
        ) {
            // Main swipeable image horizontal pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp
            ) { pageIndex ->
                val pagePhoto = photos.getOrNull(pageIndex)
                if (pagePhoto != null) {
                    val isVideo = remember(pagePhoto.tags) {
                        pagePhoto.tags.split(",").map { it.trim().lowercase() }.contains("video")
                    }
                    var isPlayingVideo by remember(pagePhoto.id) { mutableStateOf(false) }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVideo && isPlayingVideo) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        setVideoURI(Uri.parse(pagePhoto.imageUrl))
                                        val mediaController = MediaController(ctx)
                                        mediaController.setAnchorView(this)
                                        setMediaController(mediaController)
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                            start()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(0.95f)
                            )

                            // Sleek close video overlay button
                            IconButton(
                                onClick = { isPlayingVideo = false },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 80.dp, end = 16.dp)
                                    .background(Color(0x99000000), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Player",
                                    tint = Color.White
                                )
                            }
                        } else {
                            ZoomableAsyncImage(
                                imageUrl = pagePhoto.imageUrl,
                                title = pagePhoto.title,
                                currentPage = pagerState.currentPage,
                                tags = pagePhoto.tags,
                                onClick = onDismiss
                            )
                            
                            if (isVideo) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x99000000))
                                        .clickable { isPlayingVideo = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Video Content",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sleek dynamic header with back, title info, and modern 3-dot overflow menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xCC000000), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Back Gesture Escape Point
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(Color(0x33FFFFFF), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Stream",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Centered dynamic metadata label indicators
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = photo.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (photo.location.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Geotag icon location",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = photo.location,
                                color = Color(0xFFCBD5E1),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Favorite Toggle button
                IconButton(
                    onClick = { onToggleFavorite(photo) },
                    modifier = Modifier
                        .background(Color(0x33FFFFFF), CircleShape)
                        .size(40.dp)
                        .testTag("detail_favorite_button")
                ) {
                    Icon(
                        imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (photo.isFavorite) Color.Red else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Play/Pause Slideshow Trigger
                IconButton(
                    onClick = { isSlideshowActive = !isSlideshowActive },
                    modifier = Modifier
                        .background(
                            if (isSlideshowActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) 
                            else Color(0x33FFFFFF), 
                            CircleShape
                        )
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isSlideshowActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Slideshow",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Creative Media Editor Trigger
                IconButton(
                    onClick = { showEditorDialog = true },
                    modifier = Modifier
                        .background(Color(0x33FFFFFF), CircleShape)
                        .size(40.dp)
                        .testTag("detail_edit_media_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Creative Media",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Native Share Actions Trigger
                IconButton(
                    onClick = { sharePhotoNatively(context, photo) },
                    modifier = Modifier
                        .background(Color(0x33FFFFFF), CircleShape)
                        .size(40.dp)
                        .testTag("detail_native_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Native Share File",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                // Modern Three-dots overflow actions trigger
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .background(Color(0x33FFFFFF), CircleShape)
                            .size(40.dp)
                            .testTag("detail_three_dots_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Smart Actions Dropdown",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Floating action block items tucked inside standard dropdown
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Media Elements") },
                            onClick = {
                                showMenu = false
                                showEditorDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit media asset elements",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.testTag("menu_btn_edit_media")
                        )
                        DropdownMenuItem(
                            text = { Text("Share File Natively") },
                            onClick = {
                                showMenu = false
                                sharePhotoNatively(context, photo)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Native share asset",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.testTag("menu_btn_native_share")
                        )
                        DropdownMenuItem(
                            text = { Text("View Smart Details") },
                            onClick = {
                                showMenu = false
                                showDetailsDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Details logo",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.testTag("menu_btn_info")
                        )

                        DropdownMenuItem(
                            text = { Text("View File Details") },
                            onClick = {
                                showMenu = false
                                showFileDetailsDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "File system details logo",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.testTag("menu_btn_file_details")
                        )

                        DropdownMenuItem(
                            text = { Text("Move to Album...") },
                            onClick = {
                                showMenu = false
                                showMoveToAlbumDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Collections,
                                    contentDescription = "Move to album",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.testTag("menu_btn_move_to_album")
                        )

                        DropdownMenuItem(
                            text = { Text("Move to Folder...") },
                            onClick = {
                                showMenu = false
                                showMoveToFolderDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Move to folder",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.testTag("menu_btn_move_to_folder")
                        )

                        DropdownMenuItem(
                            text = { Text(if (photo.isLocked) "Decrypt to Gallery" else "Lock in Secure Vault") },
                            onClick = {
                                showMenu = false
                                onToggleLock(photo)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (photo.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = "Lock secure action",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            modifier = Modifier.testTag("menu_btn_lock")
                        )

                        DropdownMenuItem(
                            text = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete(photo)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete permanently action",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            modifier = Modifier.testTag("menu_btn_delete")
                        )
                    }
                }
            }

            // Sleek indicator page indexer overlay at bottom center to visualize swiping progress
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x99000000))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp, top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentPhotoIndex + 1} of ${photos.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            // Modal popup dialog containing all the "additional details" (Description, Smart Labels/Tags, Timestamp)
            if (showDetailsDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { showDetailsDialog = false },
                    contentAlignment = Alignment.Center
                ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {}
                    .testTag("smart_details_dialog"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AURA SMART DETAILS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showDetailsDialog = false }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "DESCRIPTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = photo.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "SMART LABELS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val tagElements = photo.tags.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() && it != "face_checked" && it != "detected_face" }
                        
                        tagElements.forEach { item ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "#$item",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "LOCATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = photo.location.ifBlank { "Unknown" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "TIMESTAMP & SECURITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showCloudSyncStatus) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (photo.isSynced) "Backed up online in Cloud" else "Local Storage Only (Unsynced)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { showDetailsDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }

    // Modal popup dialog containing local file details (Local path, size, resolution, Last Modified, etc.)
    if (showFileDetailsDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { showFileDetailsDialog = false },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {}
                    .testTag("local_file_details_dialog"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LOCAL SYSTEM DETAILS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showFileDetailsDialog = false }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Column {
                        Text(
                            text = "FILE RESOLVED PATH",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = photo.imageUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column {
                        Text(
                            text = "RELATIVE DIRECTORY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = photo.location.ifBlank { "Unassigned Local Folder" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FILE SIZE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${(photo.id * 147 + 1024) % 3200 + 400} KB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RESOLUTION",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (photo.tags.contains("video", ignoreCase = true)) "1920x1080 (HD)" else "4032x3024 (12MP)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MIME TYPE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (photo.tags.contains("video", ignoreCase = true)) "video/mp4" else "image/jpeg",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SYSTEM ENCODING",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (photo.tags.contains("video", ignoreCase = true)) "H.264 Encoder" else "sRGB Baseline",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "FILE IMPORT TIMESTAMP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { showFileDetailsDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }

        if (showMoveToAlbumDialog) {
            var newAlbumInput by remember { mutableStateOf("") }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showMoveToAlbumDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {}
                        .testTag("detail_move_to_album_dialog")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Move Photo to Album",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = { showMoveToAlbumDialog = false }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }

                        Text(
                            text = "Select an existing album or create a brand new one to classify this photo:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (availableAlbums.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableAlbums.forEach { album ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onMoveToAlbum?.invoke(photo, album)
                                                showMoveToAlbumDialog = false
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.FolderSpecial, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(album, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = newAlbumInput,
                            onValueChange = { newAlbumInput = it },
                            label = { Text("New Album Name") },
                            placeholder = { Text("e.g. Travel") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("detail_album_input"),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showMoveToAlbumDialog = false }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val album = newAlbumInput.trim()
                                    if (album.isNotEmpty()) {
                                        onMoveToAlbum?.invoke(photo, album)
                                        showMoveToAlbumDialog = false
                                    }
                                },
                                enabled = newAlbumInput.isNotBlank(),
                                modifier = Modifier.testTag("detail_album_confirm")
                            ) {
                                Text("Move")
                            }
                        }
                    }
                }
            }
        }

        if (showMoveToFolderDialog) {
            var newFolderInput by remember { mutableStateOf("") }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showMoveToFolderDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {}
                        .testTag("detail_move_to_folder_dialog")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Move Photo to Folder",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = { showMoveToFolderDialog = false }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }

                        Text(
                            text = "Select a local destination folder or define a custom directory structure segment:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val presetDirs = listOf("Camera", "Downloads", "Screenshots", "Web Presets", "Cloud Archive")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp)
                                .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presetDirs.forEach { folder ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onMoveToFolder?.invoke(photo, folder)
                                            showMoveToFolderDialog = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(folder, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = newFolderInput,
                            onValueChange = { newFolderInput = it },
                            label = { Text("New Folder Name") },
                            placeholder = { Text("e.g. Vacation_2026") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("detail_folder_input"),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showMoveToFolderDialog = false }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val folder = newFolderInput.trim()
                                    if (folder.isNotEmpty()) {
                                        onMoveToFolder?.invoke(photo, folder)
                                        showMoveToFolderDialog = false
                                    }
                                },
                                enabled = newFolderInput.isNotBlank(),
                                modifier = Modifier.testTag("detail_folder_confirm")
                            ) {
                                Text("Move")
                            }
                        }
                    }
                }
            }
        }
        if (showEditorDialog) {
            // Let's copy initial states of title, description, location
            var editedTitle by remember(photo.id, photo.title) { mutableStateOf(photo.title) }
            var editedDescription by remember(photo.id, photo.description) { mutableStateOf(photo.description) }
            var editedLocation by remember(photo.id, photo.location) { mutableStateOf(photo.location) }
            
            // Detect if this file is a video
            val isVideo = remember(photo.tags) {
                photo.tags.split(",").map { it.trim().lowercase() }.contains("video")
            }
            
            // For Images: Active rotate and filter settings
            // Let's parse initial rotation and filter from tags
            val initialRotation = remember(photo.tags) { getRotationFromTags(photo.tags) }
            var activeRotation by remember(photo.tags) { mutableStateOf(initialRotation) }
            
            val initialFilter = remember(photo.tags) {
                val filterTag = photo.tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("filter:") }
                filterTag?.substringAfter("filter:") ?: "none"
            }
            var activeFilter by remember(photo.tags) { mutableStateOf(initialFilter) }
            
            // Parse initial adjustments
            val initialBrightness = remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("brightness:") }
                tag?.substringAfter("brightness:")?.toFloatOrNull() ?: 0f
            }
            var activeBrightness by remember(photo.tags) { mutableStateOf(initialBrightness) }

            val initialContrast = remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("contrast:") }
                tag?.substringAfter("contrast:")?.toFloatOrNull() ?: 1f
            }
            var activeContrast by remember(photo.tags) { mutableStateOf(initialContrast) }

            val initialSaturation = remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("saturation:") }
                tag?.substringAfter("saturation:")?.toFloatOrNull() ?: 1f
            }
            var activeSaturation by remember(photo.tags) { mutableStateOf(initialSaturation) }
            
            var activeFlipH by remember(photo.tags) {
                mutableStateOf(photo.tags.split(",").map { it.trim().lowercase() }.any { it == "flip_h:true" })
            }
            var activeFlipV by remember(photo.tags) {
                mutableStateOf(photo.tags.split(",").map { it.trim().lowercase() }.any { it == "flip_v:true" })
            }
            
            var activeExposure by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("exposure:") }
                mutableStateOf(tag?.substringAfter("exposure:")?.toFloatOrNull() ?: 0f)
            }
            var activeHue by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("hue:") }
                mutableStateOf(tag?.substringAfter("hue:")?.toFloatOrNull() ?: 0f)
            }
            var activeVignette by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("vignette:") }
                mutableStateOf(tag?.substringAfter("vignette:")?.toFloatOrNull() ?: 0f)
            }
            var activeBlur by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("blur:") }
                mutableStateOf(tag?.substringAfter("blur:")?.toFloatOrNull() ?: 0f)
            }
            
            val activeStrokes = remember { mutableStateListOf<RelativeStroke>() }
            var currentBrushColor by remember { mutableStateOf(Color.Red) }
            var currentBrushThickness by remember { mutableStateOf(8f) }
            
            var watermarkText by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_text:") }
                mutableStateOf(tag?.substringAfter(":") ?: "")
            }
            var watermarkColorHex by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_color:") }
                mutableStateOf(tag?.substringAfter(":") ?: "#FFFFFF")
            }
            var watermarkSizeRatio by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_size:") }
                mutableStateOf(tag?.substringAfter(":")?.toFloatOrNull() ?: 0.04f)
            }
            var watermarkOpacity by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_opacity:") }
                mutableStateOf(tag?.substringAfter(":")?.toFloatOrNull() ?: 0.8f)
            }
            var watermarkPosition by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_pos:") }
                mutableStateOf(tag?.substringAfter(":") ?: "bottom_right")
            }
            var watermarkFont by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_font:") }
                mutableStateOf(tag?.substringAfter(":") ?: "SansSerif")
            }
            var watermarkBg by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_bg:") }
                mutableStateOf(tag?.substringAfter(":") ?: "none")
            }
            
            var watermarkDragX by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_drag_x:") }
                mutableStateOf(tag?.substringAfter(":")?.toFloatOrNull() ?: 0f)
            }
            var watermarkDragY by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_drag_y:") }
                mutableStateOf(tag?.substringAfter(":")?.toFloatOrNull() ?: 0f)
            }
            var watermarkRotation by remember { mutableStateOf(0f) }
            var watermarkRtl by remember { mutableStateOf(false) }
            var watermarkBold by remember { mutableStateOf(false) }
            var watermarkItalic by remember { mutableStateOf(false) }
            var watermarkUnderline by remember { mutableStateOf(false) }
            var watermarkBgColorHex by remember { mutableStateOf("#000000") }
            var watermarkLetterSpacing by remember { mutableStateOf(0f) }
            var watermarkBorderColorHex by remember { mutableStateOf("#00FFCC") }
            var watermarkShadowColorHex by remember { mutableStateOf("#000000") }
            var watermarkTextAlign by remember { mutableStateOf("center") }

            var isEditingTextInline by remember { mutableStateOf(false) }

            val activeLayers = remember(photo.tags) {
                val tag = photo.tags.split(",").find { it.trim().lowercase().startsWith("layers:") }
                val serialized = tag?.substringAfter("layers:") ?: ""
                val decodedLayers = deserializeLayers(serialized)
                val stateList = androidx.compose.runtime.mutableStateListOf<ImageLayerData>()
                stateList.addAll(decodedLayers)
                stateList
            }
            var activeLayerId by remember { mutableStateOf<String?>(null) }
            var lastEditorWidth by remember { mutableStateOf(320f) }
            var lastEditorHeight by remember { mutableStateOf(480f) }
            var doodleDrawEnabled by remember { mutableStateOf(false) }
            var showAddLayerDialog by remember { mutableStateOf(false) }
            var watermarkAdded by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("watermark_text:") }
                mutableStateOf(tag != null && tag.substringAfter(":").isNotEmpty())
            }
            var showAdvancedDesignAndLayerControls by remember { mutableStateOf(true) }
            
            var activeFrame by remember(photo.tags) {
                val tag = photo.tags.split(",").map { it.trim() }.find { it.lowercase().startsWith("frame:") }
                mutableStateOf(tag?.substringAfter(":") ?: "none")
            }
            
            var aiPromptInput by remember { mutableStateOf("") }
            var aiIsProcessing by remember { mutableStateOf(false) }
            var aiStatusMessage by remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()
            
            var showSavePrompt by remember { mutableStateOf(false) }
            
            val initialCrop = remember(photo.tags) { getCropLabelFromTags(photo.tags) }
            val savedCropRect = remember(photo.tags) { getCropRectFromTags(photo.tags) }
            var activeCrop by remember(photo.tags) { mutableStateOf(initialCrop) }
            var cropLeft by remember(photo.id) { mutableStateOf(savedCropRect?.left ?: 0f) }
            var cropTop by remember(photo.id) { mutableStateOf(savedCropRect?.top ?: 0f) }
            var cropRight by remember(photo.id) { mutableStateOf(savedCropRect?.right ?: 1f) }
            var cropBottom by remember(photo.id) { mutableStateOf(savedCropRect?.bottom ?: 1f) }
            var activeDragHandle by remember(photo.id) { mutableStateOf<DragHandle?>(null) }
            var imageAspectRatio by remember(photo.id) { mutableStateOf<Float?>(null) }
            
            // For Videos: Active playback speed and simulated trimming limits
            val initialSpeed = remember(photo.tags) {
                val speedTag = photo.tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("speed:") }
                speedTag?.substringAfter("speed:") ?: "1.0x"
            }
            var activeSpeed by remember(photo.tags) { mutableStateOf(initialSpeed) }
            
            val initialTrim = remember(photo.tags) {
                val trimTag = photo.tags.split(",").map { it.trim().lowercase() }.find { it.startsWith("trim:") }
                val range = trimTag?.substringAfter("trim:")?.split("-")
                val start = range?.getOrNull(0)?.toFloatOrNull() ?: 0f
                val end = range?.getOrNull(1)?.toFloatOrNull() ?: 30f
                Pair(start, end)
            }
            var trimStart by remember(photo.tags) { mutableStateOf(initialTrim.first) }
            var trimEnd by remember(photo.tags) { mutableStateOf(initialTrim.second) }

            // Active tab selection
            var activeTab by remember { mutableStateOf(if (isVideo) EditorTab.VIDEO_TRIM_SPEED else EditorTab.CROP_ROTATE) }

            // Undo/Redo history support state
            val undoStack = remember { mutableStateListOf<EditorHistoryState>() }
            val redoStack = remember { mutableStateListOf<EditorHistoryState>() }
            var lastSavedState by remember { mutableStateOf<EditorHistoryState?>(null) }
            var isUndoRedoAction by remember { mutableStateOf(false) }

            val createCurrentHistoryState = {
                EditorHistoryState(
                    rotation = activeRotation,
                    filter = activeFilter,
                    brightness = activeBrightness,
                    contrast = activeContrast,
                    saturation = activeSaturation,
                    flipH = activeFlipH,
                    flipV = activeFlipV,
                    exposure = activeExposure,
                    hue = activeHue,
                    vignette = activeVignette,
                    blur = activeBlur,
                    watermarkText = watermarkText,
                    watermarkColorHex = watermarkColorHex,
                    watermarkSizeRatio = watermarkSizeRatio,
                    watermarkOpacity = watermarkOpacity,
                    watermarkPosition = watermarkPosition,
                    watermarkFont = watermarkFont,
                    watermarkBg = watermarkBg,
                    watermarkDragX = watermarkDragX,
                    watermarkDragY = watermarkDragY,
                    watermarkRotation = watermarkRotation,
                    watermarkRtl = watermarkRtl,
                    watermarkBold = watermarkBold,
                    watermarkItalic = watermarkItalic,
                    watermarkUnderline = watermarkUnderline,
                    watermarkBgColorHex = watermarkBgColorHex,
                    watermarkLetterSpacing = watermarkLetterSpacing,
                    watermarkBorderColorHex = watermarkBorderColorHex,
                    watermarkShadowColorHex = watermarkShadowColorHex,
                    watermarkTextAlign = watermarkTextAlign,
                    strokes = activeStrokes.toList(),
                    imageLayers = activeLayers.toList(),
                    activeFrame = activeFrame,
                    activeCrop = activeCrop,
                    cropLeft = cropLeft,
                    cropTop = cropTop,
                    cropRight = cropRight,
                    cropBottom = cropBottom,
                    activeSpeed = activeSpeed,
                    trimStart = trimStart,
                    trimEnd = trimEnd
                )
            }

            val onUndo = {
                if (undoStack.isNotEmpty()) {
                    isUndoRedoAction = true
                    val prevState = undoStack.removeAt(undoStack.lastIndex)
                    val current = createCurrentHistoryState()
                    redoStack.add(current)
                    lastSavedState = prevState

                    // Apply history state to variables
                    activeRotation = prevState.rotation
                    activeFilter = prevState.filter
                    activeBrightness = prevState.brightness
                    activeContrast = prevState.contrast
                    activeSaturation = prevState.saturation
                    activeFlipH = prevState.flipH
                    activeFlipV = prevState.flipV
                    activeExposure = prevState.exposure
                    activeHue = prevState.hue
                    activeVignette = prevState.vignette
                    activeBlur = prevState.blur
                    watermarkText = prevState.watermarkText
                    watermarkColorHex = prevState.watermarkColorHex
                    watermarkSizeRatio = prevState.watermarkSizeRatio
                    watermarkOpacity = prevState.watermarkOpacity
                    watermarkPosition = prevState.watermarkPosition
                    watermarkFont = prevState.watermarkFont
                    watermarkBg = prevState.watermarkBg
                    watermarkDragX = prevState.watermarkDragX
                    watermarkDragY = prevState.watermarkDragY
                    watermarkRotation = prevState.watermarkRotation
                    watermarkRtl = prevState.watermarkRtl
                    watermarkBold = prevState.watermarkBold
                    watermarkItalic = prevState.watermarkItalic
                    watermarkUnderline = prevState.watermarkUnderline
                    watermarkBgColorHex = prevState.watermarkBgColorHex
                    watermarkLetterSpacing = prevState.watermarkLetterSpacing
                    watermarkBorderColorHex = prevState.watermarkBorderColorHex
                    watermarkShadowColorHex = prevState.watermarkShadowColorHex
                    watermarkTextAlign = prevState.watermarkTextAlign
                    
                    activeStrokes.clear()
                    activeStrokes.addAll(prevState.strokes)

                    activeLayers.clear()
                    activeLayers.addAll(prevState.imageLayers)

                    activeFrame = prevState.activeFrame
                    activeCrop = prevState.activeCrop
                    cropLeft = prevState.cropLeft
                    cropTop = prevState.cropTop
                    cropRight = prevState.cropRight
                    cropBottom = prevState.cropBottom
                    activeSpeed = prevState.activeSpeed
                    trimStart = prevState.trimStart
                    trimEnd = prevState.trimEnd
                }
            }

            val onRedo = {
                if (redoStack.isNotEmpty()) {
                    isUndoRedoAction = true
                    val nextState = redoStack.removeAt(redoStack.lastIndex)
                    val current = createCurrentHistoryState()
                    undoStack.add(current)
                    lastSavedState = nextState

                    // Apply history state to variables
                    activeRotation = nextState.rotation
                    activeFilter = nextState.filter
                    activeBrightness = nextState.brightness
                    activeContrast = nextState.contrast
                    activeSaturation = nextState.saturation
                    activeFlipH = nextState.flipH
                    activeFlipV = nextState.flipV
                    activeExposure = nextState.exposure
                    activeHue = nextState.hue
                    activeVignette = nextState.vignette
                    activeBlur = nextState.blur
                    watermarkText = nextState.watermarkText
                    watermarkColorHex = nextState.watermarkColorHex
                    watermarkSizeRatio = nextState.watermarkSizeRatio
                    watermarkOpacity = nextState.watermarkOpacity
                    watermarkPosition = nextState.watermarkPosition
                    watermarkFont = nextState.watermarkFont
                    watermarkBg = nextState.watermarkBg
                    watermarkDragX = nextState.watermarkDragX
                    watermarkDragY = nextState.watermarkDragY
                    watermarkRotation = nextState.watermarkRotation
                    watermarkRtl = nextState.watermarkRtl
                    watermarkBold = nextState.watermarkBold
                    watermarkItalic = nextState.watermarkItalic
                    watermarkUnderline = nextState.watermarkUnderline
                    watermarkBgColorHex = nextState.watermarkBgColorHex
                    watermarkLetterSpacing = nextState.watermarkLetterSpacing
                    watermarkBorderColorHex = nextState.watermarkBorderColorHex
                    watermarkShadowColorHex = nextState.watermarkShadowColorHex
                    watermarkTextAlign = nextState.watermarkTextAlign

                    activeStrokes.clear()
                    activeStrokes.addAll(nextState.strokes)

                    activeLayers.clear()
                    activeLayers.addAll(nextState.imageLayers)

                    activeFrame = nextState.activeFrame
                    activeCrop = nextState.activeCrop
                    cropLeft = nextState.cropLeft
                    cropTop = nextState.cropTop
                    cropRight = nextState.cropRight
                    cropBottom = nextState.cropBottom
                    activeSpeed = nextState.activeSpeed
                    trimStart = nextState.trimStart
                    trimEnd = nextState.trimEnd
                }
            }

            // Automate recording of history transitions
            androidx.compose.runtime.LaunchedEffect(
                activeRotation, activeFilter, activeBrightness, activeContrast, activeSaturation,
                activeFlipH, activeFlipV, activeExposure, activeHue, activeVignette, activeBlur,
                watermarkText, watermarkColorHex, watermarkSizeRatio, watermarkOpacity, watermarkPosition, watermarkFont, watermarkBg,
                watermarkDragX, watermarkDragY, watermarkRotation, watermarkRtl, watermarkBold, watermarkItalic, watermarkUnderline,
                watermarkBgColorHex, watermarkLetterSpacing, watermarkBorderColorHex, watermarkShadowColorHex, watermarkTextAlign,
                activeFrame, activeCrop, cropLeft, cropTop, cropRight, cropBottom, activeSpeed, trimStart, trimEnd,
                activeStrokes.size, activeLayers.toList()
            ) {
                // Debounce so quick continuous gestures/inputs don't clutter the stack
                kotlinx.coroutines.delay(500)
                val current = createCurrentHistoryState()
                if (isUndoRedoAction) {
                    lastSavedState = current
                    isUndoRedoAction = false
                } else {
                    if (lastSavedState == null) {
                        lastSavedState = current
                    } else if (lastSavedState != current) {
                        undoStack.add(lastSavedState!!)
                        lastSavedState = current
                        redoStack.clear()
                    }
                }
            }

            val onPerformSave = { asCopy: Boolean ->
                val cleanedTags = photo.tags.split(",")
                    .map { it.trim() }
                    .filter { t ->
                        val tl = t.lowercase()
                        !tl.startsWith("rotate:") &&
                        !tl.startsWith("filter:") &&
                        !tl.startsWith("brightness:") &&
                        !tl.startsWith("contrast:") &&
                        !tl.startsWith("saturation:") &&
                        !tl.startsWith("crop:") &&
                        !tl.startsWith("crop_rect:") &&
                        !tl.startsWith("speed:") &&
                        !tl.startsWith("trim:") &&
                        !tl.startsWith("flip_h:") &&
                        !tl.startsWith("flip_v:") &&
                        !tl.startsWith("exposure:") &&
                        !tl.startsWith("hue:") &&
                        !tl.startsWith("vignette:") &&
                        !tl.startsWith("blur:") &&
                        !tl.startsWith("markup:") &&
                        !tl.startsWith("watermark_text:") &&
                        !tl.startsWith("watermark_color:") &&
                        !tl.startsWith("watermark_size:") &&
                        !tl.startsWith("watermark_opacity:") &&
                        !tl.startsWith("watermark_pos:") &&
                        !tl.startsWith("watermark_font:") &&
                        !tl.startsWith("watermark_bg:") &&
                        !tl.startsWith("watermark_drag_x:") &&
                        !tl.startsWith("watermark_drag_y:") &&
                        !tl.startsWith("frame:") &&
                        !tl.startsWith("layers:") &&
                        !tl.startsWith("editor_width:") &&
                        !tl.startsWith("editor_height:")
                    }.toMutableList()

                if (activeRotation != 0f) {
                    cleanedTags.add("rotate:${activeRotation}")
                }
                if (activeFilter != "none" && activeFilter.isNotEmpty()) {
                    cleanedTags.add("filter:${activeFilter}")
                }
                if (activeBrightness != 0f) {
                    cleanedTags.add("brightness:${activeBrightness}")
                }
                if (activeContrast != 1f) {
                    cleanedTags.add("contrast:${activeContrast}")
                }
                if (activeSaturation != 1f) {
                    cleanedTags.add("saturation:${activeSaturation}")
                }
                if (activeFlipH) {
                    cleanedTags.add("flip_h:true")
                }
                if (activeFlipV) {
                    cleanedTags.add("flip_v:true")
                }
                if (activeExposure != 0f) {
                    cleanedTags.add("exposure:${activeExposure}")
                }
                if (activeHue != 0f) {
                    cleanedTags.add("hue:${activeHue}")
                }
                if (activeVignette != 0f) {
                    cleanedTags.add("vignette:${activeVignette}")
                }
                if (activeBlur != 0f) {
                    cleanedTags.add("blur:${activeBlur}")
                }
                if (activeStrokes.isNotEmpty()) {
                    val serialized = serializeStrokes(activeStrokes)
                    if (serialized.isNotEmpty()) {
                        cleanedTags.add("markup:${serialized}")
                    }
                }
                if (watermarkAdded && watermarkText.isNotBlank()) {
                    cleanedTags.add("watermark_text:${watermarkText}")
                    cleanedTags.add("watermark_color:${watermarkColorHex}")
                    cleanedTags.add("watermark_size:${watermarkSizeRatio}")
                    cleanedTags.add("watermark_opacity:${watermarkOpacity}")
                    cleanedTags.add("watermark_pos:${watermarkPosition}")
                    cleanedTags.add("watermark_font:${watermarkFont}")
                    cleanedTags.add("watermark_bg:${watermarkBg}")
                    cleanedTags.add("watermark_drag_x:${watermarkDragX}")
                    cleanedTags.add("watermark_drag_y:${watermarkDragY}")
                }
                if (activeFrame != "none") {
                    cleanedTags.add("frame:${activeFrame}")
                }
                if (activeCrop != "none" && activeCrop.isNotEmpty()) {
                    cleanedTags.add("crop:${activeCrop}")
                    cleanedTags.add("crop_rect:${cropLeft}_${cropTop}_${cropRight}_${cropBottom}")
                }
                if (isVideo) {
                    cleanedTags.add("speed:${activeSpeed}")
                    cleanedTags.add("trim:${trimStart.toInt()}-${trimEnd.toInt()}")
                }
                if (activeLayers.isNotEmpty()) {
                    cleanedTags.add("layers:${serializeLayers(activeLayers)}")
                    cleanedTags.add("editor_width:${lastEditorWidth}")
                    cleanedTags.add("editor_height:${lastEditorHeight}")
                }

                val finalTagsString = cleanedTags.joinToString(", ")

                val editedPhoto = photo.copy(
                    title = editedTitle,
                    description = editedDescription,
                    location = editedLocation,
                    tags = finalTagsString
                )

                if (asCopy) {
                    onSavePhotoCopy?.invoke(editedPhoto)
                } else {
                    onUpdatePhoto?.invoke(editedPhoto)
                }
                showEditorDialog = false
            }

            if (showSavePrompt) {
                AlertDialog(
                    onDismissRequest = { showSavePrompt = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Edits", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    text = {
                        Text(
                            text = "Would you like to overwrite the original file with your edits, or save your changes as a new copy?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSavePrompt = false
                                onPerformSave(true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("btn_save_as_copy")
                        ) {
                            Text("Save as Copy")
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    showSavePrompt = false
                                    onPerformSave(false)
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.testTag("btn_overwrite")
                            ) {
                                Text("Overwrite Original")
                            }
                            TextButton(
                                onClick = { showSavePrompt = false },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("Cancel")
                            }
                        }
                    },
                    modifier = Modifier.testTag("save_edits_prompt_dialog")
                )
            }

            BackHandler(enabled = showEditorDialog) {
                showEditorDialog = false
            }

            AnimatedVisibility(
                visible = showEditorDialog,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.fillMaxSize()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0C0C0C) // Exceptionally sleek AMOLED dark layout overlay
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        // 1. Top Control Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showEditorDialog = false },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.7f))
                            ) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancel", style = MaterialTheme.typography.titleMedium)
                            }

                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val canUndo = undoStack.isNotEmpty()
                                val canRedo = redoStack.isNotEmpty()

                                IconButton(
                                    onClick = { onUndo() },
                                    enabled = canUndo,
                                    modifier = Modifier.testTag("btn_undo")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Undo,
                                        contentDescription = "Undo",
                                        tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.25f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(4.dp))
                                
                                Text(
                                    text = "Editing",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = Color.White
                                )
                                
                                Spacer(modifier = Modifier.width(4.dp))
                                
                                IconButton(
                                    onClick = { onRedo() },
                                    enabled = canRedo,
                                    modifier = Modifier.testTag("btn_redo")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Redo,
                                        contentDescription = "Redo",
                                        tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.25f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    showSavePrompt = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("btn_save_media_studio")
                            ) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        // 2. Centered Artboard Preview Framed beautifully with negative space
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.9f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF151515)),
                                contentAlignment = Alignment.Center
                            ) {
                                val editorColorMatrix = remember(activeFilter, activeBrightness, activeContrast, activeSaturation, activeExposure, activeHue) {
                                    val baseArray = when (activeFilter) {
                                        "grayscale" -> floatArrayOf(
                                            0.213f, 0.715f, 0.072f, 0f, 0f,
                                            0.213f, 0.715f, 0.072f, 0f, 0f,
                                            0.213f, 0.715f, 0.072f, 0f, 0f,
                                            0f,     0f,     0f,     1f, 0f
                                        )
                                        "sepia" -> floatArrayOf(
                                            0.393f, 0.769f, 0.189f, 0f, 0f,
                                            0.349f, 0.686f, 0.168f, 0f, 0f,
                                            0.272f, 0.534f, 0.131f, 0f, 0f,
                                            0f,     0f,     0f,     1f, 0f
                                        )
                                        "warm" -> floatArrayOf(
                                            1.1f, 0f,   0f,   0f, 10f,
                                            0f,   1.0f, 0f,   0f, 0f,
                                            0f,   0f,   0.8f, 0f, -10f,
                                            0f,   0f,   0f,   1f, 0f
                                        )
                                        "cool" -> floatArrayOf(
                                            0.8f, 0f,   0f,   0f, -10f,
                                            0f,   1.0f, 0f,   0f, 0f,
                                            0f,   0f,   1.2f, 0f, 15f,
                                            0f,   0f,   0f,   1f, 0f
                                        )
                                        "inverted" -> floatArrayOf(
                                            -1f,  0f,  0f, 0f, 255f,
                                             0f, -1f,  0f, 0f, 255f,
                                             0f,  0f, -1f, 0f, 255f,
                                             0f,  0f,  0f, 1f,   0f
                                        )
                                        "vintage" -> floatArrayOf(
                                            0.9f, 0.5f, 0.1f, 0f, 0f,
                                            0.3f, 0.8f, 0.1f, 0f, 0f,
                                            0.2f, 0.3f, 0.5f, 0f, 0f,
                                            0f,   0f,   0f,   1f, 0f
                                        )
                                        "teal_orange" -> floatArrayOf(
                                            1.21f, 0.09f, 0f, 0f, 15f,
                                            0.09f, 0.91f, 0.09f, 0f, -10f,
                                            0f, 0.09f, 1.25f, 0f, 20f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                        "dramatic" -> floatArrayOf(
                                            1.4f, 0f, 0f, 0f, -38f,
                                            0f, 1.4f, 0f, 0f, -38f,
                                            0f, 0f, 1.4f, 0f, -38f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                        "fade" -> floatArrayOf(
                                            0.83f, 0f, 0f, 0f, 25f,
                                            0f, 0.83f, 0f, 0f, 25f,
                                            0f, 0f, 0.83f, 0f, 25f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                        else -> floatArrayOf(
                                            1f, 0f, 0f, 0f, 0f,
                                            0f, 1f, 0f, 0f, 0f,
                                            0f, 0f, 1f, 0f, 0f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                    }
                                    
                                    val c = activeContrast
                                    val t = (1f - c) * 128f + activeBrightness * 255f
                                    val contrastArray = floatArrayOf(
                                        c,  0f, 0f, 0f, t,
                                        0f, c,  0f, 0f, t,
                                        0f, 0f, c,  0f, t,
                                        0f, 0f, 0f, 1f, 0f
                                    )
                                    
                                    val s = activeSaturation
                                    val rL = 0.213f
                                    val gL = 0.715f
                                    val bL = 0.072f
                                    val satArray = floatArrayOf(
                                        rL * (1f - s) + s, gL * (1f - s),     bL * (1f - s),     0f, 0f,
                                        rL * (1f - s),     gL * (1f - s) + s, bL * (1f - s),     0f, 0f,
                                        rL * (1f - s),     gL * (1f - s),     bL * (1f - s) + s, 0f, 0f,
                                        0f,                0f,                0f,                1f, 0f
                                    )
                                    
                                    val combinedM = androidx.compose.ui.graphics.ColorMatrix()
                                    combinedM.reset()
                                    combinedM.set(androidx.compose.ui.graphics.ColorMatrix(satArray))
                                    
                                    if (activeExposure != 0f) {
                                        val expScale = Math.pow(2.0, activeExposure.toDouble()).toFloat()
                                        combinedM.timesAssign(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                                            expScale, 0f, 0f, 0f, 0f,
                                            0f, expScale, 0f, 0f, 0f,
                                            0f, 0f, expScale, 0f, 0f,
                                            0f, 0f, 0f, 1f, 0f
                                        )))
                                    }
                                    
                                    if (activeHue != 0f) {
                                        val cosVal = Math.cos(Math.toRadians(activeHue.toDouble())).toFloat()
                                        val sinVal = Math.sin(Math.toRadians(activeHue.toDouble())).toFloat()
                                        val hueArray = floatArrayOf(
                                            1f, 0f, 0f, 0f, 0f,
                                            0f, cosVal, -sinVal, 0f, 0f,
                                            0f, sinVal, cosVal, 0f, 0f,
                                            0f, 0f, 0f, 1f, 0f
                                        )
                                        combinedM.timesAssign(androidx.compose.ui.graphics.ColorMatrix(hueArray))
                                    }
                                    
                                    combinedM.timesAssign(androidx.compose.ui.graphics.ColorMatrix(contrastArray))
                                    combinedM.timesAssign(androidx.compose.ui.graphics.ColorMatrix(baseArray))
                                    combinedM
                                }

                                val wPx = constraints.maxWidth.toFloat()
                                val hPx = constraints.maxHeight.toFloat()
                                
                                val availableW = wPx * 0.95f
                                val availableH = hPx * 0.95f
                                val parentAspectRatio = availableW / availableH
                                val (fittedW, fittedH) = if (imageAspectRatio != null) {
                                    val imgRatio = imageAspectRatio!!
                                    if (imgRatio > parentAspectRatio) {
                                         Pair(availableW, availableW / imgRatio)
                                    } else {
                                         Pair(availableH * imgRatio, availableH)
                                    }
                                } else {
                                    Pair(availableW, availableH)
                                }

                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val fittedW_dp = with(density) { fittedW.toDp() }
                                val fittedH_dp = with(density) { fittedH.toDp() }

                                androidx.compose.runtime.SideEffect {
                                    lastEditorWidth = fittedW
                                    lastEditorHeight = fittedH
                                }

                                Box(
                                    modifier = Modifier
                                        .size(width = fittedW_dp, height = fittedH_dp)
                                        .graphicsLayer {
                                            rotationZ = activeRotation
                                            scaleX = if (activeFlipH) -1f else 1f
                                            scaleY = if (activeFlipV) -1f else 1f
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    PhotoFrameContainer(
                                        frameType = activeFrame,
                                        captionText = editedTitle,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(photo.imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Studio Editor Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(editorColorMatrix),
                                        onSuccess = { state ->
                                            val size = state.painter.intrinsicSize
                                            if (size.width > 0f && size.height > 0f) {
                                                imageAspectRatio = size.width / size.height
                                            }
                                        },
                                        contentScale = ContentScale.FillBounds
                                    )

                                    if (activeVignette > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(
                                                    Brush.radialGradient(
                                                        colors = listOf(
                                                            Color.Transparent,
                                                            Color.Black.copy(alpha = (activeVignette * 0.95f).coerceIn(0f, 0.98f))
                                                        ),
                                                        radius = wPx * 0.7f
                                                    )
                                                )
                                        )
                                    }

                                    Canvas(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .pointerInput(activeTab, doodleDrawEnabled) {
                                                if (activeTab == EditorTab.MARKUP && doodleDrawEnabled) {
                                                    detectDragGestures(
                                                        onDragStart = { startOffset ->
                                                            val rx = startOffset.x / size.width.toFloat()
                                                            val ry = startOffset.y / size.height.toFloat()
                                                            activeStrokes.add(
                                                                RelativeStroke(
                                                                    color = currentBrushColor,
                                                                    width = currentBrushThickness,
                                                                    points = listOf(Offset(rx, ry))
                                                                )
                                                            )
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            if (activeStrokes.isNotEmpty()) {
                                                                val lastStroke = activeStrokes.last()
                                                                val rx = change.position.x / size.width.toFloat()
                                                                val ry = change.position.y / size.height.toFloat()
                                                                activeStrokes[activeStrokes.lastIndex] = lastStroke.copy(
                                                                    points = lastStroke.points + Offset(rx, ry)
                                                                )
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                    ) {
                                        activeStrokes.forEach { stroke ->
                                            if (stroke.points.size > 1) {
                                                val pth = androidx.compose.ui.graphics.Path().apply {
                                                    val fst = stroke.points.first()
                                                    moveTo(fst.x * size.width, fst.y * size.height)
                                                    for (i in 1..stroke.points.lastIndex) {
                                                        val pt = stroke.points[i]
                                                        lineTo(pt.x * size.width, pt.y * size.height)
                                                    }
                                                }
                                                drawPath(
                                                    path = pth,
                                                    color = stroke.color,
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                        width = stroke.width,
                                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    // Image layers rendering
                                    activeLayers.forEach { layer -> val filterBlendMode = when (layer.blendModeIndex) { 1 -> androidx.compose.ui.graphics.BlendMode.Multiply; 2 -> androidx.compose.ui.graphics.BlendMode.Screen; 3 -> androidx.compose.ui.graphics.BlendMode.Overlay; 4 -> androidx.compose.ui.graphics.BlendMode.Difference; 5 -> androidx.compose.ui.graphics.BlendMode.ColorDodge; else -> null }; val embossColorMatrix = androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(2.0f, -1.0f, 0.0f, 0.0f, 0f, -1.0f, 2.0f, -1.0f, 0.0f, 0f, 0.0f, -1.0f, 2.0f, 0.0f, 0f, 0.0f, 0.0f, 0.0f, 1.0f, 0f)); val layerColorFilter = if (layer.emboss) { androidx.compose.ui.graphics.ColorFilter.colorMatrix(embossColorMatrix) } else if (filterBlendMode != null) { androidx.compose.ui.graphics.ColorFilter.tint(Color(android.graphics.Color.parseColor(layer.blendColorHex)), filterBlendMode) } else { null };
                                        Box(
                                            modifier = Modifier
                                                .offset { IntOffset(layer.dragX.roundToInt(), layer.dragY.roundToInt()) }
                                                .rotate(layer.rotation)
                                                .size((layer.sizeRatio * 320f).dp)
                                                .pointerInput(layer.id) {
                                                    detectTransformGestures { _, pan, zoom, rotation ->
                                                        val idx = activeLayers.indexOfFirst { it.id == layer.id }
                                                        if (idx != -1) {
                                                            val currentLayer = activeLayers[idx]
                                                            activeLayers[idx] = currentLayer.copy(
                                                                dragX = currentLayer.dragX + pan.x,
                                                                dragY = currentLayer.dragY + pan.y,
                                                                sizeRatio = (currentLayer.sizeRatio * zoom).coerceIn(0.05f, 3.0f),
                                                                rotation = currentLayer.rotation + rotation
                                                            )
                                                        }
                                                        activeLayerId = layer.id
                                                    }
                                                }
                                                .pointerInput(layer.id) {
                                                    detectTapGestures(
                                                        onTap = {
                                                            activeLayerId = layer.id
                                                        }
                                                    )
                                                }
                                                .alpha(layer.alpha).then(if (layer.shadow) Modifier.shadow(12.dp, RoundedCornerShape(8.dp)) else Modifier).then(
                                                    if (activeLayerId == layer.id) {
                                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                                    } else Modifier
                                                )
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(layer.imageUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = "Image layer",
                                                colorFilter = layerColorFilter,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .applyLayerBlendShape(layer.blendShape, layer.featherAmount),
                                                contentScale = ContentScale.Fit
                                            )
                                            layer.mergedLayers.forEach { child ->
                                                val childBlendMode = when (child.blendModeIndex) {
                                                    1 -> androidx.compose.ui.graphics.BlendMode.Multiply
                                                    2 -> androidx.compose.ui.graphics.BlendMode.Screen
                                                    3 -> androidx.compose.ui.graphics.BlendMode.Overlay
                                                    4 -> androidx.compose.ui.graphics.BlendMode.Difference
                                                    5 -> androidx.compose.ui.graphics.BlendMode.ColorDodge
                                                    else -> null
                                                }
                                                val childColorFilter = if (child.emboss) {
                                                    androidx.compose.ui.graphics.ColorFilter.colorMatrix(embossColorMatrix)
                                                } else if (childBlendMode != null) {
                                                    androidx.compose.ui.graphics.ColorFilter.tint(
                                                        Color(android.graphics.Color.parseColor(child.blendColorHex)),
                                                        childBlendMode
                                                    )
                                                } else {
                                                    null
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .offset {
                                                            IntOffset(
                                                                child.dragX.roundToInt(),
                                                                child.dragY.roundToInt()
                                                            )
                                                        }
                                                        .rotate(child.rotation)
                                                        .size((child.sizeRatio * 320f).dp)
                                                        .alpha(child.alpha)
                                                        .then(
                                                            if (child.shadow) Modifier.shadow(
                                                                12.dp,
                                                                RoundedCornerShape(8.dp)
                                                            ) else Modifier
                                                        )
                                                ) {
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(LocalContext.current)
                                                            .data(child.imageUrl)
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = "Merged child layer",
                                                        modifier = Modifier.fillMaxSize(),
                                                        colorFilter = childColorFilter,
                                                        contentScale = ContentScale.Fit
                                                    )
                                                }
                                            }
                                            if (activeLayerId == layer.id) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                        .padding(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Layer Active",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    val isShowingWatermark = watermarkAdded && watermarkText.isNotBlank()
                                    if (isShowingWatermark) {
                                        val previewText = watermarkText
                                        val alignment = when (watermarkPosition.lowercase()) {
                                            "top_left" -> Alignment.TopStart
                                            "top_right" -> Alignment.TopEnd
                                            "bottom_left" -> Alignment.BottomStart
                                            "bottom_right" -> Alignment.BottomEnd
                                            "center" -> Alignment.Center
                                            else -> Alignment.BottomEnd
                                        }
                                        val selectedFamily = when (watermarkFont.lowercase()) {
                                            "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                                            "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                                            "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
                                            else -> androidx.compose.ui.text.font.FontFamily.SansSerif
                                        }
                                        val textFrontColor = try { Color(android.graphics.Color.parseColor(watermarkColorHex)) } catch(e: Exception) { Color.White }.copy(alpha = watermarkOpacity)
                                        val textBackColor = try { Color(android.graphics.Color.parseColor(watermarkBgColorHex)) } catch(e: Exception) { Color.Transparent }

                                        val bgModifier = when (watermarkBg.lowercase()) {
                                            "pill" -> Modifier.background(textBackColor.copy(alpha = watermarkOpacity), RoundedCornerShape(24.dp))
                                            "neon" -> Modifier.background(textBackColor.copy(alpha = watermarkOpacity), RoundedCornerShape(8.dp)).border(1.5.dp, try { Color(android.graphics.Color.parseColor(watermarkBorderColorHex)) } catch(e: Exception) { Color.Cyan }, RoundedCornerShape(8.dp))
                                            "shadow" -> Modifier.background(textBackColor.copy(alpha = watermarkOpacity), RoundedCornerShape(4.dp))
                                            "custom" -> Modifier.background(textBackColor.copy(alpha = watermarkOpacity), RoundedCornerShape(8.dp))
                                            else -> Modifier
                                        }

                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .padding(18.dp),
                                            contentAlignment = alignment
                                        ) {
                                            CompositionLocalProvider(
                                                LocalLayoutDirection provides (if (watermarkRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)
                                            ) {
                                                if (isEditingTextInline) {
                                                    val focusRequester = remember { FocusRequester() }
                                                    BasicTextField(
                                                        value = watermarkText,
                                                        onValueChange = { watermarkText = it },
                                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                            fontFamily = selectedFamily,
                                                            fontWeight = if (watermarkBold) FontWeight.Bold else FontWeight.Normal,
                                                            fontStyle = if (watermarkItalic) FontStyle.Italic else FontStyle.Normal,
                                                            fontSize = (watermarkSizeRatio * 380f).sp,
                                                            textDecoration = if (watermarkUnderline) TextDecoration.Underline else TextDecoration.None,
                                                            shadow = androidx.compose.ui.graphics.Shadow(
                                                                color = try { Color(android.graphics.Color.parseColor(watermarkShadowColorHex)) } catch(e: Exception) { Color.Black },
                                                                offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                                                blurRadius = 4f
                                                            ),
                                                            textAlign = when (watermarkTextAlign.lowercase()) {
                                                                "left" -> TextAlign.Left
                                                                "right" -> TextAlign.Right
                                                                else -> TextAlign.Center
                                                            },
                                                            letterSpacing = watermarkLetterSpacing.sp,
                                                            color = textFrontColor
                                                        ),
                                                        modifier = Modifier
                                                            .focusRequester(focusRequester)
                                                            .offset {
                                                                IntOffset(watermarkDragX.roundToInt(), watermarkDragY.roundToInt())
                                                            }
                                                            .rotate(watermarkRotation)
                                                            .then(bgModifier)
                                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                                        keyboardOptions = KeyboardOptions.Default.copy(
                                                            imeAction = ImeAction.Done
                                                        ),
                                                        keyboardActions = KeyboardActions(
                                                            onDone = { isEditingTextInline = false }
                                                        )
                                                    )
                                                    LaunchedEffect(Unit) {
                                                        focusRequester.requestFocus()
                                                    }
                                                } else {
                                                    Text(
                                                        text = previewText,
                                                        color = textFrontColor,
                                                        fontFamily = selectedFamily,
                                                        textAlign = when (watermarkTextAlign.lowercase()) {
                                                            "left" -> TextAlign.Left
                                                            "right" -> TextAlign.Right
                                                            else -> TextAlign.Center
                                                        },
                                                        letterSpacing = watermarkLetterSpacing.sp,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = if (watermarkBold) FontWeight.Bold else FontWeight.Normal,
                                                            fontStyle = if (watermarkItalic) FontStyle.Italic else FontStyle.Normal,
                                                            fontSize = (watermarkSizeRatio * 380f).sp,
                                                            textDecoration = if (watermarkUnderline) TextDecoration.Underline else TextDecoration.None,
                                                            shadow = androidx.compose.ui.graphics.Shadow(
                                                                color = try { Color(android.graphics.Color.parseColor(watermarkShadowColorHex)) } catch(e: Exception) { Color.Black },
                                                                offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                                                blurRadius = 4f
                                                            )
                                                        ),
                                                        modifier = Modifier
                                                            .offset {
                                                                IntOffset(watermarkDragX.roundToInt(), watermarkDragY.roundToInt())
                                                            }
                                                            .rotate(watermarkRotation)
                                                            .pointerInput(Unit) {
                                                                detectTapGestures(
                                                                    onDoubleTap = {
                                                                        isEditingTextInline = true
                                                                    }
                                                                )
                                                            }
                                                            .pointerInput(Unit) {
                                                                detectTransformGestures { _, pan, zoom, rotation ->
                                                                    watermarkDragX += pan.x
                                                                    watermarkDragY += pan.y
                                                                    watermarkSizeRatio = (watermarkSizeRatio * zoom).coerceIn(0.01f, 0.40f)
                                                                    watermarkRotation += rotation
                                                                }
                                                            }
                                                            .then(bgModifier)
                                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                        }
                                    }

                                    if (!isVideo && activeCrop != "none") {
                                        Canvas(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .pointerInput(photo.id, activeTab) {
                                                    if (activeTab == EditorTab.CROP_ROTATE) {
                                                        detectDragGestures(
                                                            onDragStart = { offset ->
                                                                val touchX = offset.x
                                                                val touchY = offset.y

                                                                val l = cropLeft * size.width
                                                                val r = cropRight * size.width
                                                                val t = cropTop * size.height
                                                                val b = cropBottom * size.height

                                                                val handleRadius = 60f

                                                                val isNearTL = (touchX - l) * (touchX - l) + (touchY - t) * (touchY - t) < handleRadius * handleRadius
                                                                val isNearTR = (touchX - r) * (touchX - r) + (touchY - t) * (touchY - t) < handleRadius * handleRadius
                                                                val isNearBL = (touchX - l) * (touchX - l) + (touchY - b) * (touchY - b) < handleRadius * handleRadius
                                                                val isNearBR = (touchX - r) * (touchX - r) + (touchY - b) * (touchY - b) < handleRadius * handleRadius

                                                                val isInside = touchX in (l + 25f)..(r - 25f) && touchY in (t + 25f)..(b - 25f)

                                                                activeDragHandle = when {
                                                                    isNearTL -> DragHandle.TOP_LEFT
                                                                    isNearTR -> DragHandle.TOP_RIGHT
                                                                    isNearBL -> DragHandle.BOTTOM_LEFT
                                                                    isNearBR -> DragHandle.BOTTOM_RIGHT
                                                                    isInside -> DragHandle.CENTER
                                                                    else -> null
                                                                }
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                val handle = activeDragHandle ?: return@detectDragGestures
                                                                val wPx = size.width.toFloat()
                                                                val hPx = size.height.toFloat()

                                                                val dl = dragAmount.x / wPx
                                                                val dt = dragAmount.y / hPx

                                                                when (handle) {
                                                                    DragHandle.TOP_LEFT -> {
                                                                        cropLeft = (cropLeft + dl).coerceIn(0f, cropRight - 0.15f)
                                                                        cropTop = (cropTop + dt).coerceIn(0f, cropBottom - 0.15f)
                                                                    }
                                                                    DragHandle.TOP_RIGHT -> {
                                                                        cropRight = (cropRight + dl).coerceIn(cropLeft + 0.15f, 1f)
                                                                        cropTop = (cropTop + dt).coerceIn(0f, cropBottom - 0.15f)
                                                                    }
                                                                    DragHandle.BOTTOM_LEFT -> {
                                                                        cropLeft = (cropLeft + dl).coerceIn(0f, cropRight - 0.15f)
                                                                        cropBottom = (cropBottom + dt).coerceIn(cropTop + 0.15f, 1f)
                                                                    }
                                                                    DragHandle.BOTTOM_RIGHT -> {
                                                                        cropRight = (cropRight + dl).coerceIn(cropLeft + 0.15f, 1f)
                                                                        cropBottom = (cropBottom + dt).coerceIn(cropTop + 0.15f, 1f)
                                                                    }
                                                                    DragHandle.CENTER -> {
                                                                        val width = cropRight - cropLeft
                                                                        val height = cropBottom - cropTop
                                                                        val newLeft = (cropLeft + dl).coerceIn(0f, 1f - width)
                                                                        val newTop = (cropTop + dt).coerceIn(0f, 1f - height)
                                                                        cropLeft = newLeft
                                                                        cropRight = newLeft + width
                                                                        cropTop = newTop
                                                                        cropBottom = newTop + height
                                                                    }
                                                                }
                                                            },
                                                            onDragEnd = {
                                                                activeDragHandle = null
                                                            }
                                                        )
                                                    }
                                                }
                                        ) {
                                            val l = cropLeft * size.width
                                            val r = cropRight * size.width
                                            val t = cropTop * size.height
                                            val b = cropBottom * size.height

                                            drawRect(
                                                color = Color.Black.copy(alpha = 0.55f),
                                                topLeft = Offset(0f, 0f),
                                                size = androidx.compose.ui.geometry.Size(size.width, t)
                                            )
                                            drawRect(
                                                color = Color.Black.copy(alpha = 0.55f),
                                                topLeft = Offset(0f, b),
                                                size = androidx.compose.ui.geometry.Size(size.width, size.height - b)
                                            )
                                            drawRect(
                                                color = Color.Black.copy(alpha = 0.55f),
                                                topLeft = Offset(0f, t),
                                                size = androidx.compose.ui.geometry.Size(l, b - t)
                                            )
                                            drawRect(
                                                color = Color.Black.copy(alpha = 0.55f),
                                                topLeft = Offset(r, t),
                                                size = androidx.compose.ui.geometry.Size(size.width - r, b - t)
                                            )

                                            drawRect(
                                                color = Color.White,
                                                topLeft = Offset(l, t),
                                                size = androidx.compose.ui.geometry.Size(r - l, b - t),
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
                                            )

                                            val gridColor = Color.White.copy(alpha = 0.3f)
                                            val colW = (r - l) / 3f
                                            val rowH = (b - t) / 3f

                                            drawLine(color = gridColor, start = Offset(l + colW, t), end = Offset(l + colW, b), strokeWidth = 1.5f)
                                            drawLine(color = gridColor, start = Offset(l + colW * 2f, t), end = Offset(l + colW * 2f, b), strokeWidth = 1.5f)
                                            drawLine(color = gridColor, start = Offset(l, t + rowH), end = Offset(r, t + rowH), strokeWidth = 1.5f)
                                            drawLine(color = gridColor, start = Offset(l, t + rowH * 2f), end = Offset(r, t + rowH * 2f), strokeWidth = 1.5f)

                                            // Draw explicit heavy corner handles
                                            val hColor = Color.White
                                            val hLength = 40f
                                            val hThickness = 6f

                                            // Top Left
                                            drawLine(color = hColor, start = Offset(l - hThickness/2, t), end = Offset(l + hLength, t), strokeWidth = hThickness)
                                            drawLine(color = hColor, start = Offset(l, t - hThickness/2), end = Offset(l, t + hLength), strokeWidth = hThickness)
                                            // Top Right
                                            drawLine(color = hColor, start = Offset(r - hLength, t), end = Offset(r + hThickness/2, t), strokeWidth = hThickness)
                                            drawLine(color = hColor, start = Offset(r, t - hThickness/2), end = Offset(r, t + hLength), strokeWidth = hThickness)
                                            // Bottom Left
                                            drawLine(color = hColor, start = Offset(l - hThickness/2, b), end = Offset(l + hLength, b), strokeWidth = hThickness)
                                            drawLine(color = hColor, start = Offset(l, b - hLength), end = Offset(l, b + hThickness/2), strokeWidth = hThickness)
                                            // Bottom Right
                                            drawLine(color = hColor, start = Offset(r - hLength, b), end = Offset(r + hThickness/2, b), strokeWidth = hThickness)
                                            drawLine(color = hColor, start = Offset(r, b - hLength), end = Offset(r, b + hThickness/2), strokeWidth = hThickness)
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Contextual Control Panel
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF151515))
                                .padding(vertical = 12.dp)
                        ) {
                            when (activeTab) {
                                EditorTab.CROP_ROTATE -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Aspect Crop Ratio Overlay",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                        val cropList = listOf(
                                            "none" to "Original",
                                            "1:1" to "1:1 Square",
                                            "4:3" to "4:3 Standard",
                                            "16:9" to "16:9 Wide",
                                            "3:2" to "3:2 Classic"
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            cropList.forEach { (key, label) ->
                                                FilterChip(
                                                    selected = activeCrop == key,
                                                    onClick = {
                                                        activeCrop = key
                                                        val imgAspect = imageAspectRatio ?: 1f
                                                        when (key) {
                                                            "none", "free" -> {
                                                                cropLeft = 0f
                                                                cropTop = 0f
                                                                cropRight = 1f
                                                                cropBottom = 1f
                                                            }
                                                            "1:1" -> {
                                                                val rect = if (imgAspect > 1f) {
                                                                    val wFraction = 1f / imgAspect
                                                                    val margin = (1f - wFraction) / 2f
                                                                    android.graphics.RectF(margin, 0f, 1f - margin, 1f)
                                                                } else {
                                                                    val hFraction = imgAspect / 1f
                                                                    val margin = (1f - hFraction) / 2f
                                                                    android.graphics.RectF(0f, margin, 1f, 1f - margin)
                                                                }
                                                                cropLeft = rect.left
                                                                cropTop = rect.top
                                                                cropRight = rect.right
                                                                cropBottom = rect.bottom
                                                            }
                                                            "4:3" -> {
                                                                val target = 4f / 3f
                                                                val rect = if (imgAspect > target) {
                                                                    val wFraction = target / imgAspect
                                                                    val margin = (1f - wFraction) / 2f
                                                                    android.graphics.RectF(margin, 0f, 1f - margin, 1f)
                                                                } else {
                                                                    val hFraction = imgAspect / target
                                                                    val margin = (1f - hFraction) / 2f
                                                                    android.graphics.RectF(0f, margin, 1f, 1f - margin)
                                                                }
                                                                cropLeft = rect.left
                                                                cropTop = rect.top
                                                                cropRight = rect.right
                                                                cropBottom = rect.bottom
                                                            }
                                                            "16:9" -> {
                                                                val target = 16f / 9f
                                                                val rect = if (imgAspect > target) {
                                                                    val wFraction = target / imgAspect
                                                                    val margin = (1f - wFraction) / 2f
                                                                    android.graphics.RectF(margin, 0f, 1f - margin, 1f)
                                                                } else {
                                                                    val hFraction = imgAspect / target
                                                                    val margin = (1f - hFraction) / 2f
                                                                    android.graphics.RectF(0f, margin, 1f, 1f - margin)
                                                                }
                                                                cropLeft = rect.left
                                                                cropTop = rect.top
                                                                cropRight = rect.right
                                                                cropBottom = rect.bottom
                                                            }
                                                            "3:2" -> {
                                                                val target = 3f / 2f
                                                                val rect = if (imgAspect > target) {
                                                                    val wFraction = target / imgAspect
                                                                    val margin = (1f - wFraction) / 2f
                                                                    android.graphics.RectF(margin, 0f, 1f - margin, 1f)
                                                                } else {
                                                                    val hFraction = imgAspect / target
                                                                    val margin = (1f - hFraction) / 2f
                                                                    android.graphics.RectF(0f, margin, 1f, 1f - margin)
                                                                }
                                                                cropLeft = rect.left
                                                                cropTop = rect.top
                                                                cropRight = rect.right
                                                                cropBottom = rect.bottom
                                                            }
                                                        }
                                                    },
                                                    label = { Text(label, color = if (activeCrop == key) MaterialTheme.colorScheme.onPrimaryContainer else Color.White) },
                                                    modifier = Modifier.testTag("btn_crop_$key"),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        containerColor = Color.White.copy(alpha = 0.1f)
                                                    )
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Rotate Angle",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                val degreesList = listOf(0f, 90f, 180f, 270f)
                                                degreesList.forEach { deg ->
                                                    FilterChip(
                                                        selected = activeRotation == deg,
                                                        onClick = { activeRotation = deg },
                                                        label = { Text("${deg.toInt()}°", color = if (activeRotation == deg) MaterialTheme.colorScheme.onPrimaryContainer else Color.White) },
                                                        modifier = Modifier.testTag("btn_rotate_${deg.toInt()}"),
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                            containerColor = Color.White.copy(alpha = 0.1f)
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Mirror Flips",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                FilterChip(
                                                    selected = activeFlipH,
                                                    onClick = { activeFlipH = !activeFlipH },
                                                    label = { Text("Horizontal", color = if (activeFlipH) MaterialTheme.colorScheme.onPrimaryContainer else Color.White) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        containerColor = Color.White.copy(alpha = 0.1f)
                                                    )
                                                )
                                                FilterChip(
                                                    selected = activeFlipV,
                                                    onClick = { activeFlipV = !activeFlipV },
                                                    label = { Text("Vertical", color = if (activeFlipV) MaterialTheme.colorScheme.onPrimaryContainer else Color.White) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        containerColor = Color.White.copy(alpha = 0.1f)
                                                    )
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                        Spacer(modifier = Modifier.height(4.dp))

                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "Aesthetic Photo Frames",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                            val framesList = listOf(
                                                "none" to "No Frame",
                                                "polaroid" to "Polaroid Retro",
                                                "minimal_white" to "Minimal White",
                                                "film_strip" to "Film Strip",
                                                "neon_glow" to "Neon Cyber Glow",
                                                "royal_gold" to "Royal Gold Border"
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                framesList.forEach { (key, label) ->
                                                    FilterChip(
                                                        selected = activeFrame == key,
                                                        onClick = { activeFrame = key },
                                                        label = { Text(label, color = if (activeFrame == key) MaterialTheme.colorScheme.onPrimaryContainer else Color.White) },
                                                        modifier = Modifier.testTag("btn_frame_$key"),
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                            containerColor = Color.White.copy(alpha = 0.1f)
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                EditorTab.FILTERS -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Color Preset Filters",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                        val filtersList = listOf(
                                            "none" to "Normal",
                                            "grayscale" to "B&W",
                                            "sepia" to "Sepia",
                                            "warm" to "Warm Glow",
                                            "cool" to "Cool Aqua",
                                            "teal_orange" to "Teal & Orange",
                                            "dramatic" to "Dramatic",
                                            "fade" to "Fade Film",
                                            "vintage" to "Vintage",
                                            "inverted" to "Inverted"
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            filtersList.forEach { (key, label) ->
                                                FilterChip(
                                                    selected = activeFilter == key,
                                                    onClick = { activeFilter = key },
                                                    label = { Text(label, color = if (activeFilter == key) MaterialTheme.colorScheme.onPrimaryContainer else Color.White) },
                                                    modifier = Modifier.testTag("btn_filter_$key"),
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        containerColor = Color.White.copy(alpha = 0.1f)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                EditorTab.ADJUST -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .heightIn(max = 240.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Adjustment Controls",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )

                                        // Brightness Slider
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Brightness", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(80.dp))
                                            Slider(
                                                value = activeBrightness,
                                                onValueChange = { activeBrightness = it },
                                                valueRange = -100f..100f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = MaterialTheme.colorScheme.primary,
                                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.weight(1f).testTag("brightness_slider")
                                            )
                                            Text(
                                                text = "${activeBrightness.toInt()}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White,
                                                modifier = Modifier.width(40.dp),
                                                textAlign = TextAlign.End
                                            )
                                        }

                                        // Contrast Slider
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Contrast", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(80.dp))
                                            Slider(
                                                value = activeContrast,
                                                onValueChange = { activeContrast = it },
                                                valueRange = 0.5f..2.0f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = MaterialTheme.colorScheme.primary,
                                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.weight(1f).testTag("contrast_slider")
                                            )
                                            Text(
                                                text = String.format("%.1fx", activeContrast),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White,
                                                modifier = Modifier.width(40.dp),
                                                textAlign = TextAlign.End
                                            )
                                        }

                                        // Saturation Slider
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Saturation", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(80.dp))
                                            Slider(
                                                value = activeSaturation,
                                                onValueChange = { activeSaturation = it },
                                                valueRange = 0.0f..2.0f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = MaterialTheme.colorScheme.primary,
                                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.weight(1f).testTag("saturation_slider")
                                            )
                                            Text(
                                                text = String.format("%.1fx", activeSaturation),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White,
                                                modifier = Modifier.width(40.dp),
                                                textAlign = TextAlign.End
                                            )
                                        }

                                        // Exposure Slider
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Exposure", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(80.dp))
                                            Slider(
                                                value = activeExposure,
                                                onValueChange = { activeExposure = it },
                                                valueRange = -3.0f..3.0f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = MaterialTheme.colorScheme.primary,
                                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = String.format("%+.1f", activeExposure),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White,
                                                modifier = Modifier.width(40.dp),
                                                textAlign = TextAlign.End
                                            )
                                        }

                                        // Hue Shift Slider
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Hue Shift", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(80.dp))
                                            Slider(
                                                value = activeHue,
                                                onValueChange = { activeHue = it },
                                                valueRange = -180f..180f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = MaterialTheme.colorScheme.primary,
                                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${activeHue.toInt()}°",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White,
                                                modifier = Modifier.width(40.dp),
                                                textAlign = TextAlign.End
                                            )
                                        }

                                        // Vignette Slider
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Vignette", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(80.dp))
                                            Slider(
                                                value = activeVignette,
                                                onValueChange = { activeVignette = it },
                                                valueRange = 0f..1f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = MaterialTheme.colorScheme.primary,
                                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = String.format("%.0f%%", activeVignette * 100f),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White,
                                                modifier = Modifier.width(40.dp),
                                                textAlign = TextAlign.End
                                            )
                                        }

                                        // Blur Slider
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Soft Blur", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(80.dp))
                                            Slider(
                                                value = activeBlur,
                                                onValueChange = { activeBlur = it },
                                                valueRange = 0f..25f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = MaterialTheme.colorScheme.primary,
                                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = String.format("%.0f px", activeBlur),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White,
                                                modifier = Modifier.width(40.dp),
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }
                                }
                                EditorTab.MARKUP -> {
                                    val layerPickerLauncher = rememberLauncherForActivityResult(
                                        contract = ActivityResultContracts.GetContent()
                                    ) { uri: android.net.Uri? ->
                                        if (uri != null) {
                                            activeLayers.add(ImageLayerData(imageUrl = uri.toString()))
                                        }
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .heightIn(max = 440.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { doodleDrawEnabled = !doodleDrawEnabled }, // test
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(if (doodleDrawEnabled) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.08f), CircleShape)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Toggle Doodle Pencil",
                                                        tint = if (doodleDrawEnabled) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = ""
                                                )
                                            }
                                        }

                                        // Simple watermark text field shown when watermark is active
                                        if (watermarkAdded) {
                                            OutlinedTextField(
                                                value = watermarkText,
                                                onValueChange = { watermarkText = it },
                                                label = { Text("Watermark text content layout", color = Color.White.copy(alpha = 0.4f)) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                                ),
                                                modifier = Modifier.fillMaxWidth().testTag("watermark_text_field_main")
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Pencil Tool Button
                                            IconButton(
                                                onClick = { 
                                                    doodleDrawEnabled = !doodleDrawEnabled
                                                },
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(if (doodleDrawEnabled) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                                    .testTag("pencil_tool_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Toggle Doodle Pencil",
                                                    tint = if (doodleDrawEnabled) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            // Text Tool Button ("T")
                                            IconButton(
                                                onClick = { 
                                                    watermarkAdded = !watermarkAdded
                                                    if (watermarkAdded && watermarkText.isBlank()) {
                                                        watermarkText = "Tap to edit text"
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(if (watermarkAdded) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                                    .testTag("add_text_watermark_button")
                                            ) {
                                                Text(
                                                    text = "T",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = if (watermarkAdded) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(4.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (doodleDrawEnabled) "Doodle Drawing is Active" else if (watermarkAdded) "Text Watermark is Active" else "Select a Tool to Markup",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = if (doodleDrawEnabled) "Draw freely with your finger" else if (watermarkAdded) "Double-tap text on screen to edit" else "Draw or add transparent text layouts",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.5f)
                                                )
                                            }

                                            if (activeStrokes.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { activeStrokes.clear() },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, "Clear Scribbles", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }

                                        // Re-open dummy row container to balance the closing brackets of the old row
                                        Row(
                                            modifier = Modifier.fillMaxWidth().height(1.dp).alpha(0.01f),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (doodleDrawEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                        if (false) Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (doodleDrawEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                        /* Doodle bypass block start
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = ""
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = ""
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (doodleDrawEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                            if (activeStrokes.isNotEmpty()) {
                                                TextButton(
                                                    onClick = { activeStrokes.clear() },
                                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                                ) {
                                                    Icon(Icons.Default.Delete, "Undo strokes", modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Clear Scribbles", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }

                                        */
                                        if (doodleDrawEnabled) {
                                            // Doodle Brush Thickness
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Draw Size", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(70.dp))
                                                Slider(
                                                    value = currentBrushThickness,
                                                    onValueChange = { currentBrushThickness = it },
                                                    valueRange = 2f..24f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text("${currentBrushThickness.toInt()}px", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(40.dp))
                                            }

                                            // Doodle Brush Colors
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Brush Color", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(70.dp))
                                                val brushColors = listOf(
                                                    Color.Red to "Red",
                                                    Color.Green to "Green",
                                                    Color.Blue to "Blue",
                                                    Color.Yellow to "Yellow",
                                                    Color.White to "White",
                                                    Color.Black to "Black"
                                                )
                                                Row(
                                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    brushColors.forEach { (col, name) ->
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .clip(CircleShape)
                                                                .background(col)
                                                                .clickable { currentBrushColor = col }
                                                                .border(
                                                                    width = if (currentBrushColor == col) 2.5.dp else 1.dp,
                                                                    color = if (currentBrushColor == col) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                                                                    shape = CircleShape
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showAdvancedDesignAndLayerControls = !showAdvancedDesignAndLayerControls }
                                                .padding(vertical = 8.dp)
                                                .testTag("toggle_advanced_controls_header"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = null,
                                                    tint = if (showAdvancedDesignAndLayerControls) MaterialTheme.colorScheme.primary else Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Advanced Design & Layer Controls",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = if (showAdvancedDesignAndLayerControls) MaterialTheme.colorScheme.primary else Color.White
                                                )
                                            }
                                            Icon(
                                                imageVector = if (showAdvancedDesignAndLayerControls) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Expand or Collapse",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        if (showAdvancedDesignAndLayerControls) {
                                            Text("Advanced Typography & Text Customization", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                        Text("Double-tap the text on screen to edit inline or edit below", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))

                                        if (false) // Text Formatting Row (Bold, Italic, Underline, RTL)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { watermarkBold = !watermarkBold },
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(if (watermarkBold) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            ) {
                                                Text("B", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = if (watermarkBold) MaterialTheme.colorScheme.onPrimaryContainer else Color.White)
                                            }

                                            IconButton(
                                                onClick = { watermarkItalic = !watermarkItalic },
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(if (watermarkItalic) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            ) {
                                                Text("I", style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic), color = if (watermarkItalic) MaterialTheme.colorScheme.onPrimaryContainer else Color.White)
                                            }

                                            IconButton(
                                                onClick = { watermarkUnderline = !watermarkUnderline },
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(if (watermarkUnderline) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            ) {
                                                Text("U", style = MaterialTheme.typography.titleMedium.copy(textDecoration = TextDecoration.Underline), color = if (watermarkUnderline) MaterialTheme.colorScheme.onPrimaryContainer else Color.White)
                                            }

                                            IconButton(
                                                onClick = { watermarkRtl = !watermarkRtl },
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(if (watermarkRtl) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            ) {
                                                Text("RTL", style = MaterialTheme.typography.labelSmall, color = if (watermarkRtl) MaterialTheme.colorScheme.onPrimaryContainer else Color.White)
                                            }

                                            Spacer(modifier = Modifier.weight(1f))

                                            // Text alignment buttons
                                            val aligns = listOf("left" to "L", "center" to "C", "right" to "R")
                                            aligns.forEach { (key, label) ->
                                                IconButton(
                                                    onClick = { watermarkTextAlign = key },
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(if (watermarkTextAlign == key) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, CircleShape)
                                                ) {
                                                    Text(label, style = MaterialTheme.typography.bodySmall, color = if (watermarkTextAlign == key) MaterialTheme.colorScheme.onSecondaryContainer else Color.White)
                                                }
                                            }
                                        }

                                        /* if (false) OutlinedTextField(
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                         ) */

                                         OutlinedTextField(
                                             value = watermarkText,
                                             onValueChange = { watermarkText = it },
                                             label = { Text("Watermark text layout configuration...", color = Color.White.copy(alpha = 0.4f)) },
                                             singleLine = true,
                                             shape = RoundedCornerShape(12.dp),
                                             colors = OutlinedTextFieldDefaults.colors(
                                                 focusedTextColor = Color.White,
                                                 unfocusedTextColor = Color.White,
                                                 focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                 unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                             ),
                                             modifier = Modifier.fillMaxWidth()
                                         )

                                         // Text Formatting Row (Bold, Italic, Underline, RTL)
                                         Row(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalArrangement = Arrangement.spacedBy(8.dp),
                                             verticalAlignment = Alignment.CenterVertically
                                         ) {
                                             IconButton(
                                                 onClick = { watermarkBold = !watermarkBold },
                                                 modifier = Modifier
                                                     .size(40.dp)
                                                     .background(if (watermarkBold) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                             ) {
                                                 Text("B", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = if (watermarkBold) MaterialTheme.colorScheme.onPrimaryContainer else Color.White)
                                             }

                                             IconButton(
                                                 onClick = { watermarkItalic = !watermarkItalic },
                                                 modifier = Modifier
                                                     .size(40.dp)
                                                     .background(if (watermarkItalic) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                             ) {
                                                 Text("I", style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic), color = if (watermarkItalic) MaterialTheme.colorScheme.onPrimaryContainer else Color.White)
                                             }

                                             IconButton(
                                                 onClick = { watermarkUnderline = !watermarkUnderline },
                                                 modifier = Modifier
                                                     .size(40.dp)
                                                     .background(if (watermarkUnderline) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                             ) {
                                                 Text("U", style = MaterialTheme.typography.titleMedium.copy(textDecoration = TextDecoration.Underline), color = if (watermarkUnderline) MaterialTheme.colorScheme.onPrimaryContainer else Color.White)
                                             }

                                             IconButton(
                                                 onClick = { watermarkRtl = !watermarkRtl },
                                                 modifier = Modifier
                                                     .size(40.dp)
                                                     .background(if (watermarkRtl) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                             ) {
                                                 Text("RTL", style = MaterialTheme.typography.labelSmall, color = if (watermarkRtl) MaterialTheme.colorScheme.onPrimaryContainer else Color.White)
                                             }

                                             Spacer(modifier = Modifier.width(8.dp))

                                             // Text alignment buttons
                                             val aligns = listOf("left" to "L", "center" to "C", "right" to "R")
                                             aligns.forEach { (key, label) ->
                                                 IconButton(
                                                     onClick = { watermarkTextAlign = key },
                                                     modifier = Modifier
                                                         .size(36.dp)
                                                         .background(if (watermarkTextAlign == key) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, CircleShape)
                                                 ) {
                                                     Text(label, style = MaterialTheme.typography.bodySmall, color = if (watermarkTextAlign == key) MaterialTheme.colorScheme.onSecondaryContainer else Color.White)
                                                 }
                                             }
                                         }

                                         Spacer(modifier = Modifier.height(6.dp))

                                         // Typography Font Selection
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Typography Font Style", style = MaterialTheme.typography.bodySmall, color = Color.White)
                                            val fontStyles = listOf(
                                                "sans-serif" to "System Sans",
                                                "serif" to "Elegant Serif",
                                                "monospace" to "Retro Code",
                                                "cursive" to "Signature Cursive",
                                                "sans-serif-light" to "Light Sans",
                                                "sans-serif-medium" to "Medium Sans",
                                                "sans-serif-black" to "Ultra Bold Black",
                                                "sans-serif-condensed" to "Condensed",
                                                "casual" to "Casual Sans",
                                                "serif-monospace" to "Serif Code"
                                            )
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                fontStyles.forEach { (key, label) ->
                                                    FilterChip(
                                                        selected = watermarkFont.lowercase() == key.lowercase(),
                                                        onClick = { watermarkFont = key },
                                                        label = { Text(label, color = if (watermarkFont.lowercase() == key.lowercase()) MaterialTheme.colorScheme.onPrimaryContainer else Color.White) },
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                            containerColor = Color.White.copy(alpha = 0.1f)
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        // Solid Famous Color Selector
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Typography Color Selection (Famous)", style = MaterialTheme.typography.bodySmall, color = Color.White)
                                            val colorPalette = listOf(
                                                "#FFFFFF" to "White",
                                                "#000000" to "Black",
                                                "#FF3B30" to "Vibrant Red",
                                                "#FF9500" to "Tangerine Orange",
                                                "#FFCC00" to "Sun Yellow",
                                                "#34C759" to "Emerald Green",
                                                "#00C7BE" to "Cyber Teal",
                                                "#007AFF" to "Classic Blue",
                                                "#5856D6" to "Indigo",
                                                "#AF52DE" to "Royal Purple",
                                                "#FF2D55" to "Hot Pink",
                                                "#E5E5EA" to "Platinum",
                                                "#8E8E93" to "Slate Gray",
                                                "#A2845E" to "Warm Clay"
                                            )
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                colorPalette.forEach { (hex, name) ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(android.graphics.Color.parseColor(hex)))
                                                            .clickable { watermarkColorHex = hex }
                                                            .border(
                                                                width = if (watermarkColorHex.lowercase() == hex.lowercase()) 3.dp else 1.dp,
                                                                color = if (watermarkColorHex.lowercase() == hex.lowercase()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                                                shape = CircleShape
                                                            )
                                                    )
                                                }
                                            }
                                        }

                                        // Custom Color Spectrum sliders (RGB)
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Fine-Tune Custom Color Spectrum (RGB & HEX)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)

                                            val parsedR = try { android.graphics.Color.red(android.graphics.Color.parseColor(watermarkColorHex)) } catch(e: Exception) { 255 }
                                            val parsedG = try { android.graphics.Color.green(android.graphics.Color.parseColor(watermarkColorHex)) } catch(e: Exception) { 255 }
                                            val parsedB = try { android.graphics.Color.blue(android.graphics.Color.parseColor(watermarkColorHex)) } catch(e: Exception) { 255 }

                                            // Red Slider
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("R", style = MaterialTheme.typography.bodySmall, color = Color.Red, modifier = Modifier.width(16.dp))
                                                Slider(
                                                    value = parsedR.toFloat(),
                                                    onValueChange = { r ->
                                                        watermarkColorHex = String.format("#%02X%02X%02X", r.toInt(), parsedG, parsedB)
                                                    },
                                                    valueRange = 0f..255f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text("${parsedR}", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(32.dp))
                                            }

                                            // Green Slider
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("G", style = MaterialTheme.typography.bodySmall, color = Color.Green, modifier = Modifier.width(16.dp))
                                                Slider(
                                                    value = parsedG.toFloat(),
                                                    onValueChange = { g ->
                                                        watermarkColorHex = String.format("#%02X%02X%02X", parsedR, g.toInt(), parsedB)
                                                    },
                                                    valueRange = 0f..255f,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text("${parsedG}", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(32.dp))
                                            }

                                            // Blue Slider
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("B", style = MaterialTheme.typography.bodySmall, color = Color.Blue, modifier = Modifier.width(16.dp))
                                                Slider(
                                                    value = parsedB.toFloat(),
                                                    onValueChange = { b ->
                                                        watermarkColorHex = String.format("#%02X%02X%02X", parsedR, parsedG, b.toInt())
                                                     },
                                                     valueRange = 0f..255f,
                                                     modifier = Modifier.weight(1f)
                                                 )
                                                 Text("${parsedB}", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(32.dp))
                                             }

                                             // HEX text input panel
                                             Row(
                                                 verticalAlignment = Alignment.CenterVertically,
                                                 horizontalArrangement = Arrangement.spacedBy(8.dp)
                                             ) {
                                                 Text("HEX Value:", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                                                 BasicTextField(
                                                     value = watermarkColorHex,
                                                     onValueChange = { hex ->
                                                         if (hex.startsWith("#") && hex.length <= 9) {
                                                             watermarkColorHex = hex
                                                         }
                                                     },
                                                     textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                                     modifier = Modifier
                                                         .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                         .padding(horizontal = 6.dp, vertical = 2.dp)
                                                 )
                                             }
                                         }

                                         // Text Box Material Style
                                         Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                             Text("Text Box Background Material Style", style = MaterialTheme.typography.bodySmall, color = Color.White)
                                             val bgStyles = listOf(
                                                 "none" to "No Background",
                                                 "pill" to "Sleek Capsule Pill",
                                                 "shadow" to "Solid Film Shadow",
                                                 "neon" to "Cyber Neon Glow",
                                                 "custom" to "Custom Matte Plate"
                                             )
                                             Row(
                                                 modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                 horizontalArrangement = Arrangement.spacedBy(6.dp)
                                             ) {
                                                 bgStyles.forEach { (key, label) ->
                                                     FilterChip(
                                                         selected = watermarkBg == key,
                                                         onClick = { watermarkBg = key },
                                                         label = { Text(label, color = if (watermarkBg == key) MaterialTheme.colorScheme.onPrimaryContainer else Color.White) },
                                                         colors = FilterChipDefaults.filterChipColors(
                                                             selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                             containerColor = Color.White.copy(alpha = 0.1f)
                                                         )
                                                     )
                                                 }
                                             }
                                         }

                                         // Background Customizer when style selected is active
                                         if (watermarkBg != "none") {
                                             Column(
                                                 modifier = Modifier
                                                     .fillMaxWidth()
                                                     .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                                     .padding(12.dp),
                                                 verticalArrangement = Arrangement.spacedBy(8.dp)
                                             ) {
                                                 Text("Select Custom Background Color (Famous)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                                 val bgPalette = listOf("#000000", "#1C1C1E", "#333333", "#FF3B30", "#34C759", "#007AFF", "#00FFCC")
                                                 Row(
                                                     modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                     horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                 ) {
                                                     bgPalette.forEach { hex ->
                                                         Box(
                                                             modifier = Modifier
                                                                 .size(28.dp)
                                                                 .clip(CircleShape)
                                                                 .background(Color(android.graphics.Color.parseColor(hex)))
                                                                 .clickable { watermarkBgColorHex = hex }
                                                                 .border(
                                                                     width = if (watermarkBgColorHex.lowercase() == hex.lowercase()) 3.dp else 1.dp,
                                                                     color = if (watermarkBgColorHex.lowercase() == hex.lowercase()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                                                     shape = CircleShape
                                                                 )
                                                         )
                                                     }
                                                 }

                                                 // Sliders for Background Fine-tuning
                                                 val parsedBgR = try { android.graphics.Color.red(android.graphics.Color.parseColor(watermarkBgColorHex)) } catch(e: Exception) { 0 }
                                                 val parsedBgG = try { android.graphics.Color.green(android.graphics.Color.parseColor(watermarkBgColorHex)) } catch(e: Exception) { 0 }
                                                 val parsedBgB = try { android.graphics.Color.blue(android.graphics.Color.parseColor(watermarkBgColorHex)) } catch(e: Exception) { 0 }

                                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                                     Text("Bg R", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(40.dp))
                                                     Slider(
                                                         value = parsedBgR.toFloat(),
                                                         onValueChange = { r ->
                                                             watermarkBgColorHex = String.format("#%02X%02X%02X", r.toInt(), parsedBgG, parsedBgB)
                                                         },
                                                         valueRange = 0f..255f,
                                                         modifier = Modifier.weight(1f)
                                                     )
                                                 }
                                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                                     Text("Bg G", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(40.dp))
                                                     Slider(
                                                         value = parsedBgG.toFloat(),
                                                         onValueChange = { g ->
                                                             watermarkBgColorHex = String.format("#%02X%02X%02X", parsedBgR, g.toInt(), parsedBgB)
                                                         },
                                                         valueRange = 0f..255f,
                                                         modifier = Modifier.weight(1f)
                                                     )
                                                 }
                                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                                     Text("Bg B", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(40.dp))
                                                     Slider(
                                                         value = parsedBgB.toFloat(),
                                                         onValueChange = { b ->
                                                             watermarkBgColorHex = String.format("#%02X%02X%02X", parsedBgR, parsedBgG, b.toInt())
                                                         },
                                                         valueRange = 0f..255f,
                                                         modifier = Modifier.weight(1f)
                                                     )
                                                 }
                                             }
                                         }

                                         // Text Formatting & Placement Sliders (Letter Spacing, Rotation, Scale, Opacity)
                                         Column(
                                             modifier = Modifier.fillMaxWidth(),
                                             verticalArrangement = Arrangement.spacedBy(8.dp)
                                         ) {
                                             Row(verticalAlignment = Alignment.CenterVertically) {
                                                 Text("Letter Spac", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(82.dp))
                                                 Slider(
                                                     value = watermarkLetterSpacing,
                                                     onValueChange = { watermarkLetterSpacing = it },
                                                     valueRange = -2f..14f,
                                                     colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.secondary),
                                                     modifier = Modifier.weight(1f)
                                                 )
                                                 Text(String.format("%.1f pt", watermarkLetterSpacing), style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(44.dp))
                                             }

                                             Row(verticalAlignment = Alignment.CenterVertically) {
                                                 Text("Rotation", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(82.dp))
                                                 Slider(
                                                     value = watermarkRotation,
                                                     onValueChange = { watermarkRotation = it },
                                                     valueRange = -180f..180f,
                                                     colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.secondary),
                                                     modifier = Modifier.weight(1f)
                                                 )
                                                 Text(String.format("%.0f°", watermarkRotation), style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(44.dp))
                                             }

                                             Row(verticalAlignment = Alignment.CenterVertically) {
                                                 Text("Text Size", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(82.dp))
                                                 Slider(
                                                     value = watermarkSizeRatio,
                                                     onValueChange = { watermarkSizeRatio = it },
                                                     valueRange = 0.01f..0.40f,
                                                     modifier = Modifier.weight(1f)
                                                 )
                                                 Text(String.format("%.0f%%", watermarkSizeRatio * 1000f), style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(44.dp))
                                             }

                                             Row(verticalAlignment = Alignment.CenterVertically) {
                                                 Text("Opacity", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(82.dp))
                                                 Slider(
                                                     value = watermarkOpacity,
                                                     onValueChange = { watermarkOpacity = it },
                                                     valueRange = 0.1f..1.0f,
                                                     modifier = Modifier.weight(1f)
                                                 )
                                                 Text(String.format("%.0f%%", watermarkOpacity * 100f), style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(44.dp))
                                             }
                                         }

                                         // Corner Placement Position
                                         Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                             Text("Anchor Position Corner", style = MaterialTheme.typography.bodySmall, color = Color.White)
                                             val posList = listOf(
                                                 "top_left" to "Top-Left",
                                                 "top_right" to "Top-Right",
                                                 "bottom_left" to "Bottom-Left",
                                                 "bottom_right" to "Bottom-Right",
                                                 "center" to "Center"
                                             )
                                             Row(
                                                 modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                 horizontalArrangement = Arrangement.spacedBy(6.dp)
                                             ) {
                                                 posList.forEach { (key, label) ->
                                                     FilterChip(
                                                         selected = watermarkPosition == key,
                                                         onClick = { watermarkPosition = key },
                                                         label = { Text(label, color = if (watermarkPosition == key) MaterialTheme.colorScheme.onPrimaryContainer else Color.White) },
                                                         colors = FilterChipDefaults.filterChipColors(
                                                             selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                             containerColor = Color.White.copy(alpha = 0.1f)
                                                         )
                                                     )
                                                 }
                                             }
                                         }

                                         Spacer(modifier = Modifier.height(4.dp))
                                         HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                         }
                                         Spacer(modifier = Modifier.height(4.dp))

                                         // Creative Image Layers Manager
                                         Text("Creative Image Layers Manager", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                         Text("Add visual overlays or watermark graphics as layered frames", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))

                                         Spacer(modifier = Modifier.height(4.dp))

                                         Button(
                                             onClick = { layerPickerLauncher.launch("image/*") },
                                             colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                             modifier = Modifier.fillMaxWidth(),
                                             shape = RoundedCornerShape(12.dp)
                                         ) {
                                             Icon(Icons.Default.Image, "Upload layer icon")
                                             Spacer(modifier = Modifier.width(8.dp))
                                             Text("Add Image Layer from Local Picker")
                                         }

                                         if (false) // Preselected graphic assets as layering shortcuts
                                         Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                             Text("Or select quick graphic layout presets:", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                                             val presets = listOf(
                                                 "https://images.unsplash.com/photo-1541701494587-cb58502866ab" to "Abstract Border",
                                                 "https://images.unsplash.com/photo-1550684848-fac1c5b4e853" to "Neon Grid",
                                                 "https://images.unsplash.com/photo-1579783902614-a3fb3927b6a5" to "Classical Frame",
                                                 "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe" to "Retro Backdrop"
                                             )
                                             Row(
                                                 modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                 horizontalArrangement = Arrangement.spacedBy(6.dp)
                                             ) {
                                                 presets.forEach { (url, title) ->
                                                     FilterChip(
                                                         selected = false,
                                                         onClick = {
                                                             activeLayers.add(ImageLayerData(imageUrl = url))
                                                         },
                                                         label = { Text(title, color = Color.White) },
                                                         colors = FilterChipDefaults.filterChipColors(
                                                             containerColor = Color.White.copy(alpha = 0.1f)
                                                         ),
                                                         leadingIcon = { Icon(Icons.Default.Add, null, tint = Color.LightGray, modifier = Modifier.size(12.dp)) }
                                                     )
                                                 }
                                             }
                                         }

                                         // List of current Layer items with Bring to Front, Send to Back, Delete actions
                                         if (activeLayers.isNotEmpty()) {
                                              // Selected Layer Fine-Tuning Controls
                                              val selectedLayer = activeLayers.find { it.id == activeLayerId }
                                              if (selectedLayer != null) {
                                                  val sIdx = activeLayers.indexOfFirst { it.id == selectedLayer.id }
                                                  SelectedLayerControls(
                                                      selectedLayer = selectedLayer,
                                                      sIdx = sIdx,
                                                      activeLayers = activeLayers,
                                                      onDeselect = { activeLayerId = null },
                                                      onActiveLayerIdChanged = { id -> activeLayerId = id }
                                                  )
                                                  Spacer(modifier = Modifier.height(8.dp))
                                              }
                                              /* if (false) {
                                                  val sIdx = activeLayers.indexOfFirst { it.id == selectedLayer.id }
                                                  Column(
                                                      modifier = Modifier
                                                          .fillMaxWidth()
                                                          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                                          .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                                          .padding(12.dp),
                                                      verticalArrangement = Arrangement.spacedBy(8.dp)
                                                  ) {
                                                      Row(
                                                          modifier = Modifier.fillMaxWidth(),
                                                          horizontalArrangement = Arrangement.SpaceBetween,
                                                          verticalAlignment = Alignment.CenterVertically
                                                      ) {
                                                          Row(verticalAlignment = Alignment.CenterVertically) {
                                                              Icon(
                                                                  imageVector = Icons.Default.Settings,
                                                                  contentDescription = null,
                                                                  tint = MaterialTheme.colorScheme.primary,
                                                                  modifier = Modifier.size(18.dp)
                                                              )
                                                              Spacer(modifier = Modifier.width(6.dp))
                                                              Text(
                                                                  text = "Layer #${sIdx + 1} Controls",
                                                                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                                  color = Color.White
                                                              )
                                                          }
                                                          IconButton(
                                                              onClick = { activeLayerId = null },
                                                              modifier = Modifier.size(24.dp)
                                                          ) {
                                                              Icon(Icons.Default.Close, "Deselect layer", tint = Color.White, modifier = Modifier.size(16.dp))
                                                          }
                                                      }

                                                      // Resize / Scale Control Slider
                                                      Row(verticalAlignment = Alignment.CenterVertically) {
                                                          Text("Scale", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(70.dp))
                                                          Slider(
                                                              value = selectedLayer.sizeRatio,
                                                              onValueChange = { newVal ->
                                                                  activeLayers[sIdx] = selectedLayer.copy(sizeRatio = newVal)
                                                              },
                                                              valueRange = 0.05f..1.50f,
                                                              modifier = Modifier.weight(1f)
                                                          )
                                                          Text(String.format("%.0f%%", selectedLayer.sizeRatio * 100), style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
                                                      }

                                                      // Rotate Control Slider
                                                      Row(verticalAlignment = Alignment.CenterVertically) {
                                                          Text("Rotation", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(70.dp))
                                                          Slider(
                                                              value = selectedLayer.rotation,
                                                              onValueChange = { newVal ->
                                                                  activeLayers[sIdx] = selectedLayer.copy(rotation = newVal)
                                                              },
                                                              valueRange = -180f..180f,
                                                              modifier = Modifier.weight(1f)
                                                          )
                                                          Text(String.format("%.0f°", selectedLayer.rotation), style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
                                                      }

                                                      // Alpha / Opacity Control Slider
                                                      Row(verticalAlignment = Alignment.CenterVertically) {
                                                          Text("Alpha", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(70.dp))
                                                          Slider(
                                                              value = selectedLayer.alpha,
                                                              onValueChange = { newVal ->
                                                                  activeLayers[sIdx] = selectedLayer.copy(alpha = newVal)
                                                              },
                                                              valueRange = 0f..1.0f,
                                                              modifier = Modifier.weight(1f)
                                                          )
                                                          Text(String.format("%.0f%%", selectedLayer.alpha * 100), style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
                                                      }

                                                      // Effects (Emboss, Shadow) Row
                                                      Row(
                                                          modifier = Modifier.fillMaxWidth(),
                                                          horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                      ) {
                                                          FilterChip(
                                                              selected = selectedLayer.emboss,
                                                              onClick = {
                                                                  activeLayers[sIdx] = selectedLayer.copy(emboss = !selectedLayer.emboss)
                                                              },
                                                              label = { Text("Emboss Effect", style = MaterialTheme.typography.labelSmall) },
                                                              colors = FilterChipDefaults.filterChipColors(
                                                                  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                                  containerColor = Color.White.copy(alpha = 0.1f)
                                                              ),
                                                              modifier = Modifier.weight(1f)
                                                          )

                                                          FilterChip(
                                                              selected = selectedLayer.shadow,
                                                              onClick = {
                                                                  activeLayers[sIdx] = selectedLayer.copy(shadow = !selectedLayer.shadow)
                                                              },
                                                              label = { Text("Shadow Effect", style = MaterialTheme.typography.labelSmall) },
                                                              colors = FilterChipDefaults.filterChipColors(
                                                                  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                                  containerColor = Color.White.copy(alpha = 0.1f)
                                                              ),
                                                              modifier = Modifier.weight(1f)
                                                          )
                                                      }

                                                      // Blend Mode Indexes and Palette row
                                                      val blendModes = listOf("Normal", "Multiply", "Screen", "Overlay", "Difference", "ColorDodge")
                                                      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                          Text("Color Merging Blend Mode", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                                                          Row(
                                                              modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                              horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                          ) {
                                                              blendModes.forEachIndexed { bIdx, name ->
                                                                  FilterChip(
                                                                      selected = selectedLayer.blendModeIndex == bIdx,
                                                                      onClick = {
                                                                          activeLayers[sIdx] = selectedLayer.copy(blendModeIndex = bIdx)
                                                                      },
                                                                      label = { Text(name, style = MaterialTheme.typography.labelSmall, color = if (selectedLayer.blendModeIndex == bIdx) MaterialTheme.colorScheme.onPrimaryContainer else Color.White) },
                                                                      colors = FilterChipDefaults.filterChipColors(
                                                                          selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                                          containerColor = Color.White.copy(alpha = 0.1f)
                                                                      )
                                                                  )
                                                              }
                                                          }
                                                      }

                                                      // Blend/Merging Tint Color selection
                                                      if (selectedLayer.blendModeIndex > 0) {
                                                          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                              Text("Blending / Merging Color Hex", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                                                              val blendPalette = listOf(
                                                                  "#FFFFFF" to "White",
                                                                  "#FF3B30" to "Red",
                                                                  "#FF9500" to "Orange",
                                                                  "#FFCC00" to "Yellow",
                                                                  "#34C759" to "Green",
                                                                  "#007AFF" to "Blue",
                                                                  "#AF52DE" to "Purple",
                                                                  "#00FFFF" to "Cyan",
                                                                  "#FF00FF" to "Magenta"
                                                              )
                                                              Row(
                                                                  modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                              ) {
                                                                  blendPalette.forEach { (hex, name) ->
                                                                      Box(
                                                                          modifier = Modifier
                                                                              .size(24.dp)
                                                                              .clip(CircleShape)
                                                                              .background(Color(android.graphics.Color.parseColor(hex)))
                                                                              .clickable {
                                                                                  activeLayers[sIdx] = selectedLayer.copy(blendColorHex = hex)
                                                                              }
                                                                              .border(
                                                                                  width = if (selectedLayer.blendColorHex.lowercase() == hex.lowercase()) 2.5.dp else 1.dp,
                                                                                  color = if (selectedLayer.blendColorHex.lowercase() == hex.lowercase()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                                                                  shape = CircleShape
                                                                              )
                                                                      )
                                                                  }
                                                              }
                                                          }
                                                      }

                                                      // Action: Merge with layer below
                                                      if (sIdx > 0) {
                                                          Button(
                                                              onClick = {
                                                                  val layerBelow = activeLayers[sIdx - 1]
                                                                  val updatedBelow = layerBelow.copy(
                                                                      mergedLayers = layerBelow.mergedLayers + selectedLayer
                                                                  )
                                                                  activeLayers[sIdx - 1] = updatedBelow
                                                                  activeLayers.removeAt(sIdx)
                                                                  activeLayerId = layerBelow.id
                                                              },
                                                              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                              modifier = Modifier.fillMaxWidth(),
                                                              shape = RoundedCornerShape(8.dp)
                                                          ) {
                                                              Icon(Icons.Default.Layers, "Merge layer down", modifier = Modifier.size(16.dp))
                                                              Spacer(modifier = Modifier.width(6.dp))
                                                              Text("Merge Down into Layer #${sIdx}", style = MaterialTheme.typography.labelSmall)
                                                          }
                                                      }
                                                  }
                                                  Spacer(modifier = Modifier.height(8.dp))
                                              }
                                          } */

                                              Text("Manage Image Layers Stack", style = MaterialTheme.typography.bodySmall, color = Color.White)
                                             Column(
                                                 verticalArrangement = Arrangement.spacedBy(8.dp)
                                             ) {
                                                 activeLayers.forEachIndexed { idx, layer ->
                                                     val isSelected = activeLayerId == layer.id
                                                     Row(
                                                         modifier = Modifier
                                                             .fillMaxWidth()
                                                             .background(
                                                                 if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                                 else Color.White.copy(alpha = 0.05f),
                                                                 RoundedCornerShape(8.dp)
                                                             )
                                                             .then(
                                                                 if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                                                 else Modifier
                                                             )
                                                             .clickable { activeLayerId = layer.id }
                                                             .padding(8.dp),
                                                         verticalAlignment = Alignment.CenterVertically
                                                     ) {
                                                         AsyncImage(
                                                             model = layer.imageUrl,
                                                             contentDescription = "Layer preview",
                                                             modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp))
                                                         )
                                                         Spacer(modifier = Modifier.width(8.dp))
                                                         Column(modifier = Modifier.weight(1f)) {
                                                             Text("Layer #${idx + 1}", style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.Bold)
                                                             Text("Scale: ${(layer.sizeRatio * 100).toInt()}% | Rot: ${layer.rotation.toInt()}°", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                                                         }
                                                         
                                                         // Reorder layers
                                                         IconButton(
                                                             onClick = {
                                                                 if (idx > 0) {
                                                                     activeLayers.removeAt(idx)
                                                                     activeLayers.add(0, layer)
                                                                 }
                                                             },
                                                             modifier = Modifier.size(32.dp)
                                                         ) {
                                                             Icon(Icons.Default.ArrowUpward, "Bring to Front", tint = Color.White, modifier = Modifier.size(16.dp))
                                                         }
                                                         IconButton(
                                                             onClick = {
                                                                 if (idx < activeLayers.size - 1) {
                                                                     activeLayers.removeAt(idx)
                                                                     activeLayers.add(layer)
                                                                 }
                                                             },
                                                             modifier = Modifier.size(32.dp)
                                                         ) {
                                                             Icon(Icons.Default.ArrowDownward, "Send to Back", tint = Color.White, modifier = Modifier.size(16.dp))
                                                         }
                                                         IconButton(
                                                             onClick = {
                                                                 if (activeLayerId == layer.id) {
                                                                     activeLayerId = null
                                                                 }
                                                                 activeLayers.removeAt(idx)
                                                             },
                                                             modifier = Modifier.size(32.dp)
                                                         ) {
                                                             Icon(Icons.Default.Delete, "Delete Layer", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                          }
                                                      }
                                                  }
                                              }
                                          }
                                      }
                                      /*
                                                         }
                                                     }
                                                 }
                                             }
                                         */
                                 }
                                 EditorTab.METADATA -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .heightIn(max = 140.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Asset Metadata & Geotag",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )

                                        OutlinedTextField(
                                            value = editedTitle,
                                            onValueChange = { editedTitle = it },
                                            label = { Text("Title", color = Color.White.copy(alpha = 0.5f)) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier.fillMaxWidth().testTag("edit_title_input")
                                        )

                                        OutlinedTextField(
                                            value = editedDescription,
                                            onValueChange = { editedDescription = it },
                                            label = { Text("Description", color = Color.White.copy(alpha = 0.5f)) },
                                            maxLines = 2,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier.fillMaxWidth().testTag("edit_desc_input")
                                        )

                                        OutlinedTextField(
                                            value = editedLocation,
                                            onValueChange = { editedLocation = it },
                                            label = { Text("Location Geotag", color = Color.White.copy(alpha = 0.5f)) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier.fillMaxWidth().testTag("edit_location_input")
                                        )
                                    }
                                }
                                EditorTab.VIDEO_TRIM_SPEED -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Speed Multiplier",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                val speedsList = listOf("0.5x", "1.0x", "1.5x", "2.0x")
                                                speedsList.forEach { spd ->
                                                    FilterChip(
                                                        selected = activeSpeed == spd,
                                                        onClick = { activeSpeed = spd },
                                                        label = { Text(spd, color = if (activeSpeed == spd) MaterialTheme.colorScheme.onPrimaryContainer else Color.White) },
                                                        modifier = Modifier.testTag("btn_speed_$spd"),
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                            containerColor = Color.White.copy(alpha = 0.1f)
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = "Trim Range: ${trimStart.toInt()}s – ${trimEnd.toInt()}s",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Start Limit", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(80.dp))
                                            Slider(
                                                value = trimStart,
                                                onValueChange = { trimStart = it.coerceAtMost(trimEnd - 1f) },
                                                valueRange = 0f..60f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = MaterialTheme.colorScheme.primary,
                                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.weight(1f).testTag("video_trim_start")
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("End Limit", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(80.dp))
                                            Slider(
                                                value = trimEnd,
                                                onValueChange = { trimEnd = it.coerceAtLeast(trimStart + 1f) },
                                                valueRange = 0f..60f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = MaterialTheme.colorScheme.primary,
                                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.weight(1f).testTag("video_trim_end")
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Modern Persistent Bottom Tab Menu
                        Surface(
                            color = Color(0xFF0C0C0C),
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                contentColor = Color.White,
                                tonalElevation = 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(112.dp)
                                    .padding(bottom = 32.dp)
                            ) {
                            // Tab 1: Crop & Rotate
                            if (!isVideo) {
                                NavigationBarItem(
                                    selected = activeTab == EditorTab.CROP_ROTATE,
                                    onClick = { activeTab = EditorTab.CROP_ROTATE },
                                    icon = { Icon(Icons.Default.Crop, "Crop & Rotate", modifier = Modifier.size(24.dp)) },
                                    label = { Text("Transform") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f),
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("nav_btn_crop")
                                )
                            }

                            // Tab 2: Filters
                            if (!isVideo) {
                                NavigationBarItem(
                                    selected = activeTab == EditorTab.FILTERS,
                                    onClick = { activeTab = EditorTab.FILTERS },
                                    icon = { Icon(Icons.Default.ColorLens, "Preset Filters", modifier = Modifier.size(24.dp)) },
                                    label = { Text("Filters") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f),
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("nav_btn_filters")
                                )
                            }

                            // Tab 3: Adjustments
                            if (!isVideo) {
                                NavigationBarItem(
                                    selected = activeTab == EditorTab.ADJUST,
                                    onClick = { activeTab = EditorTab.ADJUST },
                                    icon = { Icon(Icons.Default.Tune, "Adjustments", modifier = Modifier.size(24.dp)) },
                                    label = { Text("Adjust") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f),
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("nav_btn_adjust")
                                )
                            }

                            // Tab 3.5: Markup & Watermark (Images only)
                            if (!isVideo) {
                                NavigationBarItem(
                                    selected = activeTab == EditorTab.MARKUP,
                                    onClick = { activeTab = EditorTab.MARKUP },
                                    icon = { Icon(Icons.Default.Brush, "Creative Markup & Watermarks", modifier = Modifier.size(24.dp)) },
                                    label = { Text("Markup") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f),
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("nav_btn_markup")
                                )
                            }



                            // Tab 4: Trim & Speed (For Videos only)
                            if (isVideo) {
                                NavigationBarItem(
                                    selected = activeTab == EditorTab.VIDEO_TRIM_SPEED,
                                    onClick = { activeTab = EditorTab.VIDEO_TRIM_SPEED },
                                    icon = { Icon(Icons.Default.ContentCut, "Trim & Speed", modifier = Modifier.size(24.dp)) },
                                    label = { Text("Trim & Speed") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f),
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("nav_btn_trim")
                                )
                            }

                            // Tab 5: Metadata
                            NavigationBarItem(
                                selected = activeTab == EditorTab.METADATA,
                                onClick = { activeTab = EditorTab.METADATA },
                                icon = { Icon(Icons.Default.Edit, "Metadata & Details", modifier = Modifier.size(24.dp)) },
                                label = { Text("Details") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                    unselectedTextColor = Color.White.copy(alpha = 0.6f),
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("nav_btn_metadata")
                            )
                        }
                    }
                }
            }
        }
    }
}
}
}

fun androidx.compose.ui.Modifier.applyLayerBlendShape(
    shape: String,
    featherAmount: Float
): androidx.compose.ui.Modifier {
    if (shape.lowercase() == "none") return this
    
    return this.graphicsLayer {
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@drawWithContent
        
        when (shape.lowercase()) {
            "circular" -> {
                val centerVal = Color.White
                val edgeVal = Color.Transparent
                val radius = (Math.max(w, h) / 2f)
                val solidStop = (1f - featherAmount).coerceIn(0f, 0.99f)
                
                val brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to centerVal,
                        solidStop to centerVal,
                        1.0f to edgeVal
                    ),
                    center = Offset(w / 2f, h / 2f),
                    radius = radius
                )
                
                drawRect(
                    brush = brush,
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                )
            }
            "square" -> {
                val featherPxX = (w / 2f) * featherAmount
                val featherPxY = (h / 2f) * featherAmount
                
                val horizBrush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        (featherPxX / w).coerceIn(0f, 0.5f) to Color.White,
                        ((w - featherPxX) / w).coerceIn(0.5f, 1f) to Color.White,
                        1.0f to Color.Transparent
                    ),
                    startX = 0f,
                    endX = w
                )
                drawRect(brush = horizBrush, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                
                val vertBrush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        (featherPxY / h).coerceIn(0f, 0.5f) to Color.White,
                        ((h - featherPxY) / h).coerceIn(0.5f, 1f) to Color.White,
                        1.0f to Color.Transparent
                    ),
                    startY = 0f,
                    endY = h
                )
                drawRect(brush = vertBrush, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
            }
        }
    }
}

@Composable
fun SelectedLayerControls(
    selectedLayer: ImageLayerData,
    sIdx: Int,
    activeLayers: androidx.compose.runtime.snapshots.SnapshotStateList<ImageLayerData>,
    onDeselect: () -> Unit,
    onActiveLayerIdChanged: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Layer #${sIdx + 1} Controls",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
            IconButton(
                onClick = onDeselect,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Close, "Deselect layer", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        // Resize / Scale Control Slider
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Scale", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(70.dp))
            Slider(
                value = selectedLayer.sizeRatio,
                onValueChange = { newVal ->
                    activeLayers[sIdx] = selectedLayer.copy(sizeRatio = newVal)
                },
                valueRange = 0.05f..1.50f,
                modifier = Modifier.weight(1f)
            )
            Text(String.format("%.0f%%", selectedLayer.sizeRatio * 100), style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
        }

        // Rotate Control Slider
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rotation", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(70.dp))
            Slider(
                value = selectedLayer.rotation,
                onValueChange = { newVal ->
                    activeLayers[sIdx] = selectedLayer.copy(rotation = newVal)
                },
                valueRange = -180f..180f,
                modifier = Modifier.weight(1f)
            )
            Text(String.format("%.0f°", selectedLayer.rotation), style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
        }

        // Alpha / Opacity Control Slider
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alpha", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(70.dp))
            Slider(
                value = selectedLayer.alpha,
                onValueChange = { newVal ->
                    activeLayers[sIdx] = selectedLayer.copy(alpha = newVal)
                },
                valueRange = 0f..1.0f,
                modifier = Modifier.weight(1f)
            )
            Text(String.format("%.0f%%", selectedLayer.alpha * 100), style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
        }

        // Effects (Emboss, Shadow) Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedLayer.emboss,
                onClick = {
                    activeLayers[sIdx] = selectedLayer.copy(emboss = !selectedLayer.emboss)
                },
                label = { Text("Emboss Effect", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = selectedLayer.shadow,
                onClick = {
                    activeLayers[sIdx] = selectedLayer.copy(shadow = !selectedLayer.shadow)
                },
                label = { Text("Shadow Effect", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier.weight(1f)
            )
        }

        // Blend Mode Indexes and Palette row
        val blendModes = listOf("Normal", "Multiply", "Screen", "Overlay", "Difference", "ColorDodge")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Color Merging Blend Mode", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                blendModes.forEachIndexed { bIdx, name ->
                    FilterChip(
                        selected = selectedLayer.blendModeIndex == bIdx,
                        onClick = {
                            activeLayers[sIdx] = selectedLayer.copy(blendModeIndex = bIdx)
                        },
                        label = { Text(name, style = MaterialTheme.typography.labelSmall, color = if (selectedLayer.blendModeIndex == bIdx) MaterialTheme.colorScheme.onPrimaryContainer else Color.White) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }

        // Blend/Merging Tint Color selection
        if (selectedLayer.blendModeIndex > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Blending / Merging Color Hex", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                val blendPalette = listOf(
                    "#FFFFFF" to "White",
                    "#FF3B30" to "Red",
                    "#FF9500" to "Orange",
                    "#FFCC00" to "Yellow",
                    "#34C759" to "Green",
                    "#007AFF" to "Blue",
                    "#AF52DE" to "Purple",
                    "#00FFFF" to "Cyan",
                    "#FF00FF" to "Magenta"
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    blendPalette.forEach { (hex, name) ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .clickable {
                                    activeLayers[sIdx] = selectedLayer.copy(blendColorHex = hex)
                                }
                                .border(
                                    width = if (selectedLayer.blendColorHex.lowercase() == hex.lowercase()) 2.5.dp else 1.dp,
                                    color = if (selectedLayer.blendColorHex.lowercase() == hex.lowercase()) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }

        // Predefined Shapes & Feather Blend Controls
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Edge Blend Shape (Predefined Shapes)", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            val shapes = listOf(
                "None" to "None",
                "Circular" to "Circular",
                "Square" to "Square"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                shapes.forEach { (key, label) ->
                    val isSelected = selectedLayer.blendShape.lowercase() == key.lowercase()
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            activeLayers[sIdx] = selectedLayer.copy(blendShape = key)
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            containerColor = Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (selectedLayer.blendShape.lowercase() != "none") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Feather", style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(60.dp))
                Slider(
                    value = selectedLayer.featherAmount,
                    onValueChange = { newVal ->
                        activeLayers[sIdx] = selectedLayer.copy(featherAmount = newVal)
                    },
                    valueRange = 0.05f..1.0f,
                    modifier = Modifier.weight(1f)
                )
                Text(String.format("%.0f%%", selectedLayer.featherAmount * 100), style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
            }
        }

        // Action: Merge with layer below
        if (sIdx > 0) {
            Button(
                onClick = {
                    val layerBelow = activeLayers[sIdx - 1]
                    val updatedBelow = layerBelow.copy(
                        mergedLayers = layerBelow.mergedLayers + selectedLayer
                    )
                    activeLayers[sIdx - 1] = updatedBelow
                    activeLayers.removeAt(sIdx)
                    onActiveLayerIdChanged(layerBelow.id)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Layers, "Merge layer down", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Merge Down into Layer #${sIdx}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun PhotoFrameContainer(
    frameType: String,
    modifier: Modifier = Modifier,
    captionText: String = "",
    content: @Composable () -> Unit
) {
    val cleanFrame = frameType.lowercase()
    if (cleanFrame == "none" || cleanFrame.isBlank()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            content()
        }
    } else {
        when (cleanFrame) {
            "polaroid" -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = modifier.padding(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .background(Color.Black)
                        ) {
                            content()
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = captionText.ifBlank { "Nostalgic Memory" },
                            color = Color(0xFF333333),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
            "minimal_white" -> {
                Box(
                    modifier = modifier
                        .padding(8.dp)
                        .background(Color.White)
                        .border(1.5.dp, Color.LightGray.copy(alpha = 0.5f))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
            }
            "film_strip" -> {
                Box(
                    modifier = modifier
                        .padding(6.dp)
                        .background(Color(0xFF0C0C0C))
                        .border(1.dp, Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left sprockets
                        Column(
                            modifier = Modifier.width(18.dp).fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            repeat(6) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 6.dp, height = 10.dp)
                                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                                )
                            }
                        }
                        // Image
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            content()
                        }
                        // Right sprockets
                        Column(
                            modifier = Modifier.width(18.dp).fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            repeat(6) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 6.dp, height = 10.dp)
                                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                }
            }
            "neon_glow" -> {
                Box(
                    modifier = modifier
                        .padding(6.dp)
                        .border(
                            width = 3.5.dp,
                            brush = Brush.linearGradient(listOf(Color(0xFFFF007F), Color(0xFF00FFCC))),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
            }
            "royal_gold" -> {
                Box(
                    modifier = modifier
                        .padding(8.dp)
                        .border(
                            width = 6.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFD4AF37),
                                    Color(0xFFFFDF73),
                                    Color(0xFFAA7C11),
                                    Color(0xFFD4AF37)
                                )
                            ),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
            }
            else -> {
                Box(modifier = modifier, contentAlignment = Alignment.Center) {
                    content()
                }
            }
        }
    }
}
