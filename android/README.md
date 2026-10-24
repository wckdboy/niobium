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

Hermes and embedded DSH have separate engine/session owners and can run side by
side. DSH runs in the app-private ARM64 PRoot/Alpine runtime under a foreground
service; its Pixels use the same native chat and room surfaces. Provider setup,
model selection and preset creation are native. See [ANDROID-DSH.md](../docs/ANDROID-DSH.md)
for runtime packaging and remaining physical-device acceptance.

Photos use the system image picker, bounded background decoding, orientation
correction, a small preview and resized uploads. DMs and rooms preserve older
reading positions while replies stream; Latest messages resumes following.
Direct-message drafts clear after engine acceptance. Opaque pending receipts
survive app restart and block another send after uncertain delivery until the
user reviews it; the app never automatically replays the prompt. Text drafts are
bounded in-memory per conversation, not persisted across process death.
Abandoned/rejected uploads are detached by their exact engine-returned handle.
After a lost connection, review the engine's queued photos as well as the chat
before unlocking an uncertain send.

Use HTTPS for remote gateways. Cleartext HTTP is limited to literal loopback for
the private local runtime; LAN hosts need HTTPS or a secure tunnel.

Chats searches the loaded titles and models, filters working conversations and
marks the current chat. Hermes currently requests the 60 most recent stored rows;
local DSH returns all visible chats. A failed refresh keeps the loaded rows and
shows an explicit retry state. Replies from previous Pixels, clients or superseded
refreshes cannot overwrite the current list.

See [HERMES-PARITY.md](../docs/HERMES-PARITY.md) for evidence and remaining gaps.
Demo/build checks do not establish live provider, authorization or background
behaviour.


## Android inventory ownership candidate

Toolsets, skills, MCP servers and routines use a captured gateway/chat/profile and
a newest-request revision. Opening, closing or resuming a chat clears its previous
inventory. Late replies cannot write into another Pixel or overwrite a newer
refresh. Failed refreshes retain loaded rows with retry controls. MCP configuration
stays visible if status is unavailable, explicitly marked unknown.

Tool, skill-reload and routine mutations capture the original owner, reject stale
controls and repeated taps, and ignore acknowledgements after a Pixel switch. An
unconfirmed change requires a successful explicit refresh before another change.
Native regression tests cover a failed-before/passed-after Pixel-switch race, newest
refresh failures/partial MCP status, mutation deduplication/owner changes, and
explicit refresh recovery after unconfirmed changes. Malformed catalogs retain
loaded rows and report a failure rather than unlocking mutable controls.
The candidate APK is 2.2.2/code17; publication requires final-head review and checks.

The routine editor constrains its scrollable fields to the space above the IME
and keeps Create/Cancel in a fixed action row. A 320 dp-wide, 240 dp-high native
form test at 1.3 text scale fills the fields and creates a routine through visible
actions. The initial narrow-screen keyboard capture exposed hidden actions; the
custom inset-aware editor replaces the unconstrained dialog. Action labels stay
on one line, with text-only controls in the compact form.

Automatic sheet initialization preserves an existing catalog error; the explicit
Refresh/Retry paths recover it. Toolbox, skill reload and routine controls share
the same eligibility predicates as their ViewModel mutations, including chat
loading. Re-entry regressions verify automatic loads cannot unlock unconfirmed
changes. A separate native dialog test opens the real IME, enters valid fields,
checks that Create is above the keyboard and creates through the visible action.
