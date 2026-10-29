"""Gate Worlds, Mirror Palace (SPEC §38.12): the theme's 14 world blocks and its set piece, the Hand Mirror.

    python3 tools/art/build_all.py gate_worlds_palace        textures
    python3 tools/art/specs/gate_worlds_palace.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_palace.py sheet PATH every texture at 6x, named

White marble with grey veins and marble whose veins glow gold, a black and white checkered floor, gilt mouldings, tall
mirrors (whole and cracked), a glass floor, the White's own white, shadows lying on the floor, purple velvet curtains,
a crystal chandelier, a gilt candelabra, crystals, and the standing mirror you look into. Drawn by code, nothing copied.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "marble": ["#a8a4a0", "#bcb8b2", "#ceccc6", "#dedcd6", "#ecebe6", "#f8f7f4"],
    "vein": ["#5a5a62", "#74747c", "#8e8e96"],
    "black": ["#0c0c10", "#16161c", "#202028", "#2a2a34"],
    "gilt": ["#4a300c", "#7a5214", "#a8781e", "#d4a030", "#f0c850", "#fff0a0"],
    "silver": ["#4a5260", "#5e6878", "#76808e", "#909aa8", "#aab4c0", "#c8d0da", "#e4eaf0"],
    "velvet": ["#1a0a24", "#2a1036", "#3c184c", "#502262", "#662e7a"],
    "white": ["#e8e8ee", "#f0f0f4", "#f8f8fa", "#ffffff"],
}
CANDLE = ("#ff9a30", "#ffd060", "#fff4c0")


def _P():
    return K.px(RAMPS)


def veins(P, seed, c, col, n=3, length=14):
    for path in K.walk(P, seed, n, length, turn=0.25):
        for x, y in path:
            c.set(x % 16, y % 16, col)


def tex_marble(P):
    c = K.noisy(P, "marble", "palace-marble", scale=5, lo=2, span=4, jitter=0.12)
    veins(P, "palace-marble-veins", c, P.ramp("vein")[2])
    return c


def tex_veined(P):
    c = K.noisy(P, "marble", "palace-veined", scale=5, lo=2, span=4, jitter=0.12)
    g = P.Canvas(16, 16)
    for path in K.walk(P, "palace-gold-veins", 3, 15, turn=0.3):
        for x, y in path:
            c.set(x % 16, y % 16, P.ramp("gilt")[3])
            g.set(x % 16, y % 16, P.ramp("gilt")[5])
    return c, g


def tex_checker(P):
    mp, bp = P.ramp("marble"), P.ramp("black")
    rng = P.rng_for("palace-checker")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            dark = (x // 8 + y // 8) % 2
            v = rng.random()
            c.set(x, y, bp[1 + int(v * 2)] if dark else mp[3 + int(v * 2)])
            if x % 8 == 0 or y % 8 == 0:
                c.set(x, y, P.ramp("gilt")[1] if dark else mp[1])
    veins(P, "palace-checker-veins", c, mp[1], n=2, length=8)
    return c


def tex_gilt(P):
    """Gilt moulding: an egg-and-dart run between two beads."""
    gp = P.ramp("gilt")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if y in (0, 15):
                c.set(x, y, gp[1])
            elif y in (1, 14):
                c.set(x, y, gp[4] if x % 2 else gp[3])
            elif y in (2, 13):
                c.set(x, y, gp[2])
            else:
                u = x % 8
                egg = math.hypot((u - 3.5) / 2.6, (y - 7.5) / 4.6) < 1
                dart = u == 7 and 4 < y < 11
                c.set(x, y, gp[4 if egg and y < 8 else 3] if egg else gp[0] if dart else gp[2])
    return c


def tex_mirror(P, cracked):
    sp = P.ramp("silver")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            # a cool sheen running across it, brighter toward the top
            k = 2 + int(2.5 * (0.5 + 0.5 * math.sin((x - y) * 0.35))) + (1 if y < 6 else 0)
            c.set(x, y, sp[min(6, k)])
            if x in (0, 15) or y in (0, 15):
                c.set(x, y, P.ramp("gilt")[3])
            elif x in (1, 14) or y in (1, 14):
                c.set(x, y, sp[6])
    if cracked:
        rng = P.rng_for("palace-crack")
        cx, cy = 6 + rng.randrange(4), 6 + rng.randrange(4)
        for i in range(7):
            a = i / 7 * math.pi * 2 + rng.random() * 0.5
            for t in range(1, 9):
                x, y = int(round(cx + math.cos(a) * t)), int(round(cy + math.sin(a) * t))
                if 1 < x < 14 and 1 < y < 14:
                    c.set(x, y, sp[0] if t % 3 else sp[6])
    return c


def tex_glass_floor(P):
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            c.set(x, y, (*K.rgba("#d0dcec")[:3], 200 if edge else 40))
    for i in range(3, 9):
        c.set(i, 12 - i, (*K.rgba("#ffffff")[:3], 150))
    return c


def tex_white(P):
    wp = P.ramp("white")
    rng = P.rng_for("palace-white")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, wp[2 + (1 if rng.random() < 0.7 else 0)])
    for x in range(16):
        c.set(x, 0, wp[1])
        c.set(0, x, wp[1])
    return c


def tex_shadow(P):
    """A shadow lying on the floor: soft-edged and a little too dark for the light."""
    c = P.Canvas(16, 16)
    rng = P.rng_for("palace-shadow")
    n = P.value_noise(16, 16, rng, scale=6, octaves=2)
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 7, (y - 7.5) / 6) + (n[y][x] - 0.5) * 0.5
            if d < 1:
                c.set(x, y, (12, 10, 20, int(150 * (1 - d * d))))
    return c


def tex_curtain(P):
    vp = P.ramp("velvet")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            fold = 0.5 + 0.5 * math.sin((x + 0.5) / 16 * math.pi * 4)
            c.set(x, y, vp[1 + int(fold * 3.4)])
    for x in range(16):
        c.set(x, 13, P.ramp("gilt")[3])
        c.set(x, 14, P.ramp("gilt")[2] if x % 3 else P.ramp("gilt")[4])
    return c


def tex_chandelier(P):
    rows = ["................",
            ".......GG.......",
            ".......GG.......",
            "...F...GG...F...",
            "...C..GGGG..C...",
            "...C.GG..GG.C...",
            "..GGGG....GGGG..",
            "..G..........G..",
            "..W.W......W.W..",
            "..W.W.GGGG.W.W..",
            "....W.GWWG.W....",
            "......GWWG......",
            ".......WW.......",
            ".......W........",
            "................",
            "................"]
    c = K.sprite(rows, {"G": "#d4a030", "C": "#f4eedc", "F": CANDLE[1], "W": "#e4f0ff"})
    return c, K.only(c, {K.rgba(CANDLE[1])[:3], K.rgba("#e4f0ff")[:3]})


def tex_candelabra(P):
    gp = P.ramp("gilt")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, gp[2 + (x + y) % 3])
    wax = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            wax.set(x, y, K.rgba("#f4eedc") if y > 2 else K.rgba(CANDLE[1 + (y == 0)]))
    return c, wax


def tex_crystal(P):
    rows = ["................",
            "......W.........",
            "......C.....W...",
            ".....CBC....C...",
            "..W..CBC...CBC..",
            "..C..CBBC..CBC..",
            ".CBC.CBBC.CBBC..",
            ".CBC.CBBBCCBBC..",
            ".CBBCCBBBCBBBC..",
            "..CBBCBBBBBBC...",
            "..CBBBBBBBBBC...",
            "...CBBBBBBBC....",
            "...CCBBBBBCC....",
            "....CCCCCCC.....",
            "................",
            "................"]
    c = K.sprite(rows, {"W": "#ffffff", "C": "#e8e4f4", "B": "#b8b0d4"})
    return c, K.only(c, {K.rgba("#ffffff")[:3], K.rgba("#e8e4f4")[:3]})


def tex_stand(P):
    """The standing mirror: an oval of silver in a gilt frame; looked into, it shines."""
    sp, gp = P.ramp("silver"), P.ramp("gilt")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 6.5, (y - 7.5) / 7.5)
            if d < 0.86:
                c.set(x, y, sp[3 + int(2.5 * (0.5 + 0.5 * math.sin((x - y) * 0.4)))])
                g.set(x, y, K.rgba("#f0f4ff", 200 if d < 0.6 else 140))
            elif d < 1.02:
                c.set(x, y, gp[4] if (x + y) % 3 else gp[3])
    return c, g


def tex_hand_mirror(P):
    sp, gp = P.ramp("silver"), P.ramp("gilt")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 6, (y - 6) / 6)
            if d < 0.82:
                c.set(x, y, sp[4 + (1 if x < y else 0)])
            elif d < 1.05 or (abs(x - 7.5) < 1.2 and y > 11):
                c.set(x, y, gp[4] if (x + y) % 2 else gp[3])
    return c


def textures():
    P = _P()
    t = {}
    t["palace_marble"] = tex_marble(P)
    t["palace_marble_veined"], t["palace_marble_veined_glow"] = tex_veined(P)
    t["palace_checker"] = tex_checker(P)
    t["palace_gilt"] = tex_gilt(P)
    t["palace_mirror"] = tex_mirror(P, False)
    t["palace_mirror_cracked"] = tex_mirror(P, True)
    t["palace_glass_floor"] = tex_glass_floor(P)
    t["palace_white"] = tex_white(P)
    t["palace_shadow"] = tex_shadow(P)
    t["palace_curtain"] = tex_curtain(P)
    t["palace_chandelier"], t["palace_chandelier_glow"] = tex_chandelier(P)
    t["palace_candelabra"], t["palace_candelabra_wax"] = tex_candelabra(P)
    t["palace_crystal"], t["palace_crystal_glow"] = tex_crystal(P)
    t["mirror_stand"], t["mirror_stand_glow"] = tex_stand(P)
    t["hand_mirror"] = tex_hand_mirror(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


BLOCKS = {
    "palace_marble": ("White Marble", "pickaxe"),
    "palace_marble_veined": ("Gold-Veined Marble", "pickaxe"),
    "palace_checker": ("Checkered Marble", "pickaxe"),
    "palace_gilt": ("Gilt Moulding", "pickaxe"),
    "palace_mirror": ("Palace Mirror", "pickaxe"),
    "palace_mirror_cracked": ("Cracked Palace Mirror", "pickaxe"),
    "palace_glass_floor": ("Glass Floor", "pickaxe"),
    "palace_white": ("The White", "pickaxe"),
    "palace_shadow": ("A Shadow", None),
    "palace_curtain": ("Velvet Curtain", None),
    "palace_chandelier": ("Crystal Chandelier", None),
    "palace_candelabra": ("Gilt Candelabra", "pickaxe"),
    "palace_crystal": ("Palace Crystals", None),
    "mirror_stand": ("Standing Mirror", "pickaxe"),
    "hand_mirror": ("The Hand Mirror", "pickaxe"),
}
FLAT = ("palace_chandelier", "palace_crystal", "palace_shadow")
B = K.B


def models():
    m = {}
    m["palace_marble"] = K.cube_model("palace_marble")
    m["palace_marble_veined"] = K.cube_model("palace_marble_veined", glow=True)
    m["palace_checker"] = K.cube_model("palace_checker")
    m["palace_gilt"] = K.cube_model("palace_gilt")
    m["palace_mirror"] = K.cube_model("palace_mirror")
    m["palace_mirror_cracked"] = K.cube_model("palace_mirror_cracked")
    m["palace_glass_floor"] = K.cube_model("palace_glass_floor", translucent=True)
    m["palace_white"] = K.cube_model("palace_white")
    m["palace_shadow"] = K.carpet_model("palace_shadow")
    m["palace_curtain"] = K.cube_model("palace_curtain")
    m["palace_chandelier"] = K.cross_model("palace_chandelier", glow=True)
    m["palace_crystal"] = K.cross_model("palace_crystal", glow=True)
    # the candelabra: a gilt foot and stem, three arms, a candle burning on each
    flames = []
    for cx, cz in ((8, 8), (5, 8), (11, 8)):
        flames += [K.box([cx - 0.75, 11, cz - 0.75], [cx + 0.75, 13.5, cz + 0.75], "#wax", uv=[0, 3, 2, 6])]
        flames += [K.box([cx - 0.5, 13.5, cz - 0.5], [cx + 0.5, 15, cz + 0.5], "#wax", 15, uv=[0, 0, 1, 2])]
    m["palace_candelabra"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                              "textures": {"particle": B + "palace_candelabra", "gilt": B + "palace_candelabra",
                                           "wax": B + "palace_candelabra_wax"},
                              "elements": [K.box([5.5, 0, 5.5], [10.5, 1, 10.5], "#gilt"), K.box([7.25, 1, 7.25], [8.75, 10, 8.75], "#gilt"),
                                           K.box([4.5, 10, 7.25], [11.5, 11, 8.75], "#gilt")] + flames}
    # the standing mirror: an oval in its gilt frame on a foot; looked into, it shines
    for lit in (False, True):
        tex = {"particle": B + "mirror_stand", "face": B + "mirror_stand", "frame": B + "palace_gilt"}
        els = [K.box([4, 0, 6], [12, 1, 10], "#frame"), K.box([7, 1, 7.25], [9, 3, 8.75], "#frame"),
               K.box([3, 3, 7.5], [13, 16, 8.5], "#face", uv=[1.5, 0, 14.5, 16])]
        if lit:
            tex["glow"] = B + "mirror_stand_glow"
            els.append(K.box([3, 3, 7.45], [13, 16, 8.55], "#glow", 15, sides=("north", "south"), uv=[1.5, 0, 14.5, 16]))
        m["mirror_stand" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    # the Hand Mirror: a little mirror on its handle, propped on a gilt stand
    m["hand_mirror"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                        "textures": {"particle": B + "hand_mirror", "face": B + "hand_mirror", "frame": B + "palace_gilt"},
                        "elements": [K.box([5, 0, 6.5], [11, 1, 9.5], "#frame"),
                                     K.box([4, 1, 7.5], [12, 15, 8.5], "#face", sides=("north", "south"), uv=[2, 0, 14, 16])]}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n not in ("mirror_stand",)}
    s["mirror_stand"] = K.lit_state("mirror_stand")
    return s


def content():
    K.content("gate_worlds_palace", BLOCKS, models(), states(), {}, flat_items=FLAT)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_palace.png")
    else:
        print(__doc__)
