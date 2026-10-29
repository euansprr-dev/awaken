"""Minimal NBT and region-file reader for the offline Far Sight replicas (sheets.py). Read only."""
import struct, gzip, zlib, io
def read_nbt(data):
    b=io.BytesIO(data)
    def rd(fmt): return struct.unpack('>'+fmt, b.read(struct.calcsize('>'+fmt)))[0]
    def rstr(): n=rd('H'); return b.read(n).decode('utf-8','replace')
    def payload(t):
        if t==1: return rd('b')
        if t==2: return rd('h')
        if t==3: return rd('i')
        if t==4: return rd('q')
        if t==5: return rd('f')
        if t==6: return rd('d')
        if t==7: n=rd('i'); return b.read(n)
        if t==8: return rstr()
        if t==9:
            et=rd('b'); n=rd('i'); return [payload(et) for _ in range(n)]
        if t==10:
            d={}
            while True:
                et=rd('b')
                if et==0: return d
                k=rstr(); d[k]=payload(et)
        if t==11: n=rd('i'); return list(struct.unpack('>%di'%n, b.read(4*n)))
        if t==12: n=rd('i'); return list(struct.unpack('>%dq'%n, b.read(8*n)))
        raise ValueError(t)
    t=rd('b'); rstr(); return payload(t)
def level(path):
    return read_nbt(gzip.open(path).read())
def region_chunk(path, cx, cz):
    with open(path,'rb') as f: data=f.read()
    i=4*((cx&31)+(cz&31)*32)
    off=int.from_bytes(data[i:i+3],'big'); cnt=data[i+3]
    if off==0: return None
    o=off*4096; ln=int.from_bytes(data[o:o+4],'big'); comp=data[o+4]
    raw=data[o+5:o+4+ln]
    if comp==2: raw=zlib.decompress(raw)
    elif comp==1: raw=gzip.decompress(raw)
    return read_nbt(raw)
