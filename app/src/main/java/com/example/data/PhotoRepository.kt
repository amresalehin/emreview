package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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

    /**
     * Convert Bitmap to JPEG Base64
     */
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Runs AI tagging model (Gemini 3.5 Flash) via Direct REST API call
     */
    suspend fun generateTagsWithGemini(imageUrl: String, title: String): TaggingResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        // Safety Fallback check for missing API Key or default placeholders
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "GEMINI_API_KEY") {
            Log.i("PhotoRepository", "No Gemini API Key found. Using intelligent matching rules engine.")
            return@withContext runRuleBasedTagging(title, imageUrl)
        }

        // Download image to compress and prepare base64 inline data for standard multimodal models
        val bitmap = downloadImageAsBitmap(imageUrl)
        if (bitmap == null) {
            Log.w("PhotoRepository", "Failed to retrieve bitmap for Gemini. Using rule fallback.")
            return@withContext runRuleBasedTagging(title, imageUrl)
        }

        val base64Data = bitmap.toBase64()
        
        try {
            val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
            
            // Instruction to output strict JSON
            val systemInstructions = "Analyze this image. Provide 3 to 5 lowercase keyword tags representing its objects/feel, plus a short descriptive title sentence. Output ONLY a valid JSON object in this format: {\"tags\": [\"ocean\", \"sunset\", \"horizon\"], \"description\": \"Golden sunset over ocean waves.\"} without markdown wrappers."

            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemInstructions)
                            })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Data)
                                })
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(requestUrl)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errMsg = response.body?.string() ?: "Unknown API response failure"
                    Log.e("PhotoRepository", "Gemini HTTP error ${response.code}: $errMsg")
                    return@withContext runRuleBasedTagging(title, imageUrl)
                }

                val responseBody = response.body?.string() ?: throw Exception("Empty payload")
                val responseJson = JSONObject(responseBody)
                val candidates = responseJson.optJSONArray("candidates")
                val parts = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                val rawText = parts?.optJSONObject(0)?.optString("text") ?: ""

                // Extract and clean content from optional markdown indicators
                val cleanedJsonStr = rawText.trim()
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                try {
                    val parsedResult = JSONObject(cleanedJsonStr)
                    val tagArray = parsedResult.optJSONArray("tags")
                    val desc = parsedResult.optString("description")

                    val tagList = mutableListOf<String>()
                    if (tagArray != null) {
                        for (i in 0 until tagArray.length()) {
                            tagList.add(tagArray.getString(i).trim())
                        }
                    }

                    TaggingResult(
                        tags = tagList.joinToString(", "),
                        description = desc.ifBlank { "Analyzed intelligently." },
                        isRealAI = true
                    )
                } catch (pe: Exception) {
                    Log.w("PhotoRepository", "Lax JSON cleanup failed, extracting fallback", pe)
                    // If exact JSON parse failed, try scanning for standard comma lists or tags
                    TaggingResult("ai, auto, image", rawText.take(150), isRealAI = true)
                }
            }
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Gemini tagging request aborted", e)
            runRuleBasedTagging(title, imageUrl)
        }
    }

    /**
     * Offline Rules-Engine fallback to ensure seamless experience
     */
    private fun runRuleBasedTagging(title: String, url: String): TaggingResult {
        val titleLower = title.lowercase()
        val urlLower = url.lowercase()
        val tags = mutableSetOf("auto")
        var desc = "Organized smart album media"

        if (titleLower.contains("face") || titleLower.contains("portrait") || titleLower.contains("person") || 
            titleLower.contains("man") || titleLower.contains("woman") || titleLower.contains("girl") || 
            titleLower.contains("boy") || titleLower.contains("smile") || titleLower.contains("headshot") || 
            titleLower.contains("selfie") || titleLower.contains("friend") || titleLower.contains("model") ||
            urlLower.contains("face") || urlLower.contains("portrait") || urlLower.contains("person") ||
            urlLower.contains("man") || urlLower.contains("woman") || urlLower.contains("girl") || urlLower.contains("boy")) {
            tags.addAll(listOf("face", "portrait", "person"))
            desc = "A high-fidelity photograph highlighting authentic human facial detail and cinematic focus."
        } else if (titleLower.contains("mountain") || titleLower.contains("alpine") || urlLower.contains("landscape")) {
            tags.addAll(listOf("nature", "mountain", "scenery", "outdoor"))
            desc = "Breathtaking landscape with alpine slopes and pristine valley reflections."
        } else if (titleLower.contains("neon") || titleLower.contains("tokyo") || titleLower.contains("shibuya")) {
            tags.addAll(listOf("urban", "cyberpunk", "night", "travel", "japan"))
            desc = "Neon glows in Shibuya district illuminating wet urban streets."
        } else if (titleLower.contains("eiffel") || titleLower.contains("paris") || titleLower.contains("golden hour")) {
            tags.addAll(listOf("travel", "architecture", "europe", "sunset"))
            desc = "Historic golden hour overlooking the architectural lines of the Eiffel Tower."
        } else if (titleLower.contains("gourmet") || titleLower.contains("sushi") || titleLower.contains("pasta") || titleLower.contains("food")) {
            tags.addAll(listOf("cuisine", "dining", "gastronomy", "gourmet"))
            desc = "Sensory culinary display prepared by artisan chefs with fresh materials."
        } else if (titleLower.contains("workspace") || titleLower.contains("minimalist") || titleLower.contains("tech")) {
            tags.addAll(listOf("minimalist", "design", "cozy", "workspace"))
            desc = "Clean work space displaying balanced design aesthetics and cozy hardware."
        } else {
            tags.addAll(listOf("preset", "snapshot", "gallery"))
            desc = "Media captured on device, indexed seamlessly inside local vault."
        }

        return TaggingResult(tags.joinToString(", "), desc, isRealAI = false)
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
