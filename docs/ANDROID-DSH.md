# Embedded DSH on Android

Niobium packages an ARM64 PRoot engine and the pinned Alpine/Node/DSH image
already used by the full iOS build. Android uses native Linux syscall
translation through PRoot; iOS uses iSH-ARM64. Neither mode requires a remote
Hermes connection. Model requests still need a provider key or a reachable
model endpoint.

## Implementation

- The welcome screen offers **Run Pixels locally** before gateway setup. The
  Local tab has native pixel icons, stepped buttons, install progress and
  explicit Start/Stop controls. Remote DSH remains available.
- PRoot, its loader and Niobium's supervisor are APK native-library executables.
  Android blocks execution from writable app storage for apps targeting API
  29+. Native ELF load segments are checked for 16 KB alignment at build time.
- The image and native packages are SHA-256 pinned in
  config/android-runtime.json. Preparation downloads verified data, never runs
  vendor installation scripts, and compiles the small supervisor with NDK 30.
- Installation verifies the image before streaming bounded TAR extraction.
  Traversal, replaced links and symlink parents are rejected. An atomic image
  swap keeps home/workspace outside the replaceable root. Cleanup never follows
  guest links into host directories.
- A user-started foreground service owns the run. The native subreaper owns
  guest descendants and reaps them on Stop or parent death. Android kernels
  without the proc children file use direct-child discovery through proc stat.
  No process is killed by port or process name. The service never auto-restarts
  a task after process death.
- DSH binds a Unix socket in app-private storage. Only the native proxy listens
  on an OS-allocated loopback TCP port. Unauthenticated headers have a total deadline and a connection cap. Every HTTP/WebSocket connection needs a
  fresh 256-bit per-run cookie; Host and Origin are checked. The HttpOnly cookie
  stays out of URLs and command arguments. The WebView blocks external
  navigation/resources, and CSP restricts WebSocket/fetch destinations.
- Provider settings entered in DSH live in private guest home. This is currently
  DSH storage, not a native Keystore provider vault. Hermes tokens continue to
  use Android Keystore. The application never logs runtime output or keys.
- The shared bot-build skill is installed in the workspace. DSH exposes its
  own sessions, tools, agent presets and settings. The native Hermes workshop
  is not yet a DSH preset editor; a generated PixelSpec is a design for review.

PRoot is a compatibility layer, not a security boundary between Pixels. The
guest shares the Android app UID and its Linux home. DSH's guest permission
preset asks for tool approval, but per-Pixel host filesystem/network isolation
requires additional work. The iOS-only host bridge is not advertised on Android.

## Verified in Niobium

Four instrumentation tests ran inside the APK's own app UID on the ARM64
Android 16/API 36 emulator with 4 KB pages:

1. Cold image preparation, guest shell, Node and DSH CLI execution.
2. Real DSH HTTP readiness, rejected anonymous/foreign-origin access, successful
   authenticated HTTP and WebSocket upgrade, and no stock unauthenticated TCP
   listener. Real session creation and bot-build skill discovery are tested.
3. Foreground-service start, accessible workspace entry, a nonzero CSS viewport
   in the real WebView, Stop and closed listener.
4. Image replacement preserving a sentinel and user-edited bot-build instructions.
   Immediate Stop/Start and slow unauthenticated connections have regression coverage.

JVM tests cover traversal, symlink parents, replacement links, size/entry limits
and cancellation. The source/recipe bundle and NOTICE accompany release assets.

## Remaining acceptance work

- Provider-backed turns, native addons, PTYs, uploads and actual delegated work.
- Native DSH Pixel creation and permission mapping; encrypted provider vault.
- Physical ARM64 devices, Android 9 through current, physical 16 KB page-size
  operation, OEM background restrictions, process death and battery measurements.
- Signed upgrades across real installed versions and low-storage/corrupt-image
  recovery. The emulator image-swap test does not replace these checks.

The Android runtime is a beta with recorded test coverage, not a claim of
universal device stability.

## Sources and redistribution

Runtime executables come from official Termux packages, not an installed Termux
app or unattributed third-party APK binaries. Exact hashes, upstream archives
and the Termux packaging commit are recorded in the lock file. Run:

    python3 tools/prepare-android-runtime.py --sources

This bundles upstream source archives, build recipes/patches, the supervisor and
checksums. The UI/supervisor are MIT; bundled GPL/LGPL/BSD software keeps its own
licenses. See NOTICE.

Primary references:
- https://github.com/termux/proot
- https://github.com/termux/termux-packages
- https://github.com/everettjf/dsh-ios
- https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission
- https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
- https://issuetracker.google.com/issues/190411390
