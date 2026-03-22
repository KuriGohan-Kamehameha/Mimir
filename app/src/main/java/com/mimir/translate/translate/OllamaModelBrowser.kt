package com.mimir.translate.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class OllamaModel(
    val name: String,
    val size: Long,
    val parameterSize: String,
    val quantizationLevel: String,
    val hasVision: Boolean,
)

class OllamaModelBrowser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchModels(ollamaUrl: String): Result<List<OllamaModel>> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = ollamaUrl.trimEnd('/')

            // Step 1: List all models
            val tagsRequest = Request.Builder()
                .url("$baseUrl/api/tags")
                .build()

            val tagsResponse = client.newCall(tagsRequest).execute()
            val tagsBody = tagsResponse.body?.string()
                ?: return@withContext Result.failure(Exception("Empty response from server"))

            if (!tagsResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Server error: ${tagsResponse.code}"))
            }

            val tagsJson = JSONObject(tagsBody)
            val modelsArray = tagsJson.optJSONArray("models")
                ?: return@withContext Result.success(emptyList())

            val models = mutableListOf<OllamaModel>()

            // Step 2: For each model, call /api/show to check capabilities
            for (i in 0 until modelsArray.length()) {
                val modelObj = modelsArray.getJSONObject(i)
                val name = modelObj.getString("name")
                val size = modelObj.optLong("size", 0)
                val details = modelObj.optJSONObject("details")
                val parameterSize = details?.optString("parameter_size", "") ?: ""
                val quantizationLevel = details?.optString("quantization_level", "") ?: ""

                // Check vision capability via /api/show
                val hasVision = checkVisionCapability(baseUrl, name)

                models.add(
                    OllamaModel(
                        name = name,
                        size = size,
                        parameterSize = parameterSize,
                        quantizationLevel = quantizationLevel,
                        hasVision = hasVision,
                    )
                )
            }

            // Sort: vision models first, then by name
            models.sortWith(compareByDescending<OllamaModel> { it.hasVision }.thenBy { it.name })

            Result.success(models)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun checkVisionCapability(baseUrl: String, modelName: String): Boolean {
        return try {
            val showBody = JSONObject().apply {
                put("name", modelName)
            }

            val showRequest = Request.Builder()
                .url("$baseUrl/api/show")
                .post(showBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val showResponse = client.newCall(showRequest).execute()
            val showResponseBody = showResponse.body?.string() ?: return false
            if (!showResponse.isSuccessful) return false

            val showJson = JSONObject(showResponseBody)

            // Check capabilities array for "vision"
            val capabilities = showJson.optJSONArray("capabilities")
            if (capabilities != null) {
                for (j in 0 until capabilities.length()) {
                    if (capabilities.optString(j) == "vision") return true
                }
            }

            // Fallback: check model family/name patterns
            val details = showJson.optJSONObject("details")
            val families = details?.optJSONArray("families")
            if (families != null) {
                for (j in 0 until families.length()) {
                    val family = families.optString(j, "").lowercase()
                    if (family.contains("clip") || family.contains("vision")) return true
                }
            }

            // Name-based fallback
            val lowerName = modelName.lowercase()
            lowerName.contains("llava") ||
                lowerName.contains("bakllava") ||
                lowerName.contains("vision") ||
                lowerName.contains("moondream")
        } catch (e: Exception) {
            false
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            String.format("%.1f GB", gb)
        } else {
            val mb = bytes / (1024.0 * 1024.0)
            String.format("%.0f MB", mb)
        }
    }
}
