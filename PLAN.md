# Niobium v2

The goal is a complete native mobile surface for Hermes Bot Mode, with an
optional on-device DSH runtime, a professional pixel-art interface, and Pixels
that feel like individual teammates.

Reference: [the supplied dashboard](docs/reference-dashboard.png).
Ledger: [Plane](https://app.plane.so/wckd/projects/2fbcf987-20de-43dc-80ac-0a6bab7ec03a/issues/).
Protocol audit: [HERMES-PARITY.md](docs/HERMES-PARITY.md).

## Decisions

- SwiftUI on iOS/iPadOS; Kotlin/Compose on Android. Keep platform UI native.
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
   provider turns, host bridges, physical/16 KB devices, battery and encrypted
   provider storage. Integrate the native Pixel workshop with real DSH presets.

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
