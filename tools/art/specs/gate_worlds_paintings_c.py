"""Gate Worlds, the Atlas paintings of the C themes (SPEC §38.12): the Frost Monastery, the Mirror Palace, the Precursor Lab
and the Nightmare, five worlds each, painted the same way as the Bell Crypt's (gate_worlds_paintings.py).

    python3 tools/art/build_all.py gate_worlds_paintings_c     textures/gui/gate_atlas/<theme>/<world>.png
    python3 tools/art/specs/gate_worlds_paintings_c.py sheet PATH   every painting at 3x, named
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
from PIL import Image  # noqa: E402


# ---- shapes --------------------------------------------------------------------------------------------------------

def ridge(p, y, amp, colour, seed, alpha=1.0, jag=1.0):
    """A mountain ridge: sharp peaks over a broad swell."""
    rng = random.Random(seed)
    pts, x = [], 0.0
    while x <= W + 4:
        pts.append((x, y - abs(math.sin(x / 23 + seed)) * amp - rng.uniform(0, amp * 0.35 * jag)))
        x += rng.uniform(3, 7)
    p.poly(pts + [(W, H), (0, H)], colour, alpha)


def roof(p, x0, x1, base, rise, colour, eave="#2a1a14"):
    """A pitched monastery roof, its eaves turned up at the ends."""
    p.poly([(x0 - 2, base + 0.6), (x0 + 1, base - 0.8), ((x0 + x1) / 2, base - rise), (x1 - 1, base - 0.8), (x1 + 2, base + 0.6)], colour)
    p.line([(x0 - 2, base + 0.6), (x1 + 2, base + 0.6)], eave, 0.6)


def flags(p, x0, y0, x1, y1, seed, n=9):
    """A string of prayer flags sagging between two posts."""
    rng = random.Random(seed)
    cols = ("#3a6ac8", "#f0f0f0", "#c83a3a", "#3aa05a", "#e8c040")
    p.line([(x0 + (x1 - x0) * k / 12, y0 + (y1 - y0) * k / 12 + math.sin(math.pi * k / 12) * 3) for k in range(13)], "#8a7a6a", 0.25)
    for k in range(n):
        t = (k + 0.5) / n
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t + math.sin(math.pi * t) * 3
        p.rect(x - 0.7, y, x + 0.7, y + 2.0 + rng.uniform(-0.2, 0.3), cols[k % 5], 0.9)


def frame(p, x0, y0, x1, y1, gilt="#c8a040", face="#8aa0c0", glint=0.25):
    """A gilt-framed mirror: the frame, a pale face with a slant of light across it."""
    p.rect(x0, y0, x1, y1, gilt)
    p.rect(x0 + 0.8, y0 + 0.8, x1 - 0.8, y1 - 0.8, face)
    p.poly([(x0 + 1, y1 - 3), (x0 + 3, y1 - 1), (x1 - 1, y0 + 3), (x1 - 1, y0 + 1)], "#ffffff", glint)


def chandelier(p, cx, cy, r, colour="#ffd890", glow=0.4):
    p.line([(cx, 0), (cx, cy - 2)], "#4a3a20", 0.3)
    p.ellipse(cx, cy, r, r * 0.3, "#c8a040", 0.9)
    for k in range(7):
        x = cx - r + 2 * r * k / 6
        p.ellipse(x, cy - 1.2, 0.4, 0.8, colour)
    p.glow(cx, cy - 1, r * 2.4, colour, glow)


def tank(p, cx, bottom, w, h, liquid="#3e9e4e", shape="#12301a", glow=0.4, broken=False):
    """A specimen tank: a black cap and foot, green liquid lit from within, something dark asleep in it."""
    p.rect(cx - w, bottom - h, cx + w, bottom, "#0a1a10")
    if not broken:
        p.rect(cx - w + 0.6, bottom - h + 1.5, cx + w - 0.6, bottom - 1.5, liquid, 0.85)
        p.ellipse(cx, bottom - h / 2, w * 0.45, h * 0.28, shape, 0.9)
        p.glow(cx, bottom - h / 2, w * 2.5, liquid, glow)
    else:
        p.rect(cx - w + 0.6, bottom - h * 0.3, cx + w - 0.6, bottom - 1.5, liquid, 0.6)
        p.line([(cx - w + 0.6, bottom - h + 2), (cx, bottom - h * 0.55), (cx - 1, bottom - h * 0.4)], "#bfffd0", 0.3, 0.6)
    p.rect(cx - w - 0.4, bottom - h - 1, cx + w + 0.4, bottom - h + 1.5, "#2a2f38")
    p.rect(cx - w - 0.4, bottom - 1.5, cx + w + 0.4, bottom, "#2a2f38")


def rack(p, x0, x1, top, bottom, seed):
    """A server rack face: black, rows of tiny lights."""
    rng = random.Random(seed)
    p.rect(x0, top, x1, bottom, "#0a0c10")
    y = top + 1
    while y < bottom - 1:
        p.line([(x0 + 0.4, y), (x1 - 0.4, y)], "#1e2229", 0.3)
        for k in range(int((x1 - x0) / 1.6)):
            if rng.random() < 0.45:
                p.ellipse(x0 + 0.8 + k * 1.6, y + 0.8, 0.25, 0.25, rng.choice(["#40ff80", "#40e0ff", "#ffb040"]))
        y += 2.2


def bed(p, x, y, s=1.0, sheet="#e2e2ea", frame_c="#382218"):
    """A child's bed: posts, a mattress under a white sheet, a pillow."""
    p.rect(x, y - 3 * s, x + 12 * s, y, frame_c)
    p.rect(x + 0.5 * s, y - 4.2 * s, x + 11.5 * s, y - 2.6 * s, sheet)
    p.rect(x + 0.8 * s, y - 5 * s, x + 3.4 * s, y - 4 * s, "#f0f0f6")
    p.rect(x - 0.4 * s, y - 7 * s, x + 0.6 * s, y, frame_c)
    p.rect(x + 11.4 * s, y - 5.5 * s, x + 12.4 * s, y, frame_c)


def window(p, x, y, w, h, sky=("#1a2a6a", "#4a6ac8"), moon=True):
    p.rect(x - 0.8, y - 0.8, x + w + 0.8, y + h + 0.8, "#2a1810")
    for k in range(int(h)):
        t = k / max(h - 1, 1)
        c0 = tuple(int(sky[0][i:i + 2], 16) for i in (1, 3, 5))
        c1 = tuple(int(sky[1][i:i + 2], 16) for i in (1, 3, 5))
        c = "#%02x%02x%02x" % tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3))
        p.rect(x, y + k, x + w, y + k + 1, c)
    p.line([(x + w / 2, y), (x + w / 2, y + h)], "#2a1810", 0.5)
    p.line([(x, y + h / 2), (x + w, y + h / 2)], "#2a1810", 0.5)
    if moon:
        p.ellipse(x + w * 0.72, y + h * 0.25, 1.2, 1.2, "#fff4c8")
        p.glow(x + w * 0.72, y + h * 0.25, 3.5, "#fff4c8", 0.4)


def toy(p, x, y, s, colour):
    p.rect(x, y - s, x + s, y, colour)
    p.rect(x + s * 0.25, y - s * 0.75, x + s * 0.75, y - s * 0.25, "#f4e8c8", 0.8)


# ---- the Frost Monastery --------------------------------------------------------------------------------------------

def cloisters():
    """The monastery on its mountain shelf: snowy roofs in rows, a covered walk, prayer flags, the peaks behind."""
    p = Pic()
    p.vgrad([(0, "#8ab4e8"), (0.55, "#c8d8e8"), (1, "#e8eef4")])
    ridge(p, 30, 16, "#a8bcd4", 3)
    ridge(p, 36, 10, "#c8d4e4", 5)
    for i, (x0, x1, base, rise) in enumerate(((6, 34, 44, 7), (40, 88, 40, 10), (94, 122, 44, 7))):
        p.rect(x0, base, x1, 56, "#e8e0d0")
        p.rect(x0, base, x1, base + 2, "#8a3a2a")
        roof(p, x0, x1, base, rise, "#5a2a20")
        p.poly([(x0 - 1, base - 0.2), ((x0 + x1) / 2, base - rise + 0.6), (x1 + 1, base - 0.2)], "#f4f8fc", 0.85)
        for k in range(3):
            wx = x0 + (x1 - x0) * (k + 0.5) / 3
            p.rect(wx - 1.2, base + 4, wx + 1.2, base + 8, "#ffc860")
            p.glow(wx, base + 6, 4, "#ffc860", 0.35)
    for x in range(4, 126, 8):
        p.rect(x, 56, x + 1.2, 62, "#5a2a20")
    p.rect(0, 55, 128, 57, "#4a2418")
    p.rect(0, 62, 128, 72, "#f0f4f8")
    flags(p, 34, 36, 40, 33, 7, 4)
    flags(p, 88, 33, 94, 36, 8, 4)
    flags(p, 0, 50, 128, 50, 9, 22)
    motes(p, 11, 40, "#ffffff", (0, 0, W, H), (0.3, 0.5), (0.4, 0.9))
    return finish(p, 81, top=0.15, bottom=0.1)


def ice_caves():
    """Blue ice caves: walls of glassy ice lit from inside, icicles hanging, a frozen fall at the back."""
    p = Pic()
    p.vgrad([(0, "#0a1a30"), (0.5, "#1a3a60"), (1, "#081428")])
    p.ellipse(64, 34, 52, 30, "#2a5a90")
    p.ellipse(64, 34, 44, 24, "#4a8ac8", 0.8)
    p.rect(56, 10, 72, 60, "#bfe8ff", 0.6, blur=1.0)
    p.glow(64, 34, 30, "#9fd8ff", 0.5)
    rng = random.Random(83)
    for _ in range(26):
        x = rng.uniform(4, 124)
        L = rng.uniform(3, 12)
        top = 4 + abs(x - 64) / 8
        p.poly([(x - 0.8, top), (x + 0.8, top), (x, top + L)], "#d8f4ff", 0.85)
    for x, h in ((14, 10), (24, 6), (104, 8), (116, 12)):
        p.poly([(x - 3, 62), (x, 62 - h), (x + 3, 62)], "#8ac8f0")
        p.glow(x, 62 - h / 2, 5, "#9fd8ff", 0.3)
    p.rect(0, 60, 128, 72, "#cfe8f8")
    p.rect(40, 61, 88, 64, "#ffffff", 0.4)
    motes(p, 85, 24, "#e8f8ff", (0, 0, W, 60), (0.3, 0.5), (0.3, 0.7))
    return finish(p, 82)


def cliff_stair():
    """Stairs cut up the cliff face, rope bridges between ledges, a shrine on the highest point, clouds far below."""
    p = Pic()
    p.vgrad([(0, "#3a6a90"), (0.6, "#a8c4dc"), (1, "#e8eef4")])
    p.poly([(30, 72), (38, 10), (52, 4), (60, 20), (66, 72)], "#5a6a7a")
    p.poly([(80, 72), (88, 24), (100, 18), (110, 34), (114, 72)], "#4a5a6a")
    for k in range(10):
        y = 66 - k * 5.5
        x = 40 + (k % 2) * 8 + k * 0.6
        p.rect(x, y, x + 6, y + 1.2, "#e8e0d0")
    p.line([(62, 30), (72, 34), (88, 30)], "#8a6a4a", 0.4)
    flags(p, 62, 29, 88, 29, 21, 8)
    p.rect(44, 1, 52, 6, "#8a3a2a")
    roof(p, 43, 53, 1.5, 3, "#5a2a20")
    p.glow(48, 4, 6, "#ffc860", 0.45)
    for x, y, rx in ((10, 60, 24), (70, 64, 30), (120, 58, 20), (40, 70, 34)):
        p.ellipse(x, y, rx, 5, "#f4f8fc", 0.9, blur=1.0)
    motes(p, 23, 30, "#ffffff", (0, 0, W, H), (0.3, 0.5), (0.3, 0.8))
    return finish(p, 84, top=0.1, bottom=0.05)


def still_snow():
    """The Still Snow: a white valley under a low sun, a great stupa in the middle, snowflakes hanging in the air."""
    p = Pic()
    p.vgrad([(0, "#c8a8c8"), (0.4, "#e8d0d8"), (0.62, "#f4ecec"), (1, "#f8f8fc")])
    p.glow(96, 26, 22, "#ffe8c8", 0.5)
    p.ellipse(96, 26, 4, 4, "#fff4e0")
    ridge(p, 40, 12, "#d0c8d8", 31)
    ridge(p, 46, 6, "#e4dce8", 33)
    p.ellipse(56, 50, 12, 6, "#f8f8fc")
    p.ellipse(56, 46, 9, 7, "#f0f0f6")
    p.rect(54.5, 30, 57.5, 40, "#e8c040")
    p.poly([(53, 40), (59, 40), (56, 26)], "#c8a040")
    p.rect(52, 42, 60, 44, "#8a3a2a")
    flags(p, 20, 48, 56, 30, 35, 10)
    flags(p, 56, 30, 96, 48, 36, 10)
    rng = random.Random(37)
    for _ in range(60):
        p.ellipse(rng.uniform(0, 128), rng.uniform(0, 70), 0.35, 0.35, "#ffffff", 0.9)
    return finish(p, 85, top=0.1, bottom=0.05, sides=0.2)


def frozen_choir():
    """The Frozen Choir: monks in a ring frozen mid-song, ice to their shoulders, a pale light falling on them."""
    p = Pic()
    p.vgrad([(0, "#0e1624"), (1, "#203048")])
    p.poly([(52, 0), (76, 0), (92, 60), (36, 60)], "#bfe8ff", 0.12, blur=1.0)
    for k in range(9):
        a = math.pi * (0.1 + 0.8 * k / 8)
        x, y = 64 - math.cos(a) * 40, 52 - math.sin(a) * 10
        s = 0.8 + 0.25 * math.sin(a)
        p.poly([(x - 3 * s, y + 6 * s), (x - 2 * s, y - 2 * s), (x + 2 * s, y - 2 * s), (x + 3 * s, y + 6 * s)], "#8a3a2a")
        p.ellipse(x, y - 3.4 * s, 1.6 * s, 1.8 * s, "#d8c0a8")
        p.ellipse(x, y - 4 * s, 2.0 * s, 1.0 * s, "#e8c040", 0.7)
        p.poly([(x - 3.6 * s, y + 6 * s), (x - 3 * s, y), (x + 3 * s, y), (x + 3.6 * s, y + 6 * s)], "#9fd8ff", 0.45)
    p.rect(0, 58, 128, 72, "#4a6a8a")
    p.ellipse(64, 60, 28, 4, "#bfe8ff", 0.4)
    p.glow(64, 40, 26, "#bfe8ff", 0.25)
    motes(p, 41, 24, "#e8f8ff", (30, 0, 98, 58), (0.3, 0.5), (0.3, 0.7))
    return finish(p, 86)


# ---- the Mirror Palace ----------------------------------------------------------------------------------------------

def gallery():
    """The long gallery: mirrors in gilt frames down both walls, chandeliers, a checkered floor running away."""
    p = Pic()
    p.vgrad([(0, "#1a1620"), (0.55, "#3a3440"), (1, "#14101a")])
    for k in range(6):
        t = k / 6
        xl, xr = 4 + t * 50, 124 - t * 50
        h = 30 * (1 - t * 0.8)
        frame(p, xl, 34 - h / 2, xl + 6 * (1 - t * 0.7), 34 + h / 2, glint=0.2)
        frame(p, xr - 6 * (1 - t * 0.7), 34 - h / 2, xr, 34 + h / 2, glint=0.2)
    for row in range(8):
        y0 = 50 + row * 2.8
        for col in range(-10, 11):
            x0 = 64 + col * (2 + row * 1.2)
            if (row + col) % 2 == 0:
                p.poly([(x0, y0), (x0 + 2 + row * 1.2, y0), (x0 + 2 + (row + 1) * 1.2, y0 + 2.8), (x0 + (row + 1) * 0.0, y0 + 2.8)], "#e8e0d0", 0.8)
    p.rect(0, 50, 128, 72, "#14101a", 0.35)
    for x, y, r in ((40, 10, 7), (64, 14, 4), (88, 10, 7)):
        chandelier(p, x, y, r)
    return finish(p, 87)


def mirror_maze():
    """Mirror walls folding away in every direction, your own lamp shown back a hundred times."""
    p = Pic()
    p.vgrad([(0, "#1a1a24"), (1, "#3a3a48")])
    rng = random.Random(89)
    for k in range(14):
        x = rng.uniform(0, 120)
        w = rng.uniform(4, 14)
        top = rng.uniform(8, 20)
        p.rect(x, top, x + w, 60, rng.choice(["#6a7a9a", "#8aa0c0", "#5a6a8a"]), 0.85)
        p.rect(x, top - 1, x + w, top, "#c8a040")
        p.poly([(x + 1, 58), (x + 2.4, 58), (x + w - 0.5, top + 2), (x + w - 1.9, top + 2)], "#ffffff", 0.15)
        p.glow(x + w / 2, 42, 3, "#ffd890", 0.35)
        p.ellipse(x + w / 2, 42, 0.5, 0.7, "#fff0c0")
    p.rect(0, 60, 128, 72, "#2a2a34")
    p.rect(0, 60, 128, 61, "#c8a040", 0.4)
    p.glow(64, 46, 10, "#ffd890", 0.5)
    return finish(p, 88)


def inverted_palace():
    """The palace upside down: a ceiling of chandeliers standing upright on the floor, the floor hanging overhead."""
    p = Pic()
    p.vgrad([(0, "#14101a"), (0.5, "#2a2434"), (1, "#3a3440")])
    for row in range(5):
        y0 = row * 2.8
        for col in range(-10, 11):
            if (row + col) % 2 == 0:
                x0 = 64 + col * (2 + (5 - row) * 1.2)
                p.rect(x0, y0, x0 + 2 + (5 - row) * 1.2, y0 + 2.8, "#e8e0d0", 0.6)
    for x in (16, 112):
        column(p, x, 14, 60, 2.4, "#c8b890")
    for x, r in ((40, 8), (88, 8), (64, 5)):
        p.ellipse(x, 60, r, r * 0.3, "#c8a040")
        for k in range(7):
            xx = x - r + 2 * r * k / 6
            p.ellipse(xx, 61.2, 0.4, 0.8, "#ffd890")
        p.line([(x, 60), (x, 50)], "#4a3a20", 0.3)
        p.glow(x, 60, r * 2.4, "#ffd890", 0.4)
    p.rect(0, 62, 128, 72, "#c8c0b0")
    for x in range(0, 128, 10):
        p.rect(x, 62, x + 5, 72, "#a89880")
    return finish(p, 89)


def the_white():
    """The White: nothing but white to every side, one door standing alone in it, a whale far off in the haze."""
    p = Pic()
    p.vgrad([(0, "#f4f4f8"), (1, "#e8e8f0")])
    for x in range(0, 128, 8):
        for y in range(48, 72, 4):
            if (x // 8 + y // 4) % 2 == 0:
                p.rect(x, y, x + 4, y + 0.6, "#ffffff")
    p.ellipse(90, 18, 18, 3.6, "#c8c8d0", 0.7, blur=0.8)
    p.poly([(106, 18), (114, 14), (112, 18), (114, 22)], "#c8c8d0", 0.7, blur=0.8)
    p.ellipse(76, 17, 0.6, 0.6, "#8aa0c0", 0.8)
    p.rect(52, 30, 62, 52, "#c8a040")
    p.rect(53.2, 31.2, 60.8, 52, "#14101a")
    p.glow(57, 41, 6, "#8aa0c0", 0.3)
    p.ellipse(57, 53, 9, 1.2, "#d0d0dc", 0.6)
    return finish(p, 90, top=0.05, bottom=0.05, sides=0.12)


def room_behind_glass():
    """The room behind the glass: a little palace room seen backwards, a clock running the wrong way, candles."""
    p = Pic()
    p.vgrad([(0, "#2a2034"), (1, "#14101a")])
    p.rect(8, 6, 120, 62, "#3a3048")
    for x in (8, 120):
        p.rect(x - 1.5, 6, x + 1.5, 62, "#c8a040")
    p.rect(8, 4.5, 120, 7.5, "#c8a040")
    p.ellipse(64, 26, 10, 10, "#c8a040")
    p.ellipse(64, 26, 8.5, 8.5, "#f4ecd8")
    for k in range(12):
        a = -k * math.pi / 6
        p.ellipse(64 + math.cos(a) * 7, 26 + math.sin(a) * 7, 0.4, 0.4, "#3a3048")
    p.line([(64, 26), (60, 22)], "#14101a", 0.5)
    p.line([(64, 26), (64, 20)], "#14101a", 0.4)
    for x in (30, 98):
        p.rect(x - 1, 40, x + 1, 56, "#c8a040")
        for k in (-3, 0, 3):
            p.ellipse(x + k, 39, 0.5, 0.9, "#ffd890")
        p.glow(x, 38, 8, "#ffd890", 0.4)
    p.rect(8, 56, 120, 62, "#e8e0d0")
    p.rect(0, 0, 128, 72, "#8aa0c0", 0.1)
    p.poly([(10, 60), (20, 60), (60, 8), (50, 8)], "#ffffff", 0.08)
    return finish(p, 91)


# ---- the Precursor Lab ----------------------------------------------------------------------------------------------

def facility():
    """A white corridor of the lab: panels and light strips running away, a door at the end, a bench with a hologram."""
    p = Pic()
    p.vgrad([(0, "#aab4bc"), (0.5, "#ccd4d8"), (1, "#606873")])
    p.poly([(0, 0), (52, 26), (52, 46), (0, 72)], "#b8c2c8")
    p.poly([(128, 0), (76, 26), (76, 46), (128, 72)], "#a2acb4")
    p.poly([(0, 72), (52, 46), (76, 46), (128, 72)], "#515862")
    p.poly([(0, 0), (52, 26), (76, 26), (128, 0)], "#dde3e6")
    for t in (0.0, 0.3, 0.55, 0.75):
        y = t * 26
        p.line([(t * 52, y + 6 * (1 - t)), (128 - t * 52, y + 6 * (1 - t))], "#f0ffff", 1.4 * (1 - t) + 0.3)
    p.rect(58, 30, 70, 46, "#14171c")
    p.rect(58, 30, 70, 31, "#5ae0ec")
    p.glow(64, 31, 6, "#5ae0ec", 0.4)
    p.rect(14, 52, 34, 56, "#2a2f38")
    p.glow(24, 47, 6, "#5ae0ec", 0.45)
    p.line([(24, 52), (24, 44)], "#b0fcff", 0.3)
    p.ellipse(22.5, 45, 1.4, 0.8, "#5ae0ec", 0.8)
    p.ellipse(25.5, 46, 1.2, 0.7, "#5ae0ec", 0.8)
    return finish(p, 92, top=0.1, bottom=0.25)


def server_abyss():
    """The Server Abyss: towers of racks dropping into the dark, a catwalk across, a million little lights."""
    p = Pic()
    p.vgrad([(0, "#0a0c10"), (1, "#020304")])
    for i, (x, w, top) in enumerate(((2, 14, 4), (20, 10, 10), (96, 12, 8), (112, 14, 2), (40, 8, 18), (80, 8, 16))):
        rack(p, x, x + w, top, 72, 95 + i)
    p.rect(0, 34, 128, 36, "#1e2229")
    for x in range(0, 128, 6):
        p.line([(x, 34), (x, 30)], "#1e2229", 0.4)
    p.line([(0, 30), (128, 30)], "#2a2f38", 0.5)
    p.glow(64, 70, 30, "#1a8c9c", 0.3, squash=0.4)
    motes(p, 97, 40, "#40e0ff", (0, 36, W, H), (0.2, 0.4), (0.3, 0.8))
    return finish(p, 93)


def containment():
    """Containment: rows of glowing tanks with shapes asleep in them, one broken open and spilling green."""
    p = Pic()
    p.vgrad([(0, "#08140e"), (0.6, "#14281e"), (1, "#060c08")])
    for i, (x, w, h, b) in enumerate(((14, 6, 40, 60), (36, 5, 32, 56), (92, 5, 32, 56), (114, 6, 40, 60), (54, 3.5, 22, 50), (74, 3.5, 22, 50))):
        tank(p, x, b, w, h, broken=(i == 3))
    p.rect(0, 60, 128, 72, "#1c2228")
    p.ellipse(114, 64, 18, 3, "#3e9e4e", 0.6)
    p.glow(114, 64, 14, "#6ad070", 0.3, squash=0.3)
    p.line([(0, 4), (128, 4)], "#2a2f38", 1.0)
    for x in range(10, 128, 20):
        p.line([(x, 4), (x + 3, 12), (x + 1, 16)], "#14171c", 0.5)
    return finish(p, 94)


def simulation():
    """The Simulation: a world in cyan lines on dark blue, half of it drawn in grey where someone has walked."""
    p = Pic()
    p.vgrad([(0, "#040a16"), (1, "#122448")])
    for k in range(13):
        y = 40 + k * k * 0.22
        p.line([(0, y), (128, y)], "#2ab8c8", 0.25, 0.6)
    for k in range(-12, 13):
        p.line([(64 + k * 2, 40), (64 + k * 14, 72)], "#2ab8c8", 0.25, 0.6)
    for x, h in ((20, 14), (36, 22), (100, 18), (112, 10)):
        p.line([(x, 40), (x, 40 - h)], "#5ae0ec", 0.4)
        p.line([(x - 6, 40 - h * 0.4), (x, 40 - h), (x + 6, 40 - h * 0.4), (x - 6, 40 - h * 0.4)], "#5ae0ec", 0.4)
    ridge(p, 40, 8, "#122448", 51, 0.6)
    p.poly([(44, 72), (56, 44), (72, 44), (84, 72)], "#c0c8ce", 0.85)
    p.poly([(56, 44), (60, 30), (66, 26), (72, 44)], "#aeb8c0", 0.85)
    p.ellipse(64, 28, 6, 5, "#9aa4ae", 0.85)
    p.glow(64, 50, 16, "#b0fcff", 0.25)
    return finish(p, 95)


def garden_dome():
    """The Garden Dome: a glass dome full of green kept alive by the last machine, birches, flowers, light falling in."""
    p = Pic()
    p.vgrad([(0, "#1a3a4a"), (1, "#0a1a14")])
    p.ellipse(64, 62, 62, 60, "#2a5a5a", 0.5)
    for k in range(9):
        a = math.pi * (k + 0.5) / 9
        p.line([(64, 2), (64 - math.cos(a) * 62, 62)], "#0a0c10", 0.5)
    for r in (20, 40):
        p.ellipse(64, 62, r * 1.5, r, "#0a0c10", 0.0)
    p.poly([(56, 2), (72, 2), (90, 60), (38, 60)], "#e8fcff", 0.1, blur=1.0)
    for x, h in ((30, 30), (46, 38), (82, 34), (98, 28)):
        p.rect(x - 0.8, 60 - h, x + 0.8, 60, "#e8e8e0")
        p.ellipse(x, 60 - h, 7, 9, "#5aa040", 0.9)
        p.ellipse(x + 2, 60 - h - 2, 4, 5, "#7ac050", 0.8)
    p.rect(0, 58, 128, 72, "#2a5a2a")
    rng = random.Random(97)
    for _ in range(30):
        p.ellipse(rng.uniform(10, 118), rng.uniform(59, 70), 0.6, 0.6, rng.choice(["#e8c040", "#e85a8a", "#f0f0f0"]))
    motes(p, 99, 20, "#e8fcff", (30, 0, 98, 58), (0.3, 0.5), (0.3, 0.6))
    return finish(p, 96)


# ---- the Nightmare --------------------------------------------------------------------------------------------------

def the_house():
    """The House: a child's bedroom dreamed far too big, the bed miles off, a door ajar with light behind it."""
    p = Pic()
    p.vgrad([(0, "#2a1430"), (0.62, "#3a1c42"), (0.63, "#4c301c"), (1, "#3a2414")])
    for x in range(0, 128, 8):
        p.line([(x, 0), (x, 45)], "#4a2654", 0.4)
    p.rect(0, 44, 128, 46, "#1c1008")
    window(p, 14, 10, 14, 18)
    bed(p, 72, 46, 1.4)
    p.rect(104, 16, 118, 46, "#1c1008")
    p.rect(106, 18, 116, 46, "#ffd890")
    p.glow(110, 32, 14, "#ffd890", 0.35)
    p.rect(108, 18, 116, 46, "#2a1810")
    toy(p, 40, 52, 4, "#c83838")
    toy(p, 46, 50, 3, "#3a6ac8")
    p.glow(40, 30, 10, "#e09a30", 0.2)
    return finish(p, 97)


def endless_hall():
    """The Endless Hall: the same stretch of corridor over and over, doors on both sides, the light at the end never nearer."""
    p = Pic()
    p.vgrad([(0, "#120c1a"), (1, "#2a2034")])
    for k in range(8):
        t = 1 - 0.6 ** (k + 1)
        xl, xr = t * 58, 128 - t * 58
        yt, yb = t * 30, 72 - t * 28
        p.rect(xl, yt, xr, yb, "#3a1c42" if k % 2 == 0 else "#2a1430", 0.9)
        dw = (xr - xl) * 0.07
        p.rect(xl + dw * 0.5, yb - (yb - yt) * 0.55, xl + dw * 1.5, yb, "#1c1008")
        p.rect(xr - dw * 1.5, yb - (yb - yt) * 0.55, xr - dw * 0.5, yb, "#1c1008")
        p.ellipse((xl + xr) / 2, yt + (yb - yt) * 0.15, dw * 0.4, dw * 0.25, "#ffc860")
    p.rect(60, 30, 68, 44, "#fff4c8")
    p.glow(64, 37, 10, "#fff4c8", 0.5)
    p.poly([(0, 72), (58, 44), (70, 44), (128, 72)], "#4c301c", 0.8)
    return finish(p, 98)


def upside_down():
    """Upside Down: the bedroom hanging from the ceiling, the bed and toys stuck overhead, the floor a long way down."""
    p = Pic()
    p.vgrad([(0, "#4c301c"), (0.25, "#2a1430"), (1, "#08040c")])
    p.rect(0, 0, 128, 6, "#5e3c24")
    for x in range(0, 128, 9):
        p.line([(x, 0), (x, 6)], "#3a2414", 0.4)
    p.rect(30, 6, 50, 9, "#382218")
    p.rect(30.5, 9, 49.5, 10.6, "#e2e2ea")
    p.rect(30, 6, 31, 14, "#382218")
    p.rect(49, 6, 50, 12, "#382218")
    toy(p, 70, 10, 4, "#c83838")
    p.rect(88, 6, 89, 14, "#5e3c24")
    p.ellipse(88.5, 16, 3, 2.4, "#ffc860")
    p.glow(88.5, 16, 10, "#ffc860", 0.45)
    window(p, 104, 20, 12, 14)
    motes(p, 101, 30, "#c8a0ff", (0, 20, W, H), (0.3, 0.5), (0.3, 0.7))
    return finish(p, 99)


def sleepers_dream():
    """The Sleeper's Dream: islands of bedroom and garden floating in the dark, joined by candlelit planks."""
    p = Pic()
    p.vgrad([(0, "#020208"), (1, "#0c0c20")])
    stars(p, 103, 40, (0, 0, W, H))
    for i, (cx, cy, r, top) in enumerate(((24, 40, 16, "#5e3c24"), (70, 30, 20, "#3e7a3a"), (110, 46, 14, "#a8446e"))):
        p.ellipse(cx, cy, r, r * 0.3, top)
        p.poly([(cx - r, cy), (cx + r, cy), (cx + r * 0.2, cy + r * 0.6), (cx - r * 0.2, cy + r * 0.6)], "#2a1c34")
        p.glow(cx, cy, r * 1.2, "#c8a0ff", 0.12, squash=0.3)
    bed(p, 18, 39, 0.9)
    for x in (62, 68, 74, 80):
        p.line([(x, 30), (x, 26)], "#3a2414", 0.4)
        p.ellipse(x, 24.8, 1.6, 1.8, "#e85a5a")
    p.rect(106, 42, 112, 46, "#c83838")
    for (xa, ya), (xb, yb) in (((40, 40), (50, 32)), ((90, 32), (96, 45))):
        p.line([(xa, ya), (xb, yb)], "#5e3c24", 0.9)
        p.ellipse((xa + xb) / 2, (ya + yb) / 2 - 1.5, 0.4, 0.8, "#ffc860")
        p.glow((xa + xb) / 2, (ya + yb) / 2 - 1.5, 4, "#ffc860", 0.4)
    return finish(p, 100)


def nursery():
    """The Nursery: a cot under a mobile of stars, a night light glowing, toys on the rug, the lullaby nearly heard."""
    p = Pic()
    p.vgrad([(0, "#3a2a3a"), (0.62, "#2a1a2a"), (0.63, "#5e3c24"), (1, "#3a2414")])
    p.ellipse(64, 60, 30, 6, "#a8446e", 0.8)
    p.rect(48, 34, 80, 52, "#e2e2ea", 0.0)
    for x in range(48, 81, 3):
        p.line([(x, 34), (x, 52)], "#e8dcc0", 0.5)
    p.rect(47, 33, 81, 35, "#e8dcc0")
    p.rect(47, 50, 81, 52, "#e8dcc0")
    p.rect(49, 44, 79, 50, "#e2e2ea", 0.7)
    p.line([(64, 0), (64, 14)], "#8a7a6a", 0.3)
    p.line([(54, 14), (74, 14)], "#8a7a6a", 0.3)
    for k, x in enumerate((54, 60, 68, 74)):
        p.line([(x, 14), (x, 18 + k % 2 * 2)], "#8a7a6a", 0.2)
        p.ellipse(x, 19 + k % 2 * 2, 1.1, 1.1, ("#fff4c8", "#c8a0ff", "#a8c8ff", "#fff4c8")[k])
    p.ellipse(100, 50, 3, 3.6, "#c8d0f0")
    p.glow(100, 49, 14, "#fff4c8", 0.45)
    toy(p, 30, 62, 4, "#c83838")
    toy(p, 36, 64, 3, "#3aa05a")
    window(p, 12, 12, 12, 16)
    return finish(p, 101)


PAINTINGS = {
    "frost_monastery/cloisters": cloisters,
    "frost_monastery/ice_caves": ice_caves,
    "frost_monastery/cliff_stair": cliff_stair,
    "frost_monastery/still_snow": still_snow,
    "frost_monastery/frozen_choir": frozen_choir,
    "mirror_palace/gallery": gallery,
    "mirror_palace/mirror_maze": mirror_maze,
    "mirror_palace/inverted_palace": inverted_palace,
    "mirror_palace/the_white": the_white,
    "mirror_palace/room_behind_glass": room_behind_glass,
    "precursor_lab/facility": facility,
    "precursor_lab/server_abyss": server_abyss,
    "precursor_lab/containment": containment,
    "precursor_lab/simulation": simulation,
    "precursor_lab/garden_dome": garden_dome,
    "nightmare/the_house": the_house,
    "nightmare/endless_hall": endless_hall,
    "nightmare/upside_down": upside_down,
    "nightmare/sleepers_dream": sleepers_dream,
    "nightmare/nursery": nursery,
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
