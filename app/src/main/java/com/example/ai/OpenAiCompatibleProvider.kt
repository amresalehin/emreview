package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class OpenAiCompatibleProvider(
    override val id: String,
    override val displayName: String,
    baseUrl: String,
    private val apiKey: String,
    private val selectedModelId: String? = null
) : AiProvider {
    private val baseUrl = baseUrl.trim().trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun listVisionModels(): Result<List<VisionModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$baseUrl/models").headers(authHeaders()).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Model discovery failed (HTTP ${response.code}): ${body.take(400)}")
                val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
                buildList {
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val id = item.optString("id").trim()
                        val capability = visionCapability(item)
                        if (id.isNotBlank() && capability != false) {
                            add(VisionModel(id, item.optString("name").ifBlank { id }, capabilityKnown = capability == true))
                        }
                    }
                }.distinctBy { it.id }
            }
        }
    }

    override suspend fun analyzeImage(bitmap: Bitmap, prompt: String): Result<AiAnalysis> = withContext(Dispatchers.IO) {
        runCatching {
            val model = selectedModelId ?: error("No vision model selected")
            val payload = JSONObject().apply {
                put("model", model)
                put("temperature", 0.2)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray()
                        .put(JSONObject().put("type", "text").put("text", prompt))
                        .put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put("url", bitmap.toJpegDataUrl()))
                        }))
                }))
            }
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .headers(authHeaders())
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Vision request failed (HTTP ${response.code}): ${body.take(500)}")
                parseAnalysis(body)
            }
        }
    }

    private fun authHeaders(): Headers = Headers.Builder()
        .add("Authorization", "Bearer $apiKey")
        .add("Accept", "application/json")
        .build()

    private fun visionCapability(item: JSONObject): Boolean? {
        val capabilities = item.optJSONObject("capabilities")
        if (item.optBoolean("vision", false) || item.optBoolean("multimodal", false) ||
            capabilities?.optBoolean("vision", false) == true) return true

        val modalities = item.optJSONArray("modalities") ?: item.optJSONArray("input_modalities")
        if (modalities != null) {
            var hasImage = false
            var hasKnownModality = false
            for (i in 0 until modalities.length()) {
                val value = modalities.optString(i).lowercase()
                if (value.isNotBlank()) {
                    hasKnownModality = true
                    if (value.contains("image") || value.contains("vision")) hasImage = true
                }
            }
            if (hasImage) return true
            if (hasKnownModality) return false
        }

        // Unknown is retained for user choice, but is no longer assumed to support vision.
        return null
    }
    private fun parseAnalysis(body: String): AiAnalysis {
        val root = JSONObject(body)
        val raw = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.opt("content")
        val text = when (raw) {
            is String -> raw
            is JSONArray -> buildString {
                for (i in 0 until raw.length()) append(raw.optJSONObject(i)?.optString("text").orEmpty())
            }
            else -> ""
        }.trim()
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = JSONObject(cleaned)
        val array = json.optJSONArray("tags") ?: JSONArray()
        val tags = buildList {
            for (i in 0 until array.length()) add(array.optString(i).trim().lowercase())
        }.filter { it.isNotBlank() }.distinct().take(12)
        return AiAnalysis(tags, json.optString("description").trim())
    }

    private fun Bitmap.toJpegDataUrl(): String {
        val maxDimension = 1536
        var source = this
        var resized: Bitmap? = null
        val largest = maxOf(width, height)
        if (largest > maxDimension) {
            val scale = maxDimension.toFloat() / largest.toFloat()
            resized = Bitmap.createScaledBitmap(
                this,
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
                true
            )
            source = resized
        }
        return try {
            val out = ByteArrayOutputStream()
            source.compress(Bitmap.CompressFormat.JPEG, 72, out)
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            resized?.recycle()
        }
    }
}