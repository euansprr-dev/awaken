"""Arms' HUD textures (SPEC §25 "Feel"): the low-health edge, a soft elliptical falloff that is clear in the middle
and gathers in the corners, white so the HUD tints it (a cold dark blue). 256 px: the GUI draws it stretched with
nearest filtering, so the falloff is fine enough that no band shows.

    python3 tools/art/specs/arms_hud.py
"""
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "../../../src/main/resources/assets/awaken/textures/gui/arms/low_health.png")


def edge(size=256):
    y, x = np.mgrid[0:size, 0:size].astype(np.float64)
    u, v = (x + 0.5) / size * 2 - 1, (y + 0.5) / size * 2 - 1
    r = np.sqrt((u * 0.92) ** 2 + (v * 1.0) ** 2)
    t = np.clip((r - 0.5) / 0.62, 0, 1)
    a = t * t * (3 - 2 * t)  # smoothstep: no line where it starts
    a = a ** 1.35
    rgba = np.zeros((size, size, 4), np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.round(a * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


if __name__ == "__main__":
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    edge().save(OUT)
    print("wrote", os.path.relpath(OUT))
