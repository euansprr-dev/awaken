"""Generates the wild's plants' resources (SPEC §41.2, the Wild Lands stage 1a): blockstates, models, client item definitions,
loot tables, tags and lang. The sprites come from tools/art/specs/wild_plants.py (run that first: this reads which sprites
have a tinted layer and which an overlay, and makes the models fit). Run: python3 tools/content/wild_plants_content.py
(rewrites the files; a second run changes nothing).

Models: a plant with both layers is a cross whose tinted blades (tint index 0, the grass colour) and untinted overlay lie on
the same planes, the way vanilla lays a grass block's side overlay; a plant with one layer is vanilla's (tinted_)cross. The
halves of a two-block plant pick their versions from one seed (vanilla's double plant seed), so the arrays line up.
"""
import json
import os

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = f"{RES}/assets/awaken"
DATA = f"{RES}/data/awaken"
TEX = f"{ASSETS}/textures/block/wild"
MERGE = os.path.join(ROOT, "src/main/merge/ground")
GRASS_TINT = {"type": "minecraft:grass", "temperature": 0.5, "downfall": 1.0}
LEAF_TINT = {"type": "minecraft:constant", "value": -12012264}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2, sort_keys=True)
        f.write("\n")


def aw(n):
    return "awaken:" + n


def mc(n):
    return "minecraft:" + n


def has_pixels(name):
    p = f"{TEX}/{name}.png"
    if not os.path.exists(p):
        return False
    return Image.open(p).convert("RGBA").getextrema()[3][1] > 0


def layers(sprite):
    """(tinted, overlay) for a sprite: which of its two layers have any pixel."""
    return has_pixels(sprite), has_pixels(sprite + "_overlay")


# ---- shared parents -----------------------------------------------------------------------------------------------------
def cross_elements(tex, tint, y1=16, v0=0):
    faces = lambda a, b: {f: dict({"uv": [0, v0, 16, 16], "texture": tex}, **({"tintindex": 0} if tint else {})) for f in (a, b)}
    return [
        {"from": [0.8, 0, 8], "to": [15.2, y1, 8], "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
         "shade_direction_override": "up", "faces": faces("north", "south")},
        {"from": [8, 0, 0.8], "to": [8, y1, 15.2], "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
         "shade_direction_override": "up", "faces": faces("west", "east")},
    ]


def flat_elements(tex, tint, y=0.25):
    f = lambda uv: dict({"uv": uv, "texture": tex}, **({"tintindex": 0} if tint else {}))
    return [{"from": [0, y, 0], "to": [16, y, 16], "faces": {"up": f([0, 0, 16, 16]), "down": f([0, 16, 16, 0])}}]


def parents():
    write(f"{ASSETS}/models/block/wild/cross_overlay.json", {
        "ambientocclusion": False, "render_type": mc("cutout"), "textures": {"particle": "#cross"},
        "elements": cross_elements("#cross", True) + cross_elements("#overlay", False)})
    write(f"{ASSETS}/models/block/wild/flat_overlay.json", {
        "ambientocclusion": False, "render_type": mc("cutout"), "textures": {"particle": "#flat"},
        "elements": flat_elements("#flat", True) + flat_elements("#overlay", False)})
    write(f"{ASSETS}/models/block/wild/flat.json", {
        "ambientocclusion": False, "render_type": mc("cutout"), "textures": {"particle": "#flat"},
        "elements": flat_elements("#flat", False)})
    # trampled grass standing half up again: the old flat layer and a low cross of short bent blades
    write(f"{ASSETS}/models/block/wild/trampled_rising.json", {
        "ambientocclusion": False, "render_type": mc("cutout"), "textures": {"particle": "#flat"},
        "elements": flat_elements("#flat", True) + cross_elements("#rise", True, y1=6, v0=10)})
    # hawthorn leaves: a leaf cube tinted with the foliage colour, the blossom laid on the same faces
    cube = lambda tex, tint: [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        d: dict({"uv": [0, 0, 16, 16], "texture": tex, "cullface": d}, **({"tintindex": 0} if tint else {}))
        for d in ("down", "up", "north", "south", "west", "east")}}]
    write(f"{ASSETS}/models/block/wild/leaves_overlay.json", {
        "parent": mc("block/block"), "render_type": mc("cutout_mipped"), "textures": {"particle": "#all"},
        "elements": cube("#all", True) + cube("#overlay", False)})
    # wild sward: the grass block, its top tinted, thatch laid over the top
    write(f"{ASSETS}/models/block/wild/sward.json", {
        "parent": mc("block/block"), "render_type": mc("cutout_mipped"),
        "textures": {"particle": mc("block/dirt"), "bottom": mc("block/dirt"), "side": mc("block/grass_block_side"),
                     "overlay": mc("block/grass_block_side_overlay")},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up", "tintindex": 0},
                **{d: {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": d} for d in ("north", "south", "west", "east")}}},
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                "up": {"uv": [0, 0, 16, 16], "texture": "#thatch", "cullface": "up"},
                **{d: {"uv": [0, 0, 16, 16], "texture": "#overlay", "tintindex": 0, "cullface": d} for d in ("north", "south", "west", "east")}}}]})
    # a shelf fungus on the north face of its block's south edge (turned by the blockstate)
    write(f"{ASSETS}/models/block/wild/bracket.json", {
        "ambientocclusion": False, "render_type": mc("cutout"), "textures": {"particle": "#f"},
        "elements": [{"from": [0, 0, 15.6], "to": [16, 16, 15.6], "faces": {
            "north": {"uv": [0, 0, 16, 16], "texture": "#f"}, "south": {"uv": [16, 0, 0, 16], "texture": "#f"}}},
            {"from": [1, 9, 12], "to": [9, 10, 15.6], "faces": {
                "up": {"uv": [2, 3, 9, 6], "texture": "#f"}, "down": {"uv": [2, 4, 9, 6], "texture": "#f"},
                "north": {"uv": [2, 4, 9, 5], "texture": "#f"}}}]})


def sprite_model(sprite):
    """The model for one sprite as a cross (writes it; returns its id)."""
    t, u = layers(sprite)
    if t and u:
        doc = {"parent": aw("block/wild/cross_overlay"), "textures": {"cross": aw(f"block/wild/{sprite}"), "overlay": aw(f"block/wild/{sprite}_overlay")}}
    elif t:
        doc = {"parent": mc("block/tinted_cross"), "render_type": mc("cutout"), "textures": {"cross": aw(f"block/wild/{sprite}")}}
    else:
        doc = {"parent": mc("block/cross"), "render_type": mc("cutout"), "textures": {"cross": aw(f"block/wild/{sprite}_overlay")}}
    write(f"{ASSETS}/models/block/wild/{sprite}.json", doc)
    return aw(f"block/wild/{sprite}")


def variants(sprites):
    return [{"model": sprite_model(s)} for s in sprites]


def item_generated(name, sprite):
    """A flat item from a sprite's layers; tinted as the game's grass items are where it has a tinted layer."""
    t, u = layers(sprite)
    tex = {}
    if t:
        tex["layer0"] = aw(f"block/wild/{sprite}")
    if u:
        tex["layer%d" % len(tex)] = aw(f"block/wild/{sprite}_overlay")
    write(f"{ASSETS}/models/item/{name}.json", {"parent": mc("item/generated"), "textures": tex})
    model = {"type": mc("model"), "model": aw(f"item/{name}")}
    if t:
        model["tints"] = [GRASS_TINT]
    write(f"{ASSETS}/items/{name}.json", {"model": model})


# ---- the plants ---------------------------------------------------------------------------------------------------------
def single(name, sprites, item_sprite=None):
    write(f"{ASSETS}/blockstates/{name}.json", {"variants": {"": variants(sprites)}})
    item_generated(name, item_sprite or sprites[0])


def double(name, bottoms, tops_by_look, item_sprite):
    """A two-block plant: lower and upper sprites per look (0..2); looks it lacks fall back to look 0."""
    v = {}
    for look in range(3):
        tops = tops_by_look.get(look, tops_by_look[0])
        lows = bottoms.get(look, bottoms[0]) if isinstance(bottoms, dict) else bottoms
        v[f"half=lower,look={look}"] = variants(lows)
        v[f"half=upper,look={look}"] = variants(tops)
    write(f"{ASSETS}/blockstates/{name}.json", {"variants": v})
    if item_sprite:
        item_generated(name, item_sprite)


def plants():
    single("meadow_grass", [f"meadow_grass_{i}" for i in range(4)])
    # meadow grass carries its lawn's memory in its state; every state looks the same
    v = variants([f"meadow_grass_{i}" for i in range(4)])
    write(f"{ASSETS}/blockstates/meadow_grass.json", {"variants": {"": v}})
    single("frost_grass", [f"frost_grass_{i}" for i in range(3)])
    looks = ("green", "seeding", "tawny")
    double("long_grass", [f"long_grass_bottom_{i}" for i in range(3)],
           {k: [f"long_grass_top_{look}_{i}" for i in range(3)] for k, look in enumerate(looks)}, "long_grass_top_green_0")
    # wild rye: the foot (its item) and the two blocks above it
    write(f"{ASSETS}/blockstates/wild_rye.json", {"variants": {"": variants([f"wild_rye_stems_{i}" for i in range(2)])}})
    item_generated("wild_rye", "wild_rye_top_gold_0")
    double("wild_rye_top", [f"wild_rye_middle_{i}" for i in range(2)],
           {0: [f"wild_rye_top_gold_{i}" for i in range(3)], 1: [f"wild_rye_top_glass_{i}" for i in range(3)]}, None)
    double("cow_parsley", [f"cow_parsley_bottom_{i}" for i in range(3)], {0: [f"cow_parsley_top_{i}" for i in range(3)]}, "cow_parsley_top_0")
    double("foxglove", [f"foxglove_bottom_{i}" for i in range(3)], {0: [f"foxglove_top_{i}" for i in range(3)]}, "foxglove_top_0")
    double("bracken", {0: [f"bracken_bottom_{i}" for i in range(3)], 1: [f"bracken_bottom_turned_{i}" for i in range(3)]},
           {0: [f"bracken_top_{i}" for i in range(3)], 1: [f"bracken_top_turned_{i}" for i in range(3)]}, "bracken_top_0")
    double("reeds", {0: [f"reeds_bottom_{i}" for i in range(3)], 1: [f"reeds_bottom_pale_{i}" for i in range(3)]},
           {0: [f"reeds_top_{i}" for i in range(3)], 1: [f"reeds_top_pale_{i}" for i in range(3)]}, "reeds_top_0")
    for name in ("thistle", "heather", "gorse", "dry_tussock"):
        single(name, [f"{name}_{i}" for i in range(3)])
    write(f"{ASSETS}/blockstates/bramble.json", {"variants": {
        f"fruit={k}": variants([f"bramble_{fruit}_{i}" for i in range(3)]) for k, fruit in enumerate(("none", "green", "ripe"))}})
    item_generated("bramble", "bramble_ripe_0")


def trampled():
    v = {}
    for stage in range(3):
        kind = "fresh" if stage == 0 else "old"
        models = []
        for i in range(2):
            name = f"trampled_{kind}_{i}" if stage < 2 else f"trampled_rising_{i}"
            if stage < 2:
                t, u = layers(f"trampled_{kind}_{i}")
                doc = {"parent": aw("block/wild/flat_overlay" if u else "block/wild/flat"),
                       "textures": dict({"flat": aw(f"block/wild/trampled_{kind}_{i}")}, **({"overlay": aw(f"block/wild/trampled_{kind}_{i}_overlay")} if u else {}))}
                if not u:
                    doc = {"parent": mc("block/block"), "render_type": mc("cutout"), "ambientocclusion": False,
                           "textures": {"particle": aw(f"block/wild/trampled_{kind}_{i}"), "flat": aw(f"block/wild/trampled_{kind}_{i}")},
                           "elements": flat_elements("#flat", True)}
            else:
                doc = {"parent": aw("block/wild/trampled_rising"), "textures": {"flat": aw(f"block/wild/trampled_old_{i}"), "rise": aw(f"block/wild/trampled_rise_{i}")}}
            write(f"{ASSETS}/models/block/wild/{name}.json", doc)
            models.append(aw(f"block/wild/{name}"))
        for axis, y in (("x", 0), ("z", 90)):
            for frm in range(5):
                v[f"axis={axis},from={frm},stage={stage}"] = [dict({"model": m}, **({"y": y} if y else {})) for m in models]
    write(f"{ASSETS}/blockstates/trampled_grass.json", {"variants": v})


def clover():
    """Clover lies in quarters like the game's pink petals: a quarter more for each patch, turned with its facing."""
    quarters = [(0, 0), (0, 8), (8, 8), (8, 0)]
    for k, (x, z) in enumerate(quarters):
        sprite = f"clover_{k % 3}"
        t, u = layers(sprite)
        f = lambda tex, tint: dict({"uv": [x, z, x + 8, z + 8], "texture": tex}, **({"tintindex": 0} if tint else {}))
        els = [{"from": [x, 1, z], "to": [x + 8, 1, z + 8], "faces": {"up": f("#leaves", True), "down": f("#leaves", True)}}]
        if u:
            els.append({"from": [x, 1.05, z], "to": [x + 8, 1.05, z + 8], "faces": {"up": f("#heads", False), "down": f("#heads", False)}})
        write(f"{ASSETS}/models/block/wild/clover_{k + 1}.json", {
            "ambientocclusion": False, "render_type": mc("cutout"),
            "textures": {"particle": "#leaves", "leaves": aw(f"block/wild/{sprite}"), "heads": aw(f"block/wild/{sprite}_overlay")},
            "elements": els})
    parts = []
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for k in range(4):
            amounts = "|".join(str(a) for a in range(k + 1, 5))
            parts.append({"apply": dict({"model": aw(f"block/wild/clover_{k + 1}")}, **({"y": y} if y else {})),
                          "when": {"facing": facing, "flower_amount": amounts}})
    write(f"{ASSETS}/blockstates/clover.json", {"multipart": parts})
    item_generated("clover", "clover_0")


def leaves():
    models = []
    for i in range(3):
        write(f"{ASSETS}/models/block/wild/hawthorn_leaves_{i}.json", {"parent": aw("block/wild/leaves_overlay"),
               "textures": {"all": aw(f"block/wild/hawthorn_leaves_{i}"), "overlay": aw(f"block/wild/hawthorn_leaves_{i}_overlay")}})
        models.append({"model": aw(f"block/wild/hawthorn_leaves_{i}")})
    write(f"{ASSETS}/blockstates/hawthorn_leaves.json", {"variants": {"": models}})
    write(f"{ASSETS}/items/hawthorn_leaves.json", {"model": {"type": mc("model"), "model": aw("block/wild/hawthorn_leaves_0"), "tints": [LEAF_TINT]}})


def still():
    # bracket fungus on a log's side: faces away from the trunk
    for i in range(3):
        write(f"{ASSETS}/models/block/wild/bracket_fungus_{i}.json", {"parent": aw("block/wild/bracket"),
               "textures": {"f": aw(f"block/wild/bracket_fungus_{i}_overlay")}})
    write(f"{ASSETS}/blockstates/bracket_fungus.json", {"variants": {
        f"facing={f}": [dict({"model": aw(f"block/wild/bracket_fungus_{i}")}, **({"y": y} if y else {})) for i in range(3)]
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    item_generated("bracket_fungus", "bracket_fungus_0")
    for i in range(2):
        write(f"{ASSETS}/models/block/wild/antler_shed_{i}.json", {"parent": aw("block/wild/flat"),
               "textures": {"flat": aw(f"block/wild/antler_shed_{i}_overlay")}})
    write(f"{ASSETS}/blockstates/antler_shed.json", {"variants": {
        f"facing={f}": [dict({"model": aw(f"block/wild/antler_shed_{i}")}, **({"y": y} if y else {})) for i in range(2)]
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    item_generated("antler_shed", "antler_shed_0")
    for name, bottom in (("wallow", "mud"), ("salt_lick", "calcite"), ("trodden_earth", "dirt")):
        ms = []
        for i in range(2):
            write(f"{ASSETS}/models/block/wild/{name}_{i}.json", {"parent": mc("block/cube_bottom_top"), "textures": {
                "top": aw(f"block/wild/{name}_top_{i}"), "side": aw(f"block/wild/{name}_side_{i}"), "bottom": mc(f"block/{bottom}")}})
            ms += [{"model": aw(f"block/wild/{name}_{i}")}, {"model": aw(f"block/wild/{name}_{i}"), "y": 180}]
        write(f"{ASSETS}/blockstates/{name}.json", {"variants": {"": ms}})
        write(f"{ASSETS}/items/{name}.json", {"model": {"type": mc("model"), "model": aw(f"block/wild/{name}_0")}})
    ms = []
    for i in range(3):
        write(f"{ASSETS}/models/block/wild/wild_sward_{i}.json", {"parent": aw("block/wild/sward"), "textures": {
            "top": aw(f"block/wild/wild_sward_top_{i}"), "thatch": aw(f"block/wild/wild_sward_top_{i}_overlay")}})
        ms += [{"model": aw(f"block/wild/wild_sward_{i}")}, {"model": aw(f"block/wild/wild_sward_{i}"), "y": 90}]
    write(f"{ASSETS}/blockstates/wild_sward.json", {"variants": {"snowy=false": ms, "snowy=true": {"model": mc("block/grass_block_snow")}}})
    write(f"{ASSETS}/items/wild_sward.json", {"model": {"type": mc("model"), "model": aw("block/wild/wild_sward_0"), "tints": [GRASS_TINT]}})
    write(f"{ASSETS}/models/item/blackberries.json", {"parent": mc("item/generated"), "textures": {"layer0": aw("item/blackberries")}})
    write(f"{ASSETS}/items/blackberries.json", {"model": {"type": mc("model"), "model": aw("item/blackberries")}})


# ---- loot (26.3's format: one `condition`, `modifier`s, `match_block`, the tool/can_shear and tool/can_silk_touch predicates) ----
SHEAR = mc("tool/can_shear")
SILK = mc("tool/can_silk_touch")
SURVIVES = {"type": mc("survives_explosion")}


def item(n, condition=None, modifier=None):
    e = {"type": mc("item"), "name": n}
    if condition is not None:
        e["condition"] = condition
    if modifier is not None:
        e["modifier"] = modifier
    return e


def lower_only(name):
    return {"type": mc("match_block"), "blocks": aw(name), "state": {"half": "lower"}}


def loot(name, entries, condition=None):
    pool = {"rolls": 1, "entries": entries}
    if condition:
        pool["condition"] = condition
    write(f"{DATA}/loot_table/blocks/{name}.json", {"type": mc("block"), "pools": [pool], "random_sequence": aw(f"blocks/{name}")})


def grassy(name, seeds=0.125, cond=None):
    """Shears give the plant; otherwise now and then wheat seeds (none where `seeds` is 0), as the game's grass."""
    children = [item(aw(name), SHEAR)]
    if seeds:
        children.append(item(mc("wheat_seeds"), {"type": mc("all_of"), "terms": [SURVIVES, {"type": mc("random_chance"), "chance": seeds}]}))
    loot(name, [{"type": mc("alternatives"), "children": children}], cond)


def silk_or(name, other):
    loot(name, [{"type": mc("alternatives"), "children": [item(aw(name), SILK), item(other, SURVIVES)]}])


def loot_tables():
    grassy("meadow_grass")
    grassy("frost_grass")
    grassy("long_grass", cond=lower_only("long_grass"))
    grassy("wild_rye", seeds=0.25)
    grassy("bracken", seeds=0.0, cond=lower_only("bracken"))
    loot("dry_tussock", [{"type": mc("alternatives"), "children": [
        item(aw("dry_tussock"), SHEAR), item(mc("stick"), {"type": mc("random_chance"), "chance": 0.3})]}])
    for name in ("cow_parsley", "foxglove", "reeds"):
        loot(name, [item(aw(name))], {"type": mc("all_of"), "terms": [SURVIVES, lower_only(name)]})
    for name in ("thistle", "heather", "gorse", "bracket_fungus", "antler_shed", "wallow", "salt_lick"):
        loot(name, [item(aw(name))], SURVIVES)
    loot("bramble", [{"type": mc("alternatives"), "children": [
        item(aw("bramble"), SHEAR),
        item(mc("stick"), modifier=[{"type": mc("set_count"), "count": {"type": mc("uniform"), "min": 0, "max": 2}}, {"type": mc("explosion_decay")}])]}])
    loot("hawthorn_leaves", [{"type": mc("alternatives"), "children": [
        item(aw("hawthorn_leaves"), {"type": mc("any_of"), "terms": [SHEAR, SILK]}),
        item(mc("stick"), {"type": mc("all_of"), "terms": [SURVIVES, {"type": mc("random_chance"), "chance": 0.05}]})]}])
    loot("clover", [item(aw("clover"), modifier=[
        {"type": mc("set_count"), "count": n, "condition": {"type": mc("match_block"), "blocks": aw("clover"), "state": {"flower_amount": str(n)}}}
        for n in range(1, 5)] + [{"type": mc("explosion_decay")}])])
    silk_or("trodden_earth", mc("dirt"))
    silk_or("wild_sward", mc("dirt"))
    # trampled grass and the rye's upper blocks drop nothing (the rye's foot drops the rye)
    for name in ("trampled_grass", "wild_rye_top"):
        write(f"{DATA}/loot_table/blocks/{name}.json", {"type": mc("block"), "pools": []})


# ---- tags and lang ------------------------------------------------------------------------------------------------------
GRASSES = ["meadow_grass", "frost_grass", "long_grass", "wild_rye", "wild_rye_top", "trampled_grass", "dry_tussock", "bracken"]
PLANTS = GRASSES + ["clover", "cow_parsley", "foxglove", "thistle", "heather", "gorse", "bramble", "reeds"]
NAMES = {
    "meadow_grass": "Meadow Grass", "frost_grass": "Frost Grass", "long_grass": "Long Grass", "wild_rye": "Wild Rye",
    "wild_rye_top": "Wild Rye", "trampled_grass": "Trampled Grass", "clover": "Clover", "cow_parsley": "Cow Parsley",
    "foxglove": "Foxglove", "thistle": "Thistle", "heather": "Heather", "gorse": "Gorse", "dry_tussock": "Dry Tussock",
    "bramble": "Bramble", "bracken": "Bracken", "reeds": "Reeds", "hawthorn_leaves": "Hawthorn Leaves",
    "bracket_fungus": "Bracket Fungus", "antler_shed": "Antler Shed", "wallow": "Wallow", "salt_lick": "Salt Lick",
    "trodden_earth": "Trodden Earth", "wild_sward": "Wild Sward",
}


def merge_tag(path, values):
    full = f"{MERGE}/data/{path}.json"
    doc = json.load(open(full)) if os.path.exists(full) else {"values": []}
    for v in values:
        if v not in doc["values"]:
            doc["values"].append(v)
    write(full, doc)


def tags_and_lang():
    merge_tag("minecraft/tags/block/replaceable_by_trees", [aw(n) for n in GRASSES])
    merge_tag("minecraft/tags/block/sword_efficient", [aw(n) for n in PLANTS + ["hawthorn_leaves"]])
    merge_tag("minecraft/tags/block/enchantment_power_transmitter", [aw(n) for n in GRASSES])
    merge_tag("minecraft/tags/block/flowers", [aw(n) for n in ("cow_parsley", "foxglove", "thistle", "heather", "gorse", "clover")])
    merge_tag("minecraft/tags/block/leaves", [aw("hawthorn_leaves")])
    merge_tag("minecraft/tags/item/leaves", [aw("hawthorn_leaves")])
    merge_tag("minecraft/tags/block/mineable/hoe", [aw("hawthorn_leaves")])
    merge_tag("minecraft/tags/block/mineable/shovel", [aw(n) for n in ("wallow", "trodden_earth", "wild_sward")])
    merge_tag("minecraft/tags/block/mineable/pickaxe", [aw("salt_lick")])
    merge_tag("minecraft/tags/block/mineable/axe", [aw("bracket_fungus")])
    merge_tag("minecraft/tags/block/grass_blocks", [aw("wild_sward")])
    merge_tag("minecraft/tags/block/dirt", [aw("trodden_earth")])
    merge_tag("minecraft/tags/block/mud", [aw("wallow")])
    merge_tag("minecraft/tags/block/turns_into_farmland", [aw("wild_sward"), aw("trodden_earth")])
    merge_tag("minecraft/tags/block/turns_into_dirt_path", [aw("wild_sward"), aw("trodden_earth")])
    merge_tag("minecraft/tags/block/animals_spawnable_on", [aw("wild_sward"), aw("trodden_earth")])
    merge_tag("awaken/tags/block/ground/brush", [aw(n) for n in PLANTS])
    merge_tag("awaken/tags/block/wild/plants", [aw(n) for n in PLANTS])
    path = f"{MERGE}/assets/awaken/lang/en_us.json"
    lang = json.load(open(path)) if os.path.exists(path) else {}
    lang.update({f"block.awaken.{k}": v for k, v in NAMES.items()})
    lang["item.awaken.blackberries"] = "Blackberries"
    write(path, lang)


def main():
    parents()
    plants()
    trampled()
    clover()
    leaves()
    still()
    loot_tables()
    tags_and_lang()
    print("wild_plants_content: done")


if __name__ == "__main__":
    main()
