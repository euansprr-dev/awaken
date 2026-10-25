"""The third pair of Awakened biomes (docs/modules/lands.md): Frost Spires and Mirror Marsh. Their blocks' blockstates,
models, item definitions, loot, recipes, tags, data maps and lang, and everything they put in the lands pack (noises,
the ground's material rules and the Lithostitched modifiers that splice them in, features, the two biomes, their
placement, the Aurora Spire landmark and the Weeping Matriarch hero tree). Numbers live here.

Run tools/content/lands_content.py (it calls the hooks below, so the shared tag, data-map and lang files it writes
include these blocks); running this file does the same.

Block ids match dev.awaken.terrain.frost.FrostBlocks and dev.awaken.terrain.mire.MireBlocks; feature types
dev.awaken.terrain.frost.FrostLands. Biome tags come from the biome table (tools/terrain/biomes.py).
"""
import importlib.util
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))

_spec = importlib.util.spec_from_file_location("lands3_art", os.path.join(ROOT, "tools/art/specs/lands3.py"))
art = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(art)

RIME_FROM = -0.3  # the rime noise above this is hoarfrost ground (most of it); below, snowy grass
PEAT_FROM = -0.45  # the peat noise above this is mirepeat on dry ground; below, the marsh's dark grass
POOLS_FROM = -0.2  # vanilla's swamp pools (surface_swamp noise at the sea's edge), opened wider: more still water

LOGS = {  # name: (side texture, end texture)
    "bluepine_log": ("bluepine_log", "bluepine_log_top"),
    "stripped_bluepine_log": ("stripped_bluepine_log", "stripped_bluepine_log_top"),
    "bluepine_wood": ("bluepine_log", "bluepine_log"),
    "stripped_bluepine_wood": ("stripped_bluepine_log", "stripped_bluepine_log"),
    "weepwood_log": ("weepwood_log", "weepwood_log_top"),
    "stripped_weepwood_log": ("stripped_weepwood_log", "stripped_weepwood_log_top"),
    "weepwood_wood": ("weepwood_log", "weepwood_log"),
    "stripped_weepwood_wood": ("stripped_weepwood_log", "stripped_weepwood_log"),
}
WOODS = ("bluepine", "weepwood")
NAMES = {
    "bluepine_log": "Bluepine Log", "stripped_bluepine_log": "Stripped Bluepine Log", "bluepine_wood": "Bluepine Wood",
    "stripped_bluepine_wood": "Stripped Bluepine Wood", "bluepine_planks": "Bluepine Planks", "bluepine_leaves": "Bluepine Needles",
    "bluepine_sapling": "Bluepine Sapling", "frostspar": "Frostspar", "frostspar_cluster": "Frostspar Cluster", "hoarfrost": "Hoarfrost",
    "weepwood_log": "Weepwood Log", "stripped_weepwood_log": "Stripped Weepwood Log", "weepwood_wood": "Weepwood Wood",
    "stripped_weepwood_wood": "Stripped Weepwood Wood", "weepwood_planks": "Weepwood Planks", "weepwood_leaves": "Weepwood Leaves",
    "weepwood_sapling": "Weepwood Sapling", "weepwood_tendrils": "Weepwood Tendrils", "moonbloom": "Moonbloom", "mirepeat": "Mirepeat",
}
SELF_DROPS = ["frostspar", "frostspar_cluster", "hoarfrost", "moonbloom", "mirepeat"]


def block_assets(lc):
    aw, mc, w = lc.aw, lc.mc, lc.write
    A = lc.ASSETS
    for name, (side, end) in LOGS.items():
        w(f"{A}/models/block/{name}.json", {"parent": mc("block/cube_column"), "textures": {"end": aw("block/" + end), "side": aw("block/" + side)}})
        horizontal = side != end
        if horizontal:
            w(f"{A}/models/block/{name}_horizontal.json", {"parent": mc("block/cube_column_horizontal"),
                                                            "textures": {"end": aw("block/" + end), "side": aw("block/" + side)}})
        h = aw(f"block/{name}_horizontal" if horizontal else f"block/{name}")
        w(f"{A}/blockstates/{name}.json", {"variants": {
            "axis=x": {"model": h, "x": 90, "y": 90}, "axis=y": {"model": aw("block/" + name)}, "axis=z": {"model": h, "x": 90}}})
        w(f"{A}/items/{name}.json", {"model": {"type": mc("model"), "model": aw("block/" + name)}})
    for kind in WOODS:
        # planks; leaves in fixed colours (no tint source is registered for them); the sapling a cross
        w(f"{A}/models/block/{kind}_planks.json", {"parent": mc("block/cube_all"), "textures": {"all": aw(f"block/{kind}_planks")}})
        w(f"{A}/blockstates/{kind}_planks.json", {"variants": {"": {"model": aw(f"block/{kind}_planks")}}})
        w(f"{A}/items/{kind}_planks.json", {"model": {"type": mc("model"), "model": aw(f"block/{kind}_planks")}})
        w(f"{A}/models/block/{kind}_leaves.json", {"parent": mc("block/leaves"), "textures": {"all": aw(f"block/{kind}_leaves")}})
        w(f"{A}/blockstates/{kind}_leaves.json", {"variants": {"": {"model": aw(f"block/{kind}_leaves")}}})
        w(f"{A}/items/{kind}_leaves.json", {"model": {"type": mc("model"), "model": aw(f"block/{kind}_leaves")}})
        w(f"{A}/models/block/{kind}_sapling.json", {"parent": mc("block/cross"), "textures": {"cross": aw(f"block/{kind}_sapling")}})
        w(f"{A}/blockstates/{kind}_sapling.json", {"variants": {"": {"model": aw(f"block/{kind}_sapling")}}})
        w(f"{A}/models/item/{kind}_sapling.json", {"parent": mc("item/generated"), "textures": {"layer0": aw(f"block/{kind}_sapling")}})
        w(f"{A}/items/{kind}_sapling.json", {"model": {"type": mc("model"), "model": aw(f"item/{kind}_sapling")}})
    # frostspar: ice crystal with the aurora's light in its seams (two variants, emissive faces), turned every way
    for i in range(2):
        lc.lands2.lit_cube(lc, f"frostspar_{i}", f"frostspar_{i}", art.glow_pixels(f"frostspar_{i}"), "frostspar_glow")
    w(f"{A}/blockstates/frostspar.json", lc.lands2.turned([aw("block/frostspar_0"), aw("block/frostspar_1")]))
    w(f"{A}/items/frostspar.json", {"model": {"type": mc("model"), "model": aw("block/frostspar_0")}})
    w(f"{A}/models/block/frostspar_cluster.json", {"parent": mc("block/cross_emissive"),
                                                   "textures": {"cross": aw("block/frostspar_cluster"), "cross_emissive": aw("block/frostspar_cluster_glow")}})
    w(f"{A}/blockstates/frostspar_cluster.json", {"variants": {"": {"model": aw("block/frostspar_cluster")}}})
    w(f"{A}/models/item/frostspar_cluster.json", {"parent": mc("item/generated"), "textures": {
        "layer0": aw("block/frostspar_cluster"), "layer1": {"sprite": aw("block/frostspar_cluster_glow"), "neoforge_data": {"light_emission": 15}}}})
    w(f"{A}/items/frostspar_cluster.json", {"model": {"type": mc("model"), "model": aw("item/frostspar_cluster")}})
    # hoarfrost and mirepeat: the ground, three and two looks, turned every way (no repeat may show from a mountain top)
    for i in range(3):
        w(f"{A}/models/block/hoarfrost_{i}.json", {"parent": mc("block/cube_all"), "textures": {"all": aw(f"block/hoarfrost_{i}")}})
    w(f"{A}/blockstates/hoarfrost.json", lc.lands2.turned([aw(f"block/hoarfrost_{i}") for i in range(3)]))
    w(f"{A}/items/hoarfrost.json", {"model": {"type": mc("model"), "model": aw("block/hoarfrost_0")}})
    for i in range(2):
        w(f"{A}/models/block/mirepeat_{i}.json", {"parent": mc("block/cube_bottom_top"), "textures": {
            "top": aw(f"block/mirepeat_top_{i}"), "side": aw("block/mirepeat"), "bottom": aw("block/mirepeat_bottom")}})
    w(f"{A}/blockstates/mirepeat.json", lc.lands2.turned([aw("block/mirepeat_0"), aw("block/mirepeat_1")]))
    w(f"{A}/items/mirepeat.json", {"model": {"type": mc("model"), "model": aw("block/mirepeat_0")}})
    # weepwood tendrils: hanging strands, a pale catkin at the tip (vanilla HangingMossBlock states)
    w(f"{A}/models/block/weepwood_tendrils.json", {"parent": mc("block/cross"), "textures": {"cross": aw("block/weepwood_tendrils")}})
    w(f"{A}/models/block/weepwood_tendrils_tip.json", {"parent": mc("block/cross"), "textures": {"cross": aw("block/weepwood_tendrils_tip")}})
    w(f"{A}/blockstates/weepwood_tendrils.json", {"variants": {"tip=false": {"model": aw("block/weepwood_tendrils")},
                                                                  "tip=true": {"model": aw("block/weepwood_tendrils_tip")}}})
    w(f"{A}/models/item/weepwood_tendrils.json", {"parent": mc("item/generated"), "textures": {"layer0": aw("block/weepwood_tendrils_tip")}})
    w(f"{A}/items/weepwood_tendrils.json", {"model": {"type": mc("model"), "model": aw("item/weepwood_tendrils")}})
    # moonbloom: a pad on the water like a lily's, its petals a solid emissive face a hair above it
    w(f"{A}/models/block/moonbloom.json", {
        "parent": mc("block/block"), "ambientocclusion": False,
        "textures": {"pad": aw("block/moonbloom"), "glow": aw("block/moonbloom_glow"), "particle": aw("block/moonbloom")},
        "elements": [
            {"from": [0, 0.25, 0], "to": [16, 0.25, 16],
             "faces": {"down": {"uv": [0, 16, 16, 0], "texture": "#pad"}, "up": {"uv": [0, 0, 16, 16], "texture": "#pad"}}},
            {"from": [0, 0.3, 0], "to": [16, 0.3, 16], "light_emission": 15, "shade_direction_override": "up",
             "neoforge_data": {"ambient_occlusion": False},
             "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#glow"}}}]})
    w(f"{A}/blockstates/moonbloom.json", {"variants": {"": [{"model": aw("block/moonbloom")}, {"model": aw("block/moonbloom"), "y": 90},
                                                            {"model": aw("block/moonbloom"), "y": 180}, {"model": aw("block/moonbloom"), "y": 270}]}})
    w(f"{A}/models/item/moonbloom.json", {"parent": mc("item/generated"), "textures": {
        "layer0": aw("block/moonbloom"), "layer1": {"sprite": aw("block/moonbloom_glow"), "neoforge_data": {"light_emission": 15}}}})
    w(f"{A}/items/moonbloom.json", {"model": {"type": mc("model"), "model": aw("item/moonbloom")}})


def block_data(lc):
    aw, mc, w = lc.aw, lc.mc, lc.write
    for name in list(LOGS) + [f"{k}_planks" for k in WOODS] + [f"{k}_sapling" for k in WOODS] + SELF_DROPS:
        w(f"{lc.DATA}/loot_table/blocks/{name}.json", lc.self_drop(name))
    for kind in WOODS:
        w(f"{lc.DATA}/loot_table/blocks/{kind}_leaves.json", lc.leaves_loot(f"{kind}_leaves", f"{kind}_sapling"))
        w(f"{lc.DATA}/recipe/{kind}_planks.json", {"type": mc("crafting_shapeless"), "category": "building", "group": "planks",
                                                    "ingredients": [f"#awaken:{kind}_logs"], "result": {"count": 4, "id": aw(f"{kind}_planks")}})
        for src, dst in ((f"{kind}_log", f"{kind}_wood"), (f"stripped_{kind}_log", f"stripped_{kind}_wood")):
            w(f"{lc.DATA}/recipe/{dst}.json", {"type": mc("crafting_shaped"), "category": "building", "group": "bark", "key": {"#": aw(src)},
                                                "pattern": ["##", "##"], "result": {"count": 3, "id": aw(dst)}})
    w(f"{lc.DATA}/loot_table/blocks/weepwood_tendrils.json", {"type": mc("block"), "pools": [
        {"condition": lc.SHEARS_OR_SILK, "entries": [{"type": mc("item"), "name": aw("weepwood_tendrils")}], "rolls": 1}],
        "random_sequence": aw("blocks/weepwood_tendrils")})


def tags(lc, btags, itags):
    aw, add = lc.aw, lc.lands2._add
    for kind in WOODS:
        logs = [aw(n) for n in LOGS if n.endswith(f"{kind}_log") or n.endswith(f"{kind}_wood")]
        add(btags, f"awaken:{kind}_logs", logs)
        add(itags, f"awaken:{kind}_logs", logs)
        for t in (btags, itags):
            add(t, "minecraft:logs_that_burn", [f"#awaken:{kind}_logs"])
            add(t, "minecraft:planks", [aw(f"{kind}_planks")])
            add(t, "minecraft:leaves", [aw(f"{kind}_leaves")])
            add(t, "minecraft:saplings", [aw(f"{kind}_sapling")])
        add(btags, "minecraft:overworld_natural_logs", [aw(f"{kind}_log")])
    add(btags, "minecraft:mineable/pickaxe", [aw("frostspar"), aw("frostspar_cluster")])
    add(btags, "minecraft:mineable/shovel", [aw("hoarfrost"), aw("mirepeat")])
    # frozen soil and wet peat are soil: saplings, grass and flowers take root in them
    add(btags, "minecraft:dirt", [aw("hoarfrost"), aw("mirepeat")])
    add(btags, "minecraft:overworld_carver_replaceables", [aw("hoarfrost"), aw("mirepeat"), aw("frostspar")])
    add(btags, "minecraft:replaceable_by_trees", [aw("frostspar_cluster"), aw("weepwood_tendrils"), aw("moonbloom")])
    add(btags, "minecraft:sword_efficient", [aw("weepwood_tendrils")])
    # hoarfrost is the ground snow never settles on: from a mountain top the biome reads ice-blue, not white
    add(btags, "minecraft:cannot_support_snow_layer", [aw("hoarfrost"), aw("frostspar")])
    # what the air reads (terrain.client.LandsMotes): cold glints wake on frostspar at night
    add(btags, "awaken:lands/frost_glints", [aw("frostspar"), aw("frostspar_cluster")])


def flammable(lc, values):
    for name in LOGS:
        values[lc.aw(name)] = {"burn_odds": 5, "ignite_odds": 5}
    for kind in WOODS:
        values[lc.aw(f"{kind}_planks")] = {"burn_odds": 20, "ignite_odds": 5}
        values[lc.aw(f"{kind}_leaves")] = {"burn_odds": 60, "ignite_odds": 30}
    values[lc.aw("weepwood_tendrils")] = {"burn_odds": 100, "ignite_odds": 15}


def strip_map(lc, values):
    mc, aw = lc.mc, lc.aw
    for kind in WOODS:
        for src in (f"{kind}_log", f"{kind}_wood"):
            values[aw(src)] = {"transformer": mc("axe"), "transform_data": {
                "block_state_provider": {"type": mc("rule_based"), "rules": [
                    {"if_true": {"type": mc("matching_blocks"), "blocks": aw(src)},
                     "then": {"type": mc("copy_properties"), "source": {"id": aw("stripped_" + src), "properties": {"axis": "y"}}}}]},
                "item_damage_per_use": 1, "sound": mc("item.axe.strip")}}


def lang(lc, values):
    for k, v in NAMES.items():
        values[f"block.awaken.{k}"] = v
    values["biome.awaken.frost_spires"] = "Frost Spires"
    values["biome.awaken.mirror_marsh"] = "Mirror Marsh"


# =====================================================================================================================
# The lands pack


def noises():
    return {
        # where the frozen ground is hoarfrost and where snow lies: drifts tens of blocks across
        "lands/rime": {"amplitude_modifiers": [1.0, 0.6, 0.4], "base_amplitude": 1.0, "base_octave": -5, "octave_count": 3},
        # dry peat or the marsh's dark grass
        "lands/peat": {"amplitude_modifiers": [1.0, 0.5], "base_amplitude": 1.0, "base_octave": -5, "octave_count": 2},
    }


def material_rules(lc):
    mc, aw = lc.mc, lc.aw
    block = lambda s: {"type": mc("block"), "result_state": s}  # noqa: E731
    cond = lambda c, then: {"type": mc("condition"), "if_true": c, "then_run": then}  # noqa: E731
    band = lc.lands2.noise_band
    big = lc.lands2.BIG
    y_above = lambda y: {"type": mc("y_above"), "add_stone_depth": False, "anchor": {"absolute": y}, "surface_depth_multiplier": 0}  # noqa: E731
    return {
        # Frost Spires: hoarfrost over most of the ground (snow never settles on it), grass in drifts between (the snow
        # layer that settles on it makes it snowy, as in any snowy biome)
        "lands/frost_spires_surface": {"type": mc("sequence"), "sequence": [
            cond(band(aw("lands/rime"), RIME_FROM, big), block(aw("hoarfrost"))),
            block(mc("grass_block"))]},
        # Mirror Marsh, the whole floor (the overworld's surface rule, ahead of everything, so the bed under the water is
        # peat too and the water reads black): wide still pools at the sea's edge (the swamp's own rule, nested as it
        # nests it, with more of the noise), mirepeat, the marsh's dark grass
        "lands/mirror_marsh_floor": cond(mc("on_floor"), {"type": mc("sequence"), "sequence": [
            cond(y_above(62), cond({"type": mc("not"), "invert": y_above(63)}, cond(band(mc("surface_swamp"), POOLS_FROM, big), block(mc("water"))))),
            cond(mc("not_underwater"), {"type": mc("sequence"), "sequence": [
                cond(band(aw("lands/peat"), PEAT_FROM, big), block(aw("mirepeat"))),
                block(mc("grass_block"))]}),
            block(aw("mirepeat"))]}),
        "lands/mirror_marsh_under": block(aw("mirepeat")),
    }


# which overworld rule each part of an Awakened biome's ground goes first in
TARGETS = {"surface": "biome_surface", "under": "under_biome_surface", "floor": "surface"}


def material_modifiers(lc):
    out = {}
    for name in material_rules(lc):
        biome, part = name.split("/")[1].rsplit("_", 1)
        out[name] = {
            "type": "lithostitched:set_material_rule", "target_rules": lc.mc("overworld/" + TARGETS[part]), "injection_type": "prepend",
            "material_rule": {"type": lc.mc("condition"), "if_true": {"type": lc.mc("biome"), "biome_is": lc.aw(biome)},
                              "then_run": lc.aw(name)}}
    return out


def features(lc):
    aw, mc, st, uni = lc.aw, lc.mc, lc.state, lc.uniform
    f = {}
    # bluepine: a tall narrow trunk under tiers of short level limbs spiralling up it, flat blue pads drooping at their
    # rims and shrinking toward the top: a pagoda, the trunk dark between the tiers (never a spruce's solid cone)
    f["lands/bluepine"] = lc.tree(aw("bluepine_log"), aw("bluepine_wood"), aw("bluepine_leaves"), base=11, ra=3, rb=2, width=1,
                                  lean=uni(0, 1), lean_start=0.6, limbs=uni(6, 9), limb_start=0.25, limb_length=uni(2, 4),
                                  limb_rise=0.15, twig=uni(1, 1), flare=uni(0, 1), radius=uni(2, 3), height=uni(1, 1), depth=0.5,
                                  hollow=0.0, ragged=0.2, hang=0.3, offset=0, limb_mass=0, crown_mass=-1, taper=0.6,
                                  decorators=[],
                                  size={"type": mc("two_layers_feature_size"), "limit": 1, "lower_size": 0, "upper_size": 1, "min_clipped_height": 9})
    # the blue pine giants: a 2x2 trunk 22-32 tall, nine to twelve tiers
    f["lands/giant_bluepine"] = lc.tree(aw("bluepine_log"), aw("bluepine_wood"), aw("bluepine_leaves"), base=22, ra=6, rb=4, width=2,
                                        lean=uni(0, 1), lean_start=0.7, limbs=uni(9, 12), limb_start=0.28, limb_length=uni(4, 6),
                                        limb_rise=0.1, twig=uni(2, 2), flare=uni(1, 2), radius=uni(3, 3), height=uni(1, 1), depth=0.6,
                                        hollow=0.0, ragged=0.2, hang=0.35, offset=0, limb_mass=0, crown_mass=-2, taper=0.65,
                                        decorators=[],
                                        size={"type": mc("two_layers_feature_size"), "limit": 1, "lower_size": 1, "upper_size": 2, "min_clipped_height": 18})
    # weepwood: a leaning pale trunk on a swollen foot, a few rising limbs under low domes, curtains of tendrils to the water
    tendrils = {"type": aw("hanging_vines"), "body": st(aw("weepwood_tendrils"), tip=False), "tip": st(aw("weepwood_tendrils"), tip=True)}
    f["lands/weepwood"] = lc.tree(aw("weepwood_log"), aw("weepwood_wood"), aw("weepwood_leaves"), base=7, ra=2, rb=2, width=1,
                                  lean=uni(1, 2), lean_start=0.3, limbs=uni(3, 4), limb_start=0.55, limb_length=uni(3, 4),
                                  limb_rise=0.55, twig=uni(1, 2), flare=uni(1, 2), radius=uni(3, 3), height=uni(2, 2), depth=0.45,
                                  hollow=0.3, ragged=0.35, hang=0.45, offset=0, limb_mass=0, crown_mass=0,
                                  decorators=[dict(tendrils, probability=0.32, length=uni(3, 8))],
                                  size={"type": mc("two_layers_feature_size"), "limit": 1, "lower_size": 0, "upper_size": 1, "min_clipped_height": 6})
    f["lands/great_weepwood"] = lc.tree(aw("weepwood_log"), aw("weepwood_wood"), aw("weepwood_leaves"), base=9, ra=3, rb=3, width=2,
                                        lean=uni(1, 2), lean_start=0.35, limbs=uni(4, 5), limb_start=0.5, limb_length=uni(4, 5),
                                        limb_rise=0.5, twig=uni(2, 2), flare=uni(2, 3), radius=uni(3, 4), height=uni(2, 2), depth=0.45,
                                        hollow=0.35, ragged=0.35, hang=0.5, offset=0, limb_mass=0, crown_mass=0,
                                        decorators=[dict(tendrils, probability=0.36, length=uni(4, 10))],
                                        size={"type": mc("two_layers_feature_size"), "limit": 1, "lower_size": 1, "upper_size": 2, "min_clipped_height": 8})
    for kind, length, cover in (("bluepine", uni(5, 10), st(mc("snow"), layers=1)), ("weepwood", uni(4, 8), st(mc("moss_carpet")))):
        f[f"lands/fallen_{kind}"] = {
            "type": mc("fallen_tree"), "trunk_provider": st(aw(f"{kind}_log"), axis="y"), "log_length": length, "stump_decorators": [],
            "log_decorators": [{"type": mc("attached_to_logs"), "block_provider": cover, "directions": ["up"], "probability": 0.4}]}
    f["lands/bluepine_trees"] = {"type": mc("random_selector"), "default": aw("lands/bluepine"), "features": [
        {"chance": 0.07, "feature": aw("lands/fallen_bluepine")},
        {"chance": 0.28, "feature": aw("lands/giant_bluepine")}]}
    f["lands/weepwood_trees"] = {"type": mc("random_selector"), "default": aw("lands/weepwood"), "features": [
        {"chance": 0.08, "feature": aw("lands/fallen_weepwood")},
        {"chance": 0.3, "feature": aw("lands/great_weepwood")}]}
    # Frost Spires' crystals: sheaves of needles, and slivers in the snow
    f["lands/frost_spires"] = {"type": aw("frost_spires"), "count": uni(4, 8), "height": uni(9, 16), "splay": 0.5}
    f["lands/small_frost_spires"] = {"type": aw("frost_spires"), "count": uni(2, 4), "height": uni(3, 7), "splay": 0.6}
    f["lands/frostspar_cluster"] = {"type": mc("simple_block"), "to_place": st(aw("frostspar_cluster"))}
    f["lands/moonbloom"] = {"type": mc("simple_block"), "to_place": st(aw("moonbloom"))}
    return f


def placed(lc):
    aw, mc, st = lc.aw, lc.mc, lc.state
    p = {}
    for kind in WOODS:
        for name in (kind, f"{'giant' if kind == 'bluepine' else 'great'}_{kind}", f"fallen_{kind}"):
            p[f"lands/{name}"] = {"feature": aw("lands/" + name), "placement": [
                {"type": mc("block_predicate_filter"), "predicate": {"type": mc("would_survive"), "state": st(aw(f"{kind}_sapling"), stage=0)}}]}
    # a sparse frozen forest with clearings for the spires
    p["lands/bluepine_trees"] = lc.trees_placed(aw("lands/bluepine_trees"), [(1, 4), (2, 4), (3, 2)], aw("bluepine_sapling"))
    # marsh woodland: the weepwoods stand in the shallows like swamp oaks do (water up to two deep)
    p["lands/weepwood_trees"] = {"feature": aw("lands/weepwood_trees"), "placement": [
        {"type": mc("count"), "count": {"type": mc("weighted_list"), "distribution": [{"data": 1, "weight": 3}, {"data": 2, "weight": 4}, {"data": 3, "weight": 2}]}},
        {"type": mc("in_square")}, {"type": mc("surface_water_depth_filter"), "max_water_depth": 2},
        {"type": mc("heightmap"), "heightmap": "OCEAN_FLOOR"}, {"type": mc("biome")},
        {"type": mc("block_predicate_filter"), "predicate": {"type": mc("would_survive"), "state": st(aw("weepwood_sapling"), stage=0)}}]}

    def surface(feature, rarity):
        return {"feature": feature, "placement": [{"type": mc("rarity_filter"), "chance": rarity}, {"type": mc("in_square")},
                                                  {"type": mc("heightmap"), "heightmap": "WORLD_SURFACE_WG"}, {"type": mc("biome")}]}
    p["lands/frost_spires"] = surface(aw("lands/frost_spires"), 3)
    p["lands/small_frost_spires"] = surface(aw("lands/small_frost_spires"), 2)
    p["lands/frostspar_clusters"] = lc.scatter(aw("lands/frostspar_cluster"), 2, 6, 10, survive=st(aw("frostspar_cluster")))
    # moonblooms on the still water (where a lily pad could float)
    p["lands/moonblooms"] = lc.scatter(aw("lands/moonbloom"), 4, 7, 12, survive=st(aw("moonbloom")), heightmap="MOTION_BLOCKING")
    return p


def biomes(lc):
    aw, mc = lc.aw, lc.mc
    frost = lc.lands2.biome(lc, "snowy_taiga", sky="#86b6ee", fog="#d6e6f4", water="#3f76c9", water_fog="#0b1f3a",
                            grass="#8fb1aa", foliage="#5f8fa8", dry="#9aa9b0",
                            steps={},
                            swap={mc("trees_taiga"): [aw("lands/frost_spires"), aw("lands/small_frost_spires"), aw("lands/bluepine_trees")],
                                  mc("patch_grass_taiga_2"): [mc("patch_grass_taiga_2"), aw("lands/frostspar_clusters")]},
                            drop=(mc("flower_default"), mc("patch_pumpkin")), ambience=aw("amb.frost_spires"))
    marsh = lc.lands2.biome(lc, "swamp", sky="#8aa3c0", fog="#9fb0ac", water="#11191c", water_fog="#030607",
                            grass="#4f6250", foliage="#5d6f5c", dry="#6a6f5e",
                            steps={},
                            swap={mc("trees_swamp"): [aw("lands/weepwood_trees")],
                                  mc("patch_waterlily"): [aw("lands/moonblooms")]},
                            drop=(mc("flower_swamp"), mc("patch_dead_bush")), ambience=aw("amb.mirror_marsh"))
    return {"frost_spires": frost, "mirror_marsh": marsh}


LANDMARKS = {"aurora_spire": (26, 9, 1067321447)}
HEROES = {"weeping_matriarch": (24, 9, 2018440031)}


def pack(lc):
    P = lc.PACK
    for name, data in noises().items():
        lc.write(f"{P}/awaken/worldgen/noise/{name}.json", data)
    for name, data in material_rules(lc).items():
        lc.write(f"{P}/awaken/worldgen/material_rule/{name}.json", data)
    for name, data in material_modifiers(lc).items():
        lc.write(f"{P}/awaken/lithostitched/worldgen_modifier/{name}.json", data)
    for name, data in features(lc).items():
        lc.write(f"{P}/awaken/worldgen/feature/{name}.json", data)
    for name, data in placed(lc).items():
        lc.write(f"{P}/awaken/worldgen/placed_feature/{name}.json", data)
    for name, data in biomes(lc).items():
        lc.write(f"{P}/awaken/worldgen/biome/{name}.json", data)
    for name, (spacing, separation, salt) in LANDMARKS.items():
        lc.structure("landmark", "landmark", name, spacing, separation, salt)
    for name, (spacing, separation, salt) in HEROES.items():
        lc.structure("hero_tree", "tree", name, spacing, separation, salt)
    mc = lc.mc
    lc.write(f"{P}/awaken/lithostitched/biome_injector/lands/frost_spires.json",
             lc.injector("frost_spires", [mc("snowy_plains"), mc("snowy_taiga"), mc("grove"), mc("ice_spikes")]))
    lc.write(f"{P}/awaken/lithostitched/biome_injector/lands/mirror_marsh.json",
             lc.injector("mirror_marsh", [mc("swamp"), mc("mangrove_swamp")]))


if __name__ == "__main__":
    spec = importlib.util.spec_from_file_location("lands_content", os.path.join(HERE, "lands_content.py"))
    lc = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(lc)
    lc.block_assets()
    lc.block_data()
    lc.pack()
    print(f"{len(lc.WRITTEN)} files")
