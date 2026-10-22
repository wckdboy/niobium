#!/usr/bin/env python3
from PIL import Image, ImageDraw, ImageFilter
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
W,H=780,392
im=Image.new('RGB',(W,H),'#f7f3ff'); d=ImageDraw.Draw(im)
# sky bands with ordered dither
bands=[('#fbf8ff',0,80),('#f3edff',80,150),('#e9dfff',150,210),('#dccdfb',210,265),('#c9b4f4',265,320),('#b49be8',320,392)]
for c,a,b in bands:
    d.rectangle((0,a,W,b),fill=c)
# subtle horizontal haze
for y in range(0,H,4): d.rectangle((0,y,W,y+1),fill='#ffffff' if y<150 else '#cbb6f1')
# moon
for r,c in [(58,'#fdfbff'),(48,'#f5edff'),(36,'#efe3ff')]:
    d.ellipse((620-r,52-r,620+r,52+r),fill=c)
d.ellipse((590,26,610,46),fill='#e4d4ff')
# stars
for x,y in [(80,40),(150,70),(220,30),(340,58),(470,35),(540,82),(720,150)]:
    d.rectangle((x,y,x+2,y+2),fill='#ffffff')
# clouds, layered
def cloud(x,y,s,c1,c2):
    for dx,dy,w,h in [(0,0,24,4),(6,-4,20,5),(16,2,24,4),(4,4,16,4)]:
        d.rectangle((x+dx*s,y+dy*s,x+(dx+w)*s,y+(dy+h)*s),fill=c1)
    d.rectangle((x+8*s,y+8*s,x+28*s,y+9*s),fill=c2)
for x,y,s in [(40,88,2),(160,46,2),(300,105,2),(500,70,2),(650,120,1),(90,170,1)]:
    cloud(x,y,s,'#ffffff','#e9dfff')
# distant mountains
def mountain(points,c):
    d.polygon(points,fill=c)
mountain([(0,270),(90,190),(180,270),(280,205),(390,280),(0,392)],'#bea8e9')
mountain([(300,280),(430,210),(560,285),(660,230),(780,300),(780,392),(300,392)],'#a98cdf')
# floating islands
def island(x,y,w,h,top,rock,deep):
    d.ellipse((x,y,x+w,y+h//2),fill=top)
    d.polygon([(x+8,y+h//3),(x+w//2,y+h),(x+w-8,y+h//3)],fill=rock)
    d.polygon([(x+w//2,y+h//3),(x+w-8,y+h//3),(x+w//2,y+h)],fill=deep)
    for i in range(3):
        tx=x+12+i*(w-24)//2
        d.rectangle((tx,y-6,tx+3,y+2),fill='#6d4aa8')
        d.rectangle((tx-4,y-15,tx+7,y-5),fill='#8b6ed6')
        d.rectangle((tx-2,y-21,tx+5,y-13),fill='#b9a4ef')
island(30,205,150,60,'#e7d9ff','#b9a4ef','#9d83da')
island(590,190,170,64,'#eadfff','#c6b2f2','#a98cdf')
# waterfalls
for x,y,h in [(96,238,90),(666,222,100)]:
    for i in range(h):
        d.rectangle((x+(i%3),y+i,x+4+(i%3),y+i+1),fill='#f6f0ff' if i%6<3 else '#cdb8f5')
# castle
cx,cy=360,175
d.rectangle((cx,cy,cx+120,cy+150),fill='#b7a2ea')
d.rectangle((cx+8,cy+10,cx+112,cy+150),fill='#cbb8f4')
# towers
for tx,tw,th in [(cx-18,34,95),(cx+104,36,110),(cx+42,36,135)]:
    d.rectangle((tx,cy-th,tx+tw,cy+140),fill='#c4aef1')
    for i in range(3):
        d.rectangle((tx+4+i*10,cy-th-8,tx+9+i*10,cy-th+4),fill='#b7a2ea')
        d.rectangle((tx+5+i*10,cy-th-14,tx+8+i*10,cy-th-7),fill='#a78bdb')
# windows
for yy in range(cy-70,cy+110,22):
    for xx in (cx+18,cx+48,cx+82):
        d.rectangle((xx,yy,xx+9,yy+12),fill='#5c3f8f')
        d.rectangle((xx+2,yy+2,xx+7,yy+7),fill='#f7e9ff' if (xx+yy)%3 else '#c4b5fd')
# gate
d.rectangle((cx+48,cy+78,cx+72,cy+148),fill='#7b57b7')
d.ellipse((cx+48,cy+55,cx+72,cy+92),fill='#7b57b7')
d.rectangle((cx+52,cy+88,cx+68,cy+148),fill='#5c3f8f')
# bridge
d.rectangle((cx-70,cy+126,cx,cy+136),fill='#b7a2ea')
for x in range(cx-70,cx,12):
    d.rectangle((x,cy+136,x+4,cy+150),fill='#9d83da')
# foreground terrace
d.rectangle((0,330,W,392),fill='#a98cdf'); d.rectangle((0,330,W,340),fill='#c9b4f4')
d.rectangle((0,340,W,348),fill='#8b6ed6')
for x in range(0,W,26):
    d.rectangle((x,340,x+2,392),fill='#8b6ed6')
# lanterns
for x in (120,250,400,530,670):
    d.rectangle((x,306,x+3,340),fill='#6d4aa8')
    d.rectangle((x-5,296,x+8,310),fill='#f5d36a'); d.rectangle((x-2,298,x+5,306),fill='#fff3c4')
# plants
for x in (12,70,700,748):
    d.rectangle((x,338,x+3,392),fill='#6d4aa8')
    d.ellipse((x-12,330,x+14,360),fill='#8b6ed6'); d.ellipse((x-6,320,x+20,350),fill='#b9a4ef')
# portraits on terrace
def portrait(pid,x,bottom,scale=1.55):
    p=Image.open(ROOT/f'brand/portraits/portrait_{pid[1:]}.png').convert('RGBA')
    p=p.resize((int(96*scale),int(96*scale)),Image.NEAREST)
    shadow=Image.new('RGBA',p.size,(0,0,0,0)); sd=ImageDraw.Draw(shadow)
    sd.ellipse((8,p.height-16,p.width-8,p.height),fill=(80,50,130,70))
    im.paste(shadow,(x,bottom-p.height),shadow)
    im.paste(p,(x,bottom-p.height),p)
portrait('p01',120,342,1.35)
portrait('p02',320,348,1.55)
portrait('p13',470,342,1.35)
portrait('p20',590,340,1.25)
# vignette
im.save(ROOT/'brand/hero-pixels-v2.png')
im.resize((780,392),Image.NEAREST).save(ROOT/'ios/Resources/Assets.xcassets/HeroPixels.imageset/HeroPixels.png')
print('hero v2')
