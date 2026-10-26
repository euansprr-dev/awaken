"""The deep (delve module, SPEC §28.3): Deepglass and its shard, the nine lode hearts, raw starmetal and its ingot, bell
bronze, the Tuning Fork, the Runner's piton and rope, the miner's lamp (icon and the band it wears on the head), and the
deep's own particles (sparks, grit, dust, glints, motes). Pixel art in the mod's palette like every texture; the game
tints sparks, grit, dust and glints per ore or rock.

    python3 tools/art/build_all.py delve        textures
    python3 tools/art/specs/delve.py content    blockstates, models, item models and definitions, the lamp's equipment
                                                asset, loot tables, recipes, tags, worldgen (lodes, Deepglass veins and
                                                their biome modifier), particles, lang, the fallen star's starmetal
    python3 tools/art/specs/delve.py preview DIR   a contact sheet of every texture, large

The tier metals (#awaken:tier_metal_<d|c|b|a|s>, PLAN 7.1) are written here too: the gear and Arms recipes read them.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import pixelart as P  # noqa: E402
from pixelart import Canvas, ramp, rng_for, value_noise  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/awaken")
DATA = os.path.join(RES, "data/awaken")
MERGE = os.path.join(ROOT, "src/main/merge/delve")

P.RAMPS.update({
    # the deep's rock: deepslate's cold grey-black, a little blue in it
    "deeprock": ["#121318", "#1a1c22", "#23262e", "#2e323b", "#3b3f4a", "#4a4f5b"],
    # Deepglass: black glass that holds a violet light
    "deepglass": ["#07050c", "#120a20", "#24133f", "#3e2470", "#6a45b0", "#b48cff", "#eadcff"],
    "starmetal": ["#0d1220", "#1e2a44", "#36507a", "#5f86b8", "#a6c8ee", "#eaf4ff"],
    "bronze": ["#2e1a0c", "#5a3314", "#8a5424", "#b87a3a", "#dca460", "#f6d29a"],
    "hemp": ["#3a2c18", "#5c4626", "#7e6336", "#a2844e", "#c4a870", "#e2cc9a"],
    "iron": ["#1c1e22", "#2e3238", "#474c55", "#68707a", "#9aa2ab", "#d2d8de"],
    "leather": ["#24160c", "#3a2414", "#54361e", "#704a2a", "#8c6038"],
})

ORES = ["coal", "copper", "iron", "gold", "redstone", "lapis", "emerald", "diamond", "deepglass"]
ORE_TITLE = {o: o.capitalize() for o in ORES}
# each heart's crystal ramp, dark to white-hot (the colours of Ore Sense's glass, deepened)
HEART = {
    "coal": ["#0c0d10", "#1b1d22", "#3a3f48", "#6c7380", "#a8b0bc", "#e6ecf4"],
    "copper": ["#2a1006", "#5a2a14", "#9a4a24", "#d8743e", "#ffb07a", "#ffe4c8"],
    "iron": ["#2a1e16", "#5a4636", "#8e7258", "#b89a80", "#ead2bc", "#fff6ec"],
    "gold": ["#3a2604", "#6e4a0a", "#b07c14", "#e2ac2a", "#ffe27a", "#fff8d8"],
    "redstone": ["#280404", "#5a0a0a", "#9a1414", "#e02a2a", "#ff7a6a", "#ffd8d0"],
    "lapis": ["#060c2e", "#0f1e64", "#2240a8", "#4a6ee0", "#9ab4ff", "#e2eaff"],
    "emerald": ["#022412", "#06401c", "#108a40", "#2cc866", "#8affb4", "#e8fff0"],
    "diamond": ["#04242a", "#0c4048", "#1a8090", "#3cc8d8", "#9ff8ff", "#ffffff"],
    "deepglass": ["#07050c", "#24133f", "#3e2470", "#6a45b0", "#b48cff", "#f4ecff"],
}
# which tool tier each heart takes (its ore's)
HEART_TOOL = {"coal": None, "copper": "stone", "iron": "stone", "lapis": "stone", "gold": "iron", "redstone": "iron",
              "emerald": "iron", "diamond": "iron", "deepglass": "diamond"}
# a heart's lump: the item and how many (before Fortune)
HEART_DROP = {"coal": ("minecraft:coal", 6, 10), "copper": ("minecraft:raw_copper", 6, 10), "iron": ("minecraft:raw_iron", 4, 7),
              "gold": ("minecraft:raw_gold", 3, 6), "redstone": ("minecraft:redstone", 10, 16), "lapis": ("minecraft:lapis_lazuli", 10, 16),
              "emerald": ("minecraft:emerald", 2, 4), "diamond": ("minecraft:diamond", 2, 4), "deepglass": ("awaken:deepglass_shard", 4, 7)}


def hx(h, a=255):
    return P.hex_rgba(h, a)


def canvas(fill, w=16, h=16):
    c = Canvas(w, h)
    for y in range(h):
        for x in range(w):
            v = fill(x, y)
            if v is not None:
                c.set(x, y, v)
    return c


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(4))


# ---- rock and crystal ------------------------------------------------------------------------------------------------

def rock(seed, rp="deeprock", lo=1, hi=4):
    """Deep rock: layered value noise, a few strata lines (seams follow the strata)."""
    r = ramp(rp)
    n = value_noise(16, 16, rng_for("rock", seed), scale=4, octaves=3)

    def f(x, y):
        v = n[y][x]
        strata = 0.12 if (y * 3 + int(n[(y + 5) % 16][x] * 6)) % 7 == 0 else 0.0
        i = lo + int((v - strata) * (hi - lo + 1))
        return r[max(lo - 1, min(hi, i))]
    return canvas(f)


def facets(c, cols, seed, count, cx=7.5, cy=7.5, spread=6.5, length=(3, 6), core=True):
    """Crystal facets radiating from (cx, cy): each a bright edge on a dark body, the tip lit."""
    rnd = rng_for("facets", seed)
    for k in range(count):
        ang = (k / count) * math.tau + rnd.uniform(-0.35, 0.35)
        ln = rnd.uniform(*length)
        ox = cx + math.cos(ang) * rnd.uniform(0, spread * 0.35)
        oy = cy + math.sin(ang) * rnd.uniform(0, spread * 0.35)
        for s in range(int(ln * 4)):
            t = s / (ln * 4)
            x = ox + math.cos(ang) * ln * t
            y = oy + math.sin(ang) * ln * t
            w = (1 - t) * 1.6 + 0.4
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    px, py = int(round(x + dx * w * 0.5)), int(round(y + dy * w * 0.5))
                    if not (0 <= px < 16 and 0 <= py < 16):
                        continue
                    lit = (dx - dy) > 0
                    i = 2 + int(t * 2) + (1 if lit else 0)
                    c.set(px, py, cols[min(len(cols) - 2, i)])
        tip = (int(round(ox + math.cos(ang) * ln)), int(round(oy + math.sin(ang) * ln)))
        c.set(*tip, cols[-2])
    if core:
        for (dx, dy, i) in ((0, 0, -1), (1, 0, -2), (0, 1, -2), (-1, 0, -3), (0, -1, -3), (1, 1, -3)):
            c.set(int(cx) + dx, int(cy) + dy, cols[i])
    return c


def deepglass():
    """Deepglass: black glass in deep rock, its facets dark inside and lit violet only at their edges and tips."""
    g = [hx(h) for h in P.RAMPS["deepglass"]]
    dark = [g[0], g[1], g[1], g[2], g[3], g[5], g[6]]
    c = rock("deepglass", lo=1, hi=3)
    rnd = rng_for("deepglass_body")
    for (cx, cy, n) in ((5.0, 5.5, 4), (11.0, 9.0, 5), (5.5, 12.0, 3)):
        facets(c, dark, f"dg{cx}{cy}", n, cx, cy, spread=4, length=(2.0, 3.8), core=False)
    for _ in range(5):
        x, y = rnd.randrange(16), rnd.randrange(16)
        if c.get(x, y)[2] > 50:
            c.set(x, y, g[6])
    return c


def heart(ore):
    """A lode's heart: a crystallised knot of its ore bursting out of deep rock, veins of it running to every edge (so
    the seam reads as one thing through the heart), a white-hot core."""
    cols = [hx(h) for h in HEART[ore]]
    c = rock("heart_" + ore, lo=1, hi=3)
    rnd = rng_for("heart_veins", ore)
    # veins to the edges
    for edge in range(4):
        x, y = 7.5, 7.5
        tx, ty = [(rnd.uniform(3, 12), -1), (16, rnd.uniform(3, 12)), (rnd.uniform(3, 12), 16), (-1, rnd.uniform(3, 12))][edge]
        steps = 14
        for s in range(steps):
            t = s / steps
            px = int(round(x + (tx - x) * t + math.sin(t * 6 + edge) * 0.8))
            py = int(round(y + (ty - y) * t + math.cos(t * 5 + edge) * 0.8))
            c.set(px, py, cols[2 if t > 0.5 else 3])
    facets(c, cols, "heart_" + ore, 7, spread=6, length=(3, 5.5))
    return c


def heart_frames(ore):
    """Its slow pulse: the core and facet tips breathe brighter and back (the block's animation)."""
    base = heart(ore)
    cols = [hx(h) for h in HEART[ore]]
    frames = []
    for k in range(4):
        f = base.copy()
        glow = [0.0, 0.45, 0.8, 0.45][k]
        for y in range(16):
            for x in range(16):
                p = f.get(x, y)
                if p[3] and sum(p[:3]) > sum(cols[3][:3]):
                    f.set(x, y, lerp(p, cols[-1], glow * 0.6))
        frames.append(f)
    return frames


# ---- items -----------------------------------------------------------------------------------------------------------

def outline(c):
    P._outline(c)
    return c


def shard_icon(rp, seed):
    g = [hx(h) for h in P.RAMPS[rp]]
    c = Canvas()
    # a long splinter of glass, corner to corner, with a smaller one beside it
    for (x0, y0, x1, y1, w) in ((4, 13, 12, 3, 2.3), (8, 14, 13, 8, 1.4)):
        n = 40
        for i in range(n + 1):
            t = i / n
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            ww = w * (1 - abs(t - 0.45) * 1.2)
            for dx in range(-2, 3):
                for dy in range(-2, 3):
                    if math.hypot(dx, dy) <= ww:
                        px, py = int(round(x + dx * 0.6)), int(round(y + dy * 0.6))
                        lit = dx - dy > 0
                        c.set(px, py, g[5 if lit else 3 if t > 0.3 else 2])
        c.set(int(round(x1)), int(round(y1)), g[6])
    return outline(c)


def nugget_icon(rp, seed, specks):
    g = [hx(h) for h in P.RAMPS[rp]]
    c = Canvas()
    rnd = rng_for("nugget", seed)
    n = value_noise(16, 16, rnd, scale=3, octaves=2)
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 5.6, (y - 8.2) / 4.6) + (n[y][x] - 0.5) * 0.5
            if d < 1:
                i = 1 + int((1 - d) * 2.4 + (1 if (x - y) < -2 else 0))
                c.set(x, y, g[min(4, i)])
    for _ in range(specks):
        x, y = rnd.randrange(4, 12), rnd.randrange(5, 12)
        if c.get(x, y)[3]:
            c.set(x, y, g[5])
    return outline(c)


def ingot_icon(rp, accent=None):
    g = [hx(h) for h in P.RAMPS[rp]]
    c = Canvas()
    # vanilla's ingot read: a long bar seen from above at an angle, its top face lit
    for y in range(16):
        for x in range(16):
            u, v = x - 7.5, y - 8.5
            a = (u * 0.8 + v * 0.6)
            b = (-u * 0.6 + v * 0.8)
            if abs(a) < 6.2 and abs(b) < 2.6:
                top = b < -0.2
                i = 4 if top else 2
                if abs(a) > 5.4:
                    i -= 1
                c.set(x, y, g[i])
    for (x, y) in ((5, 8), (6, 7), (7, 7), (8, 6)):
        c.set(x, y, g[5])
    if accent:
        ac = [hx(h) for h in P.RAMPS[accent]]
        for (x, y) in ((9, 8), (10, 9), (7, 10)):
            c.set(x, y, ac[4])
    return outline(c)


def starmetal_ingot():
    c = ingot_icon("starmetal")
    sm = [hx(h) for h in P.RAMPS["starmetal"]]
    for (x, y) in ((4, 9), (9, 6), (11, 9)):
        c.set(x, y, sm[5])
    return c


def tuning_fork():
    """A tuning fork, held at a slant: a copper stem, two long tines side by side, amethyst at their tips."""
    cu = [hx(h) for h in P.RAMPS["bronze"]]
    am = [hx(h) for h in P.RAMPS["deepglass"]]
    rows = [
        "...........A..A.",
        "..........aT.aT.",
        ".........Tt.Tt..",
        "........Tt.Tt...",
        ".......Tt.Tt....",
        "......Tt.Tt.....",
        ".....Tt.Tt......",
        ".....TTTt.......",
        "....TTtt........",
        "...Ss...........",
        "..Ss............",
        ".Ss.............",
        "Ss..............",
        "s...............",
        "................",
        "................",
    ]
    pal = {"T": cu[4], "t": cu[2], "S": cu[3], "s": cu[1], "A": am[6], "a": am[5]}
    c = Canvas()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                c.set(x, y + 1, pal[ch])
    return outline(c)


def piton_icon():
    ir = [hx(h) for h in P.RAMPS["iron"]]
    c = Canvas()
    # a spike, point down-left, a ring at its head
    for i in range(9):
        x, y = 3 + i, 12 - i
        c.set(x, y, ir[4 if i > 2 else 3])
        c.set(x + 1, y, ir[2])
    for (dx, dy) in ((0, -2), (1, -2), (2, -1), (2, 0), (2, 1), (1, 2), (0, 2), (-1, 1), (-1, 0), (-1, -1)):
        c.set(12 + dx, 3 + dy, ir[5] if dy < 0 else ir[3])
    return outline(c)


def rope_icon():
    hp = [hx(h) for h in P.RAMPS["hemp"]]
    am = [hx(h) for h in P.RAMPS["amber"]] if "amber" in P.RAMPS else hp
    c = Canvas()
    # a coil: three loops, the Runners' amber tracer thread through it
    for loop, (cx, cy, r) in enumerate(((8, 7.5, 5.2), (8, 8.5, 4.0), (8, 9.5, 2.8))):
        for k in range(64):
            a = k / 64 * math.tau
            x, y = cx + math.cos(a) * r, cy + math.sin(a) * r * 0.62
            px, py = int(round(x)), int(round(y))
            lit = math.sin(a) < 0
            col = hp[4 if lit else 2]
            if (k + loop * 7) % 11 == 0:
                col = am[4]
            c.set(px, py, col)
    c.set(12, 12, hp[3])
    c.set(13, 13, hp[4])
    return outline(c)


def lamp_icon():
    br = [hx(h) for h in P.RAMPS["brass"]]
    lt = [hx(h) for h in P.RAMPS["leather"]]
    c = Canvas()
    # the strap round the back, the brass lamp at the front with its glass lit
    for k in range(48):
        a = k / 48 * math.tau
        x, y = 8 + math.cos(a) * 6, 9.5 + math.sin(a) * 3
        c.set(int(round(x)), int(round(y)), lt[3 if math.sin(a) < 0 else 2])
    for y in range(4, 11):
        for x in range(5, 11):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 3.6:
                c.set(x, y, br[4 if x < 8 else 3] if d > 2.2 else (255, 244, 214, 255) if d < 1.2 else (255, 214, 140, 255))
    c.set(6, 5, br[5])
    return outline(c)


def lamp_layer():
    """The lamp as worn (humanoid layer, 64 x 32): a leather band round the head, the brass lamp on the brow."""
    lt = [hx(h) for h in P.RAMPS["leather"]]
    br = [hx(h) for h in P.RAMPS["brass"]]
    c = Canvas(64, 32)
    # the head's four sides are u 0-32, v 8-16: a band two pixels high round them all
    for x in range(0, 32):
        c.set(x, 11, lt[3] if x % 4 else lt[2])
        c.set(x, 12, lt[2])
    # the buckle at the back
    c.set(27, 11, br[3])
    c.set(28, 11, br[4])
    # the lamp on the front face (u 8-16): a brass housing, its glass lit, a reflector rim
    for (x, y, col) in ((10, 9, br[3]), (11, 9, br[4]), (12, 9, br[4]), (13, 9, br[3]),
                        (10, 10, br[4]), (11, 10, (255, 236, 190, 255)), (12, 10, (255, 248, 226, 255)), (13, 10, br[3]),
                        (10, 11, br[3]), (11, 11, (255, 214, 140, 255)), (12, 11, (255, 236, 190, 255)), (13, 11, br[2]),
                        (11, 12, br[2]), (12, 12, br[2])):
        c.set(x, y, col)
    return c


def piton_block():
    ir = [hx(h) for h in P.RAMPS["iron"]]
    n = value_noise(16, 16, rng_for("piton"), scale=4)
    return canvas(lambda x, y: ir[2 + int(n[y][x] * 3)])


def rope_block():
    hp = [hx(h) for h in P.RAMPS["hemp"]]
    am = [hx(h) for h in P.RAMPS["amber"]] if "amber" in P.RAMPS else hp

    def f(x, y):
        # a braid: strands crossing every four rows, an amber tracer winding through
        strand = (x + y) % 4 < 2
        if (y * 2 + x) % 16 == 0:
            return am[4]
        return hp[4 if strand else 2] if x % 3 else hp[1]
    return canvas(f)


# ---- particles (greyscale: the game tints them) ------------------------------------------------------------------

def soft(size, r, hard=0.0, a_max=255):
    c = Canvas(size, size)
    m = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - m, y - m) / r
            if d < 1:
                a = int(a_max * ((1 - d) ** (1.4 - hard)))
                c.set(x, y, (255, 255, 255, a))
    return c


def spark_frames():
    """A white-hot fleck: a hard bright core in a short soft bloom, shrinking as it cools."""
    out = []
    for r, core in ((3.4, 2), (2.8, 2), (2.2, 1), (1.6, 1)):
        c = soft(8, r, hard=0.7)
        for dx in range(core):
            for dy in range(core):
                c.set(3 + dx, 3 + dy, (255, 255, 255, 255))
        out.append(c)
    return out


def grit_frames():
    out = []
    for k in range(4):
        rnd = rng_for("grit", k)
        c = Canvas(8, 8)
        for _ in range(3 + k % 2):
            x, y = rnd.randrange(2, 6), rnd.randrange(2, 6)
            v = rnd.randrange(170, 255)
            c.set(x, y, (v, v, v, 255))
            if rnd.random() < 0.6:
                c.set(x + 1, y, (v - 40, v - 40, v - 40, 255))
        out.append(c)
    return out


def dust_frames():
    return [soft(8, r, a_max=a) for r, a in ((3.8, 170), (3.4, 140), (2.8, 100))]


def glint_frames():
    out = []
    for arm in (1, 2, 3, 2):
        c = soft(9, 1.8, hard=0.4)
        for i in range(-arm, arm + 1):
            a = 255 - abs(i) * 50
            c.set(4 + i, 4, (255, 255, 255, a))
            c.set(4, 4 + i, (255, 255, 255, a))
        c.set(4, 4, (255, 255, 255, 255))
        out.append(c)
    return out


def mote_frames():
    return [soft(8, r, hard=0.2) for r in (2.2, 3.0, 3.6, 2.6)]


PARTICLES = {"delve_spark": spark_frames, "delve_grit": grit_frames, "delve_dust": dust_frames, "delve_glint": glint_frames,
             "delve_mote": mote_frames}


def build(out):
    out.block("deepglass", deepglass())
    for o in ORES:
        out.block(o + "_lode_heart", heart_frames(o), frametime=20)
    out.block("piton", piton_block())
    out.block("runner_line", rope_block())
    out.item("deepglass_shard", shard_icon("deepglass", "dg"))
    out.item("raw_starmetal", nugget_icon("starmetal", "raw_starmetal", 6))
    out.item("starmetal_ingot", starmetal_ingot())
    out.item("bell_bronze_ingot", ingot_icon("bronze", accent="deepglass"))
    out.item("tuning_fork", tuning_fork())
    out.item("piton", piton_icon())
    out.item("runner_rope", rope_icon())
    out.item("miners_lamp", lamp_icon())
    out._save("entity/equipment/humanoid/miners_lamp", lamp_layer())
    for name, fn in PARTICLES.items():
        for i, f in enumerate(fn()):
            out.particle(f"{name}_{i}", f)


# ---- content ---------------------------------------------------------------------------------------------------------

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def cube(name):
    write(os.path.join(ASSETS, "models/block", name + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": "awaken:block/" + name}})


def piton_models():
    """The piton: a spike out of the wall (north-facing model: the wall to the south), a ring at its head; roped, a knot
    under the ring and the rope's first length down to the cell's floor."""
    spike = {"from": [7.5, 8, 9], "to": [8.5, 9, 16], "faces": {d: {"texture": "#iron"} for d in ("north", "south", "east", "west", "up", "down")}}
    ring = [
        {"from": [7, 9, 7.5], "to": [9, 10.5, 8.5]},
        {"from": [7, 6.5, 7.5], "to": [9, 8, 8.5]},
        {"from": [6, 7, 7.5], "to": [7, 10, 8.5]},
        {"from": [9, 7, 7.5], "to": [10, 10, 8.5]},
    ]
    for r in ring:
        r["faces"] = {d: {"texture": "#iron"} for d in ("north", "south", "east", "west", "up", "down")}
    plate = {"from": [6.5, 7.5, 15.5], "to": [9.5, 10.5, 16], "faces": {d: {"texture": "#iron"} for d in ("north", "east", "west", "up", "down")}}
    base = {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": {"iron": "awaken:block/piton", "particle": "awaken:block/piton"},
            "elements": [spike, plate] + ring}
    write(os.path.join(ASSETS, "models/block/piton.json"), base)
    knot = {"from": [7, 5, 7], "to": [9, 7.5, 9], "faces": {d: {"texture": "#rope"} for d in ("north", "south", "east", "west", "up", "down")}}
    line = {"from": [7.25, 0, 7.25], "to": [8.75, 5, 8.75], "faces": {d: {"texture": "#rope"} for d in ("north", "south", "east", "west")}}
    roped = {"parent": "minecraft:block/block", "ambientocclusion": False,
             "textures": {"iron": "awaken:block/piton", "rope": "awaken:block/runner_line", "particle": "awaken:block/piton"},
             "elements": [spike, plate] + ring + [knot, line]}
    write(os.path.join(ASSETS, "models/block/piton_roped.json"), roped)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for r in ("false", "true"):
            v = {"model": "awaken:block/piton" + ("_roped" if r == "true" else "")}
            if y:
                v["y"] = y
            variants[f"facing={facing},roped={r}"] = v
    write(os.path.join(ASSETS, "blockstates/piton.json"), {"variants": variants})


def rope_model():
    faces = {d: {"texture": "#rope", "uv": [6, 0, 9, 16]} for d in ("north", "south", "east", "west")}
    write(os.path.join(ASSETS, "models/block/runner_line.json"), {
        "parent": "minecraft:block/block", "ambientocclusion": False,
        "textures": {"rope": "awaken:block/runner_line", "particle": "awaken:block/runner_line"},
        "elements": [{"from": [7.25, 0, 7.25], "to": [8.75, 16, 8.75], "faces": faces}]})
    write(os.path.join(ASSETS, "blockstates/runner_line.json"), {"variants": {"": {"model": "awaken:block/runner_line"}}})


def item_generated(name, texture=None):
    write(os.path.join(ASSETS, "models/item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/" + (texture or name)}})
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "awaken:item/" + name}})


def item_handheld(name):
    write(os.path.join(ASSETS, "models/item", name + ".json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": "awaken:item/" + name}})
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "awaken:item/" + name}})


def item_block(name):
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "awaken:block/" + name}})


def loot(name, pools):
    write(os.path.join(DATA, "loot_table/blocks", name + ".json"), {"type": "minecraft:block", "pools": pools, "random_sequence": "awaken:blocks/" + name})


SILK = {"type": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}


def ore_drop(item, lo, hi):
    return {"type": "minecraft:item", "name": item, "modifier": [
        {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False},
        {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
        {"type": "minecraft:explosion_decay"}]}


def recipes():
    r = os.path.join(DATA, "recipe")
    write(os.path.join(r, "starmetal_ingot_from_smelting.json"), {"type": "minecraft:smelting", "category": "misc", "cookingtime": 400,
          "experience": 2.0, "group": "starmetal_ingot", "ingredient": "awaken:raw_starmetal", "result": {"id": "awaken:starmetal_ingot"}})
    write(os.path.join(r, "starmetal_ingot_from_blasting.json"), {"type": "minecraft:blasting", "category": "misc", "cookingtime": 200,
          "experience": 2.0, "group": "starmetal_ingot", "ingredient": "awaken:raw_starmetal", "result": {"id": "awaken:starmetal_ingot"}})
    write(os.path.join(r, "bell_bronze_ingot.json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
          "ingredients": ["minecraft:copper_ingot", "minecraft:copper_ingot", "minecraft:copper_ingot", "minecraft:amethyst_shard"],
          "result": {"id": "awaken:bell_bronze_ingot", "count": 3}})
    # a bell, cast from bell bronze: vanilla never let you make one
    write(os.path.join(r, "bell_from_bell_bronze.json"), {"type": "minecraft:crafting_shaped", "category": "misc",
          "key": {"B": "awaken:bell_bronze_ingot", "/": "minecraft:stick"}, "pattern": [" / ", "BBB", "B B"], "result": {"id": "minecraft:bell"}})
    write(os.path.join(r, "tuning_fork.json"), {"type": "minecraft:crafting_shaped", "category": "equipment",
          "key": {"A": "minecraft:amethyst_shard", "C": "minecraft:copper_ingot"}, "pattern": ["A A", "C C", " C "], "result": {"id": "awaken:tuning_fork"}})
    write(os.path.join(r, "piton.json"), {"type": "minecraft:crafting_shaped", "category": "equipment",
          "key": {"I": "minecraft:iron_ingot", "N": "minecraft:iron_nugget"}, "pattern": ["N", "I", "I"], "result": {"id": "awaken:piton", "count": 4}})
    write(os.path.join(r, "runner_rope.json"), {"type": "minecraft:crafting_shaped", "category": "equipment",
          "key": {"S": "minecraft:string", "N": "minecraft:iron_nugget"}, "pattern": ["SSS", "SNS", "SSS"], "result": {"id": "awaken:runner_rope"}})
    write(os.path.join(r, "miners_lamp.json"), {"type": "minecraft:crafting_shaped", "category": "equipment",
          "key": {"L": "minecraft:leather", "C": "minecraft:copper_ingot", "G": "minecraft:glowstone_dust"}, "pattern": ["LCL", "CGC"],
          "result": {"id": "awaken:miners_lamp"}})


def tags():
    t = os.path.join(MERGE, "data")
    hearts = ["awaken:" + o + "_lode_heart" for o in ORES]
    write(os.path.join(t, "minecraft/tags/block/mineable/pickaxe.json"), {"values": ["awaken:deepglass", "awaken:piton"] + hearts})
    write(os.path.join(t, "minecraft/tags/block/needs_diamond_tool.json"), {"values": ["awaken:deepglass", "awaken:deepglass_lode_heart"]})
    write(os.path.join(t, "minecraft/tags/block/needs_iron_tool.json"), {"values": ["awaken:" + o + "_lode_heart" for o in ORES if HEART_TOOL[o] == "iron"]})
    write(os.path.join(t, "minecraft/tags/block/needs_stone_tool.json"), {"values": ["awaken:" + o + "_lode_heart" for o in ORES if HEART_TOOL[o] == "stone"]})
    # the Veil sees Deepglass and the hearts; each heart counts as its ore (Ore Sense's colour, the Veil's)
    write(os.path.join(t, "c/tags/block/ores.json"), {"values": ["awaken:deepglass"] + hearts})
    for o in ORES[:-1]:
        write(os.path.join(t, f"c/tags/block/ores/{o}.json"), {"values": [f"awaken:{o}_lode_heart"]})
        write(os.path.join(t, f"minecraft/tags/block/{o}_ores.json"), {"values": [f"awaken:{o}_lode_heart"]})
    write(os.path.join(t, "awaken/tags/block/runner_climbs.json"), {"values": ["awaken:runner_line"]})
    # the tier metals (PLAN 7.1): one item each, nothing to stand in for it
    metals = {"d": "minecraft:iron_ingot", "c": "minecraft:diamond", "b": "awaken:deepglass_shard", "a": "minecraft:netherite_scrap",
              "s": "awaken:starmetal_ingot"}
    for k, v in metals.items():
        write(os.path.join(t, f"awaken/tags/item/tier_metal_{k}.json"), {"values": [v]})
    sets = ["wildhide", "beastbound", "gatewrought", "veilwoven", "lordscale", "sovereign"]
    write(os.path.join(t, "awaken/tags/item/mining_tools.json"), {"values": ["#minecraft:pickaxes"] + [{"id": f"awaken:{s}_mattock", "required": False} for s in sets]})


def worldgen():
    w = os.path.join(DATA, "worldgen")
    write(os.path.join(w, "feature/delve/lodes.json"), {"type": "awaken:lodes"})
    write(os.path.join(w, "placed_feature/delve/lodes.json"), {"feature": "awaken:delve/lodes", "placement": []})
    # Deepglass veins below y 0 (W5 adds the seeps): small, open to the caves they cross
    write(os.path.join(w, "feature/delve/deepglass.json"), {"type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0, "size": 5,
          "targets": [{"state": "awaken:deepglass",
                       "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}}]})
    write(os.path.join(w, "placed_feature/delve/deepglass.json"), {"feature": "awaken:delve/deepglass", "placement": [
        {"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 4}, "max_inclusive": {"absolute": 0}}},
        {"type": "minecraft:biome"}]})
    write(os.path.join(DATA, "neoforge/biome_modifier/delve.json"), {"type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
          "features": ["awaken:delve/lodes", "awaken:delve/deepglass"], "step": "underground_ores"})


def fallen_star():
    """The fallen star's core: raw starmetal, cut out of every meteor (S-rank's metal: you chase stars across the night)."""
    path = os.path.join(DATA, "loot_table/blocks/anomaly_fallen_star.json")
    d = json.load(open(path))
    d["pools"] = [p for p in d["pools"] if not any(e.get("name") == "awaken:raw_starmetal" for e in p.get("entries", []))]
    d["pools"].append({"rolls": 1, "entries": [{"type": "minecraft:item", "name": "awaken:raw_starmetal", "modifier": {
        "type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}}]})
    write(path, d)


def content():
    lang = {}
    # Deepglass
    cube("deepglass")
    write(os.path.join(ASSETS, "blockstates/deepglass.json"), {"variants": {"": {"model": "awaken:block/deepglass"}}})
    item_block("deepglass")
    loot("deepglass", [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": "awaken:deepglass", "condition": SILK},
        ore_drop("awaken:deepglass_shard", 1, 2)]}]}])
    lang["block.awaken.deepglass"] = "Deepglass"
    # the hearts
    for o in ORES:
        name = o + "_lode_heart"
        cube(name)
        write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": "awaken:block/" + name}}})
        item_block(name)
        item, lo, hi = HEART_DROP[o]
        loot(name, [{"rolls": 1, "entries": [ore_drop(item, lo, hi)]}])
        lang["block.awaken." + name] = ORE_TITLE[o] + " Lode Heart"
    # piton and rope
    piton_models()
    rope_model()
    item_generated("piton")
    loot("piton", [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "awaken:piton"}], "condition": {"type": "minecraft:survives_explosion"}},
                   {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "awaken:runner_rope"}],
                    "condition": {"type": "minecraft:match_block", "blocks": "awaken:piton", "state": {"roped": "true"}}}])
    lang["block.awaken.piton"] = "Runner's Piton"
    lang["block.awaken.runner_line"] = "Runner's Rope"
    # items
    for n in ("deepglass_shard", "raw_starmetal", "starmetal_ingot", "bell_bronze_ingot", "runner_rope", "miners_lamp"):
        item_generated(n)
    item_handheld("tuning_fork")
    lang.update({"item.awaken.deepglass_shard": "Deepglass Shard", "item.awaken.raw_starmetal": "Raw Starmetal",
                 "item.awaken.starmetal_ingot": "Starmetal Ingot", "item.awaken.bell_bronze_ingot": "Bell Bronze Ingot",
                 "item.awaken.tuning_fork": "Tuning Fork", "item.awaken.runner_rope": "Runner's Rope", "item.awaken.miners_lamp": "Miner's Lamp"})
    # the lamp as worn
    write(os.path.join(ASSETS, "equipment/miners_lamp.json"), {"layers": {"humanoid": [{"texture": "awaken:miners_lamp"}]}})
    # particles
    for name, fn in PARTICLES.items():
        write(os.path.join(ASSETS, "particles", name + ".json"), {"textures": [f"awaken:{name}_{i}" for i in range(len(fn()))]})
    recipes()
    tags()
    worldgen()
    fallen_star()
    lang_path = os.path.join(MERGE, "assets/awaken/lang/en_us.json")
    cur = json.load(open(lang_path)) if os.path.exists(lang_path) else {}
    cur.update(lang)
    write(lang_path, dict(sorted(cur.items())))
    print("delve content:", len(lang), "names")


def preview(dest):
    from PIL import Image

    class Grab(P.Out):
        def __init__(self):
            self.items = []
            self.written = []

        def _save(self, rel, c, frametime=None):
            self.items.append((rel, c[0] if isinstance(c, list) else c))

    g = Grab()
    build(g)
    scale = 8
    cols = 8
    w = cols * (16 * scale + 8)
    rows = (len(g.items) + cols - 1) // cols
    sheet = Image.new("RGBA", (w, rows * (16 * scale + 8)), (40, 40, 46, 255))
    for i, (rel, c) in enumerate(g.items):
        img = c.image()
        f = max(1, (16 * scale) // max(img.width, img.height))
        img = img.resize((img.width * f, img.height * f), Image.NEAREST)
        sheet.alpha_composite(img, ((i % cols) * (16 * scale + 8) + 4, (i // cols) * (16 * scale + 8) + 4))
    os.makedirs(dest, exist_ok=True)
    out = os.path.join(dest, "delve_sheet.png")
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "content":
        content()
    elif cmd == "preview":
        preview(sys.argv[2] if len(sys.argv) > 2 else ".")
    else:
        raise SystemExit(__doc__)
