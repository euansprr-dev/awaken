"""The Carillon kit's art (docs/carillon/m4/look-bible.md §8): textures, models and blockstates for the core kit
(dev.awaken.city.kit): slate and clay roof tiles (+ stairs, slabs), the flower box, shutters, the drainpipe, trellis and
ivy, lit windows in ten styles, the Quay's loading door, the hoist beam with its pulley, rope, and the chimney crown.

Pixel art in vanilla's register: 16x16, crisp, 4-6 tone ramps per material, light from the top-left, colours anchored on
real vanilla textures (stripped_dark_oak_log (72,56,36), stone_bricks (122,121,122), calcite (223,224,220), ...).

    python3 tools/art/build_all.py city_kit                       textures
    python3 tools/art/specs/city_kit.py content                   blockstates, models, lang, mineable tags
    python3 tools/art/specs/city_kit.py check                     every blockstate variant, model and texture resolves
    python3 tools/art/specs/city_kit.py sheet <out.png> [prefix]  contact sheet of the kit's textures only (in memory)
    python3 tools/art/specs/city_kit.py mock <out.png>            facade elevations of the six districts (dev look-test)
"""
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import Canvas, rng_for  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
TEX = os.path.join(ASSETS, "textures/block")
MERGE = os.path.join(ROOT, "src/main/merge/city")
VANILLA = "/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft"  # dev look-tests only (mock, check); never needed to build

# ---------------------------------------------------------------------------------------------------------------------
# colour and drawing helpers


def R(*hexes):
    """A ramp (dark -> light) from hex strings."""
    return [rgb(h) for h in hexes]


def rgb(h):
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255


def clamp8(v):
    return max(0, min(255, int(round(v))))


def lerp(a, b, t):
    return tuple(clamp8(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def mul(c, f):
    return clamp8(c[0] * f), clamp8(c[1] * f), clamp8(c[2] * f), c[3] if len(c) > 3 else 255


def tint(c, dr=0, dg=0, db=0):
    return clamp8(c[0] + dr), clamp8(c[1] + dg), clamp8(c[2] + db), c[3] if len(c) > 3 else 255


def step(rp, i, d=0):
    return rp[max(0, min(len(rp) - 1, i + d))]


def box(c, x0, y0, x1, y1, col):
    """Fill [x0,x1) x [y0,y1)."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            c.set(x, y, col)


def put(c, pts, col):
    for x, y in pts:
        c.set(x, y, col)


def opaque(c):
    return [p for row in c.px for p in row if p[3] > 0]


def average(c):
    px = opaque(c)
    return tuple(sum(p[i] for p in px) / len(px) for i in range(3))


def fit(c, target):
    """Scale an opaque texture's channels so its average lands on the target (a deterministic tone-matching pass)."""
    for _ in range(3):
        a = average(c)
        k = [target[i] / a[i] for i in range(3)]
        for y in range(c.h):
            for x in range(c.w):
                p = c.px[y][x]
                if p[3] > 0:
                    c.px[y][x] = (clamp8(p[0] * k[0]), clamp8(p[1] * k[1]), clamp8(p[2] * k[2]), p[3])
    return c


def noisy(rng, rp, i, p=0.15, spread=1):
    """Ramp step i, nudged by up to +-spread with probability p (the grain every vanilla texture has)."""
    if rng.random() < p:
        i += rng.choice([-spread, spread])
    return step(rp, i)


# ---------------------------------------------------------------------------------------------------------------------
# roofs


SLATE = R("#30363f", "#444d5c", "#566175", "#657189", "#76839a", "#8b98ae")
# Staggered joints per 4px course (cyclic), every slate 4-6 wide, neighbours at least 2px apart.
SLATE_COURSES = [(0, 5, 11), (3, 8, 13), (1, 6, 11), (3, 9, 13)]


def slate_tiles():
    """Old Welsh slate: blue-grey, lighter than deepslate, a lighter top-left edge, a dark shadow line under each course."""
    rng = rng_for("city_kit", "slate_tiles")
    c = Canvas()
    odd = {(0, 1): "green", (2, 2): "purple", (3, 0): "green"}
    for j, joints in enumerate(SLATE_COURSES):
        y0 = j * 4
        for i, xa in enumerate(joints):
            xb = joints[(i + 1) % 3] + (16 if i == 2 else 0)
            base = rng.choice([2, 3, 3, 3, 4])
            kind = odd.get((j, i))
            for x in range(xa, xb):
                for r in range(4):
                    if r == 3:
                        col = step(SLATE, 0) if rng.random() > 0.2 else step(SLATE, 1)
                    else:
                        k = base
                        if r == 0:
                            k += 1
                        if x == xa:
                            k += 1
                        if x == xb - 1:
                            k -= 1
                        if r == 2 and rng.random() < 0.4:
                            k -= 1
                        if 0 < r and x not in (xa, xb - 1) and rng.random() < 0.16:
                            k += rng.choice([-1, 1])
                        col = step(SLATE, k)
                    if kind == "green" and r < 3:
                        col = tint(col, -3, 3, -2)
                    elif kind == "purple" and r < 3:
                        col = tint(col, 4, -2, 4)
                    c.set(x % 16, y0 + r, col)
    return fit(c, (98, 106, 120))


CLAY = R("#5f2b1c", "#85422a", "#a95c38", "#c27240", "#d98a50", "#ee9f64")


def clay_tiles():
    """Pantiles: 4px columns of rounded tiles (highlight left of centre, shadow at the right), an overlap line every 4px."""
    rng = rng_for("city_kit", "clay_tiles")
    c = Canvas()
    prof = {0: [3, 4, 4, 2], 1: [3, 4, 3, 2], 2: [3, 3, 3, 1], 3: [1, 1, 1, 0]}  # per row of a tile: lip, body, body, the overlap line
    for i in range(4):
        off = 2 if i % 2 else 0  # odd columns sit half a tile lower, so the overlap lines don't read as one grid
        for j in range(4):
            shade = rng.choice(["", "", "", "brown", "red", "light"])
            for dy in range(4):
                for dx in range(4):
                    k = prof[dy][dx]
                    if dy < 3 and rng.random() < 0.10:
                        k += rng.choice([-1, 1])
                    col = step(CLAY, k)
                    if shade == "brown":
                        col = tint(mul(col, 0.93), -6, 0, 4)
                    elif shade == "red":
                        col = tint(col, 8, -9, -6)
                    elif shade == "light":
                        col = tint(col, 8, 6, 2)
                    c.set(i * 4 + dx, (j * 4 + dy + off) % 16, col)
    return fit(c, (178, 98, 60))


# ---------------------------------------------------------------------------------------------------------------------
# flower boxes. Texture layout (16x16): row 8 the rim colour, rows 9..15 x 1..14 the planter's front/side elevation,
# rows 0..10 x 1..14 the top view for the iron basket's rim. Models map their faces onto these regions 1:1.

SOIL = R("#241810", "#33231a", "#43301f", "#573e29", "#6b4d33")
SPRUCE_DARK = R("#2a1c10", "#3d2a18", "#523822", "#68482b", "#7e5a38")
IRON = R("#101114", "#1b1c21", "#2c2e36", "#42444e", "#666a76")
CALCITE = R("#9d9d98", "#bcbcb5", "#d3d3cb", "#e6e6df", "#f4f4ee")


def flower_box_soil():
    rng = rng_for("city_kit", "flower_box_soil")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.set(x, y, step(SOIL, rng.choice([1, 2, 2, 2, 3, 3]), rng.choice([0, 0, 0, 1, -1])))
    # a few pebbles: a light stone with a shadow under it
    for (x, y) in ((3, 4), (11, 9), (7, 13), (13, 2)):
        c.set(x, y, (118, 112, 104, 255))
        c.set(x + 1, y + 1, step(SOIL, 0))
        c.set(x, y - 1, (84, 78, 72, 255)) if (x + y) % 2 else None
    return c


def flower_box_wood():
    """Dark-stained spruce boards held by two black iron bands, the top edge lighter."""
    rng = rng_for("city_kit", "flower_box_wood")
    c = Canvas()
    boards = {9: 4, 10: 3, 11: 3, 12: 0, 13: 3, 14: 2, 15: 1}
    for y in range(9, 16):
        for x in range(1, 15):
            k = boards[y]
            if y not in (9, 12, 15) and rng.random() < 0.3:
                k += rng.choice([-1, 1])
            if y == 9 and x == 1:
                k = 4
            c.set(x, y, step(SPRUCE_DARK, k))
    # two plank joints, offset, so the boards read as boards
    c.set(5, 10, step(SPRUCE_DARK, 1))
    c.set(5, 11, step(SPRUCE_DARK, 1))
    c.set(10, 13, step(SPRUCE_DARK, 1))
    c.set(10, 14, step(SPRUCE_DARK, 1))
    # the iron bands
    for bx in (3, 11):
        for y in range(9, 16):
            c.set(bx, y, step(IRON, 3))
            c.set(bx + 1, y, step(IRON, 1))
        c.set(bx, 9, step(IRON, 4))
        c.set(bx, 12, step(IRON, 4))  # a rivet on the seam
        c.set(bx + 1, 12, step(IRON, 0))
    # the underside (rows 0..10): three boards laid along the box, the bands wrapping under them
    for y in range(0, 11):
        for x in range(1, 15):
            k = 2 if y not in (3, 7) else 0
            if k and rng.random() < 0.22:
                k += rng.choice([-1, 1])
            c.set(x, y, step(SPRUCE_DARK, k))
    for bx in (3, 11):
        for y in range(0, 11):
            c.set(bx, y, step(IRON, 2))
            c.set(bx + 1, y, step(IRON, 1))
    return c


def flower_box_iron():
    """A wrought-iron balconette basket: a handrail, scrolls, 1px balusters with 1px gaps, a bottom rail. Cutout: soil shows through."""
    c = Canvas()
    bars = (1, 3, 5, 7, 8, 10, 12, 14)
    for x in range(1, 15):
        c.set(x, 9, step(IRON, 4 if x % 4 else 3))   # the handrail, lit along its top
        c.set(x, 10, step(IRON, 2 if x in bars else 1))
        c.set(x, 14, step(IRON, 3))                  # bottom rail
        c.set(x, 15, step(IRON, 1))                  # base plate
        if x in bars:
            for y in range(11, 14):
                c.set(x, y, step(IRON, 3 if x not in (7, 8) else 2))
    for x in (2, 4, 6, 9, 11, 13):                   # a scroll between each pair of balusters: a curl, a dot
        c.set(x, 11, step(IRON, 2))
        c.set(x + (1 if x < 8 else -1) if x in (2, 9) else x, 12, step(IRON, 3))
    for x in (1, 14):                                # end posts a shade lighter, a finial pixel above the rail's end
        for y in range(9, 16):
            c.set(x, y, step(IRON, 4 if y in (9, 12) else 3))
    for x in (7, 8):                                 # the central double bar, with a ring
        c.set(x, 12, step(IRON, 4))
    # the underside (rows 0..10): the basket's floor, a riveted plate with drain holes, framed in a 1px rim
    for y in range(0, 11):
        for x in range(1, 15):
            edge = x in (1, 14) or y in (0, 10)
            c.set(x, y, step(IRON, 3 if edge else 2))
    for (x, y) in ((4, 3), (10, 3), (4, 7), (10, 7)):
        c.set(x, y, step(IRON, 0))
    for (x, y) in ((2, 1), (13, 1), (2, 9), (13, 9), (7, 5), (8, 5)):
        c.set(x, y, step(IRON, 4))
    return c


def flower_box_stone():
    """A pale carved sill box: a moulded lip, a shadow under it, two recessed panels round a boss, a base moulding."""
    rng = rng_for("city_kit", "flower_box_stone")
    C = CALCITE
    c = Canvas()
    rows = {9: 4, 10: 3, 11: 1, 12: 3, 13: 3, 14: 3, 15: 2}
    for y in range(9, 16):
        for x in range(1, 15):
            k = rows[y]
            if y in (12, 13, 14) and rng.random() < 0.14:
                k += rng.choice([-1, 1])
            if x == 1 and y >= 12:
                k += 1
            if x == 14 and y >= 12:
                k -= 1
            c.set(x, y, step(C, k))
    for x in range(1, 15):                      # the lip: a rolled edge, lit along its top, darker under
        c.set(x, 9, step(C, 4 if x % 5 else 3))
        c.set(x, 10, step(C, 2))
    for (px0, px1) in ((2, 6), (9, 13)):        # two recessed panels: shaded top and left, lit bottom and right
        for x in range(px0, px1 + 1):
            c.set(x, 12, step(C, 1))
            c.set(x, 14, step(C, 4))
            if x not in (px0, px1):
                c.set(x, 13, step(C, 2))
        c.set(px0, 13, step(C, 1))
        c.set(px1, 13, step(C, 4))
        c.set(px0, 14, step(C, 3))
        c.set(px1, 12, step(C, 3))
    for y in (12, 13, 14):                      # the boss between them
        c.set(7, y, step(C, 4))
        c.set(8, y, step(C, 2))
    c.set(7, 13, step(C, 4))
    # the underside (rows 0..10): a plain slab in shade, a chamfered rim round it
    for y in range(0, 11):
        for x in range(1, 15):
            k = 2
            if x in (1, 14) or y in (0, 10):
                k = 3 if (x == 1 or y == 0) else 1
            elif rng.random() < 0.16:
                k += rng.choice([-1, 1])
            c.set(x, y, step(C, k))
    return c


# ---------------------------------------------------------------------------------------------------------------------
# blooms: cutout clumps for the flower box's crossed planes. Rows 6..15 carry the plant (the model maps them 1:1 onto the
# planes' 10px height); rows 14-15 are stems only, so the clump sits in the soil.

LEAF = R("#10321a", "#1a4d22", "#276b2b", "#378a37", "#55a645", "#7cc15c")
BLOOM_RAMPS = {
    "red": R("#5a0d12", "#9a1a22", "#cf2c30", "#ec5448", "#ff8f78"),
    "yellow": R("#8a4c0a", "#d0860f", "#f4b620", "#ffd84a", "#fff3a0"),
    "blue": R("#1d2f7a", "#2f4cb8", "#5878e6", "#8ca4ff", "#c9d6ff"),
    "pink": R("#7a1f4e", "#c44a86", "#ec7db0", "#ffa6cc", "#ffd9ea"),
    "white": R("#a4a6b0", "#d0d2da", "#f2f2f4", "#ffffff", "#ffffff"),
}


def _leaf_mass(c, rng, top, spread, gap=0.1):
    """The green body of the clump: an uneven mound from row `top`, shaded dark below and light on top."""
    heights = []
    h = top
    for x in range(1, 15):
        h = max(top - spread, min(top + 1, h + rng.choice([-1, 0, 0, 1])))
        heights.append(h)
    for i, x in enumerate(range(1, 15)):
        h = heights[i]
        for y in range(h, 14):
            depth = (y - h) / max(1, 13 - h)
            k = 4 - int(depth * 3.2) + (1 if y == h else 0)
            k += rng.choice([-1, 0, 0, 1]) if rng.random() < 0.35 else 0
            if y > h and rng.random() < gap:
                continue
            c.set(x, y, step(LEAF, k))
    return heights


def _stems(c, rng, cols):
    for x in cols:
        for y in (14, 15):
            c.set(x, y, step(LEAF, 2 if y == 14 else 1))
        c.set(x + 1, 14, step(LEAF, 1)) if rng.random() < 0.3 else None


def _umbel(c, cx, cy, rp, rng):
    """A round blossom cluster of ~3px: a plus with a bright centre and a shadow pixel lower right."""
    for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1), (0, 0)):
        c.set(cx + dx, cy + dy, rp[2])
    c.set(cx, cy, rp[3])
    c.set(cx - 1, cy - 1, rp[3]) if rng.random() < 0.7 else None
    c.set(cx + 1, cy + 1, rp[1])
    c.set(cx, cy + 1, rp[1]) if rng.random() < 0.5 else None
    c.set(cx - 1, cy, rp[4]) if rng.random() < 0.5 else None


def _stalk(c, x, y0, y1):
    for y in range(y0, y1):
        if c.get(x, y)[3] == 0:
            c.set(x, y, step(LEAF, 2))


def bloom_red():
    """Geraniums: fat red umbels on short stalks over a dark leafy mound."""
    rng = rng_for("city_kit", "bloom_red")
    c = Canvas()
    _leaf_mass(c, rng, 9, 2)
    for cx, cy in ((3, 8), (7, 7), (11, 8), (5, 10), (9, 10), (13, 10)):
        _stalk(c, cx, cy + 2, 11)
        _umbel(c, cx, cy, BLOOM_RAMPS["red"], rng)
    _stems(c, rng, (3, 6, 9, 12))
    return c


def bloom_yellow():
    """Marigolds: compact orange-yellow pom-poms with a darker heart."""
    rng = rng_for("city_kit", "bloom_yellow")
    c = Canvas()
    _leaf_mass(c, rng, 9, 2)
    rp = BLOOM_RAMPS["yellow"]
    for cx, cy in ((2, 9), (5, 7), (8, 8), (11, 7), (13, 9), (6, 10), (10, 10)):
        _stalk(c, cx, cy + 2, 11)
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if abs(dx) + abs(dy) < 2 or rng.random() < 0.35:
                    c.set(cx + dx, cy + dy, rp[3] if dx + dy <= 0 else rp[2])
        c.set(cx, cy, rp[1])
        c.set(cx - 1, cy - 1, rp[4])
    _stems(c, rng, (3, 7, 10, 13))
    return c


def bloom_blue():
    """Lobelia and cornflower: many small blue blossoms frothing over the leaves."""
    rng = rng_for("city_kit", "bloom_blue")
    c = Canvas()
    _leaf_mass(c, rng, 9, 2, gap=0.14)
    rp = BLOOM_RAMPS["blue"]
    spots = [(2, 9), (4, 7), (6, 9), (8, 6), (9, 9), (11, 7), (13, 9), (3, 11), (7, 11), (12, 11), (10, 7), (5, 10)]
    for cx, cy in spots:
        if cy < 10:
            _stalk(c, cx, cy + 1, 11)
        for dx, dy in ((0, 0), (1, 0), (0, 1)):
            c.set(cx + dx, cy + dy, rp[2])
        c.set(cx, cy, rp[3])
        c.set(cx + 1, cy + 1, rp[1])
        c.set(cx - 1, cy, rp[4]) if rng.random() < 0.6 else None
    _stems(c, rng, (3, 6, 9, 12))
    return c


def bloom_pink():
    """Pink petunias: open trumpets, a paler throat."""
    rng = rng_for("city_kit", "bloom_pink")
    c = Canvas()
    _leaf_mass(c, rng, 9, 2)
    rp = BLOOM_RAMPS["pink"]
    for cx, cy in ((3, 8), (6, 7), (9, 8), (12, 7), (5, 10), (11, 10)):
        _stalk(c, cx, cy + 2, 11)
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, 1), (1, 1)):
            c.set(cx + dx, cy + dy, rp[2] if dy <= 0 else rp[1])
        c.set(cx, cy, rp[4])
        c.set(cx - 1, cy - 1, rp[3]) if rng.random() < 0.6 else None
        c.set(cx + 1, cy - 1, rp[2]) if rng.random() < 0.5 else None
    _stems(c, rng, (3, 6, 9, 12))
    return c


def bloom_white():
    """Daisies: white petals round a yellow eye."""
    rng = rng_for("city_kit", "bloom_white")
    c = Canvas()
    _leaf_mass(c, rng, 9, 2)
    rp = BLOOM_RAMPS["white"]
    eye = BLOOM_RAMPS["yellow"]
    for cx, cy in ((3, 8), (7, 7), (11, 8), (5, 10), (9, 10), (13, 10)):
        _stalk(c, cx, cy + 2, 11)
        for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            c.set(cx + dx, cy + dy, rp[2])
        for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            if rng.random() < 0.55:
                c.set(cx + dx, cy + dy, rp[1])
        c.set(cx - 1, cy, rp[3])
        c.set(cx, cy - 1, rp[3])
        c.set(cx, cy, eye[3])
    _stems(c, rng, (3, 6, 9, 12))
    return c


def bloom_herbs():
    """Kitchen herbs: sage, thyme and rosemary, no blossoms: grey-green leaf sprays, some pointing up like rosemary."""
    rng = rng_for("city_kit", "bloom_herbs")
    c = Canvas()
    _leaf_mass(c, rng, 10, 2, gap=0.16)
    sage = R("#44573a", "#667c52", "#8aa06c", "#a9bf8c")
    for x in (3, 7, 12):  # rosemary: narrow spikes rising out of the clump
        top = rng.choice([6, 7])
        for y in range(top, 11):
            c.set(x, y, step(LEAF, 4 if y < top + 2 else 3))
            if (y - top) % 2 == 1:
                c.set(x - 1, y, step(LEAF, 3))
                c.set(x + 1, y, step(LEAF, 2))
        c.set(x, top, step(LEAF, 5))
    for x, y in ((5, 9), (9, 9), (13, 10), (1, 10), (10, 8)):  # sage leaves, paler and rounder
        c.set(x, y, sage[2])
        c.set(x + 1, y, sage[1])
        c.set(x, y + 1, sage[1])
        c.set(x - 1, y, sage[3])
    _stems(c, rng, (3, 6, 9, 12))
    return c


# ---------------------------------------------------------------------------------------------------------------------
# shutters: one 16x32 design per colour, drawn from the street with the hinges on the LEFT; split into _upper (rows 0-15)
# and _lower (rows 16-31). The model mirrors the UV for hinge=right.

PAINTS = {
    "blue": R("#2f4468", "#44608f", "#5b7aa8", "#7491bb", "#93aed0", "#b4c7de"),     # faded cornflower
    "sage": R("#4a5844", "#667659", "#80906f", "#97a785", "#afbd9d", "#c7d2b8"),     # grey-green
    "oxblood": R("#2c1010", "#4a1b1a", "#682a26", "#823a32", "#9c4d42", "#b46658"),   # dark red-brown
    "natural": R("#2d1c0d", "#46301a", "#5f4524", "#795a30", "#926f3d", "#ad8851"),   # stained oak
    "green": R("#102a1e", "#1b4430", "#285c42", "#367556", "#4b8f6c", "#69a888"),     # bottle green
    "cream": R("#8a7c5c", "#aa9c78", "#c6b992", "#dacfa8", "#eae1c0", "#f6efd6"),     # pale cream
}
WORN = R("#3d3126", "#5a4a38", "#76624a", "#917b5e")  # bare grey-brown wood where the paint has gone


def _strap_hinge(c, x0, y0):
    """A black iron strap hinge on the hinge edge (the image's left): 5px long, 2px tall, a tapering tail, a nail head."""
    pts = [(0, 0, 3), (1, 0, 4), (2, 0, 3), (3, 0, 3), (4, 0, 2), (0, 1, 1), (1, 1, 2), (2, 1, 1), (3, 1, 1)]
    for dx, dy, k in pts:
        c.set(x0 + dx, y0 + dy, step(IRON, k))
    c.set(x0 + 1, y0, step(IRON, 4))


SW = 8  # a shutter is an 8px panel hugging the window side of its cell


def _shutter_painted(name):
    """One 8x32 design (a two-block shutter), hinge on the image's LEFT edge: frame, louvres above, panels below."""
    P = PAINTS[name]
    rng = rng_for("city_kit", "shutter8", name)
    c = Canvas(SW, 32)
    for y in range(32):
        for x in range(SW):
            c.set(x, y, step(P, 3))
    # the outer frame, a shade darker; the stiles and rails raised, lit on their top-left edges
    for x in range(SW):
        c.set(x, 0, step(P, 1))
        c.set(x, 31, step(P, 1))
    for y in range(32):
        c.set(0, y, step(P, 1))
        c.set(SW - 1, y, step(P, 1))
    for y in range(1, 31):
        c.set(1, y, step(P, 4))
        c.set(SW - 2, y, step(P, 2))
    for ry in (1, 29):                               # top and bottom rails
        for x in range(1, SW - 1):
            c.set(x, ry, step(P, 4))
            c.set(x, ry + 1, step(P, 2))
    for x in range(1, SW - 1):                       # the mid rail across the two halves
        c.set(x, 15, step(P, 4))
        c.set(x, 16, step(P, 3))
        c.set(x, 17, step(P, 2))
    # louvres in the upper half: a light edge then a shadow line, every 2px
    for y in range(3, 15):
        for x in range(2, SW - 2):
            k = 4 if (y - 3) % 2 == 0 else 1
            if x == SW - 3:
                k -= 1
            if rng.random() < 0.07:
                k += rng.choice([-1, 1])
            c.set(x, y, step(P, k))
    # two recessed panels in the lower half
    for (py0, py1) in ((18, 22), (25, 28)):
        for y in range(py0, py1 + 1):
            for x in range(2, SW - 2):
                k = 2
                if y == py0 or x == 2:
                    k = 1
                if y == py1 or x == SW - 3:
                    k = 4
                if (x, y) == (2, py1) or (x, y) == (SW - 3, py0):
                    k = 3
                elif k == 2 and rng.random() < 0.10:
                    k += rng.choice([-1, 1])
                c.set(x, y, step(P, k))
    _strap_hinge(c, 0, 1)
    _strap_hinge(c, 0, 16)
    _weather(c, P, rng)
    return c


def _weather(c, P, rng):
    """Worn paint: bare wood along the frame's outer and lower edges, a few flakes, a dark streak under each hinge."""
    W = c.w
    for y in range(1, 31):
        for x in (1, W - 2):
            if rng.random() < (0.16 if y > 16 else 0.08) and c.get(x, y)[:3] != step(IRON, 3)[:3]:
                c.set(x, y, WORN[rng.choice([1, 2, 2, 3])])
    for x in range(1, W - 1):
        if rng.random() < 0.28:
            c.set(x, 30, WORN[rng.choice([1, 2])])
        if rng.random() < 0.12:
            c.set(x, 1, WORN[rng.choice([2, 3])])
    for _ in range(4):
        x, y = rng.randrange(2, W - 2), rng.randrange(3, 30)
        p = c.get(x, y)
        if p[:3] not in [i[:3] for i in IRON] and p[3]:
            c.set(x, y, tint(p, 10, 9, 7) if rng.random() < 0.5 else mul(p, 0.82))
    for hy in (3, 18):
        for dy in range(0, 2):
            c.set(1, hy + dy, mul(c.get(1, hy + dy), 0.8))


def _shutter_natural():
    """Stained oak boards with a Z brace, the same hinges (8x32)."""
    P = PAINTS["natural"]
    rng = rng_for("city_kit", "shutter8", "natural")
    c = Canvas(SW, 32)
    for b in range(2):                                # two vertical boards, a dark seam at each right edge
        base = rng.choice([2, 3, 3, 4])
        for x in range(b * 4, b * 4 + 4):
            for y in range(32):
                k = base
                if x == b * 4:
                    k += 1
                if x == b * 4 + 3:
                    k = 0
                elif rng.random() < 0.2:
                    k += rng.choice([-1, 1])
                c.set(x, y, step(P, k))
        kx, ky = b * 4 + rng.choice([1, 2]), rng.randrange(8, 24)   # a knot
        c.set(kx, ky, step(P, 1))
        c.set(kx, ky + 1, step(P, 1))
    for x in range(SW):
        c.set(x, 0, step(P, 1))
        c.set(x, 31, step(P, 1))
    for y in range(32):
        c.set(0, y, step(P, 1))
        c.set(SW - 1, y, step(P, 1))
    # the Z: a ledger top and bottom and a diagonal brace between them
    for ly in (4, 25):
        for x in range(1, SW - 1):
            c.set(x, ly, step(P, 5))
            c.set(x, ly + 1, step(P, 3))
            c.set(x, ly + 2, step(P, 1))
        c.set(1, ly + 1, step(IRON, 3))
        c.set(SW - 2, ly + 1, step(IRON, 3))
    for y in range(7, 25):
        x0 = 5 - (y - 7) * 4 // 17      # a clean diagonal board: lit left edge, a shadow line under its right
        for dx, k in ((0, 5), (1, 4), (2, 2)):
            c.set(x0 + dx, y, step(P, k))
    _strap_hinge(c, 0, 1)
    _strap_hinge(c, 0, 16)
    return c


def _halves(c):
    """Split a 8x32 design into the 16x16 _lower and _upper files; the design sits in the left 8 columns."""
    low, up = Canvas(), Canvas()
    for y in range(16):
        for x in range(c.w):
            up.set(x, y, c.get(x, y))
            low.set(x, y, c.get(x, y + 16))
    return low, up


def shutters():
    out = {}
    for name in PAINTS:
        full = _shutter_natural() if name == "natural" else _shutter_painted(name)
        out[f"shutter_{name}_lower"], out[f"shutter_{name}_upper"] = _halves(full)
    return out


# ---------------------------------------------------------------------------------------------------------------------
# drainpipe. Layout: the pipe column x 6..9 (rows 0..15, seam rings at rows 7 and 15), the bracket strap in x 10..15
# (rows 0..5 its top, row 6 its edge).

LEAD = [mul(c, 1.11) for c in R("#1f2229", "#2f333c", "#41454f", "#575c68", "#767b88")]  # the pipe column averages ~(86,90,99)


def drainpipe():
    rng = rng_for("city_kit", "drainpipe")
    c = Canvas()
    cols = {6: 4, 7: 3, 8: 2, 9: 1}  # the highlight column on the left, a dark shadow edge on the right
    for y in range(16):
        for x, k in cols.items():
            kk = k
            if rng.random() < 0.12:
                kk += rng.choice([-1, 1])
            if y == 15:        # the seam ring, one per length (two read as a chain from the street): a dark joint ...
                kk = max(0, k - 2)
            elif y == 0:       # ... and the lip of the next length just below it
                kk = min(4, k + 1)
            c.set(x, y, step(LEAD, kk))
    for y in range(0, 6):      # the bracket strap's top: dark iron
        for x in range(10, 16):
            c.set(x, y, step(IRON, 2 + (1 if (x + y) % 5 == 0 else 0)))
    for x in range(10, 16):
        c.set(x, 6, step(IRON, 3))
    c.set(12, 6, step(IRON, 4))   # a rivet on the strap's front
    c.set(14, 6, step(IRON, 4))
    return c


def drainpipe_head():
    """The hopper head. Layout: body front x0..7 rows 0..6, rim front x0..9 rows 7..9, rim top x0..9 rows 10..15."""
    rng = rng_for("city_kit", "drainpipe_head")
    c = Canvas()
    for y in range(7):                                  # the hopper's body: sheet metal with an embossed diamond
        for x in range(8):
            k = 3 - (1 if y > 4 else 0) + (1 if x == 0 else 0) - (1 if x == 7 else 0)
            if rng.random() < 0.12:
                k += rng.choice([-1, 1])
            c.set(x, y, step(LEAD, k))
    for (x, y, k) in ((3, 1, 4), (4, 2, 4), (2, 2, 4), (3, 3, 4), (4, 1, 1), (5, 2, 1), (4, 3, 1), (3, 4, 1)):
        c.set(x, y, step(LEAD, k))
    c.set(3, 2, step(LEAD, 2))
    c.set(0, 0, step(LEAD, 4))
    for x in range(8):
        c.set(x, 6, step(LEAD, 1))                      # the seam where the hopper meets its pipe
    for x in range(10):                                 # the moulded rim: a bright roll, a body, a shadow lip
        c.set(x, 7, step(LEAD, 4))
        c.set(x, 8, step(LEAD, 3 if x not in (0, 9) else 2))
        c.set(x, 9, step(LEAD, 1))
    c.set(2, 8, step(LEAD, 2))
    c.set(7, 8, step(LEAD, 2))
    for y in range(10, 16):                             # the rim's top face and the dark opening
        for x in range(10):
            edge = x in (0, 9) or y in (10, 15)
            if edge:
                c.set(x, y, step(LEAD, 4 if (x < 5 and y == 10) or x == 0 else 3))
            elif x == 1 or y == 11:
                c.set(x, y, step(LEAD, 1))
            else:
                c.set(x, y, (14, 14, 18, 255) if rng.random() > 0.2 else (24, 24, 30, 255))
    return c


# ---------------------------------------------------------------------------------------------------------------------
# trellis and ivy

TIMBER = R("#3f362a", "#5c5140", "#7d705a", "#9c8f76", "#bbae94", "#d3c8ae")
IVY = R("#17301c", "#223f24", "#2c5428", "#386a30", "#4a8438", "#609c48", "#7bb35c")


def trellis():
    """A diamond lattice of weathered pale laths, ~5px apart, 1-2px thick, over and under at the crossings. Cutout."""
    rng = rng_for("city_kit", "trellis")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            a = (x + y) % 8      # "/" laths
            b = (x - y) % 8      # "\" laths
            a_on = a in (0, 1) and not (a == 1 and y % 2)
            b_on = b in (0, 1) and not (b == 1 and y % 2 == 0)
            if not (a_on or b_on):
                continue
            over_a = ((x + y) // 8 + (x - y) // 8) % 2 == 0
            lath_a = a_on and (over_a or not b_on)
            if lath_a:
                k = 4 if a == 0 else 2
            else:
                k = 3 if b == 0 else 1
            if a_on and b_on and not lath_a:
                k = 2
            if rng.random() < 0.14:
                k += rng.choice([-1, 1])
            c.set(x, y, step(TIMBER, k))
    for (x, y) in ((0, 0), (8, 0), (0, 8), (8, 8), (4, 4), (12, 4), (4, 12), (12, 12)):   # nail heads at the crossings
        c.set(x, y, step(TIMBER, 0))
    return c


def trellis_ivy():
    """Ivy leaves, baked greens (not biome tinted), ~60% cover with gaps, tileable both ways."""
    rng = rng_for("city_kit", "trellis_ivy")
    c = Canvas()
    shapes = [
        ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)),
        ((0, 0), (-1, 0), (0, -1), (-1, -1), (1, 0)),
        ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0)),
        ((0, 0), (0, -1), (0, 1), (1, 0), (1, 1), (-1, -1)),
        ((0, 0), (-1, 0), (0, 1), (-1, 1), (1, 0), (0, -1)),
    ]

    def leaf(cx, cy, k):
        for dx, dy in rng.choice(shapes):
            light = 2 if dx + dy <= -1 else 1 if dx + dy == 0 else 0 if dx + dy == 1 else -1
            c.set((cx + dx) % 16, (cy + dy) % 16, step(IVY, k + light - 1))

    # vines first (dark, thin), leaves over them
    for vx in (2, 7, 12):
        x = vx
        for y in range(16):
            if rng.random() < 0.35:
                x += rng.choice([-1, 1])
            c.set(x % 16, y, step(IVY, 0))
    for _ in range(80):
        leaf(rng.randrange(16), rng.randrange(16), rng.choice([2, 3, 3, 4]))
        if len(opaque(c)) >= 0.62 * 256:
            break
    return c


# ---------------------------------------------------------------------------------------------------------------------
# lit windows. Each 2-tall style is drawn as one 16x32 design (the window fills the whole face; the outer pixel is the
# reveal, never wall) and split into _upper (rows 0-15) and _lower (rows 16-31). The glass shows a dim room, never a flat
# colour: a warm brown-black interior, a faint sky reflection, a curtain. `_glow` is transparent except the glass, painted
# lamp-amber (centre bright, edges deeper, curtains darker) so a lit window glows through the same bars and leading.

ROOM_TOP = (34, 26, 22)
ROOM_BOT = (58, 43, 33)
SKY = (126, 146, 166)

# frame materials
DARK_TIMBER = R("#1c130b", "#2e2014", "#43301d", "#5a4129", "#765737")
CREAM = R("#8e8468", "#b4aa8c", "#d6cdb0", "#ebe4cc", "#f8f3e2")
PALE_STONE = R("#8d8d88", "#b0b0aa", "#cccbc4", "#e2e1da", "#f2f1ea")
WHITE_PAINT = R("#a8a49a", "#cbc7bb", "#e3e0d6", "#f2f0e8", "#fcfbf6")
VERDIGRIS = R("#2c6a58", "#3a8470", "#4d9d86", "#6bb69d", "#8fcdb8")
DARK_STONE = R("#18181c", "#25252a", "#34343a", "#464650", "#5c5c68")
STEEL = R("#23272d", "#363c45", "#4a525d", "#626b78", "#7d8793")
SHOP_GREEN = R("#060b08", "#0f1a14", "#182a22", "#243f35", "#35574a")
SIGN_WOOD = R("#2a1b0e", "#43301a", "#5e4528", "#7a5c36", "#977549")


class Win:
    """A window face under construction: the pixels, and which of them are glass (and how bright a lamp lights them)."""

    def __init__(self, h, name):
        self.h = h
        self.c = Canvas(16, h)
        self.glass = {}
        self.rng = rng_for("city_kit", "window", name)

    def fill(self, x0, y0, x1, y1, col):
        box(self.c, x0, y0, x1, y1, col)

    def bevel(self, x0, y0, x1, y1, rp, thick=2, noise=0.12, raised=True):
        """A frame ring: its outer top/left edge lit, outer bottom/right in shade; reversed on the inside edge."""
        for y in range(y0, y1):
            for x in range(x0, x1):
                inner = x - x0 >= thick and x1 - 1 - x >= thick and y - y0 >= thick and y1 - 1 - y >= thick
                if inner:
                    continue
                k = 2
                if y == y0 or x == x0:
                    k = 3 if raised else 2
                if y == y1 - 1 or x == x1 - 1:
                    k = 1
                if thick >= 2:
                    # the inner lip of the ring
                    if (y == y0 + thick - 1 and x0 <= x < x1 and x - x0 >= 0 and x1 - 1 - x >= 0 and y0 + thick - 1 < y1 - thick) or (x == x0 + thick - 1 and y0 + thick - 1 <= y):
                        k = 1 if raised else 3
                    if y == y1 - thick or x == x1 - thick:
                        k = 3 if raised else 1
                if self.rng.random() < noise:
                    k += self.rng.choice([-1, 1])
                self.c.set(x, y, step(rp, k))

    def mark(self, x, y, f=1.0):
        self.glass[(x, y)] = f

    def room(self, x, y, y0, y1, tintc, tintk):
        """The dim interior seen through the glass at (x, y), mixed with the glass's own tint."""
        t = (y - y0) / max(1, y1 - y0 - 1)
        base = lerp(ROOM_TOP, ROOM_BOT, t)
        base = lerp(base, tintc, tintk)
        if self.rng.random() < 0.14:
            base = mul(base, self.rng.choice([0.92, 1.07]))
        return base

    def sky(self, x, y, y0, y1):
        """How much sky the glass at (x, y) reflects: a lighter head, a diagonal streak that runs across neighbouring panes."""
        t = 0.0
        top = y - y0
        if top < 4:
            t += (0.26, 0.17, 0.10, 0.05)[top]
        if (x + y) % 14 in (6, 7):
            t += 0.16 if (x + y) % 14 == 6 else 0.09
        return t

    def pane(self, x0, y0, x1, y1, tintc=(60, 72, 68), tintk=0.35, reflect=True, rows=None, mask=None):
        """Glass over [x0,x1) x [y0,y1): the room, the glass's tint, the sky in it (a lit head, a diagonal streak, a glint)."""
        ry0, ry1 = rows if rows else (y0, y1)
        for y in range(y0, y1):
            for x in range(x0, x1):
                if mask and not mask(x, y):
                    continue
                col = self.room(x, y, ry0, ry1, tintc, tintk)
                if reflect:
                    col = lerp(col, SKY, self.sky(x, y, y0, y1) if (x1 - x0) >= 3 else 0.0)
                    if (x - x0) + (y - y0) == 1 and (x1 - x0) >= 3 and (y1 - y0) >= 3:
                        col = lerp(col, SKY, 0.5)   # the glint at the pane's upper-left corner
                self.c.set(x, y, col)
                self.mark(x, y)

    def curtain(self, pts, light, mid, dark, dim=0.74, fold=3):
        """Paint a curtain over some glass pixels: vertical folds in three tones; the lamp lights it a little less."""
        for (x, y) in pts:
            if (x, y) not in self.glass:
                continue
            k = (x + y // 7) % fold
            col = (dark, mid, light)[[0, 1, 2][k % 3]] if fold == 3 else mid
            self.c.set(x, y, col)
            self.glass[(x, y)] = dim

    def shade_edges(self, lead):
        """Light from the top-left: glass pixels beside a bar are lit on that bar's lower-right and shaded on its other side."""
        for (x, y) in list(self.glass):
            if self.glass[(x, y)] < 1.0:
                continue
            p = self.c.get(x, y)
            if (x - 1, y) in lead or (x, y - 1) in lead:
                self.c.set(x, y, mul(p, 1.2))
            elif (x + 1, y) in lead or (x, y + 1) in lead:
                self.c.set(x, y, mul(p, 0.82))

    def glow(self):
        g = Canvas(16, self.h)
        cx, cy = 7.5, self.h * 0.56
        rng = rng_for("city_kit", "glow", self.h, len(self.glass))
        for (x, y), f in self.glass.items():
            d = math.hypot((x - cx) / 8.0, (y - cy) / (self.h / 2.0))
            t = min(1.0, d * 0.95) ** 1.15
            col = lerp((255, 196, 110), (214, 128, 52), t)
            if rng.random() < 0.18:
                col = lerp(col, (255, 214, 140), 0.18) if rng.random() < 0.5 else lerp(col, (200, 112, 44), 0.2)
            g.set(x, y, mul(col, f))
        return g

    def split(self):
        """(lower, upper) for a 16x32 design."""
        low, up = Canvas(), Canvas()
        for y in range(16):
            for x in range(16):
                up.set(x, y, self.c.get(x, y))
                low.set(x, y, self.c.get(x, y + 16))
        return low, up

    def split_glow(self):
        g = self.glow()
        low, up = Canvas(), Canvas()
        for y in range(16):
            for x in range(16):
                up.set(x, y, g.get(x, y))
                low.set(x, y, g.get(x, y + 16))
        return low, up


def _sill(w, y0, rp, lip=True):
    """The window's sill, 3-4px tall, across the full face: a lit top edge, a face, a shadow line."""
    h = 32 if w.h == 32 else w.h
    n = h - y0
    for i in range(n):
        for x in range(16):
            k = {0: 4, 1: 3, 2: 2, 3: 1}.get(i, 2) if n == 4 else {0: 4, 1: 2, 2: 1}[i]
            if x in (0, 15):
                k -= 1
            if w.rng.random() < 0.1:
                k += w.rng.choice([-1, 1])
            w.c.set(x, y0 + i, step(rp, k))


def _lead_lines(w, x0, y0, x1, y1, period, lead_col, ox=0, oy=0):
    lead = set()
    for y in range(y0, y1):
        for x in range(x0, x1):
            u, v = x - x0 + ox, y - y0 + oy
            if (u + v) % period == 0 or (u - v) % period == 0:
                lead.add((x, y))
    return lead


def window_leaded():
    """Bellwatch and Wallring: dark timber, leaded diamond panes on green-grey glass, a red curtain."""
    w = Win(32, "leaded")
    w.bevel(0, 0, 16, 32, DARK_TIMBER, 2)
    w.pane(2, 2, 14, 28, tintc=(96, 120, 110), tintk=0.58, reflect=True)
    lead = _lead_lines(w, 2, 2, 14, 28, 6, None, ox=3, oy=0)
    for (x, y) in lead:
        w.c.set(x, y, (30, 33, 34, 255) if (x + y) % 5 else (44, 47, 48, 255))
        w.glass.pop((x, y), None)
    # every diamond catches a little sky at its upper-left, deeper in the old glass's waves
    for (x, y), f in list(w.glass.items()):
        if (x - 1, y) in lead and (x, y - 1) in lead or ((x - 1, y - 1) in lead and (x + y) % 3 == 0):
            w.c.set(x, y, lerp(w.c.get(x, y), SKY, 0.4))
    w.shade_edges(lead)
    # a transom rail in the middle
    for x in range(2, 14):
        w.c.set(x, 15, step(DARK_TIMBER, 3))
        w.c.set(x, 16, step(DARK_TIMBER, 1))
        w.glass.pop((x, 15), None)
        w.glass.pop((x, 16), None)
    # the red curtain, gathered at the left, one fold longer on the lower
    pts = [(x, y) for y in range(2, 22) for x in range(2, 5 if y < 14 else 4 if y < 19 else 3)]
    w.curtain(pts, rgb("#a8383a"), rgb("#7e2427"), rgb("#521719"))
    for (x, y) in lead:  # the lead stays in front of the curtain
        if (x, y) in lead and 2 <= x < 14 and 2 <= y < 28:
            w.c.set(x, y, (30, 33, 34, 255) if (x + y) % 5 else (44, 47, 48, 255))
    _sill(w, 28, DARK_TIMBER)
    return w


def window_sash():
    """Market: cream-white painted frame, 6 over 6 glazing bars, the meeting rail, a lace curtain high up."""
    w = Win(32, "sash")
    w.bevel(0, 0, 16, 32, CREAM, 2, noise=0.08)
    cols = [(2, 5), (6, 10), (11, 14)]
    rows_u = [(2, 8), (9, 15)]
    rows_l = [(17, 23), (24, 29)]
    for (ya, yb) in rows_u + rows_l:
        for (xa, xb) in cols:
            w.pane(xa, ya, xb, yb, tintc=(70, 88, 100), tintk=0.3, rows=(2, 29))
    bars = set()
    for x in (5, 10):
        for y in range(2, 29):
            bars.add((x, y))
    for y in (8, 23):
        for x in range(2, 14):
            bars.add((x, y))
    for (x, y) in bars:
        w.c.set(x, y, step(CREAM, 3 if (x + y) % 3 else 4))
        w.glass.pop((x, y), None)
    # the meeting rail: a fat cream rail across rows 15-16
    for y in (15, 16):
        for x in range(2, 14):
            w.c.set(x, y, step(CREAM, 4 if y == 15 else 2))
            w.glass.pop((x, y), None)
    for x in range(2, 14):
        w.c.set(x, 14, mul(w.c.get(x, 14), 0.8))
    # the lace curtain: a cream valance with a scalloped hem, then a sparse web of stitches, over the top pane row
    for y in range(2, 8):
        for x in range(2, 14):
            if (x, y) not in w.glass:
                continue
            if y in (2, 3):
                amt = 0.78 if y == 2 else (0.70 if x % 2 == 0 else 0.0)
            else:
                amt = 0.0
                if (x + y) % 3 == 0 and y % 2 == 0:
                    amt = 0.5
                elif (x * 2 + y) % 5 == 0:
                    amt = 0.32
            if amt:
                w.c.set(x, y, lerp(w.c.get(x, y), (224, 216, 194), amt))
                w.glass[(x, y)] = 0.8
    _sill(w, 29, CREAM)
    return w


def window_arched():
    """High Terrace: a pale stone surround, a round-headed opening with voussoirs and a keystone, glazing bars, a blue curtain."""
    w = Win(32, "arched")
    S = PALE_STONE
    rng = w.rng
    cx, cy, rad = 8.0, 8.0, 6.0
    glass = set()
    for y in range(2, 28):
        for x in range(2, 14):
            if y >= 8 or math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= rad + 0.05:
                glass.add((x, y))
    for y in range(32):
        for x in range(16):
            if (x, y) in glass:
                continue
            k = 3
            if rng.random() < 0.14:
                k += rng.choice([-1, 1])
            if x in (0, 15) or y == 0:
                k = 2 if (x != 0 and y != 0) else 3     # the surround's outer edge, a shade down so it parts from the wall
            if (x - 1, y) in glass or (x, y - 1) in glass:
                k = 1                                    # the reveal: shaded on the sides the light misses (top and left of the glass)
            elif (x + 1, y) in glass or (x, y + 1) in glass:
                k = 4                                    # lit on the right and below
            w.c.set(x, y, step(S, k))
    # voussoir joints: radial lines through the arch band
    for y in range(0, 8):
        for x in range(16):
            if (x, y) in glass:
                continue
            r = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if rad + 0.9 < r < rad + 2.6:
                ang = math.degrees(math.atan2(-(y + 0.5 - cy), x + 0.5 - cx))
                if any(abs(ang - a) < 4.2 for a in (150, 120, 60, 30)):
                    w.c.set(x, y, step(S, 1))
    # the keystone: a lighter block in the crown, its joints either side, standing a pixel into the glass
    for y in range(0, 3):
        for x in range(6, 10):
            if y == 2 and x not in (7, 8):
                continue
            w.c.set(x, y, step(S, 4 if (x == 6 or y == 0) else 3))
    for y in range(0, 2):
        w.c.set(5, y, step(S, 1))
        w.c.set(10, y, step(S, 1))
    w.c.set(6, 2, step(S, 1))
    w.c.set(9, 2, step(S, 1))
    for (x, y) in glass:
        col = w.room(x, y, 2, 28, (60, 86, 118), 0.28)
        col = lerp(col, SKY, w.sky(x, y, 2, 28))
        if (x - 1, y - 1) not in glass or (x - 1, y) not in glass:
            col = lerp(col, SKY, 0.18)
        w.c.set(x, y, col)
        w.mark(x, y)
    bars = set()
    for y in range(2, 28):
        for x in (5, 10):
            if (x, y) in glass:
                bars.add((x, y))
    for y in (10, 24):
        for x in range(2, 14):
            if (x, y) in glass:
                bars.add((x, y))
    # a blue curtain either side, caught up
    pts = [(x, y) for (x, y) in glass if (x <= 4 and 5 <= y < 21 and x < 5) or (x >= 11 and 5 <= y < 21)]
    w.curtain([p for p in pts if p not in bars], rgb("#5a7ac0"), rgb("#3f5c9c"), rgb("#2a4072"))
    for (x, y) in bars:
        w.c.set(x, y, step(WHITE_PAINT, 3 if (x + y) % 2 else 2))
        w.glass.pop((x, y), None)
    for y in range(28, 32):                                  # the sill: a stone slab with a lit lip
        for x in range(16):
            k = (4, 3, 2, 1)[y - 28] - (1 if x in (0, 15) else 0)
            if rng.random() < 0.12:
                k += rng.choice([-1, 1])
            w.c.set(x, y, step(S, k))
    return w


def window_cottage():
    """Garden Ward: white frame inside a verdigris-green outer frame, a cross mullion per half, a red-and-white curtain."""
    w = Win(32, "cottage")
    for y in range(32):
        for x in range(16):
            w.c.set(x, y, step(WHITE_PAINT, 3))
    # verdigris outer frame (1px), white inner frame (1px)
    for y in range(32):
        for x in range(16):
            if x in (0, 15) or y == 0:
                w.c.set(x, y, step(VERDIGRIS, 2 if x != 15 else 1) if w.rng.random() > 0.12 else step(VERDIGRIS, 3))
            elif x in (1, 14) or y == 1:
                w.c.set(x, y, step(WHITE_PAINT, 4 if (x == 1 or y == 1) else 2))
    panes = [(2, 8, 2, 8), (8, 14, 2, 8), (2, 8, 9, 15), (8, 14, 9, 15), (2, 8, 17, 22), (8, 14, 17, 22), (2, 8, 23, 28), (8, 14, 23, 28)]
    for (xa, xb, ya, yb) in panes:
        w.pane(xa, ya, xb, yb, tintc=(74, 96, 92), tintk=0.3, rows=(2, 28))
    bars = set()
    for y in range(2, 28):
        for x in (7, 8):
            bars.add((x, y))
    for y in (8, 15, 16, 22):
        for x in range(2, 14):
            bars.add((x, y))
    for (x, y) in bars:
        k = 4 if (x == 7 or y in (8, 15, 22)) else 2
        w.c.set(x, y, step(WHITE_PAINT, k))
        w.glass.pop((x, y), None)
    # red-and-white gingham curtains in the upper panes, tied back
    for y in range(2, 15):
        for x in list(range(2, 5 if y < 11 else 4)) + list(range(11 if y < 11 else 12, 14)):
            if (x, y) in w.glass:
                u = (x - 2) if x < 8 else (13 - x)
                chk = (u // 2 + y // 2) % 2
                w.c.set(x, y, rgb("#b83c3c") if chk else rgb("#e9e3d6"))
                w.glass[(x, y)] = 0.78
    for y in (2, 3):                                           # a plain red valance across the top of each curtain
        for x in list(range(2, 5)) + list(range(11, 14)):
            if (x, y) in w.glass:
                w.c.set(x, y, rgb("#9a2f30") if y == 2 else rgb("#b83c3c"))
    for (x, y) in bars:
        k = 4 if (x == 7 or y in (8, 15, 22)) else 2
        w.c.set(x, y, step(WHITE_PAINT, k))
    # sill: a white board with a green drip edge
    for i in range(4):
        for x in range(16):
            w.c.set(x, 28 + i, step(WHITE_PAINT, (4, 3, 2, 1)[i]) if i < 3 else step(VERDIGRIS, 1 if x else 0))
    for y in range(28, 32):
        for x in (0, 15):
            w.c.set(x, y, step(VERDIGRIS, 2 if x == 0 else 1))
    return w


def window_warehouse():
    """Lantern Quay's upper storeys: dark timber, 3x3 small panes a half, dusty grey-brown glass."""
    w = Win(32, "warehouse")
    w.bevel(0, 0, 16, 32, DARK_TIMBER, 2)
    cols = [(2, 5), (6, 10), (11, 14)]
    rows = [(2, 6), (7, 11), (12, 15), (17, 20), (21, 24), (25, 28)]
    for (ya, yb) in rows:
        for (xa, xb) in cols:
            w.pane(xa, ya, xb, yb, tintc=(104, 92, 76), tintk=0.55, rows=(2, 28))
    bars = set()
    for x in (5, 10):
        for y in range(2, 28):
            bars.add((x, y))
    for y in (6, 11, 15, 16, 20, 24):
        for x in range(2, 14):
            bars.add((x, y))
    for (x, y) in bars:
        k = 3 if (x in (5, 10) and x % 2 or y in (6, 11, 20, 24)) else 2
        w.c.set(x, y, step(DARK_TIMBER, k))
        w.glass.pop((x, y), None)
    # dust and cobweb: a few pale specks on the glass, a grey corner web in the top-left pane
    for _ in range(7):
        x, y = w.rng.randrange(2, 14), w.rng.randrange(2, 28)
        if (x, y) in w.glass:
            w.c.set(x, y, lerp(w.c.get(x, y), (170, 158, 138), 0.38))
    for (x, y) in ((2, 2), (3, 2), (2, 3), (4, 2), (2, 4), (3, 3)):
        w.c.set(x, y, lerp(w.c.get(x, y), (190, 188, 180), 0.45))
        w.glass[(x, y)] = 0.85
    _sill(w, 28, DARK_TIMBER)
    return w


def window_barred():
    """Quay and Works ground storeys: a dark stone frame, three vertical iron bars in front of grimy glass."""
    w = Win(32, "barred")
    for y in range(32):
        for x in range(16):
            k = 2 + (1 if w.rng.random() < 0.3 else 0) - (1 if w.rng.random() < 0.3 else 0)
            w.c.set(x, y, step(DARK_STONE, k))
    # stone jambs: a lit left edge, a shadowed right edge, a lintel course line
    for y in range(32):
        w.c.set(0, y, step(DARK_STONE, 3))
        w.c.set(1, y, step(DARK_STONE, 2))
        w.c.set(14, y, step(DARK_STONE, 1))
        w.c.set(15, y, step(DARK_STONE, 0))
    for x in range(16):
        w.c.set(x, 0, step(DARK_STONE, 3))
        w.c.set(x, 1, step(DARK_STONE, 1))
    w.pane(2, 2, 14, 28, tintc=(44, 42, 34), tintk=0.5, rows=(2, 28))
    for y in range(2, 28):
        if y % 6 == 0:
            for x in range(2, 14):
                w.c.set(x, y, mul(w.c.get(x, y), 0.8))
    bars = set()
    for x in (4, 7, 10):
        for y in range(0, 31):
            bars.add((x, y))
    for (x, y) in bars:
        k = 4 if y % 5 == 1 else 3
        w.c.set(x, y, step(IRON, k))
        w.glass.pop((x, y), None)
        if (x + 1, y) in w.glass:
            w.c.set(x + 1, y, mul(w.c.get(x + 1, y), 0.7))   # the bar's shadow on the glass
    for x in range(2, 14):                       # one flat tie bar across the middle, riveted
        if x not in (4, 7, 10):
            w.c.set(x, 15, step(IRON, 3))
            w.glass.pop((x, 15), None)
    for bx in (4, 7, 10):
        w.c.set(bx, 15, step(IRON, 4))
    _sill(w, 28, DARK_STONE)
    return w


def window_works():
    """The Works: a steel frame, a grid of small panes (4x4 a half), sooty glass, a pane or two broken."""
    w = Win(32, "works")
    for y in range(32):
        for x in range(16):
            w.c.set(x, y, step(STEEL, 2))
    for y in range(32):
        for x in range(16):
            k = 2
            if x in (0,) or y == 0:
                k = 3
            if x in (15,):
                k = 1
            if x in (1, 14):
                k = 2 if x == 1 else 1
            w.c.set(x, y, step(STEEL, k + (1 if w.rng.random() < 0.1 else 0)))
    cols = [(2, 4), (5, 7), (9, 11), (12, 14)]
    rows_u = [(1, 4), (5, 8), (9, 12), (13, 16)]
    rows_l = [(17, 19), (20, 22), (23, 25), (26, 28)]
    broken = {(3, 0), (1, 2)}
    for ri, (ya, yb) in enumerate(rows_u + rows_l):
        for ci, (xa, xb) in enumerate(cols):
            if (ci, ri) in broken:
                for y in range(ya, yb):
                    for x in range(xa, xb):
                        w.c.set(x, y, (14, 12, 12, 255) if w.rng.random() > 0.2 else (24, 20, 18, 255))
                        w.mark(x, y, 0.35)
                w.c.set(xa, ya, lerp(w.c.get(xa, ya), SKY, 0.5))   # a shard still holds in the corner
                continue
            w.pane(xa, ya, xb, yb, tintc=(52, 50, 46), tintk=0.6, rows=(1, 28), reflect=(xb - xa) > 2)
    # bars: the grid in steel with a lit top-left edge
    for (x, y) in [(x, y) for x in range(1, 15) for y in range(1, 29) if (x, y) not in w.glass]:
        k = 3 if (x % 2 == 0 or y % 2 == 0) else 2
        w.c.set(x, y, step(STEEL, k))
    for y in range(1, 29):
        w.c.set(7, y, step(STEEL, 3))
        w.c.set(8, y, step(STEEL, 1))
    _sill(w, 28, STEEL)
    # rust streaks under the bottom bars, soot above
    for x in (3, 9, 12):
        w.c.set(x, 29, rgb("#6a4630"))
        w.c.set(x, 30, rgb("#4e3324"))
    return w


def window_shop():
    """Shopfronts: a dark green-black painted frame; a stall board and two shelves of goods below, a transom bar and the
    shop's warm dark interior above."""
    w = Win(32, "shop")
    G = SHOP_GREEN
    for y in range(32):
        for x in range(16):
            k = 2 if (x + y) % 7 else 1
            if x in (0, 1) or y in (0, 1):
                k = 3 if (x == 0 or y == 0) else 2
            if x in (14, 15):
                k = 1 if x == 15 else 2
            w.c.set(x, y, step(G, k))
    # upper: a thin transom light, the bar, then the big pane
    w.pane(2, 2, 14, 3, tintc=(58, 70, 62), tintk=0.3, rows=(2, 29), reflect=False)
    for x in range(2, 14):
        w.c.set(x, 3, step(G, 4))
        w.c.set(x, 4, step(G, 1))
    w.pane(2, 5, 14, 29, tintc=(66, 74, 64), tintk=0.25, rows=(5, 29))
    # the stall board: three px of timber with a lit top edge and a ledge
    for y in range(29, 32):
        for x in range(2, 14):
            w.c.set(x, y, step(SIGN_WOOD, (4, 3, 1)[y - 29]) if w.rng.random() > 0.1 else step(SIGN_WOOD, 2))
        w.glass.pop((x, y), None)
    for x in range(2, 14):
        w.glass.pop((x, 29), None); w.glass.pop((x, 30), None); w.glass.pop((x, 31), None)
    # shelves
    for sy in (22, 27):
        for x in range(2, 14):
            w.c.set(x, sy, step(SIGN_WOOD, 3))
            w.c.set(x, sy + 1, step(SIGN_WOOD, 0))
            w.glass.pop((x, sy), None)
            w.glass.pop((x, sy + 1), None)

    def good(pts, light, mid, dark, dim=0.72):
        for (x, y, k) in pts:
            if (x, y) in w.glass or w.c.get(x, y)[3]:
                w.c.set(x, y, (dark, mid, light)[k])
                if (x, y) in w.glass:
                    w.glass[(x, y)] = dim
    # top shelf (y 22): loaves and jars
    good([(3, 21, 2), (4, 21, 1), (5, 21, 1), (3, 20, 1), (4, 20, 0), (5, 20, 0)], rgb("#cf9448"), rgb("#a86e30"), rgb("#6e4420"))
    good([(7, 21, 2), (8, 21, 1), (9, 21, 1), (7, 20, 1), (8, 20, 1), (9, 20, 0)], rgb("#cf9448"), rgb("#a86e30"), rgb("#6e4420"))
    good([(11, 21, 1), (12, 21, 1), (11, 20, 2), (12, 20, 1), (11, 19, 0), (12, 19, 0)], rgb("#9a4a3a"), rgb("#7a3228"), rgb("#4a1c18"))
    good([(11, 18, 2), (12, 18, 1)], rgb("#c8c0a8"), rgb("#9c9480"), rgb("#6a6454"))
    # bottom shelf (y 27): bottles and folded cloth
    good([(3, 26, 2), (3, 25, 1), (3, 24, 1), (3, 23, 0), (5, 26, 2), (5, 25, 1), (5, 24, 1), (5, 23, 0)], rgb("#58a864"), rgb("#2e7c42"), rgb("#164a28"))
    good([(7, 26, 2), (7, 25, 2), (7, 24, 1), (8, 26, 1), (8, 25, 1), (8, 24, 0), (7, 23, 0)], rgb("#e0a838"), rgb("#b87a20"), rgb("#7c4c12"))
    good([(10, 26, 2), (11, 26, 2), (12, 26, 1), (10, 25, 1), (11, 25, 1), (12, 25, 0), (10, 24, 2), (11, 24, 1), (12, 24, 0)], rgb("#6c8cd0"), rgb("#4a66a8"), rgb("#2c4070"))
    good([(10, 23, 2), (11, 23, 2), (12, 23, 1)], rgb("#e8e0d0"), rgb("#bcb4a2"), rgb("#847c6c"))
    return w


def window_dormer():
    """A small four-pane casement in dark timber (16x16)."""
    w = Win(16, "dormer")
    w.bevel(0, 0, 16, 16, DARK_TIMBER, 2)
    for (xa, xb, ya, yb) in ((2, 7, 2, 7), (9, 14, 2, 7), (2, 7, 9, 14), (9, 14, 9, 14)):
        w.pane(xa, ya, xb, yb, tintc=(70, 88, 84), tintk=0.35, rows=(2, 14))
    for y in range(2, 14):
        for x in (7, 8):
            w.c.set(x, y, step(DARK_TIMBER, 3 if x == 7 else 1))
            w.glass.pop((x, y), None)
    for x in range(2, 14):
        for y in (7, 8):
            w.c.set(x, y, step(DARK_TIMBER, 3 if y == 7 else 1))
            w.glass.pop((x, y), None)
    # a scrap of cream curtain, left
    w.curtain([(x, y) for y in range(2, 7) for x in range(2, 4 if y > 4 else 5)], rgb("#e4dcc6"), rgb("#bdb49c"), rgb("#8c846e"), fold=3)
    return w


def window_round():
    """A round window (radius 6) in a pale stone square, a glazing cross, voussoir joints round the ring (16x16)."""
    w = Win(16, "round")
    S = PALE_STONE
    for y in range(16):
        for x in range(16):
            k = 3
            if w.rng.random() < 0.16:
                k += w.rng.choice([-1, 1])
            if x in (0, 15) or y in (0, 15):
                k = 2 if (x + y) % 2 else 3
            w.c.set(x, y, step(S, k))
    cx = cy = 8.0
    for y in range(16):
        for x in range(16):
            r = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if r <= 6.1:
                w.pane(x, y, x + 1, y + 1, tintc=(62, 84, 98), tintk=0.3, rows=(2, 14), reflect=False)
                if (x + y) % 13 == 4 or (x < 8 and y < 8 and r > 4.2):
                    w.c.set(x, y, lerp(w.c.get(x, y), SKY, 0.26))
            elif r <= 7.1:
                ang = math.atan2(y + 0.5 - cy, x + 0.5 - cx)
                k = 1 if ang > -0.4 and ang < 2.7 else 4   # the moulded ring: shaded lower right, lit upper left
                w.c.set(x, y, step(S, k))
    # voussoir joints at the compass points and the diagonals
    for (x, y) in ((8, 0), (7, 0), (8, 15), (7, 15), (0, 7), (0, 8), (15, 7), (15, 8), (2, 2), (13, 2), (2, 13), (13, 13)):
        w.c.set(x, y, step(S, 1))
    for y in range(2, 14):
        for x in (7, 8):
            if (x, y) in w.glass:
                w.c.set(x, y, step(WHITE_PAINT, 3 if x == 7 else 1))
                w.glass.pop((x, y), None)
    for x in range(2, 14):
        for y in (7, 8):
            if (x, y) in w.glass or w.c.get(x, y)[3]:
                if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= 6.1:
                    w.c.set(x, y, step(WHITE_PAINT, 3 if y == 7 else 1))
                    w.glass.pop((x, y), None)
    return w


WINDOWS_TALL = ["leaded", "sash", "arched", "cottage", "warehouse", "barred", "works", "shop"]


def windows():
    """name -> (base, glow) for every window texture: `window_<style>_lower/_upper` and the two singles."""
    out = {}
    for s in WINDOWS_TALL:
        w = globals()["window_" + s]()
        lo, up = w.split()
        glo, gup = w.split_glow()
        out[f"window_{s}_lower"], out[f"window_{s}_upper"] = lo, up
        out[f"window_{s}_lower_glow"], out[f"window_{s}_upper_glow"] = glo, gup
    for s in ("dormer", "round"):
        w = globals()["window_" + s]()
        out[f"window_{s}_single"] = w.c
        out[f"window_{s}_single_glow"] = w.glow()
    return out


# ---------------------------------------------------------------------------------------------------------------------
# the Quay's loading door: two leaves, each 16x32 (a lower and an upper quarter), drawn from the street

DOOR_WOOD = R("#1d1308", "#2f200f", "#46301a", "#5d4224", "#775633", "#93703f")


def _door_leaf(rng, hinge_side_left=True):
    """One leaf, hinge on the LEFT of the image (mirrored by the caller for the right leaf)."""
    P = DOOR_WOOD
    c = Canvas(16, 32)
    widths = [4, 4, 4, 3]
    x = 0
    for bi, bw in enumerate(widths):
        base = rng.choice([2, 3, 3, 4])
        for xx in range(x, x + bw):
            for y in range(32):
                k = base
                if xx == x:
                    k += 1
                if xx == x + bw - 1:
                    k = 0 if y % 9 else 1
                elif rng.random() < 0.16:
                    k += rng.choice([-1, 1])
                c.set(xx, y, step(P, k))
        for _ in range(3):   # grain streaks
            gx, gy = x + rng.randrange(1, max(2, bw - 1)), rng.randrange(0, 28)
            for yy in range(gy, gy + rng.randrange(3, 7)):
                c.set(gx, yy % 32, step(P, base - 1))
        if rng.random() < 0.7:  # a knot
            kx, ky = x + rng.randrange(1, max(2, bw - 1)), rng.randrange(3, 29)
            c.set(kx, ky, step(P, 0))
            c.set(kx, ky + 1, step(P, 1))
        x += bw
    for y in range(32):   # the dark gap between the leaves
        c.set(15, y, (14, 8, 4, 255))
    # a ledger board across the middle: lit top edge, shadow below, nail heads at the ends
    for xx in range(0, 15):
        c.set(xx, 14, step(P, 5))
        c.set(xx, 15, step(P, 4))
        c.set(xx, 16, step(P, 3))
        c.set(xx, 17, step(P, 1))
    for nx in (2, 6, 10, 13):
        c.set(nx, 15, step(IRON, 4))
    # a diagonal brace in each half, hinge side to latch side, three px thick with a shadow on its lower right
    for y in range(19, 31):
        x0 = 1 + (30 - y)
        for dx, k in ((0, 5), (1, 4), (2, 3), (3, 0)):
            if 0 <= x0 + dx < 15:
                c.set(x0 + dx, y, step(P, k))
    for y in range(2, 13):
        x0 = 1 + (12 - y)
        for dx, k in ((0, 5), (1, 4), (2, 3), (3, 0)):
            if 0 <= x0 + dx < 15:
                c.set(x0 + dx, y, step(P, k))
    c.set(3, 29, step(IRON, 4))
    c.set(11, 2, step(IRON, 4))
    # black iron strap hinges on the outer edge
    for hy in (2, 15, 29):
        for dx in range(8):
            c.set(dx, hy, step(IRON, 3 if dx not in (2, 5) else 4))
        for dx in range(7):
            c.set(dx, hy + 1, step(IRON, 1))
        c.set(0, hy, step(IRON, 4))
        c.set(0, hy + 1, step(IRON, 3))
    # the ring handle by the centre, at the lower quarter's top, hung from a little plate
    for (rx, ry) in ((12, 18), (13, 18)):
        c.set(rx, ry, step(IRON, 3))
    ring = [(12, 19), (13, 19), (11, 20), (14, 20), (11, 21), (14, 21), (12, 22), (13, 22)]
    for (rx, ry) in ring:
        c.set(rx, ry, step(IRON, 4 if (rx, ry) in ((12, 19), (11, 20)) else 2))
    for (rx, ry) in ((12, 20), (13, 20), (12, 21), (13, 21)):   # the dark hollow of the ring on the planks
        c.set(rx, ry, mul(c.get(rx, ry), 0.55))
    # weather: the foot of the leaf damp and dark
    for xx in range(0, 15):
        if rng.random() < 0.55:
            c.set(xx, 31, step(P, 0))
        if rng.random() < 0.3:
            c.set(xx, 30, mul(c.get(xx, 30), 0.8))
    for yy in range(32):
        for xx in range(16):
            p = c.get(xx, yy)
            if p[3] and p[:3] not in [i[:3] for i in IRON]:
                c.set(xx, yy, (clamp8(p[0] * 0.86), clamp8(p[1] * 0.86), clamp8(p[2] * 0.88), 255))
    return c


def loading_door():
    """name -> Canvas for the four quarters."""
    left = _door_leaf(rng_for("city_kit", "loading_door", "left"))
    right_src = _door_leaf(rng_for("city_kit", "loading_door", "right"))
    right = Canvas(16, 32)
    for y in range(32):
        for x in range(16):
            right.set(15 - x, y, right_src.get(x, y))
    # the right leaf's inner (left) edge is the gap's other side: a lighter lip so the meeting reads
    for y in range(32):
        right.set(0, y, step(DOOR_WOOD, 1) if y % 3 else step(DOOR_WOOD, 0))
    out = {}
    for side, leaf in (("left", left), ("right", right)):
        lo, up = Canvas(), Canvas()
        for y in range(16):
            for x in range(16):
                up.set(x, y, leaf.get(x, y))
                lo.set(x, y, leaf.get(x, y + 16))
        out[f"loading_door_lower_{side}"] = lo
        out[f"loading_door_upper_{side}"] = up
    return out


# ---------------------------------------------------------------------------------------------------------------------
# rope and pulley

HEMP = R("#4d3a22", "#6e5533", "#8f7348", "#ad8f5a", "#c9ac74", "#dcc592")


def rope():
    """Twisted hemp: diagonal strands, 2px wide, light and dark tan alternating; shaded left to right like a round cord.
    The model samples the 2px column x 7..8, but the whole tile reads as rope and tiles vertically."""
    rng = rng_for("city_kit", "rope")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            s = (x + y) % 4         # strand phase: two px of strand, then the other strand
            k = (3, 3, 2, 2)[s] if s < 2 else (2, 1)[s - 2]
            k = [4, 3, 1, 2][s]
            col = x % 2  # cord shading, light on the left of each pair
            if rng.random() < 0.12:
                k += rng.choice([-1, 1])
            if x in (7, 9, 5, 11):
                k += 1 if s in (0, 1) else 0
            c.set(x, y, step(HEMP, k + (1 if x in (7,) else -1 if x in (8,) else 0)))
    return c


def hoist_pulley():
    """An iron pulley wheel face (5x7 oval, on east/west) and, top-left, a swatch of the wheel's rim iron."""
    rng = rng_for("city_kit", "hoist_pulley")
    c = Canvas()
    for y in range(4):
        for x in range(4):
            c.set(x, y, step(IRON, 2 + (1 if rng.random() < 0.3 else 0) - (1 if rng.random() < 0.2 else 0)))
    ox, oy = 5, 4
    rows = [".rrr.", "rsdsr", "rd.dr", "s.h.s", "rd.dr", "rsdsr", ".rrr."]
    # r = rim, s = spoke, d = dark inside, h = hub, . = (inside) or empty at the corners
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            x, y = ox + i, oy + j
            if ch == "r":
                c.set(x, y, step(IRON, 4 if (i + j) <= 2 else 3 if (i + j) < 7 else 2))
            elif ch == "s":
                c.set(x, y, step(IRON, 3))
            elif ch == "d":
                c.set(x, y, step(IRON, 0))
            elif ch == "h":
                c.set(x, y, (118, 96, 52, 255))      # a brass hub
            elif ch == ".":
                if j in (0, 6):
                    continue
                c.set(x, y, step(IRON, 0))
    c.set(ox + 2, oy + 3, (150, 124, 66, 255))
    return c


# ---------------------------------------------------------------------------------------------------------------------
# chimney crown

BRICK_FACE = R("#6c3a2d", "#7c4536", "#8f503f", "#9b5643", "#b1624d", "#c66851")
BRICK_MORTAR = R("#7e625c", "#8b6e67", "#a2867d", "#a9948d")
MUD_FACE = R("#6f5441", "#7e5d48", "#8c674f", "#957150", "#9d785c", "#ab8661")
MUD_JOINT = R("#4a3830", "#5e4841", "#695146")
STONE_FACE = R("#5a595a", "#6a6d6a", "#787678", "#7f7f7f", "#8b898b", "#9c999c")
SOOT = R("#0c0b0b", "#171514", "#262322", "#3a3533", "#524a46")


def _chimney_ground(kind, rng):
    """The material's face, drawn like vanilla's: bricks laid in stretcher bond, a lit top row, a shaded bottom row."""
    c = Canvas()
    if kind == "brick":
        for y in range(16):
            cy = y % 4
            course = y // 4
            joint_x = (3 if course % 2 == 0 else 7)
            for x in range(16):
                if cy == 3:
                    k = rng.choice([1, 2, 2, 3]) if (x % 8) not in (3, 7) else 3
                    c.set(x, y, step(BRICK_MORTAR, k))
                    continue
                if x % 8 == joint_x % 8:
                    c.set(x, y, step(BRICK_MORTAR, 2 if cy < 2 else 1))
                    continue
                brick = (x - (joint_x + 1)) // 8 + course * 2
                tone = (hash((brick, course)) % 3) - 1
                k = [5, 3, 1][cy] + tone if cy < 3 else 3
                if rng.random() < 0.18:
                    k += rng.choice([-1, 1])
                c.set(x, y, step(BRICK_FACE, k))
    elif kind == "mud":
        for y in range(16):
            cy = y % 8
            course = y // 8
            for x in range(16):
                jx = (x + (4 if course else 0)) % 8
                if cy == 7:
                    c.set(x, y, step(MUD_JOINT, rng.choice([0, 1, 1, 2])))
                elif jx == 0:
                    c.set(x, y, step(MUD_JOINT, 1 if cy else 2))
                else:
                    k = 4 - cy // 3
                    if cy == 0:
                        k = 5
                    if rng.random() < 0.28:
                        k += rng.choice([-1, 1])
                    c.set(x, y, step(MUD_FACE, k))
    else:
        for y in range(16):
            cy = y % 8
            course = y // 8
            for x in range(16):
                jx = (x + (8 if course else 0)) % 16
                if cy == 7:
                    c.set(x, y, step(STONE_FACE, 1 if x % 5 else 0))
                elif jx == 15 or (course == 1 and jx == 7 and False):
                    c.set(x, y, step(STONE_FACE, 0))
                else:
                    k = 4 - cy // 3
                    if cy == 0:
                        k = 5
                    if cy == 6:
                        k = 1
                    if rng.random() < 0.26:
                        k += rng.choice([-1, 1])
                    c.set(x, y, step(STONE_FACE, k))
    return c


def chimney_flue(kind):
    """Top face: the material's bricks round a 6x6 black soot flue, the brick near it stained."""
    rng = rng_for("city_kit", "chimney_flue", kind)
    c = _chimney_ground(kind, rng)
    cx = cy = 8.0
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d > 7.2:
                continue
            p = c.get(x, y)
            if 3.9 <= d <= 7.2:   # soot staining spreading out from the flue: strongest at the rim, fading
                t = 0.62 * (1 - (d - 3.9) / 3.3) ** 1.3
                if rng.random() < 0.8:
                    c.set(x, y, lerp(p, (28, 24, 22), t))
    for y in range(5, 11):
        for x in range(5, 11):
            corner = (x in (5, 10)) and (y in (5, 10))
            if corner:
                c.set(x, y, step(SOOT, 3))
            elif x in (5, 10) or y in (5, 10):
                c.set(x, y, step(SOOT, 2 if (x < 8 and y < 8) else 1))
            else:
                c.set(x, y, step(SOOT, 0 if rng.random() > 0.2 else 1))
    c.set(6, 6, step(SOOT, 3))
    c.set(7, 6, step(SOOT, 2))
    return c


def chimney_band(kind):
    """The oversailing band's side: the material a shade darker, one brick course, soot rising up its top 3px."""
    rng = rng_for("city_kit", "chimney_band", kind)
    c = _chimney_ground(kind, rng)
    for y in range(16):
        for x in range(16):
            p = c.get(x, y)
            col = mul(p, 0.82)
            if y < 3:                              # soot: heaviest at the very top, streaking down in wisps
                heavy = (0.62, 0.42, 0.24)[y]
                if (x * 5 + y * 3) % 7 in (0, 1, 4):
                    heavy += 0.18
                col = lerp(col, (22, 19, 18), min(0.85, heavy))
            c.set(x, y, col)
    return c


# ---------------------------------------------------------------------------------------------------------------------
# walls: plaster, timber, city bricks, ashlar, and the extra roof-tile variants (SPEC §16.0 decision 27: nothing tiles).
# Every material has one BASE pattern, identical in all its variants (same seed), and the variants only add features in
# the interior, so any two variants meet without a visible seam. Colour matching uses the base's scale factor.


def tnoise(rng, w, h, px, py):
    """Tileable value noise in [0,1]: a px x py grid of random values, smoothly interpolated, wrapping."""
    grid = [[rng.random() for _ in range(px)] for _ in range(py)]
    out = []
    for y in range(h):
        gy = y * py / h
        y0, fy = int(gy) % py, gy - int(gy)
        y1 = (y0 + 1) % py
        fy = fy * fy * (3 - 2 * fy)
        row = []
        for x in range(w):
            gx = x * px / w
            x0, fx = int(gx) % px, gx - int(gx)
            x1 = (x0 + 1) % px
            fx = fx * fx * (3 - 2 * fx)
            a = grid[y0][x0] * (1 - fx) + grid[y0][x1] * fx
            bb = grid[y1][x0] * (1 - fx) + grid[y1][x1] * fx
            row.append(a * (1 - fy) + bb * fy)
        out.append(row)
    return out


def rank_levels(field, n):
    """Quantise a 2D field into n levels of equal area (so every tone is used, whatever the noise did)."""
    flat = sorted(v for row in field for v in row)
    cuts = [flat[int(len(flat) * (i + 1) / n) - 1] for i in range(n - 1)]
    return [[sum(1 for c in cuts if v > c) for v in row] for row in field]


def blur_noise(rng, sx, sy):
    """Tileable blotch noise: white noise blurred (wrapping) by a Gaussian sx wide and sy tall, standardised to mean 0.5, sd 0.16.
    Unlike value noise it has no grid, so its contours never line up into a lattice."""
    white = [[rng.random() - 0.5 for _ in range(16)] for _ in range(16)]

    def kernel(sig):
        r = max(1, int(sig * 3))
        ws = [math.exp(-(d * d) / (2 * sig * sig)) for d in range(-r, r + 1)]
        t = sum(ws)
        return r, [w / t for w in ws]
    rx, kx = kernel(sx)
    ry, ky = kernel(sy)
    tmp = [[sum(kx[i + rx] * white[y][(x + i) % 16] for i in range(-rx, rx + 1)) for x in range(16)] for y in range(16)]
    out = [[sum(ky[j + ry] * tmp[(y + j) % 16][x] for j in range(-ry, ry + 1)) for x in range(16)] for y in range(16)]
    flat = [v for row in out for v in row]
    mean = sum(flat) / len(flat)
    sd = math.sqrt(sum((v - mean) ** 2 for v in flat) / len(flat)) or 1.0
    return [[0.5 + 0.16 * (v - mean) / sd for v in row] for row in out]


def noise_field(rng, specs):
    """Weighted sum of blur noises, specs = [(weight, sigma_x, sigma_y)]."""
    parts = [(w, blur_noise(rng, sx, sy)) for (w, sx, sy) in specs]
    tw = sum(w for w, _ in parts)
    return [[0.5 + sum(w * (p[y][x] - 0.5) for w, p in parts) / tw * 1.25 for x in range(16)] for y in range(16)]


def blend_edges(base, own, ring=3.0):
    """A variant's own noise inside, the shared base noise on its edge, faded over `ring` px: so two different variants meet
    seamlessly (same values on the shared edge), yet their interiors differ. The mix is re-normalised to the noise's spread."""
    out = []
    for y in range(16):
        row = []
        for x in range(16):
            d = min(x, 15 - x, y, 15 - y)
            w = max(0.0, 1.0 - d / ring)
            v = w * base[y][x] + (1 - w) * own[y][x]
            norm = math.sqrt(w * w + (1 - w) * (1 - w))
            row.append(0.5 + (v - 0.5) / norm)
        out.append(row)
    return out


def cuts_of(field, n):
    flat = sorted(v for row in field for v in row)
    return [flat[int(len(flat) * (i + 1) / n) - 1] for i in range(n - 1)]


def levels_by(cuts, field):
    return [[sum(1 for c in cuts if v > c) for v in row] for row in field]


def scale_by(c, k):
    for y in range(c.h):
        for x in range(c.w):
            p = c.px[y][x]
            if p[3] > 0:
                c.px[y][x] = (clamp8(p[0] * k[0]), clamp8(p[1] * k[1]), clamp8(p[2] * k[2]), p[3])
    return c


def fit_factor(c, target):
    a = average(c)
    return tuple(target[i] / a[i] for i in range(3))


def walk(c, x, y, n, col, rng, bias=(0, 1), wobble=0.35):
    """A hairline crack: n steps from (x, y), mostly following `bias`, now and then stepping sideways."""
    pts = []
    for i in range(n):
        if 0 <= x < c.w and 0 <= y < c.h:
            c.set(x, y, col)
            pts.append((x, y))
        if rng.random() < wobble:
            x += rng.choice([-1, 1])
        else:
            x += bias[0]
        y += bias[1]
    return pts


# ----- plaster ---------------------------------------------------------------------------------------------------------

PLASTER_COLOURS = {"white": (226, 224, 214), "cream": (222, 206, 170), "ochre": (204, 158, 84), "rose": (208, 160, 150),
                   "clay": (184, 118, 90), "mauve": (166, 150, 160), "oxblood": (128, 58, 48), "sage": (158, 170, 138)}
PLASTER_ORDER = ["white", "cream", "ochre", "rose", "clay", "mauve", "oxblood", "sage"]
PLASTER_TONES = (0.955, 0.98, 1.0, 1.025)


def _plaster_specs():
    return [(0.6, 2.6, 1.7), (0.4, 1.0, 0.9)]      # broad trowel strokes (wider than tall), a finer scale


def _plaster_levels(colour, variant):
    rng_b = rng_for("city_kit", "plaster", colour, "base")
    base = noise_field(rng_b, _plaster_specs())
    cuts = cuts_of(base, 4)
    if variant == 0:
        own = noise_field(rng_for("city_kit", "plaster", colour, "own", 0), _plaster_specs())
    else:
        own = noise_field(rng_for("city_kit", "plaster", colour, "own", variant), _plaster_specs())
    field = blend_edges(base, own, ring=2.0)
    rj = rng_for("city_kit", "plaster", colour, "jitter", variant)
    field = [[v + (rj.random() - 0.5) * 0.06 for v in row] for row in field]
    return levels_by(cuts, field)


def _plaster_base(colour, variant=0):
    """Low-contrast lime-wash mottling in four tones, never flat, never noisy."""
    M = PLASTER_COLOURS[colour]
    lv = _plaster_levels(colour, variant)
    lum = 0.3 * M[0] + 0.59 * M[1] + 0.11 * M[2]
    spread = 1.6 if lum < 120 else 1.25 if lum < 160 else 1.0
    c = Canvas()
    for y in range(16):
        for x in range(16):
            k = lv[y][x]
            col = mul(M, 1 + (PLASTER_TONES[k] - 1) * spread)
            if k == 0:
                col = tint(col, 2, 0, -2)       # the shadowed tone a touch warmer
            elif k == 3:
                col = tint(col, 0, 1, 2)        # the lit tone a touch cooler
            c.set(x, y, col)
    return c


def plaster_raw(colour, variant):
    M = PLASTER_COLOURS[colour]
    c = _plaster_base(colour, variant)
    rng = rng_for("city_kit", "plaster", colour, variant)
    dark = mul(M, 0.82)
    for _ in range(4 + (2 if variant == 0 else 0)):   # a few darker flecks, never on the edge
        x, y = rng.randrange(1, 15), rng.randrange(1, 15)
        c.set(x, y, mul(M, rng.choice([0.84, 0.88, 0.92])))
    if variant == 1:     # a hairline crack and a faint stain beside it
        sx, sy = rng.randrange(5, 10), rng.randrange(2, 4)
        pts = walk(c, sx, sy, 10, mul(M, 0.76), rng, bias=(0, 1), wobble=0.38)
        for (x, y) in pts[2::4]:
            c.set(x + 1, y, mul(M, 0.86))
        ex, ey = pts[-1]
        for dy in range(-2, 3):
            for dx in range(-3, 3):
                x, y = ex + 2 + dx, ey - 1 + dy
                if 1 <= x < 15 and 1 <= y < 15 and (dx * dx / 9 + dy * dy / 4) <= 1.0 and (x, y) not in pts:
                    c.set(x, y, lerp(c.get(x, y), tint(mul(M, 0.86), 6, 2, -4), 0.42))
    elif variant == 2:   # the wash worn thin: a ~3x4 patch of grey render showing through
        lum = 0.3 * M[0] + 0.59 * M[1] + 0.11 * M[2]
        G = lerp(M, (128, 124, 118), 0.5) if lum > 150 else lerp(M, (190, 184, 172), 0.45)
        px0, py0 = rng.randrange(5, 9), rng.randrange(5, 8)
        for j, (xa, xb) in enumerate(((1, 3), (0, 3), (0, 3), (1, 2))):
            for i in range(xa, xb + 1 if xb < 3 else 4):
                if xa <= i <= xb:
                    k = rng.choice([0.94, 1.0, 1.0, 1.05])
                    c.set(px0 + i, py0 + j, mul(G, k))
        for (i, j) in ((0, 1), (0, 2), (1, 3)):          # the edge of the wash, lifted: a shade lighter
            c.set(px0 + i - 1, py0 + j, mul(M, 1.04))
        c.set(px0 + 3, py0 + 2, mul(G, 0.84))
    elif variant == 3:   # a weather streak running down from the top edge
        x0 = rng.randrange(3, 12)
        for y in range(0, 11):
            s = 0.55 * (1 - y / 11.0) ** 1.1
            c.set(x0, y, lerp(c.get(x0, y), mul(M, 0.66), s))
            if y < 8:
                c.set(x0 + 1, y, lerp(c.get(x0 + 1, y), mul(M, 0.72), s * 0.5))
        c.set(x0, 10, lerp(c.get(x0, 10), mul(M, 0.7), 0.16))
    return c


def plaster_k(colour):
    return fit_factor(plaster_raw(colour, 0), PLASTER_COLOURS[colour])


def plaster(colour, variant):
    return scale_by(plaster_raw(colour, variant), plaster_k(colour))


# ----- timber ----------------------------------------------------------------------------------------------------------

TIMBER_TARGET = {"dark": (66, 52, 38), "pale": (128, 100, 66)}


def _timber_ramp(tone):
    """Tight tones round the target, as vanilla's stripped logs are (dark oak spans only ~59..82): a little greyer when lit."""
    fs = (0.80, 0.90, 1.0, 1.09, 1.19) if tone == "dark" else (0.86, 0.93, 1.0, 1.06, 1.12)
    return [tint(mul(TIMBER_TARGET[tone] + (255,), f), int(2 * (f - 1) * 10), int((f - 1) * 8), int(4 * (f - 1) * 10)) for f in fs]


TIMBER_RAMPS = {"dark": _timber_ramp("dark"), "pale": _timber_ramp("pale")}
PEG = {"dark": (112, 90, 60), "pale": (176, 142, 92)}


def _timber_levels(tone, variant):
    specs = [(0.5, 0.7, 7.0), (0.3, 0.45, 3.0), (0.2, 0.6, 1.4)]     # long vertical streaks, some short ones
    base = noise_field(rng_for("city_kit", "timber", tone, "side-base"), specs)
    cuts = cuts_of(base, 5)
    own = noise_field(rng_for("city_kit", "timber", tone, "side-own", variant), specs)
    return levels_by(cuts, blend_edges(base, own, ring=2.0))


def _timber_side_base(tone, variant=0):
    rng = rng_for("city_kit", "timber", tone, "side-jit", variant)
    rp = TIMBER_RAMPS[tone]
    lv = _timber_levels(tone, variant)
    c = Canvas()
    for y in range(16):
        for x in range(16):
            k = lv[y][x]
            if rng.random() < 0.05:
                k += rng.choice([-1, 1])
            c.set(x, y, step(rp, k))
    for _ in range(2):   # adze facets: a short lit dash
        x, y = rng.randrange(2, 11), rng.randrange(2, 13)
        for dx in range(rng.randrange(2, 4)):
            c.set(x + dx, y, step(rp, 3))
    return c


def timber_raw_side(tone, variant):
    rp = TIMBER_RAMPS[tone]
    c = _timber_side_base(tone, variant)
    rng = rng_for("city_kit", "timber", tone, "side", variant)
    if variant == 1:     # a knot: a dark ring round a paler heart, the grain bending round it
        cx, cy = rng.randrange(5, 10), rng.randrange(5, 10)
        for dy in range(-2, 3):
            for dx in range(-1, 2):
                d = dx * dx * 1.8 + dy * dy * 0.7
                if d <= 1.9:
                    c.set(cx + dx, cy + dy, step(rp, 3 if (dx + dy) < 0 else 2))
                elif d <= 3.6:
                    c.set(cx + dx, cy + dy, mul(step(rp, 0), 0.62))
        c.set(cx, cy, mul(step(rp, 1), 0.7))
        c.set(cx - 1, cy - 1, step(rp, 4))
    elif variant == 2:   # a split along the grain: a dark seam, a lit lip beside it, widest in the middle
        sx, sy = rng.randrange(4, 11), rng.randrange(2, 5)
        for i in range(9):
            y = sy + i
            c.set(sx, y, mul(step(rp, 0), 0.55))
            if 2 <= i <= 5:
                c.set(sx + 1, y, mul(step(rp, 0), 0.7))
            c.set(sx - 1, y, step(rp, 4))
    elif variant == 3:   # a pair of oak pegs: two small lighter squares, one above the other
        px = rng.randrange(5, 9)
        for py in (3, 10):
            P = PEG[tone]
            c.set(px, py, lerp(P, (255, 255, 255), 0.18))
            c.set(px + 1, py, P)
            c.set(px, py + 1, mul(P, 0.88))
            c.set(px + 1, py + 1, mul(P, 0.72))
            c.set(px - 1, py, step(rp, 0)) if False else None
            for (dx, dy) in ((-1, 0), (-1, 1), (0, 2), (1, 2)):
                c.set(px + dx, py + dy, mul(c.get(px + dx, py + dy), 0.8))
    return c


def timber_end_raw(tone, variant):
    """End grain, squared by an adze: concentric square rings round the pith, a dark cut edge, a few checks."""
    rp = TIMBER_RAMPS[tone]
    rng = rng_for("city_kit", "timber", tone, "end", variant)
    ox, oy = (0.0, 0.0) if variant == 0 else (1.5, -1.0)
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5 - ox, y - 7.5 - oy
            d = max(abs(dx), abs(dy)) + (rng.random() - 0.5) * 0.7
            ring = (0, 3, 1, 3, 1, 4, 2)[min(6, int(d / 1.15))] if d < 8 else 2
            k = ring
            edge = max(abs(x - 7.5), abs(y - 7.5))
            if edge > 7.0:
                k = 0
            elif edge > 6.0:
                k = 1 if (x < 8 and y < 8) else 0
            elif rng.random() < 0.1:
                k += rng.choice([-1, 1])
            c.set(x, y, step(rp, k))
    if variant == 1:     # a drying check running out from the pith
        sx, sy = 8 + int(ox), 8 + int(oy)
        for i in range(6):
            c.set(sx + i, sy + (i // 3), step(rp, 0))
        c.set(sx + 3, sy - 1, step(rp, 1))
    else:
        c.set(8, 8, step(rp, 0))
        c.set(7, 7, step(rp, 0))
    return c


def timber_k(tone):
    return fit_factor(_timber_side_base(tone, 0), TIMBER_TARGET[tone])


def timber_side(tone, variant):
    return scale_by(timber_raw_side(tone, variant), timber_k(tone))


def timber_end(tone, variant):
    return scale_by(timber_end_raw(tone, variant), timber_k(tone))


# ----- city bricks -----------------------------------------------------------------------------------------------------

BRICK_C = R("#4e2822", "#693328", "#7f3f31", "#954c3a", "#a95a43", "#bc684e", "#cf7a5c")
BRICK_MORT = R("#8a766b", "#a08d80", "#b5a496")
BURNT = R("#4a2a2c", "#5e3836", "#734640", "#855549")
PALE_BRICK = R("#86694a", "#9c7d5a", "#b09068", "#c3a47c")
REPAIR = R("#6c453a", "#85594a", "#9d6d5a", "#b3806a")
REPAIR_MORT = R("#a89b8e", "#bdb0a2", "#cfc3b4")


def _brick_cells():
    """pixel -> ('m'|'v'|'b', course, brick, row) for vanilla's layout: 4px courses (3 brick rows + a mortar row), 8px bricks."""
    cells = {}
    for y in range(16):
        course, r = y // 4, y % 4
        jx = 3 if course % 2 == 0 else 7
        for x in range(16):
            if r == 3:
                cells[(x, y)] = ("m", course, 0, r)
            elif x % 8 == jx:
                cells[(x, y)] = ("v", course, 0, r)
            else:
                cells[(x, y)] = ("b", course, ((x - jx - 1) % 16) // 8, r)
    return cells


def _bricks_base():
    rng = rng_for("city_kit", "city_bricks", "base")
    cells = _brick_cells()
    tone = {(c, b): rng.choice([1, 2, 2, 3, 3, 3, 4, 4, 5]) for c in range(4) for b in range(2)}
    c = Canvas()
    for (x, y), (kind, course, br, r) in cells.items():
        if kind == "b":
            k = tone[(course, br)] + (1, 0, -1)[r]
            if rng.random() < 0.2:
                k += rng.choice([-1, 1])
            c.set(x, y, step(BRICK_C, k))
        else:
            c.set(x, y, step(BRICK_MORT, rng.choice([0, 1, 1, 2]) if kind == "m" else rng.choice([1, 1, 2])))
    return c, cells


def bricks_raw(variant):
    c, cells = _bricks_base()
    rng = rng_for("city_kit", "city_bricks", variant)

    def paint(course, br, ramp, jitter=0.25):
        for (x, y), (kind, cc, bb, r) in cells.items():
            if kind == "b" and cc == course and bb == br:
                k = (2, 1, 0)[r] + (1 if rng.random() < jitter else 0)
                c.set(x, y, step(ramp, k + 1 if r == 0 else k))

    def pick(n):
        spots = [(cc, bb) for cc in range(4) for bb in range(2)]
        rng.shuffle(spots)
        return spots[:n]
    if variant == 1:
        for (cc, bb) in pick(2):
            paint(cc, bb, BURNT)
    elif variant == 2:
        for (cc, bb) in pick(2):
            paint(cc, bb, PALE_BRICK)
    elif variant == 3:
        x0 = rng.randrange(3, 10)
        for y in range(0, 12):
            f = 0.62 * (1 - y / 12.0) ** 1.1
            for x in (x0, x0 + 1, x0 + 2):
                w = f * (1.0 if x == x0 + 1 else 0.55)
                c.set(x, y, lerp(c.get(x, y), (24, 20, 20), w))
    elif variant == 4:
        course = rng.randrange(0, 3)
        for (cc, bb) in ((course, 0), (course, 1), (course + 1, rng.randrange(0, 2))):
            paint(cc, bb, REPAIR, 0.2)
        for (x, y), (kind, cc, bb, r) in cells.items():
            if kind != "b" and cc in (course, course + 1):
                if (cc == course) or (x // 8 == 0):
                    c.set(x, y, step(REPAIR_MORT, rng.choice([0, 1, 1, 2])))
    return c


def bricks_k():
    return fit_factor(bricks_raw(0), (150, 92, 76))


def city_bricks(variant):
    return scale_by(bricks_raw(variant), bricks_k())


# ----- ashlar ----------------------------------------------------------------------------------------------------------

ASH = R("#5a595a", "#6a6d6a", "#787678", "#7f7f7f", "#8b898b", "#9c999c")  # vanilla stone_bricks' own tones
MOSS = R("#2f4a28", "#436636", "#5a8044", "#76a058")
LICHEN = R("#7d8a62", "#98a578", "#b4bf94")


def _ashlar_blocks():
    """(x0, x1_exclusive, y0) of each block incl. its joint column: course A one 16px block, course B two 8px blocks."""
    return [(0, 16, 0), (0, 8, 8), (8, 16, 8)]


def _ashlar_base():
    rng = rng_for("city_kit", "ashlar", "base")
    c = Canvas()
    for (xa, xb, ya) in _ashlar_blocks():
        for y in range(ya, ya + 8):
            r = y - ya
            for x in range(xa, xb):
                if x == xb - 1:
                    k = 0
                elif r == 7:
                    k = 0 if (x * 3 + y) % 5 else 1
                elif r == 6:
                    k = 1
                elif r == 0:
                    k = 5 if rng.random() > 0.25 else 4
                elif x == xa and r < 6:
                    k = 5 if rng.random() > 0.3 else 4
                elif r == 5:
                    k = 2 if rng.random() > 0.3 else 3
                else:
                    k = 3 + (1 if rng.random() < 0.28 else 0)
                    if rng.random() < 0.05:
                        k = 2
                c.set(x, y, step(ASH, k))
    return c


def ashlar_raw(variant):
    c = _ashlar_base()
    rng = rng_for("city_kit", "ashlar", variant)
    blocks = _ashlar_blocks()
    if variant == 1:     # one block a shade warmer
        xa, xb, ya = blocks[rng.choice([1, 2])]
        for y in range(ya, ya + 8):
            for x in range(xa, xb - 1):
                c.set(x, y, tint(c.get(x, y), 7, 3, -6))
    elif variant == 2:   # a crack, with a lit lip on one side
        xa, xb, ya = blocks[0]
        sx = rng.randrange(3, 10)
        pts = walk(c, sx, ya + 1, 6, step(ASH, 0), rng, bias=(1, 1), wobble=0.3)
        for (x, y) in pts[::2]:
            c.set(x - 1, y, step(ASH, 5))
    elif variant == 3:   # moss creeping along the bottom edge of the blocks, with a drip or two
        for (xa, xb, ya) in blocks[1:]:
            for x in range(xa, xb - 1):
                if rng.random() < 0.65:
                    c.set(x, ya + 6, step(MOSS, rng.choice([1, 2, 2])))
                if rng.random() < 0.38:
                    c.set(x, ya + 5, step(MOSS, rng.choice([0, 1, 2])))
                if rng.random() < 0.12:
                    c.set(x, ya + 4, step(MOSS, 1))
        for x in range(0, 15):
            if rng.random() < 0.3:
                c.set(x, 6, step(MOSS, 1))
    elif variant == 4:   # lichen spots
        for _ in range(3):
            cx, cy = rng.randrange(2, 13), rng.randrange(2, 13)
            if cy % 8 >= 6:
                cy -= 2
            for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (1, 1), (0, -1)):
                if rng.random() < 0.65 and (cx + dx) % 8 != 7:
                    c.set(cx + dx, cy + dy, step(LICHEN, rng.choice([0, 1, 1, 2])))
    elif variant == 5:   # a chipped arris: the corner of a block knocked off, the fresh break paler
        xa, xb, ya = blocks[rng.choice([1, 2])]
        for (dx, dy) in ((0, 0), (1, 0), (2, 0), (0, 1), (1, 1), (0, 2)):
            c.set(xa + dx, ya + dy, step(ASH, 0) if dx + dy < 2 else step(ASH, 1))
        for (dx, dy) in ((3, 0), (2, 1), (1, 2), (0, 3)):                # the fresh break along the chip's edge, paler
            c.set(xa + dx, ya + dy, step(ASH, 5 if dx < 3 else 4))
    return c


def ashlar_k():
    return fit_factor(ashlar_raw(0), (122, 121, 122))


def ashlar(variant):
    return scale_by(ashlar_raw(variant), ashlar_k())


# ----- roof tile variants: the same base as slate_tiles / clay_tiles with a few features added after the colour fit -----


def _slate_spans():
    """[(course, index, xa, xb)] for every slate, xb exclusive and unwrapped (> 16 when the slate wraps the edge)."""
    out = []
    for j, joints in enumerate(SLATE_COURSES):
        for i, xa in enumerate(joints):
            xb = joints[(i + 1) % 3] + (16 if i == 2 else 0)
            out.append((j, i, xa, xb))
    return out


def slate_variant(v):
    c = slate_tiles()
    rng = rng_for("city_kit", "slate-variant", v)
    spans = [s for s in _slate_spans() if s[3] <= 16]       # leave the slates that wrap the edge alone: they must match their neighbours
    if v == 1:           # a few new slates, lighter and bluer
        for (j, i, xa, xb) in rng.sample(spans, 3):
            for x in range(xa, xb):
                for r in range(3):
                    c.set(x, j * 4 + r, tint(mul(c.get(x, j * 4 + r), 1.13), -2, 1, 4))
    elif v == 2:         # lichen on one slate: a pale grey-green patch with darker pits
        j, i, xa, xb = rng.choice([s for s in spans if s[0] in (1, 2)])
        for _ in range(7):
            x, y = rng.randrange(xa, xb - 1), j * 4 + rng.randrange(0, 3)
            c.set(x, y, lerp(c.get(x, y), rng.choice([(124, 138, 98), (150, 160, 116), (104, 120, 84)]), 0.7))
    elif v == 3:         # a slipped, cracked slate: a dark gap above it, a crack through it
        j, i, xa, xb = rng.choice([s for s in spans if s[0] in (1, 2) and s[3] - s[2] >= 5])
        for x in range(xa, xb):
            c.set(x, j * 4, mul(c.get(x, j * 4), 0.62))                 # the gap it slid down from
        walk(c, xa + 2, j * 4 + 1, 3, mul(c.get(xa, j * 4 + 1), 0.45), rng, bias=(1, 1), wobble=0.2)
        c.set(xa + 1, j * 4 + 2, tint(c.get(xa + 1, j * 4 + 2), 26, 26, 24))
        for x in range(xa, xb):
            c.set(x, j * 4 + 3, step(SLATE, 0))
    return c


def clay_variant(v):
    c = clay_tiles()
    rng = rng_for("city_kit", "clay-variant", v)
    cells = [(i, j) for i in range(4) for j in range(4) if not (i % 2 == 1 and j == 3)]   # an odd column's last tile wraps the edge
    pick = rng.sample(cells, 3)

    def tile_pixels(i, j):
        off = 2 if i % 2 else 0
        return [(i * 4 + dx, (j * 4 + dy + off) % 16, dy) for dy in range(4) for dx in range(4)]
    if v == 1:           # a few new tiles: lighter, a little pinker
        for (i, j) in pick:
            for (x, y, dy) in tile_pixels(i, j):
                c.set(x, y, tint(mul(c.get(x, y), 1.12), 6, 6, 6))
    elif v == 2:         # a darker, weathered tile or two with a mossy speck
        for (i, j) in pick[:2]:
            for (x, y, dy) in tile_pixels(i, j):
                c.set(x, y, tint(mul(c.get(x, y), 0.78), -4, -2, 0))
        (i, j) = pick[0]
        x, y, _ = tile_pixels(i, j)[5]
        c.set(x, y, lerp(c.get(x, y), (96, 112, 64), 0.7))
    elif v == 3:         # a cracked tile that has slipped: a dark gap line, a crack down it
        (i, j) = pick[0]
        px = tile_pixels(i, j)
        for (x, y, dy) in px:
            if dy == 0:
                c.set(x, y, mul(c.get(x, y), 0.55))
        for (x, y, dy) in px:
            if dx_of(x) == 1 and dy in (1, 2):
                c.set(x, y, mul(c.get(x, y), 0.5))
    return c


def dx_of(x):
    return x % 4


# ---------------------------------------------------------------------------------------------------------------------
# registry


def textures():
    t = {}
    t["slate_tiles"] = slate_tiles()
    t["clay_tiles"] = clay_tiles()
    t["flower_box_wood"] = flower_box_wood()
    t["flower_box_iron"] = flower_box_iron()
    t["flower_box_stone"] = flower_box_stone()
    t["flower_box_soil"] = flower_box_soil()
    for b in ("red", "yellow", "blue", "pink", "white", "herbs"):
        t["bloom_" + b] = globals()["bloom_" + b]()
    t.update(shutters())
    t["drainpipe"] = drainpipe()
    t["drainpipe_head"] = drainpipe_head()
    t["trellis"] = trellis()
    t["trellis_ivy"] = trellis_ivy()
    t.update(windows())
    t.update(loading_door())
    t["rope"] = rope()
    t["hoist_pulley"] = hoist_pulley()
    for m in ("brick", "mud", "stone"):
        t[f"chimney_flue_{m}"] = chimney_flue(m)
        t[f"chimney_band_{m}"] = chimney_band(m)
    for colour in PLASTER_ORDER:
        for v in range(4):
            t[f"plaster_{colour}_{v}"] = plaster(colour, v)
    for tone in ("dark", "pale"):
        for v in range(4):
            t[f"timber_{tone}_side_{v}"] = timber_side(tone, v)
        for v in range(2):
            t[f"timber_{tone}_end_{v}"] = timber_end(tone, v)
    for v in range(5):
        t[f"city_bricks_{v}"] = city_bricks(v)
    for v in range(6):
        t[f"ashlar_{v}"] = ashlar(v)
    for v in (1, 2, 3):
        t[f"slate_tiles_{v}"] = slate_variant(v)
        t[f"clay_tiles_{v}"] = clay_variant(v)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c)


# ---------------------------------------------------------------------------------------------------------------------
# content: blockstates, models, lang, mineable tags

DIRS = ("north", "east", "south", "west")
YROT = {"north": 0, "east": 90, "south": 180, "west": 270}
CUT = "minecraft:cutout"

STYLES_2TALL = WINDOWS_TALL
STYLES_SINGLE = ("dormer", "round")
ALL_STYLES = ["leaded", "sash", "arched", "cottage", "warehouse", "barred", "works", "shop", "dormer", "round"]  # Java enum order
SHUTTER_COLOURS = ["blue", "sage", "oxblood", "natural", "green", "cream"]
BLOOMS = ["none", "red", "yellow", "blue", "pink", "white", "herbs"]
BOX_STYLES = ["wood", "iron", "stone"]
DOOR_PARTS = ["lower_left", "lower_right", "upper_left", "upper_right"]
CHIMNEY_MATERIALS = {"brick": "bricks", "mud": "mud_bricks", "stone": "stone_bricks"}
LANG = {
    "slate_tiles": "Slate Tiles", "slate_tile_stairs": "Slate Tile Stairs", "slate_tile_slab": "Slate Tile Slab",
    "clay_tiles": "Clay Tiles", "clay_tile_stairs": "Clay Tile Stairs", "clay_tile_slab": "Clay Tile Slab",
    "flower_box": "Flower Box", "shutter": "Shutter", "drainpipe": "Drainpipe", "trellis": "Trellis",
    "lit_window": "Lit Window", "loading_door": "Loading Door", "hoist_beam": "Hoist Beam", "rope": "Rope",
    "chimney_bricks": "Chimney Crown", "plaster": "Plaster", "timber": "Timber", "city_bricks": "City Bricks",
    "ashlar": "Ashlar",
}
PICKAXE = ["slate_tiles", "slate_tile_stairs", "slate_tile_slab", "clay_tiles", "clay_tile_stairs", "clay_tile_slab",
           "chimney_bricks", "drainpipe", "plaster", "city_bricks", "ashlar"]
AXE = ["flower_box", "shutter", "trellis", "lit_window", "loading_door", "hoist_beam", "rope", "timber"]


WRITTEN = []


def _write(path, data):
    WRITTEN.append(path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def _merge(path, data):
    WRITTEN.append(path)
    cur = {}
    if os.path.exists(path):
        with open(path) as f:
            cur = json.load(f)
    if "values" in data and isinstance(cur.get("values"), list):
        cur["values"] += [v for v in data["values"] if v not in cur["values"]]
    else:
        cur.update(data)
    _write(path, cur)


def b(n):
    return f"awaken:block/{n}"


def model(name, data):
    _write(os.path.join(ASSETS, f"models/block/{name}.json"), data)


def blockstate(name, variants):
    _write(os.path.join(ASSETS, f"blockstates/{name}.json"), {"variants": variants})


def face(tex, uv=None, **kw):
    f = {"texture": tex}
    if uv is not None:
        f["uv"] = list(uv)
    f.update(kw)
    return f


def rot_variant(model_name, facing):
    v = {"model": b(model_name)}
    if YROT[facing]:
        v["y"] = YROT[facing]
    return v


ROOF_VARIANTS = [(0, 5), (1, 2), (2, 1), (3, 1)]     # (texture variant, weight): variant 0 is the base texture


def vname(base, v, suffix=""):
    """Model name of texture variant v: the plain name for variant 0, base_<v> for the others."""
    return base + ("" if v == 0 else f"_{v}") + suffix


def weighted(entries):
    """A blockstate value: one model dict, or a list of them with weights (the game picks one per block position)."""
    out = []
    for e in entries:
        d = {"model": b(e["model"])}
        for k in ("x", "y"):
            if e.get(k):
                d[k] = e[k]
        if e.get("uvlock"):
            d["uvlock"] = True
        if len(entries) > 1:
            d["weight"] = e.get("weight", 1)
        out.append(d)
    return out if len(out) > 1 else out[0]


def _stairs_variants(prefix, variants=ROOF_VARIANTS):
    """Vanilla's stairs table (rotations by its rule; the check script compares it with deepslate_tile_stairs.json), every state
    a weighted list of that state's model in each texture variant."""
    out = {}
    base = {"east": 0, "south": 90, "west": 180, "north": 270}
    for facing, by in base.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                if half == "bottom":
                    x, y = 0, by + (270 if shape.endswith("left") else 0)
                else:
                    x, y = 180, by + (0 if shape in ("straight",) or shape.endswith("left") else 90)
                y %= 360
                suffix = {"straight": "", "inner": "_inner", "outer": "_outer"}[shape.split("_")[0]]
                out[f"facing={facing},half={half},shape={shape}"] = weighted(
                    [{"model": vname(prefix, v, suffix), "weight": w, "x": x, "y": y, "uvlock": bool(x or y)} for v, w in variants])
    return out


def roof_cube_entries(family):
    t = f"{family}_tiles"
    return [{"model": t, "weight": 3, "y": 0}, {"model": t, "weight": 2, "y": 180}] + [
        {"model": vname(t, v), "weight": w, "y": 0} for v, w in ROOF_VARIANTS[1:]]


def content_roofs():
    for name, tile, stairs, slab in (("slate", "slate_tiles", "slate_tile_stairs", "slate_tile_slab"),
                                     ("clay", "clay_tiles", "clay_tile_stairs", "clay_tile_slab")):
        for v, w in ROOF_VARIANTS:
            t = vname(tile, v)
            model(t, {"parent": "minecraft:block/cube_all", "textures": {"all": b(t)}})
            tx = {"bottom": b(t), "side": b(t), "top": b(t)}
            model(vname(stairs, v), {"parent": "minecraft:block/stairs", "textures": tx})
            model(vname(stairs, v, "_inner"), {"parent": "minecraft:block/inner_stairs", "textures": tx})
            model(vname(stairs, v, "_outer"), {"parent": "minecraft:block/outer_stairs", "textures": tx})
            model(vname(slab, v), {"parent": "minecraft:block/slab", "textures": tx})
            model(vname(slab, v, "_top"), {"parent": "minecraft:block/slab_top", "textures": tx})
        blockstate(tile, {"": weighted(roof_cube_entries(name))})
        blockstate(stairs, _stairs_variants(stairs))
        blockstate(slab, {"type=bottom": weighted([{"model": vname(slab, v), "weight": w} for v, w in ROOF_VARIANTS]),
                          "type=top": weighted([{"model": vname(slab, v, "_top"), "weight": w} for v, w in ROOF_VARIANTS]),
                          "type=double": weighted(roof_cube_entries(name))})


# ----- the walls' kit: plaster, timber, city bricks, ashlar (weighted random variants) ---------------------------------------


def plaster_entries(colour):
    """Clean (weight 6, as plain, mirrored and turned 180), crack 2, worn patch 1, weather streak 1."""
    p = f"plaster_{colour}"
    return [{"model": f"{p}_0", "weight": 2, "y": 0}, {"model": f"{p}_0_mirrored", "weight": 2, "y": 0},
            {"model": f"{p}_0", "weight": 1, "y": 180}, {"model": f"{p}_0_mirrored", "weight": 1, "y": 180},
            {"model": f"{p}_1", "weight": 2, "y": 0}, {"model": f"{p}_2", "weight": 1, "y": 0},
            {"model": f"{p}_3", "weight": 1, "y": 0}]


TIMBER_MIX = [(0, 0, 3), (0, 1, 2), (1, 0, 2), (2, 1, 1), (3, 0, 1), (3, 1, 1)]   # (side, end, weight): sides 5 / 2 / 1 / 2


def timber_entries(tone, horizontal=False):
    return [{"model": f"timber_{tone}_{s}{e}" + ("_horizontal" if horizontal else ""), "weight": w}
            for (s, e, w) in TIMBER_MIX]


def bricks_entries():
    return [{"model": "city_bricks_0", "weight": 3, "y": 0}, {"model": "city_bricks_0", "weight": 2, "y": 180},
            {"model": "city_bricks_1", "weight": 2, "y": 0}, {"model": "city_bricks_2", "weight": 2, "y": 0},
            {"model": "city_bricks_3", "weight": 1, "y": 0}, {"model": "city_bricks_4", "weight": 1, "y": 0}]


def ashlar_entries():
    return [{"model": "ashlar_0", "weight": 3, "y": 0}, {"model": "ashlar_0", "weight": 2, "y": 180},
            {"model": "ashlar_1", "weight": 2, "y": 0}, {"model": "ashlar_2", "weight": 1, "y": 0},
            {"model": "ashlar_3", "weight": 1, "y": 0}, {"model": "ashlar_4", "weight": 1, "y": 0},
            {"model": "ashlar_5", "weight": 1, "y": 0}]


def content_walls():
    # plaster
    variants = {}
    for colour in PLASTER_ORDER:
        for v in range(4):
            model(f"plaster_{colour}_{v}", {"parent": "minecraft:block/cube_all", "textures": {"all": b(f"plaster_{colour}_{v}")}})
        model(f"plaster_{colour}_0_mirrored", {"parent": "minecraft:block/cube_mirrored_all", "textures": {"all": b(f"plaster_{colour}_0")}})
        variants[f"colour={colour}"] = weighted(plaster_entries(colour))
    blockstate("plaster", variants)
    # timber
    variants = {}
    for tone in ("dark", "pale"):
        for (s, e, w) in TIMBER_MIX:
            tx = {"side": b(f"timber_{tone}_side_{s}"), "end": b(f"timber_{tone}_end_{e}")}
            model(f"timber_{tone}_{s}{e}", {"parent": "minecraft:block/cube_column", "textures": tx})
            model(f"timber_{tone}_{s}{e}_horizontal", {"parent": "minecraft:block/cube_column_horizontal", "textures": tx})
        for axis in ("y", "x", "z"):
            ents = timber_entries(tone, horizontal=axis != "y")
            if axis == "x":
                ents = [dict(e, x=90, y=90) for e in ents]
            elif axis == "z":
                ents = [dict(e, x=90) for e in ents]
            variants[f"axis={axis},tone={tone}"] = weighted(ents)
    blockstate("timber", variants)
    # city bricks and ashlar: a single weighted list on the plain state
    for v in range(5):
        model(f"city_bricks_{v}", {"parent": "minecraft:block/cube_all", "textures": {"all": b(f"city_bricks_{v}")}})
    blockstate("city_bricks", {"": weighted(bricks_entries())})
    for v in range(6):
        model(f"ashlar_{v}", {"parent": "minecraft:block/cube_all", "textures": {"all": b(f"ashlar_{v}")}})
    blockstate("ashlar", {"": weighted(ashlar_entries())})


def _bracket(x0, y0, z0, x1, y1, z1, uv, down=True, up=False):
    """A small solid piece under the planter (y may go below the cell: the box hangs on its brackets)."""
    f = {d: face("#box", uv) for d in ("north", "east", "west")}
    if down:
        f["down"] = face("#box", uv)
    if up:
        f["up"] = face("#box", uv)
    return {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": f}


def flower_box_brackets(style):
    """Two brackets under the planter: dark L-brackets (wood), stepped stone corbels (stone), scrolled irons (iron)."""
    els = []
    if style == "wood":
        for x in (3, 12):
            uv = [3, 13, 4, 15]
            els.append(_bracket(x, -1, 5, x + 1, 0, 16, uv))                 # the arm under the planter, wall to front edge
            els.append(_bracket(x, -3, 15, x + 1, -1, 16, uv))               # the leg down the wall
            els.append(_bracket(x, -2, 5, x + 1, -1, 6, uv))                 # a turned-up lip at the front
    elif style == "stone":
        for x in (3, 11):
            uv = [1, 9, 4, 12]
            els.append(_bracket(x, -2, 10, x + 2, 0, 16, uv))                # the upper step of the corbel
            els.append(_bracket(x, -4, 13, x + 2, -2, 16, uv))               # the lower step, under it
    else:
        for x in (3, 12):
            uv = [3, 12, 4, 14]
            els.append(_bracket(x, -1, 5, x + 1, 0, 16, uv))                 # the arm
            els.append(_bracket(x, -4, 15, x + 1, -1, 16, uv))               # the leg down the wall
            els.append(_bracket(x, -3, 13, x + 1, -2, 15, uv))               # the scroll's diagonal, in steps
            els.append(_bracket(x, -2, 10, x + 1, -1, 13, uv))
            els.append(_bracket(x, -2, 5, x + 1, -1, 6, uv))                 # the curl at the front end
            els.append(_bracket(x, -3, 5, x + 1, -2, 6, uv))
    return els


def flower_box_model(style, bloom):
    box = b(f"flower_box_{style}")
    tex = {"particle": b("flower_box_soil") if style == "iron" else box, "box": box, "soil": b("flower_box_soil")}
    els = [{"from": [1, 0, 5], "to": [15, 7, 16],
            "faces": {"north": face("#box", [1, 9, 15, 16]), "east": face("#box", [2, 9, 13, 16]),
                      "west": face("#box", [2, 9, 13, 16]), "down": face("#box", [1, 0, 15, 11])}}]
    # the rim, as four flat strips at the top edge (the planter is open: its box has no top face)
    for (f0, t0, u) in (((1, 7, 5), (15, 7, 6), [1, 9, 15, 10]), ((1, 7, 15), (15, 7, 16), [1, 9, 15, 10]),
                        ((1, 7, 6), (2, 7, 15), [1, 9, 2, 10]), ((14, 7, 6), (15, 7, 15), [1, 9, 2, 10])):
        els.append({"from": list(f0), "to": list(t0), "faces": {"up": face("#box", u)}})
    if style == "iron":   # the soil sits inside the basket, seen through the bars; its top is at y 5 (no floor face: the plate is under it)
        els.append({"from": [2, 0, 6], "to": [14, 5, 15],
                    "faces": {"up": face("#soil", [2, 2, 14, 11]), "north": face("#soil", [2, 2, 14, 7]),
                              "east": face("#soil", [2, 2, 11, 7]), "west": face("#soil", [2, 2, 11, 7])}})
    else:                 # the soil sits in the open planter, a half pixel under the rim
        els.append({"from": [2, 0, 6], "to": [14, 6.5, 15], "faces": {"up": face("#soil", [2, 2, 14, 11])}})
    els += flower_box_brackets(style)
    if bloom != "none":
        tex["bloom"] = b(f"bloom_{bloom}")
        els.append({"from": [1, 5, 10.5], "to": [15, 15, 10.5], "shade_direction_override": "up",
                    "faces": {"north": face("#bloom", [0, 6, 16, 16]), "south": face("#bloom", [0, 6, 16, 16])}})
        els.append({"from": [8, 5, 6], "to": [8, 15, 15], "shade_direction_override": "up",
                    "faces": {"west": face("#bloom", [3, 6, 12, 16]), "east": face("#bloom", [3, 6, 12, 16])}})
    m = {"ambientocclusion": False, "textures": tex, "elements": els}
    if style == "iron" or bloom != "none":
        m = {"render_type": CUT, **m}
    return m


def content_flower_box():
    variants = {}
    for style in BOX_STYLES:
        for bloom in BLOOMS:
            name = f"flower_box_{style}_{bloom}"
            model(name, flower_box_model(style, bloom))
            for facing in DIRS:
                variants[f"bloom={bloom},facing={facing},style={style}"] = rot_variant(name, facing)
    blockstate("flower_box", variants)


def shutter_model(colour, half, hinge):
    """An 8px panel (z 14..16) hugging the window side of its cell. Seen from the north the viewer's left is +x, so
    hinge=left is the panel at x 8..16 (the image as drawn) and hinge=right the panel at x 0..8 (the image mirrored)."""
    t = f"shutter_{colour}_{half}"
    if hinge == "left":
        x0, x1, north = 8, 16, [0, 0, 8, 16]
        east, west = [0, 0, 2, 16], [6, 0, 8, 16]      # the hinge edge is the outer one, at x=16
    else:
        x0, x1, north = 0, 8, [8, 0, 0, 16]
        east, west = [6, 0, 8, 16], [0, 0, 2, 16]      # the hinge edge is at x=0
    return {"textures": {"particle": b(t), "t": b(t)},
            "elements": [{"from": [x0, 0, 14], "to": [x1, 16, 16],
                          "faces": {"north": face("#t", north), "east": face("#t", east), "west": face("#t", west),
                                    "up": face("#t", [0, 0, 8, 2]), "down": face("#t", [0, 14, 8, 16])}}]}


def content_shutter():
    variants = {}
    for colour in SHUTTER_COLOURS:
        for half in ("lower", "upper"):
            for hinge in ("left", "right"):
                name = f"shutter_{colour}_{half}_{hinge}"
                model(name, shutter_model(colour, half, hinge))
                for facing in DIRS:
                    variants[f"colour={colour},facing={facing},half={half},hinge={hinge}"] = rot_variant(name, facing)
    blockstate("shutter", variants)


def content_drainpipe():
    pipe_t = {"particle": b("drainpipe"), "pipe": b("drainpipe")}
    bracket = {"from": [5, 12, 10], "to": [11, 13, 16],
               "faces": {"north": face("#pipe", [10, 6, 16, 7]), "east": face("#pipe", [10, 6, 16, 7]),
                         "west": face("#pipe", [10, 6, 16, 7]), "up": face("#pipe", [10, 0, 16, 6]),
                         "down": face("#pipe", [10, 0, 16, 6])}}

    def column(y0, y1, uv_v):
        v0, v1 = uv_v
        return {"from": [6, y0, 11], "to": [10, y1, 15],
                "faces": {d: face("#pipe", [6, v0, 10, v1]) for d in DIRS} | {"up": face("#pipe", [6, 0, 10, 4]),
                                                                                 "down": face("#pipe", [6, 0, 10, 4])}}
    model("drainpipe_pipe", {"ambientocclusion": False, "textures": pipe_t, "elements": [column(0, 16, (0, 16)), bracket]})
    head_t = {"particle": b("drainpipe_head"), "pipe": b("drainpipe"), "head": b("drainpipe_head")}
    model("drainpipe_head", {"ambientocclusion": False, "textures": head_t, "elements": [
        {"from": [4, 6, 9], "to": [12, 13, 16],
         "faces": {"north": face("#head", [0, 0, 8, 7]), "east": face("#head", [0, 0, 7, 7]),
                   "west": face("#head", [0, 0, 7, 7]), "down": face("#head", [0, 0, 8, 7])}},
        {"from": [3, 13, 8], "to": [13, 16, 16],
         "faces": {"north": face("#head", [0, 7, 10, 10]), "east": face("#head", [0, 7, 8, 10]),
                   "west": face("#head", [0, 7, 8, 10]), "up": face("#head", [0, 10, 10, 16]),
                   "down": face("#head", [0, 7, 10, 10])}},
        {"from": [6, 0, 11], "to": [10, 6, 15],
         "faces": {d: face("#pipe", [6, 10, 10, 16]) for d in DIRS} | {"down": face("#pipe", [6, 0, 10, 4])}}]})
    model("drainpipe_shoe", {"ambientocclusion": False, "textures": pipe_t, "elements": [
        {"from": [6, 4, 11], "to": [10, 16, 15],
         "faces": {d: face("#pipe", [6, 0, 10, 12]) for d in DIRS} | {"up": face("#pipe", [6, 0, 10, 4])}},
        {"from": [6, 0, 6], "to": [10, 4, 15],
         "faces": {"north": face("#pipe", [6, 12, 10, 16]), "south": face("#pipe", [6, 12, 10, 16]),
                   "east": face("#pipe", [6, 0, 10, 9], rotation=90),
                   "west": face("#pipe", [6, 0, 10, 9], rotation=90), "up": face("#pipe", [6, 0, 10, 9]),
                   "down": face("#pipe", [6, 0, 10, 9])}}]})
    variants = {}
    for part, m in (("pipe", "drainpipe_pipe"), ("head", "drainpipe_head"), ("shoe", "drainpipe_shoe")):
        for facing in DIRS:
            variants[f"facing={facing},part={part}"] = rot_variant(m, facing)
    blockstate("drainpipe", variants)


def content_trellis():
    lat = {"from": [0, 0, 15], "to": [16, 16, 15], "shade_direction_override": "up",
           "faces": {"north": face("#lattice", [0, 0, 16, 16]), "south": face("#lattice", [16, 0, 0, 16])}}
    ivy = {"from": [0, 0, 14], "to": [16, 16, 14], "shade_direction_override": "up",
           "faces": {"north": face("#ivy", [0, 0, 16, 16]), "south": face("#ivy", [16, 0, 0, 16])}}
    model("trellis", {"render_type": CUT, "ambientocclusion": False,
                      "textures": {"particle": b("trellis"), "lattice": b("trellis")}, "elements": [lat]})
    model("trellis_ivy", {"render_type": CUT, "ambientocclusion": False,
                          "textures": {"particle": b("trellis_ivy"), "lattice": b("trellis"), "ivy": b("trellis_ivy")},
                          "elements": [lat, ivy]})
    variants = {}
    for ivy_on in ("false", "true"):
        for facing in DIRS:
            variants[f"facing={facing},ivy={ivy_on}"] = rot_variant("trellis_ivy" if ivy_on == "true" else "trellis", facing)
    blockstate("trellis", variants)


def lit_window_model(style, half, lit):
    if style in STYLES_SINGLE:
        tex = f"window_{style}_single"
    else:
        tex = f"window_{style}_{'upper' if half == 'single' else half}"
    textures = {"particle": b(tex), "w": b(tex), "wood": "minecraft:block/dark_oak_planks"}
    side = {d: face("#w", [0, 0, 16, 16], cullface=d) for d in DIRS}
    els = [{"from": [0, 0, 0], "to": [16, 16, 16],
            "faces": side | {"up": face("#wood", cullface="up"), "down": face("#wood", cullface="down")}}]
    m = {"parent": "minecraft:block/block", "textures": textures, "elements": els}
    if lit:
        textures["glow"] = b(tex + "_glow")
        els.append({"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 15, "shade_direction_override": "up",
                    "faces": {d: face("#glow", [0, 0, 16, 16], cullface=d) for d in DIRS}})
        m["render_type"] = CUT
    return m


def lit_window_key(style, half):
    if style in STYLES_SINGLE:
        return "single"
    return half


def content_lit_window():
    variants = {}
    made = set()
    for style in ALL_STYLES:
        for half in ("lower", "upper", "single"):
            for lit in (False, True):
                key = lit_window_key(style, half)
                name = f"lit_window_{style}_{key}" + ("_lit" if lit else "")
                if name not in made:
                    made.add(name)
                    model(name, lit_window_model(style, key, lit))
                variants[f"half={half},lit={'true' if lit else 'false'},style={style}"] = {"model": b(name)}
    blockstate("lit_window", variants)


def content_loading_door():
    variants = {}
    for part in DOOR_PARTS:
        t = b(f"loading_door_{part}")
        faces = {"north": face("#front", [0, 0, 16, 16], cullface="north")}
        faces |= {d: face("#side", [0, 0, 16, 16], cullface=d) for d in ("east", "south", "west", "up", "down")}
        model(f"loading_door_{part}", {"parent": "minecraft:block/block",
                                       "textures": {"particle": t, "front": t, "side": "minecraft:block/spruce_planks"},
                                       "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]})
        for facing in DIRS:
            variants[f"facing={facing},part={part}"] = rot_variant(f"loading_door_{part}", facing)
    blockstate("loading_door", variants)


def content_hoist_beam():
    beam_t = {"particle": "minecraft:block/stripped_dark_oak_log", "side": "minecraft:block/stripped_dark_oak_log",
              "end": "minecraft:block/stripped_dark_oak_log_top"}
    beam = {"from": [5, 8, 0], "to": [11, 14, 16],
            "faces": {"north": face("#end", [5, 5, 11, 11]), "up": face("#side", [5, 0, 11, 16]),
                      "down": face("#side", [5, 0, 11, 16]), "east": face("#side", [5, 0, 11, 16], rotation=90),
                      "west": face("#side", [5, 0, 11, 16], rotation=90)}}
    model("hoist_beam", {"textures": beam_t, "elements": [beam]})
    pt = dict(beam_t, pulley=b("hoist_pulley"))
    wheel = {"from": [7, 0, 2], "to": [9, 7, 7],      # a pixel lower than first briefed, so the strap shows between wheel and beam
             "faces": {"east": face("#pulley", [5, 4, 10, 11]), "west": face("#pulley", [5, 4, 10, 11]),
                       "north": face("#pulley", [0, 0, 2, 4]), "south": face("#pulley", [0, 0, 2, 4]),
                       "up": face("#pulley", [0, 0, 2, 4]), "down": face("#pulley", [0, 0, 2, 4])}}
    strap = {"from": [7.5, 7, 3.5], "to": [8.5, 8, 5.5],
             "faces": {"east": face("#pulley", [0, 0, 2, 1]), "west": face("#pulley", [0, 0, 2, 1]),
                       "north": face("#pulley", [0, 0, 1, 1]), "south": face("#pulley", [0, 0, 1, 1]),
                       "down": face("#pulley", [0, 0, 1, 2])}}
    model("hoist_beam_pulley", {"render_type": CUT, "textures": pt, "elements": [beam, wheel, strap]})
    variants = {}
    for pulley in ("false", "true"):
        for facing in DIRS:
            variants[f"facing={facing},pulley={pulley}"] = rot_variant("hoist_beam_pulley" if pulley == "true" else "hoist_beam", facing)
    blockstate("hoist_beam", variants)


def content_rope():
    model("rope", {"ambientocclusion": False, "textures": {"particle": b("rope"), "rope": b("rope")}, "elements": [
        {"from": [7, 0, 7], "to": [9, 16, 9],
         "faces": {d: face("#rope", [7, 0, 9, 16]) for d in DIRS} | {"up": face("#rope", [7, 7, 9, 9]),
                                                                      "down": face("#rope", [7, 7, 9, 9])}}]})
    blockstate("rope", {"": {"model": b("rope")}})


def content_chimney():
    variants = {}
    for m, vanilla in CHIMNEY_MATERIALS.items():
        base = f"minecraft:block/{vanilla}"
        faces = {d: face("#base", [0, 0, 16, 16], cullface=d) for d in ("down", "north", "south", "east", "west")}
        faces["up"] = face("#flue", [0, 0, 16, 16])
        band = {"from": [-1, 12, -1], "to": [17, 15, 17],
                "faces": {d: face("#band", [0, 0, 16, 3]) for d in DIRS} | {"up": face("#flue", [0, 0, 16, 16]),
                                                                              "down": face("#band", [0, 3, 16, 16])}}
        model(f"chimney_bricks_{m}", {"parent": "minecraft:block/block", "textures": {
            "particle": base, "base": base, "band": b(f"chimney_band_{m}"), "flue": b(f"chimney_flue_{m}")},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}, band]})
        variants[f"material={m}"] = {"model": b(f"chimney_bricks_{m}")}
    blockstate("chimney_bricks", variants)


def content():
    content_roofs()
    content_walls()
    content_flower_box()
    content_shutter()
    content_drainpipe()
    content_trellis()
    content_lit_window()
    content_loading_door()
    content_hoist_beam()
    content_rope()
    content_chimney()
    _merge(os.path.join(MERGE, "assets/awaken/lang/en_us.json"), {f"block.awaken.{k}": v for k, v in LANG.items()})
    _merge(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), {"values": [f"awaken:{n}" for n in PICKAXE]})
    _merge(os.path.join(MERGE, "data/minecraft/tags/block/mineable/axe.json"), {"values": [f"awaken:{n}" for n in AXE]})
    print("city_kit: content written")



# ---------------------------------------------------------------------------------------------------------------------
# check: every blockstate variant covers every property combination of the Java block, every model and texture resolves


CLASS_TO_BLOCK = {"FlowerBox": "flower_box", "Shutter": "shutter", "Drainpipe": "drainpipe", "Trellis": "trellis",
                  "LitWindow": "lit_window", "LoadingDoor": "loading_door", "Rope": "rope", "HoistBeam": "hoist_beam",
                  "ChimneyBricks": "chimney_bricks", "Plaster": "plaster", "Timber": "timber"}
SIMPLE_BLOCKS = ("city_bricks", "ashlar")   # registered with registerSimpleBlock: no class, no properties
BUILTIN_PROPS = {"DOOR_HINGE": ("hinge", ["left", "right"]), "DOUBLE_BLOCK_HALF": ("half", ["lower", "upper"]),
                 "LIT": ("lit", ["false", "true"])}


def parse_kitblocks():
    """block id -> {property: [values]} from KitBlocks.java (its enums, properties and createBlockStateDefinition)."""
    src = open(os.path.join(ROOT, "src/main/java/dev/awaken/city/kit/KitBlocks.java")).read()
    out = {}
    parts = re.split(r"public static final class (\w+) ", src)[1:]
    for cls, body in zip(parts[0::2], parts[1::2]):
        if cls not in CLASS_TO_BLOCK:
            continue
        enums = {m.group(1): [v.strip().lower() for v in m.group(2).split(",")]
                 for m in re.finditer(r"enum (\w+) implements Named \{ ([A-Z_, ]+) \}", body)}
        consts = {}
        for m in re.finditer(r"EnumProperty<\w+> (\w+) = EnumProperty\.create\(\"(\w+)\", (\w+)\.class\)", body):
            consts[m.group(1)] = (m.group(2), enums[m.group(3)])
        for m in re.finditer(r"BooleanProperty (\w+) = BooleanProperty\.create\(\"(\w+)\"\)", body):
            consts[m.group(1)] = (m.group(2), ["false", "true"])
        for m in re.finditer(r"EnumProperty<\w+> (\w+) = BlockStateProperties\.(\w+);", body):
            consts[m.group(1)] = BUILTIN_PROPS[m.group(2)]
        for m in re.finditer(r"BooleanProperty (\w+) = BlockStateProperties\.(\w+);", body):
            consts[m.group(1)] = BUILTIN_PROPS[m.group(2)]
        consts["FACING"] = ("facing", ["north", "east", "south", "west"])
        added = []
        mm = re.search(r"createBlockStateDefinition\([^)]*\) \{\s*(?:super\.[^;]*;\s*)?b\.add\(([^)]*)\)", body)
        if mm:
            added = [a.strip() for a in mm.group(1).split(",")]
        props = {}
        for a in added:
            name, vals = consts[a]
            props[name] = vals
        head = body.split("{", 1)[0]
        if "extends HorizontalDirectionalBlock" in head and "facing" not in props:
            props["facing"] = consts["FACING"][1]
        if "extends RotatedPillarBlock" in head and "axis" not in props:
            props["axis"] = ["x", "y", "z"]
        out[CLASS_TO_BLOCK[cls]] = props
    reg = open(os.path.join(ROOT, "src/main/java/dev/awaken/city/kit/CityKit.java")).read()
    for sb in SIMPLE_BLOCKS:
        if f'registerSimpleBlock("{sb}"' in reg:
            out[sb] = {}
    return out


def _combos(props):
    keys = sorted(props)
    out = [()]
    for k in keys:
        out = [c + ((k, v),) for c in out for v in props[k]]
    return {frozenset(c) for c in out}


def _vkey(k):
    return frozenset(tuple(p.split("=")) for p in k.split(",")) if k else frozenset()


def _load_model(ref, errors):
    ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
    p = os.path.join(ASSETS if ns == "awaken" else VANILLA, "models", path + ".json")
    if not os.path.exists(p):
        errors.append(f"missing model {ref}")
        return None
    with open(p) as f:
        return json.load(f)


def _tex_ok(ref):
    ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
    base = os.path.join(ASSETS if ns == "awaken" else VANILLA, "textures", path + ".png")
    return os.path.exists(base), base


def check():
    errors, notes = [], []
    props = parse_kitblocks()
    notes.append("java blocks parsed: " + ", ".join(f"{k}({'/'.join(v)})" for k, v in props.items()))
    from PIL import Image
    # 1. blockstates cover every combination
    used_models = set()
    for blk, pr in props.items():
        p = os.path.join(ASSETS, "blockstates", blk + ".json")
        if not os.path.exists(p):
            errors.append(f"missing blockstate {blk}")
            continue
        variants = json.load(open(p))["variants"]
        want = _combos(pr) if pr else {frozenset()}
        have = {_vkey(k) for k in variants}
        if want - have:
            errors.append(f"{blk}: {len(want - have)} property combinations without a variant, e.g. {sorted(next(iter(want - have)))}")
        if have - want:
            errors.append(f"{blk}: {len(have - want)} variants the block can't have, e.g. {sorted(next(iter(have - want)))}")
        n_models = 0
        for k, v in variants.items():
            for e in (v if isinstance(v, list) else [v]):
                used_models.add(e["model"])
                n_models += 1
                if isinstance(v, list) and not (isinstance(e.get("weight"), int) and e["weight"] >= 1):
                    errors.append(f"{blk} {k}: a weighted list entry without a positive integer weight")
        notes.append(f"{blk}: {len(variants)} variants" + (f", {n_models} weighted models" if n_models > len(variants) else ""))
    # stairs and slabs: same keys and rotations as vanilla's deepslate tile table
    for blk, van in (("slate_tile_stairs", "deepslate_tile_stairs"), ("clay_tile_stairs", "deepslate_tile_stairs"),
                     ("slate_tile_slab", "deepslate_tile_slab"), ("clay_tile_slab", "deepslate_tile_slab"),
                     ("slate_tiles", None), ("clay_tiles", None)):
        p = os.path.join(ASSETS, "blockstates", blk + ".json")
        if not os.path.exists(p):
            errors.append(f"missing blockstate {blk}")
            continue
        mine = json.load(open(p))["variants"]
        nm = 0
        for k, v in mine.items():
            for e in (v if isinstance(v, list) else [v]):
                used_models.add(e["model"])
                nm += 1
                if isinstance(v, list) and not (isinstance(e.get("weight"), int) and e["weight"] >= 1):
                    errors.append(f"{blk} {k}: a weighted list entry without a positive integer weight")
        if van and os.path.exists(os.path.join(VANILLA, "blockstates", van + ".json")):
            vv = json.load(open(os.path.join(VANILLA, "blockstates", van + ".json")))["variants"]
            if set(vv) != set(mine):
                errors.append(f"{blk}: variant keys differ from vanilla's {van}")
            for k, v in vv.items():
                if k == "type=double":   # the double slab is the full block: its list turns the cube 180 for its top and bottom
                    continue
                for m in (mine.get(k) if isinstance(mine.get(k), list) else [mine.get(k, {})]):
                    if (v.get("x", 0), v.get("y", 0), v.get("uvlock", False)) != (m.get("x", 0), m.get("y", 0), m.get("uvlock", False)):
                        errors.append(f"{blk} {k}: rotation differs from vanilla")
                        break
            notes.append(f"{blk}: {len(mine)} states, {nm} weighted models, rotations identical to vanilla {van}")
        else:
            notes.append(f"{blk}: {len(mine)} states, {nm} weighted models")
    # 2. models: exist, parents exist, every texture ref resolves, every sampled region is opaque unless the model is cutout
    cache = {}
    for ref in sorted(used_models):
        m = _load_model(ref, errors)
        if m is None:
            continue
        chain, cur, texmap, elements, render_type = [], m, {}, None, None
        seen = 0
        while cur is not None and seen < 8:
            seen += 1
            for k, v in cur.get("textures", {}).items():
                texmap.setdefault(k, v)
            if elements is None and "elements" in cur:
                elements = cur["elements"]
            render_type = render_type or cur.get("render_type")
            parent = cur.get("parent")
            cur = _load_model(parent, errors) if parent else None
        def resolve(t, depth=0):
            while isinstance(t, str) and t.startswith("#") and depth < 8:
                t = texmap.get(t[1:])
                depth += 1
            return t
        for k in texmap:
            t = resolve(texmap[k])
            if t is None or t.startswith("#"):
                errors.append(f"{ref}: texture key {k} does not resolve")
            else:
                ok, path = _tex_ok(t)
                if not ok and not (t.startswith("minecraft:") and not os.path.isdir(VANILLA)):
                    errors.append(f"{ref}: texture {t} not found")
        for el in elements or []:
            for dname, f in el.get("faces", {}).items():
                t = resolve(f["texture"])
                if t is None or t.startswith("#"):
                    errors.append(f"{ref}: face {dname} texture {f['texture']} unresolved")
                    continue
                uv = f.get("uv", [0, 0, 16, 16])
                if any(not (0 <= u <= 16) for u in uv):
                    errors.append(f"{ref}: face {dname} uv {uv} outside 0..16")
                ok, path = _tex_ok(t)
                if ok and t.startswith("awaken:") and render_type != CUT:
                    im = Image.open(path).convert("RGBA")
                    x0, x1 = sorted((int(math.floor(uv[0])), int(math.ceil(uv[2]))))
                    y0, y1 = sorted((int(math.floor(uv[1])), int(math.ceil(uv[3]))))
                    for yy in range(y0, y1):
                        for xx in range(x0, x1):
                            if im.getpixel((xx, yy))[3] < 255:
                                errors.append(f"{ref}: face {dname} samples a transparent texel of {t} at {xx},{yy} but the model is not cutout")
                                break
                        else:
                            continue
                        break
        cache[ref] = True
    notes.append(f"{len(used_models)} models referenced, all resolved" if not errors else f"{len(used_models)} models referenced")
    # 3. textures written
    t = textures()
    for name, c in t.items():
        p = os.path.join(TEX, name + ".png")
        if not os.path.exists(p):
            errors.append(f"texture {name}.png not written (run build_all.py city_kit)")
        else:
            im = Image.open(p)
            if im.size != (16, 16):
                errors.append(f"texture {name} is {im.size}")
    notes.append(f"{len(t)} textures")
    # 4. lang and tags
    lang = json.load(open(os.path.join(MERGE, "assets/awaken/lang/en_us.json")))
    for k in LANG:
        if f"block.awaken.{k}" not in lang:
            errors.append(f"lang block.awaken.{k} missing")
    for tag, names in (("pickaxe", PICKAXE), ("axe", AXE)):
        vals = json.load(open(os.path.join(MERGE, f"data/minecraft/tags/block/mineable/{tag}.json")))["values"]
        for n in names:
            if f"awaken:{n}" not in vals:
                errors.append(f"tag {tag} lacks {n}")
    for n in notes:
        print("  ", n)
    if errors:
        print("CHECK FAILED:")
        for e in errors[:60]:
            print("  -", e)
        print(f"  ({len(errors)} problems)")
        return 1
    print("check: ok")
    return 0


def manifest():
    """Every file this spec owns (for `git add`)."""
    content()
    paths = [os.path.join(TEX, n + ".png") for n in textures()] + [p for p in dict.fromkeys(WRITTEN)]
    for p in paths:
        print(os.path.relpath(p, ROOT))



# ---------------------------------------------------------------------------------------------------------------------
# contact sheet (kit textures only, in memory) and the facade mock

def _img(c):
    return c.image() if hasattr(c, "image") else c


def _lit(base, glow):
    im = _img(base).copy()
    im.alpha_composite(_img(glow))
    return im


def _stack(top, bottom):
    from PIL import Image
    im = Image.new("RGBA", (16, 32))
    im.paste(_img(top), (0, 0))
    im.paste(_img(bottom), (0, 16))
    return im


def _tiled(c, n=2):
    from PIL import Image
    im = _img(c)
    out = Image.new("RGBA", (16 * n, 16 * n))
    for j in range(n):
        for i in range(n):
            out.paste(im, (i * 16, j * 16))
    return out


def sheet_items(t):
    from PIL import Image
    items = [("slate_tiles x2", _tiled(t["slate_tiles"])), ("clay_tiles x2", _tiled(t["clay_tiles"]))]
    for n in ("flower_box_wood", "flower_box_iron", "flower_box_stone", "flower_box_soil"):
        items.append((n, t[n]))
    for n in ("red", "yellow", "blue", "pink", "white", "herbs"):
        items.append(("bloom_" + n, t["bloom_" + n]))
    for n in SHUTTER_COLOURS:
        items.append((f"shutter_{n}", _stack(t[f"shutter_{n}_upper"], t[f"shutter_{n}_lower"]).crop((0, 0, 8, 32))))
    for n in ("drainpipe", "drainpipe_head", "trellis", "trellis_ivy"):
        items.append((n, t[n]))
    ti = _tiled(t["trellis"], 1).copy()
    ti.alpha_composite(_img(t["trellis_ivy"]))
    items.append(("trellis+ivy", ti))
    for s in WINDOWS_TALL:
        base = _stack(t[f"window_{s}_upper"], t[f"window_{s}_lower"])
        glow = _stack(t[f"window_{s}_upper_glow"], t[f"window_{s}_lower_glow"])
        items.append((f"window_{s}", base))
        items.append((f"{s} lit", _lit(base, glow)))
    for s in ("dormer", "round"):
        items.append((f"window_{s}_single", t[f"window_{s}_single"]))
        items.append((f"{s} lit", _lit(t[f"window_{s}_single"], t[f"window_{s}_single_glow"])))
    door = Image.new("RGBA", (32, 32))
    for n, xy in (("upper_left", (0, 0)), ("upper_right", (16, 0)), ("lower_left", (0, 16)), ("lower_right", (16, 16))):
        door.paste(_img(t["loading_door_" + n]), xy)
    items.append(("loading_door x4", door))
    items.append(("rope", t["rope"]))
    items.append(("hoist_pulley", t["hoist_pulley"]))
    for m in ("brick", "mud", "stone"):
        items.append((f"chimney_flue_{m}", t[f"chimney_flue_{m}"]))
        items.append((f"chimney_band_{m}", t[f"chimney_band_{m}"]))
    return items


def make_sheet(out, prefix=()):
    from PIL import Image, ImageDraw
    S, GAP, MAXW = 6, 10, 1560
    items = sheet_items(textures())
    if prefix:
        items = [i for i in items if any(i[0].startswith(p) for p in prefix)]
    placed, x, y, rowh = [], GAP, GAP, 0
    for lab, im in items:
        im = _img(im)
        w, h = im.width * S, im.height * S
        if x + w + GAP > MAXW:
            x, y, rowh = GAP, y + rowh + GAP + 14, 0
        placed.append((lab, im, x, y))
        x += w + GAP
        rowh = max(rowh, h)
    H = y + rowh + GAP + 16
    sheet = Image.new("RGBA", (MAXW, H), (36, 38, 48, 255))
    d = ImageDraw.Draw(sheet)
    for lab, im, px, py in placed:
        w, h = im.width * S, im.height * S
        chk = Image.new("RGBA", (w, h))
        cd = ImageDraw.Draw(chk)
        for yy in range(0, h, S):
            for xx in range(0, w, S):
                v = 112 + (12 if ((xx // S + yy // S) % 2) else 0)
                cd.rectangle([xx, yy, xx + S - 1, yy + S - 1], fill=(v, v, v + 6, 255))
        sheet.paste(chk, (px, py))
        big = im.resize((w, h), Image.NEAREST)
        sheet.paste(big, (px, py), big)
        d.text((px, py + h + 2), lab[:28], fill=(226, 228, 236, 255))
    sheet.save(out)
    print(out, len(items), "items")


# ----- the facade mock: 7 wide x 13 tall elevations built from the real textures and the models' UV regions ------------

_VCACHE = {}


def vtex(name, rot=0):
    from PIL import Image
    key = (name, rot)
    if key not in _VCACHE:
        im = Image.open(os.path.join(VANILLA, "textures/block", name + ".png")).convert("RGBA").crop((0, 0, 16, 16))
        if rot:
            im = im.rotate(rot)
        _VCACHE[key] = im
    return _VCACHE[key]


class Elev:
    """A 7x13 facade at 1 texel per pixel; (cx, row) cells counted from the bottom-left. `region` mimics a model face:
    texture region (u0,v0,u1,v1) -> element rectangle (x0,y0,x1,y1) in block pixels of a cell, y up."""

    W, H = 7, 13

    def __init__(self, sky=(150, 190, 230)):
        from PIL import Image
        self.im = Image.new("RGBA", (self.W * 16, self.H * 16), sky + (255,))
        self.t = textures()

    def cell(self, cx, row, tex, rot=0):
        if isinstance(tex, str):
            im = self.t[tex] if tex in self.t else vtex(tex, rot)
        else:
            im = tex
        self.im.alpha_composite(_img(im), (cx * 16, (self.H - 1 - row) * 16))

    def region(self, cx, row, tex, uv, rect, flipx=False):
        from PIL import Image
        src = _img(self.t[tex]) if isinstance(tex, str) and tex in self.t else (vtex(tex) if isinstance(tex, str) else _img(tex))
        u0, v0, u1, v1 = uv
        x0, y0, x1, y1 = rect
        w, h = int(round(x1 - x0)), int(round(y1 - y0))
        if w <= 0 or h <= 0:
            return
        out = Image.new("RGBA", (w, h))
        for j in range(h):
            for i in range(w):
                fu = u0 + (u1 - u0) * (i + 0.5) / w
                fv = v0 + (v1 - v0) * (j + 0.5) / h
                px = src.getpixel((min(15, max(0, int(fu))), min(15, max(0, int(fv)))))
                out.putpixel((i, j), px)
        ox = cx * 16 + int(round(x0))
        oy = (self.H - 1 - row) * 16 + (16 - int(round(y1)))
        self.im.alpha_composite(out, (ox, oy))

    def wall(self, tex, cells, rot=0):
        for (cx, row) in cells:
            self.cell(cx, row, tex, rot)

    def rows(self, tex, rows, xs=range(7), rot=0):
        for r in rows:
            for x in xs:
                self.cell(x, r, tex, rot)

    def window(self, cx, row, style, lit=False):
        if style in STYLES_SINGLE:
            self.cell(cx, row, self._win(style + "_single", lit))
            return
        self.cell(cx, row, self._win(style + "_lower", lit))
        self.cell(cx, row + 1, self._win(style + "_upper", lit))

    def _win(self, n, lit):
        base = _img(self.t["window_" + n]).copy()
        if lit:
            base.alpha_composite(_img(self.t["window_" + n + "_glow"]))
        return base

    def shutters(self, cx, row, colour):
        """Half-width panels hugging the window: the cell on the screen-left holds a mirrored panel on its right half
        (hinge on the window side), the cell on the screen-right the panel as drawn on its left half."""
        for dx in (-1, 1):
            for half, r in (("lower", row), ("upper", row + 1)):
                t = f"shutter_{colour}_{half}"
                if dx == -1:
                    self.region(cx + dx, r, t, (8, 0, 0, 16), (8, 0, 16, 16))
                else:
                    self.region(cx + dx, r, t, (0, 0, 8, 16), (0, 0, 8, 16))

    def box(self, cx, row, style, bloom):
        if style == "iron":
            self.region(cx, row, "flower_box_soil", (2, 2, 14, 7), (2, 0, 14, 5))
        if bloom != "none":
            self.region(cx, row, f"bloom_{bloom}", (0, 6, 16, 16), (1, 5, 15, 15))
        self.region(cx, row, f"flower_box_{style}", (1, 9, 15, 16), (1, 0, 15, 7))
        if row >= 1:   # the brackets hang below the cell: draw their fronts in the cell under it
            for el in sorted(flower_box_brackets(style), key=lambda e: -e["from"][2]):
                x0, y0, _ = el["from"]
                x1, y1, _ = el["to"]
                if y0 >= 0:
                    continue
                self.region(cx, row - 1, f"flower_box_{style}", tuple(el["faces"]["north"]["uv"]), (x0, 16 + y0, x1, 16 + min(y1, 0)))

    def drainpipe(self, cx, row0, row1):
        for r in range(row0, row1 + 1):
            kind = "shoe" if r == row0 else "head" if r == row1 else "pipe"
            if kind in ("pipe", "shoe"):
                top = 16 if kind == "pipe" else 16
                self.region(cx, r, "drainpipe", (6, 0, 10, 16) if kind == "pipe" else (6, 0, 10, 12), (6, 0 if kind == "pipe" else 4, 10, 16))
                if kind == "shoe":
                    self.region(cx, r, "drainpipe", (6, 12, 10, 16), (6, 0, 10, 4))
                self.region(cx, r, "drainpipe", (10, 6, 16, 7), (5, 12, 11, 13))
            else:
                self.region(cx, r, "drainpipe", (6, 10, 10, 16), (6, 0, 10, 6))
                self.region(cx, r, "drainpipe_head", (0, 0, 8, 7), (4, 6, 12, 13))
                self.region(cx, r, "drainpipe_head", (0, 7, 10, 10), (3, 13, 13, 16))

    def trellis(self, cx, row0, row1, ivy=True):
        for r in range(row0, row1 + 1):
            self.region(cx, r, "trellis", (0, 0, 16, 16), (0, 0, 16, 16))
            if ivy:
                self.region(cx, r, "trellis_ivy", (0, 0, 16, 16), (0, 0, 16, 16))

    def door(self, cx, row):
        for dx, side in ((0, "left"), (1, "right")):
            self.cell(cx + dx, row, self.t[f"loading_door_lower_{side}"])
            self.cell(cx + dx, row + 1, self.t[f"loading_door_upper_{side}"])

    def rope(self, cx, row0, row1):
        for r in range(row0, row1 + 1):
            self.region(cx, r, "rope", (7, 0, 9, 16), (7, 0, 9, 16))

    def hoist(self, cx, row):
        self.region(cx, row, "hoist_pulley", (0, 0, 2, 4), (7, 1, 9, 8))
        self.region(cx, row, "stripped_dark_oak_log_top", (5, 5, 11, 11), (5, 8, 11, 14))


def _pick(rng, names, weights=None):
    return rng.choices(names, weights)[0] if weights else rng.choice(names)


def elevation(district):
    e = Elev()
    rng = rng_for("city_kit", "mock", district)
    xs = range(7)
    if district == "bellwatch":
        for r in range(4):
            for x in xs:
                n = _pick(rng, ["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"], [70, 16, 14])
                e.cell(x, r, "polished_andesite" if x in (0, 6) and r % 2 == 0 else n)
        e.cell(3, 0, "spruce_door_bottom")
        e.cell(3, 1, "spruce_door_top")
        e.rows("stripped_dark_oak_log", [4, 8], rot=90)
        for r0 in (5, 9):
            for r in (r0, r0 + 1, r0 + 2):
                for x in xs:
                    e.cell(x, r, "stripped_dark_oak_log" if x in (0, 6) else "calcite")
            e.window(3, r0, "leaded")
            e.shutters(3, r0, ["blue", "sage", "oxblood"][(r0 // 4) % 3] if r0 == 5 else "natural")
            e.box(3, r0, "wood", ["red", "yellow"][(r0 // 4) % 2])
        e.rows("slate_tiles", [12])
        e.drainpipe(6, 0, 11)
    elif district == "market":
        for r in range(4):
            for x in xs:
                e.cell(x, r, "cut_sandstone")
        e.rows("stone_bricks", [0])
        for x, st in ((1, "shop"), (2, "shop"), (4, "shop"), (5, "shop")):
            e.window(x, 1, st, lit=(x == 4))
        e.cell(3, 1, "dark_oak_door_bottom")
        e.cell(3, 2, "dark_oak_door_top")
        e.rows("exposed_cut_copper", [4, 8])
        for r0 in (5, 9):
            for r in (r0, r0 + 1, r0 + 2):
                for x in xs:
                    e.cell(x, r, "white_terracotta")
            for x, bl in ((1, "red"), (3, "yellow"), (5, "pink")):
                e.window(x, r0, "sash", lit=(r0 == 9 and x == 3))
                e.box(x, r0, "iron", bl if (x + r0) % 2 == 0 else "white")
            if r0 == 5:
                for r in (7,):
                    pass
        e.rows("clay_tiles", [12])
    elif district == "quay":
        for r in range(4):
            for x in xs:
                e.cell(x, r, _pick(rng, ["stone_bricks", "mossy_stone_bricks", "stone_bricks"]))
        for x in (1, 5):
            e.window(x, 1, "barred")
        e.cell(3, 0, "dark_oak_door_bottom")
        e.cell(3, 1, "dark_oak_door_top")
        e.rows("stripped_dark_oak_log", [4, 8], rot=90)
        for r0 in (5, 9):
            for r in (r0, r0 + 1, r0 + 2):
                for x in xs:
                    e.cell(x, r, _pick(rng, ["bricks", "bricks", "bricks", "mud_bricks"], [8, 8, 8, 1]))
            e.window(1, r0, "warehouse", lit=(r0 == 9))
            e.window(6, r0, "warehouse")
            e.door(3, r0)
        e.rows("spruce_planks", [12])
        e.hoist(3, 11)
        e.rope(3, 8, 10)
    elif district == "terrace":
        for r in range(0, 12):
            for x in xs:
                e.cell(x, r, "calcite")
        e.rows("polished_andesite", [0])
        for x in (0, 2, 4, 6):
            for r in range(1, 12):
                e.cell(x, r, "quartz_block_side")
        e.rows("quartz_block_top", [4, 8])
        for r0 in (1, 5, 9):
            for x, bl in ((1, "white"), (3, "pink"), (5, "white")):
                e.window(x, r0, "arched", lit=(r0 == 5 and x == 3))
                if r0 > 1:
                    e.box(x, r0, "stone", bl)
        e.rows("polished_diorite", [12])
    elif district == "garden":
        for r in range(0, 12):
            for x in xs:
                e.cell(x, r, "calcite")
        e.rows("stripped_birch_log", [4, 8], rot=90)
        e.cell(5, 0, "spruce_door_bottom")
        e.cell(5, 1, "spruce_door_top")
        for r0 in (1, 5, 9):
            e.window(3, r0, "cottage", lit=(r0 == 5))
            e.shutters(3, r0, "green")
            if r0 > 1:
                e.box(3, r0, "wood", "blue" if r0 == 5 else "pink")
        e.trellis(1, 0, 10)
        e.rows("oxidized_cut_copper", [12])
    else:  # works
        for r in range(0, 12):
            for x in xs:
                e.cell(x, r, "bricks")
        e.rows("polished_blackstone_bricks", [4, 8])
        for r0 in (1, 5, 9):
            for x in (1, 3, 5):
                e.window(x, r0, "works", lit=(r0 == 5 and x == 3))
        e.cell(6, 0, "dark_oak_door_bottom")
        e.cell(6, 1, "dark_oak_door_top")
        e.rows("deepslate_tiles", [12])
        e.drainpipe(0, 0, 11)
    return e.im


def mock(out):
    from PIL import Image, ImageDraw
    S = 4
    names = ["bellwatch", "market", "quay", "terrace", "garden", "works"]
    W, H = 7 * 16 * S, 13 * 16 * S
    pad, label = 12, 16
    sheet = Image.new("RGBA", (3 * (W + pad) + pad, 2 * (H + pad + label) + pad), (30, 32, 40, 255))
    d = ImageDraw.Draw(sheet)
    for i, n in enumerate(names):
        im = elevation(n).resize((W, H), Image.NEAREST)
        x, y = pad + (i % 3) * (W + pad), pad + (i // 3) * (H + pad + label)
        sheet.paste(im, (x, y))
        d.text((x, y + H + 2), n, fill=(230, 232, 240, 255))
        elevation(n).resize((7 * 16 * 8, 13 * 16 * 8), Image.NEAREST).save(os.path.splitext(out)[0] + f"-{n}-8x.png")
    sheet.save(out)
    print(out)


# ----- mock 2 and sheet 2: the walls' kit, with variants picked by their blockstate weights from a seeded random -----------


def pick(rng, entries):
    total = sum(e["weight"] for e in entries)
    r = rng.random() * total
    for e in entries:
        r -= e["weight"]
        if r <= 0:
            return e
    return entries[-1]


def variant_image(t, model):
    """The north face a placed block of this model shows: (texture image, rotated-for-horizontal handled by the caller)."""
    from PIL import Image
    mirrored = model.endswith("_mirrored")
    base = model[:-len("_mirrored")] if mirrored else model
    m = re.match(r"timber_(dark|pale)_(\d)(\d)(_horizontal)?$", base)
    if m:
        im = _img(t[f"timber_{m.group(1)}_side_{m.group(2)}"])
        if m.group(4):
            im = im.rotate(90)           # a horizontal log: the grain runs along the beam
        return im
    im = _img(t[base])
    return im.transpose(Image.FLIP_LEFT_RIGHT) if mirrored else im


def wall_entries(kind):
    if kind.startswith("plaster_"):
        return plaster_entries(kind[8:])
    if kind.startswith("timber_"):
        _, tone, axis = kind.split("_")
        return timber_entries(tone, horizontal=axis == "x")
    return {"city_bricks": bricks_entries, "ashlar": ashlar_entries, "slate": lambda: roof_cube_entries("slate"),
            "clay": lambda: roof_cube_entries("clay")}[kind]()


def wall_image(t, kind, rng, w=12, h=8):
    from PIL import Image
    ents = wall_entries(kind)
    im = Image.new("RGBA", (w * 16, h * 16))
    for y in range(h):
        for x in range(w):
            e = pick(rng, ents)
            im.paste(variant_image(t, e["model"]), (x * 16, y * 16))
    return im


class Elev2(Elev):
    """Elev with weighted-random wall cells."""

    def vcell(self, cx, row, kind, rng):
        e = pick(rng, wall_entries(kind))
        self.im.alpha_composite(variant_image(self.t, e["model"]), (cx * 16, (self.H - 1 - row) * 16))

    def vrows(self, kind, rows, rng, xs=range(7)):
        for r in rows:
            for x in xs:
                self.vcell(x, r, kind, rng)


def facade_bellwatch(t):
    """A timber frame: dark timber posts every 3, plaster_white panels, leaded windows with half-width shutters and boxes, slate eave."""
    e = Elev2()
    rng = rng_for("city_kit", "mock2", "bellwatch")
    e.vrows("ashlar", range(0, 4), rng)
    e.cell(3, 0, "spruce_door_bottom")
    e.cell(3, 1, "spruce_door_top")
    for r in (4, 8):
        e.vrows("timber_dark_x", [r], rng)
    for r0 in (5, 9):
        for r in (r0, r0 + 1, r0 + 2):
            for x in range(7):
                e.vcell(x, r, "timber_dark_y" if x in (0, 3, 6) else "plaster_white", rng)
        for wx in (1, 5):
            e.window(wx, r0, "leaded", lit=(r0 == 9 and wx == 5))
            e.shutters(wx, r0, "blue" if r0 == 5 else "sage")
            e.box(wx, r0, "wood", "red" if wx == 1 else "yellow")
    e.rows("slate_tiles", [12])
    return e.im


def facade_market(t):
    """Plaster_rose fronts over an ashlar ground storey with shop windows; sash windows, iron balconettes, a clay roof."""
    e = Elev2()
    rng = rng_for("city_kit", "mock2", "market")
    e.vrows("ashlar", range(0, 4), rng)
    for x in (1, 2, 4, 5):
        e.window(x, 1, "shop", lit=(x == 4))
    e.cell(3, 1, "dark_oak_door_bottom")
    e.cell(3, 2, "dark_oak_door_top")
    e.cell(3, 0, "dark_oak_door_bottom") if False else None
    e.rows("exposed_cut_copper", [4, 8])
    for r0 in (5, 9):
        for r in (r0, r0 + 1, r0 + 2):
            e.vrows("plaster_rose", [r], rng)
        for x, bl in ((1, "red"), (3, "yellow"), (5, "pink")):
            e.window(x, r0, "sash", lit=(r0 == 9 and x == 3))
            e.box(x, r0, "iron", bl)
    e.rows("clay_tiles", [12])
    return e.im


def sheet_rows_2(t):
    rows = []
    for colour in PLASTER_ORDER:
        rows.append((f"plaster_{colour}  (clean 6, crack 2, worn 1, streak 1)", [(f"_{v}", t[f"plaster_{colour}_{v}"]) for v in range(4)]))
    for tone in ("dark", "pale"):
        rows.append((f"timber_{tone}  side 0 grain (5), 1 knot (2), 2 split (1), 3 pegs (2); end 0, 1",
                     [(f"s{v}", t[f"timber_{tone}_side_{v}"]) for v in range(4)] + [(f"e{v}", t[f"timber_{tone}_end_{v}"]) for v in range(2)]))
    rows.append(("city_bricks  plain 5, dark headers 2, pale 2, soot 1, repair 1", [(f"_{v}", t[f"city_bricks_{v}"]) for v in range(5)]))
    rows.append(("ashlar  plain 5, warm 2, crack, moss, lichen, chip", [(f"_{v}", t[f"ashlar_{v}"]) for v in range(6)]))
    rows.append(("slate_tiles  0 (5), 1 new (2), 2 lichen (1), 3 slipped (1)", [("_0", t["slate_tiles"])] + [(f"_{v}", t[f"slate_tiles_{v}"]) for v in (1, 2, 3)]))
    rows.append(("clay_tiles  0 (5), 1 new (2), 2 weathered (1), 3 cracked (1)", [("_0", t["clay_tiles"])] + [(f"_{v}", t[f"clay_tiles_{v}"]) for v in (1, 2, 3)]))
    return rows


def make_sheet_2(out):
    from PIL import Image, ImageDraw
    S, GAP = 6, 8
    t = textures()
    rows = sheet_rows_2(t)
    W = 1500
    sheet = Image.new("RGBA", (W, len(rows) * (16 * S + 34) + 10), (36, 38, 48, 255))
    d = ImageDraw.Draw(sheet)
    y = 8
    for title, items in rows:
        d.text((8, y), title, fill=(226, 228, 236, 255))
        x = 8
        for lab, im in items:
            big = _img(im).resize((16 * S, 16 * S), Image.NEAREST)
            sheet.paste(big, (x, y + 14), big)
            d.text((x + 2, y + 16 * S + 15), lab, fill=(200, 204, 214, 255))
            x += 16 * S + GAP
        y += 16 * S + 34
    sheet.save(out)
    print(out, sum(len(r[1]) for r in rows), "textures")


def mock2(out):
    from PIL import Image, ImageDraw
    t = textures()
    walls = [(f"plaster_{c}", f"plaster_{c}") for c in PLASTER_ORDER] + [
        ("timber dark, upright", "timber_dark_y"), ("timber dark, horizontal", "timber_dark_x"),
        ("timber pale, upright", "timber_pale_y"), ("timber pale, horizontal", "timber_pale_x"),
        ("city_bricks", "city_bricks"), ("ashlar", "ashlar"), ("slate_tiles", "slate"), ("clay_tiles", "clay")]
    S, pad, label = 2, 8, 16
    ww, wh = 12 * 16 * S, 8 * 16 * S
    cols = 4
    rows = (len(walls) + cols - 1) // cols
    FW, FH, FS = 7 * 16 * 3, 13 * 16 * 3, 3
    H = rows * (wh + label + pad) + pad + FH + label + 2 * pad
    sheet = Image.new("RGBA", (cols * (ww + pad) + pad, H), (30, 32, 40, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, kind) in enumerate(walls):
        rng = rng_for("city_kit", "mock2", "wall", kind)
        im = wall_image(t, kind, rng).resize((ww, wh), Image.NEAREST)
        x, y = pad + (i % cols) * (ww + pad), pad + (i // cols) * (wh + label + pad)
        sheet.paste(im, (x, y))
        d.text((x, y + wh + 2), name + "  (12 x 8)", fill=(230, 232, 240, 255))
        wall_image(t, kind, rng_for("city_kit", "mock2", "wall", kind)).resize((12 * 16 * 4, 8 * 16 * 4), Image.NEAREST).save(
            os.path.splitext(out)[0] + f"-wall-{kind}-4x.png")
    y = pad + rows * (wh + label + pad)
    for j, (name, fn) in enumerate((("Bellwatch timber frame: timber_dark posts every 3, plaster_white, ashlar", facade_bellwatch),
                                    ("Market front: plaster_rose over an ashlar ground storey", facade_market))):
        im = fn(t).resize((FW, FH), Image.NEAREST)
        x = pad + j * (FW + 2 * pad)
        sheet.paste(im, (x, y))
        d.text((x, y + FH + 2), name, fill=(230, 232, 240, 255))
        fn(t).resize((7 * 16 * 8, 13 * 16 * 8), Image.NEAREST).save(os.path.splitext(out)[0] + f"-facade{j}-8x.png")
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "content":
        content()
    elif cmd == "check":
        sys.exit(check())
    elif cmd == "manifest":
        manifest()
    elif cmd == "sheet":
        make_sheet(sys.argv[2], sys.argv[3:])
    elif cmd == "sheet2":
        make_sheet_2(sys.argv[2])
    elif cmd == "mock":
        mock(sys.argv[2] if len(sys.argv) > 2 else "kit-mock.png")
    elif cmd == "mock2":
        mock2(sys.argv[2] if len(sys.argv) > 2 else "kit-mock-2.png")
    else:
        print(__doc__)
