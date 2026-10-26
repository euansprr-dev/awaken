"""The Carillon commons kit's art (M4 Commons, docs/carillon/briefs/M4-commons-sonnet-1.md): the milestone, the fingerpost, the
scarecrow and the wheat stook, for the countryside outside the wall. Textures, models, blockstates, lang and mineable tags, in the
same register as the street and wall kits (city_street.py, city_wall.py): 16x16 per face, crisp, 4-6 tone ramps, light from the
top-left, colours anchored on real vanilla textures (hay_block, wheat_stage7, spruce/oak planks, cobblestone).

    python3 -I tools/art/build_all.py city_commons                textures
    python3 -I tools/art/specs/city_commons.py content            blockstates, models, lang, mineable tags
    python3 -I tools/art/specs/city_commons.py check              every blockstate key covers the Java properties; models, textures resolve
    python3 -I tools/art/specs/city_commons.py sheet <out.png> [prefix...]   contact sheet of every new texture at 4x
    python3 -I tools/art/specs/city_commons.py mock <out.png> [scene...]     assembled models, rendered from the real blockstate/model data
    python3 -I tools/art/specs/city_commons.py view <out.png> <block> [k=v ...]   one block (all states of one property with k=*)

Everything is deterministic (hash noise seeded per block, rng_for("city_commons", name)). Models are drawn for facing=north (the
road / the front on the north face) and rotated by the blockstate (rot_variant).

How the art is made. The four things are small and solid, so most of their faces are not painted one by one: each figure is a list
of axis-aligned parts (a box, a painter) and a baker draws what you would see of the whole figure from each side (an "elevation",
the frontmost part at every texel) into one 16-wide texture per side, in the position-natural mapping a vanilla cube uses. A part's
face then samples its own place in that elevation, so wear, moss, a patch or a stitched face runs on across every tier and across
the seam of a two-block piece. Rotated things (the arrow tips, the hat, the dangling legs, the straw, every sheaf of the stook) are
ordinary boxes painted into the automatic atlas (Atlas / MB of the street kit) and placed by a small rigid-transform helper that
composes several rotations into the one rotation 26.3 allows an element (single axis, or the Euler x/y/z form: verified against
JOML's rotationZYX, vanilla's own hanging-sign models use it).
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import Canvas, rng_for  # noqa: E402
import city_kit as ck  # noqa: E402
from city_kit import R, rgb, lerp, mul, step, clamp8, rot_variant, weighted, YROT, CUT, b, HEMP  # noqa: E402,F401
import city_street as cs  # noqa: E402  (the model builder, the renderer and the shared ramps are the street kit's)
from city_street import (World, MB, E, View, finish, fnum, FACE_NAMES, FNORM, LETTER, SHADE, _corners, _rotp, _rotv,  # noqa: E402,F401
                         render, GILT, BURLAP, FORGE, STEEL6, OAK, DARKOAK, WEATHERED, cloth_ramp_fine)
import city_wall as cw  # noqa: E402  (the wall's weathered limestone and moss: the milestone is cut from the same stone)

ROOT = ck.ROOT
ASSETS = ck.ASSETS
TEX = ck.TEX
MERGE = ck.MERGE
VANILLA = ck.VANILLA
PARENT = "minecraft:block/block"
SEED = "city_commons"
FACINGS = ["north", "east", "south", "west"]

# ---------------------------------------------------------------------------------------------------------------------
# palettes (dark -> light). Anchors: hay_block_side (#ab9225 #bfab31 #cbb630), wheat_stage7 (#a69553 #cdb159 #dcbb65 #8a8636 #75772f),
# oak_log (109,85,50), spruce_planks (114,84,48), calcite and the wall's limestone, mossy_cobblestone's greens.

LIME = cw.LIME                      # (74,74,71) .. (201,200,188): the wall kit's weathered limestone
MOSS = cw.MOSS
LICHEN = R("#6f7c3e", "#8d9a52", "#a8b465", "#c1c981", "#d6dba0")        # pale yellow-green crusts
OAKW = R("#352c20", "#4d4132", "#675844", "#85735a", "#9e8b70", "#b5a387")     # weathered grey-brown oak
CREAMP = R("#6e6450", "#8f8468", "#b0a587", "#c9be9f", "#ddd3b6", "#ece4cc")   # faded cream board paint
BLUEP = R("#15213a", "#213354", "#2e466d", "#3c5887", "#4f6f9f", "#6a8bb8")    # faded blue board paint
FLAME = R("#5a2a14", "#8a3f1a", "#b85a22", "#d97a2c", "#eaa23c", "#f4c860")    # faded orange paint
IRONP = R("#2c3036", "#454a52", "#5f656e", "#7a808a", "#9aa0aa")                # dull grey paint (the picks)
WHEAT = R("#4a5410", "#5b6b0f", "#75772f", "#8a8636", "#a69553", "#cdb159", "#dcbb65", "#ecd488")   # wheat_stage7 + shadows
HAYV = R("#6e5c18", "#7f6a1a", "#8a7320", "#94801e", "#ab9225", "#bfab31", "#cbb630")                # hay_block_side
BROWN_C = R("#2a1d12", "#43301f", "#5c442b", "#755a3a", "#8d7048", "#a58a5e")   # the coat's brown wool
GREEN_C = R("#1e2a1c", "#2f4129", "#445a38", "#5c7549", "#7a9462", "#98ae7e")   # the coat's faded green patches
DRAB = R("#241f17", "#3a3326", "#51483a", "#665b49", "#7d705b", "#968873")      # trouser canvas
STRAWP = R("#5c4a1c", "#80672a", "#a28538", "#c0a24a", "#d8bd68", "#ecd88e")    # pale dry straw (the scarecrow's)
THREAD = rgb("#2e2216")
STITCH = rgb("#b79f6c")                                            # pale thread: patches are sewn on with light stitches


def fine(rp):
    """A ramp with a step halfway between every two: the same range in twice the steps, so one step of noise is a grain, not a blotch."""
    out = []
    for i, c in enumerate(rp):
        out.append(c)
        if i + 1 < len(rp):
            out.append(lerp(c, rp[i + 1], 0.5))
    return out


LIMEF = fine(LIME)
OAKF = fine(OAKW)


# ---------------------------------------------------------------------------------------------------------------------
# deterministic noise: hr is a hash in [0,1) of a seed and integer coordinates, vn a smooth 3D value noise of it

def hr(seed, *c):
    h = (seed * 0x9E3779B1 + 0x7F4A7C15) & 0xFFFFFFFF
    for v in c:
        h = ((h ^ (int(v) & 0xFFFFFFFF)) * 0x85EBCA6B) & 0xFFFFFFFF
        h ^= h >> 13
        h = (h * 0xC2B2AE35) & 0xFFFFFFFF
        h ^= h >> 16
    return (h & 0xFFFFFF) / 16777216.0


def vn(seed, x, y, z, s):
    gx, gy, gz = x / s, y / s, z / s
    x0, y0, z0 = math.floor(gx), math.floor(gy), math.floor(gz)
    fx, fy, fz = [t * t * (3 - 2 * t) for t in (gx - x0, gy - y0, gz - z0)]
    out = 0.0
    for dz in (0, 1):
        for dy in (0, 1):
            for dx in (0, 1):
                w = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                out += w * hr(seed, x0 + dx, y0 + dy, z0 + dz)
    return out


def seedn(name):
    return int(rng_for(SEED, name).random() * 1e9)


# ---------------------------------------------------------------------------------------------------------------------
# geometry helpers

def natural_uv(d, a, b_):
    """Position-natural UV (what a vanilla full cube has) of the rectangle a..b on the face of direction d."""
    x0, y0, z0 = a
    x1, y1, z1 = b_
    uv = {"N": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "S": [x0, 16 - y1, x1, 16 - y0],
          "E": [16 - z1, 16 - y1, 16 - z0, 16 - y0], "W": [z0, 16 - y1, z1, 16 - y0],
          "U": [x0, z0, x1, z1], "D": [x0, 16 - z1, x1, 16 - z0]}[d]
    return [fnum(min(16, max(0, v))) for v in uv]


def mrx(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def mry(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def mrz(d):
    c, s = math.cos(math.radians(d)), math.sin(math.radians(d))
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def mmul(A, B):
    return [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mvec(M, v):
    return [sum(M[i][k] * v[k] for k in range(3)) for i in range(3)]


def mtr(M):
    return [[M[j][i] for j in range(3)] for i in range(3)]


def euler_of(M):
    """x, y, z (degrees) with M = Rz(z) Ry(y) Rx(x): the order of 26.3's {"x","y","z"} element rotation (JOML rotationZYX)."""
    sy = max(-1.0, min(1.0, -M[2][0]))
    y = math.asin(sy)
    if abs(math.cos(y)) > 1e-5:
        x = math.atan2(M[2][1], M[2][2])
        z = math.atan2(M[1][0], M[0][0])
    else:
        x = math.atan2(-M[1][2], M[1][1])
        z = 0.0
    return tuple(round(math.degrees(v), 3) for v in (x, y, z))


ROT = {"x": mrx, "y": mry, "z": mrz}


def rigid(a, b_, faces, ops=(), shade=True, override=None):
    """An element for the box a..b with the rotations ops applied in order, each (axis, degrees, pivot): the sequence is composed
    into one rotation (about the box's centre) plus a shift of the box, and written as a single-axis rotation when it is one."""
    M = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]
    t = [0.0, 0.0, 0.0]
    for ax, deg, pv in ops:
        if not deg:
            continue
        Rm = ROT[ax](deg)
        Rp = mvec(Rm, pv)
        t = [mvec(Rm, t)[i] + pv[i] - Rp[i] for i in range(3)]
        M = mmul(Rm, M)
    ident = all(abs(M[i][j] - (1 if i == j else 0)) < 1e-9 for i in range(3) for j in range(3))
    if ident:
        return E(a, b_, faces, shade=shade, override=override)
    O = [(a[i] + b_[i]) / 2 for i in range(3)]
    d = [O[i] + mvec(mtr(M), [t[k] - O[k] for k in range(3)])[i] for i in range(3)]
    a2 = [a[i] + d[i] for i in range(3)]
    b2 = [b_[i] + d[i] for i in range(3)]
    el = E(a2, b2, faces, shade=shade, override=override)
    axes = {ax for ax, deg, pv in ops if deg}
    if len(axes) == 1:
        ax = next(iter(axes))
        el["rotation"] = {"origin": [fnum(v) for v in O], "axis": ax, "angle": fnum(round(sum(dg for a_, dg, p in ops if a_ == ax), 3))}
    else:
        ex, ey, ez = euler_of(M)
        el["rotation"] = {"origin": [fnum(v) for v in O], "x": fnum(ex), "y": fnum(ey), "z": fnum(ez)}
    return el


# ---------------------------------------------------------------------------------------------------------------------
# the world: the street kit's, plus a texture registry that reuses a texture another side already drew (identical pixels)

class CommonsWorld(World):
    def named(self, name, c):
        """Register a canvas under name, or return the name of an identical canvas already registered."""
        for n, t in self.tex.items():
            if not isinstance(t, list) and t.px == c.px:
                return n
        self.texture(name, c)
        return name


GENERATORS = []


def gen(fn):
    GENERATORS.append(fn)
    return fn


# ---------------------------------------------------------------------------------------------------------------------
# the baker. A Part is a box and a painter; a Fig draws, for each side, what is seen of all its parts (the frontmost part at every
# texel, the first-listed part winning a tie of depth) into one 16-wide texture in the position-natural mapping, and builds the
# elements whose faces sample it. A painter is fn(ctx) -> (r,g,b,a) or None (a hole); ctx carries the sample point in block px
# (x, y, z: the texel's centre, the third coordinate the face's plane), the texel's cell (cx, cy, cz), the texel's place in the
# part's own face rectangle as the viewer outside sees it (i from the left, j from the top, of w x h) and a hash (rnd).

class Ctx:
    __slots__ = ("face", "x", "y", "z", "cx", "cy", "cz", "i", "j", "w", "h", "seed", "part")

    def rnd(self, tag=0):
        return hr(self.seed, tag, self.cx, self.cy, self.cz, ord(self.face))


class Part:
    def __init__(self, name, a, b_, paint, hide="", dark="", block=0, seed=None):
        self.name, self.a, self.b, self.paint = name, tuple(a), tuple(b_), paint
        self.hide, self.dark, self.block = hide, dark, block
        self.seed = seed if seed is not None else seedn(name)

    def rect(self, view):
        """(a0, a1, v0, v1): the part's extent in whole texels along the view's two axes."""
        ax, ay, az = self.a
        bx, by, bz = self.b
        fl = lambda v: int(math.floor(v + 1e-6))      # noqa: E731
        ce = lambda v: int(math.ceil(v - 1e-6))       # noqa: E731
        if view in "NS":
            return fl(ax), ce(bx), fl(ay), ce(by)
        if view in "EW":
            return fl(az), ce(bz), fl(ay), ce(by)
        return fl(ax), ce(bx), fl(az), ce(bz)

    def depth(self, view):
        """Smaller is nearer to the viewer."""
        return {"N": self.a[2], "S": -self.b[2], "E": -self.b[0], "W": self.a[0], "U": -self.b[1]}[view]

    def covers(self, view, a, v):
        """Does the part overlap the texel (a, v) of the view (positive area)?"""
        (p0, p1), (q0, q1) = self.span(view)
        return p0 < a + 1 - 1e-6 and p1 > a + 1e-6 and q0 < v + 1 - 1e-6 and q1 > v + 1e-6

    def span(self, view):
        if view in "NS":
            return (self.a[0], self.b[0]), (self.a[1], self.b[1])
        if view in "EW":
            return (self.a[2], self.b[2]), (self.a[1], self.b[1])
        return (self.a[0], self.b[0]), (self.a[2], self.b[2])


class Fig:
    def __init__(self, name, parts, blocks=1, under=None, alias=None, under_key=None):
        self.name, self.parts, self.blocks = name, parts, blocks
        self.under_key = under_key or name
        self.H = 16 * blocks
        self.under = under            # the painter of the underside swatch (also the reveals marked dark)
        self.alias = alias or {}      # view -> view whose texture it reuses (a symmetric thing)

    def _front(self, view, a, v):
        best, bd = None, None
        for p in self.parts:
            if view in p.hide or view in p.dark:
                continue
            if not p.covers(view, a, v):
                continue
            d = p.depth(view)
            if bd is None or d <= bd + 1e-9:
                best, bd = p, d
        return best

    def bake(self, view):
        """The canvas (16 wide; H tall for the four sides, 16 for the top) of one side."""
        H = 16 if view == "U" else self.H
        c = Canvas(16, H)
        for row in range(H):
            for col in range(16):
                if view in "NS":
                    a = (15 - col) if view == "N" else col
                    v = H - 1 - row
                elif view in "EW":
                    a = (15 - col) if view == "E" else col
                    v = H - 1 - row
                else:
                    a, v = col, row
                p = self._front(view, a, v)
                if p is None:
                    continue
                a0, a1, v0, v1 = p.rect(view)
                ctx = Ctx()
                ctx.face, ctx.seed, ctx.part = view, p.seed, p
                (pa0, pa1), (pv0, pv1) = p.span(view)
                ac = min(max(a + 0.5, pa0), pa1)
                vc = min(max(v + 0.5, pv0), pv1)
                if view in "NS":
                    ctx.x, ctx.y = ac, vc
                    ctx.z = p.a[2] if view == "N" else p.b[2]
                    ctx.cx, ctx.cy, ctx.cz = a, v, int(ctx.z)
                    ctx.i = (a1 - 1 - a) if view == "N" else (a - a0)
                    ctx.j = v1 - 1 - v
                elif view in "EW":
                    ctx.z, ctx.y = ac, vc
                    ctx.x = p.b[0] if view == "E" else p.a[0]
                    ctx.cx, ctx.cy, ctx.cz = int(ctx.x), v, a
                    ctx.i = (a1 - 1 - a) if view == "E" else (a - a0)
                    ctx.j = v1 - 1 - v
                else:
                    ctx.x, ctx.z = ac, vc
                    ctx.y = p.b[1]
                    ctx.cx, ctx.cy, ctx.cz = a, int(ctx.y), v
                    ctx.i = a - a0
                    ctx.j = v - v0
                ctx.w, ctx.h = a1 - a0, v1 - v0
                px = p.paint(ctx)
                if px is not None:
                    c.set(col, row, px if len(px) == 4 else (*px, 255))
        return c

    def textures(self, w, prefix, names=None):
        """Bake and register every side; returns {view: [texture name per block]} (the top view serves every block)."""
        out = {}
        for view in sorted("NESWU", key=lambda v_: v_ in self.alias):
            src = self.alias.get(view, view)
            if src != view:
                out[view] = out[src]
                continue
            c = self.bake(view)
            if view == "U" or self.blocks == 1:
                out[view] = [w.named(f"{prefix}_{view.lower()}", c)] * self.blocks
                continue
            ns = []
            for k in range(self.blocks):
                part = Canvas(16, 16)
                top = self.H - 16 * (k + 1)
                for r in range(16):
                    for q in range(16):
                        part.px[r][q] = c.px[top + r][q]
                ns.append(w.named(f"{prefix}_{(names or ['lower', 'upper', 'top'])[k]}_{view.lower()}", part))
            out[view] = ns
        return out

    def bake_under(self):
        """The underside swatch (16x16): the painter self.under drawn as a bottom view, for the faces that never see the light."""
        c = Canvas(16, 16)
        for row in range(16):
            for col in range(16):
                ctx = Ctx()
                ctx.face, ctx.seed, ctx.part = "D", seedn(self.under_key + "_under"), None
                ctx.x, ctx.y, ctx.z = col + 0.5, 0.0, 15 - row + 0.5
                ctx.cx, ctx.cy, ctx.cz = col, 0, 15 - row
                ctx.i, ctx.j, ctx.w, ctx.h = col, row, 16, 16
                px = self.under(ctx)
                c.set(col, row, px if len(px) == 4 else (*px, 255))
        return c

    def elements(self, m, tex, block, under_var, ops=()):
        """The block's parts as elements of the MB m: every face samples its place in the baked texture (the underside and the
        faces marked dark sample the underside swatch instead)."""
        els = []
        off = 16 * block
        for p in self.parts:
            if p.block != block:
                continue
            a = (p.a[0], p.a[1] - off, p.a[2])
            b_ = (p.b[0], p.b[1] - off, p.b[2])
            faces = {}
            for L in "NESWUD":
                if L in p.hide:
                    continue
                if L == "D" or L in p.dark:
                    faces[L] = (under_var, natural_uv(L, a, b_))
                else:
                    faces[L] = (m.var(tex[L][block]), natural_uv(L, a, b_))
            els.append(rigid(a, b_, faces, ops) if ops else E(a, b_, faces))
        return els


def alloc_face(m, mat, wpx, hpx, face, dims, key=None):
    """Paint one face of a model into the atlas with a material (the street kit's Atlas / MB do this for a whole box; this is
    the same for one face, so every face of a box can be drawn on its own). Returns (texture variable, uv)."""
    ck_ = (key, face, round(wpx, 2), round(hpx, 2)) if key else None
    if ck_ and ck_ in m.atlas.cache:
        tname, ux, uy = m.atlas.cache[ck_]
        wi = min(16, int(math.ceil(wpx - 1e-6)))
        return m.var(tname), [ux, uy, ux + min(wpx, 16), uy + min(hpx, 16)]
    rng = rng_for(SEED, m.name, m.n)
    m.n += 1
    tname, canvas, ux, uy, wi, hi = m.atlas.alloc(wpx, hpx)
    mat(View(canvas, ux, uy, wi, hi), 0, 0, wi, hi, face, rng, dims)
    if ck_:
        m.atlas.cache[ck_] = (tname, ux, uy)
    return m.var(tname), [ux, uy, ux + min(wpx, 16), uy + min(hpx, 16)]


def box_faces(m, a, b_, mat, faces="NESWUD", key=None, mats=None):
    """The faces dict of the box a..b, every face painted on its own by mat (or mats[face])."""
    dx, dy, dz = b_[0] - a[0], b_[1] - a[1], b_[2] - a[2]
    sizes = {"N": (dx, dy), "S": (dx, dy), "E": (dz, dy), "W": (dz, dy), "U": (dx, dz), "D": (dx, dz)}
    out = {}
    for f in faces:
        wpx, hpx = sizes[f]
        mt = (mats or {}).get(f, mat)
        out[f] = alloc_face(m, mt, wpx, hpx, f, (dx, dy, dz), key=(f"{key}_{f}" if key else None))
    return out


# @@ GENERATORS BEGIN @@
# ---------------------------------------------------------------------------------------------------------------------
# 1. milestone. A squat roadside post of weathered limestone cut from the wall's own stone: a foot that flares half a pixel (2 px),
#    an 8x8 shaft to y 10 with a sunk panel on its north face (0.75 px deep, 6 px wide, a one pixel jamb each side, a sill) and the
#    city's bell cut into the panel's floor, a pale collar under the head (y 10-11, proud a quarter pixel), and a head of two
#    rounded steps to y 13. Lichen and moss low on every side, a darker damp foot.

DEEPL = R("#2a2a27", "#363633", "#42423e")                         # the cut of the bell: deeper than the ramp goes
BELL_M = ["......",
          "..##..",
          ".####.",
          ".####.",
          ".####.",
          "######",
          "..##.."]                                                  # 6 wide, 7 tall: the panel's opening


def stone_paint(seed, tone=3, moss=0.0, moss_h=4.0, lichen=0.0, damp_h=0.0, strata=True, rim=True, streak_top=None):
    """Weathered limestone: soft mottling, grain, a faint bedding line, a lit top-left rim; moss and lichen low, a damp foot."""
    def paint(c):
        x, y, z = c.x, c.y, c.z
        n = vn(seed, x, y, z, 2.8)
        k = 2 * tone + (1 if n > 0.62 else -1 if n < 0.34 else 0)
        r = c.rnd(1)
        if r < 0.20:
            k += 1
        elif r < 0.40:
            k -= 1
        if strata and c.cy % 4 == 1 and c.rnd(2) < 0.45:
            k -= 2
        if streak_top is not None and c.face in "NESW":                  # damp streaks running down from the collar
            cu = c.cx if c.face in "NS" else c.cz
            if hr(seed + 31, ord(c.face), cu) < 0.32:
                ln = 2 + int(hr(seed + 32, ord(c.face), cu) * 4)
                if streak_top - ln <= y < streak_top:
                    k -= 2
        if rim and c.w > 2 and c.h > 2:
            if c.i == 0 or c.j == 0:
                k += 2
            elif c.i == c.w - 1 or c.j == c.h - 1:
                k -= 2
        col = step(LIMEF, k)
        if damp_h and y < damp_h:
            f = (damp_h - y) / damp_h
            col = lerp(mul(col, 0.86 - 0.12 * f), (50, 66, 60), 0.2 * f)
        if moss:
            wgt = max(0.0, (moss_h - y) / moss_h)
            if vn(seed + 11, x, y, z, 1.8) > 0.55 and c.rnd(3) < moss * (0.25 + 0.9 * wgt):
                col = step(MOSS, 1 + int(c.rnd(5) * 3))
        if lichen:
            if vn(seed + 23, x, y, z, 1.5) > 0.72 and c.rnd(4) < lichen * 8:
                col = lerp(col, step(LICHEN, 1 + int(c.rnd(6) * 3)), 0.8)
        return col
    return paint


def stone_under(c):
    """The dark of a reveal or an underside: limestone in shade, damp speckles."""
    k = 3 + (1 if c.rnd(1) < 0.25 else 0) - (1 if c.rnd(2) < 0.25 else 0)
    col = step(LIMEF, k)
    if c.rnd(3) < 0.1:
        col = lerp(col, (44, 62, 52), 0.5)
    return mul(col, 0.8)


def panel_paint(seed):
    """The core's north face, 8 x 8 texels as the viewer outside sees them: the jambs are columns 0 and 7 and the sill row 7 (hidden
    behind the frame); the opening is columns 1..6, rows 0..6. The floor is mid limestone, shadowed under the collar and beside the
    left reveal (light from the top-left); the bell is cut deeper: dark, its top-left edge deepest, its lower-right edge catching
    light."""
    bell = {(i + 1, j) for j, row in enumerate(BELL_M) for i, ch in enumerate(row) if ch == "#"}

    def mat(c, x, y, w, h, face, rng, dims):
        for j in range(8):
            for i in range(8):
                x_, y_ = 11 - i, 9 - j                                 # the block cell this texel sits at
                n = vn(seed, x_ + 0.5, y_ + 0.5, 4.75, 2.6)
                k = 6 + (1 if n > 0.64 else -1 if n < 0.32 else 0)
                r = rng.random()
                if r < 0.18:
                    k += 1
                elif r < 0.36:
                    k -= 1
                col = step(LIMEF, k)
                if j == 0:                                              # shadow under the collar
                    col = mul(col, 0.74)
                elif j == 1:
                    col = mul(col, 0.9)
                if i == 1:                                              # shadow beside the left reveal
                    col = mul(col, 0.88)
                if (i, j) in bell:
                    up = (i, j - 1) not in bell
                    left = (i - 1, j) not in bell
                    down = (i, j + 1) not in bell
                    right = (i + 1, j) not in bell
                    base = 40 + rng.randrange(0, 7)
                    if up or left:
                        base = 26 + rng.randrange(0, 4)           # the cut's shadowed wall
                    elif down or right:
                        base = 64 + rng.randrange(0, 6)           # its lit wall
                    col = (base, base, base - 3, 255)
                elif j >= 5 and rng.random() < 0.10:                    # a little damp low in the panel
                    col = lerp(col, (44, 62, 52), 0.3)
                c.set(x + i, y + j, col)
    return mat


def milestone_fig(w):
    s_foot = stone_paint(seedn("ms_foot"), tone=2, moss=0.45, moss_h=3.0, lichen=0.06, damp_h=2.6)
    s_shaft = stone_paint(seedn("ms_shaft"), tone=3, moss=0.35, moss_h=4.5, lichen=0.06, damp_h=2.0, streak_top=10)
    s_collar = stone_paint(seedn("ms_collar"), tone=4, moss=0.0, lichen=0.04, strata=False)
    s_head = stone_paint(seedn("ms_head"), tone=3, moss=0.0, lichen=0.05, strata=False)
    parts = [
        Part("foot", (3.5, 0, 3.5), (12.5, 2, 12.5), s_foot, hide="D"),
        Part("core", (4, 2, 4.75), (12, 10, 12), s_shaft, hide="UD", dark=""),
        Part("jambL", (4, 2, 4), (5, 10, 4.75), s_shaft, hide="SUD", dark="E"),
        Part("jambR", (11, 2, 4), (12, 10, 4.75), s_shaft, hide="SUD", dark="W"),
        Part("sill", (5, 2, 4), (11, 3, 4.75), s_shaft, hide="SEWD", dark="U"),
        Part("collar", (3.75, 10, 3.75), (12.25, 11, 12.25), s_collar),
        Part("headA", (4.5, 11, 4.5), (11.5, 12, 11.5), s_head, hide="D"),
        Part("headB", (5.5, 12, 5.5), (10.5, 13, 10.5), s_head, hide="D"),
    ]
    return Fig("milestone", parts, under=stone_under)


def milestone_model(w):
    fig = milestone_fig(w)
    tex = fig.textures(w, "milestone")
    under = w.named("milestone_under", fig.bake_under())
    m = MB(w.atlas("milestone"), "milestone", ao=True)
    uv_ = m.var(under)
    els = fig.elements(m, {k: v for k, v in tex.items()}, 0, uv_)
    # the core's north face is the panel (explicit, drawn by hand)
    pv, puv = alloc_face(m, panel_paint(seedn("ms_panel")), 8, 8, "N", (8, 8, 7.25))
    for el in els:
        if el["from"] == [4, 2, 4.75] and el["to"] == [12, 10, 12]:
            el["faces"]["north"] = {"uv": [fnum(u) for u in puv], "texture": pv}
    for el in els:
        m.raw(el)
    return finish(w, m.build(ao=True))


@gen
def g_milestone(w):
    w.model("milestone", milestone_model(w))
    w.state("milestone", {f"facing={f}": rot_variant("milestone", f) for f in FACINGS})


# ---------------------------------------------------------------------------------------------------------------------
# 2. fingerpost. Two blocks of weathered grey-brown oak: a 4x4 post (x 6-10, z 6-10) with a pyramidal cap of three steps (y 13-16 of
#    the upper block, 6 px at the foot of the cap), mossy and damp at the foot. Two boards pass through the upper post, 2 px thick, 4 px
#    tall: the upper (y 7-11) points the way the block faces, painted cream with the destination's pictograph (a campfire, or crossed
#    picks), the lower (y 2-6) points back, painted blue with the city's bell in faded gold. Each ends in a pointed tip (a 45 degree
#    diamond cut into the end, bare wood showing end grain). The paint is faded and chipped. No text.

FIELD_UP = {"camp": CREAMP, "mine": CREAMP}
CAMP_S = ["..o.",
          ".oYo",
          "oYYo",
          "LlLL"]
MINE_S = ["G..G",
          ".GG.",
          ".HH.",
          "H..H"]
BELL_S = [".GG.",
          ".GG.",
          "GGGG",
          ".YY."]
SPRITES = {"camp": (CAMP_S, {"o": FLAME[3], "Y": FLAME[5], "L": OAKW[1], "l": OAKW[2]}),
           "mine": (MINE_S, {"G": IRONP[3], "H": OAKW[3]}),
           "bell": (BELL_S, {"G": GILT[4], "Y": GILT[5]})}


def post_paint(seed, foot_h=6.0):
    """A square oak post: grain running up, a few cracks, grey weathering flecks; moss and damp at the foot, a lit left edge."""
    def paint(c):
        face = c.face
        ca = hr(seed, ord(face), c.i)
        k = 6
        if ca < 0.34:
            k -= 1
        elif ca > 0.84:
            k += 1
        if c.w >= 3:
            if c.i == 0:
                k += 2
            elif c.i == c.w - 1:
                k -= 2
        cr = hr(seed + 5, ord(face), c.i)
        if cr < 0.55:
            y0 = int(hr(seed + 6, ord(face), c.i) * 22) + 3
            ln = 3 + int(hr(seed + 7, ord(face), c.i) * 6)
            if y0 <= c.cy < y0 + ln:
                k -= 3
        r = c.rnd(1)
        if r < 0.18:
            k += 1
        elif r < 0.34:
            k -= 1
        col = step(OAKF, k)
        if c.rnd(2) < 0.07:
            col = lerp(col, (132, 126, 112), 0.4)
        y = c.y
        if y < foot_h:
            f = (foot_h - y) / foot_h
            col = lerp(mul(col, 0.9 - 0.1 * f), (44, 56, 46), 0.18 * f)
            if c.rnd(3) < 0.12 + 0.55 * f * f:
                col = step(MOSS, 1 + int(c.rnd(5) * 3))
        elif c.rnd(4) < 0.03 and y < 12:
            col = lerp(col, step(LICHEN, 2), 0.8)
        return col
    return paint


def cap_paint(seed):
    """The cap's three steps: darker oak, shingled rows, moss on the tops."""
    def paint(c):
        k = 5
        if c.face != "U" and c.j == c.h - 1:
            k -= 2
        r = c.rnd(1)
        if r < 0.2:
            k += 1
        elif r < 0.4:
            k -= 1
        if c.face == "U":
            k += 1
            if vn(seed, c.x, c.y, c.z, 1.6) > 0.52 and c.rnd(3) < 0.55:
                return step(MOSS, 1 + int(c.rnd(5) * 3))
        return step(OAKF, k)
    return paint


def wood_under(c):
    k = 3 + (1 if c.rnd(1) < 0.3 else 0) - (1 if c.rnd(2) < 0.3 else 0)
    return mul(step(OAKF, k), 0.78)


def board_paint(kind, tip_side, sprite, field_ramp, seed):
    """A painted board: the visible 4 x 4 of each broad face is the faded field with the pictograph on it (chipped to bare wood here
    and there, lighter at the lit top edge); the narrow faces are bare wood (the top and bottom edges) and end grain (the ends)."""
    spr, leg = SPRITES[sprite]
    vis = (2, 5) if tip_side == "N" else (10, 13)           # the cells of z the arm shows outside the post

    def paint(c):
        f = c.face
        if f in "NS":
            return endgrain_px(c, seed)
        if f == "U" or f == "D":
            k = 7 - (2 if f == "D" else 0) + (1 if c.rnd(1) < 0.3 else 0) - (1 if c.rnd(2) < 0.3 else 0)
            return step(OAKF, k)
        z = c.cz
        if not (vis[0] <= z <= vis[1]):
            return step(field_ramp, 3)                       # inside the post: never seen
        s = (z - vis[0]) if f == "W" else (vis[1] - z)
        ch = spr[c.j][s] if 0 <= c.j < len(spr) and 0 <= s < len(spr[0]) else "."
        base = step(field_ramp, 3 + (1 if c.j == 0 else 0) - (1 if c.j == c.h - 1 else 0))
        base = lerp(base, step(field_ramp, 3), 0.0)
        r = c.rnd(5)
        if ch != "." and ch in leg:
            col = leg[ch]
            if r < 0.07:
                return base                                  # the pictograph's paint has flaked away
            return lerp(col, base, 0.12)
        if r < 0.10:                                         # chipped to the bare wood
            return step(OAKF, 4 + int(c.rnd(6) * 3))
        if r < 0.22:
            return step(field_ramp, 2 + (1 if c.rnd(7) < 0.5 else 0))
        if r > 0.93:
            return step(field_ramp, 4)
        return base
    return paint


def endgrain_px(c, seed):
    """End grain: a few rings round a heart, lighter than the faces."""
    cx_, cy_ = 0.4 + hr(seed, 1) * 0.6, 0.5 + hr(seed, 2) * 0.4
    d = math.hypot((c.i + 0.5 - c.w * cx_) * 0.9, (c.j + 0.5 - c.h * cy_))
    k = 7 + (-2 if int(d * 1.5) % 2 else 0)
    if c.rnd(3) < 0.2:
        k += 1
    return step(OAKF, k)


def fingerpost_fig(to):
    s_post = post_paint(seedn("fp_post"))
    s_cap = cap_paint(seedn("fp_cap"))
    spr_up = to
    parts = [
        Part("post_lo", (6, 0, 6), (10, 16, 10), s_post, hide="UD", block=0),
        Part("post_up", (6, 16, 6), (10, 29, 10), s_post, hide="UD", block=1),
        Part("cap1", (5, 29, 5), (11, 30, 11), s_cap, block=1),
        Part("cap2", (6, 30, 6), (10, 31, 10), s_cap, hide="D", block=1),
        Part("cap3", (7, 31, 7), (9, 32, 9), s_cap, hide="D", block=1),
        Part("arm_up", (7, 23, 2), (9, 27, 9), board_paint(to, "N", spr_up, CREAMP, seedn("fp_arm_up")), hide="D", block=1),
        Part("arm_lo", (7, 18, 7), (9, 22, 14), board_paint("bell", "S", "bell", BLUEP, seedn("fp_arm_lo")), hide="D", block=1),
    ]
    # the post and the caps are painted against fig coordinates (two blocks stacked), so the grain runs on across the seam
    return Fig(f"fingerpost_{to}", parts, blocks=2, under=wood_under, under_key="fingerpost")


def tip_paint(field_ramp, seed, side):
    """The bare tip of a board: end grain on the four cut faces, the board's own paint (chipped) on the two flat sides."""
    def paint(c, x, y, w, h, face, rng, dims):
        for j in range(h):
            for i in range(w):
                if face in "EW":
                    r = rng.random()
                    k = 3 + (1 if j == 0 else 0)
                    col = step(field_ramp, k) if r > 0.2 else step(OAKF, 4 + int(rng.random() * 3))
                else:
                    ctx = Ctx()
                    ctx.i, ctx.j, ctx.w, ctx.h = i, j, w, h
                    ctx.seed, ctx.cx, ctx.cy, ctx.cz, ctx.face = seed, i, j, ord(face), face
                    col = endgrain_px(ctx, seed)
                c.set(x + i, y + j, col)
    return paint


def fingerpost_models(w, to):
    fig = fingerpost_fig(to)
    tex = fig.textures(w, f"fingerpost_{to}")
    under = w.named("fingerpost_under", fig.bake_under())
    out = {}
    for block, name in ((0, "lower"), (1, "upper")):
        m = MB(w.atlas("fingerpost"), f"fingerpost_{name}_{to}" if block else "fingerpost_lower", ao=False)
        for el in fig.elements(m, tex, block, m.var(under)):
            m.raw(el)
        if block == 1:
            # the two pointed tips: 45 degree diamonds on the ends of the boards (local y of the block: upper arm 7-11, lower arm 2-6)
            s_ = 4 / math.sqrt(2)
            for (cy, cz, ramp, tip) in ((9.0, 2.0, CREAMP, "N"), (4.0, 14.0, BLUEP, "S")):
                a = (7.1, cy - s_ / 2, cz - s_ / 2)
                b_ = (8.9, cy + s_ / 2, cz + s_ / 2)
                mt = tip_paint(ramp, seedn(f"fp_tip_{tip}"), tip)
                faces = box_faces(m, a, b_, mt, "NESWUD", key=f"fp_tip_{tip}")
                m.raw(rigid(a, b_, faces, ops=[("x", 45, (8, cy, cz))]))
        out[name] = m.build(ao=False)
    return out


@gen
def g_fingerpost(w):
    variants = {}
    lower = None
    for to in TOS:
        ms = fingerpost_models(w, to)
        if lower is None:
            lower = ms["lower"]
            w.model("fingerpost_lower", finish(w, lower))
        w.model(f"fingerpost_upper_{to}", finish(w, ms["upper"]))
    for f in FACINGS:
        for to in TOS:
            variants[f"facing={f},half=lower,to={to}"] = rot_variant("fingerpost_lower", f)
            variants[f"facing={f},half=upper,to={to}"] = rot_variant(f"fingerpost_upper_{to}", f)
    w.state("fingerpost", variants)


# ---------------------------------------------------------------------------------------------------------------------
# 3. scarecrow. Two blocks on a rough 2x2 stake. Lower: the ragged hem of a long coat (y 10-16, 8 wide, 4 deep, torn at the bottom,
#    patched brown and faded green), two straw-stuffed trouser legs hanging from under it (tied at the ankles, straw tufts below,
#    swinging a few degrees each way). Upper: the coat's body with a rope belt (knot, two loose ends), the crossbar's sleeves
#    (x 1.5-14.5, y 5-8) ending in cuffs with straw bursting out, a rope-bound neck, a sacking head 6 x 6 with a stitched face (two
#    mismatched button eyes, a stitched smile), and a battered wide-brimmed straw hat: an octagonal brim 11 px across and a dented crown,
#    knocked askew. Straw, rope and cloth are boxes in the atlas; the coat, belt, sleeves and head are baked as one two-block figure so the
#    wear and the patches run on across the seam.

BURLF = fine(BURLAP)
BROWNF = fine(BROWN_C)
GREENF = fine(GREEN_C)
DRABF = fine(DRAB)
STRAWF = fine(STRAWP)
HEMPF = fine(HEMP)
# patches: (face, u0, u1, y0, y1, kind) in figure coordinates (u is x for the north/south faces, z for the east/west ones)
PATCHES = [("N", 4.6, 8.6, 11, 15, "g"), ("S", 7.4, 11.4, 12, 15.4, "g"), ("E", 6.6, 9.6, 11.4, 14.8, "g"), ("W", 6.4, 9.4, 12, 14.6, "b"),
           ("N", 7.6, 10.8, 19.6, 22.6, "g"), ("S", 5.8, 9.4, 19.6, 22.6, "g"), ("W", 6.8, 9.6, 19.4, 22.4, "g"),
           ("N", 10.2, 13.4, 21, 24, "b")]


def patch_at(face, u, y):
    for (f, u0, u1, y0, y1, kind) in PATCHES:
        if f == face and u0 <= u < u1 and y0 <= y < y1:
            edge = (u - u0 < 1) or (u1 - u <= 1) or (y - y0 < 1) or (y1 - y <= 1)
            return kind, edge
    return None


def coat_paint(seed, hem=False):
    """Long-coat wool: brown, mottled, vertical folds, patches of faded green and a darker brown sewn on with dashed stitches; the hem
    is torn into ragged tongues (holes) and the thread frays pale above the tear."""
    def paint(c):
        f = c.face
        u = c.x if f in "NS" else c.z
        cu = c.cx if f in "NS" else c.cz
        y = c.y
        if hem and f != "U" and c.cy < 13:
            depth = int(hr(seed + 9, ord(f), cu) * 3.3)
            if depth == 3 and hr(seed + 10, ord(f), cu) < 0.5:
                depth = 1
            if c.cy - 10 < depth:
                return None                                                 # torn away
            frayed = (c.cy - 10 == depth) and hr(seed + 11, ord(f), cu) < 0.5
        else:
            frayed = False
        k = 6
        n = vn(seed, c.x, c.y, c.z, 2.2)
        if n > 0.64:
            k += 1
        elif n < 0.32:
            k -= 1
        fold = hr(seed + 3, ord(f), cu)
        if fold < 0.2 and hr(seed + 4, ord(f), cu, int(y / 3)) < 0.7:
            k -= 1
        elif fold > 0.9:
            k += 1
        r = c.rnd(1)
        if r < 0.2:
            k += 1
        elif r < 0.4:
            k -= 1
        base = step(BROWNF, k)
        if f != "U":
            pa = patch_at(f, u, y)
            if pa:
                kind, edge = pa
                kk = 6 + (1 if c.rnd(2) < 0.25 else -1 if c.rnd(3) < 0.2 else 0)
                pc = step(GREENF, kk) if kind == "g" else step(BROWNF, kk - 3)
                if edge:
                    return STITCH if (c.cx + c.cy + c.cz) % 2 == 0 else mul(pc, 0.8)
                return pc
        if frayed:
            return lerp(base, step(BROWNF, 10), 0.6)
        if hem and f != "U" and y < 11.5:
            base = mul(base, 0.88)                                          # the dirty hem
        return base
    return paint


def stake_paint(seed):
    def paint(c):
        k = 4 + (1 if c.i == 0 else -1 if c.i == c.w - 1 else 0)
        if c.rnd(1) < 0.25:
            k += 1 if c.rnd(2) < 0.5 else -1
        col = step(OAKF, k)
        if c.y < 2.5 and c.rnd(3) < 0.4:
            col = step(MOSS, 1 + int(c.rnd(5) * 2))
        return col
    return paint


def rope_paint(seed, knot=False):
    """Hemp rope, twisted: a diagonal lay, lit and shaded strands."""
    def paint(c):
        f = c.face
        twist = (c.cx + c.cz + c.cy * 2) % 3
        k = 5 + (1 if twist == 0 else 0) - (1 if twist == 2 else 0)
        if c.rnd(1) < 0.2:
            k += 1 if c.rnd(2) < 0.5 else -1
        if knot and f == "N" and 8 <= c.cx <= 9 and 18 <= c.cy <= 18:
            k = 8 if c.cx == 8 else 3                                         # the knot's bulge, lit on its left
        return step(HEMPF, k)
    return paint


def cuff_paint(seed):
    def paint(c):
        k = 3 + (1 if c.rnd(1) < 0.3 else 0) - (1 if c.rnd(2) < 0.3 else 0)
        if c.face in "NS" and c.i % 2 == 0:
            k -= 1                                                             # a dark stitched hem
        return step(BROWNF, 2 * k - 1)
    return paint


def head_paint(seed, low=False):
    """Sacking: a coarse weave (a checker of one fine step with threads alternating by row), a patch at the side, and on the north
    face the stitched face: two mismatched button eyes (a big bone one with two thread holes, a small dark one) and a stitched smile."""
    EYE_L = {(1, 1): "h", (2, 1): "k", (1, 2): "k", (2, 2): "k"}              # a big dark button with a glint, left eye
    EYE_R = {(4, 1): "k", (4, 2): "k"}                                        # a small dark button, right eye
    MOUTH = {(1, 3), (4, 3), (2, 4), (3, 4)}                                  # a smile

    def paint(c):
        k = 6 + (1 if (c.cx + c.cy) % 2 == 0 else -1) * 1
        if c.cy % 3 == 0:
            k -= 1
        r = c.rnd(1)
        if r < 0.18:
            k += 1
        elif r < 0.30:
            k -= 1
        if c.face in "EW" and c.i == 0:
            k += 1
        if c.face == "U":
            k += 1
        col = step(BURLF, k)
        if not low and c.face == "N" and c.w == 6:
            q = (c.i, c.j)
            if q in EYE_L:
                return {"h": rgb("#7a6444"), "k": rgb("#211812")}[EYE_L[q]]
            if q in EYE_R:
                return {"h": rgb("#6b5638"), "k": rgb("#211812")}[EYE_R[q]]
            if q in MOUTH:
                return THREAD if (c.i + c.j) % 2 == 0 or c.j == 3 else lerp(THREAD, col, 0.45)
            if q in ((0, 3), (5, 3)):                                          # the thread tails at the smile's ends
                return lerp(col, THREAD, 0.35)
        if c.face == "E" and 29 <= c.y and False:
            pass
        return col
    return paint


def rope_under(c):
    return mul(step(BROWNF, 3 + (1 if c.rnd(1) < 0.3 else 0)), 0.8)


def hat_paint(seed, role):
    """A straw hat: braided rings on the brim, a vertical plait on the crown, a dark band, a frayed rim."""
    def paint(c):
        f = c.face
        d = math.hypot(c.x - 8, c.z - 8)
        if role == "band":
            k = 2 + (1 if c.rnd(1) < 0.3 else 0)
            if f != "U" and c.j == 0:
                k += 1
            return step(fine(BROWN_C), 2 * k)
        if f == "U" or role == "brim":
            k = 6 + (1 if int(d * 1.15) % 2 == 0 else -1)
        else:
            k = 6 + (1 if (c.cx + c.cz) % 2 == 0 else -1)
        r = c.rnd(1)
        if r < 0.2:
            k += 1
        elif r < 0.4:
            k -= 1
        if role == "brim" and f != "U" and f != "D":
            k -= 1
        if f == "D":
            k -= 2
        if role == "crown" and f == "U":
            k -= 1 if vn(seed, c.x, c.y, c.z, 1.4) > 0.55 else 0
        col = step(STRAWF, k)
        if role == "brim" and d > 5.0 and c.rnd(2) < 0.12 and f in "UD":
            return None                                                      # a frayed bite out of the rim
        return col
    return paint


def hat_fig():
    parts = [Part("brim1", (2.5, 13, 5), (13.5, 14, 11), hat_paint(1, "brim"), block=0),
             Part("brim2", (5, 13, 2.5), (11, 14, 13.5), hat_paint(1, "brim"), block=0),
             Part("brim3", (3.5, 13, 3.5), (12.5, 14, 12.5), hat_paint(1, "brim"), block=0),
             Part("crownA", (4.75, 14, 4.75), (8.5, 16, 11.25), hat_paint(2, "crown"), hide="D", block=0),
             Part("crownB", (8.5, 14, 4.75), (11.25, 15.25, 11.25), hat_paint(3, "crown"), hide="D", block=0),
             Part("band", (4.5, 14, 4.5), (11.5, 15, 11.5), hat_paint(4, "band"), hide="UD", block=0)]
    return Fig("scarecrow_hat", parts, blocks=1, under=rope_under, under_key="scarecrow")


def scarecrow_fig():
    coat_h = coat_paint(seedn("sc_hem"), hem=True)
    coat_b = coat_paint(seedn("sc_body"))
    coat_s = coat_paint(seedn("sc_sleeve"))
    rope = rope_paint(seedn("sc_rope"))
    rope_k = rope_paint(seedn("sc_belt"), knot=True)
    neck = rope_paint(seedn("sc_neck"))
    head = head_paint(seedn("sc_head"))
    head_low = head_paint(seedn("sc_headlow"), low=True)
    cuff = cuff_paint(seedn("sc_cuff"))
    parts = [
        Part("stake", (7, 0, 7), (9, 16, 9), stake_paint(seedn("sc_stake")), hide="UD", block=0),
        Part("hem", (4, 10, 6), (12, 16, 10), coat_h, block=0),
        Part("body", (4.75, 16, 6.25), (11.25, 23, 9.75), coat_b, hide="D", block=1),
        Part("belt", (4.5, 18, 6), (11.5, 19, 10), rope_k, hide="UD", block=1),
        Part("tail1", (7.0, 16.25, 5.7), (7.8, 18, 6.0), rope, hide="UD", block=1),
        Part("tail2", (8.5, 16.75, 5.7), (9.3, 18, 6.0), rope, hide="UD", block=1),
        Part("sleeveL", (2, 21, 6.5), (4.75, 24, 9.5), coat_s, hide="ED", block=1),
        Part("sleeveR", (11.25, 21, 6.5), (14, 24, 9.5), coat_s, hide="WD", block=1),
        Part("cuffL", (1.5, 21, 6.25), (2.5, 24, 9.75), cuff, hide="ED", block=1),
        Part("cuffR", (13.5, 21, 6.25), (14.5, 24, 9.75), cuff, hide="WD", block=1),
        Part("neck", (5.5, 23, 5.5), (10.5, 24, 10.5), neck, hide="D", block=1),
        Part("headlow", (5.5, 24, 5.5), (10.5, 25, 10.5), head_low, hide="D", block=1),
        Part("head", (5, 25, 5), (11, 30, 11), head, hide="D", block=1),
    ]
    return Fig("scarecrow", parts, blocks=2, under=rope_under)


def M_cloth(ramp, seed, patch=None):
    """Trouser canvas on a box in the atlas: vertical creases, a few darker flecks, an optional stitched patch."""
    def mat(c, x, y, w, h, face, rng, dims):
        for j in range(h):
            for i in range(w):
                k = 6 + (1 if hr(seed, ord(face), i) > 0.7 else -1 if hr(seed, ord(face), i) < 0.25 else 0)
                r = rng.random()
                k += 1 if r < 0.15 else -1 if r < 0.30 else 0
                if face in "UD":
                    k = 5
                col = step(ramp, k)
                if patch and face in "NS" and w >= 3 and h >= 4 and 1 <= i <= w - 2 and 1 <= j <= 3:
                    edge = i in (1, w - 2) or j in (1, 3)
                    col = THREAD if (edge and (i + j) % 2 == 0) else (mul(step(BLUEF, 6), 1.0) if not edge else mul(col, 0.8))
                c.set(x + i, y + j, col)
    return mat


BLUEF = fine(R("#1a2338", "#2a3a58", "#3d5278", "#556f98", "#7390b6"))


def M_strawbox(seed, fray=False):
    """Loose straw on a box: lengthwise stalks in two or three shades, a pale cut end."""
    def mat(c, x, y, w, h, face, rng, dims):
        for j in range(h):
            for i in range(w):
                q = hr(seed, ord(face), i)
                k = 6 + (1 if q > 0.65 else -1 if q < 0.3 else 0)
                r = rng.random()
                k += 1 if r < 0.18 else -1 if r < 0.34 else 0
                if face in "UD":
                    k = 8 if face == "D" else 6
                c.set(x + i, y + j, step(STRAWF, k))
    return mat


def M_ropebox(c, x, y, w, h, face, rng, dims):
    for j in range(h):
        for i in range(w):
            k = 6 + (2 if (i + j) % 3 == 0 else 0) - (2 if (i + j) % 3 == 2 else 0)
            c.set(x + i, y + j, step(HEMPF, k))


def scarecrow_models(w):
    fig = scarecrow_fig()
    tex = fig.textures(w, "scarecrow")
    under = w.named("scarecrow_under", fig.bake_under())
    hat = hat_fig()
    htex = hat.textures(w, "scarecrow_hat")
    hunder = w.named("scarecrow_under", hat.bake_under())
    out = {}
    # ---- lower block
    m = MB(w.atlas("scarecrow"), "scarecrow_lower", ao=False)
    for el in fig.elements(m, tex, 0, m.var(under)):
        m.raw(el)
    for (xc, ang_z, ang_x, tag, sd) in ((6.1, -6, 5, "L", 1), (9.9, 8, -4, "R", 2)):
        P = (xc, 10.2, 8)
        ops = [("z", ang_z, P), ("x", ang_x, P)]
        dx = xc - 6.1
        tube = (5.2 + dx, 4.8, 6.6), (7.0 + dx, 10.2, 9.4)
        m.raw(rigid(*tube, box_faces(m, *tube, M_cloth(DRABF, 30 + sd, patch=(tag == "L")), "NESWUD", key=f"leg_{tag}"), ops))
        tie = (5.0 + dx, 5.2, 6.4), (7.2 + dx, 6.1, 9.6)
        m.raw(rigid(*tie, box_faces(m, *tie, M_ropebox, "NESWUD", key="tie"), ops))
        wad = (5.4 + dx, 3.6, 6.9), (6.8 + dx, 5.2, 9.1)
        m.raw(rigid(*wad, box_faces(m, *wad, M_strawbox(40 + sd), "NESWD", key=f"wad{tag}"), ops))
        for q, (sx, sz, ang, ax_) in enumerate(((5.6, 7.1, 22, "z"), (6.0, 8.5, -18, "z"), (6.4, 7.6, 16, "x"), (6.65, 8.9, -26, "x"))):
            st = (sx + dx, 2.0, sz), (sx + dx + 0.6, 3.8, sz + 0.6)
            piv = (sx + dx + 0.3, 3.8, sz + 0.3)
            m.raw(rigid(*st, box_faces(m, *st, M_strawbox(50 + q), "NESWD", key=f"strand{q % 2}"), [(ax_, ang, piv)] + ops))
    out["lower"] = finish(w, m.build(ao=False))
    # ---- upper block
    m = MB(w.atlas("scarecrow"), "scarecrow_upper", ao=False)
    for el in fig.elements(m, tex, 1, m.var(under)):
        m.raw(el)
    for side in (0, 1):                                                    # the straw that bursts from each cuff
        sgn = -1 if side == 0 else 1
        x0 = 0.0 if side == 0 else 14.5
        wad = (x0 + 0.2 * (1 - side), 5.0, 6.6), (x0 + 1.5 - 0.2 * (1 - side) + 0.2 * side, 8.0, 9.4)
        wad = ((0.1, 5.0, 6.6), (1.5, 8.0, 9.4)) if side == 0 else ((14.5, 5.0, 6.6), (15.9, 8.0, 9.4))
        m.raw(rigid(*wad, box_faces(m, *wad, M_strawbox(60 + side), "NESWUD", key=f"cwad{side}")))
        for q, (yy, zz, ang) in enumerate(((5.2, 6.8, 28), (6.0, 8.4, -14), (7.0, 7.2, 36), (7.4, 8.8, -30), (5.8, 9.0, 12))):
            if side == 0:
                st = (0.0, yy, zz), (1.7, yy + 0.6, zz + 0.6)
                piv = (1.6, yy + 0.3, zz + 0.3)
            else:
                st = (14.3, yy, zz), (16.0, yy + 0.6, zz + 0.6)
                piv = (14.4, yy + 0.3, zz + 0.3)
            m.raw(rigid(*st, box_faces(m, *st, M_strawbox(70 + q), "NESWD", key=f"cstrand{q % 3}"), [("z", ang * (-sgn), piv)]))
    for q, (xx, zz, ang, ax_) in enumerate(((5.6, 9.8, -34, "z"), (9.6, 9.9, 38, "z"), (7.6, 5.4, 30, "x"))):     # straw from the neck tie
        st = (xx, 6.6, zz), (xx + 0.6, 8.0, zz + 0.6)
        piv = (xx + 0.3, 7.5, zz + 0.3)
        m.raw(rigid(*st, box_faces(m, *st, M_strawbox(80 + q), "NESWD", key=f"nstrand{q}"), [(ax_, ang, piv)]))
    for el in hat.elements(m, htex, 0, m.var(hunder), ops=[("z", 10, (8, 13, 8))]):
        m.raw(el)
    out["upper"] = finish(w, m.build(ao=False))
    return out


@gen
def g_scarecrow(w):
    ms = scarecrow_models(w)
    w.model("scarecrow_lower", ms["lower"])
    w.model("scarecrow_upper", ms["upper"])
    w.state("scarecrow", {f"facing={f},half={h}": rot_variant(f"scarecrow_{h}", f) for f in FACINGS for h in HALVES})


# ---------------------------------------------------------------------------------------------------------------------
# 4. stook. Eight sheaves stood leaning in together (about 14 degrees, each about its own foot, so the tops meet over the middle
#    and the feet make a ring 12 px across), a fat upright core so the ring is never hollow, and a cap sheaf of ears spread over the
#    top like a hat to shed the rain. Each sheaf is five boxes (cut butts, waist, a twine band, the stalks, the heavy ears) plus two
#    crossed cutout planes of awns above the ears. Gold to pale straw, on wheat_stage7 and the hay bale. Three variants (the game picks
#    one per position, so a field of stooks is not a row of copies), each rotated by the blockstate.

STOOKG = R("#5e4d14", "#7d6820", "#9a8226", "#b49a30", "#c9ae3c", "#dcc258", "#ecd883")   # hay_block_side, a little warmer
GOLDF = fine(STOOKG)
WHEATF = fine(WHEAT)
STOOK_VARIANTS = ["a", "b", "c"]


def M_stalks(seed, tone=6, cut=False):
    """Straw stalks standing up the face: columns of two or three shades with a thin pale stalk here and there, darker at the foot,
    the underside (a cut end) pale."""
    def mat(c, x, y, w, h, face, rng, dims):
        for j in range(h):
            for i in range(w):
                q = hr(seed, ord(face), i)
                k = tone + (2 if q > 0.66 else -2 if q < 0.28 else 0)
                if face in "UD":
                    k = tone + (2 if face == "D" else 1)
                r = rng.random()
                k += 1 if r < 0.12 else -1 if r < 0.24 else 0
                if face not in "UD" and cut and j >= h - 1:
                    k -= 2
                col = step(GOLDF, k)
                if face not in "UD" and rng.random() < 0.07:
                    col = step(GOLDF, tone + 4)                                     # a pale stalk
                c.set(x + i, y + j, col)
    return mat


def M_ears(seed):
    """Ripe ears: staggered pale grains with olive shadows between, an awn or two caught in the light; seen from above, a dense bed."""
    def mat(c, x, y, w, h, face, rng, dims):
        for j in range(h):
            for i in range(w):
                stag = (i + (j // 1)) % 2 == 0
                k = 11 + (1 if stag else -1) + (1 if j < 1 else 0)
                if face == "U":
                    k = 11 + (1 if stag else -1)
                r = rng.random()
                k += 1 if r < 0.1 else -1 if r < 0.2 else 0
                if face != "U" and j >= h - 1:
                    k -= 2                                                           # shaded where the ears meet the stalks
                c.set(x + i, y + j, step(WHEATF, k))
    return mat


def M_twine(c, x, y, w, h, face, rng, dims):
    for j in range(h):
        for i in range(w):
            k = 6 + (2 if (i + j) % 2 == 0 else -1)
            if face in "UD":
                k = 5
            c.set(x + i, y + j, step(HEMPF, k))


def M_awn(seed):
    """Three or four ears against the light (cutout): a thin stem, a pale head of grains two pixels wide, awns bristling above."""
    def mat(c, x, y, w, h, face, rng, dims):
        rr = __import__("random").Random(seed)
        stems = sorted(rr.sample(range(w), min(w, 3 + (seed % 2))))
        for n, sx in enumerate(stems):
            top = rr.randrange(0, 2)                                   # how high this ear's head reaches
            for py in range(h - 1, top - 1, -1):
                t = h - 1 - py
                if t < 1:
                    c.set(x + sx, y + py, step(WHEATF, 8))             # the stem
                elif py >= top + 2:
                    c.set(x + sx, y + py, step(WHEATF, 8 if t < 2 else 10))
                    if sx + 1 < w and py > top + 1 and t >= 2:
                        c.set(x + sx + 1, y + py, step(WHEATF, 11 if (py + n) % 2 else 9))      # the grains, two wide
                else:
                    c.set(x + sx, y + py, step(WHEATF, 12))            # the awns' bright tips
    return mat


def stook_model(w, var):
    m = MB(w.atlas("stook"), f"stook_{var}", ao=False)
    seed0 = seedn(f"stook_{var}")                       # the shape of this variant
    tseed = seedn("stook_tex")                          # the paint is shared by every variant (so the atlas stays small)
    sheaves = 8
    for i in range(sheaves):
        jit = lambda t: hr(seed0, i, t) * 2 - 1         # noqa: E731  (in -1..1)
        theta = i * 360.0 / sheaves + 14 + jit(1) * 7
        rad = 4.4 + jit(2) * 0.3
        lean = 14.5 + jit(3) * 2.2
        hgt = 1.0 + jit(4) * 0.06
        cx_ = 8 + rad * math.sin(math.radians(theta))
        cz_ = 8 + rad * math.cos(math.radians(theta))
        pv = (cx_, 0.0, cz_)
        ops = [("x", lean, pv), ("y", theta + 180.0, pv)]
        tag = i % 3

        def box(y0, y1, hw, hd, name, mat, faces="NESWU"):
            # the faces are painted at the nominal size (so every sheaf of a tag shares them); the box is a little taller or shorter
            nom_a, nom_b = (cx_ - hw, y0, cz_ - hd), (cx_ + hw, y1, cz_ + hd)
            fc = box_faces(m, nom_a, nom_b, mat, faces, key=f"{name}{tag}")
            a = (cx_ - hw, y0 * hgt, cz_ - hd)
            b_ = (cx_ + hw, y1 * hgt, cz_ + hd)
            m.raw(rigid(a, b_, fc, ops))
        box(0, 3.2, 1.3, 1.15, "butt", M_stalks(tseed + 1 + tag, 5, cut=True), "NESWD")
        box(3.2, 4.6, 1.1, 1.0, "waist", M_stalks(tseed + 2 + tag, 5), "NESW")
        box(4.0, 5.1, 1.4, 1.25, "band", M_twine, "NESW")
        box(4.6, 9.2, 1.2, 1.1, "body", M_stalks(tseed + 3 + tag, 6), "NESW")
        box(9.2, 12.8, 1.7, 1.4, "ears", M_ears(tseed + 4 + tag), "NESWU")
        # two crossed cutout planes of ears and awns over the top
        for ax_ in ("x", "z"):
            if ax_ == "x":
                nom = (cx_ - 1.9, 11.9, cz_), (cx_ + 1.9, 14.6, cz_)
                fc = box_faces(m, *nom, M_awn(tseed + 5 + tag), "NS", key=f"awn{tag}x")
                a, b_ = (cx_ - 1.9, 11.9 * hgt, cz_), (cx_ + 1.9, 14.6 * hgt, cz_)
            else:
                nom = (cx_, 11.9, cz_ - 1.9), (cx_, 14.6, cz_ + 1.9)
                fc = box_faces(m, *nom, M_awn(tseed + 6 + tag), "EW", key=f"awn{tag}z")
                a, b_ = (cx_, 11.9 * hgt, cz_ - 1.9), (cx_, 14.6 * hgt, cz_ + 1.9)
            m.raw(rigid(a, b_, fc, ops))
    # the core: so the ring is never hollow
    core = (6.3, 0, 6.3), (9.7, 9.6, 9.7)
    m.raw(E(*core, box_faces(m, *core, M_stalks(tseed + 20, 6), "NESW", key="core")))
    # the cap sheaf: ears spread over the top
    cap = (5.6, 9.6, 5.6), (10.4, 12.4, 10.4)
    m.raw(E(*cap, box_faces(m, *cap, M_ears(tseed + 21), "NESWUD", key="cap")))
    tie = (6.1, 8.8, 6.1), (9.9, 9.7, 9.9)
    m.raw(E(*tie, box_faces(m, *tie, M_twine, "NESW", key="tie")))
    return finish(w, m.build(ao=False))


@gen
def g_stook(w):
    for v in STOOK_VARIANTS:
        w.model(f"stook_{v}", stook_model(w, v))
    variants = {}
    for f in FACINGS:
        variants[f"facing={f}"] = weighted([{"model": f"stook_{v}", "y": YROT[f]} for v in STOOK_VARIANTS])
    w.state("stook", variants)


# @@ NEXT SECTION @@
# @@ GENERATORS END @@

# ---------------------------------------------------------------------------------------------------------------------
# registry

HALVES = ["lower", "upper"]
TOS = ["camp", "mine"]
# the Java properties (CommonsBlocks.java); the blockstates must cover exactly their product
PROPS = {
    "milestone": {"facing": FACINGS},
    "fingerpost": {"facing": FACINGS, "half": HALVES, "to": TOS},
    "scarecrow": {"facing": FACINGS, "half": HALVES},
    "stook": {"facing": FACINGS},
}
LANG = {"milestone": "Milestone", "fingerpost": "Fingerpost", "scarecrow": "Scarecrow", "stook": "Wheat Stook"}
AXE = ["fingerpost", "scarecrow"]
PICKAXE = ["milestone"]
HOE = ["stook"]

_WORLD = None


def everything():
    global _WORLD
    if _WORLD is None:
        w = CommonsWorld()
        for g in GENERATORS:
            g(w)
        _WORLD = w
    return _WORLD


def textures():
    return everything().tex


def build(out):
    w = everything()
    for name, c in w.tex.items():
        out.block(name, c)


# ---------------------------------------------------------------------------------------------------------------------
# content: blockstates, models, lang, tags

WRITTEN = ck.WRITTEN


def content():
    w = everything()
    for name, m in w.models.items():
        ck.model(name, m)
    for blk, variants in w.states.items():
        ck.blockstate(blk, variants)
    ck._merge(os.path.join(MERGE, "assets/awaken/lang/en_us.json"), {f"block.awaken.{k}": v for k, v in LANG.items()})
    for tag, names in (("axe", AXE), ("pickaxe", PICKAXE), ("hoe", HOE)):
        ck._merge(os.path.join(MERGE, f"data/minecraft/tags/block/mineable/{tag}.json"), {"values": [f"awaken:{n}" for n in names]})
    print(f"city_commons: content written ({len(w.models)} models, {len(w.states)} blockstates)")


def manifest():
    content()
    w = everything()
    paths = [os.path.join(TEX, n + ".png") for n in w.tex]
    paths += [p for p in dict.fromkeys(WRITTEN)]
    for p in paths:
        print(os.path.relpath(p, ROOT))


# ---------------------------------------------------------------------------------------------------------------------
# check

def _combos(props):
    keys = sorted(props)
    out = [()]
    for k in keys:
        out = [c + ((k, v),) for c in out for v in props[k]]
    return {frozenset(c) for c in out}


def _vkey(k):
    return frozenset(tuple(p.split("=")) for p in k.split(",")) if k else frozenset()


def _resolve(texmap, t):
    d = 0
    while isinstance(t, str) and t.startswith("#") and d < 8:
        t = texmap.get(t[1:])
        d += 1
    return t


def check():
    from PIL import Image
    errors, notes = [], []
    w = everything()
    used_models = set()
    n_variants = 0
    for blk, pr in PROPS.items():
        p = os.path.join(ASSETS, "blockstates", blk + ".json")
        if not os.path.exists(p):
            errors.append(f"missing blockstate {blk}")
            continue
        variants = json.load(open(p))["variants"]
        want = _combos(pr)
        have = {_vkey(k) for k in variants}
        if len(have) != len(variants):
            errors.append(f"{blk}: duplicate variant keys")
        if want - have:
            errors.append(f"{blk}: {len(want - have)} property combinations without a variant, e.g. {sorted(next(iter(want - have)))}")
        if have - want:
            errors.append(f"{blk}: {len(have - want)} variants the block can't have, e.g. {sorted(next(iter(have - want)))}")
        for k, v in variants.items():
            n_variants += 1
            for e in (v if isinstance(v, list) else [v]):
                used_models.add(e["model"])
        notes.append(f"{blk}: {len(variants)} variants ({'x'.join(str(len(v)) for v in pr.values())} = {len(want)})")
    for blk in w.states:
        if blk not in PROPS:
            errors.append(f"blockstate {blk} has no entry in PROPS")
    n_el = 0
    for ref in sorted(used_models):
        ns, path = ref.split(":")
        p = os.path.join(ASSETS, "models", path + ".json")
        if not os.path.exists(p):
            errors.append(f"missing model {ref}")
            continue
        m = json.load(open(p))
        parent = m.get("parent")
        if parent:
            pns, ppath = parent.split(":") if ":" in parent else ("minecraft", parent)
            pp = os.path.join(ASSETS if pns == "awaken" else VANILLA, "models", ppath + ".json")
            if not os.path.exists(pp):
                errors.append(f"{ref}: parent {parent} missing")
        texmap = dict(m.get("textures", {}))
        cut = m.get("render_type") == CUT
        for k in texmap:
            t = _resolve(texmap, texmap[k])
            if t is None or t.startswith("#"):
                errors.append(f"{ref}: texture key {k} does not resolve")
                continue
            tns, tpath = t.split(":")
            tp = os.path.join(ASSETS if tns == "awaken" else VANILLA, "textures", tpath + ".png")
            if not os.path.exists(tp):
                errors.append(f"{ref}: texture {t} not found (run build_all.py city_commons)")
        if "elements" not in m:
            errors.append(f"{ref}: no elements")
            continue
        for ei, el in enumerate(m["elements"]):
            n_el += 1
            for vec_ in (el["from"], el["to"]):
                if any(not (-16 <= v <= 32) for v in vec_):
                    errors.append(f"{ref}: element {ei} outside -16..32: {vec_}")
            if "rotation" in el:
                r = el["rotation"]
                if "axis" in r:
                    if r["axis"] not in ("x", "y", "z") or not isinstance(r.get("angle"), (int, float)):
                        errors.append(f"{ref}: element {ei} bad rotation {r}")
                elif not any(k in r for k in "xyz") or abs(r.get("y", 0)) > 89:
                    errors.append(f"{ref}: element {ei} bad euler rotation {r}")
                if len(r["origin"]) != 3:
                    errors.append(f"{ref}: element {ei} rotation origin {r['origin']}")
            for dname, f in el["faces"].items():
                t = _resolve(texmap, f["texture"])
                if t is None or t.startswith("#"):
                    errors.append(f"{ref}: element {ei} face {dname} texture {f['texture']} unresolved")
                    continue
                uv = f.get("uv", [0, 0, 16, 16])
                if any(not (0 <= u <= 16) for u in uv):
                    errors.append(f"{ref}: element {ei} face {dname} uv {uv} outside 0..16")
                tns, tpath = t.split(":")
                tp = os.path.join(ASSETS if tns == "awaken" else VANILLA, "textures", tpath + ".png")
                if os.path.exists(tp) and tns == "awaken" and not cut:
                    im = Image.open(tp).convert("RGBA")
                    x0, x1 = sorted((int(math.floor(uv[0] + 1e-6)), int(math.ceil(uv[2] - 1e-6))))
                    y0, y1 = sorted((int(math.floor(uv[1] + 1e-6)), int(math.ceil(uv[3] - 1e-6))))
                    bad = False
                    for yy in range(y0, max(y1, y0 + 1)):
                        for xx in range(x0, max(x1, x0 + 1)):
                            if im.getpixel((min(xx, 15), min(yy, 15)))[3] < 255:
                                bad = True
                    if bad:
                        errors.append(f"{ref}: element {ei} face {dname} samples a transparent texel of {t} but the model is not cutout")
    notes.append(f"{len(used_models)} models referenced, {n_el} elements, {n_variants} variants")
    for name, c in w.tex.items():
        p = os.path.join(TEX, name + ".png")
        if not os.path.exists(p):
            errors.append(f"texture {name}.png not written (run build_all.py city_commons)")
            continue
        im = Image.open(p)
        if im.size != (16, 16):
            errors.append(f"texture {name} is {im.size}")
        elif im.convert("RGBA").tobytes() != c.image().tobytes():
            errors.append(f"texture {name}.png differs from the generator (run build_all.py city_commons)")
        else:
            for yy in range(16):
                for xx in range(16):
                    if c.get(xx, yy)[3] not in (0, 255):
                        errors.append(f"{name}: partial alpha at {(xx, yy)}")
    import glob
    for f in glob.glob(os.path.join(TEX, "*.png")):                         # a texture of ours the generator no longer makes
        nm = os.path.basename(f)[:-4]
        if nm.split("_")[0] in PROPS and nm not in w.tex:
            errors.append(f"stale texture {nm}.png (the generator no longer writes it: delete it)")
    notes.append(f"{len(w.tex)} textures")
    for name, m in w.models.items():
        p = os.path.join(ASSETS, "models/block", name + ".json")
        if not os.path.exists(p):
            errors.append(f"model {name}.json not written (run content)")
        elif json.load(open(p)) != json.loads(json.dumps(m)):
            errors.append(f"model {name}.json differs from the generator (run content)")
    lang = json.load(open(os.path.join(MERGE, "assets/awaken/lang/en_us.json")))
    for k in LANG:
        if f"block.awaken.{k}" not in lang:
            errors.append(f"lang block.awaken.{k} missing")
    for tag, names in (("axe", AXE), ("pickaxe", PICKAXE), ("hoe", HOE)):
        vals = json.load(open(os.path.join(MERGE, f"data/minecraft/tags/block/mineable/{tag}.json")))["values"]
        for n in names:
            if f"awaken:{n}" not in vals:
                errors.append(f"tag {tag} lacks {n}")
    for n in notes:
        print("  ", n)
    if errors:
        print("CHECK FAILED:")
        for e in errors[:80]:
            print("  -", e)
        print(f"  ({len(errors)} problems)")
        return 1
    print("check: ok")
    return 0


# ---------------------------------------------------------------------------------------------------------------------
# contact sheet

def make_sheet(out, prefixes=()):
    from PIL import Image, ImageDraw
    w = everything()
    S, GAP, MAXW = int(os.environ.get("SHEET_S", "4")), 10, 1900
    items = []
    for name, c in w.tex.items():
        if prefixes and not any(name.startswith(p) for p in prefixes):
            continue
        items.append((name, c.image()))
    placed, x, y, rowh = [], GAP, GAP, 0
    for lab, im in items:
        wd, ht = 16 * S, 16 * S
        if x + wd + GAP > MAXW:
            x, y, rowh = GAP, y + rowh + GAP + 50, 0
        placed.append((lab, im, x, y))
        x += wd + GAP
        rowh = max(rowh, ht)
    H = y + rowh + GAP + 56
    sheet = Image.new("RGBA", (MAXW, H), (36, 38, 48, 255))
    d = ImageDraw.Draw(sheet)
    for lab, im, px, py in placed:
        chk = Image.new("RGBA", (16 * S, 16 * S))
        cd = ImageDraw.Draw(chk)
        for yy in range(0, 16 * S, S):
            for xx in range(0, 16 * S, S):
                v = 112 + (12 if ((xx // S + yy // S) % 2) else 0)
                cd.rectangle([xx, yy, xx + S - 1, yy + S - 1], fill=(v, v, v + 6, 255))
        sheet.paste(chk, (px, py))
        big = im.resize((16 * S, 16 * S), Image.NEAREST)
        sheet.paste(big, (px, py), big)
        parts = [lab[i:i + 12] for i in range(0, min(len(lab), 48), 12)]
        for li, part in enumerate(parts):
            d.text((px, py + 16 * S + 2 + li * 11), part, fill=(226, 228, 236, 255))
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    sheet.save(out)
    print(out, len(items), "textures")


# ---------------------------------------------------------------------------------------------------------------------
# the renderer: the street kit's rasteriser (cs.render) fed from this spec's own world (with the Euler element rotation and a few
# stand-in textures for scenery)

_IMG = {}
MOCK_TEX = {}
GRASS_TINT = (145, 189, 89)                       # a plains grass colour map value; the vanilla texture is grey and tinted in game


def tex_image(ref, frame=0):
    from PIL import Image
    key = (ref, frame)
    if key in _IMG:
        return _IMG[key]
    ns, path = ref.split(":")
    if ns == "mock":
        im = MOCK_TEX[path]()
    elif ns == "awaken":
        nm = path.split("block/", 1)[1]
        if nm in everything().tex:
            im = everything().tex[nm].image()
        else:
            im = Image.open(os.path.join(TEX, nm + ".png")).convert("RGBA").crop((0, 0, 16, 16))
    else:
        im = Image.open(os.path.join(VANILLA, "textures", path + ".png")).convert("RGBA")
        im = im.crop((0, 0, im.width, im.width))
        if im.width != 16:
            im = im.resize((16, 16), Image.NEAREST)
    _IMG[key] = im
    return im


def _tinted(path, tint):
    def f():
        from PIL import Image
        im = Image.open(os.path.join(VANILLA, "textures", path + ".png")).convert("RGBA")
        px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                r, g, b_, a = px[x, y]
                px[x, y] = (r * tint[0] // 255, g * tint[1] // 255, b_ * tint[2] // 255, a)
        return im
    return f


MOCK_TEX["grass"] = _tinted("block/grass_block_top", GRASS_TINT)
MOCK_TEX["grass_side"] = lambda: _grass_side()


def _grass_side():
    from PIL import Image
    base = Image.open(os.path.join(VANILLA, "textures/block/grass_block_side.png")).convert("RGBA")
    ov = _tinted("block/grass_block_side_overlay", GRASS_TINT)()
    base.alpha_composite(ov)
    return base


def resolve_state(block, props, pick=0):
    w = everything()
    want = {k: str(v) for k, v in props.items()}
    for k, v in w.states[block].items():
        have = dict(p.split("=") for p in k.split(",")) if k else {}
        if all(have.get(p) == q for p, q in want.items()) and set(have) == set(want):
            return v[pick % len(v)] if isinstance(v, list) else v
    raise KeyError(f"{block} {props}")


MOCK_MODELS = {}


def _rot_el(p, r):
    """Apply an element's rotation (either form) to a point."""
    if "axis" in r:
        return _rotp(p, r["axis"], r["angle"], r["origin"])
    o = r["origin"]
    v = [p[0] - o[0], p[1] - o[1], p[2] - o[2]]
    M = mmul(mrz(r.get("z", 0)), mmul(mry(r.get("y", 0)), mrx(r.get("x", 0))))
    v = mvec(M, v)
    return (v[0] + o[0], v[1] + o[1], v[2] + o[2])


def _rot_nrm(n, r):
    if "axis" in r:
        return _rotv(n, r["axis"], r["angle"])
    M = mmul(mrz(r.get("z", 0)), mmul(mry(r.get("y", 0)), mrx(r.get("x", 0))))
    return tuple(mvec(M, list(n)))


def model_quads(model_ref, xrot, yrot, offset):
    """Quads (4 corner points, 4 uv, image, shade, emit, normal) of a model, rotated by the blockstate and placed at offset (px)."""
    w = everything()
    if model_ref.startswith("mock:"):
        m = MOCK_MODELS[model_ref[5:]]
    else:
        m = w.models[model_ref.split("block/", 1)[1]]
    texmap = m["textures"]
    quads = []
    for el in m["elements"]:
        a, b_ = el["from"], el["to"]
        emit = el.get("light_emission", 0)
        noshade = el.get("shade") is False
        override = el.get("shade_direction_override")
        for dname, f in el["faces"].items():
            L = LETTER[dname]
            pts = _corners(a, b_, L)
            nrm = FNORM[L]
            if "rotation" in el:
                pts = [_rot_el(p, el["rotation"]) for p in pts]
                nrm = _rot_nrm(nrm, el["rotation"])
            if xrot:
                pts = [_rotp(p, "x", -xrot, (8, 8, 8)) for p in pts]
                nrm = _rotv(nrm, "x", -xrot)
            if yrot:
                pts = [_rotp(p, "y", -yrot, (8, 8, 8)) for p in pts]
                nrm = _rotv(nrm, "y", -yrot)
            ax = max(range(3), key=lambda i: abs(nrm[i]))
            near = {(0, 1): "E", (0, -1): "W", (1, 1): "U", (1, -1): "D", (2, 1): "S", (2, -1): "N"}[(ax, 1 if nrm[ax] > 0 else -1)]
            if override:
                near = LETTER[override]
            sh = 1.0 if noshade else SHADE[near]
            ref = _resolve(texmap, f["texture"])
            img = tex_image(ref)
            u0, v0, u1, v1 = f["uv"]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            for _ in range(f.get("rotation", 0) // 90):
                uvs = [uvs[3], uvs[0], uvs[1], uvs[2]]
            pts = [(p[0] + offset[0], p[1] + offset[1], p[2] + offset[2]) for p in pts]
            quads.append((pts, uvs, img, sh, emit, nrm))
    return quads


def placed_block(block, props, cell, pick=0):
    v = resolve_state(block, props, pick)
    return model_quads(v["model"], v.get("x", 0), v.get("y", 0), (cell[0] * 16, cell[1] * 16, cell[2] * 16))


def mock_box(cell, a, b_, tex, faces="NESWUD", top=None):
    """A stand-in cuboid (vanilla texture) for scenery; top overrides the up face's texture."""
    fd = {}
    for L in faces:
        fd[FACE_NAMES[L]] = {"uv": [0, 0, 16, 16], "texture": "#u" if (L == "U" and top) else "#t"}
    tx = {"t": tex}
    if top:
        tx["u"] = top
    m = {"textures": tx, "elements": [{"from": list(a), "to": list(b_), "faces": fd}]}
    name = f"box{len(MOCK_MODELS)}"
    MOCK_MODELS[name] = m
    return model_quads("mock:" + name, 0, 0, (cell[0] * 16, cell[1] * 16, cell[2] * 16))


def mock_crop(cell, tex):
    """The vanilla crop model: four crossed planes at 4 and 12, shaded as the top."""
    els = []
    for (a, b_, fa) in (((4, -1, 0), (4, 15, 16), ("west", "east")), ((12, -1, 0), (12, 15, 16), ("west", "east")),
                        ((0, -1, 4), (16, 15, 4), ("north", "south")), ((0, -1, 12), (16, 15, 12), ("north", "south"))):
        els.append({"from": list(a), "to": list(b_), "shade_direction_override": "up",
                    "faces": {fa[0]: {"uv": [0, 0, 16, 16], "texture": "#c"}, fa[1]: {"uv": [16, 0, 0, 16], "texture": "#c"}}})
    m = {"textures": {"c": tex}, "elements": els}
    name = f"box{len(MOCK_MODELS)}"
    MOCK_MODELS[name] = m
    return model_quads("mock:" + name, 0, 0, (cell[0] * 16, cell[1] * 16, cell[2] * 16))


def blocks_quads(items):
    q = []
    last = {it[1]: i for i, it in enumerate(items) if it[0] == "box"}          # a later stand-in box replaces an earlier one in its cell
    items = [it for i, it in enumerate(items) if it[0] != "box" or last[it[1]] == i]
    for it in items:
        if it[0] == "box":
            q += mock_box(it[1], it[2], it[3], it[4], top=(it[5] if len(it) > 5 else None))
        elif it[0] == "crop":
            q += mock_crop(it[1], it[2])
        else:
            opts = it[3] if len(it) > 3 else {}
            q += placed_block(it[0], it[1], it[2], opts.get("pick", 0))
    return q


def view_cmd(out, block, kv):
    """One block, every value of the one property given as k=*, at the other properties' first values."""
    pr = PROPS[block]
    fixed = {k: v[0] for k, v in pr.items()}
    sweep = None
    opts = {"S": 8, "yaw": 28, "pitch": 24, "night": 1.0, "gap": 1.25, "pick": 0}
    for a in kv:
        k, v = a.split("=")
        if k in opts:
            opts[k] = float(v) if k in ("night", "gap") else int(v)
        elif v == "*":
            sweep = k
        else:
            fixed[k] = v
    items = []
    vals = pr[sweep] if sweep else [None]
    for i, val in enumerate(vals):
        p = dict(fixed)
        if sweep:
            p[sweep] = val
        items.append((block, p, (i * opts["gap"], 0, 0), {"pick": opts["pick"]}))
        if "half" in pr and "half" != sweep and fixed.get("half") == "lower":
            q = dict(p)
            q["half"] = "upper"
            items.append((block, q, (i * opts["gap"], 1, 0), {"pick": opts["pick"]}))
    im = render(blocks_quads(items), yaw=opts["yaw"], pitch=opts["pitch"], S=opts["S"], night=opts["night"])
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    im.save(out)
    print(out, im.size)


# ----- scene library (stand-in scenery uses vanilla textures) ------------------------------------------------------------

GRASS_T = "mock:grass"
GRASS_S = "mock:grass_side"
DIRT_T = "minecraft:block/dirt"
PATH_T = "minecraft:block/dirt_path_top"
PATH_S = "minecraft:block/dirt_path_side"
FARM_T = "minecraft:block/farmland"
COB_T = "minecraft:block/cobblestone"
HAY_S = "minecraft:block/hay_block_side"
HAY_T = "minecraft:block/hay_block_top"
WHEAT7 = "minecraft:block/wheat_stage7"
SPRUCE_T = "minecraft:block/spruce_planks"
STONE_T = "minecraft:block/stone_bricks"


def ground(x0, x1, z0, z1, y=-1, top=GRASS_T, side=GRASS_S):
    return [("box", (x, y, z), (0, 0, 0), (16, 16, 16), side, top) for x in range(x0, x1) for z in range(z0, z1)]


def strip(x0, x1, z0, z1, y=-1, h=15):
    """A dirt path: 15/16 high."""
    return [("box", (x, y, z), (0, 0, 0), (16, h, 16), PATH_S, PATH_T) for x in range(x0, x1) for z in range(z0, z1)]


def farmland(x0, x1, z0, z1, y=-1):
    return [("box", (x, y, z), (0, 0, 0), (16, 15, 16), DIRT_T, FARM_T) for x in range(x0, x1) for z in range(z0, z1)]


def wheat(x0, x1, z0, z1, y=0):
    return [("crop", (x, y, z), WHEAT7) for x in range(x0, x1) for z in range(z0, z1)]


def hay(x, y, z):
    return ("box", (x, y, z), (0, 0, 0), (16, 16, 16), HAY_S, HAY_T)


SCENES = {}
SCENE_ORDER = []


def scene(title):
    def deco(fn):
        SCENE_ORDER.append((title, fn))
        return fn
    return deco


def mock(out):
    from PIL import Image, ImageDraw
    sel = sys.argv[3:]
    ims = []
    for title, fn in SCENE_ORDER:
        if sel and not any(x in title for x in sel):
            continue
        items, kw = fn()
        ims.append((title, render(blocks_quads(items), **kw)))
    MAXW, GAP = 3100, 12
    placed, x, y, rowh = [], GAP, GAP, 0
    for title, im in ims:
        if x + im.width + GAP > MAXW and x > GAP:
            x, y, rowh = GAP, y + rowh + GAP + 16, 0
        placed.append((title, im, x, y))
        x += im.width + GAP
        rowh = max(rowh, im.height)
    sheet = Image.new("RGBA", (max(p[2] + p[1].width for p in placed) + GAP, y + rowh + GAP + 16), (30, 32, 40, 255))
    d = ImageDraw.Draw(sheet)
    for title, im, px, py in placed:
        sheet.paste(im, (px, py + 14))
        d.text((px, py), title[:130], fill=(230, 232, 240, 255))
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    sheet.save(out)
    print(out, sheet.size)


# ---------------------------------------------------------------------------------------------------------------------
# scenes: noon, in fields and on a road; the stand-in ground is vanilla grass (tinted as in a plains biome), dirt path, farmland,
# wheat_stage7, hay bales, cobblestone and spruce, so the pieces are judged beside the things they must sit next to

def milestone(items, x, z, facing, y=0):
    items.append(("milestone", {"facing": facing}, (x, y, z)))


def fpost(items, x, z, facing, to, y=0):
    items.append(("fingerpost", {"facing": facing, "half": "lower", "to": to}, (x, y, z)))
    items.append(("fingerpost", {"facing": facing, "half": "upper", "to": to}, (x, y + 1, z)))


def scare(items, x, z, facing, y=0):
    items.append(("scarecrow", {"facing": facing, "half": "lower"}, (x, y, z)))
    items.append(("scarecrow", {"facing": facing, "half": "upper"}, (x, y + 1, z)))


def stook(items, x, z, facing, pick=0, y=0):
    items.append(("stook", {"facing": facing}, (x, y, z), {"pick": pick}))


def comparison(items, x, z):
    """A strip of vanilla neighbours: cobblestone, spruce planks, a hay bale, mossy cobblestone, stone bricks."""
    for i, t in enumerate((COB_T, SPRUCE_T, "minecraft:block/mossy_cobblestone", STONE_T)):
        items.append(("box", (x + i, 0, z), (0, 0, 0), (16, 16, 16), t))
    items.append(hay(x + 4, 0, z))


@scene("the milestone, one on each facing (north, east, south, west), on a verge beside the road, beside vanilla cobblestone, spruce, a hay bale, mossy cobblestone and stone bricks")
def scene_milestones():
    items = ground(0, 9, 0, 5) + strip(0, 9, 3, 5)
    for i, f in enumerate(("north", "east", "south", "west")):
        milestone(items, 1 + i * 2, 2, f)
    comparison(items, 0, 0)
    return items, dict(yaw=-24, pitch=22, S=9)


@scene("the milestone seen from the road, square on (facing south: the road side), then at an angle")
def scene_milestone_front():
    items = ground(0, 5, 0, 3) + strip(0, 5, 1, 3)
    milestone(items, 1, 0, "south")
    milestone(items, 3, 0, "south")
    return items, dict(yaw=180, pitch=8, S=14)


@scene("fingerposts: to=camp on the top row, to=mine below, each of the four facings (the upper arm points the way the block faces, the lower arm back)")
def scene_fingerposts():
    items = ground(0, 10, 0, 7) + strip(0, 10, 4, 6)
    for i, f in enumerate(("north", "east", "south", "west")):
        fpost(items, 1 + i * 2, 1, f, "camp")
        fpost(items, 1 + i * 2, 3, f, "mine")
    return items, dict(yaw=-30, pitch=18, S=8)


@scene("fingerposts from the side, upper and lower arms, camp and mine, at 22x")
def scene_fingerposts_close():
    items = ground(0, 2, 0, 4)
    fpost(items, 0, 0, "north", "camp")
    fpost(items, 0, 2, "north", "mine")
    return items, dict(yaw=90, pitch=6, S=20)


@scene("scarecrows in a wheat field: all four facings, farmland and ripe wheat round them")
def scene_scarecrows():
    items = farmland(0, 9, 0, 6) + wheat(0, 9, 0, 6)
    items = [it for it in items if not (it[0] == "crop" and it[1][1] == 0 and it[1][2] in (3, 4) and it[1][0] in (1, 3, 5, 7))]
    for i, f in enumerate(("north", "east", "south", "west")):
        scare(items, 1 + i * 2, 3, f)
    return items, dict(yaw=-24, pitch=20, S=9)


@scene("the scarecrow close up: front")
def scene_scarecrow_close():
    items = ground(0, 3, 0, 2)
    scare(items, 1, 0, "north")
    return items, dict(yaw=0, pitch=10, S=20)


@scene("the scarecrow, three-quarter view")
def scene_scarecrow_34():
    items = ground(0, 3, 0, 3)
    scare(items, 1, 1, "north")
    return items, dict(yaw=40, pitch=14, S=20)


@scene("the scarecrow from behind (a plain sack head, the coat's back)")
def scene_scarecrow_back():
    items = ground(0, 3, 0, 3)
    scare(items, 1, 1, "north")
    return items, dict(yaw=180, pitch=10, S=20)


@scene("a reaped field: stooks in rows on stubble (farmland), three variants and four facings, a hay bale and ripe wheat at the edge")
def scene_stooks():
    items = farmland(0, 10, 0, 7) + wheat(0, 10, 5, 7)
    n = 0
    for r, z in enumerate((1, 3)):
        for c in range(4):
            stook(items, 1 + c * 2 + (r % 2), z, ("north", "east", "south", "west")[(r * 3 + c) % 4], pick=n)
            n += 1
    items.append(hay(8, 0, 1))
    items.append(hay(8, 0, 2))
    items.append(hay(8, 1, 1))
    return items, dict(yaw=-28, pitch=24, S=9)


@scene("the stook close up: from the front and from above")
def scene_stook_close():
    items = ground(0, 3, 0, 2)
    stook(items, 0, 0, "north", pick=0)
    stook(items, 1, 0, "east", pick=1)
    stook(items, 2, 0, "south", pick=2)
    return items, dict(yaw=-20, pitch=26, S=18)


@scene("the commons at noon: the road out of the wall with its milestones and a fingerpost at the fork, a ripe field with a scarecrow, a reaped field of stooks")
def scene_commons():
    items = ground(0, 24, 0, 16) + strip(0, 24, 7, 9) + strip(10, 12, 0, 7)
    items += farmland(14, 24, 0, 6) + wheat(14, 24, 0, 6)
    items += farmland(14, 24, 10, 16)
    milestone(items, 3, 9, "north")
    milestone(items, 7, 6, "south")
    fpost(items, 9, 6, "north", "camp")
    fpost(items, 12, 9, "south", "mine")
    scare(items, 18, 3, "south")
    for r, z in enumerate((11, 13)):
        for c in range(4):
            stook(items, 15 + c * 2 + (r % 2), z, ("north", "east", "south", "west")[(r + c) % 4], pick=r * 4 + c)
    items.append(hay(22, 0, 11))
    return items, dict(yaw=-32, pitch=26, S=6)



if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "content":
        content()
    elif cmd == "check":
        sys.exit(check())
    elif cmd == "manifest":
        manifest()
    elif cmd == "sheet":
        make_sheet(sys.argv[2], sys.argv[3:])
    elif cmd == "mock":
        mock(sys.argv[2] if len(sys.argv) > 2 else "city_commons_mock.png")
    elif cmd == "view":
        view_cmd(sys.argv[2], sys.argv[3], sys.argv[4:])
    else:
        print(__doc__)
