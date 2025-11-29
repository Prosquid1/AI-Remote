# AI Remote

AI Remote is a two-part toolkit that lets an Android device trigger actions on a macOS machine over Bluetooth Low Energy (BLE). The macOS app advertises a 4-digit key, receives text commands, and executes them as AppleScript. The Android app generates the AppleScript locally (or via Gemini as fallback), sends it over BLE, and also supports voice commands via on-device transcription.

## How it works

- **macOS app** (`macOS/AI Remote App`): SwiftUI peripheral that advertises a BLE service keyed by a 4-digit code. Incoming messages ending with `*EOM*` are cleaned of backticks and run through AppleScript (`runAppleScript`), with UI feedback for advertising status and connectivity.
- **Android app** (`android/`): Jetpack Compose UI to enter the same 4-digit key, connect, and send commands. Uses `HybridRouter` to classify a request and either:
  - Generate AppleScript locally using Cactus LM models (`smollm2-360m` classifier, `lfm2-1.2b` generator), or
  - Fall back to Gemini (`GeminiCloudClient`) with an API key you supply.
- **BLE protocol**: Service UUID `12345678-<key>-1234-1234-1234567890AB`, characteristic UUID `87654321-4321-4321-4321-BA0987654321`, payload is UTF-8 text terminated with `*EOM*`.

## Requirements

- macOS 12+ with Bluetooth enabled and permission to run AppleScript.
- Xcode (to build/run the macOS SwiftUI app).
- Android Studio + Android SDK (compile/target 36, minSdk 24) and a device/emulator with BLE + microphone.
- Network access the first time the Android app runs to download Cactus models and, if using Gemini, to call the API.

## Setup

### macOS app

1. Open `macOS/AI Remote App.xcodeproj` in Xcode.
2. Build and run the app. Enter a 4-digit access key; the app advertises a BLE service with that key and waits for commands.
3. Grant Bluetooth and AppleScript automation permissions if macOS prompts you.

### Android app

1. In `android/app/src/main/java/com/ai/remote/ai/ServiceLocator.kt`, replace the `TOKEN` value with your Gemini API key. Prefer sourcing it from secure config (e.g., Gradle `local.properties`) before shipping.
2. From `android/`, run `./gradlew assembleDebug` or launch from Android Studio on a physical device.
3. On first run, allow Bluetooth/location (as prompted) and microphone access for recording.
4. The app will download and initialize Cactus models on first use; keep the device on Wi‑Fi and powered.

## Using the apps together

1. Start the macOS app and set your 4-digit key; ensure it shows advertising is active.
2. On Android, enter the same key. Tap **Connect** to scan for the Mac peripheral.
3. Type a command (e.g., “Open Chrome and go to calendar”) or record voice. The app generates AppleScript and sends it with `*EOM*`.
4. The macOS app executes the AppleScript and keeps advertising for additional commands.

## Development notes

- BLE keep-alive pings use the same characteristic; message writes are chunked into 20-byte packets.
- The Mac side strips code fences before running AppleScript; keep messages plaintext.
- Service/characteristic UUIDs and the `*EOM*` terminator are hardcoded in both apps; update both sides if you change them.

## Repository structure

- `android/` — Android app (Compose UI, BLE central, Cactus/Gemini script generation).
- `macOS/` — macOS SwiftUI BLE peripheral and AppleScript runner.
