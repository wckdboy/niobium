# Hermes gateway ↔ Niobium

The v2 audit uses the contracts in NousResearch/hermes-agent at
[1186b6bd5330185ba61c4f80fbfc073f7febbf0e](https://github.com/NousResearch/hermes-agent/tree/1186b6bd5330185ba61c4f80fbfc073f7febbf0e/tui_gateway/contracts).
This is implementation coverage, not a claim of complete live parity.

## Implemented paths

- Discovery: client.capabilities, runtime.describe, gateway.capabilities.
- Profiles: profiles.list/create/describe/configure; reviewed PixelSpec
  provisioning; installed-skill filtering; pinned toolsets; manual host
  approvals and reasoning preferences through config.set.
- Identity: versioned PixelSpec and appearance in ui_meta.niobium; appearance
  updates use ui_meta_expected_revisions and preserve other metadata.
- Direct chat: canonical Bot Chat discovery, session.create/resume/list,
  transcripts, prompt.submit and session.interrupt.
- Model and reasoning changes: session.mutate model/reasoning with a refreshed expected_revision and
  expected_generation. Stop uses the current execution_generation.
- Chat events: message, reasoning, tool progress/completion, usage, notices and
  request cancellation. Non-focused session events are excluded from the chat.
- Approvals: live JSON-RPC replies; canonical recovered approvals use prompt_id
  and execution_generation through approval.respond. The old fabricated
  recovered RPC id is removed.
- Credential prompts: transient secure fields for secret, sudo, vault unlock,
  login saving and verification codes. Values are cleared on resolution or
  disconnect, not persisted or appended to the transcript.
- Tools, skills and MCP: tools.list, tools.configure, skills.manage list,
  skills.reload, MCP inventory and status.
- Stored sessions, model inventory and usage cards.
- Rooms: groups.capabilities/list/create/log/send, local roster and @mentions.
  Background-room copy follows the advertised durable-driver capability.
- Companions: subagent.list/tail/steer/interrupt, including failed delegations.
  UI updates are scoped to the active profile/session.
- Routines: cron.manage list/add/remove.
- Photos and local dictation on both platforms.
- DSH: embedded Alpine/iSH on iOS full and PRoot/Alpine on ARM64 Android; remote harness on both. Remote addresses
  are stored in Keychain/Keystore, with visible retry/error controls and WebView
  teardown.

## Contract and lifetime fixes

Canonical session methods reject extra keys. The client uses advertised source
values, does not send legacy inline_images on resume, and sends explicit
submission identifiers with prompt.submit.

Model mutations and interruption require generation fences. A control snapshot
is refreshed before either operation. Resume restores the canonical running
flag and pending approval prompts.

A canceled iOS request releases its waiter. A ready timeout belongs to the
socket that created it and cannot close a replacement connection. Old transport
callbacks cannot overwrite the current connection. Android connection callbacks
carry an epoch for the same reason.

The Pixel builder no longer discards generated permission metadata or calls
nonexistent skill-enable actions. It clones installed skill files, disables
unselected skills, checks each profile-configuration result, and reports
partial provisioning.

The Hermes-only iOS target no longer bundles root.tar.gz. The audited simulator
bundles measured 17 MB (client) and 98 MB (full DSH); these are debug simulator
bundles, not download-size claims.

## Still partial or missing

- Sessions: history paging/export, rename/archive/delete/branch/rewind, durable
  replay cursors and epoch recovery.
- Admissions: unknown-delivery/outbox recovery, receipts/cancel, queued prompt
  editing, busy steering and redirection.
- Models: complete provider/account management and live reasoning acceptance.
- Attachments: file and PDF paths; upload bounds/resizing need a deeper pass.
- Skills: command catalog/autocomplete, detail/edit/install and complete errors.
- MCP: test, OAuth, enable/disable and catalog setup.
- Memory: learning.frames/detail/edit/delete.
- Projects: projects.* and workspace session mutations.
- Rooms: state/rename/stop/approve/retry/attachments; qualified multi-connection
  identities and uncertain-delivery recovery.
- Approvals/clarify: pending/received acknowledgements, clarify locking and replay races,
  request.answer coverage and reconnect races need further live verification.
- Connectors: catalog/accounts/connect/policy and connection operation cards.
- Browser/voice: complete host browser control, voice recording/TTS and device
  bridges.
- Native background delivery/push.
- Android DSH physical/16 KB devices, provider turns, host bridges and native preset editor.

## Verification

- 38 pure Swift PixelSpec checks: valid templates, adult/layer validation,
  typed decoding, permissions, unavailable tools and duplicate capabilities.
- 23 Android JVM tests cover schemas, gateway WebSockets, provisioning and safe archive extraction.
- Four Android app-domain runtime tests cover shell/Node/DSH, HTTP/WebSocket authentication,
  session/skill APIs, WebView layout, foreground Start/Stop and workspace preservation.
- 20 iOS unit tests and two UI tests cover transport, prompt races, room identity and native flows.
- Android debug/release builds and iOS Hermes-only/full-DSH simulator builds.
- Native Home and Pixel-review captures inspected on both platforms.
- Website build, seven generated HTML pages, 81 internal links/assets and skill
  copy consistency; Cloudflare deployment verified.
- Full live gateway/provider execution, real account authorization and physical
  device background behaviour have not been established by these checks.

The remaining acceptance criteria are in the
[Niobium Plane project](https://app.plane.so/wckd/projects/2fbcf987-20de-43dc-80ac-0a6bab7ec03a/issues/).
