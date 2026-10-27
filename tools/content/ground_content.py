"""Generates the Soft Land's soft slabs (SPEC §31, P1): blockstates, models, client item definitions, loot tables, tags and
lang for the slabs of natural ground that vanilla has no slab for. Textures are vanilla's, referenced by name; nothing of
Mojang's is copied. Run: python3 tools/content/ground_content.py (rewrites the files; a second run changes nothing).

Soft slabs: turf_slab (grass), dirt_slab, coarse_dirt_slab, podzol_slab, mud_slab, gravel_slab, sand_slab, red_sand_slab.
The turf slab carries a `tuft` property (0 none, 1 short grass, 2 tall grass, 3 a fern, 4 a flower, 5 a bush): a plant
drawn inside the slab's own block from its top up, so foot columns are not bald and nothing floats above a half block.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = f"{RES}/assets/awaken"
DATA = f"{RES}/data/awaken"
MERGE = os.path.join(ROOT, "src/main/merge/ground")


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2, sort_keys=True)
        f.write("\n")


def aw(n):
    return "awaken:" + n


def mc(n):
    return "minecraft:" + n


# name: (display name, bottom texture, top texture, side texture, vanilla model of the double slab, tool)
PLAIN = {
    "dirt_slab": ("Dirt Slab", "dirt", "dirt", "dirt", "block/dirt"),
    "coarse_dirt_slab": ("Coarse Dirt Slab", "coarse_dirt", "coarse_dirt", "coarse_dirt", "block/coarse_dirt"),
    "podzol_slab": ("Podzol Slab", "dirt", "podzol_top", "podzol_side", "block/podzol"),
    "mud_slab": ("Mud Slab", "mud", "mud", "mud", "block/mud"),
    "gravel_slab": ("Gravel Slab", "gravel", "gravel", "gravel", "block/gravel"),
    "sand_slab": ("Sand Slab", "sand", "sand", "sand", "block/sand"),
    "red_sand_slab": ("Red Sand Slab", "red_sand", "red_sand", "red_sand", "block/red_sand"),
}
NAMES = {k: v[0] for k, v in PLAIN.items()}
NAMES["turf_slab"] = "Grass Slab"

SIDES = ("north", "south", "west", "east")


def plain_models():
    for name, (_, bottom, top, side, double) in PLAIN.items():
        tex = {"bottom": mc("block/" + bottom), "top": mc("block/" + top), "side": mc("block/" + side)}
        write(f"{ASSETS}/models/block/{name}.json", {"parent": mc("block/slab"), "textures": tex})
        write(f"{ASSETS}/models/block/{name}_top.json", {"parent": mc("block/slab_top"), "textures": tex})
        write(f"{ASSETS}/blockstates/{name}.json", {"variants": {
            "type=bottom": {"model": aw("block/" + name)},
            "type=top": {"model": aw("block/" + name + "_top")},
            "type=double": {"model": mc(double)}}})
        write(f"{ASSETS}/items/{name}.json", {"model": {"type": mc("model"), "model": aw("block/" + name)}})


def slab_elements(y0, y1):
    """The grass slab's body between y0 and y1: the top half of the grass side (fringe and a little dirt) on every side."""
    body = {"down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
            "up": {"uv": [0, 0, 16, 16], "texture": "#top", "tintindex": 0}}
    over = {}
    for s in SIDES:
        body[s] = {"uv": [0, 0, 16, 8], "texture": "#side", "cullface": s}
        over[s] = {"uv": [0, 0, 16, 8], "texture": "#overlay", "tintindex": 0, "cullface": s}
    return [{"from": [0, y0, 0], "to": [16, y1, 16], "faces": body},
            {"from": [0, y0, 0], "to": [16, y1, 16], "faces": over}]


# the tufts: texture, top y in sixteenths (they start at the slab's top, y 8), tinted by the grass colour or not
TUFTS = {
    1: ("short_grass", 16, True),
    2: ("tall_grass_bottom", 22, True),
    3: ("fern", 18, True),
    4: ("dandelion", 18, False),
    5: ("bush", 17, True),
}


def tuft_elements(n):
    tex, top, tinted = TUFTS[n]
    face = {"uv": [0, 0, 16, 16], "texture": "#tuft"}
    if tinted:
        face["tintindex"] = 0
    rot = {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}
    return [
        {"from": [0.8, 8, 8], "to": [15.2, top, 8], "rotation": rot, "shade_direction_override": "up",
         "faces": {"north": dict(face), "south": dict(face)}},
        {"from": [8, 8, 0.8], "to": [8, top, 15.2], "rotation": rot, "shade_direction_override": "up",
         "faces": {"west": dict(face), "east": dict(face)}},
    ]


def turf_models():
    textures = {"particle": mc("block/dirt"), "bottom": mc("block/dirt"), "top": mc("block/grass_block_top"),
                "side": mc("block/grass_block_side"), "overlay": mc("block/grass_block_side_overlay")}
    write(f"{ASSETS}/models/block/turf_slab.json", {"parent": mc("block/block"), "textures": textures, "elements": slab_elements(0, 8)})
    write(f"{ASSETS}/models/block/turf_slab_top.json", {"parent": mc("block/block"), "textures": textures, "elements": slab_elements(8, 16)})
    states = {"type=top,tuft=0": {"model": aw("block/turf_slab_top")}}
    for tuft in range(6):
        if tuft == 0:
            model = aw("block/turf_slab")
        else:
            model = aw(f"block/turf_slab_tuft_{tuft}")
            tex = dict(textures)
            tex["tuft"] = mc("block/" + TUFTS[tuft][0])
            write(f"{ASSETS}/models/block/turf_slab_tuft_{tuft}.json", {"parent": mc("block/block"), "textures": tex,
                                                                        "ambientocclusion": False,
                                                                        "elements": slab_elements(0, 8) + tuft_elements(tuft)})
        states[f"type=bottom,tuft={tuft}"] = {"model": model}
        states[f"type=top,tuft={tuft}"] = {"model": aw("block/turf_slab_top")}
        states[f"type=double,tuft={tuft}"] = {"model": mc("block/grass_block")}
    write(f"{ASSETS}/blockstates/turf_slab.json", {"variants": states})
    write(f"{ASSETS}/items/turf_slab.json", {"model": {"type": mc("model"), "model": aw("block/turf_slab"),
                                                       "tints": [{"type": mc("grass"), "downfall": 1.0, "temperature": 0.5}]}})


def slab_loot(name, drop=None, silk=None):
    """Vanilla's slab table (two for a double slab); the grass slab drops a dirt slab unless the tool can silk-touch."""
    def item(n):
        return {"type": mc("item"), "name": aw(n), "modifier": [
            {"type": mc("set_count"), "condition": {"type": mc("match_block"), "blocks": aw(name), "state": {"type": "double"}}, "count": 2},
            {"type": mc("explosion_decay")}]}
    if silk:
        entry = {"type": mc("alternatives"), "children": [dict(item(name), condition=mc("tool/can_silk_touch")), item(drop)]}
    else:
        entry = item(name)
    return {"type": mc("block"), "pools": [{"rolls": 1, "entries": [entry]}], "random_sequence": aw("blocks/" + name)}


def loot():
    for name in NAMES:
        if name == "turf_slab":
            write(f"{DATA}/loot_table/blocks/{name}.json", slab_loot(name, drop="dirt_slab", silk=True))
        else:
            write(f"{DATA}/loot_table/blocks/{name}.json", slab_loot(name))


def tags_and_lang():
    every = [aw(n) for n in NAMES]
    blocks = {
        "minecraft:slabs": every,
        "minecraft:mineable/shovel": every,
        "awaken:ground/soft_slabs": every,
        # spawning on them: a vanilla slab's half-height top is not sturdy ground, ours is
        "minecraft:animals_spawnable_on": [aw("turf_slab")],
        "minecraft:rabbits_spawnable_on": [aw("turf_slab")],
        "minecraft:foxes_spawnable_on": [aw("turf_slab")],
        "minecraft:wolves_spawnable_on": [aw("turf_slab")],
    }
    items = {"minecraft:slabs": every}
    for tag, values in blocks.items():
        if tag == "awaken:ground/soft_slabs":
            continue                                    # a single owner: src/main/resources/data/awaken/tags/block/ground/soft_slabs.json
        ns, path = tag.split(":")
        write(f"{MERGE}/data/{ns}/tags/block/{path}.json", {"values": values})
    for tag, values in items.items():
        ns, path = tag.split(":")
        write(f"{MERGE}/data/{ns}/tags/item/{path}.json", {"values": values})
    write(f"{RES}/data/awaken/tags/block/ground/soft_slabs.json", {"values": every})
    write(f"{MERGE}/assets/awaken/lang/en_us.json", {f"block.awaken.{k}": v for k, v in NAMES.items()})


# ---- P2: pebbles and twigs -----------------------------------------------------------------------------------------------
PEBBLE_KINDS = ["stone", "andesite", "granite", "diorite"]
# the stones of a pebbles block, in sixteenths: (from, to, turn in degrees about its own centre)
PEBBLE_SETS = {
    1: [([5.5, 0, 5], [10.5, 2.5, 9.5], 0)],
    2: [([3, 0, 4], [7.5, 2, 8.5], 22.5), ([9, 0, 8], [12.5, 3, 12], -22.5)],
    3: [([2.5, 0, 3.5], [6.5, 2, 7.5], -22.5), ([8.5, 0, 2.5], [12, 2.5, 6.5], 22.5), ([6, 0, 9], [10.5, 3, 13], 0)],
}
TWIG_WOODS = ["oak_log", "birch_log", "spruce_log"]
TWIG_STICKS = [([1, 0, 6.5], [14, 1.2, 8.2], 0), ([3, 0, 8.8], [15, 1, 10], 22.5), ([0, 0, 4], [9, 1, 5.3], -22.5)]


def box(frm, to, turn, tex="#t"):
    faces = {f: {"texture": tex} for f in ("down", "up", "north", "south", "west", "east")}
    el = {"from": frm, "to": to, "faces": faces}
    if turn:
        el["rotation"] = {"origin": [(frm[0] + to[0]) / 2, 0, (frm[2] + to[2]) / 2], "axis": "y", "angle": turn}
    return el


def cover_assets():
    for k, tex in enumerate(PEBBLE_KINDS):
        for n, stones in PEBBLE_SETS.items():
            write(f"{ASSETS}/models/block/pebbles_{k}_{n}.json", {"textures": {"particle": mc("block/" + tex), "t": mc("block/" + tex)},
                                                                    "elements": [box(a, b, t) for a, b, t in stones]})
    variants = {}
    for k in range(4):
        for n in range(1, 4):
            variants[f"kind={k},stones={n}"] = [{"model": aw(f"block/pebbles_{k}_{n}"), "y": y} for y in (0, 90, 180, 270)]
    write(f"{ASSETS}/blockstates/pebbles.json", {"variants": variants})
    write(f"{ASSETS}/items/pebbles.json", {"model": {"type": mc("model"), "model": aw("block/pebbles_0_3")}})
    for w, tex in enumerate(TWIG_WOODS):
        write(f"{ASSETS}/models/block/twigs_{w}.json", {"textures": {"particle": mc("block/" + tex), "t": mc("block/" + tex)},
                                                          "elements": [box(a, b, t) for a, b, t in TWIG_STICKS]})
    variants = {}
    for w in range(3):
        for facing, y in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
            variants[f"wood={w},facing={facing}"] = {"model": aw(f"block/twigs_{w}"), "y": y}
    write(f"{ASSETS}/blockstates/twigs.json", {"variants": variants})
    write(f"{ASSETS}/items/twigs.json", {"model": {"type": mc("model"), "model": aw("block/twigs_0")}})
    write(f"{DATA}/loot_table/blocks/pebbles.json", {"type": mc("block"), "pools": [{"rolls": 1, "entries": [
        {"type": mc("item"), "name": aw("pebbles"), "modifier": [{"type": mc("explosion_decay")}]}]}], "random_sequence": aw("blocks/pebbles")})
    write(f"{DATA}/loot_table/blocks/twigs.json", {"type": mc("block"), "pools": [{"rolls": 1, "entries": [
        {"type": mc("item"), "name": mc("stick"), "modifier": [
            {"type": mc("set_count"), "count": {"type": mc("uniform"), "min": 1, "max": 2}}, {"type": mc("explosion_decay")}]}]}],
        "random_sequence": aw("blocks/twigs")})
    # tags for the footsteps work later: what the ground is made of underfoot
    tags = {
        "awaken:ground/litter": [mc("leaf_litter")],
        "awaken:ground/brush": [mc(n) for n in ("short_grass", "tall_grass", "fern", "large_fern", "bush")] + [aw("turf_slab")],
        "awaken:ground/gravel": [mc("gravel"), aw("gravel_slab"), aw("pebbles")],
        "awaken:ground/small_things": [aw("pebbles"), aw("twigs")],
        # the rock a boulder is made of (and its slabs): wild life perches on it as on bare stone (wildlife.client.Sites)
        "awaken:ground/perch_rock": [mc(n) for n in ("mossy_cobblestone", "cobblestone", "sandstone", "red_sandstone", "stone_slab", "andesite_slab",
                                                      "granite_slab", "diorite_slab", "tuff_slab", "cobblestone_slab", "mossy_cobblestone_slab",
                                                      "sandstone_slab", "red_sandstone_slab")],
    }
    for tag, values in tags.items():
        ns, path = tag.split(":")
        write(f"{MERGE}/data/{ns}/tags/block/{path}.json", {"values": values})
    lang = {"block.awaken.pebbles": "Pebbles", "block.awaken.twigs": "Twigs"}
    path = f"{MERGE}/assets/awaken/lang/en_us.json"
    existing = json.load(open(path)) if os.path.exists(path) else {}
    existing.update(lang)
    write(path, existing)


def main():
    plain_models()
    turf_models()
    loot()
    tags_and_lang()
    cover_assets()
    print("ground_content: wrote", len(NAMES), "soft slabs, pebbles and twigs")


if __name__ == "__main__":
    main()
