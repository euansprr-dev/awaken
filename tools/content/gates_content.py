"""Generates the gates module's data and asset JSON: the gate dimension, its 16 biomes, blockstates, models, client item
definitions, loot tables, lang and sounds. Edit here, then run: python3 tools/content/gates_content.py
Theme ids must match dev.awaken.gates.content.Theme."""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "../../src/main/resources")
MERGE = os.path.join(HERE, "../../src/main/merge/gates")
DATA = os.path.join(RES, "data/awaken")
ASSETS = os.path.join(RES, "assets/awaken")


def merged(path, generated):
    """The generated entries over what's already in the file: other builders add keys here (the gate hum, its subtitle)
    and a regeneration must keep them."""
    try:
        with open(path) as f:
            existing = json.load(f)
    except (OSError, ValueError):
        existing = {}
    existing.update(generated)
    return existing


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def particles(*pairs):
    return {"modifier": "append",
            "argument": [{"particle": {"type": "minecraft:" + p}, "probability": prob} for p, prob in pairs]}


def music(sound, lo=12000, hi=24000):
    return {"default": {"sound": sound, "min_delay": lo, "max_delay": hi}}


def ambient(loop=None, additions=None, chance=0.006, mood="minecraft:ambient.cave"):
    a = {}
    if loop:
        a["loop"] = loop
    if mood:
        a["mood"] = {"sound": mood, "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0}
    if additions:
        a["additions"] = {"sound": additions, "tick_chance": chance}
    return a


NO_SPAWNS = {"modifier": "overlay", "argument": {"spawn_costs": {}, "spawns_by_category": {
    "ambient": [], "axolotls": [], "creature": [], "misc": [], "monster": [], "underground_water_creature": [],
    "water_ambient": [], "water_creature": []}}}

# id: (display, fog colour, fog start, fog end, particles, ambient, music, water colour)
THEMES = {
    "bell_crypt": ("Bell Crypt", "#1c1a24", 4.0, 52.0, particles(("white_ash", 0.004)),
                   ambient(additions="awaken:gate.ambient.bell", chance=0.004), music("minecraft:music.overworld.deep_dark"), "#3f4c5f"),
    "fungal_hollow": ("Fungal Hollow", "#3b2a44", 2.0, 44.0, particles(("spore_blossom_air", 0.01), ("crimson_spore", 0.004)),
                      ambient(loop="minecraft:ambient.crimson_forest.loop", additions="minecraft:ambient.crimson_forest.additions", chance=0.008),
                      music("minecraft:music.overworld.lush_caves"), "#5a7a4a"),
    "ember_mines": ("Ember Mines", "#3a1a10", 6.0, 64.0, particles(("ash", 0.008), ("small_flame", 0.0008)),
                    ambient(loop="minecraft:ambient.basalt_deltas.loop", additions="minecraft:ambient.basalt_deltas.additions", chance=0.01),
                    music("minecraft:music.nether.basalt_deltas"), "#6a3a20"),
    "drowned_archive": ("Drowned Archive", "#10303a", 4.0, 48.0, particles(("underwater", 0.02), ("dripping_water", 0.002)),
                        ambient(loop="minecraft:ambient.underwater.loop", additions="minecraft:ambient.underwater.loop.additions", chance=0.01),
                        music("minecraft:music.under_water"), "#1d6a7a"),
    "clockwork_foundry": ("Clockwork Foundry", "#3a2e22", 6.0, 64.0, particles(("white_smoke", 0.002), ("electric_spark", 0.001)),
                          ambient(additions="awaken:gate.ambient.gears", chance=0.008), music("minecraft:music.overworld.dripstone_caves"), "#4a6a5a"),
    "glass_desert": ("Glass Desert", "#c9a86a", 10.0, 80.0, particles(("white_ash", 0.012)),
                     ambient(additions="awaken:gate.ambient.glass", chance=0.005, mood=None), music("minecraft:music.overworld.desert"), "#32a598"),
    "hive": ("Hive", "#4a3a10", 4.0, 48.0, particles(("falling_honey", 0.004), ("dripping_honey", 0.003)),
             ambient(additions="awaken:gate.ambient.buzz", chance=0.012), music("minecraft:music.overworld.flower_forest"), "#b0882a"),
    "overgrown_temple": ("Overgrown Temple", "#2b3a22", 6.0, 60.0, particles(("firefly", 0.006), ("spore_blossom_air", 0.004)),
                         ambient(additions="awaken:gate.ambient.chant", chance=0.002), music("minecraft:music.overworld.jungle"), "#3a7a5a"),
    "lantern_night_market": ("Lantern Night Market", "#2a1f3a", 10.0, 90.0, particles(("firefly", 0.01)),
                             ambient(additions="awaken:gate.ambient.market", chance=0.012, mood=None), music("minecraft:music.overworld.cherry_grove"), "#3f76e4"),
    "frost_monastery": ("Frost Monastery", "#cfe3ff", 6.0, 60.0, particles(("snowflake", 0.02)),
                        ambient(additions="awaken:gate.ambient.chant", chance=0.004, mood=None), music("minecraft:music.overworld.frozen_peaks"), "#3d57d6"),
    "mirror_palace": ("Mirror Palace", "#d8d8f0", 8.0, 72.0, particles(("end_rod", 0.002)),
                      ambient(additions="awaken:gate.ambient.glass", chance=0.006, mood=None), music("minecraft:music.end"), "#9aa4ff"),
    "precursor_lab": ("Precursor Lab", "#0c1e2a", 6.0, 64.0, particles(("electric_spark", 0.003), ("glow", 0.002)),
                      ambient(additions="awaken:gate.ambient.lab", chance=0.006), music("minecraft:music.end"), "#2a7aa0"),
    "nightmare": ("Nightmare", "#1a0a24", 3.0, 40.0, particles(("reverse_portal", 0.006), ("soul", 0.002)),
                  ambient(loop="minecraft:ambient.soul_sand_valley.loop", additions="minecraft:ambient.soul_sand_valley.additions", chance=0.0111,
                          mood="minecraft:ambient.soul_sand_valley.mood"), music("minecraft:music.overworld.deep_dark"), "#5a2a7a"),
    "sky_archipelago": ("Sky Archipelago", "#bfe0ff", 12.0, 96.0, particles(("cloud", 0.002), ("white_ash", 0.004)),
                        ambient(additions="awaken:gate.ambient.wind", chance=0.006, mood=None), music("minecraft:music.overworld.meadow"), "#4a9ff0"),
    "abyssal_shrine": ("Abyssal Shrine", "#05040a", 2.0, 36.0, None, None, None, "#0a0a14"),
}


def biome(fog, start, end, parts, amb, mus, water):
    attrs = {
        "minecraft:visual/fog_color": fog,
        "minecraft:visual/sky_color": fog,
        "minecraft:visual/fog_start_distance": start,
        "minecraft:visual/fog_end_distance": end,
        "minecraft:visual/water_fog_color": "#050f1a",
        "minecraft:gameplay/natural_mob_spawns": NO_SPAWNS,
    }
    if parts:
        attrs["minecraft:visual/ambient_particles"] = parts
    if amb:
        attrs["minecraft:audio/ambient_sounds"] = amb
    if mus:
        attrs["minecraft:audio/background_music"] = mus
    else:
        attrs["minecraft:audio/music_volume"] = 0.0  # "Nothing. No sound at all."
    return {"has_precipitation": False, "temperature": 0.5, "downfall": 0.0,
            "effects": {"water_color": water}, "attributes": attrs, "carvers": [], "features": []}


def worldgen(lang):
    biomes = []
    for tid, (name, fog, start, end, parts, amb, mus, water) in THEMES.items():
        # each theme's own track (its sound event mixes in the old vanilla music 3:1; docs/modules/audio.md)
        mus = music("awaken:music.gate." + tid, 3600, 12000) if mus else None
        write(os.path.join(DATA, "worldgen/biome/gate_%s.json" % tid), biome(fog, start, end, parts, amb, mus, water))
        biomes.append("awaken:gate_" + tid)
        lang["biome.awaken.gate_" + tid] = name
    write(os.path.join(DATA, "worldgen/biome/gate_void.json"), biome("#000000", 0.0, 32.0, None, None, None, "#3f76e4"))
    lang["biome.awaken.gate_void"] = "Between Gates"
    write(os.path.join(DATA, "dimension_type/gates.json"), {
        "has_fixed_time": True, "has_skylight": False, "has_ceiling": True, "has_ender_dragon_fight": False,
        "coordinate_scale": 1.0, "min_y": 0, "height": 256, "logical_height": 256,
        "infiniburn": "#minecraft:infiniburn_overworld", "ambient_light": 0.0,
        "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0, "skybox": "none",
        "attributes": {
            "minecraft:visual/fog_color": "#0b0a10",
            "minecraft:visual/sky_color": "#000000",
            "minecraft:visual/ambient_light_color": "#000000",
            "minecraft:gameplay/bed_rule": {"can_sleep": "never", "can_set_spawn": "never",
                                            "error_message": {"translate": "awaken.gate.no_sleep"}},
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/nether_portal_spawns_piglin": False,
        },
    })
    write(os.path.join(DATA, "dimension/gates.json"), {
        "type": "awaken:gates",
        "generator": {"type": "awaken:gate_cells",
                      "biome_source": {"type": "awaken:gate_cells", "biomes": biomes, "fallback": "awaken:gate_void"}},
    })


# ---- blocks ---------------------------------------------------------------------------------------------------------

RANKS = ["e", "d", "c", "b", "a", "s", "sovereign"]


def cube(name, tex=None):
    write(os.path.join(ASSETS, "models/block/%s.json" % name), {"parent": "minecraft:block/cube_all", "textures": {"all": "awaken:block/" + (tex or name)}})


def bottom_top(name, side, top, bottom=None):
    write(os.path.join(ASSETS, "models/block/%s.json" % name), {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "side": "awaken:block/" + side, "top": "awaken:block/" + top, "bottom": "awaken:block/" + (bottom or side)}})


def single(name, model=None):
    write(os.path.join(ASSETS, "blockstates/%s.json" % name), {"variants": {"": {"model": "awaken:block/" + (model or name)}}})


def item(name, model):
    write(os.path.join(ASSETS, "items/%s.json" % name), {"model": {"type": "minecraft:model", "model": model}})


def loot(name):
    write(os.path.join(DATA, "loot_table/blocks/%s.json" % name), {
        "type": "minecraft:block",
        "pools": [{"condition": {"type": "minecraft:survives_explosion"}, "entries": [{"type": "minecraft:item", "name": "awaken:" + name}], "rolls": 1}],
        "random_sequence": "awaken:blocks/" + name})


def blocks(lang):
    names = {
        "gate_frame": "Gate Frame", "gate_frame_cracked": "Cracked Gate Frame", "gate_core": "Gate Core",
        "gate_exit": "Gate Exit", "gate_rift": "Rift", "gate_inner": "Inner Gate", "gate_return": "Way Back",
        "rest_shrine": "Rest Shrine", "gate_seal": "Seal", "gate_cache": "Cache", "gate_bars": "Rune Bars",
        "gate_rune": "Rune", "gate_entrance": "Entrance Sigil", "lost_lantern": "Lost Lantern", "gate_updraft": "Updraft",
    }
    for k, v in names.items():
        lang["block.awaken." + k] = v
    cube("gate_frame")
    cube("gate_frame_cracked")
    single("gate_frame")
    single("gate_frame_cracked")
    # core: one animated texture per rank, plus the broken one
    variants = {}
    for i, r in enumerate(RANKS):
        cube("gate_core_" + r)
        variants["broken=false,rank=%d" % i] = {"model": "awaken:block/gate_core_" + r}
        variants["broken=true,rank=%d" % i] = {"model": "awaken:block/gate_core_broken"}
    cube("gate_core_broken")
    write(os.path.join(ASSETS, "blockstates/gate_core.json"), {"variants": variants})
    for p in ["gate_exit", "gate_rift", "gate_inner", "gate_return"]:
        cube(p)
        single(p)
    bottom_top("rest_shrine", "rest_shrine_side", "rest_shrine_top", "gate_frame")
    single("rest_shrine")
    bottom_top("gate_seal", "gate_seal_side", "gate_seal_top", "gate_frame")
    single("gate_seal")
    bottom_top("gate_cache", "gate_cache_side", "gate_cache_top")
    bottom_top("gate_cache_open", "gate_cache_side", "gate_cache_top_open")
    write(os.path.join(ASSETS, "blockstates/gate_cache.json"), {"variants": {
        "open=false": {"model": "awaken:block/gate_cache"}, "open=true": {"model": "awaken:block/gate_cache_open"}}})
    cube("gate_bars")
    single("gate_bars")
    cube("gate_rune_off")
    cube("gate_rune_on")
    write(os.path.join(ASSETS, "blockstates/gate_rune.json"), {"variants": {
        "lit=false": {"model": "awaken:block/gate_rune_off"}, "lit=true": {"model": "awaken:block/gate_rune_on"}}})
    bottom_top("gate_entrance", "gate_frame", "gate_entrance_top")
    single("gate_entrance")
    bottom_top("gate_updraft", "gate_updraft_side", "gate_updraft_top")
    single("gate_updraft")
    # lost lantern: a small lantern box
    write(os.path.join(ASSETS, "models/block/lost_lantern.json"), {
        "parent": "minecraft:block/block", "ambientocclusion": False,
        "textures": {"particle": "awaken:block/lost_lantern", "all": "awaken:block/lost_lantern"},
        "elements": [
            {"from": [5, 0, 5], "to": [11, 7, 11], "light_emission": 12, "faces": {
                d: {"uv": [0, 2, 6, 9] if d not in ("up", "down") else [0, 9, 6, 15], "texture": "#all"} for d in ("north", "south", "east", "west", "up", "down")}},
            {"from": [6, 7, 6], "to": [10, 9, 10], "faces": {
                d: {"uv": [1, 0, 5, 2], "texture": "#all"} for d in ("north", "south", "east", "west", "up", "down")}},
        ]})
    single("lost_lantern")
    item_blocks = {"gate_frame": "gate_frame", "gate_frame_cracked": "gate_frame_cracked", "rest_shrine": "rest_shrine",
                   "gate_seal": "gate_seal", "gate_cache": "gate_cache", "gate_bars": "gate_bars", "gate_rune": "gate_rune_on",
                   "gate_entrance": "gate_entrance", "lost_lantern": "lost_lantern", "gate_updraft": "gate_updraft"}
    for name, model in item_blocks.items():
        item(name, "awaken:block/" + model)
        loot(name)


def entities(lang):
    lang.update({
        "entity.awaken.gate_shade": "Shadow", "entity.awaken.echo_ghost": "Echo",
        "entity.awaken.hollow_follower": "Something", "entity.awaken.gate_hoarder": "Hoarder",
    })


def sounds(lang):
    s = {}

    def add(name, subtitle, *entries):
        s[name] = {"subtitle": "subtitles.awaken." + name, "sounds": [e if isinstance(e, dict) else {"name": e} for e in entries]}
        lang["subtitles.awaken." + name] = subtitle

    add("gate.enter", "You step through a gate", {"name": "minecraft:block/beacon/activate", "pitch": 0.6},
        {"name": "minecraft:block/end_portal/endportal", "pitch": 1.4, "volume": 0.4})
    add("gate.exit", "You come out of a gate", {"name": "minecraft:block/beacon/deactivate", "pitch": 0.8})
    add("gate.shrine", "A Rest Shrine hums", {"name": "minecraft:block/respawn_anchor/charge1", "pitch": 1.2},
        {"name": "minecraft:block/amethyst/resonate1", "pitch": 0.8})
    add("gate.seal", "The Seal breaks", {"name": "minecraft:block/vault/open_shutter", "pitch": 0.6},
        {"name": "minecraft:block/beacon/deactivate", "pitch": 0.5})
    add("gate.cache", "A cache opens", {"name": "minecraft:block/chest/open", "pitch": 0.8}, {"name": "minecraft:block/vault/eject1", "pitch": 1.0})
    add("gate.break", "A gate breaks", {"name": "minecraft:block/trial_spawner/ominous_activate", "pitch": 0.5, "volume": 1.0, "attenuation_distance": 96},
        {"name": "minecraft:block/beacon/deactivate", "pitch": 0.4, "attenuation_distance": 96})
    add("gate.pressure", "The gate sends more", {"name": "minecraft:block/trial_spawner/spawn1", "pitch": 0.7},
        {"name": "minecraft:block/trial_spawner/spawn2", "pitch": 0.7})
    add("gate.warden", "The Warden arrives", {"name": "minecraft:block/trial_spawner/ominous_activate", "pitch": 0.7, "attenuation_distance": 64})
    add("gate.bars", "Rune bars shift", {"name": "minecraft:block/chain/break1", "pitch": 0.6}, {"name": "minecraft:block/vault/insert", "pitch": 0.6})
    add("gate.rune", "A rune turns", {"name": "minecraft:block/amethyst/resonate2", "pitch": 1.0})
    add("gate.solved", "The runes agree", {"name": "minecraft:block/vault/activate", "pitch": 1.0},
        {"name": "minecraft:block/amethyst/shimmer", "pitch": 1.2})
    add("gate.alarm", "An alarm bell", {"name": "minecraft:block/bell/bell_use01", "pitch": 1.4, "attenuation_distance": 48})
    add("gate.collapse", "Stone falls", {"name": "minecraft:block/trial_spawner/break1", "pitch": 0.5}, {"name": "minecraft:block/vault/break1", "pitch": 0.5})
    add("gate.cleared", "The gate falls quiet", {"name": "minecraft:block/beacon/power1", "pitch": 0.8}, {"name": "minecraft:block/respawn_anchor/set_spawn1", "pitch": 1.2})
    # heard from 48 blocks: the hum swells as you approach a gate (structures)
    add("gate.hum", "A gate hums", {"name": "minecraft:block/beacon/ambient", "pitch": 0.7, "attenuation_distance": 48})
    add("gate.ambient.bell", "Bells under the floor", {"name": "minecraft:block/bell/resonate", "pitch": 0.5, "volume": 0.4},
        {"name": "minecraft:block/bell/bell_use02", "pitch": 0.45, "volume": 0.3})
    add("gate.ambient.gears", "Gears grind", {"name": "minecraft:block/grindstone/grindstone1", "pitch": 0.6, "volume": 0.4},
        {"name": "minecraft:block/grindstone/grindstone3", "pitch": 0.5, "volume": 0.3})
    add("gate.ambient.chant", "Distant chanting", {"name": "minecraft:block/amethyst/resonate3", "pitch": 0.5, "volume": 0.4},
        {"name": "minecraft:block/conduit/ambient", "pitch": 0.6, "volume": 0.3})
    add("gate.ambient.buzz", "A deep hum", {"name": "minecraft:block/beacon/ambient", "pitch": 1.6, "volume": 0.3})
    add("gate.ambient.market", "Music and haggling", {"name": "minecraft:block/amethyst/resonate4", "pitch": 1.3, "volume": 0.3},
        {"name": "minecraft:block/bell/bell_use02", "pitch": 1.8, "volume": 0.15})
    add("gate.ambient.lab", "A voice counting down", {"name": "minecraft:block/conduit/short1", "pitch": 0.7, "volume": 0.3},
        {"name": "minecraft:block/conduit/short5", "pitch": 0.9, "volume": 0.3})
    add("gate.ambient.glass", "Glass rings", {"name": "minecraft:block/amethyst/shimmer", "pitch": 0.8, "volume": 0.4})
    add("gate.ambient.wind", "Wind far below", {"name": "minecraft:ambient/nether/soulsand_valley/wind1", "pitch": 1.2, "volume": 0.4},
        {"name": "minecraft:ambient/nether/soulsand_valley/wind3", "pitch": 1.1, "volume": 0.4})
    write(os.path.join(MERGE, "assets/awaken/sounds.json"), merged(os.path.join(MERGE, "assets/awaken/sounds.json"), s))


def gui(lang):
    lang.update({
        "awaken.gate.no_sleep": "There is no rest inside a gate except at its Shrines.",
        "screen.awaken.satchel": "Satchel",
        "screen.awaken.satchel.hint": "Carried, not yours yet. Extract at a Rest Shrine or defeat the Lord to keep it.",
        "hud.awaken.gate.floor": "Floor %s / %s",
        "status.awaken.gate_map": "Gate Map",
        "status.awaken.gate_run": "This run",
        "key.awaken.satchel": "Open Satchel",
    })


def main():
    lang = {}
    worldgen(lang)
    blocks(lang)
    entities(lang)
    sounds(lang)
    gui(lang)
    write(os.path.join(MERGE, "assets/awaken/lang/en_us.json"),
          dict(sorted(merged(os.path.join(MERGE, "assets/awaken/lang/en_us.json"), lang).items())))
    print("gates content written:", len(lang), "lang keys")


if __name__ == "__main__":
    main()
