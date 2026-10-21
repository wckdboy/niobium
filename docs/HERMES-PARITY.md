# Hermes gateway ↔ Niobium parity audit

Audited against `NousResearch/hermes-agent` `main`, contracts in
`tui_gateway/contracts` (277 client→server methods, 79 events, 18 server→client
requests). This is the working matrix for Niobium's mobile surface, not a claim
that every internal/desktop method exists in the app.

## The wire split (important)

`/api/ws` attaches the session **authority** and answers canonical
`hermes-gateway-v1` verbs. Legacy sidecars answer only names the authority does
not know, and never `session.*`, `prompt.*` or `message.*`. Params are
`extra="forbid"`.

This pass fixed two hard incompatibilities:

- `session.resume` no longer sends `inline_images`; the canonical resume params
  are `session_id`, `profile`, `source`, `latest`, `workspace`, `defer_history`,
  `omit_messages`, `cols`, `display_projection`.
- `session.create` now sends `source: "gui"` (one of
  `cli|tui|gui|acp|tool|oneshot`), not the legacy `"desktop"`.

Both clients now call `runtime.describe` and read
`session_create.sources`, then choose `gui` when supported (falling back to
`tui`). They also call `gateway.capabilities` for build-level discovery.

Without this, an authority-backed gateway returned `4001 invalid_params` and
Pixel chat attach failed; only legacy sidecars worked.

## Implemented in Niobium

| Domain | Methods / behavior | iOS | Android |
|---|---|---|---|
| Discovery | `client.capabilities`, `gateway.capabilities`, `runtime.describe` | ✅ | ✅ |
| Profiles / Pixels | `profiles.list`, `profiles.create`, `profiles.configure` | ✅ | ✅ |
| Chat | `session.create/resume/list/history`, `prompt.submit`, `session.interrupt` | ✅ | ✅ |
| Events | `message.*`, `reasoning.*`, `tool.*`, `approval.*`, `clarify`, `session.usage` | ✅ | ✅ |
| Approvals | inline approval + multi-question clarify | ✅ | ✅ |
| Tools | `toolsets.list`, `tools.list`, `tools.configure` | ✅ | ✅ |
| Rooms | `groups.capabilities/list/create/log/send` | ✅ | ✅ |
| Routines | `cron.manage` list/add/remove | ✅ | ✅ (new) |
| Photos | `image.attach_bytes` | ✅ | ✅ (new) |
| Dictation | on-device speech → `prompt.submit` | ✅ | ✅ (new, system recognizer) |
| Remote DSH | `Settings → Harness`, arbitrary DSH web UI over http(s) | ✅ | ✅ (new) |
| Local DSH | embedded iSH-ARM64 + Alpine + `dsh-serve` | ✅ (full build) | — (platform limit) |
| Light/dark | System/Light/Dark palette | ✅ (new) | ✅ (new) |
| Mascots | chosen/random/custom 8×8 per profile | ✅ (new) | ✅ (new) |
| Ore icon | 16×16 purple-on-white app icon | ✅ (new) | ✅ (new) |

## Remaining gaps (honest)

| Domain | Missing methods / UI |
|---|---|
| Sessions | stored-session browser, `session.history` browsing, rename/archive/delete via `session.mutate`, `session.export`, canonical replay `session.events.since` |
| Busy controls | `session.steer/redirect`, `prompt.receipt/cancel`, queued prompt steer/replace |
| Models | `model.options` picker; model/reasoning switches via `session.mutate` |
| Usage | `session.stats`, context breakdown, token ring |
| Attachments | `pdf.attach`, `file.attach`, `image.attach` from a path |
| Skills | `skills.manage`, `skills.reload`, `commands.catalog`, slash autocomplete |
| MCP | `mcp.servers.list/status/test/oauth` |
| Memory | `learning.frames/detail/edit/delete` (journey/memory UI) |
| Projects | `projects.*`, workspace switch via `session.mutate` |
| Rooms | `groups.state/rename/stop/approve/retry/attachments` |
| Approvals recovery | `approval.pending/received`, `clarify.lock`, `request.answer` after reconnect |
| Subagents | `subagent.list/tail/steer/interrupt` |
| Vault/security | `vault.*`; secret/vault prompts are currently declined |
| Connectors | `connectors.list/catalog/connect/accounts/policy`, `connection.respond` |
| Browser | `browser.manage` |
| Voice backend | `voice.toggle/record/tts`; current dictation is local STT only |
| Android local DSH | iSH-ARM64 is iOS-only; Android uses the remote harness path |

## Cross-platform notes

- Android now has photo attachment (`image.attach_bytes` with `mime`) and system
  dictation, matching the iOS composer.
- Routines are no longer iOS-only.
- Remote DSH is the cross-platform harness path. Local Alpine/iSH remains an iOS
  full-build feature (GPLv3); Android connects to a DSH web UI on a computer/VPS.
- `groups.*` verbs are permission-gated by ticket scope; a `permission_denied` is
  surfaced rather than hidden.

## Verification

- Builds: iOS standard and full, Android debug + release.
- Local DSH on simulator: Alpine image import, kernel boot, `dsh-serve` on
  127.0.0.1:3080, stock DSH web UI loaded.
- Demo flows: roster, chat, tools, approvals, rooms, routines, light mode,
  mascot studio, remote harness URL.
