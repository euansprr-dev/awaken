"""Gate Worlds, the Atlas paintings of the D themes (SPEC §38.11): the Drowned Archive, the Clockwork Foundry, the Glass
Desert and the Lantern Night Market, five worlds each, painted the same way as the Bell Crypt's (gate_worlds_paintings.py).

    python3 tools/art/build_all.py gate_worlds_paintings_d     textures/gui/gate_atlas/<theme>/<world>.png
    python3 tools/art/specs/gate_worlds_paintings_d.py sheet PATH   every painting at 3x, named
"""
from __future__ import annotations

import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from gate_worlds_paintings import Pic, _Img, W, H  # noqa: E402
from gate_worlds_paintings_caves import motes, finish  # noqa: E402
from PIL import Image  # noqa: E402


# ---- shapes --------------------------------------------------------------------------------------------------------

def shelf(p, x0, x1, top, bottom, wood="#3a2a1c", books=("#6a3a2a", "#2a4a5a", "#5a4a2a", "#3a2a4a"), seed=0, rows=4):
    """A bookcase face: a dark frame, rows of book spines in muted colours."""
    rng = random.Random(seed)
    p.rect(x0, top, x1, bottom, wood)
    step = (bottom - top) / rows
    for r in range(rows):
        y0, y1 = top + r * step + 0.6, top + (r + 1) * step - 0.4
        x = x0 + 0.6
        while x < x1 - 0.8:
            w = rng.uniform(0.6, 1.4)
            p.rect(x, y0 + rng.uniform(0, 0.8), min(x + w, x1 - 0.6), y1, books[rng.randrange(len(books))], 0.9)
            x += w + 0.15


def gear(p, cx, cy, r, colour, teeth=12, hub="#1a140c", rot=0.0, alpha=1.0):
    """A gear: a toothed ring round a dark hub with spokes."""
    pts = []
    for k in range(teeth * 2):
        a = rot + k * math.pi / teeth
        rr = r if k % 2 == 0 else r * 0.86
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    p.poly(pts, colour, alpha)
    p.ellipse(cx, cy, r * 0.7, r * 0.7, hub, alpha)
    for k in range(4):
        a = rot + k * math.pi / 2
        p.line([(cx, cy), (cx + math.cos(a) * r * 0.7, cy + math.sin(a) * r * 0.7)], colour, max(0.6, r * 0.1), alpha)
    p.ellipse(cx, cy, r * 0.18, r * 0.18, colour, alpha)


def lantern(p, cx, cy, r, colour="#ff9a40", body="#c8402a", glow=0.5):
    """A paper lantern: a red-orange body lit from inside, a dark cap and tassel."""
    p.glow(cx, cy, r * 3.2, colour, glow)
    p.ellipse(cx, cy, r, r * 1.2, body)
    p.ellipse(cx, cy, r * 0.65, r * 0.9, colour, 0.8)
    p.rect(cx - r * 0.5, cy - r * 1.35, cx + r * 0.5, cy - r * 1.05, "#2a1a14")
    p.line([(cx, cy + r * 1.2), (cx, cy + r * 1.8)], "#e8c070", 0.3)


def column(p, x, top, bottom, w, colour, shade="#00000000"):
    p.rect(x - w, top, x + w, bottom, colour)
    p.rect(x - w * 1.4, top - 1.2, x + w * 1.4, top, colour)
    p.rect(x - w * 1.4, bottom - 1, x + w * 1.4, bottom, colour)


def dune(p, y, amp, colour, seed, alpha=1.0):
    rng = random.Random(seed)
    ph = rng.uniform(0, 6.28)
    pts = [(x, y + math.sin(x / 18 + ph) * amp + math.sin(x / 7 + ph * 2) * amp * 0.3) for x in range(0, W + 2, 2)]
    p.poly(pts + [(W, H), (0, H)], colour, alpha)


def dome(p, cx, base, r, colour, h=None):
    h = r if h is None else h
    p.poly([(cx - r + 2 * r * k / 16, base - math.sin(math.pi * k / 16) * h) for k in range(17)], colour)


def stars(p, seed, n, box=(0, 0, W, 30)):
    motes(p, seed, n, "#e8e8ff", box, (0.25, 0.5), (0.3, 0.9))


# ---- the Drowned Archive --------------------------------------------------------------------------------------------

def stacks():
    """Shelves three storeys high down both sides, a gallery rail across, lamps, still water on the floor."""
    p = Pic()
    p.vgrad([(0, "#060a0c"), (0.6, "#0c1618"), (1, "#04080a")])
    for i, (x0, x1) in enumerate(((0, 30), (98, 128))):
        shelf(p, x0, x1, 2, 56, seed=1 + i, rows=9)
    for i, (x0, x1) in enumerate(((32, 46), (82, 96))):
        shelf(p, x0, x1, 12, 50, seed=3 + i, rows=6)
    shelf(p, 48, 80, 20, 46, seed=5, rows=4)
    for y in (20, 38):
        p.line([(0, y), (128, y)], "#2a1a10", 1.0)
        p.line([(0, y - 2.5), (128, y - 2.5)], "#4a3420", 0.4)
    for x, y in ((16, 30), (112, 30), (39, 44), (89, 44), (64, 34)):
        p.glow(x, y, 9, "#9ff0d8", 0.45)
        p.ellipse(x, y, 1.0, 1.4, "#e8fff4")
    p.rect(0, 56, 128, 72, "#061214")
    p.rect(0, 56, 128, 58, "#2a6a70", 0.25)
    for x, _ in ((16, 0), (112, 0), (64, 0)):
        p.line([(x - 4, 62), (x + 4, 62)], "#7fe0d0", 0.4, 0.4)
    motes(p, 7, 30, "#efe8d2", (0, 0, W, 56), (0.3, 0.5), (0.2, 0.5))
    return finish(p, 41)


def atrium():
    """A dome of sea glass, light falling through it onto clear water and a ring of columns."""
    p = Pic()
    p.vgrad([(0, "#0e2a30"), (0.5, "#0a1a1e"), (1, "#041014")])
    p.ellipse(64, 30, 70, 40, "#12343a")
    for k in range(9):
        a = math.pi * (k + 0.5) / 9
        p.line([(64, 30 - 36), (64 - math.cos(a) * 66, 30 - math.sin(a) * 4)], "#0a1a1e", 0.6)
    p.ellipse(64, 4, 24, 10, "#7fe0d0", 0.5)
    p.glow(64, 6, 22, "#bff8ec", 0.5, squash=0.6)
    p.poly([(52, 4), (76, 4), (86, 60), (42, 60)], "#9ff0e0", 0.12, blur=1.0)
    for x in (10, 30, 98, 118):
        column(p, x, 22, 52, 2.2, "#3a5a5a")
    p.rect(0, 52, 128, 72, "#0a3036")
    p.rect(40, 54, 88, 70, "#5fd0c0", 0.25)
    p.ellipse(64, 60, 4, 1.2, "#e8d080", 0.8)
    p.glow(64, 60, 6, "#ffe0a0", 0.4)
    motes(p, 9, 24, "#bff8ec", (40, 4, 88, 56), (0.3, 0.5), (0.2, 0.5))
    return finish(p, 42)


def sunken_shelves():
    """Underwater: shelves standing on the sea floor, their lamps still burning, light rays from far above."""
    p = Pic()
    p.vgrad([(0, "#2a7a90"), (0.4, "#0e3a4a"), (1, "#041018")])
    for x in (20, 46, 78, 104):
        p.poly([(x - 3, 0), (x + 3, 0), (x + 12, 72), (x + 2, 72)], "#9ff0f0", 0.08, blur=1.0)
    for i, (x, top, w) in enumerate(((10, 24, 12), (36, 34, 9), (82, 30, 10), (108, 20, 14))):
        shelf(p, x, x + w, top, 60, wood="#1a2a2c", books=("#2a4a4a", "#3a3a4a", "#2a3a2a"), seed=11 + i, rows=5)
        p.glow(x + w / 2, top - 1.5, 7, "#9ff0d8", 0.5)
        p.ellipse(x + w / 2, top - 1.5, 0.9, 1.2, "#e8fff4")
    p.rect(0, 60, 128, 72, "#0a1a1a")
    rng = random.Random(13)
    for _ in range(18):
        x = rng.uniform(4, 124)
        p.line([(x, 72), (x + rng.uniform(-2, 2), 72 - rng.uniform(6, 16))], "#1a4a3a", 0.5, 0.8)
    motes(p, 14, 26, "#d8f8ff", (0, 0, W, 60), (0.3, 0.6), (0.2, 0.6))
    return finish(p, 43, top=0.1)


def inkwell():
    """A black sea of ink, paper bridges folding out across it, ink falling from the dark, something long underneath."""
    p = Pic()
    p.vgrad([(0, "#0a0c16"), (0.55, "#141a2a"), (0.6, "#04050a"), (1, "#020306")])
    for i, (x0, x1, top) in enumerate(((0, 14, 14), (114, 128, 10), (20, 30, 26), (98, 108, 24))):
        shelf(p, x0, x1, top, 46, wood="#10121a", books=("#1a2030", "#20202a", "#1a1a24"), seed=31 + i, rows=4)
    for x, w in ((40, 2.5), (88, 3.5)):
        p.rect(x - w, 0, x + w, 50, "#1a2034", 0.9)
        for k in range(3):
            p.line([(x - w + 0.8 + k * w * 0.7, 0), (x - w + 0.8 + k * w * 0.7, 50)], "#5a6a9a", 0.3, 0.5)
        p.ellipse(x, 50, w * 3, 1.4, "#3a4a7a", 0.6)
    p.glow(64, 24, 46, "#3a4470", 0.35)
    # the paper bridge, folded out in leaves, lit from the lamps along it
    pts = [(0, 54), (32, 50), (64, 49), (96, 50), (128, 54)]
    for i in range(len(pts) - 1):
        (xa, ya), (xb, yb) = pts[i], pts[i + 1]
        for k in range(5):
            t0, t1 = k / 5, (k + 0.92) / 5
            x0, y0, x1, y1 = xa + (xb - xa) * t0, ya + (yb - ya) * t0, xa + (xb - xa) * t1, ya + (yb - ya) * t1
            p.poly([(x0, y0 - 1.2), (x1, y1 - 1.2), (x1, y1 + 1.0), (x0, y0 + 1.0)], "#efe8d2" if k % 2 == 0 else "#cfc4a4")
            p.line([(x1, y1 - 1.2), (x1 + 0.6, y1 + 1.0)], "#8a7e60", 0.3)
    for x in (16, 48, 80, 112):
        y = 49 + abs(x - 64) / 16
        p.line([(x, y - 1), (x, y - 6)], "#3a3020", 0.4)
        p.ellipse(x, y - 6.6, 0.9, 1.1, "#9ff0d8")
        p.glow(x, y - 6, 7, "#7fe0d0", 0.4)
    p.glow(64, 50, 40, "#efe8d2", 0.12, squash=0.15)
    # the worm's back breaking the ink
    for k, (x, y, r) in enumerate(((68, 64, 3.4), (76, 61, 4.2), (86, 60, 4.6), (96, 62, 3.8), (104, 66, 3.0))):
        p.ellipse(x, y, r * 1.4, r, "#1a1c28")
        p.line([(x - r, y - r * 0.6), (x + r, y - r * 0.6)], "#4a5070", 0.4, 0.7)
    p.ellipse(110, 64, 1.0, 0.7, "#7fe0d0")
    p.glow(110, 64, 4, "#7fe0d0", 0.5)
    for x in (30, 52, 122):
        p.line([(x, 66), (x + 8, 66)], "#3a4470", 0.3, 0.6)
    motes(p, 15, 22, "#efe8d2", (0, 0, W, 46), (0.3, 0.6), (0.3, 0.6))
    return finish(p, 44)


def last_readers_room():
    """A dry room inside a glass bubble on the sea floor: a desk, a lamp, a book left open, fish beyond the glass."""
    p = Pic()
    p.vgrad([(0, "#0a3040"), (1, "#020a10")])
    p.ellipse(64, 50, 58, 46, "#3a2a1a")
    p.ellipse(64, 50, 55, 43, "#2a1e12")
    p.ellipse(64, 50, 58, 46, "#7fe0d0", 0.12)
    p.line([(20, 20), (36, 8)], "#bff8ec", 0.5, 0.4)
    p.rect(44, 52, 84, 55, "#4a3420")
    p.rect(47, 55, 50, 66, "#3a2a1a")
    p.rect(78, 55, 81, 66, "#3a2a1a")
    p.poly([(56, 51), (64, 50), (72, 51), (72, 52), (56, 52)], "#efe8d2")
    p.line([(64, 50), (64, 52)], "#a89878", 0.3)
    p.rect(76, 44, 78, 52, "#5a4a3a")
    p.ellipse(77, 43, 2.0, 1.6, "#ffd890")
    p.glow(72, 46, 26, "#ffb060", 0.55)
    for x, y, d in ((14, 30, 1), (110, 22, -1), (100, 40, -1)):
        p.poly([(x, y), (x + 4 * d, y - 1.4), (x + 6 * d, y), (x + 4 * d, y + 1.4)], "#1a5a6a")
    p.rect(0, 64, 128, 72, "#100c08")
    return finish(p, 45)


# ---- the Clockwork Foundry ------------------------------------------------------------------------------------------

def works_floor():
    """A catwalk across the works, pits of turning gears glowing under the grating, pipes on the walls."""
    p = Pic()
    p.vgrad([(0, "#140e08"), (0.5, "#1e1408"), (1, "#0a0604")])
    for x in (6, 12, 116, 122):
        p.rect(x - 1.5, 0, x + 1.5, 72, "#3a3a34")
        p.line([(x - 0.5, 0), (x - 0.5, 72)], "#7a7a68", 0.3)
    p.rect(0, 44, 128, 72, "#0a0604")
    for cx, cy, r, t in ((34, 58, 12, 12), (60, 64, 9, 10), (92, 58, 14, 14), (74, 52, 6, 8)):
        p.glow(cx, cy, r * 1.6, "#ff8a30", 0.35)
        gear(p, cx, cy, r, "#8a6a30", t)
    p.rect(0, 44, 128, 46, "#5a4a30", 0.6)
    for x in range(0, 128, 3):
        p.line([(x, 44), (x, 72)], "#2a2014", 0.4, 0.5)
    p.rect(0, 40, 128, 42.5, "#6a5a3a")
    for x in range(4, 128, 10):
        p.line([(x, 34), (x, 41)], "#4a4030", 0.4)
    p.line([(0, 34), (128, 34)], "#8a7040", 0.5)
    p.glow(64, 20, 20, "#ffc070", 0.2)
    motes(p, 21, 22, "#ffb060", (0, 30, W, 72), (0.2, 0.4), (0.4, 0.9))
    return finish(p, 51)


def pipe_maze():
    """Pipes and ducts all the way up, steam out of every joint, warm light from somewhere above."""
    p = Pic()
    p.vgrad([(0, "#2a1e10"), (1, "#0a0806")])
    rng = random.Random(22)
    for i in range(14):
        x = rng.uniform(0, 128)
        w = rng.uniform(1.5, 4)
        col = rng.choice(["#4a4034", "#5a4a30", "#3a3a34", "#7a5a2a"])
        p.rect(x - w, 0, x + w, 72, col)
        p.line([(x - w * 0.5, 0), (x - w * 0.5, 72)], "#9a8a68", 0.3, 0.5)
        for _ in range(2):
            y = rng.uniform(6, 66)
            p.rect(x - w * 1.3, y, x + w * 1.3, y + 1.5, "#8a6a30")
            p.glow(x + w, y, 6, "#e8e0d8", 0.25, squash=0.6)
    for i in range(5):
        y = rng.uniform(10, 60)
        p.rect(0, y, 128, y + rng.uniform(2, 3.5), "#3a3228")
    p.glow(64, -4, 40, "#ffc070", 0.4)
    return finish(p, 52)


def great_engine():
    """A gear twenty blocks across turning overhead, iron pillars holding its axle, a tiny figure below."""
    p = Pic()
    p.vgrad([(0, "#1a1008"), (0.6, "#120a06"), (1, "#060402")])
    p.glow(64, 26, 50, "#ff9a40", 0.25)
    gear(p, 64, 26, 26, "#8a6a30", 24, "#1a120a", 0.1)
    gear(p, 22, 14, 10, "#6a5028", 12, "#140e08", 0.3)
    gear(p, 108, 12, 12, "#6a5028", 14, "#140e08", 0.2)
    for x in (58, 70):
        p.rect(x - 2, 26, x + 2, 64, "#3a3630")
    p.rect(0, 62, 128, 72, "#1a140c")
    p.rect(62.5, 58, 64.5, 62, "#0a0806")
    p.ellipse(63.5, 57.4, 0.8, 0.8, "#0a0806")
    for x in (14, 40, 88, 114):
        p.glow(x, 62, 8, "#ff7a20", 0.4, squash=0.4)
    return finish(p, 53)


def stopped_hour():
    """Something exploded and time stopped: pieces hanging in the air around a frozen flash, a clock face behind."""
    p = Pic()
    p.vgrad([(0, "#10141a"), (1, "#06080a")])
    p.ellipse(64, 30, 24, 24, "#2a2a28")
    p.ellipse(64, 30, 22, 22, "#d8d0b8", 0.18)
    for k in range(12):
        a = k * math.pi / 6
        p.line([(64 + math.cos(a) * 19, 30 + math.sin(a) * 19), (64 + math.cos(a) * 21, 30 + math.sin(a) * 21)], "#d8d0b8", 0.6, 0.5)
    p.line([(64, 30), (64, 14)], "#d8d0b8", 0.7, 0.5)
    p.line([(64, 30), (74, 34)], "#d8d0b8", 0.7, 0.5)
    p.glow(64, 40, 16, "#fff0c0", 0.9)
    rng = random.Random(24)
    for _ in range(46):
        a = rng.uniform(0, 6.28)
        d = rng.uniform(8, 60)
        x, y = 64 + math.cos(a) * d, 40 + math.sin(a) * d * 0.6
        s = rng.uniform(0.6, 2.4)
        p.poly([(x - s, y - s * 0.4), (x + s * 0.6, y - s), (x + s, y + s * 0.5), (x - s * 0.4, y + s)],
               rng.choice(["#8a6a30", "#5a5a50", "#6a4a20", "#a88a40"]))
        p.line([(x, y), (64 + math.cos(a) * (d - 4), 40 + math.sin(a) * (d - 4) * 0.6)], "#fff0c0", 0.2, 0.25)
    p.rect(0, 62, 128, 72, "#14120e")
    return finish(p, 54)


def orrery():
    """Brass planets turning round a little sun in the dark, their orbits drawn in faint light."""
    p = Pic()
    p.vgrad([(0, "#06050a"), (1, "#020204")])
    stars(p, 25, 30, (0, 0, W, H))
    for r in (12, 22, 33, 46):
        p.ellipse(64, 38, r * 1.3, r * 0.42, "#8a6a30", 0.25)
        p.ellipse(64, 38, r * 1.3 - 0.5, r * 0.42 - 0.4, "#06050a", 0.25)
    p.rect(63, 40, 65, 66, "#5a4a30")
    p.glow(64, 36, 18, "#ffb040", 0.8)
    p.ellipse(64, 36, 4, 4, "#ffe0a0")
    for (a, r, s) in ((0.5, 12, 1.6), (2.4, 22, 2.4), (4.0, 33, 3.0), (5.5, 46, 2.0)):
        x, y = 64 + math.cos(a) * r * 1.3, 38 + math.sin(a) * r * 0.42
        p.ellipse(x, y, s, s, "#b08a40")
        p.ellipse(x - s * 0.3, y - s * 0.3, s * 0.4, s * 0.4, "#f0d890", 0.7)
    p.rect(0, 66, 128, 72, "#0e0c08")
    return finish(p, 55)


# ---- the Glass Desert -----------------------------------------------------------------------------------------------

def buried_colossus():
    """Dunes under a real sun, a line of old columns, and a stone head as big as a hill looking out of the sand."""
    p = Pic()
    p.vgrad([(0, "#4a80c0"), (0.45, "#a8c8e0"), (0.55, "#e8d8b0"), (1, "#c8a060")])
    p.glow(22, 9, 14, "#fff8e0", 0.9)
    dune(p, 44, 2.5, "#d8b878", 31)
    # the head: a long weathered face, three-quarters on, lit from the sun's side and shadowed away from it
    face = [(76, 60), (74, 40), (76, 28), (82, 20), (94, 17), (106, 19), (113, 26), (115, 38), (113, 52), (108, 60)]
    p.poly(face, "#a88a60")
    p.poly([(76, 60), (74, 40), (76, 28), (82, 20), (90, 18), (88, 40), (90, 60)], "#c8a878")
    p.poly([(78, 25), (84, 18), (94, 15.5), (106, 17), (113, 23), (112, 26), (100, 22), (88, 22)], "#c89a40")
    for x in (82, 90, 98, 106):
        p.poly([(x - 2, 22 - (0 if x != 90 else 1)), (x, 14.5), (x + 2, 22)], "#d8b040")
    p.line([(80, 31), (92, 29.5)], "#6a5030", 1.0)
    p.line([(97, 29.5), (109, 31)], "#6a5030", 1.0)
    p.ellipse(86, 34, 3.6, 1.6, "#3a2818")
    p.ellipse(103, 34, 3.6, 1.6, "#3a2818")
    p.glow(86, 34, 3, "#ffe0a0", 0.35)
    p.line([(86, 36), (85.5, 46)], "#d8b040", 0.5, 0.8)
    p.poly([(93, 31), (96, 31), (99, 44), (95, 45.5), (91, 44)], "#b89868")
    p.poly([(96, 31), (99, 44), (95, 45.5)], "#8a6a48")
    p.line([(88, 50), (95, 51), (102, 50)], "#5a4028", 0.8)
    p.line([(108, 24), (104, 40), (110, 52)], "#6a5030", 0.4, 0.7)
    for i, x in enumerate((10, 20, 30, 40, 50)):
        h = 22 - i * 1.5
        column(p, x, 48 - h, 52, 1.6, "#d8c098")
    p.rect(6, 27.5, 54, 29, "#c8a878")
    p.poly([(40, 29), (54, 29), (54, 30.5), (44, 30.5)], "#b89868")
    dune(p, 56, 3, "#e0c088", 32)
    dune(p, 64, 2, "#c8a060", 33)
    return finish(p, 61, top=0.1, sides=0.25)


def glass_caves():
    """Caves where the sand turned to glass: light coming through the walls, sand pouring down in curtains."""
    p = Pic()
    p.vgrad([(0, "#1a2a2e"), (1, "#0a1012")])
    rng = random.Random(34)
    for _ in range(16):
        x = rng.uniform(0, 128)
        y = rng.uniform(0, 60)
        s = rng.uniform(6, 14)
        p.poly([(x, y - s), (x + s * 0.6, y), (x, y + s), (x - s * 0.6, y)], rng.choice(["#3aa0b0", "#8ad8d0", "#ffe0a0"]), 0.35)
    for x in (30, 96):
        p.poly([(x - 6, 0), (x + 6, 0), (x + 16, 72), (x - 4, 72)], "#ffe8b0", 0.18, blur=1.0)
        p.glow(x, 10, 12, "#fff0c0", 0.5)
    for x in (54, 76):
        p.rect(x - 1.5, 0, x + 1.5, 60, "#d8b878", 0.7)
        p.ellipse(x, 60, 5, 1.6, "#d8b878")
    p.rect(0, 60, 128, 72, "#c8a060")
    p.rect(0, 60, 128, 61, "#e8d0a0", 0.6)
    motes(p, 35, 30, "#fff0c0", (0, 0, W, 60), (0.3, 0.5), (0.3, 0.7))
    return finish(p, 62)


def sunken_city():
    """The rooftops of a whole city poking out of a basin of sand, domes and towers, the rest still below."""
    p = Pic()
    p.vgrad([(0, "#6a98c8"), (0.4, "#b0c8d8"), (0.5, "#e8d8b8"), (1, "#c8a068")])
    p.glow(100, 8, 12, "#fff8e0", 0.8)
    dune(p, 34, 1.5, "#d8b880", 36)
    rng = random.Random(37)
    for i in range(14):
        x = 6 + i * 9 + rng.uniform(-2, 2)
        h = rng.uniform(6, 16)
        base = 50 + rng.uniform(-3, 3)
        p.rect(x - 3, base - h, x + 3, base, rng.choice(["#c8906a", "#b88058", "#d8a078"]))
        p.rect(x - 1, base - h + 3, x + 1, base - h + 6, "#5a3a20")
        if rng.random() < 0.4:
            dome(p, x, base - h, 3.4, "#3a8a90")
    p.rect(60, 18, 64, 46, "#d8a078")
    dome(p, 62, 18, 4, "#3a8a90")
    dune(p, 50, 2, "#e0c088", 38)
    dune(p, 62, 2.5, "#c8a060", 39)
    return finish(p, 63, top=0.1, sides=0.25)


def mirage():
    """A palace shimmering on the far side of the sand, an empty land of heat, a little pool that mirrors the sky."""
    p = Pic()
    p.vgrad([(0, "#7aa8d8"), (0.42, "#d8e0e0"), (0.5, "#f0e0b8"), (1, "#d8b078")])
    for x, w, h in ((52, 4, 10), (64, 10, 8), (76, 4, 10), (60, 2, 14), (68, 2, 14)):
        p.rect(x - w, 34 - h, x + w, 35, "#c8b0d0", 0.5)
    dome(p, 64, 26, 5, "#c8b0d0")
    p.glow(64, 30, 26, "#fff0e0", 0.25, squash=0.4)
    for y in range(31, 39, 2):
        p.line([(30, y), (98, y)], "#f8f0e0", 0.4, 0.25)
    dune(p, 40, 1, "#e8c890", 41)
    p.ellipse(40, 56, 14, 3, "#8ab8e0")
    p.ellipse(40, 56, 12, 2.2, "#c8d8f0", 0.6)
    for x in (30, 50):
        p.line([(x, 56), (x + 1, 46)], "#7a5a3a", 0.6)
        p.ellipse(x + 1, 46, 4, 1.6, "#4a7a3a")
    dune(p, 62, 2, "#c8a068", 42)
    return finish(p, 64, top=0.1, sides=0.25)


def hourglass():
    """A little city at the bottom of an hourglass, sand falling on it in a thin bright stream from the neck above."""
    p = Pic()
    p.vgrad([(0, "#2a2014"), (1, "#0e0a06")])
    p.poly([(14, 0), (114, 0), (70, 30), (58, 30)], "#d8c098", 0.12)
    p.poly([(58, 34), (70, 34), (118, 72), (10, 72)], "#d8c098", 0.08)
    p.line([(14, 0), (58, 31), (10, 72)], "#e8d8b0", 0.5, 0.4)
    p.line([(114, 0), (70, 31), (118, 72)], "#e8d8b0", 0.5, 0.4)
    p.poly([(30, 0), (98, 0), (68, 22), (60, 22)], "#c8a060", 0.7)
    p.rect(63, 22, 65, 56, "#f0d8a0")
    p.glow(64, 40, 10, "#ffe0a0", 0.5, squash=2.0)
    rng = random.Random(43)
    for i in range(10):
        x = 22 + i * 9 + rng.uniform(-1, 1)
        h = rng.uniform(4, 9)
        p.rect(x - 2.5, 62 - h, x + 2.5, 62, "#b88058")
        p.glow(x, 62 - h / 2, 2, "#ffc070", 0.4)
        if i % 3 == 0:
            dome(p, x, 62 - h, 2.8, "#3a8a90")
    p.ellipse(64, 60, 18, 4, "#d8b878")
    p.rect(0, 62, 128, 72, "#c8a060")
    return finish(p, 65)


# ---- the Lantern Night Market ---------------------------------------------------------------------------------------

def lantern_street():
    """A canal down the middle of the street, stalls each side, strings of lanterns overhead in the night."""
    p = Pic()
    p.vgrad([(0, "#0a1030"), (0.5, "#1a1a3a"), (1, "#06080e")])
    stars(p, 51, 18, (0, 0, W, 22))
    # houses behind, receding to the vanishing point at the canal's end
    for side in (-1, 1):
        for k in range(5):
            d = 1 - k * 0.17
            x = 64 + side * (60 - k * 11)
            w = 14 * d
            top = 40 - 22 * d
            base = 40 + 20 * d
            p.rect(x - w / 2, top, x + w / 2, base, "#2a1c20" if k % 2 else "#33222a")
            p.poly([(x - w / 2 - 1.5, top), (x, top - 5 * d), (x + w / 2 + 1.5, top)], "#5a2018")
            p.rect(x - w / 4, top + 3 * d, x + w / 4, top + 7 * d, "#ffb060", 0.55)
            # the stall in front: an awning and its lit counter
            aw = base - 9 * d
            p.poly([(x - w / 2 - 1, aw), (x + w / 2 + 1, aw), (x + w / 2 + 2, aw + 3 * d), (x - w / 2 - 2, aw + 3 * d)],
                   "#a83020" if k % 2 == 0 else "#2a6a5a")
            p.rect(x - w / 2, aw + 3 * d, x + w / 2, base, "#1a1014")
            p.rect(x - w / 2 + 1, base - 3 * d, x + w / 2 - 1, base - 2 * d, "#ffc070", 0.5)
            p.glow(x, base - 3 * d, 8 * d, "#ff9a40", 0.35)
    # the canal, its water catching the lantern light
    p.poly([(34, 72), (94, 72), (68, 40), (60, 40)], "#0a1428")
    for k in range(9):
        y = 44 + k * 3.2
        hw = 4 + (y - 40) * 0.9
        p.line([(64 - hw * 0.5, y), (64 - hw * 0.1, y)], "#ff9a40", 0.35, 0.45)
        p.line([(64 + hw * 0.15, y + 1.2), (64 + hw * 0.6, y + 1.2)], "#ffc070", 0.3, 0.35)
    for y0, sag in ((10, 7), (18, 6)):
        pts = [(x, y0 + sag * math.sin(math.pi * x / 128)) for x in range(0, 129, 4)]
        p.line(pts, "#1a1410", 0.3)
        for x in range(8, 128, 12):
            lantern(p, x, y0 + sag * math.sin(math.pi * x / 128) + 2.2, 1.4, glow=0.35)
    return finish(p, 71)


def rooftops():
    """Rooftops joined by bridges, the streets lit far below, lanterns along the ridges, a sky full of stars."""
    p = Pic()
    p.vgrad([(0, "#060a20"), (0.6, "#10183a"), (1, "#04060c")])
    stars(p, 52, 40, (0, 0, W, 40))
    rng = random.Random(53)
    for i in range(9):
        x = 6 + i * 14 + rng.uniform(-2, 2)
        top = rng.uniform(30, 44)
        p.rect(x - 6, top, x + 6, 72, "#1a1418")
        p.poly([(x - 8, top), (x, top - 5), (x + 8, top)], "#4a1e18")
        lantern(p, x, top - 6.5, 1.2, glow=0.3)
        p.rect(x - 3, top + 6, x - 1, top + 9, "#ffb060", 0.6)
    for x0, x1, y in ((20, 34, 38), (62, 76, 40), (90, 104, 36)):
        p.line([(x0, y), ((x0 + x1) / 2, y + 2), (x1, y)], "#3a2418", 0.8)
    for x in range(10, 128, 14):
        p.glow(x, 70, 6, "#ff9a40", 0.25, squash=0.4)
    return finish(p, 72)


def floating_market():
    """Boats tied together on a lagoon, every one a stall with a lamp, their lights wobbling in the water."""
    p = Pic()
    p.vgrad([(0, "#060a24"), (0.48, "#141a3a"), (0.5, "#0a1020"), (1, "#04060c")])
    stars(p, 54, 30, (0, 0, W, 34))
    p.rect(0, 34, 128, 36, "#0e1018")
    rng = random.Random(55)
    for i in range(7):
        x = 10 + i * 18 + rng.uniform(-3, 3)
        y = 44 + (i % 3) * 7
        s = 0.8 + (i % 3) * 0.25
        p.poly([(x - 8 * s, y), (x + 8 * s, y), (x + 6 * s, y + 2.4 * s), (x - 6 * s, y + 2.4 * s)], "#3a2418")
        p.poly([(x - 5 * s, y), (x - 5 * s, y - 5 * s), (x, y - 7 * s), (x + 5 * s, y - 5 * s), (x + 5 * s, y)], "#4a1e18", 0.9)
        lantern(p, x, y - 3 * s, 1.3 * s, glow=0.45)
        for k in range(3):
            p.line([(x - 2, y + 4 + k * 2.5), (x + 2, y + 4 + k * 2.5)], "#ff9a40", 0.35, 0.5 - k * 0.12)
    motes(p, 56, 14, "#ffb060", (0, 36, W, 72), (0.3, 0.5), (0.3, 0.7))
    return finish(p, 73)


def festival():
    """The great lantern going up over the square, a thousand small ones rising after it, the crowd below."""
    p = Pic()
    p.vgrad([(0, "#0a0620"), (0.6, "#2a1430"), (1, "#0a0408")])
    stars(p, 57, 20)
    rng = random.Random(58)
    for _ in range(70):
        x = rng.uniform(0, 128)
        y = rng.uniform(2, 54)
        r = 0.4 + (y / 54) * 0.8
        p.glow(x, y, r * 2.2, "#ff9a40", 0.35)
        p.ellipse(x, y, r * 0.6, r * 0.8, "#ffc070")
    lantern(p, 64, 20, 6, glow=0.6)
    p.glow(64, 20, 22, "#ffb060", 0.35)
    p.rect(0, 60, 128, 72, "#0a0408")
    for i in range(40):
        x = rng.uniform(0, 128)
        p.ellipse(x, 60 + rng.uniform(-1, 1), 1.2, 1.6, "#140a10")
    p.glow(64, 66, 40, "#ff7a30", 0.2, squash=0.3)
    return finish(p, 74, top=0.15)


def brokers_vault():
    """Where every wish is kept: shelves of little lanterns glowing in a vault, gold in heaps, the ledger open."""
    p = Pic()
    p.vgrad([(0, "#1a0e08"), (1, "#080402")])
    for row in range(4):
        y = 10 + row * 12
        p.rect(4, y + 4, 124, y + 5.4, "#3a2414")
        for k in range(14):
            x = 10 + k * 8.4 + (row % 2) * 2
            lantern(p, x, y + 1.4, 1.3, colour=random.Random(row * 31 + k).choice(["#ff9a40", "#ffc070", "#ff7a50"]), glow=0.3)
    for cx, r in ((24, 10), (104, 12)):
        p.ellipse(cx, 66, r, 5, "#b08a20")
        p.ellipse(cx, 64, r * 0.7, 3, "#e8c040")
        p.glow(cx, 64, r, "#ffd060", 0.35)
    p.rect(54, 58, 74, 66, "#3a2414")
    p.poly([(55, 57.5), (64, 56.5), (73, 57.5), (73, 58.5), (55, 58.5)], "#efe8d2")
    p.glow(64, 56, 8, "#ffe0a0", 0.4)
    return finish(p, 75)


PAINTINGS = {
    "drowned_archive/stacks": stacks,
    "drowned_archive/atrium": atrium,
    "drowned_archive/sunken_shelves": sunken_shelves,
    "drowned_archive/inkwell": inkwell,
    "drowned_archive/last_readers_room": last_readers_room,
    "clockwork_foundry/works_floor": works_floor,
    "clockwork_foundry/pipe_maze": pipe_maze,
    "clockwork_foundry/great_engine": great_engine,
    "clockwork_foundry/stopped_hour": stopped_hour,
    "clockwork_foundry/orrery": orrery,
    "glass_desert/buried_colossus": buried_colossus,
    "glass_desert/glass_caves": glass_caves,
    "glass_desert/sunken_city": sunken_city,
    "glass_desert/mirage": mirage,
    "glass_desert/hourglass": hourglass,
    "lantern_night_market/lantern_street": lantern_street,
    "lantern_night_market/rooftops": rooftops,
    "lantern_night_market/floating_market": floating_market,
    "lantern_night_market/festival": festival,
    "lantern_night_market/brokers_vault": brokers_vault,
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
