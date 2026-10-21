# Niobium v2 — build plan

**By WCKD.ai. Not affiliated with xAI/Grok, Mausbot, Nous Research/Hermes, or DeepSeek.**

Niobium is a native iOS/iPadOS and Android client for a self-hosted Hermes
gateway, with an optional on-device or remote DeepSeek Harness. v2 is a new
product surface, not a skin: a home dashboard, 32×32 character cast, companions,
and an AI builder that creates a complete Pixel from one sentence.

## North star

> Your pixels, everywhere. A fast, quiet command deck for the agents that do the
> work — with a cast you recognise at a glance.

## Principles

1. **One glance, one action.** The home screen answers: is the instance alive,
   what is working, and what needs me?
2. **Characters, not initials.** Every Pixel is an adult 32×32 pixel character
   with a role, a companion, and a visual identity that survives light/dark.
3. **Build a teammate in one sentence.** The `bot-build.md` skill turns intent
   into personality, permissions, skills, connectors, character and companion.
4. **Speed is a feature.** No blocking launch work; local DSH boots on demand;
   every list is virtualised; no third-party SDKs.
5. **Honest limits.** The gateway matrix is public; unsupported verbs are not
   faked.
6. **Mature, not explicit.** Adult characters may be stylish, cyber/fantasy and
   revealing; no nudity, sexual acts, or minors. Companions are cute monsters.

## Experience

Five tabs: **Home · Pixels · Local · Activity · Settings**.

- **Home**: pixel landscape hero, instance card (host/VPS, latency, tools,
  access, local count), two-column character roster with companions, Local DSH
  card with runtime resources and local agents.
- **Pixels**: full roster, search, status, create/import, per-Pixel detail.
- **Local**: local iSH DSH (iOS full), remote DSH URL (both), runtime status,
  local agents, terminal/log affordances.
- **Activity**: live turn/tool/approval stream, memory journey.
- **Settings**: appearance, gateway, model defaults, permissions, integrations,
  bot-build skill, diagnostics, legal/disclaimers.

Chat stays a focused full-screen surface with the character/companion in the
header, approval cards, tools, rooms and routines.

## Art system

- 32×32 indexed pixel sprites, nearest-neighbour, transparent background.
- 16×16 companions, 2–3 colours + outline.
- Palettes: deep plum/ink, lavender ion, mint companion light, skin/hair range.
- Cast: Orion (research mage), Nova (cyber creative), Forge (automation),
  Sage (knowledge/druid), Nyx (security/rogue), Astra (vision/pilot), plus
  additional male/female archetypes for variety.
- Character selection: curated, random, AI-designed, or palette/companion edit.
- Companion: one per Pixel, separate sprite, used in cards and chat.
- App icon: a lowercase ore-cut **n** with a companion pixel spark, purple on
  white; WCKD's one-letter/one-colour brand system.

## AI builder — `bot-build.md`

A portable agent skill (agentskills.io-compatible) that produces a strict JSON
`PixelSpec`:

```json
{
  "name": "nova",
  "title": "Nova",
  "role": "Creative director",
  "personality": ["..."],
  "soul": "...",
  "permissions": {"approval_mode": "ask", "network": false},
  "toolsets": ["web", "image_gen"],
  "skills": ["..."],
  "connectors": ["..."],
  "character": {"archetype": "cyber", "gender": "woman", "palette": "ion"},
  "companion": {"kind": "cat", "name": "Miso"}
}
```

The app sends the user's sentence + `bot-build.md` to `llm.oneshot`, validates the
JSON, creates the Hermes profile (`profiles.create`), applies the soul and tools
(`profiles.configure`), saves the character/companion locally, and opens the new
Pixel. If the model is unavailable, a deterministic template is used and marked
as such.

## Hermes parity

The canonical/legacy wire split is fixed in v1.1. v2 adds, in order:
sessions/history + mutations, model/reasoning picker, usage/context, skills +
commands, MCP status, approvals recovery, subagents, projects/workspace, PDF/file
attach, connectors. `docs/HERMES-PARITY.md` is the source of truth.

## Platforms

- iOS 18+, SwiftUI, Swift 6, no third-party dependencies.
- Android 9+ (API 28), Kotlin + Compose Material 3.
- Shared product spec, copy, palette, sprite ids and bot-build schema; platform
  UI is native.
- iOS full build embeds iSH/Alpine DSH and is GPLv3; Android uses remote DSH.

## Site

A WCKD lab site in the same static system as invincible.sh / sovware.sh /
nightfall.sh: one stylesheet, monochrome, JetBrains Mono, 2px rules, purple mark
only. Pages: home, system, local/DSH, build, releases, legal. Deployed with
Cloudflare Workers Static Assets via Wrangler.

## Plane

Plane is the project ledger. Modules: Foundation, Art, Home, Bot Builder, Parity,
Local/Remote, Site, Release. Issues are the tickets in `plane/issues.csv`, with
IDs matching this plan. The bootstrap script reads `PLANE_API_KEY` and
`PLANE_WORKSPACE_SLUG`; without credentials it validates the payload and writes a
dry-run JSON. No secrets are committed.

## Milestones

- **M0 Foundation** — v2 branch, tokens, README, Plane import, disclaimers.
- **M1 Art** — 32×32 cast + companions, hero, icon, light/dark tokens.
- **M2 Home** — five-tab shell, dashboard, instance card, Pixel grid.
- **M3 Builder** — bot-build.md skill, AI spec, create/configure flow, fallback.
- **M4 Parity** — sessions, models, usage, skills/MCP, approvals recovery.
- **M5 Local** — iOS local DSH + both remote harness, status/resources.
- **M6 Site** — WCKD static site, Wrangler config, build + dry run.
- **M7 Release** — screenshots, docs, signed APK/IPA, GitHub release.

## Acceptance

- App launches to Home in under 1.5s (demo, warm); no DSH boot at launch.
- Every Pixel has a 32×32 character and companion in light and dark.
- AI builder creates a valid profile on a live gateway, or clean fallback.
- All five tabs work on iPhone, iPad and Android; chat/approval flows intact.
- Site builds to static assets and `wrangler deploy --dry-run` passes.
- No affiliation claims; legal copy visible on site and in app.
