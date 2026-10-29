"""Awaken's armour sets (docs/armour/PLAN.md): sculpted from designed boxes at the player's own texel density, painted
as pixel art, worn on the body's bones and drawn by our armour shader (assets/awaken/shaders/core/armour.*).

    python3 tools/art/specs/armour_sets.py build [set]            every set's meshes and texture
    python3 tools/art/specs/armour_sets.py check                  the checks only (each set built, nothing written)
    python3 tools/art/specs/armour_sets.py preview DIR [set]      a turntable of each set on a plain figure

A set is designed bone by bone, in each bone's own design space: texels (1 texel = 1 skin pixel = 1/16 block), the
origin at the bone's pivot, x toward the wearer's left, y up, z forward (the face). The head's skull spans x -4..4,
y 0..8, z -4..4; the body x -4..4, y -12..0, z -2..2; the right arm x -3..1, y -10..2 (its outer side at -x); the
left arm its mirror; each leg x -2..2, y -12..0. Armour stands off the skin a texel or more and grows from there: a
plate is a box, a layer over it a box a texel proud, a horn a chain of boxes turned along its sweep. The cape and the
cloth over the legs are bones of their own (the game swings them).

Each piece is a set of meshes, one per bone it rides on; a set's pieces share one texture (albedo | material map, as
the weapons': r metal, g gloss, b emissive, a flow). Written per set:
    assets/awaken/armour/<set>/<piece>_<bone>.mesh     (the weapons' mesh format, tools/art/mesh.py; part space)
    assets/awaken/armour/<set>/rune.mesh               (a Channeler's rune, which floats over the shoulder)
    assets/awaken/textures/entity/armour/<set>.png
and for a set with items of its own (OWN_ITEMS: the Hunter sets wear gear's), each piece's icon, drawn from the set's
own meshes, and above E its pattern's (a vellum sheet with the helm in ink and a seal in the rank's colour):
    assets/awaken/textures/item/armour/<set>_<piece>.png, <set>_pattern.png
    assets/awaken/items/<id>.json, assets/awaken/models/item/<id>.json
Deterministic: nothing is random.
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
for p in (HERE, ART):
    if p not in sys.path:
        sys.path.insert(0, p)
from PIL import Image, ImageDraw  # noqa: E402

import arms_weapons as AW  # noqa: E402  (the shared ramps and the Mat record)
import mesh as MS  # noqa: E402
import mesh_render as MR  # noqa: E402
import voxel as VX  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
RES = os.path.join(ROOT, "src/main/resources")
BUDGET = 9000  # corners per set (3,000 triangles): every piece worn

# ---------------------------------------------------------------------------------------------------------------------
# The body: each bone's pivot in the figure at rest (texels; feet at y 0, x to the wearer's left, z forward)

PIVOT = {"head": (0, 24, 0), "body": (0, 24, 0), "right_arm": (-5, 22, 0), "left_arm": (5, 22, 0),
         "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0),
         "cape0": (0, 23.5, -4), "front_cloth": (0, 11, 5), "back_cloth": (0, 11, -4),
         "rune": (12, 18, 0)}  # a Channeler's rune: circles the wearer in the game; beside the figure in the preview
CAPE_LINK = 6  # each cape segment's length: the next hangs from the last's lower edge
FIGURE = 33.0  # the figure's height with a helmet: along runs 0 at the feet to 1 at the crown

# What each piece wears on: the game looks for these meshes (dev.awaken.armour.client.ArmourLayer)
BONES = {"helmet": ("head",), "chestplate": ("body", "right_arm", "left_arm", "cape0", "cape1", "cape2"),
         "leggings": ("body", "right_leg", "left_leg", "front_cloth", "back_cloth"), "boots": ("right_leg", "left_leg")}

# ---------------------------------------------------------------------------------------------------------------------
# Ramps: the weapons' own, so a set and its tier's blades share a palette; and the armour's few more

RAMPS = dict(AW.RAMPS)
RAMPS.update({
    "wine": ["#12020a", "#250612", "#3e0b1c", "#5c1226", "#7e1a30", "#a3263a", "#c8404a", "#e86a66"],
    # Wildhide: a grey wolf's pelt and the ochre a hunter paints with
    "wolf": ["#141418", "#222228", "#34343a", "#48474c", "#5e5b5e", "#787274", "#958d8a", "#b6aca4", "#d8cec4"],
    "ochre": ["#2a1606", "#4a2a0c", "#6e4214", "#94601e", "#b8802c", "#d6a040", "#ecc068", "#f8e0a0"],
    # Beastbound: tanned beast hide gone the colour of rust
    "rust": ["#1a0a06", "#2e130a", "#4a1f10", "#682c16", "#86391c", "#a44c26", "#c06638", "#d88a58"],
    # Sovereign Weave: black lacquer with a violet sheen
    "lacquer": ["#020204", "#07080c", "#0e1016", "#161a22", "#20252f", "#2c323e", "#3a414f", "#4c5462"],
    # Veilwoven: the Veil's pale violet
    "lilac": ["#2a2238", "#42365a", "#5e4e80", "#7c6aa4", "#9c8cc4", "#bcaedc", "#d8ccee", "#f0eaff"],
    # Bellguard: struck copper and the green a bell grows in the rain
    "copper": ["#1c0906", "#34120a", "#561e10", "#7c2e16", "#a2421e", "#c45a2a", "#de7a40", "#f2a266"],
    "verdigris": ["#0a1a18", "#10302a", "#18483e", "#226454", "#2e826c", "#46a488", "#6ec4a4", "#a8e4c8"],
    # Amberwing: black leather
    "jet": ["#060507", "#0d0b0f", "#151218", "#1e1a22", "#29232e", "#352e3b", "#433a4a", "#54495c"],
    # Hornlord: blackened iron
    "soot": ["#050506", "#0c0b0d", "#151316", "#1f1c20", "#2a262b", "#373238", "#464047", "#57505a"],
})
RAMP_NAMES = list(RAMPS)
RAMP_RGB = [np.array([[int(h[i:i + 2], 16) for i in (1, 3, 5)] for h in RAMPS[n]], dtype=float) / 255.0 for n in RAMP_NAMES]


def rid(name):
    return RAMP_NAMES.index(name)


def top(name):
    return len(RAMPS[name]) - 1


# ---------------------------------------------------------------------------------------------------------------------
# The painted look: the weapons' rules on a body. Light from above, a little to the viewer's left and in front:
# a face turned to it a step lighter, away a step darker; each plate's outer edges a bevel band (lit on top, dark
# underneath), a contact shadow where it runs into another part; then each material's few details. Whole ramp steps,
# no noise.

LIGHT = np.array([-0.35, 0.8, 0.5]) / np.linalg.norm([-0.35, 0.8, 0.5])


def bands(f):
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


class Tex:
    """One face's visible texels as the painter sees them: design-space position, the figure height, the face's own
    across (h) and up (v) texel coordinates, and what each is painted (ramp, step, metal, gloss, emissive, flow)."""

    def __init__(self, f, m, oy):
        k = len(f.P)
        self.f, self.box, self.m, self.k = f, f.box, m, k
        self.P, self.n = f.P, f.n
        self.x, self.y, self.z = f.P[:, 0], f.P[:, 1], f.P[:, 2]
        self.world_y = self.y + oy
        L = (f.P - f.box.c) @ f.box.R                    # the box's own frame: rows and columns follow a turned box
        self.L = L
        self.side = abs(f.n[1]) < 0.7                     # a side of a plate (not its top or underside)
        # across: along the face's horizontal (for a top or underside, along x)
        hz = np.array([f.n[2], 0.0, -f.n[0]]) if self.side else np.array([1.0, 0.0, 0.0])
        hz = hz / max(np.linalg.norm(hz), 1e-9)
        self.h = f.P @ hz
        self.H = np.floor(self.h + 1e-6).astype(int)
        self.Y = np.floor(self.y + 1e-6).astype(int)
        self.ramp = np.full(k, rid(m.ramp))
        self.tone = np.full(k, m.base)
        self.metal = np.full(k, float(m.metal))
        self.gloss = np.full(k, float(m.gloss))
        self.emit = np.full(k, float(m.emit))
        self.flow = np.zeros(k)
        self.data = f.box.data

    def put(self, sel, ramp_name=None, tone=None, metal=None, gloss=None, emit=None, flow=None):
        if ramp_name is not None:
            self.ramp[sel] = rid(ramp_name)
        for arr, v in ((self.tone, tone), (self.metal, metal), (self.gloss, gloss), (self.emit, emit), (self.flow, flow)):
            if v is not None:
                arr[sel] = v[sel] if isinstance(v, np.ndarray) and v.shape == (self.k,) else v

    def bottom(self, rows=1):
        """Texels within rows of the box's lower edge (in its own frame)."""
        return self.L[:, 1] < -self.box.h[1] + rows - 1e-6

    def topmost(self, rows=1):
        return self.L[:, 1] > self.box.h[1] - rows + 1e-6


def colours(t):
    col = np.zeros((t.k, 3))
    for r in np.unique(t.ramp):
        sel = t.ramp == r
        col[sel] = RAMP_RGB[r][np.clip(t.tone[sel], 0, len(RAMP_RGB[r]) - 1)]
    return col


def painter(mats, oy):
    def paint(f):
        m = mats[f.box.mat]
        t = Tex(f, m, oy)
        s = float(f.n @ LIGHT)
        t.tone += int(s > 0.45) - int(s < -0.3)
        band, contact, edge = bands(f)
        t.band, t.contact, t.edge = band, contact, edge
        if m.bevel:
            t.tone += np.clip(band, -1, 1) if not t.side else band
        t.tone[contact] = m.base - m.contact
        for d in m.details:
            d(t)
        # what glows pulses up the figure: molten light rising from the feet to the crown
        t.flow[:] = np.where((t.emit > 0) & (t.flow == 0), np.clip(t.world_y / FIGURE, 0.02, 1.0), t.flow)
        alb = np.concatenate([colours(t), np.ones((t.k, 1))], axis=1)
        mat = np.stack([t.metal, t.gloss, t.emit, t.flow], axis=1)
        return (np.clip(alb, 0, 1) * 255).round().astype(np.uint8), (np.clip(mat, 0, 1) * 255).round().astype(np.uint8)
    return paint


# ---- details ---------------------------------------------------------------------------------------------------------

def hem(ramp_name, step, rows=1, metal=0.8, gloss=0.85, emit=0.0):
    """A trim along a plate's lower edge, round every side (the gold rim under a lame)."""
    def d(t):
        on = t.bottom(rows) & t.side
        on |= (t.n[1] < -0.7)  # its underside
        t.put(on, ramp_name=ramp_name, tone=np.clip(step + t.band, 0, top(ramp_name)), metal=metal, gloss=gloss, emit=emit)
    return d


def crown_hem(ramp_name, step, rows=1, metal=0.8, gloss=0.85):
    """A trim along a plate's upper edge."""
    def d(t):
        on = t.topmost(rows) & t.side
        on |= (t.n[1] > 0.7) & ~t.contact
        t.put(on, ramp_name=ramp_name, tone=np.clip(step + t.band, 0, top(ramp_name)), metal=metal, gloss=gloss)
    return d


def rims(ramp_name, step, metal=0.8, gloss=0.85):
    """Every outer edge of a plate's face in another metal (a trimmed plate), its field left as it was."""
    def d(t):
        on = t.edge.any(axis=1) & ~t.contact
        t.put(on, ramp_name=ramp_name, tone=np.clip(step + np.clip(t.band, -1, 1), 0, top(ramp_name)), metal=metal, gloss=gloss)
    return d


def scales():
    """Scale in staggered rows on a plate's sides: each scale three texels wide and two high, an arch: its lower
    middle texel the lit lip, its lower corners a step up, its upper corners the dark gap between it and its
    neighbours; each row offset by half a scale."""
    def d(t):
        if not t.side:
            return
        row = np.floor(t.y / 2 + 1e-6).astype(int)
        u = np.floor(t.h + 1e-6).astype(int) + 2 * (row % 2)
        u = u % 3
        v = np.floor(t.y + 1e-6).astype(int) % 2
        inner = ~t.edge.any(axis=1)
        t.tone[inner & (v == 0) & (u == 1)] += 2
        t.tone[inner & (v == 0) & (u != 1)] += 1
        t.tone[inner & (v == 1) & (u != 1)] -= 1
        t.metal[inner & (v == 0)] = 0.75
    return d


def lames(every=2, step=-1):
    """Horizontal plates (lames) on a part's sides: every so many rows a groove a step darker."""
    def d(t):
        if not t.side:
            return
        on = (t.Y % every) == 0
        t.tone[on & ~t.edge.any(axis=1)] += step
    return d


def seam(ramp_name="ember", column=None, row=None, emit=0.9):
    """A molten seam: a column (across coordinate) or a row (height) of a side, glowing."""
    def d(t):
        if not t.side:
            return
        on = np.zeros(t.k, dtype=bool)
        if column is not None:
            on |= t.H == column
        if row is not None:
            on |= t.Y == row
        t.put(on, ramp_name=ramp_name, tone=top(ramp_name) - 2, metal=0.0, gloss=0.4, emit=emit)
    return d


def edge_columns(ramp_name="gold", step=5):
    """A plate's upright edges in another metal: gold down each corner (a vambrace's, seen in first person)."""
    def d(t):
        if not t.side:
            return
        ax = 2 if abs(t.n[0]) > 0.7 else 0  # across the face: z on a face turned to x, x on one turned to z
        on = np.abs(t.L[:, ax]) > t.box.h[ax] - 1 + 1e-6
        t.put(on, ramp_name=ramp_name, tone=np.clip(step + np.clip(t.band, -1, 1), 0, top(ramp_name)), metal=0.8, gloss=0.85)
    return d


def side_seam(ramp_name="ember", emit=0.9):
    """A molten line down the middle of the faces turned to x (an arm's outer side), flush with the plate."""
    def d(t):
        if not t.side or abs(t.n[0]) < 0.7:
            return
        on = (t.L[:, 2] > 0) & (t.L[:, 2] < 1)
        t.put(on, ramp_name=ramp_name, tone=top(ramp_name) - 2, metal=0.0, gloss=0.4, emit=emit)
    return d


def folds(every=3, step=-1):
    """Cloth: a fold every few columns a step darker, its other side a step lighter (heavy cloth hanging)."""
    def d(t):
        if not t.side:
            return
        k = t.H % every
        t.tone[k == 0] += step
        t.tone[k == 1] -= step
    return d


def gold_hem_cloth(ramp_name="gold", step=5, rows=1, width=5.0):
    """Cloth trimmed in gold thread down its outer sides (|x| within a texel of width) and, on a garment's last
    link (data: hem), along its lower edge."""
    def d(t):
        if not t.side:
            return
        on = (np.abs(t.x) > width - 1) & (abs(t.n[2]) > 0.7)
        on |= abs(t.n[0]) > 0.7
        if t.data.get("hem"):
            on |= t.bottom(rows)
        t.put(on, ramp_name=ramp_name, tone=np.clip(step + np.clip(t.band, -1, 1), 0, top(ramp_name)), metal=0.7, gloss=0.8)
    return d


def sigil(ramp_name="gold", step=5):
    """The Lord's mark on the cape's back: a crowned chevron stitched in gold thread (data: rows: strings top down,
    centred on x 0, starting at the box's top)."""
    def d(t):
        rows = t.data.get("sigil")
        if not rows or abs(t.n[2]) < 0.7 or t.n[2] > 0:
            return
        y_top = t.box.c[1] + t.box.h[1]
        r = np.floor(y_top - t.y - 1e-6).astype(int) - t.data.get("sigil_top", 0)
        w = len(rows[0])
        c = np.floor(t.x + w / 2 + 1e-6).astype(int)
        on = np.zeros(t.k, dtype=bool)
        for i in range(t.k):
            if 0 <= r[i] < len(rows) and 0 <= c[i] < w:
                on[i] = rows[r[i]][w - 1 - c[i]] == "#"  # seen from behind: the wearer's left is the viewer's left
        t.put(on, ramp_name=ramp_name, tone=step, metal=0.7, gloss=0.8)
    return d


def gem_face(core_lift=0):
    """A cut stone seen face on: rings darker from a bright heart, glowing most at the heart (the weapons' gem)."""
    def d(t):
        tp = top(t.m.ramp)
        if abs(t.n[2]) < 0.7:
            t.tone[:] = np.clip(tp - 4 + t.band, 0, tp)
            t.emit[:] = t.m.emit * 0.5
            return
        f = t.f
        dc = np.maximum(np.abs(f.i + 0.5 - f.su / 2), np.abs(f.j + 0.5 - f.sv / 2))
        ring = np.maximum(np.ceil(dc - 0.5 - 1e-6), 0).astype(int)
        t.tone[:] = np.clip(tp - 2 + core_lift - ring, 0, tp)
        t.emit[:] = t.m.emit * np.clip(1.0 - 0.3 * ring, 0.3, 1.0)
    return d


def tip(ramp_name="gold", step=5, run=2):
    """A horn's or spike's last texels in another metal (data: the chain's tip link only)."""
    def d(t):
        on = t.L[:, 1] > t.box.h[1] - run
        t.put(on, ramp_name=ramp_name, tone=np.clip(step + t.band, 0, top(ramp_name)), metal=0.9, gloss=0.9)
    return d


def ridge_line(ramp_name="gold", step=5):
    """The middle column of a face in another metal (a gold ridge)."""
    def d(t):
        if not t.side:
            return
        on = np.abs(t.h - (t.box.c @ np.array([t.n[2], 0, -t.n[0]]))) < 0.5
        t.put(on, ramp_name=ramp_name, tone=np.clip(step + t.band, 0, top(ramp_name)), metal=0.8, gloss=0.85)
    return d


def _in_face(t):
    """The two box axes a face lies along, and each texel's distance (centre) in from the face's edges along them."""
    k = int(np.argmax(np.abs(t.box.R.T @ t.n)))
    a, b = [i for i in range(3) if i != k]
    da = t.box.h[a] - np.abs(t.L[:, a])
    db = t.box.h[b] - np.abs(t.L[:, b])
    return a, b, da, db


def inset_ring(ramp_name, step, inset=1, every=1, metal=None, gloss=None, emit=None, tops=True):
    """A line round a face, inset texels in from its edges: every texel (an engraved border, a silver filigree) or
    every so many (stitches, rivets)."""
    def d(t):
        if not tops and not t.side:
            return
        a, b, da, db = _in_face(t)
        ring_a = (np.abs(da - inset - 0.5) < 0.01) & (db > inset)
        ring_b = (np.abs(db - inset - 0.5) < 0.01) & (da > inset)
        on = ring_a | ring_b
        if every > 1:
            run = np.where(ring_a, np.floor(t.L[:, b] + 1e-6), np.floor(t.L[:, a] + 1e-6)).astype(int)
            on &= (run % every) == 0
        tone = np.full(t.k, step) if step >= 0 else t.tone + step
        t.put(on, ramp_name=ramp_name, tone=np.clip(tone, 0, top(ramp_name if ramp_name else t.m.ramp)), metal=metal, gloss=gloss, emit=emit)
    return d


def stitches(ramp_name, step, every=2):
    return inset_ring(ramp_name, step, 1, every, gloss=0.3)


def rivets(ramp_name="flint", step=6, every=3):
    return inset_ring(ramp_name, step, 1, every, metal=0.9, gloss=0.9)


def engraved(step=-2):
    """A groove cut a texel in from a plate's edges, in its own colour a few steps darker."""
    return inset_ring(None, step, 1, 1)


def chainmail():
    """Mail: rings in staggered rows, each ring's lit top and dark gap, bright metal."""
    def d(t):
        if not t.side:
            return
        v = t.Y % 2
        u = (t.H + v) % 2
        t.tone[(v == 0) & (u == 0)] += 2
        t.tone[(v == 1) & (u == 1)] -= 1
        t.metal[:] = 0.85
        t.gloss[:] = 0.8
    return d


def fur(dark=-1, tips=1):
    """Fur in hanging locks: locks two columns wide and four rows tall, staggered, most plain and a few a step lighter
    or darker; the lowest row a step darker (the roots of the next lock), the top a step lighter (tips)."""
    pattern = np.array([0, 1, 0, 0, -1, 0, 1, 0, -1, 0, 0])
    def d(t):
        if t.side:
            col = t.H // 2
            lock = col * 3 + (t.Y + 2 * (col % 2)) // 4
            t.tone += pattern[lock % len(pattern)]
            t.tone[t.bottom(1)] += dark
            t.tone[t.topmost(1)] += tips
        else:
            t.tone += tips
        t.gloss[:] = 0.1
    return d


def wraps(every=3):
    """Strips of leather wound round a limb: diagonal bands, each a step lit at its top and dark at its bottom."""
    def d(t):
        if not t.side:
            return
        k = (t.H + t.Y) % every
        t.tone[k == 0] -= 1
        t.tone[k == every - 1] += 1
    return d


def marks(cells, ramp_name, step, emit=0.0, front=True):
    """Paint on a face turned to the front (or the back): the texels at (column, row) in design space, column x floor,
    row y floor (war paint, an eye, a stitched sign)."""
    want = set(cells)
    def d(t):
        if abs(t.n[2]) < 0.7 or (t.n[2] > 0) != front:
            return
        X = np.floor(t.x + 1e-6).astype(int)
        on = np.array([(X[i], t.Y[i]) in want for i in range(t.k)], dtype=bool)
        t.put(on, ramp_name=ramp_name, tone=step, metal=0.0, gloss=0.3, emit=emit)
    return d


def lining(ramp_name, step):
    """A cloth's inner face (the side toward the body, +z) in another cloth: the cape's lining."""
    def d(t):
        if t.n[2] > 0.7:
            t.ramp[:] = rid(ramp_name)
            t.tone[:] = np.clip(step + (t.tone - t.m.base), 0, top(ramp_name))
    return d


def glow_tip(ramp_name, step, emit=0.9, run=1):
    """A spire's last texels lit from within."""
    def d(t):
        on = t.L[:, 1] > t.box.h[1] - run
        t.put(on, ramp_name=ramp_name, tone=step, metal=0.0, gloss=0.5, emit=emit)
    return d


def patina(ramp_name="verdigris", step=4, rows=1, every=3, run=2):
    """A bell's green: along a plate's lower edge, and in drips down from it every few columns (rain runs off bronze)."""
    def d(t):
        if not t.side:
            return
        on = t.bottom(rows) | (t.bottom(rows + run) & ((t.H % every) == 0))
        on &= ~t.contact
        t.put(on, ramp_name=ramp_name, tone=np.clip(step + np.clip(t.band, -1, 1), 0, top(ramp_name)), metal=0.1, gloss=0.3)
    return d


def bark(step=-2):
    """Bark: deep fissures running up the face, wandering a column every few rows, a lit ridge beside each."""
    def d(t):
        if not t.side:
            return
        k = (t.H + (t.Y // 3)) % 3
        t.tone[k == 0] += step
        t.tone[k == 1] += 1
        t.gloss[:] = 0.12
    return d


def leaves(step=1):
    """Leaves in rows: each three texels wide and three high, a lit vein up its middle, offset row to row."""
    def d(t):
        if not t.side:
            return
        row = t.Y // 3
        u = (t.H + 2 * (row % 2)) % 3
        v = t.Y % 3
        t.tone[u == 1] += step
        t.tone[(u != 1) & (v == 0)] -= 1
        t.gloss[:] = 0.3
    return d


def resin(every=13):
    """Amber resin: a few dark motes held inside (seeds, a wing, grit), not glowing like the rest."""
    def d(t):
        inner = ~t.edge.any(axis=1)
        mote = inner & (((t.H * 7 + t.Y * 5) % every) == 0)
        t.tone[mote] -= 3
        t.emit[mote] = 0.0
    return d


def lamellar(step=-1):
    """Lamellar: little plates three wide and two high laced in rows, each row offset, each plate's lower lip lit."""
    def d(t):
        if not t.side:
            return
        inner = ~t.edge.any(axis=1)
        row = t.Y // 2
        u = (t.H + row % 2) % 3
        v = t.Y % 2
        t.tone[inner & (u == 0)] += step
        t.tone[inner & (v == 0) & (u != 0)] += 1
    return d


def rivet_grid(ramp_name="bronze", step=6, every=2):
    """A brigandine's rivets: a bright head every few texels across and up its cloth."""
    def d(t):
        if not t.side:
            return
        inner = ~t.edge.any(axis=1)
        on = inner & (t.H % every == 0) & (t.Y % every == 0)
        t.put(on, ramp_name=ramp_name, tone=step, metal=0.9, gloss=0.9)
    return d


# ---------------------------------------------------------------------------------------------------------------------
# Construction helpers

B = VX.box


def rot(rx=0.0, ry=0.0, rz=0.0):
    """Turned rx about x (pitch), then ry about y (yaw), then rz about z (roll), degrees."""
    x, y, z = (math.radians(v) for v in (rx, ry, rz))
    RX = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    RY = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    RZ = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return RZ @ RY @ RX


def link(p0, d, length, w, dep, mat, part, up=(0, 0, 1), **data):
    """A bar from p0 along d: length along it, w across, dep through (whole texels), its frame built round d."""
    d = np.asarray(d, dtype=float)
    d = d / np.linalg.norm(d)
    ex = np.cross(np.asarray(up, dtype=float), d)
    if np.linalg.norm(ex) < 1e-3:
        ex = np.cross(np.array([1.0, 0, 0]), d)
    ex /= np.linalg.norm(ex)
    ez = np.cross(ex, d)
    R = np.stack([ex, d, ez], axis=1)
    c = np.asarray(p0, dtype=float) + d * length / 2
    return VX.Box(c, np.array([w, length, dep]) / 2, mat, part, R, **data), np.asarray(p0, dtype=float) + d * length


def chain(p0, links, part, up=(0, 0, 1)):
    """A horn or spike: links end to end (direction, length, width, depth, material), each sunk half its width into
    the last so the joints stay solid."""
    out, p = [], np.asarray(p0, dtype=float)
    for k, (d, length, w, dep, mat) in enumerate(links):
        dd = np.asarray(d, dtype=float) / np.linalg.norm(d)
        if k:
            p = p - dd * min(w, length) / 2
        b, p = link(p, dd, length, w, dep, mat, part, up)
        out.append(b)
    return out


def mirror(boxes):
    return VX.mirror(boxes)


def turned(boxes, R, pivot):
    """Boxes turned together about a pivot (a stepped dome tipped outward as one)."""
    pv = np.asarray(pivot, dtype=float)
    return [VX.Box(pv + R @ (b.c - pv), b.h, b.mat, b.part, R @ b.R, b.name, **b.data) for b in boxes]


# ---------------------------------------------------------------------------------------------------------------------
# The sets

def lordscale():
    """Lordscale (A): the war-plate of a fallen Lord, remade at the Forge. Black scale that drinks the light, every
    plate rimmed in gold; molten seams that wake and pulse up the body; a horned helm with a burning eye-slit; layered
    pauldrons that flare like a wing's coverts, crowned with swept spikes; a gold-hemmed cape of deep wine bearing the
    Lord's chevron."""
    mats = {
        "plate": AW.Mat("lord", 3, 0.55, 0.85),
        "plate_hem": AW.Mat("lord", 3, 0.55, 0.85, details=(hem("gold", 5),)),
        "plate_plain": AW.Mat("lord", 3, 0.55, 0.85),
        "plate_lames": AW.Mat("lord", 3, 0.55, 0.85, details=(lames(2, -1),)),
        "scale": AW.Mat("lord", 3, 0.6, 0.8, details=(scales(),)),
        "scale_plain": AW.Mat("lord", 2, 0.6, 0.8, details=(scales(),)),
        "rimmed": AW.Mat("lord", 3, 0.55, 0.85, details=(rims("gold", 5),)),
        "gold": AW.Mat("gold", 5, 0.75, 0.9),
        "gold_ridge": AW.Mat("gold", 5, 0.75, 0.9),
        "ember": AW.Mat("ember", 5, 0.0, 0.5, emit=0.95, bevel=False),
        "eye": AW.Mat("ember", 6, 0.0, 0.4, emit=1.0, bevel=False),
        "ember_gem": AW.Mat("crimson", 5, 0.3, 1.0, emit=0.75, details=(gem_face(),), contact=2),
        "horn": AW.Mat("obsidian", 3, 0.35, 0.75, details=(lames(2, -1),)),
        "horn_tip": AW.Mat("obsidian", 3, 0.35, 0.75, details=(tip("gold", 5, 2),)),
        "cloth": AW.Mat("wine", 4, 0.0, 0.2, details=(folds(3), gold_hem_cloth())),
        "cloth_sigil": AW.Mat("wine", 4, 0.0, 0.2, details=(folds(3), gold_hem_cloth(), sigil())),
        "tabard": AW.Mat("wine", 4, 0.0, 0.2, details=(folds(3), gold_hem_cloth(width=2.0))),
        "tabard_wide": AW.Mat("wine", 4, 0.0, 0.2, details=(folds(3), gold_hem_cloth(width=3.0))),
        "lining": AW.Mat("wine", 2, 0.0, 0.15),
        "plate_seam": AW.Mat("lord", 3, 0.55, 0.85, details=(hem("gold", 5), seam("ember", row=None, column=None))),
        "vambrace": AW.Mat("lord", 3, 0.55, 0.85, details=(lames(2, -1), edge_columns("gold", 5), side_seam())),
    }

    # ---- helmet: a closed helm the size of the head, its brow and prow a texel proud, a burning slit between; a gold
    # ridge from the nose over the crown; two horns sweeping back from the temples and rising behind
    head = [
        B(-5, 5, 8, 9, -5, 5, "plate", "shell"),                       # the crown, a texel over the skull
        B(4, 5, -1, 8, -5, 5, "plate", "shell"), B(-5, -4, -1, 8, -5, 5, "plate", "shell"),
        B(-4, 4, -1, 8, -5, -4, "plate_lames", "shell"),               # the back, in lames
        B(-4, 4, 5, 8, 4, 6, "plate_hem", "brow"),                      # the brow, a texel proud, gold over the slit
        B(-4, 4, -1, 4, 4, 5, "plate", "jaw"),                         # the jaw, flush with the shell
        B(-3, 3, -1, 3, 5, 6, "plate_hem", "prow"),                    # its prow, under the slit
        B(-3, -1, 4, 5, 4, 5, "eye", "eyes"), B(1, 3, 4, 5, 4, 5, "eye", "eyes"),  # the slit, sunk between them
        B(-1, 1, 4, 5, 4, 5, "plate_plain", "jaw"), B(-4, -3, 4, 5, 4, 5, "plate_plain", "jaw"),
        B(3, 4, 4, 5, 4, 5, "plate_plain", "jaw"),
        B(-1, 1, 3, 8, 6, 7, "gold_ridge", "ridge"),                   # the nasal, down the brow to the slit
        B(-1, 1, 9, 11, -3, 4, "plate", "crest"),                       # a low dark crest over the crown
        B(-5, 5, 0, 2, -6, -5, "plate_hem", "neck"),                   # the neck guard
    ]
    horn = chain((4.5, 6.5, 1.0), [((0.85, 0.1, -0.5), 4, 3, 3, "horn"), ((0.45, 0.55, -0.7), 4, 2, 2, "horn"),
                                   ((0.1, 1.0, -0.45), 4, 2, 2, "horn"), ((0.0, 1.0, 0.2), 3, 1, 1, "horn_tip")], "horn")
    head += horn + mirror(horn)

    # ---- chestplate: a cuirass with a stepped chevron of plates down the breast, a burning heart-stone at its
    # middle in a gold frame, a standing collar behind the helm
    body = [
        B(-5, 5, -10, 0, -3, 3, "plate", "cuirass"),
        B(-4, 4, -5, -1, 3, 4, "plate_hem", "breast"),
        B(-3, 3, -7, -5, 3, 4, "plate_hem", "breast2"),
        B(-2, 2, -9, -7, 3, 4, "plate_hem", "breast3"),
        B(-5, 5, 0, 2, -4, -3, "rimmed", "collar"),                    # the standing collar at the back
        B(-5, -4, 0, 2, -3, 1, "rimmed", "collar"), B(4, 5, 0, 2, -3, 1, "rimmed", "collar"),
        B(-1, 1, -9, 0, -4, -3, "gold_ridge", "spine"),                # the spine ridge down the back
        B(-6, 6, -1, 1, -6, -4, "gold", "yoke"),                        # the cape's gold yoke across the shoulders
    ]
    body += AW.frame((0, -3, 4.5), 4, 2, 1, 45, "gold", "heart_frame")
    body.append(VX.rbox((0, -3, 4.5), (2, 2, 2), 45, "ember_gem", "heart"))

    # ---- arms: a great pauldron over the shoulder, its outer edge rising, two lames under it stepping in, one swept
    # spike; a scaled upper arm; an elbow cop behind; a vambrace in lames, gold at its corners, a molten line down it
    right_arm = [
        VX.Box((-2.0, 3.5, 0.0), (3.5, 1.5, 4.0), "plate_hem", "pauldron1", rot(0, 0, -14)),
        VX.Box((-1.5, 1.0, 0.0), (3.0, 1.0, 3.5), "plate", "pauldron2", rot(0, 0, -6)),
        B(-4, 1, -2, 0, -3, 3, "plate", "pauldron3"),
        B(-4, 2, -6, -2, -3, 3, "scale_plain", "rerebrace"),
        B(-4, 2, -6, -4, -4, -3, "plate", "couter"),                    # the elbow cop, behind
        B(-4, 2, -10, -6, -3, 3, "vambrace", "vambrace"),
        B(-4, 2, -11, -10, -3, 3, "plate", "gauntlet"),                  # the knuckles, a texel under the hand
    ]
    right_arm += chain((-4.0, 5.0, -1.0), [((-0.35, 1.0, -0.35), 4, 2, 2, "horn"), ((-0.2, 1.0, -0.6), 3, 1, 1, "horn_tip")],
                       "pspike")
    left_arm = mirror(right_arm)

    # ---- cape: three links of wine cloth hemmed in gold, the Lord's chevron on the back, its lower edge cut
    sig = ["#......#",
           "##....##",
           ".##..##.",
           "..####..",
           "...##...",
           "........"]
    cape0 = [B(-5, 5, -6, 0, -1, 0, "cloth", "cape")]
    cape1 = [B(-5, 5, -6, 0, -1, 0, "cloth_sigil", "cape", sigil=sig, sigil_top=0)]
    cape2 = []
    for x0, ln in ((-5, 6), (-3, 5), (-1, 6), (1, 5), (3, 6)):  # the cut edge: tongues of cloth of two lengths
        cape2.append(B(x0, x0 + 2, -ln, 0, -1, 0, "cloth", f"cape_t{x0}", hem=True))

    # ---- leggings: a gold-hemmed belt with a molten stone; scaled cuisses; a plate over each hip; knee cops; a wine
    # tabard front and back to the knee that the legs push as they stride
    belt = [B(-5, 5, -13, -10, -4, 4, "plate_hem", "belt"),
            B(-2, 2, -13, -10, 4, 5, "gold", "buckle"),
            B(-1, 1, -12, -11, 5, 6, "eye", "buckle_stone")]
    right_leg = [
        B(-3, 2, -6, 0, -3, 3, "scale", "cuisse"),                       # five wide: flush with the other leg's
        B(-4, -3, -4, 1, -3, 3, "plate_hem", "tasset"),
        B(-3, 2, -8, -6, 3, 4, "plate", "poleyn"),
    ]
    left_leg = mirror(right_leg)
    front_cloth = [B(-2, 2, -6, 0, 0, 1, "tabard", "tabard", hem=True)]
    back_cloth = [B(-3, 3, -7, 0, -1, 0, "tabard_wide", "tabard", hem=True)]

    # ---- boots: greaves with a gold ridge; pointed sabatons a texel under the sole; gold spurs
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "plate", "greave"),
        B(-1, 1, -11, -8, 3, 4, "gold_ridge", "greave_ridge"),
        B(-3, 2, -13, -11, -3, 3, "plate", "sabaton"),
        B(-2, 2, -13, -11, 3, 5, "plate", "toe"),
        B(-1, 1, -13, -12, 5, 6, "gold", "toe_tip"),
        B(-1, 1, -11, -10, -4, -3, "gold", "spur"),
    ]
    left_boot = mirror(right_boot)

    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": left_arm, "cape0": cape0, "cape1": cape1, "cape2": cape2},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": left_leg, "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": left_boot},
    }
    return dict(id="lordscale", mats=mats, pieces=pieces, accent=AW.RAMPS["ember"][5])


def _along(c, u, ts, z, mat, part):
    """Little cubes along a turned strap: centre c, direction u, at each distance t, their front at z."""
    return [VX.Box((c[0] + t * u[0], c[1] + t * u[1], z), (0.5, 0.5, 0.5), mat, part) for t in ts]


def wildhide():
    """Wildhide (E, Strider): the first hunter's kit, cut from the wilds' beasts. A grey wolf's head worn as a hood,
    its ears up, its fangs over your brow and its eyes catching the light; tan hide in layers under a bandolier of
    bone toggles; the wolf's pelt over one shoulder, shaggy at its edge, its foreleg down the breast; ochre claw marks
    painted on; a pouch, a horn and a knife at the belt; the wolf's own brush of a tail behind."""
    claw = [(-5, -6), (-5, -7), (-4, -8), (-3, -6), (-3, -7), (-2, -8), (-1, -7), (-1, -8), (0, -9)]
    flap = [(-1, -2), (0, -2), (-1, -3), (0, -3)]
    eyes = [(-4, 8), (-3, 8), (2, 8), (3, 8)]
    lids = [(-4, 9), (-3, 9), (2, 9), (3, 9)]
    mats = {
        "jerkin": AW.Mat("hide", 4, 0.0, 0.25, details=(lames(3, -1), marks(claw, "ochre", 5))),
        "hide": AW.Mat("hide", 4, 0.0, 0.25),
        "hide_layer": AW.Mat("hide", 4, 0.0, 0.25, details=(hem("hide", 2), stitches("bone", 5, 2))),
        "flap": AW.Mat("hide", 3, 0.0, 0.25, details=(stitches("bone", 5), marks(flap, "ochre", 5))),
        "hide_dark": AW.Mat("hide", 2, 0.05, 0.35),
        "strap": AW.Mat("hide", 2, 0.05, 0.35),
        "wrap": AW.Mat("hide", 3, 0.0, 0.25, details=(wraps(3),)),
        "pelt": AW.Mat("wolf", 5, 0.0, 0.1, details=(fur(),)),
        "pelt_dark": AW.Mat("wolf", 3, 0.0, 0.1, details=(fur(),)),
        "pelt_light": AW.Mat("wolf", 7, 0.0, 0.1, details=(fur(),)),
        "wolf": AW.Mat("wolf", 5, 0.0, 0.12, details=(fur(),)),
        "brow": AW.Mat("wolf", 5, 0.0, 0.12, details=(marks(lids, "wolf", 1), marks(eyes, "amber", 5, emit=0.6))),
        "muzzle": AW.Mat("wolf", 6, 0.0, 0.12),
        "lip": AW.Mat("wolf", 2, 0.0, 0.3),
        "ear": AW.Mat("wolf", 5, 0.0, 0.12, details=(lining("wolf", 2),)),
        "ear_tip": AW.Mat("wolf", 1, 0.0, 0.12),
        "nose": AW.Mat("wolf", 0, 0.0, 0.7, bevel=False),
        "fang": AW.Mat("bone", 6, 0.1, 0.6),
        "bone": AW.Mat("bone", 5, 0.1, 0.5),
        "claw": AW.Mat("bone", 4, 0.1, 0.6),
    }

    # ---- helmet: the wolf's pelt round your head, the face open; its head rides on yours, its muzzle forward over
    # your brow, its fangs hanging over your forehead, its ears up
    head = [
        B(-5, -4, -1, 8, -5, 3, "pelt", "hood"), B(4, 5, -1, 8, -5, 3, "pelt", "hood"),
        B(-4, 4, -1, 8, -5, -4, "pelt", "hood"),
        B(-5, 5, -3, -1, -6, -4, "pelt_dark", "hood"),                  # the pelt over the nape
        B(-5, 5, 8, 10, -5, 3, "wolf", "wolf"),                          # the wolf's skull, on yours
        B(-4, 4, 7, 10, 3, 5, "brow", "wolf"),                           # its brow, its eyes
        B(-5, -4, 4, 8, 3, 5, "wolf", "wolf"), B(4, 5, 4, 8, 3, 5, "wolf", "wolf"),   # its cheeks round your face
    ]
    # its muzzle reaches forward over your brow, tipped down; the upper jaw under it, its fangs at the front
    tilt = rot(18, 0, 0)
    fwd, dn = tilt @ np.array([0, 0, 1.0]), tilt @ np.array([0, 1.0, 0])
    base = np.array([0, 8.5, 5.0])
    head += [
        VX.Box(base + fwd * 2.5 + dn * 0.5, (2.0, 1.0, 2.5), "muzzle", "wolf", tilt),
        VX.Box(base + fwd * 5.5 + dn * 0.5, (1.0, 1.0, 0.5), "nose", "wolf", tilt),
        VX.Box(base + fwd * 2.0 - dn * 1.0, (2.0, 0.5, 2.0), "lip", "wolf", tilt),
    ]
    tooth = base + fwd * 3.5 - dn * 1.5
    head += [VX.Box(tooth + np.array([-1.5, 0, 0]), (0.5, 1.0, 0.5), "fang", "fangs", tilt),
             VX.Box(tooth + np.array([1.5, 0, 0]), (0.5, 1.0, 0.5), "fang", "fangs", tilt),
             VX.Box(tooth + np.array([0, 0.5, 0]), (1.0, 0.5, 0.5), "bone", "fangs", tilt)]
    ear = chain((3.0, 10.0, -1.5), [((0.25, 1.0, -0.2), 2, 3, 1, "ear"), ((0.15, 1.0, -0.15), 2, 2, 1, "ear"),
                                    ((0.05, 1.0, 0.0), 2, 1, 1, "ear_tip")], "ear")
    head += ear + mirror(ear)

    # ---- chestplate: a hide jerkin in layers, a bandolier with bone toggles, the pelt over the left shoulder and its
    # foreleg down the breast; on the right a layered hide pauldron and an arm strap
    u = (-math.sin(math.radians(40)), math.cos(math.radians(40)))
    body = [
        B(-5, 5, -10, 0, -3, 3, "jerkin", "jerkin"),
        VX.Box((0, -5.5, 3.5), (1, 7.5, 0.5), "strap", "strap", rot(0, 0, 40)),
        VX.Box((0, -5.5, -3.5), (1, 7.5, 0.5), "strap", "strap_back", rot(0, 0, 40)),
        B(1, 6, -2, 1, -4, 4, "pelt", "mantle"),
        B(2, 5, -5, -2, 3, 5, "pelt", "mantle"),
        B(2, 5, -6, -5, 4, 5, "claw", "claws"),
    ]
    body += _along((0, -5.5), u, (-5.0, -2.0, 1.0), 4.5, "bone", "toggle")
    right_arm = [
        VX.Box((-1.5, 2.5, 0.0), (2.5, 1.0, 3.5), "hide_layer", "pauldron", rot(0, 0, -14)),
        VX.Box((-2.0, 0.5, 0.0), (2.0, 1.0, 3.0), "hide_layer", "pauldron2", rot(0, 0, -8)),
        B(-4, 2, -3, -2, -3, 3, "strap", "armband"),
        B(-5, -4, -3, -2, -1, 0, "bone", "armband"),
        B(-4, 2, -10, -5, -3, 3, "wrap", "bracer"),
        B(-5, -4, -7, -6, -1, 1, "bone", "toggle"), B(-5, -4, -9, -8, -1, 1, "bone", "toggle"),
        B(-4, 2, -11, -10, -3, 3, "hide_dark", "knuckles"),
    ]
    left_arm = [b for b in mirror(right_arm) if not b.part.startswith("pauldron")] + [
        VX.Box((1.5, 2.5, 0.0), (3.0, 1.5, 4.5), "pelt", "mantle", rot(0, 0, 16)),
        VX.Box((2.0, 0.0, 0.0), (2.5, 1.5, 4.0), "pelt_dark", "mantle2", rot(0, 0, 9)),
        B(3, 4, -4, -2, -3, -2, "pelt_dark", "mantle3"), B(3, 4, -3, -2, -1, 0, "pelt_dark", "mantle3"),
        B(3, 4, -4, -2, 1, 2, "pelt_dark", "mantle3"),
    ]

    # ---- leggings: a belt with a pouch and a horn, hide breeches, a knife on the right thigh, a painted flap in
    # front and the wolf's tail behind
    belt = [
        B(-5, 5, -13, -10, -3, 3, "strap", "belt"),
        B(-1, 1, -12, -11, 3, 4, "bone", "buckle"),
    ]
    right_leg = [
        B(-3, 2, -5, 0, -3, 3, "hide", "breech"),
        B(-4, -3, -5, -1, -1, 1, "hide_dark", "sheath"),
        B(-4, -3, -1, 1, -1, 0, "bone", "hilt"),
    ]
    left_leg = [b for b in mirror(right_leg) if b.part not in ("sheath", "hilt")]
    front_cloth = [B(-2, 2, -5, 0, -1, 0, "flap", "flap"),
                   B(-2, -1, -6, -5, -1, 0, "flap", "flap"), B(0, 1, -6, -5, -1, 0, "flap", "flap")]
    back_cloth = [VX.Box((0, -1.0, -1.0), (1.5, 1.0, 1.0), "pelt", "tail"),
                  VX.Box((0, -3.5, -1.5), (1.5, 2.5, 1.5), "pelt", "tail"),
                  VX.Box((0, -7.0, -1.0), (1.0, 1.0, 1.0), "pelt_light", "tail")]

    # ---- boots: wrapped hide, a shaggy fur cuff, a bone toggle
    right_boot = [
        B(-3, 2, -11, -7, -3, 3, "wrap", "boot"),
        B(-4, 2, -7, -5, -4, 4, "pelt", "cuff"),
        B(-3, 2, -13, -11, -3, 3, "hide_dark", "sole"),
        B(-2, 2, -13, -11, 3, 4, "hide_dark", "toe"),
        B(-4, -3, -10, -9, -1, 1, "bone", "toggle"),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": left_arm},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": left_leg, "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="wildhide", mats=mats, pieces=pieces, accent=AW.RAMPS["amber"][4])


def beastbound():
    """Beastbound (D, Bulwark): iron hammered over beast hide. A boar's skull worn as the visor, its tusks curving up
    past your cheeks and a bristled mane down the helm; a riveted breastplate with a diamond boss and a horn; great
    riveted pauldrons on a hide pad, a tusk on each; mail on the arms; chain skirts that swing as you walk."""
    mats = {
        "iron": AW.Mat("flint", 4, 0.75, 0.7),
        "iron_riv": AW.Mat("flint", 4, 0.75, 0.7, details=(rivets("flint", 7, 3),)),
        "iron_lames": AW.Mat("flint", 4, 0.75, 0.7, details=(lames(2, -1),)),
        "iron_boss": AW.Mat("flint", 5, 0.8, 0.8, details=(rivets("flint", 7, 2),)),
        "hide": AW.Mat("rust", 4, 0.0, 0.3, details=(stitches("rust", 6),)),
        "fur": AW.Mat("rust", 3, 0.0, 0.1, details=(fur(),)),
        "bristle": AW.Mat("rust", 2, 0.0, 0.1, details=(fur(-1, 2),)),
        "chain": AW.Mat("flint", 4, 0.85, 0.8, details=(chainmail(),)),
        "skirt": AW.Mat("flint", 4, 0.85, 0.8, details=(chainmail(), hem("rust", 4))),
        "skull": AW.Mat("bone", 5, 0.05, 0.45),
        "snout": AW.Mat("bone", 5, 0.05, 0.45, details=(marks([(-2, 1), (1, 1)], "bone", 0),)),
        "socket": AW.Mat("pelt", 0, 0.0, 0.2, bevel=False),
        "tusk": AW.Mat("bone", 6, 0.1, 0.6),
        "tusk_tip": AW.Mat("bone", 7, 0.1, 0.7),
    }
    head = [
        B(-5, 5, 8, 9, -5, 5, "iron_riv", "helm"),
        B(-5, -4, -1, 8, -5, 4, "iron", "helm"), B(4, 5, -1, 8, -5, 4, "iron", "helm"),
        B(-4, 4, -1, 8, -5, -4, "iron_lames", "helm"),
        B(-4, 4, 6, 8, 4, 6, "skull", "skull"),                         # the skull's brow ridge
        B(-4, -3, 3, 6, 4, 6, "skull", "skull"), B(3, 4, 3, 6, 4, 6, "skull", "skull"),
        B(-1, 1, 3, 6, 4, 6, "skull", "skull"),
        B(-3, -1, 3, 6, 4, 5, "socket", "socket"), B(1, 3, 3, 6, 4, 5, "socket", "socket"),
        B(-3, 3, 0, 3, 4, 7, "snout", "skull"),                         # the boar's snout
        B(-4, 4, -1, 0, 4, 6, "iron", "chin"),
        B(-1, 1, 9, 11, -2, 4, "bristle", "mane"), B(-1, 1, 9, 10, -5, -2, "bristle", "mane"),
        B(-1, 1, 4, 8, -6, -5, "bristle", "mane"),
    ]
    tusk = chain((3.0, 1.0, 6.0), [((1.0, 0.3, 0.3), 3, 2, 2, "tusk"), ((0.5, 1.0, 0.1), 3, 2, 2, "tusk"),
                                   ((-0.1, 1.0, -0.2), 3, 1, 1, "tusk_tip")], "tusk")
    head += tusk + mirror(tusk)
    body = [
        B(-5, 5, -10, 0, -3, 3, "hide", "jerkin"),
        B(-4, 4, -8, 0, 3, 4, "iron_riv", "breast"),
        B(-4, 4, -8, 0, -4, -3, "iron_riv", "back"),
        B(-6, 6, -10, -8, -4, 4, "iron_lames", "fauld"),
        VX.rbox((0, -4, 4.5), (4, 4, 1), 45, "iron_boss", "boss"),
        B(-1, 1, -5, -3, 5, 6, "tusk", "boss_horn"),
    ]
    right_arm = [
        B(-5, 1, -1, 2, -4, 4, "fur", "pad"),
        VX.Box((-2.5, 3.0, 0.0), (4.0, 1.5, 4.5), "iron_riv", "pauldron", rot(0, 0, -12)),
        B(-4, 2, -4, -1, -3, 3, "chain", "mail"),
        B(-4, 2, -10, -4, -3, 3, "iron_lames", "vambrace"),
        B(-5, -4, -8, -6, -3, 3, "hide", "strap"),
        B(-4, 2, -11, -10, -3, 3, "iron", "gauntlet"),
    ]
    right_arm += chain((-5.0, 4.5, 0.0), [((-0.4, 1.0, 0.0), 3, 2, 2, "tusk"), ((0.1, 1.0, 0.0), 2, 1, 1, "tusk_tip")], "ptusk")
    belt = [B(-5, 5, -13, -10, -3, 3, "hide", "belt"), B(-2, 2, -13, -10, 3, 4, "iron_riv", "buckle")]
    right_leg = [
        B(-3, 2, -6, 0, -3, 3, "chain", "mail"),
        B(-4, -3, -5, 1, -3, 3, "iron_riv", "tasset"),
        B(-3, 2, -8, -6, 3, 4, "iron", "poleyn"),
    ]
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "iron_lames", "greave"),
        B(-3, 2, -8, -7, -4, 4, "hide", "strap"),
        B(-3, 2, -13, -11, -3, 3, "iron", "sabaton"),
        B(-2, 2, -13, -11, 3, 5, "iron_riv", "toe"),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm)},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg),
                     "front_cloth": [B(-3, 3, -6, 0, -1, 0, "skirt", "skirt", hem=True)],
                     "back_cloth": [B(-4, 4, -6, 0, -1, 0, "skirt", "skirt", hem=True)]},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="beastbound", mats=mats, pieces=pieces, accent=RAMPS["rust"][7])


def _ring_cells(cx, cy, r):
    """The texels a circle of radius r about (cx, cy) passes through (a ring painted on a plate)."""
    out = set()
    for k in range(360):
        a = math.radians(k)
        out.add((math.floor(cx + r * math.cos(a)), math.floor(cy + r * math.sin(a))))
    return sorted(out)


def gatewrought():
    """Gatewrought (C, Duelist): plate cut from pale gate-stone, every plate's edge engraved. The helm's face is a gate:
    two silver-trimmed pillars and a lintel round a doorway of gate light, an arched crest over it; the Five Forces set
    in a silver ring on the breast round a point of gate light (heat, charge, gravity, spirit, frost); layered stone
    pauldrons; a split tabard of storm blue, two tails before and two behind, each edged in silver."""
    forces = [("ember", 5), ("signal", 5), ("veil", 6), ("spore", 5), ("ice", 5)]
    centre, r = (0.0, -4.5), 2.0
    mats = {
        "stone": AW.Mat("slate", 6, 0.05, 0.35, details=(engraved(-2),)),
        "stone_plain": AW.Mat("slate", 6, 0.05, 0.35),
        "stone_dark": AW.Mat("slate", 3, 0.05, 0.3),
        "stone_lames": AW.Mat("slate", 5, 0.05, 0.35, details=(lames(2, -1),)),
        "breast": AW.Mat("slate", 6, 0.05, 0.35, details=(engraved(-2),)),
        "plinth": AW.Mat("slate", 2, 0.4, 0.7, details=(rims("mirror", 5),)),
        "pillar": AW.Mat("slate", 7, 0.08, 0.4, details=(edge_columns("slate", 4),)),
        "door": AW.Mat("signal", 6, 0.0, 0.5, emit=0.85, bevel=False),
        "silver": AW.Mat("mirror", 4, 0.9, 0.95),
        "tail_r": AW.Mat("storm", 3, 0.0, 0.2, details=(folds(2), gold_hem_cloth("mirror", 5, width=3.0))),
        "tail_l": AW.Mat("storm", 3, 0.0, 0.2, details=(folds(2), gold_hem_cloth("mirror", 5, width=3.0))),
        "tail_br": AW.Mat("storm", 3, 0.0, 0.2, details=(folds(2), gold_hem_cloth("mirror", 5, width=4.0))),
    }
    for i, (ramp, step) in enumerate(forces):
        mats[f"gem{i}"] = AW.Mat(ramp, step, 0.3, 1.0, emit=1.0, bevel=False)
    head = [
        B(-5, 5, 8, 9, -5, 5, "stone", "helm"),
        B(-5, -4, -1, 8, -5, 4, "stone", "helm"), B(4, 5, -1, 8, -5, 4, "stone", "helm"),
        B(-4, 4, -1, 8, -5, -4, "stone_lames", "helm"),
        B(-3, -1, 0, 7, 5, 6, "pillar", "gate"), B(1, 3, 0, 7, 5, 6, "pillar", "gate"),
        B(-4, 4, 7, 9, 5, 6, "stone", "lintel"),
        B(-1, 1, 1, 7, 4, 5, "door", "door"),
        B(-1, 1, 9, 10, 5, 6, "silver", "keystone"),
        # the face plate round the doorway
        B(-4, -1, -1, 8, 4, 5, "stone_plain", "face"), B(1, 4, -1, 8, 4, 5, "stone_plain", "face"),
        B(-1, 1, -1, 1, 4, 5, "stone_plain", "face"), B(-1, 1, 7, 8, 4, 5, "stone_plain", "face"),
        # an arched crest, front to back
        B(-1, 1, 9, 10, -4, 4, "stone_dark", "crest"), B(-1, 1, 10, 11, -3, 3, "stone_dark", "crest"),
        B(-1, 1, 11, 12, -1, 2, "silver", "crest"),
    ]
    body = [
        B(-5, 5, -10, 0, -3, 3, "stone_lames", "cuirass"),
        B(-4, 4, -9, -1, 3, 4, "breast", "breast"),
        B(-5, 5, 0, 1, -4, -3, "silver", "collar"),
        VX.rbox((0, -4.5, 4.5), (5, 5, 1), 45, "plinth", "plinth"),
        VX.rbox((0, -4.5, 5.5), (1, 1, 1), 45, "door", "core"),
    ]
    for i in range(5):
        a = math.radians(90 + 72 * i)
        body.append(VX.Box((centre[0] + r * math.cos(a), centre[1] + r * math.sin(a), 5.5), (0.5, 0.5, 0.5), f"gem{i}", f"gem{i}"))
    right_arm = [
        VX.Box((-2.0, 3.0, 0.0), (3.0, 1.0, 3.5), "stone", "pauldron1", rot(0, 0, -16)),
        VX.Box((-2.0, 1.0, 0.0), (2.5, 1.0, 3.0), "stone", "pauldron2", rot(0, 0, -10)),
        VX.Box((-2.0, -0.5, 0.0), (2.0, 0.5, 2.5), "silver", "pauldron3", rot(0, 0, -6)),
        B(-4, 2, -4, -1, -3, 3, "stone_lames", "rerebrace"),
        B(-4, 2, -10, -4, -3, 3, "stone", "vambrace"),
        B(-4, 2, -11, -10, -3, 3, "silver", "cuff"),
    ]
    belt = [B(-5, 5, -13, -10, -3, 3, "stone", "belt"), B(-1, 1, -12, -11, 3, 4, "door", "clasp")]
    right_leg = [
        B(-3, 2, -6, 0, -3, 3, "stone", "cuisse"),
        B(-3, 2, -8, -6, 3, 4, "silver", "poleyn"),
    ]
    tails_f = [B(-3, -1, -6, 0, -1, 0, "tail_r", "tail_r", hem=True), B(1, 3, -6, 0, -1, 0, "tail_l", "tail_l", hem=True)]
    tails_b = [B(-4, -1, -7, 0, -1, 0, "tail_br", "tail_r", hem=True), B(1, 4, -7, 0, -1, 0, "tail_br", "tail_l", hem=True)]
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "stone", "greave"),
        B(-3, 2, -13, -11, -3, 3, "stone_dark", "sabaton"),
        B(-2, 2, -13, -11, 3, 5, "stone", "toe"),
        B(-1, 1, -12, -11, 5, 6, "silver", "toe_tip"),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm)},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": tails_f, "back_cloth": tails_b},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="gatewrought", mats=mats, pieces=pieces, accent=AW.RAMPS["signal"][6])


def veilwoven():
    """Veilwoven (B, Channeler): veils layered over fine silver plate. A deep peaked hood whose point falls back, its
    opening in shadow; a silver circlet with a Veil-stone at the brow and a veil over mouth and nose so only the eyes
    show; veils falling off the shoulders in layers like petals; two veils crossing the breast to a glowing brooch;
    bell sleeves trailing ribbons; a long cape that ends in ribbons of two lengths, the Veil's eye stitched on it."""
    eye = ["..####..",
           ".#....#.",
           "#..##..#",
           ".#....#.",
           "..####.."]
    mats = {
        "hood": AW.Mat("veil", 4, 0.0, 0.2, details=(folds(3),)),
        "shadow": AW.Mat("veil", 1, 0.0, 0.2),
        "mask": AW.Mat("lilac", 5, 0.0, 0.25, details=(crown_hem("mirror", 6),)),
        "silver": AW.Mat("mirror", 5, 0.9, 0.95),
        "gem": AW.Mat("spirit", 5, 0.3, 1.0, emit=0.85, details=(gem_face(),), contact=2),
        "plate": AW.Mat("mirror", 3, 0.85, 0.9, details=(inset_ring("lilac", 5, 1, 1, metal=0.2, gloss=0.6),)),
        "plate_lames": AW.Mat("mirror", 3, 0.85, 0.9, details=(lames(2, -1),)),
        "petal": AW.Mat("lilac", 4, 0.0, 0.25, details=(hem("mirror", 6),)),
        "petal_deep": AW.Mat("veil", 5, 0.0, 0.25, details=(hem("mirror", 6),)),
        "drape": AW.Mat("lilac", 5, 0.0, 0.25, details=(folds(2), hem("mirror", 6))),
        "sleeve": AW.Mat("veil", 5, 0.0, 0.2, details=(folds(2), hem("mirror", 6))),
        "ribbon": AW.Mat("lilac", 6, 0.0, 0.25, details=(hem("mirror", 6),)),
        "cloth": AW.Mat("veil", 4, 0.0, 0.2, details=(folds(3), lining("lilac", 4))),
        "cloth_sigil": AW.Mat("veil", 4, 0.0, 0.2, details=(folds(3), sigil("mirror", 6), lining("lilac", 4))),
        "tongue": AW.Mat("veil", 4, 0.0, 0.2, details=(folds(2), hem("mirror", 6), lining("lilac", 4))),
        "sheer": AW.Mat("lilac", 5, 0.0, 0.2, details=(folds(2), hem("mirror", 6), lining("lilac", 6))),
        "legs": AW.Mat("veil", 3, 0.0, 0.2, details=(folds(3),)),
        "tabard": AW.Mat("lilac", 4, 0.0, 0.2, details=(folds(3), gold_hem_cloth("mirror", 6, width=2.0))),
        "tabard_wide": AW.Mat("lilac", 4, 0.0, 0.2, details=(folds(3), gold_hem_cloth("mirror", 6, width=3.0))),
    }
    head = [
        B(-5, -4, -1, 8, -5, 4, "hood", "hood"), B(4, 5, -1, 8, -5, 4, "hood", "hood"),
        B(-4, 4, -1, 8, -5, -4, "hood", "hood"),
        B(-5, 5, 8, 9, -5, 4, "hood", "hood"),                           # the crown, stepping in to a point
        B(-4, 4, 9, 10, -4, 3, "hood", "hood"),
        B(-3, 3, 10, 11, -3, 1, "hood", "hood"),
        B(-4, 4, 7, 8, 4, 5, "shadow", "brim"),                          # the opening's shadow over the brow
        B(-4, -3, -1, 7, 4, 5, "shadow", "brim"), B(3, 4, -1, 7, 4, 5, "shadow", "brim"),
        B(-3, 3, 6, 7, 4, 5, "silver", "circlet"),
        VX.rbox((0, 6.5, 5.5), (1, 1, 1), 45, "gem", "gem"),
        B(-3, 3, 0, 3, 4, 5, "mask", "mask"),                            # the veil over mouth and nose
    ]
    head += chain((0.0, 10.5, -2.0), [((0, 0.5, -1.0), 3, 4, 2, "hood"), ((0, -0.2, -1.0), 2, 2, 1, "hood")], "peak")
    u = (math.sin(math.radians(12)), math.cos(math.radians(12)))
    drape = [VX.Box((-2.5, -4.5, 4.5), (1.0, 4.5, 0.5), "drape", "drape", rot(0, 0, 12))]
    body = [
        B(-5, 5, -10, 0, -3, 3, "plate_lames", "plate"),
        B(-4, 4, -9, -1, 3, 4, "plate", "breast"),
        B(-1, 1, -2, 0, 4, 6, "gem", "brooch"),
        B(-6, 6, -1, 1, -6, -4, "silver", "yoke"),
    ] + drape + mirror(drape)
    right_arm = [
        VX.Box((-2.0, 3.0, 0.0), (3.0, 0.5, 4.0), "petal", "petal1", rot(0, 0, -22)),
        VX.Box((-2.5, 1.5, 0.0), (3.0, 0.5, 4.0), "petal_deep", "petal2", rot(0, 0, -30)),
        VX.Box((-3.0, 0.0, 0.0), (2.5, 0.5, 3.5), "petal", "petal3", rot(0, 0, -38)),
        B(-4, 2, -4, -1, -3, 3, "plate_lames", "rerebrace"),
        B(-4, 2, -6, -4, -3, 3, "sleeve", "sleeve"),
        B(-5, 2, -9, -6, -4, 4, "sleeve", "bell"),
        B(-4, 2, -10, -9, -3, 3, "silver", "cuff"),
        B(-5, -4, -14, -9, -2, 1, "ribbon", "ribbon"),
    ]
    cape0 = [B(-5, 5, -6, 0, -1, 0, "cloth", "cape")]
    cape1 = [B(-5, 5, -6, 0, -1, 0, "cloth_sigil", "cape", sigil=eye, sigil_top=0)]
    cape2 = [B(x0, x0 + 2, -ln, 0, -1, 0, m, f"cape_t{x0}", hem=True)
             for x0, ln, m in ((-5, 8, "tongue"), (-3, 6, "sheer"), (-1, 9, "tongue"), (1, 6, "sheer"), (3, 8, "tongue"))]
    belt = [B(-5, 5, -13, -10, -3, 3, "drape", "belt"), B(-1, 1, -12, -10, 3, 4, "gem", "clasp")]
    right_leg = [B(-3, 2, -6, 0, -3, 3, "legs", "legs"), B(-3, 2, -8, -6, 3, 4, "silver", "knee")]
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "plate_lames", "greave"),
        B(-3, 2, -13, -11, -3, 3, "silver", "sole"),
        B(-2, 2, -13, -11, 3, 5, "silver", "toe"),
        B(-1, 1, -12, -11, 5, 6, "silver", "toe_tip"),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm), "cape0": cape0, "cape1": cape1, "cape2": cape2},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg),
                     "front_cloth": [B(-2, 2, -6, 0, 0, 1, "tabard", "tabard", hem=True)],
                     "back_cloth": [B(-3, 3, -7, 0, -1, 0, "tabard_wide", "tabard", hem=True)]},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    # the rune: a Veil-stone in a silver diamond
    rune = [VX.rbox((0, 0, 0), (2, 2, 2), 45, "gem", "rgem")]
    rune += AW.frame((0, 0, 0), 4, 2, 1, 45, "silver", "rframe")
    return dict(id="veilwoven", mats=mats, pieces=pieces, rune=rune, accent=AW.RAMPS["spirit"][6])


def sovereign():
    """Sovereign Weave (S, Lord-forged): the Hollow Court's own. Black lacquered plate traced in silver; a smooth mask
    with violet eyes under a hollow silver crown of spires lit at their tips; a high collar of two fins behind the
    head and fins sweeping back from the pauldrons; the Court's hollow sigil on the breast; a long court cape lined in
    violet, cut to a point, the hollow crown stitched on it."""
    crown_sig = ["#..#..#",
                 "#.###.#",
                 "#######",
                 "#.....#",
                 "#######"]
    eyes = [(-3, 5), (-2, 4), (-1, 4), (0, 4), (1, 4), (2, 5)]
    mats = {
        "lacquer": AW.Mat("lacquer", 3, 0.6, 0.95),
        "lacquer_fil": AW.Mat("lacquer", 3, 0.6, 0.95, details=(inset_ring("mirror", 5, 1, 1, metal=0.9, gloss=0.95),)),
        "lacquer_lames": AW.Mat("lacquer", 3, 0.6, 0.95, details=(lames(2, -1),)),
        "lacquer_hem": AW.Mat("lacquer", 3, 0.6, 0.95, details=(hem("mirror", 5),)),
        "mask": AW.Mat("lacquer", 4, 0.6, 0.95, details=(marks(eyes, "veil", 7, emit=1.0),)),
        "silver": AW.Mat("mirror", 5, 0.9, 0.95),
        "spire": AW.Mat("mirror", 5, 0.9, 0.95, details=(glow_tip("veil", 7, 0.9, 1),)),
        "fin": AW.Mat("lacquer", 3, 0.6, 0.95, details=(rims("mirror", 5),)),
        "vambrace": AW.Mat("lacquer", 3, 0.6, 0.95, details=(lames(2, -1), edge_columns("mirror", 5), side_seam("veil", 0.9))),
        "glow": AW.Mat("veil", 7, 0.0, 0.5, emit=0.9, bevel=False),
        "cape": AW.Mat("lacquer", 2, 0.1, 0.35, details=(folds(3), gold_hem_cloth("mirror", 5, width=6.0), lining("veil", 3))),
        "cape_sigil": AW.Mat("lacquer", 2, 0.1, 0.35, details=(folds(3), gold_hem_cloth("mirror", 5, width=6.0), sigil("mirror", 5), lining("veil", 3))),
        "tabard": AW.Mat("veil", 3, 0.0, 0.25, details=(folds(3), gold_hem_cloth("mirror", 5, width=2.0))),
        "tabard_wide": AW.Mat("veil", 3, 0.0, 0.25, details=(folds(3), gold_hem_cloth("mirror", 5, width=3.0))),
    }
    head = [
        B(-5, 5, 8, 9, -5, 5, "lacquer", "helm"),
        B(-5, -4, -1, 8, -5, 4, "lacquer_fil", "helm"), B(4, 5, -1, 8, -5, 4, "lacquer_fil", "helm"),
        B(-4, 4, -1, 8, -5, -4, "lacquer_lames", "helm"),
        B(-4, 4, -1, 8, 4, 5, "mask", "mask"),
        B(-1, 1, 5, 8, 5, 6, "silver", "nasal"),
        # the hollow crown: a silver ring on the helm with spires, the tallest at the brow
        B(-5, 5, 9, 10, 4, 5, "silver", "crown"), B(-5, 5, 9, 10, -5, -4, "silver", "crown"),
        B(-5, -4, 9, 10, -4, 4, "silver", "crown"), B(4, 5, 9, 10, -4, 4, "silver", "crown"),
    ]
    for x0, h in ((-5, 2), (-3, 3), (2, 3), (4, 2)):
        head.append(B(x0, x0 + 1, 10, 10 + h, 4, 5, "spire", f"spire{x0}"))
    head.append(B(-1, 1, 10, 14, 4, 5, "spire", "spire_mid"))
    for x0, h in ((-3, 2), (-1, 3), (2, 2)):
        head.append(B(x0, x0 + 1 if x0 != -1 else 1, 10, 10 + h, -5, -4, "spire", f"spire_b{x0}"))
    head += [B(-5, -4, 10, 13, -1, 1, "spire", "spire_side"), B(4, 5, 10, 13, -1, 1, "spire", "spire_side")]
    body = [
        B(-5, 5, -10, 0, -3, 3, "lacquer_lames", "cuirass"),
        B(-4, 4, -9, -1, 3, 4, "lacquer_fil", "breast"),
        B(-6, 6, -1, 1, -6, -4, "silver", "yoke"),
        B(-1, 1, -5, -3, 4, 5, "glow", "heart"),
    ]
    body += AW.frame((0, -4, 4.5), 4, 2, 1, 0, "silver", "sigil")
    collar = [VX.Box((-5.5, 3.0, -5.5), (1.5, 3.5, 0.5), "fin", "collar", rot(-10, 0, 25))]
    body += collar + mirror(collar)
    right_arm = [
        VX.Box((-2.0, 3.5, 0.0), (3.0, 1.0, 3.5), "lacquer_hem", "pauldron1", rot(0, 0, -14)),
        VX.Box((-1.5, 1.5, 0.0), (2.5, 1.0, 3.0), "lacquer_hem", "pauldron2", rot(0, 0, -7)),
        VX.Box((-4.5, 6.0, -1.5), (0.5, 2.5, 2.0), "fin", "pfin", rot(-35, 0, 15)),
        B(-4, 2, -4, 0, -3, 3, "lacquer_lames", "rerebrace"),
        B(-4, 2, -10, -4, -3, 3, "vambrace", "vambrace"),
        B(-4, 2, -11, -10, -3, 3, "silver", "gauntlet"),
    ]
    cape0 = [B(-6, 6, -6, 0, -1, 0, "cape", "cape")]
    cape1 = [B(-6, 6, -6, 0, -1, 0, "cape_sigil", "cape", sigil=crown_sig, sigil_top=0)]
    cape2 = [B(x0, x0 + 2, -ln, 0, -1, 0, "cape", f"cape_t{x0}", hem=True)
             for x0, ln in ((-6, 7), (-4, 8), (-2, 9), (0, 9), (2, 8), (4, 7))]
    belt = [B(-5, 5, -13, -10, -3, 3, "lacquer_hem", "belt"), B(-1, 1, -12, -11, 3, 4, "glow", "clasp")]
    right_leg = [
        B(-3, 2, -6, 0, -3, 3, "lacquer_lames", "cuisse"),
        B(-4, -3, -5, 1, -3, 3, "lacquer_fil", "tasset"),
        B(-3, 2, -8, -6, 3, 4, "silver", "poleyn"),
    ]
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "lacquer_fil", "greave"),
        B(-3, 2, -13, -11, -3, 3, "lacquer", "sabaton"),
        B(-2, 2, -13, -11, 3, 5, "silver", "toe"),
        VX.Box((-3.5, -8.0, -3.5), (0.5, 1.5, 1.0), "fin", "heel_fin", rot(-30, 0, 0)),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm), "cape0": cape0, "cape1": cape1, "cape2": cape2},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg),
                     "front_cloth": [B(-2, 2, -6, 0, 0, 1, "tabard", "tabard", hem=True)],
                     "back_cloth": [B(-3, 3, -7, 0, -1, 0, "tabard_wide", "tabard", hem=True)]},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="sovereign", mats=mats, pieces=pieces, accent=AW.RAMPS["veil"][7])


# ---- P2: the E and D sets with items of their own (docs/armour/PLAN.md §6.2) -----------------------------------------

def bellguard():
    """Bellguard (E, Bulwark): a town's bell-founders' guard, cast rather than hammered. The helm is a bronze bell worn
    mouth down, its lip flaring at the neck, its crown stepping in to the loop it would hang from, a thin eye-slit that
    holds a green light; bell-dome pauldrons; a breastplate banded like a bell with a small bell cast on it; a skirt of
    copper plates. Green patina gathers along every lower edge and runs down in drips."""
    mats = {
        "bronze": AW.Mat("bronze", 3, 0.8, 0.8, details=(patina(rows=0, every=5, run=2),)),
        "bronze_plain": AW.Mat("bronze", 3, 0.8, 0.8),
        "bronze_lames": AW.Mat("bronze", 3, 0.8, 0.8, details=(lames(2, -1),)),
        "lip": AW.Mat("bronze", 4, 0.85, 0.85, details=(hem("bronze", 6), patina(rows=1, every=3, run=1))),
        "hoop": AW.Mat("bronze", 5, 0.85, 0.9),
        "bell": AW.Mat("bronze", 5, 0.85, 0.9),
        "copper": AW.Mat("copper", 4, 0.75, 0.75, details=(lames(2, -1), hem("bronze", 5))),
        "slit": AW.Mat("verdigris", 5, 0.0, 0.5, emit=0.7, bevel=False),
        "clapper": AW.Mat("verdigris", 5, 0.0, 0.5, emit=0.7, bevel=False),
        "leather": AW.Mat("hide", 2, 0.05, 0.35, details=(stitches("hide", 5, 2),)),
        "leather_plain": AW.Mat("hide", 2, 0.05, 0.35),
    }

    # ---- helmet: the bell mouth down over the head, its lip flaring at the neck, its shoulder stepping in to the
    # crown and the loop it would hang from; an eye-slit lit green
    head = [
        B(-5, -4, 1, 8, -5, 5, "bronze", "shell"), B(4, 5, 1, 8, -5, 5, "bronze", "shell"),
        B(-4, 4, 1, 8, -5, -4, "bronze", "shell"),
        B(-4, 4, 6, 8, 4, 5, "bronze_plain", "face"),
        B(-4, -3, 5, 6, 4, 5, "bronze_plain", "face"), B(3, 4, 5, 6, 4, 5, "bronze_plain", "face"),
        B(-3, 3, 5, 6, 4, 5, "slit", "slit"),
        B(-4, 4, 1, 5, 4, 5, "bronze_plain", "face"),
        B(-6, 6, -1, 1, -6, 6, "lip", "lip"),                            # the bell's lip, flaring at the neck
        B(-4, 4, 8, 9, -4, 4, "bronze_plain", "crown"),
        B(-3, 3, 9, 10, -3, 3, "bronze_plain", "crown"),
        B(-2, 2, 10, 11, -2, 2, "hoop", "crown"),
        B(-2, -1, 11, 13, -1, 1, "hoop", "loop"), B(1, 2, 11, 13, -1, 1, "hoop", "loop"),
        B(-2, 2, 13, 14, -1, 1, "hoop", "loop"),
    ]

    # ---- chestplate: banded like a bell, flaring at the waist; a small bell cast on the breast, its clapper lit
    body = [
        B(-5, 5, -10, 0, -3, 3, "bronze", "cuirass"),
        B(-4, 4, -8, 0, 3, 4, "bronze_plain", "breast"),
        B(-5, 5, -1, 0, 4, 5, "hoop", "band"), B(-5, 5, -9, -8, 4, 5, "hoop", "band"),
        B(-6, 6, -10, -8, -4, 5, "bronze_lames", "flare"),
        B(-1, 1, -3, -2, 4, 5, "bell", "bell"), B(-2, 2, -5, -3, 4, 5, "bell", "bell"),
        B(-3, 3, -6, -5, 4, 6, "bell", "bell"),
        B(-1, 1, -7, -6, 4, 6, "clapper", "clapper"),
        B(-5, 5, -1, 1, -4, -3, "hoop", "yoke"),
    ]
    # bell-dome pauldrons: a flared lip over the upper arm, the dome stepping in above it, a knob; tipped outward
    dome = [
        B(-6, 2, -1, 1, -4, 4, "lip", "plip"),
        B(-5, 1, 1, 2, -3, 3, "bronze_plain", "pdome"),
        B(-4, 0, 2, 3, -2, 2, "bronze_plain", "pdome"),
        B(-3, -1, 3, 4, -1, 1, "hoop", "pknob"),
    ]
    right_arm = turned(dome, rot(0, 0, -12), (-2.0, 0.0, 0.0)) + [
        B(-4, 2, -5, -1, -3, 3, "bronze_lames", "rerebrace"),
        B(-4, 2, -8, -5, -3, 3, "bronze", "vambrace"),
        B(-5, 3, -10, -8, -4, 4, "lip", "cuff"),                         # a cuff flaring at the wrist
        B(-4, 2, -11, -10, -3, 3, "leather_plain", "glove"),
    ]

    # ---- leggings: a leather belt with a bronze buckle; leather breeches; bronze knee domes; a copper plate skirt
    belt = [B(-5, 5, -13, -10, -3, 3, "leather", "belt"), B(-2, 2, -13, -10, 3, 4, "hoop", "buckle")]
    right_leg = [
        B(-3, 2, -6, 0, -3, 3, "leather_plain", "breech"),
        B(-3, 2, -8, -6, 3, 4, "bronze_plain", "poleyn"), B(-2, 1, -8, -6, 4, 5, "hoop", "poleyn_knob"),
    ]
    front_cloth = [B(-3, 3, -6, 0, 0, 1, "copper", "skirt", hem=True)]
    back_cloth = [B(-4, 4, -6, 0, -1, 0, "copper", "skirt", hem=True)]

    # ---- boots: bronze greaves under a flared cuff at the knee; heavy sabatons
    right_boot = [
        B(-4, 3, -7, -5, -4, 4, "lip", "cuff"),
        B(-3, 2, -11, -7, -3, 3, "bronze", "greave"),
        B(-3, 2, -13, -11, -3, 3, "bronze_lames", "sabaton"),
        B(-2, 2, -13, -11, 3, 5, "bronze_plain", "toe"),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm)},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="bellguard", mats=mats, pieces=pieces, accent=RAMPS["verdigris"][6])


def rootwarden():
    """Rootwarden (E, Channeler): what the old woods dress their wardens in. A moss hood under a bark circlet, and from
    it a crown of branching wood like a stag's, leaves at the tips; a knot of amber sap at the brow; bark plates bound to
    the body by roots, a sap-heart where they cross; moss on the shoulders; a leaf apron; boots wound in roots. The
    runes it leaves circling are amber seeds."""
    mats = {
        "moss": AW.Mat("moss", 2, 0.0, 0.15, details=(fur(-1, 0),)),
        "moss_light": AW.Mat("moss", 3, 0.0, 0.15, details=(fur(-1, 0),)),
        "mask": AW.Mat("bark", 4, 0.0, 0.15, details=(bark(), marks([(-3, 4), (-2, 4), (1, 4), (2, 4)], "amber", 4, emit=0.8))),
        "shadow": AW.Mat("moss", 0, 0.0, 0.2, bevel=False),
        "bark": AW.Mat("bark", 4, 0.0, 0.15, details=(bark(),)),
        "bark_plate": AW.Mat("bark", 5, 0.0, 0.15, details=(bark(), hem("bark", 2))),
        "wood": AW.Mat("bark", 5, 0.0, 0.2, details=(lames(3, -1),)),
        "wood_tip": AW.Mat("bark", 6, 0.0, 0.2),
        "root": AW.Mat("bark", 2, 0.0, 0.2, details=(wraps(2),)),
        "root_wrap": AW.Mat("bark", 3, 0.0, 0.2, details=(wraps(3),)),
        "leaf": AW.Mat("moss", 5, 0.0, 0.3),
        "apron": AW.Mat("moss", 4, 0.0, 0.25, details=(leaves(), hem("moss", 2))),
        "sap": AW.Mat("amber", 4, 0.2, 1.0, emit=0.6, details=(gem_face(),), contact=2),
        "sap_plain": AW.Mat("amber", 3, 0.2, 1.0, emit=0.6, bevel=False),
        "cloth": AW.Mat("moss", 2, 0.0, 0.2, details=(folds(3),)),
    }

    # ---- helmet: a moss hood over a bark mask, two eyes lit amber in it; a bark circlet round the hood, a sap knot at
    # the brow; antlers of wood branching from the circlet's sides
    head = [
        B(-5, -4, -1, 8, -5, 4, "moss", "hood"), B(4, 5, -1, 8, -5, 4, "moss", "hood"),
        B(-4, 4, -1, 8, -5, -4, "moss", "hood"),
        B(-5, 5, 8, 9, -5, 4, "moss", "hood"),
        B(-4, 4, 7, 8, 4, 5, "shadow", "brim"),
        B(-4, 4, -1, 7, 4, 5, "mask", "mask"),
        B(-6, 6, 7, 9, 4, 5, "bark", "circlet"), B(-6, 6, 7, 9, -6, -5, "bark", "circlet"),
        B(-6, -5, 7, 9, -5, 4, "bark", "circlet"), B(5, 6, 7, 9, -5, 4, "bark", "circlet"),
        VX.rbox((0, 8, 5.5), (2, 2, 2), 45, "sap", "sap"),
        B(-5, 5, -2, -1, -6, 4, "moss_light", "hem"),                   # the hood's moss falling round the neck
    ]
    beam = chain((5.5, 8.5, -0.5), [((0.9, 0.6, -0.2), 3, 2, 2, "wood"), ((0.5, 1.0, -0.2), 3, 2, 2, "wood"),
                                     ((0.15, 1.0, -0.1), 3, 1, 1, "wood"), ((-0.1, 1.0, 0.0), 2, 1, 1, "wood_tip")], "antler")
    tine1 = chain((8.0, 11.0, -0.8), [((0.3, 0.7, 0.8), 3, 1, 1, "wood"), ((0.1, 1.0, 0.4), 2, 1, 1, "wood_tip")], "tine1")
    tine2 = chain((8.8, 13.5, -1.0), [((0.8, 0.8, -0.3), 3, 1, 1, "wood"), ((0.4, 1.0, -0.1), 1, 1, 1, "wood_tip")], "tine2")
    head += beam + tine1 + tine2
    head += mirror(beam + tine1 + tine2)

    # ---- chestplate: bark plates on a moss tunic, two roots binding them crosswise, a sap heart where they cross; a
    # moss collar; bark pauldrons with moss on them; arms wound in roots
    body = [
        B(-5, 5, -10, 0, -3, 3, "cloth", "tunic"),
        B(-4, 4, -8, 0, 3, 4, "bark_plate", "breast"),
        B(-4, 4, -8, 0, -4, -3, "bark_plate", "back"),
        VX.Box((0, -4.5, 4.5), (0.5, 6.0, 0.5), "root", "root", rot(0, 0, 38)),
        VX.Box((0, -4.5, 4.5), (0.5, 6.0, 0.5), "root", "root2", rot(0, 0, -38)),
        VX.rbox((0, -4.5, 5.5), (2, 2, 2), 45, "sap", "heart"),
        B(-6, 6, -1, 1, -4, 4, "moss_light", "collar"),
    ]
    right_arm = [
        VX.Box((-2.0, 3.0, 0.0), (3.5, 1.5, 4.0), "bark_plate", "pauldron", rot(0, 0, -16)),
        VX.Box((-2.5, 4.5, 0.0), (2.5, 0.5, 3.0), "moss", "pmoss", rot(0, 0, -16)),
        B(-4, 2, -4, 2, -3, 3, "cloth", "sleeve"),
        B(-4, 2, -10, -4, -3, 3, "root_wrap", "bracer"),
        B(-5, -4, -8, -6, -1, 1, "sap_plain", "bracer_sap"),
        B(-4, 2, -11, -10, -3, 3, "bark", "glove"),
    ]

    # ---- leggings: a belt of twisted root, a sap clasp; moss breeches, bark knee plates; a leaf apron front and back
    belt = [B(-5, 5, -13, -10, -3, 3, "root", "belt"), B(-1, 1, -12, -11, 3, 4, "sap_plain", "clasp")]
    right_leg = [B(-3, 2, -6, 0, -3, 3, "cloth", "breech"), B(-3, 2, -8, -6, 3, 4, "bark_plate", "knee")]
    front_cloth = [B(-3, -1, -6, 0, 0, 1, "apron", "apron", hem=True), B(-1, 1, -5, 0, 0, 1, "apron", "apron2", hem=True),
                   B(1, 3, -6, 0, 0, 1, "apron", "apron3", hem=True)]
    back_cloth = [B(-4, 4, -6, 0, -1, 0, "apron", "apron", hem=True)]

    # ---- boots: wound in roots to the shin, bark soles
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "root_wrap", "boot"),
        B(-4, 3, -7, -6, -4, 4, "moss", "cuff"),
        B(-3, 2, -13, -11, -3, 3, "bark", "sole"),
        B(-2, 2, -13, -11, 3, 4, "bark", "toe"),
    ]
    # the rune: an amber seed in a husk of bark, two leaves above it
    rune = [VX.rbox((0, 0, 0), (2, 2, 2), 45, "sap", "seed"),
            B(-1, 1, -3, -2, -0.5, 0.5, "wood", "husk"),
            B(-2, -1, 1, 3, -0.5, 0.5, "leaf", "rleaf"), B(1, 2, 1, 3, -0.5, 0.5, "leaf", "rleaf")]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm)},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="rootwarden", mats=mats, pieces=pieces, rune=rune, accent=AW.RAMPS["amber"][5])


def amberwing():
    """Amberwing (E, Duelist): a duelist of the resin flats, all in black leather, with wings of amber: two pairs, long
    and narrow like a dragonfly's at rest, crossing on the back and rising past the shoulders, lit from within and
    holding a few dark motes the way old amber holds seeds. A closed black helm with a band of amber across the eyes
    and a swept amber fin; amber plates on the shoulders; an amber sash across the breast, knotted at the hip."""
    mats = {
        "leather": AW.Mat("jet", 4, 0.1, 0.45),
        "leather_lames": AW.Mat("jet", 4, 0.1, 0.45, details=(lames(2, -1),)),
        "leather_st": AW.Mat("jet", 5, 0.1, 0.45),
        "amber": AW.Mat("amber", 3, 0.15, 0.7, emit=0.08, details=(resin(17),)),
        "wing": AW.Mat("amber", 3, 0.1, 0.8, emit=0.35, details=(lames(3, -1), resin(11), rims("jet", 3, metal=0.1, gloss=0.4))),
        "case": AW.Mat("amber", 3, 0.15, 0.75, emit=0.12, details=(resin(17), hem("amber", 1, metal=0.15, gloss=0.6))),
        "thorax": AW.Mat("amber", 2, 0.15, 0.8, emit=0.2, details=(ridge_line("amber", 4),)),
        "visor": AW.Mat("amber", 2, 0.1, 1.0, emit=0.5, bevel=False),
        "fin": AW.Mat("amber", 2, 0.15, 0.6, emit=0.08, details=(resin(17),)),
        "fin_tip": AW.Mat("amber", 4, 0.15, 0.6, emit=0.15),
        "sash": AW.Mat("amber", 3, 0.0, 0.35, details=(folds(2), hem("amber", 5))),
        "sash_dark": AW.Mat("amber", 2, 0.0, 0.35, details=(folds(2),)),
    }

    # ---- helmet: closed black leather, a band of lit amber across the eyes; a fin swept back over the crown
    head = [
        B(-5, -4, -1, 8, -5, 3, "leather", "cap"), B(4, 5, -1, 8, -5, 3, "leather", "cap"),
        B(-4, 4, -1, 8, -5, -4, "leather_lames", "cap"),
        B(-5, 5, 8, 9, -5, 4, "leather_st", "cap"),
        B(-4, 4, 5, 8, 4, 5, "leather", "brow"),
        B(-5, -4, -1, 3, 3, 5, "leather_st", "cheek"), B(4, 5, -1, 3, 3, 5, "leather_st", "cheek"),
        B(-4, 4, -1, 3, 4, 5, "leather_lames", "mask"),
        B(-5, 5, 3, 5, 4, 5, "visor", "visor"),
        B(-6, -5, 3, 5, -1, 5, "visor", "visor"), B(5, 6, 3, 5, -1, 5, "visor", "visor"),
    ]
    # the fin, stepped: rising from the brow and sweeping back past the nape to a point
    head += [B(-1, 1, 9, 10, -1, 5, "fin", "fin"), B(-1, 1, 10, 11, -4, 4, "fin", "fin"),
             B(-1, 1, 11, 12, -7, 2, "fin", "fin"), B(-1, 1, 12, 13, -7, -1, "fin", "fin"),
             B(-1, 1, 12, 13, -9, -7, "fin_tip", "fin"), B(-1, 1, 13, 14, -9, -5, "fin_tip", "fin")]

    # ---- chestplate: a black jerkin; the wing-cases on the back, parting at their feet, their tops over the shoulders;
    # a sash across the breast; case pauldrons sweeping back
    # the wings: the upper pair rising out past the shoulders, the lower pair falling out over the hips, crossing at
    # an amber ridge down the spine
    wings = [VX.Box((5.0, 3.5, -4.5), (2.0, 6.5, 0.5), "wing", "wing_up", rot(0, 0, -28)),
             VX.Box((5.0, -7.5, -3.5), (2.0, 5.0, 0.5), "wing", "wing_low", rot(0, 0, 26))]
    body = [
        B(-5, 5, -10, 0, -3, 3, "leather_lames", "jerkin"),
        B(-4, 4, -9, -1, 3, 4, "leather", "breast"),
        VX.Box((0, -4.5, 4.5), (1.0, 6.5, 0.5), "sash", "sash", rot(0, 0, -40)),
        B(-5, 5, 0, 2, -4, -2, "leather", "collar"),
        B(-1, 1, -9, -4, -4, -3, "thorax", "thorax"),
    ] + wings + mirror(wings)
    right_arm = [
        VX.Box((-2.0, 3.0, -0.8), (3.0, 1.0, 4.0), "case", "pauldron", rot(-12, 0, -18)),
        VX.Box((-2.5, 1.5, -1.0), (2.5, 1.0, 3.5), "amber", "pauldron2", rot(-12, 0, -10)),
        B(-4, 2, -5, -1, -3, 3, "leather", "sleeve"),
        B(-4, 2, -10, -5, -3, 3, "leather_lames", "bracer"),
        B(-5, -4, -9, -6, -1, 1, "amber", "bracer_inlay"),
        B(-4, 2, -11, -10, -3, 3, "leather", "glove"),
    ]

    # ---- leggings: the sash tied at the waist, its ends hanging at the left hip; black breeches, amber knee caps
    belt = [B(-5, 5, -13, -10, -3, 3, "sash", "belt"), B(2, 4, -13, -10, 3, 4, "sash_dark", "knot")]
    right_leg = [B(-3, 2, -6, 0, -3, 3, "leather_lames", "breech"), B(-3, 2, -8, -6, 3, 4, "amber", "knee")]
    front_cloth = [B(1, 2, -6, 0, 0, 1, "sash", "tail", hem=True), B(2, 3, -4, 0, 0, 1, "sash_dark", "tail2", hem=True)]

    # ---- boots: tall black boots folded at the top, amber toe caps
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "leather", "boot"),
        B(-4, 3, -6, -5, -4, 4, "leather_st", "fold"),
        B(-3, 2, -13, -11, -3, 3, "leather", "sole"),
        B(-2, 2, -13, -11, 3, 5, "amber", "toe"),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm)},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="amberwing", mats=mats, pieces=pieces, accent=AW.RAMPS["amber"][4])


def cleftrunner():
    """Cleftrunner (D, Strider): the canyon messengers who run the walls of the clefts. A dusk-blue hood, and a long red
    scarf wound twice round the throat and up over mouth and nose so only the eyes show, its tail streaming out behind
    as they run; slim lamellar laced in red over the breast and the right shoulder, a red band on the left arm; iron on
    the forearms and shins and nowhere else; the rest wrapped close for speed."""
    mats = {
        "hood": AW.Mat("storm", 3, 0.0, 0.2, details=(folds(3),)),
        "hood_dark": AW.Mat("storm", 1, 0.0, 0.2),
        "cloth": AW.Mat("storm", 2, 0.0, 0.2, details=(folds(3),)),
        "wrap": AW.Mat("storm", 2, 0.0, 0.2, details=(wraps(4),)),
        "iron": AW.Mat("flint", 5, 0.8, 0.75),
        "iron_hem": AW.Mat("flint", 5, 0.8, 0.75, details=(hem("flint", 7),)),
        "lamellar": AW.Mat("storm", 3, 0.6, 0.7, details=(lamellar(), hem("crimson", 3, metal=0.0, gloss=0.3))),
        "scarf": AW.Mat("crimson", 2, 0.0, 0.25, details=(folds(4),)),
        "scarf_light": AW.Mat("crimson", 3, 0.0, 0.25, details=(folds(4),)),
        "scarf_hem": AW.Mat("crimson", 2, 0.0, 0.25, details=(folds(4), hem("crimson", 1, metal=0.0, gloss=0.3))),
        "strap": AW.Mat("hide", 2, 0.05, 0.35),
    }

    # ---- helmet: the hood, peaked behind; the scarf over mouth and nose; the eyes in the hood's shadow
    head = [
        B(-5, -4, -1, 8, -5, 5, "hood", "hood"), B(4, 5, -1, 8, -5, 5, "hood", "hood"),
        B(-4, 4, -1, 8, -5, -4, "hood", "hood"),
        B(-5, 5, 8, 9, -5, 5, "hood", "hood"),
        B(-4, 4, 9, 10, -5, 2, "hood", "peak"),
        B(-4, 4, 7, 8, 4, 5, "hood_dark", "brow"),
        B(-5, 5, -1, 3, 4, 6, "scarf", "veil"),
    ]

    # ---- chestplate: close wraps; lamellar laced on the breast, a strap across the back; the scarf wound at the
    # throat; lamellar on the right shoulder only, a red band on the left arm; iron bracers
    body = [
        B(-5, 5, -10, 0, -3, 3, "cloth", "tunic"),
        B(-5, 5, -9, -1, 3, 4, "lamellar", "lamellar"),
        VX.Box((0, -6.5, -3.5), (1, 4.5, 0.5), "strap", "strap_back", rot(0, 0, -35)),
        B(-6, 6, -2, 1, -4, 4, "scarf", "scarf"),
        B(-5, 5, 1, 2, -3, 3, "scarf_light", "scarf_top"),
    ]
    right_arm = [
        VX.Box((-2.0, 2.5, 0.0), (3.0, 1.0, 3.5), "lamellar", "pauldron", rot(0, 0, -16)),
        VX.Box((-2.5, 0.8, 0.0), (2.5, 1.0, 4.0), "lamellar", "pauldron2", rot(0, 0, -10)),
        B(-4, 2, -5, 2, -3, 3, "cloth", "sleeve"),
        B(-4, 2, -10, -5, -3, 3, "wrap", "forearm"),
        B(-5, -4, -9, -5, -2, 2, "iron_hem", "bracer"),
        B(-4, 2, -11, -10, -3, 3, "strap", "glove"),
    ]
    left_arm = [b for b in mirror(right_arm) if not b.part.startswith("pauldron")] + [
        B(-3, 5, -3, -1, -4, 4, "scarf", "armband")]
    # the scarf's tail, streaming from the left of the throat
    cape0 = [B(0, 5, -6, 0, -1, 0, "scarf", "tail")]
    cape1 = [B(0, 5, -6, 0, -1, 0, "scarf_light", "tail")]
    cape2 = [B(0, 3, -8, 0, -1, 0, "scarf_hem", "tail_a", hem=True), B(3, 5, -5, 0, -1, 0, "scarf_hem", "tail_b", hem=True)]

    # ---- leggings: a strap belt, an iron buckle; close cloth; a short split flap front and back
    belt = [B(-5, 5, -13, -10, -3, 3, "strap", "belt"), B(-1, 1, -12, -11, 3, 4, "iron", "buckle")]
    right_leg = [B(-3, 2, -6, 0, -3, 3, "cloth", "leg"), B(-2, 1, -7, -6, 3, 4, "iron", "knee")]
    front_cloth = [B(-2, 0, -5, 0, 0, 1, "cloth", "flap", hem=True), B(0, 2, -4, 0, 0, 1, "cloth", "flap2", hem=True)]
    back_cloth = [B(-3, 3, -5, 0, -1, 0, "cloth", "flap", hem=True)]

    # ---- boots: wrapped to the knee, an iron shin plate and toe cap
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "wrap", "boot"),
        B(-2, 1, -10, -6, 3, 4, "iron_hem", "shin"),
        B(-3, 2, -13, -11, -3, 3, "strap", "sole"),
        B(-2, 2, -13, -11, 3, 4, "iron", "toe"),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": left_arm, "cape0": cape0, "cape1": cape1, "cape2": cape2},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="cleftrunner", mats=mats, pieces=pieces, accent=AW.RAMPS["storm"][6])


def lanternlight():
    """Lanternlight (D, Channeler): the lamplighters of the deep roads, who walk where nothing else is lit. A deep cowl
    whose face is only dark, a small lamp hanging at its brow; a tall iron collar to the nose; charcoal robes trimmed
    in brass; a brass lantern caged on each shoulder, burning. The runes it leaves circling are little lamps."""
    mats = {
        "cowl": AW.Mat("slate", 2, 0.0, 0.2, details=(folds(3),)),
        "void": AW.Mat("void", 1, 0.0, 0.1, bevel=False),
        "robe": AW.Mat("slate", 2, 0.0, 0.2, details=(folds(3),)),
        "robe_hem": AW.Mat("slate", 2, 0.0, 0.2, details=(folds(3), gold_hem_cloth("brass", 5, width=3.0))),
        "robe_hem_w": AW.Mat("slate", 2, 0.0, 0.2, details=(folds(3), gold_hem_cloth("brass", 5, width=4.0))),
        "iron": AW.Mat("flint", 3, 0.75, 0.7),
        "iron_rim": AW.Mat("flint", 3, 0.75, 0.7, details=(rims("brass", 5),)),
        "iron_lames": AW.Mat("flint", 3, 0.75, 0.7, details=(lames(2, -1),)),
        "brass": AW.Mat("brass", 5, 0.85, 0.9),
        "glass": AW.Mat("amber", 5, 0.1, 1.0, emit=0.75, bevel=False),
        "glass_small": AW.Mat("amber", 3, 0.1, 1.0, emit=0.6, bevel=False),
    }

    def lantern(c, part, size=1.0):
        """A brass lantern: a cap and a base, four corner posts, a burning glass between them."""
        x, y, z = c
        w = 2.0 * size
        return [B(x - w, x + w, y + 2.5 * size, y + 3.5 * size, z - w, z + w, "brass", part),
                B(x - w, x + w, y - 2.5 * size, y - 1.5 * size, z - w, z + w, "brass", part),
                B(x - 1 * size, x + 1 * size, y + 3.5 * size, y + 4.5 * size, z - 0.5 * size, z + 0.5 * size, "brass", part),
                B(x - w, x - w + 1, y - 1.5 * size, y + 2.5 * size, z - w, z - w + 1, "brass", part),
                B(x + w - 1, x + w, y - 1.5 * size, y + 2.5 * size, z - w, z - w + 1, "brass", part),
                B(x - w, x - w + 1, y - 1.5 * size, y + 2.5 * size, z + w - 1, z + w, "brass", part),
                B(x + w - 1, x + w, y - 1.5 * size, y + 2.5 * size, z + w - 1, z + w, "brass", part),
                B(x - w + 1, x + w - 1, y - 1.5 * size, y + 2.5 * size, z - w + 1, z + w - 1, "glass", part + "_glass")]

    # ---- helmet: a deep cowl reaching past the face, the opening only dark; a little lamp hanging from its peak
    head = [
        B(-5, -4, -1, 9, -5, 6, "cowl", "cowl"), B(4, 5, -1, 9, -5, 6, "cowl", "cowl"),
        B(-4, 4, -1, 9, -5, -4, "cowl", "cowl"),
        B(-5, 5, 9, 10, -5, 6, "cowl", "cowl"),
        B(-4, 4, 7, 9, 5, 6, "cowl", "brim"),
        B(-4, 4, -1, 7, 4, 5, "void", "void"),
        B(-3, 3, 10, 11, -4, 3, "cowl", "peak"),
        B(-0.5, 0.5, 6, 7, 5.5, 6.5, "brass", "lamp_chain"),
    ]
    head += [B(-1, 1, 5, 6, 5.5, 7.5, "brass", "lamp"), B(-1, 1, 3, 5, 5.5, 7.5, "glass_small", "lamp_glass"),
             B(-1, 1, 2, 3, 5.5, 7.5, "brass", "lamp")]

    # ---- chestplate: the robe; a tall iron collar rising to the nose; a brass bar across the breast; iron pauldrons
    # with a burning lantern caged on each
    body = [
        B(-5, 5, -10, 0, -3, 3, "robe", "robe"),
        B(-4, 4, -8, -1, 3, 4, "robe_hem", "breast"),
        B(-4, 4, -3, -2, 4, 5, "brass", "bar"),
        B(-5, 5, 0, 4, 3, 5, "iron_rim", "collar"),
        B(-5, -4, 0, 4, -3, 3, "iron_rim", "collar"), B(4, 5, 0, 4, -3, 3, "iron_rim", "collar"),
        B(-5, 5, 0, 4, -4, -3, "iron_rim", "collar"),
    ]
    right_arm = [
        VX.Box((-2.0, 2.5, 0.0), (3.5, 1.0, 4.0), "iron_rim", "pauldron", rot(0, 0, -12)),
        B(-4, 2, -6, 2, -3, 3, "robe", "sleeve"),
        B(-4, 2, -10, -6, -3, 3, "iron_lames", "vambrace"),
        B(-5, 3, -7, -6, -4, 4, "brass", "cuff"),
        B(-4, 2, -11, -10, -3, 3, "iron", "glove"),
    ] + lantern((-3.0, 6.5, 0.0), "lantern")

    # ---- leggings: a brass-clasped belt; long robe skirts to the knee, front and back; dark breeches
    belt = [B(-5, 5, -13, -10, -3, 3, "iron_rim", "belt"), B(-1, 1, -12, -11, 3, 4, "glass_small", "clasp")]
    right_leg = [B(-3, 2, -6, 0, -3, 3, "robe", "leg")]
    front_cloth = [B(-3, 3, -6, 0, 0, 1, "robe_hem", "skirt", hem=True)]
    back_cloth = [B(-4, 4, -6, 0, -1, 0, "robe_hem_w", "skirt", hem=True)]

    # ---- boots: plain iron boots, a brass toe
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "iron_lames", "boot"),
        B(-3, 2, -13, -11, -3, 3, "iron", "sole"),
        B(-2, 2, -13, -11, 3, 4, "brass", "toe"),
    ]
    rune = [B(-1, 1, 1, 2, -1, 1, "brass", "rcap"), B(-0.5, 0.5, 2, 3, -0.5, 0.5, "brass", "rhook"),
            B(-1, 1, -1, 1, -1, 1, "glass_small", "rglass"), B(-1, 1, -2, -1, -1, 1, "brass", "rbase")]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm)},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="lanternlight", mats=mats, pieces=pieces, rune=rune, accent=AW.RAMPS["ember"][6])


def bladeward():
    """Bladeward (D, Duelist): a fencing master's harness. An oxblood brigandine studded in brass; a close steel helm
    cut in a dark T, a red plume streaming back from its ridge; one great steel pauldron on the left, standing up to
    guard the neck, a light one on the right; a half cape over the left shoulder; steel sabatons."""
    mats = {
        "steel": AW.Mat("flint", 5, 0.85, 0.85),
        "steel_hem": AW.Mat("flint", 5, 0.85, 0.85, details=(hem("bronze", 6),)),
        "steel_lames": AW.Mat("flint", 5, 0.85, 0.85, details=(lames(2, -1),)),
        "slit": AW.Mat("flint", 0, 0.2, 0.3, bevel=False),
        "brig": AW.Mat("wine", 3, 0.0, 0.3, details=(rivet_grid("bronze", 5, 3),)),
        "brig_plain": AW.Mat("wine", 3, 0.0, 0.3),
        "plume": AW.Mat("crimson", 2, 0.0, 0.2, details=(fur(-1, 1),)),
        "cape": AW.Mat("wine", 3, 0.0, 0.25, details=(folds(2), gold_hem_cloth("bronze", 6, width=6.0), lining("wine", 5))),
        "brass": AW.Mat("bronze", 6, 0.85, 0.9),
        "leather": AW.Mat("hide", 2, 0.05, 0.35),
    }

    # ---- helmet: a close steel helm, its front cut in a dark T for the eyes and breath; a ridge front to back, the
    # plume streaming from it; a bronze rim at the neck
    head = [
        B(-5, -4, -1, 9, -5, 5, "steel_hem", "helm"), B(4, 5, -1, 9, -5, 5, "steel_hem", "helm"),
        B(-4, 4, -1, 9, -5, -4, "steel_hem", "helm"),
        B(-5, 5, 9, 10, -5, 5, "steel", "helm"),
        B(-4, 4, 5, 9, 4, 5, "steel", "face"),
        B(-4, -3, 4, 5, 4, 5, "steel", "face"), B(3, 4, 4, 5, 4, 5, "steel", "face"),
        B(-3, 3, 4, 5, 4, 5, "slit", "slit"),
        B(-4, -1, -1, 4, 4, 5, "steel_hem", "face"), B(1, 4, -1, 4, 4, 5, "steel_hem", "face"),
        B(-1, 1, 0, 4, 4, 5, "slit", "slit"),
        B(-1, 1, -1, 0, 4, 5, "steel_hem", "face"),
        B(-1, 1, 10, 11, -4, 5, "steel", "ridge"),
    ]
    # the plume, stepped: up off the ridge and streaming back and down
    head += [B(-1, 1, 11, 14, -3, 1, "plume", "plume"), B(-1, 1, 10, 14, -6, -3, "plume", "plume"),
             B(-1, 1, 8, 13, -9, -6, "plume", "plume"), B(-1, 1, 5, 10, -11, -9, "plume", "plume")]

    # ---- chestplate: the brigandine and its high collar; the great pauldron on the left, its haute-piece standing
    # at the neck; a riveted sleeve and a steel elbow on the right; a half cape from the left shoulder
    body = [
        B(-5, 5, -10, 0, -3, 3, "brig", "brig"),
        B(-4, 4, -9, -1, 3, 4, "brig", "breast"),
        B(-1, 1, -9, -1, 4, 5, "steel", "placket"),
        B(-5, -4, 0, 3, -3, 2, "brig_plain", "collar"), B(4, 5, 0, 3, -3, 2, "brig_plain", "collar"),
        B(-5, 5, 0, 3, -4, -3, "brig_plain", "collar"),
        B(-6, 6, -10, -9, -4, 4, "leather", "girdle"),
    ]
    right_arm = [
        VX.Box((-2.0, 2.0, 0.0), (3.0, 1.0, 3.5), "steel_hem", "spaulder", rot(0, 0, -14)),
        B(-4, 2, -5, 2, -3, 3, "brig", "sleeve"),
        B(-4, 2, -7, -5, -4, -3, "steel", "couter"),
        B(-4, 2, -10, -5, -3, 3, "steel_lames", "vambrace"),
        B(-4, 2, -11, -10, -3, 3, "leather", "glove"),
    ]
    left_arm = mirror([b for b in right_arm if b.part not in ("sleeve", "spaulder")]) + [
        VX.Box((2.5, 3.5, 0.0), (4.0, 1.5, 4.5), "steel_hem", "pauldron1", rot(0, 0, 16)),
        VX.Box((2.0, 1.0, 0.0), (3.5, 1.0, 4.0), "steel", "pauldron2", rot(0, 0, 7)),
        B(-1, 5, -2, 0, -3, 3, "steel_hem", "pauldron3"),
        VX.Box((-0.2, 6.0, 0.0), (0.5, 2.0, 3.5), "steel_hem", "haute", rot(0, 0, 14)),
        B(-2, 4, -4, -1, -3, 3, "steel_lames", "rerebrace"),
    ]
    cape0 = [B(0, 6, -6, 0, -1, 0, "cape", "cape")]
    cape1 = [B(0, 6, -6, 0, -1, 0, "cape", "cape")]
    cape2 = [B(x0, x0 + 2, -ln, 0, -1, 0, "cape", f"cape_t{x0}", hem=True) for x0, ln in ((0, 4), (2, 6), (4, 7))]

    # ---- leggings: steel cuisses, riveted tassets at the hips, a sword belt; two short tassets in front
    belt = [B(-5, 5, -13, -10, -3, 3, "leather", "belt"), B(-2, 2, -13, -10, 3, 4, "brass", "buckle")]
    right_leg = [
        B(-3, 2, -6, 0, -3, 3, "steel_lames", "cuisse"),
        B(-4, -3, -5, 1, -3, 3, "brig", "tasset"),
        B(-3, 2, -8, -6, 3, 4, "steel", "poleyn"),
    ]
    front_cloth = [B(-3, -1, -4, 0, 0, 1, "brig", "tasset_f", hem=True), B(1, 3, -4, 0, 0, 1, "brig", "tasset_f2", hem=True)]

    # ---- boots: steel greaves, pointed sabatons in lames
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "steel", "greave"),
        B(-3, 2, -13, -11, -3, 3, "steel_lames", "sabaton"),
        B(-2, 2, -13, -11, 3, 5, "steel_lames", "toe"),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": left_arm, "cape0": cape0, "cape1": cape1, "cape2": cape2},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="bladeward", mats=mats, pieces=pieces, accent=AW.RAMPS["crimson"][5])


def hornlord():
    """Hornlord (D, Lord-forged): forged from what a D-rank gate's Lord leaves. Blackened iron with embers burning in
    its cracks; a closed helm with a burning slit, and great pale horns banded in gold at the root that sweep out
    from the temples, curl forward and rise like a bull's; a heavy fur mantle; a short ragged fur cape; sabatons split
    like hooves."""
    cracks = [(0, -1), (0, -2), (1, -3), (1, -4), (0, -5), (-1, -6), (-1, -7), (2, -4), (3, -4), (-1, -2), (-2, -2)]
    mats = {
        "iron": AW.Mat("soot", 4, 0.65, 0.7),
        "iron_hem": AW.Mat("soot", 4, 0.65, 0.7, details=(hem("ember", 4, emit=0.6),)),
        "iron_lames": AW.Mat("soot", 4, 0.65, 0.7, details=(lames(2, -1),)),
        "iron_cracks": AW.Mat("soot", 4, 0.65, 0.7, details=(marks(cracks, "ember", 5, emit=0.9),)),
        "iron_seam": AW.Mat("soot", 4, 0.65, 0.7, details=(lames(2, -1), side_seam("ember", 0.85))),
        "eye": AW.Mat("ember", 5, 0.0, 0.4, emit=0.9, bevel=False),
        "buckle": AW.Mat("ember", 4, 0.0, 0.4, emit=0.7, bevel=False),
        "gold": AW.Mat("gold", 5, 0.8, 0.9),
        "horn": AW.Mat("bone", 6, 0.1, 0.55, details=(lames(2, -1),)),
        "horn_mid": AW.Mat("bone", 4, 0.1, 0.55, details=(lames(2, -1),)),
        "horn_tip": AW.Mat("obsidian", 4, 0.3, 0.7),
        "fur": AW.Mat("pelt", 3, 0.0, 0.1, details=(fur(),)),
        "fur_dark": AW.Mat("pelt", 2, 0.0, 0.1, details=(fur(),)),
    }

    # ---- helmet: closed, a heavy brow over a burning slit, a gold ring through the front plate; the horns
    head = [
        B(-5, -4, -1, 8, -5, 5, "iron", "helm"), B(4, 5, -1, 8, -5, 5, "iron", "helm"),
        B(-4, 4, -1, 8, -5, -4, "iron_lames", "helm"),
        B(-5, 5, 8, 9, -5, 5, "iron", "helm"),
        B(-5, 5, 5, 8, 5, 6, "iron", "brow"),
        B(-3, 3, 4, 5, 4, 5, "eye", "eye"),
        B(-4, -3, 4, 5, 4, 5, "iron", "face"), B(3, 4, 4, 5, 4, 5, "iron", "face"),
        B(-4, 4, -1, 4, 4, 5, "iron", "face"),
        B(-1, 1, 9, 10, -3, 4, "iron", "ridge"),
    ]
    horn = chain((4.5, 6.5, 1.0), [((1.0, 0.15, 0.0), 5, 3, 3, "horn"), ((0.75, 0.3, 0.7), 4, 3, 3, "horn"),
                                   ((0.15, 0.7, 0.8), 4, 2, 2, "horn_mid"), ((-0.3, 1.0, 0.35), 3, 2, 2, "horn_mid"),
                                   ((-0.4, 1.0, -0.1), 3, 1, 1, "horn_tip")], "horn")
    horn += [B(6, 7, 4.5, 8.5, -1, 3, "gold", "horn_band")]
    head += horn + mirror(horn)

    # ---- chestplate: blackened plate cracked with embers; a great fur mantle; heavy pauldrons with a burning seam
    body = [
        B(-5, 5, -10, 0, -3, 3, "iron_lames", "cuirass"),
        B(-4, 4, -8, 0, 3, 4, "iron_cracks", "breast"),
        B(-7, 7, -2, 2, -5, 5, "fur", "mantle"),
        B(-5, 5, 2, 3, -5, 2, "fur_dark", "mantle_top"),
        B(-6, 6, -10, -8, -4, 4, "iron_hem", "fauld"),
    ]
    right_arm = [
        VX.Box((-2.5, 2.0, 0.0), (4.0, 1.5, 4.5), "iron_hem", "pauldron", rot(0, 0, -14)),
        VX.Box((-2.5, 0.0, 0.0), (3.0, 1.0, 3.5), "iron", "pauldron2", rot(0, 0, -6)),
        B(-4, 2, -5, -1, -3, 3, "iron_lames", "rerebrace"),
        B(-4, 2, -10, -5, -3, 3, "iron_seam", "vambrace"),
        B(-4, 2, -11, -10, -3, 3, "iron", "gauntlet"),
    ]
    cape0 = [B(-5, 5, -6, 0, -1, 0, "fur", "cape")]
    cape1 = [B(x0, x0 + 2, -ln, 0, -1, 0, "fur_dark", f"cape_t{x0}", hem=True) for x0, ln in ((-5, 4), (-3, 6), (-1, 5), (1, 6), (3, 4))]

    # ---- leggings: an iron belt with a burning buckle; iron cuisses; plate tassets front and back
    belt = [B(-5, 5, -13, -10, -3, 3, "iron_hem", "belt"), B(-1, 1, -12, -11, 3, 4, "buckle", "buckle")]
    right_leg = [B(-3, 2, -6, 0, -3, 3, "iron_lames", "cuisse"), B(-3, 2, -8, -6, 3, 4, "iron", "poleyn")]
    front_cloth = [B(-3, 3, -5, 0, 0, 1, "iron_lames", "tasset", hem=True)]
    back_cloth = [B(-4, 4, -6, 0, -1, 0, "fur_dark", "tail", hem=True)]

    # ---- boots: iron greaves, sabatons split like a hoof
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "iron_seam", "greave"),
        B(-3, 2, -13, -11, -3, 3, "iron", "sabaton"),
        B(-3, -1, -13, -11, 3, 5, "iron", "hoof"), B(0, 2, -13, -11, 3, 5, "iron", "hoof2"),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm), "cape0": cape0, "cape1": cape1},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="hornlord", mats=mats, pieces=pieces, accent=AW.RAMPS["ember"][5])


# ---- P3: the C and B sets with items of their own (docs/armour/PLAN.md §6.2) -----------------------------------------

def closed_helm(side, back, top_, brow, face, slit):
    """A closed helm (Hornlord's): sides, back and top a texel off the skull, a brow standing a texel proud over one
    slit, the lower face plate; materials by part."""
    return [
        B(-5, -4, -1, 8, -5, 5, side, "helm"), B(4, 5, -1, 8, -5, 5, side, "helm"),
        B(-4, 4, -1, 8, -5, -4, back, "helm"),
        B(-5, 5, 8, 9, -5, 5, top_, "helm"),
        B(-5, 5, 5, 8, 5, 6, brow, "brow"),
        B(-3, 3, 4, 5, 4, 5, slit, "slit"),
        B(-4, -3, 4, 5, 4, 5, face, "face"), B(3, 4, 4, 5, 4, 5, face, "face"),
        B(-4, 4, -1, 4, 4, 5, face, "face"),
    ]


def vaultbreaker():
    """Vaultbreaker (C, Bulwark): the plate of those who break the gates' sealed vaults. Massive steel studded with
    diamond; a closed helm with a T of cold light, a ram's horns curling round the ears; a round pauldron on the
    sword arm and on the other a shield of a pauldron, a diamond boss at its heart; a heavy fauld; sabatons like
    anvils."""
    mats = {
        "steel": AW.Mat("flint", 5, 0.85, 0.85),
        "steel_lames": AW.Mat("flint", 5, 0.85, 0.85, details=(lames(2, -1),)),
        "steel_rim": AW.Mat("flint", 5, 0.85, 0.85, details=(rims("mirror", 6),)),
        "steel_dark": AW.Mat("flint", 2, 0.8, 0.75, details=(lames(2, -1),)),
        "studs": AW.Mat("flint", 5, 0.85, 0.85, details=(rivet_grid("pearl", 6, 2),)),
        "diamond": AW.Mat("pearl", 5, 0.2, 1.0, emit=0.25, bevel=False),
        "slit": AW.Mat("ice", 5, 0.0, 0.5, emit=0.75, bevel=False),
        "horn": AW.Mat("flint", 3, 0.6, 0.7, details=(lames(1, -1),)),
        "horn_tip": AW.Mat("flint", 5, 0.6, 0.8),
        "leather": AW.Mat("slate", 1, 0.05, 0.35, details=(stitches("slate", 3, 2),)),
    }

    # ---- helmet: closed and heavy; a T of light; a raised comb; ram's horns sweeping back and curling forward
    head = closed_helm("steel_rim", "steel_lames", "steel", "steel", "steel_lames", "slit") + [
        B(-1, 1, 9, 10, -4, 5, "steel_rim", "comb"), B(-1, 1, 9, 10, 5, 6, "diamond", "comb_gem"),
    ]
    horn = chain((5.0, 7.0, 2.0), [((0.4, 0.5, -1.0), 4, 3, 3, "horn"), ((0.5, -0.4, -1.0), 4, 3, 3, "horn"),
                                   ((0.3, -1.0, 0.2), 4, 3, 3, "horn"), ((0.2, -0.3, 1.0), 3, 2, 2, "horn"),
                                   ((0.5, 0.4, 0.8), 3, 2, 2, "horn"), ((0.6, 0.8, 0.2), 2, 1, 1, "horn_tip")], "horn")
    head += horn + mirror(horn)

    # ---- chestplate: a great breastplate studded with diamond, a diamond at its heart; a fauld; the pauldrons
    body = [
        B(-5, 5, -10, 0, -3, 3, "steel_lames", "cuirass"),
        B(-4, 4, -7, 0, 3, 4, "studs", "breast"),
        B(-2, 2, -5, -1, 4, 5, "steel_rim", "boss"), B(-1, 1, -4, -2, 5, 6, "diamond", "heart"),
        B(-6, 6, -10, -8, -4, 4, "steel_rim", "fauld"),
        B(-5, 5, 0, 2, -4, 4, "steel_rim", "gorget"),
    ]
    # the sword arm: a round pauldron stepping in to a knob, tipped outward
    dome = [B(-6, 2, -1, 1, -4, 4, "steel_rim", "pldome"), B(-5, 1, 1, 2, -3, 3, "steel", "pldome"),
            B(-4, 0, 2, 3, -2, 2, "steel", "pldome"), B(-3, -1, 3, 4, -1, 1, "diamond", "plknob")]
    arm = [
        B(-4, 2, -5, -1, -3, 3, "steel_lames", "rerebrace"),
        B(-4, 2, -9, -5, -3, 3, "steel", "vambrace"), B(-5, 3, -7, -6, -4, 4, "steel_rim", "cop"),
        B(-4, 2, -11, -9, -3, 3, "leather", "gauntlet"), B(-5, 3, -10, -9, -4, 4, "steel_rim", "cuff"),
    ]
    right_arm = turned(dome, rot(0, 0, -12), (-2.0, 0.0, 0.0)) + arm
    # the shield arm: a tall shield of a pauldron on the outer side, a diamond boss, its rim proud
    shield = [
        B(3, 4, -6, 5, -4, 4, "steel_rim", "shield"),
        B(4, 5, -2, 2, -1, 1, "steel", "shield_boss"), B(5, 6, -1, 1, -1, 1, "diamond", "shield_gem"),
        B(-1, 3, 2, 3, -3, 3, "steel", "shield_strap"),
    ]
    left_arm = turned(shield, rot(0, 0, 10), (3.0, 2.0, 0.0)) + mirror(arm)

    # ---- leggings: a belt; steel cuisses; knee cops with a diamond stud; a plate tasset front and back
    belt = [B(-5, 5, -13, -10, -3, 3, "steel_rim", "belt"), B(-1, 1, -12, -11, 3, 4, "diamond", "buckle")]
    right_leg = [B(-3, 2, -6, 0, -3, 3, "steel_lames", "cuisse"), B(-3, 2, -8, -6, 3, 4, "steel_rim", "poleyn"),
                 B(-1, 0, -7, -6, 4, 5, "diamond", "poleyn_gem")]
    front_cloth = [B(-3, 3, -5, 0, 0, 1, "steel_dark", "tasset", hem=True)]
    back_cloth = [B(-4, 4, -5, 0, -1, 0, "steel_dark", "tasset", hem=True)]

    # ---- boots: greaves under a flared cuff; heavy square sabatons
    right_boot = [
        B(-4, 3, -7, -6, -4, 4, "steel_rim", "cuff"),
        B(-3, 2, -11, -7, -3, 3, "steel", "greave"),
        B(-4, 3, -13, -11, -4, 4, "steel_lames", "sabaton"),
        B(-3, 2, -13, -11, 4, 6, "steel", "toe"),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": left_arm},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="vaultbreaker", mats=mats, pieces=pieces, accent=AW.RAMPS["pearl"][5])


def frostwalker():
    """Frostwalker (C, Strider): those who cross the frozen gates' white wastes. A pale ice visor under a crest of
    icicles that runs crown to nape; a thick white fur collar; clear ice plates over white pelts, shards of it rising
    off the shoulders; a white fur cape whose hem has frozen into ice; fur boots with an ice spur at the heel."""
    mats = {
        "fur": AW.Mat("wolf", 7, 0.0, 0.15, details=(fur(-1, 1),)),
        "fur_dark": AW.Mat("wolf", 6, 0.0, 0.15, details=(fur(-1, 1),)),
        "pelt": AW.Mat("wolf", 6, 0.0, 0.2, details=(folds(3),)),
        "hollow": AW.Mat("ice", 0, 0.0, 0.2, bevel=False),
        "ice": AW.Mat("ice", 5, 0.3, 1.0),
        "ice_rim": AW.Mat("ice", 5, 0.3, 1.0, details=(rims("ice", 7),)),
        "ice_deep": AW.Mat("ice", 3, 0.3, 1.0),
        "icicle": AW.Mat("glass", 6, 0.2, 1.0),
        "icicle_glow": AW.Mat("ice", 5, 0.1, 1.0, emit=0.45, bevel=False),
        "helm": AW.Mat("ice", 4, 0.35, 1.0, details=(lames(2, -1),)),
        "helm_top": AW.Mat("ice", 5, 0.35, 1.0),
        "helm_brow": AW.Mat("ice", 5, 0.35, 1.0, details=(rims("ice", 7),)),
        "slit": AW.Mat("ice", 5, 0.0, 0.4, emit=0.75, bevel=False),
        "frost_hem": AW.Mat("pearl", 4, 0.0, 0.2, details=(folds(3), hem("ice", 6, rows=1, metal=0.3, gloss=1.0))),
        "strap": AW.Mat("storm", 1, 0.05, 0.35),
    }

    # ---- helmet: clear ice grown into a closed helm, a slit of cold light under its brow; a crown of icicles round the
    # brow, tallest at the front, leaning out
    head = closed_helm("helm", "helm", "helm_top", "helm_brow", "helm", "slit") + [
        B(-5, 5, 9, 10, -1, 5, "ice_rim", "circlet"),
    ]
    for x, z, h, lean, glow in ((0, 3, 6, 0, True), (-2.5, 3, 4, 10, False), (2.5, 3, 4, -10, False),
                                (-4, 1, 3, 18, True), (4, 1, 3, -18, True)):
        head += turned([B(x - 1, x + 1, 10, 11, z - 1, z + 1, "ice", "icicle_base"),
                        B(x - 0.5, x + 0.5, 11, 11 + h, z - 0.5, z + 0.5, "icicle_glow" if glow else "icicle", "icicle")],
                       rot(-8, 0, lean), (x, 10.0, z))

    # ---- chestplate: white pelt; a fur collar; clear ice plates on the breast; shards of ice off the shoulders
    body = [
        B(-5, 5, -10, 0, -3, 3, "pelt", "pelt"),
        B(-4, 4, -8, -2, 3, 4, "ice_rim", "plate"), B(-1, 1, -7, -3, 4, 5, "icicle_glow", "keel"),
        B(-7, 7, -2, 2, -5, 5, "fur", "mantle"), B(-5, 5, 2, 3, -5, 2, "fur_dark", "mantle_top"),
        B(-5, 5, -10, -9, 3, 4, "strap", "strap"),
    ]
    right_arm = [
        VX.Box((-3.0, 2.0, 0.0), (3.0, 1.0, 3.5), "ice_rim", "pauldron", rot(0, 0, -16)),
        VX.Box((-5.0, 4.0, 1.0), (0.5, 2.5, 0.5), "icicle", "shard", rot(0, 0, 28)),
        VX.Box((-4.0, 4.5, -1.0), (0.5, 2.0, 0.5), "icicle_glow", "shard2", rot(0, 0, 18)),
        B(-4, 2, -6, 1, -3, 3, "pelt", "sleeve"),
        B(-4, 2, -10, -6, -3, 3, "ice", "vambrace"), B(-5, 3, -7, -6, -4, 4, "fur_dark", "cuff"),
        B(-4, 2, -11, -10, -3, 3, "strap", "glove"),
    ]
    cape0 = [B(-5, 5, -6, 0, -1, 0, "fur", "cape")]
    cape1 = [B(-5, 5, -4, 0, -1, 0, "frost_hem", "cape", hem=True)]

    # ---- leggings: white pelt; ice plates at the knee; fur front and back to the knee
    belt = [B(-5, 5, -13, -10, -3, 3, "strap", "belt"), B(-1, 1, -12, -11, 3, 4, "ice", "buckle")]
    right_leg = [B(-3, 2, -6, 0, -3, 3, "pelt", "leg"), B(-3, 2, -8, -6, 3, 4, "ice_rim", "poleyn")]
    front_cloth = [B(-3, 3, -5, 0, 0, 1, "fur_dark", "flap", hem=True)]
    back_cloth = [B(-4, 4, -5, 0, -1, 0, "fur_dark", "flap", hem=True)]

    # ---- boots: fur boots, an ice shin plate, an ice spur rising behind the heel
    right_boot = [
        B(-4, 3, -8, -6, -4, 4, "fur", "cuff"),
        B(-3, 2, -13, -8, -3, 3, "fur_dark", "boot"),
        B(-2, 1, -12, -8, 3, 4, "ice", "shin"),
        VX.Box((-0.5, -11.0, -4.5), (0.5, 2.0, 0.5), "icicle_glow", "spur", rot(-40, 0, 0)),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm), "cape0": cape0, "cape1": cape1},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="frostwalker", mats=mats, pieces=pieces, accent=AW.RAMPS["ice"][5])


def _disc(cx, cy, z0, z1, r, mat, part):
    """A round disc facing forward from stepped boxes (rows of whole texels, each as wide as the circle there)."""
    out = []
    for k in range(-r, r):
        y = k + 0.5
        w = int(math.floor(math.sqrt(max(r * r - y * y, 0)) + 0.35))
        if w > 0:
            out.append(B(cx - w, cx + w, cy + k, cy + k + 1, z0, z1, mat, part))
    return out


def sunward():
    """Sunward (C, Channeler): the sun's priests of the open gates. An ivory coif, open at the face, a gold band at
    the brow holding a diamond; behind the head a gold sun disc with its rays, lit at its heart; a gold breastplate
    with the sun on it over ivory robes; long ivory tabards to the knee, a gold line down each; gilded greaves. The
    runes it leaves circling are little suns."""
    mats = {
        "coif": AW.Mat("ivory", 6, 0.0, 0.2, details=(folds(3),)),
        "robe": AW.Mat("ivory", 6, 0.0, 0.2, details=(folds(3),)),
        "tabard": AW.Mat("ivory", 6, 0.0, 0.2, details=(folds(3), marks([(0, y) for y in range(-6, 0)], "gold", 5))),
        "gold": AW.Mat("gold", 5, 0.85, 0.9),
        "gold_rim": AW.Mat("gold", 5, 0.85, 0.9, details=(rims("gold", 6),)),
        "gold_lames": AW.Mat("gold", 5, 0.85, 0.9, details=(lames(2, -1),)),
        "gold_hem": AW.Mat("gold", 5, 0.85, 0.9, details=(hem("gold", 6),)),
        "diamond": AW.Mat("pearl", 5, 0.2, 1.0, emit=0.25, bevel=False),
        "sun": AW.Mat("gold", 5, 0.85, 0.9, details=(rims("gold", 6),)),
        "sun_boss": AW.Mat("gold", 6, 0.85, 0.95),
        "sun_heart": AW.Mat("amber", 5, 0.1, 0.8, emit=0.6, bevel=False),
        "ray": AW.Mat("gold", 5, 0.85, 0.9),
    }

    # ---- helmet: the coif round head and neck, open at the face; the brow band and its diamond; the sun disc
    head = [
        B(-5, -4, -1, 8, -5, 4, "coif", "coif"), B(4, 5, -1, 8, -5, 4, "coif", "coif"),
        B(-4, 4, -1, 8, -5, -4, "coif", "coif"),
        B(-5, 5, 8, 9, -5, 4, "coif", "coif"),
        B(-5, 5, 6, 7, 4, 5, "gold", "band"), B(-1, 1, 6, 8, 5, 6, "diamond", "band_gem"),
        B(-4, 4, -1, 1, 4, 5, "coif", "chin"),
    ]
    # the sun: a gold disc wider than the head, so its burning ring shows all round it from the front; a boss on its back
    head += _disc(0, 5, -8, -7, 8, "sun", "disc")
    head += _disc(0, 5, -7, -6, 7, "sun_heart", "disc_ring")
    head += _disc(0, 5, -9, -8, 4, "sun_boss", "disc_boss")
    for deg in range(0, 360, 45):
        R = rot(0, 0, deg)
        c = np.array([0.0, 5.0, -8.0]) + R @ np.array([0.0, 9.5, 0.0])
        head.append(VX.Box(c, (0.5, 2.0 if deg % 90 == 0 else 1.5, 0.5), "ray", "ray", R))

    # ---- chestplate: the ivory robe; a gold breastplate with the sun; rising gold pauldrons
    body = [
        B(-5, 5, -10, 0, -3, 3, "robe", "robe"),
        B(-4, 4, -6, 0, 3, 4, "gold_rim", "breast"),
        B(-2, 2, -4, -1, 4, 5, "gold", "sunplate"), B(-1, 1, -3, -2, 5, 6, "sun_heart", "sunface"),
        B(-5, 5, -1, 1, -4, 4, "gold_hem", "yoke"),
    ]
    right_arm = [
        VX.Box((-2.5, 2.0, 0.0), (3.5, 1.0, 4.0), "gold_hem", "pauldron", rot(0, 0, -16)),
        VX.Box((-3.0, 3.5, 0.0), (2.0, 0.5, 3.0), "gold", "pauldron2", rot(0, 0, -22)),
        B(-4, 2, -6, 1, -3, 3, "robe", "sleeve"),
        B(-4, 2, -10, -6, -3, 3, "gold_lames", "vambrace"),
        B(-4, 2, -11, -10, -3, 3, "coif", "glove"),
    ]

    # ---- leggings: a gold belt; ivory robe legs; long tabards front and back, a gold line down each
    belt = [B(-5, 5, -13, -10, -3, 3, "gold_rim", "belt"), B(-1, 1, -12, -11, 3, 4, "diamond", "clasp")]
    right_leg = [B(-3, 2, -6, 0, -3, 3, "robe", "leg")]
    front_cloth = [B(-3, 3, -6, 0, 0, 1, "tabard", "tabard", hem=True)]
    back_cloth = [B(-4, 4, -6, 0, -1, 0, "tabard", "tabard", hem=True)]

    # ---- boots: gilded greaves, ivory shoes
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "gold_lames", "greave"), B(-4, 3, -7, -6, -4, 4, "gold_rim", "cuff"),
        B(-3, 2, -13, -11, -3, 3, "coif", "shoe"),
        B(-2, 2, -13, -11, 3, 4, "gold", "toe"),
    ]
    rune = _disc(0, 0, -0.5, 0.5, 2, "sun_heart", "rsun")
    for deg in range(0, 360, 90):
        R = rot(0, 0, deg)
        c = np.array([0.0, 0.0, 0.0]) + R @ np.array([0.0, 3.0, 0.0])
        rune.append(VX.Box(c, (0.5, 1.0, 0.5), "ray", "rray", R))
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm)},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="sunward", mats=mats, pieces=pieces, rune=rune, accent=AW.RAMPS["gold"][5])


def shardcrown():
    """Shardcrown (C, Lord-forged): forged round a C-rank Lord's heart, which goes on growing. Dark steel; a crown of
    violet crystal breaking up out of a closed helm; crystal bursting from the pauldrons and the spine; the heart
    itself, a cluster of crystal, burning in the breast."""
    mats = {
        "steel": AW.Mat("lacquer", 5, 0.8, 0.85),
        "steel_lames": AW.Mat("lacquer", 5, 0.8, 0.85, details=(lames(2, -1),)),
        "steel_rim": AW.Mat("lacquer", 5, 0.8, 0.85, details=(rims("veil", 5),)),
        "crystal": AW.Mat("veil", 5, 0.2, 1.0, details=(lames(2, 1),)),
        "crystal_dark": AW.Mat("veil", 3, 0.2, 1.0),
        "crystal_glow": AW.Mat("veil", 4, 0.1, 1.0, emit=0.6, bevel=False),
        "eye": AW.Mat("veil", 4, 0.0, 0.4, emit=0.55, bevel=False),
        "cloth": AW.Mat("obsidian", 2, 0.0, 0.2, details=(folds(3),)),
    }

    def shard(c, h, deg_z, deg_x, mat, part, w=1.0):
        return VX.Box(c, (w, h, w), mat, part, rot(deg_x, 0, deg_z))

    # ---- helmet: closed, a violet slit; a crown of crystal shards breaking up round the crown, tallest at the front
    head = [
        B(-5, -4, -1, 8, -5, 5, "steel_rim", "helm"), B(4, 5, -1, 8, -5, 5, "steel_rim", "helm"),
        B(-4, 4, -1, 8, -5, -4, "steel_lames", "helm"),
        B(-5, 5, 8, 9, -5, 5, "steel", "helm"),
        B(-4, 4, 5, 8, 4, 5, "steel", "brow"), B(-3, 3, 4, 5, 4, 5, "eye", "eye"),
        B(-4, -3, 4, 5, 4, 5, "steel", "face"), B(3, 4, 4, 5, 4, 5, "steel", "face"),
        B(-4, 4, -1, 4, 4, 5, "steel_lames", "face"),
    ]
    head += [shard((0.0, 11.0, 3.0), 3.0, 0, 12, "crystal_glow", "crown_c"),
             shard((-2.5, 10.5, 2.5), 2.0, 18, 10, "crystal", "crown_l"), shard((2.5, 10.5, 2.5), 2.0, -18, 10, "crystal", "crown_r"),
             shard((-4.0, 10.0, 0.0), 1.5, 30, 0, "crystal_dark", "crown_l2"), shard((4.0, 10.0, 0.0), 1.5, -30, 0, "crystal_dark", "crown_r2"),
             shard((-2.0, 10.0, -3.0), 1.5, 14, -16, "crystal", "crown_l3", 0.5), shard((2.0, 10.0, -3.0), 1.5, -14, -16, "crystal", "crown_r3", 0.5)]

    # ---- chestplate: dark plate; the heart, a burning cluster in the breast; crystal down the spine
    body = [
        B(-5, 5, -10, 0, -3, 3, "steel_lames", "cuirass"),
        B(-4, 4, -8, 0, 3, 4, "steel_rim", "breast"),
        B(-2, 2, -5, -1, 4, 5, "crystal_dark", "heart_bed"),
        shard((0.0, -2.5, 5.5), 1.5, 0, 30, "crystal_glow", "heart"),
        shard((-1.5, -3.5, 5.0), 1.0, 25, 25, "crystal", "heart2", 0.5), shard((1.5, -3.5, 5.0), 1.0, -25, 25, "crystal", "heart3", 0.5),
        B(-6, 6, -10, -8, -4, 4, "steel_rim", "fauld"),
        shard((0.0, -1.0, -4.5), 2.5, 0, -30, "crystal", "spine"), shard((-1.5, -4.5, -4.0), 1.5, 15, -35, "crystal_dark", "spine2"),
        shard((1.5, -4.5, -4.0), 1.5, -15, -35, "crystal_dark", "spine3"),
    ]
    right_arm = [
        VX.Box((-2.5, 2.0, 0.0), (4.0, 1.5, 4.5), "steel_rim", "pauldron", rot(0, 0, -14)),
        shard((-4.5, 5.0, 0.0), 2.5, 30, 0, "crystal", "pshard"), shard((-3.0, 5.0, 2.0), 2.0, 15, 20, "crystal_glow", "pshard2"),
        shard((-3.0, 4.5, -2.0), 1.5, 15, -20, "crystal_dark", "pshard3"),
        B(-4, 2, -5, -1, -3, 3, "steel_lames", "rerebrace"),
        B(-4, 2, -10, -5, -3, 3, "steel", "vambrace"),
        B(-4, 2, -11, -10, -3, 3, "steel_rim", "gauntlet"),
    ]
    cape0 = [B(-5, 5, -6, 0, -1, 0, "cloth", "cape")]
    cape1 = [B(-5, 5, -5, 0, -1, 0, "cloth", "cape", hem=True)]

    # ---- leggings: dark plate, a shard at each knee; tassets front and back
    belt = [B(-5, 5, -13, -10, -3, 3, "steel_rim", "belt"), B(-1, 1, -12, -11, 3, 4, "crystal_glow", "buckle")]
    right_leg = [B(-3, 2, -6, 0, -3, 3, "steel_lames", "cuisse"), B(-3, 2, -8, -6, 3, 4, "steel_rim", "poleyn"),
                 shard((-1.0, -6.0, 4.5), 1.0, 10, 30, "crystal", "kshard", 0.5)]
    front_cloth = [B(-3, 3, -5, 0, 0, 1, "steel_lames", "tasset", hem=True)]
    back_cloth = [B(-4, 4, -5, 0, -1, 0, "steel_lames", "tasset", hem=True)]

    # ---- boots: dark greaves, a crystal breaking from each heel
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "steel_lames", "greave"), B(-4, 3, -7, -6, -4, 4, "steel_rim", "cuff"),
        B(-3, 2, -13, -11, -3, 3, "steel", "sabaton"), B(-2, 2, -13, -11, 3, 5, "steel", "toe"),
        shard((-0.5, -10.0, -4.0), 1.5, 0, -40, "crystal", "heel"),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm), "cape0": cape0, "cape1": cape1},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="shardcrown", mats=mats, pieces=pieces, accent=AW.RAMPS["veil"][5])


def deepcarapace():
    """Deepcarapace (B, Bulwark): Deepglass grown like the shell of the Living Deep's great beetles. A domed helm with a
    ridge from brow to nape and its jaws swept forward at the cheeks; ridged shell pauldrons; two wing-cases closed
    over the back, split down the middle; the shell's seams lit cave cyan."""
    mats = {
        "shell": AW.Mat("obsidian", 5, 0.35, 1.0, details=(rims("sea", 3),)),
        "shell_lames": AW.Mat("obsidian", 4, 0.35, 1.0, details=(lames(2, -1),)),
        "shell_seam": AW.Mat("obsidian", 4, 0.35, 1.0, details=(lames(3, -1), side_seam("spore", 0.7))),
        "shell_rim": AW.Mat("obsidian", 4, 0.35, 1.0, details=(rims("sea", 4),)),
        "ridge": AW.Mat("obsidian", 6, 0.35, 1.0),
        "glow": AW.Mat("spore", 5, 0.0, 0.6, emit=0.75, bevel=False),
        "horn": AW.Mat("obsidian", 3, 0.35, 1.0, details=(lames(1, -1),)),
        "jaw": AW.Mat("obsidian", 5, 0.35, 1.0),
        "under": AW.Mat("void", 3, 0.1, 0.4, details=(lames(2, -1),)),
    }

    # ---- helmet: a domed shell with a cyan slit under its brow; a great horn rising from the brow and sweeping back,
    # like the cave beetles'
    head = closed_helm("shell_lames", "shell_lames", "shell", "shell", "shell_lames", "glow") + [
        B(-4, 4, 9, 10, -4, 4, "shell", "dome_top"),
        B(-1, 1, 10, 11, -4, 2, "ridge", "ridge"),
    ]
    head += chain((0.0, 8.0, 4.0), [((0.0, 1.0, 0.35), 4, 3, 3, "horn"), ((0.0, 1.0, -0.15), 4, 2, 2, "horn"),
                                    ((0.0, 1.0, -0.4), 3, 1, 1, "horn")], "horn")

    # ---- chestplate: a segmented breast, a cyan seam down it; ridged shell pauldrons; the wing-cases on the back
    body = [
        B(-5, 5, -10, 0, -3, 3, "under", "cuirass"),
        B(-4, 4, -8, 0, 3, 4, "shell_lames", "breast"), B(-1, 1, -7, 0, 4, 5, "ridge", "keel"),
        B(-6, 6, -10, -8, -4, 4, "shell_rim", "fauld"),
        B(-5, 0, -11, 1, -5, -3, "shell_rim", "elytron"), B(0, 5, -11, 1, -5, -3, "shell_rim", "elytron2"),
        B(-0.5, 0.5, -10, 0, -5.5, -4.5, "glow", "elytra_seam"),
    ]
    right_arm = [
        VX.Box((-2.5, 2.5, 0.0), (4.0, 1.0, 4.5), "shell_rim", "pauldron", rot(0, 0, -16)),
        VX.Box((-3.0, 3.5, 0.0), (2.5, 0.5, 3.5), "ridge", "pauldron2", rot(0, 0, -20)),
        B(-4, 2, -5, 0, -3, 3, "shell_lames", "rerebrace"),
        B(-4, 2, -10, -5, -3, 3, "shell_seam", "vambrace"),
        B(-4, 2, -11, -10, -3, 3, "shell", "gauntlet"),
    ]

    # ---- leggings: segmented cuisses, a cyan seam; shell tassets
    belt = [B(-5, 5, -13, -10, -3, 3, "shell_rim", "belt"), B(-1, 1, -12, -11, 3, 4, "glow", "buckle")]
    right_leg = [B(-3, 2, -6, 0, -3, 3, "shell_seam", "cuisse"), B(-3, 2, -8, -6, 3, 4, "shell", "poleyn")]
    front_cloth = [B(-3, 3, -5, 0, 0, 1, "shell_lames", "tasset", hem=True)]
    back_cloth = [B(-4, 4, -5, 0, -1, 0, "shell_lames", "tasset", hem=True)]

    # ---- boots: shell greaves, pointed sabatons
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "shell_seam", "greave"), B(-4, 3, -7, -6, -4, 4, "shell_rim", "cuff"),
        B(-3, 2, -13, -11, -3, 3, "shell", "sabaton"), B(-2, 1, -13, -11, 3, 5, "shell", "toe"),
        B(-1, 0, -13, -12, 5, 6, "ridge", "point"),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm)},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="deepcarapace", mats=mats, pieces=pieces, accent=AW.RAMPS["spore"][5])


def cloudrunner():
    """Cloudrunner (B, Strider): the high gates' messengers, who run where the ground gives out. A sleek white helm with
    feathered fins swept back from the temples; light white plates over slate cloth; fins of feathers on the
    shoulders; a short mantle of long feathers at the back; feathered fins at the heels."""
    mats = {
        "plate": AW.Mat("white", 5, 0.6, 0.85),
        "plate_rim": AW.Mat("white", 5, 0.6, 0.85, details=(rims("slate", 4),)),
        "plate_lames": AW.Mat("white", 5, 0.6, 0.85, details=(lames(2, -1),)),
        "cloth": AW.Mat("storm", 2, 0.0, 0.2, details=(folds(3),)),
        "feather": AW.Mat("white", 6, 0.0, 0.4, details=(lames(2, -1),)),
        "feather_b": AW.Mat("pearl", 5, 0.0, 0.4, details=(lames(2, -1),)),
        "feather_tip": AW.Mat("storm", 3, 0.0, 0.4),
        "visor": AW.Mat("storm", 1, 0.3, 0.8),
        "slit": AW.Mat("storm", 6, 0.0, 0.5, emit=0.75, bevel=False),
        "gem": AW.Mat("storm", 5, 0.1, 1.0, emit=0.4, bevel=False),
    }

    def fin(p0, ds, lens, part, flat=(1, 0, 0)):
        """A wing of broad flat feathers fanned from p0, each along its own direction, a dark tip on each; flat: the
        axis a feather is thin along (x: a wing at the side of the head or heel)."""
        out = []
        for k, (d, ln) in enumerate(zip(ds, lens)):
            dd = np.asarray(d, dtype=float)
            b, end = link(p0, dd, ln, 2, 1, "feather" if k % 2 == 0 else "feather_b", f"{part}{k}", up=flat)
            t, _ = link(end, dd, 2, 2, 1, "feather_tip", f"{part}{k}t", up=flat)
            out += [b, t]
        return out

    # ---- helmet: sleek, a dark visor with a pale slit; feathered fins swept back from the temples
    # the face in shadow behind a nasal, a slit of storm light under the brow
    head = closed_helm("plate_rim", "plate_lames", "plate", "plate", "visor", "slit") + [
        B(-1, 1, 9, 10, -3, 5, "plate", "crest"),
        B(-1, 1, 0, 4, 5, 6, "plate", "nasal"),
    ]
    wing = fin((5.5, 6.0, 1.5), [(0.12, 0.35, -1.0), (0.2, 0.8, -0.8), (0.28, 1.3, -0.5)], [6, 5, 4], "hfin")
    head += wing + mirror(wing)

    # ---- chestplate: light plate on slate cloth, a storm gem; feather fins rising off the shoulders; the mantle
    body = [
        B(-5, 5, -10, 0, -3, 3, "cloth", "cloth"),
        B(-4, 4, -6, 0, 3, 4, "plate_rim", "breast"), B(-1, 1, -3, -1, 4, 5, "gem", "gem"),
        B(-5, 5, -1, 1, -4, 4, "plate", "yoke"),
    ]
    right_arm = [
        VX.Box((-2.5, 2.0, 0.0), (3.5, 1.0, 4.0), "plate_rim", "pauldron", rot(0, 0, -16)),
        B(-4, 2, -6, 1, -3, 3, "cloth", "sleeve"),
        B(-4, 2, -10, -6, -3, 3, "plate_lames", "vambrace"),
        B(-4, 2, -11, -10, -3, 3, "visor", "glove"),
    ] + fin((-4.5, 3.0, -1.0), [(-0.25, 0.8, -0.8), (-0.4, 1.2, -0.4)], [4, 3], "pfin")
    cape0 = [B(-5, 5, -6, 0, -1, 0, "feather", "mantle")]
    cape1 = [B(x0, x0 + 2, -ln, 0, -1, 0, "feather" if k % 2 == 0 else "feather_b", f"mantle_t{x0}") for k, (x0, ln) in enumerate(((-5, 2), (-3, 4), (-1, 5), (1, 4), (3, 2)))]
    cape1 += [B(x0, x0 + 2, -ln - 1, -ln, -1, 0, "feather_tip", f"mantle_tip{x0}", hem=True) for x0, ln in ((-5, 2), (-3, 4), (-1, 5), (1, 4), (3, 2))]

    # ---- leggings: slate cloth, white knee plates, a short white flap front and back
    belt = [B(-5, 5, -13, -10, -3, 3, "plate_rim", "belt"), B(-1, 1, -12, -11, 3, 4, "gem", "buckle")]
    right_leg = [B(-3, 2, -6, 0, -3, 3, "cloth", "leg"), B(-3, 2, -8, -6, 3, 4, "plate_rim", "poleyn")]
    front_cloth = [B(-3, 3, -4, 0, 0, 1, "plate_lames", "flap", hem=True)]
    back_cloth = [B(-4, 4, -4, 0, -1, 0, "plate_lames", "flap", hem=True)]

    # ---- boots: white greaves; a fin of feathers swept back and up from each heel
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "plate_lames", "greave"), B(-4, 3, -7, -6, -4, 4, "plate_rim", "cuff"),
        B(-3, 2, -13, -11, -3, 3, "visor", "shoe"), B(-2, 2, -13, -11, 3, 4, "plate", "toe"),
    ] + fin((-3.5, -10.0, -1.5), [(-0.12, 0.4, -1.0), (-0.2, 0.9, -0.8)], [4, 3], "bfin")
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm), "cape0": cape0, "cape1": cape1},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="cloudrunner", mats=mats, pieces=pieces, accent=AW.RAMPS["storm"][6])


def tidebreaker():
    """Tidebreaker (B, Duelist): duelists of the drowned gates. Sea-green scale edged in amethyst; a helm with a tall
    fin crest, its spines amethyst, and small fins at the cheeks; fin pauldrons swept back; a cape cut like a wave
    breaking, its crest curling back white with foam."""
    mats = {
        "scale": AW.Mat("sea", 3, 0.6, 0.85, details=(scales(),)),
        "scale_rim": AW.Mat("sea", 3, 0.6, 0.85, details=(scales(), rims("veil", 5))),
        "helm": AW.Mat("sea", 4, 0.6, 0.85, details=(lames(2, -1),)),
        "helm_plain": AW.Mat("sea", 4, 0.6, 0.85),
        "plate": AW.Mat("sea", 4, 0.6, 0.85, details=(rims("veil", 5),)),
        "plate_lames": AW.Mat("sea", 3, 0.6, 0.85, details=(lames(2, -1),)),
        "web": AW.Mat("sea", 5, 0.2, 0.9, details=(lames(1, -1),)),
        "spine": AW.Mat("veil", 5, 0.6, 0.9),
        "eye": AW.Mat("lilac", 4, 0.0, 0.4, emit=0.75, bevel=False),
        "cape": AW.Mat("sea", 3, 0.0, 0.3, details=(folds(3),)),
        "cape_wave": AW.Mat("sea", 3, 0.0, 0.3, details=(folds(3), hem("glass", 6, rows=1, metal=0.0, gloss=0.4))),
        "foam": AW.Mat("glass", 6, 0.0, 0.4),
        "dark": AW.Mat("sea", 1, 0.1, 0.4),
    }

    # ---- helmet: a close scale helm, a dark visor with a violet slit; the fin crest; fins at the cheeks
    head = closed_helm("helm", "helm", "helm_plain", "helm_plain", "helm", "eye")
    for z0, h in ((3, 6), (1, 7), (-1, 6), (-3, 5), (-5, 3)):
        head += [B(-0.5, 0.5, 9, 9 + h - 1, z0, z0 + 2, "web", "fin"),
                 B(-0.5, 0.5, 9 + h - 1, 9 + h, z0, z0 + 2, "spine", "fin_spine")]
    cheek = turned([B(5, 6, 2, 5, -1, 3, "web", "cheek"), B(5, 6, 5, 6, -1, 1, "spine", "cheek_spine")],
                   rot(0, 25, 0), (5.0, 3.0, 1.0))
    head += cheek + mirror(cheek)

    # ---- chestplate: a scale cuirass edged in amethyst; fin pauldrons swept back; the wave cape
    body = [
        B(-5, 5, -10, 0, -3, 3, "scale", "cuirass"),
        B(-4, 4, -7, 0, 3, 4, "scale_rim", "breast"),
        B(-6, 6, -10, -8, -4, 4, "plate", "fauld"),
    ]
    right_arm = [
        VX.Box((-2.5, 2.0, 0.0), (3.5, 1.0, 4.0), "plate", "pauldron", rot(0, 0, -14)),
    ] + turned([B(-4.5, -3.5, 2, 5, -5, 0, "web", "pfin"), B(-4.5, -3.5, 5, 6, -5, -2, "spine", "pfin_spine")],
               rot(25, 0, 0), (-4.0, 3.5, -2.5)) + [
        B(-4, 2, -5, 0, -3, 3, "scale", "rerebrace"),
        B(-4, 2, -10, -5, -3, 3, "plate_lames", "vambrace"),
        B(-4, 2, -11, -10, -3, 3, "dark", "glove"),
    ]
    cape0 = [B(-5, 5, -6, 0, -1, 0, "cape", "cape")]
    # the wave: the cape's lower link leans back and curls, its crest white with foam
    # its hem cut like a wave about to break, rising to a crest and falling away, foam along its edge
    cape1 = [B(x0, x0 + 2, -ln, 0, -1, 0, "cape_wave", f"wave{x0}", hem=True) for x0, ln in ((-5, 2), (-3, 3), (-1, 5), (1, 6), (3, 3))]

    # ---- leggings: scale; plate tassets edged in amethyst
    belt = [B(-5, 5, -13, -10, -3, 3, "plate", "belt"), B(-1, 1, -12, -11, 3, 4, "eye", "buckle")]
    right_leg = [B(-3, 2, -6, 0, -3, 3, "scale", "cuisse"), B(-3, 2, -8, -6, 3, 4, "plate", "poleyn")]
    front_cloth = [B(-3, 3, -5, 0, 0, 1, "plate_lames", "tasset", hem=True)]
    back_cloth = [B(-4, 4, -5, 0, -1, 0, "plate_lames", "tasset", hem=True)]

    # ---- boots: scale greaves, a fin at the heel
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "scale", "greave"), B(-4, 3, -7, -6, -4, 4, "plate", "cuff"),
        B(-3, 2, -13, -11, -3, 3, "dark", "sabaton"), B(-2, 2, -13, -11, 3, 5, "plate", "toe"),
        VX.Box((-3.5, -10.0, -3.5), (0.5, 1.5, 1.5), "web", "bfin", rot(-30, 0, 0)),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm), "cape0": cape0, "cape1": cape1},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="tidebreaker", mats=mats, pieces=pieces, accent=AW.RAMPS["veil"][6])


def hollowheart():
    """Hollowheart (B, Lord-forged): a B-rank Lord's heart, hollowed out and still beating, carried in a cage of bone
    on the chest. Pale bone over deep red; a smooth bone helm with a dark slit, a crown of ribs sweeping back from
    it; ribbed bone pauldrons; a ragged red cape."""
    mats = {
        "bone": AW.Mat("bone", 4, 0.1, 0.55),
        "bone_lames": AW.Mat("bone", 4, 0.1, 0.55, details=(lames(2, -1),)),
        "bone_rim": AW.Mat("bone", 4, 0.1, 0.55, details=(rims("bone", 2),)),
        "rib": AW.Mat("bone", 6, 0.1, 0.6),
        "rib_dark": AW.Mat("bone", 4, 0.1, 0.55),
        "red": AW.Mat("wine", 3, 0.0, 0.3, details=(folds(3),)),
        "red_dark": AW.Mat("wine", 2, 0.0, 0.3, details=(folds(3),)),
        "hollow": AW.Mat("void", 2, 0.0, 0.2, bevel=False),
        "heart": AW.Mat("wine", 6, 0.1, 0.7, emit=0.6, bevel=False),
        "heart_core": AW.Mat("crimson", 4, 0.1, 0.7, emit=0.75, bevel=False),
        "eye": AW.Mat("crimson", 4, 0.0, 0.4, emit=0.75, bevel=False),
    }

    # ---- helmet: smooth bone, a dark slit with a red light; a crown of ribs sweeping back
    head = closed_helm("bone_rim", "bone_lames", "bone", "bone_rim", "bone_lames", "eye")
    # the crown: bone spikes standing round the crown of the helm, leaning out, the front ones tallest
    for x, z, h in ((0.0, 3.5, 6), (-3.0, 3.0, 5), (3.0, 3.0, 5), (-4.0, -0.5, 4), (4.0, -0.5, 4)):
        out = np.array([x, 0.0, z]) / max(math.hypot(x, z), 1e-6)
        head += chain((x, 8.5, z), [((out[0] * 0.3, 1.0, out[2] * 0.3), h - 2, 2, 2, "rib"),
                                    ((out[0] * 0.45, 1.0, out[2] * 0.45), 3, 1, 1, "rib_dark")], f"crown{x}{z}")

    # ---- chestplate: bone over red; the cage of ribs open on the breast, the heart burning in the hollow
    body = [
        B(-5, 5, -10, 0, -3, 3, "red", "under"),
        B(-4, 4, -8, 0, 3, 4, "hollow", "hollow"),
        B(-2, 2, -6, -1, 4, 5, "heart", "heart"), B(-1, 1, -7, -6, 4, 5, "heart", "heart_tip"),
        B(-1, 1, -4, -2, 5, 6, "heart_core", "heart_core"),
        B(-4, 4, -1, 0, 4, 6, "bone", "sternum_top"), B(-4, 4, -9, -8, 3, 5, "bone", "cage_base"),
    ]
    for y in (-2, -4, -6):
        body += [B(-4, -2, y - 1, y, 5, 6, "rib", "cage"), B(2, 4, y - 1, y, 5, 6, "rib", "cage"),
                 B(-5, -4, y - 1, y, 3, 6, "rib_dark", "cage_side"), B(4, 5, y - 1, y, 3, 6, "rib_dark", "cage_side")]
    body += [B(-6, 6, -10, -9, -4, 3, "bone_rim", "fauld"), B(-5, 5, 0, 1, -4, 4, "bone", "yoke")]
    right_arm = [
        VX.Box((-2.5, 2.0, 0.0), (4.0, 1.5, 4.5), "bone_lames", "pauldron", rot(0, 0, -14)),
        VX.Box((-4.0, 3.0, 0.0), (1.0, 1.5, 3.5), "rib", "pauldron_rib", rot(0, 0, -30)),
        B(-4, 2, -5, -1, -3, 3, "red", "sleeve"),
        B(-4, 2, -10, -5, -3, 3, "bone_lames", "vambrace"),
        B(-4, 2, -11, -10, -3, 3, "red_dark", "glove"),
    ]
    cape0 = [B(-5, 5, -6, 0, -1, 0, "red", "cape")]
    cape1 = [B(x0, x0 + 2, -ln, 0, -1, 0, "red_dark", f"cape_t{x0}", hem=True) for x0, ln in ((-5, 5), (-3, 3), (-1, 6), (1, 4), (3, 5))]

    # ---- leggings: red cloth, bone knee plates, bone tassets
    belt = [B(-5, 5, -13, -10, -3, 3, "bone_rim", "belt"), B(-1, 1, -12, -11, 3, 4, "heart", "buckle")]
    right_leg = [B(-3, 2, -6, 0, -3, 3, "red", "leg"), B(-3, 2, -8, -6, 3, 4, "bone_rim", "poleyn")]
    front_cloth = [B(-3, 3, -5, 0, 0, 1, "bone_lames", "tasset", hem=True)]
    back_cloth = [B(-4, 4, -6, 0, -1, 0, "red_dark", "tail", hem=True)]

    # ---- boots: bone greaves, red wraps
    right_boot = [
        B(-3, 2, -11, -6, -3, 3, "bone_lames", "greave"), B(-4, 3, -7, -6, -4, 4, "bone_rim", "cuff"),
        B(-3, 2, -13, -11, -3, 3, "red_dark", "foot"), B(-2, 2, -13, -11, 3, 5, "bone", "toe"),
    ]
    pieces = {
        "helmet": {"head": head},
        "chestplate": {"body": body, "right_arm": right_arm, "left_arm": mirror(right_arm), "cape0": cape0, "cape1": cape1},
        "leggings": {"body": belt, "right_leg": right_leg, "left_leg": mirror(right_leg), "front_cloth": front_cloth, "back_cloth": back_cloth},
        "boots": {"right_leg": right_boot, "left_leg": mirror(right_boot)},
    }
    return dict(id="hollowheart", mats=mats, pieces=pieces, accent=AW.RAMPS["crimson"][5])


SETS = {"wildhide": wildhide, "beastbound": beastbound, "gatewrought": gatewrought, "veilwoven": veilwoven,
        "lordscale": lordscale, "sovereign": sovereign,
        "bellguard": bellguard, "rootwarden": rootwarden, "amberwing": amberwing, "cleftrunner": cleftrunner,
        "lanternlight": lanternlight, "bladeward": bladeward, "hornlord": hornlord,
        "vaultbreaker": vaultbreaker, "frostwalker": frostwalker, "sunward": sunward, "shardcrown": shardcrown,
        "deepcarapace": deepcarapace, "cloudrunner": cloudrunner, "tidebreaker": tidebreaker, "hollowheart": hollowheart}


# ---------------------------------------------------------------------------------------------------------------------
# Building: every piece's bones into one atlas

def to_part(P, direction=False):
    """Design space (y up, z forward) to the bone's part space (Minecraft's model space: y down, z backward)."""
    return np.asarray(P) * np.array([1.0, -1.0, -1.0])


def bone_y(bone):
    bone = bone.split("@")[0]
    if bone.startswith("cape"):
        return PIVOT["cape0"][1] - CAPE_LINK * int(bone[4:])
    return PIVOT[bone][1]


class Built:
    def __init__(self, pos, nrm, uv, along):
        self.pos, self.nrm, self.uv, self.along = pos, nrm, uv, along
        self.anchors, self.streams = [], []


def make(design, width=128):
    """(meshes {(piece, bone): Built (part space)}, albedo, material)."""
    groups, faces = [], []
    VX.WARNINGS.clear()
    warns = []
    parts = [(p, b, boxes) for p, bones in design["pieces"].items() for b, boxes in bones.items()]
    if design.get("rune"):
        parts.append(("rune", "rune", design["rune"]))
    # mastery IV's awakened parts: drawn over the piece's own on the same bone (bone@awake, <piece>_<bone>_awakened.mesh)
    parts += [(p, b + "@awake", boxes) for p, bones in design.get("awake", {}).items() for b, boxes in bones.items()]
    for piece, bone, boxes in parts:
        fs = VX.faces(boxes)
        warns += [f"{piece}/{bone}: {w}" for w in VX.WARNINGS]
        for f in fs:
            f.group = (piece, bone)
        groups.append((piece, bone))
        faces += fs
    height = VX.pack(faces, width)
    albedo = np.zeros((height, width, 4), dtype=np.uint8)
    material = np.zeros((height, width, 4), dtype=np.uint8)
    filled = np.zeros((height, width), dtype=bool)
    acc = {g: ([], [], [], []) for g in groups}
    for f in faces:
        piece, bone = f.group
        oy = bone_y(bone)
        a, m = painter(design["mats"], oy)(f)
        tx, ty = f.x + f.i - f.i0, f.y + f.j - f.j0
        albedo[ty, tx] = a
        material[ty, tx] = m
        filled[ty, tx] = True
        mask = np.zeros((f.sv, f.su), dtype=bool)
        mask[f.j, f.i] = True
        P, N, UV, A = acc[f.group]
        for i0, j0, w, h in VX.rects(mask):
            quad = [(i0, j0), (i0 + w, j0), (i0 + w, j0 + h), (i0, j0 + h)]
            for tri in ((0, 1, 2), (0, 2, 3)):
                for q in tri:
                    ci, cj = quad[q]
                    p = f.corner + ci * f.u + cj * f.v
                    P.append(p)
                    N.append(f.n)
                    UV.append(((f.x + ci - f.i0) / (2 * width), (f.y + cj - f.j0) / height))
                    A.append(min(1.0, max(0.0, (p[1] + oy) / FIGURE)))
    albedo, _ = MS.dilate(albedo, filled)
    material, _ = MS.dilate(material, filled)
    meshes = {}
    for g, (P, N, UV, A) in acc.items():
        if P:
            meshes[g] = Built(to_part(np.array(P)), to_part(np.array(N), True), np.array(UV), np.array(A))
    return meshes, albedo, material, warns


def write(design):
    meshes, albedo, material, warns = make(design)
    sid = design["id"]
    mesh_dir = os.path.join(RES, "assets/awaken/armour", sid)
    os.makedirs(mesh_dir, exist_ok=True)
    for old in os.listdir(mesh_dir):
        if old.endswith(".mesh"):
            os.remove(os.path.join(mesh_dir, old))
    accent = AW.hex_rgb(design["accent"])
    for (piece, bone), b in meshes.items():
        name = "rune" if piece == "rune" else f"{piece}_{bone.replace('@awake', '_awakened')}"
        MS.write(os.path.join(mesh_dir, name + ".mesh"), b, accent)
    tex_dir = os.path.join(RES, "assets/awaken/textures/entity/armour")
    os.makedirs(tex_dir, exist_ok=True)
    Image.fromarray(np.concatenate([albedo, material], axis=1), "RGBA").save(os.path.join(tex_dir, sid + ".png"))
    items(design, meshes, albedo, material)
    return meshes, warns


# ---------------------------------------------------------------------------------------------------------------------
# Items: the sets with pieces of their own (dev.awaken.armour.ArmourItems), each piece's icon drawn from its meshes, and
# each pattern a vellum sheet with its set's helm drawn on it in ink and a seal in its rank's colour

# set -> (rank, a Lord's set): the sets whose pieces are items of their own; a pattern for every rank above E
OWN_ITEMS = {"bellguard": ("E", False), "rootwarden": ("E", False), "amberwing": ("E", False),
             "cleftrunner": ("D", False), "lanternlight": ("D", False), "bladeward": ("D", False), "hornlord": ("D", True),
             "vaultbreaker": ("C", False), "frostwalker": ("C", False), "sunward": ("C", False), "shardcrown": ("C", True),
             "deepcarapace": ("B", False), "cloudrunner": ("B", False), "tidebreaker": ("B", False), "hollowheart": ("B", True)}
RANK_COLOUR = {"E": "#f4f6fb", "D": "#5fd08f", "C": "#6fb3ff", "B": "#b48cff", "A": "#f2a541", "S": "#ff5a6a"}
ICON_SKIP = ("cape0", "cape1", "cape2", "back_cloth")  # behind the piece: they would only shrink its icon
# each piece's turn in its icon (yaw, pitch): a helm and a chest a little from above, the legs level, boots side on
ICON_VIEW = {"helmet": (-24, 10), "chestplate": (-20, 8), "leggings": (-18, 2), "boots": (-34, 14)}
ICON_SPREAD = {"leggings": 1.0, "boots": 2.5}  # the legs drawn apart (texels each way), so a pair reads as two
ITEM_DIR = os.path.join(RES, "assets/awaken")


def piece_model(design, piece, meshes):
    """One piece's meshes placed on the figure at rest, in item units (the render's space)."""
    pos, nrm, uv, al = [], [], [], []
    spread = ICON_SPREAD.get(piece, 0.0)
    for (p, bone), b in meshes.items():
        if p != piece or bone in ICON_SKIP or "@" in bone:
            continue
        P, N = rest(bone, b.pos, b.nrm)
        if bone.endswith("_leg"):
            P = P + np.array([spread if bone.startswith("left") else -spread, 0.0, 0.0])
        pos.append(P)
        nrm.append(N)
        uv.append(b.uv)
        al.append(b.along)
    return {"pos": np.concatenate(pos) / 16.0, "nrm": np.concatenate(nrm), "uv": np.concatenate(uv),
            "along": np.concatenate(al), "accent": AW.hex_rgb(design["accent"])}


def icon_of(m, tex, size=32, yaw=-24, pitch=10, outline=True):
    """The weapons' icon (arms_weapons.icon): each pixel the surface's colour at its centre, the silhouette from how much
    of the pixel the piece covers, a dark outline round it; turned a little so the piece shows its depth."""
    view = MR.rot(yaw, pitch)
    c3 = (m["pos"].min(axis=0) + m["pos"].max(axis=0)) / 2
    pv = (view @ (m["pos"] - c3).T).T * 16.0
    lo, hi = pv.min(axis=0), pv.max(axis=0)
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    centre = c3 + view.T @ np.array([(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, 0.0]) / 16.0
    key = np.array([255, 0, 255])

    def shot(k):
        img = MR.render(m, tex, yaw, pitch, scale=(size - 2.2) * k / span, size=(size * k, size * k), centre=centre,
                        bg=key, shader=MR.shade_icon)
        a = np.asarray(img).astype(int)
        return a, ~((a[..., 0] > 250) & (a[..., 1] < 5) & (a[..., 2] > 250))

    col, hit = shot(1)
    _, fine = shot(5)
    cov = fine.reshape(size, 5, size, 5).mean(axis=(1, 3))
    on = (cov > 0.38) | hit
    col = col.copy()
    for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        nb, nh = np.roll(np.roll(col, dy, 0), dx, 1), np.roll(np.roll(hit, dy, 0), dx, 1)
        fill = on & ~hit & nh
        col[fill] = nb[fill]
        hit = hit | fill
    on &= hit
    out = np.zeros((size, size, 4), dtype=np.uint8)
    out[on, :3] = col[on]
    out[on, 3] = 255
    if outline:
        rim = np.zeros_like(on)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            rim |= np.roll(np.roll(on, dy, 0), dx, 1)
        rim &= ~on
        out[rim] = (12, 10, 18, 255)
    return out


def pattern_icon(helm, rank, lord, size=32):
    """A pattern: a rolled vellum sheet, the set's helm drawn on it in brown ink, a wax seal in its rank's colour (a
    Lord's in gold)."""
    vel = [np.array(AW.hex_rgb(h)) for h in RAMPS["bone"]]
    out = np.zeros((size, size, 4), dtype=np.uint8)
    yy, xx = np.mgrid[0:size, 0:size]
    sheet = (xx >= 5) & (xx <= 26) & (yy >= 5) & (yy <= 26)
    out[sheet, :3] = vel[6]
    out[sheet & ((xx + 2 * yy) % 7 == 0), :3] = vel[5]               # the grain of the skin
    out[sheet & ((xx == 5) | (xx == 26)), :3] = vel[4]
    for y0 in (3, 25):                                               # the rolls at top and bottom
        roll = (xx >= 4) & (xx <= 27) & (yy >= y0) & (yy <= y0 + 3)
        out[roll, :3] = vel[5]
        out[roll & (yy == y0), :3] = vel[6]
        out[roll & (yy == y0 + 3), :3] = vel[3]
        out[roll & ((xx == 4) | (xx == 27)), :3] = vel[3]
        out[roll, 3] = 255
    out[sheet, 3] = 255
    # the helm drawn in ink: a pale wash over its shape, its outline and its darkest parts (slits, eyes) in dark ink
    h = helm.astype(float)
    shape = h[..., 3] > 0
    lum = (h[..., 0] * 0.3 + h[..., 1] * 0.55 + h[..., 2] * 0.15) / 255.0
    edge = np.zeros_like(shape)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge |= ~np.roll(np.roll(np.pad(shape, 1), dy, 0), dx, 1)[1:-1, 1:-1]
    edge &= shape
    ink, wash, mid = np.array([52, 30, 16]), np.array(vel[4]), np.array([128, 88, 54])
    ox, oy = (size - helm.shape[1]) // 2, 6
    region = out[oy:oy + helm.shape[0], ox:ox + helm.shape[1]]
    region[shape, :3] = wash
    region[shape & (lum < 0.42), :3] = mid
    region[edge | (shape & (lum < 0.2)), :3] = ink
    # the seal, low on the right
    seal = np.array(AW.hex_rgb("#efbf55" if lord else RANK_COLOUR[rank]))
    cx, cy, r = 23.0, 23.0, 3.6
    d = np.hypot(xx + 0.5 - cx, yy + 0.5 - cy)
    disc = d <= r
    out[disc, :3] = (seal * 0.72).astype(np.uint8)
    out[(d <= r - 1.2), :3] = seal.astype(np.uint8)
    out[(d <= r - 1.2) & (xx + yy < cx + cy - 2), :3] = np.minimum(seal * 1.25, 255).astype(np.uint8)
    out[disc, 3] = 255
    on = out[..., 3] > 0
    rim = np.zeros_like(on)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        rim |= np.roll(np.roll(on, dy, 0), dx, 1)
    rim &= ~on
    out[rim] = (12, 10, 18, 255)
    return out


def _item_json(iid):
    with open(os.path.join(ITEM_DIR, "items", iid + ".json"), "w") as f:
        f.write('{\n  "model": {\n    "type": "minecraft:model",\n    "model": "awaken:item/%s"\n  }\n}\n' % iid)
    with open(os.path.join(ITEM_DIR, "models/item", iid + ".json"), "w") as f:
        f.write('{\n  "parent": "minecraft:item/generated",\n  "textures": {\n    "layer0": "awaken:item/armour/%s"\n  }\n}\n' % iid)


def items(design, meshes, albedo, material):
    """A set's own items: four piece icons and, above E, its pattern's."""
    sid = design["id"]
    if sid not in OWN_ITEMS:
        return []
    rank, lord = OWN_ITEMS[sid]
    tex = np.concatenate([albedo, material], axis=1)
    tex_dir = os.path.join(ITEM_DIR, "textures/item/armour")
    os.makedirs(tex_dir, exist_ok=True)
    made = []
    for piece in BONES:
        iid = f"{sid}_{piece}"
        yaw, pitch = ICON_VIEW[piece]
        Image.fromarray(icon_of(piece_model(design, piece, meshes), tex, yaw=yaw, pitch=pitch), "RGBA").save(
            os.path.join(tex_dir, iid + ".png"))
        _item_json(iid)
        made.append(iid)
    if rank != "E":
        helm = icon_of(piece_model(design, "helmet", meshes), tex, size=19, yaw=0, pitch=0, outline=False)
        iid = sid + "_pattern"
        Image.fromarray(pattern_icon(helm, rank, lord), "RGBA").save(os.path.join(tex_dir, iid + ".png"))
        _item_json(iid)
        made.append(iid)
    return made


# ---------------------------------------------------------------------------------------------------------------------
# The preview: the set on a plain figure, at rest, in the shader's light

SKIN = {"head": [B(-4, 4, 0, 8, -4, 4, "skin")], "body": [B(-4, 4, -12, 0, -2, 2, "skin")],
        "right_arm": [B(-3, 1, -10, 2, -2, 2, "skin")], "left_arm": [B(-1, 3, -10, 2, -2, 2, "skin")],
        "right_leg": [B(-2, 2, -12, 0, -2, 2, "skin")], "left_leg": [B(-2, 2, -12, 0, -2, 2, "skin")]}


def _rx(deg):
    a = math.radians(deg)
    return np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])


def rest(bone, P, N, cape=6.0, curl=4.0):
    """A bone's part-space corners placed in the figure at rest (design space): the cape hangs back from its yoke a
    few degrees, each link a little more."""
    P = to_part(P)
    N = to_part(N, True)
    bone = bone.split("@")[0]
    if bone.startswith("cape"):
        k = int(bone[4:])
        piv = np.array(PIVOT["cape0"], dtype=float)
        R = np.eye(3)
        off = np.zeros(3)
        for j in range(k + 1):
            R = R @ _rx(cape if j == 0 else curl)  # design space: a turn about x that swings the lower edge back (-z)
            if j < k:
                off = off + R @ np.array([0, -CAPE_LINK, 0])
        return piv + off + P @ R.T, N @ R.T
    return np.array(PIVOT[bone], dtype=float) + P, N


def figure(design, skin=True, awake=False):
    meshes, albedo, material, _ = make(design)
    tex = np.concatenate([albedo, material], axis=1)
    pos, nrm, uv, al = [], [], [], []
    for (piece, bone), b in meshes.items():
        if "@" in bone and not awake:
            continue
        P, N = rest(bone, b.pos, b.nrm)
        pos.append(P)
        nrm.append(N)
        uv.append(b.uv)
        al.append(b.along)
    m = {"pos": np.concatenate(pos) / 16.0, "nrm": np.concatenate(nrm), "uv": np.concatenate(uv), "along": np.concatenate(al),
         "accent": AW.hex_rgb(design["accent"])}
    if skin:
        sk = skin_mesh()
        # the figure's own skin in a texture of its own: draw it first, then the armour over it (one depth buffer)
        return m, tex, sk
    return m, tex, None


def skin_mesh():
    mats = {"skin": AW.Mat("bone", 3, 0.0, 0.2)}
    out = []
    for bone, boxes in SKIN.items():
        built = VX.build(boxes, painter(mats, PIVOT[bone][1]), lambda P, direction=False: P, width=64)
        P = np.array(PIVOT[bone], dtype=float) + built.pos
        out.append((P, built.nrm, built.uv, built.along, built.texture()))
    return out


def render(design, yaw, pitch, scale=13.0, size=(520, 620), centre=(0, 17, 0), time=0.27, light=1.0, awake=False):
    m, tex, sk = figure(design, awake=awake)
    W, H = size
    view = MR.rot(yaw, pitch)
    c = np.asarray(centre, dtype=float) / 16.0
    img = np.tile(MR.BG / 255.0, (H, W, 1))
    depth = np.full((H, W), -1e9)
    parts = []
    if sk:
        for P, N, UV, A, t in sk:
            parts.append(({"pos": P / 16.0, "nrm": N, "uv": UV, "along": A, "accent": m["accent"]}, t))
    parts.append((m, tex))
    for mm, tt in parts:
        pv = (view @ (mm["pos"] - c).T).T * 16.0
        screen = {"sx": W / 2 + pv[:, 0] * scale, "sy": H / 2 - pv[:, 1] * scale, "sz": pv[:, 2], "nv": (view @ mm["nrm"].T).T}
        _raster_into(img, depth, mm, tt, screen, size, time=time, light=light)
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGB")


def _raster_into(img, depth, m, tex, screen, size, **kw):
    """mesh_render._raster into a shared image and depth buffer (so the skin and the armour occlude each other)."""
    W, H = size
    sx, sy, sz, nv = screen["sx"], screen["sy"], screen["sz"], screen["nv"]
    th, tw2 = tex.shape[:2]
    tw = tw2 // 2
    texf = tex.astype(float) / 255.0
    accent = np.asarray(m["accent"], dtype=float) / 255.0
    for t in range(0, len(sx), 3):
        xs, ys, zs = sx[t:t + 3], sy[t:t + 3], sz[t:t + 3]
        area = (xs[1] - xs[0]) * (ys[2] - ys[0]) - (xs[2] - xs[0]) * (ys[1] - ys[0])
        if area >= -1e-9:
            continue
        x0, x1 = max(0, int(xs.min())), min(W - 1, int(math.ceil(xs.max())))
        y0, y1 = max(0, int(ys.min())), min(H - 1, int(math.ceil(ys.max())))
        if x1 < x0 or y1 < y0:
            continue
        gy, gx = np.mgrid[y0:y1 + 1, x0:x1 + 1] + 0.5
        w0 = ((xs[1] - gx) * (ys[2] - gy) - (xs[2] - gx) * (ys[1] - gy)) / area
        w1 = ((xs[2] - gx) * (ys[0] - gy) - (xs[0] - gx) * (ys[2] - gy)) / area
        w2 = 1 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not inside.any():
            continue
        z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
        reg = depth[y0:y1 + 1, x0:x1 + 1]
        ok = inside & (z > reg)
        if not ok.any():
            continue
        wts = np.stack([w0[ok], w1[ok], w2[ok]], axis=1)
        uv = wts @ m["uv"][t:t + 3]
        tu = np.clip((uv[:, 0] * tw2).astype(int), 0, tw - 1)
        tv = np.clip((uv[:, 1] * th).astype(int), 0, th - 1)
        alb, mat = texf[tv, tu], texf[tv, tu + tw]
        keep = alb[:, 3] >= 0.1
        if not keep.any():
            continue
        nw = wts @ m["nrm"][t:t + 3]
        nw /= np.maximum(np.linalg.norm(nw, axis=1, keepdims=True), 1e-9)
        nvv = wts @ nv[t:t + 3]
        nvv /= np.maximum(np.linalg.norm(nvv, axis=1, keepdims=True), 1e-9)
        al = wts @ m["along"][t:t + 3]
        col = MR.shade(alb[keep], mat[keep], nw[keep], nvv[keep], al[keep], accent, **kw)
        ys_, xs_ = np.nonzero(ok)
        ys_, xs_ = ys_[keep], xs_[keep]
        reg[ys_, xs_] = z[ok][keep]
        img[y0:y1 + 1, x0:x1 + 1][ys_, xs_] = col


def preview(out_dir, only=None):
    os.makedirs(out_dir, exist_ok=True)
    outs = []
    for sid, fn in SETS.items():
        if only and sid not in only:
            continue
        design = fn()
        views = [(-30, 10), (30, 10), (0, 0), (150, 10), (90, 5)]
        tiles = [render(design, yaw, pitch) for yaw, pitch in views]
        close = render(design, -25, 10, scale=34, size=(520, 620), centre=(0, 27, 0))
        night = render(design, -30, 8, light=0.25, time=0.33)
        extra = []
        if design.get("awake"):  # mastery IV: front and back awakened
            extra = [render(design, -30, 10, awake=True), render(design, 150, 10, awake=True)]
        sheet = Image.new("RGB", (530 * (7 + len(extra)) + 10, 640), (24, 26, 34))
        for i, t in enumerate(tiles + [close, night] + extra):
            sheet.paste(t, (10 + i * 530, 10))
        path = os.path.join(out_dir, sid + ".png")
        sheet.save(path)
        meshes, albedo, material, _ = make(design)
        Image.fromarray(np.concatenate([albedo, material], axis=1), "RGBA").resize(
            (albedo.shape[1] * 8, albedo.shape[0] * 4), Image.NEAREST).save(os.path.join(out_dir, sid + "_tex.png"))
        outs.append(path)
    return outs


def check():
    bad = []
    for sid, fn in SETS.items():
        design = fn()
        meshes, albedo, material, warns = make(design)
        n = sum(len(b.pos) for b in meshes.values())
        if n > BUDGET:
            bad.append(f"{sid}: {n} corners (at most {BUDGET})")
        bad += [f"{sid}: {w}" for w in warns]
        if albedo.shape[0] > 512:
            bad.append(f"{sid}: atlas {albedo.shape[0]} tall")
        for piece, bones in BONES.items():
            for bone in list(design["pieces"].get(piece, {})) + list(design.get("awake", {}).get(piece, {})):
                if bone not in bones:
                    bad.append(f"{sid}: {piece} on a bone the game doesn't draw it on ({bone})")
        per = {p: sum(len(b.pos) for (pp, _), b in meshes.items() if pp == p) // 3 for p in BONES}
        print(f"  {sid:14s} {n // 3:5d} triangles {per}  atlas {albedo.shape[1]}x{albedo.shape[0]}")
    return bad


def build(out=None):
    """build_all.py entry."""
    for fn in SETS.values():
        write(fn())


def main(argv):
    cmd = argv[0] if argv else "build"
    if cmd in ("build", "check"):
        bad = check()
        for b in bad:
            print("CHECK", b)
        if bad:
            return 1
        if cmd == "build":
            for sid, fn in SETS.items():
                if len(argv) > 1 and sid not in argv[1:]:
                    continue
                write(fn())
            print("written")
        return 0
    if cmd == "preview":
        print("\n".join(preview(argv[1] if len(argv) > 1 else ".", argv[2:] or None)))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
