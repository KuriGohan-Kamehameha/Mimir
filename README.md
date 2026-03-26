# Mimir – Real-Time Japanese Game Translation for AYN Thor

[![Build Status](https://github.com/KuriGohan-Kamehameha/Mimir/actions/workflows/ci.yml/badge.svg)](https://github.com/KuriGohan-Kamehameha/Mimir/actions)
[![Release](https://img.shields.io/github/v/release/KuriGohan-Kamehameha/Mimir)](https://github.com/KuriGohan-Kamehameha/Mimir/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

<p align="left"><img src="app/src/main/res/drawable/mimir_logo.png" alt="Mimir logo" width="200"/></p>

Mimir is an open-source Android app for translating and understanding Japanese content in real time. It is designed for dual-screen devices such as the AYN Thor, where the game runs on the top screen and Mimir runs on the bottom. When a second display is not available it works as a single-screen app.

## Table of Contents
1. [Features](#features)
2. [Translation Engines](#translation-engines)
3. [Requirements](#requirements)
4. [Installation](#installation)
5. [Build](#build)
6. [Usage](#usage)
7. [Project Structure](#project-structure)
8. [Tech Stack](#tech-stack)
9. [License](#license)

## Features

### Dual-Screen Game Translation
- Captures the top screen and passes it through OCR + translation on the bottom screen.
- Custom capture region: crop to a dialogue box or UI panel for cleaner results.
- Offline Auto mode retranslates only when text changes, reducing unnecessary processing.
- Bottom-screen behavior controls for AYN Thor: launch on bottom screen and keep app pinned.

### Translation Modes
- **Translate mode** – captures the top screen and translates visible Japanese text.
- **JP Dictionary mode** – OCR + morphological tokenization + JMDict lookup for offline word-by-word breakdown with readings and JLPT levels.

### Translation Engines
| Engine | Network | Cost | Notes |
|---|---|---|---|
| Offline (ML Kit) | No (after first model download) | Free | On-device translation, default mode (~30 MB per output language model) |
| Offline Auto | No (after first model download) | Free | Auto-refresh loop, retranslates only when text changes |
| Google | Yes | Free | Uses Google Translate endpoint, no user API key required |
| Ollama | Yes (local network) | Self-hosted | Uses your Ollama server + selected vision model |

### Output Languages
English, Spanish, Portuguese, French, German, Italian, Chinese, Korean, Russian.

## Requirements

- Android **8.0 (API 26)** or higher (target API 35).
- Java **17+** (bundled with Android Studio).
- Gradle **8.0+** (wrapper included).
- AYN Thor dual-screen hardware recommended; single-screen fallback is built in.

## Installation

### 🚀 Via Obtainium (Recommended)

1. Install [Obtainium](https://github.com/ImranR98/Obtainium) on your device.
2. Open Obtainium and tap **Add App**.
3. Enter the repository URL:
   ```
   https://github.com/KuriGohan-Kamehameha/Mimir
   ```
4. Tap **Add** — Obtainium will fetch the latest APK and install it.
5. In Obtainium app settings for Mimir, set APK filter regex to:
   ```
   .*release\.apk$
   ```
   This ensures Obtainium installs the release build instead of debug builds.
6. Updates are automatically detected when new GitHub releases are published.

## Build

```bash
git clone https://github.com/KuriGohan-Kamehameha/Mimir.git
cd Mimir

# if needed
echo "sdk.dir=$HOME/Android/sdk" > local.properties

# debug build
./gradlew assembleDebug

# release APK (minified)
./gradlew assembleRelease
```

Install debug APK directly:

```bash
./gradlew installDebug
```

Releases are also available on the [GitHub Releases page](https://github.com/KuriGohan-Kamehameha/Mimir/releases).

## Usage

1. Open Mimir on your device.
2. Grant screen-capture permission when prompted.
3. Keep your game on the top screen and Mimir on the bottom.
4. Choose app mode: **Translate** or **JP Dictionary**.
5. For Translate mode, choose engine (Offline, Offline Auto, Google, or Ollama).
6. Optional: tap **Full** in the top bar and set a crop region for the dialogue box.

For Ollama:

1. Open **Settings**.
2. Set Ollama server URL (e.g. `http://192.168.1.10:11434`).
3. Browse and select a vision model.
4. Choose translation style (Auto, Translate, or Explain).

## Project Structure

```
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

## Tech Stack

- **Language**: Kotlin + Jetpack Compose + AndroidX.
- **Min SDK**: 26; **Target SDK**: 35.
- MediaProjection API + Foreground Service for screen capture.
- ML Kit Text Recognition v2 (Japanese) for OCR.
- ML Kit On-Device Translation for offline translation.
- Kuromoji (morphological analyzer) for Japanese tokenization.
- JMDict (offline dictionary data bundled in assets as JSON).
- OkHttp for Ollama and Google Translate network calls.
- ProGuard/minify enabled for release builds.

## License

MIT © 2026 KuriGohan-Kamehameha

See the [LICENSE](LICENSE) file for full details.
