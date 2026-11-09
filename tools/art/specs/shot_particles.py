"""Creature shots' own particles (SPEC §47.15; Euan 8 Nov, "just kind of green particles, like vanilla green particles"):
seven shapes that a shot's trail and burst are made of, in place of vanilla's. White with alpha, four frames each (young
to spent); BoltKind picks the shape and tints it, mobs.client.ShotParticles moves it.

    python3 tools/art/build_all.py shot_particles
"""
import json
import math
import os

from pixelart import Canvas

SIZE = 16
SS = 4
RES = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../src/main/resources"))
FRAMES = 4


def _segment(x, y, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy + 1e-9)))
    return math.hypot(x - ax - dx * t, y - ay - dy * t), t


def spark(x, y, k):
    """Four thin rays and a hot centre: a thing struck, not a glow."""
    length = (1.0, 0.86, 0.62, 0.36)[k]
    a = 0.0
    for rx, ry, reach in ((1, 0, 1.0), (0, 1, 1.0), (0.71, 0.71, 0.5), (0.71, -0.71, 0.5)):
        along = abs(x * rx + y * ry) / (length * reach)
        across = abs(x * ry - y * rx)
        if along < 1:
            a = max(a, 1.0 if across < 0.14 * (1 - along) + 0.02 else 0.0)
    return a


def ember(x, y, k):
    """A coal: hard in the middle, soft at the rim."""
    r = (0.5, 0.68, 0.56, 0.32)[k]
    d = math.hypot(x, y) / r
    return 0.0 if d >= 1 else min(1.0, (1 - d) ** 0.55 * 1.25)


def flake(x, y, k):
    """Six arms with a barb on each: ice, not a dot."""
    r = (0.95, 0.9, 0.72, 0.5)[k]
    best = 0.0
    for i in range(6):
        ang = i * math.pi / 3 + k * 0.14
        ex, ey = math.cos(ang) * r, math.sin(ang) * r
        d, _ = _segment(x, y, 0, 0, ex, ey)
        if d < 0.075:
            best = 1.0
        for side in (-0.7, 0.7):
            bx, by = ex * 0.55, ey * 0.55
            d2, _ = _segment(x, y, bx, by, bx + math.cos(ang + side) * r * 0.32, by + math.sin(ang + side) * r * 0.32)
            if d2 < 0.06:
                best = max(best, 0.85)
    return best


def wisp(x, y, k):
    """A curl of smoke that loosens as it thins."""
    best = 0.0
    for i in range(14):
        t = i / 13
        th = 0.6 + t * (3.6 + k * 0.5)
        r = 0.08 + t * (0.62 + k * 0.07)
        cx, cy = math.cos(th) * r, math.sin(th) * r
        d = math.hypot(x - cx, y - cy) / (0.2 + t * 0.13)
        if d < 1:
            best = max(best, (1 - d) ** 0.8 * (1 - 0.55 * t))
    return best * (1.0, 0.9, 0.7, 0.45)[k]


_LUMPS = ((0.0, 0.05, 0.5), (-0.38, 0.2, 0.34), (0.4, 0.14, 0.36), (-0.14, -0.34, 0.34), (0.26, -0.32, 0.3), (0.06, 0.42, 0.32))


def puff(x, y, k):
    """A lumpy cloud of spores or spray."""
    grow = (0.72, 0.88, 1.0, 1.1)[k]
    best = 0.0
    for cx, cy, r in _LUMPS:
        d = math.hypot(x - cx * grow, y - cy * grow) / (r * grow)
        if d < 1:
            best = max(best, (1 - d) ** 0.7)
    return best * (0.9, 0.78, 0.55, 0.32)[k]


def drop(x, y, k):
    """A falling bead, point up, its rim brighter than its middle."""
    s = (1.0, 0.92, 0.8, 0.62)[k]
    px, py = x / s, y / s
    if py >= 0.2:
        d = math.hypot(px, py - 0.25) / 0.52
    else:
        w = 0.52 * max(0.0, (py + 0.86) / 1.08)
        if w <= 0:
            return 0.0
        d = abs(px) / w
    return 0.0 if d >= 1 else (1.0 if d > 0.6 else 0.72)


def sliver(x, y, k):
    """A long thin shard, its widest a third of the way down."""
    ang = (0.0, 0.0, 0.35, 0.8)[k]
    c, s = math.cos(ang), math.sin(ang)
    u, v = x * c + y * s, -x * s + y * c
    length = (0.98, 0.9, 0.66, 0.42)[k]
    if abs(v) >= length:
        return 0.0
    widest = -0.3 * length
    t = (v - widest) / (length - widest) if v > widest else (widest - v) / (length + widest)
    w = 0.2 * (1 - t) + 0.015
    return 0.0 if abs(u) >= w else (1.0 if abs(u) < w * 0.55 else 0.7)


SHAPES = {"spark": spark, "ember": ember, "flake": flake, "wisp": wisp, "puff": puff, "drop": drop, "sliver": sliver}


def render(fn, k):
    c = Canvas(SIZE, SIZE)
    for py in range(SIZE):
        for px in range(SIZE):
            total = 0.0
            for j in range(SS):
                for i in range(SS):
                    x = ((px + (i + 0.5) / SS) / SIZE) * 2 - 1
                    y = ((py + (j + 0.5) / SS) / SIZE) * 2 - 1
                    total += fn(x, y, k)
            a = int(round(255 * min(1.0, total / (SS * SS))))
            if a > 2:
                c.set(px, py, (255, 255, 255, a))
    return c


def build(out):
    for name, fn in SHAPES.items():
        for k in range(FRAMES):
            out.particle(f"shot_{name}_{k}", render(fn, k))
        path = os.path.join(RES, "assets/awaken/particles", f"shot_{name}.json")
        with open(path, "w") as f:
            json.dump({"textures": [f"awaken:shot_{name}_{k}" for k in range(FRAMES)]}, f, indent=2)
            f.write("\n")
