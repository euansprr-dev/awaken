import json, math, sys, collections
import numpy as np
r=json.load(open(sys.argv[1]))
CITY=(490,-411,330); SET=[(-352,-2080,116),(464,-1283,38)]; HEARTH=(403,-1961,40)
road=set()
for c in r:
    if c['c'].get('path',0)>=12: road.add((c['cx'],c['cz']))
def dset(x,z):
    d=math.hypot(x-CITY[0],z-CITY[1])-CITY[2]
    for sx,sz,sr in SET+[HEARTH]: d=min(d, math.hypot(x-sx,z-sz)-sr)
    return max(0,d)
rc=np.array(sorted(road))
def droad(cx,cz):
    if len(rc)==0: return 9999
    return float(np.min(np.hypot(rc[:,0]-cx, rc[:,1]-cz)))*16
ANIM=('sheep','pig','cow','chicken','rabbit','horse','donkey','goat','wolf','mossback','fox','llama')
rows=[]
for c in r:
    x,z=c['cx']*16+8,c['cz']*16+8
    if 'ocean' in c['biome'] or 'river' in c['biome'] or c['h'] is None or c['h']<62: continue
    gb=c['c'].get('grassblock',0)
    rows.append(dict(ds=dset(x,z), dr=droad(c['cx'],c['cz']), biome=c['biome'],
      grass=c['c'].get('grass',0)+c['c'].get('fern',0), tall=c['c'].get('tall',0), bush=c['c'].get('bush',0), flower=c['c'].get('flower',0),
      leaves=c['c'].get('leaves',0), gb=gb, hr=c['hr'], anim=sum(c['e'].get(a,0) for a in ANIM), wolf=c['e'].get('wolf',0),
      farm=sum(c['e'].get(a,0) for a in ('sheep','pig','cow','chicken')), moss=c['e'].get('mossback',0)+c['e'].get('mossback#ledger',0)))
print('land chunks', len(rows), 'road chunks', len(road))
def table(key, bins, filt=lambda w: True, label=''):
    print(f'\n{label}  by {key}')
    print(f"{'bin':>12} {'chunks':>6} {'grass':>6} {'tall':>6} {'bush':>5} {'flower':>6} {'leaves':>7} {'animals/km2':>11} {'wolves':>6}")
    for lo,hi in zip(bins[:-1],bins[1:]):
        sel=[w for w in rows if lo<=w[key]<hi and filt(w)]
        if not sel: continue
        n=len(sel); m=lambda k: sum(w[k] for w in sel)/n
        print(f"{lo:>5}-{hi:<6} {n:>6} {m('grass'):6.1f} {m('tall'):6.1f} {m('bush'):5.2f} {m('flower'):6.1f} {m('leaves'):7.0f} {m('anim')*3906:11.0f} {m('wolf')*3906:6.0f}")
B=[0,64,128,256,512,768,1024,1536,4000]
table('ds',B,label='ALL land')
table('ds',B,lambda w: w['biome'] in ('minecraft:plains','minecraft:meadow'),'PLAINS+MEADOW')
table('ds',B,lambda w: w['biome'] in ('minecraft:forest','minecraft:birch_forest','minecraft:old_growth_birch_forest','awaken:elderwood'),'FORESTS')
table('dr',[0,16,48,96,192,384,800,4000],label='ALL land by road distance')
# spread: per-chunk coefficient of variation of grass among plains chunks
pl=[w['grass']+w['tall'] for w in rows if w['biome']=='minecraft:plains']
print('\nplains chunks grass+tall: mean %.1f sd %.1f p10 %.0f p90 %.0f'%(np.mean(pl),np.std(pl),np.percentile(pl,10),np.percentile(pl,90)))
an=[w['anim'] for w in rows]; print('animals per chunk: share of chunks with any %.1f%%, max %d'%(100*np.mean([a>0 for a in an]), max(an)))
# group sizes: animals of one kind in a 3x3 chunk window clustering
