package com.mimir.translate.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mimir.translate.analysis.DictionaryLookup
import com.mimir.translate.analysis.JapaneseTokenizer
import com.mimir.translate.capture.ScreenCaptureManager
import com.mimir.translate.capture.ScreenCaptureService
import com.mimir.translate.data.models.AnalysisResult
import com.mimir.translate.data.models.AppSettings
import com.mimir.translate.data.models.CaptureState
import com.mimir.translate.data.models.TranslationResult
import com.mimir.translate.ocr.TextRecognizer
import com.mimir.translate.translate.ScreenTranslator
import com.mimir.translate.translate.TranslateResult
import com.mimir.translate.ui.components.CaptureButton
import com.mimir.translate.ui.components.TranslationResultView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

// NASA Rule 2: all loops must have fixed upper bounds
private const val MAX_AUTO_CYCLES = 10_000

/** NASA Rule 4: extracted text change detection as a top-level pure function. */
private fun isSignificantChange(oldText: String, newText: String): Boolean {
    if (oldText.isEmpty()) return true
    val diff = kotlin.math.abs(oldText.length - newText.length)
    if (diff > 3) return true
    val maxLen = maxOf(oldText.length, newText.length)
    if (maxLen == 0) return false
    var matches = 0
    for (i in 0 until minOf(oldText.length, newText.length)) {
        if (oldText[i] == newText[i]) matches++
    }
    return matches.toFloat() / maxLen < 0.8f
}

/** NASA Rule 4: extracted projection result handler as a top-level function. */
private fun handleProjectionResult(
    resultCode: Int,
    data: Intent?,
    context: android.content.Context,
    captureManager: ScreenCaptureManager,
    scope: CoroutineScope,
    pendingCrop: Boolean,
    pendingAuto: Boolean,
    onCropClick: (Bitmap) -> Unit,
    onCaptureStateChange: (CaptureState) -> Unit,
    clearPendingCrop: () -> Unit,
    clearPendingAuto: () -> Unit,
    startAutoMode: () -> Unit,
    doCapture: () -> Unit,
) {
    if (resultCode != Activity.RESULT_OK || data == null) {
        clearPendingCrop()
        clearPendingAuto()
        onCaptureStateChange(CaptureState.Error("Permission denied"))
        return
    }
    ScreenCaptureService.captureManager = captureManager
    val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
        putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
        putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
    }
    ContextCompat.startForegroundService(context, serviceIntent)
    when {
        pendingCrop -> {
            clearPendingCrop()
            captureManager.awaitProjectionReady {
                scope.launch {
                    val bmp = captureManager.captureScreen()
                    if (bmp != null) onCropClick(bmp)
                }
            }
        }
        pendingAuto -> {
            clearPendingAuto()
            captureManager.awaitProjectionReady { startAutoMode() }
        }
        else -> {
            onCaptureStateChange(CaptureState.Capturing)
            captureManager.awaitProjectionReady { doCapture() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    captureManager: ScreenCaptureManager,
    textRecognizer: TextRecognizer,
    tokenizer: JapaneseTokenizer,
    dictionary: DictionaryLookup,
    translator: ScreenTranslator,
    settings: AppSettings,
    dictionaryState: CaptureState,
    translateState: CaptureState,
    onDictionaryStateChange: (CaptureState) -> Unit,
    onTranslateStateChange: (CaptureState) -> Unit,
    onSettingsClick: () -> Unit,
    onHelpClick: () -> Unit,
    onCropClick: (Bitmap) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val textSize by settings.textSize.collectAsState()
    val appMode by settings.appMode.collectAsState()
    val translateStyle by settings.translateStyle.collectAsState()
    val aiModel by settings.aiModel.collectAsState()
    val outputLanguage by settings.outputLanguage.collectAsState()
    val cropEnabled by settings.cropEnabled.collectAsState()
    val cropRegion by settings.cropRegion.collectAsState()
    val ollamaUrl by settings.ollamaUrl.collectAsState()
    val ollamaModelName by settings.ollamaModel.collectAsState()
    val autoModeRefreshMs by settings.autoModeRefresh.collectAsState()

    val isAutoMode = aiModel == AppSettings.MODEL_MLKIT_OFFLINE_AUTO
    var autoJob by remember { mutableStateOf<Job?>(null) }
    var lastOcrText by remember { mutableStateOf<String?>(null) }
    
    // Lock for thread-safe auto mode job management
    val autoJobLock = remember { Object() }

    fun stopAutoMode() {
        synchronized(autoJobLock) {
            autoJob?.cancel()
            autoJob = null
        }
        lastOcrText = null
        settings.setAiModel(AppSettings.MODEL_MLKIT_OFFLINE)
    }

    fun cropBitmap(bitmap: Bitmap): Bitmap {
        if (!cropEnabled || cropRegion == null) return bitmap
        val region = cropRegion!!
        val x = (region.left * bitmap.width).toInt().coerceIn(0, bitmap.width)
        val y = (region.top * bitmap.height).toInt().coerceIn(0, bitmap.height)
        val w = ((region.right - region.left) * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
        val h = ((region.bottom - region.top) * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }

    val captureState = if (appMode == AppSettings.MODE_TRANSLATE) translateState else dictionaryState
    val onCaptureStateChange: (CaptureState) -> Unit = if (appMode == AppSettings.MODE_TRANSLATE) {
        onTranslateStateChange
    } else {
        onDictionaryStateChange
    }

    fun doDictionaryCapture() {
        scope.launch {
            onDictionaryStateChange(CaptureState.Capturing)
            val fullBitmap = captureManager.captureScreen()
            if (fullBitmap == null) {
                onDictionaryStateChange(CaptureState.Error("Failed to capture screen"))
                return@launch
            }
            val bitmap = cropBitmap(fullBitmap)
            onDictionaryStateChange(CaptureState.Processing)
            val recognizedText = textRecognizer.recognizeText(bitmap)
            if (recognizedText != null) {
                val tokens = tokenizer.tokenize(recognizedText)
                val words = dictionary.lookupTokens(tokens).map { word ->
                    word.copy(romaji = tokenizer.toRomaji(word.reading))
                }
                onDictionaryStateChange(CaptureState.DictionarySuccess(
                    AnalysisResult(originalText = recognizedText, words = words)
                ))
            } else {
                onDictionaryStateChange(CaptureState.Error("No Japanese text found in screenshot"))
            }
        }
    }

    fun doTranslateCapture() {
        scope.launch {
            onTranslateStateChange(CaptureState.Capturing)
            val fullBitmap = captureManager.captureScreen()
            if (fullBitmap == null) {
                onTranslateStateChange(CaptureState.Error("Failed to capture screen"))
                return@launch
            }
            val bitmap = cropBitmap(fullBitmap)
            onTranslateStateChange(CaptureState.Processing)
            when (val result = translator.translateScreen(
                bitmap = bitmap,
                style = translateStyle,
                model = aiModel,
                outputLanguage = outputLanguage,
                ollamaUrl = ollamaUrl,
                ollamaModel = ollamaModelName,
                onDownloading = { onTranslateStateChange(CaptureState.DownloadingModel) },
            )) {
                is TranslateResult.Success -> onTranslateStateChange(
                    CaptureState.TranslateSuccess(TranslationResult(translation = result.text)),
                )
                is TranslateResult.Error -> onTranslateStateChange(CaptureState.Error(result.message))
            }
        }
    }

    fun doCapture() {
        if (appMode == AppSettings.MODE_TRANSLATE) doTranslateCapture() else doDictionaryCapture()
    }

    fun doAutoTranslateCycle() {
        scope.launch {
            val fullBitmap = captureManager.captureScreen() ?: return@launch
            val bitmap = cropBitmap(fullBitmap)
            val blocks = textRecognizer.recognizeTextBlocks(bitmap)
            if (blocks.isNullOrEmpty()) return@launch
            val currentText = blocks.joinToString("").filter { c -> c.code > 0x3000 }
            if (currentText.isEmpty()) return@launch
            if (!isSignificantChange(lastOcrText ?: "", currentText)) {
                return@launch
            }
            lastOcrText = currentText
            onTranslateStateChange(CaptureState.Processing)
            when (val result = translator.translateScreen(
                bitmap = bitmap, style = AppSettings.TRANSLATE_STYLE_AUTO,
                model = AppSettings.MODEL_MLKIT_OFFLINE, outputLanguage = outputLanguage,
                onDownloading = { onTranslateStateChange(CaptureState.DownloadingModel) },
            )) {
                is TranslateResult.Success -> onTranslateStateChange(
                    CaptureState.TranslateSuccess(TranslationResult(translation = result.text)),
                )
                is TranslateResult.Error -> onTranslateStateChange(CaptureState.Error(result.message))
            }
        }
    }

    fun startAutoMode() {
        synchronized(autoJobLock) {
            if (autoJob?.isActive == true) return
            lastOcrText = null
            autoJob = scope.launch {
                for (cycle in 0 until MAX_AUTO_CYCLES) {
                    if (captureManager.isReady) doAutoTranslateCycle()
                    delay(autoModeRefreshMs.toLong())
                }
                stopAutoMode()
            }
        }
    }

    var pendingAutoAfterPermission by remember { mutableStateOf(false) }
    var pendingCropAfterPermission by remember { mutableStateOf(false) }

    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleProjectionResult(
            result.resultCode, result.data, context, captureManager, scope,
            pendingCropAfterPermission, pendingAutoAfterPermission,
            onCropClick, onCaptureStateChange,
            { pendingCropAfterPermission = false }, { pendingAutoAfterPermission = false },
            ::startAutoMode, ::doCapture,
        )
    }

    fun onCaptureClick() {
        if (captureManager.isReady) {
            doCapture()
        } else {
            pendingCropAfterPermission = false
            projectionLauncher.launch(captureManager.projectionManager.createScreenCaptureIntent())
        }
    }

    fun onCropRegionClick() {
        if (captureManager.isReady) {
            scope.launch {
                val bmp = captureManager.captureScreen()
                if (bmp != null) onCropClick(bmp)
            }
        } else {
            pendingCropAfterPermission = true
            projectionLauncher.launch(captureManager.projectionManager.createScreenCaptureIntent())
        }
    }

    Scaffold(
        topBar = {
            MimirTopBar(
                cropEnabled = cropEnabled, aiModel = aiModel,
                ollamaModelName = ollamaModelName, settings = settings,
                captureManager = captureManager, projectionLauncher = projectionLauncher,
                onCropRegionClick = ::onCropRegionClick,
                onHelpClick = onHelpClick, onSettingsClick = onSettingsClick,
                onStopAutoMode = ::stopAutoMode, onStartAutoMode = ::startAutoMode,
                onSetPendingAuto = { pendingAutoAfterPermission = it },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            ModeToggle(currentMode = appMode, onModeChange = { settings.setAppMode(it) })
            CaptureStateContent(
                captureState = captureState, appMode = appMode, aiModel = aiModel,
                outputLanguage = outputLanguage, textSize = textSize,
                modifier = Modifier.weight(1f).padding(top = 8.dp),
            )
            CaptureButton(
                isProcessing = captureState is CaptureState.Capturing
                    || captureState is CaptureState.DownloadingModel
                    || captureState is CaptureState.Processing,
                onClick = { onCaptureClick() },
                modifier = Modifier.padding(bottom = 16.dp),
                isAutoMode = isAutoMode,
                onStopAuto = { stopAutoMode() },
            )
        }
    }
}

// --- Extracted composables (NASA Rule 4: each <=60 lines) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MimirTopBar(
    cropEnabled: Boolean,
    aiModel: Int,
    ollamaModelName: String,
    settings: AppSettings,
    captureManager: ScreenCaptureManager,
    projectionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onCropRegionClick: () -> Unit,
    onHelpClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onStopAutoMode: () -> Unit,
    onStartAutoMode: () -> Unit,
    onSetPendingAuto: (Boolean) -> Unit,
) {
    TopAppBar(
        title = { Text(text = "Mimir", fontWeight = FontWeight.Bold) },
        actions = {
            CropButton(cropEnabled, onCropRegionClick, { settings.clearCropRegion() })
            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            ModelSelector(
                aiModel, ollamaModelName, settings, captureManager, projectionLauncher,
                onStopAutoMode, onStartAutoMode, onSetPendingAuto,
            )
            IconButton(onClick = onHelpClick) {
                Text("?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
            IconButton(onClick = onSettingsClick) {
                Text("\u2699", fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

@Composable
private fun CropButton(cropEnabled: Boolean, onClick: () -> Unit, onClear: () -> Unit) {
    if (!cropEnabled) {
        // Simple "Full" button when no region is set
        Text(
            text = "Full",
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    } else {
        // When region is set: show "Region" | "Clear" buttons side-by-side
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Text(
                text = "Region",
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onClick() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )
            // Clear button - more prominent and visible
            Text(
                text = "Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable { onClear() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ModelSelector(
    aiModel: Int,
    ollamaModelName: String,
    settings: AppSettings,
    captureManager: ScreenCaptureManager,
    projectionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onStopAutoMode: () -> Unit,
    onStartAutoMode: () -> Unit,
    onSetPendingAuto: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (aiModel) {
        AppSettings.MODEL_MLKIT_OFFLINE -> "Offline"
        AppSettings.MODEL_MLKIT_OFFLINE_AUTO -> "Auto"
        AppSettings.MODEL_GOOGLE_FREE -> "Google"
        AppSettings.MODEL_OLLAMA -> "Ollama"
        else -> "Offline"
    }
    Box {
        Text(
            text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        ModelDropdownMenu(
            expanded, { expanded = false }, ollamaModelName, settings,
            captureManager, projectionLauncher, onStopAutoMode, onStartAutoMode, onSetPendingAuto,
        )
    }
}

@Composable
private fun ModelDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    ollamaModelName: String,
    settings: AppSettings,
    captureManager: ScreenCaptureManager,
    projectionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onStopAutoMode: () -> Unit,
    onStartAutoMode: () -> Unit,
    onSetPendingAuto: (Boolean) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Offline") }, onClick = {
            onStopAutoMode(); settings.setAiModel(AppSettings.MODEL_MLKIT_OFFLINE); onDismiss()
        })
        DropdownMenuItem(text = { Text("Offline Auto") }, onClick = {
            settings.setAiModel(AppSettings.MODEL_MLKIT_OFFLINE_AUTO); onDismiss()
            if (captureManager.isReady) {
                onStartAutoMode()
            } else {
                onSetPendingAuto(true)
                projectionLauncher.launch(captureManager.projectionManager.createScreenCaptureIntent())
            }
        })
        DropdownMenuItem(text = { Text("Google Translate") }, onClick = {
            onStopAutoMode(); settings.setAiModel(AppSettings.MODEL_GOOGLE_FREE); onDismiss()
        })
        DropdownMenuItem(
            text = { Text(if (ollamaModelName.isNotEmpty()) "Ollama ($ollamaModelName)" else "Ollama (LAN)") },
            onClick = { onStopAutoMode(); settings.setAiModel(AppSettings.MODEL_OLLAMA); onDismiss() },
        )
    }
}

@Composable
private fun CaptureStateContent(
    captureState: CaptureState,
    appMode: Int,
    aiModel: Int,
    outputLanguage: String,
    textSize: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        when (val state = captureState) {
            is CaptureState.Idle -> StatusText(
                if (appMode == AppSettings.MODE_TRANSLATE) "Press the button to capture\nand translate the screen"
                else "Press the button to capture\nand look up words"
            )
            is CaptureState.Capturing -> StatusText("Capturing...")
            is CaptureState.DownloadingModel -> StatusText(
                "Downloading ${AppSettings.languageDisplayName(outputLanguage)} model..."
            )
            is CaptureState.Processing -> ProcessingText(aiModel, outputLanguage)
            is CaptureState.DictionarySuccess -> TranslationResultView(state.result, textSize)
            is CaptureState.TranslateSuccess -> TranslateSuccessContent(
                state.result.translation, aiModel, textSize,
            )
            is CaptureState.Error -> Text(
                state.message, fontSize = 16.sp,
                color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text, textAlign = TextAlign.Center, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ProcessingText(aiModel: Int, outputLanguage: String) {
    val langName = AppSettings.languageDisplayName(outputLanguage)
    val label = when (aiModel) {
        AppSettings.MODEL_MLKIT_OFFLINE, AppSettings.MODEL_MLKIT_OFFLINE_AUTO -> "Translating to $langName..."
        AppSettings.MODEL_GOOGLE_FREE -> "Translating to $langName via Google..."
        AppSettings.MODEL_OLLAMA -> "Translating to $langName via Ollama..."
        else -> "Translating..."
    }
    Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TranslateSuccessContent(translation: String, aiModel: Int, textSize: Int) {
    val fontSize = when (textSize) {
        AppSettings.TEXT_SIZE_SMALL -> 14.sp
        AppSettings.TEXT_SIZE_LARGE -> 20.sp
        else -> 16.sp
    }
    val isBlockMode = aiModel == AppSettings.MODEL_MLKIT_OFFLINE
        || aiModel == AppSettings.MODEL_MLKIT_OFFLINE_AUTO
        || aiModel == AppSettings.MODEL_GOOGLE_FREE
    if (isBlockMode) {
        BlockTranslationView(translation, fontSize)
    } else {
        Text(translation, fontSize = fontSize, color = MaterialTheme.colorScheme.onSurface, lineHeight = fontSize * 1.5)
    }
}

@Composable
private fun BlockTranslationView(translation: String, fontSize: TextUnit) {
    Column {
        val lines = translation.split("\n")
        var i = 0
        while (i < lines.size) { // Bounded by lines.size (NASA Rule 2)
            val line = lines[i].trim()
            if (line.isEmpty()) { i++; continue }
            Text(line, fontSize = fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = fontSize * 1.4)
            if (i + 1 < lines.size && lines[i + 1].trim().isNotEmpty()) {
                Text(
                    lines[i + 1].trim(), fontSize = fontSize, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, lineHeight = fontSize * 1.4,
                )
                i += 2
            } else {
                i++
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ModeToggle(currentMode: Int, onModeChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        ModeOption("Translate", currentMode == AppSettings.MODE_TRANSLATE,
            { onModeChange(AppSettings.MODE_TRANSLATE) }, Modifier.weight(1f))
        ModeOption("JP Dictionary", currentMode == AppSettings.MODE_DICTIONARY,
            { onModeChange(AppSettings.MODE_DICTIONARY) }, Modifier.weight(1f))
    }
}

@Composable
private fun ModeOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(bgColor)
            .clickable { onClick() }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}
