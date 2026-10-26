"""A gust front crossing a strip: how each frame's brightness across the screen differs from the run's mean.

    python3 -I docs/spikes/plant-sway/front_trace.py <out.png> <y0> <y1> <frame1.png> [...]

One row per frame (top = first), one column per screen column, rows y0..y1 of each frame averaged. Bright = brighter
than that column's mean over the run: the sheen and lean of the passing front, so a front rolling across the view
shows as a bright band stepping sideways row by row.
"""
import sys

import numpy as np
from PIL import Image


def main(argv):
    out, y0, y1, paths = argv[0], int(argv[1]), int(argv[2]), argv[3:]
    rows = []
    for p in paths:
        a = np.asarray(Image.open(p).convert("L"), dtype=np.float32)[y0:y1]
        rows.append(a.mean(axis=0))
    m = np.array(rows)
    dev = m - m.mean(axis=0)
    # smooth across columns so single blades don't dominate
    k = np.ones(31) / 31
    dev = np.array([np.convolve(r, k, mode="same") for r in dev])
    scale = 127 / max(1e-3, np.abs(dev).max())
    img = np.clip(128 + dev * scale, 0, 255).astype(np.uint8)
    h = 60
    big = np.repeat(img, h, axis=0)
    Image.fromarray(big).resize((big.shape[1] // 2, big.shape[0])).save(out)
    peaks = [int(np.argmax(r)) for r in dev]
    print("brightest column per frame:", peaks, "max deviation %.1f/255" % np.abs(dev).max())


if __name__ == "__main__":
    main(sys.argv[1:])
