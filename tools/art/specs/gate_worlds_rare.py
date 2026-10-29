"""Gate Worlds, the rare worlds (SPEC §38.13): the 13 blocks the rare worlds grow that no theme has.

    python3 tools/art/build_all.py gate_worlds_rare        textures
    python3 tools/art/specs/gate_worlds_rare.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_rare.py sheet PATH every texture at 6x, named

Moonflowers for the Quiet, the red lights of the Reflection, the stone of the giant's hand in the Hollow Sky, the
cartographer's map and its ink, rain running up out of puddles, standing stones the aurora lights, lantern fruit hanging
in the orchard (and the one gone out), the Gates' Heart and its veins, the shimmer in a doorway of the Library of Doors, and the pale figures
sitting round the Last Campfire. Drawn by code.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "petal": ["#8a96b0", "#b4c0d8", "#d8e2f4", "#f0f6ff", "#ffffff"],
    "stem": ["#1e3424", "#2c4a32", "#3c6040"],
    "red": ["#3a0608", "#5c0a0e", "#8a1418", "#c02024", "#f04a40", "#ff9a80"],
    "giant": ["#4e4c48", "#5e5c56", "#6e6c64", "#7e7b72", "#8e8a80", "#a09c90"],
    "lichen": ["#6a7a48", "#8a9a5a"],
    "paper": ["#a89470", "#bca884", "#ccba96", "#dccca8", "#e8dcbc"],
    "ink": ["#1a2850", "#26386a", "#344a84", "#4a64a4"],
    "rain": ["#5a7898", "#7a9ab8", "#a0bcd8", "#c8dcf0", "#ecf6ff"],
    "aurora_rock": ["#1a1e24", "#22272e", "#2c323a", "#363d46", "#424a54"],
    "aurora": ["#20a070", "#40d090", "#70f0b0", "#a0ffd0", "#c890ff"],
    "lantern": ["#6a2008", "#a03c10", "#d0641c", "#f09030", "#ffc060", "#fff0b0"],
    "unlit": ["#1e140e", "#2c1e14", "#3a281a", "#4a3422", "#5a402a", "#6a4c32"],
    "heart": ["#2a0408", "#4a0810", "#701018", "#a01c24", "#d43434", "#ff7058"],
    "vein_rock": ["#1a1214", "#24181c", "#2e2024", "#3a282c"],
    "door": ["#2a1450", "#4a2888", "#7448c0", "#a07ce8", "#d0c0ff"],
    "figure": ["#9aa4b4", "#b4bccc", "#ccd4e0", "#e2e8f0", "#f4f8fc"],
}


def _P():
    return K.px(RAMPS)


def tex_moonflower(P):
    """A pale flower on a dark stem, its petals open and faintly lit from inside."""
    pp, sp = P.ramp("petal"), P.ramp("stem")
    c = P.Canvas(16, 16)
    for y in range(7, 16):
        c.set(8, y, sp[1])
        if y in (10, 13):
            c.set(7 if y == 10 else 9, y, sp[2])
    for y in range(0, 9):
        for x in range(2, 15):
            dx, dy = x - 8, y - 4.5
            r = math.hypot(dx, dy * 1.2)
            a = math.atan2(dy, dx)
            petal = 4.0 + math.cos(a * 5) * 0.9
            if r < petal:
                v = 4 if r < 1.2 else 3 if r < petal * 0.55 else 2 if r < petal * 0.85 else 1
                c.set(x, y, pp[v])
    g = K.only(c, {pp[3][:3], pp[4][:3]})
    return c, g


def tex_red_light(P):
    """A lamp of deep red glass in a dark frame, brightest in the middle of each pane."""
    rp = P.ramp("red")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            lx, ly = x % 8, y % 8
            if lx == 0 or ly == 0:
                c.set(x, y, rp[0])
                continue
            d = math.hypot(lx - 4, ly - 4)
            c.set(x, y, rp[max(2, 5 - int(d * 0.9))])
    g = K.only(c, {rp[4][:3], rp[5][:3], rp[3][:3]})
    return c, g


def tex_giant_stone(P):
    """Old weathered stone, smooth from wind, with patches of lichen."""
    c = K.noisy(P, "giant", "giant-stone", scale=4, lo=1, span=4, jitter=0.08)
    lp = P.ramp("lichen")
    rng = P.rng_for("giant-lichen")
    for _ in range(5):
        x0, y0 = rng.randrange(16), rng.randrange(16)
        for _ in range(4):
            c.set((x0 + rng.randrange(3)) % 16, (y0 + rng.randrange(2)) % 16, lp[rng.randrange(2)])
    return c


def tex_map(P, ink):
    """The cartographer's map: old paper with a faint grid; inked, the wavy contour lines of hills."""
    pp, ip = P.ramp("paper"), P.ramp("ink")
    c = K.noisy(P, "paper", "map-paper", scale=4, lo=1, span=3, jitter=0.1)
    for i in range(16):
        c.set(i, 0, pp[1])
        c.set(0, i, pp[1])
    if not ink:
        return c
    for k in range(3):
        for x in range(16):
            y = int(3 + k * 5 + math.sin(x / 16 * math.pi * 2 + k) * 1.6) % 16
            c.set(x, y, ip[1 if k == 1 else 2])
    return c


def tex_rain_up(P):
    """Rain running upward: the falling curtain's streaks, played backward."""
    return list(reversed(K.streaks(P, "rain", "rain-up", frames=8, alpha=120)))


def tex_aurora_stone(P):
    """A standing stone, dark and rough, veins in it lit green and violet like the sky above it."""
    c = K.noisy(P, "aurora_rock", "aurora-rock", scale=4, lo=0, span=5, jitter=0.1)
    ap = P.ramp("aurora")
    paths = K.walk(P, "aurora-veins", 3, 10, turn=0.35)
    for i, path in enumerate(paths):
        for (x, y) in path:
            c.set(x % 16, y % 16, ap[4] if i == 2 else ap[2])
    g = K.only(c, {ap[2][:3], ap[4][:3]})
    return c, g


def tex_lantern_fruit(P, ramp="lantern"):
    """Lantern fruit: a round paper lantern hanging from the branch on a short stem, lit inside (or one gone out)."""
    lp, sp = P.ramp(ramp), P.ramp("stem")
    c = P.Canvas(16, 16)
    for y in range(0, 6):
        c.set(8, y, sp[1])
    for y in range(6, 15):
        for x in range(3, 13):
            dx, dy = (x - 7.5) / 4.6, (y - 10.5) / 4.2
            r = dx * dx + dy * dy
            if r < 1:
                rib = (x - 3) % 3 == 0
                v = 5 if r < 0.25 else 4 if r < 0.55 else 3 if not rib else 2
                c.set(x, y, lp[v])
    for x in (6, 7, 8, 9):
        c.set(x, 6, lp[0])
        c.set(x, 15, lp[0])
    g = K.only(c, {lp[3][:3], lp[4][:3], lp[5][:3]})
    return c, g


def tex_heart(P):
    """The Gates' Heart: deep red living stone, brighter folds through it."""
    hp = P.ramp("heart")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = math.sin(x * 0.7 + math.sin(y * 0.5) * 2) + math.sin(y * 0.6 + math.cos(x * 0.4) * 2)
            c.set(x, y, hp[max(1, min(5, int(3 + v * 1.2)))])
    g = K.only(c, {hp[4][:3], hp[5][:3]})
    return c, g


def tex_heart_vein(P):
    """Dark stone with the Heart's red veins running through it."""
    c = K.noisy(P, "vein_rock", "vein-rock", scale=4, lo=0, span=4, jitter=0.1)
    hp = P.ramp("heart")
    for path in K.walk(P, "heart-veins", 3, 12, turn=0.3):
        for (x, y) in path:
            c.set(x % 16, y % 16, hp[4])
    g = K.only(c, {hp[4][:3]})
    return c, g


def tex_doorway(P):
    """The shimmer in a doorway: violet light drifting across it."""
    dp = P.ramp("door")
    out = []
    for f in range(8):
        c = P.Canvas(16, 16)
        for y in range(16):
            for x in range(16):
                v = (math.sin(x * 0.6 + f * 0.8) + math.sin(y * 0.45 - f * 0.6 + x * 0.2)) * 0.25 + 0.5
                col = dp[max(0, min(4, int(v * 5)))]
                c.set(x, y, (*col[:3], int(120 + v * 100)))
        out.append(c)
    return out


def tex_figure(P):
    """A pale figure, sitting: hardly more than a shape of light, a little brighter where its face would be."""
    fp = P.ramp("figure")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = 2 + int(math.sin(y * 0.5 + x * 0.15) * 1.3)
            c.set(x, y, fp[max(0, min(4, v))])
    for x in range(5, 11):
        for y in range(2, 6):
            c.set(x, y, fp[4])
    return c


def textures():
    P = _P()
    t = {}
    t["rare_moonflower"], t["rare_moonflower_glow"] = tex_moonflower(P)
    t["rare_red_light"], t["rare_red_light_glow"] = tex_red_light(P)
    t["rare_giant_stone"] = tex_giant_stone(P)
    t["rare_map"] = tex_map(P, False)
    t["rare_map_ink"] = tex_map(P, True)
    t["rare_rain_up"] = tex_rain_up(P)
    t["rare_aurora_stone"], t["rare_aurora_stone_glow"] = tex_aurora_stone(P)
    t["rare_lantern_fruit"], t["rare_lantern_fruit_glow"] = tex_lantern_fruit(P)
    t["rare_lantern_dark"], _ = tex_lantern_fruit(P, "unlit")
    t["rare_heart"], t["rare_heart_glow"] = tex_heart(P)
    t["rare_heart_vein"], t["rare_heart_vein_glow"] = tex_heart_vein(P)
    t["rare_doorway"] = tex_doorway(P)
    t["rare_figure"] = tex_figure(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


BLOCKS = {
    "rare_moonflower": ("Moonflower", None),
    "rare_red_light": ("Red Light", "pickaxe"),
    "rare_giant_stone": ("Giant's Stone", "pickaxe"),
    "rare_map": ("Map Paper", "axe"),
    "rare_map_ink": ("Inked Map", "axe"),
    "rare_rain_up": ("Upward Rain", None),
    "rare_aurora_stone": ("Aurora Stone", "pickaxe"),
    "rare_lantern_fruit": ("Lantern Fruit", None),
    "rare_lantern_dark": ("Unlit Lantern Fruit", None),
    "rare_heart": ("The Gates' Heart", "pickaxe"),
    "rare_heart_vein": ("Heart Vein", "pickaxe"),
    "rare_doorway": ("Doorway Shimmer", None),
    "rare_figure": ("Pale Figure", None),
}
FLAT = ("rare_moonflower", "rare_rain_up", "rare_lantern_fruit", "rare_lantern_dark", "rare_doorway")
B = K.B


def models():
    m = {}
    for n in ("rare_giant_stone", "rare_map", "rare_map_ink"):
        m[n] = K.cube_model(n)
    for n in ("rare_red_light", "rare_aurora_stone", "rare_heart", "rare_heart_vein"):
        m[n] = K.cube_model(n, glow=True)
    m["rare_moonflower"] = K.cross_model("rare_moonflower", glow=True)
    m["rare_lantern_fruit"] = K.cross_model("rare_lantern_fruit", glow=True)
    m["rare_lantern_dark"] = K.cross_model("rare_lantern_dark")
    m["rare_rain_up"] = K.fall_model("rare_rain_up", emission=4)
    m["rare_doorway"] = K.fall_model("rare_doorway", emission=10)
    # a pale figure sitting with its knees up: a body, its head, its arms round its knees
    m["rare_figure"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                        "textures": {"particle": B + "rare_figure", "f": B + "rare_figure"},
                        "elements": [K.box([4, 0, 5], [12, 4, 12], "#f", 6, uv=[2, 12, 14, 16]),
                                     K.box([5, 4, 7], [11, 11, 11], "#f", 6, uv=[3, 5, 13, 12]),
                                     K.box([5.5, 11, 6.5], [10.5, 15, 10.5], "#f", 9, uv=[5, 2, 11, 6]),
                                     K.box([4, 5, 4], [12, 8, 7], "#f", 6, uv=[2, 6, 14, 9])]}
    return m


def states():
    return {n: K.simple_state(n) for n in BLOCKS}


def content():
    K.content("gate_worlds_rare", BLOCKS, models(), states(), {}, flat_items=FLAT)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_rare.png")
    else:
        print(__doc__)
