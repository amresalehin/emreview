package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.ai.AiProvider
import com.example.ai.OpenAiCompatibleProvider
import com.example.ai.VisionModel
import com.example.data.KeyValueEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.io.File
import java.io.InputStream
import okio.BufferedSink
import java.util.concurrent.TimeUnit
class PhotoRepository(private val photoDao: PhotoDao) {

    val allPhotos: Flow<List<Photo>> = photoDao.getAllPhotos()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Key-Value operations
    suspend fun getSystemConfig(key: String): String? {
        return photoDao.getConfig(key)?.configValue
    }

    fun getSystemConfigFlow(key: String): Flow<KeyValueEntry?> {
        return photoDao.getConfigFlow(key)
    }

    suspend fun saveSystemConfig(key: String, value: String) {
        photoDao.insertConfig(KeyValueEntry(key, value))
    }

    suspend fun deleteSystemConfig(key: String) {
        photoDao.deleteConfig(key)
    }

    // Photo CRUD operations
    suspend fun getUnsyncedPhotos(): List<Photo> = photoDao.getUnsyncedPhotos()

    suspend fun permanentlyDeleteDeletedBefore(cutoff: Long): Int = photoDao.permanentlyDeleteDeletedBefore(cutoff)

    suspend fun getPhotoById(id: Int): Photo? {
        return photoDao.getPhotoById(id)
    }

    suspend fun insertPhoto(photo: Photo): Long {
        return photoDao.insertPhoto(photo)
    }

    suspend fun insertPhotos(photos: List<Photo>): List<Long> {
        if (photos.isEmpty()) return emptyList()
        return photoDao.insertPhotos(photos)
    }

    suspend fun getAllPhotoUrls(): List<String> = photoDao.getAllPhotoUrls()

    suspend fun updatePhoto(photo: Photo) {
        photoDao.updatePhoto(photo)
    }

    suspend fun updatePhotos(photos: List<Photo>) {
        photoDao.updatePhotos(photos)
    }

    suspend fun deletePhotoById(id: Int) {
        photoDao.deletePhotoById(id)
    }

    /**
     * Download an image URL as Bitmap (supported for remote web links)
     */
    suspend fun downloadImageAsBitmap(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val url = URL(imageUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            val input = connection.getInputStream()
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed to download image: $imageUrl", e)
            null
        }
    }

    suspend fun analyzePhoto(context: Context, imageUrl: String, title: String, provider: AiProvider?): TaggingResult {
        val bitmap = loadSampledBitmap(context, imageUrl, 1536) ?: return ruleBasedTagging(title, imageUrl)
        if (provider == null) {
            bitmap.recycle()
            return ruleBasedTagging(title, imageUrl)
        }
        val prompt = """Analyze this photo for a personal gallery. Return ONLY JSON:
{"tags":["tag1","tag2","tag3"],"description":"one concise factual description"}
Use 3-12 lowercase tags. Do not invent details that are not visible."""
        return try {
            provider.analyzeImage(bitmap, prompt).fold(
                onSuccess = { a -> TaggingResult(a.tags.distinct().joinToString(", "), a.description, true) },
                onFailure = { e -> Log.w("PhotoRepository", "AI request failed; using local fallback", e); ruleBasedTagging(title, imageUrl) }
            )
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    suspend fun discoverVisionModels(baseUrl: String, apiKey: String): Result<List<VisionModel>> =
        OpenAiCompatibleProvider("custom", "Custom provider", baseUrl, apiKey).listVisionModels()

    private suspend fun loadSampledBitmap(context: Context, imageUrl: String, maxDimension: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                openImageStream(context, imageUrl)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

                var sample = 1
                while ((bounds.outWidth / sample) > maxDimension || (bounds.outHeight / sample) > maxDimension) {
                    sample *= 2
                }

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                openImageStream(context, imageUrl)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                }
            } catch (e: Exception) {
                Log.e("PhotoRepository", "Failed to load sampled bitmap", e)
                null
            }
        }

    private fun openImageStream(context: Context, imageUrl: String): InputStream? =
        when {
            imageUrl.startsWith("content://") || imageUrl.startsWith("file://") ->
                context.contentResolver.openInputStream(Uri.parse(imageUrl))
            imageUrl.startsWith("http://") || imageUrl.startsWith("https://") ->
                (URL(imageUrl).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                }.getInputStream()
            else -> File(imageUrl).takeIf { it.isFile }?.inputStream()
        }

    private fun ruleBasedTagging(title: String, url: String): TaggingResult {
        val value = "$title $url".lowercase()
        val tags = linkedSetOf("auto")
        val description = when {
            listOf("portrait","selfie","person","face","man","woman","girl","boy").any(value::contains) -> { tags += listOf("person","portrait"); "Photo containing a person." }
            listOf("mountain","landscape","nature","forest","beach").any(value::contains) -> { tags += listOf("nature","outdoors","landscape"); "Outdoor scene." }
            listOf("food","sushi","pasta","restaurant").any(value::contains) -> { tags += listOf("food","dining"); "Food or dining scene." }
            else -> { tags += listOf("snapshot","gallery"); "Photo indexed locally." }
        }
        return TaggingResult(tags.joinToString(", "), description, false)
    }

    suspend fun createGDriveFolderIfNotExist(accessToken: String, folderName: String): String? = withContext(Dispatchers.IO) {
        try {
            val query = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery"
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(searchRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val json = JSONObject(responseStr)
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        return@withContext files.getJSONObject(0).optString("id")
                    }
                } else if (response.code == 401 || response.code == 403) {
                    throw UnauthorizedException("Folder search failed: HTTP ${response.code} Unauthorized")
                } else {
                    val errorStr = response.body?.string() ?: ""
                    throw Exception("Folder search failed: HTTP ${response.code} - $errorStr")
                }
            }

            val createUrl = "https://www.googleapis.com/drive/v3/files"
            val bodyJson = JSONObject().apply {
                put("name", folderName)
                put("mimeType", "application/vnd.google-apps.folder")
            }
            val createRequest = Request.Builder()
                .url(createUrl)
                .header("Authorization", "Bearer $accessToken")
                .post(bodyJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            httpClient.newCall(createRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val json = JSONObject(responseStr)
                    return@withContext json.optString("id")
                } else if (response.code == 401 || response.code == 403) {
                    throw UnauthorizedException("Folder creation failed: HTTP ${response.code} Unauthorized")
                } else {
                    val errorStr = response.body?.string() ?: ""
                    throw Exception("Folder creation failed: HTTP ${response.code} - $errorStr")
                }
            }
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Error resolving Google Drive folder", e)
            if (e is UnauthorizedException) throw e
            throw Exception("Folder resolution failed: ${e.message}", e)
        }
    }

    suspend fun uploadPhotoToGDrive(
        context: android.content.Context,
        accessToken: String,
        folderId: String,
        photo: Photo
    ): String? = withContext(Dispatchers.IO) {
        try {
            val isVideo = photo.mimeType.startsWith("video/", ignoreCase = true) || photo.tags.contains("video", ignoreCase = true)
            val fileExtension = if (isVideo) ".mp4" else ".jpg"
            val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
            val fileName = if (photo.title.lowercase().endsWith(fileExtension)) photo.title else "${photo.title}$fileExtension"

            val sanitizedTitle = fileName.replace("'", "\\'")
            val query = "name = '$sanitizedTitle' and '$folderId' in parents and trashed = false"
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery"

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(searchRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val json = JSONObject(responseStr)
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        return@withContext files.getJSONObject(0).optString("id")
                    }
                } else if (response.code == 401 || response.code == 403) {
                    throw UnauthorizedException("HTTP search check failed: Unauthorized (HTTP ${response.code})")
                }
            }

            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray().put(folderId))
            }
            val metadata = metadataJson.toString()

            val requestBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .addPart(buildStreamingRequestBody(context, photo, mimeType))
                .build()

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val json = JSONObject(responseStr)
                    return@withContext json.optString("id")
                } else if (response.code == 401 || response.code == 403) {
                    throw UnauthorizedException("Multipart upload failed: Unauthorized (HTTP ${response.code})")
                } else {
                    val errorStr = response.body?.string() ?: ""
                    throw Exception("Multipart upload failed: HTTP ${response.code} - $errorStr")
                }
            }
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Error uploading photo content to Google Drive", e)
            throw e
        }
    }

    private fun buildStreamingRequestBody(
        context: Context,
        photo: Photo,
        mimeType: String
    ): RequestBody {
        val imageUrl = photo.imageUrl
        return object : RequestBody() {
            override fun contentType() = mimeType.toMediaType()

            override fun contentLength(): Long = when {
                imageUrl.startsWith("content://") || imageUrl.startsWith("file://") ->
                    runCatching {
                        context.contentResolver.openAssetFileDescriptor(Uri.parse(imageUrl), "r")
                            ?.use { it.length } ?: -1L
                    }.getOrDefault(-1L)
                imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> -1L
                else -> File(imageUrl).takeIf { it.isFile }?.length() ?: -1L
            }

            override fun writeTo(sink: BufferedSink) {
                openImageStream(context, imageUrl)?.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        sink.write(buffer, 0, read)
                    }
                } ?: error("Unable to open '${photo.imageUrl}' for upload")
            }
        }
    }

}

data class TaggingResult(
    val tags: String,
    val description: String,
    val isRealAI: Boolean
)

class UnauthorizedException(message: String, cause: Throwable? = null) : Exception(message, cause)
