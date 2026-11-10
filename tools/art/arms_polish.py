"""The weapon polish pass, as a DRAFT (plan: docs/weapon-polish/PLAN.md). Nothing here writes into src/: it builds
each weapon in memory twice, as it is today (arms_weapons.py, untouched) and as the plan proposes, and renders both
at the same views for the approval page.

    python3 tools/art/arms_polish.py directions OUT.png        the three ways to slim a blade, on one Longsword
    python3 tools/art/arms_polish.py strips DIR [id ...]       before | after strips (first person, third person, Hall, icon)
    python3 tools/art/arms_polish.py measure                   each blade's section today and proposed, in texels and on screen

The proposal in one line: blades stop being boxes. A blade is ground: a flat down its middle falling in a real bevel
to a knife edge, drawn to a true point, on the same texel grid, the same paint and the same hilt (Solid, loft, wedge
below). Everything else on a weapon stays the box construction of docs/combat/weapon-style.md.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
for p in (HERE, os.path.join(HERE, "specs")):
    if p not in sys.path:
        sys.path.insert(0, p)
import mesh as MS  # noqa: E402
import mesh_render as MR  # noqa: E402
import voxel as VX  # noqa: E402
import arms_weapons as AW  # noqa: E402
import arms_holds as AH  # noqa: E402


# ---------------------------------------------------------------------------------------------------------------------
# A ground solid: a convex piece of blade whose faces need not be square to the grid. Each face is still its own chart
# at about 1 texel per unit (a bevel 2.24 across takes 2 texels), painted by the same painter.

class Solid:
    def __init__(self, V, polys, mat, part=None, name="", c=None, h=None, R=None, sharp=(), mats=None, ends=(), **data):
        self.V = np.asarray(V, dtype=float)
        self.mat, self.part, self.name, self.data = mat, part or mat, name or mat, data
        self.mats = mats or {}          # face index -> material (the flat's own, a cap's)
        self.edge_mat = data.pop("edge_mat", None)
        lo, hi = self.V.min(axis=0), self.V.max(axis=0)
        self.c = (lo + hi) / 2 if c is None else np.asarray(c, dtype=float)
        self.h = (hi - lo) / 2 if h is None else np.asarray(h, dtype=float)
        self.R = np.eye(3) if R is None else np.asarray(R, dtype=float)
        self.sharp = [(np.asarray(a, dtype=float), np.asarray(b, dtype=float)) for a, b in sharp]
        cen = self.V.mean(axis=0)
        self.polys, self.planes = [], []
        for p in polys:
            p = [k for i, k in enumerate(p) if not np.allclose(self.V[k], self.V[p[i - 1]])]  # a collapsed corner
            if len(p) < 3:
                continue
            P = self.V[p]
            n = np.zeros(3)
            for i in range(len(P)):  # Newell: the best plane of a (slightly twisted) face
                a, b = P[i], P[(i + 1) % len(P)]
                n += np.cross(a, b)
            if np.linalg.norm(n) < 1e-9:
                continue
            n /= np.linalg.norm(n)
            if n @ (P.mean(axis=0) - cen) < 0:
                n, p = -n, p[::-1]
            self.polys.append(p)
            self.planes.append((n, float(n @ self.V[p].mean(axis=0))))
        self.bounds = list(self.planes)
        for n, d in ends:  # planes that close it where it has no face (a joint with the next piece)
            n = np.asarray(n, dtype=float)
            self.bounds.append((n / np.linalg.norm(n), float(d) / np.linalg.norm(n)))
        self._faces = None

    def inside(self, P):
        P = np.asarray(P, dtype=float)
        ok = np.ones(P.shape[:-1], dtype=bool)
        for n, d in self.bounds:
            ok &= (P @ n - d) <= 1e-6
        return ok

    def faces(self):
        if self._faces is None:
            self._faces = []
            for k, (p, (n, d)) in enumerate(zip(self.polys, self.planes)):
                P = self.V[p]
                up = self.R[:, 1]
                v = up - n * (up @ n)
                if np.linalg.norm(v) < 0.2:
                    ax = self.R[:, 0]
                    v = ax - n * (ax @ n)
                v /= np.linalg.norm(v)
                u = np.cross(v, n)
                a, b = (P - P[0]) @ u, (P - P[0]) @ v
                eu, ev = a.max() - a.min(), b.max() - b.min()
                if eu < 1e-6 or ev < 1e-6:
                    continue
                su, sv = max(1, int(round(eu))), max(1, int(round(ev)))
                corner = P[0] + u * a.min() + v * b.min()
                corner = corner + n * (d - n @ corner)
                self._faces.append((n, u * (eu / su), v * (ev / sv), corner, su, sv, P, k))
        for rec in self._faces:
            yield rec[:6]

    def record(self, f):
        for rec in self._faces:
            if rec[3] is f.corner:
                return rec
        raise KeyError(self.name)

    def moved(self, M=None, t=(0, 0, 0), part=None, name=None, data=None):
        """The same solid turned by M (3x3) and moved by t."""
        M = np.eye(3) if M is None else np.asarray(M, dtype=float)
        t = np.asarray(t, dtype=float)
        ends = [(M @ n, d + (M @ n) @ t) for n, d in self.bounds[len(self.planes):]]
        d2 = dict(self.data if data is None else data)
        if self.edge_mat:
            d2["edge_mat"] = self.edge_mat
        return Solid(self.V @ M.T + t, self.polys, self.mat, part or self.part, name or self.name, M @ self.c + t, self.h, M @ self.R,
                     [(M @ a + t, M @ b + t) for a, b in self.sharp], dict(self.mats), ends, **d2)


def is_solid(b):
    return isinstance(b, Solid)


SECTIONS = {
    # (x, z) round the section, x in half widths (-1 the back or left edge, +1 the edge), z in half thicknesses; the
    # flat's half width f is put in per blade. sharp: which corners are knife edges.
    "double": lambda f: ([(-1, 0), (-f, 1), (f, 1), (1, 0), (f, -1), (-f, -1)], (0, 3)),
    "single": lambda f: ([(-1, 1), (-1 + 2 * f, 1), (1, 0), (-1 + 2 * f, -1), (-1, -1)], (2,)),
}


def loft(stations, mat, edge_mat, part="blade", kind="double", d=2.0, flat=1.0, edge=1, apex=None, cap=True, flat_mat=None,
         name=None, seg_mat=None, **data):
    """A ground blade through stations (x, y, width): the section carried along the path and mitred where it bends,
    one Solid per stretch. kind: "double" (a flat down the middle, a bevel to each edge) or "single" (a thick back, a
    flat, one bevel to the edge on the +x side; edge=-1 puts it on -x). apex: where the point is drawn to (x, y), past
    the last station. flat: the flat's width in texels at the widest station. The outer texel of each bevel is painted
    as edge_mat."""
    S = [np.array([s[0], s[1]], dtype=float) for s in stations]
    W = [float(s[2]) for s in stations]
    D = [float(s[3]) if len(s) > 3 else d for s in stations]
    wmax = max(W)
    n = len(S)
    tang = [(S[k + 1] - S[k]) / np.linalg.norm(S[k + 1] - S[k]) for k in range(n - 1)]
    rings = []
    for k in range(n):
        if k == 0:
            b = tang[0]
        elif k == n - 1:
            b = tang[-1]
        else:
            b = tang[k - 1] + tang[k]
            b /= np.linalg.norm(b)
        E = np.array([b[1], -b[0]])
        seg = tang[min(k, n - 2)]
        stretch = 1.0 / max(0.5, E @ np.array([seg[1], -seg[0]]))
        f = min(0.9, flat / wmax)  # the flat narrows with the blade
        pts, sharp = SECTIONS[kind](f)
        ring = [np.array([*(S[k] + E * (x * edge * W[k] / 2 * stretch)), z * D[k] / 2]) for x, z in pts]
        rings.append((ring, sharp, E))
    out = []
    ap = None if apex is None else np.array([apex[0], apex[1], 0.0])
    m = len(rings[0][0])
    for k in range(n - 1 + (ap is not None)):
        A = rings[k][0]
        last = k == n - 1
        Bq = [ap] * m if last else rings[k + 1][0]
        V = np.array(A + Bq)
        polys = [[j, (j + 1) % m, m + (j + 1) % m, m + j] for j in range(m)]
        ends = []
        t = (ap[:2] - S[k]) if last else (S[k + 1] - S[k])
        length = float(np.linalg.norm(t))
        t = t / length
        if k == 0 and cap:
            polys.append(list(range(m)))
        elif k > 0 or not cap:
            e = rings[k][2]
            nb = np.array([-e[1], e[0], 0.0])
            nb = -nb if nb[:2] @ t > 0 else nb
            ends.append((nb, nb @ A[0]))
        if not last:
            if k == n - 2 and ap is None:
                polys.append(list(range(m, 2 * m)))
            else:
                e = rings[k + 1][2]
                nt = np.array([-e[1], e[0], 0.0])
                nt = nt if nt[:2] @ t > 0 else -nt
                ends.append((nt, nt @ Bq[0]))
        sharp = [(A[j], Bq[j]) for j in rings[k][1]]
        R = np.array([[t[1], t[0], 0], [-t[0], t[1], 0], [0, 0, 1.0]])
        c = np.array([*((S[k] + (ap[:2] if last else S[k + 1])) / 2), 0.0])
        h = np.array([max(W[k], W[min(k + 1, n - 1)], 1.0) / 2, max(1.0, round(length)) / 2, max(D) / 2])
        mats = {}
        if flat_mat:  # the flat's own material (a lit fuller): the faces that lie in the blade's plane
            mats["flat"] = flat_mat
        dd = dict(data)
        dd.setdefault("edge_side", edge)
        sm = seg_mat[min(k, len(seg_mat) - 1)] if seg_mat else mat
        s = Solid(V, polys, sm, part, name or f"{part}:{k}", c, h, R, sharp, mats, ends, edge_mat=edge_mat, width=wmax, **dd)
        out.append(s)
    return out


def wedge(xs, mat, edge_mat, part="bit", top_mat=None, **data):
    """An axe's bit ground from the eye to its edge: stations (x, y0, y1, thickness) outward, a slice between each
    pair, the last slice (to thickness 0) the cutting edge in edge_mat."""
    out = []
    for k in range(len(xs) - 1):
        (xa, ya0, ya1, da), (xb, yb0, yb1, db) = xs[k], xs[k + 1]
        V = np.array([[xa, ya0, -da / 2], [xa, ya1, -da / 2], [xa, ya1, da / 2], [xa, ya0, da / 2],
                      [xb, yb0, -db / 2], [xb, yb1, -db / 2], [xb, yb1, db / 2], [xb, yb0, db / 2]])
        polys = [[3, 2, 6, 7], [0, 1, 5, 4], [1, 2, 6, 5], [0, 3, 7, 4]]
        if k == 0:
            polys.append([0, 1, 2, 3])
        ends = [] if k == 0 else [((1.0 if xa > xb else -1.0, 0, 0), (1.0 if xa > xb else -1.0) * xa)]
        if db > 1e-6:
            ends.append(((1.0 if xb > xa else -1.0, 0, 0), (1.0 if xb > xa else -1.0) * xb))
        last = k == len(xs) - 2
        sharp = [(V[4], V[5])] if db < 1e-6 else []
        mats = {"top": top_mat} if top_mat else {}
        R = np.eye(3)
        dd = dict(data, edge_all=True) if last else dict(data)
        s = Solid(V, polys, mat, part, f"{part}:{k}", None, None, R, sharp, mats, ends, edge_mat=edge_mat if last else None, **dd)
        out.append(s)
    return out


# ---- painting a ground face -------------------------------------------------------------------------------------------

# what a blade's edge is painted, the left one (toward the painted light) and the right: (ramp, step, metal, gloss, glow)
EDGE_SPEC = {
    "flint_edge": (("flint", 7, 0.1, 0.8, 0.0), ("flint", 6, 0.1, 0.8, 0.0)),
    "tusk_edge": (("bone", 7, 0.0, 0.6, 0.0), ("bone", 6, 0.0, 0.6, 0.0)),
    "slate_flat": (("mirror", 6, 0.7, 0.95, 0.0), ("mirror", 5, 0.7, 0.95, 0.0)),
    "mirror_edge": (("mirror", 6, 0.7, 0.95, 0.0), ("mirror", 5, 0.7, 0.95, 0.0)),
    "veil_edge": (("spirit", 6, 0.0, 0.5, 0.62), ("mirror", 5, 1.0, 0.95, 0.0)),
    "spirit_edge": (("spirit", 6, 0.0, 0.5, 0.62), ("spirit", 6, 0.0, 0.5, 0.62)),
    "ember_edge": (("ember", 5, 0.0, 0.6, 0.8), ("ember", 5, 0.0, 0.6, 0.8)),
    "gold_edge": (("gold", 6, 0.45, 0.9, 0.0), ("gold", 5, 0.45, 0.9, 0.0)),
    "horn_tip": (("bone", 4, 0.0, 0.45, 0.0), ("bone", 3, 0.0, 0.45, 0.0)),
}
# the curved single-edged blades (arms_weapons.katana): (edge, hamon, edge glow, hamon glow)
KAT = {
    "flint_kat": (("flint", 7), ("bone", 6), 0.0, 0.0), "tusk_kat": (("bone", 7), ("bronze", 5), 0.0, 0.0),
    "slate_kat": (("mirror", 6), ("signal", 6), 0.0, 0.8), "veil_kat": (("mirror", 6), ("spirit", 6), 0.0, 0.75),
    "lord_kat": (("ember", 6), ("gold", 6), 0.8, 0.0), "ivory_kat": (("gold", 6), ("crimson", 5), 0.0, 0.9),
    "scythe_b": (("spirit", 6), ("mirror", 5), 0.75, 0.0), "scythe_s": (("crimson", 5), ("gold", 6), 0.9, 0.0),
    "l_dream_kat": (("dream", 6), ("white", 6), 0.8, 0.4),
}


def seg_dist(P, a, b):
    ab = b - a
    L = ab @ ab
    if L < 1e-12:
        return np.linalg.norm(P - a, axis=1)
    t = np.clip(((P - a) @ ab) / L, 0, 1)
    return np.linalg.norm(P - (a + t[:, None] * ab), axis=1)


def painter2(clean=False):
    base = AW.painter(clean)

    def paint(f):
        b = f.box
        if not is_solid(b):
            return base(f)
        rec = b.record(f)
        flatface = abs(f.n[2]) > 0.995
        mat = b.mats.get("flat") if flatface and "flat" in b.mats else None
        if mat is None and "top" in b.mats and f.n[1] > 0.5:
            mat = b.mats["top"]
        if mat is None and "bottom" in b.mats and f.n[1] < -0.5:
            mat = b.mats["bottom"]
        mat = mat or b.mat
        m = AW.MATS[mat]
        t = AW.Tex(f, m, clean)
        s = float(f.n @ AW.LIGHT)
        ground = abs(f.n[2]) > 0.5 and not flatface
        if ground:  # a bevel: the side toward the painted light a step up, the far side a step down
            t.tone += 1 if s > 0.05 else (-1 if s < -0.05 else 0)
        else:
            t.tone += int(s > 0.4) - int(s < -0.4)
        band, contact, edge = AW.bands(f)
        t.band, t.contact, t.edge = band, contact, edge
        if m.bevel:
            t.tone += np.clip(band, -1, 1) if (abs(f.n[1]) > 0.9 or ground) else band
        t.tone[contact] = m.base - m.contact
        rim = np.full(t.k, 99.0)
        for a_, b_ in b.sharp:
            rim = np.minimum(rim, seg_dist(t.P, a_, b_))
        t.rim = rim
        kat = KAT.get(mat)
        for d in m.details:
            if kat is not None and d.__qualname__.startswith("katana"):
                continue
            if d.__qualname__.startswith("edges_of"):
                continue
            d(t)
        tu = float(np.linalg.norm(f.u))
        near = rim < 0.5 * tu + 0.56          # the texel that holds the edge
        if b.data.get("edge_all"):
            near = np.full(t.k, abs(f.n[2]) > 0.5)
        if kat is not None and t.flat:
            (er, et), (hr, ht), eg, hg = kat
            back = (t.P - b.c) @ b.R[:, 0] * b.data.get("edge_side", 1)
            t.tone[back < -b.h[0] + 0.9] -= 1
            hm = (rim >= 0.5 * tu + 0.56) & (rim < 1.5 * tu + 0.6) & (b.data.get("width", 3) >= 3)
            row = np.floor((t.P - b.c) @ b.R[:, 1] + b.h[1] + 1e-6).astype(int)
            t.put(hm, ramp_name=hr, tone=np.where((row % 4) < 2, ht, ht - 1), emit=hg, metal=0.2, gloss=0.8)
            t.put(near, ramp_name=er, tone=np.clip(et + np.clip(t.band, -1, 1), 0, len(AW.RAMPS[er]) - 1), emit=eg, metal=0.7, gloss=0.95)
            a0, b0 = b.data.get("span", (0, 30))
            t.flow[:] = np.where(t.emit > 0, np.clip((t.y - a0) / (b0 - a0), 0.02, 1.0), 0.0)
        elif b.edge_mat and near.any():
            spec = EDGE_SPEC.get(b.edge_mat)
            if spec is None:
                em = AW.MATS[b.edge_mat]
                spec = ((em.ramp, em.base, em.metal, em.gloss, em.emit),) * 2
            side = (t.P - b.c) @ b.R[:, 0] if "edge_fixed" not in b.data else np.full(t.k, float(b.data["edge_fixed"]))
            for sel, (rn, st, me, gl, em_) in ((near & (side <= 0), spec[0]), (near & (side > 0), spec[1])):
                bd = np.clip(t.band, 0, 1) if em_ == 0 else 0
                t.put(sel, ramp_name=rn, tone=np.clip(st + bd, 0, len(AW.RAMPS[rn]) - 1), metal=me, gloss=gl, emit=em_)
            if any(sp[4] > 0 for sp in spec):
                a0, b0 = sorted(b.data.get("span", (4, 23)))
                lit = near & (t.emit > 0)
                t.flow[lit] = np.clip((t.y[lit] - a0) / max(b0 - a0, 1), 0.02, 1.0)
        alb = np.concatenate([AW.colours(t), np.ones((t.k, 1))], axis=1)
        mm = np.stack([t.metal, t.gloss, t.emit, t.flow], axis=1)
        return (np.clip(alb, 0, 1) * 255).round().astype(np.uint8), (np.clip(mm, 0, 1) * 255).round().astype(np.uint8)
    return paint


def build2(boxes, paint, to_item, width=64, anchors=()):
    """voxel.build, with a Solid's faces written as their own polygons."""
    fs = VX.faces(boxes)
    height = VX.pack(fs, width)
    albedo = np.zeros((height, width, 4), dtype=np.uint8)
    material = np.zeros((height, width, 4), dtype=np.uint8)
    filled = np.zeros((height, width), dtype=bool)
    ys = np.concatenate([f.P[:, 1] for f in fs])
    lo, hi = ys.min() - 0.5, ys.max() + 0.5
    P, N, UV, A, PT = [], [], [], [], []
    for f in fs:
        a, m = paint(f)
        tx, ty = f.x + f.i - f.i0, f.y + f.j - f.j0
        albedo[ty, tx] = a
        material[ty, tx] = m
        filled[ty, tx] = True
        if is_solid(f.box):
            poly = f.box.record(f)[6]
            tu, tv = np.linalg.norm(f.u), np.linalg.norm(f.v)
            uh, vh = f.u / tu, f.v / tv
            if np.cross(poly[1] - poly[0], poly[2] - poly[0]) @ f.n < 0:
                poly = poly[::-1]
            for k in range(1, len(poly) - 1):
                for p in (poly[0], poly[k], poly[k + 1]):
                    ci, cj = ((p - f.corner) @ uh) / tu, ((p - f.corner) @ vh) / tv
                    P.append(p)
                    N.append(f.n)
                    UV.append(((f.x + ci - f.i0) / (2 * width), (f.y + cj - f.j0) / height))
                    A.append(min(1.0, max(0.0, (p[1] - lo) / (hi - lo))))
                    PT.append(f.box.part)
            continue
        mask = np.zeros((f.sv, f.su), dtype=bool)
        mask[f.j, f.i] = True
        for i0, j0, w, h in VX.rects(mask):
            quad = [(i0, j0), (i0 + w, j0), (i0 + w, j0 + h), (i0, j0 + h)]
            for tri in ((0, 1, 2), (0, 2, 3)):
                for q in tri:
                    ci, cj = quad[q]
                    p = f.corner + ci * f.u + cj * f.v
                    P.append(p)
                    N.append(f.n)
                    UV.append(((f.x + ci - f.i0) / (2 * width), (f.y + cj - f.j0) / height))
                    A.append(min(1.0, max(0.0, (p[1] - lo) / (hi - lo))))
                    PT.append(f.box.part)
    albedo, _ = MS.dilate(albedo, filled)
    material, _ = MS.dilate(material, filled)
    built = MS.Built(to_item(np.array(P)), to_item(np.array(N), direction=True), np.array(UV), np.array(A), albedo, material, list(anchors))
    built.parts = PT
    return built


def make2(row, design=None, clean=False):
    """arms_weapons.make over a design that may hold Solids."""
    boxes, anchors, look = (design or AW.design_of(row))()
    item_anchors = [(tuple(AW.to_item(np.array([c]))[0]), r * AW.SCALE, AW.hex_rgb(rgb), kind) for c, r, rgb, kind in anchors]
    built = build2(boxes, painter2(clean), AW.to_item, width=64, anchors=item_anchors)
    built.streams = []
    built.boxes = boxes
    return built, look


# ---------------------------------------------------------------------------------------------------------------------
# The same designs, ground: stand-ins for arms_weapons' blade helpers that take the same arguments and return Solids,
# so every design keeps its own lines (its length, width, wave, materials, hilt) and only its section changes.

def flat_of(W):
    bevel = 1 if W <= 3 else (2 if W <= 8 else 3)
    return max(1.0, W - 2 * bevel)


def thick_of(W):
    return 1.0 if W < 3 else (2.0 if W < 9 else 2.5)


def ground(segs, core, edge, core_d=3, edge_d=1, part="blade", shift=lambda y: 0, x=0.0, d=None, back=None, tipx=1.0, flat=None,
           point=True, flat_mat=None, **data):
    """arms_weapons.blade's stand-in: the same segments (y0, y1, width, core width) read as an outline, not a stack: the
    widest run kept, the root's shoulders sloped, and the point drawn from where the old steps began, a little lower,
    to a true apex a texel past the old tip."""
    segs = sorted(segs)
    W = max(s[2] for s in segs)
    wide = [s for s in segs if s[2] == W]
    y_end = segs[-1][1]
    y_first = segs[0][0]
    st = [(x + shift(y_first), y_first, segs[0][2])]
    for s in segs:
        y0, y1, w, _cw = s
        if y0 > wide[-1][0]:
            break
        cx = x + shift(y0)
        if s is wide[-1]:
            bk = (y_end - y1) / 2 if back is None else back
            if back is None:
                bk = min(bk, max(0.0, 1.6 * W - (y_end + tipx - y1)))
            bk = min(bk, (y1 - y0) / 2)
            if len(wide) > 1 or y0 != y_first:
                st.append((cx, min(y0 + 1, y1 - bk - 0.5), w))
            st.append((cx, y1 - bk, w))
        elif not (y0 == y_first and y1 - y0 < 0.01):
            st.append((cx, (y0 + y1) / 2, w))
    st = [s for i, s in enumerate(st) if i == 0 or s[1] > st[i - 1][1] + 1e-6]
    last = segs[-1]
    kw = dict(part=part, kind="double", d=thick_of(W) if d is None else d, flat=flat_of(W) if flat is None else flat, flat_mat=flat_mat)
    if not point:
        if y_end > st[-1][1] + 1e-6:
            st.append((x + shift(last[0]), y_end, last[2]))
        return loft(st, core, edge, **kw, **data)
    return loft(st, core, edge, apex=(x + shift(last[0]), y_end + tipx), **kw, **data)


D_OF = {1: 1.0, 2: 1.5, 3: 2.0, 4: 2.5, 5: 3.0}


def curve(x, y, links, part, side=-1, back=None, edge=1, keep="back", flat=1.0, edge_mat=None, tip=0.0, **data):
    """arms_weapons.chain's stand-in for a curved blade: the same links (degrees, length, width, depth, material) read as
    a path, a single-edged ground section carried along it and mitred at each bend (no joints to see), drawn to a point.
    keep: which line stays whole as it narrows ("back": the edge sweeps up to the back, a katana's point; "centre")."""
    p = np.array([x, y], dtype=float)
    st, mats = [], []
    w0 = links[0][2]
    dv = np.array([0.0, 1.0])
    for k, (deg, length, width, depth, mat) in enumerate(links):
        th = math.radians(deg * side)
        dv = np.array([math.sin(th), math.cos(th)])
        if k:
            p = p - dv * (min(width, length) / 2 if back is None else back)
        perp = np.array([dv[1], -dv[0]])
        off = -edge * (w0 - width) / 2 if keep == "back" else 0.0
        q = p + perp * off
        st.append((q[0], q[1], width, D_OF.get(depth, 2.0)))
        mats.append(mat)
        p = p + dv * length
    perp = np.array([dv[1], -dv[0]])
    apex = p + dv * tip + (perp * (-edge * w0 / 2) if keep == "back" else 0.0)
    data.pop("stagger", None)
    data.pop("cycle", None)
    flip = data.pop("flip", False)
    return loft(st, mats[0], edge_mat, part=part, kind="single", flat=flat, edge=edge, apex=(apex[0], apex[1]), seg_mat=mats, **data)


ORIG = {n: getattr(AW, n) for n in ("blade", "bit", "chain", "spin", "turned", "moved", "KATANA_LINKS")}
ORIG_MIRROR = VX.mirror
BLADE_PARTS = ("blade", "hook", "sickle", "fin")
LORD_BLADES = ("l_gill", "l_ice", "l_storm", "l_iron")  # the Lord-forged blades' own steels
TUSK_EDGE = {"tusk": "tusk_edge", "veil": "spirit_edge", "flint": "flint_edge"}


def chain2(x, y, links, part, side=-1, back=None, stagger=0.0, cycle=2, **data):
    """arms_weapons.chain, but a blade's links become one ground curve; every other chain (antlers, quillons, a real
    chain's links) stays boxes."""
    mat = links[0][4]
    if part not in BLADE_PARTS or not (mat in KAT or mat in TUSK_EDGE or mat in LORD_BLADES):
        return ORIG["chain"](x, y, links, part, side, back, stagger, cycle, **data)
    if mat in LORD_BLADES:  # painted as its own material, edge and all
        flip = bool(data.get("flip"))
        return curve(x, y, links, part, side, back, edge=-1 if (flip or part == "hook") else 1,
                     keep="back" if mat == "l_ice" else "centre", **data)
    if mat in KAT:
        if data.get("flip"):  # a scythe: the edge along the inside
            return curve(x, y, links, part, side, back, edge=-1, keep="centre", **data)
        return curve(x, y, links, part, side, back, edge=1, keep="back", **data)
    edge_mat = TUSK_EDGE.get(mat, "tusk_edge")
    if part != "blade":
        return curve(x, y, links, part, side, back, edge=-1, keep="centre", edge_mat=edge_mat, **data)
    edge = 1 if (side == 1 or links[0][2] >= 4) else -1
    return curve(x, y, links, part, side, back, edge=edge, keep="centre", edge_mat=edge_mat, **data)


def bit2(x0, cols, core, edge, edge_w=1, core_d=3, edge_d=1, side=-1, part="bit", top_mat=None, **data):
    """arms_weapons.bit's stand-in: the same columns read as a flare, not steps: a wedge thick at the eye and ground to
    its edge, its top and bottom straight lines out to the edge's horns."""
    xs = [(x0, cols[0][1], cols[0][2], float(core_d))]
    x = x0
    n = len(cols)
    for k, (w, y0, y1) in enumerate(cols):
        x += side * w
        ya, yb = (cols[k + 1][1], cols[k + 1][2]) if k + 1 < n else (cols[-1][1] - 1, cols[-1][2] + 1)
        xs.append((x, ya, yb, core_d + (1.0 - core_d) * (k + 1) / n))
    (xa, a0, a1, _), (xb, b0, b1, _) = xs[-2], xs[-1]
    run = max(abs(xb - xa), 1e-6)
    xs.append((x + side * edge_w, b0 + (b0 - a0) / run * edge_w, b1 + (b1 - a1) / run * edge_w, 0.0))
    dd = dict(data)
    dd["edge_fixed"] = -1.0
    return wedge(xs, core, edge, part, top_mat, **dd)


def spin2(boxes):
    R180 = VX.rotz(180)
    out = []
    for b in boxes:
        if not is_solid(b):
            out += ORIG["spin"]([b])
            continue
        data = dict(b.data)
        if "span" in data:
            data["span"] = tuple(-v for v in data["span"])
        if "column" in data:
            data["column"] = -data["column"]
        out.append(b.moved(R180, part=b.part + "~", name=b.name + "~", data=data))
    return out


def turned2(boxes, deg, about):
    Rz = VX.rotz(deg)
    o = np.asarray(about, dtype=float)
    return [b.moved(Rz, o - Rz @ o) if is_solid(b) else ORIG["turned"]([b], deg, about)[0] for b in boxes]


def moved2(boxes, d, keep=lambda b: True):
    return [b.moved(None, d) if is_solid(b) else ORIG["moved"]([b], d)[0] for b in boxes if keep(b)]


def mirror2(boxes):
    M = np.diag([-1.0, 1.0, 1.0])
    out = []
    for b in boxes:
        if is_solid(b):
            data = dict(b.data)
            if "edge_fixed" in data:
                data["edge_fixed"] = -data["edge_fixed"]
            out.append(b.moved(M, name=b.name + "'", data=data))
        else:
            out += ORIG_MIRROR([b])
    return out


def lengthened2(design, dy, cut):
    """arms_weapons.lengthened over a design that holds Solids (a head's ground bit rises with the head)."""
    def build():
        B, anchors, look = design()
        boxes = [b for b in B if not is_solid(b)]
        solids = [b for b in B if is_solid(b)]
        out, anchors, look = AW.lengthened(lambda: (boxes, anchors, look), dy, cut)()
        for s in solids:
            data = dict(s.data)
            if s.V[:, 1].min() >= cut - 1e-6:
                if "row" in data:
                    data["row"] = data["row"] + dy
                elif "span" in data:
                    data["span"] = tuple(v + dy for v in data["span"])
                out.append(s.moved(None, (0, dy, 0), data=data))
            else:
                out.append(s)
        return out, anchors, look
    return build


class Ground:
    """While open, arms_weapons' designs build ground blades."""

    def __enter__(self):
        AW.blade, AW.bit, AW.chain, AW.spin, AW.turned, AW.moved = ground, bit2, chain2, spin2, turned2, moved2
        VX.mirror = mirror2
        AW.KATANA_LINKS = KATANA_LINKS
        return self

    def __exit__(self, *a):
        for n, v in ORIG.items():
            setattr(AW, n, v)
        VX.mirror = ORIG_MIRROR


# the Katana's curve: even from the collar to the point (the old one ran straight and hooked back at the tip), a
# little longer, its point drawn up to the back (curve's keep="back")
KATANA_LINKS = ((1, 5, 3, 2), (3, 5, 3, 2), (5, 5, 3, 2), (7, 4, 3, 2), (9, 4, 3, 2), (11, 3, 3, 2))

AFTER = {}  # (style, tier) or id -> design, where the stand-ins alone don't do it




# ---- the designs the stand-ins can't reach: the same weapons, their blades re-cut by hand ----------------------------------

def _swap(design, drop, add, fix=None):
    """A design without the parts in drop, with what add() returns, each kept box passed through fix."""
    def build():
        B, anchors, look = design()
        out = []
        for b in B:
            if b.part in drop:
                continue
            out.append(fix(b) if fix else b)
        return out + add(), anchors, look
    return build


def _depth(b, part, d):
    """A box of a part made d deep."""
    if is_solid(b) or b.part != part:
        return b
    return VX.Box(b.c, [b.h[0], b.h[1], d / 2], b.mat, b.part, b.R, b.name, **b.data)


# the Beastbound Longsword: the tusk cleaver ground from its capped back to one edge, its point drawn up to the back
AFTER[("longsword", "D")] = _swap(
    AW.beastbound, ("blade",),
    lambda: loft([(0, 5, 5), (0, 23, 5)], "tusk", "tusk_edge", kind="single", d=2.0, flat=2.0, apex=(-2.5, 29.5), groove=-1),
    lambda b: _depth(b, "back", 3))


def _flint_cleaver():
    # the struck slab: both edges ground, a flake broken out of each, its top struck off at a slant up to the left corner
    st = [(0, 8, 9), (0, 13.6, 9), (-0.5, 14.2, 8), (-0.5, 14.8, 8), (0, 15.4, 9), (0, 21.6, 9), (0.5, 22.2, 8), (0.5, 22.8, 8),
          (0, 23.4, 9), (0, 30, 9)]
    return loft(st, "flint", "flint_edge", d=2.0, flat=5.0, apex=(-4.5, 34.5))


AFTER[("greatblade", "E")] = _swap(AW.wildhide_greatblade, ("blade",), _flint_cleaver)
AFTER[("greatblade", "D")] = _swap(
    AW.beastbound_greatblade, ("blade",),
    lambda: loft([(-0.5, 8, 8), (-0.5, 28, 8)], "tusk", "tusk_edge", kind="single", d=2.5, flat=5.0, apex=(-4.5, 34.5), groove=-2),
    lambda b: _depth(b, "back", 3))


def _gate_window():
    B, anchors, look = AW.gatewrought_greatblade()
    out = []
    for b in B:
        if b.part in ("blade", "rim") and not is_solid(b):
            if b.c[1] - b.h[1] >= 30 - 1e-6:
                continue  # the stepped point
            if abs(b.h[2] - 1.5) < 1e-6:
                b = VX.Box(b.c, [b.h[0], b.h[1], 1.0], b.mat, b.part, b.R, b.name, **b.data)
        out.append(b)
    out += loft([(0, 30, 9), (0, 31, 9)], "slate", "mirror_edge", d=2.0, flat=5.0, apex=(0, 35.5))
    return out, anchors, look


AFTER[("greatblade", "C")] = _gate_window


def _flamberge():
    st = [(0, 8, 7)] + [(0, y0 + 1, max(AW.FLAMBERGE[k % 4], 5)) for k, y0 in enumerate(range(8, 29, 2))]
    return loft(st, "veil", "veil_edge", d=2.5, flat=3.0, apex=(0, 35.0), span=AW.GB_SPAN)


AFTER[("greatblade", "B")] = _swap(AW.veilwoven_greatblade, ("blade",), _flamberge)


def _thin_tines(b):
    if is_solid(b) or not b.part.startswith("tine") or b.h[2] <= 1.0:
        return b
    return VX.Box(b.c, [b.h[0], b.h[1], 1.0], b.mat, b.part, b.R, b.name, **b.data)


AFTER[("greatblade", "A")] = _swap(
    AW.lordscale_greatblade, ("blade",),
    lambda: loft([(0, 8, 7), (0, 11, 7), (0, 17, 9), (0, 21, 11), (0, 28, 11)], "lord", "ember_edge", d=2.5, flat=5.0, span=AW.GB_SPAN),
    _thin_tines)

AFTER[("daggers", "E")] = _swap(
    AW.wildhide_daggers, ("blade",),
    lambda: loft([(0, 3, 5), (0, 10, 5)], "flint", "flint_edge", kind="single", d=2.0, flat=2.0, apex=(-2.5, 14.5)))


def _axe(raw, tier, drop=(), top=None, bottom=None):
    def design():
        B, anchors, look = raw()
        out = []
        for b in B:
            if b.part in drop:
                continue
            if is_solid(b) and b.part == "bit":
                if top:
                    b.mats["top"] = top
                if bottom:
                    b.mats["bottom"] = bottom
            out.append(b)
        return out, anchors, look
    return lengthened2(design, AW.AXE_LONGER[tier], 6.0)


AFTER[("axe", "A")] = _axe(AW.lordscale_axe, "A", ("cap",), top="gold")
AFTER[("axe", "S")] = _axe(AW.sovereign_axe, "S", ("rim_low", "rim_top"), top="gold_edge", bottom="gold_edge")


def chainblade2(t):
    """arms_weapons.chainblade with its head ground (the chain itself is boxes still)."""
    k = AW.KIT[t]
    a_mat, b_mat = AW.CHAIN_LINKS[t]
    B = [AW.grip(-3, 2, k["wrap"]), AW.ring(2, 3, k["metal"], part="guard")]
    B += [VX.slab(-2.5, 2.5, -2, -1, 5, a_mat, "coil"), VX.slab(-2.5, 2.5, -1, 0, 3, b_mat, "coil'"), VX.slab(-2.5, 2.5, 0, 1, 5, a_mat, "coil''")]
    links = [(deg, 2, 2, 1, a_mat) if i % 2 == 0 else (deg, 2, 1, 2, b_mat) for i, deg in enumerate(AW.CHAIN_PATH)]
    ch = ORIG["chain"](0.0, 3.0, links, "chain", back=0.5)
    B += ch
    a = math.radians(-AW.CHAIN_PATH[-1])
    end = np.asarray(ch[-1].data["base"]) + np.array([math.sin(a), math.cos(a)]) * 1.5
    head = moved2(AW.chain_head(t), (end[0], end[1], 0.0))
    for b in head:
        if "span" in b.data:
            b.data["span"] = tuple(v + end[1] for v in b.data["span"])
    B += turned2(head, AW.CHAIN_PATH[-1], (end[0], end[1], 0.0))
    stone = (0, -4, 0)
    B += [VX.slab(-2, 2, -5, -3, 4, k["metal"], "pommel"), AW.stone_at(t, stone, 2, 6)]
    line = [tuple(b.c) for b in ch[2::2]]
    return B, [(stone, 1.4, AW.GLOW[t], 1)], AW.look_of(t, (-3, 2), (0, 6), line, stone)


def sword2(t):
    """The Shieldblade's sword: the Longsword's own section (5 wide, ground), the lit fuller its flat."""
    k = AW.KIT[t]
    fuller = {"C": "signal_line", "B": "spirit_edge", "A": "ember_edge", "S": "light_crimson"}.get(t)
    B = [AW.grip(-4, 3, k["wrap"]), AW.ring(-5, -4, k["metal"], part="pommel")]
    B += loft([(0, 5, 5), (0, 24, 5)], k["core"], k["edge"], d=2.0, flat=1.0, apex=(0, 32.0), flat_mat=fuller, span=(5, 31), column=0)
    B += [VX.slab(-6.5, 6.5, 3, 5, 3, k["metal"], "guard"), VX.slab(-1.5, 1.5, 5, 7, 3, k["metal"], "guard_top")]
    stone = (0.0, 4.0, 0.0)
    B.append(AW.stone_at(t, stone, 2, 5))
    if t == "E":
        for sx in (-1, 1):
            B.append(VX.rbox((sx * 7.5, 6.0, 0), (2, 5, 3), -sx * 25, "antler", f"quillon{sx}"))
    elif t == "D":
        for sx in (-1, 1):
            for y in (8.0, 14.0, 20.0):
                B.append(VX.rbox((sx * 2.9, y, 0), (1, 3, 1), sx * 20, "tooth", f"tooth{sx}{y}"))
    elif t == "A":
        B += AW.both(AW.fan(-6.0, 5.0, [(35, 4, 2, 2, "gold"), (65, 3, 1, 1, "feather")], "guard_wing"))
    elif t == "S":
        for sx in (-1, 1):
            B.append(VX.rbox((sx * 7.0, 5.0, 0), (2, 2, 3), 45, "gold", f"crown{sx}"))
    line = [(0.0, y, 1.0) for y in (10.0, 18.0, 25.0)] if t in "BAS" else []
    return B, [(stone, 1.4, AW.GLOW[t], 1)], AW.look_of(t, (-4, 3), (0, 15), line, stone)


def shieldblade2(t):
    sb, sa, sl = sword2(t)
    hb, ha, _ = AW.shieldblade_shield(t)
    B, anchors = AW.shield_over_sword(sb, hb, sa, ha)
    return B, anchors, sl


for _t in "EDCBAS":
    AFTER[("chainblade", _t)] = (lambda tt=_t: chainblade2(tt))
    AFTER[("shieldblade", _t)] = (lambda tt=_t: shieldblade2(tt))


def AFTER_SWORD(t):
    """The Shieldblade's sword, proposed (the hand's part)."""
    return sword2(t)


# the Hive Regent's Sting: the stinger one long ground spike, its barbs set back along it
def _sting():
    B, anchors, look = AW.hive_regent_daggers()
    out = [b for b in B if b.part not in ("sting",) and not b.part.startswith("barb")]
    out += loft([(0, 3, 5), (0, 6, 5)], "l_sting", None, part="sting", d=3.0, flat=1.0, apex=(0, 16.6))
    for k, (x, y) in enumerate(((-2.2, 6.8), (2.2, 6.8), (-1.5, 10.2), (1.5, 10.2), (-0.9, 13.0), (0.9, 13.0))):
        out.append(VX.rbox((x, y, 0), (1, 2, 1), (-40 if x < 0 else 40), "obsidian", f"barb{k}"))
    return out, anchors, look


AFTER["hive_regent_daggers"] = _sting


def after_design(row):
    """A row's proposed design (to be called inside Ground)."""
    wid, tier, style = row[0], row[2], row[8]
    if wid in AFTER:
        return AFTER[wid]
    if wid not in AW.LORD_COLOUR and (style, tier) in AFTER:
        return AFTER[(style, tier)]
    if style == "axe" and wid not in AW.LORD_COLOUR:
        raw = {"E": AW.wildhide_axe, "D": AW.beastbound_axe, "C": AW.gatewrought_axe, "B": AW.veilwoven_axe, "A": AW.lordscale_axe,
               "S": AW.sovereign_axe}[tier]
        return lengthened2(raw, AW.AXE_LONGER[tier], 6.0)
    return AW.design_of(row)


def before(row):
    return AW.make(row)


def after(row):
    with Ground():
        return make2(row, after_design(row))


def as_m(built, look):
    return {"pos": built.pos / 16.0, "nrm": built.nrm, "uv": built.uv, "along": built.along, "accent": AW.hex_rgb(look["accent"])}


ROWS = {r[0]: r for r in AW.CATALOG}
SKY = np.array([88, 96, 112])


def fp_shot(row, built, look, size=(960, 540)):
    m = as_m(built, look)
    fp = AW.row_hold(row, built.pos / 16.0)["firstperson_righthand"]
    hold = (tuple(fp["rotation"]), tuple(fp["translation"]), fp["scale"][0])
    return MR.render_fp(m, built.texture(), display=hold, size=size, time=0.27, bg=SKY)


def hall_shot(built, look, size=(260, 420), yaw=28, pitch=12, bg=(20, 18, 26)):
    up = AW.upright(as_m(built, look))
    lo, hi = up["pos"].min(axis=0), up["pos"].max(axis=0)
    c = (lo + hi) / 2
    span_x = np.abs(up["pos"][:, [0, 2]] - c[[0, 2]]).sum(axis=1).max() * 2
    sc = min((size[1] - 24) / ((hi[1] - lo[1]) * 16 * 1.06), (size[0] - 10) / (span_x * 16))
    return MR.render(up, built.texture(), yaw, pitch, scale=sc, size=size, centre=c, time=0.27, bg=np.array(bg))


def directions(out):
    """One Longsword three ways, beside today's: thinner boxes, ground, flat."""
    row = ROWS["gatewrought_longsword"]
    base = AW.gatewrought

    def variant(blade_fn):
        def design():
            saved = AW.blade
            AW.blade = blade_fn
            try:
                return base()
            finally:
                AW.blade = saved
        return design

    def thin(segs, core, edge, **kw):
        kw.pop("core_d", None)
        return ORIG["blade"](segs, core, edge, core_d=2, **kw)

    def flat(segs, core, edge, **kw):
        kw.pop("core_d", None)
        return ORIG["blade"](segs, core, edge, core_d=1, **kw)

    shots = [("Today: a 3 x 3 core between thin edges", AW.make(row)),
             ("A. Thinner boxes: the core 2 deep", make2(row, variant(thin))),
             ("B. Ground: a flat, two bevels, a true point", make2(row, variant(ground))),
             ("C. Flat: one texel thick, like a vanilla sword", make2(row, variant(flat)))]
    W, H = 470, 760
    sheet = Image.new("RGB", (W * len(shots), H), (22, 22, 30))
    d = ImageDraw.Draw(sheet)
    for i, (name, (built, look)) in enumerate(shots):
        fp = fp_shot(row, built, look, (1280, 720)).crop((760, 60, 1230, 720)).resize((W, int(660 * W / 470)))
        sheet.paste(fp.crop((0, 0, W, 430)), (i * W, 26))
        sheet.paste(hall_shot(built, look, (200, 300)), (i * W + 10, 458))
        sheet.paste(hall_shot(built, look, (200, 300), yaw=78, pitch=8), (i * W + 240, 458))
        d.text((i * W + 8, 6), name, fill=(236, 232, 244))
    sheet.save(out)
    return out


# ---- the views ------------------------------------------------------------------------------------------------------------

def _font(size):
    from PIL import ImageFont
    for path in ("/System/Library/Fonts/Supplemental/Arial.ttf", "/System/Library/Fonts/Helvetica.ttc", "/Library/Fonts/Arial.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def backdrop(size, horizon=0.56):
    """A plain daylight scene behind a first-person render (sky over ground), so a blade is judged against the game's
    kind of light and not a grey card."""
    W, H = size
    y = np.linspace(0, 1, H)[:, None, None]
    sky = np.array([104, 132, 172]) + (np.array([168, 190, 214]) - np.array([104, 132, 172])) * np.clip(y / horizon, 0, 1)
    ground = np.array([92, 112, 78]) + (np.array([62, 78, 56]) - np.array([92, 112, 78])) * np.clip((y - horizon) / (1 - horizon), 0, 1)
    img = np.where(y < horizon, sky, ground)
    return np.broadcast_to(img, (H, W, 3)).astype(np.uint8).copy()


KEY = np.array([255, 0, 255])


def keyed(img, bg):
    """A render made over the key colour laid on a backdrop."""
    a = np.asarray(img).astype(int)
    hit = ~((a[..., 0] > 250) & (a[..., 1] < 5) & (a[..., 2] > 250))
    out = bg.copy()
    out[hit] = a[hit]
    return Image.fromarray(out.astype(np.uint8), "RGB"), hit


def fp_full(row, built, look, size=(1280, 720), part=None):
    m = as_m(built, look)
    fp = AW.row_hold(row, built.pos / 16.0)["firstperson_righthand"]
    hold = (tuple(fp["rotation"]), tuple(fp["translation"]), fp["scale"][0])
    img = MR.render_fp(m, built.texture(), display=hold, size=size, time=0.27, bg=KEY)
    return keyed(img, backdrop(size))


def fp_pair(row, b0, l0, b1, l1, out=(480, 360)):
    """First person, today and proposed, the same part of the screen in both."""
    (i0, h0), (i1, h1) = fp_full(row, b0, l0), fp_full(row, b1, l1)
    hit = h0 | h1
    H, W = hit.shape
    ys, xs = np.nonzero(hit)
    if not len(xs):
        box = (W // 2, 0, W, H)
    else:
        x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
        asp = out[0] / out[1]
        h = max(y1 - y0 + 60, H * 0.62)
        w = max(x1 - x0 + 60, h * asp)
        h = w / asp
        if h > H:
            h, w = H, H * asp
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        bx = min(max(cx - w / 2, 0), W - w)
        by = min(max(cy - h / 2, 0), H - h)
        box = (int(bx), int(by), int(bx + w), int(by + h))
    res = []
    for im in (i0, i1):
        d = ImageDraw.Draw(im)
        d.line((W / 2 - 9, H / 2, W / 2 + 9, H / 2), fill=(240, 240, 240), width=2)
        d.line((W / 2, H / 2 - 9, W / 2, H / 2 + 9), fill=(240, 240, 240), width=2)
        res.append(im.crop(box).resize(out, Image.LANCZOS))
    return res


def box_tris(boxes):
    P, N, C = [], [], []
    for lo, hi, ci in boxes:
        lo, hi = np.array(lo, dtype=float), np.array(hi, dtype=float)
        for ax in range(3):
            a, b = (ax + 1) % 3, (ax + 2) % 3
            for s in (1, -1):
                n = np.zeros(3)
                n[ax] = s
                ua, va = (a, b) if s > 0 else (b, a)
                c = lo.copy()
                c[ax] = hi[ax] if s > 0 else lo[ax]
                u, v = np.zeros(3), np.zeros(3)
                u[ua], v[va] = hi[ua] - lo[ua], hi[va] - lo[va]
                q = [c, c + u, c + u + v, c + v]
                for tri in ((0, 1, 2), (0, 2, 3)):
                    for k in tri:
                        P.append(q[k])
                        N.append(n)
                        C.append(ci)
    return np.array(P), np.array(N), np.array(C)


# a plain figure the player's size (the player model's boxes, 1/16 block, y down, before the renderer's flip)
FIGURE = [((-4, -8, -4), (4, 0, 4), 0), ((-4, 0, -2), (4, 12, 2), 1), ((-8, 0, -2), (-4, 12, 2), 2), ((4, 0, -2), (8, 12, 2), 2),
          ((-3.9, 12, -2), (0.1, 24, 2), 3), ((-0.1, 12, -2), (3.9, 24, 2), 3)]
FIG_RGB = [(196, 164, 140), (92, 116, 148), (176, 146, 124), (70, 74, 96)]


def third_shot(row, built, look, size=(300, 400), yaw=-112, pitch=8):
    """Third person, offline: the weapon where the game's hand layer puts it (ItemInHandLayer: the arm's frame, X -90,
    Y 180, (1, 2, -10)/16, then the item's own thirdperson transform) on a plain figure the player's size, arm at rest."""
    tp = AW.row_hold(row, built.pos / 16.0).get("thirdperson_righthand")
    rot, tr, sc = ((0, -90, 55), (0, 4, 0.5), 0.85) if tp is None else (tuple(tp["rotation"]), tuple(tp["translation"]), tp["scale"][0])
    Rd, Rh = MR.euler(*rot), MR.euler(-90, 180, 0)
    S = np.diag([-1.0, -1.0, 1.0])
    q = ((built.pos / 16.0 - 0.5) * sc) @ Rd.T + np.asarray(tr) / 16.0 + np.array([1, 2, -10]) / 16.0
    q = q @ Rh.T + np.array([-5, 2, 0]) / 16.0 + np.array([0, -1.501, 0])
    wpos = q @ S
    wnrm = built.nrm @ (S @ Rh @ Rd).T
    fP, fN, fC = box_tris(FIGURE)
    fpos = (fP / 16.0 + np.array([0, -1.501, 0])) @ S
    fnrm = fN @ S

    def flip(a):  # a mirror turns every triangle inside out: wind them back
        a = a.reshape(-1, 3, a.shape[-1]).copy()
        a[:, [1, 2]] = a[:, [2, 1]]
        return a.reshape(-1, a.shape[-1])

    tex = built.texture()
    H, W2 = tex.shape[:2]
    big = np.zeros((H + 2, W2, 4), dtype=np.uint8)
    big[:H] = tex
    for k, rgb in enumerate(FIG_RGB):
        big[H:, k * 2:k * 2 + 2, :3] = rgb
        big[H:, k * 2:k * 2 + 2, 3] = 255
    wuv = built.uv * np.array([1.0, H / (H + 2)])
    fuv = np.stack([(fC * 2 + 1.0) / W2, np.full(len(fC), (H + 1.0) / (H + 2))], axis=1)
    m = {"pos": np.concatenate([flip(fpos), flip(wpos)]), "nrm": np.concatenate([flip(fnrm), flip(wnrm)]),
         "uv": np.concatenate([flip(fuv), flip(wuv)]), "along": np.concatenate([np.zeros(len(fpos)), flip(built.along[:, None])[:, 0]]),
         "accent": AW.hex_rgb(look["accent"])}
    lo, hi = m["pos"].min(axis=0), m["pos"].max(axis=0)
    c = np.array([(lo[0] + hi[0]) / 2, (min(lo[1], 0) + max(hi[1], 2.0)) / 2, 0.0])
    sc_px = min((size[1] - 16) / ((max(hi[1], 2.0) - min(lo[1], 0)) * 16), (size[0] - 10) / ((hi[0] - lo[0] + 0.6) * 16), 13.0)
    img = MR.render(m, big, yaw, pitch, scale=sc_px, size=size, centre=c, time=0.27, bg=KEY)
    return img, c, sc_px


def third_pair(row, b0, l0, b1, l1, size=(250, 360)):
    out = []
    for b, l in ((b0, l0), (b1, l1)):
        img, _, _ = third_shot(row, b, l, size)
        out.append(keyed(img, backdrop(size, 0.8))[0])
    return out


def icon_big(built_clean, look, k=4):
    ic = AW.icon(built_clean, look)
    return ic, ic.resize((32 * k, 32 * k), Image.NEAREST)


def hand_parts(row, fn):
    """What the hands draw of a weapon: a Shieldblade's sword alone (the shield is the other arm's)."""
    return fn


def pair(row):
    """(today, proposed): each (built, look, built for the icon)."""
    b0, l0 = AW.make(row)
    c0, _ = AW.make(row, clean=True)
    with Ground():
        d = after_design(row)
        b1, l1 = make2(row, d)
        c1, _ = make2(row, d, clean=True)
    return (b0, l0, c0), (b1, l1, c1)


def hand_pair(row):
    """As pair, but what the main hand holds (the Shieldblade's sword)."""
    if row[8] != "shieldblade" or row[0] in AW.LORD_COLOUR:
        t, p = pair(row)
        return t[:2], p[:2]
    tier = row[2]
    b0 = build2(*_design(AW.shieldblade_blade(tier)))
    with Ground():
        b1 = build2(*_design(AFTER_SWORD(tier)))
    look = AW.shieldblade_blade(tier)[2]
    return (b0, look), (b1, look)


def _design(d):
    boxes, anchors, look = d
    return boxes, painter2(False), AW.to_item


def strip(row, out_dir):
    """One weapon's before and after at the four views, as one image and its parts."""
    (b0, l0, c0), (b1, l1, c1) = pair(row)
    (h0, hl0), (h1, hl1) = hand_pair(row)
    F = _font(15)
    Fs = _font(13)
    fp = fp_pair(row, h0, hl0, h1, hl1, (470, 350))
    tp = third_pair(row, h0, hl0, h1, hl1, (188, 330))
    hall = [hall_shot(b, l, (150, 330)) for b, l in ((b0, l0), (b1, l1))]
    side = [hall_shot(b, l, (78, 330), yaw=88, pitch=6) for b, l in ((b0, l0), (b1, l1))]
    ic = [icon_big(c, l, 2) for c, l in ((c0, l0), (c1, l1))]
    W = 960
    H = 24 + 350 + 28 + 330 + 8
    im = Image.new("RGB", (W, H), (17, 17, 23))
    d = ImageDraw.Draw(im)
    lab, gold = (150, 152, 166), (236, 214, 150)
    d.text((8, 4), "First person: today", fill=lab, font=Fs)
    d.text((488, 4), "First person: proposed", fill=gold, font=Fs)
    im.paste(fp[0], (5, 24))
    im.paste(fp[1], (485, 24))
    y = 24 + 350 + 6
    d.text((8, y), "Third person: today", fill=lab, font=Fs)
    d.text((200, y), "proposed", fill=gold, font=Fs)
    d.text((396, y), "Hall of Arms: today", fill=lab, font=Fs)
    d.text((630, y), "proposed", fill=gold, font=Fs)
    d.text((868, y), "Icon", fill=lab, font=Fs)
    y += 22
    im.paste(tp[0], (5, y))
    im.paste(tp[1], (197, y))
    x = 394
    for k in (0, 1):
        im.paste(hall[k], (x, y))
        im.paste(side[k], (x + 150, y))
        x += 232
    icx = 872
    d.text((icx, y + 6), "today", fill=lab, font=Fs)
    im.paste(ic[0][1], (icx, y + 26), ic[0][1])
    im.paste(ic[0][0], (icx + 16, y + 96), ic[0][0])
    d.text((icx, y + 150), "proposed", fill=gold, font=Fs)
    im.paste(ic[1][1], (icx, y + 170), ic[1][1])
    im.paste(ic[1][0], (icx + 16, y + 240), ic[1][0])
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, row[0] + ".jpg")
    im.save(path, quality=88)
    return path


def review(style, out, tiers="EDCBAS"):
    """A working sheet: one line per weapon of a style, first person and the Hall view, today beside proposed."""
    rows = [r for r in AW.CATALOG if (r[0] in AW.LORD_COLOUR) == (style == "lordforged") and (style == "lordforged" or r[8] == style)
            and (style == "lordforged" or r[2] in tiers)]
    if style == "lordforged" and tiers != "EDCBAS":
        rows = [r for r in rows if r[0] in tiers.split(",")]
    cw, ch = 300, 250
    sheet = Image.new("RGB", (cw * 2 + 150 * 4 + 140, ch * len(rows)), (17, 17, 23))
    d = ImageDraw.Draw(sheet)
    for k, row in enumerate(rows):
        (b0, l0, c0), (b1, l1, c1) = pair(row)
        (h0, hl0), (h1, hl1) = hand_pair(row)
        fp = fp_pair(row, h0, hl0, h1, hl1, (cw, ch))
        y = k * ch
        sheet.paste(fp[0], (0, y))
        sheet.paste(fp[1], (cw, y))
        x = cw * 2
        for b, l in ((b0, l0), (b1, l1)):
            sheet.paste(hall_shot(b, l, (150, ch)), (x, y))
            sheet.paste(hall_shot(b, l, (150, ch), yaw=80, pitch=8), (x + 150, y))
            x += 300
        for j, (c, l) in enumerate(((c0, l0), (c1, l1))):
            icn = icon_big(c, l, 3)[1]
            sheet.paste(icn, (x + 20, y + 10 + j * 120), icn)
        d.text((6, y + 4), f"{row[1]} ({row[2]})  {len(b0.pos) // 3} -> {len(b1.pos) // 3} triangles", fill=(240, 236, 220), font=_font(13))
    sheet.save(out)
    return out


def vanilla_sword():
    """Vanilla's diamond sword as the game extrudes it (a texel thick), for the same first-person projection."""
    im = np.asarray(Image.open("/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/textures/item/diamond_sword.png").convert("RGBA"))
    boxes = [((x, 15 - y, 7.5), (x + 1, 16 - y, 8.5), 0) for y in range(16) for x in range(16) if im[y, x, 3] > 10]
    P, N, C = box_tris(boxes)
    tex = np.zeros((2, 4, 4), dtype=np.uint8)
    tex[:, :2] = (80, 220, 200, 255)
    return {"pos": P / 16.0, "nrm": N, "uv": np.tile([[0.2, 0.5]], (len(P), 1)), "along": np.zeros(len(P)), "accent": (0, 0, 0)}, tex


def screen_width(hit):
    """How thick a held weapon reads on screen: its silhouette's width across its own length, at five places between
    0.45 and 0.75 of the way up what shows (pixels of a 1280 x 720 frame, the median)."""
    ys, xs = np.nonzero(hit)
    if len(xs) < 50:
        return 0.0
    P = np.stack([xs, ys], axis=1).astype(float)
    c = P.mean(axis=0)
    w, v = np.linalg.eigh(np.cov((P - c).T))
    a = v[:, 1]
    if a[1] > 0:
        a = -a  # toward the top of the screen: the point
    t = (P - c) @ a
    sdir = np.array([-a[1], a[0]])
    sv = (P - c) @ sdir
    t0, t1 = t.min(), t.max()
    out = []
    for f in (0.45, 0.52, 0.6, 0.68, 0.75):
        sel = np.abs(t - (t0 + f * (t1 - t0))) < 2.5
        if sel.any():
            out.append(sv[sel].max() - sv[sel].min())
    return float(np.median(out)) if out else 0.0


def measure(ids=None):
    m, tex = vanilla_sword()
    img = MR.render_fp(m, tex, display=MR.HANDHELD_FP, size=(1280, 720), bg=KEY)
    a = np.asarray(img).astype(int)
    van = screen_width(~((a[..., 0] > 250) & (a[..., 1] < 5) & (a[..., 2] > 250)))
    print(f"vanilla diamond sword: {van:.0f} px across at 1280x720")
    for row in AW.CATALOG:
        if ids and row[0] not in ids:
            continue
        (h0, l0), (h1, l1) = hand_pair(row)
        w0 = screen_width(fp_full(row, h0, l0)[1])
        w1 = screen_width(fp_full(row, h1, l1)[1])
        print(f"{row[0]:28s} today {w0:5.0f} px   proposed {w1:5.0f} px   ({(w1 / w0 - 1) * 100 if w0 else 0:+.0f}%)   triangles {len(h0.pos) // 3} -> {len(h1.pos) // 3}")


UNCHANGED = ("maul", "warstaff", "precursor", "gauntlets")


def changed(row):
    return row[8] not in UNCHANGED and row[0] != "beastbound_rapier"


def _streams(built, look):
    built.streams = [(kind, AW.hex_rgb(rgb), rate, radius * AW.SCALE, [tuple(q) for q in AW.to_item(np.array(pts))])
                     for kind, rgb, rate, radius, pts in look.get("streams", [])]
    return built


def draft_write():
    """Writes the proposed meshes, textures and icons over the game's (ONLY on the throwaway draft branch, for a CI look
    tour of the real game; never on a branch that merges). Holds, recipes, names and code are left alone."""
    mesh_dir = os.path.join(AW.RES, "assets/awaken/arms/weapons")
    tex_dir = os.path.join(AW.RES, "assets/awaken/textures/item/arms")
    n = 0
    for row in AW.CATALOG:
        if not changed(row):
            continue
        wid = row[0]
        with Ground():
            d = after_design(row)
            built, look = make2(row, d)
            clean, _ = make2(row, d, clean=True)
            parts = {}
            if row[8] == "shieldblade":
                boxes, anchors, plook = AW.mirror_knight_blade() if wid in AW.LORD_COLOUR else sword2(row[2])
                item_anchors = [(tuple(AW.to_item(np.array([c]))[0]), r * AW.SCALE, AW.hex_rgb(rgb), kind) for c, r, rgb, kind in anchors]
                parts["_blade"] = (_streams(build2(boxes, painter2(False), AW.to_item, anchors=item_anchors), plook), plook)
        _streams(built, look)
        MS.write(os.path.join(mesh_dir, wid + ".mesh"), built, AW.hex_rgb(look["accent"]))
        Image.fromarray(built.texture(), "RGBA").save(os.path.join(tex_dir, wid + ".png"))
        AW.icon(clean, look).save(os.path.join(tex_dir, wid + "_icon.png"))
        for suffix, (part, plook) in parts.items():
            MS.write(os.path.join(mesh_dir, wid + suffix + ".mesh"), part, AW.hex_rgb(plook["accent"]))
            Image.fromarray(part.texture(), "RGBA").save(os.path.join(tex_dir, wid + suffix + ".png"))
        n += 1
        print(wid, len(built.pos) // 3, "triangles", flush=True)
    print(n, "weapons written (draft)")


def main(argv):
    cmd = argv[0] if argv else ""
    if cmd == "review":
        print(review(argv[1], argv[2], argv[3] if len(argv) > 3 else "EDCBAS"))
        return 0
    if cmd == "draft-write":
        if "draft" not in os.popen("git -C " + AW.ROOT + " branch --show-current").read():
            raise SystemExit("draft-write only on the throwaway draft branch")
        draft_write()
        return 0
    if cmd == "measure":
        measure(argv[1:] or None)
        return 0
    if cmd == "strips":
        ids = argv[2:] or [r[0] for r in AW.CATALOG]
        for wid in ids:
            print(strip(ROWS[wid], argv[1]))
        return 0
    if cmd == "directions":
        print(directions(argv[1]))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
