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

Fourteen Android tests include real OkHttp HTTP/WebSocket exchanges with a local
Node fixture: reverse-proxy paths, token encoding/header authentication,
out-of-order replies, server errors, timeout/cancellation recovery, typed
server-request IDs, approval replies and disconnecting an in-flight request.
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

Ten unit tests cover URL/auth handling, JSON-RPC IDs, cancellation and PixelSpec
validation. Two UI tests launch only the labelled demo, verify the dashboard and
review flow, and retain screenshots in the xcresult. Native tests live in
NativeTests; the lowercase tests symlink belongs to the DSH submodule.

The full DSH target has a separate device archive gate; do not regenerate it
with XcodeGen. TestFlight uses ai.wckd.niobium on team XKA8CGC2AB. Export/upload
options are in ios/Release.

GitHub Actions runs shared, Android and iOS suites for pull requests and pushes.
Live provider/connector authorization, physical-device background behaviour and
Android embedded DSH remain explicit acceptance work in PLAN.md.
