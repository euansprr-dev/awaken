"""Generates every JSON resource of the descent module: dimension types, dimensions, biomes, blockstates, block and
item models, client item definitions, loot tables, lang, sounds and mineable tags.

Run from the repo root: python3 tools/content/descent_assets.py
Block names must match dev.awaken.descent.DescentContent.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
RES = os.path.join(ROOT, "src/main/resources")
MERGE = os.path.join(ROOT, "src/main/merge/descent")


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


# ---------------------------------------------------------------------------------------------------------------------
# Blocks: name -> (kind, english name, tool)
BLOCKS = {
    "abyss_stone": ("all", "Abyss Stone", "pickaxe"),
    "well_stone": ("all", "Well Stone", "pickaxe"),
    "well_glyph": ("all", "Well Glyph", "pickaxe"),
    "well_light": ("all", "Deep Light", "pickaxe"),
    "well_lift": ("lift", "Well Lift", None),
    "ascent_stone": ("column", "Ascent Stone", None),
    "waystone": ("waystone", "Waystone", None),
    "descent_pit": ("pit", "Descent Pit", None),
    "conduit": ("conduit", "Foundry Conduit", None),
    "foundry_seal": ("all", "Foundry Seal", None),
    "rim_stone": ("all", "Rim Stone", "pickaxe"),
    "rim_soil": ("all", "Rim Soil", "shovel"),
    "rim_moss": ("all", "Rim Glowmoss", "hoe"),
    "garden_stone": ("all", "Garden Stone", "pickaxe"),
    "garden_bloom": ("all", "Garden Bloom", "hoe"),
    "choir_stone": ("all", "Choir Stone", "pickaxe"),
    "choir_bricks": ("all", "Choir Bricks", "pickaxe"),
    "choir_crystal": ("all", "Choir Crystal", "pickaxe"),
    "orchard_ash": ("all", "Orchard Ash", "shovel"),
    "marrow_stone": ("all", "Marrow Stone", "pickaxe"),
    "marrow_lantern": ("all", "Marrow Lantern", "pickaxe"),
    "hush_stone": ("all", "Hush Stone", "pickaxe"),
    "hush_tiles": ("all", "Hush Tiles", "pickaxe"),
    "hush_lamp": ("all", "Hush Lamp", "pickaxe"),
    "foundry_plate": ("all", "Foundry Plate", "pickaxe"),
    "foundry_grate": ("all", "Foundry Grate", "pickaxe"),
    "foundry_lamp": ("all", "Foundry Lamp", "pickaxe"),
    "brine_stone": ("all", "Brine Stone", "pickaxe"),
    "tide_coral": ("all", "Tide Coral", "pickaxe"),
    "stratum_cloud": ("all", "Dreaming Cloud", "shovel"),
    "dream_tile": ("all", "Dream Tile", "pickaxe"),
    "dream_glow": ("all", "Dream Glow", "pickaxe"),
    "court_stone": ("all", "Court Stone", "pickaxe"),
    "court_tile": ("all", "Court Tile", "pickaxe"),
    "court_brazier": ("column", "Court Brazier", "pickaxe"),
    "seed_shell": ("all", "Seed Shell", "pickaxe"),
    "seed_vein": ("all", "Seed Vein", "pickaxe"),
    "seed_core": ("all", "Seed Core", None),
}


def block_assets():
    for name, (kind, english, tool) in BLOCKS.items():
        bs = f"{RES}/assets/awaken/blockstates/{name}.json"
        model = f"{RES}/assets/awaken/models/block/{name}.json"
        item = f"{RES}/assets/awaken/items/{name}.json"
        write(item, {"model": {"type": "minecraft:model", "model": f"awaken:block/{name}"}})
        write(f"{RES}/data/awaken/loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"condition": {"type": "minecraft:survives_explosion"},
                       "entries": [{"type": "minecraft:item", "name": f"awaken:{name}"}], "rolls": 1}],
            "random_sequence": f"awaken:blocks/{name}"})
        if kind == "all":
            write(bs, {"variants": {"": {"model": f"awaken:block/{name}"}}})
            write(model, {"parent": "minecraft:block/cube_all", "textures": {"all": f"awaken:block/{name}"}})
        elif kind == "column":
            write(bs, {"variants": {"": {"model": f"awaken:block/{name}"}}})
            write(model, {"parent": "minecraft:block/cube_column",
                          "textures": {"end": f"awaken:block/{name}_top", "side": f"awaken:block/{name}_side"}})
        elif kind == "lift":
            write(bs, {"variants": {"": {"model": f"awaken:block/{name}"}}})
            write(model, {"parent": "minecraft:block/cube_bottom_top",
                          "textures": {"top": f"awaken:block/{name}_top", "bottom": "awaken:block/well_stone",
                                       "side": f"awaken:block/{name}_side"}})
        elif kind == "waystone":
            write(bs, {"variants": {"lit=false": {"model": "awaken:block/waystone"},
                                    "lit=true": {"model": "awaken:block/waystone_lit"}}})
            for suffix in ("", "_lit"):
                write(f"{RES}/assets/awaken/models/block/waystone{suffix}.json", pillar_model(
                    f"awaken:block/waystone_side{suffix}", f"awaken:block/waystone_top{suffix}", 3, 13, 16))
        elif kind == "conduit":
            write(bs, {"variants": {"lit=false": {"model": "awaken:block/conduit"},
                                    "lit=true": {"model": "awaken:block/conduit_lit"}}})
            for suffix in ("", "_lit"):
                write(f"{RES}/assets/awaken/models/block/conduit{suffix}.json", pillar_model(
                    f"awaken:block/conduit_side{suffix}", f"awaken:block/conduit_top{suffix}", 2, 14, 14))
        elif kind == "pit":
            write(bs, {"variants": {"": {"model": "awaken:block/descent_pit"}}})
            write(model, {
                "parent": "minecraft:block/block",
                "textures": {"particle": "awaken:block/descent_pit", "pit": "awaken:block/descent_pit"},
                "elements": [{"from": [0, 0, 0], "to": [16, 12, 16],
                              "faces": {f: {"texture": "#pit", **({"cullface": f} if f != "up" else {})}
                                        for f in ("down", "up", "north", "south", "west", "east")}}]})


def pillar_model(side, top, lo, hi, height):
    faces = {}
    for f in ("north", "south", "west", "east"):
        faces[f] = {"uv": [lo, 16 - height, hi, 16], "texture": "#side"}
    faces["up"] = {"uv": [lo, lo, hi, hi], "texture": "#top"}
    faces["down"] = {"uv": [lo, lo, hi, hi], "texture": "#top", "cullface": "down"}
    return {"parent": "minecraft:block/block",
            "textures": {"particle": side, "side": side, "top": top},
            "elements": [{"from": [lo, 0, lo], "to": [hi, height, hi], "faces": faces}]}


# ---------------------------------------------------------------------------------------------------------------------
# Strata look and sound. fog, water, particle(s), music, mood, additions, ambient light, fog end distance
STRATA = {
    1: dict(name="The Rim", fog="#1c2a1d", water="#3b6f4a", wfog="#0e2416", light=0.18, tint="#121d10", fog_end=88,
            particles=[("minecraft:spore_blossom_air", 0.0025)], music="minecraft:music.overworld.lush_caves",
            mood="minecraft:ambient.cave", additions=None),
    2: dict(name="The Hanging Gardens", fog="#25382c", water="#3f8f6a", wfog="#0f2a1e", light=0.2, tint="#14221a", fog_end=96,
            particles=[("minecraft:spore_blossom_air", 0.008)], music="minecraft:music.overworld.flower_forest",
            mood="minecraft:ambient.cave", additions=("awaken:descent.wind", 0.003)),
    3: dict(name="The Drowned Choir", fog="#0d2433", water="#2a6f9a", wfog="#07202f", light=0.12, tint="#08182a", fog_end=64,
            particles=[("minecraft:dripping_water", 0.012)], music="minecraft:music.under_water",
            mood="minecraft:ambient.underwater.loop.additions.rare", additions=("awaken:descent.choir", 0.0015)),
    4: dict(name="The Bone Orchard", fog="#2b2620", water="#5a5848", wfog="#1d1a14", light=0.1, tint="#1d1810", fog_end=72,
            particles=[("minecraft:white_ash", 0.02)], music="minecraft:music.nether.soul_sand_valley",
            mood="minecraft:ambient.soul_sand_valley.mood", additions=("minecraft:ambient.soul_sand_valley.additions", 0.006)),
    5: dict(name="The Silent Halls", fog="#101216", water="#33404f", wfog="#0a0d12", light=0.04, tint="#08090c", fog_end=44,
            particles=[("minecraft:ash", 0.002)], music="minecraft:music.overworld.deep_dark",
            mood="minecraft:ambient.cave", additions=("awaken:descent.listener", 0.0012)),
    6: dict(name="The Precursor Foundry", fog="#22180f", water="#5c4a2f", wfog="#1a1208", light=0.1, tint="#1c1206", fog_end=80,
            particles=[("minecraft:electric_spark", 0.002), ("minecraft:smoke", 0.003)],
            music="minecraft:music.nether.basalt_deltas", mood="minecraft:ambient.basalt_deltas.mood",
            additions=("minecraft:ambient.basalt_deltas.additions", 0.005)),
    7: dict(name="The Inverted Sea", fog="#0f2a3a", water="#2f86b8", wfog="#0b2433", light=0.15, tint="#0a1c28", fog_end=120,
            particles=[("minecraft:dripping_water", 0.02)], music="minecraft:music.under_water",
            mood="minecraft:ambient.underwater.loop.additions", additions=("minecraft:ambient.underwater.loop.additions.rare", 0.004)),
    8: dict(name="The Dreaming Stratum", fog="#3a2340", water="#a06ac0", wfog="#2a1832", light=0.22, tint="#24142a", fog_end=72,
            particles=[("minecraft:end_rod", 0.003)], music="minecraft:music.overworld.cherry_grove",
            mood="minecraft:ambient.cave", additions=("awaken:descent.room_shift", 0.001)),
    9: dict(name="The Hollow Court", fog="#120c18", water="#3c2a50", wfog="#0c0812", light=0.06, tint="#0e0814", fog_end=64,
            particles=[("minecraft:ash", 0.006), ("minecraft:soul_fire_flame", 0.0006)],
            music="minecraft:music.nether.crimson_forest", mood="minecraft:ambient.crimson_forest.mood",
            additions=("minecraft:ambient.crimson_forest.additions", 0.004)),
    10: dict(name="The Seed", fog="#0c1830", water="#3c86cc", wfog="#081226", light=0.08, tint="#06122a", fog_end=128,
             particles=[("minecraft:end_rod", 0.006)], music="minecraft:music.end",
             mood="minecraft:ambient.cave", additions=("awaken:descent.seed", 0.003)),
}


def bed_rule():
    return {"can_sleep": "always", "can_set_spawn": "never"}


def dimension_type(light, tint):
    return {
        "has_fixed_time": True, "has_skylight": False, "has_ceiling": True, "has_ender_dragon_fight": False,
        "coordinate_scale": 1.0, "min_y": 0, "height": 256, "logical_height": 256,
        "infiniburn": "#minecraft:infiniburn_overworld", "ambient_light": light,
        "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0, "skybox": "none",
        "attributes": {
            "minecraft:visual/ambient_light_color": tint,
            "minecraft:gameplay/bed_rule": bed_rule(),
            "minecraft:gameplay/straw_bed_rule": bed_rule(),
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/nether_portal_spawns_piglin": False,
        },
    }


def biome(s):
    attrs = {
        "minecraft:visual/fog_color": s["fog"],
        "minecraft:visual/sky_color": s["fog"],
        "minecraft:visual/water_fog_color": s["wfog"],
        "minecraft:visual/fog_start_distance": 0.0,
        "minecraft:visual/fog_end_distance": float(s["fog_end"]),
        "minecraft:visual/ambient_particles": {"modifier": "append", "argument": [
            {"particle": {"type": p}, "probability": prob} for p, prob in s["particles"]]},
        "minecraft:audio/background_music": {"default": {"sound": s["music"], "min_delay": 6000, "max_delay": 18000}},
    }
    sounds = {"mood": {"sound": s["mood"], "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0}}
    if s["additions"]:
        sounds["additions"] = {"sound": s["additions"][0], "tick_chance": s["additions"][1]}
    attrs["minecraft:audio/ambient_sounds"] = sounds
    return {"has_precipitation": False, "temperature": 0.5, "downfall": 0.0,
            "effects": {"water_color": s["water"]}, "attributes": attrs, "carvers": [], "features": []}


def worldgen():
    for n, s in STRATA.items():
        write(f"{RES}/data/awaken/dimension_type/stratum_{n}.json", dimension_type(s["light"], s["tint"]))
        write(f"{RES}/data/awaken/worldgen/biome/stratum_{n}.json", biome(s))
        write(f"{RES}/data/awaken/dimension/stratum_{n}.json", {
            "type": f"awaken:stratum_{n}",
            "generator": {"type": "awaken:stratum", "stratum": n,
                          "biome_source": {"type": "minecraft:fixed", "biome": f"awaken:stratum_{n}"}}})
    write(f"{RES}/data/awaken/dimension_type/endless.json", dimension_type(0.1, "#0a0a14"))
    write(f"{RES}/data/awaken/dimension/endless.json", {
        "type": "awaken:endless",
        "generator": {"type": "awaken:stratum", "stratum": 0,
                      "biome_source": {"type": "awaken:endless", "biomes": [f"awaken:stratum_{n}" for n in range(1, 10)]}}})


# ---------------------------------------------------------------------------------------------------------------------
# Sounds (vanilla files with pitch/volume changes)
def snd(name, pitch=1.0, volume=1.0, **kw):
    d = {"name": "minecraft:" + name, "pitch": pitch, "volume": volume}
    d.update(kw)
    return d


SOUNDS = {
    "descent.well": ("The Well whispers", [snd("ambient/nether/soulsand_valley/whisper1", 0.6, 0.5),
                                           snd("ambient/cave/cave13", 0.6, 0.5)]),
    "descent.drop": ("You fall into the dark", [snd("portal/travel", 1.6, 0.35), snd("mob/phantom/swoop1", 0.6, 0.8)]),
    "descent.arrive": ("You arrive", [snd("block/respawn_anchor/set_spawn1", 0.7, 0.8)]),
    "descent.sealed": ("The way is sealed", [snd("block/vault/insert_fail", 0.6, 1.0)]),
    "descent.waystone_light": ("Waystone lit", [snd("block/beacon/activate", 1.2, 0.9), snd("block/respawn_anchor/charge1", 1.0, 0.7)]),
    "descent.waystone_rest": ("Waystone hums", [snd("block/respawn_anchor/ambient1", 1.4, 0.8)]),
    "descent.wind": ("Wind howls", [snd(f"ambient/nether/soulsand_valley/wind{i}", 0.8, 1.0) for i in range(1, 5)]),
    "descent.choir": ("The Choir sings", [snd("mob/allay/idle_without_item1", 0.5, 1.0), snd("mob/allay/idle_without_item2", 0.45, 1.0),
                                          snd("block/amethyst/resonate1", 0.5, 1.0)]),
    "descent.flood": ("Water rises", [snd("block/bubble_column/upwards_ambient1", 0.6, 1.0), snd("ambient/underwater/additions/bubbles1", 0.7, 1.0)]),
    "descent.rise": ("The dead rise", [snd("mob/drowned/convert1", 0.6, 1.0), snd("mob/drowned/convert2", 0.6, 1.0)]),
    "descent.listener": ("Something listens", [snd("mob/warden/listening_1", 1.3, 0.9), snd("mob/warden/listening_2", 1.3, 0.9),
                                               snd("mob/warden/sniff_1", 1.4, 0.8)]),
    "descent.conduit_on": ("Conduit wakes", [snd("block/conduit/activate", 1.4, 0.8)]),
    "descent.conduit_off": ("Conduit sleeps", [snd("block/conduit/deactivate", 1.3, 0.8)]),
    "descent.foundry_open": ("The Foundry opens", [snd("block/vault/activate", 0.6, 1.0), snd("block/beacon/power1", 0.6, 1.0)]),
    "descent.tide": ("The tide turns", [snd("item/trident/riptide1", 0.5, 0.9), snd("block/conduit/ambient", 0.5, 1.0)]),
    "descent.room_shift": ("A room shifts", [snd("block/chorus_flower/grow1", 0.5, 0.6), snd("block/chorus_flower/grow2", 0.45, 0.6)]),
    "descent.shadow": ("Your shadow", [snd("mob/vex/charge1", 0.5, 1.0), snd("mob/vex/charge2", 0.5, 1.0)]),
    "descent.grave": ("A Grave Echo", [snd("enchant/soulspeed/soulspeed1", 0.6, 1.0), snd("ambient/nether/soulsand_valley/whisper1", 0.9, 0.6)]),
    "descent.grave_recover": ("Grave Echo recovered", [snd("block/respawn_anchor/charge2", 1.4, 0.9)]),
    "descent.strain": ("The Strain of climbing", [snd("mob/warden/heartbeat_1", 1.2, 1.0), snd("mob/warden/heartbeat_2", 1.2, 1.0)]),
    "descent.seed": ("The Seed beats", [snd("mob/warden/heartbeat_1", 0.5, 1.0, attenuation_distance=64),
                                       snd("mob/warden/heartbeat_3", 0.5, 1.0, attenuation_distance=64)]),
}


def sounds_and_lang():
    sounds = {}
    lang = {}
    for key, (subtitle, files) in SOUNDS.items():
        sounds[key] = {"subtitle": f"subtitles.awaken.{key}", "sounds": files}
        lang[f"subtitles.awaken.{key}"] = subtitle
    write(f"{MERGE}/assets/awaken/sounds.json", sounds)
    for name, (_, english, _) in BLOCKS.items():
        lang[f"block.awaken.{name}"] = english
    for n, s in STRATA.items():
        lang[f"biome.awaken.stratum_{n}"] = s["name"]
    lang["entity.awaken.grave_echo"] = "Grave Echo"
    lang["entity.awaken.shadow_self"] = "Your Shadow"
    lang["screen.awaken.descent_travel"] = "Waystones"
    lang["hud.awaken.deep_pack"] = "Deep Pack"
    write(f"{MERGE}/assets/awaken/lang/en_us.json", lang)
    tags = {"pickaxe": [], "shovel": [], "hoe": []}
    for name, (_, _, tool) in BLOCKS.items():
        if tool:
            tags[tool].append(f"awaken:{name}")
    for tool, values in tags.items():
        write(f"{MERGE}/data/minecraft/tags/block/mineable/{tool}.json", {"values": values})


if __name__ == "__main__":
    block_assets()
    worldgen()
    sounds_and_lang()
    print("descent assets written")
