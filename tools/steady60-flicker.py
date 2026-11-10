#!/usr/bin/env python3
"""Shimmer between frames of a still scene (Steady Light, SPEC §22): for each set of shots s<time>_<view>_<n>.png in a
results folder, the mean and the 99th centile of the per-pixel change between consecutive frames, over the lower
two thirds of the picture (the ground; the sky and the held item's sway are left out), in 8-bit steps. The camera is
at rest, so what changes from one frame to the next is the picture's own noise.
tools/steady60-flicker.py run-autopilot/results/<name>"""
import sys, re, glob, os
from PIL import Image, ImageChops

def stats(a, b):
    w, h = a.size
    box = (0, h // 3, int(w * 0.62), h)
    d = ImageChops.difference(a.crop(box).convert("L"), b.crop(box).convert("L"))
    hist = d.histogram()
    n = sum(hist)
    mean = sum(i * c for i, c in enumerate(hist)) / n
    acc, p99 = 0, 0
    for i, c in enumerate(hist):
        acc += c
        if acc >= n * 0.99:
            p99 = i
            break
    over = sum(c for i, c in enumerate(hist) if i >= 6) / n * 100
    return mean, p99, over

root = sys.argv[1]
sets = {}
for f in sorted(glob.glob(os.path.join(root, "s*_*_*.png"))):
    m = re.match(r"(s\d+_[a-z]+)_(\d+)\.png", os.path.basename(f))
    if m:
        sets.setdefault(m.group(1), []).append(f)
print("%-14s %8s %6s %14s" % ("view", "mean", "p99", "pixels over 5"))
for name, files in sets.items():
    imgs = [Image.open(f).convert("RGB") for f in files]
    rows = [stats(imgs[i], imgs[i + 1]) for i in range(len(imgs) - 1)]
    print("%-14s %8.2f %6d %13.2f%%" % (name, sum(r[0] for r in rows) / len(rows), max(r[1] for r in rows), sum(r[2] for r in rows) / len(rows)))
