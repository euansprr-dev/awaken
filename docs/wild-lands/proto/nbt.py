import gzip, struct, sys, json, io
def rd(f, t):
    if t==1: return struct.unpack('>b', f.read(1))[0]
    if t==2: return struct.unpack('>h', f.read(2))[0]
    if t==3: return struct.unpack('>i', f.read(4))[0]
    if t==4: return struct.unpack('>q', f.read(8))[0]
    if t==5: return struct.unpack('>f', f.read(4))[0]
    if t==6: return struct.unpack('>d', f.read(8))[0]
    if t==7: n=rd(f,3); return list(f.read(n))
    if t==8: n=struct.unpack('>H', f.read(2))[0]; return f.read(n).decode('utf-8','replace')
    if t==9:
        et=f.read(1)[0]; n=rd(f,3); return [rd(f,et) for _ in range(n)]
    if t==10:
        d={}
        while True:
            tt=f.read(1)[0]
            if tt==0: return d
            name=rd(f,8); d[name]=rd(f,tt)
    if t==11: n=rd(f,3); return [rd(f,3) for _ in range(n)]
    if t==12: n=rd(f,3); return [rd(f,4) for _ in range(n)]
def load(p):
    raw=open(p,'rb').read()
    try: raw=gzip.decompress(raw)
    except Exception: pass
    f=io.BytesIO(raw); t=f.read(1)[0]; rd(f,8); return rd(f,t)
if __name__=='__main__':
    for p in sys.argv[1:]:
        print('###', p.split('/')[-1]); print(json.dumps(load(p), indent=None, default=str)[:4000])
