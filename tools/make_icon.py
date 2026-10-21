from PIL import Image, ImageDraw, ImageFilter
import math, random, os

W = 1024
S = 64            # pixel cell
GRID = 16         # 16 * 64 = 1024
img = Image.new('RGB', (W, W), (7, 5, 11))
px = img.load()

# Dark purple field with a single soft bloom and a subtle diagonal lift.
for y in range(W):
    for x in range(0, W, 2):
        nx, ny = x / W, y / W
        d = math.hypot(nx - .56, ny - .34)
        bloom = max(0.0, 1.0 - d / .82) ** 1.7
        top = max(0.0, 1.0 - ny)
        r = int(8 + 30 * bloom + 5 * top)
        g = int(6 + 10 * bloom + 2 * top)
        b = int(16 + 54 * bloom + 12 * top)
        for xx in range(x, min(x + 2, W)):
            px[xx, y] = (min(255, r), min(255, g), min(255, b))

# Pixel grid: N + b. 14 rows x 14 columns, centered in 16x16.
# N = bright porcelain, b = lavender; a couple of darker violet steps give it depth.
N, B, b, d, g = 'N', 'B', 'b', 'D', 'G'  # bright, lavender, mid, shadow, glow
rows = [
    "................",
    "................",
    "..N......B......",
    "..N......B......",
    "..NN.....B......",
    "..N.N....B......",
    "..N..N...B......",
    "..N...N..B......",
    "..N....N.BBBB...",
    "..N.....NB...B..",
    "..N......B...B..",
    "..N......BBBB...",
    "................",
    "................",
    "................",
    "................",
]
colors = {
    'N': (246, 243, 255),
    'B': (196, 181, 253),
    'b': (167, 139, 250),
    'D': (109, 40, 217),
}
# Draw with a 1-cell shadow offset on the lower-right, then the glyph.
shadow = Image.new('RGBA', (W, W), (0, 0, 0, 0))
sd = ImageDraw.Draw(shadow)
for y, row in enumerate(rows):
    for x, ch in enumerate(row):
        if ch in colors:
            sd.rectangle((x*S + S*0.68, y*S + S*0.78, x*S + S*1.68, y*S + S*1.78), fill=(20, 8, 42, 220))
shadow = shadow.filter(ImageFilter.GaussianBlur(S*0.32))
img = Image.alpha_composite(img.convert('RGBA'), shadow)

d = ImageDraw.Draw(img)
for y, row in enumerate(rows):
    for x, ch in enumerate(row):
        if ch not in colors:
            continue
        col = colors[ch]
        # crisp pixel block, with a tiny inner top highlight for a glassy edge
        d.rectangle((x*S, y*S, x*S + S - 1, y*S + S - 1), fill=col + (255,))
        if ch == 'N':
            d.rectangle((x*S, y*S, x*S + S - 1, y*S + S*0.18), fill=(255, 255, 255, 70))
        elif ch == 'B':
            d.rectangle((x*S, y*S, x*S + S - 1, y*S + S*0.16), fill=(255, 255, 255, 38))

# One minimal spark: a 2x2 plus of pixel light at the top right.
spark = [(12, 2), (11, 3), (12, 3), (13, 3), (12, 4)]
for x, y in spark:
    d.rectangle((x*S, y*S, x*S + S - 1, y*S + S - 1), fill=(216, 180, 254, 235))
d.rectangle((12*S, 2*S, 12*S + S - 1, 2*S + S - 1), fill=(255, 255, 255, 255))

img = img.convert('RGB')
out = '/Users/dearwolf/wckdboy/niobium/brand/app-icon-1024.png'
img.save(out)

# Square pixel source for UI marks (no shadow downsample blur in the app).
img.resize((512, 512), Image.NEAREST).save('/Users/dearwolf/wckdboy/niobium/brand/pixel-niobium-512.png')

rounded = img.convert('RGBA')
mask = Image.new('L', (W, W), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, W, W), radius=232, fill=255)
rounded.putalpha(mask)
rounded.save('/Users/dearwolf/wckdboy/niobium/brand/app-icon-rounded.png')
print(out)
