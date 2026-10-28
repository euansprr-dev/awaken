"""Illustrative mock-ups for the wild lands plan: the same patch of land as tamed, wild and deep wild. Drawn with the Soft Land
plan's planning renderer (docs/terrain-immersion/proof); animals are simple box stand-ins, not the game's models."""
import sys, os
import numpy as np
P = '/Volumes/MEDIA/Awaken/.claude/worktrees/wild-lands-plan-bc5271/docs/terrain-immersion/proof'
sys.path.insert(0, P)
import scenes as sc
from scenes import *
from voxel_render import BOX, CROSS, CUT, FULL
sc.OUT = sys.argv[1]
A, D, TEX = sc.A, sc.D, sc.TEX
for n in ['brown_wool', 'white_wool', 'black_wool', 'gray_wool', 'light_gray_wool', 'bone_block_side', 'oak_planks', 'brown_terracotta',
          'farmland', 'wheat_stage7', 'sweet_berry_bush_stage3', 'hay_block_side']:
    TEX[n] = A.load(n)
TEX['azalea'] = A.load('azalea_leaves', (0.95, 1.0, 0.9), 'azalea')
TEX['oak_dark'] = A.load('oak_leaves', (0.28, 0.48, 0.20), 'oak_dark')
C = {}
def box(name, tex, b, shadow=0.5):
    if name not in C: C[name] = D.add(BOX, [TEX[tex]] * 6, box=b, shadow=shadow)
    return C[name]
def cross(name, tex, h=1.0):
    if name not in C: C[name] = D.add(CROSS, [TEX[tex]] * 6, box=[0, 0, 0, 1, h, 1], shadow=0, solid=False)
    return C[name]
def cut(name, tex):
    if name not in C: C[name] = D.add(CUT, [TEX[tex]] * 6, shadow=0.55, solid=True)
    return C[name]
EXTRA = {}   # (x, y, z) -> render id, laid over the baked blocks
def put_x(x, y, z, rid):
    EXTRA[(x, y, z)] = rid
def animal(S, h, x, z, kind, along_x=True, big=1):
    """A stand-in beast: legs, a body, a head. kind: cow (aurochs), sheep, wolf."""
    if not (3 <= x < S.X - 4 and 3 <= z < S.Z - 4): return
    y = int(h[x, z]) + 1
    wool = {'cow': 'brown_wool', 'sheep': 'white_wool', 'wolf': 'gray_wool', 'alpha': 'black_wool'}[kind]
    L = {'cow': 2, 'sheep': 1, 'wolf': 1, 'alpha': 3}[kind]
    for k in range(L):
        cx, cz = (x + k, z) if along_x else (x, z + k)
        if kind in ('cow', 'alpha', 'sheep'):
            put_x(cx, y, cz, box('legs_' + kind + str(along_x), 'brown_terracotta' if kind != 'sheep' else 'light_gray_wool',
                                 [0.12, 0, 0.3, 0.88, 1, 0.7] if along_x else [0.3, 0, 0.12, 0.7, 1, 0.88]))
            put_x(cx, y + 1, cz, box('body_' + kind + str(along_x), wool, [0, 0, 0.08, 1, 0.65 if kind != 'alpha' else 0.9, 0.92] if along_x else [0.08, 0, 0, 0.92, 0.65 if kind != 'alpha' else 0.9, 1]))
        else:
            put_x(cx, y, cz, box('body_wolf' + str(along_x), wool, [0, 0.3, 0.3, 1, 0.85, 0.7] if along_x else [0.3, 0.3, 0, 0.7, 0.85, 1]))
    hx, hz = (x + L, z) if along_x else (x, z + L)
    hy = y + (1 if kind != 'wolf' else 0)
    put_x(hx, hy, hz, box('head_' + kind + str(along_x), wool if kind != 'cow' else 'brown_terracotta',
                          [0, 0.15, 0.3, 0.5, 0.8, 0.7] if along_x else [0.3, 0.15, 0, 0.7, 0.8, 0.5]))
    if kind == 'alpha':
        put_x(hx, hy + 1, hz, box('horn' + str(along_x), 'bone_block_side', [0, 0, 0.1, 0.3, 0.45, 0.9] if along_x else [0.1, 0, 0, 0.9, 0.45, 0.3]))
def bush_mound(S, h, x, z, r, rs, tex='oak_dark'):
    """A big bush: a mound of leaves over a stub, 2-5 across, 1-3 high."""
    if not (r + 1 <= x < S.X - r - 1 and r + 1 <= z < S.Z - r - 1): return
    y = int(h[x, z]) + 1
    S.put(x, y, z, LOGY)
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            for dy in range(0, r + 1):
                d2 = dx * dx + dz * dz + (dy * 1.5) ** 2
                if d2 <= r * r + 0.6 and rs.random() < (0.95 if d2 < r * r - 1 else 0.6):
                    yy = int(h[x + dx, z + dz]) + 1 + dy
                    if S.g[x + dx, yy, z + dz] not in GROUND: put_x(x + dx, yy, z + dz, cut('bush_' + tex, tex))
def big_oak(S, h, x, z, rs, trunk=11, r=6):
    y = int(h[x, z]) + 1
    for k in range(trunk):
        for dx in (0, 1):
            for dz in (0, 1): S.put(x + dx, y + k, z + dz, LOGY)
    for arm in range(5):   # limbs out to the crown
        a = rs.random() * 6.28; ln = int(rs.integers(3, 5))
        for s in range(ln): S.put(int(x + np.cos(a) * s), y + trunk - 3 + s // 2, int(z + np.sin(a) * s), LOGY)
    cy = y + trunk
    for dx in range(-r, r + 2):
        for dz in range(-r, r + 2):
            for dy in range(-3, 4):
                d2 = (dx - .5) ** 2 + (dz - .5) ** 2 + (dy * 1.7) ** 2
                if d2 <= r * r and (d2 < r * r - 4 or rs.random() < 0.55):
                    xx, yy, zz = x + dx, cy + dy, z + dz
                    if 0 <= xx < S.X and 0 <= zz < S.Z and yy < S.Y and S.g[xx, yy, zz] == AIR_: S.put(xx, yy, zz, LEAVES)
def fallen_log(S, h, x, z, L, along_x=True):
    for k in range(L):
        xx, zz = (x + k, z) if along_x else (x, z + k)
        if 0 <= xx < S.X and 0 <= zz < S.Z: S.put(xx, int(h[xx, zz]) + 1, zz, LOGX if along_x else LOGZ)
def terrain(W=88):
    big = value_noise(W, W, 30, 4); small = value_noise(W, W, 11, 6)
    h = (big * 9 + small * 3).astype(int); h -= h.min(); h += 3
    return h
def build(stage, W=88):
    EXTRA.clear()
    h = terrain(W)
    S = Scene(W, int(h.max()) + 30, W, seed=11)
    rs = np.random.default_rng(5 + stage)
    tn = value_noise(W, W, 13, 40)
    tint_wild = lambda x, z: 1 if stage == 0 else (2 if tn[x, z] > 0.62 else 0 if tn[x, z] < 0.4 else 1)
    fill_ground(S, h, tint_fn=(lambda x, z: 1) if stage == 0 else tint_wild)
    sw = value_noise(W, W, 9, 21 + stage)           # swathes of long grass
    if stage == 0:
        # tamed: a lane, a fence, a wheat strip, short mown grass, a few flowers, a small flock
        for x in range(W):
            z = 30 + int(3 * np.sin(x / 14))
            for dz in (0, 1, 2): S.g[x, int(h[x, z + dz]), z + dz] = PATH
            if x % 3 == 0: put_x(x, int(h[x, z + 5]) + 1, z + 5, box('post', 'oak_planks', [0.4, 0, 0.4, 0.6, 1, 0.6]))
            else: put_x(x, int(h[x, z + 5]) + 1, z + 5, box('rail', 'oak_planks', [0, 0.55, 0.44, 1, 0.75, 0.56]))
        for x in range(10, 40):
            for z in range(10, 22):
                y = int(h[x, z]); S.g[x, y, z] = DIRT
                put_x(x, y + 1, z, cross('wheat', 'wheat_stage7', 0.9))
        sprinkle_plants(S, h, 0.16, rs, codes=((P_SHORT_LOW, 3), (P_SHORT, 1)), skip=lambda x, z: 10 <= x < 40 and 10 <= z < 22)
        sprinkle_plants(S, h, 0.03, rs, codes=((P_DAND, 2), (P_POPPY, 1), (P_OXEYE, 1)))
        for i, (x, z) in enumerate([(50, 40), (53, 37), (47, 43), (56, 42)]):
            animal(S, h, x, z, 'sheep', i % 2 == 0)
        for (x, z) in [(62, 46), (58, 50)]: animal(S, h, x, z, 'cow', True)
        for (x, z) in [(20, 66), (72, 74)]: oak(S, x, int(h[x, z]) + 1, z, rs, trunk=5, r=3)
    else:
        deep = stage == 2
        dens = lambda x, z: (0.85 if sw[x, z] > (0.42 if deep else 0.5) else 0.45 if deep else 0.3)
        sprinkle_plants(S, h, dens, rs, codes=((P_TALL_B, 6 if deep else 4), (P_SHORT, 3), (P_FERN, 2 if deep else 1), (P_BUSH, 1)), tint_fn=tint_wild)
        sprinkle_plants(S, h, 0.015, rs, codes=((P_CORN, 1), (P_OXEYE, 1)))
        for _ in range(9 if deep else 5):
            x, z = int(rs.integers(6, W - 6)), int(rs.integers(16, W - 6))
            if 22 <= x <= 66 and 18 <= z <= 46: continue
            bush_mound(S, h, x, z, int(rs.integers(2, 4 if deep else 3)), rs, 'azalea' if rs.random() < 0.3 else 'oak_dark')
            if deep:
                for k in range(6):
                    xx, zz = x + int(rs.integers(-4, 5)), z + int(rs.integers(-4, 5))
                    if 0 <= xx < W and 0 <= zz < W: put_x(xx, int(h[xx, zz]) + 1, zz, cross('berry', 'sweet_berry_bush_stage3', 0.9))
        fallen_log(S, h, 30, 40, 7, True)
        if deep: fallen_log(S, h, 60, 30, 6, False)
        big_oak(S, h, 70, 70, rs, trunk=12 if deep else 9, r=7 if deep else 5)
        if deep: big_oak(S, h, 16, 76, rs, trunk=13, r=7)
        herd = [(38, 30), (42, 27), (45, 32), (40, 36), (48, 29), (35, 25), (50, 35), (44, 38), (53, 31)]
        if deep: herd += [(56, 26), (32, 33), (47, 41), (58, 36), (29, 28), (39, 42), (53, 40), (61, 30), (26, 36), (63, 38)]
        for i, (x, z) in enumerate(herd): animal(S, h, x, z, 'cow', i % 3 != 0)
        if deep:
            animal(S, h, 43, 22, 'alpha', True)
            for (x, z) in [(12, 38), (9, 41), (15, 43)]: animal(S, h, x, z, 'wolf', True)
            for (x, z) in [(22, 34), (23, 35), (21, 36)]: put_x(x, int(h[x, z]) + 1, z, box('bone', 'bone_block_side', [0.1, 0, 0.3, 0.9, 0.25, 0.7]))
    return S, h
def draw_stage(stage, name):
    S, h = build(stage)
    blk = sc.bake(S)
    for (x, y, z), rid in EXTRA.items():
        if 0 <= x < S.X and 0 <= y < S.Y and 0 <= z < S.Z and S.g[x, y, z] not in GROUND:
            blk[x, y, z] = rid
            if y + 1 < S.Y and S.g[x, y + 1, z] == P_TALL_T and (x, y + 1, z) not in EXTRA: blk[x, y + 1, z] = 0
    cam = (44, h[44, 4] + 9.5, 2)
    look = (46, h[46, 40] + 0.5, 40)
    from voxel_render import render
    img = render(blk, D, A, cam, look, fov=68, size=(1120, 630), fog=300, sun=(0.5, 0.62, -0.6))
    sc.to_png(img, name)
    print('done', name, flush=True)
for st, nm in [(0, 'scene_tamed.png'), (1, 'scene_wild.png'), (2, 'scene_deep.png')]:
    if len(sys.argv) < 3 or sys.argv[2] == str(st): draw_stage(st, nm)
