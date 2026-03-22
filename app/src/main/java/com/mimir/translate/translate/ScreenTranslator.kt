package com.mimir.translate.translate

import android.graphics.Bitmap
import android.util.Base64
import android.util.LruCache
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.mimir.translate.data.models.AppSettings
import com.mimir.translate.ocr.TextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.UnknownHostException

sealed class TranslateResult {
    data class Success(val text: String) : TranslateResult()
    data class Error(val message: String) : TranslateResult()
}

class ScreenTranslator(
    private val textRecognizer: TextRecognizer,
) {

    companion object {
        const val STYLE_AUTO = 0
        const val STYLE_TRANSLATE_ONLY = 1
        const val STYLE_TRANSLATE_AND_EXPLAIN = 2
    }

    private val ollamaTranslator = OllamaTranslator()
    private val googleTranslator = GoogleTranslator()
    private val translationCache = LruCache<String, TranslateResult>(50)

    // Synchronization lock for thread-safe translator lifecycle
    private val translatorLock = Any()
    
    private var mlKitTranslator: com.google.mlkit.nl.translate.Translator? = null
    private var mlKitCurrentTargetLang: String? = null

    private fun mlKitLanguageCode(appLangCode: String): String {
        return when (appLangCode) {
            AppSettings.LANG_ENGLISH -> TranslateLanguage.ENGLISH
            AppSettings.LANG_SPANISH -> TranslateLanguage.SPANISH
            AppSettings.LANG_PORTUGUESE -> TranslateLanguage.PORTUGUESE
            AppSettings.LANG_FRENCH -> TranslateLanguage.FRENCH
            AppSettings.LANG_GERMAN -> TranslateLanguage.GERMAN
            AppSettings.LANG_ITALIAN -> TranslateLanguage.ITALIAN
            AppSettings.LANG_CHINESE -> TranslateLanguage.CHINESE
            AppSettings.LANG_KOREAN -> TranslateLanguage.KOREAN
            AppSettings.LANG_RUSSIAN -> TranslateLanguage.RUSSIAN
            else -> TranslateLanguage.ENGLISH
        }
    }

    suspend fun ensureOfflineModelReady(targetLang: String = AppSettings.LANG_ENGLISH) {
        val mlKitLang = mlKitLanguageCode(targetLang)
        
        // Double-check pattern with lock to avoid redundant initialization
        synchronized(translatorLock) {
            if (mlKitTranslator != null && mlKitCurrentTargetLang == mlKitLang) return
            
            // Close old translator if changing language
            mlKitTranslator?.close()
        }
        
        withContext(Dispatchers.IO) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.JAPANESE)
                .setTargetLanguage(mlKitLang)
                .build()
            val translator = Translation.getClient(options)
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()
            
            // Assign under lock to prevent concurrent modifications
            synchronized(translatorLock) {
                mlKitTranslator = translator
                mlKitCurrentTargetLang = mlKitLang
            }
        }
    }

    suspend fun translateScreen(
        bitmap: Bitmap,
        style: Int = STYLE_AUTO,
        model: Int = AppSettings.MODEL_MLKIT_OFFLINE,
        outputLanguage: String = AppSettings.LANG_ENGLISH,
        ollamaUrl: String = "",
        ollamaModel: String = "",
        onDownloading: (() -> Unit)? = null,
    ): TranslateResult {
        // NASA Rule 5: precondition assertions
        require(bitmap.width > 0 && bitmap.height > 0) { "Bitmap must have positive dimensions" }
        require(outputLanguage.isNotBlank()) { "Output language must not be blank" }
        return withContext(Dispatchers.IO) {
            try {
                when (model) {
                    AppSettings.MODEL_MLKIT_OFFLINE ->
                        translateOffline(bitmap, outputLanguage, onDownloading)

                    AppSettings.MODEL_GOOGLE_FREE ->
                        translateWithGoogle(bitmap, outputLanguage)

                    AppSettings.MODEL_OLLAMA ->
                        translateWithOllama(bitmap, style, outputLanguage, ollamaUrl, ollamaModel)

                    else ->
                        translateOffline(bitmap, outputLanguage, onDownloading)
                }
            } catch (e: UnknownHostException) {
                TranslateResult.Error("No internet connection")
            } catch (e: java.net.SocketTimeoutException) {
                TranslateResult.Error("Connection timed out. Try again.")
            } catch (e: Exception) {
                TranslateResult.Error("Translation failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    private suspend fun translateOffline(
        bitmap: Bitmap,
        outputLanguage: String = AppSettings.LANG_ENGLISH,
        onDownloading: (() -> Unit)? = null,
    ): TranslateResult {
        val blocks = textRecognizer.recognizeTextBlocks(bitmap)
            ?: return TranslateResult.Error("No text found in screenshot")

        if (blocks.isEmpty()) {
            return TranslateResult.Error("No text found in screenshot")
        }

        // Check if download is needed (under lock)
        val needsDownload = synchronized(translatorLock) {
            mlKitTranslator == null || mlKitCurrentTargetLang != mlKitLanguageCode(outputLanguage)
        }
        
        // Call withContext outside of synchronized block
        if (needsDownload) {
            withContext(Dispatchers.Main) { onDownloading?.invoke() }
        }

        try {
            ensureOfflineModelReady(outputLanguage)
        } catch (e: Exception) {
            return TranslateResult.Error("Download the offline model first. Connect to WiFi and try again.")
        }

        val translator = synchronized(translatorLock) { mlKitTranslator }
            ?: return TranslateResult.Error("Offline translator not available")

        val cacheKey = "offline:$outputLanguage:${blocks.joinToString("|")}"
        translationCache.get(cacheKey)?.let { return it }

        return try {
            val result = StringBuilder()
            for (block in blocks) {
                val translated = translator.translate(block).await()
                result.appendLine(block)
                result.appendLine(translated)
                result.appendLine()
            }
            TranslateResult.Success(result.toString().trimEnd()).also {
                translationCache.put(cacheKey, it)
            }
        } catch (e: Exception) {
            TranslateResult.Error("Offline translation failed: ${e.message ?: "unknown error"}")
        }
    }

    private suspend fun translateWithGoogle(
        bitmap: Bitmap,
        outputLanguage: String,
    ): TranslateResult {
        val text = textRecognizer.recognizeText(bitmap)
            ?: return TranslateResult.Error("No text found in screenshot")

        if (text.isBlank()) {
            return TranslateResult.Error("No text found in screenshot")
        }

        val cacheKey = "google:$outputLanguage:$text"
        translationCache.get(cacheKey)?.let { return it }

        return try {
            val translated = googleTranslator.translate(text, "ja", outputLanguage)
            val result = StringBuilder()
            result.appendLine(text)
            result.appendLine(translated)
            TranslateResult.Success(result.toString().trimEnd()).also {
                translationCache.put(cacheKey, it)
            }
        } catch (e: Exception) {
            TranslateResult.Error("Google Translate failed: ${e.message ?: "unknown error"}")
        }
    }

    private suspend fun translateWithOllama(
        bitmap: Bitmap,
        style: Int,
        outputLanguage: String,
        ollamaUrl: String,
        ollamaModel: String,
    ): TranslateResult {
        if (ollamaUrl.isBlank()) {
            return TranslateResult.Error("Set your Ollama server URL in Settings")
        }
        if (ollamaModel.isBlank()) {
            return TranslateResult.Error("Select an Ollama model in Settings")
        }

        val base64Image = bitmapToBase64(bitmap)
        val prompt = getSystemPrompt(style, outputLanguage)

        val result = ollamaTranslator.translate(
            base64Image = base64Image,
            systemPrompt = prompt,
            ollamaUrl = ollamaUrl,
            modelName = ollamaModel,
        )

        return if (result != null) {
            TranslateResult.Success(result)
        } else {
            TranslateResult.Error("Ollama translation failed. Check your server connection.")
        }
    }

    fun bitmapToBase64(bitmap: Bitmap): String {
        check(bitmap.width > 0 && bitmap.height > 0) { "Cannot encode empty bitmap" } // NASA Rule 5
        val stream = ByteArrayOutputStream()
        val scaled = if (bitmap.width > 1024) {
            val ratio = 1024f / bitmap.width
            Bitmap.createScaledBitmap(
                bitmap,
                1024,
                (bitmap.height * ratio).toInt(),
                true,
            )
        } else {
            bitmap
        }
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    fun getSystemPrompt(style: Int, outputLanguage: String = AppSettings.LANG_ENGLISH): String {
        val langName = AppSettings.languageDisplayName(outputLanguage)
        val baseRules = "Never be conversational. No greetings, no questions, no \"feel free to ask\", no \"let me know\". No markdown formatting."

        return when (style) {
            STYLE_TRANSLATE_ONLY -> {
                "You translate game screenshots to $langName. The text may be in any language (Japanese, Chinese, Korean, etc). " +
                    "Translate all visible text on screen. " +
                    "For menus, list each option translated. " +
                    "For dialogue, translate naturally. " +
                    "For stats, translate the labels and values. " +
                    "Only translate, do not explain or give advice. " +
                    "Always respond in $langName. " +
                    baseRules
            }
            STYLE_TRANSLATE_AND_EXPLAIN -> {
                "You are a game assistant helping someone play a game that's not in their language. The screen may be in any language (Japanese, Chinese, Korean, etc).\n\n" +
                    "Rules:\n" +
                    "- First: translate all text on screen to $langName\n" +
                    "- Then: explain what you're looking at and what you should do to progress\n" +
                    "- For menus: translate each option and recommend which to pick\n" +
                    "- For dialogue/story: translate naturally, then summarize what's happening\n" +
                    "- For gameplay/instructions: translate and explain what the game wants you to do\n" +
                    "- For stats/progress: explain the key numbers and what they mean\n" +
                    "- Talk directly to the user using \"you\" (e.g. \"you need to select...\", \"your stats are...\")\n" +
                    "- Keep it concise but useful\n" +
                    "- Always respond in $langName\n" +
                    "- $baseRules"
            }
            else -> { // AUTO
                "You are a game assistant helping someone play a game that's not in their language. The screen may be in any language (Japanese, Chinese, Korean, etc).\n\n" +
                    "Always do both:\n" +
                    "1. Translate all text on screen to $langName\n" +
                    "2. Briefly explain what you're seeing and what to do next\n\n" +
                    "- For menus: translate each option and say which one to pick to progress\n" +
                    "- For dialogue/story: translate naturally, then summarize what's happening\n" +
                    "- For gameplay/instructions: translate and explain what the game wants you to do\n" +
                    "- For stats/progress: explain the key numbers and what they mean\n" +
                    "- Talk directly to the user using \"you\" (e.g. \"you need to select...\", \"your health is...\")\n" +
                    "- Keep it concise — you just want to keep playing\n" +
                    "- Always respond in $langName\n" +
                    "- $baseRules"
            }
        }
    }
}
