#!/usr/bin/env python3
"""Niobium character engine — deterministic 32x32 pixel-art layers.

The engine authors real pixel art on a 32x32 integer canvas, then exports:
  * character_engine.json  (layers + palettes, consumed by iOS/Android at runtime)
  * brand/cast/<id>.png    (marketing/site previews)
No anti-aliasing, no image model, no external assets.
"""
from __future__ import annotations
import json, pathlib
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
S = 32
TRANSPARENT = "."

class Canvas:
    def __init__(self): self.g = [[TRANSPARENT] * S for _ in range(S)]
    def set(self, x, y, k):
        if 0 <= x < S and 0 <= y < S: self.g[y][x] = k
    def rect(self, x, y, w, h, k):
        for yy in range(y, y + h):
            for xx in range(x, x + w): self.set(xx, yy, k)
    def row(self, y, x0, x1, k):
        for x in range(x0, x1 + 1): self.set(x, y, k)
    def ellipse(self, cx, cy, rx, ry, k):
        for y in range(cy - ry, cy + ry + 1):
            for x in range(cx - rx, cx + rx + 1):
                if ((x - cx) / max(rx, 1)) ** 2 + ((y - cy) / max(ry, 1)) ** 2 <= 1.02:
                    self.set(x, y, k)
    def outline(self, k="K"):
        """Add a 1px outline around every opaque cell (does not overwrite it)."""
        add = []
        for y in range(S):
            for x in range(S):
                if self.g[y][x] != TRANSPARENT: continue
                for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                    nx, ny = x+dx, y+dy
                    if 0 <= nx < S and 0 <= ny < S and self.g[ny][nx] not in (TRANSPARENT, k):
                        add.append((x,y)); break
        for x,y in add: self.set(x,y,k)
    def rows(self): return ["".join(r) for r in self.g]

# ---------------------------------------------------------------- body

def body(gender: str) -> Canvas:
    c = Canvas()
    # head + jaw + ears
    c.ellipse(16, 12, 6, 7, "S")
    c.rect(10, 12, 12, 4, "S")
    c.rect(9, 12, 1, 4, "s"); c.rect(22, 12, 1, 4, "s")
    # jaw shadow
    c.row(18, 12, 19, "s"); c.row(19, 13, 18, "s")
    # neck
    c.rect(13, 18, 6, 5, "S"); c.rect(14, 19, 4, 4, "s")
    # shoulders + chest skin, wider for men
    width = 13 if gender == "man" else 11
    c.rect(16-width, 23, width*2, 9, "S")
    c.rect(16-width, 23, width*2, 2, "S")
    c.rect(16-width, 30, width*2, 2, "s")
    c.rect(6, 26, 4, 6, "s"); c.rect(22, 26, 4, 6, "s")
    # collarbone hints
    c.row(24, 12, 14, "s"); c.row(24, 17, 19, "s")
    c.outline()
    return c

# ---------------------------------------------------------------- hair

def hair(style: str):
    back = Canvas(); front = Canvas()
    if style == "silver_swept":
        back.rect(8, 4, 16, 15, "H"); back.rect(7, 8, 3, 10, "h"); back.rect(21, 6, 3, 9, "H")
        front.rect(9, 3, 14, 5, "H"); front.rect(10, 7, 5, 3, "H"); front.rect(17, 6, 5, 3, "G")
        front.rect(13, 4, 3, 1, "G"); front.rect(8, 8, 2, 5, "h")
    elif style == "violet_bob":
        back.rect(7, 4, 18, 18, "H"); back.rect(6, 8, 3, 13, "h"); back.rect(22, 8, 3, 13, "H")
        front.rect(8, 3, 16, 7, "H"); front.rect(9, 8, 4, 2, "h"); front.rect(18, 6, 5, 3, "G")
        front.rect(7, 10, 2, 9, "H"); front.rect(23, 10, 2, 9, "h")
    elif style == "undercut":
        back.rect(9, 4, 14, 8, "H"); back.rect(8, 5, 3, 6, "h")
        front.rect(9, 3, 14, 4, "H"); front.rect(11, 6, 8, 2, "G"); front.rect(7, 7, 3, 4, "h")
    elif style == "locs":
        back.rect(7, 4, 18, 22, "H"); back.rect(6, 9, 3, 17, "h")
        for x in (7,10,13,18,21,24): back.rect(x, 8, 2, 16, "H")
        front.rect(8, 3, 16, 5, "H"); front.rect(10, 7, 12, 2, "h"); front.rect(11, 3, 5, 2, "G")
    elif style == "cat_black":
        back.rect(7, 4, 18, 20, "H"); back.rect(6, 9, 3, 14, "h"); back.rect(22, 9, 3, 14, "H")
        front.rect(8, 3, 16, 6, "H"); front.rect(10, 7, 12, 2, "h")
        for i in range(4):
            back.rect(8+i, 1+i, 1, 2, "H"); back.rect(22-i, 1+i, 1, 2, "H")
    elif style == "white_pilot":
        back.rect(9, 4, 14, 8, "H"); back.rect(8, 6, 3, 6, "h"); back.rect(21, 6, 3, 6, "H")
        front.rect(9, 3, 14, 4, "H"); front.rect(11, 6, 5, 2, "G"); front.rect(17, 6, 5, 2, "H")
    elif style == "silver_bob":
        back.rect(7, 4, 18, 17, "H"); back.rect(6, 8, 3, 12, "h"); back.rect(22, 8, 3, 12, "h")
        front.rect(8, 3, 16, 6, "H"); front.rect(10, 7, 5, 2, "h"); front.rect(18, 5, 5, 3, "G")
    elif style == "headwrap":
        back.rect(8, 3, 16, 10, "h"); back.rect(7, 8, 3, 9, "H"); back.rect(23, 8, 3, 9, "H")
        front.rect(8, 2, 16, 5, "H"); front.rect(9, 6, 14, 2, "G"); front.rect(23, 9, 3, 7, "h")
    for c in (back, front): c.outline()
    return back, front

# ---------------------------------------------------------------- face

def face(face_id: str) -> Canvas:
    c = Canvas()
    # brows
    if face_id == "soft":
        c.rect(12, 9, 3, 1, "h"); c.rect(18, 9, 3, 1, "h")
        c.rect(13, 11, 2, 2, "W"); c.rect(18, 11, 2, 2, "W")
        c.set(14, 11, "E"); c.set(19, 11, "E")
        c.set(14, 12, "D"); c.set(19, 12, "D")
        c.rect(14, 15, 4, 1, "a")
    elif face_id == "sharp":
        c.rect(11, 9, 4, 1, "h"); c.rect(18, 8, 4, 1, "h")
        c.rect(13, 11, 2, 1, "E"); c.rect(18, 11, 2, 1, "E")
        c.set(14, 11, "D"); c.set(19, 11, "D")
        c.row(15, 15, 16, "a"); c.set(14, 15, "o")
    elif face_id == "calm":
        c.rect(12, 9, 3, 1, "h"); c.rect(18, 9, 3, 1, "h")
        c.rect(13, 11, 3, 1, "E"); c.rect(18, 11, 3, 1, "E")
        c.set(16, 14, "s"); c.rect(14, 15, 4, 1, "a")
    elif face_id == "fierce":
        c.rect(11, 8, 4, 1, "h"); c.rect(18, 8, 4, 1, "h")
        c.rect(13, 10, 3, 2, "W"); c.rect(18, 10, 3, 2, "W")
        c.set(14, 10, "E"); c.set(19, 10, "E"); c.set(15, 11, "D"); c.set(20, 11, "D")
        c.row(15, 14, 17, "a"); c.set(18, 14, "a")
    c.outline("K")
    return c

# ---------------------------------------------------------------- outfit

def outfit(style: str) -> Canvas:
    c = Canvas()
    if style == "mage":
        c.rect(6, 23, 8, 9, "O"); c.rect(18, 23, 8, 9, "O")
        c.rect(8, 23, 3, 7, "o"); c.rect(21, 23, 3, 7, "o")
        c.rect(10, 23, 2, 2, "A"); c.rect(20, 23, 2, 2, "A")
        c.row(24, 14, 17, "S")
        c.rect(15, 24, 2, 7, "A")
        c.outline()
    elif style == "cyber":
        c.rect(5, 24, 8, 8, "O"); c.rect(19, 24, 8, 8, "O")
        c.rect(5, 24, 2, 8, "o"); c.rect(25, 24, 2, 8, "o")
        c.rect(12, 23, 8, 2, "S"); c.rect(13, 25, 6, 1, "A")
        c.rect(10, 26, 3, 5, "A"); c.rect(19, 26, 3, 5, "A")
        c.rect(11, 23, 2, 3, "O"); c.rect(19, 23, 2, 3, "O")
        c.outline()
    elif style == "armor":
        c.rect(5, 23, 9, 9, "O"); c.rect(18, 23, 9, 9, "O")
        c.rect(5, 23, 4, 2, "A"); c.rect(23, 23, 4, 2, "A")
        c.rect(8, 25, 5, 2, "o"); c.rect(19, 25, 5, 2, "o")
        c.rect(12, 24, 8, 1, "A"); c.rect(15, 25, 2, 6, "A")
        c.outline()
    elif style == "druid":
        c.rect(6, 23, 8, 9, "O"); c.rect(18, 23, 8, 9, "O")
        c.rect(8, 23, 3, 8, "o"); c.rect(21, 23, 3, 8, "o")
        c.rect(10, 23, 4, 2, "A"); c.rect(18, 23, 4, 2, "A")
        c.rect(14, 25, 4, 6, "O"); c.rect(15, 29, 2, 1, "A")
        c.outline()
    elif style == "rogue":
        c.rect(5, 25, 8, 7, "O"); c.rect(19, 25, 8, 7, "O")
        c.rect(8, 23, 2, 6, "A"); c.rect(22, 23, 2, 6, "A")
        c.rect(12, 26, 8, 1, "A"); c.rect(14, 27, 4, 1, "O")
        c.outline()
    elif style == "pilot":
        c.rect(5, 23, 9, 9, "O"); c.rect(18, 23, 9, 9, "O")
        c.rect(6, 23, 2, 9, "o"); c.rect(24, 23, 2, 9, "o")
        c.rect(10, 23, 3, 2, "S"); c.rect(19, 23, 3, 2, "S")
        c.rect(15, 24, 2, 8, "A"); c.rect(12, 26, 3, 1, "A"); c.rect(19, 26, 3, 1, "A")
        c.outline()
    elif style == "veil":
        c.rect(5, 23, 22, 9, "O"); c.rect(7, 23, 18, 2, "o")
        c.rect(12, 23, 8, 2, "S"); c.rect(14, 25, 4, 1, "A")
        c.rect(4, 23, 3, 8, "O"); c.rect(25, 23, 3, 8, "O")
        c.outline()
    elif style == "ranger":
        c.rect(5, 23, 9, 9, "O"); c.rect(18, 23, 9, 9, "O")
        c.rect(9, 23, 5, 2, "A"); c.rect(18, 23, 5, 2, "A")
        c.rect(6, 25, 2, 7, "o"); c.rect(24, 25, 2, 7, "o")
        c.rect(14, 25, 4, 6, "S"); c.rect(12, 27, 2, 1, "A"); c.rect(18, 27, 2, 1, "A")
        c.outline()
    return c

# ---------------------------------------------------------------- accessory

def accessory(kind: str) -> Canvas:
    c = Canvas()
    if kind == "visor":
        c.rect(11, 10, 10, 2, "A"); c.rect(12, 10, 3, 1, "W"); c.rect(18, 10, 3, 1, "W")
    elif kind == "goggles":
        c.rect(10, 8, 5, 3, "m"); c.rect(17, 8, 5, 3, "m")
        c.rect(11, 9, 3, 1, "A"); c.rect(18, 9, 3, 1, "A")
    elif kind == "antlers":
        c.rect(8, 2, 1, 5, "m"); c.rect(7, 1, 1, 2, "A"); c.rect(23, 2, 1, 5, "m"); c.rect(24, 1, 1, 2, "A")
    elif kind == "cat_ears":
        c.rect(8, 2, 2, 4, "h"); c.rect(22, 2, 2, 4, "h"); c.rect(9, 3, 1, 2, "A"); c.rect(23, 3, 1, 2, "A")
    elif kind == "circlet":
        c.rect(11, 6, 10, 1, "A"); c.rect(15, 5, 2, 1, "W")
    elif kind == "veil":
        c.rect(6, 4, 3, 14, "O"); c.rect(23, 4, 3, 14, "O"); c.rect(11, 3, 10, 1, "A")
    elif kind == "headwrap":
        c.rect(8, 3, 16, 3, "A"); c.rect(22, 5, 4, 8, "O")
    return c

# ---------------------------------------------------------------- companions

def companion(kind: str) -> Canvas:
    c = Canvas()
    if kind == "owl":
        c.ellipse(8, 8, 5, 5, "C"); c.rect(3, 4, 3, 3, "C"); c.rect(11, 4, 3, 3, "C")
        c.rect(5, 7, 2, 2, "W"); c.rect(10, 7, 2, 2, "W"); c.rect(6, 7, 1, 1, "D"); c.rect(11, 7, 1, 1, "D")
        c.rect(7, 10, 2, 1, "A"); c.rect(4, 12, 2, 2, "c"); c.rect(11, 12, 2, 2, "c")
    elif kind == "cat":
        c.ellipse(8, 9, 5, 4, "C"); c.rect(4, 3, 3, 4, "C"); c.rect(10, 3, 3, 4, "C")
        c.rect(5, 8, 2, 2, "E"); c.rect(10, 8, 2, 2, "E"); c.rect(6, 8, 1, 1, "D"); c.rect(11, 8, 1, 1, "D")
        c.rect(7, 10, 2, 1, "D"); c.rect(12, 9, 3, 2, "C")
    elif kind == "drone":
        c.rect(4, 6, 9, 6, "C"); c.rect(2, 4, 3, 2, "A"); c.rect(12, 4, 3, 2, "A")
        c.rect(5, 8, 7, 2, "D"); c.rect(6, 8, 1, 1, "E"); c.rect(9, 8, 1, 1, "E"); c.rect(7, 12, 3, 1, "c")
    elif kind == "slime":
        c.ellipse(8, 10, 6, 4, "C"); c.ellipse(8, 8, 4, 3, "C")
        c.rect(6, 9, 1, 2, "D"); c.rect(10, 9, 1, 2, "D"); c.rect(7, 12, 2, 1, "A")
    elif kind == "bat":
        c.ellipse(8, 9, 4, 4, "C"); c.rect(2, 8, 5, 3, "C"); c.rect(10, 8, 5, 3, "C")
        c.rect(6, 8, 1, 2, "W"); c.rect(10, 8, 1, 2, "W"); c.rect(6, 9, 1, 1, "D"); c.rect(10, 9, 1, 1, "D")
        c.rect(5, 12, 1, 2, "c"); c.rect(11, 12, 1, 2, "c")
    elif kind == "bunny":
        c.ellipse(8, 10, 5, 4, "C"); c.rect(5, 2, 2, 6, "C"); c.rect(10, 2, 2, 6, "C")
        c.rect(6, 9, 1, 1, "D"); c.rect(10, 9, 1, 1, "D"); c.rect(7, 11, 2, 1, "A")
    elif kind == "moth":
        c.ellipse(8, 8, 3, 5, "C"); c.rect(2, 6, 5, 5, "A"); c.rect(10, 6, 5, 5, "A")
        c.rect(7, 7, 2, 2, "W"); c.rect(7, 8, 1, 1, "D")
    elif kind == "hawk":
        c.ellipse(8, 8, 5, 4, "C"); c.rect(2, 7, 4, 4, "A"); c.rect(11, 7, 4, 4, "A")
        c.rect(6, 7, 1, 1, "D"); c.rect(10, 7, 1, 1, "D"); c.rect(7, 10, 3, 1, "A")
    c.outline()
    return c

PALETTES = {
    "orion": {"S":"#E8B98F","s":"#C98B62","H":"#D9D4F2","h":"#9B91C9","G":"#FFFFFF","E":"#F5D36A","O":"#3B2A5E","o":"#241A3A","A":"#A78BFA","a":"#E9D5FF"},
    "nova": {"S":"#B97850","s":"#8D5435","H":"#C084FC","h":"#7C3AED","G":"#F5D0FE","E":"#5EEAD4","O":"#17131F","o":"#0B0810","A":"#A78BFA","a":"#F0ABFC"},
    "forge": {"S":"#7A4A2E","s":"#5C3520","H":"#201A22","h":"#4A3D4F","G":"#6B5B73","E":"#FBBF24","O":"#2B2620","o":"#17130F","A":"#F5C97A","a":"#FDE68A"},
    "sage": {"S":"#8D5A3B","s":"#6B3F28","H":"#5E7D4A","h":"#3F5A33","G":"#A3E635","E":"#A3E635","O":"#2F3E2D","o":"#1B241A","A":"#86EFAC","a":"#D9F99D"},
    "nyx": {"S":"#D8B49C","s":"#B07C5E","H":"#1A1620","h":"#3B2F45","G":"#7C3AED","E":"#FB7185","O":"#221320","o":"#110A12","A":"#E11D48","a":"#FDA4AF"},
    "astra": {"S":"#C68B62","s":"#96613D","H":"#F4F1FF","h":"#B9B2D8","G":"#FFFFFF","E":"#7DD3FC","O":"#1B2A3A","o":"#0D1620","A":"#38BDF8","a":"#BAE6FD"},
    "vesper": {"S":"#F0D0B6","s":"#C79D7A","H":"#E5E7EB","h":"#9CA3AF","G":"#FFFFFF","E":"#C4B5FD","O":"#14121A","o":"#09070C","A":"#E9D5FF","a":"#F5F3FF"},
    "rook": {"S":"#5E3B2A","s":"#3F261A","H":"#2B211C","h":"#57453A","G":"#8B6A50","E":"#F59E0B","O":"#3A2E24","o":"#211A13","A":"#D6A45E","a":"#FDE68A"},
}
FIXED = {"K":"#170D24","W":"#FFFFFF","D":"#120A1C","M":"#D9D2E8","m":"#7A6B8E","C":"#A78BFA","c":"#6D4AC7","E":"#5EEAD4","A":"#A78BFA","a":"#E9D5FF"}

def compose(spec, layers):
    out = [[TRANSPARENT]*S for _ in range(S)]
    for key in ("hair_back","body","outfit","face","bangs","accessory"):
        for y in range(S):
            for x in range(S):
                ch = layers[key][y][x]
                if ch != TRANSPARENT: out[y][x] = ch
    pal = {**FIXED, **PALETTES[spec["palette"]]}
    return out, pal

def preview(layers, spec, path):
    out, pal = compose(spec, layers)
    img = Image.new("RGBA",(S,S),(0,0,0,0)); px=img.load()
    for y in range(S):
        for x in range(S):
            ch=out[y][x]
            if ch in pal:
                h=pal[ch].lstrip("#"); px[x,y]=(int(h[0:2],16),int(h[2:4],16),int(h[4:6],16),255)
    img.resize((256,256),Image.NEAREST).save(path)

def main():
    specs = {
        "orion": dict(palette="orion", body="man", hair="silver_swept", face="calm", outfit="mage", accessory="circlet", companion="owl"),
        "nova": dict(palette="nova", body="woman", hair="violet_bob", face="sharp", outfit="cyber", accessory="visor", companion="cat"),
        "forge": dict(palette="forge", body="man", hair="undercut", face="fierce", outfit="armor", accessory="goggles", companion="drone"),
        "sage": dict(palette="sage", body="woman", hair="locs", face="soft", outfit="druid", accessory="antlers", companion="slime"),
        "nyx": dict(palette="nyx", body="woman", hair="cat_black", face="fierce", outfit="rogue", accessory="cat_ears", companion="bat"),
        "astra": dict(palette="astra", body="man", hair="white_pilot", face="sharp", outfit="pilot", accessory="visor", companion="bunny"),
        "vesper": dict(palette="vesper", body="woman", hair="silver_bob", face="calm", outfit="veil", accessory="veil", companion="moth"),
        "rook": dict(palette="rook", body="man", hair="headwrap", face="calm", outfit="ranger", accessory="headwrap", companion="hawk"),
    }
    layers_by_spec = {}
    for name, s in specs.items():
        hb,hf = hair(s["hair"])
        layers = {
            "hair_back": hb.rows(), "body": body(s["body"]).rows(), "outfit": outfit(s["outfit"]).rows(),
            "face": face(s["face"]).rows(), "bangs": hf.rows(), "accessory": accessory(s["accessory"]).rows(),
        }
        layers_by_spec[name] = layers
        preview(layers, s, ROOT/f"brand/cast/{name}-engine.png")
    data = {
        "size": S,
        "keys": {".":"transparent","K":"outline","S":"skin","s":"skinShadow","H":"hair","h":"hairShadow","G":"hairLight","E":"eye","W":"white","D":"dark","O":"outfit","o":"outfitShadow","A":"accent","a":"accentLight","M":"metal","m":"metalShadow","C":"companion","c":"companionShadow"},
        "palettes": PALETTES,
        "fixed": FIXED,
        "bodies": {"man": body("man").rows(), "woman": body("woman").rows()},
        "hair": {k: {"back": hair(k)[0].rows(), "front": hair(k)[1].rows()} for k in ("silver_swept","violet_bob","undercut","locs","cat_black","white_pilot","silver_bob","headwrap")},
        "faces": {k: face(k).rows() for k in ("soft","sharp","calm","fierce")},
        "outfits": {k: outfit(k).rows() for k in ("mage","cyber","armor","druid","rogue","pilot","veil","ranger")},
        "accessories": {k: accessory(k).rows() for k in ("visor","goggles","antlers","cat_ears","circlet","veil","headwrap","none")},
        "companions": {k: companion(k).rows() for k in ("owl","cat","drone","slime","bat","bunny","moth","hawk")},
        "cast": specs,
    }
    out = ROOT/"ios/Resources/character_engine.json"
    out.write_text(json.dumps(data, separators=(",",":")))
    (ROOT/"android/app/src/main/assets/character_engine.json").write_text(out.read_text())
    print(out, out.stat().st_size, "bytes")

if __name__ == "__main__":
    main()
