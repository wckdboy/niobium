#!/usr/bin/env python3
"""Niobium 48x48 layered pixel-art character engine."""
from __future__ import annotations
import json, pathlib
from PIL import Image, ImageDraw
ROOT=pathlib.Path(__file__).resolve().parent.parent
S=48
T='.'

class C:
    def __init__(self): self.g=[[T]*S for _ in range(S)]
    def p(self,x,y,k):
        if 0<=x<S and 0<=y<S: self.g[y][x]=k
    def r(self,x,y,w,h,k):
        for yy in range(y,y+h):
            for xx in range(x,x+w): self.p(xx,yy,k)
    def e(self,cx,cy,rx,ry,k):
        for y in range(cy-ry,cy+ry+1):
            for x in range(cx-rx,cx+rx+1):
                if ((x-cx)/max(rx,1))**2+((y-cy)/max(ry,1))**2<=1.03: self.p(x,y,k)
    def line(self,x0,y0,x1,y1,k):
        dx=abs(x1-x0); sx=1 if x0<x1 else -1; dy=-abs(y1-y0); sy=1 if y0<y1 else -1; err=dx+dy
        while True:
            self.p(x0,y0,k)
            if x0==x1 and y0==y1: break
            e2=2*err
            if e2>=dy: err+=dy; x0+=sx
            if e2<=dx: err+=dx; y0+=sy
    def outline(self,k='K'):
        add=[]
        for y in range(S):
            for x in range(S):
                if self.g[y][x]!=T: continue
                for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
                    nx,ny=x+dx,y+dy
                    if 0<=nx<S and 0<=ny<S and self.g[ny][nx] not in (T,k): add.append((x,y)); break
        for x,y in add:self.p(x,y,k)
    def rows(self):return [''.join(r) for r in self.g]

def body(gender):
    c=C(); w=11 if gender=='man' else 9
    # shoulders/chest base is a dark bodysuit; outfits layer over it.
    c.e(24,37,w+7,13,'O')
    c.r(8,38,32,10,'O')
    # neck
    c.r(20,27,8,12,'S'); c.r(21,28,5,9,'s')
    # upper chest / collarbone only, no broad skin field
    c.r(20,33,8,7,'S'); c.r(21,34,2,5,'s'); c.r(27,34,2,5,'s')
    # head oval + jaw
    c.e(24,17,9,11,'S')
    c.r(15,17,18,8,'S'); c.r(17,25,14,3,'S')
    c.r(13,16,2,6,'s'); c.r(33,16,2,6,'s')
    # jaw/chin shadow
    c.r(18,25,12,1,'s'); c.r(20,26,8,1,'s')
    # ears
    c.r(13,16,2,5,'S'); c.r(33,16,2,5,'S')
    # collarbones, shoulder shading
    c.r(10,44,28,4,'o'); c.line(17,36,20,39,'o'); c.line(31,36,28,39,'o')
    c.outline(); return c

def hair(style):
    b=C(); f=C()
    if style=='silver_swept':
        b.e(24,15,11,12,'H'); b.r(12,14,3,17,'h'); b.r(34,12,3,18,'H')
        b.r(14,4,21,6,'H'); b.r(15,3,13,3,'H')
        f.r(13,7,23,7,'H'); f.r(14,12,7,3,'H'); f.r(28,9,8,2,'G')
        f.r(20,5,4,1,'G'); f.r(12,12,2,9,'h')
    elif style=='violet_bob':
        b.e(24,16,12,13,'H'); b.r(11,15,4,20,'h'); b.r(34,15,4,20,'H')
        b.r(13,5,23,7,'H')
        f.r(12,7,24,10,'H'); f.r(14,1,17,2,'h') if False else None
        f.r(13,14,6,4,'h'); f.r(31,10,5,4,'G'); f.r(11,18,2,12,'H'); f.r(36,18,2,12,'h')
    elif style=='undercut':
        b.e(24,14,10,8,'H'); b.r(13,7,20,6,'h'); b.r(11,10,3,8,'h')
        f.r(14,6,20,5,'H'); f.r(17,10,11,3,'G'); f.r(11,11,3,5,'h')
    elif style=='locs':
        b.e(24,16,12,13,'H'); b.r(11,12,4,28,'h'); b.r(34,12,4,28,'H')
        for x in (10,13,36,39): b.r(x,15,2,22,'H')
        b.r(12,5,25,6,'H')
        f.r(12,7,24,8,'H'); f.r(15,13,7,3,'h'); f.r(24,7,7,2,'G')
    elif style=='cat_black':
        b.e(24,16,12,13,'H'); b.r(11,14,4,22,'h'); b.r(34,14,4,22,'H')
        b.r(12,4,24,7,'H')
        f.r(12,7,24,8,'H'); f.r(15,13,8,3,'h')
        # ears
        for i in range(5): b.p(14+i,8-i,'H'); b.p(34-i,8-i,'H')
    elif style=='white_pilot':
        b.e(24,15,10,9,'H'); b.r(12,10,3,14,'h'); b.r(34,10,3,12,'H'); b.r(13,4,22,7,'H')
        f.r(13,6,22,6,'H'); f.r(17,11,8,3,'G'); f.r(26,10,8,3,'H')
    elif style=='silver_bob':
        b.e(24,17,12,12,'H'); b.r(11,15,4,17,'h'); b.r(34,15,4,17,'h'); b.r(12,5,24,7,'H')
        f.r(12,7,24,8,'H'); f.r(14,13,7,3,'h'); f.r(29,10,6,4,'G'); f.r(10,17,3,11,'H')
    elif style=='headwrap':
        b.r(11,12,26,16,'h'); b.r(12,10,24,6,'H'); b.r(10,17,4,14,'H'); b.r(35,17,4,14,'h')
        f.r(12,7,24,7,'H'); f.r(14,12,20,3,'G'); f.r(36,14,4,16,'h')
    for c in (b,f):c.outline()
    return b,f

def face(kind):
    c=C()
    if kind=='soft':
        c.r(18,15,5,1,'h'); c.r(27,15,5,1,'h')
        c.r(18,18,4,2,'W'); c.r(27,18,4,2,'W')
        c.r(19,18,2,2,'E'); c.r(28,18,2,2,'E'); c.p(19,18,'D'); c.p(28,18,'D')
    elif kind=='sharp':
        c.r(17,14,6,1,'h'); c.r(27,14,6,1,'h')
        c.r(18,18,4,2,'E'); c.r(27,18,4,2,'E'); c.p(19,18,'D'); c.p(28,18,'D')
    elif kind=='calm':
        c.r(18,15,5,1,'h'); c.r(27,15,5,1,'h')
        c.r(18,18,4,2,'W'); c.r(27,18,4,2,'W'); c.r(19,18,2,2,'E'); c.r(28,18,2,2,'E')
    elif kind=='fierce':
        c.r(17,14,6,1,'h'); c.r(27,14,6,1,'h')
        c.r(18,17,4,2,'W'); c.r(27,17,4,2,'W'); c.r(19,17,2,2,'E'); c.r(28,17,2,2,'E'); c.p(20,18,'D'); c.p(29,18,'D')
    # nose + lips
    c.p(24,21,'s'); c.r(22,23,5,1,'a'); c.p(23,24,'o'); c.p(25,24,'o')
    c.outline(); return c

def outfit(style):
    c=C()
    if style=='mage':
        c.r(8,33,10,15,'O'); c.r(30,33,10,15,'O'); c.r(11,33,4,12,'o'); c.r(34,33,4,12,'o')
        c.r(14,33,4,3,'A'); c.r(30,33,4,3,'A'); c.r(22,34,4,14,'A'); c.r(18,35,12,2,'o')
    elif style=='cyber':
        c.r(7,34,13,14,'O'); c.r(28,34,13,14,'O'); c.r(7,34,3,14,'o'); c.r(38,34,3,14,'o')
        c.r(18,33,12,2,'A'); c.r(19,35,10,2,'S'); c.r(15,37,4,11,'A'); c.r(30,37,4,11,'A'); c.r(23,35,2,8,'A')
    elif style=='armor':
        c.r(7,32,14,16,'O'); c.r(27,32,14,16,'O'); c.r(7,32,5,3,'A'); c.r(36,32,5,3,'A')
        c.r(11,36,8,4,'o'); c.r(29,36,8,4,'o'); c.r(20,33,8,2,'A'); c.r(23,36,2,12,'A')
    elif style=='druid':
        c.r(8,33,11,15,'O'); c.r(29,33,11,15,'O'); c.r(11,33,4,14,'o'); c.r(33,33,4,14,'o')
        c.r(15,33,6,3,'A'); c.r(27,33,6,3,'A'); c.r(22,36,4,12,'O'); c.r(23,45,2,2,'A')
    elif style=='rogue':
        c.r(8,36,12,12,'O'); c.r(28,36,12,12,'O'); c.r(13,32,3,12,'A'); c.r(32,32,3,12,'A')
        c.r(18,38,12,2,'A'); c.r(21,39,6,2,'O'); c.r(16,34,2,8,'O'); c.r(30,34,2,8,'O')
    elif style=='pilot':
        c.r(7,33,14,15,'O'); c.r(27,33,14,15,'O'); c.r(9,33,3,15,'o'); c.r(37,33,3,15,'o')
        c.r(17,33,5,3,'S'); c.r(27,33,5,3,'S'); c.r(23,35,2,13,'A'); c.r(18,38,5,2,'A'); c.r(26,38,5,2,'A')
    elif style=='veil':
        c.r(7,33,34,15,'O'); c.r(10,33,28,3,'o'); c.r(18,33,12,3,'S'); c.r(21,36,6,2,'A')
        c.r(6,33,4,15,'O'); c.r(38,33,4,15,'O'); c.r(22,38,4,10,'o')
    elif style=='ranger':
        c.r(7,33,14,15,'O'); c.r(27,33,14,15,'O'); c.r(11,33,7,3,'A'); c.r(30,33,7,3,'A')
        c.r(8,36,3,11,'o'); c.r(37,36,3,11,'o'); c.r(20,36,8,12,'S'); c.r(17,40,3,2,'A'); c.r(28,40,3,2,'A')
    c.outline(); return c

def accessory(kind):
    c=C()
    if kind=='visor':
        c.r(16,12,17,1,'A'); c.r(18,10,4,2,'W'); c.r(27,10,4,2,'W')
    elif kind=='goggles':
        c.r(15,12,8,4,'m'); c.r(26,12,8,4,'m'); c.r(17,13,4,2,'A'); c.r(28,13,4,2,'A')
    elif kind=='antlers':
        c.r(12,3,2,9,'m'); c.r(10,2,2,3,'A'); c.r(34,3,2,9,'m'); c.r(36,2,2,3,'A')
    elif kind=='cat_ears':
        c.r(12,4,4,7,'h'); c.r(32,4,4,7,'h'); c.r(14,6,2,3,'A'); c.r(34,6,2,3,'A')
    elif kind=='circlet':
        c.r(16,10,16,1,'A'); c.r(23,8,2,2,'W')
    elif kind=='veil':
        c.r(10,5,3,18,'O'); c.r(35,5,3,18,'O'); c.r(17,6,14,1,'A')
    elif kind=='headwrap':
        c.r(12,8,24,4,'A'); c.r(34,11,5,12,'O')
    return c

def companion(kind):
    c=C()
    if kind=='owl':
        c.e(14,15,8,7,'C'); c.r(6,8,5,5,'C'); c.r(19,8,5,5,'C'); c.r(10,13,2,2,'W'); c.r(17,13,2,2,'W'); c.r(11,13,1,1,'D'); c.r(18,13,1,1,'D'); c.r(13,18,3,2,'A'); c.r(7,22,3,2,'c'); c.r(19,22,3,2,'c')
    elif kind=='cat':
        c.e(14,17,8,7,'C'); c.r(7,7,5,7,'C'); c.r(17,7,5,7,'C'); c.r(10,14,2,2,'E'); c.r(17,14,2,2,'E'); c.r(11,14,1,1,'D'); c.r(18,14,1,1,'D'); c.r(13,18,3,1,'D'); c.r(21,17,5,3,'C')
    elif kind=='drone':
        c.r(7,11,16,9,'C'); c.r(3,8,5,3,'A'); c.r(22,8,5,3,'A'); c.r(9,14,12,3,'D'); c.r(10,14,2,2,'E'); c.r(17,14,2,2,'E'); c.r(13,21,4,2,'c')
    elif kind=='slime':
        c.e(14,19,10,7,'C'); c.e(14,15,7,6,'C'); c.r(10,17,2,3,'D'); c.r(17,17,2,3,'D'); c.r(13,22,3,1,'A')
    elif kind=='bat':
        c.e(14,17,7,7,'C'); c.r(3,15,8,5,'C'); c.r(17,15,8,5,'C'); c.r(10,14,2,3,'W'); c.r(17,14,2,3,'W'); c.r(10,15,1,1,'D'); c.r(18,15,1,1,'D'); c.r(9,22,2,3,'c'); c.r(18,22,2,3,'c')
    elif kind=='bunny':
        c.e(14,19,8,7,'C'); c.r(9,4,4,11,'C'); c.r(17,4,4,11,'C'); c.r(11,17,2,2,'D'); c.r(17,17,2,2,'D'); c.r(13,21,3,1,'A')
    elif kind=='moth':
        c.e(14,15,5,9,'C'); c.r(3,11,9,9,'A'); c.r(18,11,9,9,'A'); c.r(12,13,4,4,'W'); c.r(13,15,2,2,'D')
    elif kind=='hawk':
        c.e(14,15,8,7,'C'); c.r(3,13,7,6,'A'); c.r(19,13,7,6,'A'); c.r(11,13,1,1,'D'); c.r(18,13,1,1,'D'); c.r(13,18,3,2,'A')
    c.outline(); return c

PAL={
 'orion':dict(S='#E4B48E',s='#BD825F',H='#DCD6F7',h='#9288C4',G='#FFFFFF',E='#F5D36A',O='#3A2857',o='#211536',A='#A78BFA',a='#E9D5FF'),
 'nova':dict(S='#B97850',s='#8D5435',H='#C084FC',h='#7C3AED',G='#F5D0FE',E='#5EEAD4',O='#17131F',o='#08060C',A='#A78BFA',a='#F0ABFC'),
 'forge':dict(S='#7A4A2E',s='#5C3520',H='#201A22',h='#4A3D4F',G='#6B5B73',E='#FBBF24',O='#2B2620',o='#15120E',A='#F5C97A',a='#FDE68A'),
 'sage':dict(S='#8D5A3B',s='#6B3F28',H='#5E7D4A',h='#3F5A33',G='#A3E635',E='#A3E635',O='#2F3E2D',o='#182016',A='#86EFAC',a='#D9F99D'),
 'nyx':dict(S='#D8B49C',s='#B07C5E',H='#17131D',h='#3B2F45',G='#8B5CF6',E='#FB7185',O='#21111E',o='#0D060B',A='#E11D48',a='#FDA4AF'),
 'astra':dict(S='#C68B62',s='#96613D',H='#F4F1FF',h='#B9B2D8',G='#FFFFFF',E='#7DD3FC',O='#1B2A3A',o='#0B1219',A='#38BDF8',a='#BAE6FD'),
 'vesper':dict(S='#F0D0B6',s='#C79D7A',H='#E5E7EB',h='#9CA3AF',G='#FFFFFF',E='#C4B5FD',O='#14121A',o='#07060A',A='#E9D5FF',a='#F5F3FF'),
 'rook':dict(S='#5E3B2A',s='#3F261A',H='#2B211C',h='#57453A',G='#8B6A50',E='#F59E0B',O='#3A2E24',o='#1E1711',A='#D6A45E',a='#FDE68A')}
FIX={'K':'#160B22','W':'#FFFFFF','D':'#0D0714','M':'#D9D2E8','m':'#7A6B8E','C':'#A78BFA','c':'#6D4AC7'}

SPECS={
 'orion':dict(palette='orion',body='man',hair='silver_swept',face='calm',outfit='mage',accessory='circlet',companion='owl'),
 'nova':dict(palette='nova',body='woman',hair='violet_bob',face='soft',outfit='cyber',accessory='visor',companion='cat'),
 'forge':dict(palette='forge',body='man',hair='undercut',face='sharp',outfit='armor',accessory='goggles',companion='drone'),
 'sage':dict(palette='sage',body='woman',hair='locs',face='soft',outfit='druid',accessory='antlers',companion='slime'),
 'nyx':dict(palette='nyx',body='woman',hair='cat_black',face='fierce',outfit='rogue',accessory='cat_ears',companion='bat'),
 'astra':dict(palette='astra',body='man',hair='white_pilot',face='sharp',outfit='pilot',accessory='visor',companion='bunny'),
 'vesper':dict(palette='vesper',body='woman',hair='silver_bob',face='calm',outfit='veil',accessory='veil',companion='moth'),
 'rook':dict(palette='rook',body='man',hair='headwrap',face='calm',outfit='ranger',accessory='headwrap',companion='hawk')}

def compose(spec,layers):
    out=[[T]*S for _ in range(S)]
    for key in ('hair_back','body','outfit','face','bangs','accessory'):
        for y in range(S):
            for x in range(S):
                ch=layers[key][y][x]
                if ch!=T: out[y][x]=ch
    return out

def preview(spec,name):
    hb,hf=hair(spec['hair']); layers=dict(hair_back=hb.rows(),body=body(spec['body']).rows(),outfit=outfit(spec['outfit']).rows(),face=face(spec['face']).rows(),bangs=hf.rows(),accessory=accessory(spec['accessory']).rows())
    out=compose(spec,layers); pal={**FIX,**PAL[spec['palette']]}
    im=Image.new('RGBA',(S,S),(0,0,0,0)); px=im.load()
    for y in range(S):
        for x in range(S):
            ch=out[y][x]
            if ch in pal:
                h=pal[ch].lstrip('#'); px[x,y]=(int(h[0:2],16),int(h[2:4],16),int(h[4:6],16),255)
    im.resize((S*6,S*6),Image.NEAREST).save(ROOT/f'brand/cast/{name}-engine.png')
    return layers

data=dict(size=S,palettes=PAL,fixed=FIX,bodies={k:body(k).rows() for k in ('man','woman')},
          hair={k:{'back':hair(k)[0].rows(),'front':hair(k)[1].rows()} for k in ('silver_swept','violet_bob','undercut','locs','cat_black','white_pilot','silver_bob','headwrap')},
          faces={k:face(k).rows() for k in ('soft','sharp','calm','fierce')},
          outfits={k:outfit(k).rows() for k in ('mage','cyber','armor','druid','rogue','pilot','veil','ranger')},
          accessories={k:accessory(k).rows() for k in ('visor','goggles','antlers','cat_ears','circlet','veil','headwrap','none')},
          companions={k:companion(k).rows() for k in ('owl','cat','drone','slime','bat','bunny','moth','hawk')},
          cast=SPECS)
for name,spec in SPECS.items(): preview(spec,name)
out=ROOT/'ios/Resources/character_engine.json'; out.write_text(json.dumps(data,separators=(',',':')))
(ROOT/'android/app/src/main/assets/character_engine.json').write_text(out.read_text())
print('48 engine',out.stat().st_size)
