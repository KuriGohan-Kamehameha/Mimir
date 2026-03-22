package com.mimir.translate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Help", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(
                            text = "<",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HelpSection("What is Mimir?")
            HelpBody(
                "Mimir translates foreign-language game screens in real time. " +
                "Built for dual-screen devices like the Ayn Thor \u2014 run your game on the top screen " +
                "and Mimir on the bottom."
            )

            HelpDivider()

            HelpSection("Translate Mode")
            HelpBody(
                "Captures a screenshot and translates the text on screen. " +
                "Works with any language \u2014 Japanese, Chinese, Korean, and more."
            )

            HelpDivider()

            HelpSection("JP Dictionary Mode")
            HelpBody(
                "Offline Japanese word-by-word breakdown. Uses on-device OCR to read Japanese text, " +
                "then looks up each word in a 212K-entry dictionary. Shows kanji, reading, meaning, " +
                "and JLPT level. No internet required."
            )

            HelpDivider()

            HelpSection("Translation Engines")
            HelpBullet("Offline", "On-device ML Kit translation. No internet after first download (~30MB). Fast but basic quality.")
            HelpBullet("Offline Auto", "Same as Offline, but captures and translates automatically every second. Only re-translates when the text changes.")
            HelpBullet("Google Translate", "Free online translation. No account or API key needed. Good quality.")
            HelpBullet("Ollama (LAN)", "Connect to an Ollama server on your local network. Uses vision-capable LLMs to understand and translate game screens with context. Set up in Settings.")
            Spacer(modifier = Modifier.height(4.dp))
            HelpBody("Switch engines from the top bar or in Settings. Offline is the default.")

            HelpDivider()

            HelpSection("Ollama Setup")
            HelpBody("1. Install Ollama on a computer on your local network (ollama.com)")
            HelpBody("2. Pull a vision model: ollama pull llama3.2-vision")
            HelpBody("3. Start Ollama with network access: OLLAMA_HOST=0.0.0.0 ollama serve")
            HelpBody("4. In Mimir Settings, enter the server URL (e.g. http://192.168.1.100:11434)")
            HelpBody("5. Tap 'Browse Models' to select your vision model")

            HelpDivider()

            HelpSection("Translation Style (Ollama only)")
            HelpBullet("Auto", "Translates and explains what to do next (recommended)")
            HelpBullet("Translate", "Just translates the text, no extra explanation")
            HelpBullet("Explain", "Full translation with detailed guidance on how to progress")

            HelpDivider()

            HelpSection("Output Language")
            HelpBody(
                "Choose which language to translate into. Available in Settings. " +
                "Supports English, Spanish, Portuguese, French, German, Italian, Chinese, Korean, and Russian."
            )
            HelpBody("For Offline mode, each language downloads a ~30MB model on first use.")

            HelpDivider()

            HelpSection("Custom Region")
            HelpBody(
                "By default, the entire screen is captured. Tap \"Full\" in the top bar to select " +
                "a specific area (e.g. the dialogue box). Drag on the screenshot to draw the region, " +
                "then tap \"Save Region\"."
            )
            HelpBody("Tap the \u2715 next to \"Region\" to go back to full screen capture.")

            HelpDivider()

            Text(
                text = "Based on open-source work by magiobus (MIT License)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HelpSection(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun HelpBody(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 20.sp,
    )
}

@Composable
private fun HelpBullet(label: String, description: String) {
    Text(
        text = "\u2022 $label \u2014 $description",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 20.sp,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun HelpDivider() {
    Spacer(modifier = Modifier.height(12.dp))
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
}
