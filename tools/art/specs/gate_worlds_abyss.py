"""Gate Worlds, Abyssal Shrine (SPEC §38.13): the theme's 12 world blocks and its set piece, the Dark Lantern.

    python3 tools/art/build_all.py gate_worlds_abyss        textures
    python3 tools/art/specs/gate_worlds_abyss.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_abyss.py sheet PATH every texture at 6x, named

A shrine at the bottom of everything: near-black stone with a faint violet sheen, polished black tiles, pale steps you
can see in the dark, runes that glow a little, black glass, clusters of candles, stars hanging in the void, thin dark
veils, and the eye (its iris, its pupil, the white of it). The offering bowl takes what you give it. Drawn by code.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "abyss": ["#0a0810", "#110e1a", "#181424", "#201a30", "#2a223e", "#362c4e"],
    "tile": ["#060509", "#0c0a12", "#14111c", "#1e1a28", "#2c2638", "#3e3650"],
    "pale": ["#8a8478", "#a09a8c", "#b6b0a0", "#cac4b4", "#dcd6c8", "#ece8dc"],
    "rune": ["#4a2a7a", "#6a3eaa", "#9058d8", "#b888f4", "#e0c8ff"],
    "glass": ["#04030a", "#0a0814", "#120e20", "#1c162e", "#2a2242"],
    "wax": ["#8a7a5a", "#b8a47a", "#dccaa0", "#f0e4c4", "#fcf6e4"],
    "flame": ["#c86010", "#f09020", "#ffc040", "#ffe890", "#fffce0"],
    "star": ["#7080c0", "#a0b0f0", "#d0dcff", "#f4f8ff"],
    "veil": ["#0c0a14", "#16121e", "#221c2c", "#2e263a"],
    "iris": ["#3a1a08", "#6a3010", "#a05018", "#d08024", "#f0b040", "#ffe080"],
    "sclera": ["#a89c90", "#c0b4a8", "#d4cac0", "#e4dcd2", "#f2ece4"],
    "vein": ["#6a1818", "#8a2424", "#a83434"],
    "bronze": ["#2a1e10", "#423018", "#5c4422", "#7a5c30", "#9a7840", "#c0a060"],
}


def _P():
    return K.px(RAMPS)


def tex_stone(P):
    """Near-black stone, a faint violet sheen across it and a few hairline cracks."""
    c = K.noisy(P, "abyss", "abyss-stone", scale=5, lo=0, span=4, jitter=0.1)
    K.lines(c, K.walk(P, "abyss-cracks", 3, 9, turn=0.4), P.ramp("abyss")[0])
    return c


def tex_tiles(P):
    """Polished black tiles, a thin bright edge where the light catches each one."""
    tp = P.ramp("tile")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            lx, ly = x % 8, y % 8
            v = 2 if (x // 8 + y // 8) % 2 else 1
            if lx == 0 or ly == 0:
                v = 0
            elif lx == 1 or ly == 1:
                v = 4
            elif lx + ly == 7:
                v += 1
            c.set(x, y, tp[v])
    return c


def tex_pale(P):
    """Pale steps: worn bone-white stone, darker where feet have gone."""
    c = K.noisy(P, "pale", "abyss-pale", scale=4, lo=1, span=4, jitter=0.1)
    pp = P.ramp("pale")
    for x in range(16):
        c.set(x, 15, pp[0])
        c.set(x, 0, pp[5])
    return c


def tex_rune(P):
    """Black stone with one rune cut into it, the cut glowing violet."""
    c = tex_stone(P)
    rp = P.ramp("rune")
    rune = [
        "................",
        ".......#........",
        ".......#........",
        "...#...#...#....",
        "....#..#..#.....",
        ".......#........",
        "......###.......",
        ".....#.#.#......",
        "....#..#..#.....",
        ".....#.#.#......",
        "......###.......",
        ".......#........",
        "....#..#..#.....",
        "...#...#...#....",
        ".......#........",
        "................",
    ]
    g = P.Canvas(16, 16)
    for y, row in enumerate(rune):
        for x, ch in enumerate(row):
            if ch == "#":
                c.set(x, y, rp[1])
                g.set(x, y, rp[3] if (x + y) % 3 else rp[4])
    return c, g


def tex_glass(P):
    """Black glass: almost nothing, a faint pale streak where it shines."""
    gp = P.ramp("glass")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = 1 if x in (0, 15) or y in (0, 15) else 0
            if x - y in (3, 4) or x - y in (-8,):
                v = 3
            col = gp[v]
            c.set(x, y, (*col[:3], 230 if v else 200))
    return c


def tex_candles(P):
    """A cluster of five candles of different heights, wax run down them, each flame lit."""
    wp, fp = P.ramp("wax"), P.ramp("flame")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for cx, h in ((3, 6), (6, 10), (9, 8), (12, 5), (7, 4)):
        top = 16 - h
        for y in range(top, 16):
            c.set(cx, y, wp[2 if y > top + 1 else 3])
            c.set(cx + 1, y, wp[1 if (y + cx) % 4 else 0])
        c.set(cx, top - 1, wp[0])
        for fy, col in ((top - 2, fp[2]), (top - 3, fp[3]), (top - 4, fp[4])):
            if fy >= 0:
                c.set(cx, fy, col)
                g.set(cx, fy, col)
    return c, g


def tex_star(P):
    """A star hanging in the dark: a bright point, four thin rays, a soft halo."""
    sp = P.ramp("star")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r < 1.2:
                c.set(x, y, sp[3])
            elif (abs(x - 7.5) < 0.6 or abs(y - 7.5) < 0.6) and r < 6:
                c.set(x, y, (*sp[2][:3], int(255 * (1 - r / 6.5))))
            elif r < 3:
                c.set(x, y, (*sp[1][:3], 120))
    return c, c.copy()


def tex_veil(P):
    """Thin dark veils hanging in folds, ragged at the bottom."""
    vp = P.ramp("veil")
    rng = P.rng_for("abyss-veil")
    c = P.Canvas(16, 16)
    for x in range(16):
        end = 11 + rng.randrange(5)
        for y in range(end):
            v = 1 + int((math.sin(x * 1.1) + 1) * 1.2)
            c.set(x, y, (*vp[min(3, v)][:3], 200))
    return c


def tex_iris(P):
    """The iris of the eye, read a block at a time: amber streaks running out from the middle of the tile."""
    ip = P.ramp("iris")
    rng = P.rng_for("abyss-iris")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            a = math.atan2(y - 7.5, x - 7.5)
            v = 2 + int((math.sin(a * 9 + rng.random() * 0.6) + 1) * 1.4)
            c.set(x, y, ip[min(5, v)])
    g = K.only(c, {ip[4][:3], ip[5][:3]})
    return c, g


def tex_pupil(P):
    c = K.noisy(P, "glass", "abyss-pupil", scale=6, lo=0, span=2, jitter=0.04)
    return c


def tex_sclera(P):
    """The white of the eye, wet and pale, with red veins through it."""
    c = K.noisy(P, "sclera", "abyss-sclera", scale=5, lo=1, span=4, jitter=0.06)
    vp = P.ramp("vein")
    K.lines(c, K.walk(P, "abyss-veins", 3, 12, turn=0.5), vp[1])
    K.lines(c, K.walk(P, "abyss-veins-fine", 3, 6, turn=0.7), vp[2])
    return c


def tex_offering(P, lit):
    """The offering bowl: dark bronze on a short foot, a pale ember in the bowl once something's been given."""
    bp = P.ramp("bronze")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = 2 + int(math.sin(x * 0.6) * 1.2)
            if y in (0, 1):
                v = 4
            if y > 12:
                v = 1
            c.set(x, y, bp[max(0, min(5, v))])
    if not lit:
        return c
    g = P.Canvas(16, 16)
    rp = P.ramp("rune")
    for x in range(16):
        g.set(x, 0, rp[4])
        g.set(x, 1, rp[3])
    return c, g


def tex_dark_lantern(P):
    """The Dark Lantern: a black iron cage around a dim violet flame."""
    rp = P.ramp("rune")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15) or x in (7, 8)
            c.set(x, y, (24, 20, 30, 255) if edge else (*rp[2][:3], 255))
    for y in range(5, 12):
        for x in (3, 4, 11, 12):
            c.set(x, y, rp[4])
    g = K.only(c, {rp[2][:3], rp[4][:3]})
    return c, g


def textures():
    P = _P()
    t = {}
    t["shrine_stone"] = tex_stone(P)
    t["abyss_tiles"] = tex_tiles(P)
    t["abyss_pale"] = tex_pale(P)
    t["abyss_rune"], t["abyss_rune_glow"] = tex_rune(P)
    t["abyss_glass"] = tex_glass(P)
    t["abyss_candles"], t["abyss_candles_glow"] = tex_candles(P)
    t["abyss_star"], t["abyss_star_glow"] = tex_star(P)
    t["abyss_veil"] = tex_veil(P)
    t["abyss_iris"], t["abyss_iris_glow"] = tex_iris(P)
    t["abyss_pupil"] = tex_pupil(P)
    t["abyss_sclera"] = tex_sclera(P)
    t["abyss_offering"] = tex_offering(P, False)
    _, t["abyss_offering_glow"] = tex_offering(P, True)
    t["dark_lantern"], t["dark_lantern_glow"] = tex_dark_lantern(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


BLOCKS = {
    "shrine_stone": ("Abyssal Stone", "pickaxe"),
    "abyss_tiles": ("Black Tiles", "pickaxe"),
    "abyss_pale": ("Pale Step", "pickaxe"),
    "abyss_rune": ("Rune Stone", "pickaxe"),
    "abyss_glass": ("Black Glass", None),
    "abyss_candles": ("Vigil Candles", None),
    "abyss_star": ("Hanging Star", None),
    "abyss_veil": ("Dark Veil", None),
    "abyss_iris": ("Iris", "pickaxe"),
    "abyss_pupil": ("Pupil", "pickaxe"),
    "abyss_sclera": ("The White of the Eye", "pickaxe"),
    "abyss_offering": ("Offering Bowl", "pickaxe"),
    "dark_lantern": ("The Dark Lantern", None),
}
FLAT = ("abyss_candles", "abyss_star", "abyss_veil")
B = K.B


def models():
    m = {}
    for n in ("shrine_stone", "abyss_tiles", "abyss_pale", "abyss_pupil", "abyss_sclera"):
        m[n] = K.cube_model(n)
    m["abyss_rune"] = K.cube_model("abyss_rune", glow=True)
    m["abyss_iris"] = K.cube_model("abyss_iris", glow=True)
    m["abyss_glass"] = K.cube_model("abyss_glass", translucent=True)
    m["abyss_candles"] = K.cross_model("abyss_candles", glow=True)
    m["abyss_star"] = K.cross_model("abyss_star", glow=True)
    m["abyss_veil"] = K.cross_model("abyss_veil")
    # the offering bowl on its foot: dark, and lit a while after an offering
    for lit in (False, True):
        tex = {"particle": B + "abyss_offering", "b": B + "abyss_offering"}
        els = [K.box([6, 0, 6], [10, 3, 10], "#b", uv=[6, 12, 10, 16]), K.box([3, 3, 3], [13, 7, 13], "#b", uv=[0, 2, 16, 8]),
               K.box([4, 7, 4], [12, 7.5, 12], "#b", uv=[2, 0, 14, 2])]
        if lit:
            tex["glow"] = B + "abyss_offering_glow"
            els.append(K.box([4, 6.5, 4], [12, 7.6, 12], "#glow", 15, sides=("up",), uv=[2, 0, 14, 2]))
        m["abyss_offering" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": els}
    m["dark_lantern"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                         "textures": {"particle": B + "dark_lantern", "l": B + "dark_lantern", "glow": B + "dark_lantern_glow"},
                         "elements": [K.box([5, 0, 5], [11, 9, 11], "#l", uv=[2, 3, 14, 16]),
                                      K.box([5, 0, 5], [11, 9, 11], "#glow", 12, sides=("north", "south", "east", "west"), uv=[2, 3, 14, 16]),
                                      K.box([4.5, 9, 4.5], [11.5, 10.5, 11.5], "#l", uv=[0, 0, 16, 2]),
                                      K.box([7, 10.5, 7], [9, 13, 9], "#l", uv=[0, 0, 2, 3])]}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n != "abyss_offering"}
    s["abyss_offering"] = K.lit_state("abyss_offering")
    return s


def content():
    K.content("gate_worlds_abyss", BLOCKS, models(), states(), {}, flat_items=FLAT)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_abyss.png")
    else:
        print(__doc__)
