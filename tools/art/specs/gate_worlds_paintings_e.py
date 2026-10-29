"""Gate Worlds, the Atlas paintings of the Sky Archipelago, the Abyssal Shrine and the fifteen rare worlds (SPEC §38.13),
painted the same way as the Bell Crypt's (gate_worlds_paintings.py).

    python3 tools/art/build_all.py gate_worlds_paintings_e     textures/gui/gate_atlas/<folder>/<world>.png
    python3 tools/art/specs/gate_worlds_paintings_e.py sheet PATH   every painting at 3x, named
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
from gate_worlds_paintings_d import stars, column  # noqa: E402
from gate_worlds_paintings_c import ridge  # noqa: E402
from PIL import Image  # noqa: E402


# ---- shapes --------------------------------------------------------------------------------------------------------

def isle(p, cx, top, r, turf="#5a9a4a", rock="#b8b0a0", under="#8a8478", seed=1, roots=True):
    """A floating island: a turf top, pale rock tapering to a point under it, roots hanging off."""
    rng = random.Random(seed)
    p.poly([(cx - r, top), (cx + r, top), (cx + r * 0.6, top + r * 0.5), (cx + r * 0.15, top + r * 1.2), (cx - r * 0.3, top + r * 0.8),
            (cx - r * 0.75, top + r * 0.4)], rock)
    p.poly([(cx + r * 0.2, top + r * 0.2), (cx + r * 0.6, top + r * 0.5), (cx + r * 0.15, top + r * 1.2)], under, 0.8)
    p.ellipse(cx, top, r * 1.02, r * 0.16, turf)
    if roots:
        for _ in range(int(r)):
            x = cx + rng.uniform(-r * 0.6, r * 0.5)
            p.line([(x, top + r * 0.3), (x + rng.uniform(-1, 1), top + r * 0.3 + rng.uniform(2, r * 0.8))], "#4a5a3a", 0.3, 0.7)


def clouds(p, y, colour="#f4f8fc", seed=3, alpha=0.9):
    rng = random.Random(seed)
    for _ in range(14):
        p.ellipse(rng.uniform(-6, W + 6), y + rng.uniform(-2, 4), rng.uniform(8, 20), rng.uniform(2.5, 5), colour, alpha)


def tree(p, x, ground, h, crown, trunk="#3a2a1e", leaves="#2a4a2a", alpha=1.0):
    p.rect(x - 0.8, ground - h, x + 0.8, ground, trunk, alpha)
    p.ellipse(x, ground - h - crown * 0.3, crown, crown * 0.7, leaves, alpha)


def figure(p, x, ground, s=1.0, colour="#dde4f0", alpha=0.8):
    """A pale figure sitting: a hunched back and a head."""
    p.ellipse(x, ground - 2.2 * s, 1.8 * s, 2.4 * s, colour, alpha)
    p.ellipse(x + 0.3 * s, ground - 5 * s, 1.1 * s, 1.2 * s, colour, alpha)


def door(p, x, ground, w, h, inside="#a07ce8", frame="#3a2414", glow=0.4):
    p.rect(x - w - 1, ground - h - 1, x + w + 1, ground, frame)
    p.rect(x - w, ground - h, x + w, ground, inside)
    if glow:
        p.glow(x, ground - h / 2, w * 3, inside, glow)


# ---- the Sky Archipelago ---------------------------------------------------------------------------------------------

def the_isles():
    """Green islands hanging in a blue sky, a rope bridge between two, water falling off the nearest into the cloud."""
    p = Pic()
    p.vgrad([(0, "#5a90d8"), (0.55, "#a8ccf0"), (0.8, "#e8f2fc"), (1, "#f8fbff")])
    clouds(p, 60, seed=11)
    isle(p, 98, 18, 9, seed=12)
    isle(p, 24, 30, 14, seed=13)
    isle(p, 72, 42, 7, seed=14)
    p.line([(38, 30), (52, 33), (66, 41)], "#8a6a40", 0.4)
    p.line([(38, 29), (52, 32), (66, 40)], "#6a4a28", 0.25)
    p.rect(30, 30, 31.5, 60, "#d8f0ff", 0.55)
    p.glow(30.7, 46, 6, "#d8f0ff", 0.3)
    for x in (18, 28):
        tree(p, x, 30, 3, 3, "#c8c0b0", "#e8a8c8")
    return finish(p, 91, top=0.05, bottom=0.05, sides=0.2)


def sky_ruins():
    """White marble columns broken off on an island, a roofless temple and gilt, a fallen drum on the turf."""
    p = Pic()
    p.vgrad([(0, "#6a9ad8"), (0.6, "#b8d4f0"), (1, "#eef4fc")])
    clouds(p, 62, seed=21)
    isle(p, 64, 40, 40, turf="#7aa05a", seed=22)
    for i, x in enumerate((34, 44, 54, 74, 84, 94)):
        h = 22 if i % 3 != 2 else 10
        column(p, x, 40 - h, 40, 1.6, "#f0ece0")
        if h > 15:
            p.rect(x - 2.4, 40 - h - 2, x + 2.4, 40 - h - 0.8, "#e0b040")
    p.rect(34, 16, 56, 18, "#f0ece0")
    p.ellipse(64, 41, 4, 1.4, "#e0b040")
    p.poly([(100, 39), (108, 38), (109, 40), (100, 41)], "#e8e4d8")
    return finish(p, 92, top=0.05, bottom=0.05, sides=0.2)


def wind_stair():
    """Small islands in a storm, loose stones flying round the bridge between them, rain slanting across."""
    p = Pic()
    p.vgrad([(0, "#3a4658"), (0.6, "#6a788a"), (1, "#8a98a8")])
    isle(p, 30, 36, 10, turf="#4a7040", seed=31)
    isle(p, 96, 28, 9, turf="#4a7040", seed=32)
    p.line([(40, 36), (68, 34), (87, 28)], "#5a4028", 0.4)
    rng = random.Random(33)
    for _ in range(14):
        x, y = rng.uniform(40, 90), rng.uniform(16, 50)
        p.rect(x, y, x + rng.uniform(1, 2.5), y + rng.uniform(1, 2), "#8a8478")
    for k in range(40):
        x = rng.uniform(0, W + 20)
        p.line([(x, 0), (x - 14, 72)], "#c8d4e0", 0.15, 0.3)
    p.glow(110, 10, 10, "#e8f0ff", 0.4)
    return finish(p, 93, top=0.2, bottom=0.1, sides=0.3)


def above_the_clouds():
    """The highest island above a sea of cloud, far islands drifting round it, the Wind Well's column of spray."""
    p = Pic()
    p.vgrad([(0, "#2a5ab0"), (0.5, "#7aa8e8"), (0.7, "#d8e8f8"), (1, "#ffffff")])
    p.rect(0, 50, 128, 72, "#f4f8fc")
    clouds(p, 50, seed=41)
    for x, y, r in ((14, 22, 4), (112, 16, 5), (100, 40, 3), (24, 44, 3)):
        isle(p, x, y, r, seed=int(x), roots=False)
    isle(p, 64, 36, 22, seed=42)
    p.rect(62, 10, 66, 36, "#e0f4ff", 0.6)
    p.glow(64, 22, 10, "#ffffff", 0.4)
    for x in (52, 76):
        p.rect(x - 0.6, 28, x + 0.6, 36, "#e0dccc")
        p.ellipse(x, 27.5, 1, 1, "#ffd890")
    return finish(p, 94, top=0.05, bottom=0.0, sides=0.2)


def whale_road():
    """The back of a whale swimming through the sky, its blowhole spouting, an island's turf growing on it."""
    p = Pic()
    p.vgrad([(0, "#5a90d8"), (0.7, "#c8e0f8"), (1, "#f0f6fc")])
    clouds(p, 64, seed=51)
    p.ellipse(64, 46, 56, 14, "#5a6a80")
    p.ellipse(64, 40, 50, 8, "#7a8aa0")
    p.poly([(116, 44), (128, 34), (126, 50)], "#5a6a80")
    p.ellipse(30, 48, 1.4, 1.2, "#101418")
    p.rect(40, 18, 42, 34, "#e8f4ff", 0.6)
    p.glow(41, 18, 6, "#ffffff", 0.4)
    p.ellipse(72, 34, 18, 3, "#5a9a4a")
    rng = random.Random(52)
    for _ in range(12):
        p.ellipse(rng.uniform(30, 100), rng.uniform(42, 54), 1.2, 0.8, "#a0a8b0", 0.8)
    return finish(p, 95, top=0.05, bottom=0.05, sides=0.2)


# ---- the Abyssal Shrine --------------------------------------------------------------------------------------------

def dark_steps():
    """Pale steps going down into the black, landing after landing, candles left burning on them."""
    p = Pic()
    p.vgrad([(0, "#0a0810"), (1, "#020104")])
    for i in range(7):
        y, x, w = 14 + i * 8, 64 + (i % 2) * 18 - 9, 22 - i * 2.4
        p.rect(x - w, y, x + w, y + 1.6, "#cac4b4", 1 - i * 0.12)
        p.ellipse(x + w * 0.6, y - 0.8, 0.5, 0.9, "#ffe890", 1 - i * 0.1)
        p.glow(x + w * 0.6, y - 1, 5, "#ffc040", 0.35 - i * 0.04)
    return finish(p, 101)


def black_mirror():
    """A still black lake under a black roof, and in it the reflection of a starry sky that isn't there."""
    p = Pic()
    p.vgrad([(0, "#04030a"), (0.55, "#08060e"), (0.56, "#0a0c1e"), (1, "#020208")])
    p.glow(64, 56, 40, "#2a3060", 0.35, squash=0.4)
    stars(p, 102, 70, (0, 42, W, 70))
    p.line([(30, 50), (98, 64)], "#4a5aa0", 4, 0.2, blur=2)
    p.line([(0, 40), (128, 40)], "#2a2242", 0.4)
    for x in (30, 98):
        p.rect(x - 1.5, 22, x + 1.5, 40, "#16121e")
        p.ellipse(x, 21, 1, 1, "#b888f4")
        p.glow(x, 21, 5, "#b888f4", 0.4)
    return finish(p, 102)


def starfall():
    """Black islands in the void, stars hanging all round them on threads of nothing, one falling."""
    p = Pic()
    p.vgrad([(0, "#06061a"), (1, "#020208")])
    isle(p, 40, 40, 14, turf="#2c2638", rock="#1e1a28", under="#14111c", seed=111, roots=False)
    isle(p, 100, 30, 9, turf="#2c2638", rock="#1e1a28", under="#14111c", seed=112, roots=False)
    rng = random.Random(113)
    for _ in range(26):
        x, y = rng.uniform(0, 128), rng.uniform(0, 64)
        p.ellipse(x, y, 0.6, 0.6, "#f4f8ff")
        p.glow(x, y, 3, "#a0b0f0", 0.3)
    p.line([(70, 6), (84, 22)], "#e8eeff", 0.4, 0.8)
    p.glow(84, 22, 4, "#ffffff", 0.6)
    return finish(p, 103)


def the_last_room():
    """An ordinary room at the bottom of everything: a chair, a lamp, a door with light under it."""
    p = Pic()
    p.vgrad([(0, "#1a1418"), (1, "#0a0808")])
    p.rect(0, 52, 128, 72, "#3a2a1e")
    p.rect(54, 18, 74, 52, "#2a1a10")
    p.rect(54, 51, 74, 52.5, "#ffe0a0")
    p.glow(64, 52, 10, "#ffd890", 0.5, squash=0.3)
    p.ellipse(71, 36, 0.8, 0.8, "#c0a060")
    p.rect(24, 40, 36, 42, "#4a3020")
    p.rect(24, 30, 26, 52, "#4a3020")
    p.rect(34, 42, 36, 52, "#4a3020")
    p.rect(100, 30, 102, 52, "#2a2018")
    p.ellipse(101, 28, 4, 3, "#e8c890")
    p.glow(101, 30, 14, "#ffd8a0", 0.35)
    return finish(p, 104)


def the_eye():
    """An eye as big as a hill looking up out of the dark, its iris burning amber."""
    p = Pic()
    p.vgrad([(0, "#06040a"), (1, "#020104")])
    p.ellipse(64, 42, 48, 20, "#d4cac0")
    p.ellipse(64, 42, 18, 18, "#a05018")
    p.ellipse(64, 42, 13, 13, "#d08024")
    p.ellipse(64, 42, 3, 13, "#050305")
    p.glow(64, 42, 20, "#f0b040", 0.3)
    p.poly([(16, 42), (64, 18), (112, 42), (64, 24)], "#06040a", 0.9)
    return finish(p, 105, top=0.3, bottom=0.3, sides=0.5)


# ---- the rare worlds -------------------------------------------------------------------------------------------------

def the_quiet():
    """A meadow at night under a sky full of stars, one old tree, moonflowers open in the grass."""
    p = Pic()
    p.vgrad([(0, "#04081a"), (0.6, "#101a38"), (0.7, "#1a2a3a"), (1, "#0e1a14")])
    stars(p, 201, 110, (0, 0, W, 44))
    p.line([(10, 8), (40, 30)], "#1a2a5a", 6, 0.25, blur=2)
    ridge(p, 50, 6, "#0c1420", 202, jag=0.2)
    p.rect(0, 52, 128, 72, "#14281a")
    tree(p, 84, 54, 12, 12, "#1a140e", "#16261a")
    rng = random.Random(203)
    for _ in range(26):
        x, y = rng.uniform(0, 128), rng.uniform(56, 70)
        p.ellipse(x, y, 0.5, 0.4, "#e8f0ff")
        p.glow(x, y, 2, "#d8e4ff", 0.25)
    p.line([(100, 6), (112, 14)], "#ffffff", 0.3, 0.7)
    return finish(p, 201, top=0.1)


def the_reflection():
    """The gate you came in by, grey all through, and its lights red."""
    p = Pic()
    p.vgrad([(0, "#1a1a1a"), (0.6, "#3a3a3a"), (1, "#141414")])
    for x in (12, 36, 92, 116):
        column(p, x, 8, 52, 3, "#5a5a5a")
    p.rect(0, 52, 128, 72, "#8a8a8a")
    p.rect(0, 52, 128, 53, "#c8c8c8")
    for x in (24, 64, 104):
        p.ellipse(x, 20, 2, 2, "#ff3a2a")
        p.glow(x, 20, 10, "#d82020", 0.45)
    p.poly([(52, 52), (76, 52), (70, 72), (58, 72)], "#5a5a5a", 0.5)
    figure(p, 64, 52, 1.6, "#2a2a2a", 0.9)
    # its reflection in the floor, a little late
    p.ellipse(65, 55.5, 2.9, 3.8, "#2a2a2a", 0.4)
    p.ellipse(65.5, 60, 1.8, 1.9, "#2a2a2a", 0.4)
    return finish(p, 202)


def the_hollow_sky():
    """Islands in an empty sky, and the biggest held in the open hand of a stone giant."""
    p = Pic()
    p.vgrad([(0, "#4a80d0"), (0.6, "#a8c8f0"), (1, "#f0f6fc")])
    clouds(p, 62, seed=211)
    isle(p, 64, 38, 28, seed=212)
    p.ellipse(64, 34, 12, 4, "#8a8a80")
    for k in range(4):
        x = 54 + k * 6.5
        p.rect(x - 1.6, 20 + (k in (0, 3)) * 3, x + 1.6, 34, "#7e7b72")
        p.ellipse(x, 20 + (k in (0, 3)) * 3, 1.6, 1.4, "#8e8a80")
    p.poly([(48, 34), (44, 26), (47, 25), (51, 32)], "#7e7b72")
    p.poly([(70, 36), (100, 46), (98, 50), (66, 40)], "#6e6c64")
    for x, y in ((16, 18), (110, 24)):
        isle(p, x, y, 5, seed=x, roots=False)
    return finish(p, 211, top=0.05, bottom=0.05, sides=0.2)


def the_endless_stair():
    """The same landing again and again, a flight going up from it and a flight going down, into the dark both ways."""
    p = Pic()
    p.vgrad([(0, "#08060e"), (1, "#020104")])
    for i, (x, y, s) in enumerate(((64, 36, 1.0), (30, 16, 0.55), (98, 58, 0.55), (14, 6, 0.3), (114, 68, 0.3))):
        p.rect(x - 10 * s, y, x + 10 * s, y + 3 * s, "#cac4b4", 0.4 + s * 0.6)
        for k in range(6):
            p.rect(x + (10 + k * 3) * s, y - (k + 1) * 2 * s, x + (13 + k * 3) * s, y - k * 2 * s, "#b6b0a0", (0.4 + s * 0.6) * (1 - k * 0.12))
            p.rect(x - (13 + k * 3) * s, y + (k + 1) * 2 * s + 3 * s, x - (10 + k * 3) * s, y + (k + 2) * 2 * s + 3 * s, "#a09a8c",
                   (0.4 + s * 0.6) * (1 - k * 0.12))
    p.line([(54, 34), (74, 34)], "#c0a060", 0.3)
    return finish(p, 212)


def the_other_gate():
    """A cave you know, every colour wrong: violet stone, green light, an orange sky through a hole in the roof."""
    p = Pic()
    p.vgrad([(0, "#3a1a50"), (0.5, "#5a2a6a"), (1, "#1a0a24")])
    p.ellipse(64, 8, 20, 8, "#ff9a40", 0.8)
    p.glow(64, 8, 20, "#ff9a40", 0.3)
    for x, h in ((20, 30), (44, 22), (86, 26), (108, 34)):
        p.ellipse(x, 52, 6, h * 0.25, "#2a6a3a")
        p.rect(x - 1.2, 52 - h, x + 1.2, 52, "#c8e040")
        p.ellipse(x, 52 - h, 7, 3, "#40d0c0")
        p.glow(x, 52 - h, 8, "#40ffd0", 0.3)
    p.rect(0, 54, 128, 72, "#4a2a10")
    return finish(p, 213)


def the_tide_room():
    """A room the sea comes into twice a day, the water out, its shells all over the floor and pools left behind."""
    p = Pic()
    p.vgrad([(0, "#0a1418"), (0.6, "#1e3438"), (1, "#0a1a1e")])
    for x in (10, 118):
        p.rect(x - 6, 0, x + 6, 56, "#2a4a4a")
    p.rect(0, 54, 128, 72, "#c8b890")
    p.ellipse(44, 60, 16, 3.5, "#3a9aa0", 0.8)
    p.ellipse(90, 64, 12, 2.6, "#3a9aa0", 0.8)
    rng = random.Random(214)
    for _ in range(22):
        p.ellipse(rng.uniform(4, 124), rng.uniform(56, 71), 0.9, 0.6, ("#f0e0ff", "#e8a090", "#f0e8d0")[rng.randrange(3)])
    p.ellipse(90, 64, 1.2, 0.8, "#fff0ff")
    p.glow(90, 64, 4, "#f0e0ff", 0.5)
    p.line([(0, 30), (128, 30)], "#3a9aa0", 0.4, 0.4)
    return finish(p, 214)


def the_cartographers_table():
    """You stand on a map that goes on for ever: inked contour lines, a river, a compass rose, a giant inkwell."""
    p = Pic()
    p.vgrad([(0, "#5a4a30"), (0.3, "#c8b890"), (1, "#e8dcbc")])
    for k in range(8):
        y = 30 + k * 5
        p.line([(x, y + math.sin(x / 12 + k) * 2) for x in range(0, 130, 4)], "#344a84", 0.3, 0.7)
    p.line([(0, 60), (40, 52), (80, 58), (128, 48)], "#4a64a4", 1.2, 0.7)
    for a in range(8):
        r = 10 if a % 2 == 0 else 6
        t = a * math.pi / 4
        p.poly([(64, 52), (64 + math.cos(t + 0.15) * 2, 52 + math.sin(t + 0.15) * 1), (64 + math.cos(t) * r, 52 + math.sin(t) * r * 0.4),
                (64 + math.cos(t - 0.15) * 2, 52 + math.sin(t - 0.15) * 1)], "#1a2850")
    p.ellipse(64, 52, 1.4, 0.8, "#e0b040")
    p.rect(100, 18, 116, 34, "#101018")
    p.ellipse(108, 18, 8, 2, "#26386a")
    p.line([(20, 34), (44, 14)], "#f0f0f0", 1.6)
    return finish(p, 215, top=0.2)


def upward_rain():
    """Green hills in grey light, and the rain going up out of the puddles into the sky."""
    p = Pic()
    p.vgrad([(0, "#6a7a88"), (0.6, "#9aaab8"), (1, "#5a7a4a")])
    ridge(p, 44, 8, "#4a6a40", 221, jag=0.1)
    ridge(p, 52, 5, "#3a5a32", 222, jag=0.1)
    p.rect(0, 56, 128, 72, "#3a5a32")
    rng = random.Random(223)
    for _ in range(9):
        x, y = rng.uniform(4, 124), rng.uniform(58, 70)
        p.ellipse(x, y, 4, 1, "#9ab8d0", 0.8)
        for _ in range(3):
            xx = x + rng.uniform(-3, 3)
            p.line([(xx, y), (xx + 1, rng.uniform(0, y - 20))], "#c8dcf0", 0.25, 0.6)
    return finish(p, 221, top=0.1)


def the_dollhouse():
    """A room in a doll's house, the wallpaper and a tiny bed, and outside the window an eye as big as the room."""
    p = Pic()
    p.vgrad([(0, "#3a2a3a"), (1, "#1a1018")])
    p.rect(0, 54, 128, 72, "#5a3a24")
    p.rect(64, 8, 120, 50, "#080610")
    p.ellipse(92, 30, 24, 14, "#d4cac0")
    p.ellipse(92, 30, 9, 9, "#6a3010")
    p.ellipse(92, 30, 4, 4, "#050305")
    p.ellipse(89, 27, 1.2, 1.2, "#ffffff")
    p.rect(62, 6, 122, 8, "#8a5a3a")
    p.rect(62, 50, 122, 52, "#8a5a3a")
    p.rect(91, 8, 93, 50, "#8a5a3a")
    p.rect(10, 42, 44, 54, "#c8b8d8")
    p.rect(10, 36, 14, 54, "#6a4a30")
    p.glow(30, 30, 18, "#ffd8a0", 0.25)
    return finish(p, 222)


def the_glass_sea():
    """A sea frozen clear under a pale sky, fish swimming under the ice, and something huge and dark far below them."""
    p = Pic()
    p.vgrad([(0, "#8ab0d8"), (0.4, "#d8e8f8"), (0.42, "#9ac0e0"), (1, "#0a2a50")])
    ridge(p, 29, 5, "#e8f0f8", 231, jag=0.4)
    p.ellipse(70, 62, 34, 6, "#06182e", 0.8)
    rng = random.Random(232)
    for _ in range(12):
        x, y = rng.uniform(4, 124), rng.uniform(36, 54)
        p.ellipse(x, y, 1.6, 0.6, ("#e8a040", "#c0c8d0", "#40a0c0")[rng.randrange(3)], 0.8)
    p.line([(10, 34), (60, 31)], "#ffffff", 0.4, 0.5)
    p.line([(70, 38), (124, 33)], "#ffffff", 0.4, 0.4)
    return finish(p, 231, top=0.05, bottom=0.2)


def the_aurora_steppe():
    """Dry grass under the aurora, old standing stones lit along their cracks."""
    p = Pic()
    p.vgrad([(0, "#040814"), (0.6, "#0a1828"), (0.62, "#2a3a30"), (1, "#1a2418")])
    stars(p, 241, 50, (0, 0, W, 40))
    for k, col in enumerate(("#40d090", "#70f0b0", "#c890ff")):
        pts = [(x, 12 + k * 5 + math.sin(x / 14 + k) * 5) for x in range(0, 130, 4)]
        p.line(pts, col, 5 - k, 0.25, blur=2.5)
    p.rect(0, 46, 128, 72, "#3a4a30")
    for x, h in ((22, 14), (40, 10), (86, 16), (104, 11)):
        p.rect(x - 2.4, 46 - h, x + 2.4, 48, "#2c323a")
        p.line([(x - 0.5, 46 - h + 2), (x + 0.6, 46 - h * 0.4)], "#70f0b0", 0.3)
    return finish(p, 241, top=0.1)


def the_library_of_doors():
    """A library with a doorway standing on its own in the room, violet light shimmering in it, shelves behind."""
    p = Pic()
    p.vgrad([(0, "#0a1214"), (1, "#060a0c")])
    for x0 in (0, 104):
        for k in range(8):
            p.rect(x0, 4 + k * 6, x0 + 24, 9 + k * 6, ("#5a2a1a", "#2a3a5a", "#3a4a2a", "#6a5a2a")[k % 4])
            p.rect(x0, 9 + k * 6, x0 + 24, 10 + k * 6, "#2a1a10")
    p.rect(0, 54, 128, 72, "#2a2018")
    door(p, 64, 54, 9, 28)
    door(p, 38, 52, 4, 13, glow=0.2)
    door(p, 90, 52, 4, 13, glow=0.2)
    return finish(p, 251)


def the_lantern_orchard():
    """Rows of dark trees at night, and lit lanterns growing on them like fruit."""
    p = Pic()
    p.vgrad([(0, "#08061a"), (0.6, "#1e1428"), (1, "#14200e")])
    stars(p, 261, 30, (0, 0, W, 24))
    p.glow(100, 10, 14, "#d8d8ff", 0.4)
    p.rect(0, 50, 128, 72, "#14200e")
    rng = random.Random(262)
    for row, (y, s) in enumerate(((50, 0.6), (58, 0.8), (70, 1.1))):
        for k in range(6 - row):
            x = 10 + k * (128 / (6 - row)) + row * 8
            tree(p, x, y, 6 * s, 6 * s, "#3a2a1a", "#1e3418")
            for _ in range(3):
                lx, ly = x + rng.uniform(-5, 5) * s, y - 6 * s - rng.uniform(-1, 2) * s
                p.ellipse(lx, ly, 0.9 * s, 1.1 * s, "#ffc060")
                p.glow(lx, ly, 3 * s, "#ff9030", 0.35)
    return finish(p, 261, top=0.1)


def the_last_campfire():
    """A clearing at night, a fire burning, pale figures sitting round it, and one seat empty."""
    p = Pic()
    p.vgrad([(0, "#04060c"), (0.6, "#0c0e18"), (1, "#141810")])
    stars(p, 271, 60, (0, 0, W, 34))
    ridge(p, 40, 6, "#080a0e", 272, jag=0.3)
    p.rect(0, 44, 128, 72, "#141a10")
    p.glow(64, 52, 26, "#ff8a30", 0.6)
    p.poly([(60, 54), (64, 42), (68, 54)], "#ffb040")
    p.poly([(62, 54), (64, 47), (66, 54)], "#fff0a0")
    for k, (x, y) in enumerate(((40, 56), (50, 62), (78, 62), (88, 56), (64, 66))):
        p.rect(x - 4, y - 0.4, x + 4, y + 1, "#5a3a20")
        if k != 3:
            figure(p, x, y, 1.0, "#dde4f0", 0.75)
    return finish(p, 271, top=0.1)


def the_gates_heart():
    """In the middle of the stars a great red heart, held up on its veins, a gold ring standing round it."""
    p = Pic()
    p.vgrad([(0, "#0a0408"), (1, "#020103")])
    stars(p, 281, 40, (0, 0, W, H))
    isle(p, 64, 54, 26, turf="#2a1418", rock="#1a1214", under="#100a0c", seed=282, roots=False)
    p.ellipse(64, 30, 25, 25, "#e0b040", 0.9)
    p.ellipse(64, 30, 23, 23, "#0a0408")
    stars(p, 283, 10, (42, 8, 86, 52))
    for a in (-0.9, -0.4, 0.4, 0.9):
        p.line([(64, 36), (64 + math.sin(a) * 18, 54)], "#a01c24", 1.2)
    p.ellipse(64, 30, 9, 9.5, "#a01c24")
    p.ellipse(62, 28, 5, 5, "#d43434")
    p.glow(64, 30, 26, "#ff4a3a", 0.6)
    return finish(p, 281)


PAINTINGS = {
    "sky_archipelago/the_isles": the_isles,
    "sky_archipelago/sky_ruins": sky_ruins,
    "sky_archipelago/wind_stair": wind_stair,
    "sky_archipelago/above_the_clouds": above_the_clouds,
    "sky_archipelago/whale_road": whale_road,
    "abyssal_shrine/dark_steps": dark_steps,
    "abyssal_shrine/black_mirror": black_mirror,
    "abyssal_shrine/starfall": starfall,
    "abyssal_shrine/the_last_room": the_last_room,
    "abyssal_shrine/the_eye": the_eye,
    "rare/the_quiet": the_quiet,
    "rare/the_reflection": the_reflection,
    "rare/the_hollow_sky": the_hollow_sky,
    "rare/the_endless_stair": the_endless_stair,
    "rare/the_other_gate": the_other_gate,
    "rare/the_tide_room": the_tide_room,
    "rare/the_cartographers_table": the_cartographers_table,
    "rare/upward_rain": upward_rain,
    "rare/the_dollhouse": the_dollhouse,
    "rare/the_glass_sea": the_glass_sea,
    "rare/the_aurora_steppe": the_aurora_steppe,
    "rare/the_library_of_doors": the_library_of_doors,
    "rare/the_lantern_orchard": the_lantern_orchard,
    "rare/the_last_campfire": the_last_campfire,
    "rare/the_gates_heart": the_gates_heart,
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
    else:
        print(__doc__)
