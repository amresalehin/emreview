# SmartGallery

An Android photo gallery app powered by Gemini AI. SmartGallery lets you browse your device photos with AI-generated labels, organize images into custom collections, protect private photos in a secure vault, and back up your library to Google Drive.

## Features

- **AI Labeling** — Gemini automatically tags photos with descriptive labels for fast search and filtering
- **Secure Folder** — PIN-protected vault for private photos, with PIN stored as a SHA-256 hash
- **Google Drive Sync** — Back up photos to a chosen Drive folder with automatic retry on token expiry
- **Trash** — Soft-delete with restore support before permanent removal
- **Custom Labels** — Create and manage your own photo collections alongside AI-generated ones
- **Image Layers** — Compose and view layered image edits in the photo detail view

## Requirements

- Android Studio (latest stable)
- Android device or emulator running API 24 (Android 7.0) or higher
- A [Gemini API key](https://aistudio.google.com/apikey)

## Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/SmartGallery.git
   ```
2. Open Android Studio, select **Open**, and choose the cloned directory. Let Gradle sync complete.
3. Create a `.env` file in the project root (next to `build.gradle.kts`) and add your Gemini API key:
   ```
   GEMINI_API_KEY=your_key_here
   ```
   See `.env.example` for the full list of supported variables.
4. Connect a physical device (with USB debugging enabled) or start an emulator via **Device Manager**.
5. Run the app using one of the options below:

   **Android Studio (recommended):** Click **Run ▶** or press `Shift+F10`.

   **Command line:**
   ```bash
   # Mac / Linux
   ./gradlew installDebug

   # Windows
   gradlew.bat installDebug
   ```

> **Google Drive sync** requires signing in with a Google account that has Google Play Services. No additional setup is needed — the app requests Drive access at runtime.

## Build from Command Line

Requires the Android SDK installed with `ANDROID_HOME` set, or an `sdk.dir` entry in `local.properties`.

```bash
# Build debug APK
./gradlew assembleDebug          # Mac / Linux
gradlew.bat assembleDebug        # Windows

# Install on connected device / running emulator
./gradlew installDebug           # Mac / Linux
gradlew.bat installDebug         # Windows

# Run unit tests
./gradlew test                   # Mac / Linux
gradlew.bat test                 # Windows
```

The built APK is written to `app/build/outputs/apk/debug/`.

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `GEMINI_API_KEY` | Yes | Gemini API key for AI labeling |
| `KEYSTORE_PATH` | Release only | Path to upload keystore `.jks` |
| `STORE_PASSWORD` | Release only | Keystore password |
| `KEY_PASSWORD` | Release only | Key password |

## License

This project is open source. See [LICENSE](LICENSE) for details.
