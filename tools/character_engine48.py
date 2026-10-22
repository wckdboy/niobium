#!/usr/bin/env python3
"""Build Niobium's deterministic 48px character layers and 16px familiars.

No image service or third-party package is needed at runtime or to rebuild this
catalog. The authored cast is a separate, versioned art asset.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
TRANSPARENT = "."

class Canvas:
    def __init__(self, size=48):
        self.size = size
        self.grid = [[TRANSPARENT] * size for _ in range(size)]
    def pixel(self, x, y, color):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.grid[y][x] = color
    def rect(self, x, y, w, h, color):
        for yy in range(y, y + h):
            for xx in range(x, x + w): self.pixel(xx, yy, color)
    def line(self, x0, y0, x1, y1, color):
        dx, dy = abs(x1-x0), -abs(y1-y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        error = dx + dy
        while True:
            self.pixel(x0, y0, color)
            if x0 == x1 and y0 == y1: break
            twice = 2 * error
            if twice >= dy: error += dy; x0 += sx
            if twice <= dx: error += dx; y0 += sy
    def polygon(self, points, color):
        for y in range(max(0, min(p[1] for p in points)), min(self.size, max(p[1] for p in points)+1)):
            crossings = []
            for a, b in zip(points, points[1:] + points[:1]):
                if (a[1] <= y < b[1]) or (b[1] <= y < a[1]):
                    crossings.append(a[0] + (y-a[1]) * (b[0]-a[0]) / (b[1]-a[1]))
            crossings.sort()
            for left, right in zip(crossings[::2], crossings[1::2]):
                for x in range(max(0, int(left+0.5)), min(self.size, int(right+0.5)+1)):
                    self.pixel(x, y, color)
        for a, b in zip(points, points[1:] + points[:1]): self.line(*a, *b, color)
    def ellipse(self, cx, cy, rx, ry, color):
        for y in range(cy-ry, cy+ry+1):
            for x in range(cx-rx, cx+rx+1):
                if ((x-cx)/max(rx,1))**2 + ((y-cy)/max(ry,1))**2 <= 1.02:
                    self.pixel(x,y,color)
    def outline(self):
        boundary=[]
        for y in range(self.size):
            for x in range(self.size):
                if self.grid[y][x] != TRANSPARENT: continue
                if any(0<=x+dx<self.size and 0<=y+dy<self.size and self.grid[y+dy][x+dx] not in (TRANSPARENT,"K")
                       for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))): boundary.append((x,y))
        for x,y in boundary: self.pixel(x,y,"K")
        return self
    def rows(self): return ["".join(row) for row in self.grid]
    p=pixel; r=rect; e=ellipse; poly=polygon

# Pigment roles are shared by every layer. Skin and hair overrides never change
# clothing, and garment colors never change the character's complexion.
FIXED = dict(K="#151321", W="#FFF5DF", D="#100E1A", M="#DEE4EB", m="#747A94", C="#A99AD8", c="#65558B")
PALETTES = {
 "orion": dict(S="#D8A980",s="#AA755B",L="#F2CCA0",d="#775044",H="#DBD7ED",h="#85849F",G="#FFF5F1",O="#433259",o="#251E36",B="#766187",A="#B498EB",a="#E0D0FF",E="#D6B767"),
 "nova": dict(S="#AD704D",s="#804731",L="#DC9A68",d="#522F2D",H="#9361C5",h="#4B316F",G="#DBA6F0",O="#332A42",o="#1B1A29",B="#656174",A="#A66AE0",a="#DCB1F5",E="#7EE3D4"),
 "forge": dict(S="#B78057",s="#83553D",L="#DDA674",d="#573B32",H="#393038",h="#201D2B",G="#79606B",O="#4D4542",o="#282832",B="#81766F",A="#D8A759",a="#FFE1A0",E="#EDA45D"),
 "sage": dict(S="#C4976B",s="#916647",L="#EAC59A",d="#5F473A",H="#557251",h="#2D473C",G="#A0B876",O="#3F6651",o="#233D36",B="#71956B",A="#A4CF89",a="#E1ECB2",E="#BED689"),
 "nyx": dict(S="#DDAC98",s="#A17468",L="#F5D1B3",d="#674B50",H="#302A3B",h="#171929",G="#65546F",O="#453048",o="#251D30",B="#7D546B",A="#CC647F",a="#F4B5BE",E="#F28587"),
 "astra": dict(S="#BC865D",s="#87573F",L="#E4B388",d="#553E36",H="#E1E4E9",h="#8C96B1",G="#FFF9EA",O="#334C6A",o="#202B43",B="#647E9C",A="#66BCDA",a="#B5E3EF",E="#71C4EB"),
 "vesper": dict(S="#895A46",s="#603D36",L="#B78160",d="#3F2C31",H="#CBC6E3",h="#7F7B9B",G="#F7EAF6",O="#514161",o="#2D263F",B="#8E7194",A="#C8A1D5",a="#F1C8EB",E="#C9A6F1"),
 "rook": dict(S="#79523E",s="#543D34",L="#AA7955",d="#382B2D",H="#564132",h="#302B2C",G="#947053",O="#50523B",o="#2A342D",B="#89905B",A="#C29F66",a="#E7CC93",E="#DFBB65")
}
SKIN_TONES = {
 "porcelain":dict(S="#E1B8A0",s="#AF8377",L="#F6D8B8",d="#7C5D5E"),
 "sand":dict(S="#D1A277",s="#9F7254",L="#F0C898",d="#704F41"),
 "copper":dict(S="#B97851",s="#87523C",L="#E3A171",d="#58382F"),
 "umber":dict(S="#8D614A",s="#624436",L="#BC8962",d="#412F2D"),
 "ebony":dict(S="#60463D",s="#42333A",L="#94694E",d="#2D2630")
}
HAIR_TONES = {
 "silver":dict(H="#C9CDDD",h="#7E879F",G="#F3ECEA"),
 "ink":dict(H="#302B3D",h="#181A2B",G="#655C78"),
 "violet":dict(H="#9465C6",h="#51367A",G="#D8A5EA"),
 "moss":dict(H="#5E7954",h="#344B3E",G="#A7BC7C"),
 "copper":dict(H="#AC6849",h="#633E39",G="#E1AA72"),
 "rose":dict(H="#B87593",h="#70445E",G="#F0B1B9"),
 "ice":dict(H="#78A7BF",h="#46627F",G="#B8E0DE"),
 "gold":dict(H="#BC995F",h="#7A5F49",G="#F2D393")
}

CAST = {
 "orion":dict(body="man",palette="orion",hair="silver_swept",face="calm",outfit="mage",accessory="circlet"),
 "nova":dict(body="woman",palette="nova",hair="violet_bob",face="soft",outfit="cyber",accessory="visor"),
 "forge":dict(body="man",palette="forge",hair="undercut",face="sharp",outfit="armor",accessory="goggles"),
 "sage":dict(body="woman",palette="sage",hair="locs",face="soft",outfit="druid",accessory="antlers"),
 "nyx":dict(body="woman",palette="nyx",hair="cat_black",face="fierce",outfit="rogue",accessory="cat_ears"),
 "astra":dict(body="man",palette="astra",hair="white_pilot",face="sharp",outfit="pilot",accessory="visor"),
 "vesper":dict(body="woman",palette="vesper",hair="silver_bob",face="calm",outfit="veil",accessory="veil"),
 "rook":dict(body="man",palette="rook",hair="headwrap",face="calm",outfit="ranger",accessory="headwrap")
}
HAIR = ["silver_swept","violet_bob","undercut","locs","cat_black","white_pilot","silver_bob","headwrap","braid","mohawk","long_waves","high_ponytail"]
FACES = ["soft","sharp","calm","fierce","wry","stern"]
OUTFITS = ["mage","cyber","armor","druid","rogue","pilot","veil","ranger","corsair","techwear","battlemage","astral"]
ACCESSORIES = ["none","visor","goggles","antlers","cat_ears","circlet","veil","headwrap","horns","antenna","ear_cuff","halo"]
DETAILS = ["none","freckles","scar","cyber_eye","rune","stubble"]
COMPANIONS = ["owl","cat","drone","slime","bat","bunny","moth","hawk","axolotl","imp","wisp","mimic"]


def body(kind):
 c=Canvas(); masculine=kind=="man"
 c.poly([(16,27),(22,25),(29,25),(35,28),(41,32),(42,43),(38,47),(10,47),(7,42),(9,32)],"O")
 if masculine:
  c.poly([(9,31),(15,29),(18,33),(16,43),(12,47),(7,44)],"S")
  c.poly([(35,29),(41,32),(43,44),(38,47),(34,40)],"S")
  c.line(10,34,9,42,"L");c.line(39,34,41,42,"s");c.line(11,44,15,43,"s")
 else:
  c.poly([(12,29),(16,30),(16,36),(13,43),(9,44),(9,37)],"S")
  c.poly([(34,29),(38,31),(40,41),(37,45),(34,40)],"S")
  c.line(11,33,10,40,"L");c.line(37,34,39,41,"s")
 c.poly([(22,22),(28,22),(29,28),(32,30),(28,34),(21,33),(17,29),(22,27)],"S")
 c.r(26,24,3,5,"s");c.line(20,29,23,30,"L");c.line(28,30,30,29,"s")
 c.poly([(19,9),(29,8),(33,12),(33,20),(30,24),(26,26),(22,25),(18,21),(17,14)],"S")
 c.poly([(18,14),(20,12),(20,20),(24,24),(23,25),(18,21)],"s")
 c.line(22,11,29,10,"L");c.line(30,14,31,18,"L");c.line(27,24,30,22,"s")
 c.r(16,16,2,4,"S");c.p(16,17,"L");c.p(17,19,"s");c.r(33,16,2,3,"s")
 # Opaque base garments remain behind all outfits, including cropped designs.
 c.poly([(18,32),(24,34),(31,31),(35,38),(33,47),(16,47),(15,38)],"O")
 c.r(17,45,17,3,"o");c.line(18,35,16,43,"B");c.line(32,37,34,44,"o")
 return c.outline()


def hair(style):
 b,f=Canvas(),Canvas()
 # A common adult head anchor keeps all hair/outfit/body combinations aligned.
 if style in ("locs","cat_black","long_waves","high_ponytail","braid"):
  b.poly([(17,9),(22,5),(30,6),(34,11),(36,28),(39,38),(33,42),(29,30),(18,35),(12,32),(14,21)],"h")
  b.poly([(17,12),(19,9),(18,27),(15,36),(12,36),(15,24)],"H")
  b.poly([(31,9),(34,13),(34,25),(38,36),(34,37),(31,27)],"H")
 else:
  b.poly([(17,9),(21,5),(29,6),(34,10),(35,20),(32,27),(29,23),(19,25),(15,20),(15,12)],"h")
 if style=="silver_swept":
  f.poly([(16,14),(18,7),(23,7),(25,4),(31,6),(34,8),(31,10),(24,12),(21,15),(20,11)],"H")
  f.line(19,8,26,8,"G");f.line(23,10,30,8,"G");f.line(16,14,17,19,"h");f.p(32,11,"G")
 elif style in ("violet_bob","silver_bob"):
  b.poly([(14,15),(16,9),(18,11),(17,30),(14,31),(12,28)],"H")
  b.poly([(32,12),(35,14),(36,29),(32,31),(31,24)],"H")
  f.poly([(16,11),(20,6),(29,6),(34,10),(32,13),(27,12),(22,16),(21,12),(17,17)],"H")
  f.line(19,9,25,8,"G");f.line(15,18,14,26,"G");f.line(33,17,34,27,"h")
  if style=="silver_bob": f.poly([(29,9),(32,11),(29,17),(26,16)],"H")
 elif style in ("undercut","mohawk"):
  f.poly([(17,13),(18,8),(22,8),(22,5),(25,7),(28,4),(30,7),(34,8),(32,12),(24,13),(21,11)],"H")
  f.line(21,9,29,9,"G");f.line(16,13,17,16,"h")
  if style=="mohawk": f.poly([(23,8),(23,3),(26,5),(28,2),(30,7),(29,11)],"H");f.line(25,5,25,8,"G")
 elif style=="locs":
  for x,y in [(15,10),(18,7),(22,6),(26,6),(30,8),(33,11)]:
   f.poly([(x,y),(x+2,y-1),(x+3,y+6),(x+1,y+10),(x-1,y+7)],"H");f.p(x+1,y+2,"G")
  for x in [13,16,33,36]: b.line(x,24,x+1,35,"H");b.p(x,28,"G");b.p(x+1,33,"G")
 elif style=="cat_black":
  f.poly([(16,13),(18,7),(25,6),(31,8),(34,12),(30,12),(26,10),(22,15),(20,12),(17,18)],"H")
  f.line(19,9,23,8,"G");f.p(31,10,"G");b.line(14,24,14,31,"G")
 elif style=="white_pilot":
  f.poly([(16,14),(16,9),(19,9),(18,6),(22,7),(23,4),(26,7),(30,5),(30,8),(34,8),(32,12),(27,12),(23,14),(21,11)],"H")
  f.line(19,9,25,8,"G");f.p(30,10,"G");f.r(16,15,2,3,"H")
 elif style=="headwrap":
  b.poly([(14,13),(16,8),(22,6),(29,7),(34,11),(35,29),(38,34),(34,36),(30,27),(31,15)],"H")
  f.poly([(16,11),(18,7),(29,7),(33,10),(32,14),(26,12),(20,14)],"H")
  f.line(18,10,30,11,"G");f.line(17,13,23,12,"h")
 elif style=="braid":
  f.poly([(16,13),(18,7),(27,6),(32,9),(32,12),(24,12),(20,16),(20,11)],"H");f.line(19,9,26,8,"G")
  for i in range(6):
   x=33+(i%2);y=22+i*3;b.poly([(x,y),(x+2,y-1),(x+3,y+2),(x+1,y+3),(x-1,y+1)],"H");b.p(x+1,y,"G")
  b.r(34,40,3,2,"A")
 elif style=="long_waves":
  f.poly([(16,14),(17,9),(21,6),(29,6),(33,10),(30,13),(27,11),(22,12),(19,17),(19,11)],"H")
  for x in [14,34]:
   b.line(x,20,x+2,25,"G");b.line(x+2,25,x,31,"H");b.line(x,31,x+2,36,"G")
  f.line(20,8,27,8,"G")
 elif style=="high_ponytail":
  b.poly([(26,7),(28,3),(34,3),(38,7),(37,14),(40,22),(39,33),(35,38),(35,23),(33,17),(34,11)],"H")
  b.line(35,7,36,12,"G");b.line(37,22,37,31,"G");b.r(28,6,5,2,"A")
  f.poly([(16,14),(18,8),(24,6),(30,8),(33,11),(29,12),(23,11),(19,14)],"H");f.line(19,9,25,8,"G")
 b.outline(); f.outline()
 # Hair frames the adult face; every variant keeps both eye anchors readable.
 for y in range(16,21):
  for x in range(20,32): f.grid[y][x]=TRANSPARENT
 return b,f


def face(kind):
 c=Canvas()
 # Small eyes, a defined nose/jaw and asymmetric mouths avoid oversized doll faces.
 left,right=21,28
 if kind in ("sharp","fierce","stern"):
  c.line(20,14,23,15,"h");c.line(28,15,31,14,"h")
 else:
  c.line(20,14,23,14,"h");c.line(28,14,31,14,"h")
 for x in [left,right]:
  c.r(x-1,17,4,1,"D");c.p(x,17,"W");c.p(x+1,17,"E");c.p(x+1,18,"D")
 c.p(26,18,"s");c.p(26,20,"s");c.p(27,21,"L")
 if kind=="soft": c.line(24,23,27,23,"s");c.p(27,24,"s");c.p(29,21,"L")
 elif kind=="calm": c.line(24,23,27,23,"s");c.p(28,22,"s")
 elif kind=="wry": c.line(24,23,27,22,"s");c.p(28,21,"s");c.p(21,13,"h")
 elif kind=="fierce": c.line(24,23,27,23,"s");c.p(24,22,"L");c.p(29,18,"a")
 elif kind=="stern": c.line(24,23,27,23,"s");c.p(23,24,"s");c.p(29,24,"s")
 else: c.line(24,23,27,22,"s");c.p(22,21,"s")
 return c


def outfit(kind):
 c=Canvas()
 # All chest panels are opaque. Exposed pixels are arms, neck or midriff only.
 if kind in ("mage","battlemage","veil"):
  c.poly([(16,26),(20,29),(19,40),(16,47),(5,47),(8,32)],"O")
  c.poly([(30,27),(34,28),(40,34),(43,47),(32,47),(30,39)],"O")
  c.poly([(8,34),(13,29),(12,43),(8,47)],"o");c.line(15,29,19,34,"A");c.line(30,29,34,34,"A")
  c.poly([(21,31),(26,33),(30,31),(30,45),(20,45)],"o");c.line(24,34,24,43,"B")
  c.r(19,44,14,3,"m");c.r(24,44,4,3,"A");c.p(25,45,"W")
  if kind=="battlemage":
   c.poly([(7,30),(15,28),(18,33),(15,37),(6,36)],"m");c.line(8,31,14,30,"M");c.r(34,34,5,7,"m");c.line(35,35,37,35,"A")
  if kind=="veil":
   c.poly([(19,30),(23,32),(26,33),(31,29),(33,36),(30,42),(20,42),(17,35)],"A");c.line(23,33,27,39,"a");c.r(18,43,16,2,"m");c.p(26,43,"W")
 elif kind in ("cyber","rogue","astral"):
  c.poly([(16,30),(21,32),(26,33),(31,30),(34,35),(30,39),(18,39),(14,35)],"O")
  c.line(18,31,17,36,"B");c.line(30,32,32,35,"B");c.line(20,38,29,38,"A")
  c.poly([(19,40),(30,40),(32,44),(18,44)],"S");c.line(27,40,28,43,"s");c.p(23,42,"L")
  c.r(16,44,19,4,"o");c.r(17,44,17,1,"m");c.r(26,44,3,2,"A");c.p(27,44,"W")
  if kind=="cyber":
   c.poly([(11,29),(17,28),(18,32),(15,35),(15,39),(7,38),(7,33)],"O")
   c.poly([(31,28),(36,30),(40,36),(34,39),(32,33)],"O")
   c.line(10,31,14,30,"B");c.line(16,31,14,35,"A");c.r(8,35,3,2,"A");c.p(9,35,"a");c.line(35,32,37,35,"B")
  elif kind=="rogue":
   c.line(16,28,20,35,"o");c.line(32,29,28,36,"o");c.line(17,29,21,34,"m");c.line(29,38,32,41,"m")
   c.poly([(8,28),(16,27),(21,31),(16,34),(6,34)],"O");c.line(9,29,14,29,"B");c.r(10,39,3,6,"o");c.r(36,39,3,6,"o")
  else:
   c.poly([(29,27),(34,28),(38,32),(39,41),(35,47),(31,43)],"O");c.line(31,29,36,34,"A");c.r(20,32,3,2,"a");c.p(21,31,"W");c.p(21,34,"W")
 elif kind=="armor":
  c.poly([(8,29),(15,28),(19,33),(17,37),(7,36),(5,33)],"m");c.line(8,30,14,30,"M");c.line(7,34,14,35,"B")
  c.poly([(32,28),(39,30),(42,35),(39,39),(32,36),(29,32)],"m");c.line(34,30,39,33,"M");c.r(35,39,6,6,"m");c.line(36,40,39,40,"A")
  c.poly([(18,31),(23,33),(26,33),(31,30),(31,40),(26,43),(19,40)],"O");c.line(20,34,24,37,"B");c.line(29,33,27,38,"A");c.r(17,44,17,3,"o");c.r(23,44,5,2,"A")
 elif kind=="druid":
  c.poly([(16,30),(20,30),(25,34),(30,29),(33,31),(32,38),(28,41),(20,39),(17,36)],"O")
  for x,y in [(17,32),(21,35),(28,32),(29,37)]: c.poly([(x,y),(x+4,y-2),(x+2,y+3)],"B");c.p(x+2,y,"A")
  c.r(20,41,10,3,"S");c.line(26,41,27,43,"s");c.r(17,44,18,3,"o");c.r(25,43,3,4,"A");c.p(26,44,"W")
  c.poly([(10,33),(12,32),(14,35),(11,38),(8,37)],"O");c.line(9,35,12,34,"B");c.r(35,36,3,3,"A")
 elif kind in ("pilot","ranger","corsair"):
  c.poly([(12,28),(19,29),(21,33),(19,47),(9,47),(7,35)],"O")
  c.poly([(30,29),(36,29),(40,35),(39,47),(30,47),(29,34)],"O")
  c.line(14,30,19,34,"B");c.line(30,31,33,35,"B");c.r(21,33,9,11,"o")
  c.line(20,33,21,43,"m");c.line(31,33,29,43,"m");c.r(20,37,4,2,"A");c.r(28,37,4,2,"A");c.r(17,44,17,2,"m")
  if kind=="ranger": c.line(18,30,32,44,"m");c.line(17,30,31,44,"A");c.r(11,37,5,5,"o");c.line(11,37,15,37,"B")
  if kind=="corsair": c.poly([(9,28),(15,27),(18,33),(14,37),(6,39)],"o");c.line(8,31,14,29,"A");c.r(22,33,7,3,"S");c.r(22,45,5,2,"A")
 elif kind=="techwear":
  c.poly([(18,29),(22,30),(28,30),(32,29),(35,37),(33,47),(17,47),(15,37)],"O")
  c.r(21,28,9,3,"o");c.line(20,32,19,42,"B");c.line(30,32,32,41,"A");c.r(22,34,7,4,"o");c.r(23,35,3,1,"A");c.r(16,44,19,2,"m");c.r(25,44,3,2,"A")
  c.r(9,39,5,7,"o");c.line(10,40,12,40,"A");c.r(36,39,5,7,"o")
 return c.outline()


def accessory(kind):
 c=Canvas()
 if kind=="visor": c.line(19,12,31,12,"m");c.r(21,11,10,2,"A");c.r(22,11,4,1,"a");c.r(32,12,2,4,"m")
 elif kind=="goggles":
  c.line(17,10,33,10,"m");c.r(19,9,5,4,"o");c.r(27,9,5,4,"o");c.r(20,10,3,2,"A");c.r(28,10,3,2,"A");c.p(20,10,"a");c.p(28,10,"a")
 elif kind=="antlers":
  for x,dx in [(16,-1),(33,1)]: c.line(x,10,x+dx*2,3,"m");c.line(x+dx,7,x+dx*5,5,"m");c.p(x+dx*2,3,"A");c.p(x+dx*5,4,"A")
 elif kind=="cat_ears":
  c.poly([(14,9),(14,3),(20,8)],"h");c.poly([(30,8),(35,3),(35,11)],"h");c.line(15,5,17,8,"A");c.line(33,6,32,8,"A")
 elif kind=="circlet": c.line(19,11,31,11,"m");c.poly([(24,10),(26,8),(28,10),(26,12)],"A");c.p(26,9,"W")
 elif kind=="veil": c.line(15,9,16,26,"O");c.line(34,9,34,28,"O");c.line(18,8,30,7,"A");c.p(28,8,"W")
 elif kind=="headwrap": c.poly([(16,9),(20,7),(30,8),(34,11),(32,13),(19,12)],"A");c.line(19,9,30,10,"a");c.line(34,12,37,23,"O")
 elif kind=="horns": c.poly([(17,10),(12,7),(12,2),(15,6),(20,7)],"m");c.poly([(30,8),(34,6),(36,2),(36,8),(33,12)],"m");c.p(13,3,"M");c.p(35,4,"M")
 elif kind=="antenna": c.line(17,15,14,10,"m");c.line(14,10,15,5,"m");c.r(14,4,3,2,"A");c.p(15,4,"a");c.r(33,16,3,4,"m");c.p(34,17,"A")
 elif kind=="ear_cuff": c.r(16,17,2,3,"m");c.p(16,18,"A");c.p(17,21,"W");c.r(33,17,2,2,"A")
 elif kind=="halo": c.line(18,3,30,3,"A");c.line(16,4,17,5,"A");c.line(31,4,33,5,"A");c.line(18,6,30,6,"a");c.p(24,2,"W")
 return c.outline()


def detail(kind):
 c=Canvas()
 if kind=="freckles":
  for p in [(21,20),(23,21),(29,20),(31,20)]:c.p(*p,"s")
 elif kind=="scar": c.line(29,14,27,20,"L");c.p(28,18,"d");c.p(29,15,"d")
 elif kind=="cyber_eye":c.r(27,16,5,3,"m");c.r(28,17,3,1,"A");c.p(29,17,"W");c.line(31,19,32,22,"m")
 elif kind=="rune":c.line(20,19,20,22,"A");c.p(21,20,"a");c.p(19,21,"a")
 elif kind=="stubble":
  for p in [(20,22),(21,23),(22,24),(24,25),(26,25),(28,24),(30,22)]:c.p(*p,"d")
 return c


COMP_COLORS = {
 "owl":("#BDB4D6","#78708F","#EAB96F"),"cat":("#655077","#3B314E","#E899BC"),
 "drone":("#A1B8CD","#53677E","#71DDE5"),"slime":("#93CB74","#56875E","#E9F2B2"),
 "bat":("#5D506B","#352D4A","#F39B9B"),"bunny":("#D9D7E8","#9B96B8","#9DDAEA"),
 "moth":("#A79AC9","#625F91","#F2CE96"),"hawk":("#AE8158","#665445","#F3CD7F"),
 "axolotl":("#DA9FAF","#985E83","#F7D2AE"),"imp":("#9584B5","#5C517C","#D8E6A3"),
 "wisp":("#95CFE0","#527DA6","#D7F3DC"),"mimic":("#9E7860","#604D52","#D8C395")
}

def companion(kind):
 c=Canvas(16)
 if kind=="owl":
  c.poly([(3,7),(3,3),(6,5),(9,5),(12,3),(12,10),(10,13),(5,13),(3,10)],"C")
  c.r(3,8,2,3,"c");c.r(11,8,2,3,"c");c.r(5,6,2,3,"W");c.r(9,6,2,3,"W");c.p(6,7,"D");c.p(9,7,"D");c.p(8,9,"A");c.r(5,13,2,1,"A");c.r(10,13,2,1,"A")
 elif kind=="cat":
  c.poly([(3,7),(3,3),(6,5),(10,5),(12,2),(13,8),(11,12),(5,12),(3,10)],"C")
  c.r(4,4,1,2,"A");c.r(11,4,1,2,"A");c.p(6,7,"A");c.p(10,7,"A");c.line(6,10,10,9,"D");c.p(7,10,"W");c.p(10,9,"W");c.line(12,11,14,9,"c");c.p(14,8,"C")
 elif kind=="drone":
  c.r(4,5,8,6,"C");c.r(5,6,6,3,"c");c.r(6,7,4,1,"A");c.p(7,6,"W");c.line(3,4,1,4,"m");c.line(12,4,14,4,"m");c.r(2,3,3,1,"A");c.r(11,3,3,1,"A");c.line(6,12,5,13,"m");c.line(10,12,11,13,"m")
 elif kind=="slime":
  c.poly([(2,12),(3,8),(6,6),(6,3),(8,5),(10,6),(13,8),(14,12),(12,13),(10,12),(7,13),(4,12)],"C")
  c.r(5,7,2,2,"W");c.p(6,8,"D");c.r(10,7,2,2,"W");c.p(10,8,"D");c.line(7,10,10,10,"c");c.p(4,9,"A");c.p(5,5,"A")
 elif kind=="bat":
  c.poly([(1,6),(4,8),(6,6),(5,3),(8,5),(10,3),(11,7),(14,5),(14,10),(11,9),(10,12),(6,12),(5,10),(2,11)],"C")
  c.p(7,6,"A");c.p(6,8,"A");c.p(9,8,"A");c.p(8,10,"W");c.line(2,8,4,9,"c");c.line(12,7,13,8,"c")
 elif kind=="bunny":
  c.e(8,10,4,3,"C");c.poly([(5,8),(4,2),(6,1),(7,7)],"C");c.poly([(9,7),(10,1),(12,2),(11,8)],"C")
  c.line(5,3,6,6,"A");c.line(11,3,10,6,"A");c.r(5,8,6,3,"c");c.p(6,9,"A");c.p(9,9,"A");c.p(8,11,"m");c.p(4,12,"W")
 elif kind=="moth":
  c.poly([(7,6),(4,3),(1,4),(2,9),(5,10),(3,13),(7,12)],"C");c.poly([(9,6),(12,3),(14,4),(13,9),(10,10),(12,13),(8,12)],"C")
  c.r(7,6,2,6,"c");c.r(7,5,2,2,"W");c.p(8,6,"D");c.p(3,6,"A");c.p(12,6,"A");c.p(5,11,"A");c.p(10,11,"A");c.p(6,3,"A");c.p(9,3,"A")
 elif kind=="hawk":
  c.poly([(4,8),(5,5),(8,3),(11,5),(12,9),(10,12),(6,13),(4,11)],"C")
  c.poly([(4,8),(2,6),(2,10),(4,12)],"c");c.poly([(11,8),(14,6),(13,12),(11,11)],"c")
  c.p(7,6,"W");c.p(10,6,"W");c.p(8,5,"A");c.p(8,8,"A");c.line(5,13,3,14,"A");c.line(10,13,12,14,"A")
 elif kind=="axolotl":
  c.e(8,9,4,4,"C");c.line(3,6,1,5,"A");c.line(3,8,1,8,"A");c.line(3,10,1,11,"A");c.line(12,6,14,5,"A");c.line(12,8,14,8,"A");c.line(12,10,14,11,"A")
  c.p(6,8,"D");c.p(10,8,"D");c.line(7,10,9,10,"c");c.p(5,13,"c");c.p(11,13,"c")
 elif kind=="imp":
  c.poly([(4,7),(3,3),(6,5),(10,5),(13,3),(12,8),(11,12),(5,12)],"C");c.r(6,7,2,2,"W");c.r(10,7,1,2,"W");c.p(7,8,"D");c.p(10,8,"D");c.line(7,10,10,10,"c");c.p(8,10,"W");c.p(4,4,"A");c.p(12,4,"A");c.line(12,12,14,11,"c")
 elif kind=="wisp":
  c.poly([(8,1),(9,5),(12,7),(13,10),(11,13),(8,14),(4,12),(3,9),(5,6),(5,3),(7,6)],"C")
  c.poly([(8,5),(10,8),(11,11),(8,13),(5,11),(6,8)],"A");c.p(6,9,"D");c.p(9,9,"D");c.p(8,11,"W");c.p(7,3,"W")
 else:
  c.r(3,6,10,7,"C");c.r(3,4,10,4,"C");c.r(4,7,8,3,"D");c.r(3,5,10,1,"A");c.p(5,7,"W");c.p(7,7,"W");c.p(10,7,"W");c.p(6,9,"W");c.p(9,9,"W");c.r(6,11,4,2,"A");c.p(4,12,"c");c.p(12,12,"c")
 return c.outline()


def atlas():
 return dict(version=2,size=48,companion_size=16,palettes=PALETTES,fixed=FIXED,
  skin_tones=SKIN_TONES,hair_tones=HAIR_TONES,
  bodies={k:body(k).rows() for k in ["man","woman"]},
  hair={k:dict(back=hair(k)[0].rows(),front=hair(k)[1].rows()) for k in HAIR},
  faces={k:face(k).rows() for k in FACES},outfits={k:outfit(k).rows() for k in OUTFITS},
  accessories={k:accessory(k).rows() for k in ACCESSORIES},details={k:detail(k).rows() for k in DETAILS},
  companions={k:companion(k).rows() for k in COMPANIONS},cast=CAST)


def compose(recipe, data=None):
 data=data or atlas()
 palette={**data['fixed'],**data['palettes'][recipe['palette']],
          **data['skin_tones'].get(recipe.get('skin','auto'),{}),
          **data['hair_tones'].get(recipe.get('hair_tone','auto'),{})}
 hair=data['hair'][recipe['hair']]
 layers=[hair['back'],data['bodies'][recipe['body']],data['outfits'][recipe['outfit']],
         data['faces'][recipe['face']],data['details'][recipe.get('detail','none')],hair['front'],data['accessories'][recipe['accessory']]]
 pixels=[[None]*48 for _ in range(48)]
 for layer in layers:
  for y,row in enumerate(layer):
   for x,symbol in enumerate(row):
    if symbol in palette: pixels[y][x]=palette[symbol]
 return pixels


def png(pixels, scale=1):
 height=len(pixels);width=len(pixels[0])
 def rgba(color):
  if not color:return bytes(4)
  return bytes.fromhex(color.removeprefix('#'))+b'\xff'
 raw=b''.join((b'\0'+b''.join(rgba(color)*scale for color in row))*scale for row in pixels)
 def chunk(kind,payload):return struct.pack('>I',len(payload))+kind+payload+struct.pack('>I',zlib.crc32(kind+payload)&0xffffffff)
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width*scale,height*scale,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b'')


# Portable UTF-8 FNV-1a / xorshift32. These fixtures are also consumed by native
# tests, so Swift and Kotlin must agree on field order, choices and overflow.
OUTFIT_POOLS = {
 "orion":["mage","battlemage","astral","veil"], "nova":["cyber","astral","corsair","techwear"],
 "forge":["armor","techwear","pilot","battlemage"], "sage":["druid","ranger","astral","mage"],
 "nyx":["rogue","techwear","cyber","corsair"], "astra":["pilot","techwear","armor","corsair"],
 "vesper":["veil","astral","mage","corsair"], "rook":["ranger","corsair","armor","techwear"]
}
def generated(archetype, seed):
 state=2166136261
 for byte in (archetype+"|"+seed).encode("utf-8"): state=((state^byte)*16777619)&0xffffffff
 state=state or 0xA341316C
 choices=dict(body=["man","woman"],palette=list(CAST),skin=list(SKIN_TONES),hair_tone=list(HAIR_TONES),
              hair=HAIR,face=FACES,outfit=OUTFIT_POOLS.get(archetype,OUTFITS),accessory=ACCESSORIES,detail=DETAILS)
 recipe={}
 for field,options in choices.items():
  state^=(state<<13)&0xffffffff;state^=state>>17;state^=(state<<5)&0xffffffff
  recipe[field]=options[state%len(options)]
 return recipe

def recipe_fixtures():
 import hashlib
 data=atlas();rows=[]
 for archetype,seed in [("orion","atelier-001"),("nova","atelier-001"),("forge","ship-it"),("nyx","æøå-星"),
                        ("vesper","the-wicked-edit"),("rook","a-map-and-a-detour")]:
  recipe=generated(archetype,seed)
  rgba=bytes(channel for row in compose(recipe,data) for color in row
             for channel in (list(bytes.fromhex(color[1:]))+[255] if color else [0,0,0,0]))
  rows.append(dict(archetype=archetype,seed=seed,recipe=recipe,rgba_sha256=hashlib.sha256(rgba).hexdigest()))
 return (json.dumps(rows,indent=2,ensure_ascii=False)+"\n").encode()


def artifacts():
 data=atlas();outputs={}
 fixture=recipe_fixtures()
 for name in ["tests/fixtures/character-recipes.json","ios/NativeTests/Fixtures/character-recipes.json",
              "android/app/src/test/resources/character-recipes.json","android/app/src/androidTest/assets/character-recipes.json"]:
  outputs[name]=fixture
 encoded=(json.dumps(data,separators=(',',':'))+'\n').encode()
 for path in ['ios/Resources/character_engine.json','android/app/src/main/assets/character_engine.json']:outputs[path]=encoded
 for name,recipe in CAST.items():outputs[f'brand/cast/{name}-engine.png']=png(compose(recipe,data),6)
 # Actual 16px creature sprites, shared byte-for-byte by the native clients.
 familiar_strip=[[] for _ in range(16)]
 for name in COMPANIONS:
  a,b,accent=COMP_COLORS[name];palette={**FIXED,'C':a,'c':b,'A':accent}
  pixels=[[palette.get(symbol) for symbol in row] for row in data['companions'][name]]
  for y,row in enumerate(pixels): familiar_strip[y].extend(row)
  raw=png(pixels)
  outputs[f'ios/Resources/Assets.xcassets/Comp_{name}.imageset/Comp_{name}.png']=raw
  outputs[f'ios/Resources/Assets.xcassets/Comp_{name}.imageset/Contents.json']=(json.dumps({'images':[{'filename':f'Comp_{name}.png','idiom':'universal'}],'info':{'author':'xcode','version':1}},indent=2)+'\n').encode()
  outputs[f'android/app/src/main/res/drawable-nodpi/comp_{name}.png']=raw
 outputs["site/public/pixel-familiars.png"]=png(familiar_strip)
 return outputs


def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
 outputs=artifacts();stale=[]
 for name,raw in outputs.items():
  path=ROOT/name
  if args.check:
   if not path.exists() or path.read_bytes()!=raw:stale.append(name)
  else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
 if stale:raise SystemExit('Stale character artifacts: '+', '.join(stale))
 print(('Verified' if args.check else 'Built')+f' {len(outputs)} character artifacts: 48px layers, 16px familiars')

if __name__=='__main__':main()
