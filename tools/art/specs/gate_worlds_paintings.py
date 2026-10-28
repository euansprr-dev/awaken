"""Gate Worlds, the Atlas paintings (SPEC §38.7): one small painting per world for its card in the Codex.

    python3 tools/art/build_all.py gate_worlds_paintings     textures/gui/gate_atlas/<theme>/<world>.png
    python3 tools/art/specs/gate_worlds_paintings.py sheet PATH   every painting at 4x, named

Each is drawn by code at four times its size (shapes, gradients, glows), shrunk to 128 x 72 and quantised to a small
palette with error diffusion, so it reads as a painted pixel picture of the world's best view. Nothing copied.
"""
from __future__ import annotations

import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

W, H, S = 128, 72, 4
SW, SH = W * S, H * S


# ---- a float canvas -------------------------------------------------------------------------------------------------

def rgb(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


class Pic:
    def __init__(self):
        self.a = np.zeros((SH, SW, 3))
        yy, xx = np.mgrid[0:SH, 0:SW]
        self.x = (xx + 0.5) / SW
        self.y = (yy + 0.5) / SH

    def vgrad(self, stops):
        """Vertical gradient over the whole canvas: [(y, colour), ...]."""
        ys = [s[0] for s in stops]
        for c in range(3):
            self.a[..., c] = np.interp(self.y, ys, [rgb(s[1])[c] for s in stops])

    def mask(self, draw_fn, blur=0.0) -> np.ndarray:
        m = Image.new("L", (SW, SH), 0)
        draw_fn(ImageDraw.Draw(m))
        if blur:
            m = m.filter(ImageFilter.GaussianBlur(blur * S))
        return np.asarray(m, dtype=np.float64) / 255.0

    def paint(self, m: np.ndarray, colour, alpha=1.0):
        c = rgb(colour) if isinstance(colour, str) else colour
        k = (m * alpha)[..., None]
        self.a = self.a * (1 - k) + c * k

    def poly(self, pts, colour, alpha=1.0, blur=0.0):
        self.paint(self.mask(lambda d: d.polygon([(px * S, py * S) for px, py in pts], fill=255), blur), colour, alpha)

    def rect(self, x0, y0, x1, y1, colour, alpha=1.0, blur=0.0):
        self.poly([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], colour, alpha, blur)

    def ellipse(self, cx, cy, rx, ry, colour, alpha=1.0, blur=0.0):
        self.paint(self.mask(lambda d: d.ellipse([(cx - rx) * S, (cy - ry) * S, (cx + rx) * S, (cy + ry) * S], fill=255), blur),
                   colour, alpha)

    def line(self, pts, colour, width=1.0, alpha=1.0, blur=0.0):
        self.paint(self.mask(lambda d: d.line([(px * S, py * S) for px, py in pts], fill=255, width=max(1, int(width * S))), blur),
                   colour, alpha)

    def glow(self, cx, cy, r, colour, strength=1.0, squash=1.0):
        """Additive light falling off from a point (picture pixels)."""
        dx = self.x * W - cx
        dy = (self.y * H - cy) / squash
        d2 = (dx * dx + dy * dy) / (r * r)
        k = np.exp(-d2 * 2.2) * strength
        self.a = self.a + rgb(colour) * k[..., None]

    def shade(self, top=0.0, bottom=0.0, sides=0.0):
        """Darkens towards the edges (the painting's vignette)."""
        v = 1 - top * np.clip(1 - self.y * 3, 0, 1) - bottom * np.clip(self.y * 3 - 2, 0, 1)
        v = v - sides * (np.abs(self.x - 0.5) * 2) ** 2.2
        self.a = self.a * np.clip(v, 0, 1)[..., None]

    def grain(self, seed, amount=0.03):
        r = np.random.default_rng(seed)
        n = r.normal(0, amount, (SH // S, SW // S))
        n = np.kron(n, np.ones((S, S)))
        self.a = self.a * (1 + n[..., None])

    def image(self, colours=64) -> Image.Image:
        img = Image.fromarray((np.clip(self.a, 0, 1) * 255).astype(np.uint8), "RGB")
        img = img.resize((W, H), Image.Resampling.BOX)
        q = img.quantize(colors=colours, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.FLOYDSTEINBERG)
        return q.convert("RGBA")


def bell(p: Pic, cx, top, r, h, colour, lip=None, alpha=1.0, blur=0.0):
    """A bell's silhouette: crown, shoulder, waist and flared lip (r = lip radius)."""
    pts = []
    for i in range(21):
        t = i / 20
        w = r * (0.42 + 0.18 * math.sin(t * math.pi * 0.5) + 0.40 * t ** 3.2)
        pts.append((cx - w, top + t * h))
    right = [(2 * cx - x, y) for x, y in reversed(pts)]
    crown = [(cx - r * 0.42, top), (cx - r * 0.25, top - h * 0.08), (cx + r * 0.25, top - h * 0.08), (cx + r * 0.42, top)]
    p.poly(crown + right + pts[::-1][::-1], colour, alpha, blur)
    p.poly(pts + right, colour, alpha, blur)
    if lip:
        p.ellipse(cx, top + h, r, h * 0.08, lip, alpha, blur)


# ---- the five Bell Crypt worlds -------------------------------------------------------------------------------------

def sunken_belfry() -> Image.Image:
    """Down the drowned nave: arches either side, the rose window at the end, the great bell before it, water below."""
    p = Pic()
    p.vgrad([(0, "#05060b"), (0.45, "#0b1120"), (0.62, "#0a1318"), (1, "#03060a")])
    vx = 64
    # far wall, lit by its window
    p.rect(42, 4, 86, 46, "#141c2c")
    p.glow(64, 18, 22, "#2a3a7a", 0.5)
    # the rose window: a ring of coloured lights in a dark stone wheel, lead spokes between them
    p.ellipse(64, 17, 13, 13, "#06070c")
    cols = ["#5274ee", "#e04a4a", "#f0b84a", "#66c8e6", "#9a52d6", "#5274ee", "#e04a4a", "#f0b84a", "#66c8e6", "#9a52d6"]
    for k, col in enumerate(cols):
        a = k / len(cols) * math.tau
        x, y = 64 + math.cos(a) * 8.2, 17 + math.sin(a) * 8.2
        p.ellipse(x, y, 2.9, 2.9, col)
        p.glow(x, y, 3.5, col, 0.35)
    for k in range(5):
        a = k / 5 * math.tau
        p.ellipse(64 + math.cos(a) * 3.6, 17 + math.sin(a) * 3.6, 1.8, 1.8, "#9db4dc")
    p.ellipse(64, 17, 1.6, 1.6, "#fff2cc")
    p.glow(64, 17, 2.5, "#fff2cc", 0.6)
    # arcades receding on both sides
    for side in (-1, 1):
        for i in range(6):
            t = i / 6
            x = vx + side * (60 - 50 * t ** 0.7)
            top = 6 + 14 * t ** 0.8
            bot = 52 - 16 * t ** 0.8
            wcol = 3.4 * (1 - t) + 0.8
            shade = np.array([0.20, 0.21, 0.25]) * (1 - 0.55 * t)
            p.rect(x - wcol, top, x + wcol, bot, shade)
            p.rect(x - wcol, top, x - wcol + 0.8, bot, shade * 1.5)
            nx = vx + side * (60 - 50 * ((i + 1) / 6) ** 0.7)
            mxp = (x + nx) / 2
            p.line([(x, top + 6 * (1 - t)), (mxp, top - 2), (nx, top + 6 * (1 - t))], shade * 1.3, 1.4 * (1 - t) + 0.5)
        for i in range(3):  # moonlight from the high windows
            t = (i + 0.5) / 4
            x = vx + side * (52 - 44 * t)
            p.poly([(x - 2, 6), (x + 2, 6), (x + side * 10 + 4, 52), (x + side * 10 - 4, 52)], "#7c96c4", 0.07, blur=1.5)
    # the great bell, hanging before the window's lower half, its patina catching the light
    p.line([(64, 0), (64, 24)], "#0c0d12", 0.8)
    bell(p, 64, 25, 8, 13, "#2a2016")
    bell(p, 64.6, 25.6, 6.6, 11.6, "#3b5a4c", alpha=0.35)
    p.line([(57, 37), (60, 29), (62, 25.5)], "#6cb09a", 0.6, 0.55)
    p.ellipse(64, 38, 8, 1.1, "#1e2a24")
    # water: the nave's floor, holding the window and the bell
    p.rect(0, 46, 128, 72, "#081218", 0.94)
    for i in range(14):
        y = 47 + i * 1.8
        p.line([(30 - i * 2, y), (98 + i * 2, y)], "#14303a", 0.25, 0.5)
    for k, col in enumerate(["#5274ee", "#e04a4a", "#f0b84a", "#66c8e6"]):
        p.rect(56 + k * 4, 47, 59 + k * 4, 68 - k * 2, col, 0.16, blur=1.2)
    p.rect(61, 47, 67, 58, "#0a0c10", 0.6, blur=0.6)
    # candle glints on the pier bases and their reflections
    for side in (-1, 1):
        for i in range(5):
            t = i / 6
            x = vx + side * (58 - 50 * t ** 0.7)
            y = 51 - 15 * t ** 0.8
            p.glow(x, y, 2.2 * (1 - t) + 0.8, "#ffb44a", 0.9 * (1 - t * 0.6))
            p.glow(x, y + 7 * (1 - t) + 1, 1.4, "#ffb44a", 0.22, squash=2.5)
    p.shade(top=0.6, bottom=0.4, sides=0.5)
    p.grain(11)
    return p.image()


def ossuary_ways() -> Image.Image:
    """A passage of bones: skulls in rows of niches, a vault overhead, one candle-lit alcove ahead."""
    p = Pic()
    p.vgrad([(0, "#050404"), (0.5, "#120e0a"), (1, "#070605")])
    vx, vy = 70, 36
    # the vault and walls as receding frames
    for i in range(10, -1, -1):
        t = i / 10
        s = 1 - 0.86 * t ** 0.75
        x0, x1 = vx - 66 * s, vx + 60 * s
        y0, y1 = vy - 36 * s, vy + 34 * s
        col = np.array([0.45, 0.40, 0.31]) * (0.18 + 0.82 * (1 - t) ** 1.6)
        p.poly([(x0, y1), (x0, y0 + 10 * s), (vx - 3 * s, y0), (x1, y0 + 10 * s), (x1, y1), (x1 - 6 * s, y1), (x1 - 6 * s, y0 + 14 * s),
                (vx, y0 + 5 * s), (x0 + 6 * s, y0 + 14 * s), (x0 + 6 * s, y1)], col)
        # rows of skulls in the side walls between frames
        if i < 10:
            for side, xw in ((-1, x0 + 3 * s), (1, x1 - 3 * s)):
                for row in range(4):
                    yy = y0 + 18 * s + row * 9 * s
                    if yy > y1 - 4 * s:
                        break
                    rs = 2.1 * s
                    bone = np.array([0.84, 0.80, 0.70]) * (0.25 + 0.75 * (1 - t) ** 1.3)
                    p.ellipse(xw, yy, rs, rs * 0.9, bone)
                    p.ellipse(xw - rs * 0.4, yy, rs * 0.28, rs * 0.32, "#0a0806")
                    p.ellipse(xw + rs * 0.4, yy, rs * 0.28, rs * 0.32, "#0a0806")
    # the far dark and the lit alcove
    p.ellipse(vx, vy, 7, 9, "#020202")
    p.rect(vx + 6, vy - 1, vx + 13, vy + 8, "#2a1a0c")
    p.glow(vx + 9.5, vy + 4, 7, "#ffb44a", 0.9)
    p.glow(vx + 9.5, vy + 3, 1.6, "#fff2cc", 1.2)
    # candles along the floor, warm pools
    for i, t in enumerate([0.15, 0.35, 0.6]):
        s = 1 - 0.86 * t ** 0.75
        x = vx - 50 * s
        y = vy + 30 * s
        p.glow(x, y - 2 * s, 5 * s + 1, "#ff8a2a", 0.8)
        p.glow(x, y - 3 * s, 0.9 * s + 0.4, "#fff2cc", 1.1)
        p.ellipse(x, y + 1, 9 * s, 2 * s, "#ffb44a", 0.12, blur=1)
    # bone dust in the air
    rng = random.Random(4)
    for _ in range(40):
        p.glow(rng.uniform(10, 120), rng.uniform(8, 64), 0.5, "#d6ceb6", rng.uniform(0.05, 0.25))
    p.shade(top=0.5, bottom=0.3, sides=0.6)
    p.grain(12)
    return p.image()


def field_of_bells() -> Image.Image:
    """Black water to nowhere, bells hung from nothing with a lamp lit in each, a causeway of lily candles."""
    p = Pic()
    p.vgrad([(0, "#020306"), (0.55, "#070a12"), (0.58, "#04060a"), (1, "#020304")])
    horizon = 40
    bells = [(20, 6, 5), (36, 16, 3.2), (50, 4, 7), (76, 12, 4.4), (96, 2, 8), (111, 18, 3.6), (64, 22, 2.4), (119, 10, 2.8),
             (8, 22, 2.4), (86, 25, 2)]
    for bx, by, r in sorted(bells, key=lambda b: b[2]):
        h = r * 1.45
        near = min(1.0, r / 8)
        p.line([(bx, 0), (bx, by - h * 0.06)], "#14161c", 0.5)
        lamp_y = by + h * 0.92
        # light spilling out of the mouth onto the dark below, then the bell's body over it
        p.poly([(bx - r * 0.9, lamp_y), (bx + r * 0.9, lamp_y), (bx + r * 1.6, lamp_y + r * 1.8), (bx - r * 1.6, lamp_y + r * 1.8)],
               "#ffb44a", 0.10, blur=1.2)
        bell(p, bx, by, r, h, np.array([0.30, 0.22, 0.12]) * (0.7 + 0.5 * near))
        bell(p, bx + r * 0.12, by + h * 0.05, r * 0.82, h * 0.9, np.array([0.48, 0.36, 0.2]) * (0.6 + 0.4 * near), alpha=0.45)
        p.ellipse(bx, lamp_y, r * 0.95, r * 0.22, "#ffd88a")
        p.glow(bx, lamp_y + r * 0.3, r * 0.9, "#ffb44a", 0.55, squash=0.5)
        # its light on the water and its reflection
        p.rect(bx - r * 0.3, horizon + 1, bx + r * 0.3, min(71, horizon + 6 + r * 3), "#ffb44a", 0.16, blur=0.8)
        p.glow(bx, horizon + 1, r * 0.9, "#ff8a2a", 0.14, squash=0.3)
    for i in range(16):  # the water's long slow ripples
        y = horizon + 1 + i * i * 0.13
        p.line([(0, y), (128, y)], "#101826", 0.25, 0.35)
    # the causeway with lily candles, coming towards you
    pts_l = [(58, horizon + 2), (54, 72)]
    pts_r = [(62, horizon + 2), (76, 72)]
    p.poly([pts_l[0], pts_r[0], pts_r[1], pts_l[1]], "#1a1c20")
    for i in range(9):
        t = (i / 8) ** 1.6
        y = horizon + 2 + (72 - horizon - 2) * t
        for side in (0, 1):
            x0, x1 = (pts_l if side == 0 else pts_r)[0][0], (pts_l if side == 0 else pts_r)[1][0]
            x = x0 + (x1 - x0) * t + (-2 if side == 0 else 2) * t
            p.ellipse(x, y + 0.6, 1.4 * t + 0.5, 0.5 * t + 0.2, "#335f3c", 0.9)
            p.glow(x, y - 0.4, 1.5 * t + 0.5, "#ffd88a", 0.7)
    p.ellipse(60, horizon + 1, 12, 1.6, "#1e2024")  # the island at its end
    p.glow(60, horizon - 1, 5, "#ffb44a", 0.3, squash=0.4)
    p.shade(top=0.2, bottom=0.5, sides=0.35)
    p.grain(13)
    return p.image()


def bell_shaft() -> Image.Image:
    """Across the shaft from the top landing: landings stepping down the far wall, the bell column, the clapper bridge."""
    p = Pic()
    p.vgrad([(0, "#0b0c11"), (0.7, "#06070a"), (1, "#0a0c14")])
    # side walls in perspective and the far wall
    p.poly([(0, 0), (22, 6), (22, 72), (0, 72)], "#1c1c20")
    p.poly([(128, 0), (106, 6), (106, 72), (128, 72)], "#18181c")
    p.rect(22, 6, 106, 72, "#121216")
    # string courses down the far wall, fading into the depth
    for i in range(9):
        y = 8 + i * 7.5
        k = 1 - i / 9
        p.line([(22, y), (106, y)], np.array([0.35, 0.33, 0.30]) * (0.3 + 0.7 * k), 0.6, 0.8)
        for side in (-1, 1):
            p.line([(64 + side * 64, y - 6 * k * 0 - 7 + i * 1.0), (64 + side * 42, y)], np.array([0.3, 0.29, 0.27]) * k, 0.5, 0.6)
    # the landings: balconies on the far wall, each a step lower, going round
    for i in range(8):
        y = 12 + i * 7.5
        x = 30 + (i * 23) % 64
        k = 1 - i / 9
        p.rect(x, y, x + 14, y + 1.2, np.array([0.42, 0.40, 0.36]) * (0.35 + 0.65 * k))
        p.rect(x, y - 3.5, x + 14, y - 3, np.array([0.3, 0.3, 0.32]) * k, 0.8)
        for c in range(3):
            p.glow(x + 3 + c * 4, y - 1.2, 1.2, "#ffb44a", 0.75 * (0.4 + 0.6 * k))
        p.glow(x + 7, y - 2, 6, "#ff8a2a", 0.07 * k)
    # the glow of the foot far below
    p.glow(64, 76, 34, "#5a6a98", 0.45, squash=0.6)
    # the bell column down the middle, each from the mouth of the one above
    y = 10
    for i, r in enumerate([9, 6.5, 5, 3.8, 2.8, 2]):
        h = r * 1.45
        p.line([(64, y - 3), (64, y)], "#0e0e12", 0.6)
        col = np.array([0.34, 0.25, 0.14]) * (1 - i * 0.1)
        bell(p, 64, y, r, h, col)
        bell(p, 64.5, y + h * 0.05, r * 0.8, h * 0.9, np.array([0.30, 0.48, 0.40]) * (1 - i * 0.12), alpha=0.35)
        p.glow(64, y + h, r * 0.7, "#ffb44a", 0.2)
        y += h + 3.5
    # the chain to the roof
    p.line([(64, 0), (64, 10)], "#16161a", 0.8)
    # the clapper bridge from your balcony out to the stand in the top bell's mouth
    p.poly([(0, 60), (0, 66), (57, 25.5), (57, 23.5)], "#2c2b2e")
    p.line([(0, 57), (57, 21)], "#4a4846", 0.6)
    p.line([(0, 63), (57, 23.5)], "#3a3836", 0.5)
    for i in range(8):
        t = i / 8
        x = t * 55
        y0 = 57 - 36 * t
        p.line([(x, y0), (x, y0 + 3 - 2 * t)], "#1c1c1e", 0.5)
        if i % 3 == 1:
            p.glow(x, y0 + 1, 1.3, "#ffb44a", 0.6)
    # your balcony's railing in the foreground
    p.rect(0, 66, 128, 72, "#1a1a1d")
    p.line([(0, 66), (128, 66)], "#4c4a46", 1)
    for x in range(4, 128, 9):
        p.line([(x, 66), (x, 72)], "#0e0e10", 0.7)
    p.shade(top=0.35, bottom=0.1, sides=0.3)
    p.grain(14)
    return p.image()


def bellmakers_rest() -> Image.Image:
    """The secret foundry: a bell still in its mould, glowing; a furnace mouth; tools hung on the wall."""
    p = Pic()
    p.vgrad([(0, "#0a0604"), (0.6, "#1a0e08"), (1, "#0a0605")])
    # the back wall and its arches
    p.rect(0, 0, 128, 54, "#1e1410")
    for x in (18, 64, 110):
        p.ellipse(x, 20, 16, 18, "#140c08")
    # tools on the wall
    for i, x in enumerate(range(80, 124, 7)):
        p.line([(x, 14), (x + (i % 2) * 2 - 1, 32)], "#3a2b18", 0.8)
        p.ellipse(x + (i % 2) * 2 - 1, 32, 1.4, 1, "#5a4a3a")
    # the furnace mouth, left
    p.rect(6, 26, 30, 54, "#2a1a10")
    p.ellipse(18, 40, 8, 7, "#ff6a1a")
    p.glow(18, 40, 16, "#ff6a1a", 0.8)
    p.glow(18, 40, 4, "#fff2cc", 0.9)
    # the floor
    p.rect(0, 52, 128, 72, "#140d09")
    for i in range(6):
        p.line([(0, 54 + i * 3.4), (128, 54 + i * 3.4)], "#24170f", 0.35, 0.5)
    # the mould pit and the bell rising out of it, still hot
    p.ellipse(68, 58, 26, 5, "#0a0605")
    bell(p, 68, 28, 15, 28, "#6a2a10", lip="#ff8a2a")
    p.glow(68, 50, 20, "#ff6a1a", 0.6)
    p.glow(68, 56, 6, "#ffd88a", 0.7, squash=0.4)
    # hot seams on the bell
    for t in (0.35, 0.62, 0.85):
        y = 28 + 28 * t
        w = 15 * (0.42 + 0.18 * math.sin(t * math.pi * 0.5) + 0.40 * t ** 3.2)
        p.line([(68 - w + 1, y), (68 + w - 1, y)], "#ffb44a", 0.5, 0.8)
    # the crane and its chain
    p.line([(30, 6), (100, 6)], "#2a1a10", 2)
    p.line([(68, 6), (68, 26)], "#3a2b18", 0.8)
    # sparks
    rng = random.Random(9)
    for _ in range(30):
        p.glow(rng.uniform(40, 100), rng.uniform(10, 56), 0.5, "#ffd88a", rng.uniform(0.2, 0.7))
    p.shade(top=0.5, bottom=0.3, sides=0.4)
    p.grain(15)
    return p.image()


PAINTINGS = {
    "bell_crypt/sunken_belfry": sunken_belfry,
    "bell_crypt/ossuary_ways": ossuary_ways,
    "bell_crypt/field_of_bells": field_of_bells,
    "bell_crypt/bell_shaft": bell_shaft,
    "bell_crypt/bellmakers_rest": bellmakers_rest,
}


class _Img:
    """Lets Out save a PIL image as it saves a Canvas."""

    def __init__(self, img):
        self._img = img
        self.w, self.h = img.size

    def image(self):
        return self._img


def build(out):
    for name, fn in PAINTINGS.items():
        out.gui("gate_atlas/" + name, _Img(fn()))


def sheet(path):
    imgs = [(n, fn().resize((W * 4, H * 4), Image.Resampling.NEAREST)) for n, fn in PAINTINGS.items()]
    sheet_img = Image.new("RGBA", (W * 4 * 2 + 12, (H * 4 + 6) * ((len(imgs) + 1) // 2)), (20, 20, 24, 255))
    for i, (n, im) in enumerate(imgs):
        sheet_img.paste(im, ((i % 2) * (W * 4 + 12), (i // 2) * (H * 4 + 6)))
    sheet_img.save(path)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "sheet":
        sheet(sys.argv[2])
