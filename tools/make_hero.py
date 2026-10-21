from PIL import Image, ImageDraw
import random
W,H=360,150
im=Image.new('RGB',(W,H),'#f4f0fc'); d=ImageDraw.Draw(im)
# sky bands
bands=[('#f6f2ff',0,50),('#eee6ff',50,82),('#ded1fb',82,104),('#cdbdf6',104,124),('#b7a2ee',124,150)]
for c,a,b in bands: d.rectangle((0,a,W,b),fill=c)
# sun/moon
d.ellipse((260,24,300,64),fill='#fdfbff'); d.ellipse((268,26,296,58),fill='#f3ecff')
# clouds pixel blocks
def cloud(x,y,s=1,c='#ffffff'):
    for dx,dy,w,h in [(0,0,8,2),(2,-1,8,2),(5,1,8,2)]: d.rectangle((x+dx*s,y+dy*s,x+(dx+w)*s,y+(dy+h)*s),fill=c)
for x,y in [(20,18),(70,30),(120,12),(180,38),(240,20),(310,40)]: cloud(x,y)
# distant floating islands
def island(x,y,w,h,c1,c2):
    d.rectangle((x,y,x+w,y+h),fill=c1)
    for i in range(h):
        d.rectangle((x+i,y+h+i,x+w-i,y+h+i+1),fill=c2)
    # trees
    for i in range(3):
        tx=x+6+i*12
        d.rectangle((tx,y-4,tx+2,y),fill='#7c5cff')
        d.rectangle((tx-2,y-7,tx+4,y-4),fill='#a78bfa')
island(18,82,70,16,'#cdbdf6','#b7a2ee')
island(250,76,90,20,'#d9cdf8','#c2b1ef')
# castle on right
x,y=276,68
d.rectangle((x,y,x+38,y+42),fill='#b9a6e8')
for i in range(3):
    d.rectangle((x+4+i*12,y-10,x+9+i*12,y+2),fill='#b9a6e8')
    d.rectangle((x+5+i*12,y-14,x+8+i*12,y-8),fill='#a78bfa')
d.rectangle((x+14,y+12,x+24,y+42),fill='#8b74c9')
d.rectangle((x+16,y+18,x+22,y+26),fill='#f6f2ff')
for i in range(5): d.rectangle((x+2+i*7,y+44,x+5+i*7,y+50),fill='#a78bfa')
# foreground trees
for i in range(9):
    tx=10+i*40; ty=118+((i*7)%8)
    d.rectangle((tx,ty,tx+3,150),fill='#6d4aa8')
    d.rectangle((tx-4,ty-10,tx+7,ty+2),fill='#7c5cff')
    d.rectangle((tx-2,ty-14,tx+5,ty-6),fill='#a78bfa')
# birds
for x,y in [(120,64),(128,60),(136,65),(210,54)]:
    d.rectangle((x,y,x+3,y+1),fill='#8b74c9'); d.rectangle((x+1,y-1,x+2,y),fill='#8b74c9')
im.save('brand/hero-pixels.png')
im.resize((720,300),Image.NEAREST).save('brand/hero-pixels-2x.png')
print('hero')
