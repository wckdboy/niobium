# Niobium v2

The goal is a complete native desktop and mobile surface for Hermes Bot Mode, with an
optional on-device DSH runtime, a professional pixel-art interface, and Pixels
that feel like individual teammates.

Reference: [the supplied dashboard](docs/reference-dashboard.png).
Ledger: [Plane](https://app.plane.so/wckd/projects/2fbcf987-20de-43dc-80ac-0a6bab7ec03a/issues/).
Protocol audit: [HERMES-PARITY.md](docs/HERMES-PARITY.md).

## Active focus: Linux, macOS and Android

- Build and ship the Compose desktop preview alongside Android; retain iOS sources
  and make its CI/signing work opt-in while desktop and Android are the priority.
  The 2.1 beta.1 desktop preview and Android 2.1/code13 are published.
- Share Kotlin protocol, persona/spec validation and art reduction across Android
  and desktop. Keep engine connection/session/prompt ownership separate.
- Native desktop DSH: pinned private runtime, direct host process, authenticated
  loopback API over a private DSH Unix socket, explicit lifecycle and safe shutdown.
- Complete controlled model/room/restart acceptance, Linux and macOS packaging,
  Android regressions, and public download verification before declaring a release.
- Next desktop work: additional local provider setup, OS credential vaults, richer session/history controls, companion
  task monitoring, skill/MCP/routine surfaces, accessibility and long-running work.
- Continue real-account acceptance, physical Android/16 KB execution, cross-engine
  rooms, Hermes replay and connector authorization. These remain unverified goals.

## Decisions

- Kotlin/Compose on Android and Linux/macOS desktop, with a shared Kotlin core.
  Existing SwiftUI iOS/iPadOS sources remain available.
- Shared PixelSpec, bot-build skill, character recipes, palette and terminology.
- 48×48 portrait output, cached and displayed without smoothing; smaller
  companion sprites. Original adult fantasy/cyborg cast, with mature outfits
  and a mischievous voice. All human characters are 18+.
- The app follows the pale lavender reference. The website follows the shared
  WCKD mono/rules/one-colour brand system.
- Every New Pixel entry goes through design, review and creation.
- Generated capabilities come from the connected gateway's catalog.
  Credentials, connections, tool access and host sandboxing remain distinct.
- Companions represent real delegated work. The parent owns verification.
- DMs and group rooms use Hermes attribution and gateway-owned state.
- Unsupported methods, incomplete setup and uncertain delivery are visible.

## Completed implementation in this pass

- [x] Shared pixel panel components, original landscape/cast art and native
  48×48 cached rendering.
- [x] Compact Home overview with six Pixels and a working link to the full
  roster; measured gateway latency and honest runtime labels.
- [x] Reviewed Pixel builder with strict names, adult age, layer, briefing and
  capability validation; correctly labelled model/template results.
- [x] Actual tool/skill pins, manual host approvals, disabled inherited MCP
  servers, versioned persona metadata and partial-creation error handling.
- [x] Appearance metadata persistence with revision checks.
- [x] Companion list/tail/steer/interrupt UI and failed-delegation visibility.
- [x] Pixel mentions in direct chat.
- [x] Generation-fenced model/reasoning/Stop controls and running-state restoration.
- [x] Canonical recovered approval and clarify responses.
- [x] Transient secret/vault/code/login prompts with transport ownership.
- [x] Reconnect timeout ownership and cancellation-aware iOS RPC waiters.
- [x] Remote harness error/retry/teardown and encrypted saved addresses.
- [x] Remove the 81 MB guest image from the Hermes-only iOS target.
- [x] Consolidated WCKD website, internal checks and Cloudflare deployment.
- [x] Swift validation checks, Android unit tests, native build checks and
  Home/review captures.

These checkboxes record implementation and the listed checks. They do not
replace the live integration acceptance criteria below.

## Next: prove the complete workflow

- [ ] Run the workshop against a provider-backed gateway: valid generation,
  invalid generation, no model, inaccessible catalog, duplicate slug and
  failure after profile creation.
- [ ] Verify copied skills, effective tool pins, manual approvals, credential
  inheritance and metadata compare-and-swap against the actual host.
- [ ] Create multiple distinct Pixels and verify canonical DMs, @mentions,
  durable group rooms and reconnect attribution.
- [ ] Delegate actual tasks; inspect live tails, failed work, steering delivery,
  interruption and ownership after reconnect.
- [ ] Exercise approval/clarify recovery and cancellation across transports.
- [ ] Verify physical-device accessibility, dynamic type, keyboard behaviour,
  connection loss, battery usage and large histories.

## Complete the Hermes surface

Prioritize these domains without replacing backend behaviour with simulations:

1. **Admissions and replay:** retained outbox IDs, receipts/cancel, uncertain
   delivery, queue editing, steer/redirect, replay cursors and epoch changes.
2. **Sessions and models:** history paging, rename/archive/delete/export,
   provider/account management and live reasoning acceptance.
3. **Connections:** connector account/catalog/policy flows and connection
   operation cards; MCP testing, OAuth, setup and status.
4. **Knowledge and projects:** skill details/install/edit, command catalog,
   memory journey editing, projects and workspace mutation.
5. **Rooms:** state, rename, stop, approvals, retry, attachments and qualified
   cross-connection identities.
6. **Files and voice:** file/PDF attachment, upload bounds/downsampling, host
   recording/TTS and supported device bridges.
7. **Background delivery:** durable reconciliation and a native notification
   design that respects mobile execution limits.
8. **Android local runtime:** the ARM64 PRoot/Alpine beta now passes cold boot,
   DSH HTTP/WebSocket, service Stop and image-swap preservation tests. Next prove
   real-account provider turns, host bridges, physical/16 KB devices, battery and
   encrypted provider storage. Native preset creation and fixture model turns pass.

## Release gates

- Both native clients and the iOS full target compile.
- Permission and contract tests pass with meaningful failure cases.
- Live gateway scenarios above pass, including reconnect and partial failure.
- Small/large phone and tablet layouts, both themes and accessibility are
  inspected.
- APK and device-signed iOS artifacts are validated for their intended audience.
- README, website, Plane and public release notes describe the same state.
- Website links/assets and deployed content are verified; browser QA limits
  are recorded explicitly.
- Full Hermes parity is claimed only after the remaining domain work and live
  acceptance checks are complete.

## Release and next implementation steps

- Published [2.0.0-beta.1](https://github.com/wckdboy/niobium/releases/tag/v2.0.0-beta.1)
  with signed Android APK, full iOS distribution IPA, checksums, runtime/source
  metadata and the complete improvement notes. iOS 2.0 (4) uploaded successfully
  to App Store Connect and Apple began processing it. PR #1 is merged to main.
- Native/shared suites passed. Greptile’s seven findings and six earlier Codex
  findings were fixed and resolved; regression cases are in the native suites.
- The daily [Hermes watcher](docs/HERMES-UPDATES.md) is on main; review
  new contracts and tools before updating the audited baseline.
- Android embedded DSH now uses APK-packaged PRoot with the pinned Alpine/Node
  image. Physical-device and provider-backed acceptance remain.
- Then complete receipts/replay, connector authorization and room control.
- Polish modular character art to the standard of the authored adult cast.

Reproducible verification is in [TESTING.md](docs/TESTING.md).

## Current cycle: 2.0.0-beta.2

- Android now offers on-device DSH before Hermes setup, with native pixel controls.
- The lowercase n is shared by website headers, app icons, launch and in-app marks.
- Fixed clipped WebView dialogs, malformed skill frontmatter, same-profile room attribution,
  stale clarification acknowledgements and disconnected transport prompts.
- Next: accept the runtime on physical/16 KB devices with a provider-backed turn;
  add native DSH preset creation, provider vault, files/PTY and actual delegation checks.
- Hermes watcher detected upstream member_activity in groups.state at 81a55437f3f31a9f12a30c53b6c5d49d420898aa.
  Add capability-gated room activity next; keep the audited baseline until acceptance passes.
- Active-session build/test/review/release cycles work. Unattended continuation needs
  a supported wake/timer integration; a shell sleep loop is not installed.

## Harness2 evaluation

Inspected google-research/harness2 at commit
1cfe254e8d25b15f37048c3730134686abe60335 (Apache-2.0, Python 3.12/3.13).
Its deterministic local demo passed both recursion modes with k=2:
two tasks, zero failures, 28 Python executions and 32 deterministic editor calls.
No model credentials were used. The evidence pin is in config/harness2-evaluation.json.

The proposed Niobium use is an optional **Improve this Pixel** experiment on the
PC/VPS, with a Hermes substrate adapter. Existing upstream executors are
OpenCode and Codex; Hermes/DSH integration has not been implemented.

- Start with one probe step on explicit test tasks in an isolated workspace.
  Bound model cost, elapsed time and companion fan-out; side-effecting user
  tasks must not be silently repeated as probes.
- Map proposed persona/skill/delegation changes to a reviewed PixelSpec diff.
  Keep provider secrets, connector authorization and enforced permissions
  outside the editable harness. A prompt must not grant access.
- Retain run evidence, compare with the original Pixel, and adopt a versioned
  change with rollback after review. Confirm claimed improvements with
  independent task checks rather than model preference alone.
- Prove cancellation, reconnect, partial failure, evidence redaction and
  unchanged access before exposing the control in the app.

Source: https://github.com/google-research/harness2/tree/1cfe254e8d25b15f37048c3730134686abe60335

Release review fixed four Greptile findings: unauthenticated header deadlines,
serialized service completion/restart, preservation of edited skills, and valid
source checksum manifests. Runtime regression tests cover the first three;
the bundled source manifest passes standard SHA-256 verification.

Final beta.2 artifacts: Android 2.0 (5) and full iOS 2.0 (6). The iOS package
uploaded successfully to App Store Connect for processing; tester availability
still requires verification there. Android installation budgets include measured
4 KB file allocation rather than compressed/unpacked byte counts alone.

## Shipped: 2.0.0-beta.3 — native DSH provider and coherent UI

- One native Home, Pixels, workshop and chat surface for Hermes and local DSH.
- Engines chooses the provider and retains saved Hermes settings. DSH stays
  behind the app; raw web, terminal and Stop are explicit advanced controls.
- Shared pinned-API adapter creates real DSH presets from reviewed PixelSpecs,
  restores native chats/requests, streams model replies, switches models and
  maps companion controls. Unsupported Hermes rooms/routines are hidden locally.
- Android real-runtime test passes native creation, a controlled model turn,
  streaming and history restoration. Required hard-link emulation is enabled.
- Native provider keys, scoped appearance identities, pixel navigation/icons
  and cleaner provider-aware copy are implemented on both platforms.
- Next: physical iOS/Android and 16 KB acceptance, real-account model turns and
  delegation, encrypted provider vault, then Hermes member activity and replay.
- Review fixes cover atomic preset metadata, multi-select answers, partial-turn
  recovery, snapshot/stream handoff, older history pages and all saved chat rows,
  full-log usage projections and failed-image cleanup.
- Validation: 24 iOS unit + 3 UI tests; 24 Android JVM + 5 app-domain runtime
  tests; 18 shared adapter + 13 watcher tests, plus build/package/site checks.
- See docs/DSH-PROVIDER.md and screenshots/README.md for boundaries and evidence.

Beta.3 release completed 2026-10-22: PR #4 merged at
90c6a91ec3563ca2fbef0543de69b9ed304ab7e1; CI run 39772986232 and final
Greptile review passed, with all eight threads resolved. The signed Android
2.0 (6), full iOS 2.0 (7), source bundle and checksums are published at
https://github.com/wckdboy/niobium/releases/tag/v2.0.0-beta.3. Apple accepted
build 7 for processing; tester availability remains unverified. The site was
deployed as Cloudflare version 2549f0c3-9bb9-4493-9d7b-7403a1e5d3e7.

Physical iPhone follow-up: full build 7 installed over build 6 on an iPhone 18
Pro Max running iOS 27.0.1. The opt-in native DSH smoke test passed startup,
Scout roster, native Engines/provider settings, Home-screen return and native
chat UI. The test sends no model request; real-account turns, delegation,
long background/suspension behavior and broader device acceptance remain.
Reproduce with tools/check-ios-device.py; sanitized evidence is in
docs/evidence/ios-build7-device-smoke.json.

## Shipped: beta.4 device acceptance and readability

- Strengthen the shared dashboard scrim and use the pixel icon set for engine stats.
- Gate Send on a connected, attached session; retain drafts during startup/offline.
- Prevent repeated Pixel opens from racing the local attachment handshake, and
  discard obsolete session lookups before they can change the backend selection.
- Keep Send disabled if the local attachment handshake fails. Regression coverage
  now includes duplicate opens, a delayed obsolete lookup and a rejected handoff.
- Exercise an eight-second background handoff and a cold history reload.
- Add an isolated Debug-only model fixture flow on iOS, with a separate app
  identity and Keychain group. The production binary contains no fixture hook.
- Full iOS simulator model acceptance passed in 87.152 seconds: a unique
  current-turn reply, eight seconds on the Home screen, finished native stream
  and restored history after a full app/guest restart. Evidence:
  docs/evidence/ios-build8-simulator-model.json.
- Validation: 28 iOS unit + 3 UI tests; 25 Android JVM + 5 runtime tests;
  31 shared Node tests, 5 asset checks and 38 Swift PixelSpec assertions.
  Both themes were inspected on iOS/Android. Signed Android release and full
  iOS archive/export pass; Release fixture-hook exclusion is verified.
- Published versions: Android 2.0 (7), full iOS 2.0 (8). PR #6 merged at
  00e9975548b9777c25713fa06064266c1aafdb37. Final head 0a589bd and CI run
  39792307093 pass shared, Android and iOS suites. Greptile reviewed 32 files
  with no new comments; both review threads are resolved.
- Beta.4 assets, runtime sources, metadata and SHA256SUMS are published at
  https://github.com/wckdboy/niobium/releases/tag/v2.0.0-beta.4. All five GitHub
  asset digests match the staged files. App source is d16bda4, unchanged by the
  later test/documentation fixes.
- Apple accepted full iOS 2.0 (8) at 2026-10-22T14:14:59Z for processing.
  TestFlight tester availability is unverified. The iPhone is unavailable, so
  build 8 physical model/lifecycle acceptance remains pending. Build 7 physical
  smoke covered UI only.
- niobium.sh deployed as d208831f-9b39-4c71-8f21-0f8d9c372063. Exact deployed
  bytes match all six pages, www Home, favicon, stylesheet, skill and icon.
  Plane NIO-24/29/32 and both local exports record the same evidence and limits.
- Next loop: physical build 8 and Android/16 KB acceptance, real-account model
  turns and actual delegation, then encrypted provider storage and Hermes replay.
  After device acceptance, remove the temporary acceptance app/test runner;
  preserve the production app and its workspace.

## Pixel atelier cycle — 22 October 2026

Merged in PR #7 at 20c4b9218e58a4c1583ddea06fd05165dd08753d.
Beta.5 packages reviewed implementation ed8f661 (Android code 8 / iOS build 9):

- New adult cast art without embedded creatures, baked to one canonical 192×96
  atlas. Native clients use identical exact 48×48 cells and physical-pixel scaling.
- More detailed procedural layers; independent skin/hair colors, new hair, outfits,
  expressions, accessories and face details. Legacy six-field recipes stay valid.
- Shared editor in creation review and Pixel studio: nine traits, pinned local
  remixes, original-look restoration, and twelve independent 16×16 companions.
- Eight offline persona voices and working methods, explicit character cues,
  corrected word-based role matching, and an updated bot-build skill.
- Portable seed/RGBA fixtures, native renderer checks, pin/legacy/type tests,
  native editor interactions and reproducible art/catalog checks.
- Full build 9 DSH simulator model/history acceptance passed in 104.198 seconds.
  Android’s five runtime, two renderer and new editor interaction tests passed.

Next: lift custom layered portraits to the authored cast's quality; improve persona
naming/voice variety within each offline role; add undoable appearance history;
complete real-account delegation and physical-device acceptance. The iPhone is
still unavailable, so current physical acceptance and temporary QA-app cleanup
remain pending. Keep provider-key vault work, Android 16 KB devices and the
reviewed Harness2 evaluation on the following runtime cycle.

Final CI run 39822663459 passes shared, Android and iOS jobs. Its downloaded
iOS xcresult verifies 37 passed tests, zero failures and two expected opt-in skips.
The final independent Codex review reports no findings. Greptile found two
distinct issues (skill-example escaping and pin lifetime), now fixed with
regressions; all five threads are resolved. Its 50-credit trial quota blocked
the final Greptile re-review.

Apple accepted build 9 for TestFlight processing at 2026-10-22 17:37:46 UTC.
Tester availability remains unverified. The temporary simulator acceptance app
and runner have been removed; physical QA-app cleanup still awaits the iPhone.

Beta.5 is public at https://github.com/wckdboy/niobium/releases/tag/v2.0.0-beta.5
(published 2026-10-22 17:43:05 UTC). All five GitHub asset names, sizes and digests,
and the public metadata/checksums, match the staged files. The APK keeps the
existing certificate. niobium.sh and www were deployed as version
49b8f0be-0d15-47fe-a678-7df3c88a0b58; all six pages, www, the stylesheet, icon,
cast, familiar strip and downloadable skill match the built HTTP bytes.
Plane NIO-3/10/12/24/29/32/36 and both local exports record the release and limits.


## Modular portrait cycle — 2.0.0-beta.6 candidate

- Rebuilt custom 48px source layers around smaller adult faces, longer torsos,
  a tilted shoulder line and a hip-set pose. Twelve outfits have two tailored
  cuts; skin, hair and garment pigments remain independent.
- Bold opaque crop tops, strappy panels, open coats, bare shoulders and midriffs
  retain adult fantasy/cyborg styling. All human characters remain 18+, with
  28–40 as the workshop default. The authored cast stays independent.
- Large custom previews use two cached open/blink frames. Small portraits stay
  still; motion is scoped to mounted active previews and system preferences.
- New structural art gates cover eye occlusion, every outfit cut, semantic
  pigments and silhouette stability. Swift/Kotlin hash both actual frames.
- Generated visual reference, exact prompt, actual custom gallery, outfit-cut
  sheet and frame comparison are versioned with provenance. The website shows
  actual custom recipes, not the higher-resolution reference.
- Android candidate code 9; full iOS candidate build 10. Release and upload
  status will be recorded after verification.

Next: undoable appearance history, saved looks and names with more variation
within each offline role; physical/current real-account and Android 16 KB
acceptance; provider-key vault, actual delegation and Hermes replay.

Candidate validation: 33+4 iOS tests, 29 Android JVM and nine final code-9
ARM64 checks pass; eleven Python art/assets, 31 Node and 38 Swift assertions
pass. Full build 10 DSH simulator model/background/cold-history acceptance
passes in 99.595 seconds (docs/evidence/ios-build10-simulator-model.json).
The full signed archive and distribution IPA pass current-resource, signature,
identity and Release fixture-hook exclusion checks. Independent review of
implementation eaefe46 reports no findings. A later wireless physical run boots
DSH but fails native roster attachment. Physical acceptance has not passed;
publication is held. Greptile remains at its trial quota.


Candidate delivery status (22 October, after wireless device testing):

- Signed Android 2.0 (9) and full iOS 2.0 (10), unchanged corresponding runtime
  sources, metadata and checksums are uploaded to the unpublished
  beta.6 draft (release ID 421093112).
  All five GitHub names, lengths and digests match local staging.
- Apple accepted build 10 for processing at 18:55:09 UTC. Tester availability
  remains unverified.
- [PR #8](https://github.com/wckdboy/niobium/pull/8) is open. The native
  implementation reviewed at eaefe46 has no findings. The final metadata
  follow-up review remains running; Greptile cannot review at its trial quota.
- The latest Mac runner remained queued for about 20 minutes. The client suite
  now pins the supported macOS 15 ARM64/Xcode 26.3 image, with the same tests.
  [CI 39837175768](https://github.com/wckdboy/niobium/actions/runs/39837175768)
  passed all three jobs: 37 iOS tests pass with two opt-in skips; shared and
  Android also pass. Follow-up diagnostic source changes need fresh CI and
  replacement signed iOS artifacts before release.
- The paired iPhone is reachable over the local network without a cable. The
  build-10 production smoke launches, boots Linux and reaches server readiness
  in 17.229 seconds, then fails with an empty native roster and Error state.
  The exact connection failure still needs diagnosis. The controlled model run
  was blocked before app launch by the lock screen and cancelled. See
  docs/evidence/ios-build10-wireless-device.json.
- Follow-up source exposes connection failures in Home/Engines and redacted DSH
  logs, prevents Auto-Lock during full-app acceptance, and improves helper lock
  preflight/result collection. All 38 client tests pass (two opt-in skips),
  including 19 gateway-state tests. The full diagnostic device app builds.
  These source changes are not in the existing build-10 distribution artifact.
- Beta.6 publication and site deployment remain held for the native connection
  fix, fresh build and physical acceptance. Temporary physical QA-app cleanup
  remains pending; the production app and data have been preserved.
- Plane NIO-3/10/24/29/32/36 and both exports record the candidate and limits.
  The public website/release remain beta.5 until beta.6 publication.

Next cycle acceptance:

1. Undo restores the complete draft appearance and companion identity. Keep a
   bounded history in the parent editor so folds and lazy-list disposal retain it.
2. Named saved looks use versioned recipes and provider-qualified identities,
   with bounded storage and clear apply/delete controls. Save still uses the
   existing metadata revision check.
3. Offline roles gain deterministic name/voice variation while retaining explicit
   user cues, stable profile IDs, adult ages and clearly labelled template output.
4. Retry the physical build when unlocked, remove only isolated QA apps, then
   complete real-account turns/delegation, physical Android/16 KB, provider-key
   storage and Hermes replay.

A scoped device query verifies ai.wckd.niobium 2.0 (10) is installed on the
iPhone. No production uninstall was performed. Physical tests exited before
launch after the lock-screen/runner connection block; behavior remains
unverified. Evidence: docs/evidence/ios-build10-iphone-install.json.


## Clean UI and instant portrait cycle — beta.6 replacement candidate

The next candidate is Android code 10 / iOS build 12. Build 11 was diagnostic
only; its shared overlay version makes it unsuitable for repeated adapter edits.

- New Pixel previews are local and immediate. Optional AI refines persona;
  it no longer blocks the first portrait behind a long model request.
- Four short tabs separate Look, Creature, Persona and Access. The create
  action stays visible. Dashboard telemetry and repeated explanations are reduced.
- Cards, lists, controls and tab accents share flat stepped pixel frames.
  Android idle status indicators no longer schedule an infinite animation.
- Eight authored adult fantasy/cyborg silhouettes replace the default procedural
  mixer. Garment colors, remix and bounded undo preserve face, hair and skin.
  This is a curated library with color variants, not unlimited generated artwork.
- Saved layered recipes still load with their original controls. Modern look
  metadata round-trips through each provider; explicit appearance choices win.
- The engine catalog is checked before creation and can only narrow reviewed
  access. Persona refinement cannot overwrite changes made during the request.
- A raw local WebSocket refusal regression verifies that the real socket error
  reaches the caller immediately instead of becoming generic “not connected.”

Validation: 39 iOS unit and five demo UI checks, 30 Android unit and ten ARM64
emulator checks, fourteen Python checks, 31 Node checks and 38 standalone Swift
assertions. Both native look renderers match sixteen shared RGBA fixtures.
Build/lint, runtime package hashes and ELF alignment, site and Plane checks pass.
Final remote CI and packaged release signatures remain separate release gates.

The physical full Debug acceptance passed the controlled model/background/cold
history workflow over Wi-Fi in 73.243 seconds on the existing test device;
see docs/evidence/ios-build10-iphone-model.json. Production Release build 11
loads DSH but fails its upstream event stream. The error now remains visible;
a build-12 retry with bounded HTTP/error-code diagnostics is pending the unlocked
phone. Keep beta.6 in draft until replacement artifacts and production acceptance
are verified. Apple has accepted only the previous build 10; build 12 is not yet
uploaded. Greptile remains unavailable at its trial quota.

Next: finish the production event-stream diagnosis, accept the replacement on
the paired phone, then publish verified artifacts and update the public site.
After that, expand the authored portrait library with reviewed variants, add
named saved looks, provider-key storage, real-account delegation and physical
Android/16 KB acceptance. Look undo is implemented; a cross-creature history and
unlimited bespoke sprite generation are not claimed.


Verified merge and draft replacement (2026-10-22 UTC): PR #8 merged at e6d8fc1.
All three CI jobs pass at 379cf8b (run 39861554438); its Git tree exactly matches
the merge. Signed Android code 10 and full iOS build 12, current corresponding
runtime sources, metadata and SHA256SUMS now replace the older draft payloads.
All five GitHub asset names, sizes and SHA-256 digests match local staging; the
old build-10 IPA was retired. The public release and website remain beta.5.

Full Release build 12 also passes simulator native startup, roster, attachment
and background restoration in 50.698 seconds with port 3080 deliberately busy
and DSH on 3081. This does not reproduce the production phone failure. The
scoped phone query confirms only Niobium 2.0 (12) remains after removing the
temporary acceptance and UI-runner apps. Production was updated in place; it
was not uninstalled. The device test runner is prebuilt to reduce the next
unlocked-device wait. The phone remains locked and production acceptance stays
pending. Evidence: docs/evidence/clean-pixels-validation.json and
docs/evidence/ios-build12-iphone-install.json.

Main merge CI also passes all three jobs (run 39862983795). The Release IPA
executable excludes the Debug model-fixture hooks.

## Production DSH startup follow-up — 23 October

An unlocked-phone run reproduced build 12's connection failure. Diagnostic
build 13 passed once, but three repeated cold launches included an initial
`ECONNRESET`. The pinned DSH HTTP server accepts connections before registering
its event upgrade route; the old readiness probe accepts that early listener.

Android code 11 / iOS build 14 adds a bounded ten-second startup wait in the
shared native adapter. Only the initial connection is retried, with backoff for
transient socket failures and 404/503 responses. Authentication failures are
immediate; client closure cancels all timers. An established stream failure is
visible and never causes model requests to be replayed. Nine real-WebSocket
regressions pass, bringing the shared Node suite to forty checks.

The production build-14 phone test and all three repeated cold launches pass
native startup, roster, Engines, eight seconds in the background and Scout's
real chat attachment. Two initial resets recover in 0.324 and 0.304 seconds.
Android passes 30 unit and ten ARM64 emulator tests, including a controlled DSH
model turn; lint, the signed APK and iOS archive/export pass. Production was
updated in place over Wi-Fi; Pixels, workspace and keys remain. The temporary
runner was removed. Evidence: docs/evidence/ios-build14-iphone-startup.json.
Release publication, Apple upload and website deployment follow fresh CI and
verification of these replacement artifacts.

Next after this release: provider-key storage, real-account turns and delegation,
physical Android/16 KB acceptance, named saved looks and expanded authored art,
then Hermes replay and connector authorization. Full Hermes parity remains open.


Verified beta.6 checkpoint: PR #9 merged to main at 6fa4b0c, whose Git tree
matches CI-tested source 0e76310 exactly. All three CI jobs pass (39869124900).
Independent Codex review completed with no findings; Greptile hit its trial quota.
Beta.6 is published with Android code 11, full iOS build 14 and matching sources,
metadata and checksums. Apple accepted build 14 for processing at 23:13:15 UTC
on 22 October; tester availability remains unverified. Phone acceptance and
release verification are recorded in docs/evidence/ios-build14-iphone-startup.json
and docs/evidence/dsh-startup-release-validation.json.

## Next product cycle: wardrobe, AI design and connected teams

The owner requested both a much larger instant wardrobe and optional AI-created
clothes, styles and companion art. Keep clearly adult character designs, stable
identity, crisp 48px output, transparent creature cutouts and fast native controls.
The existing creature sprites already have alpha; remove the opaque native
backplates. AI design is explicit and reviewable; it must preserve the existing
look until a new result is accepted and must not block the instant preview.

Use OpenMausBot as interaction and architecture inspiration, pinned for research
at 30b62aaf18adc22099c24a7181abf5e9ddead980. Keep Niobium's native pixel identity.
Priorities: independent Hermes/DSH connection ownership, simultaneous or individual
operation, durable DM/room routing with clear sender/engine attribution, then
provider-backed acceptance. Existing Hermes messaging is implemented; local DSH
currently advertises rooms as unsupported. Implement and test that capability
before exposing it. Account authorization, cancellation, delivery uncertainty and
mobile suspension must remain visible and recover safely.

The matching beta.6 site is deployed and verified at niobium.sh (Cloudflare
version c664c648-949c-4ea9-82c6-a935f5ff424f). Plane NIO-3/10/24/29/32/36 and
both exports record the verified checkpoint and the expanded next-cycle scope,
with all existing workflow states preserved.


## Wardrobe and connected workspaces — beta.7

Shipped in beta.7:

- 32 adult authored looks, automatic brief-based choice, family/search wardrobe,
  eight outfit palettes, surprise/remix/undo and twelve transparent creatures.
- Optional OpenAI image atelier on both platforms. Separate secure image key,
  reference-based designs, saved background receipts, preview/accept, portable
  indexed pixels, companion transparency validation and no automatic paid retry.
- Independent Hermes/DSH stores and transports, with native engine selection and
  separate sessions, requests and history. Hermes-only startup does not boot DSH.
- Durable local rooms and DMs, real `list_pixels` / `message_agent` tools behind
  reviewed messaging access, attributed replies and bounded discussion.
- Native room questions/approvals, stop, receipt-preserving failed sends and
  session archival when clearing rooms. Room sessions never replace a DM.
- Cleaner pixel chat actions and actual character portraits in room messages.

OpenMausBot's thread isolation and bounded discussion informed this design;
its code and artwork were not copied. Research source:
https://github.com/milind-soni/OpenMausBot at
30b62aaf18adc22099c24a7181abf5e9ddead980.

[PR #10](https://github.com/wckdboy/niobium/pull/10) merged to main at a60067f.
Source 8a988cf and the merge have the same Git tree. All three CI jobs pass;
final Codex review has no findings and all nine review threads are resolved.
Greptile cannot review at its exhausted trial quota.

[Beta.7](https://github.com/wckdboy/niobium/releases/tag/v2.0.0-beta.7) is published
with signed Android code 12, corresponding sources, metadata and checksums.
Validation: 50 iOS unit + six UI tests, 35 Android JVM + sixteen ARM64 native
checks, 61 Node, 15 Python and 38 standalone Swift checks. Full iOS build 15
simulator acceptance covers local Rooms, background streaming and cold history
in 134.594 seconds. The current full iOS archive verifies its source modules,
identity and Release exclusions; export/TestFlight upload is blocked by Xcode's
“No Accounts” error. Build 14 remains the latest accepted Apple upload and
physical iPhone result; the paired phone is still locked. No stale IPA is published.

Next acceptance cycle: restore Apple signing and upload build 15; verify the
current physical iPhone build; physical Android/16 KB and real-account provider
acceptance; explicit cross-engine conversation bridging; recovered room work
when a delivery receipt is lost; longer-running delegation and native connector
consent. Expand wardrobe collections and image-provider choices after measuring
real usage. Full Hermes parity remains tracked separately.

Uncertain native room-send IDs now survive workspace changes and cold launches.
Only scoped digests and random receipt IDs are stored; message text and tokens
are not retained in this receipt book. Android instrumented checks also verify
the actual Keystore image key in an isolated namespace.

Review follow-ups bound acknowledged/unconfirmed receipts, admit Android room
questions, and preserve a checkpointed active-time budget across suspension and
restart. Time awaiting a user answer does not spend the room execution budget.
Both native clients confirm permanent room clearing; the real Android DSH test
checks cancel preserves the room before confirming archival.

## 2.1 desktop and Android cycle

The active PR is [#11](https://github.com/wckdboy/niobium/pull/11). CodeRabbit
is connected and automatic Greptile reviews/status/approval are disabled.
Linux x64 DEB, macOS ARM64 DMG and signed Android 2.1/code13 are the release
targets. iOS release/signing work remains parked.
[2.1 beta.1 is published](https://github.com/wckdboy/niobium/releases/tag/v2.1.0-beta.1),
CodeRabbit approved the release commit and PR #11 is merged to main. All active
[release CI jobs](https://github.com/wckdboy/niobium/actions/runs/39961030217) pass.
Artifact and deployment receipts are in
[release validation](docs/evidence/beta21-release-validation.json).

Validated locally: 33 shared-core tests, 18 desktop tests, seven additional
Android JVM tests, 67 Node checks, 15 Python checks and 64 exact wardrobe fixtures.
The signed APK installs and cold-launches on the ARM64 Android 16 emulator.
All 16 instrumented checks passed after extraction; the two affected DSH/transport
checks were repeated after the shared transport fixes. macOS app-image and mounted
DMG checks start real DSH through the packaged launcher and verify current runtime
source hashes and executable permissions.

Next acceptance work is tracked in Plane:

- NIO-39: additional native desktop provider setup, with actual model activation,
  streaming and restart fixtures. The first preview offers DeepSeek setup.
- NIO-31: desktop OS credential vaults; gateway and image keys are session-only now.
- NIO-32: keyboard/accessibility, desktop layout and long-running task soaks.
- NIO-34/NIO-35: richer history, companion controls, skill/MCP/routine surfaces,
  Hermes contracts and reconnect replay.
- NIO-29/NIO-33/NIO-36: physical Android/16 KB execution, cross-engine rooms and
  real-account model/image acceptance. Controlled fixtures do not establish these.

## Native provider cycle — NIO-39

Current branch: `codex/native-local-providers`. Native desktop setup activates
DeepSeek, OpenAI, Anthropic and a compatible route through DSH's own settings and
credential APIs. Models come from its installed catalog. Defaults require explicit
selection; existing chats, other routes and custom settings remain intact.
Compatible no-auth endpoints use an isolated nonsecret credential placeholder.
The shared adapter also fixes legacy Android OpenAI/Anthropic key activation.

Local acceptance: 33 shared-core tests, 22 desktop tests, seven Android JVM tests,
79 Node checks and 15 Python checks. Real DSH streams all four provider protocols
against controlled loopback fixtures, restores the original conversation after
restart and keeps keys out of settings/responses. Android provider setup passes
on the ARM64 Android 16 emulator. The streaming regression is repeated separately.

Next gates: CodeRabbit review, native Linux/macOS package checks, signed Android
upgrade/install validation and release publication. OS credential vaults and
accessibility/soaks remain NIO-31/NIO-32. Real account acceptance remains open.
