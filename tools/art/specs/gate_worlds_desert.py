"""Gate Worlds, Glass Desert (SPEC §38.11): the theme's 13 world blocks and its set piece, the Sun Hourglass.

    python3 tools/art/build_all.py gate_worlds_desert        textures
    python3 tools/art/specs/gate_worlds_desert.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_desert.py sheet PATH every texture at 6x, named

Pale gold sand and sand the wind has rippled, sandstone in courses, sandstone carved with sun signs that glow, sun-dried
brick, blue glazed tiles off a buried roof, fluted columns, sand fused into amber glass (some still glowing from the heat
that made it), glass crystals, dry grass, a fall of sand, and the bronze mirror you turn to the sun. Drawn by code,
nothing copied.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "dsand": ["#9a7a48", "#b08c56", "#c4a064", "#d4b274", "#e2c488", "#eed4a0"],
    "dstone": ["#7a5c34", "#8e6c3e", "#a27e4a", "#b49058", "#c4a268", "#d4b47c"],
    "dbrick": ["#4a2a18", "#6a3c22", "#86502c", "#9e6238", "#b47446", "#c88858"],
    "dtile": ["#0c2a4a", "#143c66", "#1e5288", "#2c6aa8", "#4a8ac4", "#7cb0dc"],
    "dglass": ["#7a3a0a", "#a85414", "#d07422", "#ec9a3a", "#f8bc60", "#ffdc90"],
    "sun": ["#e88a1a", "#ffc040", "#ffe08a", "#fff6d8"],
    "grass": ["#6a5a2a", "#86743a", "#a08e4a", "#b8a65e"],
    "bronze": ["#3a2410", "#5a3a1a", "#7c5426", "#a06e34", "#c48c48", "#e8b468"],
}
SUN = ("#ffc040", "#ffe08a", "#fff6d8")


def _P():
    return K.px(RAMPS)


def tex_sand(P):
    c = K.noisy(P, "dsand", "sand", scale=5, octaves=3, lo=1, span=4, jitter=0.5)
    rng = P.rng_for("sand-glints")
    for _ in range(5):
        c.set(rng.randrange(16), rng.randrange(16), K.rgba("#fff0c8"))
    return c


def tex_rippled(P):
    sp = P.ramp("dsand")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            w = math.sin((y + math.sin(x * 0.4) * 1.5) * 1.3)
            c.set(x, y, sp[4] if w > 0.55 else sp[2] if w < -0.4 else sp[3])
    return c


def tex_sandstone(P):
    return K.ashlar(P, "dstone", "sandstone", course=8)


def tex_carved(P):
    c = K.ashlar(P, "dstone", "sandstone-carved", course=16)
    sp = P.ramp("dstone")
    g = P.Canvas(16, 16)
    # a sun disc with its rays cut into the block
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r, a = math.hypot(dx, dy), math.atan2(dy, dx)
            if 2.4 < r < 3.7 or (4.6 < r < 7.6 and abs(math.sin(a * 4)) < 0.38):
                c.set(x, y, sp[0])
                g.set(x, y, K.rgba(SUN[1] if r < 4 else SUN[0]))
    return K.under(c, g, 0.5), g


def tex_brick(P):
    bp = P.ramp("dbrick")
    rng = P.rng_for("mudbrick")
    c = P.Canvas(16, 16)
    for y in range(16):
        row = y // 4
        for x in range(16):
            xx = (x + (4 if row % 2 else 0)) % 16
            if y % 4 == 3 or xx % 8 == 0:
                c.set(x, y, P.ramp("dsand")[1])
            else:
                c.set(x, y, bp[2 + (1 if rng.random() < 0.3 else 0) + (1 if (xx // 8 + row) % 2 else 0)])
    return c


def tex_tile(P):
    tp = P.ramp("dtile")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            u, v = x % 8, y % 8
            if u == 0 or v == 0:
                c.set(x, y, P.ramp("dsand")[3])
            else:
                d = abs(u - 4) + abs(v - 4)
                c.set(x, y, tp[5] if d == 1 else tp[4] if d < 3 else tp[2] if d < 5 else tp[3])
    rng = P.rng_for("tile-chips")
    for _ in range(5):
        c.set(rng.randrange(16), rng.randrange(16), P.ramp("dsand")[2])
    return c


def tex_column(P):
    sp = P.ramp("dstone")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            flute = x % 4
            c.set(x, y, sp[1] if flute == 0 else sp[4] if flute == 2 else sp[3])
    for x in range(16):
        c.set(x, 0, sp[5])
        c.set(x, 15, sp[0])
    return c


def tex_column_end(P):
    sp = P.ramp("dstone")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            c.set(x, y, sp[4] if d < 6.5 else sp[2] if d < 7.5 else sp[1])
    return c


def tex_glass(P, glowing):
    gp = P.ramp("dglass")
    rng = P.rng_for("dglass", glowing)
    n = P.value_noise(16, 16, rng, scale=3, octaves=2)
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            k = int(n[y][x] * 5.9)
            edge = x in (0, 15) or y in (0, 15)
            c.set(x, y, (*gp[5 if edge else k][:3], 230 if edge else 140 + k * 14))
            if glowing and n[y][x] > 0.48:
                g.set(x, y, K.rgba(SUN[1] if n[y][x] > 0.66 else SUN[0], 220))
    # bubbles caught in the glass
    for _ in range(4):
        x, y = rng.randrange(2, 14), rng.randrange(2, 14)
        c.set(x, y, K.rgba("#fff0c8", 200))
    return (c, g) if glowing else c


def tex_crystal(P):
    rows = ["................",
            "......W.........",
            "......Y.....W...",
            ".....YOY....Y...",
            ".....YOO...YOY..",
            "..W..YOO...YOO..",
            "..Y..OOR...OOR..",
            ".YOY.OOR...OOR..",
            ".YOO.ORR..YOOR..",
            ".OOR.ORR..OORR..",
            ".ORR.ORR..ORR...",
            "bORRbORRbbORRb..",
            "bbRbbbRbbbbRbb..",
            "................",
            "................",
            "................"]
    s = K.sprite(rows, {"W": SUN[2], "Y": SUN[1], "O": "#f8bc60", "R": "#d07422", "b": "#a27e4a"})
    return s, K.only(s, {K.rgba(SUN[2])[:3], K.rgba(SUN[1])[:3], K.rgba("#f8bc60")[:3]})


def tex_grass(P):
    gp = P.ramp("grass")
    rng = P.rng_for("dgrass")
    c = P.Canvas(16, 16)
    for _ in range(9):
        x = rng.randrange(1, 15)
        h = rng.randrange(6, 14)
        lean = rng.choice((-1, 0, 1))
        for i in range(h):
            xx = x + (lean * i) // 5
            c.set(xx, 15 - i, gp[min(3, i * 4 // h)])
    return c


def tex_sandfall(P):
    return K.streaks(P, "dsand", "sandfall", frames=8, alpha=200, emissive=False)


def tex_mirror(P):
    bp = P.ramp("bronze")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 6.2:
                v = 4 + (1 if (x - y) % 6 < 2 else 0)
                c.set(x, y, bp[v])
                g.set(x, y, K.rgba(SUN[2] if d < 2.5 else SUN[1] if d < 4.5 else SUN[0]))
            elif d < 7.6:
                c.set(x, y, bp[2] if (x + y) % 2 else bp[3])
            else:
                c.set(x, y, bp[1])
    return c, g


def tex_hourglass(P):
    bp = P.ramp("bronze")
    frame = K.noisy(P, "bronze", "hourglass", scale=2, lo=2, span=3)
    glass = P.Canvas(16, 16)
    sp = P.ramp("dsand")
    for y in range(16):
        w = abs(y - 7.5) / 7.5 * 6 + 0.6
        for x in range(16):
            if abs(x - 7.5) < w:
                sand = (y > 11 and abs(x - 7.5) < w) or (y < 5 and y > 2 and abs(x - 7.5) < w - 1) or (abs(x - 7.5) < 0.6)
                glass.set(x, y, sp[4] if sand else K.rgba("#e8f0f4", 90))
    for x in range(16):
        frame.set(x, 0, bp[5])
        frame.set(x, 15, bp[1])
    return frame, glass


def textures():
    P = _P()
    t = {}
    t["desert_sand"] = tex_sand(P)
    t["desert_sand_rippled"] = tex_rippled(P)
    t["desert_sandstone"] = tex_sandstone(P)
    t["desert_sandstone_carved"], t["desert_sandstone_carved_glow"] = tex_carved(P)
    t["desert_brick"] = tex_brick(P)
    t["desert_tile"] = tex_tile(P)
    t["desert_column"] = tex_column(P)
    t["desert_column_end"] = tex_column_end(P)
    t["desert_glass"] = tex_glass(P, False)
    t["desert_glass_glowing"], t["desert_glass_glowing_glow"] = tex_glass(P, True)
    t["desert_crystal"], t["desert_crystal_glow"] = tex_crystal(P)
    t["desert_grass"] = tex_grass(P)
    t["desert_sandfall"] = tex_sandfall(P)
    t["desert_mirror"], t["desert_mirror_glow"] = tex_mirror(P)
    t["sun_hourglass"], t["sun_hourglass_glass"] = tex_hourglass(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


BLOCKS = {
    "desert_sand": ("Glass Desert Sand", "shovel"),
    "desert_sand_rippled": ("Rippled Sand", "shovel"),
    "desert_sandstone": ("Desert Sandstone", "pickaxe"),
    "desert_sandstone_carved": ("Sun-Carved Sandstone", "pickaxe"),
    "desert_brick": ("Sun-Dried Brick", "pickaxe"),
    "desert_tile": ("Blue Roof Tiles", "pickaxe"),
    "desert_column": ("Fluted Column", "pickaxe"),
    "desert_glass": ("Desert Glass", None),
    "desert_glass_glowing": ("Glowing Desert Glass", None),
    "desert_crystal": ("Glass Crystal", None),
    "desert_grass": ("Dry Grass", None),
    "desert_sandfall": ("Falling Sand Curtain", None),
    "desert_mirror": ("Sun Mirror", "pickaxe"),
    "sun_hourglass": ("The Sun Hourglass", "pickaxe"),
}
FLAT = ("desert_crystal", "desert_grass")
B = K.B


def models():
    m = {}
    m["desert_sand"] = K.cube_model("desert_sand")
    m["desert_sand_rippled"] = K.cube_model("desert_sand_rippled")
    m["desert_sandstone"] = K.cube_model("desert_sandstone")
    m["desert_sandstone_carved"] = K.cube_model("desert_sandstone_carved", glow=True)
    m["desert_brick"] = K.cube_model("desert_brick")
    m["desert_tile"] = K.cube_model("desert_tile")
    m["desert_column"] = K.column_model("desert_column", "desert_column_end")
    m["desert_glass"] = K.cube_model("desert_glass", translucent=True)
    m["desert_glass_glowing"] = K.cube_model("desert_glass_glowing", glow=True, translucent=True)
    m["desert_crystal"] = K.cross_model("desert_crystal", glow=True)
    m["desert_grass"] = K.cross_model("desert_grass")
    m["desert_sandfall"] = K.fall_model("desert_sandfall", 0)
    # the sun mirror: a round bronze mirror on a forked stand; turned to the sun, it blazes
    for lit in (False, True):
        tex = {"particle": B + "desert_sandstone", "stone": B + "desert_sandstone", "face": B + "desert_mirror"}
        els = [K.box([3, 0, 3], [13, 2, 13], "#stone"), K.box([7, 2, 7], [9, 5, 9], "#stone"),
               K.box([2, 4, 7], [3, 13, 9], "#stone"), K.box([13, 4, 7], [14, 13, 9], "#stone"),
               K.box([3, 3, 7.5], [13, 14, 8.5], "#face", uv=[2, 2, 14, 14])]
        if lit:
            tex["glow"] = B + "desert_mirror_glow"
            els += [K.box([3, 3, 7.45], [13, 14, 8.55], "#glow", 15, sides=("north", "south"), uv=[2, 2, 14, 14])]
        m["desert_mirror" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    # the Sun Hourglass: bronze caps, posts and the glass between
    m["sun_hourglass"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                          "textures": {"particle": B + "sun_hourglass", "frame": B + "sun_hourglass",
                                       "glass": {"sprite": B + "sun_hourglass_glass", "force_translucent": True}},
                          "elements": [K.box([4, 0, 4], [12, 1.5, 12], "#frame"), K.box([4, 13.5, 4], [12, 15, 12], "#frame"),
                                       K.box([4.5, 1.5, 4.5], [5.5, 13.5, 5.5], "#frame"), K.box([10.5, 1.5, 10.5], [11.5, 13.5, 11.5], "#frame"),
                                       K.box([4.5, 1.5, 10.5], [5.5, 13.5, 11.5], "#frame"), K.box([10.5, 1.5, 4.5], [11.5, 13.5, 5.5], "#frame"),
                                       K.box([5.5, 1.5, 8], [10.5, 13.5, 8], "#glass", 5, sides=("north", "south"), uv=[3, 1, 13, 15]),
                                       K.box([8, 1.5, 5.5], [8, 13.5, 10.5], "#glass", 5, sides=("west", "east"), uv=[3, 1, 13, 15])]}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n not in ("desert_column", "desert_mirror")}
    s["desert_column"] = K.axis_state("desert_column")
    s["desert_mirror"] = K.lit_state("desert_mirror")
    return s


def content():
    K.content("gate_worlds_desert", BLOCKS, models(), states(), {}, flat_items=FLAT)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_desert.png")
    else:
        print(__doc__)
