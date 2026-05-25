# Wi-Fi Drop

Share photos, videos, documents, and folders between devices on the same Wi-Fi network—no cloud upload, no account required.

**Package:** `com.michael.wifidrop` · **Privacy policy:** [Wi-FiDrop-pv](https://github.com/michaelsam94/Wi-FiDrop-pv)

## Features

- **Send** — pick files or folder trees, discover nearby receivers, transfer with live speed and progress
- **Receive** — advertise a display name and accept incoming transfers to `Downloads/Wifi-Drop`
- **Web Share** — host files on a built-in local server; download via QR code or browser URL
- **History** — local log of sent and received transfers with sizes and timestamps

## Tech stack

| Layer | Tools |
|-------|-------|
| UI | Jetpack Compose, Material 3 |
| Architecture | ViewModel, Room, Kotlin Coroutines |
| Networking | Ktor (local HTTP server), NSD/mDNS discovery |
| Other | ZXing (QR codes), Apache Commons Compress (folder zips) |

## Requirements

- [Android Studio](https://developer.android.com/studio) (latest stable)
- **JDK 17** for Gradle builds
- Android device or emulator (API 24+)

## Run locally

1. Clone the repo and open this folder in Android Studio.
2. Sync Gradle when prompted.
3. Connect a device or start an emulator.
4. Run the **app** configuration.

Debug builds use the bundled debug keystore automatically.

## Build release AAB

Signing credentials live in `key.properties` (not committed). To generate a keystore and signed bundle:

```bash
bash ~/.cursor/skills/generate-signed-aab/scripts/generate-signed-aab.sh .
```

Output: `app/build/outputs/bundle/release/app-release.aab`

## Play Store assets

Store graphics and listing copy are in [`play-store/`](play-store/). Regenerate screenshots and the feature graphic:

```bash
bash ~/.cursor/skills/generate-app-assets/scripts/init-play-store-dirs.sh .
JAVA_HOME=/path/to/jdk-17 ./gradlew generatePlayStoreAssets --no-configuration-cache
bash ~/.cursor/skills/generate-app-assets/scripts/verify-play-store-assets.sh .
```

Listing text: [`play-store/listing-descriptions.md`](play-store/listing-descriptions.md)

## Tests

```bash
# Unit tests (Play Store screenshot tests excluded)
./gradlew testDebugUnitTest

# Regenerate Play Store screenshots only
./gradlew generatePlayStoreAssets --no-configuration-cache
```

## Permissions

| Permission | Why |
|------------|-----|
| Location / Nearby Wi-Fi devices | Required by Android for local network peer discovery |
| Storage / Media read | Pick files to send; save received files |

## Project layout

```
app/src/main/java/com/michael/wifidrop/
├── feature/          # Send, Receive, History screens + ViewModels
├── core/             # Domain, data, network, storage
├── di/               # AppContainer
└── ui/theme/         # Material 3 theme

play-store/           # Play Console graphics + listing copy
```

## Contact

Questions or feedback: [support@wifidrop.app](mailto:support@wifidrop.app)

## License

All rights reserved.
