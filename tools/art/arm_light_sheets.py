#!/usr/bin/env python3
"""Close sheets of the powers' light on armoured forearms (SPEC 32.N), from arm_light_preview.py's cells and rules.

A row a set, two views of the forearm and hand a type, at one charge. Run from the repo's root:

  python3 tools/art/arm_light_sheets.py <out dir> <charge 0..1> <TYPE,TYPE,...> <file name> <set> [set ...]
  python3 tools/art/arm_light_sheets.py run-autopilot/powers-next/armlight 1.0 ENERGY,HEAT,FROST,KINETIC,SHADOW offline-C.png \
      gatewrought vaultbreaker frostwalker sunward shardcrown
"""
import sys,os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import arm_light_preview as A
from PIL import Image, ImageDraw
out=sys.argv[1]; charge=float(sys.argv[2]); styles=sys.argv[3].split(','); name=sys.argv[4]; sets=sys.argv[5:]
W,H=200,290
sheet=Image.new('RGB',(W*len(styles)*2+110,H*len(sets)),(24,26,34))
d=ImageDraw.Draw(sheet)
for si,sid in enumerate(sets):
    tex=Image.open(os.path.join(A.ROOT,'textures','entity','armour',sid+'.png')).convert('RGBA')
    mesh=os.path.join(A.ROOT,'armour',sid,'chestplate_left_arm.mesh'); cs=A.cells(A.read(mesh),tex,A.accent_of(mesh))
    fore=[c for c in cs if c['v']>=4.0]
    gains=A.SETS.get(sid,A.SETS['default'])
    d.text((4,si*H+H//2),sid,fill=(255,255,255))
    for ti,style in enumerate(styles):
        seed=hash(style)&0xFFFF
        lit=[A.light(style,c,37.0,seed,charge,False,gains) for c in fore]
        shown=[dict(c,p=[[p[0],p[1]-0.30,p[2]] for p in c['p']]) for c in fore]
        for vi,(yaw,pitch) in enumerate([(90,0),(40,-40)]):
            im=A.view(shown,lit,style,yaw,pitch,size=(W,H),scale=50)
            sheet.paste(im,(110+(ti*2+vi)*W,si*H))
        if si==0: d.text((110+ti*2*W+6,4),style,fill=(255,255,255))
sheet.save(os.path.join(out,name)); print(name,sheet.size)
