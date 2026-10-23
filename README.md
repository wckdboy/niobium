<p align="center"><img src="brand/app-icon-n-1024.png" width="80" alt="Niobium"></p>

# Niobium

**Your pixels, everywhere. By [WCKD.ai](https://wckd.ai).**

A pixel-art home for agents on Linux, macOS and Android: connect Hermes, run
DSH locally, or keep both engines ready. Pixels have their own personas, tools
and companions. The existing iOS client is retained; iOS release work is parked.

**2.1 preview: [Linux, macOS and Android](https://github.com/wckdboy/niobium/releases/tag/v2.1.0-beta.1).** Full Hermes parity is the target;
it is not finished. The current scope is in [PLAN.md](PLAN.md), the implementation
audit is in [docs/HERMES-PARITY.md](docs/HERMES-PARITY.md), and the work is tracked
in [Plane](https://app.plane.so/wckd/projects/2fbcf987-20de-43dc-80ac-0a6bab7ec03a/issues/).

<img src="docs/evidence/clean-pixels-ios-dashboard.png" width="280" alt="Niobium v2 on iPhone"> <img src="docs/evidence/clean-pixels-android-workshop.png" width="252" alt="Niobium v2 on Android">

## Desktop preview: Linux and macOS

The Compose desktop client shares transport, PixelSpec, persona generation and
image palette reduction with Android in `core/`. It includes independent Hermes
and local DSH stores, persistent chats, streamed tools, approvals/questions,
model selection, engine-owned rooms and the wardrobe/workshop with optional AI art.
Desktop is an early preview: mobile photos/dictation, routines, full connector
management, companion task controls and complete Hermes parity are still ahead.

Local DSH runs directly as a host process using a bundled Node runtime, with a
private engine home and workspace. Its raw web listener uses a private Unix
socket; only the authenticated Niobium API is exposed on loopback. Start and
Stop are explicit. Closing the app stops owned local work; Hermes continues on
its host. Tools use your account permissions, not a desktop OS sandbox.

The first desktop preview provides native DeepSeek key setup for local chat;
additional local provider setup is planned. Hermes uses its host’s providers.
Remote gateways require HTTPS; HTTP is reserved for literal loopback addresses.
Authenticated gateway requests do not follow redirects.
Remote gateway tokens and the AI image key are session-only on desktop. DSH
provider keys use its owner-only credential file. Android retains Keystore storage.

From the repository root, with Python 3.12+, Node for fixtures, and a JDK 17+:

```sh
python3 tools/prepare-desktop-runtime.py
cd desktop
./gradlew :core:test :test
./gradlew run
./gradlew packageDistributionForCurrentOS
```

Packaging uses the host platform (Linux `.deb`, macOS `.dmg`) and bundles a Java
runtime, Node and the locked DSH dependency graph. Use Temurin or another supported
JDK distribution for packaging. The first macOS preview is unsigned and unnotarized.
Desktop CI runs native tests and packaging on both Linux and macOS; iOS CI is opt-in.

CodeRabbit reviews new pull requests and incremental pushes using
[repository guidance](.coderabbit.yaml). Automatic Greptile reviews are disabled.
The 2.1 checks include 33 shared-core, 18 desktop and seven Android-specific JVM
tests, 67 Node checks and 15 Python checks. Native installer checks run the
packaged app and its private DSH API, and reject stale adapters.

<img src="site/public/desktop-preview.png" width="760" alt="Niobium desktop wardrobe">

## The mobile app

Five tabs: **Home · Pixels · Engines · Activity · Settings**.

- **Home:** lavender pixel landscape, gateway status and measured round trip,
  compact roster and one coherent native experience across providers.
- **Pixels:** persistent Bot Chats, rooms, @mentions and the Pixel workshop.
- **Engines:** select Hermes or on-device DSH. Local Pixels use the same native
  roster, workshop and chats. DSH runs behind the app; its web workspace lives
  under Advanced controls. Saved Hermes settings are retained when switching.
- **Activity:** messages, tools, approvals and background results.
- **Settings:** gateway, appearance, skills, MCP inventory and runtime controls.

Chat includes photos, dictation, model/reasoning selection, stored sessions, usage,
routines, approval/clarify UI and companion work. Subagent monitoring, transcript
tails, steering and interruption use real Hermes RPCs. A queued correction is
reported as queued.

## Pixel workshop

Every New Pixel entry opens the same **describe → preview → review → create**
flow. [bot-build.md](bot-build.md) is the source skill.

The first preview is a local template and appears without a model or image
request. Four short tabs separate Look, Creature, Persona and Access; the create
button stays visible. Optional AI refinement updates the persona while keeping
reviewed appearance and permissions. Its model call is bounded to 35 seconds.
Invalid or unavailable model output remains an honestly labelled local template.
The current engine's capabilities are checked before creating a profile;
newly available tools cannot silently broaden the review.

Creation pins the reviewed toolsets and skills, disables inherited MCP servers,
keeps host command approvals manual and stores the versioned PixelSpec in
profile metadata. Model credentials are inherited from the default profile.
Connector requests need separate account authorization. Workspace and network
isolation are configured on the Hermes host.

Appearance edits are saved to Hermes with metadata revision checks and restored
when the roster refreshes. An incomplete setup is reported without silently
creating a second profile.

Local creation writes reviewed DSH presets and keeps credentials in DSH app storage.
Local DSH supports attributed DMs and rooms. Scheduled routines require Hermes. See [the native provider](docs/DSH-PROVIDER.md)
for the method mapping, execution limits and acceptance evidence.

## Characters and companions

Thirty-two adult fantasy/cyborg looks, with mature, non-explicit outfits and a
playful edge. Every human Pixel is 18+; default templates are 28–40.

Four art atlases provide exact **48×48** portraits with at most 32 colors per
base cell and hard alpha. Android and desktop use the same Kotlin core and pixels; iOS keeps the same portable art, garment
accents and cached renderers. Twelve transparent **16×16** creatures accompany
any look. Automatic selection follows the brief; the wardrobe has style filters,
search, surprise, color remix and undo. The first preview makes no model call.

Optional **AI atelier** designs new Pixel outfits and companion art using the
owner's OpenAI image key. Reference art and the brief go directly to OpenAI;
provider usage and retention apply. Jobs save a provider receipt for resuming,
and interrupted submissions are never silently posted again. Preview and accept
a design before it changes the Pixel. Indexed results remain small and portable;
opaque companion backgrounds are rejected. Appearance never grants agent access.

Older layered recipes remain readable with their original controls and optional
reduced-motion-aware blink.

<img src="brand/pixel-looks-v2-gallery.png" width="640" alt="32 adult 48px looks and 32 garment-accent examples, enlarged four times">

The offline generator has eight role-specific working methods, voices and
creature temperaments; it handles word boundaries and honors explicit build
cues. Connected model generation uses the expanded bot-build skill and remains
labelled separately. Named creatures represent real bounded delegation; they
do not simulate task completion.

Rebuild/check the catalogs with
`python3 tools/portrait_looks.py [--check]`,
`python3 tools/character_engine48.py [--check]` and
`python3 tools/sync-pixel-personas.py [--check]`.
The generated art source and exact prompt are versioned; the macOS Swift baking
tool produces the canonical native atlas.

[Art and prompts](brand/art-provenance.json) ·
[Design reference](docs/reference-dashboard.png) ·
[Dashboard](docs/evidence/clean-pixels-ios-dashboard.png) ·
[Editor](docs/evidence/beta7-ios-workshop.png) ·
[Companions](docs/evidence/pixel-atelier-companions.png)

## Build and check

Shared runtime and contract checks:

~~~sh
npm ci --prefix runtime --ignore-scripts
node --test tests/*.test.mjs
~~~

Android requires JDK 17, SDK platform android-37.0 and NDK 30.0.16248370.
The verified Alpine image and pinned native packages are prepared automatically.

~~~sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
~~~

The Hermes-only iOS target excludes the Linux root filesystem:

~~~sh
cd ios
xcodegen generate
xcodebuild -project Niobium.xcodeproj -scheme Niobium \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
~~~

The full local-DSH target is a separate project. Do not regenerate it with
XcodeGen. See [ios/README.md](ios/README.md) for the submodule/rootfs prerequisites.

Pure Swift validation checks:

~~~sh
swiftc ios/Sources/Niobium/PixelArt.swift ios/Sources/Niobium/PixelSpec.swift tools/check-pixel-spec.swift \
  -o /tmp/niobium-check-pixel-spec
/tmp/niobium-check-pixel-spec
~~~

Website:

~~~sh
node site/build.mjs
node site/check.mjs
~~~

Beta.7 passed 50 iOS unit and six UI checks, 35 Android unit
and sixteen ARM64 emulator checks, 61 Node and 15 Python checks. Sixty-four
shared wardrobe fixtures verify both native renderers. All three CI jobs pass
at source 8a988cf; final Codex review has no findings, with nine earlier review
issues fixed and resolved. CodeRabbit now reviews new changes; historical
release evidence records the reviewer used at the time.

The full iOS build 15 simulator test opens local Rooms, preserves an unfinished
model stream over backgrounding and restores actual DSH history after a cold
launch (134.594 seconds). Android verifies real tool-driven DMs, native questions,
room-clear cancellation, retained custom creatures and explicit remote/local
launch targets. Signed Android code 12 installs and cold-launches; runtime hashes,
corresponding sources and 16 KB ELF/ZIP alignment verify.

Full iOS build 15 archives successfully, with exact current runtime modules and
Release fixture exclusions. Distribution export and TestFlight upload are blocked
because Xcode reports “No Accounts.” Build 14 remains the latest accepted Apple
upload and physical iPhone acceptance; the paired phone is currently locked.
Real-account provider/image acceptance and full Hermes parity remain open. See
[beta.7 evidence](docs/evidence/beta7-release-validation.json).

## Connected workspaces

Beta.7 adds the 32-look wardrobe and optional AI atelier. Hermes and
DSH now own independent transports, conversations and approvals; changing the
selected workspace does not disconnect its peer. Hermes-only mode does not boot
Linux. Stop DSH explicitly to return to Hermes alone.

Local rooms keep separate provider sessions and a durable, attributed outbox.
Reviewed messaging access adds `list_pixels` and `message_agent` for real local
DMs. Room @mentions schedule bounded replies; eight deliveries per user message
and at most two per recipient stop loops. Unknown receipts are visible and never
replayed. Room approvals/questions use the native UI; clearing archives the
owned sessions. These rooms belong to their selected engine; cross-engine rooms
are not bridged. iOS may suspend local work in the background.

## Current limits

Native connector authorization, memory editing, project/workspace management,
complete file/PDF support, durable replay/outbox handling and several room/busy
controls remain incomplete. Android local DSH is a beta; provider-backed and
physical/16 KB device acceptance remain. The native workshop creates real DSH
presets and the loopback model integration test passes. Real-account provider
acceptance and actual delegation remain. Mobile gateway push delivery is not provided.

## Site and licenses

[niobium.sh](https://niobium.sh) uses the shared WCKD web design and is deployed
with Cloudflare Workers Static Assets from this repository. The
[2.1 preview release](https://github.com/wckdboy/niobium/releases/tag/v2.1.0-beta.1)
includes Linux x64 and macOS ARM64 desktop installers, a signed Android APK,
corresponding runtime/OpenJDK sources, metadata and checksums. iOS sources remain
available; iOS release work is parked. The last stable release is
[1.1](https://github.com/wckdboy/niobium/releases/tag/v1.1.0).

The Android UI is MIT; bundled PRoot/Alpine components retain their GPL/LGPL/BSD licenses. The iOS full build combines GPLv3 components. Niobium is
independent of Hermes/Nous Research and DeepSeek; see [NOTICE](NOTICE) and
[LICENSE](LICENSE). Legacy v1 notes remain in [docs/README-v1.md](docs/README-v1.md).

[Expanded test suite](docs/TESTING.md) · [Hermes update watch](docs/HERMES-UPDATES.md) ·
[Android local-runtime decision](docs/ANDROID-DSH.md)
