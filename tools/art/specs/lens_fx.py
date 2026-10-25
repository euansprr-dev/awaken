"""Lens 2's ambient particles (SPEC §22): dust, embers, frost, spores, wisps and breath. Smooth white light sprites
(not pixel art, not vanilla's), tinted per place in dev.awaken.lens.client.LensParticles; deterministic.

    python3 tools/art/build_all.py lens_fx          textures/particle/lens_<kind>_<n>.png, particles/lens_<kind>.json
    python3 tools/art/specs/lens_fx.py preview DIR   every sprite on black and on a daylight sky, enlarged

- mote    a soft speck; a speck with a faint four-point glint (dust catching the sun)
- ember   a hot core in a soft halo; the same drawn out a little (a spark in flight)
- frost   a six-armed crystal; a small hexagonal plate; a soft speck of rime
- spore   a fuzzy ball in a halo; a ball with a faint ring
- wisp    a soft orb with a bright core; an orb trailing a short tail
- breath  three soft puffs, each a lumpy cloud fading at its edge
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
TEX = "assets/awaken/textures"
N = 16


def _grid(n=N):
    c = (np.arange(n) + 0.5) / n * 2 - 1
    return np.meshgrid(c, c)


def _img(a, rgb=None):
    a = np.clip(a, 0, 1)
    out = np.zeros(a.shape + (4,), dtype=np.uint8)
    out[..., :3] = 255 if rgb is None else np.clip(rgb * 255, 0, 255).astype(np.uint8)
    out[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def mote(k):
    X, Y = _grid()
    r = np.hypot(X, Y)
    a = np.exp(-(r / 0.3) ** 2)
    if k == 1:
        a = a + 0.55 * (np.exp(-(np.abs(X) / 0.07) ** 2) * np.exp(-(np.abs(Y) / 0.75) ** 2)
                        + np.exp(-(np.abs(Y) / 0.07) ** 2) * np.exp(-(np.abs(X) / 0.75) ** 2))
    return _img(a)


def ember(k):
    X, Y = _grid()
    if k == 1:
        Y = Y * 0.62
    r = np.hypot(X, Y)
    core = np.exp(-(r / 0.22) ** 2)
    a = core + 0.45 * np.exp(-(r / 0.62) ** 2)
    # a whiter core, the halo keeps the tint
    rgb = np.ones(r.shape + (3,)) * (0.82 + 0.18 * core)[..., None]
    return _img(a, rgb)


def frost(k):
    X, Y = _grid()
    r = np.hypot(X, Y)
    ang = np.arctan2(Y, X)
    if k == 0:
        # six arms, thin, with a small side barb on each
        arm = np.cos(3 * ang) ** 2
        a = np.exp(-((1 - np.abs(np.cos(3 * ang))) * r / 0.06) ** 2) * (r < 0.92) * (1 - r * 0.6)
        a = np.maximum(a, np.exp(-(r / 0.16) ** 2))
        a = np.maximum(a, 0.6 * np.exp(-((r - 0.55) / 0.06) ** 2) * arm ** 6)
    elif k == 1:
        hexd = np.maximum.reduce([np.abs(X * math.cos(t) + Y * math.sin(t)) for t in (0, math.pi / 3, 2 * math.pi / 3)])
        a = np.clip((0.62 - hexd) / 0.12, 0, 1) * 0.75 + np.exp(-(r / 0.2) ** 2) * 0.4
    else:
        a = np.exp(-(r / 0.35) ** 2) * 0.9
    return _img(a)


def spore(k):
    X, Y = _grid()
    r = np.hypot(X, Y)
    rng = np.random.default_rng(11 + k)
    fuzz = 1 + 0.18 * rng.standard_normal(r.shape)
    a = np.exp(-(r * fuzz / 0.34) ** 2) + 0.3 * np.exp(-(r / 0.7) ** 2)
    if k == 1:
        a = a + 0.45 * np.exp(-((r - 0.62) / 0.08) ** 2)
    return _img(a)


def wisp(k):
    X, Y = _grid()
    if k == 1:
        Yt = Y + 0.25
        r = np.hypot(X, Yt)
        tail = np.exp(-(X / 0.18) ** 2) * np.clip((Y + 0.1) / 0.9, 0, 1) * np.exp(-((Y - 0.4) / 0.5) ** 2) * 0.5
        a = np.exp(-(r / 0.42) ** 2) * 0.75 + np.exp(-(r / 0.14) ** 2) * 0.5 + tail
    else:
        r = np.hypot(X, Y)
        a = np.exp(-(r / 0.5) ** 2) * 0.7 + np.exp(-(r / 0.15) ** 2) * 0.6
    return _img(a)


def breath(k):
    X, Y = _grid(32)
    rng = random.Random(40 + k)
    a = np.zeros(X.shape)
    for _ in range(6):
        cx, cy, s = rng.uniform(-0.3, 0.3), rng.uniform(-0.3, 0.3), rng.uniform(0.25, 0.45)
        a = np.maximum(a, np.exp(-(((X - cx) ** 2 + (Y - cy) ** 2) / (s * s))))
    a = a * 0.8 * np.clip(1.1 - np.hypot(X, Y), 0, 1)
    return _img(a)


KINDS = {"mote": (mote, 2), "ember": (ember, 2), "frost": (frost, 3), "spore": (spore, 2), "wisp": (wisp, 2), "breath": (breath, 3)}


def write(root):
    written = []
    for kind, (fn, frames) in KINDS.items():
        for k in range(frames):
            rel = f"particle/lens_{kind}_{k}"
            path = os.path.join(root, TEX, rel + ".png")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            fn(k).save(path)
            written.append(rel)
        pj = os.path.join(root, "assets/awaken/particles", f"lens_{kind}.json")
        os.makedirs(os.path.dirname(pj), exist_ok=True)
        with open(pj, "w") as f:
            f.write('{\n  "textures": [\n' + ",\n".join(f'    "awaken:lens_{kind}_{k}"' for k in range(frames)) + "\n  ]\n}\n")
    return written


def build(out):
    out.written.extend(write(out.root))


def preview(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    tiles = []
    for kind, (fn, frames) in KINDS.items():
        for k in range(frames):
            im = fn(k).resize((128, 128), Image.BILINEAR)
            for bg in ((8, 10, 16), (150, 190, 230)):
                base = Image.new("RGBA", im.size, bg + (255,))
                tiles.append(Image.alpha_composite(base, im))
    cols = 6
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * 132, rows * 132), (30, 30, 30, 255))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * 132, (i // cols) * 132))
    sheet.save(os.path.join(out_dir, "lens_fx.png"))


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        preview(sys.argv[2])
    else:
        print("\n".join(write(os.path.join(ROOT, "src/main/resources"))))
