"""Gate Worlds, Fungal Hollow (SPEC §38.10): the theme's 12 world blocks and its set piece, the Spore Jar.

    python3 tools/art/build_all.py gate_worlds_fungal        textures
    python3 tools/art/specs/gate_worlds_fungal.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/gate_worlds_fungal.py sheet PATH every texture at 6x, named

Violet rock with veins of teal light, black loam, moss that glows at its tips, glowcaps (pale fibrous stems, purple caps
spotted lighter, gills that shine), lumen roots, sprouts, threads of mycelium, a falling curtain of glowing water and the
spore puffball you strike. Drawn by code, nothing copied.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402

RAMPS = {
    "frock": ["#16101c", "#20172a", "#2b1f37", "#382945", "#463453", "#574263"],
    "fvein": ["#1f6a66", "#36a89a", "#6fe0c8", "#c8fff0"],
    "floam": ["#0f0a0e", "#181115", "#22191c", "#2d2225", "#392c2e"],
    "fmoss": ["#0d2426", "#133638", "#1a4a4a", "#24605c", "#307a70", "#3e9486"],
    "fstem": ["#7c7282", "#968c9b", "#b0a7b4", "#c8c0ca", "#dcd6dd", "#ece8ed"],
    "fcap": ["#22092e", "#331043", "#47185a", "#5d2274", "#762f8e", "#9248a8"],
    "fgill": ["#123848", "#1c5466", "#2a7686", "#4aa4ac", "#82d4d2", "#c4f6f0"],
    "fpuff": ["#6a6274", "#8a8296", "#aaa2b4", "#c6c0ce", "#dedae4", "#f2f0f6"],
}
TEAL = ("#6fe0c8", "#a8f8e8", "#c8fff0")


def _P():
    return K.px(RAMPS)


def tex_rock(P):
    c = K.noisy(P, "frock", "rock", scale=3, lo=1, span=4)
    rp = P.ramp("frock")
    K.lines(c, K.walk(P, "rock-crack", 3, 6, turn=0.6), rp[0])
    for x, y in ((3, 4), (11, 9), (6, 13)):
        c.blend(x, y, rp[5], 0.5)
    return c


def tex_veined(P):
    c = tex_rock(P)
    veins = K.walk(P, "veins", 3, 11, turn=0.45)
    g = K.glow_of(veins, [K.rgba(TEAL[0]), K.rgba(TEAL[1])], halo=K.rgba("#2a7a72", 150))
    return K.under(c, g, 0.6), g


def tex_loam(P):
    c = K.noisy(P, "floam", "loam", scale=4, octaves=3, jitter=0.3)
    rp = P.ramp("floam")
    rng = P.rng_for("loam-bits")
    for _ in range(9):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, rp[4])
        c.set((x + 1) % 16, y, rp[3])
    return c


def tex_moss(P):
    top = K.noisy(P, "fmoss", "moss", scale=4, octaves=3, lo=1, jitter=0.25)
    g_top = K.dots(P, "moss-tips", 11, [K.rgba(TEAL[0]), K.rgba(TEAL[1])])
    top = K.under(top, g_top, 0.5)
    side = tex_loam(P)
    mp = P.ramp("fmoss")
    rng = P.rng_for("moss-hang")
    g_side = P.Canvas(16, 16)
    for x in range(16):
        d = 3 + rng.randrange(3)
        for y in range(d):
            side.set(x, y, mp[min(5, 2 + (d - y) // 2 + (x % 3 == 0))])
        if rng.random() < 0.3:
            g_side.set(x, d - 1, K.rgba(TEAL[0]))
    return top, g_top, K.under(side, g_side, 0.5), g_side


def tex_stem(P):
    rp = P.ramp("fstem")
    rng = P.rng_for("stem")
    n = P.value_noise(16, 16, rng, scale=2, octaves=2)
    c = P.Canvas(16, 16)
    fib = [rng.random() * 0.6 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            v = 1 + int((n[y][x] * 0.6 + fib[x] + math.sin(y * 0.9 + x) * 0.08) * 4)
            if x % 4 == 1 and (y + x) % 7 < 3:
                v -= 1
            c.set(x, y, rp[max(0, min(5, v))])
    end = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            a = math.atan2(y - 7.5, x - 7.5)
            v = 4 - int(d / 2.2) + (1 if math.sin(a * 9) > 0.6 else 0)
            end.set(x, y, rp[max(0, min(5, v))] if d < 7.6 else rp[1])
    return c, end


def tex_cap(P):
    c = K.noisy(P, "fcap", "cap", scale=3, lo=1, span=4)
    rp = P.ramp("fcap")
    rng = P.rng_for("cap-spots")
    for _ in range(5):
        cx, cy, r = rng.randrange(16), rng.randrange(16), 1 + rng.random() * 1.3
        for y in range(-2, 3):
            for x in range(-2, 3):
                if x * x + y * y <= r * r:
                    c.set((cx + x) % 16, (cy + y) % 16, P.hex_rgba("#c8a0d8") if x * x + y * y < r * r * 0.4 else P.hex_rgba("#a878c0"))
    return c


def tex_gills(P):
    rp = P.ramp("fgill")
    c = P.Canvas(16, 16)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            k = (x + int(math.sin(y * 0.5) * 1.5)) % 4
            v = (5, 3, 1, 3)[k] - (1 if (y * 3 + x) % 11 == 0 else 0)
            c.set(x, y, rp[v])
            if k == 0:
                g.set(x, y, K.rgba(TEAL[1]) if y % 5 else K.rgba(TEAL[2]))
    return c, g


def tex_roots(P):
    rows = ["..r.....a....r..",
            "..r.....a....r..",
            "..r....aa....r..",
            "..rr...a....rr..",
            "...r...a....r...",
            "...r...a....r...",
            "...r...aa...r...",
            "..rr....a...r...",
            "..r.....a...rr..",
            "..r.....a....r..",
            "..g.....a....r..",
            "..G.....a....r..",
            "........g....g..",
            "........G....G..",
            "................",
            "................"]
    s = K.sprite(rows, {"r": "#5a7a86", "a": "#6e909c", "g": "#6fe0c8", "G": "#c8fff0"})
    return s, K.only(s, {K.rgba("#6fe0c8")[:3], K.rgba("#c8fff0")[:3]})


def tex_sprouts(P):
    rows = ["................",
            "................",
            "................",
            "................",
            "...cCc..........",
            "..cGGGc.....cc..",
            "...sss.....cGGc.",
            "....s.......ss..",
            "....s.......s...",
            "....s...cc..s...",
            "....s..cGGc.s...",
            "....s...ss..s...",
            "...ss....s..s...",
            "....s....s.ss...",
            "...ss....s..s...",
            "..s.s...ss.s.s.."]
    s = K.sprite(rows, {"c": "#5d2274", "C": "#9248a8", "s": "#c8c0ca", "G": "#8ff0d8"})
    return s, K.only(s, {K.rgba("#8ff0d8")[:3]})


def tex_threads(P):
    c = P.Canvas(16, 16)
    paths = K.walk(P, "threads", 6, 12, turn=0.5)
    K.lines(c, paths, K.rgba("#9ad8d0"))
    g = K.glow_of(paths[:3], [K.rgba(TEAL[1]), K.rgba(TEAL[0])])
    return c, g


def tex_glowfall(P):
    return K.streaks(P, "fvein", "glowfall", frames=8, alpha=150)


def tex_puff(P):
    rp = P.ramp("fpuff")
    c = K.noisy(P, "fpuff", "puff", scale=3, lo=2, span=3)
    rng = P.rng_for("warts")
    g = P.Canvas(16, 16)
    for _ in range(14):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, rp[5])
        c.set(x, (y + 1) % 16, rp[1])
        if rng.random() < 0.5:
            g.set(x, y, K.rgba(TEAL[1]))
    return c, g


def tex_jar(P):
    glass = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            glass.set(x, y, (180, 230, 230, 150) if edge else (120, 200, 200, 40))
    glass.set(2, 3, (240, 255, 255, 200))
    glass.set(2, 4, (240, 255, 255, 170))
    glass.set(3, 2, (240, 255, 255, 170))
    core = P.Canvas(16, 16)
    rng = P.rng_for("jar-core")
    for _ in range(30):
        x, y = rng.randrange(16), rng.randrange(16)
        core.set(x, y, K.rgba(TEAL[rng.randrange(3)]))
    return glass, core


def textures():
    P = _P()
    t = {}
    t["fungal_rock"] = tex_rock(P)
    t["fungal_rock_veined"], t["fungal_rock_veined_glow"] = tex_veined(P)
    t["fungal_loam"] = tex_loam(P)
    t["fungal_moss_top"], t["fungal_moss_top_glow"], t["fungal_moss"], t["fungal_moss_glow"] = tex_moss(P)
    t["glowcap_stem"], t["glowcap_stem_top"] = tex_stem(P)
    t["glowcap_cap"] = tex_cap(P)
    t["glowcap_gills"], t["glowcap_gills_glow"] = tex_gills(P)
    t["lumen_roots"], t["lumen_roots_glow"] = tex_roots(P)
    t["glowcap_sprouts"], t["glowcap_sprouts_glow"] = tex_sprouts(P)
    t["mycelium_threads"], t["mycelium_threads_glow"] = tex_threads(P)
    t["fungal_glowfall"] = tex_glowfall(P)
    t["spore_puffball"], t["spore_puffball_glow"] = tex_puff(P)
    t["spore_jar_glass"], t["spore_jar_core"] = tex_jar(P)
    return t


def build(out):
    for name, c in textures().items():
        out.block(name, c, 3 if isinstance(c, list) else None)


BLOCKS = {
    "fungal_rock": ("Hollow Rock", "pickaxe"),
    "fungal_rock_veined": ("Veined Hollow Rock", "pickaxe"),
    "fungal_loam": ("Hollow Loam", "shovel"),
    "fungal_moss": ("Glowing Moss", "hoe"),
    "glowcap_stem": ("Glowcap Stem", "axe"),
    "glowcap_cap": ("Glowcap Cap", "hoe"),
    "glowcap_gills": ("Glowcap Gills", "hoe"),
    "lumen_roots": ("Lumen Roots", None),
    "glowcap_sprouts": ("Glowcap Sprouts", None),
    "mycelium_threads": ("Mycelium Threads", "hoe"),
    "fungal_glowfall": ("Glowing Falls", None),
    "spore_puffball": ("Spore Puffball", "hoe"),
    "spore_jar": ("The Spore Jar", "pickaxe"),
}
FLAT = ("lumen_roots", "glowcap_sprouts")
B = K.B


def models():
    m = {}
    m["fungal_rock"] = K.cube_model("fungal_rock")
    m["fungal_rock_veined"] = K.cube_model("fungal_rock_veined", glow=True)
    m["fungal_loam"] = K.cube_model("fungal_loam")
    m["fungal_moss"] = K.cube_model("fungal_moss", glow=True, top="fungal_moss_top", bottom="fungal_loam")
    m["glowcap_stem"] = K.column_model("glowcap_stem", "glowcap_stem_top")
    m["glowcap_cap"] = K.cube_model("glowcap_cap")
    m["glowcap_gills"] = K.cube_model("glowcap_gills", glow=True)
    m["lumen_roots"] = K.cross_model("lumen_roots", glow=True)
    m["glowcap_sprouts"] = K.cross_model("glowcap_sprouts", glow=True)
    m["mycelium_threads"] = K.carpet_model("mycelium_threads", glow=True)
    m["fungal_glowfall"] = K.fall_model("fungal_glowfall", 15)
    # the puffball: a squat round body (stacked boxes), a little crown, spores glowing in its warts when struck
    for lit in (False, True):
        els = [K.box([3, 0, 3], [13, 2, 13], "#puff"), K.box([2, 2, 2], [14, 8, 14], "#puff"),
               K.box([3, 8, 3], [13, 10, 13], "#puff"), K.box([5.5, 10, 5.5], [10.5, 11, 10.5], "#puff")]
        tex = {"particle": B + "spore_puffball", "puff": B + "spore_puffball"}
        if lit:
            tex["glow"] = B + "spore_puffball_glow"
            els += [K.box([1.98, 2, 1.98], [14.02, 8, 14.02], "#glow", 15, sides=("north", "south", "west", "east")),
                    K.box([2.98, 8, 2.98], [13.02, 10.02, 13.02], "#glow", 15, sides=("up", "north", "south", "west", "east"))]
        m["spore_puffball" + ("_lit" if lit else "")] = {"parent": "minecraft:block/block", "textures": tex, "elements": els}
    # the Spore Jar: a glass jar of glowing spores under a cap lid
    m["spore_jar"] = {"parent": "minecraft:block/block", "ambientocclusion": False,
                      "textures": {"particle": B + "spore_jar_glass", "core": B + "spore_jar_core", "lid": B + "glowcap_cap",
                                   "glass": {"sprite": B + "spore_jar_glass", "force_translucent": True}},
                      "elements": [K.box([5, 1, 5], [11, 10, 11], "#core", 15),
                                   K.box([3, 0, 3], [13, 11, 13], "#glass"),
                                   K.box([4, 11, 4], [12, 12, 12], "#lid"), K.box([3.5, 12, 3.5], [12.5, 14, 12.5], "#lid")]}
    return m


def states():
    s = {n: K.simple_state(n) for n in BLOCKS if n not in ("glowcap_stem", "spore_puffball")}
    s["glowcap_stem"] = K.axis_state("glowcap_stem")
    s["spore_puffball"] = K.lit_state("spore_puffball")
    return s


def content():
    K.content("gate_worlds_fungal", BLOCKS, models(), states(), {}, climbable=("lumen_roots",), flat_items=FLAT)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/gate_worlds_fungal.png")
    else:
        print(__doc__)
