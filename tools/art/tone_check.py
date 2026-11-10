#!/usr/bin/env python3
"""Can this family of block textures be lit? (SPEC 50.8, "Textures that can be lit")

The game shows texture x light, so a family painted as dark as its painting's shadows stays near-black under any lamp. A family's
darkness has to come from its light, never from its textures. This prints every block texture of a family with its mean brightness
(0 to 255) and the family's median, and fails when the family can't be lit:

    python3 tools/art/tone_check.py knell_            one family
    python3 tools/art/tone_check.py knell_ keep_      several

Exit 1 when a family's median is outside 95 to 150, or when any structural texture (stone, brick, flagstone, hexstone, tile, planks,
timber, iron, bed in its name) is under 60. Brightness is Rec. 709 over a texture's opaque pixels. Amber's family sits at 122.
"""
from __future__ import annotations

import os
import statistics
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BLOCKS = os.path.join(ROOT, "src", "main", "resources", "assets", "awaken", "textures", "block")
MEDIAN_LO, MEDIAN_HI, STRUCTURAL_MIN = 95, 150, 60
STRUCTURAL = ("stone", "brick", "flagstone", "hexstone", "tile", "planks", "timber", "iron", "bed")
# Structural textures that sat under 60 on 10 Nov 2026 in the two families the re-tone left as they were (Saltcomb's and the Vine
# Seat's, both lit by a sun). They pass with a note until those families are re-toned; nothing new joins this list.
KNOWN_DARK = {"salt_stone_deep", "fane_bed", "fane_planks", "fane_stone_deep", "fane_timber", "fane_timber_end"}


def brightness(path: str) -> float | None:
    """Mean luminance of a texture's opaque pixels, first frame only; None when it has none."""
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    if h > w:  # an animated strip: frames stacked downwards
        im = im.crop((0, 0, w, w))
    px = im.load()
    total, n = 0.0, 0
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a < 128:
                continue
            total += 0.2126 * r + 0.7152 * g + 0.0722 * b  # Rec. 709, the weights the family figures in FINDINGS.md used
            n += 1
    return total / n if n else None


def structural(name: str) -> bool:
    return any(word in name for word in STRUCTURAL)


def check(prefix: str) -> bool:
    names = sorted(f[:-4] for f in os.listdir(BLOCKS) if f.startswith(prefix) and f.endswith(".png"))
    if not names:
        print(f"{prefix}: no textures in {BLOCKS}")
        return False
    rows = [(n, brightness(os.path.join(BLOCKS, n + ".png"))) for n in names]
    rows = [(n, v) for n, v in rows if v is not None]
    median = statistics.median(v for _, v in rows)
    dark = [n for n, v in rows if structural(n) and v < STRUCTURAL_MIN]
    low = [n for n in dark if n not in KNOWN_DARK]
    for n, v in rows:
        mark = "  TOO DARK" if n in low else "  dark, known" if n in dark else ""
        print(f"  {n:<34} {v:6.1f}{'  structural' if structural(n) else ''}{mark}")
    ok = MEDIAN_LO <= median <= MEDIAN_HI and not low
    why = []
    if not MEDIAN_LO <= median <= MEDIAN_HI:
        why.append(f"median outside {MEDIAN_LO} to {MEDIAN_HI}")
    if low:
        why.append(f"{len(low)} structural under {STRUCTURAL_MIN}: {', '.join(low)}")
    known = f" ({len(dark) - len(low)} known dark)" if len(dark) > len(low) else ""
    print(f"{prefix}: {len(rows)} textures, median {median:.1f}  {'ok' + known if ok else 'FAIL (' + '; '.join(why) + ')'}")
    return ok


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    results = [check(p) for p in sys.argv[1:]]
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
