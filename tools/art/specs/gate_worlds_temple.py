"""Gate Worlds, the Overgrown Temple (SPEC §38.10): the theme's 13 world blocks and its set piece, the Little Idol.

    python3 tools/art/build_all.py gate_worlds_temple        textures
    python3 tools/art/specs/gate_worlds_temple.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_temple.py sheet PATH every texture at 6x, named

Green-grey cut stone, the same stone with moss climbing it, carved glyphs that glow, floor tiles, roots as thick as
pillars, vines with glowing flowers, moss, the stone brazier you feed, the four-faced idols, ferns, lantern bulbs, a
canopy of leaves and falling water. Drawn by code, nothing copied.
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "tstone": ["#1a211c", "#242d26", "#2f3a31", "#3b483d", "#48574a", "#576858"],
    "tmoss": ["#16290f", "#203a15", "#2b4e1c", "#386424", "#477c2e", "#5a963a"],
    "ttile": ["#252a22", "#30362c", "#3c4337", "#495143", "#575f50", "#666e5e"],
    "troot": ["#1e130b", "#2b1c10", "#3a2616", "#4a311c", "#5a3c22", "#6c4a2a"],
    "tleaf": ["#0e2010", "#153016", "#1d421e", "#275628", "#326c32", "#408440"],
    "twater": ["#2a6a8a", "#3a86a6", "#58a6c4", "#86c8e0", "#bce6f4"],
    "glyph": ["#2a7a6a", "#4ab8a0", "#8ae8cc", "#d0fff0"],
}
BULB = ("#ffb040", "#ffd870", "#fff4c0")
GLYPH = ("#4ab8a0", "#8ae8cc", "#d0fff0")


def _P():
    return K.px(RAMPS)


def tex_stone(P):
    return K.ashlar(P, "tstone", "stone", course=8)


def tex_mossy(P):
    c = tex_stone(P)
    mp = P.ramp("tmoss")
    rng = P.rng_for("moss-creep")
    n = P.value_noise(16, 16, rng, scale=3, octaves=2)
    for y in range(16):
        for x in range(16):
            want = n[y][x] + (1 - y / 16) * 0.45
            if want > 0.72:
                c.set(x, y, mp[min(5, 1 + int((want - 0.72) * 14))])
            elif want > 0.64 and (x + y) % 2 == 0:
                c.set(x, y, mp[1])
    return c


def tex_carving(P):
    c = tex_stone(P)
    sp = P.ramp("tstone")
    # a glyph cut into the face: an eye over a stepped mark, its grooves holding light
    glyph = ["................",
             "................",
             "....gggggggg....",
             "...g........g...",
             "..g...gggg...g..",
             "..g..g.GG.g..g..",
             "..g...gggg...g..",
             "...g........g...",
             "....gggggggg....",
             "................",
             ".......gg.......",
             ".....gggggg.....",
             "...gggggggggg...",
             "................",
             "................",
             "................"]
    s = K.sprite(glyph, {"g": GLYPH[0], "G": GLYPH[2]})
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            col = s.get(x, y)
            if col[3]:
                g.set(x, y, col)
                c.set(x, y, sp[0])
                c.blend(x, y, col, 0.5)
                c.set(x, y + 1, sp[5]) if not s.get(x, y + 1)[3] else None
    return c, g


def tex_tiles(P):
    c = K.tiles(P, "ttile", "tiles", size=8)
    tp = P.ramp("ttile")
    # a small diamond cut at each tile's heart
    for cx in (4, 12):
        for cy in (4, 12):
            for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)):
                c.set(cx + dx, cy + dy, tp[0])
            c.set(cx, cy, tp[5])
    return c


def tex_root(P):
    return K.bark(P, "troot", "root"), K.rings(P, "troot", "troot", "root")


def tex_vines(P):
    rows = ["..v.....v...v...",
            "..vl....v...vl..",
            ".lv.....vl..v...",
            "..v....lv...v...",
            "..vl....v..lv...",
            "..v.....v...v...",
            ".lv....Fv...vl..",
            "..v.....vl..v...",
            "..v....lv...F...",
            "..vl....v.......",
            "..F.....v.......",
            "........vl......",
            "........v.......",
            ".......lv.......",
            "........F.......",
            "................"]
    s = K.sprite(rows, {"v": "#2b4e1c", "l": "#477c2e", "F": BULB[1]})
    return s, K.only(s, {K.rgba(BULB[1])[:3]})


def tex_moss_carpet(P):
    c = P.Canvas(16, 16)
    mp = P.ramp("tmoss")
    rng = P.rng_for("moss-carpet")
    n = P.value_noise(16, 16, rng, scale=3, octaves=2)
    for y in range(16):
        for x in range(16):
            if n[y][x] > 0.42:
                c.set(x, y, mp[min(5, 1 + int((n[y][x] - 0.42) * 9))])
    return c


def tex_fern(P):
    rows = ["................",
            "................",
            "........f.......",
            ".......ff.......",
            "..f....f.f...f..",
            "...f..ff..f.f...",
            "...ff.f...ff....",
            "....ff.f.ff..f..",
            ".f...fff.f..ff..",
            "..ff..fff..ff...",
            "...ffffff.ff....",
            ".....fffff......",
            "......sss.......",
            ".......s........",
            ".......s........",
            "................"]
    return K.sprite(rows, {"f": "#386424", "s": "#203a15"})


def tex_bulb(P):
    rows = ["......ss........",
            ".......s........",
            ".......s........",
            ".......s........",
            "......ls........",
            ".......sl.......",
            ".......s........",
            "......lsl.......",
            ".....bBBBb......",
            "....bBWWBBb.....",
            "....BBWBBBB.....",
            "....bBBBBBb.....",
            ".....bBBBb......",
            "......bbb.......",
            ".......b........",
            "................"]
    s = K.sprite(rows, {"s": "#2b4e1c", "l": "#477c2e", "b": BULB[0], "B": BULB[1], "W": BULB[2]})
    return s, K.only(s, {K.rgba(BULB[0])[:3], K.rgba(BULB[1])[:3], K.rgba(BULB[2])[:3]})


def tex_canopy(P):
    c = K.noisy(P, "tleaf", "canopy", scale=4, octaves=3, jitter=0.4)
    lp = P.ramp("tleaf")
    rng = P.rng_for("canopy-leaves")
    for _ in range(14):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, lp[5])
        c.set((x + 1) % 16, (y + 1) % 16, lp[0])
    return c


def tex_waterfall(P):
    return K.streaks(P, "twater", "temple-fall", frames=8, alpha=160)


def tex_flame(P):
    rows = ["................",
            "................",
            "................",
            "......W.........",
            ".....WY.....W...",
            ".....YY....WY...",
            "....YOY.W..YO...",
            "...YOOYWY.YOY...",
            "...OOROYYYOO....",
            "..OORRROYOOO....",
            "..ORRRRROOROO...",
            "...RRRRRRRRR....",
            "....RRRRRRR.....",
            "................",
            "................",
            "................"]
    return K.sprite(rows, {"W": "#fff4d0", "Y": "#ffd060", "O": "#ff9a30", "R": "#e05a18"})


def tex_coals(P):
    c = P.Canvas(16, 16)
    rng = P.rng_for("temple-coals")
    for y in range(16):
        for x in range(16):
            c.set(x, y, P.hex_rgba(["#141010", "#1e1814", "#2a201a"][rng.randrange(3)]))
    return c


def tex_eyes(P):
    rows = ["................",
            "................",
            "................",
            "................",
            "................",
            "...WG......GW...",
            "...GG......GG...",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................"]
    return K.sprite(rows, {"W": GLYPH[2], "G": GLYPH[1]})


def tex_idol_face(P):
    c = K.noisy(P, "tstone", "idol", scale=3, lo=2, span=3, jitter=0.05)
    sp = P.ramp("tstone")
    for x in range(2, 14):
        c.set(x, 3, sp[0])
        c.set(x, 4, sp[5])
    for x, y in ((3, 5), (4, 5), (11, 5), (12, 5), (3, 6), (4, 6), (11, 6), (12, 6)):
        c.set(x, y, sp[0])
    for y in range(7, 10):
        c.set(7, y, sp[1])
        c.set(8, y, sp[4])
    for x in range(5, 11):
        c.set(x, 11, sp[0])
    c.set(4, 12, sp[0])
    c.set(11, 12, sp[0])
    return c


def textures():
    P = _P()
    t = {}
    t["temple_stone"] = tex_stone(P)
    t["temple_stone_mossy"] = tex_mossy(P)
    t["temple_carving"], t["temple_carving_glow"] = tex_carving(P)
    t["temple_tiles"] = tex_tiles(P)
    t["temple_root"], t["temple_root_top"] = tex_root(P)
    t["temple_vines"], t["temple_vines_glow"] = tex_vines(P)
    t["temple_moss"] = tex_moss_carpet(P)
    t["temple_fern"] = tex_fern(P)
    t["temple_lantern_bulb"], t["temple_lantern_bulb_glow"] = tex_bulb(P)
    t["temple_canopy"] = tex_canopy(P)
    t["temple_waterfall"] = tex_waterfall(P)
    t["temple_brazier_flame"] = tex_flame(P)
    t["temple_brazier_coals"] = tex_coals(P)
    t["temple_idol_face"] = tex_idol_face(P)
    t["temple_idol_eyes"] = tex_eyes(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


BLOCKS = {
    "temple_stone": ("Temple Stone", "pickaxe"),
    "temple_stone_mossy": ("Mossy Temple Stone", "pickaxe"),
    "temple_carving": ("Glowing Temple Carving", "pickaxe"),
    "temple_tiles": ("Temple Tiles", "pickaxe"),
    "temple_root": ("Temple Root", "axe"),
    "temple_vines": ("Flowering Temple Vines", None),
    "temple_moss": ("Temple Moss", "hoe"),
    "temple_brazier": ("Temple Brazier", "pickaxe"),
    "temple_idol": ("Four-Faced Idol", "pickaxe"),
    "temple_fern": ("Temple Fern", None),
    "temple_lantern_bulb": ("Lantern Bulb", None),
    "temple_canopy": ("Temple Canopy", "hoe"),
    "temple_waterfall": ("Temple Falls", None),
    "little_idol": ("The Little Idol", "pickaxe"),
}
FLAT = ("temple_vines", "temple_fern", "temple_lantern_bulb")
B = K.B
SIDES4 = ("north", "south", "west", "east")


def _idol(scale, crown):
    """A squat four-faced idol on a plinth, a face to every side, eyes glowing; scale 1 fills its 10x15 outline."""
    def bx(a, b, tex, em=0, sides=K.SIDES, uv=None):
        lo = [8 + (a[0] - 8) * scale, a[1] * scale, 8 + (a[2] - 8) * scale]
        hi = [8 + (b[0] - 8) * scale, b[1] * scale, 8 + (b[2] - 8) * scale]
        return K.box(lo, hi, tex, em, sides=sides, uv=uv)
    els = [bx([3, 0, 3], [13, 3, 13], "#stone"), bx([4, 3, 4], [12, 9, 12], "#stone"),
           bx([4.5, 9, 4.5], [11.5, 15, 11.5], "#face", sides=SIDES4, uv=[0, 0, 16, 16]), bx([4.5, 9, 4.5], [11.5, 15, 11.5], "#stone", sides=("up", "down")),
           bx([4.48, 9, 4.48], [11.52, 15, 11.52], "#eyes", 15, sides=SIDES4, uv=[0, 0, 16, 16])]
    if crown:
        els += [bx([4, 15, 4], [12, 16, 12], "#leaf"), bx([6, 15.5, 3.5], [10, 16, 12.5], "#leaf")]
    return els


def models():
    m = {}
    m["temple_stone"] = K.cube_model("temple_stone")
    m["temple_stone_mossy"] = K.cube_model("temple_stone_mossy")
    m["temple_carving"] = K.cube_model("temple_carving", glow=True)
    m["temple_tiles"] = K.cube_model("temple_tiles")
    m["temple_root"] = K.column_model("temple_root", "temple_root_top")
    m["temple_vines"] = K.cross_model("temple_vines", glow=True)
    m["temple_moss"] = K.carpet_model("temple_moss")
    m["temple_fern"] = K.cross_model("temple_fern")
    m["temple_lantern_bulb"] = K.cross_model("temple_lantern_bulb", glow=True)
    m["temple_canopy"] = K.cube_model("temple_canopy")
    m["temple_waterfall"] = K.fall_model("temple_waterfall", 6)
    # the brazier: a stone bowl on a foot, cold coals in it until it's fed, then fire
    for lit in (False, True):
        tex = {"particle": B + "temple_stone", "stone": B + "temple_stone", "coals": B + "temple_brazier_coals"}
        els = [K.box([5, 0, 5], [11, 2, 11], "#stone"), K.box([6, 2, 6], [10, 5, 10], "#stone"),
               K.box([2, 5, 2], [14, 8, 14], "#stone"),
               K.box([2, 8, 2], [14, 10, 3], "#stone"), K.box([2, 8, 13], [14, 10, 15], "#stone"),
               K.box([2, 8, 3], [3, 10, 13], "#stone"), K.box([13, 8, 3], [14, 10, 13], "#stone"),
               K.box([3, 8, 3], [13, 8.5, 13], "#coals", sides=("up",))]
        if lit:
            tex["flame"] = B + "temple_brazier_flame"
            els += K.cross("#flame", 15, 3, 8, 16)
        m["temple_brazier" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                                                        "textures": tex, "elements": els}
    face = {"particle": B + "temple_stone", "stone": B + "temple_stone_mossy", "face": B + "temple_idol_face",
            "eyes": B + "temple_idol_eyes", "leaf": B + "temple_canopy"}
    m["temple_idol"] = {"parent": "minecraft:block/block", "textures": face, "elements": _idol(1.0, False)}
    m["little_idol"] = {"parent": "minecraft:block/block", "textures": dict(face, stone=B + "temple_stone"), "elements": _idol(0.9, True)}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n not in ("temple_root", "temple_brazier")}
    s["temple_root"] = K.axis_state("temple_root")
    s["temple_brazier"] = K.lit_state("temple_brazier")
    return s


def content():
    K.content("gate_worlds_temple", BLOCKS, models(), states(), {}, climbable=("temple_vines",), flat_items=FLAT)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_temple.png")
    else:
        print(__doc__)
