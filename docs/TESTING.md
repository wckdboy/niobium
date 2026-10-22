# Test suite

Run shared checks from the repository root:

```sh
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
the opaque white icon, art provenance, shared skill/recipe copies and CSV fields.

Android, with Node available on PATH and JDK 17:

```sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:assembleRelease
```

Twenty-four Android JVM tests include real OkHttp HTTP/WebSocket exchanges with a local
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

Twenty-four unit tests cover URL/auth handling, JSON-RPC IDs, cancellation,
PixelSpec validation, reversed chat-selection responses, resumed streaming,
recovered clarification dismissal, provisioning access and failed appearance saves.
Three UI tests launch only the labelled demo, verify the dashboard and
review flow plus coherent native Engines navigation, and retain screenshots in the xcresult. Native tests live in
NativeTests; the lowercase tests symlink belongs to the DSH submodule.

The full DSH target has a separate device archive gate; do not regenerate it
with XcodeGen. TestFlight uses ai.wckd.niobium on team XKA8CGC2AB. Export/upload
options are in ios/Release.

For a connected, unlocked iPhone with Developer Mode enabled, run the opt-in
native DSH smoke test against the development-signed full app from an archive:

```sh
python3 tools/check-ios-device.py --device YOUR_IPHONE_UDID \
  --app dist/Niobium-2.0-7-publish.xcarchive/Products/Applications/Niobium.app
```

The helper builds the UI test runner and points its `UITargetAppPath` at the
full app using Apple's editable xctestrun format. It updates the existing app;
it does not erase its container. The test selects local DSH, waits for the real
Scout roster, checks native Engines/provider settings, returns from the Home
screen and opens native chat. It sends no model request and changes no API key.
Screenshots are retained in the local xcresult; inspect them for private data
before sharing. The default client-only CI run skips this opt-in test.

GitHub Actions runs shared, Android and iOS suites for pull requests and pushes.
Live provider/connector authorization, physical-device background behaviour and
physical-device Android DSH remain explicit acceptance work in PLAN.md.

Android runtime tests require an ARM64 Android device/emulator:

    ./gradlew :app:connectedDebugAndroidTest
    python3 ../tools/check-android-runtime.py app/build/outputs/apk/debug/app-debug.apk

Five app-domain tests verify Linux/Node/DSH, private authenticated HTTP/WebSocket access,
real session creation and bot-build discovery, service Start/Stop, CSS viewport layout,
workspace preservation through image replacement, and a full native preset/chat/history
round trip through real DSH and a controlled model server. Eighteen shared adapter
tests cover tool grants, adult/slug validation, model identity, pending approvals
and questions, stream/transcript folding and explicit unsupported capabilities.
See DSH-PROVIDER.md and ANDROID-DSH.md for limits.
