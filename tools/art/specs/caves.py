"""Caves worth entering (delve's cave places, SPEC §28.5): the Delvers' things (a log, the lode map, an old pick), the
mist that rises from a sinkhole and the cold draught at a chute's mouth, and the data the places need: their structures
and structure sets, the grotto cache's loot, lang (names, the logs' pages) and the caption's sound.

    python3 tools/art/build_all.py caves        textures
    python3 tools/art/specs/caves.py content    item models and definitions, particles, structures and sets, loot, lang, sounds
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import pixelart as P  # noqa: E402
from pixelart import Canvas, ramp, rng_for, value_noise  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
DATA = os.path.join(RES, "data/awaken")
MERGE = os.path.join(ROOT, "src/main/merge/delve_caves")

P.RAMPS.update({
    "oldleather": ["#1a100a", "#2c1a10", "#432818", "#5c3a22", "#7a5030", "#9a6a40"],
    "brass": ["#2a1c06", "#54380e", "#8a621c", "#c0922e", "#e8c060", "#fff0b0"],
    "parchment": ["#3a2e1c", "#6a5636", "#9a8258", "#c4ac7e", "#e0cca0", "#f4e6c4"],
    "lapisink": ["#0a1440", "#16288a", "#2a4ac8", "#5a7cf0", "#a4baff"],
    "oldiron": ["#16181c", "#262a30", "#3a3f47", "#555c66", "#7c848e", "#b0b8c0"],
    "haft": ["#24160c", "#3a2614", "#56391e", "#74502c", "#94693c", "#b4864e"],
    "copperband": ["#2a1006", "#5a2a14", "#9a4a24", "#d8743e", "#ffb07a"],
})


def hx(h, a=255):
    return P.hex_rgba(h, a)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


# ---- icons -----------------------------------------------------------------------------------------------------------

def log_icon():
    """A Delver's log: a squat notebook in worn leather, a brass clasp, the pages' edge showing, a lapis thread hanging."""
    c = Canvas(16, 16)
    L, B, Pp = ramp("oldleather"), ramp("brass"), ramp("parchment")
    rnd = rng_for("log", 1)
    for y in range(2, 14):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (2, 13)
            wear = rnd.random()
            c.set(x, y, L[1] if edge else (L[4] if wear < 0.12 else L[3] if wear < 0.55 else L[2]))
    for y in range(3, 13):  # the pages' edge on the right
        c.set(12, y, Pp[4] if y % 2 else Pp[3])
    for x in range(4, 12):  # a strap across, a scuffed corner
        c.set(x, 7, L[0])
        c.set(x, 8, L[1])
    for y in range(6, 10):
        c.set(10, y, B[3] if y in (7, 8) else B[2])
        c.set(11, y, B[4] if y == 7 else B[3])
    c.set(10, 7, B[5])
    for y in range(3, 6):  # a pressed mark on the cover: the Delvers' pick
        c.set(6 + (y - 3), y, L[4])
    c.set(5, 5, L[4])
    c.set(8, 3, L[4])
    c.set(4, 13, hx("#2a4ac8"))
    c.set(4, 14, hx("#16288a"))
    c.set(5, 15, hx("#16288a"))
    return c


def map_icon():
    """The lode map: a folded parchment, fold lines, blue seams drawn on it and a lapis mark where the vein runs."""
    c = Canvas(16, 16)
    Pp, Ink = ramp("parchment"), ramp("lapisink")
    n = value_noise(16, 16, rng_for("map", 2), scale=3, octaves=2)
    for y in range(2, 14):
        for x in range(1, 15):
            v = n[y][x]
            col = Pp[4] if v > 0.6 else Pp[3] if v > 0.25 else Pp[2]
            if x in (1, 14) or y in (2, 13):
                col = Pp[1]
            if x in (5, 10):
                col = Pp[2]  # the folds
            c.set(x, y, col)
    # the seams it shows: wandering lines in lapis ink
    for x in range(2, 14):
        y = int(7 + 2.2 * math.sin(x * 0.7))
        c.set(x, y, Ink[1])
        if x % 3 == 0:
            c.set(x, y + 1, Ink[2])
    for x in range(3, 9):
        c.set(x, 11 - (x % 2), Ink[2])
    c.set(11, 5, Ink[3])
    c.set(12, 5, Ink[4])
    c.set(11, 4, Ink[3])
    c.set(12, 4, Ink[3])
    return c


def pick_icon():
    """An old pick: a pitted iron head, a dark worn haft bound in copper where the hands went."""
    c = Canvas(16, 16)
    I, H, Cu = ramp("oldiron"), ramp("haft"), ramp("copperband")
    head = [(2, 3), (3, 2), (4, 2), (5, 2), (6, 2), (7, 3), (8, 3), (9, 4), (10, 5), (11, 6), (12, 7), (12, 8),
            (3, 3), (4, 3), (5, 3), (6, 3), (9, 5), (10, 6), (11, 7)]
    rnd = rng_for("pick", 3)
    for (x, y) in head:
        c.set(x, y, I[4] if rnd.random() < 0.3 else I[3] if rnd.random() < 0.7 else I[2])
    c.set(2, 4, I[1])
    c.set(12, 9, I[1])
    c.set(3, 2, I[5])
    c.set(11, 6, I[5])
    for i in range(10):  # the haft, from the head's eye down to the bottom left
        x, y = 8 - i, 5 + i
        if 0 <= x < 16 and 0 <= y < 16:
            c.set(x, y, H[3] if i % 2 else H[2])
            if x + 1 < 16:
                c.set(x + 1, y, H[1])
    for i in (5, 6):
        x, y = 8 - i, 5 + i
        c.set(x, y, Cu[3])
        c.set(x + 1, y, Cu[2])
    c.set(7, 4, I[1])
    c.set(8, 4, I[2])
    return c


# ---- particles -------------------------------------------------------------------------------------------------------

def mist_frames():
    """Mist off a sinkhole: big soft lobes, ragged at the edge, faint (the game fades and grows them as they rise)."""
    out = []
    for k in range(4):
        c = Canvas(32, 32)
        n = value_noise(32, 32, rng_for("mist", k), scale=7, octaves=3)
        for y in range(32):
            for x in range(32):
                d = math.hypot(x - 15.5, (y - 15.5) * 1.25) / 15.5
                if d >= 1:
                    continue
                edge = (1 - d) ** 1.6
                a = int(150 * edge * (0.45 + 0.75 * n[y][x]))
                if a > 4:
                    c.set(x, y, (255, 255, 255, min(170, a)))
        out.append(c)
    return out


def draught_frames():
    """A breath of cold air: a thin pale streak, soft at both ends."""
    out = []
    for k in range(4):
        c = Canvas(16, 8)
        rnd = rng_for("draught", k)
        bend = rnd.uniform(-1.2, 1.2)
        for x in range(16):
            t = x / 15
            y0 = 3.5 + bend * math.sin(t * math.pi)
            a_line = math.sin(t * math.pi) ** 1.3
            for y in range(8):
                d = abs(y - y0)
                if d < 1.6:
                    a = int(150 * a_line * (1 - d / 1.6))
                    if a > 6:
                        c.set(x, y, (235, 245, 255, a))
        out.append(c)
    return out


def build(out):
    out.item("delvers_log", log_icon())
    out.item("lode_map", map_icon())
    out.item("delvers_pick", pick_icon())
    for i, f in enumerate(mist_frames()):
        out.particle(f"delve_mist_{i}", f)
    for i, f in enumerate(draught_frames()):
        out.particle(f"delve_draught_{i}", f)


# ---- data ------------------------------------------------------------------------------------------------------------

# the places' structure sets: (spacing, separation, salt) in chunks; the first sinkhole is placed by awaken:near_city
SETS = {
    "sinkhole": (44, 18, 610317793),
    "mine": (52, 22, 481120967),
    "seep": (26, 10, 723008411),
    "chute": (34, 13, 365520049),
    "grotto": (28, 11, 902117323),
}
STEPS = {"sinkhole": "underground_structures", "mine": "underground_structures", "seep": "underground_structures",
         "chute": "underground_structures", "grotto": "underground_structures"}


def worldgen():
    for kind, (spacing, sep, salt) in SETS.items():
        write(os.path.join(DATA, "worldgen/structure/delve", kind + ".json"), {
            "type": "awaken:cave_place", "place": kind, "biomes": "#minecraft:is_overworld", "step": STEPS[kind],
            "spawn_overrides": {}, "terrain_adaptation": "none"})
        name = kind + "s" if not kind.endswith("e") else kind + "s"
        write(os.path.join(DATA, "worldgen/structure_set/delve", name + ".json"), {
            "structures": [{"structure": "awaken:delve/" + kind, "weight": 1}],
            "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": sep, "salt": salt}})
    write(os.path.join(DATA, "worldgen/structure/delve/first_sinkhole.json"), {
        "type": "awaken:cave_place", "place": "sinkhole", "first": True, "biomes": "#minecraft:is_overworld",
        "step": "underground_structures", "spawn_overrides": {}, "terrain_adaptation": "none"})
    write(os.path.join(DATA, "worldgen/structure_set/delve/first_sinkhole.json"), {
        "structures": [{"structure": "awaken:delve/first_sinkhole", "weight": 1}],
        "placement": {"type": "awaken:near_city", "min_distance": 600, "max_distance": 900, "avoid_north": 40, "salt": 1336077101}})


def loot():
    write(os.path.join(DATA, "loot_table/chests/delve/grotto_cache.json"), {
        "type": "minecraft:chest", "random_sequence": "awaken:chests/delve/grotto_cache",
        "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 5}, "entries": [
                {"type": "minecraft:item", "name": "minecraft:emerald", "weight": 6,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 6}}]},
                {"type": "minecraft:item", "name": "minecraft:lapis_lazuli", "weight": 5,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 3, "max": 8}}]},
                {"type": "minecraft:item", "name": "minecraft:amethyst_shard", "weight": 5,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 5}}]},
                {"type": "minecraft:item", "name": "minecraft:glow_berries", "weight": 4,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 3, "max": 7}}]},
                {"type": "minecraft:item", "name": "awaken:runner_rope", "weight": 3},
                {"type": "minecraft:item", "name": "awaken:piton", "weight": 3,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 4}}]},
                {"type": "minecraft:item", "name": "awaken:lode_map", "weight": 2},
                {"type": "minecraft:item", "name": "minecraft:gold_ingot", "weight": 3,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]}]},
            {"rolls": 1, "entries": [
                {"type": "minecraft:item", "name": "minecraft:diamond", "weight": 1},
                {"type": "minecraft:item", "name": "awaken:deepglass_shard", "weight": 2,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}]},
                {"type": "minecraft:empty", "weight": 3}]}]})


LOGS = [
    ("Foreman Hessel's Log", [
        "The Carillon wants more bell metal than any hill has given it. The copper here runs down, so we follow it down. "
        "Twelve picks, two carts, a winch that squeals like the Watch's gulls.",
        "Third gallery opened. Good copper, a little tin. The men say the rock is warmer than it ought to be this deep. "
        "I told them that is the price of good copper. I wrote the seams on the map in lapis, so the next crew finds them."]),
    ("A Delver's Notes", [
        "Past the chasm the lamps burn blue and the rock hums when you lay a hand on it. Hessel says it is the water. "
        "It is not the water.",
        "Ott found black glass in the wall today, glass that holds a light of its own. He would not put it in the cart. "
        "We left it where it grew. Nobody sleeps near the far drift now."]),
    ("The Last Entry", [
        "We broke into it. Violet, all of it, and warm, and something in there is breathing. Ott went in with a lamp. "
        "The lamp came back out on its own.",
        "We are sealing the drift. Tell the Carillon to find its metal somewhere else. The old ones said the Well goes down "
        "to where this comes from. I believe them now."]),
]


def lang():
    out = {
        "item.awaken.delvers_log": "Delver's Log",
        "item.awaken.delvers_log.named": "Delver's Log: %s",
        "item.awaken.delvers_log.desc": "Use to read it. Read, it joins your Codex.",
        "item.awaken.lode_map": "Lode Map",
        "item.awaken.lode_map.desc": "Read it with a lapis (used up): the nearest lodes come up on your Gate Map.",
        "item.awaken.lode_map.needs": "The map's lines are drawn in lapis: carry one to read it",
        "item.awaken.lode_map.none": "No lode within the map's reach of here",
        "item.awaken.delvers_pick": "Delver's Old Pick",
        "item.awaken.delvers_pick.desc": "Worn smooth where the hands went. It knows where the metal is.",
        "item.awaken.delvers_pick.perk": "Carried: the Tuning Fork reaches %s further",
        "subtitles.awaken.delve.cave.name": "A place is named",
    }
    for i, (title, pages) in enumerate(LOGS):
        out[f"item.awaken.delvers_log.{i}.title"] = title
        for k, p in enumerate(pages):
            out[f"item.awaken.delvers_log.{i}.page{k}"] = p
    return out


def content():
    for n in ("delvers_log", "lode_map"):
        write(os.path.join(ASSETS, "models/item", n + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/" + n}})
        write(os.path.join(ASSETS, "items", n + ".json"), {"model": {"type": "minecraft:model", "model": "awaken:item/" + n}})
    write(os.path.join(ASSETS, "models/item/delvers_pick.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": "awaken:item/delvers_pick"}})
    write(os.path.join(ASSETS, "items/delvers_pick.json"), {"model": {"type": "minecraft:model", "model": "awaken:item/delvers_pick"}})
    write(os.path.join(ASSETS, "particles/delve_mist.json"), {"textures": [f"awaken:delve_mist_{i}" for i in range(4)]})
    write(os.path.join(ASSETS, "particles/delve_draught.json"), {"textures": [f"awaken:delve_draught_{i}" for i in range(4)]})
    worldgen()
    loot()
    write(os.path.join(MERGE, "assets/awaken/lang/en_us.json"), dict(sorted(lang().items())))
    # the caption's cue: the region caption's own chime, lower and quieter (a place named under the ground)
    write(os.path.join(MERGE, "assets/awaken/sounds.json"), {"delve.cave.name": {
        "subtitle": "subtitles.awaken.delve.cave.name",
        "sounds": [{"name": "awaken:ui_living_region_enter", "pitch": 0.74, "volume": 0.62}]}})
    print("caves content written")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    else:
        print(__doc__)
