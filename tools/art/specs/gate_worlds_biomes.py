"""Gate world biomes (SPEC 38.6): one per world and mood, written from the table below, and listed in the gates dimension.

Run: python3 tools/art/specs/gate_worlds_biomes.py
Each biome carries the world's air (fog colour and reach, sky, water), its ambient particles, its sound bed (a loop),
its additions (drips, far tolls, thunder in the stone) and the theme's music. Moods change the air, not the place.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "..", "src", "main", "resources", "data", "awaken")
BIOMES = os.path.join(ROOT, "worldgen", "biome", "gate_world")
DIMENSION = os.path.join(ROOT, "dimension", "gates.json")

# world: fog, sky, water, water fog, fog start, fog end, loop, base particles, additions
WORLDS = {
    "sunken_belfry": ("#2a3140", "#151a24", "#2c3a4f", "#0a1420", 12.0, 140.0, "awaken:gate.world.belfry",
                      [("minecraft:white_ash", 0.003)], ("awaken:gate.world.drip", 0.006)),
    "ossuary_ways": ("#1f1d24", "#121117", "#3a4250", "#0a0e14", 4.0, 56.0, "awaken:gate.world.ossuary",
                     [("minecraft:white_ash", 0.006)], ("awaken:gate.world.drip", 0.004)),
    "bell_shaft": ("#1c2230", "#0e121b", "#2c3a4f", "#0a1420", 10.0, 110.0, "awaken:gate.world.shaft",
                   [("minecraft:white_ash", 0.005)], ("awaken:world.bell_far", 0.003)),
    "field_of_bells": ("#141826", "#0b0e18", "#101828", "#04070d", 24.0, 220.0, "awaken:gate.world.field",
                       [("minecraft:glow", 0.0012), ("minecraft:white_ash", 0.001)], ("awaken:world.bell_far", 0.004)),
    "bellmakers_rest": ("#3a2a20", "#20160f", "#3f4c5f", "#050f1a", 6.0, 48.0, "awaken:gate.world.rest",
                        [("minecraft:ash", 0.002)], ("awaken:gate.world.hum", 0.002)),
}

# mood: fog tint (mixed in), fog end scale, extra particles, additions override
MOODS = {
    "tolling": ("#3a3446", 0.9, [("minecraft:white_ash", 0.006)], ("awaken:gate.world.toll", 0.02)),
    "requiem": ("#4a3528", 1.0, [("minecraft:smoke", 0.0015), ("minecraft:soul", 0.0008)], None),
    "storm": ("#10141c", 0.75, [("minecraft:falling_water", 0.012)], ("awaken:gate.world.storm", 0.012)),
}

# Stage 2 (38.10): the cave themes. Same columns as the Bell Crypt's tables; the honey lake's water is honey.
FUNGAL = {
    "glowing_deep": ("#123432", "#08161a", "#2a8a80", "#06201e", 8.0, 120.0, "awaken:gate.world.hollow_glow",
                     [("minecraft:glow", 0.0016), ("minecraft:warped_spore", 0.004)], ("awaken:gate.world.drip", 0.006)),
    "root_warrens": ("#162426", "#0a1214", "#2a6a70", "#061418", 4.0, 64.0, "awaken:gate.world.hollow_roots",
                     [("minecraft:mycelium", 0.006), ("minecraft:glow", 0.0008)], ("awaken:gate.world.drip", 0.004)),
    "spore_canopy": ("#1a2c34", "#0a141c", "#2a7a80", "#061a1e", 12.0, 150.0, "awaken:gate.world.hollow_glow",
                     [("minecraft:spore_blossom_air", 0.004), ("minecraft:glow", 0.0012)], ("awaken:gate.world.puff", 0.002)),
    "inside_the_bloom": ("#2c1a36", "#140a1c", "#6a3a8a", "#140a1e", 6.0, 96.0, "awaken:gate.world.hollow_bloom",
                         [("minecraft:spore_blossom_air", 0.006), ("minecraft:crimson_spore", 0.004)], ("awaken:gate.world.puff", 0.004)),
    "mycelium_mind": ("#182c2c", "#0a1616", "#3a9a90", "#061a1a", 4.0, 48.0, "awaken:gate.world.hollow_bloom",
                      [("minecraft:glow", 0.003), ("minecraft:mycelium", 0.004)], ("awaken:gate.world.bloom", 0.002)),
}
FUNGAL_MOODS = {
    "bloom": ("#3a2a4a", 0.9, [("minecraft:spore_blossom_air", 0.008)], ("awaken:gate.world.puff", 0.008)),
    "drought": ("#2a2a1e", 1.2, [("minecraft:white_ash", 0.003)], None),
    "migration": ("#1a3a3a", 1.0, [("minecraft:glow", 0.003)], ("awaken:gate.world.bloom", 0.004)),
}
EMBER = {
    "working_mine": ("#2a1a12", "#140a06", "#3a2a1e", "#140804", 8.0, 110.0, "awaken:gate.world.mine_works",
                     [("minecraft:ash", 0.004), ("minecraft:small_flame", 0.0004)], ("awaken:gate.world.bellows", 0.002)),
    "open_chasm": ("#3a1a0e", "#1a0804", "#3a2a1e", "#140804", 14.0, 170.0, "awaken:gate.world.mine_chasm",
                   [("minecraft:ash", 0.006), ("minecraft:lava", 0.0006)], ("awaken:gate.world.tremor", 0.002)),
    "forge_below": ("#4a1e0c", "#200a04", "#3a2a1e", "#140804", 10.0, 130.0, "awaken:gate.world.mine_chasm",
                    [("minecraft:small_flame", 0.002), ("minecraft:falling_dripstone_lava", 0.004)], ("awaken:gate.world.tremor", 0.003)),
    "cooling_sea": ("#3a1608", "#1c0804", "#3a2a1e", "#140804", 16.0, 180.0, "awaken:gate.world.mine_sea",
                    [("minecraft:lava", 0.0008), ("minecraft:white_ash", 0.004)], ("awaken:gate.world.tremor", 0.002)),
    "heart_seam": ("#4a2410", "#200e06", "#3a2a1e", "#140804", 4.0, 48.0, "awaken:gate.world.mine_sea",
                   [("minecraft:small_flame", 0.002), ("minecraft:glow", 0.001)], ("awaken:gate.world.wakening", 0.002)),
}
EMBER_MOODS = {
    "eruption": ("#5a1a08", 0.8, [("minecraft:lava", 0.002), ("minecraft:large_smoke", 0.002)], ("awaken:gate.world.tremor", 0.01)),
    "ashfall": ("#3a3634", 0.6, [("minecraft:ash", 0.02), ("minecraft:white_ash", 0.01)], None),
    "shift_change": ("#3a2614", 1.0, [("minecraft:small_flame", 0.001)], ("awaken:gate.world.bellows", 0.008)),
}
HIVE = {
    "the_comb": ("#3a2a0e", "#1c1406", "#b07a10", "#3a2406", 6.0, 90.0, "awaken:gate.world.hive_comb",
                 [("minecraft:falling_honey", 0.004), ("minecraft:falling_nectar", 0.002)], ("awaken:gate.world.drum", 0.001)),
    "brood_hall": ("#4a3412", "#241a08", "#b07a10", "#3a2406", 12.0, 150.0, "awaken:gate.world.hive_brood",
                   [("minecraft:falling_nectar", 0.003), ("minecraft:white_ash", 0.002)], ("awaken:gate.world.swarm", 0.002)),
    "honey_lake": ("#5a3e14", "#2a1c08", "#d08a14", "#5a3806", 14.0, 170.0, "awaken:gate.world.hive_lake",
                   [("minecraft:falling_honey", 0.006), ("minecraft:glow", 0.0008)], ("awaken:gate.world.drip", 0.004)),
    "the_swarm": ("#3a280c", "#1c1406", "#b07a10", "#3a2406", 5.0, 72.0, "awaken:gate.world.hive_brood",
                  [("minecraft:falling_nectar", 0.006), ("minecraft:white_ash", 0.004)], ("awaken:gate.world.swarm", 0.006)),
    "queens_garden": ("#6a5a2a", "#3a3014", "#c08a20", "#5a3806", 10.0, 110.0, "awaken:gate.world.hive_lake",
                      [("minecraft:falling_spore_blossom", 0.004), ("minecraft:cherry_leaves", 0.002)], ("awaken:gate.world.choir", 0.002)),
}
HIVE_MOODS = {
    "swarming": ("#4a3008", 0.8, [("minecraft:falling_nectar", 0.01)], ("awaken:gate.world.swarm", 0.012)),
    "harvest": ("#6a4a10", 1.1, [("minecraft:falling_honey", 0.01)], None),
    "wax_moon": ("#2a2a3a", 1.0, [("minecraft:glow", 0.002), ("minecraft:white_ash", 0.002)], ("awaken:gate.world.drum", 0.004)),
}
TEMPLE = {
    "jungle_courts": ("#1e3420", "#2a5a34", "#3a8a7a", "#0a2a24", 14.0, 160.0, "awaken:gate.world.temple_courts",
                      [("minecraft:spore_blossom_air", 0.003), ("minecraft:firefly", 0.0008)], ("awaken:gate.world.drip", 0.003)),
    "root_halls": ("#1a2418", "#0e140c", "#3a7a6a", "#0a2420", 6.0, 90.0, "awaken:gate.world.temple_halls",
                   [("minecraft:mycelium", 0.004), ("minecraft:falling_spore_blossom", 0.001)], ("awaken:gate.world.drip", 0.004)),
    "waterfall_temple": ("#1e3a3a", "#2a5a5a", "#3a8aa0", "#0a2a34", 12.0, 160.0, "awaken:gate.world.temple_falls",
                         [("minecraft:falling_water", 0.004), ("minecraft:spore_blossom_air", 0.002)], ("awaken:gate.world.drip", 0.006)),
    "great_tree": ("#24381e", "#2e5a2a", "#3a8a7a", "#0a2a24", 14.0, 170.0, "awaken:gate.world.temple_courts",
                   [("minecraft:falling_spore_blossom", 0.003), ("minecraft:firefly", 0.0012)], ("awaken:gate.world.wakening", 0.001)),
    "sleeping_idol": ("#1a2a20", "#0e1a14", "#3a7a6a", "#0a2420", 6.0, 72.0, "awaken:gate.world.temple_halls",
                      [("minecraft:glow", 0.0016), ("minecraft:mycelium", 0.003)], ("awaken:gate.world.saint", 0.001)),
}
TEMPLE_MOODS = {
    "monsoon": ("#1a2a2e", 0.7, [("minecraft:falling_water", 0.016)], ("awaken:gate.world.rains", 0.01)),
    "fireflies": ("#14241a", 1.1, [("minecraft:firefly", 0.004)], None),
    "overgrowth": ("#2a4a1e", 0.85, [("minecraft:spore_blossom_air", 0.008), ("minecraft:falling_spore_blossom", 0.003)], None),
}

# theme: its worlds, its moods (every world but the secret one has them), its secret world, its water colour if not the table's
THEMES = {
    "bell_crypt": (WORLDS, MOODS, "bellmakers_rest"),
    "fungal_hollow": (FUNGAL, FUNGAL_MOODS, "mycelium_mind"),
    "ember_mines": (EMBER, EMBER_MOODS, "heart_seam"),
    "hive": (HIVE, HIVE_MOODS, "queens_garden"),
    "overgrown_temple": (TEMPLE, TEMPLE_MOODS, "sleeping_idol"),
}


def mix(a, b, t):
    pa = [int(a[i:i + 2], 16) for i in (1, 3, 5)]
    pb = [int(b[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join("%02x" % round(x + (y - x) * t) for x, y in zip(pa, pb))


def biome(fog, sky, water, wfog, start, end, loop, particles, additions, theme="bell_crypt"):
    no_spawns = {k: [] for k in ("ambient", "axolotls", "creature", "misc", "monster", "underground_water_creature",
                                 "water_ambient", "water_creature")}
    return {
        "has_precipitation": False,
        "temperature": 0.5,
        "downfall": 0.0,
        "effects": {"water_color": water},
        "attributes": {
            "minecraft:visual/fog_color": fog,
            "minecraft:visual/sky_color": sky,
            "minecraft:visual/fog_start_distance": start,
            "minecraft:visual/fog_end_distance": end,
            "minecraft:visual/water_fog_color": wfog,
            "minecraft:gameplay/natural_mob_spawns": {"modifier": "overlay",
                                                      "argument": {"spawn_costs": {}, "spawns_by_category": no_spawns}},
            "minecraft:visual/ambient_particles": {"modifier": "append", "argument": [
                {"particle": {"type": p}, "probability": pr} for p, pr in particles]},
            "minecraft:audio/ambient_sounds": {
                "loop": loop,
                "mood": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0},
                "additions": {"sound": additions[0], "tick_chance": additions[1]},
            },
            "minecraft:audio/background_music": {
                "default": {"sound": "awaken:music.gate." + theme, "min_delay": 3600, "max_delay": 12000}},
        },
        "carvers": [],
        "features": [],
    }


def main():
    os.makedirs(BIOMES, exist_ok=True)
    ids = []
    for theme, (worlds, moods, secret) in THEMES.items():
        for w, (fog, sky, water, wfog, start, end, loop, parts, adds) in worlds.items():
            out = {w: biome(fog, sky, water, wfog, start, end, loop, parts, adds, theme)}
            if w != secret:
                for m, (tint, scale, extra, madds) in moods.items():
                    out[w + "_" + m] = biome(mix(fog, tint, 0.55), mix(sky, tint, 0.4), water, wfog, start, round(end * scale, 1),
                                             loop, parts + extra, madds or adds, theme)
            for name, b in out.items():
                with open(os.path.join(BIOMES, name + ".json"), "w") as f:
                    json.dump(b, f, indent=2)
                    f.write("\n")
                ids.append("awaken:gate_world/" + name)
    with open(DIMENSION) as f:
        dim = json.load(f)
    src = dim["generator"]["biome_source"]
    src["biomes"] = [b for b in src["biomes"] if not b.startswith("awaken:gate_world/")] + sorted(ids)
    with open(DIMENSION, "w") as f:
        json.dump(dim, f, indent=2)
        f.write("\n")
    print(len(ids), "biomes")


if __name__ == "__main__":
    main()
