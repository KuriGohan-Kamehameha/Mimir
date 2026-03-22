# Mimir

Android app for translating and understanding foreign-language game screens in real time. It is designed for dual-screen devices such as the [Ayn Thor](https://www.ayntec.com/), where the game runs on the top screen and Mimir runs on the bottom.

## Features

- Translate mode: captures the top screen and translates visible text.
- JP Dictionary mode: OCR + tokenization + JMDict lookup for offline Japanese breakdown.
- Custom capture region: crop to a dialogue box or UI panel for cleaner results.
- Bottom-screen behavior controls for Thor: launch on bottom and enforce bottom-screen placement.

## Translation Engines

| Engine | Network | Cost | Notes |
|---|---|---|---|
| Offline (ML Kit) | No (after first model download) | Free | On-device translation, default mode (~30 MB per output language model) |
| Offline Auto | No (after first model download) | Free | Auto-refresh capture loop, retranslates only when text changes |
| Google | Yes | Free | Uses Google Translate endpoint, no user API key flow in app |
| Ollama | Yes (local network) | Self-hosted | Uses your Ollama server + selected vision model |

## Output Languages

Current language options in app settings:

- English
- Spanish
- Portuguese
- French
- German
- Italian
- Chinese
- Korean
- Russian

## Tech Stack

- Kotlin + Jetpack Compose
- MediaProjection API + Foreground Service for screen capture
- ML Kit Text Recognition (Japanese)
- ML Kit On-Device Translation
- Kuromoji (Japanese tokenizer)
- JMDict (offline dictionary data in assets)
- OkHttp + Gson

## Requirements

- JDK 17
- Android SDK (compileSdk 35, minSdk 26)
- A connected Android device (Thor recommended)

## Build

```bash
git clone <repo-url>
cd Mimir

# if needed
echo "sdk.dir=$HOME/Android/sdk" > local.properties

./gradlew assembleDebug
```

Install debug APK:

```bash
./gradlew installDebug
```

## Quick Start

1. Open Mimir on device.
2. Grant screen-capture permission when prompted.
3. Keep your game on the top screen and Mimir on the bottom.
4. Choose app mode: Translate or JP Dictionary.
5. For Translate mode, choose engine (Offline, Offline Auto, Google, or Ollama).
6. Optional: tap Full in the top bar and set a crop region.

For Ollama:

1. Open Settings.
2. Set Ollama server URL (for example: http://192.168.1.10:11434).
3. Browse/select a model.
4. Choose translation style (Auto, Translate, or Explain).

## Project Structure

```text
app/src/main/java/com/mimir/translate/
├── MainActivity.kt
├── MimirApp.kt
├── capture/
│   ├── ScreenCaptureManager.kt
│   └── ScreenCaptureService.kt
├── ocr/
│   └── TextRecognizer.kt
├── analysis/
│   ├── DictionaryLookup.kt
│   └── JapaneseTokenizer.kt
├── translate/
│   ├── GoogleTranslator.kt
│   ├── OllamaModelBrowser.kt
│   ├── OllamaTranslator.kt
│   └── ScreenTranslator.kt
├── data/models/
│   ├── AppSettings.kt
│   └── CaptureState.kt
└── ui/
    ├── screens/
    │   ├── CropScreen.kt
    │   ├── HelpScreen.kt
    │   ├── MainScreen.kt
    │   └── SettingsScreen.kt
    ├── components/
    │   ├── CaptureButton.kt
    │   ├── TranslationResult.kt
    │   └── WordCard.kt
    └── theme/
        └── Theme.kt
```

## License

MIT
