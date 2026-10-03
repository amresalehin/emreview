package com.amresalehin.emreview.ai

import android.graphics.Bitmap

interface AiProvider {
    val id: String
    val displayName: String
    suspend fun listVisionModels(): Result<List<VisionModel>>
    suspend fun analyzeImage(bitmap: Bitmap, prompt: String): Result<AiAnalysis>
}

data class VisionModel(val id: String, val name: String = id)
data class AiAnalysis(val tags: List<String>, val description: String)
