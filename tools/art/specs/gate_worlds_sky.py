"""Gate Worlds, Sky Archipelago (SPEC §38.13): the theme's 15 world blocks and its set piece, the Wind Bell.

    python3 tools/art/build_all.py gate_worlds_sky        textures
    python3 tools/art/specs/gate_worlds_sky.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_sky.py sheet PATH every texture at 6x, named

Islands in the sky: pale blue-grey rock in layers, turf with small flowers on top, the white marble and gilt of a city
that fell from higher up, sun-bleached bridge planks and rope, water falling off an island's edge into nothing, roots
and vines hanging under, wind crystals, long pennants, a wind lantern, a sky whale's hide and its barnacles, and the
wind chime that sings when you touch it. Drawn by code, nothing copied.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "skyrock": ["#4c5664", "#5c6878", "#6e7a8a", "#808c9c", "#94a0ae", "#a8b2be"],
    "turf": ["#2e5a24", "#3a6e2c", "#488234", "#56963e", "#6aaa4a", "#80be5a"],
    "marble": ["#9a968e", "#b0aca2", "#c4c0b6", "#d6d2c8", "#e6e2d8", "#f2efe6"],
    "gilt": ["#5a3a10", "#7a5218", "#a07024", "#c89434", "#e8b848", "#fcd870"],
    "plank": ["#6a5a44", "#7e6c52", "#928062", "#a69474", "#b8a888", "#cabc9e"],
    "rope": ["#4e3c24", "#64502e", "#7a643a", "#907a4a", "#a8925c"],
    "fall": ["#3a7aa8", "#5a9ac4", "#7ab8dc", "#a0d4f0", "#d0eefc", "#f4fcff"],
    "vine": ["#1c3a18", "#26501e", "#346a28", "#448432", "#58a03e"],
    "crystal": ["#3a8aa0", "#58b0c8", "#7ed0e0", "#aee8f2", "#e0faff"],
    "pennant": ["#6a1c1c", "#8c2a24", "#b03c30", "#d0583c", "#e8d8b0", "#f8ecd0"],
    "hide": ["#2e3440", "#3a4250", "#485262", "#586474", "#6a7686", "#8a96a4"],
    "barnacle": ["#5a5650", "#78746a", "#96907e", "#b4ae98", "#d0caae"],
    "brass": ["#4a3414", "#6a4c1c", "#8c6826", "#b08a34", "#d4ae4a", "#f0d270"],
}


def _P():
    return K.px(RAMPS)


def tex_rock(P):
    """Layered island rock: soft horizontal strata, a few darker seams."""
    rp = P.ramp("skyrock")
    rng = P.rng_for("sky-rock")
    n = P.value_noise(16, 16, rng, scale=5, octaves=2)
    c = P.Canvas(16, 16)
    for y in range(16):
        band = 1 + (y // 3) % 3
        for x in range(16):
            v = band + int((n[y][x] - 0.5) * 2.4)
            if y % 5 == 4 and rng.random() < 0.7:
                v -= 1
            c.set(x, y, rp[max(0, min(5, v))])
    return c


def tex_turf(P):
    """Short turf from above, with a few tiny white and yellow flowers."""
    c = K.noisy(P, "turf", "sky-turf", scale=3, lo=1, span=4)
    rng = P.rng_for("sky-turf-flowers")
    for _ in range(5):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, K.rgba("#f4f0e0") if rng.random() < 0.6 else K.rgba("#f0d050"))
    return c


def tex_turf_side(P, rock):
    """The island's edge: turf over the rock, its roots hanging a little way down."""
    c = rock.copy()
    tp = P.ramp("turf")
    rng = P.rng_for("sky-turf-side")
    for x in range(16):
        d = 3 + int(math.sin(x * 1.7) * 1.2 + rng.random() * 1.5)
        for y in range(d):
            c.set(x, y, tp[2 + (x * 3 + y) % 3])
        if rng.random() < 0.4:
            c.set(x, d, tp[0])
    return c


def tex_marble(P, carved):
    """Weathered white marble with fine grey veins; carved, a band of key pattern with a gilt line through it."""
    c = K.noisy(P, "marble", "sky-marble" + ("-carved" if carved else ""), scale=6, lo=2, span=3, jitter=0.08)
    mp = P.ramp("marble")
    K.lines(c, K.walk(P, "sky-marble-veins", 2, 14, turn=0.25), mp[1])
    if not carved:
        return c
    gp = P.ramp("gilt")
    # a band of key pattern, gilt lines above and below it
    key = ["####", "#..#", "#.##", "#...", "####", "...."]
    for x in range(16):
        c.set(x, 4, gp[4])
        c.set(x, 11, gp[4])
        for y in range(5, 11):
            c.set(x, y, mp[1] if key[y - 5][x % 4] == "#" else mp[4])
    g = K.only(c, {gp[4][:3]})
    return c, g


def tex_gilt(P):
    """Gilded bronze: a polished rod, worn darker where hands held it."""
    gp = P.ramp("gilt")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            v = 3 + int(math.sin(x * 0.4 + 0.6) * 1.6)
            if (x * 7 + y * 5) % 23 == 0:
                v -= 1
            c.set(x, y, gp[max(0, min(5, v))])
    return c


def tex_gilt_top(P):
    gp = P.ramp("gilt")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            c.set(x, y, gp[5 if r < 3 else 4 if r < 5 else 3 if r < 7 else 1])
    return c


def tex_planks(P):
    return K.boards(P, "plank", "sky-planks", h=4, nails=True)


def tex_rope(P):
    """A post wound round with rope: diagonal turns."""
    rp = P.ramp("rope")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            t = (x + y * 2) % 6
            c.set(x, y, rp[4 if t == 0 else 3 if t < 3 else 2 if t < 5 else 1])
    return c


def tex_rope_top(P):
    rp = P.ramp("rope")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            a = math.atan2(y - 7.5, x - 7.5)
            c.set(x, y, rp[1 + int((math.sin(r * 1.6 + a) + 1) * 1.4)])
    return c


def tex_fall(P):
    return K.streaks(P, "fall", "sky-fall", frames=8, alpha=150)


def tex_vines(P):
    """Roots and vines hanging under an island, thinning to the tips."""
    vp = P.ramp("vine")
    rng = P.rng_for("sky-vines")
    c = P.Canvas(16, 16)
    for i in range(5):
        x = 1 + i * 3 + rng.randrange(2)
        end = 9 + rng.randrange(7)
        for y in range(end):
            xx = x + int(math.sin(y * 0.5 + i) * 0.8)
            c.set(xx % 16, y, vp[1 + (y + i) % 3])
            if y % 4 == 2:
                c.set((xx + 1) % 16, y, vp[4])
    return c


def tex_crystal(P):
    """A wind crystal: three pale blue spikes, bright at their tips."""
    cp = P.ramp("crystal")
    c = P.Canvas(16, 16)
    for cx, h, w in ((5, 11, 2), (9, 15, 3), (12, 8, 2)):
        for y in range(16 - h, 16):
            half = max(0, int(w * (y - (16 - h)) / h + 0.5))
            for x in range(cx - half, cx + half + 1):
                if 0 <= x < 16:
                    c.set(x, y, cp[4] if y < 16 - h + 2 else cp[3] if x == cx else cp[1 + (x + y) % 2])
    g = K.only(c, {cp[4][:3], cp[3][:3]})
    return c, g


def tex_banner(P):
    """Long pennants on a cord, red and cream, frayed at the tails."""
    pp = P.ramp("pennant")
    c = P.Canvas(16, 16)
    for x in range(16):
        c.set(x, 1, pp[0])
    for i, x0 in enumerate((1, 6, 11)):
        col = pp[3] if i % 2 == 0 else pp[5]
        for y in range(2, 14):
            w = max(0, 4 - (y - 2) // 3)
            for x in range(x0, x0 + w):
                c.set(x, y, col if (x + y) % 5 else pp[2])
    return c


def tex_lantern(P):
    """A wind lantern: a brass cage, its pane bright, a little vane on top."""
    bp = P.ramp("brass")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15) or x == 7 or x == 8
            c.set(x, y, bp[2] if edge else K.rgba("#fff0c0"))
    g = K.only(c, {(255, 240, 192)})
    return c, g


def tex_hide(P, barnacles):
    """A sky whale's hide: blue-grey, pale scars across it; with barnacles, crusted in rough pale cones."""
    c = K.noisy(P, "hide", "sky-hide" + ("-b" if barnacles else ""), scale=7, lo=1, span=3, jitter=0.06)
    hp = P.ramp("hide")
    K.lines(c, K.walk(P, "sky-hide-scars", 2, 9, turn=0.15), hp[5])
    if barnacles:
        bp = P.ramp("barnacle")
        rng = P.rng_for("sky-barnacles")
        for _ in range(7):
            x, y = rng.randrange(1, 15), rng.randrange(1, 15)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                c.set(x + dx, y + dy, bp[3 if (dx + dy) == 0 else 2])
            c.set(x, y, bp[4])
            c.set(x + 1, y + 1, bp[0])
    return c


def tex_chime(P, lit):
    """The wind chime: a brass bar with five tubes hanging under it, a striker in the middle."""
    bp = P.ramp("brass")
    c = P.Canvas(16, 16)
    for x in range(1, 15):
        c.set(x, 1, bp[1])
        c.set(x, 2, bp[3])
    for i, x in enumerate((2, 5, 8, 11, 14)):
        for y in range(3, 9 + (i % 3) * 2):
            c.set(x, y, bp[4] if y % 3 else bp[5])
    for y in range(3, 12):
        c.set(7, y, bp[0])
    c.set(7, 12, bp[2])
    if not lit:
        return c
    g = P.Canvas(16, 16)
    for i, x in enumerate((2, 5, 8, 11, 14)):
        for y in range(3, 9 + (i % 3) * 2):
            g.set(x, y, K.rgba("#c8f0ff"))
    return c, g


def tex_wind_bell(P):
    """The Wind Bell: a small brass bell with a pale feather hanging from its clapper."""
    bp = P.ramp("brass")
    c = P.Canvas(16, 16)
    for y in range(2, 11):
        half = 2 + (y - 2) // 2
        for x in range(8 - half, 8 + half):
            c.set(x, y, bp[4] if x < 7 else bp[3] if x < 9 else bp[2])
    for x in range(3, 13):
        c.set(x, 11, bp[1])
    for y in range(12, 15):
        c.set(8, y, K.rgba("#f0f4f8"))
        c.set(7, y + 1 if y < 14 else y, K.rgba("#d8e0e8"))
    g = K.only(c, {(240, 244, 248)})
    return c, g


def textures():
    P = _P()
    t = {}
    rock = tex_rock(P)
    t["sky_rock"] = rock
    t["sky_turf"] = tex_turf_side(P, rock)
    t["sky_turf_top"] = tex_turf(P)
    t["sky_marble"] = tex_marble(P, False)
    t["sky_marble_carved"], t["sky_marble_carved_glow"] = tex_marble(P, True)
    t["sky_gold"] = tex_gilt(P)
    t["sky_gold_top"] = tex_gilt_top(P)
    t["sky_planks"] = tex_planks(P)
    t["sky_rope"] = tex_rope(P)
    t["sky_rope_top"] = tex_rope_top(P)
    t["sky_fall"] = tex_fall(P)
    t["sky_vines"] = tex_vines(P)
    t["sky_crystal"], t["sky_crystal_glow"] = tex_crystal(P)
    t["sky_banner"] = tex_banner(P)
    t["sky_lantern"], t["sky_lantern_glow"] = tex_lantern(P)
    t["sky_hide"] = tex_hide(P, False)
    t["sky_barnacle"] = tex_hide(P, True)
    t["sky_chime"] = tex_chime(P, False)
    _, t["sky_chime_glow"] = tex_chime(P, True)
    t["wind_bell"], t["wind_bell_glow"] = tex_wind_bell(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


BLOCKS = {
    "sky_rock": ("Island Rock", "pickaxe"),
    "sky_turf": ("Island Turf", "shovel"),
    "sky_marble": ("Fallen Marble", "pickaxe"),
    "sky_marble_carved": ("Carved Fallen Marble", "pickaxe"),
    "sky_gold": ("Sky Gilt", "pickaxe"),
    "sky_planks": ("Bridge Planks", "axe"),
    "sky_rope": ("Rope Post", "axe"),
    "sky_fall": ("Falling Water", None),
    "sky_vines": ("Hanging Roots", None),
    "sky_crystal": ("Wind Crystal", None),
    "sky_banner": ("Pennants", None),
    "sky_lantern": ("Wind Lantern", None),
    "sky_hide": ("Sky Whale Hide", "pickaxe"),
    "sky_barnacle": ("Barnacled Hide", "pickaxe"),
    "sky_chime": ("Wind Chime", "pickaxe"),
    "wind_bell": ("The Wind Bell", None),
}
FLAT = ("sky_vines", "sky_crystal", "sky_banner", "sky_fall")
B = K.B


def models():
    m = {}
    for n in ("sky_rock", "sky_marble", "sky_planks", "sky_hide", "sky_barnacle"):
        m[n] = K.cube_model(n)
    m["sky_turf"] = K.cube_model("sky_turf", top="sky_turf_top", bottom="sky_rock")
    m["sky_marble_carved"] = K.cube_model("sky_marble_carved", glow=True)
    m["sky_gold"] = K.column_model("sky_gold", "sky_gold_top")
    m["sky_rope"] = K.column_model("sky_rope", "sky_rope_top")
    m["sky_fall"] = K.fall_model("sky_fall", emission=6)
    m["sky_vines"] = K.cross_model("sky_vines")
    m["sky_crystal"] = K.cross_model("sky_crystal", glow=True)
    m["sky_banner"] = K.cross_model("sky_banner")
    # the wind lantern: a brass cage round a bright pane, a cap and a ring
    m["sky_lantern"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                        "textures": {"particle": B + "sky_lantern", "l": B + "sky_lantern", "glow": B + "sky_lantern_glow"},
                        "elements": [K.box([5, 0, 5], [11, 8, 11], "#l", uv=[2, 4, 14, 16]),
                                     K.box([5, 0, 5], [11, 8, 11], "#glow", 15, sides=("north", "south", "east", "west"), uv=[2, 4, 14, 16]),
                                     K.box([4.5, 8, 4.5], [11.5, 9.5, 11.5], "#l", uv=[0, 0, 16, 2]),
                                     K.box([7, 9.5, 7], [9, 12, 9], "#l", uv=[7, 0, 9, 3])]}
    # the wind chime on its stand: shut, and lit while it sings
    for lit in (False, True):
        tex = {"particle": B + "sky_chime", "c": B + "sky_chime"}
        els = [K.box([2, 0, 6], [14, 1, 10], "#c", uv=[0, 13, 16, 15]), K.box([2, 1, 7.5], [3, 15, 8.5], "#c", uv=[1, 0, 2, 14]),
               K.box([13, 1, 7.5], [14, 15, 8.5], "#c", uv=[1, 0, 2, 14]),
               K.box([2, 14, 7.5], [14, 15, 8.5], "#c", uv=[0, 1, 16, 2]),
               K.box([3.5, 6, 8], [12.5, 14, 8], "#c", sides=("north", "south"), uv=[1, 2, 15, 10])]
        if lit:
            tex["glow"] = B + "sky_chime_glow"
            els.append(K.box([3.5, 6, 7.98], [12.5, 14, 8.02], "#glow", 15, sides=("north", "south"), uv=[1, 2, 15, 10]))
        m["sky_chime" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": els}
    m["wind_bell"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                      "textures": {"particle": B + "wind_bell", "b": B + "wind_bell", "glow": B + "wind_bell_glow"},
                      "elements": [K.box([5, 3, 5], [11, 10, 11], "#b", uv=[3, 2, 13, 11]),
                                   K.box([4, 2, 4], [12, 3, 12], "#b", uv=[3, 11, 13, 12]),
                                   K.box([7.5, 10, 7.5], [8.5, 13, 8.5], "#b", uv=[7, 0, 8, 2]),
                                   K.box([7.5, 0, 7.5], [8.5, 3, 8.5], "#glow", 12, uv=[7, 12, 9, 15])]}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n not in ("sky_gold", "sky_rope", "sky_chime")}
    s["sky_gold"] = K.axis_state("sky_gold")
    s["sky_rope"] = K.axis_state("sky_rope")
    s["sky_chime"] = K.lit_state("sky_chime")
    return s


def content():
    K.content("gate_worlds_sky", BLOCKS, models(), states(), {}, flat_items=FLAT)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_sky.png")
