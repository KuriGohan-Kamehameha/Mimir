package com.mimir.translate.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimir.translate.data.models.AppSettings
import com.mimir.translate.translate.OllamaModel
import com.mimir.translate.translate.OllamaModelBrowser
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val textSize by settings.textSize.collectAsState()
    val translateStyle by settings.translateStyle.collectAsState()
    val aiModel by settings.aiModel.collectAsState()
    val outputLanguage by settings.outputLanguage.collectAsState()
    val ollamaUrl by settings.ollamaUrl.collectAsState()
    val ollamaModel by settings.ollamaModel.collectAsState()
    val autoModeRefresh by settings.autoModeRefresh.collectAsState()
    val themeMode by settings.themeMode.collectAsState()
    val launchBottomScreen by settings.launchBottomScreen.collectAsState()
    val lockAppToBottomScreen by settings.lockAppToBottomScreen.collectAsState()
    val omitEnglish by settings.omitEnglish.collectAsState()

    var ollamaUrlInput by remember { mutableStateOf(ollamaUrl) }
    val scope = rememberCoroutineScope()
    val modelBrowser = remember { OllamaModelBrowser() }

    var showModelPicker by remember { mutableStateOf(false) }
    var modelList by remember { mutableStateOf<List<OllamaModel>>(emptyList()) }
    var modelLoadError by remember { mutableStateOf<String?>(null) }
    var isLoadingModels by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SettingsTopBar(onBack)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextSizeSection(textSize, settings)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            ThemeModeSection(themeMode, settings)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            BottomScreenSection(launchBottomScreen, lockAppToBottomScreen, settings)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            TranslationEngineSection(aiModel, settings)
            if (aiModel == AppSettings.MODEL_MLKIT_OFFLINE_AUTO) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                AutoRefreshSection(autoModeRefresh, settings)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            OutputLanguageSection(outputLanguage, aiModel, settings)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            OmitEnglishSection(omitEnglish, settings)
            if (aiModel == AppSettings.MODEL_OLLAMA) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                TranslationStyleSection(translateStyle, settings)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                OllamaServerSection(
                    ollamaUrlInput = ollamaUrlInput,
                    onUrlChange = { ollamaUrlInput = it; settings.setOllamaUrl(it) },
                    ollamaModel = ollamaModel,
                    isLoadingModels = isLoadingModels,
                    modelLoadError = modelLoadError,
                    onBrowseModels = {
                        isLoadingModels = true
                        modelLoadError = null
                        scope.launch {
                            val result = modelBrowser.fetchModels(ollamaUrlInput)
                            isLoadingModels = false
                            result.fold(
                                onSuccess = { models ->
                                    modelList = models
                                    if (models.isEmpty()) {
                                        modelLoadError = "No models found. Install models with: ollama pull <model>"
                                    } else {
                                        showModelPicker = true
                                    }
                                },
                                onFailure = { e -> modelLoadError = "Connection failed: ${e.message}" },
                            )
                        }
                    },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showModelPicker) {
        ModelPickerDialog(
            modelList = modelList,
            selectedModel = ollamaModel,
            onSelect = { settings.setOllamaModel(it); showModelPicker = false },
            onDismiss = { showModelPicker = false },
        )
    }
}

@Composable
private fun ThemeModeSection(themeMode: Int, settings: AppSettings) {
    var expanded by remember { mutableStateOf(false) }
    SettingsSection(title = "Theme") {
        Box {
            Text(
                text = AppSettings.themeModeLabel(themeMode),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { expanded = true }
                    .padding(vertical = 12.dp, horizontal = 16.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AppSettings.THEME_MODES.forEach { (mode, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            settings.setThemeMode(mode)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomScreenSection(launchBottomScreen: Boolean, lockAppToBottomScreen: Boolean, settings: AppSettings) {
    SettingsSection(title = "Display") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Keep app on bottom screen",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Always moves Mimir back to the Thor's lower display if it gets moved.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = lockAppToBottomScreen,
                onCheckedChange = { settings.setLockAppToBottomScreen(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Launch on bottom screen",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (lockAppToBottomScreen) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Opens Mimir on the Thor's lower display at startup",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = launchBottomScreen || lockAppToBottomScreen,
                onCheckedChange = { settings.setLaunchBottomScreen(it) },
                enabled = !lockAppToBottomScreen,
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun AutoRefreshSection(autoModeRefresh: Int, settings: AppSettings) {
    var expanded by remember { mutableStateOf(false) }
    SettingsSection(title = "Auto Mode Refresh") {
        Box {
            Text(
                text = AppSettings.autoRefreshLabel(autoModeRefresh),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { expanded = true }
                    .padding(vertical = 12.dp, horizontal = 16.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AppSettings.AUTO_REFRESH_RATES.forEach { (rateMs, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            settings.setAutoModeRefresh(rateMs)
                            expanded = false
                        },
                    )
                }
            }
        }
        Text(
            "Controls how often screen translation runs in Auto mode.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// --- Extracted composables (NASA Rule 4: each <=60 lines) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("Settings", fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Text("<", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

@Composable
private fun TextSizeSection(textSize: Int, settings: AppSettings) {
    SettingsSection(title = "Text Size") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsOption("S", textSize == AppSettings.TEXT_SIZE_SMALL,
                { settings.setTextSize(AppSettings.TEXT_SIZE_SMALL) }, Modifier.weight(1f))
            SettingsOption("M", textSize == AppSettings.TEXT_SIZE_MEDIUM,
                { settings.setTextSize(AppSettings.TEXT_SIZE_MEDIUM) }, Modifier.weight(1f))
            SettingsOption("L", textSize == AppSettings.TEXT_SIZE_LARGE,
                { settings.setTextSize(AppSettings.TEXT_SIZE_LARGE) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TranslationEngineSection(aiModel: Int, settings: AppSettings) {
    SettingsSection(title = "Translation Engine") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsOption("Offline",
                aiModel == AppSettings.MODEL_MLKIT_OFFLINE || aiModel == AppSettings.MODEL_MLKIT_OFFLINE_AUTO,
                { settings.setAiModel(AppSettings.MODEL_MLKIT_OFFLINE) }, Modifier.weight(1f))
            SettingsOption("Google", aiModel == AppSettings.MODEL_GOOGLE_FREE,
                { settings.setAiModel(AppSettings.MODEL_GOOGLE_FREE) }, Modifier.weight(1f))
            SettingsOption("Ollama", aiModel == AppSettings.MODEL_OLLAMA,
                { settings.setAiModel(AppSettings.MODEL_OLLAMA) }, Modifier.weight(1f))
        }
        EngineDescription(aiModel)
    }
}

@Composable
private fun EngineDescription(aiModel: Int) {
    val text = when (aiModel) {
        AppSettings.MODEL_MLKIT_OFFLINE, AppSettings.MODEL_MLKIT_OFFLINE_AUTO ->
            "On-device translation. No internet after first download (~30MB)."
        AppSettings.MODEL_GOOGLE_FREE ->
            "Free Google Translate. No account or API key needed. Requires internet."
        AppSettings.MODEL_OLLAMA ->
            "Connect to an Ollama server on your local network. Vision model required."
        else -> return
    }
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun OutputLanguageSection(outputLanguage: String, aiModel: Int, settings: AppSettings) {
    var expanded by remember { mutableStateOf(false) }
    SettingsSection(title = "Output Language") {
        Box {
            Text(
                text = AppSettings.languageDisplayName(outputLanguage),
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { expanded = true }
                    .padding(vertical = 12.dp, horizontal = 16.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AppSettings.OUTPUT_LANGUAGES.forEach { (code, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = {
                        settings.setOutputLanguage(code); expanded = false
                    })
                }
            }
        }
        val isOffline = aiModel == AppSettings.MODEL_MLKIT_OFFLINE || aiModel == AppSettings.MODEL_MLKIT_OFFLINE_AUTO
        if (isOffline && outputLanguage != AppSettings.LANG_ENGLISH) {
            Text(
                "First use will download the ${AppSettings.languageDisplayName(outputLanguage)} model (~30MB)",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun OmitEnglishSection(omitEnglish: Boolean, settings: AppSettings) {
    SettingsSection(title = "Omit English") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Skip English text blocks",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Filters out text that's already in English (e.g. menu options like Save/Load)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = omitEnglish,
                onCheckedChange = { settings.setOmitEnglish(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun TranslationStyleSection(translateStyle: Int, settings: AppSettings) {
    SettingsSection(title = "Translation Style") {
        Text("Controls how the Ollama model responds", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsOption("Auto", translateStyle == AppSettings.TRANSLATE_STYLE_AUTO,
                { settings.setTranslateStyle(AppSettings.TRANSLATE_STYLE_AUTO) }, Modifier.weight(1f))
            SettingsOption("Translate", translateStyle == AppSettings.TRANSLATE_STYLE_TRANSLATE_ONLY,
                { settings.setTranslateStyle(AppSettings.TRANSLATE_STYLE_TRANSLATE_ONLY) }, Modifier.weight(1f))
            SettingsOption("Explain", translateStyle == AppSettings.TRANSLATE_STYLE_TRANSLATE_AND_EXPLAIN,
                { settings.setTranslateStyle(AppSettings.TRANSLATE_STYLE_TRANSLATE_AND_EXPLAIN) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun OllamaServerSection(
    ollamaUrlInput: String,
    onUrlChange: (String) -> Unit,
    ollamaModel: String,
    isLoadingModels: Boolean,
    modelLoadError: String?,
    onBrowseModels: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val clipboardText = (clipboardManager.getText()?.text ?: "").trim()
    val canPasteUrl = clipboardText.startsWith("http://") || clipboardText.startsWith("https://")

    SettingsSection(title = "Ollama Server") {
        Text("Enter the URL of your Ollama server", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Paste URL",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (canPasteUrl) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (canPasteUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = canPasteUrl) {
                        onUrlChange(clipboardText.removeSuffix("/"))
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (ollamaUrlInput.isNotBlank()) {
                Text(
                    text = "Clear",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onUrlChange("") }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        OutlinedTextField(
            value = ollamaUrlInput, onValueChange = onUrlChange,
            placeholder = { Text("http://192.168.1.100:11434") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (ollamaModel.isNotEmpty()) {
            Text("Selected: $ollamaModel", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
        }
        BrowseModelsButton(ollamaUrlInput, isLoadingModels, onBrowseModels)
        if (modelLoadError != null) {
            Text(modelLoadError, fontSize = 12.sp, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun BrowseModelsButton(ollamaUrlInput: String, isLoadingModels: Boolean, onClick: () -> Unit) {
    val enabled = ollamaUrlInput.isNotBlank() && !isLoadingModels
    Text(
        text = if (isLoadingModels) "Loading..." else "Browse Models",
        fontSize = 14.sp, fontWeight = FontWeight.Bold,
        color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (!enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
    )
}

@Composable
private fun ModelPickerDialog(
    modelList: List<OllamaModel>,
    selectedModel: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Ollama Model", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val browser = remember { OllamaModelBrowser() }
                modelList.forEach { model ->
                    ModelPickerItem(model, browser, model.name == selectedModel, onSelect)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ModelPickerItem(
    model: OllamaModel,
    browser: OllamaModelBrowser,
    isSelected: Boolean,
    onSelect: (String) -> Unit,
) {
    val sizeStr = browser.formatSize(model.size)
    val detailStr = buildString {
        if (model.parameterSize.isNotEmpty()) append(model.parameterSize)
        if (model.quantizationLevel.isNotEmpty()) { if (isNotEmpty()) append(" / "); append(model.quantizationLevel) }
        if (sizeStr.isNotEmpty()) { if (isNotEmpty()) append(" / "); append(sizeStr) }
    }
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        model.hasVision -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bgColor)
            .clickable(enabled = model.hasVision) { onSelect(model.name) }.padding(12.dp),
    ) {
        Text(model.name, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = if (model.hasVision) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        if (detailStr.isNotEmpty()) {
            Text(detailStr, fontSize = 11.sp,
                color = if (model.hasVision) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
        if (!model.hasVision) {
            Text("No vision support", fontSize = 11.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun SettingsOption(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(bgColor)
            .clickable { onClick() }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}
