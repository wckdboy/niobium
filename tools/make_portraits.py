#!/usr/bin/env python3
from pathlib import Path
from PIL import Image
import json, shutil
ROOT=Path(__file__).resolve().parent.parent
RAW=ROOT/'brand/portraits/raw'; OUT=ROOT/'brand/portraits'
CELL=96
meta=[]
for sheet in (1,2,3):
    im=Image.open(RAW/f'Cyber_Faces_{sheet:03d}.png').convert('RGBA')
    for row in range(2):
        for col in range(4):
            idx=(sheet-1)*8+row*4+col+1
            cell=im.crop((col*CELL,row*CELL,(col+1)*CELL,(row+1)*CELL))
            cell.save(OUT/f'portrait_{idx:02d}.png')
            meta.append({'id':f'p{idx:02d}','sheet':sheet,'row':row,'col':col,'file':f'portrait_{idx:02d}.png'})
(OUT/'catalog.json').write_text(json.dumps({'cell':CELL,'portraits':meta},indent=2))
# iOS asset catalog + Android drawables
ios=ROOT/'ios/Resources/Assets.xcassets'; android=ROOT/'android/app/src/main/res/drawable-nodpi'
for item in meta:
    pid=item['id']; src=OUT/item['file']
    d=ios/f'Portrait_{pid}.imageset'; d.mkdir(parents=True,exist_ok=True)
    shutil.copy(src,d/f'Portrait_{pid}.png')
    (d/'Contents.json').write_text(json.dumps({'images':[{'filename':f'Portrait_{pid}.png','idiom':'universal','scale':'1x'}],'info':{'author':'xcode','version':1}},indent=2))
    shutil.copy(src,android/f'portrait_{pid}.png')
print('portraits',len(meta))
