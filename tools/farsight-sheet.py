#!/usr/bin/env python3
"""Far Sight's side-by-side contact sheet (docs/perf/far-sight/REPORT.md).

Rows are the A/B tour's moments (tools/autopilot/farsight-ab.txt), columns the far-terrain setups, each cell the
setup's shot of that moment (from tools/farsight-ab.sh: docs/perf/far-sight/shots/<setup>/<shot>.jpg).
Usage: tools/farsight-sheet.py <out.jpg> <setup> [<setup> ...] [--shots ab_3_noon_west,ab_6_ground,...]
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docs", "perf", "far-sight", "shots")
CELL_W, CELL_H, LABEL = 720, 450, 34


def font(size):
    for path in ("/System/Library/Fonts/Supplemental/Arial.ttf", "/System/Library/Fonts/Helvetica.ttc"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 1
    out, rest = argv[1], argv[2:]
    shots = None
    if "--shots" in rest:
        i = rest.index("--shots")
        shots = rest[i + 1].split(",")
        rest = rest[:i] + rest[i + 2:]
    setups = rest
    if shots is None:
        names = set()
        for s in setups:
            d = os.path.join(ROOT, s)
            names.update(f[:-4] for f in os.listdir(d) if f.endswith(".jpg"))
        shots = sorted(names, key=lambda n: (int(n.split("_")[1]) if n.split("_")[1].isdigit() else 99, n))
    f = font(22)
    sheet = Image.new("RGB", (LABEL * 6 + CELL_W * len(setups), LABEL + (CELL_H + LABEL) * len(shots)), (18, 22, 26))
    draw = ImageDraw.Draw(sheet)
    for c, s in enumerate(setups):
        draw.text((LABEL * 6 + c * CELL_W + 10, 6), s, fill=(230, 235, 240), font=f)
    for r, shot in enumerate(shots):
        y = LABEL + r * (CELL_H + LABEL)
        draw.text((8, y + CELL_H // 2), shot.replace("ab_", ""), fill=(200, 205, 210), font=font(18))
        for c, s in enumerate(setups):
            p = os.path.join(ROOT, s, shot + ".jpg")
            x = LABEL * 6 + c * CELL_W
            if os.path.exists(p):
                im = Image.open(p).convert("RGB")
                im.thumbnail((CELL_W - 6, CELL_H - 6))
                sheet.paste(im, (x + 3, y + 3))
            else:
                draw.text((x + 20, y + CELL_H // 2), "(no shot)", fill=(150, 150, 150), font=f)
    sheet.save(out, quality=84)
    print(f"{out}: {len(shots)} moments x {len(setups)} setups")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
