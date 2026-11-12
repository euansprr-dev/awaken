#!/usr/bin/env python3
"""What shimmers on far grass (SPEC §22 "The small shade's reach"): reads the bursts of tools/autopilot/
steady60-meadow-ci.txt (shots <view>_<variant>_<n>.png, six frames two ticks apart, the plants swaying and the camera breathing as in play) and says, for
each variant and each band of distance along the lawn, how much the picture changes from one frame to the next:
the mean change in 8-bit steps of luma, and the share of pixels that change by more than 12 steps (a fleck that
flips). A row's distance comes from the camera's height over the lawn and its pitch (view 1: 7.62 blocks up, 14
degrees down; view 2: 2.62 up, 5 down), with the game's 90 degree view.

  tools/lens/meadow.py <shots folder> [--gif <out folder>]

--gif also writes, for each view, a looping picture of the far band, one variant beside another, to judge by eye."""
import glob
import math
import os
import re
import sys

from PIL import Image, ImageChops, ImageDraw

VIEWS = {"v1": (7.62, 14.0), "v2": (2.62, 5.0)}
BANDS = [(6, 12), (12, 24), (24, 48), (48, 110)]
FOV = 90.0


def rows_for(height, eye, pitch, lo, hi):
    """The rows of the picture whose ground point lies between lo and hi blocks along the lawn."""
    out = []
    half = height / 2.0
    for y in range(height):
        theta = math.radians(pitch) + math.atan((y + 0.5 - half) / half * math.tan(math.radians(FOV / 2.0)))
        if theta <= 0.002:
            continue
        d = eye / math.tan(theta)
        if lo <= d < hi:
            out.append(y)
    return (min(out), max(out) + 1) if out else None


def change(a, b, box):
    d = ImageChops.difference(a.crop(box), b.crop(box))
    hist = d.histogram()
    n = sum(hist)
    mean = sum(i * c for i, c in enumerate(hist)) / n
    flecks = sum(hist[13:]) / n * 100.0
    return mean, flecks


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    folder = sys.argv[1]
    gif = sys.argv[sys.argv.index("--gif") + 1] if "--gif" in sys.argv else None
    bursts = {}
    for f in sorted(glob.glob(os.path.join(folder, "*.png"))):
        m = re.match(r"(v\d)_(.+)_(\d+)\.png$", os.path.basename(f))
        if m:
            bursts.setdefault((m.group(1), m.group(2)), []).append(f)
    if not bursts:
        print("no bursts in", folder)
        sys.exit(1)
    kept = {}
    for view in sorted({k[0] for k in bursts}):
        eye, pitch = VIEWS[view]
        print(f"\n{view}: mean change between frames in 8-bit steps / share of pixels changing by more than 12")
        print(f"{'variant':34s}" + "".join(f"{lo:>4d}-{hi:<4d} blocks   " for lo, hi in BANDS))
        for (v, name), files in sorted(bursts.items()):
            if v != view:
                continue
            frames = [Image.open(f).convert("L") for f in sorted(files)]
            w, h = frames[0].size
            line = f"{name:34s}"
            for lo, hi in BANDS:
                rows = rows_for(h, eye, pitch, lo, hi)
                if rows is None or rows[1] - rows[0] < 2:
                    line += f"{'-':>18s}"
                    continue
                box = (int(w * 0.1), rows[0], int(w * 0.9), rows[1])
                pairs = [change(frames[i], frames[i + 1], box) for i in range(len(frames) - 1)]
                line += f"{sum(p[0] for p in pairs) / len(pairs):7.2f} /{sum(p[1] for p in pairs) / len(pairs):5.1f}%    "
            print(line)
            kept[(view, name)] = sorted(files)
    if gif:
        os.makedirs(gif, exist_ok=True)
        for view in sorted({k[0] for k in kept}):
            eye, pitch = VIEWS[view]
            names = [n for (v, n) in sorted(kept) if v == view]
            first = Image.open(kept[(view, names[0])][0])
            w, h = first.size
            far = rows_for(h, eye, pitch, 20, 110)
            if far is None:
                continue
            box = (int(w * 0.3), max(far[0] - 8, 0), int(w * 0.7), far[1])
            for name in names:
                frames = []
                for f in kept[(view, name)]:
                    c = Image.open(f).convert("RGB").crop(box)
                    ImageDraw.Draw(c).text((6, 4), name, fill=(255, 255, 255))
                    frames.append(c)
                frames[0].save(os.path.join(gif, f"{view}_{name}.gif"), save_all=True, append_images=frames[1:], duration=100, loop=0)
        print("\nlooping pictures in", gif)


if __name__ == "__main__":
    main()
