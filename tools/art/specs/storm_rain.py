"""The Rolling Storm's rain curtain (SPEC §21.6, the no-Lens fallback): a soft veil of falling rain for the sheets that
hang from the storm cell's base. White (the curtain's vertex colour tints it), its alpha a thin even veil under fine
vertical streaks of uneven length and weight. Tiles both ways (the sheets repeat it and scroll it down as the rain falls).

    python3 tools/art/build_all.py storm_rain      textures/living/storm_rain.png
"""
import os
import sys

import numpy as np
from PIL import Image

W, H = 64, 256


def periodic_noise(rng, n, cycles):
    """Smooth periodic 1-D noise, 0..1: a few random harmonics."""
    t = np.arange(n) / n
    v = np.zeros(n)
    for k in range(1, cycles + 1):
        v += rng.normal() / k * np.sin(2 * np.pi * k * t + rng.random() * 2 * np.pi)
    v -= v.min()
    return v / (v.max() + 1e-9)


def curtain():
    rng = np.random.default_rng(7)
    a = np.zeros((H, W))
    x = np.arange(W)
    for _ in range(46):
        cx = rng.random() * W
        sig = rng.uniform(0.45, 1.1)
        d = np.abs(x - cx)
        d = np.minimum(d, W - d)
        across = np.exp(-0.5 * (d / sig) ** 2)
        # each streak comes and goes along its length: dashes of falling drops
        along = np.clip((periodic_noise(rng, H, 6) - 0.35) / 0.65, 0, 1) ** 1.5
        a += np.outer(along, across) * rng.uniform(0.35, 1.0)
    a = 0.3 + 0.7 * np.clip(a / np.percentile(a, 99), 0, 1)
    out = np.ones((H, W, 4))
    out[..., 3] = a
    return Image.fromarray((out * 255).astype(np.uint8), "RGBA")


def write(root):
    path = os.path.join(root, "assets/awaken/textures/living/storm_rain.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    curtain().save(path)
    return ["living/storm_rain"]


def build(out):
    out.written.extend(write(out.root))


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        im = np.array(curtain())
        os.makedirs(sys.argv[2], exist_ok=True)
        Image.fromarray(im[..., 3]).convert("RGB").resize((W * 3, H * 3), Image.NEAREST).save(os.path.join(sys.argv[2], "storm_rain.png"))
