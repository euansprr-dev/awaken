#!/usr/bin/env python3
"""The hundred bells (SPEC §43.7): the band's handbell, the bell frame your bells hang on, and a sleeping bell out in the
land (grey-green and dull while it sleeps, bronze once it's woken). Item textures, block models, blockstates and item
definitions.
  python3 -I tools/art/specs/bells.py            writes them under src/main/resources/assets/awaken
  python3 -I tools/art/specs/bells.py preview D  also a sheet of the item textures at 8x into D
"""
import json
import os
import sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/awaken")


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


BRONZE = [rgb("#3e2610"), rgb("#6e4519"), rgb("#9c6727"), rgb("#c98f3a"), rgb("#e9bf63"), rgb("#fff0b0")]
WOOD = [rgb("#2a1a10"), rgb("#4a3020"), rgb("#6b4a30"), rgb("#8a6440")]
OUT = rgb("#1a1008")


def img():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def outline(im):
    px = im.load()
    src = im.copy().load()
    for y in range(16):
        for x in range(16):
            if src[x, y][3] != 0:
                continue
            if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and src[x + dx, y + dy][3] != 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = OUT
    return im


def bell_body(px, top, rows, lit=0.25):
    """A flared bell from row {@code top}: rows is a list of (left, right) spans, lit down its left side."""
    for i, (a, b) in enumerate(rows):
        y = top + i
        for x in range(a, b + 1):
            t = (x - a) / max(1, b - a)
            k = 4 if t < lit else 3 if t < 0.55 else 2 if t < 0.85 else 1
            px[x, y] = BRONZE[k]
    a, b = rows[-1]
    for x in range(a, b + 1):
        px[x, top + len(rows)] = BRONZE[1] if a < x < b else BRONZE[0]


def handbell():
    """A bronze handbell on a turned wooden handle, held mouth-down, its clapper showing under the lip."""
    im = img()
    px = im.load()
    # the handle: a turned grip and a collar
    for y in range(0, 6):
        for x in (7, 8):
            px[x, y] = WOOD[3] if x == 7 else WOOD[2]
    px[7, 0] = WOOD[2]
    for x in range(6, 10):
        px[x, 5] = WOOD[1]
    bell_body(px, 6, [(6, 9), (5, 10), (5, 10), (4, 11), (4, 11), (3, 12), (3, 12)])
    px[5, 7] = BRONZE[5]
    px[5, 8] = BRONZE[5]
    px[7, 14] = rgb("#3a3a3a")
    px[8, 14] = rgb("#3a3a3a")
    px[8, 15] = rgb("#5a5a5a")
    return outline(im)


def bell_frame():
    """The frame: two dark timber posts and a beam, a bronze bell hanging under it."""
    im = img()
    px = im.load()
    for y in range(1, 16):
        for x in (1, 2, 13, 14):
            px[x, y] = WOOD[3] if x in (1, 13) else WOOD[1]
    for x in range(0, 16):
        px[x, 1] = WOOD[3]
        px[x, 2] = WOOD[2]
    px[7, 3] = WOOD[0]
    px[8, 3] = WOOD[0]
    bell_body(px, 4, [(6, 9), (5, 10), (5, 10), (4, 11), (4, 11), (4, 11), (3, 12)])
    px[5, 5] = BRONZE[5]
    px[7, 12] = rgb("#3a3a3a")
    px[8, 12] = rgb("#3a3a3a")
    return outline(im)


# ---- models -------------------------------------------------------------------------------------------------------------

def box(frm, to, tex, uv=None):
    faces = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        face = {"texture": tex}
        if uv:
            face["uv"] = uv
        faces[f] = face
    return {"from": frm, "to": to, "faces": faces}


def write_json(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def bell_shape(cx, top, tex, scale=1.0):
    """A bell hanging from {@code top} (model units): crown, shoulder, body, flared lip."""
    s = scale
    parts = [(2.0, 1.0), (3.0, 1.5), (3.5, 3.0), (4.0, 2.0), (5.0, 1.0)]
    out, y = [], top
    for half, h in parts:
        hh = half * s
        y0 = y - h * s
        out.append(box([cx - hh, y0, cx - hh], [cx + hh, y, cx + hh], tex))
        y = y0
    return out


def frame_model():
    wood, bronze = "#wood", "#bronze"
    els = [box([1, 0, 6], [3, 16, 10], wood), box([13, 0, 6], [15, 16, 10], wood), box([0, 14, 5.5], [16, 16, 10.5], wood),
           box([0, 0, 5], [4, 1, 11], wood), box([12, 0, 5], [16, 1, 11], wood), box([7.5, 12.5, 7.5], [8.5, 14, 8.5], "#iron")]
    els += bell_shape(8, 12.5, bronze)
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": "minecraft:block/stripped_dark_oak_log", "wood": "minecraft:block/stripped_dark_oak_log",
                         "bronze": "awaken:block/gate_bell_bronze", "iron": "minecraft:block/chain"},
            "elements": els}


def sleeping_model(tex):
    """A great bell, half sunk, mouth down: its lip on the ground and its crown at shoulder height."""
    s = 1.6
    els = []
    y = 0.0
    for half, h in reversed([(2.0, 1.0), (3.0, 1.5), (3.5, 3.0), (4.0, 2.0), (5.0, 1.0)]):
        hh = min(8.0, half * s)
        els.append(box([8 - hh, y, 8 - hh], [8 + hh, min(16.0, y + h * s), 8 + hh], "#bell"))
        y = min(16.0, y + h * s)
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": tex, "bell": tex}, "elements": els}


def fix_bell_boxes(model):
    """bell_shape builds boxes as [cx-h, y0, cx-h] to [cx+h, y, cx+h]: make their z span match their x span."""
    for e in model["elements"]:
        if e["faces"]["north"]["texture"] == "#bronze":
            x0, x1 = e["from"][0], e["to"][0]
            e["from"][2], e["to"][2] = x0, x1
    return model


def build(out):
    """tools/art/build_all.py: this spec writes its own files (run it directly)."""


def main(argv):
    written = []
    for name, fn in (("handbell", handbell), ("bell_frame", bell_frame)):
        path = os.path.join(ASSETS, "textures/item/%s.png" % name)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        fn().save(path)
        written.append(path)
    write_json("models/item/handbell.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": "awaken:item/handbell"}})
    write_json("items/handbell.json", {"model": {"type": "minecraft:model", "model": "awaken:item/handbell"}})
    write_json("models/item/bell_frame.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/bell_frame"}})
    write_json("items/bell_frame.json", {"model": {"type": "minecraft:model", "model": "awaken:item/bell_frame"}})
    write_json("models/block/bell_frame.json", fix_bell_boxes(frame_model()))
    write_json("blockstates/bell_frame.json", {"variants": {
        "facing=north": {"model": "awaken:block/bell_frame"}, "facing=east": {"model": "awaken:block/bell_frame", "y": 90},
        "facing=south": {"model": "awaken:block/bell_frame", "y": 180}, "facing=west": {"model": "awaken:block/bell_frame", "y": 270}}})
    write_json("models/block/sleeping_bell.json", sleeping_model("minecraft:block/oxidized_copper"))
    write_json("models/block/sleeping_bell_awake.json", sleeping_model("awaken:block/gate_bell_bronze"))
    write_json("blockstates/sleeping_bell.json", {"variants": {
        "awake=false": {"model": "awaken:block/sleeping_bell"}, "awake=true": {"model": "awaken:block/sleeping_bell_awake"}}})
    print("bells:", len(written), "textures")
    if len(argv) > 2 and argv[1] == "preview":
        sheet = Image.new("RGBA", (len(written) * 136, 136), (40, 44, 52, 255))
        for i, p in enumerate(written):
            sheet.alpha_composite(Image.open(p).convert("RGBA").resize((128, 128), Image.NEAREST), (i * 136 + 4, 4))
        os.makedirs(argv[2], exist_ok=True)
        sheet.save(os.path.join(argv[2], "bells.png"))


if __name__ == "__main__":
    main(sys.argv)
