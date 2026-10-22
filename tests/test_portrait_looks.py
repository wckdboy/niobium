"""Verify portable art, untouched identities and bounded garment masks."""
import hashlib
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import portrait_looks as art
from png_rgba import read_rgba

class PortraitLookTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = json.loads((ROOT / "android/app/src/main/assets/portrait_looks.json").read_text())

    def test_both_platforms_and_all_fixture_copies_are_identical(self):
        self.assertEqual((ROOT / "android/app/src/main/assets/portrait_looks.json").read_bytes(),
                         (ROOT / "ios/Resources/Assets.xcassets/PortraitLookCatalog.dataset/catalog.json").read_bytes())
        fixture = (ROOT / "tests/fixtures/portrait-looks.json").read_bytes()
        for path in ["ios/NativeTests/Fixtures/portrait-looks.json", "android/app/src/test/resources/portrait-looks.json", "android/app/src/androidTest/assets/portrait-looks.json"]:
            self.assertEqual(fixture, (ROOT / path).read_bytes())

    def test_original_look_matches_authored_pixels_and_remixes_keep_the_identity(self):
        _, _, source = read_rgba(ROOT / art.SOURCE)
        for index, name in enumerate(art.IDS):
            data = self.catalog["looks"][name]
            original = art.compose(dict(portrait=name), self.catalog)
            source_cell = [row[index % 4 * 48:(index % 4 + 1) * 48] for row in source[index // 4 * 48:(index // 4 + 1) * 48]]
            for y in range(48):
                for x in range(48):
                    pixel = original[y][x]
                    expected = (*art.channels(art.rgb(pixel)),255) if pixel else (0,0,0,0)
                    self.assertEqual(source_cell[y][x], expected)
            self.assertEqual(set(data["materials"]), {0,1})
            self.assertTrue(all(data["materials"][y*48+x] == 0 for y in range(27) for x in range(48)))
            for accent in art.IDS:
                tinted = art.compose(dict(portrait=name, accent=accent), self.catalog)
                changes = 0
                for y in range(48):
                    for x in range(48):
                        self.assertEqual(original[y][x] is None, tinted[y][x] is None)
                        if original[y][x] != tinted[y][x]:
                            changes += 1
                            self.assertEqual(data["materials"][y*48+x],1)
                self.assertGreater(changes,0, name)

    def test_portable_hashes_match_the_canonical_art(self):
        for fixture in json.loads((ROOT / "tests/fixtures/portrait-looks.json").read_text()):
            cell = art.compose(fixture["look"],self.catalog)
            raw = bytes(c for row in cell for p in row for c in ((*art.channels(art.rgb(p)),255) if p else (0,0,0,0)))
            self.assertEqual(hashlib.sha256(raw).hexdigest(),fixture["rgba_sha256"])
