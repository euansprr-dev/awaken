import glob, re, math, struct, zlib, numpy as np, sys
sys.path.insert(0,'.')
import nbt
mouths=[]
for f in glob.glob('out/log_*.txt'):
    for line in open(f):
        m=re.match(r'\s+mouth at (-?\d+) (-?\d+) (-?\d+) \[(\w+), relief (-?\d+).*?(\d+) down', line)
        if m: mouths.append(tuple(int(m.group(i)) for i in (1,2,3))+(m.group(4),int(m.group(5)),int(m.group(6))))
pts=[]
for f in glob.glob('reg/r.*.mca'):
    rx,rz=map(int,re.findall(r'r\.(-?\d+)\.(-?\d+)',f)[0])
    if (rx,rz) in ((0,0),(-1,-1)): continue
    data=open(f,'rb').read()
    for i in range(1024):
        off=int.from_bytes(data[i*4:i*4+3],'big')
        if off==0: continue
        o=off*4096; ln=struct.unpack('>i',data[o:o+4])[0]
        raw=zlib.decompress(data[o+5:o+4+ln])
        c=nbt.loadraw(raw)
        if 'full' not in c.get('Status',''): continue
        cx,cz=i%32,i//32
        if cx<1 or cx>30 or cz<1 or cz>30: continue
        pts.append((rx*512+cx*16+8, rz*512+cz*16+8))
P=np.array(pts,float); M=np.array([(m[0],m[2]) for m in mouths],float)
d=np.sqrt(((P[:,None,:]-M[None,:,:])**2).sum(-1)).min(1)
print('mouths',len(M),'chunks sampled',len(P), 'area km2', len(P)*256/1e6)
for r in (100,150,200,300,400): print(f'within {r} m: {np.mean(d<=r)*100:.0f}%')
print('median distance to nearest mouth', np.median(d), 'p90', np.percentile(d,90))
deep=np.array([(m[0],m[2]) for m in mouths if m[5]>=30],float)
d2=np.sqrt(((P[:,None,:]-deep[None,:,:])**2).sum(-1)).min(1)
print('mouths leading 30+ down', len(deep), 'median', np.median(d2), 'within 200m', np.mean(d2<=200)*100)
for name,(x,z) in {'hearth':(407,-1965),'mine':(472,-1384)}.items():
    dd=np.sqrt(((M-[x,z])**2).sum(1)); j=dd.argmin(); print(name,'nearest mouth',mouths[j][:3],round(dd[j]))
