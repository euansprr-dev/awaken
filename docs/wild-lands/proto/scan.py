# Read-only scan of a save's overworld region + entity files: per chunk plant cover, ground, paths, animals, structures.
import sys, os, zlib, gzip, struct, io, json, math, collections
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
from nbt import rd
W = sys.argv[1]; OUT = sys.argv[2]
def chunks(path):
    with open(path,'rb') as f: data=f.read()
    for i in range(1024):
        off=struct.unpack('>I', data[i*4:i*4+4])[0]
        if off==0: continue
        o=(off>>8)*4096; ln=struct.unpack('>I',data[o:o+4])[0]; ct=data[o+4]
        raw=data[o+5:o+4+ln]
        if ct==2: raw=zlib.decompress(raw)
        elif ct==1: raw=gzip.decompress(raw)
        elif ct==3: pass
        else: continue
        f=io.BytesIO(raw); t=f.read(1)[0]; rd(f,8); yield rd(f,t)
def NM(p):
    if isinstance(p,str): return p
    return p.get('id') or p.get('') or p.get('Name') or 'minecraft:air'
def unpack(longs, bits, n):
    a=np.array(longs, dtype=np.int64).view(np.uint64)
    per=64//bits; mask=np.uint64((1<<bits)-1)
    shifts=np.arange(per, dtype=np.uint64)*np.uint64(bits)
    v=((a[:,None]>>shifts[None,:])&mask).reshape(-1)[:n]
    return v.astype(np.int32)
PLANTS={'short_grass':'grass','tall_grass':'tall','fern':'fern','large_fern':'tall','bush':'bush','firefly_bush':'bush','sweet_berry_bush':'bush',
 'dandelion':'flower','poppy':'flower','blue_orchid':'flower','allium':'flower','azure_bluet':'flower','red_tulip':'flower','orange_tulip':'flower','white_tulip':'flower','pink_tulip':'flower','oxeye_daisy':'flower','cornflower':'flower','lily_of_the_valley':'flower','sunflower':'flower','lilac':'flower','rose_bush':'flower','peony':'flower','wildflowers':'flower','pink_petals':'flower','leaf_litter':'litter','dirt_path':'path','grass_block':'grassblock','short_dry_grass':'grass','tall_dry_grass':'tall','dead_bush':'bush','moss_carpet':'litter'}
res=[]
rdir=os.path.join(W,'region')
for fn in sorted(os.listdir(rdir)):
    if not fn.endswith('.mca'): continue
    for c in chunks(os.path.join(rdir,fn)):
        if c.get('Status')!='minecraft:full': continue
        cx,cz=c['xPos'],c['zPos']
        cnt=collections.Counter(); biomes=collections.Counter()
        hm=c.get('Heightmaps',{}).get('MOTION_BLOCKING_NO_LEAVES')
        h=None
        if hm: h=unpack(hm,9,256)+ (c.get('yPos',-4)*16) - 1
        for s in c.get('sections',[]):
            Y=s['Y']
            if Y<2: continue
            bs=s.get('block_states'); 
            if not bs: continue
            names=[NM(p) for p in bs['palette']]; pal=names
            if 'data' not in bs:
                idx=np.zeros(4096,dtype=np.int32)
            else:
                bits=max(4, math.ceil(math.log2(len(pal))))
                idx=unpack(bs['data'],bits,4096)
            bc=np.bincount(idx, minlength=len(pal))
            for i,n in enumerate(bc):
                if n==0: continue
                nm=names[i].split(':')[1]; ns=names[i].split(':')[0]
                if nm in PLANTS:
                    k=PLANTS[nm]
                    if nm in('tall_grass','large_fern','sunflower','lilac','rose_bush','peony','tall_dry_grass'): n=n/2
                    cnt[k]+=n
                elif nm.endswith('_leaves'): cnt['leaves']+=n
                elif nm.endswith('_log') or nm.endswith('_wood'): cnt['log']+=n
                elif ns=='awaken' and ('turf' in nm or 'pebbles' in nm or 'twigs' in nm): cnt['aw_'+nm.split('_')[0]]+=n
                elif nm in ('farmland','hay_block','composter','bell','oak_planks','spruce_planks','cobblestone','stone_bricks','oak_fence','spruce_fence','white_wool','torch','lantern','glass_pane','oak_door','spruce_door'): cnt['built']+=n
            b=s.get('biomes')
            if b and Y>=3 and Y<=8:
                bp=[NM(x) for x in b['palette']]
                if len(bp)==1: biomes[bp[0]]+=64
                else:
                    bits=max(1, math.ceil(math.log2(len(bp)))); bi=unpack(b['data'],bits,64)
                    for i,n in enumerate(np.bincount(bi,minlength=len(bp))): biomes[bp[i]]+=int(n)
        starts=[]
        for k,v in c.get('structures',{}).get('starts',{}).items():
            if v.get('id','INVALID')!='INVALID':
                bb=None
                ch=v.get('Children',[])
                starts.append([k, len(ch), [p.get('BB') for p in ch][:40]])
        refs=list(c.get('structures',{}).get('References',{}).keys())
        rec={'cx':cx,'cz':cz,'c':dict(cnt),'biome':(biomes.most_common(1)[0][0] if biomes else ''),
             'h':(float(h.mean()) if h is not None else None),'hs':(float(h.std()) if h is not None else None),
             'hr':(int(h.max()-h.min()) if h is not None else None),'starts':starts,'refs':refs}
        res.append(rec)
    print(fn, len(res), flush=True)
# entities
ent=collections.defaultdict(collections.Counter)
edir=os.path.join(W,'entities')
for fn in sorted(os.listdir(edir)):
    if not fn.endswith('.mca'): continue
    for c in chunks(os.path.join(edir,fn)):
        pos=c.get('Position')
        for e in c.get('Entities',[]):
            p=e.get('Pos',[0,0,0]); key=(int(p[0])>>4, int(p[2])>>4)
            tag=e['id'].split(':')[1] + ('#ledger' if 'awaken_ledger' in e.get('Tags',[]) else '')
            ent[key][tag]+=1
for r in res:
    r['e']=dict(ent.get((r['cx'],r['cz']),{}))
json.dump(res, open(OUT,'w'), default=float)
print('done', len(res))
