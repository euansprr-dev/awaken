"""Lucidium textures: dream blocks, the Dream Bell, the journal, residue, the rising raindrop and the Shaken icon.

The dream ramp (lilac and rose) carries the module; signal blue marks lucidity, amber the bell's ring.
"""
import math

from pixelart import *
from pixelart import _outline


def cloud(seed):
    c = stone("cloud", seed=seed, scale=2, contrast=0.55, lo=2)
    rp = ramp("cloud")
    rd = ramp("dream")
    rng = rng_for("cloud-tint", seed)
    for y in range(16):
        for x in range(16):
            if rng.random() < 0.05:
                c.blend(x, y, rd[5], 0.35)
    # a soft underside shadow on the lower rows
    for x in range(16):
        c.blend(x, 15, rp[0], 0.3)
        c.blend(x, 14, rp[1], 0.15)
    return c


def dream_glass():
    rd = ramp("dream")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge:
                r, g, b, _ = rd[4]
                c.set(x, y, (r, g, b, 230))
            else:
                r, g, b, _ = rd[5]
                c.set(x, y, (r, g, b, 70))
    # inner streaks of light, like glass catching a sunset that isn't there
    for i in range(2, 6):
        r, g, b, _ = ramp("cloud")[5]
        c.set(i, i - 1, (r, g, b, 170))
        c.set(i + 8, i + 6, (r, g, b, 120))
    for (x, y) in ((11, 3), (4, 11), (12, 12)):
        r, g, b, _ = ramp("signal")[5]
        c.set(x, y, (r, g, b, 200))
    return c


def shifting_frames(n=10):
    """Stone whose colours drift through the dream ramp and into signal blue and back."""
    frames = []
    rd, rs = ramp("dream"), ramp("spirit")
    rng = rng_for("shifting")
    base = value_noise(16, 16, rng, scale=4, octaves=2)
    cracks = [[rng.random() < 0.08 for _ in range(16)] for _ in range(16)]
    for f in range(n):
        t = f / n * math.tau
        c = Canvas()
        for y in range(16):
            for x in range(16):
                v = base[y][x] * 0.7 + 0.3 * (0.5 + 0.5 * math.sin(t + x * 0.45 + y * 0.3))
                rp = rd if (0.5 + 0.5 * math.sin(t * 0.5 + base[y][x] * 3)) > 0.45 else rs
                col = rp[1 + quantize(v, len(rp) - 2)]
                if cracks[y][x]:
                    col = rp[-1]
                c.set(x, y, col)
        add_bevel(c, rd)
        frames.append(c)
    return frames


def bell(lit):
    rb = ramp("brass")
    ra = ramp("amber")
    rd = ramp("dream")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            light = ((x - 8) * -0.5 + (y - 8) * -0.4) / 10
            v = 0.55 + light
            col = rb[1 + quantize(max(0, min(0.999, v)), len(rb) - 1)]
            c.set(x, y, col)
    # a band of dream glass round the waist, and a rune
    for x in range(16):
        c.set(x, 9, rd[3])
        c.set(x, 10, rd[2])
    glyph(c, "amber" if lit else "dream", seed="dream_bell", cx=8, cy=5, size=3)
    if lit:
        for y in range(16):
            for x in range(16):
                if (x + y) % 5 == 0:
                    c.blend(x, y, ra[5], 0.35)
        for x in range(16):
            c.set(x, 9, ramp("signal")[4])
    return c


def bell_base():
    c = stone("slate", seed="bell_base", scale=2, contrast=0.6, lo=1, hi=4)
    frame_border(c, "brass")
    return c


def residue():
    """A little drift of glittering dust."""
    rd, rc = ramp("dream"), ramp("cloud")
    c = Canvas()
    rng = rng_for("residue")
    for y in range(6, 15):
        for x in range(2, 14):
            d = math.hypot((x - 7.5) / 6, (y - 12) / 3.8)
            if d <= 1:
                c.set(x, y, rd[2 + quantize(1 - d, 3)])
    for _ in range(14):
        x, y = rng.randrange(3, 13), rng.randrange(4, 13)
        if c.get(x, y)[3] or rng.random() < 0.4:
            c.set(x, y, rc[5] if rng.random() < 0.5 else rd[5])
    for (x, y) in ((5, 3), (10, 2), (12, 5)):
        c.set(x, y, ramp("signal")[5])
    return outline_soft(c)


def outline_soft(c):
    """Like the toolkit outline, but in dark plum so the dust doesn't look heavy."""
    col = ramp("dream")[0]
    mask = [[c.get(x, y)[3] > 0 for x in range(c.w)] for y in range(c.h)]
    for y in range(c.h):
        for x in range(c.w):
            if not mask[y][x] and any(0 <= x + dx < c.w and 0 <= y + dy < c.h and mask[y + dy][x + dx]
                                      for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                c.set(x, y, col)
    return c


def raindrop():
    rd, rs = ramp("dream"), ramp("signal")
    c = Canvas(8, 8)
    for y in range(8):
        for x in range(8):
            d = math.hypot((x - 3.5) / 1.6, (y - 4.5) / 3.4)
            if d < 1:
                a = int(255 * (1 - d) ** 0.8)
                r, g, b, _ = (rs[5] if y < 3 else rd[5])
                c.set(x, y, (r, g, b, a))
    return c


def shaken_icon():
    """A wobbling spiral: the world still swaying after a fall that never happened."""
    rd = ramp("dream")
    c = Canvas(18, 18)
    for i in range(80):
        t = i / 80 * math.tau * 2.2
        r = 1.2 + t * 0.95
        x = 9 + math.cos(t) * r * 0.9
        y = 9 + math.sin(t) * r * 0.75
        c.set(int(x), int(y), rd[2 + min(3, int(i / 22))])
    _outline(c, OUTLINE)
    return c


def build(out):
    out.block("dream_cloud", cloud("dream_cloud"))
    out.block("dream_glass", dream_glass())
    out.block("shifting_stone", shifting_frames(), frametime=4)
    out.block("dream_bell", bell(False))
    out.block("dream_bell_lit", bell(True))
    out.block("dream_bell_base", bell_base())
    out.item("dream_journal", book(cover="dream", accent="signal"))
    out.item("dream_residue", residue())
    out.particle("dream_drop", raindrop())
    out._save("mob_effect/shaken", shaken_icon())
