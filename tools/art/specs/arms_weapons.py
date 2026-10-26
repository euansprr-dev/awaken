"""Arms' weapons (SPEC §25 "Weapons"; docs/combat/PLAN.md §4.5, §4.10; the look: docs/combat/weapon-style.md): built of
designed boxes at the player's own texel density, painted, drawn by our shader.

    python3 tools/art/build_all.py arms_weapons        every file below
    python3 tools/art/specs/arms_weapons.py build      the same, after the checks
    python3 tools/art/specs/arms_weapons.py check      the checks only (each weapon built, nothing written)
    python3 tools/art/specs/arms_weapons.py preview DIR [id]   turntables and a close-up per weapon (mesh_render.py),
                                                               and the review sheet DIR/weapons.png
    python3 tools/art/specs/arms_weapons.py sheet OUT.png      the contact sheet beside the owner's reference image

Each weapon is designed by hand here, one function per design, from the box toolkit in tools/art/voxel.py: bold parts
at whole-texel sizes where 1 texel is 1 item unit (a skin pixel), so a weapon sits in the hand at the player's own
density. A blade is drawn like pixel art in its plane, each texel a material and a depth, so its section steps (a
thick spine or ribs, thin edges) and its point and notches are whole texels; guards, grips and pommels are boxes, cut
stones are boxes turned 45 degrees and set in frames. Every face is painted in big flat areas from its material's
hand-picked ramp: a lit band on the edges that face the painted light, a dark band on those that face away, a contact
shadow where it meets another part, and a few deliberate details (a highlight streak, wrap bands, scales, a circuit,
runes, a stone's rings). No per-texel noise. The material map beside the albedo tells the shader
(assets/awaken/shaders/core/arms_weapon.fsh) what is metal, how glossy, what glows and in which order.

Per weapon this writes: arms/weapons/<id>.mesh (the game's mesh), textures/item/arms/<id>.png (albedo | material),
the inventory icon rendered from the mesh (<id>_icon.png), the item definition (the mesh in hand, the icon in the
inventory, on the ground, in frames), its recipe, lang, #minecraft:swords, and its row in the generated ArmsData.java.
Deterministic: nothing is random.
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
import voxel as VX  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
MERGE = os.path.join(ROOT, "src/main/merge/arms")
JAVA = os.path.join(ROOT, "src/main/java/dev/awaken/arms/ArmsData.java")
HAND = (3.0, 3.0, 8.0)  # the grip's centre in item units: where iron_sword.png's handle is, so vanilla's hands fit
ANGLE = -45.0           # the blade along the item's diagonal, tip to the upper right
SCALE = 1.0             # design units to item units: a texel is an item unit (1/16 block), the player's own density

# ---------------------------------------------------------------------------------------------------------------------
# The catalog: one line per weapon (damage over the player's 1; uses; vanilla rarity; enchantability; repair tag)

# The forged lines, one per fighting style of data/awaken/arms/archetypes.json: (name, damage over the tier's Longsword,
# the item's attack speed modifier, the vanilla item tag that gives it its enchantments, its recipe's shape). A recipe
# is always the tier's Hunter blade (X) with its core (C) and bind (B); each style its own shape, so no two share one.
STYLES = {
    "longsword": ("Longsword", 0.0, -2.4, "swords", ["C", "X", "B"]),
    "axe": ("Axe", 1.5, -3.0, "axes", ["XC", " B"]),
    "maul": ("Maul", 3.0, -3.3, "enchantable/mace", ["CXC", " B "]),
    "spear": ("Spear", 0.5, -2.9, "spears", ["  X", " C ", "B  "]),
    "greatblade": ("Greatblade", 2.0, -3.0, "swords", ["XX", "CB"]),
    "rapier": ("Rapier", 0.0, -2.2, "swords", ["X", "B", "C"]),
    "daggers": ("Daggers", -1.5, -1.8, "swords", ["XB", "C "]),
    "katana": ("Katana", 1.0, -2.5, "swords", ["X  ", " B ", "  C"]),
    "twinblades": ("Twinblades", 0.5, -2.0, "swords", ["XCX", " B "]),
    "glaive": ("Glaive", 1.0, -2.9, "swords", [" X", " C", "B "]),
    "scythe": ("Scythe", 1.5, -3.0, "swords", ["XX", " C", " B"]),
    "gauntlets": ("Gauntlets", -0.5, -1.6, "swords", ["BXB", " C "]),
    "chainblade": ("Chainblade", 0.0, -2.7, "swords", ["XB", " B", " C"]),
    "shieldblade": ("Shieldblade", 0.5, -2.6, "swords", ["C C", "CXC", " B "]),
    "warstaff": ("Warstaff", 0.0, -2.8, "swords", ["  C", " B ", "X  "]),
    "precursor": ("Precursor Arm", 1.0, -2.5, "swords", ["CX", "XB"]),
}
# The Hunter tiers: (tier, set, name, the Longsword's damage over the player's 1, uses, vanilla rarity, enchantability,
# repair tag); every style of a tier keeps its uses, rarity, enchantability and repair
TIERS = [
    ("E", "wildhide", "Wildhide", 4.5, 220, "COMMON", 15, "hunter_core_e"),
    ("D", "beastbound", "Beastbound", 5.5, 500, "UNCOMMON", 12, "hunter_bind_d"),
    ("C", "gatewrought", "Gatewrought", 6.5, 1400, "RARE", 12, "hunter_bind_c"),
    ("B", "veilwoven", "Veilwoven", 7.5, 1900, "EPIC", 15, "hunter_bind_b"),
    ("A", "lordscale", "Lordscale", 8.5, 2300, "EPIC", 15, "hunter_bind_a"),
    ("S", "sovereign", "Sovereign", 9.5, 2800, "EPIC", 18, "hunter_bind_s"),
]
# One row per weapon: (id, name, tier, damage, uses, rarity, enchantability, repair, style, speed); the Longswords first
CATALOG = [(f"{sid}_{style}", f"{sname} {STYLES[style][0]}", tier, dmg + STYLES[style][1], dur, rarity, ench, repair, style,
            STYLES[style][2])
           for style in STYLES for tier, sid, sname, dmg, dur, rarity, ench, repair in TIERS]

# The Lord-forged (SPEC §25, S7): one per Gate Lord, forged from what it was; they only drop (no recipe). Each is the
# forged weapon of its home tier and style plus a point of damage, at its speed, epic, with the home tier's uses,
# enchantability and repair; its trail and glow are the Lord's own colour. The ids are fixed: LordForged.java keys on
# them. (id, name, home tier, style, the Lord's colour)
LORD_FORGED = [
    ("bell_warden_maul", "The Bell Warden's Toll", "E", "maul", "#f2c25a"),
    ("mother_of_spores_scythe", "The Sporemother's Reaping", "E", "scythe", "#5fe0b0"),
    ("ember_tyrant_axe", "The Ember Tyrant's Cleft", "E", "axe", "#ff7a1e"),
    ("hive_regent_daggers", "The Hive Regent's Sting", "E", "daggers", "#ffb830"),
    ("root_saint_warstaff", "The Root Saint's Reliquary", "E", "warstaff", "#b8e070"),
    ("drowned_archivist_spear", "The Drowned Archivist's Index", "D", "spear", "#5fd0b8"),
    ("gearwright_colossus_gauntlets", "The Gearwright's Grip", "D", "gauntlets", "#ff9a30"),
    ("glass_serpent_rapier", "The Glass Serpent's Fang", "D", "rapier", "#8ae8e8"),
    ("lantern_broker_chainblade", "The Lantern Broker's Due", "D", "chainblade", "#ffd070"),
    ("abbot_of_frost_glaive", "The Frost Abbot's Hymn", "C", "glaive", "#9ad8ff"),
    ("mirror_knight_shieldblade", "The Mirror Knight's Reflection", "C", "shieldblade", "#e0f4ff"),
    ("unit_zero_precursor", "Unit Zero's Countdown", "C", "precursor", "#ff4a4a"),
    ("the_sleeper_katana", "The Sleeper's Last Dream", "C", "katana", "#ff7ab8"),
    ("sky_leviathan_twinblades", "The Sky Leviathan's Fins", "B", "twinblades", "#7ab0ff"),
    ("hollow_herald_greatblade", "The Hollow Herald's Horizon", "A", "greatblade", "#ffb070"),
]
LORD_COLOUR = {lid: colour for lid, _, _, _, colour in LORD_FORGED}
_FORGED = {(r[8], r[2]): r for r in CATALOG}
CATALOG += [(lid, name, tier, _FORGED[(style, tier)][3] + 1.0, _FORGED[(style, tier)][4], "EPIC", _FORGED[(style, tier)][6],
             _FORGED[(style, tier)][7], style, STYLES[style][2]) for lid, name, tier, style, _ in LORD_FORGED]

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


RAMP_NAMES = list(RAMPS)
RAMP_RGB = [ramp(n) for n in RAMP_NAMES]


def rid(name):
    return RAMP_NAMES.index(name)


# ---------------------------------------------------------------------------------------------------------------------
# The painted look (docs/combat/weapon-style.md): per face, flat areas of one ramp step; a band a texel wide along each
# outer edge, lit where the edge faces the painted light (the blade plane's upper left: up the blade and toward its
# back), dark where it faces away; a contact shadow where the face runs into another part; then the material's own
# few details. Everything is a whole ramp step, so nothing is noise.

LIGHT = np.array([-0.5, 0.85, 0.0]) / np.linalg.norm([-0.5, 0.85, 0.0])


def bands(f):
    """Per texel: its bevel (ramp steps: +2 a lit edge, +1 an edge across the light, -1/-2 edges in shade), whether it
    lies in a contact shadow, and which of its four sides are outer edges. A face one texel across in some direction
    takes no bevel that way (it is all edge: its tone comes from which way it faces). A corner takes the edge that
    faces the light most squarely."""
    s = f.dirs @ LIGHT
    step = np.where(s > 0.3, 2, np.where(s >= -0.3, 1, np.where(s >= -0.7, -1, -2)))
    edge = (f.code == VX.EDGE) | (f.code == VX.SEAM)
    for a, b in ((0, 1), (2, 3)):
        thin = edge[:, a] & edge[:, b]
        edge[thin, a] = False
        edge[thin, b] = False
    w = np.abs(s) + (step > 0) * 1e-3
    score = np.where(edge, w[None, :], -1.0)
    band = np.where(edge.any(axis=1), step[score.argmax(axis=1)], 0)
    contact = (f.code == VX.CONTACT).any(axis=1)
    return band, contact, edge


class Mat:
    """A material: its ramp and base step, metal, gloss, glow (material map), how deep its contact shadow falls and its
    details (functions of a Tex, run in order)."""

    def __init__(self, ramp_name, base, metal=0.0, gloss=0.3, emit=0.0, details=(), contact=3, bevel=True):
        self.ramp, self.base, self.metal, self.gloss, self.emit = ramp_name, base, metal, gloss, emit
        self.details, self.contact, self.bevel = details, contact, bevel


class Tex:
    """The visible texels of one face as its painter sees them: where each is (design space), its row and column in
    the design's grid, and what it is painted (ramp, step, metal, gloss, emissive, flow), all changeable."""

    def __init__(self, f, m, clean):
        k = len(f.P)
        self.f, self.box, self.m, self.k, self.clean = f, f.box, m, k, clean
        self.P, self.n = f.P, f.n
        self.x, self.y, self.z = f.P[:, 0], f.P[:, 1], f.P[:, 2]
        self.Y = np.floor(self.y + 1e-6).astype(int)
        self.X = np.floor(self.x + 0.5 + 1e-6).astype(int)
        self.flat = abs(f.n[2]) > 0.9                    # a face of the blade's plane, front or back
        self.ramp = np.full(k, rid(m.ramp))
        self.tone = np.full(k, m.base)
        self.metal = np.full(k, float(m.metal))
        self.gloss = np.full(k, float(m.gloss))
        self.emit = np.full(k, float(m.emit))
        self.flow = np.zeros(k)
        self.data = f.box.data

    def top(self, name=None):
        return len(RAMPS[name or self.m.ramp]) - 1

    def put(self, sel, ramp_name=None, tone=None, metal=None, gloss=None, emit=None, flow=None):
        if ramp_name is not None:
            self.ramp[sel] = rid(ramp_name)
        for arr, v in ((self.tone, tone), (self.metal, metal), (self.gloss, gloss), (self.emit, emit), (self.flow, flow)):
            if v is not None:
                arr[sel] = v[sel] if isinstance(v, np.ndarray) and v.shape == (self.k,) else v


def colours(t):
    col = np.zeros((t.k, 3))
    for r in np.unique(t.ramp):
        sel = t.ramp == r
        col[sel] = RAMP_RGB[r][np.clip(t.tone[sel], 0, len(RAMP_RGB[r]) - 1)]
    return col


def painter(clean=False):
    def paint(f):
        m = MATS[f.box.mat]
        t = Tex(f, m, clean)
        s = float(f.n @ LIGHT)
        t.tone += int(s > 0.4) - int(s < -0.4)           # a face turned to the light a step up, away a step down
        band, contact, edge = bands(f)
        t.band, t.contact, t.edge = band, contact, edge
        if m.bevel:
            t.tone += np.clip(band, -1, 1) if abs(f.n[1]) > 0.9 else band
        t.tone[contact] = m.base - m.contact
        for d in m.details:
            d(t)
        alb = np.concatenate([colours(t), np.ones((t.k, 1))], axis=1)
        mat = np.stack([t.metal, t.gloss, t.emit, t.flow], axis=1)
        return (np.clip(alb, 0, 1) * 255).round().astype(np.uint8), (np.clip(mat, 0, 1) * 255).round().astype(np.uint8)
    return paint


# ---- details: each a deliberate mark of its material, a few per part --------------------------------------------------

def streak(period=7, run=4, phase=0):
    """Steel's highlight: the lit edge of a flat breaks into a run of its lightest step, then rests (a glint, not a
    stripe)."""
    def d(t):
        on = t.flat & (t.band >= 2) & (((t.Y + phase) % period) < run)
        t.put(on, tone=t.top())
    return d


def wrap(ramp_name, tone, period=3, width=1, twist=1, caps=False):
    """A strap or wire wound round a grip: bands `width` texels wide every `period` rows, straight round it (twist 0)
    or climbing (twist 1: each face's texels counted round the box's perimeter, so a band runs on unbroken from one
    face onto the next)."""
    def d(t):
        if abs(t.n[1]) > 0.9 and not caps:
            return
        c = t.box.c
        perim = int(round(4 * (t.box.h[0] + t.box.h[2])))
        a = np.arctan2(t.x - c[0], t.z - c[2])
        pa = np.round(a / (2 * math.pi) * perim).astype(int) % perim
        on = ((t.Y + twist * pa) % period) < width
        t.put(on, ramp_name=ramp_name, tone=np.clip(tone + t.band, 0, len(RAMPS[ramp_name]) - 1))
        t.wrapped = on
    return d


def rows(every=2, step=-1, phase=0):
    """Ribs round a grip: every so many rows a step darker (a groove between ribs)."""
    def d(t):
        if abs(t.n[1]) > 0.9:
            return
        on = ((t.Y + phase) % every) == 0
        t.tone[on] += step
    return d


def fur():
    """Pelt: strands laid down the grip, alternate columns a step apart, the tips light every third row (fur's one
    pattern)."""
    def d(t):
        if abs(t.n[1]) > 0.9:
            return
        c = t.box.c
        perim = int(round(4 * (t.box.h[0] + t.box.h[2])))
        a = np.arctan2(t.x - c[0], t.z - c[2])
        pa = np.round(a / (2 * math.pi) * perim).astype(int) % perim
        t.tone += np.where(pa % 2 == 0, 1, -1)
        tips = ((t.Y + pa) % 3 == 0) & (pa % 2 == 0)
        t.tone[tips] += 1
    return d


def gem(core_lift=0, rim=1, glow=1.0):
    """A cut stone face-on: rings stepping darker from its heart to a dark rim, the heart's upper-left texel the
    lightest (its sparkle); the facets on its sides darker still. Glows of itself, the heart most. Its colours sit a
    few steps under the ramp's top: the shader adds the glow on top, and a light albedo would glow white, not red."""
    def d(t):
        top = t.top()
        if not t.flat:
            t.tone[:] = np.clip(top - 4 + t.band, 0, top)
            t.emit[:] = t.m.emit * 0.5 * glow
            return
        f = t.f
        dc = np.maximum(np.abs(f.i + 0.5 - f.su / 2), np.abs(f.j + 0.5 - f.sv / 2))
        ring = np.maximum(np.ceil(dc - 0.5 - 1e-6), 0).astype(int)
        t.tone[:] = np.clip(top - 2 + core_lift - rim * ring, 0, top)
        heart = ring == 0
        if heart.any():
            idx = np.flatnonzero(heart)
            spark = idx[np.argmax(t.P[idx] @ LIGHT)]
            t.tone[spark] = top - 1 + core_lift
        t.emit[:] = t.m.emit * glow * np.clip(1.0 - 0.3 * ring, 0.3, 1.0)
    return d


def flow_up(y0, y1, lo=0.02):
    """What glows pulses in order up the weapon: its flow is how far up it lies (the shader's pulse runs along it).
    A box's own span (data) overrides y0..y1, so one material serves weapons of any length."""
    def d(t):
        a, b = t.data.get("span", (y0, y1))
        t.flow[:] = np.where(t.emit > 0, np.clip((t.y - a) / (b - a), lo, 1.0), 0.0)
    return d


def knap():
    """Knapped flint: each flake struck off a facet of its own step, three rows high and slanting up from the edge
    toward the spine (chevrons), the two sides a texel out of step, as flakes struck from each edge meet."""
    pattern = (1, -1, 0, 1, -1, 0)

    def d(t):
        if not t.flat:
            return
        flake = (t.Y + np.abs(t.X) + (t.X > 0)) // 3
        t.tone += np.array(pattern)[flake % len(pattern)]
    return d


def growth(every=4):
    """Tusk: a groove two steps darker down the flat, a texel in from the back (the line it grew along), and a band a
    step darker across it every few rows (the rings it grew in)."""
    def d(t):
        if t.flat:
            groove = t.X == t.data.get("groove", -1)
            t.tone[groove] -= 2
            t.tone[~groove & (t.Y % every == 0)] -= 1
    return d


def scales():
    """Black scale in rows, three texels a scale, each rimmed a light step along its lower edge where the light finds
    it; the rows offset side to side, as scales lie."""
    def d(t):
        if not t.flat:
            return
        rim = ((t.Y + (t.X % 2)) % 3) == 0
        t.tone[rim] += 2
        t.metal[rim] = 0.8
    return d


def sheen(period=9):
    """Veil-steel's oil sheen: a diagonal band of a lighter step sliding down the flats, its trailing side dithered
    (the material's one pattern)."""
    def d(t):
        if not t.flat:
            return
        b = (t.Y - 2 * t.X) % period
        t.tone[b < 2] += 2
        dither = (b == 2) & (((t.X + t.Y) % 2) == 0)
        t.tone[dither] += 1
    return d


CHANNEL_RUNES = ("#.#", "##.#", "#..#", "###", "#.##", "##", "#.#.#")


def runes(y0, y1, column=0, ramp_name="crimson"):
    """The Court's runes burning in the crimson channel down the blade's middle column: the channel a dark crimson,
    glyphs a texel wide in it (lit and dark texels in a row) with a dark texel between, each glyph lighting a beat
    after the one below it."""
    col, glyph = [], []
    k = 0
    while len(col) < 64:
        g = CHANNEL_RUNES[k % len(CHANNEL_RUNES)]
        col += [ch == "#" for ch in g] + [False]
        glyph += [k] * (len(g) + 1)
        k += 1
    n = max(glyph) + 1

    def d(t):
        if not t.flat:
            return
        a, b = sorted(t.data.get("span", (y0, y1)))
        a, b = int(math.floor(a)), int(math.ceil(b))
        chan = (t.X == t.data.get("column", column)) & (t.Y >= a) & (t.Y < b)
        t.put(chan, ramp_name=ramp_name, tone=2, metal=0.2, gloss=0.55)
        r = t.Y - a
        lit = chan & np.array([col[v] if 0 <= v < len(col) else False for v in r])
        t.put(lit, tone=t.top(ramp_name) - 2, emit=1.0, gloss=0.3)
        phase = np.array([(glyph[v] + 1) / n if 0 <= v < len(glyph) else 0.0 for v in r])
        t.flow[lit] = np.clip(phase[lit], 0.02, 1.0)
    return d


def circuit(y0, y1, every=4):
    """The precursors' signal circuit inlaid down the rib: a trace lit along it, a node every few texels, light climbing
    it in pulses."""
    def d(t):
        if not t.flat:
            return
        a0, b0 = t.data.get("span", (y0, y1))
        a, b = sorted((a0, b0))
        if "row" in t.data:  # run across a head instead: along x on one row, out from the haft
            ok = (t.Y == t.data["row"]) & (np.abs(t.x) >= a) & (np.abs(t.x) < b)
            pos = np.abs(t.x)
            a0, b0 = a, b
        else:
            ok = (t.Y >= a) & (t.Y < b)
            if "column" in t.data:
                ok &= t.X == t.data["column"]
            pos = t.y
        node = ok & ((np.floor(pos + 1e-6).astype(int) - int(a)) % every == 0)
        t.put(ok, ramp_name="signal", tone=5, emit=0.6, gloss=0.4, metal=0.0)
        t.put(node, tone=7, emit=1.0)
        t.flow[ok] = np.clip((pos[ok] - a0) / (b0 - a0), 0.02, 1.0)
    return d


def branches(y0, y1, every=4):
    """The circuit's branches: at each node a lit texel on the flat either side of the rib."""
    def d(t):
        if not t.flat:
            return
        a0, b0 = t.data.get("span", (y0, y1))
        a, b = sorted((a0, b0))
        c = t.data.get("column", 0)
        on = (t.Y >= a) & (t.Y < b) & ((t.Y - a) % every == 0) & (np.abs(t.X - c) == t.data.get("branch", 1))
        t.put(on, ramp_name="signal", tone=6, emit=0.85, gloss=0.4, metal=0.0)
        t.flow[on] = np.clip((t.y[on] - a0) / (b0 - a0), 0.02, 1.0)
    return d


def panel(ramp_name, step, margin=1, metal=None, gloss=None, faces="flat"):
    """A face as a field inside a border: the texels at least `margin` in from its edges in another material (the
    reference's trimmed caps: a band of one metal round a field of another). faces: "flat" (front and back), "all"."""
    def d(t):
        if faces == "flat" and not t.flat:
            return
        f = t.f
        inner = (f.i >= margin) & (f.i < f.su - margin) & (f.j >= margin) & (f.j < f.sv - margin) & ~t.contact
        t.put(inner, ramp_name=ramp_name, tone=np.clip(step + np.clip(t.band, -1, 1), 0, len(RAMPS[ramp_name]) - 1),
              metal=metal, gloss=gloss)
    return d


def strip(y0, y1, inner=True, ramp_name="signal"):
    """A lit slot down a pylon's face: its inner column glowing, pulsing up."""
    def d(t):
        if not t.flat:
            return
        c = t.box.c[0]
        col = np.floor(c - np.sign(c) * 0.5 + 0.5 + 1e-6) if inner else np.floor(c + np.sign(c) * 0.5 + 0.5)
        col = t.data.get("column", int(col))
        a, b = sorted(t.data.get("span", (y0, y1)))
        on = (t.X == int(col)) & (t.Y >= a) & (t.Y < b)
        t.put(on, ramp_name=ramp_name, tone=6, emit=0.9, metal=0.0, gloss=0.4)
        t.flow[on] = np.clip((t.y[on] - a) / max(b - a, 1) * 0.5 + 0.1, 0.02, 1.0)
    return d



def edges_of(left=None, right=None):
    """An edge slab's outer columns, each its own material: (ramp, step, metal, gloss, emissive) or None to leave it;
    side faces follow the column they bound."""
    def d(t):
        rel = t.x - t.box.c[0]
        half = t.box.h[0]
        L = rel < -half + 1
        R = rel > half - 1
        for sel, spec in ((L, left), (R, right)):
            if spec is None:
                continue
            name, step, metal, gloss, emit = spec
            band = t.band if emit == 0 else 0
            t.put(sel, ramp_name=name, tone=np.clip(step + band, 0, len(RAMPS[name]) - 1), metal=metal, gloss=gloss, emit=emit)
    return d


def ridge(column=0, ramp_name="gold", step=5, metal=0.8, gloss=0.85):
    """A ridge of another metal down a face's column (Lordscale's gold line), lit and shaded with the face."""
    def d(t):
        if not t.flat:
            return
        on = t.X == column
        t.put(on, ramp_name=ramp_name, tone=np.clip(step + t.band, 0, len(RAMPS[ramp_name]) - 1), metal=metal, gloss=gloss)
    return d


def tips(ramp_name="gold", step=5, run=2):
    """Feathers tipped in gold: the last texels of a slat, by its length."""
    def d(t):
        base = np.asarray(t.data.get("base", (0, 0)), dtype=float)
        length = t.data.get("length", 0)
        r = np.linalg.norm(t.P[:, :2] - base, axis=1)
        on = r > length - run
        t.put(on, ramp_name=ramp_name, tone=np.clip(step + t.band, 0, len(RAMPS[ramp_name]) - 1), metal=0.9, gloss=0.85)
    return d


def studs(every=3, phase=1):
    """Rivets down a cap: every few rows a texel of the lightest step with a dark one under it (a stud and its
    shadow)."""
    def d(t):
        if not t.flat:
            return
        on = ((t.Y + phase) % every) == 0
        t.put(on, tone=t.top())
        t.put(((t.Y + phase + 1) % every) == 0, tone=t.m.base - 2)
    return d


def katana(edge, hamon, edge_emit=0.0, hamon_emit=0.0):
    """A curved single-edged blade link, painted in its own turned frame: the spine a step dark, the hardened edge
    bright in its own material, and between them the hamon, the temper line: an unbroken line of the tier's accent
    along the edge's side, its tone rising and falling every two rows (its wave). What glows pulses up the blade."""
    def d(t):
        if not t.flat:
            return
        L = (t.P - t.box.c) @ t.box.R
        w = int(round(2 * t.box.h[0]))
        col = np.clip(np.floor(L[:, 0] + t.box.h[0] + 1e-6).astype(int), 0, w - 1)
        if t.data.get("flip"):
            col = w - 1 - col
        row = np.floor(L[:, 1] + t.box.h[1] + 1e-6).astype(int)
        t.tone[col == 0] -= 1
        e_ = col == w - 1
        t.put(e_, ramp_name=edge[0], tone=np.clip(edge[1] + t.band, 0, len(RAMPS[edge[0]]) - 1), emit=edge_emit, metal=0.7, gloss=0.95)
        if w >= 3:
            hm = col == w - 2
            t.put(hm, ramp_name=hamon[0], tone=np.where((row % 4) < 2, hamon[1], hamon[1] - 1), emit=hamon_emit, metal=0.2, gloss=0.8)
        a, b = t.data.get("span", (0, 30))
        t.flow[:] = np.where(t.emit > 0, np.clip((t.y - a) / (b - a), 0.02, 1.0), 0.0)
    return d


def ito(wrap_ramp, wrap_tone, skin_ramp, skin_tone):
    """A tsuka's binding: the wrap crossing round the handle, a diamond of the skin beneath showing between its
    crossings every other row, in the middle of each face."""
    def d(t):
        if abs(t.n[1]) > 0.9:
            return
        c = t.box.c
        h = np.where(abs(t.n[2]) > 0.9, t.x - c[0], t.z - c[2])
        skin = (np.abs(h) < 0.5) & (t.Y % 2 == 0)
        t.put(~skin, ramp_name=wrap_ramp, tone=np.clip(wrap_tone + t.band, 0, len(RAMPS[wrap_ramp]) - 1), metal=0.0, gloss=0.3)
        t.put(skin, ramp_name=skin_ramp, tone=skin_tone, metal=0.3, gloss=0.6)
    return d


def spiral(every=3, step=-2):
    """A narwhal's tusk: its spiral climbing round it, a groove a texel wide (on every face, so it winds on unbroken)."""
    def d(t):
        h = np.where(abs(t.n[2]) > 0.9, t.x - t.box.c[0], np.where(abs(t.n[0]) > 0.9, -(t.z - t.box.c[2]), 0))
        on = ((t.Y + np.round(h).astype(int) + (t.n[2] < -0.9) * 2 + (t.n[0] > 0.9) * 1) % every) == 0
        t.tone[on] += step
    return d


def staged(stages=3):
    """Hard light that grows with the chain: each stage of the blade its own pulse, the root first (data: stage); its
    middle column the white of the light, its sides the colour."""
    def d(t):
        k = t.data.get("stage", 0)
        t.flow[:] = np.where(t.emit > 0, (k + 1) / (stages + 1), 0.0)
        if t.flat:
            mid = np.abs(t.x - t.box.c[0]) < 0.5
            t.tone[mid] = t.top() - 1
    return d


MATS = {
    # E: Wildhide
    "flint": Mat("flint", 2, 0.1, 0.7, details=(knap(),)),
    "flint_edge": Mat("flint", 2, 0.1, 0.7, details=(edges_of(("flint", 7, 0.1, 0.8, 0.0), ("flint", 6, 0.1, 0.8, 0.0)),)),
    "bone": Mat("bone", 4, 0.0, 0.35),
    "antler": Mat("bone", 5, 0.0, 0.4),
    "sinew": Mat("hide", 3, 0.0, 0.3, details=(rows(2, -2),)),
    "bone_grip": Mat("bone", 4, 0.0, 0.35, details=(wrap("hide", 3, period=3, width=2, twist=0),)),
    "hide": Mat("hide", 3, 0.0, 0.3),
    "amber_gem": Mat("amber", 4, 0.2, 0.9, emit=0.5, details=(gem(),), contact=2),
    # D: Beastbound
    "tusk": Mat("bone", 5, 0.0, 0.55, details=(growth(),)),
    "tusk_edge": Mat("bone", 6, 0.0, 0.6),
    "horn": Mat("bone", 5, 0.0, 0.45),
    "horn_tip": Mat("bone", 3, 0.0, 0.45),
    "tooth": Mat("bone", 6, 0.0, 0.5),
    "bronze": Mat("bronze", 5, 0.3, 0.75),
    "bronze_back": Mat("bronze", 5, 0.3, 0.75, details=(studs(),)),
    "pelt": Mat("pelt", 4, 0.0, 0.15, details=(fur(),)),
    "pelt_wrap": Mat("pelt", 4, 0.0, 0.15, details=(rows(2, -2),)),
    "moss_gem": Mat("moss", 5, 0.2, 0.9, emit=0.45, details=(gem(),), contact=2),
    # C: Gatewrought
    "slate_rib": Mat("slate", 4, 0.5, 0.8, details=(circuit(5, 20),)),
    "slate_flat": Mat("slate", 4, 0.5, 0.8, details=(edges_of(("mirror", 6, 0.7, 0.95, 0.0), ("mirror", 5, 0.7, 0.95, 0.0)),
                                                      branches(5, 20))),
    "slate_bit": Mat("slate", 3, 0.5, 0.8, details=(circuit(3, 10),)),
    "mirror_edge": Mat("mirror", 6, 0.7, 0.95),
    "slate": Mat("slate", 3, 0.5, 0.8, details=(streak(),)),
    "light_amber": Mat("amber", 5, 0.0, 0.5, emit=0.9, details=(staged(),), bevel=False),
    "light_moss": Mat("moss", 5, 0.0, 0.5, emit=0.9, details=(staged(),), bevel=False),
    "light_signal": Mat("signal", 5, 0.0, 0.5, emit=0.95, details=(staged(),), bevel=False),
    "light_spirit": Mat("spirit", 5, 0.0, 0.5, emit=0.9, details=(staged(),), bevel=False),
    "light_ember": Mat("ember", 5, 0.0, 0.5, emit=0.95, details=(staged(),), bevel=False),
    "light_crimson": Mat("crimson", 5, 0.0, 0.5, emit=0.95, details=(staged(),), bevel=False),
    "scythe_b": Mat("veil", 3, 0.6, 0.9, details=(katana(("spirit", 6), ("mirror", 5), edge_emit=0.75),)),
    "scythe_s": Mat("ivory", 6, 0.1, 0.8, details=(katana(("crimson", 5), ("gold", 6), edge_emit=0.9),)),
    "hide_face": Mat("hide", 4, 0.0, 0.3, details=(rows(3, -1),)),
    "lord_face": Mat("lord", 3, 0.5, 0.85, details=(scales(),)),
    "narwhal": Mat("bone", 6, 0.0, 0.6, details=(spiral(),)),
    "flint_kat": Mat("flint", 2, 0.1, 0.75, details=(katana(("flint", 7), ("bone", 6)),)),
    "tusk_kat": Mat("bone", 5, 0.0, 0.6, details=(katana(("bone", 7), ("bronze", 5)),)),
    "slate_kat": Mat("slate", 3, 0.5, 0.85, details=(katana(("mirror", 6), ("signal", 6), hamon_emit=0.8),)),
    "veil_kat": Mat("veil", 3, 0.6, 0.9, details=(katana(("mirror", 6), ("spirit", 6), hamon_emit=0.75),)),
    "lord_kat": Mat("lord", 3, 0.5, 0.9, details=(katana(("ember", 6), ("gold", 6), edge_emit=0.8),)),
    "ivory_kat": Mat("ivory", 6, 0.1, 0.8, details=(katana(("gold", 6), ("crimson", 5), hamon_emit=0.9),)),
    "ito_e": Mat("hide", 3, 0.0, 0.3, details=(ito("hide", 2, "bone", 6),)),
    "ito_d": Mat("pelt", 3, 0.0, 0.2, details=(ito("hide", 3, "bone", 5),)),
    "ito_c": Mat("slate", 2, 0.3, 0.5, details=(ito("slate", 2, "brass", 5),)),
    "ito_b": Mat("obsidian", 2, 0.3, 0.6, details=(ito("obsidian", 2, "mirror", 5),)),
    "ito_a": Mat("lord", 2, 0.3, 0.6, details=(ito("lord", 2, "gold", 5),)),
    "ito_s": Mat("weave", 3, 0.0, 0.3, details=(ito("weave", 2, "gold", 5),)),
    "signal_line": Mat("signal", 5, 0.0, 0.4, emit=0.7, details=(flow_up(4, 23),), bevel=False),
    "slate_head": Mat("slate", 2, 0.5, 0.8, details=(panel("slate", 3, margin=1),)),
    "cap_slot": Mat("brass", 5, 0.4, 0.8, details=(panel("brass", 3, margin=1), strip(20, 27))),
    "brass": Mat("brass", 5, 0.4, 0.8),
    "pylon": Mat("brass", 5, 0.4, 0.8, details=(strip(5, 9),)),
    "slate_grip": Mat("slate", 3, 0.3, 0.5, details=(rows(2, -2),)),
    "signal_gem": Mat("signal", 5, 0.3, 1.0, emit=0.55, details=(gem(),), contact=2),
    # B: Veilwoven
    "veil": Mat("veil", 3, 0.6, 0.85, details=(sheen(),)),
    "veil_edge": Mat("veil", 3, 0.6, 0.85, details=(edges_of(("spirit", 6, 0.0, 0.5, 0.62), ("mirror", 5, 1.0, 0.95, 0.0)),
                                                    flow_up(4, 23))),
    "mirror": Mat("mirror", 5, 0.7, 0.95),
    "obsidian": Mat("obsidian", 3, 0.4, 0.8),
    "spirit_gem": Mat("spirit", 5, 0.3, 1.0, emit=0.55, details=(gem(),), contact=2),
    "spirit_edge": Mat("spirit", 6, 0.0, 0.5, emit=0.62, details=(flow_up(4, 23),), bevel=False),
    # A: Lordscale
    "lord": Mat("lord", 3, 0.5, 0.85, details=(scales(), ridge())),
    "lord_dark": Mat("lord", 2, 0.5, 0.85),
    "feather": Mat("lord", 3, 0.5, 0.85, details=(scales(), tips())),
    "ember_edge": Mat("ember", 5, 0.0, 0.6, emit=0.8, details=(flow_up(4, 23),), bevel=False),
    "gold": Mat("gold", 5, 0.4, 0.85),
    "gold_cap": Mat("gold", 5, 0.4, 0.85, details=(panel("lord", 3, margin=1, metal=0.5, gloss=0.85),)),
    "lord_grip": Mat("lord", 3, 0.5, 0.8, details=(scales(),)),
    "lord_plain": Mat("lord", 3, 0.5, 0.85, details=(scales(),)),
    "pearl_gem": Mat("pearl", 3, 0.3, 1.0, emit=0.4, details=(gem(),), contact=2),
    # S: Sovereign
    "ivory": Mat("ivory", 6, 0.1, 0.75, details=(streak(period=9, run=5, phase=2), runes(5, 20))),
    "ivory_tip": Mat("ivory", 6, 0.1, 0.75),
    "ivory_twin": Mat("ivory", 6, 0.1, 0.75, details=(runes(20, 27, column=-3), runes(20, 27, column=3))),
    "gold_trim": Mat("gold", 5, 0.4, 0.85, details=(panel("crimson", 3, margin=1, metal=0.2, gloss=0.6),)),
    "gold_edge": Mat("gold", 5, 0.45, 0.9),
    "weave": Mat("weave", 3, 0.0, 0.25),
    "weave_grip": Mat("weave", 3, 0.0, 0.25, details=(wrap("gold", 5, period=3, width=1, twist=0),)),
    "crimson_gem": Mat("crimson", 4, 0.3, 1.0, emit=0.5, details=(gem(),), contact=2),
}


# ---------------------------------------------------------------------------------------------------------------------
# Construction helpers (design space: texels; x across the blade, y up it from the grip's middle, z through its flat)

def blade(segs, core, edge, core_d=3, edge_d=1, part="blade", shift=lambda y: 0, x=0.0, **data):
    """A blade from the guard up, a segment at a time (y0, y1, width, core width): a thin edge slab the full width and
    a thick core over its middle, so the section steps from edge to spine in whole texels; segments narrow to the
    point. shift(y0) moves a segment sideways (a wave); data (a span, a column) goes to every box for its painter."""
    B = []
    for y0, y1, w, cw in segs:
        cx = x + shift(y0)
        if w > cw:
            B.append(VX.slab(cx - w / 2, cx + w / 2, y0, y1, edge_d, edge, part, **data))
        if cw:
            B.append(VX.slab(cx - cw / 2, cx + cw / 2, y0, y1, core_d, core, part, **data))
    return B


def chain(x, y, links, part, side=-1, back=None, stagger=0.0, **data):
    """Bars end to end from (x, y), each turned a whole angle: links (degrees from straight up, outward positive;
    length, width, depth, material). side -1 builds the left one (mirror() makes the right). Each starts `back` inside
    the last (half its width by default), so the joints stay solid. stagger lifts every other link that far off the
    blade's plane (0.05: unseen), so links of one depth that overlap at a joint never share a plane (a curved blade)."""
    out = []
    p = np.array([x, y], dtype=float)
    for k, (deg, length, width, depth, mat) in enumerate(links):
        th = math.radians(deg * side)
        d = np.array([math.sin(th), math.cos(th)])
        if k:
            p = p - d * (min(width, length) / 2 if back is None else back)
        c = p + d * length / 2
        out.append(VX.rbox((c[0], c[1], stagger * (k % 2)), (width, length, depth), -deg * side, mat, part, base=tuple(p),
                           length=length, **data))
        p = p + d * length
    return out


def spin(boxes):
    """The same boxes turned half round the blade's normal (x and y both flip): the far blade of a twinblade, point for
    point, its edge on the other side. Spans flip so what glows still runs outward from the grip."""
    R180 = VX.rotz(180)
    out = []
    for b in boxes:
        data = dict(b.data)
        if "span" in data:
            data["span"] = tuple(-v for v in data["span"])
        if "column" in data:
            data["column"] = -data["column"]
        if "row" in data:
            data["row"] = -data["row"] - 1
        if "base" in data:
            data["base"] = tuple(-v for v in data["base"])
        out.append(VX.Box(R180 @ b.c, b.h, b.mat, b.part + "~", R180 @ b.R, b.name + "~", **data))
    return out


def turned(boxes, deg, about):
    """Boxes turned deg about z through a point (a blade set at the end of a chain at the chain's own angle)."""
    Rz = VX.rotz(deg)
    o = np.asarray(about, dtype=float)
    return [VX.Box(o + Rz @ (b.c - o), b.h, b.mat, b.part, Rz @ b.R, b.name, **b.data) for b in boxes]


def edge_points(boxes, side=1):
    """Points along one side (local +x, or -x) of a curved blade's links, for its stream."""
    return [tuple(b.c + b.R[:, 0] * b.h[0] * side) for b in boxes]


def fan(x, y, slats, part, side=-1):
    """Slats radiating from one pivot (a wing's feathers): (degrees from straight up, length, width, depth, material)."""
    out = []
    for deg, length, width, depth, mat in slats:
        th = math.radians(deg * side)
        d = np.array([math.sin(th), math.cos(th)])
        c = np.array([x, y]) + d * length / 2
        out.append(VX.rbox((c[0], c[1], 0.0), (width, length, depth), -deg * side, mat, part, base=(x, y), length=length))
    return out


def both(boxes):
    """A part and its mirror across the blade."""
    return boxes + VX.mirror(boxes)


def frame(centre, outer_, inner, depth, deg, mat, part):
    """A square frame turned deg about z (45: a diamond): four bars round a hole inner texels across, the top and
    bottom bars the full width, the sides between them (no two overlap, so their faces never fight)."""
    w = (outer_ - inner) / 2
    R = VX.rotz(deg)
    c = np.asarray(centre, dtype=float)
    bars = [((0, (inner + w) / 2), (outer_, w)), ((0, -(inner + w) / 2), (outer_, w)),
            ((-(inner + w) / 2, 0), (w, inner)), (((inner + w) / 2, 0), (w, inner))]
    return [VX.Box(c + R @ np.array([lx, ly, 0.0]), np.array([sx, sy, depth]) / 2, mat, part, R, f"{part}:bar")
            for (lx, ly), (sx, sy) in bars]


def grip(y0, y1, mat, width=3, depth=3):
    return VX.slab(-width / 2, width / 2, y0, y1, depth, mat, "grip")


def ring(y0, y1, mat, width=5, depth=5, part=None):
    return VX.slab(-width / 2, width / 2, y0, y1, depth, mat, part or f"ring{y0}")


# ---------------------------------------------------------------------------------------------------------------------
# Designs: (boxes, anchors (centre, radius, colour, kind), look); all in design space. The grip runs y -1..3 round
# the hand, the guard sits on it at y 3, the blade rises from there to about y 22, the pommel hangs to about y -6:
# about 28 texels, a fifth longer than vanilla's sword (22.6 along its diagonal), the blade two thirds of it.

# ---- E: Wildhide ----------------------------------------------------------------------------------------------------

def wildhide():
    """The Wildhide Longsword (E): the first blade a hunter makes. A leaf of knapped flint, every flake struck off it a
    facet of its own grey, its edges thin enough to glow pale; lashed with sinew between two antler tines that fork up
    beside it off a bone collar; a bone grip wound in a hide strap; an amber bead for a pommel, warm with its own light,
    and a tooth hung from it on a thong."""
    B = blade([(4, 8, 3, 1), (8, 18, 5, 3), (18, 20, 3, 1), (20, 22, 1, 1)], "flint", "flint_edge")
    B.append(VX.slab(-2.5, 2.5, 5, 7, 5, "sinew", "lashing"))
    B.append(VX.slab(-4.5, 4.5, 3, 5, 5, "bone", "collar"))
    B += both(chain(-3.5, 4.5, [(40, 5, 2, 3, "antler"), (12, 3, 1, 2, "antler")], "antler"))
    B += both(chain(-5.2, 6.4, [(-8, 3, 1, 2, "antler")], "tine"))
    B.append(grip(-1, 3, "bone_grip"))
    B.append(ring(-2, -1, "bone", part="cup"))
    B.append(VX.slab(-1.5, 1.5, -5, -2, 3, "amber_gem", "bead"))
    B.append(VX.slab(1.5, 2.5, -4, -2, 1, "hide", "thong"))
    B.append(VX.rbox((2.2, -4.9, 0), (1, 2, 2), -15, "antler", "tooth"))
    anchors = [((0, -3.5, 0), 2.2, RAMPS["amber"][5], 1)]
    return B, anchors, dict(accent=RAMPS["amber"][4], ramps=("flint", "bone", "hide", "amber"))


# ---- D: Beastbound ----------------------------------------------------------------------------------------------------

def beastbound():
    """The Beastbound Longsword (D): a trophy of the hunt. A broad cleaver of boar-tusk ivory, thick at its back and
    ground thin to the edge, its back capped in bronze and set with teeth, its point clipped up toward the back; two
    tusks for a guard, curling back to the hand from a riveted bronze collar; a grip wrapped in pelt between bronze
    rings; a bronze claw gripping a moss-green stone."""
    B = [VX.slab(-3.5, -2.5, 5, 18, 5, "bronze_back", "back"),
         VX.slab(-2.5, 0.5, 4, 19, 3, "tusk", "blade", groove=-1),
         VX.slab(-2.5, 2.5, 4, 18, 1, "tusk_edge", "blade"),
         VX.slab(-2.5, 1.5, 18, 19, 1, "tusk_edge", "blade"),
         VX.slab(-1.5, 0.5, 19, 20, 3, "tusk", "blade"),
         VX.slab(-1.5, -0.5, 20, 21, 3, "tusk", "blade")]
    for y in (7, 10.5, 14):  # teeth set in the back, leaning up the blade
        B.append(VX.rbox((-4.2, y + 0.6, 0), (1, 2, 1), 50, "tooth", "teeth"))
    B.append(VX.slab(-3.5, 3.5, 3, 5, 5, "bronze", "collar"))
    for x in (-2, 2):  # rivets through the collar, both faces
        for z0 in (2.5, -3.5):
            B.append(VX.box(x - 0.5, x + 0.5, 3.5, 4.5, z0, z0 + 1, "bronze", "rivets"))
    B += both(chain(-3.0, 4.2, [(70, 3, 2, 3, "horn"), (155, 3, 2, 2, "horn"), (215, 2, 1, 1, "horn_tip")], "tusks"))
    B.append(grip(-1, 3, "pelt"))
    B.append(ring(2, 3, "bronze", part="ring_top"))
    B.append(ring(-2, -1, "bronze", part="claw"))
    B.append(VX.slab(-1.5, 1.5, -5, -2, 3, "moss_gem", "stone"))
    for sx in (-1, 1):
        for sz in (-1, 1):  # the claw's four talons down the stone's corners, their points turned in under it
            B.append(VX.box(sx * 2 - 0.5, sx * 2 + 0.5, -5, -2, sz * 2 - 0.5, sz * 2 + 0.5, "bronze", "claw"))
            B.append(VX.box(sx - 0.5, sx + 0.5, -6, -5, sz - 0.5, sz + 0.5, "bronze", "claw"))
    anchors = [((0, -3.5, 0), 2.2, RAMPS["moss"][6], 1)]
    return B, anchors, dict(accent=RAMPS["moss"][5], ramps=("bone", "bronze", "pelt", "moss"))


# ---- C: Gatewrought ---------------------------------------------------------------------------------------------------

def gatewrought():
    """The Gatewrought Longsword (C): forged from a Gate's own metal by its precursors' rules. A long straight blade of
    slate-blue gate-steel ground to a raised rib, its edges mirror-honed, the signal circuit inlaid down the rib, light
    climbing it in pulses and branching onto the flats; the guard a gate in brass, two pylons the blade stands between,
    signal slots lit in their faces under heavy caps; a ribbed slate grip; a brass ring for a pommel with a signal
    crystal floating in it."""
    B = blade([(4, 20, 5, 1), (20, 21, 3, 1), (21, 22, 1, 1)], "slate_rib", "slate_flat")
    B.append(VX.slab(-7.5, 7.5, 3, 5, 5, "brass", "bar"))
    for s in (-1, 1):
        B.append(VX.slab(*sorted((s * 4.5, s * 6.5)), 5, 9, 5, "pylon", f"pylon{s}"))
        B.append(VX.slab(*sorted((s * 3.5, s * 7.5)), 9, 10, 7, "brass", f"cap{s}"))
    B.append(grip(-1, 3, "slate_grip"))
    B.append(ring(2, 3, "brass", part="ferrule"))
    B.append(ring(-1, 0, "brass", part="ferrule_low"))
    B += VX.sprite(["RRRRR", "R...R", "R...R", "R...R", "RRRRR"], -2, -1, {"R": ("brass", 3, "loop")})
    B.append(VX.rbox((0, -3.5, 0), (2, 2, 2), 45, "signal_gem", "crystal"))
    anchors = [((0, -3.5, 0), 2.0, RAMPS["signal"][6], 1)]
    return B, anchors, dict(accent=RAMPS["signal"][5], ramps=("slate", "brass", "signal", "mirror"))


# ---- B: Veilwoven -----------------------------------------------------------------------------------------------------

VEIL_WAVE = ((4, 0), (7, 1), (10, 0), (13, -1), (16, 0), (19, 1), (20, 0))  # the flame wave: where the blade steps a texel aside


def veil_shift(y):
    o = 0
    for y0, s in VEIL_WAVE:
        if y >= y0:
            o = s
    return o


def veilwoven():
    """The Veilwoven Longsword (B): steel that has been through the Veil and back. A flame-waved blade, near-black
    violet with a sheen that slides like oil down it; one edge is living spirit light, the other mirror-honed; quillons
    of mirror-silver swept up like wings off a mirror heart holding a spirit stone; an obsidian grip ringed in mirror; a
    teardrop of mirror holding a second stone."""
    segs = [(4, 7, 5, 3), (7, 10, 5, 3), (10, 13, 5, 3), (13, 16, 5, 3), (16, 19, 5, 3), (19, 20, 5, 3), (20, 22, 3, 1), (22, 23, 1, 1)]
    B = blade(segs, "veil", "veil_edge", shift=veil_shift)
    B.append(VX.slab(-2.5, 2.5, 3, 6, 5, "mirror", "heart"))
    B.append(VX.rbox((0, 4.5, 0), (3, 3, 7), 45, "spirit_gem", "heart_gem"))
    B += both(chain(-2.0, 4.5, [(75, 3, 2, 3, "mirror"), (45, 3, 2, 2, "mirror"), (18, 3, 1, 1, "mirror")], "quillons"))
    B.append(grip(-1, 3, "obsidian"))
    for y0 in (2, -1):
        B.append(ring(y0, y0 + 1, "mirror", part=f"band{y0}"))
    B.append(VX.rbox((0, -3.1, 0), (3, 3, 5), 45, "mirror", "drop"))
    B.append(VX.rbox((0, -3.1, 0), (2, 2, 7), 45, "spirit_gem", "drop_gem"))
    anchors = [((0, 4.5, 0), 2.4, RAMPS["spirit"][6], 1), ((0, -3.1, 0), 1.9, RAMPS["spirit"][6], 1)]
    # the spirit edge (the -x side) lets go of wisps that drift and curl away; the heart stone breathes motes
    edge = [(-2.5 + veil_shift(y), y + 0.5, 0.0) for y in range(6, 21, 2)]
    streams = [(WISP, RAMPS["spirit"][6], 5, 0.18, edge), (MOTE, RAMPS["spirit"][7], 2, 1.6, [(0, 4.5, 0)])]
    return B, anchors, dict(accent=RAMPS["spirit"][5], ramps=("veil", "mirror", "spirit", "obsidian"), streams=streams)


# ---- A: Lordscale -----------------------------------------------------------------------------------------------------

def lordscale():
    """The Lordscale Longsword (A): cut from a fallen Lord. A broad blade of black scale, each scale rimmed where the
    light finds it, a gold ridge down its middle, its edges molten gold that flows toward the point; a guard of two
    great wings spread up and out, a gold pinion over layered black feathers tipped in gold; the sky's pearl at their
    heart; a grip of black scale banded in gold; a gold claw holding a second pearl."""
    B = blade([(4, 20, 5, 3), (20, 22, 3, 1), (22, 23, 1, 1)], "lord", "ember_edge")
    B.append(VX.slab(-2.5, 2.5, 3, 6, 5, "lord_dark", "heart"))
    B += frame((0, 4.5, 0), 4, 2, 7, 45, "gold", "bezel")
    B.append(VX.rbox((0, 4.5, 0), (2, 2, 6), 45, "pearl_gem", "pearl"))
    B += both(fan(-2.0, 4.5, [(38, 9, 2, 4, "gold"), (56, 8, 2, 3, "feather"), (74, 7, 2, 2, "feather"), (94, 5, 2, 1, "feather")], "wing"))
    B.append(grip(-1, 3, "lord_grip"))
    for y0 in (2, -1):
        B.append(ring(y0, y0 + 1, "gold", part=f"band{y0}"))
    B.append(ring(-2, -1, "gold", part="claw"))
    B.append(VX.slab(-1.5, 1.5, -5, -2, 3, "pearl_gem", "pearl_low"))
    for sx, sz in ((-1, 0), (1, 0), (0, -1), (0, 1)):  # the claw's talons down the pearl's sides, curling under it
        B.append(VX.box(sx * 2 - 0.5, sx * 2 + 0.5, -5, -2, sz * 2 - 0.5, sz * 2 + 0.5, "gold", "claw"))
        B.append(VX.box(sx - 0.5, sx + 0.5, -6, -5, sz - 0.5, sz + 0.5, "gold", "claw"))
    anchors = [((0, 4.5, 0), 2.2, RAMPS["pearl"][4], 1), ((0, -3.5, 0), 2.2, RAMPS["pearl"][4], 1)]
    # the molten edges shed embers that rise and cool, more as the blade fills with light
    streams = [(EMBER, RAMPS["ember"][6], 7, 0.12, [(side * 2.5, y + 0.5, 0.0) for y in range(6, 21, 2)]) for side in (-1, 1)]
    return B, anchors, dict(accent=RAMPS["ember"][5], ramps=("lord", "gold", "ember", "pearl"), streams=streams)


# ---- S: Sovereign -----------------------------------------------------------------------------------------------------

def sovereign():
    """The Sovereign Longsword (S): the Court's oath made steel. A long ivory-white spire of a blade between gold edges,
    a crimson channel down its middle where the Court's runes burn in turn; a crown for a guard, its quillons sweeping
    up into points, a great cut crimson stone at its heart in a raised gold frame; a grip of void weave bound in gold
    wire; a gold star for a pommel round a second stone."""
    B = blade([(4, 20, 5, 3), (20, 22, 3, 1), (22, 23, 1, 1)], "ivory", "gold_edge")
    B.append(VX.slab(-5.5, 5.5, 3, 5, 5, "gold", "guard"))
    B += both(chain(-5.0, 4.0, [(42, 4, 2, 3, "gold"), (12, 3, 1, 2, "gold")], "quillons"))
    B += frame((0, 5.5, 0), 5, 3, 9, 45, "gold", "frame")
    B.append(VX.rbox((0, 5.5, 0), (3, 3, 7), 45, "crimson_gem", "heart"))
    B.append(grip(-1, 3, "weave_grip"))
    B.append(ring(2, 3, "gold", part="ferrule"))
    B.append(ring(-2, -1, "gold", part="ferrule_low"))
    B.append(VX.slab(-2, 2, -5.5, -1.5, 3, "gold", "star"))
    B.append(VX.rbox((0, -3.5, 0), (4, 4, 4), 45, "gold", "star_turned"))
    B.append(VX.rbox((0, -3.5, 0), (2, 2, 7), 0, "crimson_gem", "pommel_gem"))
    anchors = [((0, 5.5, 0), 2.8, RAMPS["crimson"][5], 1), ((0, -3.5, 0), 2.0, RAMPS["crimson"][5], 1)]
    # motes hang round the Court's stones and drift up off the burning runes of the channel
    streams = [(MOTE, RAMPS["crimson"][6], 4, 2.0, [(0, 5.5, 0)]), (MOTE, RAMPS["crimson"][6], 2, 1.6, [(0, -3.5, 0)]),
               (MOTE, RAMPS["gold"][6], 3, 0.25, [(0, 9.5, 0.0), (0, 19.5, 0.0)])]
    return B, anchors, dict(accent=RAMPS["crimson"][4], ramps=("ivory", "gold", "crimson", "weave"), streams=streams)


# ---- what lifts off a weapon (SPEC §25 "Weapons"): embers, wisps, motes, drawn by the effects engine ----------------
EMBER, WISP, MOTE = 1, 2, 3

HOLDS = {}  # each style's first-person hold (hold_of below)


def design_of(row):
    """A row's design: a Lord-forged weapon's own by its id, else its style's and tier's (None when there isn't one)."""
    return DESIGNS.get(row[0]) or DESIGNS.get((row[8], row[2]))
DESIGNS = {("longsword", "E"): wildhide, ("longsword", "D"): beastbound, ("longsword", "C"): gatewrought,
           ("longsword", "B"): veilwoven, ("longsword", "A"): lordscale, ("longsword", "S"): sovereign}
# the light of each tier's swings (a bright step of its own glow)
TRAILS = {"E": "#f1ebd6", "D": "#b9d890", "C": "#7fc3ff", "B": "#c3cbff", "A": "#f3c443", "S": "#ff9080"}

# =====================================================================================================================
# Batch 1: four more forged lines, each tier's materials and story in another fighting style. Design space as the
# Longswords': x across (the axe's bit toward -x, the item's upper left), y up from the hand, z through the flat.

def haft(y0, y1, mat, part="haft", width=3, depth=3, **data):
    return VX.slab(-width / 2, width / 2, y0, y1, depth, mat, part, **data)


def bit(x0, cols, core, edge, edge_w=1, core_d=3, edge_d=1, side=-1, part="bit", **data):
    """An axe's bit from the eye outward: cols (width, y0, y1), each a column group of the core a little taller than the
    last (so the bit flares from the eye to its edge in whole-texel steps), then the cutting edge: thinner, in its own
    material, a texel taller at each end than the last group."""
    B = []
    x = x0
    for w, y0, y1 in cols:
        xa, xb = sorted((x, x + side * w))
        B.append(VX.slab(xa, xb, y0, y1, core_d, core, part, **data))
        x += side * w
    y0, y1 = cols[-1][1] - 1, cols[-1][2] + 1
    xa, xb = sorted((x, x + side * edge_w))
    B.append(VX.slab(xa, xb, y0, y1, edge_d, edge, part, **data))
    return B


def langets(y0, y1, mat, depth=5, part="langets"):
    """Metal strips down the haft's two faces from the head (the reference's copper strip down its shaft)."""
    return [VX.box(-0.5, 0.5, y0, y1, depth / 2 - 1, depth / 2, mat, part), VX.box(-0.5, 0.5, y0, y1, -depth / 2, -depth / 2 + 1, mat, part)]


# ---- Axes: a haft about 23 texels, a broad bit flaring to one side, a spike or a poll to the other ------------------

def wildhide_axe():
    """The Wildhide Axe (E): a hand-axe of knapped flint, its pale edge struck fresh, lashed with sinew into the top of a
    bone haft; an antler tine curls up off its back; the grip wound in hide; an amber bead at its foot."""
    B = [haft(4, 18, "bone"), haft(-4, 4, "bone_grip", "grip")]
    B += bit(-2.5, [(2, 14, 18), (2, 12, 19), (2, 11, 20)], "flint", "flint_edge", edge_w=2)
    B.append(VX.slab(-2.5, 2.5, 13, 16, 5, "sinew", "lashing"))
    B.append(VX.slab(-2.5, 2.5, 17, 18, 5, "sinew", "lashing_top"))
    B += chain(1.5, 15.0, [(62, 4, 2, 3, "antler"), (24, 3, 1, 2, "antler")], "tine", side=1)
    B.append(ring(-5, -4, "bone", part="cup"))
    B.append(VX.slab(-1.5, 1.5, -8, -5, 3, "amber_gem", "bead"))
    anchors = [((0, -6.5, 0), 2.2, RAMPS["amber"][5], 1)]
    return B, anchors, dict(accent=RAMPS["amber"][4], grip=(-4, 4), focus=(-3, 15))


def beastbound_axe():
    """The Beastbound Axe (D): a bearded bit of boar-tusk ivory, banded with its growth, two teeth set in its top,
    socketed in a riveted bronze eye with bronze langets down the haft; a bronze hammer poll holding the moss stone; a
    hide-wrapped haft banded in bronze, pelt round the hand."""
    B = [haft(3, 19, "hide"), haft(-4, 3, "pelt", "grip")]
    B += bit(-2.5, [(2, 15, 19), (2, 14, 20), (2, 12, 20), (2, 10, 20)], "tusk", "tusk_edge")
    for x in (-8.0, -5.0):  # teeth set in the bit's top
        B.append(VX.rbox((x, 20.7, 0), (1, 2, 1), 20, "tooth", "teeth"))
    B.append(VX.slab(-2.5, 2.5, 13, 20, 5, "bronze", "eye"))
    for y in (14.5, 18.5):
        for z0 in (2.5, -3.5):
            B.append(VX.box(-0.5, 0.5, y - 0.5, y + 0.5, z0, z0 + 1, "bronze", "rivets"))
    B += langets(8, 13, "bronze")
    B.append(VX.slab(2.5, 6.5, 14, 19, 5, "bronze_back", "poll"))
    B.append(VX.rbox((4.5, 16.5, 0), (2, 2, 7), 45, "moss_gem", "stone"))
    for y0 in (3, -1):
        B.append(ring(y0, y0 + 1, "bronze", part=f"band{y0}"))
    B.append(VX.slab(-2.5, 2.5, -6, -4, 5, "bronze", "foot"))
    anchors = [((4.5, 16.5, 0), 1.8, RAMPS["moss"][6], 1)]
    return B, anchors, dict(accent=RAMPS["moss"][5], grip=(-4, 3), focus=(-2, 15))


def gatewrought_axe():
    """The Gatewrought Axe (C): a squared fan of gate-steel, its edge mirror-honed, the signal circuit running out
    across it from a brass eye where the crystal sits; a brass back-spike stepped like a pylon's point; a slate haft
    with the circuit climbing it to the head between brass langets, ribbed round the hand."""
    B = [haft(3, 19, "slate_rib", span=(4, 13)), haft(-4, 3, "slate_grip", "grip")]
    B += bit(-3.5, [(2, 14, 18), (2, 13, 19), (2, 12, 20)], "slate_bit", "mirror_edge", row=16, span=(3, 10))
    B.append(VX.slab(-3.5, 3.5, 13, 19, 5, "brass", "eye"))
    B.append(VX.rbox((0, 16, 0), (2, 2, 7), 45, "signal_gem", "crystal"))
    B += [VX.slab(3.5, 6.5, 14, 18, 3, "brass", "spike"), VX.slab(6.5, 8.5, 15, 17, 3, "pylon", "spike_tip", column=7, span=(15, 17)),
          VX.slab(8.5, 9.5, 15.5, 16.5, 1, "brass", "spike_point")]
    B += [VX.box(-2.5, -1.5, 9, 13, -1.5, 1.5, "brass", "langets"), VX.box(1.5, 2.5, 9, 13, -1.5, 1.5, "brass", "langets")]
    B.append(ring(2, 3, "brass", part="ferrule"))
    B.append(VX.slab(-2.5, 2.5, -6, -4, 5, "brass", "foot"))
    B.append(VX.rbox((0, -6.5, 0), (2, 2, 2), 45, "signal_gem", "foot_crystal"))
    anchors = [((0, 16, 0), 1.9, RAMPS["signal"][6], 1)]
    return B, anchors, dict(accent=RAMPS["signal"][5], grip=(-4, 3), focus=(-3, 16))


def veilwoven_axe():
    """The Veilwoven Axe (B): a crescent of near-black Veil-steel whose edge is living spirit light, its horns swept
    back like the Veilwoven's quillons; a mirror spike curving up off its back; the spirit stone in a mirror eye; an
    obsidian haft ringed in mirror, mirror langets, a mirror drop at its foot."""
    B = [haft(-4, 19, "obsidian")]
    B += bit(-2.5, [(2, 14, 18), (2, 13, 19), (2, 12, 20)], "veil", "spirit_edge", span=(11, 21))
    B += chain(-8.0, 19.5, [(30, 3, 2, 2, "veil"), (8, 3, 1, 1, "spirit_edge")], "horn_up")
    B += chain(-8.0, 12.5, [(150, 3, 2, 2, "veil"), (172, 3, 1, 1, "spirit_edge")], "horn_low")
    B.append(VX.slab(-2.5, 2.5, 13, 18, 5, "mirror", "eye"))
    B.append(VX.rbox((0, 15.5, 0), (3, 3, 7), 45, "spirit_gem", "eye_gem"))
    B += chain(2.5, 15.5, [(65, 3, 2, 3, "mirror"), (30, 3, 1, 2, "mirror")], "spike", side=1)
    B += langets(9, 13, "mirror")
    for y0 in (3, -3):
        B.append(ring(y0, y0 + 1, "mirror", part=f"band{y0}"))
    B.append(VX.rbox((0, -6.2, 0), (3, 3, 5), 45, "mirror", "drop"))
    anchors = [((0, 15.5, 0), 2.2, RAMPS["spirit"][6], 1)]
    edge = [(-9.0, y + 0.5, 0.0) for y in range(11, 21, 2)]
    streams = [(WISP, RAMPS["spirit"][6], 4, 0.18, edge), (MOTE, RAMPS["spirit"][7], 2, 1.4, [(0, 15.5, 0)])]
    return B, anchors, dict(accent=RAMPS["spirit"][5], grip=(-4, 3), focus=(-3, 15.5), streams=streams)


def lordscale_axe():
    """The Lordscale Axe (A): a broad bearded bit of black scale capped along its top in gold, its edge molten gold
    running down into the beard; a gold wing for its poll, its feathers black and gold-tipped; the pearl in a
    gold-framed eye; a black-scale haft banded in gold with gold langets, a gold claw at its foot."""
    B = [haft(-4, 19, "lord_grip")]
    cols = [(2, 14, 19), (2, 13, 20), (2, 11, 20), (2, 9, 21)]
    B += bit(-2.5, cols, "lord_plain", "ember_edge", edge_w=2, span=(8, 22))
    x = -2.5
    for w, y0, y1 in cols:  # the gold cap along the bit's top
        B.append(VX.slab(x - w, x, y1, y1 + 1, 3, "gold", "cap"))
        x -= w
    B.append(VX.slab(-2.5, 2.5, 12, 20, 5, "lord_dark", "eye"))
    B += frame((0, 15.5, 0), 4, 2, 7, 45, "gold", "bezel")
    B.append(VX.rbox((0, 15.5, 0), (2, 2, 6), 45, "pearl_gem", "pearl"))
    B += fan(2.0, 15.5, [(48, 7, 2, 4, "gold"), (72, 6, 2, 3, "feather"), (96, 5, 2, 2, "feather")], "wing", side=1)
    B += langets(8, 12, "gold")
    for y0 in (3, -3):
        B.append(ring(y0, y0 + 1, "gold", part=f"band{y0}"))
    B.append(VX.slab(-2.5, 2.5, -6, -4, 5, "gold", "foot"))
    B.append(VX.slab(-1, 1, -7, -6, 2, "gold", "foot_claw"))
    anchors = [((0, 15.5, 0), 2.0, RAMPS["pearl"][4], 1)]
    streams = [(EMBER, RAMPS["ember"][6], 6, 0.12, [(-12.0, y + 0.5, 0.0) for y in range(9, 21, 2)])]
    return B, anchors, dict(accent=RAMPS["ember"][5], grip=(-4, 3), focus=(-3, 15), streams=streams)


def sovereign_axe():
    """The Sovereign Axe (S): an ivory bit flaring to a gold edge, the Court's runes burning down a crimson channel in
    its face; a gold crown of two spikes for its back; a great crimson stone in a raised gold diamond at the eye; a
    void-weave haft bound in gold wire with gold langets, a gold star at its foot."""
    B = [haft(3, 19, "weave"), haft(-4, 3, "weave_grip", "grip")]
    B += bit(-2.5, [(2, 14, 18), (2, 13, 19), (3, 11, 21)], "ivory", "gold_edge", column=-7, span=(12, 20))
    B.append(VX.slab(-9.5, -6.5, 10, 11, 1, "gold_edge", "rim_low"))
    B.append(VX.slab(-9.5, -6.5, 21, 22, 1, "gold_edge", "rim_top"))
    B.append(VX.slab(-2.5, 2.5, 13, 18, 5, "gold", "eye"))
    B += frame((0, 15.5, 0), 5, 3, 9, 45, "gold", "frame")
    B.append(VX.rbox((0, 15.5, 0), (3, 3, 7), 45, "crimson_gem", "heart"))
    B += chain(3.0, 16.5, [(55, 3, 2, 3, "gold"), (20, 3, 1, 2, "gold")], "crown", side=1)
    B += chain(3.0, 14.5, [(110, 3, 2, 3, "gold"), (140, 2, 1, 2, "gold")], "crown_low", side=1)
    B += langets(8, 12, "gold")
    for y0 in (3, -3):
        B.append(ring(y0, y0 + 1, "gold", part=f"band{y0}"))
    B.append(VX.slab(-1.5, 1.5, -7, -4, 3, "gold", "star"))
    B.append(VX.rbox((0, -5.5, 0), (3, 3, 4), 45, "gold", "star_turned"))
    B.append(VX.rbox((0, -5.5, 0), (1, 1, 6), 0, "crimson_gem", "star_gem"))
    anchors = [((0, 15.5, 0), 2.6, RAMPS["crimson"][5], 1)]
    streams = [(MOTE, RAMPS["crimson"][6], 4, 1.8, [(0, 15.5, 0)]), (MOTE, RAMPS["gold"][6], 2, 0.25, [(-7, 12.5, 0.0), (-7, 19.5, 0.0)])]
    return B, anchors, dict(accent=RAMPS["crimson"][4], grip=(-4, 3), focus=(-3, 15.5), streams=streams)


DESIGNS.update({("axe", "E"): wildhide_axe, ("axe", "D"): beastbound_axe, ("axe", "C"): gatewrought_axe,
                ("axe", "B"): veilwoven_axe, ("axe", "A"): lordscale_axe, ("axe", "S"): sovereign_axe})
HOLDS["axe"] = ([0, -72, 30], [-2.0, 1.5, -0.6], 0.4)


# ---- Mauls: the reference's own: a long haft about 26 texels, a huge block head across its top (a core between two
# flared caps, a stone in a raised frame at its heart), a heavy pommel; held in two hands --------------------------

HEAD = 23.5  # the head's middle


def caps(x0, x1, y0, y1, depth, mat, part="caps"):
    """The head's two flared ends, mirrored across the haft."""
    return [VX.slab(-x1, -x0, y0, y1, depth, mat, part), VX.slab(x0, x1, y0, y1, depth, mat, part + "'")]


def wildhide_maul():
    """The Wildhide Maul (E): a great nodule of knapped flint lashed crosswise to a bone haft, sinew bound round it
    where the haft goes in, an amber bead knotted into the binding; antler burrs ring its ends and two tines rise from
    them; a bone knob and a hung tooth at its foot."""
    B = [haft(-5, 21, "bone"), haft(-5, 3, "bone_grip", "grip")]
    B.append(VX.slab(-5.5, 5.5, 20, 27, 7, "flint", "head"))
    B += caps(5.5, 6.5, 19, 28, 9, "antler", "burrs")
    B.append(VX.slab(-1.5, 1.5, 19, 28, 9, "sinew", "binding"))
    B.append(VX.rbox((0, HEAD, 0), (2, 2, 11), 45, "amber_gem", "bead"))
    B += both(chain(-6.0, 27.5, [(-20, 3, 1, 3, "antler"), (8, 2, 1, 2, "antler")], "tines"))
    B.append(VX.slab(-2.5, 2.5, 17, 19, 5, "sinew", "collar"))
    B.append(VX.slab(-2.5, 2.5, -8, -5, 5, "bone", "knob"))
    B.append(VX.slab(2.5, 3.5, -8, -6, 1, "hide", "thong"))
    B.append(VX.rbox((3.0, -9.0, 0), (1, 2, 2), -10, "antler", "tooth"))
    anchors = [((0, HEAD, 0), 2.0, RAMPS["amber"][5], 1)]
    return B, anchors, dict(accent=RAMPS["amber"][4], grip=(-5, 3), focus=(0, HEAD - 1))


def beastbound_maul():
    """The Beastbound Maul (D): a block of tusk ivory, banded with its growth, held between riveted bronze straps and
    plated bronze ends; two tusks curl up off its top; the moss stone in a bronze diamond at its heart; a hide haft
    with bronze langets, pelt round the hands, a bronze claw at its foot."""
    B = [haft(-5, 21, "hide"), haft(-5, 3, "pelt", "grip")]
    B.append(VX.slab(-5.5, 5.5, 20, 27, 7, "tusk", "head"))
    B += caps(5.5, 7.5, 19, 28, 9, "bronze", "ends")
    B += [VX.slab(-3.5, -2.5, 19.5, 27.5, 8, "bronze_back", "strap"), VX.slab(2.5, 3.5, 19.5, 27.5, 8, "bronze_back", "strap'")]
    B += frame((0, HEAD, 0), 4, 2, 9, 45, "bronze", "bezel")
    B.append(VX.rbox((0, HEAD, 0), (2, 2, 8), 45, "moss_gem", "stone"))
    B += both(chain(-6.0, 27.5, [(-10, 3, 2, 3, "horn"), (25, 3, 2, 2, "horn"), (60, 2, 1, 1, "horn_tip")], "tusks"))
    B.append(VX.slab(-2.5, 2.5, 18, 20, 5, "bronze", "collar"))
    B += langets(12, 18, "bronze")
    B.append(ring(3, 4, "bronze", part="band"))
    B.append(VX.slab(-2.5, 2.5, -8, -5, 5, "bronze", "foot"))
    for sx in (-1, 1):
        B.append(VX.box(sx * 2 - 0.5, sx * 2 + 0.5, -9, -8, -0.5, 0.5, "bronze", "talons"))
    anchors = [((0, HEAD, 0), 1.9, RAMPS["moss"][6], 1)]
    return B, anchors, dict(accent=RAMPS["moss"][5], grip=(-5, 3), focus=(0, HEAD - 1))


def gatewrought_maul():
    """The Gatewrought Maul (C): a Gate's keystone on a staff. A block of slate gate-steel between flared brass caps,
    a signal slot lit down each cap's face; the crystal at its heart in a raised brass diamond; the circuit climbing the
    slate haft between brass langets; a brass block for a pommel with a second crystal in it."""
    B = [haft(-5, 21, "slate_rib", span=(4, 18)), haft(-5, 3, "slate_grip", "grip")]
    B.append(VX.slab(-4.5, 4.5, 20, 27, 7, "slate_head", "head"))
    B += [VX.slab(-7.5, -4.5, 19, 28, 9, "cap_slot", "cap", column=-6, span=(20, 27)),
          VX.slab(4.5, 7.5, 19, 28, 9, "cap_slot", "cap'", column=6, span=(20, 27))]
    B += frame((0, HEAD, 0), 5, 3, 11, 45, "brass", "frame")
    B.append(VX.rbox((0, HEAD, 0), (3, 3, 9), 45, "signal_gem", "crystal"))
    B.append(VX.slab(-2.5, 2.5, 18, 20, 5, "brass", "collar"))
    B += [VX.box(-2.5, -1.5, 13, 18, -1.5, 1.5, "brass", "langets"), VX.box(1.5, 2.5, 13, 18, -1.5, 1.5, "brass", "langets")]
    B.append(ring(3, 4, "brass", part="ferrule"))
    B.append(VX.slab(-2.5, 2.5, -8, -5, 5, "brass", "pommel"))
    B.append(VX.rbox((0, -6.5, 0), (2, 2, 7), 45, "signal_gem", "pommel_crystal"))
    anchors = [((0, HEAD, 0), 2.6, RAMPS["signal"][6], 1), ((0, -6.5, 0), 1.6, RAMPS["signal"][6], 1)]
    return B, anchors, dict(accent=RAMPS["signal"][5], grip=(-5, 3), focus=(0, HEAD - 1))


def veilwoven_maul():
    """The Veilwoven Maul (B): a block of near-black Veil-steel, oil-sheened, a seam of living spirit light round it
    either side of its heart; its ends mirror fins swept back like the Veilwoven's quillons; the spirit stone in a mirror
    frame; an obsidian haft ringed in mirror; a great mirror drop for a pommel holding a second stone."""
    B = [haft(-5, 21, "obsidian")]
    B.append(VX.slab(-4.5, 4.5, 20, 27, 7, "veil", "head"))
    B += [VX.slab(-3.5, -2.5, 19.5, 27.5, 8, "spirit_edge", "seam", span=(19, 28)),
          VX.slab(2.5, 3.5, 19.5, 27.5, 8, "spirit_edge", "seam'", span=(19, 28))]
    B += caps(4.5, 6.5, 19, 28, 9, "mirror", "ends")
    B += both(chain(-6.5, 27.0, [(-40, 3, 2, 3, "mirror"), (-15, 3, 1, 2, "mirror")], "fins"))
    B += both(chain(-6.5, 20.0, [(-140, 3, 2, 3, "mirror"), (-165, 2, 1, 2, "mirror")], "fins_low"))
    B += frame((0, HEAD, 0), 4, 2, 9, 45, "mirror", "frame")
    B.append(VX.rbox((0, HEAD, 0), (2, 2, 8), 45, "spirit_gem", "heart"))
    B.append(VX.slab(-2.5, 2.5, 18, 20, 5, "mirror", "collar"))
    B += langets(12, 18, "mirror")
    for y0 in (3, -5):
        B.append(ring(y0, y0 + 1, "mirror", part=f"band{y0}"))
    B.append(VX.rbox((0, -8.0, 0), (4, 4, 5), 45, "mirror", "drop"))
    B.append(VX.rbox((0, -8.0, 0), (2, 2, 7), 45, "spirit_gem", "drop_gem"))
    anchors = [((0, HEAD, 0), 2.2, RAMPS["spirit"][6], 1), ((0, -8.0, 0), 1.8, RAMPS["spirit"][6], 1)]
    streams = [(WISP, RAMPS["spirit"][6], 4, 0.3, [(-3.0, 20.0, 0.0), (-3.0, 23.5, 0.0), (-3.0, 27.0, 0.0), (3.0, 27.0, 0.0),
                                                  (3.0, 23.5, 0.0), (3.0, 20.0, 0.0)]),
               (MOTE, RAMPS["spirit"][7], 2, 1.6, [(0, HEAD, 0)])]
    return B, anchors, dict(accent=RAMPS["spirit"][5], grip=(-5, 3), focus=(0, HEAD - 1), streams=streams)


def lordscale_maul():
    """The Lordscale Maul (A): a head of black scale between gold caps, molten gold seething in the seams where the
    plates meet, a gold claw gripping each cap's corners; the pearl in a gold bezel at its heart; a black-scale haft
    banded in gold, gold langets; a gold claw for a pommel holding a second pearl."""
    B = [haft(-5, 21, "lord_grip")]
    B.append(VX.slab(-4.5, 4.5, 20, 27, 7, "lord_plain", "head"))
    B += [VX.slab(-5.5, -4.5, 19.5, 27.5, 8, "ember_edge", "seam", span=(19, 28)),
          VX.slab(4.5, 5.5, 19.5, 27.5, 8, "ember_edge", "seam'", span=(19, 28))]
    B += caps(5.5, 8.5, 19, 28, 9, "gold_cap", "caps")
    for sx in (-1, 1):
        for y, deg in ((28.2, 35), (18.8, 145)):  # claws over the caps' corners
            B.append(VX.rbox((sx * 8.4, y, 0), (1, 3, 5), -sx * deg, "gold", f"claw{sx}{y}"))
    B += frame((0, HEAD, 0), 5, 3, 9, 45, "gold", "bezel")
    B.append(VX.rbox((0, HEAD, 0), (3, 3, 8), 45, "pearl_gem", "pearl"))
    B.append(VX.slab(-2.5, 2.5, 18, 20, 5, "gold", "collar"))
    B += langets(12, 18, "gold")
    for y0 in (3, -5):
        B.append(ring(y0, y0 + 1, "gold", part=f"band{y0}"))
    B.append(ring(-6, -5, "gold", part="claw_cup"))
    B.append(VX.slab(-1.5, 1.5, -9, -6, 3, "pearl_gem", "pearl_low"))
    for sx, sz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        B.append(VX.box(sx * 2 - 0.5, sx * 2 + 0.5, -9, -6, sz * 2 - 0.5, sz * 2 + 0.5, "gold", "talons"))
    anchors = [((0, HEAD, 0), 2.6, RAMPS["pearl"][4], 1), ((0, -7.5, 0), 2.0, RAMPS["pearl"][4], 1)]
    streams = [(EMBER, RAMPS["ember"][6], 5, 0.12, [(side * 5.0, y, 0.0) for y in (20.0, 22.0, 24.0, 26.0, 27.0)]) for side in (-1, 1)]
    return B, anchors, dict(accent=RAMPS["ember"][5], grip=(-5, 3), focus=(0, HEAD - 1), streams=streams)


def sovereign_maul():
    """The Sovereign Maul (S): the reference's hammer made the Court's. An ivory block between flared gold caps
    trimmed round in crimson, a crown of gold points along its top, the Court's runes burning down two crimson
    channels either side of a great crimson stone in a raised gold diamond; a void-weave haft bound in gold wire, gold
    langets; a gold star for a pommel round a second stone."""
    B = [haft(4, 21, "weave"), haft(-5, 4, "weave_grip", "grip")]
    B.append(VX.slab(-4.5, 4.5, 20, 27, 7, "ivory_twin", "head"))
    B += caps(4.5, 7.5, 19, 28, 9, "gold_trim", "caps")
    for x in (-6.0, 6.0, 0.0):  # the crown's points
        B.append(VX.rbox((x, 28.4, 0), (2, 2, 3), 45, "gold", f"crown{x}"))
    B += frame((0, HEAD, 0), 5, 3, 11, 45, "gold", "frame")
    B.append(VX.rbox((0, HEAD, 0), (3, 3, 9), 45, "crimson_gem", "heart"))
    B.append(VX.slab(-2.5, 2.5, 18, 20, 5, "gold", "collar"))
    B += langets(12, 18, "gold")
    for y0 in (3, -5):
        B.append(ring(y0, y0 + 1, "gold", part=f"band{y0}"))
    B.append(VX.slab(-2, 2, -10, -6, 3, "gold", "star"))
    B.append(VX.rbox((0, -8, 0), (4, 4, 4), 45, "gold", "star_turned"))
    B.append(VX.rbox((0, -8, 0), (2, 2, 7), 0, "crimson_gem", "star_gem"))
    anchors = [((0, HEAD, 0), 2.8, RAMPS["crimson"][5], 1), ((0, -8, 0), 2.0, RAMPS["crimson"][5], 1)]
    streams = [(MOTE, RAMPS["crimson"][6], 4, 2.2, [(0, HEAD, 0)]), (MOTE, RAMPS["crimson"][6], 2, 1.6, [(0, -8, 0)]),
               (MOTE, RAMPS["gold"][6], 3, 0.25, [(-3, 20.5, 0.0), (-3, 26.5, 0.0)])]
    return B, anchors, dict(accent=RAMPS["crimson"][4], grip=(-5, 3), focus=(0, HEAD - 1), streams=streams)


DESIGNS.update({("maul", "E"): wildhide_maul, ("maul", "D"): beastbound_maul, ("maul", "C"): gatewrought_maul,
                ("maul", "B"): veilwoven_maul, ("maul", "A"): lordscale_maul, ("maul", "S"): sovereign_maul})
HOLDS["maul"] = ([0, -72, 30], [-2.0, 1.0, -0.6], 0.3)


# ---- Spears: reach at a glance: a slender shaft about 31 texels (2 by 2, where the other hafts are 3) and a head
# about 10 long: a leaf, a boar spear's lugs, a fork, a wave, wings, a spire --------------------------------------

def shaft(y0, y1, mat, part="shaft", **data):
    return VX.slab(-1, 1, y0, y1, 2, mat, part, **data)


def collar(y0, y1, mat, width=4, depth=4, part=None):
    return VX.slab(-width / 2, width / 2, y0, y1, depth, mat, part or f"collar{y0}")


def wildhide_spear():
    """The Wildhide Spear (E): a long leaf of knapped flint, its edges struck pale, lashed with sinew into a bone shaft;
    an antler tine bound below it for a barb; two hide tassels hang from the lashing; hide round the hand; an amber
    bead on its butt."""
    B = [shaft(-10, 21, "bone"), shaft(-4, 3, "bone_grip", "grip")]
    B += blade([(20, 22, 3, 1), (22, 27, 5, 3), (27, 29, 3, 1), (29, 31, 1, 1)], "flint", "flint_edge")
    B.append(collar(17, 21, "sinew", part="lashing"))
    B += chain(2.0, 18.0, [(35, 3, 1, 2, "antler"), (10, 2, 1, 1, "antler")], "barb", side=1)
    B += [VX.slab(-3.0, -2.0, 12, 17, 1, "hide", "tassel"), VX.slab(-2.0, -1.0, 13, 17, 1, "hide", "tassel'", z=1.5)]
    B.append(collar(-11, -10, "bone", part="cup"))
    B.append(VX.rbox((0, -12.4, 0), (2, 2, 2), 45, "amber_gem", "bead"))
    anchors = [((0, -12.4, 0), 1.6, RAMPS["amber"][5], 1)]
    return B, anchors, dict(accent=RAMPS["amber"][4], grip=(-4, 3), focus=(0, 23))


def beastbound_spear():
    """The Beastbound Spear (D): a boar spear. A broad leaf of tusk ivory on a bronze socket, a bronze crossbar below it
    to stop the charge, a tooth set upright at each end of it, the moss stone in the socket; a hide shaft with pelt at
    the hand between bronze ferrules; a bronze spike on its butt."""
    B = [shaft(-10, 20, "hide"), shaft(-4, 3, "pelt", "grip")]
    B += blade([(21, 23, 5, 3), (23, 27, 7, 5), (27, 29, 5, 3), (29, 31, 3, 1), (31, 32, 1, 1)], "tusk", "tusk_edge", groove=0)
    B.append(collar(17, 21, "bronze", part="socket"))
    B.append(VX.rbox((0, 19, 0), (2, 2, 6), 45, "moss_gem", "stone"))
    B.append(VX.slab(-5.5, 5.5, 18, 19, 3, "bronze", "lugs"))
    for x in (-5.0, 5.0):
        B.append(VX.rbox((x, 20.0, 0), (1, 2, 1), 0, "tooth", f"tooth{x}"))
    for y0 in (3, -5):
        B.append(collar(y0, y0 + 1, "bronze", part=f"ferrule{y0}"))
    B += [collar(-11, -10, "bronze", 3, 3, "butt"), VX.slab(-0.5, 0.5, -13, -11, 1, "bronze", "spike")]
    anchors = [((0, 19, 0), 1.6, RAMPS["moss"][6], 1)]
    return B, anchors, dict(accent=RAMPS["moss"][5], grip=(-4, 3), focus=(0, 23))


def gatewrought_spear():
    """The Gatewrought Spear (C): a fork of gate-steel, two prongs rising from a brass yoke, mirror-edged, the circuit
    lit up each prong; the signal crystal floats between them; a slate shaft, the circuit climbing it to the yoke,
    ribbed round the hand; a brass butt with a second crystal."""
    B = [shaft(-10, 20, "slate_rib", span=(4, 19), column=0), shaft(-4, 3, "slate_grip", "grip")]
    for x in (-3.0, 3.0):
        B += blade([(21, 28, 3, 1), (28, 30, 1, 1)], "slate_rib", "slate_flat", x=x, span=(21, 28), column=int(x))
    B.append(VX.slab(-4.5, 4.5, 19, 21, 4, "brass", "yoke"))
    B.append(collar(16, 19, "brass", part="socket"))
    B.append(VX.rbox((0, 25.0, 0), (2, 2, 2), 45, "signal_gem", "crystal"))
    B.append(collar(3, 4, "brass", part="ferrule"))
    B.append(collar(-11, -10, "brass", part="butt"))
    B.append(VX.rbox((0, -12.4, 0), (2, 2, 2), 45, "signal_gem", "butt_crystal"))
    anchors = [((0, 25.0, 0), 1.8, RAMPS["signal"][6], 1)]
    return B, anchors, dict(accent=RAMPS["signal"][5], grip=(-4, 3), focus=(0, 24))


SPEAR_WAVE = ((21, 0), (24, 1), (27, 0), (29, -1), (31, 0))


def spear_shift(y):
    o = 0
    for y0, s_ in SPEAR_WAVE:
        if y >= y0:
            o = s_
    return o


def veilwoven_spear():
    """The Veilwoven Spear (B): a long waved spike of near-black Veil-steel, one edge living spirit light, the other
    mirror-honed; mirror wings swept back below it like a crescent; the spirit stone in a mirror socket; an obsidian
    shaft ringed in mirror; a mirror drop on its butt."""
    B = [shaft(-10, 20, "obsidian")]
    B += blade([(21, 24, 5, 3), (24, 27, 5, 3), (27, 29, 5, 3), (29, 31, 3, 1), (31, 33, 1, 1)], "veil", "veil_edge", shift=spear_shift,
               span=(21, 33))
    B.append(collar(17, 21, "mirror", part="socket"))
    B.append(VX.rbox((0, 19, 0), (2, 2, 6), 45, "spirit_gem", "stone"))
    B += both(chain(-2.0, 20.0, [(110, 3, 2, 3, "mirror"), (150, 3, 1, 2, "mirror")], "wings"))
    for y0 in (14, 3, -5):
        B.append(collar(y0, y0 + 1, "mirror", 3, 3, f"band{y0}"))
    B.append(VX.rbox((0, -11.6, 0), (2, 2, 3), 45, "mirror", "drop"))
    anchors = [((0, 19, 0), 1.6, RAMPS["spirit"][6], 1)]
    edge = [(-2.5 + spear_shift(y), y + 0.5, 0.0) for y in (21, 23, 25, 27, 28, 30)]
    streams = [(WISP, RAMPS["spirit"][6], 4, 0.18, edge), (MOTE, RAMPS["spirit"][7], 2, 1.2, [(0, 19, 0)])]
    return B, anchors, dict(accent=RAMPS["spirit"][5], grip=(-4, 3), focus=(0, 24), streams=streams)


def lordscale_spear():
    """The Lordscale Spear (A): a broad leaf of black scale, a gold ridge up its middle, its edges molten gold that run
    to the point; a gold wing spread either side of its socket, black feathers under gold pinions; the pearl in the
    socket; a black-scale shaft banded in gold; a gold claw on its butt."""
    B = [shaft(-10, 20, "lord_grip")]
    B += blade([(21, 23, 3, 1), (23, 28, 5, 3), (28, 30, 3, 1), (30, 31, 1, 1)], "lord", "ember_edge", span=(21, 31))
    B.append(collar(17, 21, "gold", part="socket"))
    B.append(VX.rbox((0, 19, 0), (2, 2, 6), 45, "pearl_gem", "pearl"))
    B += both(fan(-1.5, 19.5, [(58, 6, 2, 3, "gold"), (84, 5, 2, 2, "feather"), (108, 4, 1, 1, "feather")], "wing"))
    for y0 in (14, 3, -5):
        B.append(collar(y0, y0 + 1, "gold", 3, 3, f"band{y0}"))
    B += [collar(-11, -10, "gold", part="cup"), VX.rbox((0, -11.8, 0), (2, 2, 2), 45, "pearl_gem", "pearl_low")]
    anchors = [((0, 19, 0), 1.6, RAMPS["pearl"][4], 1)]
    streams = [(EMBER, RAMPS["ember"][6], 5, 0.12, [(side * 2.5, y + 0.5, 0.0) for y in (23, 24, 25, 26, 27)]) for side in (-1, 1)]
    return B, anchors, dict(accent=RAMPS["ember"][5], grip=(-4, 3), focus=(0, 23), streams=streams)


def sovereign_spear():
    """The Sovereign Spear (S): a long ivory spire edged in gold, the Court's runes burning down a crimson channel up
    its middle; a gold crown for its collar, its points rising round the blade's root, a crimson stone in a gold
    diamond on it; a void-weave shaft bound in gold wire; a gold star on its butt."""
    B = [shaft(-10, 20, "weave"), shaft(-4, 3, "weave_grip", "grip")]
    B += blade([(21, 29, 5, 3), (29, 31, 3, 1), (31, 33, 1, 1)], "ivory", "gold_edge", span=(22, 29), column=0)
    B.append(collar(17, 21, "gold", 5, 5, "crown"))
    for x in (-2.0, 2.0):
        B.append(VX.rbox((x, 21.6, 0), (1, 2, 3), -x * 8, "gold", f"crown_point{x}"))
    B += frame((0, 19, 0), 4, 2, 7, 45, "gold", "frame")
    B.append(VX.rbox((0, 19, 0), (2, 2, 6), 45, "crimson_gem", "stone"))
    for y0 in (3, -5):
        B.append(collar(y0, y0 + 1, "gold", 3, 3, f"band{y0}"))
    B.append(VX.slab(-1.5, 1.5, -13, -10, 2, "gold", "star"))
    B.append(VX.rbox((0, -11.5, 0), (3, 3, 3), 45, "gold", "star_turned"))
    anchors = [((0, 19, 0), 2.0, RAMPS["crimson"][5], 1)]
    streams = [(MOTE, RAMPS["crimson"][6], 3, 1.6, [(0, 19, 0)]), (MOTE, RAMPS["gold"][6], 3, 0.25, [(0, 22.5, 0.0), (0, 28.5, 0.0)])]
    return B, anchors, dict(accent=RAMPS["crimson"][4], grip=(-4, 3), focus=(0, 23), streams=streams)


DESIGNS.update({("spear", "E"): wildhide_spear, ("spear", "D"): beastbound_spear, ("spear", "C"): gatewrought_spear,
                ("spear", "B"): veilwoven_spear, ("spear", "A"): lordscale_spear, ("spear", "S"): sovereign_spear})
HOLDS["spear"] = ([0, -72, 30], [-1.5, -1.0, -0.6], 0.4)


# ---- Greatblades: about 38 texels; a blade 9 to 11 wide, far broader than a Longsword's 5; a grip of 8 for two hands
# with a ring between them; above the guard a ricasso (a blunt stretch to choke up on) bound and ended in parrying
# lugs; and each tier's own outline no Longsword has: a cleaver slab, a hooked back, a window through the blade, a
# flamberge, a dragon-tail fork, a crown with flame lugs ------------------------------------------------------------

def ricasso(core, wrap, y0=5, y1=11, part="ricasso", wraps=((6, 7), (8, 9)), **data):
    """The blunt root above the guard: the blade's core 3 wide, bound in bands of another material."""
    B = [VX.slab(-1.5, 1.5, y0, y1, 3, core, part, **data)]
    for a, b in wraps:
        B.append(VX.slab(-2.5, 2.5, a, b, 5, wrap, f"{part}_wrap{a}"))
    return B


def two_hands(mat, ring_mat, y0=-5, y1=3):
    """The long grip, a ring between the hands."""
    return [grip(y0, y1, mat), ring(-1, 0, ring_mat, width=5, depth=5, part="grip_ring")]


def wildhide_greatblade():
    """The Wildhide Greatblade (E): a cleaver of knapped flint, a slab broad as two hands with its top struck off at a
    slant, flakes broken deep out of both edges, the edges pale; its root a sinew-bound ricasso with antler lugs; a bone
    collar with antlers branching up beside it; a long bone grip in hide; an amber bead in a bone cup."""
    rows_ = [
        "efe......",  # 29..30 the slant of the struck top
        "efffe....",
        "efffffe..",
        "efffffffe",  # 26..27
        "efffffffe",
        "efffffff.",  # a flake broke deep
        "efffffffe",
        "efffffffe",
        ".fffffffe",  # 21..22
        "efffffffe",
        "efffffffe",
        "efffffff.",
        "efffffffe",
        ".fffffffe",  # 16..17
        "efffffffe",
        "efffffffe",
        "efffffff.",
        "efffffffe",
        "efffffffe",  # 11..12
    ]
    B = VX.sprite(rows_, -4, 30, {"f": ("flint", 3, "blade"), "e": ("flint_edge", 1, "blade")})
    B += ricasso("flint", "sinew")
    B += both(chain(-2.5, 10.5, [(70, 3, 1, 2, "antler"), (30, 2, 1, 1, "antler")], "lugs"))
    B.append(VX.slab(-5.5, 5.5, 3, 5, 5, "bone", "collar"))
    B += both(chain(-4.5, 4.5, [(40, 5, 2, 3, "antler"), (12, 3, 1, 2, "antler")], "antler"))
    B += both(chain(-6.0, 6.5, [(80, 2, 1, 1, "antler")], "tine"))
    B += two_hands("bone_grip", "bone")
    B.append(ring(-6, -5, "bone", part="cup"))
    B.append(VX.slab(-1.5, 1.5, -9, -6, 3, "amber_gem", "bead"))
    anchors = [((0, -7.5, 0), 2.2, RAMPS["amber"][5], 1)]
    return B, anchors, dict(accent=RAMPS["amber"][4], grip=(-5, 3), focus=(0, 10))


def beastbound_greatblade():
    """The Beastbound Greatblade (D): a great tusk blade, thick at its bronze-capped back and ground thin to the edge,
    rising to a point at its back, where the back curls over into a hook like a boar's tusk; teeth set down the back
    below it; a pelt-bound ricasso ending in bronze lugs with a tooth upright on each; a riveted bronze collar with
    tusks curling back to the hands; a long pelt grip; a bronze claw round the moss stone."""
    B = [VX.slab(-5.5, -4.5, 11, 25, 5, "bronze_back", "back"),
         VX.slab(-4.5, 1.5, 11, 27, 3, "tusk", "blade", groove=-2),
         VX.slab(-4.5, 3.5, 11, 26, 1, "tusk_edge", "blade"),
         VX.slab(-4.5, 2.5, 26, 27, 1, "tusk_edge", "blade"),
         VX.slab(-4.5, 0.5, 27, 28, 3, "tusk", "blade"),
         VX.slab(-4.5, -1.5, 28, 29, 3, "tusk", "blade"),
         VX.slab(-4.5, -2.5, 29, 30, 3, "tusk", "blade")]
    B += chain(-5.0, 24.5, [(40, 3, 2, 4, "horn"), (115, 3, 2, 2, "horn"), (170, 3, 1, 1, "horn_tip")], "hook")
    for y in (13, 16, 19):
        B.append(VX.rbox((-6.2, y + 0.6, 0), (1, 2, 1), 50, "tooth", "teeth"))
    B += ricasso("tusk", "pelt_wrap")
    B += [VX.slab(-5.5, -1.5, 10, 11, 3, "bronze", "lug"), VX.slab(1.5, 5.5, 10, 11, 3, "bronze", "lug'")]
    for x in (-5.0, 5.0):
        B.append(VX.rbox((x, 12.0, 0), (1, 2, 1), 0, "tooth", f"lug_tooth{x}"))
    B.append(VX.slab(-4.5, 4.5, 3, 5, 5, "bronze", "collar"))
    for x in (-3, 0, 3):
        for z0 in (2.5, -3.5):
            B.append(VX.box(x - 0.5, x + 0.5, 3.5, 4.5, z0, z0 + 1, "bronze", "rivets"))
    B += both(chain(-4.0, 4.0, [(70, 4, 2, 3, "horn"), (150, 4, 2, 2, "horn"), (205, 2, 1, 1, "horn_tip")], "tusks"))
    B += two_hands("pelt", "bronze")
    B.append(ring(-6, -5, "bronze", part="claw"))
    B.append(VX.slab(-1.5, 1.5, -9, -6, 3, "moss_gem", "stone"))
    for sx in (-1, 1):
        for sz in (-1, 1):
            B.append(VX.box(sx * 2 - 0.5, sx * 2 + 0.5, -9, -6, sz * 2 - 0.5, sz * 2 + 0.5, "bronze", "claw"))
    anchors = [((0, -7.5, 0), 2.2, RAMPS["moss"][6], 1)]
    return B, anchors, dict(accent=RAMPS["moss"][5], grip=(-5, 3), focus=(-1, 10))


def gatewrought_greatblade():
    """The Gatewrought Greatblade (C): a broad blade of gate-steel with a Gate cut through it: an open frame in the
    blade, the signal circuit lit round its rim and running on down the steel, the crystal floating in the opening; its
    edges mirror-honed; its point stepped like a precursors' stair; a brass-banded ricasso with brass lugs; the guard a
    great gate in brass, two pylons under heavy caps; a ribbed slate grip; a brass ring for a pommel round a second
    crystal."""
    B = [VX.slab(-3.5, -2.5, 11, 27, 3, "slate", "blade"), VX.slab(2.5, 3.5, 11, 27, 3, "slate", "blade"),
         VX.slab(-2.5, -1.5, 14, 24, 3, "signal_line", "rim", span=(11, 27)), VX.slab(1.5, 2.5, 14, 24, 3, "signal_line", "rim", span=(11, 27)),
         VX.slab(-1.5, 1.5, 14, 15, 3, "signal_line", "rim", span=(11, 27)), VX.slab(-1.5, 1.5, 23, 24, 3, "signal_line", "rim", span=(11, 27)),
         VX.slab(-2.5, 2.5, 11, 14, 3, "slate_rib", "blade", span=(11, 14), column=0),
         VX.slab(-2.5, 2.5, 24, 27, 3, "slate_rib", "blade", span=(24, 27), column=0),
         VX.slab(-4.5, -3.5, 11, 26, 1, "mirror_edge", "blade"), VX.slab(3.5, 4.5, 11, 26, 1, "mirror_edge", "blade"),
         VX.slab(-3.5, 3.5, 27, 28, 3, "slate", "blade"), VX.slab(-2.5, 2.5, 28, 29, 3, "slate", "blade"),
         VX.slab(-1.5, 1.5, 29, 30, 3, "slate", "blade"), VX.slab(-0.5, 0.5, 30, 31, 3, "slate", "blade")]
    B.append(VX.rbox((0, 19.0, 0), (2, 2, 2), 45, "signal_gem", "crystal"))
    B += ricasso("slate_rib", "brass", span=(5, 11), column=0)
    B += [VX.slab(-5.5, -1.5, 10, 11, 3, "brass", "lug"), VX.slab(1.5, 5.5, 10, 11, 3, "brass", "lug'")]
    B.append(VX.slab(-8.5, 8.5, 3, 5, 5, "brass", "bar"))
    for sx in (-1, 1):
        B.append(VX.slab(*sorted((sx * 5.5, sx * 7.5)), 5, 11, 5, "pylon", f"pylon{sx}", span=(5, 11)))
        B.append(VX.slab(*sorted((sx * 4.5, sx * 8.5)), 11, 12, 7, "brass", f"cap{sx}"))
    B += two_hands("slate_grip", "brass")
    B += VX.sprite(["RRRRR", "R...R", "R...R", "R...R", "RRRRR"], -2, -5, {"R": ("brass", 3, "loop")})
    B.append(VX.rbox((0, -7.5, 0), (2, 2, 2), 45, "signal_gem", "pommel_crystal"))
    anchors = [((0, 19.0, 0), 2.4, RAMPS["signal"][6], 1), ((0, -7.5, 0), 1.8, RAMPS["signal"][6], 1)]
    return B, anchors, dict(accent=RAMPS["signal"][5], grip=(-5, 3), focus=(0, 15))


FLAMBERGE = (9, 7, 5, 7)  # the blade's width two rows at a time: the core alone (5) at the wave's waist


def veilwoven_greatblade():
    """The Veilwoven Greatblade (B): a flamberge of near-black Veil-steel, its edges rippling in and out down its whole
    length, one edge living spirit light, the other mirror-honed; a mirror-banded ricasso ending in mirror hooks; mirror
    quillons swept up like wings off a mirror heart holding the spirit stone; a long obsidian grip; a mirror drop round
    a second stone."""
    B = [VX.slab(-2.5, 2.5, 11, 27, 3, "veil", "blade")]
    for k, y0 in enumerate(range(11, 27, 2)):  # the flamberge: the edges swell and draw in, two texels each way
        w = FLAMBERGE[k % len(FLAMBERGE)]
        if w > 5:
            B.append(VX.slab(-w / 2, w / 2, y0, min(y0 + 2, 27), 1, "veil_edge", "blade", span=(11, 31)))
    B += blade([(27, 29, 5, 3), (29, 31, 3, 1), (31, 32, 1, 1)], "veil", "veil_edge", span=(11, 31))
    B += ricasso("veil", "mirror")
    B += both(chain(-2.5, 10.5, [(85, 2, 1, 4, "mirror"), (30, 2, 1, 3, "mirror"), (-10, 2, 1, 2, "spirit_edge")], "hooks"))
    B.append(VX.slab(-3.5, 3.5, 2, 5, 5, "mirror", "heart"))
    B.append(VX.rbox((0, 3.5, 0), (3, 3, 7), 45, "spirit_gem", "heart_gem"))
    B += both(chain(-3.0, 3.5, [(75, 3, 2, 3, "mirror"), (45, 4, 2, 2, "mirror"), (15, 4, 1, 1, "mirror")], "quillons"))
    B += two_hands("obsidian", "mirror", y1=2)
    B.append(VX.rbox((0, -7.4, 0), (3, 3, 5), 45, "mirror", "drop"))
    B.append(VX.rbox((0, -7.4, 0), (2, 2, 7), 45, "spirit_gem", "drop_gem"))
    anchors = [((0, 3.5, 0), 2.4, RAMPS["spirit"][6], 1), ((0, -7.4, 0), 1.9, RAMPS["spirit"][6], 1)]
    edge = [(-FLAMBERGE[((y - 11) // 2) % len(FLAMBERGE)] / 2, y + 0.5, 0.0) for y in range(11, 27, 2)]
    streams = [(WISP, RAMPS["spirit"][6], 6, 0.2, edge), (MOTE, RAMPS["spirit"][7], 2, 1.6, [(0, 3.5, 0)])]
    return B, anchors, dict(accent=RAMPS["spirit"][5], grip=(-5, 2), focus=(0, 7), streams=streams)


def lordscale_greatblade():
    """The Lordscale Greatblade (A): a blade of black scale that widens as it rises and ends not in a point but in a
    dragon's tail fork, two tines splayed either side of a notch; a gold ridge up its middle, its edges molten gold that
    run out along the tines; a gold-banded ricasso ending in small gold pinions; a guard of two great wings, gold
    pinions over black feathers, the sky's pearl at their heart; a long black-scale grip; a gold claw round a second
    pearl."""
    B = blade([(11, 17, 7, 5), (17, 22, 9, 7), (22, 26, 11, 9)], "lord", "ember_edge", span=(11, 31))
    for sx in (-1, 1):  # the fork's tines
        x = sx * 3.0
        B += chain(x, 25.5, [(16, 5, 3, 4, "lord_plain"), (6, 2, 1, 1, "ember_edge")], f"tine{sx}", side=sx)
        B.append(VX.rbox((sx * 5.6, 28.0, 0), (1, 4, 2), -sx * 16, "ember_edge", f"tine_edge{sx}", span=(11, 31)))
    B += ricasso("lord", "gold")
    B += both(fan(-2.5, 10.5, [(65, 3, 1, 2, "gold"), (95, 2, 1, 1, "gold")], "pinions"))
    B.append(VX.slab(-3.5, 3.5, 1, 5, 5, "lord_dark", "heart"))
    B += frame((0, 3.0, 0), 5, 3, 9, 45, "gold", "bezel")
    B.append(VX.rbox((0, 3.0, 0), (3, 3, 8), 45, "pearl_gem", "pearl"))
    B += both(fan(-3.0, 3.0, [(36, 11, 2, 4, "gold"), (54, 10, 2, 3, "feather"), (72, 8, 2, 2, "feather"), (92, 6, 2, 1, "feather")], "wing"))
    B += [grip(-5, 1, "lord_grip"), ring(-2, -1, "gold", part="grip_ring")]
    B.append(ring(-6, -5, "gold", part="claw"))
    B.append(VX.slab(-1.5, 1.5, -9, -6, 3, "pearl_gem", "pearl_low"))
    for sx, sz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        B.append(VX.box(sx * 2 - 0.5, sx * 2 + 0.5, -9, -6, sz * 2 - 0.5, sz * 2 + 0.5, "gold", "talons"))
    anchors = [((0, 3.0, 0), 2.6, RAMPS["pearl"][4], 1), ((0, -7.5, 0), 2.2, RAMPS["pearl"][4], 1)]
    streams = [(EMBER, RAMPS["ember"][6], 8, 0.12, [(side * w, y, 0.0) for w, y in ((3.5, 12.0), (3.5, 15.0), (4.5, 18.0), (4.5, 21.0),
                                                                              (5.5, 23.0), (5.5, 25.0))]) for side in (-1, 1)]
    return B, anchors, dict(accent=RAMPS["ember"][5], grip=(-5, 1), focus=(0, 7), streams=streams)


def sovereign_greatblade():
    """The Sovereign Greatblade (S): the Court's zweihander. A broad straight ivory blade between gold edges, the Court's
    runes burning down a crimson channel up its middle, a long point; a ricasso bound in void weave and gold, ending in
    two pairs of gold lugs shaped like flames; the guard a wide gold crown, its points rising along it and its ends
    curling up, a great crimson stone in a raised gold diamond at its heart; a long void-weave grip; a gold star round
    a second stone."""
    B = blade([(11, 26, 9, 7), (26, 28, 7, 5), (28, 30, 5, 3), (30, 32, 3, 1), (32, 33, 1, 1)], "ivory", "gold_edge",
              span=(12, 27), column=0)
    B += ricasso("ivory", "weave_grip", wraps=((6, 10),))
    for sx in (-1, 1):  # the flame lugs: each a tongue of gold licking up and out, a smaller one inside it
        B += chain(sx * 2.5, 10.5, [(75, 3, 2, 4, "gold"), (35, 3, 2, 3, "gold"), (0, 3, 1, 2, "gold"), (-25, 2, 1, 1, "gold")],
                   f"flame{sx}", side=sx)
        B += chain(sx * 2.5, 8.5, [(80, 2, 1, 2, "gold"), (35, 2, 1, 1, "gold")], f"flame_low{sx}", side=sx)
    B.append(VX.slab(-7.5, 7.5, 3, 5, 5, "gold", "crown"))
    for x in (-6.2, -3.0, 3.0, 6.2):  # the crown's points along the bar
        B.append(VX.rbox((x, 5.4, 0), (2, 2, 3), 45, "gold", f"point{x}"))
    B += both(chain(-7.0, 4.0, [(20, 3, 2, 4, "gold"), (-15, 2, 1, 2, "gold")], "crown_ends"))
    B += frame((0, 4.0, 0), 5, 3, 9, 45, "gold", "frame")
    B.append(VX.rbox((0, 4.0, 0), (3, 3, 7), 45, "crimson_gem", "heart"))
    B += two_hands("weave_grip", "gold", y1=2)
    B.append(VX.slab(-2, 2, -9.5, -5.5, 3, "gold", "star"))
    B.append(VX.rbox((0, -7.5, 0), (4, 4, 4), 45, "gold", "star_turned"))
    B.append(VX.rbox((0, -7.5, 0), (2, 2, 7), 0, "crimson_gem", "pommel_gem"))
    anchors = [((0, 4.0, 0), 2.8, RAMPS["crimson"][5], 1), ((0, -7.5, 0), 2.0, RAMPS["crimson"][5], 1)]
    streams = [(MOTE, RAMPS["crimson"][6], 4, 2.0, [(0, 4.0, 0)]), (MOTE, RAMPS["crimson"][6], 2, 1.6, [(0, -7.5, 0)]),
               (MOTE, RAMPS["gold"][6], 4, 0.3, [(0.0, 12.5, 0.0), (0.0, 26.5, 0.0)])]
    return B, anchors, dict(accent=RAMPS["crimson"][4], grip=(-5, 2), focus=(0, 7), streams=streams)


DESIGNS.update({("greatblade", "E"): wildhide_greatblade, ("greatblade", "D"): beastbound_greatblade,
                ("greatblade", "C"): gatewrought_greatblade, ("greatblade", "B"): veilwoven_greatblade,
                ("greatblade", "A"): lordscale_greatblade, ("greatblade", "S"): sovereign_greatblade})
HOLDS["greatblade"] = ([0, -72, 30], [-2.5, -1.0, -0.6], 0.4)


# =====================================================================================================================
# Batch 2: the rapier, the daggers, the katana and the twinblades

# ---- Rapiers: a long needle 3 texels wide stepping to a point 1 wide, about 31 long; a swept, cup or ring hilt over
# the hand; slender and elegant --------------------------------------------------------------------------------------

def needle(core, edge, y0=4, y1=22, tip=28, **data):
    """A rapier's blade: three wide with a raised ridge, then the ridge alone to a long point."""
    return blade([(y0, y1, 3, 1), (y1, tip, 1, 1)], core, edge, **data)


def bow(mat, x=2.0, y=4.0, y_end=-2.5, part="bow", depth=1):
    """A knuckle bow: from the guard round the outside of the hand down to the pommel."""
    mid = max(1, int(round(y - y_end - 2.5)))
    return chain(x, y, [(150, 2, 1, depth, mat), (178, mid, 1, depth, mat), (205, 2, 1, depth, mat)], part, side=1, back=0.3,
                 stagger=0.05)


def wildhide_rapier():
    """The Wildhide Rapier (E): a long three-edged spike of knapped flint set in an antler, the antler's tines swept
    round the hand for its hilt (one up beside the blade, one back over the knuckles); lashed with sinew; a bone grip
    wound in hide; an amber bead."""
    B = needle("flint", "flint_edge")
    B.append(VX.slab(-1.5, 1.5, 3, 5, 3, "sinew", "lashing"))
    B.append(VX.slab(-2.5, 2.5, 2, 3, 3, "antler", "collar"))
    B += chain(-2.0, 2.5, [(60, 3, 1, 2, "antler"), (15, 3, 1, 1, "antler")], "tine")
    B += bow("antler", 2.0, 2.5, -2.5, depth=2)
    B.append(grip(-2, 2, "bone_grip"))
    B.append(VX.slab(-1.5, 1.5, -5, -2, 3, "amber_gem", "bead"))
    anchors = [((0, -3.5, 0), 1.8, RAMPS["amber"][5], 1)]
    return B, anchors, dict(accent=RAMPS["amber"][4], grip=(-2, 2), focus=(0, 3))


def beastbound_rapier():
    """The Beastbound Rapier (D): a narwhal's tusk for a blade, a long ivory needle with its spiral winding up it,
    stepping thinner as it goes; a riveted bronze cup over the hand; bronze quillons with a tooth at each end; a pelt
    grip; the moss stone in the pommel."""
    B = [VX.slab(-1.5, 1.5, 4, 17, 3, "narwhal", "blade"), VX.slab(-1, 1, 17, 23, 2, "narwhal", "blade"),
         VX.slab(-0.5, 0.5, 23, 28, 1, "narwhal", "blade")]
    B += [VX.slab(-3.5, 3.5, 3, 4, 3, "bronze", "cup"), VX.slab(-2.5, 2.5, 3, 4, 5, "bronze", "cup"),
          VX.slab(-1.5, 1.5, 3, 4, 7, "bronze", "cup")]
    B += [VX.slab(-4.5, -3.5, 1, 3, 3, "bronze_back", "rim"), VX.slab(3.5, 4.5, 1, 3, 3, "bronze_back", "rim'")]
    B.append(VX.slab(-6.5, 6.5, 4, 5, 1, "bronze", "quillons"))
    for x in (-6.0, 6.0):
        B.append(VX.rbox((x, 6.0, 0), (1, 2, 2), 0, "tooth", f"tooth{x}"))
    B.append(grip(-2, 3, "pelt"))
    B.append(ring(-3, -2, "bronze", part="claw"))
    B.append(VX.rbox((0, -4.0, 0), (2, 2, 3), 45, "moss_gem", "stone"))
    anchors = [((0, -4.0, 0), 1.6, RAMPS["moss"][6], 1)]
    return B, anchors, dict(accent=RAMPS["moss"][5], grip=(-2, 3), focus=(0, 3))


def gatewrought_rapier():
    """The Gatewrought Rapier (C): a needle of gate-steel, the signal circuit lit up its ridge; a ring hilt of brass
    gate-arcs, a square ring beside the blade's root on each side; straight brass quillons with pylon caps; a brass
    knuckle bow; a slate grip; a signal crystal for a pommel."""
    B = needle("slate_rib", "slate_flat", span=(4, 26), column=0)
    B += frame((-3.0, 6.0, 0), 3, 1, 1, 0, "brass", "ring")
    B += frame((3.0, 6.0, 0), 3, 1, 1, 0, "brass", "ring'")
    B.append(VX.slab(-6.5, 6.5, 3, 4, 1, "brass", "quillons"))
    B += [VX.slab(-7.5, -5.5, 2.5, 4.5, 3, "pylon", "cap", column=-7, span=(2, 5)), VX.slab(5.5, 7.5, 2.5, 4.5, 3, "pylon", "cap'", column=7, span=(2, 5))]
    B.append(VX.slab(-2.5, 2.5, 2, 3, 3, "brass", "collar"))
    B += bow("brass", 2.0, 2.5, -2.5)
    B.append(grip(-2, 2, "slate_grip"))
    B.append(ring(-3, -2, "brass", width=3, depth=3, part="neck"))
    B.append(VX.rbox((0, -4.4, 0), (2, 2, 2), 45, "signal_gem", "crystal"))
    anchors = [((0, -4.4, 0), 1.6, RAMPS["signal"][6], 1)]
    return B, anchors, dict(accent=RAMPS["signal"][5], grip=(-2, 2), focus=(0, 4), zoom=1.7)


def veilwoven_rapier():
    """The Veilwoven Rapier (B): a needle of near-black Veil-steel, one edge living spirit light; a swept hilt of
    mirror crescents curling round the hand, one up, two down and back; the spirit stone at the guard; an obsidian grip;
    a mirror drop."""
    B = needle("veil", "veil_edge", span=(4, 28))
    B.append(VX.slab(-2.5, 2.5, 2, 4, 3, "mirror", "guard"))
    B.append(VX.rbox((0, 3.0, 0), (2, 2, 5), 45, "spirit_gem", "stone"))
    B += chain(-2.5, 3.6, [(70, 3, 1, 2, "mirror"), (25, 3, 1, 1, "mirror")], "crescent_up")
    B += chain(-2.5, 2.0, [(120, 3, 1, 2, "mirror"), (165, 3, 1, 1, "mirror"), (205, 2, 1, 1, "mirror")], "crescent_down", stagger=0.05)
    B += bow("mirror", 2.5, 2.5, -2.5)
    B.append(grip(-2, 2, "obsidian"))
    B.append(VX.rbox((0, -3.4, 0), (2, 2, 3), 45, "mirror", "drop"))
    anchors = [((0, 3.0, 0), 1.6, RAMPS["spirit"][6], 1)]
    streams = [(WISP, RAMPS["spirit"][6], 3, 0.15, [(-1.5, y + 0.5, 0.0) for y in range(5, 22, 3)]),
               (MOTE, RAMPS["spirit"][7], 2, 1.0, [(0, 3.0, 0)])]
    return B, anchors, dict(accent=RAMPS["spirit"][5], grip=(-2, 2), focus=(0, 3), streams=streams)


def lordscale_rapier():
    """The Lordscale Rapier (A): a needle of black scale, its edges molten gold that run to the point; a gold cup hilt
    of two wings folded down over the hand, black feathers under gold pinions; a gold knuckle bow; a black-scale grip;
    the pearl for a pommel."""
    B = needle("lord", "ember_edge", span=(4, 28))
    B.append(VX.slab(-2.5, 2.5, 2, 4, 3, "gold", "guard"))
    B += both(fan(-2.0, 3.5, [(110, 5, 2, 4, "gold"), (140, 5, 2, 2, "feather"), (168, 4, 1, 1, "feather")], "wing"))
    B += bow("gold", 2.5, 2.0, -2.5, depth=3)
    B.append(grip(-2, 2, "lord_grip"))
    B.append(ring(-3, -2, "gold", width=3, depth=3, part="neck"))
    B.append(VX.slab(-1.5, 1.5, -6, -3, 3, "pearl_gem", "pearl"))
    anchors = [((0, -4.5, 0), 1.8, RAMPS["pearl"][4], 1)]
    streams = [(EMBER, RAMPS["ember"][6], 4, 0.1, [(side * 1.5, y + 0.5, 0.0) for y in range(5, 22, 3)]) for side in (-1, 1)]
    return B, anchors, dict(accent=RAMPS["ember"][5], grip=(-2, 2), focus=(0, 3), streams=streams)


def sovereign_rapier():
    """The Sovereign Rapier (S): an ivory needle edged in gold, the Court's runes burning up its ridge; a gold swept
    hilt: a ring either side of the blade's root, quillons ending in crown points, a knuckle bow; the crimson stone at
    the guard; a void-weave grip bound in gold; a small gold star."""
    B = needle("ivory", "gold_edge", span=(5, 22), column=0)
    B += frame((-3.0, 6.0, 0), 3, 1, 1, 0, "gold", "ring")
    B += frame((3.0, 6.0, 0), 3, 1, 1, 0, "gold", "ring'")
    B.append(VX.slab(-6.5, 6.5, 3, 4, 1, "gold", "quillons"))
    for x in (-6.5, 6.5):
        B.append(VX.rbox((x, 4.0, 0), (2, 2, 3), 45, "gold", f"crown{x}"))
    B.append(VX.slab(-2.5, 2.5, 2, 3, 3, "gold", "collar"))
    B += frame((0, 3.0, 0), 3, 1, 5, 45, "gold", "setting")
    B.append(VX.rbox((0, 3.0, 0), (1, 1, 4), 45, "crimson_gem", "stone"))
    B += bow("gold", 2.5, 2.5, -2.5)
    B.append(grip(-2, 2, "weave_grip"))
    B.append(VX.slab(-1.5, 1.5, -5, -2, 2, "gold", "star"))
    B.append(VX.rbox((0, -3.5, 0), (3, 3, 4), 45, "gold", "star_turned"))
    anchors = [((0, 3.0, 0), 1.6, RAMPS["crimson"][5], 1)]
    streams = [(MOTE, RAMPS["crimson"][6], 3, 1.2, [(0, 3.0, 0)]), (MOTE, RAMPS["gold"][6], 2, 0.2, [(0, 6.5, 0.0), (0, 20.5, 0.0)])]
    return B, anchors, dict(accent=RAMPS["crimson"][4], grip=(-2, 2), focus=(0, 3), streams=streams)


DESIGNS.update({("rapier", "E"): wildhide_rapier, ("rapier", "D"): beastbound_rapier, ("rapier", "C"): gatewrought_rapier,
                ("rapier", "B"): veilwoven_rapier, ("rapier", "A"): lordscale_rapier, ("rapier", "S"): sovereign_rapier})
HOLDS["rapier"] = ([0, -72, 30], [-1.5, -1.0, -0.6], 0.48)


# ---- Daggers: short and quick, about 15 texels: a wide-backed or curved blade about 9 long, a knuckle guard round
# the hand ------------------------------------------------------------------------------------------------------------

def wildhide_daggers():
    """The Wildhide Daggers (E): a skinning knife of knapped flint, thick and straight along its back, its pale edge
    sweeping up to a point at the back; lashed with sinew; an antler tine bent round the knuckles for a guard; a bone
    grip wound in hide; an amber nub."""
    B = [VX.slab(-2.5, 0.5, 3, 9, 3, "flint", "blade"), VX.slab(-2.5, 2.5, 3, 9, 1, "flint_edge", "blade"),
         VX.slab(-2.5, -0.5, 9, 10, 3, "flint", "blade"), VX.slab(-2.5, 1.5, 9, 10, 1, "flint_edge", "blade"),
         VX.slab(-2.5, -1.5, 10, 11, 3, "flint", "blade"), VX.slab(-2.5, 0.5, 10, 11, 1, "flint_edge", "blade"),
         VX.slab(-2.5, -1.5, 11, 12, 1, "flint_edge", "blade")]
    B.append(VX.slab(-3.5, 3.5, 2, 3, 3, "sinew", "lashing"))
    B += bow("antler", 3.0, 2.5, -2.5, depth=2)
    B.append(grip(-2, 2, "bone_grip"))
    B.append(VX.slab(-1.5, 1.5, -4, -2, 3, "amber_gem", "nub"))
    anchors = [((0, -3.0, 0), 1.4, RAMPS["amber"][5], 1)]
    return B, anchors, dict(accent=RAMPS["amber"][4], grip=(-2, 2), focus=(0, 4), zoom=1.7)


def beastbound_daggers():
    """The Beastbound Daggers (D): a boar's tusk, curved as it grew, honed along its inside; socketed in a riveted
    bronze collar; a bronze knuckle bow; a pelt grip; the moss stone for a pommel."""
    B = chain(0.0, 3.0, [(5, 3, 3, 3, "tusk"), (24, 3, 3, 3, "tusk"), (46, 3, 2, 2, "tusk"), (66, 2, 1, 1, "horn_tip")], "blade",
              back=0.3, stagger=0.05)
    B.append(VX.slab(-2.5, 2.5, 2, 4, 5, "bronze", "collar"))
    for z0 in (2.5, -3.5):
        B.append(VX.box(-0.5, 0.5, 2.5, 3.5, z0, z0 + 1, "bronze", "rivets"))
    B += bow("bronze", 3.0, 2.5, -2.5)
    B.append(grip(-2, 2, "pelt"))
    B.append(VX.rbox((0, -3.4, 0), (2, 2, 3), 45, "moss_gem", "stone"))
    anchors = [((0, -3.4, 0), 1.4, RAMPS["moss"][6], 1)]
    return B, anchors, dict(accent=RAMPS["moss"][5], grip=(-2, 2), focus=(0, 4), zoom=1.7)


def gatewrought_daggers():
    """The Gatewrought Daggers (C): a wide squared blade of gate-steel, its edges mirror-honed, the circuit lit up its
    rib; its guard a little gate: a squared brass bow from the cross-bar round the hand to the pommel; a slate grip; a
    signal crystal."""
    B = blade([(3, 9, 5, 1), (9, 10, 3, 1), (10, 11, 1, 1)], "slate_rib", "slate_flat", span=(3, 10), column=0, branch=1)
    B.append(VX.slab(-3.5, 3.5, 2, 3, 3, "brass", "bar"))
    B += [VX.slab(3.5, 4.5, -3, 3, 1, "brass", "bow"), VX.slab(1.5, 3.5, -3, -2, 1, "brass", "bow_foot")]
    B.append(grip(-2, 2, "slate_grip"))
    B.append(ring(-3, -2, "brass", width=3, depth=3, part="neck"))
    B.append(VX.rbox((0, -4.4, 0), (2, 2, 2), 45, "signal_gem", "crystal"))
    anchors = [((0, -4.4, 0), 1.4, RAMPS["signal"][6], 1)]
    return B, anchors, dict(accent=RAMPS["signal"][5], grip=(-2, 2), focus=(0, 4), zoom=1.7)


def veilwoven_daggers():
    """The Veilwoven Daggers (B): a claw of near-black Veil-steel curving forward like a talon, its tip living spirit
    light; the spirit stone at its root in a mirror guard; a mirror ring at the pommel for a finger; an obsidian
    grip."""
    B = chain(0.0, 3.0, [(8, 3, 3, 3, "veil"), (30, 3, 3, 3, "veil"), (58, 3, 2, 2, "veil"), (88, 2, 1, 1, "spirit_edge")], "blade",
              side=1, back=0.3, stagger=0.05, span=(3, 11))
    B.append(VX.slab(-2.5, 2.5, 2, 4, 5, "mirror", "guard"))
    B.append(VX.rbox((0, 3.0, 0), (2, 2, 7), 45, "spirit_gem", "stone"))
    B.append(grip(-2, 2, "obsidian"))
    B += frame((0, -4.0, 0), 4, 2, 2, 0, "mirror", "ring")
    anchors = [((0, 3.0, 0), 1.4, RAMPS["spirit"][6], 1)]
    streams = [(WISP, RAMPS["spirit"][6], 3, 0.15, edge_points(B[:4], 1)), (MOTE, RAMPS["spirit"][7], 1, 0.9, [(0, 3.0, 0)])]
    return B, anchors, dict(accent=RAMPS["spirit"][5], grip=(-2, 2), focus=(0, 4), zoom=1.7, streams=streams)


def lordscale_daggers():
    """The Lordscale Daggers (A): a broad leaf of black scale, a gold ridge up it, its edges molten gold; a gold claw
    for its guard, two talons curling down over the knuckles; a black-scale grip; the pearl for a pommel."""
    B = blade([(3, 8, 5, 3), (8, 10, 3, 1), (10, 11, 1, 1)], "lord", "ember_edge", span=(3, 11))
    B.append(VX.slab(-3.5, 3.5, 2, 3, 3, "gold", "guard"))
    for k, x in enumerate((2.5, 3.5)):
        B += chain(x, 2.5, [(150, 2, 1, 2 - k, "gold"), (185, 3, 1, 2 - k, "gold"), (215, 2, 1, 2 - k, "gold")], f"talon{k}",
                   side=1, back=0.3, stagger=0.05)
    B.append(grip(-2, 2, "lord_grip"))
    B.append(VX.slab(-1.5, 1.5, -5, -2, 3, "pearl_gem", "pearl"))
    anchors = [((0, -3.5, 0), 1.6, RAMPS["pearl"][4], 1)]
    streams = [(EMBER, RAMPS["ember"][6], 3, 0.1, [(side * 2.5, y + 0.5, 0.0) for y in (3, 5, 7)]) for side in (-1, 1)]
    return B, anchors, dict(accent=RAMPS["ember"][5], grip=(-2, 2), focus=(0, 4), zoom=1.7, streams=streams)


def sovereign_daggers():
    """The Sovereign Daggers (S): an ivory blade edged in gold, a short crimson channel up it where three runes burn; a
    crown for a guard, its points rising; a gold knuckle bow; the crimson stone in a gold diamond at the guard; a
    void-weave grip; a small gold star."""
    B = blade([(3, 9, 5, 3), (9, 11, 3, 1), (11, 12, 1, 1)], "ivory", "gold_edge", span=(4, 10), column=0)
    B.append(VX.slab(-3.5, 3.5, 2, 3, 3, "gold", "crown"))
    for x in (-3.0, 3.0):
        B.append(VX.rbox((x, 3.4, 0), (1, 1, 2), 45, "gold", f"point{x}"))
    B += frame((0, 2.5, 0), 3, 1, 5, 45, "gold", "setting")
    B.append(VX.rbox((0, 2.5, 0), (1, 1, 4), 45, "crimson_gem", "stone"))
    B += bow("gold", 3.0, 2.5, -2.5)
    B.append(grip(-2, 2, "weave_grip"))
    B.append(VX.slab(-1.5, 1.5, -4, -2, 2, "gold", "star"))
    B.append(VX.rbox((0, -3.0, 0), (2, 2, 4), 45, "gold", "star_turned"))
    anchors = [((0, 2.5, 0), 1.2, RAMPS["crimson"][5], 1)]
    streams = [(MOTE, RAMPS["crimson"][6], 2, 1.0, [(0, 2.5, 0)]), (MOTE, RAMPS["gold"][6], 1, 0.2, [(0, 4.5, 0.0), (0, 9.5, 0.0)])]
    return B, anchors, dict(accent=RAMPS["crimson"][4], grip=(-2, 2), focus=(0, 4), zoom=1.7, streams=streams)


DESIGNS.update({("daggers", "E"): wildhide_daggers, ("daggers", "D"): beastbound_daggers, ("daggers", "C"): gatewrought_daggers,
                ("daggers", "B"): veilwoven_daggers, ("daggers", "A"): lordscale_daggers, ("daggers", "S"): sovereign_daggers})
HOLDS["daggers"] = ([0, -72, 30], [-2.5, 3.0, -0.6], 0.76)


# ---- Katanas: a gently curved single-edged blade of whole-angle turned links (never a staircase), its hamon painted
# along the edge in the tier's accent; a disc tsuba; a long tsuka in diamond wrap; about 34 texels ------------------

KATANA_LINKS = ((0, 5, 3, 2), (3, 5, 3, 2), (6, 5, 3, 2), (9, 4, 3, 2), (13, 3, 2, 2), (20, 2, 1, 1))


def katana_blade(mat, y0=6.5):
    """The blade: links curving back toward the spine (the item's upper left), every other one lifted off the plane."""
    return chain(0.0, y0, [(deg, l, w, d, mat) for deg, l, w, d in KATANA_LINKS], "blade", back=0.3, stagger=0.05, span=(6, 28))


def disc(y0, y1, r, mat, part="tsuba"):
    """A round guard seen from above: an octagon of three crossed slabs, r texels out from the blade."""
    return [VX.slab(-r, r, y0, y1, 2 * r - 4, mat, part), VX.slab(-(r - 1), r - 1, y0, y1, 2 * r - 2, mat, part),
            VX.slab(-(r - 2), r - 2, y0, y1, 2 * r, mat, part)]


def katana_hilt(wrap, cap, collar):
    """The tsuka in its wrap, the kashira over its end, the habaki round the blade's root."""
    return [VX.slab(-1.5, 1.5, -5, 4, 3, wrap, "tsuka"), VX.slab(-2, 2, -6, -5, 4, cap, "kashira"),
            VX.slab(-2, 2, 5, 7, 4, collar, "habaki")]


def wildhide_katana():
    """The Wildhide Katana (E): a curved blade of knapped flint, its edge struck pale, the hamon a line of bone-white
    where the flaking stops; a round tsuba of antler bound with sinew; a tsuka of bone wrapped in hide, the bone showing
    through in diamonds; an antler kashira."""
    B = katana_blade("flint_kat")
    B += disc(4, 5, 3.5, "antler")
    B.append(VX.slab(-2.5, 2.5, 3, 4, 4, "sinew", "seppa"))
    B += katana_hilt("ito_e", "antler", "sinew")
    anchors = [((0, -5.5, 0), 0.8, RAMPS["amber"][5], 1)]
    return B, anchors, dict(accent=RAMPS["amber"][4], grip=(-5, 4), focus=(0, 5))


def beastbound_katana():
    """The Beastbound Katana (D): a curved blade of boar-tusk ivory, banded with its growth, a bronze hamon along its
    edge; a square bronze tsuba with a rivet in each corner; a bronze habaki; a tsuka of bone wrapped in hide; the moss
    stone in the kashira."""
    B = katana_blade("tusk_kat")
    B.append(VX.slab(-3.5, 3.5, 4, 5, 7, "bronze", "tsuba"))
    for sx in (-1, 1):
        for sz in (-1, 1):
            B.append(VX.box(sx * 3 - 0.5, sx * 3 + 0.5, 5, 6, sz * 3 - 0.5, sz * 3 + 0.5, "bronze", "rivets"))
    B += katana_hilt("ito_d", "bronze", "bronze")
    B.append(VX.rbox((0, -6.6, 0), (2, 2, 2), 45, "moss_gem", "stone"))
    anchors = [((0, -6.6, 0), 1.2, RAMPS["moss"][6], 1)]
    return B, anchors, dict(accent=RAMPS["moss"][5], grip=(-5, 4), focus=(0, 5))


def gatewrought_katana():
    """The Gatewrought Katana (C): a curved blade of gate-steel, its edge mirror-honed, the hamon a line of signal
    light running up beside it; a brass tsuba cut like a gear; a brass habaki; a tsuka of slate wrapped over brass; a
    signal crystal in the kashira."""
    B = katana_blade("slate_kat")
    B += disc(4, 5, 3.5, "brass")
    for k in range(8):  # the gear's teeth
        a = math.radians(k * 45 + 22.5)
        B.append(VX.Box((4.2 * math.cos(a), 4.5, 4.2 * math.sin(a)), (0.5, 0.5, 0.5), "brass", "teeth", VX.rotz(0) @ np.array(
            [[math.cos(a), 0, -math.sin(a)], [0, 1, 0], [math.sin(a), 0, math.cos(a)]]), "tooth"))
    B += katana_hilt("ito_c", "brass", "brass")
    B.append(VX.rbox((0, -6.6, 0), (2, 2, 2), 45, "signal_gem", "crystal"))
    anchors = [((0, -6.6, 0), 1.2, RAMPS["signal"][6], 1)]
    return B, anchors, dict(accent=RAMPS["signal"][5], grip=(-5, 4), focus=(0, 5))


def veilwoven_katana():
    """The Veilwoven Katana (B): a curved blade of near-black Veil-steel, mirror-edged, its hamon a line of living
    spirit light; a mirror tsuba of four lobes like a quatrefoil; a mirror habaki; a tsuka of obsidian wrapped over
    mirror; the spirit stone in the kashira."""
    B = katana_blade("veil_kat")
    B += [VX.slab(-4.5, 4.5, 4, 5, 3, "mirror", "tsuba"), VX.slab(-1.5, 1.5, 4, 5, 9, "mirror", "tsuba"),
          VX.slab(-3.5, 3.5, 4, 5, 5, "mirror", "tsuba"), VX.slab(-2.5, 2.5, 4, 5, 7, "mirror", "tsuba")]
    B += katana_hilt("ito_b", "mirror", "mirror")
    B.append(VX.rbox((0, -6.6, 0), (2, 2, 2), 45, "spirit_gem", "stone"))
    anchors = [((0, -6.6, 0), 1.2, RAMPS["spirit"][6], 1)]
    blade_ = B[:6]
    streams = [(WISP, RAMPS["spirit"][6], 3, 0.15, edge_points(blade_, 1)), (MOTE, RAMPS["spirit"][7], 1, 0.9, [(0, -6.6, 0)])]
    return B, anchors, dict(accent=RAMPS["spirit"][5], grip=(-5, 4), focus=(0, 5), streams=streams)


def lordscale_katana():
    """The Lordscale Katana (A): a curved blade of black scale, its edge molten gold, a gold hamon beside it; a round
    gold tsuba with a small gold wing rising from each side; a gold habaki; a tsuka of black scale wrapped over gold;
    the pearl in the kashira."""
    B = katana_blade("lord_kat")
    B += disc(4, 5, 3.5, "gold")
    B += both(fan(-3.0, 5.0, [(50, 4, 1, 2, "gold"), (80, 3, 1, 1, "feather")], "wing"))
    B += katana_hilt("ito_a", "gold", "gold")
    B.append(VX.rbox((0, -6.6, 0), (2, 2, 2), 45, "pearl_gem", "pearl"))
    anchors = [((0, -6.6, 0), 1.2, RAMPS["pearl"][4], 1)]
    streams = [(EMBER, RAMPS["ember"][6], 5, 0.1, edge_points(B[:6], 1))]
    return B, anchors, dict(accent=RAMPS["ember"][5], grip=(-5, 4), focus=(0, 5), streams=streams)


def sovereign_katana():
    """The Sovereign Katana (S): a curved ivory blade, its edge gold, the hamon a line of crimson that burns; a round
    gold tsuba crowned with four points; a gold habaki; a tsuka of void weave wrapped over gold; a crimson stone in the
    gold kashira."""
    B = katana_blade("ivory_kat")
    B += disc(4, 5, 3.5, "gold")
    for x, z in ((-3.0, 0.0), (3.0, 0.0), (0.0, -3.0), (0.0, 3.0)):
        B.append(VX.Box((x, 5.5, z), (0.5, 0.5, 0.5), "gold", "crown", None, "point"))
    B += katana_hilt("ito_s", "gold", "gold")
    B.append(VX.rbox((0, -6.6, 0), (2, 2, 2), 45, "crimson_gem", "stone"))
    anchors = [((0, -6.6, 0), 1.2, RAMPS["crimson"][5], 1)]
    streams = [(MOTE, RAMPS["crimson"][6], 3, 0.25, edge_points(B[:6], 1)), (MOTE, RAMPS["crimson"][6], 1, 0.9, [(0, -6.6, 0)])]
    return B, anchors, dict(accent=RAMPS["crimson"][4], grip=(-5, 4), focus=(0, 5), streams=streams)


DESIGNS.update({("katana", "E"): wildhide_katana, ("katana", "D"): beastbound_katana, ("katana", "C"): gatewrought_katana,
                ("katana", "B"): veilwoven_katana, ("katana", "A"): lordscale_katana, ("katana", "S"): sovereign_katana})
HOLDS["katana"] = ([0, -72, 30], [-1.0, -1.0, -0.6], 0.46)


# ---- Twinblades: two blades from one grip in the middle, the far one turned half round (its edge on the other side),
# each with its own guard; the grip short between them, so it never reads as a staff or a spear -------------------

def twin(top, centre):
    """A twinblade: the near blade and its guard (built upward from the grip), the same turned half round for the far
    one, and the centre (the grip and what sits on it)."""
    return top + spin(top) + centre


def wildhide_twinblades():
    """The Wildhide Twinblades (E): two leaves of knapped flint, each lashed with sinew into an antler fork; a bone grip
    between them wound in hide; an amber bead set in its middle."""
    top = blade([(6, 9, 3, 1), (9, 15, 5, 3), (15, 17, 3, 1), (17, 19, 1, 1)], "flint", "flint_edge")
    top += [VX.slab(-3.5, 3.5, 4, 6, 5, "bone", "collar"), VX.slab(-2.5, 2.5, 6, 8, 5, "sinew", "lashing")]
    top += both(chain(-3.5, 5.0, [(40, 3, 1, 2, "antler"), (12, 2, 1, 1, "antler")], "tines"))
    centre = [grip(-4, 4, "bone_grip"), VX.rbox((0, 0, 0), (2, 2, 5), 45, "amber_gem", "bead")]
    anchors = [((0, 0, 0), 1.6, RAMPS["amber"][5], 1)]
    return twin(top, centre), anchors, dict(accent=RAMPS["amber"][4], grip=(-4, 4), focus=(0, 5))


def beastbound_twinblades():
    """The Beastbound Twinblades (D): two boar tusks curving away from each other (the pair an S), each socketed in a
    riveted bronze collar; a pelt grip; the moss stone at its middle in a bronze ring."""
    top = chain(0.0, 5.5, [(5, 4, 3, 3, "tusk"), (20, 4, 3, 3, "tusk"), (38, 3, 2, 2, "tusk"), (56, 2, 1, 1, "horn_tip")], "blade",
                back=0.3, stagger=0.05)
    top += [VX.slab(-2.5, 2.5, 4, 6, 5, "bronze", "collar")]
    for z0 in (2.5, -3.5):
        top.append(VX.box(-0.5, 0.5, 4.5, 5.5, z0, z0 + 1, "bronze", "rivets"))
    centre = [grip(-4, 4, "pelt"), ring(-1, 1, "bronze", part="hub"), VX.rbox((0, 0, 0), (2, 2, 7), 45, "moss_gem", "stone")]
    anchors = [((0, 0, 0), 1.6, RAMPS["moss"][6], 1)]
    return twin(top, centre), anchors, dict(accent=RAMPS["moss"][5], grip=(-4, 4), focus=(0, 5))


def gatewrought_twinblades():
    """The Gatewrought Twinblades (C): two straight blades of gate-steel, mirror-edged, each between a pair of brass
    pylons; the signal circuit runs the whole length, up one blade, through the slate grip and up the other."""
    top = blade([(6, 16, 5, 1), (16, 17, 3, 1), (17, 18, 1, 1)], "slate_rib", "slate_flat", span=(4, 18), column=0)
    top += [VX.slab(-4.5, 4.5, 4, 6, 5, "brass", "bar")]
    for sx in (-1, 1):
        top.append(VX.slab(*sorted((sx * 3.5, sx * 4.5)), 6, 9, 3, "pylon", f"pylon{sx}", span=(6, 9), column=sx * 4))
        top.append(VX.slab(*sorted((sx * 3.0, sx * 5.0)), 9, 10, 5, "brass", f"cap{sx}"))
    centre = [VX.slab(-1.5, 1.5, -4, 4, 3, "slate_rib", "grip", span=(-4, 4), column=0), ring(-1, 0, "brass", part="hub")]
    anchors = []
    return twin(top, centre), anchors, dict(accent=RAMPS["signal"][5], grip=(-4, 4), focus=(0, 5))


def veilwoven_twinblades():
    """The Veilwoven Twinblades (B): two flame-waved blades of near-black Veil-steel, each with one edge of living
    spirit light (the far blade's on the far side, so the light winds round the pair); mirror crescents swept back off
    each guard; an obsidian grip; the spirit stone at its middle in a mirror frame."""
    segs = [(6, 9, 5, 3), (9, 12, 5, 3), (12, 15, 5, 3), (15, 17, 3, 1), (17, 19, 1, 1)]
    top = blade(segs, "veil", "veil_edge", shift=lambda y: 1 if 9 <= y < 12 else (-1 if 15 <= y < 17 else 0), span=(4, 19))
    top.append(VX.slab(-2.5, 2.5, 4, 6, 5, "mirror", "guard"))
    top += both(chain(-2.5, 5.0, [(70, 3, 2, 3, "mirror"), (110, 3, 1, 2, "mirror")], "crescents"))
    centre = [grip(-4, 4, "obsidian")] + frame((0, 0, 0), 4, 2, 5, 45, "mirror", "frame") + \
        [VX.rbox((0, 0, 0), (2, 2, 4), 45, "spirit_gem", "stone")]
    anchors = [((0, 0, 0), 1.8, RAMPS["spirit"][6], 1)]
    edge = [(-2.5, y + 0.5, 0.0) for y in (6, 7, 12, 13)]
    streams = [(WISP, RAMPS["spirit"][6], 3, 0.18, edge), (WISP, RAMPS["spirit"][6], 3, 0.18, [(-x, -y, z) for x, y, z in edge]),
               (MOTE, RAMPS["spirit"][7], 1, 1.2, [(0, 0, 0)])]
    return twin(top, centre), anchors, dict(accent=RAMPS["spirit"][5], grip=(-4, 4), focus=(0, 5), streams=streams)


def lordscale_twinblades():
    """The Lordscale Twinblades (A): two broad blades of black scale, gold-ridged, their edges molten gold; at each
    guard a single gold wing swept back (the two wings on opposite sides, so the pair turns like a dragon's body);
    the pearl at the grip's middle in a gold bezel; a black-scale grip."""
    top = blade([(6, 15, 5, 3), (15, 17, 3, 1), (17, 18, 1, 1)], "lord", "ember_edge", span=(6, 18))
    top.append(VX.slab(-2.5, 2.5, 4, 6, 5, "gold", "guard"))
    top += fan(-2.0, 5.0, [(55, 7, 2, 3, "gold"), (80, 6, 2, 2, "feather"), (105, 5, 2, 1, "feather")], "wing")
    centre = [grip(-4, 4, "lord_grip"), VX.slab(-2.5, 2.5, -1, 1, 5, "lord_dark", "hub")] + frame((0, 0, 0), 4, 2, 7, 45, "gold", "bezel") + \
        [VX.rbox((0, 0, 0), (2, 2, 6), 45, "pearl_gem", "pearl")]
    anchors = [((0, 0, 0), 1.8, RAMPS["pearl"][4], 1)]
    pts = [(side * 2.5, y + 0.5, 0.0) for side in (-1, 1) for y in (7, 10, 13)]
    streams = [(EMBER, RAMPS["ember"][6], 4, 0.12, pts), (EMBER, RAMPS["ember"][6], 4, 0.12, [(-x, -y, z) for x, y, z in pts])]
    return twin(top, centre), anchors, dict(accent=RAMPS["ember"][5], grip=(-4, 4), focus=(0, 3), streams=streams)


def sovereign_twinblades():
    """The Sovereign Twinblades (S): two ivory blades edged in gold, the Court's runes burning up each; a gold crown at
    each guard, its quillons sweeping up into points; at the very middle of the grip a great crimson stone in a raised
    gold diamond; void weave bound in gold either side of it."""
    top = blade([(6, 16, 5, 3), (16, 18, 3, 1), (18, 19, 1, 1)], "ivory", "gold_edge", span=(7, 16), column=0)
    top.append(VX.slab(-4.5, 4.5, 4, 6, 5, "gold", "crown"))
    top += both(chain(-4.0, 5.0, [(45, 3, 2, 3, "gold"), (12, 2, 1, 2, "gold")], "quillons"))
    centre = [grip(-4, 4, "weave_grip"), VX.slab(-2.5, 2.5, -1.5, 1.5, 5, "gold", "hub")] + frame((0, 0, 0), 5, 3, 9, 45, "gold", "frame") + \
        [VX.rbox((0, 0, 0), (3, 3, 7), 45, "crimson_gem", "heart")]
    anchors = [((0, 0, 0), 2.4, RAMPS["crimson"][5], 1)]
    streams = [(MOTE, RAMPS["crimson"][6], 4, 1.8, [(0, 0, 0)]), (MOTE, RAMPS["gold"][6], 2, 0.25, [(0, 7.5, 0.0), (0, 15.5, 0.0)]),
               (MOTE, RAMPS["gold"][6], 2, 0.25, [(0, -7.5, 0.0), (0, -15.5, 0.0)])]
    return twin(top, centre), anchors, dict(accent=RAMPS["crimson"][4], grip=(-4, 4), focus=(0, 3), streams=streams)


DESIGNS.update({("twinblades", "E"): wildhide_twinblades, ("twinblades", "D"): beastbound_twinblades,
                ("twinblades", "C"): gatewrought_twinblades, ("twinblades", "B"): veilwoven_twinblades,
                ("twinblades", "A"): lordscale_twinblades, ("twinblades", "S"): sovereign_twinblades})
HOLDS["twinblades"] = ([0, -72, 30], [-0.5, 2.0, -0.6], 0.6)


# =====================================================================================================================
# Batch 3: the glaive, the scythe, the gauntlets, the chainblade, the shieldblade, the warstaff and the precursor arm.
# One builder per style over each tier's kit (its materials and stone), with each tier's own ornaments on top.

KIT = {
    "E": dict(metal="bone", tine="antler", plate="bone", wrap="bone_grip", pole="bone", gem="amber_gem", core="flint",
              edge="flint_edge", curve="flint_kat", light="light_amber", bind="sinew", face="hide_face", cuff="hide_face"),
    "D": dict(metal="bronze", tine="horn", plate="bronze_back", wrap="pelt", pole="hide", gem="moss_gem", core="tusk",
              edge="tusk_edge", curve="tusk_kat", light="light_moss", bind="pelt_wrap", face="tusk", cuff="pelt"),
    "C": dict(metal="brass", tine="brass", plate="brass", wrap="slate_grip", pole="slate", gem="signal_gem", core="slate_rib",
              edge="slate_flat", curve="slate_kat", light="light_signal", bind="brass", face="slate_head", cuff="slate_head"),
    "B": dict(metal="mirror", tine="mirror", plate="mirror", wrap="obsidian", pole="obsidian", gem="spirit_gem", core="veil",
              edge="veil_edge", curve="veil_kat", light="light_spirit", bind="mirror", face="veil", cuff="obsidian"),
    "A": dict(metal="gold", tine="gold", plate="gold", wrap="lord_grip", pole="lord_grip", gem="pearl_gem", core="lord",
              edge="ember_edge", curve="lord_kat", light="light_ember", bind="gold", face="lord_face", cuff="lord_face"),
    "S": dict(metal="gold", tine="gold", plate="gold", wrap="weave_grip", pole="weave", gem="crimson_gem", core="ivory",
              edge="gold_edge", curve="ivory_kat", light="light_crimson", bind="gold", face="ivory_tip", cuff="ivory_tip"),
}
GLOW = {"E": RAMPS["amber"][5], "D": RAMPS["moss"][6], "C": RAMPS["signal"][6], "B": RAMPS["spirit"][6], "A": RAMPS["pearl"][4],
        "S": RAMPS["crimson"][5]}
ACCENT = {"E": RAMPS["amber"][4], "D": RAMPS["moss"][5], "C": RAMPS["signal"][5], "B": RAMPS["spirit"][5], "A": RAMPS["ember"][5],
          "S": RAMPS["crimson"][4]}


def look_of(t, grip_, focus, line, stone, **kw):
    """The look of a batch-3 design: its tier's accent, and the light lifting off the high tiers (B wisps off its line
    and motes at its stone, A embers off its line, S motes at its stone and up its line)."""
    d = dict(accent=ACCENT[t], grip=grip_, focus=focus, **kw)
    if t == "B":
        d["streams"] = [(WISP, RAMPS["spirit"][6], 4, 0.18, line), (MOTE, RAMPS["spirit"][7], 2, 1.2, [stone])]
    elif t == "A":
        d["streams"] = [(EMBER, RAMPS["ember"][6], 5, 0.12, line)]
    elif t == "S":
        d["streams"] = [(MOTE, RAMPS["crimson"][6], 3, 1.6, [stone]), (MOTE, RAMPS["gold"][6], 2, 0.25, line[:2])]
    return d


def stone_at(t, centre, size=2, depth=7, deg=45):
    return VX.rbox(centre, (size, size, depth), deg, KIT[t]["gem"], "stone")


def register(style, fn):
    for t in "EDCBAS":
        DESIGNS[(style, t)] = (lambda tt=t: fn(tt))


# ---- Glaive: a pole about 30 with a broad curved blade on top, its edge on the outside of the curve; sweeping reach --

def glaive(t):
    """The Glaive: a broad blade curving back off the top of a long pole, its hardened edge on the outside of the
    curve with the hamon beside it; a socket and langets of the tier's metal; a fluke on the blade's back (E an antler
    hook, D a tusk hook, C a stepped brass spike, B a mirror crescent, A a gold wing, S a crown); a weighted butt."""
    k = KIT[t]
    B = [haft(3, 14, k["pole"]), haft(-4, 3, k["wrap"], "grip"), haft(-7, -4, k["pole"], "pole_low")]
    blade_ = chain(0.0, 16.0, [(0, 5, 5, 3, k["curve"]), (10, 4, 5, 3, k["curve"]), (22, 4, 5, 3, k["curve"]), (36, 3, 4, 2, k["curve"]),
                               (52, 3, 3, 2, k["curve"]), (70, 2, 2, 1, k["curve"])], "blade", back=0.3, stagger=0.05, span=(15, 32))
    B += blade_
    B.append(VX.slab(-2.5, 2.5, 12, 17, 5, "sinew" if t == "E" else k["metal"], "socket"))
    B += langets(7, 12, k["metal"])
    B += [ring(-8, -7, k["metal"], part="butt_ring"), VX.slab(-1.5, 1.5, -10, -8, 3, k["metal"], "butt")]
    stone = (0, 14.5, 0)
    if t == "E":
        B += chain(-2.5, 15.0, [(105, 3, 1, 2, "antler"), (65, 2, 1, 1, "antler")], "fluke")
        B.append(stone_at(t, (0, -10.6, 0), 2, 2))
        stone = (0, -10.6, 0)
    elif t == "D":
        B += chain(-2.5, 15.0, [(110, 3, 2, 2, "horn"), (150, 2, 1, 1, "horn_tip")], "fluke")
        B.append(stone_at(t, stone))
    elif t == "C":
        B += [VX.slab(-5.5, -2.5, 14, 16, 3, "brass", "spike"), VX.slab(-7.5, -5.5, 14.5, 15.5, 1, "brass", "spike_tip")]
        B.append(stone_at(t, stone))
    elif t == "B":
        B += chain(-2.5, 15.0, [(100, 3, 2, 3, "mirror"), (140, 3, 1, 2, "mirror"), (175, 2, 1, 1, "spirit_edge")], "fluke")
        B.append(stone_at(t, stone))
    elif t == "A":
        B += fan(-2.5, 15.0, [(105, 5, 2, 3, "gold"), (130, 4, 2, 2, "feather"), (155, 3, 1, 1, "feather")], "wing")
        B.append(stone_at(t, stone))
    else:
        for x in (-2.0, 2.0):
            B.append(VX.rbox((x, 17.4, 0), (1, 2, 4), -x * 8, "gold", f"crown{x}"))
        B += frame(stone, 4, 2, 9, 45, "gold", "setting")
        B.append(stone_at(t, stone, 2, 8))
    anchors = [(stone, 1.6, GLOW[t], 1)]
    return B, anchors, look_of(t, (-4, 3), (0, 18), edge_points(blade_, 1), stone)


register("glaive", glaive)
HOLDS["glaive"] = ([0, -72, 30], [-0.5, -1.0, -0.6], 0.36)


# ---- Scythe: a long snath with a hand-peg, a great curved blade at right angles off its top, the edge along its
# inside; it cuts spirits, so on the high tiers that edge glows -------------------------------------------------------

SCYTHE_CURVE = {"E": "flint_kat", "D": "tusk_kat", "C": "slate_kat", "B": "scythe_b", "A": "lord_kat", "S": "scythe_s"}


def scythe(t):
    """The Scythe: a slender snath with a peg for the second hand, its blade sweeping out at right angles off the top
    and curving down, the cutting edge along its inside (glowing on the high tiers: spirit, molten gold, crimson); a
    collar of the tier's metal where the blade is set (E sinew and an antler, D bronze with teeth, C brass and a
    crystal, B a mirror crescent and the spirit stone, A a gold wing over the pearl, S a crown and its stone)."""
    k = KIT[t]
    B = [shaft(0, 21, k["pole"]), shaft(-5, 0, k["wrap"], "grip"), shaft(-9, -5, k["pole"], "snath_low")]
    B += [VX.slab(1, 5, 8, 9, 2, k["pole"], "nib"), VX.slab(3, 5, 5, 8, 2, k["wrap"], "nib_grip")]
    blade_ = chain(-1.0, 21.0, [(80, 4, 3, 2, SCYTHE_CURVE[t]), (92, 4, 3, 2, SCYTHE_CURVE[t]), (104, 4, 3, 2, SCYTHE_CURVE[t]),
                                (118, 3, 3, 2, SCYTHE_CURVE[t]), (134, 3, 2, 2, SCYTHE_CURVE[t]), (152, 2, 1, 1, SCYTHE_CURVE[t])],
                   "blade", back=0.3, stagger=0.05, flip=True, span=(10, 24))
    B += blade_
    B.append(VX.slab(-1.5, 1.5, 19, 23, 3, "sinew" if t == "E" else k["metal"], "collar"))
    B += [collar(-10, -9, k["metal"], part="butt"), VX.slab(-0.5, 0.5, -11, -10, 1, k["metal"], "spike")]
    stone = (0, 21.0, 0)
    if t == "E":
        B += chain(1.5, 21.5, [(30, 3, 1, 2, "antler"), (5, 2, 1, 1, "antler")], "tine", side=1)
        stone = (0, -10.5, 0)
        B.append(stone_at(t, (0, -11.6, 0), 2, 2))
        stone = (0, -11.6, 0)
    elif t == "D":
        for y in (19.5, 22.5):
            B.append(VX.rbox((2.0, y, 0), (1, 2, 1), -60, "tooth", f"tooth{y}"))
        B.append(stone_at(t, stone, 2, 5))
    elif t == "C":
        B.append(stone_at(t, stone, 2, 5))
        B.append(VX.slab(1.5, 2.5, 19, 23, 1, "pylon", "slot", column=2, span=(19, 23)))
    elif t == "B":
        B += chain(1.5, 21.5, [(40, 3, 1, 2, "mirror"), (5, 3, 1, 1, "mirror")], "crescent", side=1)
        B.append(stone_at(t, stone, 2, 5))
    elif t == "A":
        B += fan(1.5, 21.5, [(35, 5, 2, 2, "gold"), (60, 4, 1, 1, "feather")], "wing", side=1)
        B.append(stone_at(t, stone, 2, 5))
    else:
        for x in (-1.0, 1.0):
            B.append(VX.rbox((x, 23.4, 0), (1, 2, 2), -x * 10, "gold", f"crown{x}"))
        B.append(stone_at(t, stone, 2, 5))
    anchors = [(stone, 1.4, GLOW[t], 1)]
    return B, anchors, look_of(t, (-5, 0), (-6, 19), edge_points(blade_, -1), stone)


register("scythe", scythe)
HOLDS["scythe"] = ([0, -72, 30], [-3.0, -1.0, -0.6], 0.56)


# ---- Gauntlets: a fist weapon held as an item: an armoured gauntlet (design space: +y the way it punches, +z the back
# of the hand), knuckle plates, a cuff up the forearm; held in first person as if worn ------------------------------

def gauntlets(t):
    """The Gauntlets: a fist of the tier's metal, three knuckle plates across its front (E antler spikes, D tusk spikes,
    C lit circuit plates, B spirit-glowing knuckles, A gold claws, S ivory plates), the stone on a plate on the back of
    the hand, a thumb guard, and a cuff of the tier's own (E laced hide, D pelt, C slate, B obsidian, A black scale, S
    ivory) up the forearm with a rim of metal."""
    k = KIT[t]
    B = [VX.box(-3, 3, 0, 5, -2.5, 2.5, k["plate"], "hand"), VX.box(3, 4, 0, 3, -1.5, 1.5, k["plate"], "thumb"),
         VX.box(-2, 2, 1, 4, 2.5, 3.5, k["metal"], "back_plate"),
         VX.box(-3.5, 3.5, -6, 0, -3, 3, k["cuff"], "cuff"), VX.box(-4, 4, -7, -6, -3.5, 3.5, k["metal"], "rim")]
    knuckle = {"E": "antler", "D": "bronze", "C": "slate_rib", "B": "spirit_edge", "A": "lord_dark", "S": "ivory_tip"}[t]
    for i, x0 in enumerate((-3, -1, 1)):
        B.append(VX.box(x0, x0 + 2, 5, 6, -2, 2, knuckle, f"knuckle{i}", column=x0 + 1, span=(5, 6)))
    B.append(VX.rbox((0, 2.5, 4.0), (2, 2, 1), 45, k["gem"], "stone"))
    B.append(VX.box(-3, 3, 2, 5, -3.5, -2.5, k["wrap"], "fingers"))  # curled under the knuckles: a fist
    if t == "E":
        for x in (-2.0, 0.0, 2.0):
            B.append(VX.rbox((x, 7.0, 0), (1, 2, 1), 0, "antler", f"spike{x}"))
        B.append(VX.box(-4, 4, -3, -2, -3.5, 3.5, "sinew", "lacing"))
    elif t == "D":
        for x in (-2.0, 2.0):
            B.append(VX.rbox((x, 7.0, 0), (1, 2, 1), 0, "horn", f"spike{x}"))
        B.append(VX.box(-4, 4, -2.5, -1.5, -3.5, 3.5, "bronze_back", "strap"))
    elif t == "C":
        B += [VX.box(-4, -3, -5, -1, -2, 2, "pylon", "pylon", column=-4, span=(-5, -1)), VX.box(3, 4, -5, -1, -2, 2, "pylon", "pylon'", column=3, span=(-5, -1))]
    elif t == "B":
        B += [VX.rbox((x, -3.0, 3.5), (1, 3, 1), -x * 12, "mirror", f"fin{x}") for x in (-2.5, 2.5)]
    elif t == "A":
        for x in (-2.0, 0.0, 2.0):
            B.append(VX.rbox((x, 6.8, 0.5), (1, 2, 2), 0, "gold", f"claw{x}"))
        B.append(VX.box(-4, 4, -2.5, -1.5, -3.5, 3.5, "ember_edge", "seam", span=(-3, 0)))
    else:
        for x in (-3.0, 0.0, 3.0):
            B.append(VX.rbox((x, -7.6, 0), (1, 1, 5), 45, "gold", f"crown{x}"))
    stone = (0, 2.5, 4.0)
    anchors = [(stone, 1.2, GLOW[t], 1)]
    line = [(x, 6.0, 0.0) for x in (-2.0, 0.0, 2.0)]
    return B, anchors, look_of(t, (-7, -6), (0, 0), line, stone, zoom=1.6)


register("gauntlets", gauntlets)
# worn, not held: knuckles into the view and the back of the hand up, low at the right where the fist would be (the
# rotation turns the design's +y forward and +z up; found with fp_screen)
HOLDS["gauntlets"] = ([-80, 15, 55], [-4.0, 5.0, -0.6], 0.4)


# ---- Chainblade: a short blade on a chain that leaves the grip in an S, its links alternating face-on and edge-on
# (deep and shallow), a coil of it round the grip; something that lashes out and reels in -------------------------

CHAIN_PATH = (0, 14, 28, 34, 26, 12, -2, -16, -28, -30, -20, -8)
CHAIN_LINKS = {"E": ("hide", "bone"), "D": ("bronze", "bronze_back"), "C": ("brass", "light_signal"), "B": ("mirror", "obsidian"),
               "A": ("gold", "lord_dark"), "S": ("gold", "weave")}


def chain_head(t):
    """The blade at the chain's end, built pointing up from its own origin."""
    k = KIT[t]
    if t == "E":
        return blade([(0, 2, 3, 1), (2, 6, 5, 3), (6, 8, 3, 1), (8, 9, 1, 1)], "flint", "flint_edge") + [VX.slab(-1.5, 1.5, -1, 1, 3, "sinew", "knot")]
    if t == "D":
        return chain(0.0, 0.0, [(0, 3, 3, 3, "tusk"), (28, 3, 2, 2, "tusk"), (56, 3, 1, 1, "horn_tip")], "hook", back=0.3) + \
            [VX.slab(-2, 2, -1, 1, 4, "bronze", "socket")]
    if t == "C":
        return blade([(0, 6, 3, 1), (6, 9, 1, 1)], "slate_rib", "slate_flat", span=(0, 8), column=0) + [VX.slab(-2, 2, -1, 1, 4, "brass", "socket")]
    if t == "B":
        return chain(0.0, 0.0, [(0, 3, 3, 3, "veil"), (32, 3, 2, 2, "veil"), (64, 3, 1, 1, "spirit_edge")], "sickle", back=0.3) + \
            [VX.slab(-2, 2, -1, 1, 4, "mirror", "socket")]
    if t == "A":
        return blade([(0, 1, 3, 1), (1, 5, 5, 3), (5, 7, 3, 1), (7, 8, 1, 1)], "lord", "ember_edge", span=(0, 8)) + \
            [VX.slab(-2, 2, -1, 1, 4, "gold", "socket")]
    return blade([(0, 6, 5, 3), (6, 8, 3, 1), (8, 9, 1, 1)], "ivory", "gold_edge", span=(1, 6), column=0) + \
        [VX.slab(-2, 2, -1, 1, 4, "gold", "socket")]


def chainblade(t):
    """The Chainblade: a short blade (E a flint leaf, D a tusk hook, C a gate-steel dart with its circuit, B a Veil
    sickle tipped in spirit light, A a molten-edged arrowhead, S an ivory leaf with its runes) on a chain leaving the
    grip in an S, its links alternating face-on and edge-on (C's every other link lit); the chain coiled round the
    grip; the tier's stone in the pommel."""
    k = KIT[t]
    a_mat, b_mat = CHAIN_LINKS[t]
    B = [grip(-3, 2, k["wrap"]), ring(2, 3, k["metal"], part="guard")]
    B += [VX.slab(-2.5, 2.5, -2, -1, 5, a_mat, "coil"), VX.slab(-2.5, 2.5, -1, 0, 3, b_mat, "coil'"),
          VX.slab(-2.5, 2.5, 0, 1, 5, a_mat, "coil''")]
    links = []
    for k_, deg in enumerate(CHAIN_PATH):
        if k_ % 2 == 0:
            links.append((deg, 2, 2, 1, a_mat))
        else:
            links.append((deg, 2, 1, 2, b_mat))
    ch = chain(0.0, 3.0, links, "chain", back=0.5)
    B += ch
    end = np.asarray(ch[-1].data["base"]) + np.array([math.sin(math.radians(-CHAIN_PATH[-1])), math.cos(math.radians(-CHAIN_PATH[-1]))]) * 1.5
    head = [VX.Box(b.c + np.array([end[0], end[1], 0.0]), b.h, b.mat, b.part, b.R, b.name, **b.data) for b in chain_head(t)]
    for b in head:
        if "span" in b.data:
            b.data["span"] = tuple(v + end[1] for v in b.data["span"])
    B += turned(head, CHAIN_PATH[-1], (end[0], end[1], 0.0))
    B += [VX.slab(-2, 2, -5, -3, 4, k["metal"], "pommel"), stone_at(t, (0, -4, 0), 2, 6)]
    stone = (0, -4, 0)
    anchors = [(stone, 1.4, GLOW[t], 1)]
    line = [tuple(b.c) for b in ch[2::2]]
    return B, anchors, look_of(t, (-3, 2), (0, 6), line, stone)


register("chainblade", chainblade)
HOLDS["chainblade"] = ([0, -72, 30], [-1.0, -1.0, -0.6], 0.48)


# ---- Shieldblade: a short blade fused with a shield face that dominates its outline: round (E, D) or kite (C, B, A,
# S), with a rim and a boss holding the tier's stone ---------------------------------------------------------------

def round_shield(face, rim, centre=8.0):
    c = centre
    B = [VX.slab(-6.5, 6.5, c - 3, c + 3, 2, face, "face"), VX.slab(-5.5, 5.5, c - 5, c + 5, 2, face, "face"),
         VX.slab(-3.5, 3.5, c - 6, c + 6, 2, face, "face")]
    B += [VX.slab(-3.5, 3.5, c + 5, c + 6, 4, rim, "rim"), VX.slab(-3.5, 3.5, c - 6, c - 5, 4, rim, "rim_low"),
          VX.slab(-6.5, -5.5, c - 3, c + 3, 4, rim, "rim_l"), VX.slab(5.5, 6.5, c - 3, c + 3, 4, rim, "rim_r")]
    for sx in (-1, 1):
        for sy in (-1, 1):
            B.append(VX.rbox((sx * 4.9, c + sy * 4.4, 0), (1, 3, 4), sx * sy * 45, rim, f"rim_c{sx}{sy}"))
    return B


def kite_shield(face, rim, top=14.0):
    """A kite: a face squared above (its rim the full width over it, and down its sides) on a point below (a square
    turned 45, thinner, its rim two turned bars meeting at the point)."""
    B = [VX.slab(-4.5, 4.5, 8, top - 1, 3, face, "face"), VX.rbox((0, 8.0, 0), (8, 8, 1), 45, face, "face_point")]
    B += [VX.slab(-5.5, 5.5, top - 1, top, 4, rim, "rim_top"), VX.slab(-5.5, -4.5, 8, top - 1, 4, rim, "rim_l"),
          VX.slab(4.5, 5.5, 8, top - 1, 4, rim, "rim_r")]
    B += [VX.rbox((-2.12, 5.16, 0), (1, 7, 5), 45, rim, "rim_point"), VX.rbox((2.12, 5.16, 0.05), (1, 7, 5), -45, rim, "rim_point'")]
    return B


def shieldblade(t):
    """The Shieldblade: a shield face that fills the outline (E round, of laced hide on bone with an antler boss; D
    round, of tusk plates in a bronze rim set with teeth; C, B, A, S kite-shaped: slate in brass with a lit circuit
    cross; Veil-steel in mirror with a spirit cross; black scale in gold with a molten cross and two small wings; ivory
    in gold with a crimson rune cross and a crown), a raised boss holding the stone, a short blade rising from its top
    and the grip below it."""
    k = KIT[t]
    B = [grip(-4, 2, k["wrap"]), ring(-5, -4, k["metal"], part="pommel")]
    B += blade([(14, 19, 5, 3), (19, 21, 3, 1), (21, 22, 1, 1)], k["core"], k["edge"], span=(14, 22), column=0)
    if t in "ED":
        B += round_shield(k["face"], "bone" if t == "E" else "bronze")
        B += [VX.slab(-1.5, 1.5, 6.5, 9.5, 5, "antler" if t == "E" else "bronze", "boss"), stone_at(t, (0, 8, 0), 2, 7)]
        if t == "D":
            for a in range(0, 360, 60):
                x, y = 7.4 * math.sin(math.radians(a)), 8 + 7.4 * math.cos(math.radians(a))
                B.append(VX.rbox((x, y, 0), (1, 2, 1), -a, "tooth", f"tooth{a}"))
        stone = (0, 8, 0)
    else:
        rim = {"C": "brass", "B": "mirror", "A": "gold", "S": "gold"}[t]
        cross = {"C": "signal_line", "B": "spirit_edge", "A": "ember_edge", "S": "ivory"}[t]
        B += kite_shield(k["face"], rim)
        B += [VX.slab(-0.5, 0.5, 5, 13, 6, cross, "cross", span=(4, 13), column=0), VX.slab(-4.5, 4.5, 10, 11, 6, cross, "cross_bar", span=(4, 13), column=0)]
        B += [VX.slab(-1.5, 1.5, 9, 12, 7, rim, "boss"), stone_at(t, (0, 10.5, 0), 2, 9)]
        if t == "A":
            B += both(fan(-5.0, 13.5, [(40, 4, 2, 2, "gold"), (70, 3, 1, 1, "feather")], "wing"))
        if t == "S":
            for x in (-4.0, 0.0, 4.0):
                B.append(VX.rbox((x, 14.4, 0), (2, 2, 5), 45, "gold", f"crown{x}"))
        stone = (0, 10.5, 0)
    anchors = [(stone, 1.6, GLOW[t], 1)]
    line = [(0.0, y, 1.0) for y in (5.0, 8.0, 12.0)] if t in "BAS" else []
    return B, anchors, look_of(t, (-4, 2), (0, 9), line, stone)


register("shieldblade", shieldblade)
HOLDS["shieldblade"] = ([0, -72, 30], [-3.0, 1.5, -0.6], 0.52)


# ---- Warstaff: a two-handed staff about 30, weighted caps at both ends, two grips, and a power focus at its head (its
# blows feed power energy, so the focus glows) ----------------------------------------------------------------------

def warstaff(t):
    """The Warstaff: a staff of the tier's own (C's with the circuit lit up it), bound for two hands, weighted caps at
    both ends, and at its head the power focus, a great stone held up (E in antler prongs, D in curled tusks, C floating
    in a brass ring, B in mirror crescents, A cupped by gold wings, S in a raised gold diamond under a crown)."""
    k = KIT[t]
    pole = k["pole"] if t != "C" else "slate_rib"
    B = [haft(-9, -5, pole, "pole_low", span=(-9, 15), column=0), haft(-5, -1, k["wrap"], "grip_low"), haft(-1, 3, pole, "pole_mid", span=(-9, 15), column=0),
         haft(3, 7, k["wrap"], "grip_hi"), haft(7, 15, pole, "pole_hi", span=(-9, 15), column=0)]
    B += [VX.slab(-2.5, 2.5, -12, -9, 5, k["metal"], "foot"), VX.slab(-2.5, 2.5, 15, 17, 5, k["metal"], "head")]
    stone = (0, 20.5, 0)
    if t == "E":
        B += both(chain(-1.5, 16.5, [(25, 4, 1, 2, "antler"), (-12, 3, 1, 1, "antler")], "prongs"))
        B.append(stone_at(t, stone, 3, 3))
    elif t == "D":
        B += both(chain(-2.0, 16.5, [(30, 3, 2, 3, "horn"), (0, 3, 2, 2, "horn"), (-35, 2, 1, 1, "horn_tip")], "tusks"))
        B.append(stone_at(t, stone, 3, 4))
    elif t == "C":
        B += frame((0, 21.0, 0), 7, 5, 3, 0, "brass", "ring")
        B.append(stone_at(t, (0, 21.0, 0), 3, 2))
        stone = (0, 21.0, 0)
    elif t == "B":
        B += both(chain(-2.0, 16.5, [(25, 4, 2, 3, "mirror"), (-10, 3, 1, 2, "mirror"), (-45, 2, 1, 1, "spirit_edge")], "crescents"))
        B.append(stone_at(t, stone, 3, 5))
    elif t == "A":
        B += both(fan(-2.0, 17.0, [(30, 6, 2, 3, "gold"), (55, 5, 2, 2, "feather")], "wings"))
        B.append(stone_at(t, stone, 3, 5))
    else:
        B += frame(stone, 5, 3, 7, 45, "gold", "frame")
        B.append(stone_at(t, stone, 3, 5))
        for x in (-2.0, 0.0, 2.0):
            B.append(VX.rbox((x, 24.6, 0), (1, 2, 3), -x * 10, "gold", f"crown{x}"))
    anchors = [(stone, 2.6, GLOW[t], 1)]
    line = [(0.0, 18.0, 0.0), (0.0, 23.0, 0.0)]
    return B, anchors, look_of(t, (-5, 7), (0, 18), line, stone)


register("warstaff", warstaff)
HOLDS["warstaff"] = ([0, -72, 30], [-3.0, -1.0, -0.6], 0.54)


# ---- Precursor Arm: tech: a gauntleted hilt, an emitter, and a blade of hard light (its emissive is the blade),
# stepped in three stages that light in turn (its reach grows with each step of the chain); each tier another
# precursor technology -------------------------------------------------------------------------------------------

def precursor(t):
    """The Precursor Arm: a hilt cased like a gauntlet round the hand, an emitter, and a blade of hard light in three
    stepped stages, each narrower, each lighting in turn. E a relic the hunters found and bound in bone and sinew round
    an amber light; D a bronze clockwork with a geared ring and moss-green light; C the Gate's own engine, slate and
    brass between two lit pylons, signal blue; B a mirror lens round the spirit stone, spirit light; A a Lord's heart
    engine of black scale, molten vents, molten gold light; S the Court's crown emitter, crimson light."""
    k = KIT[t]
    B = [grip(-2, 3, k["wrap"]), VX.slab(-3.5, -2.5, -2, 4, 5, k["plate"], "case"), VX.slab(2.5, 3.5, -2, 4, 5, k["plate"], "case'"),
         VX.box(-2.5, 2.5, -1, 3, 2.5, 3.5, k["plate"], "knuckles"), VX.slab(-2, 2, -4, -2, 4, k["metal"], "pommel")]
    housing = {"E": "flint", "D": "bronze", "C": "slate_head", "B": "mirror", "A": "lord_dark", "S": "gold"}[t]
    B.append(VX.slab(-2.5, 2.5, 4, 7, 5, housing, "emitter"))
    light = k["light"]
    for st, (y0, y1, w) in enumerate(((7, 13, 5), (14, 19, 3), (20, 25, 1))):
        B.append(VX.slab(-w / 2, w / 2, y0, y1, 1, light, f"stage{st}", stage=st))
        if w > 1:
            B.append(VX.slab(-0.5, 0.5, y0, y1, 3, light, f"core{st}", stage=st))
    B.append(VX.slab(-0.5, 0.5, 25, 27, 1, light, "tip", stage=2))
    stone = (0, 5.5, 0)
    if t == "E":
        B += [VX.slab(-3, 3, 4.5, 6.5, 6, "sinew", "binding")]
        B.append(stone_at(t, (0, -4.6, 0), 2, 2))
        stone = (0, -4.6, 0)
    elif t == "D":
        B += disc(5, 6, 4, "bronze", "gear")
        B.append(stone_at(t, (0, 5.5, 0), 2, 9))
    elif t == "C":
        for sx in (-1, 1):
            B.append(VX.slab(*sorted((sx * 2.5, sx * 4.5)), 4, 9, 3, "pylon", f"pylon{sx}", span=(4, 9), column=sx * 4))
        B.append(stone_at(t, (0, 5.5, 0), 2, 7))
    elif t == "B":
        B += frame((0, 8.0, 0), 7, 5, 9, 45, "mirror", "lens")
        B.append(stone_at(t, (0, 5.5, 0), 2, 7))
    elif t == "A":
        B += [VX.slab(-3.5, -2.5, 4.5, 6.5, 3, "ember_edge", "vent", span=(4, 7)), VX.slab(2.5, 3.5, 4.5, 6.5, 3, "ember_edge", "vent'", span=(4, 7))]
        B.append(stone_at(t, (0, 5.5, 0), 2, 7))
    else:
        for x in (-2.5, 2.5):
            B.append(VX.rbox((x, 7.4, 0), (1, 2, 4), -x * 8, "gold", f"crown{x}"))
        B.append(stone_at(t, (0, 5.5, 0), 2, 7))
    anchors = [(stone, 1.4, GLOW[t], 1)]
    line = [(0.0, y, 0.0) for y in (9.0, 16.0, 22.0)]
    return B, anchors, look_of(t, (-2, 3), (0, 8), line, stone)


register("precursor", precursor)
HOLDS["precursor"] = ([0, -72, 30], [-1.5, -1.0, -0.6], 0.52)


RAMPS.update({
    "spore": ["#0a1a1a", "#0f2e2c", "#145046", "#1c7a64", "#2aa884", "#4fd4a6", "#8af0c8", "#d0fff0"],
    "mycel": ["#2a2026", "#43343c", "#625058", "#86727a", "#a8969a", "#c8b8b6", "#e2d6cc", "#f6eee2"],
    "gill": ["#140a18", "#24122c", "#3a1e44", "#52305e", "#6c4678", "#8a6094", "#a87eae"],
    "bark": ["#140c08", "#22150c", "#352214", "#4a311c", "#634426", "#7e5a34", "#9a7446", "#b48f5e"],
    "sea": ["#06161a", "#0b2a2e", "#114248", "#1a5e62", "#287c7c", "#3e9c94", "#62bcaa", "#9adcc6"],
    "glass": ["#1c2a30", "#2e4650", "#466874", "#64909a", "#8ab8be", "#b4dcdc", "#dcf4f0", "#f8fffc"],
    "ice": ["#0c1a2e", "#16304e", "#22507a", "#3478a6", "#54a0cc", "#84c6e6", "#b8e2f6", "#eaf8ff"],
    "dream": ["#2a0c22", "#4a1638", "#741f52", "#a42c6e", "#d4448c", "#f070ac", "#ffa2cc", "#ffd8ea"],
    "nightmare": ["#06030a", "#0e0716", "#170c24", "#221234", "#2e1946", "#3c2058", "#4c286c"],
    "storm": ["#060c1a", "#0c1830", "#14284c", "#1e3e6c", "#2c5a90", "#4480b8", "#6aa8dc", "#a8d6ff"],
    "volt": ["#10183a", "#20307a", "#3050c0", "#4a7cf0", "#7aaaff", "#b0d4ff", "#e4f2ff", "#ffffff"],
    "void": ["#000000", "#030206", "#07050d", "#0d0a16", "#151022", "#1f1830"],
    "white": ["#3a3e46", "#5c626c", "#868c96", "#aeb4bc", "#d2d6da", "#eceef0", "#ffffff"],
})
RAMP_NAMES[:] = list(RAMPS)
RAMP_RGB[:] = [ramp(n) for n in RAMP_NAMES]


def cracks(ramp_name="ember", tone=5, emit=0.9):
    """Magma showing through obsidian: cracks zigzagging up the stone every four texels, a branch off them every six
    rows, glowing, pulsing up."""
    def d(t):
        if not t.flat:
            return
        on = ((t.X + (t.Y // 2) % 2) % 4 == 0) | ((t.Y % 6 == 0) & ((t.X % 4) == 1))
        t.put(on, ramp_name=ramp_name, tone=tone, emit=emit, metal=0.0, gloss=0.5)
        a, b = t.data.get("span", (0, 30))
        t.flow[on] = np.clip((t.y[on] - a) / (b - a), 0.02, 1.0)
    return d


def gills(spore=("spore", 6)):
    """A mushroom's gills along a curved blade: lines across it every other row (in its own turned frame), and spores
    glowing along the gill edge."""
    def d(t):
        if not t.flat:
            return
        L = (t.P - t.box.c) @ t.box.R
        row = np.floor(L[:, 1] + t.box.h[1] + 1e-6).astype(int)
        col = np.floor(L[:, 0] + t.box.h[0] + 1e-6).astype(int)
        w = int(round(2 * t.box.h[0]))
        if t.data.get("flip"):
            col = w - 1 - col
        t.tone[row % 2 == 0] -= 2
        sp = (col == w - 1) & (row % 3 == 1)
        t.put(sp, ramp_name=spore[0], tone=spore[1], emit=0.85, metal=0.0, gloss=0.4)
        t.flow[sp] = np.clip(row[sp] / 12.0, 0.02, 1.0)
    return d


def honeycomb(fill=("amber", 4, 0.35)):
    """A comb: cells in offset rows, a dark wall round each, honey glowing in their middles."""
    def d(t):
        off = (t.Y // 2) % 2 * 2
        wall = (t.Y % 2 == 0) | (((t.X + off) % 4) == 0)
        t.tone[wall] -= 2
        honey = ~wall & (((t.X + off) % 4) == 2)
        t.put(honey, ramp_name=fill[0], tone=fill[1], emit=fill[2])
    return d


def bands_of_two(ramp_a, tone_a, ramp_b, tone_b, every=2):
    """Stripes round a grip in two materials (a wasp's)."""
    def d(t):
        if abs(t.n[1]) > 0.9:
            return
        a_ = (t.Y // every) % 2 == 0
        t.put(a_, ramp_name=ramp_a, tone=np.clip(tone_a + t.band, 0, len(RAMPS[ramp_a]) - 1))
        t.put(~a_, ramp_name=ramp_b, tone=np.clip(tone_b + t.band, 0, len(RAMPS[ramp_b]) - 1))
    return d


def grain(moss=("moss", 4)):
    """Living root: its grain running down it a texel in two, moss in its creases."""
    def d(t):
        h = np.where(abs(t.n[2]) > 0.9, t.X, np.floor(t.z + 0.5).astype(int))
        t.tone[(h % 2) == 0] -= 1
        m = ((h + 3 * t.Y) % 7) == 0
        t.put(m, ramp_name=moss[0], tone=moss[1])
    return d


def pages():
    """A bound sheaf: page edges a line a row, every third a step darker; the cord across them."""
    def d(t):
        t.tone[t.Y % 3 == 0] -= 1
        t.tone[t.Y % 3 == 1] += 1
    return d


def glassy():
    """Glass: long diagonal glints through it (two lit rows of a slant, then clear)."""
    def d(t):
        if not t.flat:
            return
        g = (t.X - t.Y) % 6
        t.put(g == 0, tone=t.top())
        t.put(g == 1, tone=t.top() - 1)
    return d


def facets():
    """Ice: facets a step lighter on one slant, fracture lines a step darker on the other, a cold light in it."""
    def d(t):
        t.tone[((t.X + t.Y) % 4) == 0] += 1
        t.tone[((t.X - 2 * t.Y) % 7) == 0] -= 2
    return d


def bolts(ramp_name="volt", tone=6, emit=0.9):
    """Lightning caught in storm-steel: zigzag lines lit across the flats."""
    def d(t):
        if not t.flat:
            return
        on = ((t.Y + np.abs(t.X)) % 5 == 0) & ((t.Y // 5) % 2 == 0)
        t.put(on, ramp_name=ramp_name, tone=tone, emit=emit, metal=0.0, gloss=0.4)
        a, b = t.data.get("span", (0, 30))
        t.flow[on] = np.clip((np.abs(t.y[on]) - abs(a)) / max(abs(b - a), 1), 0.02, 1.0)
    return d


def stars(ramp_name="white", tone=6, every=5, emit=0.6):
    """A night's stars: a lit texel here and there on the dark."""
    def d(t):
        on = ((t.X * 3 + t.Y * 7) % every) == 0
        t.put(on, ramp_name=ramp_name, tone=tone, emit=emit)
    return d


GLYPH_ZERO = ("###", "#.#", "#.#", "#.#", "###")


def glyph(rows_, ramp_name="crimson", tone=3, emit=0.7):
    """A glyph (rows of # and .) drawn in light on the middle of a face (Unit Zero's countdown, at zero)."""
    pat = np.array([[ch == "#" for ch in r] for r in rows_])

    def d(t):
        if not t.flat:
            return
        f = t.f
        gh, gw = pat.shape
        i0, j0 = (f.su - gw) // 2, (f.sv - gh) // 2
        gi, gj = f.i - i0, f.j - j0
        ok = (gi >= 0) & (gi < gw) & (gj >= 0) & (gj < gh)
        on = np.zeros(t.k, dtype=bool)
        on[ok] = pat[gh - 1 - gj[ok], gi[ok] if t.n[2] > 0 else gw - 1 - gi[ok]]
        t.put(on, ramp_name=ramp_name, tone=tone, emit=emit, metal=0.0, gloss=0.4)
    return d


def dots(ramp_name, tone, every=2, emit=0.6):
    """Stones set along a rim, every other texel (the Mirror Knight's diamonds)."""
    def d(t):
        on = ((t.X + t.Y) % every) == 0
        t.put(on, ramp_name=ramp_name, tone=tone, emit=emit, metal=0.3, gloss=1.0)
    return d


MATS.update({
    "l_bell": Mat("bronze", 5, 0.5, 0.85, details=(rows(4, 1, 1),)),
    "l_hollow": Mat("bronze", 0, 0.2, 0.3, bevel=False),
    "l_toll": Mat("amber", 5, 0.0, 0.5, emit=0.85, details=(flow_up(17, 28),), bevel=False),
    "l_clapper": Mat("bronze", 3, 0.6, 0.8),
    "l_mycel": Mat("mycel", 5, 0.0, 0.3, details=(rows(3, -1),)),
    "l_cap": Mat("gill", 4, 0.0, 0.35, details=(stars("spore", 6, 4, 0.7),)),
    "l_gill": Mat("gill", 4, 0.0, 0.5, details=(gills(),)),
    "l_spore_gem": Mat("spore", 5, 0.2, 1.0, emit=0.6, details=(gem(),), contact=2),
    "l_magma": Mat("obsidian", 3, 0.4, 0.9, details=(cracks(),)),
    "l_magma_edge": Mat("ember", 5, 0.0, 0.6, emit=0.85, details=(flow_up(8, 23),), bevel=False),
    "l_ember_gem": Mat("ember", 4, 0.2, 1.0, emit=0.6, details=(gem(),), contact=2),
    "l_sting": Mat("amber", 4, 0.1, 0.95, emit=0.3, details=(streak(period=6, run=3), flow_up(3, 15))),
    "l_comb": Mat("amber", 3, 0.0, 0.6, details=(honeycomb(),)),
    "l_wasp": Mat("amber", 4, 0.0, 0.4, details=(bands_of_two("obsidian", 2, "amber", 4),)),
    "l_wing": Mat("glass", 5, 0.2, 1.0, details=(glassy(),)),
    "l_bark": Mat("bark", 4, 0.0, 0.3, details=(grain(),)),
    "l_shrine": Mat("ivory", 5, 0.1, 0.6, details=(panel("gold", 4, margin=1, metal=0.5, gloss=0.85),)),
    "l_relic": Mat("moss", 6, 0.1, 1.0, emit=0.85, details=(gem(),), contact=2),
    "l_sea": Mat("sea", 4, 0.5, 0.8),
    "l_sea_blade": Mat("sea", 5, 0.6, 0.9, details=(streak(),)),
    "l_sea_tip": Mat("sea", 7, 0.0, 0.6, emit=0.75, details=(flow_up(20, 34),), bevel=False),
    "l_pages": Mat("bone", 6, 0.0, 0.3, details=(pages(),)),
    "l_sea_gem": Mat("sea", 6, 0.2, 1.0, emit=0.6, details=(gem(),), contact=2),
    "l_piston": Mat("slate", 4, 0.8, 0.9, details=(rows(2, -1),)),
    "l_furnace": Mat("ember", 5, 0.1, 0.9, emit=0.85, details=(gem(),), contact=2),
    "l_glass": Mat("glass", 5, 0.3, 1.0, emit=0.15, details=(glassy(),)),
    "l_glass_gem": Mat("glass", 6, 0.2, 1.0, emit=0.5, details=(gem(),), contact=2),
    "l_iron": Mat("slate", 2, 0.6, 0.7),
    "l_lantern": Mat("amber", 6, 0.0, 0.5, emit=0.9, details=(flow_up(0, 30),), bevel=False),
    "l_ice": Mat("ice", 5, 0.3, 1.0, emit=0.25, details=(facets(),)),
    "l_ice_edge": Mat("ice", 7, 0.2, 1.0, emit=0.6, details=(flow_up(12, 34),), bevel=False),
    "l_hymn": Mat("mirror", 5, 0.7, 0.95, details=(runes(-6, 15, column=0, ramp_name="ice"),)),
    "l_frost_gem": Mat("ice", 6, 0.2, 1.0, emit=0.6, details=(gem(),), contact=2),
    "l_diamond_rim": Mat("mirror", 6, 0.8, 0.95, details=(dots("pearl", 5, 4, 0.5),)),
    "l_diamond": Mat("pearl", 4, 0.3, 1.0, emit=0.55, details=(gem(),), contact=2),
    "l_mirror_face": Mat("mirror", 6, 0.9, 1.0, details=(glassy(),)),
    "l_white": Mat("white", 5, 0.4, 0.9, details=(panel("obsidian", 2, margin=1),)),
    "l_white_plain": Mat("white", 5, 0.4, 0.9),
    "l_black": Mat("obsidian", 2, 0.5, 0.9),
    "l_countdown": Mat("white", 5, 0.4, 0.9, details=(glyph(GLYPH_ZERO),)),
    "l_light_white": Mat("white", 5, 0.0, 0.5, emit=0.95, details=(staged(),), bevel=False),
    "l_dream_kat": Mat("nightmare", 3, 0.5, 0.9, details=(katana(("dream", 6), ("white", 6), edge_emit=0.8, hamon_emit=0.4), stars("dream", 6, 7, 0.6))),
    "l_nightmare": Mat("nightmare", 3, 0.4, 0.8, details=(stars("dream", 6, 6, 0.6),)),
    "l_dream_ito": Mat("nightmare", 2, 0.0, 0.3, details=(ito("nightmare", 2, "dream", 5),)),
    "l_dream_gem": Mat("dream", 5, 0.2, 1.0, emit=0.6, details=(gem(),), contact=2),
    "l_storm": Mat("storm", 3, 0.6, 0.9, details=(bolts(),)),
    "l_storm_edge": Mat("volt", 6, 0.0, 0.5, emit=0.8, details=(flow_up(0, 20),), bevel=False),
    "l_fin": Mat("storm", 4, 0.5, 0.85, details=(rows(2, -1),)),
    "l_volt_gem": Mat("volt", 5, 0.2, 1.0, emit=0.7, details=(gem(),), contact=2),
    "l_void": Mat("void", 1, 0.3, 0.9, bevel=False),
    "l_void_edge": Mat("void", 4, 0.4, 0.9),
    "l_horizon": Mat("ember", 6, 0.0, 0.5, emit=0.95, details=(flow_up(4, 14),), bevel=False),
    "l_horizon_white": Mat("white", 6, 0.0, 0.5, emit=0.9, bevel=False),
})


# =====================================================================================================================
# The Lord-forged: fifteen weapons, one per Gate Lord, each its own outline a step above its tier's forged weapon of
# the same style, glowing in the Lord's colour (the B and A Lords' light lifts off them as the high tiers' does).

def lord_look(lid, grip_, focus, streams=None, **kw):
    d = dict(accent=LORD_COLOUR[lid], grip=grip_, focus=focus, **kw)
    if streams:
        d["streams"] = streams
    return d


def bell_warden_maul():
    """The Bell Warden's Toll: the warden's own bronze bell for a head, laid across the haft, its mouth open to one side
    with the clapper hanging in the dark of it, a ring of tolling light round its shoulder; banded bronze, a small bell
    for a pommel."""
    hy = 22.5
    B = [haft(3, 18, "hide"), haft(-5, 3, "pelt", "grip")]
    for x0, x1, sz in ((1.5, 4.5, 5), (-1.5, 1.5, 7), (-5.5, -1.5, 9)):
        B.append(VX.box(x0, x1, hy - sz / 2, hy + sz / 2, -sz / 2, sz / 2, "l_bell", f"bell{x0}"))
    B += [VX.box(-7.5, -5.5, hy + 3.5, hy + 5.5, -5.5, 5.5, "l_bell", "lip"), VX.box(-7.5, -5.5, hy - 5.5, hy - 3.5, -5.5, 5.5, "l_bell", "lip"),
          VX.box(-7.5, -5.5, hy - 3.5, hy + 3.5, 3.5, 5.5, "l_bell", "lip"), VX.box(-7.5, -5.5, hy - 3.5, hy + 3.5, -5.5, -3.5, "l_bell", "lip")]
    B.append(VX.box(-6.5, -5.5, hy - 3.5, hy + 3.5, -3.5, 3.5, "l_hollow", "hollow"))
    B.append(VX.box(-7.5, -6.5, hy - 1.5, hy + 1.5, -1.5, 1.5, "l_clapper", "clapper"))
    B += [VX.box(-3.5, -2.5, hy + 4.5, hy + 5.5, -5.5, 5.5, "l_toll", "toll"), VX.box(-3.5, -2.5, hy - 5.5, hy - 4.5, -5.5, 5.5, "l_toll", "toll"),
          VX.box(-3.5, -2.5, hy - 4.5, hy + 4.5, 4.5, 5.5, "l_toll", "toll"), VX.box(-3.5, -2.5, hy - 4.5, hy + 4.5, -5.5, -4.5, "l_toll", "toll")]
    B += [VX.box(4.5, 6.5, hy - 1, hy + 1, -0.5, 0.5, "bronze", "loop"), VX.box(6.5, 7.5, hy - 2, hy + 2, -0.5, 0.5, "bronze", "loop_end")]
    B += [VX.slab(-2.5, 2.5, 17, 19, 5, "bronze", "collar"), ring(3, 4, "bronze", part="band")]
    B += [VX.slab(-1.5, 1.5, -7, -5, 3, "bronze", "pommel_bell"), VX.slab(-2.5, 2.5, -8, -7, 5, "bronze", "pommel_lip"),
          VX.rbox((0, -8.6, 0), (1, 1, 1), 0, "l_toll", "pommel_clapper")]
    c = LORD_COLOUR["bell_warden_maul"]
    anchors = [((-7.5, hy, 0), 2.6, c, 1), ((-3.0, hy, 0), 3.4, c, 1)]
    return B, anchors, lord_look("bell_warden_maul", (-5, 3), (-1, hy - 1))


def mother_of_spores_scythe():
    """The Sporemother's Reaping: a snath of pale fungal stalk with shelf fungi growing off it, a great gill-lined blade
    curving off its top under a mushroom cap, spores glowing along its edge; a bulb of fungus at its foot."""
    B = [shaft(0, 21, "l_mycel"), shaft(-5, 0, "l_mycel", "grip"), shaft(-9, -5, "l_mycel", "snath_low")]
    B += [VX.slab(1, 5, 8, 9, 2, "l_mycel", "nib"), VX.slab(3, 5, 5, 8, 2, "l_mycel", "nib_grip")]
    for y, x0, x1 in ((14, 1, 4), (10, -4, -1), (3, 1, 3)):  # shelf fungi
        B.append(VX.slab(x0, x1, y, y + 1, 4, "l_cap", f"shelf{y}"))
    blade_ = chain(-1.0, 21.5, [(78, 5, 4, 2, "l_gill"), (92, 5, 4, 2, "l_gill"), (106, 4, 3, 2, "l_gill"), (122, 4, 3, 2, "l_gill"),
                                (140, 3, 2, 2, "l_gill"), (160, 2, 1, 1, "l_gill")], "blade", back=0.3, stagger=0.05, flip=True)
    B += blade_
    B += [VX.slab(-3.5, 3.5, 22, 24, 7, "l_cap", "cap"), VX.slab(-2.5, 2.5, 24, 25, 5, "l_cap", "cap_top"),
          VX.slab(-1.5, 1.5, 19, 22, 3, "l_mycel", "stalk")]
    B += [VX.slab(-2.5, 2.5, -11, -9, 5, "l_mycel", "bulb"), VX.rbox((0, -12.0, 0), (2, 2, 3), 45, "l_spore_gem", "spore")]
    c = LORD_COLOUR["mother_of_spores_scythe"]
    anchors = [((0, 23.0, 0), 3.0, c, 1), ((0, -12.0, 0), 1.6, c, 1)]
    return B, anchors, lord_look("mother_of_spores_scythe", (-5, 0), (-6, 19))


def ember_tyrant_axe():
    """The Ember Tyrant's Cleft: a great bearded head of obsidian split by magma, the cracks glowing through it and
    its edge molten; an ember heart in the eye; a back-spike of obsidian tipped in fire; an obsidian haft banded with
    ember-lit cracks."""
    B = [haft(3, 19, "l_magma", span=(3, 22)), haft(-4, 3, "hide", "grip")]
    B += bit(-2.5, [(2, 13, 19), (2, 12, 20), (2, 10, 21), (2, 8, 22)], "l_magma", "l_magma_edge", edge_w=2, span=(8, 23))
    B.append(VX.slab(-2.5, 2.5, 12, 20, 5, "obsidian", "eye"))
    B.append(VX.rbox((0, 16.0, 0), (3, 3, 7), 45, "l_ember_gem", "heart"))
    B += chain(2.5, 16.0, [(70, 4, 3, 3, "obsidian"), (50, 3, 2, 2, "obsidian"), (30, 2, 1, 1, "l_magma_edge")], "spike", side=1, back=0.5)
    for y0 in (8, 3, -3):
        B.append(ring(y0, y0 + 1, "l_magma_edge", part=f"band{y0}"))
    B += [VX.slab(-2.5, 2.5, -6, -4, 5, "obsidian", "foot"), VX.rbox((0, -6.6, 0), (2, 2, 3), 45, "l_ember_gem", "foot_gem")]
    c = LORD_COLOUR["ember_tyrant_axe"]
    anchors = [((0, 16.0, 0), 2.6, c, 1), ((-11.0, 15.0, 0), 3.0, c, 1)]
    return B, anchors, lord_look("ember_tyrant_axe", (-4, 3), (-3, 15))


def hive_regent_daggers():
    """The Hive Regent's Sting: a long stinger of amber, honey-gold and lit from within, barbed down both sides; a guard
    of honeycomb; wings of glass for a knuckle guard; a grip striped like a wasp; a drop of amber at its foot."""
    B = [VX.slab(-2.5, 2.5, 3, 7, 3, "l_sting", "sting"), VX.slab(-1.5, 1.5, 7, 11, 3, "l_sting", "sting"),
         VX.slab(-1, 1, 11, 14, 2, "l_sting", "sting"), VX.slab(-0.5, 0.5, 14, 16, 1, "l_sting", "sting")]
    for k, (x, y) in enumerate(((-2.6, 6.6), (2.6, 6.6), (-1.8, 10.4), (1.8, 10.4), (-1.3, 13.2), (1.3, 13.2))):
        B.append(VX.rbox((x, y, 0), (1, 2, 1), (-40 if x < 0 else 40), "obsidian", f"barb{k}"))
    B.append(VX.slab(-3.5, 3.5, 2, 3, 5, "l_comb", "comb"))
    B += both(fan(-3.0, 2.5, [(150, 5, 2, 1, "l_wing"), (190, 4, 2, 2, "l_wing")], "wing"))
    B.append(grip(-2, 2, "l_wasp"))
    B.append(VX.rbox((0, -3.4, 0), (3, 3, 4), 45, "amber_gem", "drop"))
    c = LORD_COLOUR["hive_regent_daggers"]
    anchors = [((0, 8.0, 0), 2.4, c, 1), ((0, -3.4, 0), 1.6, c, 1)]
    return B, anchors, lord_look("hive_regent_daggers", (-2, 2), (0, 5), zoom=1.7)


def root_saint_warstaff():
    """The Root Saint's Reliquary: a staff of living root, twisting as it grew, moss in its grain, tendrils wound round
    it; at its head the saint's temple relic, a little ivory shrine under a gold roof, the relic glowing green-gold in
    its open front, roots climbing round it; root claws at its foot."""
    trunk = chain(0.0, -9.0, [(4, 5, 3, 3, "l_bark"), (-5, 5, 3, 3, "l_bark"), (5, 5, 3, 3, "l_bark"), (-4, 5, 3, 3, "l_bark"),
                              (3, 4, 3, 3, "l_bark")], "trunk", back=0.4, stagger=0.05)
    B = list(trunk)
    for y, d_ in ((-2.0, 1), (4.5, -1), (10.0, 1)):  # tendrils wound round it
        B.append(VX.rbox((0, y, 0), (5, 1, 5), d_ * 18, "l_bark", f"tendril{y}"))
    top = np.asarray(trunk[-1].data["base"]) + np.array([math.sin(math.radians(-3)), math.cos(math.radians(-3))]) * 4
    hy = float(top[1]) + 0.5
    B += [VX.box(-2.5, 2.5, hy, hy + 5, -2.5, 2.5, "l_shrine", "shrine"), VX.box(-3.5, 3.5, hy + 5, hy + 6, -3.5, 3.5, "gold", "roof"),
          VX.box(-2.5, 2.5, hy + 6, hy + 7, -2.5, 2.5, "gold", "roof_top"), VX.box(-0.5, 0.5, hy + 7, hy + 8, -0.5, 0.5, "gold", "finial")]
    B.append(VX.rbox((0, hy + 2.5, 2.0), (2, 2, 2), 45, "l_relic", "relic"))
    B += both(chain(-2.0, hy - 0.5, [(30, 3, 1, 2, "l_bark"), (5, 4, 1, 1, "l_bark")], "climbing"))
    B += both(chain(-1.0, -9.5, [(150, 3, 1, 2, "l_bark"), (175, 2, 1, 1, "l_bark")], "claws"))
    c = LORD_COLOUR["root_saint_warstaff"]
    anchors = [((0, hy + 2.5, 2.0), 2.4, c, 1)]
    return B, anchors, lord_look("root_saint_warstaff", (-5, 7), (0, hy + 2))


def drowned_archivist_spear():
    """The Drowned Archivist's Index: a trident of sea-green bronze, its three prongs lit at the tips like deep water,
    a sea-glass stone in its socket; bound pages of the drowned archive lashed to the shaft below the head, a ribbon
    trailing; barnacled bronze rings down the shaft."""
    B = [shaft(-10, 20, "l_sea"), shaft(-4, 3, "hide", "grip")]
    B += blade([(21, 27, 3, 1), (27, 29, 1, 1)], "l_sea_blade", "l_sea_blade")
    B.append(VX.slab(-0.5, 0.5, 29, 31, 1, "l_sea_tip", "tip"))
    for sx in (-1, 1):
        B += chain(sx * 2.0, 21.0, [(12, 5, 1, 2, "l_sea_blade"), (0, 2, 1, 1, "l_sea_tip")], f"prong{sx}", side=sx)
    B.append(VX.slab(-4.5, 4.5, 20, 22, 3, "l_sea", "crossbar"))
    B.append(collar(17, 20, "l_sea", 4, 4, "socket"))
    B.append(VX.rbox((0, 18.5, 0), (2, 2, 6), 45, "l_sea_gem", "stone"))
    B += [VX.box(-3, 3, 11, 16, -2, 2, "l_pages", "pages"), VX.box(-3.5, 3.5, 13, 14, -2.5, 2.5, "hide", "cord"),
          VX.slab(2.5, 3.5, 7, 13, 1, "hide", "ribbon")]
    for y0 in (8, -6):
        B.append(collar(y0, y0 + 1, "l_sea", 3, 3, f"ring{y0}"))
    B.append(collar(-11, -10, "l_sea", part="butt"))
    c = LORD_COLOUR["drowned_archivist_spear"]
    anchors = [((0, 18.5, 0), 1.8, c, 1), ((0, 29.5, 0), 1.4, c, 1)]
    return B, anchors, lord_look("drowned_archivist_spear", (-4, 3), (0, 21))


def gearwright_colossus_gauntlets():
    """The Gearwright's Grip: a colossus's fist of brass, riveted plates across the knuckles, a great gear on the back of
    the hand round a glowing furnace core, pistons down the sides of the forearm driving it; a riveted cuff."""
    B = [VX.box(-3.5, 3.5, 0, 6, -3, 3, "brass", "hand"), VX.box(3.5, 4.5, 0, 4, -2, 2, "brass", "thumb"),
         VX.box(-3.5, 3.5, 2, 6, -4, -3, "bronze_back", "fingers"),
         VX.box(-4, 4, -7, 0, -3.5, 3.5, "bronze_back", "cuff"), VX.box(-4.5, 4.5, -8, -7, -4, 4, "brass", "rim")]
    for i, x0 in enumerate((-3.5, -1, 1.5)):
        B.append(VX.box(x0, x0 + 2, 6, 7, -2.5, 2.5, "bronze_back", f"knuckle{i}"))
    B += [VX.box(-2.5, 2.5, 0.5, 5.5, 3, 4, "brass", "gear"), VX.Box((0, 3.0, 4.0), (2.5, 2.5, 1.0), "brass", "gear_turned", VX.rotz(45), "gear")]
    B.append(VX.rbox((0, 3.0, 5.5), (2, 2, 1), 45, "l_furnace", "core"))
    for sx in (-1, 1):
        B += [VX.box(*sorted((sx * 4, sx * 6)), -6, -1, -1, 1, "l_piston", f"cylinder{sx}"),
              VX.box(*sorted((sx * 4.5, sx * 5.5)), -1, 2, -0.5, 0.5, "mirror", f"rod{sx}")]
    c = LORD_COLOUR["gearwright_colossus_gauntlets"]
    anchors = [((0, 3.0, 5.5), 1.8, c, 1)]
    return B, anchors, lord_look("gearwright_colossus_gauntlets", (-8, -7), (0, 0), zoom=1.5)


def glass_serpent_rapier():
    """The Glass Serpent's Fang: a needle of glass, clear and glinting, lit faintly from within; its hilt the serpent
    itself in sand-gold, coiled round the hand from a fanged head at the guard down to its tail at the pommel; a glass
    orb at its foot."""
    B = needle("l_glass", "l_glass", y1=23, tip=29)
    coil = chain(1.5, 3.5, [(150, 2, 1, 2, "gold"), (200, 2, 1, 2, "gold"), (250, 2, 1, 2, "gold"), (160, 2, 1, 2, "gold"),
                            (195, 2, 1, 2, "gold"), (230, 2, 1, 1, "gold")], "serpent", side=1, back=0.3, stagger=0.05)
    B += coil
    B += [VX.slab(-2.5, 2.5, 2, 4, 3, "gold", "head"), VX.slab(-3.5, -2.5, 3, 4, 1, "gold", "jaw")]
    for x in (-1.5, 1.5):
        B.append(VX.box(x - 0.5, x + 0.5, 3, 4, 1.5, 2.5, "l_glass_gem", f"eye{x}"))
    B += both(chain(-2.5, 3.5, [(60, 2, 1, 2, "bone")], "fang"))
    B.append(grip(-2, 2, "gold"))
    B.append(VX.rbox((0, -3.6, 0), (3, 3, 4), 45, "l_glass_gem", "orb"))
    c = LORD_COLOUR["glass_serpent_rapier"]
    anchors = [((0, -3.6, 0), 1.8, c, 1), ((0, 16.0, 0), 2.2, c, 1)]
    return B, anchors, lord_look("glass_serpent_rapier", (-2, 2), (0, 3))


def lantern_broker_chainblade():
    """The Lantern Broker's Due: a chain of dark iron hung with little lanterns, every third link one, glowing; at its
    end the broker's great lantern, warm in its iron cage, a hooked blade curling from it; an iron grip with brass."""
    B = [grip(-3, 2, "l_iron"), ring(2, 3, "brass", part="guard"), VX.slab(-2.5, 2.5, -1, 0, 5, "brass", "coil")]
    links = []
    for k_, deg in enumerate(CHAIN_PATH):
        links.append((deg, 2, 2, 1, "l_iron") if k_ % 2 == 0 else (deg, 2, 1, 2, "l_iron"))
    ch = chain(0.0, 3.0, links, "chain", back=0.5)
    B += ch
    for k_ in (2, 5, 8):  # lanterns hung on the chain
        c_ = ch[k_].c
        B += [VX.box(c_[0] - 1, c_[0] + 1, c_[1] - 1, c_[1] + 1, -1.5, 1.5, "l_lantern", f"lamp{k_}"),
              VX.box(c_[0] - 1.5, c_[0] + 1.5, c_[1] + 1, c_[1] + 1.5 + 0.5, -2, 2, "l_iron", f"lamp_cap{k_}")]
    end = np.asarray(ch[-1].data["base"]) + np.array([math.sin(math.radians(-CHAIN_PATH[-1])), math.cos(math.radians(-CHAIN_PATH[-1]))]) * 1.5
    head = [VX.box(-2, 2, 0, 5, -2, 2, "l_lantern", "lantern"),
            VX.box(-2.5, -1.5, 0, 5, -2.5, 2.5, "l_iron", "cage"), VX.box(1.5, 2.5, 0, 5, -2.5, 2.5, "l_iron", "cage"),
            VX.box(-2.5, 2.5, 5, 6, -2.5, 2.5, "l_iron", "cage_top"), VX.box(-2.5, 2.5, -1, 0, -2.5, 2.5, "l_iron", "cage_low")]
    head += chain(0.0, 6.0, [(10, 3, 2, 2, "l_iron"), (40, 3, 2, 1, "slate"), (75, 2, 1, 1, "slate")], "hook", back=0.4, stagger=0.05)
    head = [VX.Box(b.c + np.array([end[0], end[1], 0.0]), b.h, b.mat, b.part, b.R, b.name, **b.data) for b in head]
    B += turned(head, CHAIN_PATH[-1], (end[0], end[1], 0.0))
    B += [VX.slab(-2, 2, -5, -3, 4, "brass", "pommel")]
    c = LORD_COLOUR["lantern_broker_chainblade"]
    hc = turned([VX.Box((end[0], end[1] + 2.5, 0), (0.5, 0.5, 0.5), "l_lantern")], CHAIN_PATH[-1], (end[0], end[1], 0.0))[0].c
    anchors = [(tuple(hc), 3.0, c, 1)] + [(tuple(ch[k_].c), 1.4, c, 1) for k_ in (2, 5, 8)]
    return B, anchors, lord_look("lantern_broker_chainblade", (-3, 2), (0, 8))


def abbot_of_frost_glaive():
    """The Frost Abbot's Hymn: a blade of ice curving off a pole of hymnal silver, its edge colder and brighter, shards
    of ice grown off its back; the abbey's hymn running up the silver pole in pale blue; a frost stone at the socket; a
    silver bell-cap at its foot."""
    B = [haft(3, 14, "l_hymn", span=(-6, 15), column=0), haft(-4, 3, "mirror", "grip"), haft(-7, -4, "l_hymn", "pole_low", span=(-6, 15), column=0)]
    blade_ = chain(0.0, 16.0, [(0, 5, 5, 3, "l_ice"), (10, 4, 5, 3, "l_ice"), (22, 4, 5, 3, "l_ice"), (36, 3, 4, 2, "l_ice"),
                               (52, 3, 3, 2, "l_ice"), (70, 2, 2, 1, "l_ice_edge")], "blade", back=0.3, stagger=0.05)
    B += blade_
    for k, (x, y, deg) in enumerate(((-2.8, 19.0, 55), (-3.6, 23.0, 65), (-4.8, 26.5, 80))):  # ice shards off its back
        B.append(VX.rbox((x, y, 0.0), (1, 3, 4), deg, "l_ice_edge", f"shard{k}"))
    B.append(VX.slab(-2.5, 2.5, 12, 17, 5, "mirror", "socket"))
    B.append(VX.rbox((0, 14.5, 0), (2, 2, 7), 45, "l_frost_gem", "stone"))
    B += langets(7, 12, "mirror")
    B += [ring(-8, -7, "mirror", part="butt_ring"), VX.slab(-1.5, 1.5, -10, -8, 3, "mirror", "butt")]
    c = LORD_COLOUR["abbot_of_frost_glaive"]
    anchors = [((0, 14.5, 0), 2.0, c, 1), (tuple(blade_[3].c), 3.2, c, 1)]
    return B, anchors, lord_look("abbot_of_frost_glaive", (-4, 3), (0, 18))


def mirror_knight_shieldblade():
    """The Mirror Knight's Reflection: a kite shield whose face is a mirror, glinting, trimmed all round in silver set
    with diamonds; a great diamond for its boss; a short blade of mirror steel above it; a silver grip."""
    B = [grip(-4, 2, "mirror"), ring(-5, -4, "mirror", part="pommel")]
    B += blade([(14, 19, 5, 3), (19, 21, 3, 1), (21, 22, 1, 1)], "l_mirror_face", "mirror_edge")
    B += kite_shield("l_mirror_face", "l_diamond_rim")
    B += [VX.slab(-2.5, 2.5, 8, 13, 7, "mirror", "boss"), VX.rbox((0, 10.5, 0), (3, 3, 9), 45, "l_diamond", "diamond")]
    for x in (-3.0, 3.0):
        B.append(VX.rbox((x, 6.0, 0), (1, 1, 6), 45, "l_diamond", f"diamond{x}"))
    c = LORD_COLOUR["mirror_knight_shieldblade"]
    anchors = [((0, 10.5, 0), 2.8, c, 1)]
    return B, anchors, lord_look("mirror_knight_shieldblade", (-4, 2), (0, 9))


def unit_zero_precursor():
    """Unit Zero's Countdown: precursor tech in white and black, white plates on a black frame, its countdown glyph
    burning red on the emitter, stopped at zero; its blade hard white light in three stages; a black core in the
    light."""
    B = [grip(-2, 3, "l_black"), VX.slab(-3.5, -2.5, -2, 4, 5, "l_white", "case"), VX.slab(2.5, 3.5, -2, 4, 5, "l_white", "case'"),
         VX.box(-2.5, 2.5, -1, 3, 2.5, 3.5, "l_white_plain", "knuckles"), VX.slab(-2, 2, -4, -2, 4, "l_black", "pommel")]
    B += [VX.slab(-3.5, 3.5, 4, 9, 5, "l_countdown", "emitter"), VX.slab(-2.5, 2.5, 9, 10, 3, "l_black", "lens")]
    for st, (y0, y1, w) in enumerate(((10, 16, 5), (17, 22, 3), (23, 28, 1))):
        B.append(VX.slab(-w / 2, w / 2, y0, y1, 1, "l_light_white", f"stage{st}", stage=st))
        if w > 1:
            B.append(VX.slab(-0.5, 0.5, y0, y1, 3, "l_black", f"core{st}"))
    B.append(VX.slab(-0.5, 0.5, 28, 30, 1, "l_light_white", "tip", stage=2))
    for sx in (-1, 1):
        B.append(VX.slab(*sorted((sx * 3.5, sx * 4.5)), 5, 8, 3, "l_black", f"fin{sx}"))
    c = LORD_COLOUR["unit_zero_precursor"]
    anchors = [((0, 6.5, 2.5), 1.8, c, 1), ((0, 18.0, 0), 2.6, "#f0f6ff", 1)]
    return B, anchors, lord_look("unit_zero_precursor", (-2, 3), (0, 8))


def the_sleeper_katana():
    """The Sleeper's Last Dream: a curved blade half dream and half nightmare, its spine the dark of a bad sleep strewn
    with pink stars, its edge glowing dream-pink, a white hamon; a tsuba like a dark moon with a pink crescent rising off
    it; a tsuka of nightmare wrapped over dream; a pink stone in the kashira."""
    B = katana_blade("l_dream_kat")
    B += disc(4, 5, 4, "l_nightmare", "tsuba")
    B += chain(-2.5, 5.0, [(40, 2, 1, 2, "l_dream_gem"), (5, 2, 1, 1, "l_dream_gem"), (-35, 2, 1, 1, "l_dream_gem")], "crescent", stagger=0.05)
    B += katana_hilt("l_dream_ito", "l_nightmare", "l_nightmare")
    B.append(VX.rbox((0, -6.6, 0), (2, 2, 2), 45, "l_dream_gem", "stone"))
    c = LORD_COLOUR["the_sleeper_katana"]
    anchors = [((0, -6.6, 0), 1.4, c, 1), ((-2.0, 7.0, 0), 1.6, c, 1)]
    return B, anchors, lord_look("the_sleeper_katana", (-5, 4), (0, 6))


def sky_leviathan_twinblades():
    """The Sky Leviathan's Fins: two great fins of storm-blue steel swept back from a grip, each fin a curved blade with
    its rays trailing behind it, lightning caught in it, its leading edge crackling; a storm stone at the grip's heart;
    storm light lifting off the edges."""
    top = chain(0.0, 5.5, [(0, 4, 5, 3, "l_storm"), (12, 4, 5, 3, "l_storm"), (26, 4, 4, 2, "l_storm"), (42, 3, 3, 2, "l_storm"),
                           (60, 2, 1, 2, "l_storm_edge")], "fin", back=0.3, stagger=0.05, span=(5, 19))
    edge = [VX.Box(b.c + b.R[:, 0] * (b.h[0] + 0.5) + np.array([0, 0, 0.05 * (k % 2)]), np.array([0.5, b.h[1] - 0.5, 0.5]), "l_storm_edge",
                   "edge", b.R, "edge", span=(5, 19)) for k, b in enumerate(top[1:4])]
    rays = []
    for k, (deg, length) in enumerate(((40, 6), (62, 6), (84, 5))):  # the fin's rays, trailing back, each a hair off the last
        th = math.radians(deg)
        c_ = np.array([1.5, 6.0]) + np.array([math.sin(th), math.cos(th)]) * length / 2
        rays.append(VX.rbox((c_[0], c_[1], 0.1 + 0.05 * k), (1, length, 1), -deg, "l_fin", "rays"))
    top = top + edge + rays + [VX.slab(-3.5, 3.5, 4, 6, 5, "l_storm", "guard")]
    centre = [grip(-4, 4, "obsidian"), VX.slab(-2.5, 2.5, -1, 1, 5, "l_storm", "hub"), VX.rbox((0, 0, 0), (2, 2, 7), 45, "l_volt_gem", "stone")]
    c = LORD_COLOUR["sky_leviathan_twinblades"]
    anchors = [((0, 0, 0), 2.0, c, 1)]
    line = [tuple(b.c) for b in edge]
    streams = [(WISP, c, 5, 0.2, line), (WISP, c, 5, 0.2, [(-x, -y, z) for x, y, z in line]),
               (MOTE, RAMPS["volt"][6], 2, 1.2, [(0, 0, 0)])]
    return twin(top, centre), anchors, lord_look("sky_leviathan_twinblades", (-4, 4), (0, 5), streams=streams)


def hollow_herald_greatblade():
    """The Hollow Herald's Horizon: a broad greatblade of void, black past black, edged faintly; round its root an
    event-horizon ring, light caught circling the dark, the blade passing through it; a black ricasso and guard; the
    light that falls in lifting off the ring."""
    B = blade([(5, 10, 5, 3), (10, 24, 9, 7), (24, 26, 7, 5), (26, 28, 5, 3), (28, 30, 3, 1), (30, 31, 1, 1)], "l_void", "l_void_edge")
    B += frame((0, 12.0, 0), 13, 11, 9, 0, "l_horizon", "ring")
    B += frame((0, 12.0, 0), 13, 11, 7, 45, "l_horizon_white", "ring_turned")
    B += [VX.slab(-3.5, 3.5, 6, 7, 5, "obsidian", "ricasso"), VX.slab(-3.5, 3.5, 8, 9, 5, "obsidian", "ricasso2")]
    B.append(VX.slab(-6.5, 6.5, 3, 5, 5, "obsidian", "guard"))
    B += both(chain(-6.0, 4.0, [(50, 4, 2, 3, "obsidian"), (20, 3, 1, 2, "l_horizon")], "guard_points"))
    B += [grip(-5, 2, "obsidian"), ring(-1, 0, "l_horizon", width=5, depth=5, part="grip_ring")]
    B += [VX.slab(-2, 2, -9, -5, 3, "obsidian", "pommel"), VX.rbox((0, -7.0, 0), (2, 2, 5), 45, "l_horizon_white", "pommel_light")]
    c = LORD_COLOUR["hollow_herald_greatblade"]
    anchors = [((0, 12.0, 0), 5.0, c, 1), ((0, -7.0, 0), 1.6, c, 1)]
    ring_pts = [(6.0 * math.cos(a), 12.0 + 6.0 * math.sin(a), 0.0) for a in np.linspace(0, 2 * math.pi, 9)[:-1]]
    streams = [(EMBER, c, 8, 0.3, ring_pts), (MOTE, RAMPS["ember"][6], 3, 3.0, [(0, 12.0, 0)])]
    return B, anchors, lord_look("hollow_herald_greatblade", (-5, 2), (0, 10), streams=streams)


for _lid, _fn in (("bell_warden_maul", bell_warden_maul), ("mother_of_spores_scythe", mother_of_spores_scythe),
                  ("ember_tyrant_axe", ember_tyrant_axe), ("hive_regent_daggers", hive_regent_daggers),
                  ("root_saint_warstaff", root_saint_warstaff), ("drowned_archivist_spear", drowned_archivist_spear),
                  ("gearwright_colossus_gauntlets", gearwright_colossus_gauntlets), ("glass_serpent_rapier", glass_serpent_rapier),
                  ("lantern_broker_chainblade", lantern_broker_chainblade), ("abbot_of_frost_glaive", abbot_of_frost_glaive),
                  ("mirror_knight_shieldblade", mirror_knight_shieldblade), ("unit_zero_precursor", unit_zero_precursor),
                  ("the_sleeper_katana", the_sleeper_katana), ("sky_leviathan_twinblades", sky_leviathan_twinblades),
                  ("hollow_herald_greatblade", hollow_herald_greatblade)):
    DESIGNS[_lid] = _fn
# their own holds where their outline outgrows their style's (found with fp_screen: the point in view, the margin kept)
HOLDS.update({
    "mother_of_spores_scythe": ([0, -72, 30], [-1.5, 0.0, -0.6], 0.48),
    "ember_tyrant_axe": ([0, -72, 30], [-2.5, 1.5, -0.6], 0.4),
    "hive_regent_daggers": ([0, -72, 30], [-1.0, 2.0, -0.6], 0.76),
    "glass_serpent_rapier": ([0, -72, 30], [-2.0, -1.5, -0.6], 0.48),
    "lantern_broker_chainblade": ([0, -72, 30], [-1.5, -2.5, -0.6], 0.46),
    "unit_zero_precursor": ([0, -72, 30], [-2.0, -2.5, -0.6], 0.52),
    "sky_leviathan_twinblades": ([0, -72, 30], [-0.5, 0.5, -0.6], 0.6),
    "hollow_herald_greatblade": ([0, -72, 30], [-1.5, 0.0, -0.6], 0.38),
})


# ---------------------------------------------------------------------------------------------------------------------
# Building: design space to item space, the mesh, the icon


def to_item(P, direction=False):
    a = math.radians(ANGLE)
    R = np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])
    Q = np.asarray(P) @ R.T
    return Q if direction else Q * SCALE + np.asarray(HAND)


def make(row, clean=False):
    """One weapon from its design: (the built mesh, the design's look); clean paints it for the icon (the same paint:
    nothing here is grain, so the icon and the mesh agree)."""
    boxes, anchors, look = design_of(row)()
    item_anchors = [(tuple(to_item(np.array([c]))[0]), r * SCALE, hex_rgb(rgb), kind) for c, r, rgb, kind in anchors]
    built = VX.build(boxes, painter(clean), to_item, width=64, anchors=item_anchors)
    built.streams = [(kind, hex_rgb(rgb), rate, radius * SCALE, [tuple(q) for q in to_item(np.array(pts))])
                     for kind, rgb, rate, radius, pts in look.get("streams", [])]
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
        nb, nh = np.roll(np.roll(col, dy, 0), dx, 1), np.roll(np.roll(hit, dy, 0), dx, 1)  # (filled ones lend on)
        fill = on & ~hit & nh
        col[fill] = nb[fill]
        hit = hit | fill
    on &= hit
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
        wid, name, tier, dmg, dur, rarity, ench, repair, style, speed = row
        lang["item.awaken." + wid] = name
        trail = "0x" + LORD_COLOUR.get(wid, TRAILS[tier]).lstrip("#").upper()
        java_rows.append(f'            new Weapon("{wid}", "{style}", "{tier}", {dmg}F, {speed}F, {dur}, Rarity.{rarity}, {ench}, "{repair}", {trail})')
        if design_of(row) is None or (only and wid not in only):
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
            "parent": "minecraft:item/handheld", "textures": {"particle": tex + "_icon"}, "display": row_hold(row, built.pos / 16.0)})
        _write(os.path.join(RES, "assets/awaken/items", wid + ".json"), {"model": {
            "type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": {"type": "minecraft:model", "model": "awaken:item/" + wid}}],
            "fallback": {"type": "minecraft:special", "base": "awaken:item/" + wid + "_in_hand", "model": {"type": "awaken:weapon", "weapon": wid}}}})
        write_recipe(wid, style, tier)
        for stale in (wid + "_icon_glow.png",):
            if os.path.exists(os.path.join(tex_dir, stale)):
                os.remove(os.path.join(tex_dir, stale))
    # vanilla's tags, for its enchantments: each line in its style's tag (Longswords and Greatblades are swords)
    for tag in sorted({v[3] for v in STYLES.values()}):
        _write(os.path.join(MERGE, "data/minecraft/tags/item", tag + ".json"),
               {"values": ["awaken:" + row[0] for row in CATALOG if STYLES[row[8]][3] == tag]})
    lpath = os.path.join(MERGE, "assets/awaken/lang/en_us.json")
    cur = json.load(open(lpath)) if os.path.exists(lpath) else {}
    cur.update(lang)
    with open(lpath, "w") as fh:
        json.dump(dict(sorted(cur.items())), fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    with open(JAVA, "w") as fh:
        fh.write(JAVA_HEAD + ",\n".join(java_rows) + "\n    );\n}\n")


def blueprint_pattern(pattern):
    """The style's own shape with its Blueprint (P) in the first free cell (a column or row added when there is none)."""
    w = max(len(r) for r in pattern)
    rows = [list(r.ljust(w)) for r in pattern]
    for r in rows:
        for i, c in enumerate(r):
            if c == " ":
                r[i] = "P"
                return ["".join(x) for x in rows]
    if w < 3:
        return ["".join(r) + ("P" if i == 0 else " ") for i, r in enumerate(rows)]
    assert len(rows) < 3, pattern
    return ["".join(r) for r in rows] + ["P".ljust(w)]


def write_recipe(wid, style, tier):
    """A forged weapon's recipe at the crafting table: the tier's Hunter blade, core and bind in the style's shape, and
    every style but the Longsword needs its Blueprint, drawn at the Forge and kept (it comes back out of the grid). The
    Lord-forged only drop."""
    recipe = os.path.join(RES, "data/awaken/recipe", wid + ".json")
    if wid in LORD_COLOUR:
        if os.path.exists(recipe):
            os.remove(recipe)
        return
    key = {"C": "#awaken:hunter_core_" + tier.lower(), "X": "awaken:" + G.SET[tier] + "_blade", "B": "#awaken:hunter_bind_" + tier.lower()}
    pattern = STYLES[style][4]
    if style != "longsword":
        key["P"] = "awaken:" + style + "_blueprint"
        pattern = blueprint_pattern(pattern)
    _write(recipe, {"type": "minecraft:crafting_shaped", "category": "equipment", "group": "awaken_arms_" + style,
                    "key": key, "pattern": pattern, "result": {"id": "awaken:" + wid}})


def write_recipes():
    for row in CATALOG:
        write_recipe(row[0], row[8], row[2])


# First person, at rest: the weapon presented, not a slab of blade (vanilla's sword sits so close that a longsword shows
# a fragment). The whole blade stands at the right, its point in view and its flat turned a little to the eye; a little
# smaller than vanilla's hold, further in. Chosen with mesh_render.render_fp over the game's own frame (it matches
# vanilla's projection exactly).
FIRST_PERSON = {
    # 0.5 (raised 1.3) for the voxel blades (about 28 texels, longer than the lofted ones): at 0.6 their points ran off the top of
    # the view (tour, 2026-10-26)
    "firstperson_righthand": {"rotation": [0, -72, 30], "translation": [0.3, 1.3, -0.6], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [0, 72, -30], "translation": [0.3, 1.3, -0.6], "scale": [0.5, 0.5, 0.5]},
}
# Each style's own: (rotation, translation, scale) of the right hand (the left mirrors it). The longer the weapon the
# smaller it is held, so its head or point sits where the Longsword's point does (check() holds every point above the
# grip inside the view, with the Longsword's own margin to the top).
HOLDS.update({
    "longsword": ([0, -72, 30], [0.3, 1.3, -0.6], 0.5),
})


def hold_key(row):
    """Which hold a row takes: its own (a Lord-forged weapon whose outline outgrows its style's) or its style's."""
    return row[0] if row[0] in HOLDS else row[8]


def hold_of(style):
    if style == "longsword":
        return FIRST_PERSON
    rot, tr, sc = HOLDS[style]
    return {"firstperson_righthand": {"rotation": rot, "translation": tr, "scale": [sc, sc, sc]},
            "firstperson_lefthand": {"rotation": [rot[0], -rot[1], -rot[2]], "translation": tr, "scale": [sc, sc, sc]}}

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
        if design_of(row) is None or (only and row[0] not in only):
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


REFERENCE = "/Volumes/MEDIA/arms-ref/weapon-style-ref.webp"  # the owner's look bar (Minecraft Dungeons style)


def as_render(built, look):
    return {"pos": built.pos / 16.0, "nrm": built.nrm, "uv": built.uv, "along": built.along, "accent": hex_rgb(look["accent"])}


def upright(m):
    """The same mesh stood up (the blade turned from the item's diagonal to straight up about the grip), to be seen as
    the reference sees its hammer."""
    a = math.radians(-ANGLE)
    R = np.array([[math.cos(a), -math.sin(a), 0], [math.sin(a), math.cos(a), 0], [0, 0, 1]])
    c = np.array(HAND) / 16.0
    return dict(m, pos=(m["pos"] - c) @ R.T, nrm=m["nrm"] @ R.T)


def contact_sheet(out, style="longsword"):
    """The review sheet of one line beside the owner's reference: per weapon, upright turntable frames (front
    three-quarter, its other side, the back), a close-up of what it's about (its look's focus: a hilt, a head), first
    person at rest (its style's own hold) and as vanilla's handheld holds it, the same at night (the world's light
    low: only what glows of itself carries), and the icon at 4x and 1x."""
    from PIL import ImageDraw
    bg = np.array([30, 28, 40])
    rows_ = []
    for row in CATALOG:
        if (style == "lordforged") != (row[0] in LORD_COLOUR) or (style != "lordforged" and row[8] != style):
            continue
        built, look = make(row)
        rows_.append((row, built, look))
    H, tw = 300, 170
    ref_w = 640
    cols = [("three-quarter", tw), ("other side", tw), ("back", tw)] + \
        ([("beside its tier's forged one (one scale)" if style == "lordforged" else "beside its Longsword (one scale)", 250)]
         if style != "longsword" else []) + \
        [("close-up", 300), ("first person, at rest", 250), ("vanilla's hold", 250), ("night", tw), ("icon", 170)]
    W = ref_w + 20 + sum(w for _, w in cols)
    sheet = Image.new("RGB", (W, 34 + H * len(rows_)), tuple(int(v) for v in bg))
    d = ImageDraw.Draw(sheet)
    if os.path.exists(REFERENCE):
        ref = Image.open(REFERENCE).convert("RGB")
        sheet.paste(ref.resize((ref_w, int(ref.size[1] * ref_w / ref.size[0]))), (0, 34))
        crop = ref.crop((420, 220, 760, 680))
        ch = 34 + H * len(rows_) - (34 + int(ref.size[1] * ref_w / ref.size[0]) + 10)
        cw = int(crop.size[0] * ch / crop.size[1])
        sheet.paste(crop.resize((cw, ch)), ((ref_w - cw) // 2, 34 + int(ref.size[1] * ref_w / ref.size[0]) + 10))
        d.text((8, 10), "the reference (owner's look bar) and its hammer", fill=(230, 226, 240))
    x = ref_w + 20
    for name, w in cols:
        d.text((x + 6, 10), name, fill=(200, 196, 214))
        x += w
    for k, (row, built, look) in enumerate(rows_):
        y0 = 34 + k * H
        m = as_render(built, look)
        up = upright(m)
        tex = built.texture()
        lo, hi = up["pos"].min(axis=0), up["pos"].max(axis=0)
        c = (lo + hi) / 2
        span_x = np.abs(up["pos"][:, [0, 2]] - c[[0, 2]]).sum(axis=1).max() * 2  # what a turn can sweep across
        sc = min((H - 30) / ((hi[1] - lo[1]) * 16 * 1.12), (tw - 8) / (span_x * 16))
        x = ref_w + 20
        for yaw, pitch in ((28, 14), (-34, 12), (180 + 25, 12)):
            sheet.paste(MR.render(up, tex, yaw, pitch, scale=sc, size=(tw, H), centre=c, time=0.27, bg=bg), (x, y0))
            x += tw
        if style != "longsword":  # the tier's Longsword beside it, both at one texel scale, to tell them apart at a glance
            lrow = _FORGED[(row[8], row[2])] if style == "lordforged" else next(r for r in CATALOG if r[8] == "longsword" and r[2] == row[2])
            lb, ll = make(lrow)
            lup = upright(as_render(lb, ll))
            both_ = np.concatenate([up["pos"], lup["pos"]])
            lo2, hi2 = both_.min(axis=0), both_.max(axis=0)
            sc2 = (H - 30) / ((hi2[1] - lo2[1]) * 16 * 1.12)
            for mm, tt, w in ((up, tex, 140), (lup, lb.texture(), 110)):
                c2 = np.array([(mm["pos"][:, 0].min() + mm["pos"][:, 0].max()) / 2, (lo2[1] + hi2[1]) / 2, 0])
                sheet.paste(MR.render(mm, tt, 28, 14, scale=sc2, size=(w, H), centre=c2, time=0.27, bg=bg), (x, y0))
                x += w
        fx, fy = look.get("focus", (0, 3.5))
        zoom = look.get("zoom", 3.2 * (28.0 / ((hi[1] - lo[1]) * 16)))
        sheet.paste(MR.render(up, tex, 24, 14, scale=sc * zoom, size=(300, H), centre=np.array([fx, fy, 0]) / 16, time=0.27, bg=bg), (x, y0))
        x += 300
        for hold in (held_as(hold_key(row)), MR.HANDHELD_FP):
            f = MR.render_fp(m, tex, display=hold, size=(960, 540), time=0.27, bg=np.array([88, 96, 112]))
            sheet.paste(f.crop((480, 0, 960, 540)).resize((250, 281)), (x, y0 + 10))
            x += 250
        sheet.paste(MR.render(up, tex, 28, 14, scale=sc, size=(tw, H), centre=c, time=0.27, bg=np.array([8, 9, 16]), light=0.22), (x, y0))
        x += tw
        ic = icon(make(row, clean=True)[0], look)
        big = ic.resize((128, 128), Image.NEAREST)
        sheet.paste(big, (x + 20, y0 + 40), big)
        sheet.paste(ic, (x + 70, y0 + 190), ic)
        d.text((ref_w + 26, y0 + 6), f"{row[1]} ({row[2]})  {len(built.pos) // 3} triangles", fill=(236, 232, 244))
    sheet.save(out)
    return out


def row_hold(row, pos):
    """A weapon's first-person hold: its style's real hold (arms_holds.HOLDS, written as the weapon is really held) solved
    for its own mesh so it sits in the hand like every other of its style; the gauntlets keep their worn hold."""
    import arms_holds as AH
    edge = AH.edge_of(row[8], None if row[0] in LORD_COLOUR else row[2])
    d = AH.hold_for(row[8], pos, edge)
    if d is None:  # the style's own (the gauntlets' worn hold)
        fp = hold_of(hold_key(row))["firstperson_righthand"]
        d = (tuple(fp["rotation"]), tuple(fp["translation"]), fp["scale"][0])
    rot, tr, sc = d
    # the left hand holds its true mirror (a pair's twin, or a weapon in the off hand), a single-edged one edge and all
    lrot, ltr, _ = AH.mirror_display(d, keep_edge=edge != 0)
    out = {"firstperson_righthand": {"rotation": list(rot), "translation": list(tr), "scale": [sc, sc, sc]},
           "firstperson_lefthand": {"rotation": lrot, "translation": ltr, "scale": [sc, sc, sc]}}
    third = AH.third_person(row[8], edge)  # a single edge on the other side from its style's: turned over in F5 too
    if row[8] == "gauntlets":
        third = AH.worn_third_person()  # worn on the fist in F5 too, never held out in front of it
    if third is not None:
        out["thirdperson_righthand"], out["thirdperson_lefthand"] = third
    return out


def write_holds():
    """Only the holds (models/item/<id>_in_hand.json), from the meshes already built: no mesh or texture is re-made."""
    for row in CATALOG:
        wid = row[0]
        m = MS.read(os.path.join(RES, "assets/awaken/arms/weapons", wid + ".mesh"))
        path = os.path.join(RES, "assets/awaken/models/item", wid + "_in_hand.json")
        model = json.load(open(path))
        model["display"] = row_hold(row, m["pos"])
        _write(path, model)


def held_as(style):
    """A style's first-person hold as mesh_render takes it: (rotation, translation, scale)."""
    fp = hold_of(style)["firstperson_righthand"]
    return tuple(fp["rotation"]), tuple(fp["translation"]), fp["scale"][0]


def fp_screen(built, style, hand=(0.56, -0.52, -0.72), fov=70.0, aspect=16 / 9):
    """Where each corner lands on a 16:9 screen in first person at rest (0..1 across and down), the same projection as
    mesh_render.render_fp; and each corner's height up the weapon in design space."""
    (rx, ry, rz), t, sc = held_as(style)
    P = (built.pos / 16.0 - 0.5) * sc
    P = P @ MR.euler(rx, ry, rz).T + np.asarray(t) / 16.0 + np.asarray(hand)
    f = 1.0 / math.tan(math.radians(fov) / 2)
    sx = 0.5 + (P[:, 0] / -P[:, 2]) * f / aspect / 2
    sy = 0.5 - (P[:, 1] / -P[:, 2]) * f / 2
    a = math.radians(ANGLE)
    up = np.array([-math.sin(a), math.cos(a)])  # the design's +y in item space
    height = (built.pos[:, :2] - np.asarray(HAND[:2])) @ up
    return sx, sy, height


# The Longsword's tuned hold (set in the game, 2026-10-26) leaves its point this far down the screen; no line's head or
# point may sit higher (render_fp draws the hand a little lower than the game does)
FP_TOP = 0.42


BUDGET = 18_000  # triangle corners per weapon, the same as ArmsTests.MESH_BUDGET


def check():
    """Each weapon built: within budget, inside the item box, its atlas fitting, no two faces fighting over a plane, its
    icon not empty; and what ArmsTests.assets checks of the written mesh (texels in the albedo half, every glow sized,
    every stream's points on the weapon, light lifting off the B, A and S blades and only those)."""
    bad = []
    for row in CATALOG:
        if design_of(row) is None:
            bad.append(f"{row[0]}: no design")
            continue
        built, look = make(row)
        n = len(built.pos)
        lo, hi = built.pos.min(axis=0) / 16.0, built.pos.max(axis=0) / 16.0
        if n > BUDGET:
            bad.append(f"{row[0]}: {n} corners (at most {BUDGET})")
        if lo.min() < -1 or hi.max() > 2:
            bad.append(f"{row[0]}: outside the item box ({lo.round(2)}, {hi.round(2)})")
        if built.albedo.shape[0] > 256:
            bad.append(f"{row[0]}: atlas {built.albedo.shape[0]} tall")
        for w in sorted(set(VX.WARNINGS)):
            bad.append(f"{row[0]}: {w}")
        if built.uv[:, 0].min() < 0 or built.uv[:, 0].max() > 0.5 or built.uv[:, 1].min() < 0 or built.uv[:, 1].max() > 1:
            bad.append(f"{row[0]}: a texel outside the albedo half")
        if any(a[1] <= 0 for a in built.anchors):
            bad.append(f"{row[0]}: a glow with no size")
        for kind, rgb, rate, radius, pts in built.streams:
            q = np.array(pts) / 16.0
            if (q < lo - 0.05).any() or (q > hi + 0.05).any() or rate <= 0 or radius <= 0:
                bad.append(f"{row[0]}: a stream off the weapon, or with no rate or spread")
        # (the old rule, every point inside the view, shrank every style to the Longsword's outline; the holds are now
        # each weapon's own, true to its size, and a spear's point may leave the view: arms_holds.py)
        if (row[2] in ("B", "A", "S")) != bool(built.streams):
            bad.append(f"{row[0]}: light lifts off the B, A and S blades and only those")
        alpha = np.asarray(icon(make(row, clean=True)[0], look))[..., 3]
        if (alpha > 0).mean() < 0.12:
            bad.append(f"{row[0]}: its icon is nearly empty")
        ys = (built.pos[:, :2] - np.asarray(HAND[:2])) @ np.array([-math.sin(math.radians(ANGLE)), math.cos(math.radians(ANGLE))])
        print(f"  {row[0]:24s} {n // 3:5d} triangles  atlas {built.albedo.shape[1]}x{built.albedo.shape[0]}  "
              f"{ys.max() - ys.min():4.1f} texels long")
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
    if cmd == "holds":  # only the first-person holds (models/item/<id>_in_hand.json)
        write_holds()
        print("holds written")
        return 0
    if cmd == "recipes":  # only the recipes (no meshes or textures re-made)
        write_recipes()
        print("recipes written")
        return 0
    if cmd == "preview":
        print("\n".join(preview(argv[1] if len(argv) > 1 else ".", argv[2:] or None)))
        return 0
    if cmd == "sheet":
        print(contact_sheet(argv[1] if len(argv) > 1 else "weapons.png", argv[2] if len(argv) > 2 else "longsword"))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
