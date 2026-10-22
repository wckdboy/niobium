# Native DSH provider

Choosing **On device** opens the normal Niobium Home, Pixels, workshop and chat.
The **Engines** tab selects DSH or Hermes. The selected engine owns the visible
roster and session history; switching does not copy conversations between engines.
The saved Hermes endpoint/token is retained when local DSH is selected, and the
local runtime can continue while Hermes is selected.

## Shared adapter

[runtime/niobium-agent.cjs](../runtime/niobium-agent.cjs) adapts the pinned
DSH 0.1.0-rc.7 APIs for the native clients' existing RPC transport. It runs only
inside the DSH web process, under the /niobium prefix. A fresh per-run native
token protects the adapter. Android additionally requires its private proxy
cookie; DSH itself binds a private Unix socket. iOS uses guest loopback.

The adapter maps real agent presets to Pixels, creates/resumes DSH sessions,
streams DSH events into native chat, restores transcripts and pending questions,
pages older history,
submits images, cancels turns, switches models, and lists/inspects/controls
continuable companion tasks. A Pixel's appearance identity includes its engine,
so an identically named Hermes profile keeps its own character.

Snapshot-to-stream handoff buffers events until native chat acknowledges its
session, then replays only events newer than the snapshot. Completed model
steps stay separate from the unfinished reply on reconnect. Failed photo
submissions clear their queued attachment.

The Pixel workshop uses the same bot-build skill and validated PixelSpec.
AI drafting runs a tool-free minimal DSH session; a missing provider or invalid
output yields an explicitly labelled template. Creation writes a DSH preset from
trusted plugin rows, adds the reviewed persona, and stores appearance metadata.
Model-generated plugin code and YAML are never loaded. Tool access is selected
when the preset is created. DSH skills share a workspace registry; the app does
not claim to pin individual local skills or configure connectors.

## Runtime and credentials

Android uses a foreground service and PRoot. Hard-link emulation is required
because DSH publishes session logs through link(); Android app policy forbids
native hard links. The emulation's backing files stay beside the persisted
session files. Stopping the engine drains its owned process tree; simply
changing a tab or engine leaves it running.

The iOS full target uses its existing iSH boot coordinator and harness supervisor.
A final build phase installs the shared adapter in the versioned rootfs overlay,
without rebuilding or replacing the pinned image. The app boots DSH on selection,
not by presenting its web controller. iOS may suspend the guest when Niobium is
backgrounded; it is not an always-on server.

DeepSeek, OpenAI and Anthropic API keys can be saved from the native Engines
screen through DSH's credential API. Credentials stay in DSH's app-private home;
they are not yet held in a separate native provider vault. Models are selected
per chat from DSH's actual model catalog. Other provider configuration, terminal,
logs and the stock web workspace are under **Advanced controls**.

Hermes group rooms, direct agent mentions and scheduled jobs remain Hermes
capabilities and are hidden in local mode. Filesystem/network scopes in a
persona are operating instructions. PRoot and a shared iSH guest do not isolate
Pixels from one another.

## Evidence and remaining checks

The ARM64 Android app-domain test creates a real DSH preset through the native
view model, sends a prompt through DSH to a controlled loopback model server,
receives its streamed response in native chat state and restores history.
Separate tests prove that local startup retains the native shell and navigation,
and that the web interface opens only from advanced controls.

Both iOS targets compile, and the full iOS simulator boots DSH into the native
dashboard and Scout roster. Native state and UI tests cover DSH creation routing,
engine identity and the coherent Engines screen. Physical iOS/Android execution,
real-account provider acceptance, actual delegation, model/image edge cases,
16 KB devices and OEM battery/process-death behavior still need acceptance.

DSH APIs are grounded in the pinned image and
[dsh-ios source](https://github.com/everettjf/dsh-ios/tree/bac298478545db7bb8762e82ba3db52f50ac6a5d).
Android's restriction is recorded in the
[AOSP app policy](https://android.googlesource.com/platform/system/sepolicy/+/f78629f7b4dc0e45f964b2cdb3b6f652955987c4/private/app_neverallows.te).
