package com.mimir.translate.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import com.mimir.translate.R
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// NASA Rule 2: all loops must have fixed upper bounds
private const val MAX_AUTO_CYCLES = 10_000
private val LAST_UPDATED_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
private const val APP_TITLE = "MIMIR"
private const val HEADER_BURST_MS = 2600L

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
    val omitEnglish by settings.omitEnglish.collectAsState()

    val isAutoMode = aiModel == AppSettings.MODEL_MLKIT_OFFLINE_AUTO
    var autoJob by remember { mutableStateOf<Job?>(null) }
    var lastOcrText by remember { mutableStateOf<String?>(null) }
    var lastSuccessAtMs by remember { mutableStateOf<Long?>(null) }
    
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
                lastSuccessAtMs = System.currentTimeMillis()
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
                omitEnglish = omitEnglish,
                onDownloading = { onTranslateStateChange(CaptureState.DownloadingModel) },
            )) {
                is TranslateResult.Success -> onTranslateStateChange(
                    CaptureState.TranslateSuccess(TranslationResult(translation = result.text)),
                ).also {
                    lastSuccessAtMs = System.currentTimeMillis()
                }
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
                omitEnglish = omitEnglish,
                onDownloading = { onTranslateStateChange(CaptureState.DownloadingModel) },
            )) {
                is TranslateResult.Success -> onTranslateStateChange(
                    CaptureState.TranslateSuccess(TranslationResult(translation = result.text)),
                ).also {
                    lastSuccessAtMs = System.currentTimeMillis()
                }
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

    var compactMode by remember { mutableStateOf(false) }
    var headerBurstTick by remember { mutableStateOf(0) }
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
            if (!compactMode) {
                MimirTopBar(
                    cropEnabled = cropEnabled, aiModel = aiModel,
                    ollamaModelName = ollamaModelName, settings = settings,
                    captureManager = captureManager, projectionLauncher = projectionLauncher,
                    headerBurstTick = headerBurstTick,
                    onCropRegionClick = ::onCropRegionClick,
                    onHelpClick = onHelpClick, onSettingsClick = onSettingsClick,
                    onStopAutoMode = ::stopAutoMode, onStartAutoMode = ::startAutoMode,
                    onSetPendingAuto = { pendingAutoAfterPermission = it },
                    onLogoTap = {
                        compactMode = true
                        headerBurstTick++
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val buttonIdleLabel = if (appMode == AppSettings.MODE_TRANSLATE) "Translate" else "Analyze"
        val buttonBusyLabel = if (appMode == AppSettings.MODE_TRANSLATE) "Translating..." else "Analyzing..."
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!compactMode) {
                    ModeToggle(currentMode = appMode, onModeChange = { settings.setAppMode(it) })
                    ActiveStatusStrip(
                        appMode = appMode,
                        aiModel = aiModel,
                        outputLanguage = outputLanguage,
                        lastSuccessAtMs = lastSuccessAtMs,
                    )
                }
                CaptureStateContent(
                    captureState = captureState, appMode = appMode, aiModel = aiModel,
                    outputLanguage = outputLanguage, textSize = textSize,
                    modifier = Modifier.weight(1f),
                )
                CaptureButton(
                    isProcessing = captureState is CaptureState.Capturing
                        || captureState is CaptureState.DownloadingModel
                        || captureState is CaptureState.Processing,
                    onClick = { onCaptureClick() },
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .graphicsLayer { alpha = if (compactMode) 0.2f else 1f },
                    isAutoMode = isAutoMode,
                    onStopAuto = { stopAutoMode() },
                    idleLabel = buttonIdleLabel,
                    processingLabel = buttonBusyLabel,
                )
            }
            if (compactMode) {
                CompactModeToggle(
                    onToggle = {
                        compactMode = false
                        headerBurstTick++
                    },
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp),
                )
            }
        }
    }
}

// --- Extracted composables (NASA Rule 4: each <=60 lines) ---

@Composable
private fun CompactModeToggle(onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.mimir_logo),
        contentDescription = "Toggle compact mode",
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable { onToggle() }
            .graphicsLayer { alpha = 0.2f },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MimirTopBar(
    cropEnabled: Boolean,
    aiModel: Int,
    ollamaModelName: String,
    settings: AppSettings,
    captureManager: ScreenCaptureManager,
    projectionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    headerBurstTick: Int,
    onCropRegionClick: () -> Unit,
    onHelpClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onStopAutoMode: () -> Unit,
    onStartAutoMode: () -> Unit,
    onSetPendingAuto: (Boolean) -> Unit,
    onLogoTap: () -> Unit,
) {
    TopAppBar(
        title = { AnimatedMimirTitle(headerBurstTick = headerBurstTick, onLogoTap = onLogoTap) },
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
private fun AnimatedMimirTitle(headerBurstTick: Int, onLogoTap: () -> Unit) {
    var burstActive by remember { mutableStateOf(true) }
    LaunchedEffect(headerBurstTick) {
        burstActive = true
        delay(HEADER_BURST_MS)
        burstActive = false
    }
    val burstBlend by animateFloatAsState(
        targetValue = if (burstActive) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "header_burst_blend",
    )

    val transition = rememberInfiniteTransition(label = "mimir_header")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "header_phase",
    )
    val bob by transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "header_bob",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "header_pulse",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = R.drawable.mimir_logo),
            contentDescription = "Mimir logo",
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable { onLogoTap() }
                .graphicsLayer {
                    val wave = kotlin.math.sin((phase * 2f * Math.PI).toFloat())
                    rotationZ = wave * 14f * burstBlend
                    translationY = bob * burstBlend
                    scaleX = 1f + ((pulse - 1f) * burstBlend)
                    scaleY = 1f + ((pulse - 1f) * burstBlend)
                },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box {
            Text(
                text = watercolorTitleText(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.08.sp,
                    shadow = Shadow(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        offset = Offset(0f, 0f),
                        blurRadius = 12f,
                    ),
                ),
                modifier = Modifier.graphicsLayer { alpha = 1f - burstBlend },
            )
            Text(
                text = goldShimmerTitleText(phase),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.08.sp,
                    shadow = Shadow(
                        color = Color(0xFFF4C86B).copy(alpha = 0.4f),
                        offset = Offset(0f, 0f),
                        blurRadius = 14f,
                    ),
                ),
                modifier = Modifier.graphicsLayer { alpha = burstBlend },
            )
        }
    }
}

private fun watercolorTitleText(): AnnotatedString = buildAnnotatedString {
    val maxIndex = (APP_TITLE.length - 1).coerceAtLeast(1)
    APP_TITLE.forEachIndexed { index, char ->
        val t = index.toFloat() / maxIndex.toFloat()
        val hue = 248f + (32f * t)
        val sat = 0.28f + (0.08f * kotlin.math.sin(t * Math.PI).toFloat())
        val value = 0.92f
        withStyle(style = SpanStyle(color = Color.hsv(hue, sat, value))) {
            append(char)
        }
    }
}

private fun goldShimmerTitleText(phase: Float): AnnotatedString = buildAnnotatedString {
    val maxIndex = (APP_TITLE.length - 1).coerceAtLeast(1)
    APP_TITLE.forEachIndexed { index, char ->
        val t = ((index.toFloat() / maxIndex.toFloat()) + phase) % 1f
        val wave = ((kotlin.math.sin(t * 2f * Math.PI).toFloat()) + 1f) / 2f
        val hue = 38f + (11f * wave)
        val sat = 0.55f + (0.18f * wave)
        val value = 0.86f + (0.14f * wave)
        withStyle(style = SpanStyle(color = Color.hsv(hue, sat, value))) {
            append(char)
        }
    }
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
                .padding(horizontal = 10.dp, vertical = 6.dp),
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
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
                .padding(horizontal = 8.dp, vertical = 5.dp),
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
    Box(modifier = modifier.verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        when (val state = captureState) {
            is CaptureState.Idle -> {}
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
private fun ActiveStatusStrip(
    appMode: Int,
    aiModel: Int,
    outputLanguage: String,
    lastSuccessAtMs: Long?,
) {
    val modeLabel = if (appMode == AppSettings.MODE_TRANSLATE) "Translate" else "JP Dictionary"
    val engineLabel = when (aiModel) {
        AppSettings.MODEL_MLKIT_OFFLINE -> "Offline"
        AppSettings.MODEL_MLKIT_OFFLINE_AUTO -> "Offline Auto"
        AppSettings.MODEL_GOOGLE_FREE -> "Google"
        AppSettings.MODEL_OLLAMA -> "Ollama"
        else -> "Offline"
    }
    val langLabel = AppSettings.languageDisplayName(outputLanguage)
    val lastLabel = lastSuccessAtMs?.let {
        val localTime = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
        "Last: ${LAST_UPDATED_TIME_FORMATTER.format(localTime)}"
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(modeLabel)
        StatusPill(engineLabel)
        StatusPill(langLabel)
        if (lastLabel != null) {
            Text(
                text = lastLabel,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

@Composable
private fun StatusPill(label: String) {
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun TranslateSuccessContent(translation: String, aiModel: Int, textSize: Int) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val fontSize = when (textSize) {
        AppSettings.TEXT_SIZE_SMALL -> 14.sp
        AppSettings.TEXT_SIZE_LARGE -> 20.sp
        else -> 16.sp
    }
    val isBlockMode = aiModel == AppSettings.MODEL_MLKIT_OFFLINE
        || aiModel == AppSettings.MODEL_MLKIT_OFFLINE_AUTO
        || aiModel == AppSettings.MODEL_GOOGLE_FREE
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${translation.length} chars",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Copy",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        clipboardManager.setText(AnnotatedString(translation))
                        Toast.makeText(context, "Translation copied", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        if (isBlockMode) {
            BlockTranslationView(translation, fontSize)
        } else {
            Text(translation, fontSize = fontSize, color = MaterialTheme.colorScheme.onSurface, lineHeight = fontSize * 1.5)
        }
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
            .clickable { onClick() }.padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}
