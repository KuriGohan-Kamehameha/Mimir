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

class OllamaTranslator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun translate(
        base64Image: String,
        systemPrompt: String,
        ollamaUrl: String,
        modelName: String,
    ): String? {
        // NASA Rule 5: precondition assertions
        require(base64Image.isNotBlank()) { "base64Image must not be blank" }
        require(ollamaUrl.isNotBlank()) { "ollamaUrl must not be blank" }
        require(modelName.isNotBlank()) { "modelName must not be blank" }
        return withContext(Dispatchers.IO) {
            val url = ollamaUrl.trimEnd('/') + "/v1/chat/completions"

            val body = JSONObject().apply {
                put("model", modelName)
                put("max_tokens", 1000)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", "Translate this game screen.")
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                })
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            // NASA Rule 7: explicitly check and handle all return values
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (responseBody == null) return@withContext null
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) return@withContext null

            val message = choices.getJSONObject(0).optJSONObject("message")
                ?: return@withContext null
            message.optString("content").ifBlank { null }
        }
    }
}
