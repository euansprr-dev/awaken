"""Above module data and client assets: blockstates, models, item definitions, equipment, loot tables, recipes,
worldgen (Precursor ruin structures, structure sets, the Halo dimension), tags, lang and sounds.

Run: python3 tools/content/above_content.py
Writes under src/main/resources/{assets,data}/awaken and src/main/merge/above (shared lang/sounds/tags).
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.abspath(os.path.join(HERE, "../../src/main/resources"))
MERGE = os.path.abspath(os.path.join(HERE, "../../src/main/merge/above"))
NS = "awaken"


def write(root, rel, data):
    path = os.path.join(root, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def asset(rel, data):
    write(RES, "assets/awaken/" + rel, data)


def data(rel, data_):
    write(RES, "data/" + rel, data_)


def merge(rel, data_):
    write(MERGE, rel, data_)


def a(path):
    return NS + ":" + path


# ---------------------------------------------------------------------------------------------------------------------
# catalogue (keep in sync with MachineType / AboveBlocks / AboveItems)

MACHINES = {
    "essence_reactor": "Essence Reactor",
    "gate_scanner": "Gate Scanner",
    "gate_stabiliser": "Gate Stabiliser",
    "waypoint_pylon": "Waypoint Pylon",
    "deep_lift": "Deep Lift",
    "frame_forge": "Frame Forge",
    "clone_vat": "Clone Vat",
    "dream_recorder": "Dream Recorder",
    "signal_relay": "Signal Relay",
    "recalibrator": "Recalibrator",
    "observatory": "Observatory",
    "launch_ring": "Launch Ring",
}
ALL_MACHINES = dict(MACHINES)
ALL_MACHINES["fabricator"] = "Fabricator"

SIMPLE_BLOCKS = {
    "precursor_plating": "Precursor Plating",
    "precursor_panel": "Precursor Panel",
    "precursor_glow_panel": "Precursor Glow Panel",
    "precursor_warning_light": "Precursor Warning Light",
    "precursor_void_glass": "Void Glass",
    "precursor_membrane": "Pressure Membrane",
    "precursor_door": "Sealed Precursor Door",
    "precursor_door_core": "Precursor Door Core",
    "launch_ring_frame": "Launch Ring Frame",
}

ITEMS = {
    "precursor_scrap": "Precursor Scrap",
    "precursor_circuit": "Precursor Circuit",
    "precursor_alloy": "Precursor Alloy",
    "essence_cell": "Essence Cell",
    "precursor_scanner": "Precursor Scanner",
    "precursor_frame_helmet": "Frame Helm",
    "precursor_frame_chestplate": "Frame Chest",
    "precursor_frame_leggings": "Frame Legs",
    "precursor_frame_boots": "Frame Boots",
    "frame_module_jet_boots": "Frame Module: Jet Boots",
    "frame_module_shield_emitter": "Frame Module: Shield Emitter",
    "frame_module_stealth_field": "Frame Module: Stealth Field",
    "frame_module_magnet": "Frame Module: Magnet",
    "frame_module_night_vision": "Frame Module: Night Vision",
    "frame_module_step_assist": "Frame Module: Step Assist",
}

OTHER_BLOCKS = {
    "precursor_trim": "Precursor Brass Trim",
    "broken_machine": "Broken Precursor Machine",
    "precursor_log_terminal": "Precursor Log",
    "halo_return_pad": "Return Pad",
}

ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
MACHINE_IDS = ["essence_reactor", "gate_scanner", "gate_stabiliser", "waypoint_pylon", "deep_lift", "frame_forge",
               "clone_vat", "dream_recorder", "signal_relay", "recalibrator", "observatory", "launch_ring"]


def block_models():
    for b in SIMPLE_BLOCKS:
        asset(f"models/block/{b}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": a("block/" + b)}})
        asset(f"blockstates/{b}.json", {"variants": {"": {"model": a("block/" + b)}}})
    asset("models/block/precursor_trim.json", {"parent": "minecraft:block/cube_column",
                                               "textures": {"end": a("block/precursor_trim_top"), "side": a("block/precursor_trim")}})
    asset("blockstates/precursor_trim.json", {"variants": {"": {"model": a("block/precursor_trim")}}})

    base_faces = {
        "down": {"texture": "#bottom", "cullface": "down"},
        "up": {"texture": "#top", "cullface": "up"},
        "north": {"texture": "#front", "cullface": "north"},
        "south": {"texture": "#side", "cullface": "south"},
        "west": {"texture": "#side", "cullface": "west"},
        "east": {"texture": "#side", "cullface": "east"},
    }
    asset("models/block/precursor_machine_base.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#side"},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": base_faces}],
    })
    asset("models/block/precursor_machine_lit.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#side"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": base_faces},
            {"from": [0, 0, 0], "to": [16, 16, 16], "light_emission": 15, "shade_direction_override": "up", "faces": {
                "north": {"texture": "#front_glow", "cullface": "north"},
                "up": {"texture": "#top_glow", "cullface": "up"},
            }},
        ],
    })
    for m in ALL_MACHINES:
        tex = {
            "front": a(f"block/{m}_front"), "front_glow": a(f"block/{m}_front_glow"),
            "side": a("block/precursor_machine_side"), "top": a("block/precursor_machine_top"),
            "top_glow": a("block/precursor_machine_top_glow"), "bottom": a("block/precursor_machine_bottom"),
        }
        asset(f"models/block/{m}.json", {"parent": a("block/precursor_machine_lit"), "textures": tex})
        asset(f"models/block/{m}_off.json", {"parent": a("block/precursor_machine_base"), "textures": tex})
        variants = {}
        for f, r in ROT.items():
            for lit in ("true", "false"):
                v = {"model": a(f"block/{m}" + ("" if lit == "true" else "_off"))}
                if r:
                    v["y"] = r
                variants[f"facing={f},lit={lit}"] = v
        asset(f"blockstates/{m}.json", {"variants": variants})

    for scanned in (False, True):
        name = "broken_machine_scanned" if scanned else "broken_machine"
        asset(f"models/block/{name}.json", {"parent": a("block/precursor_machine_base"), "textures": {
            "front": a("block/broken_machine_front_scanned" if scanned else "block/broken_machine_front"),
            "side": a("block/broken_machine_side"), "top": a("block/broken_machine_top"),
            "bottom": a("block/precursor_machine_bottom")}})
    variants = {}
    for f, r in ROT.items():
        for scanned in ("false", "true"):
            v = {"model": a("block/broken_machine" + ("_scanned" if scanned == "true" else ""))}
            if r:
                v["y"] = r
            variants[f"facing={f},scanned={scanned}"] = v
    asset("blockstates/broken_machine.json", {"variants": variants})

    side = a("block/precursor_plating")
    trim = a("block/precursor_trim")
    screen = a("block/precursor_log_terminal_screen")
    asset("models/block/precursor_log_terminal.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": side, "side": side, "trim": trim, "screen": screen},
        "elements": [
            {"from": [3, 0, 3], "to": [13, 2, 13], "faces": {
                "down": {"texture": "#trim", "cullface": "down"}, "up": {"texture": "#trim"},
                "north": {"texture": "#trim"}, "south": {"texture": "#trim"}, "west": {"texture": "#trim"}, "east": {"texture": "#trim"}}},
            {"from": [5, 2, 5], "to": [11, 11, 11], "faces": {
                "north": {"texture": "#side"}, "south": {"texture": "#side"}, "west": {"texture": "#side"}, "east": {"texture": "#side"}}},
            {"from": [1, 10, 2], "to": [15, 13, 14], "rotation": {"origin": [8, 11.5, 8], "axis": "x", "angle": -22.5},
             "faces": {
                 "up": {"texture": "#screen"}, "down": {"texture": "#side"},
                 "north": {"texture": "#trim"}, "south": {"texture": "#trim"}, "west": {"texture": "#trim"}, "east": {"texture": "#trim"}}},
            {"from": [1, 10, 2], "to": [15, 13, 14], "rotation": {"origin": [8, 11.5, 8], "axis": "x", "angle": -22.5},
             "light_emission": 12, "shade_direction_override": "up", "faces": {"up": {"texture": "#screen"}}},
        ],
    })
    asset("blockstates/precursor_log_terminal.json", {"variants": {
        f"facing={f}": ({"model": a("block/precursor_log_terminal"), "y": r} if r else {"model": a("block/precursor_log_terminal")})
        for f, r in ROT.items()}})

    asset("models/block/halo_return_pad.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": trim, "top": a("block/halo_return_pad_top"), "side": trim},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 4, 16], "faces": {
                "down": {"texture": "#side", "cullface": "down"},
                "up": {"texture": "#top"},
                "north": {"texture": "#side", "uv": [0, 12, 16, 16], "cullface": "north"},
                "south": {"texture": "#side", "uv": [0, 12, 16, 16], "cullface": "south"},
                "west": {"texture": "#side", "uv": [0, 12, 16, 16], "cullface": "west"},
                "east": {"texture": "#side", "uv": [0, 12, 16, 16], "cullface": "east"}}},
            {"from": [0, 4, 0], "to": [16, 4, 16], "light_emission": 15, "shade_direction_override": "up", "faces": {"up": {"texture": "#top"}}},
        ],
    })
    asset("blockstates/halo_return_pad.json", {"variants": {"": {"model": a("block/halo_return_pad")}}})


def item_assets():
    for b in list(SIMPLE_BLOCKS) + ["precursor_trim", "precursor_log_terminal", "halo_return_pad"]:
        asset(f"items/{b}.json", {"model": {"type": "minecraft:model", "model": a("block/" + b)}})
    asset("items/broken_machine.json", {"model": {"type": "minecraft:model", "model": a("block/broken_machine")}})
    for m in ALL_MACHINES:
        asset(f"items/{m}.json", {"model": {"type": "minecraft:model", "model": a("block/" + m)}})
    for i in ITEMS:
        parent = "minecraft:item/handheld" if i == "precursor_scanner" else "minecraft:item/generated"
        asset(f"models/item/{i}.json", {"parent": parent, "textures": {"layer0": a("item/" + i)}})
        asset(f"items/{i}.json", {"model": {"type": "minecraft:model", "model": a("item/" + i)}})
    asset("equipment/precursor_frame.json", {"layers": {
        "humanoid": [{"texture": a("precursor_frame")}],
        "humanoid_leggings": [{"texture": a("precursor_frame")}],
    }})


# ---------------------------------------------------------------------------------------------------------------------
# loot


def item_entry(name, weight=1, lo=1, hi=1, extra=None):
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    mods = []
    if hi > 1 or lo != 1:
        mods.append({"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if extra:
        mods.extend(extra)
    if mods:
        e["modifier"] = mods if len(mods) > 1 else mods[0]
    return e


def pool(rolls, entries, bonus=None):
    p = {"rolls": rolls, "entries": entries}
    return p


def uniform(lo, hi):
    return {"type": "minecraft:uniform", "min": lo, "max": hi}


def charged(amount):
    return [{"type": "minecraft:set_components", "components": {"awaken:essence": amount}}]


MODULES = ["jet_boots", "shield_emitter", "stealth_field", "magnet", "night_vision", "step_assist"]


def loot_tables():
    drops_self = list(SIMPLE_BLOCKS) + ["precursor_trim", "precursor_log_terminal"] + list(ALL_MACHINES)
    for b in drops_self:
        if b in ("precursor_door", "precursor_door_core"):
            continue
        data(f"awaken/loot_table/blocks/{b}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": a(b)}],
                       "condition": {"type": "minecraft:survives_explosion"}}],
            "random_sequence": a("blocks/" + b)})
    data("awaken/loot_table/blocks/broken_machine.json", {
        "type": "minecraft:block",
        "pools": [
            {"rolls": 1, "entries": [item_entry(a("precursor_scrap"), 1, 2, 4)]},
            {"rolls": uniform(0, 1), "entries": [item_entry(a("precursor_circuit"))]},
        ],
        "random_sequence": a("blocks/broken_machine")})

    common = [
        item_entry(a("precursor_scrap"), 30, 2, 6),
        item_entry(a("precursor_circuit"), 18, 1, 3),
        item_entry("awaken:essence_shard", 18, 1, 4),
        item_entry(a("precursor_void_glass"), 10, 2, 6),
        item_entry("minecraft:redstone", 10, 3, 8),
        item_entry("minecraft:copper_ingot", 10, 2, 6),
        item_entry(a("essence_cell"), 5),
        item_entry(a("precursor_alloy"), 3, 1, 2),
        item_entry("minecraft:diamond", 3, 1, 2),
        item_entry(a("precursor_scanner"), 2),
    ]
    modules = [item_entry(a("frame_module_" + m), 1) for m in MODULES]

    def chest(name, rolls, entries, extra_pools=()):
        data(f"awaken/loot_table/chests/{name}.json", {
            "type": "minecraft:chest",
            "pools": [pool(rolls, entries)] + list(extra_pools),
            "random_sequence": a("chests/" + name)})

    chest("precursor_vault", uniform(4, 7), common + modules)
    chest("precursor_spawn_vault", uniform(3, 5), common, [
        pool(1, [item_entry(a("essence_cell"), 1, extra=charged(300))]),
        pool(1, [item_entry(a("precursor_circuit"), 1, 2, 3)]),
        pool(1, [item_entry("awaken:essence_shard", 1, 3, 5)]),
    ])
    chest("precursor_spawn_antechamber", uniform(2, 3), [
        item_entry(a("precursor_scrap"), 3, 2, 5), item_entry("minecraft:copper_ingot", 2, 1, 3), item_entry("minecraft:redstone", 2, 2, 4)], [
        pool(1, [item_entry(a("precursor_scanner"))]),
    ])
    chest("precursor_sky", uniform(4, 7), common + [item_entry(a("precursor_alloy"), 6, 1, 3), item_entry(a("essence_cell"), 6, extra=charged(500))]
          + [item_entry(a("frame_module_" + m), 2) for m in MODULES])
    chest("precursor_ocean", uniform(4, 6), common + [
        item_entry("minecraft:amethyst_shard", 10, 2, 6), item_entry("minecraft:phantom_membrane", 6, 1, 2),
        item_entry("minecraft:prismarine_crystals", 6, 2, 5)] + modules)
    chest("precursor_monolith", uniform(4, 6), common + [
        item_entry("minecraft:gold_ingot", 10, 2, 5), item_entry("minecraft:crying_obsidian", 5, 1, 3)] + modules)
    chest("halo_cache", uniform(5, 8), [
        item_entry(a("precursor_alloy"), 14, 2, 5), item_entry(a("precursor_circuit"), 14, 2, 5),
        item_entry(a("essence_cell"), 8, extra=charged(1000)), item_entry("awaken:essence_shard", 12, 3, 8),
        item_entry("awaken:lord_heart", 2), item_entry("minecraft:diamond", 4, 1, 3)]
          + [item_entry(a("frame_module_" + m), 3) for m in MODULES])


# ---------------------------------------------------------------------------------------------------------------------
# recipes


def shaped(name, pattern, key, result, count=1, category="misc"):
    data(f"awaken/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
                                        "key": key, "result": {"id": result, "count": count}})


def shapeless(name, ingredients, result, count=1, category="misc"):
    data(f"awaken/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": category,
                                        "ingredients": ingredients, "result": {"id": result, "count": count}})


def recipes():
    shaped("precursor_scanner", [" A ", "CGC", " R "], {"A": "minecraft:amethyst_shard", "C": "minecraft:copper_ingot",
                                                       "G": "minecraft:glass_pane", "R": "minecraft:redstone"}, a("precursor_scanner"),
           category="equipment")
    shaped("fabricator", ["SCS", "ITI", "III"], {"S": a("precursor_scrap"), "C": a("precursor_circuit"), "I": "minecraft:iron_ingot",
                                                "T": "minecraft:crafting_table"}, a("fabricator"), category="building")
    shapeless("precursor_circuit", [a("precursor_scrap"), a("precursor_scrap"), "minecraft:redstone", "minecraft:redstone",
                                    "minecraft:copper_ingot"], a("precursor_circuit"), 2)
    shaped("precursor_alloy", ["SSS", "SHS", "SSS"], {"S": a("precursor_scrap"), "H": "awaken:lord_heart"}, a("precursor_alloy"), 8)
    shaped("essence_cell", ["SVS", "VCV", "SVS"], {"S": a("precursor_scrap"), "V": a("precursor_void_glass"), "C": a("precursor_circuit")},
           a("essence_cell"), category="equipment")
    shaped("precursor_void_glass", ["GGG", "GSG", "GGG"], {"G": "minecraft:glass", "S": a("precursor_scrap")}, a("precursor_void_glass"), 8,
           category="building")
    shaped("launch_ring_frame", [" A ", "SCS"], {"A": a("precursor_alloy"), "S": a("precursor_scrap"), "C": a("precursor_circuit")},
           a("launch_ring_frame"), 4, category="building")
    shaped("precursor_plating", ["SS", "SS"], {"S": a("precursor_scrap")}, a("precursor_plating"), 4, category="building")
    shaped("precursor_panel", ["PP", "PP"], {"P": a("precursor_plating")}, a("precursor_panel"), 4, category="building")
    shaped("precursor_trim", ["SNS"], {"S": a("precursor_scrap"), "N": "minecraft:gold_ingot"}, a("precursor_trim"), 3, category="building")
    shapeless("precursor_glow_panel", [a("precursor_plating"), "minecraft:glowstone_dust", "minecraft:glowstone_dust"],
              a("precursor_glow_panel"), category="building")
    shapeless("precursor_warning_light", [a("precursor_plating"), "minecraft:glowstone_dust", "minecraft:orange_dye"],
              a("precursor_warning_light"), category="building")
    shapeless("precursor_membrane", [a("precursor_void_glass"), "minecraft:slime_ball"], a("precursor_membrane"), 2, category="building")


# ---------------------------------------------------------------------------------------------------------------------
# worldgen


SALT = 1096176177


def worldgen():
    kinds = {
        # structure, kind, biomes, terrain, set name, placement
        "precursor_vault": ("vault", "#awaken:has_structure/precursor_vault", "beard_thin", "precursor_vaults",
                            {"type": "minecraft:random_spread", "spacing": 40, "separation": 14, "salt": SALT}),
        "precursor_sky_platform": ("sky_platform", "#awaken:has_structure/precursor_sky_platform", "none", "precursor_sky_platforms",
                                   {"type": "minecraft:random_spread", "spacing": 44, "separation": 16, "salt": SALT + 1}),
        "precursor_ocean_lab": ("ocean_lab", "#awaken:has_structure/precursor_ocean_lab", "none", "precursor_ocean_labs",
                                {"type": "minecraft:random_spread", "spacing": 36, "separation": 12, "salt": SALT + 2}),
        "precursor_monolith": ("monolith", "#awaken:has_structure/precursor_monolith", "beard_thin", "precursor_monoliths",
                               {"type": "minecraft:random_spread", "spacing": 28, "separation": 10, "salt": SALT + 3}),
        "precursor_spawn_vault": ("spawn_vault", "#minecraft:is_overworld", "beard_thin", "precursor_spawn_vault",
                                  {"type": "awaken:precursor_spawn_ring", "min_distance": 240, "max_distance": 360, "salt": SALT + 4}),
    }
    for name, (kind, biomes, terrain, set_name, placement) in kinds.items():
        data(f"awaken/worldgen/structure/{name}.json", {
            "type": "awaken:precursor_ruin", "kind": kind, "biomes": biomes, "step": "surface_structures",
            "spawn_overrides": {}, "terrain_adaptation": terrain})
        data(f"awaken/worldgen/structure_set/{set_name}.json", {
            "structures": [{"structure": a(name), "weight": 1}], "placement": placement})
    merge("data/awaken/tags/worldgen/structure/precursor_ruins.json", {"values": [a(n) for n in kinds]})
    merge("data/awaken/tags/worldgen/biome/has_structure/precursor_vault.json", {"values": [
        "#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_jungle", "#minecraft:is_savanna", "#minecraft:is_hill",
        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains", "minecraft:meadow", "minecraft:swamp",
        "minecraft:mangrove_swamp", "minecraft:cherry_grove"]})
    merge("data/awaken/tags/worldgen/biome/has_structure/precursor_sky_platform.json", {"values": [
        "#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_jungle", "#minecraft:is_savanna", "#minecraft:is_hill",
        "#minecraft:is_mountain", "#minecraft:is_badlands", "#minecraft:is_ocean", "#minecraft:is_beach",
        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains", "minecraft:meadow", "minecraft:swamp",
        "minecraft:desert", "minecraft:cherry_grove"]})
    merge("data/awaken/tags/worldgen/biome/has_structure/precursor_ocean_lab.json", {"values": ["#minecraft:is_ocean"]})
    merge("data/awaken/tags/worldgen/biome/has_structure/precursor_monolith.json", {"values": ["minecraft:desert", "#minecraft:is_badlands"]})

    # the Halo
    data("awaken/dimension_type/halo.json", {
        "has_fixed_time": True, "has_skylight": True, "has_ceiling": True, "has_ender_dragon_fight": False,
        "coordinate_scale": 1.0, "min_y": 0, "height": 256, "logical_height": 256,
        "infiniburn": "#minecraft:infiniburn_end", "ambient_light": 0.12,
        "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0,
        "skybox": "overworld",
        "attributes": {
            "minecraft:visual/sky_color": "#02040b",
            "minecraft:visual/fog_color": "#060a16",
            "minecraft:visual/fog_start_distance": 600.0,
            "minecraft:visual/fog_end_distance": 1200.0,
            "minecraft:visual/sky_fog_end_distance": 1024.0,
            "minecraft:visual/star_brightness": 1.0,
            "minecraft:visual/sun_angle": 70.0,
            "minecraft:visual/cloud_color": "#00000000",
            "minecraft:visual/ambient_light_color": "#1a2433",
            "minecraft:gameplay/sky_light_level": 15.0,
            "minecraft:gameplay/bed_rule": {"can_sleep": "never", "can_set_spawn": "never"},
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:audio/background_music": {
                "default": {"sound": "minecraft:music.end", "min_delay": 6000, "max_delay": 18000, "replace_current_music": True}},
        },
    })
    data("awaken/worldgen/biome/halo_orbit.json", {
        "has_precipitation": False, "temperature": 0.5, "downfall": 0.0,
        "effects": {"water_color": "#3f76e4"},
        "attributes": {
            "minecraft:visual/ambient_particles": {"modifier": "append", "argument": [
                {"particle": {"type": "minecraft:white_ash"}, "probability": 0.0025}]},
        },
        "carvers": [], "features": []})
    data("awaken/dimension/halo.json", {
        "type": "awaken:halo",
        "generator": {"type": "awaken:halo_station", "biome_source": {"type": "minecraft:fixed", "biome": "awaken:halo_orbit"}}})


# ---------------------------------------------------------------------------------------------------------------------
# tags, lang, sounds


def tags():
    merge("data/awaken/tags/block/machines.json", {"values": [a(m) for m in ALL_MACHINES]})
    pick = [a(b) for b in SIMPLE_BLOCKS if b not in ("precursor_door", "precursor_door_core")] + [
        a("precursor_trim"), a("broken_machine"), a("precursor_log_terminal")] + [a(m) for m in ALL_MACHINES]
    merge("data/minecraft/tags/block/mineable/pickaxe.json", {"values": pick})
    merge("data/awaken/tags/item/repairs_precursor_frame.json", {"values": [a("precursor_alloy")]})
    for slot, item in (("head_armor", "precursor_frame_helmet"), ("chest_armor", "precursor_frame_chestplate"),
                       ("leg_armor", "precursor_frame_leggings"), ("foot_armor", "precursor_frame_boots")):
        merge(f"data/minecraft/tags/item/{slot}.json", {"values": [a(item)]})


SOUNDS = {
    "above.scan": [("minecraft:block/beacon/power1", 1.9, 0.6), ("minecraft:block/amethyst/resonate1", 2.0, 0.7)],
    "above.scan_fail": [("minecraft:block/vault/insert_fail", 0.9, 0.7)],
    "above.ping": [("minecraft:block/copper_bulb/toggle", 1.0, 0.6)],
    "above.door_hum": [("minecraft:block/conduit/ambient", 0.55, 0.9), ("minecraft:block/beacon/ambient", 0.5, 0.8)],
    "above.door_open": [("minecraft:block/vault/open_shutter", 0.7, 1.0), ("minecraft:block/beacon/activate", 0.6, 0.9)],
    "above.build": [("minecraft:block/crafter/craft", 0.8, 1.0), ("minecraft:block/beacon/power2", 1.6, 0.4)],
    "above.reactor": [("minecraft:block/beacon/ambient", 1.25, 0.5), ("minecraft:block/respawn_anchor/ambient1", 1.4, 0.4)],
    "above.teleport": [("minecraft:block/respawn_anchor/set_spawn1", 1.4, 0.7), ("minecraft:block/end_portal/eyeplace1", 0.8, 0.5)],
    "above.launch": [("minecraft:mob/breeze/wind_burst1", 0.6, 1.0), ("minecraft:block/beacon/activate", 0.5, 1.0)],
    "above.leviathan": [("minecraft:mob/guardian/elder_idle1", 0.35, 1.0), ("minecraft:mob/guardian/elder_idle3", 0.3, 1.0)],
    "above.log": [("minecraft:block/amethyst/shimmer", 0.8, 0.8), ("minecraft:block/enchantment_table/enchant1", 1.3, 0.5)],
    "above.jet": [("minecraft:mob/breeze/slide1", 1.6, 0.5), ("minecraft:mob/breeze/slide2", 1.6, 0.5)],
    "above.module_off": [("minecraft:block/vault/deactivate", 1.2, 0.6)],
    "above.ui_click": [("minecraft:block/copper_bulb/toggle", 1.6, 0.4)],
}


def lang_and_sounds():
    lang = {}
    for b, n in {**SIMPLE_BLOCKS, **OTHER_BLOCKS, **ALL_MACHINES}.items():
        lang[f"block.awaken.{b}"] = n
    for i, n in ITEMS.items():
        lang[f"item.awaken.{i}"] = n
    lang["biome.awaken.halo_orbit"] = "The Halo"
    subtitles = {
        "above.scan": "Scanner reads a machine", "above.scan_fail": "Scanner buzzes", "above.ping": "Scanner pings",
        "above.door_hum": "Sealed door hums", "above.door_open": "Precursor door opens", "above.build": "Fabricator builds",
        "above.reactor": "Reactor hums", "above.teleport": "Pylon jump", "above.launch": "Launch Ring fires",
        "above.leviathan": "Something vast calls", "above.log": "Log plays", "above.jet": "Jet boots",
        "above.module_off": "Frame powers down", "above.ui_click": "Click",
    }
    sounds = {}
    for key, entries in SOUNDS.items():
        lang["subtitles.awaken." + key] = subtitles[key]
        sounds[key] = {"subtitle": "subtitles.awaken." + key,
                       "sounds": [{"name": n, "pitch": p, "volume": v} for (n, p, v) in entries]}
    merge("assets/awaken/lang/en_us.json", lang)
    merge("assets/awaken/sounds.json", sounds)


if __name__ == "__main__":
    block_models()
    item_assets()
    loot_tables()
    recipes()
    worldgen()
    tags()
    lang_and_sounds()
    print("above content written")
