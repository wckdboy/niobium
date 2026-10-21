from PIL import Image, ImageDraw
import os, math

ROOT='/Users/dearwolf/wckdboy/niobium'
S=32
CAST = [
    dict(id='orion', name='Orion', gender='man', skin='#E8B98F', skin2='#C98B62', hair='#D9D4F2', hair2='#9B91C9', eye='#F5D36A', outfit='#3B2A5E', outfit2='#7C3AED', style='hood', companion='owl', accent='#A78BFA'),
    dict(id='nova', name='Nova', gender='woman', skin='#B97850', skin2='#8D5435', hair='#C084FC', hair2='#7C3AED', eye='#5EEAD4', outfit='#17131F', outfit2='#A78BFA', style='cyber', companion='cat', accent='#F0ABFC'),
    dict(id='forge', name='Forge', gender='man', skin='#7A4A2E', skin2='#5C3520', hair='#201A22', hair2='#4A3D4F', eye='#FBBF24', outfit='#2B2620', outfit2='#F5C97A', style='armor', companion='drone', accent='#F5C97A'),
    dict(id='sage', name='Sage', gender='woman', skin='#8D5A3B', skin2='#6B3F28', hair='#5E7D4A', hair2='#3F5A33', eye='#A3E635', outfit='#2F3E2D', outfit2='#86EFAC', style='druid', companion='slime', accent='#5EEAD4'),
    dict(id='nyx', name='Nyx', gender='woman', skin='#D8B49C', skin2='#B07C5E', hair='#1A1620', hair2='#3B2F45', eye='#FB7185', outfit='#221320', outfit2='#E11D48', style='rogue', companion='bat', accent='#FB7185'),
    dict(id='astra', name='Astra', gender='man', skin='#C68B62', skin2='#96613D', hair='#F4F1FF', hair2='#B9B2D8', eye='#7DD3FC', outfit='#1B2A3A', outfit2='#38BDF8', style='pilot', companion='bunny', accent='#7DD3FC'),
    dict(id='vesper', name='Vesper', gender='woman', skin='#F0D0B6', skin2='#C79D7A', hair='#E5E7EB', hair2='#9CA3AF', eye='#C4B5FD', outfit='#14121A', outfit2='#E9D5FF', style='nun', companion='moth', accent='#C4B5FF'),
    dict(id='rook', name='Rook', gender='man', skin='#5E3B2A', skin2='#3F261A', hair='#2B211C', hair2='#57453A', eye='#F59E0B', outfit='#3A2E24', outfit2='#D6A45E', style='ranger', companion='hawk', accent='#D6A45E'),
]

def rgb(h):
    h=h.lstrip('#'); return tuple(int(h[i:i+2],16) for i in (0,2,4))

def rect(d, x,y,w,h,color): d.rectangle((x,y,x+w-1,y+h-1), fill=rgb(color) if isinstance(color,str) else color)

def px(d, x,y,color): d.point((x,y), fill=rgb(color) if isinstance(color,str) else color)

def outline(d, color): pass

def hair_back(d,c,style):
    h=c['hair']; h2=c['hair2']
    if style in ('hood','druid','nun'):
        rect(d,7,4,18,24,h2)
    elif style in ('cyber','rogue'):
        rect(d,6,5,20,26,h)
    elif style=='pilot':
        rect(d,8,3,16,10,h)
    else:
        rect(d,8,4,16,14,h)

def head(d,c):
    sk=c['skin']; s2=c['skin2']
    # neck
    rect(d,14,19,4,4,s2); rect(d,14,19,4,2,sk)
    # face silhouette 10x12
    for i in range(10):
        rect(d,11+i,7,1,10,sk)
    rect(d,10,9,12,7,sk)
    rect(d,11,7,10,2,sk)
    rect(d,11,17,10,2,sk)
    # jaw shading
    rect(d,12,18,8,1,s2)
    rect(d,10,9,1,6,s2); rect(d,21,9,1,6,s2)
    # ears
    rect(d,9,12,2,4,s2); rect(d,21,12,2,4,s2)

def hair_front(d,c,style):
    h=c['hair']; h2=c['hair2']
    if style=='hood':
        rect(d,7,4,18,4,h2); rect(d,7,4,3,18,h2); rect(d,22,4,3,18,h2)
        rect(d,10,6,12,2,h)
        rect(d,11,7,2,3,h); rect(d,19,7,2,3,h)
    elif style=='cyber':
        rect(d,9,4,14,4,h); rect(d,8,6,3,8,h2); rect(d,21,5,3,10,h)
        rect(d,12,7,8,2,h2)
        rect(d,23,8,2,8,h)
    elif style=='armor':
        rect(d,9,4,14,3,h); rect(d,8,5,3,6,h2); rect(d,21,5,3,5,h2)
        rect(d,12,7,8,2,h)
    elif style=='druid':
        rect(d,8,4,16,4,h); rect(d,7,6,3,14,h2); rect(d,22,6,3,14,h2)
        rect(d,12,7,8,2,h2)
        # antlers
        for x,y in [(7,2),(8,1),(9,2),(23,2),(24,1),(23,3),(6,5),(25,5)]: px(d,x,y,c['outfit2'])
    elif style=='rogue':
        rect(d,8,4,16,4,h); rect(d,7,5,3,12,h2); rect(d,22,5,3,12,h2)
        rect(d,12,7,8,3,h)
        # cat ears
        for i in range(3):
            rect(d,8+i,2+i,1,1,h); rect(d,23-i,2+i,1,1,h)
    elif style=='pilot':
        rect(d,9,4,14,4,h); rect(d,8,6,3,6,h2); rect(d,21,6,3,6,h2)
        rect(d,12,7,4,2,h2)
    elif style=='nun':
        rect(d,8,3,16,5,h); rect(d,7,5,3,14,h2); rect(d,22,5,3,14,h2)
        rect(d,12,7,8,2,h2)
        # veil
        rect(d,6,9,3,14,c['outfit']); rect(d,23,9,3,14,c['outfit'])
    elif style=='ranger':
        rect(d,8,3,16,5,h2); rect(d,7,5,18,3,h)
        rect(d,12,7,8,2,h)
        # headwrap tails
        rect(d,23,8,3,8,h2); rect(d,25,12,3,3,h2)

def face(d,c,style):
    eye=c['eye']; sk=c['skin']; s2=c['skin2']
    if style in ('cyber','pilot'):
        # visor
        rect(d,11,10,10,3,c['outfit2']); rect(d,11,10,10,1,(255,255,255))
        rect(d,12,11,3,1,eye); rect(d,18,11,3,1,eye)
    else:
        rect(d,13,11,2,2,eye); rect(d,18,11,2,2,eye)
        px(d,13,11,(255,255,255)); px(d,18,11,(255,255,255))
    # brows
    rect(d,12,9,3,1,c['hair2']); rect(d,18,9,3,1,c['hair2'])
    # nose + mouth
    px(d,16,14,s2); px(d,16,15,s2)
    rect(d,14,16,4,1,c['skin2'])
    # cheek accents
    if style in ('cyber','rogue'):
        px(d,12,15,c['outfit2']); px(d,20,15,c['outfit2'])

def body(d,c,style):
    sk=c['skin']; s2=c['skin2']; o=c['outfit']; o2=c['outfit2']
    # shoulders
    rect(d,5,23,22,9,o)
    rect(d,4,26,24,6,o)
    # neck/chest skin
    rect(d,13,21,6,8,sk); rect(d,15,22,2,7,s2)
    # outfit necklines
    if style in ('hood','druid','nun'):
        rect(d,10,23,5,9,o2); rect(d,17,23,5,9,o2)
        rect(d,14,23,4,5,sk)
    elif style in ('cyber','rogue'):
        rect(d,8,23,7,9,o); rect(d,17,23,7,9,o)
        rect(d,14,23,4,6,sk)
        rect(d,11,27,3,1,o2); rect(d,18,27,3,1,o2)
        # zipper/straps
        rect(d,15,23,2,8,o2)
    elif style=='armor':
        rect(d,8,23,7,9,o2); rect(d,17,23,7,9,o2)
        rect(d,14,23,4,7,sk)
        rect(d,9,24,5,2,o); rect(d,18,24,5,2,o)
    elif style=='pilot':
        rect(d,8,23,8,9,o); rect(d,16,23,8,9,o)
        rect(d,14,23,4,8,sk)
        rect(d,10,24,2,7,o2); rect(d,20,24,2,7,o2)
    elif style=='ranger':
        rect(d,7,23,9,9,o); rect(d,16,23,9,9,o)
        rect(d,14,23,4,7,sk)
        rect(d,9,26,5,1,o2); rect(d,18,26,5,1,o2)
    # arms
    rect(d,3,27,4,6,s2); rect(d,25,27,4,6,s2)
    # shoulder highlights
    rect(d,5,23,22,1,o2)
    # belt/necklace
    if style in ('cyber','rogue'):
        rect(d,8,30,16,1,o2)
    if style in ('druid','nun'):
        rect(d,14,26,4,1,o2)

def companion(kind):
    im=Image.new('RGBA',(16,16),(0,0,0,0)); d=ImageDraw.Draw(im)
    if kind=='owl':
        body=(139,92,246); light=(196,181,253); dark=(39,24,64); beak=(245,158,11)
        rect(d,3,5,10,8,body); rect(d,2,4,3,3,body); rect(d,11,4,3,3,body)
        rect(d,5,7,2,2,(255,255,255)); rect(d,9,7,2,2,(255,255,255)); rect(d,6,7,1,1,dark); rect(d,10,7,1,1,dark)
        rect(d,7,9,2,1,beak); rect(d,4,12,2,2,light); rect(d,10,12,2,2,light)
    elif kind=='cat':
        body=(167,139,250); light=(233,213,255); dark=(30,20,40); eye=(94,234,212)
        rect(d,3,6,10,8,body); rect(d,4,3,3,4,body); rect(d,9,3,3,4,body)
        rect(d,5,8,2,2,eye); rect(d,9,8,2,2,eye); rect(d,6,8,1,1,dark); rect(d,10,8,1,1,dark)
        rect(d,7,10,2,1,dark); rect(d,12,10,3,2,body); rect(d,3,12,2,2,light)
    elif kind=='drone':
        body=(245,201,122); dark=(38,30,24); light=(255,255,255); eye=(251,113,133)
        rect(d,4,6,8,6,body); rect(d,2,4,3,2,dark); rect(d,11,4,3,2,dark); rect(d,5,8,6,2,dark)
        rect(d,6,8,1,1,eye); rect(d,9,8,1,1,eye); rect(d,7,12,2,1,body)
    elif kind=='slime':
        body=(94,234,212); dark=(21,70,64)
        rect(d,4,7,8,6,body); rect(d,3,9,10,4,body); rect(d,5,6,6,2,body)
        rect(d,6,9,1,2,dark); rect(d,9,9,1,2,dark); rect(d,7,12,2,1,dark)
    elif kind=='bat':
        body=(251,113,133); dark=(30,20,40); light=(253,164,175)
        rect(d,4,6,8,7,body); rect(d,1,7,3,4,body); rect(d,12,7,3,4,body)
        rect(d,5,8,2,2,(255,255,255)); rect(d,9,8,2,2,(255,255,255)); rect(d,6,8,1,1,dark); rect(d,10,8,1,1,dark)
        rect(d,5,12,1,2,light); rect(d,10,12,1,2,light)
    elif kind=='bunny':
        body=(125,211,252); light=(224,242,254); dark=(30,40,60)
        rect(d,4,7,8,7,body); rect(d,4,2,2,6,body); rect(d,10,2,2,6,body)
        rect(d,5,9,2,2,dark); rect(d,9,9,2,2,dark); rect(d,7,11,2,1,dark); rect(d,3,12,2,2,light)
    elif kind=='moth':
        body=(196,181,253); light=(245,243,255); dark=(40,30,50)
        rect(d,6,5,4,8,body); rect(d,1,6,5,5,body); rect(d,10,6,5,5,body)
        rect(d,6,7,4,2,light); rect(d,7,9,2,2,dark)
    elif kind=='hawk':
        body=(214,164,94); light=(245,222,179); dark=(40,30,20)
        rect(d,4,5,8,8,body); rect(d,2,6,3,5,body); rect(d,11,6,3,5,body)
        rect(d,6,8,2,2,light); rect(d,9,8,2,2,light); rect(d,7,8,1,1,dark); rect(d,10,8,1,1,dark)
        rect(d,7,11,2,2,(245,158,11))
    return im

def draw_character(c):
    im=Image.new('RGBA',(32,32),(0,0,0,0)); d=ImageDraw.Draw(im)
    hair_back(d,c,c['style'])
    body(d,c,c['style'])
    head(d,c)
    hair_front(d,c,c['style'])
    face(d,c,c['style'])
    # outline silhouette: darken edges (simple)
    # accessory
    if c['style']=='forge' or c['style']=='armor':
        rect(d,10,8,4,2,c['outfit2']); rect(d,18,8,4,2,c['outfit2'])
    return im

def save(img,name):
    img.save(f'{ROOT}/brand/cast/{name}.png')
    img.resize((256,256),Image.NEAREST).save(f'{ROOT}/brand/cast/{name}-256.png')

os.makedirs(f'{ROOT}/brand/cast',exist_ok=True)
for c in CAST:
    im=draw_character(c)
    save(im,c['id'])
    comp=companion(c['companion'])
    save(comp,c['companion']+'-'+c['id'])
print('cast written')
