"""Gate Worlds, the Hive (SPEC §38.10): the theme's 12 world blocks and its set piece, the Queen's Comb.

    python3 tools/art/build_all.py gate_worlds_hive        textures
    python3 tools/art/specs/gate_worlds_hive.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_hive.py sheet PATH every texture at 6x, named

Golden wax and dark wax, comb whose cells glow with honey, capped comb, honey you can see through, a packed wax floor,
pollen, honey dripping, the wax drum you strike, royal cells, petals and beeswax candles. Drawn by code, nothing copied.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "hwax": ["#6a4a10", "#86600f", "#a27816", "#bc8f20", "#d4a82e", "#e8c04a"],
    "hdark": ["#1e1206", "#2a1a09", "#38220c", "#462c10", "#563614", "#66421a"],
    "honey": ["#b05a06", "#d27a10", "#f09a1e", "#ffbe40", "#ffdc7a", "#fff2c0"],
    "hcap": ["#9a7a3a", "#b49448", "#c8aa5a", "#dcc06e", "#ecd486", "#f8e6a4"],
    "royal": ["#5a1e3a", "#7a2a50", "#9a3a68", "#b85282", "#d474a0", "#ec9cc0"],
    "hfloor": ["#2e1e0c", "#3a2710", "#473014", "#553a19", "#64451f", "#735026"],
    "pollen": ["#8a6408", "#a87c0e", "#c49416", "#dcae22", "#eec638", "#fade66"],
}
HONEY = ("#ffbe40", "#ffdc7a", "#fff2c0")


def _P():
    return K.px(RAMPS)


def tex_wax(P):
    c = K.noisy(P, "hwax", "wax", scale=2, octaves=3, lo=1, span=4, jitter=0.06)
    rp = P.ramp("hwax")
    # a faint ghost of the comb pressed into the wax
    faint = K.hexes(P, "hwax", "hwax", "wax-ghost")
    for y in range(16):
        for x in range(16):
            if faint.get(x, y)[:3] in (rp[1][:3], rp[2][:3]):
                c.blend(x, y, rp[1], 0.25)
    return c


def tex_dark(P):
    return K.noisy(P, "hdark", "dark", scale=3, octaves=3, lo=1, span=4, jitter=0.15)


def tex_comb(P):
    hp = P.ramp("honey")
    g = P.Canvas(16, 16)

    def fill(key, k, x, y, edge):
        if k < 0.25:
            return None
        col = hp[min(5, 2 + int((1 - edge) * 3.5))]
        g.set(x, y, col if edge < 0.45 else hp[1])
        return col

    c = K.hexes(P, "hdark", "hwax", "comb", fill=fill)
    return c, g


def tex_capped(P):
    cp = P.ramp("hcap")

    def fill(key, k, x, y, edge):
        return cp[min(5, 1 + int((1 - edge) * 4.5) + (1 if k > 0.7 else 0))]

    return K.hexes(P, "hdark", "hcap", "capped", fill=fill)


def tex_honey(P):
    hp = P.ramp("honey")
    rng = P.rng_for("honey")
    n = P.value_noise(16, 16, rng, scale=2, octaves=2)
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = n[y][x]
            col = hp[min(5, 1 + int(v * 3.5))]
            c.set(x, y, (*col[:3], 190))
            if v > 0.62:
                g.set(x, y, (*hp[4][:3], 200))
    for _ in range(5):
        x, y = rng.randrange(1, 15), rng.randrange(1, 15)
        c.set(x, y, (*hp[5][:3], 220))
        g.set(x, y, hp[5])
    return c, g


def tex_floor(P):
    c = K.noisy(P, "hfloor", "floor", scale=3, octaves=3, lo=1, span=4, jitter=0.2)
    rp = P.ramp("hfloor")
    for pts in K.walk(P, "drag", 2, 9, turn=0.15):
        for x, y in pts:
            c.blend(x, y, rp[0], 0.5)
    return c


def tex_pollen(P):
    c = K.noisy(P, "pollen", "pollen", scale=5, octaves=3, lo=1, jitter=0.45)
    rng = P.rng_for("pollen-grains")
    for _ in range(12):
        c.set(rng.randrange(16), rng.randrange(16), P.hex_rgba("#fff0a0"))
    return c


def tex_drip(P):
    rows = ["..hHh....hhHh...",
            "...h......hH....",
            "...h......h.....",
            "...h......h.....",
            "...H......h.....",
            "..hGh.....h.....",
            "..GWG.....H.....",
            "..hGh....hGh....",
            "...h.....GWG....",
            ".........hGh....",
            "..........h.....",
            "................",
            "...........G....",
            "..........GWG...",
            "...........G....",
            "................"]
    s = K.sprite(rows, {"h": "#d27a10", "H": "#f09a1e", "G": HONEY[1], "W": HONEY[2]})
    return s, K.only(s, {K.rgba(HONEY[1])[:3], K.rgba(HONEY[2])[:3]})


def tex_royal(P):
    rp = P.ramp("royal")
    g = P.Canvas(16, 16)

    def fill(key, k, x, y, edge):
        col = rp[min(5, 1 + int((1 - edge) * 4.5))]
        if edge < 0.4:
            g.set(x, y, rp[5])
        return col

    return K.hexes(P, "hdark", "royal", "royal", fill=fill, size=5.3), g


def tex_petals(P):
    c = P.Canvas(16, 16)
    rng = P.rng_for("petals")
    cols = ["#ec9cc0", "#f4c0d8", "#fff4f8", "#f8dc60"]
    for _ in range(13):
        x, y = rng.randrange(16), rng.randrange(16)
        col = P.hex_rgba(cols[rng.randrange(4)])
        for dx, dy in ((0, 0), (1, 0), (0, 1)):
            c.set((x + dx) % 16, (y + dy) % 16, col)
    return c


def tex_candle(P):
    wax = K.noisy(P, "hcap", "candle", scale=2, lo=2, span=3)
    rows = ["................",
            "................",
            "................",
            "................",
            "................",
            "......W.........",
            ".....WYW........",
            ".....YOY........",
            "......O.........",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................"]
    flame = K.sprite(rows, {"W": "#fff6d0", "Y": "#ffd060", "O": "#ff9a30"})
    return wax, flame


def tex_drum(P):
    rp = P.ramp("hdark")
    side = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = 2 + (1 if (x + y) % 6 == 0 or (x - y) % 6 == 0 else 0) - (1 if y in (0, 15) else 0)
            side.set(x, y, rp[v] if 2 <= y <= 13 else rp[4])
    top = K.noisy(P, "hcap", "drum-skin", scale=2, lo=2, span=3, jitter=0.05)
    ring = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3.5 < d < 5 or d < 1.6:
                ring.set(x, y, K.rgba(HONEY[1]))
    return side, top, ring


def tex_frame(P):
    return K.boards(P, "hdark", "frame", h=4)


def tex_comb_empty(P):
    def fill(key, k, x, y, edge):
        return P.hex_rgba("#2a1a09") if edge < 0.5 else P.hex_rgba("#462c10")

    return K.hexes(P, "hwax", "hdark", "comb", fill=fill)


def textures():
    P = _P()
    t = {}
    t["hive_wax"] = tex_wax(P)
    t["hive_wax_dark"] = tex_dark(P)
    t["hive_comb"], t["hive_comb_glow"] = tex_comb(P)
    t["hive_comb_capped"] = tex_capped(P)
    t["hive_honey_glow"], t["hive_honey_glow_glow"] = tex_honey(P)
    t["hive_floor"] = tex_floor(P)
    t["hive_pollen"] = tex_pollen(P)
    t["hive_drip"], t["hive_drip_glow"] = tex_drip(P)
    t["hive_royal"], t["hive_royal_glow"] = tex_royal(P)
    t["hive_petals"] = tex_petals(P)
    t["hive_candle"], t["hive_candle_flame"] = tex_candle(P)
    t["hive_drum"], t["hive_drum_top"], t["hive_drum_glow"] = tex_drum(P)
    t["queens_comb_frame"] = tex_frame(P)
    t["queens_comb_empty"] = tex_comb_empty(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


BLOCKS = {
    "hive_wax": ("Hive Wax", "axe"),
    "hive_wax_dark": ("Dark Hive Wax", "axe"),
    "hive_comb": ("Honey Comb", "axe"),
    "hive_comb_capped": ("Capped Comb", "axe"),
    "hive_honey_glow": ("Glowing Honey", None),
    "hive_floor": ("Packed Wax Floor", "shovel"),
    "hive_pollen": ("Pollen", "shovel"),
    "hive_drip": ("Honey Drip", None),
    "hive_drum": ("Wax Drum", "axe"),
    "hive_royal": ("Royal Comb", "axe"),
    "hive_petals": ("Hive Petals", None),
    "hive_candle": ("Beeswax Candle", None),
    "queens_comb": ("The Queen's Comb", "axe"),
}
FLAT = ("hive_drip",)
B = K.B


def models():
    m = {}
    m["hive_wax"] = K.cube_model("hive_wax")
    m["hive_wax_dark"] = K.cube_model("hive_wax_dark")
    m["hive_comb"] = K.cube_model("hive_comb", glow=True)
    m["hive_comb_capped"] = K.cube_model("hive_comb_capped")
    m["hive_honey_glow"] = K.cube_model("hive_honey_glow", glow=True, translucent=True)
    m["hive_floor"] = K.cube_model("hive_floor")
    m["hive_pollen"] = K.cube_model("hive_pollen")
    m["hive_drip"] = K.cross_model("hive_drip", glow=True)
    m["hive_royal"] = K.cube_model("hive_royal", glow=True)
    m["hive_petals"] = K.carpet_model("hive_petals")
    # a beeswax candle: a stub of wax with its flame (crossed planes) lit for good
    m["hive_candle"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                        "textures": {"particle": B + "hive_candle", "wax": B + "hive_candle", "flame": B + "hive_candle_flame"},
                        "elements": [K.box([6.5, 0, 6.5], [9.5, 7, 9.5], "#wax", uv=[0, 9, 3, 16]),
                                     *K.cross("#flame", 15, 6, 7, 11, [4, 5, 8, 9])]}
    # the wax drum: a squat drum laced with sinew, its skin rings with honey light when struck
    for lit in (False, True):
        tex = {"particle": B + "hive_drum", "side": B + "hive_drum", "top": B + "hive_drum_top"}
        els = [K.box([2, 0, 2], [14, 10, 14], "#side", sides=("down", "north", "south", "west", "east")),
               K.box([2, 10, 2], [14, 10, 14], "#top", sides=("up",)),
               K.box([1.5, 9, 1.5], [14.5, 11, 14.5], "#side", sides=("north", "south", "west", "east")),
               K.box([1.5, 0, 1.5], [14.5, 1.5, 14.5], "#side", sides=("north", "south", "west", "east"))]
        if lit:
            tex["glow"] = B + "hive_drum_glow"
            els.append(K.box([2, 10.02, 2], [14, 10.02, 14], "#glow", 15, sides=("up",)))
        m["hive_drum" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    # the Queen's Comb: a slab of comb in a dark wooden frame; full, its cells glow with honey
    for full in (True, False):
        comb = "hive_comb" if full else "queens_comb_empty"
        tex = {"particle": B + "queens_comb_frame", "frame": B + "queens_comb_frame", "comb": B + comb}
        els = [K.box([1, 0, 5], [15, 2, 11], "#frame"), K.box([1, 14, 5], [15, 16, 11], "#frame"),
               K.box([1, 2, 5], [3, 14, 11], "#frame"), K.box([13, 2, 5], [15, 14, 11], "#frame"),
               K.box([3, 2, 6], [13, 14, 10], "#comb", uv=[3, 2, 13, 14]),
               K.box([0, 0, 3], [3, 1, 13], "#frame"), K.box([13, 0, 3], [16, 1, 13], "#frame")]
        if full:
            tex["glow"] = B + "hive_comb_glow"
            els += [K.box([2.98, 2, 5.98], [13.02, 14, 10.02], "#glow", 15, sides=("north", "south"), uv=[3, 2, 13, 14])]
        m["queens_comb" + ("" if full else "_empty")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n not in ("hive_drum", "queens_comb")}
    s["hive_drum"] = K.lit_state("hive_drum")
    s["queens_comb"] = {"variants": {"full=true": {"model": B + "queens_comb"}, "full=false": {"model": B + "queens_comb_empty"}}}
    return s


def content():
    K.content("gate_worlds_hive", BLOCKS, models(), states(), {})


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_hive.png")
    else:
        print(__doc__)
