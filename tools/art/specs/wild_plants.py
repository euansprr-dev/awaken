"""The wild's own plants (SPEC §41, the Wild Lands, stage 1a): 16x16 sprites in Minecraft's own manner.

Every plant is two layers. The blades are grey (the shades of vanilla's own grass, 100..190) and take the biome's colour the
way the game's grass does, so every biome and the lawn drift colour them. The overlay holds what keeps its own colour (seed
heads, flowers, berries, canes, frost); the block models lay it on the same planes as the blades, the way vanilla lays a grass
block's side overlay, so no pixel is tinted that should not be. Three or four versions of each, picked per block.

  textures/block/wild/<plant>[_<part>][_<look>]_<n>.png and ..._overlay.png (the overlay only where a plant has one)

Run: python3 tools/art/build_all.py wild_plants        (writes the textures)
     python3 tools/art/specs/wild_plants.py sheet <dir> (the style sheet's pictures: every sprite x8 in three biome tints)
"""
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import Canvas  # noqa: E402

# ---- palette ------------------------------------------------------------------------------------------------------------
# the blades' greys: vanilla short grass runs 108..184 with a mean near 146; long grass's stems sit darker, its tips lighter
STEM = (100, 110, 120, 130, 140)
BLADE = (118, 132, 146, 160, 174)
TIP = (150, 162, 174, 186, 196)
STRAW = [(222, 200, 132), (204, 178, 106), (236, 220, 160), (186, 156, 90)]
GOLD = [(226, 186, 92), (208, 162, 66), (242, 210, 126), (188, 140, 58)]
TAWNY = [(190, 136, 76), (164, 112, 60), (210, 162, 98), (146, 98, 52)]
FROST = [(236, 244, 250), (212, 228, 240), (250, 252, 255)]
GLASS = [(206, 236, 244), (160, 214, 232), (238, 250, 255), (128, 186, 214)]


def rng(*parts):
    return random.Random("|".join(str(p) for p in ("wild",) + parts))


def g(v):
    v = max(0, min(255, int(v)))
    return (v, v, v, 255)


class Pair:
    """A plant sprite: the grey layer the biome tints, and the overlay that keeps its own colours."""

    def __init__(self):
        self.t = Canvas(16, 16)
        self.u = Canvas(16, 16)

    def tint(self, x, y, v):
        if 0 <= x < 16 and 0 <= y < 16:
            self.t.set(x, y, g(v))

    def own(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.u.set(x, y, c if len(c) == 4 else (*c, 255))
            self.t.set(x, y, (0, 0, 0, 0))        # the overlay owns this pixel: no tinted pixel under it

    def has_overlay(self):
        return any(p[3] for row in self.u.px for p in row)


def blade(p, r, x0, y0, length, lean, shades=BLADE, gaps=0.0, bend=2.0, own=None):
    """One blade from (x0, y0) up `length` pixels, drifting `lean` pixels sideways (more toward its tip: `bend` > 1).
    Darker at the root, lighter toward the tip, a pixel's jitter of shade. Returns its pixels, root first."""
    pts = []
    for i in range(length):
        f = i / max(1, length - 1)
        x = int(round(x0 + lean * f ** bend))
        y = y0 - i
        if y < 0:
            break
        if gaps and 1 < i < length - 1 and r.random() < gaps:
            pts.append((x, y))
            continue
        s = shades[min(len(shades) - 1, int(f * len(shades)))] + r.randint(-5, 5)
        if own:
            p.own(x, y, own[r.randrange(len(own))])
        else:
            p.tint(x, y, s)
        pts.append((x, y))
    return pts


def ellipse(cx, cy, rx, ry, x, y):
    return (x - cx) ** 2 / (rx * rx) + (y - cy) ** 2 / (ry * ry)


# ---- grasses ------------------------------------------------------------------------------------------------------------
def meadow_grass(n):
    """Knee-high meadow grass, one block: many fine blades, finer and taller than the game's short grass, a few straw tips.
    The base of every wild field."""
    r = rng("meadow", n)
    p = Pair()
    for k in range(15):
        x0 = 1 + (k * 7 + r.randint(0, 3)) % 14
        L = r.randint(6, 14)
        lean = r.choice((-3, -2, -1, 0, 1, 2, 3))
        pts = blade(p, r, x0, 15, L, lean, shades=(112, 124, 138, 152, 168))
        if pts and L >= 10 and r.random() < 0.28:
            x, y = pts[-1]
            p.own(x, y - 1, r.choice(STRAW))
            if r.random() < 0.5:
                p.own(x + (1 if lean >= 0 else -1), y - 2, r.choice(STRAW))
    # a dense foot, so a field of it reads as ground cover from a hill
    for x in range(1, 15):
        if r.random() < 0.7:
            p.tint(x, 15, 104 + r.randint(0, 10))
        if r.random() < 0.4:
            p.tint(x, 14, 110 + r.randint(0, 12))
    return p


def frost_grass(n):
    """Snowy land's low long grass: meadow grass with frost on the tips."""
    r = rng("frost", n)
    p = meadow_grass(n + 7)
    p.u = Canvas(16, 16)
    for x in range(16):
        top = next((y for y in range(16) if p.t.get(x, y)[3]), None)
        if top is not None and top < 12:
            p.own(x, top, r.choice(FROST))
            if r.random() < 0.5 and top + 1 < 15:
                p.own(x, top + 1, (220, 232, 238))
    return p


def long_bottom(n):
    """Long grass, the lower block: close stems, darker toward the ground, each its own shade. Stems reach the top edge so
    the upper block carries on whichever version it is."""
    r = rng("long_bottom", n)
    p = Pair()
    for k in range(11):
        x0 = (k * 3 + r.randint(0, 2)) % 16
        base = r.randint(-8, 8)
        lean = r.choice((-2, -1, 1, 2))
        for i in range(16):
            x = int(round(x0 + lean * (i / 15) ** 1.5))
            p.tint(x, 15 - i, 98 + base + i * 2.4 + r.randint(-3, 3))
    for k in range(5):                                   # short blades at the foot
        blade(p, r, r.randint(0, 15), 15, r.randint(4, 8), r.choice((-3, 3)), shades=(104, 114, 124, 132, 140))
    return p

def long_top(look, n):
    """Long grass, the upper block, in one of three looks: green, going to seed (gold heads), tawny and dry."""
    r = rng("long_top", look, n)
    p = Pair()
    heads = {"green": 0.18, "seeding": 0.75, "tawny": 0.35}[look]
    for k in range(15):
        x0 = 1 + (k * 5 + r.randint(0, 4)) % 14
        L = r.randint(5, 15)
        lean = r.choice((-5, -4, -2, -1, 1, 2, 4, 5))
        dry = look == "tawny" and r.random() < 0.55
        pts = blade(p, r, x0, 15, L, lean, shades=TIP, own=TAWNY if dry else None)
        if pts and L > 7 and r.random() < heads:
            x, y = pts[-1]
            cols = GOLD if look == "seeding" else STRAW
            d = 1 if lean >= 0 else -1
            for i in range(r.randint(2, 4)):
                p.own(x + (d if i % 2 else 0), y - 1 - i, r.choice(cols))
    return p


def rye_stems(n):
    """Wild rye, the bottom block: stiff close stems (it bends from its middle, so the foot stands still), a leaf or two."""
    r = rng("rye_stems", n)
    p = Pair()
    for k in range(9):
        x0 = (k * 4 + r.randint(0, 2)) % 16
        base = r.randint(-6, 6)
        for i in range(16):
            p.tint(x0, 15 - i, 96 + base + i * 1.6 + r.randint(-2, 2))
        if r.random() < 0.6:                             # a leaf leaving the stem, falling away
            y = r.randint(2, 9)
            d = r.choice((-1, 1))
            for j in range(1, 4):
                p.tint(x0 + d * j, y + (j // 2), 124 + j * 8)
    return p

def rye_middle(n):
    """Wild rye, the middle block: the stems, and long leaves arching off them."""
    r = rng("rye_middle", n)
    p = Pair()
    xs = []
    for k in range(9):
        x0 = (k * 4 + r.randint(0, 2)) % 16
        lean = r.choice((-1, 0, 0, 1))
        base = r.randint(-6, 6)
        for i in range(16):
            p.tint(x0 + int(round(lean * i / 15)), 15 - i, 118 + base + i * 1.8 + r.randint(-2, 2))
        xs.append(x0)
    for _ in range(4):
        x0, y0 = r.choice(xs), r.randint(5, 12)
        d = r.choice((-1, 1))
        for j in range(1, 6):                            # a leaf: up and out, then over
            p.tint(x0 + d * j, y0 - (2 if j < 3 else 3) + (1 if j == 5 else 0), 150 + j * 6)
    return p

def rye_top(look, n):
    """Wild rye, the top block: stems curving over into long nodding ears with whiskered awns, gold (on the Glass Steppe
    glassy, catching the light). Four or five ears, apart, so each reads on its own."""
    r = rng("rye_top", look, n)
    p = Pair()
    ear = GLASS if look == "glass" else GOLD
    awn = (250, 254, 255) if look == "glass" else (238, 222, 168)
    for k in range(5):                                   # leaves without ears, behind
        blade(p, r, 1 + (k * 3 + n) % 14, 15, r.randint(4, 8), r.choice((-4, -3, 3, 4)), shades=(136, 146, 156, 166, 176))
    starts = [(2, 1), (6, -1), (9, 1), (13, -1), (11, 1)][: 4 + n % 2]
    for k, (x0, d) in enumerate(starts):
        L = (9, 12, 7, 10, 5)[(k + n) % 5]
        pts = []
        for i in range(L):                               # the stem, bending over toward its tip
            f = i / (L - 1)
            x = int(round(x0 + d * 2 * f ** 2.5))
            pts.append((x, 15 - i))
            p.tint(x, 15 - i, 146 + i * 3)
        x, y = pts[-1]
        for i in range(5):                               # the ear: a two-wide spike hanging out and down from the tip
            hx, hy = x + d * (1 + i // 2), y + i
            p.own(hx, hy, ear[2] if i == 0 else ear[i % 2])
            p.own(hx + d, hy, ear[3] if i % 2 else ear[1])
            if i % 2 == 0:
                p.own(hx + 2 * d, hy - 1, awn)           # awns whisker out from the ear
    return p

def trampled(kind, n):
    """Grass pressed flat by a herd, lying along the way they went (drawn flat on the ground, along the texture's x).
    `fresh` is flat and pale; `old` is flat and greener, half stood up again (drawn by the rising model)."""
    r = rng("trampled", kind, n)
    p = Pair()
    base = 150 if kind == "fresh" else 132
    for _ in range(30):
        x0, y0 = r.randint(0, 15), r.randint(0, 15)
        L = r.randint(4, 9)
        for i in range(L):
            x = (x0 + i) % 16
            y = (y0 + (1 if (i > 2 and r.random() < 0.18) else 0)) % 16
            p.tint(x, y, base + i * 5 + r.randint(-8, 8))
    for _ in range(8 if kind == "fresh" else 3):
        x0, y0 = r.randint(0, 15), r.randint(0, 15)
        for i in range(r.randint(2, 4)):
            p.own((x0 + i) % 16, y0, r.choice(STRAW))
    return p


def trampled_rise(n):
    """A trail springing back: short bent blades leaning one way (a short cross, under half a block)."""
    r = rng("rise", n)
    p = Pair()
    for k in range(14):
        x0 = (k * 3 + r.randint(0, 2)) % 16
        blade(p, r, x0, 15, r.randint(3, 7), r.choice((3, 4, 5)), shades=(124, 136, 148, 158, 166), bend=1.4)
    return p


def clover(n):
    """Clover in the lawn: trefoil leaves (tinted) lying on the flower bed's plane, white or pink heads (own colour)."""
    r = rng("clover", n)
    p = Pair()
    for _ in range(16):
        cx, cy = r.randint(1, 14), r.randint(1, 14)
        for dx, dy, v in ((0, -1, 166), (-1, 0, 150), (1, 0, 158), (0, 0, 112), (-1, -1, 140), (1, -1, 146), (0, 1, 120)):
            if (dx, dy) in ((-1, -1), (1, -1), (0, 1)) and r.random() < 0.6:
                continue
            p.tint(cx + dx, cy + dy, v + r.randint(-8, 8))
        if r.random() < 0.4:
            p.tint(cx, cy - 1, 190)                      # the pale chevron on a clover leaf
    for k in range(2 + n % 2):
        cx, cy = r.randint(2, 13), r.randint(2, 13)
        pink = (n + k) % 3 == 0
        cols = [(226, 150, 182), (200, 112, 156), (240, 190, 210)] if pink else [(244, 240, 230), (222, 214, 206), (252, 250, 244)]
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (0, -1), (1, -1)):
            if dy == -1 and r.random() < 0.5:
                continue
            p.own(cx + dx, cy + dy, cols[(dx + dy + 2) % 3])
    return p

# ---- flowers and herbs ------------------------------------------------------------------------------------------------
LACE = [(246, 244, 234), (228, 226, 214), (252, 252, 246), (214, 212, 200)]


def cow_parsley(part, n):
    """Cow parsley, the white lace of pasture verges and hedge feet: two blocks, thin stems, flat umbels on top.
    The stems stand at the same places in every version, so the halves meet."""
    r = rng("parsley", part, n)
    p = Pair()
    stems = (3, 8, 12)
    for i, x0 in enumerate(stems):
        if part == "bottom":
            for k in range(16):
                p.tint(x0, 15 - k, 108 + k * 2.4)
            for y in (15, 14, 13, 11):                   # the ferny leaves at its foot
                for d in (-3, -2, -1, 1, 2, 3):
                    if r.random() < 0.45 - 0.08 * abs(d):
                        p.tint(x0 + d, y - abs(d) // 2, 140 + r.randint(0, 28))
            y = r.randint(4, 8)
            p.tint(x0 + 1, y, 150); p.tint(x0 + 2, y - 1, 160); p.tint(x0 + 3, y - 1, 168)
        else:
            top = (4, 2, 6)[(i + n) % 3]
            lean = (-1, 0, 1)[(i + n * 2) % 3]
            pts = blade(p, r, x0, 15, 16 - top, lean, shades=(128, 138, 148, 156, 164), bend=3)
            hx, hy = pts[-1]
            for dx in (-3, -2, -1, 1, 2, 3):              # the umbel's spokes
                p.tint(hx + dx, hy - (1 if abs(dx) < 3 else 0), 158)
            for dx in range(-4, 5):                      # the umbel: a flat-topped cloud of tiny white flowers
                for dy in (-1, -2, -3):
                    if abs(dx) + abs(dy + 2) * 2 < 6 and r.random() < 0.82:
                        p.own(hx + dx, hy + dy, r.choice(LACE))
    return p

def foxglove(part, n, colour="purple"):
    """Foxglove: a tall one-sided spike of hanging bells over a rosette of soft leaves, two blocks, at the wild's edge."""
    r = rng("foxglove", part, n, colour)
    p = Pair()
    bell = {"purple": [(196, 104, 182), (156, 70, 146), (220, 150, 206), (118, 40, 104)],
            "white": [(240, 232, 238), (208, 196, 210), (252, 248, 250), (150, 110, 150)]}[colour]
    spikes = (6, 11) if n % 2 == 0 else (5,)
    for s, x0 in enumerate(spikes):
        d = 1 if (s + n) % 2 == 0 else -1               # the side the bells hang on
        if part == "bottom":
            for i in range(16):
                p.tint(x0, 15 - i, 112 + i * 2)
            for y, L in ((15, 5), (14, 4), (12, 3)):     # the rosette: broad leaves out to both sides
                for e in (-1, 1):
                    for i in range(1, L + 1):
                        p.tint(x0 + e * i, y - (1 if i > L - 2 else 0), 120 + i * 9 + r.randint(-6, 6))
            for y in range(1, 9, 3):                     # the lowest bells, open, spotted inside
                p.own(x0 + d, y, bell[0]); p.own(x0 + 2 * d, y, bell[0])
                p.own(x0 + d, y + 1, bell[1]); p.own(x0 + 2 * d, y + 1, bell[3])
                p.own(x0 + d, y + 2, bell[2])
        else:
            L = 12 + r.randint(0, 2)
            for i in range(L):
                p.tint(x0, 15 - i, 130 + i * 3)
            top = 16 - L
            for y in range(top + 1, 16, 3):
                big = y > top + 4
                p.own(x0 + d, y, bell[2] if not big else bell[0])
                p.own(x0 + d, y + 1, bell[1])
                if big:
                    p.own(x0 + 2 * d, y, bell[0])
                    p.own(x0 + 2 * d, y + 1, bell[3])
                    p.own(x0 + d, y + 2, bell[2])
            p.own(x0, top - 1, (150, 176, 110))          # the green bud at the tip
    return p

def thistle(n):
    """Spear thistle: spiny grey-green leaves up a stem and a purple brush head on a green cup, in rough pasture."""
    r = rng("thistle", n)
    p = Pair()
    heads = [(8, 3)] if n % 2 == 0 else [(6, 4), (11, 6)]
    for hx, hy in heads:
        blade(p, r, hx + r.choice((-1, 0, 1)) * 0, 15, 16 - hy - 1, 0, shades=(120, 130, 140, 150, 160))
        for y in range(14, hy + 3, -3):                  # spiny leaves, alternate
            for d in (-1, 1):
                L = max(2, min(4, (y - hy) // 3))
                for i in range(1, L + 1):
                    p.tint(hx + d * i, y - (i % 2), 148 + i * 8)
                p.tint(hx + d * (L + 1), y - 1, 186)      # a spine's pale tip
        for dx in (-1, 0, 1):
            p.own(hx + dx, hy + 1, (112, 132, 84))         # the cup
        for dx in (-1, 0, 1):
            p.own(hx + dx, hy, (186, 94, 188))
        p.own(hx, hy - 1, (214, 124, 216))
        p.own(hx - 1, hy - 1, (160, 70, 160))
        p.own(hx + 1, hy - 1, (160, 70, 160))
    return p


def heather(n):
    """Heather (ling): a low cushion of tiny dark needles with pink-purple flower spikes, on heaths and highland."""
    r = rng("heather", n)
    p = Pair()
    for k in range(12):
        blade(p, r, 2 + (k * 3 + r.randint(0, 2)) % 12, 15, r.randint(5, 10), r.choice((-3, -2, -1, 1, 2, 3)), shades=(76, 88, 100, 112, 122))
    for _ in range(110):
        x, y = r.randint(0, 15), r.randint(4, 15)
        if ellipse(8, 15, 8, 10, x, y) < 1:
            p.tint(x, y, r.randint(80, 128))
    for _ in range(40):
        x, y = r.randint(1, 14), r.randint(4, 12)
        if ellipse(8, 14, 7, 9.5, x, y) < 1 and p.t.get(x, y)[3]:
            p.own(x, y, r.choice([(194, 112, 172), (168, 92, 152), (216, 146, 198), (140, 76, 128)]))
    return p


def gorse(n):
    """Gorse: a dark spiny scrub studded with coconut-yellow flowers, on heath and hill edges."""
    r = rng("gorse", n)
    p = Pair()
    for k in range(9):
        blade(p, r, 2 + (k * 4 + r.randint(0, 3)) % 12, 15, r.randint(8, 15), r.choice((-3, -2, -1, 1, 2, 3)), shades=(66, 76, 86, 96, 106))
    for _ in range(80):
        x, y = r.randint(0, 15), r.randint(1, 14)
        if ellipse(8, 8.5, 7.5, 7, x, y) < 1:
            p.tint(x, y, r.randint(72, 116))
    for _ in range(22):
        x, y = r.randint(1, 14), r.randint(1, 12)
        if ellipse(8, 8, 6.5, 6.5, x, y) < 1:
            p.own(x, y, r.choice([(244, 208, 62), (226, 182, 40), (252, 230, 112)]))
    return p


def dry_tussock(n):
    """Desert and badlands: a sparse dry tussock (its own straw colour, untinted)."""
    r = rng("tussock", n)
    p = Pair()
    for k in range(9):
        blade(p, r, 4 + (k * 3 + r.randint(0, 2)) % 9, 15, r.randint(4, 11), r.choice((-5, -3, -2, 2, 3, 5)), own=STRAW + TAWNY[:2])
    return p


# ---- shrubs, ferns, the water's edge ----------------------------------------------------------------------------------
def bramble(n, fruit):
    """Bramble: arching red-brown thorny canes (own colour) carrying leaves of three to five leaflets (tinted), and white
    flowers, green or ripe blackberries. A thicket of it slows you."""
    r = rng("bramble", n)
    p = Pair()
    canes = [(1, 1), (14, -1), (7, 1)] if n % 2 == 0 else [(2, 1), (13, -1), (9, -1)]
    leaves = []
    for x0, d in canes:
        reach = 9 + r.randint(0, 3)
        rise = 11 + r.randint(0, 3)
        for i in range(24):
            f = i / 23
            x = int(round(x0 + d * reach * f))
            y = int(round(15 - rise * math.sin(f * 2.5)))
            p.own(x, y, (118, 58, 54) if i % 5 else (160, 94, 80))
            if i % 6 == 3:
                leaves.append((x, y, d))
    for x, y, d in leaves:                               # a leaf: a rounded clump of tinted leaflets above the cane
        for dx in range(-2, 3):
            for dy in range(-3, 1):
                if dx * dx + (dy + 1.5) ** 2 < 4.5 and r.random() < 0.85:
                    p.tint(x + dx, y + dy, 96 + (dy + 3) * -6 + (dx + 2) * 9 + r.randint(-8, 8))
        p.tint(x, y - 1, 168)
    if fruit == "none":
        for x, y, d in leaves[::2]:
            p.own(x + d, y - 3, (246, 238, 238)); p.own(x + 2 * d, y - 3, (228, 206, 214)); p.own(x + d, y - 4, (240, 226, 232))
    else:
        cols = [(48, 22, 54), (84, 42, 92), (28, 12, 34)] if fruit == "ripe" else [(176, 62, 60), (206, 104, 76), (136, 152, 82)]
        for x, y, d in leaves[1::2] + leaves[:1]:
            bx, by = x - d, y + 1
            p.own(bx, by, cols[0]); p.own(bx + 1, by, cols[1]); p.own(bx, by + 1, cols[2]); p.own(bx + 1, by + 1, cols[0])
    return p

def bracken(part, n, turned=False):
    """Bracken: a big two-block fern of the forest edge and the deep wild. Its fronds rise on bare stalks and arch out flat,
    leaflets combed out either side of the midrib, longest at its middle. Turned bracken is tawny and keeps its own colour."""
    r = rng("bracken", part, n, turned)
    p = Pair()

    def put(x, y, v):
        if turned:
            p.own(x, y, (118, 82, 52) if v < 0 else TAWNY[(x * 7 + y * 3 + n) % 4])
        else:
            p.tint(x, y, v if v >= 0 else 104)
    stalks = (5, 10) if n % 2 == 0 else (4, 11)
    if part == "bottom":
        for x0 in stalks:
            for i in range(16):
                put(x0, 15 - i, -1 if turned else 100 + i * 2)
        x0 = stalks[0]
        for i in range(1, 6):                            # one low frond near the ground
            put(x0 - i, 11 + i // 2, 128 + i * 8)
            put(x0 - i, 12 + i // 2, 118 + i * 6)
            if i > 1:
                put(x0 - i, 10 + i // 2, 140 + i * 6)
    else:
        # two fronds: a big one rising on the left stalk and arching out right, a smaller, higher one from the right back left
        for k, (x0, d, rise, length) in enumerate(((stalks[0] - 2, 1, 7, 11), (stalks[1] + 2, -1, 11, 8))):
            rib = [(x0, 15 - i) for i in range(rise)]
            top = 15 - rise
            for i in range(1, length):
                rib.append((x0 + d * i, top - int(round(2 * math.sin(min(1.0, i / length) * 2.6)))))
            for x, y in rib:
                put(x, y, -1 if turned else 112)
            for j, (x, y) in enumerate(rib[rise:]):
                w = 4 if 1 < j < length - 4 else 3 if j < length - 2 else 1
                if j % 2 == 0:                           # combed: a leaflet every other pixel, slanting toward the tip
                    for h in range(1, w + 1):
                        put(x + d * (h // 2), y + h, 124 + h * 9 + r.randint(-6, 6))
                    put(x, y - 1, 156 + r.randint(-6, 6))
            if not turned and k == 0 and n % 2 == 0:     # a fiddlehead still curled
                fx = x0 + 5
                for (dx, dy) in ((0, 0), (0, -1), (0, -2), (1, -3), (2, -3), (2, -2)):
                    p.tint(fx + dx, 15 + dy, 150 - dy * 6)
    return p

def reeds(part, n, pale=False):
    """Reeds and bulrush at the water's edge, two blocks: stiff blades and cattail heads (pale in Mirror Marsh)."""
    r = rng("reeds", part, n, pale)
    p = Pair()
    head = [(214, 206, 186), (190, 180, 160), (232, 226, 210)] if pale else [(112, 70, 40), (92, 56, 32), (126, 84, 50)]
    for k in range(9):
        x0 = 1 + (k * 2 + r.randint(0, 1)) % 15
        if part == "bottom":
            blade(p, r, x0, 15, 16, 0, shades=(108, 118, 128, 138, 148))
        else:
            blade(p, r, x0, 15, r.randint(5, 15), r.choice((-2, -1, 0, 1, 2)), shades=(130, 142, 154, 164, 176), bend=2.5)
    if part == "top":
        for x0 in ((4, 10) if n % 2 == 0 else (6, 12)):
            top = r.randint(2, 5)
            for y in range(top, top + 6):
                p.own(x0, y, head[y % 2])
                p.own(x0 + 1, y, head[2])
            p.own(x0, top - 1, (160, 150, 112))
            p.own(x0, top - 2, (160, 150, 112))
            for y in range(top + 6, 16):
                p.tint(x0, y, 132)
    return p


def hawthorn(n):
    """Hawthorn for hedgerows: small dense leaves (tinted as the game's leaves) with May blossom and red haws."""
    r = rng("hawthorn", n)
    p = Pair()
    for y in range(16):
        for x in range(16):
            v = r.random()
            if v < 0.78:
                shade = 72 + int(80 * r.random())
                if (x + y * 3) % 7 == 0:
                    shade += 24
                p.tint(x, y, shade)
    for _ in range(12 + n * 2):                            # blossom in clusters of two to four
        x, y = r.randint(0, 14), r.randint(0, 14)
        p.own(x, y, (248, 246, 238))
        p.own(x + 1, y, (228, 222, 214))
        if r.random() < 0.5:
            p.own(x, y + 1, (236, 230, 226))
            p.own(x + 1, y + 1, (214, 190, 196))
    for _ in range(4):
        x, y = r.randint(0, 15), r.randint(0, 15)
        p.own(x, y, (178, 36, 40))
        if r.random() < 0.4:
            p.own(x + 1, y + 1, (140, 24, 30))
    return p


# ---- still things: fungus, sheds, ground tops -------------------------------------------------------------------------
def bracket_fungus(n):
    """Shelf fungus on a dead trunk's side: tan shelves with dark gills (laid on the side of the log the block faces from)."""
    r = rng("bracket", n)
    p = Pair()
    shelves = [((2, 4, 7), (8, 9, 6), (4, 13, 5)), ((6, 3, 6), (2, 8, 8), (9, 12, 5)), ((3, 5, 9), (8, 11, 6))][n % 3]
    for x0, y0, w in shelves:
        for i in range(w):
            edge = i in (0, w - 1)
            p.own(x0 + i, y0 - (0 if edge else 1), (224, 190, 132) if i % 2 else (206, 168, 110))
            p.own(x0 + i, y0, (186, 146, 92) if not edge else (150, 110, 70))
            p.own(x0 + i, y0 + 1, (110, 76, 46))
        p.own(x0 + w // 2, y0 - 1, (240, 214, 160))
    return p


def antler_shed(n):
    """A shed antler lying in the grass, flat on the ground: a sign, and a find for the Hunting craft."""
    p = Pair()
    beam = [(2, 12), (3, 11), (4, 11), (5, 10), (6, 10), (7, 9), (8, 9), (9, 8), (10, 8), (11, 7), (12, 6), (13, 5)]
    if n % 2:
        beam = [(15 - x, y) for x, y in beam]
    for x, y in beam:
        p.own(x, y, (230, 218, 190))
        p.own(x, y + 1, (176, 160, 128))
    d = -1 if n % 2 else 1
    for x, y, L in ((beam[3][0], beam[3][1], 3), (beam[7][0], beam[7][1], 3), (beam[10][0], beam[10][1], 2)):
        for i in range(1, L + 1):
            p.own(x - d * (i // 2), y - i, (240, 232, 208))
    bx, by = beam[0]
    p.own(bx, by, (150, 120, 90))                          # the burr where it left the skull
    p.own(bx - d, by + 1, (130, 102, 76))
    return p


def wild_sward_top(n):
    """Wild sward, the deep wild's ground: grass (tinted, as the game's grass top) with last year's thatch showing (own)."""
    r = rng("sward", n)
    p = Pair()
    for y in range(16):
        for x in range(16):
            p.tint(x, y, 112 + int(72 * r.random() ** 1.3))
    for _ in range(20):
        x0, y0 = r.randint(0, 15), r.randint(0, 15)
        horizontal = r.random() < 0.5
        for i in range(r.randint(2, 5)):
            x, y = (x0 + i, y0) if horizontal else (x0, y0 + i)
            p.own(x % 16, y % 16, r.choice(STRAW + TAWNY[:2]))
    return p


def trodden_earth(face, n):
    """Trodden earth: the bare packed ground of a herd's path, hoof-dented on top (own colours: it is earth)."""
    r = rng("trodden", face, n)
    c = Canvas(16, 16)
    base = [(112, 84, 58), (100, 74, 50), (124, 94, 66), (90, 66, 44), (134, 104, 74)]
    for y in range(16):
        for x in range(16):
            c.set(x, y, r.choice(base[:3]) if r.random() < 0.85 else r.choice(base[3:]))
    if face == "top":
        for _ in range(5):                                   # cloven prints
            x, y = r.randint(1, 13), r.randint(1, 12)
            for dx, dy in ((0, 0), (0, 1), (0, 2), (2, 0), (2, 1), (2, 2)):
                c.set(x + dx, y + dy, (70, 50, 32))
            c.set(x + 1, y + 3, (84, 62, 40))
        for _ in range(6):
            c.set(r.randint(0, 15), r.randint(0, 15), (150, 140, 120))   # a crushed pebble
    else:
        for y in range(4):
            for x in range(16):
                if r.random() < 0.3:
                    c.set(x, y, (82, 60, 40))
    return c


def wallow(face, n):
    """A wallow: mud by water, churned with hoofprints, where aurochs and boar roll (own colours)."""
    r = rng("wallow", face, n)
    c = Canvas(16, 16)
    mud = [(68, 58, 50), (58, 50, 44), (78, 66, 56), (50, 42, 38), (88, 76, 64)]
    for y in range(16):
        for x in range(16):
            c.set(x, y, r.choice(mud[:3]))
    if face == "top":
        for _ in range(7):
            x, y = r.randint(0, 13), r.randint(0, 12)
            for dx, dy in ((0, 0), (0, 1), (0, 2), (2, 0), (2, 1), (2, 2)):
                c.set(x + dx, y + dy, (40, 34, 30))
            c.set(x + 1, y + 3, (46, 38, 34))
        for _ in range(10):                                   # wet sheen
            c.set(r.randint(0, 15), r.randint(0, 15), (112, 104, 96))
        for _ in range(3):                                    # a smear of hair
            c.set(r.randint(0, 15), r.randint(0, 15), (120, 82, 50))
    return c


def salt_lick(face, n):
    """A salt lick: pale crusted stone where herds gather, licked smooth in hollows (own colours)."""
    r = rng("salt", face, n)
    c = Canvas(16, 16)
    stone = [(196, 190, 176), (180, 174, 160), (214, 208, 196), (160, 154, 142), (232, 228, 220)]
    for y in range(16):
        for x in range(16):
            c.set(x, y, r.choice(stone[:3]))
    for _ in range(18):                                       # salt crystals
        c.set(r.randint(0, 15), r.randint(0, 15), stone[4])
    if face == "top":
        for cx, cy, rad in ((5, 6, 3), (11, 11, 2.5)):        # licked hollows, smooth and a little darker
            for y in range(16):
                for x in range(16):
                    if (x - cx) ** 2 + (y - cy) ** 2 < rad * rad:
                        c.set(x, y, (168, 160, 150) if (x + y) % 3 else (176, 170, 160))
    else:
        for y in range(16):
            for x in range(16):
                if r.random() < 0.15:
                    c.set(x, y, stone[3])
    return c


# ---- the catalogue -----------------------------------------------------------------------------------------------------
# name -> (function, count). Pairs are written as <name>_<n> (+ _overlay when it has one); canvases as <name>_<n>.
PAIRS = {}
for i in range(4):
    PAIRS[f"meadow_grass_{i}"] = lambda i=i: meadow_grass(i)
for i in range(3):
    PAIRS[f"frost_grass_{i}"] = lambda i=i: frost_grass(i)
    PAIRS[f"long_grass_bottom_{i}"] = lambda i=i: long_bottom(i)
    for look in ("green", "seeding", "tawny"):
        PAIRS[f"long_grass_top_{look}_{i}"] = lambda i=i, look=look: long_top(look, i)
    for look in ("gold", "glass"):
        PAIRS[f"wild_rye_top_{look}_{i}"] = lambda i=i, look=look: rye_top(look, i)
    PAIRS[f"thistle_{i}"] = lambda i=i: thistle(i)
    PAIRS[f"heather_{i}"] = lambda i=i: heather(i)
    PAIRS[f"gorse_{i}"] = lambda i=i: gorse(i)
    PAIRS[f"dry_tussock_{i}"] = lambda i=i: dry_tussock(i)
    PAIRS[f"clover_{i}"] = lambda i=i: clover(i)
    PAIRS[f"hawthorn_leaves_{i}"] = lambda i=i: hawthorn(i)
    PAIRS[f"bracket_fungus_{i}"] = lambda i=i: bracket_fungus(i)
    PAIRS[f"wild_sward_top_{i}"] = lambda i=i: wild_sward_top(i)
    for part in ("bottom", "top"):
        PAIRS[f"cow_parsley_{part}_{i}"] = lambda i=i, part=part: cow_parsley(part, i)
        PAIRS[f"foxglove_{part}_{i}"] = lambda i=i, part=part: foxglove(part, i, "purple" if i < 2 else "white")
        PAIRS[f"bracken_{part}_{i}"] = lambda i=i, part=part: bracken(part, i)
        PAIRS[f"bracken_{part}_turned_{i}"] = lambda i=i, part=part: bracken(part, i, True)
        PAIRS[f"reeds_{part}_{i}"] = lambda i=i, part=part: reeds(part, i)
        PAIRS[f"reeds_{part}_pale_{i}"] = lambda i=i, part=part: reeds(part, i, True)
    for fruit in ("none", "green", "ripe"):
        PAIRS[f"bramble_{fruit}_{i}"] = lambda i=i, fruit=fruit: bramble(i, fruit)
for i in range(2):
    PAIRS[f"wild_rye_stems_{i}"] = lambda i=i: rye_stems(i)
    PAIRS[f"wild_rye_middle_{i}"] = lambda i=i: rye_middle(i)
    PAIRS[f"trampled_fresh_{i}"] = lambda i=i: trampled("fresh", i)
    PAIRS[f"trampled_old_{i}"] = lambda i=i: trampled("old", i)
    PAIRS[f"trampled_rise_{i}"] = lambda i=i: trampled_rise(i)
    PAIRS[f"antler_shed_{i}"] = lambda i=i: antler_shed(i)

CANVASES = {}
for i in range(2):
    for face in ("top", "side"):
        CANVASES[f"trodden_earth_{face}_{i}"] = lambda i=i, face=face: trodden_earth(face, i)
        CANVASES[f"wallow_{face}_{i}"] = lambda i=i, face=face: wallow(face, i)
        CANVASES[f"salt_lick_{face}_{i}"] = lambda i=i, face=face: salt_lick(face, i)


def blackberries():
    """The item: a little cluster of ripe blackberries on a green stalk."""
    c = Canvas(16, 16)
    berries = [(6, 7), (9, 6), (8, 9), (5, 10), (10, 10), (7, 12)]
    for k, (bx, by) in enumerate(berries):
        for dx in range(3):
            for dy in range(3):
                if (dx, dy) in ((0, 0), (2, 0), (0, 2), (2, 2)):
                    continue
                shade = [(40, 18, 48), (70, 34, 82), (24, 10, 30)][(dx + dy + k) % 3]
                c.set(bx + dx, by + dy, (*shade, 255))
        c.set(bx + 1, by, (132, 96, 150, 255))                 # a glint on each drupe
    for i, (x, y) in enumerate([(9, 2), (9, 3), (10, 4), (10, 5)]):
        c.set(x, y, (86, 128, 54, 255))
    c.set(11, 2, (108, 150, 66, 255)); c.set(12, 2, (86, 128, 54, 255)); c.set(8, 1, (108, 150, 66, 255))
    return c


def seed(n):
    """Seed fluff on the wind: a pale tuft with a dark seed, three shapes."""
    c = Canvas(8, 8)
    hair = (246, 242, 226, 230)
    soft = (226, 220, 200, 150)
    if n == 0:
        for x, y in [(3, 2), (4, 2), (2, 3), (5, 3), (3, 1), (4, 4)]:
            c.set(x, y, hair)
        for x, y in [(1, 2), (6, 2), (3, 0), (2, 4), (5, 4)]:
            c.set(x, y, soft)
        c.set(3, 5, (120, 100, 70, 255)); c.set(3, 6, (96, 80, 56, 255))
    elif n == 1:
        for x, y in [(2, 2), (3, 3), (4, 3), (5, 2), (3, 2), (4, 2)]:
            c.set(x, y, hair)
        for x, y in [(1, 1), (6, 1), (2, 1), (5, 1)]:
            c.set(x, y, soft)
        c.set(4, 4, (110, 92, 64, 255))
    else:
        for x, y in [(3, 3), (4, 3), (3, 4), (4, 4)]:
            c.set(x, y, hair)
        for x, y in [(2, 2), (5, 2), (2, 5), (5, 5), (3, 2), (4, 5)]:
            c.set(x, y, soft)
    return c


def build(out):
    out.item("blackberries", blackberries())
    for i in range(3):
        out.particle(f"wild_seed_{i}", seed(i))
    for name, make in PAIRS.items():
        p = make()
        out.block("wild/" + name, p.t)
        if p.has_overlay():
            out.block("wild/" + name + "_overlay", p.u)
    for name, make in CANVASES.items():
        out.block("wild/" + name, make())


# ---- the style sheet's pictures -----------------------------------------------------------------------------------------
TINTS = {"plains": (0x91, 0xBD, 0x59), "forest": (0x79, 0xC0, 0x5A), "savanna": (0xBF, 0xB7, 0x55)}
LEAF_TINTS = {"plains": (0x77, 0xAB, 0x2F), "forest": (0x59, 0xAE, 0x30), "savanna": (0xAE, 0xA4, 0x2A)}


def compose(p, tint):
    """The sprite as the game draws it: the grey layer times the tint, the overlay over it."""
    from PIL import Image
    img = Image.new("RGBA", (16, 16))
    out = []
    for y in range(16):
        for x in range(16):
            u = p.u.get(x, y)
            if u[3]:
                out.append(u)
                continue
            t = p.t.get(x, y)
            out.append((t[0] * tint[0] // 255, t[1] * tint[1] // 255, t[2] * tint[2] // 255, t[3]) if t[3] else (0, 0, 0, 0))
    img.putdata(out)
    return img


def sheet(outdir):
    from PIL import Image
    os.makedirs(outdir, exist_ok=True)
    for name, make in PAIRS.items():
        p = make()
        for biome, tint in TINTS.items():
            t = LEAF_TINTS[biome] if name.startswith("hawthorn") else tint
            compose(p, t).resize((128, 128), Image.NEAREST).save(os.path.join(outdir, f"{name}__{biome}.png"))
    for name, make in CANVASES.items():
        make().image().resize((128, 128), Image.NEAREST).save(os.path.join(outdir, f"{name}.png"))
    print(len(PAIRS), "sprites,", len(CANVASES), "ground faces ->", outdir)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "sheet":
        sheet(sys.argv[2])
