import json, sys, collections, datetime
def scan(path):
    rows=[]
    seen=set()
    with open(path,'rb') as f:
        for line in f:
            if b'"usage"' not in line: continue
            try: o=json.loads(line)
            except Exception: continue
            m=o.get('message') or {}
            u=m.get('usage')
            if not u or o.get('type')!='assistant': continue
            mid=m.get('id')
            key=(mid, o.get('requestId'))
            if key in seen: continue
            seen.add(key)
            ts=o.get('timestamp')
            rows.append((ts, m.get('model'), u.get('input_tokens',0), u.get('cache_creation_input_tokens',0), u.get('cache_read_input_tokens',0), u.get('output_tokens',0), bool(o.get('isSidechain'))))
    return rows
if __name__=='__main__':
    rows=scan(sys.argv[1])
    print(len(rows),'assistant turns', rows[0][0], rows[-1][0])
    by=collections.OrderedDict()
    for ts,model,i,cc,cr,out,sc in rows:
        h=ts[:13]
        b=by.setdefault(h,[0,0,0,0,0,collections.Counter()])
        b[0]+=i; b[1]+=cc; b[2]+=cr; b[3]+=out; b[4]+=1; b[5][model]+=1
    print('hour(UTC)      turns  input   cacheW      cacheR     output  models')
    for h,b in by.items():
        print(h, f'{b[4]:5d} {b[0]:7d} {b[1]:9d} {b[2]:11d} {b[3]:8d}', dict(b[5]))
    t=[sum(r[k] for r in rows) for k in (2,3,4,5)]
    print('TOTAL input %d cacheW %d cacheR %d output %d'%tuple(t))
