"""The Living Deep's skin and regions (SPEC §42, P1-P3): 16x16 pieces in Minecraft's own manner, made of the rock they sit on.

Every cave gets the skin, rock-aware: rubble heaps where a floor meets a wall, loose stones, stalactites and stalagmites in
four thicknesses (real tapered 3D models, not vanilla's flat cross), lichen in the band's colour, mineral crust, wet
streaks that catch the light, puddles that take the cave's water colour, and dusty webs. Each region adds its own rock and
kit; the style sheet shows all eight regions' palettes, a rock, a particle and a creature each before any is made.

  textures/block/caves/<rock>_rubble.png, <rock>_formation.png, <rock>_formation_tip.png    the skin, per rock
  textures/block/caves/lichen_<band>.png, mineral_crust.png, wet_streaks.png                  wall overlays
  textures/block/caves/puddle.png (animated, grey: the biome's water colour tints it), dusty_web.png
  textures/block/caves/<region rock>.png                                                       region rocks
  textures/particle/caves/*.png                                                                drips, splashes, motes, spores

Run: python3 tools/art/build_all.py deep_skin            (textures)
     python3 tools/art/specs/deep_skin.py models          (block models for the skin, for the game and the dioramas)
     python3 tools/art/specs/deep_skin.py sheet <dir>     (the style sheet's pictures)
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import Canvas, hex_rgba  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/awaken")


def rng(*parts):
    return random.Random("|".join(str(p) for p in ("deep",) + parts))


def H(h, a=255):
    return hex_rgba(h, a)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


# ---- rocks --------------------------------------------------------------------------------------------------------------
# Seven steps each, crevice to highlight. The middle five are the game's own (sampled from its textures), so a piece reads as
# made of the rock beside it; the crevice and the highlight are ours, for depth.
ROCKS = {
    "stone": ["#3c3c3e", "#5c5c5e", "#686868", "#747474", "#7f7f7f", "#8f8f8f", "#a6a6a4"],
    "deepslate": ["#16161c", "#25252c", "#2f2f37", "#3d3d43", "#515151", "#646464", "#80808a"],
    "tuff": ["#2e3029", "#43463d", "#4d5046", "#5d5d52", "#6a6e6f", "#85837b", "#a6a89c"],
    # the regions' rocks (P2 the first two, P3 the rest; the style sheet shows all)
    "rootstone": ["#1e140e", "#33241a", "#47321f", "#5c4128", "#735336", "#8c6a48", "#a8875f"],
    "limestone": ["#6a6352", "#8a826c", "#a69e86", "#bdb59c", "#d0c9b0", "#e2dcc6", "#f2eedf"],
    "slate": ["#15181d", "#22262d", "#2e333a", "#3e444c", "#545b64", "#6e7680", "#8e98a2"],
    "ochre": ["#2c1209", "#4f2111", "#6e2f16", "#8f421d", "#b05a28", "#cc7a3a", "#e6a25e"],
    "siltstone": ["#1f2420", "#2f3630", "#3d453d", "#4c554b", "#5e685c", "#737e70", "#8e9a8a"],
    "glimmer": ["#3a2c1e", "#5a4632", "#76603f", "#8f7a52", "#a99466", "#c2ae80", "#dccaa0"],
    "basalt": ["#0e0e11", "#18181c", "#222227", "#2d2d33", "#3b3b42", "#4c4c54", "#64646e"],
    "seepstone": ["#130e1c", "#201830", "#2c2240", "#3a2e52", "#4b3e66", "#5e5080", "#7a6c9e"],
}
SKIN_ROCKS = ("stone", "deepslate", "tuff")
# the ores that live in the P2 regions, with the region's rock behind them: (rock, ore, block id)
TWINS = [("rootstone", "coal", "rootstone_coal_ore"), ("rootstone", "copper", "rootstone_copper_ore"), ("ochre", "iron", "ochre_iron_ore")]


def ramp(rock):
    return [H(c) for c in ROCKS[rock]]


def tor(dx, n=16):
    """The shortest offset on a 16-pixel torus (textures tile)."""
    return (dx + n / 2) % n - n / 2


# ---- rubble: broken rock heaped where a floor meets a wall --------------------------------------------------------------

def rubble(rock, seed=0):
    """Angular broken fragments with dark gaps between them (not cobblestone's rounded mortar): a Voronoi of jittered
    centres on the torus under a polygon metric, so each piece has flat faces; lit from the top left, each piece's far
    edge in shadow, the rock's grain on every face."""
    r = rng("rubble", rock, seed)
    R = ramp(rock)
    cells = []
    for gy in range(3):
        for gx in range(3):
            cx, cy = gx * 16 / 3 + r.uniform(0.6, 4.6), gy * 16 / 3 + r.uniform(0.6, 4.6)
            dirs = []
            a0 = r.uniform(0, math.pi)
            for k in range(3):
                a = a0 + k * math.pi / 3 + r.uniform(-0.25, 0.25)
                dirs.append((math.cos(a), math.sin(a), r.uniform(2.4, 3.6)))
            cells.append((cx, cy, dirs, r.choice((2, 3, 3, 4, 4, 5))))
    c = Canvas(16, 16)
    grain = [[r.random() for _ in range(16)] for _ in range(16)]
    for y in range(16):
        for x in range(16):
            best = []
            for cx, cy, dirs, tone in cells:
                dx, dy = tor(x + 0.5 - cx), tor(y + 0.5 - cy)
                m = max(abs(dx * u + dy * v) / s for u, v, s in dirs)
                best.append((m, dx, dy, tone))
            best.sort()
            m1, dx, dy, tone = best[0]
            m2 = best[1][0]
            if m2 - m1 < 0.16 or m1 > 1.15:
                # the gap: deep shadow, now and then a grain of grit
                c.set(x, y, R[1] if grain[y][x] < 0.18 else R[0])
                continue
            # light from the top left: the piece's upper-left faces bright, its lower-right edge dark
            facing = -(dx + dy) / (abs(dx) + abs(dy) + 0.01)
            edge = min(1.0, m1 / 0.95)
            k = tone + (1 if facing > 0.35 and edge > 0.55 else -1 if facing < -0.35 and edge > 0.6 else 0)
            if grain[y][x] > 0.86:
                k += 1
            elif grain[y][x] < 0.1:
                k -= 1
            c.set(x, y, R[max(1, min(6, k))])
    return c


# ---- formations: stalactites and stalagmites of the local rock ----------------------------------------------------------

def formation(rock, tip=False, seed=0):
    """Flowstone: the rock in long fluted runs (a groove, its lit lip on the left, its shadow on the right), soft growth
    ripples that wander across rather than ruled rings, and short wet glints down the flutes. The tip's texture is paler
    and wetter, a bead of water at its point (its middle columns, the bottom rows: what the point's box shows)."""
    r = rng("formation", rock, tip, seed)
    R = ramp(rock)
    c = Canvas(16, 16)
    ph = [r.uniform(0, 6.28) for _ in range(3)]
    k = [[3.3 + 0.55 * math.sin(x * 2 * math.pi / 16 + ph[0]) + 0.35 * math.sin(x * 4 * math.pi / 16 + ph[1]) for x in range(16)]
         for _ in range(16)]
    # ripples: a light crest and a soft shade under it, wandering in y as they cross
    for y in range(16):
        for x in range(16):
            w = math.sin((y + 1.1 * math.sin(x * 0.55 + ph[2])) * 2 * math.pi / 5.3)
            k[y][x] += 0.45 * max(0.0, w) ** 3 - 0.3 * max(0.0, -w) ** 4
    # flutes: grooves of uneven length, each with a lit lip on its left
    xs = r.sample(range(16), 5)
    for gx in xs:
        y0 = r.randint(-6, 6)
        y1 = y0 + r.randint(7, 16)
        for y in range(max(0, y0), min(16, y1)):
            fade = 1.0 if y < y1 - 2 else 0.5
            k[y][gx] -= 1.3 * fade
            k[y][(gx - 1) % 16] += 0.55 * fade
    for y in range(16):
        for x in range(16):
            v = k[y][x] + (0.6 if tip else 0) + r.uniform(-0.3, 0.3)
            c.set(x, y, R[max(1, min(6, int(round(v))))])
    # short wet glints down the lips
    for _ in range(4 if tip else 3):
        gx = (r.choice(xs) - 1) % 16
        gy = r.randint(0, 13)
        for y in range(gy, min(16, gy + r.randint(1, 3))):
            c.set(gx, y, mix(R[6], H("#d8ecf6"), 0.25))
    if tip:
        for y in range(9, 14):
            c.set(7, y, mix(R[5], H("#cfe6f2"), 0.25 + 0.08 * (y - 9)))
        c.set(8, 12, mix(R[6], H("#d8ecf6"), 0.5))
        c.set(7, 13, H("#e8f6ff"))
        c.set(8, 13, mix(R[6], H("#cfe6f2"), 0.6))
    return c


# ---- the rock itself, for the regions -------------------------------------------------------------------------------------

def region_rock(rock, seed=0):
    """Each region rock in its own manner: rootstone threaded with old roots, limestone in bedding planes, slate in bands
    with mica, ochre in warm bands, siltstone in fine damp laminae, the Glimmer host sandy with a crystal, basalt in
    joints, seepstone violet-stained with a Deepglass vein; the Glimmer host is sandy with amber flecks."""
    r = rng("rock", rock, seed)
    R = ramp(rock)
    c = Canvas(16, 16)
    base = [[r.random() for _ in range(16)] for _ in range(16)]
    # soft noise for blotches
    blot = [[0.0] * 16 for _ in range(16)]
    for _ in range(9):
        bx, by, rad = r.uniform(0, 16), r.uniform(0, 16), r.uniform(2, 4.5)
        for y in range(16):
            for x in range(16):
                d = math.hypot(tor(x - bx), tor(y - by))
                if d < rad:
                    blot[y][x] += (1 - d / rad) * r.choice((-1, 1)) * 0.8
    band_phase = r.uniform(0, 6.28)
    for y in range(16):
        for x in range(16):
            k = 3.4 + blot[y][x] + (base[y][x] - 0.5) * 1.1
            if rock in ("limestone", "slate", "ochre", "siltstone"):
                period = {"limestone": 5.3, "slate": 3.2, "ochre": 4.1, "siltstone": 2.7}[rock]
                k += math.sin((y + math.sin(x * 0.4 + band_phase) * 0.6) * 2 * math.pi / period) * (1.1 if rock != "limestone" else 0.6)
            if rock == "limestone" and y % 8 == 3:
                k -= 1.3
            c.set(x, y, R[max(1, min(6, int(round(k))))])
    if rock == "rootstone":
        # one old root threading through, a step darker than the rock (not black), its lit upper edge; pale grit
        x, y = r.randrange(16), 0
        for _ in range(22):
            c.set(x % 16, y % 16, R[1])
            if r.random() < 0.5:
                c.set(x % 16, (y - 1) % 16, R[4])
            x += r.choice((-1, 1, 1, 0))
            y += r.choice((0, 1)) if r.random() < 0.7 else 0
        for _ in range(4):
            c.set(r.randrange(16), r.randrange(16), R[6])
    if rock == "slate":
        for _ in range(6):
            c.set(r.randrange(16), r.randrange(16), H("#d8d2b8"))
    if rock == "glimmer":
        # the crystals grow as their own blocks; the host only glints: amber flecks, one catching the light
        for i in range(5):
            c.set(r.randrange(16), r.randrange(16), H("#f0b040") if i else H("#ffe08a"))
    if rock == "basalt":
        for y in range(16):
            for x in (0, 5, 11):
                xx = (x + (1 if (y // 4) % 2 else 0)) % 16
                c.set(xx, y, R[0])
        for x in range(16):
            if x % 5 != 0:
                c.set(x, 7, R[1])
    if rock == "seepstone":
        x = r.randint(2, 6)
        for y in range(16):
            x += r.choice((-1, 0, 1)) if y % 2 else 0
            c.set(x % 16, y, H("#9a62e0"))
            if r.random() < 0.3:
                c.set((x + 1) % 16, y, H("#d8b0ff"))
    return c


# ---- wall overlays ------------------------------------------------------------------------------------------------------
LICHEN = {
    "shallows": ["#3c4a2a", "#56683a", "#71854c", "#93a466", "#b6c48a"],
    "middle": ["#4a3a16", "#6e5620", "#94762e", "#b8983e", "#d8bc62"],
    "deep": ["#26263e", "#36385a", "#4a4e7a", "#64689a", "#8a8ebc"],
}


def lichen(band, seed=0):
    """Crust lichen in the band's colour, grown rather than stamped: each colony spreads cell by cell, its outer cells
    favoured so it reaches out in ragged lobes; the growing rim is pale, the old heart dark and cracked, with a few dark
    fruiting dots; bare rock shows between colonies (glow lichen's shape is vanilla's; this is flat crust)."""
    r = rng("lichen", band, seed)
    L = [H(c) for c in LICHEN[band]]
    c = Canvas(16, 16)
    owner = {}
    for colony in range(r.randint(4, 5)):
        sx, sy = r.randrange(16), r.randrange(16)
        if (sx, sy) in owner:
            continue
        cells = {(sx, sy): 0}
        front = [(sx, sy)]
        for step in range(1, r.randint(18, 34)):
            # pick a cell to grow from, favouring the newest (the lobes' tips)
            fx, fy = front[min(len(front) - 1, int(len(front) * (1 - r.random() ** 2.2)))]
            dx, dy = r.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
            n = ((fx + dx) % 16, (fy + dy) % 16)
            if n in cells or n in owner:
                continue
            cells[n] = step
            front.append(n)
        for cell, age in cells.items():
            owner[cell] = colony
    for (x, y), col in owner.items():
        nb = sum(((x + dx) % 16, (y + dy) % 16) in owner for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        k = 3 if nb <= 2 else 2 if nb == 3 else 1
        if nb == 4 and r.random() < 0.18:
            k = 0  # a crack in the old heart
        if k == 3 and r.random() < 0.35:
            k = 4  # the growing rim, pale
        c.set(x, y, L[k])
    for _ in range(r.randint(3, 5)):
        x, y = r.randrange(16), r.randrange(16)
        if (x, y) in owner:
            c.set(x, y, L[0])
    for _ in range(8):
        x, y = r.randrange(16), r.randrange(16)
        if (x, y) not in owner:
            c.set(x, y, (*L[2][:3], 200))
    return c


def mineral_crust(seed=0):
    """Efflorescence: white salts crept out of the rock in branching fronds, cream at their roots, a few crystals that
    catch your light."""
    r = rng("crust", seed)
    W = [H("#a69c86"), H("#cfc6b0"), H("#e8e2d2"), H("#fbf8ee")]
    c = Canvas(16, 16)
    for _ in range(r.randint(4, 6)):
        x, y = r.randrange(16), r.randrange(16)
        for step in range(r.randint(9, 16)):
            c.set(x % 16, y % 16, W[min(3, 1 + step // 5)])
            if r.random() < 0.35:
                c.set((x + r.choice((-1, 1))) % 16, y % 16, W[min(3, step // 4)])
            x += r.choice((-1, 0, 1))
            y += r.choice((-1, 0, 1))
    for _ in range(3):
        x, y = r.randrange(16), r.randrange(16)
        if c.get(x, y)[3]:
            c.set(x, y, H("#ffffff"))
    return c


def wet_streaks(seed=0):
    """Water creeping down the wall: dark runs from the roof that fade as they go, each with a bead that catches light."""
    r = rng("wet", seed)
    c = Canvas(16, 16)
    for _ in range(r.randint(5, 7)):
        x = r.randrange(16)
        y0 = r.randint(-4, 4)
        n = r.randint(6, 16)
        for i in range(n):
            y = y0 + i
            if 0 <= y < 16:
                a = int(120 * (1 - i / n) + 30)
                c.set(x, y, (16, 22, 30, a))
                if r.random() < 0.18:
                    x = (x + r.choice((-1, 1))) % 16
        by = y0 + n - 1
        if 0 <= by < 16:
            c.set(x, by, (214, 232, 244, 200))
    return c


# ---- the regions' own (P2): ore twins, the Rootways' growth, the Ochre Deep's walls ---------------------------------------
# an ore's mineral in five steps, rim to glint, and how its lumps sit in the rock. The Ochre Deep's iron is hematite: dark
# steel-red lumps with the peach rim iron ore always has, so it reads as iron and still stands out on the orange rock.
ORE = {
    "coal": ["#3a3434", "#151314", "#221f20", "#353132", "#9a9496"],
    "copper": ["#7fc0a4", "#8c4a24", "#b8642e", "#e08a50", "#ffd0a0"],     # the rim a verdigris bloom
    "iron": ["#e8bc94", "#3a2622", "#563830", "#7a5a52", "#e6e2dc"],
}


def ore_twin(rock, ore, seed=0):
    """An ore that lives in a region, with that region's rock behind it: four or five lumps of the mineral grown into the
    rock (not dots stamped on), each with a rim where it meets the rock (coal's a smear of soot, copper's green bloom,
    iron's peach), lit along its upper left, a glint on the best."""
    r = rng("ore", rock, ore, seed)
    c = region_rock(rock, seed)
    M = [H(x) for x in ORE[ore]]
    placed = []
    for lump in range(r.randint(4, 5)):
        for _ in range(30):
            cx, cy = r.uniform(1, 15), r.uniform(1, 15)
            if all(abs(tor(cx - px)) + abs(tor(cy - py)) > 5 for px, py in placed):
                break
        placed.append((cx, cy))
        rx, ry = r.uniform(1.4, 2.3), r.uniform(1.2, 1.9)
        cells = []
        for y in range(16):
            for x in range(16):
                dx, dy = tor(x + 0.5 - cx) / rx, tor(y + 0.5 - cy) / ry
                d = dx * dx + dy * dy + (r.random() - 0.5) * 0.4
                if d < 1.35:
                    cells.append((x, y, d, dx + dy))
        for x, y, d, lit in cells:
            if d >= 1.0:
                if r.random() < 0.55:
                    c.set(x, y, mix(c.get(x, y), M[0], 0.55))   # the rim, half into the rock
            else:
                c.set(x, y, M[3] if lit < -0.6 else M[2] if lit < 0.4 else M[1])
        if cells:
            x, y = min(cells, key=lambda t: t[3])[:2]
            c.set(x, y, M[4])
    return c


def glowcap(variant, glow=False):
    """The Rootways' glowcaps, a little cluster seen side-on (a cross): thin pale stems, round domed caps dark at the crown
    and pale teal at the rim, the gills under the rim lit and a few spots on top; the glow layer is only the lit parts,
    so they shine in the dark while the rest takes the cave's light."""
    r = rng("glowcap", variant)
    c = Canvas(16, 16)
    stems = ([(5, 10, 4), (11, 7, 3)], [(4, 8, 3), (9, 11, 4), (13, 6, 2)], [(6, 12, 4), (11, 9, 3), (3, 5, 2)])[variant]
    for sx, h, cap in stems:
        top = 16 - h
        lean = r.choice((-1, 1)) if h > 6 else 0
        if not glow:
            for y in range(top + 1, 16):
                x = sx + (lean if y < top + h // 2 else 0)
                c.set(x, y, H("#cfd6c4") if y < 15 else H("#8c9484"))
        sx += lean
        ch = cap // 2 + 1
        shade = r.uniform(-0.1, 0.1)
        for k in range(ch):                                    # the dome, top row first
            t = (ch - 1 - k + 0.5) / ch
            w = max(1, round(cap * math.sqrt(max(0.0, 1 - t * t))))
            y = top - ch + 1 + k
            for dx in range(-w, w + 1):
                rim = k == ch - 1
                edge = abs(dx) == w
                if glow:
                    if rim or (k > 0 and not edge and r.random() < 0.18):
                        c.set(sx + dx, y, H("#a6fff0") if rim else H("#5fe6d2"))
                else:
                    base = H("#47c4b2") if rim else H("#1b5550") if k == 0 else H("#25786e") if edge else H("#2e8c80")
                    c.set(sx + dx, y, mix(base, (0, 0, 0) if shade < 0 else (255, 255, 255), abs(shade)))
        # the gills: a lit line under the rim, a pixel narrower each side
        for dx in range(-cap + 1, cap):
            if dx != 0:
                c.set(sx + dx, top + 1, H("#7ef2de") if glow else H("#5fd0be"))
    return c


def root_curtain(tip=False, seed=0):
    """Old roots hanging through the roof: three or four strands wandering down with clear gaps between, dark on one
    side and lit on the other, fine pale rootlets off them; the tip's strands end at different heights in fraying hairs."""
    r = rng("roots", tip, seed)
    c = Canvas(16, 16)
    D, M, L, P = H("#2e1c10"), H("#5a3a22"), H("#86603e"), H("#b8a080")
    xs = [2, 6, 10, 13]
    r.shuffle(xs)
    for x in xs[: r.randint(3, 4)]:
        x += r.choice((-1, 0, 1))
        end = r.randint(5, 13) if tip else 16
        for y in range(0, end):
            c.set(x, y, M)
            c.set(x - 1, y, D)
            if y % 5 != 2:
                c.set(x + 1, y, L) if r.random() < 0.5 else None
            if r.random() < 0.1 and y < end - 2:
                dx = r.choice((-1, 1))
                for k in range(1, r.randint(2, 4)):
                    c.set(x + dx * (k + 1), y + k, (*P[:3], 190))
            if r.random() < 0.14:
                x = max(1, min(14, x + r.choice((-1, 1))))
        if tip:
            for k in range(1, r.randint(2, 4)):
                c.set(x + r.choice((-1, 0, 1)), end + k - 1, (*P[:3], 170))
    return c


def rust_crust(seed=0):
    """Rust crust: iron-stained crust crept over the rock in blistered patches, dark at its heart, bright orange at its
    fresh edge, a few black specks of manganese; about two fifths of the face, so the rock still shows."""
    r = rng("rust", seed)
    W = [H("#3e170a"), H("#6e2a10"), H("#9a4218"), H("#c4622a"), H("#e8944e")]
    c = Canvas(16, 16)
    blobs = [(r.uniform(0, 16), r.uniform(0, 16), r.uniform(2.2, 3.8)) for _ in range(r.randint(3, 4))]
    for y in range(16):
        for x in range(16):
            best = 9.0
            for bx, by, br in blobs:
                d = math.hypot(tor(x + 0.5 - bx), tor(y + 0.5 - by)) / br
                best = min(best, d + (r.random() - 0.5) * 0.3)
            if best < 1.0:
                k = 4 if best > 0.82 else 3 if best > 0.6 else 2 if best > 0.3 else 1
                if k < 4 and r.random() < 0.12:
                    k = 0                                          # a blister's pit
                c.set(x, y, W[k])
    for _ in range(4):
        x, y = r.randrange(16), r.randrange(16)
        if c.get(x, y)[3]:
            c.set(x, y, H("#140a06"))
    return c


def rust_flowstone(seed=0):
    """Rust flowstone: iron-rich water has run down the wall for ages and left orange drapery, curtains of it in rounded
    ridges (dark, mid, lit across each), banded where it grew in seasons, ending in points at different heights."""
    r = rng("flow", seed)
    F = [H("#5a220c"), H("#8a3c16"), H("#b65e28"), H("#da8644"), H("#f6c48c")]
    c = Canvas(16, 16)
    x = r.randint(0, 1)
    while x < 16:
        w = r.randint(2, 4)
        length = r.randint(8, 16)
        for y in range(length):
            taper = max(0, y - (length - 3))                       # the last rows narrow to a point
            band = 1 if y % 4 == 3 else 0
            for k in range(taper // 2, w - (taper + 1) // 2):
                across = 0 if k == 0 else 3 if k == w - 1 else 2
                col = F[max(0, across - band)]
                c.set(x + k, y, (*col[:3], 240))
            if r.random() < 0.08:
                c.set(x + w - 1, y, F[4])
        x += w + r.choice((0, 1, 1, 2))
    return c


def ember_lichen(glow=False, seed=0):
    """Ember lichen: a dark rusty crust lichen whose fruiting dots glow like embers in a dying fire, a dozen or more, some
    in pairs; the glow layer is only the embers and their red halo, so they light up in the dark while the crust takes
    the cave's light."""
    r = rng("ember", seed)
    base = lichen("middle", seed + 7)
    c = Canvas(16, 16)
    if not glow:
        for y in range(16):
            for x in range(16):
                px = base.get(x, y)
                if px[3]:
                    c.set(x, y, mix(px, H("#3a1a0c"), 0.6))
    spots = [(x, y) for y in range(16) for x in range(16) if base.get(x, y)[3]]
    r.shuffle(spots)
    for x, y in spots[: r.randint(13, 17)]:
        for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1)):
            if base.get(x + dx, y + dy)[3] and r.random() < 0.5:
                c.set(x + dx, y + dy, H("#8a2008"))           # the halo, a dull red
        c.set(x, y, H("#ffd27a") if r.random() < 0.3 else H("#ff7a24"))
    return c


# ---- puddles and webs ---------------------------------------------------------------------------------------------------

def puddle_frames(n=16, seed=0):
    """A shallow skin of water on the floor, grey so the cave's water colour tints it (the Ochre Deep's runs orange): an
    irregular pool well inside the block (an outline of wandering lobes round a middle, a small satellite beside it), a
    darker deep, a bright shallow edge, a wet dark rim on the rock around it, and now and then a ring from a drip
    spreading out and fading."""
    r = rng("puddle", seed)
    cx, cy = r.uniform(7.2, 8.8), r.uniform(7.2, 8.8)
    harm = [(r.uniform(0.5, 1.1), r.uniform(0, 6.28), k) for k in (2, 3, 5)]

    def radius(a):
        return 5.2 + sum(amp * math.sin(k * a + ph) for amp, ph, k in harm) * 0.8

    sat = (cx + r.choice((-1, 1)) * r.uniform(4.2, 5.0), cy + r.choice((-1, 1)) * r.uniform(3.6, 4.6), r.uniform(1.4, 2.0))

    def depth(x, y):
        a = math.atan2(y - cy, x - cx)
        d = 1 - math.hypot(x - cx, y - cy) / min(6.6, radius(a))
        d2 = 1 - math.hypot(x - sat[0], y - sat[1]) / sat[2]
        return max(d, d2 * 0.6)

    rings = [(cx + r.uniform(-1.5, 1.5), cy + r.uniform(-1.5, 1.5), n // 2)]
    frames = []
    for f in range(n):
        c = Canvas(16, 16)
        for y in range(1, 15):
            for x in range(1, 15):
                v = depth(x + 0.5, y + 0.5)
                if v <= -0.16:
                    continue
                if v <= 0:
                    c.set(x, y, (40, 40, 40, 70))  # the wet rim on the rock
                    continue
                g = 92 if v > 0.5 else 116 if v > 0.22 else 156
                a = 225 if v > 0.5 else 205 if v > 0.22 else 175
                g += int(7 * math.sin((x * 0.7 + y * 0.4) + f * 2 * math.pi / n))
                for rx, ry, t0 in rings:
                    age = (f - t0) % n
                    rad = age * 0.5
                    if age < 7 and abs(math.hypot(x + 0.5 - rx, y + 0.5 - ry) - rad) < 0.5:
                        g, a = 196, min(255, a + 30 - age * 3)
                c.set(x, y, (g, g, g, a))
        frames.append(c)
    return frames


def dusty_web(seed=0):
    """An old web in a dry dead end: a few spokes from a hub high in one corner, threads sagging between them and
    broken in places, gone grey with dust; a clump or two of grit, beads of dew that catch your light (vanilla's cobweb
    is a clean white tangle)."""
    r = rng("web", seed)
    c = Canvas(16, 16)
    T, D, B = (186, 180, 166, 190), (128, 120, 106, 230), (236, 246, 255, 255)
    hub = (r.uniform(3, 5), r.uniform(2.5, 4.5))
    spokes = [math.radians(a + r.uniform(-8, 8)) for a in (-5, 40, 95)]
    ends = []
    for a in spokes:
        for i in range(30):
            x, y = hub[0] + math.cos(a) * i * 0.55, hub[1] + math.sin(a) * i * 0.55
            if not (0 <= x < 16 and 0 <= y < 16):
                break
            c.set(int(x), int(y), T)
        ends.append(a)
    for ring in (3.4, 7.2, 11.8):
        for j in range(len(spokes) - 1):
            if r.random() < 0.25:
                continue  # broken
            a0, a1 = spokes[j], spokes[j + 1]
            for k in range(25):
                t = k / 24
                a = a0 + (a1 - a0) * t
                sag = math.sin(t * math.pi) * (0.5 + ring * 0.09)
                x, y = hub[0] + math.cos(a) * ring, hub[1] + math.sin(a) * ring + sag
                if 0 <= x < 16 and 0 <= y < 16:
                    c.set(int(x), int(y), T)
    for _ in range(2):
        x, y = r.randrange(16), r.randrange(16)
        if c.get(x, y)[3]:
            c.set(x, y, D)
            c.set(min(15, x + 1), y, D)
    n = 0
    for _ in range(40):
        x, y = r.randrange(16), r.randrange(16)
        if c.get(x, y)[3] and n < 4:
            c.set(x, y, B)
            n += 1
    return c


# ---- particles ----------------------------------------------------------------------------------------------------------

def drip_frames():
    """A drop gathering and falling: round, then long as it falls."""
    out = []
    for h in (2, 3, 4, 4):
        c = Canvas(8, 8)
        for y in range(h):
            for x in (3, 4):
                edge = y == 0 or x == 4
                c.set(x, 2 + y, (150, 192, 218, 220) if edge else (214, 236, 248, 255))
        c.set(3, 2 + h, (180, 214, 236, 160))
        out.append(c)
    return out


def splash_frames():
    """A crown where a drop lands: four beads thrown up and out, falling back."""
    out = []
    for f in range(4):
        c = Canvas(8, 8)
        spread, lift = 1 + f, (2, 3, 2, 1)[f]
        for sx in (-1, 1):
            for k in (1, 2):
                x = 3.5 + sx * (spread * k / 2)
                y = 6 - lift * (1 if k == 1 else 0.6)
                c.set(int(x), int(y), (206, 230, 246, 230 - f * 40))
        out.append(c)
    return out


def mote(colour, size, seed=0):
    """A soft speck of dust or spore: a bright heart, fading out."""
    c = Canvas(8, 8)
    col = H(colour)
    for y in range(8):
        for x in range(8):
            d = math.hypot(x + 0.5 - 4, y + 0.5 - 4) / size
            if d < 1:
                c.set(x, y, (*col[:3], int(255 * (1 - d) ** 1.4)))
    return c


def spore(seed=0):
    """A Rootways spore: a pale gold-green fuzz with a bright centre, drifting."""
    r = rng("spore", seed)
    c = Canvas(8, 8)
    for _ in range(9):
        a, d = r.uniform(0, 2 * math.pi), r.uniform(0.8, 2.4)
        c.set(int(4 + math.cos(a) * d), int(4 + math.sin(a) * d), (196, 214, 140, 150))
    c.set(4, 4, (240, 246, 200, 255))
    c.set(3, 4, (220, 232, 168, 220))
    return c


def pollen(seed=0):
    """Rootways pollen: two or three gold grains drifting together."""
    r = rng("pollen", seed)
    c = Canvas(8, 8)
    for _ in range(r.randint(2, 3)):
        x, y = r.randint(2, 5), r.randint(2, 5)
        c.set(x, y, (236, 206, 110, 255))
        c.set(x + 1, y, (196, 160, 70, 180))
    return c


def mist(seed=0):
    """A wisp of mist low over still water: long and soft, lighter in the middle, never a round puff."""
    r = rng("mist", seed)
    c = Canvas(16, 16)
    ph = r.uniform(0, 6.28)
    for y in range(4, 12):
        for x in range(16):
            mid = 8 + math.sin(x * 0.45 + ph) * 0.9
            d = abs(y + 0.5 - mid) / 2.6 + abs(x + 0.5 - 8) / 9
            if d < 1:
                c.set(x, y, (232, 240, 244, int(120 * (1 - d) ** 1.6)))
    return c


def glint(colour, arms, seed=0):
    """A glint (mica in the Windcleft's slate, glitter in the Glimmer Hollows): a white heart, arms of the colour, a soft
    halo; three sizes make a twinkle."""
    col = H(colour)
    c = Canvas(8, 8)
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5)
            if d < 2.2:
                c.set(x, y, (*col[:3], int(70 * (1 - d / 2.2))))
    for k in range(1, arms + 1):
        a = int(230 - 60 * k)
        for x, y in ((3 - k, 3), (4 + k, 4), (3, 3 - k), (4, 4 + k)):
            if 0 <= x < 8 and 0 <= y < 8:
                c.set(x, y, (*col[:3], a))
    for x, y in ((3, 3), (4, 3), (3, 4), (4, 4)):
        c.set(x, y, (255, 255, 255, 255) if (x, y) == (3, 3) else (*col[:3], 240))
    return c


def ash(seed=0):
    """A flake of ash from the Black Vaults: a grey scrap, curled, one in three still has an ember at its edge."""
    r = rng("ash", seed)
    c = Canvas(8, 8)
    x, y = 3, 3
    for _ in range(r.randint(4, 6)):
        c.set(x, y, (*H(r.choice(("#6e6a66", "#8a8480", "#a29c96")))[:3], 230))
        x += r.choice((-1, 0, 1))
        y += r.choice((0, 1)) if r.random() < 0.6 else 0
        x, y = max(1, min(6, x)), max(1, min(6, y))
    if seed % 3 == 0:
        c.set(x, y, (255, 150, 60, 255))
    return c


def seep(seed=0):
    """The Violet Deep's seep: a violet bead rising, a pale heart and a thin bright ring."""
    c = Canvas(8, 8)
    rad = 2.0 + seed * 0.5
    for y in range(8):
        for x in range(8):
            d = math.hypot(x + 0.5 - 4, y + 0.5 - 4)
            if d < rad:
                edge = d > rad - 0.8
                c.set(x, y, (216, 176, 255, 230) if edge else (150, 98, 224, 110))
    c.set(3, 3, (250, 236, 255, 255))
    return c


PARTICLES = {
    "warm_dust": "#e8a050",   # the Ochre Deep
    "mist": "#d8e4ea",        # the Pale Halls and the Drowned Galleries, low over water
    "mica": "#fff4c8",        # the Windcleft's glints
    "glitter": "#c8ecff",     # the Glimmer Hollows
    "ash": "#8a8480",         # the Black Vaults
    "seep": "#b884ff",        # the Violet Deep
}


def build(out):
    for rock in SKIN_ROCKS + ("rootstone", "ochre"):
        out.block(f"caves/{rock}_rubble", rubble(rock))
        out.block(f"caves/{rock}_formation", formation(rock))
        out.block(f"caves/{rock}_formation_tip", formation(rock, tip=True))
    for rock in ("rootstone", "ochre"):
        out.block(f"caves/{rock}" if rock == "rootstone" else "caves/ochre_stone", region_rock(rock))
    for rock, ore, name in TWINS:
        out.block(f"caves/{name}", ore_twin(rock, ore))
    for v in range(3):
        out.block(f"caves/glowcap_{v}", glowcap(v))
        out.block(f"caves/glowcap_{v}_glow", glowcap(v, glow=True))
    out.block("caves/root_curtain", root_curtain())
    out.block("caves/root_curtain_tip", root_curtain(tip=True))
    out.block("caves/rust_crust", rust_crust())
    out.block("caves/rust_flowstone", rust_flowstone())
    out.block("caves/ember_lichen", ember_lichen())
    out.block("caves/ember_lichen_glow", ember_lichen(glow=True))
    for band in LICHEN:
        out.block(f"caves/lichen_{band}", lichen(band))
    out.block("caves/mineral_crust", mineral_crust())
    out.block("caves/wet_streaks", wet_streaks())
    out.block("caves/puddle", puddle_frames(), frametime=4)
    out.block("caves/dusty_web", dusty_web())
    for i, f in enumerate(drip_frames()):
        out.particle(f"caves/drip_{i}", f)
    for i, f in enumerate(splash_frames()):
        out.particle(f"caves/splash_{i}", f)
    for i, s in enumerate((1.4, 2.0, 2.6)):
        out.particle(f"caves/mote_{i}", mote("#d6d2c8", s))
    for i in range(3):
        out.particle(f"caves/spore_{i}", spore(i))
    for i, s in enumerate((1.4, 2.2)):
        out.particle(f"caves/warm_dust_{i}", mote(PARTICLES["warm_dust"], s))
    for i in range(3):
        out.particle(f"caves/pollen_{i}", pollen(i))
        out.particle(f"caves/ash_{i}", ash(i))
    for i in range(2):
        out.particle(f"caves/mist_{i}", mist(i))
        out.particle(f"caves/seep_{i}", seep(i))
    for i in range(3):
        out.particle(f"caves/mica_{i}", glint(PARTICLES["mica"], i + 1))
        out.particle(f"caves/glitter_{i}", glint(PARTICLES["glitter"], i + 1))
    for rock in ROCKS:
        if rock not in SKIN_ROCKS + ("rootstone", "ochre"):
            out.block(f"caves/{rock}", region_rock(rock))  # P3's rocks, on the style sheet now


# ---- models -------------------------------------------------------------------------------------------------------------
# The skin's shapes in Minecraft's model format (elements in pixels, 16 a block). Formations are real tapered columns, a few
# boxes each, so they catch light on their sides; rubble is a low heap of fragments of the rock on a bed of broken rock.
ROCK_TEXTURE = {"stone": "minecraft:block/stone", "deepslate": "minecraft:block/deepslate", "tuff": "minecraft:block/tuff",
                "rootstone": "awaken:block/caves/rootstone", "ochre": "awaken:block/caves/ochre_stone"}
# boxes (x0, y0, x1, y1) of a stalagmite's four thicknesses, x the half-width round the column's middle
FORMATION = {
    "base": [(6.5, 0, 3), (6.0, 3, 6), (5.5, 6, 9), (5.0, 9, 12), (4.5, 12, 16)],
    "middle": [(4.0, 0, 4), (3.75, 4, 8), (3.5, 8, 12), (3.25, 12, 16)],
    "frustum": [(3.0, 0, 3), (2.5, 3, 6), (2.0, 6, 9), (1.5, 9, 12), (1.25, 12, 16)],
    "tip": [(1.0, 0, 4), (0.75, 4, 8), (0.5, 8, 12)],
}
THICKNESSES = ("tip", "frustum", "middle", "base")


def box(frm, to, tex, faces=("north", "south", "east", "west", "up", "down"), rot=None, tint=None):
    """An element, its faces' UVs from its own position (as the game does when a face gives none)."""
    (x0, y0, z0), (x1, y1, z1) = frm, to
    uv = {"north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
          "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
          "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0]}
    e = {"from": list(frm), "to": list(to), "faces": {}}
    for f in faces:
        e["faces"][f] = {"uv": [round(v, 3) for v in uv[f]], "texture": tex}
        if tint is not None:
            e["faces"][f]["tintindex"] = tint
    if rot:
        e["rotation"] = rot
    return e


def formation_model(rock, direction, thickness):
    els = []
    for i, (half, y0, y1) in enumerate(FORMATION[thickness]):
        tex = "#tip" if thickness == "tip" and i == len(FORMATION[thickness]) - 1 else "#side"
        if direction == "down":
            y0, y1 = 16 - y1, 16 - y0
        els.append(box((8 - half, y0, 8 - half), (8 + half, y1, 8 + half), tex))
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": f"awaken:block/caves/{rock}_formation", "side": f"awaken:block/caves/{rock}_formation",
                         "tip": f"awaken:block/caves/{rock}_formation_tip"}, "elements": els}


def rubble_model(rock, variant):
    """A low heap, not a slab: a bed of broken rock in two tiers, rising toward the wall (the model's north), with five or
    six fragments of the rock lying on it at odd angles."""
    r = rng("rubble_model", rock, variant)
    els = [box((0.5, 0, 0.5), (15.5, 1.5, 15.5), "#bed"), box((1.5, 1.5, 0.5), (14.5, 3, 10), "#bed"),
           box((3, 3, 0.5), (13, 4.5, 5), "#bed")]
    placed = []
    for _ in range(60):
        if len(placed) >= r.randint(5, 6):
            break
        w, d, h = r.uniform(3, 6), r.uniform(3, 5.5), r.choice((2, 2.5, 3, 3.5))
        x, z = r.uniform(1, 15 - w), r.uniform(0.5, 15 - d)
        if any(abs(x + w / 2 - px) < (w + pw) / 2 - 0.5 and abs(z + d / 2 - pz) < (d + pd) / 2 - 0.5 for px, pz, pw, pd in placed):
            continue
        placed.append((x + w / 2, z + d / 2, w, d))
        foot = 4.5 if z + d < 5 else 3 if z + d < 10 else 1.5
        rot = {"angle": r.choice((-22.5, 0, 22.5)), "axis": "y", "origin": [round(x + w / 2, 2), foot, round(z + d / 2, 2)]}
        els.append(box((round(x, 2), foot - 1, round(z, 2)), (round(x + w, 2), round(foot - 1 + h, 2), round(z + d, 2)), "#chunk",
                       rot=rot if rot["angle"] else None))
    return {"parent": "minecraft:block/block", "textures": {"particle": f"awaken:block/caves/{rock}_rubble",
            "bed": f"awaken:block/caves/{rock}_rubble", "chunk": ROCK_TEXTURE[rock]}, "elements": els}


def pebbles_model(rock, variant):
    r = rng("pebbles_model", rock, variant)
    els = []
    for _ in range(r.randint(2, 3)):
        w, d, h = r.choice((2, 2.5, 3)), r.choice((1.5, 2, 2.5)), r.choice((1, 1.5, 2))
        x, z = r.uniform(2, 13 - w), r.uniform(2, 13 - d)
        els.append(box((round(x, 2), 0, round(z, 2)), (round(x + w, 2), h, round(z + d, 2)), "#t",
                       rot={"angle": r.choice((-22.5, 22.5, 0)) or 22.5, "axis": "y", "origin": [round(x + w / 2, 2), 0, round(z + d / 2, 2)]}))
    return {"parent": "minecraft:block/block", "textures": {"particle": ROCK_TEXTURE[rock], "t": ROCK_TEXTURE[rock]}, "elements": els}


def overlay_model(name, glow=0):
    """A thin overlay on a wall (multiface); with {@code glow}, a second layer of its glowing parts at that light."""
    m = {"ambientocclusion": False, "render_type": "minecraft:cutout", "textures": {"particle": f"awaken:block/caves/{name}", "t": f"awaken:block/caves/{name}"},
         "elements": [{"from": [0, 0, 0.1], "to": [16, 16, 0.1],
                       "faces": {"north": {"uv": [16, 0, 0, 16], "texture": "#t"}, "south": {"uv": [0, 0, 16, 16], "texture": "#t"}}}]}
    if glow:
        m["textures"]["g"] = f"awaken:block/caves/{name}_glow"
        m["elements"].append({"from": [0, 0, 0.1], "to": [16, 16, 0.1], "light_emission": glow,
                              "faces": {"north": {"uv": [16, 0, 0, 16], "texture": "#g"}, "south": {"uv": [0, 0, 16, 16], "texture": "#g"}}})
    return m


def cross_model(tex, glow_tex=None, glow=0):
    """Two crossed planes (as minecraft:block/cross), and the same again in the glowing parts' texture at that light."""
    def planes(t, extra):
        rot = {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}
        face = {"uv": [0, 0, 16, 16], "texture": t}
        return [{"from": [0.8, 0, 8], "to": [15.2, 16, 8], "rotation": rot, "shade_direction_override": "up", **extra,
                 "faces": {"north": face, "south": face}},
                {"from": [8, 0, 0.8], "to": [8, 16, 15.2], "rotation": rot, "shade_direction_override": "up", **extra,
                 "faces": {"west": face, "east": face}}]
    m = {"ambientocclusion": False, "render_type": "minecraft:cutout", "textures": {"particle": tex, "t": tex}, "elements": planes("#t", {})}
    if glow_tex:
        m["textures"]["g"] = glow_tex
        m["elements"] += planes("#g", {"light_emission": glow})
    return m


def model_dict():
    out = {}
    for rock in SKIN_ROCKS + ("rootstone", "ochre"):
        for d in ("up", "down"):
            for t in THICKNESSES:
                out[f"{rock}_formation_{d}_{t}"] = formation_model(rock, d, t)
        for v in range(3):
            out[f"{rock}_rubble_{v}"] = rubble_model(rock, v)
            out[f"{rock}_pebbles_{v}"] = pebbles_model(rock, v)
    for name in ["lichen_" + b for b in LICHEN] + ["mineral_crust", "wet_streaks", "rust_crust", "rust_flowstone"]:
        out[name] = overlay_model(name)
    out["ember_lichen"] = overlay_model("ember_lichen", glow=9)
    for v in range(3):
        out[f"glowcap_{v}"] = cross_model(f"awaken:block/caves/glowcap_{v}", f"awaken:block/caves/glowcap_{v}_glow", glow=12)
    for t in ("root_curtain", "root_curtain_tip"):
        out[t] = cross_model(f"awaken:block/caves/{t}")
    for rock in ("rootstone", "ochre_stone") + tuple(t[2] for t in TWINS):
        out[rock] = {"parent": "minecraft:block/cube_all", "textures": {"all": f"awaken:block/caves/{rock}"}}
    out["puddle"] = {"ambientocclusion": False, "render_type": "minecraft:translucent", "textures": {"particle": "awaken:block/caves/puddle", "t": "awaken:block/caves/puddle"},
                     "elements": [box((0, 0.2, 0), (16, 0.2, 16), "#t", faces=("up",), tint=0)]}
    out["dusty_web"] = {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": "awaken:block/caves/dusty_web"}}
    return out


def models():
    out = model_dict()
    for name, m in out.items():
        path = os.path.join(ASSETS, "models/block/caves", name + ".json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            json.dump(m, f, indent=1)
    print(len(out), "models")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "models":
        models()
    else:
        print(__doc__)
