#!/usr/bin/env python3
"""The crossing's paintings (SPEC 50.5): crops a 3:2 painting to 16:9 and writes the game's PNG beside its .mcmeta.

usage: crossing_paintings.py [--into <textures/gui subfolder>] <key>=<source image> ...
(the Far Landing's skiff crossing, SPEC 67.5, writes into gui/frontier as crossing_out and crossing_home.)
Keeps the upper part of the picture, since the paintings keep their lower quarter dark and simple for the words.
"""
import json, os, sys
from PIL import Image

GUI = os.path.join(os.path.dirname(__file__), "../../src/main/resources/assets/awaken/textures/gui")
OUT = os.path.join(GUI, "crossing")
W, H = 1536, 864

args = sys.argv[1:]
if len(args) >= 2 and args[0] == "--into":
    OUT = os.path.join(GUI, args[1])
    os.makedirs(OUT, exist_ok=True)
    args = args[2:]

for arg in args:
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
