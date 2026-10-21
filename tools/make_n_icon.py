from PIL import Image, ImageDraw
ROOT='/Users/dearwolf/wckdboy/niobium'
GRID=16; CELL=64; SIZE=GRID*CELL
rows=[
"................","................","....DDDDDD......","...DLLLLLLD.....","..DLLD..DLLD....","..DLD....DLD....","..DLD....DLD....","..DLD....DLD....","..DLD....DLD....","..DLD....DLD....","..DLD....DLD....","..DDD....DDD....","................","................","...........MM...","..........MMMM.."]
colors={'D':(76,29,149),'M':(124,58,237),'L':(167,139,250),'m':(94,234,212)}
img=Image.new('RGB',(SIZE,SIZE),'white'); d=ImageDraw.Draw(img)
for y,row in enumerate(rows):
    for x,ch in enumerate(row):
        if ch in colors:
            d.rectangle((x*CELL,y*CELL,x*CELL+CELL-1,y*CELL+CELL-1),fill=colors[ch])
# one white facet inside the left stem
for x,y in [(3,4),(3,5)]: d.rectangle((x*CELL,y*CELL,x*CELL+CELL-1,y*CELL+CELL-1),fill=(255,255,255))
img.save(f'{ROOT}/brand/app-icon-n-1024.png')
img.resize((512,512),Image.NEAREST).save(f'{ROOT}/brand/n-512.png')
# transparent mark for UI
tr=img.convert('RGBA'); px=tr.load()
for yy in range(SIZE):
    for xx in range(SIZE):
        r,g,b,a=px[xx,yy]
        if r>248 and g>248 and b>248: px[xx,yy]=(255,255,255,0)
tr.save(f'{ROOT}/brand/n-mark.png')
# rounded
rounded=img.convert('RGBA'); mask=Image.new('L',(SIZE,SIZE),0)
ImageDraw.Draw(mask).rounded_rectangle((0,0,SIZE,SIZE),radius=232,fill=255)
rounded.putalpha(mask); rounded.save(f'{ROOT}/brand/app-icon-n-rounded.png')
# iOS icon
img.save(f'{ROOT}/ios/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png')
# Android adaptive foreground
fg=Image.new('RGBA',(512,512),(0,0,0,0)); ore=img.resize((340,340),Image.NEAREST).convert('RGBA'); p=ore.load()
for yy in range(ore.height):
    for xx in range(ore.width):
        r,g,b,a=p[xx,yy]
        if r>248 and g>248 and b>248: p[xx,yy]=(255,255,255,0)
fg.alpha_composite(ore,(86,86))
for density,size in [('mdpi',108),('hdpi',162),('xhdpi',216),('xxhdpi',324),('xxxhdpi',432)]:
    fg.resize((size,size),Image.LANCZOS).save(f'{ROOT}/android/app/src/main/res/mipmap-{density}/ic_launcher_foreground.png')
# monochrome
small=img.resize((286,286),Image.NEAREST).convert('RGB'); m=Image.new('L',(286,286),0); sp=small.load(); mp=m.load()
for yy in range(286):
    for xx in range(286):
        r,g,b=sp[xx,yy]
        if r<248 or g<248 or b<248: mp[xx,yy]=255
white=Image.new('RGBA',(286,286),(255,255,255,255)); white.putalpha(m)
canvas=Image.new('RGBA',(432,432),(255,255,255,0)); canvas.alpha_composite(white,(73,73))
canvas.save(f'{ROOT}/android/app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png')
# launch mark, transparent
for size,name in [(160,'LaunchMark.png'),(320,'LaunchMark@2x.png'),(480,'LaunchMark@3x.png')]:
    tr.resize((size,size),Image.NEAREST).save(f'{ROOT}/ios/Resources/Assets.xcassets/LaunchMark.imageset/{name}')
print('n icon written')
