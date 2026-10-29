"""Gate Worlds depth (SPEC §38.14): the slabs, stairs, drifts and bits the places' ground is shaped with. The same table
as dev.awaken.gates.block.DepthBlocks.DEFS.

    python3 tools/art/build_all.py gate_depth        textures (the bits; slabs, stairs and drifts wear their block's)
    python3 tools/art/specs/gate_depth.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_depth.py sheet PATH every texture at 6x, named

A slab or a stair is cut from its theme block and wears its texture, so a step reads as the same ground stepping down. A
drift piles up in layers like snow. Bits are a scatter underfoot (crumbs of wax), drawn by code, nothing copied.
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402
import gate_worlds_hive as HIVE  # noqa: E402

B = K.B

# name -> (kind, base block, English, tool)
DEPTH = {
    # Hive
    "hive_wax_slab": ("slab", "hive_wax", "Hive Wax Slab", "axe"),
    "hive_wax_stairs": ("stairs", "hive_wax", "Hive Wax Stairs", "axe"),
    "hive_wax_dark_slab": ("slab", "hive_wax_dark", "Dark Hive Wax Slab", "axe"),
    "hive_floor_slab": ("slab", "hive_floor", "Packed Wax Floor Slab", "shovel"),
    "hive_floor_stairs": ("stairs", "hive_floor", "Packed Wax Floor Stairs", "shovel"),
    "hive_comb_capped_slab": ("slab", "hive_comb_capped", "Capped Comb Slab", "axe"),
    "hive_pollen_drift": ("drift", "hive_pollen", "Pollen Drift", "shovel"),
    "hive_wax_crumbs": ("bits", "", "Wax Crumbs", None),
}


# ---- textures ---------------------------------------------------------------------------------------------------------

def tex_crumbs(P, ramp, seed, n=13):
    """Crumbs on clear: small lumps, lit on top, a shadow texel under each."""
    rp = P.ramp(ramp)
    rng = P.rng_for("crumbs", ramp, seed)
    c = P.Canvas(16, 16)
    taken = set()
    for _ in range(n * 4):
        if len(taken) >= n:
            break
        x, y = rng.randrange(1, 15), rng.randrange(1, 14)
        if any(abs(x - a) < 3 and abs(y - b) < 3 for a, b in taken):
            continue
        taken.add((x, y))
        big = rng.random() < 0.45
        shade = rng.randrange(2, len(rp) - 1)
        c.set(x, y + 1, rp[0])
        c.set(x, y, rp[shade])
        if big:
            c.set(x + 1, y, rp[shade - 1])
            c.set(x + 1, y + 1, rp[0])
            c.set(x, y - 1, rp[min(len(rp) - 1, shade + 1)])
        else:
            c.set(x, y, rp[min(len(rp) - 1, shade + 1)])
    return c


def textures():
    P = K.px(HIVE.RAMPS)
    return {"hive_wax_crumbs": tex_crumbs(P, "hwax", "hive")}


def build(out):
    for name, c in textures().items():
        out.block(name, c, None)


# ---- models -----------------------------------------------------------------------------------------------------------

def drift_model(base, h):
    """A drift {h} sixteenths deep in its block's texture (the sides show the texture's bottom {h} rows)."""
    f = {"down": {"texture": "#t", "cullface": "down"}, "up": {"texture": "#t"}}
    for d in ("north", "south", "west", "east"):
        f[d] = {"texture": "#t", "uv": [0, 16 - h, 16, 16], "cullface": d}
    return {"parent": "minecraft:block/block", "textures": {"particle": B + base, "t": B + base},
            "elements": [{"from": [0, 0, 0], "to": [16, h, 16], "faces": f}]}


def bits_model(name):
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": {"particle": B + name, "bits": B + name},
            "elements": [K.plane([0, 0.1, 0], [16, 0.1, 16], "#bits")]}


ROT = {"east": 0, "south": 90, "west": 180, "north": 270}


def stairs_state(name):
    """The stairs' 40 states: vanilla's rotations, worked out here."""
    v = {}
    for facing, r in ROT.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                m = B + name + {"straight": "", "inner_left": "_inner", "inner_right": "_inner",
                                "outer_left": "_outer", "outer_right": "_outer"}[shape]
                y = r
                if half == "bottom" and shape.endswith("_left"):
                    y += 270
                if half == "top" and shape.endswith("_right") and shape != "straight":
                    y += 90
                e = {"model": m}
                if half == "top":
                    e["x"] = 180
                if y % 360:
                    e["y"] = y % 360
                if "x" in e or "y" in e:
                    e["uvlock"] = True
                v[f"facing={facing},half={half},shape={shape}"] = e
    return {"variants": v}


def models_and_states():
    m, s = {}, {}
    for name, (kind, base, _, _) in DEPTH.items():
        t = B + base
        if kind == "slab":
            tex = {"bottom": t, "top": t, "side": t}
            m[name] = {"parent": "minecraft:block/slab", "textures": tex}
            m[name + "_top"] = {"parent": "minecraft:block/slab_top", "textures": tex}
            s[name] = {"variants": {"type=bottom": {"model": B + name}, "type=top": {"model": B + name + "_top"},
                                    "type=double": {"model": t}}}
        elif kind == "stairs":
            tex = {"bottom": t, "top": t, "side": t}
            m[name] = {"parent": "minecraft:block/stairs", "textures": tex}
            m[name + "_inner"] = {"parent": "minecraft:block/inner_stairs", "textures": tex}
            m[name + "_outer"] = {"parent": "minecraft:block/outer_stairs", "textures": tex}
            s[name] = stairs_state(name)
        elif kind == "drift":
            for layers in range(1, 8):
                m[f"{name}_{layers}"] = drift_model(base, layers * 2)
            s[name] = {"variants": {f"layers={layers}": {"model": B + (f"{name}_{layers}" if layers < 8 else base)}
                                    for layers in range(1, 9)}}
        else:
            m[name] = bits_model(name)
            s[name] = K.simple_state(name)
    return m, s


def slab_loot(name):
    """A double slab drops two."""
    K.write(os.path.join(K.RES, f"data/awaken/loot_table/blocks/{name}.json"), {
        "type": "minecraft:block", "random_sequence": f"awaken:blocks/{name}",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"awaken:{name}", "functions": [
            {"function": "minecraft:set_count", "count": 2, "add": False, "conditions": [
                {"condition": "minecraft:block_state_property", "block": f"awaken:{name}", "properties": {"type": "double"}}]},
            {"function": "minecraft:explosion_decay"}]}]}]})


def content():
    m, s = models_and_states()
    K.content("gate_depth", {k: (v[2], v[3]) for k, v in DEPTH.items()}, m, s, {})
    for name, (kind, *_rest) in DEPTH.items():
        if kind == "slab":
            slab_loot(name)
    # slabs and stairs join vanilla's tags (how they place, mobs walk them)
    for kind, tag in (("slab", "slabs"), ("stairs", "stairs")):
        vals = [f"awaken:{k}" for k, v in DEPTH.items() if v[0] == kind]
        K.merge(os.path.join(K.MERGE, f"gates/data/minecraft/tags/block/{tag}.json"), {"values": vals})
        K.merge(os.path.join(K.MERGE, f"gates/data/minecraft/tags/item/{tag}.json"), {"values": vals})


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_depth.png")
    else:
        print(__doc__)
