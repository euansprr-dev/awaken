"""
The four before/after scenes for docs/terrain-immersion/PLAN.md, drawn with voxel_render.py.

A scene is built once as *logical* blocks (what the world generator would place), then baked to render ids under a
set of levers, so "before" and "after" differ only by the levers and the content the plan adds:

  wrap     grass wraps down a one-block riser (the seamless grass side)
  fringe   grass creeps onto the paths, dirt, gravel and sand beside it (the seamless edge)
  tint     the grass tint drifts in patches instead of one flat colour
  soft     the half-block slab pass (the same rule as SoftBench.java, checked against its output)

Run: python3 -I docs/terrain-immersion/proof/scenes.py <scene|all> [before|after|ladder] ; pictures go to docs/terrain-immersion/img/.
"""
import os
import sys
import time

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from voxel_render import Atlas, Defs, render, AIR, FULL, BOX, CROSS, CUT, BOXC  # noqa: E402

OUT = os.path.join(HERE, "..", "img")
BIN = os.environ.get("SOFTBENCH_OUT", os.path.join(HERE, "out"))

# ---- logical blocks ------------------------------------------------------------------------------------------------------
(AIR_, GRASS, DIRT, PATH, COARSE, GRAVEL, STONE, COBBLE, MOSSY, ANDESITE, DIORITE, GRANITE, TUFF, CALCITE, SAND, SNOW, MOSS,
 PODZOL, MUD, LOGY, LOGX, LOGZ, LEAVES, SPRUCE_LEAVES, SPRUCE_LOG) = range(25)
SLAB_TURF, SLAB_DIRT, SLAB_STONE, SLAB_GRAVEL, SLAB_COBBLE, SLAB_SNOW, SLAB_SAND, SLAB_COARSE, LAYER_SNOW = range(30, 39)
(P_SHORT, P_TALL_B, P_TALL_T, P_FERN, P_BUSH, P_DAND, P_POPPY, P_CORN, P_OXEYE, P_BROWNM, P_REDM, P_DEAD, LITTER,
 PEBBLE, TWIG, P_SHORT_LOW, P_TALL3_B, P_BLUET) = range(50, 68)
GROUND = {GRASS, DIRT, PATH, COARSE, GRAVEL, STONE, COBBLE, MOSSY, ANDESITE, DIORITE, GRANITE, TUFF, CALCITE, SAND, SNOW, MOSS,
          PODZOL, MUD, LOGY, LOGX, LOGZ, LEAVES, SPRUCE_LEAVES, SPRUCE_LOG}
SLABS = {SLAB_TURF, SLAB_DIRT, SLAB_STONE, SLAB_GRAVEL, SLAB_COBBLE, SLAB_SNOW, SLAB_SAND, SLAB_COARSE}

A = Atlas()
D = Defs()
GT = [(0.40, 0.66, 0.28), (0.50, 0.74, 0.34), (0.62, 0.77, 0.31), (0.45, 0.72, 0.42)]   # deep, plain, sun-dry, cool
LEAF_T = (0.36, 0.60, 0.22)
LITTER_T = [(0.80, 0.52, 0.20), (0.68, 0.40, 0.16), (0.86, 0.62, 0.26)]
TEX = {}
for l, t in enumerate(GT):
    TEX["gtop%d" % l] = A.load("grass_block_top", t, "gtop%d" % l)
    TEX["gside%d" % l] = A.grass_side(t, "gside%d" % l)
    TEX["short%d" % l] = A.load("short_grass", t, "short%d" % l)
    TEX["fern%d" % l] = A.load("fern", t, "fern%d" % l)
    TEX["bush%d" % l] = A.load("bush", t, "bush%d" % l)
    TEX["tallb%d" % l] = A.load("tall_grass_bottom", t, "tallb%d" % l)
    TEX["tallt%d" % l] = A.load("tall_grass_top", t, "tallt%d" % l)
for n in ["dirt", "coarse_dirt", "gravel", "stone", "cobblestone", "mossy_cobblestone", "andesite", "diorite", "granite", "tuff",
          "calcite", "sand", "moss_block", "podzol_top", "podzol_side", "mud", "oak_log", "oak_log_top", "dirt_path_top",
          "dirt_path_side", "snow", "dandelion", "poppy", "cornflower", "oxeye_daisy", "azure_bluet", "brown_mushroom",
          "red_mushroom", "dead_bush", "spruce_log", "spruce_log_top"]:
    TEX[n] = A.load(n)
TEX["oak_leaves"] = A.load("oak_leaves", LEAF_T)
TEX["spruce_leaves"] = A.load("spruce_leaves", (0.30, 0.45, 0.30))
for i, t in enumerate(LITTER_T):
    TEX["litter%d" % i] = A.load("leaf_litter", t, "litter%d" % i)
TEX["moss_t"] = A.load("moss_block", (0.85, 1.0, 0.8), "moss_t")


def _arr(key):
    return A.tex[TEX[key]]


# grass fringe sources for the seamless edge: the grass top itself
def fringe_tex(base, mask, grass, seed):
    out = base.copy()
    rs = np.random.default_rng(seed)
    for bit in range(4):                      # N S E W
        if not (mask >> bit) & 1:
            continue
        for line in range(16):
            depth = 1 + int(rs.integers(0, 4)) if rs.random() < 0.8 else int(rs.integers(0, 2))
            for dep in range(depth):
                keep = 0.92 - 0.2 * dep
                if rs.random() > keep:
                    continue
                if bit == 0:
                    out[dep, line] = grass[dep, line]
                elif bit == 1:
                    out[15 - dep, line] = grass[15 - dep, line]
                elif bit == 2:
                    out[line, 15 - dep] = grass[line, 15 - dep]
                else:
                    out[line, dep] = grass[line, dep]
    return out


_ids = {}


def bid(key, make):
    if key not in _ids:
        _ids[key] = make()
    return _ids[key]


def solid_block(texkey, side=None, bottom=None, tint=None):
    t = TEX[texkey]
    return D.add(FULL, [t, TEX[bottom or texkey], *([TEX[side or texkey]] * 4)], tint)


def grass_id(level, mask):
    def mk():
        top, side, w = TEX["gtop%d" % level], TEX["gside%d" % level], TEX["gtop%d" % level]
        faces = [top, TEX["dirt"]] + [w if (mask >> i) & 1 else side for i in range(4)]
        return D.add(FULL, faces)
    return bid(("grass", level, mask), mk)


def fringed_id(kind, mask, level=1):
    """A ground block whose top has grass creeping in from the sides in mask (N S E W)."""
    def mk():
        base = {"path": _arr("dirt_path_top"), "dirt": _arr("dirt"), "coarse": _arr("coarse_dirt"), "gravel": _arr("gravel"),
                "sand": _arr("sand"), "cobble": _arr("cobblestone"), "stone": _arr("stone"), "podzol": _arr("podzol_top")}[kind]
        side = {"path": "dirt_path_side", "dirt": "dirt", "coarse": "coarse_dirt", "gravel": "gravel", "sand": "sand",
                "cobble": "cobblestone", "stone": "stone", "podzol": "podzol_side"}[kind]
        key = "fr_%s_%d_%d" % (kind, mask, level)
        top = A.add(key, fringe_tex(base, mask, _arr("gtop%d" % level), hash((kind, mask)) & 0xFFFF))
        if kind == "path":
            return D.add(BOX, [top, TEX["dirt"], *([TEX[side]] * 4)], box=[0, 0, 0, 1, 15 / 16, 1])
        return D.add(FULL, [top, TEX["dirt"] if kind != "sand" else TEX["sand"], *([TEX[side]] * 4)])
    return bid(("fr", kind, mask, level), mk)


def plain(code):
    def mk():
        T = TEX
        if code == DIRT:
            return solid_block("dirt")
        if code == PATH:
            return D.add(BOX, [T["dirt_path_top"], T["dirt"], *([T["dirt_path_side"]] * 4)], box=[0, 0, 0, 1, 15 / 16, 1])
        if code == COARSE:
            return solid_block("coarse_dirt")
        if code == GRAVEL:
            return solid_block("gravel")
        if code == STONE:
            return solid_block("stone")
        if code == COBBLE:
            return solid_block("cobblestone")
        if code == MOSSY:
            return solid_block("mossy_cobblestone")
        if code == ANDESITE:
            return solid_block("andesite")
        if code == DIORITE:
            return solid_block("diorite")
        if code == GRANITE:
            return solid_block("granite")
        if code == TUFF:
            return solid_block("tuff")
        if code == CALCITE:
            return solid_block("calcite")
        if code == SAND:
            return solid_block("sand")
        if code == SNOW:
            return solid_block("snow")
        if code == MOSS:
            return solid_block("moss_t")
        if code == PODZOL:
            return D.add(FULL, [T["podzol_top"], T["dirt"], *([T["podzol_side"]] * 4)])
        if code == MUD:
            return solid_block("mud")
        if code == LOGY:
            return D.add(FULL, [T["oak_log_top"], T["oak_log_top"], *([T["oak_log"]] * 4)])
        if code in (LOGX, LOGZ):
            return D.add(FULL, [T["oak_log"], T["oak_log"], *([T["oak_log"]] * 4)])
        if code == SPRUCE_LOG:
            return D.add(FULL, [T["spruce_log_top"], T["spruce_log_top"], *([T["spruce_log"]] * 4)])
        if code == LEAVES:
            return D.add(CUT, [T["oak_leaves"]] * 6, shadow=0.55, solid=True)
        if code == SPRUCE_LEAVES:
            return D.add(CUT, [T["spruce_leaves"]] * 6, shadow=0.55, solid=True)
        if code == SLAB_DIRT:
            return D.add(BOX, [T["dirt"]] * 6, box=[0, 0, 0, 1, .5, 1])
        if code == SLAB_STONE:
            return D.add(BOX, [T["stone"]] * 6, box=[0, 0, 0, 1, .5, 1])
        if code == SLAB_GRAVEL:
            return D.add(BOX, [T["gravel"]] * 6, box=[0, 0, 0, 1, .5, 1])
        if code == SLAB_COBBLE:
            return D.add(BOX, [T["cobblestone"]] * 6, box=[0, 0, 0, 1, .5, 1])
        if code == SLAB_SAND:
            return D.add(BOX, [T["sand"]] * 6, box=[0, 0, 0, 1, .5, 1])
        if code == SLAB_COARSE:
            return D.add(BOX, [T["coarse_dirt"]] * 6, box=[0, 0, 0, 1, .5, 1])
        if code == SLAB_SNOW:
            return D.add(BOX, [T["snow"]] * 6, box=[0, 0, 0, 1, .5, 1])
        if code == LAYER_SNOW:
            return D.add(BOX, [T["snow"]] * 6, box=[0, 0, 0, 1, .25, 1])
        if code == PEBBLE:
            return D.add(BOX, [T["stone"]] * 6, box=[.3, 0, .3, .62, .2, .6], shadow=0)
        if code == TWIG:
            return D.add(BOX, [T["oak_log"]] * 6, box=[.15, 0, .45, .85, .1, .55], shadow=0)
        if code in (P_DAND, P_POPPY, P_CORN, P_OXEYE, P_BLUET):
            n = {P_DAND: "dandelion", P_POPPY: "poppy", P_CORN: "cornflower", P_OXEYE: "oxeye_daisy", P_BLUET: "azure_bluet"}[code]
            return D.add(CROSS, [T[n]] * 6, box=[0, 0, 0, 1, 0.8, 1], shadow=0, solid=False)
        if code in (P_BROWNM, P_REDM):
            n = "brown_mushroom" if code == P_BROWNM else "red_mushroom"
            return D.add(CROSS, [T[n]] * 6, box=[0, 0, 0, 1, 0.5, 1], shadow=0, solid=False)
        if code == P_DEAD:
            return D.add(CROSS, [T["dead_bush"]] * 6, box=[0, 0, 0, 1, 0.8, 1], shadow=0, solid=False)
        raise KeyError(code)
    return bid(("plain", code), mk)


def plant_id(code, level):
    def mk():
        T = TEX
        if code == P_SHORT:
            return D.add(CROSS, [T["short%d" % level]] * 6, box=[0, 0, 0, 1, 0.9, 1], shadow=0, solid=False)
        if code == P_SHORT_LOW:
            return D.add(CROSS, [T["short%d" % level]] * 6, box=[0, 0, 0, 1, 0.5, 1], shadow=0, solid=False)
        if code == P_FERN:
            return D.add(CROSS, [T["fern%d" % level]] * 6, box=[0, 0, 0, 1, 1.0, 1], shadow=0, solid=False)
        if code == P_BUSH:
            return D.add(CROSS, [T["bush%d" % level]] * 6, box=[0, 0, 0, 1, 0.9, 1], shadow=0, solid=False)
        if code in (P_TALL_B, P_TALL3_B):
            return D.add(CROSS, [T["tallb%d" % level]] * 6, box=[0, 0, 0, 1, 1.0, 1], shadow=0, solid=False)
        if code == P_TALL_T:
            return D.add(CROSS, [T["tallt%d" % level]] * 6, box=[0, 0, 0, 1, 1.0, 1], shadow=0, solid=False)
        if code == LITTER:
            return D.add(BOX, [T["litter%d" % (level % 3)]] * 6, box=[0, 0, 0, 1, 1 / 16, 1], shadow=0)
        raise KeyError(code)
    return bid(("plant", code, level), mk)


def turf_slab_id(level, tuft):
    """The turf slab; tuft 0 none, 1 short grass, 2 tall, 3 fern, 4 a flower, 5 bush: standing in the slab's own cell."""
    def mk():
        T = TEX
        faces = [T["gtop%d" % level], T["dirt"], *([T["gside%d" % level]] * 4)]
        if tuft == 0:
            return D.add(BOX, faces, box=[0, 0, 0, 1, .5, 1], anchor_top=True)
        tex, h = {1: (T["short%d" % level], .5), 2: (T["tallb%d" % level], .5), 3: (T["fern%d" % level], .5),
                  4: (T["dandelion"], .45), 5: (T["bush%d" % level], .5)}[tuft]
        return D.add(BOXC, faces, box=[0, 0, 0, 1, .5, 1], anchor_top=True, tuft=(tex, h, .5, (1, 1, 1)))
    return bid(("turf", level, tuft), mk)


# ---- scene container -------------------------------------------------------------------------------------------------------
class Scene:
    def __init__(self, X, Y, Z, seed=1):
        self.g = np.zeros((X, Y, Z), np.int16)
        self.lv = np.ones((X, Y, Z), np.uint8)          # grass/plant tint level
        self.tuft = np.zeros((X, Y, Z), np.uint8)       # what stands on a turf slab
        self.X, self.Y, self.Z = X, Y, Z
        self.rng = np.random.default_rng(seed)

    def put(self, x, y, z, code, lv=None):
        if 0 <= x < self.X and 0 <= y < self.Y and 0 <= z < self.Z:
            self.g[x, y, z] = code
            if lv is not None:
                self.lv[x, y, z] = lv


def value_noise(X, Z, cell, seed):
    rs = np.random.default_rng(seed)
    gx, gz = int(X / cell) + 3, int(Z / cell) + 3
    g = rs.random((gx, gz))
    xs = np.arange(X) / cell
    zs = np.arange(Z) / cell
    x0, z0 = xs.astype(int), zs.astype(int)
    fx, fz = xs - x0, zs - z0
    fx = fx * fx * (3 - 2 * fx)
    fz = fz * fz * (3 - 2 * fz)
    a = g[x0][:, z0]
    b = g[x0 + 1][:, z0]
    c = g[x0][:, z0 + 1]
    d = g[x0 + 1][:, z0 + 1]
    return (a * (1 - fx)[:, None] + b * fx[:, None]) * (1 - fz)[None] + (c * (1 - fx)[:, None] + d * fx[:, None]) * fz[None]


def soft_raise(h):
    """The soft-steps rule on a height field h[x, z]: where a column takes a half slab. Same as SoftBench.soft (verified)."""
    r = np.zeros(h.shape, bool)
    c = h[1:-1, 1:-1]
    e, w, s, n = h[2:, 1:-1] - c, h[:-2, 1:-1] - c, h[1:-1, 2:] - c, h[1:-1, :-2] - c
    ok = (e >= 0) & (w >= 0) & (s >= 0) & (n >= 0) & ((e == 1) | (w == 1) | (s == 1) | (n == 1)) & (e <= 1) & (w <= 1) & (s <= 1) & (n <= 1)
    for dx in (-1, 1):
        for dz in (-1, 1):
            ok &= (h[1 + dx:h.shape[0] - 1 + dx, 1 + dz:h.shape[1] - 1 + dz] - c) <= 1
    r[1:-1, 1:-1] = ok
    return r


def load_softbench(name):
    with open(os.path.join(BIN, name + ".bin"), "rb") as f:
        raw = f.read()
    n = int(np.frombuffer(raw[:4], ">i4")[0])
    h = np.frombuffer(raw[4:4 + 4 * n * n], ">i4").reshape(n, n).astype(np.int32)     # indexed [z][x]
    r = np.frombuffer(raw[4 + 4 * n * n:4 + 4 * n * n + n * n], np.uint8).reshape(n, n)
    return h, r


# ---- baking: logical blocks to render ids -------------------------------------------------------------------------------------
def bake(S, wrap=False, fringe=False):
    X, Y, Z = S.X, S.Y, S.Z
    g, lv = S.g, S.lv
    blk = np.zeros((X, Y, Z), np.uint16)
    # which logical blocks count as "solid ground" for the one-block riser rule
    solidish = np.isin(g, list(GROUND | SLABS))
    isgrass = g == GRASS
    # wrap mask: a side wraps when the neighbour in that direction is open and the ground just under it is within a block
    def nb(arr, dx, dz, fill):
        out = np.full(arr.shape, fill, arr.dtype)
        xs = slice(max(0, -dx), X - max(0, dx))
        xd = slice(max(0, dx), X - max(0, -dx))
        zs = slice(max(0, -dz), Z - max(0, dz))
        zd = slice(max(0, dz), Z - max(0, -dz))
        out[xs, :, zs] = arr[xd, :, zd]
        return out
    dirs = [(0, -1), (0, 1), (1, 0), (-1, 0)]          # N S E W
    masks = np.zeros((X, Y, Z), np.uint8)
    if wrap:
        for bit, (dx, dz) in enumerate(dirs):
            n_same = nb(g, dx, dz, 0)
            n_open = ~np.isin(n_same, list(GROUND)) & ~np.isin(n_same, list(SLABS) ) | np.isin(n_same, list(SLABS))
            below = np.zeros_like(g)
            below[:, 1:, :] = nb(g, dx, dz, 0)[:, :-1, :]
            n_below = np.isin(below, list(GROUND - {LEAVES, SPRUCE_LEAVES} | SLABS))
            slabn = np.isin(n_same, list(SLABS))
            open_ok = ~np.isin(n_same, list(GROUND))
            m = isgrass & ((open_ok & n_below) | slabn)
            masks |= (m.astype(np.uint8) << bit)
    # fringe: a non-grass ground block whose side neighbour at the same height is grass gets grass creeping in
    fr = np.zeros((X, Y, Z), np.uint8)
    if fringe:
        for bit, (dx, dz) in enumerate(dirs):
            n_same = nb(g, dx, dz, 0)
            up_open = np.zeros_like(g)
            up_open[:, :-1, :] = g[:, 1:, :]
            m = (n_same == GRASS) & ~np.isin(up_open, list(GROUND | SLABS))
            fr |= (m.astype(np.uint8) << bit)
    for code in np.unique(g):
        if code == AIR_:
            continue
        sel = np.nonzero(g == code)
        if code == GRASS:
            for x, y, z in zip(*sel):
                blk[x, y, z] = grass_id(int(lv[x, y, z]), int(masks[x, y, z]))
        elif code in (PATH, DIRT, COARSE, GRAVEL, SAND, COBBLE, STONE, PODZOL) and fringe:
            kind = {PATH: "path", DIRT: "dirt", COARSE: "coarse", GRAVEL: "gravel", SAND: "sand", COBBLE: "cobble", STONE: "stone", PODZOL: "podzol"}[code]
            for x, y, z in zip(*sel):
                m = int(fr[x, y, z])
                blk[x, y, z] = fringed_id(kind, m, int(lv[x, y, z])) if m else plain(code)
        elif code == SLAB_TURF:
            for x, y, z in zip(*sel):
                blk[x, y, z] = turf_slab_id(int(lv[x, y, z]), int(S.tuft[x, y, z]))
        elif code in (P_SHORT, P_SHORT_LOW, P_FERN, P_BUSH, P_TALL_B, P_TALL3_B, P_TALL_T, LITTER):
            for x, y, z in zip(*sel):
                blk[x, y, z] = plant_id(int(code), int(lv[x, y, z]))
        elif code == LOGZ:
            blk[sel] = plain(LOGZ)
        else:
            blk[sel] = plain(int(code))
    return blk


# ---- terrain helpers ---------------------------------------------------------------------------------------------------------
def fill_ground(S, h, top=GRASS, tint_fn=None):
    """Column fill from a height field h[x][z] (the y of the top block): top, three dirt, stone."""
    X, Z = h.shape
    for x in range(X):
        for z in range(Z):
            y = int(h[x, z])
            S.g[x, 0:max(0, y - 3), z] = STONE
            S.g[x, max(0, y - 3):y, z] = DIRT
            S.g[x, y, z] = top
            S.lv[x, y, z] = tint_fn(x, z) if tint_fn else 1


def sprinkle_plants(S, h, density, rs, codes=((P_SHORT, 1.0),), tint_fn=None, skip=None, lowest=None):
    """Plants on top of grass at a fixed density; returns nothing."""
    X, Z = h.shape
    codes_, w = zip(*codes)
    w = np.array(w) / sum(w)
    for x in range(X):
        for z in range(Z):
            y = int(h[x, z]) + 1
            if S.g[x, y - 1, z] != GRASS or S.g[x, y, z] != AIR_:
                continue
            if skip is not None and skip(x, z):
                continue
            if rs.random() < (density(x, z) if callable(density) else density):
                c = codes_[rs.choice(len(codes_), p=w)]
                lv = tint_fn(x, z) if tint_fn else 1
                if c == P_TALL_B:
                    S.g[x, y + 1, z] = P_TALL_T
                    S.lv[x, y + 1, z] = lv
                S.put(x, y, z, c, lv)


def oak(S, x, y, z, rs, trunk=6, r=3, litter=False):
    for k in range(trunk):
        S.put(x, y + k, z, LOGY)
    cy = y + trunk - 1
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            for dy in range(-2, 3):
                d2 = dx * dx + dz * dz + dy * dy * 1.6
                if d2 <= r * r + 0.5 and (d2 < r * r - 1.5 or rs.random() < 0.55):
                    if S.g[x + dx, cy + dy, z + dz] == AIR_ if (0 <= x + dx < S.X and 0 <= z + dz < S.Z and 0 <= cy + dy < S.Y) else False:
                        S.put(x + dx, cy + dy, z + dz, LEAVES)


def to_png(img, name):
    os.makedirs(OUT, exist_ok=True)
    p = os.path.join(OUT, name.replace(".png", ".jpg"))
    Image.fromarray(img).save(p, quality=90)
    return p


def draw(S, name, cam, look, wrap=False, fringe=False, size=(960, 540), fov=70, fog=520, **kw):
    t0 = time.time()
    blk = bake(S, wrap=wrap, fringe=fringe)
    img = render(blk, D, A, cam, look, fov=fov, size=size, fog=fog, **kw)
    p = to_png(img, name)
    print("  %s %.0fs" % (name, time.time() - t0), flush=True)
    return img


# ===== scene 1: a hillside ==========================================================================================================
def hillside(stage):
    """stage: 0 before; 1 + seamless grass; 2 + soft steps; 3 + cover and colour."""
    h0, _ = load_softbench("hillside")
    x0, z0, W = 205, 140, 72
    h = h0[z0:z0 + W, x0:x0 + W].T.copy()          # [x][z]
    h -= h.min()
    h += 2
    Y = int(h.max()) + 26
    S = Scene(W, Y, W, seed=11)
    rs = np.random.default_rng(5)
    big = value_noise(W, W, 17, 4)
    small = value_noise(W, W, 6, 9)
    tint = (lambda x, z: 1) if stage < 3 else (lambda x, z: int(np.clip(big[x, z] * 3.2 + (small[x, z] - .5) * 1.6, 0, 3)))
    fill_ground(S, h, tint_fn=tint)
    raise_ = soft_raise(h) if stage >= 2 else np.zeros(h.shape, bool)
    # the soft pass: the column below the slab turns to dirt (grass dies under a slab), the slab goes on top
    for x in range(W):
        for z in range(W):
            if raise_[x, z]:
                y = int(h[x, z])
                S.g[x, y, z] = DIRT
                S.g[x, y + 1, z] = SLAB_TURF
                S.lv[x, y + 1, z] = tint(x, z)
    # plants
    if stage < 3:
        sprinkle_plants(S, h, 0.2, rs, codes=((P_SHORT, 1.0),), skip=lambda x, z: raise_[x, z])
        sprinkle_plants(S, h, 0.025, rs, codes=((P_DAND, 1), (P_POPPY, 1)), skip=lambda x, z: raise_[x, z])
    else:
        dens = lambda x, z: 0.12 + 0.55 * max(0.0, big[x, z] - 0.35) * 2.0 + 0.1 * small[x, z]
        sprinkle_plants(S, h, dens, rs, codes=((P_SHORT, 5), (P_TALL_B, 2.5), (P_FERN, 1.2), (P_BUSH, .5), (P_SHORT_LOW, 3)), tint_fn=tint,
                        skip=lambda x, z: raise_[x, z])
        # flowers in drifts, not scatter
        fl = value_noise(W, W, 9, 21)
        sprinkle_plants(S, h, lambda x, z: 0.5 if fl[x, z] > 0.78 else 0.0, rs, codes=((P_DAND, 3), (P_OXEYE, 2), (P_CORN, 1), (P_POPPY, 1)),
                        skip=lambda x, z: raise_[x, z])
        # stones: pebbles and the odd boulder
        for x in range(W):
            for z in range(W):
                y = int(h[x, z]) + 1
                if S.g[x, y, z] == AIR_ and S.g[x, y - 1, z] == GRASS and rs.random() < 0.02:
                    S.put(x, y, z, PEBBLE)
        for _ in range(4):
            bx, bz = int(rs.integers(8, W - 8)), int(rs.integers(8, W - 8))
            by = int(h[bx, bz]) + 1
            for dx, dz, hh in [(0, 0, 2), (1, 0, 1), (-1, 0, 1), (0, 1, 1), (0, -1, 1), (1, 1, 0.5), (-1, -1, 0.5)]:
                yy = int(h[np.clip(bx + dx, 0, W - 1), np.clip(bz + dz, 0, W - 1)]) + 1
                for k in range(int(np.ceil(hh))):
                    if hh >= 1 or k == 0:
                        S.put(bx + dx, yy + k, bz + dz, MOSSY if (dx + dz) % 2 else STONE)
        # tufts on the turf slabs, at the density the plants have around them
        for x in range(W):
            for z in range(W):
                if raise_[x, z]:
                    S.tuft[x, int(h[x, z]) + 1, z] = int(rs.choice([0, 1, 1, 2, 3, 4, 5], p=[.25, .25, .17, .1, .1, .08, .05]))
    if stage == 2:
        for x in range(W):
            for z in range(W):
                if raise_[x, z] and rs.random() < 0.55:
                    S.tuft[x, int(h[x, z]) + 1, z] = 1
    # trees, the same in every stage
    rt = np.random.default_rng(77)
    for _ in range(5):
        tx, tz = int(rt.integers(10, W - 10)), int(rt.integers(6, W - 6))
        ty = int(h[tx, tz]) + 1
        if S.g[tx, ty - 1, tz] in (GRASS, DIRT):
            oak(S, tx, ty, tz, rt, trunk=int(rt.integers(5, 8)), r=3)
    cam = (30, h[30, 6] + 2.4, 3)
    look = (46, h[46, 36] + 3.0, 36)
    return S, cam, look


# ===== scene 2: a forest floor ======================================================================================================
def forest(stage):
    """stage 0 before; 3 after (the cover and colour)."""
    W = 64
    rs = np.random.default_rng(31)
    h = (60 + 3.2 * value_noise(W, W, 18, 5) + 1.4 * value_noise(W, W, 7, 6)).astype(int)
    S = Scene(W, 100, W, seed=3)
    canopy = value_noise(W, W, 14, 8)
    tint = (lambda x, z: 1) if stage == 0 else (lambda x, z: 0 if canopy[x, z] > 0.55 else 3 if canopy[x, z] < 0.3 else 1)
    fill_ground(S, h, tint_fn=tint)
    # trees on a loose grid with jitter
    trees = []
    rt = np.random.default_rng(12)
    for gx in range(6, W - 4, 9):
        for gz in range(8, W - 4, 9):
            tx, tz = gx + int(rt.integers(-2, 3)), gz + int(rt.integers(-2, 3))
            ty = int(h[tx, tz]) + 1
            oak(S, tx, ty, tz, rt, trunk=int(rt.integers(7, 10)), r=int(rt.integers(3, 5)))
            trees.append((tx, tz))
    near_tree = np.zeros((W, W), float)
    for tx, tz in trees:
        for x in range(max(0, tx - 6), min(W, tx + 7)):
            for z in range(max(0, tz - 6), min(W, tz + 7)):
                near_tree[x, z] = max(near_tree[x, z], 1 - np.hypot(x - tx, z - tz) / 6.5)
    if stage == 0:
        sprinkle_plants(S, h, 0.18, rs, codes=((P_SHORT, 1.0),))
        sprinkle_plants(S, h, 0.02, rs, codes=((P_DAND, 1), (P_POPPY, 1)))
        sprinkle_plants(S, h, 0.03, rs, codes=((P_BROWNM, 1), (P_REDM, 1)))
        _clear(S, FOREST_CAM(h)[0], 2.2)
        return S, FOREST_CAM(h)[0], FOREST_CAM(h)[1]
    # --- after
    # podzol under the oldest trees, moss and coarse dirt in patches
    patch = value_noise(W, W, 8, 14)
    for x in range(W):
        for z in range(W):
            y = int(h[x, z])
            if S.g[x, y, z] == GRASS:
                if near_tree[x, z] > 0.55 and rs.random() < 0.7:
                    S.g[x, y, z] = PODZOL
                elif patch[x, z] > 0.8:
                    S.g[x, y, z] = MOSS
                elif patch[x, z] < 0.12:
                    S.g[x, y, z] = COARSE
    # understory by the light: ferns in the shade, grass in the gaps
    dens = lambda x, z: 0.32 + 0.35 * near_tree[x, z]
    for x in range(W):
        for z in range(W):
            y = int(h[x, z]) + 1
            if S.g[x, y, z] != AIR_ or S.g[x, y - 1, z] not in (GRASS, PODZOL, MOSS, COARSE):
                continue
            r = rs.random()
            lv = tint(x, z)
            if near_tree[x, z] > 0.45 and r < 0.55:
                S.put(x, y, z, LITTER, int(rs.integers(0, 3)))
            elif r < dens(x, z):
                s_ = rs.random()
                c = P_FERN if (canopy[x, z] > 0.5 and s_ < 0.5) else P_BUSH if s_ < 0.08 else P_TALL_B if s_ < 0.3 else P_SHORT
                if c == P_TALL_B:
                    S.g[x, y + 1, z] = P_TALL_T
                    S.lv[x, y + 1, z] = lv
                S.put(x, y, z, c, lv)
            elif r < dens(x, z) + 0.03:
                S.put(x, y, z, int(rs.choice([P_BROWNM, P_REDM, P_DAND, P_BLUET])))
            elif r < dens(x, z) + 0.06:
                S.put(x, y, z, PEBBLE if rs.random() < 0.6 else TWIG)
    # roots: logs running out of the trunk's foot, half buried
    for tx, tz in trees:
        for dx, dz in rs.permutation([(1, 0), (-1, 0), (0, 1), (0, -1)])[:3]:
            L = int(rs.integers(1, 3))
            for k in range(1, L + 1):
                x, z = tx + dx * k, tz + dz * k
                if 0 <= x < W and 0 <= z < W:
                    y = int(h[x, z])
                    S.g[x, y, z] = LOGX if dx else LOGZ
                    S.g[x, y + 1, z] = AIR_ if k > 1 else S.g[x, y + 1, z]
    # fallen logs with fungus, and moss-skinned boulders, placed where the walk passes
    for fx, fz, L in [(17, 15, 5), (27, 30, 6), (9, 26, 4)]:
        for k in range(L):
            x = fx + k
            y = int(h[x, fz]) + 1
            S.g[x, y, fz] = LOGX
            S.g[x, y + 1, fz] = AIR_
            if rs.random() < 0.4:
                S.g[x, y + 1, fz] = P_BROWNM if rs.random() < .5 else P_REDM
            S.g[x, y - 1, fz + 1] = MOSS
            S.g[x, y, fz + 1] = P_FERN if k % 2 == 0 else AIR_
    for bx, bz in [(26, 21), (14, 22), (33, 34), (20, 9)]:
        for dx, dz, hh in [(0, 0, 2), (1, 0, 1), (-1, 0, 1), (0, 1, 1), (0, -1, 1), (1, 1, 1), (-1, -1, 0.5)]:
            yy = int(h[np.clip(bx + dx, 0, W - 1), np.clip(bz + dz, 0, W - 1)]) + 1
            for k in range(int(np.ceil(hh))):
                S.put(bx + dx, yy + k, bz + dz, MOSSY if (dx * 3 + dz) % 3 else STONE)
            if hh == 1:
                S.put(bx + dx, yy + 1, bz + dz, SLAB_STONE)
            elif hh == .5:
                S.put(bx + dx, yy, bz + dz, SLAB_STONE)
    _clear(S, FOREST_CAM(h)[0], 2.2)
    return S, FOREST_CAM(h)[0], FOREST_CAM(h)[1]


def _clear(S, cam, r):
    for x in range(int(cam[0] - r), int(cam[0] + r) + 1):
        for z in range(int(cam[2] - r), int(cam[2] + r) + 1):
            for y in range(int(cam[1]) - 3, int(cam[1]) + 2):
                if 0 <= x < S.X and 0 <= z < S.Z and 0 <= y < S.Y and S.g[x, y, z] >= 50:
                    S.g[x, y, z] = AIR_


def FOREST_CAM(h):
    return (11.5, h[11, 4] + 2.65, 4.5), (34, h[34, 36] + 2.2, 36)


# ===== scene 3: a road across a side slope ==========================================================================================
def road(stage):
    """stage 0 the road as painted today; 3 graded, with a coherent surface and a worn edge."""
    W, Zn = 72, 60
    rs = np.random.default_rng(17)
    cross = 0.46                                   # the land falls this many blocks to the south for each block north
    hn = np.zeros((W, Zn), int)
    nz = value_noise(W, Zn, 14, 2)
    for x in range(W):
        for z in range(Zn):
            hn[x, z] = int(30 + (Zn - z) * cross + 3.0 * (nz[x, z] - .5) + 0.04 * x)
    Y = 80
    S = Scene(W, Y, Zn, seed=9)
    big = value_noise(W, Zn, 13, 6)
    tint = (lambda x, z: 1) if stage == 0 else (lambda x, z: int(np.clip(big[x, z] * 3.2, 0, 3)))
    h = hn.copy()
    # the road: a line along x at z = 30 +- a gentle wander; it keeps one level for 5 cells at a time (the planner's integer y)
    zc = (30 + 4 * np.sin(np.arange(W) / 11.0)).astype(float)
    g = np.zeros(W, int)
    for x in range(W):
        g[x] = int(round(np.mean([hn[x, int(zc[x])], hn[x, min(Zn - 1, int(zc[x]) + 1)], hn[x, max(0, int(zc[x]) - 1)]])))
    g = (np.round(np.convolve(g, np.ones(7) / 7, mode="same"))).astype(int)
    g[:3], g[-3:] = g[3], g[-4]
    hw, verge = 2.2, 1.6
    dist = np.zeros((W, Zn))
    for x in range(W):
        for z in range(Zn):
            dist[x, z] = abs(z + 0.5 - zc[x])
    # bank: how the land meets the road level
    if stage == 3:
        reach = 7.5
        for x in range(W):
            for z in range(Zn):
                d = dist[x, z]
                if d <= hw + verge:
                    h[x, z] = g[x]
                elif d < reach:
                    t = (d - hw - verge) / (reach - hw - verge)
                    t = t * t * (3 - 2 * t)
                    h[x, z] = int(round(g[x] * (1 - t) + hn[x, z] * t))
    else:
        for x in range(W):
            for z in range(Zn):
                if dist[x, z] <= hw + verge:
                    h[x, z] = g[x]
    fill_ground(S, h, tint_fn=tint)
    # a cut shows its dirt, a fill shows its dirt: nothing to do, the column fill does it
    soft = soft_raise(h) if stage == 3 else np.zeros(h.shape, bool)
    # the road surface
    for x in range(W):
        for z in range(Zn):
            d = dist[x, z]
            y = int(h[x, z])
            if d <= hw:
                u = abs((z + 0.5) - zc[x])
                if stage == 0:
                    r = rs.integers(0, 100)
                    if u < 0.6:
                        c = GRASS if r < 68 else PATH if r < 92 else COARSE
                    elif u < 1.6:
                        c = PATH if r < 86 else COARSE if r < 96 else GRAVEL
                    else:
                        c = PATH if r < 55 else GRASS if r < 75 else COARSE
                    S.g[x, y, z] = c
                else:
                    # two worn tracks of path, a crown of short grass between them that breaks now and then, gravel at the shoulders
                    wob = np.sin(x / 7.0) * 0.25
                    if abs(u - 0.95 - wob) < 0.55:
                        c = PATH if rs.random() < 0.93 else COARSE
                    elif u < 0.55:
                        c = GRASS if value_noise(1, 1, 1, 0).size and (np.sin(x / 3.3 + z) > -0.2) else PATH
                    else:
                        c = GRAVEL if rs.random() < 0.55 else COARSE
                    S.g[x, y, z] = c
                S.lv[x, y, z] = tint(x, z)
            elif d <= hw + verge and stage == 0:
                S.g[x, y, z] = GRASS if rs.random() < 0.8 else COARSE
    if stage == 3:
        # soft steps on the graded banks, not on the road itself
        for x in range(W):
            for z in range(Zn):
                if soft[x, z] and dist[x, z] > hw + 0.9 and S.g[x, int(h[x, z]), z] == GRASS:
                    y = int(h[x, z])
                    S.g[x, y, z] = DIRT
                    S.g[x, y + 1, z] = SLAB_TURF
                    S.lv[x, y + 1, z] = tint(x, z)
                    S.tuft[x, y + 1, z] = int(rs.choice([0, 1, 1, 2, 3, 5]))
    # a half step where the road climbs, in its own stone
    for x in range(W - 1):
        if g[x + 1] == g[x] + 1:
            for z in range(Zn):
                if dist[x, z] <= hw:
                    S.g[x, int(h[x, z]) + 1, z] = SLAB_COARSE
    # plants
    skip = lambda x, z: dist[x, z] <= hw + 0.2 or S.g[x, int(h[x, z]) + 1, z] != AIR_
    if stage == 0:
        sprinkle_plants(S, h, 0.22, rs, codes=((P_SHORT, 1.0),), skip=skip)
        sprinkle_plants(S, h, 0.03, rs, codes=((P_DAND, 1), (P_OXEYE, 1)), skip=skip)
    else:
        sprinkle_plants(S, h, lambda x, z: 0.3 + 0.35 * max(0, big[x, z] - .4), rs, codes=((P_SHORT, 5), (P_TALL_B, 3), (P_FERN, 1), (P_BUSH, .7), (P_SHORT_LOW, 2)),
                        tint_fn=tint, skip=skip)
        fl = value_noise(W, Zn, 8, 22)
        sprinkle_plants(S, h, lambda x, z: 0.45 if fl[x, z] > 0.75 else 0.0, rs, codes=((P_DAND, 3), (P_OXEYE, 2), (P_CORN, 1), (P_BLUET, 1)), skip=skip)
        # the road's own detail: stones and tufts along the edges, a flowering verge
        for x in range(W):
            for z in range(Zn):
                if hw - .3 < dist[x, z] < hw + 2.2 and S.g[x, int(h[x, z]) + 1, z] == AIR_ and S.g[x, int(h[x, z]), z] in (GRASS, GRAVEL, COARSE, PATH):
                    r = rs.random()
                    y = int(h[x, z]) + 1
                    if r < 0.10:
                        S.put(x, y, z, PEBBLE)
                    elif r < 0.17 and dist[x, z] > hw + .3:
                        S.put(x, y, z, P_SHORT, tint(x, z))
        # kerb stones along the uphill cut, a stone here and there
        for x in range(2, W - 2, 3):
            zz = int(zc[x] - hw - 1.4)
            y = int(h[x, zz]) + 1
            if S.g[x, y, zz] == AIR_ and rs.random() < 0.6:
                S.put(x, y, zz, MOSSY if rs.random() < .5 else STONE)
    # trees on the uphill bank
    rt = np.random.default_rng(4)
    for tx in (14, 38, 60):
        tz = int(zc[tx] - 11)
        ty = int(h[tx, tz]) + 1
        oak(S, tx, ty, tz, rt, trunk=6, r=3)
    cam = (4, g[4] + 2.75, zc[4] + 0.3)
    look = (40, g[40] + 1.4, zc[40] - 1.2)
    return S, cam, look


# ===== scene 4: a mountain face =====================================================================================================
def mountain(stage):
    """stage 0 grass and dirt up the face, ore blobs showing through; 3 slope-led ground: strata, scree, ledges, snow."""
    h0, _ = load_softbench("massif")
    x0, z0, WX, WZ = 200, 150, 140, 200
    h = h0[z0:z0 + WZ, x0:x0 + WX].T.copy()           # [x][z]
    h -= h.min()
    h += 3
    Y = int(h.max()) + 14
    S = Scene(WX, Y, WZ, seed=2)
    rs = np.random.default_rng(8)
    gx = (np.roll(h, -1, 0) - np.roll(h, 1, 0)) / 2.0
    gz = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) / 2.0
    slope = np.hypot(gx, gz)
    big = value_noise(WX, WZ, 15, 3)
    patch = value_noise(WX, WZ, 6, 11)
    tint = (lambda x, z: 1) if stage == 0 else (lambda x, z: int(np.clip(big[x, z] * 3.2, 0, 3)))
    for x in range(WX):
        for z in range(WZ):
            y = int(h[x, z])
            S.g[x, 0:max(0, y - 3), z] = STONE
            S.g[x, max(0, y - 3):y + 1, z] = DIRT
            S.lv[x, y, z] = tint(x, z)
            S.g[x, y, z] = GRASS
    if stage == 0:
        # vanilla: soil over stone; where the soil is thin (steep ground) the ore blobs of the stone show: white and grey streaks
        for x in range(WX):
            for z in range(WZ):
                y = int(h[x, z])
                if slope[x, z] > 1.0:
                    for yy in range(max(0, y - 3), y + 1):
                        r = np.sin(x * 0.55 + yy * 0.8) + np.sin(z * 0.43 - yy * 0.6 + x * 0.1) + (patch[x, z] - .5) * 2.4
                        if r > 1.15:
                            S.g[x, yy, z] = DIORITE if (x + yy) % 3 else GRANITE if (z + yy) % 2 else ANDESITE
                if y > h.max() - 12 and slope[x, z] < 1.3:
                    pass
        sprinkle_plants(S, h, 0.14, rs, codes=((P_SHORT, 1.0),))
        return S, MTN_CAM(h)[0], MTN_CAM(h)[1]
    # after: the surface by slope, strata by height, snow by altitude
    strata = [STONE, ANDESITE, STONE, TUFF, STONE, STONE, GRANITE, ANDESITE, STONE, TUFF]
    top_y = h.max()
    for x in range(WX):
        for z in range(WZ):
            y = int(h[x, z])
            s_ = slope[x, z] + 0.35 * (patch[x, z] - .5)
            band = strata[(y // 3 + int(big[x, z] * 4)) % len(strata)]
            rare = patch[x, z] > 0.93
            if s_ >= 1.7:
                top = CALCITE if rare else band
            elif s_ >= 1.3:
                top = GRAVEL if patch[x, z] > 0.55 else band
            elif s_ >= 0.95:
                top = band if patch[x, z] > 0.45 else (GRAVEL if patch[x, z] > 0.3 else GRASS)
            elif s_ >= 0.7:
                top = COARSE if patch[x, z] > 0.8 else (GRAVEL if patch[x, z] > 0.7 else GRASS)
            else:
                top = GRASS
            snowy = y > top_y - 34 + 10 * (big[x, z] - .5) and s_ < 1.45
            if snowy:
                top = SNOW if patch[x, z] > 0.2 else top
            for yy in range(max(0, y - 3), y + 1):
                S.g[x, yy, z] = DIRT if top in (GRASS, COARSE) else strata[(yy // 3 + int(big[x, z] * 4)) % len(strata)]
            S.g[x, y, z] = top
            S.lv[x, y, z] = tint(x, z)
    raise_ = soft_raise(h)
    for x in range(WX):
        for z in range(WZ):
            if raise_[x, z]:
                y = int(h[x, z])
                t = S.g[x, y, z]
                if t == GRASS:
                    S.g[x, y, z] = DIRT
                    S.g[x, y + 1, z] = SLAB_TURF
                    S.lv[x, y + 1, z] = tint(x, z)
                    S.tuft[x, y + 1, z] = int(rs.choice([0, 1, 3, 5]))
                elif t == GRAVEL:
                    S.g[x, y + 1, z] = SLAB_GRAVEL
                elif t == SNOW:
                    S.g[x, y + 1, z] = SLAB_SNOW
                elif t in (STONE, ANDESITE, TUFF, GRANITE, COARSE, CALCITE):
                    S.g[x, y + 1, z] = SLAB_STONE
    # snow drifts over the edge of the snow, in layers
    for x in range(1, WX - 1):
        for z in range(1, WZ - 1):
            y = int(h[x, z]) + 1
            if S.g[x, y, z] != AIR_:
                continue
            below = S.g[x, y - 1, z]
            if below in (STONE, ANDESITE, TUFF, GRANITE, GRAVEL) and y > top_y - 40 and rs.random() < 0.18 and slope[x, z] < 1.5:
                S.g[x, y, z] = LAYER_SNOW
            elif slope[x, z] < 0.95 and below == GRASS and rs.random() < (0.5 + 0.3 * big[x, z]):
                c = P_FERN if big[x, z] > .55 else P_SHORT if rs.random() < .6 else P_BUSH
                S.put(x, y, z, c, tint(x, z))
            elif below in (STONE, ANDESITE, TUFF, GRANITE, GRAVEL) and rs.random() < 0.03:
                S.put(x, y, z, PEBBLE)
    rt = np.random.default_rng(19)
    for _ in range(70):
        tx, tz = int(rt.integers(4, WX - 4)), int(rt.integers(4, WZ - 4))
        if slope[tx, tz] < 0.85 and S.g[tx, int(h[tx, tz]), tz] in (GRASS, DIRT) and h[tx, tz] < top_y - 45:
            ty = int(h[tx, tz]) + 1
            for k in range(5):
                S.put(tx, ty + k, tz, SPRUCE_LOG)
            for k, rr in enumerate([2, 2, 1, 1, 0]):
                for dx in range(-rr, rr + 1):
                    for dz in range(-rr, rr + 1):
                        if abs(dx) + abs(dz) <= rr + 1 and 0 <= tx + dx < WX and 0 <= tz + dz < WZ and S.g[tx + dx, ty + 2 + k, tz + dz] == AIR_:
                            S.put(tx + dx, ty + 2 + k, tz + dz, SPRUCE_LEAVES)
    return S, MTN_CAM(h)[0], MTN_CAM(h)[1]


def MTN_CAM(h):
    return (70, h[70, 4] + 24, 4), (78, h[78, 150] + 16, 150)


SCENES = {"hillside": hillside, "forest": forest, "road": road, "mountain": mountain}
LEVERS = {  # stage -> (wrap, fringe)
    0: (False, False),
    1: (True, False),
    2: (True, False),
    3: (True, True),
}


def main():
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    mode = sys.argv[2] if len(sys.argv) > 2 else "before,after"
    names = list(SCENES) if which == "all" else [which]
    for name in names:
        stages = {"before": [0], "after": [3], "ladder": [0, 1, 2, 3], "before,after": [0, 3]}[mode]
        for st in stages:
            t0 = time.time()
            S, cam, look = SCENES[name](st)
            wrap, fringe = LEVERS[st]
            tag = {0: "before", 1: "wrap", 2: "soft", 3: "after"}[st]
            draw(S, "%s-%s.png" % (name, tag), cam, look, wrap=wrap, fringe=fringe,
                 fov={'forest': 78, 'mountain': 58}.get(name, 72), fog={'mountain': 1400}.get(name, 520))


if __name__ == "__main__":
    main()
