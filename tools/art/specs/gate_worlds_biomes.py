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
# the open-sky gate dimension (38.11) lists the same biomes, so a world's air is the same in either
DIMENSIONS = [DIMENSION, os.path.join(ROOT, "dimension", "gates_sky.json")]

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

# Stage 3 (38.11): the D themes. The Glass Desert's and the Market's open lands sit under a real sky (SKY below).
ARCHIVE = {
    "stacks": ("#1a2a2e", "#0c1618", "#2a6a70", "#06181c", 8.0, 120.0, "awaken:gate.world.archive_stacks",
               [("minecraft:white_ash", 0.003)], ("awaken:gate.world.page", 0.003)),
    "atrium": ("#1e3438", "#0e1a1e", "#3a9aa0", "#0a2a30", 12.0, 150.0, "awaken:gate.world.archive_atrium",
               [("minecraft:glow", 0.001), ("minecraft:white_ash", 0.002)], ("awaken:gate.world.drip", 0.005)),
    "sunken_shelves": ("#142a34", "#081620", "#2a7a90", "#082430", 10.0, 140.0, "awaken:gate.world.archive_atrium",
                       [("minecraft:underwater", 0.004), ("minecraft:glow", 0.001)], ("awaken:gate.world.tide", 0.002)),
    "inkwell": ("#0e1018", "#06070c", "#0a0d16", "#04050a", 8.0, 120.0, "awaken:gate.world.archive_ink",
                [("minecraft:white_ash", 0.002), ("minecraft:glow", 0.0008)], ("awaken:gate.world.pages", 0.002)),
    "last_readers_room": ("#2a2418", "#141008", "#2a6a70", "#06181c", 4.0, 48.0, "awaken:gate.world.archive_ink",
                          [("minecraft:white_ash", 0.002)], ("awaken:gate.world.page", 0.002)),
}
ARCHIVE_MOODS = {
    "high_tide": ("#1a3a44", 0.85, [("minecraft:falling_water", 0.006)], ("awaken:gate.world.tide", 0.006)),
    "lamplit": ("#4a3a20", 1.15, [("minecraft:glow", 0.001)], None),
    "paper_storm": ("#3a3830", 0.7, [("minecraft:white_ash", 0.01)], ("awaken:gate.world.pages", 0.01)),
}
FOUNDRY = {
    "works_floor": ("#2a2218", "#140e08", "#3a3a30", "#14120a", 8.0, 120.0, "awaken:gate.world.foundry_works",
                    [("minecraft:ash", 0.003), ("minecraft:electric_spark", 0.0006)], ("awaken:gate.world.valve", 0.002)),
    "pipe_maze": ("#24201a", "#100c08", "#3a3a30", "#14120a", 4.0, 64.0, "awaken:gate.world.foundry_pipes",
                  [("minecraft:white_smoke", 0.003)], ("awaken:gate.world.valve", 0.004)),
    "great_engine": ("#2e2414", "#160e06", "#3a3a30", "#14120a", 12.0, 150.0, "awaken:gate.world.foundry_engine",
                     [("minecraft:ash", 0.002), ("minecraft:small_flame", 0.0006)], ("awaken:gate.world.clockwork", 0.002)),
    "stopped_hour": ("#262a30", "#10141a", "#3a3a30", "#14120a", 12.0, 150.0, "awaken:gate.world.foundry_engine",
                     [("minecraft:white_ash", 0.002), ("minecraft:electric_spark", 0.0006)], ("awaken:gate.world.chime", 0.002)),
    "orrery": ("#14121c", "#08060e", "#3a3a30", "#14120a", 6.0, 72.0, "awaken:gate.world.foundry_engine",
               [("minecraft:glow", 0.0012)], ("awaken:gate.world.chime", 0.001)),
}
FOUNDRY_MOODS = {
    "overdrive": ("#5a3010", 0.9, [("minecraft:small_flame", 0.002), ("minecraft:electric_spark", 0.001)],
                  ("awaken:gate.world.overdrive", 0.006)),
    "steam": ("#5a5a58", 0.55, [("minecraft:white_smoke", 0.01)], ("awaken:gate.world.valve", 0.01)),
    "power_cut": ("#050505", 0.6, [], ("awaken:gate.world.powercut", 0.004)),
}
DESERT = {
    "buried_colossus": ("#e0c898", "#8ac0e8", "#3aa0b0", "#0a3a44", 24.0, 150.0, "awaken:gate.world.desert_wind",
                        [("minecraft:white_ash", 0.0006)], ("awaken:gate.world.shimmer", 0.001)),
    "glass_caves": ("#2a3a3e", "#101a1e", "#3aa0b0", "#0a3a44", 8.0, 120.0, "awaken:gate.world.desert_glass",
                    [("minecraft:glow", 0.001), ("minecraft:white_ash", 0.002)], ("awaken:gate.world.chime", 0.002)),
    "sunken_city": ("#d8c090", "#90b8d8", "#3aa0b0", "#0a3a44", 24.0, 150.0, "awaken:gate.world.desert_city",
                    [("minecraft:white_ash", 0.001)], ("awaken:gate.world.shimmer", 0.001)),
    "mirage": ("#f0d8a8", "#a8c8e0", "#3aa0b0", "#0a3a44", 30.0, 158.0, "awaken:gate.world.desert_wind",
               [("minecraft:white_ash", 0.0006), ("minecraft:end_rod", 0.0002)], ("awaken:gate.world.shimmer", 0.004)),
    "hourglass": ("#4a3a20", "#201808", "#3aa0b0", "#0a3a44", 6.0, 72.0, "awaken:gate.world.desert_city",
                  [("minecraft:white_ash", 0.004)], ("awaken:gate.world.shimmer", 0.002)),
}
DESERT_MOODS = {
    "golden_hour": ("#6a4018", 1.0, [], None),
    "sandstorm": ("#8a7048", 0.35, [("minecraft:white_ash", 0.02)], ("awaken:gate.world.sandstorm", 0.004)),
    "night": ("#101830", 0.9, [], None),
}
MARKET = {
    "lantern_street": ("#1a1a30", "#0c1430", "#1a3050", "#060c18", 16.0, 140.0, "awaken:gate.world.market_street",
                       [("minecraft:glow", 0.0006)], ("awaken:gate.world.chime", 0.002)),
    "rooftops": ("#1c1a34", "#0c1430", "#1a3050", "#060c18", 20.0, 150.0, "awaken:gate.world.market_street",
                 [("minecraft:glow", 0.0006)], ("awaken:gate.world.chime", 0.003)),
    "floating_market": ("#141a30", "#0a1230", "#1a3050", "#060c18", 20.0, 150.0, "awaken:gate.world.market_water",
                        [("minecraft:glow", 0.0008)], ("awaken:gate.world.wish", 0.001)),
    "festival": ("#2a1a30", "#120c28", "#1a3050", "#060c18", 20.0, 150.0, "awaken:gate.world.market_festival",
                 [("minecraft:glow", 0.001)], ("awaken:gate.world.fireworks", 0.003)),
    "brokers_vault": ("#2a1a10", "#140c06", "#1a3050", "#060c18", 6.0, 64.0, "awaken:gate.world.market_festival",
                      [("minecraft:glow", 0.001)], ("awaken:gate.world.wish", 0.002)),
}
MARKET_MOODS = {
    "fireworks": ("#3a1a3a", 1.0, [], ("awaken:gate.world.fireworks", 0.012)),
    "rain": ("#1a2030", 0.65, [("minecraft:falling_water", 0.01)], ("awaken:gate.world.rains", 0.006)),
    "ghost_market": ("#1a3a3a", 0.8, [("minecraft:soul", 0.0008)], ("awaken:gate.world.spirits", 0.003)),
}

# Stage 4 (38.12): the C themes. The Frost Monastery's open lands sit high on a mountain under a cold sky; the White is
# white all the way out (its fog starts at your feet).
FROST = {
    "cloisters": ("#c8d8e8", "#8ab4e8", "#3a6a90", "#0a2030", 24.0, 150.0, "awaken:gate.world.frost_chant",
                  [("minecraft:snowflake", 0.002)], ("awaken:gate.world.bells", 0.001)),
    "ice_caves": ("#1a3040", "#0a1620", "#3a6a90", "#0a2030", 8.0, 110.0, "awaken:gate.world.frost_ice",
                  [("minecraft:snowflake", 0.002), ("minecraft:glow", 0.0008)], ("awaken:gate.world.icecrack", 0.003)),
    "cliff_stair": ("#d0dcec", "#7aa8e0", "#3a6a90", "#0a2030", 30.0, 170.0, "awaken:gate.world.frost_wind",
                    [("minecraft:snowflake", 0.003)], ("awaken:gate.world.bells", 0.0008)),
    "still_snow": ("#dce4f0", "#9ab8e0", "#3a6a90", "#0a2030", 20.0, 140.0, "awaken:gate.world.frost_wind",
                   [("minecraft:snowflake", 0.004), ("minecraft:end_rod", 0.0002)], ("awaken:gate.world.icecrack", 0.001)),
    "frozen_choir": ("#203048", "#0e1624", "#3a6a90", "#0a2030", 6.0, 64.0, "awaken:gate.world.frost_chant",
                     [("minecraft:snowflake", 0.003)], ("awaken:gate.world.wheel", 0.002)),
}
FROST_MOODS = {
    "blizzard": ("#e8eef8", 0.35, [("minecraft:snowflake", 0.03)], ("awaken:gate.world.blizzard", 0.004)),
    "aurora": ("#203a50", 1.0, [("minecraft:end_rod", 0.0006)], None),
    "thaw": ("#3a4a50", 0.8, [("minecraft:dripping_water", 0.006)], ("awaken:gate.world.icecrack", 0.006)),
}
PALACE = {
    "gallery": ("#3a3440", "#1a1620", "#3a4a60", "#0a1020", 10.0, 120.0, "awaken:gate.world.palace_halls",
                [("minecraft:white_ash", 0.001), ("minecraft:end_rod", 0.0002)], ("awaken:gate.world.mirror", 0.0015)),
    "mirror_maze": ("#3a3a48", "#1a1a24", "#3a4a60", "#0a1020", 6.0, 80.0, "awaken:gate.world.palace_halls",
                    [("minecraft:white_ash", 0.001)], ("awaken:gate.world.doubled", 0.001)),
    "inverted_palace": ("#2a2838", "#14121c", "#3a4a60", "#0a1020", 10.0, 120.0, "awaken:gate.world.palace_under",
                        [("minecraft:end_rod", 0.0004)], ("awaken:gate.world.mirror", 0.002)),
    "the_white": ("#f4f4f8", "#ffffff", "#c0d0e0", "#e0e8f0", 0.0, 96.0, "awaken:gate.world.palace_white",
                  [("minecraft:white_ash", 0.002)], ("awaken:gate.world.whale", 0.0004)),
    "room_behind_glass": ("#2a2030", "#140e18", "#3a4a60", "#0a1020", 6.0, 64.0, "awaken:gate.world.palace_under",
                          [("minecraft:white_ash", 0.002)], ("awaken:gate.world.mirror", 0.002)),
}
PALACE_MOODS = {
    "shattered": ("#4a4a5a", 0.8, [("minecraft:white_ash", 0.006)], ("awaken:gate.world.shatter", 0.001)),
    "candlelit": ("#4a2e18", 0.85, [("minecraft:small_flame", 0.0004)], None),
    "doubled": ("#2a2a3a", 0.7, [("minecraft:end_rod", 0.0008)], ("awaken:gate.world.doubled", 0.003)),
}
LAB = {
    "facility": ("#4a5860", "#1a2428", "#3aa0a0", "#0a2a2a", 10.0, 110.0, "awaken:gate.world.lab_hum",
                 [("minecraft:white_ash", 0.0008)], ("awaken:gate.world.console", 0.001)),
    "server_abyss": ("#101a24", "#060c12", "#3aa0a0", "#0a2a2a", 6.0, 100.0, "awaken:gate.world.lab_servers",
                     [("minecraft:glow", 0.001)], ("awaken:gate.world.console", 0.002)),
    "containment": ("#14281e", "#08140e", "#3aa0a0", "#0a2a2a", 6.0, 90.0, "awaken:gate.world.lab_hum",
                    [("minecraft:glow", 0.0008), ("minecraft:dripping_water", 0.002)], ("awaken:gate.world.breach", 0.0006)),
    "simulation": ("#06101c", "#020610", "#3aa0a0", "#0a2a2a", 8.0, 120.0, "awaken:gate.world.lab_sim",
                   [("minecraft:end_rod", 0.0006)], ("awaken:gate.world.glitch", 0.0008)),
    "garden_dome": ("#1e3a24", "#0c1a10", "#3aa0a0", "#0a2a2a", 8.0, 80.0, "awaken:gate.world.lab_sim",
                    [("minecraft:spore_blossom_air", 0.002)], ("awaken:gate.world.console", 0.001)),
}
LAB_MOODS = {
    "lockdown": ("#5a1010", 0.8, [], ("awaken:gate.world.lockdown", 0.002)),
    "breach": ("#1a4a20", 0.7, [("minecraft:dripping_water", 0.004)], ("awaken:gate.world.breach", 0.002)),
    "reboot": ("#101828", 0.6, [("minecraft:end_rod", 0.0008)], ("awaken:gate.world.glitch", 0.002)),
}
DREAM = {
    "the_house": ("#2a2034", "#120c1a", "#3a3060", "#0a0818", 8.0, 96.0, "awaken:gate.world.dream_house",
                  [("minecraft:white_ash", 0.001)], ("awaken:gate.world.musicbox", 0.0008)),
    "endless_hall": ("#241a2e", "#0e0a14", "#3a3060", "#0a0818", 4.0, 48.0, "awaken:gate.world.dream_hall",
                     [("minecraft:white_ash", 0.001)], ("awaken:gate.world.musicbox", 0.0006)),
    "upside_down": ("#2a1a30", "#140a18", "#3a3060", "#0a0818", 8.0, 96.0, "awaken:gate.world.dream_deep",
                    [("minecraft:white_ash", 0.002)], ("awaken:gate.world.stirs", 0.0004)),
    "sleepers_dream": ("#140a24", "#08041a", "#3a3060", "#0a0818", 16.0, 140.0, "awaken:gate.world.dream_deep",
                       [("minecraft:end_rod", 0.0004), ("minecraft:white_ash", 0.001)], ("awaken:gate.world.stirs", 0.0006)),
    "nursery": ("#3a2a3a", "#1a101a", "#3a3060", "#0a0818", 6.0, 56.0, "awaken:gate.world.dream_house",
                [("minecraft:white_ash", 0.002)], ("awaken:gate.world.musicbox", 0.003)),
}
DREAM_MOODS = {
    "fever": ("#5a2020", 0.7, [("minecraft:crimson_spore", 0.002)], None),
    "lullaby": ("#3a2a4a", 1.0, [("minecraft:end_rod", 0.0006)], ("awaken:gate.world.musicbox", 0.004)),
    "wake": ("#4a4a5a", 1.2, [("minecraft:falling_water", 0.004)], ("awaken:gate.world.alarm", 0.001)),
}

# The real sky over the open lands (gates_sky, 38.11): sun_angle 0 is noon, 90 sunset, 180 midnight. Every sky attribute is
# spatially interpolated, so a biome carries it; the Market's lands are always night (its recipes say night 1).
NIGHT = {"sun_angle": 180.0, "moon_angle": 0.0, "star_brightness": 0.6, "sky_light_factor": 0.24, "sky_light_color": "#7a7aff"}
SKY = {
    "buried_colossus": {"sun_angle": 25.0, "sky_light_color": "#fff4e0"},
    "sunken_city": {"sun_angle": 40.0, "sky_light_color": "#fff0d8"},
    "mirage": {"sun_angle": 15.0, "sky_light_color": "#fff8e8"},
    "lantern_street": dict(NIGHT, sky_light_color="#8a80ff"),
    "rooftops": dict(NIGHT, sky_light_color="#8a80ff", star_brightness=0.8),
    "floating_market": dict(NIGHT, sky_light_color="#8090ff"),
    "festival": dict(NIGHT, sky_light_color="#b080ff", star_brightness=0.4),
    "cloisters": {"sun_angle": 30.0, "sky_light_color": "#e8f0ff"},
    "cliff_stair": {"sun_angle": 20.0, "sky_light_color": "#f0f4ff"},
    "still_snow": {"sun_angle": 62.0, "sky_light_color": "#d8e0ff", "star_brightness": 0.3},
}
# a mood's sky over an open world: its own fog and sky colours (not a tint) and its light
MOOD_SKY = {
    "golden_hour": {"fog": "#e8a868", "sky": "#d89868", "sun_angle": 72.0, "sunrise_sunset_color": "#c0ff9040",
                    "sky_light_color": "#ffc890", "sky_light_factor": 0.85},
    "sandstorm": {"fog": "#b89868", "sky": "#a89070", "sky_light_factor": 0.55},
    "night": dict(NIGHT, fog="#141830", sky="#0a1028"),
    "blizzard": {"fog": "#e0e8f0", "sky": "#c0c8d0", "sky_light_factor": 0.6},
    "aurora": dict(NIGHT, fog="#102030", sky="#081830", sky_light_color="#80ffd0", star_brightness=0.9),
    "thaw": {"fog": "#a8b8c8", "sky": "#8898b0", "sky_light_factor": 0.8},
}

# theme: its worlds, its moods (every world but the secret one has them), its secret world, its water colour if not the table's
THEMES = {
    "bell_crypt": (WORLDS, MOODS, "bellmakers_rest"),
    "fungal_hollow": (FUNGAL, FUNGAL_MOODS, "mycelium_mind"),
    "ember_mines": (EMBER, EMBER_MOODS, "heart_seam"),
    "hive": (HIVE, HIVE_MOODS, "queens_garden"),
    "overgrown_temple": (TEMPLE, TEMPLE_MOODS, "sleeping_idol"),
    "drowned_archive": (ARCHIVE, ARCHIVE_MOODS, "last_readers_room"),
    "clockwork_foundry": (FOUNDRY, FOUNDRY_MOODS, "orrery"),
    "glass_desert": (DESERT, DESERT_MOODS, "hourglass"),
    "lantern_night_market": (MARKET, MARKET_MOODS, "brokers_vault"),
    "frost_monastery": (FROST, FROST_MOODS, "frozen_choir"),
    "mirror_palace": (PALACE, PALACE_MOODS, "room_behind_glass"),
    "precursor_lab": (LAB, LAB_MOODS, "garden_dome"),
    "nightmare": (DREAM, DREAM_MOODS, "nursery"),
}


def mix(a, b, t):
    pa = [int(a[i:i + 2], 16) for i in (1, 3, 5)]
    pb = [int(b[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join("%02x" % round(x + (y - x) * t) for x, y in zip(pa, pb))


def biome(fog, sky, water, wfog, start, end, loop, particles, additions, theme="bell_crypt", heavens=None):
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
    } if heavens is None else with_sky(biome(fog, sky, water, wfog, start, end, loop, particles, additions, theme), heavens)


def with_sky(b, heavens):
    """An open world's sun, moon, stars and light. The sky's own colour is the table's."""
    for k, v in heavens.items():
        if k not in ("fog", "sky"):
            b["attributes"]["minecraft:visual/" + k] = v
    return b


def main():
    os.makedirs(BIOMES, exist_ok=True)
    ids = []
    for theme, (worlds, moods, secret) in THEMES.items():
        for w, (fog, sky, water, wfog, start, end, loop, parts, adds) in worlds.items():
            heavens = SKY.get(w)
            out = {w: biome(fog, sky, water, wfog, start, end, loop, parts, adds, theme, heavens)}
            if w != secret:
                for m, (tint, scale, extra, madds) in moods.items():
                    ms = MOOD_SKY.get(m) if heavens is not None else None
                    mfog, msky = (ms["fog"], ms["sky"]) if ms else (mix(fog, tint, 0.55), mix(sky, tint, 0.4))
                    out[w + "_" + m] = biome(mfog, msky, water, wfog, start, round(end * scale, 1), loop, parts + extra,
                                             madds or adds, theme, dict(heavens, **ms) if ms else heavens)
            for name, b in out.items():
                with open(os.path.join(BIOMES, name + ".json"), "w") as f:
                    json.dump(b, f, indent=2)
                    f.write("\n")
                ids.append("awaken:gate_world/" + name)
    for path in DIMENSIONS:
        with open(path) as f:
            dim = json.load(f)
        src = dim["generator"]["biome_source"]
        src["biomes"] = [b for b in src["biomes"] if not b.startswith("awaken:gate_world/")] + sorted(ids)
        with open(path, "w") as f:
            json.dump(dim, f, indent=2)
            f.write("\n")
    print(len(ids), "biomes")


if __name__ == "__main__":
    main()
