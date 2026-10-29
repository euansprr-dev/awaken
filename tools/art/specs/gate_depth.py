"""Gate Worlds depth (SPEC §38.14): the slabs, stairs, drifts and bits the places' ground is shaped with. The same table
as dev.awaken.gates.block.DepthBlocks.DEFS.

    python3 tools/art/build_all.py gate_depth        textures (the bits; slabs, stairs and drifts wear their block's)
    python3 tools/art/specs/gate_depth.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_depth.py sheet PATH every texture at 6x, named

A slab or a stair is cut from its theme block and wears its texture, so a step reads as the same ground stepping down. A
drift piles up in layers like snow. Bits are a scatter underfoot (crumbs of wax, cinders, shards of glass, paper scraps,
leaves, curls of brass), drawn by code in each theme's own colours, nothing copied.
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

# Stage 2: every other theme's (base, English name, tool) for its two slab-and-stairs pairs, its drift and its bits
THEMES = {
    "ember": ([("ember_basalt", "Ember Basalt", "pickaxe"), ("ember_slag", "Slag", "pickaxe")],
              ("ember_ash", "Ash Drift"), ("ember_cinders", "Cinders")),
    "temple": ([("temple_stone", "Temple Stone", "pickaxe"), ("temple_tiles", "Temple Tile", "pickaxe")],
               ("temple_moss", "Moss Drift"), ("temple_leaves", "Fallen Leaves")),
    "archive": ([("archive_stone", "Archive Stone", "pickaxe"), ("archive_tiles", "Archive Tile", "pickaxe")],
                ("archive_paper", "Paper Drift"), ("archive_scraps", "Paper Scraps")),
    "foundry": ([("foundry_iron", "Foundry Iron", "pickaxe"), ("foundry_brick", "Foundry Brick", "pickaxe")],
                None, ("foundry_shavings", "Brass Shavings")),
    "desert": ([("desert_sandstone", "Glass Desert Sandstone", "pickaxe"), ("desert_tile", "Desert Tile", "pickaxe")],
               ("desert_sand", "Sand Drift"), ("desert_shards", "Glass Shards")),
    "market": ([("market_cobbles", "Market Cobble", "pickaxe"), ("market_boards", "Market Board", "axe")],
               None, ("market_confetti", "Lantern Paper Scraps")),
    "frost": ([("frost_stone", "Monastery Stone", "pickaxe"), ("frost_tiles", "Monastery Tile", "pickaxe")],
              ("frost_snow", "Snow Drift"), ("frost_chips", "Ice Chips")),
    "palace": ([("palace_marble", "Palace Marble", "pickaxe"), ("palace_checker", "Palace Checker", "pickaxe")],
               None, ("palace_shards", "Mirror Shards")),
    "lab": ([("lab_panel", "Lab Panel", "pickaxe"), ("lab_floor", "Lab Floor", "pickaxe")],
            ("lab_moss", "Lab Moss Drift"), ("lab_screws", "Loose Screws")),
    "dream": ([("dream_wood", "Dream Wood", "axe"), ("dream_boards", "Dream Board", "axe")],
              ("dream_plush", "Plush Drift"), ("dream_feathers", "Pillow Feathers")),
    "sky": ([("sky_rock", "Sky Rock", "pickaxe"), ("sky_marble", "Sky Marble", "pickaxe")],
            ("sky_turf", "Turf Drift"), ("sky_pebbles", "Sky Pebbles")),
    "abyss": ([("shrine_stone", "Shrine Stone", "pickaxe"), ("abyss_tiles", "Abyss Tile", "pickaxe")],
              None, ("abyss_wax_stubs", "Candle Stubs")),
    "fungal": ([("fungal_rock", "Fungal Rock", "pickaxe"), ("fungal_loam", "Loam", "shovel")],
               ("fungal_moss", "Glow Moss Drift"), ("fungal_spores", "Fallen Spore Caps")),
}
for _t, (_pairs, _drift, _bits) in THEMES.items():
    for _base, _en, _tool in _pairs:
        DEPTH[_base + "_slab"] = ("slab", _base, _en + " Slab", _tool)
        DEPTH[_base + "_stairs"] = ("stairs", _base, _en + " Stairs", _tool)
    if _drift:
        DEPTH[_drift[0] + "_drift"] = ("drift", _drift[0], _drift[1], "shovel")
    DEPTH[_bits[0]] = ("bits", "", _bits[1], None)

# each theme's bits: its art module, the ramp they're drawn in, how they're shaped
BITS = {
    "ember_cinders": ("ember", "slag", "crumbs"),
    "temple_leaves": ("temple", "tleaf", "leaves"),
    "archive_scraps": ("archive", "paper", "scraps"),
    "foundry_shavings": ("foundry", "brass", "shavings"),
    "desert_shards": ("desert", "dglass", "shards"),
    "market_confetti": ("market", "lantern", "scraps"),
    "frost_chips": ("frost", "ice", "shards"),
    "palace_shards": ("palace", "silver", "shards"),
    "lab_screws": ("lab", "plate", "crumbs"),
    "dream_feathers": ("dream", "sheet", "leaves"),
    "sky_pebbles": ("sky", "skyrock", "crumbs"),
    "abyss_wax_stubs": ("abyss", "wax", "crumbs"),
    "fungal_spores": ("fungal", "fcap", "crumbs"),
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


def tex_shapes(P, ramp, seed, style, n=11):
    """Bits of other shapes on clear: shards (thin slivers with a lit edge), scraps (small flat pieces), leaves (a vein
    down each), shavings (curls)."""
    rp = P.ramp(ramp)
    rng = P.rng_for(style, ramp, seed)
    c = P.Canvas(16, 16)
    taken = []
    for _ in range(n * 6):
        if len(taken) >= n:
            break
        x, y = rng.randrange(1, 14), rng.randrange(1, 14)
        if any(abs(x - a) < 3 and abs(y - b) < 3 for a, b in taken):
            continue
        taken.append((x, y))
        hi, lo = rp[min(len(rp) - 1, rng.randrange(len(rp) - 2, len(rp)))], rp[rng.randrange(1, max(2, len(rp) - 2))]
        if style == "shards":
            d = rng.choice(((1, 0), (0, 1), (1, 1), (1, -1)))
            for k in range(rng.randrange(2, 4)):
                c.set(x + d[0] * k, y + d[1] * k, hi if k == 0 else lo)
        elif style == "scraps":
            w, h = rng.choice(((2, 1), (2, 2), (3, 2), (1, 2)))
            for a in range(w):
                for b in range(h):
                    c.set(x + a, y + b, hi if b == 0 else lo)
            c.set(x + w - 1, y + h, rp[0])
        elif style == "leaves":
            c.set(x, y, lo)
            c.set(x + 1, y, hi)
            c.set(x, y + 1, hi)
            c.set(x + 1, y + 1, lo)
            c.set(x + 2, y + 1, rp[0])
        else:  # shavings: a curl
            for a, b in ((0, 0), (1, 0), (2, 1), (1, 2)):
                c.set(x + a, y + b, hi if (a + b) % 2 == 0 else lo)
    return c


def textures():
    import importlib
    P = K.px(HIVE.RAMPS)
    out = {"hive_wax_crumbs": tex_crumbs(P, "hwax", "hive")}
    for name, (module, ramp, style) in BITS.items():
        # each theme's own ramps, set just before its bits (two themes may name a ramp alike)
        P = K.px(importlib.import_module("gate_worlds_" + module).RAMPS)
        out[name] = tex_crumbs(P, ramp, name) if style == "crumbs" else tex_shapes(P, ramp, name, style)
    return out


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
