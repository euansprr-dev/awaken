"""The Pattern Stands (docs/arms-progression/PLAN.md §3.4): four built-in places that hold a weapon pattern, each a
different make so a structure's own stand looks as if it belongs: a hunter's rack (oak and iron), a drafting lectern
(Precursor slate), a stone plinth and a bier (dark slate). Every one carries the Blueprint paper's white line, in an
emissive layer, so a stand reads as something you can take from across a room.

    python3 tools/art/build_all.py pattern_stand        textures
    python3 tools/art/specs/pattern_stand.py content    blockstate, models, item model, loot table, name, pickaxe tag
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import pixelart as P  # noqa: E402
from pixelart import Canvas, ramp, rng_for  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
MERGE = os.path.join(ROOT, "src/main/merge/arms")
KINDS = ("rack", "lectern", "plinth", "bier")


def pair():
    return Canvas(), Canvas()


def rack():
    wd, st, sg = ramp("wood"), ramp("stone"), ramp("signal")
    rng = rng_for("pattern_stand_rack")
    base, glow = pair()
    for y in range(16):
        for x in range(16):
            k = 2 + (1 if (x * 5 + y // 4) % 7 == 0 else 0) + (1 if rng.random() < 0.18 else 0) - (1 if (x + y) % 11 == 0 else 0)
            base.set(x, y, wd[max(0, min(5, k))])
    for y in (3, 4, 11, 12):
        for x in range(16):
            base.set(x, y, st[3] if y in (3, 11) else st[2])
    for x in range(1, 15):
        base.set(x, 7, sg[2])
        glow.set(x, 7, sg[4] if x % 3 else sg[5])
    return base, glow


def lectern():
    sl, sg = ramp("precursor"), ramp("signal")
    rng = rng_for("pattern_stand_lectern")
    base, glow = pair()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            base.set(x, y, sl[4] if edge else sl[2 + (1 if rng.random() < 0.25 else 0)])
            if not edge and (x % 4 == 2 or y % 4 == 2):
                crossing = x % 4 == 2 and y % 4 == 2
                base.set(x, y, sg[3])
                glow.set(x, y, sg[5] if crossing else sg[4])
    return base, glow


def plinth():
    st, sg = ramp("stone"), ramp("signal")
    rng = rng_for("pattern_stand_plinth")
    base, glow = pair()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            base.set(x, y, st[4] if edge else st[2 + (1 if rng.random() < 0.3 else 0)])
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            ring = abs(r - 4.6) < 0.7
            cross = (abs(x - 7.5) < 0.7 or abs(y - 7.5) < 0.7) and r < 5.4
            if ring or cross:
                base.set(x, y, sg[3])
                glow.set(x, y, sg[5] if r < 1.4 else sg[4])
    return base, glow


def bier():
    sl, sg = ramp("slate"), ramp("signal")
    rng = rng_for("pattern_stand_bier")
    base, glow = pair()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            base.set(x, y, sl[4] if edge else sl[1 + (1 if rng.random() < 0.3 else 0)])
    for y in range(16):
        for x in (6, 7, 8, 9):
            base.set(x, y, sg[2] if x in (6, 9) else sg[3])
            glow.set(x, y, sg[3] if x in (6, 9) else sg[5])
    return base, glow


MAKERS = {"rack": rack, "lectern": lectern, "plinth": plinth, "bier": bier}


def build(out):
    for k, f in MAKERS.items():
        base, glow = f()
        out.block(f"pattern_stand_{k}", base)
        out.block(f"pattern_stand_{k}_glow", glow)


# ---- content ---------------------------------------------------------------------------------------------------------

SIDES = ("down", "up", "north", "south", "west", "east")

# cuboids in pixels: (from, to, rotation or None)
SHAPES = {
    "rack": [([0, 0, 4], [4, 1, 12], None), ([12, 0, 4], [16, 1, 12], None), ([1, 0, 6], [3, 14, 10], None), ([13, 0, 6], [15, 14, 10], None),
             ([1, 9, 7], [15, 11, 9], None), ([1, 4, 7], [15, 6, 9], None)],
    "lectern": [([3, 0, 3], [13, 2, 13], None), ([5, 2, 5], [11, 10, 11], None), ([1, 10, 3], [15, 12, 13], {"angle": 22.5, "axis": "x", "origin": [8, 11, 8]})],
    "plinth": [([2, 0, 2], [14, 3, 14], None), ([4, 3, 4], [12, 11, 12], None), ([3, 11, 3], [13, 13, 13], None)],
    "bier": [([1, 0, 1], [4, 6, 4], None), ([12, 0, 1], [15, 6, 4], None), ([1, 0, 12], [4, 6, 15], None), ([12, 0, 12], [15, 6, 15], None),
             ([0, 6, 0], [16, 8, 16], None)],
}


def _write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def _merge(path, data):
    cur = {}
    if os.path.exists(path):
        with open(path) as f:
            cur = json.load(f)
    if "values" in data and isinstance(cur.get("values"), list):
        cur["values"] += [v for v in data["values"] if v not in cur["values"]]
    else:
        cur.update(data)
    _write(path, cur)


def model(kind):
    b = f"awaken:block/pattern_stand_{kind}"
    faces = lambda tex: {d: {"texture": tex} for d in SIDES}  # noqa: E731
    elements = []
    for lo, hi, rot in SHAPES[kind]:
        e = {"from": lo, "to": hi, "faces": faces("#t")}
        if rot:
            e["rotation"] = rot
        elements.append(e)
    for lo, hi, rot in SHAPES[kind]:
        e = {"from": lo, "to": hi, "light_emission": 15, "shade_direction_override": "up", "faces": faces("#g")}
        if rot:
            e["rotation"] = rot
        elements.append(e)
    return {"parent": "minecraft:block/block", "textures": {"particle": b, "t": b, "g": b + "_glow"}, "elements": elements}


def content():
    variants = {}
    for kind in KINDS:
        _write(os.path.join(ASSETS, f"models/block/pattern_stand_{kind}.json"), model(kind))
        for i, face in enumerate(("north", "east", "south", "west")):
            v = {"model": f"awaken:block/pattern_stand_{kind}"}
            if i:
                v["y"] = 90 * i
            variants[f"facing={face},kind={kind}"] = v
    _write(os.path.join(ASSETS, "blockstates/pattern_stand.json"), {"variants": variants})
    _write(os.path.join(ASSETS, "items/pattern_stand.json"), {"model": {"type": "minecraft:model", "model": "awaken:block/pattern_stand_lectern"}})
    _write(os.path.join(RES, "data/awaken/loot_table/blocks/pattern_stand.json"), {
        "type": "minecraft:block", "random_sequence": "awaken:blocks/pattern_stand",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": "awaken:pattern_stand"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    _merge(os.path.join(MERGE, "assets/awaken/lang/en_us.json"), {"block.awaken.pattern_stand": "Pattern Stand"})
    _merge(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), {"values": ["awaken:pattern_stand"]})
    print("pattern_stand: content written")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    else:
        print(__doc__)
