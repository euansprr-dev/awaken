# Disposable, read-only: cave spots in a generated region, by band and biome, with the longest view from each.
# python3 -I spots.py <region.mca> ...   -> JSON lines
import sys, math, json, collections
import numpy as np
sys.path.insert(0, sys.argv[0].rsplit('/', 1)[0])
exec(open(sys.argv[0].rsplit('/', 1)[0] + '/census.py').read().split('def main')[0])

Y0, YN = -64, 224          # y -64 .. 159
def biome_grid(c):
    """(4, YN//4, 4) biome names per 4^3 cell [x, y, z]."""
    out = np.empty((4, YN // 4, 4), object); out[:] = '?'
    for s in c.get('sections', []):
        y0 = s['Y'] * 16 - YMIN
        if y0 < 0 or y0 >= YN: continue
        b = s.get('biomes', {}); pal = b.get('palette', [])
        if not pal: continue
        if 'data' not in b: vals = np.zeros(64, int)
        else:
            bits = max(1, (len(pal) - 1).bit_length()); per = 64 // bits; mask = (1 << bits) - 1
            longs = np.array(b['data'], dtype=np.int64).view(np.uint64)
            idx = np.arange(64); vals = ((longs[idx // per] >> ((idx % per) * bits).astype(np.uint64)) & np.uint64(mask)).astype(int)
        arr = np.array(pal, object)[vals].reshape(4, 4, 4)   # y, z, x
        out[:, y0 // 4:y0 // 4 + 4, :] = arr.transpose(2, 0, 1)
    return out

def region(path, rx, rz):
    N = 512
    g = np.zeros((N, YN, N), np.int32); have = np.zeros((32, 32), bool); bio = {}
    for cx, cz, c in chunks(path):
        col = column(c)
        if col is None: continue
        gg = col[0][:, :YN, :]
        if np.isin(gg, BUILT_IDS()).sum() > 300: continue
        g[cx*16:cx*16+16, :, cz*16:cz*16+16] = gg; have[cx, cz] = True; bio[(cx, cz)] = biome_grid(c)
    K = np.array(kinds, np.int8)[g]
    terr = K == 1
    # the column's ground: top terrain over the whole column (we keep y < 160 only; mountains above count as roof)
    top = YN - 1 - np.argmax(terr[:, ::-1, :], axis=1)
    known = np.repeat(np.repeat(have, 16, 0), 16, 1)
    yy = np.arange(YN)[None, :, None]
    under = (yy < top[:, None, :] - 5) & known[:, None, :]
    cave = ((K == 0) | (K == 3)) & under
    wet = K == 2
    # standable: cave here and above, rock below, no fluid within 1 below
    st = cave.copy()
    st[:, :-1, :] &= cave[:, 1:, :]
    st[:, 1:, :] &= terr[:, :-1, :]
    st[:, 0, :] = False
    # openness on 4^3 coarse cells: cave cells within the 3x3x3 coarse block round a spot (12^3 = 1728)
    D = cave.reshape(128, 4, YN // 4, 4, 128, 4).sum(axis=(1, 3, 5)).astype(np.int32)
    P = np.pad(D, 1)
    O = sum(P[1+a:129+a, 1+b:YN//4+1+b, 1+c:129+c] for a in (-1, 0, 1) for b in (-1, 0, 1) for c in (-1, 0, 1))
    pts = np.argwhere(st[::3, :, ::3]); pts[:, 0] *= 3; pts[:, 2] *= 3
    res = []
    for x, y, z in pts:
        if x < 8 or z < 8 or x > N - 9 or z > N - 9: continue
        if wet[x, y-1, z] or wet[max(0, x-1):x+2, y-1:y+3, max(0, z-1):z+2].any(): continue
        o = int(O[x // 4, y // 4, z // 4])
        res.append((x, y, z, o))
    return g, K, cave, top, bio, res

def view(cave, x, y, z):
    """The yaw (of 16) with the longest run of open cells at eye level (y + 1), and that run."""
    best = (0, 0)
    for i in range(16):
        yaw = i * 22.5; dx = -math.sin(math.radians(yaw)); dz = math.cos(math.radians(yaw))
        n = 0
        for s in range(1, 48):
            px, pz = int(math.floor(x + 0.5 + dx * s)), int(math.floor(z + 0.5 + dz * s))
            if not (0 <= px < cave.shape[0] and 0 <= pz < cave.shape[2]) or not cave[px, y + 1, pz]: break
            n = s
        if n > best[1]: best = (yaw, n)
    return best

def main():
    rng = np.random.default_rng(7)
    for path in sys.argv[1:]:
        rx, rz = map(int, re.findall(r'r\.(-?\d+)\.(-?\d+)', path)[0])
        g, K, cave, top, bio, res = region(path, rx, rz)
        rows = []
        for x, y, z, o in res:
            b = bio.get((x // 16, z // 16))
            bn = b[(x % 16) // 4, y // 4, (z % 16) // 4] if b is not None else '?'
            wy = y + Y0
            band = 'shallows' if wy >= 40 else 'middle' if wy >= 0 else 'deep'
            rows.append((x, y, z, o, bn, band, int(top[x, z]) + Y0))
        by = collections.defaultdict(list)
        for r in rows:
            cave_b = r[4] in ('minecraft:lush_caves', 'minecraft:dripstone_caves', 'minecraft:deep_dark', 'minecraft:sulfur_caves')
            by[(r[4] if cave_b else 'plain', r[5])].append(r)
        print(f'# region {rx} {rz}: {len(rows)} standable spots', file=sys.stderr)
        for key, rs in sorted(by.items()):
            os_ = np.array([r[3] for r in rs])
            for label, q in (('typical', 50), ('big', 97)):
                t = np.percentile(os_, q)
                cand = [r for r in rs if abs(r[3] - t) <= max(20, 0.05 * t)]
                if not cand: continue
                pick = cand[int(rng.integers(len(cand)))]
                yaw, run = view(cave, pick[0], pick[1], pick[2])
                wx, wz = rx * 512 + pick[0], rz * 512 + pick[2]
                print(json.dumps(dict((k, (int(v) if isinstance(v, (np.integer,)) else float(v) if isinstance(v, np.floating) else v)) for k, v in dict(biome=key[0], band=key[1], kind=label, n=len(rs), x=wx, y=pick[1] + Y0, z=wz, open=pick[3],
                                      ground=pick[6], yaw=yaw, view=run, under_biome=pick[4]).items())))
main()
