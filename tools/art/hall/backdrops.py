"""The Hall of Arms' backdrops (SPEC §52): the wall of niches, the Picks wall and the terraces, lit and dark. Own art, numpy only.
Run: python3 tools/art/hall/backdrops.py [picks]   (writes assets/awaken/textures/gui/hall/*.png; "picks" writes only the Picks wall)
The niche layouts here are the same arithmetic as dev.awaken.hall.client.HallLayout and PickLayout: change them together."""
import numpy as np, os, math, sys
from PIL import Image
ONLY_PICKS=len(sys.argv)>1 and sys.argv[1]=='picks'
R=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..','..'))
O=os.environ.get('HALL_OUT',R+'/src/main/resources/assets/awaken/textures/gui/hall')
W,H=1708,960
rng=np.random.default_rng(7)
# ---------- layout
ROW_H=[184,156,130,108,90,74]; GAP=9; BOT=908
P=[1.0,.935,.87,.805,.74,.68]
FAM=[4,2,4,3,3]
cells=[]; y=BOT; rows=[]
for r in range(6):
    h=ROW_H[r]; top=y-h; rows.append((top,y)); 
    tw=1640*P[r]; fg=26*P[r]; cw=(tw-fg*4)/16; x=854-tw/2; c=0
    for f,n in enumerate(FAM):
        for i in range(n):
            cells.append(dict(r=r,c=c,f=f,x=round(x+cw/2,2),y0=top,y1=y,w=round(cw,2))); x+=cw; c+=1
        x+=fg
    y=top-GAP
wall=dict(cells=cells,rows=rows,P=P)
# the Picks wall (PickLayout): the same rows, four forms across, each its own bay, narrower and centred
PICK_WIDE=980; PICK_GAP=46
pick_cells=[]
for r in range(6):
    top,bot=rows[r]
    tw=PICK_WIDE*P[r]; fg=PICK_GAP*P[r]; cw=(tw-fg*3)/4; x=854-tw/2
    for c in range(4):
        pick_cells.append(dict(r=r,c=c,f=c,x=round(x+cw/2,2),y0=top,y1=bot,w=round(cw,2))); x+=cw+fg
# terraces: E nearest; staggered like a choir; Strider Bulwark Channeler Duelist Lord
TERR=[(940,288,322),(786,232,272),(646,190,232),(520,154,198),(408,126,168),(312,102,142)]
terr=[]
for r,(feet,h,sp) in enumerate(TERR):
    n=5
    off=0 if r%2==0 else sp*0.5*0.0
    for c in range(n):
        if r==0 and c==4: continue
        cx=854+(c-((3 if r==0 else 4)/2))*sp+({2:.22,3:-.22,4:.22,5:-.22}.get(r,0))*sp
        terr.append(dict(r=r,c=c,x=round(cx,1),feet=feet,h=h))
# ---------- noise
def vnoise(u,v,seed=1):
    iu=np.floor(u).astype(int); iv=np.floor(v).astype(int)
    n=(iu*73856093 ^ iv*19349663 ^ seed*83492791)&0x7fffffff
    return ((n*48271)%2147483647)/2147483647.
yy,xx=np.mgrid[0:H,0:W]+0.5
def smooth(a,b,x):
    t=np.clip((x-a)/(b-a),0,1); return t*t*(3-2*t)
def save(a,n):
    Image.fromarray((np.clip(a,0,1)*255).astype(np.uint8),'RGB').save(O+'/'+n+'.png',optimize=True)
# ---------- the wall
warm=np.array([1.0,.74,.50]); cool=np.array([.40,.55,.85])
def paint_wall(cells,edges,side,lit_name,dark_name):
    rt=[top for top,_ in rows]; ry=[b for _,b in rows]
    ys=np.array([BOT]+[t for t in rt])
    vs=np.arange(7.0)
    v=np.interp(-yy,-ys,vs)  # continuous row coord
    v=np.where(yy<ys[-1],6+(ys[-1]-yy)/70.,v)
    pv=np.interp(v,np.arange(6),P)
    pv=np.where(v>5,P[5]-(v-5)*.05,pv)
    u=(xx-854)/pv
    cw=30.;ch=.22
    course=np.floor(v/ch)
    sx=u/cw+0.5*(course%2)
    fu=sx-np.floor(sx); fv=v/ch-course
    b=vnoise(sx,course,3)
    fine=vnoise(u/ (7.),v*18,5)*.5+vnoise(u/15.,v*9,9)*.5
    mortar=1-(1-smooth(0,.05,fu)*smooth(1,.95,fu))*.75; mortar=mortar*(1-(1-smooth(0,.08,fv)*smooth(1,.92,fv))*.75)
    stone=(.52+.38*b)*(.62+.5*fine)*mortar
    alb=np.dstack([stone*.56,stone*.52,stone*.52])
    # ribs between the five bays and at the sides
    rib=np.zeros((H,W)); ribhi=np.zeros((H,W))
    acc=0; 
    for edge in edges:
        # centre of the gap for each row via cells
        pts=[]
        for r in range(6):
            cs=[c for c in cells if c['r']==r]
            if edge==0: x=cs[0]['x']-cs[0]['w']/2-side*P[r]
            elif edge==len(cs): x=cs[-1]['x']+cs[-1]['w']/2+side*P[r]
            else: x=(cs[edge-1]['x']+cs[edge-1]['w']/2+cs[edge]['x']-cs[edge]['w']/2)/2
            pts.append(x)
        xc=np.interp(v,np.arange(6),pts); xc=np.where(v>5,854+(pts[5]-854)*(pv/P[5]),xc)
        hw=11*pv
        d=(xx-xc)/hw
        m=smooth(1.08,.9,np.abs(d))
        rib=np.maximum(rib,m)
        ribhi=np.maximum(ribhi,m*smooth(-.9,-.4,d)*smooth(.1,-.3,d))
    alb=alb*(1-rib[...,None])+np.dstack([.30+.0*rib,.28+.0*rib,.29+0*rib])*rib[...,None]*(.75+.5*fine[...,None])
    alb=alb+ribhi[...,None]*.10
    # ledges
    led=np.zeros((H,W)); 
    for r in range(6):
        top,bot=rows[r]
        led=np.maximum(led,smooth(bot+9,bot+6,yy)*smooth(bot,bot+2,yy))
    alb=alb*(1-led[...,None]*.45)+led[...,None]*.28
    nicheI=np.zeros((H,W)); nicheR=np.zeros((H,W)); floorg=np.zeros((H,W))
    for c in cells:
        x,y0,y1,w=c['x'],c['y0'],c['y1'],c['w']*.90
        x0,x1=int(x-w/2-4),int(x+w/2+4); yA,yB=int(y0-2),int(y1+2)
        sub=(max(0,yA),min(H,yB),max(0,x0),min(W,x1))
        yy2=yy[sub[0]:sub[1],sub[2]:sub[3]]; xx2=xx[sub[0]:sub[1],sub[2]:sub[3]]
        s=(xx2-x)/(w/2); t=(y1-yy2)/(y1-y0)
        lim=np.where(t<.70,1.0,np.clip(1-((t-.70)/.30)**1.5,0,1))
        ins=np.abs(s)-lim; ins=np.where((t<0)|(t>1),1,ins)
        # hard-stepped for a pixel look
        m=(ins<0).astype(float); rim=((ins<.09)&(ins>=-0.0)).astype(float)
        nicheI[sub[0]:sub[1],sub[2]:sub[3]]=np.maximum(nicheI[sub[0]:sub[1],sub[2]:sub[3]],m)
        nicheR[sub[0]:sub[1],sub[2]:sub[3]]=np.maximum(nicheR[sub[0]:sub[1],sub[2]:sub[3]],rim)
        g=np.exp(-(((t-.0)/.34)**2))*np.exp(-(s/.8)**2)*m
        floorg[sub[0]:sub[1],sub[2]:sub[3]]=np.maximum(floorg[sub[0]:sub[1],sub[2]:sub[3]],g)
    inner=np.dstack([.055,.046,.048])*(1+.5*fine[...,None])
    alb=alb*(1-nicheI[...,None])+inner*nicheI[...,None]
    alb=alb*(1-nicheR[...,None])+np.dstack([.42,.36,.31])*nicheR[...,None]
    # light
    key=smooth(-.2,1.1,(yy/H))**.8*.95+.12
    shaft=np.exp(-(((xx-854)/(380-yy*.05))**2))*(.5)*(1-yy/H*.55)
    L=(key[...,None]*warm*1.2 + shaft[...,None]*np.array([.85,.8,.75])*1.0 + .09*cool)
    lit=alb*L + floorg[...,None]*warm*.85
    vig=1-.62*smooth(.55,1.25,np.hypot((xx-854)/854,(yy-520)/520))
    lit=lit*vig[...,None]
    lit=np.clip(lit,0,1)**.92
    dark=alb*(.13*cool+.05)+floorg[...,None]*.012
    dark=dark*vig[...,None]
    save(lit,lit_name); save(dark,dark_name)
if not ONLY_PICKS: paint_wall(cells,[0,4,6,10,13,16],13,'wall_lit','wall_dark')
paint_wall(pick_cells,[0,1,2,3,4],PICK_GAP/2,'pick_lit','pick_dark')
if ONLY_PICKS: sys.exit(0)
# ---------- terraces
feet=[t[0] for t in TERR]
ty=np.array(feet+[feet[-1]-58,feet[-1]-116,feet[-1]-190,feet[-1]-280,-400.])
tv=np.interp(-yy,-ty,np.arange(len(ty)))
tv=np.where(yy>feet[0],-(yy-feet[0])/150.,tv)
tp=np.interp(tv,[-1,0,1,2,3,4,5,6,8,10],[1.12,1,.86,.74,.63,.54,.47,.42,.34,.3])
tu=(xx-854)/tp
row=np.floor(tv+.25)
fr=tv+.25-row
rise=smooth(.40,.43,fr)
bw=74.
bx=tu/bw+row*.37
bid=np.floor(bx)
bfr=bx-bid
sh=.78+.34*vnoise(bid,row,11)
fine=vnoise(tu/6.,tv*40,13)*.5+vnoise(tu/13.,tv*18,17)*.5
st=sh*(.72+.34*fine)
mort=1-(1-smooth(0,.02,bfr)*smooth(1,.98,bfr))*.5*rise
tread=np.dstack([st*.46,st*.42,st*.41])
riser=np.dstack([st*.21,st*.19,st*.20])*mort[...,None]
a=tread*(1-rise[...,None])+riser*rise[...,None]
lip=np.exp(-((fr-.40)/.018)**2)*.28
a=a+lip[...,None]*np.array([1.,.9,.8])
sidef=smooth(.78,1.18,np.abs(tu)/860.)
a=a*(1-sidef[...,None])
keyL=np.clip(smooth(120,960,yy)**.75,0,1)*1.05+.05
shaft=np.exp(-(((xx-854)/(250+(960-yy)*.12))**2))*.7*(1-.35*yy/H)
Lt=keyL[...,None]*warm*1.25+shaft[...,None]*np.array([.92,.88,.82])*1.1+.06*cool
tl=a*Lt
vig2=1-.72*smooth(.5,1.2,np.hypot((xx-854)/854,(yy-640)/640))
tl=np.clip(tl*vig2[...,None],0,1)**.95
td=a*(.13*cool+.04)*vig2[...,None]
save(tl,'terr_lit'); save(td,'terr_dark')
# ---------- a soft round of light, tinted when it is drawn (the hero's glow, the lantern's pool)
n=512; v_,u_=np.mgrid[0:n,0:n]+0.5
d=np.hypot(u_-n/2,v_-n/2)/(n/2)
a=np.clip(1-d,0,1)**2.2
a=a+(np.random.default_rng(3).random((n,n))-.5)/255*1.5  # a little dither, or a wide faint glow bands
rgba=np.dstack([np.ones((n,n)),np.ones((n,n)),np.ones((n,n)),np.clip(a,0,1)])
Image.fromarray((rgba*255).astype(np.uint8),'RGBA').save(O+'/glow.png',optimize=True)
