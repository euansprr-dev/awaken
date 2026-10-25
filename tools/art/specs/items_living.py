"""The Living Wilds' items: seven 16x16 icons painted for the quests and Association errands.

    python3 tools/art/build_all.py items_living          write src/main/resources/assets/awaken/textures/item/<id>.png
    python3 tools/art/specs/items_living.py preview DIR  contact sheet (x12, on mid grey and on night) + the checks

    shepherds_lantern  Hettie's old iron lantern: crook handle, dented cage, cracked glass, one steady amber flame, rust
    lumen_tonic        Ilse's stoppered flask of pale gold glow with a moth on its label and two motes rising
    wrens_hook         a three-tined grapnel of blue-grey steel, brass collar, a coil of hemp rope with its end trailing
    caravan_writ       Marta's rolled writ: parchment tube, teal ribbon bow, a red wax seal pressed with a wheel
    herdcaller_horn    Gus's horn carved from a Stonehorn shard: ribbed grey stone, leather bands, a pale tip, a dark bell
    pells_spurs        Pell's worn riding spur, one drawn bold: brass heel band, strap stubs, verdigris, a bent star rowel
    pells_letter       the folded letter, blue wax at the flap's point, a road-worn corner and a rust-brown thumbprint

Every icon is drawn by hand from the shared palette (pixelart.RAMPS) plus small local ramps, one pixel outline, light from
the top left. Nothing is random: the same ids always give the same pixels.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import *  # noqa: E402,F401,F403
from pixelart import _outline  # noqa: E402

LOCAL = {
    "iron":   ["#14161b", "#25282f", "#3b3f49", "#555b68", "#737a89", "#9aa2b2"],
    "steel":  ["#1a2230", "#2c3a4e", "#43566f", "#5f7592", "#82a0bc", "#b6d0e4"],
    "rust":   ["#2e140a", "#5a2a12", "#82401a", "#a85a26", "#c47a38"],
    "hemp":   ["#4a3820", "#6e5430", "#957a46", "#b89c64", "#d6bc86", "#ece0b8"],
    "cork":   ["#5a4026", "#8a6a42", "#b08c58", "#cfae78", "#e8cf9a"],
    "glass":  ["#4a7494", "#7aa6c4", "#a9cfe0", "#cde4ee", "#e6f4f8", "#ffffff"],
    "gold":   ["#7a4c0e", "#b07818", "#dca630", "#f2cc54", "#ffe88a", "#fffbe0"],
    "paper":  ["#8a7a58", "#b4a37c", "#d4c49a", "#e8dcb8", "#f4ecd2", "#fffaea"],
    "ink":    ["#2a2438", "#4a4560", "#6c6890", "#9892b8"],
    "teal":   ["#0c2a2a", "#14504e", "#1f7a72", "#34a496", "#66d0be"],
    "wax":    ["#2a0508", "#5a0d12", "#8c1a20", "#bc2a30", "#e0504c", "#ff8c7c"],
    "blue":   ["#0a1a40", "#122e7a", "#1e4ea8", "#3a7ad8", "#78b4ff", "#c4e0ff"],
    "horn":   ["#2a2824", "#46443e", "#64625a", "#84817a", "#a6a298", "#c8c4b8"],
    "leather": ["#1e120a", "#33200f", "#4d3019", "#684523", "#835c30", "#a07a45"],
    "dust":   ["#6a2a1c", "#8a3a22"],
}


def col(name, i):
    rp = LOCAL.get(name) or RAMPS[name]
    return hex_rgba(rp[max(0, min(len(rp) - 1, i))])


def art(rows, legend, c=None):
    """Paint rows of characters ('.' and ' ' are clear) from a legend of char -> rgba."""
    c = c or Canvas()
    assert len(rows) <= 16, f"{len(rows)} rows"
    for y, row in enumerate(rows):
        assert len(row) <= 16, f"row {y} is {len(row)} wide: {row!r}"
        for x, ch in enumerate(row):
            if ch in ". ":
                continue
            c.set(x, y, legend[ch])
    return c


def line(c, p0, p1, colours):
    (x0, y0), (x1, y1) = p0, p1
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(n + 1):
        t = i / n if n else 0
        c.set(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t),
              colours[i % len(colours)] if isinstance(colours, list) else colours)


# ---------------------------------------------------------------------------------------------------------------------
# shepherds_lantern

def shepherds_lantern() -> Canvas:
    rows = [
        "................",
        ".....hII........",   # the crook: an arch that ends in a brass ball ...
        "....h...I.......",
        "....b...I.......",
        ".......hIi......",   # ... and comes down into the hat
        "......hIIIi.....",
        "....hhIIIrIii...",   # brim, one rust patch
        "....IggggggGd...",
        "....IgGGyGGgd...",
        "....IgGaYaGgd...",
        "....IgaYWYaGd...",
        "....IgGaYaGgd...",
        "....IgGcccgGd...",   # the candle stub
        "....hIIIIIrii...",   # base plate
        ".....diIIIid....",
        "................",
    ]
    leg = {"h": col("iron", 5), "I": col("iron", 3), "i": col("iron", 2), "d": col("iron", 1), "r": col("rust", 3),
           "b": col("brass", 4),
           "g": hex_rgba("#6e3a0c"), "G": hex_rgba("#b0661a"), "a": hex_rgba("#f0a030"), "y": hex_rgba("#ffcc66"),
           "Y": hex_rgba("#ffe49a"), "W": hex_rgba("#fff8dc"), "c": hex_rgba("#d8cca8")}
    c = art(rows, leg)
    # battered: the right bar is knocked in at the middle, rust creeps up the left bar, the brim has a chipped corner
    c.set(12, 9, (0, 0, 0, 0))
    c.set(11, 9, col("iron", 1))
    c.set(11, 10, col("iron", 2))
    c.set(12, 10, (0, 0, 0, 0))
    c.set(5, 6, col("rust", 2))
    c.set(4, 10, col("rust", 3))
    c.set(4, 11, col("rust", 2))
    c.set(12, 13, col("rust", 2))
    # one crack through the left pane
    c.set(6, 8, hex_rgba("#c9701a"))
    c.set(6, 9, hex_rgba("#2a1406"))
    return _outline(c)


# ---------------------------------------------------------------------------------------------------------------------
# lumen_tonic

def R(x0, text):
    """One row: `text` starting at column x0, clear everywhere else."""
    row = "." * x0 + text
    assert len(row) <= 16, f"{text!r} at {x0} is too wide"
    return row.ljust(16, ".")


def lumen_tonic() -> Canvas:
    rows = [
        R(0, ""),
        R(5, "qqKKkk"),         # the cork's flange
        R(6, "eKkE"),
        R(6, "eWwE"),
        R(6, "eWwE"),
        R(5, "eWwwwE"),
        R(4, "eWwwwwwE"),
        R(3, "eWwwwwwwwE"),
        R(3, "elPPPPPPlE"),     # the surface of the tonic
        R(3, "elppppppLE"),     # the label ...
        R(3, "eLvvppvvlE"),
        R(3, "eLpvoovplE"),     # ... with a moth on it, wings spread
        R(3, "eLppvvpplE"),
        R(4, "eLLPPLlE"),
        R(5, "eLLllE"),
    ]
    leg = {"q": col("cork", 4), "K": col("cork", 3), "k": col("cork", 2),
           "e": col("glass", 2), "E": col("glass", 1), "W": col("glass", 5), "w": col("glass", 3),
           "P": col("gold", 5), "L": col("gold", 3), "l": col("gold", 2),
           "p": col("paper", 4), "v": col("ink", 3), "o": col("ink", 1)}
    c = art(rows, leg)
    # glow: the liquid is the brightest thing in the flask, hottest low on the left where the label's shadow does not fall
    for (x, y) in ((4, 9), (4, 10), (4, 11), (5, 13), (9, 13), (6, 14)):
        c.set(x, y, col("gold", 4))
    c.set(11, 9, col("gold", 4))
    c.set(5, 9, col("paper", 5)); c.set(6, 9, col("paper", 5))   # a paper highlight on the label's top edge
    c = _outline(c)
    # a plus-shaped spark and two motes drift off the top right, outside the outline
    c.set(12, 3, col("gold", 5))
    for (x, y) in ((11, 3), (13, 3), (12, 2), (12, 4)):
        c.set(x, y, col("gold", 3))
    c.set(14, 6, col("gold", 4))
    c.set(2, 5, col("gold", 3))
    return c


# ---------------------------------------------------------------------------------------------------------------------
# wrens_hook

def wrens_hook() -> Canvas:
    # three curved claws (the outer two bow out and hook back in, the middle is a spike), a brass collar, the coil beside it
    leg = {"f": col("steel", 5), "e": col("steel", 4), "c": col("steel", 3), "b": col("steel", 2), "a": col("steel", 1),
           "E": col("brass", 4), "D": col("brass", 5), "B": col("brass", 3), "C": col("brass", 2)}
    rows = [
        R(0, ""),
        R(5, "f..f..e"),
        R(4, "e...e...c"),
        R(3, "e....e....b"),
        R(3, "c...ecb...b"),
        R(4, "c..ecb..b"),
        R(5, "ceecbba"),
        R(7, "ecb"),
        R(6, "EDDBC"),
    ]
    c = art(rows, leg)
    hp = [col("hemp", i) for i in range(6)]
    # the coil: two turns of hemp, each strand twisted, with the hole showing
    cx, cy, a, b = 5.0, 11.0, 3.9, 3.3
    for y in range(7, 15):
        for x in range(0, 12):
            dx, dy = (x + 0.5 - cx) / a, (y + 0.5 - cy) / b
            d = dx * dx + dy * dy
            inner = (dx * a / 1.75) ** 2 + (dy * b / 1.5) ** 2
            if d <= 1.0 and inner > 1.0:
                lit = (-dx * 0.55 - dy * 0.85)
                i = 3 if lit > 0.15 else 2 if lit > -0.45 else 1
                if (x * 3 + y * 2) % 5 < 2:                      # strands and fibres
                    i += 1
                if d > 0.8:
                    i -= 1
                c.set(x, y, hp[max(1, min(5, i))])
    # the loose end trails off to the right and frays
    for (x, y, i) in ((9, 11, 3), (9, 12, 2), (10, 12, 3), (11, 12, 2), (12, 12, 3), (13, 11, 3), (13, 10, 4), (14, 10, 5)):
        c.set(x, y, hp[i])
    return _outline(c)


# ---------------------------------------------------------------------------------------------------------------------
# caravan_writ

def caravan_writ() -> Canvas:
    c = Canvas()
    cx = cy = 8.0
    k = math.sqrt(0.5)
    paper = [col("paper", i) for i in range(6)]

    def uv(x, y):
        dx, dyu = x + 0.5 - cx, cy - (y + 0.5)
        return (dx + dyu) * k, (dx - dyu) * k       # along the roll (up and right), across it (down and right: shadow)

    for y in range(16):
        for x in range(16):
            u, v = uv(x, y)
            if abs(u) <= 7.0 and abs(v) <= 2.45:
                i = 5 if v < -1.5 else 4 if v < -0.3 else 3 if v < 0.9 else 2 if v < 1.8 else 1
                c.set(x, y, paper[i])
    # the rolled ends: a bright lip and the dark curl of the next turn inside it
    for sgn in (1, -1):
        for y in range(16):
            for x in range(16):
                u, v = uv(x, y)
                u *= sgn
                if abs(v) <= 2.45 and 5.55 <= u <= 7.0:
                    if u > 6.3:
                        c.set(x, y, paper[5] if v < 0.4 else paper[3])
                    elif abs(v) < 1.3:
                        c.set(x, y, paper[0])
                    else:
                        c.set(x, y, paper[1])
    # a ribbon around the middle: wide enough to show on both sides of the seal, bow and two tails off the shadow side
    tl = [col("teal", i) for i in range(5)]
    for y in range(16):
        for x in range(16):
            u, v = uv(x, y)
            if -1.7 <= u <= 0.8 and abs(v) <= 2.45:
                c.set(x, y, tl[4] if v < -1.4 else tl[3] if v < 0.4 else tl[2] if v < 1.6 else tl[1])
    for (x, y, i) in ((10, 11, 3), (10, 12, 3), (11, 13, 2), (11, 14, 2),       # the long tail
                      (11, 10, 3), (12, 10, 2), (13, 11, 3), (13, 12, 2)):      # the short tail
        c.set(x, y, tl[i])
    c.set(9, 11, tl[2]); c.set(10, 10, tl[3])
    # the wax seal sits on the shadow edge, over the knot, pressed with a wheel (a ring around a hub)
    wx = [col("wax", i) for i in range(6)]
    seal = {(9, 8): 4, (10, 8): 3, (8, 9): 4, (9, 9): 4, (10, 9): 3, (11, 9): 2,
            (8, 10): 3, (9, 10): 3, (10, 10): 3, (11, 10): 2, (9, 11): 2, (10, 11): 2}
    for (x, y), i in seal.items():
        c.set(x, y, wx[i])
    c.set(9, 9, wx[5])         # the wax's shine
    c.set(10, 10, wx[1])       # the wheel's hub, pressed in
    c.set(9, 10, wx[2]); c.set(11, 10, wx[1]); c.set(10, 9, wx[2]); c.set(10, 11, wx[1])
    return _outline(c)


# ---------------------------------------------------------------------------------------------------------------------
# herdcaller_horn

def herdcaller_horn() -> Canvas:
    c = Canvas()
    hr = [col("horn", i) for i in range(6)]
    lt = [col("leather", i) for i in range(6)]
    br = [col("brass", i) for i in range(6)]
    P0, P1, P2, P3 = (2.6, 13.4), (0.8, 7.2), (6.4, 6.4), (12.3, 3.5)

    def bez(t):
        m = 1 - t
        return (m ** 3 * P0[0] + 3 * m * m * t * P1[0] + 3 * m * t * t * P2[0] + t ** 3 * P3[0],
                m ** 3 * P0[1] + 3 * m * m * t * P1[1] + 3 * m * t * t * P2[1] + t ** 3 * P3[1])

    def tangent(t):
        m = 1 - t
        dx = 3 * m * m * (P1[0] - P0[0]) + 6 * m * t * (P2[0] - P1[0]) + 3 * t * t * (P3[0] - P2[0])
        dy = 3 * m * m * (P1[1] - P0[1]) + 6 * m * t * (P2[1] - P1[1]) + 3 * t * t * (P3[1] - P2[1])
        n = math.hypot(dx, dy)
        return dx / n, dy / n

    half = lambda t: 0.85 + 2.25 * t ** 1.7             # a thin mouthpiece flaring to a wide bell
    steps = 240
    end_t = tangent(1.0)
    cover = {}
    for s in range(steps + 1):
        t = s / steps
        px, py = bez(t)
        tx, ty = tangent(t)
        nx, ny = ty, -tx
        if nx * -0.7 + ny * -0.7 < 0:
            nx, ny = -nx, -ny                               # the normal that faces the light (upper left)
        w = half(t)
        for y in range(16):
            for x in range(16):
                qx, qy = x + 0.5 - px, y + 0.5 - py
                d = math.hypot(qx, qy)
                if d > w:
                    continue
                if (x + 0.5 - P3[0]) * end_t[0] + (y + 0.5 - P3[1]) * end_t[1] > 0.0:
                    continue                                # nothing past the bell's mouth
                prev = cover.get((x, y))
                if prev is None or d / w < prev[0]:
                    cover[(x, y)] = (d / w, t, (qx * nx + qy * ny) / w)
    for (x, y), (rel, t, off) in cover.items():
        i = 5 if off > 0.55 else 4 if off > 0.12 else 3 if off > -0.35 else 2 if off > -0.72 else 1
        if int(t / 0.075) % 2:
            i -= 1                                          # strata: the stone is ribbed like horn
        if (x * 13 + y * 7) % 11 == 0:
            i += 1                                          # grit
        elif (x * 5 + y * 11) % 13 == 0:
            i -= 1
        if t < 0.1:
            i += 1                                          # the tip is paler, like a real horn's
        c.set(x, y, hr[max(0, min(5, i))])
    # leather bands pulled tight: a mouthpiece wrap, a strap in the middle, one under the bell
    for tb, wd, studs in ((0.12, 0.05, False), (0.46, 0.05, True), (0.8, 0.045, False)):
        for (x, y), (rel, t, off) in cover.items():
            if abs(t - tb) < wd:
                i = 5 if off > 0.45 else 4 if off > -0.1 else 3 if off > -0.6 else 2
                c.set(x, y, lt[i])
        if studs:
            best = min((abs(t - tb) + abs(off - 0.4) * 0.1, (x, y)) for (x, y), (rel, t, off) in cover.items() if abs(t - tb) < wd)
            c.set(best[1][0], best[1][1], br[5])
    # the mouth of the bell: dark inside, a pale rim on the far side
    nx, ny = -end_t[1], end_t[0]
    r = half(1.0)
    for y in range(16):
        for x in range(16):
            qx, qy = x + 0.5 - P3[0], y + 0.5 - P3[1]
            a = (qx * nx + qy * ny) / r
            b = (qx * end_t[0] + qy * end_t[1]) / 1.15
            e = math.hypot(a, b)
            if e <= 0.62:
                c.set(x, y, hr[0])
            elif e <= 1.0:
                c.set(x, y, hr[5] if b > -0.2 else hr[4])
    # a cold fleck or two of mineral in the stone
    for (x, y) in ((6, 9), (3, 12)):
        if c.get(x, y)[3]:
            c.set(x, y, col("steel", 5))
    return _outline(c)


# ---------------------------------------------------------------------------------------------------------------------
# pells_spurs

STAR = {(-2, -2): 4, (0, -2): 5, (2, -2): 3, (-1, -1): 5, (0, -1): 4, (1, -1): 3,
        (-2, 0): 4, (-1, 0): 4, (0, 0): None, (1, 0): 3, (2, 0): 2,
        (-1, 1): 3, (0, 1): 3, (1, 1): 2, (-2, 2): 3, (0, 2): 2, (2, 2): 1}   # a rowel: eight points round a pivot


def pells_spurs() -> Canvas:
    """One spur drawn bold on the diagonal (a pair, as the name says, will not read at 16 px: two of them overlap into a
    lump): the heel band at the lower left open where the straps buckle, a slim neck, a star rowel at the upper right."""
    c = Canvas()
    br = [col("brass", i) for i in range(6)]
    lt = [col("leather", i) for i in range(6)]
    cx, cy = 5.2, 10.2
    for y in range(5, 15):
        for x in range(1, 11):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if 1.9 <= d <= 3.9:
                if dx <= -1.2 and dy >= 1.2 and abs(abs(dx) - abs(dy)) <= 1.7:
                    continue                                        # open at the front, where the straps buckle
                lit = -dx * 0.7 - dy * 0.7
                i = 5 if lit > 2.4 else 4 if lit > 1.0 else 3 if lit > -0.8 else 2 if lit > -2.0 else 1
                c.set(x, y, br[i])
    for (x, y, i) in ((1, 12, 4), (2, 13, 3)):                      # strap stubs at the open end
        c.set(x, y, lt[i])
    c.set(1, 11, br[5])                                             # ... and the buckle's bit of brass
    for (x, y, i) in ((8, 8, 3), (8, 7, 4), (9, 6, 4), (9, 5, 3)):  # the neck: a slim arc climbing from the band's back
        c.set(x, y, br[i])
    for (dx, dy), i in STAR.items():
        if (dx, dy) == (2, 0):
            c.set(11 + 1, 3 + 1, br[1])                             # one point is bent down: the road has not been kind
            continue
        c.set(11 + dx, 3 + dy, br[1] if i is None else br[i])
    # wear: verdigris in the band's seams, a few dull flecks and a nick out of the rim
    for (x, y) in ((8, 9), (6, 13)):
        c.set(x, y, hex_rgba("#3f7a68"))
    for (x, y) in ((5, 7), (4, 12), (9, 10)):
        if c.get(x, y)[3]:
            c.set(x, y, br[2])
    return _outline(c)


# ---------------------------------------------------------------------------------------------------------------------
# pells_letter

def pells_letter() -> Canvas:
    c = Canvas()
    p = [col("paper", i) for i in range(6)]
    bl = [col("blue", i) for i in range(6)]
    x0, x1, y0, y1 = 1, 14, 4, 12
    # the folded letter seen from the back: the flap is the V from the two top corners down to the point
    left = {1: 4, 2: 5, 3: 6, 4: 7, 5: 7, 6: 8, 7: 9}         # x -> y of the fold line on the left
    right = {14 - (x - 1): y for x, y in left.items()}         # and its mirror on the right
    line_px = {(x, y) for x, y in left.items()} | {(x, y) for x, y in right.items()}
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            i = 3 + (1 if x < 4 else 0) - (1 if x > 11 or y > 10 else 0)
            c.set(x, y, p[i])
    # flap: lighter than the body; its edge a dark fold, with the shadow it throws on the body below
    for y in range(y0, 10):
        xs = [x for x in range(x0, x1 + 1) if (x, y) not in line_px and
              any((lx, y) in line_px for lx in range(x0, x)) and any((rx, y) in line_px for rx in range(x + 1, x1 + 1))]
        for x in xs:
            c.set(x, y, p[5] if y == y0 + 1 else p[4])
    for x in range(x0, x1 + 1):
        c.set(x, y0, p[5])                                     # the top edge catches the light
    for (x, y) in line_px:
        c.set(x, y, p[1] if (x + y) % 3 else p[0])
        if y + 1 <= y1 and (x, y + 1) not in line_px:
            c.set(x, y + 1, p[2])
    # torn corner at the bottom left, a frayed notch on the right edge
    for (x, y) in ((1, 12), (2, 12), (1, 11)):
        c.set(x, y, (0, 0, 0, 0))
    c.set(14, 8, (0, 0, 0, 0)); c.set(14, 9, p[1])
    # the Runner's hand under the fold at the left: a line and half a line
    ink = col("ink", 1)
    for x in range(2, 6):
        c.set(x, 10, ink if x != 4 else col("ink", 2))
    for x in range(3, 5):
        c.set(x, 11, col("ink", 2))
    # blue wax over the flap's point, with a bell's dot pressed in it
    seal = {(7, 8): 4, (8, 8): 3, (6, 9): 3, (7, 9): 4, (8, 9): 3, (9, 9): 2, (6, 10): 2, (7, 10): 3, (8, 10): 2, (9, 10): 1,
            (7, 11): 1, (8, 11): 1}
    for (x, y), i in seal.items():
        c.set(x, y, bl[i])
    c.set(7, 8, bl[5]); c.set(6, 9, bl[4]); c.set(8, 9, bl[1]); c.set(7, 10, bl[4]); c.set(8, 10, bl[1])
    # the road: a rust-brown thumbprint at the lower right, dust in the paper
    for (x, y, i) in ((11, 10, 1), (12, 10, 0), (11, 11, 0), (12, 11, 1), (13, 11, 0), (12, 12, 0), (13, 12, 1), (11, 12, 1)):
        c.set(x, y, hex_rgba(LOCAL["dust"][i]))
    for (x, y) in ((10, 5), (3, 7), (12, 8)):
        c.set(x, y, p[2])
    return _outline(c)


ITEMS = {"shepherds_lantern": shepherds_lantern, "lumen_tonic": lumen_tonic, "wrens_hook": wrens_hook,
         "caravan_writ": caravan_writ, "herdcaller_horn": herdcaller_horn, "pells_spurs": pells_spurs,
         "pells_letter": pells_letter}


def build(out):
    for name, fn in ITEMS.items():
        out.item(name, fn())


# ---------------------------------------------------------------------------------------------------------------------
# Preview and checks

def check(images=None):
    images = images or {n: f().image() for n, f in ITEMS.items()}
    assert len(images) == 7
    seen = {}
    for n, im in images.items():
        assert im.size == (16, 16) and im.mode == "RGBA", n
        raw = im.tobytes()
        px = list(zip(*[iter(raw)] * 4))
        solid = [p for p in px if p[3] > 0]
        assert all(p[3] == 255 for p in solid), f"{n} has partial alpha"
        assert 55 <= len(solid) <= 210, f"{n} fills {len(solid)} px"
        colours = {p for p in solid}
        assert len(colours) >= 9, f"{n} only has {len(colours)} colours"
        h = raw
        assert h not in seen, f"{n} is identical to {seen[h]}"
        seen[h] = n
        print(f"  {n:18s} {len(solid):3d} px  {len(colours):2d} colours")
    print("items ok: 16x16, opaque pixels only, distinct, every one multi-tone")


def preview(directory):
    from PIL import Image
    os.makedirs(directory, exist_ok=True)
    images = {n: f().image() for n, f in ITEMS.items()}
    check(images)
    scale, pad = 14, 10
    w = len(images) * (16 * scale + pad) + pad
    for bg, name in (((74, 78, 90, 255), "items_living_grey.png"), ((14, 16, 24, 255), "items_living_night.png")):
        sheet = Image.new("RGBA", (w, 16 * scale + 2 * pad), bg)
        for i, im in enumerate(images.values()):
            big = im.resize((16 * scale, 16 * scale), Image.NEAREST)
            sheet.paste(big, (pad + i * (16 * scale + pad), pad), big)
        sheet.save(os.path.join(directory, name))
    # and at the size they have in a hotbar slot (x2) next to a few neighbours
    strip = Image.new("RGBA", (len(images) * 40 + 8, 48), (139, 139, 139, 255))
    for i, im in enumerate(images.values()):
        big = im.resize((32, 32), Image.NEAREST)
        strip.paste(big, (8 + i * 40, 8), big)
    strip.save(os.path.join(directory, "items_living_x2.png"))
    print("preview written to", directory)


if __name__ == "__main__":
    if len(sys.argv) >= 2 and sys.argv[1] == "preview":
        preview(sys.argv[2] if len(sys.argv) > 2 else ".")
    else:
        check()
