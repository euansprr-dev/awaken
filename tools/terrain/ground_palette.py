"""The Soft Land's ground palettes (SPEC §31, P2): what lies on the ground in each kind of land, as one table, written out as the
worldgen of the data pack `datapacks/awakened_ground` (own features, placed features and biome modifiers). Vanilla features
are reused by name (fallen trees, moss patches, bush patches); our own are the boulders, pebbles and twigs.

A palette entry is one placed feature in a group of biomes: how often (a chunk in `rarity`), how many in a drift (`count`
within `spread` blocks: a cluster, not an even scatter) and, for P5, the `ranks` it belongs to (the regions plan's E..S ranks;
all of them for now). Run: python3 tools/terrain/ground_palette.py (rewrites the pack's ground/ files; a second run changes nothing).
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
PACK = f"{ROOT}/src/main/resources/datapacks/awakened_ground/data/awaken"
ALL = "EDCBAS"


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def weighted(entries):
    return {"type": "minecraft:weighted", "entries": [{"data": d, "weight": w} for d, w in entries]}


def pebble_states():
    out = []
    for kind, kw in enumerate((3, 2, 1, 1)):
        for stones, sw in ((1, 4), (2, 3), (3, 1)):
            out.append(({"id": "awaken:pebbles", "properties": {"kind": str(kind), "stones": str(stones)}}, kw * sw))
    return out


def twig_states(wood):
    return [({"id": "awaken:twigs", "properties": {"wood": str(wood), "facing": f}}, 1) for f in ("north", "east", "south", "west")]


# own configured features
FEATURES = {
    "pebbles": {"type": "minecraft:simple_block", "to_place": weighted(pebble_states())},
    "twigs_oak": {"type": "minecraft:simple_block", "to_place": weighted(twig_states(0))},
    "twigs_birch": {"type": "minecraft:simple_block", "to_place": weighted(twig_states(1))},
    "twigs_spruce": {"type": "minecraft:simple_block", "to_place": weighted(twig_states(2))},
    "boulder_forest": {"type": "awaken:boulder", "palette": "forest", "min_radius": 1, "max_radius": 3},
    "boulder_highland": {"type": "awaken:boulder", "palette": "highland", "min_radius": 2, "max_radius": 5},
    "boulder_plains": {"type": "awaken:boulder", "palette": "plains", "min_radius": 1, "max_radius": 2},
    "boulder_desert": {"type": "awaken:boulder", "palette": "desert", "min_radius": 1, "max_radius": 3},
    "boulder_badlands": {"type": "awaken:boulder", "palette": "badlands", "min_radius": 1, "max_radius": 3},
}

AIR_AND_FLOOR = {"type": "minecraft:all_of", "predicates": [
    {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
    {"type": "minecraft:has_sturdy_face", "offset": [0, -1, 0], "direction": "up"}]}


def trapezoid(r, ry=2):
    return {"type": "minecraft:offset", "x": {"type": "minecraft:trapezoid", "min": -r, "max": r, "plateau": 0},
            "y": {"type": "minecraft:trapezoid", "min": -ry, "max": ry, "plateau": 0},
            "z": {"type": "minecraft:trapezoid", "min": -r, "max": r, "plateau": 0}}


def drift(feature, rarity, count, spread):
    """A cluster: a chunk in `rarity` gets `count` of them within `spread` blocks of a spot (never a flat scatter)."""
    p = []
    if rarity > 1:
        p.append({"type": "minecraft:rarity_filter", "chance": rarity})
    p += [{"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"},
          {"type": "minecraft:count", "count": count}, trapezoid(spread), {"type": "minecraft:block_predicate_filter", "predicate": AIR_AND_FLOOR}]
    return {"feature": feature, "placement": p}


def single(feature, rarity, survive=None):
    """One of them where a chunk in `rarity` picks (a boulder, a fallen tree)."""
    p = []
    if rarity > 1:
        p.append({"type": "minecraft:rarity_filter", "chance": rarity})
    p += [{"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}]
    if survive:
        p.append({"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive", "state": survive}})
    return {"feature": feature, "placement": p}


# the palettes: group -> (biomes, [(name, placed feature, ranks)])
# Lands biomes are listed by name where they should (not) join; their archetype tags already carry them for most things.
G = {}
G["forest"] = (["minecraft:forest", "minecraft:flower_forest", "minecraft:dark_forest"], [
    ("fallen_oak", single("minecraft:fallen_oak_tree", 6, "minecraft:oak_sapling"), ALL),
    ("boulder_forest", single("awaken:ground/boulder_forest", 3), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 1, 10, 6), ALL),
    ("twigs_oak", drift("awaken:ground/twigs_oak", 1, 6, 6), ALL),
    ("moss_patch", single("minecraft:moss_patch", 9), "EDC"),
])
G["birch"] = (["minecraft:birch_forest", "minecraft:old_growth_birch_forest", "awaken:elderwood"], [
    ("fallen_birch", single("minecraft:fallen_birch_tree", 6, "minecraft:birch_sapling"), ALL),
    ("boulder_forest", single("awaken:ground/boulder_forest", 3), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 1, 10, 6), ALL),
    ("twigs_birch", drift("awaken:ground/twigs_birch", 1, 6, 6), ALL),
])
G["lumen"] = (["awaken:lumen_forest"], [
    ("boulder_forest", single("awaken:ground/boulder_forest", 3), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 1, 8, 6), ALL),
])
G["taiga"] = (["minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:snowy_taiga"], [
    ("fallen_spruce", single("minecraft:fallen_spruce_tree", 6, "minecraft:spruce_sapling"), ALL),
    ("boulder_forest", single("awaken:ground/boulder_forest", 3), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 1, 10, 6), ALL),
    ("twigs_spruce", drift("awaken:ground/twigs_spruce", 1, 6, 6), ALL),
    ("moss_patch", single("minecraft:moss_patch", 10), "EDC"),
])
G["highland"] = (["#minecraft:is_hill", "#minecraft:is_mountain"], [
    ("boulder_highland", single("awaken:ground/boulder_highland", 3), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 1, 14, 7), ALL),
])
G["plains"] = (["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:cherry_grove"], [
    ("boulder_plains", single("awaken:ground/boulder_plains", 10), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 3, 8, 6), ALL),
    ("bush", drift("minecraft:bush", 4, 14, 5), "EDC"),
])
G["savanna"] = (["#minecraft:is_savanna"], [
    ("boulder_plains", single("awaken:ground/boulder_plains", 8), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 2, 8, 6), ALL),
])
G["desert"] = (["minecraft:desert"], [
    ("boulder_desert", single("awaken:ground/boulder_desert", 6), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 3, 8, 6), ALL),
])
G["badlands"] = (["#minecraft:is_badlands"], [
    ("boulder_badlands", single("awaken:ground/boulder_badlands", 4), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 2, 8, 6), ALL),
])
G["swamp"] = (["minecraft:swamp", "minecraft:mangrove_swamp"], [
    ("twigs_oak", drift("awaken:ground/twigs_oak", 1, 8, 6), ALL),
    ("moss_patch", single("minecraft:moss_patch", 8), "EDC"),
])


def build():
    for name, f in FEATURES.items():
        write(f"{PACK}/worldgen/feature/ground/{name}.json", f)
    count = 0
    for group, (biomes, entries) in G.items():
        names = []
        for name, placed, ranks in entries:
            pid = f"{group}_{name}"
            doc = dict(placed)
            write(f"{PACK}/worldgen/placed_feature/ground/{pid}.json", doc)
            names.append(f"awaken:ground/{pid}")
            count += 1
        # a biome holder set is one tag, one id or a list of ids: tags and the id list each get a modifier
        ids = [b for b in biomes if not b.startswith("#")]
        sets = ([ids if len(ids) > 1 else ids[0]] if ids else []) + [b for b in biomes if b.startswith("#")]
        for k, bs in enumerate(sets):
            write(f"{PACK}/neoforge/biome_modifier/ground/floor_{group}{'' if k == 0 else '_' + str(k)}.json", {
                "type": "neoforge:add_features", "biomes": bs, "features": names, "step": "vegetal_decoration"})
    # the palette as data for the tests and for P5 (ranks)
    table = {g: {"biomes": b, "entries": [{"name": n, "ranks": r} for n, _, r in e]} for g, (b, e) in G.items()}
    write(f"{PACK}/../../palette.json", table)
    print("ground_palette:", len(G), "groups,", count, "placed features,", len(FEATURES), "own features")


if __name__ == "__main__":
    build()
