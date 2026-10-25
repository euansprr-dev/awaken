"""The Ascent's blocks (docs/SPEC.md §17.6, §17.9): the updraft vent, a Precursor grate that breathes a column of rising
air (the Wilds' pylons stand on them); the anchor ring, a glowing ring for the rope (the sky platforms' ladders); the
wing core, a sky platform's heart (a glyph of two wings in light). Pixel art in the mod's palette: the Precursor's dark slate round a grate whose gaps
glow with the Signal's cyan, and a band of light round its sides.

    python3 tools/art/build_all.py ascent_blocks        textures
    python3 tools/art/specs/ascent_blocks.py content    blockstate, model, item model, loot table, name, pickaxe tag
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
MERGE = os.path.join(ROOT, "src/main/merge/ascent")

P.RAMPS.update({
    "ventglow": ["#0d3b5c", "#1d6f9e", "#3fa6d8", "#7fd3ff", "#c4ecff", "#f2fbff"],
})


def canvas(fill):
    c = Canvas()
    for y in range(16):
        for x in range(16):
            c.set(x, y, fill(x, y))
    return c


def vent_top():
    """A round grate: a slate rim, four bars across, light in the gaps (brightest at the centre)."""
    st, gl = ramp("precursor"), ramp("ventglow")
    rng = rng_for("ascent_updraft_vent_top")
    base, glow = Canvas(), Canvas()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            bar = (y % 4) == 1
            if r > 6.6:
                c = st[2 + (1 if rng.random() < 0.3 else 0) - (1 if (x + 2 * y) % 9 == 0 else 0)]
            elif r > 5.6:
                c = st[4]
            elif bar:
                c = st[3] if x % 5 else st[4]
            else:
                k = 4 if r < 2.2 else 3 if r < 4.2 else 2
                c = gl[k]
                glow.set(x, y, gl[k + 1])
            base.set(x, y, c)
    return base, glow


def vent_side():
    """Slate with a band of light round it near the top, where the air is drawn in."""
    st, gl = ramp("precursor"), ramp("ventglow")
    rng = rng_for("ascent_updraft_vent_side")
    base, glow = Canvas(), Canvas()
    for y in range(16):
        for x in range(16):
            if y in (3, 4):
                c = gl[3] if y == 3 else gl[2]
                glow.set(x, y, gl[4] if y == 3 else gl[3])
            elif y in (2, 5):
                c = st[4]
            else:
                c = st[1 + (1 if rng.random() < 0.35 else 0) + (1 if (x * 3 + y) % 11 == 0 else 0)]
            base.set(x, y, c)
    for x in (3, 8, 13):
        base.set(x, 9, st[0])
        base.set(x, 10, st[0])
    return base, glow


def anchor_ring():
    """Slate with a ring of light on every face: something to aim a rope at from far below."""
    st, gl = ramp("precursor"), ramp("ventglow")
    rng = rng_for("ascent_anchor_ring")
    base, glow = Canvas(), Canvas()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if 4.4 <= r <= 6.4:
                k = 4 if 5.0 <= r <= 5.8 else 3
                base.set(x, y, gl[k])
                glow.set(x, y, gl[k + 1])
            elif r < 2.2:
                base.set(x, y, gl[2])
                glow.set(x, y, gl[3])
            else:
                base.set(x, y, st[1 + (1 if rng.random() < 0.3 else 0) + (1 if r > 7.2 else 0)])
    return base, glow


def wing_core():
    """Pale light in a slate frame: two wings rising from a bright orb."""
    st, gl = ramp("precursor"), ramp("ventglow")
    base, glow = Canvas(), Canvas()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge:
                base.set(x, y, st[3])
                continue
            dx, dy = abs(x - 7.5), 15 - y
            # a wing on each side: a band rising outward from the centre, feathered toward the tips
            wing = 1.5 < dx < 7 and abs(dy - (4 + dx * 0.9)) < 1.6 - dx * 0.12
            centre = math.hypot(dx, dy - 5.5) < 2.1
            if wing or centre:
                k = 5 if centre else 4
                base.set(x, y, gl[k])
                glow.set(x, y, gl[k])
            else:
                base.set(x, y, gl[1] if (x + y) % 2 == 0 else st[2])
                glow.set(x, y, gl[0])
    return base, glow


def spire_crown():
    """A Tether Spire's crown: a ring of light with three points rising from it, in a slate frame (a rope's knot, crowned)."""
    st, gl = ramp("precursor"), ramp("ventglow")
    base, glow = Canvas(), Canvas()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                base.set(x, y, st[3])
                continue
            dx, dy = x - 7.5, y - 9.0
            ring = abs(math.hypot(dx, dy) - 3.6) < 0.75
            # three points above the ring: the middle one tallest
            spike = (abs(dx) < 0.6 and 1 <= y <= 5) or (abs(abs(dx) - 3.5) < 0.6 and 3 <= y <= 5)
            heart = math.hypot(dx, dy) < 1.3
            if ring or spike or heart:
                k = 5 if heart or (spike and y <= 2) else 4
                base.set(x, y, gl[k])
                glow.set(x, y, gl[k])
            else:
                base.set(x, y, gl[1] if (x * 3 + y) % 5 == 0 else st[2])
                glow.set(x, y, gl[0])
    return base, glow


def textures():
    t = {}
    t["ascent_updraft_vent_top"], t["ascent_updraft_vent_top_glow"] = vent_top()
    t["ascent_updraft_vent_side"], t["ascent_updraft_vent_side_glow"] = vent_side()
    t["ascent_anchor_ring"], t["ascent_anchor_ring_glow"] = anchor_ring()
    t["ascent_wing_core"], t["ascent_wing_core_glow"] = wing_core()
    t["ascent_spire_crown"], t["ascent_spire_crown_glow"] = spire_crown()
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c)


# ---- content ---------------------------------------------------------------------------------------------------------

SIDES = ("down", "up", "north", "south", "west", "east")


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


def vent_model():
    b = lambda n: f"awaken:block/{n}"  # noqa: E731
    faces = {d: {"texture": "#side", "cullface": d} for d in SIDES}
    faces["up"] = {"texture": "#top", "cullface": "up"}
    glow = {d: {"texture": "#side_glow", "cullface": d} for d in ("north", "south", "west", "east")}
    glow["up"] = {"texture": "#top_glow", "cullface": "up"}
    return {"parent": "minecraft:block/block",
            "textures": {"particle": b("ascent_updraft_vent_side"), "top": b("ascent_updraft_vent_top"),
                         "side": b("ascent_updraft_vent_side"), "top_glow": b("ascent_updraft_vent_top_glow"),
                         "side_glow": b("ascent_updraft_vent_side_glow")},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces},
                         {"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 15, "shade_direction_override": "up",
                          "faces": glow}]}


def glow_cube(name):
    b = f"awaken:block/ascent_{name}"
    return {"parent": "minecraft:block/block", "textures": {"particle": b, "all": b, "glow": b + "_glow"},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {d: {"texture": "#all", "cullface": d} for d in SIDES}},
                         {"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 15, "shade_direction_override": "up",
                          "faces": {d: {"texture": "#glow", "cullface": d} for d in SIDES}}]}


def content():
    for name in ("anchor_ring", "wing_core", "spire_crown"):
        _write(os.path.join(ASSETS, f"models/block/{name}.json"), glow_cube(name))
        _write(os.path.join(ASSETS, f"blockstates/{name}.json"), {"variants": {"": {"model": f"awaken:block/{name}"}}})
        _write(os.path.join(ASSETS, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"awaken:block/{name}"}})
    name = "updraft_vent"
    _write(os.path.join(ASSETS, f"models/block/{name}.json"), vent_model())
    _write(os.path.join(ASSETS, f"blockstates/{name}.json"), {"variants": {"": {"model": f"awaken:block/{name}"}}})
    _write(os.path.join(ASSETS, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": f"awaken:block/{name}"}})
    _write(os.path.join(RES, f"data/awaken/loot_table/blocks/{name}.json"), {
        "type": "minecraft:block", "random_sequence": f"awaken:blocks/{name}",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"awaken:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    _merge(os.path.join(MERGE, "assets/awaken/lang/en_us.json"), {f"block.awaken.{name}": "Updraft Vent"})
    _merge(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), {"values": [f"awaken:{name}"]})
    print("ascent_blocks: content written for", name)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    else:
        print(__doc__)
