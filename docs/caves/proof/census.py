# Disposable, read-only: what a generated world's caves are made of, against its surface.
# python3 -I census.py <world dir> [max regions]
import sys, struct, zlib, glob, re, collections, json
import numpy as np
sys.path.insert(0, '/Volumes/MEDIA/Awaken/docs/mining/proof')
import nbt

YMIN, H = -64, 384
TERRAIN = ('stone','deepslate','dirt','grass_block','gravel','sand','tuff','granite','diorite','andesite','calcite',
  'clay','_ore','podzol','mycelium','terracotta','basalt','mud','moss_block','snow_block','ice','dripstone_block','obsidian',
  'bedrock','blackstone','amethyst_block','budding','smooth_basalt','rooted_dirt','coarse_dirt','farmland','dirt_path','magma',
  'lode_heart','deepglass','sunshard','frostspar','fault_seam','cobble','raw_','sulfur','cinnabar','shale','peat','hoarfrost','glass')
names, kinds = [], []
index = {}
def nid(n):
    i = index.get(n)
    if i is None:
        i = len(names); index[n] = i; names.append(n)
        s = n.split(':')[1]
        if s in ('air', 'cave_air', 'void_air'): k = 0
        elif s in ('water', 'lava', 'bubble_column'): k = 2
        elif any(w in s for w in TERRAIN) and 'leaves' not in s and 'log' not in s and 'slab' not in s and 'stairs' not in s and 'wall' not in s: k = 1
        else: k = 3   # passable or partial: plants, lichen, vines, slabs, wood, leaves
        kinds.append(k)
    return i

def pname(p):
    if isinstance(p, str): n = p.split('[')[0]
    else: n = p.get('id') or p.get('Name') or p.get('') or next((v for v in p.values() if isinstance(v, str)), 'unknown:?')
    return n if ':' in n else 'minecraft:' + n

def chunks(path):
    data = open(path, 'rb').read()
    for i in range(1024):
        off = int.from_bytes(data[i*4:i*4+3], 'big')
        if off == 0: continue
        o = off * 4096; ln = struct.unpack('>i', data[o:o+4])[0]; comp = data[o+4]
        raw = data[o+5:o+4+ln]
        if comp != 2: continue
        yield i % 32, i // 32, nbt.loadraw(zlib.decompress(raw))

BUILT_WORDS = ('planks', 'bricks', 'ashlar', 'plaster', 'timber', 'lit_window', 'glass_pane', 'tile', 'wool', 'carpet_', 'bookshelf', 'drainpipe', 'thatch')
def BUILT_IDS():
    return np.array([i for i, n in enumerate(names) if any(w in n for w in BUILT_WORDS) and 'moss_carpet' not in n] or [-1], np.int32)

def column(c):
    """(16, H, 16) name ids [x, y, z] and the underground biomes, or None."""
    if 'full' not in c.get('Status', ''): return None
    g = np.zeros((16, H, 16), np.int32)
    air = nid('minecraft:air')
    g[:] = air
    biomes = []
    for s in c.get('sections', []):
        y0 = s['Y'] * 16 - YMIN
        if y0 < 0 or y0 >= H: continue
        bs = s.get('block_states')
        if bs is None: continue
        pal = np.array([nid(pname(p)) for p in bs['palette']], np.int32)
        if 'data' not in bs:
            g[:, y0:y0+16, :] = pal[0]
        else:
            bits = max(4, (len(pal) - 1).bit_length()); per = 64 // bits; mask = (1 << bits) - 1
            longs = np.array(bs['data'], dtype=np.int64).view(np.uint64)
            idx = np.arange(4096); li = idx // per; sh = (idx % per) * bits
            vals = ((longs[li] >> sh.astype(np.uint64)) & np.uint64(mask)).astype(np.int64)
            g[:, y0:y0+16, :] = pal[vals].reshape(16, 16, 16).transpose(2, 0, 1)
        bp = s.get('biomes', {}).get('palette', [])
        biomes.append((s['Y'], [b if isinstance(b, str) else str(b) for b in bp]))
    return g, biomes

def main():
    world = sys.argv[1]; maxr = int(sys.argv[2]) if len(sys.argv) > 2 else 999
    regs = sorted(glob.glob(world + '/dimensions/minecraft/overworld/region/r.*.mca'), key=lambda f: -__import__('os').path.getsize(f))[:maxr]
    wall = collections.Counter(); dress = collections.Counter(); fluid = collections.Counter()
    floor = collections.Counter(); roof = collections.Counter()
    surf_top = collections.Counter(); surf_cover = collections.Counter()
    ub = collections.Counter()
    runs = collections.Counter()   # ceiling height -> cells
    band = collections.Counter(); light = collections.Counter()
    nchunks = 0; cave_cells = 0; under_cells = 0; skipped = [0]
    # chambers: largest connected cave volume per chunk column (a proxy for halls)
    big = []
    for f in regs:
        for cx, cz, c in chunks(f):
            col = column(c)
            if col is None: continue
            g, biomes = col
            nchunks += 1
            K = np.array(kinds, np.int8)[g]
            terr = K == 1
            top = H - 1 - np.argmax(terr[:, ::-1, :], axis=1)        # [x, z]
            has = terr.any(axis=1)
            yy = np.arange(H)[None, :, None]
            under = (yy < (top[:, None, :] - 5)) & has[:, None, :]
            openc = ((K == 0) | (K == 3)) & under
            cave = openc
            built = np.isin(g, BUILT_IDS()).sum()
            if built > 300:
                skipped[0] += 1; continue
            n = int(cave.sum()); cave_cells += n; under_cells += int(under.sum())
            if n:
                ys = np.nonzero(cave)[1] + YMIN
                band['shallows y>=40'] += int((ys >= 40).sum()); band['middle 0..39'] += int(((ys >= 0) & (ys < 40)).sum()); band['deep <0'] += int((ys < 0).sum())
                # dressing inside cave cells
                d = g[cave & (K == 3)]
                for v, k in zip(*np.unique(d, return_counts=True)): dress[names[v]] += int(k)
                # wall faces: terrain neighbours of cave cells (inside the chunk)
                for ax, dd in ((0, 1), (0, -1), (1, 1), (1, -1), (2, 1), (2, -1)):
                    nb = np.roll(g, -dd, axis=ax); nk = np.roll(K, -dd, axis=ax)
                    m = cave & (nk == 1)
                    if ax == 0: m[(15 if dd == 1 else 0), :, :] = False
                    if ax == 2: m[:, :, (15 if dd == 1 else 0)] = False
                    if ax == 1: m[:, (H - 1 if dd == 1 else 0), :] = False
                    vals, cnt = np.unique(nb[m], return_counts=True)
                    tgt = wall
                    for v, k in zip(vals, cnt):
                        wall[names[v]] += int(k)
                        if ax == 1 and dd == -1: floor[names[v]] += int(k)
                        if ax == 1 and dd == 1: roof[names[v]] += int(k)
                # ceiling heights: vertical runs of cave cells
                for x in range(16):
                    for z in range(16):
                        colm = cave[x, :, z]
                        if not colm.any(): continue
                        e = np.diff(np.concatenate(([0], colm.astype(np.int8), [0])))
                        st = np.nonzero(e == 1)[0]; en = np.nonzero(e == -1)[0]
                        for a, b in zip(st, en):
                            L = b - a
                            bucket = '1-2' if L <= 2 else '3-4' if L <= 4 else '5-8' if L <= 8 else '9-16' if L <= 16 else '17-32' if L <= 32 else '33+'
                            runs[bucket] += int(L)
            fl = g[((K == 2)) & under]
            for v, k in zip(*np.unique(fl, return_counts=True)): fluid[names[v]] += int(k)
            # surface: the top terrain block and the cell above it
            xs, zs = np.nonzero(has)
            t = top[xs, zs]
            for v, k in zip(*np.unique(g[xs, t, zs], return_counts=True)): surf_top[names[v]] += int(k)
            above = g[xs, np.minimum(t + 1, H - 1), zs]
            for v, k in zip(*np.unique(above, return_counts=True)): surf_cover[names[v]] += int(k)
            for sy, bp in biomes:
                if (sy * 16 + 15 - YMIN) < (top.min() - 5):
                    for b in bp: ub[b] += 1
    out = dict(skipped_built=skipped[0], chunks=nchunks, cave_cells=cave_cells, under_cells=under_cells, wall=wall.most_common(60), floor=floor.most_common(25),
               roof=roof.most_common(25), dress=dress.most_common(50), fluid=fluid.most_common(), surf_top=surf_top.most_common(40),
               surf_cover=surf_cover.most_common(60), under_biomes=ub.most_common(30), runs=dict(runs), band=dict(band))
    def share(cnt, pred):
        tot = sum(cnt.values()); return round(100 * sum(v for k, v in cnt.items() if pred(k)) / max(1, tot), 2)
    aw = lambda k: k.startswith('awaken:')
    out['awaken_share'] = dict(cave_wall_faces=share(wall, aw), cave_dressing=share(dress, aw), surface_top=share(surf_top, aw),
                               surface_cover=share({k: v for k, v in surf_cover.items() if k != 'minecraft:air'}, aw))
    plain = ('minecraft:stone', 'minecraft:deepslate', 'minecraft:tuff', 'minecraft:granite', 'minecraft:diorite', 'minecraft:andesite', 'minecraft:gravel', 'minecraft:dirt')
    out['plain_rock_wall_share'] = share(wall, lambda k: k in plain)
    out['dressed_cells_per_1000_cave'] = round(1000 * sum(dress.values()) / max(1, cave_cells), 2)
    out['covered_surface_share'] = share(surf_cover, lambda k: k != 'minecraft:air')
    print(json.dumps(out, indent=1))

main()
