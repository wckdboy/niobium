# Niobium for Android

Kotlin + Jetpack Compose client for a Hermes Agent gateway. minSdk 28, target 37.
OkHttp is the only runtime network dependency. A Hermes profile is a **Pixel**.

## Build

```sh
cd android
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
gradle :app:assembleDebug
gradle :app:assembleRelease
```

Gradle 9.6+ and JDK 17. Release signing reads `android/keystore.properties`
(git-ignored). Without it the release build is unsigned. R8 and resource shrinking
are enabled; no analytics or third-party SDKs.

## Layout

- `GatewayClient.kt` — OkHttp WebSocket JSON-RPC, pending calls, text frames,
  socket-identity guards against stale callbacks.
- `NiobiumViewModel.kt` — state, roster, canonical chat, tools, groups, demo.
- `SecurePrefs.kt` — AES-256-GCM token storage via Android Keystore.
- `Ui.kt`, `Theme.kt` — Compose design system, pixel faces, markdown/tool cards.
- `Screens.kt`, `Chat.kt`, `Sheets.kt`, `Onboarding.kt` — four-tab app,
  Toolbox overlay, Pixels/Rooms/Activity/Settings.

## Demo / screenshots

```sh
adb shell am start -n io.sovware.niobium/.MainActivity --ez demo true
adb shell am start -n io.sovware.niobium/.MainActivity --ez demo true --es tab rooms
adb shell am start -n io.sovware.niobium/.MainActivity --ez demo true --es tab activity
adb shell am start -n io.sovware.niobium/.MainActivity --ez demo true --es room demo-launch
adb shell am start -n io.sovware.niobium/.MainActivity \
  --ez demo true --es bot forge --ez demoTool true
adb shell am start -n io.sovware.niobium/.MainActivity --ez demo true --ez tools true
```

Live gateway on an emulator where Hermes binds loopback only:

```sh
adb reverse tcp:9119 tcp:9119
adb shell am start -n io.sovware.niobium/.MainActivity \
  --es gateway http://127.0.0.1:9119 --es token "$TOKEN" --es bot athena
```

## Lint

`gradle :app:lintDebug` passes with zero errors. The remaining warnings are
Compose modifier-parameter ordering, KTX suggestions and the intentional
cleartext self-hosted gateway configuration.

## Limitations

- Photo attachment and dictation are iOS-only in 1.0.
- Routines UI is iOS-only in 1.0.
- Room-level approvals are not surfaced; direct pixel approvals are.
- Cleartext HTTP is permitted for self-hosted gateways. Prefer HTTPS/Tailscale.
