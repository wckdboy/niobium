# Niobium for iOS

Native SwiftUI client for a Hermes Agent gateway. Swift 6, iOS 18+, no third-party
dependencies. A Hermes profile is rendered as a **Pixel**.

## Build and run

```sh
brew install xcodegen
cd ios
xcodegen generate
xcodebuild -project Niobium.xcodeproj -scheme Niobium \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' build
open Niobium.xcodeproj
```

The checked-in Xcode project is generated. Edit `project.yml`, not the `.pbxproj`.

### Signing

Automatic signing; this build used team `XKA8CGC2AB`. For your own device:

```sh
xcodebuild -project Niobium.xcodeproj -scheme Niobium -configuration Release \
  -destination 'generic/platform=iOS' -archivePath build/Niobium.xcarchive archive \
  -allowProvisioningUpdates DEVELOPMENT_TEAM=<YOUR_TEAM_ID>
```

Change `PRODUCT_BUNDLE_IDENTIFIER` if `io.sovware.niobium` is taken in your team.

## Layout

- `Sources/Niobium/Connection.swift` — URLSession WebSocket JSON-RPC and Keychain.
- `AppStore.swift` — roster, canonical chats, events, approvals, rooms, toolbox,
  routines, demo state.
- `Models.swift` — tolerant projections of live Hermes data.
- `Speech.swift` — on-device dictation.
- `Views/` — Pixels, Chat, Rooms, Activity, Toolbox, Settings and sheets.
- `Resources/PrivacyInfo.xcprivacy` — no tracking or collected data.

## Transport notes

- `GET /api/status` probes HTTP/auth.
- `WS /api/ws?token=…` carries JSON-RPC 2.0 **text** frames. Binary frames are
  rejected by Hermes.
- After `gateway.ready`, send `client.capabilities {server_requests:true}` and
  answer `approval` / `clarify` requests.
- `session.create` uses the title `Bot Chat`; the UI calls the resulting
  conversation a **pixel chat**.
- `groups.log` is polled every 3 s while a room is open; `groups.send` is
  idempotent per `event_id`.
- A socket's callbacks are bound to that exact socket, so a late failure from an
  old connection cannot clear a new one.

## Demo / screenshots

```sh
xcrun simctl launch booted io.sovware.niobium --demo --no-splash
xcrun simctl launch booted io.sovware.niobium --demo --no-splash --bot athena
xcrun simctl launch booted io.sovware.niobium --demo --no-splash --room demo-launch
xcrun simctl launch booted io.sovware.niobium --demo --no-splash --bot forge --demo-tool
xcrun simctl launch booted io.sovware.niobium --demo --no-splash --tab activity
xcrun simctl launch booted io.sovware.niobium --demo --no-splash --toolbox
```

`--no-splash` skips the splash and keeps the simulator display awake; it is for
capture/testing only.

## Limitations

- No APNs/background connection.
- Room-level approvals are not surfaced yet.
- Uploaded Hermes avatars are not fetched; faces are generated pixel tiles.
- Cleartext HTTP is allowed for self-hosted LAN gateways (ATS). Prefer
  HTTPS/Tailscale.
