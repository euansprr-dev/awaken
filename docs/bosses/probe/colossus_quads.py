"""Disposable probe: how many quads does a voxel colossus (ref image: ~48 blocks tall, stepped stone plates) cost?
Each bone is a voxel grid in whole blocks; a rough body (ellipsoid limbs) plus surface bumps (the ref's stepped plates
and spines). Counts visible faces, then greedy-merged rectangles per face plane (what a baked mesh would draw)."""
import numpy as np, sys
rng = np.random.default_rng(7)

def ellipsoid(sx, sy, sz):
    x, y, z = np.mgrid[0:sx, 0:sy, 0:sz]
    cx, cy, cz = (sx-1)/2, (sy-1)/2, (sz-1)/2
    return ((x-cx)/(sx/2))**2 + ((y-cy)/(sy/2))**2 + ((z-cz)/(sz/2))**2 <= 1.0

def bumpy(v, p_bump=0.18, spines=0):
    # stepped plates: add a 1-voxel layer on random surface cells
    pad = np.pad(v, 2)
    surf = pad & ~np.roll(pad, 1, 0) | pad & ~np.roll(pad, -1, 0) | pad & ~np.roll(pad, 1, 2) | pad & ~np.roll(pad, -1, 2)
    out = pad.copy()
    idx = np.argwhere(surf)
    for (x, y, z) in idx[rng.random(len(idx)) < p_bump]:
        for dx, dz in ((1,0),(-1,0),(0,1),(0,-1)):
            if 0 <= x+dx < out.shape[0] and 0 <= z+dz < out.shape[2] and not pad[x+dx, y, z+dz]:
                out[x+dx, y, z+dz] = True
                break
    for _ in range(spines):
        x, y, z = idx[rng.integers(len(idx))]
        for k in range(1, 4):
            if y+k < out.shape[1]: out[x, y+k, z] = True
    return out

def faces(v):
    n = 0; rects = 0
    for axis in range(3):
        for d in (1, -1):
            nb = np.roll(np.pad(v, 1), d, axis)[1:-1,1:-1,1:-1]
            vis = v & ~nb
            n += int(vis.sum())
            # greedy merge per slice (rectangles of same-orientation faces; one material assumed)
            vs = np.moveaxis(vis, axis, 0)
            for sl in vs:
                rects += greedy(sl.copy())
    return n, rects

def greedy(m):
    r = 0
    h, w = m.shape
    for i in range(h):
        j = 0
        while j < w:
            if not m[i, j]: j += 1; continue
            k = j
            while k < w and m[i, k]: k += 1
            ii = i + 1
            while ii < h and m[ii, j:k].all(): ii += 1
            m[i:ii, j:k] = False
            r += 1
            j = k
    return r

bones = {  # name: (size x,y,z), count, bump prob, spines
    "torso": ((16, 18, 10), 1, 0.25, 30), "pelvis": ((12, 6, 8), 1, 0.2, 0), "head": ((7, 9, 7), 1, 0.1, 6),
    "upper_arm": ((6, 15, 6), 2, 0.25, 10), "forearm": ((6, 16, 6), 2, 0.25, 6), "hand": ((7, 6, 6), 2, 0.15, 0),
    "finger": ((2, 6, 2), 8, 0.0, 0), "thigh": ((7, 14, 7), 2, 0.2, 0), "shin": ((7, 15, 7), 2, 0.2, 0), "foot": ((7, 3, 9), 2, 0.1, 0),
}
tot_f = tot_r = 0
for name, (size, cnt, p, sp) in bones.items():
    v = bumpy(ellipsoid(*size), p, sp)
    f, r = faces(v)
    tot_f += f * cnt; tot_r += r * cnt
    print(f"{name:10s} x{cnt}: voxels {int(v.sum()):5d}  visible faces {f:5d}  merged quads {r:5d}")
print(f"TOTAL visible block faces {tot_f}, greedy-merged quads {tot_r}; vertices {tot_r*4}")
