#!/usr/bin/env python3
"""Shimmer under a small camera sway (SPEC §20): how much distant detail flickers frame to frame.

Each burst is <prefix>_<set-up>_NN.png, the same view under the same sub-pixel sway. A well-filtered image moves
smoothly under such a sway; an aliased one flickers: pixels jump between unrelated colours. The score is the mean,
over the land below the horizon, of each pixel's frame-to-frame luminance change minus what a smooth shift explains
(the change after a 3-pixel blur, which carries the sway's smooth part), in 1/255 units, and the share of pixels
that flicker hard (more than 24). A flicker map per set-up (the hard flicker, white) is written next to the frames.

    python3 tools/farsight/shimmer.py <frames dir> [prefix fs3s]
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter


def lum(img):
    a = np.asarray(img.convert("RGB"), dtype=np.float32)
    return 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]


def main():
    folder = Path(sys.argv[1])
    prefix = sys.argv[2] if len(sys.argv) > 2 else "fs3s"
    setups = sorted({p.stem.rsplit("_", 1)[0] for p in folder.glob(prefix + "_*_[0-9][0-9].png")})
    if not setups:
        print("no bursts")
        return 2
    for s in setups:
        frames = sorted(folder.glob(s + "_[0-9][0-9].png"))
        if len(frames) < 3:
            continue
        imgs = [Image.open(f).convert("RGB") for f in frames]
        w, h = imgs[0].size
        top, bot = int(h * 0.30), int(h * 0.92)   # the land, below the horizon and above the wall's top
        L = [lum(i)[top:bot] for i in imgs]
        B = [lum(i.filter(ImageFilter.GaussianBlur(3)))[top:bot] for i in imgs]
        fine, hard = [], []
        for k in range(1, len(L)):
            d = np.abs(L[k] - L[k - 1])
            smooth = np.abs(B[k] - B[k - 1])
            f = np.clip(d - smooth, 0, None)
            fine.append(f.mean())
            hard.append((f > 24).mean())
        acc = np.zeros_like(L[0])
        for k in range(1, len(L)):
            acc = np.maximum(acc, np.clip(np.abs(L[k] - L[k - 1]) - np.abs(B[k] - B[k - 1]), 0, None))
        Image.fromarray(np.clip(acc * 4, 0, 255).astype(np.uint8)).save(folder / (s + "_flicker.png"))
        print(f"{s.split('_', 1)[1]:12s} fine flicker {np.mean(fine):6.2f}   hard-flicker pixels {np.mean(hard) * 100:6.3f}%")
    return 0


if __name__ == "__main__":
    sys.exit(main())
