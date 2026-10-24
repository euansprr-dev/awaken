"""The Awakened Body's dust (assets/awaken/textures/particle/body_dust_0..3): one clod of grit, drawn as pixel art
with hard edges like the game's own sprites, going from a tight clump to loose specks over its life. Pale and nearly
colourless on purpose: the game tints every clod with the colour of the ground it came off. Sounds are
tools/art/specs/audio_body.py.
"""
import math
import random

from pixelart import Canvas

LIGHT, MID, DARK = 236, 206, 172


def dust(frame):
    rnd = random.Random(7310 + frame * 31)
    c = Canvas()
    cx, cy = 7.5, 8.0 - frame * 0.3
    spread = 1.0 + frame * 0.45
    # a clump of overlapping lumps: hard-edged, lit from the top left
    lumps = [(cx + rnd.uniform(-1.5, 1.5) * spread, cy + rnd.uniform(-1.0, 1.0) * spread, rnd.uniform(1.4, 2.3) * (1 - frame * 0.12))
             for _ in range(3 + frame)]
    hole = (0.0, 0.12, 0.3, 0.55)[frame]
    alpha = (235, 215, 185, 150)[frame]
    for y in range(16):
        for x in range(16):
            inside = None
            for lx, ly, lr in lumps:
                d = math.hypot(x + 0.5 - lx, y + 0.5 - ly) / lr
                if d <= 1.0 and (inside is None or d < inside[0]):
                    inside = (d, lx, ly)
            if inside is None or rnd.random() < hole:
                continue
            d, lx, ly = inside
            light = (lx - (x + 0.5)) + (ly - (y + 0.5))  # > 0: toward the top left of its lump
            shade = LIGHT if light > 0.8 else DARK if light < -0.9 or d > 0.85 else MID
            c.set(x, y, (shade, shade - 3, shade - 8, alpha if d < 0.8 else alpha - 40))
    # loose grains thrown off the clump
    for _ in range(2 + frame * 2):
        a = rnd.uniform(0, math.tau)
        r = rnd.uniform(3.0, 4.0 + frame * 1.2)
        x, y = int(cx + math.cos(a) * r), int(cy + math.sin(a) * r * 0.8)
        if 0 <= x < 16 and 0 <= y < 16:
            c.set(x, y, (MID, MID - 3, MID - 8, alpha - 30))
    return c


def build(out):
    for i in range(4):
        out.particle(f"body_dust_{i}", dust(i))
