#!/usr/bin/env python3
"""Rasterize the website's pixel SVG into native icons. Standard library only."""
from pathlib import Path
from xml.etree import ElementTree
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
SVG = ROOT / "site/public/favicon.svg"
COLOR = (167, 139, 250)
RECTS = [tuple(int(node.attrib[k]) for k in ("x", "y", "width", "height"))
         for node in ElementTree.parse(SVG).getroot() if node.tag.endswith("rect")]
assert RECTS and all(x >= 0 and y >= 0 and x+w <= 16 and y+h <= 16 for x,y,w,h in RECTS)

def png_chunk(kind, payload):
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))

def render(size, extent, opaque=False, monochrome=False):
    channels = 3 if opaque else 4
    background = bytes((255,255,255) if opaque else (0,0,0,0))
    color = (255,255,255) if monochrome else COLOR
    ink = bytes(color if opaque else color + (255,))
    pixels = bytearray(background * size * size)
    start = (size - extent) // 2
    for x, y, width, height in RECTS:
        left, top = start + round(x*extent/16), start + round(y*extent/16)
        right, bottom = start + round((x+width)*extent/16), start + round((y+height)*extent/16)
        stripe = ink * (right-left)
        for row in range(top, bottom):
            offset = (row*size + left)*channels
            pixels[offset:offset+len(stripe)] = stripe
    stride = size*channels
    scanlines = b"".join(b"\0" + pixels[row*stride:(row+1)*stride] for row in range(size))
    header = struct.pack(">IIBBBBB", size, size, 8, 2 if opaque else 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", header) + png_chunk(b"IDAT", zlib.compress(scanlines, 9)) + png_chunk(b"IEND", b"")

def save(relative, data):
    path = ROOT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)

icon = render(1024, 640, opaque=True)
save("brand/app-icon-n-1024.png", icon)
save("ios/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png", icon)
save("brand/n-mark.png", render(512, 320))
for density, size in [("mdpi",108),("hdpi",162),("xhdpi",216),("xxhdpi",324),("xxxhdpi",432)]:
    save("android/app/src/main/res/mipmap-"+density+"/ic_launcher_foreground.png", render(size, round(size*0.56)))
save("android/app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png", render(432, 240, monochrome=True))
for size, name in [(160,"LaunchMark.png"),(320,"LaunchMark@2x.png"),(480,"LaunchMark@3x.png")]:
    save("ios/Resources/Assets.xcassets/LaunchMark.imageset/"+name, render(size, round(size*0.8)))
print("Built native icons from site/public/favicon.svg on solid white.")
