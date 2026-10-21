from PIL import Image, ImageDraw
import os

ROOT = '/Users/dearwolf/wckdboy/niobium'
GRID = 16
CELL = 64
SIZE = GRID * CELL

# Simple faceted niobium-ore crystal, 16x16, purple on white.
rows = [
    "................",
    "................",
    ".......DD.......",
    "......DLLD......",
    ".....DLLLLD.....",
    "....DLLMMLLD....",
    "....DLMMMMLD....",
    "...DLLMMMMLLD...",
    "...DLMMMMMMLD...",
    "...DMMMMMMMMD...",
    "....DMMMMMMD....",
    "....DDMMMMDD....",
    ".....DMMMMD.....",
    "......DDDD......",
    "................",
    "................",
]
colors = {
    'D': (76, 29, 149, 255),    # deep violet outline
    'M': (124, 58, 237, 255),   # niobium purple
    'L': (167, 139, 250, 255),  # lavender facet
    'H': (237, 233, 254, 255),  # bright edge
}

img = Image.new('RGB', (SIZE, SIZE), (255, 255, 255))
d = ImageDraw.Draw(img)
for y, row in enumerate(rows):
    for x, ch in enumerate(row):
        if ch not in colors:
            continue
        d.rectangle((x*CELL, y*CELL, x*CELL+CELL-1, y*CELL+CELL-1), fill=colors[ch][:3])
# A single white pixel facet catches the light.
for x, y in [(7, 5), (7, 6)]:
    d.rectangle((x*CELL, y*CELL, x*CELL+CELL-1, y*CELL+CELL-1), fill=(255, 255, 255))
# Two tiny pixel sparkles keep the ore legible at 16 px.
for x, y in [(12, 3), (3, 4)]:
    d.rectangle((x*CELL+CELL//3, y*CELL+CELL//3, x*CELL+CELL*2//3, y*CELL+CELL*2//3), fill=(196, 181, 253))

img.save(f'{ROOT}/brand/app-icon-ore-1024.png')
img.resize((512, 512), Image.NEAREST).save(f'{ROOT}/brand/ore-512.png')

rounded = img.convert('RGBA')
mask = Image.new('L', (SIZE, SIZE), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, SIZE, SIZE), radius=232, fill=255)
rounded.putalpha(mask)
rounded.save(f'{ROOT}/brand/app-icon-ore-rounded.png')

# iOS 1024 icon.
img.save(f'{ROOT}/ios/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png')

# Android adaptive foreground: transparent, ore at ~68% of the safe area.
fg_size = 512
fg = Image.new('RGBA', (fg_size, fg_size), (0, 0, 0, 0))
ore = img.resize((int(fg_size * 0.66), int(fg_size * 0.66)), Image.NEAREST).convert('RGBA')
# Make white transparent for the foreground.
px = ore.load()
for yy in range(ore.height):
    for xx in range(ore.width):
        r, g, b, a = px[xx, yy]
        if r > 248 and g > 248 and b > 248:
            px[xx, yy] = (255, 255, 255, 0)
fg.alpha_composite(ore, ((fg_size - ore.width)//2, (fg_size - ore.height)//2))
for density, size in [('mdpi', 108), ('hdpi', 162), ('xhdpi', 216), ('xxhdpi', 324), ('xxxhdpi', 432)]:
    fg.resize((size, size), Image.LANCZOS).save(f'{ROOT}/android/app/src/main/res/mipmap-{density}/ic_launcher_foreground.png')

# Monochrome: a flat alpha mask the launcher can tint.
small = img.resize((286, 286), Image.NEAREST).convert('RGB')
m = Image.new('L', (286, 286), 0)
sp = small.load(); mp = m.load()
for yy in range(286):
    for xx in range(286):
        r, g, b = sp[xx, yy]
        if r < 248 or g < 248 or b < 248:
            mp[xx, yy] = 255
white = Image.new('RGBA', (286, 286), (255, 255, 255, 255))
white.putalpha(m)
canvas = Image.new('RGBA', (432, 432), (255, 255, 255, 0))
canvas.alpha_composite(white, (73, 73))
canvas.save(f'{ROOT}/android/app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png')

# Update the Android background color to white.
open(f'{ROOT}/android/app/src/main/res/values/colors.xml', 'w').write('<resources><color name="icon_background">#FFFFFF</color></resources>')
print('ore icon written')
