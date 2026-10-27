import gzip, struct, sys
def read(f):
    data = gzip.open(f).read()
    pos = 0
    def u(fmt):
        nonlocal pos
        s = struct.calcsize(fmt); v = struct.unpack_from('>'+fmt, data, pos); pos += s; return v[0]
    def string():
        nonlocal pos
        n = u('H'); s = data[pos:pos+n].decode('utf8','replace'); pos += n; return s
    def payload(t):
        nonlocal pos
        if t==1: return u('b')
        if t==2: return u('h')
        if t==3: return u('i')
        if t==4: return u('q')
        if t==5: return u('f')
        if t==6: return u('d')
        if t==7:
            n=u('i'); v=list(data[pos:pos+n]); pos+=n; return v
        if t==8: return string()
        if t==9:
            et=u('b'); n=u('i'); return [payload(et) for _ in range(n)]
        if t==10:
            d={}
            while True:
                tt=u('b')
                if tt==0: return d
                k=string(); d[k]=payload(tt)
        if t==11:
            n=u('i'); return [u('i') for _ in range(n)]
        if t==12:
            n=u('i'); return [u('q') for _ in range(n)]
        raise Exception('tag %d'%t)
    t=u('b'); name=string(); return payload(t)
if __name__=='__main__':
    import json
    print(json.dumps(read(sys.argv[1]), indent=1, default=str)[:int(sys.argv[2]) if len(sys.argv)>2 else 4000])
