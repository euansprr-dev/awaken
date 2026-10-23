"""Anomalies (world/anomaly): the rift tear and its core, the procession's light, the spirit cache."""
import math

from pixelart import *


def rift_tear(seed="rift_tear", w=32, h=64):
    """A jagged vertical crack: a white-hot seam, signal-blue body, spirit-violet edge and a soft glow that fades out.
    Drawn on a transparent 32x64 canvas; the renderer stretches it about 1.6 x 3.6 blocks."""
    sig, spi = ramp("signal"), ramp("spirit")
    rng = rng_for("rift", seed)
    # the seam wanders left and right in short straight runs, like a crack in glass
    xs, x, y = [], w / 2, 0
    while y < h:
        run = rng.randrange(3, 8)
        dx = rng.uniform(-2.2, 2.2)
        for _ in range(run):
            if y >= h:
                break
            xs.append(x)
            x = max(9.0, min(w - 9.0, x + dx / run))
            y += 1
    # two short side cracks
    branches = []
    for _ in range(2):
        by = rng.randrange(h // 4, 3 * h // 4)
        branches.append((by, rng.choice((-1, 1)), rng.randrange(4, 8)))
    c = Canvas(w, h)
    for y in range(h):
        t = y / (h - 1)
        width = 0.6 + 4.2 * math.sin(math.pi * t) ** 0.7  # thin at both tips, widest in the middle
        for x in range(w):
            d = abs(x + 0.5 - xs[y])
            for by, side, ln in branches:
                k = y - by
                if 0 <= k < ln:
                    d = min(d, abs(x + 0.5 - (xs[by] + side * k * 0.9)) + k * 0.25)
            if d < width * 0.3:
                c.set(x, y, (255, 255, 255, 255))
            elif d < width * 0.6:
                c.set(x, y, sig[5])
            elif d < width:
                r, g, b, _ = sig[4]
                c.set(x, y, (r, g, b, 235))
            elif d < width + 1.5:
                r, g, b, _ = spi[4]
                c.set(x, y, (r, g, b, 190))
            elif d < width + 6:
                r, g, b, _ = spi[3]
                a = int(150 * (1 - (d - width - 1.5) / 4.5) ** 1.6 * (0.4 + 0.6 * math.sin(math.pi * t)))
                if a > 4:
                    c.set(x, y, (r, g, b, a))
    return c


def rift_core(seed="rift_core"):
    """The core at the tear's foot: a bright eye of light in a violet shell, with dark fracture lines."""
    sig, spi = ramp("signal"), ramp("spirit")
    rng = rng_for("core", seed)
    c = Canvas(16, 16)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d < 2.0:
                c.set(x, y, (255, 255, 255, 255))
            elif d < 3.4:
                c.set(x, y, sig[5])
            elif d < 5.0:
                c.set(x, y, sig[4] if (x + y) % 5 else spi[5])
            elif d < 6.4:
                c.set(x, y, spi[4])
            elif d < 7.8:
                r, g, b, _ = spi[3]
                c.set(x, y, (r, g, b, int(220 * (7.8 - d) / 1.4)))
    for _ in range(4):
        a = rng.uniform(0, math.tau)
        for k in range(4, 8):
            c.set(int(round(cx + math.cos(a) * k)), int(round(cy + math.sin(a) * k)), spi[1])
    return c


def cache_side():
    """Slate in a brass frame with a small four-pointed amber star (the lights' mark) low on the face."""
    c = stone("slate", seed="spirit_cache", scale=3, contrast=0.8, lo=1, hi=4)
    frame_border(c, "brass")
    am = ramp("amber")
    cx, cy = 7, 11
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - cx), abs(y - cy)
            if dx + dy <= 1:
                c.set(x, y, am[5])
            elif (dx == 0 and dy <= 3) or (dy == 0 and dx <= 3):
                c.set(x, y, am[4])
            elif dx + dy <= 3:
                c.blend(x, y, am[2], 0.4)
    return c


def cache_top():
    c = stone("slate", seed="spirit_cache_top", scale=3, contrast=0.7, lo=1, hi=4)
    frame_border(c, "brass")
    am = ramp("amber")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 1.6:
                c.set(x, y, am[5])
            elif d < 2.8:
                c.set(x, y, am[4])
            elif d < 3.8:
                c.blend(x, y, am[3], 0.6)
    return c


def build(out):
    out.entity("rift_tear", rift_tear())
    out.entity("rift_core", rift_core())
    out.entity("anomaly_mote", particle_dot("amber", 16))
    out.block("anomaly_spirit_cache", cache_side())
    out.block("anomaly_spirit_cache_top", cache_top())
