"""The Sky Leviathan (SPEC §35.11, docs/bosses/ROLLOUT.md): the Sky gate's Lord, a Serpent of the air. The Ray, picked
2026-10-29: "The ground is much too far below."

A ray ten blocks across that flies the way a manta swims. On top it is storm slate veined with lightning that branches
out from its spine to its wingtips, with pale cloud patches on its shoulders; beneath it is pale as a cloud's underside,
with its gill slits in two rows. Two horns curl up either side of its wide mouth, and its small eyes glow at the corners
of its head. Six pearls glow along its spine. Behind it trails a whip of a tail that ends in forked lightning.

Its wings are chains of six strips each, root to tip, so every beat runs out along them as a wave; the tail is a chain
of eight that swings as a whip. Moves (SPEC §35.11): it banks and slaps down with one wing and then the other; the Horn
Ram (the horns unroll into a scoop and it surges at you); the Wingbeat (both wings raised high over its back, held,
then one enormous downstroke); the Leviathan Dive (it climbs nose up and dives at you like a dart); the tail cracks at
whoever is behind; the Gravity Tide (it rolls over onto its back, pale belly to the sky, and the sky tilts); the Pearl
Barrage (it arches its back and the pearls fire one after another down its spine); and the Storm Eye (the signature: it
rises with its wings curled up into a bowl and its tail raised over it, the disc wheeling, then throws them flat and the
lightning comes down). Its stance broken, it sags in the air with its wings drooping.
"""
import math
import random

from kit import core
from kit.core import DEG, Move, keyed, stage_clips
import fauna_rig as R
from fauna_rig import Ramp, hexc, SIDES

ID = "sky_leviathan"
SCALE = 3.2  # the hitbox on the def's SERPENT model at size 1.4 x 0.6: 4.5 x 1.9 blocks; the ray is modelled at true size

# The ray: 160 px from wingtip to wingtip (ten blocks), 92 from the horns to the back of the disc, and the tail 112 more.
# Its middle flies 16 px over the bottom of its box. Value story: the pale belly and the shoulder patches lightest, the
# slate top dark; the light is the lightning in its veins, its eyes, the pearls and the fork at the end of its tail.
HOVER = 16.0
SPAN0, SPAN = 15.0, 65.0  # the wings run from 15 px out to 80
SEG_W = (11, 11, 11, 11, 11, 10)  # each wing's six segments, root to tip
STRIPS = {11: (4, 4, 3), 10: (4, 3, 3)}  # each segment's strips (the edges step a pixel at a time)
NSEG = len(SEG_W)
TAIL = ((4, 4), (4, 3), (3, 3), (3, 3), (3, 2), (2, 2), (2, 2), (2, 2))  # each tail segment's width and height
TAIL_L = 14
NTAIL = len(TAIL)
PEARL_Z = (-21.0, -12.0, -3.0, 6.0, 15.0, 24.0)  # the six pearls down the spine, front to back
SIDES_ = (("l", 1), ("r", -1))
HORN_CURL = (12.0, 38.0, 50.0, 56.0)  # each horn segment's curl at rest (degrees, up and back): unrolled, they point ahead


def _le(u):
    """The leading edge's z at u (0 at the wing's root, 1 at its tip): out from the head, sweeping back to the tip."""
    return -32.0 + 26.0 * u + 18.0 * u ** 3


def _te(u):
    """The trailing edge's z: from the back of the body, in to a notch at mid-span and out to the tip."""
    return 30.0 - 18.0 * u - 12.0 * math.sin(math.pi * u)


def _thick(u):
    return max(1, int(round(1 + 8 * (1 - u) ** 1.4)))


def _bone(parent, name, x, up, z, rot=(0, 0, 0)):
    """A bone placed by height: x, how far up, z from its parent's pivot."""
    return parent.bone(name, (x, -up, z), rot)


def _box(bone, name, cx, y0, cz, w, h, d, mirror=False):
    """A cube centred on (cx, cz), from y0 up to y0 + h over its bone's pivot."""
    return bone.cube(name, cx - w / 2, -(y0 + h), cz - d / 2, w, h, d, mirror=mirror)


# every cube's place in the ray's own floor plan at rest (x, z of its x and z low corner), for painting across cubes
PLAN = {}


def _plan_box(bone, name, x0, y0, z0, w, h, d, at):
    """A cube from (x0, y0, z0) (x and z on the plan, y0 up over the bone's pivot) of size w, h, d; at is the bone's
    pivot on the plan, so the cube's plan corner is remembered."""
    c = bone.cube(name, x0 - at[0], -(y0 + h), z0 - at[1], w, h, d)
    PLAN[name] = (x0, z0)
    return c


def body():
    PLAN.clear()
    r = R.Rig(ID, (512, 512))
    base = r.bone("base", (0, 24, 0))
    bd = _bone(base, "body", 0, HOVER, 0)
    at = (0.0, 0.0)
    # the disc's middle: the body, the ridge of its back and the crest of its spine, and the belly under it
    _plan_box(bd, "core", -15, -4, -30, 30, 9, 60, at)
    _plan_box(bd, "ridge", -9, 5, -24, 18, 3, 52, at)
    _plan_box(bd, "crest", -3, 8, -26, 6, 1, 52, at)
    _plan_box(bd, "belly", -13, -6, -26, 26, 2, 50, at)
    _plan_box(bd, "dorsal", -1, 6, 26, 2, 6, 6, at)
    for k, z in enumerate(PEARL_Z):
        pb = _bone(bd, f"pearl{k}", 0, 9.5, z)
        _box(pb, f"pearl{k}", 0, -1.5, 0, 3, 3, 3)
    # the head: a wide flat front with the mouth across it, eyes at its corners, and the horns
    hd = _bone(bd, "head", 0, 0.5, -30)
    _plan_box(hd, "head", -14, -4, -42, 28, 8, 12, (0, -30))
    _plan_box(hd, "brow", -11, 4, -40, 22, 2, 10, (0, -30))
    jw = _bone(hd, "jaw", 0, -4, -11)
    _plan_box(jw, "jaw", -12, -2, -42, 24, 2, 7, (0, -41))
    for nm, sg in SIDES_:
        _plan_box(hd, "eye_" + nm, sg * 14.5 - 1, -0.5, -38, 2, 3, 4, (0, -30))
        hn = _bone(hd, "horn_" + nm, sg * 11.0, 0.5, -12, (-HORN_CURL[0], sg * 10, 0))
        dims = ((5, 3, 8), (4, 3, 7), (3, 2, 6), (2, 2, 5))
        b = hn
        for k, (w, h, d) in enumerate(dims):
            if k:
                b = b.bone(f"horn_{nm}{k}", (0, 0, -dims[k - 1][2]), (-HORN_CURL[k], 0, 0))
            b.cube(f"horn_{nm}{k}" if k else f"horn_{nm}0", -w / 2, -h / 2, -d, w, h, d)
    # the wings: six segments a side, each a few strips stepping out to the tip
    for nm, sg in SIDES_:
        parent, piv, x_at = bd, (sg * SPAN0, 0.0, 0.0), SPAN0
        for k, w in enumerate(SEG_W):
            wb = parent.bone(f"wing_{nm}{k}", piv)
            a = 0
            for j, sw in enumerate(STRIPS[w]):
                xc = x_at + a + sw / 2
                u = (xc - SPAN0) / SPAN
                le, te, t = _le(u), _te(u), _thick(u)
                d = max(1, int(round(te - le)))
                x0 = (x_at + a) if sg > 0 else -(x_at + a + sw)
                _plan_box(wb, f"wing_{nm}{k}{j}", x0, 0.5 - t / 2, le, sw, t, d, (sg * x_at, 0.0))
                a += sw
            parent, piv, x_at = wb, (sg * w, 0.0, 0.0), x_at + w
        pv = _bone(bd, "pelvic_" + nm, sg * 7, -3, 27)
        _plan_box(pv, "pelvic_" + nm, sg * 7 - 3, -1, 27, 6, 2, 8, (sg * 7, 27))
    # the tail, a whip of eight, and the fork of lightning at its end
    parent, piv = bd, (0, -0.5, 30)
    for k, (w, h) in enumerate(TAIL):
        tb = parent.bone(f"tail{k}", piv)
        tb.cube(f"tail{k}", -w / 2, -h / 2, 0, w, h, TAIL_L)
        parent, piv = tb, (0, 0, TAIL_L)
    fk = parent.bone("fork", piv)
    for nm, sg in SIDES_:
        pr = fk.bone("prong_" + nm, (0, 0, 0), (0, -sg * 28, 0))
        pr.cube(f"prong_{nm}0", -1, -1, 0, 2, 2, 6)
        pr.cube(f"prong_{nm}1", -1 + sg * 2, -1, 5, 2, 2, 5)
        pr.cube(f"prong_{nm}2", -1, -1, 9, 2, 2, 5)
        pr.cube(f"prong_{nm}3", -1 + sg * 2, -1, 13, 2, 2, 4)
    fk.cube("spark", -1, -1, 0, 2, 2, 9)
    r.look = [("head", 0.25, 0.2)]
    r.shadow = 3.5
    r.size = 1.0
    # secondary parts (SPEC §35.10): the wingtips, the tail and its fork, the horns' tips and the pelvic fins swing
    r.springs = ([(f"wing_{nm}{k}", 0.25 + 0.07 * k, 1.6 - 0.1 * k, 0.25) for nm, _ in SIDES_ for k in (3, 4, 5)]
                 + [(f"tail{k}", 0.3 + 0.04 * k, 1.8 - 0.08 * k, 0.22) for k in range(2, NTAIL)] + [("fork", 0.5, 2.0, 0.25)]
                 + [(f"horn_{nm}3", 0.3, 2.6, 0.3) for nm, _ in SIDES_] + [("pelvic_" + nm, 0.4, 2.4, 0.3) for nm, _ in SIDES_])
    r.guard = []
    return r



# ---------------------------------------------------------------------------------------------------------------------
# Paint: the top and the belly are painted on the floor plan, so the veins, the shoulder patches and the margins run on
# from one strip to the next

SLATE = Ramp("#080c13", "#0d131d", "#131b27", "#1a2431", "#222e3d", "#2b384a", "#354457", "#405166", "#4d5f76", "#5c6f88")
CLOUD = Ramp("#4f5b6d", "#667284", "#7f8a9b", "#99a3b2", "#b2bbc7", "#c9d0da", "#dde2e9")
BELLY = Ramp("#2e333c", "#424852", "#585f6a", "#6f7682", "#878e99", "#a0a6b0", "#b8bdc5", "#cdd1d7", "#dee1e4", "#eceef0")
HORN = Ramp("#0b1018", "#141c28", "#1e2836", "#2a3646", "#374558", "#46566b", "#58697f")
MOUTH = Ramp("#06070b", "#0e0c12", "#1a1219", "#281a24", "#3a2632", "#523845")
VEIN = {2: ((214, 244, 255, 255), (178, 236, 255, 255)), 1: ((140, 190, 222, 255), (110, 200, 255, 190))}  # skin, glow
PEARL = ("#ffffff", "#e8f6ff", "#bfe6ff", "#86ccff")
EYE = ("#ffffff", "#c8f2ff", "#6fd4ff", "#2e8fd8")


def _vn(x, z, scale, seed=0):
    """Smooth value noise on the plan, 0..1."""
    gx, gz = x / scale, z / scale
    ix, iz = math.floor(gx), math.floor(gz)
    fx, fz = gx - ix, gz - iz
    fx, fz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    g = lambda a, b: (R._h("ray", seed, a, b) % 1000) / 999.0
    top = g(ix, iz) + (g(ix + 1, iz) - g(ix, iz)) * fx
    bot = g(ix, iz + 1) + (g(ix + 1, iz + 1) - g(ix, iz + 1)) * fx
    return top + (bot - top) * fz


def _edge(x, z):
    """How far a plan point is inside the disc's outline (pixels; the head's front and the body's back count)."""
    ax = abs(x)
    if ax <= SPAN0:
        return min(z + 42, 30 - z) if z > -30 else min(z + 42, 14 - ax)
    u = min(1.0, (ax - SPAN0) / SPAN)
    return min(z - _le(u), _te(u) - z, (80 - ax) * 0.5)


def _inside(x, z):
    return abs(x) <= 80 and _edge(x, z) >= 0 and not (abs(x) <= SPAN0 and z < -42)


def _veins():
    """The lightning: bolts branch out from the spine toward the wingtips, and one runs down the spine through the
    pearls; {(x, z): 2 for a bolt's core, 1 for a fainter branch}."""
    rng = random.Random(29)
    v = {}

    def line(p, q, lvl):
        n = int(max(abs(q[0] - p[0]), abs(q[1] - p[1]))) + 1
        for t in range(n + 1):
            k = (round(p[0] + (q[0] - p[0]) * t / n), round(p[1] + (q[1] - p[1]) * t / n))
            v[k] = max(v.get(k, 0), lvl)

    def bolt(p, ang, steps, lvl, depth):
        x, z = p
        for _ in range(steps):
            step = rng.uniform(3.0, 6.5)
            a = ang + rng.uniform(-0.65, 0.65)
            nx, nz = x + math.cos(a) * step, z + math.sin(a) * step
            if not _inside(nx, nz) or _edge(nx, nz) < 1.0:
                break
            line((x, z), (nx, nz), lvl)
            if depth < 2 and rng.random() < 0.3:
                bolt((nx, nz), ang + rng.choice((-1, 1)) * rng.uniform(0.55, 1.05), max(2, steps // 3), 1, depth + 1)
            x, z = nx, nz

    for sg in (1, -1):
        for (zs, tx, tz) in ((-22, 62, -10), (-11, 74, 4), (0, 70, 8), (11, 52, 12), (21, 30, 22)):
            a = math.atan2(tz - zs, tx - 5)
            bolt((sg * 5, zs), a if sg > 0 else math.pi - a, 16, 2, 0)
    x, z = 0.0, -38.0
    while z < 30:
        nz = z + rng.uniform(3, 5)
        nx = max(-2.0, min(2.0, x + rng.uniform(-1.6, 1.6)))
        line((x, z), (nx, nz), 2)
        x, z = nx, nz
    return v


def _cloud(x, z):
    """The pale shoulder patches, one either side behind the head, their back edges ragged like a cloud's."""
    ax = abs(x)
    if ax < 6 or ax > 42:
        return 0.0
    front = -40.0 if ax <= SPAN0 else _le((ax - SPAN0) / SPAN) + 1.5
    back = -9.0 - 0.42 * (ax - 6) + 7 * (_vn(x, z, 5, 3) - 0.5)
    inner = 6 + 4 * _vn(x, z, 4, 4)
    if z < front or z > back or ax < inner:
        return 0.0
    return min(1.0, (back - z) / 4.0) * min(1.0, (ax - inner) / 2.0)


def paint(r, P_):
    V = _veins()
    near = {(x + dx, z + dz) for (x, z) in V for dx in (-1, 0, 1) for dz in (-1, 0, 1)}

    def top(x, z, c, i, j, f):
        k = (round(x - 0.5), round(z - 0.5))
        lvl = V.get(k, 0)
        if lvl:
            return VEIN[lvl]
        v = 3.4 + 1.3 * (_vn(x, z, 9, 1) - 0.5) + 0.7 * (_vn(x, z, 3, 2) - 0.5)
        e = _edge(x, z)
        if e < 4:
            v -= (4 - e) * 0.3
        if k in near:
            v += 1.1
        cl = _cloud(x, z)
        if cl > 0.35:
            return CLOUD.at(2.5 + 3.2 * cl + 1.2 * (_vn(x, z, 3, 5) - 0.5)), None
        return SLATE.at(v + 2.5 * cl), None

    def belly(x, z, c, i, j, f):
        ax = abs(x)
        v = 7.6 + 0.5 * (_vn(x, z, 7, 6) - 0.5)
        e = _edge(x, z)
        if e < 7:
            v -= (7 - e) * 0.7
        for g in range(5):  # the gill slits, two rows of five
            gz = -24 + 3.6 * g
            if 7 <= ax <= 13 and abs(z - (gz + 0.25 * (ax - 7))) < 0.6:
                return BELLY.at(1.5), None
            if 7 <= ax <= 13 and abs(z - (gz + 0.25 * (ax - 7)) - 1) < 0.6:
                v += 0.8
        if z < -36:
            v -= 1.2
        for sx, sz, rr in SPOTS:
            if (x - sx) ** 2 + (z - sz) ** 2 < rr * rr:
                v = 2.6 + 0.5 * _vn(x, z, 2, 8)
        return BELLY.at(v), None

    for c in r.cubes:
        n = c.name
        if n in PLAN:
            x0, z0 = PLAN[n]
            for fname, fn in (("top", top), ("bottom", belly)):
                f, g = P_.face(c, fname), P_.face(c, fname, glow=True)
                for i, j in f.each():
                    col, gl = fn(x0 + i + 0.5, z0 + c.d - 1 - j + 0.5, c, i, j, f)
                    f.set(i, j, col)
                    if gl:
                        g.set(i, j, gl)
            pale = n.startswith(("belly", "jaw"))
            P_.paint(c, BELLY if pale else SLATE, 5.0 if pale else 3.6, faces=SIDES, grad=0.6, rim=0.8, ao=0.4, noise=0.6)
            if n.startswith(("wing_", "core")):
                # where a strip steps thinner its sides show: slate over the pale of the belly, as the faces round them
                for fname in ("east", "west"):
                    sf = P_.face(c, fname)
                    for i, j in sf.each():
                        low = j >= sf.h / 2
                        sf.set(i, j, BELLY.at(6.4 + 0.5 * _vn(i, j, 3, 12)) if low else SLATE.at(3.4 + 0.8 * (_vn(i, j, 3, 13) - 0.5)))
    # the leading edge catches the light; the trailing edge is a darker hem
    for c in r.cubes:
        if c.name.startswith("wing_"):
            P_.paint(c, SLATE, 4.6, faces=("north",), grad=0.4, rim=1.0, ao=0.3)
            P_.paint(c, SLATE, 2.4, faces=("south",), grad=0.3, rim=0.4, ao=0.3)
    # the mouth: a wide dark slot across the head's front, the lower lip pale, the gill rakers inside on the jaw
    f = P_.face("head", "north")
    for i, j in f.each():
        if j >= f.h - 3:
            f.set(i, j, MOUTH.at(0.5 + (0.8 if j == f.h - 3 else 0) + 0.4 * _vn(i, j, 3, 9)))
        elif j == f.h - 4:
            f.set(i, j, SLATE.at(5.5))
    jf = P_.face("jaw", "top")
    for i, j in jf.each():
        jf.set(i, j, MOUTH.at(4.2 if i % 3 == 0 else 2.6 - 0.3 * j / max(1, jf.h)))
    P_.paint("jaw", BELLY, 7.0, faces=("north",), grad=0.3, rim=1.0)
    # the horns: slate outside, pale where they curl in, ringed like cartilage
    for nm, _ in SIDES_:
        for k in range(4):
            cn = f"horn_{nm}{k}"
            P_.paint(cn, HORN, 3.0 + 0.4 * k, pattern=lambda f, i, j: 0.9 if (j if f.name in SIDES else i) % 3 == 0 else 0.0,
                     faces=("top",) + SIDES, grad=0.6, rim=0.8)
            P_.paint(cn, BELLY, 5.2, faces=("bottom",), pattern=lambda f, i, j: -1.0 if j % 3 == 0 else 0.0)
        # the eyes, small and bright at the corners of the head
        with P_.eyes():
            for fname in ("top", "bottom") + SIDES:
                for gl in (False, True):
                    ef = P_.face("eye_" + nm, fname, glow=gl)
                    for i, j in ef.each():
                        mid = abs(i - (ef.w - 1) / 2) < 0.6 and abs(j - (ef.h - 1) / 2) < 0.8
                        ef.set(i, j, hexc(EYE[0] if mid else EYE[1] if (i + j) % 2 else EYE[2]))
        P_.paint("pelvic_" + nm, SLATE, 3.2, faces=SIDES)
    # the pearls down the spine
    for k in range(len(PEARL_Z)):
        for fname in ("top", "bottom") + SIDES:
            for gl in (False, True):
                pf = P_.face(f"pearl{k}", fname, glow=gl)
                for i, j in pf.each():
                    edge = i in (0, pf.w - 1) or j in (0, pf.h - 1)
                    corner = i in (0, pf.w - 1) and j in (0, pf.h - 1)
                    pf.set(i, j, hexc(PEARL[3] if corner else PEARL[1] if edge else PEARL[0]))
    # the tail: slate over pale, the last of it crackling, and the fork of lightning
    for k in range(NTAIL):
        cn = f"tail{k}"
        P_.paint(cn, SLATE, 3.4 - 0.1 * k, faces=("top",) + SIDES, pattern=lambda f, i, j: 1.6 if (i if f.name == "top" else j) == 0 else 0.0,
                 noise=0.6, grad=0.5)
        P_.paint(cn, BELLY, 4.0, faces=("bottom",))
        if k >= NTAIL - 3:
            for fname in ("top",) + SIDES:
                gf = P_.face(cn, fname, glow=True)
                for i, j in gf.each():
                    if R._h(cn, fname, i, j) % 9 < 2 + (k - NTAIL + 3):
                        gf.set(i, j, hexc("#9fe4ff", 200))
    for c in r.cubes:
        if c.name.startswith(("prong_", "spark")):
            for fname in ("top", "bottom") + SIDES:
                for gl in (False, True):
                    ff = P_.face(c, fname, glow=gl)
                    for i, j in ff.each():
                        core_ = ff.w <= 2 or 0 < i < ff.w - 1
                        ff.set(i, j, hexc("#ffffff" if core_ and (i + j) % 3 else "#a8ecff"))
    P_.paint("dorsal", SLATE, 2.8, faces=SIDES + ("top",), grad=0.8, rim=1.2)
    # the spine's crest carries the bolt that joins the pearls
    return P_.skin, P_.glow


SPOTS = [(-3.0, -8.0, 1.6), (4.5, -4.0, 1.2), (-6.0, 4.0, 1.8), (2.0, 9.0, 1.0), (7.5, 13.0, 1.4), (-2.5, 17.0, 1.1), (5.0, 21.0, 0.9)]


# ---------------------------------------------------------------------------------------------------------------------
# Motion: one pose call (the body, both wings, the tail, the head and horns)

CURL_W = (0.06, 0.1, 0.15, 0.2, 0.22, 0.27)  # how a wing's curl spreads along it, more toward the tip
WAVE_W = (0.34, 0.2, 0.19, 0.17, 0.15, 0.13)  # how a beat's angle is shared out along it
LAG = 0.38  # radians each wing joint trails the one before in a beat: the wave runs out to the tip
TAIL_W = (0.06, 0.08, 0.1, 0.12, 0.14, 0.15, 0.17, 0.18)


def _wing(p, nm, sg, lift=0.0, curl=0.0, wave=0.0, ph=0.0, sweep=0.0, twist=0.0):
    """One wing: lift raises it at the root (degrees, + up), curl bends it up along its span (+ up, the degrees spread
    toward the tip), wave a beat running out along it (its amplitude, at phase ph: + up at ph = pi/2), sweep turns it
    back at the root, twist turns its leading edge down (and a beat feathers it: down on the downstroke)."""
    for k in range(NSEG):
        f = (k + 1) / NSEG
        a = (lift if k == 0 else 0.0) + curl * CURL_W[k]
        tw = twist * f
        if wave:
            a += wave * WAVE_W[k] * math.sin(ph - LAG * k) * 2.2
            tw -= 0.35 * wave * f * math.cos(ph - LAG * k)
        p[f"wing_{nm}{k}"] = (tw, -sg * sweep if k == 0 else 0.0, -sg * a)


def _tail(p, lift=0.0, curl=0.0, sway=0.0, wave=0.0, ph=0.0, vwave=0.0):
    """The tail: lift raises it at the root (+ up), curl bends it up along its length (+ up, spread toward the tip), sway
    swings it to its left, wave an S running down it side to side (and vwave up and down)."""
    for k in range(NTAIL):
        rx = (lift if k == 0 else 0.0) + curl * TAIL_W[k] + vwave * math.sin(ph - 0.6 * k - 0.8) * 0.5
        ry = (sway * 0.3 if k == 0 else sway * 0.1) + wave * math.sin(ph - 0.7 * k)
        p[f"tail{k}"] = (rx, ry, 0.0)


def _horns(p, curl=0.0, spread=0.0):
    """The horns: curl + rolls them tighter, - unrolls them (at -1 they point straight ahead like a scoop); spread
    turns them out from the mouth."""
    for nm, sg in SIDES_:
        for k in range(4):
            b = "horn_" + nm if k == 0 else f"horn_{nm}{k}"
            p[b] = (-curl * HORN_CURL[k], sg * spread if k == 0 else 0.0, 0.0)


def _pose(pitch=0.0, yaw=0.0, roll=0.0, at=(0.0, 0.0, 0.0), wings=None, wl=None, wr=None, tail=None, head=(0, 0, 0), jaw=0.0,
          horns=0.0, spread=0.0, pelvic=0.0):
    """The body: pitch (+ nose down), yaw (+ turning to its right), roll (+ its left wing down), at (x to its left, up,
    z back); wings: the same _wing arguments for both, or wl and wr each its own; tail: _tail arguments; the head nods
    (+ down), the jaw opens, the horns curl (+) or unroll (-) and spread; the pelvic fins lift."""
    p = {"body": {"rot": (pitch, yaw, roll), "pos": tuple(at)}, "head": tuple(head), "jaw": (jaw, 0, 0),
         "pelvic_l": (0, 0, -pelvic), "pelvic_r": (0, 0, pelvic)}
    _wing(p, "l", 1, **(wl if wl is not None else wings or {}))
    _wing(p, "r", -1, **(wr if wr is not None else wings or {}))
    _tail(p, **(tail or {}))
    _horns(p, horns, spread)
    return p


def _pearls(r, clip, pts):
    """Each pearl's swell: [(seconds, [six scales])] as scale tracks."""
    c = r.clips[clip]
    for k in range(len(PEARL_Z)):
        c.scale(f"pearl{k}", [(t, sc[k], sc[k], sc[k]) for t, sc in pts], "cat")


def _idle(r):
    """Flying in place the way a manta swims: two slow beats a loop, each running out along the wings to their tips, the
    body lifting on the downstroke and settling on the up; the tail swings an S behind, the horns breathe in and out,
    the mouth opens and closes, and a glow runs down the pearls."""
    L, n = 6.0, 48
    keys, pk = [], []
    for k in range(n + 1):
        u = k / n
        ph = 2 * math.pi * 2 * u
        tph = 2 * math.pi * 3 * u
        keys.append((L * u, _pose(pitch=1.5 * math.cos(ph) + 0.8 * math.sin(2 * math.pi * u), roll=1.4 * math.sin(2 * math.pi * u),
                                  yaw=2.0 * math.sin(2 * math.pi * u + 0.6), at=(0, -1.6 * math.sin(ph), 0),
                                  wings=dict(lift=4 * math.sin(ph), curl=-6 + 3 * math.sin(ph - 1.2), wave=15, ph=ph, twist=-2),
                                  tail=dict(lift=2 * math.sin(ph - 1.4), curl=-6, wave=4.5, ph=tph, vwave=3),
                                  head=(-1.5 * math.sin(ph - 0.6), 0, 0), jaw=2 + 3 * max(0.0, math.sin(2 * math.pi * u)),
                                  horns=0.08 * math.sin(2 * math.pi * u), pelvic=6 * math.sin(2 * ph))))
        pk.append((L * u, [1 + 0.14 * max(0.0, math.sin(2 * math.pi * 2 * u - 0.9 * j)) for j in range(len(PEARL_Z))]))
    keyed(r, "idle", L, keys, loop=True)
    _pearls(r, "idle", pk)


def _flight(r):
    """On top of the idle while it flies: it lowers its nose, sweeps its wings back a little and streams its tail, and a
    ripple runs along the wings' edges (faster the faster it goes). It banks into its turns."""
    L, n = 1.5, 12
    keys = []
    for k in range(n + 1):
        u = k / n
        ph = 2 * math.pi * u
        keys.append((L * u, _pose(pitch=3, wings=dict(sweep=6, curl=-3, wave=2.5, ph=ph), tail=dict(lift=-2, curl=-4, wave=1.5, ph=ph))))
    keyed(r, "fly", L, keys, loop=True)
    for nm, sg in (("turn_l", 1), ("turn_r", -1)):  # into a turn to its left, its left wing dips
        keys = []
        for k in range(n + 1):
            u = k / n
            ph = 2 * math.pi * u
            keys.append((1.0 * u, _pose(roll=12 * sg + 2 * math.sin(ph), yaw=-4 * sg, at=(sg * 2, 0, 0),
                                        wl=dict(lift=-6 * sg + 3 * math.sin(ph), curl=-4 * sg), wr=dict(lift=6 * sg - 3 * math.sin(ph), curl=4 * sg),
                                        tail=dict(sway=-10 * sg, wave=3, ph=ph), head=(0, -6 * sg, 0))))
        keyed(r, nm, 1.0, keys, loop=True)


def _sag(r, low=1.0):
    """Its stance broken: it sags in the air nose down, its wings drooping and its tail hanging, the horns slack."""
    return _pose(pitch=10 * low, roll=6 * low, at=(0, -14 * low, 0), wings=dict(lift=-12 * low, curl=-34 * low, twist=6 * low),
                 tail=dict(lift=-14 * low, curl=-12 * low), head=(6 * low, 0, 0), jaw=7 * low, horns=-0.35 * low)


def _reactions(r):
    """Struck, it jolts back with its wings flinching up and its tail whipping; from the side, it rolls away. Broken, it
    sags in the air; it rises out of it with one great beat."""
    hit = _pose(pitch=-9, at=(0, 3, 3), wings=dict(lift=14, curl=18, twist=-6), tail=dict(lift=12, curl=10), head=(-6, 0, 0), jaw=8, horns=0.3)
    back = _pose(pitch=3, at=(0, -1, 0), wings=dict(lift=-4, curl=-6), tail=dict(lift=-4, curl=-4), head=(2, 0, 0), jaw=3)
    keyed(r, "hurt", 0.6, [(0, _pose()), (0.08, hit), (0.32, back), (0.6, _pose())])
    for nm, sg in (("hurt_l", 1), ("hurt_r", -1)):  # struck from its left it rolls away, its left wing thrown up
        h = _pose(roll=-15 * sg, yaw=-6 * sg, pitch=-4, at=(-3 * sg, 2, 1), wl=dict(lift=20 * sg if sg > 0 else -8, curl=16 * sg if sg > 0 else -6),
                  wr=dict(lift=-20 * sg if sg < 0 else -8, curl=-16 * sg if sg < 0 else -6), tail=dict(sway=14 * sg, curl=6), head=(-4, 10 * sg, 0), jaw=7)
        b = _pose(roll=4 * sg, at=(sg * 0.5, -0.5, 0), wings=dict(lift=-3, curl=-4), tail=dict(sway=-5 * sg), head=(0, -3 * sg, 0), jaw=3)
        keyed(r, nm, 0.65, [(0, _pose()), (0.08, h), (0.34, b), (0.65, _pose())])
    keyed(r, "kneel", 0.8, [(0, _pose()), (0.25, _pose(pitch=-4, at=(0, 2, 0), wings=dict(lift=10, curl=10), jaw=6)), (0.6, _sag(r, 1.08)), (0.8, _sag(r))])
    up_ = _pose(pitch=4, at=(0, -9, 0), wings=dict(lift=36, curl=26, twist=-8), tail=dict(lift=-12, curl=-8), head=(2, 0, 0), jaw=5, horns=-0.1)
    down = _pose(pitch=-3, at=(0, 4, 0), wings=dict(lift=-22, curl=-26, twist=10), tail=dict(lift=8, curl=6), jaw=3)
    keyed(r, "rise", 1.1, [(0, _sag(r)), (0.4, up_), (0.75, down), (1.1, _pose())])
    _death(r)


def _death(r):
    """Its fall: it convulses, its wings flung up and the lightning crackling; it beats once more, weakly, then rolls
    over onto its back and sinks, its wings going limp and its tail trailing, belly to the sky, and its pearls go out."""
    conv = _pose(pitch=-14, at=(0, 4, 2), wings=dict(lift=30, curl=34, twist=-10), tail=dict(lift=22, curl=30), head=(-10, 0, 0), jaw=16, horns=-0.6)
    weak = _pose(pitch=6, roll=-8, at=(0, -2, 0), wings=dict(lift=-18, curl=-24), tail=dict(lift=-6, curl=-10), head=(6, 0, 0), jaw=10, horns=-0.4)
    lean = _pose(pitch=8, roll=-34, at=(0, -6, 0), wl=dict(lift=8, curl=-10), wr=dict(lift=-14, curl=-30), tail=dict(lift=-8, sway=8), jaw=10, horns=-0.5)
    over = _pose(pitch=4, roll=-118, at=(0, -16, 0), wl=dict(lift=-4, curl=14), wr=dict(lift=6, curl=6), tail=dict(lift=6, curl=10, sway=6), jaw=8, horns=-0.6)
    belly = lambda dz: _pose(pitch=-4, roll=-172, at=(0, -26 - dz, 0), wings=dict(lift=10, curl=26 + dz), tail=dict(lift=12, curl=16), head=(-6, 0, 0),
                             jaw=6, horns=-0.7)
    keys = [(0, _pose()), (0.35, conv), (0.85, weak), (1.5, lean), (2.3, over), (3.1, belly(0)), (3.8, belly(3)), (4.4, belly(4))]
    keyed(r, "death", 4.4, keys)
    _pearls(r, "death", [(0, [1] * 6), (0.35, [1.4] * 6), (1.5, [1.0] * 6), (3.1, [0.7] * 6), (4.4, [0.4] * 6)])


# ---------------------------------------------------------------------------------------------------------------------
# Moves

def _lag(**kw):
    out = {"head": 0.05, "jaw": 0.06}
    out.update(kw)
    return out


def _tip(nm, sg):
    return [(f"wing_{nm}5", (sg * 5, 0, 10), 9.0), (f"wing_{nm}3", (sg * 5.5, 0, -2), 10.0)]


def slap(side="l", mid="slap"):
    """It banks, the near wing raised high over its back and curled; then the wing comes down like a whip, its tip
    cracking down ahead of it, the body rolling down after it (LIGHT)."""
    sg = 1 if side == "l" else -1
    near, far = ("wl", "wr") if sg > 0 else ("wr", "wl")
    gather = _pose(roll=-13 * sg, yaw=-4 * sg, pitch=-2, at=(sg * 2, 2.5, 0.5), **{near: dict(lift=30, curl=26, twist=-6), far: dict(lift=-9, curl=-8)},
                   tail=dict(sway=-14 * sg, lift=6, curl=14), head=(-3, -6 * sg, 0), jaw=5, horns=-0.45, spread=4, pelvic=8)
    rear = _pose(roll=-22 * sg, yaw=-8 * sg, pitch=-5, at=(sg * 3, 4.5, 1), **{near: dict(lift=52, curl=70, sweep=-12, twist=-14), far: dict(lift=-16, curl=-24)},
                 tail=dict(sway=-34 * sg, lift=10, curl=34), head=(-6, -12 * sg, 0), jaw=8, horns=-0.6, spread=6, pelvic=14)
    hit = _pose(roll=19 * sg, yaw=-2 * sg, pitch=4, at=(-sg, -4, -4), **{near: dict(lift=-36, curl=-50, sweep=-12, twist=14), far: dict(lift=9, curl=11)},
                tail=dict(sway=26 * sg, lift=-6, curl=-22), head=(5, -5 * sg, 0), jaw=4, horns=-0.55, spread=5, pelvic=-10)
    held = _pose(roll=21 * sg, yaw=-2 * sg, pitch=5, at=(-sg, -5, -5), **{near: dict(lift=-40, curl=-56, sweep=-12, twist=12), far: dict(lift=11, curl=13)},
                 tail=dict(sway=30 * sg, lift=-7, curl=-26), head=(6, -5 * sg, 0), jaw=4, horns=-0.55, spread=5, pelvic=-10)
    sink = _pose(roll=18 * sg, pitch=4, at=(-0.5 * sg, -5.5, -4), **{near: dict(lift=-36, curl=-50, sweep=-10, twist=8), far: dict(lift=9, curl=10)},
                 tail=dict(sway=24 * sg, lift=-5, curl=-20), head=(4, -3 * sg, 0), jaw=4, horns=-0.45, spread=4, pelvic=-6)
    back = _pose(roll=6 * sg, at=(0, -2.5, -1), **{near: dict(lift=-12, curl=-16), far: dict(lift=4)}, tail=dict(sway=8 * sg, curl=-6), jaw=3, horns=-0.15)
    return Move(mid, "LIGHT", 10, 4, 14, through=sink, track=8.0, rng=(0, 6.0), kinds=("combo", "near"), damage=1.0, knock=0.8, plant=(),
                hit=_tip(side, sg), stages={"windup": [(0.25, gather), (0.62, core._scaled(rear, 0.92)), (1.0, rear)], "active": [(0.5, hit), (1.0, held)],
                        "recover": [(0.5, back)]}, lag=_lag())


def ram(r):
    """The Horn Ram: it draws back nose up, its horns unrolling into a scoop either side of its gaping mouth, and surges
    at you head first, its wings sweeping back (LIGHT)."""
    gather = _pose(pitch=-6, at=(0, 1, 4), wings=dict(lift=10, curl=10, sweep=3), tail=dict(lift=-4), head=(-3, 0, 0), jaw=8, horns=-0.5, spread=4)
    peak = _pose(pitch=-12, at=(0, 2, 9), wings=dict(lift=22, curl=18, sweep=-6, twist=-6), tail=dict(lift=-8, curl=-8), head=(-6, 0, 0), jaw=16,
                 horns=-1.0, spread=8)
    hit = _pose(pitch=12, at=(0, -3, -22), wings=dict(lift=-22, curl=-24, sweep=14, twist=10), tail=dict(lift=10, curl=8), head=(6, 0, 0), jaw=20,
                horns=-1.0, spread=6)
    held = _pose(pitch=13, at=(0, -3.5, -25), wings=dict(lift=-24, curl=-26, sweep=16, twist=10), tail=dict(lift=11, curl=10), head=(7, 0, 0), jaw=18,
                 horns=-1.0, spread=6)
    sink = _pose(pitch=10, at=(0, -3, -23), wings=dict(lift=-18, curl=-20, sweep=12, twist=6), tail=dict(lift=8, curl=8), head=(5, 0, 0), jaw=14,
                 horns=-0.9, spread=5)
    back = _pose(pitch=3, at=(0, -1, -8), wings=dict(lift=6, curl=4), tail=dict(lift=2), head=(1, 0, 0), jaw=6, horns=-0.3)
    return Move("ram", "LIGHT", 12, 4, 14, through=sink, track=8.0, rng=(0, 4.5), kinds=("near",), damage=1.1, knock=1.1, plant=(),
                hit=[("head", (0, 0, -14), 10.0), ("horn_l3", (0, 0, -4), 6.0), ("horn_r3", (0, 0, -4), 6.0)],
                stages={"windup": [(0.3, gather), (1.0, peak)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


def wingbeat(r):
    """The Wingbeat (HEAVY): it sinks and raises both wings high over its back, curled until their tips nearly meet,
    holds them there, and brings them down in one enormous stroke that throws it up into the air."""
    gather = _pose(pitch=-4, at=(0, -3, 0), wings=dict(lift=20, curl=14, twist=-4), tail=dict(lift=-6, curl=-4), jaw=4)
    rise = _pose(pitch=-8, at=(0, -6, 1), wings=dict(lift=48, curl=32, twist=-10), tail=dict(lift=-12, curl=-10), head=(-6, 0, 0), jaw=8, horns=0.3)
    peak = _pose(pitch=-10, at=(0, -7, 1), wings=dict(lift=56, curl=64, twist=-14, sweep=-5), tail=dict(lift=-16, curl=-46), head=(-10, 0, 0), jaw=12,
                 horns=0.5, pelvic=16)
    hit = _pose(pitch=8, at=(0, 9, -1), wings=dict(lift=-40, curl=-42, twist=14, sweep=6), tail=dict(lift=12, curl=10), head=(6, 0, 0), jaw=4, horns=-0.2)
    held = _pose(pitch=9, at=(0, 11, -1), wings=dict(lift=-44, curl=-48, twist=12, sweep=6), tail=dict(lift=14, curl=12), head=(7, 0, 0), jaw=4,
                 horns=-0.2)
    sink = _pose(pitch=7, at=(0, 10, -1), wings=dict(lift=-36, curl=-40, twist=8, sweep=4), tail=dict(lift=12, curl=10), head=(5, 0, 0), jaw=4)
    back = _pose(pitch=2, at=(0, 3, 0), wings=dict(lift=-8, curl=-10), tail=dict(lift=2), jaw=3)
    return Move("wingbeat", "HEAVY", 18, 6, 24, through=sink, title="Wingbeat", hold=10, track=4.0, ability="Wingbeat", origin=(0, 0, 0), rng=(0, 10),
                kinds=("area",), damage=0.9, knock=1.6, plant=(),
                hit=_tip("l", 1) + _tip("r", -1) + [("body", (0, 0, 0), 20.0)],
                stages={"windup": [(0.3, gather), (0.7, rise), (1.0, peak)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]},
                lag=_lag())


def dive(r):
    """The Leviathan Dive (HEAVY, a gap closer): two strong beats carry it up nose high, its horns unrolling; at the top
    it tips over and dives at you like a dart, its wings swept back and its tail streaming; it pulls up out of it with
    its wings flared."""
    climb = _pose(pitch=-14, at=(0, 6, 3), wings=dict(lift=24, curl=16, twist=-6), tail=dict(lift=-10, curl=-6), head=(-4, 0, 0), jaw=4)
    push = _pose(pitch=-22, at=(0, 12, 4), wings=dict(lift=-16, curl=-22, twist=10), tail=dict(lift=-14, curl=-10), head=(-6, 0, 0), jaw=6, horns=0.1)
    top = _pose(pitch=-26, at=(0, 14, 3), wings=dict(lift=30, curl=34, sweep=14, twist=-10), tail=dict(lift=-16, curl=-40), head=(-8, 0, 0), jaw=12,
                horns=-1.0, spread=8, pelvic=14)
    act = [(0.3, _pose(pitch=26, at=(0, 4, -6), wings=dict(lift=8, curl=-4, sweep=26, twist=6), tail=dict(lift=6), head=(4, 0, 0), jaw=14, horns=-1.0,
                       spread=6)),
           (0.65, _pose(pitch=30, at=(0, -6, -12), wings=dict(lift=6, curl=-6, sweep=30, twist=6), tail=dict(lift=4, curl=2), head=(6, 0, 0), jaw=16,
                        horns=-1.0, spread=6)),
           (1.0, _pose(pitch=22, at=(0, -9, -14), wings=dict(lift=2, curl=-10, sweep=24, twist=4), tail=dict(lift=6, curl=4), head=(5, 0, 0), jaw=16,
                       horns=-1.0, spread=6))]
    flare = _pose(pitch=-6, at=(0, -6, -12), wings=dict(lift=-20, curl=-26, sweep=-6, twist=-16), tail=dict(lift=14, curl=16), head=(-4, 0, 0), jaw=8,
                  horns=-0.7, spread=5, pelvic=10)
    end = _pose(pitch=-4, at=(0, 0, -4), wings=dict(lift=10, curl=8), tail=dict(lift=4), jaw=4)
    return Move("dive", "HEAVY", 16, 12, 20, through=flare, title="Leviathan Dive", track=6.0, lock=10, advance=9.0, ability="Leviathan Dive",
                rng=(6, 18), kinds=("gap",), damage=1.3, knock=1.5, plant=(),
                hit=[("head", (0, 0, -14), 12.0), ("body", (0, 0, 0), 18.0), ("horn_l3", (0, 0, -4), 6.0), ("horn_r3", (0, 0, -4), 6.0)],
                stages={"windup": [(0.3, climb), (0.65, push), (1.0, top)], "active": act, "recover": [(0.5, end)]}, lag=_lag())


def lash(r):
    """For whoever is behind it (LIGHT): it dips its nose and its tail rises in an arc over its back like a scorpion's,
    the fork crackling over it; then it cracks down behind it like a whip."""
    gather = _pose(pitch=4, at=(0, 1, -1), wings=dict(lift=6, curl=4), tail=dict(lift=14, curl=24), jaw=3)
    peak = _pose(pitch=12, at=(0, 2, -2), wings=dict(lift=24, curl=24, twist=6), tail=dict(lift=30, curl=124), head=(6, 0, 0), jaw=10, horns=0.5,
                 pelvic=20)
    hit = _pose(pitch=-6, at=(0, -1, 1), wings=dict(lift=-6, curl=-6), tail=dict(lift=-16, curl=-52), head=(-3, 0, 0), jaw=3)
    held = _pose(pitch=-7, at=(0, -1.5, 1.5), wings=dict(lift=-8, curl=-8), tail=dict(lift=-18, curl=-58), head=(-3, 0, 0), jaw=3)
    sink = _pose(pitch=-5, at=(0, -1, 1), wings=dict(lift=-6, curl=-6), tail=dict(lift=-14, curl=-42), head=(-2, 0, 0), jaw=3)
    back = _pose(pitch=-1, wings=dict(lift=2), tail=dict(lift=-2, curl=-6), jaw=3)
    return Move("lash", "LIGHT", 10, 4, 14, through=sink, track=0.0, rng=(0, 7.0), kinds=("behind",), damage=1.0, knock=1.2, plant=(),
                hit=[("fork", (0, 0, 8), 8.0), ("tail7", (0, 0, 7), 6.0), ("tail5", (0, 0, 7), 6.0)],
                stages={"windup": [(0.3, gather), (1.0, peak)], "active": [(0.5, hit), (1.0, held)], "recover": [(0.5, back)]}, lag=_lag())


def tide(r):
    """The Gravity Tide: it rolls over onto its back, its pale belly to the sky and its wings cupped up, and swims there
    upside down as the sky tilts; then it rolls back."""
    over = lambda rl, up, **kw: _pose(roll=rl, at=(0, up, 0), **kw)
    w0 = over(40, 4, pitch=-4, wings=dict(lift=10, curl=10), tail=dict(sway=6), jaw=6)
    w1 = over(115, 6, wings=dict(lift=2, curl=-8), tail=dict(sway=8, curl=6), jaw=8, horns=-0.2)
    w2 = over(172, 6, wings=dict(lift=-8, curl=-22), tail=dict(curl=10), head=(-4, 0, 0), jaw=10, horns=-0.4)
    swim = lambda s, up: over(174 + 5 * s, up, pitch=3 * s, yaw=4 * s, wings=dict(lift=-8 + 16 * s, curl=-20 - 14 * s, twist=-7 * s),
                              tail=dict(curl=10 - 6 * s, sway=12 * s), head=(-4 - 5 * s, 6 * s, 0), jaw=10 + 6 * s, horns=-0.4 + 0.3 * s, spread=4 * s)
    back = over(70, 3, wings=dict(lift=8, curl=6), tail=dict(sway=-6), jaw=6)
    return Move("tide", "NONE", 18, 26, 20, through=swim(0.4, 6.5), title="Gravity Tide", track=0.0, ability="Gravity Tide", origin=(0, 0, 0),
                rng=(0, 99), kinds=("area",), damage=0, knock=0, plant=(),
                stages={"windup": [(0.3, w0), (0.65, w1), (1.0, w2)], "active": [(0.25, swim(1, 7.5)), (0.5, swim(-1, 5)), (0.75, swim(1, 7.5)), (1.0, swim(-0.4, 6))],
                        "recover": [(0.45, back)]}, lag=_lag())


def barrage(r):
    """The Pearl Barrage: it rises, arching its back with its wings lifted, and the pearls down its spine fire one after
    another, front to back, each one a jolt through it."""
    arch = _pose(pitch=-6, at=(0, 2, 2), wings=dict(lift=14, curl=10), tail=dict(lift=-8), head=(-6, 0, 0), jaw=6)
    peak = _pose(pitch=-10, at=(0, 3, 3), wings=dict(lift=18, curl=16, twist=-6), tail=dict(lift=-12, curl=-10), head=(-8, 0, 0), jaw=10, horns=0.25)
    act = []
    for k in range(6):
        f = (k + 0.3) / 6
        b = math.sin(math.pi * k / 2)
        act.append((f, _pose(pitch=-6, at=(0, 3 - 0.2 * k, 5), wings=dict(lift=18 - 14 * b, curl=14 - 14 * b, twist=-4 + 8 * b), tail=dict(lift=-8 + 8 * b, curl=-6),
                             head=(-4, 0, 0), jaw=14, horns=0.25)))
        act.append((min(1.0, f + 0.5 / 6), _pose(pitch=-10, at=(0, 3 - 0.2 * k, 3), wings=dict(lift=18 - 8 * b, curl=14 - 8 * b), tail=dict(lift=-12, curl=-10),
                                                head=(-8, 0, 0), jaw=10, horns=0.25)))
    back = _pose(pitch=-3, at=(0, 1, 1), wings=dict(lift=4, curl=2), jaw=4)
    mv = Move("barrage", "NONE", 12, 30, 16, through=act[-1][1], title="Pearl Barrage", track=9.0, ability="Pearl Barrage", origin=(0, 0.7, 0.3),
              rng=(4, 22), kinds=("ranged",), damage=0, knock=0, plant=(),
              stages={"windup": [(0.3, arch), (1.0, peak)], "active": act, "recover": [(0.5, back)]}, lag=_lag())
    mv._pearls = "barrage"
    return mv


def storm(r):
    """The Storm Eye (PERILOUS, the signature): it rises with its wings curling up into a bowl and its tail raised in an
    arc over it, the fork high over its middle, and the whole disc wheels round and round, faster, its pearls swelling;
    then it throws its wings flat and wide, the tail cracks down, and the lightning comes down all round."""
    w = []
    n = 10
    for k in range(1, n + 1):
        f = k / n
        th = 2 * math.pi * 2.25 * f * f  # the wheel, quickening
        a = 11 * f
        w.append((f, _pose(pitch=a * math.cos(th) - 4 * f, roll=a * math.sin(th), at=(0, 20 * f, 0),
                           wings=dict(lift=16 + 14 * f, curl=20 + 32 * f, twist=-10 * f), tail=dict(lift=18 + 22 * f, curl=40 + 64 * f),
                           head=(-6 * f, 0, 0), jaw=8 + 14 * f, horns=0.2 + 0.25 * f)))
    hit = _pose(pitch=6, at=(0, 12, 0), wings=dict(lift=-30, curl=-30, twist=14), tail=dict(lift=-10, curl=-30), head=(5, 0, 0), jaw=26, horns=-0.8, spread=6)
    held = _pose(pitch=5, at=(0, 10, 0), wings=dict(lift=-24, curl=-24, twist=10), tail=dict(lift=-6, curl=-24), head=(4, 0, 0), jaw=20, horns=-0.7, spread=5)
    sink = _pose(pitch=4, at=(0, 10, 0), wings=dict(lift=-18, curl=-18, twist=6), tail=dict(lift=-4, curl=-16), head=(3, 0, 0), jaw=14, horns=-0.5)
    settle = _pose(pitch=1, at=(0, 6, 0), wings=dict(lift=6, curl=4), tail=dict(lift=2), jaw=8)
    low = _pose(pitch=-1, at=(0, 2, 0), wings=dict(lift=-4, curl=-4), jaw=4)
    mv = Move("storm", "PERILOUS", 30, 16, 30, through=sink, title="Storm Eye", signature=True, track=0.0, ability="Storm Eye", origin=(0, 0, 0),
              rng=(0, 99), kinds=("area", "ranged"), damage=1.0, knock=1.0, plant=(),
              stages={"windup": w, "active": [(0.25, hit), (1.0, held)], "recover": [(0.4, settle), (0.75, low)]}, lag=_lag())
    mv._pearls = "storm"
    return mv


def unfurl(r):
    """Its intro, once it has risen: it draws its wings in round itself like a cloak, then throws them up and out in one
    great beat with a call, its horns unrolling, and beats again, its tail cracking."""
    wrap = _pose(pitch=2, at=(0, 3, 0), wings=dict(lift=-12, curl=-52, twist=8), tail=dict(lift=-18, curl=-36), head=(4, 0, 0), jaw=2, horns=0.4)
    up_ = _pose(pitch=-8, at=(0, -2, 0), wings=dict(lift=52, curl=40, twist=-12), tail=dict(lift=18, curl=24), head=(-8, 0, 0), jaw=20, horns=-0.8, spread=8)
    down = _pose(pitch=4, at=(0, 10, 0), wings=dict(lift=-30, curl=-32, twist=12), tail=dict(lift=-10, curl=-20), head=(4, 0, 0), jaw=16, horns=-0.6, spread=6)
    up2 = _pose(pitch=-3, at=(0, 6, 0), wings=dict(lift=22, curl=16, twist=-6), tail=dict(lift=10, curl=12), head=(-2, 0, 0), jaw=8, horns=-0.1)
    end = _pose(pitch=1, at=(0, 7, 0), wings=dict(lift=-8, curl=-10), tail=dict(lift=-4), jaw=4)
    return Move("unfurl", "NONE", 12, 26, 16, through=end, track=0.0, rng=(0, 99), kinds=("intro",), damage=0, knock=0, plant=(),
                stages={"windup": [(1.0, wrap)], "active": [(0.3, up_), (0.6, down), (0.85, up2), (1.0, end)], "recover": [(0.5, _pose(at=(0, 2, 0), jaw=3))]},
                lag=_lag())


def moves(r):
    return [slap("l"), slap("r", "slap_b"), ram(r), wingbeat(r), dive(r), lash(r), tide(r), barrage(r), storm(r), unfurl(r)]


def _staged_at(mv, stage, f):
    t0 = {"windup": 0.0, "active": mv.seconds("windup"), "recover": mv.seconds("windup") + mv.seconds("active")}[stage]
    return t0 + f * mv.seconds(stage)


def _pearl_tracks(r, mv):
    """The barrage fires the pearls front to back, each swelling and going dim as it goes; the Storm Eye swells them one
    by one through its wind-up and they all flare as it breaks."""
    W, A, R_ = mv.seconds("windup"), mv.seconds("active"), mv.seconds("recover")
    end = W + A + R_
    if mv._pearls == "barrage":
        pts = [(0, [1] * 6), (W, [1.25] * 6)]
        for k in range(6):
            f = W + A * (k + 0.3) / 6
            pts.append((f - 0.04, [1.25 if j >= k else 0.55 for j in range(6)]))
            pts.append((f, [1.8 if j == k else 1.25 if j > k else 0.55 for j in range(6)]))
        pts += [(W + A + 0.4 * R_, [0.6] * 6), (end, [1] * 6)]
    else:
        pts = [(0, [1] * 6)] + [(W * (k + 1) / 7, [1.5 if j <= k else 1 for j in range(6)]) for k in range(6)] + [(W, [1.6] * 6), (W + 0.25 * A, [2.0] * 6),
                                                                                                                 (W + A, [1.2] * 6), (end, [1] * 6)]
    pts.sort(key=lambda p: p[0])
    _pearls(r, core.clip_name(mv), pts)


def clips(r):
    _idle(r)
    _flight(r)
    _reactions(r)
    mv = moves(r)
    for m in mv:
        stage_clips(r, m)
        if getattr(m, "_pearls", None):
            _pearl_tracks(r, m)
    return mv


# Its sounds (SPEC §35.8): great wingbeats, wind, a whale's deep call and thunder; chosen by ear on paper, not yet heard
# in game.
SOUNDS = {
    "tell_light": [("entity.phantom.flap", 0.9, 0.7), ("entity.breeze.idle_air", 0.8, 0.6)],
    "tell_heavy": [("entity.ender_dragon.flap", 0.9, 0.7), ("entity.elder_guardian.ambient", 1.2, 0.5)],
    "tell_perilous": [("entity.lightning_bolt.thunder", 0.6, 1.4), ("entity.elder_guardian.curse", 0.8, 0.5), ("entity.warden.sonic_charge", 0.8, 0.6)],
    "swing_light": [("entity.phantom.swoop", 1.0, 0.8), ("entity.player.attack.sweep", 0.8, 0.6)],
    "swing_heavy": [("entity.ender_dragon.flap", 1.4, 0.6), ("entity.breeze.wind_burst", 1.0, 0.7)],
    "cast": [("entity.breeze.charge", 1.0, 0.7), ("block.amethyst_block.chime", 1.4, 0.9)],
    "impact": [("entity.lightning_bolt.impact", 0.8, 1.0), ("entity.breeze.wind_burst", 1.0, 0.6)],
    "roar": [("entity.elder_guardian.ambient", 1.6, 0.5), ("entity.lightning_bolt.thunder", 0.7, 1.2)],
    "step": [("entity.phantom.flap", 0.8, 0.6)],
}
MOVE_SOUNDS = {"tide": {"swing": "cast", "impact": ""}, "barrage": {"swing": "cast"}, "storm": {"swing": "roar"}, "unfurl": {"swing": "roar", "impact": ""}}

FIGHT = {
    "intro": {"pose": "kneel", "move": "unfurl"},
    "phases": [
        {"pool": [
            {"move": "slap", "weight": 3, "when": "near", "then": [["slap_b"], ["slap_b", "ram"]]},
            {"move": "ram", "weight": 2, "when": "near"},
            {"move": "wingbeat", "weight": 2},
            {"move": "dive", "weight": 3, "when": "far", "then": [["slap"]]},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
        {"transition": "tide", "pool": [
            {"move": "slap", "weight": 3, "when": "near", "then": [["slap_b"], ["slap_b", "ram"]]},
            {"move": "ram", "weight": 2, "when": "near", "then": [["slap"]]},
            {"move": "tide", "weight": 1},
            {"move": "barrage", "weight": 2, "when": "far"},
            {"move": "dive", "weight": 3, "when": "far", "then": [["ram"]]},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
        {"transition": "storm", "pool": [
            {"move": "storm", "weight": 2},
            {"move": "wingbeat", "weight": 2, "then": [["slap"]]},
            {"move": "dive", "weight": 3, "when": "far", "then": [["slap", "slap_b"]]},
            {"move": "slap", "weight": 3, "when": "near", "then": [["slap_b", "ram"]]},
            {"move": "ram", "weight": 2, "when": "near"},
            {"move": "lash", "weight": 3, "when": "behind"},
        ]},
    ],
    "enrage": {"below": 0.15, "speed": 1.2},
}


def design():
    r = body()
    r.pack()
    mv = clips(r)
    return {"rig": r, "plan": None, "moves": mv, "fight": FIGHT, "paint": paint, "scale": SCALE, "half": 2.2, "sounds": SOUNDS,
            "move_sounds": MOVE_SOUNDS}


def probe():
    """Where its parts are at rest (blocks: x, up, forward)."""
    r = body()
    fr = core.frames(r, {})
    for b, p in (("body", (0, 0, 0)), ("wing_l5", (5, 0, 10)), ("wing_r5", (-5, 0, 10)), ("head", (0, 0, -12)), ("horn_l3", (0, 0, -4)),
                 ("fork", (0, 0, 8)), ("pearl0", (0, 0, 0))):
        print(b, p, [round(v, 2) for v in core.body(core.point(fr, b, p))])
    print("faces", core.faces(r), "cubes", sum(len(b.cubes) for b in r.bones), "bones", len(r.bones))
    print(core.floating(r))


if __name__ == "__main__":
    probe()
