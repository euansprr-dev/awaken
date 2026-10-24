"""The Ascent's light: the rope, glows, the shock ring, a rope's bite, the wings of light and the motes
(docs/plan-ascent.html; SPEC §17). Smooth light textures (not pixel art), deterministic.

    python3 tools/art/build_all.py ascent_fx        assets/awaken/textures/ascent/*.png, textures/particle/ascent_mote_*.png
    python3 tools/art/specs/ascent_fx.py preview DIR   every texture on black and on a daylight sky, enlarged

Layout the renderer expects (dev.awaken.body.ascent.client.AscentRender, WingLayer):
- rope.png    u along the rope (repeats), v across it; drawn translucent and full bright: a white-hot core, two
              strands of cyan light braided round it, a thin deep-blue rim so it reads against a bright sky
- glow.png    u along, v across: a soft halo, premultiplied (drawn additive)
- ring.png    u round the ring, v from inner (0) to outer (1) edge: a sharp bright shock front fading inward
- star.png    the flash where a rope bites: a four-point flare on a soft core
- wing.png    u from the shoulder (0) to the tip (1), v from the leading edge (0) to the trailing edge (1): coverts
              along the leading edge, long flight feathers trailing, scalloped tips, a rounded wingtip
- wing_glow.png  the wing's own shape blurred into a halo, premultiplied (drawn additive over the wing)
- stream.png  an updraft's rising streak: u across (0..1), v from the head (0, top) to the tail (1): a bright soft head
              and a long thinning tail of air, premultiplied (drawn additive, scrolling up the column)
"""
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
TEX = "assets/awaken/textures"

CORE = np.array([1.0, 1.0, 1.0])
CYAN = np.array([0.62, 0.88, 1.0])
DEEP = np.array([0.16, 0.36, 0.78])
GOLD = np.array([1.0, 0.86, 0.55])


def _img(rgb, a):
    """rgb: HxWx3 in 0..1, a: HxW in 0..1 -> RGBA image."""
    out = np.zeros(a.shape + (4,), dtype=np.uint8)
    out[..., :3] = np.clip(rgb * 255, 0, 255).astype(np.uint8)
    out[..., 3] = np.clip(a * 255, 0, 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def _premul(rgb, a):
    return _img(rgb * a[..., None], a)


def rope():
    w, h = 128, 16
    u = (np.arange(w) + 0.5) / w
    v = (np.arange(h) + 0.5) / h
    U, V = np.meshgrid(u, v)
    across = (V - 0.5) * 2  # -1..1
    # two strands spiralling round the core (period 1/4 of the texture, so 4 twists a repeat)
    s1 = np.cos((U * 4 + across * 0.35) * 2 * math.pi)
    s2 = np.cos((U * 4 - across * 0.35) * 2 * math.pi + math.pi)
    strand = np.maximum(s1, s2)
    body = np.clip(1 - np.abs(across) ** 2.2, 0, 1)
    core = np.exp(-(across / 0.22) ** 2)
    rim = np.exp(-((np.abs(across) - 0.82) / 0.12) ** 2)
    rgb = CYAN[None, None, :] * (0.55 + 0.45 * (strand[..., None] * 0.5 + 0.5)) * body[..., None]
    rgb = rgb * (1 - core[..., None]) + CORE[None, None, :] * core[..., None]
    rgb = rgb * (1 - rim[..., None] * 0.7) + DEEP[None, None, :] * rim[..., None] * 0.7
    a = np.clip(body * 0.9 + core * 0.1 + rim * 0.6, 0, 1) * (np.abs(across) < 0.98)
    return _img(rgb, a)


def glow():
    w, h = 64, 32
    v = (np.arange(h) + 0.5) / h
    across = (v - 0.5) * 2
    prof = np.exp(-(across / 0.42) ** 2) * 0.85 + np.exp(-(across / 0.12) ** 2) * 0.15
    a = np.tile(prof[:, None], (1, w))
    rgb = np.ones((h, w, 3))
    return _premul(rgb, a)


def stream():
    w, h = 16, 128
    u = (np.arange(w) + 0.5) / w
    v = (np.arange(h) + 0.5) / h
    U, V = np.meshgrid(u, v)
    across = (U - 0.5) * 2
    # a head near the top, a tail that thins and fades toward the bottom
    width = 0.28 + 0.5 * np.exp(-((V - 0.08) / 0.06) ** 2)
    body = np.exp(-(across / np.maximum(0.05, width * (1 - 0.7 * V))) ** 2)
    along = np.exp(-((V - 0.08) / 0.05) ** 2) * 0.6 + np.clip(1 - V, 0, 1) ** 1.6 * 0.55
    along *= np.clip(V / 0.04, 0, 1)
    a = np.clip(body * along, 0, 1)
    rgb = np.ones((h, w, 3)) * (0.82 + 0.18 * np.exp(-((V - 0.08) / 0.08) ** 2))[..., None]
    rgb[..., 0] *= 0.86
    rgb[..., 1] *= 0.95
    return _premul(rgb, a)


def ring():
    w, h = 128, 16
    rnd = np.random.default_rng(4401)
    v = (np.arange(h) + 0.5) / h
    front = np.exp(-((v - 0.88) / 0.07) ** 2) + np.clip((v - 0.2) / 0.68, 0, 1) ** 2 * 0.45
    streak = 0.75 + 0.25 * np.convolve(rnd.random(w + 8), np.ones(9) / 9, mode="valid")[:w]
    a = np.clip(front[:, None] * streak[None, :], 0, 1)
    rgb = np.ones((h, w, 3)) * 0.92 + CYAN[None, None, :] * 0.08
    return _premul(rgb, a)


def star():
    n = 64
    c = (np.arange(n) + 0.5) / n * 2 - 1
    X, Y = np.meshgrid(c, c)
    r = np.sqrt(X * X + Y * Y)
    core = np.exp(-(r / 0.16) ** 2)
    halo = np.exp(-(r / 0.5) ** 2) * 0.35
    rays = np.exp(-(np.abs(X) / 0.035) ** 2) * np.exp(-(np.abs(Y) / 0.85) ** 2) + np.exp(-(np.abs(Y) / 0.035) ** 2) * np.exp(-(np.abs(X) / 0.85) ** 2)
    diag = (np.exp(-(np.abs(X - Y) / 0.05) ** 2) + np.exp(-(np.abs(X + Y) / 0.05) ** 2)) * np.exp(-(r / 0.4) ** 2) * 0.35
    a = np.clip(core + halo + rays * 0.8 + diag, 0, 1)
    rgb = np.ones((n, n, 3)) * (1 - halo[..., None]) + CYAN[None, None, :] * halo[..., None]
    return _premul(rgb, a)


def wing_alpha(w=256, h=128, seed=7):
    """The wing's coverage and its feather structure: (alpha, shade) arrays, u across x, v down y."""
    rnd = random.Random(seed)
    u = (np.arange(w) + 0.5) / w
    v = (np.arange(h) + 0.5) / h
    U, V = np.meshgrid(u, v)
    alpha = np.zeros((h, w))
    shade = np.zeros((h, w))
    # the outline: full depth mid-wing, a rounded tip, a short root
    depth = np.clip(0.55 + 0.45 * np.sin(np.clip(u, 0, 1) * math.pi * 0.92) - np.clip((u - 0.86) / 0.14, 0, 1) ** 2 * 0.65, 0.3, 1)
    # flight feathers: long ellipses fanning from the leading edge, angled toward the tip as they go out
    count = 21
    for i in range(count):
        fu = (i + 0.5) / count
        fu += rnd.uniform(-0.01, 0.01)
        length = depth[min(w - 1, int(fu * w))] * rnd.uniform(0.9, 1.0)
        lean = 0.05 + 0.18 * fu  # outer feathers sweep toward the tip
        width = 0.062 + 0.02 * math.sin(fu * math.pi)
        # a feather from (fu, 0.18) to (fu + lean, length)
        x0, y0, x1, y1 = fu, 0.16, fu + lean * length, length
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy)
        tx, ty = dx / L, dy / L
        px, py = U - x0, V - y0
        along = (px * tx + py * ty) / L
        side = (-px * ty + py * tx)
        prof = np.clip(1 - (side / (width * (0.55 + 0.45 * np.sin(np.clip(along, 0, 1) * math.pi * 0.9 + 0.25)))) ** 2, 0, 1)
        inside = (along > 0) & (along < 1)
        tip = np.clip((1 - along) / 0.08, 0, 1)
        f = prof * inside * tip
        rachis = np.exp(-(side / 0.0045) ** 2) * inside * tip
        # each feather lies over the one before it (outer over inner), its own edge a faint darker line
        over = f > 0.02
        alpha = np.where(over, np.maximum(alpha * 0.4, np.sqrt(f) * 0.86), alpha)
        shade = np.where(over, np.clip(rachis * 0.9 + np.sqrt(f) * (0.3 + 0.4 * along), 0, 1), shade)
    # coverts: rows of rounded scales along the leading edge
    for row, (top, size, n) in enumerate(((0.0, 0.16, 22), (0.1, 0.13, 26))):
        for i in range(n):
            cu = (i + 0.5 + 0.5 * row) / n
            cv = top + size * 0.55
            ru, rv = 0.032, size * 0.62
            e = ((U - cu) / ru) ** 2 + ((V - cv) / rv) ** 2
            scale = np.clip(1 - e, 0, 1) * (V > top)
            alpha = np.maximum(alpha, np.sqrt(scale) * 0.9 * (cu < 0.97))
            shade = np.maximum(shade, scale * 0.55)
    # the leading edge: a bright bone of light
    edge = np.exp(-((V - 0.025) / 0.03) ** 2) * (U < 0.985)
    alpha = np.maximum(alpha, edge)
    shade = np.maximum(shade, edge)
    # the root fades into the back
    alpha *= np.clip(U / 0.06, 0, 1)
    return alpha, shade


def wing():
    """Light, not cloth: vanes a see-through Signal cyan, shafts and edges white-hot, a warm breath at the tips."""
    a, s = wing_alpha()
    h, w = a.shape
    V = np.tile(((np.arange(h) + 0.5) / h)[:, None], (1, w))
    vane = DEEP * 0.35 + CYAN * 0.65
    t = np.clip(s, 0, 1) ** 1.6
    rgb = vane[None, None, :] * (1 - t[..., None]) + CORE[None, None, :] * t[..., None]
    rgb = rgb * (1 - 0.14 * V[..., None]) + GOLD[None, None, :] * 0.14 * V[..., None]
    return _img(rgb, a * (0.5 + 0.5 * t))


def wing_glow():
    a, s = wing_alpha()
    im = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), "L").filter(ImageFilter.GaussianBlur(5))
    g = np.asarray(im).astype(float) / 255 * 0.85
    rgb = np.ones(g.shape + (3,)) * 0.85 + CYAN[None, None, :] * 0.15
    return _premul(rgb, g)


def mote(kind):
    n = 16
    c = (np.arange(n) + 0.5) / n * 2 - 1
    X, Y = np.meshgrid(c, c)
    r = np.sqrt(X * X + Y * Y)
    if kind == 0:
        a = np.exp(-(r / 0.45) ** 2)
    elif kind == 1:
        a = np.clip(np.exp(-(r / 0.25) ** 2) + np.exp(-(np.abs(X) / 0.09) ** 2) * np.exp(-(np.abs(Y) / 0.8) ** 2) * 0.8
                    + np.exp(-(np.abs(Y) / 0.09) ** 2) * np.exp(-(np.abs(X) / 0.8) ** 2) * 0.8, 0, 1)
    elif kind == 2:
        a = np.exp(-(r / 0.3) ** 2) * 0.6 + np.exp(-((r - 0.6) / 0.12) ** 2) * 0.7
    else:
        a = np.exp(-((X / 0.18) ** 2 + (Y / 0.7) ** 2)) * 0.9
    return _img(np.ones((n, n, 3)), np.clip(a, 0, 1))


TEXTURES = {
    "ascent/rope": rope, "ascent/glow": glow, "ascent/ring": ring, "ascent/star": star,
    "ascent/wing": wing, "ascent/wing_glow": wing_glow, "ascent/stream": stream,
}
BLUR = {"ascent/stream", "ascent/rope", "ascent/glow", "ascent/ring", "ascent/star", "ascent/wing", "ascent/wing_glow"}


def write(root):
    written = []
    for rel, fn in TEXTURES.items():
        path = os.path.join(root, TEX, rel + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        fn().save(path)
        if rel in BLUR:
            with open(path + ".mcmeta", "w") as m:
                m.write('{"texture": {"blur": true}}\n')
        written.append(rel)
    for k in range(4):
        rel = f"particle/ascent_mote_{k}"
        path = os.path.join(root, TEX, rel + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        mote(k).save(path)
        written.append(rel)
    pj = os.path.join(root, "assets/awaken/particles/ascent_mote.json")
    os.makedirs(os.path.dirname(pj), exist_ok=True)
    with open(pj, "w") as f:
        f.write('{\n  "textures": [\n' + ",\n".join(f'    "awaken:ascent_mote_{k}"' for k in range(4)) + "\n  ]\n}\n")
    return written


def build(out):
    out.written.extend(write(out.root))


def preview(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    tiles = []
    for rel, fn in TEXTURES.items():
        im = fn()
        scale = max(1, 256 // max(im.size))
        big = im.resize((im.width * scale, im.height * scale), Image.NEAREST if scale > 2 else Image.BILINEAR)
        for bg in ((8, 10, 16), (150, 190, 230)):
            base = Image.new("RGBA", big.size, bg + (255,))
            tiles.append(Image.alpha_composite(base, big))
    W = max(t.width for t in tiles)
    H = sum(t.height + 6 for t in tiles)
    sheet = Image.new("RGBA", (W, H), (30, 30, 30, 255))
    y = 0
    for t in tiles:
        sheet.paste(t, (0, y))
        y += t.height + 6
    p = os.path.join(out_dir, "ascent_fx.png")
    sheet.save(p)
    print(p)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        preview(sys.argv[2])
    else:
        print("\n".join(write(os.path.join(ROOT, "src/main/resources"))))
