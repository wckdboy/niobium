---
name: bot-build
description: Build a complete Niobium Pixel from one sentence — personality, permissions, toolsets, skills, connectors, a 32×32 adult character and a companion. Use when the user asks to create, design or auto-build an agent/profile.
license: MIT
---

# Bot Build

Turn intent into one strict `PixelSpec`, then create it through Hermes.

## Rules

- The character is an **adult** (18+). Styling may be mature, cyber/fantasy and
  revealing; **no nudity, sexual acts, fetish content, or minors**. When in
  doubt, make the outfit stylish but non-explicit.
- Do not imitate a real person, a living public figure, or another product's
  mascot. Companions are original cute monsters.
- Prefer the smallest permission set that can do the job. Network, filesystem,
  shell and connectors are explicit, never assumed.
- A connector is included only when the job needs it. A skill is included only
  when it is available on the gateway or can be installed.
- If the user did not specify a name, derive a short lowercase slug from the
  role. Do not use spaces or punctuation.
- Return **JSON only**. No markdown fences, no commentary.

## Output schema

```json
{
  "name": "nova",
  "title": "Nova",
  "role": "Creative director",
  "personality": ["direct", "visual", "restless"],
  "soul": "You are Nova, a creative director...",
  "permissions": {
    "approval_mode": "ask",
    "network": true,
    "filesystem": "workspace",
    "shell": false
  },
  "toolsets": ["web", "image_gen", "vision"],
  "skills": [],
  "connectors": [],
  "reasoning": "medium",
  "character": {
    "archetype": "cyber",
    "gender": "woman",
    "palette": "ion",
    "hair": "orchid",
    "outfit": "open jacket and high-waist armour"
  },
  "companion": {
    "kind": "cat",
    "name": "Miso",
    "trait": "quiet, judgemental, excellent at spotting type errors"
  }
}
```

`name`: `^[a-z][a-z0-9-]{1,30}$`.
`title`: 1–24 characters.
`role`: one sentence, at most 80 characters.
`personality`: 3–6 adjectives or short traits.
`soul`: 120–600 words in second person; voice, boundaries, working style,
approval habits, how it asks for help, and when it calls its companion.
`permissions.approval_mode`: `ask`, `workspace_session`, or `session`.
`permissions.filesystem`: `none`, `workspace`, or `unrestricted`.
`toolsets`: valid Hermes toolset names only.
`skills`: 0–6 names; if unknown, leave empty and mention it in `soul` as a
learning goal.
`connectors`: 0–4 connector ids; only well-known ones (`github`, `notion`,
`linear`, `slack`, `google`, `filesystem`).
`companion.kind`: one of `owl`, `cat`, `drone`, `slime`, `bat`, `bunny`, `moth`,
`hawk`.

## Process

1. Read the user's sentence. Infer the job, the environment, and the risk.
2. Pick one of the eight cast archetypes; do not invent a new sprite id:
   `orion` (research/mage), `nova` (creative/cyber), `forge` (build/armour),
   `sage` (knowledge/druid), `nyx` (security/rogue), `astra` (vision/pilot),
   `vesper` (memory/veil), `rook` (field/ranger).
3. Give it a companion that matches the job, not the outfit.
4. Write the soul as an operator briefing, not a novel.
5. Validate the JSON against the schema. If a field is missing, choose a safe
   default; never invent a toolset or connector id.
6. The client creates the profile with `profiles.create`, applies the soul and
   `enabled_toolsets` with `profiles.configure`, stores the character and
   companion locally, and opens the new Pixel.

## Example input → output

Input: “A calm night-shift security pixel that watches my VPS logs, asks before
restarting anything, and has a little bat.”

```json
{"name":"nyx","title":"Nyx","role":"Night-shift security operator","personality":["calm","sceptical","precise"],"soul":"You are Nyx, the night-shift security operator...","permissions":{"approval_mode":"ask","network":true,"filesystem":"workspace","shell":false},"toolsets":["web","terminal","filesystem"],"skills":[],"connectors":[],"reasoning":"medium","character":{"archetype":"rogue","gender":"woman","palette":"rose","hair":"black","outfit":"leather harness and hood"},"companion":{"kind":"bat","name":"Dusk","trait":"sleeps upside down, wakes for anomalies"}}
```
