"""The Awakened Body's dust (assets/awaken/textures/particle/body_dust_0..3): one clod of grit going from a dense
puff to a thin, grainy haze over its life. Pale and nearly colourless on purpose: the game tints every puff with the
colour of the ground it came off. Sounds are tools/art/specs/audio_body.py.
"""
import math
import random

from pixelart import Canvas


def dust(frame):
    rnd = random.Random(4242 + frame * 17)
    c = Canvas()
    cx, cy = 7.5, 8.5 - frame * 0.4
    radius = 3.6 + frame * 1.15
    # a few lumps make the outline irregular; they drift apart as the puff thins
    lumps = [(cx + rnd.uniform(-1.6, 1.6) * (1 + frame * 0.35), cy + rnd.uniform(-1.4, 1.2) * (1 + frame * 0.3),
              radius * rnd.uniform(0.55, 0.8)) for _ in range(4)]
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, (y - cy) * 1.15) / radius
            dens = max(0.0, 1.0 - d) ** 1.4
            for lx, ly, lr in lumps:
                dl = math.hypot(x - lx, y - ly) / lr
                dens = max(dens, 0.8 * max(0.0, 1.0 - dl) ** 1.6)
            grain = rnd.random()
            # older puffs break into grains: holes open up
            if grain < 0.08 + frame * 0.11:
                dens *= 0.35
            dens *= 0.88 + 0.24 * grain
            a = min(1.0, dens * (1.05 - frame * 0.17))
            if a < 0.04:
                continue
            shade = int(200 + 45 * (1 - d) - 18 * grain)
            shade = max(150, min(250, shade))
            c.set(x, y, (shade, shade - 4, shade - 10, int(255 * a)))
    return c


def build(out):
    for i in range(4):
        out.particle(f"body_dust_{i}", dust(i))
