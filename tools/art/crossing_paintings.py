#!/usr/bin/env python3
"""The crossing's paintings (SPEC 50.5): crops a 3:2 painting to 16:9 and writes the game's PNG beside its .mcmeta.

usage: crossing_paintings.py <key>=<source image> ...
Keeps the upper part of the picture, since the paintings keep their lower quarter dark and simple for the words.
"""
import json, os, sys
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "../../src/main/resources/assets/awaken/textures/gui/crossing")
W, H = 1536, 864

for arg in sys.argv[1:]:
    key, src = arg.split("=", 1)
    im = Image.open(src).convert("RGB")
    sw, sh = im.size
    want = round(sw * 9 / 16)
    top = round((sh - want) * 0.35)
    im = im.crop((0, top, sw, top + want)).resize((W, H), Image.LANCZOS)
    # 256 well-chosen colours and a dither: a fifth of the size, and the shader's drift hides the rest
    pal = im.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)
    path = os.path.join(OUT, key + ".png")
    pal.save(path, optimize=True)
    with open(path + ".mcmeta", "w") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f)
    print(key, os.path.getsize(path) // 1024, "KB")
