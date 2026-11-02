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


WOOD_TEX = {"particle": "minecraft:block/stripped_dark_oak_log", "wood": "minecraft:block/stripped_dark_oak_log",
            "iron": "minecraft:block/iron_block"}


def hooks(xs, under):
    """Small iron hooks hanging from a beam's underside at {@code under}: a shank and a curl a bell's loop sits on."""
    out = []
    for x in xs:
        out.append(box([x - 0.4, under - 1.2, 7.6], [x + 0.4, under, 8.4], "#iron"))
        out.append(box([x - 0.9, under - 1.6, 7.6], [x + 0.9, under - 1.2, 8.4], "#iron"))
    return out


def frame_model():
    """Two posts and a beam with three hooks (SPEC 43.16): the bells themselves are drawn by the block's renderer."""
    wood = "#wood"
    els = [box([1, 0, 6], [3, 16, 10], wood), box([13, 0, 6], [15, 16, 10], wood), box([0, 14, 5.5], [16, 16, 10.5], wood),
           box([0, 0, 5], [4, 1, 11], wood), box([12, 0, 5], [16, 1, 11], wood)]
    els += hooks([4.5, 8, 11.5], 14)
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": dict(WOOD_TEX), "elements": els}


def beam_model():
    """A Bell Beam: a squared timber across the top of its block, iron straps at each end, four hooks."""
    wood = "#wood"
    els = [box([0, 13, 6], [16, 16, 10], wood), box([0.5, 12.8, 5.8], [1.5, 16.2, 10.2], "#iron"),
           box([14.5, 12.8, 5.8], [15.5, 16.2, 10.2], "#iron")]
    els += hooks([2.5, 6.17, 9.83, 13.5], 13)
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": dict(WOOD_TEX), "elements": els}


def great_beam_model():
    """A Great Beam: a heavy timber, three iron bands and one big hook a Great Bell hangs from."""
    wood = "#wood"
    els = [box([0, 12, 5], [16, 16, 11], wood)]
    for x in (1.0, 7.0, 13.0):
        els.append(box([x, 11.8, 4.8], [x + 2, 16.2, 11.2], "#iron"))
    els += [box([7.4, 10.4, 7.4], [8.6, 12, 8.6], "#iron"), box([6.6, 9.8, 7.4], [9.4, 10.4, 8.6], "#iron")]
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": dict(WOOD_TEX), "elements": els}


def bell_item_model():
    """One of the hundred as a thing (SPEC 43.16): a bronze bell hanging from (8, 15, 8), its loop above."""
    els = bell_shape(8, 15, "#bronze", 1.6) + [box([7.4, 15, 7.4], [8.6, 16, 8.6], "#iron")]
    return fix_bell_boxes({"parent": "minecraft:block/block", "ambientocclusion": False,
                           "textures": {"particle": "awaken:block/gate_bell_bronze", "bronze": "awaken:block/gate_bell_bronze",
                                        "iron": "minecraft:block/iron_block"}, "elements": els})


def facing_states(model):
    return {"variants": {
        "facing=north": {"model": model}, "facing=east": {"model": model, "y": 90},
        "facing=south": {"model": model, "y": 180}, "facing=west": {"model": model, "y": 270}}}


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
    write_json("models/block/bell_frame.json", frame_model())
    write_json("blockstates/bell_frame.json", facing_states("awaken:block/bell_frame"))
    # stage 2 (SPEC 43.16): the beams, the bell as a thing, and a found bell (drawn by its renderer; the model is its particle)
    write_json("models/block/bell_beam.json", beam_model())
    write_json("blockstates/bell_beam.json", facing_states("awaken:block/bell_beam"))
    write_json("items/bell_beam.json", {"model": {"type": "minecraft:model", "model": "awaken:block/bell_beam"}})
    write_json("models/block/great_beam.json", great_beam_model())
    write_json("blockstates/great_beam.json", facing_states("awaken:block/great_beam"))
    write_json("items/great_beam.json", {"model": {"type": "minecraft:model", "model": "awaken:block/great_beam"}})
    write_json("models/item/bell.json", bell_item_model())
    write_json("items/bell.json", {"model": {"type": "minecraft:model", "model": "awaken:item/bell"}})
    write_json("models/block/found_bell.json", {"textures": {"particle": "awaken:block/gate_bell_bronze"}})
    write_json("blockstates/found_bell.json", {"variants": {"": {"model": "awaken:block/found_bell"}}})
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
