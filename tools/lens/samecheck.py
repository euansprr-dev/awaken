#!/usr/bin/env python3
"""Whether a picture is the picture it should be (SPEC §22 "A Look switched back to"): compares each shot of a tour
with its reference shot from the same camera and says how far apart they are, beside how far apart two shots of the
reference itself are (the picture's own noise from shot to shot: dithers, the light buffer's settling).

  tools/lens/samecheck.py <shots folder> <reference prefix> [<noise shot>]

e.g. tools/lens/samecheck.py run-autopilot/screenshots-ci/steady60-lookswitch-ci-<run> s0_fresh s0_again_a
Every shot named <tag>_<view>.png with a <reference prefix>_<view>.png beside it is compared: the mean change in
8-bit steps, and the share of pixels that differ by more than 12. Exit 1 if any shot is more than three times its
view's noise away (or, with no noise shot, more than 1.5 steps mean)."""
import glob
import os
import re
import sys

from PIL import Image, ImageChops


def apart(a, b):
    d = ImageChops.difference(Image.open(a).convert("L"), Image.open(b).convert("L"))
    hist = d.histogram()
    n = sum(hist)
    return sum(i * c for i, c in enumerate(hist)) / n, sum(hist[13:]) / n * 100.0


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(2)
    folder, ref = sys.argv[1], sys.argv[2]
    noise = {}
    if len(sys.argv) > 3:
        m = re.match(r"(.+)_([a-z0-9]+)$", sys.argv[3])
        view = m.group(2)
        noise[view] = apart(os.path.join(folder, f"{ref}_{view}.png"), os.path.join(folder, sys.argv[3] + ".png"))
        print(f"the picture's own noise, view {view}: mean {noise[view][0]:.2f}, {noise[view][1]:.2f}% of pixels over 12")
    bad = 0
    for f in sorted(glob.glob(os.path.join(folder, "*.png"))):
        name = os.path.basename(f)[:-4]
        m = re.match(r"(.+)_([a-z0-9]+)$", name)
        if not m or m.group(1) == ref or name in sys.argv[3:]:
            continue
        view = m.group(2)
        r = os.path.join(folder, f"{ref}_{view}.png")
        if not os.path.exists(r):
            continue
        mean, over = apart(r, f)
        limit = max(noise[view][0] * 3.0, 0.6) if view in noise else 1.5
        mark = "" if mean <= limit else "   <-- NOT the fresh picture"
        bad += mean > limit
        print(f"{name:28s} mean {mean:6.2f}, {over:6.2f}% of pixels over 12{mark}")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
