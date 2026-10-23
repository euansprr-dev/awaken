"""AWAKEN pixel-art toolkit.

Every texture in the mod is generated from code with one shared palette, so the whole mod reads as one game and can be
re-tuned in one place. Deterministic: the same name always produces the same pixels.

Usage from a module spec (tools/art/specs/<module>.py):

    from pixelart import *
    def build(out):
        out.block("gate_frame", stone("obsidian", seed="gate_frame", bevel=True, glyphs="signal"))
        out.item("power_core", orb("signal"))

Run everything: python3 tools/art/build_all.py
"""
from __future__ import annotations

import hashlib
import json
import math
import os
import random
from dataclasses import dataclass

from PIL import Image

# ---------------------------------------------------------------------------------------------------------------------
# Palette. Ramps go dark -> light. Keep ramps short (4-6) so textures stay readable at 16x16.
RAMPS: dict[str, list[str]] = {
    "slate":     ["#14161d", "#1f232d", "#2c3240", "#3c4456", "#515b70", "#6a7590"],
    "stone":     ["#2b2a2e", "#3d3c41", "#525057", "#6a6870", "#84828b", "#a19fa8"],
    "obsidian":  ["#0b0a12", "#141221", "#1e1a33", "#2a2447", "#3a3260"],
    "precursor": ["#121a22", "#1b2733", "#263646", "#34495d", "#47627a", "#62829c"],
    "brass":     ["#3b2a12", "#5c4119", "#80601f", "#a8832b", "#cfa843", "#ecd27a"],
    "amber":     ["#4a2a06", "#7a460b", "#b26a12", "#e08f1f", "#f2a541", "#ffd28a"],
    "signal":    ["#0a1a2e", "#123056", "#1d5591", "#3c86cc", "#7fc3ff", "#d6ecff"],
    "spirit":    ["#120f2c", "#211a52", "#33307f", "#4c50b3", "#7a86e6", "#c3cbff"],
    "dream":     ["#2a1532", "#4a2357", "#73367c", "#a2569f", "#d088c2", "#f6c6e6"],
    "crimson":   ["#260608", "#4a0d12", "#771820", "#a8262f", "#d84a4f", "#ff8a7a"],
    "bone":      ["#4f4636", "#6e6450", "#918670", "#b6ac93", "#d6ceb6", "#f1ead6"],
    "moss":      ["#142012", "#1f331b", "#2e4a26", "#436634", "#5f8746", "#86ad5f"],
    "frost":     ["#1a2a3a", "#2a4560", "#406a8c", "#6b98b8", "#a7cde3", "#e6f6ff"],
    "ember":     ["#2a0c05", "#55180a", "#8a2a0e", "#c44a12", "#f07a22", "#ffc15a"],
    "sand":      ["#4d3b22", "#6e5631", "#937646", "#b8975e", "#d9bb80", "#f2dcaa"],
    "mirror":    ["#2a2f38", "#454d5a", "#69737f", "#9aa4ae", "#cdd5dc", "#f5f8fb"],
    "abyss":     ["#05040a", "#0d0a17", "#171127", "#231a3b", "#33255a"],
    "fungal":    ["#1e1426", "#33213c", "#4f3157", "#6e4a6b", "#9a6f86", "#c9a0b0"],
    "spore":     ["#3a3a12", "#5c5c1b", "#8a8a2a", "#b8b63f", "#e2df6a", "#fffcae"],
    "water":     ["#071a2a", "#0d2d47", "#154468", "#22628f", "#3d88b5", "#79b8dc"],
    "cloud":     ["#8d93a8", "#a9aec0", "#c4c8d6", "#dcdfe8", "#eef0f5", "#ffffff"],
    "wood":      ["#2a1a0e", "#3f2815", "#583a1e", "#74502a", "#93693a", "#b58a52"],
    "leaf":      ["#0f2414", "#173a1f", "#21522b", "#2f6e3a", "#45904f", "#6db46a"],
    "night":     ["#05070d", "#0a0f1c", "#111a2e", "#1b2742", "#28385a"],
}

RANK_COLOURS = {"E": "#f4f6fb", "D": "#5fd08f", "C": "#6fb3ff", "B": "#b48cff", "A": "#f2a541", "S": "#ff5a6a",
                "SOVEREIGN": "#d4af37"}

OUTLINE = "#07080c"


def hex_rgba(h: str, a: int = 255) -> tuple[int, int, int, int]:
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a


def ramp(name: str) -> list[tuple[int, int, int, int]]:
    return [hex_rgba(c) for c in RAMPS[name]]


def rng_for(*parts) -> random.Random:
    key = "|".join(str(p) for p in parts)
    return random.Random(int(hashlib.sha256(key.encode()).hexdigest()[:16], 16))


# ---------------------------------------------------------------------------------------------------------------------
# Canvas


@dataclass
class Canvas:
    w: int = 16
    h: int = 16

    def __post_init__(self):
        self.px = [[(0, 0, 0, 0) for _ in range(self.w)] for _ in range(self.h)]

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[y][x]
        return (0, 0, 0, 0)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            if len(c) == 3:
                c = (*c, 255)
            self.px[y][x] = c

    def blend(self, x, y, c, a: float):
        if not (0 <= x < self.w and 0 <= y < self.h):
            return
        r, g, b, al = self.px[y][x]
        cr, cg, cb = c[:3]
        self.px[y][x] = (round(r + (cr - r) * a), round(g + (cg - g) * a), round(b + (cb - b) * a), max(al, round(255 * a)))

    def image(self) -> Image.Image:
        img = Image.new("RGBA", (self.w, self.h))
        img.putdata([p for row in self.px for p in row])
        return img

    def copy(self) -> "Canvas":
        c = Canvas(self.w, self.h)
        c.px = [row[:] for row in self.px]
        return c


def value_noise(w, h, rng: random.Random, scale=4, octaves=2):
    """Tileable value noise in [0,1]."""
    out = [[0.0] * w for _ in range(h)]
    amp, total = 1.0, 0.0
    for o in range(octaves):
        s = max(1, scale * (2 ** o))
        grid = [[rng.random() for _ in range(s)] for _ in range(s)]
        for y in range(h):
            for x in range(w):
                gx, gy = x * s / w, y * s / h
                x0, y0 = int(gx) % s, int(gy) % s
                x1, y1 = (x0 + 1) % s, (y0 + 1) % s
                fx, fy = gx - int(gx), gy - int(gy)
                fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
                a = grid[y0][x0] * (1 - fx) + grid[y0][x1] * fx
                b = grid[y1][x0] * (1 - fx) + grid[y1][x1] * fx
                out[y][x] += (a * (1 - fy) + b * fy) * amp
        total += amp
        amp *= 0.5
    return [[v / total for v in row] for row in out]


def quantize(v: float, n: int) -> int:
    return max(0, min(n - 1, int(v * n)))


# ---------------------------------------------------------------------------------------------------------------------
# Block materials


def stone(ramp_name="stone", seed="", scale=4, contrast=1.0, bevel=False, lo=0, hi=None, w=16, h=16) -> Canvas:
    rp = ramp(ramp_name)
    hi = len(rp) - 1 if hi is None else hi
    rng = rng_for("stone", ramp_name, seed)
    n = value_noise(w, h, rng, scale=scale, octaves=3)
    c = Canvas(w, h)
    for y in range(h):
        for x in range(w):
            v = 0.5 + (n[y][x] - 0.5) * contrast
            v += (rng.random() - 0.5) * 0.18
            idx = lo + quantize(v, hi - lo + 1)
            c.set(x, y, rp[idx])
    if bevel:
        add_bevel(c, rp)
    return c


def add_bevel(c: Canvas, rp, strength=1):
    for i in range(c.w):
        c.set(i, 0, rp[min(len(rp) - 1, len(rp) - 2 + 0)])
        c.set(i, c.h - 1, rp[0])
    for j in range(c.h):
        c.set(0, j, rp[min(len(rp) - 1, len(rp) - 2)])
        c.set(c.w - 1, j, rp[0])


def bricks(ramp_name="stone", seed="", brick_w=8, brick_h=4, mortar=0, w=16, h=16) -> Canvas:
    rp = ramp(ramp_name)
    base = stone(ramp_name, seed=seed, lo=1, hi=len(rp) - 2, w=w, h=h)
    for y in range(h):
        row = y // brick_h
        off = (brick_w // 2) if row % 2 else 0
        for x in range(w):
            if y % brick_h == brick_h - 1 or (x + off) % brick_w == brick_w - 1:
                base.set(x, y, rp[mortar])
            elif y % brick_h == 0:
                base.set(x, y, rp[min(len(rp) - 1, len(rp) - 2)])
    return base


def plates(ramp_name="precursor", seed="", size=8, rivets=True, w=16, h=16) -> Canvas:
    rp = ramp(ramp_name)
    c = stone(ramp_name, seed=seed, scale=2, contrast=0.6, lo=2, hi=3, w=w, h=h)
    for y in range(h):
        for x in range(w):
            if x % size == 0 or y % size == 0:
                c.set(x, y, rp[1])
            elif x % size == size - 1 or y % size == size - 1:
                c.set(x, y, rp[4] if len(rp) > 4 else rp[-1])
    if rivets:
        for oy in range(0, h, size):
            for ox in range(0, w, size):
                c.set(ox + 2, oy + 2, rp[-1])
                c.set(ox + size - 3, oy + size - 3, rp[0])
    return c


def circuits(c: Canvas, glow_ramp="signal", seed="", lines=3, glow=True):
    """Precursor glyph lines: Manhattan random walks with bright nodes."""
    rg = ramp(glow_ramp)
    rng = rng_for("circuit", seed)
    for _ in range(lines):
        x, y = rng.randrange(2, c.w - 2), rng.randrange(2, c.h - 2)
        length = rng.randrange(5, 12)
        dx, dy = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
        for i in range(length):
            c.set(x, y, rg[3])
            if glow:
                for ax, ay in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if c.get(x + ax, y + ay)[:3] != rg[3][:3]:
                        c.blend(x + ax, y + ay, rg[2], 0.35)
            if rng.random() < 0.3:
                dx, dy = rng.choice([(dy, dx), (-dy, -dx)])
            x = max(1, min(c.w - 2, x + dx))
            y = max(1, min(c.h - 2, y + dy))
        c.set(x, y, rg[5])
    return c


def glyph(c: Canvas, glow_ramp="amber", seed="", cx=None, cy=None, size=5):
    """A small symmetric rune centred on the canvas."""
    rg = ramp(glow_ramp)
    rng = rng_for("glyph", seed)
    cx = c.w // 2 if cx is None else cx
    cy = c.h // 2 if cy is None else cy
    half = size // 2
    pattern = [[rng.random() < 0.45 for _ in range(half + 1)] for _ in range(size)]
    pattern[half][half] = True
    for j in range(size):
        for i in range(half + 1):
            if pattern[j][i]:
                for xx in (cx - half + i, cx + half - i):
                    c.set(xx, cy - half + j, rg[4])
    for j in range(size + 2):
        for i in range(size + 2):
            x, y = cx - half - 1 + i, cy - half - 1 + j
            if c.get(x, y)[:3] != rg[4][:3]:
                c.blend(x, y, rg[2], 0.25)
    return c


def crystal(c: Canvas, ramp_name="signal", seed="", count=3):
    rp = ramp(ramp_name)
    rng = rng_for("crystal", seed)
    for _ in range(count):
        x, y = rng.randrange(3, c.w - 3), rng.randrange(3, c.h - 3)
        r = rng.randrange(1, 3)
        for j in range(-r - 1, r + 2):
            for i in range(-r - 1, r + 2):
                d = abs(i) + abs(j)
                if d <= r:
                    c.set(x + i, y + j, rp[4 if (i + j) < 0 else 3])
                elif d == r + 1:
                    c.set(x + i, y + j, rp[1])
        c.set(x - 1, y - 1, rp[-1])
    return c


def overlay_noise(c: Canvas, ramp_name, seed="", density=0.15, idx=3):
    rp = ramp(ramp_name)
    rng = rng_for("speck", ramp_name, seed)
    for y in range(c.h):
        for x in range(c.w):
            if rng.random() < density:
                c.set(x, y, rp[idx])
    return c


def frame_border(c: Canvas, ramp_name="brass", width=1):
    rp = ramp(ramp_name)
    for k in range(width):
        for i in range(c.w):
            c.set(i, k, rp[4 - k])
            c.set(i, c.h - 1 - k, rp[1])
        for j in range(c.h):
            c.set(k, j, rp[3])
            c.set(c.w - 1 - k, j, rp[1])
    return c


def portal_frames(ramp_name="signal", seed="", frames=16, w=16, h=16) -> list[Canvas]:
    """Animated swirl for gate cores. Returns frames for a vertical strip + mcmeta."""
    rp = ramp(ramp_name)
    out = []
    for f in range(frames):
        c = Canvas(w, h)
        t = f / frames * math.tau
        for y in range(h):
            for x in range(w):
                dx, dy = (x - w / 2 + 0.5) / (w / 2), (y - h / 2 + 0.5) / (h / 2)
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                v = 0.5 + 0.5 * math.sin(a * 3 + r * 7 - t * 2)
                v = v * (1 - r * 0.35) + 0.15 * math.sin(t + r * 9)
                c.set(x, y, rp[quantize(max(0.0, min(0.999, v)), len(rp))])
        out.append(c)
    return out


# ---------------------------------------------------------------------------------------------------------------------
# Item shapes (16x16, 1px dark outline, light from top-left)


def _outline(c: Canvas, col=OUTLINE):
    mask = [[c.get(x, y)[3] > 0 for x in range(c.w)] for y in range(c.h)]
    for y in range(c.h):
        for x in range(c.w):
            if not mask[y][x] and any(
                0 <= x + dx < c.w and 0 <= y + dy < c.h and mask[y + dy][x + dx]
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                c.set(x, y, hex_rgba(col))
    return c


def orb(ramp_name="signal", core_ramp=None, radius=5.5, cx=7.5, cy=7.5, ring=False) -> Canvas:
    rp = ramp(ramp_name)
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= radius:
                light = ((x - cx) * -0.6 + (y - cy) * -0.8) / radius  # top-left light
                v = 0.55 + light * 0.45 - (d / radius) * 0.25
                c.set(x, y, rp[1 + quantize(max(0, min(0.999, v)), len(rp) - 1)])
    if core_ramp:
        cr = ramp(core_ramp)
        for y in range(16):
            for x in range(16):
                if math.hypot(x - cx, y - cy) <= radius * 0.38:
                    c.set(x, y, cr[4])
    c.set(int(cx - radius * 0.45), int(cy - radius * 0.5), rp[-1])
    if ring:
        rg = ramp("brass")
        for x in range(16):
            for y in range(16):
                d = math.hypot((x - cx) * 1.0, (y - cy) * 2.6)
                if abs(d - radius * 1.25) < 0.7 and y >= cy:
                    c.set(x, y, rg[4])
    return _outline(c)


def polygon(points, ramp_name="slate", shade=True) -> Canvas:
    rp = ramp(ramp_name)
    c = Canvas()

    def inside(px, py):
        n, ins = len(points), False
        j = n - 1
        for i in range(n):
            xi, yi = points[i]
            xj, yj = points[j]
            if ((yi > py) != (yj > py)) and (px < (xj - xi) * (py - yi) / (yj - yi + 1e-9) + xi):
                ins = not ins
            j = i
        return ins

    ys = [p[1] for p in points]
    xs = [p[0] for p in points]
    for y in range(16):
        for x in range(16):
            if inside(x + 0.5, y + 0.5):
                if shade:
                    t = ((x - min(xs)) / max(1, max(xs) - min(xs)) * 0.5 + (y - min(ys)) / max(1, max(ys) - min(ys)) * 0.5)
                    c.set(x, y, rp[len(rp) - 2 - quantize(t, len(rp) - 2)])
                else:
                    c.set(x, y, rp[3])
    return _outline(c)


def blade(ramp_name="mirror", hilt="brass", gem="signal") -> Canvas:
    """A diagonal sword silhouette (bottom-left hilt to top-right tip)."""
    rp, rh, rg = ramp(ramp_name), ramp(hilt), ramp(gem)
    c = Canvas()
    for i in range(4, 15):
        x, y = i, 15 - i
        c.set(x, y, rp[4])
        c.set(x - 1, y, rp[3])
        c.set(x, y + 1, rp[2])
    c.set(14, 1, rp[5])
    for k in range(-2, 3):
        c.set(4 + k, 11 + k, rh[3])
    c.set(3, 12, rh[2]); c.set(2, 13, rh[2]); c.set(1, 14, rh[4])
    c.set(4, 11, rg[4])
    return _outline(c)


def book(cover="dream", accent="amber") -> Canvas:
    rc, ra = ramp(cover), ramp(accent)
    c = Canvas()
    for y in range(2, 14):
        for x in range(3, 13):
            c.set(x, y, rc[3] if x > 4 else rc[1])
    for y in range(3, 13):
        c.set(12, y, hex_rgba("#e9e2cf"))
    glyph(c, accent, seed="book" + cover, cx=8, cy=7, size=3)
    for x in range(5, 12):
        c.set(x, 2, rc[4])
    return _outline(c)


def pouch(ramp_name="wood", tie="brass") -> Canvas:
    rp, rt = ramp(ramp_name), ramp(tie)
    c = Canvas()
    for y in range(4, 15):
        for x in range(2, 14):
            d = math.hypot((x - 7.5) / 6, (y - 9.5) / 5.6)
            if d <= 1:
                c.set(x, y, rp[1 + quantize(0.8 - (x + y) / 40, len(rp) - 1)])
    for x in range(5, 11):
        c.set(x, 4, rt[4])
    for x in range(6, 10):
        c.set(x, 3, rp[3])
        c.set(x, 2, rp[4])
    return _outline(c)


def shard(ramp_name="signal") -> Canvas:
    return polygon([(8, 1), (12, 6), (9, 15), (5, 11), (4, 5)], ramp_name)


def device(body="precursor", screen="signal", seed="") -> Canvas:
    rb, rs = ramp(body), ramp(screen)
    c = Canvas()
    for y in range(3, 14):
        for x in range(3, 13):
            c.set(x, y, rb[3 if (x + y) % 7 else 2])
    for y in range(5, 10):
        for x in range(5, 11):
            c.set(x, y, rs[2])
    circuits(c, screen, seed=seed or "device", lines=1, glow=False)
    for y in range(5, 10):
        for x in range(5, 11):
            if c.get(x, y)[:3] == rb[3][:3]:
                c.set(x, y, rs[2])
    c.set(5, 11, ramp("amber")[4]); c.set(7, 11, rs[4]); c.set(9, 11, ramp("crimson")[4])
    return _outline(c)


def trophy(ramp_name="bone", eyes="crimson") -> Canvas:
    rp, re_ = ramp(ramp_name), ramp(eyes)
    c = Canvas()
    for y in range(2, 13):
        for x in range(3, 13):
            d = math.hypot((x - 7.5) / 5, (y - 7) / 5.5)
            if d <= 1:
                c.set(x, y, rp[2 + quantize(0.9 - (x + y) / 34, len(rp) - 2)])
    for x in (5, 6, 9, 10):
        c.set(x, 7, re_[4])
    for x in range(5, 11, 2):
        c.set(x, 11, rp[0])
    for x in range(4, 12):
        c.set(x, 14, ramp("brass")[3])
    return _outline(c)


def particle_dot(ramp_name="signal", size=8) -> Canvas:
    rp = ramp(ramp_name)
    c = Canvas(size, size)
    m = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - m, y - m) / (size / 2)
            if d < 1:
                a = int(255 * (1 - d) ** 1.5)
                r, g, b, _ = rp[4 if d > 0.4 else 5]
                c.set(x, y, (r, g, b, a))
    return c


# ---------------------------------------------------------------------------------------------------------------------
# Output


class Out:
    """Writes textures (and simple models) under src/main/resources/assets/awaken."""

    def __init__(self, root: str):
        self.root = root
        self.written: list[str] = []

    def _save(self, rel: str, c: Canvas | list[Canvas], frametime: int | None = None):
        path = os.path.join(self.root, "assets/awaken/textures", rel + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if isinstance(c, list):
            w, h = c[0].w, c[0].h
            img = Image.new("RGBA", (w, h * len(c)))
            for i, f in enumerate(c):
                img.paste(f.image(), (0, i * h))
            img.save(path)
            with open(path + ".mcmeta", "w") as m:
                json.dump({"animation": {"frametime": frametime or 2, "interpolate": True}}, m)
        else:
            c.image().save(path)
        self.written.append(rel)

    def block(self, name: str, c, frametime=None):
        self._save("block/" + name, c, frametime)

    def item(self, name: str, c):
        self._save("item/" + name, c)

    def entity(self, name: str, c):
        self._save("entity/" + name, c)

    def particle(self, name: str, c):
        self._save("particle/" + name, c)

    def gui(self, name: str, c):
        self._save("gui/" + name, c)
