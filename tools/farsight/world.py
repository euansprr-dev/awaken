"""A save's blocks, read straight from its region files (sheets.py). Read only."""
import nbt, math, os
AIRS={'minecraft:air','minecraft:cave_air','minecraft:void_air'}
class World:
    def __init__(self, region_dir):
        self.dir=region_dir; self.cache={}; self.regions={}
    def chunk(self, cx, cz):
        k=(cx,cz)
        if k in self.cache: return self.cache[k]
        p=os.path.join(self.dir,'r.%d.%d.mca'%(cx>>5,cz>>5))
        c=None
        if os.path.exists(p):
            try: raw=nbt.region_chunk(p,cx,cz)
            except Exception as e: raw=None
            if raw is not None: c=self.decode(raw)
        self.cache[k]=c
        return c
    def decode(self, raw):
        secs={}
        for s in raw.get('sections',[]):
            y=s['Y']; bs=s.get('block_states')
            if bs is None: continue
            pal=[];props=[]
            for p in bs['palette']:
                if isinstance(p,str): pal.append(p); props.append({}); continue
                if '' in p: p=p['']
                if isinstance(p,str): pal.append(p); props.append({}); continue
                pal.append(p.get('Name') or p.get('id')); props.append(p.get('Properties') or p.get('properties') or {})
            data=bs.get('data')
            if data is None or len(pal)==1: idx=None
            else:
                bits=max(4,(len(pal)-1).bit_length()); per=64//bits; mask=(1<<bits)-1
                idx=[]
                for i in range(4096):
                    l=data[i//per]&0xFFFFFFFFFFFFFFFF
                    idx.append((l>>((i%per)*bits))&mask)
            sky=s.get('SkyLight'); bl=s.get('BlockLight')
            secs[y]=(pal,props,idx,sky,bl)
        hm=raw.get('Heightmaps',{}).get('MOTION_BLOCKING_NO_LEAVES')
        ground=None
        if hm:
            bits=9; per=64//bits
            ground=min(((hm[i//per]&0xFFFFFFFFFFFFFFFF)>>((i%per)*bits))&511 for i in range(256))-64
        return {'secs':secs,'status':raw.get('Status'),'ground':ground}
    def block(self, x, y, z):
        c=self.chunk(x>>4,z>>4)
        if c is None: return None
        s=c['secs'].get(y>>4)
        if s is None: return 'minecraft:air'
        pal,props,idx,sky,bl=s
        if idx is None: return pal[0]
        return pal[idx[((y&15)<<8)|((z&15)<<4)|(x&15)]]
    def props(self,x,y,z):
        c=self.chunk(x>>4,z>>4); s=c['secs'].get(y>>4)
        pal,props,idx,sky,bl=s
        return props[0] if idx is None else props[idx[((y&15)<<8)|((z&15)<<4)|(x&15)]]
    def sky(self, x, y, z):
        c=self.chunk(x>>4,z>>4)
        if c is None: return -1
        s=c['secs'].get(y>>4)
        if s is None or s[3] is None: return 15 if (y>>4)>=5 else 0
        i=((y&15)<<8)|((z&15)<<4)|(x&15); b=s[3][i>>1]&0xFF
        return (b>>4) if i&1 else (b&15)
    def skyraw(self, x, y, z):
        # the stored nibble, or None where the section keeps no sky layer
        c=self.chunk(x>>4,z>>4)
        if c is None: return None
        s=c['secs'].get(y>>4)
        if s is None or s[3] is None: return None
        i=((y&15)<<8)|((z&15)<<4)|(x&15); b=s[3][i>>1]&0xFF
        return (b>>4) if i&1 else (b&15)
    def skyvanilla(self, x, y, z):
        # vanilla's rule: a missing layer takes the bottom row of the nearest layer above; none above: 15
        sy=y>>4
        while sy<20:
            c=self.chunk(x>>4,z>>4)
            s=c['secs'].get(sy) if c else None
            if s is not None and s[3] is not None:
                yy=y if sy==(y>>4) else sy*16
                i=((yy&15)<<8)|((z&15)<<4)|(x&15); b=s[3][i>>1]&0xFF
                return (b>>4) if i&1 else (b&15)
            sy+=1
        return 15
    def blight(self, x, y, z):
        c=self.chunk(x>>4,z>>4)
        if c is None: return -1
        s=c['secs'].get(y>>4)
        if s is None or s[4] is None: return 0
        i=((y&15)<<8)|((z&15)<<4)|(x&15); b=s[4][i>>1]&0xFF
        return (b>>4) if i&1 else (b&15)
