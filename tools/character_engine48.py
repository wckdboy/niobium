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

from pixel_canvas import Canvas
from portrait_layers48 import body, hair, face, outfit, accessory, detail

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
 return dict(version=3,size=48,companion_size=16,palettes=PALETTES,fixed=FIXED,
  skin_tones=SKIN_TONES,hair_tones=HAIR_TONES,
  bodies={k:body(k).rows() for k in ["man","woman"]},
  hair={k:dict(back=hair(k)[0].rows(),front=hair(k)[1].rows()) for k in HAIR},
  faces={k:face(k).rows() for k in FACES},blink_faces={k:face(k,blink=True).rows() for k in FACES},
  outfits={k:{build:outfit(k,build).rows() for build in ["man","woman"]} for k in OUTFITS},
  accessories={k:accessory(k).rows() for k in ACCESSORIES},details={k:detail(k).rows() for k in DETAILS},
  companions={k:companion(k).rows() for k in COMPANIONS},cast=CAST)


def compose(recipe, data=None, blink=False):
 data=data or atlas()
 palette={**data['fixed'],**data['palettes'][recipe['palette']],
          **data['skin_tones'].get(recipe.get('skin','auto'),{}),
          **data['hair_tones'].get(recipe.get('hair_tone','auto'),{})}
 hair=data['hair'][recipe['hair']]
 layers=[hair['back'],data['bodies'][recipe['body']],data['outfits'][recipe['outfit']][recipe['body']],
         data['blink_faces' if blink else 'faces'][recipe['face']],data['details'][recipe.get('detail','none')],hair['front'],data['accessories'][recipe['accessory']]]
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
  closed=bytes(channel for row in compose(recipe,data,blink=True) for color in row
             for channel in (list(bytes.fromhex(color[1:]))+[255] if color else [0,0,0,0]))
  rows.append(dict(archetype=archetype,seed=seed,recipe=recipe,rgba_sha256=hashlib.sha256(rgba).hexdigest(),
                   blink_rgba_sha256=hashlib.sha256(closed).hexdigest()))
 return (json.dumps(rows,indent=2,ensure_ascii=False)+"\n").encode()


def contact_sheet(recipes, data, columns=4):
 rows=(len(recipes)+columns-1)//columns
 sheet=[[None]*(columns*48) for _ in range(rows*48)]
 for index,recipe in enumerate(recipes):
  for y,row in enumerate(compose(recipe,data)):
   sheet[index//columns*48+y][index%columns*48:(index%columns+1)*48]=row
 return sheet


def artifacts():
 data=atlas();outputs={}
 fixture=recipe_fixtures()
 for name in ["tests/fixtures/character-recipes.json","ios/NativeTests/Fixtures/character-recipes.json",
              "android/app/src/test/resources/character-recipes.json","android/app/src/androidTest/assets/character-recipes.json"]:
  outputs[name]=fixture
 encoded=(json.dumps(data,separators=(',',':'))+'\n').encode()
 for path in ['ios/Resources/character_engine.json','android/app/src/main/assets/character_engine.json']:outputs[path]=encoded
 for name,recipe in CAST.items():outputs[f'brand/cast/{name}-engine.png']=png(compose(recipe,data),6)
 # This sheet shows the actual custom generator, never the stock portrait path.
 examples=[{**CAST[key],"skin":skin,"hair_tone":tone,"hair":style,"detail":detail}
           for key,skin,tone,style,detail in [
            ("nova","copper","violet","high_ponytail","cyber_eye"),
            ("orion","sand","silver","silver_swept","scar"),
            ("sage","ebony","silver","locs","freckles"),
            ("forge","umber","copper","mohawk","stubble"),
            ("nyx","porcelain","ink","long_waves","none"),
            ("astra","copper","ice","undercut","none"),
            ("vesper","copper","rose","silver_bob","rune"),
            ("rook","ebony","moss","undercut","stubble")]]
 examples[6].update(outfit="astral",accessory="circlet")
 sheet=contact_sheet(examples,data)
 outputs["site/public/pixel-custom-atlas.png"]=png(sheet)
 outputs["brand/pixel-studio-v3-gallery.png"]=png(sheet,5)
 checks=[{**CAST["nova"],"body":build,"outfit":look,"hair":HAIR[index],"accessory":ACCESSORIES[index]}
         for build in ["woman","man"] for index,look in enumerate(OUTFITS)]
 outputs["brand/pixel-studio-v3-cuts.png"]=png(contact_sheet(checks,data,6),4)
 outputs["brand/pixel-studio-v3-recipes.json"]=(json.dumps(dict(examples=examples,cuts=checks),indent=2)+"\n").encode()
 open_frame=compose(examples[0],data);closed_frame=compose(examples[0],data,blink=True)
 outputs["brand/pixel-studio-v3-blink.png"]=png([a+b for a,b in zip(open_frame,closed_frame)],6)
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
