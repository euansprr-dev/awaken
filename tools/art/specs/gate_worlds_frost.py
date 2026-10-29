"""Gate Worlds, Frost Monastery (SPEC §38.12): the theme's 14 world blocks and its set piece, the Singing Bowl.

    python3 tools/art/build_all.py gate_worlds_frost        textures
    python3 tools/art/specs/gate_worlds_frost.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_frost.py sheet PATH every texture at 6x, named

Cold grey mountain stone, a carved knot glowing gold in it, blue slate tiles, red-painted timber banded in gold, white
lime plaster, snow, blue ice and ice glowing from inside, icicles, strings of five-coloured prayer flags, brass butter
lamps, ice crystals, cloud, and the prayer wheel you turn. Drawn by code, nothing copied.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "fstone": ["#2a2e36", "#3a404a", "#4c535e", "#5e6672", "#727a86", "#8a929c"],
    "slate": ["#141c2a", "#1c2638", "#253248", "#2f3f58", "#3b4d6a", "#4a5e7c"],
    "red": ["#2a0806", "#46100a", "#64180e", "#842414", "#a2321a", "#be4424"],
    "gold": ["#5a3a10", "#8a5a18", "#b88024", "#e0a838", "#ffd060", "#fff0a8"],
    "lime": ["#8e8c86", "#a8a69e", "#c0beb6", "#d6d4cc", "#e8e6de", "#f6f4ee"],
    "snow": ["#a8b8cc", "#c0cede", "#d4e0ec", "#e4ecf6", "#f0f6fc", "#ffffff"],
    "ice": ["#2a5a8a", "#3a74a8", "#4e8ec4", "#6aa8d8", "#8cc4e8", "#b8e0f6"],
    "brass": ["#3a2408", "#5a3a10", "#7e5418", "#a87424", "#d09a34", "#f0c860"],
}
FLAG = ("#2a64c8", "#f4f0e6", "#c42a20", "#2a9a4a", "#f0c020")
FLAME = ("#ff9a30", "#ffd060", "#fff4c0")


def _P():
    return K.px(RAMPS)


def tex_stone(P):
    c = K.ashlar(P, "fstone", "frost-stone", course=8)
    rng = P.rng_for("frost-stone-rime")
    # rime caught in the joints
    for _ in range(10):
        x, y = rng.randrange(16), rng.choice((7, 15))
        c.blend(x, y, K.rgba("#dce8f4"), 0.45)
    return c


def tex_carved(P):
    """The endless knot cut into the stone, its grooves glowing gold."""
    c = K.ashlar(P, "fstone", "frost-carved", course=16)
    g = P.Canvas(16, 16)
    gold = P.ramp("gold")
    for y in range(16):
        for x in range(16):
            u, v = x - 7.5, y - 7.5
            # two interlaced squares turned 45 degrees, and a ring
            a = abs(abs(u) + abs(v) - 5.5) < 0.8
            b = (abs(abs(u) - 3.5) < 0.6 and abs(v) < 4.2) or (abs(abs(v) - 3.5) < 0.6 and abs(u) < 4.2)
            if a or b:
                c.set(x, y, P.ramp("fstone")[0])
                g.set(x, y, gold[4] if (x + y) % 3 else gold[5])
    return c, g


def tex_tiles(P):
    return K.tiles(P, "slate", "frost-tiles", size=8)


def tex_timber(P):
    rp = P.ramp("red")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = 2 + int(2 * math.sin((x + 0.5) / 16 * math.pi)) + (1 if x == 4 else 0)
            c.set(x, y, rp[min(5, v)])
    for x in range(16):
        c.set(x, 1, P.ramp("gold")[3])
        c.set(x, 2, P.ramp("gold")[2])
        c.set(x, 13, P.ramp("gold")[2])
    rng = P.rng_for("frost-timber-wear")
    for _ in range(5):
        c.set(rng.randrange(16), rng.randrange(3, 13), P.ramp("red")[0])
    return c


def tex_timber_end(P):
    return K.rings(P, "red", "red", "frost-timber")


def tex_plaster(P):
    c = K.noisy(P, "lime", "frost-plaster", scale=4, lo=2, span=3, jitter=0.25)
    rng = P.rng_for("frost-plaster-drip")
    for _ in range(3):
        x = rng.randrange(16)
        for y in range(rng.randrange(4, 10)):
            c.blend(x, y, K.rgba("#8a8070"), 0.18)
    return c


def tex_snow(P):
    c = K.noisy(P, "snow", "frost-snow", scale=5, lo=2, span=4, jitter=0.15)
    rng = P.rng_for("frost-snow-glint")
    for _ in range(7):
        c.set(rng.randrange(16), rng.randrange(16), K.rgba("#ffffff"))
    for _ in range(5):
        c.set(rng.randrange(16), rng.randrange(16), K.rgba("#b8cce4"))
    return c


def tex_ice(P, glowing):
    ip = P.ramp("ice")
    rng = P.rng_for("frost-ice", glowing)
    n = P.value_noise(16, 16, rng, scale=4, octaves=2)
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            k = int(n[y][x] * 5.9)
            edge = x in (0, 15) or y in (0, 15)
            c.set(x, y, (*ip[5 if edge else k][:3], 235 if edge else 150 + k * 12))
            if glowing and n[y][x] > 0.55:
                g.set(x, y, K.rgba("#c8f0ff" if n[y][x] > 0.7 else "#7ad0ff", 210))
    # cracks running through it
    for path in K.walk(P, ("frost-ice-crack", glowing), 2, 9):
        for x, y in path:
            c.set(x % 16, y % 16, K.rgba("#e8f6ff", 220))
    return (c, g) if glowing else c


def tex_icicles(P):
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    rng = P.rng_for("frost-icicles")
    for x0 in (1, 4, 6, 9, 12, 14):
        length = rng.randrange(6, 15)
        for y in range(length):
            w = 1 if y > length * 0.4 else 2
            for dx in range(w):
                col = P.ramp("ice")[5 if dx == 0 else 3]
                c.set(min(15, x0 + dx), y, (*col[:3], 230))
            if y == length - 1:
                g.set(x0, y, K.rgba("#e0f8ff"))
    return c, g


def tex_flags(P):
    """A string of prayer flags: the cord across the top, five colours hanging from it, letters on the white."""
    c = P.Canvas(16, 16)
    for x in range(16):
        c.set(x, 1, K.rgba("#3a2a1a"))
    for i in range(5):
        col = K.rgba(FLAG[i])
        x0 = i * 3 + 1
        for y in range(2, 12):
            for x in range(x0, x0 + 3):
                if x > 15:
                    continue
                if y == 11 and (x + i) % 2:
                    continue
                c.set(x, y, col if (y + x) % 4 else tuple(max(0, v - 30) for v in col[:3]) + (255,))
        c.set(x0 + 1, 5, K.rgba("#3a2a1a"))
        c.set(x0 + 1, 7, K.rgba("#3a2a1a"))
    return c


def tex_lamp(P):
    """A butter lamp: a brass cup on a foot, the flame above."""
    bp = P.ramp("brass")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, bp[2 + (x * 3 + y) % 3] if y % 5 else bp[4])
    flame = P.Canvas(16, 16)
    rows = ["................",
            "................",
            ".......W........",
            ".......W........",
            "......WYW.......",
            "......YYY.......",
            ".....YOYOY......",
            ".....YOOOY......",
            ".....OOOOO......",
            "......OOO.......",
            "......OOO.......",
            ".......B........",
            ".......B........",
            "................",
            "................",
            "................"]
    flame = K.sprite(rows, {"W": FLAME[2], "Y": FLAME[1], "O": FLAME[0], "B": "#3a2408"})
    return c, flame


def tex_crystal(P):
    rows = ["................",
            ".......W........",
            ".......C....W...",
            "......CBC...C...",
            "..W...CBC..CBC..",
            "..C...CBC..CBC..",
            ".CBC..CBBC.CBC..",
            ".CBC.CBBBC.CBBC.",
            ".CBBCCBBBCCBBBC.",
            "..CBBCBBBCBBBC..",
            "..CBBBBBBBBBBC..",
            "...CBBBBBBBBC...",
            "...CCBBBBBBCC...",
            "....CCCCCCCC....",
            "................",
            "................"]
    c = K.sprite(rows, {"W": "#ffffff", "C": "#8cc4e8", "B": "#3a74a8"})
    return c, K.only(c, {K.rgba("#ffffff")[:3], K.rgba("#8cc4e8")[:3]})


def tex_cloud(P):
    sp = P.ramp("snow")
    rng = P.rng_for("frost-cloud")
    n = P.value_noise(16, 16, rng, scale=6, octaves=3)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            k = n[y][x]
            c.set(x, y, (*sp[min(5, 2 + int(k * 4))][:3], 170 + int(k * 70)))
    return c


def tex_wheel(P):
    """The prayer wheel's drum: gold bands top and bottom, red between with the mantra in gold."""
    gp, rp = P.ramp("gold"), P.ramp("red")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            shade = int(1.5 * math.sin((x + 0.5) / 16 * math.pi))
            if y < 3 or y > 12:
                c.set(x, y, gp[2 + shade + (1 if y in (1, 14) else 0)])
            else:
                c.set(x, y, rp[2 + shade])
    # the mantra: a row of little gold syllables
    for i in range(5):
        x0 = 1 + i * 3
        for dx, dy in ((0, 0), (1, 0), (1, 1), (0, 2), (1, 2), (0, 3)):
            c.set(x0 + dx, 6 + dy, gp[4])
            g.set(x0 + dx, 6 + dy, gp[5])
    top = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            top.set(x, y, gp[4] if d < 2 else gp[2] if d < 6 else gp[3])
    return c, g, top


def tex_bowl(P):
    """The Singing Bowl: hammered bronze, a ring of light round its rim when it sings."""
    bp = P.ramp("brass")
    rng = P.rng_for("frost-bowl")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, bp[2 + int(rng.random() * 2.5) + (1 if y < 3 else 0)])
    g = P.Canvas(16, 16)
    for x in range(16):
        g.set(x, 0, K.rgba("#fff0a8"))
        g.set(x, 1, K.rgba("#ffd060", 200))
    inside = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            inside.set(x, y, bp[1] if d < 5 else bp[3] if d < 7 else bp[4])
    return c, g, inside


def textures():
    P = _P()
    t = {}
    t["frost_stone"] = tex_stone(P)
    t["frost_stone_carved"], t["frost_stone_carved_glow"] = tex_carved(P)
    t["frost_tiles"] = tex_tiles(P)
    t["frost_timber"] = tex_timber(P)
    t["frost_timber_top"] = tex_timber_end(P)
    t["frost_plaster"] = tex_plaster(P)
    t["frost_snow"] = tex_snow(P)
    t["frost_ice"] = tex_ice(P, False)
    t["frost_ice_glowing"], t["frost_ice_glowing_glow"] = tex_ice(P, True)
    t["frost_icicles"], t["frost_icicles_glow"] = tex_icicles(P)
    t["frost_flags"] = tex_flags(P)
    t["frost_butter_lamp"], t["frost_butter_lamp_flame"] = tex_lamp(P)
    t["frost_crystal"], t["frost_crystal_glow"] = tex_crystal(P)
    t["frost_cloud"] = tex_cloud(P)
    t["frost_prayer_wheel"], t["frost_prayer_wheel_glow"], t["frost_prayer_wheel_top"] = tex_wheel(P)
    t["singing_bowl"], t["singing_bowl_glow"], t["singing_bowl_inside"] = tex_bowl(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


BLOCKS = {
    "frost_stone": ("Mountain Stone", "pickaxe"),
    "frost_stone_carved": ("Carved Mountain Stone", "pickaxe"),
    "frost_tiles": ("Slate Tiles", "pickaxe"),
    "frost_timber": ("Red Monastery Timber", "axe"),
    "frost_plaster": ("Lime Plaster", "pickaxe"),
    "frost_snow": ("Packed Snow", "shovel"),
    "frost_ice": ("Blue Ice", "pickaxe"),
    "frost_ice_glowing": ("Glowing Ice", "pickaxe"),
    "frost_icicles": ("Icicles", None),
    "frost_flags": ("Prayer Flags", None),
    "frost_butter_lamp": ("Butter Lamp", None),
    "frost_crystal": ("Ice Crystals", None),
    "frost_cloud": ("Cloud", None),
    "frost_prayer_wheel": ("Prayer Wheel", "axe"),
    "singing_bowl": ("The Singing Bowl", "pickaxe"),
}
FLAT = ("frost_icicles", "frost_flags", "frost_crystal")
B = K.B


def models():
    m = {}
    m["frost_stone"] = K.cube_model("frost_stone")
    m["frost_stone_carved"] = K.cube_model("frost_stone_carved", glow=True)
    m["frost_tiles"] = K.cube_model("frost_tiles")
    m["frost_timber"] = K.column_model("frost_timber", "frost_timber_top")
    m["frost_plaster"] = K.cube_model("frost_plaster")
    m["frost_snow"] = K.cube_model("frost_snow")
    m["frost_ice"] = K.cube_model("frost_ice", translucent=True)
    m["frost_ice_glowing"] = K.cube_model("frost_ice_glowing", glow=True, translucent=True)
    m["frost_icicles"] = K.cross_model("frost_icicles", glow=True)
    m["frost_flags"] = K.cross_model("frost_flags")
    m["frost_crystal"] = K.cross_model("frost_crystal", glow=True)
    m["frost_cloud"] = K.cube_model("frost_cloud", translucent=True)
    # the butter lamp: a brass cup on a little foot, its flame burning over it
    m["frost_butter_lamp"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                              "textures": {"particle": B + "frost_butter_lamp", "brass": B + "frost_butter_lamp",
                                           "flame": B + "frost_butter_lamp_flame"},
                              "elements": [K.box([6.5, 0, 6.5], [9.5, 1, 9.5], "#brass"), K.box([7.25, 1, 7.25], [8.75, 2.5, 8.75], "#brass"),
                                           K.box([5, 2.5, 5], [11, 5, 11], "#brass")]
                              + K.cross("#flame", 15, inset=4, y0=3, y1=13, uv=[4, 1, 12, 12])}
    # the prayer wheel: a drum on a spindle, gold caps; lit, its mantra glows
    for lit in (False, True):
        tex = {"particle": B + "frost_prayer_wheel", "drum": B + "frost_prayer_wheel", "cap": B + "frost_prayer_wheel_top",
               "post": B + "frost_timber"}
        els = [K.box([7.25, 0, 7.25], [8.75, 2, 8.75], "#post"), K.box([4, 2, 4], [12, 13, 12], "#drum", uv=[0, 1, 16, 15]),
               K.box([3.5, 12.5, 3.5], [12.5, 13.5, 12.5], "#cap"), K.box([3.5, 2, 3.5], [12.5, 3, 12.5], "#cap"),
               K.box([7.25, 13.5, 7.25], [8.75, 15, 8.75], "#cap")]
        if lit:
            tex["glow"] = B + "frost_prayer_wheel_glow"
            els.append(K.box([3.98, 2, 3.98], [12.02, 13, 12.02], "#glow", 15, sides=("north", "south", "west", "east"), uv=[0, 1, 16, 15]))
        m["frost_prayer_wheel" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    # the Singing Bowl: a wide bronze bowl on a cushion of red; singing, its rim rings with light
    for lit in (False, True):
        tex = {"particle": B + "singing_bowl", "bowl": B + "singing_bowl", "inside": B + "singing_bowl_inside",
               "cushion": B + "frost_timber"}
        els = [K.box([4, 0, 4], [12, 1, 12], "#cushion"), K.box([3, 1, 3], [13, 6, 13], "#bowl", sides=("north", "south", "west", "east", "down")),
               K.box([3.5, 5.5, 3.5], [12.5, 5.5, 12.5], "#inside", sides=("up",))]
        if lit:
            tex["glow"] = B + "singing_bowl_glow"
            els.append(K.box([2.98, 5, 2.98], [13.02, 6, 13.02], "#glow", 15, sides=("north", "south", "west", "east"), uv=[0, 0, 16, 2]))
        m["singing_bowl" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n not in ("frost_timber", "frost_prayer_wheel", "singing_bowl")}
    s["frost_timber"] = K.axis_state("frost_timber")
    s["frost_prayer_wheel"] = K.lit_state("frost_prayer_wheel")
    s["singing_bowl"] = K.lit_state("singing_bowl")
    return s


def content():
    K.content("gate_worlds_frost", BLOCKS, models(), states(), {}, flat_items=FLAT)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_frost.png")
    else:
        print(__doc__)
