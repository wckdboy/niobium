"""Structural art checks complement the reviewed visual contact sheets.

These verify real seams/occlusion and cross-frame identity, not attractiveness.
Both native platforms additionally hash the actual rendered RGBA against these
portable fixtures. The same frame changes only the face, never the outfit.
"""
import itertools
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
import character_engine48 as art


class CharacterArtTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data=art.atlas()

    def test_every_layer_has_valid_pixels_and_semantic_color_roles(self):
        data=self.data
        allowed=set(data["fixed"]) | set(data["palettes"]["orion"]) | {"."}
        layers=[]
        for group in ("bodies","faces","blink_faces","accessories","details"):
            layers.extend(data[group].values())
        for hair in data["hair"].values():layers.extend(hair.values())
        for cuts in data["outfits"].values():layers.extend(cuts.values())
        for layer in layers:
            self.assertEqual(len(layer),48)
            self.assertTrue(all(len(row)==48 for row in layer))
            self.assertLessEqual(set("".join(layer)),allowed)
        self.assertEqual(len({tuple(v) for v in data["faces"].values()}),6)

    def test_all_hair_and_headwear_leave_both_eye_anchors_readable(self):
        # Outline pixels count as occlusion too. Mechanical eye detail is an
        # intentional replacement, checked separately through native fixtures.
        for hair,accessory,build in itertools.product(art.HAIR,art.ACCESSORIES,["man","woman"]):
            recipe={**art.CAST["nova"],"hair":hair,"accessory":accessory,"body":build}
            pixels=art.compose(recipe,self.data)
            with self.subTest(hair=hair,accessory=accessory,body=build):
                self.assertEqual(pixels[13][23],self.data["fixed"]["W"])
                self.assertEqual(pixels[12][29],self.data["fixed"]["W"])

    def test_all_outfit_cuts_cover_the_chest_and_preserve_skin_color_roles(self):
        private_anchors=[(x,y) for x in range(20,24) for y in range(31,34)]
        private_anchors += [(x,y) for x in range(27,31) for y in range(30,34)]
        skin_roles=set("SsLd")
        for name,cuts in self.data["outfits"].items():
            for build,layer in cuts.items():
                with self.subTest(outfit=name,body=build):
                    self.assertTrue(all(layer[y][x] not in skin_roles|{"."} for x,y in private_anchors))
        for body in self.data["bodies"].values():
            self.assertTrue(all(body[y][x] not in skin_roles|{"."} for x,y in private_anchors))

    def test_blink_changes_only_the_face_and_keeps_one_hard_pixel_grid(self):
        for build,outfit,face,detail in itertools.product(["man","woman"],art.OUTFITS,art.FACES,art.DETAILS):
            recipe={**art.CAST["nova"],"body":build,"outfit":outfit,"face":face,"detail":detail}
            open_frame=art.compose(recipe,self.data)
            closed_frame=art.compose(recipe,self.data,blink=True)
            changed={(x,y) for y in range(48) for x in range(48) if open_frame[y][x]!=closed_frame[y][x]}
            with self.subTest(body=build,outfit=outfit,face=face,detail=detail):
                self.assertTrue(changed)
                self.assertTrue(all(22<=x<=32 and 11<=y<=14 for x,y in changed))
                self.assertEqual([[p is not None for p in row] for row in open_frame],
                                 [[p is not None for p in row] for row in closed_frame])
                self.assertLessEqual(len({p for row in open_frame for p in row if p}),32)


if __name__=="__main__":unittest.main()
