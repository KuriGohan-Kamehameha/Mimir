package com.mimir.translate

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.mimir.translate.analysis.DictionaryLookup
import com.mimir.translate.analysis.JapaneseTokenizer
import com.mimir.translate.capture.ScreenCaptureManager
import com.mimir.translate.data.models.AppSettings
import com.mimir.translate.data.models.CaptureState
import com.mimir.translate.ocr.TextRecognizer
import com.mimir.translate.translate.ScreenTranslator
import com.mimir.translate.ui.screens.CropScreen
import com.mimir.translate.ui.screens.HelpScreen
import com.mimir.translate.ui.screens.MainScreen
import com.mimir.translate.ui.screens.SettingsScreen
import com.mimir.translate.ui.theme.MimirTheme

class MainActivity : ComponentActivity() {

    lateinit var captureManager: ScreenCaptureManager
    lateinit var textRecognizer: TextRecognizer
    lateinit var tokenizer: JapaneseTokenizer
    lateinit var dictionary: DictionaryLookup
    lateinit var settings: AppSettings
    lateinit var translator: ScreenTranslator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureManager = ScreenCaptureManager(this)
        textRecognizer = TextRecognizer()
        translator = ScreenTranslator(textRecognizer)
        tokenizer = JapaneseTokenizer()
        dictionary = DictionaryLookup(this)
        settings = AppSettings(this)
        lifecycleScope.launch { dictionary.loadAsync() }
        enableEdgeToEdge()
        setContent {
            val themeMode by settings.themeMode.collectAsState()
            MimirTheme(themeMode = themeMode) {
                var currentScreen by remember { mutableStateOf("main") }
                var dictionaryState by remember { mutableStateOf<CaptureState>(CaptureState.Idle) }
                var translateState by remember { mutableStateOf<CaptureState>(CaptureState.Idle) }
                var cropScreenshot by remember { mutableStateOf<Bitmap?>(null) }

                when (currentScreen) {
                    "settings" -> SettingsScreen(
                        settings = settings,
                        onBack = { currentScreen = "main" },
                    )
                    "help" -> HelpScreen(
                        onBack = { currentScreen = "main" },
                    )
                    "crop" -> {
                        val bmp = cropScreenshot
                        if (bmp != null) {
                            CropScreen(
                                screenshot = bmp,
                                settings = settings,
                                onSave = { currentScreen = "main" },
                                onCancel = { currentScreen = "main" },
                            )
                        } else {
                            currentScreen = "main"
                        }
                    }
                    else -> MainScreen(
                        captureManager = captureManager,
                        textRecognizer = textRecognizer,
                        tokenizer = tokenizer,
                        dictionary = dictionary,
                        translator = translator,
                        settings = settings,
                        dictionaryState = dictionaryState,
                        translateState = translateState,
                        onDictionaryStateChange = { dictionaryState = it },
                        onTranslateStateChange = { translateState = it },
                        onSettingsClick = { currentScreen = "settings" },
                        onHelpClick = { currentScreen = "help" },
                        onCropClick = { bitmap ->
                            cropScreenshot = bitmap
                            currentScreen = "crop"
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        captureManager.release()
        textRecognizer.close()
    }
}
