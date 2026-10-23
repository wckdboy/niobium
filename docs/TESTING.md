# Test suite

Run shared checks from the repository root:

```sh
npm ci --prefix runtime --ignore-scripts
node --test tests/*.test.mjs
python3 -m unittest discover -s tests -p 'test_*.py' -v
node tools/check-plane-sync.mjs
node site/build.mjs
node site/check.mjs
```

The Node watcher tests cover contract/feature detection, new releases, truncated
comparisons, pagination, idempotent issue updates and invalid repository paths.
The Plane test opens a loopback-only server and checks dry-run behaviour,
credentials, stable UUID updates and workflow preservation. Asset checks cover
the opaque white icon, art provenance, shared skill/recipe copies and CSV fields. New character checks verify all twelve
16×16 companion sprites, hard-alpha 48×48 cast cells, color budgets, and generated
catalog/fixture drift without third-party image packages.

Android, with Node available on PATH and JDK 17:

```sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:assembleRelease
```

Thirty Android JVM tests include real OkHttp HTTP/WebSocket exchanges with a local
Node fixture: reverse-proxy paths, token encoding/header authentication,
out-of-order replies, server errors, timeout/cancellation recovery, typed
server-request IDs, approval replies and disconnecting an in-flight request.
Provisioning tests verify the actual creation fields, reviewed tool and skill
pins, disabled MCP servers, and failure after creation without retrying it.
Schema tests cover permissions, adult age, malformed output and custom
companion persistence through serialization. The fixture uses no model provider.

iOS, with XcodeGen and an available iPhone simulator:

```sh
cd ios
xcodegen generate
xcodebuild -project Niobium.xcodeproj -scheme Niobium \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  CODE_SIGNING_ALLOWED=NO test
```

Thirty-nine unit tests cover URL/auth handling, JSON-RPC IDs, cancellation,
PixelSpec validation, reversed chat-selection responses, resumed streaming,
recovered clarification dismissal, provisioning access and failed appearance saves.
Attachment regressions cover duplicate opens, obsolete lookups and rejected handoffs.
Five UI tests launch only the labelled demo, verify the dashboard and
review flow plus coherent native Engines navigation, and capture screenshots during the run. Reviewed CLI captures are stored under
`docs/evidence/clean-pixels-*`; xcresult attachment export is not assumed. Native tests live in
NativeTests; the lowercase tests symlink belongs to the DSH submodule.

The full DSH target has a separate device archive gate; do not regenerate it
with XcodeGen. TestFlight uses ai.wckd.niobium on team XKA8CGC2AB. Export/upload
options are in ios/Release.

For a paired, unlocked iPhone with Developer Mode enabled, run the opt-in
native DSH smoke test against the development-signed full app from an archive:

```sh
python3 tools/check-ios-device.py --device YOUR_IPHONE_UDID \
  --app dist/Niobium-2.0-10-pixel-studio.xcarchive/Products/Applications/Niobium.app
```

The helper builds the UI test runner and points its `UITargetAppPath` at the
full app using Apple's editable xctestrun format. It updates the existing app;
it does not erase its container. The test selects local DSH, waits for the real
Scout roster, checks native Engines/provider settings, returns from the Home
screen and opens native chat. It sends no model request and changes no API key.
Screenshots are retained in the local xcresult; inspect them for private data
before sharing. The default client-only CI run skips this opt-in test.
An existing paired local-network connection works without a cable. The helper
checks the lock state before building and reports when an unlock is required.
The full app honors the acceptance launch argument to prevent Auto-Lock during
the run. If both apps are supplied, each test runs even if the other fails;
the helper still exits unsuccessfully if either acceptance test fails.

The build-10 wireless run reached the real device and booted DSH, then failed
because the native connection entered an error state and the roster did not
load. The separate controlled model run was blocked before launch by the lock
screen and was cancelled. These are not physical acceptance passes. Sanitized
evidence is in [ios-build10-wireless-device.json](evidence/ios-build10-wireless-device.json).
The follow-up source exposes local connection errors on Home and Engines and
records them in the redacted DSH diagnostic log. The existing build-10 release
artifacts predate that change; beta.6 publication remains on hold.

For a controlled model turn, build a separate full Debug app. Its bundle identity
keeps app data and Keychain items separate from the installed Niobium app:

```sh
xcodebuild -project ios/NiobiumDSH.xcodeproj -scheme Niobium \
  -configuration Debug -destination 'generic/platform=iOS' \
  -derivedDataPath dist/iphone-model-app \
  NIOBIUM_APP_BUNDLE_ID=ai.wckd.niobium.acceptance \
  -allowProvisioningUpdates build
python3 tools/check-ios-device.py --device YOUR_IPHONE_UDID --only-model \
  --model-app dist/iphone-model-app/Build/Products/Debug-iphoneos/Niobium.app
```

The test runner hosts a loopback-only SSE model fixture. Only the Debug acceptance
bundle can receive its URL and fixture key; Release builds omit that hook. The
test sends a prompt through real DSH and waits for partial output plus a running
turn. It holds the final SSE chunk until Niobium has spent eight seconds on the
Home screen, then checks completion and history after a full app/guest restart.
It uses a unique reply marker on each run, so old history cannot satisfy the
stream/history assertions. It uses no real provider account. Sanitized simulator
evidence is in [ios-build10-simulator-model.json](evidence/ios-build10-simulator-model.json). The production smoke also waits eight seconds
on the Home screen, checks engine readiness again after returning, and verifies
that a temporary unsent draft can be submitted before clearing it.

The controlled model test can also run on a simulator. Build the same full Debug
acceptance target with a simulator destination and separate derived data, then
pass `--simulator`, its UDID, the matching `--model-app` and `--only-model` to
the helper. This checks the real guest and model stream; physical lifecycle and
device performance still require the iPhone run.

After inspection, remove only the temporary `ai.wckd.niobium.acceptance` app and
`ai.wckd.niobium.uitests.xctrunner` from the test device. Preserve the production
`ai.wckd.niobium` app and its data. If the device disconnects, cleanup and physical
acceptance remain pending until it reconnects.


GitHub Actions runs shared, Android, Linux desktop and macOS desktop suites for pull requests and pushes. The parked iOS suite is opt-in through workflow_dispatch.
The client suite pins macOS 15 ARM64 and Xcode 26.3, both listed in the
[official runner image](https://github.com/actions/runner-images/blob/main/images/macos/macos-15-arm64-Readme.md).
This keeps the client toolchain explicit as the latest image migrates. Full DSH
release archives are also built locally with Xcode 27.1. Both run the same
native client sources; the opt-in full runtime checks use their matching apps.
Live provider/connector authorization, physical-device background behaviour and
physical-device Android DSH remain explicit acceptance work in PLAN.md.

Android runtime tests require an ARM64 Android device/emulator:

    ./gradlew :app:connectedDebugAndroidTest
    python3 ../tools/check-android-runtime.py app/build/outputs/apk/debug/app-debug.apk

An Android Compose workshop test verifies portrait selection through garment
remix, undo, tab changes and creature selection. The iOS UI suite also checks
that a local preview appears within two seconds without an AI request.
Espresso
is pinned to 3.7.0: Compose’s old transitive version reflects an InputManager
method removed on Android 16. The stable fix uses getSystemService
([Android Test release notes](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0)). Two additional on-device renderer checks verify shared RGBA pixels and that changing
companions preserves each authored portrait. Five app-domain runtime tests verify Linux/Node/DSH, private authenticated HTTP/WebSocket access,
real session creation and bot-build discovery, service Start/Stop, CSS viewport layout,
workspace preservation through image replacement, and a full native preset/chat/history
round trip through real DSH and a controlled model server. Eighteen shared adapter
tests cover tool grants, adult/slug validation, model identity, pending approvals
and questions, stream/transcript folding and explicit unsupported capabilities.
See DSH-PROVIDER.md and ANDROID-DSH.md for limits.


## Modular portrait quality

`python3 -m unittest discover -s tests -p 'test_*.py' -v` runs fourteen checks.
The art-specific checks inspect 288 hair/headwear/build combinations for
readable eyes, all 24 outfit cuts for opaque chest coverage, and 864
build/outfit/expression/detail combinations for stable silhouettes across
open/blink frames. Colors stay on the hard 48×48 grid. These structural checks
complement visual review; they do not measure attractiveness.

`python3 tools/character_engine48.py --check` verifies 55 legacy generated files.
The source layers live in `tools/portrait_layers48.py`; drawing primitives are
in `tools/pixel_canvas.py`. The original generated reference and exact prompt
are retained alongside the actual custom gallery, all outfit cuts, recipe list
and open/blink sheet in `brand/pixel-studio-v3-*`. The reference is visual
direction, not the runtime source atlas. The legacy gallery is retained for compatibility review. New designs use the
authored-look catalog built by `python3 tools/portrait_looks.py [--check]`.
Sixteen portable RGBA fixtures are checked by the actual Swift and Android
renderers. Python checks exact original pixels, unchanged alpha and identities,
bounded garment masks, native catalog equality and fixture drift. The website
custom sheet uses the same new garment variants.

Swift and on-device Kotlin compare both rendered frames with shared RGBA
fixtures and verify independent cache entries. Android also exercises the actual
Compose preview coroutine, waits for a blink, disables motion and checks that
the open frame is restored. Large previews cancel decorative motion when
leaving the active scene/lifecycle or when system motion is disabled. Small
roster portraits stay still. Both caches remain bounded to 192 images.

Native demo captures and the contact sheets are reviewed visually before
release. Current device/account limitations remain in PLAN.md.


## Instant previews and authored looks

New previews use eight prebuilt adult 48×48 silhouettes, restricted garment
colors, twelve independent creatures and a bounded twelve-entry look undo stack.
No model or image request is needed for the initial preview. The optional
persona model call has a 35-second timeout and 1,800-token budget; the preceding
catalog request is separately bounded to 12 seconds. AI completion merges only
unchanged persona fields and preserves reviewed appearance and permissions.
Creation intersects the reviewed tool catalog with the current engine catalog.

Both native renderers hash sixteen authored-look fixtures, including garment
variants. Python checks that recolors preserve identity and alpha; atlas checks
reject a stray divider line above a head. The remaining original layered engine
and its blink tests are compatibility coverage. Static modern previews do not
run the decorative blink loop. Android status pixels no longer run an infinite
animation while idle.

The separately sandboxed full Debug iPhone acceptance passed an actual DSH
model stream, eight seconds in the background and a cold-launch history restore:
[evidence](evidence/ios-build10-iphone-model.json). Production build 12 failed
its native connection. Diagnostic build 13 passed once, then reproduced an
initial `ECONNRESET` during repeated cold launches. DSH listens before registering
its event upgrade route; the old HTTP readiness check accepts that early listener.

Build 14 waits up to ten seconds for that initial event attachment, with bounded
backoff for connection resets/refusals and temporary 404/503 responses. Once a
stream has opened, disconnection fails visibly and is never retried by the
adapter. Cancellation clears the deadline and pending retries. Authorization
failures fail immediately. Forty Node checks include nine real-socket regressions
for this startup window, response filtering, cancellation, timeout and established
stream loss. The test-only `ws` dependency is pinned to the bundled DSH version;
it is not another production runtime dependency.

Build 14 passed the production phone smoke and all three repeated cold launches.
Two repetitions hit the initial reset and recovered in 0.324 and 0.304 seconds.
Each run verifies native roster/navigation, eight seconds in the background and
Scout attachment with an unsent draft. The temporary runner was removed and a
scoped query confirmed only production Niobium remains. No production app data
was erased. [Sanitized evidence](evidence/ios-build14-iphone-startup.json) includes
the before/after startup traces. Real-account turns and delegation remain separate
acceptance work.


## Beta.7 wardrobe, image jobs and local conversations

The wardrobe now has 32 base looks, 64 shared rendered-look fixtures, 12
transparent stock creatures and shared 16/48px indexed custom-art fixtures.
Original eight looks remain pixel-identical. Both native UIs exercise the
filtered wardrobe and optional AI flow without paid image calls.

Image-job tests cover stored receipt recovery via GET, ambiguous POST/server
failures without automatic resubmission, provider cancellation transport,
rejected redirects, strict bounded indexed pixels and opaque-background
rejection. Network fixtures use fake keys; iOS tests inject an isolated memory
vault. Android instrumented coverage tests actual Keystore encryption in its own
unique namespace. Real image-provider account acceptance remains separate.

Local messaging tests verify actual member/session attribution, separate room
and DM sessions, durable idempotency even after transcript trimming, startup
recovery of accepted work without replay, bounded @mention feedback, reviewed
messaging access, pending questions, cancellation, corrupt-store preservation,
archival and room activity in the roster. The Android controlled model fixture
calls the real registered `message_agent` tool, receives Scout's actual DSH
reply and restores that DM in native Rooms.

Engine tests switch workspaces with both chats attached, preserve each transport
and isolate disconnection, approvals and history. Failed room sends retain a
receipt for explicit retry and leave the UI draft intact. The isolated full
build 15 simulator acceptance preserves a streamed reply over eight seconds in
the background and restores history after a cold guest launch. Production
build 14 remains the latest physical iPhone acceptance until the phone unlocks.

The Android DSH integration also emits a real `ask_user_question` call inside
the DM room, checks that the room-owned request reaches native state, submits
its native answer, and verifies Scout continues. It then clears the room and
checks DSH's archived session never becomes Scout's direct-message session.
Receipt retention regressions cover hundreds of acknowledged sends, preserved
unconfirmed IDs and synchronization after the unconfirmed window fills.

Controlled-clock room tests cover a ten-minute suspension, saved active-time
budgets across restart and questions waiting for the user. Accepted work resumes
through history without submitting another prompt. The actual Android DSH DM
test also opens Clear room, cancels it while checking durable history remains,
then explicitly confirms and verifies archival.

Session-provisioning races are covered with held create/rename RPCs: stopping
prevents a paid prompt, clearing waits for in-flight ownership to settle, and
failed archival retains room ownership for a later retry. The real Android DSH
test starts through the engine hub with a local Pixel launch target and verifies
that its chat opens in the selected local workspace.

An isolated Node/WebSocket Hermes fixture now runs inside the Android test
sandbox to prove explicit gateway launches override saved DSH selection and
open the requested remote chat. Android reaches its real DSH DM through Pixels
→ Rooms before clearing it. The full iOS DSH lifecycle test also opens Rooms
from Pixels, then returns to its model/history acceptance flow.

iOS provisioning tests assert that reviewed messaging survives the native
permission filter and reaches `pixel.create`, while an unavailable catalog entry
still removes that grant. A held-acceptance room test presses Stop before the
receipt returns, verifies a second cancellation after acceptance, and proves no
prompt is replayed. Explicit Stop also cancels known sessions with uncertain
receipts.

## 2.1 beta.1 desktop acceptance

`python3 tools/prepare-desktop-runtime.py` verifies the pinned official Node archive,
installs only the locked DSH graph, restores the audited native helper permission,
and checks WebSocket, Koffi and real PTY execution before staging the bundle.
`cd desktop && ./gradlew :core:test :test` ran 33 shared Kotlin checks and 18 desktop tests for that release. UI tests capture the actual Compose wardrobe. Linux runs
these UI checks under Xvfb, then creates its own native `.deb`; macOS builds `.dmg`.

Desktop acceptance uses an isolated temporary DSH home and a local model fixture:
real preset creation, reviewed messaging plugin exposure, streamed reply, native
chat restoration, room creation, Stop/restart and persisted appearance/history.
The fixture makes no external model request. Tests delete their temporary trees
with NIO traversal that does not follow DSH's dependency symlinks.
Additional checks exercise independent engine stores, stale connection work,
proxy authentication/origin/host rules, private socket lifetime, durable room
receipts, corrupt receipt preservation, single-instance ownership, exact shared
RGBA fixtures and rejection of opaque generated-art backgrounds.

Android regression checks after the shared-core extraction pass the JVM suite,
debug build and lint, plus 16 ARM64 Android 16 emulator instrumented tests covering
the renderer, Android Keystore, native DSH streaming, service lifecycle, private
access and workspace preservation. Physical Android and actual 16 KB device
execution remain separate acceptance tasks.

## Review provider

CodeRabbit is the selected reviewer. `.coderabbit.yaml` enables assertive reviews
and incremental review of pushes, with specific protocol, ownership, runtime,
packaging and Android instructions. It disables unsolicited chat replies and
label/reviewer assignment. `.greptile/config.json` disables Greptile automatic
reviews, status checks/comments and automatic approvals for this repository.
Historical release evidence retains the reviewer that actually ran at the time.

The CodeRabbit GitHub App is connected to `wckdboy/niobium` and has reviewed
[PR #11](https://github.com/wckdboy/niobium/pull/11) using this configuration.
Request a fresh complete review with `@coderabbitai full review`. Configuration
reference: https://docs.coderabbit.ai/reference/configuration .

The 2.1 desktop review adds forced early-handshake publication and slow-probe
responsiveness regressions, strict canonical resume parameters, failed-tool
status/capability checks, cold restart after app relocation and legacy messaging
path migration. Runtime source inputs invalidate the Gradle test task.
`tools/check-desktop-package.py` checks the actual packaged launcher and its
private DSH API with global Java/Node removed from PATH. It verifies runtime
hashes and every recorded executable mode; packaging restores those modes after
jpackage copies resources. Desktop familiar sprites now use Android's authored
source directly. CodeRabbit approved the release commit in PR #11 using `.coderabbit.yaml`.

The shutdown regression lets an owning DSH process finish delayed disposal of a
detached child before escalation; Node coverage is now 67 checks. The race test
uses a monitor checkpoint rather than a timing assumption. Linux verifies both
the application image and the extracted DEB; generated DEB permissions are
restored before upload. macOS verifies the application image and mounted DMG.

The AI-art controller also verifies that an unusable completed image becomes a
terminal failure, retains its receipt and starts another billable design only on
an explicit request. Wardrobe selection opens the chosen look in the workshop.
That release has 18 desktop tests, alongside 33 shared-core tests.

Installer tasks also track the runtime source inputs, and package verification
compares adapters and the dependency lock against the repository. This catches
stale installed resources after an incremental runtime-only build.

Android native package URLs use the dedicated `android-runtime-2026.10.22`
release archive after Termux removed a pinned URL. Original upstream URLs,
versions and checksums remain recorded in the lock. All mirrored download bytes
were checked against the original SHA-256 values; corresponding sources accompany
the archive. Fresh CI downloads verify the same bytes.

The final CodeRabbit pass adds three shared transport checks: cleartext remote
addresses fail before credentials are sent, and neither status probes nor socket
handshakes follow redirects. HTTPS is required outside literal loopback addresses.
Startup timeouts now report a retryable failure after owned process cleanup; a
regression retries without restarting the app. Concurrent Stop callers share the
same cleanup promise. Current suites: 33 shared-core, 18 desktop, seven Android-
specific JVM tests, 67 Node and 15 Python checks.

Gateway request deadlines report transport errors rather than masquerading as
caller cancellation. External coroutine cancellation still propagates. A delayed
WebSocket handshake also clears the desktop Connecting state with a retry action.

The macOS package minimum is 13.5, matching the bundled Node Mach-O load command.
Installer verification compares the app Info.plist against that binary minimum.

[2.1 beta.1](https://github.com/wckdboy/niobium/releases/tag/v2.1.0-beta.1) is
published from `b683440`, approved by CodeRabbit and merged in PR #11. All active
[release CI jobs](https://github.com/wckdboy/niobium/actions/runs/39961030217) pass.
See [release validation](evidence/beta21-release-validation.json) for artifact
hashes, device receipts, review, merge and website deployment evidence.

## Native provider setup (2.1.1 candidate)

The current suite has 33 shared-core, 22 desktop, seven Android-specific JVM,
79 Node and 15 Python tests. The provider integration runs real pinned DSH against
loopback DeepSeek chat, OpenAI Responses, Anthropic Messages and compatible chat
streams. It checks exact credential routing, explicit defaults, preserved original
conversations, key-free settings, owner-only credential files and cold restart.

Provider editor tests cover masked input, deliberate model selection, keeping an
existing default and a key-optional compatible endpoint with model limits. Adapter
checks cover dormant catalogs, preservation of other configuration, stale edits,
read-only credentials, invalid endpoints, no-auth key isolation and partial failures.
The Android app-domain check saves all three vendor keys through the legacy API
and verifies active catalogs, unchanged defaults and credential privacy.

These fixtures use no real account and make no billable provider requests. Native
Android streaming is repeated separately after the adapter update. Account/model
entitlements, real provider acceptance and physical Android/16 KB execution remain
open. The API fixtures follow the pinned SDK and the official streaming references:
[OpenAI](https://developers.openai.com/api/reference/resources/responses/streaming-events)
and [Anthropic](https://platform.claude.com/docs/en/build-with-claude/streaming).
