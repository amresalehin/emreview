import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.amresalehin.emreview.ai.AiProvider
import com.amresalehin.emreview.ai.OpenAiCompatibleProvider
import com.amresalehin.emreview.ai.VisionModel
import com.example.data.KeyValueEntry
import com.example.data.PhotoDao
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

    // Photo CRUD operations
    suspend fun getPhotoById(id: Int): Photo? {
        return photoDao.getPhotoById(id)
    }

    suspend fun insertPhoto(photo: Photo): Long {
        return photoDao.insertPhoto(photo)
    }

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
        val bitmap = loadBitmap(context, imageUrl) ?: return ruleBasedTagging(title, imageUrl)
        if (provider == null) return ruleBasedTagging(title, imageUrl)
        val prompt = """Analyze this photo for a personal gallery. Return ONLY JSON:
{"tags":["tag1","tag2","tag3"],"description":"one concise factual description"}
Use 3-12 lowercase tags. Do not invent details that are not visible."""
        return provider.analyzeImage(bitmap, prompt).fold(
            onSuccess = { a -> TaggingResult(a.tags.distinct().joinToString(", "), a.description, true) },
            onFailure = { e -> Log.w("PhotoRepository", "AI request failed; using local fallback", e); ruleBasedTagging(title, imageUrl) }
        )
    }

    suspend fun discoverVisionModels(baseUrl: String, apiKey: String): Result<List<VisionModel>> =
        OpenAiCompatibleProvider("custom", "Custom provider", baseUrl, apiKey).listVisionModels()

    private suspend fun loadBitmap(context: Context, imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            when {
                imageUrl.startsWith("content://") || imageUrl.startsWith("file://") ->
                    context.contentResolver.openInputStream(Uri.parse(imageUrl))?.use(BitmapFactory::decodeStream)
                imageUrl.startsWith("http://") || imageUrl.startsWith("https://") ->
                    URL(imageUrl).openConnection().apply { connectTimeout = 15000; readTimeout = 15000 }
                        .getInputStream().use(BitmapFactory::decodeStream)
                else -> BitmapFactory.decodeFile(imageUrl)
            }
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed to load bitmap", e)
            null
        }
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
            val isVideo = photo.imageUrl.contains("video", ignoreCase = true) || photo.tags.contains("video", ignoreCase = true)
            val fileExtension = if (isVideo) ".mp4" else ".jpg"
            val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
            val fileName = if (photo.title.lowercase().endsWith(fileExtension)) photo.title else "${photo.title}$fileExtension"

            val fileBytes = getPhotoBytes(context, photo) ?: run {
                throw Exception("Could not retrieve file bytes for '${photo.title}' from destination URI/URL")
            }

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
                .addPart(fileBytes.toRequestBody(mimeType.toMediaType()))
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

    private suspend fun getPhotoBytes(context: android.content.Context, photo: Photo): ByteArray? = withContext(Dispatchers.IO) {
        try {
            if (photo.imageUrl.startsWith("content://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(photo.imageUrl))?.use { inputStream ->
                    return@withContext inputStream.readBytes()
                }
            } else if (photo.imageUrl.startsWith("http://") || photo.imageUrl.startsWith("https://")) {
                val request = Request.Builder().url(photo.imageUrl).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return@withContext response.body?.bytes()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Failed to retrieve bytes for photo ${photo.id}", e)
        }
        null
    }
}

data class TaggingResult(
    val tags: String,
    val description: String,
    val isRealAI: Boolean
)

class UnauthorizedException(message: String, cause: Throwable? = null) : Exception(message, cause)
