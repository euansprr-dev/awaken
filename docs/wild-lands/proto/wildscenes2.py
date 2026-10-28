"""Mock-ups for the Wild Lands plan with the proposed flora (flora.py): tamed, wild, deep wild, and a hunter's view
through the rye. Drawn with the Soft Land plan's planning renderer (docs/terrain-immersion/proof); animals are plain box
stand-ins, not the game's models. Run: python3 -I wildscenes2.py <out_dir> [tamed|wild|deep|hunter]"""
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
P = os.path.join(HERE, '..', '..', 'terrain-immersion', 'proof')
sys.path.insert(0, P)
sys.path.insert(0, HERE)
import scenes as sc  # noqa: E402
from scenes import *  # noqa: E402,F401
from voxel_render import BOX, CROSS, CUT, FULL, render  # noqa: E402
import flora as fl  # noqa: E402

sc.OUT = sys.argv[1]
A, D, TEX = sc.A, sc.D, sc.TEX
for n in ['brown_wool', 'white_wool', 'black_wool', 'gray_wool', 'light_gray_wool', 'bone_block_side', 'oak_planks',
          'brown_terracotta', 'farmland', 'wheat_stage7', 'dark_oak_log', 'dark_oak_log_top', 'mossy_cobblestone']:
    TEX[n] = A.load(n)
TEX['oak_dark'] = A.load('oak_leaves', (0.28, 0.48, 0.20), 'oak_dark')

# grass tints: lawn, plain, warm wild, deep, tawny
TINTS = [(0.46, 0.74, 0.36), (0.50, 0.74, 0.34), (0.64, 0.74, 0.32), (0.44, 0.64, 0.28), (0.74, 0.70, 0.36)]
SPR = {
    'meadow': [fl.meadow_grass(1)], 'meadow2': [fl.meadow_grass(9)],
    'long': [fl.long_grass(0, seed=2), fl.long_grass(1, 0.2, seed=2)],
    'long_seed': [fl.long_grass(0, seed=5), fl.long_grass(1, 0.9, seed=5)],
    'rye': [fl.wild_rye(0), fl.wild_rye(1), fl.wild_rye(2)],
    'rye2': [fl.wild_rye(0, seed=8), fl.wild_rye(1, seed=8), fl.wild_rye(2, seed=8)],
    'parsley': [fl.cow_parsley(0), fl.cow_parsley(1)], 'foxglove': [fl.foxglove(0), fl.foxglove(1)],
    'thistle': [fl.thistle()], 'bramble': [fl.bramble()], 'bracken': [fl.bracken(0), fl.bracken(1)],
    'bracken_au': [fl.bracken(0, autumn=True), fl.bracken(1, autumn=True)], 'reeds': [fl.reeds(0), fl.reeds(1)],
    'heather': [fl.heather()], 'gorse': [fl.gorse()],
}
FLAT = {'clover': fl.clover(), 'trampled': fl.trampled(), 'trampled_z': fl.trampled(axis=1, seed=7)}
C = {}


def tex(key, sprite, ti):
    k = '%s_%d' % (key, ti)
    if k not in TEX:
        TEX[k] = A.add(k, fl.compose(sprite, TINTS[ti]))
    return TEX[k]


def cross_id(key, part, ti, h=1.0):
    k = ('x', key, part, ti, h)
    if k not in C:
        C[k] = D.add(CROSS, [tex('%s%d' % (key, part), SPR[key][part], ti)] * 6, box=[0, 0, 0, 1, h, 1], shadow=0, solid=False)
    return C[k]


def flat_id(key, ti):
    k = ('f', key, ti)
    if k not in C:
        C[k] = D.add(BOX, [tex(key, FLAT[key], ti)] * 6, box=[0, 0, 0, 1, 1 / 16, 1], shadow=0)
    return C[k]


def box(name, texname, b, shadow=0.5):
    if name not in C:
        C[name] = D.add(BOX, [TEX[texname]] * 6, box=b, shadow=shadow)
    return C[name]


def cut(name, t):
    if name not in C:
        C[name] = D.add(CUT, [t] * 6, shadow=0.55, solid=True)
    return C[name]


HAW = A.add('hawthorn', fl.compose(fl.hawthorn_leaves(), (0.40, 0.60, 0.26)))
FUNGUS = A.add('fungus', fl.compose(fl.bracket_fungus(), (1, 1, 1)))
EXTRA = {}


def put_x(x, y, z, rid):
    EXTRA[(x, y, z)] = rid


def ground_y(h, x, z):
    return int(h[x, z]) + 1


def plant(S, h, x, z, key, ti):
    if not (0 <= x < S.X and 0 <= z < S.Z):
        return
    y = ground_y(h, x, z)
    for i in range(len(SPR[key])):
        put_x(x, y + i, z, cross_id(key, i, ti))


def flat(S, h, x, z, key, ti):
    if 0 <= x < S.X and 0 <= z < S.Z:
        put_x(x, ground_y(h, x, z), z, flat_id(key, ti))


def animal(S, h, x, z, kind, along_x=True):
    """A stand-in beast: legs, a body, a head. kind: cow (aurochs), sheep, wolf, alpha."""
    if not (3 <= x < S.X - 4 and 3 <= z < S.Z - 4):
        return
    y = ground_y(h, x, z)
    wool = {'cow': 'brown_wool', 'sheep': 'white_wool', 'wolf': 'gray_wool', 'alpha': 'black_wool'}[kind]
    L = {'cow': 2, 'sheep': 1, 'wolf': 1, 'alpha': 3}[kind]
    for k in range(L):
        cx, cz = (x + k, z) if along_x else (x, z + k)
        if kind != 'wolf':
            put_x(cx, y, cz, box('legs_' + kind + str(along_x), 'brown_terracotta' if kind != 'sheep' else 'light_gray_wool',
                                 [0.12, 0, 0.3, 0.88, 1, 0.7] if along_x else [0.3, 0, 0.12, 0.7, 1, 0.88]))
            hb = 0.9 if kind == 'alpha' else 0.65
            put_x(cx, y + 1, cz, box('body_' + kind + str(along_x), wool,
                                     [0, 0, 0.08, 1, hb, 0.92] if along_x else [0.08, 0, 0, 0.92, hb, 1]))
        else:
            put_x(cx, y, cz, box('body_wolf' + str(along_x), wool, [0, 0.3, 0.3, 1, 0.85, 0.7] if along_x else [0.3, 0.3, 0, 0.7, 0.85, 1]))
    hx, hz = (x + L, z) if along_x else (x, z + L)
    hy = y + (1 if kind != 'wolf' else 0)
    put_x(hx, hy, hz, box('head_' + kind + str(along_x), wool if kind != 'cow' else 'brown_terracotta',
                          [0, 0.15, 0.3, 0.5, 0.8, 0.7] if along_x else [0.3, 0.15, 0, 0.7, 0.8, 0.5]))
    if kind == 'alpha':
        put_x(hx, hy + 1, hz, box('horn' + str(along_x), 'bone_block_side', [0, 0, 0.1, 0.3, 0.45, 0.9] if along_x else [0.1, 0, 0, 0.9, 0.45, 0.3]))


def great_oak(S, h, x, z, rs, trunk=12, r=7):
    """An old giant: a 2x2 trunk with a root flare, limbs out to a broad crown."""
    y = ground_y(h, x, z)
    for k in range(trunk):
        for dx in (0, 1):
            for dz in (0, 1):
                S.put(x + dx, y + k, z + dz, LOGY)
    for dx, dz in ((-1, 0), (2, 1), (0, -1), (1, 2), (-1, 1), (2, 0)):      # root flare
        S.put(x + dx, y, z + dz, LOGY)
    for arm in range(6):
        a = arm * 1.05 + rs.random() * 0.5
        ln = int(rs.integers(4, 7))
        for s in range(ln):
            S.put(int(round(x + .5 + np.cos(a) * s)), y + trunk - 4 + s // 2, int(round(z + .5 + np.sin(a) * s)), LOGY)
    cy = y + trunk
    for dx in range(-r - 1, r + 3):
        for dz in range(-r - 1, r + 3):
            for dy in range(-3, 4):
                d2 = (dx - .5) ** 2 + (dz - .5) ** 2 + (dy * 1.8) ** 2
                if d2 <= r * r and (d2 < r * r - 5 or rs.random() < 0.5):
                    xx, yy, zz = x + dx, cy + dy, z + dz
                    if 0 <= xx < S.X and 0 <= zz < S.Z and yy < S.Y and S.g[xx, yy, zz] == AIR_:
                        S.put(xx, yy, zz, LEAVES)


def deadfall(S, h, x, z, L, along_x=True, fungus=True):
    for k in range(L):
        xx, zz = (x + k, z) if along_x else (x, z + k)
        if 0 <= xx < S.X and 0 <= zz < S.Z:
            y = ground_y(h, xx, zz)
            S.put(xx, y, zz, LOGX if along_x else LOGZ)
            if fungus and k % 3 == 1:
                put_x(xx + (0 if along_x else 1), y, zz + (1 if along_x else 0),
                      box('fungus' + str(along_x), 'oak_planks', [0.1, 0.35, 0, 0.9, 0.5, 0.25] if along_x else [0, 0.35, 0.1, 0.25, 0.5, 0.9], 0.3))


def hedge(S, h, x0, x1, z, rs):
    """A hawthorn hedgerow along a field edge, one or two blocks high, ragged."""
    for x in range(x0, x1):
        zz = z + (1 if rs.random() < 0.15 else 0)
        y = ground_y(h, x, zz)
        put_x(x, y, zz, cut('haw', HAW))
        if rs.random() < 0.7:
            put_x(x, y + 1, zz, cut('haw', HAW))
        if rs.random() < 0.2:
            put_x(x, y + 2, zz, cut('haw', HAW))


def thicket(S, h, x, z, r, rs, key='bramble'):
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            if dx * dx + dz * dz <= r * r + 1 and rs.random() < 0.85:
                plant(S, h, x + dx, z + dz, key, 3)
                if dx * dx + dz * dz < (r - 1) ** 2 and 0 <= x + dx < S.X and 0 <= z + dz < S.Z:
                    put_x(x + dx, ground_y(h, x + dx, z + dz) + 1, z + dz, cross_id(key, 0, 3))


def terrain(W):
    big = value_noise(W, W, 30, 4)
    small = value_noise(W, W, 11, 6)
    h = (big * 9 + small * 3).astype(int)
    h -= h.min()
    h += 3
    return h


GRAZED = lambda x, z: ((x - 44) / 22.0) ** 2 + ((z - 32) / 14.0) ** 2 < 1


def field(S, h, rs, W, stage, skip):
    """The grass layer: what grows where, by stage (0 tamed, 1 wild, 2 deep)."""
    sw = value_noise(W, W, 9, 21 + stage)      # swathes
    pat = value_noise(W, W, 6, 70 + stage)     # small patches
    for x in range(W):
        for z in range(W):
            if skip(x, z) or (x, ground_y(h, x, z), z) in EXTRA:
                continue
            r = rs.random()
            s, p = sw[x, z], pat[x, z]
            if stage == 0:
                if p > 0.62 and r < 0.8:
                    flat(S, h, x, z, 'clover', 0)
                elif r < 0.10:
                    plant(S, h, x, z, 'meadow', 0)
            elif stage == 1:
                ti = 2 if p > 0.58 else 1
                if ((x - 46) / 16.0) ** 2 + ((z - 34) / 10.0) ** 2 < 1:      # the herd's grazing lawn
                    if r < 0.3:
                        plant(S, h, x, z, 'meadow', ti)
                    continue
                if s > 0.55 and r < 0.75:
                    plant(S, h, x, z, 'long_seed' if p > 0.6 else 'long', ti)
                elif r < 0.55:
                    plant(S, h, x, z, 'meadow' if r < 0.3 else 'meadow2', ti)
                elif r < 0.575:
                    plant(S, h, x, z, 'thistle', ti)
            else:
                ti = 4 if p > 0.64 else 3 if p < 0.4 else 2
                if GRAZED(x, z):
                    if r < 0.35:
                        plant(S, h, x, z, 'meadow' if r < 0.2 else 'meadow2', 3)
                    elif r < 0.37:
                        plant(S, h, x, z, 'thistle', 3)
                    continue
                if s > 0.5 and r < 0.8:
                    plant(S, h, x, z, 'rye' if r < 0.5 else 'rye2', ti)
                elif r < 0.7:
                    plant(S, h, x, z, 'long_seed' if p > 0.5 else 'long', ti)
                elif r < 0.85:
                    plant(S, h, x, z, 'meadow2', ti)


def build(stage, W=88):
    EXTRA.clear()
    h = terrain(W)
    S = Scene(W, int(h.max()) + 30, W, seed=11)
    rs = np.random.default_rng(5 + stage)
    fill_ground(S, h, tint_fn=lambda x, z: 1)
    skip = lambda x, z: False
    if stage == 0:
        # tamed: a lane, a hawthorn hedge, a wheat strip, clover lawn, a small flock and two cows
        lane = {}
        for x in range(W):
            z = 30 + int(3 * np.sin(x / 14))
            lane[x] = z
            for dz in (0, 1, 2):
                S.g[x, int(h[x, z + dz]), z + dz] = PATH
            if x % 3 == 0:
                put_x(x, ground_y(h, x, z + 5), z + 5, box('post', 'oak_planks', [0.4, 0, 0.4, 0.6, 1, 0.6]))
            else:
                put_x(x, ground_y(h, x, z + 5), z + 5, box('rail', 'oak_planks', [0, 0.55, 0.44, 1, 0.75, 0.56]))
        hedge(S, h, 0, W, 9, rs)
        for x in range(10, 44):
            for z in range(12, 24):
                y = int(h[x, z])
                S.g[x, y, z] = DIRT
                if 'wheat' not in C:
                    C['wheat'] = D.add(CROSS, [TEX['wheat_stage7']] * 6, box=[0, 0, 0, 1, 0.9, 1], shadow=0, solid=False)
                put_x(x, y + 1, z, C['wheat'])
        for i, (x, z) in enumerate([(50, 42), (53, 39), (47, 45), (56, 44)]):
            animal(S, h, x, z, 'sheep', i % 2 == 0)
        for (x, z) in [(64, 48), (60, 52)]:
            animal(S, h, x, z, 'cow', True)
        for (x, z) in [(22, 68), (74, 74)]:
            oak(S, x, ground_y(h, x, z), z, rs, trunk=5, r=3)
        skip = lambda x, z: (10 <= x < 44 and 12 <= z < 24) or abs(z - lane.get(x, -99) - 1) <= 1
        field(S, h, rs, W, 0, skip)
    elif stage == 1:
        # the wild, pasture side: an old hedge gone shaggy with cow parsley at its foot, long grass, foxgloves, a thicket
        hedge(S, h, 0, 60, 62, rs)
        for x in range(0, 60):
            if rs.random() < 0.6:
                plant(S, h, x, 60 - int(rs.integers(0, 2)), 'parsley', 1)
        for (x, z) in [(30, 12), (33, 14), (31, 16), (58, 12), (60, 15), (27, 18)]:
            plant(S, h, x, z, 'foxglove', 1)
        thicket(S, h, 18, 30, 3, rs)
        thicket(S, h, 66, 22, 2, rs)
        deadfall(S, h, 30, 52, 7, True)
        great_oak(S, h, 70, 70, rs, trunk=10, r=6)
        herd = [(40, 32), (44, 29), (47, 34), (42, 38), (50, 31), (37, 27), (52, 37), (46, 40), (55, 33)]
        for i, (x, z) in enumerate(herd):
            animal(S, h, x, z, 'cow', i % 3 != 0)
        field(S, h, rs, W, 1, skip)
    else:
        # deep wild: rye and seeding grass in swathes, a herd with its bull and the trail it pressed, bracken, deadfall,
        # two old giants, wolves at the edge, a kill site
        trail = set()
        for t in range(0, W):
            x = int(44 + 10 * np.sin(t / 16))
            for dx in (-1, 0, 1):
                trail.add((x + dx, t))
                if 0 <= x + dx < W:
                    flat(S, h, x + dx, t, 'trampled_z', 3)
        for (x, z) in [(10, 60), (14, 64), (8, 70), (18, 58), (12, 74), (20, 66)]:
            plant(S, h, x, z, 'bracken', 3)
        for (x, z) in [(78, 58), (80, 62), (76, 66), (62, 54), (60, 57), (64, 58)]:
            plant(S, h, x, z, 'bracken_au', 3)
        deadfall(S, h, 24, 46, 8, True)
        deadfall(S, h, 66, 30, 6, False)
        great_oak(S, h, 72, 74, rs, trunk=13, r=8)
        great_oak(S, h, 12, 80, rs, trunk=14, r=7)
        thicket(S, h, 68, 48, 3, rs)
        herd = [(38, 30), (42, 27), (45, 32), (40, 36), (48, 29), (35, 25), (50, 35), (44, 38), (53, 31),
                (56, 26), (32, 33), (47, 41), (58, 36), (29, 28), (39, 42), (53, 40), (61, 30), (26, 36)]
        for i, (x, z) in enumerate(herd):
            animal(S, h, x, z, 'cow', i % 3 != 0)
        animal(S, h, 43, 22, 'alpha', True)
        for (x, z) in [(12, 38), (9, 41), (15, 43)]:
            animal(S, h, x, z, 'wolf', True)
        for (x, z) in [(20, 34), (21, 35), (19, 36)]:
            put_x(x, ground_y(h, x, z), z, box('bone', 'bone_block_side', [0.1, 0, 0.3, 0.9, 0.25, 0.7]))
        skip = lambda x, z: (x, z) in trail
        field(S, h, rs, W, 2, skip)
    return S, h


def draw_stage(stage, name, cam=None, look=None, size=(1120, 630), fov=68, clear=0.0):
    S, h = build(stage)
    blk = sc.bake(S)
    for (x, y, z), rid in EXTRA.items():
        if clear and (x + .5 - cam[0]) ** 2 + (z + .5 - cam[2]) ** 2 < clear * clear:
            continue                                     # nothing at the lens
        if 0 <= x < S.X and 0 <= y < S.Y and 0 <= z < S.Z and S.g[x, y, z] not in GROUND:
            blk[x, y, z] = rid
    cam = cam or (44, h[44, 4] + 9.5, 2)
    look = look or (46, h[46, 40] + 0.5, 40)
    img = render(blk, D, A, cam, look, fov=fov, size=size, fog=300, sun=(0.5, 0.62, -0.6))
    sc.to_png(img, name)
    print('done', name, flush=True)


if __name__ == '__main__':
    which = sys.argv[2] if len(sys.argv) > 2 else 'all'
    h = terrain(88)
    if which in ('all', 'tamed'):
        draw_stage(0, 'flora_tamed.png')
    if which in ('all', 'wild'):
        draw_stage(1, 'flora_wild.png', cam=(44, h[44, 4] + 14, 0), look=(46, h[46, 36] + 0.5, 36))
    if which in ('all', 'deep'):
        draw_stage(2, 'flora_deep.png', cam=(44, h[44, 4] + 13, 0), look=(46, h[46, 36] + 0.5, 36))
    if which in ('all', 'hunter'):
        # crouched in the rye at the field's south edge, eye just over the heads, looking at the herd
        draw_stage(2, 'flora_hunter.png', cam=(40.5, h[40, 16] + 2.62, 16.5), look=(45, h[45, 30] + 1.8, 30), fov=64, clear=1.6)
