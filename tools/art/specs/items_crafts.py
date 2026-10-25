"""The Living Wilds' craft items: six foraged herbs and the nine meals cooked from them, 16x16 icons.

    python3 tools/art/build_all.py items_crafts          write src/main/resources/assets/awaken/textures/item/<id>.png
    python3 tools/art/specs/items_crafts.py preview DIR  contact sheet (x14, on mid grey and on night, labelled) + the checks

    sunleaf         a sprig of broad golden-green leaves on a bent stem, a warm sunlit edge on every leaf
    frostcap        a small pale-blue mushroom-like herb: domed cap crusted with ice crystals, a pale stalk
    emberroot       a gnarled red-brown root forking to the lower left, glowing orange in its cracks and flecked with embers
    moonmoss        a low clump of silver-violet moss, fuzzy at the edges, a few pale glints in it
    duskbloom       a dark purple bud on a stem with two leaves: closed petals, a deep magenta glow in the seams and tip
    spiritgrass     a tuft of thin cyan-white blades, bright at the tips, a dithered glow round them and a teal outline

    herb_broth      bowl of clear green-gold broth with leaf bits and a sprig
    frostcap_tea    bowl of pale blue tea, a frostcap slice floating and a frost sparkle over it
    ember_skewer    a stick skewer of seared meat chunks, grill-marked, glazed orange and flecked with embers (no bowl)
    moonmoss_soup   bowl of dark mushroom soup, a heap of silver-violet moss on top
    hunters_stew    bowl of thick brown stew heaped with meat, carrot and potato chunks
    foragers_salad  bowl of mixed greens piled high, red berries and a purple petal
    miners_pasty    a golden crimped half-moon pastry, egg-washed, two steam slits (no bowl)
    anglers_chowder bowl of creamy white chowder, a salmon chunk, potato cubes, a fish tail and a chive fleck
    spirit_tea      bowl of glowing pale cyan tea with a honey swirl and a few motes rising

The bowls share one wooden bowl shape (vanilla's, a little wider) and differ in what is in them. Everything is drawn by hand
from the shared palette (pixelart.RAMPS), items_living's local ramps and a few more of our own, one pixel outline, light from
the top left. Nothing is random: the same ids always give the same pixels.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)
from pixelart import *  # noqa: E402,F401,F403
from pixelart import _outline  # noqa: E402
import items_living as IL  # noqa: E402
from items_living import R, line  # noqa: E402

CRAFT = {
    "bowl":    ["#241707", "#3e290b", "#5c3d12", "#7e5718", "#a3772a", "#c79a48"],
    "sunleaf": ["#34440f", "#587a1c", "#8aae2a", "#bcd03c", "#e6dc50", "#fff08a"],
    "sunedge": ["#a8601a", "#d88a22", "#f8b830", "#ffd860"],
    "cap":     ["#27425c", "#3f6a8c", "#6b9cc0", "#9cc8e2", "#cfe8f6", "#f4fcff"],
    "ice":     ["#2c3470", "#46589c", "#86e4f4", "#bff4ff"],         # periwinkle in the shade, cyan in the crystals
    "root":    ["#1e0708", "#3e1010", "#6a1c0e", "#98300e", "#c24a14", "#e87a24"],
    "moss":    ["#221b36", "#3a3256", "#5a5286", "#8680b2", "#b4aed4", "#e2e0f2"],
    "dusk":    ["#120a22", "#25113f", "#3c1a62", "#552686", "#7340ac", "#9a66d2"],
    "magenta": ["#4a0a34", "#860f56", "#c01a78", "#ee3c9c", "#ff86c6"],
    "spirit":  ["#0a2c3c", "#145468", "#228c98", "#58d0c8", "#a6f0ea", "#eafffc"],
    "spiritb": ["#10304e", "#1e5680", "#3a86b4", "#78bce0", "#c2e6fa"],  # the blades behind, cooler and dimmer
    "broth":   ["#566014", "#808a1e", "#aab42c", "#cfca44", "#eadf68", "#fff6b0"],
    "greens":  ["#143a1c", "#2c6a26", "#4a9a38", "#7cc24c", "#aee070", "#d8f4a0"],
    "tea":     ["#2a5478", "#4a82a8", "#7cb0cc", "#a8d2e6", "#d2eaf6", "#f4fcff"],
    "soup":    ["#16101a", "#261c28", "#3a2c34", "#503e46", "#6c5860", "#8c7a80"],
    "stew":    ["#2a1308", "#4a2610", "#6c3a1a", "#8c5028", "#ae6c3a", "#cc8c58"],
    "meat":    ["#2e0e0a", "#5a1e16", "#86382a", "#ae5640", "#d07a5c", "#ea9c7c"],
    "carrot":  ["#8a3406", "#c85c0e", "#f08a20", "#ffb044", "#ffd080"],
    "potato":  ["#7a5a22", "#a8803a", "#d0a858", "#ecca7a", "#fae6a8"],
    "cream":   ["#9c8c64", "#c8b88c", "#e4d6ae", "#f6ecce", "#fffaea", "#ffffff"],
    "salmon":  ["#9a3a26", "#cc5e40", "#ec8460", "#ffac84", "#ffd0b4"],
    "berry":   ["#4a0814", "#861424", "#bc2234", "#e84860", "#ff9aa6"],
    "petal":   ["#3a1a5a", "#5c2a8c", "#8248b8", "#a870d8", "#cc9af0"],
    "pastry":  ["#4e2c08", "#7e4c10", "#ac6e1c", "#d2942c", "#eeb84a", "#fcdc84"],
    "honey":   ["#7a4c0e", "#b07818", "#dca630", "#f2cc54", "#ffe88a", "#fffbe0"],
    "skewer":  ["#2a1a0a", "#4a3016", "#6e4c26", "#93703c", "#b8965c"],
    "shroom":  ["#3e2a1e", "#6a4c36", "#8e6e50", "#b09070", "#d0b890"],
    "char":    ["#0e0604", "#24100a", "#3c1c10"],
}


def col(name, i):
    """A colour from our own ramps, else from items_living's and the shared palette."""
    rp = CRAFT.get(name)
    if rp is None:
        return IL.col(name, i)
    return hex_rgba(rp[max(0, min(len(rp) - 1, i))])


def h01(x, y, salt=0):
    """Deterministic 0..1 noise for a pixel."""
    n = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def stamp(c, x0, y0, rows, leg):
    """Paint rows of characters ('.' and ' ' are clear) with their top-left at (x0, y0)."""
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch not in ". ":
                c.set(x0 + dx, y0 + dy, leg[ch])


def ellipse(c, cx, cy, rx, ry, paint, angle=0.0):
    """Fill a rotated ellipse; paint(u, v, d) gets the offset along and across it (each -1..1) and the radius (0..1)."""
    ca, sa = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    cover = {}
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            u, v = (dx * ca + dy * sa) / rx, (-dx * sa + dy * ca) / ry
            d = math.hypot(u, v)
            if d <= 1.0:
                cover[(x, y)] = (u, v, d)
                c.set(x, y, paint(u, v, d))
    return cover


def stroke(pts, steps=60):
    """Pixels along a polyline of (x, y, radius) points: {(x, y): (rel, t, off)}, with off the signed distance across it
    (positive on the lit upper-left side) over the radius there."""
    out = {}
    segs = list(zip(pts, pts[1:]))
    total = len(segs) * steps
    for si, ((x0, y0, r0), (x1, y1, r1)) in enumerate(segs):
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1.0
        nx, ny = dy / ln, -dx / ln
        if nx * -0.7 + ny * -0.7 < 0:
            nx, ny = -nx, -ny
        for s in range(steps + 1):
            t = s / steps
            px, py, r = x0 + dx * t, y0 + dy * t, r0 + (r1 - r0) * t
            for y in range(16):
                for x in range(16):
                    qx, qy = x + 0.5 - px, y + 0.5 - py
                    d = math.hypot(qx, qy)
                    if d <= r and (x, y) not in out or (d <= r and d / r < out[(x, y)][0]):
                        out[(x, y)] = (d / r, (si * steps + s) / total, (qx * nx + qy * ny) / r)
    return out


# ---------------------------------------------------------------------------------------------------------------------
# the herbs

def sunleaf() -> Canvas:
    """Three broad leaves and a small one on a stem that bends right: each leaf is lit gold on its upper edge, a pale midrib."""
    c = Canvas()
    sl = [col("sunleaf", i) for i in range(6)]
    se = [col("sunedge", i) for i in range(4)]
    # the stem: a slim bend from the base up to the top leaf
    for (x, y, i) in ((6, 14, 1), (6, 13, 2), (7, 12, 2), (7, 11, 1), (8, 10, 2), (8, 9, 2), (8, 8, 1)):
        c.set(x, y, sl[i])

    def leaf(cx, cy, rx, ry, angle):
        def paint(u, v, d):
            lit = -v * 0.75 - u * 0.25               # v < 0 is the upper side of the leaf after rotation
            i = 4 if lit > 0.35 else 3 if lit > -0.25 else 2 if lit > -0.7 else 1
            if abs(v) < 0.2 and u > -0.75:
                i = min(5, i + 1)                    # the midrib
            if d > 0.78 and lit > 0.15:
                return se[2] if lit > 0.45 else se[1]   # a warm sunlit edge
            if d > 0.82:
                i -= 1
            return sl[max(0, i)]
        return ellipse(c, cx, cy, rx, ry, paint, angle)

    leaf(5.0, 9.4, 3.9, 2.0, -35)       # lower left, pointing up-left
    leaf(11.2, 11.0, 3.8, 1.9, 22)      # lower right, drooping a little
    leaf(5.6, 4.6, 3.7, 1.9, -62)       # upper left
    leaf(10.6, 5.2, 4.4, 2.3, -48)      # the top leaf, the broadest
    for (x, y) in ((8, 8), (8, 9)):
        c.set(x, y, sl[2])
    c.set(9, 3, se[3]); c.set(4, 6, se[3])          # two points that catch the sun
    return _outline(c)


def frostcap() -> Canvas:
    """A little mushroom of ice: a pale blue dome flecked with white, ice crystals standing up from its crown, a ragged gill
    edge in periwinkle shade, a stalk that swells at the foot."""
    c = Canvas()
    cp = [col("cap", i) for i in range(6)]
    ic = [col("ice", i) for i in range(4)]

    def cap(u, v, d):
        lit = -u * 0.55 - v * 0.8
        return cp[5 if lit > 0.82 else 4 if lit > 0.36 else 3 if lit > -0.12 else 2]
    cover = ellipse(c, 8.0, 9.6, 6.5, 5.5, cap)
    for (x, y) in cover:
        if y >= 10:                                     # only the dome: the stalk is drawn below it
            c.set(x, y, (0, 0, 0, 0))
    for x in range(1, 15):
        if c.get(x, 9)[3]:
            c.set(x, 9, cp[2] if x < 8 else ic[1])      # the cap's lower edge, in shade
    for (x, y) in ((12, 8), (13, 8), (11, 9)):
        if c.get(x, y)[3]:
            c.set(x, y, ic[1])
    # gills under the rim: a ragged row of dark and shade
    for k, x in enumerate(range(4, 12)):
        c.set(x, 10, ic[0] if k % 2 else ic[1])
    # the stalk: lit left, shaded right, swelling to a foot
    for y, (a, b) in {11: (6, 9), 12: (6, 9), 13: (5, 10)}.items():
        for x in range(a, b + 1):
            c.set(x, y, cp[4 if x == a else 3 if x < b - 1 else 2] if x < b else ic[1])
    c.set(5, 13, cp[3])
    # frost: white flecks in the cap, a few cyan glints in it
    for (x, y) in ((5, 7), (8, 6), (10, 8), (6, 8), (12, 8), (4, 8), (7, 5)):
        c.set(x, y, cp[5])
    for (x, y) in ((6, 7), (9, 6), (11, 7), (5, 6), (9, 8)):
        c.set(x, y, cp[3])
    for (x, y) in ((11, 6), (3, 8), (7, 8)):
        c.set(x, y, cp[2])
    for (x, y) in ((8, 7), (4, 7)):
        c.set(x, y, ic[2])
    # ice crystals standing from the crown: a tall one with a branch, a leaning shard, a small one on the right
    for (x, y, i) in ((9, 1, 5), (9, 2, 5), (9, 3, 4), (9, 4, 3), (10, 3, 4), (8, 3, 3),     # the big one
                      (5, 3, 5), (5, 4, 4), (4, 5, 3), (5, 5, 3),                         # leaning left
                      (12, 4, 5), (12, 5, 4), (13, 6, 3)):                                # right
        c.set(x, y, cp[i])
    for (x, y) in ((9, 2), (5, 4), (12, 5)):
        c.set(x, y, ic[3])
    for (x, y) in ((9, 4), (5, 5)):
        c.set(x, y, ic[2])
    return _outline(c)


def emberroot() -> Canvas:
    """A gnarled root, thick at the upper right, forking and thinning to the lower left, glowing in its cracks."""
    c = Canvas()
    rt = [col("root", i) for i in range(6)]
    em = [col("ember", i) for i in range(6)]
    trunk = [(11.8, 3.2, 2.1), (9.8, 5.6, 2.6), (7.8, 8.2, 2.6), (6.2, 10.8, 1.9), (5.0, 12.8, 1.1), (3.6, 14.6, 0.6)]
    forks = [[(9.2, 5.2, 1.3), (6.4, 3.8, 1.0), (4.0, 3.6, 0.6)],            # up to the left
             [(9.0, 8.8, 1.3), (11.8, 10.8, 1.1), (13.8, 12.8, 0.6)],       # out to the right
             [(6.0, 10.6, 1.1), (3.4, 10.4, 0.8), (1.6, 11.6, 0.5)]]       # down to the left
    cover = {}
    for pts in [trunk] + forks:
        for k, v in stroke(pts, 14).items():
            if k not in cover or v[0] < cover[k][0]:
                cover[k] = v
    for (x, y), (rel, t, off) in cover.items():
        i = 4 if off > 0.45 else 3 if off > -0.1 else 2 if off > -0.6 else 1
        if rel > 0.8:
            i -= 1
        if h01(x, y, 3) < 0.22:
            i += 1 if h01(x, y, 4) < 0.5 else -1          # bark: gnarled and uneven
        c.set(x, y, rt[max(0, min(4, i))])
    # knots in the bark: a dark ring and a pale scar on the trunk
    for (x, y) in ((8, 6), (7, 10)):
        c.set(x, y, rt[0])
    # the glow in the cracks: orange along the root's belly, knots of yellow
    for (x, y) in ((11, 4), (10, 6), (9, 7), (8, 9), (7, 11), (6, 12), (5, 4), (12, 10), (13, 12), (4, 10), (9, 5)):
        if c.get(x, y)[3]:
            c.set(x, y, em[3] if (x + y) % 2 else rt[5])
    for (x, y) in ((10, 5), (8, 8), (7, 10), (12, 3)):
        if c.get(x, y)[3]:
            c.set(x, y, em[5])
    for (x, y) in ((11, 5), (9, 9), (6, 11)):
        if c.get(x, y)[3]:
            c.set(x, y, em[4])
    # the cut end of the trunk, pale: it was dug, not fallen
    for (x, y, h) in ((13, 1, "#e8b890"), (12, 1, "#c88a62"), (13, 2, "#c88a62"), (14, 2, "#a06a48")):
        c.set(x, y, hex_rgba(h))
    out = _outline(c)
    # loose embers rise off it
    for (x, y, i) in ((14, 5, 4), (1, 7, 3), (10, 1, 5), (15, 8, 3)):
        if out.get(x, y)[3] == 0:
            out.set(x, y, em[i])
    return out


def moonmoss() -> Canvas:
    """A low clump built from a row of small domes, silver on their lit tops and violet in the cracks between, tufts standing
    off the edge, a few pale glints in it and one beside it."""
    c = Canvas()
    ms = [col("moss", i) for i in range(6)]
    domes = [(8.0, 11.8, 6.8, 2.9), (4.2, 9.6, 2.3, 2.1), (7.0, 8.2, 2.5, 2.2), (10.0, 8.9, 2.4, 2.0), (12.2, 10.2, 2.1, 1.8),
             (5.8, 10.8, 2.6, 2.0), (9.0, 10.6, 2.6, 2.0)]
    cover = {}
    for (cx, cy, rx, ry) in domes:
        for y in range(16):
            for x in range(16):
                u, v = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
                d = u * u + v * v
                if d <= 1.0 and y <= 14:
                    lit = -u * 0.5 - v * 0.9 - d * 0.35 + (cy - 9.5) * -0.12
                    if (x, y) not in cover or lit > cover[(x, y)]:
                        cover[(x, y)] = lit
    for (x, y), lit in cover.items():
        i = 4 if lit > 0.5 else 3 if lit > 0.0 else 2 if lit > -0.5 else 1
        n = h01(x, y, 7)
        if n < 0.3:
            i += 1                                        # fuzz: tufts of the lighter tone
        elif n > 0.78:
            i -= 1
        if y >= 14:
            i = 1
        c.set(x, y, ms[max(0, min(5, i))])
    for (x, y) in ((5, 8), (9, 7), (12, 9), (7, 11), (3, 10), (10, 10)):      # glints: pale and tiny
        if c.get(x, y)[3]:
            c.set(x, y, ms[5])
    # tufts standing up round the top, a pixel clear of the clump
    for (x, y, i) in ((5, 6, 3), (7, 5, 4), (8, 6, 3), (10, 6, 3), (3, 8, 2), (13, 8, 3), (2, 10, 2), (14, 11, 2), (11, 7, 3)):
        if c.get(x, y)[3] == 0 and c.get(x, y + 1)[3]:
            c.set(x, y, ms[i])
    out = _outline(c)
    for (x, y, h) in ((1, 5, "#c8e0ff"), (13, 4, "#e2e0f2"), (11, 2, "#a8c8ff")):   # glints that have drifted off
        if out.get(x, y)[3] == 0:
            out.set(x, y, hex_rgba(h))
    return out


def duskbloom() -> Canvas:
    """A closed bud: three dark purple petals folded together, the magenta heart glowing through the tip and the seams."""
    c = Canvas()
    du = [col("dusk", i) for i in range(6)]
    mg = [col("magenta", i) for i in range(5)]
    gl = [col("leaf", i) for i in range(6)]
    # stem and two leaves
    for (x, y, i) in ((8, 14, 2), (8, 13, 3), (8, 12, 2), (7, 11, 3), (7, 10, 2)):
        c.set(x, y, gl[i])
    for (x, y, i) in ((9, 12, 3), (10, 12, 4), (11, 11, 3), (12, 11, 4), (12, 10, 3), (10, 11, 2),
                      (6, 13, 3), (5, 13, 4), (4, 12, 3), (3, 12, 4), (3, 11, 3), (5, 12, 2)):
        c.set(x, y, gl[i])
    # the petals: two behind, one in front, each an ellipse leaning a little
    def back(side):
        def paint(u, v, d):
            lit = -u * 0.6 - v * 0.6
            return du[3 if lit > 0.3 else 2 if lit > -0.4 else 1]
        return paint

    def front(u, v, d):
        lit = -u * 0.65 - v * 0.5
        i = 5 if lit > 0.62 else 4 if lit > 0.28 else 3 if lit > -0.2 else 2 if lit > -0.6 else 1
        return du[i]
    ellipse(c, 5.8, 6.6, 2.6, 4.7, back(-1), angle=-14)
    ellipse(c, 10.0, 6.6, 2.6, 4.7, back(1), angle=14)
    ellipse(c, 8.0, 7.2, 2.7, 4.9, front)
    # the seams between the petals: dark, with magenta light leaking through them
    for (x, y) in ((6, 4), (6, 5), (5, 6), (5, 7), (5, 8), (10, 4), (10, 5), (10, 6), (11, 7), (11, 8)):
        if c.get(x, y)[3]:
            c.set(x, y, du[0] if (x + y) % 2 else du[1])
    for (x, y, i) in ((7, 2, 2), (8, 2, 3), (8, 3, 2), (6, 3, 1), (9, 3, 1)):
        c.set(x, y, mg[i])                                  # the tip: not quite shut
    for (x, y, i) in ((5, 7, 1), (10, 5, 1), (11, 8, 1)):
        c.set(x, y, mg[i])
    c.set(8, 4, mg[3]); c.set(8, 5, mg[2])                     # the heart shows through a split in the front petal
    # the faintest bloom of glow on the purple round the heart
    for (x, y) in ((7, 5), (9, 5), (8, 6)):
        c.blend(x, y, mg[2], 0.35)
    return _outline(c)


def spiritgrass() -> Canvas:
    """A tuft of thin curved blades fanning from a pale root clutch: white at the tips, cyan toward the base, the shaded side
    of each in teal, a deep teal outline instead of black, a few motes of glow loose beside it (the textures here are opaque,
    so isolated dither pixels stand in for translucency)."""
    c = Canvas()
    sp = [col("spirit", i) for i in range(6)]
    blades = [   # base x, tip x, tip y, bend (px the middle bows sideways), shaded side
        (7.0, 0.8, 5.0, -2.2, True), (7.4, 3.6, 1.0, -1.0, True), (8.0, 8.0, 0.0, 1.4, True), (8.6, 12.2, 1.6, 1.2, True),
        (9.2, 14.4, 6.0, 2.4, True), (8.0, 10.2, 7.0, -1.2, False), (7.2, 5.4, 7.6, 0.8, False)]
    cells = {}
    for k, (bx, tx, ty, bend, shade) in enumerate(blades):
        steps = 40
        for s in range(steps + 1):
            t = s / steps
            x = bx + (tx - bx) * t + bend * 4 * t * (1 - t)
            y = 14.0 + (ty - 14.0) * t
            key = (round(x), round(y))
            if key not in cells or cells[key][0] < t:
                cells[key] = (t, shade)
    sb = [col("spiritb", i) for i in range(5)]
    for (x, y), (t, shade) in cells.items():
        ramp = sp if shade else sb
        c.set(x, y, ramp[min(len(ramp) - 1, 5 if t > 0.82 else 4 if t > 0.5 else 3 if t > 0.2 else 2)])
    for (x, y), (t, shade) in list(cells.items()):                  # the shaded side of the tall blades
        if shade and t < 0.7 and c.get(x + 1, y)[3] == 0:
            c.set(x + 1, y, sp[2] if t > 0.3 else sp[1])
    for (x, y, i) in ((5, 14, 1), (6, 14, 2), (7, 14, 3), (8, 14, 4), (9, 14, 3), (10, 14, 2), (11, 14, 1), (7, 13, 3), (8, 13, 4)):
        c.set(x, y, sp[i] if i < 4 else col("cream", 3))             # the roots: a clutch of pale threads
    out = _outline(c, "#06222e")
    for (x, y, i) in ((6, 0, 3), (11, 4, 4), (2, 2, 4), (14, 11, 2), (1, 9, 2), (12, 8, 3)):       # motes loose in the dark
        if out.get(x, y)[3] == 0 and all(out.get(x + dx, y + dy)[3] == 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            out.set(x, y, sp[i])
    return out


# ---------------------------------------------------------------------------------------------------------------------
# the bowl

BOWL_OUTER = {4: (4, 11), 5: (2, 13), 6: (1, 14), 7: (1, 14), 8: (2, 13), 9: (3, 12), 10: (4, 11), 11: (5, 10),
              12: (6, 9)}
OUTER = {(x, y) for y, (a, b) in BOWL_OUTER.items() for x in range(a, b + 1)}
RIM = {(x, y) for (x, y) in OUTER if y <= 8 and any((x + dx, y + dy) not in OUTER for dx, dy in ((1, 0), (-1, 0), (0, -1)))}
CONTENT = {(x, y) for (x, y) in OUTER if y <= 8} - RIM


def bowl(fill) -> Canvas:
    """The wooden bowl with its contents painted by fill(x, y) -> rgba: a ring of rim, the surface in it, the lip, the body."""
    c = Canvas()
    for (x, y) in OUTER:
        if (x, y) in CONTENT:
            c.set(x, y, fill(x, y))
            continue
        if (x, y) in RIM:
            lit = -(x - 7.5) * 0.6 - (y - 6.5) * 0.9
            i = 5 if lit > 2.6 else 4 if lit > 0.6 else 3 if lit > -1.8 else 2
        elif y == 9:
            i = 4 if x <= 6 else 3
        else:
            a, b = BOWL_OUTER[y]
            i = {10: 3, 11: 2, 12: 1}[y] + (1 if x == a else -1 if x == b else 0)
        c.set(x, y, col("bowl", i))
    for (x, y) in CONTENT:
        if y == 5:                                        # the far wall's shadow falls on the surface
            c.blend(x, y, hex_rgba("#120a04"), 0.28)
    return c


def surface(base, lo=2, hi=4):
    """A fill that is lit toward the upper left: ramp `base` from `lo` to `hi`."""
    def fill(x, y):
        lit = -(x - 7.0) * 0.32 - (y - 6.5) * 0.55
        return col(base, hi if lit > 0.8 else lo + 1 if lit > -0.7 else lo)
    return fill


def herb_broth() -> Canvas:
    br = [col("broth", i) for i in range(6)]
    gn = [col("greens", i) for i in range(6)]
    c = bowl(surface("broth", 2, 4))
    for (x, y, i) in ((4, 6, 5), (5, 6, 4), (11, 7, 2), (12, 7, 2)):       # sheen on a clear broth
        c.set(x, y, br[i])
    # leaf bits: each two or three pixels, a dark and a bright, floating
    for (x, y, i) in ((5, 7, 2), (6, 7, 3), (6, 8, 1),
                      (8, 5, 2), (9, 5, 3), (9, 6, 1),
                      (10, 7, 3), (11, 6, 2), (11, 8, 1), (12, 8, 2)):
        c.set(x, y, gn[i])
    c.set(7, 6, br[1]); c.set(3, 7, br[1])
    # a sprig riding on top: two leaves off a short stem, over the back rim
    for (x, y, i) in ((6, 4, 2), (7, 3, 3), (8, 3, 4), (7, 4, 1), (8, 4, 2), (9, 2, 3), (10, 2, 4), (9, 3, 2), (10, 3, 1)):
        c.set(x, y, gn[i])
    return _outline(c)


def frostcap_tea() -> Canvas:
    tea = [col("tea", i) for i in range(6)]
    cp = [col("cap", i) for i in range(6)]
    c = bowl(surface("tea", 2, 4))
    for (x, y) in ((4, 6), (5, 6), (3, 7), (12, 7)):
        c.set(x, y, tea[5] if x < 6 else tea[3])
    # the slice of frostcap: a pale ellipse, a ring of ice-blue gills, a bright stalk dot
    def slice_(u, v, d):
        if d < 0.35:
            return cp[5]
        if d < 0.7:
            return cp[2] if (round(u * 3) + round(v * 3)) % 2 == 0 else cp[3]
        return cp[4] if u + v < 0.4 else cp[3]
    ellipse(c, 8.2, 6.9, 3.2, 1.8, slice_)
    c.set(5, 5, tea[1]); c.set(11, 8, tea[1])
    out = _outline(c)
    # a frost sparkle standing over the rim: a plus of white, its arms ice blue
    for (x, y) in ((11, 2), (10, 3), (12, 3), (11, 4)):
        out.set(x, y, cp[3])
    out.set(11, 3, cp[5])
    out.set(4, 3, cp[4])
    return out


def ember_skewer() -> Canvas:
    """A skewer on the diagonal: bare stick at both ends, a pointed tip at the lower left, three seared chunks of meat with
    grill marks, glazed with emberroot: bright orange runs and a few embers."""
    c = Canvas()
    sk = [col("skewer", i) for i in range(5)]
    mt = [col("meat", i) for i in range(6)]
    em = [col("ember", i) for i in range(6)]
    ch = [col("char", i) for i in range(3)]
    # the stick: two px wide at the handle end, thinning to a point
    for k in range(0, 14):
        x, y = 1 + k, 14 - k
        c.set(x, y, sk[3] if k > 1 else sk[2])
        if 2 <= k <= 13:
            c.set(x + 1, y, sk[1])
        if k > 4:
            c.set(x, y - 1, sk[4])
    c.set(14, 1, sk[4]); c.set(15, 1, sk[2]); c.set(14, 0, sk[3])
    for (cx, cy) in ((4.4, 10.8), (8.0, 7.4), (11.4, 4.0)):
        def paint(u, v, d, cx=cx, cy=cy):
            lit = -u * 0.6 - v * 0.7
            i = 4 if lit > 0.5 else 3 if lit > -0.05 else 2 if lit > -0.55 else 1
            return mt[i]
        cover = ellipse(c, cx, cy, 2.55, 2.55, paint)
        # the chunk's corners are knocked off
        for (x, y), (u, v, d) in cover.items():
            if abs(u) > 0.78 and abs(v) > 0.78:
                c.set(x, y, (0, 0, 0, 0))
        # sear: a dark grill mark crosses it, then the glaze runs down the shadow side and pools
        for (x, y), (u, v, d) in cover.items():
            if c.get(x, y)[3] == 0:
                continue
            if abs((u - v) * 0.5 - 0.1) < 0.2 and abs(u + v) < 1.1:
                c.set(x, y, ch[2] if d < 0.9 else ch[1])
            elif u + v > 0.35 and d > 0.35:
                c.set(x, y, em[3] if (x + y) % 2 == 0 else em[2])
    # glaze flecks and glowing embers on and round the chunks
    for (x, y, i) in ((5, 9, 4), (7, 8, 5), (9, 7, 4), (10, 4, 5), (12, 5, 4), (3, 11, 4), (6, 12, 3), (9, 9, 3)):
        if c.get(x, y)[3]:
            c.set(x, y, em[i])
    out = _outline(c)
    for (x, y, i) in ((13, 6, 4), (2, 8, 3), (6, 3, 5), (13, 9, 3)):
        if out.get(x, y)[3] == 0:
            out.set(x, y, em[i])
    return out


def moonmoss_soup() -> Canvas:
    sp = [col("soup", i) for i in range(6)]
    ms = [col("moss", i) for i in range(6)]
    c = bowl(surface("soup", 2, 4))
    # mushroom slices drifting in the dark: paler browns
    for (x, y, i) in ((4, 6, 5), (5, 6, 4), (10, 8, 4), (11, 8, 5), (11, 7, 3), (3, 7, 3), (8, 7, 3)):
        c.set(x, y, sp[i])
    for (x, y) in ((5, 8), (9, 6), (12, 6)):
        c.set(x, y, sp[1])
    # the moss: a heap off-centre, silver on its lit side, violet in shadow, fuzzy edge and pale glints
    heap = [R(0, ""), R(0, ""), R(0, ""),
            R(5, "..3.4.."),
            R(4, "..2433.."),
            R(4, ".234444.."),
            R(4, ".1233324."),
            R(5, ".12221.")]
    for y, row in enumerate(heap):
        for x, chx in enumerate(row):
            if chx.isdigit():
                c.set(x + 1, y, ms[int(chx)])
    # lay the heap properly: it sits across the back rim and the near surface
    for (x, y) in ((7, 3), (9, 4), (6, 5), (10, 5)):
        c.set(x, y, ms[5])
    c.set(8, 4, ms[5]); c.set(11, 6, ms[4])
    return _outline(c)


def hunters_stew() -> Canvas:
    st = [col("stew", i) for i in range(6)]
    mt = [col("meat", i) for i in range(6)]
    ca = [col("carrot", i) for i in range(5)]
    po = [col("potato", i) for i in range(5)]
    c = bowl(surface("stew", 2, 4))
    for (x, y) in ((4, 6), (5, 6), (3, 7), (6, 7)):
        c.set(x, y, st[5])
    for (x, y) in ((11, 8), (12, 7)):
        c.set(x, y, st[1])
    # a heap above the rim: gravy-slick chunks. Meat dark red-brown, carrot orange with a lighter ring, potato pale cubes
    meat = ["m1m", "m21", "dd1"]
    def chunk(x0, y0, rows, leg):
        stamp(c, x0, y0, rows, leg)
    leg_meat = {"a": mt[4], "b": mt[3], "c": mt[2], "d": mt[1]}
    leg_car = {"a": ca[3], "b": ca[2], "c": ca[1], "d": ca[0], "e": ca[4]}
    leg_pot = {"a": po[4], "b": po[3], "c": po[2], "d": po[1]}
    chunk(3, 4, [".aab", "abbc", "bccd", ".dd."], leg_meat)         # meat, left
    chunk(8, 3, [".ea.", "abbc", "bbcd", ".cd."], leg_car)           # carrot, top
    chunk(11, 5, ["aab.", "bbcd", ".cdd"], leg_pot)                  # potato, right
    chunk(6, 5, ["ab.", "bcc", ".dd"], leg_pot)                      # potato, centre
    chunk(8, 6, ["ab.", "bbc", "cdd"], leg_meat)                     # meat, front
    chunk(4, 7, ["ea", "bc"], leg_car)                               # a carrot coin on the left
    c.set(10, 7, st[5]); c.set(5, 4, st[5])
    return _outline(c)


def foragers_salad() -> Canvas:
    gn = [col("greens", i) for i in range(6)]
    br = [col("berry", i) for i in range(5)]
    pt = [col("petal", i) for i in range(5)]
    c = bowl(surface("greens", 1, 3))
    # the leaves piled above the rim: ragged, three greens, pale ribs
    pile = [(6, 1, "3423"), (4, 2, "34234323"), (3, 3, "3342343323"), (2, 4, "334234232433"), (2, 5, "423343234423"),
            (2, 6, "334234323344")]
    for (x0, y, text) in pile:
        for k, chx in enumerate(text):
            c.set(x0 + k, y, gn[int(chx)])
    # a few light ribs and dark gaps so it reads as leaves, not a lump
    for (x, y, i) in ((6, 3, 5), (7, 4, 5), (8, 5, 5), (10, 4, 5), (4, 5, 4), (11, 6, 4), (5, 7, 5)):
        c.set(x, y, gn[i])
    for (x, y, i) in ((6, 5, 1), (9, 4, 1), (7, 6, 1), (10, 7, 1), (5, 6, 1), (8, 3, 1)):
        c.set(x, y, gn[i])
    # three red berries (each a 2x2 with a highlight) and a purple petal curling over the front
    for (bx, by) in ((4, 4), (9, 5), (6, 6)):
        c.set(bx, by, br[3]); c.set(bx + 1, by, br[2]); c.set(bx, by + 1, br[2]); c.set(bx + 1, by + 1, br[1])
        c.set(bx, by, br[4] if (bx, by) != (6, 6) else br[3])
    for (x, y, i) in ((10, 6, 3), (11, 6, 4), (12, 6, 3), (11, 7, 2), (12, 7, 3), (13, 7, 1), (10, 7, 1)):
        c.set(x, y, pt[i])
    return _outline(c)


def miners_pasty() -> Canvas:
    """A half-moon pasty standing on its flat side: a raised rope of crimping along the curve (lit and shadowed twists, a
    dark seam under it so it stands proud), the crust golden with an egg-wash shine on the upper left, two steam slits,
    and a darker baked base."""
    c = Canvas()
    pa = [col("pastry", i) for i in range(6)]
    cx, base, rx, ry = 8.0, 13.0, 7.4, 9.0
    for y in range(16):
        for x in range(16):
            u, v = (x + 0.5 - cx) / rx, (base - (y + 0.5)) / ry       # v up from the flat base
            if v < -0.06:
                continue
            e = math.hypot(u, v)
            if e > 1.0:
                continue
            lit = -u * 0.5 + v * 0.6 - e * 0.25
            if e > 0.8:                                               # the rope: twists by angle, leaning one way
                a = math.atan2(max(v, 0.0), u)
                twist = int((a + 0.12) / math.radians(19)) % 2
                i = (5 if lit > 0.0 else 4) if twist == 0 else (3 if lit > -0.3 else 2)
            elif e > 0.68:
                i = 1                                                 # the seam under the rope
            elif v < 0.12:
                i = 1 if (x % 2) else 2                               # the baked base
            else:
                i = 4 if lit > 0.12 else 3 if lit > -0.25 else 2
            c.set(x, y, pa[i])
    for (x, y) in ((5, 8), (6, 7), (5, 9), (7, 7)):                   # the egg-wash shine
        c.set(x, y, col("honey", 5) if (x, y) == (6, 7) else col("honey", 4))
    for (x, y) in ((7, 10), (8, 9), (10, 9), (11, 8)):              # two parallel steam cuts, a lit lip under each
        c.set(x, y, col("char", 2))
    for (x, y) in ((8, 10), (11, 9)):
        c.set(x, y, pa[5])
    return _outline(c)


def anglers_chowder() -> Canvas:
    cr = [col("cream", i) for i in range(6)]
    sm = [col("salmon", i) for i in range(5)]
    po = [col("potato", i) for i in range(5)]
    gn = [col("greens", i) for i in range(6)]
    c = bowl(surface("cream", 2, 4))
    for (x, y) in ((4, 6), (5, 6), (3, 7)):
        c.set(x, y, cr[5])
    for (x, y) in ((11, 8), (12, 7)):
        c.set(x, y, cr[1])
    # a chunk of salmon with its pale flake lines, two potato cubes, a few chives
    stamp(c, 5, 5, ["ab.", "bcc", ".dd"], {"a": sm[4], "b": sm[3], "c": sm[2], "d": sm[1]})
    for (x, y) in ((6, 6), (7, 7)):
        c.set(x, y, sm[4])
    stamp(c, 9, 6, ["ab", "bc"], {"a": po[4], "b": po[3], "c": po[2]})
    stamp(c, 3, 6, ["a"], {"a": po[3]})
    for (x, y, i) in ((8, 5, 3), (11, 6, 2), (4, 8, 3), (10, 8, 2)):
        c.set(x, y, gn[i])
    # the tail of a fish poking out over the back rim at the right
    stamp(c, 9, 1, ["..a", ".aa", "bba", "bbc", ".bc", "..c"][:0] or ["b.a", "bba", ".bb", ".bc"], {"a": sm[3], "b": sm[2], "c": sm[1]})
    out = _outline(c)
    return out


def spirit_tea() -> Canvas:
    sp = [col("spirit", i) for i in range(6)]
    hn = [col("honey", i) for i in range(6)]

    def fill(x, y):
        d = math.hypot((x - 7.5) / 5.0, (y - 6.7) / 2.1)         # brightest in the middle: the tea glows
        return sp[5] if d < 0.4 else sp[4] if d < 0.75 else sp[3]
    c = bowl(fill)
    # the honey swirl: a spiral drawn in three tones over the middle of the cup
    for (x, y, i) in ((6, 6, 4), (7, 6, 5), (8, 6, 5), (9, 6, 4), (10, 6, 3), (10, 7, 3), (9, 8, 2), (8, 8, 3), (7, 8, 3),
                      (5, 7, 3), (6, 8, 2), (6, 7, 2), (8, 7, 4), (9, 7, 2)):
        c.set(x, y, hn[i])
    c.set(8, 7, hn[5]); c.set(7, 7, hn[1])
    out = _outline(c)
    # motes of light rising off it, outside the outline
    for (x, y, i) in ((6, 2, 4), (7, 1, 5), (8, 2, 3), (11, 3, 4), (4, 3, 3), (9, 0, 3), (12, 1, 2), (5, 1, 2)):
        out.set(x, y, sp[i])
    return out


ITEMS = {"sunleaf": sunleaf, "frostcap": frostcap, "emberroot": emberroot, "moonmoss": moonmoss, "duskbloom": duskbloom,
         "spiritgrass": spiritgrass, "herb_broth": herb_broth, "frostcap_tea": frostcap_tea, "ember_skewer": ember_skewer,
         "moonmoss_soup": moonmoss_soup, "hunters_stew": hunters_stew, "foragers_salad": foragers_salad,
         "miners_pasty": miners_pasty, "anglers_chowder": anglers_chowder, "spirit_tea": spirit_tea}


def build(out):
    for name, fn in ITEMS.items():
        out.item(name, fn())


# ---------------------------------------------------------------------------------------------------------------------
# Preview and checks

def check(images=None):
    images = images or {n: f().image() for n, f in ITEMS.items()}
    assert len(images) == len(ITEMS) == 15
    seen, shapes, means = {}, {}, {}
    for n, im in images.items():
        assert im.size == (16, 16) and im.mode == "RGBA", n
        raw = im.tobytes()
        px = list(zip(*[iter(raw)] * 4))
        solid = [p for p in px if p[3] > 0]
        assert all(p[3] == 255 for p in solid), f"{n} has partial alpha"
        assert 50 <= len(solid) <= 215, f"{n} fills {len(solid)} px"
        colours = {p for p in solid}
        assert len(colours) >= 9, f"{n} only has {len(colours)} colours"
        assert raw not in seen, f"{n} is identical to {seen[raw]}"
        seen[raw] = n
        mask = tuple(p[3] > 0 for p in px)
        mean = tuple(sum(p[k] for p in solid if p[:3] != hex_rgba(OUTLINE)[:3]) / max(1, len([p for p in solid if p[:3] != hex_rgba(OUTLINE)[:3]])) for k in range(3))
        means[n] = mean
        shapes[n] = mask
        print(f"  {n:16s} {len(solid):3d} px  {len(colours):2d} colours  mean rgb {tuple(round(v) for v in mean)}")
    # the herbs must differ in silhouette from each other and in colour from every other item; no two items share a hue
    names = list(images)
    for i, a in enumerate(names):
        for b in names[i + 1:]:
            dist = math.dist(means[a], means[b])
            assert dist >= 14, f"{a} and {b} are too close in colour ({dist:.0f})"
            if a in HERBS and b in HERBS:
                same = sum(1 for p, q in zip(shapes[a], shapes[b]) if p == q)
                assert same < 220, f"{a} and {b} have almost the same silhouette"
    for n, f in ITEMS.items():
        assert f().image().tobytes() == images[n].tobytes(), f"{n} is not deterministic"
    print("items ok: 16x16, opaque pixels only, distinct, multi-tone, herbs distinct in silhouette, all distinct in colour, deterministic")


HERBS = ("sunleaf", "frostcap", "emberroot", "moonmoss", "duskbloom", "spiritgrass")


def preview(directory):
    from PIL import Image, ImageDraw, ImageFont
    os.makedirs(directory, exist_ok=True)
    images = {n: f().image() for n, f in ITEMS.items()}
    scale, pad, cols, label = 14, 10, 5, 14
    rows = (len(images) + cols - 1) // cols
    cell_w, cell_h = 16 * scale + pad, 16 * scale + pad + label
    try:
        font = ImageFont.load_default(11)
    except TypeError:
        font = ImageFont.load_default()
    for bg, fg, name in (((74, 78, 90, 255), (236, 238, 244, 255), "items_crafts_grey.png"),
                         ((14, 16, 24, 255), (190, 196, 214, 255), "items_crafts_night.png")):
        sheet = Image.new("RGBA", (cols * cell_w + pad, rows * cell_h + pad), bg)
        d = ImageDraw.Draw(sheet)
        for i, (n, im) in enumerate(images.items()):
            x0, y0 = pad + (i % cols) * cell_w, pad + (i // cols) * cell_h
            big = im.resize((16 * scale, 16 * scale), Image.NEAREST)
            sheet.paste(big, (x0, y0), big)
            d.text((x0 + 2, y0 + 16 * scale + 1), n, fill=fg, font=font)
        sheet.save(os.path.join(directory, name))
    # and at the size they have in a hotbar slot (x2), next to a vanilla-grey inventory background
    strip = Image.new("RGBA", (len(images) * 40 + 8, 48), (139, 139, 139, 255))
    for i, im in enumerate(images.values()):
        big = im.resize((32, 32), Image.NEAREST)
        strip.paste(big, (8 + i * 40, 8), big)
    strip.save(os.path.join(directory, "items_crafts_x2.png"))
    print("preview written to", directory)
    check(images)


if __name__ == "__main__":
    if len(sys.argv) >= 2 and sys.argv[1] == "preview":
        preview(sys.argv[2] if len(sys.argv) > 2 else ".")
    else:
        check()
