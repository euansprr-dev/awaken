#!/usr/bin/env python3
"""The works (SPEC §43.12): the vent stone from a hot spring, the Deep's rot in a Pondling's pond, an empty town's granary
keystone, the woken clapper, and the blocks the works are built round (the Chime Perch, the Foundry's crucible, the
Storehouse's Drop and Ledger desk, a Work Plan). Painted textures, block models, blockstates, item definitions.
  python3 -I tools/art/specs/works.py            writes them under src/main/resources/assets/awaken
  python3 -I tools/art/specs/works.py preview D  also a sheet of the painted textures at 8x into D
"""
import json
import os
import random
import sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/awaken")


def rgb(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def img():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


# ---- painted textures ---------------------------------------------------------------------------------------------------

def vent_stone():
    """Dark, rough basalt split by glowing seams, warmest where they meet, with a pale crust of spring salt."""
    r = random.Random(43)
    im = img()
    px = im.load()
    stone = [rgb("#1e1b1d"), rgb("#2c2729"), rgb("#3a3335"), rgb("#4a4143")]
    for y in range(16):
        for x in range(16):
            k = r.choice([0, 1, 1, 2, 2, 2, 3])
            px[x, y] = stone[k]
    # the seams: two crooked cracks that cross near the middle
    glow = [rgb("#7a2a0c"), rgb("#c4501a"), rgb("#f08a2e"), rgb("#ffd27a")]
    seams = []
    x = 2
    for y in range(16):
        seams.append((x, y))
        x = max(1, min(14, x + r.choice([0, 0, 1, 1, -1])))
    y = 3
    for x in range(16):
        seams.append((x, y))
        y = max(1, min(14, y + r.choice([0, 0, 1, -1])))
    for (sx, sy) in seams:
        heat = 1 + (1 if abs(sx - 8) + abs(sy - 8) < 7 else 0)
        px[sx, sy] = glow[heat + (1 if r.random() < 0.3 else 0)]
        for dx, dy in ((1, 0), (0, 1)):
            nx, ny = sx + dx, sy + dy
            if nx < 16 and ny < 16 and px[nx, ny] in stone:
                px[nx, ny] = mix(px[nx, ny], glow[0], 0.5)
    # salt crust in the corners
    for _ in range(9):
        cx, cy = r.choice([(0, 15), (15, 0), (14, 14), (1, 1)])
        sx, sy = max(0, min(15, cx + r.randint(-2, 2))), max(0, min(15, cy + r.randint(-2, 2)))
        if px[sx, sy] in stone:
            px[sx, sy] = rgb("#bfb6a8") if r.random() < 0.6 else rgb("#8f877c")
    return im


def pond_rot():
    """The Deep's rot: a black-violet sludge with sickly pale specks and a slick sheen."""
    r = random.Random(77)
    im = img()
    px = im.load()
    base = [rgb("#140c18"), rgb("#1f1226"), rgb("#2c1a36"), rgb("#3b2448")]
    for y in range(16):
        for x in range(16):
            n = (x * 7 + y * 13 + r.randint(0, 6)) % 7
            px[x, y] = base[0 if n < 2 else 1 if n < 4 else 2 if n < 6 else 3]
    for _ in range(14):
        x, y = r.randint(0, 15), r.randint(0, 15)
        px[x, y] = r.choice([rgb("#9a8fb0"), rgb("#6f5f8a"), rgb("#b9c48a")])
    for x in range(3, 9):
        px[x, 4] = mix(px[x, 4], rgb("#7d6aa0"), 0.45)
    for x in range(9, 13):
        px[x, 11] = mix(px[x, 11], rgb("#7d6aa0"), 0.4)
    return im


def granary_keystone():
    """A sandstone keystone with a wheat sheaf carved in it, its edges worn, the town's mark under it."""
    im = img()
    px = im.load()
    r = random.Random(11)
    sand = [rgb("#b9a06a"), rgb("#cdb47c"), rgb("#ddc690"), rgb("#ead7a4")]
    for y in range(16):
        for x in range(16):
            px[x, y] = sand[1 + (r.random() < 0.35) + (r.random() < 0.1)]
    for i in range(16):
        px[i, 0] = sand[3]
        px[0, i] = sand[3]
        px[i, 15] = sand[0]
        px[15, i] = sand[0]
    cut, deep = rgb("#8a7246"), rgb("#6e5a36")
    # the sheaf: stalks gathered at a band, ears fanned at the top
    for y in range(6, 13):
        for x in (6, 7, 8, 9):
            px[x, y] = cut
    for x in range(5, 11):
        px[x, 9] = deep
    ears = [(4, 3), (5, 2), (6, 3), (7, 1), (8, 2), (9, 1), (10, 3), (11, 2), (5, 4), (7, 3), (9, 3), (10, 4), (6, 5), (8, 4), (9, 5)]
    for (x, y) in ears:
        px[x, y] = cut
    for (x, y) in ((7, 2), (9, 2), (6, 4), (10, 2)):
        px[x, y] = deep
    for x in (4, 5, 10, 11):
        px[x, 13] = cut
    return im


def work_plan():
    """A building plan pinned to a board: pale paper ruled in blue, a room's outline and its door, a few notes."""
    im = img()
    px = im.load()
    paper, shade, line, note = rgb("#e9e2cc"), rgb("#d3cab0"), rgb("#3f6aa8"), rgb("#7a6a52")
    for y in range(16):
        for x in range(16):
            px[x, y] = paper if (x + y) % 9 else shade
    for i in range(16):
        px[i, 0] = shade
        px[i, 15] = shade
    for x in range(3, 13):
        px[x, 3] = line
        px[x, 11] = line
    for y in range(3, 12):
        px[3, y] = line
        px[12, y] = line
    for x in (7, 8):
        px[x, 11] = paper
    px[6, 11] = line
    px[9, 11] = line
    px[7, 12] = line
    px[8, 12] = line
    for y in range(5, 10):
        px[8, y] = rgb("#7fa0d0")
    for x in range(5, 11):
        px[x, 7] = rgb("#7fa0d0")
    for x in (2, 4, 6, 9, 11, 13):
        px[x, 13] = note
    px[1, 1] = rgb("#9a3a2a")
    px[14, 1] = rgb("#9a3a2a")
    return im


def woken_clapper():
    """A bell's clapper, come loose when it woke: a bronze teardrop on its iron loop, still faintly ringing."""
    im = img()
    px = im.load()
    bronze = [rgb("#3e2610"), rgb("#6e4519"), rgb("#9c6727"), rgb("#c98f3a"), rgb("#e9bf63"), rgb("#fff0b0")]
    iron = [rgb("#2c2f36"), rgb("#5b616d"), rgb("#9aa2b0")]
    out = rgb("#1a1008")
    for (x, y) in ((7, 1), (8, 1), (6, 2), (9, 2), (6, 3), (9, 3), (7, 4), (8, 4)):
        px[x, y] = iron[1]
    px[7, 1] = iron[2]
    for y in range(5, 8):
        px[7, y] = iron[0]
        px[8, y] = iron[1]
    rows = [(7, 8), (6, 9), (5, 10), (5, 10), (4, 11), (4, 11), (5, 10), (6, 9)]
    for i, (a, b) in enumerate(rows):
        y = 8 + i
        for x in range(a, b + 1):
            t = (x - a) / max(1, b - a)
            px[x, y] = bronze[4 if t < 0.25 else 3 if t < 0.55 else 2 if t < 0.85 else 1]
    px[5, 10] = bronze[5]
    px[6, 9] = bronze[5]
    # an outline
    src = im.copy().load()
    for y in range(16):
        for x in range(16):
            if src[x, y][3]:
                continue
            if any(0 <= x + dx < 16 and 0 <= y + dy < 16 and src[x + dx, y + dy][3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = out
    # the ring it still has in it
    for (x, y) in ((1, 9), (2, 7), (14, 9), (13, 7)):
        px[x, y] = rgb("#ffe9a8", 170)
    return im


# ---- models -------------------------------------------------------------------------------------------------------------

def box(frm, to, tex, rot=None, faces=("north", "south", "east", "west", "up", "down"), uv=None):
    f = {}
    for name in faces:
        face = {"texture": tex}
        if uv:
            face["uv"] = uv
        f[name] = face
    e = {"from": frm, "to": to, "faces": f}
    if rot:
        e["rotation"] = rot
    return e


def write_json(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def chime_perch():
    """A post with a cross-bar, the woken clapper hung in the middle and two rings for the wrens to grip."""
    els = [box([7, 0, 7], [9, 14, 9], "#post"), box([6, 0, 6], [10, 1, 10], "#post"),
           box([1, 13, 7.25], [15, 14.5, 8.75], "#post"),
           box([7.6, 10.5, 7.6], [8.4, 13, 8.4], "#iron"),
           box([6.75, 7, 7.25], [9.25, 10.5, 8.75], "#bronze"), box([7.25, 6, 7.5], [8.75, 7, 8.5], "#bronze"),
           box([2, 11.5, 7.6], [3, 13, 8.4], "#iron"), box([13, 11.5, 7.6], [14, 13, 8.4], "#iron")]
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": "minecraft:block/stripped_spruce_log", "post": "minecraft:block/stripped_spruce_log",
                         "bronze": "awaken:block/gate_bell_bronze", "iron": "minecraft:block/iron_chain"},
            "elements": els}


def crucible(lit):
    """A Deepglass bowl banded in bell bronze on three short feet; molten inside while a bell's being cast."""
    els = [box([2, 0, 2], [4, 2, 4], "#bronze"), box([12, 0, 2], [14, 2, 4], "#bronze"), box([7, 0, 12], [9, 2, 14], "#bronze"),
           box([2, 2, 2], [14, 3, 14], "#bronze"),
           box([1, 3, 1], [15, 11, 15], "#glass", faces=("north", "south", "east", "west", "down")),
           box([1, 6, 0.5], [15, 7.5, 15.5], "#bronze", faces=("north", "south")), box([0.5, 6, 1], [15.5, 7.5, 15], "#bronze", faces=("east", "west")),
           box([0.5, 11, 0.5], [15.5, 13, 2], "#bronze"), box([0.5, 11, 14], [15.5, 13, 15.5], "#bronze"),
           box([0.5, 11, 2], [2, 13, 14], "#bronze"), box([14, 11, 2], [15.5, 13, 14], "#bronze"),
           box([2, 10, 2], [14, 10.5, 14], "#melt", faces=("up",))]
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": "awaken:block/gate_bell_bronze", "bronze": "awaken:block/gate_bell_bronze",
                         "glass": "awaken:block/deepglass", "melt": "minecraft:block/lava_still" if lit else "minecraft:block/blackstone"},
            "elements": els}


def drop():
    """The Drop: a sunken spruce bin with a low rim, the granary's keystone set in its front."""
    els = [box([0, 0, 0], [16, 2, 16], "#floor"),
           box([0, 2, 0], [16, 6, 2], "#rim"), box([0, 2, 14], [16, 6, 16], "#rim"),
           box([0, 2, 2], [2, 6, 14], "#rim"), box([14, 2, 2], [16, 6, 14], "#rim"),
           box([6, 1, -0.5], [10, 7, 1.5], "#key")]
    return {"parent": "minecraft:block/block",
            "textures": {"particle": "minecraft:block/spruce_planks", "floor": "minecraft:block/dark_oak_planks",
                         "rim": "minecraft:block/stripped_spruce_log", "key": "awaken:block/granary_keystone"},
            "elements": els}


def ledger_desk():
    """A slant-topped desk on four legs with the Ledger open on it, a ribbon down the middle."""
    els = [box([1, 0, 1], [3, 11, 3], "#wood"), box([13, 0, 1], [15, 11, 3], "#wood"),
           box([1, 0, 13], [3, 11, 15], "#wood"), box([13, 0, 13], [15, 11, 15], "#wood"),
           box([1, 4, 2], [15, 5, 14], "#wood"),
           box([0, 11, 0], [16, 13, 16], "#wood", rot={"angle": -22.5, "axis": "x", "origin": [8, 12, 8]}),
           box([2, 13, 3], [7.75, 13.75, 13], "#page", rot={"angle": -22.5, "axis": "x", "origin": [8, 12, 8]}),
           box([8.25, 13, 3], [14, 13.75, 13], "#page", rot={"angle": -22.5, "axis": "x", "origin": [8, 12, 8]}),
           box([7.75, 13, 2.5], [8.25, 13.6, 13.5], "#cover", rot={"angle": -22.5, "axis": "x", "origin": [8, 12, 8]})]
    return {"parent": "minecraft:block/block",
            "textures": {"particle": "minecraft:block/dark_oak_planks", "wood": "minecraft:block/dark_oak_planks",
                         "page": "minecraft:block/white_wool", "cover": "minecraft:block/red_wool"},
            "elements": els}


def work_plan_model():
    """A plan pinned to a board on two stakes, leaning back toward its door so it reads from the front."""
    els = [box([3, 0, 9], [4.5, 10, 10.5], "#stake"), box([11.5, 0, 9], [13, 10, 10.5], "#stake"),
           box([2, 4, 7.5], [14, 15, 8.5], "#board", rot={"angle": 22.5, "axis": "x", "origin": [8, 4, 8]},
               faces=("south", "east", "west", "up", "down")),
           box([2, 4, 7.5], [14, 15, 8.5], "#plan", rot={"angle": 22.5, "axis": "x", "origin": [8, 4, 8]}, faces=("north",))]
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": "minecraft:block/oak_planks", "stake": "minecraft:block/stripped_oak_log",
                         "board": "minecraft:block/oak_planks", "plan": "awaken:block/work_plan"},
            "elements": els}


def facing(model):
    return {"variants": {"facing=north": {"model": model}, "facing=east": {"model": model, "y": 90},
                         "facing=south": {"model": model, "y": 180}, "facing=west": {"model": model, "y": 270}}}


def build(out):
    """tools/art/build_all.py: this spec writes its own files (run it directly)."""


def main(argv):
    painted = {"block/vent_stone": vent_stone, "block/pond_rot": pond_rot, "block/granary_keystone": granary_keystone,
               "block/work_plan": work_plan, "item/woken_clapper": woken_clapper}
    written = []
    for rel, fn in painted.items():
        path = os.path.join(ASSETS, "textures/%s.png" % rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        fn().save(path)
        written.append(path)
    for cube in ("vent_stone", "pond_rot", "granary_keystone"):
        write_json("models/block/%s.json" % cube, {"parent": "minecraft:block/cube_all", "textures": {"all": "awaken:block/%s" % cube}})
        write_json("blockstates/%s.json" % cube, {"variants": {"": {"model": "awaken:block/%s" % cube}}})
    write_json("models/block/chime_perch.json", chime_perch())
    write_json("blockstates/chime_perch.json", {"variants": {"": {"model": "awaken:block/chime_perch"}}})
    write_json("models/block/foundry_crucible.json", crucible(False))
    write_json("models/block/foundry_crucible_lit.json", crucible(True))
    write_json("blockstates/foundry_crucible.json", {"variants": {"lit=false": {"model": "awaken:block/foundry_crucible"},
                                                                  "lit=true": {"model": "awaken:block/foundry_crucible_lit"}}})
    write_json("models/block/storehouse_drop.json", drop())
    write_json("blockstates/storehouse_drop.json", {"variants": {"": {"model": "awaken:block/storehouse_drop"}}})
    write_json("models/block/ledger_desk.json", ledger_desk())
    write_json("blockstates/ledger_desk.json", facing("awaken:block/ledger_desk"))
    write_json("models/block/work_plan.json", work_plan_model())
    write_json("blockstates/work_plan.json", facing("awaken:block/work_plan"))
    for b in ("vent_stone", "granary_keystone", "chime_perch", "foundry_crucible", "storehouse_drop", "ledger_desk", "work_plan"):
        write_json("items/%s.json" % b, {"model": {"type": "minecraft:model", "model": "awaken:block/%s" % b}})
    write_json("models/item/woken_clapper.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/woken_clapper"}})
    write_json("items/woken_clapper.json", {"model": {"type": "minecraft:model", "model": "awaken:item/woken_clapper"}})
    print("works:", len(written), "textures")
    if len(argv) > 2 and argv[1] == "preview":
        sheet = Image.new("RGBA", (len(written) * 136, 136), (40, 44, 52, 255))
        for i, p in enumerate(written):
            sheet.alpha_composite(Image.open(p).convert("RGBA").resize((128, 128), Image.NEAREST), (i * 136 + 4, 4))
        os.makedirs(argv[2], exist_ok=True)
        sheet.save(os.path.join(argv[2], "works.png"))


if __name__ == "__main__":
    main(sys.argv)
