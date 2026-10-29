"""Gate Worlds, Clockwork Foundry (SPEC §38.11): the theme's 12 world blocks and its set piece, the Pocket Engine.

    python3 tools/art/build_all.py gate_worlds_foundry        textures
    python3 tools/art/specs/gate_worlds_foundry.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_foundry.py sheet PATH every texture at 6x, named

Riveted iron plate, sooty red brick, polished brass, brass gear faces, open iron grating you see down through, copper pipe
with its collars, a furnace mouth that glows, caged work lamps, a curtain of steam, sparks, hanging cables, the great
valve wheel and the little engine that ticks. Drawn by code, nothing copied.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "iron": ["#121316", "#1c1d22", "#272930", "#33363e", "#41444d", "#51545e"],
    "brick": ["#1e0f0c", "#2e1610", "#3f1e15", "#52281b", "#663222", "#7a3e2a"],
    "brass": ["#3a2a10", "#5a4218", "#7a5c22", "#9c7a30", "#c09c48", "#e0c070"],
    "copper": ["#2a140c", "#4a2414", "#6a361e", "#8c4a28", "#ae6236", "#cc8048"],
    "fglow": ["#c85a10", "#ff9a30", "#ffd27a", "#fff2c8"],
    "steam": ["#8a9098", "#a8aeb6", "#c4c8ce", "#dce0e4", "#eef0f2"],
    "rubber": ["#0a0a0c", "#141418", "#1e1e24", "#2a2a32"],
}
HOT = ("#ff9a30", "#ffd27a", "#fff2c8")


def _P():
    return K.px(RAMPS)


def tex_iron(P):
    ip = P.ramp("iron")
    c = K.noisy(P, "iron", "plate", scale=3, lo=2, span=3)
    for y in range(16):
        for x in range(16):
            if x == 0 or y == 0:
                c.set(x, y, ip[0])
            elif x == 1 or y == 1:
                c.set(x, y, ip[5])
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, ip[5])
        c.set(x + 1, y + 1, ip[0])
    rng = P.rng_for("iron-rust")
    for _ in range(6):
        c.blend(rng.randrange(16), rng.randrange(16), K.rgba("#6a361e"), 0.45)
    return c


def tex_brick(P):
    bp = P.ramp("brick")
    rng = P.rng_for("foundry-brick")
    c = P.Canvas(16, 16)
    for y in range(16):
        row = y // 4
        for x in range(16):
            xx = (x + (4 if row % 2 else 0)) % 16
            if y % 4 == 3 or xx % 8 == 0:
                c.set(x, y, bp[0])
            else:
                c.set(x, y, bp[2 + (xx // 8 + row) % 3 - (1 if rng.random() < 0.12 else 0)])
    # soot climbing from below
    for x in range(16):
        for y in range(10, 16):
            if rng.random() < (y - 9) / 9:
                c.blend(x, y, K.rgba("#0a0a0c"), 0.35)
    return c


def tex_brass(P):
    bp = P.ramp("brass")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = 3 + (1 if (x - y) % 7 < 2 else 0) - (1 if y in (0, 15) or x in (0, 15) else 0)
            c.set(x, y, bp[v])
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13), (7, 2), (7, 13)):
        c.set(x, y, bp[5])
    return c


def tex_gear(P):
    bp, ip = P.ramp("brass"), P.ramp("iron")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r, a = math.hypot(dx, dy), math.atan2(dy, dx)
            tooth = math.cos(a * 8) > 0.2
            if r < 1.6:
                c.set(x, y, ip[0])
            elif r < 2.8:
                c.set(x, y, bp[5])
            elif r < 5.6:
                c.set(x, y, bp[3] if int(a * 3 / math.pi * 2) % 2 else bp[2])
            elif r < 7.2 or (r < 8.2 and tooth):
                c.set(x, y, bp[4] if dy < 0 else bp[3])
            else:
                c.set(x, y, ip[1])
    return c


def tex_grate(P):
    ip = P.ramp("iron")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if x % 4 in (0, 1) or y % 4 in (0, 1):
                c.set(x, y, ip[4] if (x % 4 == 0 or y % 4 == 0) else ip[2])
    return c


def tex_pipe(P):
    cp = P.ramp("copper")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = int(2 + 3 * math.sin((x + 0.5) / 16 * math.pi))
            if y in (0, 1, 14, 15):
                v = min(5, v + 1) if y in (1, 14) else 1
            c.set(x, y, cp[max(0, min(5, v))])
    rng = P.rng_for("verdigris")
    for _ in range(8):
        c.blend(rng.randrange(16), rng.randrange(2, 14), K.rgba("#4a9a80"), 0.55)
    return c


def tex_pipe_end(P):
    cp = P.ramp("copper")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            c.set(x, y, cp[0] if d < 4.5 else cp[4] if d < 6 else cp[2] if d < 7.6 else cp[1])
    return c


def tex_furnace(P):
    c = tex_brick(P)
    g = P.Canvas(16, 16)
    for y in range(6, 15):
        for x in range(3, 13):
            arch = y > 8 or abs(x - 7.5) < 2 + (y - 6) * 0.9
            if arch:
                k = (y - 6) / 8
                col = HOT[2] if k > 0.75 else HOT[1] if k > 0.4 else HOT[0]
                c.set(x, y, K.rgba(col))
                g.set(x, y, K.rgba(col))
    return c, g


def tex_lamp(P):
    ip = P.ramp("iron")
    cage = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if x in (0, 4, 11, 15) or y in (0, 5, 10, 15):
                cage.set(x, y, ip[4] if (x + y) % 2 else ip[3])
    glow = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            glow.set(x, y, K.rgba("#fff6dc") if d < 3 else K.rgba(HOT[1]) if d < 6 else K.rgba(HOT[0]))
    return cage, glow


def tex_steam(P):
    return K.streaks(P, "steam", "steam", frames=8, alpha=120, emissive=False)


def tex_sparks(P):
    rng = P.rng_for("sparks")
    c = P.Canvas(16, 16)
    for _ in range(14):
        x, y = rng.randrange(1, 15), rng.randrange(1, 15)
        col = HOT[rng.randrange(3)]
        c.set(x, y, K.rgba(col))
        if rng.random() < 0.5:
            c.set(x, y + 1, K.rgba(HOT[0], 180))
    return c, K.only(c, {K.rgba(h)[:3] for h in HOT})


def tex_cables(P):
    rp = P.ramp("rubber")
    c = P.Canvas(16, 16)
    for cx, ph in ((5, 0.0), (10, 1.3)):
        for y in range(16):
            x = cx + int(round(math.sin(y * 0.3 + ph) * 1.4))
            c.set(x, y, rp[2])
            c.set(x + 1, y, rp[1])
            if y % 6 == 3:
                c.set(x, y, P.ramp("brass")[4])
    return c


def tex_valve(P):
    bp = P.ramp("brass")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r, a = math.hypot(dx, dy), math.atan2(dy, dx)
            if 5.6 < r < 7.6 or r < 2 or (r < 6 and min(abs(math.sin(a * 2)), abs(math.cos(a * 2))) < 0.18):
                c.set(x, y, bp[4] if dy < 0 else bp[3])
    hot = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if c.get(x, y)[3]:
                hot.set(x, y, K.rgba(HOT[1] if (x + y) % 3 else HOT[2]))
    return c, hot


def tex_engine(P):
    bp = P.ramp("brass")
    body = tex_brass(P)
    face = tex_gear(P)
    glow = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            if math.hypot(x - 7.5, y - 7.5) < 2.8:
                glow.set(x, y, K.rgba(HOT[1]))
    return body, face, glow


def textures():
    P = _P()
    t = {}
    t["foundry_iron"] = tex_iron(P)
    t["foundry_brick"] = tex_brick(P)
    t["foundry_brass"] = tex_brass(P)
    t["foundry_gear"] = tex_gear(P)
    t["foundry_grille"] = tex_grate(P)
    t["foundry_pipe"] = tex_pipe(P)
    t["foundry_pipe_end"] = tex_pipe_end(P)
    t["foundry_furnace"], t["foundry_furnace_glow"] = tex_furnace(P)
    t["foundry_bulb"], t["foundry_bulb_glow"] = tex_lamp(P)
    t["foundry_steam"] = tex_steam(P)
    t["foundry_sparks"], t["foundry_sparks_glow"] = tex_sparks(P)
    t["foundry_cables"] = tex_cables(P)
    t["foundry_valve"], t["foundry_valve_glow"] = tex_valve(P)
    t["pocket_engine"], t["pocket_engine_face"], t["pocket_engine_glow"] = tex_engine(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


BLOCKS = {
    "foundry_iron": ("Riveted Iron", "pickaxe"),
    "foundry_brick": ("Foundry Brick", "pickaxe"),
    "foundry_brass": ("Polished Brass", "pickaxe"),
    "foundry_gear": ("Brass Gear Block", "pickaxe"),
    "foundry_grille": ("Iron Grating", "pickaxe"),
    "foundry_pipe": ("Copper Pipe", "pickaxe"),
    "foundry_furnace": ("Foundry Furnace", "pickaxe"),
    "foundry_bulb": ("Work Lamp", "pickaxe"),
    "foundry_steam": ("Steam", None),
    "foundry_sparks": ("Sparks", None),
    "foundry_cables": ("Hanging Cables", None),
    "foundry_valve": ("Pressure Valve", "pickaxe"),
    "pocket_engine": ("The Pocket Engine", "pickaxe"),
}
FLAT = ("foundry_sparks", "foundry_cables")
B = K.B


def models():
    m = {}
    m["foundry_iron"] = K.cube_model("foundry_iron")
    m["foundry_brick"] = K.cube_model("foundry_brick")
    m["foundry_brass"] = K.cube_model("foundry_brass")
    m["foundry_gear"] = K.cube_model("foundry_gear")
    m["foundry_grille"] = K.model({"side": B + "foundry_grille"}, [K.cube_el("#side")])
    m["foundry_pipe"] = K.column_model("foundry_pipe", "foundry_pipe_end")
    m["foundry_furnace"] = K.cube_model("foundry_furnace", glow=True)
    m["foundry_steam"] = K.fall_model("foundry_steam", 0)
    m["foundry_sparks"] = K.cross_model("foundry_sparks", glow=True)
    m["foundry_cables"] = K.cross_model("foundry_cables")
    m["foundry_bulb"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                         "textures": {"particle": B + "foundry_bulb", "cage": B + "foundry_bulb", "glow": B + "foundry_bulb_glow",
                                      "iron": B + "foundry_iron"},
                         "elements": [K.box([6, 1, 6], [10, 10, 10], "#glow", 15),
                                      K.box([5, 0, 5], [11, 11, 11], "#cage", sides=("north", "south", "west", "east")),
                                      K.box([5, 0, 5], [11, 1, 11], "#iron"), K.box([4.5, 11, 4.5], [11.5, 12, 11.5], "#iron")]}
    # the valve: a brass wheel on a stub of pipe; turned, the wheel glows hot
    for lit in (False, True):
        tex = {"particle": B + "foundry_pipe", "pipe": B + "foundry_pipe", "end": B + "foundry_pipe_end",
               "wheel": B + ("foundry_valve_glow" if lit else "foundry_valve")}
        els = [K.box([5, 0, 5], [11, 10, 11], "#pipe"), K.box([4, 0, 4], [12, 2, 12], "#end", sides=("up", "north", "south", "west", "east")),
               {"from": [2, 12, 2], "to": [14, 12, 14], "faces": {"up": {"texture": "#wheel"}, "down": {"texture": "#wheel"}},
                **({"light_emission": 15} if lit else {})},
               K.box([7, 10, 7], [9, 12.5, 9], "#end")]
        m["foundry_valve" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    # the Pocket Engine: a brass box with a gear face each side, its heart glows while it runs
    for on in (False, True):
        tex = {"particle": B + "pocket_engine", "body": B + "pocket_engine", "face": B + "pocket_engine_face"}
        els = [K.box([3, 0, 3], [13, 10, 13], "#body"), K.box([2.9, 1, 4], [2.9, 9, 12], "#face", sides=("west",)),
               K.box([13.1, 1, 4], [13.1, 9, 12], "#face", sides=("east",)), K.box([6, 10, 6], [10, 12, 10], "#body")]
        if on:
            tex["glow"] = B + "pocket_engine_glow"
            els += [K.box([2.85, 1, 4], [2.85, 9, 12], "#glow", 15, sides=("west",)),
                    K.box([13.15, 1, 4], [13.15, 9, 12], "#glow", 15, sides=("east",))]
        m["pocket_engine" + ("_on" if on else "")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n not in ("foundry_pipe", "foundry_valve", "pocket_engine")}
    s["foundry_pipe"] = K.axis_state("foundry_pipe")
    s["foundry_valve"] = K.lit_state("foundry_valve")
    s["pocket_engine"] = {"variants": {"powered=false": {"model": B + "pocket_engine"}, "powered=true": {"model": B + "pocket_engine_on"}}}
    return s


def content():
    K.content("gate_worlds_foundry", BLOCKS, models(), states(), {}, climbable=("foundry_cables",), flat_items=FLAT)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_foundry.png")
    else:
        print(__doc__)
