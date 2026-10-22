#!/usr/bin/env python3
"""Package authored portraits and material masks for instant native customization.

This is an offline asset build. Native clients only composite cached integer
pixels; creating a Pixel does not call an image service or this build script.
"""
from pathlib import Path
import argparse
import colorsys
import hashlib
import json

from png_rgba import read_rgba
from character_engine48 import png, PALETTES

ROOT = Path(__file__).resolve().parents[1]
IDS = ["orion", "nova", "forge", "sage", "nyx", "astra", "vesper", "rook"]
SOURCE = "brand/pixel-cast-runtime-v6.png"


def rgb(hex_color):
    return int(hex_color.removeprefix("#"), 16)


def channels(color):
    return (color >> 16 & 255, color >> 8 & 255, color & 255)


def shade(color, amount):
    return sum(int(channel * amount) << shift for channel, shift in zip(channels(color), [16, 8, 0]))


def material(key, x, y, color):
    """Dye garment fabric only, preserving authored face, hair and metal.

    Tight per-portrait garment regions avoid the blotchy full-portrait recolors
    produced by guessing skin/hair masks from a shared bounding box.
    """
    r, g, b = color
    h, saturation, value = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    regions = {
        "orion": y >= 31 and (x < 17 or x > 36 or y >= 40),
        "nova": y >= 28 and 18 <= x <= 37,
        "forge": y >= 28 and (15 <= x <= 23 or 30 <= x <= 33),
        "sage": y >= 29 and 17 <= x <= 40,
        "nyx": y >= 31 and 13 <= x <= 39,
        "astra": y >= 31 and 19 <= x <= 39,
        "vesper": y >= 29 and 14 <= x <= 40,
        "rook": y >= 31 and (x < 22 or x > 35 or y >= 43),
    }
    if not regions[key] or value < .14:
        return 0
    warm = r > g * 1.12 and g > b * 1.04
    # Warm skin, leather and gold stay authored. Nyx's saturated red fabric
    # belongs to the garment, while her softer skin colors stay untouched.
    red_fabric = key == "nyx" and saturation > .80 and (h < .035 or h > .96)
    fabric = .48 < h < .92 or .18 < h < .45 or (value < .38 and saturation < .65)
    return 1 if red_fabric or (not warm and fabric) else 0


def build_catalog():
    width, height, pixels = read_rgba(ROOT / SOURCE)
    assert (width, height) == (192, 96)
    looks = {}
    for index, key in enumerate(IDS):
        cell = [row[index % 4 * 48:(index % 4 + 1) * 48]
                for row in pixels[index // 4 * 48:(index // 4 + 1) * 48]]
        colors = sorted({(r << 16) | (g << 8) | b for row in cell for r, g, b, a in row if a})
        assert len(colors) <= 32
        slots = {color: slot for slot, color in enumerate(colors)}
        indices, roles, luminances = [], [], []
        for y, row in enumerate(cell):
            for x, (r, g, b, a) in enumerate(row):
                assert a in (0, 255)
                indices.append(slots[(r << 16) | (g << 8) | b] if a else -1)
                roles.append(material(key, x, y, (r, g, b)) if a else 0)
                luminances.append((54 * r + 183 * g + 19 * b) >> 8)
        ranges = {}
        for role in (1,):
            values = [l for l, r in zip(luminances, roles) if r == role]
            if not values:
                raise ValueError(f"{key} has no pixels for material {role}")
            ranges[role] = (min(values), max(values))
        shades = []
        for value, role in zip(luminances, roles):
            low, high = ranges.get(role, (0, 255))
            shades.append(min(3, (value - low) * 4 // (high - low + 1)) if role else 0)
        looks[key] = dict(colors=colors, pixels=indices, materials=roles, shades=shades)
    accents = {key: [shade(rgb(tone["A"]), amount) for amount in (.25, .45, .70, 1)] for key, tone in PALETTES.items()}
    return dict(version=1, size=48, looks=looks, accents=accents)


def compose(look, data):
    art = data["looks"][look["portrait"]]
    pigments = {1: data["accents"].get(look.get("accent"))}
    colors = []
    for i, index in enumerate(art["pixels"]):
        if index < 0:
            colors.append(None)
            continue
        palette = pigments.get(art["materials"][i])
        color = palette[art["shades"][i]] if palette else art["colors"][index]
        colors.append("#%06x" % color)
    return [colors[i:i + 48] for i in range(0, 48 * 48, 48)]


def artifacts():
    data = build_catalog()
    encoded = (json.dumps(data, separators=(",", ":")) + "\n").encode()
    outputs = {path: encoded for path in ["ios/Resources/Assets.xcassets/PortraitLookCatalog.dataset/catalog.json", "android/app/src/main/assets/portrait_looks.json"]}
    outputs["ios/Resources/Assets.xcassets/PortraitLookCatalog.dataset/Contents.json"] = (
        json.dumps(dict(data=[dict(filename="catalog.json", idiom="universal")], info=dict(author="xcode", version=1)), indent=2) + "\n").encode()
    examples = [dict(portrait=key) for key in IDS]
    examples += [dict(portrait=key, accent="nova" if index % 2 else "astra")
                 for index, key in enumerate(IDS)]
    sheet = [[None] * 192 for _ in range(192)]
    fixtures = []
    for index, look in enumerate(examples):
        cell = compose(look, data)
        for y, row in enumerate(cell):
            sheet[index // 4 * 48 + y][index % 4 * 48:(index % 4 + 1) * 48] = row
        raw = bytes(channel for row in cell for color in row for channel in
                    ((*channels(rgb(color)), 255) if color else (0, 0, 0, 0)))
        fixtures.append(dict(look=look, rgba_sha256=hashlib.sha256(raw).hexdigest()))
    outputs["brand/pixel-looks-v1-gallery.png"] = png(sheet, 5)
    outputs["site/public/pixel-custom-atlas.png"] = png(sheet[96:])
    fixture_bytes = (json.dumps(fixtures, indent=2) + "\n").encode()
    for path in ["tests/fixtures/portrait-looks.json", "ios/NativeTests/Fixtures/portrait-looks.json",
                 "android/app/src/test/resources/portrait-looks.json", "android/app/src/androidTest/assets/portrait-looks.json"]:
        outputs[path] = fixture_bytes
    return outputs


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    stale = []
    for name, raw in artifacts().items():
        path = ROOT / name
        if args.check:
            if not path.exists() or path.read_bytes() != raw:
                stale.append(name)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
    if stale:
        raise SystemExit("Stale portrait assets: " + ", ".join(stale))
    print("Verified" if args.check else "Built", "eight material-aware portrait looks and sixteen portable fixtures")


if __name__ == "__main__":
    main()
