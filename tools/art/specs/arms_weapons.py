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
    """What glows pulses in order up the weapon: its flow is how far up it lies (the shader's pulse runs along it)."""
    def d(t):
        t.flow[:] = np.where(t.emit > 0, np.clip((t.y - y0) / (y1 - y0), lo, 1.0), 0.0)
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


def runes(y0, y1, column=0):
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
        chan = (t.X == column) & (t.Y >= y0) & (t.Y < y1)
        t.put(chan, ramp_name="crimson", tone=2, metal=0.2, gloss=0.55)
        r = t.Y - y0
        lit = chan & np.array([col[v] if 0 <= v < len(col) else False for v in r])
        t.put(lit, tone=t.top("crimson") - 2, emit=1.0, gloss=0.3)
        phase = np.array([(glyph[v] + 1) / n if 0 <= v < len(glyph) else 0.0 for v in r])
        t.flow[lit] = np.clip(phase[lit], 0.02, 1.0)
    return d


def circuit(y0, y1, every=4):
    """The precursors' signal circuit inlaid down the rib: a trace lit along it, a node every few texels, light climbing
    it in pulses."""
    def d(t):
        if not t.flat:
            return
        ok = (t.Y >= y0) & (t.Y < y1)
        node = ok & ((t.Y - y0) % every == 0)
        t.put(ok, ramp_name="signal", tone=5, emit=0.6, gloss=0.4, metal=0.0)
        t.put(node, tone=7, emit=1.0)
        t.flow[ok] = np.clip((t.y[ok] - y0) / (y1 - y0), 0.02, 1.0)
    return d


def branches(y0, y1, every=4):
    """The circuit's branches: at each node a lit texel on the flat either side of the rib."""
    def d(t):
        if not t.flat:
            return
        on = (t.Y >= y0) & (t.Y < y1) & ((t.Y - y0) % every == 0) & (np.abs(t.X) == 1)
        t.put(on, ramp_name="signal", tone=6, emit=0.85, gloss=0.4, metal=0.0)
        t.flow[on] = np.clip((t.y[on] - y0) / (y1 - y0), 0.02, 1.0)
    return d


def strip(y0, y1, inner=True, ramp_name="signal"):
    """A lit slot down a pylon's face: its inner column glowing, pulsing up."""
    def d(t):
        if not t.flat:
            return
        c = t.box.c[0]
        col = np.floor(c - np.sign(c) * 0.5 + 0.5 + 1e-6) if inner else np.floor(c + np.sign(c) * 0.5 + 0.5)
        on = (t.X == int(col)) & (t.Y >= y0) & (t.Y < y1)
        t.put(on, ramp_name=ramp_name, tone=6, emit=0.9, metal=0.0, gloss=0.4)
        t.flow[on] = np.clip((t.y[on] - y0) / (y1 - y0) * 0.5 + 0.1, 0.02, 1.0)
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
    "moss_gem": Mat("moss", 5, 0.2, 0.9, emit=0.45, details=(gem(),), contact=2),
    # C: Gatewrought
    "slate_rib": Mat("slate", 4, 0.5, 0.8, details=(circuit(5, 20),)),
    "slate_flat": Mat("slate", 4, 0.5, 0.8, details=(edges_of(("mirror", 6, 0.7, 0.95, 0.0), ("mirror", 5, 0.7, 0.95, 0.0)),
                                                      branches(5, 20))),
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
    # A: Lordscale
    "lord": Mat("lord", 3, 0.5, 0.85, details=(scales(), ridge())),
    "lord_dark": Mat("lord", 2, 0.5, 0.85),
    "feather": Mat("lord", 3, 0.5, 0.85, details=(scales(), tips())),
    "ember_edge": Mat("ember", 5, 0.0, 0.6, emit=0.8, details=(flow_up(4, 23),), bevel=False),
    "gold": Mat("gold", 5, 0.4, 0.85),
    "lord_grip": Mat("lord", 3, 0.5, 0.8, details=(scales(),)),
    "pearl_gem": Mat("pearl", 3, 0.3, 1.0, emit=0.4, details=(gem(),), contact=2),
    # S: Sovereign
    "ivory": Mat("ivory", 6, 0.1, 0.75, details=(streak(period=9, run=5, phase=2), runes(5, 20))),
    "ivory_tip": Mat("ivory", 6, 0.1, 0.75),
    "gold_edge": Mat("gold", 5, 0.45, 0.9),
    "weave_grip": Mat("weave", 3, 0.0, 0.25, details=(wrap("gold", 5, period=3, width=1, twist=0),)),
    "crimson_gem": Mat("crimson", 4, 0.3, 1.0, emit=0.5, details=(gem(),), contact=2),
}


# ---------------------------------------------------------------------------------------------------------------------
# Construction helpers (design space: texels; x across the blade, y up it from the grip's middle, z through its flat)

def blade(segs, core, edge, core_d=3, edge_d=1, part="blade", shift=lambda y: 0):
    """A blade from the guard up, a segment at a time (y0, y1, width, core width): a thin edge slab the full width and
    a thick core over its middle, so the section steps from edge to spine in whole texels; segments narrow to the
    point. shift(y0) moves a segment sideways (a wave)."""
    B = []
    for y0, y1, w, cw in segs:
        x = shift(y0)
        if w > cw:
            B.append(VX.slab(x - w / 2, x + w / 2, y0, y1, edge_d, edge, part))
        if cw:
            B.append(VX.slab(x - cw / 2, x + cw / 2, y0, y1, core_d, core, part))
    return B


def chain(x, y, links, part, side=-1):
    """Bars end to end from (x, y), each turned a whole angle: links (degrees from straight up, outward positive;
    length, width, depth, material). side -1 builds the left one (mirror() makes the right). Each starts half its
    width back inside the last, so the joints stay solid."""
    out = []
    p = np.array([x, y], dtype=float)
    for k, (deg, length, width, depth, mat) in enumerate(links):
        th = math.radians(deg * side)
        d = np.array([math.sin(th), math.cos(th)])
        if k:
            p = p - d * min(width, length) / 2
        c = p + d * length / 2
        out.append(VX.rbox((c[0], c[1], 0.0), (width, length, depth), -deg * side, mat, part, base=tuple(p), length=length))
        p = p + d * length
    return out


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

DESIGNS = {"E": wildhide, "D": beastbound, "C": gatewrought, "B": veilwoven, "A": lordscale, "S": sovereign}
# the light of each tier's swings (a bright step of its own glow)
TRAILS = {"E": "#f1ebd6", "D": "#b9d890", "C": "#7fc3ff", "B": "#c3cbff", "A": "#f3c443", "S": "#ff9080"}

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
    boxes, anchors, look = DESIGNS[row[2]]()
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
    # 0.52 for the voxel blades (about 28 texels, longer than the lofted ones): at 0.6 their points ran off the top of
    # the view (tour, 2026-10-26)
    "firstperson_righthand": {"rotation": [0, -72, 30], "translation": [0.3, 1.7, -0.6], "scale": [0.52, 0.52, 0.52]},
    "firstperson_lefthand": {"rotation": [0, 72, -30], "translation": [0.3, 1.7, -0.6], "scale": [0.52, 0.52, 0.52]},
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


def contact_sheet(out):
    """The review sheet beside the owner's reference: per weapon, upright turntable frames (front three-quarter, its
    other side, the back), a close-up of the hilt, first person at rest (its own hold) and as vanilla's handheld holds
    it, the same at night (the world's light low: only what glows of itself carries), and the icon at 4x and 1x."""
    from PIL import ImageDraw
    bg = np.array([30, 28, 40])
    rows_ = []
    for row in CATALOG:
        built, look = make(row)
        rows_.append((row, built, look))
    H, tw = 300, 170
    ref_w = 640
    cols = [("three-quarter", tw), ("other side", tw), ("back", tw), ("hilt", 300), ("first person, at rest", 250),
            ("vanilla's hold", 250), ("night", tw), ("icon", 170)]
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
        sc = (H - 24) / ((hi[1] - lo[1]) * 16)
        x = ref_w + 20
        for yaw, pitch in ((28, 14), (-34, 12), (180 + 25, 12)):
            sheet.paste(MR.render(up, tex, yaw, pitch, scale=sc, size=(tw, H), centre=c, time=0.27, bg=bg), (x, y0))
            x += tw
        hilt = np.array([0, 3.5 / 16, 0])
        sheet.paste(MR.render(up, tex, 24, 14, scale=sc * 3.2, size=(300, H), centre=hilt, time=0.27, bg=bg), (x, y0))
        x += 300
        fp = FIRST_PERSON["firstperson_righthand"]
        own = (tuple(fp["rotation"]), tuple(fp["translation"]), fp["scale"][0])
        for hold in (own, MR.HANDHELD_FP):
            f = MR.render_fp(m, tex, display=hold, size=(960, 540), time=0.27, bg=np.array([88, 96, 112]))
            sheet.paste(f.crop((560, 0, 960, 480)).resize((250, H)), (x, y0))
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


BUDGET = 18_000  # triangle corners per weapon, the same as ArmsTests.MESH_BUDGET


def check():
    """Each weapon built: within budget, inside the item box, its atlas fitting, no two faces fighting over a plane, its
    icon not empty; and what ArmsTests.assets checks of the written mesh (texels in the albedo half, every glow sized,
    every stream's points on the weapon, light lifting off the B, A and S blades and only those)."""
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
        if (row[2] in ("B", "A", "S")) != bool(built.streams):
            bad.append(f"{row[0]}: light lifts off the B, A and S blades and only those")
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
    if cmd == "sheet":
        print(contact_sheet(argv[1] if len(argv) > 1 else "weapons.png"))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
