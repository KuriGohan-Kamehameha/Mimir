package com.mimir.translate.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GoogleTranslator {
    companion object {
        private const val TAG = "Mimir"
        private const val MAX_RETRIES = 2
        private const val INITIAL_RETRY_DELAY_MS = 500L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun translate(text: String, sourceLang: String, targetLang: String): String {
        // NASA Rule 5: precondition assertions
        require(text.isNotBlank()) { "Text to translate must not be blank" }
        require(sourceLang.isNotBlank()) { "Source language must not be blank" }
        require(targetLang.isNotBlank()) { "Target language must not be blank" }
        return withContext(Dispatchers.IO) {
            translateWithRetry(text, sourceLang, targetLang, attemptCount = 0)
        }
    }

    private suspend fun translateWithRetry(
        text: String,
        sourceLang: String,
        targetLang: String,
        attemptCount: Int,
    ): String {
        return try {
            translateOnce(text, sourceLang, targetLang)
        } catch (e: Exception) {
            if (attemptCount < MAX_RETRIES) {
                val delayMs = INITIAL_RETRY_DELAY_MS * (1 shl attemptCount)  // Exponential backoff
                kotlinx.coroutines.delay(delayMs)
                translateWithRetry(text, sourceLang, targetLang, attemptCount + 1)
            } else {
                throw IOException("Translation failed after $MAX_RETRIES retries: ${e.message}", e)
            }
        }
    }

    private suspend fun translateOnce(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): String {
        return withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single" +
                "?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encoded"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Android)")  // Mimic real user agent
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body?.string() ?: throw IOException("Empty response body")

                // Response: [[["translated","original",...], ...], null, "ja", ...]
                val chunks = JSONArray(body).getJSONArray(0)
                val sb = StringBuilder()
                for (i in 0 until chunks.length()) {
                    val chunk = chunks.optJSONArray(i)
                    if (chunk != null) sb.append(chunk.optString(0))
                }
                val result = sb.toString()
                if (result.isBlank()) throw IOException("Blank translation in response")
                result
            }
        }
    }
}
