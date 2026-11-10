"""Olden stone (SPEC 61): the masonry of the old world's monuments. One family, drawn by code, nothing copied.

    python3 tools/art/build_all.py finds_olden        textures
    python3 tools/art/specs/finds_olden.py content    blockstates, models, item definitions, loot, lang, tags
    python3 tools/art/specs/finds_olden.py sheet PATH every texture at 6x, named

Pale weathered ashlar in three drawings, its worn and its dark damp twins, dressed blocks, a carved band, a carved panel, an
inscribed panel, flags and a mosaic for floors, rubble, a fluted pillar and its capital, verdigris bronze, a bronze band on
stone, a studded door plate, a lamp (dark and lit), a seal inlay that glows teal, glowing lichen. Same table as
dev.awaken.finds.kit.Olden.
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import _gw_kit as K  # noqa: E402
import gate_depth as D  # noqa: E402

B = K.B
RAMPS = {
    "estone": ["#564f44", "#6b6457", "#81796a", "#978f7e", "#ada593", "#c4bda9"],
    "edark": ["#2b2926", "#383531", "#46433d", "#55514a", "#656057", "#767165"],
    "emoss": ["#2a3819", "#384a21", "#485e2a", "#5a7435", "#6e8a42", "#84a152"],
    "ebronze": ["#1f332e", "#2a473e", "#385d50", "#4a7664", "#62917b", "#84b098"],
    "ebrass": ["#3a2a16", "#553c1c", "#705024", "#8c672f", "#a7803d", "#c29b52"],
    "egold": ["#7a5a1c", "#a67c26", "#d3a43a", "#f2cb5e", "#ffe796", "#fff7d6"],
    "eteal": ["#0f3a3c", "#165558", "#1f7a7c", "#34a6a4", "#6fd6cf", "#c2f5ee"],
}

# name -> (English, tool)
BLOCKS = {
    "olden_stone": ("Olden Stone", "pickaxe"),
    "olden_stone_worn": ("Worn Olden Stone", "pickaxe"),
    "olden_stone_mossy": ("Mossy Olden Stone", "pickaxe"),
    "olden_stone_dark": ("Dark Olden Stone", "pickaxe"),
    "olden_block": ("Dressed Olden Block", "pickaxe"),
    "olden_block_dark": ("Dark Dressed Olden Block", "pickaxe"),
    "olden_band": ("Carved Olden Band", "pickaxe"),
    "olden_panel": ("Carved Olden Panel", "pickaxe"),
    "olden_glyph": ("Inscribed Olden Panel", "pickaxe"),
    "olden_flag": ("Olden Flagstones", "pickaxe"),
    "olden_mosaic": ("Olden Mosaic", "pickaxe"),
    "olden_rubble": ("Olden Rubble", "pickaxe"),
    "olden_pillar": ("Fluted Olden Pillar", "pickaxe"),
    "olden_capital": ("Olden Capital", "pickaxe"),
    "olden_bronze": ("Verdigris Bronze", "pickaxe"),
    "olden_bronze_band": ("Bronze-banded Olden Stone", "pickaxe"),
    "olden_door": ("Olden Door Plate", "pickaxe"),
    "olden_lamp": ("Olden Lamp", "pickaxe"),
    "olden_seal": ("Olden Seal", "pickaxe"),
    "olden_lichen": ("Dreaming Lichen", "pickaxe"),
}
CUTS = [("olden_stone", "Olden Stone"), ("olden_stone_worn", "Worn Olden Stone"), ("olden_stone_dark", "Dark Olden Stone"), ("olden_block", "Dressed Olden Block")]
SLAB_ONLY = [("olden_flag", "Olden Flagstone")]
VARIED = {"olden_stone": 3, "olden_stone_worn": 2, "olden_stone_dark": 2}   # drawings a block


def _P():
    return K.px(RAMPS)


def ashlar(P, ramp, seed, pits=0, cracks=0):
    """Two courses of cut stone a block, joints staggered, each stone a shade of its own, the arris lit from above."""
    rp = P.ramp(ramp)
    rng = P.rng_for("olden-ashlar", ramp, seed)
    n = P.value_noise(16, 16, rng, scale=3, octaves=3)
    c = P.Canvas(16, 16)
    offs = [rng.randrange(3, 13), rng.randrange(3, 13)]
    for row in range(2):
        tone = [rng.choice((-1, 0, 0, 1)), rng.choice((-1, 0, 0, 1))]
        for y in range(row * 8, row * 8 + 8):
            for x in range(16):
                stone = 0 if (x - offs[row]) % 16 < 8 + row * 2 else 1
                v = 2 + tone[stone] + (n[y][x] - 0.5) * 2.6 + (rng.random() - 0.5) * 0.5
                c.set(x, y, rp[max(1, min(len(rp) - 1, int(round(v))))])
        y0 = row * 8
        for x in range(16):
            c.set(x, y0 + 7, rp[0])                       # bed joint
            c.blend(x, y0, rp[-1], 0.28)                  # lit arris
        for jx in (offs[row] % 16, (offs[row] + 8 + row * 2) % 16):
            for y in range(y0, y0 + 7):
                c.set(jx, y, rp[0])
                c.blend((jx + 1) % 16, y, rp[-1], 0.18)
    for _ in range(pits):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, rp[0])
        c.set((x + 1) % 16, y, rp[1])
        c.blend(x, (y + 1) % 16, rp[-1], 0.4)
    if cracks:
        K.lines(c, K.walk(P, "olden-crack" + seed, cracks, 8, turn=0.5), rp[0], rp[1])
    return c


def dressed(P, ramp, seed):
    """One big dressed stone: a soft face inside a bevel, light on the top and left, shadow on the bottom and right."""
    rp = P.ramp(ramp)
    c = K.noisy(P, ramp, "dressed" + seed, scale=4, lo=2, span=3, jitter=0.1)
    for i in range(16):
        c.set(i, 0, rp[5]); c.set(0, i, rp[4])
        c.set(i, 15, rp[0]); c.set(15, i, rp[1])
        c.blend(i, 1, rp[5], 0.35); c.blend(1, i, rp[4], 0.25)
        c.blend(i, 14, rp[0], 0.35); c.blend(14, i, rp[0], 0.25)
    return c


def band(P):
    """A course with a running key cut into it."""
    rp = P.ramp("estone")
    c = K.noisy(P, "estone", "band", scale=3, lo=2, span=3)
    for x in range(16):
        c.set(x, 3, rp[5]); c.set(x, 4, rp[0]); c.set(x, 11, rp[0]); c.set(x, 12, rp[5])
        c.set(x, 0, rp[1]); c.set(x, 15, rp[1])
    key = ["#.....#.", "#.###.#.", "#.#.#.#.", "#.#...#.", "#.#####.", "#......."]
    for y, row in enumerate(key):
        for x in range(16):
            if row[x % 8] == "#":
                c.set(x, 5 + y, rp[0])
                c.blend((x + 1) % 16, 5 + y, rp[5], 0.3)
    return c


def panel(P, glyphs=False):
    """A panel sunk a finger deep in its frame: a ring and a point in relief, or four lines of writing."""
    rp = P.ramp("estone")
    c = dressed(P, "estone", "panel" + str(glyphs))
    for y in range(2, 14):
        for x in range(2, 14):
            edge = x == 2 or y == 2
            c.set(x, y, rp[0] if edge else rp[1] if (x == 13 or y == 13) else rp[2 if (x + y) % 5 else 3])
            if x == 13 or y == 13:
                c.set(x, y, rp[4])
    if glyphs:
        rng = P.rng_for("olden-glyphs")
        for row in range(4):
            x = 4
            while x < 12:
                n = rng.choice((1, 1, 2, 3))
                for i in range(n):
                    if x + i < 12:
                        c.set(x + i, 4 + row * 2 + (row > 1), rp[5])
                if rng.random() < 0.5 and x < 11:
                    c.set(x, 5 + row * 2 + (row > 1), rp[4])
                x += n + 1
    else:
        for y in range(3, 13):
            for x in range(3, 13):
                d = math.hypot(x - 7.5, y - 7.5)
                if 3.0 <= d < 4.2:
                    c.set(x, y, rp[4] if y < 8 else rp[3])
                elif d < 1.3:
                    c.set(x, y, rp[5])
    return c


def mosaic(P):
    rp, bz, tl = P.ramp("estone"), P.ramp("ebrass"), P.ramp("eteal")
    rng = P.rng_for("olden-mosaic")
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            cx, cy = x // 2, y // 2
            d = max(abs(cx - 3.5), abs(cy - 3.5))
            r = rng_cell(P, cx, cy)
            col = rp[2 + (r % 3)]
            if d == 3.5:
                col = bz[2 + r % 2]
            elif d == 1.5:
                col = tl[1 + r % 2]
            elif d == 0.5:
                col = rp[5]
            c.set(x, y, col)
            if x % 2 == 0 or y % 2 == 0:
                c.blend(x, y, rp[0], 0.35)
    return c


def rng_cell(P, cx, cy):
    return P.rng_for("olden-cell", cx, cy).randrange(100)


def rubble(P):
    rp = P.ramp("estone")
    c = K.noisy(P, "estone", "rubble", scale=2, octaves=3, lo=1, span=4, jitter=0.25)
    for path in K.walk(P, "olden-rubble", 7, 9, turn=0.9):
        for (x, y) in path:
            c.set(x, y, rp[0])
            c.blend((x + 1) % 16, (y + 1) % 16, rp[5], 0.25)
    return c


def pillar(P):
    """Flutes: four to a face, a lit ridge and a deep groove."""
    rp = P.ramp("estone")
    rng = P.rng_for("olden-pillar")
    c = P.Canvas(16, 16)
    prof = [1, 2, 4, 5]
    for y in range(16):
        for x in range(16):
            v = prof[x % 4] + (rng.random() < 0.12) * -1
            c.set(x, y, rp[max(0, min(5, v))])
    for _ in range(5):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set(x, y, rp[0])
    return c


def pillar_end(P):
    rp = P.ramp("estone")
    c = K.noisy(P, "estone", "pillar-end", scale=4, lo=2, span=2)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 6.2 <= d < 7.4:
                c.set(x, y, rp[0])
            elif 5.0 <= d < 6.2:
                c.set(x, y, rp[4])
    return c


def capital(P):
    rp = P.ramp("estone")
    c = K.noisy(P, "estone", "capital", scale=4, lo=2, span=2)
    rows = {0: 5, 1: 4, 2: 0, 3: 4, 4: 3, 5: 0, 10: 0, 11: 3, 12: 4, 13: 0, 14: 4, 15: 1}
    for y, v in rows.items():
        for x in range(16):
            c.set(x, y, rp[v])
    for x in range(1, 16, 4):
        for y in range(6, 10):
            c.set(x, y, rp[0]); c.set(x + 1, y, rp[5])
    return c


def bronze(P, studs=False):
    rp, br = P.ramp("ebronze"), P.ramp("ebrass")
    rng = P.rng_for("olden-bronze", studs)
    c = K.noisy(P, "ebronze", "bronze" + str(studs), scale=3, lo=1, span=4, jitter=0.2)
    for _ in range(26):                                   # where the green has not taken yet
        x, y = rng.randrange(16), rng.randrange(16)
        c.blend(x, y, br[2 + rng.randrange(3)], 0.7)
    for path in K.walk(P, "olden-drip" + str(studs), 4, 6, turn=0.15):
        for (x, y) in path:
            c.blend(x, y, rp[5], 0.45)
    for i in range(16):
        c.set(i, 0, rp[5]); c.set(i, 15, rp[0]); c.set(0, i, rp[4]); c.set(15, i, rp[0])
    if studs:
        for sx, sy in ((3, 3), (12, 3), (3, 12), (12, 12), (7, 7)):
            c.set(sx, sy, br[5]); c.set(sx + 1, sy, br[4]); c.set(sx, sy + 1, br[3]); c.set(sx + 1, sy + 1, br[1])
        for i in range(2, 14):
            c.blend(i, 8, rp[0], 0.5)
    return c


def bronze_band(P):
    c = ashlar(P, "estone", "bband")
    rp, br = P.ramp("ebronze"), P.ramp("ebrass")
    for x in range(16):
        c.set(x, 5, rp[5]); c.set(x, 6, rp[3]); c.set(x, 7, rp[2]); c.set(x, 8, rp[3]); c.set(x, 9, rp[1]); c.set(x, 10, P.ramp("estone")[0])
    for x in (2, 10):
        c.set(x, 7, br[5]); c.set(x + 1, 7, br[3])
    return c


def lamp(P, lit):
    """A stone housing with a core of old glass: dark, or burning pale gold. Returns (texture, glow layer or None)."""
    rp, gd, dk = P.ramp("estone"), P.ramp("egold"), P.ramp("edark")
    c = dressed(P, "estone", "lamp")
    g = P.Canvas(16, 16)
    for y in range(3, 13):
        for x in range(3, 13):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d > 4:
                c.set(x, y, rp[0])
            elif lit:
                col = gd[5] if d < 1.6 else gd[4] if d < 2.6 else gd[3] if d < 3.6 else gd[2]
                c.set(x, y, col); g.set(x, y, col)
            else:
                c.set(x, y, dk[1 if d < 2.6 else 2 if (x + y) % 3 else 0])
    for i in range(3, 13):
        c.set(7, i, rp[1] if not lit else gd[1]); c.set(i, 7, rp[1] if not lit else gd[1])
        if lit:
            g.set(7, i, (0, 0, 0, 0)); g.set(i, 7, (0, 0, 0, 0))
    return c, (g if lit else None)


def seal(P):
    """Dark bronze with two rings of teal that never went out."""
    rp, tl = P.ramp("ebronze"), P.ramp("eteal")
    c = bronze(P)
    g = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.blend(x, y, P.ramp("edark")[0], 0.45)
            d = math.hypot(x - 7.5, y - 7.5)
            if 5.2 <= d < 6.3 or 2.4 <= d < 3.3:
                col = tl[4] if (x + y) % 4 else tl[5]
                c.set(x, y, col); g.set(x, y, col)
            elif 6.3 <= d < 7.0 or 3.3 <= d < 3.9:
                c.set(x, y, tl[1])
    return c, g


def lichen(P):
    dk, tl = P.ramp("edark"), P.ramp("eteal")
    c = ashlar(P, "edark", "lichen")
    g = P.Canvas(16, 16)
    rng = P.rng_for("olden-lichen")
    n = P.value_noise(16, 16, rng, scale=3, octaves=2)
    for y in range(16):
        for x in range(16):
            if n[y][x] > 0.68 and (x + y * 3) % 5:          # speckled, the way lichen sits, never a sheet of it
                col = tl[3] if n[y][x] < 0.74 else tl[4] if n[y][x] < 0.82 else tl[5]
                c.set(x, y, col); g.set(x, y, col)
            elif n[y][x] > 0.63:
                c.set(x, y, tl[1])
    return c, g


def mossy(P):
    """Moss that came down from the top and settled in the joints."""
    ms = P.ramp("emoss")
    c = ashlar(P, "estone", "mossy", pits=3)
    rng = P.rng_for("olden-moss")
    n = P.value_noise(16, 16, rng, scale=3, octaves=2)
    for y in range(16):
        for x in range(16):
            reach = 0.78 - y * 0.03 + (0.12 if y % 8 == 7 else 0)
            if n[y][x] < reach - 0.3:
                c.set(x, y, ms[2 + (rng.random() < 0.4) + (rng.random() < 0.15)])
            elif n[y][x] < reach - 0.22:
                c.set(x, y, ms[1])
    return c


def textures():
    P = _P()
    t = {}
    for name, ramp, kw in (("olden_stone", "estone", {}), ("olden_stone_worn", "estone", {"pits": 9, "cracks": 2}), ("olden_stone_dark", "edark", {"pits": 2})):
        for i in range(VARIED[name]):
            t[name + ("" if i == 0 else "_" + "abc"[i])] = ashlar(P, ramp, name + str(i), **kw)
    t["olden_stone_mossy"] = mossy(P)
    t["olden_moss_top"] = K.noisy(P, "emoss", "moss-top", scale=2, lo=1, span=4, jitter=0.3)
    t["olden_block"] = dressed(P, "estone", "a")
    t["olden_block_dark"] = dressed(P, "edark", "a")
    t["olden_band"] = band(P)
    t["olden_panel"] = panel(P)
    t["olden_glyph"] = panel(P, glyphs=True)
    t["olden_flag"] = K.tiles(P, "estone", "olden-flag", size=8)
    t["olden_mosaic"] = mosaic(P)
    t["olden_rubble"] = rubble(P)
    t["olden_pillar"] = pillar(P)
    t["olden_pillar_end"] = pillar_end(P)
    t["olden_capital"] = capital(P)
    t["olden_bronze"] = bronze(P)
    t["olden_bronze_band"] = bronze_band(P)
    t["olden_door"] = bronze(P, studs=True)
    t["olden_lamp"], _ = lamp(P, False)
    t["olden_lamp_lit"], t["olden_lamp_lit_glow"] = lamp(P, True)
    t["olden_seal"], t["olden_seal_glow"] = seal(P)
    t["olden_lichen"], t["olden_lichen_glow"] = lichen(P)
    return t


# ---- the relics ---------------------------------------------------------------------------------------------------------

def relics():
    P = _P()
    bz, br, st, tl, gd, dk = (P.ramp(r) for r in ("ebronze", "ebrass", "estone", "eteal", "egold", "edark"))
    o = (20, 22, 20, 255)
    pal_w = {"o": o, "a": bz[1], "b": bz[2], "c": bz[3], "d": bz[5], "r": br[4], "s": br[2], "k": dk[1]}
    weight = ["................", "......oooo......", ".....orssro.....", ".....os..so.....", ".....orssro.....", "......okko......",
              "......okko......", ".....oobboo.....", "....oddccbao....", "....odccbbao....", "...odccrbbaao...", "...odccbbbaao...",
              "..odccbbbbaaao..", "..odcbbbrbaaao..", "..oaaaaaaaaaao..", "...oooooooooo..."]
    pal_s = {"o": o, "a": st[1], "b": st[2], "c": st[3], "d": st[5], "t": tl[4], "u": tl[2], "k": st[0]}
    stone = ["................", "................", ".....oooooo.....", "...oodddccboo...", "..oddccccccbao..", ".odccttttccbbao.",
             ".odcctuuuutcbao.", ".occtu.kk.utbao.", ".occtu.kk.utbao.", ".obctuuuuutbbao.", ".obbcttttbbbaao.", "..obbbbbbbaaao..",
             "...oabbbaaaoo...", ".....oooooo.....", "................", "................"]
    pal_l = {"o": o, "a": bz[1], "b": bz[3], "c": bz[5], "g": gd[3], "h": gd[4], "i": gd[5], "r": br[4]}
    lamp_ = ["................", ".......oo.......", "......orro......", "......o..o......", ".....oobboo.....", "....occccbao....",
             "....obggggao....", "....obghhgao....", "....obhiihao....", "....obhiihao....", "....obghhgao....", "....obggggao....",
             "....obbbbaao....", "...oaaaaaaaao...", "...oooooooooo...", "................"]
    return {"relic_counterweight": K.sprite(weight, pal_w), "relic_listening_stone": K.sprite(stone, pal_s), "relic_sleepers_lamp": K.sprite(lamp_, pal_l)}


RELICS = {"relic_counterweight": "The Counterweight", "relic_listening_stone": "The Listening Stone", "relic_sleepers_lamp": "The Sleeper's Lamp"}
# a monument's table: its relic, then what was left with it (item, least, most)
SPOILS = {
    "relic_stair": ("relic_counterweight", [("minecraft:gold_ingot", 4, 7), ("minecraft:emerald", 2, 4), ("minecraft:ender_pearl", 1, 2), ("awaken:olden_bronze", 3, 6)]),
    "relic_dish": ("relic_listening_stone", [("minecraft:amethyst_shard", 5, 9), ("minecraft:gold_ingot", 3, 6), ("minecraft:spyglass", 1, 1), ("awaken:olden_bronze", 3, 6)]),
    "relic_hall": ("relic_sleepers_lamp", [("minecraft:glow_berries", 6, 10), ("minecraft:diamond", 1, 2), ("minecraft:gold_ingot", 3, 5), ("awaken:olden_mosaic", 3, 6)]),
}


def build(out):
    for name, c in textures().items():
        out.block(name, c)
    for name, c in relics().items():
        out.item(name, c)


# ---- models, states, the rest -------------------------------------------------------------------------------------------

def models():
    m = {}
    for n in ("olden_block", "olden_block_dark", "olden_band", "olden_panel", "olden_glyph", "olden_flag", "olden_mosaic", "olden_rubble", "olden_bronze",
              "olden_bronze_band", "olden_door", "olden_lamp"):
        m[n] = K.cube_model(n)
    for name, count in VARIED.items():
        for i in range(count):
            n = name + ("" if i == 0 else "_" + "abc"[i])
            m[n] = K.cube_model(n)
    m["olden_stone_mossy"] = K.cube_model("olden_stone_mossy", top="olden_moss_top", bottom="olden_stone")
    m["olden_pillar"] = K.column_model("olden_pillar", "olden_pillar_end")
    m["olden_capital"] = K.cube_model("olden_capital", top="olden_block", bottom="olden_block")
    m["olden_lamp_lit"] = K.cube_model("olden_lamp_lit", glow=True)
    m["olden_seal"] = K.cube_model("olden_seal", glow=True)
    m["olden_lichen"] = K.cube_model("olden_lichen", glow=True)
    return m


def states():
    s = {}
    for n in BLOCKS:
        if n in VARIED:
            s[n] = {"variants": {"": [{"model": B + n + ("" if i == 0 else "_" + "abc"[i])} for i in range(VARIED[n])]}}
        elif n == "olden_pillar":
            s[n] = K.axis_state(n)
        elif n == "olden_lamp":
            s[n] = K.lit_state(n)
        else:
            s[n] = K.simple_state(n)
    return s


MERGE = os.path.join(K.MERGE, "finds")


def content():
    """The same files K.content writes, into the finds module's own merge folder."""
    ms, ss = models(), states()
    cut = {}
    for base, en in CUTS + SLAB_ONLY:
        t = B + base
        tex = {"bottom": t, "top": t, "side": t}
        cut[base + "_slab"] = en + " Slab"
        ms[base + "_slab"] = {"parent": "minecraft:block/slab", "textures": tex}
        ms[base + "_slab_top"] = {"parent": "minecraft:block/slab_top", "textures": tex}
        ss[base + "_slab"] = {"variants": {"type=bottom": {"model": B + base + "_slab"}, "type=top": {"model": B + base + "_slab_top"}, "type=double": {"model": t}}}
        if (base, en) in SLAB_ONLY:
            continue
        cut[base + "_stairs"] = en + " Stairs"
        ms[base + "_stairs"] = {"parent": "minecraft:block/stairs", "textures": tex}
        ms[base + "_stairs_inner"] = {"parent": "minecraft:block/inner_stairs", "textures": tex}
        ms[base + "_stairs_outer"] = {"parent": "minecraft:block/outer_stairs", "textures": tex}
        ss[base + "_stairs"] = D.stairs_state(base + "_stairs")
    # the one wall
    w = {"wall": B + "olden_stone"}
    ms["olden_stone_wall_post"] = {"parent": "minecraft:block/template_wall_post", "textures": w}
    ms["olden_stone_wall_side"] = {"parent": "minecraft:block/template_wall_side", "textures": w}
    ms["olden_stone_wall_side_tall"] = {"parent": "minecraft:block/template_wall_side_tall", "textures": w}
    ms["olden_stone_wall_inventory"] = {"parent": "minecraft:block/wall_inventory", "textures": w}
    multi = [{"when": {"up": "true"}, "apply": {"model": B + "olden_stone_wall_post"}}]
    for side, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for h, mdl in (("low", "olden_stone_wall_side"), ("tall", "olden_stone_wall_side_tall")):
            a = {"model": B + mdl, "uvlock": True}
            if y:
                a["y"] = y
            multi.append({"when": {side: h}, "apply": a})
    ss["olden_stone_wall"] = {"multipart": multi}
    cut["olden_stone_wall"] = "Olden Stone Wall"

    for name, data in ms.items():
        K.write(os.path.join(K.ASSETS, f"models/block/{name}.json"), data)
    for name, data in ss.items():
        K.write(os.path.join(K.ASSETS, f"blockstates/{name}.json"), data)
    every = {**{k: v[0] for k, v in BLOCKS.items()}, **cut}
    for name in every:
        ref = B + ("olden_stone_wall_inventory" if name == "olden_stone_wall" else name)
        K.write(os.path.join(K.ASSETS, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": ref}})
        if name.endswith("_slab"):
            D.slab_loot(name)
        else:
            K.loot(name)
    K.merge(os.path.join(MERGE, "assets/awaken/lang/en_us.json"), {f"block.awaken.{k}": v for k, v in every.items()})
    ids = [f"awaken:{k}" for k in every]
    K.merge(os.path.join(MERGE, "data/minecraft/tags/block/mineable/pickaxe.json"), {"values": ids})
    K.merge(os.path.join(MERGE, "data/minecraft/tags/block/blocks_motion_no_leaves.json"), {"values": ids})
    for kind, tag in (("_slab", "slabs"), ("_stairs", "stairs"), ("_wall", "walls")):
        vals = [i for i in ids if i.endswith(kind)]
        K.merge(os.path.join(MERGE, f"data/minecraft/tags/block/{tag}.json"), {"values": vals})
        K.merge(os.path.join(MERGE, f"data/minecraft/tags/item/{tag}.json"), {"values": vals})
    for name, en in RELICS.items():
        K.write(os.path.join(K.ASSETS, f"models/item/{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/" + name}})
        K.write(os.path.join(K.ASSETS, f"items/{name}.json"), {"model": {"type": "minecraft:model", "model": "awaken:item/" + name}})
    K.merge(os.path.join(MERGE, "assets/awaken/lang/en_us.json"), {f"item.awaken.{k}": v for k, v in RELICS.items()})
    for name, (relic, rest) in SPOILS.items():
        pools = [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "awaken:" + relic}]}]
        for item, lo, hi in rest:
            e = {"type": "minecraft:item", "name": item}
            if hi > 1:
                e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
            pools.append({"rolls": 1, "entries": [e]})
        K.write(os.path.join(K.RES, f"data/awaken/loot_table/finds/{name}.json"), {"type": "minecraft:chest", "random_sequence": "awaken:finds/" + name, "pools": pools})
    print(f"finds_olden: content written for {len(every)} blocks, {len(RELICS)} relics")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        K.sheet(textures(), sys.argv[2] if len(sys.argv) > 2 else "/tmp/finds_olden.png")
    else:
        print(__doc__)
