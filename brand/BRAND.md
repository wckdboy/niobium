# Niobium

**Nb · 41 · Hermes, in your pocket.**

Niobium is the pocket command deck for a Hermes Agent. It is quiet, small and
purple-lit: a near-black field, one soft gradient, a pixel-art **Nb**, and no
ornament that does not carry information.

Inside the app, a Hermes profile is a **Pixel**. Not a bot, not an assistant, not
a card. A pixel is a small, durable agent with its own memory, model and tools.
A team of pixels is a roster. A group of pixels is a Room.

## Voice

Direct, warm, technically fluent. Short verbs. Never cute about failures.
A pixel is a teammate, not a toy.

## Mark

The icon is a hand-built 16×16 pixel raster: a porcelain **N** and a lavender
**b** on a violet-black gradient, with a single pixel spark. It is intentionally
low-resolution — nearest-neighbor scaling, no anti-aliasing. The same raster is
used as the in-app mark, the launch mark, the Android adaptive foreground and the
favicon-sized avatar. Never redraw it in a different typeface, add an orbit ring,
or recolor the metal.

`41` may appear as small annotation text, never inside the icon.

## Colour

| Token | Hex | Use |
| --- | --- | --- |
| Void | `#07050C` | app background |
| Ink | `#0B0812` | sheets and bars |
| Surface | `#120E1B` | cards and fields |
| Ion | `#A78BFA` | primary action, active state, links |
| Ion deep | `#7C3AED` | gradient end, depth |
| Violet | `#E9D5FF` | softer agent signal |
| Mint | `#5EEAD4` | online / complete |
| Amber | `#FBBF24` | approval / attention |
| Rose | `#FB7185` | destructive / failed |

Primary actions use the two-stop `#A78BFA → #7C3AED` gradient. Ambient
backgrounds use the same two colours at 14–22% opacity over Void, with one very
soft `#3B0764` bloom at the bottom. Keep gradients subtle; the app should read
black at a glance.

## Type

System sans for everything. Display headings are tight (`-0.04em`), body copy is
15–16 pt, labels are sentence case (no all-caps sections), identifiers and tool
names use monospace. No custom fonts.

## Pixel avatars

Each Pixel has a deterministic 8×8 face tile. The body is the Pixel's accent;
eyes and mouth are cut out to Void. The tile has a soft violet-black fill and a
one-pixel border. Live Pixels carry a small mint square status light. Team rooms
stack two or three faces.
