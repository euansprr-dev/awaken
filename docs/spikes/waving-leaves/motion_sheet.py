"""Motion proof for still screenshots: frames of one fixed camera side by side with what changed between them.

    python3 -I docs/spikes/waving-leaves/motion_sheet.py <out.png> <label> <frame1.png> <frame2.png> [...]
                                                       [-- <label> <frame1.png> ...]

Each group (a row) is a run of shots from the same camera a few ticks apart. Under every frame after the first is its
difference from the previous frame (white = moved, amplified 6x), and the row ends with the per-pixel maximum change
over the run laid over the first frame in red. Prints, per row, the share of pixels that changed by more than 12/255
and the mean change, so a waving row and a still control row can be compared as numbers too.
"""
import sys

import numpy as np
from PIL import Image, ImageDraw

THRESH = 12
SCALE = 0.25  # thumbnails at a quarter of 2880x1800


def load(path):
    return np.asarray(Image.open(path).convert("RGB"), dtype=np.int16)


def thumb(arr):
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    return img.resize((int(img.width * SCALE), int(img.height * SCALE)), Image.LANCZOS)


def main(argv):
    out = argv[0]
    groups, cur = [], None
    for a in argv[1:]:
        if a == "--":
            cur = None
            continue
        if cur is None:
            cur = [a, []]
            groups.append(cur)
        else:
            cur[1].append(a)
    rows = []
    for label, paths in groups:
        frames = [load(p) for p in paths]
        cells = [thumb(frames[0])]
        diffs = []
        for prev, nxt in zip(frames, frames[1:]):
            d = np.abs(nxt - prev).max(axis=2)
            diffs.append(d)
            cells.append(thumb(np.repeat(np.minimum(d * 6, 255)[:, :, None], 3, axis=2)))
        dmax = np.max(diffs, axis=0)
        moved = dmax > THRESH
        overlay = frames[0].copy()
        overlay[moved] = (overlay[moved] * 0.3 + np.array([255, 0, 0]) * 0.7).astype(np.int16)
        cells.append(thumb(overlay))
        share = moved.mean() * 100
        mean = float(np.mean([d.mean() for d in diffs]))
        print(f"{label}: {share:.2f}% of pixels changed by >{THRESH}/255, mean change {mean:.2f}/255 over {len(diffs)} steps")
        rows.append((f"{label}  ({share:.2f}% moved, mean {mean:.2f})", cells))
    cw, ch = rows[0][1][0].size
    ncol = max(len(c) for _, c in rows)
    sheet = Image.new("RGB", (cw * ncol, (ch + 28) * len(rows)), (20, 20, 20))
    draw = ImageDraw.Draw(sheet)
    for r, (title, cells) in enumerate(rows):
        y = r * (ch + 28)
        draw.text((8, y + 8), title, fill=(230, 230, 230))
        for c, img in enumerate(cells):
            sheet.paste(img, (c * cw, y + 28))
    sheet.save(out)
    print("sheet:", out)


if __name__ == "__main__":
    main(sys.argv[1:])
