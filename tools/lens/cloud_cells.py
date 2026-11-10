#!/usr/bin/env python3
"""Writes the Air's cloud cell map (SPEC §22 "Steady Light", clouds): textures/effect/lens_cloud_cells.png, 128 x 128,
one texel a twelve-block cloud cell. The cloud noise repeats every 1536 blocks, which is 128 cells, so the whole field
of cells is this one small texture, read by cell number modulo 128: a cell costs the shaders one exact read where it
was two filtered noise reads and a hash. The value (16 bits: red the high byte, green the low; times 1.25 / 65535) is
how deep in cloud the cell stands before the cover's threshold, which the shader still subtracts
(lens_clouds.glsl, cloudCell). Run from the repo root after changing lens_cloud_noise.png or the mix below."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
FX = ROOT / "src/main/resources/assets/awaken/textures/effect"
CELL, CELLS, SCALE = 12.0, 128, 1.25

noise = Image.open(FX / "lens_cloud_noise.png").convert("RGBA")
W, H = noise.size
px = noise.load()


def sample(u, v, channel):
    """The noise as the GPU read it: bilinear, clamped at the edges, at texture coordinates u, v in 0..1."""
    x, y = u * W - 0.5, v * H - 0.5
    x0, y0 = int(x // 1), int(y // 1)
    fx, fy = x - x0, y - y0

    def at(ix, iy):
        return px[min(max(ix, 0), W - 1), min(max(iy, 0), H - 1)][channel] / 255.0

    top = at(x0, y0) * (1 - fx) + at(x0 + 1, y0) * fx
    bottom = at(x0, y0 + 1) * (1 - fx) + at(x0 + 1, y0 + 1) * fx
    return top * (1 - fy) + bottom * fy


def cell_hash(cx, cz):
    """No two neighbours quite alike: a small integer hash, 0..1 (its own, and repeating with the map)."""
    h = (cx * 0x9E3779B1 ^ cz * 0x85EBCA77) & 0xFFFFFFFF
    h ^= h >> 15
    h = (h * 0x2C1B3C6D) & 0xFFFFFFFF
    h ^= h >> 12
    return (h & 0xFFFF) / 65536.0


out = Image.new("RGBA", (CELLS, CELLS))
op = out.load()
for cz in range(CELLS):
    for cx in range(CELLS):
        ux, uz = (cx + 0.5) * CELL, (cz + 0.5) * CELL
        n = sample((ux / 1536.0) % 1.0, (uz / 1536.0) % 1.0, 0) * 0.62 \
            + sample((ux / 384.0 + 0.37) % 1.0, (uz / 384.0 + 0.37) % 1.0, 1) * 0.38
        v = min(max(n + (cell_hash(cx, cz) - 0.5) * 0.045, 0.0), SCALE)
        q = round(v / SCALE * 65535)
        op[cx, cz] = (q >> 8, q & 255, 0, 255)
out.save(FX / "lens_cloud_cells.png")
print("lens_cloud_cells.png written")
