"""Lens 2's cloud noise (SPEC §22, L4): one tileable 256x256 RGBA texture the cloud shaders sample at several scales.
Made in the frequency domain, so every channel wraps seamlessly; deterministic.

    python3 tools/art/build_all.py lens_clouds      textures/effect/lens_cloud_noise.png

- r  coverage: broad fractal noise, cells about a tenth of the tile (where clouds stand)
- g  shape: a finer fractal noise (their outlines and towers)
- b  billows: inverted cellular noise softened by fractal noise (cauliflower tops)
- a  wisps: fine noise (eroded edges)
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
N = 256


def fractal(seed, beta, lo, hi):
    """Periodic noise with a 1/f^beta spectrum between frequencies lo and hi (cycles per tile), normalised to 0..1."""
    rng = np.random.default_rng(seed)
    f = np.fft.fftfreq(N) * N
    fx, fy = np.meshgrid(f, f)
    r = np.hypot(fx, fy)
    amp = np.where((r >= lo) & (r <= hi), 1.0 / np.maximum(r, 1.0) ** beta, 0.0)
    phase = np.exp(2j * np.pi * rng.random((N, N)))
    v = np.real(np.fft.ifft2(amp * phase))
    v = (v - v.mean()) / (v.std() + 1e-9)
    return np.clip(0.5 + v * 0.2, 0.0, 1.0)


def cellular(seed, cells):
    """Periodic distance to the nearest of cells x cells jittered points, inverted (1 at the points)."""
    rng = np.random.default_rng(seed)
    pts = (np.stack(np.meshgrid(np.arange(cells), np.arange(cells)), -1).reshape(-1, 2) + rng.random((cells * cells, 2))) / cells
    c = (np.arange(N) + 0.5) / N
    X, Y = np.meshgrid(c, c)
    d = np.full((N, N), 9.0)
    for px, py in pts:
        dx = np.abs(X - px)
        dy = np.abs(Y - py)
        dx = np.minimum(dx, 1 - dx)
        dy = np.minimum(dy, 1 - dy)
        d = np.minimum(d, np.hypot(dx, dy))
    d = d * cells
    return np.clip(1.0 - d / 0.75, 0.0, 1.0)


def noise():
    r = fractal(1, 1.6, 1, 48)
    g = fractal(2, 1.3, 4, 96)
    b = np.clip(cellular(3, 12) * 0.75 + fractal(4, 1.2, 8, 96) * 0.4, 0.0, 1.0)
    a = fractal(5, 0.9, 16, 128)
    out = np.stack([r, g, b, a], -1)
    return Image.fromarray((out * 255).astype(np.uint8), "RGBA")


def write(root):
    path = os.path.join(root, "assets/awaken/textures/effect/lens_cloud_noise.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    noise().save(path)
    return ["effect/lens_cloud_noise"]


def build(out):
    out.written.extend(write(out.root))


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        im = noise()
        os.makedirs(sys.argv[2], exist_ok=True)
        tiles = [Image.fromarray(np.array(im)[..., k]).convert("RGB") for k in range(4)]
        sheet = Image.new("RGB", (N * 4, N))
        for k, t in enumerate(tiles):
            sheet.paste(t, (k * N, 0))
        sheet.save(os.path.join(sys.argv[2], "lens_clouds.png"))
    else:
        print("\n".join(write(os.path.join(ROOT, "src/main/resources"))))
