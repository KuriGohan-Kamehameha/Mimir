package com.mimir.translate

import android.app.ActivityOptions
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
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

    companion object {
        private const val EXTRA_DISPLAY_RELOCATED = "mimir_display_relocated"
    }

    lateinit var captureManager: ScreenCaptureManager
    lateinit var textRecognizer: TextRecognizer
    lateinit var tokenizer: JapaneseTokenizer
    lateinit var dictionary: DictionaryLookup
    lateinit var settings: AppSettings
    lateinit var translator: ScreenTranslator

    private fun currentDisplayId(): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            display?.displayId ?: Display.DEFAULT_DISPLAY
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.displayId
        }
    }

    private fun resolveBottomDisplayId(displayManager: DisplayManager, currentDisplayId: Int): Int? {
        val candidates = displayManager.displays
            .asSequence()
            .filter { it.displayId != Display.DEFAULT_DISPLAY }
            .filter { it.state == Display.STATE_ON }
            .sortedBy { it.displayId }
            .toList()

        val preferred = candidates.firstOrNull { (it.flags and Display.FLAG_PRESENTATION) != 0 }
            ?: candidates.firstOrNull()
            ?: return null

        return if (preferred.displayId == currentDisplayId) null else preferred.displayId
    }

    private fun relocateToBottomDisplayIfNeeded(shouldRelocate: Boolean, checkIntentGuard: Boolean): Boolean {
        if (!shouldRelocate) {
            return false
        }
        if (checkIntentGuard && intent.getBooleanExtra(EXTRA_DISPLAY_RELOCATED, false)) {
            return false
        }

        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val targetDisplayId = resolveBottomDisplayId(displayManager, currentDisplayId()) ?: return false
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = targetDisplayId
        val relaunch = android.content.Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_DISPLAY_RELOCATED, true)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(relaunch, options.toBundle())
        finish()
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        if (relocateToBottomDisplayIfNeeded(
                shouldRelocate = settings.launchBottomScreen.value || settings.lockAppToBottomScreen.value,
                checkIntentGuard = true,
            )
        ) {
            return
        }
        captureManager = ScreenCaptureManager(this)
        textRecognizer = TextRecognizer()
        translator = ScreenTranslator(textRecognizer)
        tokenizer = JapaneseTokenizer()
        dictionary = DictionaryLookup(this)
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

    override fun onResume() {
        super.onResume()
        relocateToBottomDisplayIfNeeded(
            shouldRelocate = settings.lockAppToBottomScreen.value,
            checkIntentGuard = false,
        )
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::captureManager.isInitialized) {
            captureManager.release()
        }
        if (::textRecognizer.isInitialized) {
            textRecognizer.close()
        }
    }
}
