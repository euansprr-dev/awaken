"""The War Anvil (docs/weapon-forge/PLAN.md §4.1, SPEC §46.1): a horned soot-black anvil on a block of carved dark stone.
In the stone's top before the anvil, a narrow slot lit dull orange from inside, where a finished weapon stands hilt up;
a water trough cut into the stone's left side (the smith's left, where the tongs carry the blade); an iron peg on its
right side, under the horn, where the hammer and tongs hang. Bold and plain, our voxel style like the weapons: the
hammer and tongs are sculpted like them (the weapons' painter and materials) and drawn through the weapon shader.

The model faces north (the smith stands to the north); the smith's right is west (x 0), their left east (x 16).

    python3 tools/art/build_all.py war_anvil          textures
    python3 tools/art/specs/war_anvil.py content      blockstate, model, item model, loot table, name, pickaxe tag
    python3 tools/art/specs/war_anvil.py tools        the hammer's and tongs' meshes and textures
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)
from pixelart import Canvas, ramp, rng_for  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
MERGE = os.path.join(ROOT, "src/main/merge/arms")


# ---- textures --------------------------------------------------------------------------------------------------------

def stone_side():
    """Carved dark stone: courses of dressed blocks, a carved band of hammer marks round its middle."""
    st, sl = ramp("slate"), ramp("stone")
    rng = rng_for("war_anvil_stone_side")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            course = y // 4
            joint = y % 4 == 3 or (x + (course % 2) * 4) % 8 == 7
            k = 2 + (1 if rng.random() < 0.22 else 0) - (1 if rng.random() < 0.12 else 0)
            c.set(x, y, st[0] if joint else st[max(1, min(3, k))])
    for x in range(16):  # the carved band: a row of chisel marks, lit on their upper lip
        c.set(x, 6, sl[1] if x % 3 == 1 else st[1])
        c.set(x, 5, st[3] if x % 3 == 1 else c.get(x, 5))
    return c


def stone_top():
    """The stone's top: dressed flat, darker where it's been worked round, a soot ring under the anvil."""
    st = ramp("slate")
    rng = rng_for("war_anvil_stone_top")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            k = 2 + (1 if rng.random() < 0.2 else 0)
            if 4 <= y <= 14 and 2 <= x <= 13:
                k -= 1  # soot under the anvil
            c.set(x, y, st[3] if edge else st[max(0, k)])
    for x in range(4, 12):  # the slot's lip (its glow is the emissive element)
        c.set(x, 2, st[0])
        c.set(x, 4, st[0])
    return c


def slot_glow():
    """Inside the slot: embers, dull orange to a yellow core."""
    em = ramp("ember")
    rng = rng_for("war_anvil_slot")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            k = 3 + (1 if (x + y) % 5 == 0 else 0) + (1 if rng.random() < 0.15 else 0)
            c.set(x, y, em[min(5, k)])
    return c


def iron():
    """Soot-black iron, hammered: dark with a cool sheen in the dents and a few bright scuffs."""
    ob, mi = ramp("stone"), ramp("mirror")
    rng = rng_for("war_anvil_iron")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dent = (x * 3 + y * 5) % 11 == 0
            k = (1 if rng.random() < 0.3 else 0) + (1 if dent else 0)
            c.set(x, y, ob[min(2, k)])
            if rng.random() < 0.03:
                c.set(x, y, mi[1])
    return c


def iron_face():
    """The anvil's face: worked bright down its middle by every blow, dark at its edges."""
    ob, mi = ramp("stone"), ramp("mirror")
    rng = rng_for("war_anvil_face")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            d = abs(y - 7.5) / 7.5
            if d < 0.55:
                k = 1 + (1 if rng.random() < 0.35 else 0) + (1 if d < 0.25 else 0)
                c.set(x, y, mi[min(3, k)])
            else:
                c.set(x, y, ob[1 + (1 if rng.random() < 0.25 else 0)])
    return c


def water():
    """The trough's water: dark, a glint of the room across it."""
    wa = ramp("water")
    rng = rng_for("war_anvil_water")
    c = Canvas()
    for y in range(16):
        for x in range(16):
            k = 1 + (1 if (x + 2 * y) % 9 == 0 else 0) + (1 if rng.random() < 0.08 else 0)
            c.set(x, y, wa[k])
    return c


MAKERS = {"war_anvil_stone": stone_side, "war_anvil_stone_top": stone_top, "war_anvil_slot": slot_glow, "war_anvil_iron": iron,
          "war_anvil_face": iron_face, "war_anvil_water": water}


def build(out):
    for name, f in MAKERS.items():
        out.block(name, f())


# ---- content ---------------------------------------------------------------------------------------------------------

SIDES = ("down", "up", "north", "south", "west", "east")
B = "awaken:block/war_anvil_"


def el(lo, hi, tex, up=None, faces=SIDES, **extra):
    e = {"from": lo, "to": hi, "faces": {d: {"texture": (up if d == "up" and up else tex)} for d in faces}}
    e.update(extra)
    return e


def model():
    els = [
        # the stone, the trough cut into its left (east) side
        el([0, 0, 0], [13, 8, 16], "#stone", up="#stone_top"),
        el([13, 0, 0], [16, 3, 16], "#stone"),
        el([13, 3, 0], [16, 8, 2], "#stone", up="#stone_top"),
        el([13, 3, 14], [16, 8, 16], "#stone", up="#stone_top"),
        el([15, 3, 2], [16, 8, 14], "#stone", up="#stone_top"),
        el([13, 3, 2], [15, 6.5, 14], "#water", faces=("up",)),
        # the slot's embers, glowing (just above the stone's top so it reads)
        el([4, 8, 2.5], [12, 8.02, 3.5], "#slot", faces=("up",), light_emission=12, shade_direction_override="up"),
        # the anvil: foot, waist, the face with its heel, and the horn out to the smith's right (west)
        el([3, 8, 6], [13, 10, 14], "#iron"),
        el([5, 10, 7.5], [11, 12.5, 12.5], "#iron"),
        el([2, 12.5, 6], [14, 16, 13], "#iron", up="#face"),
        el([14, 13.5, 7], [15.5, 16, 12], "#iron", up="#face"),
        el([0, 13, 7.5], [2, 15.5, 11.5], "#iron"),
        el([-2, 13.6, 8.25], [0, 15, 10.75], "#iron"),
        el([-3.5, 14, 8.9], [-2, 14.8, 10.1], "#iron"),
        # the peg on the right side, under the horn, where the hammer and tongs hang
        el([-1.5, 5.5, 7.5], [0, 6.5, 8.5], "#iron"),
    ]
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": B + "iron", "stone": B + "stone", "stone_top": B + "stone_top", "water": B + "water",
                         "slot": B + "slot", "iron": B + "iron", "face": B + "face"},
            "elements": els}


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


def content():
    _write(os.path.join(ASSETS, "models/block/war_anvil.json"), model())
    variants = {}
    for i, face in enumerate(("north", "east", "south", "west")):
        v = {"model": "awaken:block/war_anvil"}
        if i:
            v["y"] = 90 * i
        variants[f"facing={face}"] = v
    _write(os.path.join(ASSETS, "blockstates/war_anvil.json"), {"variants": variants})
    _write(os.path.join(ASSETS, "items/war_anvil.json"), {"model": {"type": "minecraft:model", "model": "awaken:block/war_anvil"}})
    _write(os.path.join(RES, "data/awaken/loot_table/blocks/war_anvil.json"), {
        "type": "minecraft:block", "random_sequence": "awaken:blocks/war_anvil",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": "awaken:war_anvil"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    _merge(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), {"values": ["awaken:war_anvil"]})
    print("war_anvil: content written")


# ---- the hammer and tongs (the weapons' voxels, painter and materials) -------------------------------------------------

def tools():
    import numpy as np
    import arms_weapons as AW
    import mesh as MS
    import voxel as VX
    from PIL import Image

    M = AW.Mat
    AW.MATS.update({
        "anvil_iron": M("flint", 3, 0.75, 0.7, details=(AW.streak(period=5, run=3),)),
        "anvil_iron_face": M("mirror", 4, 0.85, 0.85),
        "anvil_ash": M("hide", 5, 0.0, 0.35, details=(AW.rows(3, -1),)),
        "anvil_wrap": M("pelt", 3, 0.0, 0.2, details=(AW.rows(2, -2),)),
        "anvil_rivet": M("bronze", 5, 0.4, 0.8),
    })
    # design space: texels, y up the handle from the hand, x across, z through
    hammer = [
        VX.box(-1, 1, -6, 10, -1, 1, "anvil_ash", "haft"),
        VX.box(-1.5, 1.5, -5, -1, -1.5, 1.5, "anvil_wrap", "grip"),
        VX.box(-3.5, 3.5, 9, 12, -1.5, 1.5, "anvil_iron", "head"),
        VX.box(3, 5, 8.5, 12.5, -2, 2, "anvil_iron_face", "face"),
        VX.box(-5, -3, 9.5, 11.5, -1, 1, "anvil_iron", "pein"),
        VX.box(-0.5, 0.5, 12, 13, -1, 1, "anvil_rivet", "wedge"),
    ]
    tongs = [
        VX.box(-2, -1, -15, -2, -0.5, 0.5, "anvil_iron", "handle"),
        VX.box(1, 2, -15, -2, -0.5, 0.5, "anvil_iron", "handle"),
        VX.box(-1.5, 1.5, -3, 0, -1, 1, "anvil_iron", "boss"),
        VX.box(-0.5, 0.5, -2, -1, -1.5, 1.5, "anvil_rivet", "rivet"),
        VX.box(-1.5, -0.5, 0, 4, -0.5, 0.5, "anvil_iron", "jaw"),
        VX.box(0.5, 1.5, 0, 4, -0.5, 0.5, "anvil_iron", "jaw"),
        VX.box(-1.5, 1.5, 4, 5, -0.5, 0.5, "anvil_iron_face", "lips"),
    ]
    same = lambda P, direction=False: np.asarray(P, dtype=float)  # noqa: E731  (kept in design texels; the game places them)
    out_dir = os.path.join(ASSETS, "arms/anvil")
    tex_dir = os.path.join(ASSETS, "textures/item/arms")
    os.makedirs(out_dir, exist_ok=True)
    for name, boxes in (("hammer", hammer), ("tongs", tongs)):
        built = VX.build(boxes, AW.painter(), same, width=32)
        built.streams = []
        MS.write(os.path.join(out_dir, name + ".mesh"), built, (150, 160, 175))
        Image.fromarray(built.texture(), "RGBA").save(os.path.join(tex_dir, "anvil_" + name + ".png"))
    print("war_anvil: hammer and tongs written")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "tools":
        tools()
    else:
        print(__doc__)
