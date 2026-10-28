"""The Soft Land's ground palettes (SPEC §31, P2): what lies on the ground in each kind of land, as one table, written out as the
worldgen of the data pack `datapacks/awakened_ground` (own features, placed features and biome modifiers). Vanilla features
are reused by name (fallen trees, moss patches, bush patches); our own are the boulders, pebbles and twigs.

A palette entry is one placed feature in a group of biomes: how often (a chunk in `rarity`), how many in a drift (`count`
within `spread` blocks: a cluster, not an even scatter) and its `wild` weights (the Wild Lands, SPEC §41.3: how many times
it is tried in Tamed, Pasture, Wild and Deep wild land, through `awaken:wildness`; ALL is once everywhere).

The Wild Lands' own worldgen (SPEC §41.3) is written here too, under wild/: per group of biomes (a biome tag
#awaken:wild/<group>), the grass by wildness (`awaken:wild_ground`, once a chunk) and the woody things (bushes, hedgerows,
thickets, old giants, snags, deadfall), each placed by wildness.
Run: python3 tools/terrain/ground_palette.py (rewrites the pack's ground/ and wild/ files; a second run changes nothing).
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
PACK = f"{ROOT}/src/main/resources/datapacks/awakened_ground/data/awaken"
ALL = (1, 1, 1, 1)


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


def wildness(weights):
    return [] if tuple(weights) == ALL else [{"type": "awaken:wildness", "weights": list(weights)}]


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


# wildness weights (Tamed, Pasture, Wild, Deep wild): fallen trees and moss are the wild's, the game's low bushes the pasture's
FALLEN = (0.2, 0.6, 1.4, 2.2)
MOSS = (0.0, 0.5, 1.2, 1.8)
BUSHES = (0.3, 1.0, 1.3, 1.0)

# the palettes: group -> (biomes, [(name, placed feature, wild weights)])
# Lands biomes are listed by name where they should (not) join; their archetype tags already carry them for most things.
G = {}
G["forest"] = (["minecraft:forest", "minecraft:flower_forest", "minecraft:dark_forest"], [
    ("fallen_oak", single("minecraft:fallen_oak_tree", 6, "minecraft:oak_sapling"), FALLEN),
    ("boulder_forest", single("awaken:ground/boulder_forest", 3), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 1, 10, 6), ALL),
    ("twigs_oak", drift("awaken:ground/twigs_oak", 1, 6, 6), ALL),
    ("moss_patch", single("minecraft:moss_patch", 9), MOSS),
])
G["birch"] = (["minecraft:birch_forest", "minecraft:old_growth_birch_forest", "awaken:elderwood"], [
    ("fallen_birch", single("minecraft:fallen_birch_tree", 6, "minecraft:birch_sapling"), FALLEN),
    ("boulder_forest", single("awaken:ground/boulder_forest", 3), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 1, 10, 6), ALL),
    ("twigs_birch", drift("awaken:ground/twigs_birch", 1, 6, 6), ALL),
])
G["lumen"] = (["awaken:lumen_forest"], [
    ("boulder_forest", single("awaken:ground/boulder_forest", 3), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 1, 8, 6), ALL),
])
G["taiga"] = (["minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:snowy_taiga"], [
    ("fallen_spruce", single("minecraft:fallen_spruce_tree", 6, "minecraft:spruce_sapling"), FALLEN),
    ("boulder_forest", single("awaken:ground/boulder_forest", 3), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 1, 10, 6), ALL),
    ("twigs_spruce", drift("awaken:ground/twigs_spruce", 1, 6, 6), ALL),
    ("moss_patch", single("minecraft:moss_patch", 10), MOSS),
])
G["highland"] = (["#minecraft:is_hill", "#minecraft:is_mountain"], [
    ("boulder_highland", single("awaken:ground/boulder_highland", 3), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 1, 14, 7), ALL),
])
G["plains"] = (["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:cherry_grove"], [
    ("boulder_plains", single("awaken:ground/boulder_plains", 10), ALL),
    ("pebbles", drift("awaken:ground/pebbles", 3, 8, 6), ALL),
    ("bush", drift("minecraft:bush", 4, 14, 5), BUSHES),
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
    ("moss_patch", single("minecraft:moss_patch", 8), MOSS),
])


def build():
    for name, f in FEATURES.items():
        write(f"{PACK}/worldgen/feature/ground/{name}.json", f)
    count = 0
    for group, (biomes, entries) in G.items():
        names = []
        for name, placed, weights in entries:
            pid = f"{group}_{name}"
            doc = dict(placed)
            doc["placement"] = wildness(weights) + doc["placement"]
            write(f"{PACK}/worldgen/placed_feature/ground/{pid}.json", doc)
            names.append(f"awaken:ground/{pid}")
            count += 1
        # a biome holder set is one tag, one id or a list of ids: tags and the id list each get a modifier
        ids = [b for b in biomes if not b.startswith("#")]
        sets = ([ids if len(ids) > 1 else ids[0]] if ids else []) + [b for b in biomes if b.startswith("#")]
        for k, bs in enumerate(sets):
            write(f"{PACK}/neoforge/biome_modifier/ground/floor_{group}{'' if k == 0 else '_' + str(k)}.json", {
                "type": "neoforge:add_features", "biomes": bs, "features": names, "step": "vegetal_decoration"})
    # the palette as data for the tests
    table = {g: {"biomes": b, "entries": [{"name": n, "wild": list(r)} for n, _, r in e]} for g, (b, e) in G.items()}
    write(f"{PACK}/../../palette.json", table)
    print("ground_palette:", len(G), "groups,", count, "placed features,", len(FEATURES), "own features")
    wild()


# ---- the Wild Lands (SPEC §41.3) ----------------------------------------------------------------------------------------
def opt(b):
    """An Awakened Lands biome: in its own pack, so a tag names it as optional."""
    return {"id": b, "required": False} if b.startswith("awaken:") else b


WILD_BIOMES = {
    "meadow": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "awaken:glass_steppe"],
    "forest": ["minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest",
               "minecraft:dark_forest", "awaken:elderwood", "awaken:lumen_forest"],
    "taiga": ["minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga"],
    "savanna": ["minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna"],
    "marsh": ["minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:river", "awaken:mirror_marsh"],
    "heath": ["minecraft:windswept_hills", "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest", "awaken:shattered_highlands"],
    "cold": ["minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:grove", "awaken:frost_spires"],
    "dry": ["minecraft:desert", "minecraft:badlands", "minecraft:wooded_badlands", "minecraft:eroded_badlands"],
}


def role(blocks, rates, swathe=0, water=False):
    r = {"blocks": blocks if isinstance(blocks, list) else [blocks], "rates": list(rates)}
    if swathe:
        r["swathe"] = swathe
    if water:
        r["water"] = True
    return r


A = "awaken:"
# plants a chunk added to the game's own, by band (Tamed, Pasture, Wild, Deep wild); the tallest role first. The game already
# grows about 22 short grass, 2 tall and 8 flowers a plains chunk, so the totals land on the plan's table.
WILD_GROUND = {
    "meadow": {"mow": True, "sward": 0.5, "roles": [
        role(A + "wild_rye", (0, 0, 2, 25), 11),
        role(A + "long_grass", (0, 3, 23, 30), 14),
        role(A + "cow_parsley", (0, 3, 1.5, 0.5)),
        role(A + "thistle", (0, 1.5, 1, 0.5)),
        role(A + "foxglove", (0, 0.3, 0.4, 0.2)),
        role(A + "clover", (6, 4, 0.5, 0)),
        role(A + "meadow_grass", (0, 10, 18, 20))]},
    "forest": {"mow": True, "roles": [
        role(A + "bracken", (0, 4, 18, 32), 12),
        role(A + "bramble", (0, 0.5, 2, 4), 8),
        role(A + "foxglove", (0, 0.6, 1, 0.8)),
        role(A + "cow_parsley", (0, 0.5, 0.3, 0)),
        role("minecraft:fern", (0, 3, 8, 10)),
        role(A + "meadow_grass", (0, 4, 6, 6))]},
    "taiga": {"mow": True, "roles": [
        role(A + "bracken", (0, 3, 12, 22), 12),
        role(A + "heather", (0, 2, 5, 8), 10),
        role("minecraft:fern", (0, 4, 8, 10))]},
    "savanna": {"mow": True, "sward": 0.3, "roles": [
        role(A + "wild_rye", (0, 0, 3, 20), 13),
        role(A + "long_grass", (0, 4, 18, 24), 14),
        role(A + "thistle", (0, 1, 1, 0.5)),
        role(A + "meadow_grass", (0, 6, 10, 12))]},
    "marsh": {"mow": True, "roles": [
        role(A + "reeds", (3, 6, 10, 14), water=True),
        role(A + "long_grass", (0, 2, 8, 14), 12),
        role(A + "meadow_grass", (0, 6, 10, 12))]},
    "heath": {"roles": [
        role(A + "bracken", (0, 2, 8, 14), 12),
        role(A + "gorse", (0, 2, 5, 8), 9),
        role(A + "heather", (2, 6, 10, 12), 10),
        role(A + "meadow_grass", (0, 6, 10, 12))]},
    "cold": {"roles": [role(A + "frost_grass", (3, 8, 12, 14))]},
    "dry": {"roles": [role(A + "dry_tussock", (0, 1, 2, 3))]},
}


def placed_by(weights, survive=None):
    p = [{"type": "awaken:wildness", "weights": list(weights)}, {"type": "minecraft:in_square"},
         {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"}, {"type": "minecraft:biome"}]
    if survive:
        p.append({"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive", "state": survive}})
    return p


def wood(w):
    return {"log": f"minecraft:{w}_log", "leaves": f"minecraft:{w}_leaves", "bare": f"minecraft:stripped_{w}_log"}


OAK, SPRUCE, ACACIA = wood("oak"), wood("spruce"), wood("acacia")
WILD_FEATURES = {
    "bush_oak": {"type": "awaken:wild_bush", "leaves": OAK["leaves"], "log": OAK["log"]},
    "bush_spruce": {"type": "awaken:wild_bush", "leaves": SPRUCE["leaves"], "log": SPRUCE["log"], "max_radius": 2.0},
    "bush_acacia": {"type": "awaken:wild_bush", "leaves": ACACIA["leaves"], "log": ACACIA["log"], "max_radius": 2.0},
    "hedgerow": {"type": "awaken:hedgerow", "leaves": A + "hawthorn_leaves", "foot": A + "cow_parsley"},
    "thicket": {"type": "awaken:thicket", "bush_leaves": OAK["leaves"], "bush_log": OAK["log"]},
    "giant_oak": {"type": "awaken:old_giant", "log": OAK["log"], "leaves": OAK["leaves"], "min_height": 16, "max_height": 24},
    "snag_oak": {"type": "awaken:snag", "log": OAK["log"], "bare": OAK["bare"]},
    "snag_spruce": {"type": "awaken:snag", "log": SPRUCE["log"], "bare": SPRUCE["bare"]},
    "deadfall_oak": {"type": "awaken:deadfall", "log": OAK["log"]},
    "deadfall_spruce": {"type": "awaken:deadfall", "log": SPRUCE["log"]},
}
# per group: (name, feature, wildness weights a chunk, survive state)
WILD_PLACED = {
    "meadow": [("bush", "awaken:wild/bush_oak", (0, 0.5, 2, 4)), ("hedgerow", "awaken:wild/hedgerow", (0.35, 0.6, 0.05, 0)),
               ("thicket", "awaken:wild/thicket", (0, 0.15, 0.4, 0.8)), ("giant", "awaken:wild/giant_oak", (0, 0, 0.05, 0.17)),
               ("deadfall", "awaken:wild/deadfall_oak", (0, 0.05, 0.2, 0.4)), ("snag", "awaken:wild/snag_oak", (0, 0, 0.08, 0.2))],
    "forest": [("bush", "awaken:wild/bush_oak", (0, 0.4, 1.5, 3)), ("thicket", "awaken:wild/thicket", (0, 0.2, 0.6, 1.2)),
               ("giant", "awaken:wild/giant_oak", (0, 0, 0.06, 0.17)), ("snag", "awaken:wild/snag_oak", (0, 0.1, 0.4, 0.9)),
               ("deadfall", "awaken:wild/deadfall_oak", (0, 0.2, 0.6, 1.2))],
    "taiga": [("bush", "awaken:wild/bush_spruce", (0, 0.3, 1, 2)),
              ("giant", "minecraft:mega_spruce", (0, 0, 0.06, 0.17), "minecraft:spruce_sapling"),
              ("snag", "awaken:wild/snag_spruce", (0, 0.1, 0.4, 0.9)), ("deadfall", "awaken:wild/deadfall_spruce", (0, 0.2, 0.6, 1.1))],
    "savanna": [("bush", "awaken:wild/bush_acacia", (0, 0.4, 1.2, 2)),
                ("giant", "minecraft:acacia", (0, 0.1, 0.3, 0.5), "minecraft:acacia_sapling")],
    "marsh": [("bush", "awaken:wild/bush_oak", (0, 0.3, 0.8, 1.5)), ("snag", "awaken:wild/snag_oak", (0, 0.1, 0.3, 0.6)),
              ("deadfall", "awaken:wild/deadfall_oak", (0, 0.1, 0.3, 0.5))],
    "heath": [("bush", "awaken:wild/bush_oak", (0, 0.3, 0.8, 1.5)), ("snag", "awaken:wild/snag_oak", (0, 0, 0.1, 0.2))],
    "cold": [("snag", "awaken:wild/snag_spruce", (0, 0.05, 0.2, 0.4))],
    "dry": [],
}


def wild():
    count = 0
    for name, f in WILD_FEATURES.items():
        write(f"{PACK}/worldgen/feature/wild/{name}.json", f)
    for group, biomes in WILD_BIOMES.items():
        write(f"{PACK}/tags/worldgen/biome/wild/{group}.json", {"values": [opt(b) for b in biomes]})
        g = WILD_GROUND[group]
        feature = {"type": "awaken:wild_ground", "biomes": f"awaken:wild/{group}", "roles": g["roles"]}
        if g.get("mow"):
            feature["mow"] = True
        if g.get("sward"):
            feature["sward"] = g["sward"]
        write(f"{PACK}/worldgen/feature/wild/ground_{group}.json", feature)
        write(f"{PACK}/worldgen/placed_feature/wild/{group}_ground.json", {"feature": f"awaken:wild/ground_{group}", "placement": []})
        names = [f"awaken:wild/{group}_ground"]
        for entry in WILD_PLACED[group]:
            name, feat, weights = entry[:3]
            write(f"{PACK}/worldgen/placed_feature/wild/{group}_{name}.json",
                  {"feature": feat, "placement": placed_by(weights, entry[3] if len(entry) > 3 else None)})
            names.append(f"awaken:wild/{group}_{name}")
        count += len(names)
        write(f"{PACK}/neoforge/biome_modifier/wild/{group}.json", {
            "type": "neoforge:add_features", "biomes": f"#awaken:wild/{group}", "features": names, "step": "vegetal_decoration"})
    table = {g: {"biomes": WILD_BIOMES[g], "placed": ["ground"] + [e[0] for e in WILD_PLACED[g]]} for g in WILD_BIOMES}
    write(f"{PACK}/../../wild.json", table)
    print("wild:", len(WILD_BIOMES), "groups,", count, "placed features,", len(WILD_FEATURES) + len(WILD_BIOMES), "own features")


if __name__ == "__main__":
    build()
