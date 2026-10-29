"""Gate Worlds, Drowned Archive (SPEC §38.11): the theme's 13 world blocks and its set piece, the Diving Lamp.

    python3 tools/art/build_all.py gate_worlds_archive        textures
    python3 tools/art/specs/gate_worlds_archive.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_archive.py sheet PATH every texture at 6x, named

Wet blue-grey stone in courses, stone carved with lines of writing that glow sea-green, tall shelves of swollen books
(some still lit from inside), cracked white tiles, dark reading-room planks, green-glass lamps, drifts of loose pages, kelp
that glows faintly, sea glass with the water moving in it, a fall of ink, the lectern with its open book, and the paper
bridge that folds and unfolds. Drawn by code, nothing copied.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "astone": ["#151d22", "#1e292f", "#28363d", "#33444b", "#40535a", "#4e636a"],
    "aglow": ["#2a8a78", "#4fc8b0", "#8ff0dc", "#d8fff4"],
    "tile": ["#5a6460", "#8a948e", "#a8b0aa", "#c2c8c0", "#d6dad2", "#e6e8e0"],
    "oak": ["#1c120c", "#271910", "#332115", "#3f2a1a", "#4c3320", "#5a3d27"],
    "paper": ["#8e8670", "#a8a088", "#beb69c", "#d2caae", "#e2dac0", "#efe8d2"],
    "kelp": ["#0e2a1c", "#164026", "#1f5631", "#2a6c3c", "#378448"],
    "glass": ["#0c3a3c", "#14545a", "#1e6e74", "#2c8a8c", "#46a8a4", "#78d0c4"],
    "ink": ["#05060a", "#0a0d16", "#121826", "#1c2638", "#2a3a52"],
    "brass": ["#3a2a10", "#5a4218", "#7a5c22", "#9c7a30", "#c09c48", "#e0c070"],
    "leather": ["#2a1810", "#3a2216", "#4c2e1e", "#5e3a26"],
}
GLOW = ("#4fc8b0", "#8ff0dc", "#d8fff4")
BOOKS = ("#5a1e1e", "#1e3a5a", "#2a4a2a", "#5a4a1e", "#3a1e4a", "#1e4a4a", "#6a3a1a")


def _P():
    return K.px(RAMPS)


def tex_stone(P):
    c = K.ashlar(P, "astone", "archive", course=8)
    rng = P.rng_for("archive-wet")
    for _ in range(10):
        x, y = rng.randrange(16), rng.randrange(16)
        c.blend(x, y, K.rgba("#4fc8b0"), 0.18)
    return c


def tex_carved(P):
    c = tex_stone(P)
    g = P.Canvas(16, 16)
    rng = P.rng_for("archive-writing")
    # three lines of a script nobody reads any more, cut into the stone
    for row in (3, 7, 11):
        x = 2
        while x < 14:
            w = rng.randrange(1, 4)
            for i in range(w):
                if x + i < 14:
                    c.set(x + i, row, P.ramp("astone")[0])
                    g.set(x + i, row, K.rgba(GLOW[0] if i else GLOW[1]))
                    if rng.random() < 0.4:
                        g.set(x + i, row - 1, K.rgba(GLOW[0], 200))
            x += w + 1
    return K.under(c, g, 0.5), g


def _spines(P, seed, lit):
    """A shelf of books: spines of every colour on a dark board, the odd one leaning; lit ones show a glowing title."""
    op = P.ramp("oak")
    rng = P.rng_for("spines", seed)
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, op[1])
    for shelf in (0, 8):
        for x in range(16):
            c.set(x, shelf, op[4])
            c.set(x, shelf + 7, op[2])
        x = 1
        while x < 15:
            w = rng.randrange(1, 3)
            h = rng.randrange(4, 7)
            col = K.rgba(BOOKS[rng.randrange(len(BOOKS))])
            dark = tuple(max(0, v - 30) for v in col[:3]) + (255,)
            for i in range(w):
                for yy in range(shelf + 7 - h, shelf + 7):
                    c.set(x + i, yy, dark if i == w - 1 else col)
            c.set(x, shelf + 7 - h + 1, K.rgba("#c8b070"))
            if lit and rng.random() < 0.45:
                g.set(x, shelf + 7 - h + 2, K.rgba(GLOW[1]))
                g.set(x, shelf + 7 - h + 3, K.rgba(GLOW[0]))
            x += w + (1 if rng.random() < 0.2 else 0)
    # swollen with water: a tide line and a little green at the foot
    for x in range(16):
        if rng.random() < 0.5:
            c.blend(x, 14, K.rgba("#2a6c3c"), 0.5)
    return c, g


def tex_shelf(P):
    c, _ = _spines(P, "plain", False)
    return c


def tex_shelf_lit(P):
    c, g = _spines(P, "lit", True)
    return K.under(c, g, 0.6), g


def tex_shelf_top(P):
    return K.boards(P, "oak", "shelf-top", h=8)


def tex_tiles(P):
    c = K.tiles(P, "tile", "archive-tiles", size=8)
    rng = P.rng_for("tile-silt")
    for _ in range(8):
        c.blend(rng.randrange(16), rng.randrange(16), K.rgba("#4e636a"), 0.4)
    return c


def tex_planks(P):
    return K.boards(P, "oak", "archive-planks", h=4)


def tex_lamp(P):
    bp = P.ramp("brass")
    cage = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if x in (0, 7, 8, 15) or y in (0, 1, 14, 15):
                cage.set(x, y, bp[4] if (x + y) % 3 else bp[5])
    glow = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 8)
            glow.set(x, y, K.rgba(GLOW[2]) if d < 2.5 else K.rgba(GLOW[1]) if d < 5 else K.rgba(GLOW[0]))
    return cage, glow


def tex_paper(P):
    pp = P.ramp("paper")
    rng = P.rng_for("pages")
    c = P.Canvas(16, 16)
    for _ in range(7):
        x0, y0 = rng.randrange(16), rng.randrange(16)
        w, h = rng.randrange(4, 7), rng.randrange(3, 6)
        shade = rng.randrange(2, 6)
        for y in range(h):
            for x in range(w):
                c.set((x0 + x) % 16, (y0 + y) % 16, pp[shade])
            if y % 2 == 1:
                for x in range(1, w - 1):
                    if rng.random() < 0.7:
                        c.set((x0 + x) % 16, (y0 + y) % 16, K.rgba("#3a3a4a"))
    return c


def tex_kelp(P):
    kp = P.ramp("kelp")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for stalk, ph in ((4, 0.0), (8, 1.7), (12, 3.1)):
        for y in range(16):
            x = stalk + int(round(math.sin(y * 0.5 + ph) * 1.2))
            c.set(x, y, kp[3])
            c.set(x + 1, y, kp[2])
            if y % 4 == int(ph) % 4:
                c.set(x - 1, y, kp[4])
                c.set(x + 2, y, kp[1])
            if y % 5 == 2:
                g.set(x, y, K.rgba(GLOW[0]))
                c.set(x, y, K.rgba(GLOW[1]))
    return c, g


def tex_sea_glass(P):
    """Sea glass with the water moving through it: 8 frames, a slow drift of bright caustics."""
    gp = P.ramp("glass")
    frames, glows = [], []
    for f in range(8):
        c = P.Canvas(16, 16)
        g = P.Canvas(16, 16)
        for y in range(16):
            for x in range(16):
                t = f / 8 * math.tau
                v = (math.sin(x * 0.7 + t) + math.sin(y * 0.6 - t * 0.5 + x * 0.3) + math.sin((x + y) * 0.35 + t * 0.7)) / 3
                k = max(0, min(5, int((v + 1) / 2 * 5.9)))
                edge = x in (0, 15) or y in (0, 15)
                c.set(x, y, (*gp[5 if edge else k][:3], 235 if edge else 150 + k * 12))
                if v > 0.62 and not edge:
                    g.set(x, y, K.rgba(GLOW[1], 200))
        frames.append(c)
        glows.append(g)
    return frames, glows


def tex_inkfall(P):
    return K.streaks(P, "ink", "inkfall", frames=8, alpha=230)


def tex_lectern(P):
    op = P.ramp("oak")
    wood = K.boards(P, "oak", "lectern", h=4, nails=False)
    page = P.Canvas(16, 16)
    pp = P.ramp("paper")
    for y in range(16):
        for x in range(16):
            page.set(x, y, pp[4] if x not in (7, 8) else pp[2])
            if y % 2 == 1 and 1 < x < 14 and x not in (7, 8) and (x * 3 + y) % 5:
                page.set(x, y, K.rgba("#3a3a4a"))
    glow = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if y % 2 == 1 and 1 < x < 14 and x not in (7, 8) and (x * 3 + y) % 5:
                glow.set(x, y, K.rgba(GLOW[1]))
    cover = P.Canvas(16, 16)
    lp = P.ramp("leather")
    for y in range(16):
        for x in range(16):
            cover.set(x, y, lp[1 + (x + y * 2) % 3] if x not in (0, 15) else op[0])
    return wood, page, glow, cover


def tex_bridge(P):
    """The paper bridge: opened, sheets laid edge to edge with lines of writing that glow; folded, a pale crease."""
    pp = P.ramp("paper")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, pp[3 + (1 if (x // 4 + y // 8) % 2 else 0)])
            if x % 4 == 0:
                c.set(x, y, pp[1])
            elif y % 3 == 1 and x % 4 != 3 and (x * 5 + y) % 7:
                g.set(x, y, K.rgba(GLOW[0], 220))
                c.set(x, y, K.rgba("#2a6c70"))
    return K.under(c, g, 0.4), g


def tex_diving(P):
    bp = P.ramp("brass")
    shell = K.noisy(P, "brass", "diving", scale=2, lo=2, span=4)
    for y in range(16):
        for x in range(16):
            if (x + y * 3) % 11 == 0:
                shell.set(x, y, bp[5])
            if x in (0, 15) or y in (0, 15):
                shell.set(x, y, bp[1])
    port = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 7.5:
                port.set(x, y, K.rgba(GLOW[2]) if d < 2.5 else K.rgba(GLOW[1]) if d < 5 else K.rgba(GLOW[0]))
            if 6 < d < 7.5:
                port.set(x, y, bp[4])
    return shell, port


def textures():
    P = _P()
    t = {}
    t["archive_stone"] = tex_stone(P)
    t["archive_stone_carved"], t["archive_stone_carved_glow"] = tex_carved(P)
    t["archive_shelf"] = tex_shelf(P)
    t["archive_shelf_lit"], t["archive_shelf_lit_glow"] = tex_shelf_lit(P)
    t["archive_shelf_top"] = tex_shelf_top(P)
    t["archive_tiles"] = tex_tiles(P)
    t["archive_planks"] = tex_planks(P)
    t["archive_lamp"], t["archive_lamp_glow"] = tex_lamp(P)
    t["archive_paper"] = tex_paper(P)
    t["archive_kelp"], t["archive_kelp_glow"] = tex_kelp(P)
    t["archive_sea_glass"], t["archive_sea_glass_glow"] = tex_sea_glass(P)
    t["archive_inkfall"] = tex_inkfall(P)
    t["archive_lectern"], t["archive_lectern_page"], t["archive_lectern_glow"], t["archive_lectern_cover"] = tex_lectern(P)
    t["archive_paper_bridge"], t["archive_paper_bridge_glow"] = tex_bridge(P)
    t["diving_lamp"], t["diving_lamp_port"] = tex_diving(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 4 if isinstance(c, list) else None)


BLOCKS = {
    "archive_stone": ("Archive Stone", "pickaxe"),
    "archive_stone_carved": ("Written Archive Stone", "pickaxe"),
    "archive_shelf": ("Drowned Shelf", "axe"),
    "archive_shelf_lit": ("Glowing Drowned Shelf", "axe"),
    "archive_tiles": ("Archive Tiles", "pickaxe"),
    "archive_planks": ("Reading Room Planks", "axe"),
    "archive_lamp": ("Reading Lamp", "pickaxe"),
    "archive_paper": ("Loose Pages", None),
    "archive_kelp": ("Archive Kelp", None),
    "archive_sea_glass": ("Sea Glass", None),
    "archive_inkfall": ("Falling Ink", None),
    "archive_lectern": ("Archive Lectern", "axe"),
    "archive_paper_bridge": ("Paper Bridge", None),
    "diving_lamp": ("The Diving Lamp", "pickaxe"),
}
FLAT = ("archive_kelp",)
B = K.B


def models():
    m = {}
    m["archive_stone"] = K.cube_model("archive_stone")
    m["archive_stone_carved"] = K.cube_model("archive_stone_carved", glow=True)
    m["archive_shelf"] = K.column_model("archive_shelf", "archive_shelf_top")
    m["archive_shelf_lit"] = K.column_model("archive_shelf_lit", "archive_shelf_top", glow=True)
    m["archive_tiles"] = K.cube_model("archive_tiles")
    m["archive_planks"] = K.cube_model("archive_planks")
    m["archive_paper"] = K.carpet_model("archive_paper")
    m["archive_kelp"] = K.cross_model("archive_kelp", glow=True)
    m["archive_sea_glass"] = K.cube_model("archive_sea_glass", glow=True, translucent=True)
    m["archive_inkfall"] = K.fall_model("archive_inkfall", 4)
    # the reading lamp: a green glass bulb in a brass cage on a heavy foot
    m["archive_lamp"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                         "textures": {"particle": B + "archive_lamp", "cage": B + "archive_lamp", "glow": B + "archive_lamp_glow",
                                      "brass": B + "diving_lamp"},
                         "elements": [K.box([5, 2, 5], [11, 10, 11], "#glow", 15),
                                      K.box([4, 1, 4], [12, 11, 12], "#cage", sides=("north", "south", "west", "east")),
                                      K.box([4, 0, 4], [12, 2, 12], "#brass"), K.box([4.5, 11, 4.5], [11.5, 12, 11.5], "#brass"),
                                      K.box([7, 12, 7], [9, 13, 9], "#brass")]}
    # the lectern: a slanted desk with an open book; touched, its writing glows
    for lit in (False, True):
        tex = {"particle": B + "archive_planks", "wood": B + "archive_lectern", "page": B + "archive_lectern_page",
               "cover": B + "archive_lectern_cover"}
        els = [K.box([4, 0, 4], [12, 2, 12], "#wood"), K.box([6.5, 2, 6.5], [9.5, 11, 9.5], "#wood"),
               {"from": [2, 11, 3], "to": [14, 13, 13], "rotation": {"origin": [8, 12, 8], "axis": "x", "angle": -22.5},
                "faces": {d: {"texture": "#wood"} for d in K.SIDES}},
               {"from": [2.5, 13, 3.5], "to": [13.5, 13.6, 12.5], "rotation": {"origin": [8, 12, 8], "axis": "x", "angle": -22.5},
                "faces": {"up": {"texture": "#page"}, "north": {"texture": "#cover"}, "south": {"texture": "#cover"},
                          "west": {"texture": "#cover"}, "east": {"texture": "#cover"}}}]
        if lit:
            tex["glow"] = B + "archive_lectern_glow"
            els.append({"from": [2.5, 13.62, 3.5], "to": [13.5, 13.62, 12.5], "rotation": {"origin": [8, 12, 8], "axis": "x", "angle": -22.5},
                        "faces": {"up": {"texture": "#glow"}}, "light_emission": 15})
        m["archive_lectern" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    # the paper bridge: folded, a narrow pale crease you can still stand on; opened, a full sheet with glowing writing
    m["archive_paper_bridge"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                                 "textures": {"particle": B + "archive_paper_bridge", "sheet": B + "archive_paper_bridge",
                                              "glow": B + "archive_paper_bridge_glow"},
                                 "elements": [K.box([6, 15, 0], [10, 16, 16], "#sheet", uv=[6, 0, 10, 16]),
                                              K.box([6, 16.02, 0], [10, 16.02, 16], "#glow", 8, sides=("up",), uv=[6, 0, 10, 16])]}
    m["archive_paper_bridge_open"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                                      "textures": {"particle": B + "archive_paper_bridge", "sheet": B + "archive_paper_bridge",
                                                   "glow": B + "archive_paper_bridge_glow"},
                                      "elements": [K.box([0, 13, 0], [16, 16, 16], "#sheet"),
                                                   K.box([0, 16.02, 0], [16, 16.02, 16], "#glow", 15, sides=("up",))]}
    # the Diving Lamp: a brass diving helmet with its round port lit sea-green
    m["diving_lamp"] = {"parent": "minecraft:block/block",
                        "textures": {"particle": B + "diving_lamp", "brass": B + "diving_lamp", "port": B + "diving_lamp_port"},
                        "elements": [K.box([3, 0, 3], [13, 2, 13], "#brass"), K.box([4, 2, 4], [12, 12, 12], "#brass"),
                                     K.box([5, 12, 5], [11, 14, 11], "#brass"), K.box([7, 14, 7], [9, 15, 9], "#brass"),
                                     K.box([5.5, 4, 3.9], [10.5, 9, 3.9], "#port", 15, sides=("north",)),
                                     K.box([5.5, 4, 12.1], [10.5, 9, 12.1], "#port", 15, sides=("south",)),
                                     K.box([3.9, 4, 5.5], [3.9, 9, 10.5], "#port", 15, sides=("west",)),
                                     K.box([12.1, 4, 5.5], [12.1, 9, 10.5], "#port", 15, sides=("east",))]}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n not in ("archive_shelf", "archive_shelf_lit", "archive_lectern", "archive_paper_bridge")}
    s["archive_shelf"] = K.axis_state("archive_shelf")
    s["archive_shelf_lit"] = K.axis_state("archive_shelf_lit")
    s["archive_lectern"] = K.lit_state("archive_lectern")
    s["archive_paper_bridge"] = {"variants": {"open=false": {"model": B + "archive_paper_bridge"},
                                              "open=true": {"model": B + "archive_paper_bridge_open"}}}
    return s


def content():
    K.content("gate_worlds_archive", BLOCKS, models(), states(), {}, climbable=("archive_kelp",), flat_items=FLAT)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_archive.png")
    else:
        print(__doc__)
