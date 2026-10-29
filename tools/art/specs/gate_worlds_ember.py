"""Gate Worlds, Ember Mines (SPEC §38.10): the theme's 11 world blocks and its set piece, the Ever-Ember Forge.

    python3 tools/art/build_all.py gate_worlds_ember        textures
    python3 tools/art/specs/gate_worlds_ember.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_ember.py sheet PATH every texture at 6x, named

Black basalt in columns, basalt split by cracks that glow, slag with its metal glints, soft ash, mine timber and planks,
the miners' lamps, crust plates on magma with bright seams, ember crystals, a curtain of falling molten stone and the
leather bellows you work. Drawn by code, nothing copied.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "basalt": ["#0f0d10", "#18151a", "#221e24", "#2d282f", "#39333b", "#463f48"],
    "eglow": ["#a8300a", "#e85a12", "#ff9a30", "#ffd27a", "#fff2c8"],
    "slag": ["#1f1a19", "#2c2523", "#3a312e", "#4a3f3a", "#5c4f48", "#6e6058"],
    "ash": ["#3e3a39", "#4c4847", "#5a5655", "#6a6564", "#7c7776", "#8f8a88"],
    "timber": ["#1a1009", "#26180d", "#342112", "#432b17", "#53361d", "#644224"],
    "crust": ["#120807", "#1c0d0a", "#28130d", "#341a11", "#422216"],
    "leather": ["#2a140c", "#3a1c10", "#4c2615", "#5e301b", "#723c22"],
    "iron": ["#1a1a1e", "#2a2a30", "#3c3c44", "#52525c", "#6c6c78"],
}
EMBER = ("#ff9a30", "#ffd27a", "#fff2c8")


def _P():
    return K.px(RAMPS)


def tex_basalt(P):
    rp = P.ramp("basalt")
    rng = P.rng_for("basalt")
    n = P.value_noise(16, 16, rng, scale=2, octaves=3)
    c = P.Canvas(16, 16)
    seams = (0, 5, 11)
    for y in range(16):
        for x in range(16):
            v = 1 + int(n[y][x] * 4) + (1 if (y + x * 3) % 13 == 0 else 0)
            if x in seams:
                v = 0
            elif x - 1 in seams:
                v = min(5, v + 1)
            c.set(x, y, rp[max(0, min(5, v))])
    return c


def tex_cracked(P):
    c = tex_basalt(P)
    cracks = K.walk(P, "ember-cracks", 3, 10, turn=0.7)
    g = K.glow_of(cracks, [K.rgba(EMBER[0]), K.rgba(EMBER[1])], halo=K.rgba("#a8300a", 170))
    return K.under(c, g, 0.7), g


def tex_slag(P):
    c = K.noisy(P, "slag", "slag", scale=4, octaves=3, jitter=0.35)
    rng = P.rng_for("slag-glints")
    for _ in range(6):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, P.hex_rgba("#9a8a7a"))
        c.set((x + 1) % 16, y, P.hex_rgba("#6e6058"))
    for _ in range(4):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, P.hex_rgba("#7a2a12"))
    return c


def tex_ash(P):
    c = K.noisy(P, "ash", "ash", scale=5, octaves=3, lo=1, jitter=0.4)
    rng = P.rng_for("ash-cinders")
    for _ in range(4):
        c.set(rng.randrange(16), rng.randrange(16), P.hex_rgba("#2a1a14"))
    return c


def tex_crust(P):
    rp = P.ramp("crust")
    rng = P.rng_for("crust")
    pts = [(rng.random() * 16, rng.random() * 16) for _ in range(5)]
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            ds = sorted(min(math.hypot(x - px + ox, y - py + oy) for ox in (-16, 0, 16) for oy in (-16, 0, 16)) for px, py in pts)
            edge = ds[1] - ds[0]
            if edge < 0.55:
                g.set(x, y, K.rgba(EMBER[1]) if edge < 0.25 else K.rgba(EMBER[0]))
                c.set(x, y, P.hex_rgba("#ff7a20"))
            elif edge < 1.0:
                c.set(x, y, P.hex_rgba("#5a1a0c"))
            else:
                c.set(x, y, rp[min(4, 1 + int(ds[0] / 2.5) + (x * y) % 2)])
    return c, g


def tex_crystal(P):
    rows = ["................",
            "..........W.....",
            "..........Y.....",
            ".........YOY....",
            "...W.....YOO....",
            "...Y.....YOO....",
            "..YOY....OOR....",
            "..YOO...YOOR..W.",
            "..OOR...OORR..Y.",
            "..OOR...OOR..YOY",
            "..ORR..YOOR..OOR",
            "..ORR..OORR..ORR",
            ".bORRb.ORRR.bORb",
            ".bbRbbbbRRbbbbbb",
            "................",
            "................"]
    s = K.sprite(rows, {"W": EMBER[2], "Y": EMBER[1], "O": EMBER[0], "R": "#c8400e", "b": "#2d282f"})
    return s, K.only(s, {K.rgba(EMBER[2])[:3], K.rgba(EMBER[1])[:3], K.rgba(EMBER[0])[:3]})


def tex_moltenfall(P):
    return K.streaks(P, "eglow", "molten", frames=8, alpha=235)


def tex_timber(P):
    return K.bark(P, "timber", "timber"), K.rings(P, "timber", "timber", "timber")


def tex_planks(P):
    return K.boards(P, "timber", "planks", h=4)


def tex_lamp(P):
    ip = P.ramp("iron")
    cage = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if x in (0, 5, 10, 15) or y in (0, 15):
                cage.set(x, y, ip[3] if (x + y) % 3 else ip[4])
    glow = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 8.5)
            glow.set(x, y, K.rgba(EMBER[2]) if d < 3 else K.rgba(EMBER[1]) if d < 6 else K.rgba(EMBER[0]))
    return cage, glow


def tex_bellows(P):
    lp = P.ramp("leather")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            fold = (y // 3) % 2
            c.set(x, y, lp[2 + fold - (1 if y % 3 == 0 else 0)])
    nozzle = K.noisy(P, "iron", "nozzle", scale=2, lo=1, span=3)
    tip = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            tip.set(x, y, K.rgba(EMBER[1] if (x + y) % 3 else EMBER[2]))
    return c, nozzle, tip


def tex_coals(P):
    rp = P.ramp("crust")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    rng = P.rng_for("coals")
    for y in range(16):
        for x in range(16):
            c.set(x, y, rp[rng.randrange(4)])
    for _ in range(40):
        x, y = rng.randrange(16), rng.randrange(16)
        col = K.rgba(EMBER[rng.randrange(3)])
        c.set(x, y, col)
        g.set(x, y, col)
    return c, g


def textures():
    P = _P()
    t = {}
    t["ember_basalt"] = tex_basalt(P)
    t["ember_basalt_cracked"], t["ember_basalt_cracked_glow"] = tex_cracked(P)
    t["ember_slag"] = tex_slag(P)
    t["ember_ash"] = tex_ash(P)
    t["mine_timber"], t["mine_timber_top"] = tex_timber(P)
    t["mine_planks"] = tex_planks(P)
    t["mine_lamp"], t["mine_lamp_glow"] = tex_lamp(P)
    t["magma_crust"], t["magma_crust_glow"] = tex_crust(P)
    t["ember_crystal"], t["ember_crystal_glow"] = tex_crystal(P)
    t["ember_moltenfall"] = tex_moltenfall(P)
    t["ember_bellows"], t["ember_bellows_iron"], t["ember_bellows_glow"] = tex_bellows(P)
    t["ember_forge_coals"], t["ember_forge_coals_glow"] = tex_coals(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


BLOCKS = {
    "ember_basalt": ("Mine Basalt", "pickaxe"),
    "ember_basalt_cracked": ("Glowing Cracked Basalt", "pickaxe"),
    "ember_slag": ("Slag", "pickaxe"),
    "ember_ash": ("Ash", "shovel"),
    "mine_timber": ("Mine Timber", "axe"),
    "mine_planks": ("Mine Planks", "axe"),
    "mine_lamp": ("Miner's Lamp", "pickaxe"),
    "magma_crust": ("Magma Crust", "pickaxe"),
    "ember_crystal": ("Ember Crystal", None),
    "ember_moltenfall": ("Falling Molten Stone", None),
    "ember_bellows": ("Mine Bellows", "axe"),
    "ember_forge": ("The Ever-Ember Forge", "pickaxe"),
}
FLAT = ("ember_crystal",)
B = K.B


def models():
    m = {}
    m["ember_basalt"] = K.cube_model("ember_basalt")
    m["ember_basalt_cracked"] = K.cube_model("ember_basalt_cracked", glow=True)
    m["ember_slag"] = K.cube_model("ember_slag")
    m["ember_ash"] = K.cube_model("ember_ash")
    m["mine_timber"] = K.column_model("mine_timber", "mine_timber_top")
    m["mine_planks"] = K.cube_model("mine_planks")
    m["magma_crust"] = K.cube_model("magma_crust", glow=True)
    m["ember_crystal"] = K.cross_model("ember_crystal", glow=True)
    m["ember_moltenfall"] = K.fall_model("ember_moltenfall", 15)
    # the miner's lamp: an iron cage round a glowing heart, a cap and a ring to hang it by
    m["mine_lamp"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                      "textures": {"particle": B + "mine_lamp", "cage": B + "mine_lamp", "glow": B + "mine_lamp_glow",
                                   "iron": B + "ember_bellows_iron"},
                      "elements": [K.box([6, 1, 6], [10, 8, 10], "#glow", 15),
                                   K.box([5, 0, 5], [11, 9, 11], "#cage", sides=("north", "south", "west", "east")),
                                   K.box([5, 0, 5], [11, 1, 11], "#iron"), K.box([5.5, 9, 5.5], [10.5, 10, 10.5], "#iron"),
                                   K.box([7.5, 10, 7.5], [8.5, 11, 8.5], "#iron")]}
    # the bellows: two boards with folded leather between, an iron nozzle that glows when you work them
    for lit in (False, True):
        tex = {"particle": B + "mine_planks", "wood": B + "mine_planks", "leather": B + "ember_bellows",
               "iron": B + "ember_bellows_iron", "tip": B + ("ember_bellows_glow" if lit else "ember_bellows_iron")}
        els = [K.box([1, 0, 3], [13, 2, 13], "#wood"), K.box([2, 2, 4], [12, 7, 12], "#leather"),
               K.box([1, 7, 3], [13, 8.5, 13], "#wood"), K.box([3, 8.5, 7], [6, 10, 9], "#wood"),
               K.box([13, 2.5, 6.5], [15, 5.5, 9.5], "#iron"),
               K.box([14.9, 3, 7], [15.02, 5, 9], "#tip", 15 if lit else 0)]
        m["ember_bellows" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    # the Ever-Ember Forge: a basalt hearth with its coals always lit and a little iron hood
    m["ember_forge"] = {"parent": "minecraft:block/block",
                        "textures": {"particle": B + "ember_basalt", "stone": B + "ember_basalt", "coals": B + "ember_forge_coals",
                                     "glow": B + "ember_forge_coals_glow", "iron": B + "ember_bellows_iron"},
                        "elements": [K.box([1, 0, 1], [15, 8, 15], "#stone"),
                                     K.box([1, 8, 1], [15, 11, 3], "#stone"), K.box([1, 8, 13], [15, 11, 15], "#stone"),
                                     K.box([1, 8, 3], [3, 11, 13], "#stone"), K.box([13, 8, 3], [15, 11, 13], "#stone"),
                                     K.box([3, 8, 3], [13, 9, 13], "#coals", sides=("up",)),
                                     K.box([3, 9.02, 3], [13, 9.02, 13], "#glow", 15, sides=("up",)),
                                     K.box([5, 11, 12], [11, 13, 15], "#iron")]}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n not in ("mine_timber", "ember_bellows")}
    s["mine_timber"] = K.axis_state("mine_timber")
    s["ember_bellows"] = K.lit_state("ember_bellows")
    return s


def content():
    K.content("gate_worlds_ember", BLOCKS, models(), states(), {}, flat_items=FLAT)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_ember.png")
    else:
        print(__doc__)
