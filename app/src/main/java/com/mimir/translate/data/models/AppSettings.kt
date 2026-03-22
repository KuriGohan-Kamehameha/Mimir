package com.mimir.translate.data.models

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppSettings(context: Context) {

    companion object {
        private const val PREFS_NAME = "mimir_prefs"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_APP_MODE = "app_mode"
        private const val KEY_TRANSLATE_STYLE = "translate_style"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_OUTPUT_LANGUAGE = "output_language"
        private const val KEY_CROP_LEFT = "crop_left"
        private const val KEY_CROP_TOP = "crop_top"
        private const val KEY_CROP_RIGHT = "crop_right"
        private const val KEY_CROP_BOTTOM = "crop_bottom"
        private const val KEY_CROP_ENABLED = "crop_enabled"
        private const val KEY_OLLAMA_URL = "ollama_url"
        private const val KEY_OLLAMA_MODEL = "ollama_model"
        private const val KEY_AUTO_MODE_REFRESH = "auto_mode_refresh"
        private const val KEY_THEME_MODE = "theme_mode"

        const val TEXT_SIZE_SMALL = 0
        const val TEXT_SIZE_MEDIUM = 1
        const val TEXT_SIZE_LARGE = 2

        // Auto mode refresh rates (milliseconds)
        const val AUTO_REFRESH_FAST = 500
        const val AUTO_REFRESH_NORMAL = 1000
        const val AUTO_REFRESH_SLOW = 2000
        const val AUTO_REFRESH_SLOWEST = 4000

        val AUTO_REFRESH_RATES = listOf(
            AUTO_REFRESH_FAST to "Very Fast (500ms)",
            AUTO_REFRESH_NORMAL to "Normal (1s)",
            AUTO_REFRESH_SLOW to "Slow (2s)",
            AUTO_REFRESH_SLOWEST to "Very Slow (4s)",
        )

        fun autoRefreshLabel(ms: Int): String =
            AUTO_REFRESH_RATES.firstOrNull { it.first == ms }?.second ?: "Normal (1s)"

        // Theme modes
        const val THEME_DARK = 0
        const val THEME_LIGHT = 1
        const val THEME_AUTO = 2

        val THEME_MODES = listOf(
            THEME_DARK to "Dark",
            THEME_LIGHT to "Light",
            THEME_AUTO to "Auto (System)",
        )

        fun themeModeLabel(mode: Int): String =
            THEME_MODES.firstOrNull { it.first == mode }?.second ?: "Dark"

        const val MODE_DICTIONARY = 0
        const val MODE_TRANSLATE = 1

        const val TRANSLATE_STYLE_AUTO = 0
        const val TRANSLATE_STYLE_TRANSLATE_ONLY = 1
        const val TRANSLATE_STYLE_TRANSLATE_AND_EXPLAIN = 2

        const val MODEL_MLKIT_OFFLINE = 0
        const val MODEL_MLKIT_OFFLINE_AUTO = 1
        const val MODEL_GOOGLE_FREE = 2
        const val MODEL_OLLAMA = 3

        const val LANG_ENGLISH = "en"
        const val LANG_SPANISH = "es"
        const val LANG_PORTUGUESE = "pt"
        const val LANG_FRENCH = "fr"
        const val LANG_GERMAN = "de"
        const val LANG_ITALIAN = "it"
        const val LANG_CHINESE = "zh"
        const val LANG_KOREAN = "ko"
        const val LANG_RUSSIAN = "ru"

        val OUTPUT_LANGUAGES = listOf(
            LANG_ENGLISH to "English",
            LANG_SPANISH to "Spanish",
            LANG_PORTUGUESE to "Portuguese",
            LANG_FRENCH to "French",
            LANG_GERMAN to "German",
            LANG_ITALIAN to "Italian",
            LANG_CHINESE to "Chinese",
            LANG_KOREAN to "Korean",
            LANG_RUSSIAN to "Russian",
        )

        fun languageDisplayName(code: String): String =
            OUTPUT_LANGUAGES.firstOrNull { it.first == code }?.second ?: "English"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _textSize = MutableStateFlow(prefs.getInt(KEY_TEXT_SIZE, TEXT_SIZE_MEDIUM))
    val textSize: StateFlow<Int> = _textSize

    private val _appMode = MutableStateFlow(prefs.getInt(KEY_APP_MODE, MODE_TRANSLATE))
    val appMode: StateFlow<Int> = _appMode

    private val _translateStyle = MutableStateFlow(prefs.getInt(KEY_TRANSLATE_STYLE, TRANSLATE_STYLE_AUTO))
    val translateStyle: StateFlow<Int> = _translateStyle

    private val _aiModel = MutableStateFlow(prefs.getInt(KEY_AI_MODEL, MODEL_MLKIT_OFFLINE))
    val aiModel: StateFlow<Int> = _aiModel

    private val _outputLanguage = MutableStateFlow(prefs.getString(KEY_OUTPUT_LANGUAGE, LANG_ENGLISH) ?: LANG_ENGLISH)
    val outputLanguage: StateFlow<String> = _outputLanguage

    private val _cropEnabled = MutableStateFlow(prefs.getBoolean(KEY_CROP_ENABLED, false))
    val cropEnabled: StateFlow<Boolean> = _cropEnabled

    data class CropRegion(val left: Float, val top: Float, val right: Float, val bottom: Float)
    
    // Use single StateFlow for atomic crop region updates (prevents partial reads)
    private val _cropRegion = MutableStateFlow<CropRegion?>(
        if (prefs.getBoolean(KEY_CROP_ENABLED, false)) {
            CropRegion(
                prefs.getFloat(KEY_CROP_LEFT, 0f),
                prefs.getFloat(KEY_CROP_TOP, 0f),
                prefs.getFloat(KEY_CROP_RIGHT, 1f),
                prefs.getFloat(KEY_CROP_BOTTOM, 1f)
            )
        } else {
            null
        }
    )
    val cropRegion: StateFlow<CropRegion?> = _cropRegion

    private val _ollamaUrl = MutableStateFlow(prefs.getString(KEY_OLLAMA_URL, "") ?: "")
    val ollamaUrl: StateFlow<String> = _ollamaUrl

    private val _ollamaModel = MutableStateFlow(prefs.getString(KEY_OLLAMA_MODEL, "") ?: "")
    val ollamaModel: StateFlow<String> = _ollamaModel

    private val _autoModeRefresh = MutableStateFlow(prefs.getInt(KEY_AUTO_MODE_REFRESH, AUTO_REFRESH_NORMAL))
    val autoModeRefresh: StateFlow<Int> = _autoModeRefresh

    private val _themeMode = MutableStateFlow(prefs.getInt(KEY_THEME_MODE, THEME_DARK))
    val themeMode: StateFlow<Int> = _themeMode

    fun setCropRegion(left: Float, top: Float, right: Float, bottom: Float) {
        // NASA Rule 5: precondition assertions
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f) { "Crop values must be in [0,1]" }
        require(left < right && top < bottom) { "Crop region must have positive area" }
        
        // Atomic update: set both region and enabled flag together
        val region = CropRegion(left, top, right, bottom)
        _cropRegion.value = region
        _cropEnabled.value = true
        
        // Persist to SharedPreferences
        prefs.edit()
            .putBoolean(KEY_CROP_ENABLED, true)
            .putFloat(KEY_CROP_LEFT, left)
            .putFloat(KEY_CROP_TOP, top)
            .putFloat(KEY_CROP_RIGHT, right)
            .putFloat(KEY_CROP_BOTTOM, bottom)
            .apply()
    }

    fun clearCropRegion() {
        _cropRegion.value = null
        _cropEnabled.value = false
        prefs.edit().putBoolean(KEY_CROP_ENABLED, false).apply()
    }

    fun setTextSize(size: Int) {
        require(size in TEXT_SIZE_SMALL..TEXT_SIZE_LARGE) { "Invalid text size: $size" } // NASA Rule 5
        _textSize.value = size
        prefs.edit().putInt(KEY_TEXT_SIZE, size).apply()
    }

    fun setAppMode(mode: Int) {
        require(mode in MODE_DICTIONARY..MODE_TRANSLATE) { "Invalid app mode: $mode" } // NASA Rule 5
        _appMode.value = mode
        prefs.edit().putInt(KEY_APP_MODE, mode).apply()
    }

    fun setTranslateStyle(style: Int) {
        _translateStyle.value = style
        prefs.edit().putInt(KEY_TRANSLATE_STYLE, style).apply()
    }

    fun setAiModel(model: Int) {
        require(model in MODEL_MLKIT_OFFLINE..MODEL_OLLAMA) { "Invalid AI model: $model" } // NASA Rule 5
        _aiModel.value = model
        prefs.edit().putInt(KEY_AI_MODEL, model).apply()
    }

    fun setOutputLanguage(lang: String) {
        _outputLanguage.value = lang
        prefs.edit().putString(KEY_OUTPUT_LANGUAGE, lang).apply()
    }

    fun setOllamaUrl(url: String) {
        _ollamaUrl.value = url
        prefs.edit().putString(KEY_OLLAMA_URL, url).apply()
    }

    fun setOllamaModel(model: String) {
        _ollamaModel.value = model
        prefs.edit().putString(KEY_OLLAMA_MODEL, model).apply()
    }

    fun setAutoModeRefresh(refreshMs: Int) {
        require(refreshMs in listOf(AUTO_REFRESH_FAST, AUTO_REFRESH_NORMAL, AUTO_REFRESH_SLOW, AUTO_REFRESH_SLOWEST)) { "Invalid refresh rate: $refreshMs" }
        _autoModeRefresh.value = refreshMs
        prefs.edit().putInt(KEY_AUTO_MODE_REFRESH, refreshMs).apply()
    }

    fun setThemeMode(mode: Int) {
        require(mode in THEME_DARK..THEME_AUTO) { "Invalid theme mode: $mode" }
        _themeMode.value = mode
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
    }
}
