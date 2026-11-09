"""The caves' living walls (SPEC §42.10; Euan's playtest 9 Nov): growth that is grown, not stamped.

Ember vine: a dark woody runner that creeps along the Ochre Deep's rock with fire in its sap. Every block of it is a tile cut
for the way the run goes through that block (which of its four edges it leaves by), so a run is one line across the wall; its
stem is drawn in three layers: the rock round it lit warm by the vine (its own rock's pixels, so it melts into the wall), the
bark, and the sap showing through the bark's cracks. The sheet is one 256 x 256 sprite so two hundred cells stay one file.

Bloom: where the vine fruits it carries real seed pods, small boxes that stand off the wall with their hearts white hot.

Rust crust, rust drape and the puddle replace what read as black marks: the rock's own colours, no black, ragged edges, and
each lichen and crust comes in several cuts picked per block by position, so no grid shows.

  textures/block/caves/ember_vine.png          the sheet (see CELL below)
  textures/block/caves/rust_crust_<v>.png      rust_drape_<v>.png  rust_drape_tip_<v>.png
  textures/block/caves/lichen_<band>_<v>.png   mineral_crust_<v>.png  wet_streaks_<v>.png  puddle.png

Run: python3 tools/art/build_all.py deep_skin_growth      (textures; deep_skin first, this reads its ochre_stone)
     python3 tools/content/caves_content.py          (models and blockstates, which ask this file)
     python3 tools/art/specs/deep_skin_growth.py preview <out.png>   a wall of it, lit as a cave lights it (the look check)
"""
import importlib.util
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from PIL import Image  # noqa: E402
from pixelart import Canvas, hex_rgba  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/awaken")
_s = importlib.util.spec_from_file_location("deep_skin_for_growth", os.path.join(HERE, "deep_skin.py"))
SKIN = importlib.util.module_from_spec(_s)
_s.loader.exec_module(SKIN)


def rng(*parts):
    return random.Random("|".join(str(p) for p in ("growth",) + parts))


def H(h, a=255):
    return hex_rgba(h, a)


def rock_pixels(name="ochre_stone"):
    """The rock's own pixels: the lit ring round a vine is this rock, so it is seamless on the wall in any light."""
    im = Image.open(os.path.join(ASSETS, "textures/block/caves", name + ".png")).convert("RGBA")
    return [[im.getpixel((x, y)) for x in range(16)] for y in range(16)]


# ---- ember vine: the tiles ------------------------------------------------------------------------------------------------

U, D, L, R = 1, 2, 4, 8
# where a stem crosses each edge: fixed, or the tiles wouldn't join; the stem wanders between them
PORT = {U: (8.0, 0.0), D: (8.0, 16.0), L: (0.0, 8.0), R: (16.0, 8.0)}
INWARD = {U: (0.0, 1.0), D: (0.0, -1.0), L: (1.0, 0.0), R: (-1.0, 0.0)}
BARK = [H("#1c0c07"), H("#33180c"), H("#4a2412"), H("#663418"), H("#7c4520")]
SAP = [H("#b83a0c"), H("#e8621a"), H("#ff9630"), H("#ffc65e"), H("#ffecb4")]
LEAF = [H("#3a180c"), H("#5e2812"), H("#8a3c16")]

VARIANTS = {0: 1, L | R: 4, U | D: 4}


def variants(mask):
    return VARIANTS.get(mask, 3)


TILES = [(m, v) for m in range(16) for v in range(variants(m))]
# the sheet: four cells a tile (the wide ring of lit rock, the close ring, bark, sap), then the bloom's own
LAYERS = 4
CELL = {t: i * LAYERS for i, t in enumerate(TILES)}
_END = len(TILES) * LAYERS
BLOOM_RING, POD, PAD, ICON = _END, _END + 1, _END + 2, _END + 3
SHEET_W, SHEET_H, COLS = 256, 256, 16


def bezier(p0, p1, p2, p3, n=56):
    out = []
    for i in range(n + 1):
        t = i / n
        a, b, c, d = (1 - t) ** 3, 3 * t * (1 - t) ** 2, 3 * t * t * (1 - t), t ** 3
        out.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1], t))
    return out


def _lead(r, port, lo=4.5, hi=6.5):
    """A curve's handle at a port: straight out of the edge, so the stem meets its neighbour's tile square on."""
    x0, y0 = PORT[port]
    ix, iy = INWARD[port]
    bend = r.uniform(lo, hi)
    return (x0, y0), (x0 + ix * bend, y0 + iy * bend)


def skeleton(mask, v):
    """The tile's stems as curves [(points, kind)]: a smooth line through a block, edge to edge, that wanders and bends; a
    third or fourth edge joins it as a side shoot. Kind 'main', 'shoot' or 'tip' (tapers to a point). Also returns the point
    the sap runs hottest."""
    r = rng("skel", mask, v)
    ports = [p for p in (U, D, L, R) if mask & p]
    curves = []
    hot = (8 + r.uniform(-2.2, 2.2), 8 + r.uniform(-2.2, 2.2))
    if len(ports) == 0:
        a = r.uniform(0, 6.28)
        tip = (hot[0] + math.cos(a) * 5, hot[1] + math.sin(a) * 5)
        curves.append((bezier(hot, (hot[0] + r.uniform(-3, 3), hot[1] + r.uniform(-3, 3)), (tip[0] + r.uniform(-2, 2), tip[1] + r.uniform(-2, 2)), tip), "tip"))
        return curves, hot
    if len(ports) == 1:
        p = ports[0]
        p0, p1 = _lead(r, p, 4.0, 5.5)
        ix, iy = INWARD[p]
        side = r.choice((-1, 1))
        # on past the middle, then a curl to the side
        tip = (8 + ix * 2.5 + iy * side * r.uniform(2.5, 5), 8 + iy * 2.5 + ix * side * r.uniform(2.5, 5))
        hook = (tip[0] + iy * side * 2.5 - ix * 1.5, tip[1] + ix * side * 2.5 - iy * 1.5)
        curves.append((bezier(p0, p1, hook, tip), "tip"))
        return curves, tip
    # the two that make the most of a line: opposite edges, else the first pair
    a, b = ports[0], ports[1]
    for i in range(len(ports)):
        for j in range(i + 1, len(ports)):
            if ports[i] | ports[j] in (U | D, L | R):
                a, b = ports[i], ports[j]
    pa, ha = _lead(r, a)
    pb, hb = _lead(r, b)
    ax, ay = INWARD[a]
    # lean the handles apart across the line, so a straight run snakes instead of ruling a straight line
    wob = r.uniform(2.8, 5.2) * r.choice((-1, 1))
    if a | b in (U | D, L | R):
        ha = (ha[0] + ay * wob, ha[1] + ax * wob)
        hb = (hb[0] - ay * wob, hb[1] - ax * wob)
    else:
        # round the bend rather than through the middle
        pull = r.uniform(-1.2, 1.2)
        ha = (ha[0] * 0.75 + pb[0] * 0.25 + pull, ha[1] * 0.75 + pb[1] * 0.25 + pull)
        hb = (hb[0] * 0.75 + pa[0] * 0.25 - pull, hb[1] * 0.75 + pa[1] * 0.25 - pull)
    main = bezier(pa, ha, hb, pb)
    curves.append((main, "main"))
    hot = main[len(main) // 2][:2]
    for extra in ports:
        if extra in (a, b):
            continue
        pc, hc = _lead(r, extra, 3.5, 5.5)
        # it joins the line where the line is nearest the middle
        join = main[r.randint(len(main) // 3, 2 * len(main) // 3)]
        curves.append((bezier(pc, hc, (join[0] + r.uniform(-2, 2), join[1] + r.uniform(-2, 2)), join[:2]), "shoot"))
    return curves, hot


def tile(mask, v, rock):
    """A tile's four layers as (wide ring, close ring, bark, sap) 16x16 canvases."""
    r = rng("tile", mask, v)
    curves, hot = skeleton(mask, v)
    samples = []
    for ci, (cv, kind) in enumerate(curves):
        for x, y, t in cv:
            if kind == "tip":
                w = 1.02 - 0.74 * t ** 1.3
            elif kind == "shoot":
                w = 0.78 + 0.2 * (1 - t)
            else:
                w = 1.0 - 0.16 * math.sin(t * 3.14)
            samples.append((x, y, w, ci, kind, t))
    wide, ring, bark, sap = Canvas(16, 16), Canvas(16, 16), Canvas(16, 16), Canvas(16, 16)
    noise = [[r.random() for _ in range(16)] for _ in range(16)]
    near = [[None] * 16 for _ in range(16)]
    centre = [[0.0] * 16 for _ in range(16)]       # distance to the nearest curve's line
    edge_d = [[0.0] * 16 for _ in range(16)]       # distance to that stem's edge (negative inside)
    for py in range(16):
        for px in range(16):
            best = 99.0
            for s in samples:
                d = math.hypot(px + 0.5 - s[0], py + 0.5 - s[1])
                if d - s[2] < best:
                    best, near[py][px], centre[py][px] = d - s[2], s, d
            edge_d[py][px] = best
    # the bark: lit on its upper left, in shadow on its lower right; deep brown, never black, so it sits with the rock
    for py in range(16):
        for px in range(16):
            if edge_d[py][px] >= 0.34:
                continue
            nx, ny = near[py][px][:2]
            ox, oy = px + 0.5 - nx, py + 0.5 - ny
            lit = -(ox + oy) / (abs(ox) + abs(oy) + 0.01)
            k = 3 if lit > 0.3 else 1 if lit < -0.3 else 2
            if noise[py][px] > 0.86:
                k = max(1, k - 1)
            bark.set(px, py, BARK[k])
    # the sap: the stem's middle, one unbroken line of fire from edge to edge, hotter where the plant is thickest
    phase = r.uniform(0, 6.28)
    for py in range(16):
        for px in range(16):
            d = centre[py][px]
            s = near[py][px]
            if d > 0.86:
                continue
            arc = math.sin(s[5] * 9 + phase + s[3] * 2.2)
            k = 2 + (arc > 0.25) + (arc > 0.72 and noise[py][px] > 0.3)
            if s[4] == "tip":
                k = 2 + (s[5] > 0.55)
            if noise[py][px] < 0.08:
                k = max(0, k - 2)                  # a knot of the bark over it, a pixel only
            sap.set(px, py, SAP[min(4, k)])
    # a bead of fire on a run's last tip
    tips = [cv for cv, kind in curves if kind == "tip"]
    for cv in tips:
        x, y = int(cv[-1][0]), int(cv[-1][1])
        for dx, dy, k in ((0, 0, 4), (1, 0, 2), (-1, 0, 2), (0, 1, 2), (0, -1, 3), (1, -1, 1), (-1, 1, 1)):
            if 0 <= x + dx < 16 and 0 <= y + dy < 16:
                sap.set(x + dx, y + dy, SAP[k])
    # leaves: on a short stalk, drooping, dark with their rims lit by the sap inside them, so a run reads as a plant in the dark
    for _ in range(r.randint(2, 4)):
        c = r.choice(samples)
        if min(c[0], c[1], 16 - c[0], 16 - c[1]) < 3.0 or c[4] == "tip":
            continue
        a = r.uniform(0, 6.28)
        droop = r.uniform(0.35, 0.8)
        lx, ly = c[0], c[1]
        cells = []
        for i in range(r.randint(3, 6)):
            lx += math.cos(a) * 1.1
            ly += math.sin(a) * 0.8 + droop
            cells.append((int(lx), int(ly), i))
        for x, y, i in cells:
            if not (1 <= x < 15 and 1 <= y < 15) or edge_d[y][x] < 0.4:
                continue
            bark.set(x, y, LEAF[1 if i < 2 else 2])
            if i >= 1:
                side = r.choice((-1, 1))
                if abs(math.cos(a)) > 0.5:
                    bark.set(x, y + side, LEAF[0 if i < 3 else 1])
                else:
                    bark.set(x + side, y, LEAF[0 if i < 3 else 1])
            if i == len(cells) - 1 or (i >= 2 and r.random() < 0.34):
                sap.set(x, y, SAP[1 if i < len(cells) - 1 else 2])
    # the rock round it, lit: a close band hot against the stem, a wide faint one beyond, ragged at the rim. It never reaches an
    # edge the stem doesn't cross, so a run's end doesn't stop dead at a block line.
    for py in range(16):
        for px in range(16):
            d = edge_d[py][px]
            if d <= 0.3:
                continue
            limit = 99.0
            if not mask & U and py < 8:
                limit = min(limit, py + 0.5)
            if not mask & D and py >= 8:
                limit = min(limit, 15.5 - py)
            if not mask & L and px < 8:
                limit = min(limit, px + 0.5)
            if not mask & R and px >= 8:
                limit = min(limit, 15.5 - px)
            rough = (noise[py][px] - 0.5) * 1.4
            pr, pg, pb, _ = rock[py][px]
            # the outer part of each band thins out pixel by pixel: cut-out has no soft edge, and a solid rim reads as a sticker
            fray = r.random()
            # the close band
            reach = 2.4 + rough * 0.7
            f = max(d / reach, d / (limit * 1.1))
            if f < 1 and not (f > 0.5 and fray < (f - 0.5) / 0.5):
                g = 1.2 + 0.5 * (1 - d / reach)
                ring.set(px, py, (min(255, round(pr * g * 1.08)), min(255, round(pg * g * 0.98)), min(255, round(pb * g * 0.78)), 255))
            # the wide band: a shade over the rock
            reach = 6.4 + rough * 1.6
            f = max(d / reach, d / limit)
            if f < 1 and not (f > 0.35 and fray < (f - 0.35) / 0.65):
                g = 1.02 + 0.2 * (1 - d / reach)
                wide.set(px, py, (min(255, round(pr * g * 1.05)), min(255, round(pg * g * 0.99)), min(255, round(pb * g * 0.86)), 255))
    return wide, ring, bark, sap


def pod_cell():
    """The seeds' skin: mottled heat with no frame. The seeds take small pieces of it at one texel to the pixel."""
    c = Canvas(16, 16)
    r = rng("pod")
    a, b, d = r.uniform(0, 6.28), r.uniform(0, 6.28), r.uniform(0, 6.28)
    for y in range(16):
        for x in range(16):
            v = 0.55 + 0.24 * math.sin(x * 0.9 + a) * math.cos(y * 0.7 + b) + 0.22 * math.sin((x + y) * 0.5 + d) + (r.random() - 0.5) * 0.3
            c.set(x, y, SAP[1 if v < 0.34 else 2 if v < 0.58 else 3 if v < 0.84 else 4])
    return c


def bloom_ring_cell(rock):
    """The rock lit by a bloom: a round wash centred on the tile, fraying at its rim, none at the block's edge."""
    r = rng("bloomring")
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) + (r.random() - 0.5) * 1.2
            if d >= 7.4 or (d > 3.2 and r.random() < (d - 3.2) / 4.2):
                continue
            f = 1 - d / 7.4
            g = 1.08 + 0.62 * f
            pr, pg, pb, _ = rock[y][x]
            c.set(x, y, (min(255, round(pr * g * 1.08)), min(255, round(pg * g * 0.98)), min(255, round(pb * g * 0.78)), 255))
    return c


def pad_cell():
    """The bloom's cup of dark curled leaves, opening round the pods."""
    c = Canvas(16, 16)
    r = rng("pad")
    for a in range(r.randint(10, 12)):
        ang = a / 11 * 6.283 + r.uniform(-0.24, 0.24)
        for t in range(2, r.randint(5, 8)):
            x, y = int(7.5 + math.cos(ang) * t), int(7.5 + math.sin(ang) * t)
            c.set(x, y, LEAF[0] if t < 3 else LEAF[1] if t < 6 else LEAF[2])
    return c


def icon_cell(rock):
    """The item icon: a short stem and a bloom on nothing."""
    _, _, bark, sap = tile(U | D, 0, rock)
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            for layer in (bark, sap):
                if layer.get(x, y)[3]:
                    c.set(x, y, layer.get(x, y))
    for y in range(5, 11):
        for x in range(5, 11):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 2.7:
                c.set(x, y, SAP[4 if d < 1.1 else 3 if d < 2 else 2])
    return c


def sheet():
    rock = rock_pixels()
    c = Canvas(SHEET_W, SHEET_H)

    def put(cell, canvas):
        cx, cy = (cell % COLS) * 16, (cell // COLS) * 16
        for y in range(16):
            for x in range(16):
                p = canvas.get(x, y)
                if p[3]:
                    c.set(cx + x, cy + y, p)

    for (m, v), cell in CELL.items():
        for k, layer in enumerate(tile(m, v, rock)):
            put(cell + k, layer)
    put(BLOOM_RING, bloom_ring_cell(rock))
    put(POD, pod_cell())
    put(PAD, pad_cell())
    put(ICON, icon_cell(rock))
    return c


# ---- rust crust and rust drape: the rock's own colours, no black -------------------------------------------------------------

RUST = [H("#7a3216"), H("#9a4a1e"), H("#b86228"), H("#d07c36"), H("#e6a25e")]


def rust_crust(v):
    """Iron stain crept over the rock: warm, a shade from the rock itself, in ragged overlapping blots with a brighter rim, no
    pits and no black; the rock shows through it, and it thins out at every edge."""
    r = rng("rust", v)
    c = Canvas(16, 16)
    blots = [(r.uniform(2, 14), r.uniform(2, 14), r.uniform(2.0, 4.4)) for _ in range(r.randint(3, 5))]
    for y in range(16):
        for x in range(16):
            best = min(math.hypot(x + 0.5 - bx, y + 0.5 - by) / br + (r.random() - 0.5) * 0.46 for bx, by, br in blots)
            if best >= 1.0:
                continue
            edge = min(x + 0.5, y + 0.5, 15.5 - x, 15.5 - y)
            if edge < 2.4 and r.random() > edge / 2.4:
                continue
            k = 3 if best > 0.74 else 2 if best > 0.38 else 1
            if r.random() < 0.1:
                k = max(0, k - 1)
            if best > 0.86 and r.random() < 0.3:
                k = 4
            c.set(x, y, RUST[k])
    return c


FOLDS = [H("#9a4a1e"), H("#b65e28"), H("#d07c36"), H("#e89a52"), H("#f6c48c")]


def drape_tex(v, tip=False):
    """Flowstone: the folds' colours down a column, banded where it grew in seasons, a wet highlight along each fold's ridge."""
    r = rng("drape", v, tip)
    c = Canvas(16, 16)
    bands = sorted(r.sample(range(2, 15), 3))
    for y in range(16):
        band = 1 if y in bands else 0
        for x in range(16):
            across = abs((x % 5) - 2) / 2
            k = 3 if across < 0.4 else 2 if across < 0.8 else 1
            if r.random() < 0.08:
                k -= 1
            k -= band
            if x % 5 == 1 and r.random() < 0.12:
                k = 4
            c.set(x, y, FOLDS[max(0, min(4, k))])
    return c


# per block picks; a lichen's or a crust's cuts share its art and differ by seed, so a wall of them has no grid
OVERLAY_CUTS = {"lichen_shallows": 4, "lichen_middle": 4, "lichen_deep": 4, "mineral_crust": 3, "wet_streaks": 3, "rust_crust": 4, "rust_flowstone": 3}
DRAPE_CUTS = 3


def soft_wet_streaks(v):
    """Damp down the rock: a dark sheen at low alpha (drawn translucent), a single bead catching the light; never a black bar."""
    r = rng("wet", v)
    c = Canvas(16, 16)
    for _ in range(r.randint(3, 5)):
        x = r.randrange(16)
        y0 = r.randint(-6, 4)
        n = r.randint(6, 18)
        for i in range(n):
            y = y0 + i
            if 0 <= y < 16:
                a = int(60 * (1 - i / n) + 14)
                c.set(x, y, (18, 22, 30, a))
                if r.random() < 0.2:
                    x = (x + r.choice((-1, 1))) % 16
        by = y0 + n - 1
        if 0 <= by < 16 and r.random() < 0.6:
            c.set(x, by, (214, 232, 244, 120))
    return c


def soft_puddle(n=16, seed=0):
    """A skin of water: pale so the cave's water colour shows true, well see-through, a bright shallow rim and a little ripple;
    only the slightest damp round it (a dark ring read as a black blot on the Ochre Deep's orange rock)."""
    r = rng("puddle", seed)
    cx, cy = r.uniform(7.2, 8.8), r.uniform(7.2, 8.8)
    harm = [(r.uniform(0.5, 1.1), r.uniform(0, 6.28), k) for k in (2, 3, 5)]
    sat = (cx + r.choice((-1, 1)) * r.uniform(4.2, 5.0), cy + r.choice((-1, 1)) * r.uniform(3.6, 4.6), r.uniform(1.4, 2.0))

    def depth(x, y):
        a = math.atan2(y - cy, x - cx)
        d = math.hypot(x - cx, y - cy) / (5.2 + sum(amp * math.sin(k * a + ph) for amp, ph, k in harm) * 0.8)
        s = math.hypot(x - sat[0], y - sat[1]) / sat[2]
        return min(d, s)

    frames = []
    ring_at, rx, ry = r.randrange(n), cx + r.uniform(-2, 2), cy + r.uniform(-2, 2)
    for f in range(n):
        c = Canvas(16, 16)
        age = (f - ring_at) % n
        for y in range(16):
            for x in range(16):
                d = depth(x + 0.5, y + 0.5)
                if d >= 1.16:
                    continue
                if d >= 1.0:
                    c.set(x, y, (120, 120, 120, 20))           # the slightest damp
                    continue
                if d > 0.82:
                    col, a = 246, 118                          # a bright shallow rim
                elif d > 0.6:
                    col, a = 214, 92
                else:
                    col, a = 196, 104
                rr = math.hypot(x + 0.5 - rx, y + 0.5 - ry)
                if age < 8 and abs(rr - (1 + age * 0.7)) < 0.6 and d < 0.8:
                    col, a = 255, 150 - age * 12
                c.set(x, y, (col, col, col, a))
        frames.append(c)
    return frames


def build(out):
    out.block("caves/ember_vine", sheet())
    out.block("caves/ember_vine_icon", icon_cell(rock_pixels()))
    for name, cuts in OVERLAY_CUTS.items():
        for v in range(cuts):
            if name == "rust_crust":
                out.block(f"caves/rust_crust_{v}", rust_crust(v))
            elif name == "wet_streaks":
                out.block(f"caves/wet_streaks_{v}", soft_wet_streaks(v))
            elif name == "mineral_crust":
                out.block(f"caves/mineral_crust_{v}", SKIN.mineral_crust(seed=v * 13 + 1))
            elif name == "rust_flowstone":
                out.block(f"caves/rust_flowstone_{v}", soft_flowstone(v))
            else:
                band = name.split("_", 1)[1]
                out.block(f"caves/{name}_{v}", SKIN.lichen(band, seed=v * 17 + 3))
    for v in range(DRAPE_CUTS):
        out.block(f"caves/rust_drape_{v}", drape_tex(v))
        out.block(f"caves/rust_drape_tip_{v}", drape_tex(v, tip=True))
    out.block("caves/puddle", soft_puddle(), frametime=4)
    # the old single textures, redrawn so cells already placed in saved caves change with them
    out.block("caves/rust_crust", rust_crust(0))
    out.block("caves/wet_streaks", soft_wet_streaks(0))
    out.block("caves/rust_flowstone", soft_flowstone(0))


def soft_flowstone(v):
    """The flat flowstone left in saved caves: ragged folds in the rock's own warm colours, no black bars between them."""
    r = rng("flow", v)
    c = Canvas(16, 16)
    x = r.randint(0, 2)
    while x < 16:
        w = r.randint(2, 4)
        end = r.randint(9, 16)
        for y in range(end):
            taper = max(0, y - (end - 3))
            for k in range(taper // 2, w - (taper + 1) // 2):
                across = 0 if k == 0 else 3 if k == w - 1 else 2
                band = 1 if y % 4 == 3 else 0
                if x + k < 16:
                    c.set(x + k, y, FOLDS[max(0, min(4, 1 + across - band))])
        x += w + r.choice((1, 2, 3))
    return c


# ---- models -----------------------------------------------------------------------------------------------------------------

Z_WIDE, Z_RING, Z_BARK, Z_SAP = 0.05, 0.07, 0.1, 0.12


def _uv(cell):
    cx, cy = cell % COLS, cell // COLS
    return [cx * 1.0, cy * 1.0, cx + 1.0, cy + 1.0]


def _plane(z, uv, tex, glow=0):
    e = {"from": [0, 0, z], "to": [16, 16, z], "faces": {"south": {"uv": uv, "texture": tex}}}
    if glow:
        e["light_emission"] = glow
    return e


def vine_model(mask, v):
    cell = CELL[(mask, v)]
    return {"ambientocclusion": False, "render_type": "minecraft:cutout", "textures": {"particle": "awaken:block/caves/ember_vine", "t": "awaken:block/caves/ember_vine"},
            "elements": [_plane(Z_WIDE, _uv(cell), "#t", glow=5), _plane(Z_RING, _uv(cell + 1), "#t", glow=8),
                         _plane(Z_BARK, _uv(cell + 2), "#t"), _plane(Z_SAP, _uv(cell + 3), "#t", glow=15)]}


def _box(frm, to, tex_uv, glow):
    faces = {d: {"uv": tex_uv, "texture": "#t"} for d in ("south", "east", "west", "up", "down")}
    return {"from": frm, "to": to, "light_emission": glow, "faces": faces}


def bloom_model(v):
    """A clutch of seeds on a cup of leaves: small boxes heaped off the wall, biggest in the middle, at one texel to the pixel so
    none of them shows a frame. Each cut heaps them differently."""
    r = rng("bloom", v)
    cx, cy = 8 + r.uniform(-1.6, 1.6), 8 + r.uniform(-1.6, 1.6)
    tu, tv = POD % COLS, POD // COLS
    els = [_plane(0.08, _uv(BLOOM_RING), "#t", glow=11), _plane(0.14, _uv(PAD), "#t")]
    made = []
    for i, (w, h, depth) in enumerate(((2.6, 2.4, 2.2), (2.2, 2.0, 1.7), (1.8, 2.0, 1.4), (1.6, 1.4, 1.1), (1.2, 1.4, 0.8), (1.0, 1.0, 0.6))):
        for _ in range(16):
            a, rad = r.uniform(0, 6.283), 0.0 if i == 0 else r.uniform(1.5, 3.4)
            x, y = cx + math.cos(a) * rad, cy + math.sin(a) * rad * 0.85
            if all(abs(x - mx) > (w + mw) / 2 - 0.4 or abs(y - my) > (h + mh) / 2 - 0.4 for mx, my, mw, mh in made):
                break
        made.append((x, y, w, h))
        u, t = r.uniform(0, 16 - w), r.uniform(0, 16 - h)
        seed = [round(tu + u / 16, 4), round(tv + t / 16, 4), round(tu + (u + w) / 16, 4), round(tv + (t + h) / 16, 4)]
        els.append(_box([round(x - w / 2, 2), round(y - h / 2, 2), 0.16], [round(x + w / 2, 2), round(y + h / 2, 2), round(0.16 + depth, 2)], seed, 15 if i < 3 else 13))
    return {"ambientocclusion": False, "render_type": "minecraft:cutout", "textures": {"particle": "awaken:block/caves/ember_vine", "t": "awaken:block/caves/ember_vine"}, "elements": els}


def drape_model(v, tip=False):
    """Folds of flowstone with real depth: three or four tall boxes standing off the wall, tapered in a tip block."""
    r = rng("drapemodel", v, tip)
    name = f"rust_drape_tip_{v}" if tip else f"rust_drape_{v}"
    els = []
    x = r.uniform(0.0, 1.5)
    while x < 15:
        w = r.uniform(2.2, 4.6)
        depth = r.uniform(0.9, 2.1)
        bottom = r.uniform(2.0, 8.0) if tip else 0.0
        y1 = round(bottom, 1)
        x1 = min(16.0, x + w)
        els.append({"from": [round(x, 1), y1, 0.05], "to": [round(x1, 1), 16, round(depth, 1)],
                    "faces": {"south": {"uv": [round(x, 1), 0, round(x1, 1), round(16 - y1, 1)], "texture": "#t"},
                              "east": {"uv": [0, 0, 2, 16], "texture": "#t"}, "west": {"uv": [0, 0, 2, 16], "texture": "#t"}, "down": {"uv": [0, 0, 2, 2], "texture": "#t"}}})
        x += w + r.uniform(0.0, 1.8)
    return {"ambientocclusion": True, "render_type": "minecraft:cutout", "textures": {"particle": f"awaken:block/caves/{name}", "t": f"awaken:block/caves/{name}"}, "elements": els}


def overlay_model(name):
    """A thin overlay on a wall (multiface), as deep_skin's, in one cut. Wet streaks are drawn translucent: at low alpha, a
    cut-out layer would turn them into solid dark bars."""
    trans = name.startswith("wet_streaks")
    return {"ambientocclusion": False, "render_type": "minecraft:translucent" if trans else "minecraft:cutout",
            "textures": {"particle": f"awaken:block/caves/{name}", "t": f"awaken:block/caves/{name}"},
            "elements": [{"from": [0, 0, 0.1], "to": [16, 16, 0.1], "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#t"}}}]}


def model_dict():
    out = {}
    for m, v in TILES:
        out[f"ember_vine_{m}_{v}"] = vine_model(m, v)
    for v in range(3):
        out[f"ember_bloom_{v}"] = bloom_model(v)
    for v in range(DRAPE_CUTS):
        out[f"rust_drape_{v}"] = drape_model(v)
        out[f"rust_drape_tip_{v}"] = drape_model(v, tip=True)
    for name, cuts in OVERLAY_CUTS.items():
        for v in range(cuts):
            out[f"{name}_{v}"] = overlay_model(f"{name}_{v}")
    return out


# ---- a look at it before the game: a wall of the rock, the vine run over it, lit as a cave lights it --------------------------

def preview(path, S=6):
    rock = rock_pixels()
    sh = sheet()
    W, H_ = 22, 12
    # a hand-laid run: a stem up from the floor, along a ledge, with branches
    run = {(3, 11): U | D, (3, 10): U | D, (3, 9): D | R | U, (3, 8): U | D, (3, 7): D | R,
           (4, 7): L | R, (5, 7): L | R, (6, 7): L | U | R, (6, 6): U | D, (6, 5): D | R,
           (7, 5): L | R, (8, 5): L | R, (9, 5): L | D | R, (9, 6): U, (10, 5): L | R, (11, 5): L | R, (12, 5): L | U, (12, 4): U | D, (12, 3): D,
           (4, 9): L | R, (5, 9): L | D, (5, 10): U,
           (15, 11): U | D, (15, 10): U | D, (15, 9): D | L, (14, 9): R | L, (13, 9): R | U, (13, 8): D}
    blooms = {(3, 9), (6, 7), (9, 5), (12, 4), (15, 10)}
    out = Image.new("RGB", (W * 16 * S, H_ * 16 * S))
    # the world's block light: stems faint, blooms strong, one less a block; ambient for a dark cave
    srcs = [(x, y, 10 if (x, y) in blooms else 5) for (x, y) in run]

    def level(cx, cy):
        return max([0.0] + [ly - (abs(sx - cx) + abs(sy - cy)) for sx, sy, ly in srcs])

    def bright(l):
        l = max(0, min(15, l)) / 15
        return 0.04 + 0.96 * (l ** 1.6)

    def light_at(px, py):
        cx, cy = px / 16 - 0.5, py / 16 - 0.5
        x0, y0 = math.floor(cx), math.floor(cy)
        tx, ty = cx - x0, cy - y0
        a, b, c, d = level(x0, y0), level(x0 + 1, y0), level(x0, y0 + 1), level(x0 + 1, y0 + 1)
        return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty

    warm = (1.0, 0.82, 0.58)
    buf = [[None] * (W * 16) for _ in range(H_ * 16)]
    for py in range(H_ * 16):
        for px in range(W * 16):
            br = bright(light_at(px, py))
            p = rock[py % 16][px % 16]
            buf[py][px] = (p[0] * br * warm[0], p[1] * br * warm[1], p[2] * br * warm[2])

    def layer(tx, ty, cell, glow):
        cx0, cy0 = (cell % COLS) * 16, (cell // COLS) * 16
        for y in range(16):
            for x in range(16):
                p = sh.get(cx0 + x, cy0 + y)
                if not p[3]:
                    continue
                px, py = tx * 16 + x, ty * 16 + y
                br = max(bright(light_at(px, py)), glow / 15)
                buf[py][px] = (p[0] * br, p[1] * br, p[2] * br)

    r = rng("preview")
    for (tx, ty), m in run.items():
        v = r.randrange(variants(m))
        cell = CELL[(m, v)]
        layer(tx, ty, cell, 6)
        layer(tx, ty, cell + 1, 10)
        layer(tx, ty, cell + 2, 0)
        layer(tx, ty, cell + 3, 15)
        if (tx, ty) in blooms:
            layer(tx, ty, BLOOM_RING, 13)
            layer(tx, ty, PAD, 0)
            for e in bloom_model(r.randrange(3))["elements"][2:]:
                x0, y0, _ = e["from"]
                x1, y1, _ = e["to"]
                for y in range(int(16 - y1), int(16 - y0) + 1):
                    for x in range(int(x0), int(x1) + 1):
                        u = (x - x0) / max(0.01, x1 - x0) - 0.5
                        w = (16 - y - y0) / max(0.01, y1 - y0) - 0.5
                        d = max(abs(u), abs(w)) * 2
                        c = SAP[4 if d < 0.34 else 3 if d < 0.56 else 2 if d < 0.8 else 1]
                        buf[ty * 16 + y][tx * 16 + x] = (c[0], c[1], c[2])
    for py in range(H_ * 16):
        for px in range(W * 16):
            r0, g0, b0 = buf[py][px]
            for j in range(S):
                for i in range(S):
                    out.putpixel((px * S + i, py * S + j), (min(255, int(r0)), min(255, int(g0)), min(255, int(b0))))
    out.save(path)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "preview":
        preview(sys.argv[2])
    else:
        print(__doc__)
