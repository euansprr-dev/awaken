"""Arms' weapons (SPEC §24 "Weapons"; docs/combat/PLAN.md §4.5, §4.10): sculpted meshes, painted, drawn by our shader.

    python3 tools/art/build_all.py arms_weapons        every file below
    python3 tools/art/specs/arms_weapons.py build      the same, after the checks
    python3 tools/art/specs/arms_weapons.py check      the checks only (each weapon built, nothing written)
    python3 tools/art/specs/arms_weapons.py preview DIR [id]   turntables and a close-up per weapon (mesh_render.py),
                                                               and the review sheet DIR/weapons.png

Each weapon is designed by hand here, one function per design, from the toolkit in tools/art/mesh.py: a blade lofted
through its real cross-sections (edges, a hollow-ground bevel, a fuller sunk into the flat) and tapering to a true
point; guards swept along curves; grips turned on a lathe and bound in wire; cut gems. Every surface is unfolded and
painted texel by texel in a material's own language (polished ivory, gold, crimson runes, a woven grip...): light is
worked out continuously, then set onto the material's hand-picked ramp with an ordered dither, so a smooth form still
reads as pixel art. Where parts meet, a contact shadow is painted in. The material map beside the albedo tells the
shader (assets/awaken/shaders/core/arms_weapon.fsh) what is metal, how glossy, what glows and in which order.

Per weapon this writes: arms/weapons/<id>.mesh (the game's mesh), textures/item/arms/<id>.png (albedo | material),
the inventory icon rendered from the mesh (<id>_icon.png), the item definition (the mesh in hand, the icon in the
inventory, on the ground, in frames), its recipe, lang, #minecraft:swords, and its row in the generated ArmsData.java.
Deterministic: every random choice is seeded.
"""
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
for p in (HERE, ART):
    if p not in sys.path:
        sys.path.insert(0, p)
from PIL import Image  # noqa: E402

import gear as G  # noqa: E402  (the Hunter sets' names)
import mesh as MS  # noqa: E402
import mesh_render as MR  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
MERGE = os.path.join(ROOT, "src/main/merge/arms")
JAVA = os.path.join(ROOT, "src/main/java/dev/awaken/arms/ArmsData.java")
HAND = (3.0, 3.0, 8.0)  # the grip's centre in item units: where iron_sword.png's handle is, so vanilla's hands fit
ANGLE = -45.0           # the blade along the item's diagonal, tip to the upper right
SCALE = 0.9             # sword units to item units: a longsword a fifth longer than vanilla's, not a greatsword

# ---------------------------------------------------------------------------------------------------------------------
# The catalog: one line per weapon (damage over the player's 1; uses; vanilla rarity; enchantability; repair tag)

LONGSWORD_SPEED = -2.4  # the Longsword's baseSpeed 1.6 (its timing table rows are cut for it)
CATALOG = [
    # id                      name                     tier dmg  dur   rarity      ench repair
    ("wildhide_longsword", "Wildhide Longsword", "E", 4.5, 220, "COMMON", 15, "hunter_core_e"),
    ("beastbound_longsword", "Beastbound Longsword", "D", 5.5, 500, "UNCOMMON", 12, "hunter_bind_d"),
    ("gatewrought_longsword", "Gatewrought Longsword", "C", 6.5, 1400, "RARE", 12, "hunter_bind_c"),
    ("veilwoven_longsword", "Veilwoven Longsword", "B", 7.5, 1900, "EPIC", 15, "hunter_bind_b"),
    ("lordscale_longsword", "Lordscale Longsword", "A", 8.5, 2300, "EPIC", 15, "hunter_bind_a"),
    ("sovereign_longsword", "Sovereign Longsword", "S", 9.5, 2800, "EPIC", 18, "hunter_bind_s"),
]

# ---------------------------------------------------------------------------------------------------------------------
# Ramps: dark to light, hue-shifted (shadows cool toward violet, lights warm), the colours a pixel artist would pick

RAMPS = {
    "ivory": ["#2c2330", "#463a48", "#665866", "#8d7f86", "#b4a8a2", "#d6ccbc", "#ece4d2", "#f9f5e9", "#fffdf6"],
    "gold": ["#2a1206", "#52260b", "#7f4211", "#ad6a1a", "#d49530", "#efbf55", "#fadd8c", "#fff3cf"],
    "crimson": ["#18030c", "#360616", "#620d20", "#97172b", "#cd2f36", "#f05a4e", "#ff9080", "#ffd2c2"],
    "weave": ["#07040d", "#100a1c", "#1b122c", "#28193e", "#362352", "#472f68", "#5c3f80"],
    "flint": ["#0e1016", "#1a1e27", "#282e39", "#3a414e", "#515968", "#6c7584", "#8e97a6", "#b6bfcc"],
    "bone": ["#3a2e26", "#5a4a3c", "#7d6b58", "#a08e76", "#c2b296", "#ddd0b4", "#f0e8d2", "#fbf7ec"],
    "hide": ["#1c110a", "#2e1c10", "#462c18", "#603e22", "#7c542e", "#9a6c3e", "#b88a54", "#d4aa72"],
    "amber": ["#3a1404", "#6e2a06", "#a8480c", "#da7016", "#f49a2a", "#ffc25a", "#ffe29a", "#fff6dc"],
    "bronze": ["#1e1008", "#3a2210", "#5c3a18", "#835a24", "#a97c34", "#cba04a", "#e6c472", "#f8e4a8"],
    "pelt": ["#120d0a", "#1e1612", "#2c211a", "#3c2e24", "#4e3d30", "#644f3e", "#7c6450", "#968066"],
    "moss": ["#0c1a0a", "#163012", "#22481a", "#326424", "#488432", "#68a646", "#92c862", "#c4e898"],
    "slate": ["#0f141c", "#1a2230", "#283244", "#3a4658", "#525f72", "#6e7c8e", "#909eae", "#b8c4d0"],
    "brass": ["#241406", "#44280c", "#6a4414", "#94661e", "#ba8a2e", "#d8ae48", "#ecce74", "#faeab0"],
    "signal": ["#04101e", "#082442", "#0e3e72", "#1a62a8", "#3088d8", "#5cb0f4", "#98d4ff", "#e0f4ff"],
    "veil": ["#0a0716", "#140e28", "#21183e", "#302458", "#433474", "#5a4892", "#7662b0", "#9a88cc"],
    "spirit": ["#0c0c2a", "#1a1c52", "#2c3486", "#4454b8", "#6680e0", "#8eacf6", "#bcd2ff", "#eaf2ff"],
    "mirror": ["#1e222c", "#343a48", "#525a6a", "#767e8e", "#9ca4b2", "#c4cad4", "#e4e8ee", "#fafcff"],
    "obsidian": ["#05050a", "#0c0b14", "#15131f", "#201c2e", "#2c2740", "#3a3454", "#4c456c"],
    "lord": ["#06050a", "#0e0b14", "#17121f", "#22192c", "#2e213a", "#3c2b4a", "#4e385e", "#644a76"],
    "ember": ["#3a0800", "#6e1404", "#a82a08", "#e04a10", "#ff7a1e", "#ffae3e", "#ffd88a", "#fff4d8"],
    "pearl": ["#26344a", "#45628a", "#7aa0c8", "#b2d4f2", "#e0f1ff", "#f4faff", "#ffffff"],
}


def ramp(name):
    return np.array([[int(h[i:i + 2], 16) for i in (1, 3, 5)] for h in RAMPS[name]], dtype=float) / 255.0


BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], dtype=float) / 16.0 - 0.5


GRAIN = 1.0  # 1 for the game's texture; 0 paints clean (no dither, no grain) for the icon, where they'd be noise


def onto(value, name, dither=0.55):
    """Sets a continuous value (0 dark .. 1 light, (H, W)) onto a ramp with an ordered dither: pixel art from light."""
    dither *= GRAIN
    r = ramp(name)
    h, w = value.shape
    d = BAYER[np.arange(h)[:, None] % 4, np.arange(w)[None, :] % 4]
    idx = np.clip(np.round(np.clip(value, 0, 1) * (len(r) - 1) + d * dither), 0, len(r) - 1).astype(int)
    return r[idx]


def light(normal, key=(-0.35, 0.75, 0.55)):
    """The painted light: a soft key from the upper left front, wrapped round the form (0..1)."""
    k = np.asarray(key) / np.linalg.norm(key)
    return np.clip((normal @ k) * 0.5 + 0.5, 0, 1)


def hash01(*a):
    """A stable value 0..1 per integer cell."""
    x = np.zeros(np.broadcast(*a).shape, dtype=np.uint64)
    for k, v in enumerate(a):
        x ^= (np.asarray(v).astype(np.int64).astype(np.uint64) + np.uint64(0x9E3779B97F4A7C15 * (k + 1) & 0xFFFFFFFFFFFFFFFF))
        x = (x ^ (x >> np.uint64(30))) * np.uint64(0xBF58476D1CE4E5B9)
        x = (x ^ (x >> np.uint64(27))) * np.uint64(0x94D049BB133111EB)
        x ^= x >> np.uint64(31)
    return (x & np.uint64(0xFFFFFF)).astype(float) / float(0xFFFFFF)


# Runes: the Court's script, 3 wide and 5 tall (rows top to bottom)
RUNES = [
    ("#..", "##.", "#.#", "#..", "#.."), (".#.", "#.#", ".#.", ".#.", ".#."), ("#.#", "##.", "#..", "##.", "#.#"),
    ("###", "#..", "##.", "#..", "#.."), (".#.", "##.", ".##", "##.", ".#."), ("#.#", "#.#", ".#.", "#.#", "#.#"),
    ("##.", "#.#", "##.", "#.#", "##."), (".#.", ".##", ".#.", "##.", ".#."), ("#..", "#.#", "###", "#.#", "..#"),
]


# ---------------------------------------------------------------------------------------------------------------------
# Painting: per texel, a colour and its material (metal, gloss, emissive, flow), by the part's material

class Ctx:
    """What a painter knows of each texel of one part's chart."""

    def __init__(self, part, mask, point, normal, tag, along, rr, cc, du, dv, occl):
        self.part, self.mask, self.point, self.normal = part, mask, point, normal
        self.tag, self.along, self.rr, self.cc, self.occl = tag, along, rr, cc, occl
        self.du, self.dv = du, dv  # texels across from the anchor column, down from the first row
        self.h, self.w = mask.shape
        self.y, self.x = np.indices(mask.shape)


def material(ctx, metal, gloss, emit=0.0, flow=0.0):
    m = np.zeros((ctx.h, ctx.w, 4))
    m[..., 0], m[..., 1], m[..., 2], m[..., 3] = metal, gloss, emit, flow
    return m


def paint_gold(ctx):
    """Polished gold: lit by the form, the light wrapped bright, deeper where parts meet (the shader adds what it
    reflects)."""
    v = light(ctx.normal) * 0.8 + 0.16 - ctx.occl * 0.5
    return onto(v, "gold"), material(ctx, 1.0, 0.85)


def paint_ivory_blade(ctx, look):
    """The Sovereign's blade: ivory-white steel, gold-edged, its hollow-ground bevels catching the light down the length;
    the fuller lined in gold, a crimson channel at its floor where the Court's runes burn, each lit in turn up the blade."""
    t = ctx.tag
    along = ctx.along
    base = light(ctx.normal) * 0.5 + 0.46
    streak = (hash01(ctx.du) - 0.5) * 0.08 * GRAIN                   # polish lines run with the blade
    bevel = (t > 0.35) & (t < 1.6)
    v = base + streak + np.where(bevel, 0.16, 0.0) - ctx.occl * 0.5 - (1 - along) * 0.05
    col = onto(v, "ivory")
    mat = material(ctx, 0.35, 0.75)
    edge = t < 0.35                                                    # the edge itself: a gold line
    col[edge] = onto(light(ctx.normal) * 0.65 + 0.35, "gold")[edge]
    mat[edge] = (1.0, 0.9, 0.0, 0.0)
    lip = (t > 2.45) & (t < 2.95)                                      # the fuller's gold lining
    col[lip] = onto(light(ctx.normal) * 0.6 + 0.3, "gold")[lip]
    mat[lip] = (1.0, 0.85, 0.0, 0.0)
    chan = t >= 2.95                                                   # its crimson floor
    col[chan] = onto(light(ctx.normal) * 0.25 + 0.22, "crimson")[chan]
    mat[chan] = (0.2, 0.55, 0.0, 0.0)
    # runes: 3 texels wide on the fuller's middle column, 5 tall, 2 between; each lights a beat after the one below
    cell, ry = ctx.dv // 7, ctx.dv % 7
    rx = ctx.du + 1
    lit = np.zeros(ctx.mask.shape, dtype=bool)
    for k, rune in enumerate(RUNES):
        pat = np.array([[ch == "#" for ch in r] for r in rune])
        sel = ((cell + 3) % len(RUNES) == k) & (ry >= 1) & (ry <= 5) & (rx >= 0) & (rx < 3)
        lit |= sel & pat[np.clip(ry - 1, 0, 4), np.clip(rx, 0, 2)]
    runes = lit & chan & (along > 0.12) & (along < 0.74)
    col[runes] = onto(np.full(ctx.mask.shape, 0.93), "crimson", dither=0.0)[runes]
    phase = np.clip((cell[runes] + 1) / 24.0, 0.02, 1.0)
    mat[runes] = np.stack([np.zeros(runes.sum()), np.full(runes.sum(), 0.3), np.ones(runes.sum()), phase], axis=1)
    return col, mat


def paint_weave(ctx):
    """The Court's void weave: a basket weave of dark violet bands, a sheen on the raised threads."""
    u = ctx.x + ctx.y
    w = ctx.x - ctx.y
    over = ((u // 2) % 2) == ((w // 2) % 2)
    v = light(ctx.normal) * 0.5 + np.where(over, 0.35, 0.12) - ctx.occl * 0.5
    return onto(v, "weave", dither=0.3), material(ctx, 0.0, 0.25)


def paint_gem(ctx, name, glow):
    """A cut gem: each facet its own value (it sparkles as it turns), a hot heart, glowing of itself."""
    facet = hash01(np.floor(ctx.cc), np.floor(ctx.rr * 2))
    v = 0.45 + facet * 0.35 + light(ctx.normal) * 0.25
    col = onto(v, name, dither=0.2)
    return col, material(ctx, 0.3, 1.0, glow, 0.0)


def paint_metal(ctx, name, metal=1.0, gloss=0.85, lift=0.16, span=0.8):
    """Any polished metal on its ramp: lit by the form, deeper where parts meet (the shader adds what it reflects)."""
    v = light(ctx.normal) * span + lift - ctx.occl * 0.5
    return onto(v, name), material(ctx, metal, gloss)


def paint_bone(ctx, name="bone"):
    """Bone and antler: a warm matte, darker pores in it, worn pale where it stands proud."""
    pores = hash01(np.floor(ctx.du / 2), np.floor(ctx.dv / 2)) > 0.86
    v = light(ctx.normal) * 0.6 + 0.3 - ctx.occl * 0.5 - pores * 0.16 * GRAIN
    return onto(v, name, dither=0.4), material(ctx, 0.0, 0.35)


def paint_flint_blade(ctx, look):
    """Knapped flint: big conchoidal scars struck in from each edge, irregular as real knapping is (each texel belongs
    to the nearest strike), each scar its own grey, ripples ringing out from where it was struck, a dark ridge where two
    scars meet; the edges thin enough to glow pale."""
    rng = np.random.default_rng(7 + int(ctx.part.data.get("y0", 0) * 10) + len(ctx.part.name))
    h = ctx.mask.shape[0]
    seeds = []  # strike points on each edge (du far out), irregularly spaced up the blade
    for side in (-1, 1):
        v = rng.uniform(0, 4)
        while v < h:
            seeds.append((side * rng.uniform(5, 8), v, rng.uniform(-0.5, 0.5)))
            v += rng.uniform(5, 10)
    S = np.array(seeds)
    d = np.hypot((ctx.du[..., None] - S[:, 0]) * 0.8, ctx.dv[..., None] - S[:, 1])  # (H, W, n)
    order = np.argsort(d, axis=-1)
    near, second = np.take_along_axis(d, order[..., :1], -1)[..., 0], np.take_along_axis(d, order[..., 1:2], -1)[..., 0]
    tone = S[order[..., 0], 2]
    ripple = (np.floor(near / 1.6) % 2) * 0.07
    ridge = (second - near < 0.9) * -0.18
    v = light(ctx.normal) * 0.5 + 0.34 + (tone * 0.22 + ripple + ridge) * (0.4 + 0.6 * GRAIN) - ctx.occl * 0.5
    col = onto(v, "flint", dither=0.25)
    mat = material(ctx, 0.08, 0.75)
    edge = ctx.tag < 0.45
    col[edge] = onto(v * 0.5 + 0.5, "flint", dither=0.0)[edge]
    return col, mat


def paint_hide(ctx):
    """A hide strap: leather with a grain, a stitched seam down its middle, edges worn dark."""
    across = ctx.du
    stitch = (np.abs(across) == 0) & (ctx.dv % 3 == 0)
    v = light(ctx.normal) * 0.55 + 0.28 - ctx.occl * 0.5 + (hash01(ctx.du, ctx.dv) - 0.5) * 0.1 * GRAIN
    col = onto(v, "hide", dither=0.4)
    col[stitch] = onto(np.full(stitch.shape, 0.85), "bone", dither=0.0)[stitch]
    return col, material(ctx, 0.0, 0.3)


def paint_tusk_blade(ctx, look):
    """Boar tusk ivory: creamy, paling toward the honed edge, fine growth lines down its length; its squared back capped
    in bronze."""
    lines = (hash01(np.floor(ctx.cc * 2)) - 0.5) * 0.07 * GRAIN
    toward_edge = np.clip(1.6 - ctx.tag, 0, 1.6) * 0.08
    v = light(ctx.normal) * 0.5 + 0.36 + lines + toward_edge - ctx.occl * 0.5
    col = onto(v, "bone", dither=0.35)
    mat = material(ctx, 0.0, 0.55)
    edge = ctx.tag < 0.4
    col[edge] = onto(v * 0.4 + 0.58, "bone", dither=0.0)[edge]
    spine = ctx.tag > 4.4
    col[spine] = onto(light(ctx.normal) * 0.7 + 0.25, "bronze")[spine]
    mat[spine] = (1.0, 0.75, 0.0, 0.0)
    return col, mat


def paint_pelt(ctx):
    """Fur: short strands laid one way, light catching their tips."""
    strand = hash01(ctx.du, np.floor(ctx.dv / 3)) > 0.5
    v = light(ctx.normal) * 0.5 + 0.22 + strand * 0.14 * GRAIN - ctx.occl * 0.5
    return onto(v, "pelt", dither=0.5), material(ctx, 0.0, 0.15)


def paint_slate_blade(ctx, look):
    """Gate-steel: slate-blue, ground to a raised rib; the precursors' signal circuit inlaid in the fuller, its light
    climbing the blade in pulses (each node lights a beat after the one below)."""
    t = ctx.tag
    v = light(ctx.normal) * 0.58 + 0.24 + np.where((t > 0.35) & (t < 1.6), 0.12, 0.0) - ctx.occl * 0.5
    col = onto(v, "slate")
    mat = material(ctx, 0.8, 0.8)
    edge = t < 0.35
    col[edge] = onto(light(ctx.normal) * 0.5 + 0.5, "mirror")[edge]
    chan = t >= 2.95
    col[chan] = onto(np.full(chan.shape, 0.12), "signal", dither=0.0)[chan]
    mat[chan] = (0.3, 0.6, 0.0, 0.0)
    # the circuit: a trace up the middle, a node every 6 texels with a short branch to one side
    trace = chan & (ctx.du == 0)
    node = chan & (ctx.dv % 6 == 0) & (np.abs(ctx.du) <= 1)
    lit = (trace | node) & (ctx.along > 0.1) & (ctx.along < 0.8)
    col[lit] = onto(np.full(lit.shape, 0.9), "signal", dither=0.0)[lit]
    phase = np.clip(ctx.dv[lit] / 80.0, 0.02, 1.0)
    mat[lit] = np.stack([np.zeros(lit.sum()), np.full(lit.sum(), 0.4), np.ones(lit.sum()), phase], axis=1)
    return col, mat


def paint_ridged(ctx, name, metal=0.2, gloss=0.4):
    """A ridged grip: bands round it, each lit from above."""
    v = light(ctx.normal) * 0.6 + 0.2 - ctx.occl * 0.5
    return onto(v, name), material(ctx, metal, gloss)


def paint_veil_blade(ctx, look):
    """Veil-steel: near-black violet with a sheen that shifts like oil on water down its length; one edge is living
    spirit light (it flows tip-ward), the other mirror-honed; the point mirror-bright."""
    t = ctx.tag
    shimmer = np.sin(ctx.dv * 0.35 + ctx.du * 0.6) * 0.5 + 0.5
    v = light(ctx.normal) * 0.5 + 0.18 + shimmer * 0.14 * GRAIN - ctx.occl * 0.5
    col = onto(v, "veil")
    mat = material(ctx, 0.75, 0.85)
    edge = t < 0.4
    spirit = edge & (ctx.point[..., 0] < 0)
    honed = edge & ~spirit
    col[honed] = onto(light(ctx.normal) * 0.5 + 0.5, "mirror")[honed]
    mat[honed] = (1.0, 0.9, 0.0, 0.0)
    col[spirit] = onto(np.full(spirit.shape, 0.74), "spirit", dither=0.0)[spirit]
    mat[spirit] = np.stack([np.zeros(spirit.sum()), np.full(spirit.sum(), 0.5), np.full(spirit.sum(), 0.62),
                            np.clip(ctx.dv[spirit] / 90.0, 0.02, 1.0)], axis=1)
    tip = ctx.along > 0.9
    col[tip & ~spirit] = onto(light(ctx.normal) * 0.4 + 0.55, "mirror")[tip & ~spirit]
    return col, mat


def paint_lord_blade(ctx, look):
    """Lordscale: black scales overlapping down the flats (each rimmed where the light catches it), a central ridge,
    and edges of molten gold that flow toward the point."""
    t = ctx.tag
    row = np.floor(ctx.dv / 3)
    off = (row % 2) * 2
    cx = (ctx.du + off) % 4
    rim = (ctx.dv % 3 == 0) | (cx == 0)
    v = light(ctx.normal) * 0.5 + 0.22 + rim * 0.24 * (0.5 + 0.5 * GRAIN) - ctx.occl * 0.5
    col = onto(v, "lord")
    mat = material(ctx, 0.6, 0.85)
    edge = t < 0.5
    col[edge] = onto(np.full(edge.shape, 0.72) + (hash01(ctx.dv) - 0.5) * 0.2 * GRAIN, "ember", dither=0.0)[edge]
    mat[edge] = np.stack([np.zeros(edge.sum()), np.full(edge.sum(), 0.6), np.full(edge.sum(), 0.8),
                          np.clip(ctx.dv[edge] / 70.0, 0.02, 1.0)], axis=1)
    ridge = ctx.du == 0
    col[ridge & ~edge] = onto(light(ctx.normal) * 0.5 + 0.35, "gold")[ridge & ~edge]
    mat[ridge & ~edge] = (1.0, 0.85, 0.0, 0.0)
    return col, mat


def paint_scales(ctx, name="lord", rim_name="gold"):
    """Scales in rows, each rimmed in gold at its lower edge."""
    row = np.floor(ctx.dv / 2)
    off = (row % 2)
    cx = (ctx.du + off) % 3
    rim = (ctx.dv % 2 == 1) & (cx != 1)
    v = light(ctx.normal) * 0.5 + 0.2 - ctx.occl * 0.5
    col = onto(v, name)
    col[rim] = onto(v * 0.6 + 0.25, rim_name)[rim]
    mat = material(ctx, 0.6, 0.8)
    mat[rim] = (1.0, 0.85, 0.0, 0.0)
    return col, mat


PAINTERS = {
    "gold": lambda c, look: paint_gold(c),
    "ivory_blade": paint_ivory_blade,
    "weave": lambda c, look: paint_weave(c),
    "crimson_gem": lambda c, look: paint_gem(c, "crimson", 0.75),
    "flint_blade": paint_flint_blade,
    "bone": lambda c, look: paint_bone(c),
    "hide": lambda c, look: paint_hide(c),
    "amber_gem": lambda c, look: paint_gem(c, "amber", 0.7),
    "tusk_blade": paint_tusk_blade,
    "bronze": lambda c, look: paint_metal(c, "bronze", 1.0, 0.75),
    "pelt": lambda c, look: paint_pelt(c),
    "moss_gem": lambda c, look: paint_gem(c, "moss", 0.6),
    "slate_blade": paint_slate_blade,
    "slate": lambda c, look: paint_ridged(c, "slate", 0.3, 0.5),
    "brass": lambda c, look: paint_metal(c, "brass", 1.0, 0.8),
    "signal": lambda c, look: (onto(np.full(c.mask.shape, 0.85), "signal", dither=0.0), material(c, 0.0, 0.5, 1.0, 0.5)),
    "signal_gem": lambda c, look: paint_gem(c, "signal", 0.85),
    "veil_blade": paint_veil_blade,
    "mirror": lambda c, look: paint_metal(c, "mirror", 1.0, 0.95, 0.2, 0.75),
    "obsidian": lambda c, look: paint_ridged(c, "obsidian", 0.4, 0.8),
    "spirit_gem": lambda c, look: paint_gem(c, "spirit", 0.85),
    "lord_blade": paint_lord_blade,
    "lord_scale": lambda c, look: paint_scales(c),
    "lord": lambda c, look: paint_ridged(c, "lord", 0.6, 0.8),
    "pearl_gem": lambda c, look: paint_gem(c, "pearl", 0.6),
}


def painter_for(parts, look):
    """The painter mesh.build calls per part, with contact shadows from every other part painted in."""
    clouds = {}
    for p in parts:
        g = p.grid.reshape(-1, 3)
        mid = (p.grid[:-1, :-1] + p.grid[1:, 1:]).reshape(-1, 3) / 2 if p.grid.shape[0] > 1 and p.grid.shape[1] > 1 else g
        clouds[id(p)] = np.concatenate([g, mid])

    def paint(part, mask, point, normal, tag, along, rr, cc, du, dv):
        others = np.concatenate([clouds[id(o)] for o in parts if o is not part and not o.data.get("no_shadow")]) \
            if len(parts) > 1 else np.zeros((0, 3))
        occl = contact(point, normal, others) if len(others) else np.zeros(mask.shape)
        ctx = Ctx(part, mask, point, normal, tag, along, rr, cc, du, dv, occl)
        col, mat = PAINTERS[part.mat](ctx, look)
        alb = np.concatenate([col, np.ones(mask.shape + (1,))], axis=-1)
        return (np.clip(alb, 0, 1) * 255).astype(np.uint8), (np.clip(mat, 0, 1) * 255).astype(np.uint8)
    return paint


def contact(point, normal, others, reach=0.5):
    """How shadowed each texel is by other parts close in front of it (0..1): a tight contact shadow where parts meet,
    not a wash over everything near another part."""
    P = point.reshape(-1, 3)
    N = normal.reshape(-1, 3)
    occ = np.zeros(len(P))
    for s in range(0, len(P), 2048):
        d = others[None] - P[s:s + 2048, None]                 # (n, m, 3)
        dist = np.linalg.norm(d, axis=2)
        facing = np.einsum("nmk,nk->nm", d, N[s:s + 2048]) / np.maximum(dist, 1e-6)
        near = np.clip(1 - dist / reach, 0, 1) ** 2 * np.clip(facing, 0, 1)
        occ[s:s + 2048] = np.clip(near.max(axis=1), 0, 1)
    return occ.reshape(point.shape[:-1])


# ---------------------------------------------------------------------------------------------------------------------
# Shapes

def fullered(w, t, bevel, fw, depth, lip=0.12):
    """A blade's cross-section with a fuller sunk into each flat. Tags: 0 the edge, 1 where the bevel meets the flat, 2 the
    flat, 3 the fuller's lip, 4 its floor."""
    b = min(bevel, w * 0.42)
    hw = w / 2
    fw = min(fw, max(0.0, w - 2 * b - 2 * lip))
    half = fw / 2
    top = [(-hw, 0.0, 0), (-hw + b, t / 2, 1), (-half - lip, t / 2, 2), (-half, t / 2 - depth, 3), (0.0, t / 2 - depth * 1.15, 4),
           (half, t / 2 - depth, 3), (half + lip, t / 2, 2), (hw - b, t / 2, 1)]
    pts = top + [(hw, 0.0, 0)] + [(x, -y, k) for x, y, k in reversed(top[1:])]
    return [(x, y) for x, y, _ in pts], [k for _, _, k in pts]


def star(points, r_out, r_in, phase=0.0):
    pts = []
    for k in range(points * 2):
        a = phase + math.pi * k / points
        r = r_out if k % 2 == 0 else r_in
        pts.append((r * math.sin(a), r * math.cos(a)))
    return pts


def extrude(name, poly, z0, z1, mat, bevel=0.12, inset=0.85, centre=(0.0, 0.0), crease=30.0):
    """A flat shape in the (x, y) plane given depth along z, its rims bevelled; capped both ends."""
    cx, cy = centre
    zs = [z0, z0 + bevel, z1 - bevel, z1]
    ks = [inset, 1.0, 1.0, inset]
    centres = np.array([[cx, cy, z] for z in zs])
    frames = np.repeat(np.array([[[1.0, 0, 0], [0, 0, 1.0], [0, -1.0, 0]]]), 4, axis=0)  # right x, along z, out -y
    secs = [[(x * k, -y * k) for x, y in poly] for k in ks]
    side = MS.sweep(name, centres, frames, secs, np.linspace(0, 1, len(poly), endpoint=False), mat, True, crease)
    return [side, MS.cap(name + "_back", side, 0, mat), MS.cap(name + "_front", side, 1, mat)]


def turn(part, axis, degrees, origin):
    """Rotates a part's points about an axis through origin."""
    a = math.radians(degrees)
    c, s = math.cos(a), math.sin(a)
    R = {"x": [[1, 0, 0], [0, c, -s], [0, s, c]], "y": [[c, 0, s], [0, 1, 0], [-s, 0, c]], "z": [[c, -s, 0], [s, c, 0], [0, 0, 1]]}[axis]
    o = np.asarray(origin, dtype=float)
    part.grid = (part.grid - o) @ np.asarray(R).T + o
    return part


def gem(name, centre, radius, depth, mat, sides=8):
    """A cut gem facing the viewer (axis z): a table each side, a girdle, facets."""
    prof = [(0.0, -depth / 2), (radius * 0.62, -depth * 0.42), (radius, -depth * 0.12), (radius, depth * 0.12),
            (radius * 0.62, depth * 0.42), (0.0, depth / 2)]
    p = MS.lathe(name, prof, sides, mat, origin=(0, 0, 0), crease=12.0, phase=math.pi / sides)
    turn(p, "x", 90, (0, 0, 0))
    p.grid = p.grid + np.asarray(centre)
    return p


def helix(radius, y0, y1, turns, n, phase=0.0):
    t = np.linspace(0, 1, n)
    a = phase + t * turns * 2 * math.pi
    return np.stack([radius * np.cos(a), y0 + (y1 - y0) * t, radius * np.sin(a)], axis=1)


# ---------------------------------------------------------------------------------------------------------------------
# Designs

def sovereign():
    """The Sovereign Longsword (S): the Court's oath made steel. A long ivory-white spire of a blade, gold-edged, its
    hollow-ground bevels catching the light; a crimson channel down the fuller where the Court's runes burn in turn; a
    crown for a guard, its quillons sweeping up into points, a cut crimson stone at its heart; a grip of void weave
    bound in gold wire; a gold star for a pommel round a second stone."""
    parts = []
    # the blade: stations up from the guard; width, thickness and the fuller by height; a true point
    y0, y1 = 4.1, 22.8
    n = 34
    s_ = np.linspace(0, 1, n) ** 0.9
    secs, tags = [], None
    for k, f in enumerate(s_):
        point = max(0.0, (f - 0.74) / 0.26)                         # 0 until the point begins, 1 at the tip
        taper = (1 - point) ** 1.15 * (1 - 0.25 * point * (1 - point))
        w = (2.75 - 0.4 * f) * taper if k < n - 1 else 0.0
        t = (0.86 - 0.36 * f) * (1 - point) ** 0.7 if k < n - 1 else 0.0
        fw = 0.5 * (1 - smooth(0.66, 0.76, f)) * smooth(0.0, 0.04, f)
        sec, tags = fullered(w, t, 0.5 * (1 - 0.25 * f), fw, 0.15 * smooth(0.0, 0.3, fw), lip=0.1)
        secs.append(sec)
    c, fr = MS.straight(y0, y1, n)
    c[:, 1] = y0 + (y1 - y0) * s_
    (fs, ft), (bs, bt) = MS.halves(secs, tags)
    for name, S, T in (("blade_front", fs, ft), ("blade_back", bs, bt)):
        parts.append(MS.sweep(name, c, fr, S, T, "ivory_blade", False, crease=28.0, data={"align": len(T) // 2, "y0": y0}))
    # the crown guard: a bar swept along a curve, its ends rising into points
    path = np.concatenate([MS.bezier((-5.4, 5.6, 0), (-4.6, 3.0, 0), (-2.0, 2.7, 0), (0, 2.9, 0), 14),
                           MS.bezier((0, 2.9, 0), (2.0, 2.7, 0), (4.6, 3.0, 0), (5.4, 5.6, 0), 14)[1:]])
    P, F = MS.transport(path, up=(0, 0, 1))
    m = len(P)
    sc = np.concatenate([np.linspace(0.18, 1, m // 2), np.linspace(1, 0.18, m - m // 2)])
    secs = [[(x * k, y * k) for x, y in diamond_round(1.25, 1.55)] for k in sc]
    parts.append(MS.sweep("guard", P, F, secs, np.linspace(0, 1, 8, endpoint=False), "gold", True, crease=35.0))
    for x in (-1.85, 1.85):  # the crown's points, rising off the bar beside the blade
        gy = np.interp(x, P[:, 0], P[:, 1])
        parts.append(spike(f"crown_{x}", (x, gy + 0.2, 0), (x * 1.12, gy + 1.5, 0), 0.5, "gold"))
    # the heart: a gold bezel round a cut crimson stone, rising over the blade's root
    parts.append(ring("bezel", (0, 3.9, 0), 1.0, 0.78, 1.7, "gold"))
    parts.append(gem("guard_gem", (0, 3.9, 0), 0.78, 2.0, "crimson_gem"))
    parts.append(spike("crown_mid", (0, 4.7, 0), (0, 6.2, 0), 0.62, "gold"))
    # the grip: void weave on a lathe, gold wire wound round it, gold ferrules at each end
    prof = [(0.0, -2.55), (0.72, -2.55), (0.8, -1.6), (0.84, 0.0), (0.8, 1.6), (0.72, 2.45), (0.0, 2.45)]
    parts.append(MS.lathe("grip", prof, 10, "weave", crease=70.0))
    parts.append(MS.tube("wire", helix(0.86, -2.2, 2.1, 4.5, 80), 0.1, 5, "gold", data={"no_shadow": True}))
    parts.append(ring("ferrule_top", (0, 2.45, 0), 1.0, 0.0, 0.45, "gold", axis="y"))
    parts.append(ring("ferrule_low", (0, -2.55, 0), 1.0, 0.0, 0.45, "gold", axis="y"))
    # the pommel: a gold star round the second stone
    parts += extrude("pommel", star(8, 1.75, 0.85, phase=math.pi / 8), -0.6, 0.6, "gold", centre=(0, -4.2))
    parts.append(gem("pommel_gem", (0, -4.2, 0), 0.62, 1.7, "crimson_gem"))
    anchors = [((0, 3.9, 0), 2.4, RAMPS["crimson"][5], 1), ((0, -4.2, 0), 1.9, RAMPS["crimson"][5], 1)]
    # motes hang round the Court's stones and drift up off the burning runes of the channel
    streams = [(MOTE, RAMPS["crimson"][6], 4, 2.0, [(0, 3.9, 0)]), (MOTE, RAMPS["crimson"][6], 2, 1.6, [(0, -4.2, 0)]),
               (MOTE, RAMPS["gold"][6], 3, 0.25, [(0, 5.2, 0.0), (0, 16.5, 0.0)])]
    return parts, anchors, dict(accent=RAMPS["crimson"][4], density=4.0, ramps=("ivory", "gold", "crimson", "weave"), streams=streams)


def smooth(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def diamond_round(h, d, n=8):
    """A rounded diamond section: h across (along the path's right), d deep."""
    return [((h / 2) * math.cos(2 * math.pi * k / n) * (1 - 0.18 * (k % 2)), (d / 2) * math.sin(2 * math.pi * k / n) * (1 - 0.18 * (k % 2)))
            for k in range(n)]


def spike(name, base, tip, width, mat, sides=6):
    """A tapering point from base to tip."""
    path = np.linspace(base, tip, 6)
    r = np.linspace(width / 2, 0.0, 6) ** 1.0
    return MS.tube(name, path, r, sides, mat)


def ring(name, centre, r_out, r_in, depth, mat, axis="z"):
    """A bezel or ferrule: a short round band (r_in 0 for a solid boss), facing along its axis."""
    rr = max(r_in, 0.0)
    prof = [(rr, -depth / 2), (r_out * 0.92, -depth / 2), (r_out, -depth * 0.25), (r_out, depth * 0.25), (r_out * 0.92, depth / 2), (rr, depth / 2)]
    p = MS.lathe(name, prof, 16, mat, crease=40.0)
    if axis == "z":
        turn(p, "x", 90, (0, 0, 0))
    p.grid = p.grid + np.asarray(centre)
    return p


def blade(name, mat, y0, y1, n, width, thick, bevel=lambda f: 0.5, fuller=lambda f: 0.0, depth=0.15, shift=lambda f: 0.0,
          jitter=0.0, seed=0, crease=28.0):
    """A blade lofted through its cross-sections from y0 (at the guard) to y1 (the point), each a function of how far up
    (0..1): width, thickness, bevel, fuller width and the centre's shift sideways; jitter knaps the section points.
    Two faces, each unwrapped about its fuller's line (so anything painted down it stays square)."""
    fs = np.linspace(0, 1, n) ** 0.9
    rng = np.random.default_rng(seed)
    secs, tags = [], None
    for k, f in enumerate(fs):
        w, t = (width(f), thick(f)) if k < n - 1 else (0.0, 0.0)
        fw = fuller(f)
        sec, tags = fullered(w, t, bevel(f), fw, depth * smooth(0.0, 0.3, fw), lip=0.1)
        if jitter and w > 0:
            sec = [(x + rng.uniform(-jitter, jitter), z + rng.uniform(-jitter, jitter) * 0.4) for x, z in sec]
        secs.append([(x + shift(f), z) for x, z in sec])
    c, fr = MS.straight(y0, y1, n)
    c[:, 1] = y0 + (y1 - y0) * fs
    (fsec, ft), (bsec, bt) = MS.halves(secs, tags)
    return [MS.sweep(name + "_front", c, fr, fsec, ft, mat, False, crease=crease, data={"align": len(ft) // 2, "y0": y0}),
            MS.sweep(name + "_back", c, fr, bsec, bt, mat, False, crease=crease, data={"align": len(bt) // 2, "y0": y0})]


def block(name, w, d, y0, y1, mat, r=0.2, bevel=0.12, x=0.0, z=0.0):
    """A bevelled block standing up the y axis, w across and d deep, its ends capped."""
    sec, tags = MS.rounded(w, d, r)
    ys = [y0, y0 + bevel, y1 - bevel, y1]
    ks = [0.86, 1.0, 1.0, 0.86]
    c, fr = MS.straight(y0, y1, 4, x, z)
    c[:, 1] = ys
    side = MS.sweep(name, c, fr, [[(px * k, pz * k) for px, pz in sec] for k in ks], tags, mat, True, crease=35.0)
    return [side, MS.cap(name + "_low", side, 0, mat), MS.cap(name + "_top", side, 1, mat)]


def ribbon(name, radius, y0, y1, turns, width, thick, mat, n=90, phase=0.0):
    """A flat strap wound round a grip: its broad side lies on the grip, turn after turn."""
    P = helix(radius + thick / 2, y0, y1, turns, n, phase)
    T = np.gradient(P, axis=0)
    T /= np.linalg.norm(T, axis=1, keepdims=True)
    out = P * [1, 0, 1]
    out /= np.linalg.norm(out, axis=1, keepdims=True)
    right = np.cross(T, out)
    F = np.stack([right, T, out], axis=1)
    sec, tags = MS.rounded(width, thick, thick * 0.45, 2)
    return MS.sweep(name, P, F, [sec] * n, tags, mat, True, crease=50.0)


def orb(name, centre, radius, mat, sides=8, stretch=1.0, axis=None):
    """A round cut stone (or bead): a faceted ball, stretched along y if asked."""
    prof = [(radius * math.sin(a), -radius * stretch * math.cos(a)) for a in np.linspace(0, math.pi, 7)]
    p = MS.lathe(name, prof, sides, mat, crease=14.0, phase=math.pi / sides)
    if axis == "z":
        turn(p, "x", 90, (0, 0, 0))
    p.grid = p.grid + np.asarray(centre)
    return p


def torus(name, centre, R, r, mat, n=36, sides=8):
    """A ring in the blade's plane (a pommel's loop)."""
    a = np.linspace(0, 2 * math.pi, n)
    P = np.stack([centre[0] + R * np.cos(a), centre[1] + R * np.sin(a), np.full(n, centre[2])], axis=1)
    return MS.tube(name, P, r, sides, mat, up=(0, 0, 1))


def curve(p0, p1, p2, p3, n=16):
    return MS.bezier(p0, p1, p2, p3, n)


def mirror_x(points):
    return np.asarray(points) * [-1, 1, 1]


# ---- E: Wildhide --------------------------------------------------------------------------------------------------

def wildhide():
    """The Wildhide Longsword (E): the first blade a hunter makes. A leaf of knapped flint, every flake struck off it a
    facet that glints as it turns, its edges thin enough to glow pale; lashed with sinew between two antler tines that
    fork up beside it; a bone grip wound in a stitched hide strap; an amber bead for a pommel, warm with its own light,
    and a tooth hung from it on a thong."""
    parts = []
    w = lambda f: 2.3 + 1.1 * math.sin(math.pi * min(1.0, f / 0.85)) ** 1.4 * (1 - smooth(0.75, 1.0, f) * 0.4)
    parts += blade("blade", "flint_blade", 3.6, 20.4, 26, lambda f: w(f) * (1 - smooth(0.78, 1.0, f)) ** 0.8 + 0.001,
                   lambda f: 1.0 - 0.45 * f, bevel=lambda f: 0.75, jitter=0.11, seed=11, crease=18.0)
    # the sinew lashing at the blade's root, and a bone collar under it
    parts += block("collar", 2.9, 1.5, 2.4, 3.9, "bone", r=0.5)
    parts.append(MS.tube("lashing", helix_ellipse(1.5, 0.82, 2.55, 3.75, 3.5, 70), 0.11, 5, "hide", data={"no_shadow": True}))
    for side in (-1, 1):  # antler tines forking up beside the blade
        main = curve((side * 1.1, 3.2, 0), (side * 2.6, 3.0, 0.1), (side * 3.4, 4.4, 0.2), (side * 3.0, 6.6, 0.1), 18)
        parts.append(MS.tube(f"tine_{side}", main, np.linspace(0.42, 0.06, len(main)), 7, "bone"))
        branch = curve((side * 2.9, 3.6, 0.1), (side * 3.6, 3.7, 0.1), (side * 4.3, 4.2, 0.1), (side * 4.5, 5.2, 0.0), 10)
        parts.append(MS.tube(f"tine_b_{side}", branch, np.linspace(0.24, 0.04, len(branch)), 6, "bone"))
    # the grip: bone, wound in hide
    prof = [(0.0, -2.5), (0.66, -2.5), (0.74, -1.6), (0.78, 0.0), (0.74, 1.6), (0.66, 2.4), (0.0, 2.4)]
    parts.append(MS.lathe("grip", prof, 10, "bone", crease=70.0))
    parts.append(ribbon("wrap", 0.76, -2.2, 2.1, 3.2, 0.62, 0.14, "hide"))
    # the pommel: an amber bead in a bone cup, a tooth on a thong
    parts.append(MS.lathe("cup", [(0.0, -2.5), (0.8, -2.55), (0.95, -2.9), (0.7, -3.2), (0.0, -3.2)], 10, "bone", crease=50.0))
    parts.append(orb("bead", (0, -3.85, 0), 0.82, "amber_gem", sides=10))
    thong = curve((0.3, -3.2, 0.5), (0.9, -3.6, 0.9), (1.2, -4.4, 0.9), (1.0, -5.2, 0.7), 12)
    parts.append(MS.tube("thong", thong, 0.07, 4, "hide", data={"no_shadow": True}))
    parts.append(spike("tooth", (1.0, -5.1, 0.7), (0.85, -6.3, 0.6), 0.42, "bone"))
    anchors = [((0, -3.85, 0), 1.6, RAMPS["amber"][5], 1)]
    return parts, anchors, dict(accent=RAMPS["amber"][4], density=4.0, ramps=("flint", "bone", "hide", "amber"))


def helix_ellipse(rx, rz, y0, y1, turns, n):
    t = np.linspace(0, 1, n)
    a = t * turns * 2 * math.pi
    return np.stack([rx * np.cos(a), y0 + (y1 - y0) * t, rz * np.sin(a)], axis=1)


# ---- D: Beastbound ------------------------------------------------------------------------------------------------

def cleaver(xl, xr, t, spine, bevel):
    """A straight-backed blade's section: a squared spine on the left, the cutting edge on the right. Tags: 0 the
    edge, 1 the bevel, 2 the flat, 5 the spine."""
    pts = [(xr, 0.0, 0), (xr - bevel, t / 2, 1), ((xl + xr) / 2, t / 2 * 1.05, 2), (xl + 0.25, spine / 2, 2), (xl, spine / 2, 5),
           (xl, -spine / 2, 5), (xl + 0.25, -spine / 2, 2), ((xl + xr) / 2, -t / 2 * 1.05, 2), (xr - bevel, -t / 2, 1)]
    return [(x, z) for x, z, _ in pts], [k for _, _, k in pts]


def beastbound():
    """The Beastbound Longsword (D): a trophy of the hunt. A broad cleaver of boar-tusk ivory, its back squared and
    capped in bronze and set with teeth, its point clipped up to the spine; two tusks for a guard, curling back to
    the hand from a riveted bronze collar; a grip wrapped in pelt between bronze rings; a bronze claw gripping a
    moss-green stone."""
    parts = []
    n = 26
    fs = np.linspace(0, 1, n) ** 0.9
    y0, y1 = 3.7, 19.8
    secs, tags = [], None
    for k, f in enumerate(fs):
        xl = -1.45 + smooth(0.68, 1.0, f) ** 1.2 * 2.35                  # the spine, straight until it clips up
        xr = 1.55 + 0.3 * math.sin(math.pi * min(1.0, f / 0.9)) - smooth(0.78, 1.0, f) * 0.65  # the edge's belly
        if k == n - 1:
            xl = xr = 0.9
        t = (1.0 - 0.35 * f) * (1 - smooth(0.85, 1.0, f) * 0.9)
        sec, tags = cleaver(xl, xr, t, t * 1.25, 0.55)
        secs.append(sec)
    c, fr = MS.straight(y0, y1, n)
    c[:, 1] = y0 + (y1 - y0) * fs
    parts.append(MS.sweep("blade", c, fr, secs, tags, "tusk_blade", True, crease=30.0))
    for k, f in enumerate((0.12, 0.27, 0.42, 0.57)):  # teeth on the spine
        y = y0 + (y1 - y0) * f ** 0.9 + 0.2
        size = 0.55 - 0.08 * k
        parts.append(spike(f"tooth_{k}", (-1.4, y, 0), (-2.1, y + 0.55, 0), size, "bone"))
    parts += block("collar", 3.4, 1.9, 2.3, 3.9, "bronze", r=0.35)
    for x in (-1.1, 1.1):
        parts.append(orb(f"rivet_{x}", (x, 3.1, 0.95), 0.26, "bronze", sides=6, axis="z"))
        parts.append(orb(f"rivet_b{x}", (x, 3.1, -0.95), 0.26, "bronze", sides=6, axis="z"))
    for side in (-1, 1):  # tusks curling back toward the hand
        path = curve((side * 1.4, 3.1, 0), (side * 3.4, 3.6, 0), (side * 4.4, 1.6, 0), (side * 3.1, 0.6, 0), 20)
        parts.append(MS.tube(f"tusk_{side}", path, np.linspace(0.5, 0.07, len(path)), 8, "bone"))
    prof = [(0.0, -2.5), (0.74, -2.5), (0.86, -1.5), (0.9, 0.0), (0.86, 1.5), (0.74, 2.3), (0.0, 2.3)]
    parts.append(MS.lathe("grip", prof, 10, "pelt", crease=70.0))
    for y in (-2.45, -0.2, 2.15):
        parts.append(ring(f"band_{y}", (0, y, 0), 0.98, 0.0, 0.36, "bronze", axis="y"))
    # the pommel: a bronze claw round a moss stone
    parts.append(orb("stone", (0, -3.55, 0), 0.78, "moss_gem", sides=8))
    for k in range(3):
        a = k * 2 * math.pi / 3 + 0.3
        ca, sa = math.cos(a), math.sin(a)
        path = curve((0.45 * ca, -2.6, 0.45 * sa), (1.15 * ca, -2.9, 1.15 * sa), (1.05 * ca, -3.9, 1.05 * sa), (0.35 * ca, -4.45, 0.35 * sa), 12)
        parts.append(MS.tube(f"claw_{k}", path, np.linspace(0.3, 0.05, len(path)), 6, "bronze"))
    anchors = [((0, -3.55, 0), 1.5, RAMPS["moss"][6], 1)]
    return parts, anchors, dict(accent=RAMPS["moss"][5], density=4.0, ramps=("bone", "bronze", "pelt", "moss"))


# ---- C: Gatewrought -----------------------------------------------------------------------------------------------

def gatewrought():
    """The Gatewrought Longsword (C): forged from a Gate's own metal by its precursors' rules. A long straight blade of
    slate-blue gate-steel ground to a raised rib, its edges mirror-honed, the signal circuit inlaid down its fuller,
    light climbing it in pulses; the guard a gate in brass, two pylons the blade stands between, signal strips lit in
    their faces; a ribbed slate grip; a brass ring for a pommel with a signal crystal floating in it."""
    parts = []
    parts += blade("blade", "slate_blade", 3.5, 21.6, 30, lambda f: (2.5 - 0.3 * f) * (1 - smooth(0.8, 1.0, f)) ** 0.9 + 0.001,
                   lambda f: 0.85 - 0.35 * f, bevel=lambda f: 0.5, fuller=lambda f: 0.62 * (1 - smooth(0.7, 0.8, f)) * smooth(0.0, 0.04, f),
                   depth=0.14)
    parts += block("bar", 6.2, 1.5, 2.5, 3.5, "brass", r=0.3)
    for side in (-1, 1):
        x = side * 2.35
        parts += block(f"pylon_{side}", 1.0, 1.7, 2.2, 5.4, "brass", r=0.25, x=x)
        parts += block(f"cap_{side}", 1.35, 2.0, 5.3, 5.8, "brass", r=0.2, x=x)
        parts += block(f"strip_{side}", 0.34, 1.86, 3.0, 5.0, "signal", r=0.08, x=x)
    prof = [(0.0, -2.5), (0.7, -2.5)] + [(0.7 + 0.1 * (k % 2), -2.1 + k * 0.42) for k in range(11)] + [(0.7, 2.4), (0.0, 2.4)]
    parts.append(MS.lathe("grip", prof, 10, "slate", crease=40.0))
    parts.append(ring("ferrule_top", (0, 2.4, 0), 0.95, 0.0, 0.4, "brass", axis="y"))
    parts.append(ring("ferrule_low", (0, -2.5, 0), 0.95, 0.0, 0.4, "brass", axis="y"))
    parts.append(torus("loop", (0, -4.0, 0), 1.15, 0.26, "brass"))
    parts.append(MS.lathe("neck", [(0.0, -2.6), (0.5, -2.6), (0.42, -2.9), (0.0, -2.9)], 8, "brass"))
    parts.append(orb("crystal", (0, -4.0, 0), 0.5, "signal_gem", sides=6, stretch=1.7))
    anchors = [((0, -4.0, 0), 1.6, RAMPS["signal"][6], 1)]
    return parts, anchors, dict(accent=RAMPS["signal"][5], density=4.0, ramps=("slate", "brass", "signal", "mirror"))


# ---- B: Veilwoven -------------------------------------------------------------------------------------------------

def veilwoven():
    """The Veilwoven Longsword (B): steel that has been through the Veil and back. A flame-waved blade, near-black
    violet with a sheen that slides like oil down it; one edge is living spirit light, the other mirror-honed; quillons
    of mirror-silver swept up like wings; an obsidian grip ringed in mirror; a teardrop of mirror holding a spirit
    stone."""
    parts = []
    wave = lambda f: 0.26 * math.sin(f * math.pi * 7.0) * (1 - smooth(0.7, 0.85, f))
    parts += blade("blade", "veil_blade", 3.6, 21.2, 44, lambda f: (2.5 - 0.2 * f) * (1 - smooth(0.8, 1.0, f)) ** 0.85 + 0.001,
                   lambda f: 0.8 - 0.32 * f, bevel=lambda f: 0.6, shift=wave)
    parts += block("heart", 1.9, 1.7, 2.4, 4.0, "mirror", r=0.4)
    parts.append(gem("heart_gem", (0, 3.2, 0), 0.55, 2.0, "spirit_gem"))
    for side in (-1, 1):
        path = curve((side * 0.9, 3.2, 0), (side * 2.6, 3.0, 0), (side * 3.8, 4.0, 0.2), (side * 4.2, 6.4, 0.5), 18)
        parts.append(MS.tube(f"quillon_{side}", path, np.linspace(0.42, 0.05, len(path)), 7, "mirror"))
        under = curve((side * 0.9, 2.8, 0), (side * 2.0, 2.4, 0), (side * 2.8, 2.6, 0), (side * 3.2, 3.4, 0.2), 12)
        parts.append(MS.tube(f"under_{side}", under, np.linspace(0.26, 0.04, len(under)), 6, "mirror"))
    prof = [(0.0, -2.5), (0.66, -2.5), (0.72, -1.5), (0.76, 0.0), (0.72, 1.5), (0.66, 2.4), (0.0, 2.4)]
    parts.append(MS.lathe("grip", prof, 10, "obsidian", crease=70.0))
    for y in (-2.45, -1.0, 0.5, 2.1):
        parts.append(ring(f"band_{y}", (0, y, 0), 0.86, 0.0, 0.28, "mirror", axis="y"))
    drop = [(0.0, -2.55), (0.55, -2.7), (0.95, -3.3), (1.0, -3.9), (0.8, -4.5), (0.4, -4.95), (0.0, -5.1)]
    parts.append(MS.lathe("drop", drop, 12, "mirror", crease=40.0))
    parts.append(gem("drop_gem", (0, -3.9, 0), 0.6, 2.4, "spirit_gem"))
    anchors = [((0, 3.2, 0), 1.6, RAMPS["spirit"][6], 1), ((0, -3.9, 0), 1.7, RAMPS["spirit"][6], 1)]
    # the spirit edge (the -x side, as painted) lets go of wisps that drift and curl away; the stones breathe motes
    width = lambda f: (2.5 - 0.2 * f) * (1 - smooth(0.8, 1.0, f)) ** 0.85 + 0.001
    streams = [(WISP, RAMPS["spirit"][6], 5, 0.18, edge_line(3.6, 21.2, 44, width, -1, shift=wave)),
               (MOTE, RAMPS["spirit"][7], 2, 1.2, [(0, 3.2, 0)])]
    return parts, anchors, dict(accent=RAMPS["spirit"][5], density=4.0, ramps=("veil", "mirror", "spirit", "obsidian"), streams=streams)


# ---- A: Lordscale -------------------------------------------------------------------------------------------------

def wing_shape(span, rise, feathers=4):
    """A wing's outline in the blade's plane, reaching out and up from the guard, its trailing edge scalloped."""
    pts = [(0.0, 0.0), (span * 0.35, -0.35), (span * 0.8, 0.2), (span, rise * 0.55), (span * 0.95, rise)]
    for k in range(feathers):  # back along the trailing edge, a scallop per feather
        f0 = 1 - (k + 0.5) / feathers
        pts.append((span * (0.92 * f0 + 0.04), rise * (0.55 + 0.4 * f0) - 0.25))
        pts.append((span * (0.92 * (f0 - 0.5 / feathers) + 0.04), rise * (0.5 + 0.4 * f0)))
    pts.append((0.15, 0.9))
    return pts


def lordscale():
    """The Lordscale Longsword (A): cut from a fallen Lord. A broad blade of black scale, each scale rimmed where the
    light finds it, a gold ridge down its middle, its edges molten gold that flows toward the point; a guard of two
    wings spread up and out, layered in scale; a grip of black scale banded in gold; a gold claw holding the sky's
    pearl."""
    parts = []
    parts += blade("blade", "lord_blade", 3.8, 21.4, 30, lambda f: (3.3 - 0.55 * f) * (1 - smooth(0.78, 1.0, f)) ** 0.8 + 0.001,
                   lambda f: 1.0 - 0.45 * f, bevel=lambda f: 0.75)
    parts += block("heart", 2.6, 1.9, 2.3, 4.2, "lord", r=0.45)
    parts.append(gem("heart_gem", (0, 3.25, 0), 0.62, 2.2, "pearl_gem"))
    parts.append(ring("heart_bezel", (0, 3.25, 0), 0.82, 0.62, 2.0, "gold"))
    for side in (-1, 1):
        for layer, (span, rise, z, mat) in enumerate(((4.7, 3.5, -0.2, "gold"), (3.9, 2.9, 0.3, "lord_scale"))):
            pts = [(side * x, y) for x, y in wing_shape(span, rise)]
            if side < 0:
                pts = pts[::-1]
            pts = [(x + side * 1.0, y + 2.9) for x, y in pts]
            d = 0.7 - 0.25 * layer
            parts += extrude(f"wing_{side}_{layer}", pts, z - d / 2 + (0 if layer == 0 else 0.0), z + d / 2, mat, bevel=0.1)
    prof = [(0.0, -2.5), (0.74, -2.5), (0.8, -1.5), (0.84, 0.0), (0.8, 1.5), (0.74, 2.3), (0.0, 2.3)]
    parts.append(MS.lathe("grip", prof, 10, "lord_scale", crease=70.0))
    for y in (-2.45, 0.0, 2.2):
        parts.append(ring(f"band_{y}", (0, y, 0), 0.95, 0.0, 0.34, "gold", axis="y"))
    parts.append(orb("pearl", (0, -3.6, 0), 0.82, "pearl_gem", sides=10))
    for k in range(4):
        a = k * math.pi / 2 + math.pi / 4
        ca, sa = math.cos(a), math.sin(a)
        path = curve((0.4 * ca, -2.6, 0.4 * sa), (1.2 * ca, -2.8, 1.2 * sa), (1.1 * ca, -3.9, 1.1 * sa), (0.35 * ca, -4.5, 0.35 * sa), 12)
        parts.append(MS.tube(f"claw_{k}", path, np.linspace(0.28, 0.04, len(path)), 6, "gold"))
    anchors = [((0, 3.25, 0), 1.7, RAMPS["pearl"][4], 1), ((0, -3.6, 0), 1.7, RAMPS["pearl"][4], 1)]
    # the molten edges shed embers that rise and cool, more as the blade fills with light
    width = lambda f: (3.3 - 0.55 * f) * (1 - smooth(0.78, 1.0, f)) ** 0.8 + 0.001
    streams = [(EMBER, RAMPS["ember"][6], 7, 0.12, edge_line(3.8, 21.4, 30, width, side)) for side in (-1, 1)]
    return parts, anchors, dict(accent=RAMPS["ember"][5], density=4.0, ramps=("lord", "gold", "ember", "pearl"), streams=streams)


# ---- what lifts off a weapon (SPEC §25 "Weapons"): embers, wisps, motes, drawn by the effects engine ----------------
EMBER, WISP, MOTE = 1, 2, 3


def edge_line(y0, y1, n, width, side, shift=lambda f: 0.0, f0=0.04, f1=0.97, samples=8):
    """One edge of a blade() as a polyline in sword space: the same stations the loft uses, at half the width."""
    pts = []
    for f in np.linspace(f0, f1, samples):  # width and shift take the height fraction, as in the loft
        pts.append((side * width(f) / 2 + shift(f), y0 + (y1 - y0) * f, 0.0))
    return pts


DESIGNS = {"E": wildhide, "D": beastbound, "C": gatewrought, "B": veilwoven, "A": lordscale, "S": sovereign}
# the light of each tier's swings (a bright step of its own glow)
TRAILS = {"E": "#f1ebd6", "D": "#b9d890", "C": "#7fc3ff", "B": "#c3cbff", "A": "#f3c443", "S": "#ff9080"}

# ---------------------------------------------------------------------------------------------------------------------
# Building: sword space to item space, the mesh, the icon


def to_item(P, direction=False):
    a = math.radians(ANGLE)
    R = np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])
    Q = np.asarray(P) @ R.T
    return Q if direction else Q * SCALE + np.asarray(HAND)


def along(parts):
    """How far along the weapon each row of each part lies (the sheen's track): by sword-space height, 0 at the pommel."""
    lo = min(p.grid[..., 1].min() for p in parts)
    hi = max(p.grid[..., 1].max() for p in parts)
    for p in parts:
        p.along = (p.grid[..., 1].mean(axis=1) - lo) / (hi - lo)


def make(row, clean=False):
    """One weapon from its design: (the built mesh, the design's look); clean paints it for the icon."""
    global GRAIN
    GRAIN = 0.0 if clean else 1.0
    tier = row[2]
    parts, anchors, look = DESIGNS[tier]()
    along(parts)
    item_anchors = [(tuple(to_item(np.array([c]))[0]), r * SCALE, hex_rgb(rgb), kind) for c, r, rgb, kind in anchors]
    built = MS.build(parts, painter_for(parts, look), density=look["density"], width=128, to_item=to_item, anchors=item_anchors)
    built.streams = [(kind, hex_rgb(rgb), rate, radius * SCALE, [tuple(q) for q in to_item(np.array(pts))])
                     for kind, rgb, rate, radius, pts in look.get("streams", [])]
    GRAIN = 1.0
    return built, look


def hex_rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def icon(built, look, size=32):
    """The inventory icon, drawn from the mesh: seen from the front in the inventory's flat light. Each pixel takes the
    surface's colour at its centre, as a pixel artist would pick one (averaging turns pixel art to mud); the silhouette
    comes from how much of the pixel the weapon covers; vanilla's dark outline goes round it."""
    m = {"pos": built.pos / 16.0, "nrm": built.nrm, "uv": built.uv, "along": built.along, "accent": hex_rgb(look["accent"])}
    lo, hi = m["pos"].min(axis=0), m["pos"].max(axis=0)
    span = max(hi[0] - lo[0], hi[1] - lo[1]) * 16.0               # the weapon's extent seen from the front, item units
    key = np.array([255, 0, 255])

    def shot(k):
        img = MR.render(m, built.texture(), 0, 0, scale=(size - 2.2) * k / span, size=(size * k, size * k), centre=(lo + hi) / 2,
                        bg=key, shader=MR.shade_icon)
        a = np.asarray(img).astype(int)
        return a, ~((a[..., 0] > 250) & (a[..., 1] < 5) & (a[..., 2] > 250))

    centre, hit = shot(1)
    _, fine = shot(5)
    cov = fine.reshape(size, 5, size, 5).mean(axis=(1, 3))
    on = (cov > 0.38) | hit
    out = np.zeros((size, size, 4), dtype=np.uint8)
    # a covered pixel whose centre missed the weapon borrows its nearest hit neighbour's colour
    col = centre.copy()
    for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        nb, nh = np.roll(np.roll(centre, dy, 0), dx, 1), np.roll(np.roll(hit, dy, 0), dx, 1)
        fill = on & ~hit & nh
        col[fill] = nb[fill]
        hit = hit | fill
    out[on, :3] = col[on]
    out[on, 3] = 255
    rim = np.zeros_like(on)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        rim |= np.roll(np.roll(on, dy, 0), dx, 1)
    rim &= ~on
    out[rim] = (12, 10, 18, 255)
    return Image.fromarray(out, "RGBA")


# ---------------------------------------------------------------------------------------------------------------------
# Output

def write_all(only=None):
    lang, java_rows = {}, []
    mesh_dir = os.path.join(RES, "assets/awaken/arms/weapons")
    tex_dir = os.path.join(RES, "assets/awaken/textures/item/arms")
    os.makedirs(mesh_dir, exist_ok=True)
    os.makedirs(tex_dir, exist_ok=True)
    for row in CATALOG:
        wid, name, tier, dmg, dur, rarity, ench, repair = row
        lang["item.awaken." + wid] = name
        trail = "0x" + TRAILS[tier].lstrip("#").upper()
        java_rows.append(f'            new Weapon("{wid}", "longsword", "{tier}", {dmg}F, {LONGSWORD_SPEED}F, {dur}, Rarity.{rarity}, {ench}, "{repair}", {trail})')
        if tier not in DESIGNS or (only and wid not in only):
            continue
        built, look = make(row)
        MS.write(os.path.join(mesh_dir, wid + ".mesh"), built, hex_rgb(look["accent"]))
        Image.fromarray(built.texture(), "RGBA").save(os.path.join(tex_dir, wid + ".png"))
        icon(make(row, clean=True)[0], look).save(os.path.join(tex_dir, wid + "_icon.png"))
        tex = "awaken:item/arms/" + wid
        _write(os.path.join(RES, "assets/awaken/models/item", wid + ".json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": tex + "_icon"}})
        # the hands' base: a particle, no geometry of its own (the mesh draws itself), vanilla's third-person hold and
        # our own first-person one (FIRST_PERSON below)
        _write(os.path.join(RES, "assets/awaken/models/item", wid + "_in_hand.json"), {
            "parent": "minecraft:item/handheld", "textures": {"particle": tex + "_icon"}, "display": FIRST_PERSON})
        _write(os.path.join(RES, "assets/awaken/items", wid + ".json"), {"model": {
            "type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": {"type": "minecraft:model", "model": "awaken:item/" + wid}}],
            "fallback": {"type": "minecraft:special", "base": "awaken:item/" + wid + "_in_hand", "model": {"type": "awaken:weapon", "weapon": wid}}}})
        _write(os.path.join(RES, "data/awaken/recipe", wid + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment", "group": "awaken_arms_longsword",
            "key": {"C": "#awaken:hunter_core_" + tier.lower(), "X": "awaken:" + G.SET[tier] + "_blade", "B": "#awaken:hunter_bind_" + tier.lower()},
            "pattern": ["C", "X", "B"], "result": {"id": "awaken:" + wid}})
        for stale in (wid + "_icon_glow.png",):
            if os.path.exists(os.path.join(tex_dir, stale)):
                os.remove(os.path.join(tex_dir, stale))
    _write(os.path.join(MERGE, "data/minecraft/tags/item/swords.json"), {"values": ["awaken:" + row[0] for row in CATALOG]})
    lpath = os.path.join(MERGE, "assets/awaken/lang/en_us.json")
    cur = json.load(open(lpath)) if os.path.exists(lpath) else {}
    cur.update(lang)
    with open(lpath, "w") as fh:
        json.dump(dict(sorted(cur.items())), fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    with open(JAVA, "w") as fh:
        fh.write(JAVA_HEAD + ",\n".join(java_rows) + "\n    );\n}\n")


# First person, at rest: the weapon presented, not a slab of blade (vanilla's sword sits so close that a longsword shows
# a fragment). The whole blade stands at the right, its point in view and its flat turned a little to the eye; a little
# smaller than vanilla's hold, further in. Chosen with mesh_render.render_fp over the game's own frame (it matches
# vanilla's projection exactly).
FIRST_PERSON = {
    "firstperson_righthand": {"rotation": [0, -72, 30], "translation": [0.3, 2.2, -0.6], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [0, 72, -30], "translation": [0.3, 2.2, -0.6], "scale": [0.6, 0.6, 0.6]},
}

JAVA_HEAD = """package dev.awaken.arms;

import net.minecraft.world.item.Rarity;

import java.util.List;

// GENERATED by tools/art/specs/arms_weapons.py from its CATALOG: edit the catalog there, not this file.

/**
 * Arms' weapons as plain data (SPEC §24): ids, fighting style, Hunter tier, stats. {@link ArmsItems} registers one item
 * per row; the meshes, textures, recipes and names come from the same catalog.
 */
public final class ArmsData {
    private ArmsData() {}

    /**
     * damage: attack damage over the player's 1; speed: the attack speed modifier; enchantability as vanilla's; repair:
     * the item tag that mends it; trail: the light of its swings (rgb).
     */
    public record Weapon(String id, String style, String tier, float damage, float speed, int durability, Rarity rarity,
                         int enchantability, String repair, int trail) {}

    public static final List<Weapon> WEAPONS = List.of(
"""


def _write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(obj, fh, indent=1)
        fh.write("\n")


def build(out):
    """build_all.py entry."""
    write_all()


def gallery(out, rows):
    """The review sheet: per weapon a three-quarter view, the first-person hold (the game's own projection) and the
    icon at 4x, with its name."""
    from PIL import ImageDraw
    cell_w, cell_h = 360, 500
    sheet = Image.new("RGB", (cell_w * 3, cell_h * 2), (22, 24, 31))
    d = ImageDraw.Draw(sheet)
    for i, (row, built, look) in enumerate(rows):
        m = {"pos": built.pos / 16.0, "nrm": built.nrm, "uv": built.uv, "along": built.along, "accent": hex_rgb(look["accent"])}
        x0, y0 = (i % 3) * cell_w, (i // 3) * cell_h
        turn_ = MR.render(m, built.texture(), 30, 16, scale=11.5, size=(cell_w, 290), centre=(0.55, 0.55, 0.5), time=0.27,
                          bg=np.array([22, 24, 31]))
        sheet.paste(turn_, (x0, y0 + 24))
        fp = MR.render_fp(m, built.texture(), display=MR.held(row[0]), size=(480, 270), time=0.27, bg=np.array([88, 96, 112]))
        sheet.paste(fp.crop((300, 30, 480, 270)).resize((135, 180)), (x0 + 20, y0 + 314))
        ic = icon(make(row, clean=True)[0], look).resize((128, 128), Image.NEAREST)
        sheet.paste(ic, (x0 + 200, y0 + 340), ic)
        d.text((x0 + 20, y0 + 300), "first person, at rest", fill=(150, 156, 170))
        d.text((x0 + 200, y0 + 326), "icon (4x)", fill=(150, 156, 170))
        d.text((x0 + 10, y0 + 6), f"{row[1]}  ({row[2]})", fill=(230, 232, 240))
    sheet.save(out)
    return out


def preview(out_dir, only=None):
    os.makedirs(out_dir, exist_ok=True)
    outs, rows = [], []
    for row in CATALOG:
        if row[2] not in DESIGNS or (only and row[0] not in only):
            continue
        built, look = make(row)
        m = {"pos": built.pos / 16.0, "nrm": built.nrm, "uv": built.uv, "along": built.along, "accent": hex_rgb(look["accent"])}
        tex = built.texture()
        tiles = [MR.render(m, tex, yaw, pitch, scale=26, size=(560, 560), centre=(0.6, 0.6, 0.5), time=0.27)
                 for yaw, pitch in ((0, 0), (35, 18), (-40, 12), (80, 10))]
        close = MR.render(m, tex, 25, 15, scale=70, size=(700, 560), centre=(0.47, 0.47, 0.5), time=0.27)
        ic = icon(make(row, clean=True)[0], look).resize((128, 128), Image.NEAREST)
        sheet = Image.new("RGB", (560 * 4 + 700 + 128 + 60, 580), (24, 26, 34))
        for i, t in enumerate(tiles):
            sheet.paste(t, (10 + i * 570, 10))
        sheet.paste(close, (10 + 4 * 570, 10))
        sheet.paste(ic, (20 + 4 * 570 + 700, 10), ic)
        path = os.path.join(out_dir, row[0] + ".png")
        sheet.save(path)
        Image.fromarray(tex, "RGBA").resize((tex.shape[1] * 4, tex.shape[0] * 4), Image.NEAREST).save(os.path.join(out_dir, row[0] + "_tex.png"))
        outs.append(path)
        rows.append((row, built, look))
    if not only:
        outs.append(gallery(os.path.join(out_dir, "weapons.png"), rows))
    return outs


BUDGET = 18_000  # triangle corners per weapon, the same as ArmsTests.MESH_BUDGET


def check():
    """Each weapon built: within budget, inside the item box, its atlas fitting, its icon not empty."""
    bad = []
    for row in CATALOG:
        built, look = make(row)
        n = len(built.pos)
        lo, hi = built.pos.min(axis=0) / 16.0, built.pos.max(axis=0) / 16.0
        if n > BUDGET:
            bad.append(f"{row[0]}: {n} corners (at most {BUDGET})")
        if lo.min() < -1 or hi.max() > 2:
            bad.append(f"{row[0]}: outside the item box ({lo.round(2)}, {hi.round(2)})")
        if built.albedo.shape[0] > 256:
            bad.append(f"{row[0]}: atlas {built.albedo.shape[0]} tall")
        alpha = np.asarray(icon(make(row, clean=True)[0], look))[..., 3]
        if (alpha > 0).mean() < 0.12:
            bad.append(f"{row[0]}: its icon is nearly empty")
        print(f"  {row[0]:24s} {n // 3:5d} triangles  atlas {built.albedo.shape[1]}x{built.albedo.shape[0]}")
    return bad


def main(argv):
    cmd = argv[0] if argv else "build"
    if cmd in ("build", "check"):
        bad = check()
        for b in bad:
            print("CHECK", b)
        if bad:
            return 1
        if cmd == "build":
            write_all(argv[1:] or None)
            print("written")
        return 0
    if cmd == "preview":
        print("\n".join(preview(argv[1] if len(argv) > 1 else ".", argv[2:] or None)))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
