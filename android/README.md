# Niobium for Android

Native Kotlin and Jetpack Compose. minSdk 28, target 37, JDK 17.
Use the committed wrapper from this directory:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:assembleRelease
```

Set the SDK path in ignored `local.properties`. Release signing reads ignored
`keystore.properties`; R8 and resource shrinking are enabled.

The v2 source has Home, Pixels, Local, Activity and Settings. New Pixels use the
bot-build skill, validated personas, permission review and profile provisioning.
`PixelSpec.kt`/`PixelBuilder.kt` own that flow; `CharacterEngine.kt`, `Cast.kt` and
`PixelComponents.kt` own cached 48×48 art and shared panels. Custom layer art is
experimental and visually less polished than the authored cast.

`GatewayClient.kt` handles RPC/socket lifetimes; `NiobiumViewModel.kt` owns
profiles, chat, rooms, models/reasoning and approvals. `Companions.kt` exposes
real delegated work. `CredentialPrompt.kt` uses transient secure forms;
`SecurePrefs.kt` uses Android Keystore. `Harness.kt` hosts remote DSH with
retry/error controls and teardown.

Launch demo: `adb shell am start -S -n io.sovware.niobium.debug/io.sovware.niobium.MainActivity --ez demo true`.
Add `--es tab pixel-review` or `--es tab workshop` for creation captures;
`--es bot forge`, `--es room demo-launch`, `--es tab rooms` and
`--es tab activity` select other demo surfaces.

Photos, dictation and routine controls exist on both platforms. Android DSH is
currently remote; an embedded Linux runtime remains a separate gate.
Cleartext supports self-hosted LAN gateways; HTTPS/private networking also work.

See [HERMES-PARITY.md](../docs/HERMES-PARITY.md) for evidence and remaining gaps.
Demo/build checks do not establish live provider, authorization or background
behaviour.
