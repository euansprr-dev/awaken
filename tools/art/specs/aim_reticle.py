"""The lock-on's look (SPEC §40.4): the band's own language, thin cyan lines, corner ticks and the System's diamond, drawn as
white shapes the game tints (the mark's cyan, a tell's white, amber, red or violet) and scales round the creature.

    python3 tools/art/specs/aim_reticle.py            write them into src/main/resources/assets/awaken/textures/gui/aim/
    python3 tools/art/specs/aim_reticle.py preview D  the same, plus a contact sheet in D (on dark and light ground)

Each shape is two files: <name>.png, the crisp line (signed distances, anti-aliased at 1x, never resampled), and
<name>_glow.png, its soft light (a blur of the line, wider). The game draws the glow twice, black under for contrast on a
bright sky, then tinted, and the line on top a little whiter than its tint: a hot core with light round it, as the
holo plates have.

    ring      128  the lock: a thin ring broken at the four diagonals, a ring of fine dots inside it
    ticks     128  four tapered ticks pointing in, at the cardinal points (they fly in when the lock takes)
    bracket    64  one corner of the mark, drawn four times turned
    diamond    32  the System's diamond: an outline round a small solid core
    tell      128  a plain ring that closes in on the lock ring as a creature's blow comes
    edge      128x32  an arc at the screen's edge for a blow wound up out of view
    dot        16  the dot in the middle when you can mine, place or use something
    shard      16  a splinter of the ring when the locked creature dies

Nothing is random: the same pixels every time.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "../../../src/main/resources/assets/awaken/textures/gui/aim"))


def grid(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(np.float64)
    return x + 0.5, y + 0.5


def cover(d):
    """Signed distance (negative inside) to coverage, anti-aliased over one pixel."""
    return np.clip(0.5 - d, 0.0, 1.0)


def ring_d(x, y, cx, cy, r, half):
    return np.abs(np.hypot(x - cx, y - cy) - r) - half


def seg_d(x, y, ax, ay, bx, by, half):
    px, py, vx, vy = x - ax, y - ay, bx - ax, by - ay
    t = np.clip((px * vx + py * vy) / (vx * vx + vy * vy), 0.0, 1.0)
    return np.hypot(px - vx * t, py - vy * t) - half


def ring(s=128):
    x, y = grid(s, s)
    c = s / 2
    a = cover(ring_d(x, y, c, c, 52.0, 1.25))
    # broken at the four diagonals: 20 degrees out of each, soft at the cut
    ang = np.degrees(np.arctan2(y - c, x - c)) % 90.0
    gap = np.abs(ang - 45.0)
    a *= np.clip((gap - 10.0) / 1.5, 0.0, 1.0)
    # fine dots inside, every 15 degrees, skipping the cardinal points (the ticks land there)
    for k in range(24):
        if k % 6 == 0:
            continue
        t = math.radians(k * 15.0)
        a = np.maximum(a, cover(np.hypot(x - (c + 44.0 * math.cos(t)), y - (c + 44.0 * math.sin(t))) - 0.85) * 0.75)
    return a


def ticks(s=128):
    x, y = grid(s, s)
    c = s / 2
    a = np.zeros_like(x)
    for k in range(4):
        t = math.radians(k * 90.0)
        ux, uy = math.cos(t), math.sin(t)
        # along the spoke (u) and across it (v): a wedge, 4 px wide at r=61, a point at r=46
        u = (x - c) * ux + (y - c) * uy
        v = -(x - c) * uy + (y - c) * ux
        f = np.clip((u - 46.0) / 15.0, 0.0, 1.0)
        half = 0.25 + 1.85 * f
        d = np.maximum(np.abs(v) - half, np.maximum(46.0 - u, u - 61.0))
        a = np.maximum(a, cover(d))
    return a


def bracket(s=64):
    x, y = grid(s, s)
    o, n = 7.0, 30.0
    d = np.minimum(seg_d(x, y, o, o, o + n, o, 1.4), seg_d(x, y, o, o, o, o + n, 1.4))
    a = cover(d)
    # a short inner tick off the corner, like the band's panel corners
    a = np.maximum(a, cover(seg_d(x, y, o + 5.0, o + 5.0, o + 10.0, o + 10.0, 0.9)) * 0.85)
    return a


def diamond(s=32):
    x, y = grid(s, s)
    c = s / 2
    m = np.abs(x - c) + np.abs(y - c)
    outline = cover((np.abs(m - 11.0) - 1.15) / math.sqrt(2) * 1.414)
    core = cover((m - 4.2) / math.sqrt(2) * 1.414)
    return np.maximum(outline, core)


def tell(s=128):
    x, y = grid(s, s)
    c = s / 2
    return cover(ring_d(x, y, c, c, 56.0, 1.1))


def edge(w=128, h=32):
    x, y = grid(w, h)
    big = 300.0
    cx, cy = w / 2, big + 10.0
    d = ring_d(x, y, cx, cy, big, 1.6)
    a = cover(d)
    # tapered toward both ends
    t = np.clip(1.0 - np.abs(x - cx) / (w / 2 - 6.0), 0.0, 1.0)
    return a * np.sin(t * math.pi / 2) ** 0.7


def dot(s=16):
    x, y = grid(s, s)
    return cover(np.hypot(x - s / 2, y - s / 2) - 1.9)


def shard(s=16):
    x, y = grid(s, s)
    c = s / 2
    m = np.abs(x - c) / 1.6 + np.abs(y - c) / 6.5
    return cover((m - 1.0) * 1.6)


SHAPES = [("ring", ring, 4.0), ("ticks", ticks, 3.0), ("bracket", bracket, 2.5), ("diamond", diamond, 2.0), ("tell", tell, 4.0),
          ("edge", edge, 3.5), ("dot", dot, 1.6), ("shard", shard, 1.5)]


def white(alpha):
    h, w = alpha.shape
    img = np.zeros((h, w, 4), np.uint8)
    img[..., :3] = 255
    img[..., 3] = np.clip(np.round(alpha * 255), 0, 255).astype(np.uint8)
    return Image.fromarray(img, "RGBA")


def glow(alpha, radius):
    a = Image.fromarray(np.clip(np.round(alpha * 255), 0, 255).astype(np.uint8), "L")
    b = np.asarray(a.filter(ImageFilter.GaussianBlur(radius)), np.float64) / 255.0
    b = np.clip(b * 2.2, 0.0, 1.0) ** 0.9  # the blur thins a line's light: give it back
    return b


def write():
    os.makedirs(OUT, exist_ok=True)
    made = []
    for name, fn, rad in SHAPES:
        a = fn()
        for suffix, data in (("", a), ("_glow", glow(a, rad))):
            path = os.path.join(OUT, name + suffix + ".png")
            white(data).save(path)
            with open(path + ".mcmeta", "w") as f:
                json.dump({"texture": {"blur": True, "clamp": True}}, f, indent=2)
                f.write("\n")
            made.append(path)
    return made


def preview(d):
    os.makedirs(d, exist_ok=True)
    tiles = []
    for name, fn, rad in SHAPES:
        a = fn()
        g = glow(a, rad)
        h, w = a.shape
        sc = max(1, 128 // max(h, w)) * 2
        for ground in ((14, 20, 30), (196, 212, 226)):
            col = np.array([127, 227, 255], np.float64)
            base = np.ones((h, w, 3)) * np.array(ground, np.float64)
            base = base * (1 - g[..., None] * 0.35)  # the dark halo
            base = base * (1 - g[..., None] * 0.55) + col * g[..., None] * 0.55
            core = col * 0.4 + 255 * 0.6
            base = base * (1 - a[..., None]) + core * a[..., None]
            im = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8), "RGB").resize((w * sc, h * sc), Image.NEAREST)
            tiles.append(im)
    W = sum(t.width for t in tiles[:2]) + 8
    H = sum(max(t.height for t in tiles[i:i + 2]) + 8 for i in range(0, len(tiles), 2))
    sheet = Image.new("RGB", (W, H), (40, 40, 40))
    yy = 0
    for i in range(0, len(tiles), 2):
        xx = 0
        for t in tiles[i:i + 2]:
            sheet.paste(t, (xx, yy))
            xx += t.width + 8
        yy += max(t.height for t in tiles[i:i + 2]) + 8
    out = os.path.join(d, "aim_reticle_sheet.png")
    sheet.save(out)
    return out


if __name__ == "__main__":
    for p in write():
        print(os.path.relpath(p, os.getcwd()))
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        print(preview(sys.argv[2]))
