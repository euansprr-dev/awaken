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
    assets/awaken/textures/entity/armour/<set>.png
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
         "cape0": (0, 23.5, -4), "front_cloth": (0, 11, 5), "back_cloth": (0, 11, -4)}
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
    return dict(id="veilwoven", mats=mats, pieces=pieces, accent=AW.RAMPS["spirit"][6])


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


SETS = {"wildhide": wildhide, "beastbound": beastbound, "gatewrought": gatewrought, "veilwoven": veilwoven,
        "lordscale": lordscale, "sovereign": sovereign}


# ---------------------------------------------------------------------------------------------------------------------
# Building: every piece's bones into one atlas

def to_part(P, direction=False):
    """Design space (y up, z forward) to the bone's part space (Minecraft's model space: y down, z backward)."""
    return np.asarray(P) * np.array([1.0, -1.0, -1.0])


def bone_y(bone):
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
    for piece, bones in design["pieces"].items():
        for bone, boxes in bones.items():
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
        MS.write(os.path.join(mesh_dir, f"{piece}_{bone}.mesh"), b, accent)
    tex_dir = os.path.join(RES, "assets/awaken/textures/entity/armour")
    os.makedirs(tex_dir, exist_ok=True)
    Image.fromarray(np.concatenate([albedo, material], axis=1), "RGBA").save(os.path.join(tex_dir, sid + ".png"))
    return meshes, warns


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


def figure(design, skin=True):
    meshes, albedo, material, _ = make(design)
    tex = np.concatenate([albedo, material], axis=1)
    pos, nrm, uv, al = [], [], [], []
    for (piece, bone), b in meshes.items():
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


def render(design, yaw, pitch, scale=13.0, size=(520, 620), centre=(0, 17, 0), time=0.27, light=1.0):
    m, tex, sk = figure(design)
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
        sheet = Image.new("RGB", (530 * 7 + 10, 640), (24, 26, 34))
        for i, t in enumerate(tiles + [close, night]):
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
            for bone in design["pieces"].get(piece, {}):
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
