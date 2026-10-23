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

DSH starts its HTTP listener before registering the event route. The adapter
waits up to ten seconds for the initial event connection, backing off on early
connection resets/refusals or temporary 404/503 responses. It sends `gateway.ready`
only after that stream opens. Authorization errors fail immediately, and closing
the native client cancels startup. An established stream closing fails visibly;
the adapter does not reconnect or replay model requests. The native client owns
session recovery through the snapshot handoff above.

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

Scheduled jobs and cross-engine host bridges remain Hermes
capabilities and are hidden in local mode. Filesystem/network scopes in a
persona are operating instructions. PRoot and a shared iSH guest do not isolate
Pixels from one another.

## Native desktop provider setup

The next preview adds `local.providers` and `local.provider.configure` over the
existing authenticated native transport. The desktop Engines screen offers
DeepSeek, OpenAI, Anthropic and one OpenAI-compatible endpoint. Provider catalogs
come from pinned DSH; they do not imply that an account can use every model.

Writes use DSH's revision-checked `settings.mutate` and `credentials.set` seams.
Only reviewed fields are changed; unrelated routes, headers and model overrides
remain intact. Credentials are write-only to Niobium. Read-only sources are
shown as managed, and failures never echo submitted keys. A provider is marked
Configured only after its live route, catalog and credential state agree.

The default-model checkbox is explicit. It changes the `agent-default-model`
namespace for future chats and clears stale reasoning settings. Existing model
selections and history are preserved across restart. Conflicts and partial
failures require a refresh; saving never submits a model prompt.

Compatible endpoints use HTTPS or literal loopback HTTP, an explicit model ID
and reviewed context/output limits. The pinned pi-ai client requires a credential
even for an unauthenticated server, so the adapter uses a separate nonsecret
placeholder reference in that case. It never falls back to another provider's
key. A later real key uses its own reference and leaves the placeholder unchanged.
Legacy `local.credential` calls also activate the OpenAI/Anthropic routes, fixing
the Android key-saving screen without changing its transport or engine ownership.

## Evidence and remaining checks

The ARM64 Android app-domain test creates a real DSH preset through the native
view model, sends a prompt through DSH to a controlled loopback model server,
receives its streamed response in native chat state and restores history.
Separate tests prove that local startup retains the native shell and navigation,
and that the web interface opens only from advanced controls.

Both iOS targets compile, and the full iOS simulator boots DSH into the native
dashboard and Scout roster. Native state and UI tests cover DSH creation routing,
engine identity and the coherent Engines screen. A physical iPhone 18 Pro Max
(iOS 27.0.1), upgraded from build 6 to 7, passed the opt-in DSH startup, native
Engines, Home-screen return and chat-UI smoke test. The sanitized record is in
[evidence/ios-build7-device-smoke.json](evidence/ios-build7-device-smoke.json).
Physical Android and broader iOS execution, real-account provider acceptance,
actual delegation, model/image edge cases,
16 KB devices and OEM battery/process-death behavior still need acceptance.

DSH APIs are grounded in the pinned image and
[dsh-ios source](https://github.com/everettjf/dsh-ios/tree/bac298478545db7bb8762e82ba3db52f50ac6a5d).
Android's restriction is recorded in the
[AOSP app policy](https://android.googlesource.com/platform/system/sepolicy/+/f78629f7b4dc0e45f964b2cdb3b6f652955987c4/private/app_neverallows.te).


## Beta.7 local conversations

Both engines now retain their own native stores and transports. Selecting a
workspace changes presentation; it does not disconnect its peer. Hermes-only
startup leaves Linux stopped unless local mode was explicitly enabled.

`niobium-messaging.cjs` provides local `groups.*` rooms with separate provider
sessions and atomic outbox storage. Human sends have durable idempotency keys;
accepted work recovers from its existing history, while uncertain submissions
are never replayed. Replies carry the actual profile identity. Eight deliveries
per human message and two per recipient bound discussion across rooms and DMs.

Reviewed `messaging` access adds the trusted `list_pixels` and `message_agent`
tools to a Niobium preset. Existing presets are not silently granted new tools;
they can still participate in rooms and use routed @mentions. The directory
returns actual local contacts and memberships. Cross-engine rooms are not
bridged. Native approvals and questions retain their owning session IDs.
Stopping a room cancels its pending replies. Clearing it archives its owned DSH
sessions before removing ownership, so they cannot become accidental DMs.

The Android integration fixture invokes the real registered tool and receives
Scout's actual response through DSH. Full iOS build 15 also passed the isolated
background/cold-launch model check. Real-account provider/delegation acceptance
and physical Android/16 KB execution remain open. iOS may suspend local work.

Receipt storage keeps the latest 64 client-confirmed sends and at most 128
unconfirmed sends per room. Synchronizing the room confirms accepted IDs; it
does not claim a model reply completed. Unconfirmed IDs are never pruned. A full
unconfirmed window refuses new sends until synchronization. Confirmed IDs must
not be reused after the retained window. Native clients persist uncertain IDs
across rooms/restarts, using only scoped digests and random IDs.
