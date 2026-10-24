"""The Wilds' ward particles (assets/awaken/textures/particle/ward_grain_0..3 and ward_spark_0..3). Sounds are
tools/art/specs/audio_wilds.py.

- grain: a chip of a block coming apart under a Precursor ward. Pale and nearly colourless: the game tints each grain
  with its block's own colour. It starts as a faceted chip with a lit top edge and wears down to a few loose specks.
- spark: a thread of the Signal's light lifting off the block. White at the core so the game's tint (the System's edge
  blue) carries it; a short vertical streak with a soft halo that shortens and breaks up as it goes out.
"""
import math
import random

from pixelart import Canvas


def grain(frame):
    rnd = random.Random(9101 + frame * 17)
    c = Canvas()
    cx, cy = 7.5, 7.5 + frame * 0.4
    r = 3.4 - frame * 0.55
    # a faceted chip: a rough polygon, lit from the top left, darker on its lower facets
    sides = 6
    pts = []
    for i in range(sides):
        a = i * math.tau / sides + rnd.uniform(-0.35, 0.35)
        rr = r * rnd.uniform(0.75, 1.15)
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr * 0.85))

    def inside(px, py):
        n, hit = len(pts), False
        for i in range(n):
            x0, y0 = pts[i]
            x1, y1 = pts[(i + 1) % n]
            if (y0 > py) != (y1 > py) and px < (x1 - x0) * (py - y0) / (y1 - y0 + 1e-9) + x0:
                hit = not hit
        return hit

    hole = (0.0, 0.1, 0.28, 0.5)[frame]
    alpha = (245, 230, 205, 170)[frame]
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            if not inside(px, py) or rnd.random() < hole:
                continue
            light = (cx - px) * 0.7 + (cy - py)
            shade = 238 if light > 1.0 else 176 if light < -1.0 else 210
            c.set(x, y, (shade, shade - 2, shade - 6, alpha))
    # specks shaken loose as it wears
    for _ in range(frame * 3):
        a = rnd.uniform(0, math.tau)
        d = rnd.uniform(r + 0.8, r + 2.6 + frame)
        x, y = int(cx + math.cos(a) * d), int(cy + math.sin(a) * d)
        if 0 <= x < 16 and 0 <= y < 16:
            c.set(x, y, (205, 203, 198, alpha - 25))
    return c


def spark(frame):
    rnd = random.Random(4243 + frame * 29)
    c = Canvas()
    length = (11, 9, 6, 4)[frame]
    top = 8 - length // 2
    gap = (None, None, 0.5, 0.35)[frame]
    for i in range(length):
        y = top + i
        t = i / max(1, length - 1)
        core = 1.0 - abs(t - 0.4) * 1.2  # brightest a little above the middle
        if gap is not None and rnd.random() > gap + core * 0.6:
            continue
        a = int(255 * max(0.25, min(1.0, core + 0.2)))
        c.set(7, y, (255, 255, 255, a))
        c.set(8, y, (255, 255, 255, int(a * 0.75)))
        # a soft halo either side
        for dx, k in ((-1, 0.32), (2, 0.24), (-2, 0.12), (3, 0.08)):
            x = 7 + dx
            if 0 <= x < 16:
                c.set(x, y, (235, 245, 255, int(a * k)))
    # the head: one bright pixel above, a cross of light on the first frames
    if frame < 2:
        c.set(7, top - 1, (255, 255, 255, 200))
        c.set(6, top, (240, 248, 255, 90))
        c.set(9, top, (240, 248, 255, 90))
    return c


def build(out):
    for i in range(4):
        out.particle(f"ward_grain_{i}", grain(i))
        out.particle(f"ward_spark_{i}", spark(i))
