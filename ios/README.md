# Niobium for iOS

Native SwiftUI client for a Hermes Agent gateway. Swift 6, iOS 18+. A Hermes
profile is rendered as a **Pixel**.

The v2 source retains two build flavors:

| Target | What it is | Dependencies |
| --- | --- | --- |
| `Niobium.xcodeproj` | Hermes client only. Small, fast, no Linux guest. | XcodeGen + Xcode |
| `NiobiumDSH.xcodeproj` | Hermes client **plus** the full DeepSeek Harness in a bundled Alpine guest via iSH-ARM64. | dsh-ios submodule, generated rootfs, GPLv3 |

## Full build (local DeepSeek Harness)

The full build reuses [everettjf/dsh-ios](https://github.com/everettjf/dsh-ios)
and [iSH-ARM64](https://github.com/OpenMinis/ish-arm64). It compiles the emulator
into the app, boots Alpine 3.21 with Node 22 and `@deepseek-ai/dsh`, supervises
`dsh-serve`, and displays the stock DSH web UI in a WebKit surface.

```sh
cd /path/to/niobium
git submodule update --init --recursive

cd ios
scripts/build-local-dsh.sh            # builds rootfs (~6 min) + hterm
xcodebuild -project NiobiumDSH.xcodeproj -scheme Niobium \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```

`scripts/build-local-dsh.sh` needs `meson`, `ninja` and `ld.lld`
(`brew install meson ninja lld`), and writes
`ios/Resources/root.tar.gz` (81 MB, git-ignored). Re-run it when updating the
DSH submodule.

`NiobiumDSH.xcodeproj` is committed and references the submodule through the
`ish-arm64`, `app` and `tests` symlinks. It runs a Meson build phase for
`libish.a`, `libish_emu.a` and `libfakefs.a`, copies the rootfs into the app, and
compiles `Sources/Niobium` with `NIOBIUM_LOCAL_DSH=1`. Do not regenerate this
project with XcodeGen; it is derived from the DSH project and contains the iSH
build phases.

### Signing the full build

Use a team/profile that can sign a normal app. The full build deliberately uses
`Resources/Niobium.entitlements` (no HealthKit or Private Cloud Compute
entitlements), so the DSH host capabilities that need those entitlements will not
work; the harness itself, tools, terminal and sessions do.

```sh
xcodebuild -project NiobiumDSH.xcodeproj -scheme Niobium -configuration Release \
  -destination 'generic/platform=iOS' -archivePath build/Niobium-DSH.xcarchive archive \
  -allowProvisioningUpdates DEVELOPMENT_TEAM=<YOUR_TEAM_ID>
```

## Hermes-only build

```sh
brew install xcodegen
cd ios
xcodegen generate
xcodebuild -project Niobium.xcodeproj -scheme Niobium \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
open Niobium.xcodeproj
```

`Niobium.xcodeproj` is generated from `project.yml`; edit `project.yml`, not the
`.pbxproj`. The Hermes-only target does not compile DSH/iSH source or bundle the Linux
guest image. Audited debug simulator bundles measured 17 MB client and 98 MB full.

## Layout

- `Sources/Niobium/Connection.swift` — URLSession WebSocket JSON-RPC and Keychain.
- `AppStore.swift` — roster, canonical chats, events, approvals, rooms, toolbox,
  routines, demo state.
- `Models.swift` — tolerant projections of live Hermes data.
- `Speech.swift` — on-device dictation.
- `Views/` — Pixels, Chat, Rooms, Activity, Toolbox, Settings and sheets.
- `DSHLocalView.swift` — SwiftUI host for `DSHRootViewController` (full build only).
- `DSHAppDelegate.m` — defers guest boot until the user opens Settings → On device.
- `NiobiumDSH.xcodeproj` — full build project, including the iSH Meson phase.
- `ThirdParty/dsh-ios` — pinned GPLv3 submodule.
- `scripts/build-local-dsh.sh` — reproducible Alpine + Node + dsh image build.
- `Resources/root.tar.gz` — generated guest image (ignored by git).

## Transport notes

- `GET /api/status` probes HTTP/auth.
- `WS /api/ws?token=…` carries JSON-RPC 2.0 **text** frames. Binary frames are
  rejected by Hermes.
- After `gateway.ready`, advertise server requests and handle approvals, clarify
  and transient credential/vault requests. Recovered canonical prompts carry
  prompt IDs and execution generations.
- `session.create` uses the title `Bot Chat`; the UI calls the resulting
  conversation a **pixel chat**.
- `groups.log` is polled every 3 s while a room is open; `groups.send` is
  idempotent per `event_id`.
- A socket's callbacks are bound to that exact socket, so a late failure from an
  old connection cannot clear a new one.

## Local DSH behavior

- Boot is **on demand**: opening Settings → On device → DeepSeek Harness calls
  `DSHBootCoordinator.start()`.
- First boot imports the rootfs (20–60 s on a phone), then starts `dsh-serve`
  on a free loopback port; the stock DSH UI loads when ready.
- The DSH screen has its own terminal, full log, restart, capabilities and
  session data. It is a separate harness, not a Hermes profile.
- Guest data persists under Application Support; app updates with a new rootfs
  migrate `~/.dsh` and the workspace.
- The iOS full app is arm64 only; x86_64 simulators cannot run the AArch64
  emulator. Apple Silicon simulator or a physical device is required.

## Demo / screenshots

```sh
xcrun simctl launch booted ai.wckd.niobium --demo --no-splash
xcrun simctl launch booted ai.wckd.niobium --demo --no-splash --bot orion
xcrun simctl launch booted ai.wckd.niobium --demo --no-splash --room demo-launch
xcrun simctl launch booted ai.wckd.niobium --demo --no-splash --bot forge --demo-tool
xcrun simctl launch booted ai.wckd.niobium --demo --no-splash --tab activity
xcrun simctl launch booted ai.wckd.niobium --demo --no-splash --toolbox
xcrun simctl launch booted ai.wckd.niobium --demo --no-splash --dsh
```

`--no-splash` skips the splash and keeps the simulator display awake; it is for
capture/testing only. `--dsh` opens the local DeepSeek Harness immediately (full
build only).

## Limitations

- No APNs/background connection.
- Room-level approvals are not surfaced yet.
- Uploaded Hermes avatars are not fetched; the app uses its original adult cast.
- Custom 48×48 layer rendering is experimental and needs further visual polish.
- Cleartext HTTP is allowed for self-hosted LAN gateways (ATS). Prefer
  HTTPS/Tailscale.
- Local DSH needs 81 MB of guest image, grants no App Store distribution path
  because of iSH's additional terms, and the combined app is GPLv3.

Current implementation and verification limits are recorded in
[HERMES-PARITY.md](../docs/HERMES-PARITY.md).

Use `--workshop` or `--pixel-review` with `--demo` to capture Pixel creation.
