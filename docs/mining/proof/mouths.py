# Disposable probe: walkable cave mouths in real generated chunks (read-only copy of a region file).
import sys, struct, zlib, io, math, collections, numpy as np
sys.path.insert(0, sys.argv[0].rsplit('/',1)[0])
import nbt
YMIN, YMAX = -64, 320
H = YMAX - YMIN
TERRAIN_WORDS = ('stone','deepslate','dirt','grass_block','gravel','sand','tuff','granite','diorite','andesite','calcite',
  'clay','_ore','podzol','mycelium','terracotta','basalt','mud','moss_block','snow_block','ice','dripstone_block','obsidian',
  'bedrock','blackstone','amethyst_block','budding','smooth_basalt','rooted_dirt','coarse_dirt','farmland','dirt_path','magma',
  'lode_heart','deepglass','sunshard','frostspar','fault_seam','slab','cobble','raw_')
cache = {}
def kind(name):
    k = cache.get(name)
    if k is None:
        n = name.split(':')[1]
        if n in ('air','cave_air','void_air'): k = 0
        elif n in ('water','lava','bubble_column') or 'kelp' in n or 'seagrass' in n: k = 2
        elif any(w in n for w in TERRAIN_WORDS) and 'leaves' not in n and 'log' not in n: k = 1
        else: k = 0   # plants, wood, leaves, flowers: passable, never a roof
        cache[name] = k
    return k
def pname(p):
    if isinstance(p,str): return p.split('[')[0]
    return p.get('id') or p.get('Name') or p.get('')
def chunks(path):
    data = open(path,'rb').read()
    for i in range(1024):
        off = int.from_bytes(data[i*4:i*4+3],'big'); cnt = data[i*4+3]
        if off == 0: continue
        o = off*4096; ln = struct.unpack('>i', data[o:o+4])[0]; comp = data[o+4]
        raw = data[o+5:o+4+ln]
        if comp == 2: raw = zlib.decompress(raw)
        elif comp == 1: import gzip; raw = gzip.decompress(raw)
        else: continue
        yield i % 32, i // 32, nbt.loadraw(raw)
def fill(grid, cx, cz, c):
    st = c.get('Status','')
    if 'full' not in st and 'features' not in st and 'light' not in st and 'spawn' not in st: return False
    for s in c.get('sections', []):
        y = s['Y']; bs = s.get('block_states')
        if bs is None: continue
        pal = [kind(pname(p)) for p in bs['palette']]
        y0 = y*16 - YMIN
        if y0 < 0 or y0 >= H: continue
        if 'data' not in bs:
            grid[cx*16:cx*16+16, y0:y0+16, cz*16:cz*16+16] = pal[0]; continue
        bits = max(4, (len(bs['palette'])-1).bit_length()); per = 64 // bits; mask = (1<<bits)-1
        longs = np.array(bs['data'], dtype=np.int64).view(np.uint64)
        idx = np.arange(4096); li = idx // per; sh = (idx % per) * bits
        vals = ((longs[li] >> sh.astype(np.uint64)) & np.uint64(mask)).astype(np.int64)
        lut = np.array(pal, dtype=np.uint8)
        k = lut[vals].reshape(16,16,16)  # y, z, x
        grid[cx*16:cx*16+16, y0:y0+16, cz*16:cz*16+16] = k.transpose(2,0,1)
    return True
def run(path, rx, rz):
    N = 512
    grid = np.full((N, H, N), 3, dtype=np.uint8)
    have = np.zeros((32,32), bool)
    for cx, cz, c in chunks(path):
        have[cx, cz] = fill(grid, cx, cz, c)
    # top terrain per column
    terr = (grid == 1)
    top = H - 1 - np.argmax(terr[:, ::-1, :], axis=1)   # index of highest terrain cell
    known = np.repeat(np.repeat(have, 16, axis=0), 16, axis=1)
    # a column next to an unknown chunk is unreliable: shrink known by 16
    k2 = known.copy()
    for ax in (0,1):
        for d in (16,-16): k2 &= np.roll(known, d, axis=ax)
    k2[:16,:]=False; k2[-16:,:]=False; k2[:,:16]=False; k2[:,-16:]=False
    top = np.where(k2, top, -1)
    yy = np.arange(H)[None, :, None]
    under = yy < top[:, None, :]
    passable = grid == 0
    sky = passable & ~under & k2[:, None, :]
    cand = passable & under
    # BFS from underground passable cells touching sky-exposed passable cells
    seen = np.zeros_like(cand)
    front = np.zeros_like(cand)
    for ax in range(3):
        for d in (1, -1):
            front |= cand & np.roll(sky, d, axis=ax)
    # label components by BFS in python over flattened indices
    comp = np.zeros(grid.shape, dtype=np.int32)
    sx, sy, sz = grid.shape
    seeds = np.argwhere(front)
    results = []
    lab = 0
    candf = cand.reshape(-1); compf = comp.reshape(-1); topf = top; frontf = front.reshape(-1)
    for (x, y, z) in seeds:
        fi = (x*sy + y)*sz + z
        if compf[fi]: continue
        lab += 1; compf[fi] = lab
        q = collections.deque([fi]); n = 0; maxd = 0; ent = (x, y, z); deepest = None; miny = y; entmin = y; roofed = 0
        while q:
            f = q.popleft(); n += 1
            x2, rem = divmod(f, sy*sz); y2, z2 = divmod(rem, sz)
            if frontf[f] and y2 < entmin: entmin = y2
            if y2 < miny: miny = y2; deepest = (x2, y2, z2)
            if topf[x2, z2] - y2 >= 4: roofed += 1
            if n > 400000: break
            for dx, dy, dz in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)):
                a, b, cc = x2+dx, y2+dy, z2+dz
                if 0 <= a < sx and 0 <= b < sy and 0 <= cc < sz:
                    g = (a*sy + b)*sz + cc
                    if candf[g] and not compf[g]:
                        compf[g] = lab; q.append(g)
        results.append((n, entmin - miny, ent, deepest, roofed))
    mouths = [(n, d, e, dp) for (n, d, e, dp, rf) in results if d >= 12 and rf >= 200]
    
    ox, oz = rx*512, rz*512
    print(f'region {rx},{rz}: chunks with blocks {have.sum()}; sky-touching underground pockets {len(results)}; walkable mouths (lead >=12 below the mouth, >=200 roofed cells) {len(mouths)}')
    out = []
    for n, d, e, dp in sorted(mouths, key=lambda m: -m[0]):
        wx, wy, wz = e[0]+ox, e[1]+YMIN, e[2]+oz
        ex, ey, ez = e
        box = grid[max(0,ex-4):ex+5, max(0,ey-4):ey+5, max(0,ez-4):ez+5]
        wet = 'wet' if (box == 2).any() else 'dry'
        tops = top[max(0,ex-12):ex+13, max(0,ez-12):ez+13]; tops = tops[tops>=0]
        relief = int(tops.max() - tops.min()) if tops.size else -1
        out.append((wx, wy, wz, n, d, wet, relief))
        print(f'  mouth at {wx} {wy} {wz} [{wet}, relief {relief} in 25x25]: {n}{"+" if n>400000 else ""} cells reached, {d} down from the mouth (lowest {dp[0]+ox} {dp[1]+YMIN} {dp[2]+oz})')
    np.save('out/have_{rx}_{rz}.npy', have)
    np.save('out/top_{rx}_{rz}.npy', top)
    with open('out/mouths_{rx}_{rz}.txt','w') as fo:
        for m in out: fo.write(' '.join(map(str,m))+'\n')
if __name__ == '__main__':
    rx, rz = int(sys.argv[1]), int(sys.argv[2])
    run(sys.argv[3], rx, rz)
