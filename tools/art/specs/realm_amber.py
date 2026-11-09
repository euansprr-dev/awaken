"""Amber Reach (SPEC 50.7): the land's own materials, the warm mid-tones its painting is made of.

    python3 tools/art/build_all.py realm_amber        textures
    python3 tools/art/specs/realm_amber.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/realm_amber.py sheet PATH every texture at 6x, named

Layered honey-coloured stone in four weathers, hex pavers and flagstones with soft joints, fired brick, cream plaster, scalloped roof
tile, stained timber, worn gilt, honey windows, golden moss, the river's silt, and the giant flowers and the wheat. Drawn by code,
nothing copied. Same table as the Amber part of dev.awaken.gates.block.ThemeBlocks.DEFS and DepthBlocks.DEFS.
"""
from __future__ import annotations

import math
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402
import gate_depth as D  # noqa: E402

RAMPS = {
    "astone": ["#5b3a17", "#7a5021", "#97692d", "#b2823a", "#cb9a4b", "#e0b363"],
    "aworn": ["#4f3518", "#6b4a22", "#86612d", "#9f783a", "#b68d49", "#caa35c"],
    "adeep": ["#3a2110", "#4e2d14", "#64391a", "#7a4720", "#8f5628", "#a36632"],
    "apale": ["#a37c3f", "#bd9550", "#d3ab62", "#e6c078", "#f2d391", "#fae4ac"],
    "ahex": ["#6d4a20", "#8a6128", "#a67a35", "#c09345", "#d6ab58", "#e8c16f"],
    "aflag": ["#6b5230", "#86693b", "#a08148", "#b89856", "#ccad66", "#dcbf78"],
    "abrick": ["#6a3514", "#84441a", "#9e5522", "#b7672b", "#cc7a37", "#dc8d46"],
    "aplast": ["#b08e5c", "#c4a36e", "#d6b780", "#e4c992", "#eed8a6", "#f6e5bc"],
    "atile": ["#6a2f12", "#86401a", "#a25223", "#bc652e", "#d07a3c", "#e28f4e"],
    "atile2": ["#7a4a12", "#97601a", "#b47a24", "#cd932f", "#e2ab3f", "#f1c155"],
    "atimb": ["#3f2610", "#553316", "#6c421d", "#835225", "#9a632f", "#ae753b"],
    "aplank": ["#69441b", "#835723", "#9c6b2d", "#b47f39", "#c99346", "#dba657"],
    "agilt": ["#7a5410", "#9c6f14", "#c08c1a", "#e0ac2a", "#f6ca4a", "#ffe488"],
    "amoss": ["#574312", "#6b5417", "#80661d", "#957a26", "#a98d32", "#bea345"],
    "abed": ["#3d2408", "#4f300c", "#613c10", "#734914", "#855618", "#97631e"],
    "apetal": ["#a44a0c", "#c6620f", "#e27c16", "#f3982a", "#fbb548", "#ffd277"],
    "apetal2": ["#7a300a", "#94400c", "#b0520f", "#c86612", "#dc7c1a", "#ea9428"],
    "apetal3": ["#d48a1c", "#e6a02a", "#f2b83e", "#fad058", "#ffe07c", "#fff0a8"],
    "agold": ["#7a5410", "#9a7016", "#b88c1e", "#d2a62a", "#e6c03e", "#f4d65e"],
    "aseed": ["#2a1608", "#3b200c", "#4f2c10", "#643a16", "#7c4a1e", "#966028"],
    "astem": ["#2c3a14", "#3c4e1b", "#4f6423", "#647a2d", "#7a903a", "#92a84a"],
    "aleaf": ["#2e3f15", "#40561d", "#566f27", "#6e8832", "#86a03f", "#9fb850"],
    "awheat": ["#5f3f12", "#7a5418", "#966d20", "#b0872c", "#c8a040", "#dcb95e"],
    "honey": ["#b05a06", "#d27a10", "#f09a1e", "#ffbe40", "#ffdc7a", "#fff2c0"],
}

# name -> (English, tool)
BLOCKS = {
    "amber_stone": ("Amber Stone", "pickaxe"),
    "amber_stone_worn": ("Worn Amber Stone", "pickaxe"),
    "amber_stone_deep": ("Deep Amber Stone", "pickaxe"),
    "amber_stone_pale": ("Sunbleached Amber Stone", "pickaxe"),
    "amber_hexstone": ("Hex Paver", "pickaxe"),
    "amber_flagstone": ("Amber Flagstone", "pickaxe"),
    "amber_brick": ("Honey Brick", "pickaxe"),
    "amber_plaster": ("Honey Plaster", "pickaxe"),
    "amber_plaster_band": ("Painted Plaster", "pickaxe"),
    "amber_tile": ("Fired Roof Tile", "pickaxe"),
    "amber_tile_deep": ("Honey Roof Tile", "pickaxe"),
    "amber_timber": ("Stained Timber", "axe"),
    "amber_planks": ("Stained Boards", "axe"),
    "amber_gilt": ("Worn Gilt", "pickaxe"),
    "amber_window": ("Honey Window", "axe"),
    "amber_moss": ("Golden Moss", "hoe"),
    "amber_bed": ("Honey Silt", "shovel"),
    "amber_petal": ("Giant Petal", "hoe"),
    "amber_petal_deep": ("Giant Petal Base", "hoe"),
    "amber_petal_tip": ("Giant Petal Tip", "hoe"),
    "amber_leaf_gold": ("Golden Leaves", "hoe"),
    "amber_seedhead": ("Flower Heart", "hoe"),
    "amber_stem": ("Giant Stem", "axe"),
    "amber_leaf": ("Giant Leaf", "hoe"),
    "amber_wheat": ("Amber Wheat", None),
    "amber_wheat_tall": ("Tall Amber Wheat", None),
    "amber_grass": ("Amber Grass", None),
    "amber_bloom": ("Amber Bloom", None),
}

# cut into slabs and stairs: (base, English, tool). The same list as DepthBlocks.
CUTS = [("amber_stone", "Amber Stone", "pickaxe"), ("amber_stone_pale", "Sunbleached Stone", "pickaxe"), ("amber_flagstone", "Flagstone", "pickaxe"),
        ("amber_brick", "Honey Brick", "pickaxe"), ("amber_plaster", "Honey Plaster", "pickaxe"), ("amber_tile", "Fired Tile", "pickaxe"),
        ("amber_tile_deep", "Honey Tile", "pickaxe"), ("amber_planks", "Stained Board", "axe")]

B = K.B


def _P():
    return K.px(RAMPS)


# ---- stone ---------------------------------------------------------------------------------------------------------------

def strata(P, ramp, seed, bands=3, lo=1, span=4):
    """Stone laid down in beds: soft horizontal bands that tile top to bottom, broken by grain."""
    rp = P.ramp(ramp)
    rng = P.rng_for("strata", ramp, seed)
    n = P.value_noise(16, 16, rng, scale=3, octaves=3)
    phase = [rng.random() * 6.28 for _ in range(3)]
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            wob = math.sin(x / 16 * 6.283 + phase[0]) * 0.7 + math.sin(x / 16 * 12.566 + phase[1]) * 0.3
            band = math.sin((y + wob) / 16 * 6.283 * bands + phase[2]) * 0.5 + 0.5
            v = n[y][x] * 0.62 + band * 0.38 + (rng.random() - 0.5) * 0.08
            c.set(x, y, rp[max(0, min(len(rp) - 1, lo + int(v * span)))])
    return c


def tex_stone(P):
    return strata(P, "astone", "stone")


def tex_worn(P):
    rp = P.ramp("aworn")
    c = strata(P, "aworn", "worn", bands=2)
    rng = P.rng_for("worn-pits")
    for _ in range(7):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, rp[0])
        c.set((x + 1) % 16, y, rp[1])
        c.blend(x, (y + 1) % 16, rp[-1], 0.5)
    K.lines(c, K.walk(P, "worn-crack", 2, 7, turn=0.55), rp[0], rp[1])
    return c


def tex_deep(P):
    return strata(P, "adeep", "deep", bands=2)


def tex_pale(P):
    rp = P.ramp("apale")
    c = strata(P, "apale", "pale", bands=2, lo=1, span=4)
    for x, y, col in [(x, y, rp[-1]) for x, y in ((3, 2), (11, 5), (6, 9), (13, 12), (1, 14))]:
        c.set(x, y, col)
    return c


def tex_bed(P):
    rp = P.ramp("abed")
    c = K.noisy(P, "abed", "bed", scale=2, octaves=3, lo=0, span=5, jitter=0.1)
    # ripples the current left
    for y in (4, 11):
        for x in range(16):
            yy = (y + int(round(math.sin(x / 16 * 6.283) * 1.2))) % 16
            c.blend(x, yy, rp[-1], 0.4)
    return c


# ---- worked stone ------------------------------------------------------------------------------------------------------

def tex_hexstone(P):
    """Six-sided pavers, staggered; each stone its own tone, the joints soft and warm rather than a drawn grid."""
    rp = P.ramp("ahex")
    rng = P.rng_for("hexstone")
    centres = [(4, 4), (12, 4), (0, 12), (8, 12)]
    tone = {c: rng.random() for c in centres}
    grain = P.value_noise(16, 16, rng, scale=3, octaves=2)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            ds = sorted((min(abs(x + 0.5 - a), 16 - abs(x + 0.5 - a)) ** 2 + min(abs(y + 0.5 - b), 16 - abs(y + 0.5 - b)) ** 2, (a, b)) for a, b in centres)
            gap = math.sqrt(ds[1][0]) - math.sqrt(ds[0][0])
            v = 2.0 + tone[ds[0][1]] * 2.2 + grain[y][x] * 1.0 - 0.5
            if gap < 0.7:
                c.set(x, y, rp[1])
            elif gap < 1.6:
                c.set(x, y, rp[max(1, min(5, int(v) - 1))])
            else:
                c.set(x, y, rp[max(1, min(5, int(v)))])
    return c


def tex_flagstone(P):
    """Big uneven flags with soft joints; the joints wrap, so a street reads as one laid floor."""
    rp = P.ramp("aflag")
    rng = P.rng_for("flag")
    pts = [(((i * 7 + 2) % 16 + rng.random() * 3) % 16, ((i * 11 + 5) % 16 + rng.random() * 3) % 16, rng.random()) for i in range(6)]
    grain = P.value_noise(16, 16, rng, scale=3, octaves=2)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            ds = sorted((min(abs(x + 0.5 - a), 16 - abs(x + 0.5 - a)) ** 2 + min(abs(y + 0.5 - b), 16 - abs(y + 0.5 - b)) ** 2, t) for a, b, t in pts)
            gap = math.sqrt(ds[1][0]) - math.sqrt(ds[0][0])
            tone = 2 + ds[0][1] * 2.4 + grain[y][x] * 1.1 - 0.5
            if gap < 0.55:
                c.set(x, y, rp[2])
            elif gap < 1.3:
                c.set(x, y, rp[max(2, min(5, int(tone) - 1))])
            else:
                c.set(x, y, rp[max(2, min(5, int(tone)))])
    return c


def tex_brick(P):
    rp, mortar = P.ramp("abrick"), P.ramp("aplast")
    rng = P.rng_for("brick")
    tone = {}
    c = P.Canvas(16, 16)
    for y in range(16):
        row = y // 4
        off = 0 if row % 2 == 0 else 4
        for x in range(16):
            i = (x + off) % 8
            b = ((x + off) // 8 % 2, row)
            if b not in tone:
                tone[b] = rng.randrange(2, 5)
            if y % 4 == 3 or i == 7:
                c.set(x, y, mortar[1])
            elif y % 4 == 0:
                c.set(x, y, rp[min(5, tone[b] + 1)])
            else:
                c.set(x, y, rp[tone[b] - (1 if rng.random() < 0.18 else 0)])
    return c


def tex_plaster(P):
    rp, br = P.ramp("aplast"), P.ramp("abrick")
    c = K.noisy(P, "aplast", "plaster", scale=2, octaves=3, lo=2, span=3, jitter=0.05)
    rng = P.rng_for("plaster-chips")
    # a few places the plaster has come away from the brick under it
    for _ in range(2):
        x, y = rng.randrange(1, 13), rng.randrange(1, 13)
        for dx, dy in ((0, 0), (1, 0), (2, 0), (1, 1), (0, 1)):
            c.set(x + dx, y + dy, br[2 + (dx + dy) % 2])
        c.blend(x, y - 1, rp[0], 0.5)
    return c


def tex_band(P):
    c = tex_plaster(P)
    rust, gilt = P.ramp("atile"), P.ramp("agilt")
    for x in range(16):
        c.set(x, 4, gilt[2])
        c.set(x, 11, gilt[2])
        for y in range(5, 11):
            c.set(x, y, rust[2 + (x + y) % 2])
    for cx in (3, 11):
        for dy in range(-2, 3):
            w = 2 - abs(dy)
            for dx in range(-w, w + 1):
                c.set(cx + dx, 8 + dy - 0, gilt[3 + (dx + dy) % 2])
    return c


def scallops(P, ramp, seed):
    """Overlapping roof tiles, four rows a block, each row shading into the next."""
    rp = P.ramp(ramp)
    rng = P.rng_for("tile", ramp, seed)
    tone = {}
    c = P.Canvas(16, 16)
    for y in range(16):
        row, yy = y // 4, y % 4
        off = 0 if row % 2 == 0 else 4
        for x in range(16):
            i = (x + off) % 8
            k = ((x + off) // 8 % 2, row)
            if k not in tone:
                tone[k] = rng.randrange(2, 5)
            round_off = abs(i - 3.5) > 2.0 and yy == 3 or abs(i - 3.5) > 3.1 and yy == 2
            if round_off or i == 7 and yy < 2:
                c.set(x, y, rp[0])
            elif yy == 0:
                c.set(x, y, rp[max(0, tone[k] - 2)])
            elif yy == 3:
                c.set(x, y, rp[min(5, tone[k] + 1)])
            else:
                c.set(x, y, rp[tone[k] - (1 if yy == 1 else 0)])
    return c


def tex_gilt(P):
    rp = P.ramp("agilt")
    c = K.noisy(P, "agilt", "gilt", scale=2, octaves=3, lo=2, span=3, jitter=0.1)
    rng = P.rng_for("gilt-dents")
    for _ in range(6):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, rp[0])
        c.set((x + 1) % 16, (y + 1) % 16, rp[-1])
    return c


def tex_window(P):
    fr, hp = P.ramp("atimb"), P.ramp("honey")
    rng = P.rng_for("window")
    c, g = P.Canvas(16, 16), P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            bar = x in (5, 10) or y in (5, 10)
            if edge or bar:
                c.set(x, y, fr[3 if edge and (x == 0 or y == 0) else 2])
            else:
                k = 2 + rng.randrange(3)
                c.set(x, y, hp[k])
                g.set(x, y, hp[min(5, k + 1)])
    return c, g


# ---- growing things ------------------------------------------------------------------------------------------------------

def tex_moss(P):
    return K.noisy(P, "amoss", "moss-top", scale=2, octaves=3, lo=0, span=6, jitter=0.16)


def tex_moss_side(P):
    c = tex_stone(P)
    mo = P.ramp("amoss")
    rng = P.rng_for("moss-side")
    for x in range(16):
        d = 2 + rng.randrange(3)
        for y in range(d):
            c.set(x, y, mo[max(1, min(5, 4 - y + rng.randrange(-1, 2)))])
    return c


def tex_petal(P, ramp="apetal"):
    rp = P.ramp(ramp)
    rng = P.rng_for("petal", ramp)
    n = P.value_noise(16, 16, rng, scale=2, octaves=2)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            fibre = math.sin(x / 16 * 6.283 * 4 + n[y][x] * 2.2) * 0.5 + 0.5
            c.set(x, y, rp[max(0, min(5, int(0.6 + n[y][x] * 2.6 + fibre * 2.1)))])
    return c


def tex_seed(P):
    rp, gl = P.ramp("aseed"), P.ramp("agilt")
    c = K.noisy(P, "aseed", "seed", scale=1, octaves=2, lo=0, span=4, jitter=0.1)
    for x in range(0, 16, 3):
        for y in range(0, 16, 3):
            xx, yy = (x + (y // 3) % 2 * 2) % 16, y
            c.set(xx, yy, gl[1 + (x + y) % 3])
            c.set((xx + 1) % 16, yy, rp[0])
    return c


def tex_stem(P):
    rp = P.ramp("astem")
    rng = P.rng_for("stem")
    c = P.Canvas(16, 16)
    tone = [rng.randrange(1, 5) for _ in range(16)]
    for y in range(16):
        for x in range(16):
            c.set(x, y, rp[max(0, min(5, tone[x] + (1 if (y + x * 5) % 11 == 0 else 0)))])
    return c


def tex_stem_end(P):
    rp = P.ramp("astem")
    c = K.noisy(P, "astem", "stem-end", scale=2, octaves=2, lo=2, span=3, jitter=0.1)
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d > 6.5:
                c.set(x, y, rp[1])
    return c


def tex_leaf(P):
    rp = P.ramp("aleaf")
    c = K.noisy(P, "aleaf", "leaf", scale=2, octaves=3, lo=1, span=4, jitter=0.14)
    for x in range(16):
        c.set(x, 7, rp[5 - (x % 3 == 0)])
    for x in range(1, 16, 4):
        for d in range(1, 6):
            c.set((x + d) % 16, 7 - d, rp[3])
            c.set((x + d) % 16, 7 + d, rp[2])
    return c


def stalks(P, seed, tops=True, n=7, rows=(1, 15), stalk_lo=1):
    """Fine stalks standing well apart, one pixel wide, each with its own lean and a narrow bearded ear. Never a bar of colour: seen from inside the
    crop a pixel is as wide as your hand, so two stems side by side or one in stripes reads as a plank."""
    rp = P.ramp("awheat")
    rng = P.rng_for("wheat", seed)
    c = P.Canvas(16, 16)
    x, xs = 1 + rng.randrange(2), []
    while x < 15:
        xs.append(x)
        x += 3 + rng.randrange(2)
    for x0 in xs:
        top = rng.randrange(rows[0], rows[0] + 6) if tops else rng.randrange(0, 3)
        lean = rng.choice((-1, 0, 1))
        tone = rng.randrange(2)
        ear = 5 if tops else 0
        for y in range(top + ear, 16):
            sx = x0 + (lean if y < top + ear + 3 else 0)
            if 0 <= sx < 16:
                c.set(sx, y, rp[stalk_lo + tone + (0 if y > 12 else 1)])
        if tops:
            xe = x0 + lean
            for k in range(ear):
                y = top + k
                if 0 <= xe < 16:
                    c.set(xe, y, rp[5 - tone] if k < ear - 1 else rp[3 + tone])
                # one grain stands off the ear every other row, and only on alternating sides
                if k in (1, 3):
                    gx = xe + (1 if (k + x0) % 4 == 1 else -1)
                    if 0 <= gx < 16:
                        c.set(gx, y, rp[3 + tone])
    return c


def tex_grass(P):
    rp = P.ramp("awheat")
    rng = P.rng_for("amber-grass")
    c = P.Canvas(16, 16)
    for x in range(1, 15):
        if rng.random() < 0.3:
            continue
        top = 15 - rng.randrange(5, 11)
        for y in range(top, 16):
            c.set(x + (1 if y < top + 3 and x % 3 == 0 else 0), y, rp[(16 - y) // 3 + 1 if y > 8 else 3 + x % 2])
    return c


def tex_bloom(P):
    st, pe, ce = P.ramp("astem"), P.ramp("apetal"), P.ramp("agilt")
    c = P.Canvas(16, 16)
    for y in range(7, 16):
        c.set(7 + (1 if y < 11 else 0), y, st[2 + y % 2])
    for dx, dy in [(0, -2), (-2, 0), (2, 0), (0, 2), (-1, -1), (1, -1), (-1, 1), (1, 1), (0, -1), (-1, 0), (1, 0), (0, 1)]:
        c.set(8 + dx, 5 + dy, pe[4 if abs(dx) + abs(dy) == 2 else 3])
    c.set(8, 5, ce[1])
    c.set(9, 13, st[3])
    c.set(6, 12, st[3])
    return c


def textures():
    P = _P()
    win, win_g = tex_window(P)
    return {
        "amber_stone": tex_stone(P), "amber_stone_worn": tex_worn(P), "amber_stone_deep": tex_deep(P), "amber_stone_pale": tex_pale(P),
        "amber_hexstone": tex_hexstone(P), "amber_flagstone": tex_flagstone(P), "amber_brick": tex_brick(P), "amber_plaster": tex_plaster(P),
        "amber_plaster_band": tex_band(P), "amber_tile": scallops(P, "atile", "t"), "amber_tile_deep": scallops(P, "atile2", "d"),
        "amber_timber": K.bark(P, "atimb", "timber"), "amber_timber_end": K.rings(P, "atimb", "atimb", "timber"),
        "amber_planks": K.boards(P, "aplank", "planks"), "amber_gilt": tex_gilt(P), "amber_window": win, "amber_window_glow": win_g,
        "amber_moss_top": tex_moss(P), "amber_moss": tex_moss_side(P), "amber_bed": tex_bed(P), "amber_petal": tex_petal(P), "amber_petal_deep": tex_petal(P, "apetal2"), "amber_petal_tip": tex_petal(P, "apetal3"), "amber_leaf_gold": K.noisy(P, "agold", "leafg", scale=2, octaves=3, lo=1, span=5, jitter=0.2), "amber_seedhead": tex_seed(P),
        "amber_stem": tex_stem(P), "amber_stem_end": tex_stem_end(P), "amber_leaf": tex_leaf(P),
        "amber_wheat": stalks(P, "w", n=8, rows=(0, 15)), "amber_wheat_tall": stalks(P, "tl", tops=False, n=7), "amber_wheat_tall_top": stalks(P, "tt", n=7, rows=(1, 15)),
        "amber_grass": tex_grass(P), "amber_bloom": tex_bloom(P),
    }


def P_for_honey():
    return _P()


def honey_frames(P, count=32):
    """The river: thick and slow, deep amber with wide soft bands that never show the block's edge, and a few small glints
    drifting down it. Anything sharper repeats every sixteen blocks and reads as wallpaper. Loops on the last frame."""
    import math
    rp = P.ramp("honey")
    rng = P.rng_for("honey-flow")
    bands = [(rng.random() * 6.283, kx, ky, w) for kx, ky, w in ((1, 0, 1.0), (1, 1, 0.8), (1, -1, 0.6), (2, 1, 0.3))]
    # glints ride the flow at their own places and pace: whole loops only, so the last frame meets the first
    glints = [(rng.randrange(16), rng.randrange(16), 1 + rng.randrange(2), 1 + rng.randrange(3)) for _ in range(6)]
    frames = []
    for f in range(count):
        t = f / count * 6.283
        c = P.Canvas(16, 16)
        for y in range(16):
            for x in range(16):
                a, b = x / 16 * 6.283, y / 16 * 6.283
                v = sum(w * math.sin(kx * a + ky * b + ph + t) for ph, kx, ky, w in bands) / 2.7
                c.set(x, y, rp[min(3, max(2, int(2.4 + v * 1.1 + 0.5)))])
        for gx, gy, speed, size in glints:
            y = (gy + int(f / count * 16 * speed)) % 16
            for k in range(size):
                c.set((gx + k // 2) % 16, (y + k) % 16, rp[5 if k == 0 else 4])
        frames.append(c)
    return frames


def build(out):
    tx = textures()
    for name, c in tx.items():
        out.block(name, c)
    out.block("realm_honey", honey_frames(P_for_honey()), frametime=4)


# ---- models ---------------------------------------------------------------------------------------------------------------

def turned(name):
    """Stone turned four ways, so no face shows the same block twice in a row."""
    m = B + name
    return {"variants": {"": [{"model": m}, {"model": m, "y": 90}, {"model": m, "y": 180}, {"model": m, "y": 270}]}}


def models():
    m = {}
    for n in ("amber_stone", "amber_stone_worn", "amber_stone_deep", "amber_stone_pale", "amber_hexstone", "amber_flagstone", "amber_brick", "amber_plaster", "amber_plaster_band",
              "amber_tile", "amber_tile_deep", "amber_planks", "amber_gilt", "amber_bed", "amber_petal", "amber_petal_deep", "amber_petal_tip", "amber_leaf_gold", "amber_seedhead", "amber_leaf"):
        m[n] = K.cube_model(n)
    m["amber_window"] = K.cube_model("amber_window", glow=True)
    m["amber_moss"] = K.cube_model("amber_moss", top="amber_moss_top", bottom="amber_stone")
    m["amber_timber"] = K.column_model("amber_timber", "amber_timber_end")
    m["amber_stem"] = K.column_model("amber_stem", "amber_stem_end")
    m["amber_wheat"] = K.cross_model("amber_wheat")
    m["amber_grass"] = K.cross_model("amber_grass")
    m["amber_bloom"] = K.cross_model("amber_bloom")
    # one block that stands a block and a half: two pairs of crossed planes, the upper its own art
    m["amber_wheat_tall"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                             "textures": {"particle": B + "amber_wheat_tall", "low": B + "amber_wheat_tall", "up": B + "amber_wheat_tall_top"},
                             "elements": K.cross("#low") + K.cross("#up", y0=16, y1=28, uv=[0, 4, 16, 16])}
    return m


def states():
    s = {}
    for n in BLOCKS:
        if n in ("amber_stone", "amber_stone_worn", "amber_stone_deep", "amber_stone_pale", "amber_bed", "amber_moss"):
            s[n] = turned(n)
        elif n in ("amber_timber", "amber_stem"):
            s[n] = K.axis_state(n)
        else:
            s[n] = K.simple_state(n)
    return s


def honey_block():
    """The river's surface is a block, not water: it has to glow, and you wade through it up to the knees."""
    res = os.path.join(K.ROOT if hasattr(K, "ROOT") else os.path.abspath(os.path.join(os.path.dirname(__file__), "../../..")), "src/main/resources/assets/awaken")
    t = "awaken:block/realm_honey"
    side = {"texture": "#t"}
    top = {"texture": "#t", "cullface": "up"}
    faces = {"up": top, "north": dict(side, cullface="north"), "south": dict(side, cullface="south"), "east": dict(side, cullface="east"), "west": dict(side, cullface="west")}
    model = {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": {"t": t, "particle": t},
             "elements": [{"from": [0, 0, 0], "to": [16, 15, 16], "faces": faces, "light_emission": 9}]}
    with open(os.path.join(res, "models/block/realm_honey.json"), "w") as f:
        json.dump(model, f, indent=2)
    with open(os.path.join(res, "blockstates/realm_honey.json"), "w") as f:
        # turned at random block by block: the blotches never line up into a sixteen-block repeat
        json.dump({"variants": {"": [{"model": "awaken:block/realm_honey", "y": a} for a in (0, 90, 180, 270)]}}, f, indent=2)


def content():
    honey_block()
    K.content("realm_amber", BLOCKS, models(), states(), {})
    # the slabs and stairs are cut through the same machinery as every other depth block
    cut, m, s = {}, {}, {}
    for base, en, tool in CUTS:
        cut[base + "_slab"] = (en + " Slab", tool)
        cut[base + "_stairs"] = (en + " Stairs", tool)
        t = B + base
        tex = {"bottom": t, "top": t, "side": t}
        m[base + "_slab"] = {"parent": "minecraft:block/slab", "textures": tex}
        m[base + "_slab_top"] = {"parent": "minecraft:block/slab_top", "textures": tex}
        s[base + "_slab"] = {"variants": {"type=bottom": {"model": B + base + "_slab"}, "type=top": {"model": B + base + "_slab_top"}, "type=double": {"model": t}}}
        m[base + "_stairs"] = {"parent": "minecraft:block/stairs", "textures": tex}
        m[base + "_stairs_inner"] = {"parent": "minecraft:block/inner_stairs", "textures": tex}
        m[base + "_stairs_outer"] = {"parent": "minecraft:block/outer_stairs", "textures": tex}
        s[base + "_stairs"] = D.stairs_state(base + "_stairs")
    K.content("realm_amber_cuts", cut, m, s, {})
    for base, _, _ in CUTS:
        D.slab_loot(base + "_slab")
    for kind, tag in (("_slab", "slabs"), ("_stairs", "stairs")):
        vals = [f"awaken:{b}{kind}" for b, _, _ in CUTS]
        K.merge(os.path.join(K.MERGE, f"gates/data/minecraft/tags/block/{tag}.json"), {"values": vals})
        K.merge(os.path.join(K.MERGE, f"gates/data/minecraft/tags/item/{tag}.json"), {"values": vals})


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/realm_amber.png")
    else:
        print(__doc__)
