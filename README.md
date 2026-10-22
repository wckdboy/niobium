<p align="center"><img src="brand/app-icon-n-1024.png" width="80" alt="Niobium"></p>

# Niobium

**Your pixels, everywhere. By [WCKD.ai](https://wckd.ai).**

A native iPhone, iPad and Android home for Pixels: your agents on a Hermes host
or in the on-device DSH engine, with their own personas, tools and companions.

**New release: [2.0.0-beta.5](https://github.com/wckdboy/niobium/releases/tag/v2.0.0-beta.5).** Full Hermes parity is the target;
it is not finished. The current scope is in [PLAN.md](PLAN.md), the implementation
audit is in [docs/HERMES-PARITY.md](docs/HERMES-PARITY.md), and the work is tracked
in [Plane](https://app.plane.so/wckd/projects/2fbcf987-20de-43dc-80ac-0a6bab7ec03a/issues/).

<img src="screenshots/ios-dsh-home.png" width="280" alt="Niobium v2 on iPhone"> <img src="screenshots/android-dsh-home.png" width="252" alt="Niobium v2 on Android">

## The app

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

Every New Pixel entry opens the same **describe → design → review → create**
flow. [bot-build.md](bot-build.md) is the source skill.

The builder asks the gateway for its installed tools and skills, generates a
validated PixelSpec, and previews the adult character, companion, detailed
operator briefing and access. Network, shell and files are reviewed before
creation. Invalid or unavailable model output becomes a clearly labelled local
template.

Creation pins the reviewed toolsets and skills, disables inherited MCP servers,
keeps host command approvals manual and stores the versioned PixelSpec in
profile metadata. Model credentials are inherited from the default profile.
Connector requests need separate account authorization. Workspace and network
isolation are configured on the Hermes host.

Appearance edits are saved to Hermes with metadata revision checks and restored
when the roster refreshes. An incomplete setup is reported without silently
creating a second profile.

Local creation writes reviewed DSH presets and keeps credentials in DSH app storage.
Group rooms and routines require Hermes. See [the native provider](docs/DSH-PROVIDER.md)
for the method mapping, execution limits and acceptance evidence.

## Characters and companions

Eight original adult fantasy/cyborg characters, with a playful, mischievous
edge. Human characters are 18+; styling is mature and non-explicit.

The authored cast is baked once into eight exact **48×48** cells, with at most
32 opaque colors per cell and hard alpha. Both native apps use the same small
atlas and scale portraits on the physical pixel grid without smoothing.
Twelve independent **16×16** companions can accompany any portrait.

Pixel studio and the creation review share a nine-trait editor. Pin favorites,
remix the remaining traits locally, or restore the authored cast. Garment colors,
skin tone and hair color are independent. Legacy six-field recipes still load.
Custom layers use adult proportions, tailored bold outfits and an asymmetric
pose. Large previews blink subtly and respect reduced motion. Both frames are
cached; small roster portraits stay still. Visual edits preserve the briefing
and access. The renderer remains a different art treatment from the authored cast.

<img src="brand/pixel-studio-v3-gallery.png" width="576" alt="Eight actual custom adult Pixel recipes with tailored fantasy and cyborg outfits">

The offline generator has eight role-specific working methods, voices and
creature temperaments; it handles word boundaries and honors explicit build
cues. Connected model generation uses the expanded bot-build skill and remains
labelled separately. Named creatures represent real bounded delegation; they
do not simulate task completion.

Rebuild/check the catalogs with
`python3 tools/character_engine48.py [--check]` and
`python3 tools/sync-pixel-personas.py [--check]`.
The generated art source and exact prompt are versioned; the macOS Swift baking
tool produces the canonical native atlas.

[Art and prompts](brand/art-provenance.json) ·
[Design reference](docs/reference-dashboard.png) ·
[Dashboard](docs/evidence/pixel-atelier-dashboard.png) ·
[Editor](docs/evidence/pixel-studio-v3-editor.png) ·
[Companions](docs/evidence/pixel-atelier-companions.png)

## Build and check

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
swiftc ios/Sources/Niobium/PixelSpec.swift tools/check-pixel-spec.swift \
  -o /tmp/niobium-check-pixel-spec
/tmp/niobium-check-pixel-spec
~~~

Website:

~~~sh
node site/build.mjs
node site/check.mjs
~~~

The beta passed 28 iOS unit tests, 3 native UI tests, 25 Android JVM tests,
5 Android runtime instrumentation tests,
18 DSH-adapter tests, 13 upstream-watcher tests, 5 asset checks and 38 pure Swift PixelSpec assertions.
Android debug/release builds and lint, both iOS variants and the signed full
iOS 2.0 (9) archive/export passed. A full iOS simulator acceptance test drives
real DSH through a controlled SSE model, an eight-second Home-screen return and
stored history after a cold launch (104.198 seconds); see [the model evidence](docs/evidence/ios-build9-simulator-model.json).
Full build 7 also passed the opt-in native DSH smoke
test on a physical iPhone 18 Pro Max running iOS 27.0.1; see
[the device evidence](docs/evidence/ios-build7-device-smoke.json).
Build 9 physical acceptance awaits the reconnected iPhone. Native Home and Pixel-review captures are in
[screenshots](screenshots/README.md). These checks do not establish end-to-end
parity with a live, provider-backed Hermes install.

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
[2.0 beta release](https://github.com/wckdboy/niobium/releases/tag/v2.0.0-beta.5)
includes the signed Android APK, iOS distribution IPA, checksums and complete
release notes. Apple accepted full iOS 2.0 (9), ai.wckd.niobium, for TestFlight
processing on 22 October; tester availability remains unverified. TestFlight
manages installation; the App Store
IPA is not a universally sideloadable download. The last stable release is
[1.1](https://github.com/wckdboy/niobium/releases/tag/v1.1.0).

The Android UI is MIT; bundled PRoot/Alpine components retain their GPL/LGPL/BSD licenses. The iOS full build combines GPLv3 components. Niobium is
independent of Hermes/Nous Research and DeepSeek; see [NOTICE](NOTICE) and
[LICENSE](LICENSE). Legacy v1 notes remain in [docs/README-v1.md](docs/README-v1.md).

[Expanded test suite](docs/TESTING.md) · [Hermes update watch](docs/HERMES-UPDATES.md) ·
[Android local-runtime decision](docs/ANDROID-DSH.md)
