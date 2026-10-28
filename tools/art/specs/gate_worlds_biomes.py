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


def mix(a, b, t):
    pa = [int(a[i:i + 2], 16) for i in (1, 3, 5)]
    pb = [int(b[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join("%02x" % round(x + (y - x) * t) for x, y in zip(pa, pb))


def biome(fog, sky, water, wfog, start, end, loop, particles, additions):
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
                "default": {"sound": "awaken:music.gate.bell_crypt", "min_delay": 3600, "max_delay": 12000}},
        },
        "carvers": [],
        "features": [],
    }


def main():
    os.makedirs(BIOMES, exist_ok=True)
    ids = []
    for w, (fog, sky, water, wfog, start, end, loop, parts, adds) in WORLDS.items():
        out = {w: biome(fog, sky, water, wfog, start, end, loop, parts, adds)}
        if w != "bellmakers_rest":
            for m, (tint, scale, extra, madds) in MOODS.items():
                out[w + "_" + m] = biome(mix(fog, tint, 0.55), mix(sky, tint, 0.4), water, wfog, start, round(end * scale, 1), loop,
                                         parts + extra, madds or adds)
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
