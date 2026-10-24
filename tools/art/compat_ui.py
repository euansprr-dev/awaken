#!/usr/bin/env python3
"""The System's glass for the companion plugins (compat): the Jade tooltip sprite.

Same look as signal.client.Glass.panel: a deep glass gradient (#16233A to #0F1724 at ~90%), a faint signal-blue edge
(#7FC3FF at 45%), bright corner brackets top-left and bottom-right, and a faint inner line under the top edge. Drawn as
a nine-slice sprite so Jade can stretch it to any tooltip.

    python3 -I tools/art/compat_ui.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent
OUT = ROOT / "src/main/resources/assets/awaken/textures/gui/sprites/jade"

GLASS_TOP = (0x16, 0x23, 0x3A)
GLASS = (0x0F, 0x17, 0x24)
EDGE = (0x7F, 0xC3, 0xFF)


def rgba(rgb, a):
    return rgb + (round(255 * a),)


def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def system(size=32, bracket=7):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    # glass inside the 1 px margin the brackets sit in
    for y in range(1, size - 1):
        t = (y - 1) / (size - 3)
        c = mix(GLASS_TOP, GLASS, t)
        for x in range(1, size - 1):
            px[x, y] = rgba(c, 0.92 - 0.02 * t)
    # the edge
    for i in range(1, size - 1):
        for (x, y) in ((i, 1), (i, size - 2), (1, i), (size - 2, i)):
            px[x, y] = rgba(mix(EDGE, GLASS_TOP, 0.55), 0.95)
    # faint line under the top edge
    for x in range(2, size - 2):
        px[x, 2] = rgba(mix(GLASS_TOP, EDGE, 0.18), 0.95)
    # corner brackets, one pixel outside the edge, as Glass.panel draws them
    for i in range(bracket):
        px[i, 0] = rgba(EDGE, 1)
        px[0, i] = rgba(EDGE, 1)
        px[size - 1 - i, size - 1] = rgba(EDGE, 1)
        px[size - 1, size - 1 - i] = rgba(EDGE, 1)
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    system().save(OUT / "system.png")
    meta = {"gui": {"scaling": {"type": "nine_slice", "width": 32, "height": 32, "border": 8, "stretch_inner": True}}}
    (OUT / "system.png.mcmeta").write_text(json.dumps(meta, indent=2) + "\n")
    print("wrote", OUT / "system.png")


if __name__ == "__main__":
    main()
