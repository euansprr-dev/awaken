#!/usr/bin/env python3
"""Far Sight's seam audit (SPEC §20): holes the real chunks leave in the far view.

Every pair <name>.png / <name>_holes.png in a folder is the same view, normally and in debug view 2, where far terrain
and City Sight that are hidden for the real chunks paint pure red instead. Red in the second frame that the first
doesn't have is where the far view stepped aside and no real chunk drew there. One- and two-pixel slivers along real
blocks' outlines are expected (City Sight draws stairs and the like as whole cubes, hidden there for the thinner real
block, which correctly shows what's behind): the mask is eroded by 5 pixels, so only patches a hole's size count.
Prints the hole pixels per view and exits 1 if any view has more than --allow of them.

    python3 tools/farsight/seam_audit.py <screenshots dir> [--allow 0]
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter


def red(img):
    """A mask of strongly red pixels (Lens's grading moves pure red a little)."""
    r, g, b = img.split()
    rr = r.point(lambda v: 255 if v > 170 else 0)
    gg = g.point(lambda v: 255 if v < 80 else 0)
    bb = b.point(lambda v: 255 if v < 80 else 0)
    return ImageChops.multiply(ImageChops.multiply(rr, gg), bb)


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 2
    folder = Path(args[0])
    allow = int(args[args.index("--allow") + 1]) if "--allow" in args else 0
    worst = 0
    pairs = sorted(folder.glob("*_holes.png"))
    if not pairs:
        print(f"no *_holes.png in {folder}")
        return 2
    for holes in pairs:
        base = holes.with_name(holes.name.replace("_holes.png", ".png"))
        if not base.exists():
            print(f"{holes.name}: no {base.name} to compare with")
            continue
        a = Image.open(base).convert("RGB")
        b = Image.open(holes).convert("RGB")
        if a.size != b.size:
            b = b.resize(a.size)
        only = ImageChops.subtract(red(b), red(a)).filter(ImageFilter.MinFilter(5))
        n = sum(1 for v in only.get_flattened_data() if v > 0)
        share = n / (a.size[0] * a.size[1])
        worst = max(worst, n)
        print(f"{base.stem:24s} holes {n:8d} px ({share * 100:.3f}%)")
        if n > 0:
            only.save(holes.with_name(holes.stem + "_mask.png"))
    return 1 if worst > allow else 0


if __name__ == "__main__":
    sys.exit(main())
