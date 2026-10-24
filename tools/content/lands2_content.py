"""The second pair of Awakened biomes (docs/modules/lands.md): Glass Steppe and Shattered Highlands. Their blocks'
blockstates, models, item definitions, loot, recipes, tags, data maps and lang, and everything they put in the lands
pack (noises, the ground's material rules and the Lithostitched modifiers that splice them in, features, the two
biomes, their placement, the Gate Scar and Shard Drift landmarks). Numbers live here.

Run tools/content/lands_content.py (it calls the hooks below, so the shared tag, data-map and lang files it writes
include these blocks); running this file does the same.

Block ids match dev.awaken.terrain.glass.GlassBlocks and dev.awaken.terrain.fault.FaultBlocks; feature types
dev.awaken.terrain.glass.GlassLands and dev.awaken.terrain.fault.FaultLands; the fault noises
dev.awaken.terrain.fault.FaultNoise. Biome tags come from the biome table (tools/terrain/biomes.py).
"""
import importlib.util
import os
import random

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))

_spec = importlib.util.spec_from_file_location("lands2_art", os.path.join(ROOT, "tools/art/specs/lands2.py"))
art = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(art)

GLASS_FROM = 0.3  # the glass noise above this is fused glass ground

LOGS = {  # name: (side texture, end texture)
    "glassbark_log": ("glassbark_log", "glassbark_log_top"),
    "stripped_glassbark_log": ("stripped_glassbark_log", "stripped_glassbark_log_top"),
    "glassbark_wood": ("glassbark_log", "glassbark_log"),
    "stripped_glassbark_wood": ("stripped_glassbark_log", "stripped_glassbark_log"),
}
NAMES = {
    "glassbark_log": "Glassbark Log", "stripped_glassbark_log": "Stripped Glassbark Log", "glassbark_wood": "Glassbark Wood",
    "stripped_glassbark_wood": "Stripped Glassbark Wood", "glassbark_planks": "Glassbark Planks", "glassbark_leaves": "Glassbark Leaves",
    "glassbark_sapling": "Glassbark Sapling", "fused_glass": "Fused Glass", "sunshard": "Sunshard", "sunshard_cluster": "Sunshard Cluster",
    "scorched_glass": "Scorched Glass", "faultstone": "Faultstone", "fault_seam": "Fault Seam", "riven_shale": "Riven Shale",
}
STONES = ["fused_glass", "sunshard", "scorched_glass", "faultstone", "fault_seam", "riven_shale", "sunshard_cluster"]
FACES = ["up", "down", "north", "south", "west", "east"]
LIFT = 0.05


def rects(pixels):
    """Glow pixels merged into rectangles (u, v, w, h): runs along each row, then identical runs stacked."""
    rows = {}
    for (u, v) in sorted(set(pixels), key=lambda p: (p[1], p[0])):
        rows.setdefault(v, []).append(u)
    runs = []
    for v, us in rows.items():
        start = prev = us[0]
        for u in us[1:] + [None]:
            if u is not None and u == prev + 1:
                prev = u
                continue
            runs.append([start, v, prev - start + 1, 1])
            if u is not None:
                start = prev = u
    out = []
    for r in sorted(runs, key=lambda r: (r[0], r[2], r[1])):
        if out and out[-1][0] == r[0] and out[-1][2] == r[2] and out[-1][1] + out[-1][3] == r[1]:
            out[-1][3] += 1
        else:
            out.append(r)
    return [tuple(r) for r in out]


def strip(face, u, v, w, h):
    """An emissive face over texels (u, v)-(u+w, v+h) of a full cube's face, a hair in front of it (the default face UVs
    of FaceBakery: the slice's speck(), widened to rectangles)."""
    lo, hi = -LIFT, 16 + LIFT
    if face == "up":
        a, b = [u, hi, v], [u + w, hi, v + h]
    elif face == "down":
        a, b = [u, lo, 16 - h - v], [u + w, lo, 16 - v]
    elif face == "north":
        a, b = [16 - w - u, 16 - h - v, lo], [16 - u, 16 - v, lo]
    elif face == "south":
        a, b = [u, 16 - h - v, hi], [u + w, 16 - v, hi]
    elif face == "west":
        a, b = [lo, 16 - h - v, u], [lo, 16 - v, u + w]
    else:
        a, b = [hi, 16 - h - v, 16 - w - u], [hi, 16 - v, 16 - u]
    return {"from": a, "to": b, "light_emission": 15, "shade_direction_override": "up",
            "neoforge_data": {"ambient_occlusion": False},
            "faces": {face: {"texture": "#glow", "cullface": face}}}


def lit_cube(lc, name, texture, pixels, glow, faces=None):
    """A full cube of one texture with its glow pixels as emissive faces (on every face, or on `faces`). Each face
    gets the same pattern turned a different way, so stacked blocks don't repeat one crack."""
    els = [{"from": [0, 0, 0], "to": [16, 16, 16],
            "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#all", "cullface": f} for f in FACES}}]
    turns = {"up": 0, "down": 1, "north": 2, "south": 3, "west": 1, "east": 2}
    for f in faces or FACES:
        pts = pixels
        for _ in range(turns[f]):
            pts = [(15 - v, u) for (u, v) in pts]
        for (u, v, w, h) in rects(pts):
            els.append(strip(f, u, v, w, h))
    lc.write(f"{lc.ASSETS}/models/block/{name}.json", {
        "parent": lc.mc("block/block"), "ambientocclusion": True,
        "textures": {"all": lc.aw("block/" + texture), "glow": lc.aw("block/" + glow), "particle": lc.aw("block/" + texture)},
        "elements": els})


def turned(models, rotations=(0, 90, 180, 270)):
    """Blockstate variants: every model at every turn, equally likely."""
    return {"variants": {"": [{"model": m, "y": r} if r else {"model": m} for m in models for r in rotations]}}


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
    # riven shale: a column of strata, lying either way like a log
    w(f"{A}/models/block/riven_shale.json", {"parent": mc("block/cube_column"), "textures": {"end": aw("block/riven_shale_top"), "side": aw("block/riven_shale")}})
    w(f"{A}/models/block/riven_shale_horizontal.json", {"parent": mc("block/cube_column_horizontal"),
                                                        "textures": {"end": aw("block/riven_shale_top"), "side": aw("block/riven_shale")}})
    w(f"{A}/blockstates/riven_shale.json", {"variants": {
        "axis=x": {"model": aw("block/riven_shale_horizontal"), "x": 90, "y": 90}, "axis=y": {"model": aw("block/riven_shale")},
        "axis=z": {"model": aw("block/riven_shale_horizontal"), "x": 90}}})
    w(f"{A}/items/riven_shale.json", {"model": {"type": mc("model"), "model": aw("block/riven_shale")}})
    # planks, leaves (fixed amber: no tint source), sapling
    w(f"{A}/models/block/glassbark_planks.json", {"parent": mc("block/cube_all"), "textures": {"all": aw("block/glassbark_planks")}})
    w(f"{A}/blockstates/glassbark_planks.json", {"variants": {"": {"model": aw("block/glassbark_planks")}}})
    w(f"{A}/items/glassbark_planks.json", {"model": {"type": mc("model"), "model": aw("block/glassbark_planks")}})
    w(f"{A}/models/block/glassbark_leaves.json", {"parent": mc("block/leaves"), "textures": {"all": aw("block/glassbark_leaves")}})
    w(f"{A}/blockstates/glassbark_leaves.json", {"variants": {"": {"model": aw("block/glassbark_leaves")}}})
    w(f"{A}/items/glassbark_leaves.json", {"model": {"type": mc("model"), "model": aw("block/glassbark_leaves")}})
    w(f"{A}/models/block/glassbark_sapling.json", {"parent": mc("block/cross"), "textures": {"cross": aw("block/glassbark_sapling")}})
    w(f"{A}/blockstates/glassbark_sapling.json", {"variants": {"": {"model": aw("block/glassbark_sapling")}}})
    w(f"{A}/models/item/glassbark_sapling.json", {"parent": mc("item/generated"), "textures": {"layer0": aw("block/glassbark_sapling")}})
    w(f"{A}/items/glassbark_sapling.json", {"model": {"type": mc("model"), "model": aw("item/glassbark_sapling")}})
    # fused glass: three crackles, turned every way (it is the ground: no repeat may show)
    for i in range(3):
        w(f"{A}/models/block/fused_glass_{i}.json", {"parent": mc("block/cube_all"), "textures": {"all": aw(f"block/fused_glass_{i}")}})
    w(f"{A}/blockstates/fused_glass.json", turned([aw(f"block/fused_glass_{i}") for i in range(3)]))
    w(f"{A}/items/fused_glass.json", {"model": {"type": mc("model"), "model": aw("block/fused_glass_0")}})
    # sunshard: crystal with amber specks (two variants)
    for i in range(2):
        lit_cube(lc, f"sunshard_{i}", f"sunshard_{i}", art.glow_pixels(f"sunshard_{i}"), "sunshard_glow")
    w(f"{A}/blockstates/sunshard.json", turned([aw("block/sunshard_0"), aw("block/sunshard_1")]))
    w(f"{A}/items/sunshard.json", {"model": {"type": mc("model"), "model": aw("block/sunshard_0")}})
    # scorched glass and faultstone: cracks of light (three each)
    for kind, glow in (("scorched_glass", "sunshard_glow"), ("faultstone", "fault_glow")):
        for i in range(3):
            lit_cube(lc, f"{kind}_{i}", f"{kind}_{i}", art.glow_pixels(f"{kind}_{i}"), glow)
        w(f"{A}/blockstates/{kind}.json", turned([aw(f"block/{kind}_{i}") for i in range(3)]))
        w(f"{A}/items/{kind}.json", {"model": {"type": mc("model"), "model": aw(f"block/{kind}_0")}})
    # the seam: the whole cube emissive
    w(f"{A}/models/block/fault_seam.json", {"parent": mc("block/block"), "textures": {"all": aw("block/fault_seam"), "particle": aw("block/fault_seam")},
                                            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 15, "shade_direction_override": "up",
                                                          "neoforge_data": {"ambient_occlusion": False},
                                                          "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#all", "cullface": f} for f in FACES}}]})
    w(f"{A}/blockstates/fault_seam.json", turned([aw("block/fault_seam")]))
    w(f"{A}/items/fault_seam.json", {"model": {"type": mc("model"), "model": aw("block/fault_seam")}})
    # sunshard clusters: a cross with an emissive overlay (few blocks)
    w(f"{A}/models/block/sunshard_cluster.json", {"parent": mc("block/cross_emissive"),
                                                  "textures": {"cross": aw("block/sunshard_cluster"), "cross_emissive": aw("block/sunshard_cluster_glow")}})
    w(f"{A}/blockstates/sunshard_cluster.json", {"variants": {"": {"model": aw("block/sunshard_cluster")}}})
    w(f"{A}/models/item/sunshard_cluster.json", {"parent": mc("item/generated"), "textures": {
        "layer0": aw("block/sunshard_cluster"), "layer1": {"sprite": aw("block/sunshard_cluster_glow"), "neoforge_data": {"light_emission": 15}}}})
    w(f"{A}/items/sunshard_cluster.json", {"model": {"type": mc("model"), "model": aw("item/sunshard_cluster")}})


def block_data(lc):
    aw, mc, w = lc.aw, lc.mc, lc.write
    for name in list(LOGS) + ["glassbark_planks", "glassbark_sapling"] + STONES:
        w(f"{lc.DATA}/loot_table/blocks/{name}.json", lc.self_drop(name))
    w(f"{lc.DATA}/loot_table/blocks/glassbark_leaves.json", lc.leaves_loot("glassbark_leaves", "glassbark_sapling"))
    w(f"{lc.DATA}/recipe/glassbark_planks.json", {"type": mc("crafting_shapeless"), "category": "building", "group": "planks",
                                                  "ingredients": ["#awaken:glassbark_logs"], "result": {"count": 4, "id": aw("glassbark_planks")}})
    for src, dst in (("glassbark_log", "glassbark_wood"), ("stripped_glassbark_log", "stripped_glassbark_wood")):
        w(f"{lc.DATA}/recipe/{dst}.json", {"type": mc("crafting_shaped"), "category": "building", "group": "bark", "key": {"#": aw(src)},
                                            "pattern": ["##", "##"], "result": {"count": 3, "id": aw(dst)}})


def _add(tags, tag, values):
    tags.setdefault(tag, [])
    for v in values:
        if v not in tags[tag]:
            tags[tag].append(v)


def tags(lc, btags, itags):
    aw = lc.aw
    logs = [aw(n) for n in LOGS]
    _add(btags, "awaken:glassbark_logs", logs)
    _add(itags, "awaken:glassbark_logs", logs)
    for t in (btags, itags):
        _add(t, "minecraft:logs_that_burn", ["#awaken:glassbark_logs"])
        _add(t, "minecraft:planks", [aw("glassbark_planks")])
        _add(t, "minecraft:leaves", [aw("glassbark_leaves")])
        _add(t, "minecraft:saplings", [aw("glassbark_sapling")])
    _add(btags, "minecraft:overworld_natural_logs", [aw("glassbark_log")])
    _add(btags, "minecraft:mineable/pickaxe", [aw(n) for n in STONES if n != "sunshard_cluster"] + [aw("sunshard_cluster")])
    _add(btags, "minecraft:replaceable_by_trees", [aw("sunshard_cluster")])
    # caves cut through the new ground like through stone
    _add(btags, "minecraft:overworld_carver_replaceables", [aw("fused_glass"), aw("faultstone"), aw("fault_seam"), aw("riven_shale")])
    # the faults are warm: snow never settles on them, so the lines run on through snowy peaks
    _add(btags, "minecraft:cannot_support_snow_layer", [aw("faultstone"), aw("fault_seam")])
    # what the air reads (terrain.client.LandsMotes): glints catch on glass, sparks rise from the faults
    _add(btags, "awaken:lands/glints", [aw("fused_glass"), aw("sunshard"), aw("sunshard_cluster"), aw("scorched_glass")])
    _add(btags, "awaken:lands/faults", [aw("faultstone"), aw("fault_seam")])
    _add(btags, "awaken:lands/glint_spires", [aw("sunshard")])


def flammable(lc, values):
    for name in LOGS:
        values[lc.aw(name)] = {"burn_odds": 5, "ignite_odds": 5}
    values[lc.aw("glassbark_planks")] = {"burn_odds": 20, "ignite_odds": 5}
    values[lc.aw("glassbark_leaves")] = {"burn_odds": 60, "ignite_odds": 30}


def strip_map(lc, values):
    mc, aw = lc.mc, lc.aw
    for src in ("glassbark_log", "glassbark_wood"):
        values[aw(src)] = {"transformer": mc("axe"), "transform_data": {
            "block_state_provider": {"type": mc("rule_based"), "rules": [
                {"if_true": {"type": mc("matching_blocks"), "blocks": aw(src)},
                 "then": {"type": mc("copy_properties"), "source": {"id": aw("stripped_" + src), "properties": {"axis": "y"}}}}]},
            "item_damage_per_use": 1, "sound": mc("item.axe.strip")}}


def lang(lc, values):
    for k, v in NAMES.items():
        values[f"block.awaken.{k}"] = v
    values["biome.awaken.glass_steppe"] = "Glass Steppe"
    values["biome.awaken.shattered_highlands"] = "Shattered Highlands"


# =====================================================================================================================
# The lands pack


def noises():
    return {
        # fault lines: the zero set of a slow noise, roughened by four finer octaves so the lines run jagged like cracks
        "lands/fault": {"amplitude_modifiers": [1.0, 0.7, 0.5, 0.45, 0.3], "base_amplitude": 1.0, "base_octave": -8, "octave_count": 5},
        # which stretches of a fault open into trenches
        "lands/fault_trench": {"amplitude_modifiers": [1.0, 0.5], "base_amplitude": 1.0, "base_octave": -7, "octave_count": 2},
        # the steppe's glassed ground: patches tens of blocks across
        "lands/glass": {"amplitude_modifiers": [1.0, 0.6, 0.4], "base_amplitude": 1.0, "base_octave": -6, "octave_count": 3},
    }


def noise_band(noise, lo, hi):
    return {"type": "minecraft:noise_threshold", "noise": noise, "min_threshold": lo, "max_threshold": hi}


BIG = 1.7976931348623157e308


def material_rules(lc):
    mc, aw = lc.mc, lc.aw
    block = lambda s: {"type": mc("block"), "result_state": s}  # noqa: E731
    cond = lambda c, then: {"type": mc("condition"), "if_true": c, "then_run": then}  # noqa: E731
    top2 = {"type": mc("stone_depth"), "offset": 1, "add_surface_depth": False, "secondary_depth_range": 0, "surface_type": "floor"}
    return {
        # Glass Steppe: sun-gold grass, glassed in patches; under the glass a second layer of it, then sandstone
        "lands/glass_steppe_surface": {"type": mc("sequence"), "sequence": [
            cond(noise_band(aw("lands/glass"), GLASS_FROM, BIG), block(aw("fused_glass"))),
            cond(noise_band(mc("surface"), 0.62, BIG), block(mc("coarse_dirt"))),
            block(mc("grass_block"))]},
        "lands/glass_steppe_under": {"type": mc("sequence"), "sequence": [
            cond(noise_band(aw("lands/glass"), GLASS_FROM, BIG), {"type": mc("sequence"), "sequence": [
                cond(top2, block(aw("fused_glass"))), block(mc("sandstone"))]})]},
        # Shattered Highlands: riven shale on the slopes and the rocky ground, a little scree, grass on the flats (the
        # faults are laid over this by the fault_lines feature, which keeps their width constant)
        "lands/shattered_highlands_surface": {"type": mc("sequence"), "sequence": [
            cond({"type": mc("steep")}, block(aw("riven_shale"))),
            cond(noise_band(mc("surface"), -0.05, BIG), block(aw("riven_shale"))),
            cond(noise_band(mc("surface"), -0.32, -0.22), block(mc("gravel"))),
            block(mc("grass_block"))]},
        "lands/shattered_highlands_under": {"type": mc("sequence"), "sequence": [
            cond({"type": mc("steep")}, block(aw("riven_shale"))),
            cond(noise_band(mc("surface"), -0.05, BIG), block(aw("riven_shale")))]},
    }


def material_modifiers(lc):
    """Lithostitched splices: each biome's rules go first in the overworld's biome surface and under-surface rules."""
    out = {}
    for biome in ("glass_steppe", "shattered_highlands"):
        for part, target in (("surface", "biome_surface"), ("under", "under_biome_surface")):
            out[f"lands/{biome}_{part}"] = {
                "type": "lithostitched:set_material_rule", "target_rules": lc.mc("overworld/" + target), "injection_type": "prepend",
                "material_rule": {"type": lc.mc("condition"), "if_true": {"type": lc.mc("biome"), "biome_is": lc.aw(biome)},
                                  "then_run": lc.aw(f"lands/{biome}_{part}")}}
    return out


def features(lc):
    aw, mc, st, uni = lc.aw, lc.mc, lc.state, lc.uniform
    f = {}
    # glassbark: an acacia's sweep in bleached wood, leaning, a few rising limbs, flat amber pads
    f["lands/glassbark"] = lc.tree(aw("glassbark_log"), aw("glassbark_wood"), aw("glassbark_leaves"), base=6, ra=2, rb=1, width=1,
                                   lean=uni(1, 3), lean_start=0.25, limbs=uni(2, 3), limb_start=0.55, limb_length=uni(2, 4),
                                   limb_rise=0.85, twig=uni(1, 2), flare=uni(0, 1), radius=uni(3, 4), height=uni(1, 1), depth=0.35,
                                   hollow=0.0, ragged=0.35, hang=0.0, offset=1, limb_mass=0, crown_mass=0,
                                   decorators=[],
                                   size={"type": mc("two_layers_feature_size"), "limit": 1, "lower_size": 0, "upper_size": 1, "min_clipped_height": 5})
    f["lands/tall_glassbark"] = lc.tree(aw("glassbark_log"), aw("glassbark_wood"), aw("glassbark_leaves"), base=9, ra=3, rb=2, width=1,
                                        lean=uni(2, 4), lean_start=0.2, limbs=uni(3, 4), limb_start=0.5, limb_length=uni(3, 5),
                                        limb_rise=0.75, twig=uni(1, 2), flare=uni(1, 2), radius=uni(3, 5), height=uni(1, 1), depth=0.35,
                                        hollow=0.0, ragged=0.3, hang=0.0, offset=1, limb_mass=0, crown_mass=0,
                                        decorators=[],
                                        size={"type": mc("two_layers_feature_size"), "limit": 1, "lower_size": 0, "upper_size": 1, "min_clipped_height": 7})
    f["lands/fallen_glassbark"] = {
        "type": mc("fallen_tree"), "trunk_provider": st(aw("glassbark_log"), axis="y"), "log_length": uni(4, 8), "stump_decorators": [],
        "log_decorators": [{"type": mc("attached_to_logs"), "block_provider": st(mc("short_dry_grass")), "directions": ["up"], "probability": 0.2}]}
    f["lands/glassbark_trees"] = {"type": mc("random_selector"), "default": aw("lands/glassbark"), "features": [
        {"chance": 0.08, "feature": aw("lands/fallen_glassbark")},
        {"chance": 0.32, "feature": aw("lands/tall_glassbark")}]}
    f["lands/glass_shards"] = {"type": aw("glass_shards"), "count": uni(3, 7), "height": uni(8, 14), "spread": 3.5}
    f["lands/small_glass_shards"] = {"type": aw("glass_shards"), "count": uni(1, 3), "height": uni(3, 7), "spread": 1.5}
    f["lands/mirror_pool"] = {"type": aw("mirror_pool"), "radius": uni(3, 7)}
    f["lands/sunshard_cluster"] = {"type": mc("simple_block"), "to_place": st(aw("sunshard_cluster"))}
    # Shattered Highlands
    f["lands/fault_lines"] = {"type": aw("fault_lines")}
    f["lands/floating_shard"] = {"type": aw("floating_shard"), "radius": uni(1, 3), "lift": uni(7, 20)}
    f["lands/shale_boulder"] = {"type": mc("block_blob"), "state": st(aw("riven_shale"), axis="y"), "can_place_on": {
        "type": mc("matching_blocks"), "blocks": [mc("grass_block"), aw("riven_shale"), mc("stone"), mc("dirt"), mc("gravel")]}}
    f["lands/highland_trees"] = {"type": mc("random_selector"), "default": mc("spruce"), "features": [
        {"chance": 0.45, "feature": mc("pine")}]}
    return f


def placed(lc):
    aw, mc, st, uni = lc.aw, lc.mc, lc.state, lc.uniform
    p = {}
    for name in ("glassbark", "tall_glassbark", "fallen_glassbark"):
        p[f"lands/{name}"] = {"feature": aw("lands/" + name), "placement": [
            {"type": mc("block_predicate_filter"), "predicate": {"type": mc("would_survive"), "state": st(aw("glassbark_sapling"), stage=0)}}]}
    # open steppe: half a tree a chunk, often none
    p["lands/glassbark_trees"] = lc.trees_placed(aw("lands/glassbark_trees"), [(0, 5), (1, 4), (2, 1)], aw("glassbark_sapling"))

    def surface(feature, rarity, heightmap="WORLD_SURFACE_WG"):
        return {"feature": feature, "placement": [{"type": mc("rarity_filter"), "chance": rarity}, {"type": mc("in_square")},
                                                  {"type": mc("heightmap"), "heightmap": heightmap}, {"type": mc("biome")}]}
    p["lands/glass_shards"] = surface(aw("lands/glass_shards"), 4)
    p["lands/small_glass_shards"] = surface(aw("lands/small_glass_shards"), 2)
    p["lands/mirror_pool"] = surface(aw("lands/mirror_pool"), 7)
    p["lands/sunshard_clusters"] = lc.scatter(aw("lands/sunshard_cluster"), 2, 6, 10, survive=st(aw("sunshard_cluster")))
    p["lands/steppe_dry_grass"] = lc.scatter(mc("dry_grass"), 3, 7, 24, below=[mc("grass_block"), mc("coarse_dirt")])
    # the fault painter runs once a chunk over that chunk's own columns (no placement: it reads the fault noise itself)
    p["lands/fault_lines"] = {"feature": aw("lands/fault_lines"), "placement": []}
    p["lands/floating_shard"] = surface(aw("lands/floating_shard"), 2)
    p["lands/shale_boulder"] = {"feature": aw("lands/shale_boulder"), "placement": [
        {"type": mc("count"), "count": uni(0, 1)}, {"type": mc("in_square")}, {"type": mc("heightmap"), "heightmap": "MOTION_BLOCKING"},
        {"type": mc("biome")}]}
    p["lands/highland_trees"] = {"feature": aw("lands/highland_trees"), "placement": [
        {"type": mc("count"), "count": {"type": mc("weighted_list"), "distribution": [{"data": 0, "weight": 5}, {"data": 1, "weight": 2}]}},
        {"type": mc("in_square")}, {"type": mc("surface_water_depth_filter"), "max_water_depth": 0},
        {"type": mc("heightmap"), "heightmap": "OCEAN_FLOOR"}, {"type": mc("biome")},
        {"type": mc("block_predicate_filter"), "predicate": {"type": mc("would_survive"), "state": st(mc("spruce_sapling"), stage=0)}}]}
    return p


def biome(lc, base, *, sky, fog, water, water_fog, grass, foliage, dry, steps, swap, drop=(), ambience=None):
    """The archetype's biome with our colours, our features added at the end of the given steps (vanilla's keep their
    relative order: the feature sorter rejects cycles), `swap` replacing vegetation features and `drop` removing them."""
    b = lc.archetype(base)
    attrs = dict(b["attributes"])
    attrs["minecraft:visual/sky_color"] = sky
    attrs["minecraft:visual/fog_color"] = fog
    attrs["minecraft:visual/water_fog_color"] = water_fog
    if ambience:
        # the bed, and the overworld's cave mood kept (a biome's value replaces the dimension's whole)
        attrs["minecraft:audio/ambient_sounds"] = {"loop": ambience, "mood": {
            "block_search_extent": 8, "offset": 2.0, "sound": lc.mc("ambient.cave"), "tick_delay": 6000}}
    b["attributes"] = dict(sorted(attrs.items()))
    b["effects"] = {"water_color": water, "grass_color": grass, "foliage_color": foliage, "dry_foliage_color": dry}
    out = [list(s) for s in b["features"]]
    while len(out) < 11:
        out.append([])
    veg = []
    for f in out[9]:
        if f in swap:
            veg += swap[f]
        elif f not in drop:
            veg.append(f)
    out[9] = veg
    for step, extra in steps.items():
        out[step] = out[step] + extra
    b["features"] = out
    return b


def biomes(lc):
    aw, mc = lc.aw, lc.mc
    steppe = biome(lc, "savanna", sky="#9fc4f0", fog="#ecdcb8", water="#8cc0d4", water_fog="#26343a",
                   grass="#cfb35a", foliage="#c8a24a", dry="#dcb46a",
                   steps={2: [aw("lands/mirror_pool")]},
                   swap={mc("trees_savanna"): [aw("lands/glass_shards"), aw("lands/small_glass_shards"), aw("lands/glassbark_trees")],
                         mc("patch_grass_savanna"): [mc("patch_grass_savanna"), aw("lands/steppe_dry_grass"), aw("lands/sunshard_clusters")]},
                   drop=(mc("flower_warm"),), ambience=aw("amb.glass_steppe"))
    highlands = biome(lc, "windswept_hills", sky="#8da6f2", fog="#bcbedb", water="#4a66ad", water_fog="#161d36",
                      grass="#7c8b68", foliage="#6b7e5e", dry="#8c806a",
                      steps={2: [aw("lands/fault_lines")]},
                      swap={mc("trees_windswept_hills"): [aw("lands/highland_trees"), aw("lands/shale_boulder"), aw("lands/floating_shard")]},
                      drop=(mc("flower_default"),), ambience=aw("amb.shattered_highlands"))
    return {"glass_steppe": steppe, "shattered_highlands": highlands}


LANDMARKS = {"gate_scar": (28, 10, 1442977203), "shard_drift": (22, 8, 905113377)}


def landmarks(lc):
    for name, (spacing, separation, salt) in LANDMARKS.items():
        lc.write(f"{lc.PACK}/awaken/worldgen/structure/{name}.json", {
            "type": lc.aw("landmark"), "landmark": name, "biomes": f"#awaken:has_structure/{name}", "step": "surface_structures",
            "spawn_overrides": {}, "terrain_adaptation": "none"})
        lc.write(f"{lc.PACK}/awaken/worldgen/structure_set/{name}.json", {
            "structures": [{"structure": lc.aw(name), "weight": 1}],
            "placement": {"type": lc.mc("random_spread"), "spacing": spacing, "separation": separation, "salt": salt}})


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
    landmarks(lc)
    mc = lc.mc
    lc.write(f"{P}/awaken/lithostitched/biome_injector/lands/glass_steppe.json",
             lc.injector("glass_steppe", [mc("savanna"), mc("savanna_plateau")]))
    lc.write(f"{P}/awaken/lithostitched/biome_injector/lands/shattered_highlands.json",
             lc.injector("shattered_highlands", [mc("windswept_hills"), mc("windswept_gravelly_hills"), mc("stony_peaks")]))


if __name__ == "__main__":
    spec = importlib.util.spec_from_file_location("lands_content", os.path.join(HERE, "lands_content.py"))
    lc = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(lc)
    lc.block_assets()
    lc.block_data()
    lc.pack()
    print(f"{len(lc.WRITTEN)} files")
