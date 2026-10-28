"""Gate Worlds, Nightmare (SPEC §38.12): the theme's 14 world blocks and its set piece, the Night Light.

    python3 tools/art/build_all.py gate_worlds_dream        textures
    python3 tools/art/specs/gate_worlds_dream.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_dream.py sheet PATH every texture at 6x, named

A child's house dreamed too big: plum wallpaper with a faded pattern (and torn, the plaster showing), worn floorboards,
dark posts, a tufted cushion, a white sheet, the dark between dreams with a few far lights in it, a bedside lamp, a
candle, motes drifting, a toy block, a window with the night glowing in it, and the music box that plays the lullaby.
Drawn by code, nothing copied.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "plum": ["#2a1430", "#3a1c42", "#4a2654", "#5c3266", "#704078", "#88548c"],
    "plaster": ["#8c8478", "#a49c8e", "#bab2a2", "#ccc4b4"],
    "board": ["#3a2414", "#4c301c", "#5e3c24", "#70482c", "#845636"],
    "post": ["#1c1008", "#2a1810", "#382218", "#462c20"],
    "plush": ["#5a1e3a", "#74284a", "#8e345c", "#a8446e", "#c05a84"],
    "sheet": ["#a8a8b8", "#bcbccc", "#d0d0dc", "#e2e2ea", "#f0f0f6"],
    "night": ["#020208", "#04040e", "#080816", "#0c0c20"],
    "warm": ["#7a4010", "#b06818", "#e09a30", "#ffc860", "#ffe8a8"],
}


def _P():
    return K.px(RAMPS)


def tex_wallpaper(P, torn):
    """Plum wallpaper with a faded damask (a little four-petal flower on a lattice); torn, the plaster shows through."""
    pp, pl = P.ramp("plum"), P.ramp("plaster")
    rng = P.rng_for("dream-wallpaper" + ("-torn" if torn else ""))
    n = P.value_noise(16, 16, rng, scale=6, octaves=2)
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            lx, ly = x % 8, y % 8
            petal = (abs(lx - 4) + abs(ly - 4) <= 2) and (lx == 4 or ly == 4)
            lattice = (lx + ly) % 8 == 0 or (lx - ly) % 8 == 0
            v = 4 if petal else 3 if lattice else 2
            c.set(x, y, pp[v - (1 if n[y][x] < 0.35 else 0)])
    if torn:
        # a ragged tear down the middle, the plaster under it, the paper's edge curling pale
        for y in range(16):
            w = 2 + int(2.5 * math.sin(y * 0.6) + rng.random() * 1.5)
            x0 = 6 + int(math.sin(y * 0.35) * 2)
            for x in range(x0, min(16, x0 + w)):
                c.set(x, y, pl[1 + (x + y) % 2])
            if x0 > 0:
                c.set(x0 - 1, y, pp[5])
            if x0 + w < 16:
                c.set(x0 + w, y, pp[5])
    return c


def tex_boards(P):
    return K.boards(P, "board", "dream-boards", h=4, nails=True)


def tex_wood(P):
    pp = P.ramp("post")
    c = P.Canvas(16, 16)
    rng = P.rng_for("dream-wood")
    for y in range(16):
        for x in range(16):
            grain = int(1.5 + math.sin(x * 1.3 + y * 0.15) * 1.2)
            c.set(x, y, pp[max(0, min(3, grain))] if x not in (0, 15) else pp[0])
    for _ in range(3):
        c.set(rng.randrange(2, 14), rng.randrange(16), pp[3])
    return c


def tex_wood_top(P):
    pp = P.ramp("post")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            c.set(x, y, pp[0] if r > 7 else pp[1 + int(r) % 3 // 2 + (1 if int(r) % 3 == 0 else 0)])
    return c


def tex_plush(P):
    """A deep pink cushion, tufted: a button sunk in each square, the cloth puffing between."""
    pp = P.ramp("plush")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = (x % 8) - 3.5, (y % 8) - 3.5
            d = max(abs(dx), abs(dy))
            v = 3 if d < 2 else 2 if d < 3.2 else 1
            c.set(x, y, pp[v])
    for bx, by in ((0, 0), (8, 0), (0, 8), (8, 8)):
        c.set(bx, by, pp[0])
    return c


def tex_sheet(P):
    sp = P.ramp("sheet")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            fold = math.sin(x * 0.55 + math.sin(y * 0.3) * 1.5)
            c.set(x, y, sp[2 + int(round(fold))])
    return c


def tex_dark(P):
    """The dark between dreams: near black, a few far lights."""
    np = P.ramp("night")
    rng = P.rng_for("dream-dark")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, np[rng.randrange(3)])
    for col in ("#c8a0ff", "#ffe8a8", "#a8c8ff"):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, K.rgba(col))
        g.set(x, y, K.rgba(col, 200))
    return c, g


def tex_lamp(P):
    """A bedside lamp: a pleated shade glowing warm, the base dark wood."""
    wp = P.ramp("warm")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if y < 9:
                v = 3 if x % 3 else 2
                c.set(x, y, wp[v])
                g.set(x, y, wp[v + 1] if y > 1 else wp[v])
            else:
                c.set(x, y, P.ramp("post")[1 + (x % 4 == 0)])
    return c, g


def tex_candle(P):
    rows = ["................",
            "................",
            ".......F........",
            "......FHF.......",
            "......FHF.......",
            ".......F........",
            ".......k........",
            "......WWW.......",
            "......WwW.......",
            "......WWW.......",
            "......WwW.......",
            "......WWW.......",
            "......WWW.......",
            ".....hhhhh......",
            "....hhhhhhh.....",
            "................"]
    pal = {"F": "#ffb040", "H": "#fff0c0", "k": "#2a1810", "W": "#f0e6d0", "w": "#d8ccb4", "h": "#6a5a48"}
    c = K.sprite(rows, pal)
    g = K.only(c, {K.rgba(pal["F"])[:3], K.rgba(pal["H"])[:3]})
    return c, g


def tex_motes(P):
    rng = P.rng_for("dream-motes")
    c = P.Canvas(16, 16)
    for _ in range(7):
        x, y = rng.randrange(16), rng.randrange(16)
        col = ("#c8a0ff", "#ffe8a8", "#ffffff")[rng.randrange(3)]
        c.set(x, y, K.rgba(col))
    return c, c


def tex_toy(P):
    """A painted alphabet block: a colour on each face, a letter shape cut in."""
    c = P.Canvas(16, 16)
    rows = ["..AAAAA...", ".A.....A..", ".A.....A..", ".AAAAAAA..", ".A.....A..", ".A.....A..", ".A.....A.."]
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            c.set(x, y, K.rgba("#e8dcc0") if edge else K.rgba("#c83838"))
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "A":
                c.set(3 + i, 4 + j, K.rgba("#f4e8c8"))
    return c


def tex_window(P):
    """A window at night: dark wood bars and four panes glowing the deep blue of a lit sky."""
    pp = P.ramp("post")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    rng = P.rng_for("dream-window")
    for y in range(16):
        for x in range(16):
            bar = x in (0, 7, 8, 15) or y in (0, 7, 8, 15)
            if bar:
                c.set(x, y, pp[2])
            else:
                col = K.lerp(K.rgba("#1a2a6a"), K.rgba("#4a6ac8"), y / 15)
                c.set(x, y, (*col[:3], 200))
                g.set(x, y, (*col[:3], 150))
    for _ in range(4):
        x, y = rng.randrange(1, 15), rng.randrange(1, 7)
        if x not in (7, 8):
            c.set(x, y, K.rgba("#fff0c0"))
            g.set(x, y, K.rgba("#ffffff"))
    return c, g


def tex_music_box(P):
    """The music box: painted wood with a gilt band, a crank; open, a little dancer turns under a warm light."""
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    pp = P.ramp("plush")
    for y in range(16):
        for x in range(16):
            band = y in (3, 4)
            c.set(x, y, K.rgba("#d8a840") if band else pp[1 + ((x // 4 + y // 4) % 2)])
    for x, y in ((7, 9), (8, 9), (7, 10), (8, 10), (6, 11), (9, 11)):
        c.set(x, y, K.rgba("#f0e0c8"))
        g.set(x, y, K.rgba("#ffe8a8"))
    return c, g


def tex_night_light(P):
    """The Night Light: a little moon in a frosted dome; on, it glows."""
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if y >= 12:
                c.set(x, y, P.ramp("post")[2])
            else:
                c.set(x, y, K.rgba("#c8d0f0" if r < 4 else "#8890b8"))
                if r < 4 and math.hypot(x - 9, y - 6.5) > 3:
                    c.set(x, y, K.rgba("#fff4c8"))
                    g.set(x, y, K.rgba("#fff8e0"))
    return c, g


def textures():
    P = _P()
    t = {}
    t["dream_wallpaper"] = tex_wallpaper(P, False)
    t["dream_wallpaper_torn"] = tex_wallpaper(P, True)
    t["dream_boards"] = tex_boards(P)
    t["dream_wood"] = tex_wood(P)
    t["dream_wood_top"] = tex_wood_top(P)
    t["dream_plush"] = tex_plush(P)
    t["dream_sheet"] = tex_sheet(P)
    t["dream_dark"], t["dream_dark_glow"] = tex_dark(P)
    t["dream_lamp"], t["dream_lamp_glow"] = tex_lamp(P)
    t["dream_candle"], t["dream_candle_glow"] = tex_candle(P)
    t["dream_motes"], t["dream_motes_glow"] = tex_motes(P)
    t["dream_toy"] = tex_toy(P)
    t["dream_window"], t["dream_window_glow"] = tex_window(P)
    t["dream_music_box"], t["dream_music_box_glow"] = tex_music_box(P)
    t["night_light"], t["night_light_glow"] = tex_night_light(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


BLOCKS = {
    "dream_wallpaper": ("Plum Wallpaper", "axe"),
    "dream_wallpaper_torn": ("Torn Wallpaper", "axe"),
    "dream_boards": ("Worn Floorboards", "axe"),
    "dream_wood": ("Dark Post", "axe"),
    "dream_plush": ("Tufted Cushion", None),
    "dream_sheet": ("White Sheet", None),
    "dream_dark": ("The Dark Between Dreams", "pickaxe"),
    "dream_lamp": ("Bedside Lamp", None),
    "dream_candle": ("Dream Candle", None),
    "dream_motes": ("Drifting Motes", None),
    "dream_toy": ("Toy Block", "axe"),
    "dream_window": ("Night Window", None),
    "dream_music_box": ("Music Box", "axe"),
    "night_light": ("The Night Light", None),
}
FLAT = ("dream_candle", "dream_motes")
B = K.B


def models():
    m = {}
    for n in ("dream_wallpaper", "dream_wallpaper_torn", "dream_boards", "dream_plush", "dream_sheet", "dream_toy"):
        m[n] = K.cube_model(n)
    m["dream_wood"] = K.column_model("dream_wood", "dream_wood_top")
    m["dream_dark"] = K.cube_model("dream_dark", glow=True)
    m["dream_window"] = K.cube_model("dream_window", glow=True, translucent=True)
    m["dream_candle"] = K.cross_model("dream_candle", glow=True)
    m["dream_motes"] = K.cross_model("dream_motes", glow=True)
    # the bedside lamp: a dark foot and stem, the shade glowing round the bulb
    m["dream_lamp"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                       "textures": {"particle": B + "dream_lamp", "lamp": B + "dream_lamp", "glow": B + "dream_lamp_glow"},
                       "elements": [K.box([5.5, 0, 5.5], [10.5, 1.5, 10.5], "#lamp", uv=[0, 10, 16, 16]),
                                    K.box([7.5, 1.5, 7.5], [8.5, 8, 8.5], "#lamp", uv=[6, 10, 8, 16]),
                                    K.box([4, 8, 4], [12, 15, 12], "#lamp", uv=[0, 1, 16, 8]),
                                    K.box([4, 8, 4], [12, 15, 12], "#glow", 15, sides=("north", "south", "east", "west"), uv=[0, 1, 16, 8])]}
    # the music box: shut (dark), and lit while it plays
    for lit in (False, True):
        tex = {"particle": B + "dream_music_box", "box": B + "dream_music_box"}
        els = [K.box([3, 0, 4], [13, 8, 12], "#box"), K.box([3, 8, 4], [13, 10, 12], "#box", uv=[0, 2, 16, 6]),
               K.box([13, 4, 7.5], [14.5, 5, 8.5], "#box", uv=[0, 3, 2, 4]), K.box([14, 5, 7.5], [14.5, 7, 8.5], "#box", uv=[0, 3, 1, 5])]
        if lit:
            tex["glow"] = B + "dream_music_box_glow"
            els.append(K.box([2.98, -0.02, 3.98], [13.02, 8.02, 12.02], "#glow", 15, sides=("north", "south", "east", "west")))
        m["dream_music_box" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    m["night_light"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                        "textures": {"particle": B + "night_light", "dome": B + "night_light", "glow": B + "night_light_glow"},
                        "elements": [K.box([5, 0, 5], [11, 2, 11], "#dome", uv=[2, 12, 14, 16]),
                                     K.box([5.5, 2, 5.5], [10.5, 9, 10.5], "#dome", uv=[2, 0, 14, 12]),
                                     K.box([5.48, 2, 5.48], [10.52, 9.02, 10.52], "#glow", 12, uv=[2, 0, 14, 12])]}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n not in ("dream_wood", "dream_music_box")}
    s["dream_wood"] = K.axis_state("dream_wood")
    s["dream_music_box"] = K.lit_state("dream_music_box")
    return s


def content():
    K.content("gate_worlds_dream", BLOCKS, models(), states(), {}, flat_items=FLAT)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_dream.png")
    else:
        print(__doc__)
