"""Hunter gear (gear module): every texture, the content JSON, and a look-test preview.

    python3 tools/art/build_all.py gear          item icons (28) and armour layers (4 sets x humanoid + leggings)
    python3 tools/art/specs/gear.py content      item models and definitions, equipment assets, recipes, hook tags,
                                                 vanilla tags, level-road steps, lang (src/main/resources, merge/gear)
    python3 tools/art/specs/gear.py preview DIR  contact sheet of the icons and each set drawn on a figure, front and back

Tiers mirror dev.awaken.gear.HunterTier (E Wildhide, D Beastbound, C Gatewrought, B Veilwoven); keep the two in step.
Look: dark slate, amber lantern and signal blue are the mod's identity; each tier wears its materials' ramps (E hide,
bone and amber; D tusk, bronze and the beasts' moss green; C gate slate, brass and signal runes; B Veil violet and
mirror silver) and the rank colour on its gem.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, ART)
from pixelart import *  # noqa: E402,F401
from pixelart import _outline  # noqa: E402

RES = os.path.join(ROOT, "src/main/resources")
MERGE = os.path.join(ROOT, "src/main/merge/gear")

# ---------------------------------------------------------------------------------------------------------------------
# Data (mirrors HunterTier / Piece in Java)

TIERS = ["E", "D", "C", "B"]
SET = {"E": "wildhide", "D": "beastbound", "C": "gatewrought", "B": "veilwoven"}
SET_NAME = {"E": "Wildhide", "D": "Beastbound", "C": "Gatewrought", "B": "Veilwoven"}
PIECES = ["helmet", "chestplate", "leggings", "boots", "blade", "focus", "mattock"]
ARMOUR = PIECES[:4]

NAMES = {
    "E": ["Hood", "Vest", "Leggings", "Boots", "Fang", "Charm", "Mattock"],
    "D": ["Helm", "Hauberk", "Greaves", "Boots", "Cleaver", "Totem", "Mattock"],
    "C": ["Helm", "Cuirass", "Greaves", "Sabatons", "Sabre", "Lens", "Mattock"],
    "B": ["Cowl", "Mantle", "Leggings", "Treads", "Edge", "Censer", "Mattock"],
}


def item_id(t, piece):
    return SET[t] + "_" + piece


def display(t, piece):
    return SET_NAME[t] + " " + NAMES[t][PIECES.index(piece)]


# Hook tags: new creature families add their materials to these from their own src/main/merge/<module>/ folder.
OPT = lambda i: {"id": i, "required": False}  # noqa: E731  (other modules' items: a rename never breaks the tag)
TAGS = {
    "hunter_hide_e": ["minecraft:leather", "minecraft:rabbit_hide", "minecraft:phantom_membrane"],
    "hunter_core_e": ["minecraft:bone", "minecraft:flint", "minecraft:quartz"],
    "hunter_bind_e": ["minecraft:string", "minecraft:feather", "minecraft:moss_block"],
    "hunter_hide_d": ["minecraft:leather", "minecraft:turtle_scute", "minecraft:armadillo_scute", "minecraft:phantom_membrane"],
    # named beasts' trophies and the E-rank Gate Lords' parts
    "hunter_core_d": [OPT("awaken:" + i) for i in ["briar_tusk", "wyrm_scale", "antler_tine", "bell_clapper", "crypt_bronze",
                                                   "spore_sac", "mother_cap", "tyrant_crown", "molten_core", "regent_stinger",
                                                   "royal_jelly", "saint_heartwood", "temple_resin"]],
    "hunter_bind_d": ["minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:gold_ingot"],
    "hunter_hide_c": ["minecraft:turtle_scute", "minecraft:armadillo_scute", "minecraft:phantom_membrane"],
    # the D-rank Gate Lords' parts
    "hunter_core_c": [OPT("awaken:" + i) for i in ["archivist_quill", "waterlogged_codex", "colossus_gear", "mainspring",
                                                   "serpent_scale", "sand_glass_fang", "broker_lantern", "gilded_ledger"]],
    "hunter_bind_c": ["minecraft:gold_ingot", "minecraft:emerald", "minecraft:amethyst_shard"],
    "hunter_hide_b": ["minecraft:turtle_scute", "minecraft:armadillo_scute", "minecraft:shulker_shell"],
    # the C- and B-rank Gate Lords' parts
    "hunter_core_b": [OPT("awaken:" + i) for i in ["abbot_prayer_beads", "glacier_heart", "mirror_shard_crown", "knight_reflection",
                                                   "zero_core", "zero_plating", "sleeper_eyelid", "sleepers_lullaby",
                                                   "leviathan_fin", "sky_pearl"]],
    # touched by the Veil: echoes, dreams and the pearls that skip between places
    "hunter_bind_b": ["minecraft:echo_shard", OPT("awaken:dream_residue"), "minecraft:ender_pearl"],
}

# Recipes: H hide, C core, B bind, S essence shard, L Lord's Heart, / stick. Patterns differ within a tier.
RECIPES = {
    "E": {
        "helmet": ["HSH", "H H"],
        "chestplate": ["H H", "HSH", "BHB"],
        "leggings": ["HSH", "B B", "H H"],
        "boots": ["BSB", "H H"],
        "blade": ["C ", "CS", "B "],
        "focus": ["BSB", " C ", " C "],
        "mattock": ["CCC", "B/S", " / "],
    },
    "D": {
        "helmet": ["HCH", "BSB"],
        "chestplate": ["HSH", "BCB", "HSH"],
        "leggings": ["BCB", "S S", "H H"],
        "boots": ["BSB", "HCH"],
        "blade": ["CS", "B ", "B "],
        "focus": ["SBS", " C ", " B "],
        "mattock": ["BCB", " /S", " / "],
    },
    "C": {
        "helmet": ["BCB", "HSH"],
        "chestplate": ["HSH", "BLB", "SCS"],
        "leggings": ["BCB", "S S", "H H"],
        "boots": ["S S", "HCH"],
        "blade": [" C", "SC", "B "],
        "focus": ["SLS", " C ", " B "],
        "mattock": ["CBC", " /S", " / "],
    },
    "B": {
        "helmet": ["BCB", "SHS"],
        "chestplate": ["HSH", "BLB", "SCS"],
        "leggings": ["SCS", "B B", "H H"],
        "boots": ["BSB", "HCH"],
        "blade": [" C", "SC", "BS"],
        "focus": ["SLS", "BCB", " C "],
        "mattock": ["CBC", "S/S", " / "],
    },
}

ROAD = {
    "E": (3, "Hunter gear · E", "Creature hide, bone and essence: your first set. Recipe book."),
    "D": (9, "Beastbound gear · D", "Beast trophies and Lord parts make iron-grade gear."),
    "C": (22, "Gatewrought gear · C", "D-rank Lord relics forge diamond-grade gear."),
    "B": (42, "Veilwoven gear · B", "C-rank relics and Veil-touched bindings."),
}

# ---------------------------------------------------------------------------------------------------------------------
# Palette

def hx(h, a=255):
    return hex_rgba(h, a)


PAL = {
    "hide": ["#20150c", "#33220f", "#4a3219", "#644527", "#7f5b35", "#9c7446", "#b88f5c"],
    "bone": ["#5c5341", "#7d735c", "#a0957b", "#c2b89c", "#ddd5bb", "#f1ebd6"],
    "cream": ["#5a4a33", "#86704e", "#b39d76", "#d8c69e", "#efe2c0"],
    "pelt": ["#17120f", "#251d18", "#352a22", "#483a2e", "#5e4b3c", "#776050", "#917765"],
    "bark": ["#1a110a", "#2b1c10", "#432c18", "#5c3e22", "#77532e", "#93693c"],
    "amber": RAMPS["amber"],
    "brass": RAMPS["brass"],
    "bronze": ["#24180a", "#3a2810", "#58401a", "#7a5a26", "#9c7834", "#c09a48"],
    "moss": ["#16240f", "#213616", "#2e4c1f", "#3f652a", "#568338", "#74a34b"],
    "slate": RAMPS["slate"],
    "signal": RAMPS["signal"],
    "spirit": RAMPS["spirit"],
    "veil": ["#0f0b1e", "#1a1430", "#261e47", "#352a62", "#473a80", "#5f50a3"],
    "mirror": RAMPS["mirror"],
    "obsidian": RAMPS["obsidian"],
}
RANK = {"E": "#f4f6fb", "D": "#5fd08f", "C": "#6fb3ff", "B": "#b48cff"}


def P(name, i):
    r = PAL[name]
    return hx(r[max(0, min(len(r) - 1, i))])


def mix(a, b, t):
    return tuple(round(a[k] + (b[k] - a[k]) * t) for k in range(3)) + (255,)


def gem(t, bright=False):
    c = hx(RANK[t])
    return mix(c, (255, 255, 255, 255), 0.45) if bright else mix(c, (0, 0, 0, 255), 0.35)


# Per tier: the armour's main material, its secondary (plates, toggles), trim and glow.
LOOK = {
    "E": {"main": "hide", "second": "bone", "trim": "cream", "glow": "amber", "handle": "hide"},
    "D": {"main": "pelt", "second": "bone", "trim": "bronze", "glow": "moss", "handle": "bark"},
    "C": {"main": "slate", "second": "brass", "trim": "brass", "glow": "signal", "handle": "slate"},
    "B": {"main": "veil", "second": "mirror", "trim": "mirror", "glow": "spirit", "handle": "obsidian"},
}

# ---------------------------------------------------------------------------------------------------------------------
# Drawing helpers


def put(c, x, y, col):
    if col is not None and 0 <= x < c.w and 0 <= y < c.h:
        c.set(x, y, col)


def outline(c):
    return _outline(c)


def poly_mask(points, w=16, h=16):
    def inside(px, py):
        n, ins, j = len(points), False, len(points) - 1
        for i in range(n):
            xi, yi = points[i]
            xj, yj = points[j]
            if ((yi > py) != (yj > py)) and (px < (xj - xi) * (py - yi) / (yj - yi + 1e-9) + xi):
                ins = not ins
            j = i
        return ins
    return [[inside(x + 0.5, y + 0.5) for x in range(w)] for y in range(h)]


def shade_fill(c, mask, ramp_name, lo=1, hi=5, seed="", noise=0.12):
    """Fills a mask lit from the top-left, with a little value noise so materials don't look flat."""
    rng = rng_for("gear", seed)
    ys = [y for y in range(16) for x in range(16) if mask[y][x]]
    xs = [x for y in range(16) for x in range(16) if mask[y][x]]
    if not ys:
        return
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    for y in range(16):
        for x in range(16):
            if not mask[y][x]:
                continue
            t = 1 - (0.55 * (x - x0) / max(1, x1 - x0) + 0.45 * (y - y0) / max(1, y1 - y0))
            # darker right next to the silhouette's edges, a hint of volume
            edge = sum(1 for dx, dy in ((1, 0), (0, 1)) if not (0 <= x + dx < 16 and 0 <= y + dy < 16 and mask[y + dy][x + dx]))
            t -= 0.18 * edge
            t += (rng.random() - 0.5) * noise
            i = lo + int(round(max(0.0, min(1.0, t)) * (hi - lo)))
            c.set(x, y, P(ramp_name, i))


def diag(px, py, ox, oy):
    """(t, s) of a pixel centre along the hand-to-tip diagonal (t) and across it (s, + is down-right)."""
    dx, dy = px + 0.5 - ox, py + 0.5 - oy
    return (dx - dy) / math.sqrt(2), (dx + dy) / math.sqrt(2)


def diag_band(c, ox, oy, t0, t1, width, colour, centre=lambda t: 0.0):
    """Paints pixels whose (t, s) lie in the band; colour(t, s_norm) picks the colour (s_norm -1 top-left .. +1)."""
    for y in range(16):
        for x in range(16):
            t, s = diag(x, y, ox, oy)
            if t0 <= t <= t1:
                w = width(t) if callable(width) else width
                sc = centre(t)
                if abs(s - sc) <= w:
                    col = colour(t, (s - sc) / max(w, 1e-6))
                    if col is not None:
                        c.set(x, y, col)


# ---------------------------------------------------------------------------------------------------------------------
# Weapons and tools (handheld: hand at the bottom left, tip at the top right)

OX, OY = 2.0, 14.0  # the hand end of every handheld item


def grip(c, t, look, t0, t1, wrap=True):
    handle = look["handle"]
    trim = look["trim"]

    def col(tt, sn):
        if wrap and int(tt * 1.4) % 2 == 0:
            return P(trim, 3 if sn < 0 else 1)
        return P(handle, 4 if sn < -0.2 else 2 if sn < 0.5 else 1)
    diag_band(c, OX, OY, t0, t1, 0.75, col)


def pommel(c, t, x, y):
    look = LOOK[t]
    put(c, x, y, P(look["glow"], 4) if t in ("E", "C", "B") else gem(t, True))
    put(c, x + 1, y, P(look["trim"], 2))
    put(c, x, y - 1, P(look["trim"], 3))


def blade_icon(t):
    c = Canvas()
    look = LOOK[t]
    L = 15.2
    if t == "E":   # a curved bone fang, sinew-wrapped grip, an amber bead
        def width(tt):
            return 1.45 - 1.05 * max(0.0, (tt - 6.0)) / (L - 6.0)

        def centre(tt):
            return -0.55 * math.sin(math.pi * max(0.0, tt - 5.2) / (L - 5.2))

        def col(tt, sn):
            if sn < -0.45:
                return P("bone", 5)
            if sn < 0.2:
                return P("bone", 4 if tt < 11 else 5)
            return P("bone", 2 if sn < 0.7 else 1)
        diag_band(c, OX, OY, 5.4, L, width, col, centre)
        grip(c, t, look, 1.2, 5.3)
        for x, y in ((6, 10), (5, 11)):
            put(c, x, y, P("bone", 3))
        pommel(c, t, 2, 14)
    elif t == "D":   # a broad cleaver of tusk with a bronze spine and a green gem
        def width(tt):
            return 2.0 if tt < 12.5 else 2.0 - (tt - 12.5) * 1.1

        def col(tt, sn):
            if sn < -0.6:
                return P("bronze", 4 if tt % 3 < 2 else 3)
            if sn < 0.1:
                return P("bone", 5 if tt > 12 else 4)
            if sn < 0.6:
                return P("bone", 3)
            return P("bone", 1)
        diag_band(c, OX, OY, 5.6, L - 0.6, width, col, lambda tt: 0.35)
        diag_band(c, OX, OY, 4.6, 5.6, 2.6, lambda tt, sn: P("bronze", 4 if sn < 0 else 2))
        grip(c, t, look, 1.2, 4.6)
        put(c, 6, 10, gem(t, True))
        put(c, 6, 11, gem(t))
        pommel(c, t, 2, 14)
    elif t == "C":   # a slate sabre with a signal rune down the fuller and a brass guard
        def width(tt):
            return 1.25 if tt < 13 else 1.25 - (tt - 13) * 0.55

        def col(tt, sn):
            if abs(sn) < 0.3 and 7 < tt < 13.5:
                return P("signal", 4 if int(tt) % 2 else 3)
            if sn < 0:
                return P("slate", 5 if sn < -0.5 else 4)
            return P("slate", 2 if sn < 0.6 else 1)
        diag_band(c, OX, OY, 6.0, L, width, col, lambda tt: -0.1)
        diag_band(c, OX, OY, 5.0, 6.0, 2.9, lambda tt, sn: P("brass", 5 if sn < -0.3 else 3 if sn < 0.4 else 2))
        grip(c, t, look, 1.2, 5.0)
        put(c, 5, 10, gem(t, True))
        pommel(c, t, 2, 14)
    else:   # B: a dark Veil edge with a living spirit edge and a mirror tip
        def width(tt):
            return 1.5 if tt < 12.8 else 1.5 - (tt - 12.8) * 0.62

        def col(tt, sn):
            if sn < -0.45:
                return P("spirit", 5 if int(tt * 2) % 3 else 4)
            if tt > 13.6:
                return P("mirror", 5)
            if sn < 0.3:
                return P("veil", 4)
            return P("veil", 2 if sn < 0.7 else 1)
        diag_band(c, OX, OY, 6.0, L, width, col, lambda tt: 0.1)
        diag_band(c, OX, OY, 5.0, 6.0, 2.7, lambda tt, sn: P("mirror", 5 if sn < -0.3 else 3 if sn < 0.4 else 1))
        grip(c, t, look, 1.2, 5.0)
        put(c, 5, 10, gem(t, True))
        pommel(c, t, 2, 14)
    return outline(c)


def blade_glow(t):
    """Emissive layer for the C rune and the B spirit edge (held and dropped items glow a little at night)."""
    c = Canvas()
    src = blade_icon(t)
    for y in range(16):
        for x in range(16):
            col = src.get(x, y)
            if col[3] and t == "C" and col[:3] in (P("signal", 3)[:3], P("signal", 4)[:3]):
                c.set(x, y, col)
            if col[3] and t == "B" and col[:3] in (P("spirit", 4)[:3], P("spirit", 5)[:3]):
                c.set(x, y, col)
    return c


def focus_icon(t):
    c = Canvas()
    look = LOOK[t]
    if t == "E":   # a bone rod, a sinew wrap, a hanging amber lantern bead and a feather
        diag_band(c, OX, OY, 1.0, 11.5, 0.7, lambda tt, sn: P("bone", 4 if sn < 0 else 2) if int(tt * 1.3) % 4 else P("cream", 3))
        # the bead: a small amber lantern hanging from the tip
        cx, cy = 11.5, 6.5
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - cx - 0.6, y + 0.5 - cy - 1.2)
                if d <= 1.9:
                    c.set(x, y, P("amber", 5 if d < 0.9 else 4 if x < cx + 0.5 else 3))
        put(c, 11, 4, P("cream", 3))
        put(c, 12, 4, P("cream", 2))
        # the feather, Frostwild white with a moss quill
        for i, (x, y) in enumerate(((13, 2), (14, 1), (14, 2), (13, 3), (15, 1))):
            put(c, x, y, P("bone", 5 if i < 2 else 4))
        put(c, 12, 3, P("moss", 4))
    elif t == "D":   # a totem: dark wood, a tusk crescent and the green gem
        diag_band(c, OX, OY, 1.0, 10.5, 0.8, lambda tt, sn: P("bark", 4 if sn < -0.2 else 2 if sn < 0.5 else 1))
        diag_band(c, OX, OY, 6.0, 7.0, 1.6, lambda tt, sn: P("bronze", 4 if sn < 0 else 2))
        # crescent of tusk around the gem
        cx, cy = 11.6, 4.4
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                ang = math.atan2(y + 0.5 - cy, x + 0.5 - cx)
                if 2.0 <= d <= 3.4 and not (-2.6 < ang < -0.6):
                    c.set(x, y, P("bone", 5 if ang < 1.0 and ang > -0.6 else 4 if d < 2.7 else 2))
        for x, y in ((11, 4), (12, 4), (11, 5)):
            put(c, x, y, gem(t))
        put(c, 11, 4, gem(t, True))
    elif t == "C":   # a brass rod, a slate ring and a signal lens
        diag_band(c, OX, OY, 1.0, 9.6, 0.7, lambda tt, sn: P("brass", 4 if sn < 0 else 2) if int(tt) % 4 else P("slate", 3))
        cx, cy = 11.2, 4.8
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= 3.6:
                    if d > 2.5:
                        c.set(x, y, P("slate", 4 if x + y < cx + cy else 2))
                    else:
                        c.set(x, y, P("signal", 5 if d < 1.0 else 4 if x + y < cx + cy else 3))
        put(c, 10, 3, P("signal", 5))
        put(c, 13, 2, P("brass", 5))
        put(c, 8, 8, P("brass", 5))
    else:   # B: a censer, a dark rod and a mirror cage around a spirit flame
        diag_band(c, OX, OY, 1.0, 9.4, 0.8, lambda tt, sn: (P("obsidian", 4 if sn < 0 else 2) if int(tt) % 4 else P("mirror", 4)))
        cx, cy = 11.5, 5.5
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                if abs(dx) <= 3.1 and abs(dy) <= 3.6:
                    rim = abs(dy) > 2.7 or abs(dx) > 2.2
                    bar = abs(dx) < 0.6 and not rim and abs(dy) > 2.0
                    if rim or bar:
                        c.set(x, y, P("mirror", 5 if dx < 0 and dy < 0 else 3 if dx < 0 else 2))
                    else:
                        r = math.hypot(dx * 1.3, dy)
                        c.set(x, y, P("spirit", 5 if r < 1.2 else 4 if r < 2.2 else 3))
        put(c, 11, 1, P("mirror", 4))
        put(c, 12, 1, P("mirror", 3))
        put(c, 11, 0, P("mirror", 5))
    return outline(c)


def focus_glow(t):
    c = Canvas()
    src = focus_icon(t)
    keys = {"C": "signal", "B": "spirit"}.get(t)
    if keys:
        hot = {P(keys, i)[:3] for i in (3, 4, 5)}
        for y in range(16):
            for x in range(16):
                col = src.get(x, y)
                if col[3] and col[:3] in hot:
                    c.set(x, y, col)
    return c


def mattock_icon(t):
    c = Canvas()
    look = LOOK[t]
    handle = {"E": "bark", "D": "bark", "C": "slate", "B": "obsidian"}[t]
    diag_band(c, OX, OY, 0.8, 12.2, 0.8, lambda tt, sn: P(handle, 4 if sn < -0.2 else 2 if sn < 0.5 else 1))
    head = {"E": "bone", "D": "bone", "C": "slate", "B": "veil"}[t]
    edge = {"E": ("bone", 5), "D": ("bronze", 5), "C": ("signal", 4), "B": ("spirit", 5)}[t]
    # the head crosses the handle near its top: a pick point to the upper left, an adze blade to the lower right
    ht = 10.6
    for y in range(16):
        for x in range(16):
            tt, s = diag(x, y, OX, OY)
            d = tt - ht
            if -6.6 <= s < 0:      # pick, tapering to a point
                half = 1.45 - 0.2 * (-s)
                if abs(d) <= half:
                    c.set(x, y, P(head, 5 if d < -0.3 else 4 if d < 0.4 else 2))
            elif 0 <= s <= 4.9:    # adze, flaring to a bright edge
                half = 1.2 + 0.26 * s
                if abs(d) <= half:
                    c.set(x, y, P(*edge) if s > 4.0 else P(head, 4 if d < -0.4 else 3 if d < 0.5 else 1))
    # the lashing where head meets handle, and a stone of the tier's colour
    trim = "cream" if t == "E" else look["trim"]
    for y in range(16):
        for x in range(16):
            tt, s = diag(x, y, OX, OY)
            if abs(s) <= 0.9 and abs(tt - ht) <= 1.6 and c.get(x, y)[3]:
                c.set(x, y, P(trim, 4 if (x + y) % 2 else 2))
    cx, cy = 11, 5
    put(c, cx, cy, gem(t, True) if t != "E" else P("amber", 5))
    return outline(c)


# ---------------------------------------------------------------------------------------------------------------------
# Armour icons

SIL = {
    "chest": [(2, 2), (6, 2), (7, 3), (9, 3), (10, 2), (14, 2), (15, 6), (12, 7), (12, 14), (4, 14), (4, 7), (1, 6)],
    "legs": [(4, 2), (12, 2), (12.6, 14), (9, 14), (8.4, 6.5), (7.6, 6.5), (7, 14), (3.4, 14)],
    "boots_l": [(2.5, 4), (6.5, 4), (6.5, 13.5), (0.8, 13.5), (0.8, 11), (2.5, 10)],
    "boots_r": [(9.5, 4), (13.5, 4), (13.5, 10), (15.2, 11), (15.2, 13.5), (9.5, 13.5)],
}


def armour_icon(t, piece):
    look = LOOK[t]
    main, second, trim, glow = look["main"], look["second"], look["trim"], look["glow"]
    c = Canvas()
    if piece == "helmet":
        return helmet_icon(t)
    if piece == "boots":
        ml, mr = poly_mask(SIL["boots_l"]), poly_mask(SIL["boots_r"])
        shade_fill(c, ml, main, 1, 5, seed=t + "bl")
        shade_fill(c, mr, main, 1, 4, seed=t + "br")
        m = [[ml[y][x] or mr[y][x] for x in range(16)] for y in range(16)]
    else:
        key = {"chestplate": "chest", "leggings": "legs"}[piece]
        m = poly_mask(SIL[key])
        shade_fill(c, m, main, 1, 5, seed=t + piece)
    if piece == "chestplate":
        chest_details(c, m, t)
    elif piece == "leggings":
        # a belt, knee guards
        for x in range(4, 13):
            if m[3][x]:
                c.set(x, 3, P(trim, 3 if x < 8 else 2))
        put(c, 8, 3, gem(t, True) if t != "E" else P("amber", 5))
        for (x, y) in ((5, 9), (6, 9), (10, 9), (11, 9)):
            put(c, x, y, P(second, 4 if x < 8 else 2))
        if t == "C":
            for y in range(5, 13):
                put(c, 5, y, P("signal", 3)) if m[y][5] else None
        if t == "B":
            for y in range(4, 14, 2):
                for x in (5, 11):
                    if m[y][x]:
                        c.set(x, y, P("spirit", 4))
        if t == "E":
            for y in range(5, 13, 2):
                put(c, 6, y, P("cream", 3))
                put(c, 10, y, P("cream", 2))
    else:
        # boots: a cuff, soles, a buckle or a gem on each
        for x in range(16):
            if m[4][x]:
                c.set(x, 4, P(trim if t != "E" else "cream", 4 if x < 8 else 3))
            if m[13][x]:
                c.set(x, 13, P(second if t in ("D", "C") else main, 1 if t != "D" else 2))
        for x, y in ((4, 8), (11, 8)):
            put(c, x, y, gem(t, True) if t in ("C", "B", "D") else P("bone", 5))
            put(c, x, y + 1, gem(t) if t in ("C", "B", "D") else P("bone", 3))
        if t == "E":
            for x, y in ((3, 6), (5, 6), (10, 6), (12, 6)):
                put(c, x, y, P("cream", 3))
        if t == "D":
            for x in (3, 4, 5, 10, 11, 12):
                put(c, x, 11, P("bone", 4 if x < 8 else 3))
        if t == "B":
            for x in (2, 1, 14, 13):
                put(c, x, 12, P("spirit", 4))
    return outline(c)


def chest_details(c, m, t):
    look = LOOK[t]
    if t == "E":   # a laced leather vest: cream laces down the front, bone toggles, moss at the shoulder
        for y in range(4, 13):
            put(c, 7 + (y % 2), y, P("cream", 4 if y % 2 else 3))
        for y in (5, 8, 11):
            put(c, 6, y, P("bone", 5))
            put(c, 9, y, P("bone", 3))
        for x, y in ((2, 3), (3, 3), (12, 3)):
            put(c, x, y, P("moss", 4))
        put(c, 13, 3, P("moss", 3))
    elif t == "D":   # hide under tusk plates on the shoulders and across the chest, a bronze clasp
        for x in range(2, 6):
            put(c, x, 3, P("bone", 5 if x < 4 else 4))
            put(c, x, 4, P("bone", 3))
        for x in range(10, 14):
            put(c, x, 3, P("bone", 4))
            put(c, x, 4, P("bone", 2))
        for y in (7, 10):
            for x in range(5, 11):
                put(c, x, y, P("bone", 4 if x < 8 else 3))
        put(c, 7, 5, P("bronze", 5))
        put(c, 8, 5, P("bronze", 3))
        put(c, 8, 8, gem(t, True))
        put(c, 7, 8, gem(t))
    elif t == "C":   # slate plates with brass edges and a signal rune running down the sternum
        for x in range(1, 15):
            if m[2][x] or m[3][x]:
                put(c, x, 2 if m[2][x] else 3, P("brass", 4 if x < 8 else 3))
        for y in range(4, 14):
            put(c, 8, y, P("signal", 4 if y % 3 else 5))
        for x in range(4, 12):
            put(c, x, 9, P("slate", 0))
        put(c, 8, 9, gem(t, True))
        for x in (4, 11):
            for y in range(7, 14):
                put(c, x, y, P("brass", 2))
    else:   # B: a Veil mantle, folds of violet with a mirror clasp and a living spirit hem
        for y in range(4, 14):
            for x in (6, 9):
                if m[y][x]:
                    c.set(x, y, P("veil", 1))
        for x in range(4, 12):
            put(c, x, 13, P("spirit", 4 if x % 2 else 5))
        put(c, 7, 4, P("mirror", 5))
        put(c, 8, 4, P("mirror", 3))
        put(c, 7, 5, gem(t, True))
        for x, y in ((2, 3), (13, 3)):
            put(c, x, y, P("mirror", 4))


def helmet_icon(t):
    c = Canvas()
    look = LOOK[t]
    if t == "E":   # a hide hood with a shadowed face and a feather
        m = poly_mask([(3, 3), (6, 1.6), (10, 1.6), (13, 3), (14, 9), (15, 13), (1, 13), (2, 9)])
        shade_fill(c, m, "hide", 1, 5, seed="hood")
        face = poly_mask([(5, 5), (11, 5), (11.5, 11), (4.5, 11)])
        for y in range(16):
            for x in range(16):
                if face[y][x]:
                    c.set(x, y, P("hide", 0) if y > 5 else P("hide", 1))
        for x in range(2, 15):
            if m[12][x]:
                c.set(x, 12, P("cream", 3 if x % 2 else 2))
        put(c, 7, 12, P("bone", 5))
        put(c, 8, 12, P("bone", 3))
        for x, y in ((12, 1), (13, 0), (13, 1), (14, 0)):
            put(c, x, y, P("bone", 5))
        put(c, 12, 2, P("moss", 4))
    elif t == "D":   # a hide-and-bronze helm with two small tusk horns and a green brow gem
        m = poly_mask([(3, 4), (6, 2.5), (10, 2.5), (13, 4), (13.4, 12), (10, 12), (9.5, 9), (6.5, 9), (6, 12), (2.6, 12)])
        shade_fill(c, m, "pelt", 1, 5, seed="helmD")
        for x in range(3, 14):
            if m[5][x]:
                c.set(x, 5, P("bronze", 4 if x < 8 else 3))
        for x, y in ((2, 3), (1, 2), (1, 1)):
            put(c, x, y, P("bone", 5))
        for x, y in ((14, 3), (15, 2), (15, 1)):
            put(c, x, y, P("bone", 4))
        put(c, 2, 2, P("bone", 3))
        put(c, 14, 2, P("bone", 2))
        put(c, 8, 4, gem(t, True))
        put(c, 7, 4, gem(t))
        for y in range(6, 12):
            put(c, 3, y, P("bone", 4)) if m[y][3] else None
    elif t == "C":   # a gate-slate helm, brass crest, a signal visor slit
        m = poly_mask([(3, 4), (5, 2), (11, 2), (13, 4), (13.4, 12), (2.6, 12)])
        shade_fill(c, m, "slate", 1, 5, seed="helmC")
        for y in range(1, 5):
            put(c, 8, y, P("brass", 5 if y < 3 else 3))
            put(c, 7, y, P("brass", 4))
        for x in range(4, 13):
            put(c, x, 7, P("signal", 5 if 6 < x < 10 else 4))
            put(c, x, 8, P("slate", 0))
        for x in (3, 12):
            for y in range(5, 12):
                put(c, x, y, P("brass", 3 if x == 3 else 2))
        put(c, 8, 10, gem(t, True))
    else:   # B: a Veil cowl, deep hood, two spirit eyes in the dark, a mirror circlet
        m = poly_mask([(3, 3.5), (6, 1.5), (10, 1.5), (13, 3.5), (14, 10), (15, 14), (1, 14), (2, 10)])
        shade_fill(c, m, "veil", 1, 5, seed="cowl")
        face = poly_mask([(5, 5.5), (11, 5.5), (11.5, 12), (4.5, 12)])
        for y in range(16):
            for x in range(16):
                if face[y][x]:
                    c.set(x, y, P("obsidian", 0))
        for x, y in ((6, 8), (9, 8)):
            put(c, x, y, P("spirit", 5))
            put(c, x + 1, y, P("spirit", 4))
        for x in range(4, 12):
            put(c, x, 4, P("mirror", 5 if x < 8 else 3))
        put(c, 8, 4, gem(t, True))
        for x in range(2, 15):
            if m[13][x]:
                c.set(x, 13, P("spirit", 4 if x % 2 else 3))
    return outline(c)


# ---------------------------------------------------------------------------------------------------------------------
# Armour layers (64x32 humanoid layout). Boots live on the legs of the humanoid layer, leggings on their own layer.


def _box(u, v, w, h, d):
    """Faces of a model cube at texOffs (u, v), size w x h x d: name -> (x, y, fw, fh)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def material(t, x, y, rng):
    """The tier's main armour material at layer pixel (x, y)."""
    look = LOOK[t]
    main = look["main"]
    v = 0.5 + (rng.random() - 0.5) * 0.35
    if t == "E":
        return P("hide", 3 + int(v * 2.2))
    if t == "D":
        return P("pelt", 2 + int(v * 2.6))
    if t == "C":
        return P("slate", 3 if (x // 4 + y // 4) % 2 else 2) if rng.random() > 0.12 else P("slate", 4)
    return P("veil", 3 if (x % 3) else 2) if rng.random() > 0.1 else P("veil", 4)


def paint_face(c, t, face, rect, part, rng, rows=None):
    """Paints one face; rows limits it to a band (start, end) in face rows."""
    x0, y0, fw, fh = rect
    look = LOOK[t]
    r0, r1 = rows if rows else (0, fh)
    for j in range(r0, r1):
        for i in range(fw):
            x, y = x0 + i, y0 + j
            col = material(t, x, y, rng)
            edge_v = j == r0 or j == r1 - 1
            edge_h = i == 0 or i == fw - 1
            if edge_v or edge_h:
                trim = look["trim"]
                col = P(trim, 3 if face in ("front", "top") else 2) if edge_v else mix(col, P(look["main"], 0), 0.35)
            c.set(x, y, col)


def equipment_layer(t, leggings=False):
    c = Canvas(64, 32)
    rng = rng_for("gear-layer", t, leggings)
    look = LOOK[t]
    head, body, arm, leg = _box(0, 0, 8, 8, 8), _box(16, 16, 8, 12, 4), _box(40, 16, 4, 12, 4), _box(0, 16, 4, 12, 4)
    if leggings:
        for f, r in body.items():
            if f in ("front", "back", "left", "right"):
                paint_face(c, t, f, r, "waist", rng, (8, 12))
            elif f == "bottom":
                paint_face(c, t, f, r, "waist", rng)
        for f, r in leg.items():
            if f in ("front", "back", "left", "right"):
                paint_face(c, t, f, r, "leg", rng, (0, 9))
            elif f == "top":
                paint_face(c, t, f, r, "leg", rng)
        # belt and knee detail
        fx, fy, fw, fh = body["front"]
        for i in range(fw):
            c.set(fx + i, fy + 8, P(look["trim"], 4 if i % 3 else 2))
        c.set(fx + 3, fy + 9, gem(t, True) if t != "E" else P("amber", 5))
        for f in ("front",):
            lx, ly, lw, lh = leg[f]
            for i in range(lw):
                c.set(lx + i, ly + 5, P(look["second"], 4 if i < 2 else 3))
            if t == "C":
                for j in range(1, 8):
                    c.set(lx + 1, ly + j, P("signal", 4))
            if t == "B":
                for j in range(0, 9, 2):
                    c.set(lx + 2, ly + j, P("spirit", 4))
            if t == "E":
                for j in range(1, 9, 2):
                    c.set(lx + 1, ly + j, P("cream", 4))
        return c
    # helmet (no bottom face: nothing under the chin)
    for f, r in head.items():
        if f != "bottom":
            paint_face(c, t, f, r, "head", rng)
    hx0, hy0, hw, hh = head["front"]
    if t in ("E", "B"):   # hoods: the face shows through (transparent), shadowed rim
        for j in range(2, 8):
            for i in range(1, 7):
                c.set(hx0 + i, hy0 + j, (0, 0, 0, 0))
        for i in range(1, 7):
            c.set(hx0 + i, hy0 + 1, P(look["main"], 1))
        if t == "B":
            for i in range(0, 8):
                c.set(hx0 + i, hy0 + 1, P("mirror", 4 if i % 2 else 5))
            c.set(hx0 + 3, hy0 + 1, gem(t, True))
            c.set(hx0 + 4, hy0 + 1, gem(t, True))
        else:
            c.set(hx0 + 3, hy0 + 0, P("bone", 5))
            c.set(hx0 + 4, hy0 + 0, P("bone", 4))
    elif t == "D":   # face open below the brow: a nasal guard of tusk
        for j in range(3, 8):
            for i in range(1, 7):
                if i not in (3, 4) or j > 5:
                    c.set(hx0 + i, hy0 + j, (0, 0, 0, 0))
        for i in range(8):
            c.set(hx0 + i, hy0 + 2, P("bronze", 4 if i < 4 else 3))
        c.set(hx0 + 3, hy0 + 1, gem(t, True))
        c.set(hx0 + 4, hy0 + 1, gem(t))
        for j in range(3, 6):
            c.set(hx0 + 3, hy0 + j, P("bone", 5))
            c.set(hx0 + 4, hy0 + j, P("bone", 3))
    else:   # C: closed helm, a visor slit of signal light
        for j in range(5, 8):
            for i in range(1, 7):
                c.set(hx0 + i, hy0 + j, (0, 0, 0, 0))
        for i in range(1, 7):
            c.set(hx0 + i, hy0 + 3, P("signal", 5 if 2 < i < 5 else 4))
        for j in range(0, 3):
            c.set(hx0 + 3, hy0 + j, P("brass", 5))
            c.set(hx0 + 4, hy0 + j, P("brass", 3))
        tx, ty, tw, th = head["top"]
        for j in range(th):
            c.set(tx + 3, ty + j, P("brass", 4))
            c.set(tx + 4, ty + j, P("brass", 3))
    # chest: body and arms
    for f, r in body.items():
        paint_face(c, t, f, r, "body", rng)
    for f, r in arm.items():
        if f in ("front", "back", "left", "right"):
            paint_face(c, t, f, r, "arm", rng, (0, 9))
        elif f == "top":
            paint_face(c, t, f, r, "arm", rng)
    bx, by, bw, bh = body["front"]
    if t == "E":
        for j in range(1, 11):
            c.set(bx + 3 + (j % 2), by + j, P("cream", 4 if j % 2 else 3))
        for j in (2, 5, 8):
            c.set(bx + 2, by + j, P("bone", 5))
            c.set(bx + 5, by + j, P("bone", 4))
        ax, ay, aw, ah = arm["front"]
        for i in range(aw):
            c.set(ax + i, ay, P("moss", 4 if i % 2 else 3))
    elif t == "D":
        for j in (3, 6):
            for i in range(1, 7):
                c.set(bx + i, by + j, P("bone", 4 if i < 4 else 3))
        c.set(bx + 3, by + 1, P("bronze", 5))
        c.set(bx + 4, by + 1, P("bronze", 3))
        c.set(bx + 4, by + 4, gem(t, True))
        for f in ("front", "left", "right", "back", "top"):
            ax, ay, aw, ah = arm[f]
            for i in range(aw):
                c.set(ax + i, ay + (0 if f != "top" else 1), P("bone", 5 if i < 2 else 3))
    elif t == "C":
        for j in range(1, 11):
            c.set(bx + 3, by + j, P("signal", 4 if j % 3 else 5))
            c.set(bx + 4, by + j, P("signal", 3))
        for i in range(bw):
            c.set(bx + i, by + 6, P("brass", 3))
        c.set(bx + 3, by + 6, gem(t, True))
        c.set(bx + 4, by + 6, gem(t))
        for f in ("front", "left", "right", "back"):
            ax, ay, aw, ah = arm[f]
            for i in range(aw):
                c.set(ax + i, ay, P("brass", 4))
    else:
        for j in range(bh):
            for i in (2, 5):
                c.set(bx + i, by + j, P("veil", 1))
        for i in range(bw):
            c.set(bx + i, by + bh - 1, P("spirit", 4 if i % 2 else 5))
        c.set(bx + 3, by + 1, P("mirror", 5))
        c.set(bx + 4, by + 1, P("mirror", 3))
        c.set(bx + 3, by + 2, gem(t, True))
        for f in ("front", "left", "right", "back"):
            ax, ay, aw, ah = arm[f]
            for i in range(aw):
                c.set(ax + i, ay + 8, P("spirit", 4))
    # backs: a little of each tier's story behind you too
    kx, ky, kw, kh = body["back"]
    if t == "E":
        for j in range(2, 10, 2):
            c.set(kx + 3, ky + j, P("cream", 3))
            c.set(kx + 4, ky + j + 1, P("cream", 3))
        c.set(kx + 2, ky + 2, P("bone", 5))
        c.set(kx + 5, ky + 2, P("bone", 4))
    elif t == "D":
        for i in range(1, 7):
            c.set(kx + i, ky + 2, P("bone", 4 if i < 4 else 3))
            c.set(kx + i, ky + 3, P("bone", 2))
        for j in range(5, 10):
            c.set(kx + 3 + (j % 2), ky + j, P("bronze", 3))
    elif t == "C":
        for j in range(1, 11):
            c.set(kx + 3, ky + j, P("brass", 4 if j % 2 else 3))
            c.set(kx + 4, ky + j, P("brass", 2))
        for i in range(1, 7):
            c.set(kx + i, ky + 5, P("signal", 3))
    else:
        for i in range(kw):
            c.set(kx + i, ky + kh - 1, P("spirit", 4 if i % 2 else 5))
            c.set(kx + i, ky + kh - 2, P("veil", 1))
        for j in range(1, 9):
            c.set(kx + 3 + (j % 2), ky + j, P("veil", 5))
    hbx, hby, hbw, hbh = head["back"]
    if t in ("E", "B"):
        for i in range(hbw):
            c.set(hbx + i, hby + hbh - 1, P("cream" if t == "E" else "spirit", 3))
    # boots on the legs: only the lower part
    for f, r in leg.items():
        if f in ("front", "back", "left", "right"):
            paint_face(c, t, f, r, "boot", rng, (7, 12))
        elif f == "bottom":
            paint_face(c, t, f, r, "boot", rng)
    lx, ly, lw, lh = leg["front"]
    for i in range(lw):
        c.set(lx + i, ly + 7, P(look["trim"] if t != "E" else "cream", 4 if i % 2 else 3))
    if t in ("C", "B"):
        c.set(lx + 1, ly + 9, gem(t, True))
    return c


# ---------------------------------------------------------------------------------------------------------------------
# Build


def build(out):
    for t in TIERS:
        for piece in ARMOUR:
            out.item(item_id(t, piece), armour_icon(t, piece))
        out.item(item_id(t, "blade"), blade_icon(t))
        out.item(item_id(t, "focus"), focus_icon(t))
        out.item(item_id(t, "mattock"), mattock_icon(t))
        if t in ("C", "B"):
            out.item(item_id(t, "blade") + "_glow", blade_glow(t))
            out.item(item_id(t, "focus") + "_glow", focus_glow(t))
        out.entity("equipment/humanoid/" + SET[t], equipment_layer(t, False))
        out.entity("equipment/humanoid_leggings/" + SET[t], equipment_layer(t, True))


# ---------------------------------------------------------------------------------------------------------------------
# Content JSON


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def content():
    a = os.path.join(RES, "assets/awaken")
    d = os.path.join(RES, "data/awaken")
    lang = {}
    for t in TIERS:
        write(os.path.join(a, "equipment", SET[t] + ".json"), {"layers": {
            "humanoid": [{"texture": "awaken:" + SET[t]}],
            "humanoid_leggings": [{"texture": "awaken:" + SET[t]}]}})
        for piece in PIECES:
            iid = item_id(t, piece)
            handheld = piece in ("blade", "focus", "mattock")
            textures = {"layer0": "awaken:item/" + iid}
            if t in ("C", "B") and piece in ("blade", "focus"):
                textures["layer1"] = {"sprite": "awaken:item/" + iid + "_glow", "neoforge_data": {"light_emission": 15}}
            write(os.path.join(a, "models/item", iid + ".json"),
                  {"parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated", "textures": textures})
            write(os.path.join(a, "items", iid + ".json"), {"model": {"type": "minecraft:model", "model": "awaken:item/" + iid}})
            lang["item.awaken." + iid] = display(t, piece)
            # recipe
            tl = t.lower()
            keymap = {"H": "#awaken:hunter_hide_" + tl, "C": "#awaken:hunter_core_" + tl, "B": "#awaken:hunter_bind_" + tl,
                      "S": "awaken:essence_shard", "L": "awaken:lord_heart", "/": "minecraft:stick"}
            pattern = RECIPES[t][piece]
            used = sorted({ch for row in pattern for ch in row if ch != " "})
            write(os.path.join(d, "recipe", iid + ".json"), {
                "type": "minecraft:crafting_shaped", "category": "equipment", "group": "awaken_hunter_" + piece,
                "key": {ch: keymap[ch] for ch in used}, "pattern": pattern, "result": {"id": "awaken:" + iid}})
        lvl, title, text = ROAD[t]
        write(os.path.join(d, "awaken/level_road", "hunter_gear_" + t.lower() + ".json"), {
            "schema": 1, "level": lvl, "title": title, "text": text,
            "grants": [{"type": "recipe", "ids": ["awaken:" + item_id(t, p) for p in PIECES]}], "quiet": False})
    # hook tags and vanilla tags (merged at build time with every other module's)
    mt = os.path.join(MERGE, "data")
    for name, values in TAGS.items():
        write(os.path.join(mt, "awaken/tags/item", name + ".json"), {"values": values})
    by = lambda piece: ["awaken:" + item_id(t, piece) for t in TIERS]  # noqa: E731
    write(os.path.join(mt, "minecraft/tags/item/swords.json"), {"values": by("blade")})
    write(os.path.join(mt, "minecraft/tags/item/head_armor.json"), {"values": by("helmet")})
    write(os.path.join(mt, "minecraft/tags/item/chest_armor.json"), {"values": by("chestplate")})
    write(os.path.join(mt, "minecraft/tags/item/leg_armor.json"), {"values": by("leggings")})
    write(os.path.join(mt, "minecraft/tags/item/foot_armor.json"), {"values": by("boots")})
    write(os.path.join(mt, "minecraft/tags/item/enchantable/mining.json"), {"values": by("mattock")})
    write(os.path.join(mt, "minecraft/tags/item/enchantable/mining_loot.json"), {"values": by("mattock")})
    write(os.path.join(mt, "minecraft/tags/item/enchantable/durability.json"), {"values": by("mattock") + by("focus")})
    write(os.path.join(MERGE, "assets/awaken/lang/en_us.json"), lang)
    print("gear content:", len(lang), "items")


# ---------------------------------------------------------------------------------------------------------------------
# Preview


def figure(t, back=False, scale=10):
    """Front (or back) view of a player wearing the whole set, from the layer textures (skin under transparency)."""
    from PIL import Image
    tex = os.path.join(RES, "assets/awaken/textures/entity/equipment")
    l1 = Image.open(os.path.join(tex, "humanoid", SET[t] + ".png")).convert("RGBA")
    l2 = Image.open(os.path.join(tex, "humanoid_leggings", SET[t] + ".png")).convert("RGBA")
    W, H = 16, 32
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    skin, shirt, pants = (198, 150, 120, 255), (60, 120, 150, 255), (50, 50, 110, 255)
    f = "back" if back else "front"
    parts = [  # (dest x, y, texture face rect for each layer, base colour)
        ((4, 0), _box(0, 0, 8, 8, 8)[f], None, skin),
        ((4, 8), _box(16, 16, 8, 12, 4)[f], _box(16, 16, 8, 12, 4)[f], shirt),
        ((0, 8), _box(40, 16, 4, 12, 4)[f], None, skin),
        ((12, 8), _box(40, 16, 4, 12, 4)[f], None, skin),
        ((4, 20), _box(0, 16, 4, 12, 4)[f], _box(0, 16, 4, 12, 4)[f], pants),
        ((8, 20), _box(0, 16, 4, 12, 4)[f], _box(0, 16, 4, 12, 4)[f], pants),
    ]
    for (dx, dy), r1, r2, base in parts:
        x0, y0, w, h = r1
        for j in range(h):
            for i in range(w):
                img.putpixel((dx + i, dy + j), base)
        for layer, r in ((l2, r2), (l1, r1)):
            if r is None:
                continue
            x0, y0, w, h = r
            crop = layer.crop((x0, y0, x0 + w, y0 + h))
            if (dx, dy) == (12, 8) or (dx, dy) == (8, 20):
                crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
            img.alpha_composite(crop, (dx, dy))
    return img.resize((W * scale, H * scale), Image.NEAREST)


def preview(dirpath):
    from PIL import Image, ImageDraw
    os.makedirs(dirpath, exist_ok=True)
    tex = os.path.join(RES, "assets/awaken/textures/item")
    S = 96
    sheet = Image.new("RGBA", (7 * (S + 16) + 8, 4 * (S + 30) + 10), (24, 26, 34, 255))
    d = ImageDraw.Draw(sheet)
    for row, t in enumerate(TIERS):
        for col, piece in enumerate(PIECES):
            im = Image.open(os.path.join(tex, item_id(t, piece) + ".png")).convert("RGBA").resize((S, S), Image.NEAREST)
            x, y = col * (S + 16) + 8, row * (S + 30) + 8
            sheet.paste(im, (x, y), im)
            d.text((x, y + S + 2), display(t, piece)[:18], fill=(200, 205, 215, 255))
    sheet.save(os.path.join(dirpath, "gear_icons.png"))
    figs = Image.new("RGBA", (4 * 2 * 170 + 20, 360), (24, 26, 34, 255))
    for i, t in enumerate(TIERS):
        for b in (False, True):
            im = figure(t, b)
            figs.paste(im, (10 + (i * 2 + b) * 170, 10), im)
    figs.save(os.path.join(dirpath, "gear_figures.png"))
    # the raw layers, scaled
    tex2 = os.path.join(RES, "assets/awaken/textures/entity/equipment")
    lay = Image.new("RGBA", (4 * (64 * 4 + 10), 2 * (32 * 4 + 10)), (60, 60, 70, 255))
    for i, t in enumerate(TIERS):
        for j, sub in enumerate(("humanoid", "humanoid_leggings")):
            im = Image.open(os.path.join(tex2, sub, SET[t] + ".png")).convert("RGBA").resize((256, 128), Image.NEAREST)
            lay.paste(im, (i * 266, j * 138), im)
    lay.save(os.path.join(dirpath, "gear_layers.png"))
    print("preview in", dirpath)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "preview":
        preview(sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_preview")
    else:
        print(__doc__)
