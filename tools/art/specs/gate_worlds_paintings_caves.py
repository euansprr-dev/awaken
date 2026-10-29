"""Gate Worlds, the Atlas paintings of the cave themes (SPEC §38.10): the Fungal Hollow, the Ember Mines, the Hive and the
Overgrown Temple, five worlds each, painted the same way as the Bell Crypt's (gate_worlds_paintings.py).

    python3 tools/art/build_all.py gate_worlds_paintings_caves     textures/gui/gate_atlas/<theme>/<world>.png
    python3 tools/art/specs/gate_worlds_paintings_caves.py sheet PATH   every painting at 4x, named
"""
from __future__ import annotations

import math
import os
import random
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gate_worlds_paintings import Pic, _Img, W, H  # noqa: E402
from PIL import Image  # noqa: E402


# ---- shapes --------------------------------------------------------------------------------------------------------

def mushroom(p, cx, base, h, r, stem="#b0a7b4", cap="#5d2274", gill="#6fe0c8", glow=0.5, lean=0.0):
    """A glowcap: a pale stem leaning a little, a broad cap, its gills glowing underneath."""
    sw = max(0.8, r * 0.16)
    top = base - h
    p.poly([(cx - sw, base), (cx + sw, base), (cx + lean + sw * 0.8, top), (cx + lean - sw * 0.8, top)], stem)
    p.ellipse(cx + lean, top + r * 0.12, r * 0.95, r * 0.18, gill, 0.9)
    p.glow(cx + lean, top + r * 0.2, r * 0.9, gill, glow, squash=0.35)
    p.poly([(cx + lean - r, top + r * 0.1), (cx + lean - r * 0.7, top - r * 0.32), (cx + lean, top - r * 0.45),
            (cx + lean + r * 0.7, top - r * 0.32), (cx + lean + r, top + r * 0.1)], cap)
    for k in range(4):
        a = (k + 0.5) / 4
        p.ellipse(cx + lean - r * 0.6 + a * r * 1.2, top - r * 0.22 + (k % 2) * r * 0.1, r * 0.09, r * 0.06, "#c8a0d8", 0.8)


def hexagon(cx, cy, r, rot=0.0):
    return [(cx + math.cos(rot + k * math.pi / 3) * r, cy + math.sin(rot + k * math.pi / 3) * r) for k in range(6)]


def motes(p, seed, n, colour, box=(0, 0, W, H), size=(0.4, 0.8), strength=(0.3, 0.8)):
    rng = random.Random(seed)
    for _ in range(n):
        p.glow(rng.uniform(box[0], box[2]), rng.uniform(box[1], box[3]), rng.uniform(*size), colour, rng.uniform(*strength))


def finish(p, seed, top=0.35, bottom=0.2, sides=0.35):
    p.shade(top=top, bottom=bottom, sides=sides)
    p.grain(seed)
    return p.image()


# ---- the Fungal Hollow ----------------------------------------------------------------------------------------------

def glowing_deep():
    """A black lake under glowcaps, the glowing falls pouring into it at the back."""
    p = Pic()
    p.vgrad([(0, "#06040a"), (0.5, "#0e0a16"), (0.62, "#081416"), (1, "#020406")])
    p.ellipse(64, 6, 70, 22, "#120c1a")
    # the falls: a curtain from the roof into the lake, glowing teal
    p.rect(58, 4, 70, 48, "#3aa89a", 0.75)
    for x in (59.5, 62, 64.5, 67, 69):
        p.line([(x, 4), (x + 0.3, 48)], "#c8fff0", 0.4, 0.6)
    p.glow(64, 30, 18, "#6fe0c8", 0.6, squash=1.6)
    p.glow(64, 48, 12, "#c8fff0", 0.5, squash=0.3)
    # the lake and its reflections
    p.rect(0, 46, 128, 72, "#040a0c")
    p.rect(58, 48, 70, 70, "#2a8a80", 0.25)
    for i in range(7):
        p.line([(10 + i * 16, 50 + i % 3 * 6), (24 + i * 16, 50 + i % 3 * 6)], "#1a4a48", 0.3, 0.6)
    # glowcaps round the shore, tall at the sides
    for cx, base, h, r in ((14, 50, 34, 14), (36, 48, 20, 8), (94, 48, 22, 9), (116, 52, 38, 15), (48, 47, 11, 5), (82, 47, 12, 5)):
        mushroom(p, cx, base, h, r, glow=0.45)
        p.ellipse(cx, base + 3, r * 0.6, 1.2, "#6fe0c8", 0.15)
    motes(p, 3, 40, "#8ff0d8", (0, 4, W, 46))
    return finish(p, 21)


def root_warrens():
    """A tight tunnel through roots, receding, the roots' tips glowing as they hang."""
    p = Pic()
    p.vgrad([(0, "#0a0610"), (1, "#05030a")])
    for i in range(7):
        t = i / 7
        rx, ry = 70 - t * 58, 40 - t * 32
        col = np.array([0.20, 0.14, 0.25]) * (1 - t * 0.7)
        p.ellipse(64, 36 + t * 2, rx, ry, col)
        p.ellipse(64, 36 + t * 2, rx * 0.9, ry * 0.88, np.array([0.06, 0.04, 0.08]) * (1 - t * 0.5))
    p.glow(64, 38, 10, "#2a7a72", 0.5)
    rng = random.Random(4)
    for _ in range(40):
        a = rng.uniform(math.pi * 1.05, math.pi * 1.95)
        t = rng.uniform(0, 0.8)
        rx, ry = 70 - t * 58, 40 - t * 32
        x, y = 64 + math.cos(a) * rx * 0.9, 36 + math.sin(a) * ry * 0.88
        L = rng.uniform(3, 12) * (1 - t)
        p.line([(x, y), (x + rng.uniform(-1, 1), y + L)], "#4a6a74", 0.5 * (1 - t) + 0.25)
        p.glow(x, y + L, 0.9 * (1 - t) + 0.3, "#8ff0d8", 0.8)
    p.rect(0, 62, 128, 72, "#0c0810")
    return finish(p, 22, sides=0.5)


def spore_canopy():
    """A forest of glowcaps forty high, the near ones' caps overhead, spores drifting through the light under them."""
    p = Pic()
    p.vgrad([(0, "#08060e"), (0.7, "#101422"), (1, "#05070a")])
    for i, (cx, h, r) in enumerate(((20, 30, 9), (46, 26, 8), (78, 32, 10), (104, 27, 8))):
        mushroom(p, cx, 58, h, r, stem="#4a4450", cap="#2a1636", gill="#3a8a80", glow=0.25)
    for cx, h, r in ((8, 62, 28), (122, 58, 26), (64, 40, 18)):
        mushroom(p, cx, 74, h, r, glow=0.55)
    p.rect(0, 64, 128, 72, "#0a1414")
    for x in range(0, 128, 5):
        p.glow(x + 2, 66, 0.8, "#6fe0c8", 0.3)
    motes(p, 5, 60, "#c8fff0", (0, 10, W, 64), size=(0.3, 0.6))
    return finish(p, 23, top=0.2)


def inside_the_bloom():
    """Looking up the inside of one enormous fungus: gill fins all round, converging on a bright heart."""
    p = Pic()
    p.vgrad([(0, "#1a0a22"), (1, "#0a040e")])
    cx, cy = 64, 30
    for k in range(36):
        a = k / 36 * math.tau
        ca, sa = math.cos(a), math.sin(a)
        col = "#5d2274" if k % 2 else "#3d1652"
        p.poly([(cx + ca * 4, cy + sa * 3), (cx + ca * 110 - sa * 6, cy + sa * 80 + ca * 6),
                (cx + ca * 110 + sa * 6, cy + sa * 80 - ca * 6)], col)
        p.line([(cx + ca * 6, cy + sa * 4), (cx + ca * 90, cy + sa * 66)], "#d890e8", 0.35, 0.5)
    p.glow(cx, cy, 22, "#d890e8", 0.6)
    p.glow(cx, cy, 6, "#fff0ff", 0.9)
    motes(p, 6, 70, "#f0c0ff", size=(0.3, 0.7))
    return finish(p, 24, top=0.1, bottom=0.3)


def mycelium_mind():
    """A dark room laced with threads of light from wall to wall, a small gilled cap at the middle of it all."""
    p = Pic()
    p.vgrad([(0, "#040608"), (1, "#020304")])
    p.ellipse(64, 38, 70, 40, "#0a1012")
    rng = random.Random(7)
    nodes = [(rng.uniform(4, 124), rng.uniform(4, 68)) for _ in range(26)] + [(64, 42)]
    for i, a in enumerate(nodes):
        for b in sorted(nodes, key=lambda q: (q[0] - a[0]) ** 2 + (q[1] - a[1]) ** 2)[1:3]:
            p.line([a, b], "#6fe0c8", 0.35, 0.55)
    for x, y in nodes:
        p.glow(x, y, 1.6, "#a8f8e8", 0.6)
    mushroom(p, 64, 52, 10, 7, stem="#c8c0ca", cap="#2a7686", gill="#c8fff0", glow=0.8)
    p.glow(64, 44, 16, "#6fe0c8", 0.35)
    return finish(p, 25, sides=0.5)


# ---- the Ember Mines ------------------------------------------------------------------------------------------------

def working_mine():
    """Down a timbered gallery: rails running away, lamps on the posts, a cart left on the line."""
    p = Pic()
    p.vgrad([(0, "#0c0806"), (1, "#060403")])
    p.rect(40, 18, 88, 52, "#1a120c")
    p.glow(64, 36, 8, "#ff9a30", 0.4)
    for i in range(6):
        t = 1 - i / 6
        hw, top, bot = 8 + t * 56, 32 - t * 28, 40 + t * 30
        col = np.array([0.33, 0.21, 0.12]) * (0.35 + 0.65 * t)
        p.rect(64 - hw - 2 * t, top, 64 - hw + 2.2 * t, bot, col)
        p.rect(64 + hw - 2.2 * t, top, 64 + hw + 2 * t, bot, col)
        p.rect(64 - hw - 2 * t, top - 2.5 * t, 64 + hw + 2 * t, top + 0.6, col)
        p.glow(64 - hw + 3 * t, top + 5 * t, 2 + 3 * t, "#ffd27a", 0.8 * t + 0.2)
    for side in (-1, 1):
        p.line([(64 + side * 2, 40), (64 + side * 22, 72)], "#5a5a64", 0.8)
    for i in range(8):
        y = 41 + i ** 1.6 * 1.2
        w = 2 + (y - 40) * 0.7
        p.line([(64 - w, y), (64 + w, y)], "#3a2a1a", 0.6)
    p.poly([(70, 50), (86, 50), (84, 60), (72, 60)], "#2a2a30")
    p.rect(70, 48, 86, 50.5, "#3c3c44")
    p.ellipse(74, 61, 2, 2, "#141418")
    p.ellipse(82, 61, 2, 2, "#141418")
    motes(p, 8, 25, "#ffc060", (20, 10, 108, 60), size=(0.3, 0.5), strength=(0.2, 0.5))
    return finish(p, 26)


def open_chasm():
    """Across a deep chasm on a rope bridge, a river of lava glowing far below between basalt walls."""
    p = Pic()
    p.vgrad([(0, "#0a0606"), (0.6, "#2a0e06"), (1, "#5a1a06")])
    p.poly([(52, 72), (76, 72), (70, 20), (58, 20)], "#ff6a1a")
    p.glow(64, 66, 26, "#ff5a10", 0.9, squash=0.5)
    p.glow(64, 70, 8, "#ffd27a", 0.7)
    for side, x0 in ((-1, 0), (1, 128)):
        pts = [(x0, 0), (x0, 72)]
        for i in range(10):
            y = 72 - i * 8
            pts.append((64 + side * (10 + i * 0.6 + (i % 2) * 3), y))
        pts.append((64 + side * 22, 0))
        p.poly(pts, "#120c0c")
        for i in range(6):
            x = 64 + side * (14 + i * 7)
            p.line([(x, 0), (x + side * 2, 72)], "#1e1414", 0.6)
    p.line([(0, 30), (32, 36), (64, 38), (96, 36), (128, 30)], "#5a3a22", 1.2)
    p.line([(0, 24), (32, 30), (64, 32), (96, 30), (128, 24)], "#4a3018", 0.5)
    for x in range(4, 128, 6):
        y = 30 + 8 * math.sin(x / 128 * math.pi)
        p.line([(x, y - 6), (x, y)], "#4a3018", 0.3)
    motes(p, 9, 40, "#ffb040", (40, 20, 88, 72), size=(0.3, 0.6))
    return finish(p, 27, top=0.4)


def forge_below():
    """Lava pouring from the roof into the old forges' pool, an anvil black against it, sparks everywhere."""
    p = Pic()
    p.vgrad([(0, "#140806"), (1, "#0a0403")])
    for x, w in ((38, 5), (64, 8), (94, 4)):
        p.rect(x - w / 2, 0, x + w / 2, 56, "#ff7a20")
        p.line([(x, 0), (x, 56)], "#ffd27a", w * 0.25)
        p.glow(x, 30, w * 3, "#ff6a1a", 0.6, squash=2.5)
    p.ellipse(64, 60, 50, 7, "#ff8a2a")
    p.glow(64, 58, 30, "#ff5a10", 0.7, squash=0.4)
    p.rect(0, 62, 128, 72, "#140a08")
    p.poly([(16, 62), (30, 62), (28, 56), (34, 54), (12, 54), (18, 56)], "#0a0606")
    p.rect(100, 44, 120, 62, "#1a0e0a")
    p.ellipse(110, 50, 5, 4, "#ffb040")
    p.glow(110, 50, 8, "#ff8a2a", 0.6)
    motes(p, 10, 60, "#ffd27a", (10, 10, 118, 62), size=(0.3, 0.6))
    return finish(p, 28)


def cooling_sea():
    """A sea of glowing magma broken into crust plates to the horizon, and the back of something huge rising through it."""
    p = Pic()
    p.vgrad([(0, "#140806"), (0.45, "#3a1206"), (0.5, "#ff7a20"), (1, "#7a2208")])
    rng = random.Random(11)
    for i in range(40):
        y = 38 + i ** 1.2 * 0.75
        x = rng.uniform(-10, 130)
        w = 4 + (y - 36) * 0.9
        p.ellipse(x, y, w, w * 0.18, "#2a120a")
    for x, y, r in ((20, 50, 10), (100, 46, 7)):
        p.ellipse(x, y, r, r * 0.3, "#1a0c08")
        p.rect(x - 1, y - 6, x + 1, y, "#3a2414")
    # the wyrm's back: two coils arching out of the magma, spines along them, the crust running off it glowing
    for c0, c1, top in ((40, 62, 30), (70, 92, 34)):
        for i in range(25):
            t = i / 24
            x = c0 + (c1 - c0) * t
            y = 46 - (46 - top) * math.sin(t * math.pi)
            r = 3.6 * (0.7 + 0.3 * math.sin(t * math.pi))
            p.ellipse(x, y, r, r, "#140a08")
            if i % 3 == 1:
                p.poly([(x - 1.2, y - r + 0.5), (x + 0.6, y - r - 3.2), (x + 1.6, y - r + 0.5)], "#2a1208")
            if i % 4 == 0:
                p.glow(x, y + r * 0.6, 1.2, "#ff9a30", 0.5)
        p.ellipse(c0, 46, 6, 1.6, "#ffb040", 0.6)
        p.ellipse(c1, 46, 6, 1.6, "#ffb040", 0.6)
    p.glow(64, 44, 26, "#ff6a1a", 0.4, squash=0.5)
    motes(p, 12, 30, "#ffd27a", (0, 20, W, 50), size=(0.3, 0.5))
    return finish(p, 29, top=0.5)


def heart_seam():
    """Inside a geode of ember: crystals pointing in from every side at a glowing heart."""
    p = Pic()
    p.vgrad([(0, "#1a0806"), (1, "#0a0403")])
    p.ellipse(64, 36, 66, 40, "#2a0e08")
    rng = random.Random(13)
    for _ in range(46):
        a = rng.uniform(0, math.tau)
        R = rng.uniform(40, 62)
        x, y = 64 + math.cos(a) * R, 36 + math.sin(a) * R * 0.62
        L = rng.uniform(8, 18)
        tx, ty = x - math.cos(a) * L, y - math.sin(a) * L * 0.62
        nx, ny = -math.sin(a) * 2.5, math.cos(a) * 1.6
        col = ["#c8400e", "#ff9a30", "#ffd27a"][rng.randrange(3)]
        p.poly([(x + nx, y + ny), (tx, ty), (x - nx, y - ny)], col)
    p.glow(64, 36, 16, "#ff9a30", 0.7)
    p.ellipse(64, 36, 5, 6, "#ffd27a")
    p.glow(64, 36, 5, "#fff2c8", 0.9)
    return finish(p, 30, sides=0.5)


# ---- the Hive -------------------------------------------------------------------------------------------------------

def the_comb():
    """Down a six-sided tunnel of comb, honey glowing in its cells, dripping from the roof."""
    p = Pic()
    p.vgrad([(0, "#1a1004"), (1, "#0c0802")])
    for i in range(9):
        t = i / 9
        r = 76 - t * 66
        col = np.array([0.75, 0.52, 0.12]) * (1 - t * 0.8)
        p.poly(hexagon(64, 36, r, math.pi / 6), col)
        p.poly(hexagon(64, 36, r * 0.9, math.pi / 6), np.array([0.25, 0.15, 0.04]) * (1 - t * 0.6))
    p.glow(64, 36, 9, "#ffd870", 0.6)
    rng = random.Random(15)
    for _ in range(18):
        x = rng.uniform(16, 112)
        L = rng.uniform(4, 14)
        p.line([(x, 0), (x, L)], "#d27a10", 0.6)
        p.glow(x, L + 1, 1.2, "#ffdc7a", 0.8)
    for _ in range(50):
        a = rng.uniform(0, math.tau)
        R = rng.uniform(30, 70)
        p.glow(64 + math.cos(a) * R, 36 + math.sin(a) * R * 0.8, 1.6, "#ffbe40", 0.35)
    return finish(p, 31)


def brood_hall():
    """Looking up a hive so tall it fades into its own glow, rings of wax ledges going up and up."""
    p = Pic()
    p.vgrad([(0, "#5a3a0c"), (0.4, "#2a1a06"), (1, "#100a02")])
    for i in range(9):
        t = i / 9
        y = 70 - t * 64
        w = 70 - t * 46
        p.ellipse(64, y, w, 4 - t * 2.5, "#bc8f20" if i % 2 else "#86600f")
        p.ellipse(64, y + 1, w * 0.92, 3 - t * 2, "#1c1006")
    for x in range(0, 128, 6):
        for y in range(0, 72, 5):
            if abs(x - 64) > 26 + y * 0.4:
                p.glow(x + (y // 5 % 2) * 3, y, 1.2, "#ffbe40", 0.25)
    p.glow(64, 4, 24, "#ffdc7a", 0.6, squash=0.6)
    return finish(p, 32, top=0.0)


def honey_lake():
    """Islands of wax on a still lake of honey, glowing amber from underneath, drips falling from the comb above."""
    p = Pic()
    p.vgrad([(0, "#140c02"), (0.45, "#3a2406"), (0.5, "#b06a08"), (1, "#5a3204")])
    p.glow(64, 52, 60, "#f09a1e", 0.5, squash=0.3)
    for x, y, r in ((24, 48, 14), (80, 44, 9), (108, 54, 16), (52, 60, 10)):
        p.ellipse(x, y, r, r * 0.25, "#d4a82e")
        p.ellipse(x, y - 1, r * 0.85, r * 0.18, "#e8c04a")
        p.rect(x - r * 0.3, y - 6, x - r * 0.3 + 1.2, y - 1, "#ffe6a4")
        p.glow(x - r * 0.3 + 0.6, y - 7, 1.2, "#fff2c0", 0.8)
    rng = random.Random(17)
    for _ in range(12):
        x = rng.uniform(4, 124)
        p.line([(x, 0), (x, rng.uniform(4, 30))], "#d27a10", 0.4, 0.7)
    for k in range(10):
        p.line([(0, 44 + k * 3), (128, 44 + k * 3)], "#ffdc7a", 0.2, 0.15)
    return finish(p, 33, top=0.5)


def the_swarm():
    """Walls of comb sliding past at angles, and the swarm a golden blur between them."""
    p = Pic()
    p.vgrad([(0, "#1a1004"), (1, "#0a0602")])
    for i, (x, sk) in enumerate(((10, 8), (38, -6), (90, 7), (118, -9))):
        p.poly([(x - 14, 0), (x + 10, 0), (x + 10 + sk, 72), (x - 14 + sk, 72)], "#86600f" if i % 2 else "#a27816")
        for y in range(2, 72, 6):
            for dx in range(-10, 10, 6):
                p.glow(x + dx + sk * y / 72 + (y // 6 % 2) * 3, y, 1.4, "#ffbe40", 0.25)
    rng = random.Random(19)
    for _ in range(160):
        x, y = rng.gauss(64, 22), rng.gauss(34, 12)
        p.line([(x, y), (x + rng.uniform(1, 3), y + rng.uniform(-0.6, 0.6))], "#ffd870", 0.3, 0.7)
    p.glow(64, 34, 22, "#ffbe40", 0.4)
    return finish(p, 34)


def queens_garden():
    """A sunlit meadow inside the hive: a skylight of glowing honey above, flowers and wax candles below."""
    p = Pic()
    p.vgrad([(0, "#ffe6a4"), (0.3, "#c08a20"), (0.55, "#6a5a2a"), (1, "#3a3014")])
    p.ellipse(64, -6, 52, 22, "#fff2c0")
    p.glow(64, 4, 30, "#fff6d0", 0.6)
    p.poly([(44, 0), (84, 0), (96, 60), (32, 60)], "#fff2c0", 0.12)
    p.rect(0, 50, 128, 72, "#3a5a1e")
    rng = random.Random(21)
    for _ in range(120):
        x, y = rng.uniform(0, 128), rng.uniform(50, 72)
        col = ["#ec9cc0", "#f4c0d8", "#fff4f8", "#f8dc60"][rng.randrange(4)]
        p.ellipse(x, y, 0.8 + (y - 50) * 0.05, 0.6 + (y - 50) * 0.04, col)
    for x in (20, 46, 88, 110):
        p.rect(x - 1, 44, x + 1, 52, "#ecd486")
        p.glow(x, 42.5, 2, "#ffd060", 0.9)
    return finish(p, 35, top=0.0, bottom=0.25)


# ---- the Overgrown Temple -------------------------------------------------------------------------------------------

def jungle_courts():
    """A courtyard under a roof of leaves, columns round it, a brazier lit in the middle."""
    p = Pic()
    p.vgrad([(0, "#1a3a1a"), (0.35, "#2a5a34"), (0.7, "#2a3a2a"), (1, "#1a221c")])
    rng = random.Random(23)
    for _ in range(80):
        p.ellipse(rng.uniform(0, 128), rng.uniform(-4, 18), rng.uniform(4, 10), rng.uniform(2, 5), ["#1d421e", "#275628", "#326c32"][rng.randrange(3)])
    for i, x in enumerate((8, 30, 98, 120)):
        w = 6 if i in (0, 3) else 4
        top = 10 if i in (0, 3) else 18
        p.rect(x - w / 2, top, x + w / 2, 60, "#48574a")
        p.rect(x - w / 2 - 1, top - 2, x + w / 2 + 1, top, "#576858")
        p.line([(x - w / 2, top + 8), (x - w / 2 + 1, 50)], "#477c2e", 0.6)
    p.rect(0, 58, 128, 72, "#3c4337")
    for k in range(6):
        p.line([(0, 60 + k * 2.4), (128, 60 + k * 2.4)], "#2f3a31", 0.3)
    p.rect(58, 50, 70, 54, "#3b483d")
    p.rect(60, 54, 68, 60, "#2f3a31")
    p.glow(64, 46, 9, "#ffb040", 0.8)
    p.glow(64, 48, 3, "#fff4d0", 0.8)
    for x in range(10, 128, 14):
        p.line([(x, 0), (x + 1, rng.uniform(6, 26))], "#2b4e1c", 0.5)
    motes(p, 24, 20, "#e8f070", (0, 20, W, 60), size=(0.3, 0.5))
    return finish(p, 36, top=0.2)


def root_halls():
    """A stone hall split apart by roots as thick as towers, a glyph glowing on the far wall."""
    p = Pic()
    p.vgrad([(0, "#0e140e"), (1, "#080c08")])
    p.rect(30, 8, 98, 58, "#242d26")
    for y in range(10, 58, 6):
        p.line([(30, y), (98, y)], "#1a211c", 0.4)
    p.ellipse(64, 26, 7, 4, "#4ab8a0", 0.8)
    p.glow(64, 26, 10, "#8ae8cc", 0.6)
    p.ellipse(64, 26, 2, 2, "#d0fff0")
    for a, b, w in (((0, 10), (60, 72), 9), ((128, 4), (40, 60), 11), ((100, 0), (128, 50), 7), ((10, 72), (50, 0), 6)):
        p.line([a, b], "#4a311c", w)
        p.line([(a[0] + 2, a[1]), (b[0] + 2, b[1])], "#5a3c22", w * 0.3)
    p.rect(0, 60, 128, 72, "#1a1f18")
    motes(p, 25, 12, "#8ae8cc", (20, 10, 108, 60), size=(0.3, 0.5), strength=(0.2, 0.4))
    return finish(p, 37, sides=0.5)


def waterfall_temple():
    """A temple's stepped front beside a waterfall sixty blocks high, its spray lit by braziers."""
    p = Pic()
    p.vgrad([(0, "#2a5a5a"), (0.6, "#1e3a3a"), (1, "#0e1a1a")])
    p.rect(78, 0, 102, 58, "#58a6c4")
    for x in range(79, 102, 3):
        p.line([(x, 0), (x + 0.5, 58)], "#bce6f4", 0.5, 0.7)
    p.glow(90, 58, 18, "#bce6f4", 0.6, squash=0.4)
    p.ellipse(90, 62, 30, 6, "#3a86a6")
    for i in range(5):
        y = 58 - i * 7
        p.rect(8 + i * 5, y, 62 - i * 5, y + 7, "#48574a" if i % 2 else "#3b483d")
    p.rect(24, 16, 46, 24, "#2f3a31")
    p.poly([(20, 16), (35, 8), (50, 16)], "#576858")
    for x in (14, 56):
        p.glow(x, 54, 4, "#ffb040", 0.8)
    p.rect(0, 64, 128, 72, "#1a221c")
    return finish(p, 38, top=0.15)


def great_tree():
    """One enormous tree with a temple inside it, its windows lit, roots spreading across the ground."""
    p = Pic()
    p.vgrad([(0, "#2e5a2a"), (0.5, "#1a3018"), (1, "#0e160c")])
    rng = random.Random(27)
    for _ in range(60):
        p.ellipse(rng.uniform(0, 128), rng.uniform(-6, 20), rng.uniform(8, 18), rng.uniform(4, 8), ["#1d421e", "#275628", "#326c32", "#153016"][rng.randrange(4)])
    p.poly([(44, 72), (50, 10), (78, 10), (84, 72)], "#3a2616")
    p.line([(54, 12), (52, 70)], "#2b1c10", 1)
    p.line([(72, 12), (76, 70)], "#2b1c10", 1)
    for a, b in (((44, 70), (4, 72)), ((84, 70), (124, 72)), ((48, 66), (16, 64)), ((80, 66), (110, 62))):
        p.line([a, b], "#3a2616", 3)
    for x, y in ((58, 24), (70, 32), (60, 44), (68, 54)):
        p.rect(x - 2, y - 3, x + 2, y + 3, "#ffb040")
        p.glow(x, y, 5, "#ffb040", 0.5)
    p.poly([(60, 72), (64, 62), (68, 72)], "#ffd870")
    p.glow(64, 68, 7, "#ffd870", 0.6)
    motes(p, 28, 25, "#e8f070", (0, 10, W, 64), size=(0.3, 0.5))
    return finish(p, 39, top=0.1)


def sleeping_idol():
    """A stone face as big as a hill, its eyes shut and glowing at the seams, a lit doorway where its mouth is."""
    p = Pic()
    p.vgrad([(0, "#0e1a14"), (1, "#060c08")])
    p.ellipse(64, 46, 54, 44, "#3b483d")
    p.ellipse(64, 44, 50, 40, "#48574a")
    p.rect(14, 22, 114, 26, "#2f3a31")
    for x in (44, 84):
        p.line([(x - 12, 32), (x + 12, 32)], "#1a211c", 1.4)
        p.line([(x - 10, 32.6), (x + 10, 32.6)], "#8ae8cc", 0.4)
        p.glow(x, 33, 8, "#4ab8a0", 0.4, squash=0.3)
    p.poly([(62, 34), (66, 34), (68, 48), (60, 48)], "#3b483d")
    p.rect(54, 56, 74, 72, "#1a1408")
    p.ellipse(64, 56, 10, 4, "#1a1408")
    p.glow(64, 66, 10, "#ffb040", 0.6)
    rng = random.Random(29)
    for _ in range(26):
        x = rng.uniform(14, 114)
        p.line([(x, 2 + rng.uniform(0, 14)), (x + rng.uniform(-2, 2), rng.uniform(20, 50))], "#2b4e1c", 0.6)
    return finish(p, 40)


PAINTINGS = {
    "fungal_hollow/glowing_deep": glowing_deep,
    "fungal_hollow/root_warrens": root_warrens,
    "fungal_hollow/spore_canopy": spore_canopy,
    "fungal_hollow/inside_the_bloom": inside_the_bloom,
    "fungal_hollow/mycelium_mind": mycelium_mind,
    "ember_mines/working_mine": working_mine,
    "ember_mines/open_chasm": open_chasm,
    "ember_mines/forge_below": forge_below,
    "ember_mines/cooling_sea": cooling_sea,
    "ember_mines/heart_seam": heart_seam,
    "hive/the_comb": the_comb,
    "hive/brood_hall": brood_hall,
    "hive/honey_lake": honey_lake,
    "hive/the_swarm": the_swarm,
    "hive/queens_garden": queens_garden,
    "overgrown_temple/jungle_courts": jungle_courts,
    "overgrown_temple/root_halls": root_halls,
    "overgrown_temple/waterfall_temple": waterfall_temple,
    "overgrown_temple/great_tree": great_tree,
    "overgrown_temple/sleeping_idol": sleeping_idol,
}


def build(out):
    for name, fn in PAINTINGS.items():
        out.gui("gate_atlas/" + name, _Img(fn()))


def sheet(path):
    imgs = [(n, fn().resize((W * 3, H * 3), Image.Resampling.NEAREST)) for n, fn in PAINTINGS.items()]
    cols = 4
    sheet_img = Image.new("RGBA", ((W * 3 + 8) * cols, (H * 3 + 6) * ((len(imgs) + cols - 1) // cols)), (20, 20, 24, 255))
    for i, (n, im) in enumerate(imgs):
        sheet_img.paste(im, ((i % cols) * (W * 3 + 8), (i // cols) * (H * 3 + 6)))
    sheet_img.save(path)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "sheet":
        sheet(sys.argv[2])
