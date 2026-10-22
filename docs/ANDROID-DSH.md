# Embedded DSH on Android

Decision under validation: package an ARM64 PRoot runtime and a pinned
Ubuntu/glibc + Node 24 + DSH environment inside Niobium. Use the existing
native Compose UI and an authenticated loopback WebView. Android currently
ships remote DSH; this decision does not claim an embedded runtime has passed
Niobium device tests.

The strongest reference found is [DSHA](https://github.com/DSH-APP/DSHA), inspected
at commit [edcbe634874c57c3815170bf0fb44f11ed91af46](https://github.com/DSH-APP/DSHA/tree/edcbe634874c57c3815170bf0fb44f11ed91af46).
Its current source targets SDK 37, runs DSH in app-private Ubuntu, and has
APK-packaged bxroot and PRoot engines. Its README distinguishes build173 source
from build170 release evidence. That distinction must be preserved.

Android [blocks direct execution from writable app storage for target API 29+](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission).
The runtime executables and loaders therefore belong in the APK's executable
native-library directory. This is also documented by
[OpenMinis' Android build](https://github.com/OpenMinis/OpenMinis/blob/main/BUILDING.md).
An ordinary extracted Linux rootfs or a Termux installation script alone does
not solve packaging and lifecycle requirements.

## Integration boundary

- Keep PRoot as the first compatibility baseline. Evaluate bxroot against the
  same device suite before choosing it as the default. Do not revive DSHA's
  retired proroot implementation.
- Pin source commits, rootfs, Node, DSH, native modules and every binary hash.
  Build native executables/loaders for 16 KB page compatibility.
- Use an app-private runtime root and persisted home/workspace. Verify archives,
  reject traversal and escaping links, extract transactionally, and preserve
  user data through upgrades. Never repair by silently wiping the workspace.
- Run the supervisor in a separate app process with a user-started foreground
  service, visible status and a Stop action. Track process identity/generation;
  do not kill by port, process name or a reused PID.
- Allocate an available loopback port. Require a per-run authentication secret
  and the current runtime generation before attaching the WebView or host bridge.
- Keep provider keys in Android Keystore-backed storage. Pass secrets through
  the process environment or a protected channel; never command arguments,
  logs, exported files or reusable WebView URLs.
- Adapt only the runtime and supervised execution boundary. Retain Niobium's
  own permissions, file picker, notifications and design system.
- Audit MIT wrapper and transitive GPL/native-runtime redistribution obligations
  before bundling; include corresponding licenses and source references.

## Proof required before a local-runtime release

1. An APK targeting SDK 37 starts a real shell from a cold install, then Node,
   DSH and a provider-backed turn. A launcher process alone is insufficient.
2. Native modules, shell tools, PTYs, uploads and delegated work complete.
3. Stop drains the owned process tree. Restart does not replay a write whose
   result is unknown. App rotation/recreation and WebView reattachment preserve
   the current run.
4. Process death, device reboot, low storage, offline operation, missing assets
   and corrupted extraction produce recoverable states.
5. Signed upgrade preserves workspaces, sessions, attachments and key storage;
   backup/export never includes unapproved credentials.
6. Verify Android 11 through current Android, a physical ARM64 phone and a
   16 KB page-size device/emulator. Record battery, memory and cold-start costs.

DSHA's upstream tests and Android 13 evidence support choosing this route for a
spike. They do not establish stability inside Niobium. The dedicated Plane
Android-local ticket owns these acceptance gates.
