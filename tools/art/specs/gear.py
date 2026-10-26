"""Hunter gear (gear module): every texture, the content JSON, and a look-test preview.

    python3 tools/art/build_all.py gear          item icons (28) and armour layers (4 sets x humanoid + leggings)
    python3 tools/art/specs/gear.py content      item models and definitions, equipment assets, recipes, hook tags,
                                                 vanilla tags, level-road steps, lang (src/main/resources, merge/gear)
    python3 tools/art/specs/gear.py preview DIR  contact sheet of the icons and each set drawn on a figure, front and back

Tiers mirror dev.awaken.gear.HunterTier (E Wildhide, D Beastbound, C Gatewrought, B Veilwoven, A Lordscale, S Sovereign
Weave); keep the two in step. Look: dark slate, amber lantern and signal blue are the mod's identity; each tier wears its
materials' ramps (E hide, bone and amber; D tusk, bronze and the beasts' moss green; C gate slate, brass and signal
runes; B Veil violet and mirror silver; A the Lords' black scale, molten gold and sky pearl; S the Court's void weave,
gold filigree and crimson) and the rank colour on its gem.

A and S also change the silhouette: horns, a crest, layered pauldrons, a spined back and talons (Lordscale); a crown of
spires, a high collar, a cape, pauldrons and ankle fins (Sovereign Weave). Those extra cubes are listed in SILHOUETTE,
written to assets/awaken/gear_silhouettes/<set>.json (the client builds the armour model from it, a GameTest checks it)
and painted into a 128x64 texture (textures/entity/equipment/silhouette/<set>.png) whose top-left 64x32 is the normal
layer. The preview renders every set on a real 3D figure (the same cube maths as ModelPart) from the front and back.

    python3 tools/art/specs/gear.py preview DIR  also writes gear_late.png: A and S large, four angles each
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

TIERS = ["E", "D", "C", "B", "A", "S"]
LATE = ("A", "S")
SET = {"E": "wildhide", "D": "beastbound", "C": "gatewrought", "B": "veilwoven", "A": "lordscale", "S": "sovereign"}
SET_NAME = {"E": "Wildhide", "D": "Beastbound", "C": "Gatewrought", "B": "Veilwoven", "A": "Lordscale", "S": "Sovereign Weave"}
# what goes in front of a piece's name ("Sovereign Crown", not "Sovereign Weave Crown")
PREFIX = dict(SET_NAME, S="Sovereign")
PIECES = ["helmet", "chestplate", "leggings", "boots", "blade", "focus", "mattock"]
ARMOUR = PIECES[:4]

NAMES = {
    "E": ["Hood", "Vest", "Leggings", "Boots", "Fang", "Charm", "Mattock"],
    "D": ["Helm", "Hauberk", "Greaves", "Boots", "Cleaver", "Totem", "Mattock"],
    "C": ["Helm", "Cuirass", "Greaves", "Sabatons", "Sabre", "Lens", "Mattock"],
    "B": ["Cowl", "Mantle", "Leggings", "Treads", "Edge", "Censer", "Mattock"],
    "A": ["Greathelm", "Warplate", "Tassets", "Talons", "Warblade", "Orb", "Mattock"],
    "S": ["Crown", "Regalia", "Legwraps", "Striders", "Oathblade", "Sceptre", "Mattock"],
}


def item_id(t, piece):
    return SET[t] + "_" + piece


def display(t, piece):
    return PREFIX[t] + " " + NAMES[t][PIECES.index(piece)]


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
    # A: plates of Precursor alloy or netherite, the B- and A-rank Lords' relics, the B/A Descent guardians' parts
    "hunter_hide_a": [OPT("awaken:precursor_alloy"), "minecraft:netherite_scrap", "minecraft:shulker_shell"],
    "hunter_core_a": [OPT("awaken:" + i) for i in ["leviathan_fin", "sky_pearl", "herald_halo", "abyssal_glass",
                                                   "furnace_engine_core", "moon_scale", "architect_key"]],
    "hunter_bind_a": ["minecraft:diamond", "minecraft:wind_charge", OPT("awaken:precursor_circuit")],
    # S: a weave of netherite thread, the A-rank Lord's and the deepest strata's relics (the Hollow Court's when you get
    # there), bound with the Herald's echoes and dreams
    # S's hide is starmetal now (PLAN 7.2); its recipes take it through M, the tier's metal
    "hunter_hide_s": [OPT("awaken:starmetal_ingot")],
    "hunter_core_s": [OPT("awaken:" + i) for i in ["herald_halo", "abyssal_glass", "architect_key", "regent_shadow",
                                                   "hunger_fang", "silence_ear", "sleep_feather", "machine_crown",
                                                   "mirror_heart", "storm_core", "root_heart", "ash_heart", "seed_shard"]],
    "hunter_bind_s": ["minecraft:echo_shard", OPT("awaken:dream_residue")],
}

# Recipes: H hide, C core, B bind, S essence shard, L Lord's Heart, / stick; from D up M the tier's metal from the deep
# (#awaken:tier_metal_<t>: D iron, C diamond, B Deepglass, A netherite scrap, S starmetal; no other item fills it), G a
# gold ingot (every focus from D up: gold is the power's metal) and at B Y an amethyst shard (Veilwoven's resonance).
# SPEC §28.3: about 13 of the metal for a tier's seven pieces. Patterns differ within a tier.
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
        "helmet": ["HCH", "MSM"],
        "chestplate": ["HSH", "MCM", "HMH"],
        "leggings": ["MCM", "S S", "H H"],
        "boots": ["MSM", "HCH"],
        "blade": ["CS", "M ", "B "],
        "focus": ["SGS", " C ", " M "],
        "mattock": ["MCM", " /S", " / "],
    },
    "C": {
        "helmet": ["MCM", "HSH"],
        "chestplate": ["HMH", "MLM", "SCS"],
        "leggings": ["MCM", "S S", "H H"],
        "boots": ["MSM", "HCH"],
        "blade": [" C", "SC", "M "],
        "focus": ["SLS", "GCG", " M "],
        "mattock": ["MCM", " /S", " / "],
    },
    "B": {
        "helmet": ["MCM", "SYS"],
        "chestplate": ["MYM", "HLH", "SCM"],
        "leggings": ["SCS", "MYM", "H H"],
        "boots": ["MSM", "HCH", " Y "],
        "blade": [" C", "YC", "MS"],
        "focus": ["SLS", "GCG", "MYM"],
        "mattock": ["MCM", "Y/S", " / "],
    },
    "A": {
        "helmet": ["CHC", "MSM"],
        "chestplate": ["HLH", "CSC", "MMM"],
        "leggings": ["HCH", "M M", "S S"],
        "boots": ["MCM", "HSH"],
        "blade": ["  C", "SC ", "LM "],
        "focus": ["GLG", " C ", "MSM"],
        "mattock": ["CMC", " BS", " M "],
    },
    "S": {
        "helmet": ["CLC", "MSM"],
        "chestplate": ["CLC", "MSM", "BMB"],
        "leggings": ["CSC", "M M", "B B"],
        "boots": ["M M", "CSC"],
        "blade": ["  C", "LC ", "MS "],
        "focus": ["CLC", "GMG", " S "],
        "mattock": ["CCC", "SML", " M "],
    },
}

ROAD = {
    "E": (3, "Hunter gear · E", "Creature hide, bone and essence: your first set. Recipe book."),
    "D": (13, "Beastbound gear · D", "Beast trophies and Lord parts, bound in iron from the deep."),
    "C": (22, "Gatewrought gear · C", "D-rank Lord relics set in diamond: it matches diamond armour."),
    "B": (42, "Veilwoven gear · B", "C-rank relics, Deepglass and amethyst: the Veil's resonance."),
    "A": (67, "Lordscale gear · A", "Lords' scales plated in netherite, past it."),
    "S": (97, "Sovereign Weave · S", "The Herald's halo and starmetal from the fallen stars."),
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
    "flint": ["#1c1c20", "#2c2c33", "#3f3f48", "#575762", "#73737f", "#9494a0"],
    # A Lordscale: black scale with a violet-bronze sheen, molten gold, the Sky Leviathan's pearl
    "lord": ["#0b090e", "#16111b", "#221a29", "#302438", "#41314b", "#564363", "#70587f"],
    "gold": ["#3a2408", "#6b430c", "#9e6a12", "#d29a1e", "#f3c443", "#ffe79a"],
    "pearl": ["#26344a", "#45628a", "#7aa0c8", "#b2d4f2", "#e0f1ff", "#ffffff"],
    # S Sovereign Weave: the Court's void weave, gold filigree, crimson (S rank)
    "weave": ["#090611", "#120c1e", "#1c142c", "#271c3c", "#33254f", "#433163", "#57417d"],
    "crimson": RAMPS["crimson"],
    "ivory": ["#5e554a", "#857a6b", "#ada392", "#cfc6b4", "#ebe4d4", "#fbf8ef"],
}
RANK = {"E": "#f4f6fb", "D": "#5fd08f", "C": "#6fb3ff", "B": "#b48cff", "A": "#f2a541", "S": "#ff5a6a"}


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
    "A": {"main": "lord", "second": "gold", "trim": "gold", "glow": "pearl", "handle": "lord"},
    "S": {"main": "weave", "second": "gold", "trim": "gold", "glow": "crimson", "handle": "weave"},
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
    handle = {"E": "bark", "D": "bark", "C": "slate", "B": "obsidian", "A": "lord", "S": "weave"}[t]
    diag_band(c, OX, OY, 0.8, 12.2, 0.8, lambda tt, sn: P(handle, 4 if sn < -0.2 else 2 if sn < 0.5 else 1))
    head = {"E": "flint", "D": "bone", "C": "slate", "B": "veil", "A": "lord", "S": "weave"}[t]
    edge = {"E": ("bone", 5), "D": ("bronze", 5), "C": ("signal", 4), "B": ("spirit", 5), "A": ("gold", 5), "S": ("gold", 5)}[t]
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
# A Lordscale and S Sovereign Weave: the late sets. Their own drawing code: these are the sets players show off, so every
# icon is laid out by hand on the 16 px grid, the armour layers carry a scale (A) or woven (S) material, and the
# silhouette parts below add geometry to the armour model.


def scale_px(x, y, ramp="lord", rim="gold", lo=2, hi=5, rim_every=2, phase=0):
    """Overlapping scales: rows of 3 px scales, every row offset by half a scale, a bright lower rim on each scale and
    a gold rim on every {rim_every}-th row. Returns the colour for (x, y)."""
    row = (y + phase) // 2
    sx = (x + (row % 2) * 2 + 3) % 3
    sy = (y + phase) % 2
    if sy == 1:
        if row % rim_every == 0:
            return P(rim, 4 if sx == 1 else 3)
        return P(ramp, hi if sx == 1 else hi - 1)
    return P(ramp, lo + (1 if sx == 0 else 0) + (1 if sx == 1 else 0))


def scale_dark(x, y):
    """Lordscale's scales, no gold: 4 x 3 scallops in offset rows, a lit lower lip, dark seams between them."""
    row = y // 3
    sx = (x + (row % 2) * 2) % 4
    sy = y % 3
    mid = sx in (1, 2)
    if sy == 2:
        return P("lord", 5 if mid else 3)
    if sy == 1:
        return P("lord", 3 if mid else 2)
    return P("lord", 2 if mid else 1)


def grid(rows, t, tex=None):
    """An icon from 16 strings. '.' empty; 0-6 the tier's main ramp; q w e Y gold 2-5; p P W the glow ramp 3-5; a A the
    rank gem (bright, dark); i I ivory 4-5; c C crimson 3-5; s the tier's texture (scales or weave, shaded)."""
    c = Canvas()
    main = LOOK[t]["main"]
    glow = GLOW_RAMP[t]
    for y, row in enumerate(rows):
        assert len(row) == 16, (t, y, row)
        for x, ch in enumerate(row):
            col = None
            if ch == ".":
                continue
            if ch.isdigit():
                col = P(main, int(ch))
            elif ch in "qweY":
                col = P("gold", "qweY".index(ch) + 2)
            elif ch in "pPW":
                col = P(glow, "pPW".index(ch) + 3)
            elif ch == "a":
                col = gem(t, True)
            elif ch == "A":
                col = gem(t)
            elif ch in "iI":
                col = P("ivory", "iI".index(ch) + 4)
            elif ch in "cC":
                col = P("crimson", 3 if ch == "c" else 5)
            elif ch == "s":
                col = (tex or (scale_dark if t == "A" else weave_px))(x, y)
                if x >= 10:
                    col = mix(col, P(main, 0), 0.3)
                elif x <= 4:
                    col = mix(col, P(main, 6), 0.12)
            c.set(x, y, col)
    return outline(c)


def weave_px(x, y, ramp="weave", lo=2):
    """A fine twill: diagonal ribs one value apart, a darker thread every fourth."""
    k = (x + 2 * y) % 4
    return P(ramp, lo + (1 if k == 0 else 0) - (1 if k == 2 else 0) + 1)


def filigree(c, x0, y0, h, ramp="gold"):
    """A vertical band of gold filigree two pixels wide: alternating lozenges."""
    for j in range(h):
        y = y0 + j
        k = j % 4
        if k == 0:
            put(c, x0, y, P(ramp, 4))
            put(c, x0 + 1, y, P(ramp, 3))
        elif k == 1 or k == 3:
            put(c, x0, y, P(ramp, 3))
            put(c, x0 + 1, y, P(ramp, 2))
        else:
            put(c, x0, y, P(ramp, 5))
            put(c, x0 + 1, y, P(ramp, 4))


def gem_px(c, x, y, ramp, big=False):
    put(c, x, y, P(ramp, 5))
    if big:
        put(c, x + 1, y, P(ramp, 4))
        put(c, x, y + 1, P(ramp, 3))
        put(c, x + 1, y + 1, P(ramp, 2))


# ---- A: Lordscale icons ---------------------------------------------------------------------------------------------

def lord_helmet():
    # a horned greathelm: horns sweeping up to the corners, a gold crest, a pearl slit, the amber gem on the brow
    return grid([
        "Y......ew......Y",
        "Y......Ye......e",
        "e......ew......w",
        "ew.....ew.....qw",
        ".4...55wq44...3.",
        ".44.ssssssss.33.",
        "..3eeeeaAwwww2..",
        "...sssseqssss...",
        "...sPPWeqWPPs...",
        "...s000eq000s...",
        "...sssseqssss...",
        "...ss0seqs0ss...",
        "...ss0seqs0ss...",
        "...ssssssssss...",
        "....wwwwwwww....",
        "................",
    ], "A")


def lord_chest():
    # a scale cuirass under three-plate pauldrons, a gold gorget and sternum ridge, the pearl in its heart
    return grid([
        "................",
        ".3444......4443.",
        "e66666eeww66666w",
        "444444seqs333333",
        "e66666seqs55555w",
        ".33333seqs22222.",
        "..5555sWPs4444..",
        "...sssseqssss...",
        "...sssseqssss...",
        "...sssseqssss...",
        "...sssseqssss...",
        "...sssseqssss...",
        "....wwwaAwww....",
        "....ssssssss....",
        ".....ssssss.....",
        "................",
    ], "A")


def lord_legs():
    # scale tassets over the thighs, gold rims, pearl studs on the knees
    return grid([
        "................",
        "................",
        "....wwwaAwww....",
        "....ssssssss....",
        "....55554444....",
        "....44443333....",
        "....eeeewwww....",
        "...ssss..ssss...",
        "...ssss..ssss...",
        "...sWss..ssPs...",
        "...6666..5555...",
        "...ssss..ssss...",
        "...ssss..ssss...",
        "...eeee..wwww...",
        "................",
        "................",
    ], "A")


def lord_boots():
    c = Canvas()
    ml, mr = poly_mask(SIL["boots_l"]), poly_mask(SIL["boots_r"])
    for mask, dim in ((ml, 0.0), (mr, 0.3)):
        for y in range(16):
            for x in range(16):
                if mask[y][x]:
                    c.set(x, y, mix(scale_px(x, y, lo=1, hi=4), P("lord", 0), dim))
    for x in range(16):
        if ml[4][x] or mr[4][x]:
            c.set(x, 4, P("gold", 4 if x < 8 else 3))
        if ml[5][x] or mr[5][x]:
            c.set(x, 5, P("gold", 2))
    # gold talons at the toes: three claws each
    for x, y in ((0, 14), (1, 15), (2, 14), (3, 15), (4, 14)):
        put(c, x, y, P("gold", 5 if y == 15 else 4))
    for x, y in ((11, 14), (12, 15), (13, 14), (14, 15), (15, 14)):
        put(c, x, y, P("gold", 4 if y == 15 else 3))
    put(c, 4, 8, gem("A", True))
    put(c, 11, 8, gem("A", True))
    return outline(c)


def lord_blade():
    c = Canvas()
    L = 15.4

    def width(tt):
        return 2.1 if tt < 12.0 else max(0.4, 2.1 - (tt - 12.0) * 0.62)

    def col(tt, sn):
        if sn < -0.55:
            return P("gold", 5 if int(tt * 1.5) % 3 else 4)   # the molten edge
        if abs(sn + 0.05) < 0.25 and 6.8 < tt < 13.2:
            return P("pearl", 4 if int(tt) % 2 else 5)          # the pearl fuller
        if sn < 0.2:
            return P("lord", 5 if tt > 12 else 4)
        return P("lord", 2 if sn < 0.65 else 1)
    diag_band(c, OX, OY, 6.0, L, width, col, lambda tt: 0.25)
    # winged crossguard of gold
    diag_band(c, OX, OY, 4.9, 6.0, 3.3, lambda tt, sn: P("gold", 5 if sn < -0.4 else 3 if sn < 0.4 else 2))
    for x, y in ((3, 8), (8, 13)):
        put(c, x, y, P("gold", 4))
    grip(c, "A", LOOK["A"], 1.2, 4.9)
    put(c, 5, 10, gem("A", True))
    put(c, 2, 14, gem("A", True))
    put(c, 3, 14, P("gold", 3))
    put(c, 2, 13, P("gold", 4))
    return outline(c)


def lord_focus():
    c = Canvas()
    diag_band(c, OX, OY, 1.0, 9.0, 0.85, lambda tt, sn: scale_px(int(tt * 2), int(sn * 2 + 4), lo=1, hi=4) if int(tt) % 3 else P("gold", 3))
    cx, cy = 11.2, 4.6
    # the sky pearl, glowing from within
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= 2.7:
                c.set(x, y, P("pearl", 5 if d < 1.0 else 4 if d < 1.8 else 3 if x + y < cx + cy else 2))
    # a gold claw of three prongs closing round it
    for x, y in ((8, 7), (8, 6), (8, 5), (9, 4), (9, 3)):
        put(c, x, y, P("gold", 4))
    for x, y in ((10, 8), (11, 8), (12, 8), (13, 7), (14, 6)):
        put(c, x, y, P("gold", 3))
    for x, y in ((13, 2), (12, 1), (11, 1), (10, 1)):
        put(c, x, y, P("gold", 5))
    put(c, 9, 7, P("gold", 2))
    put(c, 10, 2, P("pearl", 5))
    put(c, 7, 8, gem("A", True))
    return outline(c)


def lord_mattock():
    c = mattock_icon("A")
    # pearl inlay in the head
    put(c, 10, 6, P("pearl", 5))
    put(c, 9, 7, P("pearl", 4))
    return c


# ---- S: Sovereign Weave icons ----------------------------------------------------------------------------------------

def sov_helmet():
    c = Canvas()
    m = poly_mask([(3, 5), (6, 3.6), (10, 3.6), (13, 5), (14, 10), (15, 15), (1, 15), (2, 10)])
    for y in range(16):
        for x in range(16):
            if m[y][x]:
                v = weave_px(x, y)
                if x >= 10:
                    v = mix(v, P("weave", 0), 0.35)
                if x <= 4:
                    v = mix(v, P("weave", 6), 0.25)
                c.set(x, y, v)
    face = poly_mask([(5, 6.5), (11, 6.5), (11.5, 13), (4.5, 13)])
    for y in range(16):
        for x in range(16):
            if face[y][x]:
                c.set(x, y, P("weave", 0))
    # crimson eyes in the dark
    for x in (6, 9):
        put(c, x, 9, P("crimson", 5))
        put(c, x + 1, 9, P("crimson", 4))
    # gold hem
    for x in range(1, 15):
        if m[14][x]:
            c.set(x, 14, P("gold", 4 if x % 2 else 3))
    # the crown: a gold band round the hood, five spires, a crimson jewel at the front
    for x in range(3, 13):
        put(c, x, 4, P("gold", 5 if x < 8 else 4))
        put(c, x, 5, P("gold", 3 if x < 8 else 2))
    for x, top in ((3, 2), (5, 1), (7, 0), (8, 0), (10, 1), (12, 2)):
        for y in range(top, 4):
            put(c, x, y, P("gold", 5 if x < 8 else 4) if y > top else P("crimson", 5))
    put(c, 7, 5, P("crimson", 5))
    put(c, 8, 5, P("crimson", 3))
    return outline(c)


def sov_chest():
    # a royal robe: a high collar lined in crimson, ermine over the shoulders, gold epaulettes, a filigree band
    return grid([
        "....ew....we....",
        "....ec....ce....",
        ".IIIIcsYYscIIII.",
        "eeeiissCcssiiwww",
        "w.wssssYessssw.w",
        ".w.ssssewssss.w.",
        "...ssssYessss...",
        "...ssssewssss...",
        "...ssssqqssss...",
        "...ssssewssss...",
        "...ssssYessss...",
        "...ssssewssss...",
        "...ssssqqssss...",
        "...ssssewssss...",
        "...ewewewewew...",
        "................",
    ], "S")


def sov_legs():
    # legwraps: thin gold wraps spiralling down each leg, a crimson clasp on the belt
    rows = [
        "................",
        "................",
        "....eeeCceee....",
        "....ssssssss....",
        "....ssssssss....",
        "....ssssssss....",
        "...ssssssssss...",
        "...ssss..ssss...",
        "...ssss..ssss...",
        "...ssss..ssss...",
        "...ssss..ssss...",
        "...ssss..ssss...",
        "...ssss..ssss...",
        "...ssss..ssss...",
        "...eeee..wwww...",
        "................",
    ]
    rows = [list(r) for r in rows]
    for y in range(7, 14):
        rows[y][3 + (y + 1) % 4] = "e"
        rows[y][12 - (y + 1) % 4] = "w"
    return grid(["".join(r) for r in rows], "S")


def sov_boots():
    c = Canvas()
    ml, mr = poly_mask(SIL["boots_l"]), poly_mask(SIL["boots_r"])
    for mask, dim in ((ml, 0.0), (mr, 0.3)):
        for y in range(16):
            for x in range(16):
                if mask[y][x]:
                    c.set(x, y, mix(weave_px(x, y), P("weave", 0), dim))
    for x in range(16):
        if ml[4][x] or mr[4][x]:
            c.set(x, 4, P("gold", 4 if x < 8 else 3))
        if ml[13][x] or mr[13][x]:
            c.set(x, 13, P("gold", 3 if x < 8 else 2))
    # little gold fins at the ankles
    for x, y in ((1, 5), (0, 4), (1, 6)):
        put(c, x, y, P("gold", 5))
    for x, y in ((14, 5), (15, 4), (14, 6)):
        put(c, x, y, P("gold", 3))
    put(c, 4, 9, P("crimson", 5))
    put(c, 11, 9, P("crimson", 4))
    return outline(c)


def sov_blade():
    c = Canvas()
    L = 15.4

    def width(tt):
        return 1.55 if tt < 12.6 else max(0.35, 1.55 - (tt - 12.6) * 0.55)

    def col(tt, sn):
        if abs(sn) < 0.3 and 6.6 < tt < 14.0:
            return P("crimson", 5 if int(tt * 2) % 3 else 4)    # the oath, burning in the black
        if sn < -0.5:
            return P("ivory", 5 if tt > 12 else 4)               # a pale edge
        if sn < 0:
            return P("weave", 5)
        return P("weave", 3 if sn < 0.6 else 1)
    diag_band(c, OX, OY, 6.0, L, width, col, lambda tt: 0.05)
    # a crossguard like a crown: three gold points
    diag_band(c, OX, OY, 5.0, 6.0, 3.0, lambda tt, sn: P("gold", 5 if sn < -0.3 else 3 if sn < 0.4 else 2))
    for x, y in ((3, 9), (2, 9), (8, 14), (8, 15)):
        put(c, x, y, P("gold", 4))
    grip(c, "S", LOOK["S"], 1.2, 5.0)
    put(c, 2, 14, P("crimson", 5))
    put(c, 3, 14, P("gold", 3))
    put(c, 2, 13, P("gold", 4))
    return outline(c)


def sov_focus():
    c = Canvas()
    diag_band(c, OX, OY, 1.0, 9.2, 0.75, lambda tt, sn: P("gold", 5 if sn < -0.2 else 3 if sn < 0.5 else 2) if int(tt * 1.2) % 4 else P("crimson", 3))
    cx, cy = 11.0, 5.0
    # the Herald's halo round a crimson orb, a crown of points above
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if 3.0 <= d <= 3.9:
                c.set(x, y, P("gold", 5 if x + y < cx + cy else 3))
            elif d <= 2.0:
                c.set(x, y, P("crimson", 5 if d < 0.9 else 4 if x + y < cx + cy else 3))
    for x, y in ((11, 0), (13, 1), (9, 1)):
        put(c, x, y, P("gold", 5))
    put(c, 10, 4, P("ivory", 5))
    return outline(c)


def sov_mattock():
    c = mattock_icon("S")
    put(c, 10, 6, P("crimson", 5))
    put(c, 9, 7, P("crimson", 4))
    return c


LATE_ICONS = {
    "A": {"helmet": lord_helmet, "chestplate": lord_chest, "leggings": lord_legs, "boots": lord_boots,
          "blade": lord_blade, "focus": lord_focus, "mattock": lord_mattock},
    "S": {"helmet": sov_helmet, "chestplate": sov_chest, "leggings": sov_legs, "boots": sov_boots,
          "blade": sov_blade, "focus": sov_focus, "mattock": sov_mattock},
}
GLOW_RAMP = {"C": "signal", "B": "spirit", "A": "pearl", "S": "crimson"}


def late_glow(t, piece):
    """Emissive layer: the A pearl and the S crimson that burn at night."""
    c = Canvas()
    src = LATE_ICONS[t][piece]()
    hot = {P(GLOW_RAMP[t], i)[:3] for i in (3, 4, 5)}
    for y in range(16):
        for x in range(16):
            col = src.get(x, y)
            if col[3] and col[:3] in hot:
                c.set(x, y, col)
    return c


# ---- late armour layers (64x32 base) ---------------------------------------------------------------------------------

def late_face(c, t, rect, fn):
    x0, y0, fw, fh = rect
    for j in range(fh):
        for i in range(fw):
            col = fn(i, j, fw, fh)
            if col is not None:
                c.set(x0 + i, y0 + j, col)


def late_material(t, x, y):
    return scale_dark(x, y) if t == "A" else weave_px(x, y)


def late_layer(t, leggings=False):
    """The normal 64x32 layer of a late set (also the top-left of its silhouette texture)."""
    c = Canvas(64, 32)
    head, body, arm, leg = _box(0, 0, 8, 8, 8), _box(16, 16, 8, 12, 4), _box(40, 16, 4, 12, 4), _box(0, 16, 4, 12, 4)
    lord = t == "A"
    mat = lambda i, j, fw, fh: late_material(t, i + 3, j + 1)  # noqa: E731
    if leggings:
        for f, r in body.items():
            if f in ("front", "back", "left", "right"):
                late_face(c, t, (r[0], r[1] + 8, r[2], 4), lambda i, j, fw, fh: P("gold", 4 if (i + j) % 3 else 3) if j == 0
                          else late_material(t, i, j + 8))
            elif f == "bottom":
                late_face(c, t, r, mat)
        fx, fy, fw, fh = body["front"]
        put(c, fx + 3, fy + 9, gem(t, True) if lord else P("crimson", 5))
        put(c, fx + 4, fy + 9, gem(t) if lord else P("crimson", 3))
        for f, r in leg.items():
            if f in ("front", "back", "left", "right"):
                def lf(i, j, fw, fh, f=f):
                    if j >= 9:
                        return None
                    if lord:
                        if j == 5:
                            return P("lord", 6 if f == "front" else 5)   # the tassets' lit lower edge
                        if f == "front" and j == 7 and i in (1, 2):
                            return P("gold", 4)                          # knee cop
                        return scale_dark(i, j)
                    # wraps: a thin gold band spiralling down the leg
                    if (i + j) % 6 == 0:
                        return P("gold", 4 if f in ("front", "left") else 3)
                    return weave_px(i, j)
                late_face(c, t, r, lf)
            elif f == "top":
                late_face(c, t, r, mat)
        lx, ly, lw, lh = leg["front"]
        if lord:
            put(c, lx + 1, ly + 4, P("pearl", 5))
        return c
    # helmet
    for f, r in head.items():
        if f == "bottom":
            continue

        def hf(i, j, fw, fh, f=f):
            if lord:
                if (f == "top" and i in (3, 4)) or (f == "front" and j == 0):
                    return P("gold", 4 if f in ("front", "top") else 3)
                return scale_dark(i, j)
            if f == "top":
                return weave_px(i, j)
            if j == 1:
                return P("gold", 4 if f == "front" else 3)       # the circlet under the crown
            return weave_px(i, j)
        late_face(c, t, r, hf)
    hx0, hy0, hw, hh = head["front"]
    if lord:   # closed greathelm: a slit of pearl light, a gold nasal, the amber gem on the brow
        for i in range(1, 7):
            c.set(hx0 + i, hy0 + 4, P("pearl", 5 if 2 < i < 5 else 4))
            c.set(hx0 + i, hy0 + 5, P("lord", 0))
        for j in range(3, 8):
            c.set(hx0 + 3, hy0 + j, P("gold", 4))
            c.set(hx0 + 4, hy0 + j, P("gold", 3))
        c.set(hx0 + 3, hy0 + 4, P("pearl", 5))
        c.set(hx0 + 4, hy0 + 4, P("pearl", 5))
        c.set(hx0 + 3, hy0 + 2, gem("A", True))
        c.set(hx0 + 4, hy0 + 2, gem("A"))
    else:      # the hood leaves the face open; a crimson jewel on the circlet
        for j in range(2, 8):
            for i in range(1, 7):
                c.set(hx0 + i, hy0 + j, (0, 0, 0, 0))
        c.set(hx0 + 3, hy0 + 1, P("crimson", 5))
        c.set(hx0 + 4, hy0 + 1, P("crimson", 3))
        for i in range(8):
            c.set(hx0 + i, hy0 + 7, P("gold", 3) if i in (0, 7) else c.get(hx0 + i, hy0 + 7))
    # chest: body and arms
    for f, r in body.items():
        late_face(c, t, r, lambda i, j, fw, fh: late_material(t, i, j))
    for f, r in arm.items():
        if f in ("front", "back", "left", "right"):
            late_face(c, t, (r[0], r[1], r[2], 9), lambda i, j, fw, fh: P("gold", 4 if f in ("front", "left") else 3) if j in (0, 8)
                      else late_material(t, i + 1, j))
        elif f == "top":
            late_face(c, t, r, lambda i, j, fw, fh: P("gold", 3))
    bx, by, bw, bh = body["front"]
    if lord:
        for i in range(bw):
            c.set(bx + i, by, P("gold", 4 if i % 2 else 3))     # gorget rim
            c.set(bx + i, by + bh - 2, P("gold", 3))            # belt
        for j in range(1, bh - 2):
            c.set(bx + 3, by + j, P("gold", 4 if j % 3 else 5))  # the sternum ridge
            c.set(bx + 4, by + j, P("gold", 2))
        for (i, j, k) in ((3, 4, 5), (4, 4, 4), (3, 5, 3), (4, 5, 2)):
            c.set(bx + i, by + j, P("pearl", k))
        c.set(bx + 3, by + bh - 2, gem("A", True))
    else:
        for i in range(bw):
            c.set(bx + i, by, P("ivory", 5 if i % 2 else 4))     # ivory at the neck
            c.set(bx + i, by + bh - 1, P("gold", 4 if i % 2 else 3))
        for j in range(2, bh - 1):
            k = j % 4
            c.set(bx + 3, by + j, P("gold", 5 if k == 2 else 3 if k != 0 else 4))
            c.set(bx + 4, by + j, P("gold", 4 if k == 2 else 2 if k != 0 else 3))
        for (i, j, k) in ((3, 1, 5), (4, 1, 4), (3, 2, 3), (4, 2, 2)):
            c.set(bx + i, by + j, P("crimson", k))
    kx, ky, kw, kh = body["back"]
    if lord:   # a ridge down the spine (the spikes stand on it)
        for j in range(1, kh - 1):
            c.set(kx + 3, ky + j, P("lord", 5))
            c.set(kx + 4, ky + j, P("gold", 3 if j % 2 else 2))
    else:      # the Sovereign's mark between the shoulders (mostly under the cape)
        for (i, j) in ((3, 2), (4, 2), (2, 3), (5, 3), (3, 4), (4, 4), (3, 5), (4, 5)):
            c.set(kx + i, ky + j, P("gold", 4))
        c.set(kx + 3, ky + 3, P("crimson", 5))
        c.set(kx + 4, ky + 3, P("crimson", 4))
    # boots on the legs: the lower part only
    for f, r in leg.items():
        if f in ("front", "back", "left", "right"):
            late_face(c, t, (r[0], r[1] + 7, r[2], 5), lambda i, j, fw, fh: P("gold", 4 if f == "front" else 3) if j == 0
                      else late_material(t, i, j + 7))
        elif f == "bottom":
            late_face(c, t, r, lambda i, j, fw, fh: P("lord" if lord else "weave", 1))
    lx, ly, lw, lh = leg["front"]
    if lord:
        c.set(lx + 1, ly + 9, gem("A", True))
    return c


# ---- the silhouette parts --------------------------------------------------------------------------------------------
# Each part: name, parent (a base part or an earlier extra), box [x, y, z, w, h, d] in the parent's space (model pixels,
# y down, -z is the front), pose [x, y, z, xRot, yRot, zRot] (radians; rotation order Z, Y, X like ModelPart), mirror
# (the left twin of a right part shares its texture), and a painter for its faces. UVs are packed automatically into
# the silhouette texture outside the 64x32 base area and written to the JSON the client reads.

SIL_TEX = (128, 64)
BASE_PARTS = {"head": "head", "body": "chest", "right_arm": "chest", "left_arm": "chest", "right_leg": "feet", "left_leg": "feet"}


def _part(name, parent, box, pose=(0, 0, 0, 0, 0, 0), paint=None, twin=None):
    return {"name": name, "parent": parent, "box": [float(v) for v in box], "pose": [float(v) for v in pose],
            "paint": paint, "twin": twin}


def _mirror(parts, rename):
    """Left twins of right parts: x mirrored, yRot and zRot negated, same texture (vanilla's mirror flag)."""
    out = []
    for p in parts:
        x, y, z, w, h, d = p["box"]
        px, py, pz, xr, yr, zr = p["pose"]
        parent = rename.get(p["parent"], p["parent"])
        out.append({"name": rename[p["name"]], "parent": parent, "box": [-x - w, y, z, w, h, d],
                    "pose": [-px, py, pz, xr, -yr, -zr], "paint": p["paint"], "twin": p["name"]})
    return out


def _lord_parts():
    horn = [_part("horn_r", "head", (-6, -1, -1, 6, 2, 2), (-4.0, -6.5, 0.5, 0, 0.35, 0.55), "horn"),
            _part("horn_r_tip", "horn_r", (-4, -0.5, -0.5, 4, 1, 1), (-5.6, 0, 0, 0, 0.25, 0.55), "horn_tip")]
    crest = [_part("crest", "head", (-0.5, -2.5, -3.5, 1, 3, 8), (0, -9, 0, 0, 0, 0), "crest")]
    paul = [_part("pauldron_r", "right_arm", (-3.5, -2.5, -3.5, 5, 3, 7), (-1, -2.6, 0, 0, 0, -0.32), "pauldron"),
            _part("pauldron_r_lower", "pauldron_r", (-1.5, 0, -3, 2, 4, 6), (-3.0, 0.4, 0, 0, 0, -0.22), "pauldron_lower")]
    spine = [_part("spike_%d" % i, "body", (-0.5, -0.5, 0, 1, 1, 3), (0, 2.2 + 3.1 * i, 2.6, 0.55 - 0.1 * i, 0, 0), "spike")
             for i in range(3)]
    feet = [_part("claw_r%d" % k, "right_leg", (-0.5, -0.5, -2, 1, 1, 2), (-1.9 + 1.9 * k, 12.2, -2.9, -0.35, 0.18 * (k - 1), 0), "talon")
            for k in range(3)]
    feet += [_part("spur_r", "right_leg", (-0.5, -0.5, 0, 1, 1, 3), (0, 9.6, 2.9, 0.55, 0, 0), "spur")]
    left = {"horn_r": "horn_l", "horn_r_tip": "horn_l_tip", "pauldron_r": "pauldron_l", "pauldron_r_lower": "pauldron_l_lower",
            "claw_r0": "claw_l0", "claw_r1": "claw_l1", "claw_r2": "claw_l2", "spur_r": "spur_l",
            "right_arm": "left_arm", "right_leg": "left_leg"}
    return horn + _mirror(horn, left) + crest + paul + _mirror(paul, left) + spine + feet + _mirror(feet, left)


def _sov_parts():
    crown = [_part("crown_front", "head", (-5.5, -10.5, -5.5, 11, 2, 1), paint="band"),
             _part("crown_back", "head", (-5.5, -10.5, 4.5, 11, 2, 1), paint="band"),
             _part("crown_r", "head", (-5.5, -10.5, -4.5, 1, 2, 9), paint="band_side"),
             _part("spire_c", "head", (-0.5, -14, -5.75, 1, 4, 1), paint="spire"),
             _part("spire_r", "head", (-3.75, -12.5, -5.75, 1, 2, 1), paint="spire"),
             _part("spire_br", "head", (-5.25, -12.5, 4.75, 1, 2, 1), paint="spire")]
    crown_left = _mirror([p for p in crown if p["name"] in ("crown_r", "spire_r", "spire_br")],
                         {"crown_r": "crown_l", "spire_r": "spire_l", "spire_br": "spire_bl"})
    # a standing collar fanned out behind the head (wider than the hood, so it frames the face from the front), and a
    # short cape that clears the legs when they swing
    chest = [_part("collar", "body", (-7.5, -8, 0, 15, 8, 1), (0, 0.6, 3.2, -0.42, 0, 0), "collar"),
             _part("cape", "body", (-4.5, 0, 0, 9, 13, 1), (0, 0.4, 3.5, 0.18, 0, 0), "cape")]
    paul = [_part("pauldron_r", "right_arm", (-3.5, -1.5, -3.5, 5, 2, 7), (-1, -3.1, 0, 0, 0, -0.36), "disc"),
            _part("epaulette_r", "pauldron_r", (-0.5, 0, -3.5, 1, 2.5, 7), (-3.6, 0.4, 0, 0, 0, 0), "fringe")]
    feet = [_part("fin_r", "right_leg", (-0.5, -3, 0, 1, 3, 3), (-2.9, 9.0, 0.8, 0.4, 0, 0), "fin")]
    left = {"pauldron_r": "pauldron_l", "epaulette_r": "epaulette_l", "fin_r": "fin_l", "right_arm": "left_arm", "right_leg": "left_leg"}
    return crown + crown_left + chest + paul + _mirror(paul, left) + feet + _mirror(feet, left)


SILHOUETTE = {"A": _lord_parts(), "S": _sov_parts()}


def uv_size(box):
    x, y, z, w, h, d = box
    return int(math.ceil(2 * (d + w))), int(math.ceil(d + h))


def pack_uvs(parts):
    """Shelf-packs every part that owns a texture (twins share their right-hand part's) outside the base 64x32."""
    regions = [(64, 0, 64, 32), (0, 32, 128, 32)]
    uv = {}
    owners = sorted([p for p in parts if not p["twin"]], key=lambda p: -uv_size(p["box"])[1])
    shelves = []  # (region index, y, height, x cursor)
    for p in owners:
        w, h = uv_size(p["box"])
        placed = False
        for s in shelves:
            ri, sy, sh, sx = s
            rx, ry, rw, rh = regions[ri]
            if h <= sh and sx + w <= rx + rw:
                uv[p["name"]] = (sx, sy)
                s[3] = sx + w
                placed = True
                break
        if placed:
            continue
        for ri, (rx, ry, rw, rh) in enumerate(regions):
            used = max([s[1] + s[2] for s in shelves if s[0] == ri], default=ry)
            if used + h <= ry + rh and w <= rw:
                shelves.append([ri, used, h, rx + w])
                uv[p["name"]] = (rx, used)
                placed = True
                break
        assert placed, "silhouette texture is full at " + p["name"]
    for p in parts:
        if p["twin"]:
            uv[p["name"]] = uv[p["twin"]]
    return uv


def silhouette_json(t):
    parts = SILHOUETTE[t]
    uv = pack_uvs(parts)
    slot = {}
    for p in parts:
        slot[p["name"]] = BASE_PARTS.get(p["parent"]) or slot[p["parent"]]
    return {"schema": 1, "texture": list(SIL_TEX), "parts": [
        {"name": p["name"], "parent": p["parent"], "slot": slot[p["name"]], "box": p["box"], "uv": list(uv[p["name"]]),
         "mirror": bool(p["twin"]), "pose": p["pose"]} for p in parts]}


def box_faces(u, v, box):
    """Texture rectangles of a box's faces at (u, v), like ModelPart.Cube: name -> (x, y, w, h)."""
    x, y, z, w, h, d = box
    w, h, d = int(math.ceil(w)), int(math.ceil(h)), int(math.ceil(d))
    return {"down": (u + d + w, v, w, d), "up": (u + d, v, w, d), "west": (u, v + d, d, h), "north": (u + d, v + d, w, h),
            "east": (u + d + w, v + d, d, h), "south": (u + d + w + d, v + d, w, h)}


def paint_part(c, t, kind, rects):
    """Fills a part's faces by kind. rects: face name -> (x, y, w, h)."""
    for face, (x0, y0, fw, fh) in rects.items():
        for j in range(fh):
            for i in range(fw):
                col = part_px(t, kind, face, i, j, fw, fh)
                if col is not None:
                    c.set(x0 + i, y0 + j, col)


def part_px(t, kind, face, i, j, fw, fh):
    side = face in ("west", "east", "north", "south")
    if kind in ("horn", "horn_tip"):
        # horns run along -x: the west face is the tip end, the east face the root; on the long faces the columns run
        # along x (south's columns run the other way)
        if face == "west":
            along = 1.0
        elif face == "east":
            along = 0.0
        elif face == "south":
            along = i / max(1, fw - 1)
        else:
            along = 1 - i / max(1, fw - 1)
        tip = kind == "horn_tip"
        k = along * 0.5 + (0.5 if tip else 0)
        if k > 0.82:
            return P("gold", 5 if face in ("up", "west") else 4)
        if k > 0.62:
            return P("gold", 3)
        return P("lord", 4 if face == "up" else 3 if face in ("west", "north") else 2) if (i + j) % 3 else P("lord", 5)
    if kind == "crest":
        if face == "up":
            return P("gold", 5)
        if side:
            return P("gold", 4) if j == 0 else scale_dark(i, j + 1)
        return P("gold", 3)
    if kind in ("pauldron", "pauldron_lower"):
        if face == "up":
            return P("lord", 5) if j in (0, fh - 1) or i in (0, fw - 1) else scale_dark(i, j)
        if face == "down":
            return P("lord", 1)
        if j == fh - 1:
            return P("gold", 4 if face in ("north", "west") else 3)    # the plate's gold lower edge
        return P("lord", 5) if j == 0 else scale_dark(i, j + 1)
    if kind == "spike":
        if face in ("west", "east"):
            along = i / max(1, fw - 1)
            if face == "west":
                along = 1 - along
        else:
            along = 1.0 if face == "south" else 0.0 if face == "north" else 1 - j / max(1, fh - 1)
        return P("gold", 4) if along > 0.66 else P("lord", 4 if face == "up" else 2)
    if kind == "talon":
        # a claw pointing forward: the front end bright, the root darker gold
        if face == "north":
            return P("gold", 5)
        if face in ("west", "east", "up", "down"):
            front = i / max(1, fw - 1) if face == "east" else 1 - i / max(1, fw - 1) if face == "west" else 1 - j / max(1, fh - 1)
            return P("gold", 4 if front > 0.5 else 2)
        return P("gold", 2)
    if kind == "spur":
        return P("gold", 4 if face in ("up", "south") else 3)
    # ---- Sovereign Weave
    if kind == "band":
        if face == "north" or face == "south":
            return P("crimson", 5) if (i == fw // 2 and j == 0) else P("gold", 5 if j == 0 else 3)
        return P("gold", 4)
    if kind == "band_side":
        return P("gold", 4 if face in ("up", "west") else 3)
    if kind == "spire":
        if face == "up" or (side and j == 0):
            return P("crimson", 5)
        return P("gold", 5 if face in ("north", "west") else 3)
    if kind == "collar":
        if face == "south":            # the outside of the collar, seen from behind
            return P("gold", 4) if j == 0 or i in (0, fw - 1) else weave_px(i, j)
        if face == "north":            # the inside, seen past your head: crimson lining
            return P("gold", 3) if j == 0 else P("crimson", 2 if (i + j) % 3 else 3)
        return P("gold", 4)
    if kind == "cape":
        if face == "south":            # the back: weave, a gold border and the Sovereign's mark
            if i in (0, fw - 1) or j == fh - 1:
                return P("gold", 4 if (i + j) % 2 else 3)
            cx, cy = fw / 2 - 0.5, 4
            dx, dy = abs(i - cx), abs(j - cy)
            if dx + dy <= 2.5 and dx + dy >= 1.5:
                return P("gold", 4)
            if dx + dy < 1.0:
                return P("crimson", 5)
            return weave_px(i, j)
        if face == "north":
            return P("crimson", 1 if j % 2 else 2)                # lining against your back
        return weave_px(i, j)
    if kind == "disc":
        if face == "up":
            r = math.hypot(i - fw / 2 + 0.5, j - fh / 2 + 0.5)
            return P("gold", 5) if r > 2.6 else P("weave", 4) if r > 1.2 else P("crimson", 4)
        return P("gold", 4 if face in ("north", "west") else 2)
    if kind == "fringe":
        return P("gold", 5 if j < 1 else 3 if (i + j) % 2 else 2) if face != "down" else P("gold", 2)
    if kind == "fin":
        return P("gold", 5 if face in ("west", "up") else 3)
    return None


def silhouette_texture(t):
    """128x64: the normal layer at the top left, the parts' faces in the rest."""
    c = Canvas(*SIL_TEX)
    base = late_layer(t, False)
    for y in range(32):
        for x in range(64):
            c.set(x, y, base.get(x, y))
    parts = SILHOUETTE[t]
    uv = pack_uvs(parts)
    for p in parts:
        if p["twin"]:
            continue
        u, v = uv[p["name"]]
        paint_part(c, t, p["paint"], box_faces(u, v, p["box"]))
    return c


# ---------------------------------------------------------------------------------------------------------------------
# A small software renderer for the look test: the same cube, UV and pose maths as ModelPart (net.minecraft.client.model
# .geom.ModelPart.Cube), so what the preview shows is what the armour model builds. Orthographic, with a depth buffer.


def _matmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _rot_zyx(xr, yr, zr):
    cx, sx, cy, sy, cz, sz = math.cos(xr), math.sin(xr), math.cos(yr), math.sin(yr), math.cos(zr), math.sin(zr)
    rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return _matmul(rz, _matmul(ry, rx))


class _Pose:
    def __init__(self, m=None, t=(0.0, 0.0, 0.0)):
        self.m = m or [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
        self.t = t

    def child(self, pose):
        px, py, pz, xr, yr, zr = pose
        t = self.apply((px, py, pz))
        return _Pose(_matmul(self.m, _rot_zyx(xr, yr, zr)), t)

    def apply(self, v):
        m = self.m
        return (m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2] + self.t[0],
                m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2] + self.t[1],
                m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2] + self.t[2])


def cube_polys(u, v, box, grow=0.0, mirror=False):
    """The six faces of a cube as (four (pos, uv) vertices), exactly as ModelPart.Cube builds them."""
    x, y, z, w, h, d = box
    x0, y0, z0, x1, y1, z1 = x - grow, y - grow, z - grow, x + w + grow, y + h + grow, z + d + grow
    if mirror:
        x0, x1 = x1, x0
    t0, t1, t2, t3 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    l0, l1, l2, l3 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    u0, u1, u2, u22, u3, u4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    faces = [([l1, l0, t0, t1], u1, v0, u2, v1), ([t2, t3, l3, l2], u2, v1, u22, v0), ([t0, l0, l3, t3], u0, v1, u1, v2),
             ([t1, t0, t3, t2], u1, v1, u2, v2), ([l1, t1, t2, l2], u2, v1, u3, v2), ([l0, l1, l2, l3], u3, v1, u4, v2)]
    out = []
    for verts, a0, b0, a1, b1 in faces:
        uvs = [(a1, b0), (a0, b0), (a0, b1), (a1, b1)]
        out.append(list(zip(verts, uvs)))
    return out


class Scene:
    def __init__(self, scale=8, yaw=0.0, pitch=-0.18, size=(230, 330), centre=(115, 92)):
        from PIL import Image
        self.s = scale
        self.yaw, self.pitch = yaw, pitch
        self.w, self.h = size
        self.cx, self.cy = centre
        self.img = Image.new("RGBA", size, (0, 0, 0, 0))
        self.px = self.img.load()
        self.depth = [[1e9] * size[0] for _ in range(size[1])]

    def project(self, p):
        x, y, z = p
        ca, sa = math.cos(self.yaw), math.sin(self.yaw)
        x2, z2 = x * ca + z * sa, -x * sa + z * ca
        cp, sp = math.cos(self.pitch), math.sin(self.pitch)
        y2, z3 = y * cp - z2 * sp, y * sp + z2 * cp
        return self.cx + x2 * self.s, self.cy + y2 * self.s, z3

    def quad(self, pts, colour):
        """Fills a projected quad (four (sx, sy, depth)) with a colour, depth-tested."""
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        bx0, bx1 = max(0, int(min(xs))), min(self.w - 1, int(max(xs)) + 1)
        by0, by1 = max(0, int(min(ys))), min(self.h - 1, int(max(ys)) + 1)
        zc = sum(p[2] for p in pts) / 4
        tris = ((pts[0], pts[1], pts[2]), (pts[0], pts[2], pts[3]))
        for yy in range(by0, by1 + 1):
            for xx in range(bx0, bx1 + 1):
                qx, qy = xx + 0.5, yy + 0.5
                for a, b, c in tris:
                    d = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
                    if abs(d) < 1e-9:
                        continue
                    l1 = ((b[1] - c[1]) * (qx - c[0]) + (c[0] - b[0]) * (qy - c[1])) / d
                    l2 = ((c[1] - a[1]) * (qx - c[0]) + (a[0] - c[0]) * (qy - c[1])) / d
                    l3 = 1 - l1 - l2
                    if l1 >= -1e-6 and l2 >= -1e-6 and l3 >= -1e-6:
                        if zc < self.depth[yy][xx]:
                            self.depth[yy][xx] = zc
                            self.px[xx, yy] = colour
                        break

    def cube(self, pose, tex, u, v, box, grow=0.0, mirror=False, shade=True):
        """Draws a cube textured from {tex} (a PIL image, UVs in its pixels), one quad per texel."""
        tw, th = tex.size
        texels = tex.load()
        for poly in cube_polys(u, v, box, grow, mirror):
            (p0, (a1, b0)), (p1, (a0, _)), (p2, (_, b1)), _ = poly
            if a1 == a0 or b1 == b0:
                continue
            w0 = [pose.apply(p) for p, _ in poly]
            du = [(w0[0][k] - w0[1][k]) / (a1 - a0) for k in range(3)]
            dv = [(w0[2][k] - w0[1][k]) / (b1 - b0) for k in range(3)]
            nx = du[1] * dv[2] - du[2] * dv[1]
            ny = du[2] * dv[0] - du[0] * dv[2]
            nz = du[0] * dv[1] - du[1] * dv[0]
            n = math.sqrt(nx * nx + ny * ny + nz * nz) or 1
            light = 1.0
            if shade:   # a fixed light from the upper front left (y is down in model space)
                light = 0.72 + 0.28 * max(0.0, (-ny * 0.75 - nz * 0.45 + nx * 0.2) / n)
            ua, ub = sorted((a0, a1))
            va, vb = sorted((b0, b1))
            for ty in range(int(math.floor(va)), int(math.ceil(vb))):
                for tx in range(int(math.floor(ua)), int(math.ceil(ub))):
                    if not (0 <= tx < tw and 0 <= ty < th):
                        continue
                    col = texels[tx, ty]
                    if col[3] < 128:
                        continue
                    corners = []
                    for cu, cv in ((tx, ty), (tx + 1, ty), (tx + 1, ty + 1), (tx, ty + 1)):
                        cu, cv = min(max(cu, ua), ub), min(max(cv, va), vb)
                        p = [w0[1][k] + (cu - a0) * du[k] + (cv - b0) * dv[k] for k in range(3)]
                        corners.append(self.project(p))
                    c = (int(col[0] * light), int(col[1] * light), int(col[2] * light), 255)
                    self.quad(corners, c)


BODY_PIVOTS = {"head": (0, 0, 0, 0, 0, 0), "body": (0, 0, 0, 0, 0, 0), "right_arm": (-5, 2, 0, 0, 0, 0.06),
               "left_arm": (5, 2, 0, 0, 0, -0.06), "right_leg": (-1.9, 12, 0, 0, 0, 0), "left_leg": (1.9, 12, 0, 0, 0, 0)}


def _skin():
    """A plain 64x64 stand-in skin: tanned face and hands, a dark shirt and trousers."""
    from PIL import Image
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = img.load()
    for y in range(64):
        for x in range(64):
            if y < 16 and x < 32:
                d[x, y] = (196, 150, 118, 255)
            elif 16 <= y < 32 and 16 <= x < 40:
                d[x, y] = (54, 70, 92, 255)
            elif 16 <= y < 32 and x >= 40 or y >= 48 and 32 <= x < 48:
                d[x, y] = (196, 150, 118, 255)
            elif 16 <= y < 32 and x < 16 or y >= 48 and 16 <= x < 32:
                d[x, y] = (46, 46, 72, 255)
    for x, y in ((9, 12), (10, 12), (13, 12), (14, 12)):
        d[x, y] = (40, 40, 60, 255)
    return img


def render_set(t, yaw, scale=6, size=(150, 250), centre=(75, 96), pitch=-0.18):
    """A figure wearing tier t's whole set (with A/S silhouette parts), seen from yaw (0 = front, pi = back)."""
    from PIL import Image
    tex = os.path.join(RES, "assets/awaken/textures/entity/equipment")
    late = t in LATE
    main = Image.open(os.path.join(tex, "silhouette" if late else "humanoid", SET[t] + ".png")).convert("RGBA")
    legs = Image.open(os.path.join(tex, "humanoid_leggings", SET[t] + ".png")).convert("RGBA")
    sc = Scene(scale, yaw, pitch, size, centre)
    root = _Pose()
    poses = {k: root.child(v) for k, v in BODY_PIVOTS.items()}
    skin = _skin()
    sc.cube(poses["head"], skin, 0, 0, (-4, -8, -4, 8, 8, 8))
    sc.cube(poses["body"], skin, 16, 16, (-4, 0, -2, 8, 12, 4))
    sc.cube(poses["right_arm"], skin, 40, 16, (-3, -2, -2, 4, 12, 4))
    sc.cube(poses["left_arm"], skin, 32, 48, (-1, -2, -2, 4, 12, 4))
    sc.cube(poses["right_leg"], skin, 0, 16, (-2, 0, -2, 4, 12, 4))
    sc.cube(poses["left_leg"], skin, 16, 48, (-2, 0, -2, 4, 12, 4))
    # the armour, as HumanoidModel.createArmorMeshSet builds it (outer 1.0, inner 0.5, legs 0.1 thinner)
    sc.cube(poses["head"], main, 0, 0, (-4, -8, -4, 8, 8, 8), 1.0)
    sc.cube(poses["head"], main, 32, 0, (-4, -8, -4, 8, 8, 8), 1.5)
    sc.cube(poses["body"], main, 16, 16, (-4, 0, -2, 8, 12, 4), 1.0)
    sc.cube(poses["right_arm"], main, 40, 16, (-3, -2, -2, 4, 12, 4), 1.0)
    sc.cube(poses["left_arm"], main, 40, 16, (-1, -2, -2, 4, 12, 4), 1.0, mirror=True)
    sc.cube(poses["body"], legs, 16, 16, (-4, 0, -2, 8, 12, 4), 0.5)
    sc.cube(poses["right_leg"], legs, 0, 16, (-2, 0, -2, 4, 12, 4), 0.4)
    sc.cube(poses["left_leg"], legs, 0, 16, (-2, 0, -2, 4, 12, 4), 0.4, mirror=True)
    sc.cube(poses["right_leg"], main, 0, 16, (-2, 0, -2, 4, 12, 4), 0.9)
    sc.cube(poses["left_leg"], main, 0, 16, (-2, 0, -2, 4, 12, 4), 0.9, mirror=True)
    if late:
        spec = silhouette_json(t)
        frames = dict(poses)
        for p in spec["parts"]:
            frames[p["name"]] = frames[p["parent"]].child(p["pose"])
            sc.cube(frames[p["name"]], main, p["uv"][0], p["uv"][1], p["box"], 0.0, p["mirror"])
    return sc.img


def core_shard_icon():
    """A Power Core Shard (Anchor request reward): a curved wedge of the core's obsidian shell, a brass rim, and the
    signal-blue light still caught in its broken face. Four of them make a core."""
    c = Canvas()
    shell = poly_mask([(3, 12.5), (4.5, 6), (8, 2.5), (12.5, 1.5), (11, 5), (9.5, 9), (8.5, 13.5)])
    for y in range(16):
        for x in range(16):
            if shell[y][x]:
                light = 1 - (0.6 * x + 0.4 * y) / 16
                c.set(x, y, P("obsidian", 1 + int(round(light * 3))))
    # the broken face: signal light inside the shell
    for x, y, k in ((10, 4, 5), (9, 5, 5), (10, 5, 4), (9, 6, 4), (8, 7, 4), (9, 7, 3), (8, 8, 3), (8, 9, 3), (8, 10, 2)):
        put(c, x, y, P("signal", k))
    # the brass rim along the outer curve, a white glint
    for x, y in ((4, 11), (4, 10), (4, 9), (5, 7), (5, 6), (6, 5), (7, 4), (8, 3), (9, 2), (10, 2)):
        put(c, x, y, P("brass", 4 if y < 7 else 3))
    put(c, 6, 6, (255, 255, 255, 255))
    return outline(c)


# ---------------------------------------------------------------------------------------------------------------------
# Build


def build(out):
    for t in TIERS:
        if t in LATE:
            for piece in PIECES:
                out.item(item_id(t, piece), LATE_ICONS[t][piece]())
            for piece in ("blade", "focus"):
                out.item(item_id(t, piece) + "_glow", late_glow(t, piece))
            out.entity("equipment/humanoid/" + SET[t], late_layer(t, False))
            out.entity("equipment/humanoid_leggings/" + SET[t], late_layer(t, True))
            out.entity("equipment/silhouette/" + SET[t], silhouette_texture(t))
            continue
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
    out.item("core_shard", core_shard_icon())


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
            if t in ("C", "B", "A", "S") and piece in ("blade", "focus"):
                textures["layer1"] = {"sprite": "awaken:item/" + iid + "_glow", "neoforge_data": {"light_emission": 15}}
            write(os.path.join(a, "models/item", iid + ".json"),
                  {"parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated", "textures": textures})
            write(os.path.join(a, "items", iid + ".json"), {"model": {"type": "minecraft:model", "model": "awaken:item/" + iid}})
            lang["item.awaken." + iid] = display(t, piece)
            # recipe
            tl = t.lower()
            keymap = {"H": "#awaken:hunter_hide_" + tl, "C": "#awaken:hunter_core_" + tl, "B": "#awaken:hunter_bind_" + tl,
                      "S": "awaken:essence_shard", "L": "awaken:lord_heart", "/": "minecraft:stick",
                      "M": "#awaken:tier_metal_" + tl, "G": "minecraft:gold_ingot", "Y": "minecraft:amethyst_shard"}
            pattern = RECIPES[t][piece]
            used = sorted({ch for row in pattern for ch in row if ch != " "})
            write(os.path.join(d, "recipe", iid + ".json"), {
                "type": "minecraft:crafting_shaped", "category": "equipment", "group": "awaken_hunter_" + piece,
                "key": {ch: keymap[ch] for ch in used}, "pattern": pattern, "result": {"id": "awaken:" + iid}})
        lvl, title, text = ROAD[t]
        write(os.path.join(d, "awaken/level_road", "hunter_gear_" + t.lower() + ".json"), {
            "schema": 1, "level": lvl, "title": title, "text": text,
            "grants": [{"type": "recipe", "ids": ["awaken:" + item_id(t, p) for p in PIECES]}], "quiet": False})
    # the late sets' extra geometry (read by the client's armour model, checked by gear_silhouettes_valid)
    for t in LATE:
        write(os.path.join(a, "gear_silhouettes", SET[t] + ".json"), silhouette_json(t))
    # the Power Core Shard (hearth requests pay it; its lang lives in the hearth merge folder)
    write(os.path.join(a, "models/item/core_shard.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/core_shard"}})
    write(os.path.join(a, "items/core_shard.json"), {"model": {"type": "minecraft:model", "model": "awaken:item/core_shard"}})
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
    sheet = Image.new("RGBA", (7 * (S + 16) + 8, len(TIERS) * (S + 30) + 10), (24, 26, 34, 255))
    d = ImageDraw.Draw(sheet)
    for row, t in enumerate(TIERS):
        for col, piece in enumerate(PIECES):
            im = Image.open(os.path.join(tex, item_id(t, piece) + ".png")).convert("RGBA").resize((S, S), Image.NEAREST)
            x, y = col * (S + 16) + 8, row * (S + 30) + 8
            sheet.paste(im, (x, y), im)
            d.text((x, y + S + 2), display(t, piece)[:18], fill=(200, 205, 215, 255))
    sheet.save(os.path.join(dirpath, "gear_icons.png"))
    # every set on a figure: three-quarter front and back (the real cube maths, silhouette parts included)
    W, H = 150, 250
    figs = Image.new("RGBA", (len(TIERS) * 2 * W, H + 20), (24, 26, 34, 255))
    d = ImageDraw.Draw(figs)
    for i, t in enumerate(TIERS):
        for b, yaw in enumerate((0.55, math.pi + 0.55)):
            im = render_set(t, yaw)
            figs.paste(im, ((i * 2 + b) * W, 10), im)
        d.text((i * 2 * W + 8, H), SET_NAME[t] + " · " + t, fill=(200, 205, 215, 255))
    figs.save(os.path.join(dirpath, "gear_figures.png"))
    # the late sets, large: front, three-quarter, side, back
    W2, H2 = 240, 400
    late = Image.new("RGBA", (4 * W2, len(LATE) * (H2 + 16)), (24, 26, 34, 255))
    d = ImageDraw.Draw(late)
    for r, t in enumerate(LATE):
        for k, yaw in enumerate((0.0, 0.7, math.pi / 2, math.pi)):
            im = render_set(t, yaw, scale=9, size=(W2, H2), centre=(W2 // 2, 150))
            late.paste(im, (k * W2, r * (H2 + 16)), im)
        d.text((8, r * (H2 + 16) + H2), SET_NAME[t] + " · " + t, fill=(220, 225, 235, 255))
    late.save(os.path.join(dirpath, "gear_late.png"))
    # the raw layers, scaled (and the late sets' silhouette textures)
    tex2 = os.path.join(RES, "assets/awaken/textures/entity/equipment")
    lay = Image.new("RGBA", (len(TIERS) * (64 * 4 + 10), 2 * (32 * 4 + 10)), (60, 60, 70, 255))
    for i, t in enumerate(TIERS):
        for j, sub in enumerate(("humanoid", "humanoid_leggings")):
            im = Image.open(os.path.join(tex2, sub, SET[t] + ".png")).convert("RGBA").resize((256, 128), Image.NEAREST)
            lay.paste(im, (i * 266, j * 138), im)
    lay.save(os.path.join(dirpath, "gear_layers.png"))
    sil = Image.new("RGBA", (len(LATE) * (128 * 4 + 10), 64 * 4), (60, 60, 70, 255))
    for i, t in enumerate(LATE):
        im = Image.open(os.path.join(tex2, "silhouette", SET[t] + ".png")).convert("RGBA").resize((512, 256), Image.NEAREST)
        sil.paste(im, (i * 522, 0), im)
    sil.save(os.path.join(dirpath, "gear_silhouette_textures.png"))
    print("preview in", dirpath)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    elif len(sys.argv) > 1 and sys.argv[1] == "preview":
        preview(sys.argv[2] if len(sys.argv) > 2 else "/tmp/gear_preview")
    else:
        print(__doc__)
