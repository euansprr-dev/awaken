#!/usr/bin/env python3
"""Side-by-side zoomed crops for docs/perf/pixels/REPORT.md, from one tools/autopilot/pixels.txt run.

  python3 -I tools/pixels-crops.py <screenshots dir> <out dir> <crop spec>...

A crop spec is name:prefix:x:y:w:h[:zoom], in screenshot pixels; for each spec it writes <out>/<name>.png: the same
region from every shot named px_<prefix>_<tag>.png (tags in TAGS order, missing ones skipped), zoomed with nearest
neighbour (default 2x) and labelled, left to right.
"""
import os
import sys

from PIL import Image, ImageDraw

TAGS = [("100", "native"), ("075", "0.75 FSR"), ("067", "0.67 FSR"), ("067b", "0.67 bilinear"), ("050", "0.5 FSR")]
LABEL_H = 28


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 1
    src, out = argv[0], argv[1]
    os.makedirs(out, exist_ok=True)
    for spec in argv[2:]:
        parts = spec.split(":")
        name, prefix = parts[0], parts[1]
        x, y, w, h = (int(v) for v in parts[2:6])
        zoom = int(parts[6]) if len(parts) > 6 else 2
        tiles = []
        for tag, label in TAGS:
            path = os.path.join(src, f"px_{prefix}_{tag}.png")
            if not os.path.exists(path):
                continue
            with Image.open(path) as im:
                tile = im.convert("RGB").crop((x, y, x + w, y + h)).resize((w * zoom, h * zoom), Image.NEAREST)
            tiles.append((label, tile))
        if not tiles:
            print(f"{name}: no shots for px_{prefix}_*")
            continue
        tw, th = tiles[0][1].size
        sheet = Image.new("RGB", (tw * len(tiles) + 8 * (len(tiles) - 1), th + LABEL_H), (16, 20, 24))
        draw = ImageDraw.Draw(sheet)
        for i, (label, tile) in enumerate(tiles):
            ox = i * (tw + 8)
            sheet.paste(tile, (ox, LABEL_H))
            draw.text((ox + 6, 7), label, fill=(230, 235, 240))
        sheet.save(os.path.join(out, f"{name}.png"), optimize=True)
        print(f"{name}: {len(tiles)} tiles, {sheet.size[0]}x{sheet.size[1]}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
