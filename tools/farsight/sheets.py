#!/usr/bin/env python3
"""Far trees at the edge of City Sight's chunks, re-drawn offline from a save (SPEC §20, far trees): the real blocks
City Sight holds (its store's chunks) as voxels, the far terrain past them as the sea, and far trees planted as
FarTrees.plant plants them on ring 1's 4-block cells (crown top, the camera-facing crown and trunk sides, FarTrees'
proportions), hidden where City Sight draws the real ones: 'old' face by face by the chunk each pixel lies in (as
before 2026-10-29), 'new' whole by the chunk the trunk stands in. Camera at 12x (the binoculars) from 538 m NW, y 167.
Reads the save only. Writes sheets_old.png and sheets_new.png to the out dir.

    python3 tools/farsight/sheets.py <edge x> <edge z> <out dir> [save folder]
"""
import struct, glob, sys, os
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from world import World

SAVE = sys.argv[4] if len(sys.argv) > 4 else '/Volumes/MEDIA/Awaken-play/run-pack/saves/New World (8)'
w = World(SAVE + '/dimensions/minecraft/overworld/region')
held = set()
for f in glob.glob(SAVE + '/awaken/farsight/voxels/overworld/*.bin'):
    b = open(f, 'rb').read(); n = struct.unpack('>i', b[8:12])[0]; o = 12
    for _ in range(n):
        cx, cz, ln = struct.unpack('>iii', b[o:o + 12]); o += 12 + ln; held.add((cx, cz))

TX, TZ = int(sys.argv[1]), int(sys.argv[2])   # a point on the edge
R = 72
X0, Z0, Y0, Y1 = TX - R, TZ - R, 55, 110
nx, nz, ny = 2 * R, 2 * R, Y1 - Y0
kind = np.zeros((nx, ny, nz), np.uint8)       # 0 air, 1 leaves, 2 log, 3 ground, 4 water
trees = []
for x in range(X0, X0 + nx):
    for z in range(Z0, Z0 + nz):
        if (x >> 4, z >> 4) not in held: continue
        for y in range(Y1 - 1, Y0 - 1, -1):
            b = w.block(x, y, z) or ''
            if b in ('minecraft:air', 'minecraft:cave_air', ''): continue
            k = 1 if 'leaves' in b else 2 if ('log' in b or 'wood' in b or 'stem' in b) else 4 if 'water' in b else 3
            kind[x - X0, y - Y0, z - Z0] = k
            if k in (3, 4) and y < Y1 - 1:
                break
        # a trunk: a log over ground
        col = kind[x - X0, :, z - Z0]
        logs = np.where(col == 2)[0]
        if len(logs) and (col[logs.min() - 1] == 3 if logs.min() > 0 else False):
            g = logs.min() + Y0
            leaves = np.where(col == 1)[0]
            top = (leaves.max() if len(leaves) else logs.max()) + Y0 + 1
            nb = w.block(x, logs.min() + Y0, z) or ''
            shape = 1 if 'spruce' in nb else 2 if 'jungle' in nb else 3 if 'acacia' in nb else 0
            trees.append((x + 0.5, z + 0.5, g, top - g, shape))
# FarTrees.plant over ring 1's 4-block cells, from what the chunks say of each (FarChunk: the canopy's height over the
# ground, the forest's density), two spots a cell, each tree 0.75-1.25 of the canopy, its crown 0.8-1.2 wide
rng = np.random.default_rng(7)
trees = []
ground = np.full((nx, nz), -1); canopy = np.zeros((nx, nz)); wet = np.zeros((nx, nz), bool)
for i in range(nx):
    for k in range(nz):
        c = kind[i, :, k]
        g = np.where((c == 3) | (c == 4))[0]
        if not len(g): continue
        gy = g.max(); ground[i, k] = gy; wet[i, k] = c[gy] == 4
        lv = np.where(c == 1)[0]
        if len(lv) and lv.max() > gy: canopy[i, k] = lv.max() - gy
for ci in range(0, nx, 4):
    for ck in range(0, nz, 4):
        g = ground[ci:ci + 4, ck:ck + 4]
        if (g < 0).any() or wet[ci:ci + 4, ck:ck + 4].any(): continue
        cn = canopy[ci:ci + 4, ck:ck + 4]
        density = (cn > 0).mean()
        if density <= 0: continue
        for _ in range(2):
            if rng.random() >= density * 0.72: continue
            u, v = 0.12 + 0.76 * rng.random(), 0.12 + 0.76 * rng.random()
            tall = max(cn.max(), 4) * (0.75 + 0.5 * rng.random())
            trees.append((X0 + ci + 4 * u, Z0 + ck + 4 * v, g.mean() + Y0 + 1, tall, 0, 0.8 + 0.4 * rng.random()))
print(len(held), 'chunks held;', len(trees), 'far trees planted near the edge')


def crown_width(shape, tall):
    return [1.2 + 0.55 * tall, 2.5 + 0.25 * tall, 3 + 0.3 * tall, 7.0][shape]


def crown_base(shape, tall):
    return [tall - min(0.62 * tall, 2.5 + 0.22 * tall), tall * 0.18, tall * 0.62, tall * 0.68][shape]


W_, H_ = 800, 450
fov = np.radians(70.0 / 12.0)
cam = np.array([TX - 380.0, 167.0, TZ - 380.0])
tgt = np.array([TX, 72.0, TZ])
f = tgt - cam; f /= np.linalg.norm(f)
r = np.cross(f, [0, 1, 0]); r /= np.linalg.norm(r); u = np.cross(r, f)
th = np.tan(fov / 2)
px = (np.arange(W_) + 0.5) / W_ * 2 - 1; py = 1 - (np.arange(H_) + 0.5) / H_ * 2
D = f[None, None] + px[None, :, None] * th * W_ / H_ * r + py[:, None, None] * th * u
D /= np.linalg.norm(D, axis=-1, keepdims=True)


def heldmask(x, z):
    cx = np.floor(x / 16).astype(int); cz = np.floor(z / 16).astype(int)
    out = np.zeros(x.shape, bool)
    for (a, b) in held:
        out |= (cx == a) & (cz == b)
    return out


# real blocks: march
depth = np.full((H_, W_), np.inf); col = np.zeros((H_, W_, 3))
PAL = {1: (52, 66, 30), 2: (60, 44, 30), 3: (40, 52, 30), 4: (14, 22, 70)}
tnear = np.linalg.norm(tgt - cam) - 160; tfar = tnear + 330
for t in np.arange(tnear, tfar, 0.35):
    p = cam + D * t
    i = np.floor(p[..., 0]).astype(int) - X0; j = np.floor(p[..., 1]).astype(int) - Y0; k = np.floor(p[..., 2]).astype(int) - Z0
    ok = (i >= 0) & (i < nx) & (j >= 0) & (j < ny) & (k >= 0) & (k < nz) & np.isinf(depth)
    kk = np.zeros((H_, W_), np.uint8); kk[ok] = kind[i[ok], j[ok], k[ok]]
    hit = kk > 0
    for v, c in PAL.items():
        m = hit & (kk == v)
        # each block its own shade
        g = ((i * 73856093 ^ j * 19349663 ^ k * 83492791) & 255) / 255.0
        col[m] = np.array(c)[None] * (0.8 + 0.4 * g[m])[:, None]
    depth[hit] = t
# the far terrain past the held chunks: the sea
ts = (62.9 - cam[1]) / D[..., 1]
sp = cam + D * ts[..., None]
sea = (ts > 0) & (ts < depth) & ~heldmask(sp[..., 0], sp[..., 2])
col[sea] = (12, 20, 66); depth[sea] = ts[sea]
base_col, base_depth = col.copy(), depth.copy()


def quad(o, ea, eb):
    """ray hits on the parallelogram o + a ea + b eb, a, b in [0, 1]: t (inf where missed)"""
    n = np.cross(ea, eb)
    den = D @ n
    t = ((o - cam) @ n) / np.where(np.abs(den) < 1e-9, 1e-9, den)
    p = cam + D * t[..., None] - o
    # solve p = a ea + b eb
    m = np.array([[ea @ ea, ea @ eb], [ea @ eb, eb @ eb]]); inv = np.linalg.inv(m)
    a = (p @ ea) * inv[0, 0] + (p @ eb) * inv[0, 1]; b = (p @ ea) * inv[1, 0] + (p @ eb) * inv[1, 1]
    ok = (t > 0) & (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
    return np.where(ok, t, np.inf), p


for mode in ('old', 'new'):
    col, depth = base_col.copy(), base_depth.copy()
    for (x, z, g, tall, shape, wide) in trees:
        if mode == 'new' and (int(np.floor(x / 16)), int(np.floor(z / 16))) in held: continue
        half = crown_width(shape, tall) * wide / 2; tip = 0.5 if shape == 1 else half
        cb, ct = crown_base(shape, tall), (crown_base(shape, tall) + 2 if shape == 3 else tall)
        sx = -1.0 if x - cam[0] > 0 else 1.0; sz = -1.0 if z - cam[2] > 0 else 1.0
        quads = [  # crown top, crown sides, trunk sides
            (np.array([x - tip, g + ct, z - tip]), np.array([2 * tip, 0, 0]), np.array([0, 0, 2 * tip]), 1.0, (78, 112, 28)),
            (np.array([x + sx * half, g + cb, z - half]), np.array([0, 0, 2 * half]), np.array([0, ct - cb, 0]), 0.6, (78, 112, 28)),
            (np.array([x - half, g + cb, z + sz * half]), np.array([2 * half, 0, 0]), np.array([0, ct - cb, 0]), 0.8, (78, 112, 28)),
            (np.array([x + sx * 0.5, g - 0.5, z - 0.5]), np.array([0, 0, 1.0]), np.array([0, cb + 1.5, 0]), 0.6, (70, 52, 34)),
            (np.array([x - 0.5, g - 0.5, z + sz * 0.5]), np.array([1.0, 0, 0]), np.array([0, cb + 1.5, 0]), 0.8, (70, 52, 34)),
        ]
        for o, ea, eb, shade, c in quads:
            if half <= 0: continue
            t, p = quad(o, ea, eb)
            q = cam + D * t[..., None]
            if mode == 'old':
                t = np.where(np.isfinite(t) & heldmask(q[..., 0], q[..., 2]), np.inf, t)
            m = t < depth
            if not m.any(): continue
            gr = ((np.floor(q[..., 0]).astype(int) * 7 + np.floor(q[..., 1]).astype(int) * 13 + np.floor(q[..., 2]).astype(int) * 31) % 17) / 16.0
            col[m] = np.array(c)[None] * shade * (0.85 + 0.3 * gr[m])[:, None]
            depth[m] = t[m]
    Image.fromarray(np.clip(col, 0, 255).astype(np.uint8)).save(os.path.join(sys.argv[3], f'sheets_{mode}.png'))
print('done')
