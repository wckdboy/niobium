# Native screenshots

Beta.4 dashboard captures verify the stronger header scrim and shared pixel
engine icons in both themes. These use the labelled demo roster:

- iOS 2.0 (8) simulator: [light](ios-beta4-home-light.png), [dark](ios-beta4-home-dark.png).
- Android 2.0 (7) signed release: [light](android-beta4-home-light.png), [dark](android-beta4-home-dark.png).

The full iOS simulator model test separately proves real DSH streaming, an
eight-second background round trip and history after a cold launch. See
[the evidence](../docs/evidence/ios-build8-simulator-model.json). Build 8 iPhone
acceptance remains pending; the physical build 7 evidence below is narrower.

The beta.3 captures show the actual local DSH engine connected to the standard
native app. They use the full iOS 2.0 (7) simulator build and signed Android
2.0 (6) release on the ARM64 Android 16/API 36 emulator.

- iOS: [native DSH Home](ios-dsh-home.png), with the real Scout roster.
- Android: [native DSH Home](android-dsh-home.png) and [Engines](android-engines.png).

These prove native layout and the local connection. Provider credentials were
not entered for these captures. The Android runtime test separately proves a
native preset/chat/history round trip through a controlled loopback model server.
Real-account turns and physical-device acceptance remain.

Earlier deterministic demo captures are retained for the authored roster and
Pixel review:

- iOS: [demo Home](ios-home-audit.png) and [Pixel review](ios-pixel-review.png).
- Android: [demo Home](android-home-audit.png) and [Pixel review](android-pixel-review.png).

Demo images do not prove live Hermes/provider execution. Other PNGs, including
`showcase.png` and `android-local-dsh.png`, are historical captures.
