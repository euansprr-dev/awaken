#!/usr/bin/env python3
"""A grade's colour step on finished frames (SPEC §22 "Grades"): lensGrade exactly as lens_composite.glsl has it, so a
grade can be tuned against reference pictures before any tour. Each input frame becomes a pair, as it is on the left
and with the grade's colour step on the right. Only the colour step: not the grade's haze, bloom or glow, which need
the game. Keep the maths here in step with the shader's.
/opt/homebrew/bin/python3 -I tools/lens/grade_preview.py <grade.json> <out.jpg> <frame.png>..."""
import sys, json
import numpy as np
from PIL import Image, ImageDraw
g = json.load(open(sys.argv[1]))['colour']
a = [g.get(k, 0.0) for k in ('vibrance', 'dark', 'rise', 'punch')]; b = [g.get(k, 0.0) for k in ('azure', 'leaf', 'gold', 'split')]
W = np.array([0.2126, 0.7152, 0.0722], np.float32)
def luma(c): return c @ W
def smooth(lo, hi, x):
    t = np.clip((x - lo) / (hi - lo), 0, 1); return t * t * (3 - 2 * t)
def grade(c):
    r, gr, bl = c[..., 0].copy(), c[..., 1].copy(), c[..., 2].copy()
    lead = np.maximum(bl - np.maximum(r, gr), 0) * smooth(-0.08, 0.0, (gr - r) / np.maximum(bl, 1e-3)) * b[0]
    gr += lead * 0.08; r -= lead * 0.50
    leaf = np.maximum(gr - np.maximum(r, bl), 0) * b[1]
    gr += leaf * 0.55; r += leaf * 0.40
    gold = np.maximum(np.minimum(r, gr * 1.25) - bl, 0) * smooth(0.0, 0.12, r - gr) * b[2]
    r += gold * 0.30; gr += gold * 0.14
    c = np.maximum(np.stack([r, gr, bl], -1), 0)
    l = np.clip(luma(c), 1e-4, 1)[..., None]
    c = c * (1 + a[2] * 6.75 * l * (1 - l))
    cc = np.clip(c, 0, 1); s = cc * cc * (3 - 2 * cc) - cc
    c = c + np.maximum(s, s * 0.5) * a[3]
    warm = np.array([1.0, 0.92, 0.72], np.float32); cool = np.array([0.72, 0.90, 1.12], np.float32)
    l = luma(c)[..., None]
    c = c * (1 + b[3] * 0.16 * ((warm - luma(warm)) * smooth(0.45, 0.95, l) + (cool - luma(cool)) * (1 - smooth(0.05, 0.45, l))))
    l = luma(c)[..., None]
    mx = c.max(-1, keepdims=True); mn = c.min(-1, keepdims=True); dk = 1 - np.clip(l, 0, 1)
    k = 1 + a[0] * (1 - (mx - mn) / np.maximum(mx, 1e-4)) + a[1] * dk * dk
    return np.clip(l + (c - l) * k, 0, 1)
rows = []
for f in sys.argv[3:]:
    im = Image.open(f).convert('RGB'); w = 1200; im = im.resize((w, int(im.height * w / im.width)), Image.LANCZOS)
    x = np.asarray(im).astype(np.float32) / 255
    pair = np.concatenate([x, np.ones((x.shape[0], 8, 3), np.float32), grade(x)], 1)
    rows.append(pair); rows.append(np.ones((8, pair.shape[1], 3), np.float32))
sheet = Image.fromarray((np.concatenate(rows[:-1], 0) * 255 + 0.5).astype(np.uint8))
d = ImageDraw.Draw(sheet)
y = 0
for r in rows[::2]:
    for x0, t in ((10, 'AS IT IS'), (1218, 'WITH THE GRADE (colour step only, offline)')):
        d.rectangle([x0 - 4, y + 6, x0 + 8 * len(t) + 4, y + 24], fill=(0, 0, 0)); d.text((x0, y + 9), t, fill=(255, 255, 255))
    y += r.shape[0] + 8
sheet.save(sys.argv[2], quality=92)
