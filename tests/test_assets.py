import csv
import hashlib
import json
from pathlib import Path
import struct
import unittest
import subprocess
import sys
from png_rgba import read_rgba
import zlib

ROOT = Path(__file__).resolve().parents[1]

class AssetsTests(unittest.TestCase):
    def test_native_icons_share_the_white_opaque_website_mark(self):
        icon = (ROOT / "brand/app-icon-n-1024.png").read_bytes()
        self.assertEqual(icon, (ROOT / "ios/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png").read_bytes())
        width, height, depth, kind, _, _, _ = struct.unpack(">IIBBBBB", icon[16:29])
        self.assertEqual((width, height, depth, kind), (1024, 1024, 8, 2))
        at, compressed = 8, b""
        while at < len(icon):
            size = struct.unpack(">I", icon[at:at+4])[0]
            if icon[at+4:at+8] == b"IDAT": compressed += icon[at+8:at+8+size]
            at += size+12
        raw = zlib.decompress(compressed)
        colors = set()
        for y in range(height):
            row = raw[y*(width*3+1):(y+1)*(width*3+1)]
            self.assertEqual(row[0], 0)
            colors.update(tuple(row[x:x+3]) for x in range(1, len(row), 3))
        self.assertEqual(colors, {(255,255,255), (167,139,250)})
        self.assertIn("#A78BFA", (ROOT / "site/public/favicon.svg").read_text())
        self.assertEqual(icon, (ROOT / "site/public/og/icon.png").read_bytes())
        for scale, suffix in [(1, ""), (2, "@2x"), (3, "@3x")]:
            mark = (ROOT / f"ios/Resources/Assets.xcassets/PixelNiobium.imageset/pixel-niobium@{scale}x.png").read_bytes()
            self.assertEqual(mark, (ROOT / f"ios/Resources/Assets.xcassets/LaunchMark.imageset/LaunchMark{suffix}.png").read_bytes())
            if scale == 3:
                self.assertEqual(mark, (ROOT / "android/app/src/main/res/drawable-nodpi/pixel_niobium.png").read_bytes())
        self.assertIn('src="/favicon.svg"', (ROOT / "site/layout.html").read_text())

    def test_skill_copies_cannot_drift(self):
        skill = (ROOT / "bot-build.md").read_bytes()
        # A colon in a plain YAML description prevents DSH from discovering it.
        description = next(line for line in skill.decode().splitlines() if line.startswith("description:"))
        self.assertIsInstance(json.loads(description.split(":", 1)[1].strip()), str)
        text = skill.decode()
        example = text.split("~~~json\n", 1)[1].split("\n~~~", 1)[0]
        recipe = json.loads(example)["character"]["recipe"]
        self.assertEqual(set(recipe), {"body","palette","skin","hair_tone","hair","face","outfit","accessory","detail"})
        for field in ["skin", "hair_tone", "detail"]:
            self.assertIn("\n- " + field + ":", text)
        for name in ["skills/bot-build.md", "ios/Resources/bot-build.md",
                     "android/app/src/main/assets/bot_build.md", "site/public/bot-build.md"]:
            self.assertEqual(skill, (ROOT / name).read_bytes(), name)

    def test_original_art_matches_recorded_provenance(self):
        manifest = json.loads((ROOT / "brand/art-provenance.json").read_text())
        for asset in manifest["assets"]:
            self.assertEqual(hashlib.sha256((ROOT / asset["file"]).read_bytes()).hexdigest(), asset["sha256"])
        current = (ROOT / "brand/pixel-cast-runtime-v4.png").read_bytes()
        self.assertEqual(current, (ROOT / "ios/Resources/Assets.xcassets/PixelCastAtlas.imageset/pixel-cast-atlas.png").read_bytes())
        self.assertEqual(current, (ROOT / "android/app/src/main/res/drawable-nodpi/pixel_cast_atlas.png").read_bytes())
        self.assertEqual(current, (ROOT / "site/public/pixel-cast-atlas.png").read_bytes())

    def test_recipe_atlas_is_shared_and_48_pixels(self):
        ios = (ROOT / "ios/Resources/character_engine.json").read_bytes()
        self.assertEqual(ios, (ROOT / "android/app/src/main/assets/character_engine.json").read_bytes())
        atlas = json.loads(ios)
        self.assertEqual(atlas["size"], 48)
        for group in ["bodies", "outfits", "faces", "accessories", "details"]:
            for rows in atlas[group].values():
                self.assertEqual(len(rows), 48)
                self.assertTrue(all(len(row) == 48 for row in rows))

    def test_authored_cast_has_exact_pixels_and_independent_companions(self):
        width,height,pixels = read_rgba(ROOT / "brand/pixel-cast-runtime-v4.png")
        self.assertEqual((width,height),(192,96))
        for index in range(8):
            cell=[row[(index%4)*48:(index%4+1)*48] for row in pixels[(index//4)*48:(index//4+1)*48]]
            opaque={pixel[:3] for row in cell for pixel in row if pixel[3]}
            self.assertLessEqual(len(opaque),32)
            self.assertGreater(len(opaque),16)
            self.assertEqual({p[3] for row in cell for p in row},{0,255})
            self.assertTrue(all(p[3]==0 for p in cell[0]+cell[-1]))
            self.assertTrue(all(row[0][3]==row[-1][3]==0 for row in cell))
        atlas=json.loads((ROOT / "ios/Resources/character_engine.json").read_text())
        self.assertEqual(len(atlas["companions"]),12)
        hashes=set()
        for name in atlas["companions"]:
            ios=ROOT / f"ios/Resources/Assets.xcassets/Comp_{name}.imageset/Comp_{name}.png"
            android=ROOT / f"android/app/src/main/res/drawable-nodpi/comp_{name}.png"
            self.assertEqual(ios.read_bytes(),android.read_bytes())
            w,h,rgba=read_rgba(ios)
            self.assertEqual((w,h),(16,16))
            self.assertEqual({p[3] for row in rgba for p in row},{0,255})
            hashes.add(hashlib.sha256(ios.read_bytes()).hexdigest())
        self.assertEqual(len(hashes),12)

    def test_generated_catalogs_and_seed_fixtures_cannot_drift(self):
        for tool in ["tools/character_engine48.py","tools/character_engine.py","tools/sync-pixel-personas.py"]:
            subprocess.run([sys.executable,str(ROOT/tool),"--check"],cwd=ROOT,check=True,capture_output=True)
        fixture=(ROOT/"tests/fixtures/character-recipes.json").read_bytes()
        for path in ["ios/NativeTests/Fixtures/character-recipes.json","android/app/src/test/resources/character-recipes.json",
                     "android/app/src/androidTest/assets/character-recipes.json"]:
            self.assertEqual(fixture,(ROOT/path).read_bytes(),path)

    def test_plane_export_retains_every_field_and_stable_id(self):
        snapshot = json.loads((ROOT / "plane/snapshot.json").read_text())
        with (ROOT / "plane/issues.csv").open(newline="") as stream:
            rows = list(csv.DictReader(stream))
        self.assertEqual(len(rows), len(snapshot["work_items"]))
        self.assertEqual(len({row["id"] for row in rows}), len(rows))
        self.assertTrue(all(None not in row and row["description"] for row in rows))
        self.assertEqual(len({row["id"] for row in snapshot["work_items"]}), len(rows))

if __name__ == "__main__":
    unittest.main()
