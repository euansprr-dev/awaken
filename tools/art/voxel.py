"""Box construction for Arms' weapons (SPEC §25 "Weapons"; the style rules: docs/combat/weapon-style.md).

A weapon is a list of Boxes on a whole-texel grid where 1 texel = 1 item unit (1/16 block: the size of a skin pixel):
    box(...)      an axis-aligned box from its bounds
    slab(...)     a box centred on the blade's plane (z), as deep as asked
    rbox(...)     a box turned about z by a whole angle (45 for a diamond frame or a cut stone)
    sprite(...)   rows of characters drawn like pixel art in the blade's plane, each character a material and a depth:
                  merged into as few boxes as the drawing allows
Every box is whole texels in size. Each visible face is its own chart at exactly 1 texel per unit, so texels on the
weapon are square and all the same size. Texels buried inside another box are culled (a face wholly hidden makes no
triangles); where two boxes' faces lie in one plane over each other, the earlier box keeps the face (no z-fighting).
The visible texels of a face are merged into as few rectangles as they make.

For painting, each visible texel knows what lies beside it in each of its four directions (DIR_*): the surface going on
(FLAT), falling away over an outer edge (EDGE: where a painted bevel goes), running into another part flush (SEAM),
rising into more of its own part (STEP) or into another part (CONTACT: where a contact shadow goes). A painter turns
that into the bands and shadows of the look (arms_weapons.py); positions are the texel's centre in design space, so a
pattern painted by position runs on unbroken across the boxes that make one surface.

Written through mesh.Built / mesh.write, the same versioned mesh the game reads (dev.awaken.arms.WeaponMesh).
"""
import math

import numpy as np

import mesh as MS

FLAT, EDGE, SEAM, STEP, CONTACT = 0, 1, 2, 3, 4
LIFT = 0.02  # how far off a face a point is tested (well under a texel, well over float noise)


class Box:
    """A box: centre, half sizes, rotation (columns are its own axes), the painter's material, the part it belongs to
    (contact shadows fall between parts, not within one) and anything else its painter wants."""

    def __init__(self, centre, half, mat, part=None, rot=None, name="", **data):
        self.c = np.asarray(centre, dtype=float)
        self.h = np.asarray(half, dtype=float)
        self.R = np.eye(3) if rot is None else np.asarray(rot, dtype=float)
        self.mat = mat
        self.part = part or mat
        self.name = name or mat
        self.data = data
        size = self.h * 2
        if np.any(np.abs(size - np.round(size)) > 1e-6) or np.any(size < 1 - 1e-6):
            raise ValueError(f"{self.name}: {size} is not whole texels")

    def inside(self, P):
        L = (np.asarray(P, dtype=float) - self.c) @ self.R
        return np.all(np.abs(L) <= self.h + 1e-6, axis=-1)

    def faces(self):
        """(normal, u, v, the face's corner, texels along u, texels along v): u x v = normal, so faces wind outward."""
        for k in range(3):
            a, b = (k + 1) % 3, (k + 2) % 3
            for s in (1, -1):
                n = s * self.R[:, k]
                u, v, hu, hv = (self.R[:, a], self.R[:, b], self.h[a], self.h[b]) if s > 0 else (self.R[:, b], self.R[:, a], self.h[b], self.h[a])
                corner = self.c + n * self.h[k] - u * hu - v * hv
                yield n, u, v, corner, int(round(2 * hu)), int(round(2 * hv))


def box(x0, x1, y0, y1, z0, z1, mat, part=None, name="", **data):
    lo, hi = np.array([x0, y0, z0], dtype=float), np.array([x1, y1, z1], dtype=float)
    return Box((lo + hi) / 2, (hi - lo) / 2, mat, part, None, name, **data)


def slab(x0, x1, y0, y1, depth, mat, part=None, z=0.0, name="", **data):
    """A box across the blade's plane: x and y bounds, depth texels deep centred on z."""
    return box(x0, x1, y0, y1, z - depth / 2, z + depth / 2, mat, part, name, **data)


def rotz(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1.0]])


def rbox(centre, size, deg, mat, part=None, name="", **data):
    """A box size (x, y, z) texels about centre, turned deg about z (the blade's normal)."""
    return Box(centre, np.asarray(size, dtype=float) / 2, mat, part, rotz(deg), name, **data)


def sprite(rows, x0, ytop, legend, part=None, z=0.0):
    """Pixel art in the blade's plane. rows: strings top to bottom; the first character of each row is the column at
    x = x0 (its texel spans x0 - 0.5 .. x0 + 0.5), the first row spans ytop - 1 .. ytop. legend: character -> (material,
    depth) or (material, depth, part); '.' and ' ' are empty. Runs of one character merge into rectangles."""
    H = len(rows)
    W = max(len(r) for r in rows)
    grid = [r.ljust(W, ".") for r in rows]
    done = np.zeros((H, W), dtype=bool)
    out = []
    for r in range(H):
        for c in range(W):
            ch = grid[r][c]
            if done[r, c] or ch in ". ":
                continue
            w = 1
            while c + w < W and grid[r][c + w] == ch and not done[r, c + w]:
                w += 1
            h = 1
            while r + h < H and all(grid[r + h][c + k] == ch and not done[r + h, c + k] for k in range(w)):
                h += 1
            done[r:r + h, c:c + w] = True
            spec = legend[ch]
            mat, depth = spec[0], spec[1]
            p = spec[2] if len(spec) > 2 else part
            out.append(slab(x0 + c - 0.5, x0 + c + w - 0.5, ytop - r - h, ytop - r, depth, mat, p, z, name=f"{p or mat}:{ch}"))
    return out


def mirror(boxes):
    """The same boxes mirrored across x = 0 (the other quillon, the other wing)."""
    out = []
    M = np.diag([-1.0, 1.0, 1.0])
    for b in boxes:
        R = M @ b.R @ M  # a mirrored rotation, still right-handed
        out.append(Box(b.c * [-1, 1, 1], b.h, b.mat, b.part, R, b.name + "'", **b.data))
    return out


# ---------------------------------------------------------------------------------------------------------------------
# Faces, culling, the neighbours of each texel

class Face:
    """One visible face of a box: its texels (only the visible ones are listed), what lies beside each, its chart."""

    def __init__(self, box, index, n, u, v, corner, su, sv):
        self.box, self.index = box, index
        self.n, self.u, self.v, self.corner, self.su, self.sv = n, u, v, corner, su, sv
        self.x = self.y = 0  # the chart's place in the atlas

    def centres(self, i, j):
        return self.corner + (np.asarray(i)[:, None] + 0.5) * self.u + (np.asarray(j)[:, None] + 0.5) * self.v


def _any(boxes, P, which):
    """Per point: inside any of these boxes (indices)."""
    m = np.zeros(len(P), dtype=bool)
    for k in which:
        m |= boxes[k].inside(P)
    return m


def aligned(a, b, n):
    """Whether two boxes' faces in one plane share a texel grid: the same axes and corners a whole texel apart."""
    M = np.abs(a.R.T @ b.R)
    if not np.allclose(np.sort(M, axis=1)[:, -1], 1, atol=1e-6):  # their axes not parallel (up to a quarter turn)
        return False
    d = (b.c - b.R @ b.h) - (a.c - a.R @ a.h)  # between corners of the two, in design space
    for k in range(3):
        ax = a.R[:, k]
        if abs(ax @ n) > 0.5:
            continue  # the plane's normal: no grid that way
        s = d @ ax
        if abs(s - round(s)) > 1e-6:
            return False
    return True


WARNINGS = []  # faces of two boxes lying in one plane over each other on grids that don't match: they would fight


def faces(boxes):
    """Every face with a visible texel, with its visible mask and each texel's neighbours (FLAT, EDGE, ...). Two boxes
    whose faces overlap in one plane must share a texel grid (else slivers of the two fight for the same pixels):
    any that don't are listed in WARNINGS."""
    WARNINGS.clear()
    out = []
    for bi, b in enumerate(boxes):
        others = [k for k in range(len(boxes)) if k != bi]
        same = [k for k in range(len(boxes)) if boxes[k].part == b.part]
        diff = [k for k in range(len(boxes)) if boxes[k].part != b.part]
        for n, u, v, corner, su, sv in b.faces():
            f = Face(b, bi, n, u, v, corner, su, sv)
            jj, ii = np.mgrid[0:sv, 0:su]
            i, j = ii.ravel(), jj.ravel()
            P = f.centres(i, j)
            lift = P + n * LIFT
            sunk = P - n * LIFT
            hidden = np.zeros(len(P), dtype=bool)
            fights = []
            for k in others:
                o = boxes[k]
                cover = o.inside(lift)
                for du, dv in ((-0.45, -0.45), (0.45, -0.45), (0.45, 0.45), (-0.45, 0.45)):
                    cover &= o.inside(lift + du * u + dv * v)
                # a face of an earlier box in this very plane, over this one: that one keeps it
                flush = o.inside(sunk) & ~o.inside(lift) & (k < bi)
                hidden |= cover | flush
                coplanar = any(on @ n > 1 - 1e-6 and abs(on @ (oc - corner)) < 1e-6 for on, _, _, oc, _, _ in o.faces())
                if k < bi and coplanar and not aligned(b, o, n):
                    touch = np.zeros(len(P), dtype=bool)
                    third = [c for c in others if c != k]
                    for du, dv in ((0, 0), (-0.45, -0.45), (0.45, -0.45), (0.45, 0.45), (-0.45, 0.45)):
                        q = sunk + du * u + dv * v
                        # where the two meet in the plane, unless a third box buries the spot
                        touch |= o.inside(q) & ~o.inside(q + 2 * LIFT * n) & ~_any(boxes, q + 2 * LIFT * n, third)
                    fights.append((o, touch))
            for o, touch in fights:
                if touch.any():
                    WARNINGS.append(f"{b.name} and {o.name} share a plane on different texel grids")
            vis = ~hidden
            if not vis.any():
                continue
            f.i, f.j, f.P = i[vis], j[vis], P[vis]
            # what lies beside each texel, in the directions +u, -u, +v, -v
            f.dirs = np.array([u, -u, v, -v])
            code = np.zeros((len(f.P), 4), dtype=int)
            for d, dvec in enumerate(f.dirs):
                q = f.P + dvec
                above, below = q + n * 0.5, q - n * 0.5
                oa, sa = _any(boxes, above, diff), _any(boxes, above, same)
                sb, ob = _any(boxes, below, same), _any(boxes, below, diff)
                c = np.full(len(q), EDGE)
                c[ob] = SEAM
                c[sb] = FLAT
                c[sa] = STEP
                c[oa] = CONTACT
                code[:, d] = c
            f.code = code
            out.append(f)
    return out


def rects(mask):
    """Greedy rectangles covering a boolean (sv, su) mask: (i0, j0, w, h)."""
    m = mask.copy()
    out = []
    H, W = m.shape
    for j in range(H):
        for i in range(W):
            if not m[j, i]:
                continue
            w = 1
            while i + w < W and m[j, i + w]:
                w += 1
            h = 1
            while j + h < H and m[j + h, i:i + w].all():
                h += 1
            m[j:j + h, i:i + w] = False
            out.append((i, j, w, h))
    return out


# ---------------------------------------------------------------------------------------------------------------------
# The atlas and the mesh

def pack(fs, width, pad=1):
    """Shelf-packs each face's chart (its visible texels' bounds, a texel of padding round it); returns the height (a
    power of two)."""
    for f in fs:
        f.i0, f.j0 = f.i.min(), f.j.min()
        f.cw, f.ch = f.i.max() - f.i0 + 1 + 2 * pad, f.j.max() - f.j0 + 1 + 2 * pad
    x = y = row = 0
    for f in sorted(fs, key=lambda f: (-f.ch, -f.cw)):
        if f.cw > width:
            raise ValueError(f"{f.box.name}: a face {f.cw} texels wide, the atlas {width}")
        if x + f.cw > width:
            x, y, row = 0, y + row, 0
        f.x, f.y = x + pad, y + pad
        x += f.cw
        row = max(row, f.ch)
    h = 16
    while h < y + row:
        h *= 2
    return h


def build(boxes, paint, to_item, width=64, anchors=(), lo_hi=None):
    """boxes in design space (texels); paint(face) -> (albedo (k,4), material (k,4)) uint8 per visible texel, in the
    order of face.i/face.j; to_item maps design space to item space. Returns a mesh.Built."""
    fs = faces(boxes)
    height = pack(fs, width)
    albedo = np.zeros((height, width, 4), dtype=np.uint8)
    material = np.zeros((height, width, 4), dtype=np.uint8)
    filled = np.zeros((height, width), dtype=bool)
    ys = np.concatenate([f.P[:, 1] for f in fs])
    lo, hi = (ys.min() - 0.5, ys.max() + 0.5) if lo_hi is None else lo_hi
    P, N, UV, A = [], [], [], []
    for f in fs:
        a, m = paint(f)
        tx, ty = f.x + f.i - f.i0, f.y + f.j - f.j0
        albedo[ty, tx] = a
        material[ty, tx] = m
        filled[ty, tx] = True
        mask = np.zeros((f.sv, f.su), dtype=bool)
        mask[f.j, f.i] = True
        for i0, j0, w, h in rects(mask):
            quad = [(i0, j0), (i0 + w, j0), (i0 + w, j0 + h), (i0, j0 + h)]
            for tri in ((0, 1, 2), (0, 2, 3)):
                for q in tri:
                    ci, cj = quad[q]
                    p = f.corner + ci * f.u + cj * f.v
                    P.append(p)
                    N.append(f.n)
                    UV.append(((f.x + ci - f.i0) / (2 * width), (f.y + cj - f.j0) / height))
                    A.append(min(1.0, max(0.0, (p[1] - lo) / (hi - lo))))
    albedo, _ = MS.dilate(albedo, filled)
    material, _ = MS.dilate(material, filled)
    P = to_item(np.array(P))
    N = to_item(np.array(N), direction=True)
    return MS.Built(P, N, np.array(UV), np.array(A), albedo, material, list(anchors))
