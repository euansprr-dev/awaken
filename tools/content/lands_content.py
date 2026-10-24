"""Generates the Awakened Lands' data and asset JSON (docs/modules/lands.md): the blocks' blockstates, models, client
item definitions, loot tables, recipes, tags, data maps and lang, and everything in the lands pack (trees, ground cover,
the two biomes, their placement, the hero-tree structures). Numbers live here; edit, then run:

    python3 tools/content/lands_content.py

Block ids must match dev.awaken.terrain.LandsBlocks; tree feature ids match dev.awaken.terrain.tree.LandsTrees.
Biome tag files are not written here (the biome table owns them), except the hero-tree structure tags.
"""
import importlib.util
import json
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
DATA = os.path.join(RES, "data/awaken")
MERGE = os.path.join(ROOT, "src/main/merge/terrain")
PACK = os.path.join(RES, "datapacks/awakened_lands/data")

_spec = importlib.util.spec_from_file_location("lands_art", os.path.join(ROOT, "tools/art/specs/lands.py"))
art = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(art)


def _load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


# each later pair of Awakened biomes has its own generator, called at the hooks below (each exposes block_assets,
# block_data, tags, flammable, strip_map, lang and pack): Glass Steppe and Shattered Highlands; Frost Spires and Mirror Marsh
lands2 = _load("lands2_content")
lands3 = _load("lands3_content")
PAIRS = (lands2, lands3)

WRITTEN = []


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    WRITTEN.append(os.path.relpath(path, ROOT))


def aw(name):
    return "awaken:" + name


def mc(name):
    return "minecraft:" + name


# =====================================================================================================================
# Blocks: assets

LOGS = {  # name: (side texture, end texture)
    "elder_log": ("elder_log", "elder_log_top"),
    "stripped_elder_log": ("stripped_elder_log", "stripped_elder_log_top"),
    "elder_wood": ("elder_log", "elder_log"),
    "stripped_elder_wood": ("stripped_elder_log", "stripped_elder_log"),
    "lumen_log": ("lumen_log", "lumen_log_top"),
    "stripped_lumen_log": ("stripped_lumen_log", "stripped_lumen_log_top"),
    "lumen_wood": ("lumen_wood", "lumen_wood"),  # the limbs' slate bark: branches read as wood, not beams
    "stripped_lumen_wood": ("stripped_lumen_log", "stripped_lumen_log"),
    "veined_lumen_log": ("veined_lumen_log", "lumen_log_top"),
}
PLANKS = ["elder_planks", "lumen_planks"]
SAPLINGS = ["elder_sapling", "lumen_sapling"]
NAMES = {
    "elder_log": "Elder Log", "stripped_elder_log": "Stripped Elder Log", "elder_wood": "Elder Wood",
    "stripped_elder_wood": "Stripped Elder Wood", "elder_planks": "Elder Planks", "elder_leaves": "Elder Leaves",
    "elder_leaves_gold": "Golden Elder Leaves",
    "elder_sapling": "Elder Sapling",
    "lumen_log": "Lumen Log", "stripped_lumen_log": "Stripped Lumen Log", "lumen_wood": "Lumen Wood",
    "stripped_lumen_wood": "Stripped Lumen Wood", "lumen_planks": "Lumen Planks", "lumen_leaves": "Lumen Leaves",
    "lumen_sapling": "Lumen Sapling", "lumen_moss": "Lumen Moss", "lumen_cap": "Lumen Caps", "lumen_vines": "Lumen Vines",
    "veined_lumen_log": "Veined Lumen Log",
}

# A speck is a one-pixel emissive face laid a hair in front of the matching dim pixel of the base texture: a solid
# quad (no alpha test, so no overdraw) that samples the solid glow tile, so every mip level keeps its colour.
LIFT = 0.05


def speck(face, u, v, n=1):
    """The element for the n x n texels from (u, v) of a full-cube face (vanilla's default face UVs,
    FaceBakery.defaultFaceUV)."""
    lo, hi = -LIFT, 16 + LIFT
    if face == "up":
        a, b = [u, hi, v], [u + n, hi, v + n]
    elif face == "down":
        a, b = [u, lo, 16 - n - v], [u + n, lo, 16 - v]
    elif face == "north":
        a, b = [16 - n - u, 16 - n - v, lo], [16 - u, 16 - v, lo]
    elif face == "south":
        a, b = [u, 16 - n - v, hi], [u + n, 16 - v, hi]
    elif face == "west":
        a, b = [lo, 16 - n - v, u], [lo, 16 - v, u + n]
    else:  # east
        a, b = [hi, 16 - n - v, 16 - n - u], [hi, 16 - v, 16 - u]
    return {"from": a, "to": b, "light_emission": 15, "shade_direction_override": "up",
            "neoforge_data": {"ambient_occlusion": False},
            "faces": {face: {"texture": "#glow", "cullface": face}}}


FACES = ["up", "down", "north", "south", "west", "east"]


def lumen_leaves_models():
    """Two speckled variants (two glowing specks per face, different on every face) and a plain one."""
    pixels = art.speck_pixels("lumen_leaves")
    variants = []
    for name, seed in (("lumen_leaves", None), ("lumen_leaves_specks_a", 11), ("lumen_leaves_specks_b", 23)):
        model = {"parent": mc("block/leaves"), "textures": {"all": aw("block/lumen_leaves"), "particle": aw("block/lumen_leaves")}}
        if seed is not None:
            rng = random.Random(seed)
            base = {"from": [0, 0, 0], "to": [16, 16, 16],
                    "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#all", "cullface": f} for f in FACES}}
            els = [base]
            # fewer, brighter spores: two on the top, one under, one on each side (a soft glow, not a starfield)
            for f in FACES:
                big = f in ("up", "down")
                for (u, v) in rng.sample(pixels, 2 if f == "up" else 1):
                    els.append(speck(f, min(u, 14) if big else u, min(v, 14) if big else v, 2 if big else 1))
            model = {"parent": mc("block/block"), "ambientocclusion": True,
                     "textures": {"all": aw("block/lumen_leaves"), "glow": aw("block/lumen_glow"), "particle": aw("block/lumen_leaves")},
                     "elements": els}
        write(f"{ASSETS}/models/block/{name}.json", model)
        variants.append(name)
    write(f"{ASSETS}/blockstates/lumen_leaves.json", {"variants": {"": [
        {"model": aw("block/lumen_leaves_specks_a"), "weight": 1},
        {"model": aw("block/lumen_leaves_specks_b"), "weight": 1},
        {"model": aw("block/lumen_leaves"), "weight": 2}]}})


def lumen_moss_models():
    pixels = art.speck_pixels("lumen_moss")
    names = []
    for i, seed in enumerate((5, 9)):
        rng = random.Random(seed)
        els = [{"from": [0, 0, 0], "to": [16, 1, 16], "faces": {
            "down": {"uv": [0, 0, 16, 16], "texture": "#wool", "cullface": "down"},
            "up": {"uv": [0, 0, 16, 16], "texture": "#wool"},
            "north": {"uv": [0, 15, 16, 16], "texture": "#wool", "cullface": "north"},
            "south": {"uv": [0, 15, 16, 16], "texture": "#wool", "cullface": "south"},
            "west": {"uv": [0, 15, 16, 16], "texture": "#wool", "cullface": "west"},
            "east": {"uv": [0, 15, 16, 16], "texture": "#wool", "cullface": "east"}}}]
        for (u, v) in rng.sample(pixels, 5):
            els.append({"from": [u, 1 + LIFT, v], "to": [u + 1, 1 + LIFT, v + 1], "light_emission": 15,
                        "shade_direction_override": "up", "neoforge_data": {"ambient_occlusion": False},
                        "faces": {"up": {"texture": "#glow"}}})
        name = f"lumen_moss_{'ab'[i]}"
        write(f"{ASSETS}/models/block/{name}.json", {"parent": mc("block/thin_block"),
                                                       "textures": {"wool": aw("block/lumen_moss"), "glow": aw("block/lumen_glow"),
                                                                    "particle": aw("block/lumen_moss")}, "elements": els})
        names.append(name)
    write(f"{ASSETS}/blockstates/lumen_moss.json", {"variants": {"": [{"model": aw("block/" + n), "y": r} for n in names for r in (0, 90, 180, 270)]}})
    write(f"{ASSETS}/items/lumen_moss.json", {"model": {"type": mc("model"), "model": aw("block/lumen_moss_a")}})


def veined_log_models():
    """The veined log: a column with an emissive vein layer over its four sides (cutout, few blocks: only the spires).
    The horizontal one is vanilla's cube_column_horizontal shape (its top end turned half round)."""
    for name, horizontal in (("veined_lumen_log", False), ("veined_lumen_log_horizontal", True)):
        sides = ["north", "south", "west", "east"]
        faces = {f: {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": f} for f in sides}
        faces["down"] = {"uv": [0, 0, 16, 16], "texture": "#end", "cullface": "down"}
        faces["up"] = {"uv": [0, 0, 16, 16], "texture": "#end", "cullface": "up"}
        if horizontal:
            faces["up"]["rotation"] = 180
        glow = {f: dict(faces[f], texture="#glow") for f in sides}
        write(f"{ASSETS}/models/block/{name}.json", {
            "parent": mc("block/block"),
            "textures": {"side": aw("block/veined_lumen_log"), "end": aw("block/veined_lumen_log"), "glow": aw("block/veined_lumen_log_glow"),
                         "particle": aw("block/veined_lumen_log")},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces},
                         {"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 15, "shade_direction_override": "up",
                          "neoforge_data": {"ambient_occlusion": False}, "faces": glow}]})


def cross_with_glow(name, base, glow):
    """A cross model with an emissive overlay cross (vanilla's cross_emissive, our textures)."""
    write(f"{ASSETS}/models/block/{name}.json", {"parent": mc("block/cross_emissive"),
                                                   "textures": {"cross": aw("block/" + base), "cross_emissive": aw("block/" + glow)}})


def block_assets():
    for name, (side, end) in LOGS.items():
        if name == "veined_lumen_log":
            h = aw("block/veined_lumen_log_horizontal")
            write(f"{ASSETS}/blockstates/{name}.json", {"variants": {
                "axis=x": {"model": h, "x": 90, "y": 90}, "axis=y": {"model": aw("block/" + name)}, "axis=z": {"model": h, "x": 90}}})
            write(f"{ASSETS}/items/{name}.json", {"model": {"type": mc("model"), "model": aw("block/" + name)}})
            continue
        write(f"{ASSETS}/models/block/{name}.json", {"parent": mc("block/cube_column"),
                                                       "textures": {"end": aw("block/" + end), "side": aw("block/" + side)}})
        horizontal = side != end
        if horizontal:
            write(f"{ASSETS}/models/block/{name}_horizontal.json", {"parent": mc("block/cube_column_horizontal"),
                                                                      "textures": {"end": aw("block/" + end), "side": aw("block/" + side)}})
        h = aw(f"block/{name}_horizontal" if horizontal else f"block/{name}")
        write(f"{ASSETS}/blockstates/{name}.json", {"variants": {
            "axis=x": {"model": h, "x": 90, "y": 90}, "axis=y": {"model": aw("block/" + name)}, "axis=z": {"model": h, "x": 90}}})
        write(f"{ASSETS}/items/{name}.json", {"model": {"type": mc("model"), "model": aw("block/" + name)}})
    veined_log_models()
    for name in PLANKS:
        write(f"{ASSETS}/models/block/{name}.json", {"parent": mc("block/cube_all"), "textures": {"all": aw("block/" + name)}})
        write(f"{ASSETS}/blockstates/{name}.json", {"variants": {"": {"model": aw("block/" + name)}}})
        write(f"{ASSETS}/items/{name}.json", {"model": {"type": mc("model"), "model": aw("block/" + name)}})
    # elder leaves: fixed colours (no tint source is registered for them, so tintindex is ignored)
    for name in ("elder_leaves", "elder_leaves_gold"):
        write(f"{ASSETS}/models/block/{name}.json", {"parent": mc("block/leaves"), "textures": {"all": aw("block/" + name)}})
        write(f"{ASSETS}/blockstates/{name}.json", {"variants": {"": {"model": aw("block/" + name)}}})
        write(f"{ASSETS}/items/{name}.json", {"model": {"type": mc("model"), "model": aw("block/" + name)}})
    lumen_leaves_models()
    write(f"{ASSETS}/items/lumen_leaves.json", {"model": {"type": mc("model"), "model": aw("block/lumen_leaves_specks_a")}})
    for name in SAPLINGS:
        write(f"{ASSETS}/models/block/{name}.json", {"parent": mc("block/cross"), "textures": {"cross": aw("block/" + name)}})
        write(f"{ASSETS}/blockstates/{name}.json", {"variants": {"": {"model": aw("block/" + name)}}})
        write(f"{ASSETS}/models/item/{name}.json", {"parent": mc("item/generated"), "textures": {"layer0": aw("block/" + name)}})
        write(f"{ASSETS}/items/{name}.json", {"model": {"type": mc("model"), "model": aw("item/" + name)}})
    lumen_moss_models()
    cross_with_glow("lumen_cap", "lumen_cap", "lumen_cap_glow")
    write(f"{ASSETS}/blockstates/lumen_cap.json", {"variants": {"": {"model": aw("block/lumen_cap")}}})
    write(f"{ASSETS}/models/item/lumen_cap.json", {"parent": mc("item/generated"), "textures": {
        "layer0": aw("block/lumen_cap"), "layer1": {"sprite": aw("block/lumen_cap_glow"), "neoforge_data": {"light_emission": 15}}}})
    write(f"{ASSETS}/items/lumen_cap.json", {"model": {"type": mc("model"), "model": aw("item/lumen_cap")}})
    write(f"{ASSETS}/models/block/lumen_vines.json", {"parent": mc("block/cross"), "textures": {"cross": aw("block/lumen_vines")}})
    cross_with_glow("lumen_vines_tip", "lumen_vines_tip", "lumen_vines_tip_glow")
    write(f"{ASSETS}/blockstates/lumen_vines.json", {"variants": {"tip=false": {"model": aw("block/lumen_vines")},
                                                                    "tip=true": {"model": aw("block/lumen_vines_tip")}}})
    write(f"{ASSETS}/models/item/lumen_vines.json", {"parent": mc("item/generated"), "textures": {
        "layer0": aw("block/lumen_vines_tip"), "layer1": {"sprite": aw("block/lumen_vines_tip_glow"), "neoforge_data": {"light_emission": 15}}}})
    write(f"{ASSETS}/items/lumen_vines.json", {"model": {"type": mc("model"), "model": aw("item/lumen_vines")}})
    for pair in PAIRS:
        pair.block_assets(sys.modules[__name__])


# =====================================================================================================================
# Blocks: data

def self_drop(name):
    return {"type": mc("block"), "pools": [{"condition": {"type": mc("survives_explosion")},
                                             "entries": [{"type": mc("item"), "name": aw(name)}], "rolls": 1}],
            "random_sequence": aw("blocks/" + name)}


SHEARS_OR_SILK = {"type": mc("any_of"), "terms": [mc("tool/can_shear"), mc("tool/can_silk_touch")]}


def leaves_loot(name, sapling):
    return {"type": mc("block"), "pools": [
        {"entries": [{"type": mc("alternatives"), "children": [
            {"type": mc("item"), "condition": SHEARS_OR_SILK, "name": aw(name)},
            {"type": mc("item"), "condition": {"type": mc("all_of"), "terms": [
                {"type": mc("survives_explosion")},
                {"type": mc("table_bonus"), "chances": [0.05, 0.0625, 0.083333336, 0.1], "enchantment": mc("fortune")}]},
             "name": aw(sapling)}]}], "rolls": 1},
        {"condition": {"type": mc("inverted"), "term": SHEARS_OR_SILK},
         "entries": [{"type": mc("item"), "condition": {"type": mc("table_bonus"), "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1],
                                                          "enchantment": mc("fortune")},
                      "modifier": [{"type": mc("set_count"), "count": {"type": mc("uniform"), "max": 2, "min": 1}},
                                   {"type": mc("explosion_decay")}], "name": mc("stick")}], "rolls": 1}],
        "random_sequence": aw("blocks/" + name)}


def block_data():
    for name in list(LOGS) + PLANKS + SAPLINGS + ["lumen_moss", "lumen_cap"]:
        write(f"{DATA}/loot_table/blocks/{name}.json", self_drop(name))
    write(f"{DATA}/loot_table/blocks/elder_leaves.json", leaves_loot("elder_leaves", "elder_sapling"))
    write(f"{DATA}/loot_table/blocks/elder_leaves_gold.json", leaves_loot("elder_leaves_gold", "elder_sapling"))
    write(f"{DATA}/loot_table/blocks/lumen_leaves.json", leaves_loot("lumen_leaves", "lumen_sapling"))
    write(f"{DATA}/loot_table/blocks/lumen_vines.json", {"type": mc("block"), "pools": [
        {"condition": SHEARS_OR_SILK, "entries": [{"type": mc("item"), "name": aw("lumen_vines")}], "rolls": 1}],
        "random_sequence": aw("blocks/lumen_vines")})
    for kind in ("elder", "lumen"):
        write(f"{DATA}/recipe/{kind}_planks.json", {"type": mc("crafting_shapeless"), "category": "building", "group": "planks",
                                                     "ingredients": [f"#awaken:{kind}_logs"],
                                                     "result": {"count": 4, "id": aw(f"{kind}_planks")}})
        for src, dst in ((f"{kind}_log", f"{kind}_wood"), (f"stripped_{kind}_log", f"stripped_{kind}_wood")):
            write(f"{DATA}/recipe/{dst}.json", {"type": mc("crafting_shaped"), "category": "building", "group": "bark",
                                                 "key": {"#": aw(src)}, "pattern": ["##", "##"],
                                                 "result": {"count": 3, "id": aw(dst)}})
    # tags (merged with every other module's at build time)
    btags = {
        "awaken:elder_logs": [aw(n) for n in LOGS if "elder" in n],
        "awaken:lumen_logs": [aw(n) for n in LOGS if "lumen" in n],
        "minecraft:logs_that_burn": ["#awaken:elder_logs", "#awaken:lumen_logs"],
        "minecraft:overworld_natural_logs": [aw("elder_log"), aw("lumen_log")],
        "minecraft:planks": [aw(n) for n in PLANKS],
        "minecraft:leaves": [aw("elder_leaves"), aw("elder_leaves_gold"), aw("lumen_leaves")],
        "minecraft:saplings": [aw(n) for n in SAPLINGS],
        "minecraft:mineable/hoe": [aw("lumen_moss")],
        "minecraft:replaceable_by_trees": [aw("lumen_moss"), aw("lumen_cap"), aw("lumen_vines")],
        "minecraft:replaceable_by_mushrooms": [aw("lumen_moss"), aw("lumen_cap"), aw("lumen_vines")],
        "minecraft:combination_step_sound_blocks": [aw("lumen_moss")],
        "minecraft:sword_efficient": [aw("lumen_vines")],
        "minecraft:enderman_holdable": [aw("lumen_cap")],
    }
    itags = {
        "awaken:elder_logs": btags["awaken:elder_logs"],
        "awaken:lumen_logs": btags["awaken:lumen_logs"],
        "minecraft:logs_that_burn": ["#awaken:elder_logs", "#awaken:lumen_logs"],
        "minecraft:planks": btags["minecraft:planks"],
        "minecraft:leaves": btags["minecraft:leaves"],
        "minecraft:saplings": btags["minecraft:saplings"],
    }
    for pair in PAIRS:
        pair.tags(sys.modules[__name__], btags, itags)
    for kind, tags in (("block", btags), ("item", itags)):
        for tag, values in tags.items():
            ns, path = tag.split(":")
            write(f"{MERGE}/data/{ns}/tags/{kind}/{path}.json", {"values": values})
    flammable = {}
    for name in LOGS:
        flammable[aw(name)] = {"burn_odds": 5, "ignite_odds": 5}
    for name in PLANKS:
        flammable[aw(name)] = {"burn_odds": 20, "ignite_odds": 5}
    for name in ("elder_leaves", "elder_leaves_gold", "lumen_leaves"):
        flammable[aw(name)] = {"burn_odds": 60, "ignite_odds": 30}
    flammable[aw("lumen_moss")] = {"burn_odds": 20, "ignite_odds": 60}
    flammable[aw("lumen_vines")] = {"burn_odds": 100, "ignite_odds": 15}
    for pair in PAIRS:
        pair.flammable(sys.modules[__name__], flammable)
    write(f"{RES}/data/neoforge/data_maps/block/flammable.json", {"values": flammable})
    strip = {}
    for kind in ("elder", "lumen"):
        for src in (f"{kind}_log", f"{kind}_wood") + (("veined_lumen_log",) if kind == "lumen" else ()):
            dst = "stripped_lumen_log" if src == "veined_lumen_log" else "stripped_" + src
            strip[aw(src)] = {"transformer": mc("axe"), "transform_data": {
                "block_state_provider": {"type": mc("rule_based"), "rules": [
                    {"if_true": {"type": mc("matching_blocks"), "blocks": aw(src)},
                     "then": {"type": mc("copy_properties"), "source": {"id": aw(dst), "properties": {"axis": "y"}}}}]},
                "item_damage_per_use": 1, "sound": mc("item.axe.strip")}}
    for pair in PAIRS:
        pair.strip_map(sys.modules[__name__], strip)
    write(f"{RES}/data/neoforge/data_maps/block/transformables.json", {"values": strip})
    lang = {f"block.awaken.{k}": v for k, v in NAMES.items()}
    lang["biome.awaken.elderwood"] = "Elderwood"
    lang["biome.awaken.lumen_forest"] = "Lumen Forest"
    for pair in PAIRS:
        pair.lang(sys.modules[__name__], lang)
    for pair in PAIRS:
        pair.block_data(sys.modules[__name__])
    # the terrain module's lang file is shared (wind and leaves add their subtitles): keep what others wrote
    path = f"{MERGE}/assets/awaken/lang/en_us.json"
    try:
        with open(path) as f:
            existing = json.load(f)
    except (OSError, ValueError):
        existing = {}
    existing.update(lang)
    write(path, dict(sorted(existing.items())))


# =====================================================================================================================
# The lands pack: trees

def state(block, **props):
    return {"id": block, "properties": {k: str(v).lower() for k, v in props.items()}} if props else {"id": block}


def leaves_state(block):
    return state(block, distance=7, persistent=False, waterlogged=False)


def uniform(lo, hi):
    return lo if lo == hi else {"type": mc("uniform"), "min_inclusive": lo, "max_inclusive": hi}


def weighted(*entries):
    return {"type": mc("weighted"), "entries": [{"data": d, "weight": w} for d, w in entries]}


LEAF_LITTER = weighted(*[(state(mc("leaf_litter"), facing=f, segment_amount=n), 1)
                         for n in (1, 2, 3, 4) for f in ("north", "east", "south", "west")])


def tree(trunk, branch, leaves, *, base, ra, rb, width, lean, lean_start, limbs, limb_start, limb_length, limb_rise, twig,
         flare, radius, height, depth, hollow, ragged, hang, decorators, size, offset=0, limb_mass=-1, crown_mass=0, top=None, taper=0.0):
    return {
        "type": mc("tree"),
        "below_trunk_provider": mc("soil_beneath_tree"),
        "trunk_provider": state(trunk, axis="y"),
        "trunk_placer": {"type": aw("limbed_trunk_placer"), "base_height": base, "height_rand_a": ra, "height_rand_b": rb,
                         "width": width, "lean": lean, "lean_start": lean_start, "limbs": limbs, "limb_start": limb_start,
                         "limb_length": limb_length, "limb_rise": limb_rise, "twig": twig, "root_flare": flare,
                         "limb_mass": limb_mass, "crown_mass": crown_mass, **({"taper": taper} if taper else {}),
                         "branch_provider": state(branch, axis="y")},
        "foliage_provider": leaves_state(leaves),
        "foliage_placer": {"type": aw("cloud_foliage_placer"), "radius": radius, "offset": offset, "height": height, "depth": depth,
                           "hollow": hollow, "ragged": ragged, "hang": hang, **({"top_provider": top, "top_from": 0.34} if top else {})},
        "minimum_size": size,
        "decorators": decorators,
        "ignore_vines": True,
    }


def ground_ring(provider):
    return {"type": mc("alter_ground"), "provider": {"type": mc("rule_based"), "rules": [
        {"if_true": {"type": mc("matching_block_tag"), "tag": mc("beneath_tree_podzol_replaceable")}, "then": provider}]}}


def on_ground(provider, radius, tries, height=2):
    return {"type": mc("place_on_ground"), "block_state_provider": provider, "height": height, "radius": radius, "tries": tries}


VINES = {"type": aw("hanging_vines"), "body": state(aw("lumen_vines"), tip=False), "tip": state(aw("lumen_vines"), tip=True)}
LUMEN_GROUND = weighted((state(aw("lumen_moss")), 12), (state(aw("lumen_cap")), 1))


# Elder crown tops: gold where a slow noise runs high (patches of trees a few hundred blocks across, about a third of the
# wood), gold-green elsewhere: the canopy from above is a gold-green field with warm crowns in it, not one flat colour.
ELDER_TOPS = {"type": mc("noise_threshold"), "seed": 8113, "noise": {"base_octave": 0, "octave_count": 1}, "scale": 0.03,
              "threshold": 0.15, "high_chance": 0.9, "default_state": leaves_state(aw("elder_leaves")),
              "low_states": [leaves_state(aw("elder_leaves"))], "high_states": [leaves_state(aw("elder_leaves_gold"))]}


def features():
    f = {}
    # Elder: a tall pale 2x2 column, clean below, a high crown of two to four billowing masses on rising limbs
    f["lands/elder"] = tree(aw("elder_log"), aw("elder_wood"), aw("elder_leaves"), base=18, ra=4, rb=4, width=2,
                            lean=uniform(0, 1), lean_start=0.55, limbs=uniform(3, 5), limb_start=0.5, limb_length=uniform(3, 6),
                            limb_rise=0.75, twig=uniform(2, 3), flare=uniform(2, 3), radius=uniform(3, 4), height=uniform(2, 3),
                            depth=0.5, hollow=0.35, ragged=0.45, hang=0.12, limb_mass=0, crown_mass=0, top=ELDER_TOPS,
                            decorators=[on_ground(LEAF_LITTER, 5, 70), {"type": mc("shelf_mushroom"), "probability": 0.25},
                                        ground_ring(weighted((state(mc("moss_block")), 3), (state(mc("podzol"), snowy=False), 2),
                                                             (state(mc("coarse_dirt")), 1)))],
                            size={"type": mc("two_layers_feature_size"), "limit": 1, "lower_size": 1, "upper_size": 2, "min_clipped_height": 15})
    # Young elder: slim, leaning, one or two short limbs
    f["lands/young_elder"] = tree(aw("elder_log"), aw("elder_wood"), aw("elder_leaves"), base=9, ra=2, rb=2, width=1,
                                  lean=uniform(0, 2), lean_start=0.4, limbs=uniform(1, 2), limb_start=0.6, limb_length=uniform(2, 3),
                                  limb_rise=1.0, twig=uniform(1, 1), flare=uniform(0, 1), radius=uniform(2, 3), height=uniform(2, 2),
                                  depth=0.6, hollow=0.0, ragged=0.45, hang=0.08,
                                  decorators=[on_ground(LEAF_LITTER, 3, 24)],
                                  size={"type": mc("two_layers_feature_size"), "limit": 1, "lower_size": 0, "upper_size": 1, "min_clipped_height": 7})
    # Lumen tree: a short 2x2 trunk, a few slate limbs climbing steeply to broad flat pads that hide them; a curtain of
    # glowing vines hangs from the pads
    f["lands/lumen"] = tree(aw("lumen_log"), aw("lumen_wood"), aw("lumen_leaves"), base=10, ra=3, rb=3, width=2,
                            lean=uniform(0, 1), lean_start=0.5, limbs=uniform(2, 4), limb_start=0.62, limb_length=uniform(3, 5),
                            limb_rise=1.0, twig=uniform(2, 3), flare=uniform(1, 2), radius=uniform(3, 4), height=uniform(1, 1),
                            depth=0.6, hollow=0.0, ragged=0.3, hang=0.3, offset=1, limb_mass=0,
                            decorators=[dict(VINES, probability=0.17, length=uniform(2, 6)), on_ground(LUMEN_GROUND, 4, 48)],
                            size={"type": mc("two_layers_feature_size"), "limit": 1, "lower_size": 1, "upper_size": 2, "min_clipped_height": 9})
    f["lands/small_lumen"] = tree(aw("lumen_log"), aw("lumen_wood"), aw("lumen_leaves"), base=6, ra=1, rb=2, width=1,
                                  lean=uniform(0, 1), lean_start=0.4, limbs=uniform(2, 3), limb_start=0.6, limb_length=uniform(2, 3),
                                  limb_rise=1.0, twig=uniform(1, 2), flare=uniform(0, 1), radius=uniform(2, 3), height=uniform(1, 1),
                                  depth=0.6, hollow=0.0, ragged=0.3, hang=0.25, offset=1,
                                  decorators=[dict(VINES, probability=0.12, length=uniform(1, 4)), on_ground(LUMEN_GROUND, 2, 12)],
                                  size={"type": mc("two_layers_feature_size"), "limit": 1, "lower_size": 0, "upper_size": 1, "min_clipped_height": 5})
    for kind, log, carpet in (("elder", aw("elder_log"), mc("moss_carpet")), ("lumen", aw("lumen_log"), aw("lumen_moss"))):
        f[f"lands/fallen_{kind}"] = {
            "type": mc("fallen_tree"),
            "trunk_provider": state(log, axis="y"),
            "log_length": uniform(5, 11) if kind == "elder" else uniform(4, 8),
            "stump_decorators": [],
            "log_decorators": [
                {"type": mc("attached_to_logs"), "block_provider": state(carpet), "directions": ["up"], "probability": 0.45},
                {"type": mc("attached_to_logs"), "block_provider": weighted((state(mc("brown_mushroom")), 2), (state(mc("red_mushroom")), 1))
                 if kind == "elder" else state(aw("lumen_cap")), "directions": ["up"], "probability": 0.1}]}
    # tree mixes (random selectors pick a placed tree per spot)
    f["lands/elderwood_trees"] = {"type": mc("random_selector"), "default": aw("lands/young_elder"), "features": [
        {"chance": 0.03, "feature": aw("lands/fallen_elder")},
        {"chance": 0.47, "feature": aw("lands/elder")}]}
    f["lands/lumen_trees"] = {"type": mc("random_selector"), "default": aw("lands/small_lumen"), "features": [
        {"chance": 0.03, "feature": aw("lands/fallen_lumen")},
        {"chance": 0.55, "feature": aw("lands/lumen")}]}
    # ground
    f["lands/elder_moss_patch"] = {
        "type": mc("vegetation_patch"), "surface": "floor", "depth": 1, "vertical_range": 5, "extra_bottom_block_chance": 0.0,
        "extra_edge_column_chance": 0.3, "ground_state": state(mc("moss_block")), "replaceable": "#minecraft:moss_replaceable",
        "vegetation_chance": 0.55, "xz_radius": uniform(3, 6),
        "vegetation_feature": {"feature": {"type": mc("simple_block"), "to_place": weighted(
            (state(mc("moss_carpet")), 30), (state(mc("fern")), 22), (state(mc("short_grass")), 18),
            (state(mc("large_fern"), half="lower"), 8), (state(mc("leaf_litter"), facing="north", segment_amount=3), 10))}, "placement": []}}
    f["lands/lumen_moss_patch"] = {
        "type": mc("vegetation_patch"), "surface": "floor", "depth": 1, "vertical_range": 5, "extra_bottom_block_chance": 0.0,
        "extra_edge_column_chance": 0.4, "ground_state": state(mc("grass_block"), snowy=False), "replaceable": "#minecraft:moss_replaceable",
        "vegetation_chance": 0.85, "xz_radius": uniform(3, 6),
        "vegetation_feature": {"feature": {"type": mc("simple_block"), "to_place": weighted(
            (state(aw("lumen_moss")), 70), (state(aw("lumen_cap")), 4), (state(mc("fern")), 8), (state(mc("short_grass")), 6))},
            "placement": []}}
    f["lands/lumen_cap"] = {"type": mc("simple_block"), "to_place": state(aw("lumen_cap"))}
    return f


def scatter(feature, count, spread, tries, below=None, survive=None, rarity=None, heightmap="MOTION_BLOCKING_NO_LEAVES"):
    """Vanilla's patch shape (26.3: counts and offsets as placement): a few centres per chunk, many tries round each."""
    pl = []
    if rarity:
        pl.append({"type": mc("rarity_filter"), "chance": rarity})
    if count != 1:
        pl.append({"type": mc("count"), "count": count})
    pl += [{"type": mc("in_square")}, {"type": mc("heightmap"), "heightmap": heightmap}, {"type": mc("biome")},
           {"type": mc("count"), "count": tries},
           {"type": mc("offset"), "x": {"type": mc("trapezoid"), "max": spread, "min": -spread, "plateau": 0},
            "y": {"type": mc("trapezoid"), "max": 3, "min": -3, "plateau": 0},
            "z": {"type": mc("trapezoid"), "max": spread, "min": -spread, "plateau": 0}}]
    preds = [{"type": mc("matching_block_tag"), "tag": mc("air")}]
    if below:
        preds.append({"type": mc("matching_blocks"), "blocks": below, "offset": [0, -1, 0]})
    if survive:
        preds.append({"type": mc("would_survive"), "state": survive})
    pl.append({"type": mc("block_predicate_filter"), "predicate": preds[0] if len(preds) == 1 else {"type": mc("all_of"), "predicates": preds}})
    return {"feature": feature, "placement": pl}


def trees_placed(feature, counts, sapling):
    return {"feature": feature, "placement": [
        {"type": mc("count"), "count": {"type": mc("weighted_list"), "distribution": [{"data": c, "weight": w} for c, w in counts]}},
        {"type": mc("in_square")}, {"type": mc("surface_water_depth_filter"), "max_water_depth": 0},
        {"type": mc("heightmap"), "heightmap": "OCEAN_FLOOR"}, {"type": mc("biome")},
        {"type": mc("block_predicate_filter"), "predicate": {"type": mc("would_survive"), "state": state(sapling, stage=0)}}]}


def placed():
    p = {}
    for name in ("elder", "young_elder", "lumen", "small_lumen", "fallen_elder", "fallen_lumen"):
        sap = aw("elder_sapling") if "elder" in name else aw("lumen_sapling")
        p[f"lands/{name}"] = {"feature": aw("lands/" + name), "placement": [
            {"type": mc("block_predicate_filter"), "predicate": {"type": mc("would_survive"), "state": state(sap, stage=0)}}]}
    p["lands/elderwood_trees"] = trees_placed(aw("lands/elderwood_trees"), [(5, 3), (6, 4), (7, 2)], aw("elder_sapling"))
    p["lands/lumen_trees"] = trees_placed(aw("lands/lumen_trees"), [(4, 3), (5, 4), (6, 2)], aw("lumen_sapling"))
    p["lands/elder_moss_patch"] = {"feature": aw("lands/elder_moss_patch"), "placement": [
        {"type": mc("count"), "count": uniform(0, 2)}, {"type": mc("in_square")},
        {"type": mc("heightmap"), "heightmap": "MOTION_BLOCKING_NO_LEAVES"}, {"type": mc("biome")}]}
    p["lands/lumen_moss_patch"] = {"feature": aw("lands/lumen_moss_patch"), "placement": [
        {"type": mc("count"), "count": uniform(1, 3)}, {"type": mc("in_square")},
        {"type": mc("heightmap"), "heightmap": "MOTION_BLOCKING_NO_LEAVES"}, {"type": mc("biome")}]}
    p["lands/elder_leaf_litter"] = scatter(mc("leaf_litter"), 4, 7, 32, below=[mc("grass_block"), mc("moss_block"), mc("podzol")])
    p["lands/elder_ferns"] = scatter(mc("taiga_grass"), 2, 7, 24)
    p["lands/elder_large_ferns"] = scatter(mc("large_fern"), 1, 6, 24, rarity=3)
    p["lands/lumen_caps"] = scatter(aw("lands/lumen_cap"), 3, 4, 14, survive=state(aw("lumen_cap")))
    p["lands/lumen_fireflies"] = scatter(mc("firefly_bush"), 2, 5, 10, survive=state(mc("firefly_bush")))
    return p


# =====================================================================================================================
# The lands pack: biomes

VANILLA = os.path.abspath(os.path.join(ROOT, "../Awaken/.ref/assets/data/minecraft/worldgen/biome"))


def archetype(name):
    with open(os.path.join(VANILLA, name + ".json")) as f:
        return json.load(f)


def biome(base, *, sky, fog, water, water_fog, grass, foliage, dry, swap, vegetal):
    b = archetype(base)
    attrs = dict(b["attributes"])
    attrs["minecraft:visual/sky_color"] = sky
    attrs["minecraft:visual/fog_color"] = fog
    attrs["minecraft:visual/water_fog_color"] = water_fog
    b["attributes"] = dict(sorted(attrs.items()))
    b["effects"] = {"water_color": water, "grass_color": grass, "foliage_color": foliage, "dry_foliage_color": dry}
    steps = [list(s) for s in b["features"]]
    veg = steps[9]
    out = []
    for f in veg:
        if f in swap:
            out += swap[f]
        elif f not in vegetal.get("drop", ()):
            out.append(f)
    steps[9] = out
    b["features"] = steps
    return b


def biomes():
    elder = biome("old_growth_birch_forest", sky="#88b2f4", fog="#d8d8bf", water="#4aa7c9", water_fog="#0f3b49",
                  grass="#8ab35c", foliage="#a9b64c", dry="#d9a53c",
                  swap={"minecraft:birch_tall": [aw("lands/elder_moss_patch"), aw("lands/elderwood_trees")],
                        "minecraft:patch_grass_forest": [mc("patch_grass_forest"), aw("lands/elder_ferns"), aw("lands/elder_large_ferns"),
                                                         aw("lands/elder_leaf_litter")]},
                  vegetal={})
    lumen = biome("dark_forest", sky="#7fa3e4", fog="#a3c4ca", water="#2a93a6", water_fog="#06303a",
                  grass="#40735f", foliage="#2f8076", dry="#5f8a8c",
                  swap={"minecraft:dark_forest_vegetation": [aw("lands/lumen_moss_patch"), aw("lands/lumen_trees")],
                        "minecraft:patch_grass_forest": [mc("patch_grass_forest"), aw("lands/lumen_caps"), aw("lands/lumen_fireflies")]},
                  vegetal={"drop": (mc("forest_flowers"),)})
    lumen["effects"].pop("grass_color_modifier", None)
    return {"elderwood": elder, "lumen_forest": lumen}


def injector(name, targets):
    return {"type": "lithostitched:replace_partially", "dimension": mc("overworld"), "targets": targets,
            "replacement": aw(name), "parameters": {"awaken:lands/awakening": {"min_inclusive": 0.0}}}


# Hero trees: one candidate per spacing x spacing chunks, kept where the site search finds its biome and level ground
# within four chunks of it (dev.awaken.terrain.hero.HeroStructure); inside their biome that is about one per 300-500
# blocks. The biomes come from the biome table (tools/terrain/biomes.py, awaken:has_structure/<tree>).
HEROES = {"elder_colossus": (26, 10, 1830266171), "lumen_spire": (22, 8, 774409123)}


def structure(kind, field, name, spacing, separation, salt):
    """A one-piece Awaken structure of type `kind` (`field`: which one) and its wide random spread; its biomes are the
    biome table's awaken:has_structure/<name>."""
    write(f"{PACK}/awaken/worldgen/structure/{name}.json", {
        "type": aw(kind), field: name, "biomes": f"#awaken:has_structure/{name}", "step": "surface_structures",
        "spawn_overrides": {}, "terrain_adaptation": "none"})
    write(f"{PACK}/awaken/worldgen/structure_set/{name}.json", {
        "structures": [{"structure": aw(name), "weight": 1}],
        "placement": {"type": mc("random_spread"), "spacing": spacing, "separation": separation, "salt": salt}})


def heroes():
    for name, (spacing, separation, salt) in HEROES.items():
        structure("hero_tree", "tree", name, spacing, separation, salt)


def pack():
    heroes()
    for name, data in features().items():
        write(f"{PACK}/awaken/worldgen/feature/{name}.json", data)
    for name, data in placed().items():
        write(f"{PACK}/awaken/worldgen/placed_feature/{name}.json", data)
    for name, data in biomes().items():
        write(f"{PACK}/awaken/worldgen/biome/{name}.json", data)
    write(f"{PACK}/awaken/lithostitched/biome_injector/lands/elderwood.json",
          injector("elderwood", [mc("forest"), mc("birch_forest"), mc("old_growth_birch_forest"), mc("flower_forest")]))
    write(f"{PACK}/awaken/lithostitched/biome_injector/lands/lumen_forest.json", injector("lumen_forest", [mc("dark_forest")]))
    for pair in PAIRS:
        pair.pack(sys.modules[__name__])


if __name__ == "__main__":
    block_assets()
    block_data()
    pack()
    print(f"{len(WRITTEN)} files")
